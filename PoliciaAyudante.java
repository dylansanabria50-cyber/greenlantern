package com.example.policia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Habilidad PEDIR AYUDANTE del albanil: ayudantes con pico que siguen, defienden, pican y buscan minerales. */
public class PoliciaAyudante {
    public static final RegistryObject<EntityType<Helper>> AYU = PoliciaRefuerzo.ENTITIES.register("ayudante",
            () -> EntityType.Builder.<Helper>of(Helper::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f).clientTrackingRange(10).noSave().build("policia:ayudante"));

    /** Ayudantes por nivel (indice = nivel). */
    static final int[] COUNT = {0, 1, 3, 4};
    static final String[] PICKS = {"", "piedra", "hierro", "diamante"};
    static final String[] ORDERS = {"Seguir y defender", "Picar alrededor", "Buscar minerales"};
    static final int LIFE = 1200;  // 60 s en el campo
    static final int CD = 600;     // 30 s de enfriamiento al retirarse

    static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaAyudante::attrs);
    }

    static void attrs(EntityAttributeCreationEvent e) {
        e.put(AYU.get(), Helper.attrs().build());
    }

    static Item pickItem(int t) {
        switch (t) {
            case 1: return Items.STONE_PICKAXE;
            case 2: return Items.IRON_PICKAXE;
            default: return Items.DIAMOND_PICKAXE;
        }
    }

    static boolean isOre(BlockState st) { return st.is(Tags.Blocks.ORES); }

    static List<Helper> mine(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(Helper.class, p.getBoundingBox().inflate(256.0), a -> p.getUUID().equals(a.owner));
    }

    static void clear(ServerPlayer p) {
        for (Helper h : mine(p)) h.poof();
    }

    static void use(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        int lv = Math.max(0, Math.min(3, d.getInt("alb_hl")));
        if (lv <= 0) { PoliciaMod.msg(p, "Desbloquea primero: AYUDANTE"); return; }
        if (d.getInt("alb_ht") > 0) { PoliciaMod.msg(p, "Tus ayudantes ya estan en el campo"); return; }
        if (d.getInt("alb_hcd") > 0) { PoliciaMod.msg(p, "Ayudantes en enfriamiento: " + (d.getInt("alb_hcd") + 19) / 20 + " s"); return; }
        ServerLevel sl = p.serverLevel();
        int n = COUNT[lv];
        for (int i = 0; i < n; i++) {
            Helper a = AYU.get().create(sl);
            if (a == null) continue;
            a.owner = p.getUUID();
            a.tier = lv;
            a.equip(lv);
            double ang = Math.PI * 2.0 * i / n;
            double x = p.getX() + Math.cos(ang) * 2.2, z = p.getZ() + Math.sin(ang) * 2.2;
            for (int dy = 0; dy <= 2; dy++) {
                a.moveTo(x, p.getY() + dy, z, p.getYRot(), 0.0f);
                if (sl.noCollision(a)) break;
                if (dy == 2) a.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0.0f);
            }
            sl.addFreshEntity(a);
            sl.sendParticles(ParticleTypes.CLOUD, a.getX(), a.getY() + 0.5, a.getZ(), 8, 0.3, 0.5, 0.3, 0.03);
        }
        d.putInt("alb_ht", LIFE);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.8f, 1.1f);
        PoliciaMod.msg(p, "AYUDANTE nivel " + lv + ": " + n + (n == 1 ? " ayudante" : " ayudantes") + " con pico de " + PICKS[lv] + ". Tecla H: cambiar orden (" + ORDERS[Math.max(0, Math.min(2, d.getInt("alb_mode")))] + ")");
        PoliciaMod.sync(p);
    }

    public static class Helper extends PathfinderMob {
        UUID owner;
        int tier = 1;
        int life;
        int mt;
        int stuck;
        BlockPos ore;
        BlockPos lastPos;
        final Set<Long> bad = new HashSet<>();

        public Helper(EntityType<? extends Helper> type, Level level) {
            super(type, level);
            this.xpReward = 0;
            this.setPersistenceRequired();
        }

        static AttributeSupplier.Builder attrs() {
            return Mob.createMobAttributes()
                    .add(Attributes.MAX_HEALTH, 20.0)
                    .add(Attributes.MOVEMENT_SPEED, 0.3)
                    .add(Attributes.ATTACK_DAMAGE, 1.0)
                    .add(Attributes.FOLLOW_RANGE, 32.0)
                    .add(Attributes.ARMOR, 2.0)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 0.2);
        }

        void equip(int t) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(pickItem(t)));
            setDropChance(EquipmentSlot.MAINHAND, 0.0f);
        }

        ServerPlayer ownerPlayer() {
            if (owner == null || !(level() instanceof ServerLevel sl)) return null;
            return sl.getServer().getPlayerList().getPlayer(owner);
        }

        boolean near(LivingEntity x) {
            ServerPlayer o = ownerPlayer();
            return o != null && o.distanceToSqr(x) < 400.0;
        }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(1, new FloatGoal(this));
            this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
            this.goalSelector.addGoal(3, new FollowGoal(this));
            this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0f));
            this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
            this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false, x -> near(x)));
        }

        @Override
        public boolean canAttack(LivingEntity e) {
            if (e instanceof Helper || e instanceof PoliciaRefuerzo.AgentEntity || (owner != null && e.getUUID().equals(owner))) return false;
            return super.canAttack(e);
        }

        @Override
        public boolean hurt(DamageSource s, float amount) {
            Entity src = s.getEntity();
            if (src instanceof Helper || (src != null && owner != null && src.getUUID().equals(owner))) return false;
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

        /** Se puede picar: solo bloques de pico, sin contenido, sin obsidiana y respetando el material del pico. */
        boolean canBreak(BlockPos bp) {
            BlockState st = level().getBlockState(bp);
            if (st.isAir() || !st.getFluidState().isEmpty() || st.hasBlockEntity()) return false;
            float h = st.getDestroySpeed(level(), bp);
            if (h < 0.0f || h > 20.0f) return false;
            if (PoliciaAlbanil.LIVE.contains(bp.asLong())) return false;
            if (!st.is(BlockTags.MINEABLE_WITH_PICKAXE)) return false;
            if (tier < 3 && st.is(BlockTags.NEEDS_DIAMOND_TOOL)) return false;
            if (tier < 2 && st.is(BlockTags.NEEDS_IRON_TOOL)) return false;
            return true;
        }

        void dig(BlockPos bp) {
            ServerLevel sl = (ServerLevel) level();
            BlockState st = sl.getBlockState(bp);
            getLookControl().setLookAt(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
            swing(InteractionHand.MAIN_HAND);
            Block.dropResources(st, sl, bp, null, this, getMainHandItem());
            sl.destroyBlock(bp, false, this);
            stuck = 0;
        }

        /** Orden 1: pica los bloques que tiene alrededor. */
        boolean around(ServerPlayer o) {
            BlockPos me = blockPosition();
            BlockPos ob = o.blockPosition();
            BlockPos best = null;
            double bd = 1.0E9;
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int dy = 0; dy <= 2; dy++) {
                BlockPos bp = me.offset(dx, dy, dz);
                if (bp.equals(ob) || bp.equals(ob.above())) continue;
                double dd = bp.distToCenterSqr(getX(), getEyeY(), getZ());
                if (dd > 20.0 || dd >= bd) continue;
                if (!canBreak(bp)) continue;
                bd = dd;
                best = bp;
            }
            if (best == null) return false;
            dig(best);
            return true;
        }

        /** Orden 2: busca el mineral mas cercano, abre un tunel hacia el y lo pica. */
        boolean oreStep(ServerPlayer o) {
            ServerLevel sl = (ServerLevel) level();
            if (ore != null && !isOre(sl.getBlockState(ore))) ore = null;
            if (ore == null) {
                stuck = 0;
                BlockPos me = blockPosition();
                Set<Long> taken = new HashSet<>();
                for (Helper h : sl.getEntitiesOfClass(Helper.class, o.getBoundingBox().inflate(48.0), x -> x != this && o.getUUID().equals(x.owner))) {
                    if (h.ore != null) taken.add(h.ore.asLong());
                }
                BlockPos best = null;
                double bd = 1.0E12;
                for (int dx = -12; dx <= 12; dx++) for (int dz = -12; dz <= 12; dz++) for (int dy = -6; dy <= 6; dy++) {
                    BlockPos bp = me.offset(dx, dy, dz);
                    double dd = bp.distSqr(me);
                    if (dd >= bd) continue;
                    if (bad.contains(bp.asLong()) || taken.contains(bp.asLong())) continue;
                    if (!isOre(sl.getBlockState(bp)) || !canBreak(bp)) continue;
                    bd = dd;
                    best = bp;
                }
                if (best == null) return false;
                ore = best;
            }
            double d = Math.sqrt(ore.distToCenterSqr(getX(), getEyeY(), getZ()));
            if (d <= 4.5) {
                dig(ore);
                ore = null;
                return true;
            }
            BlockPos me = blockPosition();
            int dx = ore.getX() - me.getX(), dz = ore.getZ() - me.getZ();
            if (dx == 0 && dz == 0) { bad.add(ore.asLong()); ore = null; return false; }
            Direction dir = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
            BlockPos n = me.relative(dir);
            if (me.equals(lastPos)) stuck++; else stuck = 0;
            lastPos = me;
            if (stuck > 8) { bad.add(ore.asLong()); ore = null; stuck = 0; return false; }
            BlockPos[] cells = {n, n.above()};
            for (BlockPos q : cells) {
                if (!sl.getBlockState(q).getCollisionShape(sl, q).isEmpty()) {
                    if (canBreak(q)) { dig(q); return true; }
                    bad.add(ore.asLong());
                    ore = null;
                    return false;
                }
            }
            getNavigation().moveTo(n.getX() + 0.5, n.getY(), n.getZ() + 0.5, 1.1);
            return true;
        }

        @Override
        public void tick() {
            super.tick();
            if (level().isClientSide) return;
            life++;
            if (life > LIFE + 40) { poof(); return; }
            ServerPlayer o = ownerPlayer();
            if (tickCount % 20 == 0 && (o == null || !o.isAlive() || o.level() != level())) { poof(); return; }
            if (o == null || getTarget() != null) return;
            int ord = o.getPersistentData().getInt("alb_mode");
            if (ord <= 0) { ore = null; return; }
            if (mt > 0) { mt--; return; }
            int cd = tier == 1 ? 24 : (tier == 2 ? 14 : 8);
            boolean did = ord == 1 ? around(o) : oreStep(o);
            mt = did ? cd : 30;
        }
    }

    /** Sigue al jugador y se teletransporta si se queda muy lejos. */
    static class FollowGoal extends Goal {
        final Helper a;

        FollowGoal(Helper a) {
            this.a = a;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            ServerPlayer o = a.ownerPlayer();
            if (o == null || a.getTarget() != null) return false;
            double lim = (a.ore != null && a.distanceToSqr(o) < 400.0) ? 400.0 : 36.0;
            return a.distanceToSqr(o) > lim;
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
}
