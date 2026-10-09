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

/** Jefes de oficio, perista, guardias, agentes y obreros con modelo de jugador y animaciones propias. */
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
            int k = e.kind();
            float t = ageInTicks;
            boolean idle = limbSwingAmount < 0.04f;
            if (e.sitting()) {
                this.rightLeg.xRot = -1.5708f;
                this.leftLeg.xRot = -1.5708f;
                this.rightLeg.yRot = 0.2f;
                this.leftLeg.yRot = -0.2f;
                this.rightArm.xRot = -0.9f;
                this.leftArm.xRot = -0.9f;
                if (k == 0) {
                    // el jefe de policia escribe un informe
                    this.rightArm.xRot = -1.15f + (float) Math.sin(t * 0.5f) * 0.08f;
                    this.rightArm.yRot = -0.3f + (float) Math.sin(t * 0.25f) * 0.15f;
                    this.leftArm.xRot = -1.0f;
                    this.leftArm.yRot = 0.3f;
                    this.head.xRot = 0.35f + (float) Math.sin(t * 0.05f) * 0.05f;
                } else {
                    // el jefe de ladrones tamborilea con los dedos y mueve el pie
                    this.rightArm.xRot = -1.0f + Math.max(0.0f, (float) Math.sin(t * 0.4f)) * 0.25f;
                    this.leftArm.xRot = -1.3f;
                    this.leftArm.yRot = 0.5f;
                    this.rightLeg.xRot = -1.5708f + (float) Math.sin(t * 0.3f) * 0.08f;
                    this.head.yRot += (float) Math.sin(t * 0.03f) * 0.4f;
                }
            } else if (e.isAggressive() && k == 4) {
                AnimationUtils.animateZombieArms(this.leftArm, this.rightArm, true, this.attackTime, ageInTicks);
            } else if (idle) {
                switch (k) {
                    case 1: // capataz: da ordenes senalando, una mano en la cintura
                        this.rightArm.xRot = -1.35f + (float) Math.sin(t * 0.12f) * 0.15f;
                        this.rightArm.yRot = -0.35f;
                        this.leftArm.xRot = -0.35f;
                        this.leftArm.zRot = -0.5f;
                        this.head.yRot += (float) Math.sin(t * 0.04f) * 0.35f;
                        break;
                    case 6: // obrero: martillea
                        this.rightArm.xRot = -1.0f + (float) Math.sin(t * 0.45f) * 0.9f;
                        this.leftArm.xRot = -0.6f;
                        this.body.xRot = 0.15f;
                        this.head.xRot = 0.25f;
                        break;
                    case 5: { // agente: saludo cada tanto, luego manos al cinturon
                        float c = t % 240.0f;
                        if (c < 50.0f) {
                            this.rightArm.xRot = -2.7f;
                            this.rightArm.yRot = -0.5f;
                        } else {
                            this.rightArm.zRot += 0.25f;
                            this.leftArm.zRot -= 0.25f;
                        }
                        this.head.yRot += (float) Math.sin(t * 0.03f) * 0.5f;
                        break;
                    }
                    case 4: // ladron de guardia: brazos cruzados
                        this.rightArm.xRot = -0.75f;
                        this.rightArm.yRot = -0.5f;
                        this.leftArm.xRot = -0.75f;
                        this.leftArm.yRot = 0.5f;
                        break;
                    case 3: // perista: cuenta billetes frotando las manos
                        this.rightArm.xRot = -1.0f + (float) Math.sin(t * 0.3f) * 0.12f;
                        this.leftArm.xRot = -1.0f - (float) Math.sin(t * 0.3f) * 0.12f;
                        this.rightArm.yRot = -0.5f;
                        this.leftArm.yRot = 0.5f;
                        break;
                    default:
                        break;
                }
            }
            if (!e.sitting()) {
                float br = (float) Math.sin(t * 0.09f) * 0.04f + 0.03f;
                this.rightArm.zRot += br;
                this.leftArm.zRot -= br;
            }
            this.hat.copyFrom(this.head);
            this.jacket.copyFrom(this.body);
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
        if (e.sitting()) ps.translate(0.0, 0.7, 0.0);
    }
}
