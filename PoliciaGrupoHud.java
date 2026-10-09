package com.example.policia;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Indicador en pantalla de los companeros de grupo (nombre, distancia, direccion y vida). */
@Mod.EventBusSubscriber(modid = "policia", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PoliciaGrupoHud {
    static volatile PoliciaGrupo.Team TEAM = new PoliciaGrupo.Team();
    static final String[] JOB = {"", "policia", "albanil", "ladron"};

    @SubscribeEvent
    public static void gHud(RenderGuiOverlayEvent.Post e) {
        if (e.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        PoliciaGrupo.Team t = TEAM;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (t == null || t.names.isEmpty() || me == null || mc.options.hideGui) return;
        GuiGraphics g = e.getGuiGraphics();
        int y = 8;
        g.drawString(mc.font, "GRUPO", 8, y, 0xFFE8C040);
        y += 11;
        for (int i = 0; i < t.names.size(); i++) {
            int[] p = t.pos.get(i);
            String line;
            if (p[4] < 0) {
                line = t.names.get(i) + " (otra dimension)";
            } else {
                double dx = p[0] + 0.5 - me.getX(), dz = p[2] + 0.5 - me.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                double ang = Math.toDegrees(Math.atan2(-dx, dz)) - me.getYRot();
                ang = ((ang % 360) + 540) % 360 - 180;
                String arrow;
                double aa = Math.abs(ang);
                if (aa < 25) arrow = "^";
                else if (aa < 65) arrow = ang > 0 ? "^>" : "<^";
                else if (aa < 115) arrow = ang > 0 ? ">" : "<";
                else if (aa < 155) arrow = ang > 0 ? "v>" : "<v";
                else arrow = "v";
                String job = p[4] > 0 && p[4] < JOB.length ? " [" + JOB[p[4]] + "]" : "";
                line = t.names.get(i) + job + " " + (int) dist + " m " + arrow + "  " + p[3] / 2 + (p[3] % 2 == 1 ? ".5" : "") + " vida";
            }
            int w = mc.font.width(line);
            g.fill(6, y - 1, 10 + w, y + 9, 0x80000000);
            g.drawString(mc.font, line, 8, y, 0xFFFFFFFF);
            y += 11;
        }
    }
}
