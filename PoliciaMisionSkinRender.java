package com.example.policia;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/** Bandidos, jefes y comisario con modelo de jugador y skins propias (con animacion). */
public class PoliciaMisionSkinRender extends MobRenderer<PoliciaMision.Pj, PoliciaMisionSkinRender.Mdl> {
    static final ResourceLocation[] TEX = {
            new ResourceLocation("policia", "textures/entity/pj_0.png"),
            new ResourceLocation("policia", "textures/entity/pj_1.png"),
            new ResourceLocation("policia", "textures/entity/pj_2.png"),
            new ResourceLocation("policia", "textures/entity/pj_3.png"),
            new ResourceLocation("policia", "textures/entity/pj_4.png")};

    public static class Mdl extends PlayerModel<PoliciaMision.Pj> {
        public Mdl(ModelPart root) {
            super(root, false);
        }

        @Override
        public void setupAnim(PoliciaMision.Pj e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            int k = e.kind();
            boolean ranged = (k == 2 || k == 5) && e.isAggressive();
            HumanoidModel.ArmPose pose = ranged ? HumanoidModel.ArmPose.BOW_AND_ARROW : HumanoidModel.ArmPose.EMPTY;
            this.rightArmPose = pose;
            this.leftArmPose = pose;
            super.setupAnim(e, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            if (e.isAggressive() && !ranged && k != 9 && k != 10) {
                AnimationUtils.animateZombieArms(this.leftArm, this.rightArm, true, this.attackTime, ageInTicks);
            }
            float t = ageInTicks;
            boolean idle = limbSwingAmount < 0.04f && !e.isAggressive();
            if (k == 9 && e.captive()) {
                // rehen atado: cabeza gacha, munecas juntas y temblor
                this.head.xRot = 0.4f;
                this.leftArm.xRot = -0.9f;
                this.rightArm.xRot = -0.9f;
                this.leftArm.yRot = 0.5f;
                this.rightArm.yRot = -0.5f;
                this.leftArm.zRot += (float) Math.sin(t * 1.7f) * 0.03f;
            } else if (k == 10 && idle) {
                // el comisario escribe en su libreta
                this.rightArm.xRot = -1.1f + (float) Math.sin(t * 0.5f) * 0.08f;
                this.leftArm.xRot = -1.0f;
                this.leftArm.yRot = 0.4f;
                this.head.xRot = 0.2f;
            } else if (idle) {
                if (k != 5) {
                    // bandido: vigila, se rasca la cabeza cada tanto
                    float c = t % 200.0f;
                    if (c < 40.0f) {
                        this.rightArm.xRot = -2.3f + (float) Math.sin(t * 0.6f) * 0.1f;
                        this.rightArm.yRot = -0.4f;
                    }
                    this.head.yRot += (float) Math.sin(t * 0.05f) * 0.5f;
                }
                float br = (float) Math.sin(t * 0.09f) * 0.04f + 0.03f;
                this.rightArm.zRot += br;
                this.leftArm.zRot -= br;
            }
            this.hat.copyFrom(this.head);
            this.leftSleeve.copyFrom(this.leftArm);
            this.rightSleeve.copyFrom(this.rightArm);
        }
    }

    public PoliciaMisionSkinRender(EntityRendererProvider.Context ctx) {
        super(ctx, new Mdl(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        this.addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(PoliciaMision.Pj e) {
        switch (e.kind()) {
            case 10: return TEX[0];
            case 0:
            case 12:
            case 7: return TEX[2];
            case 3:
            case 5: return TEX[3];
            case 4:
            case 8:
            case 11: return TEX[4];
            default: return TEX[1];
        }
    }

    @Override
    protected void scale(PoliciaMision.Pj e, PoseStack ps, float pt) {
        int k = e.kind();
        if (k == 4) ps.scale(1.2f, 1.2f, 1.2f);
        else if (k == 11) ps.scale(1.3f, 1.3f, 1.3f);
    }
}
