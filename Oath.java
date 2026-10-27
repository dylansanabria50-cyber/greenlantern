package com.example.greenlantern;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Juramento ante la Bateria de Voluntad: el jugador se queda cerca mientras aparecen las cuatro lineas,
 * la bateria le pasa energia por un rayo y al final la voluntad queda llena. Texto propio del mod.
 */
@Mod.EventBusSubscriber(modid = GreenLanternMod.MODID)
public final class Oath {
    private static final String[] LINEAS = {
            "Con esta luz que nace de mi voluntad,",
            "ninguna sombra me hara retroceder.",
            "Quien tema la luz que llevo, que se aparte:",
            "mi voluntad es mi escudo y mi camino!"
    };
    private static final int POR_LINEA = 40, FIN = LINEAS.length * POR_LINEA + 10, ESPERA = 600;

    private static final class Estado {
        final ResourceKey<Level> dim; final BlockPos pos; int t;
        Estado(ResourceKey<Level> dim, BlockPos pos) { this.dim = dim; this.pos = pos; }
    }

    private static final Map<UUID, Estado> ACTIVOS = new HashMap<>();
    private static final Map<UUID, Long> ESPERAS = new HashMap<>();

    private Oath() { }

    public static boolean active(Player p) { return ACTIVOS.containsKey(p.getUUID()); }

    public static void start(ServerPlayer p, BlockPos pos) {
        if (active(p)) return;
        if (!RingPowers.hasRing(p)) {
            p.displayClientMessage(Component.literal("§cNecesitas el Anillo de Poder para jurar ante la bateria"), true);
            return;
        }
        long ahora = p.serverLevel().getGameTime();
        Long hasta = ESPERAS.get(p.getUUID());
        if (hasta != null && ahora < hasta) {
            p.displayClientMessage(Component.literal("§7La bateria se esta cargando: " + ((hasta - ahora) / 20 + 1) + " s"), true);
            return;
        }
        ACTIVOS.put(p.getUUID(), new Estado(p.level().dimension(), pos.immutable()));
        p.connection.send(new ClientboundSetTitlesAnimationPacket(6, POR_LINEA, 10));
        p.serverLevel().playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0f, 0.6f);
    }

    private static void titulo(ServerPlayer p, Component grande, Component chico) {
        p.connection.send(new ClientboundSetSubtitleTextPacket(chico));
        p.connection.send(new ClientboundSetTitleTextPacket(grande));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side != LogicalSide.SERVER) return;
        if (!(e.player instanceof ServerPlayer p)) return;
        Estado s = ACTIVOS.get(p.getUUID());
        if (s == null) return;
        ServerLevel level = p.serverLevel();
        Vec3 bat = Vec3.atCenterOf(s.pos).add(0, 0.2, 0);
        if (!p.isAlive() || !level.dimension().equals(s.dim) || p.position().distanceToSqr(bat) > 25.0
                || !level.getBlockState(s.pos).is(GreenLanternMod.BATTERY.get())) {
            ACTIVOS.remove(p.getUUID());
            titulo(p, Component.empty(), Component.literal("El juramento se interrumpio").withStyle(ChatFormatting.GRAY));
            return;
        }
        s.t++;
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 3, false, false, false));
        int linea = (s.t - 1) / POR_LINEA;
        if ((s.t - 1) % POR_LINEA == 0 && linea < LINEAS.length) {
            titulo(p, Component.literal(" "), Component.literal(LINEAS[linea]).withStyle(ChatFormatting.GREEN));
            level.playSound(null, s.pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 0.8f + linea * 0.15f);
        }
        // rayo de energia de la bateria a la mano del anillo, cada vez mas fuerte
        Vec3 mano = p.getEyePosition().add(p.getLookAngle().scale(0.4)).add(0, -0.5, 0);
        Vec3 d = mano.subtract(bat);
        int n = 4 + s.t / 25;
        for (int i = 0; i < n; i++) {
            double f = level.random.nextDouble();
            Vec3 q = bat.add(d.scale(f));
            level.sendParticles(RingPowers.GREEN, q.x, q.y, q.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
        if (s.t % 4 == 0) level.sendParticles(ParticleTypes.END_ROD, bat.x, bat.y + 0.4, bat.z, 2, 0.15, 0.3, 0.15, 0.02);
        if (s.t % 10 == 0) level.sendParticles(RingPowers.GREEN, p.getX(), p.getY() + 0.1, p.getZ(), 16, 0.7, 0.05, 0.7, 0.0);
        if (s.t >= FIN) {
            ACTIVOS.remove(p.getUUID());
            ESPERAS.put(p.getUUID(), level.getGameTime() + ESPERA);
            RingPowers.recharge(p);
            p.connection.send(new ClientboundSetTitlesAnimationPacket(4, 40, 16));
            titulo(p, Component.literal("Voluntad al maximo").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                    Component.literal(RingPowers.getEnergy(p) + " / " + RingPowers.MAX_ENERGY).withStyle(ChatFormatting.DARK_GREEN));
            level.sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0, 0, 0, 0);
            level.sendParticles(RingPowers.GREEN, p.getX(), p.getY() + 1.0, p.getZ(), 120, 0.8, 1.0, 0.8, 0.15);
            level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.2f);
            level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 0.8f);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        ACTIVOS.remove(e.getEntity().getUUID());
    }
}
