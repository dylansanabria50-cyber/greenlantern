package com.example.greenlantern.client;

import com.example.greenlantern.GreenLanternMod;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/** Estado del traje de cada jugador visto desde este cliente y progreso de la animacion de nanobots. */
public class ClientSuit {
    private static final Map<Integer, Boolean> ON = new HashMap<>();
    private static final Map<Integer, Long> SINCE = new HashMap<>();
    private static final float FORM_TICKS = 30f, DISSOLVE_TICKS = 20f;

    public static void set(int entityId, boolean on) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0L : mc.level.getGameTime();
        ON.put(entityId, on);
        SINCE.put(entityId, now);
    }

    public static void clear() {
        ON.clear();
        SINCE.clear();
    }

    /** 0 = sin traje, 1 = traje completo. Sube mientras se forma y baja mientras se disuelve. */
    public static float progress(int entityId, float partialTicks) {
        Boolean on = ON.get(entityId);
        Minecraft mc = Minecraft.getInstance();
        if (on == null || mc.level == null) return 0f;
        float t = (mc.level.getGameTime() + partialTicks) - SINCE.get(entityId);
        return on ? Mth.clamp(t / FORM_TICKS, 0f, 1f) : 1f - Mth.clamp(t / DISSOLVE_TICKS, 0f, 1f);
    }

    @Mod.EventBusSubscriber(modid = GreenLanternMod.MODID, value = Dist.CLIENT)
    public static class Events {
        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
            clear();
        }
    }
}
