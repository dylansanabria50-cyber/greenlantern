package com.example.greenlantern.client;

import com.example.greenlantern.GreenLanternMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Solo para pruebas automaticas (-Dgreenlantern.test=true): revisa que el coloso y el caza tengan dibujante y cierra el juego. */
@Mod.EventBusSubscriber(modid = GreenLanternMod.MODID, value = Dist.CLIENT)
public final class ClientTest {
    private ClientTest() { }
    private static int ticks = 0;

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !Boolean.getBoolean("greenlantern.test")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return;
        if (++ticks != 40) return;
        try {
            EntityRenderDispatcher d = mc.getEntityRenderDispatcher();
            java.lang.reflect.Field f = EntityRenderDispatcher.class.getDeclaredField("renderers");
            f.setAccessible(true);
            java.util.Map<?, ?> m = (java.util.Map<?, ?>) f.get(d);
            System.out.println("GLTEST renderers total=" + m.size() + " coloso=" + m.get(GreenLanternMod.COLOSO.get()) + " jet=" + m.get(GreenLanternMod.JET.get()));
            EntityRendererProvider.Context ctx = new EntityRendererProvider.Context(d, mc.getItemRenderer(), mc.getBlockRenderer(), d.getItemInHandRenderer(), mc.getResourceManager(), mc.getEntityModels(), mc.font);
            try { new ColosoRenderer(ctx); System.out.println("GLTEST coloso renderer OK"); } catch (Throwable t) { System.out.println("GLTEST coloso renderer FALLA"); t.printStackTrace(System.out); }
            try { new JetRenderer(ctx); System.out.println("GLTEST jet renderer OK"); } catch (Throwable t) { System.out.println("GLTEST jet renderer FALLA"); t.printStackTrace(System.out); }
        } catch (Throwable t) {
            System.out.println("GLTEST error"); t.printStackTrace(System.out);
        }
        mc.stop();
    }
}
