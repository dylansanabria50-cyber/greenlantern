package com.example.greenlantern.client;

import com.example.greenlantern.GreenLanternMod;
import com.example.greenlantern.RingPowers;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Dibuja el anillo en 3D en la mano derecha del jugador cuando lo lleva puesto. */
public class RingLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(GreenLanternMod.MODID, "ring"), "main");
    private static final ResourceLocation TEX =
            new ResourceLocation(GreenLanternMod.MODID, "textures/block/construct_block.png");

    private final ModelPart ring;

    public RingLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        this.ring = models.bakeLayer(LAYER).getChild("ring");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("ring",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-3.5F, 7.0F, -2.5F, 5.0F, 1.5F, 0.5F)   // frente
                        .addBox(-3.5F, 7.0F, 2.0F, 5.0F, 1.5F, 0.5F)    // atras
                        .addBox(-3.5F, 7.0F, -2.0F, 0.5F, 1.5F, 4.0F)   // lado
                        .addBox(1.0F, 7.0F, -2.0F, 0.5F, 1.5F, 4.0F)    // lado
                        .addBox(-1.5F, 6.5F, -3.0F, 1.5F, 1.5F, 0.5F),  // gema
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (player.isInvisible() || !RingPowers.isWorn(player)) return;
        ps.pushPose();
        this.getParentModel().rightArm.translateAndRotate(ps);
        this.ring.render(ps, buf.getBuffer(RenderType.entityTranslucent(TEX)), 15728880,
                OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 0.95F);
        ps.popPose();
    }
}
