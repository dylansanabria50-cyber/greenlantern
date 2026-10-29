package com.example.greenlantern.client;

import com.example.greenlantern.MisilEntity;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Misil de energia: cuerpo verde translucido, punta y aletas que brillan, y una llama atras. */
public class MisilRenderer extends EntityRenderer<MisilEntity> {
    private static final int FULL = 15728880;
    private static ResourceLocation blanco;
    private final ModelPart cuerpo, brillo;

    public MisilRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        r.addOrReplaceChild("cuerpo", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1.5f, -1.5f, -7, 3, 3, 12)
                .texOffs(0, 0).addBox(-1, -1, 5, 2, 2, 2)
                .texOffs(0, 0).addBox(-4, -0.3f, -7, 8, 0.6f, 3)
                .texOffs(0, 0).addBox(-0.3f, -4, -7, 0.6f, 8, 3), PartPose.ZERO);
        r.addOrReplaceChild("brillo", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-0.6f, -0.6f, 7, 1.2f, 1.2f, 1.5f)
                .texOffs(0, 0).addBox(-1.6f, -1.6f, -1, 3.2f, 3.2f, 0.6f)
                .texOffs(0, 0).addBox(-1.2f, -1.2f, -7.6f, 2.4f, 2.4f, 0.6f), PartPose.ZERO);
        ModelPart root = LayerDefinition.create(mesh, 16, 16).bakeRoot();
        cuerpo = root.getChild("cuerpo");
        brillo = root.getChild("brillo");
    }

    private static ResourceLocation tex() {
        if (blanco == null) {
            NativeImage img = new NativeImage(NativeImage.Format.RGBA, 16, 16, false);
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) img.setPixelRGBA(x, y, (x + y) % 5 == 0 ? 0xFFFFFFFF : 0xFFDDDDDD);
            blanco = Minecraft.getInstance().getTextureManager().register("greenlantern_misil", new DynamicTexture(img));
        }
        return blanco;
    }

    @Override public ResourceLocation getTextureLocation(MisilEntity e) { return tex(); }

    @Override
    public void render(MisilEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(pt, e.yRotO, e.getYRot())));
        ps.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, e.xRotO, e.getXRot())));
        ps.mulPose(Axis.ZP.rotationDegrees((e.tickCount + pt) * 20f));
        float s = 1.4f / 16f;
        ps.scale(s, s, s);
        cuerpo.render(ps, buf.getBuffer(RenderType.entityTranslucent(tex())), FULL, OverlayTexture.NO_OVERLAY, 0.35f, 0.9f, 0.45f, 0.85f);
        brillo.render(ps, buf.getBuffer(RenderType.eyes(tex())), FULL, OverlayTexture.NO_OVERLAY, 0.8f, 1f, 0.85f, 1f);
        ps.popPose();
        // llama atras
        Vec3 v = e.getDeltaMovement();
        if (v.lengthSqr() > 1e-4) {
            Vec3 atras = v.normalize().scale(-0.75);
            float f = 0.8f + 0.2f * Mth.sin((e.tickCount + pt) * 3f);
            var vc = buf.getBuffer(GreenRender.GLOW);
            ClientFx.glowDisc(vc, ps.last().pose(), atras, new Vector3f(Minecraft.getInstance().gameRenderer.getMainCamera().getLeftVector()).negate(),
                    new Vector3f(Minecraft.getInstance().gameRenderer.getMainCamera().getUpVector()), 0.45f * f, 1f, 0.75f, 0.3f, 0.8f);
            ClientFx.glowDisc(vc, ps.last().pose(), atras, new Vector3f(Minecraft.getInstance().gameRenderer.getMainCamera().getLeftVector()).negate(),
                    new Vector3f(Minecraft.getInstance().gameRenderer.getMainCamera().getUpVector()), 0.2f * f, 1f, 1f, 0.8f, 1f);
        }
        super.render(e, yaw, pt, ps, buf, light);
    }
}
