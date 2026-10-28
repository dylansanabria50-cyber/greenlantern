package com.example.greenlantern.client;

import com.example.greenlantern.GreenLanternMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/** Lo que este cliente sabe del traje de cada jugador: si esta puesto, el diseño y el avance de la transformacion. */
public class ClientSuit {
    private static final Map<Integer, Boolean> ON = new HashMap<>();
    private static final Map<Integer, Long> SINCE = new HashMap<>();
    private static final Map<Integer, Integer> DESIGN = new HashMap<>();
    public static final float FORM_TICKS = 18f, DISSOLVE_TICKS = 18f, ENERGY_TICKS = 20f;
    /** Voluntad del jugador local (la manda el servidor). */
    public static int energy = 100;
    /** Mientras la pantalla de diseño esta abierta: muestra el traje del jugador local con este diseño. */
    public static int previewDesign = -1;

    public static void set(int id, boolean on, int design) {
        DESIGN.put(id, design & 127);
        Boolean before = ON.get(id);
        if (before != null && before == on) return;
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0L : mc.level.getGameTime();
        ON.put(id, on);
        SINCE.put(id, before == null && !on ? now - 1000 : now);
    }

    public static void setEnergy(int e) { energy = e; }

    public static int design(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (previewDesign >= 0 && mc.player != null && mc.player.getId() == id) return previewDesign;
        return DESIGN.getOrDefault(id, 0);
    }

    public static boolean isOn(int id) { return ON.getOrDefault(id, false); }

    /** 0 = sin traje, 1 = traje completo. Sube mientras se forma y baja mientras se disuelve. */
    public static float progress(int id, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (previewDesign >= 0 && mc.player != null && mc.player.getId() == id) return 1f;
        Boolean on = ON.get(id);
        if (on == null || mc.level == null) return 0f;
        float t = (mc.level.getGameTime() + pt) - SINCE.get(id);
        return on ? Mth.clamp(t / FORM_TICKS, 0f, 1f) : 1f - Mth.clamp(t / DISSOLVE_TICKS, 0f, 1f);
    }

    /** Ticks desde que el traje empezo a armarse o a irse (-1 si no se sabe). */
    public static float ticksSinceChange(int id, float pt) {
        Minecraft mc = Minecraft.getInstance();
        Long since = SINCE.get(id);
        if (since == null || mc.level == null) return -1f;
        return (mc.level.getGameTime() + pt) - since;
    }

    public static void clear() { ON.clear(); SINCE.clear(); DESIGN.clear(); energy = 100; }

    static Vector3f rgb(int c) { return new Vector3f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f); }

    @Mod.EventBusSubscriber(modid = GreenLanternMod.MODID, value = Dist.CLIENT)
    public static class Events {
        private static final Map<Integer, boolean[]> GUARDADO = new HashMap<>();

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) { clear(); }

        /** Con el traje puesto se ocultan las capas externas de la skin (gorro, campera, mangas) para que no asomen. */
        @SubscribeEvent
        public static void onRenderPre(RenderPlayerEvent.Pre e) {
            if (progress(e.getEntity().getId(), e.getPartialTick()) < 0.35f) return;
            PlayerModel<AbstractClientPlayer> m = e.getRenderer().getModel();
            GUARDADO.put(e.getEntity().getId(), new boolean[]{m.hat.visible, m.jacket.visible, m.leftSleeve.visible, m.rightSleeve.visible, m.leftPants.visible, m.rightPants.visible});
            m.hat.visible = m.jacket.visible = m.leftSleeve.visible = m.rightSleeve.visible = m.leftPants.visible = m.rightPants.visible = false;
        }

        @SubscribeEvent
        public static void onRenderPost(RenderPlayerEvent.Post e) {
            boolean[] g = GUARDADO.remove(e.getEntity().getId());
            if (g == null) return;
            PlayerModel<AbstractClientPlayer> m = e.getRenderer().getModel();
            m.hat.visible = g[0]; m.jacket.visible = g[1]; m.leftSleeve.visible = g[2]; m.rightSleeve.visible = g[3]; m.leftPants.visible = g[4]; m.rightPants.visible = g[5];
        }

        /** En primera persona el brazo tambien lleva el traje. */
        @SubscribeEvent
        public static void onArm(RenderArmEvent e) {
            AbstractClientPlayer pl = e.getPlayer();
            float p = progress(pl.getId(), 0f);
            if (p <= 0f) return;
            if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(pl) instanceof PlayerRenderer r)) return;
            e.setCanceled(true);
            PoseStack ps = e.getPoseStack();
            boolean derecho = e.getArm() == HumanoidArm.RIGHT;
            SuitTexture.Juego j = SuitTexture.get(design(pl.getId()));
            int f = Math.min(SuitTexture.FRAMES - 1, Math.round(p * (SuitTexture.FRAMES - 1)));
            PlayerModel<AbstractClientPlayer> m = r.getModel();
            m.attackTime = 0.0f; m.crouching = false; m.swimAmount = 0.0f;
            m.setupAnim(pl, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
            ModelPart brazo = derecho ? m.rightArm : m.leftArm;
            brazo.xRot = 0.0f;
            // el brazo con la piel del jugador debajo (sin la manga, que taparia el traje)
            brazo.render(ps, e.getMultiBufferSource().getBuffer(RenderType.entitySolid(pl.getSkinTextureLocation())), e.getPackedLight(), OverlayTexture.NO_OVERLAY);
            brazo.render(ps, e.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(j.base()[f])), e.getPackedLight(), OverlayTexture.NO_OVERLAY);
            brazo.render(ps, e.getMultiBufferSource().getBuffer(RenderType.eyes(j.brillo()[f])), 15728880, OverlayTexture.NO_OVERLAY);
            SuitArmor.renderArm(ps, e.getMultiBufferSource(), e.getPackedLight(), m, design(pl.getId()), p, derecho);
        }

        /**
         * Chispas mientras el traje aparece; al volar despacio, llamitas verdes y blancas alrededor del pecho y las piernas;
         * y el contorno verde (el brillo que manda el servidor) toma el color del equipo "gl_voluntad".
         */
        @SubscribeEvent
        public static void onTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.isPaused()) return;
            net.minecraft.world.scores.Scoreboard sb = mc.level.getScoreboard();
            net.minecraft.world.scores.PlayerTeam equipo = sb.getPlayerTeam("gl_voluntad");
            for (Player pl : mc.level.players()) {
                float p = progress(pl.getId(), 0f);
                boolean brillo = p >= 1f && pl.isCurrentlyGlowing();
                net.minecraft.world.scores.PlayerTeam actual = sb.getPlayersTeam(pl.getScoreboardName());
                if (brillo && actual == null) {
                    if (equipo == null) { equipo = sb.addPlayerTeam("gl_voluntad"); equipo.setColor(net.minecraft.ChatFormatting.GREEN); }
                    sb.addPlayerToTeam(pl.getScoreboardName(), equipo);
                } else if (!brillo && actual != null && actual == equipo) {
                    sb.removePlayerFromTeam(pl.getScoreboardName(), equipo);
                }
                if (pl.isInvisible() || p <= 0f) continue;
                int d = design(pl.getId());
                var r = mc.level.random;
                if (p < 1f) {
                    DustParticleOptions polvo = new DustParticleOptions(rgb(SuitTexture.luz(d)), 0.7f);
                    for (int i = 0; i < 3; i++) {
                        mc.level.addParticle(polvo, pl.getX() + (r.nextDouble() - 0.5) * 0.7, pl.getY() + r.nextDouble() * 1.9, pl.getZ() + (r.nextDouble() - 0.5) * 0.5, 0, 0.04, 0);
                    }
                    continue;
                }
                double vel = Math.hypot(pl.getX() - pl.xo, pl.getZ() - pl.zo);
                boolean volando = !pl.onGround() && !pl.isPassenger() && !pl.isInWater();
                if (volando && vel < 0.35) {
                    for (int i = 0; i < 2; i++) {
                        boolean abajo = r.nextBoolean();
                        float k = r.nextFloat();
                        int c = SuitTexture.luz(d);
                        Vector3f col = new Vector3f(1f, 1f, 1f).lerp(rgb(abajo ? 0x14ca2e : 0x1ab230), 0.4f + 0.6f * k);
                        DustParticleOptions llama = new DustParticleOptions(col, 1.3f + r.nextFloat() * 0.4f);
                        mc.level.addParticle(llama, pl.getX() + (r.nextDouble() - 0.5) * 0.8, pl.getY() + (abajo ? 0.4 : 1.0) + (r.nextDouble() - 0.5) * 0.8,
                                pl.getZ() + (r.nextDouble() - 0.5) * 0.8, 0, 0.01, 0);
                    }
                }
            }
        }
    }
}
