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

/** Pantalla de habilidades: personaje a la izquierda, rama de habilidades a la derecha. */
public class PoliciaTreeScreen extends Screen {
    static final int PW = 340, PH = 200, LEFT_W = 104;
    static final String[] NAMES = {"Escudo balistico", "Tanque", "Jet de combate"};
    static final String[] DESC = {
            "Despliega un escudo antidisturbios durante 12 s. Bloquea los golpes de frente.",
            "Invoca un tanque bajo tus pies. Apunta con la mira y dispara con clic derecho una sola vez; luego se retira. Enfriamiento: 30 s.",
            "Invoca un jet de combate pilotable. (Proximamente)"};
    int px, py;

    public PoliciaTreeScreen() {
        super(Component.literal("Rama de habilidades"));
    }

    @Override
    protected void init() {
        px = (width - PW) / 2;
        py = (height - PH) / 2;
        addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> onClose())
                .bounds(px + PW - 66, py + PH + 4, 60, 20).build());
    }

    static ItemStack icon(int i) {
        if (i == 0) return new ItemStack(PoliciaShield.SHIELD.get());
        if (i == 1) return new ItemStack(Items.TNT_MINECART);
        return new ItemStack(Items.ELYTRA);
    }

    static boolean has(PoliciaMod.Sync s, int i) {
        return i == 0 || (s != null && ((s.un >> i) & 1) == 1);
    }

    int nodeX() { return px + LEFT_W + 24; }
    int nodeY(int i) { return py + 34 + i * 52; }

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

    /** Icono con forma de tanque (16x16). */
    static void tankIcon(GuiGraphics g, int x, int y) {
        g.fill(x + 1, y + 10, x + 15, y + 14, 0xFF26292B);   // orugas
        g.fill(x + 2, y + 11, x + 14, y + 13, 0xFF4A4F52);
        for (int k = 0; k < 4; k++) g.fill(x + 3 + k * 3, y + 11, x + 4 + k * 3, y + 13, 0xFF8A9096); // ruedas
        g.fill(x + 2, y + 7, x + 14, y + 10, 0xFF5E7040);    // casco
        g.fill(x + 2, y + 7, x + 14, y + 8, 0xFF7C9156);
        g.fill(x + 5, y + 4, x + 11, y + 7, 0xFF6B7F48);     // torreta
        g.fill(x + 5, y + 4, x + 11, y + 5, 0xFF8AA062);
        g.fill(x + 7, y + 2, x + 9, y + 4, 0xFF3D4A28);      // escotilla
        g.fill(x + 11, y + 5, x + 16, y + 6, 0xFF2E3820);    // canon
        g.fill(x + 15, y + 4, x + 16, y + 7, 0xFF1F2616);    // boca del canon
    }

    void padlock(GuiGraphics g, int x, int y) {
        g.fill(x + 8, y + 3, x + 10, y + 11, 0xFFD8D8D8);
        g.fill(x + 14, y + 3, x + 16, y + 11, 0xFFD8D8D8);
        g.fill(x + 8, y + 3, x + 16, y + 5, 0xFFD8D8D8);
        g.fill(x + 6, y + 10, x + 18, y + 20, 0xFFE8B830);
        g.fill(x + 11, y + 13, x + 13, y + 17, 0xFF2A2A2A);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        PoliciaMod.Sync s = PoliciaClient.mine();
        int xp = s == null ? 0 : s.xp;
        int sel = s == null ? 0 : s.sel;
        // panel izquierdo: personaje
        panel(g, px, py, px + LEFT_W, py + PH);
        tag(g, "Policia", px + 6, py - 6);
        if (minecraft != null && minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, px + LEFT_W / 2, py + PH - 30, 55,
                    (px + LEFT_W / 2) - mx, (py + 60) - my, minecraft.player);
        }
        // panel derecho: habilidades
        int rx = px + LEFT_W + 8;
        panel(g, rx, py, px + PW, py + PH);
        tag(g, "Habilidades", rx + 6, py - 6);
        g.drawString(font, "XP disponible: " + xp, rx + 8, py + 10, 0xFFE8C040, true);
        g.drawString(font, "Desbloquear: clic izquierdo (" + PoliciaMod.XP_PER_NODE + " XP)", rx + 8, py + 20, 0xFF9AA4B0, false);
        int nx = nodeX();
        for (int i = 0; i < PoliciaMod.SKILLS.length; i++) {
            int y = nodeY(i);
            boolean open = has(s, i);
            if (i + 1 < PoliciaMod.SKILLS.length) {
                g.fill(nx + 11, y + 24, nx + 13, nodeY(i + 1), has(s, i + 1) ? 0xFFE070D8 : 0xFF6A3A66);
            }
            boolean hover = mx >= nx && mx <= nx + 24 && my >= y && my <= y + 24;
            int border = (open && i == sel) ? 0xFFFFFFFF : (hover ? 0xFFFFE9A0 : 0xFF6B4A0C);
            g.fill(nx - 1, y - 1, nx + 25, y + 25, border);
            g.fill(nx, y, nx + 24, y + 24, open ? 0xFFC8921C : 0xFF8A6612);
            if (i == 1) tankIcon(g, nx + 4, y + 4); else g.renderItem(icon(i), nx + 4, y + 4);
            if (!open) {
                g.fill(nx, y, nx + 24, y + 24, 0x99000000);
                padlock(g, nx, y);
            }
            int tx = nx + 34;
            g.drawString(font, NAMES[i], tx, y, open ? 0xFFFFFFFF : 0xFFA0A0A0, true);
            List<FormattedCharSequence> lines = font.split(FormattedText.of(DESC[i]), px + PW - tx - 8);
            for (int k = 0; k < lines.size() && k < 3; k++) {
                g.drawString(font, lines.get(k), tx, y + 11 + k * 9, open ? 0xFFC8D0DC : 0xFF808890, false);
            }
            if (!open) {
                g.drawString(font, "Bloqueada - cuesta " + PoliciaMod.XP_PER_NODE + " XP", tx, y + 11 + Math.min(lines.size(), 3) * 9, 0xFFE8B830, false);
            } else if (i == sel) {
                g.drawString(font, "Elegida (tecla J)", tx, y + 11 + Math.min(lines.size(), 3) * 9, 0xFF60E060, false);
            }
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            PoliciaMod.Sync s = PoliciaClient.mine();
            int nx = nodeX();
            for (int i = 0; i < PoliciaMod.SKILLS.length; i++) {
                int y = nodeY(i);
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
}
