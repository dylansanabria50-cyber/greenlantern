package com.example.greenlantern.client;

import com.example.greenlantern.GreenLanternMod;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** Registra a mano los dibujantes del caza y del coloso (se llama desde el constructor del mod, solo en el cliente). */
public final class ClientRegs {
    private ClientRegs() { }

    public static void init(IEventBus bus) {
        bus.addListener(ClientRegs::renderers);
        bus.addListener(ClientRegs::layers);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(GreenLanternMod.JET.get(), JetRenderer::new);
        e.registerEntityRenderer(GreenLanternMod.COLOSO.get(), ColosoRenderer::new);
    }

    private static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(JetModel.LAYER, JetModel::createLayer);
        e.registerLayerDefinition(ColosoRenderer.LAYER, ColosoRenderer::createLayer);
    }
}
