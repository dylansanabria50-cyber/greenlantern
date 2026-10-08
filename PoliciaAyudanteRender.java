package com.example.policia;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Ayudante del albanil: skin propia y pico en la mano. */
public class PoliciaAyudanteRender extends HumanoidMobRenderer<PoliciaAyudante.Helper, PlayerModel<PoliciaAyudante.Helper>> {
    static final ResourceLocation TEX = new ResourceLocation("policia", "textures/entity/ayudante.png");

    public PoliciaAyudanteRender(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(PoliciaAyudante.Helper e) { return TEX; }

    @Override
    public void render(PoliciaAyudante.Helper e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        this.model.rightArmPose = HumanoidModel.ArmPose.ITEM;
        this.model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        super.render(e, yaw, pt, ps, buf, light);
    }
}
