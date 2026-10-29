package com.example.greenlantern;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * Misil (y cohete) del coloso: se ve volar con su llama. Los guiados doblan hacia su blanco;
 * los cohetes van derecho. Explotan al chocar o al llegar, sin romper bloques.
 */
public class MisilEntity extends Entity {
    private Entity blanco, coloso;
    private LivingEntity piloto;
    private boolean guiado;
    private float danio = 8f, potencia = 2f;
    private int edad;

    public MisilEntity(EntityType<? extends MisilEntity> type, Level level) { super(type, level); this.noPhysics = true; }

    public static MisilEntity lanzar(ServerLevel level, Entity coloso, LivingEntity piloto, Vec3 pos, Vec3 vel, Entity blanco, boolean guiado, float danio, float potencia) {
        MisilEntity m = GreenLanternMod.MISIL.get().create(level);
        if (m == null) return null;
        m.coloso = coloso; m.piloto = piloto; m.blanco = blanco; m.guiado = guiado; m.danio = danio; m.potencia = potencia;
        m.setPos(pos.x, pos.y, pos.z);
        m.setDeltaMovement(vel);
        level.addFreshEntity(m);
        return m;
    }

    @Override protected void defineSynchedData() { }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean hurt(DamageSource s, float a) { return false; }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }

    @Override
    public void tick() {
        super.tick();
        edad++;
        Vec3 v = this.getDeltaMovement();
        if (!this.level().isClientSide) {
            ServerLevel sl = (ServerLevel) this.level();
            if (guiado) {
                Vec3 deseo;
                if (blanco != null && blanco.isAlive()) deseo = blanco.getBoundingBox().getCenter().subtract(this.position()).normalize().scale(1.4);
                else deseo = piloto != null ? piloto.getLookAngle().scale(1.4) : v;
                float giro = edad < 6 ? 0.08f : 0.22f;
                v = v.scale(1 - giro).add(deseo.scale(giro));
            }
            double rapidez = Math.min(1.5, 0.5 + edad * 0.09);
            if (v.length() > 0.001) v = v.normalize().scale(rapidez);
            Vec3 sig = this.position().add(v);
            BlockHitResult bh = sl.clip(new ClipContext(this.position(), sig, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            boolean choca = bh.getType() != HitResult.Type.MISS || edad > 120;
            if (!choca) {
                for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, new AABB(sig, sig).inflate(1.2), x -> x != piloto && x.isAlive() && !x.isSpectator())) { choca = true; break; }
            }
            if (choca) {
                Vec3 p = bh.getType() != HitResult.Type.MISS ? bh.getLocation() : sig;
                for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(3.0), x -> x != piloto && x.isAlive() && !x.isSpectator())) {
                    le.hurt(piloto instanceof Player pp ? this.damageSources().playerAttack(pp) : this.damageSources().generic(), danio);
                }
                sl.explode(coloso != null ? coloso : this, p.x, p.y, p.z, potencia, Level.ExplosionInteraction.NONE);
                this.discard();
                return;
            }
            this.setDeltaMovement(v);
            this.hurtMarked = true;
        }
        Vec3 sig = this.position().add(v);
        this.setPos(sig.x, sig.y, sig.z);
        // orientado hacia donde vuela
        double h = Math.sqrt(v.x * v.x + v.z * v.z);
        this.setYRot((float) Math.toDegrees(Math.atan2(v.x, v.z)));
        this.setXRot((float) -Math.toDegrees(Math.atan2(v.y, h)));
        if (this.level().isClientSide) {
            Vec3 atras = this.position().subtract(v.normalize().scale(0.7));
            this.level().addParticle(ParticleTypes.SMOKE, atras.x, atras.y, atras.z, 0, 0.01, 0);
            this.level().addParticle(ParticleTypes.FLAME, atras.x, atras.y, atras.z, -v.x * 0.05, -v.y * 0.05, -v.z * 0.05);
            if (edad % 2 == 0) this.level().addParticle(RingPowers.GREEN, atras.x, atras.y, atras.z, 0, 0, 0);
        }
    }
}
