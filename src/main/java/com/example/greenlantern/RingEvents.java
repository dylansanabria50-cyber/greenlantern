package com.example.greenlantern;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;

/** Logica del vuelo y la regeneracion de voluntad. */
public class RingEvents {
    private static final float NORMAL_FLY_SPEED = 0.05f;
    private static final float RING_FLY_SPEED = 0.14f;

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side != LogicalSide.SERVER) return;
        if (!(e.player instanceof ServerPlayer p)) return;

        boolean ring = RingPowers.hasRing(p);
        boolean flightOn = RingPowers.isFlightOn(p);
        int energy = RingPowers.getEnergy(p);
        CompoundTag d = RingPowers.data(p);
        Abilities ab = p.getAbilities();

        boolean wantFly = ring && flightOn && energy > 0;

        if (wantFly) {
            if (!ab.mayfly) {
                ab.mayfly = true;
                ab.setFlyingSpeed(RING_FLY_SPEED);
                d.putBoolean("GLFly", true);
                if (!p.onGround()) ab.flying = true;
                p.onUpdateAbilities();
            }
            if (ab.flying) {
                if (p.tickCount % 20 == 0) RingPowers.setEnergy(p, energy - 1);
                if (p.tickCount % 3 == 0) {
                    p.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER,
                            p.getX(), p.getY() + 0.2, p.getZ(), 2, 0.25, 0.1, 0.25, 0.0);
                }
            }
        } else if (d.getBoolean("GLFly")) {
            d.putBoolean("GLFly", false);
            if (!p.isCreative() && !p.isSpectator()) {
                ab.mayfly = false;
                ab.flying = false;
            }
            ab.setFlyingSpeed(NORMAL_FLY_SPEED);
            p.onUpdateAbilities();
            if (ring && flightOn && energy <= 0) {
                RingPowers.setFlightOn(p, false);
                p.displayClientMessage(Component.literal("\u00a7cTe quedaste sin voluntad: vuelo desactivado"), true);
            } else if (!ring) {
                RingPowers.setFlightOn(p, false);
            }
        }

        // Regeneracion de voluntad: +1 cada medio segundo si no estas volando
        if (ring && !ab.flying && p.tickCount % 10 == 0 && energy < RingPowers.MAX_ENERGY) {
            RingPowers.setEnergy(p, energy + 1);
        }
    }

    @SubscribeEvent
    public void onFall(LivingFallEvent e) {
        if (e.getEntity() instanceof Player p && RingPowers.hasRing(p) && RingPowers.isFlightOn(p)) {
            e.setCanceled(true);
        }
    }
}
