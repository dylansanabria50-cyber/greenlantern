package com.example.greenlantern.client;

import com.example.greenlantern.GreenLanternMod;
import com.example.greenlantern.ModNetwork;
import com.example.greenlantern.RingSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Ranura propia del anillo dentro del inventario (junto al muneco). */
// Desactivado: ahora se usa la ranura de equipo extra de Legends (mod glsolo)
public class RingSlotUi {
    private static final int SX = 77, SY = 44;

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post e) {
        if (!(e.getScreen() instanceof InventoryScreen scr)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        GuiGraphics g = e.getGuiGraphics();
        int x = scr.getGuiLeft() + SX, y = scr.getGuiTop() + SY;
        boolean worn = RingSlot.has(mc.player);

        g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
        g.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
        ItemStack ring = new ItemStack(GreenLanternMod.POWER_RING.get());
        g.renderItem(ring, x, y);
        if (!worn) g.fill(x, y, x + 16, y + 16, 0xC08B8B8B);

        int mx = e.getMouseX(), my = e.getMouseY();
        if (mx >= x && mx < x + 16 && my >= y && my < y + 16) {
            g.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
            g.renderTooltip(mc.font, Component.literal(worn ? "Anillo de Poder (clic para quitarlo)" : "Ranura del anillo"), mx, my);
        }
    }

    @SubscribeEvent
    public static void onClick(ScreenEvent.MouseButtonPressed.Pre e) {
        if (!(e.getScreen() instanceof InventoryScreen scr)) return;
        if (e.getButton() != 0 && e.getButton() != 1) return;
        int x = scr.getGuiLeft() + SX, y = scr.getGuiTop() + SY;
        double mx = e.getMouseX(), my = e.getMouseY();
        if (mx >= x && mx < x + 16 && my >= y && my < y + 16) {
            ModNetwork.CHANNEL.sendToServer(new RingSlot.ClickPacket());
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) {
        RingSlot.CLIENT_WORN.clear();
    }
}
