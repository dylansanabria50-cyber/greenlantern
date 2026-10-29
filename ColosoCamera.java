package com.example.greenlantern.client;

import com.example.greenlantern.ColosoEntity;
import com.example.greenlantern.GreenLanternMod;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Camara del coloso. Con F5 se cambia entre:
 * tercera persona lejos (atras y arriba, para ver todo el robot), vista desde los ojos del robot (primera persona)
 * y vista de frente. Usa una "camara" invisible que sigue al coloso, asi el jugador sigue manejandolo normalmente.
 */
@Mod.EventBusSubscriber(modid = GreenLanternMod.MODID, value = Dist.CLIENT)
public final class ColosoCamera {
    private ColosoCamera() { }

    private static Marker ojo;

    /** Vuelve la camara al jugador. */
    public static void soltar() {
        Minecraft mc = Minecraft.getInstance();
        if (ojo != null && mc.player != null && mc.getCameraEntity() == ojo) mc.setCameraEntity(mc.player);
        ojo = null;
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { ojo = null; return; }
        if (!(mc.player.getVehicle() instanceof ColosoEntity c)) { soltar(); return; }
        if (ojo == null || ojo.level() != mc.level) ojo = new Marker(EntityType.MARKER, mc.level);
        if (mc.getCameraEntity() != ojo) mc.setCameraEntity(ojo);

        float pt = e.renderTickTime;
        float yaw = mc.player.getViewYRot(pt), pitch = mc.player.getViewXRot(pt);
        Vec3 base = c.getPosition(pt);
        float cuerpo = Mth.rotLerp(pt, c.yRotO, c.getYRot());
        Vec3 frente = Vec3.directionFromRotation(0, cuerpo);
        Vec3 mira = Vec3.directionFromRotation(pitch, yaw);
        Vec3 pos;
        CameraType tipo = mc.options.getCameraType();
        if (tipo == CameraType.FIRST_PERSON) {
            // desde los ojos del robot (un poco delante del visor)
            pos = base.add(0, 10.1, 0).add(frente.scale(1.3));
        } else {
            // atras y arriba del robot; el juego suma 4 bloques mas en tercera persona
            Vec3 centro = base.add(0, 8.0, 0);
            Vec3 deseado = centro.subtract(mira.scale(tipo == CameraType.THIRD_PERSON_BACK ? 9.0 : -9.0)).add(0, 2.5, 0);
            BlockHitResult bh = mc.level.clip(new ClipContext(centro, deseado, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, ojo));
            pos = bh.getType() != HitResult.Type.MISS ? bh.getLocation().add(centro.subtract(deseado).normalize().scale(0.5)) : deseado;
        }
        ojo.setPos(pos.x, pos.y, pos.z);
        ojo.xo = ojo.xOld = pos.x; ojo.yo = ojo.yOld = pos.y; ojo.zo = ojo.zOld = pos.z;
        ojo.setYRot(yaw); ojo.yRotO = yaw;
        ojo.setXRot(pitch); ojo.xRotO = pitch;
    }

    /** Arriba del coloso no se dibuja la mano del jugador. */
    @SubscribeEvent
    public static void onHand(RenderHandEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() instanceof ColosoEntity) e.setCanceled(true);
    }
}
