package com.example.cronos;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Lector y dibujante sencillo de modelos geo (Bedrock) sin dependencias externas. */
public class CronosModel {
    static final String[] FACES = {"north", "east", "south", "west", "up", "down"};

    static class Cube {
        float x0, y0, z0, x1, y1, z1;
        float[][] uv = new float[6][];
        float[] pivot, rot;
    }

    static class Bone {
        String name, parent;
        float[] pivot = {0, 0, 0}, rot = {0, 0, 0};
        List<Cube> cubes = new ArrayList<>();
        List<Bone> kids = new ArrayList<>();
    }

    final List<Bone> roots = new ArrayList<>();
    float tw = 128, th = 128;

    static float[] arr(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        float[] r = new float[a.size()];
        for (int i = 0; i < r.length; i++) r[i] = a.get(i).getAsFloat();
        return r;
    }

    static CronosModel load(String path) throws Exception {
        Resource res = Minecraft.getInstance().getResourceManager()
                .getResource(new ResourceLocation(CronosMod.ID, path)).orElseThrow();
        CronosModel m = new CronosModel();
        try (Reader rd = new InputStreamReader(res.open(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(rd).getAsJsonObject();
            JsonObject geo = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            JsonObject d = geo.getAsJsonObject("description");
            if (d.has("texture_width")) m.tw = d.get("texture_width").getAsFloat();
            if (d.has("texture_height")) m.th = d.get("texture_height").getAsFloat();
            Map<String, Bone> map = new HashMap<>();
            List<Bone> all = new ArrayList<>();
            for (JsonElement be : geo.getAsJsonArray("bones")) {
                JsonObject bo = be.getAsJsonObject();
                Bone b = new Bone();
                b.name = bo.get("name").getAsString();
                if (bo.has("parent")) b.parent = bo.get("parent").getAsString();
                if (bo.has("pivot")) b.pivot = arr(bo.get("pivot"));
                if (bo.has("rotation")) b.rot = arr(bo.get("rotation"));
                if (bo.has("cubes")) {
                    for (JsonElement ce : bo.getAsJsonArray("cubes")) b.cubes.add(parseCube(ce.getAsJsonObject()));
                }
                map.put(b.name, b);
                all.add(b);
            }
            for (Bone b : all) {
                Bone p = b.parent == null ? null : map.get(b.parent);
                if (p != null) p.kids.add(b); else m.roots.add(b);
            }
        }
        return m;
    }

    static Cube parseCube(JsonObject c) {
        Cube q = new Cube();
        float[] o = arr(c.get("origin")), s = arr(c.get("size"));
        float inf = c.has("inflate") ? c.get("inflate").getAsFloat() : 0f;
        q.x0 = o[0] - inf; q.y0 = o[1] - inf; q.z0 = o[2] - inf;
        q.x1 = o[0] + s[0] + inf; q.y1 = o[1] + s[1] + inf; q.z1 = o[2] + s[2] + inf;
        if (c.has("pivot")) q.pivot = arr(c.get("pivot"));
        if (c.has("rotation")) q.rot = arr(c.get("rotation"));
        JsonElement ue = c.get("uv");
        if (ue != null && ue.isJsonArray()) {
            float[] a = arr(ue);
            float u = a[0], v = a[1], d = s[2], w = s[0], h = s[1];
            q.uv[0] = new float[]{u + d, v + d, u + d + w, v + d + h};
            q.uv[1] = new float[]{u, v + d, u + d, v + d + h};
            q.uv[2] = new float[]{u + d + w + d, v + d, u + d + w + d + w, v + d + h};
            q.uv[3] = new float[]{u + d + w, v + d, u + d + w + d, v + d + h};
            q.uv[4] = new float[]{u + d, v, u + d + w, v + d};
            q.uv[5] = new float[]{u + d + w, v + d, u + d + w + w, v};
        } else if (ue != null && ue.isJsonObject()) {
            JsonObject uo = ue.getAsJsonObject();
            for (int i = 0; i < 6; i++) {
                if (!uo.has(FACES[i])) continue;
                JsonObject f = uo.getAsJsonObject(FACES[i]);
                float[] a = arr(f.get("uv"));
                float[] z = f.has("uv_size") ? arr(f.get("uv_size")) : new float[]{0, 0};
                q.uv[i] = new float[]{a[0], a[1], a[0] + z[0], a[1] + z[1]};
            }
        }
        return q;
    }

    void render(PoseStack ps, VertexConsumer vc, int light, float r, float g, float b, float a,
                Function<String, float[]> anim) {
        Matrix4f base = new Matrix4f();
        for (Bone bo : roots) draw(bo, base, ps.last(), vc, light, new float[]{r, g, b, a}, anim);
    }

    static void rot(Matrix4f m, float[] pv, float rx, float ry, float rz) {
        rot(m, pv, rx, ry, rz, null);
    }

    static void rot(Matrix4f m, float[] pv, float rx, float ry, float rz, float[] sc) {
        m.translate(pv[0], pv[1], pv[2]);
        if (sc != null) m.scale(sc[0], sc[1], sc[2]);
        if (rz != 0) m.rotateZ((float) Math.toRadians(rz));
        if (ry != 0) m.rotateY((float) Math.toRadians(ry));
        if (rx != 0) m.rotateX((float) Math.toRadians(rx));
        m.translate(-pv[0], -pv[1], -pv[2]);
    }

    void draw(Bone b, Matrix4f parent, PoseStack.Pose pose, VertexConsumer vc, int light, float[] col,
              Function<String, float[]> anim) {
        Matrix4f m = new Matrix4f(parent);
        float[] ar = anim == null ? null : anim.apply(b.name);
        float rx = b.rot[0] + (ar != null ? ar[0] : 0f);
        float ry = b.rot[1] + (ar != null ? ar[1] : 0f);
        float rz = b.rot[2] + (ar != null ? ar[2] : 0f);
        if (ar != null && ar.length >= 9) m.translate(ar[3], ar[4], ar[5]);
        rot(m, b.pivot, rx, ry, rz, ar != null && ar.length >= 9 ? new float[]{ar[6], ar[7], ar[8]} : null);
        for (Cube c : b.cubes) {
            Matrix4f cm = m;
            if (c.rot != null) {
                cm = new Matrix4f(m);
                rot(cm, c.pivot != null ? c.pivot : b.pivot, c.rot[0], c.rot[1], c.rot[2]);
            }
            drawCube(c, cm, pose, vc, light, col);
        }
        for (Bone k : b.kids) draw(k, m, pose, vc, light, col, anim);
    }

    void drawCube(Cube c, Matrix4f m, PoseStack.Pose pose, VertexConsumer vc, int light, float[] col) {
        float x0 = c.x0, x1 = c.x1, y0 = c.y0, y1 = c.y1, z0 = c.z0, z1 = c.z1;
        for (int f = 0; f < 6; f++) {
            float[] uv = c.uv[f];
            if (uv == null) continue;
            float[][] p;
            float nx = 0, ny = 0, nz = 0;
            switch (f) {
                case 0 -> { p = new float[][]{{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}}; nz = -1; }
                case 1 -> { p = new float[][]{{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}}; nx = 1; }
                case 2 -> { p = new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}}; nz = 1; }
                case 3 -> { p = new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}}; nx = -1; }
                case 4 -> { p = new float[][]{{x1, y1, z1}, {x0, y1, z1}, {x0, y1, z0}, {x1, y1, z0}}; ny = 1; }
                default -> { p = new float[][]{{x1, y0, z0}, {x0, y0, z0}, {x0, y0, z1}, {x1, y0, z1}}; ny = -1; }
            }
            Vector3f n = m.transformDirection(nx, ny, nz, new Vector3f());
            float[] us = {uv[0], uv[2], uv[2], uv[0]};
            float[] vs = {uv[1], uv[1], uv[3], uv[3]};
            for (int i = 0; i < 4; i++) {
                Vector3f v = m.transformPosition(p[i][0], p[i][1], p[i][2], new Vector3f());
                vc.vertex(pose.pose(), v.x, v.y, v.z)
                        .color(col[0], col[1], col[2], col[3])
                        .uv(us[i] / tw, vs[i] / th)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(light)
                        .normal(pose.normal(), n.x, n.y, n.z)
                        .endVertex();
            }
        }
    }
}
