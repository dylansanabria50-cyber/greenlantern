package com.example.policia;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Dibuja el tractor amarillo (pala cargadora) con cajas de color: ruedas, pala y direccion animadas. */
public class PoliciaTractorRender extends EntityRenderer<PoliciaTractor.TractorEntity> {
    static final int FULL = 15728880;

    public PoliciaTractorRender(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 2.2f;
    }

    @Override
    public ResourceLocation getTextureLocation(PoliciaTractor.TractorEntity e) { return PoliciaExtraRender.WHITE; }

    static void b(PoseStack ps, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int r, int g, int bl, int light) {
        PoliciaExtraRender.box(ps, vc, x0, y0, z0, x1, y1, z1, r, g, bl, light);
    }

    /** Neumatico octogonal con cubo y radios; gira sobre su eje (X) y puede girar con la direccion. */
    static void wheel(PoseStack ps, VertexConsumer vc, float cx, float cz, float spin, float steer, int light) {
        ps.pushPose();
        ps.translate(cx, 1.15f, cz);
        if (steer != 0f) ps.mulPose(Axis.YP.rotationDegrees(steer));
        ps.mulPose(Axis.XP.rotationDegrees(spin));
        for (int k = 0; k < 8; k++) {
            ps.pushPose();
            ps.mulPose(Axis.XP.rotationDegrees(k * 45.0f));
            int c = (k & 1) == 0 ? 22 : 48;
            b(ps, vc, -0.45f, 0.80f, -0.48f, 0.45f, 1.15f, 0.48f, c, c, c + 3, light);
            ps.popPose();
        }
        b(ps, vc, -0.5f, -0.32f, -0.32f, 0.5f, 0.32f, 0.32f, 200, 150, 10, light);
        for (int k = 0; k < 4; k++) {
            ps.pushPose();
            ps.mulPose(Axis.XP.rotationDegrees(k * 45.0f));
            b(ps, vc, -0.4f, -0.78f, -0.07f, 0.4f, 0.78f, 0.07f, 105, 108, 115, light);
            ps.popPose();
        }
        ps.popPose();
    }

    /** Cilindro hidraulico entre dos puntos del plano Y-Z. */
    static void strut(PoseStack ps, VertexConsumer vc, float x, float y0, float z0, float y1, float z1, int light) {
        float dy = y1 - y0, dz = z1 - z0;
        float len = Mth.sqrt(dy * dy + dz * dz);
        float ang = (float) Math.toDegrees(Math.atan2(-dy, dz));
        ps.pushPose();
        ps.translate(x, y0, z0);
        ps.mulPose(Axis.XP.rotationDegrees(ang));
        b(ps, vc, -0.07f, -0.07f, 0f, 0.07f, 0.07f, len, 150, 154, 162, light);
        b(ps, vc, -0.11f, -0.11f, 0f, 0.11f, 0.11f, len * 0.55f, 40, 42, 48, light);
        ps.popPose();
    }

    @Override
    public void render(PoliciaTractor.TractorEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(PoliciaExtraRender.WHITE));
        float hy = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        float arm = Mth.lerp(pt, e.prevArm, e.arm);
        float spin = (float) Math.toDegrees(Mth.lerp(pt, e.prevWH, e.wh));
        float steer = Mth.lerp(pt, e.prevSteer, e.steer);
        boolean lamp = e.lampOn();
        int hl = lamp ? FULL : light;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-hy));

        // ruedas (la direccion gira las delanteras)
        wheel(ps, vc, -1.95f, -1.9f, spin, 0f, light);
        wheel(ps, vc, 1.95f, -1.9f, spin, 0f, light);
        wheel(ps, vc, -1.95f, 1.9f, spin, steer, light);
        wheel(ps, vc, 1.95f, 1.9f, spin, steer, light);
        // ejes y chasis
        b(ps, vc, -1.95f, 0.95f, -2.05f, 1.95f, 1.35f, -1.75f, 70, 72, 78, light);
        b(ps, vc, -1.95f, 0.95f, 1.75f, 1.95f, 1.35f, 2.05f, 70, 72, 78, light);
        b(ps, vc, -1.1f, 0.9f, -3.7f, 1.1f, 1.5f, 2.6f, 52, 54, 60, light);

        // motor trasero
        b(ps, vc, -1.35f, 1.5f, -3.9f, 1.35f, 3.2f, -1.35f, 240, 182, 14, light);
        b(ps, vc, -1.25f, 3.2f, -3.7f, 1.25f, 3.45f, -1.5f, 205, 150, 8, light);
        b(ps, vc, -1.4f, 1.3f, -4.15f, 1.4f, 2.7f, -3.85f, 34, 34, 38, light);
        b(ps, vc, 1.35f, 2.0f, -3.6f, 1.39f, 2.8f, -2.0f, 30, 30, 34, light);
        b(ps, vc, -1.39f, 2.0f, -3.6f, -1.35f, 2.8f, -2.0f, 30, 30, 34, light);
        b(ps, vc, 0.7f, 3.45f, -3.2f, 0.95f, 4.5f, -2.95f, 30, 30, 34, light);
        b(ps, vc, 0.66f, 4.5f, -3.24f, 0.99f, 4.58f, -2.91f, 70, 72, 78, light);
        boolean tailOn = lamp;
        b(ps, vc, 0.75f, 1.9f, -4.19f, 1.15f, 2.2f, -4.15f, 220, 20, 20, tailOn ? FULL : light);
        b(ps, vc, -1.15f, 1.9f, -4.19f, -0.75f, 2.2f, -4.15f, 220, 20, 20, tailOn ? FULL : light);

        // cabina
        b(ps, vc, -1.15f, 1.5f, -1.35f, 1.15f, 1.75f, 1.2f, 205, 150, 8, light);
        b(ps, vc, -1.15f, 1.75f, -1.35f, -1.05f, 2.7f, 1.2f, 240, 182, 14, light);
        b(ps, vc, 1.05f, 1.75f, -1.35f, 1.15f, 2.7f, 1.2f, 240, 182, 14, light);
        b(ps, vc, -1.15f, 1.75f, -1.35f, 1.15f, 3.0f, -1.25f, 240, 182, 14, light);
        b(ps, vc, -1.15f, 2.7f, -1.35f, -1.05f, 4.5f, -1.22f, 240, 182, 14, light);
        b(ps, vc, 1.05f, 2.7f, -1.35f, 1.15f, 4.5f, -1.22f, 240, 182, 14, light);
        b(ps, vc, -1.15f, 2.7f, 1.05f, -1.05f, 4.5f, 1.2f, 240, 182, 14, light);
        b(ps, vc, 1.05f, 2.7f, 1.05f, 1.15f, 4.5f, 1.2f, 240, 182, 14, light);
        b(ps, vc, -1.25f, 4.5f, -1.45f, 1.25f, 4.7f, 1.3f, 240, 182, 14, light);
        b(ps, vc, -1.25f, 4.45f, 1.3f, 1.25f, 4.6f, 1.55f, 205, 150, 8, light);
        // baliza giratoria
        boolean blink = ((e.tickCount / 6) & 1) == 0;
        b(ps, vc, -0.12f, 4.7f, -0.9f, 0.12f, 4.95f, -0.66f, blink ? 255 : 120, blink ? 150 : 70, 10, blink ? FULL : light);
        // asiento, tablero y volante
        b(ps, vc, -0.45f, 1.75f, -0.65f, 0.45f, 2.15f, 0.15f, 34, 34, 38, light);
        b(ps, vc, -0.45f, 2.15f, -0.75f, 0.45f, 3.3f, -0.62f, 34, 34, 38, light);
        b(ps, vc, -0.95f, 1.75f, 0.65f, 0.95f, 2.85f, 1.0f, 40, 42, 48, light);
        b(ps, vc, -0.05f, 2.85f, 0.4f, 0.05f, 3.15f, 0.5f, 34, 34, 38, light);
        b(ps, vc, -0.3f, 3.3f, 0.35f, 0.3f, 3.38f, 0.52f, 34, 34, 38, light);
        b(ps, vc, -0.3f, 3.0f, 0.35f, 0.3f, 3.08f, 0.52f, 34, 34, 38, light);
        b(ps, vc, -0.3f, 3.0f, 0.35f, -0.22f, 3.38f, 0.52f, 34, 34, 38, light);
        b(ps, vc, 0.22f, 3.0f, 0.35f, 0.3f, 3.38f, 0.52f, 34, 34, 38, light);

        // bastidor delantero, soportes de los brazos y guardabarros
        b(ps, vc, -1.0f, 1.0f, 1.2f, 1.0f, 1.9f, 2.6f, 205, 150, 8, light);
        b(ps, vc, 1.15f, 1.9f, 0.7f, 1.5f, 3.0f, 1.1f, 240, 182, 14, light);
        b(ps, vc, -1.5f, 1.9f, 0.7f, -1.15f, 3.0f, 1.1f, 240, 182, 14, light);
        for (int s = -1; s <= 1; s += 2) {
            float xa = s > 0 ? 1.5f : -2.5f, xb = s > 0 ? 2.5f : -1.5f;
            b(ps, vc, xa, 2.45f, -3.3f, xb, 2.6f, -0.5f, 240, 182, 14, light);
            b(ps, vc, xa, 2.45f, 0.7f, xb, 2.6f, 3.1f, 240, 182, 14, light);
        }
        // faros
        b(ps, vc, 0.55f, 1.35f, 2.6f, 0.9f, 1.65f, 2.68f, lamp ? 255 : 205, lamp ? 250 : 205, lamp ? 190 : 175, hl);
        b(ps, vc, -0.9f, 1.35f, 2.6f, -0.55f, 1.65f, 2.68f, lamp ? 255 : 205, lamp ? 250 : 205, lamp ? 190 : 175, hl);
        b(ps, vc, 0.7f, 4.38f, 1.55f, 1.1f, 4.55f, 1.63f, lamp ? 255 : 205, lamp ? 250 : 205, lamp ? 190 : 175, hl);
        b(ps, vc, -1.1f, 4.38f, 1.55f, -0.7f, 4.55f, 1.63f, lamp ? 255 : 205, lamp ? 250 : 205, lamp ? 190 : 175, hl);

        // brazos de la pala
        float phi = Mth.lerp(arm, 0.70f, -0.5f);
        float phiDeg = (float) Math.toDegrees(phi);
        float sinP = Mth.sin(phi), cosP = Mth.cos(phi);
        for (int s = -1; s <= 1; s += 2) {
            ps.pushPose();
            ps.translate(s * 1.35f, 2.7f, 0.9f);
            ps.mulPose(Axis.XP.rotationDegrees(phiDeg));
            b(ps, vc, -0.14f, -0.22f, -0.2f, 0.14f, 0.22f, 3.5f, 240, 182, 14, light);
            b(ps, vc, -0.17f, -0.25f, -0.25f, 0.17f, 0.25f, 0.25f, 70, 72, 78, light);
            ps.popPose();
            strut(ps, vc, s * 1.12f, 1.8f, 0.55f, 2.7f - 1.4f * sinP, 0.9f + 1.4f * cosP, light);
        }
        // cuchara
        float tipY = 2.7f - 3.5f * sinP, tipZ = 0.9f + 3.5f * cosP;
        float curl = (float) Math.toDegrees(Mth.lerp(arm, 0.04f, -0.45f));
        ps.pushPose();
        ps.translate(0f, tipY, tipZ);
        ps.mulPose(Axis.XP.rotationDegrees(curl));
        b(ps, vc, -2.0f, -0.35f, -0.3f, 2.0f, -0.18f, 1.7f, 240, 182, 14, light);
        b(ps, vc, -2.0f, -0.35f, -0.45f, 2.0f, 1.1f, -0.28f, 205, 150, 8, light);
        b(ps, vc, -2.0f, -0.35f, -0.3f, -1.88f, 0.9f, 1.7f, 240, 182, 14, light);
        b(ps, vc, 1.88f, -0.35f, -0.3f, 2.0f, 0.9f, 1.7f, 240, 182, 14, light);
        b(ps, vc, -2.0f, -0.38f, 1.55f, 2.0f, -0.12f, 1.75f, 95, 98, 105, light);
        for (int i = 0; i < 7; i++) {
            float x = -1.8f + i * 0.6f;
            b(ps, vc, x - 0.12f, -0.4f, 1.7f, x + 0.12f, -0.12f, 2.05f, 140, 144, 152, light);
        }
        b(ps, vc, -0.2f, -0.12f, -0.55f, 0.2f, 0.3f, -0.3f, 70, 72, 78, light);
        ps.popPose();

        ps.popPose();
    }
}
