package com.example.glsolo;

import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Mod filtro: oculta de la pestana creativa lo que no es de Linterna Verde. */
@Mod("glsolo")
public class GlSolo {
    /** Vida maxima con el traje (40 = 20 corazones) y armadura maxima (20 = barra vanilla llena). */
    static final double MAX_HP = 20.0D;
    static final double MAX_ARMOR = 8.0D;
    static final double TOUGH = 2.0D;
    static final UUID ID_TG = UUID.fromString("2d4f6a8c-1b3e-4d5f-8a7c-9e0b1c2d3e4f");
    static final UUID ID_HP = UUID.fromString("5b1c2f0e-8a31-4c55-9e0a-1d2f3a4b5c6d");
    static final UUID ID_AR = UUID.fromString("7e9d4a21-3b6c-4f08-a1d5-2c8e9f0b1a3c");

    public GlSolo() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(EventPriority.LOWEST, HideOthers::onTab);
        GlTweaks.init();
        MinecraftForge.EVENT_BUS.addListener(GlSolo::onTick);
        if (FMLEnvironment.dist.isClient()) Cl.init();
        Ring.init();
        Mv.init();
    }

    static java.lang.reflect.Method SUIT_GET;
    static boolean SUIT_INIT;

    public static boolean isGlSuit(LivingEntity p) {
        try {
            if (!SUIT_INIT) {
                SUIT_INIT = true;
                Class<?> c = Class.forName("com.tihyo.legends.armors.LegendsSuit");
                SUIT_GET = c.getMethod("getSuit", LivingEntity.class);
            }
            if (SUIT_GET == null) return false;
            Object o = SUIT_GET.invoke(null, p);
            return o != null && o.getClass().getName().contains(".greenlantern.");
        } catch (Throwable t) {
            return false;
        }
    }

    static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.level().isClientSide) return;
        Player p = e.player;
        boolean gl = isGlSuit(p);
        cap(p, Attributes.MAX_HEALTH, ID_HP, MAX_HP, gl);
        cap(p, Attributes.ARMOR, ID_AR, MAX_ARMOR, gl);
        cap(p, Attributes.ARMOR_TOUGHNESS, ID_TG, TOUGH, gl);
        if (gl && p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    static void cap(Player p, Attribute at, UUID id, double max, boolean gl) {
        AttributeInstance in = p.getAttribute(at);
        if (in == null) return;
        AttributeModifier old = in.getModifier(id);
        double desired = 0.0D;
        if (gl) {
            double v = in.getValue() - (old != null ? old.getAmount() : 0.0D);
            if (v != max) desired = max - v;
        }
        if (old == null && desired == 0.0D) return;
        if (old != null && Math.abs(old.getAmount() - desired) < 1.0E-6D) return;
        if (old != null) in.removeModifier(id);
        if (desired != 0.0D) in.addTransientModifier(new AttributeModifier(id, "glsolo_cap", desired, AttributeModifier.Operation.ADDITION));
    }

    /** Anillo de la voluntad de Legends: ranura de equipo extra + traje automatico. */
    static class Ring {
        static final net.minecraft.resources.ResourceLocation ID = new net.minecraft.resources.ResourceLocation("glsolo", "willpower_ring");
        static final String EQ = "glsolo_ring_eq";
        static final String HAD = "glsolo_ring_had";
        static final String AUTO = "glsolo_auto";
        static final String PV = "1";
        static net.minecraftforge.network.simple.SimpleChannel CH;
        static volatile boolean CLIENT_EQ;
        static java.util.Map<net.minecraft.world.entity.EquipmentSlot, net.minecraft.world.item.Item> PIECES;

        static void init() {
            CH = net.minecraftforge.network.NetworkRegistry.newSimpleChannel(
                    new net.minecraft.resources.ResourceLocation("glsolo", "main"), () -> PV, PV::equals, PV::equals);
            CH.registerMessage(0, Msg.class, Msg::enc, Msg::dec, Msg::handle);
            FMLJavaModLoadingContext.get().getModEventBus().addListener(
                    (net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(Ring::register));
            MinecraftForge.EVENT_BUS.addListener(Ring::onTick);
            MinecraftForge.EVENT_BUS.addListener(Ring::onClone);
            MinecraftForge.EVENT_BUS.addListener(Ring::onLogin);
            MinecraftForge.EVENT_BUS.addListener(Ring::onRespawn);
            MinecraftForge.EVENT_BUS.addListener(Ring::onDim);
        }

        static net.minecraft.world.item.Item ringItem() {
            return net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("legends_superheroes", "willpower_ring"));
        }

        static boolean isEquipped(Player p) {
            return p.level().isClientSide ? CLIENT_EQ : p.getPersistentData().getBoolean(EQ);
        }

        static void setEquipped(Player p, boolean v) {
            if (v) p.getPersistentData().putBoolean(EQ, true); else p.getPersistentData().remove(EQ);
            if (p instanceof net.minecraft.server.level.ServerPlayer sp) sync(sp);
        }

        static void sync(net.minecraft.server.level.ServerPlayer sp) {
            CH.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp), new Msg(sp.getPersistentData().getBoolean(EQ)));
        }

        static void register() {
            try {
                Class<?> defC = Class.forName("com.tihyo.legends.equipment.ExtraEquipmentDefinition");
                Class<?> regionC = Class.forName("com.tihyo.legends.equipment.ExtraEquipmentRegion");
                Class<?> regC = Class.forName("com.tihyo.legends.equipment.ExtraEquipmentRegistry");
                Object region = regionC.getField("RIGHT_ARM").get(null);
                java.util.function.Supplier<net.minecraft.world.item.Item> item = Ring::ringItem;
                java.util.function.Predicate<Player> isEq = Ring::isEquipped;
                java.util.function.Predicate<Player> can = p -> true;
                java.util.function.Consumer<Player> eq = p -> setEquipped(p, true);
                java.util.function.Consumer<Player> un = p -> setEquipped(p, false);
                Object def = defC.getConstructors()[0].newInstance(ID, item, region, isEq, can, eq, un);
                regC.getMethod("register", defC).invoke(null, def);
            } catch (Throwable t) {
                System.err.println("[glsolo] no se pudo registrar la ranura del anillo: " + t);
            }
        }

        static java.util.Map<net.minecraft.world.entity.EquipmentSlot, net.minecraft.world.item.Item> pieces() {
            if (PIECES != null && !PIECES.isEmpty()) return PIECES;
            java.util.Map<net.minecraft.world.entity.EquipmentSlot, net.minecraft.world.item.Item> m = new java.util.EnumMap<>(net.minecraft.world.entity.EquipmentSlot.class);
            try {
                Class<?> ai = Class.forName("com.tihyo.legends.armors.LegendsArmorItem");
                java.lang.reflect.Method gs = ai.getMethod("getSuit");
                for (net.minecraft.world.item.Item it : net.minecraftforge.registries.ForgeRegistries.ITEMS) {
                    if (!ai.isInstance(it)) continue;
                    Object s = gs.invoke(it);
                    if (s == null || !s.getClass().getName().contains(".greenlantern.")) continue;
                    net.minecraft.world.entity.EquipmentSlot sl = ((net.minecraft.world.item.ArmorItem) it).getEquipmentSlot();
                    net.minecraft.world.item.Item old = m.get(sl);
                    if (old == null || path(it).length() < path(old).length()) m.put(sl, it);
                }
            } catch (Throwable t) {
                System.err.println("[glsolo] no se pudo buscar el traje: " + t);
            }
            PIECES = m;
            return m;
        }

        static String path(net.minecraft.world.item.Item it) {
            net.minecraft.resources.ResourceLocation k = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(it);
            return k == null ? "" : k.getPath();
        }

        static void onTick(TickEvent.PlayerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !(e.player instanceof net.minecraft.server.level.ServerPlayer p)) return;
            if (p.tickCount % 10 != 0) return;
            net.minecraft.world.item.Item ring = ringItem();
            if (ring == null || ring == net.minecraft.world.item.Items.AIR) return;
            boolean has = isEquipped(p) || p.getInventory().countItem(ring) > 0;
            boolean had = p.getPersistentData().getBoolean(HAD);
            if (has && !had) {
                p.getPersistentData().putBoolean(HAD, true);
                giveSuit(p);
            } else if (!has && had) {
                p.getPersistentData().remove(HAD);
                removeSuit(p);
            }
        }

        static void giveSuit(net.minecraft.server.level.ServerPlayer p) {
            for (java.util.Map.Entry<net.minecraft.world.entity.EquipmentSlot, net.minecraft.world.item.Item> en : pieces().entrySet()) {
                net.minecraft.world.entity.EquipmentSlot sl = en.getKey();
                net.minecraft.world.item.Item piece = en.getValue();
                net.minecraft.world.item.ItemStack cur = p.getItemBySlot(sl);
                if (cur.getItem() == piece) continue;
                net.minecraft.world.item.ItemStack mine = net.minecraft.world.item.ItemStack.EMPTY;
                for (int i = 0; i < p.getInventory().items.size(); i++) {
                    net.minecraft.world.item.ItemStack s = p.getInventory().items.get(i);
                    if (s.getItem() == piece) {
                        mine = s.copy();
                        p.getInventory().items.set(i, net.minecraft.world.item.ItemStack.EMPTY);
                        break;
                    }
                }
                if (mine.isEmpty()) {
                    mine = new net.minecraft.world.item.ItemStack(piece);
                    mine.getOrCreateTag().putBoolean(AUTO, true);
                }
                if (!cur.isEmpty()) {
                    net.minecraft.world.item.ItemStack old = cur.copy();
                    p.setItemSlot(sl, net.minecraft.world.item.ItemStack.EMPTY);
                    if (!p.getInventory().add(old)) p.drop(old, false);
                }
                p.setItemSlot(sl, mine);
            }
        }

        static void removeSuit(net.minecraft.server.level.ServerPlayer p) {
            for (net.minecraft.world.entity.EquipmentSlot sl : new net.minecraft.world.entity.EquipmentSlot[] {
                    net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                    net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
                net.minecraft.world.item.ItemStack s = p.getItemBySlot(sl);
                if (!s.isEmpty() && s.hasTag() && s.getTag().getBoolean(AUTO)) p.setItemSlot(sl, net.minecraft.world.item.ItemStack.EMPTY);
            }
            for (int i = 0; i < p.getInventory().items.size(); i++) {
                net.minecraft.world.item.ItemStack s = p.getInventory().items.get(i);
                if (!s.isEmpty() && s.hasTag() && s.getTag().getBoolean(AUTO)) p.getInventory().items.set(i, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }

        static void onClone(net.minecraftforge.event.entity.player.PlayerEvent.Clone e) {
            net.minecraft.nbt.CompoundTag o = e.getOriginal().getPersistentData();
            if (o.getBoolean(EQ)) e.getEntity().getPersistentData().putBoolean(EQ, true);
        }

        static void onLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) {
            if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) sync(sp);
        }

        static void onRespawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent e) {
            if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) sync(sp);
        }

        static void onDim(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent e) {
            if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) sync(sp);
        }

        static class Msg {
            final boolean v;
            Msg(boolean v) { this.v = v; }
            static void enc(Msg m, net.minecraft.network.FriendlyByteBuf b) { b.writeBoolean(m.v); }
            static Msg dec(net.minecraft.network.FriendlyByteBuf b) { return new Msg(b.readBoolean()); }
            static void handle(Msg m, java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> c) {
                c.get().enqueueWork(() -> CLIENT_EQ = m.v);
                c.get().setPacketHandled(true);
            }
        }
    }

    /** Velocidad al correr vanilla, vuelo -25 %, turbina -60 %, jets solo atacan lo que atacas. */
    static class Mv {
        static final double FLIGHT = 0.75D;
        static final double TURBINE = 0.4D;
        static final java.util.Map<Player, St> STATE = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
        static final java.util.List<UUID> SPEED_IDS = new java.util.ArrayList<>();
        static boolean IDS_INIT;
        static Object FLIGHT_AB, TURBINE_AB;
        static boolean AB_INIT;

        static class St {
            boolean flightScaled;
            net.minecraft.world.phys.Vec3 before = net.minecraft.world.phys.Vec3.ZERO;
        }

        static void init() {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, Mv::pre);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, Mv::post);
            MinecraftForge.EVENT_BUS.addListener(Mv::onTarget);
        }

        static Object field(String cls, String name) {
            try {
                Object ro = Class.forName(cls).getField(name).get(null);
                return ((net.minecraftforge.registries.RegistryObject<?>) ro).get();
            } catch (Throwable t) {
                return null;
            }
        }

        static void abilities() {
            if (AB_INIT) return;
            AB_INIT = true;
            FLIGHT_AB = field("com.tihyo.legends.abilities.LegendsAbilities", "FLIGHT");
            TURBINE_AB = field("com.tihyo.legends.superheroes.abilities.SuperHeroesAbilities", "TURBINE_SMASH");
        }

        static Object call(Object o, String name, Object arg) {
            try {
                Class<?> c = o.getClass();
                while (c != null) {
                    for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                        if (m.getName().equals(name) && m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(arg.getClass())) {
                            m.setAccessible(true);
                            return m.invoke(o, arg);
                        }
                    }
                    c = c.getSuperclass();
                }
            } catch (Throwable t) {
                // ignorar
            }
            return null;
        }

        static boolean tracked(Player p) {
            return !p.level().isClientSide || p.isLocalPlayer();
        }

        static void pre(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent e) {
            if (!(e.getEntity() instanceof Player p) || !tracked(p)) return;
            St s = STATE.computeIfAbsent(p, k -> new St());
            if (s.flightScaled) p.setDeltaMovement(p.getDeltaMovement().scale(1.0D / FLIGHT));
            s.before = p.getDeltaMovement();
        }

        static void post(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent e) {
            net.minecraft.world.entity.LivingEntity le = e.getEntity();
            if (le instanceof net.minecraft.world.entity.TamableAnimal t && le.getClass().getName().endsWith("JetSquadronEntity")) {
                jetTick(t);
                return;
            }
            if (!(le instanceof Player p) || !tracked(p)) return;
            St s = STATE.computeIfAbsent(p, k -> new St());
            boolean gl = isGlSuit(p);
            if (gl) stripSpeed(p);
            abilities();
            if (gl && TURBINE_AB != null && Boolean.TRUE.equals(call(TURBINE_AB, "isActive", p))) {
                net.minecraft.world.phys.Vec3 d = p.getDeltaMovement().subtract(s.before);
                p.setDeltaMovement(s.before.add(d.scale(TURBINE)));
            }
            boolean flying = gl && FLIGHT_AB != null && Boolean.TRUE.equals(call(FLIGHT_AB, "canFly", p)) && !p.onGround();
            if (flying) {
                p.setDeltaMovement(p.getDeltaMovement().scale(FLIGHT));
                s.flightScaled = true;
            } else {
                s.flightScaled = false;
            }
        }

        static void stripSpeed(Player p) {
            net.minecraft.world.entity.ai.attributes.AttributeInstance in = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (in == null) return;
            if (!IDS_INIT) {
                IDS_INIT = true;
                for (String n : new String[] {"SPEED", "ADJUSTABLE_SPEED"}) {
                    Object ef = field("com.tihyo.legends.abilities.LegendsAbilities", n);
                    if (ef instanceof net.minecraft.world.effect.MobEffect me) {
                        for (java.util.Map.Entry<Attribute, net.minecraft.world.entity.ai.attributes.AttributeModifierTemplate> en : me.getAttributeModifiers().entrySet()) {
                            if (en.getKey() == Attributes.MOVEMENT_SPEED) SPEED_IDS.add(en.getValue().getAttributeModifierId());
                        }
                    }
                }
            }
            for (UUID id : SPEED_IDS) {
                if (in.getModifier(id) != null) in.removeModifier(id);
            }
        }

        static net.minecraft.world.entity.LivingEntity wanted(net.minecraft.world.entity.TamableAnimal t) {
            net.minecraft.world.entity.LivingEntity o = t.getOwner();
            if (o == null) return null;
            net.minecraft.world.entity.LivingEntity w = o.getLastHurtMob();
            return (w != null && w.isAlive() && w != o) ? w : null;
        }

        static void jetTick(net.minecraft.world.entity.TamableAnimal t) {
            if (t.level().isClientSide) return;
            net.minecraft.world.entity.LivingEntity w = wanted(t);
            if (t.getTarget() != w) t.setTarget(w);
        }

        static void onTarget(net.minecraftforge.event.entity.living.LivingChangeTargetEvent e) {
            if (e.getEntity() instanceof net.minecraft.world.entity.TamableAnimal t && t.getClass().getName().endsWith("JetSquadronEntity")) {
                net.minecraft.world.entity.LivingEntity w = wanted(t);
                if (e.getNewTarget() != w) e.setNewTarget(w);
            }
        }
    }

    /** Cliente: quita las barras de Legends y devuelve la vida/armadura vanilla con el traje puesto. */
    static class Cl {
        static void init() {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, Cl::onOverlay);
        }

        static void onOverlay(net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre e) {
            net.minecraft.world.entity.player.Player p = net.minecraft.client.Minecraft.getInstance().player;
            if (p == null || !isGlSuit(p)) return;
            net.minecraft.resources.ResourceLocation id = e.getOverlay().id();
            String ns = id.getNamespace();
            String path = id.getPath();
            if (!ns.equals("minecraft") && (path.equals("health") || path.equals("legends_armor"))) {
                e.setCanceled(true);
            } else if (ns.equals("minecraft") && (path.equals("player_health") || path.equals("armor_level"))) {
                e.setCanceled(false);
            }
        }
    }
}
