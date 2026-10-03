package com.example.greenlantern.client;

import com.example.greenlantern.GreenLanternMod;
import com.example.greenlantern.ModNetwork;
import com.example.greenlantern.RingPowers;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public class ClientEvents {
    public static final String CATEGORY = "key.categories.greenlantern";
    public static final KeyMapping FLIGHT = new KeyMapping("key.greenlantern.flight", GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping BLAST = new KeyMapping("key.greenlantern.blast", GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping SHIELD = new KeyMapping("key.greenlantern.shield", GLFW.GLFW_KEY_V, CATEGORY);
    public static final KeyMapping WALL = new KeyMapping("key.greenlantern.wall", GLFW.GLFW_KEY_B, CATEGORY);

    @Mod.EventBusSubscriber(modid = GreenLanternMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent e) {
            e.register(FLIGHT);
            e.register(BLAST);
            e.register(SHIELD);
            e.register(WALL);
        }
    }

    @Mod.EventBusSubscriber(modid = GreenLanternMod.MODID, value = Dist.CLIENT)
    public static class ForgeBus {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) return;
            while (FLIGHT.consumeClick()) ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(RingPowers.ACT_FLIGHT));
            while (BLAST.consumeClick()) ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(RingPowers.ACT_BLAST));
            while (SHIELD.consumeClick()) ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(RingPowers.ACT_SHIELD));
            while (WALL.consumeClick()) ModNetwork.CHANNEL.sendToServer(new ModNetwork.PowerPacket(RingPowers.ACT_WALL));
        }
    }
}
