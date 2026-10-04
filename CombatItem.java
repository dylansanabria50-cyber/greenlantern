package com.example.greenlantern;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Armas de energia del anillo: minigun (mantener clic derecho) y lanzacohetes (clic derecho). */
public class CombatItem extends Item {
    public enum Kind { MINIGUN, ROCKET }

    private final Kind kind;

    public CombatItem(Kind kind, Properties props) {
        super(props);
        this.kind = kind;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(kind == Kind.MINIGUN ? "Minigun de energia" : "Lanzacohetes de energia");
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (kind == Kind.ROCKET) {
            if (!level.isClientSide) fireRocket(level, player);
            player.getCooldowns().addCooldown(this, 30);
            return InteractionResultHolder.success(st);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(st);
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (kind != Kind.MINIGUN || level.isClientSide) return;
        if (user.tickCount % 2 != 0) return;
        ray(level, user, 40.0, 4.0f, true);
        level.playSound(null, user.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.5f, 1.8f);
    }

    private static void fireRocket(Level level, Player p) {
        Vec3 stop = ray(level, p, 60.0, 0.0f, true);
        level.explode(p, stop.x, stop.y, stop.z, 3.0f, Level.ExplosionInteraction.TNT);
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(RingPowers.GREEN, stop.x, stop.y, stop.z, 60, 0.8, 0.8, 0.8, 0.05);
        }
        level.playSound(null, p.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    /** Dispara un rayo verde; devuelve el punto donde se detiene. */
    private static Vec3 ray(Level level, LivingEntity user, double range, float damage, boolean entities) {
        Vec3 eye = user.getEyePosition();
        Vec3 end = eye.add(user.getLookAngle().scale(range));
        HitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        Vec3 stop = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
        if (entities) {
            EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, user, eye, stop,
                    new AABB(eye, stop).inflate(1.0),
                    x -> x instanceof LivingEntity && x.isPickable() && !x.isSpectator() && x != user);
            if (eh != null) {
                Entity target = eh.getEntity();
                if (damage > 0.0f) {
                    target.invulnerableTime = 0;
                    target.hurt(level.damageSources().mobAttack(user), damage);
                }
                stop = eh.getLocation();
            }
        }
        if (level instanceof ServerLevel sl) {
            Vec3 dir = stop.subtract(eye);
            int n = (int) Math.min(60.0, dir.length() * 2.0);
            if (n > 0) {
                Vec3 step = dir.scale(1.0 / n);
                for (int i = 1; i <= n; i++) {
                    Vec3 pt = eye.add(step.scale(i)).add(0.0, -0.2, 0.0);
                    sl.sendParticles(RingPowers.GREEN, pt.x, pt.y, pt.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }
        return stop;
    }
}
