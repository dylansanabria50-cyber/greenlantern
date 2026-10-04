package com.example.greenlantern.client;

import com.example.greenlantern.GreenLanternMod;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Modelo de cubos generico (formato de texto) con un grupo opcional que gira sobre el eje Z. */
public final class BoxModel {
    private final ResourceLocation tex;
    private final String name, cubesFile, spinFile;
    private final float tw, th;
    private final double[] spinPivot;
    private final List<float[]> quads = new ArrayList<>();
    private final List<float[]> spinQuads = new ArrayList<>();
    private boolean ready = false;

    public BoxModel(String name, String cubesFile, String spinFile, float tw, float th, double[] spinPivot) {
        this.name = name;
        this.tex = new ResourceLocation(GreenLanternMod.MODID, "dynamic/" + name);
        this.cubesFile = cubesFile;
        this.spinFile = spinFile;
        this.tw = tw;
        this.th = th;
        this.spinPivot = spinPivot;
    }

    private static String read(String file) throws IOException {
        var res = Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation(GreenLanternMod.MODID, file));
        if (res.isEmpty()) throw new IOException("falta " + file);
        try (InputStream in = res.get().open()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void load(String file, List<float[]> out) throws IOException {
        for (String line : read(file).split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            addCube(line.split(" "), out);
        }
    }

    private void init() {
        if (ready) return;
        ready = true;
        try {
            byte[] png = Base64.getMimeDecoder().decode(read(name + "_png.b64").trim());
            NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
            Minecraft.getInstance().getTextureManager().register(tex, new DynamicTexture(img));
            load(cubesFile, quads);
            if (spinFile != null) load(spinFile, spinQuads);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static double[] rotate(double[] p, double[] pv, double rx, double ry, double rz) {
        double x = p[0] - pv[0], y = p[1] - pv[1], z = p[2] - pv[2];
        double a = Math.toRadians(rx), c = Math.cos(a), s = Math.sin(a);
        double y1 = y * c - z * s, z1 = y * s + z * c;
        y = y1;
        z = z1;
        a = Math.toRadians(ry);
        c = Math.cos(a);
        s = Math.sin(a);
        double x2 = x * c + z * s, z2 = -x * s + z * c;
        x = x2;
        z = z2;
        a = Math.toRadians(rz);
        c = Math.cos(a);
        s = Math.sin(a);
        double x3 = x * c - y * s, y3 = x * s + y * c;
        return new double[]{x3 + pv[0], y3 + pv[1], z + pv[2]};
    }

    private void addCube(String[] t, List<float[]> out) {
        double[] n = new double[t.length];
        for (int i = 0; i < t.length; i++) n[i] = Double.parseDouble(t[i]);
        double[] piv = {n[0], n[1], n[2]};
        double x0 = n[6], y0 = n[7], z0 = n[8], w = n[9], h = n[10], d = n[11], u = n[12], v = n[13];
        double x1 = x0 + w, y1 = y0 + h, z1 = z0 + d;
        boolean own = n.length >= 20;
        double[] cpiv = own ? new double[]{n[14], n[15], n[16]} : null;
        double[][][] faces = {
                {{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}},
                {{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}},
                {{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}},
                {{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}},
                {{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}},
                {{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}}
        };
        double[][] uv = {
                {u + d, v + d, w, h}, {u, v + d, d, h}, {u + d + w + d, v + d, w, h},
                {u + d + w, v + d, d, h}, {u + d, v, w, d}, {u + d + w, v, w, d}
        };
        double[] cen = {x0 + w / 2, y0 + h / 2, z0 + d / 2};
        if (own) cen = rotate(cen, cpiv, n[17], n[18], n[19]);
        cen = rotate(cen, piv, n[3], n[4], n[5]);
        for (int f = 0; f < 6; f++) {
            double[][] p = new double[4][];
            for (int i = 0; i < 4; i++) {
                double[] q = faces[f][i];
                if (own) q = rotate(q, cpiv, n[17], n[18], n[19]);
                p[i] = rotate(q, piv, n[3], n[4], n[5]);
            }
            double ax = p[1][0] - p[0][0], ay = p[1][1] - p[0][1], az = p[1][2] - p[0][2];
            double bx = p[3][0] - p[0][0], by = p[3][1] - p[0][1], bz = p[3][2] - p[0][2];
            double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1.0E-9) continue;
            nx /= len;
            ny /= len;
            nz /= len;
            double fx = (p[0][0] + p[2][0]) / 2 - cen[0], fy = (p[0][1] + p[2][1]) / 2 - cen[1], fz = (p[0][2] + p[2][2]) / 2 - cen[2];
            if (nx * fx + ny * fy + nz * fz < 0) {
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
            float[] q = new float[19];
            for (int i = 0; i < 4; i++) {
                q[i * 3] = (float) p[i][0];
                q[i * 3 + 1] = (float) p[i][1];
                q[i * 3 + 2] = (float) p[i][2];
            }
            q[12] = (float) (uv[f][0] / tw);
            q[13] = (float) (uv[f][1] / th);
            q[14] = (float) ((uv[f][0] + uv[f][2]) / tw);
            q[15] = (float) ((uv[f][1] + uv[f][3]) / th);
            q[16] = (float) nx;
            q[17] = (float) ny;
            q[18] = (float) nz;
            out.add(q);
        }
    }

    private void draw(PoseStack ps, VertexConsumer vc, List<float[]> list, int light) {
        PoseStack.Pose pose = ps.last();
        for (float[] q : list) {
            for (int i = 0; i < 4; i++) {
                float u = (i == 0 || i == 3) ? q[12] : q[14];
                float v = (i < 2) ? q[13] : q[15];
                vc.vertex(pose.pose(), q[i * 3], q[i * 3 + 1], q[i * 3 + 2])
                        .color(255, 255, 255, 255)
                        .uv(u, v)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(light)
                        .normal(pose.normal(), q[16], q[17], q[18])
                        .endVertex();
            }
        }
    }

    /** Dibuja el modelo; spinDeg gira el grupo giratorio sobre el eje Z de su pivote. */
    public void render(PoseStack ps, MultiBufferSource buf, int light, float spinDeg) {
        init();
        if (quads.isEmpty()) return;
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(tex));
        draw(ps, vc, quads, light);
        if (!spinQuads.isEmpty()) {
            ps.pushPose();
            ps.translate(spinPivot[0], spinPivot[1], spinPivot[2]);
            ps.mulPose(Axis.ZP.rotationDegrees(spinDeg));
            ps.translate(-spinPivot[0], -spinPivot[1], -spinPivot[2]);
            draw(ps, vc, spinQuads, light);
            ps.popPose();
        }
    }
}
