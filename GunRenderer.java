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

/** Dibuja la minigun de energia en 3D (inventario, mano, suelo). El barril gira al disparar. */
public class GunRenderer extends BlockEntityWithoutLevelRenderer {
    private static GunRenderer instance;
    private static final BoxModel MODEL = new BoxModel("gun", "gun.txt", "gun_barrel.txt", 160.0f, 160.0f,
            new double[]{0.0, 10.996, -18.587});

    public static GunRenderer get() {
        if (instance == null) {
            Minecraft mc = Minecraft.getInstance();
            instance = new GunRenderer(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
        }
        return instance;
    }

    private GunRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
        super(dispatcher, models);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        boolean firing = mc.player != null && mc.player.isUsingItem()
                && mc.player.getUseItem().getItem() == stack.getItem();
        float spin = firing ? (float) ((System.currentTimeMillis() % 500L) * 0.72) : 0.0f;

        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        float s = 0.0125f;
        double ox = 0.0, oy = -5.0, oz = 2.0;
        if (ctx == ItemDisplayContext.GUI) {
            ps.mulPose(Axis.XP.rotationDegrees(20.0f));
            ps.mulPose(Axis.YP.rotationDegrees(-50.0f));
            s = 0.0105f;
            oy = -8.0;
            oz = 29.0;
        } else if (ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            ps.mulPose(Axis.XP.rotationDegrees(90.0f));
            ps.mulPose(Axis.ZP.rotationDegrees(180.0f));
            s = 0.011f;
        } else if (ctx == ItemDisplayContext.GROUND || ctx == ItemDisplayContext.FIXED || ctx == ItemDisplayContext.HEAD) {
            s = 0.009f;
            oy = -8.0;
            oz = 29.0;
        }
        ps.scale(s, s, s);
        ps.translate(ox, oy, oz);
        MODEL.render(ps, buf, ctx == ItemDisplayContext.GUI ? light : LightTexture.FULL_BRIGHT, spin);
        ps.popPose();
    }
}
