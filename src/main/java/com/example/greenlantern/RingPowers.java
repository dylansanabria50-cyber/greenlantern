package com.example.greenlantern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class RingPowers {
    public static final int MAX_ENERGY = 100;
    public static final int COST_BLAST = 15;
    public static final int COST_SHIELD = 30;
    public static final int COST_WALL = 25;

    public static final int ACT_FLIGHT = 0, ACT_BLAST = 1, ACT_SHIELD = 2, ACT_WALL = 3;

    private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.1f, 1.0f, 0.25f), 1.4f);

    // ---------- datos persistentes (sobreviven a la muerte) ----------
    public static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static int getEnergy(Player p) {
        CompoundTag d = data(p);
        return d.contains("GLEnergy") ? d.getInt("GLEnergy") : MAX_ENERGY;
    }

    public static void setEnergy(Player p, int v) { data(p).putInt("GLEnergy", Math.max(0, Math.min(MAX_ENERGY, v))); }

    public static boolean isFlightOn(Player p) { return data(p).getBoolean("GLFlightOn"); }

    public static void setFlightOn(Player p, boolean v) { data(p).putBoolean("GLFlightOn", v); }

    public static boolean hasRing(Player p) {
        for (ItemStack s : p.getInventory().items) if (s.is(GreenLanternMod.POWER_RING.get())) return true;
        for (ItemStack s : p.getInventory().offhand) if (s.is(GreenLanternMod.POWER_RING.get())) return true;
        return false;
    }

    private static void bar(ServerPlayer p, String msg) {
        p.displayClientMessage(Component.literal("\u00a7a" + msg + " \u00a77[Voluntad " + getEnergy(p) + "/" + MAX_ENERGY + "]"), true);
    }

    // ---------- acciones ----------
    public static void activate(ServerPlayer p, int action) {
        if (!hasRing(p)) {
            p.displayClientMessage(Component.literal("\u00a7cNecesitas el Anillo de Poder en el inventario"), true);
            return;
        }
        long now = p.level().getGameTime();
        CompoundTag d = data(p);
        if (action != ACT_FLIGHT && now < d.getLong("GLCd")) return;
        d.putLong("GLCd", now + 10);

        switch (action) {
            case ACT_FLIGHT -> toggleFlight(p);
            case ACT_BLAST -> blast(p);
            case ACT_SHIELD -> shield(p);
            case ACT_WALL -> wall(p);
            default -> { }
        }
    }

    private static boolean spend(ServerPlayer p, int cost) {
        if (getEnergy(p) < cost) {
            p.displayClientMessage(Component.literal("\u00a7cEl anillo necesita recargarse (Shift + clic derecho)"), true);
            return false;
        }
        setEnergy(p, getEnergy(p) - cost);
        return true;
    }

    private static void toggleFlight(ServerPlayer p) {
        boolean on = !isFlightOn(p);
        if (on && getEnergy(p) <= 0) {
            p.displayClientMessage(Component.literal("\u00a7cSin voluntad suficiente para volar"), true);
            return;
        }
        setFlightOn(p, on);
        bar(p, on ? "Vuelo activado (salta dos veces para volar)" : "Vuelo desactivado");
    }

    public static void blast(ServerPlayer p) {
        if (!spend(p, COST_BLAST)) return;
        ServerLevel level = p.serverLevel();
        double range = 48;
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 end = eye.add(look.scale(range));

        HitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (blockHit.getType() != HitResult.Type.MISS) end = blockHit.getLocation();

        AABB box = p.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, p, eye, end, box,
                e -> !e.isSpectator() && e.isPickable() && e != p);
        if (eh != null) {
            end = eh.getLocation();
            Entity target = eh.getEntity();
            target.hurt(p.damageSources().playerAttack(p), 12.0f);
            if (target instanceof LivingEntity le) le.knockback(1.2, -look.x, -look.z);
        }

        double dist = eye.distanceTo(end);
        for (double t = 1.0; t < dist; t += 0.4) {
            Vec3 pt = eye.add(look.scale(t));
            level.sendParticles(GREEN, pt.x, pt.y - 0.1, pt.z, 1, 0.02, 0.02, 0.02, 0);
        }
        level.sendParticles(GREEN, end.x, end.y, end.z, 25, 0.4, 0.4, 0.4, 0.05);
        level.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.6f);
        bar(p, "Rayo de energia");
    }

    public static void shield(ServerPlayer p) {
        if (!spend(p, COST_SHIELD)) return;
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 3, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 4, false, false, true));
        ServerLevel level = p.serverLevel();
        for (int i = 0; i < 80; i++) {
            double theta = Math.random() * Math.PI * 2, phi = Math.acos(2 * Math.random() - 1);
            double r = 1.6;
            level.sendParticles(GREEN, p.getX() + r * Math.sin(phi) * Math.cos(theta),
                    p.getY() + 1.0 + r * Math.cos(phi), p.getZ() + r * Math.sin(phi) * Math.sin(theta), 1, 0, 0, 0, 0);
        }
        level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.4f);
        bar(p, "Escudo de energia (10 s)");
    }

    public static void wall(ServerPlayer p) {
        if (!spend(p, COST_WALL)) return;
        ServerLevel level = p.serverLevel();
        Direction dir = p.getDirection();
        Direction side = dir.getClockWise();
        BlockPos base = p.blockPosition().relative(dir, 3);
        for (int w = -2; w <= 2; w++) {
            for (int h = 0; h <= 3; h++) {
                BlockPos pos = base.relative(side, w).above(h);
                if (level.getBlockState(pos).canBeReplaced()) {
                    level.setBlock(pos, GreenLanternMod.CONSTRUCT_BLOCK.get().defaultBlockState(), 3);
                }
            }
        }
        level.playSound(null, base, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 0.8f);
        bar(p, "Muro de energia (15 s)");
    }

    public static void recharge(Player p) {
        setEnergy(p, MAX_ENERGY);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 0.7f);
        p.displayClientMessage(Component.literal(
                "\u00a7aEn el dia mas brillante, en la noche mas oscura... \u00a77(Voluntad restaurada)"), false);
    }
}
