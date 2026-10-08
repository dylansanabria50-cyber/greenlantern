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
        if (s == null || !s.on || s.shield <= 0) return;
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
        if (s == null || !s.on || s.shield <= 0) return;
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
    static void hud(ForgeGui gui, GuiGraphics g, float pt, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        PoliciaMod.Sync s = mine();
        if (mc.player == null || mc.options.hideGui || s == null || !s.on) return;
        // escudo visto desde atras, en primera persona
        if (s.shield > 0 && mc.options.getCameraType().isFirstPerson()) {
            int bw = 120, bh = 180;
            RenderSystem.enableBlend();
            g.blit(new net.minecraft.resources.ResourceLocation("policia", "textures/entity/escudo_hud.png"), 0, 0, sw, sh, 0f, 0f, 192, 108, 192, 108);
            RenderSystem.disableBlend();
        }
        int unlocked = Math.min(PoliciaMod.SKILLS.length, 1 + s.xp / PoliciaMod.XP_PER_NODE);
        int x = 8, y = 8;
        g.drawString(mc.font, "ARBOL DE HABILIDADES - XP " + s.xp, x, y, 0xFFE8C040, true);
        for (int i = 0; i < PoliciaMod.SKILLS.length; i++) {
            boolean open = i == 0 || ((s.un >> i) & 1) == 1;
            String line = (i == s.sel && open ? "> " : "  ") + (i + 1) + ". " + PoliciaMod.SKILLS[i];
            if (!open) line += "  [" + PoliciaMod.XP_PER_NODE + " XP]";
            g.drawString(mc.font, line, x, y + 12 + i * 10, open ? (i == s.sel ? 0xFFFFFFFF : 0xFFB0C4DE) : 0xFF707070, true);
        }
        String st;
        if (mc.player.getVehicle() instanceof PoliciaTank.TankEntity) st = "TANQUE: clic derecho para disparar";
        else if (s.sel == 1) st = s.tcd > 0 ? "Tanque en enfriamiento: " + (s.tcd + 19) / 20 + " s" : "Tanque listo (J)";
        else if (s.shield > 0) st = "Escudo activo: " + (s.shield + 19) / 20 + " s";
        else if (s.cooldown > 0) st = "Escudo en enfriamiento: " + (s.cooldown + 19) / 20 + " s";
        else st = "Escudo listo (J)";
        g.drawString(mc.font, st, x, y + 12 + PoliciaMod.SKILLS.length * 10 + 4, 0xFF80E0FF, true);
    }
}
