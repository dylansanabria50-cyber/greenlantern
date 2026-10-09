package com.example.policia;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Oficio LADRON: carterismo (Mayus + clic derecho por la espalda), Suerte (probabilidad) y Silbido (distraer). */
public class PoliciaLadron {
    /** Probabilidad de exito del carterismo (%) segun el nivel de Suerte (0 a 4). Cada nivel reemplaza al anterior. */
    public static final int[] CHANCE = {30, 40, 45, 50, 55};
    static final int WHISTLE_CD = 300;   // 15 s
    static final int DIS_T = 140;        // 7 s
    static final int ATT_CD = 40;        // 2 s entre intentos
    static final double WHISTLE_R = 12.0;

    static class Dis {
        int t = DIS_T;
        float yaw;
    }

    static final Map<Mob, Dis> DIS = new WeakHashMap<>();
    static final Map<UUID, SimpleContainer> LOOT = new HashMap<>();

    public static int luck(Player p) { return Math.max(0, Math.min(4, p.getPersistentData().getInt("pol_lsu"))); }

    public static boolean whistle(Player p) { return p.getPersistentData().getInt("pol_lsi") > 0; }

    public static int wcd(Player p) { return p.getPersistentData().getInt("pol_lcd"); }

    /** Habilidades de uso: 0 Silbido, 1 Humo, 2 Salto, 3 Instinto, 4 Polvo. */
    static final String[] AN = {"Silbido", "Humo", "Salto", "Instinto", "Polvo"};
    static final int[] ACD = {300, 600, 240, 500, 400};

    public static boolean has(Player p, int a) {
        if (a == 0) return whistle(p);
        return ((p.getPersistentData().getInt("pol_lun") >> (a - 1)) & 1) == 1;
    }

    public static int cd(Player p, int a) { return p.getPersistentData().getInt(a == 0 ? "pol_lcd" : "pol_lc" + a); }

    public static int sel(Player p) {
        int s = p.getPersistentData().getInt("pol_lse");
        return s < 0 || s > 4 ? 0 : s;
    }

    /** Mascara de lo desbloqueado: bit 0 Silbido, bits 1 a 4 las demas. */
    public static int mask(Player p) { return (whistle(p) ? 1 : 0) | (p.getPersistentData().getInt("pol_lun") << 1); }

    static void init() {
        MinecraftForge.EVENT_BUS.register(new Ev());
    }

    static boolean active(ServerPlayer p) {
        if (!"ladron".equals(PoliciaMod.job(p))) return false;
        if (!PoliciaMod.on(p)) {
            PoliciaMod.msg(p, "Activa el modo ladron (B) primero");
            return false;
        }
        return true;
    }

    /** Acciones que llegan desde el teclado o desde la pantalla de la rama. */
    static void act(ServerPlayer p, int id) {
        net.minecraft.nbt.CompoundTag d = p.getPersistentData();
        if (id == 1) { // J: usar la habilidad elegida
            if (!active(p)) return;
            int a = sel(p);
            if (!has(p, a)) { PoliciaMod.msg(p, "Desbloquea primero " + AN[a] + " en la rama de habilidades (K cambia de habilidad)"); return; }
            if (cd(p, a) > 0) { PoliciaMod.msg(p, AN[a] + " en enfriamiento: " + (cd(p, a) + 19) / 20 + " s"); return; }
            use(p, a);
            return;
        }
        if (id == 2) { // K: cambiar de habilidad
            int s0 = sel(p);
            for (int i = 1; i <= 5; i++) {
                int a = (s0 + i) % 5;
                if (has(p, a)) {
                    d.putInt("pol_lse", a);
                    PoliciaMod.msg(p, "Habilidad elegida: " + AN[a]);
                    PoliciaMod.sync(p);
                    return;
                }
            }
            PoliciaMod.msg(p, "Todavia no tienes habilidades que elegir");
            return;
        }
        if (id >= 82 && id <= 85) { // desbloquear Humo, Salto, Instinto o Polvo
            int a = id - 81;
            if (has(p, a)) return;
            if (a <= 2 && !whistle(p)) { PoliciaMod.msg(p, "Primero desbloquea el Silbido"); return; }
            if (a >= 3 && luck(p) < 1) { PoliciaMod.msg(p, "Primero mejora Suerte al nivel 1"); return; }
            int cost = PoliciaMod.XP_PER_NODE * 2;
            if (PoliciaMod.xp(p) < cost) { PoliciaMod.msg(p, "Necesitas " + cost + " XP para " + AN[a] + " (tienes " + PoliciaMod.xp(p) + ")"); return; }
            d.putInt("pol_xp", PoliciaMod.xp(p) - cost);
            d.putInt("pol_lun", d.getInt("pol_lun") | (1 << (a - 1)));
            d.putInt("pol_lse", a);
            PoliciaMod.msg(p, "Habilidad desbloqueada: " + AN[a] + " (elegida; K cambia, J usa)");
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.3f);
            PoliciaMod.sync(p);
            return;
        }
        if (id == 80) { // mejorar Suerte
            int lv = luck(p);
            if (lv >= 4) { PoliciaMod.msg(p, "Suerte ya esta al nivel maximo (" + CHANCE[4] + "%)"); return; }
            int cost = PoliciaMod.XP_PER_NODE * (lv + 1);
            if (PoliciaMod.xp(p) < cost) { PoliciaMod.msg(p, "Necesitas " + cost + " XP para Suerte " + (lv + 1) + " (tienes " + PoliciaMod.xp(p) + ")"); return; }
            d.putInt("pol_xp", PoliciaMod.xp(p) - cost);
            d.putInt("pol_lsu", lv + 1);
            PoliciaMod.msg(p, "Suerte " + (lv + 1) + ": carterismo al " + CHANCE[lv + 1] + "%");
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.4f);
            PoliciaMod.sync(p);
            return;
        }
        if (id == 81) { // desbloquear Silbido
            if (whistle(p)) return;
            if (PoliciaMod.xp(p) < PoliciaMod.XP_PER_NODE) { PoliciaMod.msg(p, "Necesitas " + PoliciaMod.XP_PER_NODE + " XP para desbloquear el Silbido"); return; }
            d.putInt("pol_xp", PoliciaMod.xp(p) - PoliciaMod.XP_PER_NODE);
            d.putInt("pol_lsi", 1);
            PoliciaMod.msg(p, "Habilidad desbloqueada: Silbido");
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
            PoliciaMod.sync(p);
        }
    }

    static void use(ServerPlayer p, int a) {
        ServerLevel sl = p.serverLevel();
        if (a == 0) { whistleUse(p); return; }
        if (a == 1) { // humo: invisible y veloz, los mobs te pierden
            p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 160, 0, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 160, 1, false, false));
            for (Mob m : sl.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16.0), x -> x.getTarget() == p)) m.setTarget(null);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1.0, p.getZ(), 40, 0.5, 0.8, 0.5, 0.02);
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
            PoliciaMod.msg(p, "Humo: invisible 8 s");
        } else if (a == 2) { // salto de sombra
            Vec3 l = p.getLookAngle();
            Vec3 h = new Vec3(l.x, 0.0, l.z);
            if (h.lengthSqr() < 1.0E-4) h = new Vec3(0.0, 0.0, 1.0);
            h = h.normalize().scale(1.7);
            p.setDeltaMovement(h.x, 0.55, h.z);
            p.hurtMarked = true;
            p.fallDistance = 0.0f;
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false));
            sl.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), 15, 0.3, 0.1, 0.3, 0.05);
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.2f);
        } else if (a == 3) { // instinto: ver a todos brillando
            int n = 0;
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24.0), x -> x != p && x.isAlive())) {
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
                n++;
            }
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 0.6f);
            PoliciaMod.msg(p, "Instinto: " + n + (n == 1 ? " ser cerca" : " seres cerca") + " (brillan 10 s)");
        } else { // polvo cegador
            int n = 0;
            for (Mob m : sl.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(6.0), x -> x.isAlive() && !(x instanceof AbstractVillager))) {
                m.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 140, 0, false, false));
                m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 140, 2, false, false));
                m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 140, 1, false, false));
                m.setTarget(null);
                n++;
            }
            sl.sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 1.0, p.getZ(), 30, 1.5, 0.5, 1.5, 0.02);
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.2f, 0.8f);
            PoliciaMod.msg(p, "Polvo cegador: " + n + (n == 1 ? " objetivo afectado" : " objetivos afectados"));
        }
        p.getPersistentData().putInt("pol_lc" + a, ACD[a]);
        PoliciaMod.sync(p);
    }

    static void whistleUse(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        int n = 0;
        for (Mob m : sl.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(WHISTLE_R), x -> x.isAlive())) {
            double dx = m.getX() - p.getX(), dz = m.getZ() - p.getZ();
            Dis ds = new Dis();
            ds.yaw = (float) (Mth.atan2(-dx, dz) * 57.29577951308232);
            DIS.put(m, ds);
            face(m, ds.yaw);
            m.setTarget(null);
            m.getNavigation().stop();
            m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DIS_T, 3, false, false));
            n++;
        }
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 1.2f, 1.6f);
        sl.sendParticles(ParticleTypes.NOTE, p.getX(), p.getY() + 2.1, p.getZ(), 5, 0.4, 0.2, 0.4, 1.0);
        p.getPersistentData().putInt("pol_lcd", WHISTLE_CD);
        PoliciaMod.msg(p, n > 0 ? "Silbido: " + n + (n == 1 ? " objetivo mira" : " objetivos miran") + " para otro lado" : "Silbido: no habia nadie cerca");
        PoliciaMod.sync(p);
    }

    static void face(Mob m, float yaw) {
        m.setYRot(yaw);
        m.setYHeadRot(yaw);
        m.yBodyRot = yaw;
        m.yRotO = yaw;
    }

    /** Genera (una sola vez) lo que lleva encima el objetivo. */
    static SimpleContainer pockets(ServerLevel sl, LivingEntity t, ServerPlayer thief) {
        SimpleContainer c = LOOT.get(t.getUUID());
        if (c != null) return c;
        c = new SimpleContainer(27);
        List<ItemStack> items = new ArrayList<>();
        try {
            LootTable lt = sl.getServer().getLootData().getLootTable(t.getType().getDefaultLootTable());
            LootParams lp = new LootParams.Builder(sl)
                    .withParameter(LootContextParams.THIS_ENTITY, t)
                    .withParameter(LootContextParams.ORIGIN, t.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, sl.damageSources().generic())
                    .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, thief)
                    .create(LootContextParamSets.ENTITY);
            items.addAll(lt.getRandomItems(lp));
        } catch (RuntimeException ex) {
            // objetivo sin tabla de botin
        }
        if (t instanceof AbstractVillager) {
            items.add(new ItemStack(Items.EMERALD, 1 + sl.random.nextInt(4)));
            if (sl.random.nextInt(3) == 0) items.add(new ItemStack(Items.BREAD, 1 + sl.random.nextInt(2)));
        }
        for (ItemStack s : items) {
            if (!s.isEmpty()) c.addItem(s);
        }
        LOOT.put(t.getUUID(), c);
        return c;
    }

    static void pick(ServerPlayer p, LivingEntity t) {
        if (!active(p)) return;
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        if (now - p.getPersistentData().getLong("pol_latt") < ATT_CD) return;
        p.getPersistentData().putLong("pol_latt", now);
        // debe estar detras del objetivo
        Vec3 look = t.getLookAngle();
        Vec3 to = new Vec3(p.getX() - t.getX(), 0.0, p.getZ() - t.getZ());
        double len = Math.sqrt(to.x * to.x + to.z * to.z);
        double hl = Math.sqrt(look.x * look.x + look.z * look.z);
        if (len < 1.0E-4 || hl < 1.0E-4) return;
        double dot = (look.x * to.x + look.z * to.z) / (hl * len);
        boolean dist = t instanceof Mob dm && DIS.containsKey(dm);
        if (dot > (dist ? 0.4 : -0.35)) {
            PoliciaMod.msg(p, "Ponte detras de tu objetivo (silbido ayuda)");
            return;
        }
        if (p.distanceToSqr(t) > 9.0) return;
        int chance = CHANCE[luck(p)];
        if (p.getRandom().nextInt(100) >= chance) {
            PoliciaMod.msg(p, "Te descubrieron! (" + chance + "% de exito)");
            sl.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0f, 1.0f);
            if (t instanceof ServerPlayer victim) {
                PoliciaMod.msg(victim, "Alguien intento robarte!");
            } else if (t instanceof Mob fm && !(t instanceof AbstractVillager)) {
                fm.setTarget(p);
            }
            return;
        }
        Component title = Component.literal("Bolsillos de " + t.getName().getString());
        if (t instanceof Player vp) {
            p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new ChestMenu(MenuType.GENERIC_9x4, id, inv, vp.getInventory(), 4), title));
        } else {
            SimpleContainer c = pockets(sl, t, p);
            if (c.isEmpty()) {
                PoliciaMod.msg(p, "No lleva nada encima");
                return;
            }
            p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new ChestMenu(MenuType.GENERIC_9x3, id, inv, c, 3), title));
        }
        p.getPersistentData().putInt("pol_xp", PoliciaMod.xp(p) + 2);
        PoliciaMod.msg(p, "Carterismo exitoso (" + chance + "%)");
        PoliciaMod.sync(p);
    }

    public static class Ev {
        @SubscribeEvent
        public void lEntity(PlayerInteractEvent.EntityInteract e) {
            if (e.getLevel().isClientSide || e.getHand() != InteractionHand.MAIN_HAND) return;
            if (!(e.getEntity() instanceof ServerPlayer p) || !p.isShiftKeyDown()) return;
            if (!"ladron".equals(PoliciaMod.job(p)) || !PoliciaMod.on(p)) return;
            if (!(e.getTarget() instanceof LivingEntity t) || t == p) return;
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            pick(p, t);
        }

        @SubscribeEvent
        public void lTick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv == null) return;
            if (!DIS.isEmpty()) {
                for (Map.Entry<Mob, Dis> en : new ArrayList<>(DIS.entrySet())) {
                    Mob m = en.getKey();
                    Dis ds = en.getValue();
                    if (m == null || !m.isAlive() || --ds.t <= 0) {
                        DIS.remove(m);
                        continue;
                    }
                    if (ds.t % 3 == 0) {
                        if (m.getTarget() instanceof Player) m.setTarget(null);
                        face(m, ds.yaw);
                    }
                }
            }
            for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                boolean sy = false;
                for (int a = 0; a < 5; a++) {
                    String key = a == 0 ? "pol_lcd" : "pol_lc" + a;
                    int c = p.getPersistentData().getInt(key);
                    if (c > 0) {
                        p.getPersistentData().putInt(key, c - 1);
                        if (c == 1 || c % 20 == 0) sy = true;
                    }
                }
                if (sy) PoliciaMod.sync(p);
            }
        }

        @SubscribeEvent
        public void lDrops(LivingDropsEvent e) {
            SimpleContainer c = LOOT.remove(e.getEntity().getUUID());
            if (c == null) return;
            e.getDrops().clear();
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty()) {
                    e.getDrops().add(new ItemEntity(e.getEntity().level(), e.getEntity().getX(), e.getEntity().getY(), e.getEntity().getZ(), s));
                }
            }
        }
    }
}
