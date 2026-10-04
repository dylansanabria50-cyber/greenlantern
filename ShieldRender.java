package com.example.greenlantern.client;

import com.example.greenlantern.GreenLanternMod;
import com.example.greenlantern.ShieldFx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Dibuja la burbuja como una esfera lisa de vidrio verde (radio 5) alrededor del jugador. */
@Mod.EventBusSubscriber(modid = GreenLanternMod.MODID, value = Dist.CLIENT)
public class ShieldRender {
    private static final float RADIUS = 5.0F;

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ShieldFx.CLIENT_ON.isEmpty()) return;

        PoseStack ps = e.getPoseStack();
        Vec3 cam = e.getCamera().getPosition();
        float pt = e.getPartialTick();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(GreenRender.SPHERE);
        float pulse = 0.20F + 0.05F * Mth.sin((mc.level.getGameTime() + pt) * 0.1F);

        for (int id : ShieldFx.CLIENT_ON) {
            Entity en = mc.level.getEntity(id);
            if (en == null) continue;
            double x = Mth.lerp(pt, en.xo, en.getX());
            double y = Mth.lerp(pt, en.yo, en.getY());
            double z = Mth.lerp(pt, en.zo, en.getZ());
            ps.pushPose();
            ps.translate(x - cam.x, y + 1.0 - cam.y, z - cam.z);
            sphere(ps.last().pose(), vc, RADIUS, pulse);
            ps.popPose();
        }
        buf.endBatch(GreenRender.SPHERE);
    }

    private static void sphere(Matrix4f m, VertexConsumer vc, float r, float a) {
        int stacks = 14, slices = 28;
        for (int i = 0; i < stacks; i++) {
            float t0 = (float) Math.PI * i / stacks;
            float t1 = (float) Math.PI * (i + 1) / stacks;
            float y0 = r * Mth.cos(t0), y1 = r * Mth.cos(t1);
            float r0 = r * Mth.sin(t0), r1 = r * Mth.sin(t1);
            float a0 = a * (0.55F + 0.45F * Mth.sin(t0));
            float a1 = a * (0.55F + 0.45F * Mth.sin(t1));
            for (int j = 0; j < slices; j++) {
                float p0 = (float) (2.0 * Math.PI) * j / slices;
                float p1 = (float) (2.0 * Math.PI) * (j + 1) / slices;
                vert(vc, m, r0 * Mth.cos(p0), y0, r0 * Mth.sin(p0), a0);
                vert(vc, m, r1 * Mth.cos(p0), y1, r1 * Mth.sin(p0), a1);
                vert(vc, m, r1 * Mth.cos(p1), y1, r1 * Mth.sin(p1), a1);
                vert(vc, m, r0 * Mth.cos(p1), y0, r0 * Mth.sin(p1), a0);
            }
        }
    }

    private static void vert(VertexConsumer vc, Matrix4f m, float x, float y, float z, float a) {
        vc.vertex(m, x, y, z).color(0.25F, 1.0F, 0.5F, a).endVertex();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        ShieldFx.CLIENT_ON.clear();
    }
}
