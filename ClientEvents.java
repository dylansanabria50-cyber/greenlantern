package com.example.greenlantern.client;

import com.example.greenlantern.GreenLanternMod;
import com.example.greenlantern.ModNetwork;
import com.example.greenlantern.RingPowers;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public class ClientEvents {
    public static final String CATEGORY = "key.categories.greenlantern";

    public static final KeyMapping BLAST = new KeyMapping("key.greenlantern.blast", GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping SUIT = new KeyMapping("key.greenlantern.suit", GLFW.GLFW_KEY_H, CATEGORY);
    public static final KeyMapping FLIGHT = new KeyMapping("key.greenlantern.flight", GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping JET = new KeyMapping("key.greenlantern.jet", GLFW.GLFW_KEY_J, CATEGORY);
    public static final KeyMapping BEAM = new KeyMapping("key.greenlantern.beam", GLFW.GLFW_KEY_V, CATEGORY);
    public static final KeyMapping WAVE = new KeyMapping("key.greenlantern.wave", GLFW.GLFW_KEY_B, CATEGORY);
    public static final KeyMapping BUBBLE = new KeyMapping("key.greenlantern.bubble", GLFW.GLFW_KEY_Z, CATEGORY);
    public static final KeyMapping PULSE = new KeyMapping("key.greenlantern.pulse", GLFW.GLFW_KEY_X, CATEGORY);
    public static final KeyMapping COLOSO = new KeyMapping("key.greenlantern.coloso", GLFW.GLFW_KEY_K, CATEGORY);
    private static boolean bubbleHeld = false, escudoHeld = false, enColoso = false;
    private static net.minecraft.client.CameraType camaraAntes = null;
    private static int enElAire = 0;




    private static final int HOLD_TICKS = 8; // ~0.4 s
    private static int holdTicks = 0;
    private static boolean armed = true;

    /** Tinte verde translucido con borde, sobre un objeto de size x size pixeles. */
    public static void tint(GuiGraphics g, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, 0x7A22FF66);
        g.fill(x, y, x + size, y + 1, 0xCC66FF99);
        g.fill(x, y + size - 1, x + size, y + size, 0xCC66FF99);
        g.fill(x, y, x + 1, y + size, 0xCC66FF99);
        g.fill(x + size - 1, y, x + size, y + size, 0xCC66FF99);
    }

    @Mod.EventBusSubscriber(modid = GreenLanternMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent e) {
            e.register(BLAST);
            e.register(SUIT);
            e.register(FLIGHT);
            e.register(JET);
            e.register(BEAM);
            e.register(WAVE);
            e.register(BUBBLE);
            e.register(PULSE);
            e.register(COLOSO);
            com.example.greenlantern.ColosoEntity.CORRER = () -> Minecraft.getInstance().options.keySprint.isDown();
            com.example.greenlantern.ColosoEntity.SALTAR = () -> Minecraft.getInstance().screen == null && Minecraft.getInstance().options.keyJump.isDown();
        }

        /** Barra de voluntad arriba a la izquierda (solo con el anillo). */
        @SubscribeEvent
        public static void registerOverlays(net.minecraftforge.client.event.RegisterGuiOverlaysEvent e) {
            e.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "willpower", (gui, g, pt, w, h) -> ForgeBus.drawWillpower(g));
        }
    }

    @Mod.EventBusSubscriber(modid = GreenLanternMod.MODID, value = Dist.CLIENT)
    public static class ForgeBus {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (mc.screen != null) {
                holdTicks = 0; armed = false;
                if (bubbleHeld) { bubbleHeld = false; ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(7)); }
                return;
            }
            // con el traje puesto: saltar y mantener Espacio en el aire alcanza para empezar a volar
            var lp = mc.player;
            if (lp.onGround() || lp.isPassenger() || lp.isInWater()) enElAire = 0; else enElAire++;
            var ab = lp.getAbilities();
            if (ClientSuit.isOn(lp.getId()) && ab.mayfly && !ab.flying && enElAire >= 4 && mc.options.keyJump.isDown() && !lp.isFallFlying()) {
                ab.flying = true;
                lp.onUpdateAbilities();
            }
            // arriba del coloso las teclas manejan sus ataques
            boolean coloso = lp.getVehicle() instanceof com.example.greenlantern.ColosoEntity;
            if (coloso != enColoso) {
                enColoso = coloso;
                if (coloso) { camaraAntes = mc.options.getCameraType(); mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); }
                else if (camaraAntes != null) { mc.options.setCameraType(camaraAntes); camaraAntes = null; }
            }
            while (COLOSO.consumeClick()) ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(16));
            if (coloso) {
                while (BEAM.consumeClick()) ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(11));
                while (WAVE.consumeClick()) ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(12));
                while (FLIGHT.consumeClick()) ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(13));
                while (BUBBLE.consumeClick()) { }
                while (PULSE.consumeClick()) { }
                while (SUIT.consumeClick()) { }
                while (JET.consumeClick()) { }
                while (BLAST.consumeClick()) { }
                boolean esc = BUBBLE.isDown();
                if (esc != escudoHeld) { escudoHeld = esc; ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(esc ? 14 : 15)); }
                if (bubbleHeld) { bubbleHeld = false; ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(7)); }
                return;
            }
            escudoHeld = false;
            while (BEAM.consumeClick()) ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(4));
            while (WAVE.consumeClick()) ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(5));
            while (PULSE.consumeClick()) ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(8));
            while (BUBBLE.consumeClick()) { }
            boolean holdBubble = BUBBLE.isDown();
            if (holdBubble != bubbleHeld) { bubbleHeld = holdBubble; ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(holdBubble ? 6 : 7)); }
            while (SUIT.consumeClick()) {
                if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) { mc.setScreen(new DesignScreen(ClientSuit.design(mc.player.getId()))); return; }
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(1));
            }
            while (FLIGHT.consumeClick()) ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(2));
            while (JET.consumeClick()) ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(3));
            if (!BLAST.isDown()) armed = true;
            if (armed && BLAST.isDown()) {
                holdTicks++;
                if (holdTicks == HOLD_TICKS) {
                    if (hasRingClient(mc)) mc.setScreen(new QuickScreen());
                    else mc.player.displayClientMessage(Component.literal("§cNecesitas el Anillo de Poder en el inventario"), true);
                    return;
                }
            } else if (holdTicks > 0) {
                int t = holdTicks;
                holdTicks = 0;
                if (t < HOLD_TICKS) {
                    if (hasRingClient(mc)) mc.setScreen(new RingScreen());
                    else mc.player.displayClientMessage(Component.literal("§cNecesitas el Anillo de Poder en el inventario"), true);
                    return;
                }
            } else if (armed && BLAST.consumeClick()) { // toque muy rapido (menos de un tick)
                if (hasRingClient(mc)) mc.setScreen(new RingScreen());
                else mc.player.displayClientMessage(Component.literal("§cNecesitas el Anillo de Poder en el inventario"), true);
                return;
            }
            while (BLAST.consumeClick()) { }
        }

        /** Arriba del coloso, el clic izquierdo es el combo de puños. */
        @SubscribeEvent
        public static void onAttack(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered e) {
            Minecraft mc = Minecraft.getInstance();
            if (e.isAttack() && mc.player != null && mc.player.getVehicle() instanceof com.example.greenlantern.ColosoEntity) {
                e.setCanceled(true);
                e.setSwingHand(false);
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(10));
            }
        }

        private static boolean hasRingClient(Minecraft mc) {
            return RingPowers.hasRing(mc.player);
        }

        /** Tinte verde translucido sobre los objetos creados en inventarios y cofres. */
        @SubscribeEvent
        public static void onScreenRender(ScreenEvent.Render.Post e) {
            if (e.getScreen() instanceof AbstractContainerScreen<?> scr) {
                GuiGraphics g = e.getGuiGraphics();
                for (Slot s : scr.getMenu().slots) {
                    if (RingPowers.isConjured(s.getItem())) {
                        int x = scr.getGuiLeft() + s.x;
                        int y = scr.getGuiTop() + s.y;
                        tint(g, x, y, 16);
                    }
                }
            }
        }

        /** Barra de voluntad: verde, amarilla y roja (titila) cuando queda poca. */
        static void drawWillpower(GuiGraphics g) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui || !RingPowers.hasRing(mc.player)) return;
            int e = Math.max(0, Math.min(RingPowers.MAX_ENERGY, ClientSuit.energy));
            int x = 6, y = 6, w = 102;
            boolean poca = e < 20;
            int color = e > 50 ? 0xFF50CC4B : e >= 20 ? 0xFFFFBF4D : ((mc.player.tickCount / 5) % 2 == 0 ? 0xFFFF5D5D : 0xFF7A2424);
            g.fill(x - 2, y - 2, x + w + 2, y + 21, 0x990E1F15);
            g.drawString(mc.font, "Voluntad " + e + "/" + RingPowers.MAX_ENERGY + (ClientSuit.isOn(mc.player.getId()) ? "  \u00a7a[traje]" : ""), x, y, poca ? 0xFF8A8A : 0x9DFFC6, false);
            g.fill(x, y + 11, x + w, y + 17, 0xFF0F2A1F);
            g.fill(x + 1, y + 12, x + 1 + (w - 2) * e / RingPowers.MAX_ENERGY, y + 16, color);
        }

        /** Panel de poderes a la derecha de la pantalla (solo con el anillo). */
        private static void drawPanel(Minecraft mc, GuiGraphics g, int w, int h) {
            String[][] rows = {
                    {SUIT.getTranslatedKeyMessage().getString(), "Traje (Shift: diseño)"},
                    {"Espacio", "Volar: salta y mantene (" + FLIGHT.getTranslatedKeyMessage().getString() + ": despegar/aterrizar)"},
                    {JET.getTranslatedKeyMessage().getString(), "Caza de energia"},
                    {BLAST.getTranslatedKeyMessage().getString(), "Crear objetos (mantener: favoritos)"},
                    {BEAM.getTranslatedKeyMessage().getString(), "Rayo del anillo"},
                    {WAVE.getTranslatedKeyMessage().getString(), "Cargar el anillo (onda)"},
                    {BUBBLE.getTranslatedKeyMessage().getString(), "Burbuja (mantener) / " + PULSE.getTranslatedKeyMessage().getString() + ": pulso"},
                    {COLOSO.getTranslatedKeyMessage().getString(), "Coloso de voluntad"},
            };
            int textW = 0;
            for (String[] r : rows) textW = Math.max(textW, mc.font.width("[" + r[0] + "] " + r[1]));
            int pw = textW + 12;
            int ph = rows.length * 11 + 20;
            int x = w - pw - 6;
            int y = 4;
            g.fill(x - 1, y - 1, x + pw + 1, y + ph + 1, 0xFF2E6B45);
            g.fill(x, y, x + pw, y + ph, 0xAA0E1F15);
            g.drawString(mc.font, "Guia del Anillo", x + 6, y + 4, 0x55FF77, false);
            for (int i = 0; i < rows.length; i++) {
                g.drawString(mc.font, "§a[" + rows[i][0] + "] §f" + rows[i][1], x + 6, y + 17 + i * 11, 0xFFFFFF, false);
            }
        }

        /** Tinte verde translucido sobre los objetos creados en la barra rapida. */
        @SubscribeEvent
        public static void onHotbar(RenderGuiOverlayEvent.Post e) {
            if (e.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            GuiGraphics g = e.getGuiGraphics();
            int w = e.getWindow().getGuiScaledWidth();
            int h = e.getWindow().getGuiScaledHeight();
            if (RingPowers.hasRing(mc.player) && !mc.options.hideGui) drawPanel(mc, g, w, h);
            for (int i = 0; i < 9; i++) {
                if (RingPowers.isConjured(mc.player.getInventory().items.get(i))) {
                    int x = w / 2 - 90 + i * 20 + 2;
                    int y = h - 16 - 3;
                    tint(g, x, y, 16);
                }
            }
        }
    }
}
