package com.example.greenlantern.client;

import com.example.greenlantern.GreenLanternMod;
import com.example.greenlantern.JetEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Dibuja el caza de energia: casco translucido y luces a plena luz. */
public class JetRenderer extends EntityRenderer<JetEntity> {
    private static final int FULL_BRIGHT = 15728880;
    private final ModelPart root;

    public JetRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.8f;
        this.root = JetModel.createLayer().bakeRoot();
    }

    @Override
    public ResourceLocation getTextureLocation(JetEntity e) { return JetModel.casco(); }

    @Override
    public void render(JetEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        ps.pushPose();
        float y = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        float x = Mth.lerp(pt, e.xRotO, e.getXRot());
        ps.mulPose(Axis.YP.rotationDegrees(-y));
        ps.mulPose(Axis.XP.rotationDegrees(x));
        ps.scale(-1.0f, -1.0f, 1.0f);
        ps.scale(1.25f, 1.25f, 1.25f);
        root.render(ps, buf.getBuffer(RenderType.entityTranslucent(JetModel.casco())), FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        root.render(ps, buf.getBuffer(RenderType.eyes(JetModel.brillo())), FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }
}
