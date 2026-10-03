package com.example.greenlantern.client;

import com.example.greenlantern.GreenLanternMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Traje de Linterna Verde: se forma como nanobots (de los pies a la cabeza) sobre el cuerpo,
 * brilla (se dibuja a plena luz) y lo rodea un aura verde translucida.
 */
public class SuitLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation AURA =
            new ResourceLocation(GreenLanternMod.MODID, "textures/block/construct_block.png");
    private static final int FULL_BRIGHT = 15728880;

    public SuitLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        float p = ClientSuit.progress(player.getId(), partialTicks);
        if (p <= 0f || player.isInvisible()) return;

        PlayerModel<AbstractClientPlayer> m = this.getParentModel();
        boolean head = m.head.visible, hat = m.hat.visible, body = m.body.visible, jacket = m.jacket.visible;
        boolean la = m.leftArm.visible, ls = m.leftSleeve.visible, ra = m.rightArm.visible, rs = m.rightSleeve.visible;
        boolean ll = m.leftLeg.visible, lp = m.leftPants.visible, rl = m.rightLeg.visible, rp = m.rightPants.visible;

        boolean showLegs = p > 0.10f, showBody = p > 0.40f, showArms = p > 0.65f, showHead = p > 0.85f;
        m.head.visible = head && showHead;
        m.hat.visible = hat && showHead;
        m.body.visible = body && showBody;
        m.jacket.visible = jacket && showBody;
        m.leftArm.visible = la && showArms;
        m.leftSleeve.visible = ls && showArms;
        m.rightArm.visible = ra && showArms;
        m.rightSleeve.visible = rs && showArms;
        m.leftLeg.visible = ll && showLegs;
        m.leftPants.visible = lp && showLegs;
        m.rightLeg.visible = rl && showLegs;
        m.rightPants.visible = rp && showLegs;

        float alpha = Math.min(1.0F, p * 1.5F);

        ps.pushPose();
        ps.translate(0.0, 1.5, 0.0);
        ps.scale(1.012F, 1.008F, 1.012F);
        ps.translate(0.0, -1.5, 0.0);
        VertexConsumer suit = buf.getBuffer(RenderType.entityTranslucent(SuitTexture.get()));
        m.renderToBuffer(ps, suit, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, alpha);
        ps.popPose();

        float pulse = 0.20F + 0.08F * (float) Math.sin((player.tickCount + partialTicks) * 0.15F);
        ps.pushPose();
        ps.translate(0.0, 1.5, 0.0);
        ps.scale(1.10F, 1.05F, 1.10F);
        ps.translate(0.0, -1.5, 0.0);
        VertexConsumer aura = buf.getBuffer(RenderType.entityTranslucent(AURA));
        m.renderToBuffer(ps, aura, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0.3F, 1.0F, 0.5F, pulse * p);
        ps.popPose();

        m.head.visible = head; m.hat.visible = hat; m.body.visible = body; m.jacket.visible = jacket;
        m.leftArm.visible = la; m.leftSleeve.visible = ls; m.rightArm.visible = ra; m.rightSleeve.visible = rs;
        m.leftLeg.visible = ll; m.leftPants.visible = lp; m.rightLeg.visible = rl; m.rightPants.visible = rp;
    }
}
