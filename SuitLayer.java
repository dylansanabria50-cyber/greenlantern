package com.example.greenlantern.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Traje de Voluntad: se arma de los pies a la cabeza con una linea de luz, las juntas y el emblema brillan
 * (se dibujan a plena luz) y un aura verde translucida rodea al jugador.
 */
public class SuitLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final int FULL_BRIGHT = 15728880;
    private static ResourceLocation blanco;

    public SuitLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    private static ResourceLocation blanco() {
        if (blanco == null) {
            NativeImage img = new NativeImage(NativeImage.Format.RGBA, 4, 4, false);
            for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) img.setPixelRGBA(x, y, 0xFFFFFFFF);
            blanco = Minecraft.getInstance().getTextureManager().register("greenlantern_aura", new DynamicTexture(img));
        }
        return blanco;
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer pl,
                       float limbSwing, float limbSwingAmount, float pt, float age, float netHeadYaw, float headPitch) {
        float p = ClientSuit.progress(pl.getId(), pt);
        if (p <= 0f || pl.isInvisible()) return;
        int d = ClientSuit.design(pl.getId());
        SuitTexture.Juego j = SuitTexture.get(d);
        int f = Math.min(SuitTexture.FRAMES - 1, Math.round(p * (SuitTexture.FRAMES - 1)));
        PlayerModel<AbstractClientPlayer> m = this.getParentModel();

        ps.pushPose();
        ps.translate(0.0, 1.5, 0.0);
        ps.scale(1.012f, 1.008f, 1.012f);
        ps.translate(0.0, -1.5, 0.0);
        m.renderToBuffer(ps, buf.getBuffer(RenderType.entityTranslucent(j.base()[f])), light, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        m.renderToBuffer(ps, buf.getBuffer(RenderType.eyes(j.brillo()[f])), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        ps.popPose();

        // aura: mas fuerte mientras se forma y en el aire
        boolean volando = !pl.onGround() && !pl.isPassenger();
        float aura = (p < 1f ? 0.35f * (float) Math.sin(p * Math.PI) : 0f) + (volando ? 0.16f : 0.07f) * p;
        aura *= 0.8f + 0.2f * (float) Math.sin((pl.tickCount + pt) * 0.2f);
        int c = SuitTexture.luz(d);
        ps.pushPose();
        ps.translate(0.0, 1.5, 0.0);
        ps.scale(1.09f, 1.04f, 1.09f);
        ps.translate(0.0, -1.5, 0.0);
        m.renderToBuffer(ps, buf.getBuffer(RenderType.entityTranslucent(blanco())), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                ((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, aura);
        ps.popPose();
    }
}
