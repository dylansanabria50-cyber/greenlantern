package com.example.policia;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Tablon del comisario con aspecto de pergamino viejo y arrugado. */
public class PoliciaBoardScreen extends Screen {
    static final int PW = 320, PH = 240;
    final PoliciaBoard.Open o;
    int px, py;
    final int[][] rects = new int[7][4];

    public PoliciaBoardScreen(PoliciaBoard.Open o) {
        super(Component.literal("Tablon del comisario"));
        this.o = o;
    }

    public static void open(PoliciaBoard.Open m) {
        Minecraft.getInstance().setScreen(new PoliciaBoardScreen(m));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        px = (width - PW) / 2;
        py = (height - PH) / 2;
        for (int i = 0; i < 3; i++) rects[i] = new int[]{px + 22, py + 60 + i * 42, PW - 44, 38};
        rects[4] = new int[]{px + 18, py + 190, 70, 18};
        rects[3] = new int[]{px + 94, py + 190, 70, 18};
        rects[5] = new int[]{px + 170, py + 190, 70, 18};
        rects[6] = new int[]{px + 246, py + 190, 56, 18};
    }

    static void polyFill(GuiGraphics g, double[] xs, double[] ys, int col) {
        int n = xs.length;
        double mn = 1e9, mx = -1e9;
        for (double y : ys) {
            mn = Math.min(mn, y);
            mx = Math.max(mx, y);
        }
        for (int y = (int) Math.floor(mn); y <= (int) Math.ceil(mx); y++) {
            double yy = y + 0.5;
            List<Double> xi = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                if ((ys[i] <= yy && ys[j] > yy) || (ys[j] <= yy && ys[i] > yy)) {
                    xi.add(xs[i] + (yy - ys[i]) / (ys[j] - ys[i]) * (xs[j] - xs[i]));
                }
            }
            Collections.sort(xi);
            for (int k = 0; k + 1 < xi.size(); k += 2) g.fill((int) Math.floor(xi.get(k)), y, (int) Math.ceil(xi.get(k + 1)), y + 1, col);
        }
    }

    void paper(GuiGraphics g) {
        Random r = new Random(77L);
        int[] lef = new int[PH], rig = new int[PH];
        for (int i = 0; i < PH; i++) {
            lef[i] = r.nextInt(4);
            rig[i] = r.nextInt(4);
        }
        // borde irregular (papel rasgado)
        for (int y = 0; y < PH; y++) {
            g.fill(px + lef[y], py + y, px + PW - rig[y], py + y + 1, 0xFFC9A96A);
        }
        // cuerpo
        g.fill(px + 5, py + 5, px + PW - 5, py + PH - 5, 0xFFE4D1A0);
        g.fill(px + 10, py + 10, px + PW - 10, py + PH - 10, 0xFFEBDBB0);
        // manchas
        for (int i = 0; i < 9; i++) {
            int cx = px + 12 + r.nextInt(PW - 40), cy = py + 12 + r.nextInt(PH - 40), s = 8 + r.nextInt(18);
            g.fill(cx, cy + s / 4, cx + s, cy + s - s / 4, 0x18805A20);
            g.fill(cx + s / 4, cy, cx + s - s / 4, cy + s, 0x18805A20);
        }
        // sombras de los bordes
        for (int i = 0; i < 14; i++) {
            int a = 0x0E503010;
            g.fill(px + 5 + i, py + 5 + i, px + 6 + i, py + PH - 5 - i, a);
            g.fill(px + PW - 6 - i, py + 5 + i, px + PW - 5 - i, py + PH - 5 - i, a);
            g.fill(px + 5 + i, py + 5 + i, px + PW - 5 - i, py + 6 + i, a);
            g.fill(px + 5 + i, py + PH - 6 - i, px + PW - 5 - i, py + PH - 5 - i, a);
        }
        // arrugas
        for (int i = 0; i < 16; i++) {
            int x = px + 10 + r.nextInt(PW - 20), y = py + 10 + r.nextInt(PH - 20);
            int len = 30 + r.nextInt(90);
            boolean horiz = r.nextBoolean();
            int c1 = 0x30705030, c2 = 0x40FFF2D0;
            int cx = x, cy = y;
            for (int k = 0; k < len; k++) {
                if (cx < px + 8 || cx > px + PW - 8 || cy < py + 8 || cy > py + PH - 8) break;
                g.fill(cx, cy, cx + 1, cy + 1, c1);
                g.fill(cx + (horiz ? 0 : 1), cy + (horiz ? 1 : 0), cx + (horiz ? 1 : 2), cy + (horiz ? 2 : 1), c2);
                if (horiz) {
                    cx++;
                    if (r.nextInt(4) == 0) cy += r.nextInt(3) - 1;
                } else {
                    cy++;
                    if (r.nextInt(4) == 0) cx += r.nextInt(3) - 1;
                }
            }
        }
        // dibujo de fondo: estrella de comisario desvaida
        double cx = px + PW / 2.0, cy = py + PH / 2.0 + 6;
        double[] xs = new double[10], ys = new double[10];
        for (int i = 0; i < 10; i++) {
            double ang = Math.toRadians(-90 + 36 * i);
            double rad = i % 2 == 0 ? 78 : 32;
            xs[i] = cx + Math.cos(ang) * rad;
            ys[i] = cy + Math.sin(ang) * rad;
        }
        polyFill(g, xs, ys, 0x22604020);
        for (int i = 0; i < 10; i += 2) {
            double ang = Math.toRadians(-90 + 36 * i);
            int bx = (int) (cx + Math.cos(ang) * 82), by = (int) (cy + Math.sin(ang) * 82);
            g.fill(bx - 3, by - 3, bx + 3, by + 3, 0x22604020);
        }
        // chinchetas
        for (int[] p : new int[][]{{14, 14}, {PW - 18, 14}, {14, PH - 18}, {PW - 18, PH - 18}}) {
            g.fill(px + p[0], py + p[1], px + p[0] + 4, py + p[1] + 4, 0xFF8B1A1A);
            g.fill(px + p[0] + 1, py + p[1] + 1, px + p[0] + 2, py + p[1] + 2, 0xFFE06060);
        }
    }

    boolean in(int i, int mx, int my) {
        int[] r = rects[i];
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    boolean enabled(int i) {
        switch (i) {
            case 0: case 1: case 2: return true;
            case 3: return (o.flags & 2) != 0;
            case 4: return (o.flags & 1) != 0;
            case 5: return (o.flags & 8) != 0;
            default: return o.active > 0;
        }
    }

    int type(int i) { return i == 0 ? o.a : (i == 1 ? o.b : o.c); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        paper(g);
        int ink = 0xFF3B2410, soft = 0xFF6B4A28;
        g.pose().pushPose();
        g.pose().translate(px + PW / 2.0, py + 20, 0);
        g.pose().scale(1.5f, 1.5f, 1.0f);
        String t = "TABLON DEL COMISARIO";
        g.drawString(font, t, -font.width(t) / 2, 0, ink, false);
        g.pose().popPose();
        String sub = o.active > 0 ? "Mision en curso: " + PoliciaMision.TITLE[o.active] : "Elija un encargo, agente";
        g.drawString(font, sub, px + PW / 2 - font.width(sub) / 2, py + 42, o.active > 0 ? 0xFF8B1A1A : soft, false);
        g.fill(px + 22, py + 53, px + PW - 22, py + 54, 0x60503010);
        for (int i = 0; i < 3; i++) {
            int[] r = rects[i];
            int ty = type(i);
            boolean h = in(i, mx, my);
            if (h) g.fill(r[0] - 2, r[1] - 1, r[0] + r[2] + 2, r[1] + r[3] + 1, 0x40A07030);
            g.fill(r[0] - 2, r[1] + r[3] + 1, r[0] + r[2] + 2, r[1] + r[3] + 2, 0x30503010);
            g.drawString(font, (i + 1) + ". " + PoliciaMision.TITLE[ty], r[0] + 2, r[1] + 2, ink, false);
            g.drawWordWrap(font, Component.literal(PoliciaMision.DESC[ty]), r[0] + 2, r[1] + 13, r[2] - 4, soft);
            int xp = PoliciaMision.XP[ty];
            String rw = xp + " XP  " + PoliciaMision.TIME[ty] / 1200 + " min";
            g.drawString(font, rw, r[0] + r[2] - font.width(rw) - 2, r[1] + 2, 0xFF2E6B2E, false);
        }
        String[] names = {null, null, null, "Cerebro", "Jefe banda", "Casino", "Cancelar"};
        for (int i = 3; i < 7; i++) {
            int[] r = rects[i];
            boolean en = enabled(i);
            boolean h = en && in(i, mx, my);
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], h ? 0xFFB88A3E : (en ? 0xFFC9A05A : 0xFFB5A586));
            g.fill(r[0], r[1], r[0] + r[2], r[1] + 1, 0xFF6B4A28);
            g.fill(r[0], r[1] + r[3] - 1, r[0] + r[2], r[1] + r[3], 0xFF6B4A28);
            g.fill(r[0], r[1], r[0] + 1, r[1] + r[3], 0xFF6B4A28);
            g.fill(r[0] + r[2] - 1, r[1], r[0] + r[2], r[1] + r[3], 0xFF6B4A28);
            String s = names[i];
            if (i == 3 && (o.flags & 4) != 0) s = "Cerebro: hecho";
            g.drawString(font, s, r[0] + r[2] / 2 - font.width(s) / 2, r[1] + 5, en ? ink : 0xFF8A7A5A, false);
        }
        String st = "Cumplidas: " + o.done + "   Jefes: " + o.bd + (o.need > 0 ? "   Faltan " + o.need + " para el jefe de banda" : "");
        g.drawString(font, st, px + PW / 2 - font.width(st) / 2, py + 214, soft, false);
        for (int i = 3; i < 7; i++) {
            if (in(i, mx, my)) {
                String tip = i == 3 ? "Mision especial: El Cerebro (necesita 1 jefe cazado)" : i == 4 ? "Cazar al jefe de banda" : i == 5 ? (enabled(5) ? "Rescate en el casino subterraneo" : "No se conoce ningun casino todavia") : "Abandonar la mision actual";
                g.drawString(font, tip, px + PW / 2 - font.width(tip) / 2, py + 226, 0xFF8B1A1A, false);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        for (int i = 0; i < 7; i++) {
            if (in(i, (int) mx, (int) my) && enabled(i)) {
                PoliciaMod.NET.sendToServer(new PoliciaBoard.Pick(i));
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }
}
