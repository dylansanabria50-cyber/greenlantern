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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
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
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Habilidad PEDIR AYUDANTE del albanil: ayudantes con pico que siguen, defienden, pican un area y buscan minerales. */
public class PoliciaAyudante {
    public static final RegistryObject<EntityType<Helper>> AYU = PoliciaRefuerzo.ENTITIES.register("ayudante",
            () -> EntityType.Builder.<Helper>of(Helper::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f).clientTrackingRange(10).noSave().build("policia:ayudante"));

    /** Ayudantes por nivel (indice = nivel). */
    static final int[] COUNT = {0, 1, 3, 4};
    static final String[] PICKS = {"", "piedra", "hierro", "diamante"};
    static final String[] ORDERS = {"Seguir y defender", "Picar delante de mi", "Buscar minerales"};
    static final int LIFE = 1200;  // 60 s en el campo
    static final int CD = 600;     // 30 s de enfriamiento al retirarse

    /** Inventario de los ayudantes de cada jugador: 4 filas de 9, una fila por ayudante. */
    static final Map<UUID, SimpleContainer> BANKS = new HashMap<>();

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

    static String esName(String p) {
        if (p.contains("coal")) return "carbon";
        if (p.contains("iron")) return "hierro";
        if (p.contains("copper")) return "cobre";
        if (p.contains("gold")) return "oro";
        if (p.contains("redstone")) return "redstone";
        if (p.contains("lapis")) return "lapislazuli";
        if (p.contains("emerald")) return "esmeralda";
        if (p.contains("diamond")) return "diamante";
        if (p.contains("quartz")) return "cuarzo";
        if (p.contains("debris")) return "restos antiguos";
        return p.replace('_', ' ');
    }

    static List<Helper> mine(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(Helper.class, p.getBoundingBox().inflate(256.0), a -> p.getUUID().equals(a.owner));
    }

    static void clear(ServerPlayer p) {
        for (Helper h : mine(p)) h.poof();
    }

    // ---------- inventario ----------
    static SimpleContainer bank(ServerPlayer p) {
        UUID id = p.getUUID();
        SimpleContainer c = BANKS.get(id);
        if (c != null) return c;
        c = new SimpleContainer(36);
        ListTag l = p.getPersistentData().getList("alb_bank", 10);
        for (Tag t : l) {
            CompoundTag e = (CompoundTag) t;
            int s = e.getInt("Slot");
            if (s >= 0 && s < 36) c.setItem(s, ItemStack.of(e));
        }
        final net.minecraft.server.MinecraftServer srv = p.server;
        final SimpleContainer fc = c;
        c.addListener(cc -> {
            ServerPlayer q = srv.getPlayerList().getPlayer(id);
            if (q != null) saveBank(q, fc);
        });
        BANKS.put(id, c);
        return c;
    }

    static void saveBank(ServerPlayer p, SimpleContainer c) {
        ListTag l = new ListTag();
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) continue;
            CompoundTag e = new CompoundTag();
            e.putInt("Slot", i);
            s.save(e);
            l.add(e);
        }
        p.getPersistentData().put("alb_bank", l);
    }

    static boolean hasItems(ServerPlayer p) { return !bank(p).isEmpty(); }

    static void openBank(ServerPlayer p) {
        if (p.getPersistentData().getInt("alb_hl") < 1) { PoliciaMod.msg(p, "Aun no tienes ayudantes: desbloquea AYUDANTE"); return; }
        final SimpleContainer b = bank(p);
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new ChestMenu(MenuType.GENERIC_9x4, id, inv, b, 4),
                Component.literal("Inventario de los ayudantes (una fila por ayudante)")));
    }

    /** Respuesta del jugador (Y / N) a un aviso de mineral. */
    static void answer(ServerPlayer p, boolean yes) {
        boolean any = false;
        for (Helper h : mine(p)) {
            if (h.offer == null) continue;
            any = true;
            if (yes) {
                h.ore = h.offer;
                PoliciaMod.msg(p, "Ayudante " + (h.idx + 1) + ": voy a cavar hacia alli");
            } else {
                h.bad.add(h.offer.asLong());
            }
            h.offer = null;
        }
        if (!any) PoliciaMod.msg(p, "Ningun ayudante te esta preguntando");
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
            a.idx = i;
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
        PoliciaMod.msg(p, "AYUDANTE nivel " + lv + ": " + n + (n == 1 ? " ayudante" : " ayudantes") + " con pico de " + PICKS[lv] + ". H: orden (" + ORDERS[Math.max(0, Math.min(2, d.getInt("alb_mode")))] + "), U: inventario");
        PoliciaMod.sync(p);
    }

    public static class Helper extends PathfinderMob {
        UUID owner;
        int tier = 1;
        int idx;
        int life;
        int mt;
        int stuck;
        int warn;
        int offerT;
        int wt, wmax;
        BlockPos ore, offer, lastPos, work;
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
            stopWork();
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 1.0, getZ(), 12, 0.3, 0.5, 0.3, 0.02);
            }
            discard();
        }

        /** Se puede picar: bloques de pico o de pala, sin contenido, sin obsidiana y respetando el material del pico. */
        boolean canBreak(BlockPos bp) {
            BlockState st = level().getBlockState(bp);
            if (st.isAir() || !st.getFluidState().isEmpty() || st.hasBlockEntity()) return false;
            float h = st.getDestroySpeed(level(), bp);
            if (h < 0.0f || h > 20.0f) return false;
            if (PoliciaAlbanil.LIVE.contains(bp.asLong())) return false;
            if (!st.is(BlockTags.MINEABLE_WITH_PICKAXE) && !st.is(BlockTags.MINEABLE_WITH_SHOVEL)) return false;
            if (tier < 3 && st.is(BlockTags.NEEDS_DIAMOND_TOOL)) return false;
            if (tier < 2 && st.is(BlockTags.NEEDS_IRON_TOOL)) return false;
            return true;
        }

        // ---------- trabajo con animacion ----------
        void startWork(BlockPos bp) {
            work = bp;
            wmax = tier == 1 ? 30 : (tier == 2 ? 18 : 10);
            wt = wmax;
            stuck = 0;
            getNavigation().stop();
        }

        void stopWork() {
            if (work != null && level() instanceof ServerLevel sl) sl.destroyBlockProgress(getId(), work, -1);
            work = null;
        }

        void workTick() {
            ServerLevel sl = (ServerLevel) level();
            if (!canBreak(work) || work.distToCenterSqr(getX(), getEyeY(), getZ()) > 49.0) { stopWork(); return; }
            getLookControl().setLookAt(work.getX() + 0.5, work.getY() + 0.5, work.getZ() + 0.5);
            if (wt % 6 == 0) {
                swing(InteractionHand.MAIN_HAND);
                SoundType t = sl.getBlockState(work).getSoundType();
                sl.playSound(null, work, t.getHitSound(), SoundSource.BLOCKS, 0.6f, 0.9f);
            }
            sl.destroyBlockProgress(getId(), work, Math.min(9, (int) (10.0f * (wmax - wt) / wmax)));
            if (--wt <= 0) finishWork();
        }

        void finishWork() {
            ServerLevel sl = (ServerLevel) level();
            BlockPos bp = work;
            BlockState st = sl.getBlockState(bp);
            stopWork();
            if (!canBreak(bp)) return;
            List<ItemStack> drops = Block.getDrops(st, sl, bp, null, this, getMainHandItem());
            ServerPlayer o = ownerPlayer();
            for (ItemStack s : drops) {
                ItemStack rest = o == null ? s : store(o, s.copy());
                if (!rest.isEmpty()) Block.popResource(sl, bp, rest);
            }
            sl.destroyBlock(bp, false, this);
            swing(InteractionHand.MAIN_HAND);
        }

        /** Guarda en la fila de este ayudante; devuelve lo que no entro. */
        ItemStack store(ServerPlayer o, ItemStack s) {
            SimpleContainer b = bank(o);
            int base = Math.min(idx, 3) * 9;
            for (int i = 0; i < 9 && !s.isEmpty(); i++) {
                ItemStack c = b.getItem(base + i);
                if (c.isEmpty() || !ItemStack.isSameItemSameTags(c, s) || c.getCount() >= c.getMaxStackSize()) continue;
                int m = Math.min(s.getCount(), c.getMaxStackSize() - c.getCount());
                c.grow(m);
                s.shrink(m);
                b.setChanged();
            }
            for (int i = 0; i < 9 && !s.isEmpty(); i++) {
                if (b.getItem(base + i).isEmpty()) {
                    b.setItem(base + i, s.copy());
                    s.setCount(0);
                }
            }
            return s;
        }

        // ---------- orden 1: picar el area delante del jugador ----------
        boolean zoneStep(ServerPlayer o) {
            CompoundTag d = o.getPersistentData();
            if (d.getInt("alb_zon") != 1) {
                if (warn-- <= 0) {
                    warn = 6;
                    if (idx == 0) PoliciaMod.msg(o, "Ayudantes: mantén X para elegir el area donde picar");
                }
                return false;
            }
            BlockPos org = BlockPos.of(d.getLong("alb_zo"));
            Direction f = Direction.from2DDataValue(d.getInt("alb_zd"));
            Direction r = f.getClockWise();
            int len = d.getInt("alb_zl"), wid = d.getInt("alb_zw");
            int lo = -((wid - 1) / 2), hi = wid / 2;
            ServerLevel sl = (ServerLevel) level();
            Set<Long> taken = new HashSet<>();
            for (Helper h : sl.getEntitiesOfClass(Helper.class, o.getBoundingBox().inflate(64.0), x -> x != this && o.getUUID().equals(x.owner))) {
                if (h.work != null) taken.add(h.work.asLong());
            }
            BlockPos me = blockPosition();
            BlockPos best = null;
            double bd = 1.0E12;
            int bu = 0, bf = 1;
            for (int fw = 1; fw <= len && best == null; fw++) {
                for (int u = lo; u <= hi; u++) for (int h = 0; h <= 2; h++) {
                    BlockPos bp = PoliciaAlbanil.at(org, f, r, fw, u, h);
                    if (bad.contains(bp.asLong()) || taken.contains(bp.asLong())) continue;
                    double dd = bp.distSqr(me);
                    if (dd >= bd || !canBreak(bp)) continue;
                    bd = dd;
                    best = bp;
                    bu = u;
                    bf = fw;
                }
            }
            if (best == null) return false;
            if (best.distToCenterSqr(getX(), getEyeY(), getZ()) <= 20.0) {
                startWork(best);
                return true;
            }
            BlockPos ap = PoliciaAlbanil.at(org, f, r, bf - 1, bu, 0);
            if (me.equals(lastPos)) stuck++; else stuck = 0;
            lastPos = me;
            if (stuck > 12) { bad.add(best.asLong()); stuck = 0; return false; }
            getNavigation().moveTo(ap.getX() + 0.5, ap.getY(), ap.getZ() + 0.5, 1.1);
            return true;
        }

        // ---------- orden 2: avisar de minerales y cavar si el jugador acepta ----------
        void announce(ServerPlayer o, BlockPos bp) {
            BlockState st = level().getBlockState(bp);
            net.minecraft.resources.ResourceLocation key = ForgeRegistries.BLOCKS.getKey(st.getBlock());
            String nm = esName(key == null ? "mineral" : key.getPath());
            int dist = (int) Math.sqrt(bp.distToCenterSqr(o.getX(), o.getY(), o.getZ()));
            PoliciaMod.msg(o, "Ayudante " + (idx + 1) + ": hay " + nm + " en " + bp.getX() + " " + bp.getY() + " " + bp.getZ()
                    + " (a " + dist + " bloques). Y: cavar hacia alli, N: ignorar");
            o.level().playSound(null, o.getX(), o.getY(), o.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.4f);
        }

        boolean oreStep(ServerPlayer o) {
            ServerLevel sl = (ServerLevel) level();
            if (ore != null && !isOre(sl.getBlockState(ore))) ore = null;
            if (ore == null) {
                stuck = 0;
                if (offer != null) {
                    if (--offerT <= 0 || !isOre(sl.getBlockState(offer))) {
                        bad.add(offer.asLong());
                        offer = null;
                    }
                    return false;
                }
                Set<Long> taken = new HashSet<>();
                for (Helper h : sl.getEntitiesOfClass(Helper.class, o.getBoundingBox().inflate(64.0), x -> x != this && o.getUUID().equals(x.owner))) {
                    if (h.offer != null) return false;
                    if (h.ore != null) taken.add(h.ore.asLong());
                }
                BlockPos me = blockPosition();
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
                offer = best;
                offerT = 400;
                announce(o, best);
                return false;
            }
            if (ore.distToCenterSqr(getX(), getEyeY(), getZ()) <= 20.0) {
                startWork(ore);
                return true;
            }
            BlockPos me = blockPosition();
            int dx = ore.getX() - me.getX(), dz = ore.getZ() - me.getZ();
            if (dx == 0 && dz == 0) { bad.add(ore.asLong()); ore = null; return false; }
            Direction dir = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
            BlockPos n = me.relative(dir);
            if (me.equals(lastPos)) stuck++; else stuck = 0;
            lastPos = me;
            if (stuck > 12) { bad.add(ore.asLong()); ore = null; stuck = 0; return false; }
            BlockPos[] cells = {n, n.above()};
            for (BlockPos q : cells) {
                if (!sl.getBlockState(q).getCollisionShape(sl, q).isEmpty()) {
                    if (canBreak(q)) { startWork(q); return true; }
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
            if (o == null) return;
            if (work != null) {
                if (getTarget() != null) stopWork();
                else { workTick(); return; }
            }
            if (getTarget() != null) return;
            int ord = o.getPersistentData().getInt("alb_mode");
            if (ord <= 0) { ore = null; offer = null; return; }
            if (mt > 0) { mt--; return; }
            boolean did = ord == 1 ? zoneStep(o) : oreStep(o);
            mt = did ? 3 : 30;
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
            if (o == null || a.getTarget() != null || a.work != null) return false;
            int ord = o.getPersistentData().getInt("alb_mode");
            double lim = (ord > 0 && (a.ore != null || ord == 1) && a.distanceToSqr(o) < 900.0) ? 900.0 : 36.0;
            return a.distanceToSqr(o) > lim;
        }

        @Override
        public boolean canContinueToUse() {
            ServerPlayer o = a.ownerPlayer();
            return o != null && a.getTarget() == null && a.work == null && a.distanceToSqr(o) > 9.0;
        }

        @Override
        public void tick() {
            ServerPlayer o = a.ownerPlayer();
            if (o == null) return;
            if (a.distanceToSqr(o) > 1600.0) {
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
