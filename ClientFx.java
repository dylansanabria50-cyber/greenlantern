package com.example.greenlantern.client;

import com.example.greenlantern.GreenLanternMod;
import com.example.greenlantern.Powers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Efectos de los poderes en el cliente: rayo, columna y onda de cargar el anillo, y la burbuja de celdas hexagonales. */
@Mod.EventBusSubscriber(modid = GreenLanternMod.MODID, value = Dist.CLIENT)
public final class ClientFx {
    private ClientFx() { }

    private static long clock = 0;

    private record Beam(int id, Vec3 end, long t0) { }
    private record BigBeam(Vec3 a, Vec3 b, long t0) { }
    private static final List<BigBeam> BIG = new ArrayList<>();
    private record Glow(int id, long t0, int dur) { }
    private record Wave(int id, Vec3 at, long t0, int type) { }
    private record Hit(Vec3 at, long t0) { }

    private static final List<Beam> BEAMS = new ArrayList<>();
    private static final List<Glow> CHARGES = new ArrayList<>();
    private static final List<Wave> WAVES = new ArrayList<>();

    static final class Bub {
        boolean on;
        int res = 100;
        long since;
        final List<Hit> hits = new ArrayList<>();
    }
    private static final Map<Integer, Bub> BUBBLES = new HashMap<>();

    // ---------- celdas del domo (puntos repartidos parejo sobre la esfera, solo la mitad de arriba) ----------
    private static final int CELLS;
    private static final float[][] CELL; // x, y, z (normal), orden
    static {
        int n = 170;
        List<float[]> l = new ArrayList<>();
        double gold = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < n; i++) {
            double y = 1 - (i + 0.5) / n * 2, r = Math.sqrt(1 - y * y), a = i * gold;
            if (y < -0.08) continue;
            float x = (float) (Math.cos(a) * r), z = (float) (Math.sin(a) * r);
            // orden de aparicion: desde adelante a la derecha (donde esta el anillo) hacia el resto
            float orden = (float) (Math.acos(Mth.clamp(x * -0.6 + y * 0.2 + z * 0.77, -1, 1)) / Math.PI);
            l.add(new float[]{x, (float) y, z, orden});
        }
        CELLS = l.size();
        CELL = l.toArray(new float[0][]);
    }

    // ---------- paquetes ----------
    public static void fx(int type, int id, double ax, double ay, double az, double bx, double by, double bz) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        switch (type) {
            case Powers.FX_BEAM -> BEAMS.add(new Beam(id, new Vec3(bx, by, bz), clock));
            case Powers.FX_BIG_BEAM -> BIG.add(new BigBeam(new Vec3(ax, ay, az), new Vec3(bx, by, bz), clock));
            case Powers.FX_BEAM_CHARGE -> CHARGES.add(new Glow(id, clock, Powers.BEAM_CHARGE));
            case Powers.FX_WAVE_START -> WAVES.add(new Wave(id, new Vec3(ax, ay, az), clock, 0));
            case Powers.FX_WAVE_BURST -> WAVES.add(new Wave(id, new Vec3(ax, ay, az), clock, 1));
            case Powers.FX_PULSE -> WAVES.add(new Wave(id, new Vec3(ax, ay, az), clock, 2));
            case Powers.FX_BUBBLE_HIT -> {
                Bub b = BUBBLES.computeIfAbsent(id, k -> new Bub());
                b.hits.add(new Hit(new Vec3(ax, ay, az), clock));
                for (int i = 0; i < 6; i++) mc.level.addParticle(ParticleTypes.END_ROD, ax, ay, az, (Math.random() - .5) * 0.2, (Math.random() - .5) * 0.2, (Math.random() - .5) * 0.2);
            }
            case Powers.FX_BUBBLE_BREAK -> {
                for (float[] c : CELL) {
                    double x = ax + c[0] * Powers.BUBBLE_R, y = ay + c[1] * Powers.BUBBLE_R, z = az + c[2] * Powers.BUBBLE_R;
                    mc.level.addParticle(ParticleTypes.END_ROD, x, y, z, c[0] * 0.25, c[1] * 0.25 + 0.05, c[2] * 0.25);
                }
                WAVES.add(new Wave(id, new Vec3(ax, ay, az), clock, 2));
            }
            default -> { }
        }
    }

    public static void bubble(int id, boolean on, int res) {
        Bub b = BUBBLES.computeIfAbsent(id, k -> new Bub());
        if (b.on != on) b.since = clock;
        b.on = on;
        b.res = res;
    }

    public static boolean bubbleUp(int id) { Bub b = BUBBLES.get(id); return b != null && b.on; }

    /** Si el jugador esta usando un poder (para que el anillo brille a pleno). */
    public static boolean busy(int id) {
        for (Glow c : CHARGES) if (c.id == id) return true;
        for (Beam b : BEAMS) if (b.id == id) return true;
        for (Wave w : WAVES) if (w.id == id && w.type == 0) return true;
        return bubbleUp(id);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        clock++;
        BEAMS.removeIf(b -> clock - b.t0 > 10);
        BIG.removeIf(b -> clock - b.t0 > 14);
        CHARGES.removeIf(c -> clock - c.t0 > c.dur);
        WAVES.removeIf(w -> clock - w.t0 > (w.type == 0 ? Powers.WAVE_CHARGE : 16));
        Iterator<Map.Entry<Integer, Bub>> it = BUBBLES.entrySet().iterator();
        while (it.hasNext()) {
            Bub b = it.next().getValue();
            b.hits.removeIf(h -> clock - h.t0 > 12);
            if (!b.on && clock - b.since > 20 && b.hits.isEmpty()) it.remove();
        }
        // al apagarse, las celdas suben como chispas
        for (Map.Entry<Integer, Bub> en : BUBBLES.entrySet()) {
            Bub b = en.getValue();
            if (b.on || clock - b.since > 10) continue;
            Entity ent = mc.level.getEntity(en.getKey());
            if (ent == null) continue;
            for (int i = 0; i < 6; i++) {
                float[] c = CELL[mc.level.random.nextInt(CELLS)];
                mc.level.addParticle(ParticleTypes.END_ROD, ent.getX() + c[0] * Powers.BUBBLE_R, ent.getY() + c[1] * Powers.BUBBLE_R, ent.getZ() + c[2] * Powers.BUBBLE_R, 0, 0.06, 0);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        BEAMS.clear(); BIG.clear(); CHARGES.clear(); WAVES.clear(); BUBBLES.clear();
    }

    // ---------- dibujo ----------
    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (BEAMS.isEmpty() && BIG.isEmpty() && CHARGES.isEmpty() && WAVES.isEmpty() && BUBBLES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = e.getPartialTick();
        Camera cam = e.getCamera();
        Vec3 cp = cam.getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(GreenRender.GLOW);
        Matrix4f m = ps.last().pose();
        Vector3f right = new Vector3f(cam.getLeftVector()).negate(), up = new Vector3f(cam.getUpVector());
        float now = clock + pt;

        for (Glow c : CHARGES) {
            Entity en = mc.level.getEntity(c.id);
            if (!(en instanceof Player pl)) continue;
            Vec3 h = handPos(pl, pt).subtract(cp);
            float k = Mth.clamp((now - c.t0) / c.dur, 0, 1);
            glowDisc(vc, m, h, right, up, 0.15f + 0.35f * k, 0.1f, 1f, 0.35f, 0.5f * k);
            glowDisc(vc, m, h, right, up, 0.06f + 0.1f * k, 0.85f, 1f, 0.9f, 0.9f * k);
        }
        for (Beam b : BEAMS) {
            Entity en = mc.level.getEntity(b.id);
            Vec3 a = en instanceof Player pl ? handPos(pl, pt) : b.end;
            float f = 1f - Mth.clamp((now - b.t0) / 10f, 0, 1);
            float vib = 1f + 0.2f * Mth.sin(now * 3.1f);
            beam(vc, m, a.subtract(cp), b.end.subtract(cp), 0.24f * vib * f, 0.1f, 1f, 0.35f, 0.3f * f);
            beam(vc, m, a.subtract(cp), b.end.subtract(cp), 0.1f * vib, 0.2f, 1f, 0.45f, 0.7f * f);
            beam(vc, m, a.subtract(cp), b.end.subtract(cp), 0.035f, 0.85f, 1f, 0.9f, f);
            glowDisc(vc, m, a.subtract(cp), right, up, 0.35f * f, 0.2f, 1f, 0.45f, 0.8f * f);
            glowDisc(vc, m, b.end.subtract(cp), right, up, 0.6f * f, 0.2f, 1f, 0.45f, 0.7f * f);
        }
        for (BigBeam b : BIG) {
            float f = 1f - Mth.clamp((now - b.t0) / 14f, 0, 1);
            float vib = 1f + 0.25f * Mth.sin(now * 2.7f);
            Vec3 a = b.a.subtract(cp), z = b.b.subtract(cp);
            beam(vc, m, a, z, 1.2f * vib * f, 0.1f, 1f, 0.35f, 0.3f * f);
            beam(vc, m, a, z, 0.5f * vib, 0.2f, 1f, 0.45f, 0.7f * f);
            beam(vc, m, a, z, 0.16f, 0.85f, 1f, 0.9f, f);
            glowDisc(vc, m, a, right, up, 1.4f * f, 0.2f, 1f, 0.45f, 0.8f * f);
            glowDisc(vc, m, z, right, up, 2.5f * f, 0.3f, 1f, 0.5f, 0.8f * f);
        }
        for (Wave w : WAVES) {
            Entity en = mc.level.getEntity(w.id);
            Vec3 at = (en != null ? en.getPosition(pt) : w.at).subtract(cp);
            float t = now - w.t0;
            if (w.type == 0) {
                float c = Mth.clamp(t / 10f, 0, 1) * Mth.clamp((Powers.WAVE_CHARGE - t) / 4f, 0, 1);
                column(vc, m, at, 0.9f - 0.3f * t / Powers.WAVE_CHARGE, 7f, c);
                groundCircle(vc, m, at, 2.0f, 0.12f, t * 0.08f, 0.6f * c);
                groundCircle(vc, m, at, 1.4f, 0.06f, -t * 0.12f, 0.5f * c);
            } else {
                float k = Mth.clamp(t / 12f, 0, 1), rmax = w.type == 1 ? 8f : 6f;
                groundCircle(vc, m, at, 0.5f + rmax * (1 - (1 - k) * (1 - k)), 0.5f, 0, 0.8f * (1 - k));
                if (t < 4) glowDisc(vc, m, at.add(0, 1.2, 0), right, up, 3.5f * (1 - t / 4f), 0.3f, 1f, 0.5f, 0.6f * (1 - t / 4f));
            }
        }
        for (Map.Entry<Integer, Bub> en : BUBBLES.entrySet()) {
            Entity ent = mc.level.getEntity(en.getKey());
            if (ent == null) continue;
            dome(vc, m, ent.getPosition(pt).subtract(cp), en.getValue(), now, ent.getPosition(pt));
        }
        buf.endBatch(GreenRender.GLOW);
    }

    /** Mano derecha del jugador, interpolada. */
    static Vec3 handPos(Player pl, float pt) {
        Vec3 h = Powers.hand(pl);
        return h.subtract(pl.position()).add(pl.getPosition(pt));
    }

    private static void v(VertexConsumer vc, Matrix4f m, double x, double y, double z, float r, float g, float b, float a) {
        vc.vertex(m, (float) x, (float) y, (float) z).color(r, g, b, Mth.clamp(a, 0, 1)).endVertex();
    }

    /** Disco de luz mirando a la camara: centro con color y borde transparente. */
    public static void glowDisc(VertexConsumer vc, Matrix4f m, Vec3 c, Vector3f right, Vector3f up, float rad, float r, float g, float b, float a) {
        if (a <= 0.003f || rad <= 0) return;
        for (int i = 0; i < 24; i++) {
            double a0 = i * Math.PI / 12, a1 = (i + 1) * Math.PI / 12;
            double x0 = (Math.cos(a0) * right.x() + Math.sin(a0) * up.x()) * rad, y0 = (Math.cos(a0) * right.y() + Math.sin(a0) * up.y()) * rad, z0 = (Math.cos(a0) * right.z() + Math.sin(a0) * up.z()) * rad;
            double x1 = (Math.cos(a1) * right.x() + Math.sin(a1) * up.x()) * rad, y1 = (Math.cos(a1) * right.y() + Math.sin(a1) * up.y()) * rad, z1 = (Math.cos(a1) * right.z() + Math.sin(a1) * up.z()) * rad;
            v(vc, m, c.x, c.y, c.z, r, g, b, a);
            v(vc, m, c.x + x0, c.y + y0, c.z + z0, r, g, b, 0);
            v(vc, m, c.x + x1, c.y + y1, c.z + z1, r, g, b, 0);
            v(vc, m, c.x, c.y, c.z, r, g, b, a);
        }
    }

    /** Rayo: cinta que mira a la camara, con borde transparente. */
    private static void beam(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, float w, float r, float g, float bl, float al) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1e-6) return;
        Vec3 side = d.cross(a.add(b).scale(0.5)).normalize().scale(w);
        v(vc, m, a.x + side.x, a.y + side.y, a.z + side.z, r, g, bl, 0);
        v(vc, m, a.x, a.y, a.z, r, g, bl, al);
        v(vc, m, b.x, b.y, b.z, r, g, bl, al);
        v(vc, m, b.x + side.x, b.y + side.y, b.z + side.z, r, g, bl, 0);
        v(vc, m, a.x, a.y, a.z, r, g, bl, al);
        v(vc, m, a.x - side.x, a.y - side.y, a.z - side.z, r, g, bl, 0);
        v(vc, m, b.x - side.x, b.y - side.y, b.z - side.z, r, g, bl, 0);
        v(vc, m, b.x, b.y, b.z, r, g, bl, al);
    }

    /** Columna de luz de cargar el anillo. */
    private static void column(VertexConsumer vc, Matrix4f m, Vec3 c, float rad, float h, float a) {
        if (a <= 0) return;
        int n = 20;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            double x0 = Math.cos(a0) * rad, z0 = Math.sin(a0) * rad, x1 = Math.cos(a1) * rad, z1 = Math.sin(a1) * rad;
            v(vc, m, c.x + x0, c.y, c.z + z0, 0.3f, 1f, 0.5f, 0.35f * a);
            v(vc, m, c.x + x1, c.y, c.z + z1, 0.3f, 1f, 0.5f, 0.35f * a);
            v(vc, m, c.x + x1, c.y + h, c.z + z1, 0.3f, 1f, 0.5f, 0);
            v(vc, m, c.x + x0, c.y + h, c.z + z0, 0.3f, 1f, 0.5f, 0);
        }
    }

    /** Aro de luz en el piso. */
    private static void groundCircle(VertexConsumer vc, Matrix4f m, Vec3 c, float rad, float ancho, float giro, float a) {
        if (a <= 0) return;
        int n = 48;
        double y = c.y + 0.05;
        for (int i = 0; i < n; i++) {
            double a0 = giro + i * Math.PI * 2 / n, a1 = giro + (i + 1) * Math.PI * 2 / n;
            float al = a * (i % 6 == 0 ? 1f : 0.75f);
            v(vc, m, c.x + Math.cos(a0) * (rad - ancho), y, c.z + Math.sin(a0) * (rad - ancho), 0.3f, 1f, 0.5f, 0);
            v(vc, m, c.x + Math.cos(a0) * rad, y, c.z + Math.sin(a0) * rad, 0.3f, 1f, 0.5f, al);
            v(vc, m, c.x + Math.cos(a1) * rad, y, c.z + Math.sin(a1) * rad, 0.3f, 1f, 0.5f, al);
            v(vc, m, c.x + Math.cos(a1) * (rad - ancho), y, c.z + Math.sin(a1) * (rad - ancho), 0.3f, 1f, 0.5f, 0);
        }
    }

    /** Domo de celdas hexagonales: se arma celda por celda, se hunde con los golpes y cambia de color al debilitarse. */
    private static void dome(VertexConsumer vc, Matrix4f m, Vec3 c, Bub b, float now, Vec3 world) {
        float age = now - b.since;
        float r, g, bl;
        if (b.res > 60) { r = 0.3f; g = 1f; bl = 0.45f; }
        else if (b.res > 30) { r = 1f; g = 0.75f; bl = 0.25f; }
        else { r = 1f; g = 0.3f; bl = 0.25f; }
        float R = (float) Powers.BUBBLE_R, hex = 0.52f;
        for (float[] cell : CELL) {
            float vis = b.on ? Mth.clamp((age - cell[3] * 8f) / 3f, 0, 1) : 1f - Mth.clamp((age - cell[3] * 6f) / 3f, 0, 1);
            if (vis <= 0) continue;
            Vector3f n = new Vector3f(cell[0], cell[1], cell[2]);
            float flash = 0, dent = 0;
            for (Hit h : b.hits) {
                Vec3 rel = h.at.subtract(world);
                double dd = rel.normalize().distanceTo(new Vec3(n.x(), n.y(), n.z()));
                float k = (float) Math.exp(-dd * dd * 18) * (1 - (now - h.t0) / 12f);
                flash = Math.max(flash, k);
                dent = Math.max(dent, k * Mth.sin((now - h.t0) * 0.9f) * 0.25f);
            }
            float rr = R - dent + 0.02f * Mth.sin(now * 0.3f + cell[3] * 20);
            Vector3f t1 = Math.abs(n.y()) > 0.9f ? new Vector3f(1, 0, 0).cross(n).normalize() : new Vector3f(0, 1, 0).cross(n).normalize();
            Vector3f t2 = new Vector3f(n).cross(t1).normalize();
            double cx = c.x + n.x() * rr, cy = c.y + n.y() * rr, cz = c.z + n.z() * rr;
            double[][] out = new double[6][3], in = new double[6][3];
            for (int k = 0; k < 6; k++) {
                double a = k * Math.PI / 3;
                double ox = Math.cos(a) * t1.x() + Math.sin(a) * t2.x(), oy = Math.cos(a) * t1.y() + Math.sin(a) * t2.y(), oz = Math.cos(a) * t1.z() + Math.sin(a) * t2.z();
                float s = hex * (0.6f + 0.4f * vis);
                out[k] = new double[]{cx + ox * s, cy + oy * s, cz + oz * s};
                in[k] = new double[]{cx + ox * s * 0.78, cy + oy * s * 0.78, cz + oz * s * 0.78};
            }
            float fill = (0.12f + 0.35f * flash) * vis, edge = (0.5f + 0.5f * flash) * vis;
            float fr = r + (1 - r) * flash * 0.6f, fg = g, fb = bl + (1 - bl) * flash * 0.6f;
            // relleno (dos cuadrilateros forman el hexagono)
            v(vc, m, in[0][0], in[0][1], in[0][2], fr, fg, fb, fill); v(vc, m, in[1][0], in[1][1], in[1][2], fr, fg, fb, fill);
            v(vc, m, in[2][0], in[2][1], in[2][2], fr, fg, fb, fill); v(vc, m, in[3][0], in[3][1], in[3][2], fr, fg, fb, fill);
            v(vc, m, in[0][0], in[0][1], in[0][2], fr, fg, fb, fill); v(vc, m, in[3][0], in[3][1], in[3][2], fr, fg, fb, fill);
            v(vc, m, in[4][0], in[4][1], in[4][2], fr, fg, fb, fill); v(vc, m, in[5][0], in[5][1], in[5][2], fr, fg, fb, fill);
            // borde que brilla
            for (int k = 0; k < 6; k++) {
                int k2 = (k + 1) % 6;
                v(vc, m, in[k][0], in[k][1], in[k][2], fr, fg, fb, edge * 0.4f);
                v(vc, m, out[k][0], out[k][1], out[k][2], fr, fg, fb, edge);
                v(vc, m, out[k2][0], out[k2][1], out[k2][2], fr, fg, fb, edge);
                v(vc, m, in[k2][0], in[k2][1], in[k2][2], fr, fg, fb, edge * 0.4f);
            }
        }
        // anillo de luz donde el domo toca el piso
        if (b.on) groundCircle(vc, m, c, R + 0.1f, 0.35f, now * 0.02f, 0.7f * Mth.clamp(age / 6f, 0, 1));
    }
}
