package com.example.policia;

import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.UUID;

/** Habilidades 6 a 9: Esposas, Perro K9, Sirena y torreta, Dron de vigilancia. */
public class PoliciaExtra {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "policia");
    public static final RegistryObject<EntityType<K9Entity>> K9 = ENTITIES.register("k9",
            () -> EntityType.Builder.<K9Entity>of(K9Entity::new, MobCategory.MISC)
                    .sized(0.6f, 0.85f).clientTrackingRange(10).noSave().build("policia:k9"));
    public static final RegistryObject<EntityType<SirenEntity>> SIREN = ENTITIES.register("sirena",
            () -> EntityType.Builder.<SirenEntity>of(SirenEntity::new, MobCategory.MISC)
                    .sized(1.6f, 1.2f).clientTrackingRange(10).updateInterval(2).noSave().build("policia:sirena"));
    public static final RegistryObject<EntityType<DroneEntity>> DRONE = ENTITIES.register("dron",
            () -> EntityType.Builder.<DroneEntity>of(DroneEntity::new, MobCategory.MISC)
                    .sized(0.7f, 0.3f).fireImmune().clientTrackingRange(10).updateInterval(2).noSave().build("policia:dron"));

    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, "policia");
    public static final RegistryObject<MobEffect> CUFF = EFFECTS.register("esposado", CuffEffect::new);

    /** Efecto marcador: el cliente lo usa para dibujar las esposas en el objetivo. */
    public static class CuffEffect extends MobEffect {
        public CuffEffect() { super(MobEffectCategory.HARMFUL, 0xB0B4BC); }
    }

    /** Enfriamiento (ticks) de cada habilidad, por indice de la rama. */
    static final int[] CD = {0, 0, 0, 0, 0, 600, 1200, 800, 1200, 980};
    static final int CUFF_T = 300;        // 15 s esposado
    static final int K9_LIFE = 900;       // 45 s
    static final int SIREN_LIFE = 400;    // 20 s
    static final int DRONE_LIFE = 900;    // 45 s
    static final double SIREN_R = 14.0;
    static final double DRONE_R = 32.0;
    static final DustParticleOptions CUFF_DUST = new DustParticleOptions(new org.joml.Vector3f(0.75f, 0.78f, 0.85f), 1.0f);

    public static void attrs(EntityAttributeCreationEvent e) {
        e.put(K9.get(), Wolf.createAttributes().add(Attributes.MAX_HEALTH, 30.0).add(Attributes.ATTACK_DAMAGE, 8.0).build());
    }

    // ---------- enfriamientos ----------
    static String key(int s) { return "pol_x" + s; }

    static int cd(Player p, int s) { return s >= 5 && s <= 9 ? p.getPersistentData().getInt(key(s)) : 0; }

    /** Descuenta los enfriamientos; devuelve true si alguna habilidad quedo lista. */
    static boolean tickCd(ServerPlayer p) {
        boolean ch = false;
        CompoundTag d = p.getPersistentData();
        for (int s = 5; s <= 9; s++) {
            int v = d.getInt(key(s));
            if (v > 0) {
                d.putInt(key(s), v - 1);
                if (v == 1) { PoliciaMod.msg(p, PoliciaMod.SKILLS[s] + " listo"); ch = true; }
            }
        }
        return ch;
    }

    static void use(ServerPlayer p, int s) {
        int c = cd(p, s);
        if (c > 0) { PoliciaMod.msg(p, PoliciaMod.SKILLS[s] + " en enfriamiento: " + (c + 19) / 20 + " s"); return; }
        boolean ok;
        switch (s) {
            case 5: ok = cuff(p); break;
            case 6: ok = k9(p); break;
            case 7: ok = siren(p); break;
            case 9: ok = PoliciaBubble.use(p); break;
            default: ok = drone(p); break;
        }
        if (ok) {
            p.getPersistentData().putInt(key(s), CD[s]);
            PoliciaMod.sync(p);
        }
    }

    static void poof(Entity e) {
        if (e.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
        }
        e.discard();
    }

    // ---------- esposas ----------
    static boolean cuff(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 end = eye.add(look.scale(12.0));
        AABB box = p.getBoundingBox().expandTowards(look.scale(12.0)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p.level(), p, eye, end, box,
                en -> en instanceof LivingEntity && en.isAlive() && !en.isSpectator() && en.isPickable()
                        && !(en instanceof PoliciaRefuerzo.AgentEntity) && !(en instanceof K9Entity));
        if (hit == null || !(hit.getEntity() instanceof LivingEntity t)) {
            PoliciaMod.msg(p, "Apunta a un objetivo (hasta 12 m)");
            return false;
        }
        if (p.level().clip(new ClipContext(eye, hit.getLocation(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS) {
            PoliciaMod.msg(p, "Hay un obstaculo en el camino");
            return false;
        }
        CompoundTag td = t.getPersistentData();
        td.putInt("pol_cuff", CUFF_T);
        td.putUUID("pol_cuffo", p.getUUID());
        if (t instanceof Mob m && !m.isNoAi()) {
            m.setNoAi(true);
            td.putBoolean("pol_cuffai", true);
        }
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CUFF_T, 6, false, false));
        t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, CUFF_T, 4, false, false));
        t.addEffect(new MobEffectInstance(CUFF.get(), CUFF_T, 0, false, false, false));
        t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, CUFF_T, 0, false, false));
        ServerLevel sl = p.serverLevel();
        sl.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.2f, 0.9f);
        sl.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 1.0f, 1.3f);
        sl.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + 0.5, t.getZ(), 14, 0.3, 0.4, 0.3, 0.1);
        PoliciaMod.msg(p, "Esposado: " + t.getName().getString() + " (15 s): arrastralo donde quieras");
        return true;
    }

    @SubscribeEvent
    public void cuffTick(LivingEvent.LivingTickEvent e) {
        LivingEntity t = e.getEntity();
        if (t.level().isClientSide) return;
        CompoundTag d = t.getPersistentData();
        int c = d.getInt("pol_cuff");
        if (c <= 0) return;
        c--;
        d.putInt("pol_cuff", c);
        Vec3 v = t.getDeltaMovement();
        double vy = Math.min(v.y, 0.0);
        double vx = 0.0, vz = 0.0;
        if (d.hasUUID("pol_cuffo") && t.level() instanceof ServerLevel sl0) {
            ServerPlayer o = sl0.getServer().getPlayerList().getPlayer(d.getUUID("pol_cuffo"));
            if (o != null && o != t && o.level() == t.level()) {
                double dx = o.getX() - t.getX(), dz = o.getZ() - t.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 2.6 && dist < 40.0) {
                    double sp = Math.min(0.42, (dist - 2.2) * 0.25);
                    vx = dx / dist * sp;
                    vz = dz / dist * sp;
                    if (t.horizontalCollision && t.onGround()) vy = 0.42;
                }
            }
        }
        t.setDeltaMovement(vx, vy, vz);
        if (t instanceof ServerPlayer && (vx != 0.0 || vz != 0.0)) t.hurtMarked = true;
        if (c == 0) {
            t.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, true));
            d.remove("pol_cuffo");
        }
        if (c == 0 && d.getBoolean("pol_cuffai") && t instanceof Mob m2) {
            m2.setNoAi(false);
            d.remove("pol_cuffai");
        }
    }

    /** El policia que esposo a un jugador puede abrir su inventario (clic derecho sobre el esposado). */
    @SubscribeEvent
    public void cuffInspect(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer o) || !(e.getTarget() instanceof ServerPlayer tp) || o == tp) return;
        if (e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        CompoundTag td = tp.getPersistentData();
        if (td.getInt("pol_cuff") <= 0 || !td.hasUUID("pol_cuffo") || !td.getUUID("pol_cuffo").equals(o.getUUID())) return;
        e.setCanceled(true);
        o.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inv, pl) -> new net.minecraft.world.inventory.ChestMenu(net.minecraft.world.inventory.MenuType.GENERIC_9x4, id, inv, tp.getInventory(), 4),
                net.minecraft.network.chat.Component.literal("Inventario de " + tp.getName().getString())));
    }

    static boolean cuffed(Entity e) {
        return e instanceof LivingEntity l && l.getPersistentData().getInt("pol_cuff") > 0;
    }

    /** Un esposado no puede hacer dano, golpear, usar objetos ni interactuar con el entorno. */
    @SubscribeEvent
    public void cuffedAttack(LivingAttackEvent e) {
        if (cuffed(e.getSource().getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void cuffedHit(AttackEntityEvent e) {
        if (cuffed(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void cuffedUse(PlayerInteractEvent e) {
        if (cuffed(e.getEntity()) && e.isCancelable()) e.setCanceled(true);
    }

    @SubscribeEvent
    public void cuffedBreak(BlockEvent.BreakEvent e) {
        if (cuffed(e.getPlayer())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void cuffedItem(LivingEntityUseItemEvent.Start e) {
        if (cuffed(e.getEntity())) e.setCanceled(true);
    }

    // ---------- perro K9 ----------
    static boolean k9(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        K9Entity w = K9.get().create(sl);
        if (w == null) return false;
        Vec3 look = p.getLookAngle();
        w.moveTo(p.getX() + look.x * 1.8, p.getY(), p.getZ() + look.z * 1.8, p.getYRot(), 0.0f);
        if (!sl.noCollision(w)) w.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0.0f);
        w.tame(p);
        w.getAttribute(Attributes.MAX_HEALTH).setBaseValue(30.0);
        w.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(8.0);
        w.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.42);
        w.setHealth(30.0f);
        w.setCollarColor(DyeColor.BLUE);
        w.setOrderedToSit(false);
        sl.addFreshEntity(w);
        int n = 0;
        for (LivingEntity m : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24.0), x2 -> x2 instanceof Enemy && x2.isAlive())) {
            m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
            n++;
        }
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WOLF_HOWL, SoundSource.PLAYERS, 1.0f, 1.0f);
        PoliciaMod.msg(p, "Perro K9 en servicio" + (n > 0 ? ": " + n + " amenazas marcadas" : ""));
        return true;
    }

    public static class K9Entity extends Wolf {
        int life;

        public K9Entity(EntityType<? extends K9Entity> type, Level level) {
            super(type, level);
            this.setPersistenceRequired();
        }

        @Override
        protected void registerGoals() {
            super.registerGoals();
            this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false, t -> t instanceof Enemy));
        }

        @Override
        public InteractionResult mobInteract(Player p, InteractionHand h) { return InteractionResult.PASS; }

        @Override
        public boolean hurt(DamageSource s, float amount) {
            Entity src = s.getEntity();
            if (src != null && getOwnerUUID() != null && src.getUUID().equals(getOwnerUUID())) return false;
            return super.hurt(s, amount);
        }

        @Override
        public boolean removeWhenFarAway(double d) { return false; }

        @Override
        public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }

        @Override
        public void tick() {
            super.tick();
            if (level().isClientSide) return;
            life++;
            if (life > K9_LIFE) { poof(this); return; }
            if (life % 20 == 0) {
                LivingEntity o = getOwner();
                if (o == null || !o.isAlive()) poof(this);
            }
        }
    }

    // ---------- sirena y torreta ----------
    static boolean siren(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        SirenEntity s = SIREN.get().create(sl);
        if (s == null) return false;
        Vec3 look = p.getLookAngle();
        Vec3 fl = new Vec3(look.x, 0.0, look.z);
        fl = fl.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : fl.normalize();
        s.owner = p.getUUID();
        s.moveTo(p.getX() + fl.x * 3.0, p.getY() + 0.3, p.getZ() + fl.z * 3.0, p.getYRot(), 0.0f);
        if (!sl.noCollision(s)) s.moveTo(p.getX() + fl.x * 3.0, p.getY() + 1.3, p.getZ() + fl.z * 3.0, p.getYRot(), 0.0f);
        sl.addFreshEntity(s);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 0.8f);
        PoliciaMod.msg(p, "Patrulla desplegada: los enemigos cercanos huyen (20 s)");
        return true;
    }

    public static class SirenEntity extends Entity {
        static final EntityDataAccessor<Float> AIM = SynchedEntityData.defineId(SirenEntity.class, EntityDataSerializers.FLOAT);
        UUID owner;
        int life;
        int lastShot = -100;

        public SirenEntity(EntityType<? extends SirenEntity> type, Level level) {
            super(type, level);
            this.noCulling = true;
        }

        float aim() { return this.entityData.get(AIM); }

        @Override
        protected void defineSynchedData() { this.entityData.define(AIM, 0.0f); }

        @Override
        protected void readAdditionalSaveData(CompoundTag t) { }

        @Override
        protected void addAdditionalSaveData(CompoundTag t) { }

        @Override
        public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }

        @Override
        public boolean isPickable() { return false; }

        @Override
        public boolean canBeCollidedWith() { return true; }

        @Override
        public boolean hurt(DamageSource s, float a) { return false; }

        @Override
        public void tick() {
            super.tick();
            if (!onGround()) setDeltaMovement(0.0, getDeltaMovement().y - 0.08, 0.0);
            else setDeltaMovement(Vec3.ZERO);
            move(MoverType.SELF, getDeltaMovement());
            if (level().isClientSide) return;
            ServerLevel sl = (ServerLevel) level();
            life++;
            ServerPlayer o = owner == null ? null : sl.getServer().getPlayerList().getPlayer(owner);
            if (life > SIREN_LIFE || o == null || !o.isAlive() || o.level() != level()) { poof(this); return; }
            if (life % 10 == 1) {
                sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 2.0f, (life / 10) % 2 == 0 ? 1.9f : 1.3f);
            }
            if (life % 3 == 0) scare(sl);
            if (life % 16 == 0) shoot(sl, o);
            if (life - lastShot > 24 && life % 2 == 0) this.entityData.set(AIM, (float) ((life * 7) % 360 - 180));
        }

        /** Los enemigos cercanos huyen de la sirena. */
        void scare(ServerLevel sl) {
            for (Mob m : sl.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(SIREN_R),
                    x -> x.isAlive() && x.getMaxHealth() < 150.0f && !(x instanceof PoliciaRefuerzo.AgentEntity) && !(x instanceof K9Entity)
                            && (x instanceof Enemy || (x.getTarget() != null && x.getTarget().getUUID().equals(owner))))) {
                Vec3 away = m.position().subtract(position()).multiply(1.0, 0.0, 1.0);
                away = away.lengthSqr() < 0.01 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
                m.setTarget(null);
                Vec3 dest = m.position().add(away.scale(12.0));
                m.getNavigation().moveTo(dest.x, dest.y, dest.z, 1.5);
                Vec3 dm = m.getDeltaMovement();
                if (dm.horizontalDistanceSqr() < 0.09) m.setDeltaMovement(dm.x + away.x * 0.12, dm.y, dm.z + away.z * 0.12);
                if (life % 12 == 0) sl.sendParticles(ParticleTypes.SMOKE, m.getX(), m.getY() + m.getBbHeight() + 0.2, m.getZ(), 3, 0.2, 0.1, 0.2, 0.01);
            }
        }

        /** La torreta dispara al enemigo mas cercano a tiro. */
        void shoot(ServerLevel sl, ServerPlayer o) {
            Vec3 from = new Vec3(getX(), getY() + 1.1, getZ());
            LivingEntity best = null;
            double bd = 18.0 * 18.0;
            for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(18.0), x -> x instanceof Enemy && x.isAlive())) {
                double dd = t.distanceToSqr(this);
                if (dd < bd && sl.clip(new ClipContext(from, t.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS) {
                    bd = dd;
                    best = t;
                }
            }
            if (best == null) return;
            lastShot = life;
            Vec3 to = best.position().add(0.0, best.getBbHeight() * 0.6, 0.0).subtract(from);
            this.entityData.set(AIM, (float) (Mth.atan2(-to.x, to.z) * 57.29577951308232));
            Arrow a = new Arrow(EntityType.ARROW, sl);
            a.setPos(from.x, from.y, from.z);
            a.setOwner(o);
            a.setBaseDamage(4.0);
            a.pickup = AbstractArrow.Pickup.DISALLOWED;
            a.shoot(to.x, to.y + Math.sqrt(to.x * to.x + to.z * to.z) * 0.05, to.z, 2.6f, 0.6f);
            sl.addFreshEntity(a);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0f, 1.2f);
        }
    }

    // ---------- dron de vigilancia ----------
    static boolean drone(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        DroneEntity d = DRONE.get().create(sl);
        if (d == null) return false;
        d.owner = p.getUUID();
        d.moveTo(p.getX(), p.getY() + 2.0, p.getZ(), p.getYRot(), 0.0f);
        sl.addFreshEntity(d);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.8f);
        PoliciaMod.msg(p, "Dron de vigilancia en el aire (45 s)");
        return true;
    }

    public static class DroneEntity extends Entity {
        UUID owner;
        int life;

        public DroneEntity(EntityType<? extends DroneEntity> type, Level level) {
            super(type, level);
            this.noPhysics = true;
            this.noCulling = true;
        }

        @Override
        protected void defineSynchedData() { }

        @Override
        protected void readAdditionalSaveData(CompoundTag t) { }

        @Override
        protected void addAdditionalSaveData(CompoundTag t) { }

        @Override
        public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }

        @Override
        public boolean isPickable() { return false; }

        @Override
        public boolean hurt(DamageSource s, float a) { return false; }

        @Override
        public void tick() {
            super.tick();
            if (level().isClientSide) return;
            ServerLevel sl = (ServerLevel) level();
            life++;
            ServerPlayer o = owner == null ? null : sl.getServer().getPlayerList().getPlayer(owner);
            if (life > DRONE_LIFE || o == null || !o.isAlive() || o.level() != level()) { poof(this); return; }
            double ya = Math.toRadians(o.getYRot());
            double fx = -Math.sin(ya), fz = Math.cos(ya);
            double rx = -Math.cos(ya), rz = -Math.sin(ya);
            Vec3 tgt = new Vec3(o.getX() + rx * 1.3 - fx * 0.3, o.getY() + 3.0 + Math.sin(life * 0.1) * 0.15, o.getZ() + rz * 1.3 - fz * 0.3);
            Vec3 dv = tgt.subtract(position());
            if (dv.lengthSqr() > 400.0) setPos(tgt.x, tgt.y, tgt.z);
            else setPos(getX() + dv.x * 0.15, getY() + dv.y * 0.15, getZ() + dv.z * 0.15);
            setYRot(o.getYRot());
            if (life % 20 == 0) {
                int n = 0;
                for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(DRONE_R),
                        x -> x.isAlive() && (x instanceof Enemy || (x instanceof Mob mm && mm.getTarget() != null && mm.getTarget().getUUID().equals(owner))))) {
                    t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 45, 0, false, false));
                    n++;
                }
                if (life % 100 == 0 && n > 0) PoliciaMod.msg(o, "Dron: " + n + (n == 1 ? " amenaza detectada" : " amenazas detectadas"));
            }
        }
    }
}
