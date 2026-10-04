package com.example.greenlantern.client;

import com.example.greenlantern.GreenLanternMod;
import com.example.greenlantern.JetEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Dibuja el caza de energia (brillante, a plena luz). */
public class JetRenderer extends EntityRenderer<JetEntity> {
    public JetRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.6f;
    }

    @Override
    public ResourceLocation getTextureLocation(JetEntity e) {
        return JetModel.TEX;
    }

    @Override
    public void render(JetEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        ps.pushPose();
        float y = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        float x = Mth.lerp(pt, e.xRotO, e.getXRot());
        ps.mulPose(Axis.YP.rotationDegrees(180.0f - y));
        ps.mulPose(Axis.XP.rotationDegrees(-x));
        float s = 0.4f / 16.0f;
        ps.scale(s, s, s);
        ps.translate(0.0, -10.0, 10.0);
        JetModel.render(ps, buf, LightTexture.FULL_BRIGHT);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    @Mod.EventBusSubscriber(modid = GreenLanternMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class Register {
        @SubscribeEvent
        public static void onRegister(EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(GreenLanternMod.JET.get(), JetRenderer::new);
        }
    }
}
