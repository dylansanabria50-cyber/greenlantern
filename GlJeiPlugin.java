package com.example.glsolo;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** Oculta en JEI los objetos de los demas heroes (solo se carga si JEI esta instalado). */
@JeiPlugin
public class GlJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation("glsolo", "solo_linterna");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        List<ItemStack> hide = new ArrayList<>();
        for (Item it : ForgeRegistries.ITEMS) {
            ItemStack s = new ItemStack(it);
            if (HideOthers.hidden(s)) hide.add(s);
        }
        runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hide);
    }
}
