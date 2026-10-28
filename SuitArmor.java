package com.example.greenlantern.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Piezas del traje en relieve (diseño propio): pechera en V, hombreras, cuello, cinturon con hebilla que brilla,
 * guantes claros, botas, rodilleras y el emblema elegido en relieve. Al transformarse cada pieza aparece por separado.
 */
public final class SuitArmor {
    private SuitArmor() { }

    static final int CLARO = 0, OSCURO = 1, BLANCO = 2, NEGRO = 3, LUZ = 4, EMB_A = 5, EMB_B = 6;
    static final int TORSO = 0, BRAZO_D = 1, BRAZO_I = 2, PIERNA_D = 3, PIERNA_I = 4;

    private record Pieza(int hueso, int tipo, ModelPart parte, float umbral, int emblema) { }

    private static List<Pieza> PIEZAS;
    private static ResourceLocation blanco;
    private static final int FULL = 15728880;

    /** Agrega una pieza: medidas y posicion en pixeles con el formato de la vista previa (y hacia arriba, frente en +z). */
    private static void add(List<float[]> out, int hueso, float w, float h, float d, float x, float y, float z, int tipo, float rz) {
        out.add(new float[]{hueso, w, h, d, x, y, z, tipo, rz, -1});
    }

    private static void init() {
        if (PIEZAS != null) return;
        List<float[]> l = new ArrayList<>();
        // pecho (coordenadas relativas al centro del torso)
        add(l, TORSO, 7.4f, 5.0f, 0.7f, 0, 3.2f, 2.35f, CLARO, 0);
        add(l, TORSO, 2.3f, 7.2f, 0.7f, 1.95f, -1.3f, 2.3f, CLARO, -0.42f);
        add(l, TORSO, 2.3f, 7.2f, 0.7f, -1.95f, -1.3f, 2.3f, CLARO, 0.42f);
        add(l, TORSO, 7.5f, 0.35f, 0.8f, 0, 0.55f, 2.42f, LUZ, 0);
        add(l, TORSO, 7.0f, 5.0f, 0.6f, 0, 3.0f, -2.3f, OSCURO, 0);
        add(l, TORSO, 2.0f, 6.0f, 0.6f, 0, -2.5f, -2.3f, OSCURO, 0);
        add(l, TORSO, 8.8f, 1.2f, 4.8f, 0, 6.1f, 0, OSCURO, 0);
        add(l, TORSO, 4.0f, 0.4f, 0.5f, 0, 5.4f, 2.5f, LUZ, 0);
        add(l, TORSO, 8.6f, 1.3f, 4.6f, 0, -5.4f, 0, NEGRO, 0);
        add(l, TORSO, 2.0f, 1.5f, 0.45f, 0, -5.4f, 2.45f, LUZ, 0);
        // brazos: hombrera, borde que brilla, guante y puño
        for (int lado = 0; lado < 2; lado++) {
            int hueso = lado == 0 ? BRAZO_D : BRAZO_I;
            float sg = lado == 0 ? -1 : 1;
            add(l, hueso, 5.2f, 2.4f, 5.2f, 0.4f * sg, 1.2f, 0, CLARO, -0.15f * sg);
            add(l, hueso, 5.3f, 0.35f, 5.3f, 0.4f * sg, -0.05f, 0, LUZ, -0.15f * sg);
            add(l, hueso, 4.4f, 4.2f, 4.4f, 0, -8.0f, 0, BLANCO, 0);
            add(l, hueso, 4.5f, 0.4f, 4.5f, 0, -5.85f, 0, LUZ, 0);
        }
        // piernas: botas, borde y rodilleras
        for (int hueso : new int[]{PIERNA_D, PIERNA_I}) {
            add(l, hueso, 4.4f, 5.0f, 4.4f, 0, -9.6f, 0, CLARO, 0);
            add(l, hueso, 4.5f, 0.4f, 4.5f, 0, -7.1f, 0, LUZ, 0);
            add(l, hueso, 3.0f, 2.0f, 0.5f, 0, -5.4f, 2.2f, OSCURO, 0);
        }
        // emblemas en relieve: cada pixel del dibujo de 6x6 es un cubito de medio pixel
        for (int e = 0; e < SuitTexture.EMB.length; e++) {
            String[] dib = SuitTexture.EMB[e];
            for (int v = 0; v < 6; v++) for (int u = 0; u < 6; u++) {
                char ch = dib[v].charAt(u);
                if (ch == '.') continue;
                float[] q = {TORSO, 0.5f, 0.5f, 0.3f, (u - 2.5f) * 0.5f, 3.3f + (2.5f - v) * 0.5f, 2.82f, ch == 'a' ? EMB_A : EMB_B, 0, e};
                l.add(q);
            }
        }

        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        for (int i = 0; i < l.size(); i++) {
            float[] q = l.get(i);
            int hueso = (int) q[0];
            float w = q[1], h = q[2], d = q[3], x = q[4], y = q[5], z = q[6], rz = q[8];
            // de la vista previa al modelo de Minecraft: y hacia abajo, frente en -z, origen en la articulacion
            float mx, my;
            switch (hueso) {
                case TORSO -> { mx = x; my = 6 - y; }
                case BRAZO_D -> { mx = x - 1; my = -y; }
                case BRAZO_I -> { mx = x + 1; my = -y; }
                default -> { mx = x; my = -y; }
            }
            root.addOrReplaceChild("p" + i, CubeListBuilder.create().texOffs(0, 0).addBox(-w / 2, -h / 2, -d / 2, w, h, d),
                    PartPose.offsetAndRotation(mx, my, -z, 0, 0, -rz));
        }
        ModelPart baked = LayerDefinition.create(mesh, 16, 16).bakeRoot();
        Random rnd = new Random(7);
        List<Pieza> out = new ArrayList<>();
        for (int i = 0; i < l.size(); i++) {
            float[] q = l.get(i);
            int tipo = (int) q[7];
            float umbral = tipo >= EMB_A ? 0.9f : 0.35f + rnd.nextFloat() * 0.55f;
            out.add(new Pieza((int) q[0], tipo, baked.getChild("p" + i), umbral, (int) q[9]));
        }
        PIEZAS = out;
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, 16, 16, false);
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) img.setPixelRGBA(x, y, 0xFFFFFFFF);
        blanco = Minecraft.getInstance().getTextureManager().register("greenlantern_piezas", new DynamicTexture(img));
    }

    private static int color(int tipo, int[] p) {
        return switch (tipo) {
            case CLARO -> p[0];
            case OSCURO -> escala(p[0], 0.5f);
            case BLANCO -> 0xE6EDE6;
            case NEGRO -> 0x0D1410;
            case LUZ, EMB_B -> p[1];
            default -> 0xF2FFF2;
        };
    }

    private static int escala(int rgb, float f) {
        return (Math.round(((rgb >> 16) & 255) * f) << 16) | (Math.round(((rgb >> 8) & 255) * f) << 8) | Math.round((rgb & 255) * f);
    }

    private static ModelPart hueso(PlayerModel<?> m, int h) {
        return switch (h) {
            case TORSO -> m.body;
            case BRAZO_D -> m.rightArm;
            case BRAZO_I -> m.leftArm;
            case PIERNA_D -> m.rightLeg;
            default -> m.leftLeg;
        };
    }

    /** Dibuja las piezas sobre el modelo (ya posado). p = avance de la transformacion. soloHueso = -1 para todas. */
    public static void render(PoseStack ps, MultiBufferSource buf, int light, PlayerModel<AbstractClientPlayer> m, int design, float p, int soloHueso) {
        if (p <= 0.3f) return;
        init();
        int[] pal = SuitTexture.VERDE[SuitTexture.verde(design)];
        int emb = SuitTexture.emblema(design);
        var vc = buf.getBuffer(RenderType.entityCutoutNoCull(blanco));
        for (int h = 0; h < 5; h++) {
            if (soloHueso >= 0 && h != soloHueso) continue;
            ModelPart bone = hueso(m, h);
            ps.pushPose();
            bone.translateAndRotate(ps);
            for (Pieza pz : PIEZAS) {
                if (pz.hueso != h || (pz.emblema >= 0 && pz.emblema != emb)) continue;
                float k = Math.max(0f, Math.min(1f, (p - pz.umbral) / 0.08f));
                if (k <= 0f) continue;
                float s = 0.6f + 0.4f * k;
                pz.parte.xScale = s; pz.parte.yScale = s; pz.parte.zScale = s;
                int c = color(pz.tipo, pal);
                boolean brilla = pz.tipo == LUZ || pz.tipo == EMB_A || pz.tipo == EMB_B;
                float flash = 1f - k; // la pieza recien aparecida destella
                float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
                r += (1 - r) * flash * 0.5f; g += (1 - g) * flash * 0.5f; b += (1 - b) * flash * 0.5f;
                pz.parte.render(ps, vc, brilla || flash > 0.05f ? FULL : light, OverlayTexture.NO_OVERLAY, r, g, b, 1f);
            }
            ps.popPose();
        }
    }

    /** Primera persona: solo las piezas del brazo (el PoseStack ya esta en la posicion de la mano). */
    public static void renderArm(PoseStack ps, MultiBufferSource buf, int light, PlayerModel<AbstractClientPlayer> m, int design, float p, boolean derecho) {
        render(ps, buf, light, m, design, p, derecho ? BRAZO_D : BRAZO_I);
    }
}
