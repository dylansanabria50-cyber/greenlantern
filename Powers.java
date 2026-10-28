package com.example.greenlantern;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Poderes nuevos del anillo:
 * rayo del anillo (V), cargar el anillo / onda de voluntad (B), burbuja de voluntad (mantener Z) y su pulso (X).
 * Tambien enciende el contorno verde (brillo) mientras se vuela con el traje.
 */
@Mod.EventBusSubscriber(modid = GreenLanternMod.MODID)
public final class Powers {
    public static final int COST_BEAM = 8, COST_WAVE = 20, COST_BUBBLE = 5;
    public static final int BEAM_CHARGE = 8, WAVE_CHARGE = 50;
    public static final double BUBBLE_R = 3.0;

    /** Tipos de efecto que se mandan al cliente. */
    public static final int FX_BEAM = 1, FX_WAVE_START = 2, FX_WAVE_BURST = 3, FX_BUBBLE_HIT = 4, FX_BUBBLE_BREAK = 5, FX_PULSE = 6, FX_BEAM_CHARGE = 7, FX_BIG_BEAM = 8;

    private static final Map<UUID, Integer> BEAM = new HashMap<>();
    private static final Map<UUID, Integer> WAVE = new HashMap<>();
    private static final Map<UUID, Long> BEAM_READY = new HashMap<>();

    static final class Bubble {
        boolean on;
        float res = 100f;
        long lastHit = -1000, readyAt = 0, lastSent = -1;
        int sentRes = -1;
        boolean sentOn;
    }
    private static final Map<UUID, Bubble> BUBBLES = new HashMap<>();

    private Powers() { }

    // ---------- entrada desde las teclas ----------
    public static void beam(ServerPlayer p) {
        if (!RingPowers.hasRing(p) || BEAM.containsKey(p.getUUID())) return;
        long now = p.level().getGameTime();
        if (now < BEAM_READY.getOrDefault(p.getUUID(), 0L)) return;
        if (!RingPowers.spend(p, COST_BEAM)) return;
        BEAM.put(p.getUUID(), BEAM_CHARGE);
        BEAM_READY.put(p.getUUID(), now + BEAM_CHARGE + 12);
        fx(p, FX_BEAM_CHARGE, hand(p), Vec3.ZERO);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7f, 1.9f);
    }

    public static void wave(ServerPlayer p) {
        if (!RingPowers.hasRing(p) || WAVE.containsKey(p.getUUID())) return;
        if (!RingPowers.spend(p, COST_WAVE)) return;
        WAVE.put(p.getUUID(), WAVE_CHARGE);
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, WAVE_CHARGE + 4, 3, false, false, false));
        fx(p, FX_WAVE_START, p.position(), Vec3.ZERO);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.3f);
        p.displayClientMessage(Component.literal("§aCargando el anillo... §7[Voluntad " + RingPowers.getEnergy(p) + "/" + RingPowers.MAX_ENERGY + "]"), true);
    }

    public static void bubble(ServerPlayer p, boolean on) {
        Bubble b = BUBBLES.computeIfAbsent(p.getUUID(), k -> new Bubble());
        long now = p.level().getGameTime();
        if (on) {
            if (b.on || !RingPowers.hasRing(p)) return;
            if (now < b.readyAt) {
                p.displayClientMessage(Component.literal("§cLa burbuja se esta recargando"), true);
                return;
            }
            if (!RingPowers.spend(p, COST_BUBBLE)) return;
            b.on = true;
            p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.7f);
        } else {
            if (!b.on) return;
            b.on = false;
            p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 1.6f);
        }
        sendBubble(p, b, true);
    }

    public static void pulse(ServerPlayer p) {
        Bubble b = BUBBLES.get(p.getUUID());
        if (b == null || !b.on) { p.displayClientMessage(Component.literal("§cPrimero levanta la burbuja (mantene Z)"), true); return; }
        if (b.res < 40f) { p.displayClientMessage(Component.literal("§cA la burbuja no le alcanza la resistencia para el pulso"), true); return; }
        b.res -= 40f;
        push(p, 6.0, 2.0, 0.6, 3.0f);
        fx(p, FX_PULSE, p.position(), Vec3.ZERO);
        ring(p.serverLevel(), p.position().add(0, 0.4, 0), 64, 0.9);
        p.level().playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.5f, 1.8f);
        sendBubble(p, b, true);
    }

    public static boolean bubbleOn(Player p) {
        Bubble b = BUBBLES.get(p.getUUID());
        return b != null && b.on;
    }

    // ---------- cada tick ----------
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side != LogicalSide.SERVER || !(e.player instanceof ServerPlayer p)) return;
        ServerLevel level = p.serverLevel();
        UUID id = p.getUUID();

        // rayo: la piedra junta energia en espiral y despues dispara
        Integer b = BEAM.get(id);
        if (b != null) {
            Vec3 h = hand(p);
            for (int i = 0; i < 4; i++) {
                double a = (p.tickCount * 0.9 + i * Math.PI / 2), r = 0.25 + b * 0.06;
                level.sendParticles(RingPowers.GREEN, h.x + Math.cos(a) * r, h.y + Math.sin(a) * r * 0.6, h.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            if (b <= 1) { BEAM.remove(id); fireBeam(p); } else BEAM.put(id, b - 1);
        }

        // cargar el anillo: columna de luz y al final la onda
        Integer w = WAVE.get(id);
        if (w != null) {
            double t = WAVE_CHARGE - w;
            for (int i = 0; i < 3; i++) {
                double a = p.tickCount * 0.35 + i * Math.PI * 2 / 3, r = Math.max(0.4, 1.6 - t * 0.025);
                level.sendParticles(RingPowers.GREEN, p.getX() + Math.cos(a) * r, p.getY() + (t * 0.09 + i * 0.7) % 3.6, p.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            if (p.tickCount % 2 == 0) level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 2.2, p.getZ(), 1, 0.1, 0.6, 0.1, 0.05);
            if (w <= 1) { WAVE.remove(id); burst(p); } else WAVE.put(id, w - 1);
        }

        // burbuja
        Bubble bu = BUBBLES.get(id);
        if (bu != null) {
            long now = level.getGameTime();
            if (bu.on) {
                if (!RingPowers.hasRing(p)) bubble(p, false);
                else {
                    if (p.tickCount % 40 == 0) {
                        int en = RingPowers.getEnergy(p);
                        if (en <= 0) { bubble(p, false); p.displayClientMessage(Component.literal("§cSin voluntad: la burbuja se apaga"), true); }
                        else RingPowers.setEnergy(p, en - 1);
                    }
                    if (bu.on) guard(p, bu, now);
                }
            }
            if (now - bu.lastHit > 40 && bu.res < 100f) bu.res = Math.min(100f, bu.res + 1.5f);
            sendBubble(p, bu, false);
        }

        // contorno verde mientras vuela con el traje
        CompoundTag d = RingPowers.data(p);
        boolean glow = RingPowers.isSuitOn(p) && RingPowers.isFlying(p) && p.getAbilities().flying && !p.onGround();
        if (glow && !p.hasGlowingTag()) { p.setGlowingTag(true); d.putBoolean("GLGlow", true); }
        else if (!glow && d.getBoolean("GLGlow")) { p.setGlowingTag(false); d.remove("GLGlow"); }
    }

    /** Lo que hace la burbuja cada tick: devuelve proyectiles y despide a quien la toca. */
    private static void guard(ServerPlayer p, Bubble bu, long now) {
        Vec3 c = p.position();
        AABB box = new AABB(c, c).inflate(BUBBLE_R + 1.5);
        for (Projectile pr : p.level().getEntitiesOfClass(Projectile.class, box)) {
            if (pr.getOwner() == p) continue;
            Vec3 v = pr.getDeltaMovement();
            Vec3 rel = pr.position().subtract(c.add(0, 0.6, 0));
            if (rel.length() > BUBBLE_R + 0.4 || rel.length() < BUBBLE_R - 1.2 || v.dot(rel) >= 0) continue;
            Entity owner = pr.getOwner();
            Vec3 nv = owner != null ? owner.getEyePosition().subtract(pr.position()).normalize().scale(Math.max(0.6, v.length())) : v.scale(-1);
            pr.setDeltaMovement(nv);
            pr.setOwner(p);
            pr.hurtMarked = true;
            hit(p, bu, now, pr.position(), 8f);
        }
        for (LivingEntity le : p.level().getEntitiesOfClass(LivingEntity.class, box, x -> x != p && x.isAlive() && !x.isSpectator() && !x.isPassengerOfSameVehicle(p))) {
            Vec3 rel = le.position().subtract(c);
            double dist = Math.sqrt(rel.x * rel.x + rel.z * rel.z);
            if (dist > BUBBLE_R + 0.4 || le.getY() > c.y + BUBBLE_R + 0.5) continue;
            Vec3 dir = dist < 0.01 ? new Vec3(1, 0, 0) : new Vec3(rel.x / dist, 0, rel.z / dist);
            le.setDeltaMovement(le.getDeltaMovement().add(dir.x * 1.1, 0.35, dir.z * 1.1));
            le.hurtMarked = true;
            if (le.invulnerableTime <= 0) hit(p, bu, now, c.add(dir.scale(BUBBLE_R)).add(0, 1, 0), 3f);
            le.invulnerableTime = Math.max(le.invulnerableTime, 10);
        }
    }

    private static void hit(ServerPlayer p, Bubble bu, long now, Vec3 at, float dmg) {
        bu.res -= dmg;
        bu.lastHit = now;
        fx(p, FX_BUBBLE_HIT, at, Vec3.ZERO);
        p.level().playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0f, 1.4f);
        if (bu.res <= 0f) breakBubble(p, bu, now);
        else sendBubble(p, bu, true);
    }

    private static void breakBubble(ServerPlayer p, Bubble bu, long now) {
        bu.on = false;
        bu.res = 0f;
        bu.readyAt = now + 80;
        push(p, 5.0, 1.4, 0.5, 2.0f);
        fx(p, FX_BUBBLE_BREAK, p.position(), Vec3.ZERO);
        ring(p.serverLevel(), p.position().add(0, 0.4, 0), 48, 0.7);
        p.level().playSound(null, p.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2f, 0.7f);
        p.displayClientMessage(Component.literal("§cLa burbuja se rompio: el anillo se recarga en 4 segundos"), true);
        sendBubble(p, bu, true);
    }

    /** Mientras la burbuja esta levantada no entra el daño: lo absorbe la resistencia. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Bubble bu = BUBBLES.get(p.getUUID());
        if (bu == null || !bu.on) return;
        if (e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) || e.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FALL)
                || e.getSource().is(net.minecraft.tags.DamageTypeTags.IS_DROWNING) || e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)) return;
        e.setCanceled(true);
        Vec3 from = e.getSource().getSourcePosition();
        Vec3 c = p.position().add(0, 1, 0);
        Vec3 at = from == null ? c.add(0, 1.5, 0) : c.add(from.subtract(c).normalize().scale(BUBBLE_R));
        hit(p, bu, p.level().getGameTime(), at, e.getAmount() * 4f);
    }

    private static void fireBeam(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle(), end = eye.add(look.scale(32));
        BlockHitResult bh = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (bh.getType() != HitResult.Type.MISS) end = bh.getLocation();
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, p, eye, end, p.getBoundingBox().expandTowards(look.scale(32)).inflate(1.0),
                x -> x instanceof LivingEntity && x != p && !x.isSpectator() && x.isPickable());
        if (eh != null) {
            end = eh.getLocation();
            LivingEntity t = (LivingEntity) eh.getEntity();
            t.hurt(p.damageSources().playerAttack(p), 7.0f);
            t.setDeltaMovement(t.getDeltaMovement().add(look.x * 1.4, 0.35, look.z * 1.4));
            t.hurtMarked = true;
        }
        Vec3 h = hand(p);
        double len = h.distanceTo(end);
        for (double s = 0; s < len; s += 0.5) {
            Vec3 q = h.add(end.subtract(h).scale(s / len));
            level.sendParticles(RingPowers.GREEN, q.x, q.y, q.z, 1, 0.03, 0.03, 0.03, 0);
        }
        level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 14, 0.2, 0.2, 0.2, 0.08);
        level.sendParticles(RingPowers.GREEN, end.x, end.y, end.z, 20, 0.3, 0.3, 0.3, 0.05);
        fx(p, FX_BEAM, h, end);
        level.playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.9f, 2.0f);
        level.playSound(null, end.x, end.y, end.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    private static void burst(ServerPlayer p) {
        push(p, 7.0, 1.8, 0.6, 5.0f);
        ring(p.serverLevel(), p.position().add(0, 0.3, 0), 72, 1.0);
        p.serverLevel().sendParticles(RingPowers.GREEN, p.getX(), p.getY() + 1.2, p.getZ(), 120, 1.5, 1.0, 1.5, 0.1);
        fx(p, FX_WAVE_BURST, p.position(), Vec3.ZERO);
        p.level().playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7f, 1.5f);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 2.0f);
    }

    /** Empuja (y lastima un poco) a todo lo que esta alrededor. */
    private static void push(ServerPlayer p, double r, double fuerza, double arriba, float dmg) {
        Vec3 c = p.position();
        for (LivingEntity le : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r), x -> x != p && x.isAlive() && !x.isSpectator())) {
            Vec3 rel = le.position().subtract(c);
            double dist = rel.length();
            if (dist > r) continue;
            double k = 1.0 - dist / r * 0.5;
            Vec3 dir = dist < 0.01 ? new Vec3(1, 0, 0) : new Vec3(rel.x, 0, rel.z).normalize();
            le.setDeltaMovement(le.getDeltaMovement().add(dir.x * fuerza * k, arriba, dir.z * fuerza * k));
            le.hurtMarked = true;
            if (dmg > 0) le.hurt(p.damageSources().playerAttack(p), dmg);
        }
    }

    private static void ring(ServerLevel level, Vec3 c, int n, double speed) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            level.sendParticles(ParticleTypes.END_ROD, c.x + Math.cos(a) * 0.6, c.y, c.z + Math.sin(a) * 0.6, 0, Math.cos(a), 0.02, Math.sin(a), speed);
        }
    }

    /** Punto aproximado de la mano derecha (donde esta el anillo). */
    public static Vec3 hand(Player p) {
        float yaw = p.getYRot() * ((float) Math.PI / 180f);
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 look = p.getLookAngle();
        return p.getEyePosition().add(right.scale(0.36)).add(0, -0.45, 0).add(look.scale(0.55));
    }

    private static void fx(ServerPlayer p, int type, Vec3 a, Vec3 b) {
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new ModNetwork.FxPacket(type, p.getId(), a, b));
    }

    private static void sendBubble(ServerPlayer p, Bubble b, boolean force) {
        int res = Math.round(b.res);
        long now = p.level().getGameTime();
        if (!force && b.sentOn == b.on && (b.sentRes == res || now - b.lastSent < 5)) return;
        b.sentOn = b.on; b.sentRes = res; b.lastSent = now;
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new ModNetwork.BubblePacket(p.getId(), b.on, res));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        BEAM.remove(id); WAVE.remove(id); BUBBLES.remove(id); BEAM_READY.remove(id);
        if (e.getEntity().hasGlowingTag() && RingPowers.data(e.getEntity()).getBoolean("GLGlow")) {
            e.getEntity().setGlowingTag(false);
            RingPowers.data(e.getEntity()).remove("GLGlow");
        }
    }
}
