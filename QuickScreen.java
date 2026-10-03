package com.example.greenlantern.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Seleccion rapida: aparece al mantener R. Elige uno de los 3 favoritos. */
public class QuickScreen extends Screen {
    private static final int SIZE = 36, GAP = 10;
    private int hover = -1;

    public QuickScreen() {
        super(Component.literal("Favoritos"));
    }

    private int slotX(int i) {
        int total = Favorites.SLOTS * SIZE + (Favorites.SLOTS - 1) * GAP;
        return (this.width - total) / 2 + i * (SIZE + GAP);
    }

    private int slotY() {
        return this.height / 2 - SIZE / 2;
    }

    private int slotAt(double mx, double my) {
        for (int i = 0; i < Favorites.SLOTS; i++) {
            int x = slotX(i), y = slotY();
            if (mx >= x && mx < x + SIZE && my >= y && my < y + SIZE) return i;
        }
        return -1;
    }

    private void pick(int i) {
        ItemStack st = Favorites.get(i);
        if (st.isEmpty()) return;
        Favorites.conjure(st, hasShiftDown());
        this.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        hover = slotAt(mx, my);
        g.fill(0, 0, this.width, this.height, 0x55000000);
        g.drawCenteredString(this.font, "Favoritos del anillo", this.width / 2, slotY() - 22, 0x55FF77);
        for (int i = 0; i < Favorites.SLOTS; i++) {
            int x = slotX(i), y = slotY();
            g.fill(x - 2, y - 2, x + SIZE + 2, y + SIZE + 2, hover == i ? 0xFF55FF88 : 0xFF2E6B45);
            g.fill(x, y, x + SIZE, y + SIZE, hover == i ? 0xFF2F6B45 : 0xFF0E1F15);
            ItemStack st = Favorites.get(i);
            if (st.isEmpty()) {
                g.drawCenteredString(this.font, "" + (i + 1), x + SIZE / 2, y + SIZE / 2 - 4, 0x446655);
            } else {
                g.pose().pushPose();
                g.pose().translate(x + SIZE / 2 - 16, y + SIZE / 2 - 16, 0);
                g.pose().scale(2f, 2f, 1f);
                g.renderItem(st, 0, 0);
                g.pose().popPose();
                ClientEvents.tint(g, x + SIZE / 2 - 16, y + SIZE / 2 - 16, 32);
            }
        }
        boolean any = false;
        for (int i = 0; i < Favorites.SLOTS; i++) if (!Favorites.get(i).isEmpty()) any = true;
        String hint = any ? "Suelta R sobre uno, haz clic, o pulsa 1 / 2 / 3"
                : "Sin favoritos: toca R y haz clic derecho en un objeto";
        g.drawCenteredString(this.font, hint, this.width / 2, slotY() + SIZE + 14, 0x88CC99);
        super.render(g, mx, my, pt);
        if (hover >= 0 && !Favorites.get(hover).isEmpty()) g.renderTooltip(this.font, Favorites.get(hover), mx, my);
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        long win = mc.getWindow().getWindow();
        boolean down = InputConstants.isKeyDown(win, ClientEvents.BLAST.getKey().getValue());
        if (!down && hover >= 0) pick(hover); // soltar R sobre una ranura la elige
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int i = slotAt(mx, my);
        if (i >= 0 && button == 0) { pick(i); return true; }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= 49 && key <= 51) { pick(key - 49); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
