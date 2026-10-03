package com.example.greenlantern;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/** Mantiene sincronizado el traje: nuevos espectadores lo ven, y se apaga al cambiar de mundo o reaparecer. */
@Mod.EventBusSubscriber(modid = GreenLanternMod.MODID)
public class SuitEvents {
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer target && e.getEntity() instanceof ServerPlayer viewer
                && RingPowers.isSuitOn(target)) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                    new ModNetwork.SuitPacket(target.getId(), true));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) RingPowers.data(p).putBoolean("GLSuit", false);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) RingPowers.setSuit(p, false);
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) RingPowers.setSuit(p, false);
    }
}
