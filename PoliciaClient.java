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
        MinecraftForge.EVENT_BUS.addListener(PoliciaClient::screenPre);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PoliciaClient::setup);
    }

    static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e) {
        e.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(PoliciaShield.SHIELD.get(),
                new net.minecraft.resources.ResourceLocation("blocking"),
                (st, lv, en, sd) -> en != null && en.isUsingItem() && en.getUseItem() == st ? 1.0F : 0.0F));
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
        tb.setX(maxRight + 2);
        tb.setY(top + 61);
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

    @SuppressWarnings("unchecked")
    static void applySkin(Minecraft mc) {
        findFields();
        if (!reflectOk || mc.getConnection() == null) return;
        for (Map.Entry<UUID, PoliciaMod.Sync> en : STATE.entrySet()) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(en.getKey());
            if (info == null) continue;
            try {
                Map<MinecraftProfileTexture.Type, ResourceLocation> tex = (Map<MinecraftProfileTexture.Type, ResourceLocation>) F_TEX.get(info);
                ResourceLocation cur = tex.get(MinecraftProfileTexture.Type.SKIN);
                boolean want = en.getValue().on;
                if (want) {
                    if (cur == null) continue; // la skin real todavia no cargo
                    if (!TEX_SKIN.equals(cur)) {
                        ORIG_SKIN.put(en.getKey(), cur);
                        if (F_MODEL != null) ORIG_MODEL.put(en.getKey(), (String) F_MODEL.get(info));
                    }
                    tex.put(MinecraftProfileTexture.Type.SKIN, TEX_SKIN);
                    if (F_MODEL != null) F_MODEL.set(info, "slim");
                } else if (TEX_SKIN.equals(cur)) {
                    ResourceLocation o = ORIG_SKIN.remove(en.getKey());
                    if (o != null) tex.put(MinecraftProfileTexture.Type.SKIN, o);
                    if (F_MODEL != null) F_MODEL.set(info, ORIG_MODEL.remove(en.getKey()));
                }
            } catch (Throwable t) {
                reflectOk = false;
            }
        }
    }

    static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { STATE.clear(); ORIG_SKIN.clear(); ORIG_MODEL.clear(); return; }
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
        if (true || s == null || !s.on || s.shield <= 0) return;
        PoseStack ps = e.getPoseStack();
        MultiBufferSource buf = e.getMultiBufferSource();
        int light = e.getPackedLight();
        float yaw = p.yBodyRot;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        float h = p.isCrouching() ? 0.95f : 1.05f;
        ps.translate(0.0, h, 0.36);
        ps.mulPose(Axis.XP.rotationDegrees(-6f));
        float w = 0.30f, ht = 0.48f; // medio ancho y medio alto
        drawFace(ps, buf.getBuffer(RenderType.entityCutoutNoCull(TEX_FRONT)), w, ht, 0.02f, light, false);
        drawFace(ps, buf.getBuffer(RenderType.entityCutoutNoCull(TEX_BACK)), w, ht, -0.02f, light, true);
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
        if (false && s.shield > 0 && mc.options.getCameraType().isFirstPerson()) {
            int bw = 120, bh = 180;
            RenderSystem.enableBlend();
            g.blit(TEX_BACK, sw / 2 - bw / 2, sh - bh + 20, bw, bh, 0f, 0f, 32, 48, 32, 48);
            RenderSystem.disableBlend();
        }
        int unlocked = Math.min(PoliciaMod.SKILLS.length, 1 + s.xp / PoliciaMod.XP_PER_NODE);
        int x = 8, y = 8;
        g.drawString(mc.font, "ARBOL DE HABILIDADES - XP " + s.xp, x, y, 0xFFE8C040, true);
        for (int i = 0; i < PoliciaMod.SKILLS.length; i++) {
            boolean open = i < unlocked;
            String line = (i == s.sel && open ? "> " : "  ") + (i + 1) + ". " + PoliciaMod.SKILLS[i];
            if (!open) line += "  [" + (i * PoliciaMod.XP_PER_NODE) + " XP]";
            g.drawString(mc.font, line, x, y + 12 + i * 10, open ? (i == s.sel ? 0xFFFFFFFF : 0xFFB0C4DE) : 0xFF707070, true);
        }
        String st;
        if (s.shield > 0) st = "Escudo activo: " + (s.shield + 19) / 20 + " s";
        else if (s.cooldown > 0) st = "Escudo en enfriamiento: " + (s.cooldown + 19) / 20 + " s";
        else st = "Escudo listo (J)";
        g.drawString(mc.font, st, x, y + 12 + PoliciaMod.SKILLS.length * 10 + 4, 0xFF80E0FF, true);
    }
}
