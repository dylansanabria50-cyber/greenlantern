package com.example.policia;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Teclas, skin de policia, escudo visible y HUD del arbol de habilidades. */
public class PoliciaClient {
    static final String CAT = "Policia";
    static final KeyMapping K_FORM = new KeyMapping("Policia: Modo policia (skin)", GLFW.GLFW_KEY_H, CAT);
    static final KeyMapping K_USE = new KeyMapping("Policia: Usar habilidad", GLFW.GLFW_KEY_J, CAT);
    static final KeyMapping K_NEXT = new KeyMapping("Policia: Cambiar habilidad", GLFW.GLFW_KEY_K, CAT);

    static final ResourceLocation TEX_SKIN = new ResourceLocation("policia", "textures/entity/policia.png");
    static final ResourceLocation TEX_FRONT = new ResourceLocation("policia", "textures/entity/escudo_frente.png");
    static final ResourceLocation TEX_BACK = new ResourceLocation("policia", "textures/entity/escudo_atras.png");

    /** Estado de cada jugador visto por este cliente. */
    static final Map<UUID, PoliciaMod.Sync> STATE = new HashMap<>();
    /** Skin original de cada jugador, para devolverla al desactivar el modo. */
    static final Map<UUID, ResourceLocation> ORIG_SKIN = new HashMap<>();
    static final Map<UUID, String> ORIG_MODEL = new HashMap<>();
    static Field F_TEX, F_MODEL;
    static boolean reflectTried = false, reflectOk = false;

    public static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaClient::keys);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaClient::overlays);
        MinecraftForge.EVENT_BUS.addListener(PoliciaClient::tick);
        MinecraftForge.EVENT_BUS.addListener(PoliciaClient::camera);
        MinecraftForge.EVENT_BUS.addListener(PoliciaClient::hideRider);
        MinecraftForge.EVENT_BUS.addListener(PoliciaClient::hideHand);
        MinecraftForge.EVENT_BUS.addListener(PoliciaClient::render);
        MinecraftForge.EVENT_BUS.addListener(PoliciaClient::screenInit);
        MinecraftForge.EVENT_BUS.addListener(PoliciaClient::livingPre);
        MinecraftForge.EVENT_BUS.addListener(PoliciaClient::noClicks);
        MinecraftForge.EVENT_BUS.addListener(PoliciaClient::screenPre);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaClient::setup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaClient::renderers);
    }

    static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e) {
        e.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(PoliciaShield.SHIELD.get(),
                new net.minecraft.resources.ResourceLocation("blocking"),
                (st, lv, en, sd) -> en != null && en.isUsingItem() && en.getUseItem() == st ? 1.0F : 0.0F));
    }

    static void renderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(PoliciaTank.TANK.get(), PoliciaTankRender::new);
        e.registerEntityRenderer(PoliciaRefuerzo.AGENT.get(), PoliciaRefuerzoRender::new);
        PoliciaExtraRender.register(e);
    }

    static java.lang.reflect.Method CAM_MOVE;
    static boolean camTried = false;

    /** En tercera persona sobre el tanque, la camara se aleja para verlo completo. */
    static void camera(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.getCameraType().isFirstPerson()) return;
        if (!(mc.player.getVehicle() instanceof PoliciaTank.TankEntity)) return;
        if (!camTried) {
            camTried = true;
            try {
                CAM_MOVE = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(
                        net.minecraft.client.Camera.class, "m_90568_", double.class, double.class, double.class);
            } catch (Throwable t) {
                try {
                    CAM_MOVE = net.minecraft.client.Camera.class.getDeclaredMethod("move", double.class, double.class, double.class);
                    CAM_MOVE.setAccessible(true);
                } catch (Throwable t2) {
                    CAM_MOVE = null;
                }
            }
        }
        if (CAM_MOVE == null) return;
        try {
            net.minecraft.client.Camera cam = e.getCamera();
            net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition((float) e.getPartialTick());
            org.joml.Vector3f lk = cam.getLookVector(), up = cam.getUpVector();
            net.minecraft.world.phys.Vec3 tgt = eye.add(lk.x() * -18.0 + up.x() * 3.0, lk.y() * -18.0 + up.y() * 3.0, lk.z() * -18.0 + up.z() * 3.0);
            double total = eye.distanceTo(tgt);
            double f = 1.0;
            for (int i = 0; i < 8; i++) {
                net.minecraft.world.phys.Vec3 o = new net.minecraft.world.phys.Vec3((i & 1) * 0.2 - 0.1, (i >> 1 & 1) * 0.2 - 0.1, (i >> 2 & 1) * 0.2 - 0.1);
                net.minecraft.world.phys.HitResult h = mc.level.clip(new net.minecraft.world.level.ClipContext(eye.add(o), tgt.add(o),
                        net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
                if (h.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                    f = Math.min(f, Math.max(0.0, (eye.add(o).distanceTo(h.getLocation()) - 0.1) / total));
                }
            }
            if (!camSetTried) {
                camSetTried = true;
                try {
                    CAM_SET = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(
                            net.minecraft.client.Camera.class, "m_90584_", double.class, double.class, double.class);
                } catch (Throwable t) {
                    try {
                        CAM_SET = net.minecraft.client.Camera.class.getDeclaredMethod("setPosition", double.class, double.class, double.class);
                        CAM_SET.setAccessible(true);
                    } catch (Throwable t2) { CAM_SET = null; }
                }
            }
            if (CAM_SET != null) CAM_SET.invoke(cam, eye.x, eye.y, eye.z);
            CAM_MOVE.invoke(cam, -18.0 * f, 3.0 * f, 0.0);
        } catch (Throwable t) { CAM_MOVE = null; }
    }

    static java.lang.reflect.Method CAM_SET;
    static boolean camSetTried = false;

    /** El jugador que va dentro del tanque no se dibuja: solo se ve el tanque. */
    static void hideRider(net.minecraftforge.client.event.RenderPlayerEvent.Pre e) {
        if (e.getEntity().getVehicle() instanceof PoliciaTank.TankEntity) e.setCanceled(true);
    }

    /** Tampoco se ve la mano en primera persona. */
    static void hideHand(net.minecraftforge.client.event.RenderHandEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() instanceof PoliciaTank.TankEntity) e.setCanceled(true);
    }

    static void keys(RegisterKeyMappingsEvent e) {
        e.register(K_FORM); e.register(K_USE); e.register(K_NEXT);
    }

    static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("policia", PoliciaClient::hud);
    }

    /** Boton de la rama de habilidades, junto al libro de recetas. */
    static class TreeButton extends net.minecraft.client.gui.components.Button {
        TreeButton() {
            super(0, 0, 20, 18, net.minecraft.network.chat.Component.literal(""),
                    b -> Minecraft.getInstance().setScreen(new PoliciaTreeScreen()), DEFAULT_NARRATION);
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    net.minecraft.network.chat.Component.literal("Rama de habilidades: Policia")));
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            super.renderWidget(g, mx, my, pt);
            g.renderItem(new net.minecraft.world.item.ItemStack(PoliciaShield.SHIELD.get()), getX() + 2, getY() + 1);
        }
    }

    static void screenInit(net.minecraftforge.client.event.ScreenEvent.Init.Post e) {
        if (e.getScreen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
            e.addListener(new TreeButton());
        }
    }

    static void screenPre(net.minecraftforge.client.event.ScreenEvent.Render.Pre e) {
        if (!(e.getScreen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen inv)) return;
        TreeButton tb = null;
        for (Object o : inv.children()) {
            if (o instanceof TreeButton t) tb = t;
        }
        if (tb == null) return;
        int left = inv.getGuiLeft(), top = inv.getGuiTop();
        int maxRight = left + 124;
        for (Object o : inv.children()) {
            if (o instanceof net.minecraft.client.gui.components.AbstractWidget w && w != tb
                    && w.getY() >= top + 55 && w.getY() <= top + 70
                    && w.getX() >= left + 100 && w.getX() + w.getWidth() > maxRight) {
                maxRight = w.getX() + w.getWidth();
            }
        }
        PoliciaMod.Sync st = mine();
        tb.visible = st != null && st.on;
        tb.setX(maxRight + 2);
        tb.setY(top + 61);
    }

    /** Con el escudo activo: ambos brazos sujetan el escudo al frente (pose de bloqueo). */
    static void livingPre(net.minecraftforge.client.event.RenderLivingEvent.Pre<?, ?> e) {
        if (!(e.getEntity() instanceof Player p)) return;
        PoliciaMod.Sync s = STATE.get(p.getUUID());
        if (true) return;
        if (e.getRenderer().getModel() instanceof net.minecraft.client.model.PlayerModel<?> pm) {
            pm.rightArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.BLOCK;
            pm.leftArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.BLOCK;
        }
    }

    /** Con el escudo activo no se puede golpear ni interactuar. */
    static void noClicks(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc0 = Minecraft.getInstance();
        if (mc0.player != null && mc0.player.getVehicle() instanceof PoliciaTank.TankEntity) {
            if (e.isUseItem()) send(3);
            e.setCanceled(true);
            e.setSwingHand(false);
            return;
        }
        PoliciaMod.Sync s = mine();
        if (s != null && s.on && s.shield > 0) {
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }

    static void send(int id) {
        PoliciaMod.NET.sendToServer(new PoliciaMod.Act(id));
    }

    static void onSync(PoliciaMod.Sync m) {
        STATE.put(m.id, m);
    }

    static PoliciaMod.Sync mine() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : STATE.get(mc.player.getUUID());
    }

    // ---------- skin ----------
    static void findFields() {
        if (reflectTried) return;
        reflectTried = true;
        try {
            for (Field f : PlayerInfo.class.getDeclaredFields()) {
                if (F_TEX == null && Map.class.isAssignableFrom(f.getType())) { f.setAccessible(true); F_TEX = f; }
                else if (F_MODEL == null && f.getType() == String.class) { f.setAccessible(true); F_MODEL = f; }
            }
            reflectOk = F_TEX != null;
        } catch (Throwable t) {
            reflectOk = false;
        }
    }

    // ---------- transformacion con nanotecnologia (la skin se forma / deshace pixel a pixel) ----------
    static final int MORPH_TICKS = 40;
    static final Map<UUID, Boolean> LAST_ON = new HashMap<>();
    static final Map<UUID, Morph> MORPH = new HashMap<>();

    static class Morph {
        long start;
        boolean forming;
        net.minecraft.client.renderer.texture.DynamicTexture dyn;
        ResourceLocation rl;
        com.mojang.blaze3d.platform.NativeImage orig, pol;
    }

    static com.mojang.blaze3d.platform.NativeImage readTexture(Minecraft mc, ResourceLocation rl) {
        com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(64, 64, true);
        RenderSystem.bindTexture(mc.getTextureManager().getTexture(rl).getId());
        img.downloadTexture(0, false);
        return img;
    }

    static Morph startMorph(Minecraft mc, UUID id, boolean forming, ResourceLocation origRl) throws Exception {
        Morph m = new Morph();
        m.forming = forming;
        m.start = mc.level.getGameTime();
        m.orig = readTexture(mc, origRl);
        try (java.io.InputStream in = mc.getResourceManager().getResourceOrThrow(TEX_SKIN).open()) {
            m.pol = com.mojang.blaze3d.platform.NativeImage.read(in);
        }
        m.dyn = new net.minecraft.client.renderer.texture.DynamicTexture(new com.mojang.blaze3d.platform.NativeImage(64, 64, true));
        m.rl = new ResourceLocation("policia", "morph/" + id.toString().toLowerCase());
        mc.getTextureManager().register(m.rl, m.dyn);
        MORPH.put(id, m);
        return m;
    }

    static float noise(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 1023) / 1023.0f;
    }

    static void stepMorph(Morph m, long now) {
        float p = Math.min(1.0f, (now - m.start) / (float) MORPH_TICKS);
        com.mojang.blaze3d.platform.NativeImage out = m.dyn.getPixels();
        if (out == null) return;
        com.mojang.blaze3d.platform.NativeImage from = m.forming ? m.orig : m.pol;
        com.mojang.blaze3d.platform.NativeImage to = m.forming ? m.pol : m.orig;
        float t = p * 1.25f - 0.1f;
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                float k = 0.55f * noise(x, y) + 0.45f * (1.0f - y / 64.0f);
                int a = from.getPixelRGBA(x, y);
                int b = to.getPixelRGBA(x, y);
                int px;
                if (k < t - 0.12f) {
                    px = b;
                } else if (k < t) {
                    int al = Math.max(a >>> 24, b >>> 24);
                    px = al == 0 ? 0 : ((al << 24) | (255 << 16) | (230 << 8) | 80);
                } else {
                    px = a;
                }
                out.setPixelRGBA(x, y, px);
            }
        }
        m.dyn.upload();
    }

    @SuppressWarnings("unchecked")
    static void applySkin(Minecraft mc) {
        findFields();
        if (!reflectOk || mc.getConnection() == null || mc.level == null) return;
        for (Map.Entry<UUID, PoliciaMod.Sync> en : STATE.entrySet()) {
            UUID id = en.getKey();
            PlayerInfo info = mc.getConnection().getPlayerInfo(id);
            if (info == null) continue;
            try {
                Map<MinecraftProfileTexture.Type, ResourceLocation> tex = (Map<MinecraftProfileTexture.Type, ResourceLocation>) F_TEX.get(info);
                ResourceLocation cur = tex.get(MinecraftProfileTexture.Type.SKIN);
                boolean want = en.getValue().on;
                Boolean last = LAST_ON.put(id, want);
                Morph m = MORPH.get(id);
                if (m == null && last != null && last != want && cur != null) {
                    ResourceLocation orig = cur;
                    if (want) {
                        if (!TEX_SKIN.equals(cur)) {
                            ORIG_SKIN.put(id, cur);
                            if (F_MODEL != null) ORIG_MODEL.put(id, (String) F_MODEL.get(info));
                        }
                    } else {
                        orig = ORIG_SKIN.get(id);
                    }
                    if (orig != null) {
                        try { m = startMorph(mc, id, want, orig); } catch (Throwable t) { m = null; }
                    }
                }
                if (m != null) {
                    long el = mc.level.getGameTime() - m.start;
                    if (el < MORPH_TICKS) {
                        stepMorph(m, mc.level.getGameTime());
                        tex.put(MinecraftProfileTexture.Type.SKIN, m.rl);
                        boolean secondHalf = el * 2 >= MORPH_TICKS;
                        String origModel = ORIG_MODEL.get(id) == null ? "default" : ORIG_MODEL.get(id);
                        if (F_MODEL != null) F_MODEL.set(info, secondHalf == m.forming ? "slim" : origModel);
                        continue;
                    }
                    MORPH.remove(id);
                    mc.getTextureManager().release(m.rl);
                    m.orig.close();
                    m.pol.close();
                    if (m.forming) {
                        tex.put(MinecraftProfileTexture.Type.SKIN, TEX_SKIN);
                        if (F_MODEL != null) F_MODEL.set(info, "slim");
                    } else {
                        ResourceLocation o = ORIG_SKIN.remove(id);
                        if (o != null) tex.put(MinecraftProfileTexture.Type.SKIN, o);
                        if (F_MODEL != null) F_MODEL.set(info, ORIG_MODEL.remove(id));
                    }
                    continue;
                }
                if (want) {
                    if (cur == null) continue; // la skin real todavia no cargo
                    if (!TEX_SKIN.equals(cur)) {
                        ORIG_SKIN.put(id, cur);
                        if (F_MODEL != null) ORIG_MODEL.put(id, (String) F_MODEL.get(info));
                    }
                    tex.put(MinecraftProfileTexture.Type.SKIN, TEX_SKIN);
                    if (F_MODEL != null) F_MODEL.set(info, "slim");
                } else if (TEX_SKIN.equals(cur)) {
                    ResourceLocation o = ORIG_SKIN.remove(id);
                    if (o != null) tex.put(MinecraftProfileTexture.Type.SKIN, o);
                    if (F_MODEL != null) F_MODEL.set(info, ORIG_MODEL.remove(id));
                }
            } catch (Throwable t) {
                reflectOk = false;
            }
        }
    }

    static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { STATE.clear(); ORIG_SKIN.clear(); ORIG_MODEL.clear(); LAST_ON.clear(); MORPH.clear(); return; }
        applySkin(mc);
        boolean free = mc.screen == null;
        while (K_FORM.consumeClick()) { if (free) send(0); }
        while (K_USE.consumeClick()) { if (free) send(1); }
        while (K_NEXT.consumeClick()) { if (free) send(2); }
    }

    // ---------- escudo en el cuerpo ----------
    static void render(RenderPlayerEvent.Post e) {
        Player p = e.getEntity();
        PoliciaMod.Sync s = STATE.get(p.getUUID());
        if (true) return;
        PoseStack ps = e.getPoseStack();
        MultiBufferSource buf = e.getMultiBufferSource();
        int light = e.getPackedLight();
        float yaw = p.yBodyRot;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.translate(0.0, p.isCrouching() ? 0.85 : 0.95, 0.30);
        ps.scale(1.25f, -1.25f, -1.25f);
        PoliciaShield.Render.drawWorld(ps, buf, light);
        ps.popPose();
    }

    static void drawFace(PoseStack ps, VertexConsumer vc, float w, float h, float z, int light, boolean flip) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float nz = flip ? -1f : 1f;
        float u0 = flip ? 0f : 1f, u1 = flip ? 1f : 0f;
        v(vc, m, n, -w, -h, z, u0, 1f, nz, light);
        v(vc, m, n, w, -h, z, u1, 1f, nz, light);
        v(vc, m, n, w, h, z, u1, 0f, nz, light);
        v(vc, m, n, -w, h, z, u0, 0f, nz, light);
    }

    static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float vv, float nz, int light) {
        vc.vertex(m, x, y, z).color(255, 255, 255, 255).uv(u, vv)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0f, 0f, nz).endVertex();
    }

    // ---------- HUD ----------
    /** Mira verde del tanque en primera persona: cruz, marco, escala de distancia y datos. */
    static void scope(GuiGraphics g, Minecraft mc, PoliciaTank.TankEntity t, int sw, int sh) {
        final int G = 0xFF3CE06E, GD = 0x883CE06E, RED = 0xFFFF3030;
        int cx = sw / 2, cy = sh / 2;
        float pt = mc.getFrameTime();
        double dist = mc.player.pick(96.0, pt, false).getLocation().distanceTo(mc.player.getEyePosition(pt));
        // cruz con hueco central y marcas
        g.fill(cx - 52, cy, cx - 5, cy + 1, G);
        g.fill(cx + 5, cy, cx + 52, cy + 1, G);
        g.fill(cx, cy - 44, cx + 1, cy - 5, G);
        g.fill(cx, cy + 5, cx + 1, cy + 44, G);
        for (int k = 1; k <= 4; k++) {
            g.fill(cx + k * 12, cy - 3, cx + k * 12 + 1, cy + 4, G);
            g.fill(cx - k * 12, cy - 3, cx - k * 12 + 1, cy + 4, G);
            g.fill(cx - 3, cy + k * 10, cx + 4, cy + k * 10 + 1, G);
            g.fill(cx - 3, cy - k * 10, cx + 4, cy - k * 10 + 1, G);
        }
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, RED);
        // marco
        g.fill(cx - 84, cy - 50, cx + 84, cy - 49, GD);
        g.fill(cx - 84, cy + 49, cx + 84, cy + 50, GD);
        g.fill(cx - 84, cy - 50, cx - 83, cy + 50, GD);
        g.fill(cx + 83, cy - 50, cx + 84, cy + 50, GD);
        // escala de distancia a la derecha (arco)
        double r0 = (sh * 0.38) / Math.sin(Math.toRadians(14.0));
        int xm = sw - 70;
        for (int d = -140; d <= 140; d++) {
            double rad = Math.toRadians(d / 10.0);
            int x = xm - (int) (r0 * (1.0 - Math.cos(rad)));
            int y = cy + (int) (r0 * Math.sin(rad));
            g.fill(x, y, x + 2, y + 1, G);
        }
        for (int i = 0; i < 4; i++) {
            double rad = Math.toRadians(-14.0 + 28.0 * i / 3.0);
            int x = xm - (int) (r0 * (1.0 - Math.cos(rad)));
            int y = cy + (int) (r0 * Math.sin(rad));
            g.fill(x, y, x + 9, y + 1, G);
            g.drawString(mc.font, String.valueOf(200 + 200 * i), x + 13, y - 4, G, true);
        }
        double fr = Math.max(0.0, Math.min(1.0, (dist - 16.0) / 80.0));
        double rm = Math.toRadians(-14.0 + 28.0 * fr);
        int mx = xm - (int) (r0 * (1.0 - Math.cos(rm)));
        int my = cy + (int) (r0 * Math.sin(rm));
        g.fill(mx - 12, my - 2, mx - 3, my + 3, RED);
        // datos
        g.drawString(mc.font, "DIST " + (int) dist + " m", cx - 84, cy - 62, G, true);
        String est = t.fireState() >= 0 ? "CARGANDO" : "LISTO";
        String obus = t.isMobile() ? "OBUS " + t.shotsLeft() + "/" + PoliciaTank.SHOTS : "OBUS 1/1";
        g.drawString(mc.font, obus + "  " + est, cx - 84, cy + 56, G, true);
    }

    static void hud(ForgeGui gui, GuiGraphics g, float pt, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        PoliciaMod.Sync s = mine();
        if (mc.player == null || mc.options.hideGui || s == null || !s.on) return;
        // escudo visto desde atras, en primera persona
        if (false && s.shield > 0 && mc.options.getCameraType().isFirstPerson()) {
            int bw = 120, bh = 180;
            RenderSystem.enableBlend();
            g.blit(new net.minecraft.resources.ResourceLocation("policia", "textures/entity/escudo_hud.png"), 0, 0, sw, sh, 0f, 0f, 192, 108, 192, 108);
            RenderSystem.disableBlend();
        }
        if (mc.player.getVehicle() instanceof PoliciaTank.TankEntity tk && mc.options.getCameraType().isFirstPerson()) scope(g, mc, tk, sw, sh);
        // lista de habilidades: arriba a la derecha, mas pequena
        boolean up = ((s.un >> 4) & 1) == 1;
        java.util.List<String> ls = new java.util.ArrayList<>();
        java.util.List<Integer> cs = new java.util.ArrayList<>();
        ls.add("ARBOL DE HABILIDADES - XP " + s.xp); cs.add(0xFFE8C040);
        int num = 0;
        for (int i = 0; i < PoliciaMod.SKILLS.length; i++) {
            if (i == 4) continue;
            num++;
            boolean open = i == 0 || ((s.un >> i) & 1) == 1;
            String nm = (i == 2 && up) ? PoliciaMod.SKILLS[4] : PoliciaMod.SKILLS[i];
            String line = (i == s.sel && open ? "> " : "  ") + num + ". " + nm;
            if (!open) line += "  [" + (PoliciaMod.LEVEL_COST[i] > 0 ? PoliciaMod.LEVEL_COST[i] + " niveles" : PoliciaMod.XP_PER_NODE + " XP") + "]";
            ls.add(line);
            cs.add(open ? (i == s.sel ? 0xFFFFFFFF : 0xFFB0C4DE) : 0xFF707070);
        }
        String st;
        if (mc.player.getVehicle() instanceof PoliciaTank.TankEntity tk0) {
            st = tk0.isMobile() ? "TANQUE: WASD mover, clic derecho disparar (" + tk0.shotsLeft() + "), Shift bajar"
                    : "TANQUE: clic derecho para disparar";
        }
        else if (s.sel == 2) st = s.tcd > 0 ? "Tanque en enfriamiento: " + (s.tcd + 19) / 20 + " s" : (up ? "Tanque movil listo (J)" : "Tanque listo (J)");
        else if (s.sel == 1) st = s.rcd > 0 ? "Refuerzo en enfriamiento: " + (s.rcd + 19) / 20 + " s" : "Refuerzo nv " + Math.max(1, s.rl) + " listo (J)";
        else if (s.sel >= 5) {
            int cc = s.sel == 5 ? s.c5 : (s.sel == 6 ? s.c6 : (s.sel == 7 ? s.c7 : s.c8));
            st = PoliciaMod.SKILLS[s.sel] + (cc > 0 ? " en enfriamiento: " + (cc + 19) / 20 + " s" : " listo (J)");
        }
        else if (s.sel == 3) st = "Helicoptero: proximamente";
        else if (s.shield > 0) st = "Escudo activo: " + (s.shield + 19) / 20 + " s";
        else if (s.cooldown > 0) st = "Escudo en enfriamiento: " + (s.cooldown + 19) / 20 + " s";
        else st = "Escudo listo (J)";
        ls.add(st); cs.add(0xFF80E0FF);
        float sc = 0.75f;
        int wmax = 0;
        for (String l : ls) wmax = Math.max(wmax, mc.font.width(l));
        g.pose().pushPose();
        g.pose().translate(sw - 6 - wmax * sc, 6.0f, 0.0f);
        g.pose().scale(sc, sc, 1.0f);
        for (int i = 0; i < ls.size(); i++) {
            int yy = i == 0 ? 0 : (i == ls.size() - 1 ? i * 10 + 6 : i * 10 + 2);
            g.drawString(mc.font, ls.get(i), 0, yy, cs.get(i), true);
        }
        g.pose().popPose();
    }
}
