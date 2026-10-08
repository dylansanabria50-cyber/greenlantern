package com.example.policia;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraftforge.client.event.EntityRenderersEvent;
import org.joml.Matrix3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import org.joml.Matrix4f;

/** Dibujo del perro K9, la patrulla con torreta y el dron de vigilancia. */
public class PoliciaExtraRender {
    static final ResourceLocation WHITE = new ResourceLocation("policia", "textures/entity/blanco.png");
    static final ResourceLocation K9TEX = new ResourceLocation("policia", "textures/entity/k9.png");
    static final int FULL = 15728880;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer((EntityType) PoliciaExtra.K9.get(), (EntityRendererProvider) K9Render::new);
        e.registerEntityRenderer(PoliciaExtra.SIREN.get(), SirenRender::new);
        e.registerEntityRenderer(PoliciaExtra.DRONE.get(), DroneRender::new);
    }

    // ---------- cajas de color ----------
    static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n,
                     float ax, float ay, float az, float bx, float by, float bz,
                     float cx, float cy, float cz, float dx, float dy, float dz,
                     float nx, float ny, float nz, int r, int g, int b, int light) {
        vert(vc, m, n, ax, ay, az, nx, ny, nz, r, g, b, light);
        vert(vc, m, n, bx, by, bz, nx, ny, nz, r, g, b, light);
        vert(vc, m, n, cx, cy, cz, nx, ny, nz, r, g, b, light);
        vert(vc, m, n, dx, dy, dz, nx, ny, nz, r, g, b, light);
    }

    static void vert(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z,
                     float nx, float ny, float nz, int r, int g, int b, int light) {
        vc.vertex(m, x, y, z).color(r, g, b, 255).uv(0.5f, 0.5f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
    }

    static void box(PoseStack ps, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                    int r, int g, int b, int light) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        quad(vc, m, n, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0f, 0f, -1f, r, g, b, light);
        quad(vc, m, n, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0f, 0f, 1f, r, g, b, light);
        quad(vc, m, n, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1f, 0f, 0f, r, g, b, light);
        quad(vc, m, n, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1f, 0f, 0f, r, g, b, light);
        quad(vc, m, n, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0f, -1f, 0f, r, g, b, light);
        quad(vc, m, n, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0f, 1f, 0f, r, g, b, light);
    }

    // ---------- perro K9 ----------
    public static class K9Render extends WolfRenderer {
        public K9Render(EntityRendererProvider.Context ctx) { super(ctx); }

        @Override
        public ResourceLocation getTextureLocation(Wolf w) { return K9TEX; }
    }

    // ---------- patrulla con torreta ----------
    public static class SirenRender extends EntityRenderer<PoliciaExtra.SirenEntity> {
        public SirenRender(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.shadowRadius = 0.9f;
        }

        @Override
        public ResourceLocation getTextureLocation(PoliciaExtra.SirenEntity e) { return WHITE; }

        @Override
        public void render(PoliciaExtra.SirenEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(WHITE));
            float hy = Mth.rotLerp(pt, e.yRotO, e.getYRot());
            boolean flash = ((e.tickCount / 5) & 1) == 0;
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(-hy));
            // ruedas
            for (int i = 0; i < 4; i++) {
                float wx = (i & 1) == 0 ? -0.72f : 0.72f;
                float wz = (i & 2) == 0 ? -0.4f : 0.4f;
                box(ps, vc, wx - 0.1f, 0.0f, wz - 0.2f, wx + 0.1f, 0.28f, wz + 0.2f, 24, 24, 26, light);
            }
            // carroceria azul con franja blanca
            box(ps, vc, -0.7f, 0.14f, -0.55f, 0.7f, 0.5f, 0.55f, 30, 52, 130, light);
            box(ps, vc, -0.72f, 0.24f, -0.3f, 0.72f, 0.4f, 0.3f, 236, 238, 242, light);
            // cabina
            box(ps, vc, -0.46f, 0.5f, -0.42f, 0.46f, 0.8f, 0.42f, 236, 238, 242, light);
            box(ps, vc, -0.4f, 0.54f, 0.4f, 0.4f, 0.76f, 0.44f, 110, 170, 210, light);
            // barra de luces
            int rl = flash ? FULL : light;
            int bl = flash ? light : FULL;
            box(ps, vc, -0.42f, 0.8f, 0.06f, -0.02f, 0.92f, 0.3f, flash ? 255 : 110, flash ? 30 : 10, flash ? 30 : 10, rl);
            box(ps, vc, 0.02f, 0.8f, 0.06f, 0.42f, 0.92f, 0.3f, flash ? 10 : 40, flash ? 20 : 90, flash ? 90 : 255, bl);
            // torreta
            ps.pushPose();
            ps.translate(0.0f, 0.86f, -0.2f);
            ps.mulPose(Axis.YP.rotationDegrees(hy - e.aim()));
            box(ps, vc, -0.16f, -0.06f, -0.16f, 0.16f, 0.1f, 0.16f, 90, 96, 104, light);
            box(ps, vc, -0.045f, -0.02f, 0.1f, 0.045f, 0.06f, 0.75f, 40, 44, 50, light);
            ps.popPose();
            ps.popPose();
        }
    }

    // ---------- dron de vigilancia ----------
    public static class DroneRender extends EntityRenderer<PoliciaExtra.DroneEntity> {
        public DroneRender(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.shadowRadius = 0.2f;
        }

        @Override
        public ResourceLocation getTextureLocation(PoliciaExtra.DroneEntity e) { return WHITE; }

        @Override
        public void render(PoliciaExtra.DroneEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(WHITE));
            float hy = Mth.rotLerp(pt, e.yRotO, e.getYRot());
            float age = e.tickCount + pt;
            ps.pushPose();
            ps.translate(0.0f, 0.15f, 0.0f);
            ps.mulPose(Axis.YP.rotationDegrees(-hy));
            box(ps, vc, -0.16f, -0.05f, -0.16f, 0.16f, 0.07f, 0.16f, 44, 48, 56, light);
            box(ps, vc, -0.4f, 0.0f, -0.03f, 0.4f, 0.04f, 0.03f, 70, 76, 84, light);
            box(ps, vc, -0.03f, 0.0f, -0.4f, 0.03f, 0.04f, 0.4f, 70, 76, 84, light);
            boolean blink = ((e.tickCount / 8) & 1) == 0;
            box(ps, vc, -0.05f, -0.07f, 0.1f, 0.05f, 0.0f, 0.2f, blink ? 255 : 90, 20, 20, blink ? FULL : light);
            for (int i = 0; i < 4; i++) {
                float ox = i == 0 ? 0.4f : (i == 1 ? -0.4f : 0.0f);
                float oz = i == 2 ? 0.4f : (i == 3 ? -0.4f : 0.0f);
                ps.pushPose();
                ps.translate(ox, 0.05f, oz);
                ps.mulPose(Axis.YP.rotationDegrees(age * 60.0f + i * 45.0f));
                box(ps, vc, -0.2f, 0.0f, -0.02f, 0.2f, 0.015f, 0.02f, 200, 206, 214, light);
                box(ps, vc, -0.02f, 0.0f, -0.2f, 0.02f, 0.015f, 0.2f, 200, 206, 214, light);
                ps.popPose();
            }
            ps.popPose();
        }
    }

    // ---------- esposas ----------
    static void ring(PoseStack ps, VertexConsumer vc, int light) {
        box(ps, vc, -0.1f, -0.1f, -0.03f, 0.1f, -0.06f, 0.03f, 196, 202, 212, light);
        box(ps, vc, -0.1f, 0.06f, -0.03f, 0.1f, 0.1f, 0.03f, 196, 202, 212, light);
        box(ps, vc, -0.1f, -0.06f, -0.03f, -0.06f, 0.06f, 0.03f, 196, 202, 212, light);
        box(ps, vc, 0.06f, -0.06f, -0.03f, 0.1f, 0.06f, 0.03f, 196, 202, 212, light);
        box(ps, vc, -0.035f, 0.1f, -0.04f, 0.035f, 0.14f, 0.04f, 96, 100, 110, light);
    }

    /** Esposas: dos aros de acero unidos por una cadena. */
    static void drawCuffs(PoseStack ps, VertexConsumer vc, int light, float spread) {
        for (int s = -1; s <= 1; s += 2) {
            ps.pushPose();
            ps.translate(s * (0.14f + spread), 0.0f, 0.0f);
            ring(ps, vc, light);
            ps.popPose();
        }
        float w = 0.04f + spread;
        for (int i = 0; i < 3; i++) {
            float cx = -w + (2.0f * w) * (i + 0.5f) / 3.0f;
            box(ps, vc, cx - 0.03f, -0.015f, -0.015f, cx + 0.03f, 0.015f, 0.015f, 120, 124, 134, light);
        }
    }

    static boolean cuffsSelected(Player p) {
        PoliciaMod.Sync s = PoliciaClient.STATE.get(p.getUUID());
        return s != null && s.on && s.sel == 5 && p.getMainHandItem().isEmpty();
    }

    public static void layers(EntityRenderersEvent.AddLayers e) {
        for (String sk : new String[]{"default", "slim"}) {
            Object r = e.getSkin(sk);
            if (r instanceof PlayerRenderer pr) pr.addLayer(new CuffHandLayer(pr));
        }
    }

    /** Esposas en la mano (tercera persona) cuando la habilidad esta elegida. */
    public static class CuffHandLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        public CuffHandLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p,
                           float limb, float limbAmt, float pt, float age, float yawHead, float pitch) {
            if (!cuffsSelected(p)) return;
            HumanoidArm arm = p.getMainArm();
            ps.pushPose();
            this.getParentModel().translateToHand(arm, ps);
            ps.mulPose(Axis.XP.rotationDegrees(-90.0f));
            ps.mulPose(Axis.YP.rotationDegrees(180.0f));
            ps.translate((arm == HumanoidArm.LEFT ? -1.0f : 1.0f) / 16.0f, 0.125f, -0.625f);
            ps.scale(0.7f, 0.7f, 0.7f);
            drawCuffs(ps, buf.getBuffer(RenderType.entityCutoutNoCull(WHITE)), light, 0.0f);
            ps.popPose();
        }
    }

    /** Esposas en la mano (primera persona). */
    public static void handFirst(RenderHandEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || e.getHand() != InteractionHand.MAIN_HAND || !cuffsSelected(mc.player)) return;
        PoseStack ps = e.getPoseStack();
        float sq = Mth.sin(e.getSwingProgress() * (float) Math.PI);
        ps.pushPose();
        ps.translate(0.5f, -0.38f - e.getEquipProgress() * 0.6f - sq * 0.1f, -0.7f - sq * 0.2f);
        ps.mulPose(Axis.XP.rotationDegrees(-20.0f - sq * 35.0f));
        ps.mulPose(Axis.YP.rotationDegrees(-12.0f));
        drawCuffs(ps, e.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(WHITE)), e.getPackedLight(), 0.0f);
        ps.popPose();
    }

    /** Esposas sobre el esposado: se cierran con animacion y se abren al terminar. */
    public static void livingPost(RenderLivingEvent.Post<?, ?> e) {
        LivingEntity t = e.getEntity();
        MobEffectInstance ef = t.getEffect(PoliciaExtra.CUFF.get());
        if (ef == null) return;
        float pt = e.getPartialTick();
        float age = PoliciaExtra.CUFF_T - ef.getDuration() + pt;
        float c = Mth.clamp(age / 8.0f, 0.0f, 1.0f);
        c = Math.min(c, Mth.clamp((ef.getDuration() - pt) / 10.0f, 0.0f, 1.0f));
        float w = t.getBbWidth();
        float h = t.getBbHeight();
        float snap = 1.0f + 0.3f * Math.max(0.0f, 1.0f - Math.abs(age - 8.0f) / 3.0f);
        PoseStack ps = e.getPoseStack();
        VertexConsumer vc = e.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(WHITE));
        int light = e.getPackedLight();
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, t.yBodyRotO, t.yBodyRot)));
        float y = h * 0.42f;
        float ox = w * 0.5f + 0.1f;
        for (int s = -1; s <= 1; s += 2) {
            ps.pushPose();
            ps.translate(s * (ox + (1.0f - c) * 0.9f), y, 0.0f);
            ps.mulPose(Axis.XP.rotationDegrees(90.0f));
            ps.mulPose(Axis.ZP.rotationDegrees((1.0f - c) * 540.0f));
            ps.scale(snap, snap, snap);
            ring(ps, vc, light);
            ps.popPose();
        }
        if (c > 0.95f) {
            float zb = -(w * 0.5f + 0.06f);
            for (int i = 0; i < 6; i++) {
                float cx = -ox + 2.0f * ox * (i + 0.5f) / 6.0f;
                box(ps, vc, cx - 0.05f, y - 0.02f, zb - 0.02f, cx + 0.05f, y + 0.02f, zb + 0.02f, 120, 124, 134, light);
            }
        }
        ps.popPose();
    }
}
