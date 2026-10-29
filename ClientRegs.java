package com.example.greenlantern.client;

import com.example.greenlantern.ColosoEntity;
import com.example.greenlantern.GreenLanternMod;
import com.example.greenlantern.JetEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * Registra los dibujantes del caza y del coloso. Ademas, si al entrar al mundo el juego no los tiene
 * (pasaba con algunas combinaciones de mods y el juego se cerraba), los agrega a mano antes de dibujar.
 */
public final class ClientRegs {
    private ClientRegs() { }

    public static void init(IEventBus bus) {
        bus.addListener(ClientRegs::renderers);
        bus.addListener(ClientRegs::layers);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ClientRegs::onJoin);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        System.out.println("[greenlantern] registrando dibujantes del caza y del coloso");
        e.registerEntityRenderer(GreenLanternMod.JET.get(), JetRenderer::new);
        e.registerEntityRenderer(GreenLanternMod.COLOSO.get(), ColosoRenderer::new);
    }

    private static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(JetModel.LAYER, JetModel::createLayer);
        e.registerLayerDefinition(ColosoRenderer.LAYER, ColosoRenderer::createLayer);
    }

    private static void onJoin(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide()) return;
        if (e.getEntity() instanceof ColosoEntity || e.getEntity() instanceof JetEntity) asegurar();
    }

    /** Si falta el dibujante del coloso o del caza en el despachador, lo agrega. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void asegurar() {
        try {
            Minecraft mc = Minecraft.getInstance();
            EntityRenderDispatcher d = mc.getEntityRenderDispatcher();
            for (Field f : EntityRenderDispatcher.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || !Map.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Map<Object, Object> m = (Map<Object, Object>) f.get(d);
                if (m == null || m.isEmpty() || !(m.keySet().iterator().next() instanceof EntityType)) continue;
                EntityType<?> col = GreenLanternMod.COLOSO.get(), jet = GreenLanternMod.JET.get();
                if (m.get(col) != null && m.get(jet) != null) return;
                EntityRendererProvider.Context ctx = new EntityRendererProvider.Context(d, mc.getItemRenderer(), mc.getBlockRenderer(),
                        d.getItemInHandRenderer(), mc.getResourceManager(), mc.getEntityModels(), mc.font);
                Map<Object, Object> nuevo = new HashMap<>(m);
                if (m.get(col) == null) nuevo.put(col, new ColosoRenderer(ctx));
                if (m.get(jet) == null) nuevo.put(jet, new JetRenderer(ctx));
                f.set(d, nuevo);
                System.out.println("[greenlantern] faltaban dibujantes del coloso o del caza: agregados a mano");
                return;
            }
        } catch (Throwable t) {
            System.out.println("[greenlantern] no se pudo revisar los dibujantes: " + t);
        }
    }
}
