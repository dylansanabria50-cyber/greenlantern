package com.example.policia;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Policia de refuerzo: skin de policia, espada en la mano y el escudo pixel art delante del cuerpo. */
public class PoliciaRefuerzoRender extends HumanoidMobRenderer<PoliciaRefuerzo.AgentEntity, PlayerModel<PoliciaRefuerzo.AgentEntity>> {
    static final ResourceLocation TEX = new ResourceLocation("policia", "textures/entity/policia.png");

    public PoliciaRefuerzoRender(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(PoliciaRefuerzo.AgentEntity e) { return TEX; }

    @Override
    public void render(PoliciaRefuerzo.AgentEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        super.render(e, yaw, pt, ps, buf, light);
        float by = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-by));
        ps.translate(0.0, 1.15, 0.55);
        PoliciaClient.drawFace(ps, buf.getBuffer(RenderType.entityCutoutNoCull(PoliciaClient.TEX_FRONT)), 0.45f, 0.65f, 0.02f, light, false);
        PoliciaClient.drawFace(ps, buf.getBuffer(RenderType.entityCutoutNoCull(PoliciaClient.TEX_BACK)), 0.45f, 0.65f, -0.02f, light, true);
        ps.popPose();
    }
}
