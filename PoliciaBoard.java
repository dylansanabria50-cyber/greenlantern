package com.example.policia;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.function.Supplier;

/** Tablon del comisario (pergamino), historia narrada y brujula fisica de cada mision. */
public class PoliciaBoard {
    static final String[] STORY = {"",
            "Un ladron acaba de asaltar un puesto y huye con lo robado. Lo vieron correr por esta zona. Siga la brujula, alcancelo y espose al sospechoso.",
            "Hubo disturbios en tres puntos de la zona. Recorralos antes del tiempo limite; la brujula le ira marcando el siguiente lugar.",
            "Secuestraron a un aldeano y lo tienen retenido cerca. Siga la brujula, liberelo con clic derecho y traigalo de vuelta aqui.",
            "Una pandilla acampa cerca y asusta a los viajeros; hay un francotirador en lo alto. La brujula marca el campamento: eliminelos a todos.",
            "Escondieron evidencia clave en un cofre vigilado. Recupere el papel y traigalo aqui. La brujula marca el cofre.",
            "Un aldeano debe viajar a salvo hasta otro punto, pero huele a emboscada. Escortelo; la brujula marca el destino.",
            "Un saboteador dejo tres bombas. Desactivelas rompiendolas antes de que acabe el tiempo. La brujula marca la mas cercana.",
            "El jefe de la banda se esconde con su guardia. La brujula le lleva a su escondite. Cacelo.",
            "Siga las pistas hasta la guarida de El Cerebro. Se teletransporta e invoca refuerzos. Vaya preparado.",
            "Los ladrones del casino secuestraron a unos aldeanos. El casino esta bajo tierra, con tres plantas y mucha gente armada. La brujula le lleva a la entrada: baje, abrase paso y libere a un rehen.",
            "Los vecinos denuncian un campamento de bandidos o un escondite de ladrones cerca. La brujula marca el lugar: asaltelo y elimine a todos sus habitantes. Cuidado con los arqueros y con el jefe."};

    static void init() {
        PoliciaMod.NET.registerMessage(3, Open.class, Open::enc, Open::dec, Open::handle);
        PoliciaMod.NET.registerMessage(4, Pick.class, Pick::enc, Pick::dec, Pick::handle);
        MinecraftForge.EVENT_BUS.register(new Ev());
    }

    // ------------------------------------------------------------------ paquetes

    public static class Open {
        public int a, b, c, flags, done, bd, need, active;

        static void enc(Open m, FriendlyByteBuf f) {
            f.writeVarInt(m.a); f.writeVarInt(m.b); f.writeVarInt(m.c); f.writeVarInt(m.flags);
            f.writeVarInt(m.done); f.writeVarInt(m.bd); f.writeVarInt(m.need); f.writeVarInt(m.active);
        }

        static Open dec(FriendlyByteBuf f) {
            Open m = new Open();
            m.a = f.readVarInt(); m.b = f.readVarInt(); m.c = f.readVarInt(); m.flags = f.readVarInt();
            m.done = f.readVarInt(); m.bd = f.readVarInt(); m.need = f.readVarInt(); m.active = f.readVarInt();
            return m;
        }

        static void handle(Open m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PoliciaBoardScreen.open(m)));
            c.get().setPacketHandled(true);
        }
    }

    public static class Pick {
        public int slot;

        public Pick() {}

        public Pick(int s) { this.slot = s; }

        static void enc(Pick m, FriendlyByteBuf f) { f.writeVarInt(m.slot); }

        static Pick dec(FriendlyByteBuf f) { return new Pick(f.readVarInt()); }

        static void handle(Pick m, Supplier<NetworkEvent.Context> c) {
            ServerPlayer sp = c.get().getSender();
            c.get().enqueueWork(() -> {
                if (sp != null) pick(sp, m.slot);
            });
            c.get().setPacketHandled(true);
        }
    }

    // ------------------------------------------------------------------ servidor

    static void open(ServerPlayer sp, PoliciaMision.Pj com) {
        ServerLevel sl = sp.serverLevel();
        int[] ts = PoliciaMision.offered(com, sl);
        CompoundTag d = sp.getPersistentData();
        int done = d.getInt("pol_mdone"), bd = d.getInt("pol_bdone");
        Open o = new Open();
        o.a = ts[0];
        o.b = ts[1];
        o.c = ts[2];
        o.flags = (done >= 3 * (bd + 1) ? 1 : 0) | ((bd >= 1 && !d.getBoolean("pol_cerebro")) ? 2 : 0)
                | (d.getBoolean("pol_cerebro") ? 4 : 0) | (PoliciaStruct.Sites.get(sl).nearest(2, sp.blockPosition()) != null ? 8 : 0);
        o.done = done;
        o.bd = bd;
        o.need = Math.max(0, 3 * (bd + 1) - done);
        PoliciaMision.M m = PoliciaMision.ACTIVE.get(sp.getUUID());
        o.active = m == null ? 0 : m.type;
        PoliciaMod.NET.send(PacketDistributor.PLAYER.with(() -> sp), o);
    }

    static void pick(ServerPlayer sp, int slot) {
        if (!"policia".equals(PoliciaMod.job(sp))) return;
        ServerLevel sl = sp.serverLevel();
        List<PoliciaMision.Pj> cs = sl.getEntitiesOfClass(PoliciaMision.Pj.class, sp.getBoundingBox().inflate(8.0), p -> p.kind() == 10);
        if (cs.isEmpty()) return;
        PoliciaMision.Pj com = cs.get(0);
        CompoundTag d = sp.getPersistentData();
        int done = d.getInt("pol_mdone"), bd = d.getInt("pol_bdone");
        int[] ts = PoliciaMision.offered(com, sl);
        BlockPos o = com.blockPosition();
        if (slot >= 0 && slot <= 2) {
            PoliciaMision.start(sp, ts[slot], o);
        } else if (slot == 3) {
            if (bd >= 1 && !d.getBoolean("pol_cerebro")) PoliciaMision.start(sp, 9, o);
        } else if (slot == 4) {
            if (done >= 3 * (bd + 1)) PoliciaMision.start(sp, 8, o);
        } else if (slot == 5) {
            if (PoliciaStruct.Sites.get(sl).nearest(2, sp.blockPosition()) == null) {
                sp.sendSystemMessage(Component.literal("[Comisario] Todavia no sabemos donde esta el casino. Explore mas lejos y vuelva."));
            } else {
                PoliciaMision.start(sp, 10, o);
            }
        } else if (slot == 6) {
            PoliciaMision.M m = PoliciaMision.ACTIVE.get(sp.getUUID());
            if (m != null) {
                PoliciaMision.cleanup(sl, m);
                PoliciaMod.msg(sp, "Mision cancelada");
            }
            removeCompass(sp);
        }
    }

    static boolean setup10(ServerLevel sl, ServerPlayer sp, PoliciaMision.M m) {
        BlockPos c = PoliciaStruct.Sites.get(sl).nearest(2, sp.blockPosition());
        if (c == null) return false;
        m.dest = c;
        return true;
    }

    static void narrate(ServerPlayer sp, int type, PoliciaMision.M m) {
        String s = type >= 1 && type < STORY.length ? STORY[type] : "";
        sp.sendSystemMessage(Component.literal("[Comisario] " + PoliciaMision.TITLE[type] + ". " + s));
        sp.sendSystemMessage(Component.literal("[Comisario] Tome esta brujula: apunta a su objetivo y desaparece cuando termine la mision. Tiempo: "
                + PoliciaMision.TIME[type] / 1200 + " min."));
        PoliciaMod.msg(sp, "Mision aceptada: " + PoliciaMision.TITLE[type]);
        giveCompass(sp, m);
    }

    // ------------------------------------------------------------------ brujula

    static boolean isComp(ItemStack s) {
        return !s.isEmpty() && s.is(Items.COMPASS) && s.hasTag() && s.getTag().getBoolean("pol_comp");
    }

    static void removeCompass(ServerPlayer sp) {
        for (ItemStack s : sp.getInventory().items) if (isComp(s)) s.setCount(0);
        ItemStack off = sp.getOffhandItem();
        if (isComp(off)) off.setCount(0);
    }

    static void aim(ItemStack c, ServerLevel sl, BlockPos p, String name) {
        CompoundTag t = c.getOrCreateTag();
        t.putBoolean("pol_comp", true);
        t.putBoolean("LodestoneTracked", false);
        t.putString("LodestoneDimension", sl.dimension().location().toString());
        CompoundTag pos = new CompoundTag();
        pos.putInt("X", p.getX());
        pos.putInt("Y", p.getY());
        pos.putInt("Z", p.getZ());
        t.put("LodestonePos", pos);
        c.setHoverName(Component.literal(name));
    }

    static void giveCompass(ServerPlayer sp, PoliciaMision.M m) {
        removeCompass(sp);
        ItemStack c = new ItemStack(Items.COMPASS);
        ServerLevel sl = sp.serverLevel();
        BlockPos t = target(sl, sp, m);
        aim(c, sl, t, "Brujula del comisario");
        PoliciaMision.give(sp, c);
    }

    static BlockPos target(ServerLevel sl, ServerPlayer sp, PoliciaMision.M m) {
        BlockPos fallback = m.dest != null ? m.dest : m.origin;
        switch (m.type) {
            case 1: {
                Entity e = m.target == null ? null : sl.getEntity(m.target);
                return e != null ? e.blockPosition() : fallback;
            }
            case 2:
                return m.pts.isEmpty() ? fallback : m.pts.get(Math.min(m.phase, m.pts.size() - 1));
            case 3:
                return m.phase == 0 ? fallback : m.origin;
            case 5:
                return m.phase == 0 ? fallback : m.origin;
            case 7: {
                BlockPos best = null;
                double bd = Double.MAX_VALUE;
                for (BlockPos b : m.bombs) {
                    double d = b.distSqr(sp.blockPosition());
                    if (d < bd) {
                        bd = d;
                        best = b;
                    }
                }
                return best != null ? best : fallback;
            }
            case 8: {
                Entity e = m.target == null ? null : sl.getEntity(m.target);
                return e != null ? e.blockPosition() : fallback;
            }
            case 9:
                return m.phase < 2 && m.pts.size() > m.phase ? m.pts.get(m.phase) : fallback;
            default:
                return fallback;
        }
    }

    public static class Ev {
        @SubscribeEvent
        public void bPtick(TickEvent.PlayerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp)) return;
            if (sp.tickCount % 20 != 3) return;
            PoliciaMision.M m = PoliciaMision.ACTIVE.get(sp.getUUID());
            ServerLevel sl = sp.serverLevel();
            for (ItemStack s : sp.getInventory().items) {
                if (!isComp(s)) continue;
                if (m == null) {
                    s.setCount(0);
                    continue;
                }
                long left = Math.max(0L, (m.end - sl.getGameTime()) / 20L);
                aim(s, sl, target(sl, sp, m), "Brujula del comisario - " + left / 60 + ":" + (left % 60 < 10 ? "0" : "") + left % 60);
            }
        }
    }
}
