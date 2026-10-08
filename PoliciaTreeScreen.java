package com.example.policia;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Pantalla de la rama de habilidades (se abre desde el boton junto al libro de recetas). */
public class PoliciaTreeScreen extends Screen {
    static final String[] SHORT = {"Escudo", "Tanque", "Jet"};
    int px, py;
    static final int PW = 230, PH = 150;

    public PoliciaTreeScreen() {
        super(Component.literal("Rama de habilidades"));
    }

    @Override
    protected void init() {
        px = (width - PW) / 2;
        py = (height - PH) / 2;
        addRenderableWidget(Button.builder(Component.literal("Usar habilidad"), b -> PoliciaClient.send(1))
                .bounds(px + 10, py + PH - 28, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> onClose())
                .bounds(px + PW - 70, py + PH - 28, 60, 20).build());
    }

    int nodeX(int i) { return px + 28 + i * 70; }
    int nodeY() { return py + 50; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(px - 2, py - 2, px + PW + 2, py + PH + 2, 0xFF3A4452);
        g.fill(px, py, px + PW, py + PH, 0xF0101820);
        PoliciaMod.Sync s = PoliciaClient.mine();
        int xp = s == null ? 0 : s.xp;
        int sel = s == null ? 0 : s.sel;
        int unlocked = Math.min(PoliciaMod.SKILLS.length, 1 + xp / PoliciaMod.XP_PER_NODE);
        g.drawCenteredString(font, "RAMA DE HABILIDADES - POLICIA", px + PW / 2, py + 8, 0xFFE8C040);
        g.drawCenteredString(font, "XP: " + xp + "   (una habilidad nueva cada " + PoliciaMod.XP_PER_NODE + " XP)", px + PW / 2, py + 22, 0xFFB0C4DE);
        for (int i = 0; i < PoliciaMod.SKILLS.length; i++) {
            int x = nodeX(i), y = nodeY();
            if (i + 1 < PoliciaMod.SKILLS.length) {
                g.fill(x + 30, y + 13, nodeX(i + 1) - 2, y + 15, i + 1 < unlocked ? 0xFF4C8DFF : 0xFF555555);
            }
            boolean open = i < unlocked;
            int border = (open && i == sel) ? 0xFFFFE040 : (open ? 0xFF4C8DFF : 0xFF555555);
            g.fill(x - 2, y - 2, x + 30, y + 30, border);
            g.fill(x, y, x + 28, y + 28, open ? 0xFF1B3A66 : 0xFF2A2A2A);
            if (i == 0) {
                g.renderItem(new ItemStack(PoliciaShield.SHIELD.get()), x + 6, y + 6);
            } else {
                g.drawCenteredString(font, open ? "!" : "?", x + 14, y + 10, open ? 0xFFFFFFFF : 0xFF777777);
            }
            g.drawCenteredString(font, SHORT[Math.min(i, SHORT.length - 1)], x + 14, y + 34, open ? 0xFFFFFFFF : 0xFF808080);
            if (!open) g.drawCenteredString(font, (i * PoliciaMod.XP_PER_NODE) + " XP", x + 14, y + 46, 0xFF808080);
            else if (i == sel) g.drawCenteredString(font, "elegida", x + 14, y + 46, 0xFFFFE040);
        }
        g.drawCenteredString(font, "Clic en una habilidad para elegirla (tecla J la usa)", px + PW / 2, py + 108, 0xFF909090);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        PoliciaMod.Sync s = PoliciaClient.mine();
        int xp = s == null ? 0 : s.xp;
        int unlocked = Math.min(PoliciaMod.SKILLS.length, 1 + xp / PoliciaMod.XP_PER_NODE);
        for (int i = 0; i < unlocked; i++) {
            if (mx >= nodeX(i) && mx <= nodeX(i) + 28 && my >= nodeY() && my <= nodeY() + 28) {
                PoliciaClient.send(10 + i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
