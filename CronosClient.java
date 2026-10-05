package com.example.cronos;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

/** Teclas, barra de energia e indicadores del cliente. */
public class CronosClient {
    static final String CAT = "Cronosapiente";
    static final KeyMapping K_TRANS = new KeyMapping("Cronosapiente: Transformarse", GLFW.GLFW_KEY_V, CAT);
    static final KeyMapping K_FIRE = new KeyMapping("Cronosapiente: Disparar rayo (mantener)", GLFW.GLFW_KEY_Z, CAT);
    static final KeyMapping K_RAY = new KeyMapping("Cronosapiente: Cambiar rayo", GLFW.GLFW_KEY_X, CAT);
    static final KeyMapping K_WIND = new KeyMapping("Cronosapiente: Dar cuerda (mantener y soltar)", GLFW.GLFW_KEY_C, CAT);
    static final KeyMapping K_CUE = new KeyMapping("Cronosapiente: Cambiar poder de cuerda", GLFW.GLFW_KEY_G, CAT);
    static final KeyMapping K_10K = new KeyMapping("Cronosapiente: Forma 10K", GLFW.GLFW_KEY_H, CAT);
    static final KeyMapping K_SIZE = new KeyMapping("Cronosapiente: Cambiar tamano (10K)", GLFW.GLFW_KEY_J, CAT);

    static volatile boolean on, f10k, big;
    static volatile int energy = 1000, ray, cue, slow, stop, acc;
    static boolean windDown = false;
    static long windStartMs = 0;

    public static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(CronosClient::keys);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(CronosClient::overlays);
        MinecraftForge.EVENT_BUS.addListener(CronosClient::tick);
        MinecraftForge.EVENT_BUS.addListener(CronosClient::render);
    }

    static void keys(RegisterKeyMappingsEvent e) {
        e.register(K_TRANS); e.register(K_FIRE); e.register(K_RAY); e.register(K_WIND);
        e.register(K_CUE); e.register(K_10K); e.register(K_SIZE);
    }

    static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("cronos", CronosClient::hud);
    }

    static void send(int id) {
        CronosMod.NET.sendToServer(new CronosMod.Act(id));
    }

    static void onSync(CronosMod.Sync m) {
        boolean sizeChanged = big != m.big;
        on = m.on; f10k = m.f10k; big = m.big; energy = m.energy;
        ray = m.ray; cue = m.cue; slow = m.slow; stop = m.stop; acc = m.acc;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.getPersistentData().putBoolean("cr_big", big);
            mc.player.getPersistentData().putBoolean("cr_on", on);
            if (sizeChanged) mc.player.refreshDimensions();
        }
    }

    static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        boolean free = mc.screen == null;
        while (K_TRANS.consumeClick()) { if (free) send(0); }
        while (K_RAY.consumeClick()) { if (free) send(2); }
        while (K_CUE.consumeClick()) { if (free) send(5); }
        while (K_10K.consumeClick()) { if (free) send(6); }
        while (K_SIZE.consumeClick()) { if (free) send(7); }
        if (free && on && K_FIRE.isDown()) send(1);
        boolean w = free && K_WIND.isDown();
        if (w && !windDown) { send(3); windStartMs = System.currentTimeMillis(); }
        if (!w && windDown) send(4);
        windDown = w;
    }

    static void render(RenderPlayerEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.getEntity() == mc.player && big) {
            e.getPoseStack().scale(1.6f, 1.6f, 1.6f);
        }
    }

    static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !on) return;
        int bw = 200, x = w / 2 - bw / 2, y = 6;
        g.fill(x - 2, y - 2, x + bw + 2, y + 10, 0xA0000000);
        g.fill(x, y, x + bw, y + 8, 0xFF1A2600);
        int fill = (int) (bw * (energy / 1000.0));
        g.fill(x, y, x + fill, y + 8, 0xFFC0FF00);
        g.drawCenteredString(mc.font, "Energia " + energy + " / 1000", w / 2, y + 12, 0xFFFFFFFF);
        String rayName = ray < CronosMod.RAYS.length ? CronosMod.RAYS[ray] : "?";
        g.drawCenteredString(mc.font, "Rayo: " + rayName + (f10k ? "  [10K]" : ""), w / 2, y + 23, 0xFFC0FF00);
        g.drawCenteredString(mc.font, "Cuerda: " + CronosMod.CUES[Math.min(cue, CronosMod.CUES.length - 1)], w / 2, y + 34, 0xFF66CCFF);
        int ly = y + 45;
        if (slow > 0) { g.drawCenteredString(mc.font, "Tiempo ralentizado " + (slow / 20) + "s", w / 2, ly, 0xFF99AAFF); ly += 11; }
        if (acc > 0) { g.drawCenteredString(mc.font, "Tiempo acelerado " + (acc / 20) + "s", w / 2, ly, 0xFFFFCC66); ly += 11; }
        if (stop > 0) { g.drawCenteredString(mc.font, "Tiempo detenido " + (stop / 20) + "s", w / 2, ly, 0xFFFF6666); ly += 11; }
        if (windDown) {
            int sec = (int) Math.min(20, (System.currentTimeMillis() - windStartMs) / 1000);
            long ms = Math.min(20000, System.currentTimeMillis() - windStartMs);
            int ww = (int) (bw * (ms / 20000.0));
            g.fill(x, ly + 2, x + bw, ly + 6, 0x80000000);
            g.fill(x, ly + 2, x + ww, ly + 6, 0xFFFFFFFF);
            g.drawCenteredString(mc.font, "Dando cuerda... " + sec + "s", w / 2, ly + 9, 0xFFFFFFFF);
        }
    }
}
