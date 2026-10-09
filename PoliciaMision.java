package com.example.policia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Enemigos de policia, comisario de aldea y sistema de misiones. */
public class PoliciaMision {
    public static final RegistryObject<EntityType<Pj>> PJ = PoliciaRefuerzo.ENTITIES.register("bandido",
            () -> EntityType.Builder.<Pj>of(Pj::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f).noSave().clientTrackingRange(10).build("policia:bandido"));
    public static final RegistryObject<EntityType<Pj>> COM = PoliciaRefuerzo.ENTITIES.register("comisario",
            () -> EntityType.Builder.<Pj>of(Pj::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f).clientTrackingRange(10).build("policia:comisario"));
    public static final RegistryObject<EntityType<Pj>> VAN = PoliciaRefuerzo.ENTITIES.register("furgoneta",
            () -> EntityType.Builder.<Pj>of(Pj::new, MobCategory.MISC)
                    .sized(2.0f, 1.8f).noSave().clientTrackingRange(10).build("policia:furgoneta"));

    static final String[] NAMES = {"Ladron", "Pandillero", "Pandillero arquero", "Lider de pandilla", "Jefe de banda",
            "Francotirador", "Contrabandista", "Saboteador", "Evadido", "Aldeano", "Comisario", "El Cerebro", "Mercader negro"};
    static final double[] HP = {16, 22, 18, 40, 120, 16, 30, 14, 20, 20, 40, 220, 40};
    static final double[] SPD = {0.23, 0.26, 0.24, 0.27, 0.27, 0.0, 0.25, 0.25, 0.25, 0.22, 0.0, 0.3, 0.0};
    static final double[] DMG = {2, 4, 2, 6, 9, 2, 3, 1, 3, 1, 1, 11, 1};
    static final int[] XP = {0, 8, 6, 10, 12, 10, 10, 10, 40, 70, 60, 30};
    static final int[] TIME = {0, 6000, 4800, 9600, 9600, 12000, 12000, 3000, 14400, 24000, 36000, 24000};
    static final String[] TITLE = {"", "Atrapar al fugitivo", "Patrulla de 3 puntos", "Rescate de aldeano", "Limpiar el campamento",
            "Recuperar evidencia", "Escolta de aldeano", "Desactivar bombas", "Cazar al jefe de banda", "El cerebro de la organizacion", "Rescate en el casino", "Asalto al campamento"};
    static final String[] DESC = {"", "Un delincuente huye con lo robado. Esposelo con la habilidad Esposas.",
            "Recorra los 3 puntos marcados antes de que se acabe el tiempo.",
            "Un aldeano fue secuestrado. Liberelo (clic derecho) y llevelo al comisario.",
            "Una pandilla acampa cerca. Eliminela entera, hay un francotirador.",
            "Traiga el papel de evidencia del cofre vigilado hasta el comisario.",
            "Lleve al aldeano a salvo hasta el punto marcado. Habra una emboscada.",
            "Un saboteador dejo 3 bombas. Desactivelas (romperlas) antes de que expire el tiempo.",
            "El jefe de la banda se esconde con su guardia. Cacelo.",
            "Siga las pistas hasta la guarida del cerebro de la organizacion y derrotelo. Se teletransporta e invoca refuerzos.", "Los ladrones del casino secuestraron a unos aldeanos. Baje al casino subterraneo (tres plantas) y libere a un rehen.", "Hay un campamento de bandidos o ladrones en la zona. Asaltelo y elimine a todos sus habitantes."};
    static final Item[] ICON = {Items.PAPER, Items.LEAD, Items.COMPASS, Items.NAME_TAG, Items.CAMPFIRE, Items.CHEST,
            Items.SADDLE, Items.TNT, Items.SKELETON_SKULL, Items.NETHER_STAR, Items.GOLD_INGOT, Items.CROSSBOW};
    static final String[] DIRS = {"este", "sureste", "sur", "suroeste", "oeste", "noroeste", "norte", "noreste"};

    static final Map<UUID, M> ACTIVE = new HashMap<>();

    static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaMision::attrs);
        PoliciaBubble.init();
        MinecraftForge.EVENT_BUS.register(new Ev());
    }

    static void attrs(EntityAttributeCreationEvent e) {
        AttributeSupplier s = Pj.attrs().build();
        e.put(PJ.get(), s);
        e.put(COM.get(), s);
        e.put(VAN.get(), s);
    }

    // ---------------------------------------------------------------- entidad

    public static class Pj extends PathfinderMob implements RangedAttackMob {
        static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(Pj.class, EntityDataSerializers.INT);
        static final EntityDataAccessor<Boolean> CAP = SynchedEntityData.defineId(Pj.class, EntityDataSerializers.BOOLEAN);

        public UUID owner;
        public UUID mission;
        public int life = 6000;
        final ServerBossEvent bar = new ServerBossEvent(Component.literal("Jefe de banda"),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

        public Pj(EntityType<? extends Pj> t, Level l) {
            super(t, l);
            this.setPersistenceRequired();
        }

        static AttributeSupplier.Builder attrs() {
            return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.25)
                    .add(Attributes.ATTACK_DAMAGE, 3.0).add(Attributes.FOLLOW_RANGE, 40.0);
        }

        @Override
        protected void defineSynchedData() {
            super.defineSynchedData();
            this.entityData.define(KIND, 1);
            this.entityData.define(CAP, false);
        }

        public int kind() { return this.entityData.get(KIND); }

        public boolean captive() { return this.entityData.get(CAP); }

        public void setCaptive(boolean c) {
            this.entityData.set(CAP, c);
            this.setNoAi(c);
        }

        public void setKind(int k) {
            this.entityData.set(KIND, k);
            if (k < 0 || k > 12) return;
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HP[k]);
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(SPD[k]);
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(DMG[k]);
            if (k == 4) {
                this.getAttribute(Attributes.ARMOR).setBaseValue(8.0);
                this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.6);
            } else if (k == 3) {
                this.getAttribute(Attributes.ARMOR).setBaseValue(4.0);
            }
            this.setHealth((float) HP[k]);
            this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(k == 2 ? net.minecraft.world.item.Items.BOW : k == 5 ? net.minecraft.world.item.Items.CROSSBOW : (k == 10 || k == 12) ? net.minecraft.world.item.Items.AIR : (k == 4 || k == 11) ? net.minecraft.world.item.Items.IRON_SWORD : (k == 9 ? net.minecraft.world.item.Items.AIR : net.minecraft.world.item.Items.STONE_SWORD)));
            this.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0f);
            this.setCustomName(Component.literal(NAMES[k]));
            this.setCustomNameVisible(k == 3 || k == 4 || k == 10 || k == 11 || k == 12);
            if (k == 11) {
                this.getAttribute(Attributes.ARMOR).setBaseValue(10.0);
                this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.8);
                this.bar.setName(Component.literal("El Cerebro"));
                this.bar.setColor(BossEvent.BossBarColor.PURPLE);
            }
            if (k == 4) this.bar.setName(Component.literal("Jefe de banda"));
        }

        boolean hostile() { int k = kind(); return (k >= 1 && k <= 5) || k == 11; }

        ItemStack stolen = ItemStack.EMPTY;
        int fleeT = 0, stealCd = 100, hits = 0;
        boolean sneaking = false;

        /** Ladron de campamento: se acerca de a uno, roba un objeto al azar y huye. */
        boolean thief() { return kind() == 0 && !captive(); }

        boolean flees() { int k = kind(); return (k == 0 && (!stolen.isEmpty() || fleeT > 0)) || k == 6 || k == 7 || k == 8; }

        void steal(Player p) {
            List<Integer> slots = new ArrayList<>();
            for (int i = 0; i < 36; i++) if (!p.getInventory().getItem(i).isEmpty()) slots.add(i);
            if (slots.isEmpty()) { stealCd = 600; return; }
            ItemStack it = p.getInventory().getItem(slots.get(this.random.nextInt(slots.size())));
            stolen = it.split(Math.min(it.getCount(), 16));
            p.getInventory().setChanged();
            this.getPersistentData().put("pol_stolen", stolen.save(new CompoundTag()));
            fleeT = 1200;
            hits = 0;
            this.playSound(SoundEvents.ITEM_PICKUP, 1.0f, 0.7f);
            if (p instanceof ServerPlayer sp) sp.sendSystemMessage(Component.literal("Un ladron te robo " + stolen.getCount() + " x " + stolen.getHoverName().getString() + "! Pegale 2 veces para que lo suelte"));
        }

        /** Guarda lo robado en un cofre cercano al centro del escondite. */
        void deposit() {
            BlockPos c = this.getRestrictCenter();
            for (BlockPos bp : BlockPos.betweenClosed(c.offset(-9, -3, -9), c.offset(9, 4, 9))) {
                if (this.level().getBlockEntity(bp) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity ch) {
                    ItemStack left = net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(null, ch, stolen, null);
                    stolen = left;
                    if (left.isEmpty()) {
                        this.getPersistentData().remove("pol_stolen");
                        fleeT = 0;
                        stealCd = 1200;
                        return;
                    }
                }
            }
            if (!stolen.isEmpty()) this.getPersistentData().put("pol_stolen", stolen.save(new CompoundTag()));
        }

        void dropStolen() {
            if (stolen.isEmpty()) return;
            this.spawnAtLocation(stolen);
            stolen = ItemStack.EMPTY;
            this.getPersistentData().remove("pol_stolen");
            hits = 0;
            fleeT = 300;
            stealCd = 1200;
            this.playSound(SoundEvents.ITEM_PICKUP, 1.0f, 1.3f);
            if (this.level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 10, 0.3, 0.5, 0.3, 0.02);
        }

        @Override
        public boolean hurt(DamageSource s, float a) {
            boolean r = super.hurt(s, a);
            if (r && !this.level().isClientSide && thief()) {
                fleeT = Math.max(fleeT, 100);
                if (!stolen.isEmpty() && s.getEntity() instanceof Player && ++hits >= 2) dropStolen();
            }
            return r;
        }

        boolean melee() { int k = kind(); return k == 1 || k == 3 || k == 4 || k == 11; }

        int tpc = 0, stage = 0;

        /** Jefe final: se teletransporta cerca de su objetivo e invoca refuerzos al perder vida. */
        void cerebro() {
            if (!(this.level() instanceof ServerLevel sl)) return;
            LivingEntity t = this.getTarget();
            if (t == null) return;
            if (++tpc >= 100) {
                tpc = 0;
                if (this.distanceToSqr(t) > 25.0 && this.random.nextInt(2) == 0) {
                    sl.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1, getZ(), 30, 0.3, 0.6, 0.3, 0.3);
                    double ang = this.random.nextDouble() * Math.PI * 2.0;
                    if (this.randomTeleport(t.getX() + Math.cos(ang) * 3.0, t.getY(), t.getZ() + Math.sin(ang) * 3.0, true)) {
                        sl.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1, getZ(), 30, 0.3, 0.6, 0.3, 0.3);
                        this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    }
                }
            }
            float f = getHealth() / getMaxHealth();
            if ((stage == 0 && f < 0.66f) || (stage == 1 && f < 0.33f)) {
                stage++;
                M mm = mission == null ? null : ACTIVE.get(mission);
                for (int i = 0; i < 2; i++) {
                    Pj g1 = spawn(sl, mm, 1, around(sl, blockPosition(), 4));
                    g1.life = 3000;
                }
                Pj g2 = spawn(sl, mm, 2, around(sl, blockPosition(), 4));
                g2.life = 3000;
                this.playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.0f, 1.0f);
            }
        }

        Player ownerPlayer() { return owner == null ? null : this.level().getPlayerByUUID(owner); }

        static boolean police(LivingEntity le) {
            return le instanceof Player p && "policia".equals(PoliciaMod.job(p));
        }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(0, new FloatGoal(this));
            this.goalSelector.addGoal(1, new AvoidEntityGoal<Player>(this, Player.class, 18.0f, 1.0, 1.35) {
                @Override
                public boolean canUse() { return Pj.this.flees() && !Pj.this.captive() && super.canUse(); }
            });
            // el ladron que escapo con un objeto vuelve a su escondite y lo guarda en un cofre
            this.goalSelector.addGoal(3, new Goal() {
                {
                    this.setFlags(EnumSet.of(Goal.Flag.MOVE));
                }

                boolean far() {
                    return Pj.this.hasRestriction() && Pj.this.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(Pj.this.getRestrictCenter())) > 12.0 * 12.0;
                }

                boolean safe() {
                    Player np = Pj.this.level().getNearestPlayer(Pj.this, 14.0);
                    return np == null;
                }

                @Override
                public boolean canUse() {
                    return Pj.this.thief() && !Pj.this.stolen.isEmpty() && Pj.this.hasRestriction() && far() && safe();
                }

                @Override
                public boolean canContinueToUse() {
                    return Pj.this.thief() && !Pj.this.stolen.isEmpty() && Pj.this.hasRestriction() && safe() && Pj.this.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(Pj.this.getRestrictCenter())) > 3.0 * 3.0;
                }

                @Override
                public void tick() {
                    if (Pj.this.tickCount % 10 == 0) {
                        BlockPos c = Pj.this.getRestrictCenter();
                        Pj.this.getNavigation().moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 1.0);
                    }
                }

                @Override
                public void stop() {
                    if (Pj.this.hasRestriction() && !Pj.this.stolen.isEmpty() && Pj.this.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(Pj.this.getRestrictCenter())) <= 3.0 * 3.0) Pj.this.deposit();
                }
            });
            this.goalSelector.addGoal(2, new Goal() {
                Player tgt;
                int t = 0;

                {
                    this.setFlags(EnumSet.of(Goal.Flag.MOVE));
                }

                @Override
                public boolean canUse() {
                    if (!Pj.this.thief() || Pj.this.captive() || !Pj.this.stolen.isEmpty() || Pj.this.fleeT > 0 || Pj.this.stealCd > 0) return false;
                    if (Pj.this.getRandom().nextInt(10) != 0) return false;
                    Player best = null;
                    double bd = 18.0 * 18.0;
                    for (Player p : Pj.this.level().players()) {
                        if (p.isCreative() || p.isSpectator() || !p.isAlive() || "ladron".equals(PoliciaMod.job(p))) continue;
                        double d = p.distanceToSqr(Pj.this);
                        if (d < bd) { bd = d; best = p; }
                    }
                    if (best == null) return false;
                    if (!Pj.this.level().getEntitiesOfClass(Pj.class, Pj.this.getBoundingBox().inflate(24.0), x -> x != Pj.this && x.sneaking).isEmpty()) return false;
                    tgt = best;
                    return true;
                }

                @Override
                public boolean canContinueToUse() {
                    return tgt != null && tgt.isAlive() && Pj.this.stolen.isEmpty() && Pj.this.fleeT <= 0 && t < 700 && Pj.this.distanceToSqr(tgt) < 30.0 * 30.0;
                }

                @Override
                public void start() {
                    Pj.this.sneaking = true;
                    t = 0;
                }

                @Override
                public void stop() {
                    Pj.this.sneaking = false;
                    tgt = null;
                    Pj.this.stealCd = 200;
                    Pj.this.getNavigation().stop();
                }

                @Override
                public void tick() {
                    t++;
                    net.minecraft.world.phys.Vec3 look = tgt.getLookAngle();
                    double dx = Pj.this.getX() - tgt.getX(), dz = Pj.this.getZ() - tgt.getZ();
                    double len = Math.sqrt(dx * dx + dz * dz);
                    double hl = Math.max(1.0E-4, Math.sqrt(look.x * look.x + look.z * look.z));
                    boolean seen = len > 1.0E-4 && (look.x * dx + look.z * dz) / (len * hl) > 0.2;
                    double d2 = Pj.this.distanceToSqr(tgt);
                    if (!seen && d2 < 2.4 * 2.4) {
                        Pj.this.steal(tgt);
                        return;
                    }
                    if (seen && d2 < 64.0) {
                        Pj.this.getNavigation().stop();
                        Pj.this.getLookControl().setLookAt(tgt, 30.0f, 30.0f);
                    } else if (t % 5 == 0) {
                        Pj.this.getNavigation().moveTo(tgt, seen ? 0.8 : 1.1);
                    }
                }
            });
            this.goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 30, 16.0f) {
                @Override
                public boolean canUse() { return Pj.this.kind() == 2 && super.canUse(); }
            });
            this.goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 50, 40.0f) {
                @Override
                public boolean canUse() { return Pj.this.kind() == 5 && super.canUse(); }
            });
            this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15, false) {
                @Override
                public boolean canUse() { return Pj.this.melee() && super.canUse(); }
            });
            this.goalSelector.addGoal(4, new Goal() {
                {
                    this.setFlags(EnumSet.of(Goal.Flag.MOVE));
                }

                @Override
                public boolean canUse() {
                    return Pj.this.kind() == 9 && !Pj.this.captive() && Pj.this.ownerPlayer() != null;
                }

                @Override
                public void tick() {
                    Player o = Pj.this.ownerPlayer();
                    if (o == null) return;
                    if (Pj.this.distanceToSqr(o) > 9.0) Pj.this.getNavigation().moveTo(o, 1.2);
                }
            });
            this.goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 1.0));
            this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8) {
                @Override
                public boolean canUse() {
                    int k = Pj.this.kind();
                    return k != 5 && k != 9 && k != 10 && k != 12 && super.canUse();
                }
            });
            this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0f));
            this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
            this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
                @Override
                public boolean canUse() { return Pj.this.hostile() && super.canUse(); }
            });
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<Player>(this, Player.class, 10, true, false,
                    le -> Pj.this.hostile() && Pj.police(le)));
            // los ladrones se pelean con los agentes y guardias (nunca con un jugador ladron)
            this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<PoliciaBoss.BossNpc>(this, PoliciaBoss.BossNpc.class, 10, true, false,
                    le -> Pj.this.hostile() && le instanceof PoliciaBoss.BossNpc bn && (bn.kind() == 4 || bn.kind() == 5)));
        }

        @Override
        public void setTarget(LivingEntity t) {
            if (t != null && !hostile()) return;
            super.setTarget(t);
        }

        @Override
        public void performRangedAttack(LivingEntity t, float f) {
            Arrow a = new Arrow(this.level(), this);
            double dx = t.getX() - this.getX();
            double dz = t.getZ() - this.getZ();
            double dy = t.getY(0.3333) - a.getY();
            double h = Math.sqrt(dx * dx + dz * dz);
            boolean sn = kind() == 5;
            a.shoot(dx, dy + h * 0.2, dz, sn ? 2.4f : 1.6f, sn ? 0.8f : 10.0f);
            a.setBaseDamage(sn ? 5.0 : 2.0);
            a.pickup = AbstractArrow.Pickup.DISALLOWED;
            this.level().addFreshEntity(a);
            this.playSound(SoundEvents.SKELETON_SHOOT, 1.0f, 1.0f / (this.getRandom().nextFloat() * 0.4f + 0.8f));
        }

        @Override
        public InteractionResult mobInteract(Player p, InteractionHand h) {
            if (h != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (p instanceof ServerPlayer sp) {
                if (kind() == 10) openBoard(sp, this);
                else if (kind() == 12) PoliciaLadron.market(sp, this);
                else if (kind() == 9 && captive()) free(sp, this);
            }
            return (kind() == 10 || kind() == 12 || (kind() == 9 && captive())) ? InteractionResult.sidedSuccess(this.level().isClientSide) : InteractionResult.PASS;
        }

        @Override
        public boolean isInvulnerableTo(DamageSource s) {
            if ((kind() == 10 || kind() == 12) && !s.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
            return super.isInvulnerableTo(s);
        }

        @Override
        public boolean isPushable() { return kind() != 10 && kind() != 12 && !captive() && super.isPushable(); }

        @Override
        public boolean removeWhenFarAway(double d) { return false; }

        @Override
        public void tick() {
            super.tick();
            if (this.level().isClientSide) return;
            if (kind() != 10 && kind() != 12 && !this.getPersistentData().getBoolean("pol_cas") && --life <= 0) {
                ((ServerLevel) this.level()).sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 8, 0.3, 0.5, 0.3, 0.02);
                this.discard();
                return;
            }
            if (stealCd > 0) stealCd--;
            if (fleeT > 0) fleeT--;
            if (stolen.isEmpty() && this.getPersistentData().contains("pol_stolen")) stolen = ItemStack.of(this.getPersistentData().getCompound("pol_stolen"));
            if (kind() == 4 || kind() == 11) bar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0.0f, 1.0f));
            if (kind() == 11) cerebro();
        }

        @Override
        public void startSeenByPlayer(ServerPlayer p) {
            super.startSeenByPlayer(p);
            if (kind() == 4 || kind() == 11) bar.addPlayer(p);
        }

        @Override
        public void stopSeenByPlayer(ServerPlayer p) {
            super.stopSeenByPlayer(p);
            bar.removePlayer(p);
        }

        @Override
        public void remove(Entity.RemovalReason r) {
            bar.removeAllPlayers();
            super.remove(r);
        }

        @Override
        public void die(DamageSource s) {
            super.die(s);
            if (!this.level().isClientSide) dead(this);
        }

        @Override
        protected void dropCustomDeathLoot(DamageSource s, int looting, boolean hit) {
            super.dropCustomDeathLoot(s, looting, hit);
            int k = kind();
            if (k == 0 || k == 6 || k == 8) this.spawnAtLocation(new ItemStack(Items.EMERALD, 2 + this.random.nextInt(3)));
            else if (k >= 1 && k <= 5 && this.random.nextInt(3) == 0) this.spawnAtLocation(new ItemStack(Items.EMERALD));
            if (k == 4) {
                this.spawnAtLocation(new ItemStack(Items.GOLD_INGOT, 4));
                this.spawnAtLocation(new ItemStack(Items.EMERALD, 8));
                ItemStack sw = new ItemStack(Items.DIAMOND_SWORD);
                sw.setHoverName(Component.literal("Espada del Jefe de Banda"));
                sw.enchant(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS, 4);
                sw.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 3);
                sw.enchant(net.minecraft.world.item.enchantment.Enchantments.FIRE_ASPECT, 2);
                this.spawnAtLocation(sw);
            }
            dropStolen();
        }

        @Override
        public void addAdditionalSaveData(CompoundTag t) {
            super.addAdditionalSaveData(t);
            t.putInt("pk", kind());
            t.putBoolean("pcap", captive());
        }

        @Override
        public void readAdditionalSaveData(CompoundTag t) {
            super.readAdditionalSaveData(t);
            if (t.contains("pk")) setKind(t.getInt("pk"));
            if (t.contains("pcap")) setCaptive(t.getBoolean("pcap"));
        }
    }

    // ---------------------------------------------------------------- mision

    static class M {
        UUID pl;
        int type, phase, n;
        long start, end;
        ResourceKey<Level> dim;
        BlockPos origin, dest, chest;
        List<UUID> ents = new ArrayList<>();
        List<BlockPos> pts = new ArrayList<>();
        List<BlockPos> bombs = new ArrayList<>();
        Map<BlockPos, Block> blocks = new LinkedHashMap<>();
        UUID target, hostage;
        boolean killed, hostDead;
    }

    static void dead(Pj p) {
        if (p.mission == null) return;
        M m = ACTIVE.get(p.mission);
        if (m == null) return;
        if (p.getUUID().equals(m.target)) m.killed = true;
        if (p.getUUID().equals(m.hostage)) m.hostDead = true;
    }

    static String dir(Vec3 from, BlockPos to) {
        double dx = to.getX() - from.x, dz = to.getZ() - from.z;
        double deg = Math.toDegrees(Math.atan2(dz, dx));
        int i = (int) Math.round(((deg + 360.0) % 360.0) / 45.0) % 8;
        return DIRS[i] + ", " + (int) Math.sqrt(dx * dx + dz * dz) + " m";
    }

    static BlockPos at(ServerLevel sl, int x, int z) {
        if (!sl.hasChunk(x >> 4, z >> 4)) return null;
        int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos g = new BlockPos(x, y, z);
        BlockState b = sl.getBlockState(g.below());
        if (b.isAir() || !b.getFluidState().isEmpty()) return null;
        return g;
    }

    static BlockPos site(ServerLevel sl, BlockPos c, int min, int max) {
        for (int i = 0; i < 24; i++) {
            double ang = sl.random.nextDouble() * Math.PI * 2.0;
            double d = min + sl.random.nextDouble() * (max - min);
            BlockPos p = at(sl, c.getX() + (int) (Math.cos(ang) * d), c.getZ() + (int) (Math.sin(ang) * d));
            if (p != null) return p;
        }
        return null;
    }

    static BlockPos around(ServerLevel sl, BlockPos c, int r) {
        for (int i = 0; i < 8; i++) {
            BlockPos p = at(sl, c.getX() + sl.random.nextInt(r * 2 + 1) - r, c.getZ() + sl.random.nextInt(r * 2 + 1) - r);
            if (p != null && Math.abs(p.getY() - c.getY()) <= 4) return p;
        }
        return c;
    }

    static Pj spawn(ServerLevel sl, M m, int kind, BlockPos p) {
        Pj e = new Pj(kind == 6 ? VAN.get() : PJ.get(), sl);
        e.setKind(kind);
        e.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, sl.random.nextFloat() * 360.0f, 0.0f);
        e.life = 24000;
        if (kind == 0 && m != null) e.fleeT = 1200;
        if (m != null) {
            e.mission = m.pl;
            m.ents.add(e.getUUID());
        }
        sl.addFreshEntity(e);
        return e;
    }

    static void put(ServerLevel sl, M m, BlockPos p, Block b) {
        sl.setBlock(p, b.defaultBlockState(), 3);
        m.blocks.put(p.immutable(), b);
    }

    static BlockPos tower(ServerLevel sl, M m, BlockPos g) {
        for (int i = 0; i < 4; i++) put(sl, m, g.above(i), Blocks.COBBLESTONE);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) put(sl, m, g.offset(dx, 4, dz), Blocks.OAK_PLANKS);
        return g.above(5);
    }

    static void camp(ServerLevel sl, M m, BlockPos s, int nPand, boolean leader, boolean boss) {
        for (int i = 0; i < nPand; i++) spawn(sl, m, 1, around(sl, s, 4)).restrictTo(s, 9);
        spawn(sl, m, 2, around(sl, s, 4)).restrictTo(s, 9);
        if (leader) spawn(sl, m, 3, around(sl, s, 3)).restrictTo(s, 9);
        if (boss) {
            Pj b = spawn(sl, m, 4, s);
            b.restrictTo(s, 9);
            m.target = b.getUUID();
        }
        BlockPos tg = at(sl, s.getX() + 8, s.getZ() + 3);
        if (tg != null) {
            BlockPos top = tower(sl, m, tg);
            spawn(sl, m, 5, top);
        }
    }

    static boolean setup(ServerLevel sl, ServerPlayer sp, M m) {
        BlockPos pp = sp.blockPosition();
        switch (m.type) {
            case 1: {
                BlockPos s = site(sl, pp, 30, 45);
                if (s == null) return false;
                int[] ks = {0, 0, 6, 8};
                Pj e = spawn(sl, m, ks[sl.random.nextInt(ks.length)], s);
                m.target = e.getUUID();
                PoliciaMod.msg(sp, "Un " + NAMES[e.kind()].toLowerCase() + " huye con lo robado. Busquelo y esposelo.");
                return true;
            }
            case 2: {
                double a0 = sl.random.nextDouble() * Math.PI * 2.0;
                for (int i = 0; i < 3; i++) {
                    double ang = a0 + i * 2.0944;
                    double d = 28 + sl.random.nextInt(14);
                    BlockPos p = at(sl, pp.getX() + (int) (Math.cos(ang) * d), pp.getZ() + (int) (Math.sin(ang) * d));
                    if (p == null) p = site(sl, pp, 25, 40);
                    if (p == null) return false;
                    m.pts.add(p);
                }
                return true;
            }
            case 3: {
                BlockPos s = site(sl, pp, 38, 55);
                if (s == null) return false;
                for (int i = 0; i < 2; i++) spawn(sl, m, 1, around(sl, s, 4)).restrictTo(s, 8);
                spawn(sl, m, 2, around(sl, s, 4)).restrictTo(s, 8);
                Pj h = spawn(sl, m, 9, s);
                h.setCaptive(true);
                h.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20000, 0, false, false));
                m.hostage = h.getUUID();
                m.dest = s;
                return true;
            }
            case 4: {
                BlockPos s = site(sl, pp, 40, 60);
                if (s == null) return false;
                put(sl, m, s, Blocks.CAMPFIRE);
                camp(sl, m, s, 3, true, false);
                m.dest = s;
                return true;
            }
            case 5: {
                BlockPos s = site(sl, pp, 45, 65);
                if (s == null) return false;
                put(sl, m, s, Blocks.CHEST);
                m.chest = s;
                ItemStack ev = new ItemStack(Items.PAPER);
                ev.setHoverName(Component.literal("Evidencia"));
                ev.getOrCreateTag().putBoolean("pol_evid", true);
                if (sl.getBlockEntity(s) instanceof ChestBlockEntity ce) ce.setItem(13, ev);
                for (int i = 0; i < 2; i++) spawn(sl, m, 1, around(sl, s, 4)).restrictTo(s, 8);
                spawn(sl, m, 2, around(sl, s, 4)).restrictTo(s, 8);
                m.dest = s;
                return true;
            }
            case 6: {
                BlockPos s = site(sl, pp, 55, 70);
                if (s == null) return false;
                Pj h = spawn(sl, m, 9, around(sl, pp, 2));
                h.owner = sp.getUUID();
                m.hostage = h.getUUID();
                m.dest = s;
                return true;
            }
            case 7: {
                BlockPos s = site(sl, pp, 35, 50);
                if (s == null) return false;
                int[][] off = {{0, 0}, {9, 4}, {-6, 8}};
                for (int[] o : off) {
                    BlockPos b = at(sl, s.getX() + o[0], s.getZ() + o[1]);
                    if (b == null) b = s.offset(o[0] / 3, 0, o[1] / 3);
                    put(sl, m, b, Blocks.TNT);
                    m.bombs.add(b.immutable());
                }
                spawn(sl, m, 7, around(sl, s, 5));
                m.dest = s;
                return true;
            }
            case 8: {
                BlockPos s = site(sl, pp, 55, 70);
                if (s == null) return false;
                camp(sl, m, s, 2, true, true);
                m.dest = s;
                return true;
            }
            case 11: {
                BlockPos s = campOf(sl, pp);
                if (s == null) return false;
                m.dest = s;
                return true;
            }
            default:
                return false;
        }
    }

    /** Campamento de bandidos o ladrones conocido mas cercano. */
    static BlockPos campOf(ServerLevel sl, BlockPos from) {
        PoliciaStruct.Sites st = PoliciaStruct.Sites.get(sl);
        BlockPos best = null;
        double bd = 1200.0 * 1200.0;
        for (int k = 3; k <= 6; k++) {
            BlockPos p = st.nearest(k, from);
            if (p != null && p.distSqr(from) < bd) {
                bd = p.distSqr(from);
                best = p;
            }
        }
        return best;
    }

    static boolean setup9(ServerLevel sl, ServerPlayer sp, M m) {
        BlockPos pp = sp.blockPosition();
        BlockPos c1 = site(sl, pp, 25, 38);
        BlockPos c2 = site(sl, pp, 40, 55);
        BlockPos s = site(sl, pp, 55, 70);
        if (c1 == null || c2 == null || s == null) return false;
        m.pts.add(c1);
        m.pts.add(c2);
        camp(sl, m, s, 3, true, false);
        spawn(sl, m, 3, around(sl, s, 4)).restrictTo(s, 9);
        spawn(sl, m, 2, around(sl, s, 4)).restrictTo(s, 9);
        BlockPos tg = at(sl, s.getX() - 8, s.getZ() - 3);
        if (tg != null) spawn(sl, m, 5, tower(sl, m, tg));
        Pj b = spawn(sl, m, 11, s);
        b.restrictTo(s, 10);
        m.target = b.getUUID();
        m.dest = s;
        return true;
    }

    static void start(ServerPlayer sp, int type, BlockPos origin) {
        if (ACTIVE.containsKey(sp.getUUID())) {
            PoliciaMod.msg(sp, "Ya tiene una mision en curso");
            return;
        }
        ServerLevel sl = sp.serverLevel();
        M m = new M();
        m.pl = sp.getUUID();
        m.type = type;
        m.dim = sl.dimension();
        m.origin = origin;
        m.start = sl.getGameTime();
        m.end = m.start + TIME[type];
        ACTIVE.put(m.pl, m);
        if (!(type == 9 ? setup9(sl, sp, m) : type == 10 ? PoliciaBoard.setup10(sl, sp, m) : setup(sl, sp, m))) {
            cleanup(sl, m);
            PoliciaMod.msg(sp, "No encuentro un lugar para la mision, intente en otra zona");
            return;
        }
        PoliciaBoard.narrate(sp, type, m);
    }

    static void cleanup(ServerLevel sl, M m) {
        ACTIVE.remove(m.pl);
        ServerPlayer cp = sl.getServer().getPlayerList().getPlayer(m.pl);
        if (cp != null) PoliciaBoard.removeCompass(cp);
        for (UUID u : m.ents) {
            Entity e = sl.getEntity(u);
            if (e != null) {
                sl.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 1, e.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
                e.discard();
            }
        }
        for (Map.Entry<BlockPos, Block> en : m.blocks.entrySet()) {
            if (sl.getBlockState(en.getKey()).is(en.getValue())) sl.setBlock(en.getKey(), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    static void fail(ServerPlayer sp, ServerLevel sl, M m, String why) {
        if (m.type == 7) {
            for (BlockPos b : m.bombs) sl.explode(null, b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 2.5f, Level.ExplosionInteraction.NONE);
        }
        cleanup(sl, m);
        if (sp != null) {
            sp.sendSystemMessage(Component.literal("[Comisario] Mision fallida: " + why));
            PoliciaMod.msg(sp, "Mision fallida: " + why);
        }
    }

    static void give(ServerPlayer sp, ItemStack s) {
        if (!sp.getInventory().add(s)) sp.drop(s, false);
    }

    static void finish(ServerPlayer sp, ServerLevel sl, M m, double scale) {
        int xp = (int) Math.round(XP[m.type] * scale);
        int em = Math.max(1, xp / 2);
        sp.getPersistentData().putInt("pol_xp", PoliciaMod.xp(sp) + xp);
        sp.getPersistentData().putInt("pol_mdone", sp.getPersistentData().getInt("pol_mdone") + 1);
        give(sp, new ItemStack(Items.EMERALD, em));
        PoliciaGrupo.share(sp, xp, em);
        String extra = "";
        if (m.type == 4 || m.type == 3) {
            give(sp, new ItemStack(Items.GOLDEN_APPLE));
            extra = ", 1 manzana dorada";
        }
        if (m.type == 9) {
            sp.getPersistentData().putBoolean("pol_cerebro", true);
            sp.getPersistentData().putInt("pol_un", PoliciaMod.un(sp) | (1 << 9));
            give(sp, new ItemStack(Items.DIAMOND, 5));
            give(sp, new ItemStack(Items.NETHERITE_INGOT));
            extra = ", 5 diamantes, un lingote de netherita y la habilidad ESCUDO DE BURBUJA";
        }
        if (m.type == 11) {
            give(sp, new ItemStack(Items.DIAMOND, 2));
            extra = ", 2 diamantes";
        }
        if (m.type == 8) {
            sp.getPersistentData().putInt("pol_bdone", sp.getPersistentData().getInt("pol_bdone") + 1);
            give(sp, new ItemStack(Items.DIAMOND, 3));
            give(sp, new ItemStack(Items.TOTEM_OF_UNDYING));
            extra = ", 3 diamantes y un totem";
        }
        PoliciaMod.sync(sp);
        sp.sendSystemMessage(Component.literal("[Comisario] Mision cumplida: " + TITLE[m.type] + ". Recompensa: " + xp + " XP, " + em + " esmeraldas" + extra + "."));
        PoliciaMod.msg(sp, "Mision cumplida +" + xp + " XP");
        sl.playSound(null, sp.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.0f);
        cleanup(sl, m);
    }

    static void free(ServerPlayer sp, Pj h) {
        M m = ACTIVE.get(sp.getUUID());
        if (h.getPersistentData().getBoolean("pol_cas")) {
            h.setCaptive(false);
            h.removeEffect(MobEffects.GLOWING);
            h.owner = sp.getUUID();
            h.getPersistentData().putBoolean("pol_cas", false);
            sp.sendSystemMessage(Component.literal("[Comisario] Rehen liberado."));
            if (m != null && m.type == 10) finish(sp, sp.serverLevel(), m, 1.0);
            return;
        }
        if (m == null || m.type != 3 || !h.getUUID().equals(m.hostage)) {
            PoliciaMod.msg(sp, "No es el aldeano de su mision");
            return;
        }
        h.setCaptive(false);
        h.removeEffect(MobEffects.GLOWING);
        h.owner = sp.getUUID();
        m.phase = 1;
        sp.sendSystemMessage(Component.literal("[Comisario] Aldeano liberado. Llevelo de vuelta al comisario."));
    }

    static boolean hasEv(Player p) {
        for (ItemStack s : p.getInventory().items) if (!s.isEmpty() && s.hasTag() && s.getTag().getBoolean("pol_evid")) return true;
        return false;
    }

    static void takeEv(Player p) {
        for (ItemStack s : p.getInventory().items) if (!s.isEmpty() && s.hasTag() && s.getTag().getBoolean("pol_evid")) s.setCount(0);
    }

    static int alive(ServerLevel sl, M m) {
        int n = 0;
        for (UUID u : m.ents) {
            Entity e = sl.getEntity(u);
            if (e instanceof Pj p && p.isAlive() && p.hostile()) n++;
        }
        return n;
    }

    static void col(ServerLevel sl, BlockPos p) {
        for (int y = 0; y < 14; y += 2) sl.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + y, p.getZ() + 0.5, 1, 0.1, 0.0, 0.1, 0.0);
    }

    static void tick9(ServerPlayer sp, ServerLevel sl, M m, Vec3 pos, long t, long now) {
        if (m.killed) {
            finish(sp, sl, m, 1.0);
            return;
        }
        if (m.phase < 2) {
            BlockPos p = m.pts.get(m.phase);
            if (t % 5 == 0) col(sl, p);
            double dx = p.getX() + 0.5 - pos.x, dz = p.getZ() + 0.5 - pos.z;
            if (dx * dx + dz * dz < 25.0 && Math.abs(p.getY() - pos.y) < 10.0) {
                m.phase++;
                sl.playSound(null, sp.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.2f);
                spawn(sl, m, 1, around(sl, p, 6));
                spawn(sl, m, 1, around(sl, p, 6));
                spawn(sl, m, 3, around(sl, p, 6));
                if (m.phase == 1) sp.sendSystemMessage(Component.literal("[Comisario] Documento falso hallado. Hay otra pista mas adentro."));
                else sp.sendSystemMessage(Component.literal("[Comisario] Ya sabemos donde se esconde el cerebro. Vaya a la guarida, con cuidado."));
            } else if (t % 40 == 0) {
                PoliciaMod.msg(sp, "Pista " + (m.phase + 1) + "/2: siga la brujula");
            }
        } else if (t % 40 == 0) {
            PoliciaMod.msg(sp, "Guarida del cerebro: siga la brujula");
        }
    }

    static void tickM(MinecraftServer srv, M m) {
        ServerLevel sl = srv.getLevel(m.dim);
        if (sl == null) {
            ACTIVE.remove(m.pl);
            return;
        }
        ServerPlayer sp = srv.getPlayerList().getPlayer(m.pl);
        if (sp == null || !sp.isAlive() || sp.level() != sl) {
            cleanup(sl, m);
            if (sp != null) PoliciaMod.msg(sp, "Mision cancelada");
            return;
        }
        long now = sl.getGameTime();
        long t = now - m.start;
        if (now > m.end) {
            fail(sp, sl, m, "se acabo el tiempo");
            return;
        }
        Vec3 pos = sp.position();
        boolean hint = false;
        if (m.type == 9) {
            tick9(sp, sl, m, pos, t, now);
            return;
        }
        switch (m.type) {
            case 1: {
                Entity e = sl.getEntity(m.target);
                if (m.killed) {
                    finish(sp, sl, m, 0.5);
                    return;
                }
                if (e instanceof Pj pj) {
                    m.n = 0;
                    if (pj.hasEffect(PoliciaExtra.CUFF.get())) {
                        finish(sp, sl, m, 1.0);
                        return;
                    }
                    if (t % 10 == 0) sl.sendParticles(ParticleTypes.SMOKE, pj.getX(), pj.getY() + 0.2, pj.getZ(), 3, 0.2, 0.1, 0.2, 0.01);
                    if (t % 160 == 0) pj.addEffect(new MobEffectInstance(MobEffects.GLOWING, 50, 0, false, false));
                    if (hint) PoliciaMod.msg(sp, "Sospechoso: " + dir(pos, pj.blockPosition()) + " - " + (m.end - now) / 20 + " s");
                } else if (++m.n > 600) {
                    fail(sp, sl, m, "el sospechoso escapo");
                    return;
                }
                break;
            }
            case 2: {
                BlockPos p = m.pts.get(m.phase);
                double dx = p.getX() + 0.5 - pos.x, dz = p.getZ() + 0.5 - pos.z;
                if (t % 5 == 0) col(sl, p);
                if (dx * dx + dz * dz < 25.0 && Math.abs(p.getY() - pos.y) < 10.0) {
                    m.phase++;
                    sl.playSound(null, sp.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.2f);
                    if (m.phase >= 3) {
                        finish(sp, sl, m, 1.0);
                        return;
                    }
                    PoliciaMod.msg(sp, "Punto " + m.phase + "/3 revisado");
                    if (sl.random.nextInt(100) < 45) {
                        spawn(sl, m, 1, around(sl, p, 6));
                        spawn(sl, m, 2, around(sl, p, 6));
                    }
                } else if (hint) {
                    PoliciaMod.msg(sp, "Punto " + (m.phase + 1) + "/3: " + dir(pos, p) + " - " + (m.end - now) / 20 + " s");
                }
                break;
            }
            case 3: {
                Entity h = sl.getEntity(m.hostage);
                if (m.hostDead || (h == null && t > 100)) {
                    fail(sp, sl, m, "el aldeano murio");
                    return;
                }
                if (m.phase == 0) {
                    if (hint) PoliciaMod.msg(sp, "Rehen: " + dir(pos, m.dest) + " - " + (m.end - now) / 20 + " s");
                } else if (h != null) {
                    if (t % 10 == 0) col(sl, m.origin);
                    double dx = h.getX() - m.origin.getX(), dz = h.getZ() - m.origin.getZ();
                    if (dx * dx + dz * dz < 36.0 && sp.distanceToSqr(h) < 200.0) {
                        finish(sp, sl, m, 1.0);
                        return;
                    }
                    if (hint) PoliciaMod.msg(sp, "Lleve al aldeano al comisario: " + dir(pos, m.origin));
                }
                break;
            }
            case 4:
            case 8: {
                if (t % 20 == 0 && t > 60) {
                    boolean done = m.type == 8 ? m.killed : alive(sl, m) == 0;
                    if (done) {
                        finish(sp, sl, m, 1.0);
                        return;
                    }
                }
                if (hint) PoliciaMod.msg(sp, (m.type == 8 ? "Jefe de banda: " : "Campamento: ") + dir(pos, m.dest) + " - " + (m.end - now) / 20 + " s");
                break;
            }
            case 5: {
                if (m.phase == 0) {
                    if (hasEv(sp)) {
                        m.phase = 1;
                        sp.sendSystemMessage(Component.literal("[Comisario] Tiene la evidencia. Llevela al comisario."));
                    } else if (hint) {
                        PoliciaMod.msg(sp, "Cofre con evidencia: " + dir(pos, m.dest) + " - " + (m.end - now) / 20 + " s");
                    }
                } else {
                    if (!hasEv(sp)) m.phase = 0;
                    double dx = pos.x - m.origin.getX(), dz = pos.z - m.origin.getZ();
                    if (dx * dx + dz * dz < 49.0) {
                        takeEv(sp);
                        finish(sp, sl, m, 1.0);
                        return;
                    }
                    if (hint) PoliciaMod.msg(sp, "Lleve la evidencia al comisario: " + dir(pos, m.origin));
                }
                break;
            }
            case 6: {
                Entity h = sl.getEntity(m.hostage);
                if (m.hostDead || (h == null && t > 100)) {
                    fail(sp, sl, m, "el aldeano murio");
                    return;
                }
                if (h == null) break;
                if (t % 10 == 0) col(sl, m.dest);
                double ox = h.getX() - m.origin.getX(), oz = h.getZ() - m.origin.getZ();
                if (m.phase == 0 && ox * ox + oz * oz > 900.0) {
                    m.phase = 1;
                    BlockPos a = around(sl, h.blockPosition().offset(12, 0, 12), 6);
                    spawn(sl, m, 1, a);
                    spawn(sl, m, 1, around(sl, a, 4));
                    spawn(sl, m, 2, around(sl, a, 4));
                    PoliciaMod.msg(sp, "Emboscada!");
                }
                double dx = h.getX() - m.dest.getX(), dz = h.getZ() - m.dest.getZ();
                if (dx * dx + dz * dz < 36.0 && sp.distanceToSqr(h) < 200.0) {
                    finish(sp, sl, m, 1.0);
                    return;
                }
                if (hint) PoliciaMod.msg(sp, "Destino: " + dir(pos, m.dest) + " - " + (m.end - now) / 20 + " s");
                break;
            }
            case 11: {
                if (t % 20 == 0 && t > 40 && pos.distanceToSqr(Vec3.atCenterOf(m.dest)) < 48.0 * 48.0) {
                    boolean any = false;
                    for (Pj e : sl.getEntitiesOfClass(Pj.class, new net.minecraft.world.phys.AABB(m.dest).inflate(24.0, 12.0, 24.0))) {
                        if (e.isAlive() && e.getPersistentData().getBoolean("pol_camp")) { any = true; break; }
                    }
                    if (!any) {
                        finish(sp, sl, m, 1.0);
                        return;
                    }
                }
                break;
            }
            case 7: {
                if (t % 10 == 0) {
                    for (BlockPos b : m.bombs) sl.sendParticles(ParticleTypes.LARGE_SMOKE, b.getX() + 0.5, b.getY() + 1.2, b.getZ() + 0.5, 2, 0.1, 0.1, 0.1, 0.01);
                }
                if (t % 20 == 0) {
                    m.bombs.removeIf(b -> !sl.getBlockState(b).is(Blocks.TNT));
                    if (m.bombs.isEmpty()) {
                        finish(sp, sl, m, 1.0);
                        return;
                    }
                }
                if (hint) PoliciaMod.msg(sp, "Bombas: " + m.bombs.size() + " - " + (m.end - now) / 20 + " s - " + dir(pos, m.bombs.get(0)));
                break;
            }
            default:
                break;
        }
    }

    // ---------------------------------------------------------------- tablero

    static int[] offered(Pj com, ServerLevel sl) {
        Random r = new Random(com.getUUID().getLeastSignificantBits() ^ ((sl.getGameTime() / 24000L) * 7919L));
        List<Integer> pool = new ArrayList<>();
        for (int i = 1; i <= 7; i++) pool.add(i);
        if (campOf(sl, com.blockPosition()) != null) pool.add(11);
        Collections.shuffle(pool, r);
        return new int[]{pool.get(0), pool.get(1), pool.get(2)};
    }

    static ItemStack named(Item it, String name, String... lore) {
        ItemStack s = new ItemStack(it);
        s.setHoverName(Component.literal(name));
        ListTag l = new ListTag();
        for (String x : lore) l.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(x))));
        s.getOrCreateTagElement("display").put("Lore", l);
        return s;
    }

    static ItemStack missionItem(int type) {
        int xp = XP[type];
        return named(ICON[type], TITLE[type], DESC[type], "Tiempo: " + TIME[type] / 1200 + " min",
                "Recompensa: " + xp + " XP y " + Math.max(1, xp / 2) + " esmeraldas" + (type == 8 ? ", diamantes y un totem" : (type == 4 || type == 3 ? " y una manzana dorada" : "")));
    }

    static class Board extends ChestMenu {
        final int[] ts;
        final BlockPos origin;
        final boolean bossOk, cerOk;

        Board(int id, Inventory inv, SimpleContainer c, int[] ts, BlockPos origin, boolean bossOk, boolean cerOk) {
            super(MenuType.GENERIC_9x1, id, inv, c, 1);
            this.ts = ts;
            this.origin = origin;
            this.bossOk = bossOk;
            this.cerOk = cerOk;
        }

        @Override
        public void clicked(int slot, int btn, ClickType ct, Player pl) {
            if (slot < 0 || slot >= 9 || !(pl instanceof ServerPlayer sp)) return;
            if (slot == 1 || slot == 3 || slot == 5) {
                int i = slot == 1 ? 0 : (slot == 3 ? 1 : 2);
                sp.closeContainer();
                start(sp, ts[i], origin);
            } else if (slot == 4 && cerOk) {
                sp.closeContainer();
                start(sp, 9, origin);
            } else if (slot == 7 && bossOk) {
                sp.closeContainer();
                start(sp, 8, origin);
            } else if (slot == 8) {
                M m = ACTIVE.get(sp.getUUID());
                sp.closeContainer();
                if (m != null) {
                    cleanup(sp.serverLevel(), m);
                    PoliciaMod.msg(sp, "Mision cancelada");
                }
            }
        }

        @Override
        public net.minecraft.world.item.ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }
    }

    static void openBoard(ServerPlayer sp, Pj com) {
        if (!"policia".equals(PoliciaMod.job(sp))) {
            sp.sendSystemMessage(Component.literal("[Comisario] Solo atiendo a policias. Vuelva con su placa."));
            return;
        }
        PoliciaBoard.open(sp, com);
        if (true) return;
        ServerLevel sl = sp.serverLevel();
        int[] ts = offered(com, sl);
        int done = sp.getPersistentData().getInt("pol_mdone");
        int bd = sp.getPersistentData().getInt("pol_bdone");
        boolean bossOk = done >= 3 * (bd + 1);
        boolean cerOk = bd >= 1 && !sp.getPersistentData().getBoolean("pol_cerebro");
        SimpleContainer c = new SimpleContainer(9);
        c.setItem(1, missionItem(ts[0]));
        c.setItem(3, missionItem(ts[1]));
        c.setItem(5, missionItem(ts[2]));
        if (bossOk) c.setItem(7, missionItem(8));
        else c.setItem(7, named(Items.BARRIER, "Cazar al jefe de banda (bloqueado)",
                "Cumpla " + (3 * (bd + 1) - done) + " misiones mas para desbloquearla."));
        if (cerOk) {
            c.setItem(4, missionItem(9));
        } else if (sp.getPersistentData().getBoolean("pol_cerebro")) {
            c.setItem(4, named(Items.BEACON, "El cerebro de la organizacion (completada)", "Ya tiene el Escudo de burbuja."));
        } else {
            c.setItem(4, named(Items.BARRIER, "Mision especial (bloqueada)", "Cace primero a un jefe de banda."));
        }
        c.setItem(0, named(Items.BOOK, "Misiones cumplidas: " + done, "Jefes cazados: " + bd, "El tablero se renueva cada dia."));
        c.setItem(8, named(Items.RED_DYE, "Cancelar mision actual", "Si tiene una mision en curso, la abandona."));
        BlockPos o = com.blockPosition();
        sp.openMenu(new SimpleMenuProvider((id, inv, pl) -> new Board(id, inv, c, ts, o, bossOk, cerOk), Component.literal("Tablero del comisario")));
    }

    // ---------------------------------------------------------------- comisarios y eventos

    public static class Bells extends SavedData {
        final Set<Long> set = new HashSet<>();

        static Bells get(ServerLevel sl) {
            return sl.getDataStorage().computeIfAbsent(Bells::load, Bells::new, "policia_bells");
        }

        static Bells load(CompoundTag t) {
            Bells b = new Bells();
            for (long l : t.getLongArray("b")) b.set.add(l);
            return b;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            long[] a = new long[set.size()];
            int i = 0;
            for (long l : set) a[i++] = l;
            t.putLongArray("b", a);
            return t;
        }
    }

    static void comisarios(ServerPlayer sp, ServerLevel sl) {
        Bells bells = Bells.get(sl);
        List<BlockPos> found = sl.getPoiManager().findAll(h -> h.is(PoiTypes.MEETING), bp -> true, sp.blockPosition(), 48,
                PoiManager.Occupancy.ANY).collect(java.util.stream.Collectors.toList());
        for (BlockPos bell : found) {
            if (bells.set.contains(bell.asLong())) continue;
            bells.set.add(bell.asLong());
            bells.setDirty();
            AABB box = new AABB(bell).inflate(64.0);
            boolean has = false;
            for (Pj p : sl.getEntitiesOfClass(Pj.class, box)) if (p.kind() == 10) has = true;
            if (has) continue;
            BlockPos spot = null;
            for (int i = 0; i < 20 && spot == null; i++) {
                BlockPos p = at(sl, bell.getX() + sl.random.nextInt(9) - 4, bell.getZ() + sl.random.nextInt(9) - 4);
                if (p != null && Math.abs(p.getY() - bell.getY()) <= 3) spot = p;
            }
            if (spot == null) spot = bell.above();
            Pj c = new Pj(COM.get(), sl);
            c.setKind(10);
            c.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, sl.random.nextFloat() * 360.0f, 0.0f);
            sl.addFreshEntity(c);
        }
    }

    public static class Ev {
        @SubscribeEvent
        public void mTick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv == null) return;
            for (M m : new ArrayList<>(ACTIVE.values())) {
                try {
                    tickM(srv, m);
                } catch (Exception ex) {
                    ACTIVE.remove(m.pl);
                }
            }
        }

        @SubscribeEvent
        public void mPtick(TickEvent.PlayerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp)) return;
            ServerLevel sl = sp.serverLevel();
            if (sp.tickCount % 200 == 7) {
                try {
                    comisarios(sp, sl);
                } catch (Exception ex) {
                    // sin comisario por ahora
                }
            }
            if (sp.tickCount % 1200 == 600 && "policia".equals(PoliciaMod.job(sp)) && sl.dimension() == Level.OVERWORLD
                    && sl.isNight() && !ACTIVE.containsKey(sp.getUUID()) && sl.random.nextInt(100) < 30) {
                for (Pj p : sl.getEntitiesOfClass(Pj.class, sp.getBoundingBox().inflate(80.0))) if (p.hostile()) return;
                BlockPos s = site(sl, sp.blockPosition(), 35, 50);
                if (s == null) return;
                for (int i = 0; i < 3; i++) {
                    Pj p = spawn(sl, null, 1, around(sl, s, 4));
                    p.life = 3600;
                }
                Pj a = spawn(sl, null, 2, around(sl, s, 4));
                a.life = 3600;
                PoliciaMod.msg(sp, "Una pandilla merodea cerca...");
            }
        }

        @SubscribeEvent
        public void mBrk(BlockEvent.BreakEvent e) {
            if (!(e.getPlayer() instanceof ServerPlayer sp) || !(e.getLevel() instanceof ServerLevel sl)) return;
            M m = ACTIVE.get(sp.getUUID());
            if (m == null || m.type != 7) return;
            BlockPos p = e.getPos();
            if (!m.bombs.contains(p)) return;
            e.setCanceled(true);
            sl.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            m.bombs.remove(p);
            sl.playSound(null, p, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 1.0f, 1.0f);
            PoliciaMod.msg(sp, "Bomba desactivada (" + (3 - m.bombs.size()) + "/3)");
            if (m.bombs.isEmpty()) finish(sp, sl, m, 1.0);
        }

        @SubscribeEvent
        public void mStop(ServerStoppingEvent e) {
            for (M m : new ArrayList<>(ACTIVE.values())) {
                ServerLevel sl = e.getServer().getLevel(m.dim);
                if (sl != null) cleanup(sl, m);
            }
            ACTIVE.clear();
        }
    }
}
