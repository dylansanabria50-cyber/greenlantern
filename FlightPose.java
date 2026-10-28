package com.example.greenlantern.client;

import com.example.greenlantern.GreenLanternMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Pose de vuelo con el traje (como en Legends): deja de "caminar en el aire", el cuerpo se acuesta segun la velocidad
 * hasta quedar horizontal, se ladea hasta 55 grados en las curvas, el brazo del anillo va adelante cuando va rapido,
 * las piernas quedan juntas y la cabeza mira hacia adelante.
 */
@Mod.EventBusSubscriber(modid = GreenLanternMod.MODID, value = Dist.CLIENT)
public final class FlightPose {
    private FlightPose() { }

    private static final class Estado {
        float tilt, roll, vel;
        long ultimo = System.nanoTime();
        boolean empujado;
        float xRot, xRotO;
    }

    private static final Map<Integer, Estado> ESTADOS = new HashMap<>();

    /** Vuela con el traje: en el aire, sin montar nada, sin agua y sin elitros. */
    public static boolean volando(Player pl) {
        if (ClientSuit.progress(pl.getId(), 0f) < 1f) return false;
        if (pl.onGround() || pl.isPassenger() || pl.isInWater() || pl.isFallFlying() || pl.isSpectator()) return false;
        if (pl.getAbilities().flying) return true;
        // para los otros jugadores el cliente no sabe si vuelan: se mira que no esten cayendo
        return pl != net.minecraft.client.Minecraft.getInstance().player && pl.getY() - pl.yo > -0.35;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPre(RenderPlayerEvent.Pre e) {
        AbstractClientPlayer pl = (AbstractClientPlayer) e.getEntity();
        Estado st = ESTADOS.computeIfAbsent(pl.getId(), k -> new Estado());
        long ahora = System.nanoTime();
        float dt = Mth.clamp((ahora - st.ultimo) / 1.0e9f, 0f, 0.1f);
        st.ultimo = ahora;
        boolean vuela = volando(pl);

        float pt = e.getPartialTick();
        double dx = pl.getX() - pl.xo, dz = pl.getZ() - pl.zo;
        float h = (float) Math.sqrt(dx * dx + dz * dz); // bloques por tick
        st.vel += (h - st.vel) * (1f - (float) Math.exp(-8f * dt));
        float giro = Mth.wrapDegrees(pl.yBodyRot - pl.yBodyRotO);

        float tiltObj = 0f, rollObj = 0f;
        if (vuela) {
            tiltObj = Mth.clamp(st.vel / 0.45f, 0f, 1f) * 80f;
            rollObj = Mth.clamp(giro * 4.5f * Mth.clamp(st.vel / 0.2f, 0f, 1f), -55f, 55f);
        }
        st.tilt += (tiltObj - st.tilt) * (1f - (float) Math.exp(-3.0f * dt));
        st.roll += (rollObj - st.roll) * (1f - (float) Math.exp(-(rollObj == 0f ? 1.6f : 2.6f) * dt));

        st.empujado = false;
        if (!vuela && Math.abs(st.tilt) < 0.5f && Math.abs(st.roll) < 0.5f) return;

        // nada de caminar en el aire: las piernas quedan quietas
        if (vuela) {
            pl.walkAnimation.update(0f, 1f);
            pl.walkAnimation.update(0f, 1f);
            PlayerModel<AbstractClientPlayer> m = e.getRenderer().getModel();
            if (st.vel > 0.22f && m.rightArmPose == HumanoidModel.ArmPose.EMPTY) m.rightArmPose = HumanoidModel.ArmPose.THROW_SPEAR;
        }

        // el cuerpo se acuesta y se ladea alrededor de su centro
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        st.empujado = true;
        float yaw = Mth.rotLerp(pt, pl.yBodyRotO, pl.yBodyRot);
        ps.translate(0.0, 0.9, 0.0);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.mulPose(Axis.XP.rotationDegrees(st.tilt));
        ps.mulPose(Axis.ZP.rotationDegrees(st.roll));
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        ps.translate(0.0, -0.9, 0.0);

        // la cabeza mira hacia adelante aunque el cuerpo este acostado
        st.xRot = pl.getXRot();
        st.xRotO = pl.xRotO;
        pl.setXRot(st.xRot - st.tilt * 0.85f);
        pl.xRotO = st.xRotO - st.tilt * 0.85f;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPost(RenderPlayerEvent.Post e) {
        Estado st = ESTADOS.get(e.getEntity().getId());
        if (st == null || !st.empujado) return;
        st.empujado = false;
        e.getPoseStack().popPose();
        e.getEntity().setXRot(st.xRot);
        e.getEntity().xRotO = st.xRotO;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) { ESTADOS.clear(); }
}
