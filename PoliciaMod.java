package com.example.policia;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
            "Invocar tanque (proximamente)",
            "Jet de combate (proximamente)"};
    public static final int XP_PER_NODE = 10;
    public static final int SHIELD_TICKS = 240;   // 12 s
    public static final int SHIELD_COOLDOWN = 300; // 15 s
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
        int shield, cooldown, xp, sel;
        Sync() { }
        static void enc(Sync m, FriendlyByteBuf b) {
            b.writeUUID(m.id); b.writeBoolean(m.on);
            b.writeVarInt(m.shield); b.writeVarInt(m.cooldown); b.writeVarInt(m.xp); b.writeVarInt(m.sel);
        }
        static Sync dec(FriendlyByteBuf b) {
            Sync m = new Sync();
            m.id = b.readUUID(); m.on = b.readBoolean();
            m.shield = b.readVarInt(); m.cooldown = b.readVarInt(); m.xp = b.readVarInt(); m.sel = b.readVarInt();
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
        return !d.contains("pol_on") || d.getBoolean("pol_on");
    }
    static int xp(Player p) { return p.getPersistentData().getInt("pol_xp"); }
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
        if (FMLEnvironment.dist.isClient()) {
            PoliciaClient.init();
        }
    }

    static Sync snapshot(Player p) {
        Sync m = new Sync();
        m.id = p.getUUID(); m.on = on(p);
        m.shield = shield(p); m.cooldown = cooldown(p); m.xp = xp(p); m.sel = sel(p);
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
            msg(p, on(p) ? "Modo policia activado" : "Modo policia desactivado");
            sync(p);
            return;
        }
        if (!on(p)) { msg(p, "Activa el modo policia (H) primero"); return; }
        if (id >= 10) { // elegir habilidad concreta desde la pantalla
            int s = id - 10;
            if (s >= 0 && s < unlocked(p)) {
                d.putInt("pol_sel", s);
                msg(p, "Habilidad: " + SKILLS[s]);
                sync(p);
            }
            return;
        }
        if (id == 2) { // cambiar habilidad seleccionada
            int n = unlocked(p);
            int s = (sel(p) + 1) % n;
            d.putInt("pol_sel", s);
            msg(p, "Habilidad: " + SKILLS[s]);
            sync(p);
            return;
        }
        if (id == 1) { // usar habilidad seleccionada
            int s = Math.min(sel(p), unlocked(p) - 1);
            if (s == 0) {
                if (shield(p) > 0) { msg(p, "El escudo ya esta activo"); return; }
                if (cooldown(p) > 0) { msg(p, "Escudo en enfriamiento: " + (cooldown(p) + 19) / 20 + " s"); return; }
                d.putInt("pol_shield", SHIELD_TICKS);
                startShield(p);
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.6f, 1.6f);
                msg(p, "Escudo balistico desplegado");
                sync(p);
            } else {
                msg(p, SKILLS[s]);
            }
        }
    }

    // ---------- escudo (item real en la mano izquierda) ----------
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
        if (!p.isUsingItem() || !isMine(p.getUseItem())) p.startUsingItem(InteractionHand.OFF_HAND);
    }

    static void keepShield(ServerPlayer p) {
        if (!isMine(p.getOffhandItem())) purge(p);
        startShield(p);
    }

    static void endShield(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        if (p.isUsingItem() && isMine(p.getUseItem())) p.stopUsingItem();
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
        int sh = d.getInt("pol_shield");
        if (sh > 0) {
            d.putInt("pol_shield", --sh);
            if (sh > 0) keepShield(p);
            if (sh == 0) {
                endShield(p);
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

    @SubscribeEvent
    public void hurt(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || shield(p) <= 0 || !on(p)) return;
        DamageSource src = e.getSource();
        Vec3 from = src.getSourcePosition();
        if (from == null) return;
        Vec3 dir = from.subtract(p.position()).multiply(1, 0, 1);
        if (dir.lengthSqr() < 1.0E-4) return;
        dir = dir.normalize();
        Vec3 look = Vec3.directionFromRotation(0, p.yBodyRot);
        if (look.dot(dir) >= BLOCK_COS) {
            e.setAmount(e.getAmount() * BLOCK_FACTOR);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 0.8f);
        }
    }

    @SubscribeEvent
    public void onXp(PlayerXpEvent.XpChange e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getAmount() <= 0) return;
        int before = unlocked(p);
        p.getPersistentData().putInt("pol_xp", xp(p) + e.getAmount());
        int after = unlocked(p);
        if (after > before) {
            msg(p, "Habilidad desbloqueada: " + SKILLS[after - 1]);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
        }
        sync(p);
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone e) {
        CompoundTag o = e.getOriginal().getPersistentData();
        CompoundTag n = e.getEntity().getPersistentData();
        for (String k : new String[]{"pol_on", "pol_xp", "pol_sel"}) {
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
