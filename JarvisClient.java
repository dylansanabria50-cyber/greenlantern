package com.example.jarvis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JarvisClient {
    static class Line {
        final String text;
        final int color;
        final long time;

        Line(String t, int c) {
            text = t;
            color = c;
            time = System.currentTimeMillis();
        }
    }

    static final List<Line> LINES = new ArrayList<>();
    static volatile boolean thinking = false;
    static final Pattern CALL = Pattern.compile("^\\s*(?:hey\\s+|oye\\s+)?jarvis\\b[\\s,:;.!?-]*(.*)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(JarvisClient::overlays);
        MinecraftForge.EVENT_BUS.addListener(JarvisClient::onChat);
        MinecraftForge.EVENT_BUS.addListener(JarvisClient::onJoin);
    }

    public static void say(String text, int color) {
        synchronized (LINES) {
            LINES.add(new Line(text, color));
            while (LINES.size() > 30) LINES.remove(0);
        }
    }

    static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("jarvis", JarvisClient::render);
    }

    static void onJoin(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn e) {
        JarvisMic.start();
    }

    static void onChat(ClientChatEvent e) {
        Matcher m = CALL.matcher(e.getMessage());
        if (!m.matches()) return;
        e.setCanceled(true);
        Minecraft mc = Minecraft.getInstance();
        mc.gui.getChat().addRecentChat(e.getMessage());
        String q = m.group(1).trim();
        if (q.isEmpty()) q = "Hola";
        ask(q);
    }

    /** Entrada comun de texto (chat o, mas adelante, microfono). Debe llamarse en el hilo del cliente. */
    public static void ask(String q) {
        Minecraft mc = Minecraft.getInstance();
        say("Usted: " + q, 0xFFFFFF);
        if (JarvisMod.MUSIC_ON.get()) {
            String m = JarvisMusic.tryHandle(q);
            if (m != null) {
                say("Jarvis: " + m, 0x66E0FF);
                JarvisVoice.speak(m);
                return;
            }
        }
        String ctx = context(mc);
        thinking = true;
        final String question = q;
        Thread t = new Thread(() -> {
            try {
                String r = JarvisBrain.ask(question, ctx);
                r = r.replace("*", "").replace("_", "").replace("#", "").replace(String.valueOf((char) 96), "");
                say("Jarvis: " + r, 0x66E0FF);
                JarvisVoice.speak(r);
            } catch (java.net.ConnectException ce) {
                say("Jarvis: No logro conectar con Ollama en " + JarvisMod.OLLAMA_URL.get() + ". Verifique que este abierto, senor.", 0xFF6666);
            } catch (Throwable ex) {
                say("Jarvis: Error: " + ex.getMessage(), 0xFF6666);
            } finally {
                thinking = false;
            }
        }, "jarvis-cerebro");
        t.setDaemon(true);
        t.start();
    }

    static String context(Minecraft mc) {
        try {
            if (mc.player == null || mc.level == null) return "sin partida";
            var p = mc.player;
            String biome = mc.level.getBiome(p.blockPosition()).unwrapKey().map(k -> k.location().getPath()).orElse("?");
            long dt = mc.level.getDayTime() % 24000L;
            return "jugador " + p.getName().getString() + ", vida " + (int) p.getHealth() + "/" + (int) p.getMaxHealth()
                    + ", hambre " + p.getFoodData().getFoodLevel() + "/20, dimension " + mc.level.dimension().location().getPath()
                    + ", bioma " + biome + ", " + (dt < 12000 ? "de dia" : "de noche")
                    + ", posicion " + p.blockPosition().getX() + " " + p.blockPosition().getY() + " " + p.blockPosition().getZ()
                    + ", objeto en mano " + p.getMainHandItem().getHoverName().getString();
        } catch (Throwable t) {
            return "desconocido";
        }
    }

    static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        long now = System.currentTimeMillis();
        List<Line> vis = new ArrayList<>();
        synchronized (LINES) {
            for (Line l : LINES) if (now - l.time < 20000L) vis.add(l);
        }
        String st = JarvisMic.status;
        if (vis.isEmpty() && !thinking) {
            if (!st.isEmpty()) {
                boolean on = JarvisMic.listening;
                g.drawString(mc.font, (on ? "\u25CF " : "\u25CB ") + "Jarvis: " + st, 8, 6, on ? 0xFF33CCFF : 0xFF999999);
            }
            return;
        }
        int maxW = Math.min(260, w / 2 - 12);
        List<FormattedCharSequence> rows = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();
        for (Line l : vis) {
            for (FormattedCharSequence s : mc.font.split(FormattedText.of(l.text), maxW)) {
                rows.add(s);
                cols.add(0xFF000000 | l.color);
            }
        }
        if (thinking) {
            rows.add(mc.font.split(FormattedText.of("Jarvis esta pensando..."), maxW).get(0));
            cols.add(0xFF999999);
        }
        int lh = 10;
        int max = 14;
        int from = Math.max(0, rows.size() - max);
        int n = rows.size() - from;
        int x = 8, y = 8;
        g.fill(x - 4, y - 4, x + maxW + 4, y + n * lh + 4, 0x90001520);
        g.fill(x - 4, y - 4, x - 2, y + n * lh + 4, 0xFF33CCFF);
        for (int i = 0; i < n; i++) {
            g.drawString(mc.font, rows.get(from + i), x, y + i * lh, cols.get(from + i));
        }
    }
}
