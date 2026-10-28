package com.example.greenlantern;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;

public class ModNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GreenLanternMod.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        CHANNEL.registerMessage(0, PowerPacket.class, PowerPacket::encode, PowerPacket::decode, PowerPacket::handle);
        RingSlot.register(CHANNEL);
        CHANNEL.registerMessage(1, ConjurePacket.class, ConjurePacket::encode, ConjurePacket::decode, ConjurePacket::handle);
        CHANNEL.registerMessage(5, SuitPacket.class, SuitPacket::encode, SuitPacket::decode, SuitPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(6, EnergyPacket.class, EnergyPacket::encode, EnergyPacket::decode, EnergyPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(7, DesignPacket.class, DesignPacket::encode, DesignPacket::decode, DesignPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(8, FxPacket.class, FxPacket::encode, FxPacket::decode, FxPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(9, BubblePacket.class, BubblePacket::encode, BubblePacket::decode, BubblePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /** Servidor -> clientes: el traje de un jugador (encendido o no) y su diseño. */
    public static class SuitPacket {
        final int id; final boolean on; final int design;
        public SuitPacket(int id, boolean on, int design) { this.id = id; this.on = on; this.design = design; }
        public static void encode(SuitPacket m, FriendlyByteBuf b) { b.writeVarInt(m.id); b.writeBoolean(m.on); b.writeByte(m.design); }
        public static SuitPacket decode(FriendlyByteBuf b) { return new SuitPacket(b.readVarInt(), b.readBoolean(), b.readByte()); }
        public static void handle(SuitPacket m, Supplier<NetworkEvent.Context> sup) {
            NetworkEvent.Context ctx = sup.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.greenlantern.client.ClientSuit.set(m.id, m.on, m.design)));
            ctx.setPacketHandled(true);
        }
    }

    /** Servidor -> cliente: cuanta voluntad le queda (para la barra). */
    public static class EnergyPacket {
        final int energy;
        public EnergyPacket(int energy) { this.energy = energy; }
        public static void encode(EnergyPacket m, FriendlyByteBuf b) { b.writeVarInt(m.energy); }
        public static EnergyPacket decode(FriendlyByteBuf b) { return new EnergyPacket(b.readVarInt()); }
        public static void handle(EnergyPacket m, Supplier<NetworkEvent.Context> sup) {
            NetworkEvent.Context ctx = sup.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.greenlantern.client.ClientSuit.setEnergy(m.energy)));
            ctx.setPacketHandled(true);
        }
    }

    /** Cliente -> servidor: el jugador eligió otro diseño de traje. */
    public static class DesignPacket {
        final int design;
        public DesignPacket(int design) { this.design = design; }
        public static void encode(DesignPacket m, FriendlyByteBuf b) { b.writeByte(m.design); }
        public static DesignPacket decode(FriendlyByteBuf b) { return new DesignPacket(b.readByte()); }
        public static void handle(DesignPacket m, Supplier<NetworkEvent.Context> sup) {
            NetworkEvent.Context ctx = sup.get();
            ctx.enqueueWork(() -> { ServerPlayer p = ctx.getSender(); if (p != null) RingPowers.setDesign(p, m.design); });
            ctx.setPacketHandled(true);
        }
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

    /** El cliente pide crear un objeto desde la ventana del anillo. */
    public static class ConjurePacket {
        private final ResourceLocation id;
        private final int amount;

        public ConjurePacket(ResourceLocation id, int amount) { this.id = id; this.amount = amount; }

        public static void encode(ConjurePacket m, FriendlyByteBuf buf) {
            buf.writeResourceLocation(m.id);
            buf.writeVarInt(m.amount);
        }

        public static ConjurePacket decode(FriendlyByteBuf buf) {
            return new ConjurePacket(buf.readResourceLocation(), buf.readVarInt());
        }

        public static void handle(ConjurePacket m, Supplier<NetworkEvent.Context> sup) {
            NetworkEvent.Context ctx = sup.get();
            ctx.enqueueWork(() -> {
                ServerPlayer p = ctx.getSender();
                if (p == null) return;
                Item item = ForgeRegistries.ITEMS.getValue(m.id);
                if (item != null) RingPowers.conjure(p, item, Math.max(1, Math.min(m.amount, 64)));
            });
            ctx.setPacketHandled(true);
        }
    }

    /** Servidor -> clientes: un efecto visual de un poder (rayo, onda, golpe en la burbuja...). */
    public static class FxPacket {
        final int type, id; final double ax, ay, az, bx, by, bz;
        public FxPacket(int type, int id, net.minecraft.world.phys.Vec3 a, net.minecraft.world.phys.Vec3 b) {
            this(type, id, a.x, a.y, a.z, b.x, b.y, b.z);
        }
        FxPacket(int type, int id, double ax, double ay, double az, double bx, double by, double bz) {
            this.type = type; this.id = id; this.ax = ax; this.ay = ay; this.az = az; this.bx = bx; this.by = by; this.bz = bz;
        }
        public static void encode(FxPacket m, FriendlyByteBuf b) {
            b.writeByte(m.type); b.writeVarInt(m.id);
            b.writeDouble(m.ax); b.writeDouble(m.ay); b.writeDouble(m.az); b.writeDouble(m.bx); b.writeDouble(m.by); b.writeDouble(m.bz);
        }
        public static FxPacket decode(FriendlyByteBuf b) {
            return new FxPacket(b.readByte(), b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble());
        }
        public static void handle(FxPacket m, Supplier<NetworkEvent.Context> sup) {
            NetworkEvent.Context ctx = sup.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.greenlantern.client.ClientFx.fx(m.type, m.id, m.ax, m.ay, m.az, m.bx, m.by, m.bz)));
            ctx.setPacketHandled(true);
        }
    }

    /** Servidor -> clientes: estado de la burbuja de un jugador. */
    public static class BubblePacket {
        final int id; final boolean on; final int res;
        public BubblePacket(int id, boolean on, int res) { this.id = id; this.on = on; this.res = res; }
        public static void encode(BubblePacket m, FriendlyByteBuf b) { b.writeVarInt(m.id); b.writeBoolean(m.on); b.writeByte(Math.max(0, Math.min(100, m.res))); }
        public static BubblePacket decode(FriendlyByteBuf b) { return new BubblePacket(b.readVarInt(), b.readBoolean(), b.readByte()); }
        public static void handle(BubblePacket m, Supplier<NetworkEvent.Context> sup) {
            NetworkEvent.Context ctx = sup.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.greenlantern.client.ClientFx.bubble(m.id, m.on, m.res)));
            ctx.setPacketHandled(true);
        }
    }
}
