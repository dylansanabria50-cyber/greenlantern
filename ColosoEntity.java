package com.example.greenlantern;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.BooleanSupplier;

/**
 * Coloso de Voluntad: robot de energia de 11 bloques que se maneja desde la cabina del pecho.
 * W/S/A/D camina (Ctrl corre), Espacio salta, clic izquierdo combo de puños, G golpe sismico, V cañon,
 * B cohetes de hombro, Z (mantener) escudo. Shift para bajarse: el coloso se disuelve.
 */
public class ColosoEntity extends Entity {
    public static final int LIFE = 1800, COST = 40;
    public static final float MAX_VIDA = 150f;
    public static final int ANIM_NADA = 0, ANIM_PUNOS = 1, ANIM_SISMICO = 2, ANIM_CANON = 3, ANIM_COHETES = 4;
    public static final int[] DURACION = {0, 30, 40, 30, 30};

    private static final EntityDataAccessor<Integer> ANIM = SynchedEntityData.defineId(ColosoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANIM_T = SynchedEntityData.defineId(ColosoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ESCUDO = SynchedEntityData.defineId(ColosoEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> VIDA = SynchedEntityData.defineId(ColosoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> EDAD = SynchedEntityData.defineId(ColosoEntity.class, EntityDataSerializers.INT);

    /** Los pone el cliente (teclas de correr y saltar) — en el servidor quedan en falso. */
    public static BooleanSupplier CORRER = () -> false, SALTAR = () -> false;

    private int age;
    private boolean tuvoPiloto;
    // solo en el cliente, para la animacion de caminar
    public float paso, pasoO, velAnim;

    public ColosoEntity(EntityType<? extends ColosoEntity> type, Level level) {
        super(type, level);
        this.setMaxUpStep(1.6f);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(ANIM, 0);
        this.entityData.define(ANIM_T, 0);
        this.entityData.define(ESCUDO, false);
        this.entityData.define(VIDA, MAX_VIDA);
        this.entityData.define(EDAD, 0);
    }

    public int anim() { return this.entityData.get(ANIM); }
    public int animInicio() { return this.entityData.get(ANIM_T); }
    public boolean escudo() { return this.entityData.get(ESCUDO); }
    public float vida() { return this.entityData.get(VIDA); }
    public int edad() { return this.entityData.get(EDAD); }

    @Override protected void readAdditionalSaveData(CompoundTag tag) { }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { }
    @Override public boolean shouldBeSaved() { return false; }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }

    @Override public boolean isPickable() { return !this.isRemoved(); }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override protected boolean canAddPassenger(Entity passenger) { return this.getPassengers().isEmpty(); }
    @Override public double getPassengersRidingOffset() { return 6.9; }

    @Override
    public LivingEntity getControllingPassenger() {
        Entity e = this.getFirstPassenger();
        return e instanceof LivingEntity le ? le : null;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (!this.level().isClientSide) return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isRemoved()) return false;
        if (source.getEntity() == this || source.getDirectEntity() == this) return false;
        if (source.getEntity() != null && source.getEntity() == this.getControllingPassenger()) return false;
        float d = this.escudo() ? amount * 0.2f : amount;
        this.entityData.set(VIDA, Math.max(0f, this.vida() - d));
        ServerLevel sl = (ServerLevel) this.level();
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + 5, this.getZ(), 12, 1.0, 2.0, 1.0, 0.2);
        this.level().playSound(null, this.blockPosition(), this.escudo() ? SoundEvents.AMETHYST_BLOCK_HIT : SoundEvents.IRON_GOLEM_HURT, SoundSource.NEUTRAL, 1.0f, 0.6f);
        if (this.vida() <= 0f) disolver("El coloso no aguanto mas");
        return true;
    }

    // ---------- acciones del piloto (servidor) ----------
    public void accion(ServerPlayer p, int a) {
        if (this.getControllingPassenger() != p) return;
        switch (a) {
            case 10 -> empezar(ANIM_PUNOS, SoundEvents.IRON_GOLEM_ATTACK);
            case 11 -> empezar(ANIM_CANON, SoundEvents.BEACON_POWER_SELECT);
            case 12 -> empezar(ANIM_COHETES, SoundEvents.FIREWORK_ROCKET_LAUNCH);
            case 13 -> { if (this.onGround()) empezar(ANIM_SISMICO, SoundEvents.RAVAGER_ROAR); }
            case 14 -> this.entityData.set(ESCUDO, true);
            case 15 -> this.entityData.set(ESCUDO, false);
            default -> { }
        }
    }

    private void empezar(int anim, net.minecraft.sounds.SoundEvent sonido) {
        if (this.anim() != ANIM_NADA) return;
        this.entityData.set(ANIM, anim);
        this.entityData.set(ANIM_T, this.age);
        this.level().playSound(null, this.blockPosition(), sonido, SoundSource.PLAYERS, 1.5f, 0.7f);
    }

    @Override
    public void tick() {
        super.tick();
        this.age++;
        LivingEntity c = this.getControllingPassenger();

        if (!this.level().isClientSide) {
            this.entityData.set(EDAD, this.age);
            if (c != null) tuvoPiloto = true;
            if (c == null && (tuvoPiloto || this.age > 60)) { disolver(null); return; }
            if (this.age > LIFE) { disolver("Se acabo el tiempo del coloso"); return; }
            int a = this.anim();
            if (a != ANIM_NADA) {
                int t = this.age - this.animInicio();
                golpes(a, t, c);
                if (t >= DURACION[a]) this.entityData.set(ANIM, ANIM_NADA);
            }
            if (c instanceof ServerPlayer sp && this.age % 20 == 0) {
                sp.displayClientMessage(Component.literal("§aColoso §7| vida " + Math.round(this.vida()) + "/" + Math.round(MAX_VIDA)
                        + " | " + ((LIFE - this.age) / 20) + " s | clic: puños, G: sismico, V: cañon, B: cohetes, Z: escudo, Shift: bajar"), true);
            }
            if (this.age % 3 == 0) {
                Vec3 atras = Vec3.directionFromRotation(0, this.getYRot()).scale(-1.6);
                ((ServerLevel) this.level()).sendParticles(RingPowers.GREEN, this.getX() + atras.x, this.getY() + 7.2, this.getZ() + atras.z, 2, 0.4, 0.4, 0.4, 0);
            }
        } else {
            double dx = this.getX() - this.xo, dz = this.getZ() - this.zo;
            float v = (float) Math.sqrt(dx * dx + dz * dz);
            this.velAnim += (Math.min(1f, v * 4f) - this.velAnim) * 0.3f;
            this.pasoO = this.paso;
            this.paso += v * 0.55f;
        }

        if (c != null && this.isControlledByLocalInstance()) {
            // gira despacio hacia donde mira el piloto
            float dif = Mth.wrapDegrees(c.getYRot() - this.getYRot());
            this.setYRot(this.getYRot() + Mth.clamp(dif, -7f, 7f));
            boolean ocupado = this.anim() == ANIM_SISMICO;
            float vel = ocupado ? 0f : (CORRER.getAsBoolean() ? 0.42f : 0.24f) * (this.escudo() ? 0.6f : 1f);
            Vec3 frente = Vec3.directionFromRotation(0, this.getYRot());
            Vec3 lado = new Vec3(frente.z, 0, -frente.x);
            Vec3 mov = frente.scale(c.zza * vel).add(lado.scale(c.xxa * vel * 0.7));
            Vec3 m = this.getDeltaMovement();
            double vy = m.y - 0.08;
            if (this.onGround() && SALTAR.getAsBoolean() && !ocupado) vy = 0.95;
            this.setDeltaMovement(m.x * 0.5 + mov.x * 0.5, vy * 0.98, m.z * 0.5 + mov.z * 0.5);
            this.move(MoverType.SELF, this.getDeltaMovement());
            if (this.onGround()) this.setDeltaMovement(this.getDeltaMovement().multiply(1, 0, 1));
        } else if (c == null && !this.level().isClientSide) {
            this.setDeltaMovement(this.getDeltaMovement().add(0, -0.08, 0));
            this.move(MoverType.SELF, this.getDeltaMovement());
        }
    }

    /** Momento exacto de cada golpe dentro de la animacion. */
    private void golpes(int a, int t, LivingEntity piloto) {
        ServerLevel sl = (ServerLevel) this.level();
        Vec3 frente = Vec3.directionFromRotation(0, this.getYRot());
        Player pp = piloto instanceof Player x ? x : null;
        if (a == ANIM_PUNOS && (t == 7 || t == 17 || t == 27)) {
            Vec3 puno = this.position().add(frente.scale(4.0)).add(0, 4.5, 0);
            for (LivingEntity le : objetivos(puno, 3.8)) {
                daño(le, pp, 9f);
                le.setDeltaMovement(le.getDeltaMovement().add(frente.x * 1.6, 0.5, frente.z * 1.6));
                le.hurtMarked = true;
            }
            sl.sendParticles(ParticleTypes.EXPLOSION, puno.x, puno.y, puno.z, 1, 0, 0, 0, 0);
            sl.sendParticles(RingPowers.GREEN, puno.x, puno.y, puno.z, 25, 0.8, 0.8, 0.8, 0.1);
            sl.playSound(null, puno.x, puno.y, puno.z, SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 1.4f, 0.6f);
        } else if (a == ANIM_SISMICO && t == 24) {
            Vec3 c = this.position();
            for (LivingEntity le : objetivos(c, 9.0)) {
                Vec3 rel = le.position().subtract(c);
                Vec3 dir = rel.horizontalDistance() < 0.01 ? frente : new Vec3(rel.x, 0, rel.z).normalize();
                daño(le, pp, 12f);
                le.setDeltaMovement(le.getDeltaMovement().add(dir.x * 1.2, 1.0, dir.z * 1.2));
                le.hurtMarked = true;
            }
            for (int i = 0; i < 64; i++) {
                double ang = i * Math.PI * 2 / 64;
                sl.sendParticles(ParticleTypes.CLOUD, c.x + Math.cos(ang) * 2, c.y + 0.3, c.z + Math.sin(ang) * 2, 0, Math.cos(ang), 0.05, Math.sin(ang), 0.9);
            }
            sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
            sl.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0f, 0.6f);
            fx(Powers.FX_WAVE_BURST, c, Vec3.ZERO);
        } else if (a == ANIM_CANON && t == 10) {
            Vec3 desde = this.position().add(0, 6.6, 0).add(frente.scale(1.6));
            Vec3 dir = piloto != null ? piloto.getLookAngle() : frente;
            Vec3 fin = impacto(desde, dir, 48, pp);
            for (LivingEntity le : objetivos(fin, 2.5)) daño(le, pp, 16f);
            sl.explode(this, fin.x, fin.y, fin.z, 2.0f, Level.ExplosionInteraction.NONE);
            fx(Powers.FX_BIG_BEAM, desde, fin);
            sl.playSound(null, this.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0f, 0.5f);
        } else if (a == ANIM_COHETES && t >= 6 && t <= 26 && (t - 6) % 4 == 0) {
            int n = (t - 6) / 4;
            Vec3 hombro = this.position().add(0, 9.0, 0).add(new Vec3(frente.z, 0, -frente.x).scale(n % 2 == 0 ? 1.8 : -1.8));
            Vec3 dir = (piloto != null ? piloto.getLookAngle() : frente).add((this.random.nextDouble() - 0.5) * 0.12, (this.random.nextDouble() - 0.5) * 0.08, (this.random.nextDouble() - 0.5) * 0.12).normalize();
            Vec3 fin = impacto(hombro, dir, 40, pp);
            double len = hombro.distanceTo(fin);
            for (double s = 0; s < len; s += 0.8) {
                Vec3 q = hombro.add(fin.subtract(hombro).scale(s / len));
                sl.sendParticles(ParticleTypes.SMOKE, q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0);
                sl.sendParticles(RingPowers.GREEN, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0);
            }
            for (LivingEntity le : objetivos(fin, 2.5)) daño(le, pp, 6f);
            sl.explode(this, fin.x, fin.y, fin.z, 1.5f, Level.ExplosionInteraction.NONE);
        }
    }

    private Vec3 impacto(Vec3 desde, Vec3 dir, double alcance, Player pp) {
        Vec3 fin = desde.add(dir.scale(alcance));
        BlockHitResult bh = this.level().clip(new ClipContext(desde, fin, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (bh.getType() != HitResult.Type.MISS) fin = bh.getLocation();
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(this.level(), this, desde, fin, new AABB(desde, fin).inflate(1.5),
                x -> x instanceof LivingEntity && x != pp && x != this && x.isAlive() && !x.isSpectator());
        return eh != null ? eh.getLocation() : fin;
    }

    private java.util.List<LivingEntity> objetivos(Vec3 c, double r) {
        LivingEntity piloto = this.getControllingPassenger();
        return this.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                x -> x != piloto && x.isAlive() && !x.isSpectator() && x.position().distanceTo(c) <= r + 1.5);
    }

    private void daño(LivingEntity le, Player p, float d) {
        le.hurt(p != null ? this.damageSources().playerAttack(p) : this.damageSources().generic(), d);
    }

    private void fx(int type, Vec3 a, Vec3 b) {
        ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> this), new ModNetwork.FxPacket(type, this.getId(), a, b));
    }

    public void disolver(String msg) {
        if (this.isRemoved()) return;
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(RingPowers.GREEN, this.getX(), this.getY() + 5, this.getZ(), 160, 1.5, 4.0, 1.5, 0.1);
            sl.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 5, this.getZ(), 60, 1.5, 4.0, 1.5, 0.05);
            sl.playSound(null, this.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.5f, 0.6f);
        }
        for (Entity p : this.getPassengers()) {
            if (p instanceof LivingEntity le) le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200));
            if (msg != null && p instanceof ServerPlayer sp) sp.displayClientMessage(Component.literal("§c" + msg), true);
        }
        this.ejectPassengers();
        this.discard();
    }

    /** Invoca al coloso delante del jugador y lo sube a la cabina. */
    public static void invocar(ServerPlayer p) {
        if (!RingPowers.isSuitOn(p)) { p.displayClientMessage(Component.literal("§cPrimero activa el traje (H)"), true); return; }
        if (p.isPassenger()) return;
        if (!RingPowers.spend(p, COST)) return;
        ServerLevel level = p.serverLevel();
        ColosoEntity c = GreenLanternMod.COLOSO.get().create(level);
        if (c == null) return;
        Vec3 frente = Vec3.directionFromRotation(0, p.getYRot());
        c.moveTo(p.getX() + frente.x * 2, p.getY(), p.getZ() + frente.z * 2, p.getYRot(), 0);
        level.addFreshEntity(c);
        p.startRiding(c, true);
        level.sendParticles(RingPowers.GREEN, c.getX(), c.getY() + 5, c.getZ(), 200, 1.5, 4.5, 1.5, 0.05);
        level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0f, 0.5f);
        ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new ModNetwork.FxPacket(Powers.FX_WAVE_BURST, p.getId(), c.position(), Vec3.ZERO));
    }
}
