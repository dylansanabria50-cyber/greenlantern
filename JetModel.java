package com.example.greenlantern.client;

import com.example.greenlantern.GreenLanternMod;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Caza de energia (diseño propio): fuselaje con cabina, alas en flecha con puntas que brillan,
 * deriva, estabilizadores y dos toberas. La nariz mira a +Z. Texturas generadas en el momento.
 */
public final class JetModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(GreenLanternMod.MODID, "jet"), "main");
    private static ResourceLocation casco, brillo;

    private JetModel() { }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("casco", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.0f, -9.0f, -14.0f, 6.0f, 5.0f, 28.0f)      // fuselaje
                .texOffs(0, 0).addBox(-2.0f, -8.5f, 14.0f, 4.0f, 3.5f, 6.0f)        // nariz
                .texOffs(0, 0).addBox(-1.0f, -7.5f, 20.0f, 2.0f, 2.0f, 3.0f)        // punta
                .texOffs(0, 0).addBox(-16.0f, -6.0f, -7.0f, 13.0f, 1.0f, 12.0f)     // ala izquierda
                .texOffs(0, 0).addBox(3.0f, -6.0f, -7.0f, 13.0f, 1.0f, 12.0f)       // ala derecha
                .texOffs(0, 0).addBox(-0.5f, -16.0f, -14.0f, 1.0f, 7.0f, 7.0f)      // deriva
                .texOffs(0, 0).addBox(-8.0f, -7.0f, -14.0f, 5.0f, 1.0f, 5.0f)       // estabilizador
                .texOffs(0, 0).addBox(3.0f, -7.0f, -14.0f, 5.0f, 1.0f, 5.0f),       // estabilizador
                PartPose.ZERO);
        root.addOrReplaceChild("cabina", CubeListBuilder.create()
                .texOffs(0, 40).addBox(-2.0f, -11.5f, 2.0f, 4.0f, 2.5f, 8.0f), PartPose.ZERO);
        root.addOrReplaceChild("luces", CubeListBuilder.create()
                .texOffs(64, 40).addBox(-4.5f, -8.0f, -16.0f, 3.0f, 3.0f, 2.0f)     // toberas
                .texOffs(64, 40).addBox(1.5f, -8.0f, -16.0f, 3.0f, 3.0f, 2.0f)
                .texOffs(64, 40).addBox(-16.5f, -6.5f, -7.0f, 1.0f, 2.0f, 12.0f)    // puntas de ala
                .texOffs(64, 40).addBox(15.5f, -6.5f, -7.0f, 1.0f, 2.0f, 12.0f), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    private static int abgr(int a, int rgb) { return (a << 24) | ((rgb & 255) << 16) | (((rgb >> 8) & 255) << 8) | ((rgb >> 16) & 255); }

    private static void armar() {
        NativeImage a = new NativeImage(NativeImage.Format.RGBA, 128, 64, true), b = new NativeImage(NativeImage.Format.RGBA, 128, 64, true);
        for (int x = 0; x < 128; x++) for (int y = 0; y < 64; y++) {
            if (y < 40) { // casco: paneles verdes con juntas claras
                boolean junta = x % 8 == 0 || y % 6 == 0;
                a.setPixelRGBA(x, y, abgr(215, junta ? 0x3dff8e : ((x / 8 + y / 6) % 2 == 0 ? 0x1fb866 : 0x18a35a)));
                b.setPixelRGBA(x, y, abgr(255, junta ? 0x2bd170 : 0x06200f));
            } else if (x < 64) { // cabina
                a.setPixelRGBA(x, y, abgr(150, 0xbfffe6));
                b.setPixelRGBA(x, y, abgr(255, 0x3c7a5c));
            } else { // toberas y puntas
                a.setPixelRGBA(x, y, abgr(255, 0xd2ffe6));
                b.setPixelRGBA(x, y, abgr(255, 0xd2ffe6));
            }
        }
        var tm = Minecraft.getInstance().getTextureManager();
        casco = tm.register("greenlantern_jet", new DynamicTexture(a));
        brillo = tm.register("greenlantern_jet_brillo", new DynamicTexture(b));
    }

    public static ResourceLocation casco() { if (casco == null) armar(); return casco; }
    public static ResourceLocation brillo() { if (brillo == null) armar(); return brillo; }
}
