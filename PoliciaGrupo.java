package com.example.policia;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Grupos de jugadores (de cualquier oficio): recompensas compartidas y companeros visibles. */
public class PoliciaGrupo {
    static final int MAX = 5;
    static final Map<UUID, Set<UUID>> PARTY = new HashMap<>();   // id de grupo -> miembros
    static final Map<UUID, UUID> OF = new HashMap<>();           // jugador -> id de grupo
    static final Map<UUID, UUID> INV = new HashMap<>();          // invitado -> invitador
    static final Map<UUID, Long> INV_T = new HashMap<>();

    static void init() {
        PoliciaMod.NET.registerMessage(5, Team.class, Team::enc, Team::dec, Team::handle);
        MinecraftForge.EVENT_BUS.register(new Ev());
    }

    // ------------------------------------------------------------------ paquete con los companeros

    public static class Team {
        public final List<String> names = new ArrayList<>();
        public final List<int[]> pos = new ArrayList<>();   // x, y, z, vida, oficio

        static void enc(Team m, FriendlyByteBuf f) {
            f.writeVarInt(m.names.size());
            for (int i = 0; i < m.names.size(); i++) {
                f.writeUtf(m.names.get(i), 32);
                int[] p = m.pos.get(i);
                for (int k = 0; k < 5; k++) f.writeInt(p[k]);
            }
        }

        static Team dec(FriendlyByteBuf f) {
            Team m = new Team();
            int n = f.readVarInt();
            for (int i = 0; i < n; i++) {
                m.names.add(f.readUtf(32));
                int[] p = new int[5];
                for (int k = 0; k < 5; k++) p[k] = f.readInt();
                m.pos.add(p);
            }
            return m;
        }

        static void handle(Team m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> PoliciaGrupoHud.TEAM = m);
            c.get().setPacketHandled(true);
        }
    }

    // ------------------------------------------------------------------ logica

    static Set<UUID> members(UUID p) {
        UUID id = OF.get(p);
        return id == null ? null : PARTY.get(id);
    }

    static void tell(ServerPlayer p, String s) {
        p.sendSystemMessage(Component.literal("[Grupo] " + s));
    }

    static void leave(MinecraftServer srv, UUID p) {
        UUID id = OF.remove(p);
        if (id == null) return;
        Set<UUID> s = PARTY.get(id);
        if (s == null) return;
        s.remove(p);
        ServerPlayer me = srv.getPlayerList().getPlayer(p);
        String nm = me != null ? me.getGameProfile().getName() : "Un jugador";
        for (UUID u : new ArrayList<>(s)) {
            ServerPlayer o = srv.getPlayerList().getPlayer(u);
            if (o != null) tell(o, nm + " salio del grupo");
        }
        if (s.size() <= 1) {
            List<UUID> rest = new ArrayList<>(s);
            for (UUID u : rest) OF.remove(u);
            PARTY.remove(id);
            for (UUID u : rest) {
                ServerPlayer o = srv.getPlayerList().getPlayer(u);
                if (o != null) {
                    tell(o, "El grupo se disolvio");
                    PoliciaMod.NET.send(PacketDistributor.PLAYER.with(() -> o), new Team());
                }
            }
        }
    }

    static void join(ServerPlayer a, ServerPlayer b) {
        UUID id = OF.get(a.getUUID());
        if (id == null) {
            id = UUID.randomUUID();
            PARTY.put(id, new LinkedHashSet<>());
            PARTY.get(id).add(a.getUUID());
            OF.put(a.getUUID(), id);
        }
        Set<UUID> s = PARTY.get(id);
        if (s.size() >= MAX) {
            tell(b, "El grupo esta lleno");
            return;
        }
        s.add(b.getUUID());
        OF.put(b.getUUID(), id);
        MinecraftServer srv = a.getServer();
        for (UUID u : s) {
            ServerPlayer o = srv.getPlayerList().getPlayer(u);
            if (o != null) tell(o, b.getGameProfile().getName() + " se unio al grupo");
        }
    }

    /** Reparte parte de lo ganado en una mision a los companeros cercanos. */
    static void share(ServerPlayer sp, int xp, int em) {
        Set<UUID> s = members(sp.getUUID());
        if (s == null) return;
        for (UUID u : s) {
            if (u.equals(sp.getUUID())) continue;
            ServerPlayer o = sp.getServer().getPlayerList().getPlayer(u);
            if (o == null || o.level() != sp.level() || o.distanceToSqr(sp) > 64.0 * 64.0) continue;
            int gx = Math.max(1, (xp + 1) / 2);
            int ge = Math.max(1, (em + 1) / 2);
            o.getPersistentData().putInt("pol_xp", PoliciaMod.xp(o) + gx);
            PoliciaMision.give(o, new ItemStack(Items.EMERALD, ge));
            PoliciaMod.sync(o);
            tell(o, "Recompensa compartida de " + sp.getGameProfile().getName() + ": +" + gx + " XP, +" + ge + " esmeraldas");
        }
    }

    static int jobId(ServerPlayer p) {
        String j = PoliciaMod.job(p);
        return "policia".equals(j) ? 1 : ("albanil".equals(j) ? 2 : ("ladron".equals(j) ? 3 : 0));
    }

    public static class Ev {
        @SubscribeEvent
        public void gTick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || PARTY.isEmpty()) return;
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv == null || srv.getTickCount() % 20 != 0) return;
            for (Set<UUID> s : new ArrayList<>(PARTY.values())) {
                List<ServerPlayer> on = new ArrayList<>();
                for (UUID u : s) {
                    ServerPlayer p = srv.getPlayerList().getPlayer(u);
                    if (p != null) on.add(p);
                }
                for (ServerPlayer p : on) {
                    Team t = new Team();
                    for (ServerPlayer q : on) {
                        if (q == p) continue;
                        t.names.add(q.getGameProfile().getName());
                        t.pos.add(new int[]{q.getBlockX(), q.getBlockY(), q.getBlockZ(),
                                (int) Math.ceil(q.getHealth()), q.level().dimension() == p.level().dimension() ? jobId(q) : -1});
                        if (q.level() == p.level()) q.addEffect(new MobEffectInstance(MobEffects.GLOWING, 45, 0, false, false));
                    }
                    PoliciaMod.NET.send(PacketDistributor.PLAYER.with(() -> p), t);
                }
            }
        }

        @SubscribeEvent
        public void gOut(PlayerEvent.PlayerLoggedOutEvent e) {
            if (e.getEntity() instanceof ServerPlayer sp) {
                INV.remove(sp.getUUID());
                leave(sp.getServer(), sp.getUUID());
            }
        }

        @SubscribeEvent
        public void gCmd(RegisterCommandsEvent e) {
            e.getDispatcher().register(Commands.literal("grupo")
                    .then(Commands.literal("invitar").then(Commands.argument("jugador", EntityArgument.player()).executes(c -> {
                        ServerPlayer a = c.getSource().getPlayerOrException();
                        ServerPlayer b = EntityArgument.getPlayer(c, "jugador");
                        if (a == b) {
                            tell(a, "No puede invitarse a si mismo");
                            return 0;
                        }
                        Set<UUID> mine = members(a.getUUID());
                        if (mine != null && mine.size() >= MAX) {
                            tell(a, "Su grupo esta lleno");
                            return 0;
                        }
                        INV.put(b.getUUID(), a.getUUID());
                        INV_T.put(b.getUUID(), a.serverLevel().getGameTime());
                        tell(a, "Invitacion enviada a " + b.getGameProfile().getName());
                        tell(b, a.getGameProfile().getName() + " le invita a su grupo. Escriba /grupo aceptar (60 s)");
                        return 1;
                    })))
                    .then(Commands.literal("aceptar").executes(c -> {
                        ServerPlayer b = c.getSource().getPlayerOrException();
                        UUID from = INV.remove(b.getUUID());
                        Long t = INV_T.remove(b.getUUID());
                        ServerPlayer a = from == null ? null : b.getServer().getPlayerList().getPlayer(from);
                        if (a == null || t == null || b.serverLevel().getGameTime() - t > 1200L) {
                            tell(b, "No tiene invitaciones vigentes");
                            return 0;
                        }
                        if (OF.containsKey(b.getUUID())) leave(b.getServer(), b.getUUID());
                        join(a, b);
                        return 1;
                    }))
                    .then(Commands.literal("salir").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        if (!OF.containsKey(p.getUUID())) {
                            tell(p, "No esta en ningun grupo");
                            return 0;
                        }
                        leave(p.getServer(), p.getUUID());
                        tell(p, "Salio del grupo");
                        PoliciaMod.NET.send(PacketDistributor.PLAYER.with(() -> p), new Team());
                        return 1;
                    }))
                    .then(Commands.literal("lista").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        Set<UUID> s = members(p.getUUID());
                        if (s == null) {
                            tell(p, "No esta en ningun grupo. Use /grupo invitar <jugador>");
                            return 0;
                        }
                        StringBuilder sb = new StringBuilder();
                        for (UUID u : s) {
                            ServerPlayer o = p.getServer().getPlayerList().getPlayer(u);
                            if (sb.length() > 0) sb.append(", ");
                            sb.append(o == null ? "(desconectado)" : o.getGameProfile().getName());
                        }
                        tell(p, "Miembros: " + sb);
                        return 1;
                    })));
        }
    }
}
