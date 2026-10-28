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

  /** Tiene el anillo: puesto en su ranura, en la armadura, en la mano o en el inventario. */
  public static boolean hasRing(Player p) {
    if (isWorn(p)) return true;
    for (ItemStack s : p.getInventory().items) if (isRing(s)) return true;
    for (ItemStack s : p.getInventory().offhand) if (isRing(s)) return true;
    return false;
  }

  /** El anillo esta puesto: en su ranura propia o en una ranura de armadura. */
  public static boolean isWorn(Player p) {
    if (RingSlot.has(p)) return true;
    for (ItemStack s : p.getInventory().armor) if (isRing(s)) return true;
    return false;
  }

  // ---------- traje ----------
  public static final int COST_SUIT = 10;
  public static final int COST_JET = 25;
  public static final float NORMAL_FLY_SPEED = 0.05f;
  public static final float RING_FLY_SPEED = 0.12f;

  public static boolean isSuitOn(Player p) { return data(p).getBoolean("GLSuitOn"); }

  /** Diseño elegido: traje (bits 0-1), emblema (2-3), verde (4-5), sin antifaz (bit 6). */
  public static int design(Player p) { return data(p).getInt("GLDesign") & 127; }

  public static void setDesign(ServerPlayer p, int d) {
    data(p).putInt("GLDesign", d & 127);
    sendSuit(p);
  }

  public static void sendSuit(ServerPlayer p) {
    ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
        new ModNetwork.SuitPacket(p.getId(), isSuitOn(p), design(p)));
  }

  public static void setSuit(ServerPlayer p, boolean on) {
    if (isSuitOn(p) == on) return;
    data(p).putBoolean("GLSuitOn", on);
    if (!on) setFlight(p, false);
    sendSuit(p);
    ServerLevel level = p.serverLevel();
    level.playSound(null, p.blockPosition(), on ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, on ? 1.5f : 1.3f);
  }

  public static boolean isFlying(Player p) { return data(p).getBoolean("GLFlying"); }

  public static void setFlight(ServerPlayer p, boolean on) {
    data(p).putBoolean("GLFlying", on);
    net.minecraft.world.entity.player.Abilities ab = p.getAbilities();
    if (!p.isCreative() && !p.isSpectator()) {
      ab.mayfly = on;
      if (!on) ab.flying = false;
    }
    if (on) ab.flying = true;
    ab.setFlyingSpeed(on ? RING_FLY_SPEED : NORMAL_FLY_SPEED);
    p.onUpdateAbilities();
  }

  private static void toggleSuit(ServerPlayer p) {
    if (isSuitOn(p)) { setSuit(p, false); bar(p, "El traje vuelve al anillo"); return; }
    if (!hasRing(p)) { p.displayClientMessage(Component.literal("\u00a7cNecesitas el Anillo de Poder"), true); return; }
    if (Oath.active(p)) return;
    if (!spend(p, COST_SUIT)) return;
    setSuit(p, true);
    p.serverLevel().sendParticles(GREEN, p.getX(), p.getY() + 1.0, p.getZ(), 40, 0.4, 0.9, 0.4, 0.02);
    bar(p, "Traje activado");
  }

  private static void toggleFlight(ServerPlayer p) {
    if (!isSuitOn(p)) { p.displayClientMessage(Component.literal("\u00a7cPrimero activa el traje (H)"), true); return; }
    boolean on = !isFlying(p);
    setFlight(p, on);
    if (on) p.serverLevel().sendParticles(GREEN, p.getX(), p.getY() + 0.1, p.getZ(), 30, 0.6, 0.1, 0.6, 0.05);
    bar(p, on ? "Vuelo activado" : "Vuelo desactivado");
  }

  private static void summonJet(ServerPlayer p) {
    if (!isSuitOn(p)) { p.displayClientMessage(Component.literal("\u00a7cPrimero activa el traje (H)"), true); return; }
    if (p.isPassenger()) return;
    if (!spend(p, COST_JET)) return;
    ServerLevel level = p.serverLevel();
    JetEntity jet = GreenLanternMod.JET.get().create(level);
    if (jet == null) return;
    jet.moveTo(p.getX(), p.getY() + 0.3, p.getZ(), p.getYRot(), 0.0f);
    level.addFreshEntity(jet);
    p.startRiding(jet);
    level.sendParticles(GREEN, p.getX(), p.getY() + 0.6, p.getZ(), 50, 1.2, 0.4, 1.2, 0.05);
    level.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.4f);
    bar(p, "Caza de energia: W acelera, S frena, mira para dirigir (35 s)");
  }

    private static void bar(ServerPlayer p, String msg) {
        p.displayClientMessage(Component.literal("\u00a7a" + msg + " \u00a77[Voluntad " + getEnergy(p) + "/" + MAX_ENERGY + "]"), true);
    }

    // ---------- acciones ----------
    public static void activate(ServerPlayer p, int action) {
        switch (action) {
            case 1 -> toggleSuit(p);
            case 2 -> toggleFlight(p);
            case 3 -> summonJet(p);
            case 4 -> Powers.beam(p);
            case 5 -> Powers.wave(p);
            case 6 -> Powers.bubble(p, true);
            case 7 -> Powers.bubble(p, false);
            case 8 -> Powers.pulse(p);
            default -> { }
        }
    }

    static boolean spend(ServerPlayer p, int cost) {
        if (getEnergy(p) < cost) {
            p.displayClientMessage(Component.literal("\u00a7cTe falta voluntad: recargala jurando ante una Bateria de Voluntad"), true);
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

    /** Recarga completa (al terminar el juramento ante la bateria). */
    public static void recharge(Player p) {
        setEnergy(p, MAX_ENERGY);
    }
}
