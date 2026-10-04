package com.example.greenlantern.client;

import com.example.greenlantern.GreenLanternMod;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GreenLanternMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class RingRender {
    @SubscribeEvent
    public static void registerLayer(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(RingLayer.LAYER, RingLayer::createLayer);
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers e) {
        for (String skin : e.getSkins()) {
            PlayerRenderer r = e.getSkin(skin);
            if (r != null) { r.addLayer(new RingLayer(r, e.getEntityModels())); }
        }
    }
}
