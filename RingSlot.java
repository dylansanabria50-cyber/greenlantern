package com.example.greenlantern;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Ranura propia del anillo (no usa las de armadura) y proteccion del anillo al morir. */
public class RingSlot {
    /** En el cliente: ids de los jugadores que llevan el anillo en su ranura. */
    public static final Set<Integer> CLIENT_WORN = ConcurrentHashMap.newKeySet();
    private static final String KEY = "GLRingSlot";
    private static final String LOOSE = "GLRingLoose";

    public static ItemStack get(Player p) {
        CompoundTag d = RingPowers.data(p);
        return d.contains(KEY) ? ItemStack.of(d.getCompound(KEY)) : ItemStack.EMPTY;
    }

    public static void set(Player p, ItemStack s) {
        CompoundTag d = RingPowers.data(p);
        if (s.isEmpty()) d.remove(KEY);
        else d.put(KEY, s.save(new CompoundTag()));
    }

    public static boolean has(Player p) {
        if (p.level().isClientSide) return CLIENT_WORN.contains(p.getId());
        return get(p).is(GreenLanternMod.POWER_RING.get());
    }

    /** Pone el anillo de la mano en la ranura (si esta libre). Solo en el servidor. */
    public static boolean equip(Player p, ItemStack hand) {
        if (!get(p).isEmpty()) return false;
        ItemStack one = hand.copy();
        one.setCount(1);
        set(p, one);
        hand.shrink(1);
        if (p instanceof ServerPlayer sp) sync(sp);
        return true;
    }

    public static void sync(ServerPlayer p) {
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new SyncPacket(p.getId(), has(p)));
    }

    public static void register(SimpleChannel ch) {
        ch.registerMessage(3, SyncPacket.class, SyncPacket::encode, SyncPacket::decode, SyncPacket::handle);
        ch.registerMessage(4, ClickPacket.class, ClickPacket::encode, ClickPacket::decode, ClickPacket::handle);
    }

    private static void click(ServerPlayer p) {
        AbstractContainerMenu menu = p.containerMenu;
        ItemStack carried = menu.getCarried();
        ItemStack slot = get(p);
        if (carried.isEmpty()) {
            if (slot.isEmpty()) return;
            menu.setCarried(slot);
            set(p, ItemStack.EMPTY);
        } else if (carried.is(GreenLanternMod.POWER_RING.get())) {
            menu.setCarried(slot);
            set(p, carried);
        } else {
            return;
        }
        menu.broadcastFullState();
        sync(p);
    }

    /** Servidor -> cliente: este jugador lleva (o no) el anillo. */
    public static class SyncPacket {
        private final int id;
        private final boolean worn;

        public SyncPacket(int id, boolean worn) { this.id = id; this.worn = worn; }

        public static void encode(SyncPacket m, FriendlyByteBuf buf) { buf.writeVarInt(m.id); buf.writeBoolean(m.worn); }

        public static SyncPacket decode(FriendlyByteBuf buf) { return new SyncPacket(buf.readVarInt(), buf.readBoolean()); }

        public static void handle(SyncPacket m, Supplier<NetworkEvent.Context> sup) {
            NetworkEvent.Context ctx = sup.get();
            ctx.enqueueWork(() -> {
                if (m.worn) CLIENT_WORN.add(m.id);
                else CLIENT_WORN.remove(m.id);
            });
            ctx.setPacketHandled(true);
        }
    }

    /** Cliente -> servidor: clic en la ranura del anillo del inventario. */
    public static class ClickPacket {
        public ClickPacket() { }

        public static void encode(ClickPacket m, FriendlyByteBuf buf) { }

        public static ClickPacket decode(FriendlyByteBuf buf) { return new ClickPacket(); }

        public static void handle(ClickPacket m, Supplier<NetworkEvent.Context> sup) {
            NetworkEvent.Context ctx = sup.get();
            ctx.enqueueWork(() -> {
                ServerPlayer p = ctx.getSender();
                if (p != null) click(p);
            });
            ctx.setPacketHandled(true);
        }
    }

    @Mod.EventBusSubscriber(modid = GreenLanternMod.MODID)
    public static class Events {
        /** Al morir, los anillos sueltos no caen: se guardan y vuelven al reaparecer. */
        @SubscribeEvent
        public static void onDrops(LivingDropsEvent e) {
            if (!(e.getEntity() instanceof ServerPlayer p)) return;
            int n = 0;
            Iterator<ItemEntity> it = e.getDrops().iterator();
            while (it.hasNext()) {
                ItemStack s = it.next().getItem();
                if (s.is(GreenLanternMod.POWER_RING.get())) {
                    n += s.getCount();
                    it.remove();
                }
            }
            if (n > 0) {
                CompoundTag d = RingPowers.data(p);
                d.putInt(LOOSE, d.getInt(LOOSE) + n);
            }
        }

        /** Los datos del anillo (ranura, voluntad...) pasan al nuevo cuerpo. */
        @SubscribeEvent
        public static void onClone(PlayerEvent.Clone e) {
            CompoundTag old = e.getOriginal().getPersistentData();
            if (old.contains(Player.PERSISTED_NBT_TAG)) {
                e.getEntity().getPersistentData().put(Player.PERSISTED_NBT_TAG,
                        old.getCompound(Player.PERSISTED_NBT_TAG).copy());
            }
        }

        @SubscribeEvent
        public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
            if (!(e.getEntity() instanceof ServerPlayer p)) return;
            CompoundTag d = RingPowers.data(p);
            int n = d.getInt(LOOSE);
            if (n > 0) {
                d.remove(LOOSE);
                for (int i = 0; i < n; i++) {
                    ItemStack ring = new ItemStack(GreenLanternMod.POWER_RING.get());
                    if (!p.getInventory().add(ring)) p.drop(ring, false);
                }
            }
            sync(p);
        }

        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
            if (e.getEntity() instanceof ServerPlayer p) sync(p);
        }

        @SubscribeEvent
        public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
            if (e.getEntity() instanceof ServerPlayer p) sync(p);
        }

        @SubscribeEvent
        public static void onTrack(PlayerEvent.StartTracking e) {
            if (e.getTarget() instanceof ServerPlayer t && e.getEntity() instanceof ServerPlayer v && has(t)) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> v), new SyncPacket(t.getId(), true));
            }
        }
    }
}
