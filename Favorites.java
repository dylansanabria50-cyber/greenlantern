package com.example.greenlantern.client;

import com.example.greenlantern.ModNetwork;
import com.example.greenlantern.RingPowers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Tres ranuras de favoritos del anillo, guardadas en config/greenlantern_favorites.txt */
public class Favorites {
    public static final int SLOTS = 3;
    private static final String[] IDS = new String[SLOTS];
    private static boolean loaded = false;

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("greenlantern_favorites.txt");
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            Path p = file();
            if (Files.exists(p)) {
                List<String> lines = Files.readAllLines(p);
                for (int i = 0; i < SLOTS && i < lines.size(); i++) {
                    String l = lines.get(i).trim();
                    IDS[i] = l.isEmpty() ? null : l;
                }
            }
        } catch (Exception ignored) { }
    }

    private static void save() {
        try {
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < SLOTS; i++) lines.add(IDS[i] == null ? "" : IDS[i]);
            Files.write(file(), lines);
        } catch (Exception ignored) { }
    }

    public static ItemStack get(int i) {
        load();
        if (i < 0 || i >= SLOTS || IDS[i] == null) return ItemStack.EMPTY;
        ResourceLocation rl = ResourceLocation.tryParse(IDS[i]);
        if (rl == null) return ItemStack.EMPTY;
        Item it = ForgeRegistries.ITEMS.getValue(rl);
        if (it == null || it == Items.AIR || !RingPowers.isAllowed(it)) return ItemStack.EMPTY;
        return new ItemStack(it);
    }

    public static void set(int i, Item item) {
        load();
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(item);
        if (i < 0 || i >= SLOTS || rl == null) return;
        IDS[i] = rl.toString();
        save();
    }

    public static void clear(int i) {
        load();
        if (i < 0 || i >= SLOTS) return;
        IDS[i] = null;
        save();
    }

    public static void conjure(ItemStack st, boolean fullStack) {
        if (st.isEmpty()) return;
        int amount = fullStack ? st.getMaxStackSize() : 1;
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.ConjurePacket(ForgeRegistries.ITEMS.getKey(st.getItem()), amount));
    }
}
