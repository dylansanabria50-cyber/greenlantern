package com.example.glsolo;

import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Mod filtro: oculta de la pestana creativa lo que no es de Linterna Verde. */
@Mod("glsolo")
public class GlSolo {
    /** Vida maxima con el traje (40 = 20 corazones) y armadura maxima (20 = barra vanilla llena). */
    static final double MAX_HP = 40.0D;
    static final double MAX_ARMOR = 20.0D;
    static final UUID ID_HP = UUID.fromString("5b1c2f0e-8a31-4c55-9e0a-1d2f3a4b5c6d");
    static final UUID ID_AR = UUID.fromString("7e9d4a21-3b6c-4f08-a1d5-2c8e9f0b1a3c");

    public GlSolo() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(EventPriority.LOWEST, HideOthers::onTab);
        GlTweaks.init();
        MinecraftForge.EVENT_BUS.addListener(GlSolo::onTick);
        if (FMLEnvironment.dist.isClient()) Cl.init();
    }

    static java.lang.reflect.Method SUIT_GET;
    static boolean SUIT_INIT;

    public static boolean isGlSuit(LivingEntity p) {
        try {
            if (!SUIT_INIT) {
                SUIT_INIT = true;
                Class<?> c = Class.forName("com.tihyo.legends.armors.LegendsSuit");
                SUIT_GET = c.getMethod("getSuit", LivingEntity.class);
            }
            if (SUIT_GET == null) return false;
            Object o = SUIT_GET.invoke(null, p);
            return o != null && o.getClass().getName().contains(".greenlantern.");
        } catch (Throwable t) {
            return false;
        }
    }

    static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.level().isClientSide) return;
        Player p = e.player;
        boolean gl = isGlSuit(p);
        cap(p, Attributes.MAX_HEALTH, ID_HP, MAX_HP, gl);
        cap(p, Attributes.ARMOR, ID_AR, MAX_ARMOR, gl);
        if (gl && p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    static void cap(Player p, Attribute at, UUID id, double max, boolean gl) {
        AttributeInstance in = p.getAttribute(at);
        if (in == null) return;
        AttributeModifier old = in.getModifier(id);
        double desired = 0.0D;
        if (gl) {
            double v = in.getValue() - (old != null ? old.getAmount() : 0.0D);
            if (v > max) desired = max - v;
        }
        if (old == null && desired == 0.0D) return;
        if (old != null && Math.abs(old.getAmount() - desired) < 1.0E-6D) return;
        if (old != null) in.removeModifier(id);
        if (desired != 0.0D) in.addTransientModifier(new AttributeModifier(id, "glsolo_cap", desired, AttributeModifier.Operation.ADDITION));
    }

    /** Cliente: quita las barras de Legends y devuelve la vida/armadura vanilla con el traje puesto. */
    static class Cl {
        static void init() {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, Cl::onOverlay);
        }

        static void onOverlay(net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre e) {
            net.minecraft.world.entity.player.Player p = net.minecraft.client.Minecraft.getInstance().player;
            if (p == null || !isGlSuit(p)) return;
            net.minecraft.resources.ResourceLocation id = e.getOverlay().id();
            String ns = id.getNamespace();
            String path = id.getPath();
            if (!ns.equals("minecraft") && (path.equals("health") || path.equals("legends_armor"))) {
                e.setCanceled(true);
            } else if (ns.equals("minecraft") && (path.equals("player_health") || path.equals("armor_level"))) {
                e.setCanceled(false);
            }
        }
    }
}
