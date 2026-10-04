package com.example.greenlantern;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Oculta (sin borrar) los objetos de Legends Superheroes que no son de Linterna Verde. */
public final class HideOthers {
    private static final Pattern KEEP = Pattern.compile("lantern|willpower|oanite|^gl_");

    private HideOthers() { }

    public static boolean hidden(ItemStack st) {
        if (st.isEmpty()) return false;
        ResourceLocation k = ForgeRegistries.ITEMS.getKey(st.getItem());
        return k != null && k.getNamespace().equals("legends_superheroes") && !KEEP.matcher(k.getPath()).find();
    }

    /** Pestanas creativas: quita lo que no es de Linterna Verde (se ejecuta al final). */
    public static void onTab(BuildCreativeModeTabContentsEvent e) {
        List<ItemStack> remove = new ArrayList<>();
        for (Map.Entry<ItemStack, CreativeModeTab.TabVisibility> en : e.getEntries()) {
            if (hidden(en.getKey())) remove.add(en.getKey());
        }
        for (ItemStack s : remove) e.getEntries().remove(s);
    }
}
