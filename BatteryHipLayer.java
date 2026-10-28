package com.example.greenlantern.client;

import com.example.greenlantern.GreenLanternMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Si la bateria esta guardada en el inventario (y no en las manos), se ve chiquita colgada del cinturon, del lado izquierdo. */
public class BatteryHipLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final float ESCALA = 0.42f;

    public BatteryHipLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    private static ItemStack guardada(AbstractClientPlayer pl) {
        var item = GreenLanternMod.BATTERY_ITEM.get();
        if (pl.getMainHandItem().is(item) || pl.getOffhandItem().is(item)) return ItemStack.EMPTY;
        for (ItemStack st : pl.getInventory().items) if (st.is(item)) return st;
        return ItemStack.EMPTY;
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer pl,
                       float limbSwing, float limbSwingAmount, float pt, float age, float netHeadYaw, float headPitch) {
        if (pl.isInvisible()) return;
        ItemStack st = guardada(pl);
        if (st.isEmpty()) return;
        ps.pushPose();
        this.getParentModel().body.translateAndRotate(ps);
        // punto del cinturon, lado izquierdo (en el modelo: x positivo = izquierda del jugador, y hacia abajo)
        ps.translate(5.0f / 16f, 11.5f / 16f, -0.3f / 16f);
        // se balancea al caminar
        float vaiven = Mth.cos(limbSwing * 0.6662f) * limbSwingAmount * 0.5f;
        ps.mulPose(Axis.XP.rotation(vaiven));
        ps.mulPose(Axis.ZP.rotation(-0.08f - 0.05f * limbSwingAmount * Mth.sin(limbSwing * 1.3f)));
        // de vuelta al derecho (el modelo del jugador esta dado vuelta) y colgada del aro
        ps.mulPose(Axis.ZP.rotationDegrees(180f));
        ps.mulPose(Axis.YP.rotationDegrees(90f));
        ps.scale(ESCALA, ESCALA, ESCALA);
        ps.translate(0.0, -0.5, 0.0); // la parte de arriba del aro queda en el cinturon
        Minecraft.getInstance().getItemRenderer().renderStatic(pl, st, ItemDisplayContext.NONE, false, ps, buf, pl.level(), light, OverlayTexture.NO_OVERLAY, pl.getId());
        ps.popPose();
    }
}
