package com.example.greenlantern.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Traje de Voluntad, como en Legends: primero aparece el contorno verde de cada pieza y despues se rellena por partes,
 * mientras una capa de energia verde (un 1,5 % mas grande) envuelve al jugador durante 1 segundo.
 * Encima van las piezas en relieve (pechera, hombreras, cinturon, guantes, botas y emblema).
 */
public class SuitLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final int FULL_BRIGHT = 15728880;

    public SuitLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer pl,
                       float limbSwing, float limbSwingAmount, float pt, float age, float netHeadYaw, float headPitch) {
        if (pl.isInvisible()) return;
        PlayerModel<AbstractClientPlayer> m = this.getParentModel();
        float p = ClientSuit.progress(pl.getId(), pt);
        int d = ClientSuit.design(pl.getId());

        if (p > 0f) {
            SuitTexture.Juego j = SuitTexture.get(d);
            int f = Math.min(SuitTexture.FRAMES - 1, Math.round(p * (SuitTexture.FRAMES - 1)));
            ps.pushPose();
            ps.translate(0.0, 1.5, 0.0);
            ps.scale(1.012f, 1.008f, 1.012f);
            ps.translate(0.0, -1.5, 0.0);
            m.renderToBuffer(ps, buf.getBuffer(RenderType.entityTranslucent(j.base()[f])), light, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
            m.renderToBuffer(ps, buf.getBuffer(RenderType.eyes(j.brillo()[f])), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
            ps.popPose();
            SuitArmor.render(ps, buf, light, m, d, p, -1);
        }

        // capa de energia: 20 ticks desde que empieza a armarse o a irse
        float t = ClientSuit.ticksSinceChange(pl.getId(), pt);
        if (t >= 0f && t < ClientSuit.ENERGY_TICKS) {
            boolean on = ClientSuit.isOn(pl.getId());
            float u = t / ClientSuit.ENERGY_TICKS;
            float form = on ? u : 1f - u;
            float op = 0.75f * Math.min(1f, t / 3f) * Math.min(1f, (ClientSuit.ENERGY_TICKS - t) / 5f);
            ps.pushPose();
            ps.translate(0.0, 1.5, 0.0);
            ps.scale(1.03f, 1.015f, 1.03f);
            ps.translate(0.0, -1.5, 0.0);
            m.renderToBuffer(ps, buf.getBuffer(RenderType.entityTranslucent(SuitTexture.energia(form))), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, op);
            ps.popPose();
        }
    }
}
