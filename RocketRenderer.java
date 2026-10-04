package com.example.greenlantern.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Dibuja el lanzacohetes de energia en 3D (inventario, mano, suelo). */
public class RocketRenderer extends BlockEntityWithoutLevelRenderer {
    private static RocketRenderer instance;
    private static final BoxModel MODEL = new BoxModel("rocket", "rocket.txt", null, 256.0f, 128.0f,
            new double[]{0.0, 0.0, 0.0}).procedural();

    public static RocketRenderer get() {
        if (instance == null) {
            Minecraft mc = Minecraft.getInstance();
            instance = new RocketRenderer(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
        }
        return instance;
    }

    private RocketRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
        super(dispatcher, models);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        float s = 0.0125f;
        double ox = 0.0, oy = -6.0, oz = 30.0;
        if (ctx == ItemDisplayContext.GUI) {
            ps.mulPose(Axis.XP.rotationDegrees(20.0f));
            ps.mulPose(Axis.YP.rotationDegrees(-50.0f));
            s = 0.0125f;
            oy = -8.0;
            oz = 34.0;
        } else if (ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            ps.mulPose(Axis.XP.rotationDegrees(90.0f));
            ps.mulPose(Axis.ZP.rotationDegrees(180.0f));
            s = 0.011f;
        } else if (ctx == ItemDisplayContext.GROUND || ctx == ItemDisplayContext.FIXED || ctx == ItemDisplayContext.HEAD) {
            s = 0.01f;
            oy = -8.0;
            oz = 34.0;
        }
        ps.scale(s, s, s);
        ps.translate(ox, oy, oz);
        MODEL.render(ps, buf, ctx == ItemDisplayContext.GUI ? light : LightTexture.FULL_BRIGHT, 0.0f);
        ps.popPose();
    }
}
