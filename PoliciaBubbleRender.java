package com.example.policia;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Dibuja la esfera azul translucida que rodea al jugador. */
public class PoliciaBubbleRender extends EntityRenderer<PoliciaBubble.BubbleEntity> {
    public PoliciaBubbleRender(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(PoliciaBubble.BubbleEntity e) { return PoliciaExtraRender.WHITE; }

    @Override
    public boolean shouldRender(PoliciaBubble.BubbleEntity e, Frustum f, double x, double y, double z) { return true; }

    static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, int a, float nx, float ny, float nz) {
        vc.vertex(m, x, y, z).color(70, 150, 255, a).uv(0.0f, 0.0f).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(15728880).normal(n, nx, ny, nz).endVertex();
    }

    @Override
    public void render(PoliciaBubble.BubbleEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        Entity o = e.level().getEntity(e.ownerId());
        double ox = 0.0, oy = 0.0, oz = 0.0, h = 0.9;
        if (o != null) {
            ox = Mth.lerp(pt, o.xo, o.getX()) - Mth.lerp(pt, e.xo, e.getX());
            oy = Mth.lerp(pt, o.yo, o.getY()) - Mth.lerp(pt, e.yo, e.getY());
            oz = Mth.lerp(pt, o.zo, o.getZ()) - Mth.lerp(pt, e.zo, e.getZ());
            h = o.getBbHeight() * 0.5;
        }
        ps.pushPose();
        ps.translate(ox, oy + h, oz);
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(PoliciaExtraRender.WHITE));
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float pulse = 1.0f + 0.015f * Mth.sin((e.tickCount + pt) * 0.4f);
        float r = (float) PoliciaBubble.R * pulse;
        int stacks = 12, slices = 24;
        for (int i = 0; i < stacks; i++) {
            float a0 = (float) Math.PI * i / stacks - (float) Math.PI / 2.0f;
            float a1 = (float) Math.PI * (i + 1) / stacks - (float) Math.PI / 2.0f;
            float y0 = Mth.sin(a0), y1 = Mth.sin(a1), r0 = Mth.cos(a0), r1 = Mth.cos(a1);
            int al = 55 + (int) (45.0f * Math.abs(Mth.sin((a0 + a1) * 0.5f)));
            for (int j = 0; j < slices; j++) {
                float b0 = (float) (Math.PI * 2.0) * j / slices;
                float b1 = (float) (Math.PI * 2.0) * (j + 1) / slices;
                float c0 = Mth.cos(b0), s0 = Mth.sin(b0), c1 = Mth.cos(b1), s1 = Mth.sin(b1);
                v(vc, m, n, r * r0 * c0, r * y0, r * r0 * s0, al, r0 * c0, y0, r0 * s0);
                v(vc, m, n, r * r0 * c1, r * y0, r * r0 * s1, al, r0 * c1, y0, r0 * s1);
                v(vc, m, n, r * r1 * c1, r * y1, r * r1 * s1, al, r1 * c1, y1, r1 * s1);
                v(vc, m, n, r * r1 * c0, r * y1, r * r1 * s0, al, r1 * c0, y1, r1 * s0);
                v(vc, m, n, r * r1 * c0, r * y1, r * r1 * s0, al, -r1 * c0, -y1, -r1 * s0);
                v(vc, m, n, r * r1 * c1, r * y1, r * r1 * s1, al, -r1 * c1, -y1, -r1 * s1);
                v(vc, m, n, r * r0 * c1, r * y0, r * r0 * s1, al, -r0 * c1, -y0, -r0 * s1);
                v(vc, m, n, r * r0 * c0, r * y0, r * r0 * s0, al, -r0 * c0, -y0, -r0 * s0);
            }
        }
        ps.popPose();
    }
}
