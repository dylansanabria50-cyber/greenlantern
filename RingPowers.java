package com.example.greenlantern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.function.Consumer;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

public class RingPowers {
    public static final int MAX_ENERGY = 100;
    public static final int COST_CONJURE = 20;
    public static final int CONJURE_TICKS = 700; // 35 segundos
    private static final String CONJURE_TAG = "GLConjuredUntil";
    private static final java.util.Set<String> FORBIDDEN = java.util.Set.of(
            "minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block",
            "minecraft:command_block_minecart", "minecraft:structure_block", "minecraft:structure_void",
            "minecraft:jigsaw", "minecraft:barrier", "minecraft:light", "minecraft:debug_stick",
            "minecraft:knowledge_book", "minecraft:bedrock",
            "minecraft:totem_of_undying", "minecraft:golden_apple", "minecraft:enchanted_golden_apple",
            "minecraft:diamond", "minecraft:diamond_block", "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore", "minecraft:netherite_ingot", "minecraft:netherite_block", "minecraft:netherite_scrap", "minecraft:ancient_debris", "minecraft:gold_ingot", "minecraft:gold_nugget", "minecraft:gold_block", "minecraft:raw_gold", "minecraft:raw_gold_block", "minecraft:gold_ore", "minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore", "minecraft:iron_ingot", "minecraft:iron_nugget", "minecraft:iron_block", "minecraft:raw_iron", "minecraft:raw_iron_block", "minecraft:iron_ore", "minecraft:deepslate_iron_ore");
    public static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.1f, 1.0f, 0.25f), 1.4f);

    // ---------- datos persistentes (sobreviven a la muerte) ----------
    public static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static int getEnergy(Player p) {
        CompoundTag d = data(p);
        return d.contains("GLEnergy") ? d.getInt("GLEnergy") : MAX_ENERGY;
    }

    public static void setEnergy(Player p, int v) { data(p).putInt("GLEnergy", Math.max(0, Math.min(MAX_ENERGY, v))); }

    public static boolean isRing(ItemStack s) {
    if (s == null || s.isEmpty()) return false;
    if (s.is(GreenLanternMod.POWER_RING.get())) return true;
    net.minecraft.resources.ResourceLocation k = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem());
    return k != null && k.getNamespace().equals("legends_superheroes") && k.getPath().equals("willpower_ring");
  }

  static java.lang.reflect.Method SUIT_GET;
  static boolean SUIT_INIT;

  /** Verdadero si el jugador lleva puesto el traje de Linterna Verde de Legends Superheroes. */
  public static boolean isGlSuit(Player p) {
    try {
      if (!SUIT_INIT) {
        SUIT_INIT = true;
        Class<?> c = Class.forName("com.tihyo.legends.armors.LegendsSuit");
        SUIT_GET = c.getMethod("getSuit", net.minecraft.world.entity.LivingEntity.class);
      }
      if (SUIT_GET == null) return false;
      Object o = SUIT_GET.invoke(null, p);
      return o != null && o.getClass().getName().contains(".greenlantern.");
    } catch (Throwable t) {
      return false;
    }
  }

  public static boolean hasRing(Player p) {
    return isGlSuit(p);
  }

  @SuppressWarnings("unused")
  static boolean hasRingOld(Player p) {
        for (ItemStack s : p.getInventory().items) if (isRing(s)) return true;
        for (ItemStack s : p.getInventory().offhand) if (isRing(s)) return true;
        return isWorn(p);
    }

    /** El anillo esta "puesto" si esta en alguna de las 4 ranuras de armadura. */
    public static boolean isWorn(Player p) {
    return isGlSuit(p);
  }

  @SuppressWarnings("unused")
  static boolean isWornOld(Player p) {
        if (RingSlot.has(p)) return true;
        for (ItemStack s : p.getInventory().armor) if (isRing(s)) return true;
        return false;
    }

    private static void bar(ServerPlayer p, String msg) {
        p.displayClientMessage(Component.literal("\u00a7a" + msg + " \u00a77[Voluntad " + getEnergy(p) + "/" + MAX_ENERGY + "]"), true);
    }

    // ---------- acciones ----------
    public static void activate(ServerPlayer p, int action) { }

    private static boolean spend(ServerPlayer p, int cost) {
        if (getEnergy(p) < cost) {
            p.displayClientMessage(Component.literal("\u00a7cEl anillo necesita recargarse (Shift + clic derecho)"), true);
            return false;
        }
        setEnergy(p, getEnergy(p) - cost);
        return true;
    }

    // ---------- crear objetos (R) ----------
    public static boolean isAllowed(Item item) {
        if (item == Items.AIR) return false;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        if (key == null || FORBIDDEN.contains(key.toString())) return false;
        if (item instanceof net.minecraft.world.item.SpawnEggItem) return false;
        if (key.getNamespace().equals("legends_superheroes")
                && !java.util.regex.Pattern.compile("lantern|willpower|oanite|^gl_").matcher(key.getPath()).find()) return false;
        String path = key.getPath();
        return !(path.equals("egg") || path.endsWith("_egg") || path.endsWith("_spawn_egg"));
    }

    public static boolean isConjured(ItemStack s) {
        return !s.isEmpty() && s.hasTag() && s.getTag().contains(CONJURE_TAG);
    }

    public static long expiryOf(ItemStack s) {
        return s.getTag().getLong(CONJURE_TAG);
    }

    private static boolean expired(ItemStack s, long now) {
        return isConjured(s) && now >= expiryOf(s);
    }

    /** Marca un objeto como constructo de energia que desaparece en el tick 'end'. */
    public static void markConjured(ItemStack stack, long end) {
        stack.getOrCreateTag().putLong(CONJURE_TAG, end);
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.literal("Constructo de energia: desaparece a los 35 s de crearse"))));
        stack.getOrCreateTagElement("display").put("Lore", lore);
    }

    /** Crea el objeto en tu inventario (lo llama la ventana de R). */
    public static void conjure(ServerPlayer p, Item item, int amount) {
        if (!hasRing(p)) {
            p.displayClientMessage(Component.literal("\u00a7cNecesitas el Anillo de Poder en el inventario"), true);
            return;
        }
        if (!isAllowed(item)) {
            p.displayClientMessage(Component.literal("\u00a7cEl anillo no puede crear ese objeto"), true);
            return;
        }
        if (p.getInventory().getFreeSlot() == -1) {
            p.displayClientMessage(Component.literal("\u00a7cInventario lleno: libera un espacio"), true);
            return;
        }
        if (!spend(p, COST_CONJURE)) return;

        ItemStack stack = new ItemStack(item, Math.max(1, Math.min(amount, item.getMaxStackSize())));
        markConjured(stack, p.level().getGameTime() + CONJURE_TICKS);
        p.getInventory().add(stack);

        ServerLevel level = p.serverLevel();
        level.sendParticles(GREEN, p.getX(), p.getY() + 1.0, p.getZ(), 20, 0.4, 0.5, 0.4, 0.02);
        level.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.8f);
        bar(p, "Creaste " + stack.getCount() + " x " + stack.getHoverName().getString() + " (35 s)");
    }

    /** Cada 10 ticks: borra los objetos creados cuyo tiempo se acabo. */
    public static void expireConjured(ServerPlayer p) {
        long now = p.level().getGameTime();
        boolean any = false;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (expired(inv.getItem(i), now)) { inv.setItem(i, ItemStack.EMPTY); any = true; }
        }
        for (Slot sl : p.containerMenu.slots) {
            if (expired(sl.getItem(), now)) { sl.set(ItemStack.EMPTY); any = true; }
        }
        if (expired(p.containerMenu.getCarried(), now)) { p.containerMenu.setCarried(ItemStack.EMPTY); any = true; }
        if (any) {
            p.inventoryMenu.broadcastFullState();
            if (p.containerMenu != p.inventoryMenu) p.containerMenu.broadcastFullState();
            ServerLevel level = p.serverLevel();
            level.sendParticles(GREEN, p.getX(), p.getY() + 1.0, p.getZ(), 15, 0.4, 0.5, 0.4, 0.02);
            level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0f, 1.2f);
        }
    }

    /** Si la mesa de crafteo tiene ingredientes creados, el resultado tambien es un constructo. */
    public static void tagCraftResult(ServerPlayer p) {
        AbstractContainerMenu m = p.containerMenu;
        int from, to;
        if (m instanceof CraftingMenu) { from = 1; to = 9; }
        else if (m instanceof InventoryMenu) { from = 1; to = 4; }
        else return;
        ItemStack result = m.getSlot(0).getItem();
        if (result.isEmpty() || isConjured(result)) return;
        long end = -1;
        for (int i = from; i <= to; i++) {
            ItemStack in = m.getSlot(i).getItem();
            if (isConjured(in)) {
                long x = expiryOf(in);
                end = end < 0 ? x : Math.min(end, x);
            }
        }
        if (end >= 0) markConjured(result, end);
    }

    public static void recharge(Player p) {
        setEnergy(p, MAX_ENERGY);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 0.7f);
        p.displayClientMessage(Component.literal(
                "\u00a7aEn el dia mas brillante, en la noche mas oscura... \u00a77(Voluntad restaurada)"), false);
    }
}
