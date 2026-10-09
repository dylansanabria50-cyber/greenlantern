package com.example.policia;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/** Jefes de oficio, perista, guardias y obreros con modelo de jugador. */
public class PoliciaBossRender extends MobRenderer<PoliciaBoss.BossNpc, PoliciaBossRender.Mdl> {
    static final String[] NAMES = {"jefe_policia", "capataz", "jefe_ladrones", "perista", "ladron", "policia", "ayudante"};

    public static class Mdl extends PlayerModel<PoliciaBoss.BossNpc> {
        public Mdl(ModelPart root) {
            super(root, false);
        }

        @Override
        public void setupAnim(PoliciaBoss.BossNpc e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            this.rightArmPose = HumanoidModel.ArmPose.EMPTY;
            this.leftArmPose = HumanoidModel.ArmPose.EMPTY;
            super.setupAnim(e, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            if (e.sitting()) {
                this.rightLeg.xRot = -1.5708f;
                this.leftLeg.xRot = -1.5708f;
                this.rightLeg.yRot = 0.2f;
                this.leftLeg.yRot = -0.2f;
                this.rightArm.xRot = -0.9f;
                this.leftArm.xRot = -0.9f;
            }
            this.leftPants.copyFrom(this.leftLeg);
            this.rightPants.copyFrom(this.rightLeg);
            this.leftSleeve.copyFrom(this.leftArm);
            this.rightSleeve.copyFrom(this.rightArm);
        }
    }

    public PoliciaBossRender(EntityRendererProvider.Context ctx) {
        super(ctx, new Mdl(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        this.addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(PoliciaBoss.BossNpc e) {
        int k = Math.max(0, Math.min(NAMES.length - 1, e.kind()));
        return new ResourceLocation("policia", "textures/entity/" + NAMES[k] + ".png");
    }

    @Override
    protected void scale(PoliciaBoss.BossNpc e, PoseStack ps, float pt) {
        if (e.sitting()) ps.translate(0.0, -0.55, 0.0);
    }
}
