package com.example.policia;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Rama de habilidades del albanil. */
public class PoliciaAlbanilScreen extends Screen {
    static final int PW = 460, PH = 340, LEFT_W = 104;
    static final String[] NAMES = {"Pico de albanil", "MURO", "REFUGIO", "AYUDANTE I", "AYUDANTE II", "AYUDANTE III", "LINTERNA", "TRACTOR", "REFUGIO II", "PICO II"};
    static final String[] DESC = {
            "Te da un pico de diamante durante 7 s, con Fuerza I, Prisa minera I y Fortuna II. No se puede soltar ni guardar en cofres; puedes encantarlo y conserva los encantamientos. Enfriamiento: 8 s.",
            "Levanta un muro de 3 de alto por 3 de largo frente a ti. Puedes hacer hasta 3 muros (pulsa J de nuevo para cada uno). Desaparecen a los 15 s. Enfriamiento: 5 s.",
            "Mejora de MURO: lo reemplaza. Casa sobre la superficie con puerta y piso de madera, cama y cofre, durante 35 s. Lo que dejes dentro (cofre, bloques, objetos) vuelve a estar en el mismo lugar cuando la invocas de nuevo. Enfriamiento: 40 s.",
            "PEDIR AYUDANTE: 1 ayudante con pico de piedra que sigue y defiende. H: orden (seguir, picar delante, buscar minerales). X: elegir area (rueda: largo, Mayus+rueda: ancho). Avisan de minerales (Y/N). U: su inventario. 60 s, enf. 30 s. Ves menas de carbon a 4 bloques.",
            "Nivel 2: 3 ayudantes con picos de hierro. Mientras estan activos ves todos los minerales (menos diamante y netherita) a 4 bloques.",
            "Nivel 3: 4 ayudantes con picos de diamante. Mientras estan activos ves todos los minerales a 4 bloques.",
            "LINTERNA: una luz que sigue tu mirada hasta 48 bloques durante 60 s (tecla J con la habilidad elegida, o tecla G). Enfriamiento: 20 s.",
            "TRACTOR: pala cargadora amarilla con cabina, inventario propio (54 casillas) y luces. J la invoca, te bajas o vuelves a subir; Mayus+J la retira. W/S avanzar, A/D girar, ESPACIO sube la pala, CTRL la baja, G luces, U inventario. Rompe lo natural que tiene delante (guarda lo que rompe), pero nunca construcciones de jugadores. Dura 45 s. Enfriamiento: 35 s.",
            "Mejora de REFUGIO (40 XP): una torre de madera de 4 pisos con anexo, camas, cofres, barriles, horno y jardin en el techo. La puerta mira hacia ti. Dura 3 minutos y luego desaparece. Lo que dejes en cualquier cofre, barril u horno se conserva para la proxima vez (el cofre principal de la planta baja recibe lo del REFUGIO anterior). Enfriamiento: 40 s.",
            "Mejora del Pico (25 XP): el pico dura 14 s en vez de 7 s y da Fuerza II y Prisa minera II (conserva Fortuna II y los encantamientos). Enfriamiento: 8 s."};
    static final int[] CX = {45, 45, 45, 130, 130, 130, 215, 300, 45, 215};
    static final int[] ROW = {0, 1, 2, 0, 1, 2, 0, 0, 3, 1};
    int px, py;
    float zoom = 1.0f;

    public PoliciaAlbanilScreen() {
        super(Component.literal("Rama de habilidades"));
    }

    @Override
    protected void init() {
        px = (width - PW) / 2;
        py = (height - PH) / 2;
        addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> onClose())
                .bounds(px + PW - 66, py + PH + 4, 60, 20).build());
    }

    static boolean has(PoliciaAlbanil.AlbSync s, int i) {
        int lv = s == null ? 0 : s.lv;
        if (i == 9) return s != null && s.pl >= 1;
        if (i == 8) return s != null && s.lv >= 3;
        if (i == 7) return s != null && s.tu >= 1;
        if (i == 6) return s != null && s.lu >= 1;
        if (i >= 3) return s != null && s.hl >= i - 2;
        return i == 0 || lv >= i;
    }

    int nodeX(int i) { return px + LEFT_W + 8 + CX[i] - 12; }
    int nodeY(int i) { return py + 40 + ROW[i] * 58; }

    /** Icono del pico (16x16). */
    static void pickIcon(GuiGraphics g, int x, int y) {
        for (int k = 0; k < 10; k++) {
            int hx = x + 8 - k / 3;
            g.fill(hx, y + 5 + k, hx + 2, y + 6 + k, 0xFF8A5A2B);
        }
        g.fill(x + 1, y + 3, x + 15, y + 5, 0xFF30C8C0);
        g.fill(x + 3, y + 2, x + 13, y + 3, 0xFF6AF0E8);
        g.fill(x, y + 4, x + 2, y + 7, 0xFF20A0A0);
        g.fill(x + 14, y + 4, x + 16, y + 7, 0xFF20A0A0);
        g.fill(x + 6, y + 5, x + 10, y + 6, 0xFF1C7C78);
    }

    /** Icono del muro (16x16). */
    static void wallIcon(GuiGraphics g, int x, int y) {
        g.fill(x, y + 2, x + 16, y + 14, 0xFF5A5A5A);
        for (int r = 0; r < 4; r++) {
            int yy = y + 2 + r * 3;
            int off = (r % 2) * 4;
            for (int c = -1; c < 3; c++) {
                int bx = x + off + c * 8;
                int x0 = Math.max(x, bx), x1 = Math.min(x + 16, bx + 7);
                if (x1 > x0) g.fill(x0, yy, x1, yy + 2, 0xFFA8A8A8);
                if (x1 > x0) g.fill(x0, yy, x1, yy + 1, 0xFFC4C4C4);
            }
        }
    }

    /** Icono del refugio (16x16). */
    static void houseIcon(GuiGraphics g, int x, int y) {
        g.fill(x + 3, y + 8, x + 13, y + 15, 0xFFD9B27A);
        for (int k = 0; k < 7; k++) g.fill(x + 7 - k, y + 2 + k, x + 9 + k, y + 3 + k, 0xFFB03A2E);
        g.fill(x + 7, y + 10, x + 10, y + 15, 0xFF6B4A2A);
        g.fill(x + 4, y + 9, x + 6, y + 11, 0xFF7FD0F0);
        g.fill(x + 11, y + 9, x + 12, y + 11, 0xFF7FD0F0);
        g.fill(x + 3, y + 14, x + 13, y + 15, 0xFF8A6A3A);
    }

    /** Icono del ayudante (16x16). */
    static void helperIcon(GuiGraphics g, int x, int y) {
        g.fill(x + 4, y + 6, x + 12, y + 14, 0xFFE0B48A);
        g.fill(x + 3, y + 3, x + 13, y + 7, 0xFFF0C020);
        g.fill(x + 2, y + 6, x + 14, y + 7, 0xFFC89A10);
        g.fill(x + 5, y + 9, x + 7, y + 10, 0xFF202020);
        g.fill(x + 9, y + 9, x + 11, y + 10, 0xFF202020);
        g.fill(x + 6, y + 12, x + 10, y + 13, 0xFF8A4A2A);
        g.fill(x + 4, y + 14, x + 12, y + 16, 0xFF3A5AA8);
    }

    /** Icono de la linterna (16x16). */
    static void lampIcon(GuiGraphics g, int x, int y) {
        g.fill(x + 1, y + 6, x + 5, y + 10, 0xFF555C66);
        g.fill(x + 5, y + 4, x + 9, y + 12, 0xFF8A8F99);
        g.fill(x + 9, y + 3, x + 11, y + 13, 0xFFF0E070);
        g.fill(x + 11, y + 5, x + 15, y + 11, 0x80FFF8B0);
        g.fill(x + 12, y + 7, x + 16, y + 9, 0xA0FFFFFF);
    }

    /** Icono del tractor (16x16). */
    static void tractorIcon(GuiGraphics g, int x, int y) {
        g.fill(x + 5, y + 6, x + 13, y + 11, 0xFFF0B60E);
        g.fill(x + 8, y + 2, x + 13, y + 7, 0xFFC89610);
        g.fill(x + 9, y + 3, x + 12, y + 5, 0xFF9AD0F0);
        g.fill(x + 3, y + 8, x + 6, y + 9, 0xFFF0B60E);
        g.fill(x, y + 9, x + 4, y + 13, 0xFFF0B60E);
        g.fill(x, y + 12, x + 4, y + 13, 0xFF8A8F99);
        g.fill(x + 5, y + 10, x + 9, y + 15, 0xFF202020);
        g.fill(x + 10, y + 10, x + 14, y + 15, 0xFF202020);
        g.fill(x + 6, y + 11, x + 8, y + 14, 0xFF8A8F99);
        g.fill(x + 11, y + 11, x + 13, y + 14, 0xFF8A8F99);
    }

    void padlock(GuiGraphics g, int x, int y) {
        g.fill(x + 8, y + 3, x + 10, y + 11, 0xFFD8D8D8);
        g.fill(x + 14, y + 3, x + 16, y + 11, 0xFFD8D8D8);
        g.fill(x + 8, y + 3, x + 16, y + 5, 0xFFD8D8D8);
        g.fill(x + 6, y + 10, x + 18, y + 20, 0xFFE8B830);
        g.fill(x + 11, y + 13, x + 13, y + 17, 0xFF2A2A2A);
    }

    void tag(GuiGraphics g, String text, int x, int y) {
        int w = font.width(text) + 8;
        g.fill(x - 1, y - 1, x + w + 1, y + 13, 0xFF555C66);
        g.fill(x, y, x + w, y + 12, 0xFF8A8F99);
        g.drawString(font, text, x + 4, y + 2, 0xFFFFFFFF, true);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int rawMx = mx, rawMy = my;
        mx = (int) ((mx - width / 2.0f) / zoom + width / 2.0f);
        my = (int) ((my - height / 2.0f) / zoom + height / 2.0f);
        g.pose().pushPose();
        g.pose().translate(width / 2.0f, height / 2.0f, 0.0f);
        g.pose().scale(zoom, zoom, 1.0f);
        g.pose().translate(-width / 2.0f, -height / 2.0f, 0.0f);
        PoliciaMod.Sync ms = PoliciaClient.mine();
        PoliciaAlbanil.AlbSync s = PoliciaAlbanil.CL;
        int xp = ms == null ? 0 : ms.xp;
        int sel = s.sel == 1 ? (s.lv >= 3 ? 8 : (s.lv >= 2 ? 2 : 1)) : (s.sel == 2 ? 2 + Math.max(1, s.hl) : (s.sel == 3 ? 6 : (s.sel == 4 ? 7 : 0)));
        PoliciaTreeScreen.panel(g, px, py, px + LEFT_W, py + PH);
        tag(g, "Albanil", px + 6, py - 6);
        if (minecraft != null && minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, px + LEFT_W / 2, py + PH - 30, 55,
                    (px + LEFT_W / 2) - mx, (py + 60) - my, minecraft.player);
        }
        int rx = px + LEFT_W + 8;
        PoliciaTreeScreen.panel(g, rx, py, px + PW, py + PH);
        tag(g, "Habilidades", rx + 6, py - 6);
        g.drawString(font, "XP: " + xp, rx + 8, py + 10, 0xFFE8C040, true);
        g.drawString(font, "Desbloquear: clic izquierdo en el icono", rx + 8, py + 20, 0xFF9AA4B0, false);
        for (int i = 1; i < 6; i++) {
            if (i == 3) continue;
            int col = has(s, i) ? 0xFFE0C070 : 0xFF6A5A36;
            int x0 = nodeX(i - 1) + 11, y0 = nodeY(i - 1) + 44, y1 = nodeY(i);
            g.fill(x0, y0, x0 + 2, y1, col);
        }
        {
            int col = has(s, 8) ? 0xFFE0C070 : 0xFF6A5A36;
            int x0 = nodeX(2) + 11, y0 = nodeY(2) + 44, y1 = nodeY(8);
            g.fill(x0, y0, x0 + 2, y1, col);
        }
        int hov = -1;
        for (int i = 0; i < 10; i++) {
            int nx = nodeX(i), y = nodeY(i);
            boolean open = has(s, i);
            boolean hover = mx >= nx && mx <= nx + 24 && my >= y && my <= y + 24;
            if (hover) hov = i;
            int border = (open && i == sel) ? 0xFFFFFFFF : (hover ? 0xFFFFE9A0 : 0xFF6B4A0C);
            g.fill(nx - 1, y - 1, nx + 25, y + 25, border);
            g.fill(nx, y, nx + 24, y + 24, open ? 0xFFC8921C : 0xFF8A6612);
            if (i == 0 || i == 9) pickIcon(g, nx + 4, y + 4);
            else if (i == 1) wallIcon(g, nx + 4, y + 4);
            else if (i == 2 || i == 8) houseIcon(g, nx + 4, y + 4);
            else if (i == 6) lampIcon(g, nx + 4, y + 4);
            else if (i == 7) tractorIcon(g, nx + 4, y + 4);
            else helperIcon(g, nx + 4, y + 4);
            if (!open) {
                g.fill(nx, y, nx + 24, y + 24, 0x99000000);
                padlock(g, nx, y);
            }
            g.drawCenteredString(font, NAMES[i], nx + 12, y + 27, open ? 0xFFFFFFFF : 0xFFA0A0A0);
        }
        int show = hov >= 0 ? hov : sel;
        int bx0 = rx + 6, by0 = py + 258, bx1 = px + PW - 6, by1 = py + PH - 6;
        PoliciaTreeScreen.panel(g, bx0, by0, bx1, by1);
        boolean open = has(s, show);
        g.drawString(font, NAMES[show], bx0 + 5, by0 + 4, 0xFFFFFFFF, true);
        List<FormattedCharSequence> lines = font.split(FormattedText.of(DESC[show]), bx1 - bx0 - 10);
        for (int k = 0; k < lines.size() && k < 5; k++) {
            g.drawString(font, lines.get(k), bx0 + 5, by0 + 15 + k * 9, open ? 0xFFC8D0DC : 0xFF808890, false);
        }
        if (!open) {
            g.drawString(font, "Bloqueada - cuesta " + (show == 9 ? PoliciaAlbanil.COST3 : show == 8 ? PoliciaAlbanil.COST2 : show == 7 ? PoliciaTractor.COST : (show >= 3 && show < 6 ? PoliciaAlbanil.COST + 5 * (show - 3) : PoliciaAlbanil.COST)) + " XP", bx0 + 5, by1 - 11, 0xFFE8B830, false);
        } else if (show == sel) {
            g.drawString(font, "Elegida (tecla J)", bx0 + 5, by1 - 11, 0xFF60E060, false);
        } else {
            g.drawString(font, "Clic para elegir", bx0 + 5, by1 - 11, 0xFF9AA4B0, false);
        }
        g.pose().popPose();
        super.render(g, rawMx, rawMy, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        double rmx = mx, rmy = my;
        mx = (mx - width / 2.0) / zoom + width / 2.0;
        my = (my - height / 2.0) / zoom + height / 2.0;
        if (btn == 0) {
            PoliciaAlbanil.AlbSync s = PoliciaAlbanil.CL;
            for (int i = 0; i < 10; i++) {
                int nx = nodeX(i), y = nodeY(i);
                if (mx >= nx && mx <= nx + 24 && my >= y && my <= y + 24) {
                    if (i == 9) PoliciaClient.send(has(s, i) ? 10 : 44);
                    else if (i == 8) PoliciaClient.send(has(s, i) ? 11 : 43);
                    else if (i == 7) PoliciaClient.send(has(s, i) ? 15 : 42);
                    else if (i == 6) PoliciaClient.send(has(s, i) ? 14 : 41);
                    else if (i >= 3) {
                        if (i - 2 == s.hl + 1) PoliciaClient.send(40);
                        else if (has(s, i)) PoliciaClient.send(13);
                    } else PoliciaClient.send(has(s, i) ? 10 + i : 20 + i);
                    return true;
                }
            }
        }
        return super.mouseClicked(rmx, rmy, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        zoom = Math.max(0.5f, Math.min(2.0f, zoom + (float) delta * 0.1f));
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    /** Lista de habilidades en pantalla, arriba a la derecha. */
    static void hud(GuiGraphics g, Minecraft mc, PoliciaMod.Sync ms, int sw, int sh) {
        PoliciaAlbanil.AlbSync s = PoliciaAlbanil.CL;
        java.util.List<String> ls = new java.util.ArrayList<>();
        java.util.List<Integer> cs = new java.util.ArrayList<>();
        ls.add("OFICIO ALBANIL - XP " + ms.xp); cs.add(0xFFE8C040);
        String n1 = s.lv >= 3 ? "REFUGIO II" : (s.lv >= 2 ? "REFUGIO" : "MURO");
        ls.add((s.sel == 0 ? "> " : "  ") + "1. Pico de albanil" + (s.pl >= 1 ? " II" : "")); cs.add(s.sel == 0 ? 0xFFFFFFFF : 0xFFB0C4DE);
        boolean o1 = s.lv >= 1;
        ls.add((s.sel == 1 && o1 ? "> " : "  ") + "2. " + n1 + (o1 ? "" : "  [" + PoliciaAlbanil.COST + " XP]"));
        cs.add(o1 ? (s.sel == 1 ? 0xFFFFFFFF : 0xFFB0C4DE) : 0xFF707070);
        boolean o2 = s.hl >= 1;
        ls.add((s.sel == 2 && o2 ? "> " : "  ") + "3. AYUDANTE" + (o2 ? " " + s.hl : "  [" + PoliciaAlbanil.COST + " XP]"));
        cs.add(o2 ? (s.sel == 2 ? 0xFFFFFFFF : 0xFFB0C4DE) : 0xFF707070);
        boolean o3 = s.lu >= 1;
        ls.add((s.sel == 3 && o3 ? "> " : "  ") + "4. LINTERNA" + (o3 ? "" : "  [" + PoliciaAlbanil.COST + " XP]"));
        cs.add(o3 ? (s.sel == 3 ? 0xFFFFFFFF : 0xFFB0C4DE) : 0xFF707070);
        boolean o4 = s.tu >= 1;
        ls.add((s.sel == 4 && o4 ? "> " : "  ") + "5. TRACTOR" + (o4 ? "" : "  [" + PoliciaTractor.COST + " XP]"));
        cs.add(o4 ? (s.sel == 4 ? 0xFFFFFFFF : 0xFFB0C4DE) : 0xFF707070);
        String st;
        if (s.sel == 4 && o4) {
            if (s.ta > 0) st = "Tractor: " + (s.ta + 19) / 20 + " s (J: bajar o subir, Mayus+J: retirar)";
            else if (s.tc > 0) st = "Tractor en enfriamiento: " + (s.tc + 19) / 20 + " s";
            else st = "Tractor listo (J)";
        } else if (s.sel == 3 && o3) {
            if (s.lt > 0) st = "Linterna: " + (s.lt + 19) / 20 + " s (J o G: apagar)";
            else if (s.lc > 0) st = "Linterna en enfriamiento: " + (s.lc + 19) / 20 + " s";
            else st = "Linterna lista (J o G)";
        } else if (s.sel == 2 && o2) {
            String ord = PoliciaAyudante.ORDERS[Math.max(0, Math.min(2, s.mode))] + (s.mode == 1 ? (s.zl > 0 ? " " + s.zl + "x" + s.zw : " (X: area)") : "");
            if (s.ht > 0) st = "Ayudantes: " + (s.ht + 19) / 20 + " s - orden: " + ord + " (H)";
            else if (s.hc > 0) st = "Ayudantes en enfriamiento: " + (s.hc + 19) / 20 + " s";
            else st = "Ayudantes listos (J) - orden: " + ord + " (H)";
        } else if (s.sel == 0 || !o1) {
            if (s.pickT > 0) st = "Pico activo: " + (s.pickT + 19) / 20 + " s";
            else if (s.c0 > 0) st = "Pico en enfriamiento: " + (s.c0 + 19) / 20 + " s";
            else st = "Pico listo (J)";
        } else {
            if (s.structT > 0) st = n1 + " activo: " + (s.structT + 19) / 20 + " s" + (s.lv < 2 ? " - muros " + s.walls + "/3 (J: otro)" : "");
            else if (s.c1 > 0) st = n1 + " en enfriamiento: " + (s.c1 + 19) / 20 + " s";
            else st = n1 + " listo (J)";
        }
        ls.add(st); cs.add(0xFF80E0FF);
        float sc = 0.75f;
        int wmax = 0;
        for (String l : ls) wmax = Math.max(wmax, mc.font.width(l));
        g.pose().pushPose();
        g.pose().translate(sw - 6 - wmax * sc, 6.0f, 0.0f);
        g.pose().scale(sc, sc, 1.0f);
        for (int i = 0; i < ls.size(); i++) {
            int yy = i == 0 ? 0 : (i == ls.size() - 1 ? i * 10 + 6 : i * 10 + 2);
            g.drawString(mc.font, ls.get(i), 0, yy, cs.get(i), true);
        }
        g.pose().popPose();
    }
}
