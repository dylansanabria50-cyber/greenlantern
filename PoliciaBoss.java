package com.example.policia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Jefes de oficio (jefe de policia, capataz, jefe de ladrones), perista, guardias, tiendas y objetos de un solo uso. */
public class PoliciaBoss {
    public static final RegistryObject<EntityType<BossNpc>> BOSS = PoliciaRefuerzo.ENTITIES.register("jefe_oficio",
            () -> EntityType.Builder.<BossNpc>of(BossNpc::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f).clientTrackingRange(10).build("policia:jefe_oficio"));

    public static final RegistryObject<Item> SKILL_ITEM = PoliciaShield.ITEMS.register("habilidad_unica",
            () -> new SingleUse(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> VEST = PoliciaShield.ITEMS.register("chaleco_antibalas",
            () -> new ArmorItem(VestMat.INSTANCE, ArmorItem.Type.CHESTPLATE, new Item.Properties()));

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "policia");

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("oficios",
            () -> CreativeModeTab.builder().title(Component.literal("Oficios"))
                    .icon(() -> new ItemStack(PoliciaPlaca.PLACA.get()))
                    .displayItems((params, out) -> {
                        out.accept(PoliciaPlaca.PLACA.get());
                        out.accept(PoliciaPlaca.BALDE.get());
                        out.accept(PoliciaPlaca.GUANTE.get());
                        out.accept(PoliciaShield.SHIELD.get());
                        out.accept(VEST.get());
                        String[][] sk = {{"refuerzo", "Refuerzo"}, {"k9", "Perro K9"}, {"esposas", "Esposas"}, {"sirena", "Sirena y torreta"},
                                {"dron", "Dron de vigilancia"}, {"burbuja", "Escudo de burbuja"}, {"pico", "Pico de albanil"},
                                {"muro", "Muro"}, {"refugio", "Refugio"}, {"silbido", "Silbido"}};
                        for (String[] x : sk) out.accept(single(x[0], x[1]));
                    }).build());

    // 0 jefe de policia, 1 capataz, 2 jefe de ladrones, 3 perista, 4 guardia ladron, 5 agente, 6 obrero
    static final String[] NAMES = {"Jefe de policia", "Capataz", "Jefe de ladrones", "Perista", "Ladron", "Agente", "Obrero"};
    static final String[] JOBS = {"policia", "albanil", "ladron"};
    static final String[] JOBNAME = {"policia", "albanil", "ladron"};

    static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaBoss::attrs);
        TABS.register(FMLJavaModLoadingContext.get().getModEventBus());
        MinecraftForge.EVENT_BUS.register(new Ev());
    }

    static void attrs(EntityAttributeCreationEvent e) {
        e.put(BOSS.get(), BossNpc.attrs().build());
    }

    // ------------------------------------------------------------------ material del chaleco

    static class VestMat implements ArmorMaterial {
        static final VestMat INSTANCE = new VestMat();

        @Override
        public int getDurabilityForType(ArmorItem.Type t) { return 70; }

        @Override
        public int getDefenseForType(ArmorItem.Type t) { return t == ArmorItem.Type.CHESTPLATE ? 6 : 0; }

        @Override
        public int getEnchantmentValue() { return 9; }

        @Override
        public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_IRON; }

        @Override
        public Ingredient getRepairIngredient() { return Ingredient.of(Items.IRON_INGOT); }

        @Override
        public String getName() { return "policia:chaleco"; }

        @Override
        public float getToughness() { return 1.0f; }

        @Override
        public float getKnockbackResistance() { return 0.0f; }
    }

    // ------------------------------------------------------------------ objeto de un solo uso

    public static class SingleUse extends Item {
        public SingleUse(Item.Properties p) { super(p); }

        @Override
        public InteractionResultHolder<ItemStack> use(Level lv, Player p, InteractionHand h) {
            ItemStack st = p.getItemInHand(h);
            if (!lv.isClientSide && p instanceof ServerPlayer sp) {
                String id = st.hasTag() ? st.getTag().getString("pol_skill") : "";
                if (run(sp, id) && !sp.getAbilities().instabuild) st.shrink(1);
            }
            return InteractionResultHolder.sidedSuccess(st, lv.isClientSide);
        }
    }

    static ItemStack single(String id, String name) {
        ItemStack s = new ItemStack(SKILL_ITEM.get());
        s.getOrCreateTag().putString("pol_skill", id);
        s.setHoverName(Component.literal(name + " (un solo uso)"));
        return s;
    }

    static boolean run(ServerPlayer p, String id) {
        CompoundTag d = p.getPersistentData();
        try {
            switch (id) {
                case "refuerzo": {
                    int old = d.getInt("pol_rl");
                    int oc = d.getInt("pol_rcd");
                    if (old <= 0) d.putInt("pol_rl", 1);
                    d.putInt("pol_rcd", 0);
                    PoliciaRefuerzo.use(p);
                    boolean ok = d.getInt("pol_rcd") > 0;
                    if (old <= 0) d.putInt("pol_rl", old);
                    if (!ok) d.putInt("pol_rcd", oc);
                    return ok;
                }
                case "k9": return PoliciaExtra.k9(p);
                case "esposas": return PoliciaExtra.cuff(p);
                case "sirena": return PoliciaExtra.siren(p);
                case "dron": return PoliciaExtra.drone(p);
                case "burbuja": return PoliciaBubble.use(p);
                case "silbido": PoliciaLadron.whistleUse(p); return true;
                case "muro": PoliciaAlbanil.buildWall(p); return true;
                case "refugio": PoliciaAlbanil.buildHouse(p); return true;
                case "pico": PoliciaAlbanil.startPick(p); return true;
                default:
                    PoliciaMod.msg(p, "Objeto sin efecto");
                    return false;
            }
        } catch (RuntimeException ex) {
            PoliciaMod.msg(p, "No se pudo usar ahora");
            return false;
        }
    }

    // ------------------------------------------------------------------ entidad

    public static class BossNpc extends PathfinderMob {
        static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(BossNpc.class, EntityDataSerializers.INT);
        static final EntityDataAccessor<Boolean> SIT = SynchedEntityData.defineId(BossNpc.class, EntityDataSerializers.BOOLEAN);

        public BossNpc(EntityType<? extends BossNpc> t, Level l) {
            super(t, l);
            this.setPersistenceRequired();
        }

        static AttributeSupplier.Builder attrs() {
            return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 30.0).add(Attributes.MOVEMENT_SPEED, 0.25)
                    .add(Attributes.ATTACK_DAMAGE, 5.0).add(Attributes.FOLLOW_RANGE, 24.0);
        }

        @Override
        protected void defineSynchedData() {
            super.defineSynchedData();
            this.entityData.define(KIND, 0);
            this.entityData.define(SIT, false);
        }

        public int kind() { return this.entityData.get(KIND); }

        public boolean sitting() { return this.entityData.get(SIT); }

        public void setSitting(boolean s) { this.entityData.set(SIT, s); }

        BlockPos home;
        int homeR;

        public void setHome(BlockPos p, int r) {
            this.home = p;
            this.homeR = r;
            this.restrictTo(p, r);
        }

        public void setKind(int k) {
            this.entityData.set(KIND, k);
            if (k < 0 || k >= NAMES.length) return;
            this.setCustomName(Component.literal(NAMES[k]));
            this.setCustomNameVisible(k <= 3);
            if (k <= 3) this.setNoAi(true);
            if (k == 4) {
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(34.0);
                this.getAttribute(Attributes.ARMOR).setBaseValue(4.0);
                this.setHealth(34.0f);
                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                this.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
            } else if (k == 5) {
                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                this.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
            } else if (k == 6) {
                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
                this.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
            }
            if (k == 0 || k == 2) setSitting(true);
        }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(0, new FloatGoal(this));
            this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, true) {
                @Override
                public boolean canUse() { return BossNpc.this.kind() == 4 && super.canUse(); }
            });
            this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
                @Override
                public boolean canUse() { return BossNpc.this.kind() >= 4 && super.canUse(); }
            });
            this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
            this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
            this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
                @Override
                public boolean canUse() { return BossNpc.this.kind() == 4 && super.canUse(); }
            });
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<Player>(this, Player.class, 10, true, false,
                    pl -> this.kind() == 4 && !"ladron".equals(PoliciaMod.job((Player) pl))));
        }

        @Override
        public boolean isInvulnerableTo(DamageSource s) {
            if ((kind() <= 3 || kind() == 5 || kind() == 6) && !s.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
            return super.isInvulnerableTo(s);
        }

        @Override
        public boolean isPushable() { return kind() >= 4 && super.isPushable(); }

        @Override
        public boolean removeWhenFarAway(double d) { return false; }

        @Override
        public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
            return NetworkHooks.getEntitySpawningPacket(this);
        }

        @Override
        public void addAdditionalSaveData(CompoundTag t) {
            super.addAdditionalSaveData(t);
            t.putInt("pk", kind());
            t.putBoolean("psit", sitting());
            if (home != null) {
                t.putLong("phome", home.asLong());
                t.putInt("phr", homeR);
            }
        }

        @Override
        public void readAdditionalSaveData(CompoundTag t) {
            super.readAdditionalSaveData(t);
            if (t.contains("pk")) {
                int k = t.getInt("pk");
                setKind(k);
                if (t.contains("psit")) setSitting(t.getBoolean("psit"));
                if (t.contains("phome")) setHome(BlockPos.of(t.getLong("phome")), t.getInt("phr"));
            }
        }

        @Override
        public InteractionResult mobInteract(Player p, InteractionHand h) {
            if (h != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            int k = kind();
            if (k > 3) return InteractionResult.PASS;
            if (p instanceof ServerPlayer sp) {
                if (k == 3) openSell(sp, this);
                else openMain(sp, this);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        /** Fabrica una silla (escalera de roble oscuro con apoyabrazos) bajo el jefe sentado. */
        void ensureChair() {
            BlockPos seat = this.blockPosition();
            net.minecraft.world.level.block.state.BlockState st = this.level().getBlockState(seat);
            if (st.getBlock() instanceof net.minecraft.world.level.block.StairBlock) return;
            net.minecraft.core.Direction d = net.minecraft.core.Direction.fromYRot(this.getYRot());
            float yy = d.toYRot();
            this.setYRot(yy);
            this.setYBodyRot(yy);
            this.setYHeadRot(yy);
            this.setPos(this.getX() + d.getStepX() * 0.15, this.getY(), this.getZ() + d.getStepZ() * 0.15);
            this.level().setBlock(seat, net.minecraft.world.level.block.Blocks.DARK_OAK_STAIRS.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.StairBlock.FACING, d.getOpposite()), 3);
            for (net.minecraft.core.Direction s : new net.minecraft.core.Direction[]{d.getClockWise(), d.getCounterClockWise()}) {
                BlockPos ap = seat.relative(s);
                if (this.level().getBlockState(ap).isAir()) {
                    this.level().setBlock(ap, net.minecraft.world.level.block.Blocks.DARK_OAK_SLAB.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP), 3);
                }
            }
        }

        @Override
        public void tick() {
            super.tick();
            if (this.level().isClientSide) return;
            int k = kind();
            if ((k == 0 || k == 2) && tickCount % 20 == 5) ensureChair();
            if ((k == 0 || k == 2) && tickCount % 10 == 0) {
                String key = k == 0 ? "pol_cin0" : "pol_cin2";
                for (ServerPlayer sp : this.level().getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(7.0, 3.0, 7.0))) {
                    if (sp.getPersistentData().getBoolean(key) || CIN.containsKey(sp.getUUID())) continue;
                    sp.getPersistentData().putBoolean(key, true);
                    startCin(sp, this);
                }
            }
        }
    }

    // ------------------------------------------------------------------ cinematica

    static class Cin {
        UUID boss;
        int t;
        double x, y, z;
        float y0, p0;
        int kind;
    }

    static final Map<UUID, Cin> CIN = new HashMap<>();

    static void startCin(ServerPlayer p, BossNpc b) {
        Cin c = new Cin();
        c.boss = b.getUUID();
        c.x = p.getX();
        c.y = p.getY();
        c.z = p.getZ();
        c.y0 = p.getYRot();
        c.p0 = p.getXRot();
        c.kind = b.kind();
        CIN.put(p.getUUID(), c);
    }

    static void stepCin(MinecraftServer srv) {
        for (Map.Entry<UUID, Cin> en : new ArrayList<>(CIN.entrySet())) {
            Cin c = en.getValue();
            ServerPlayer p = srv.getPlayerList().getPlayer(en.getKey());
            Entity e = p == null ? null : p.serverLevel().getEntity(c.boss);
            if (p == null || e == null || !e.isAlive() || ++c.t > 75) {
                CIN.remove(en.getKey());
                continue;
            }
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            double dy = (e.getY() + 1.4) - (c.y + p.getEyeHeight());
            float ty = (float) (Mth.atan2(-dx, dz) * 57.29577951308232);
            float tp = (float) (-(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 57.29577951308232));
            float k = Math.min(1.0f, c.t / 20.0f);
            float sm = k * k * (3.0f - 2.0f * k);
            float yaw = c.y0 + Mth.wrapDegrees(ty - c.y0) * sm;
            float pit = c.p0 + (tp - c.p0) * sm;
            p.setDeltaMovement(0.0, 0.0, 0.0);
            p.connection.teleport(c.x, c.y, c.z, yaw, pit);
            if (c.t == 22) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(8, 40, 12));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(c.kind == 0 ? "Novato, que haces aqui?" : "Que haces aqui?")));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(c.kind == 0 ? "Jefe de policia" : "Jefe de ladrones")));
                p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), c.kind == 0 ? SoundEvents.VILLAGER_NO : SoundEvents.WITHER_AMBIENT,
                        SoundSource.NEUTRAL, 0.6f, 0.7f);
            }
        }
    }

    // ------------------------------------------------------------------ menus

    static ItemStack named(Item it, String name, String... lore) {
        ItemStack s = new ItemStack(it);
        s.setHoverName(Component.literal(name));
        if (lore.length > 0) {
            ListTag l = new ListTag();
            for (String x : lore) l.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(x))));
            s.getOrCreateTagElement("display").put("Lore", l);
        }
        return s;
    }

    static ItemStack pane() { return named(Items.GRAY_STAINED_GLASS_PANE, " "); }

    static void give(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    static void later(ServerPlayer p, Runnable r) {
        p.getServer().execute(r);
    }

    /** Menu de 3 filas cuyos objetos son botones. */
    static class Menu3 extends ChestMenu {
        final ServerPlayer sp;
        final java.util.function.BiConsumer<ServerPlayer, Integer> on;

        Menu3(int id, Inventory inv, SimpleContainer c, ServerPlayer sp, java.util.function.BiConsumer<ServerPlayer, Integer> on) {
            super(MenuType.GENERIC_9x3, id, inv, c, 3);
            this.sp = sp;
            this.on = on;
        }

        @Override
        public void clicked(int slot, int btn, ClickType t, Player p) {
            if (slot >= 0 && slot < 27) {
                if (t == ClickType.PICKUP) on.accept(sp, slot);
                return;
            }
            if (t == ClickType.QUICK_MOVE || t == ClickType.PICKUP_ALL || t == ClickType.QUICK_CRAFT) return;
            super.clicked(slot, btn, t, p);
        }

        @Override
        public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }
    }

    static void openMenu3(ServerPlayer sp, String title, SimpleContainer c, java.util.function.BiConsumer<ServerPlayer, Integer> on) {
        sp.openMenu(new SimpleMenuProvider((id, inv, pl) -> new Menu3(id, inv, c, sp, on), Component.literal(title)));
    }

    static void openMain(ServerPlayer sp, BossNpc b) {
        int k = b.kind();
        boolean mine = JOBS[k].equals(PoliciaMod.job(sp));
        SimpleContainer c = new SimpleContainer(27);
        for (int i = 0; i < 27; i++) c.setItem(i, pane());
        c.setItem(11, named(Items.BOOK, "Hablar", "Escuchar lo que tiene para decir"));
        c.setItem(13, mine ? named(Items.EMERALD, "Comprar", "Tienda de " + JOBNAME[k] + " (esmeraldas)")
                : named(Items.BARRIER, "Comprar", "Solo le vendo a mi gente (oficio: " + JOBNAME[k] + ")"));
        c.setItem(15, mine ? named(Items.REDSTONE, "Dejar el oficio", "Pedir que te despidan")
                : named(Items.BARRIER, "Dejar el oficio", "No eres de mi oficio"));
        openMenu3(sp, NAMES[k], c, (p, slot) -> {
            if (slot == 11) {
                p.closeContainer();
                talk(p, b);
            } else if (slot == 13) {
                if (!mine) { PoliciaMod.msg(p, "Solo le vendo a quienes son " + JOBNAME[k]); return; }
                later(p, () -> openShop(p, k));
            } else if (slot == 15) {
                if (!mine) { PoliciaMod.msg(p, "No eres de mi oficio"); return; }
                later(p, () -> openFire(p, k));
            }
        });
    }

    static final String[][] LINES = {
            {"Mantener el orden no es un juego, novato.", "Los bandidos acampan lejos de las aldeas. Hablen con el comisario para las misiones.",
                    "Un buen oficial cuida a la gente y cumple la ley.", "El chaleco antibalas te salva de las flechas... pero se gasta rapido."},
            {"Una obra bien hecha dura toda la vida.", "Sin ayudantes no hay muro que se termine.",
                    "Cuida el pico: lo que se rompe en la obra lo pagas tu.", "Muros, refugios y tractores: todo se construye."},
            {"En este negocio se habla poco y se roba mucho.", "El perista compra todo lo que traigas, sin preguntas.",
                    "Silba, acercate por la espalda y no mires atras.", "La suerte favorece a los que practican."}};

    static void talk(ServerPlayer p, BossNpc b) {
        int k = b.kind();
        String[] ls = LINES[k];
        p.sendSystemMessage(Component.literal("<" + NAMES[k] + "> " + ls[p.getRandom().nextInt(ls.length)]));
    }

    static void openFire(ServerPlayer sp, int k) {
        SimpleContainer c = new SimpleContainer(27);
        for (int i = 0; i < 27; i++) c.setItem(i, pane());
        c.setItem(13, named(Items.PAPER, "Esta despedido!", "Quiere dejar el oficio de " + JOBNAME[k] + "?", "Perdera el modo y el libro; el progreso se conserva."));
        c.setItem(11, named(Items.LIME_CONCRETE, "SI, quiero ser despedido"));
        c.setItem(15, named(Items.RED_CONCRETE, "NO, quiero seguir"));
        openMenu3(sp, NAMES[k], c, (p, slot) -> {
            if (slot == 11) {
                p.closeContainer();
                fire(p, k);
            } else if (slot == 15) {
                p.closeContainer();
                PoliciaMod.msg(p, "Sigues en tu oficio");
            }
        });
    }

    static void fire(ServerPlayer p, int k) {
        if (!JOBS[k].equals(PoliciaMod.job(p))) return;
        if (PoliciaMod.on(p)) PoliciaMod.act(p, 0);
        p.getPersistentData().putString("pol_job", "");
        PoliciaMod.msg(p, "Estas despedido del oficio. Busca otro (placa, balde o guante)");
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_DESTROY, SoundSource.PLAYERS, 0.6f, 1.2f);
        PoliciaMod.sync(p);
    }

    // ------------------------------------------------------------------ tienda

    static class Entry {
        final ItemStack give;
        final int price;

        Entry(ItemStack give, int price) {
            this.give = give;
            this.price = price;
        }
    }

    static ItemStack ench(ItemStack s, net.minecraft.world.item.enchantment.Enchantment e, int lv) {
        s.enchant(e, lv);
        return s;
    }

    static List<Entry> catalog(int k) {
        List<Entry> l = new ArrayList<>();
        if (k == 0) {
            l.add(new Entry(new ItemStack(Items.LEATHER_HELMET), 3));
            l.add(new Entry(new ItemStack(Items.LEATHER_CHESTPLATE), 5));
            l.add(new Entry(new ItemStack(Items.LEATHER_LEGGINGS), 4));
            l.add(new Entry(new ItemStack(Items.LEATHER_BOOTS), 3));
            l.add(new Entry(new ItemStack(Items.DIAMOND_HELMET), 14));
            l.add(new Entry(new ItemStack(Items.DIAMOND_CHESTPLATE), 24));
            l.add(new Entry(new ItemStack(Items.DIAMOND_LEGGINGS), 20));
            l.add(new Entry(new ItemStack(Items.DIAMOND_BOOTS), 12));
            ItemStack v = new ItemStack(VEST.get());
            v.setHoverName(Component.literal("Chaleco antibalas"));
            l.add(new Entry(v, 18));
            l.add(new Entry(single("refuerzo", "Refuerzo"), 12));
            l.add(new Entry(single("k9", "Perro K9"), 10));
            l.add(new Entry(single("esposas", "Esposas"), 6));
            l.add(new Entry(single("sirena", "Sirena y torreta"), 10));
            l.add(new Entry(single("dron", "Dron de vigilancia"), 12));
            l.add(new Entry(single("burbuja", "Escudo de burbuja"), 20));
        } else if (k == 1) {
            l.add(new Entry(named(Items.IRON_HELMET, "Casco de obra"), 5));
            l.add(new Entry(new ItemStack(Items.IRON_PICKAXE), 5));
            l.add(new Entry(new ItemStack(Items.DIAMOND_PICKAXE), 18));
            l.add(new Entry(new ItemStack(Items.BRICKS, 32), 6));
            l.add(new Entry(new ItemStack(Items.SCAFFOLDING, 16), 4));
            l.add(new Entry(new ItemStack(Items.OAK_PLANKS, 64), 4));
            l.add(new Entry(new ItemStack(Items.LANTERN, 8), 5));
            l.add(new Entry(new ItemStack(Items.IRON_SHOVEL), 3));
            l.add(new Entry(single("pico", "Pico de albanil"), 8));
            l.add(new Entry(single("muro", "Muro"), 6));
            l.add(new Entry(single("refugio", "Refugio"), 14));
        } else {
            l.add(new Entry(ench(named(Items.LEATHER_BOOTS, "Botas de sigilo"), Enchantments.FALL_PROTECTION, 2), 8));
            l.add(new Entry(new ItemStack(Items.ENDER_PEARL, 2), 6));
            l.add(new Entry(PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.INVISIBILITY), 10));
            l.add(new Entry(new ItemStack(Items.LEAD, 2), 2));
            l.add(new Entry(new ItemStack(Items.SPYGLASS), 6));
            l.add(new Entry(new ItemStack(Items.COOKED_BEEF, 8), 3));
            l.add(new Entry(single("silbido", "Silbido"), 8));
        }
        return l;
    }

    static int emeralds(Player p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(Items.EMERALD)) n += s.getCount();
        return n;
    }

    static void takeEmeralds(Player p, int n) {
        for (ItemStack s : p.getInventory().items) {
            if (n <= 0) return;
            if (s.is(Items.EMERALD)) {
                int t = Math.min(n, s.getCount());
                s.shrink(t);
                n -= t;
            }
        }
    }

    static class Shop extends ChestMenu {
        final ServerPlayer sp;
        final List<Entry> list;

        Shop(int id, Inventory inv, SimpleContainer c, ServerPlayer sp, List<Entry> list) {
            super(MenuType.GENERIC_9x6, id, inv, c, 6);
            this.sp = sp;
            this.list = list;
        }

        @Override
        public void clicked(int slot, int btn, ClickType t, Player p) {
            if (slot >= 0 && slot < 54) {
                if (t == ClickType.PICKUP && slot < list.size()) buy(list.get(slot));
                return;
            }
            if (t == ClickType.QUICK_MOVE || t == ClickType.PICKUP_ALL || t == ClickType.QUICK_CRAFT) return;
            super.clicked(slot, btn, t, p);
        }

        void buy(Entry e) {
            if (emeralds(sp) < e.price) {
                PoliciaMod.msg(sp, "Te faltan esmeraldas: cuesta " + e.price + " (tienes " + emeralds(sp) + ")");
                return;
            }
            takeEmeralds(sp, e.price);
            give(sp, e.give.copy());
            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 1.0f, 1.0f);
            PoliciaMod.msg(sp, "Comprado por " + e.price + " esmeraldas");
        }

        @Override
        public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }
    }

    static void openShop(ServerPlayer sp, int k) {
        List<Entry> l = catalog(k);
        SimpleContainer c = new SimpleContainer(54);
        for (int i = 0; i < l.size() && i < 54; i++) {
            Entry e = l.get(i);
            ItemStack d = e.give.copy();
            ListTag lore = new ListTag();
            lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Precio: " + e.price + " esmeraldas"))));
            d.getOrCreateTagElement("display").put("Lore", lore);
            c.setItem(i, d);
        }
        sp.openMenu(new SimpleMenuProvider((id, inv, pl) -> new Shop(id, inv, c, sp, l), Component.literal("Tienda - " + NAMES[k])));
    }

    // ------------------------------------------------------------------ perista (compra lo robado)

    static double value(ItemStack s) {
        Item it = s.getItem();
        if (it == Items.NETHERITE_INGOT) return 30.0;
        if (it == Items.DIAMOND) return 6.0;
        if (it == Items.EMERALD) return 1.0;
        if (it == Items.GOLD_INGOT) return 2.0;
        if (it == Items.IRON_INGOT) return 1.0;
        if (it == Items.GOLD_NUGGET) return 0.25;
        if (it == Items.IRON_NUGGET) return 0.1;
        if (it == Items.ENDER_PEARL) return 3.0;
        if (it == Items.BLAZE_ROD) return 2.0;
        if (it == Items.NETHER_STAR) return 60.0;
        switch (s.getRarity()) {
            case UNCOMMON: return 2.0;
            case RARE: return 5.0;
            case EPIC: return 12.0;
            default: return 0.3;
        }
    }

    static class Sell extends ChestMenu {
        final ServerPlayer sp;
        final SimpleContainer box;

        Sell(int id, Inventory inv, SimpleContainer c, ServerPlayer sp) {
            super(MenuType.GENERIC_9x3, id, inv, c, 3);
            this.sp = sp;
            this.box = c;
        }

        @Override
        public void clicked(int slot, int btn, ClickType t, Player p) {
            if (slot >= 18 && slot < 27) {
                if (slot == 22 && t == ClickType.PICKUP) sell();
                return;
            }
            if (t == ClickType.PICKUP_ALL || t == ClickType.QUICK_CRAFT) return;
            super.clicked(slot, btn, t, p);
        }

        void sell() {
            double total = 0.0;
            for (int i = 0; i < 18; i++) {
                ItemStack s = box.getItem(i);
                if (!s.isEmpty()) total += value(s) * s.getCount();
            }
            int em = (int) Math.floor(total);
            if (em < 1) {
                PoliciaMod.msg(sp, "Eso no vale nada");
                return;
            }
            for (int i = 0; i < 18; i++) box.setItem(i, ItemStack.EMPTY);
            int left = em;
            while (left > 0) {
                int n = Math.min(64, left);
                give(sp, new ItemStack(Items.EMERALD, n));
                left -= n;
            }
            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.VILLAGER_TRADE, SoundSource.PLAYERS, 1.0f, 1.0f);
            PoliciaMod.msg(sp, "Vendido por " + em + " esmeraldas");
            sp.getPersistentData().putInt("pol_xp", PoliciaMod.xp(sp) + Math.min(5, 1 + em / 10));
            PoliciaMod.sync(sp);
        }

        @Override
        public ItemStack quickMoveStack(Player p, int idx) {
            Slot s = this.slots.get(idx);
            if (s == null || !s.hasItem()) return ItemStack.EMPTY;
            ItemStack st = s.getItem();
            ItemStack copy = st.copy();
            if (idx < 27) {
                if (!this.moveItemStackTo(st, 27, this.slots.size(), true)) return ItemStack.EMPTY;
            } else if (!this.moveItemStackTo(st, 0, 18, false)) {
                return ItemStack.EMPTY;
            }
            if (st.isEmpty()) s.set(ItemStack.EMPTY);
            else s.setChanged();
            return copy;
        }

        @Override
        public void removed(Player p) {
            for (int i = 0; i < 18; i++) {
                ItemStack s = box.getItem(i);
                if (!s.isEmpty() && p instanceof ServerPlayer sp2) give(sp2, s);
                box.setItem(i, ItemStack.EMPTY);
            }
            super.removed(p);
        }
    }

    static void openSell(ServerPlayer sp, BossNpc b) {
        if (!"ladron".equals(PoliciaMod.job(sp))) {
            sp.sendSystemMessage(Component.literal("<Perista> Yo solo trato con ladrones."));
            return;
        }
        SimpleContainer c = new SimpleContainer(27);
        for (int i = 18; i < 27; i++) c.setItem(i, pane());
        c.setItem(22, named(Items.EMERALD, "VENDER", "Pon lo robado arriba y pulsa aqui", "Te pago en esmeraldas"));
        sp.openMenu(new SimpleMenuProvider((id, inv, pl) -> new Sell(id, inv, c, sp), Component.literal("Perista")));
    }

    // ------------------------------------------------------------------ eventos

    public static class Ev {
        @SubscribeEvent
        public void bsTick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || CIN.isEmpty()) return;
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv != null) stepCin(srv);
        }

        @SubscribeEvent
        public void bsHurt(LivingHurtEvent e) {
            if (!(e.getEntity() instanceof ServerPlayer p)) return;
            ItemStack ch = p.getItemBySlot(EquipmentSlot.CHEST);
            if (ch.isEmpty() || ch.getItem() != VEST.get()) return;
            if (e.getSource().is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) {
                e.setAmount(e.getAmount() * 0.3f);
                ch.hurtAndBreak(6, p, x -> x.broadcastBreakEvent(EquipmentSlot.CHEST));
            }
        }
    }
}
