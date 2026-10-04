package com.example.greenlantern;

import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Mod filtro: oculta de la pestana creativa lo que no es de Linterna Verde. */
@Mod("glsolo")
public class GlSolo {
    public GlSolo() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(EventPriority.LOWEST, HideOthers::onTab);
        GlTweaks.init();
    }
}
