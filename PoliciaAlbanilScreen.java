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
    static final String[] NAMES = {"Pico de albanil", "MURO", "REFUGIO"};
    static final String[] DESC = {
            "Te da un pico de diamante durante 7 s, con Fuerza I y Prisa minera I. No se puede soltar. Enfriamiento: 8 s.",
            "Levanta un muro de 3 de alto por 3 de largo frente a ti. Puedes hacer hasta 3 muros (pulsa J de nuevo para cada uno). Desaparecen a los 15 s. Enfriamiento: 5 s.",
            "Mejora de MURO: lo reemplaza. Casa sobre la superficie con puerta y piso de madera, cama y cofre, durante 35 s. Lo que dejes dentro (cofre, bloques, objetos) vuelve a estar en el mismo lugar cuando la invocas de nuevo. Enfriamiento: 40 s."};
    static final int[] CX = {174, 174, 174};
    static final int[] ROW = {0, 1, 2};
    int px, py;

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
        return i == 0 || lv >= i;
    }

    int nodeX(int i) { return px + LEFT_W + 8 + CX[i] - 12; }
    int nodeY(int i) { return py + 40 + ROW[i] * 70; }

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
        PoliciaMod.Sync ms = PoliciaClient.mine();
        PoliciaAlbanil.AlbSync s = PoliciaAlbanil.CL;
        int xp = ms == null ? 0 : ms.xp;
        int sel = s.sel == 1 ? (s.lv >= 2 ? 2 : 1) : 0;
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
        for (int i = 1; i < 3; i++) {
            int col = has(s, i) ? 0xFFE0C070 : 0xFF6A5A36;
            int x0 = nodeX(i - 1) + 11, y0 = nodeY(i - 1) + 44, y1 = nodeY(i);
            g.fill(x0, y0, x0 + 2, y1, col);
        }
        int hov = -1;
        for (int i = 0; i < 3; i++) {
            int nx = nodeX(i), y = nodeY(i);
            boolean open = has(s, i);
            boolean hover = mx >= nx && mx <= nx + 24 && my >= y && my <= y + 24;
            if (hover) hov = i;
            int border = (open && i == sel) ? 0xFFFFFFFF : (hover ? 0xFFFFE9A0 : 0xFF6B4A0C);
            g.fill(nx - 1, y - 1, nx + 25, y + 25, border);
            g.fill(nx, y, nx + 24, y + 24, open ? 0xFFC8921C : 0xFF8A6612);
            if (i == 0) pickIcon(g, nx + 4, y + 4);
            else if (i == 1) wallIcon(g, nx + 4, y + 4);
            else houseIcon(g, nx + 4, y + 4);
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
            g.drawString(font, "Bloqueada - cuesta " + PoliciaAlbanil.COST + " XP", bx0 + 5, by1 - 11, 0xFFE8B830, false);
        } else if (show == sel) {
            g.drawString(font, "Elegida (tecla J)", bx0 + 5, by1 - 11, 0xFF60E060, false);
        } else {
            g.drawString(font, "Clic para elegir", bx0 + 5, by1 - 11, 0xFF9AA4B0, false);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            PoliciaAlbanil.AlbSync s = PoliciaAlbanil.CL;
            for (int i = 0; i < 3; i++) {
                int nx = nodeX(i), y = nodeY(i);
                if (mx >= nx && mx <= nx + 24 && my >= y && my <= y + 24) {
                    PoliciaClient.send(has(s, i) ? 10 + i : 20 + i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    /** Lista de habilidades en pantalla, arriba a la derecha. */
    static void hud(GuiGraphics g, Minecraft mc, PoliciaMod.Sync ms, int sw, int sh) {
        PoliciaAlbanil.AlbSync s = PoliciaAlbanil.CL;
        java.util.List<String> ls = new java.util.ArrayList<>();
        java.util.List<Integer> cs = new java.util.ArrayList<>();
        ls.add("OFICIO ALBANIL - XP " + ms.xp); cs.add(0xFFE8C040);
        String n1 = s.lv >= 2 ? "REFUGIO" : "MURO";
        ls.add((s.sel == 0 ? "> " : "  ") + "1. Pico de albanil"); cs.add(s.sel == 0 ? 0xFFFFFFFF : 0xFFB0C4DE);
        boolean o1 = s.lv >= 1;
        ls.add((s.sel == 1 && o1 ? "> " : "  ") + "2. " + n1 + (o1 ? "" : "  [" + PoliciaAlbanil.COST + " XP]"));
        cs.add(o1 ? (s.sel == 1 ? 0xFFFFFFFF : 0xFFB0C4DE) : 0xFF707070);
        String st;
        if (s.sel == 0 || !o1) {
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
