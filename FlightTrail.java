package com.example.greenlantern.client;

import com.example.greenlantern.GreenLanternMod;
import com.example.greenlantern.JetEntity;
import com.example.greenlantern.RingPowers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Estela verde brillante detras de quien vuela con el anillo y detras del caza. */
@Mod.EventBusSubscriber(modid = GreenLanternMod.MODID, value = Dist.CLIENT)
public class FlightTrail {
    private static final int LIFE = 26;
    private static final Map<Integer, ArrayList<double[]>> TRAILS = new HashMap<>();
    private static int clock = 0;

    private static void add(int id, double x, double y, double z) {
        ArrayList<double[]> list = TRAILS.computeIfAbsent(id, k -> new ArrayList<>());
        if (!list.isEmpty()) {
            double[] last = list.get(list.size() - 1);
            double dx = x - last[0], dy = y - last[1], dz = z - last[2];
            if (dx * dx + dy * dy + dz * dz < 0.02) return;
        }
        list.add(new double[]{x, y, z, clock});
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        clock++;
        for (Player p : mc.level.players()) {
            double sp = Math.hypot(p.getX() - p.xo, p.getZ() - p.zo);
            boolean fly = RingPowers.isWorn(p) && !p.onGround() && !p.isPassenger()
                    && (p.getAbilities().flying || sp > 0.5);
            if (fly) add(p.getId(), p.getX(), p.getY() + 0.9, p.getZ());
        }
        for (Entity en : mc.level.entitiesForRendering()) {
            if (en instanceof JetEntity j && !j.isRemoved()) {
                Vec3 look = j.getViewVector(1.0f);
                add(j.getId(), j.getX() - look.x * 1.5, j.getY() + 0.1 - look.y * 1.5, j.getZ() - look.z * 1.5);
            }
        }
        Iterator<Map.Entry<Integer, ArrayList<double[]>>> it = TRAILS.entrySet().iterator();
        while (it.hasNext()) {
            ArrayList<double[]> list = it.next().getValue();
            list.removeIf(pt -> clock - pt[3] > LIFE);
            if (list.isEmpty()) it.remove();
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (TRAILS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(GreenRender.TRAIL);
        Matrix4f m = ps.last().pose();
        for (ArrayList<double[]> pts : TRAILS.values()) {
            for (int i = 0; i + 1 < pts.size(); i++) {
                segment(vc, m, pts.get(i), pts.get(i + 1), cam);
            }
        }
        buf.endBatch(GreenRender.TRAIL);
    }

    private static float fade(double[] p) {
        float f = 1.0f - (clock - (float) p[3]) / LIFE;
        return Math.max(0.0f, Math.min(1.0f, f));
    }

    private static void segment(VertexConsumer vc, Matrix4f m, double[] a, double[] b, Vec3 cam) {
        float fa = fade(a), fb = fade(b);
        float ax = (float) (a[0] - cam.x), ay = (float) (a[1] - cam.y), az = (float) (a[2] - cam.z);
        float bx = (float) (b[0] - cam.x), by = (float) (b[1] - cam.y), bz = (float) (b[2] - cam.z);
        float dx = bx - ax, dz = bz - az;
        float sx = -dz, sz = dx;
        float len = (float) Math.sqrt(sx * sx + sz * sz);
        if (len < 1.0E-4f) {
            sx = 1.0f;
            sz = 0.0f;
        } else {
            sx /= len;
            sz /= len;
        }
        float wa = 0.3f * fa, wb = 0.3f * fb;
        // cinta horizontal
        v(vc, m, ax - sx * wa, ay, az - sz * wa, fa);
        v(vc, m, ax + sx * wa, ay, az + sz * wa, fa);
        v(vc, m, bx + sx * wb, by, bz + sz * wb, fb);
        v(vc, m, bx - sx * wb, by, bz - sz * wb, fb);
        // cinta vertical
        v(vc, m, ax, ay - wa, az, fa);
        v(vc, m, ax, ay + wa, az, fa);
        v(vc, m, bx, by + wb, bz, fb);
        v(vc, m, bx, by - wb, bz, fb);
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, float f) {
        vc.vertex(m, x, y, z).color(0.25f, 1.0f, 0.4f, 0.7f * f).endVertex();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        TRAILS.clear();
    }
}
