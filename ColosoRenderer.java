package com.example.greenlantern.client;

import com.example.greenlantern.ColosoEntity;
import com.example.greenlantern.GreenLanternMod;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Coloso de Voluntad (diseño propio): robot de energia verde translucida de 11 bloques, con cabina de vidrio en el pecho,
 * visor, nucleo, cohetes en los hombros y propulsores en la espalda. Camina, da puñetazos, golpe sismico, cañon y escudo.
 */
public class ColosoRenderer extends EntityRenderer<ColosoEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(GreenLanternMod.MODID, "coloso"), "main");
    private static final int FULL = 15728880;
    private static final float ESCALA = 4.0f;
    private static ResourceLocation casco, vidrio, luz;

    private final ModelPart root, cadera, torso, cabeza, brazoD, brazoI, antD, antI, piernaD, piernaI, canD, canI, alaD, alaI;

    public ColosoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 2.0f;
        root = createLayer().bakeRoot();
        cadera = root.getChild("cadera");
        torso = root.getChild("torso");
        cabeza = torso.getChild("cabeza");
        brazoD = torso.getChild("brazoD");
        brazoI = torso.getChild("brazoI");
        antD = brazoD.getChild("ant");
        antI = brazoI.getChild("ant");
        piernaD = root.getChild("piernaD");
        piernaI = root.getChild("piernaI");
        canD = piernaD.getChild("can");
        canI = piernaI.getChild("can");
        alaD = torso.getChild("alaD");
        alaI = torso.getChild("alaI");
    }

    /** Medidas en "pixeles" del coloso a escala 1/4 (se dibuja 4 veces mas grande). y negativo = arriba, frente en +z. */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        r.addOrReplaceChild("cadera", CubeListBuilder.create().texOffs(0, 0).addBox(-6, -3, -3.5f, 12, 4, 7), PartPose.offset(0, -20, 0));
        PartDefinition torso = r.addOrReplaceChild("torso", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8, -14, -5, 16, 13, 10)          // pecho
                .texOffs(0, 0).addBox(-6, -17, -4, 12, 3, 8)            // cuello y hombros
                .texOffs(0, 0).addBox(-5, -2, -4, 10, 3, 8)             // cintura
                .texOffs(0, 0).addBox(-10, -18, -1, 4, 3, 6)            // cohetes
                .texOffs(0, 0).addBox(6, -18, -1, 4, 3, 6)
                .texOffs(0, 0).addBox(-5, -12, -7, 3, 8, 2)             // propulsores
                .texOffs(0, 0).addBox(2, -12, -7, 3, 8, 2), PartPose.offset(0, -22, 0));
        torso.addOrReplaceChild("vidrio", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -11, 4.6f, 8, 6, 1), PartPose.ZERO);
        torso.addOrReplaceChild("luces", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2, -4.5f, 4.8f, 4, 3, 0.6f)      // nucleo
                .texOffs(0, 0).addBox(-9.5f, -17.5f, 4.8f, 3, 2, 0.6f)  // bocas de los cohetes
                .texOffs(0, 0).addBox(6.5f, -17.5f, 4.8f, 3, 2, 0.6f)
                .texOffs(0, 0).addBox(-4.5f, -11, -7.4f, 2, 6, 0.6f)    // fuego de los propulsores
                .texOffs(0, 0).addBox(2.5f, -11, -7.4f, 2, 6, 0.6f)
                .texOffs(0, 0).addBox(-8.2f, -14, -1, 0.5f, 10, 2)      // lineas de los costados
                .texOffs(0, 0).addBox(7.7f, -14, -1, 0.5f, 10, 2), PartPose.ZERO);
        // alas en la espalda: plegadas al caminar, se abren en modo vuelo
        for (int lado = 0; lado < 2; lado++) {
            float s = lado == 0 ? 1 : -1;
            PartDefinition ala = torso.addOrReplaceChild(lado == 0 ? "alaD" : "alaI", CubeListBuilder.create()
                    .texOffs(0, 0).addBox(s > 0 ? 0 : -16, -3, -0.4f, 16, 6, 0.8f)
                    .texOffs(0, 0).addBox(s > 0 ? 2 : -14, 3, -0.3f, 12, 3, 0.6f)
                    .texOffs(0, 0).addBox(s > 0 ? 6 : -14, 6, -0.3f, 8, 2, 0.6f)
                    .texOffs(0, 0).addBox(s > 0 ? 15 : -21, -2, -0.3f, 6, 3, 0.6f), PartPose.offset(6 * s, -13, -6));
            ala.addOrReplaceChild("borde", CubeListBuilder.create()
                    .texOffs(0, 0).addBox(s > 0 ? 0 : -21, -3.6f, -0.5f, 21, 0.7f, 1.0f)
                    .texOffs(0, 0).addBox(s > 0 ? 4 : -12, 0, -0.5f, 0.5f, 7, 1.0f)
                    .texOffs(0, 0).addBox(s > 0 ? 9 : -9.5f, 0, -0.5f, 0.5f, 8, 1.0f), PartPose.ZERO);
        }
        PartDefinition cab = torso.addOrReplaceChild("cabeza", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5f, -6, -3.5f, 7, 6, 7)
                .texOffs(0, 0).addBox(-1, -8, -1, 2, 2, 2), PartPose.offset(0, -17, 0));
        cab.addOrReplaceChild("visor", CubeListBuilder.create().texOffs(0, 0).addBox(-3, -4, 3.3f, 6, 1.5f, 0.6f), PartPose.ZERO);
        for (int lado = 0; lado < 2; lado++) {
            float s = lado == 0 ? 1 : -1; // +x es el lado derecho una vez dibujado
            PartDefinition b = torso.addOrReplaceChild(lado == 0 ? "brazoD" : "brazoI", CubeListBuilder.create()
                    .texOffs(0, 0).addBox(-3, -3, -3.5f, 6, 6, 7)          // hombrera
                    .texOffs(0, 0).addBox(-2.5f, 2, -2.5f, 5, 8, 5), PartPose.offset(10.5f * s, -13, 0));
            PartDefinition a = b.addOrReplaceChild("ant", CubeListBuilder.create()
                    .texOffs(0, 0).addBox(-3, 0, -3, 6, 7, 6)
                    .texOffs(0, 0).addBox(-3.5f, 7, -3.5f, 7, 5, 7), PartPose.offset(0, 10, 0));
            a.addOrReplaceChild("luz", CubeListBuilder.create().texOffs(0, 0).addBox(-3.3f, 2, 2.8f, 6.6f, 0.6f, 0.6f), PartPose.ZERO);
            PartDefinition p = r.addOrReplaceChild(lado == 0 ? "piernaD" : "piernaI", CubeListBuilder.create()
                    .texOffs(0, 0).addBox(-2.5f, 0, -2.5f, 5, 10, 5), PartPose.offset(4 * s, -20, 0));
            PartDefinition c = p.addOrReplaceChild("can", CubeListBuilder.create()
                    .texOffs(0, 0).addBox(-2.5f, 0, -3, 5, 8, 6)
                    .texOffs(0, 0).addBox(-3, 8, -3, 6, 2, 8), PartPose.offset(0, 10, 0));
            c.addOrReplaceChild("luz", CubeListBuilder.create().texOffs(0, 0).addBox(-2, 1, 2.8f, 4, 0.6f, 0.6f), PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static void texturas() {
        if (casco != null) return;
        var tm = Minecraft.getInstance().getTextureManager();
        NativeImage a = new NativeImage(NativeImage.Format.RGBA, 64, 64, false);
        NativeImage b = new NativeImage(NativeImage.Format.RGBA, 64, 64, false);
        NativeImage c = new NativeImage(NativeImage.Format.RGBA, 64, 64, false);
        for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) {
            boolean linea = x % 6 == 0 || y % 5 == 0;
            int ruido = (x * 7 + y * 13) % 5;
            int r = linea ? 80 : 30 + ruido * 3, g = linea ? 220 : 150 + ruido * 6, bl = linea ? 95 : 60 + ruido * 3, al = linea ? 235 : 185;
            a.setPixelRGBA(x, y, (al << 24) | (bl << 16) | (g << 8) | r);
            b.setPixelRGBA(x, y, ((linea ? 150 : 80) << 24) | (200 << 16) | (255 << 8) | 190);
            c.setPixelRGBA(x, y, 0xFFC8FFD8);
        }
        casco = tm.register("greenlantern_coloso", new DynamicTexture(a));
        vidrio = tm.register("greenlantern_coloso_vidrio", new DynamicTexture(b));
        luz = tm.register("greenlantern_coloso_luz", new DynamicTexture(c));
    }

    @Override
    public ResourceLocation getTextureLocation(ColosoEntity e) { texturas(); return casco; }

    private static float suave(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }

    private void posar(ColosoEntity e, float pt) {
        for (ModelPart p : new ModelPart[]{torso, cabeza, brazoD, brazoI, antD, antI, piernaD, piernaI, canD, canI, cadera, alaD, alaI}) { p.xRot = 0; p.yRot = 0; p.zRot = 0; }
        float age = e.tickCount + pt;
        float paso = Mth.lerp(pt, e.pasoO, e.paso), a = Math.min(1f, e.velAnim), corre = Mth.clamp((e.velAnim - 1.0f) / 0.5f, 0f, 1f);
        float aire = Mth.lerp(pt, e.enAireO, e.enAire);
        // caminar y correr: zancadas mas largas, brazos doblados y torso inclinado al correr
        float amp = 0.6f + 0.35f * corre;
        piernaD.xRot = Mth.sin(paso) * amp * a;
        piernaI.xRot = -Mth.sin(paso) * amp * a;
        canD.xRot = -Math.max(0, Mth.cos(paso)) * (0.8f + 0.5f * corre) * a;
        canI.xRot = -Math.max(0, -Mth.cos(paso)) * (0.8f + 0.5f * corre) * a;
        brazoD.xRot = -Mth.sin(paso) * (0.35f + 0.4f * corre) * a;
        brazoI.xRot = Mth.sin(paso) * (0.35f + 0.4f * corre) * a;
        antD.xRot = 0.25f + 0.1f * a + 0.7f * corre; antI.xRot = 0.25f + 0.1f * a + 0.7f * corre;
        brazoD.zRot = -0.08f; brazoI.zRot = 0.08f;
        // respiracion y paso: el torso sube y baja un poco
        torso.xRot = 0.05f * a + 0.25f * corre + 0.02f * Mth.sin(age * 0.08f);
        torso.y = -22 + Math.abs(Mth.cos(paso)) * 0.6f * a + 0.2f * Mth.sin(age * 0.08f);
        cadera.y = -20 + Math.abs(Mth.cos(paso)) * 0.6f * a;
        torso.zRot = Mth.sin(paso) * 0.04f * a;
        // en el aire (salto): rodillas arriba y brazos abiertos
        if (aire > 0.01f) {
            piernaD.xRot = Mth.lerp(aire, piernaD.xRot, 0.7f); piernaI.xRot = Mth.lerp(aire, piernaI.xRot, 0.3f);
            canD.xRot = Mth.lerp(aire, canD.xRot, -1.1f); canI.xRot = Mth.lerp(aire, canI.xRot, -0.7f);
            brazoD.zRot = Mth.lerp(aire, brazoD.zRot, -0.7f); brazoI.zRot = Mth.lerp(aire, brazoI.zRot, 0.7f);
            brazoD.xRot = Mth.lerp(aire, brazoD.xRot, -0.3f); brazoI.xRot = Mth.lerp(aire, brazoI.xRot, -0.3f);
        }
        // la cabeza mira hacia donde mira el piloto
        if (e.getControllingPassenger() != null) {
            var pil = e.getControllingPassenger();
            float dif = Mth.wrapDegrees(pil.getViewYRot(pt) - Mth.rotLerp(pt, e.yRotO, e.getYRot()));
            cabeza.yRot = Mth.clamp(dif, -45f, 45f) * Mth.DEG_TO_RAD;
            cabeza.xRot = -Mth.clamp(pil.getViewXRot(pt), -30f, 30f) * Mth.DEG_TO_RAD * 0.6f;
        }
        // alas: plegadas sobre la espalda, se abren al volar y aletean un poco
        float u = Mth.lerp(pt, e.alasO, e.alas), aleteo = 0.12f * Mth.sin(age * 0.25f) * u;
        alaD.yRot = 1.45f * (1 - u) + 0.25f * u;
        alaI.yRot = -alaD.yRot;
        alaD.zRot = 0.35f * (1 - u) - 0.15f * u + aleteo;
        alaI.zRot = -alaD.zRot;
        // ataques
        int anim = e.anim();
        float t = e.edad() - e.animInicio() + pt;
        if (anim == ColosoEntity.ANIM_PUNOS) {
            int golpe = Mth.clamp((int) (t / 10), 0, 2);
            float u = (t - golpe * 10) / 10f, ext = u < 0.6f ? Mth.sin(u / 0.6f * Mth.HALF_PI) : 1f - (u - 0.6f) / 0.4f;
            ModelPart b = golpe == 1 ? brazoI : brazoD, an = golpe == 1 ? antI : antD;
            b.xRot = 1.55f * ext; an.xRot = 0.6f * (1 - ext);
            torso.yRot = (golpe == 1 ? 0.35f : -0.35f) * ext;
        } else if (anim == ColosoEntity.ANIM_SISMICO) {
            float sube = suave(t / 18f), baja = suave((t - 18f) / 6f), vuelve = suave((t - 26f) / 14f);
            float ang = 2.9f * sube - 2.0f * baja - 0.9f * vuelve;
            brazoD.xRot = brazoI.xRot = ang;
            antD.xRot = antI.xRot = 0.3f;
            torso.xRot = -0.25f * sube + 0.6f * baja - 0.35f * vuelve;
            piernaD.xRot = piernaI.xRot = -0.4f * baja * (1 - vuelve);
            canD.xRot = canI.xRot = 0.0f;
        } else if (anim == ColosoEntity.ANIM_CANON) {
            float carga = suave(t / 10f), retro = t > 10 ? 1f - suave((t - 10f) / 16f) : 0f;
            brazoD.xRot = -0.5f * carga; brazoI.xRot = -0.5f * carga;
            brazoD.zRot = -0.4f * carga; brazoI.zRot = 0.4f * carga;
            torso.xRot = -0.25f * retro;
        } else if (anim == ColosoEntity.ANIM_COHETES || anim == ColosoEntity.ANIM_MISILES) {
            float fin = ColosoEntity.DURACION[anim];
            float k = suave(t / 5f) * (1f - suave((t - (fin - 4f)) / 4f));
            torso.xRot = -0.1f * k + 0.04f * Mth.sin(t * 1.6f) * k;
            brazoD.zRot = -0.3f * k; brazoI.zRot = 0.3f * k;
        }
        if (e.volando()) {
            // en vuelo: piernas juntas hacia atras, brazos al costado, sin pasos
            float k = 0.6f + 0.4f * Mth.sin(age * 0.2f) * 0.1f;
            piernaD.xRot = piernaI.xRot = -0.35f * k; canD.xRot = canI.xRot = -0.25f;
            brazoD.xRot = brazoI.xRot = -0.3f; brazoD.zRot = -0.25f; brazoI.zRot = 0.25f;
            antD.xRot = antI.xRot = 0.15f;
        }
        if (e.escudo()) { brazoI.xRot = 1.3f; antI.xRot = -1.1f; brazoI.zRot = -0.3f; }
        cabeza.xRot += -torso.xRot * 0.5f;
    }

    @Override
    public void render(ColosoEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        texturas();
        posar(e, pt);
        float y = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        float vida = e.vida() / ColosoEntity.MAX_VIDA;
        float aparece = Mth.clamp((e.tickCount + pt) / 20f, 0f, 1f);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-y));
        // en vuelo el robot se acuesta segun la velocidad (como en la animacion)
        double dx = e.getX() - e.xo, dz = e.getZ() - e.zo, dy = e.getY() - e.yo;
        float objetivo = e.volando() ? Mth.clamp((float) Math.sqrt(dx * dx + dz * dz) / 0.9f, 0f, 1f) * 65f - (float) Mth.clamp(dy * 30, -15, 15) : 0f;
        e.inclinacion += (objetivo - e.inclinacion) * 0.08f;
        if (Math.abs(e.inclinacion) > 0.1f) {
            ps.translate(0.0, 5.5, 0.0);
            ps.mulPose(Axis.XP.rotationDegrees(e.inclinacion));
            ps.translate(0.0, -5.5, 0.0);
        }
        // escudo hexagonal delante del pecho
        if (e.escudo()) {
            VertexConsumer vc = buf.getBuffer(GreenRender.GLOW);
            Matrix4f m = ps.last().pose();
            float cx = 0, cy = 6.0f, cz = 2.6f, R = 2.6f, late = 0.8f + 0.2f * Mth.sin((e.tickCount + pt) * 0.5f);
            for (int k = 0; k < 6; k++) {
                double a0 = k * Math.PI / 3, a1 = (k + 1) * Math.PI / 3;
                float x0 = (float) Math.cos(a0) * R, y0 = (float) Math.sin(a0) * R, x1 = (float) Math.cos(a1) * R, y1 = (float) Math.sin(a1) * R;
                vc.vertex(m, cx, cy, cz).color(0.3f, 1f, 0.5f, 0.12f * late).endVertex();
                vc.vertex(m, cx + x0, cy + y0, cz).color(0.3f, 1f, 0.5f, 0.6f * late).endVertex();
                vc.vertex(m, cx + x1, cy + y1, cz).color(0.3f, 1f, 0.5f, 0.6f * late).endVertex();
                vc.vertex(m, cx, cy, cz).color(0.3f, 1f, 0.5f, 0.12f * late).endVertex();
            }
        }
        ps.scale(-1.0f, -1.0f, 1.0f);
        ps.scale(ESCALA * (0.85f + 0.15f * aparece), ESCALA, ESCALA * (0.85f + 0.15f * aparece));
        float al = 0.4f + 0.6f * aparece;
        float g = 0.55f + 0.45f * vida;
        cadera.render(ps, buf.getBuffer(RenderType.entityTranslucent(casco)), FULL, OverlayTexture.NO_OVERLAY, 1f, g, 1f, al);
        piernaD.render(ps, buf.getBuffer(RenderType.entityTranslucent(casco)), FULL, OverlayTexture.NO_OVERLAY, 1f, g, 1f, al);
        piernaI.render(ps, buf.getBuffer(RenderType.entityTranslucent(casco)), FULL, OverlayTexture.NO_OVERLAY, 1f, g, 1f, al);
        // el torso se dibuja sin las partes que brillan ni el vidrio, que van aparte
        ModelPart vid = torso.getChild("vidrio"), luces = torso.getChild("luces"), visor = cabeza.getChild("visor");
        vid.visible = false; luces.visible = false; visor.visible = false;
        antD.getChild("luz").visible = antI.getChild("luz").visible = canD.getChild("luz").visible = canI.getChild("luz").visible = false;
        alaD.getChild("borde").visible = alaI.getChild("borde").visible = false;
        torso.render(ps, buf.getBuffer(RenderType.entityTranslucent(casco)), FULL, OverlayTexture.NO_OVERLAY, 1f, g, 1f, al);
        // brillos
        vid.visible = false; luces.visible = true; visor.visible = true;
        antD.getChild("luz").visible = antI.getChild("luz").visible = canD.getChild("luz").visible = canI.getChild("luz").visible = true;
        float pulso = 0.8f + 0.2f * Mth.sin((e.tickCount + pt) * 0.3f);
        if (e.anim() == ColosoEntity.ANIM_CANON) pulso = 1.5f;
        ps.pushPose();
        torso.translateAndRotate(ps);
        luces.render(ps, buf.getBuffer(RenderType.eyes(luz)), FULL, OverlayTexture.NO_OVERLAY, pulso, pulso, pulso, 1f);
        ps.pushPose(); cabeza.translateAndRotate(ps); visor.render(ps, buf.getBuffer(RenderType.eyes(luz)), FULL, OverlayTexture.NO_OVERLAY, pulso, pulso, pulso, 1f); ps.popPose();
        alaD.getChild("borde").visible = alaI.getChild("borde").visible = true;
        float alas = Mth.lerp(pt, e.alasO, e.alas), bAla = 0.5f + 0.7f * alas;
        for (ModelPart ala : new ModelPart[]{alaD, alaI}) {
            ps.pushPose(); ala.translateAndRotate(ps);
            ala.getChild("borde").render(ps, buf.getBuffer(RenderType.eyes(luz)), FULL, OverlayTexture.NO_OVERLAY, bAla, bAla, bAla, 1f);
            ps.popPose();
        }
        for (ModelPart[] b : new ModelPart[][]{{brazoD, antD}, {brazoI, antI}}) {
            ps.pushPose(); b[0].translateAndRotate(ps); b[1].translateAndRotate(ps);
            b[1].getChild("luz").render(ps, buf.getBuffer(RenderType.eyes(luz)), FULL, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        // vidrio de la cabina
        vid.visible = true;
        vid.render(ps, buf.getBuffer(RenderType.entityTranslucent(vidrio)), FULL, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        for (ModelPart[] p : new ModelPart[][]{{piernaD, canD}, {piernaI, canI}}) {
            ps.pushPose(); p[0].translateAndRotate(ps); p[1].translateAndRotate(ps);
            p[1].getChild("luz").render(ps, buf.getBuffer(RenderType.eyes(luz)), FULL, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }
}
