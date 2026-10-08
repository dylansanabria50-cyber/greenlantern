package com.example.policia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.UUID;

/** Habilidad 4: helicoptero policial pilotable. */
public class PoliciaHeli {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "policia");
    public static final RegistryObject<EntityType<HeliEntity>> HELI = ENTITIES.register("helicoptero",
            () -> EntityType.Builder.<HeliEntity>of(HeliEntity::new, MobCategory.MISC)
                    .sized(3.0f, 3.4f).fireImmune().noSave().clientTrackingRange(16).updateInterval(1)
                    .build("policia:helicoptero"));

    public static final int DURATION = 2400;   // 2 min de vuelo
    public static final int LAND_T = 240;      // 12 s antes del final empieza a aterrizar solo
    public static final int COOLDOWN = 1200;   // 60 s
    public static final int SHRINK_T = 30;     // animacion de encogerse

    /** Habilidad: invocar el helicoptero o recordar como montarlo. */
    public static void use(ServerPlayer p) {
        if (p.getVehicle() instanceof HeliEntity) { PoliciaMod.msg(p, "Ya vas en el helicoptero (Shift para bajar)"); return; }
        if (p.isPassenger()) { PoliciaMod.msg(p, "Ya vas montado en algo"); return; }
        List<HeliEntity> mine = p.level().getEntitiesOfClass(HeliEntity.class, p.getBoundingBox().inflate(64.0),
                e -> !e.isRemoved() && p.getUUID().equals(e.owner));
        if (!mine.isEmpty()) { PoliciaMod.msg(p, "Haz clic derecho sobre el helicoptero para montarte"); return; }
        long left = p.getPersistentData().getLong("pol_hcd_t") - p.level().getGameTime();
        if (left > 0) { PoliciaMod.msg(p, "Helicoptero en enfriamiento: " + (left + 19) / 20 + " s"); return; }
        if (PoliciaMod.shield(p) > 0) { PoliciaMod.msg(p, "Espera a que termine el escudo"); return; }
        summon(p);
    }

    /** Tecla de linterna: el helicoptero queda fijo y la luz sigue la mirada del piloto. */
    public static void toggleLight(ServerPlayer p) {
        if (!(p.getVehicle() instanceof HeliEntity h)) { PoliciaMod.msg(p, "El modo linterna es del helicoptero"); return; }
        boolean on = !h.getEntityData().get(HeliEntity.LIGHT);
        h.getEntityData().set(HeliEntity.LIGHT, on);
        if (!on) h.clearLight();
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 1.0f, on ? 1.4f : 0.8f);
        PoliciaMod.msg(p, on ? "Modo linterna: helicoptero fijo, mira para apuntar la luz" : "Modo linterna desactivado");
    }

    static void summon(ServerPlayer p) {
        HeliEntity h = HELI.get().create(p.level());
        if (h == null) return;
        h.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0f);
        h.owner = p.getUUID();
        p.level().addFreshEntity(h);
        p.startRiding(h, true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 1.0f, 0.7f);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 1.0f, 0.6f);
        p.serverLevel().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 16, 1.5, 0.1, 1.5, 0.03);
        PoliciaMod.msg(p, "Helicoptero listo: W/S avanzar, A/D desplazarte, mira arriba o abajo para subir o bajar, Shift para bajar");
    }

    public static class HeliEntity extends Entity {
        static final EntityDataAccessor<Integer> SHRINK = SynchedEntityData.defineId(HeliEntity.class, EntityDataSerializers.INT);
        static final EntityDataAccessor<Boolean> LIGHT = SynchedEntityData.defineId(HeliEntity.class, EntityDataSerializers.BOOLEAN);
        net.minecraft.core.BlockPos lightPos;
        public float rotor, prevRotor, tilt, prevTilt, roll, prevRoll;
        UUID owner;
        double vx, vy, vz;
        int life, idle, landedT;
        boolean landMsg;
        // interpolacion en el cliente
        double lx, ly, lz;
        float lyr;
        int lsteps;

        public HeliEntity(EntityType<? extends HeliEntity> type, Level level) {
            super(type, level);
            this.noCulling = true;
            this.noPhysics = true;
            this.setNoGravity(true);
        }

        @Override
        protected void defineSynchedData() { this.entityData.define(SHRINK, -1); this.entityData.define(LIGHT, false); }
        public boolean isLight() { return this.entityData.get(LIGHT); }
        @Override
        public void remove(Entity.RemovalReason reason) { clearLight(); super.remove(reason); }
        @Override
        protected void readAdditionalSaveData(CompoundTag t) { }
        @Override
        protected void addAdditionalSaveData(CompoundTag t) { }
        @Override
        public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
        @Override
        public double getPassengersRidingOffset() { return 1.4; }
        @Override
        protected boolean canAddPassenger(Entity e) { return this.getPassengers().isEmpty(); }
        @Override
        public boolean isPickable() { return getFirstPassenger() == null && !isRemoved() && entityData.get(SHRINK) < 0; }
        @Override
        public InteractionResult interact(Player pl, InteractionHand hand) {
            if (getFirstPassenger() != null || pl.isPassenger() || entityData.get(SHRINK) >= 0) return InteractionResult.PASS;
            if (owner != null && !owner.equals(pl.getUUID())) return InteractionResult.PASS;
            if (!level().isClientSide && pl instanceof ServerPlayer sp) {
                sp.startRiding(this, true);
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 1.0f, 0.7f);
                PoliciaMod.msg(sp, "Subiste al helicoptero");
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
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
        @Override
        public boolean isControlledByLocalInstance() { return false; }

        @Override
        public void lerpTo(double x, double y, double z, float yr, float xr, int steps, boolean teleport) {
            lx = x; ly = y; lz = z; lyr = yr; lsteps = 3;
        }

        // ---------- terreno ----------
        boolean free(double x, double y, double z) {
            AABB b = new AABB(x - 1.5, y + 0.05, z - 1.5, x + 1.5, y + 3.2, z + 1.5);
            for (VoxelShape s : level().getBlockCollisions(this, b)) {
                if (!s.isEmpty()) return false;
            }
            return true;
        }

        double ground(double x, double z, double refY) {
            int bx = Mth.floor(x), bz = Mth.floor(z);
            int top = Mth.floor(refY) + 2;
            int bot = Math.max(Mth.floor(refY) - 96, level().getMinBuildHeight());
            BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
            for (int y = top; y >= bot; y--) {
                mp.set(bx, y, bz);
                BlockState bs = level().getBlockState(mp);
                VoxelShape sh = bs.getCollisionShape(level(), mp);
                if (!sh.isEmpty()) return y + sh.max(Direction.Axis.Y);
            }
            return refY;
        }

        /** Sitio donde se baja el jugador: al costado del helicoptero, sobre el suelo. */
        Vec3 side() {
            double rad = Math.toRadians(getYRot());
            double x = getX() - Math.cos(rad) * 2.6, z = getZ() - Math.sin(rad) * 2.6;
            double g = ground(x, z, getY() + 1.0);
            return new Vec3(x, g + 0.05, z);
        }

        @Override
        public Vec3 getDismountLocationForPassenger(LivingEntity p) { return side(); }

        @Override
        protected void removePassenger(Entity e) {
            super.removePassenger(e);
            e.fallDistance = 0.0f;
            if (e instanceof ServerPlayer sp) {
                sp.getPersistentData().putInt("pol_nofall", 100);
                if (!level().isClientSide) {
                    Vec3 d = side();
                    sp.connection.teleport(d.x, d.y, d.z, sp.getYRot(), sp.getXRot());
                }
            }
        }

        /** El casco es solido: saca a los jugadores que quedan dentro de la cabina. */
        void pushOut(Entity r) {
            double rad = Math.toRadians(getYRot()), c = Math.cos(rad), s = Math.sin(rad);
            for (Player pl : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(6.0, 1.0, 6.0),
                    e -> e != r && !e.isSpectator())) {
                if (pl.getY() + 1.8 < getY() + 0.2 || pl.getY() > getY() + 3.2) continue;
                double dx = pl.getX() - getX(), dz = pl.getZ() - getZ();
                double lat = dx * c + dz * s, fw = (-dx * s + dz * c) - 0.55;
                if (Math.abs(lat) >= 1.5 || Math.abs(fw) >= 2.65) continue;
                double nl = lat, nf = fw;
                double ol = 1.6 - Math.abs(lat), of = 2.7 - Math.abs(fw);
                if (ol <= of) nl = (lat < 0 ? -1 : 1) * 1.6; else nf = (fw < 0 ? -1 : 1) * 2.7;
                nf += 0.55;
                double nx = getX() + nl * c - nf * s, nz = getZ() + nl * s + nf * c;
                if (pl instanceof ServerPlayer sp) sp.connection.teleport(nx, pl.getY(), nz, pl.getYRot(), pl.getXRot());
                else pl.setPos(nx, pl.getY(), nz);
            }
        }

        /** Vuelo: W/S avanzan, A/D se desplazan, mirar arriba o abajo sube o baja. */
        void fly(Entity r, boolean landing) {
            double tx = 0.0, ty = 0.0, tz = 0.0;
            float yawNew = getYRot();
            boolean auto = landing || r == null;
            if (!auto && r instanceof LivingEntity le) {
                boolean lamp = entityData.get(LIGHT);
                float fw = le.zza, st = le.xxa;
                if (Math.abs(fw) < 0.05f || lamp) fw = 0f;
                if (Math.abs(st) < 0.05f || lamp) st = 0f;
                float turn = lamp ? 8.0f : 5.0f;
                yawNew = getYRot() + Mth.clamp(Mth.wrapDegrees(r.getYRot() - getYRot()), -turn, turn);
                double rad = Math.toRadians(yawNew);
                double fx = -Math.sin(rad), fz = Math.cos(rad), sx = Math.cos(rad), sz = Math.sin(rad);
                tx = (fx * fw + sx * st) * 0.55;
                tz = (fz * fw + sz * st) * 0.55;
                float pit = r.getXRot();
                if (fw != 0f) ty = -Math.sin(Math.toRadians(pit)) * fw * 0.45;
                else if (st == 0f && !lamp) {
                    if (pit < -60f) ty = 0.3; else if (pit > 60f) ty = -0.3;
                }
            } else {
                double dist = getY() - ground(getX(), getZ(), getY());
                ty = -Mth.clamp(dist * 0.12, 0.05, 0.3);
            }
            vx += (tx - vx) * 0.15;
            vy += (ty - vy) * 0.15;
            vz += (tz - vz) * 0.15;
            double x = getX(), y = getY(), z = getZ();
            if (free(x + vx, y, z)) x += vx; else vx = 0.0;
            if (free(x, y + vy, z)) y += vy; else vy = 0.0;
            if (free(x, y, z + vz)) z += vz; else vz = 0.0;
            y = Math.min(y, level().getMaxBuildHeight() - 6);
            setPos(x, y, z);
            setYRot(yawNew);
        }

        void updateTilt() {
            prevTilt = tilt;
            prevRoll = roll;
            double dx = getX() - xo, dz = getZ() - zo;
            double rad = Math.toRadians(getYRot());
            double fs = -dx * Math.sin(rad) + dz * Math.cos(rad);
            double ls = dx * Math.cos(rad) + dz * Math.sin(rad);
            tilt += (Mth.clamp((float) (fs * 22.0), -10.0f, 14.0f) - tilt) * 0.2f;
            roll += (Mth.clamp((float) (-ls * 20.0), -12.0f, 12.0f) - roll) * 0.2f;
        }

        void notifyOwner(ServerLevel sl, String m) {
            if (owner == null) return;
            ServerPlayer o = sl.getServer().getPlayerList().getPlayer(owner);
            if (o != null) PoliciaMod.msg(o, m);
        }

        @Override
        public void tick() {
            super.tick();
            prevRotor = rotor;
            rotor += 52.0f;
            if (level().isClientSide) {
                if (lsteps > 0) {
                    double k = 1.0 / lsteps;
                    setPos(getX() + (lx - getX()) * k, getY() + (ly - getY()) * k, getZ() + (lz - getZ()) * k);
                    setYRot(getYRot() + (float) Mth.wrapDegrees(lyr - getYRot()) * (float) k);
                    lsteps--;
                }
                updateTilt();
                return;
            }
            setDeltaMovement(Vec3.ZERO);
            ServerLevel sl = (ServerLevel) level();
            life++;
            if (tickCount % 20 == 0 && owner != null) {
                ServerPlayer o = sl.getServer().getPlayerList().getPlayer(owner);
                if (o == null || !o.isAlive()) { ejectPassengers(); discard(); return; }
            }
            int sh = entityData.get(SHRINK);
            if (sh >= 0) {
                entityData.set(SHRINK, sh + 1);
                if (sh + 1 >= SHRINK_T) finish(sl);
                return;
            }
            Entity r = getFirstPassenger();
            boolean landing = life >= DURATION - LAND_T;
            if (landing && !landMsg) { landMsg = true; notifyOwner(sl, "El helicoptero va a aterrizar"); }
            if (landing && entityData.get(LIGHT)) { entityData.set(LIGHT, false); clearLight(); }
            fly(r, landing);
            if (entityData.get(LIGHT) && r != null) lamp(sl, r);
            boolean landed = !free(getX(), getY() - 0.08, getZ());
            pushOut(r);
            idle = (r == null && landed) ? idle + 1 : 0;
            landedT = (landing && landed) ? landedT + 1 : 0;
            if (tickCount % 4 == 0) {
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1.4f, 0.5f);
            }
            if (landedT > 25 || idle > 600 || life > DURATION + 400) startShrink(sl);
        }

        void clearLight() {
            if (lightPos != null) {
                if (level().getBlockState(lightPos).is(net.minecraft.world.level.block.Blocks.LIGHT)) level().setBlock(lightPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                lightPos = null;
            }
        }

        /** Modo linterna: el haz sale del morro hacia donde mira el piloto, ilumina el punto y marca a los seres vivos. */
        void lamp(ServerLevel sl, Entity r) {
            Vec3 look = r.getLookAngle();
            Vec3 o = position().add(0.0, 0.7, 0.0).add(look.scale(1.8));
            Vec3 end = o.add(look.scale(48.0));
            net.minecraft.world.phys.BlockHitResult hr = sl.clip(new net.minecraft.world.level.ClipContext(o, end,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
            Vec3 hit = hr.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? end : hr.getLocation();
            if (tickCount % 4 == 0) {
                net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(hit.subtract(look.scale(0.6)));
                if (!bp.equals(lightPos) && sl.getBlockState(bp).isAir()) {
                    clearLight();
                    sl.setBlock(bp, net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState(), 3);
                    lightPos = bp;
                }
            }
        }

        void startShrink(ServerLevel sl) {
            entityData.set(SHRINK, 0);
            ejectPassengers();
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 1.0f, 0.6f);
        }

        void finish(ServerLevel sl) {
            sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.8, getZ(), 18, 1.0, 0.5, 1.0, 0.03);
            if (owner != null) {
                ServerPlayer o = sl.getServer().getPlayerList().getPlayer(owner);
                if (o != null) {
                    o.getPersistentData().putLong("pol_hcd_t", sl.getGameTime() + COOLDOWN);
                    PoliciaMod.msg(o, "Helicoptero retirado");
                }
            }
            ejectPassengers();
            discard();
        }
    }
}
