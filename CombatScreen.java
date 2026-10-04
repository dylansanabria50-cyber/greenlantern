package com.example.greenlantern.client;

import com.example.greenlantern.ModNetwork;
import com.example.greenlantern.RingPowers;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Rueda de combate (tecla G): minigun, lanzacohetes y caza de energia. */
public class CombatScreen extends Screen {
    private static final int SIZE = 48, GAP = 14;
    private static final String[] NAMES = {"Minigun", "Lanzacohetes", "Caza"};
    private static final String[] INFO = {"Mantener clic derecho", "Clic derecho", "Vuela 35 s (doble salto)"};
    private int hover = -1;

    public CombatScreen() {
        super(Component.literal("Combate"));
    }

    private int slotX(int i) {
        int total = 3 * SIZE + 2 * GAP;
        return (this.width - total) / 2 + i * (SIZE + GAP);
    }

    private int slotY() {
        return this.height / 2 - SIZE / 2;
    }

    private int slotAt(double mx, double my) {
        for (int i = 0; i < 3; i++) {
            int x = slotX(i), y = slotY();
            if (mx >= x && mx < x + SIZE && my >= y && my < y + SIZE) return i;
        }
        return -1;
    }

    private ItemStack icon(int i) {
        if (i == 0) return new ItemStack(Items.CROSSBOW);
        if (i == 1) return new ItemStack(Items.FIREWORK_ROCKET);
        return new ItemStack(Items.ELYTRA);
    }

    private void pick(int i) {
        int act = i == 0 ? RingPowers.ACT_MINIGUN : (i == 1 ? RingPowers.ACT_ROCKET : RingPowers.ACT_JET);
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(act));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        hover = slotAt(mx, my);
        g.fill(0, 0, this.width, this.height, 0x55000000);
        g.drawCenteredString(this.font, "Constructos de combate", this.width / 2, slotY() - 24, 0x55FF77);
        for (int i = 0; i < 3; i++) {
            int x = slotX(i), y = slotY();
            g.fill(x - 2, y - 2, x + SIZE + 2, y + SIZE + 2, hover == i ? 0xFF55FF88 : 0xFF2E6B45);
            g.fill(x, y, x + SIZE, y + SIZE, hover == i ? 0xFF2F6B45 : 0xFF0E1F15);
            g.pose().pushPose();
            g.pose().translate(x + SIZE / 2 - 16, y + SIZE / 2 - 16, 0);
            g.pose().scale(2f, 2f, 1f);
            g.renderItem(icon(i), 0, 0);
            g.pose().popPose();
            ClientEvents.tint(g, x + SIZE / 2 - 16, y + SIZE / 2 - 16, 32);
            g.drawCenteredString(this.font, (i + 1) + " " + NAMES[i], x + SIZE / 2, y + SIZE + 6, 0xFFFFFF);
            g.drawCenteredString(this.font, INFO[i], x + SIZE / 2, y + SIZE + 18, 0x88CC99);
        }
        g.drawCenteredString(this.font, "Suelta G sobre uno, haz clic, o pulsa 1 / 2 / 3", this.width / 2, slotY() + SIZE + 40, 0x88CC99);
        super.render(g, mx, my, pt);
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        long win = mc.getWindow().getWindow();
        boolean down = InputConstants.isKeyDown(win, ClientEvents.COMBAT.getKey().getValue());
        if (!down && hover >= 0) pick(hover);
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
