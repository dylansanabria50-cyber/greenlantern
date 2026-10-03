package com.example.greenlantern;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public class ModNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GreenLanternMod.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        CHANNEL.registerMessage(0, PowerPacket.class, PowerPacket::encode, PowerPacket::decode, PowerPacket::handle);
    }

    public static class PowerPacket {
        private final int action;

        public PowerPacket(int action) { this.action = action; }

        public static void encode(PowerPacket m, FriendlyByteBuf buf) { buf.writeVarInt(m.action); }

        public static PowerPacket decode(FriendlyByteBuf buf) { return new PowerPacket(buf.readVarInt()); }

        public static void handle(PowerPacket m, Supplier<NetworkEvent.Context> sup) {
            NetworkEvent.Context ctx = sup.get();
            ctx.enqueueWork(() -> {
                ServerPlayer p = ctx.getSender();
                if (p != null) RingPowers.activate(p, m.action);
            });
            ctx.setPacketHandled(true);
        }
    }
}
