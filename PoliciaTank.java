package com.example.policia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.UUID;

/** Habilidades 2 y 4: tanque de un disparo (bajo los pies) y tanque pilotable de 3 disparos. */
public class PoliciaTank {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "policia");
    public static final RegistryObject<EntityType<TankEntity>> TANK = ENTITIES.register("tanque",
            () -> EntityType.Builder.<TankEntity>of(TankEntity::new, MobCategory.MISC)
                    .sized(7.2f, 9.0f).fireImmune().noSave().clientTrackingRange(16).updateInterval(1)
                    .build("policia:tanque"));

    public static final float SCALE = 0.45f;
    /** Tamano final: 3 veces el anterior (12 x 9 bloques). */
    public static final float SX = 1.44f, SY = 1.86f;
    public static final int COOLDOWN = 600;   // 30 s
    public static final int SHOTS = 3;
    static final int LOAD_TICKS = 12;         // carga antes del disparo
    static final int END_TICKS = 62;          // el tanque se retira
    static final int RESET_TICKS = 30;        // el tanque pilotable queda listo para otro disparo
    static final double MIN_RANGE = 16.0;
    /** Pivotes del modelo (unidades del modelo): torreta y canon. */
    static final float[] PT = {0f, 2.6875f, -0.4375f};
    static final float[] PB = {0f, 2.3125f, -1.4375f};
    /** Radio de las ruedas en bloques (para la animacion). */
    static final float WHEEL_R = 0.47f * SX;

    public static void summon(ServerPlayer p) { summon(p, false); }

    public static void summon(ServerPlayer p, boolean mobile) {
        TankEntity t = TANK.get().create(p.level());
        if (t == null) return;
        t.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0f);
        t.owner = p.getUUID();
        if (mobile) {
            t.getEntityData().set(TankEntity.MOBILE, true);
            t.getEntityData().set(TankEntity.SHOTS_LEFT, SHOTS);
            double g = t.footGround(p.getX(), p.getZ(), p.getYRot(), p.getY());
            if (g > p.getY() - 6.0) t.setPos(p.getX(), Math.max(g, p.getY() - 0.5), p.getZ());
        }
        p.level().addFreshEntity(t);
        p.startRiding(t, true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 0.6f);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 1.0f, 0.5f);
        p.serverLevel().sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.getX(), p.getY() + 0.1, p.getZ(), 14, 1.0, 0.1, 1.0, 0.02);
        PoliciaMod.msg(p, mobile ? "Tanque listo: WASD para moverte, clic derecho para disparar (3 disparos)"
                : "Tanque listo: apunta y haz clic derecho para disparar");
    }

    /** Habilidad del tanque pilotable: invocar, bajarse o volver a subir. */
    public static void useMobile(ServerPlayer p) {
        if (p.getVehicle() instanceof TankEntity t) {
            if (t.isMobile()) {
                p.stopRiding();
                PoliciaMod.msg(p, "Bajaste del tanque (J para volver a subir)");
            } else {
                PoliciaMod.msg(p, "El tanque ya esta invocado");
            }
            return;
        }
        if (p.isPassenger()) { PoliciaMod.msg(p, "Ya vas montado en algo"); return; }
        List<TankEntity> mine = p.level().getEntitiesOfClass(TankEntity.class, p.getBoundingBox().inflate(48.0),
                e -> e.isMobile() && p.getUUID().equals(e.owner));
        if (!mine.isEmpty()) {
            TankEntity t = mine.get(0);
            if (t.isRemoved() || t.getFirstPassenger() != null) return;
            PoliciaMod.msg(p, "Haz clic derecho sobre el tanque para montarte");
            return;
        }
        CompoundTag d = p.getPersistentData();
        if (PoliciaMod.shield(p) > 0) { PoliciaMod.msg(p, "Espera a que termine el escudo"); return; }
        if (d.getInt("pol_tcd") > 0) { PoliciaMod.msg(p, "Tanque en enfriamiento: " + (d.getInt("pol_tcd") + 19) / 20 + " s"); return; }
        summon(p, true);
        PoliciaMod.sync(p);
    }

    /** Orden de disparo: el objetivo es donde mira el jugador. */
    public static void fire(ServerPlayer p) {
        if (!(p.getVehicle() instanceof TankEntity t)) return;
        if (t.fireState() >= 0) return;
        if (t.isMobile() && t.shotsLeft() <= 0) return;
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        HitResult hr = p.pick(96.0, 1.0f, false);
        Vec3 loc = hr.getLocation();
        if (eye.distanceTo(loc) < MIN_RANGE) loc = eye.add(look.scale(MIN_RANGE));
        t.target = loc;
        t.boomAt = LOAD_TICKS + Mth.clamp((int) (eye.distanceTo(loc) / 4.0), 2, 14);
        if (t.isMobile()) t.getEntityData().set(TankEntity.SHOTS_LEFT, t.shotsLeft() - 1);
        t.setFireState(0);
        p.level().playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 1.0f, 0.5f);
        PoliciaMod.msg(p, t.isMobile() ? "Cargando... (quedan " + t.shotsLeft() + ")" : "Cargando...");
    }

    public static class TankEntity extends Entity {
        static final EntityDataAccessor<Integer> FIRE = SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.INT);
        static final EntityDataAccessor<Integer> SHOTS_LEFT = SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.INT);
        static final EntityDataAccessor<Boolean> MOBILE = SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.BOOLEAN);
        static final EntityDataAccessor<Float> WL = SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.FLOAT);
        static final EntityDataAccessor<Float> WR = SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.FLOAT);
        public float turretYaw, prevTurretYaw, pitch, prevPitch;
        /** Giro acumulado de las ruedas (radianes). */
        public float wl, wr, prevWL, prevWR;
        UUID owner;
        Vec3 target;
        int boomAt = -1;
        double speed;
        int idle;
        // interpolacion en el cliente
        double lx, ly, lz;
        float lyr;
        int lsteps;

        public TankEntity(EntityType<? extends TankEntity> type, Level level) {
            super(type, level);
            this.noCulling = true;
            this.noPhysics = true;
            this.setNoGravity(true);
        }

        @Override
        protected void defineSynchedData() {
            this.entityData.define(FIRE, -1);
            this.entityData.define(SHOTS_LEFT, 0);
            this.entityData.define(MOBILE, false);
            this.entityData.define(WL, 0.0f);
            this.entityData.define(WR, 0.0f);
        }
        @Override
        protected void readAdditionalSaveData(CompoundTag t) { }
        @Override
        protected void addAdditionalSaveData(CompoundTag t) { }
        @Override
        public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
        @Override
        public double getPassengersRidingOffset() { return 5.95; }
        @Override
        public boolean shouldRiderSit() { return false; }
        @Override
        protected boolean canAddPassenger(Entity e) { return this.getPassengers().isEmpty(); }
        @Override
        public boolean isPickable() { return isMobile() && getFirstPassenger() == null && !isRemoved(); }
        @Override
        public net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player pl, net.minecraft.world.InteractionHand hand) {
            if (!isMobile() || getFirstPassenger() != null || pl.isPassenger()) return net.minecraft.world.InteractionResult.PASS;
            if (owner != null && !owner.equals(pl.getUUID())) return net.minecraft.world.InteractionResult.PASS;
            if (!level().isClientSide && pl instanceof ServerPlayer sp) {
                sp.startRiding(this, true);
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 1.0f, 0.6f);
                PoliciaMod.msg(sp, "Subiste al tanque (" + shotsLeft() + " disparos)");
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
        }
        /** Los mobs y otras entidades no empujan al tanque. */
        @Override
        public void push(Entity e) { }
        @Override
        public void push(double x, double y, double z) { }
        @Override
        public boolean isPushable() { return false; }
        @Override
        public boolean hurt(DamageSource s, float a) { return false; }
        @Override
        public boolean isInvulnerableTo(DamageSource s) { return true; }
        /** El movimiento lo calcula el servidor a partir de las teclas del jugador. */
        @Override
        public boolean isControlledByLocalInstance() { return false; }

        @Override
        public void lerpTo(double x, double y, double z, float yr, float xr, int steps, boolean teleport) {
            lx = x; ly = y; lz = z; lyr = yr; lsteps = 3;
        }

        public boolean isMobile() { return entityData.get(MOBILE); }
        public int shotsLeft() { return entityData.get(SHOTS_LEFT); }
        public int fireState() { return entityData.get(FIRE); }
        void setFireState(int v) { entityData.set(FIRE, v); }

        // ---------- terreno ----------
        double ground(double x, double z, double refY) {
            int bx = Mth.floor(x), bz = Mth.floor(z);
            int top = Mth.floor(refY) + 4;
            int bot = Math.max(Mth.floor(refY) - 14, level().getMinBuildHeight());
            BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
            for (int y = top; y >= bot; y--) {
                mp.set(bx, y, bz);
                BlockState bs = level().getBlockState(mp);
                if (bs.is(BlockTags.LEAVES) || bs.is(BlockTags.LOGS)) continue; // el tanque aplasta los arboles
                FluidState fs = bs.getFluidState();
                if (!fs.isEmpty()) return y + fs.getHeight(level(), mp);
                VoxelShape sh = bs.getCollisionShape(level(), mp);
                if (!sh.isEmpty()) return y + sh.max(Direction.Axis.Y);
            }
            return refY - 14;
        }

        /** Altura mas alta bajo la huella del tanque (9 puntos). */
        double footGround(double cx, double cz, float yaw, double ref) {
            double rad = Math.toRadians(yaw);
            double fx = -Math.sin(rad), fz = Math.cos(rad), px = Math.cos(rad), pz = Math.sin(rad);
            double best = -1.0e9;
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    best = Math.max(best, ground(cx + px * a * 2.5 + fx * b * 4.6, cz + pz * a * 2.5 + fz * b * 4.6, ref));
                }
            }
            return best;
        }

        /** Sitio donde se baja el jugador: a la derecha del tanque, sobre el suelo. */
        Vec3 dismountSpot() {
            double rad = Math.toRadians(getYRot());
            double x = getX() - Math.cos(rad) * 4.8, z = getZ() - Math.sin(rad) * 4.8;
            double g = ground(x, z, getY() + 2.0);
            return new Vec3(x, g + 0.05, z);
        }

        @Override
        public Vec3 getDismountLocationForPassenger(LivingEntity p) {
            if (isMobile()) return dismountSpot();
            return new Vec3(getX(), getY() + 0.1, getZ());
        }

        @Override
        protected void removePassenger(Entity e) {
            super.removePassenger(e);
            e.fallDistance = 0.0f;
            if (e instanceof ServerPlayer sp) {
                sp.getPersistentData().putInt("pol_nofall", 100);
                if (isMobile() && !level().isClientSide) {
                    Vec3 d = dismountSpot();
                    sp.connection.teleport(d.x, d.y, d.z, sp.getYRot(), sp.getXRot());
                }
            }
        }

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

        int clankT;
        float lastYaw;

        /** Sonido metalico de las orugas al avanzar o girar. */
        void clank(Entity r) {
            boolean turning = Math.abs(Mth.wrapDegrees(getYRot() - lastYaw)) > 0.3f;
            lastYaw = getYRot();
            if (r == null || (Math.abs(speed) < 0.03 && !turning)) { clankT = 0; return; }
            clankT++;
            if (clankT % 4 == 0) {
                float pit = 0.45f + level().random.nextFloat() * 0.2f;
                level().playSound(null, getX(), getY(), getZ(), (clankT & 4) == 0 ? SoundEvents.IRON_GOLEM_STEP : SoundEvents.CHAIN_STEP,
                        SoundSource.PLAYERS, 2.0f, pit);
            }
            if (clankT % 12 == 0) {
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_HIT, SoundSource.PLAYERS, 0.35f, 0.5f);
            }
        }

        /** El casco es solido: saca a los jugadores que quedan dentro de su huella. */
        void pushOut(Entity r) {
            double rad = Math.toRadians(getYRot()), c = Math.cos(rad), s = Math.sin(rad);
            for (net.minecraft.world.entity.player.Player pl : level().getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,
                    getBoundingBox().inflate(6.0, 0.0, 6.0), e -> e != r && !e.isSpectator())) {
                double dx = pl.getX() - getX(), dz = pl.getZ() - getZ();
                double lat = dx * c + dz * s, fw = -dx * s + dz * c;
                if (Math.abs(lat) >= 3.4 || Math.abs(fw) >= 6.3 || pl.getY() > getY() + 4.4) continue;
                double nl = lat, nf = fw;
                double ol = 3.5 - Math.abs(lat), of = 6.4 - Math.abs(fw);
                if (ol <= of) nl = (lat < 0 ? -1 : 1) * 3.5; else nf = (fw < 0 ? -1 : 1) * 6.4;
                double nx = getX() + nl * c - nf * s, nz = getZ() + nl * s + nf * c;
                if (pl instanceof ServerPlayer sp) sp.connection.teleport(nx, pl.getY(), nz, pl.getYRot(), pl.getXRot());
                else pl.setPos(nx, pl.getY(), nz);
            }
        }

        /** Conduccion: W/S avanzan, A/D giran el casco. */
        void drive(Entity r) {
            float fw = 0f, st = 0f;
            if (r instanceof LivingEntity le) { fw = le.zza; st = le.xxa; }
            if (Math.abs(fw) < 0.05f) fw = 0f;
            if (Math.abs(st) < 0.05f) st = 0f;
            if (fw != 0f) speed += fw * 0.05; else speed *= 0.85;
            speed = Mth.clamp(speed, -0.2, 0.42);
            if (Math.abs(speed) < 0.004) speed = 0.0;
            float dyaw = -st * 2.4f;
            float ny = getYRot() + dyaw;
            double rad = Math.toRadians(ny);
            double nx = getX() - Math.sin(rad) * speed, nz = getZ() + Math.cos(rad) * speed;
            double g = footGround(nx, nz, ny, getY());
            if (g - getY() > 2.3) {
                speed = 0.0;
                nx = getX(); nz = getZ();
                g = footGround(nx, nz, ny, getY());
                if (g - getY() > 2.3) { ny = getYRot(); dyaw = 0f; g = footGround(nx, nz, ny, getY()); }
            }
            double ny2 = getY() + Mth.clamp(g - getY(), -0.7, 0.5);
            setPos(nx, ny2, nz);
            setYRot(ny);
            // ruedas: el lado exterior de un giro va mas rapido
            double turn = Math.toRadians(dyaw) * 2.8;
            wl += (float) ((speed + turn) / WHEEL_R);
            wr += (float) ((speed - turn) / WHEEL_R);
            entityData.set(WL, wl);
            entityData.set(WR, wr);
        }

        @Override
        public void tick() {
            super.tick();
            if (tickCount == 1) { turretYaw = prevTurretYaw = getYRot(); }
            prevTurretYaw = turretYaw;
            prevPitch = pitch;
            prevWL = wl;
            prevWR = wr;
            Entity r = getFirstPassenger();
            int f = entityData.get(FIRE);
            if (r != null && f < 0) {
                // la torreta gira hacia donde mira el jugador, con velocidad limitada
                float dy = Mth.wrapDegrees(r.getYRot() - turretYaw);
                turretYaw += Mth.clamp(dy, -6.0f, 6.0f);
                float tp = Mth.clamp(r.getXRot(), -25.0f, 8.0f);
                pitch += Mth.clamp(tp - pitch, -3.0f, 3.0f);
            }
            if (level().isClientSide) {
                if (lsteps > 0) {
                    double k = 1.0 / lsteps;
                    setPos(getX() + (lx - getX()) * k, getY() + (ly - getY()) * k, getZ() + (lz - getZ()) * k);
                    setYRot(getYRot() + (float) Mth.wrapDegrees(lyr - getYRot()) * (float) k);
                    lsteps--;
                }
                wl = entityData.get(WL);
                wr = entityData.get(WR);
                return;
            }
            setDeltaMovement(Vec3.ZERO);
            ServerLevel sl = (ServerLevel) level();
            boolean mobile = isMobile();
            if (mobile && r != null) drive(r);
            else if (mobile) speed = 0.0;
            if (mobile) { clank(r); pushOut(r); }
            if (tickCount % 20 == 0 && owner != null) {
                ServerPlayer o = sl.getServer().getPlayerList().getPlayer(owner);
                if (o == null || !o.isAlive()) { ejectPassengers(); discard(); return; }
            }
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
                if (mobile && shotsLeft() > 0) {
                    if (f >= RESET_TICKS) { setFireState(-1); target = null; boomAt = -1; }
                } else if (f >= END_TICKS) {
                    finish(sl);
                }
            } else if (mobile) {
                idle = r == null ? idle + 1 : 0;
                if (idle > 1200 || tickCount > 24000) finish(sl);
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
