package com.example.policia;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** Vision de minerales del albanil: mientras los ayudantes estan activos, se ven (a traves de los bloques) los minerales cercanos. */
public class PoliciaXray {
    static final List<BlockPos> POS = new ArrayList<>();
    static final List<float[]> COL = new ArrayList<>();
    static final int R = 4;
    /** Seleccion del area de picado (tecla X mantenida). */
    static boolean zoning;
    static int zl = 8, zw = 3;

    static void scroll(net.minecraftforge.client.event.InputEvent.MouseScrollingEvent e) {
        if (!zoning) return;
        e.setCanceled(true);
        int s = e.getScrollDelta() > 0 ? 1 : -1;
        if (e.isShiftDown()) zw = Math.max(1, Math.min(9, zw + s));
        else zl = Math.max(1, Math.min(24, zl + s));
    }

    static boolean active() {
        PoliciaAlbanil.AlbSync s = PoliciaAlbanil.CL;
        PoliciaMod.Sync ms = PoliciaClient.mine();
        return s != null && s.ht > 0 && s.hl >= 1 && ms != null && ms.jb == 2;
    }

    static float[] color(String p) {
        if (p.contains("coal")) return new float[]{0.75f, 0.75f, 0.75f};
        if (p.contains("iron")) return new float[]{1.0f, 0.75f, 0.55f};
        if (p.contains("copper")) return new float[]{1.0f, 0.5f, 0.2f};
        if (p.contains("gold")) return new float[]{1.0f, 0.9f, 0.1f};
        if (p.contains("redstone")) return new float[]{1.0f, 0.1f, 0.1f};
        if (p.contains("lapis")) return new float[]{0.25f, 0.45f, 1.0f};
        if (p.contains("emerald")) return new float[]{0.1f, 1.0f, 0.3f};
        if (p.contains("diamond")) return new float[]{0.2f, 1.0f, 1.0f};
        if (p.contains("quartz")) return new float[]{1.0f, 1.0f, 1.0f};
        if (p.contains("debris")) return new float[]{0.7f, 0.3f, 0.9f};
        return new float[]{1.0f, 0.4f, 1.0f};
    }

    static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !active()) { POS.clear(); COL.clear(); return; }
        if (mc.player.tickCount % 5 != 0) return;
        int lv = Math.max(1, Math.min(3, PoliciaAlbanil.CL.hl));
        POS.clear();
        COL.clear();
        BlockPos c = mc.player.blockPosition();
        for (int dx = -R; dx <= R; dx++) for (int dy = -R; dy <= R; dy++) for (int dz = -R; dz <= R; dz++) {
            BlockPos bp = c.offset(dx, dy, dz);
            BlockState st = mc.level.getBlockState(bp);
            if (st.isAir()) continue;
            if (!st.is(Tags.Blocks.ORES) && !st.is(Blocks.COAL_BLOCK)) continue;
            net.minecraft.resources.ResourceLocation key = ForgeRegistries.BLOCKS.getKey(st.getBlock());
            if (key == null) continue;
            String p = key.getPath();
            boolean ok;
            if (lv == 1) ok = p.contains("coal");
            else if (lv == 2) ok = !p.contains("diamond") && !p.contains("debris") && !p.contains("netherite");
            else ok = true;
            if (!ok) continue;
            POS.add(bp);
            COL.add(color(p));
        }
    }

    static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || (POS.isEmpty() && !zoning)) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        RenderSystem.disableDepthTest();
        VertexConsumer vc = bs.getBuffer(RenderType.lines());
        for (int i = 0; i < POS.size() && i < COL.size(); i++) {
            BlockPos b = POS.get(i);
            float[] c = COL.get(i);
            LevelRenderer.renderLineBox(ps, vc, b.getX() - cam.x, b.getY() - cam.y, b.getZ() - cam.z,
                    b.getX() + 1 - cam.x, b.getY() + 1 - cam.y, b.getZ() + 1 - cam.z, c[0], c[1], c[2], 1.0f);
        }
        if (zoning && mc.player != null) {
            net.minecraft.core.Direction f = mc.player.getDirection();
            net.minecraft.core.Direction r = f.getClockWise();
            BlockPos o = mc.player.blockPosition();
            int lo = -((zw - 1) / 2), hi = zw / 2;
            BlockPos a = PoliciaAlbanil.at(o, f, r, 1, lo, 0);
            BlockPos b = PoliciaAlbanil.at(o, f, r, zl, hi, 2);
            double x0 = Math.min(a.getX(), b.getX()), y0 = Math.min(a.getY(), b.getY()), z0 = Math.min(a.getZ(), b.getZ());
            double x1 = Math.max(a.getX(), b.getX()) + 1, y1 = Math.max(a.getY(), b.getY()) + 1, z1 = Math.max(a.getZ(), b.getZ()) + 1;
            LevelRenderer.renderLineBox(ps, vc, x0 - cam.x, y0 - cam.y, z0 - cam.z, x1 - cam.x, y1 - cam.y, z1 - cam.z, 0.2f, 1.0f, 0.3f, 1.0f);
        }
        bs.endBatch(RenderType.lines());
        RenderSystem.enableDepthTest();
    }
}
