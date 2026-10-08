package com.example.policia;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;

/** Dibuja el tanque (modelo de Blockbench) con casco, torreta y canon animados. */
public class PoliciaTankRender extends EntityRenderer<PoliciaTank.TankEntity> {
    static final ResourceLocation TEX = new ResourceLocation("policia", "textures/entity/tanque.png");
    static final ResourceLocation MODEL = new ResourceLocation("policia", "tank/tank.bin");

    static class Part { float[] v; int[] idx; }
    static Part[] PARTS;

    public PoliciaTankRender(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 1.4f;
    }

    static void load() {
        if (PARTS != null) return;
        Part[] ps = new Part[3];
        try (InputStream raw = Minecraft.getInstance().getResourceManager().getResourceOrThrow(MODEL).open();
             DataInputStream in = new DataInputStream(new BufferedInputStream(raw))) {
            for (int i = 0; i < 6; i++) in.readFloat(); // pivotes: ya estan en PoliciaTank
            for (int k = 0; k < 3; k++) {
                Part p = new Part();
                int nv = in.readInt();
                p.v = new float[nv * 8];
                for (int i = 0; i < p.v.length; i++) p.v[i] = in.readFloat();
                int ni = in.readInt();
                p.idx = new int[ni];
                for (int i = 0; i < ni; i++) p.idx[i] = in.readUnsignedShort();
                ps[k] = p;
            }
        } catch (Exception ex) {
            ps = new Part[]{new Part(), new Part(), new Part()};
            for (Part p : ps) { p.v = new float[0]; p.idx = new int[0]; }
        }
        PARTS = ps;
    }

    static void draw(Part p, PoseStack ps, VertexConsumer vc, int light) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        for (int t = 0; t + 2 < p.idx.length; t += 3) {
            vert(p, p.idx[t], m, n, vc, light);
            vert(p, p.idx[t + 1], m, n, vc, light);
            vert(p, p.idx[t + 2], m, n, vc, light);
            vert(p, p.idx[t + 2], m, n, vc, light);
        }
    }

    static void vert(Part p, int i, Matrix4f m, Matrix3f n, VertexConsumer vc, int light) {
        int o = i * 8;
        vc.vertex(m, p.v[o], p.v[o + 1], p.v[o + 2]).color(255, 255, 255, 255).uv(p.v[o + 3], p.v[o + 4])
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, p.v[o + 5], p.v[o + 6], p.v[o + 7]).endVertex();
    }

    @Override
    public void render(PoliciaTank.TankEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        load();
        float hy = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        float ty = Mth.lerp(pt, e.prevTurretYaw, e.turretYaw);
        float pitch = Mth.lerp(pt, e.prevPitch, e.pitch);
        int fire = e.getEntityData().get(PoliciaTank.TankEntity.FIRE);
        // aparece creciendo desde el suelo y se retira encogiendo
        float age = e.tickCount + pt;
        float k = Mth.clamp(age / 8.0f, 0.0f, 1.0f);
        if (fire >= PoliciaTank.END_TICKS - 8) k = Math.min(k, Mth.clamp((PoliciaTank.END_TICKS - fire - pt) / 8.0f, 0.0f, 1.0f));
        float gr = 0.2f + 0.8f * k;
        // retroceso del canon tras el disparo
        float recoil = 0f;
        if (fire >= PoliciaTank.LOAD_TICKS) {
            float t = fire - PoliciaTank.LOAD_TICKS + pt;
            recoil = t < 2f ? 1.6f * (t / 2f) : 1.6f * Math.max(0f, 1f - (t - 2f) / 14f);
        }
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180.0f - hy));
        ps.scale(PoliciaTank.SX * gr, PoliciaTank.SY * gr, PoliciaTank.SX * gr);
        draw(PARTS[0], ps, vc, light);
        // torreta: gira sobre su eje
        ps.pushPose();
        ps.translate(PoliciaTank.PT[0], PoliciaTank.PT[1], PoliciaTank.PT[2]);
        ps.mulPose(Axis.YP.rotationDegrees(hy - ty));
        ps.translate(-PoliciaTank.PT[0], -PoliciaTank.PT[1], -PoliciaTank.PT[2]);
        draw(PARTS[1], ps, vc, light);
        // canon: sube y baja, y retrocede al disparar
        ps.pushPose();
        ps.translate(PoliciaTank.PB[0], PoliciaTank.PB[1], PoliciaTank.PB[2]);
        ps.mulPose(Axis.XP.rotationDegrees(-pitch));
        ps.translate(0.0f, 0.0f, recoil);
        ps.translate(-PoliciaTank.PB[0], -PoliciaTank.PB[1], -PoliciaTank.PB[2]);
        draw(PARTS[2], ps, vc, light);
        ps.popPose();
        ps.popPose();
        ps.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(PoliciaTank.TankEntity e) { return TEX; }
}
