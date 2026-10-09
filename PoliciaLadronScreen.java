package com.example.policia;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** Rama de habilidades del ladron (mismo estilo que la del policia): Carterismo, Silbido y Suerte. */
public class PoliciaLadronScreen extends Screen {
    static final int PW = 460, PH = 340, LEFT_W = 104;
    static final String[] NAMES = {"Carterismo", "Silbido", "Suerte"};
    static final String[] DESC = {
            "Con el modo ladron (B) activo, agachate (Mayus) detras de un jugador o mob y haz clic derecho. Si sale bien abres sus bolsillos y te llevas lo que quieras. Siempre activa.",
            "Los mobs a 12 bloques miran para otro lado 7 s, quedan lentos y no te atacan: facilita acercarte por la espalda. Tecla J. Enfriamiento: 15 s.",
            "Trebol de cuatro hojas: mejora la probabilidad del carterismo a 40%, 45%, 50% y 55%. Cada nivel reemplaza al anterior. Clic para mejorar."};
    static final int[] CX = {174, 90, 258};
    static final int[] ROW = {0, 1, 1};
    static final int[] PRE = {-1, 0, 0};
    int px, py;

    public PoliciaLadronScreen() {
        super(Component.literal("Rama del ladron"));
    }

    @Override
    protected void init() {
        px = (width - PW) / 2;
        py = (height - PH) / 2;
        addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> onClose())
                .bounds(px + PW - 66, py + PH + 4, 60, 20).build());
    }

    @Override
    public boolean isPauseScreen() { return false; }

    int nodeX(int i) { return px + LEFT_W + 8 + CX[i] - 12; }

    int nodeY(int i) { return py + 40 + ROW[i] * 70; }

    static void panel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF555C66);
        g.fill(x0, y0, x1, y1, 0xFF0A0C12);
    }

    void tag(GuiGraphics g, String text, int x, int y) {
        int w = font.width(text) + 8;
        g.fill(x - 1, y - 1, x + w + 1, y + 13, 0xFF555C66);
        g.fill(x, y, x + w, y + 12, 0xFF8A8F99);
        g.drawString(font, text, x + 4, y + 2, 0xFFFFFFFF, true);
    }

    /** Dibuja un trebol de cuatro hojas (cuatro corazones verdes y un tallo). */
    static void clover(GuiGraphics g, int x, int y, int s, int col) {
        int h = s / 2;
        g.fill(x + h / 2, y, x + h / 2 + h, y + h, col);
        g.fill(x + h + h / 2 - 1, y + h / 2, x + s, y + h / 2 + h, col);
        g.fill(x, y + h / 2, x + h, y + h / 2 + h, col);
        g.fill(x + h / 2, y + h, x + h / 2 + h, y + s, col);
        g.fill(x + h - 1, y + h - 1, x + h + 1, y + s + 3, 0xFF2E7D32);
    }

    boolean has(int i, boolean sil) {
        return i == 0 || (i == 1 && sil) || i == 2;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        PoliciaMod.Sync s = PoliciaClient.mine();
        int lv = s == null ? 0 : Math.max(0, Math.min(4, s.rl));
        boolean sil = s != null && (s.un & 1) != 0;
        int xp = s == null ? 0 : s.xp;
        panel(g, px, py, px + LEFT_W, py + PH);
        tag(g, "Ladron", px + 6, py - 6);
        if (minecraft != null && minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, px + LEFT_W / 2, py + PH - 30, 55,
                    (px + LEFT_W / 2) - mx, (py + 60) - my, minecraft.player);
        }
        int rx = px + LEFT_W + 8;
        panel(g, rx, py, px + PW, py + PH);
        tag(g, "Habilidades", rx + 6, py - 6);
        g.drawString(font, "XP: " + xp, rx + 8, py + 10, 0xFFE8C040, true);
        g.drawString(font, "Desbloquear / mejorar: clic izquierdo en el icono", rx + 8, py + 20, 0xFF9AA4B0, false);
        for (int i = 1; i < 3; i++) {
            int col = has(i, sil) && (i != 2 || lv > 0 || true) ? 0xFFE070D8 : 0xFF6A3A66;
            if (i == 1 && !sil) col = 0xFF6A3A66;
            if (i == 2 && lv == 0) col = 0xFF6A3A66;
            int x0 = nodeX(PRE[i]) + 11, x1 = nodeX(i) + 11;
            int y0 = nodeY(PRE[i]) + 44, y1 = nodeY(i);
            int ym = y1 - 10;
            g.fill(x0, y0, x0 + 2, ym + 2, col);
            g.fill(Math.min(x0, x1), ym, Math.max(x0, x1) + 2, ym + 2, col);
            g.fill(x1, ym, x1 + 2, y1, col);
        }
        int hov = -1;
        for (int i = 0; i < 3; i++) {
            int nx = nodeX(i), y = nodeY(i);
            boolean open = i == 0 || (i == 1 && sil) || (i == 2 && lv > 0);
            boolean hover = mx >= nx && mx <= nx + 24 && my >= y && my <= y + 24;
            if (hover) hov = i;
            int border = hover ? 0xFFFFE9A0 : 0xFF6B4A0C;
            g.fill(nx - 1, y - 1, nx + 25, y + 25, border);
            g.fill(nx, y, nx + 24, y + 24, open ? 0xFFC8921C : 0xFF8A6612);
            if (i == 0) g.renderItem(new ItemStack(PoliciaPlaca.GUANTE.get()), nx + 4, y + 4);
            else if (i == 1) g.renderItem(new ItemStack(Items.GOAT_HORN), nx + 4, y + 4);
            else clover(g, nx + 5, y + 4, 14, lv > 0 ? 0xFF43A047 : 0xFF3B5B3E);
            if (i == 1 && !sil) {
                g.fill(nx, y, nx + 24, y + 24, 0x99000000);
            }
            if (i == 2 && lv == 0) {
                g.fill(nx, y, nx + 24, y + 24, 0x55000000);
            }
            List<FormattedCharSequence> nl = font.split(FormattedText.of(NAMES[i]), 64);
            for (int k = 0; k < nl.size() && k < 2; k++) {
                g.drawCenteredString(font, nl.get(k), nx + 12, y + 27 + k * 9, open ? 0xFFFFFFFF : 0xFFA0A0A0);
            }
            if (i == 2) g.drawCenteredString(font, "Nv " + lv + "/4", nx + 12, y - 10, 0xFFE8C040);
        }
        int show = hov >= 0 ? hov : 0;
        int bx0 = rx + 6, by0 = py + 258, bx1 = px + PW - 6, by1 = py + PH - 6;
        panel(g, bx0, by0, bx1, by1);
        g.drawString(font, NAMES[show], bx0 + 5, by0 + 4, 0xFFFFFFFF, true);
        List<FormattedCharSequence> lines = font.split(FormattedText.of(DESC[show]), bx1 - bx0 - 10);
        for (int k = 0; k < lines.size() && k < 5; k++) {
            g.drawString(font, lines.get(k), bx0 + 5, by0 + 15 + k * 9, 0xFFC8D0DC, false);
        }
        if (show == 0) {
            g.drawString(font, "Probabilidad actual: " + PoliciaLadron.CHANCE[lv] + "%", bx0 + 5, by1 - 11, 0xFF60E060, false);
        } else if (show == 1) {
            g.drawString(font, sil ? "Desbloqueada (tecla J)" : "Bloqueada - cuesta " + PoliciaMod.XP_PER_NODE + " XP", bx0 + 5, by1 - 11, sil ? 0xFF60E060 : 0xFFE8B830, false);
        } else {
            g.drawString(font, lv >= 4 ? "Nivel maximo" : "Siguiente nivel: " + PoliciaLadron.CHANCE[lv + 1] + "% - cuesta " + PoliciaMod.XP_PER_NODE * (lv + 1) + " XP", bx0 + 5, by1 - 11, 0xFFE8B830, false);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            PoliciaMod.Sync s = PoliciaClient.mine();
            boolean sil = s != null && (s.un & 1) != 0;
            for (int i = 1; i < 3; i++) {
                int nx = nodeX(i), y = nodeY(i);
                if (mx >= nx && mx <= nx + 24 && my >= y && my <= y + 24) {
                    if (i == 1 && !sil) PoliciaClient.send(81);
                    else if (i == 2) PoliciaClient.send(80);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    /** Lista de habilidades en pantalla, arriba a la derecha. */
    static void hud(GuiGraphics g, Minecraft mc, PoliciaMod.Sync s, int sw, int sh) {
        int lv = Math.max(0, Math.min(4, s.rl));
        boolean sil = (s.un & 1) != 0;
        String[] ls = new String[3];
        int[] cs = new int[3];
        ls[0] = "OFICIO LADRON - XP " + s.xp;
        cs[0] = 0xFFE8C040;
        ls[1] = "Carterismo: Mayus + clic der. (" + PoliciaLadron.CHANCE[lv] + "%)";
        cs[1] = 0xFFFFFFFF;
        if (!sil) {
            ls[2] = "Silbido (J) [" + PoliciaMod.XP_PER_NODE + " XP]";
            cs[2] = 0xFF707070;
        } else if (s.c5 > 0) {
            ls[2] = "Silbido en enfriamiento: " + (s.c5 + 19) / 20 + " s";
            cs[2] = 0xFFFF8060;
        } else {
            ls[2] = "Silbido listo (J)";
            cs[2] = 0xFF80FF90;
        }
        int w = 0;
        for (String l : ls) w = Math.max(w, mc.font.width(l));
        int x = sw - w - 8, y = 8;
        g.fill(x - 4, y - 3, sw - 4, y + ls.length * 11 + 1, 0x90000000);
        for (int i = 0; i < ls.length; i++) g.drawString(mc.font, ls[i], x, y + i * 11, cs[i]);
    }
}
