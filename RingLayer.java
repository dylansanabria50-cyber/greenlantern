package com.example.greenlantern.client;

import com.example.greenlantern.GreenLanternMod;
import com.example.greenlantern.RingPowers;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
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
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * El anillo como en Legends: chiquito, apoyado en la punta del puño derecho, con la piedra redonda mirando hacia afuera
 * del puño y tres discos de luz que laten (mas fuertes cuando usas un poder o moves la mano).
 * Se ve si lo tenes puesto o en el inventario, en tercera y en primera persona.
 */
public class RingLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(GreenLanternMod.MODID, "ring"), "main");
    private static final int FULL = 15728880;
    /** Centro del anillo en el espacio del brazo derecho (pixeles, y hacia abajo, frente en -z). */
    private static final float RX = -1.9f, RY = 10.2f, RZ = -0.6f;

    private static ModelPart marco, gema;
    private static ResourceLocation blanco;

    public RingLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        // marco octogonal (dos placas cruzadas) y la piedra encima
        mesh.getRoot().addOrReplaceChild("marco", CubeListBuilder.create().texOffs(0, 0)
                .addBox(RX - 1.25f, RY - 0.2f, RZ - 0.7f, 2.5f, 0.4f, 1.4f)
                .addBox(RX - 0.7f, RY - 0.2f, RZ - 1.25f, 1.4f, 0.4f, 2.5f)
                .addBox(RX - 1.0f, RY - 0.25f, RZ - 1.0f, 2.0f, 0.3f, 2.0f), PartPose.ZERO);
        mesh.getRoot().addOrReplaceChild("gema", CubeListBuilder.create().texOffs(0, 0)
                .addBox(RX - 0.62f, RY + 0.2f, RZ - 0.62f, 1.24f, 0.22f, 1.24f), PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }

    private static void init() {
        if (marco != null) return;
        ModelPart root = createLayer().bakeRoot();
        marco = root.getChild("marco");
        gema = root.getChild("gema");
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, 16, 16, false);
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) img.setPixelRGBA(x, y, 0xFFFFFFFF);
        blanco = Minecraft.getInstance().getTextureManager().register("greenlantern_anillo", new DynamicTexture(img));
    }

    static boolean visible(AbstractClientPlayer pl) {
        return !pl.isInvisible() && (RingPowers.isWorn(pl) || RingPowers.hasRing(pl));
    }

    /** Fuerza de la luz: late siempre; a pleno si esta usando un poder o mueve la mano. */
    static float intensidad(AbstractClientPlayer pl, float pt) {
        float late = 0.88f + 0.12f * Mth.sin((pl.tickCount + pt) * 0.22f);
        boolean poder = pl.swinging || ClientFx.busy(pl.getId()) || (ClientSuit.progress(pl.getId(), pt) > 0f && ClientSuit.progress(pl.getId(), pt) < 1f);
        return late * (poder ? 1f : 0.65f);
    }

    /** Dibuja el anillo y su luz; el PoseStack ya tiene que estar en el espacio del brazo derecho. */
    static void draw(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer pl, float pt) {
        init();
        int c = SuitTexture.luz(ClientSuit.design(pl.getId()));
        float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
        float k = intensidad(pl, pt);
        marco.render(ps, buf.getBuffer(RenderType.entityCutoutNoCull(blanco)), light, OverlayTexture.NO_OVERLAY, r * 0.45f, g * 0.55f, b * 0.45f, 1f);
        gema.render(ps, buf.getBuffer(RenderType.entityCutoutNoCull(blanco)), FULL, OverlayTexture.NO_OVERLAY,
                0.55f + 0.45f * k, 1f, 0.6f + 0.4f * k, 1f);
        // tres discos de luz mirando a la camara
        Matrix4f m = ps.last().pose();
        Vector3f centro = m.transformPosition(new Vector3f(RX / 16f, (RY + 0.55f) / 16f, RZ / 16f));
        VertexConsumer vc = buf.getBuffer(GreenRender.GLOW);
        Matrix4f id = new Matrix4f();
        Vec3 cc = new Vec3(centro.x(), centro.y(), centro.z());
        Vector3f der = new Vector3f(1, 0, 0), arr = new Vector3f(0, 1, 0);
        ClientFx.glowDisc(vc, id, cc, der, arr, 0.13f, 0.1f, 1f, 0.35f, 0.14f * k);
        ClientFx.glowDisc(vc, id, cc, der, arr, 0.06f, 0.2f, 1f, 0.45f, 0.55f * k);
        ClientFx.glowDisc(vc, id, cc, der, arr, 0.02f, 0.8f, 1f, 0.85f, k);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (!visible(player)) return;
        ps.pushPose();
        this.getParentModel().rightArm.translateAndRotate(ps);
        draw(ps, buf, light, player, partialTicks);
        ps.popPose();
    }

    /** Primera persona: el anillo tambien en la punta del puño. */
    @Mod.EventBusSubscriber(modid = GreenLanternMod.MODID, value = Dist.CLIENT)
    public static class FirstPerson {
        @SubscribeEvent(receiveCanceled = true)
        public static void onArm(RenderArmEvent e) {
            if (e.getArm() != HumanoidArm.RIGHT) return;
            AbstractClientPlayer pl = e.getPlayer();
            if (!visible(pl)) return;
            if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(pl) instanceof PlayerRenderer r)) return;
            PlayerModel<AbstractClientPlayer> m = r.getModel();
            m.attackTime = 0.0f; m.crouching = false; m.swimAmount = 0.0f;
            m.setupAnim(pl, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
            m.rightArm.xRot = 0.0f;
            PoseStack ps = e.getPoseStack();
            ps.pushPose();
            m.rightArm.translateAndRotate(ps);
            draw(ps, e.getMultiBufferSource(), e.getPackedLight(), pl, Minecraft.getInstance().getFrameTime());
            ps.popPose();
        }
    }
}
