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

/** Rama de habilidades del ladron: Carterismo, Silbido, Suerte y las habilidades de uso (Humo, Salto, Instinto, Polvo). */
public class PoliciaLadronScreen extends Screen {
    static final int PW = 460, PH = 340, LEFT_W = 104;
    static final String[] NAMES = {"Carterismo", "Silbido", "Suerte", "Humo", "Salto", "Instinto", "Polvo", "Ganzua", "Escape"};
    static final String[] DESC = {
            "Con el modo ladron (B) activo, agachate (Mayus) detras de un jugador o mob y haz clic derecho. Si sale bien abres sus bolsillos y te llevas lo que quieras. Siempre activa.",
            "Los mobs a 12 bloques miran para otro lado 7 s, quedan lentos y no te atacan: facilita acercarte por la espalda. Tecla J. Enfriamiento: 15 s.",
            "Trebol de cuatro hojas: mejora la probabilidad del carterismo a 40%, 45%, 50% y 55%. Cada nivel reemplaza al anterior. Clic para mejorar.",
            "Bomba de humo: te vuelves invisible 8 s, corres mas rapido y los mobs que te perseguian te pierden. Se elige con K y se usa con J. Enfriamiento: 30 s. Requiere Silbido.",
            "Salto de sombra: das un gran salto hacia donde miras y caes sin danio. Se elige con K y se usa con J. Enfriamiento: 12 s. Requiere Silbido.",
            "Sentido de ladron: todo ser vivo a 24 bloques brilla 10 s, incluso a traves de las paredes. Se elige con K y se usa con J. Enfriamiento: 25 s. Requiere Suerte nivel 1.",
            "Polvo cegador: los mobs a 6 bloques quedan ciegos, lentos y debiles 7 s y pierden su objetivo. Se elige con K y se usa con J. Enfriamiento: 20 s. Requiere Suerte nivel 1.",
            "Ganzua: en las aldeas con comisaria hay cofres con llave (sin abrir) que nadie mas puede abrir, y puertas de hierro. Agachate (Mayus) y clic derecho: 50% de exito, +10% por cada nivel de Suerte. Si falla salta la alarma y la policia te busca. Requiere Suerte nivel 1.",
            "Escape: durante 10 s te vuelves invisible y mas rapido, la policia te pierde de vista y, si no cometes ningun delito, tu nivel de Buscado vuelve a cero. Se elige con K y se usa con J. Enfriamiento: 90 s. Requiere Silbido."};
    static final int[] CX = {174, 90, 258, 40, 140, 208, 308, 258, 90};
    static final int[] ROW = {0, 1, 1, 2, 2, 2, 2, 2, 2};
    static final int[] PRE = {-1, 0, 0, 1, 1, 2, 2, 2, 1};
    static final int NN = 9;
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

    boolean open(int i, boolean sil, int lv, int mask) {
        if (i == 0) return true;
        if (i == 1) return sil;
        if (i == 2) return lv > 0;
        return ((mask >> (i - 2)) & 1) == 1;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        PoliciaMod.Sync s = PoliciaClient.mine();
        int lv = s == null ? 0 : Math.max(0, Math.min(4, s.rl));
        boolean sil = s != null && (s.un & 1) != 0;
        int mask = s == null ? 0 : s.un;
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
        for (int i = 1; i < NN; i++) {
            int col = open(i, sil, lv, mask) ? 0xFFE070D8 : 0xFF6A3A66;
            int x0 = nodeX(PRE[i]) + 11, x1 = nodeX(i) + 11;
            int y0 = nodeY(PRE[i]) + 44, y1 = nodeY(i);
            int ym = y1 - 10;
            g.fill(x0, y0, x0 + 2, ym + 2, col);
            g.fill(Math.min(x0, x1), ym, Math.max(x0, x1) + 2, ym + 2, col);
            g.fill(x1, ym, x1 + 2, y1, col);
        }
        int hov = -1;
        for (int i = 0; i < NN; i++) {
            int nx = nodeX(i), y = nodeY(i);
            boolean open = open(i, sil, lv, mask);
            boolean hover = mx >= nx && mx <= nx + 24 && my >= y && my <= y + 24;
            if (hover) hov = i;
            int border = hover ? 0xFFFFE9A0 : 0xFF6B4A0C;
            g.fill(nx - 1, y - 1, nx + 25, y + 25, border);
            g.fill(nx, y, nx + 24, y + 24, open ? 0xFFC8921C : 0xFF8A6612);
            if (i == 0) g.renderItem(new ItemStack(PoliciaPlaca.GUANTE.get()), nx + 4, y + 4);
            else if (i == 1) g.renderItem(new ItemStack(Items.GOAT_HORN), nx + 4, y + 4);
            else if (i == 2) clover(g, nx + 5, y + 4, 14, lv > 0 ? 0xFF43A047 : 0xFF3B5B3E);
            else g.renderItem(new ItemStack(i == 3 ? Items.GUNPOWDER : i == 4 ? Items.FEATHER : i == 5 ? Items.ENDER_EYE : i == 6 ? Items.SUGAR : i == 7 ? Items.TRIPWIRE_HOOK : Items.LEATHER_BOOTS), nx + 4, y + 4);
            if (i == 1 && !sil) {
                g.fill(nx, y, nx + 24, y + 24, 0x99000000);
            }
            if ((i == 2 && lv == 0) || (i >= 3 && !open)) {
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
        } else if (show >= 3) {
            boolean op = open(show, sil, lv, mask);
            g.drawString(font, op ? (show == 7 ? "Desbloqueada (agachado + clic derecho)" : "Desbloqueada (K elige, J usa)") : "Bloqueada - cuesta " + PoliciaMod.XP_PER_NODE * 2 + " XP", bx0 + 5, by1 - 11, op ? 0xFF60E060 : 0xFFE8B830, false);
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
            int mask = s == null ? 0 : s.un;
            for (int i = 1; i < NN; i++) {
                int nx = nodeX(i), y = nodeY(i);
                if (mx >= nx && mx <= nx + 24 && my >= y && my <= y + 24) {
                    if (i == 1 && !sil) PoliciaClient.send(81);
                    else if (i == 2) PoliciaClient.send(80);
                    else if (i >= 3 && ((mask >> (i - 2)) & 1) == 0) PoliciaClient.send(82 + (i - 3));
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    /** Lista de habilidades en pantalla, arriba a la derecha. */
    static void hud(GuiGraphics g, Minecraft mc, PoliciaMod.Sync s, int sw, int sh) {
        int lv = Math.max(0, Math.min(4, s.rl));
        List<String> ls = new java.util.ArrayList<>();
        List<Integer> cs = new java.util.ArrayList<>();
        ls.add("OFICIO LADRON - XP " + s.xp);
        cs.add(0xFFE8C040);
        ls.add("Carterismo: Mayus + clic der. (" + PoliciaLadron.CHANCE[lv] + "%)");
        cs.add(0xFFFFFFFF);
        if (s.tcd > 0) {
            ls.add("BUSCADO " + "*".repeat(Math.min(5, s.tcd)));
            cs.add(0xFFFF5050);
        }
        int[] cds = {s.c5, s.c6, s.c7, s.c8, s.c9, 0, s.rcd};
        boolean any = false;
        for (int a = 0; a < 7; a++) {
            if (((s.un >> a) & 1) == 0) continue;
            any = true;
            if (a == 5) {
                ls.add("  Ganzua: agachado + clic");
                cs.add(0xFFB0D8B8);
                continue;
            }
            String pre = a == s.sel ? "> " : "  ";
            if (cds[a] > 0) {
                ls.add(pre + PoliciaLadron.AN[a] + ": " + (cds[a] + 19) / 20 + " s");
                cs.add(0xFFFF8060);
            } else {
                ls.add(pre + PoliciaLadron.AN[a] + " listo");
                cs.add(a == s.sel ? 0xFF80FF90 : 0xFFB0D8B8);
            }
        }
        if (any) {
            ls.add("J usar - K cambiar");
            cs.add(0xFF9AA4B0);
        } else {
            ls.add("Habilidades: desbloquea el Silbido [" + PoliciaMod.XP_PER_NODE + " XP]");
            cs.add(0xFF707070);
        }
        int w = 0;
        for (String l : ls) w = Math.max(w, mc.font.width(l));
        int x = sw - w - 8, y = 8;
        g.fill(x - 4, y - 3, sw - 4, y + ls.size() * 11 + 1, 0x90000000);
        for (int i = 0; i < ls.size(); i++) g.drawString(mc.font, ls.get(i), x, y + i * 11, cs.get(i));
    }
}
