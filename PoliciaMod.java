package com.example.policia;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;
import java.util.function.Supplier;

/** Policia: personaje policia con arbol de habilidades por experiencia. */
@Mod("policia")
public class PoliciaMod {
    public static final String ID = "policia";
    public static final SimpleChannel NET = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(ID, "main"))
            .networkProtocolVersion(() -> "1")
            .clientAcceptedVersions(v -> true)
            .serverAcceptedVersions(v -> true)
            .simpleChannel();

    /** Rama de habilidades, en orden. Solo la primera esta implementada por ahora. */
    public static final String[] SKILLS = {
            "Escudo balistico",
            "REFUERZO",
            "Tanque",
            "Helicoptero",
            "Tanque movil"};
    /** Habilidad previa necesaria para desbloquear cada una. */
    public static final int[] PRE = {-1, 0, 1, 2, 2};
    /** Niveles de experiencia que cuesta desbloquear (0 = se paga con XP_PER_NODE). */
    public static final int[] LEVEL_COST = {0, 0, 0, 0, 20};
    public static final int XP_PER_NODE = 10;
    public static final int SHIELD_TICKS = 240;   // 12 s
    public static final int SHIELD_COOLDOWN = 80; // 15 s
    static final double BLOCK_COS = 0.25;          // ~75 grados a cada lado del frente
    static final float BLOCK_FACTOR = 0.15f;       // recibe el 15% del dano de frente

    // ---------- red ----------
    public static class Act {
        final int id;
        Act(int id) { this.id = id; }
        static void enc(Act m, FriendlyByteBuf b) { b.writeByte(m.id); }
        static Act dec(FriendlyByteBuf b) { return new Act(b.readByte()); }
        static void handle(Act m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                ServerPlayer p = c.get().getSender();
                if (p != null) act(p, m.id);
            });
            c.get().setPacketHandled(true);
        }
    }

    public static class Sync {
        UUID id = new UUID(0, 0);
        boolean on;
        int shield, cooldown, xp, sel, un, tcd, rl, rcd;
        Sync() { }
        static void enc(Sync m, FriendlyByteBuf b) {
            b.writeUUID(m.id); b.writeBoolean(m.on);
            b.writeVarInt(m.shield); b.writeVarInt(m.cooldown); b.writeVarInt(m.xp); b.writeVarInt(m.sel); b.writeVarInt(m.un); b.writeVarInt(m.tcd); b.writeVarInt(m.rl); b.writeVarInt(m.rcd);
        }
        static Sync dec(FriendlyByteBuf b) {
            Sync m = new Sync();
            m.id = b.readUUID(); m.on = b.readBoolean();
            m.shield = b.readVarInt(); m.cooldown = b.readVarInt(); m.xp = b.readVarInt(); m.sel = b.readVarInt(); m.un = b.readVarInt(); m.tcd = b.readVarInt(); m.rl = b.readVarInt(); m.rcd = b.readVarInt();
            return m;
        }
        static void handle(Sync m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PoliciaClient.onSync(m)));
            c.get().setPacketHandled(true);
        }
    }

    // ---------- estado (en los datos persistentes del jugador) ----------
    static boolean on(Player p) {
        CompoundTag d = p.getPersistentData();
        return d.getBoolean("pol_on");
    }
    static int xp(Player p) { return p.getPersistentData().getInt("pol_xp"); }
    static int un(Player p) {
        CompoundTag d = p.getPersistentData();
        if (d.getInt("pol_ver") < 2) { // el orden de la rama cambio: pasa lo desbloqueado a su nueva posicion
            int o = d.getInt("pol_un");
            int n = (o & 1) | (((o >> 1) & 1) << 2) | (((o >> 2) & 1) << 3) | (((o >> 3) & 1) << 4);
            d.putInt("pol_un", n);
            int so = d.getInt("pol_sel");
            d.putInt("pol_sel", so == 0 ? 0 : (so == 2 ? 3 : 2));
            d.putInt("pol_ver", 2);
        }
        return d.getInt("pol_un") | 1;
    }
    static boolean has(Player p, int i) { return ((un(p) >> i) & 1) == 1; }
    /** Nivel de REFUERZO: 0 si esta bloqueada, de 1 a 4 si no. */
    static int rlevel(Player p) { return has(p, 1) ? Math.max(1, Math.min(4, p.getPersistentData().getInt("pol_rl"))) : 0; }
    static int sel(Player p) { return p.getPersistentData().getInt("pol_sel"); }
    static int shield(Player p) { return p.getPersistentData().getInt("pol_shield"); }
    static int cooldown(Player p) { return p.getPersistentData().getInt("pol_cd"); }
    /** Cantidad de habilidades desbloqueadas: la primera desde el inicio, luego una cada 10 de experiencia. */
    static int unlocked(Player p) { return Math.min(SKILLS.length, 1 + xp(p) / XP_PER_NODE); }
    static void msg(ServerPlayer p, String s) { p.displayClientMessage(Component.literal(s), true); }

    public PoliciaMod() {
        NET.registerMessage(0, Act.class, Act::enc, Act::dec, Act::handle);
        NET.registerMessage(1, Sync.class, Sync::enc, Sync::dec, Sync::handle);
        MinecraftForge.EVENT_BUS.register(this);
        PoliciaShield.ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        PoliciaTank.ENTITIES.register(FMLJavaModLoadingContext.get().getModEventBus());
        PoliciaRefuerzo.ENTITIES.register(FMLJavaModLoadingContext.get().getModEventBus());
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaRefuerzo::attrs);
        if (FMLEnvironment.dist.isClient()) {
            PoliciaClient.init();
        }
    }

    static Sync snapshot(Player p) {
        Sync m = new Sync();
        m.id = p.getUUID(); m.on = on(p);
        m.shield = shield(p); m.cooldown = cooldown(p); m.xp = xp(p); m.sel = sel(p); m.un = un(p); m.tcd = p.getPersistentData().getInt("pol_tcd"); m.rl = rlevel(p); m.rcd = p.getPersistentData().getInt("pol_rcd");
        return m;
    }

    /** Se lo manda al propio jugador y a quienes lo ven. */
    static void sync(ServerPlayer p) {
        NET.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), snapshot(p));
    }

    // ---------- acciones ----------
    static void act(ServerPlayer p, int id) {
        CompoundTag d = p.getPersistentData();
        if (id == 0) { // alternar forma policia (skin)
            d.putBoolean("pol_on", !on(p));
            if (!on(p)) { d.putInt("pol_shield", 0); endShield(p); }
            d.putInt("pol_tf", 40);
            d.putInt("pol_tfd", on(p) ? 1 : -1);
            transformFx(p);
            msg(p, on(p) ? "Modo policia activado" : "Modo policia desactivado");
            sync(p);
            return;
        }
        if (id == 30) { // mejorar REFUERZO (cuesta XP)
            int lv = rlevel(p);
            if (lv <= 0) { msg(p, "Desbloquea primero REFUERZO"); return; }
            if (lv >= 4) { msg(p, "REFUERZO ya esta al nivel maximo"); return; }
            int cost = XP_PER_NODE * lv;
            if (xp(p) < cost) { msg(p, "Necesitas " + cost + " XP para REFUERZO nivel " + (lv + 1) + " (tienes " + xp(p) + ")"); return; }
            d.putInt("pol_xp", xp(p) - cost);
            d.putInt("pol_rl", lv + 1);
            msg(p, "REFUERZO nivel " + (lv + 1) + ": " + PoliciaRefuerzo.COUNT[lv + 1] + " policias, espada de " + PoliciaRefuerzo.SWORD[lv + 1]);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.4f);
            sync(p);
            return;
        }
        if (id >= 20 && id < 30) { // desbloquear habilidad (clic en el icono): cuesta XP
            int s = id - 20;
            if (s <= 0 || s >= SKILLS.length || has(p, s)) return;
            if (!has(p, PRE[s])) { msg(p, "Desbloquea primero: " + SKILLS[PRE[s]]); return; }
            if (LEVEL_COST[s] > 0) {
                if (p.experienceLevel < LEVEL_COST[s]) { msg(p, "Necesitas " + LEVEL_COST[s] + " niveles de experiencia para desbloquear " + SKILLS[s] + " (tienes " + p.experienceLevel + ")"); return; }
                p.giveExperienceLevels(-LEVEL_COST[s]);
            } else {
                if (xp(p) < XP_PER_NODE) { msg(p, "Necesitas " + XP_PER_NODE + " XP para desbloquear " + SKILLS[s]); return; }
                d.putInt("pol_xp", xp(p) - XP_PER_NODE);
            }
            d.putInt("pol_un", un(p) | (1 << s));
            if (s == 1) d.putInt("pol_rl", 1);
            msg(p, "Habilidad desbloqueada: " + SKILLS[s]);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
            sync(p);
            return;
        }
        if (id >= 10) { // elegir habilidad desbloqueada
            int s = id - 10;
            if (s == 4) s = 2; // el tanque movil mejora la habilidad Tanque
            if (s >= 0 && s < SKILLS.length && has(p, s)) {
                d.putInt("pol_sel", s);
                msg(p, "Habilidad: " + SKILLS[s]);
                sync(p);
            }
            return;
        }
        if (id == 3) { PoliciaTank.fire(p); return; }
        if (!on(p)) { msg(p, "Activa el modo policia (H) primero"); return; }
        if (id == 2) { // cambiar habilidad seleccionada
            int s = sel(p);
            for (int k = 1; k <= SKILLS.length; k++) {
                int c = (sel(p) + k) % SKILLS.length;
                if (c == 4) continue;
                if (has(p, c)) { s = c; break; }
            }
            d.putInt("pol_sel", s);
            msg(p, "Habilidad: " + SKILLS[s]);
            sync(p);
            return;
        }
        if (id == 1) { // usar habilidad seleccionada
            int s = has(p, sel(p)) ? sel(p) : 0;
            if (s == 0) {
                if (shield(p) > 0) { msg(p, "El escudo ya esta activo"); return; }
                if (cooldown(p) > 0) { msg(p, "Escudo en enfriamiento: " + (cooldown(p) + 19) / 20 + " s"); return; }
                d.putInt("pol_shield", SHIELD_TICKS);
                startShield(p);
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.6f, 1.6f);
                msg(p, "Escudo balistico desplegado");
                sync(p);
            } else if (s == 2 && has(p, 4)) {
                PoliciaTank.useMobile(p);
            } else if (s == 2) {
                if (p.getVehicle() instanceof PoliciaTank.TankEntity) { msg(p, "El tanque ya esta invocado"); return; }
                if (shield(p) > 0) { msg(p, "Espera a que termine el escudo"); return; }
                if (d.getInt("pol_tcd") > 0) { msg(p, "Tanque en enfriamiento: " + (d.getInt("pol_tcd") + 19) / 20 + " s"); return; }
                PoliciaTank.summon(p);
                sync(p);
            } else if (s == 1) {
                PoliciaRefuerzo.use(p);
            } else {
                msg(p, SKILLS[s] + " (proximamente)");
            }
        }
    }

    // ---------- escudo (item real en la mano izquierda) ----------
    static final UUID SLOW_ID = UUID.fromString("7c1d2e3a-5b6f-4a90-8c11-2d3e4f5a6b7c");

    /** Al terminar el escudo, aparta a todo lo que este a un bloque del jugador. */
    static void pushMobs(ServerPlayer p) {
        for (LivingEntity m : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(1.0), x -> x != p && x.isAlive())) {
            m.knockback(1.8F, p.getX() - m.getX(), p.getZ() - m.getZ());
            m.hurtMarked = true;
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.4f, 1.8f);
        p.serverLevel().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), 18, 0.5, 0.1, 0.5, 0.15);
    }

    static final net.minecraft.core.particles.DustParticleOptions NANO =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.25f, 0.85f, 1.0f), 0.8f);
    static final net.minecraft.core.particles.DustParticleOptions NANO2 =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.85f, 0.92f, 1.0f), 0.6f);

    /** Transformacion con nanotecnologia: inicio (sonido). */
    static void transformFx(ServerPlayer p) {
        boolean form = p.getPersistentData().getInt("pol_tfd") > 0;
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(),
                form ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, form ? 1.6f : 1.2f);
    }

    /** Enjambre de nanobots: converge sobre el cuerpo al formarse y se dispersa al deshacerse. */
    static void transformTick(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        int tf = d.getInt("pol_tf");
        if (tf <= 0) return;
        d.putInt("pol_tf", --tf);
        boolean form = d.getInt("pol_tfd") > 0;
        float prog = (40 - tf) / 40.0f;
        var lv = p.serverLevel();
        if (tf % 8 == 0) {
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7f, 0.8f + prog);
        }
        if (tf == 0) {
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(),
                    form ? SoundEvents.BEACON_ACTIVATE : SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, form ? 1.8f : 1.2f);
        }
    }

    // ---------- con el escudo activo: nada de dano, ni golpear, ni interactuar ----------
    @SubscribeEvent
    public void noDamage(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || shield(p) <= 0 || !on(p)) return;
        if (e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        e.setCanceled(true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    @SubscribeEvent
    public void noAttack(AttackEntityEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && (shield(p) > 0 || p.getVehicle() instanceof PoliciaTank.TankEntity)) e.setCanceled(true);
    }

    @SubscribeEvent
    public void noInteract(PlayerInteractEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && (shield(p) > 0 || p.getVehicle() instanceof PoliciaTank.TankEntity) && e.isCancelable()) e.setCanceled(true);
    }

    @SubscribeEvent
    public void noBreak(BlockEvent.BreakEvent e) {
        if (e.getPlayer() instanceof ServerPlayer p && (shield(p) > 0 || p.getVehicle() instanceof PoliciaTank.TankEntity)) e.setCanceled(true);
    }

    static boolean isMine(ItemStack s) { return s.getItem() instanceof PoliciaShield; }

    static void purge(ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isMine(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
        }
    }

    static void startShield(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        ItemStack off = p.getOffhandItem();
        if (!isMine(off)) {
            if (!d.getBoolean("pol_saved")) {
                if (!off.isEmpty()) d.put("pol_off", off.save(new CompoundTag())); else d.remove("pol_off");
                d.putBoolean("pol_saved", true);
            } else if (!off.isEmpty()) {
                p.getInventory().placeItemBackInInventory(off.copy());
            }
            p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(PoliciaShield.SHIELD.get()));
        }
        AttributeInstance mv = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (mv != null && mv.getModifier(SLOW_ID) == null) {
            mv.addTransientModifier(new AttributeModifier(SLOW_ID, "policia_shield_slow", -0.30, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        if (!p.isUsingItem() || !isMine(p.getUseItem())) p.startUsingItem(InteractionHand.OFF_HAND);
    }

    static void keepShield(ServerPlayer p) {
        if (!isMine(p.getOffhandItem())) purge(p);
        startShield(p);
    }

    static void endShield(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        if (p.isUsingItem() && isMine(p.getUseItem())) p.stopUsingItem();
        AttributeInstance mv = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (mv != null) mv.removeModifier(SLOW_ID);
        purge(p);
        if (d.getBoolean("pol_saved")) {
            ItemStack orig = d.contains("pol_off") ? ItemStack.of(d.getCompound("pol_off")) : ItemStack.EMPTY;
            d.remove("pol_off");
            d.putBoolean("pol_saved", false);
            if (!orig.isEmpty()) {
                if (p.getOffhandItem().isEmpty()) p.setItemInHand(InteractionHand.OFF_HAND, orig);
                else p.getInventory().placeItemBackInInventory(orig);
            }
        }
    }

    @SubscribeEvent
    public void death(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && (shield(p) > 0 || p.getPersistentData().getBoolean("pol_saved"))) {
            p.getPersistentData().putInt("pol_shield", 0);
            endShield(p);
        }
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            p.getPersistentData().putInt("pol_shield", 0);
            endShield(p);
        }
    }

    @SubscribeEvent
    public void toss(ItemTossEvent e) {
        if (isMine(e.getEntity().getItem())) e.setCanceled(true);
    }

    // ---------- eventos ----------
    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        CompoundTag d = p.getPersistentData();
        boolean change = false;
        transformTick(p);
        int nf = d.getInt("pol_nofall");
        if (nf > 0) { d.putInt("pol_nofall", nf - 1); p.fallDistance = 0.0f; }
        int tc = d.getInt("pol_tcd");
        if (tc > 0) {
            d.putInt("pol_tcd", tc - 1);
            if (tc - 1 == 0) { msg(p, "Tanque listo"); change = true; }
        }
        int rc = d.getInt("pol_rcd");
        if (rc > 0) {
            d.putInt("pol_rcd", rc - 1);
            if (rc - 1 == 0) { msg(p, "Refuerzo listo"); change = true; }
        }
        int sh = d.getInt("pol_shield");
        if (sh > 0) {
            d.putInt("pol_shield", --sh);
            if (sh > 0) keepShield(p);
            if (sh == 0) {
                endShield(p);
                pushMobs(p);
                d.putInt("pol_cd", SHIELD_COOLDOWN);
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 0.5f, 1.4f);
                msg(p, "El escudo se retiro");
                change = true;
            }
        } else {
            int cd = d.getInt("pol_cd");
            if (cd > 0) {
                d.putInt("pol_cd", cd - 1);
                if (cd - 1 == 0) { msg(p, "Escudo listo"); change = true; }
            }
        }
        if (change || p.tickCount % 40 == 0) sync(p);
    }

    /** Sin retroceso para el jugador que va dentro del tanque. */
    @SubscribeEvent
    public void noKnock(net.minecraftforge.event.entity.living.LivingKnockBackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && p.getVehicle() instanceof PoliciaTank.TankEntity) e.setCanceled(true);
    }

    @SubscribeEvent
    public void noFall(net.minecraftforge.event.entity.living.LivingFallEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && p.getPersistentData().getInt("pol_nofall") > 0) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onXp(PlayerXpEvent.XpChange e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getAmount() <= 0) return;
        p.getPersistentData().putInt("pol_xp", xp(p) + e.getAmount());
        sync(p);
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone e) {
        CompoundTag o = e.getOriginal().getPersistentData();
        CompoundTag n = e.getEntity().getPersistentData();
        for (String k : new String[]{"pol_on", "pol_xp", "pol_sel", "pol_un", "pol_ver", "pol_rl"}) {
            if (o.contains(k)) n.put(k, o.get(k).copy());
        }
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) sync(p);
    }

    @SubscribeEvent
    public void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) sync(p);
    }

    @SubscribeEvent
    public void track(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer t && e.getEntity() instanceof ServerPlayer viewer) {
            NET.send(PacketDistributor.PLAYER.with(() -> viewer), snapshot(t));
        }
    }
}
