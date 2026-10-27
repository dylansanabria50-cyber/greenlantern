package com.example.greenlantern.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * El traje se dibuja en el momento como una skin de 64x64 (formato clasico), igual que en la pagina
 * "Traje de Voluntad": tres trajes, tres emblemas propios, tres verdes y antifaz opcional.
 * Para la transformacion se arman cuadros donde el traje sube de los pies a la cabeza con una linea de luz.
 */
public final class SuitTexture {
    public static final String[] TRAJES = {"Centinela", "Cometa", "Faro"};
    public static final String[] EMBLEMAS = {"Estrella", "Llama", "Alas"};
    public static final String[] VERDES = {"Esmeralda", "Jade", "Lima"};
    /** verde principal, claro, brillo */
    static final int[][] VERDE = {{0x1fb866, 0x3dff8e, 0xd2ffe6}, {0x13a37a, 0x2ee6a8, 0xc8fff0}, {0x5cbf18, 0x9dff3d, 0xecffd2}};
    static final String[][] EMB = {
            {".a..a.", "..aa..", ".abba.", ".abba.", "..aa..", ".a..a."},
            {"..a...", "..aa..", ".aab..", ".abba.", "abbbba", ".abba."},
            {"a....a", "aa..aa", ".abba.", "..bb..", "..aa..", "......"}};
    public static final int FRAMES = 16;

    public static int traje(int d) { return Math.min(2, d & 3); }
    public static int emblema(int d) { return Math.min(2, (d >> 2) & 3); }
    public static int verde(int d) { return Math.min(2, (d >> 4) & 3); }
    public static boolean antifaz(int d) { return ((d >> 6) & 1) == 0; }
    public static int pack(int t, int e, int v, boolean antifaz) { return (t & 3) | (e & 3) << 2 | (v & 3) << 4 | (antifaz ? 0 : 64); }

    /** Color claro del diseño (para el aura y las particulas). */
    public static int luz(int d) { return VERDE[verde(d)][1]; }

    public record Juego(ResourceLocation[] base, ResourceLocation[] brillo) { }

    private static final Map<Integer, Juego> CACHE = new HashMap<>();

    public static Juego get(int design) {
        return CACHE.computeIfAbsent(design & 127, SuitTexture::armar);
    }

    // ---------- partes de la skin ----------
    private record Parte(String nombre, int ox, int oy, int w, int h, int d, int yArriba) { }

    private static final Parte[] PARTES = {
            new Parte("cabeza", 0, 0, 8, 8, 8, 32), new Parte("torso", 16, 16, 8, 12, 4, 24),
            new Parte("brazoD", 40, 16, 4, 12, 4, 24), new Parte("brazoI", 32, 48, 4, 12, 4, 24),
            new Parte("piernaD", 0, 16, 4, 12, 4, 12), new Parte("piernaI", 16, 48, 4, 12, 4, 12)};
    private static final String[] CARAS = {"top", "bottom", "right", "front", "left", "back"};

    private static int[] rect(Parte p, String cara) {
        int x = p.ox, y = p.oy, w = p.w, h = p.h, d = p.d;
        return switch (cara) {
            case "top" -> new int[]{x + d, y, w, d};
            case "bottom" -> new int[]{x + d + w, y, w, d};
            case "right" -> new int[]{x, y + d, d, h};
            case "front" -> new int[]{x + d, y + d, w, h};
            case "left" -> new int[]{x + d + w, y + d, d, h};
            default -> new int[]{x + d + w + d, y + d, w, h};
        };
    }

    private static char cabeza(String cara, int u, int v, boolean antifaz) {
        if (cara.equals("top")) return 'h';
        if (cara.equals("bottom")) return 'k';
        if (cara.equals("back")) return v <= 5 ? 'h' : 'k';
        if (cara.equals("front")) {
            if (v <= 1) return 'h';
            if (v == 3 || v == 4) {
                if (antifaz) return v == 3 && (u == 1 || u == 2 || u == 5 || u == 6) ? 'o' : 'm';
                if (v == 3) return u == 1 || u == 6 ? 'w' : u == 2 || u == 5 ? 'p' : 'k';
            }
            if (v == 6 && (u == 3 || u == 4)) return 'q';
            return 'k';
        }
        int haciaFrente = cara.equals("right") ? u : 7 - u;
        if (v <= 2) return 'h';
        if (haciaFrente <= 1 && v <= 5) return 'h';
        if (antifaz && (v == 3 || v == 4) && haciaFrente >= 5) return 'm';
        return 'k';
    }

    private static char tipo(int design, String parte, String cara, int u, int v) {
        int t = traje(design);
        if (parte.equals("cabeza")) return cabeza(cara, u, v, antifaz(design));
        if (parte.equals("brazoD") && v == 10 && !cara.equals("top") && !cara.equals("bottom")) return 'r';
        if (parte.equals("torso") && cara.equals("front") && u >= 1 && u <= 6 && v >= 1 && v <= 6) {
            char c = EMB[emblema(design)][v - 1].charAt(u - 1);
            return c == 'a' ? 'b' : c == 'b' ? 'g' : 'e';
        }
        boolean brazo = parte.startsWith("brazo");
        boolean derecho = parte.endsWith("D");
        boolean interior = derecho ? cara.equals("left") : cara.equals("right");
        boolean exterior = derecho ? cara.equals("right") : cara.equals("left");
        boolean arribaAbajo = cara.equals("top") || cara.equals("bottom");
        if (t == 0) { // Centinela: pechera en V, guantes y botas
            if (parte.equals("torso")) {
                if (cara.equals("top")) return 'g';
                if (cara.equals("bottom")) return 'n';
                if (cara.equals("front") || cara.equals("back")) return v < 7 - Math.abs(u - 3.5) * 1.5 ? 'g' : 'n';
                return v < 2 ? 'g' : 'n';
            }
            if (brazo) return arribaAbajo || v < 2 || v >= 7 ? 'g' : 'n';
            if (arribaAbajo) return 'n';
            if (v >= 8) return 'g';
            return exterior && (u == 1 || u == 2) ? 'g' : 'n';
        }
        if (t == 1) { // Cometa: franja diagonal y mangas
            if (parte.equals("torso")) {
                if (cara.equals("top")) return 'g';
                if (cara.equals("bottom")) return 'n';
                if (cara.equals("front") || cara.equals("back")) {
                    int uu = cara.equals("front") ? u : 7 - u;
                    return Math.abs(uu - (7 - v * 7.0 / 11)) < 1.6 ? 'g' : 'n';
                }
                return v < 2 ? 'g' : 'n';
            }
            if (brazo) { if (arribaAbajo) return 'g'; if (interior) return 'n'; return v == 6 || v == 7 ? 'n' : 'g'; }
            if (arribaAbajo) return 'n';
            if (v >= 9) return 'g';
            return cara.equals("front") && (u == 1 || u == 2) ? 'g' : 'n';
        }
        // Faro: casi todo verde con paneles negros
        if (parte.equals("torso")) {
            if (cara.equals("bottom") || cara.equals("right") || cara.equals("left")) return 'n';
            if (cara.equals("back")) return u == 3 || u == 4 ? 'n' : 'g';
            if (cara.equals("front")) return v >= 7 && (u <= 1 || u >= 6) ? 'n' : 'g';
            return 'g';
        }
        if (brazo) return interior ? 'n' : 'g';
        if (cara.equals("bottom") || v >= 10) return 'n';
        return interior ? 'n' : 'g';
    }

    private static int colorDe(char t, int[] p) {
        return switch (t) {
            case 'g', 'm' -> p[0];
            case 'l' -> p[1];
            case 'b', 'r' -> p[2];
            case 'h' -> 0x3b2a1e;
            case 'k' -> 0xc99a76;
            case 'q' -> 0x8a5a48;
            case 'w' -> 0xffffff;
            case 'p' -> 0x2a3b5a;
            case 'o' -> 0xe9fff3;
            case 'e' -> 0x050807;
            default -> 0x0b1310;
        };
    }

    private static float brilloDe(char t) {
        return switch (t) {
            case 'o', 'b', 'r' -> 1.0f;
            case 'l' -> 0.6f;
            case 'g' -> 0.07f;
            case 'm' -> 0.08f;
            default -> 0f;
        };
    }

    private static int abgr(int a, int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static int escala(int rgb, float f) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 255) * f)), g = Math.min(255, Math.round(((rgb >> 8) & 255) * f)), b = Math.min(255, Math.round((rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    private static float ruido(int x, int y) {
        double s = Math.sin(x * 127.1 + y * 311.7) * 43758.5453;
        return (float) (s - Math.floor(s));
    }

    /** Pixel por pixel: color, brillo y altura desde los pies (en pixeles del modelo). */
    private static Juego armar(int design) {
        int[] col = new int[64 * 64], emi = new int[64 * 64];
        float[] alto = new float[64 * 64];
        boolean[] usado = new boolean[64 * 64];
        int[] p = VERDE[verde(design)];
        for (Parte parte : PARTES) {
            for (String cara : CARAS) {
                int[] r = rect(parte, cara);
                int w = r[2], h = r[3];
                char[] T = new char[w * h];
                for (int v = 0; v < h; v++) for (int u = 0; u < w; u++) T[v * w + u] = tipo(design, parte.nombre, cara, u, v);
                char[] T2 = T.clone(); // juntas que brillan donde el verde toca el negro
                for (int v = 0; v < h; v++) for (int u = 0; u < w; u++) {
                    if (T[v * w + u] != 'g') continue;
                    int[][] vec = {{u - 1, v}, {u + 1, v}, {u, v - 1}, {u, v + 1}};
                    for (int[] q : vec) if (q[0] >= 0 && q[1] >= 0 && q[0] < w && q[1] < h && T[q[1] * w + q[0]] == 'n') { T2[v * w + u] = 'l'; break; }
                }
                for (int v = 0; v < h; v++) for (int u = 0; u < w; u++) {
                    char t = T2[v * w + u];
                    int x = r[0] + u, y = r[1] + v, i = y * 64 + x;
                    float f = (t == 'w' || t == 'o' || t == 'b') ? 1f : 0.9f + ruido(x, y) * 0.16f;
                    col[i] = escala(colorDe(t, p), f);
                    float e = brilloDe(t);
                    emi[i] = escala((t == 'r' || t == 'b' || t == 'o') ? p[2] : p[1], e);
                    alto[i] = cara.equals("top") ? parte.yArriba : cara.equals("bottom") ? parte.yArriba - parte.h : parte.yArriba - v - 0.5f;
                    usado[i] = true;
                }
            }
        }
        ResourceLocation[] base = new ResourceLocation[FRAMES], brillo = new ResourceLocation[FRAMES];
        var tm = Minecraft.getInstance().getTextureManager();
        for (int f = 0; f < FRAMES; f++) {
            boolean completo = f == FRAMES - 1;
            float corte = (f + 1) * 33.0f / FRAMES;
            NativeImage a = new NativeImage(NativeImage.Format.RGBA, 64, 64, true);
            NativeImage b = new NativeImage(NativeImage.Format.RGBA, 64, 64, true);
            for (int i = 0; i < 64 * 64; i++) {
                if (!usado[i]) continue;
                int x = i % 64, y = i / 64;
                if (completo || alto[i] < corte - 1.5f) {
                    a.setPixelRGBA(x, y, abgr(255, col[i]));
                    b.setPixelRGBA(x, y, abgr(255, emi[i]));
                } else if (alto[i] < corte) { // linea de luz que va subiendo
                    a.setPixelRGBA(x, y, abgr(255, p[2]));
                    b.setPixelRGBA(x, y, abgr(255, p[2]));
                }
            }
            base[f] = tm.register("greenlantern_traje_" + design + "_" + f, new DynamicTexture(a));
            brillo[f] = tm.register("greenlantern_brillo_" + design + "_" + f, new DynamicTexture(b));
        }
        return new Juego(base, brillo);
    }

    private SuitTexture() { }
}
