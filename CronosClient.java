package com.example.cronos;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

/** Teclas, barra de energia e indicadores del cliente. */
public class CronosClient {
    static final String CAT = "Cronosapiente";
    static final KeyMapping K_TRANS = new KeyMapping("Cronosapiente: Transformarse", GLFW.GLFW_KEY_V, CAT);
    static final KeyMapping K_FIRE = new KeyMapping("Cronosapiente: Disparar rayo (mantener)", GLFW.GLFW_KEY_Z, CAT);
    static final KeyMapping K_RAY = new KeyMapping("Cronosapiente: Cambiar rayo", GLFW.GLFW_KEY_X, CAT);
    static final KeyMapping K_WIND = new KeyMapping("Cronosapiente: Dar cuerda (mantener y soltar)", GLFW.GLFW_KEY_C, CAT);
    static final KeyMapping K_CUE = new KeyMapping("Cronosapiente: Cambiar poder de cuerda", GLFW.GLFW_KEY_G, CAT);
    static final KeyMapping K_10K = new KeyMapping("Cronosapiente: Forma 10K", GLFW.GLFW_KEY_H, CAT);
    static final KeyMapping K_CLEAN = new KeyMapping("Cronosapiente: Limpiar efectos negativos", GLFW.GLFW_KEY_B, CAT);
    static final KeyMapping K_UP = new KeyMapping("Cronosapiente: Forma mejorada", GLFW.GLFW_KEY_Y, CAT);
    static final KeyMapping K_SIZE = new KeyMapping("Cronosapiente: Cambiar tamano (10K)", GLFW.GLFW_KEY_J, CAT);

    static volatile boolean on, f10k, big, up, drill;
    static volatile int energy = 1000, ray, cue, slow, stop, acc, gray;
    static volatile int shakeT = 0;
    static boolean grayLoaded = false;
    static int shaderMode = 0;
    static CronosModel mdlD, mdlK, mdlU;
    static final ResourceLocation TEX_U = new ResourceLocation("cronos", "textures/entity/chronosapien_upgrade.png");
    static final ResourceLocation GLOW_U = new ResourceLocation("cronos", "textures/entity/glow_upgrade.png");
    static boolean mdlFail = false;
    static float keyAng = 0;
    static final ResourceLocation TEX_D = new ResourceLocation("cronos", "textures/entity/chronosapien_default.png");
    static final ResourceLocation TEX_K = new ResourceLocation("cronos", "textures/entity/chronosapien_10k.png");
    static final ResourceLocation[] GLOW_D = {new ResourceLocation("cronos", "textures/entity/glow_default_0.png"), new ResourceLocation("cronos", "textures/entity/glow_default_1.png")};
    static final ResourceLocation[] GLOW_K = {new ResourceLocation("cronos", "textures/entity/glow_10k_0.png"), new ResourceLocation("cronos", "textures/entity/glow_10k_1.png")};
    static boolean windDown = false;
    static long windStartMs = 0;

    public static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(CronosClient::keys);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(CronosClient::overlays);
        MinecraftForge.EVENT_BUS.addListener(CronosClient::tick);
        MinecraftForge.EVENT_BUS.addListener(CronosClient::render);
        MinecraftForge.EVENT_BUS.addListener(CronosClient::cam);
        MinecraftForge.EVENT_BUS.addListener(CronosClient::arm);
    }

    static void keys(RegisterKeyMappingsEvent e) {
        e.register(K_TRANS); e.register(K_FIRE); e.register(K_RAY); e.register(K_WIND);
        e.register(K_CUE); e.register(K_10K); e.register(K_SIZE); e.register(K_CLEAN); e.register(K_UP);
    }

    static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("cronos", CronosClient::hud);
    }

    static void send(int id) {
        CronosMod.NET.sendToServer(new CronosMod.Act(id));
    }

    static void onSync(CronosMod.Sync m) {
        boolean sizeChanged = big != m.big || on != m.on || f10k != m.f10k || up != m.up;
        on = m.on; f10k = m.f10k; big = m.big; up = m.up; drill = m.drill; energy = m.energy;
        ray = m.ray; cue = m.cue; slow = m.slow; stop = m.stop; acc = m.acc; gray = m.gray;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.getPersistentData().putBoolean("cr_big", big);
            mc.player.getPersistentData().putBoolean("cr_on", on);
            mc.player.getPersistentData().putBoolean("cr_10k", f10k);
            mc.player.getPersistentData().putBoolean("cr_up", up);
            if (sizeChanged) mc.player.refreshDimensions();
        }
    }

    static void onShake(int t) {
        shakeT = Math.max(shakeT, t);
    }

    static void cam(ViewportEvent.ComputeCameraAngles e) {
        int t = shakeT;
        if (t <= 0) return;
        float k = Math.min(t, 10) * 0.25f;
        e.setYaw(e.getYaw() + (float) (Math.random() - 0.5) * k);
        e.setPitch(e.getPitch() + (float) (Math.random() - 0.5) * k);
        e.setRoll(e.getRoll() + (float) (Math.random() - 0.5) * k);
    }

    static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (shakeT > 0) shakeT--;
        if (mc.player == null) {
            gray = 0; grayLoaded = false; shaderMode = 0; shakeT = 0;
            return;
        }
        int want = gray > 0 ? 1 : (slow > 0 ? 2 : 0);
        if (want != shaderMode) {
            if (shaderMode != 0) mc.gameRenderer.shutdownEffect();
            if (want == 1) mc.gameRenderer.loadEffect(new ResourceLocation("shaders/post/desaturate.json"));
            else if (want == 2) mc.gameRenderer.loadEffect(new ResourceLocation("cronos", "shaders/post/slowgreen.json"));
            shaderMode = want;
            grayLoaded = want != 0;
        }
        boolean free = mc.screen == null;
        if (on && stop > 0 && mc.player.isInWater() && !mc.options.keyShift.isDown()) {
            Vec3 dm = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(dm.x, Math.max(dm.y, 0.11), dm.z);
            mc.player.fallDistance = 0;
        }
        if (windDown || slow > 0 || acc > 0 || stop > 0) keyAng = (keyAng + 36f) % 360f;
        while (K_TRANS.consumeClick()) { if (free) send(0); }
        while (K_RAY.consumeClick()) { if (free) send(2); }
        while (K_CUE.consumeClick()) { if (free) send(5); }
        while (K_10K.consumeClick()) { if (free) send(6); }
        while (K_SIZE.consumeClick()) { if (free) send(7); }
        while (K_CLEAN.consumeClick()) { if (free) send(8); }
        while (K_UP.consumeClick()) { if (free) send(9); }
        if (free && on && K_FIRE.isDown()) send(1);
        boolean w = free && K_WIND.isDown();
        if (w && !windDown) { send(3); windStartMs = System.currentTimeMillis(); }
        if (!w && windDown) send(4);
        windDown = w;
    }

    static void arm(RenderArmEvent e) {
        if (on && !mdlFail && e.getPlayer() == Minecraft.getInstance().player) e.setCanceled(true);
    }

    static void render(RenderPlayerEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.getEntity() != mc.player || !on) return;
        if (!mdlFail) {
            PoseStack ps = e.getPoseStack();
            boolean pushed = false;
            try {
                if (mdlD == null) mdlD = CronosModel.load("geo/chronosapien_default.geo.json");
                if (f10k && mdlK == null) mdlK = CronosModel.load("geo/chronosapien_10k.geo.json");
                if (up && mdlU == null) mdlU = CronosModel.load("geo/chronosapien_upgrade.geo.json");
                CronosModel m = up ? mdlU : (f10k ? mdlK : mdlD);
                Player p = e.getEntity();
                float pt = e.getPartialTick();
                float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
                float headYaw = Mth.clamp(Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot) - bodyYaw, -75f, 75f);
                float pitch = Mth.clamp(Mth.lerp(pt, p.xRotO, p.getXRot()), -89f, 89f);
                double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
                float amp = (float) Math.min(1.0, Math.sqrt(dx * dx + dz * dz) * 4.0);
                float sw = (float) Math.cos(Mth.lerp(pt, p.walkDistO, p.walkDist) * 4.0f) * amp;
                float ka = keyAng;
                final float fsw = sw, fh = headYaw, fp = pitch;
                final float dr = (mc.level.getGameTime() + pt) % 15f;
                final boolean bombs = up && ray == 9, drl = up && drill;
                java.util.function.Function<String, float[]> anim = name -> switch (name) {
                    case "Head" -> new float[]{-fp, -fh, 0};
                    case "LeftArm" -> new float[]{fsw * 38f, 0, 0};
                    case "RightArm" -> new float[]{-fsw * 38f, 0, 0};
                    case "LeftLeg" -> new float[]{-fsw * 35f, 0, 0};
                    case "RightLeg" -> new float[]{fsw * 35f, 0, 0};
                    case "Key" -> new float[]{0, -ka, 0};
                    case "bone", "bone2" -> bombs ? new float[]{0, 0, 0, 0, 3, 0, 1, 1, 1} : null;
                    case "LayerRightarm" -> drl ? new float[]{0, -dr * 23.8f, 0, 0, 4, 0, 0.9f, 0.7f, 0.9f} : null;
                    case "bone4" -> drl ? new float[]{0, -dr * 24f, 0, 0, -1, 0, 1.4f, 1.6f, 1.4f} : null;
                    default -> null;
                };
                ps.pushPose();
                pushed = true;
                ps.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
                float sc = (1f / 16f) * (big ? 1.6f : 1f);
                ps.scale(sc, sc, sc);
                MultiBufferSource buf = e.getMultiBufferSource();
                VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(up ? TEX_U : (f10k ? TEX_K : TEX_D)));
                m.render(ps, vc, e.getPackedLight(), 1f, 1f, 1f, 1f, anim);
                int gi = (int) ((mc.level.getGameTime() / 10) % 2);
                VertexConsumer gv = buf.getBuffer(RenderType.eyes(up ? GLOW_U : (f10k ? GLOW_K : GLOW_D)[gi]));
                m.render(ps, gv, 0xF000F0, 1f, 1f, 1f, 1f, anim);
                ps.popPose();
                pushed = false;
                e.setCanceled(true);
                return;
            } catch (Throwable t) {
                mdlFail = true;
                if (pushed) ps.popPose();
            }
        }
        if (big) e.getPoseStack().scale(1.6f, 1.6f, 1.6f);
    }

    static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !on) return;
        int bw = 200, x = w / 2 - bw / 2, y = 6;
        g.fill(x - 2, y - 2, x + bw + 2, y + 10, 0xA0000000);
        g.fill(x, y, x + bw, y + 8, 0xFF1A2600);
        int fill = (int) (bw * (energy / 1000.0));
        g.fill(x, y, x + fill, y + 8, 0xFFC0FF00);
        g.drawCenteredString(mc.font, "Energia " + energy + " / 1000", w / 2, y + 12, 0xFFFFFFFF);
        String rayName = ray < CronosMod.RAYS.length ? CronosMod.RAYS[ray] : "?";
        g.drawCenteredString(mc.font, "Rayo: " + rayName + (f10k ? "  [10K]" : ""), w / 2, y + 23, 0xFFC0FF00);
        g.drawCenteredString(mc.font, "Cuerda: " + CronosMod.CUES[Math.min(cue, CronosMod.CUES.length - 1)], w / 2, y + 34, 0xFF66CCFF);
        int ly = y + 45;
        if (slow > 0) { g.drawCenteredString(mc.font, "Tiempo ralentizado " + (slow / 20) + "s", w / 2, ly, 0xFF99AAFF); ly += 11; }
        if (acc > 0) { g.drawCenteredString(mc.font, "Tiempo acelerado " + (acc / 20) + "s", w / 2, ly, 0xFFFFCC66); ly += 11; }
        if (stop > 0) { g.drawCenteredString(mc.font, "Tiempo detenido " + (stop / 20) + "s", w / 2, ly, 0xFFFF6666); ly += 11; }
        if (windDown) {
            int sec = (int) Math.min(20, (System.currentTimeMillis() - windStartMs) / 1000);
            long ms = Math.min(20000, System.currentTimeMillis() - windStartMs);
            int ww = (int) (bw * (ms / 20000.0));
            g.fill(x, ly + 2, x + bw, ly + 6, 0x80000000);
            g.fill(x, ly + 2, x + ww, ly + 6, 0xFFFFFFFF);
            g.drawCenteredString(mc.font, "Dando cuerda... " + sec + "s", w / 2, ly + 9, 0xFFFFFFFF);
        }
    }
}
