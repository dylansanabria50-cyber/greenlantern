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
    public static final KeyMapping FLIGHT = new KeyMapping("key.greenlantern.flight", GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping BLAST = new KeyMapping("key.greenlantern.blast", GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping SHIELD = new KeyMapping("key.greenlantern.shield", GLFW.GLFW_KEY_V, CATEGORY);
    public static final KeyMapping WALL = new KeyMapping("key.greenlantern.wall", GLFW.GLFW_KEY_B, CATEGORY);
    public static final KeyMapping SUIT = new KeyMapping("key.greenlantern.suit", GLFW.GLFW_KEY_N, CATEGORY);

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
            e.register(FLIGHT);
            e.register(BLAST);
            e.register(SHIELD);
            e.register(WALL);
            e.register(SUIT);
        }
    }

    @Mod.EventBusSubscriber(modid = GreenLanternMod.MODID, value = Dist.CLIENT)
    public static class ForgeBus {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (mc.screen != null) { holdTicks = 0; armed = false; return; }
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
            while (FLIGHT.consumeClick()) ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(RingPowers.ACT_FLIGHT));
            while (SHIELD.consumeClick()) ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(RingPowers.ACT_SHIELD));
            while (SUIT.consumeClick()) ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(RingPowers.ACT_SUIT));
            while (WALL.consumeClick()) ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(RingPowers.ACT_WALL));
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

        /** Panel de poderes a la derecha de la pantalla (solo con el anillo puesto). */
        private static void drawPanel(Minecraft mc, GuiGraphics g, int w, int h) {
            String[][] rows = {
                    {FLIGHT.getTranslatedKeyMessage().getString(), "Volar (doble salto)"},
                    {BLAST.getTranslatedKeyMessage().getString(), "Crear objetos (mantener: favoritos)"},
                    {SHIELD.getTranslatedKeyMessage().getString(), "Burbuja protectora"},
                    {WALL.getTranslatedKeyMessage().getString(), "Muro de energia"},
                    {SUIT.getTranslatedKeyMessage().getString(), "Traje de Linterna Verde"}
            };
            int textW = 0;
            for (String[] r : rows) textW = Math.max(textW, mc.font.width("[" + r[0] + "] " + r[1]));
            int pw = textW + 12;
            int ph = rows.length * 11 + 20;
            int x = w - pw - 6;
            int y = h / 2 - ph / 2;
            g.fill(x - 1, y - 1, x + pw + 1, y + ph + 1, 0xFF2E6B45);
            g.fill(x, y, x + pw, y + ph, 0xAA0E1F15);
            g.drawString(mc.font, "Anillo de Poder", x + 6, y + 4, 0x55FF77, false);
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
            if (RingPowers.isWorn(mc.player)) drawPanel(mc, g, w, h);
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
