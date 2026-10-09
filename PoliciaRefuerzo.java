package com.example.policia;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/** Habilidad REFUERZO: policias NPC que protegen al jugador. 4 niveles (1, 3, 5 y 6 policias). */
public class PoliciaRefuerzo {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "policia");
    public static final RegistryObject<EntityType<AgentEntity>> AGENT = ENTITIES.register("agente",
            () -> EntityType.Builder.<AgentEntity>of(AgentEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f).clientTrackingRange(10).noSave().build("policia:agente"));

    /** Policias por nivel (indice = nivel). */
    public static final int[] COUNT = {0, 1, 3, 5, 6};
    /** Material de la espada por nivel. */
    public static final String[] SWORD = {"", "piedra", "hierro", "diamante", "netherita"};
    static final int LIFE = 700;       // 35 s en el campo
    static final int COOLDOWN = 300;   // 15 s desde que se invocan

    public static void attrs(EntityAttributeCreationEvent e) {
        e.put(AGENT.get(), AgentEntity.attrs().build());
    }

    static Item sword(int lv) {
        switch (lv) {
            case 1: return Items.STONE_SWORD;
            case 2: return Items.IRON_SWORD;
            case 3: return Items.DIAMOND_SWORD;
            default: return Items.NETHERITE_SWORD;
        }
    }

    /** Invoca a los policias de refuerzo segun el nivel de la habilidad. */
    public static void use(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        int lv = PoliciaMod.rlevel(p);
        if (lv <= 0) return;
        ServerLevel sl = p.serverLevel();
        List<AgentEntity> mine = sl.getEntitiesOfClass(AgentEntity.class, p.getBoundingBox().inflate(128.0),
                a -> p.getUUID().equals(a.owner));
        if (!mine.isEmpty()) { PoliciaMod.msg(p, "Tus refuerzos ya estan en el campo"); return; }
        if (d.getInt("pol_rcd") > 0) { PoliciaMod.msg(p, "Refuerzo en enfriamiento: " + (d.getInt("pol_rcd") + 19) / 20 + " s"); return; }
        int n = COUNT[lv];
        for (int i = 0; i < n; i++) {
            AgentEntity a = AGENT.get().create(sl);
            if (a == null) continue;
            a.owner = p.getUUID();
            a.tier = lv;
            a.equip(lv);
            double ang = Math.PI * 2.0 * i / n;
            double x = p.getX() + Math.cos(ang) * 2.6, z = p.getZ() + Math.sin(ang) * 2.6;
            for (int dy = 0; dy <= 2; dy++) {
                a.moveTo(x, p.getY() + dy, z, p.getYRot(), 0.0f);
                if (sl.noCollision(a)) break;
                if (dy == 2) a.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0.0f);
            }
            sl.addFreshEntity(a);
            sl.sendParticles(ParticleTypes.CLOUD, a.getX(), a.getY() + 0.5, a.getZ(), 8, 0.3, 0.5, 0.3, 0.03);
        }
        d.putInt("pol_rcd", COOLDOWN);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0f, 1.4f);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 1.0f, 0.8f);
        PoliciaMod.msg(p, "Refuerzo nivel " + lv + ": " + n + (n == 1 ? " policia" : " policias") + " con espada de " + SWORD[lv]);
        PoliciaMod.sync(p);
    }

    public static class AgentEntity extends PathfinderMob {
        UUID owner;
        int tier = 1;
        int life;
        int blockT;

        public AgentEntity(EntityType<? extends AgentEntity> type, Level level) {
            super(type, level);
            this.xpReward = 0;
            this.setPersistenceRequired();
        }

        static AttributeSupplier.Builder attrs() {
            return Mob.createMobAttributes()
                    .add(Attributes.MAX_HEALTH, 20.0)
                    .add(Attributes.MOVEMENT_SPEED, 0.32)
                    .add(Attributes.ATTACK_DAMAGE, 1.0)
                    .add(Attributes.FOLLOW_RANGE, 32.0)
                    .add(Attributes.ARMOR, 6.0)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
        }

        void equip(int lv) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(sword(lv)));
            setDropChance(EquipmentSlot.MAINHAND, 0.0f);
            setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(PoliciaShield.SHIELD.get()));
            setDropChance(EquipmentSlot.OFFHAND, 0.0f);
        }

        ServerPlayer ownerPlayer() {
            if (owner == null || !(level() instanceof ServerLevel sl)) return null;
            return sl.getServer().getPlayerList().getPlayer(owner);
        }

        boolean threat(LivingEntity e) {
            if (e instanceof AgentEntity || (owner != null && e.getUUID().equals(owner))) return false;
            if (e instanceof PoliciaMision.Pj bj) return bj.hostile();
            if (e instanceof Enemy) return true;
            return owner != null && e instanceof Mob m && m.getTarget() != null && owner.equals(m.getTarget().getUUID());
        }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(1, new FloatGoal(this));
            this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true));
            this.goalSelector.addGoal(3, new FollowGoal(this));
            this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0f));
            this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
            this.targetSelector.addGoal(1, new SpreadGoal(this));
            this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
        }

        @Override
        public boolean canAttack(LivingEntity e) {
            if (e instanceof AgentEntity || (owner != null && e.getUUID().equals(owner))) return false;
            return super.canAttack(e);
        }

        @Override
        public boolean hurt(DamageSource s, float amount) {
            Entity src = s.getEntity();
            if (src instanceof AgentEntity || (src != null && owner != null && src.getUUID().equals(owner))) return false;
            return super.hurt(s, amount);
        }

        @Override
        public boolean removeWhenFarAway(double d) { return false; }

        @Override
        public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }

        void poof() {
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 1.0, getZ(), 12, 0.3, 0.5, 0.3, 0.02);
            }
            discard();
        }

        @Override
        public void tick() {
            super.tick();
            if (level().isClientSide) return;
            // se cubre con el escudo (como el vanilla) cuando hay un enemigo cerca o lo golpean
            LivingEntity tg = getTarget();
            if ((tg != null && tg.isAlive() && distanceToSqr(tg) < 36.0) || hurtTime > 0) blockT = 25;
            if (blockT > 0) {
                blockT--;
                if (!isUsingItem()) startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
            } else if (isUsingItem()) {
                stopUsingItem();
            }
            life++;
            if (life > LIFE) { poof(); return; }
            if (tickCount % 20 == 0) {
                ServerPlayer o = ownerPlayer();
                if (o == null || !o.isAlive() || o.level() != level()) poof();
            }
        }
    }

    /** Sigue al jugador y se teletransporta si se queda muy lejos. */
    static class FollowGoal extends Goal {
        final AgentEntity a;

        FollowGoal(AgentEntity a) {
            this.a = a;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            ServerPlayer o = a.ownerPlayer();
            return o != null && a.getTarget() == null && a.distanceToSqr(o) > 36.0;
        }

        @Override
        public boolean canContinueToUse() {
            ServerPlayer o = a.ownerPlayer();
            return o != null && a.getTarget() == null && a.distanceToSqr(o) > 9.0;
        }

        @Override
        public void tick() {
            ServerPlayer o = a.ownerPlayer();
            if (o == null) return;
            if (a.distanceToSqr(o) > 576.0) {
                a.moveTo(o.getX() + (a.getRandom().nextDouble() - 0.5) * 3.0, o.getY(), o.getZ() + (a.getRandom().nextDouble() - 0.5) * 3.0, a.getYRot(), 0.0f);
                a.getNavigation().stop();
                return;
            }
            if (a.tickCount % 10 == 0) a.getNavigation().moveTo(o, 1.3);
        }

        @Override
        public void stop() { a.getNavigation().stop(); }
    }

    /** Reparte a los policias: con varios enemigos, cada policia elige un objetivo distinto. */
    static class SpreadGoal extends TargetGoal {
        final AgentEntity a;
        LivingEntity cand;

        SpreadGoal(AgentEntity a) {
            super(a, false);
            this.a = a;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if ((a.tickCount + a.getId()) % 8 != 0) return false;
            ServerPlayer o = a.ownerPlayer();
            if (o == null) return false;
            LivingEntity my = a.getTarget();
            if (my != null && !my.isAlive()) {
                a.setTarget(null);
                my = null;
            }
            java.util.List<LivingEntity> threats = new java.util.ArrayList<>();
            for (LivingEntity e : a.level().getEntitiesOfClass(LivingEntity.class, o.getBoundingBox().inflate(24.0),
                    x -> x.isAlive() && a.threat(x) && a.canAttack(x))) {
                threats.add(e);
            }
            LivingEntity hb = o.getLastHurtByMob();
            if (hb != null && hb.isAlive() && !threats.contains(hb) && a.canAttack(hb) && a.distanceToSqr(hb) < 900.0) threats.add(hb);
            LivingEntity hm = o.getLastHurtMob();
            if (hm != null && hm.isAlive() && !threats.contains(hm) && a.canAttack(hm) && a.distanceToSqr(hm) < 900.0) threats.add(hm);
            if (threats.isEmpty()) return false;
            java.util.Set<LivingEntity> claimed = new java.util.HashSet<>();
            for (AgentEntity b : a.level().getEntitiesOfClass(AgentEntity.class, o.getBoundingBox().inflate(48.0),
                    x -> x != a && o.getUUID().equals(x.owner))) {
                if (b.getTarget() != null) claimed.add(b.getTarget());
            }
            boolean shared = my != null && claimed.contains(my);
            if (my != null && !shared) return false;
            LivingEntity best = null;
            double bd = 1.0E18;
            for (LivingEntity e : threats) {
                if (claimed.contains(e)) continue;
                double dd = a.distanceToSqr(e);
                if (dd < bd) { bd = dd; best = e; }
            }
            if (best == null) {
                if (my != null) return false;
                for (LivingEntity e : threats) {
                    double dd = a.distanceToSqr(e);
                    if (dd < bd) { bd = dd; best = e; }
                }
            }
            if (best == null || best == my) return false;
            cand = best;
            return true;
        }

        @Override
        public boolean canContinueToUse() { return false; }

        @Override
        public void start() { a.setTarget(cand); }

        @Override
        public void stop() { }
    }

    /** Ataca a quien dane al jugador o a lo que el jugador ataca. */
    static class DefendGoal extends TargetGoal {
        final AgentEntity a;
        final boolean byOwner;
        LivingEntity cand;
        int stamp, last;

        DefendGoal(AgentEntity a, boolean byOwner) {
            super(a, false);
            this.a = a;
            this.byOwner = byOwner;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            ServerPlayer o = a.ownerPlayer();
            if (o == null) return false;
            LivingEntity t = byOwner ? o.getLastHurtByMob() : o.getLastHurtMob();
            int st = byOwner ? o.getLastHurtByMobTimestamp() : o.getLastHurtMobTimestamp();
            if (t == null || st == last || !t.isAlive() || !a.canAttack(t)) return false;
            if (!canAttack(t, TargetingConditions.DEFAULT)) return false;
            cand = t;
            stamp = st;
            return true;
        }

        @Override
        public void start() {
            mob.setTarget(cand);
            last = stamp;
            super.start();
        }
    }
}
