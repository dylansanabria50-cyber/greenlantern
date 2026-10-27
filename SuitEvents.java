package com.example.greenlantern;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/** Mantiene sincronizado el traje: quien empieza a ver a un jugador lo ve con su traje y su diseño. */
@Mod.EventBusSubscriber(modid = GreenLanternMod.MODID)
public class SuitEvents {
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer target && e.getEntity() instanceof ServerPlayer viewer) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                    new ModNetwork.SuitPacket(target.getId(), RingPowers.isSuitOn(target), RingPowers.design(target)));
        }
    }

    /** Al entrar al mundo el traje empieza guardado (y el cliente conoce su diseño). */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            RingPowers.data(p).putBoolean("GLSuitOn", false);
            if (RingPowers.isFlying(p)) RingPowers.setFlight(p, false);
            RingPowers.sendSuit(p);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            RingPowers.data(p).putBoolean("GLSuitOn", false);
            RingPowers.data(p).putBoolean("GLFlying", false);
            RingPowers.sendSuit(p);
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) RingPowers.sendSuit(p);
    }
}
