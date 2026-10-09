package com.example.policia;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Rama de habilidades del ladron: Carterismo, Silbido y Suerte (trebol de cuatro hojas). */
public class PoliciaLadronScreen extends Screen {
    static final int PW = 330, PH = 212;
    int px, py;
    Button bSil, bLuck;

    public PoliciaLadronScreen() {
        super(Component.literal("Rama del ladron"));
    }

    @Override
    protected void init() {
        px = (width - PW) / 2;
        py = (height - PH) / 2;
        bSil = Button.builder(Component.literal(""), b -> PoliciaClient.send(81)).bounds(px + PW - 112, py + 94, 100, 20).build();
        bLuck = Button.builder(Component.literal(""), b -> PoliciaClient.send(80)).bounds(px + PW - 112, py + 160, 100, 20).build();
        addRenderableWidget(bSil);
        addRenderableWidget(bLuck);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    /** Dibuja un trebol de cuatro hojas (cuatro cuadrados verdes y un tallo). */
    static void clover(GuiGraphics g, int x, int y, int s, int col) {
        int h = s / 2;
        g.fill(x + h / 2, y, x + h / 2 + h, y + h, col);
        g.fill(x + h + h / 2 - 1, y + h / 2, x + s, y + h / 2 + h, col);
        g.fill(x, y + h / 2, x + h, y + h / 2 + h, col);
        g.fill(x + h / 2, y + h, x + h / 2 + h, y + s, col);
        g.fill(x + h - 1, y + h - 1, x + h + 1, y + s + 3, 0xFF2E7D32);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        PoliciaMod.Sync s = PoliciaClient.mine();
        int lv = s == null ? 0 : Math.max(0, Math.min(4, s.rl));
        boolean sil = s != null && (s.un & 1) != 0;
        int xp = s == null ? 0 : s.xp;
        g.fill(px, py, px + PW, py + PH, 0xE0101018);
        g.fill(px, py, px + PW, py + 1, 0xFFB08A2E);
        g.fill(px, py + PH - 1, px + PW, py + PH, 0xFFB08A2E);
        g.drawString(font, "OFICIO LADRON  -  XP " + xp, px + 12, py + 10, 0xFFE8C040);

        int y0 = py + 30;
        g.renderItem(new ItemStack(PoliciaPlaca.GUANTE.get()), px + 12, y0 + 2);
        g.drawString(font, "CARTERISMO (siempre activa)", px + 36, y0, 0xFFFFFFFF);
        g.drawWordWrap(font, Component.literal("Con el modo ladron (B) activo, agachate (Mayus) detras de un jugador o mob y haz clic derecho. Si sale bien abres sus bolsillos y te llevas lo que quieras. Probabilidad ahora: " + PoliciaLadron.CHANCE[lv] + "%."), px + 36, y0 + 12, PW - 160, 0xFFB0C4DE);

        int y1 = py + 92;
        g.renderItem(new ItemStack(Items.GOAT_HORN), px + 12, y1 + 2);
        g.drawString(font, "SILBIDO (J)", px + 36, y1, sil ? 0xFFFFFFFF : 0xFF909090);
        g.drawWordWrap(font, Component.literal("Los mobs a 12 bloques miran para otro lado 7 s, quedan lentos y no te atacan: facilita acercarte. Enfriamiento: 15 s."), px + 36, y1 + 12, PW - 160, 0xFFB0C4DE);

        int y2 = py + 156;
        clover(g, px + 12, y2 + 1, 14, lv > 0 ? 0xFF43A047 : 0xFF3B5B3E);
        g.drawString(font, "SUERTE " + lv + "/4", px + 36, y2, lv > 0 ? 0xFFFFFFFF : 0xFF909090);
        g.drawWordWrap(font, Component.literal("Mejora el carterismo: 40%, 45%, 50% y 55%. Cada nivel reemplaza al anterior."), px + 36, y2 + 12, PW - 160, 0xFFB0C4DE);

        bSil.visible = !sil;
        bSil.setMessage(Component.literal("Desbloquear " + PoliciaMod.XP_PER_NODE + " XP"));
        bSil.active = xp >= PoliciaMod.XP_PER_NODE;
        bLuck.visible = lv < 4;
        bLuck.setMessage(Component.literal("Mejorar " + PoliciaMod.XP_PER_NODE * (lv + 1) + " XP"));
        bLuck.active = xp >= PoliciaMod.XP_PER_NODE * (lv + 1);
        super.render(g, mx, my, pt);
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
