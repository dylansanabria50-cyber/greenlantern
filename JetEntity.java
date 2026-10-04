package com.example.greenlantern;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/** Caza de energia: vehiculo montable que dura 35 s. W acelera, S frena, Shift baja. */
public class JetEntity extends Entity {
    public static final int LIFE = 700;

    private int age;
    private float speed = 0.3f;
    private UUID riderId;

    public JetEntity(EntityType<? extends JetEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() { }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) { }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) { }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.55;
    }

    @Override
    public LivingEntity getControllingPassenger() {
        Entity e = this.getFirstPassenger();
        return e instanceof LivingEntity le ? le : null;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (!this.level().isClientSide) {
            return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void tick() {
        super.tick();
        this.age++;
        LivingEntity c = this.getControllingPassenger();

        if (!this.level().isClientSide) {
            if (c != null) {
                this.riderId = c.getUUID();
            } else if (this.riderId != null) {
                Player pl = this.level().getPlayerByUUID(this.riderId);
                if (pl != null) pl.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200));
                this.expire();
                return;
            }
            if (this.age > LIFE || (c == null && this.age > 40)) {
                this.expire();
                return;
            }
            if (this.age % 2 == 0 && c != null) {
                Vec3 look = this.calculateViewVector(this.getXRot(), this.getYRot());
                ((ServerLevel) this.level()).sendParticles(RingPowers.GREEN,
                        this.getX() - look.x * 1.6, this.getY() + 0.3 - look.y * 1.6, this.getZ() - look.z * 1.6,
                        6, 0.2, 0.2, 0.2, 0.0);
            }
        }

        if (c != null) {
            this.setRot(c.getYRot(), Mth.clamp(c.getXRot(), -60.0f, 60.0f));
            if (this.isControlledByLocalInstance()) {
                float target = c.zza > 0.0f ? 0.55f : (c.zza < 0.0f ? 0.12f : 0.3f);
                this.speed += (target - this.speed) * 0.08f;
                Vec3 look = this.calculateViewVector(this.getXRot(), this.getYRot());
                this.setDeltaMovement(look.scale(this.speed));
                this.move(MoverType.SELF, this.getDeltaMovement());
                if (this.horizontalCollision || this.verticalCollision) this.speed = 0.1f;
            }
        } else if (!this.level().isClientSide) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.04, 0.0));
            this.move(MoverType.SELF, this.getDeltaMovement());
        }
    }

    private void expire() {
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(RingPowers.GREEN, this.getX(), this.getY() + 0.5, this.getZ(), 40, 0.8, 0.5, 0.8, 0.05);
        }
        for (Entity p : this.getPassengers()) {
            if (p instanceof LivingEntity le) le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200));
        }
        this.ejectPassengers();
        this.discard();
    }
}
