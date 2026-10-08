package com.example.policia;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.UUID;

/** Habilidad 2: tanque invocado bajo el jugador. Apunta con la mira, dispara con clic derecho y desaparece. */
public class PoliciaTank {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "policia");
    public static final RegistryObject<EntityType<TankEntity>> TANK = ENTITIES.register("tanque",
            () -> EntityType.Builder.<TankEntity>of(TankEntity::new, MobCategory.MISC)
                    .sized(7.2f, 9.0f).fireImmune().noSave().clientTrackingRange(10).updateInterval(1)
                    .build("policia:tanque"));

    public static final float SCALE = 0.45f;
    /** Tamano final: 3 veces el anterior (12 x 9 bloques). */
    public static final float SX = 1.44f, SY = 1.86f;
    public static final int COOLDOWN = 600;   // 30 s
    static final int LOAD_TICKS = 12;         // carga antes del disparo
    static final int END_TICKS = 62;          // el tanque se retira
    static final double MIN_RANGE = 16.0;
    /** Pivotes del modelo (unidades del modelo): torreta y canon. */
    static final float[] PT = {0f, 2.6875f, -0.4375f};
    static final float[] PB = {0f, 2.3125f, -1.4375f};

    public static void summon(ServerPlayer p) {
        TankEntity t = TANK.get().create(p.level());
        if (t == null) return;
        t.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0f);
        t.owner = p.getUUID();
        p.level().addFreshEntity(t);
        p.startRiding(t, true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 0.6f);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 1.0f, 0.5f);
        p.serverLevel().sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.getX(), p.getY() + 0.1, p.getZ(), 14, 1.0, 0.1, 1.0, 0.02);
        PoliciaMod.msg(p, "Tanque listo: apunta y haz clic derecho para disparar");
    }

    /** Orden de disparo: el objetivo es donde mira el jugador. */
    public static void fire(ServerPlayer p) {
        if (!(p.getVehicle() instanceof TankEntity t)) return;
        if (t.fireState() >= 0) return;
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        HitResult hr = p.pick(80.0, 1.0f, false);
        Vec3 loc = hr.getLocation();
        if (eye.distanceTo(loc) < MIN_RANGE) loc = eye.add(look.scale(MIN_RANGE));
        t.target = loc;
        t.boomAt = LOAD_TICKS + Mth.clamp((int) (eye.distanceTo(loc) / 4.0), 2, 14);
        t.setFireState(0);
        p.level().playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 1.0f, 0.5f);
        PoliciaMod.msg(p, "Cargando...");
    }

    public static class TankEntity extends Entity {
        static final EntityDataAccessor<Integer> FIRE = SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.INT);
        public float turretYaw, prevTurretYaw, pitch, prevPitch;
        UUID owner;
        Vec3 target;
        int boomAt = -1;

        public TankEntity(EntityType<? extends TankEntity> type, Level level) {
            super(type, level);
            this.noCulling = true;
            this.noPhysics = true;
            this.setNoGravity(true);
        }

        @Override
        protected void defineSynchedData() { this.entityData.define(FIRE, -1); }
        @Override
        protected void readAdditionalSaveData(CompoundTag t) { }
        @Override
        protected void addAdditionalSaveData(CompoundTag t) { }
        @Override
        public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
        @Override
        public double getPassengersRidingOffset() { return 7.75; }
        @Override
        public boolean shouldRiderSit() { return false; }
        @Override
        protected boolean canAddPassenger(Entity e) { return this.getPassengers().isEmpty(); }
        @Override
        public boolean isPickable() { return false; }
        @Override
        public boolean isPushable() { return false; }
        @Override
        public boolean hurt(DamageSource s, float a) { return false; }
        @Override
        public boolean isInvulnerableTo(DamageSource s) { return true; }

        @Override
        public Vec3 getDismountLocationForPassenger(net.minecraft.world.entity.LivingEntity p) { return new Vec3(getX(), getY() + 0.1, getZ()); }
        @Override
        protected void removePassenger(Entity e) {
            super.removePassenger(e);
            e.fallDistance = 0.0f;
            if (e instanceof ServerPlayer sp) sp.getPersistentData().putInt("pol_nofall", 100);
        }

        int fireState() { return entityData.get(FIRE); }
        void setFireState(int v) { entityData.set(FIRE, v); }

        /** Posicion mundial de la boca del canon (aproximada). */
        Vec3 muzzle() {
            double hy = Math.toRadians(getYRot());
            double yaw = Math.toRadians(turretYaw), pit = Math.toRadians(pitch);
            double fwdT = -PT[2] * SX;
            double fwdB = -(PB[2] - PT[2]) * SX;
            double px = getX() - Math.sin(hy) * fwdT - Math.sin(yaw) * fwdB;
            double pz = getZ() + Math.cos(hy) * fwdT + Math.cos(yaw) * fwdB;
            double py = getY() + PB[1] * SY;
            Vec3 dir = new Vec3(-Math.sin(yaw) * Math.cos(pit), -Math.sin(pit), Math.cos(yaw) * Math.cos(pit));
            return new Vec3(px, py, pz).add(dir.scale(5.4 * SX));
        }

        @Override
        public void tick() {
            super.tick();
            if (tickCount == 1) { turretYaw = prevTurretYaw = getYRot(); }
            prevTurretYaw = turretYaw;
            prevPitch = pitch;
            Entity r = getFirstPassenger();
            int f = entityData.get(FIRE);
            if (r != null && f < 0) {
                // la torreta gira hacia donde mira el jugador, con velocidad limitada
                float dy = Mth.wrapDegrees(r.getYRot() - turretYaw);
                turretYaw += Mth.clamp(dy, -6.0f, 6.0f);
                float tp = Mth.clamp(r.getXRot(), -25.0f, 8.0f);
                pitch += Mth.clamp(tp - pitch, -3.0f, 3.0f);
            }
            if (level().isClientSide) return;
            setDeltaMovement(Vec3.ZERO);
            ServerLevel sl = (ServerLevel) level();
            if (f >= 0) {
                f++;
                entityData.set(FIRE, f);
                if (f == LOAD_TICKS && target != null) {
                    Vec3 m = muzzle();
                    level().playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0f, 0.5f);
                    level().playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 2.0f, 0.5f);
                    sl.sendParticles(ParticleTypes.FLAME, m.x, m.y, m.z, 14, 0.15, 0.15, 0.15, 0.08);
                    sl.sendParticles(ParticleTypes.LARGE_SMOKE, m.x, m.y, m.z, 10, 0.25, 0.25, 0.25, 0.04);
                    Vec3 d = target.subtract(m);
                    int n = 16;
                    for (int i = 1; i <= n; i++) {
                        Vec3 q = m.add(d.scale(i / (double) n));
                        sl.sendParticles(ParticleTypes.SMOKE, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0.0);
                    }
                }
                if (f == boomAt && target != null) {
                    level().explode(this, target.x, target.y, target.z, 3.2f, Level.ExplosionInteraction.NONE);
                }
                if (f >= END_TICKS) finish(sl);
            } else if ((r == null && tickCount > 20) || tickCount > 1200) {
                finish(sl);
            }
        }

        void finish(ServerLevel sl) {
            sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.6, getZ(), 18, 1.0, 0.4, 1.0, 0.03);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 1.0f, 0.5f);
            if (owner != null) {
                ServerPlayer o = sl.getServer().getPlayerList().getPlayer(owner);
                if (o != null) {
                    o.getPersistentData().putInt("pol_tcd", COOLDOWN);
                    PoliciaMod.msg(o, "Tanque retirado");
                    PoliciaMod.sync(o);
                }
            }
            ejectPassengers();
            discard();
        }
    }
}
