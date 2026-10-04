package com.example.greenlantern;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Sincroniza que jugadores tienen la burbuja activa (la esfera se dibuja en el cliente). */
public class ShieldFx {
    public static final Set<Integer> CLIENT_ON = ConcurrentHashMap.newKeySet();

    public static void send(ServerPlayer p, boolean on) {
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new Packet(p.getId(), on));
    }

    public static void register(SimpleChannel ch) {
        ch.registerMessage(5, Packet.class, Packet::encode, Packet::decode, Packet::handle);
    }

    public static class Packet {
        private final int id;
        private final boolean on;

        public Packet(int id, boolean on) { this.id = id; this.on = on; }

        public static void encode(Packet m, FriendlyByteBuf buf) { buf.writeVarInt(m.id); buf.writeBoolean(m.on); }

        public static Packet decode(FriendlyByteBuf buf) { return new Packet(buf.readVarInt(), buf.readBoolean()); }

        public static void handle(Packet m, Supplier<NetworkEvent.Context> sup) {
            NetworkEvent.Context ctx = sup.get();
            ctx.enqueueWork(() -> {
                if (m.on) CLIENT_ON.add(m.id);
                else CLIENT_ON.remove(m.id);
            });
            ctx.setPacketHandled(true);
        }
    }

    @Mod.EventBusSubscriber(modid = GreenLanternMod.MODID)
    public static class Events {
        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
            if (e.getEntity() instanceof ServerPlayer p) send(p, RingPowers.shieldActive(p));
        }

        @SubscribeEvent
        public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
            if (e.getEntity() instanceof ServerPlayer p) send(p, RingPowers.shieldActive(p));
        }

        @SubscribeEvent
        public static void onTrack(PlayerEvent.StartTracking e) {
            if (e.getTarget() instanceof ServerPlayer t && e.getEntity() instanceof ServerPlayer v
                    && RingPowers.shieldActive(t)) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> v), new Packet(t.getId(), true));
            }
        }
    }
}
