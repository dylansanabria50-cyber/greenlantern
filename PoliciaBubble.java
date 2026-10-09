package com.example.policia;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Habilidad ESCUDO DE BURBUJA: esfera azul que sigue al jugador y ralentiza todo lo que entra. */
public class PoliciaBubble {
    public static final RegistryObject<EntityType<BubbleEntity>> BUBBLE = PoliciaRefuerzo.ENTITIES.register("burbuja",
            () -> EntityType.Builder.<BubbleEntity>of(BubbleEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).noSave().fireImmune().clientTrackingRange(16).updateInterval(1)
                    .build("policia:burbuja"));

    static final int DUR = 460;          // 23 s
    public static final double R = 7.0; // 14 bloques de diametro

    static class B {
        int left = DUR;
        Vec3 c;
        ResourceKey<Level> dim;
        UUID ent;
    }

    static final Map<UUID, B> ACTIVE = new HashMap<>();

    static void init() {
        MinecraftForge.EVENT_BUS.register(new Ev());
    }

    static boolean use(ServerPlayer p) {
        if (ACTIVE.containsKey(p.getUUID())) {
            PoliciaMod.msg(p, "La burbuja ya esta activa");
            return false;
        }
        ServerLevel sl = p.serverLevel();
        BubbleEntity b = new BubbleEntity(BUBBLE.get(), sl);
        b.setOwnerId(p.getId());
        b.setPos(p.getX(), p.getY(), p.getZ());
        sl.addFreshEntity(b);
        B st = new B();
        st.c = p.position().add(0.0, 0.9, 0.0);
        st.dim = sl.dimension();
        st.ent = b.getUUID();
        ACTIVE.put(p.getUUID(), st);
        sl.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.5f);
        PoliciaMod.msg(p, "Escudo de burbuja activado: 23 s");
        return true;
    }

    static void end(MinecraftServer srv, UUID id, ServerPlayer o) {
        B st = ACTIVE.remove(id);
        if (st == null) return;
        ServerLevel sl = srv.getLevel(st.dim);
        if (sl != null) {
            Entity e = sl.getEntity(st.ent);
            if (e != null) e.discard();
            sl.sendParticles(ParticleTypes.POOF, st.c.x, st.c.y, st.c.z, 16, 0.6, 0.6, 0.6, 0.03);
            sl.playSound(null, st.c.x, st.c.y, st.c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 1.5f);
        }
        if (o != null) PoliciaMod.msg(o, "La burbuja se disipo");
    }

    public static class BubbleEntity extends Entity {
        static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(BubbleEntity.class, EntityDataSerializers.INT);

        public BubbleEntity(EntityType<?> t, Level l) {
            super(t, l);
            this.noPhysics = true;
            this.setNoGravity(true);
        }

        @Override
        protected void defineSynchedData() { this.entityData.define(OWNER, -1); }

        public int ownerId() { return this.entityData.get(OWNER); }

        public void setOwnerId(int i) { this.entityData.set(OWNER, i); }

        @Override
        protected void readAdditionalSaveData(CompoundTag t) { }

        @Override
        protected void addAdditionalSaveData(CompoundTag t) { }

        @Override
        public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
    }

    public static class Ev {
        @SubscribeEvent
        public void bTick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv == null) return;
            for (UUID id : new ArrayList<>(ACTIVE.keySet())) {
                B st = ACTIVE.get(id);
                if (st == null) continue;
                ServerPlayer o = srv.getPlayerList().getPlayer(id);
                st.left--;
                if (o == null || !o.isAlive() || st.left <= 0 || o.level().dimension() != st.dim) {
                    end(srv, id, o);
                    continue;
                }
                ServerLevel sl = o.serverLevel();
                st.c = o.position().add(0.0, 0.9, 0.0);
                Entity be = sl.getEntity(st.ent);
                if (be == null) {
                    end(srv, id, o);
                    continue;
                }
                be.setPos(o.getX(), o.getY(), o.getZ());
                AABB box = new AABB(st.c, st.c).inflate(R + 1.0);
                List<Entity> in = sl.getEntities((Entity) null, box, en -> en != o && !(en instanceof BubbleEntity));
                for (Entity x : in) {
                    if (x.getBoundingBox().distanceToSqr(st.c) > R * R) continue;
                    if (x instanceof ServerPlayer sp) {
                        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 5, false, false));
                        sp.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 10, 2, false, false));
                    } else if (!(x instanceof LivingEntity)) {
                        if (x instanceof Projectile pr && pr.getOwner() == o) continue;
                        x.setDeltaMovement(x.getDeltaMovement().scale(0.2));
                        x.hurtMarked = true;
                    }
                }
                if (st.left % 5 == 0) {
                    DustParticleOptions dust = new DustParticleOptions(new org.joml.Vector3f(0.25f, 0.55f, 1.0f), 1.0f);
                    for (int i = 0; i < 4; i++) {
                        double a = sl.random.nextDouble() * Math.PI * 2.0;
                        double h = sl.random.nextDouble() * 2.0 - 1.0;
                        double rr = Math.sqrt(1.0 - h * h) * R;
                        sl.sendParticles(dust, st.c.x + Math.cos(a) * rr, st.c.y + h * R, st.c.z + Math.sin(a) * rr, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
            }
        }

        @SubscribeEvent
        public void bLtick(LivingEvent.LivingTickEvent e) {
            if (ACTIVE.isEmpty()) return;
            LivingEntity le = e.getEntity();
            if (le.level().isClientSide || le instanceof Player) return;
            if (le.tickCount % 6 == 0) return;
            for (B st : ACTIVE.values()) {
                if (st.dim == le.level().dimension() && le.getBoundingBox().distanceToSqr(st.c) <= R * R) {
                    try {
                        e.setCanceled(true);
                    } catch (UnsupportedOperationException ex) {
                        // sin ralentizacion de ticks
                    }
                    return;
                }
            }
        }

        @SubscribeEvent
        public void bAtk(LivingAttackEvent e) {
            if (!(e.getEntity() instanceof ServerPlayer p) || !ACTIVE.containsKey(p.getUUID())) return;
            if (e.getSource().is(DamageTypeTags.IS_PROJECTILE)) e.setCanceled(true);
        }

        @SubscribeEvent
        public void bHurt(LivingHurtEvent e) {
            if (!(e.getEntity() instanceof ServerPlayer p) || !ACTIVE.containsKey(p.getUUID())) return;
            if (e.getSource().getEntity() != null) e.setAmount(e.getAmount() * 0.4f);
        }
    }
}
