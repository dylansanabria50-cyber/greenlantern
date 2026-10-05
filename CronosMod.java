package com.example.cronos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.joml.Vector3f;

import java.util.*;
import java.util.function.Supplier;

/** Cronosapiente: mod propio e independiente con poderes de control del tiempo. */
@Mod("cronos")
public class CronosMod {
    public static final String ID = "cronos";
    public static final SimpleChannel NET = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(ID, "main"))
            .networkProtocolVersion(() -> "1")
            .clientAcceptedVersions(v -> true)
            .serverAcceptedVersions(v -> true)
            .simpleChannel();

    public static final String[] RAYS = {
            "Rayo de tiempo", "Rayo mejorado", "Rayo de detencion / restauracion",
            "Rayo de retroceso", "Rayo de destransformacion", "Remocion de linea temporal",
            "Disparo de energia (10K)", "Rayo de mano (10K)"};
    public static final String[] CUES = {"Ralentizar tiempo", "Acelerar tiempo", "Detener tiempo", "Efecto Sotobro"};

    static final int MAX_ENERGY = 1000;
    static final double RADIUS = 24;
    static final UUID U_ARMOR = UUID.fromString("5d9a7b10-0000-4000-8000-0000c0a0a001");
    static final UUID U_TOUGH = UUID.fromString("5d9a7b10-0000-4000-8000-0000c0a0a002");
    static final UUID U_ATK = UUID.fromString("5d9a7b10-0000-4000-8000-0000c0a0a003");

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
        boolean on, f10k, big;
        int energy, ray, cue, slow, stop, acc;
        Sync() { }
        static void enc(Sync m, FriendlyByteBuf b) {
            b.writeBoolean(m.on); b.writeBoolean(m.f10k); b.writeBoolean(m.big);
            b.writeVarInt(m.energy); b.writeVarInt(m.ray); b.writeVarInt(m.cue);
            b.writeVarInt(m.slow); b.writeVarInt(m.stop); b.writeVarInt(m.acc);
        }
        static Sync dec(FriendlyByteBuf b) {
            Sync m = new Sync();
            m.on = b.readBoolean(); m.f10k = b.readBoolean(); m.big = b.readBoolean();
            m.energy = b.readVarInt(); m.ray = b.readVarInt(); m.cue = b.readVarInt();
            m.slow = b.readVarInt(); m.stop = b.readVarInt(); m.acc = b.readVarInt();
            return m;
        }
        static void handle(Sync m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CronosClient.onSync(m)));
            c.get().setPacketHandled(true);
        }
    }

    // ---------- estado ----------
    static class St {
        long windStart = -1, slowUntil, stopUntil, accUntil, nextFire;
        int ray = 0, cue = 0;
    }

    static final Map<UUID, St> ST = new HashMap<>();
    static final Map<UUID, ArrayDeque<Snap>> HIST = new HashMap<>();
    static final List<Zone> ZONES = new ArrayList<>();

    static class Snap {
        long t; double x, y, z; float yr, xr, hp;
    }

    static class Zone {
        UUID owner; ServerLevel level; Vec3 pos; boolean stop;
    }

    static St st(Player p) { return ST.computeIfAbsent(p.getUUID(), k -> new St()); }
    static boolean on(Player p) { return p.getPersistentData().getBoolean("cr_on"); }
    static boolean f10k(Player p) { return p.getPersistentData().getBoolean("cr_10k"); }
    static boolean big(Player p) { return p.getPersistentData().getBoolean("cr_big"); }
    static int en(Player p) {
        CompoundTag d = p.getPersistentData();
        return d.contains("cr_en") ? d.getInt("cr_en") : MAX_ENERGY;
    }
    static void setEn(Player p, int v) { p.getPersistentData().putInt("cr_en", Math.max(0, Math.min(MAX_ENERGY, v))); }
    static void msg(ServerPlayer p, String s) { p.displayClientMessage(Component.literal(s), true); }

    public CronosMod() {
        NET.registerMessage(0, Act.class, Act::enc, Act::dec, Act::handle);
        NET.registerMessage(1, Sync.class, Sync::enc, Sync::dec, Sync::handle);
        MinecraftForge.EVENT_BUS.register(this);
        if (FMLEnvironment.dist.isClient()) {
            CronosClient.init();
        }
    }

    static void sync(ServerPlayer p) {
        St s = st(p);
        long now = p.level().getGameTime();
        Sync m = new Sync();
        m.on = on(p); m.f10k = f10k(p); m.big = big(p); m.energy = en(p);
        m.ray = s.ray; m.cue = s.cue;
        m.slow = (int) Math.max(0, s.slowUntil - now);
        m.stop = (int) Math.max(0, s.stopUntil - now);
        m.acc = (int) Math.max(0, s.accUntil - now);
        NET.send(PacketDistributor.PLAYER.with(() -> p), m);
    }

    // ---------- acciones ----------
    static void act(ServerPlayer p, int id) {
        St s = st(p);
        long now = p.level().getGameTime();
        if (id == 0) {
            boolean v = !on(p);
            p.getPersistentData().putBoolean("cr_on", v);
            if (!v) {
                p.getPersistentData().putBoolean("cr_10k", false);
                p.getPersistentData().putBoolean("cr_big", false);
                p.refreshDimensions();
            }
            msg(p, v ? "Cronosapiente activado, senor." : "Forma humana restaurada.");
            ((ServerLevel) p.level()).sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
            sync(p);
            return;
        }
        if (!on(p)) { msg(p, "Active primero la transformacion (V)."); return; }
        switch (id) {
            case 1 -> fire(p, s, now);
            case 2 -> {
                int n = f10k(p) ? RAYS.length : 6;
                s.ray = (s.ray + 1) % n;
                msg(p, RAYS[s.ray]);
            }
            case 3 -> s.windStart = now;
            case 4 -> release(p, s, now);
            case 5 -> {
                s.cue = (s.cue + 1) % CUES.length;
                msg(p, "Cuerda: " + CUES[s.cue]);
            }
            case 6 -> {
                boolean v = !f10k(p);
                p.getPersistentData().putBoolean("cr_10k", v);
                if (!v) { p.getPersistentData().putBoolean("cr_big", false); p.refreshDimensions(); if (s.ray >= 6) s.ray = 0; }
                msg(p, v ? "Forma 10K activada." : "Forma 10K desactivada.");
            }
            case 7 -> {
                if (!f10k(p)) { msg(p, "El cambio de tamano requiere la forma 10K (H)."); break; }
                boolean v = !big(p);
                p.getPersistentData().putBoolean("cr_big", v);
                p.refreshDimensions();
                msg(p, v ? "Tamano aumentado." : "Tamano normal.");
            }
            default -> { }
        }
        sync(p);
    }

    static boolean spend(ServerPlayer p, int cost) {
        if (en(p) < cost) {
            msg(p, "Energia insuficiente (" + en(p) + "/" + MAX_ENERGY + ", se necesitan " + cost + ").");
            return false;
        }
        setEn(p, en(p) - cost);
        return true;
    }

    static float[] color(int ray) {
        return switch (ray) {
            case 0 -> new float[]{0.4f, 1f, 0.2f};
            case 1 -> new float[]{1f, 0.8f, 0.1f};
            case 2 -> new float[]{0.2f, 0.7f, 1f};
            case 3 -> new float[]{0.7f, 0.3f, 1f};
            case 4 -> new float[]{1f, 1f, 1f};
            case 5 -> new float[]{1f, 0.1f, 0.1f};
            default -> new float[]{1f, 0.4f, 0.1f};
        };
    }

    static void fire(ServerPlayer p, St s, long now) {
        if (now < s.nextFire) return;
        int ray = s.ray;
        ServerLevel lv = p.serverLevel();
        int cost;
        switch (ray) {
            case 0 -> cost = 15;
            case 1 -> cost = 30;
            case 2 -> cost = 40;
            case 3 -> cost = 50;
            case 4 -> cost = 40;
            case 5 -> cost = Math.max(100, Math.min(en(p), 400));
            case 6 -> cost = 60;
            default -> cost = 200;
        }
        if (ray == 3 && s.slowUntil <= now) { msg(p, "El retroceso requiere el tiempo ralentizado."); s.nextFire = now + 20; return; }
        if (!spend(p, cost)) { s.nextFire = now + 20; return; }
        s.nextFire = now + 8;

        Vec3 eye = p.getEyePosition();
        Vec3 dir = p.getLookAngle();
        Vec3 end = eye.add(dir.scale(48));
        BlockHitResult bh = lv.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        Vec3 stop = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
        AABB box = p.getBoundingBox().expandTowards(dir.scale(48)).inflate(1);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(lv, p, eye, stop, box,
                e -> e instanceof LivingEntity && e.isPickable() && !e.isSpectator() && e != p);
        LivingEntity t = eh != null ? (LivingEntity) eh.getEntity() : null;
        Vec3 hit = eh != null ? eh.getLocation() : stop;

        float[] c = color(ray);
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(c[0], c[1], c[2]), 1.3f);
        double len = hit.distanceTo(eye);
        for (double d = 1; d < len; d += 0.6) {
            Vec3 q = eye.add(dir.scale(d));
            lv.sendParticles(dust, q.x, q.y - 0.15, q.z, 1, 0, 0, 0, 0);
        }
        lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, hit.x, hit.y, hit.z, 12, 0.3, 0.3, 0.3, 0.1);

        switch (ray) {
            case 0 -> { if (t != null) t.hurt(lv.damageSources().playerAttack(p), 6f); }
            case 1 -> {
                if (t != null) {
                    t.hurt(lv.damageSources().playerAttack(p), 10f);
                    t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 3));
                } else if (bh.getType() == HitResult.Type.BLOCK) {
                    accelerateBlocks(lv, bh.getBlockPos());
                }
            }
            case 2 -> {
                if (t != null) {
                    if (p.isShiftKeyDown()) restore(t); else freeze(t, 200, now);
                }
            }
            case 3 -> { if (t != null) rollback(t); }
            case 4 -> { if (t != null) detransform(t); }
            case 5 -> { if (t != null) removeFromTimeline(p, t, cost); }
            case 6 -> {
                lv.explode(p, hit.x, hit.y, hit.z, 2.5f, Level.ExplosionInteraction.NONE);
                if (t != null) t.hurt(lv.damageSources().playerAttack(p), 14f);
            }
            default -> { if (t != null) removeFromTimeline(p, t, 200); }
        }
    }

    static void accelerateBlocks(ServerLevel lv, BlockPos c) {
        for (BlockPos pos : BlockPos.betweenClosed(c.offset(-2, -1, -2), c.offset(2, 1, 2))) {
            BlockState bs = lv.getBlockState(pos);
            BlockEntity be = lv.getBlockEntity(pos);
            if (be instanceof AbstractFurnaceBlockEntity f) {
                for (int i = 0; i < 80; i++) AbstractFurnaceBlockEntity.serverTick(lv, pos.immutable(), lv.getBlockState(pos), f);
            } else if (bs.isRandomlyTicking()) {
                for (int i = 0; i < 25; i++) {
                    BlockState cur = lv.getBlockState(pos);
                    if (cur.isRandomlyTicking()) cur.randomTick(lv, pos.immutable(), lv.random);
                }
            }
        }
        lv.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5, 15, 1, 0.5, 1, 0);
    }

    static void freeze(LivingEntity t, int ticks, long now) {
        t.getPersistentData().putLong("cr_freeze", now + ticks);
    }

    static boolean frozen(Entity e) {
        long u = e.getPersistentData().getLong("cr_freeze");
        return u > e.level().getGameTime();
    }

    static void restore(LivingEntity t) {
        t.getPersistentData().remove("cr_freeze");
        if (t instanceof AgeableMob a && a.getAge() < 0) a.setAge(0);
        if (t instanceof ServerPlayer sp) { setEn(sp, MAX_ENERGY); sync(sp); }
        if (t.level() instanceof ServerLevel sl)
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, t.getX(), t.getY() + 1, t.getZ(), 15, 0.4, 0.6, 0.4, 0);
    }

    static void rollback(LivingEntity t) {
        t.getPersistentData().remove("cr_freeze");
        t.setHealth(t.getMaxHealth());
        for (MobEffectInstance e : new ArrayList<>(t.getActiveEffects())) {
            if (!e.getEffect().isBeneficial()) t.removeEffect(e.getEffect());
        }
        if (t instanceof AgeableMob a && a.getAge() >= 0 && !(t instanceof Player)) a.setAge(-24000);
    }

    static void detransform(LivingEntity t) {
        if (t instanceof ServerPlayer sp) {
            if (on(sp)) {
                sp.getPersistentData().putBoolean("cr_on", false);
                sp.getPersistentData().putBoolean("cr_10k", false);
                sp.getPersistentData().putBoolean("cr_big", false);
                sp.refreshDimensions();
                msg(sp, "Ha sido destransformado.");
                sync(sp);
            } else {
                sp.removeAllEffects();
            }
        }
    }

    static void removeFromTimeline(ServerPlayer owner, LivingEntity t, int cost) {
        if (t instanceof ServerPlayer sp) {
            ServerLevel ow = sp.server.overworld();
            BlockPos sp0 = ow.getSharedSpawnPos();
            sp.teleportTo(ow, sp0.getX() + 0.5, sp0.getY() + 1, sp0.getZ() + 0.5, sp.getYRot(), sp.getXRot());
            msg(sp, "Ha sido removido de la linea temporal.");
        } else {
            t.hurt(owner.serverLevel().damageSources().playerAttack(owner), cost / 4f);
        }
    }

    static void release(ServerPlayer p, St s, long now) {
        if (s.windStart < 0) return;
        int dur = (int) Math.min(400, now - s.windStart);
        s.windStart = -1;
        if (dur < 10) { msg(p, "Mantenga la tecla para dar cuerda (C)."); return; }
        boolean slowActive = s.slowUntil > now;
        if (p.isShiftKeyDown() && s.cue == 0 && slowActive) {
            if (!spend(p, 150)) return;
            for (Player o : p.level().getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(RADIUS), x -> x != p)) {
                if (o instanceof ServerPlayer sp) detransform(sp);
            }
            msg(p, "Destransformacion en area.");
            return;
        }
        int cost = 100 + dur / 2;
        if (!spend(p, cost)) return;
        ServerLevel lv = p.serverLevel();
        switch (s.cue) {
            case 0 -> { s.slowUntil = now + Math.max(60, dur * 3L); msg(p, "Tiempo ralentizado."); }
            case 1 -> { s.accUntil = now + Math.max(60, dur * 3L); msg(p, "Tiempo acelerado."); }
            case 2 -> { s.stopUntil = now + Math.max(40, Math.min(600, dur * 3L / 2)); msg(p, "Tiempo detenido."); }
            default -> { rewind(p, Math.min(200, dur)); msg(p, "Efecto Sotobro."); }
        }
        lv.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 60, 1, 1, 1, 0.2);
    }

    static void rewind(ServerPlayer owner, int back) {
        ServerLevel lv = owner.serverLevel();
        long now = lv.getGameTime();
        for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(RADIUS), x -> x != owner)) {
            ArrayDeque<Snap> q = HIST.get(e.getUUID());
            if (q == null || q.isEmpty()) continue;
            Snap pick = q.peekFirst();
            for (Iterator<Snap> it = q.descendingIterator(); it.hasNext(); ) {
                Snap sn = it.next();
                if (sn.t <= now - back) { pick = sn; break; }
            }
            if (e instanceof ServerPlayer sp) sp.connection.teleport(pick.x, pick.y, pick.z, pick.yr, pick.xr);
            else e.moveTo(pick.x, pick.y, pick.z, pick.yr, pick.xr);
            e.setHealth(Math.max(1f, Math.min(e.getMaxHealth(), pick.hp)));
            e.setDeltaMovement(Vec3.ZERO);
            lv.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + 1, e.getZ(), 15, 0.4, 0.6, 0.4, 0.1);
        }
    }

    // ---------- tick ----------
    static void attr(Player p, Attribute a, UUID id, double v, boolean add) {
        AttributeInstance ai = p.getAttribute(a);
        if (ai == null) return;
        boolean has = ai.getModifier(id) != null;
        if (add && !has) ai.addTransientModifier(new AttributeModifier(id, "cronos", v, AttributeModifier.Operation.ADDITION));
        else if (!add && has) ai.removeModifier(id);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        boolean on = on(p);
        attr(p, Attributes.ARMOR, U_ARMOR, 20, on);
        attr(p, Attributes.ARMOR_TOUGHNESS, U_TOUGH, 10, on);
        attr(p, Attributes.ATTACK_DAMAGE, U_ATK, 7, on);
        long now = p.level().getGameTime();
        setEn(p, en(p) + 1);
        if (on) {
            if (p.isOnFire()) p.clearFire();
            if (now % 10 == 0 && p.getHealth() < p.getMaxHealth()) p.heal(0.5f);
            St s = st(p);
            if (s.windStart >= 0 && now % 4 == 0) {
                ((ServerLevel) p.level()).sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1.2, p.getZ(), 3, 0.4, 0.4, 0.4, 0.5);
            }
            // historial para el efecto Sotobro
            for (LivingEntity le : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(RADIUS))) {
                ArrayDeque<Snap> q = HIST.computeIfAbsent(le.getUUID(), k -> new ArrayDeque<>());
                if (!q.isEmpty() && q.peekLast().t == now) continue;
                Snap sn = new Snap();
                sn.t = now; sn.x = le.getX(); sn.y = le.getY(); sn.z = le.getZ();
                sn.yr = le.getYRot(); sn.xr = le.getXRot(); sn.hp = le.getHealth();
                q.addLast(sn);
                if (q.size() > 220) q.pollFirst();
            }
        }
        if (now % 4 == 0) sync(p);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        ZONES.clear();
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            St s = st(p);
            long now = p.level().getGameTime();
            boolean slow = s.slowUntil > now, stop = s.stopUntil > now, acc = s.accUntil > now;
            if (acc) {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 5, 2, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 5, 2, false, false));
                ServerLevel sl = p.serverLevel();
                sl.setDayTime(sl.getDayTime() + 19);
            }
            if (stop) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 5, 1, false, false));
            if (slow || stop) {
                Zone z = new Zone();
                z.owner = p.getUUID(); z.level = p.serverLevel(); z.pos = p.position(); z.stop = stop;
                ZONES.add(z);
                for (Entity o : p.level().getEntities(p, p.getBoundingBox().inflate(RADIUS))) {
                    if (o instanceof Player op) {
                        applyFreezeFx(op, stop ? 10 : 3);
                    } else if (!(o instanceof LivingEntity)) {
                        if (stop) { o.setDeltaMovement(Vec3.ZERO); o.hurtMarked = true; }
                        else { o.setDeltaMovement(o.getDeltaMovement().scale(0.75)); o.hurtMarked = true; }
                    }
                }
            }
        }
        // jugadores congelados por rayo
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            if (frozen(p)) applyFreezeFx(p, 10);
        }
        if (e.getServer().getTickCount() % 1200 == 0) {
            HIST.entrySet().removeIf(en -> {
                for (ServerLevel l : e.getServer().getAllLevels()) if (l.getEntity(en.getKey()) != null) return false;
                return true;
            });
        }
    }

    static void applyFreezeFx(Player p, int amp) {
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, amp, false, false));
        if (amp >= 10) {
            p.addEffect(new MobEffectInstance(MobEffects.JUMP, 5, 128, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 5, 4, false, false));
        }
    }

    @SubscribeEvent
    public void onLivingTick(LivingEvent.LivingTickEvent e) {
        LivingEntity le = e.getEntity();
        if (le.level().isClientSide || le instanceof Player) return;
        if (frozen(le)) { e.setCanceled(true); return; }
        if (ZONES.isEmpty()) return;
        for (Zone z : ZONES) {
            if (z.level != le.level() || z.owner.equals(le.getUUID())) continue;
            if (le.position().distanceToSqr(z.pos) > RADIUS * RADIUS) continue;
            if (z.stop || le.tickCount % 4 != 0) { e.setCanceled(true); return; }
        }
    }

    @SubscribeEvent
    public void onAttack(LivingAttackEvent e) {
        if (e.getEntity() instanceof Player p && on(p) && e.getSource().is(DamageTypeTags.IS_FIRE)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onSize(EntityEvent.Size e) {
        if (e.getEntity() instanceof Player p && big(p)) {
            e.setNewSize(e.getNewSize().scale(1.6f), true);
        }
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone e) {
        CompoundTag o = e.getOriginal().getPersistentData();
        CompoundTag n = e.getEntity().getPersistentData();
        for (String k : new String[]{"cr_on", "cr_10k", "cr_en"}) {
            if (o.contains(k)) n.put(k, o.get(k).copy());
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        ST.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) { sp.refreshDimensions(); sync(sp); }
    }
}
