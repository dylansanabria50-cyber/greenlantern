package com.example.policia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Habilidad TRACTOR del albanil: pala cargadora amarilla que rompe todo lo natural que tiene delante. */
public class PoliciaTractor {
    public static final RegistryObject<EntityType<TractorEntity>> TRACTOR = PoliciaRefuerzo.ENTITIES.register("tractor",
            () -> EntityType.Builder.<TractorEntity>of(TractorEntity::new, MobCategory.MISC)
                    .sized(4.8f, 6.0f).fireImmune().noSave().clientTrackingRange(16).updateInterval(1)
                    .build("policia:tractor"));

    static final int COST = 15, CD = 400, IDLE = 6000;   // 15 XP, 20 s, 5 min sin conductor
    static final double WHEEL_R = 1.15;
    /** Zonas de rotura (en bloques, respecto al centro de la cabina). */
    static final double A0 = 2.4, A1 = 5.3, AL = 2.1;      // pala
    static final double B0 = -3.9, B1 = 2.4, BL = 2.4;     // cuerpo

    static final Map<UUID, TractorEntity> ACTIVE = new HashMap<>();
    static final Map<UUID, SimpleContainer> INVS = new HashMap<>();
    static boolean mute = false;

    static void init() {
        MinecraftForge.EVENT_BUS.register(new PoliciaPlaced.Ev());
    }

    // ---------- inventario ----------
    static SimpleContainer inv(ServerPlayer p) {
        UUID id = p.getUUID();
        SimpleContainer c = INVS.get(id);
        if (c != null) return c;
        c = new SimpleContainer(54);
        ListTag l = p.getPersistentData().getList("alb_tinv", 10);
        for (Tag t : l) {
            CompoundTag e = (CompoundTag) t;
            int s = e.getInt("Slot");
            if (s >= 0 && s < 54) c.setItem(s, ItemStack.of(e));
        }
        final net.minecraft.server.MinecraftServer srv = p.server;
        final SimpleContainer fc = c;
        c.addListener(cc -> {
            if (mute) return;
            ServerPlayer q = srv.getPlayerList().getPlayer(id);
            if (q != null) saveInv(q, fc);
        });
        INVS.put(id, c);
        return c;
    }

    static void saveInv(ServerPlayer p, SimpleContainer c) {
        ListTag l = new ListTag();
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) continue;
            CompoundTag e = new CompoundTag();
            e.putInt("Slot", i);
            s.save(e);
            l.add(e);
        }
        p.getPersistentData().put("alb_tinv", l);
    }

    static void openInv(ServerPlayer p) {
        if (p.getPersistentData().getInt("alb_tu") < 1) { PoliciaMod.msg(p, "Aun no tienes el tractor: desbloquea TRACTOR"); return; }
        final SimpleContainer c = inv(p);
        p.openMenu(new SimpleMenuProvider((id, pi, pl) -> new ChestMenu(MenuType.GENERIC_9x6, id, pi, c, 6),
                Component.literal("Inventario del tractor")));
    }

    // ---------- acciones ----------
    static void unlock(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        if (d.getInt("alb_tu") >= 1) { PoliciaMod.msg(p, "TRACTOR ya esta desbloqueado"); return; }
        if (PoliciaMod.xp(p) < COST) { PoliciaMod.msg(p, "Necesitas " + COST + " XP (tienes " + PoliciaMod.xp(p) + ")"); return; }
        d.putInt("pol_xp", PoliciaMod.xp(p) - COST);
        d.putInt("alb_tu", 1);
        PoliciaMod.msg(p, "Habilidad desbloqueada: TRACTOR");
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 0.9f);
        PoliciaMod.sync(p);
    }

    static TractorEntity mine(ServerPlayer p) {
        TractorEntity t = ACTIVE.get(p.getUUID());
        if (t != null && t.isRemoved()) { ACTIVE.remove(p.getUUID()); return null; }
        return t;
    }

    /** J: invocar, bajarse, volver a subir; Mayus+J: retirar. */
    static void use(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        if (d.getInt("alb_tu") < 1) { PoliciaMod.msg(p, "Desbloquea primero: TRACTOR"); return; }
        if (p.getVehicle() instanceof TractorEntity) {
            p.stopRiding();
            PoliciaMod.msg(p, "Bajaste del tractor (J: volver a subir, Mayus+J: retirarlo)");
            return;
        }
        TractorEntity t = mine(p);
        if (t != null) {
            if (p.isShiftKeyDown()) { t.finish((ServerLevel) t.level()); return; }
            if (t.level() != p.level() || t.distanceToSqr(p) > 400.0) { PoliciaMod.msg(p, "El tractor esta lejos (Mayus+J lo retira)"); return; }
            if (t.getFirstPassenger() != null) return;
            p.startRiding(t, true);
            hint(p);
            return;
        }
        if (p.isPassenger()) { PoliciaMod.msg(p, "Ya vas montado en algo"); return; }
        if (d.getInt("alb_tcd") > 0) { PoliciaMod.msg(p, "Tractor en enfriamiento: " + (d.getInt("alb_tcd") + 19) / 20 + " s"); return; }
        summon(p);
    }

    static void hint(ServerPlayer p) {
        PoliciaMod.msg(p, "TRACTOR: W/S avanzar, A/D girar, ESPACIO sube la pala, CTRL la baja, G luces, U inventario, Shift bajar");
    }

    static void summon(ServerPlayer p) {
        TractorEntity t = TRACTOR.get().create(p.level());
        if (t == null) return;
        t.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0f);
        t.owner = p.getUUID();
        double g = t.footGround(p.getX(), p.getZ(), p.getYRot(), p.getY());
        if (g > p.getY() - 6.0) t.setPos(p.getX(), Math.max(g, p.getY() - 0.5), p.getZ());
        p.level().addFreshEntity(t);
        ACTIVE.put(p.getUUID(), t);
        p.getPersistentData().putInt("alb_tact", 1);
        p.startRiding(t, true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 0.6f);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 1.0f, 0.5f);
        p.serverLevel().sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.getX(), p.getY() + 0.1, p.getZ(), 14, 1.0, 0.1, 1.0, 0.02);
        hint(p);
        PoliciaMod.sync(p);
    }

    /** Retira el tractor del jugador (modo albanil apagado, muerte, etc.). */
    static void retire(ServerPlayer p) {
        TractorEntity t = ACTIVE.get(p.getUUID());
        if (t != null && !t.isRemoved() && t.level() instanceof ServerLevel sl) t.finish(sl);
        ACTIVE.remove(p.getUUID());
        p.getPersistentData().putInt("alb_tact", 0);
    }

    static void lamp(ServerPlayer p) {
        if (!(p.getVehicle() instanceof TractorEntity t)) return;
        boolean on = !t.lampOn();
        t.setLamp(on);
        if (!on) t.clearLight();
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.8f, on ? 1.4f : 1.0f);
        PoliciaMod.msg(p, on ? "Luces del tractor encendidas" : "Luces del tractor apagadas");
    }

    static void arm(ServerPlayer p, int dir) {
        if (p.getVehicle() instanceof TractorEntity t) { t.armDir = dir; t.armT = 3; }
    }

    // ---------- entidad ----------
    public static class TractorEntity extends Entity {
        static final EntityDataAccessor<Float> ARM = SynchedEntityData.defineId(TractorEntity.class, EntityDataSerializers.FLOAT);
        static final EntityDataAccessor<Float> WH = SynchedEntityData.defineId(TractorEntity.class, EntityDataSerializers.FLOAT);
        static final EntityDataAccessor<Float> STEER = SynchedEntityData.defineId(TractorEntity.class, EntityDataSerializers.FLOAT);
        static final EntityDataAccessor<Boolean> LAMP = SynchedEntityData.defineId(TractorEntity.class, EntityDataSerializers.BOOLEAN);

        UUID owner;
        double speed;
        int idle, armDir, armT, fullMsg;
        public float arm, prevArm, wh, prevWH, steer, prevSteer;
        BlockPos light;
        double lx, ly, lz;
        float lyr;
        int lsteps;

        public TractorEntity(EntityType<? extends TractorEntity> type, Level level) {
            super(type, level);
            this.noCulling = true;
            this.noPhysics = true;
            this.setNoGravity(true);
        }

        @Override
        protected void defineSynchedData() {
            this.entityData.define(ARM, 0.0f);
            this.entityData.define(WH, 0.0f);
            this.entityData.define(STEER, 0.0f);
            this.entityData.define(LAMP, false);
        }
        @Override
        protected void readAdditionalSaveData(CompoundTag t) { }
        @Override
        protected void addAdditionalSaveData(CompoundTag t) { }
        @Override
        public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
        @Override
        public double getPassengersRidingOffset() { return 2.65; }
        @Override
        protected boolean canAddPassenger(Entity e) { return this.getPassengers().isEmpty(); }
        @Override
        public boolean isPickable() { return getFirstPassenger() == null && !isRemoved(); }
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
        public net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player pl, net.minecraft.world.InteractionHand hand) {
            if (pl.isPassenger()) return net.minecraft.world.InteractionResult.PASS;
            if (owner != null && !owner.equals(pl.getUUID())) return net.minecraft.world.InteractionResult.PASS;
            if (!level().isClientSide && pl instanceof ServerPlayer sp) {
                if (sp.isShiftKeyDown()) { openInv(sp); }
                else if (getFirstPassenger() == null) {
                    sp.startRiding(this, true);
                    level().playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 1.0f, 0.6f);
                    hint(sp);
                }
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
        }

        @Override
        public void lerpTo(double x, double y, double z, float yr, float xr, int steps, boolean teleport) {
            lx = x; ly = y; lz = z; lyr = yr; lsteps = 3;
        }

        public boolean lampOn() { return entityData.get(LAMP); }
        void setLamp(boolean on) { entityData.set(LAMP, on); }

        // ---------- terreno ----------
        double ground(double x, double z, double refY) {
            int bx = Mth.floor(x), bz = Mth.floor(z);
            int top = Mth.floor(refY) + 4;
            int bot = Math.max(Mth.floor(refY) - 14, level().getMinBuildHeight());
            BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
            for (int y = top; y >= bot; y--) {
                mp.set(bx, y, bz);
                BlockState bs = level().getBlockState(mp);
                if (bs.is(BlockTags.LEAVES) || bs.is(BlockTags.LOGS)) continue;
                FluidState fs = bs.getFluidState();
                if (!fs.isEmpty()) return y + fs.getHeight(level(), mp);
                VoxelShape sh = bs.getCollisionShape(level(), mp);
                if (!sh.isEmpty()) return y + sh.max(Direction.Axis.Y);
            }
            return refY - 14;
        }

        /** Altura mas alta bajo las cuatro ruedas y el centro. */
        double footGround(double cx, double cz, float yaw, double ref) {
            double rad = Math.toRadians(yaw);
            double fx = -Math.sin(rad), fz = Math.cos(rad), px = Math.cos(rad), pz = Math.sin(rad);
            double best = -1.0e9;
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    double fo = b == 1 ? 1.5 : b * 1.7;
                    best = Math.max(best, ground(cx + px * a * 1.6 + fx * fo, cz + pz * a * 1.6 + fz * fo, ref));
                }
            }
            return best;
        }

        Vec3 dismountSpot() {
            double rad = Math.toRadians(getYRot());
            double x = getX() - Math.cos(rad) * 3.6, z = getZ() - Math.sin(rad) * 3.6;
            double g = ground(x, z, getY() + 2.0);
            return new Vec3(x, g + 0.05, z);
        }

        @Override
        public Vec3 getDismountLocationForPassenger(LivingEntity p) { return dismountSpot(); }

        @Override
        protected void removePassenger(Entity e) {
            super.removePassenger(e);
            e.fallDistance = 0.0f;
            if (e instanceof ServerPlayer sp) {
                sp.getPersistentData().putInt("pol_nofall", 100);
                if (!level().isClientSide) {
                    Vec3 d = dismountSpot();
                    sp.connection.teleport(d.x, d.y, d.z, sp.getYRot(), sp.getXRot());
                }
            }
        }

        @Override
        public void remove(Entity.RemovalReason r) {
            if (!level().isClientSide) clearLight();
            super.remove(r);
        }

        // ---------- celdas ----------
        List<BlockPos> cells(double nx, double ny, double nz, float yaw, double f0, double f1, double lat, double yLo, double yHi) {
            double rad = Math.toRadians(yaw), c = Math.cos(rad), s = Math.sin(rad);
            double ext = Math.max(Math.abs(f0), Math.abs(f1)) + lat;
            int x0 = Mth.floor(nx - ext), x1 = Mth.floor(nx + ext), z0 = Mth.floor(nz - ext), z1 = Mth.floor(nz + ext);
            int y0 = Mth.floor(ny + yLo), y1 = Mth.floor(ny + yHi - 0.001);
            List<BlockPos> out = new ArrayList<>();
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    double dx = x + 0.5 - nx, dz = z + 0.5 - nz;
                    double la = dx * c + dz * s, fw = -dx * s + dz * c;
                    if (Math.abs(la) > lat || fw < f0 || fw > f1) continue;
                    for (int y = y0; y <= y1; y++) out.add(new BlockPos(x, y, z));
                }
            }
            return out;
        }

        /** Suelo bajo los pies del tractor: no cuenta como obstaculo ni se rompe. */
        boolean floorCell(BlockState st, BlockPos bp, double ny) {
            VoxelShape sh = st.getCollisionShape(level(), bp);
            return !sh.isEmpty() && bp.getY() + sh.max(Direction.Axis.Y) <= ny + 0.12;
        }

        boolean breakable(ServerLevel sl, BlockPos bp, BlockState st, Set<Long> placed) {
            if (st.isAir()) return false;
            if (!st.getFluidState().isEmpty() && st.getBlock() instanceof LiquidBlock) return false;
            if (st.hasBlockEntity()) return false;
            if (st.getDestroySpeed(sl, bp) < 0.0f) return false;
            if (PoliciaAlbanil.LIVE.contains(bp.asLong())) return false;
            if (placed != null && placed.contains(bp.asLong())) return false;
            return PoliciaPlaced.natural(st);
        }

        /** Rompe lo natural que queda dentro de la pala y del cuerpo en la pose indicada. */
        void dig(ServerLevel sl, ServerPlayer driver, double nx, double ny, double nz, float yaw) {
            PoliciaPlaced pp = PoliciaPlaced.get(sl);
            Set<Long> placed = pp == null ? null : pp.set;
            List<BlockPos> all = cells(nx, ny, nz, yaw, A0, A1, AL, 0.05 + arm * 1.8, 4.6);
            all.addAll(cells(nx, ny, nz, yaw, B0, B1, BL, 0.3, 4.7));
            SimpleContainer inv = driver == null ? null : inv(driver);
            int broken = 0, fx = 0;
            boolean full = false;
            mute = true;
            try {
                for (BlockPos bp : all) {
                    if (broken >= 200) break;
                    BlockState st = sl.getBlockState(bp);
                    if (st.isAir() || !breakable(sl, bp, st, placed) || floorCell(st, bp, ny)) continue;
                    if (inv != null) {
                        for (ItemStack it : Block.getDrops(st, sl, bp, null, this, ItemStack.EMPTY)) {
                            if (!inv.addItem(it).isEmpty()) full = true;
                        }
                    }
                    if (fx < 24) { sl.destroyBlock(bp, false, this); fx++; }
                    else sl.setBlock(bp, Blocks.AIR.defaultBlockState(), 3);
                    broken++;
                }
            } finally {
                mute = false;
            }
            if (inv != null && broken > 0) saveInv(driver, inv);
            if (full && driver != null && tickCount - fullMsg > 200) {
                fullMsg = tickCount;
                PoliciaMod.msg(driver, "Inventario del tractor lleno: lo que rompe se pierde (U para vaciarlo)");
            }
        }

        /** Cuenta lo solido (no natural, o fuera del alcance de la pala) dentro del tractor en esa pose. */
        int solid(ServerLevel sl, double nx, double ny, double nz, float yaw) {
            int n = 0;
            List<BlockPos> all = cells(nx, ny, nz, yaw, A0, A1, AL, 0.05 + arm * 1.8, 4.6);
            all.addAll(cells(nx, ny, nz, yaw, B0, B1, BL, 0.3, 4.7));
            for (BlockPos bp : all) {
                BlockState st = sl.getBlockState(bp);
                if (st.isAir()) continue;
                VoxelShape sh = st.getCollisionShape(sl, bp);
                if (sh.isEmpty()) continue;
                if (bp.getY() + sh.max(Direction.Axis.Y) <= ny + 0.12) continue;
                n++;
            }
            return n;
        }

        // ---------- conduccion ----------
        void pushOut(Entity r) {
            double rad = Math.toRadians(getYRot()), c = Math.cos(rad), s = Math.sin(rad);
            for (net.minecraft.world.entity.player.Player pl : level().getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,
                    getBoundingBox().inflate(6.0, 0.0, 6.0), e -> e != r && !e.isSpectator())) {
                double dx = pl.getX() - getX(), dz = pl.getZ() - getZ();
                double lat = dx * c + dz * s, fw = -dx * s + dz * c - 0.6;
                if (Math.abs(lat) >= 2.7 || Math.abs(fw) >= 4.9 || pl.getY() > getY() + 4.8) continue;
                double nl = lat, nf = fw;
                double ol = 2.8 - Math.abs(lat), of = 5.0 - Math.abs(fw);
                if (ol <= of) nl = (lat < 0 ? -1 : 1) * 2.8; else nf = (fw < 0 ? -1 : 1) * 5.0;
                nf += 0.6;
                double nx = getX() + nl * c - nf * s, nz = getZ() + nl * s + nf * c;
                if (pl instanceof ServerPlayer sp) sp.connection.teleport(nx, pl.getY(), nz, pl.getYRot(), pl.getXRot());
                else pl.setPos(nx, pl.getY(), nz);
            }
        }

        void drive(ServerLevel sl, Entity r) {
            float fw = 0f, st = 0f;
            if (r instanceof LivingEntity le) { fw = le.zza; st = le.xxa; }
            if (Math.abs(fw) < 0.05f) fw = 0f;
            if (Math.abs(st) < 0.05f) st = 0f;
            if (fw != 0f) speed += fw * 0.04; else speed *= 0.85;
            speed = Mth.clamp(speed, -0.16, 0.30);
            if (Math.abs(speed) < 0.004) speed = 0.0;
            float dyaw = -st * (speed != 0.0 ? 2.0f : 1.4f);
            float nyaw = getYRot() + dyaw;
            double rad = Math.toRadians(nyaw);
            double nx = getX() - Math.sin(rad) * speed, nz = getZ() + Math.cos(rad) * speed;
            ServerPlayer drv = r instanceof ServerPlayer sp && sp.getUUID().equals(owner) ? sp : null;
            if (speed != 0.0 || dyaw != 0f) {
                dig(sl, drv, nx, getY(), nz, nyaw);
                if (solid(sl, nx, getY(), nz, nyaw) > solid(sl, getX(), getY(), getZ(), getYRot())) {
                    speed = 0.0;
                    nx = getX(); nz = getZ();
                    if (dyaw != 0f) {
                        dig(sl, drv, nx, getY(), nz, nyaw);
                        if (solid(sl, nx, getY(), nz, nyaw) > solid(sl, getX(), getY(), getZ(), getYRot())) { nyaw = getYRot(); dyaw = 0f; }
                    } else {
                        nyaw = getYRot();
                    }
                }
            }
            double g = footGround(nx, nz, nyaw, getY());
            if (g - getY() > 2.3) {
                speed = 0.0;
                nx = getX(); nz = getZ(); nyaw = getYRot(); dyaw = 0f;
                g = footGround(nx, nz, nyaw, getY());
            }
            double ny2 = getY() + Mth.clamp(g - getY(), -0.7, 0.5);
            setPos(nx, ny2, nz);
            setYRot(nyaw);
            wh += (float) (speed / WHEEL_R);
            steer += (st * 28.0f - steer) * 0.3f;
        }

        @Override
        public void tick() {
            super.tick();
            prevWH = wh;
            prevArm = arm;
            prevSteer = steer;
            if (level().isClientSide) {
                if (lsteps > 0) {
                    double k = 1.0 / lsteps;
                    setPos(getX() + (lx - getX()) * k, getY() + (ly - getY()) * k, getZ() + (lz - getZ()) * k);
                    setYRot(getYRot() + (float) Mth.wrapDegrees(lyr - getYRot()) * (float) k);
                    lsteps--;
                }
                wh = entityData.get(WH);
                arm = entityData.get(ARM);
                steer = entityData.get(STEER);
                return;
            }
            setDeltaMovement(Vec3.ZERO);
            ServerLevel sl = (ServerLevel) level();
            Entity r = getFirstPassenger();
            if (armT > 0) {
                armT--;
                arm = Mth.clamp(arm + armDir * 0.035f, 0.0f, 1.0f);
            }
            if (r != null) {
                drive(sl, r);
                pushOut(r);
                idle = 0;
            } else {
                speed = 0.0;
                steer *= 0.7f;
                idle++;
                pushOut(null);
            }
            entityData.set(WH, wh);
            entityData.set(ARM, arm);
            entityData.set(STEER, steer);
            if (r != null) {
                if (Math.abs(speed) > 0.02 && tickCount % 5 == 0) {
                    level().playSound(null, getX(), getY(), getZ(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.6f, 0.4f);
                } else if (tickCount % 12 == 0) {
                    level().playSound(null, getX(), getY(), getZ(), SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 0.35f, 0.4f);
                }
                if (tickCount % 4 == 0) {
                    double rad = Math.toRadians(getYRot());
                    sl.sendParticles(ParticleTypes.SMOKE, getX() + Math.sin(rad) * 3.0 + Math.cos(rad) * 0.8, getY() + 4.4,
                            getZ() - Math.cos(rad) * 3.0 + Math.sin(rad) * 0.8, 1, 0.05, 0.1, 0.05, 0.01);
                }
            }
            if (lampOn() && tickCount % 4 == 0) lampTick(sl, r);
            if (tickCount % 20 == 0 && owner != null) {
                ServerPlayer o = sl.getServer().getPlayerList().getPlayer(owner);
                if (o == null || !o.isAlive()) { finish(sl); return; }
            }
            if (idle > IDLE || tickCount > 72000) finish(sl);
        }

        // ---------- linterna ----------
        void lampTick(ServerLevel sl, Entity r) {
            Vec3 eye, dir;
            if (r != null) {
                eye = r.getEyePosition();
                dir = r.getLookAngle();
            } else {
                double rad = Math.toRadians(getYRot());
                dir = new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
                eye = new Vec3(getX(), getY() + 2.4, getZ()).add(dir.scale(3.0));
            }
            Vec3 end = eye.add(dir.scale(48.0));
            BlockHitResult hit = sl.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            BlockPos pos = hit.getType() == HitResult.Type.MISS ? BlockPos.containing(end) : hit.getBlockPos().relative(hit.getDirection());
            if (light != null && light.equals(pos)) return;
            if (!sl.getBlockState(pos).isAir()) return;
            clearLight();
            sl.setBlock(pos, Blocks.LIGHT.defaultBlockState(), 3);
            light = pos;
        }

        void clearLight() {
            if (light == null) return;
            if (level().getBlockState(light).is(Blocks.LIGHT)) level().setBlock(light, Blocks.AIR.defaultBlockState(), 3);
            light = null;
        }

        void finish(ServerLevel sl) {
            clearLight();
            sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.6, getZ(), 18, 1.0, 0.4, 1.0, 0.03);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 1.0f, 0.5f);
            if (owner != null) {
                ACTIVE.remove(owner);
                ServerPlayer o = sl.getServer().getPlayerList().getPlayer(owner);
                if (o != null) {
                    o.getPersistentData().putInt("alb_tcd", CD);
                    o.getPersistentData().putInt("alb_tact", 0);
                    PoliciaMod.msg(o, "Tractor retirado");
                    PoliciaMod.sync(o);
                }
            }
            ejectPassengers();
            discard();
        }
    }
}
