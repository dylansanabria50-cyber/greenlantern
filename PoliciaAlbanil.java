package com.example.policia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** Oficio ALBANIL: pico, muro y refugio. */
public class PoliciaAlbanil {
    static final String JOB = "albanil";
    static final String[] NAMES = {"Pico de albanil", "MURO", "REFUGIO"};
    static final int PICK_T = 140, PICK_CD = 160;      // 7 s y 8 s
    static final int WALL_T = 300, WALL_CD = 100;      // 15 s y 5 s
    static final int HOUSE_T = 700, HOUSE_CD = 800;    // 35 s y 40 s
    static final int COST = 10;

    /** Estado del propio jugador, visto por su cliente. */
    public static class AlbSync {
        int lv, sel, pickT, structT, c0, c1, walls, hl, ht, hc, mode, lu, lt, lc, zl, zw, tu, tc, ta;
        static void enc(AlbSync m, FriendlyByteBuf b) {
            b.writeVarInt(m.lv); b.writeVarInt(m.sel); b.writeVarInt(m.pickT); b.writeVarInt(m.structT); b.writeVarInt(m.c0); b.writeVarInt(m.c1); b.writeVarInt(m.walls); b.writeVarInt(m.hl); b.writeVarInt(m.ht); b.writeVarInt(m.hc); b.writeVarInt(m.mode); b.writeVarInt(m.lu); b.writeVarInt(m.lt); b.writeVarInt(m.lc); b.writeVarInt(m.zl); b.writeVarInt(m.zw); b.writeVarInt(m.tu); b.writeVarInt(m.tc); b.writeVarInt(m.ta);
        }
        static AlbSync dec(FriendlyByteBuf b) {
            AlbSync m = new AlbSync();
            m.lv = b.readVarInt(); m.sel = b.readVarInt(); m.pickT = b.readVarInt(); m.structT = b.readVarInt(); m.c0 = b.readVarInt(); m.c1 = b.readVarInt(); m.walls = b.readVarInt(); m.hl = b.readVarInt(); m.ht = b.readVarInt(); m.hc = b.readVarInt(); m.mode = b.readVarInt(); m.lu = b.readVarInt(); m.lt = b.readVarInt(); m.lc = b.readVarInt(); m.zl = b.readVarInt(); m.zw = b.readVarInt(); m.tu = b.readVarInt(); m.tc = b.readVarInt(); m.ta = b.readVarInt();
            return m;
        }
        static void handle(AlbSync m, Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> CL = m);
            c.get().setPacketHandled(true);
        }
    }

    static AlbSync CL = new AlbSync();

    static void init() {
        PoliciaMod.NET.registerMessage(2, AlbSync.class, AlbSync::enc, AlbSync::dec, AlbSync::handle);
        PoliciaAyudante.init();
        PoliciaTractor.init();
    }

    static boolean isAlb(net.minecraft.world.entity.player.Player p) { return JOB.equals(PoliciaMod.job(p)); }

    static void send(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        AlbSync m = new AlbSync();
        m.lv = Math.max(0, Math.min(2, d.getInt("alb_lv")));
        m.sel = d.getInt("alb_sel");
        m.pickT = d.getInt("alb_pick");
        m.structT = d.getInt("alb_st");
        m.c0 = d.getInt("alb_c0");
        m.c1 = d.getInt("alb_c1");
        m.walls = d.getInt("alb_walls");
        m.hl = Math.max(0, Math.min(3, d.getInt("alb_hl")));
        m.ht = d.getInt("alb_ht");
        m.hc = d.getInt("alb_hcd");
        m.mode = d.getInt("alb_mode");
        m.lu = d.getInt("alb_lu");
        m.lt = d.getInt("alb_lamp");
        m.lc = d.getInt("alb_lcd");
        m.zl = d.getInt("alb_zon") == 1 ? d.getInt("alb_zl") : 0;
        m.zw = d.getInt("alb_zw");
        m.tu = d.getInt("alb_tu");
        m.tc = d.getInt("alb_tcd");
        m.ta = d.getInt("alb_tact");
        PoliciaMod.NET.send(PacketDistributor.PLAYER.with(() -> p), m);
    }

    static String nm(ServerPlayer p, int s) {
        if (s == 2) return "AYUDANTE";
        if (s == 3) return "LINTERNA";
        if (s == 4) return "TRACTOR";
        return s == 0 ? NAMES[0] : (lv(p) >= 2 ? NAMES[2] : NAMES[1]);
    }

    static int lv(ServerPlayer p) { return Math.max(0, Math.min(2, p.getPersistentData().getInt("alb_lv"))); }

    // ---------- acciones ----------
    static void act(ServerPlayer p, int id) {
        CompoundTag d = p.getPersistentData();
        if (id >= 1000) { setZone(p, (id - 1000) / 10, (id - 1000) % 10); return; }
        if (id == 50) { if (p.getVehicle() instanceof PoliciaTractor.TractorEntity) PoliciaTractor.openInv(p); else PoliciaAyudante.openBank(p); return; }
        if (id == 42) { PoliciaTractor.unlock(p); return; }
        if (id == 15) {
            if (d.getInt("alb_tu") >= 1) { d.putInt("alb_sel", 4); PoliciaMod.msg(p, "Habilidad: TRACTOR"); PoliciaMod.sync(p); }
            return;
        }
        if (id == 41) { // desbloquear LINTERNA
            if (d.getInt("alb_lu") >= 1) { PoliciaMod.msg(p, "LINTERNA ya esta desbloqueada"); return; }
            if (PoliciaMod.xp(p) < COST) { PoliciaMod.msg(p, "Necesitas " + COST + " XP (tienes " + PoliciaMod.xp(p) + ")"); return; }
            d.putInt("pol_xp", PoliciaMod.xp(p) - COST);
            d.putInt("alb_lu", 1);
            PoliciaMod.msg(p, "Habilidad desbloqueada: LINTERNA");
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.5f);
            PoliciaMod.sync(p);
            return;
        }
        if (id == 40) { // mejorar AYUDANTE
            int hl = d.getInt("alb_hl");
            if (hl >= 3) { PoliciaMod.msg(p, "AYUDANTE ya esta al nivel maximo"); return; }
            int cost = COST + 5 * hl;
            if (PoliciaMod.xp(p) < cost) { PoliciaMod.msg(p, "Necesitas " + cost + " XP (tienes " + PoliciaMod.xp(p) + ")"); return; }
            d.putInt("pol_xp", PoliciaMod.xp(p) - cost);
            d.putInt("alb_hl", hl + 1);
            PoliciaMod.msg(p, "AYUDANTE nivel " + (hl + 1) + ": " + PoliciaAyudante.COUNT[hl + 1] + (hl == 0 ? " ayudante" : " ayudantes") + " con pico de " + PoliciaAyudante.PICKS[hl + 1]);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.3f);
            PoliciaMod.sync(p);
            return;
        }
        if (id >= 20 && id < 30) { // desbloquear o mejorar la habilidad 2
            int s = id - 20;
            if (s != 1 && s != 2) return;
            int lv = lv(p);
            if (lv >= 2) { PoliciaMod.msg(p, "MURO ya esta mejorado a REFUGIO"); return; }
            if (PoliciaMod.xp(p) < COST) { PoliciaMod.msg(p, "Necesitas " + COST + " XP (tienes " + PoliciaMod.xp(p) + ")"); return; }
            if (lv == 0 && s == 2) { PoliciaMod.msg(p, "Desbloquea primero: MURO"); return; }
            d.putInt("pol_xp", PoliciaMod.xp(p) - COST);
            d.putInt("alb_lv", lv + 1);
            PoliciaMod.msg(p, lv == 0 ? "Habilidad desbloqueada: MURO" : "Habilidad mejorada: REFUGIO");
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
            PoliciaMod.sync(p);
            return;
        }
        if (id == 14) { // elegir LINTERNA
            if (d.getInt("alb_lu") >= 1) {
                d.putInt("alb_sel", 3);
                PoliciaMod.msg(p, "Habilidad: LINTERNA");
                PoliciaMod.sync(p);
            }
            return;
        }
        if (id == 13) { // elegir AYUDANTE
            if (d.getInt("alb_hl") >= 1) {
                d.putInt("alb_sel", 2);
                PoliciaMod.msg(p, "Habilidad: AYUDANTE");
                PoliciaMod.sync(p);
            }
            return;
        }
        if (id >= 10 && id < 20) { // elegir
            int s = id - 10;
            if (s == 2) s = 1;
            if (s == 0 || (s == 1 && lv(p) >= 1)) {
                d.putInt("alb_sel", s);
                PoliciaMod.msg(p, "Habilidad: " + nm(p, s));
                PoliciaMod.sync(p);
            }
            return;
        }
        if (!PoliciaMod.on(p)) { PoliciaMod.msg(p, "Activa el modo albanil (B) primero"); return; }
        if (id == 2) {
            int cur = d.getInt("alb_sel");
            int s = cur;
            for (int k = 1; k <= 5; k++) {
                int c = (cur + k) % 5;
                if (c == 0 || (c == 1 && lv(p) >= 1) || (c == 2 && d.getInt("alb_hl") >= 1) || (c == 3 && d.getInt("alb_lu") >= 1) || (c == 4 && d.getInt("alb_tu") >= 1)) { s = c; break; }
            }
            d.putInt("alb_sel", s);
            PoliciaMod.msg(p, "Habilidad: " + nm(p, s));
            PoliciaMod.sync(p);
            return;
        }
        if (id == 8 || id == 9) { PoliciaAyudante.answer(p, id == 8); return; }
        if (id == 7) { // orden a los ayudantes
            if (d.getInt("alb_hl") < 1) { PoliciaMod.msg(p, "Aun no tienes ayudantes: desbloquea AYUDANTE"); return; }
            int m = (d.getInt("alb_mode") + 1) % 3;
            d.putInt("alb_mode", m);
            PoliciaMod.msg(p, "Orden a los ayudantes: " + PoliciaAyudante.ORDERS[m]);
            PoliciaMod.sync(p);
            return;
        }
        if (id != 1) return;
        int s = d.getInt("alb_sel");
        if (s == 1 && lv(p) < 1) s = 0;
        if (s == 2) { PoliciaAyudante.use(p); return; }
        if (s == 3) { toggleLamp(p); return; }
        if (s == 4) { PoliciaTractor.use(p); return; }
        if (s == 0) {
            if (d.getInt("alb_pick") > 0) { PoliciaMod.msg(p, "El pico ya esta activo"); return; }
            if (d.getInt("alb_c0") > 0) { PoliciaMod.msg(p, "Pico en enfriamiento: " + (d.getInt("alb_c0") + 19) / 20 + " s"); return; }
            d.putInt("alb_pick", PICK_T);
            startPick(p);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.6f, 1.4f);
            PoliciaMod.msg(p, "Pico de albanil: fuerza y prisa minera");
        } else {
            boolean house = lv(p) >= 2;
            if (d.getInt("alb_st") > 0) {
                if (house || d.getInt("alb_house") == 1) { PoliciaMod.msg(p, "El refugio sigue en pie"); return; }
                if (d.getInt("alb_walls") >= 3) { PoliciaMod.msg(p, "Ya levantaste los 3 muros"); return; }
            } else {
                if (d.getInt("alb_c1") > 0) { PoliciaMod.msg(p, "Construccion en enfriamiento: " + (d.getInt("alb_c1") + 19) / 20 + " s"); return; }
                d.putInt("alb_walls", 0);
                d.putInt("alb_st", house ? HOUSE_T : WALL_T);
                d.putInt("alb_house", house ? 1 : 0);
            }
            if (house) buildHouse(p);
            else { buildWall(p); d.putInt("alb_walls", d.getInt("alb_walls") + 1); }
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.7f, 1.2f);
            PoliciaMod.msg(p, house ? "REFUGIO levantado" : "MURO " + d.getInt("alb_walls") + "/3");
        }
        PoliciaMod.sync(p);
    }

    // ---------- area de picado de los ayudantes ----------
    static void setZone(ServerPlayer p, int len, int wid) {
        CompoundTag d = p.getPersistentData();
        if (!isAlb(p) || d.getInt("alb_hl") < 1) return;
        len = Math.max(1, Math.min(24, len));
        wid = Math.max(1, Math.min(9, wid));
        d.putLong("alb_zo", p.blockPosition().asLong());
        d.putInt("alb_zd", p.getDirection().get2DDataValue());
        d.putInt("alb_zl", len);
        d.putInt("alb_zw", wid);
        d.putInt("alb_zon", 1);
        d.putInt("alb_mode", 1);
        PoliciaMod.msg(p, "Area fijada: " + len + " de largo x " + wid + " de ancho x 3 de alto. Orden: " + PoliciaAyudante.ORDERS[1]);
        PoliciaMod.sync(p);
    }

    // ---------- linterna ----------
    static final int LAMP_T = 1200, LAMP_CD = 400;   // 60 s y 20 s

    static void toggleLamp(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        if (!isAlb(p)) return;
        if (d.getInt("alb_lu") < 1) { PoliciaMod.msg(p, "Desbloquea primero: LINTERNA"); return; }
        if (!PoliciaMod.on(p)) { PoliciaMod.msg(p, "Activa el modo albanil (B) primero"); return; }
        if (d.getInt("alb_lamp") > 0) {
            d.putInt("alb_lamp", 0);
            lampClear(p);
            d.putInt("alb_lcd", LAMP_CD);
            PoliciaMod.msg(p, "Linterna apagada");
        } else {
            if (d.getInt("alb_lcd") > 0) { PoliciaMod.msg(p, "Linterna en enfriamiento: " + (d.getInt("alb_lcd") + 19) / 20 + " s"); return; }
            d.putInt("alb_lamp", LAMP_T);
            lampTick(p);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.8f, 1.4f);
            PoliciaMod.msg(p, "Linterna: la luz sigue tu mirada");
        }
        PoliciaMod.sync(p);
    }

    static void lampTick(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        ServerLevel lv = p.serverLevel();
        net.minecraft.world.phys.Vec3 eye = p.getEyePosition();
        net.minecraft.world.phys.Vec3 end = eye.add(p.getLookAngle().scale(48.0));
        net.minecraft.world.phys.BlockHitResult hit = lv.clip(new net.minecraft.world.level.ClipContext(eye, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        BlockPos pos = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? BlockPos.containing(end)
                : hit.getBlockPos().relative(hit.getDirection());
        BlockPos old = d.contains("alb_lp") ? BlockPos.of(d.getLong("alb_lp")) : null;
        if (old != null && old.equals(pos)) return;
        if (!lv.getBlockState(pos).isAir()) return;
        if (old != null && lv.getBlockState(old).is(Blocks.LIGHT)) lv.setBlock(old, Blocks.AIR.defaultBlockState(), 3);
        lv.setBlock(pos, Blocks.LIGHT.defaultBlockState(), 3);
        d.putLong("alb_lp", pos.asLong());
    }

    static void lampClear(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        if (!d.contains("alb_lp")) return;
        BlockPos old = BlockPos.of(d.getLong("alb_lp"));
        d.remove("alb_lp");
        ServerLevel lv = p.serverLevel();
        if (lv.getBlockState(old).is(Blocks.LIGHT)) lv.setBlock(old, Blocks.AIR.defaultBlockState(), 3);
    }

    // ---------- pico ----------
    static boolean isPick(ItemStack s) { return !s.isEmpty() && s.hasTag() && s.getTag().getBoolean("alb_pick"); }

    static ItemStack makePick(ServerPlayer p) {
        ItemStack pk = new ItemStack(Items.DIAMOND_PICKAXE);
        pk.getOrCreateTag().putBoolean("alb_pick", true);
        pk.getOrCreateTag().putBoolean("Unbreakable", true);
        pk.setHoverName(Component.literal("Pico de albanil"));
        net.minecraft.nbt.ListTag en = p.getPersistentData().getList("alb_ench", 10);
        if (!en.isEmpty()) pk.getOrCreateTag().put("Enchantments", en.copy());
        java.util.Map<net.minecraft.world.item.enchantment.Enchantment, Integer> em = net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(pk);
        if (!em.containsKey(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH)) {
            em.merge(net.minecraft.world.item.enchantment.Enchantments.BLOCK_FORTUNE, 2, Math::max);
            net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(em, pk);
        }
        return pk;
    }

    static boolean hasPick(ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (isPick(inv.getItem(i))) return true;
        return isPick(p.containerMenu.getCarried());
    }

    static void effects(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 25, 0, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 25, 0, false, false, true));
    }

    static void startPick(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        Inventory inv = p.getInventory();
        int slot = inv.selected;
        ItemStack cur = inv.getItem(slot);
        if (!d.getBoolean("alb_saved")) {
            if (!cur.isEmpty()) d.put("alb_item", cur.save(new CompoundTag())); else d.remove("alb_item");
            d.putInt("alb_slot", slot);
            d.putBoolean("alb_saved", true);
            inv.setItem(slot, makePick(p));
        }
        effects(p);
    }

    /** El pico no puede quedar guardado en cofres, barriles, cajas shulker, tolvas ni dispensadores. */
    static void guardMenu(ServerPlayer p) {
        net.minecraft.world.inventory.AbstractContainerMenu m = p.containerMenu;
        if (!(m instanceof net.minecraft.world.inventory.ChestMenu || m instanceof net.minecraft.world.inventory.ShulkerBoxMenu
                || m instanceof net.minecraft.world.inventory.HopperMenu || m instanceof net.minecraft.world.inventory.DispenserMenu)) return;
        for (net.minecraft.world.inventory.Slot sl : m.slots) {
            if (sl.container instanceof Inventory) continue;
            if (isPick(sl.getItem())) {
                sl.set(ItemStack.EMPTY);
                m.broadcastChanges();
                if (!hasPick(p)) p.getInventory().add(makePick(p));
            }
        }
    }

    static void keepPick(ServerPlayer p) {
        if (!hasPick(p)) p.getInventory().add(makePick(p));
        effects(p);
    }

    static void endPick(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isPick(inv.getItem(i))) { d.put("alb_ench", inv.getItem(i).getEnchantmentTags().copy()); break; }
        }
        for (int i = 0; i < inv.getContainerSize(); i++) if (isPick(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
        if (isPick(p.containerMenu.getCarried())) p.containerMenu.setCarried(ItemStack.EMPTY);
        p.removeEffect(MobEffects.DAMAGE_BOOST);
        p.removeEffect(MobEffects.DIG_SPEED);
        if (d.getBoolean("alb_saved")) {
            ItemStack orig = d.contains("alb_item") ? ItemStack.of(d.getCompound("alb_item")) : ItemStack.EMPTY;
            int slot = Math.max(0, Math.min(35, d.getInt("alb_slot")));
            d.remove("alb_item");
            d.putBoolean("alb_saved", false);
            if (!orig.isEmpty()) {
                if (inv.getItem(slot).isEmpty()) inv.setItem(slot, orig);
                else inv.placeItemBackInInventory(orig);
            }
        }
    }

    // ---------- construcciones ----------
    static final Set<Long> LIVE = new HashSet<>();
    static final Set<Block> MINE = Set.of(Blocks.STONE_BRICKS, Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.SPRUCE_PLANKS,
            Blocks.GLASS, Blocks.GLOWSTONE, Blocks.RED_BED, Blocks.CHEST, Blocks.OAK_DOOR, Blocks.SPRUCE_SLAB);

    static boolean free(ServerLevel lv, BlockPos bp) {
        BlockState st = lv.getBlockState(bp);
        return st.isAir() || (st.canBeReplaced() && st.getFluidState().isEmpty());
    }

    static void put(ServerLevel lv, List<Long> out, BlockPos bp, BlockState st, int flags) {
        if (!free(lv, bp)) return;
        lv.setBlock(bp, st, flags);
        out.add(bp.asLong());
        LIVE.add(bp.asLong());
    }

    static BlockPos at(BlockPos o, Direction f, Direction r, int fw, int rt, int up) {
        return o.relative(f, fw).relative(r, rt).above(up);
    }

    static void store(ServerPlayer p, List<Long> out) {
        long[] arr = new long[out.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
        CompoundTag d = p.getPersistentData();
        d.putLongArray("alb_w", arr);
        d.putString("alb_wd", p.level().dimension().location().toString());
    }

    static void buildWall(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        Direction f = p.getDirection(), r = f.getClockWise();
        BlockPos o = p.blockPosition();
        List<Long> out = new ArrayList<>();
        for (long l : p.getPersistentData().getLongArray("alb_w")) out.add(l);
        for (int u = -1; u <= 1; u++) for (int h = 0; h < 3; h++) {
            put(lv, out, at(o, f, r, 2, u, h), Blocks.STONE_BRICKS.defaultBlockState(), 3);
        }
        store(p, out);
    }

    /** La casa se apoya sobre la superficie (no entra en el terreno): piso de madera, puerta de madera, cama y cofre. */
    static void buildHouse(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        CompoundTag d = p.getPersistentData();
        Direction f = p.getDirection(), r = f.getClockWise();
        BlockPos pp = p.blockPosition();
        int top = pp.getY();
        for (int fw = 1; fw <= 6; fw++) for (int u = -2; u <= 2; u++) {
            BlockPos c = pp.relative(f, fw).relative(r, u);
            top = Math.max(top, lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ()));
        }
        if (top - pp.getY() > 6) top = pp.getY();
        BlockPos o = new BlockPos(pp.getX(), top + 1, pp.getZ());
        List<Long> out = new ArrayList<>();
        BlockState plank = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState floor = Blocks.SPRUCE_PLANKS.defaultBlockState();
        for (int fw = 2; fw <= 6; fw++) for (int u = -2; u <= 2; u++) {
            boolean fl = free(lv, at(o, f, r, fw, u, -1));
            put(lv, out, at(o, f, r, fw, u, -1), floor, 3);
            if (fl) {
                for (int k = 2; k <= 9; k++) {
                    BlockPos s = at(o, f, r, fw, u, -k);
                    if (!free(lv, s)) break;
                    put(lv, out, s, floor, 3);
                }
            }
            boolean edge = fw == 2 || fw == 6 || u == -2 || u == 2;
            boolean corner = (fw == 2 || fw == 6) && (u == -2 || u == 2);
            if (edge) {
                for (int h = 0; h < 3; h++) {
                    if (fw == 2 && u == 0 && h < 2) continue; // puerta
                    boolean win = h == 1 && fw == 4 && (u == -2 || u == 2);
                    BlockState st = corner ? log : (win ? Blocks.GLASS.defaultBlockState() : plank);
                    put(lv, out, at(o, f, r, fw, u, h), st, 3);
                }
            }
            put(lv, out, at(o, f, r, fw, u, 3), (fw == 4 && u == 0) ? Blocks.GLOWSTONE.defaultBlockState() : plank, 3);
        }
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, f);
        put(lv, out, at(o, f, r, 2, 0, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 18);
        put(lv, out, at(o, f, r, 2, 0, 1), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 18);
        put(lv, out, at(o, f, r, 1, 0, -1), Blocks.SPRUCE_SLAB.defaultBlockState(), 3); // escalon de entrada
        BlockPos foot = at(o, f, r, 4, -1, 0), head = foot.relative(f);
        if (free(lv, foot) && free(lv, head)) {
            BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, f);
            put(lv, out, head, bed.setValue(BedBlock.PART, BedPart.HEAD), 18);
            put(lv, out, foot, bed.setValue(BedBlock.PART, BedPart.FOOT), 18);
        }
        BlockPos chest = at(o, f, r, 5, 1, 0);
        if (free(lv, chest)) {
            put(lv, out, chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, f.getOpposite()), 3);
            if (d.contains("alb_chest") && lv.getBlockEntity(chest) instanceof ChestBlockEntity cbe) {
                cbe.load(d.getCompound("alb_chest").copy());
                cbe.setChanged();
            }
        }
        d.putLong("alb_o", o.asLong());
        d.putInt("alb_f", f.get2DDataValue());
        store(p, out);
        restoreInner(p, lv, o, f, r);
    }

    static AABB inner(BlockPos o, Direction f, Direction r) {
        BlockPos a = at(o, f, r, 3, -1, 0), b = at(o, f, r, 5, 1, 2);
        return new AABB(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
    }

    /** Guarda lo que el jugador dejo dentro del refugio (bloques y objetos) para devolverlo en el mismo lugar. */
    static void captureInner(ServerPlayer p, ServerLevel lv, Set<Long> ours) {
        CompoundTag d = p.getPersistentData();
        BlockPos o = BlockPos.of(d.getLong("alb_o"));
        Direction f = Direction.from2DDataValue(d.getInt("alb_f")), r = f.getClockWise();
        ListTag blocks = new ListTag(), items = new ListTag();
        List<BlockPos> clear = new ArrayList<>();
        for (int fw = 3; fw <= 5; fw++) for (int u = -1; u <= 1; u++) for (int h = 0; h <= 2; h++) {
            BlockPos bp = at(o, f, r, fw, u, h);
            if (ours.contains(bp.asLong())) continue;
            BlockState st = lv.getBlockState(bp);
            if (st.isAir() || !st.getFluidState().isEmpty() || st.getDestroySpeed(lv, bp) < 0) continue;
            CompoundTag e = new CompoundTag();
            e.putInt("fw", fw); e.putInt("u", u); e.putInt("h", h);
            e.put("st", NbtUtils.writeBlockState(st));
            BlockEntity be = lv.getBlockEntity(bp);
            if (be != null) {
                e.put("be", be.saveWithoutMetadata());
                if (be instanceof Container c) c.clearContent();
            }
            blocks.add(e);
            clear.add(bp);
        }
        for (BlockPos bp : clear) lv.setBlock(bp, Blocks.AIR.defaultBlockState(), 18);
        for (ItemEntity ie : lv.getEntitiesOfClass(ItemEntity.class, inner(o, f, r))) {
            CompoundTag e = new CompoundTag();
            e.put("item", ie.getItem().save(new CompoundTag()));
            e.putDouble("dx", ie.getX() - (o.getX() + 0.5));
            e.putDouble("dy", ie.getY() - o.getY());
            e.putDouble("dz", ie.getZ() - (o.getZ() + 0.5));
            items.add(e);
            ie.discard();
        }
        CompoundTag in = new CompoundTag();
        in.put("blocks", blocks);
        in.put("items", items);
        in.putInt("dir", f.get2DDataValue());
        d.put("alb_inner", in);
        d.remove("alb_o");
    }

    static void restoreInner(ServerPlayer p, ServerLevel lv, BlockPos o, Direction f, Direction r) {
        CompoundTag in = p.getPersistentData().getCompound("alb_inner");
        if (in.isEmpty()) return;
        int steps = ((f.get2DDataValue() - in.getInt("dir")) % 4 + 4) % 4;
        Rotation rot = Rotation.values()[steps];
        for (Tag t : in.getList("blocks", 10)) {
            CompoundTag e = (CompoundTag) t;
            BlockPos bp = at(o, f, r, e.getInt("fw"), e.getInt("u"), e.getInt("h"));
            if (!free(lv, bp)) continue;
            BlockState st = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), e.getCompound("st")).rotate(rot);
            lv.setBlock(bp, st, 18);
            if (e.contains("be")) {
                BlockEntity be = lv.getBlockEntity(bp);
                if (be != null) { be.load(e.getCompound("be")); be.setChanged(); }
            }
        }
        for (Tag t : in.getList("items", 10)) {
            CompoundTag e = (CompoundTag) t;
            ItemStack st = ItemStack.of(e.getCompound("item"));
            if (st.isEmpty()) continue;
            double dx = e.getDouble("dx"), dz = e.getDouble("dz");
            for (int k = 0; k < steps; k++) { double nx = -dz, nz = dx; dx = nx; dz = nz; }
            ItemEntity ie = new ItemEntity(lv, o.getX() + 0.5 + dx, o.getY() + e.getDouble("dy"), o.getZ() + 0.5 + dz, st);
            ie.setDeltaMovement(0, 0, 0);
            lv.addFreshEntity(ie);
        }
        p.getPersistentData().remove("alb_inner");
    }

    static void removeStruct(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        long[] arr = d.getLongArray("alb_w");
        d.putInt("alb_walls", 0);
        if (arr.length == 0) { d.remove("alb_o"); return; }
        ServerLevel lv = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(d.getString("alb_wd"))));
        if (lv == null) lv = p.serverLevel();
        if (d.contains("alb_o")) {
            Set<Long> ours = new HashSet<>();
            for (long l : arr) ours.add(l);
            captureInner(p, lv, ours);
        }
        for (long l : arr) {
            BlockPos bp = BlockPos.of(l);
            LIVE.remove(l);
            BlockState st = lv.getBlockState(bp);
            if (!MINE.contains(st.getBlock())) continue;
            if (st.is(Blocks.CHEST) && lv.getBlockEntity(bp) instanceof ChestBlockEntity cbe) {
                CompoundTag full = cbe.saveWithoutMetadata();
                CompoundTag keep = new CompoundTag();
                keep.put("Items", full.getList("Items", 10).copy());
                d.put("alb_chest", keep);
                cbe.clearContent();
            }
            lv.setBlock(bp, Blocks.AIR.defaultBlockState(), 3);
        }
        d.remove("alb_w");
        lv.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 0.8f, 0.8f);
    }

    static void reset(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        if (d.getInt("alb_pick") > 0 || d.getBoolean("alb_saved")) { d.putInt("alb_pick", 0); endPick(p); }
        if (d.getInt("alb_st") > 0 || d.getLongArray("alb_w").length > 0) { d.putInt("alb_st", 0); removeStruct(p); }
        if (d.getInt("alb_ht") > 0) { d.putInt("alb_ht", 0); PoliciaAyudante.clear(p); }
        if (d.getInt("alb_lamp") > 0) d.putInt("alb_lamp", 0);
        lampClear(p);
        PoliciaTractor.retire(p);
    }

    // ---------- eventos ----------
    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || !isAlb(p)) return;
        CompoundTag d = p.getPersistentData();
        boolean ch = false;
        if (d.getInt("alb_pick") > 0 && p.containerMenu != p.inventoryMenu) guardMenu(p);
        int pt = d.getInt("alb_pick");
        if (pt > 0) {
            d.putInt("alb_pick", --pt);
            if (pt > 0) keepPick(p);
            else {
                endPick(p);
                d.putInt("alb_c0", PICK_CD);
                PoliciaMod.msg(p, "El pico se retiro");
                ch = true;
            }
        } else if (d.getInt("alb_c0") > 0) {
            int c = d.getInt("alb_c0") - 1;
            d.putInt("alb_c0", c);
            if (c == 0) { PoliciaMod.msg(p, "Pico listo"); ch = true; }
        }
        int st = d.getInt("alb_st");
        if (st > 0) {
            d.putInt("alb_st", --st);
            if (st == 0) {
                removeStruct(p);
                d.putInt("alb_c1", d.getInt("alb_house") == 1 ? HOUSE_CD : WALL_CD);
                PoliciaMod.msg(p, "La construccion desaparecio");
                ch = true;
            }
        } else if (d.getInt("alb_c1") > 0) {
            int c = d.getInt("alb_c1") - 1;
            d.putInt("alb_c1", c);
            if (c == 0) { PoliciaMod.msg(p, "Construccion lista"); ch = true; }
        }
        int lt = d.getInt("alb_lamp");
        if (lt > 0) {
            d.putInt("alb_lamp", --lt);
            if (lt % 4 == 0) lampTick(p);
            if (lt == 0) {
                lampClear(p);
                d.putInt("alb_lcd", LAMP_CD);
                PoliciaMod.msg(p, "La linterna se apago");
                ch = true;
            }
        } else if (d.getInt("alb_lcd") > 0) {
            int c = d.getInt("alb_lcd") - 1;
            d.putInt("alb_lcd", c);
            if (c == 0) { PoliciaMod.msg(p, "Linterna lista"); ch = true; }
        }
        if (d.getInt("alb_tact") == 0 && d.getInt("alb_tcd") > 0) {
            int c = d.getInt("alb_tcd") - 1;
            d.putInt("alb_tcd", c);
            if (c == 0) { PoliciaMod.msg(p, "Tractor listo"); ch = true; }
        }
        int ht = d.getInt("alb_ht");
        if (ht > 0) {
            d.putInt("alb_ht", --ht);
            if (ht == 200 && PoliciaAyudante.hasItems(p)) PoliciaMod.msg(p, "Tus ayudantes se retiran en 10 s. Pulsa U para recoger lo que llevan");
            if (ht == 0 || (p.tickCount % 20 == 0 && PoliciaAyudante.mine(p).isEmpty())) {
                d.putInt("alb_ht", 0);
                PoliciaAyudante.clear(p);
                d.putInt("alb_hcd", PoliciaAyudante.CD);
                PoliciaMod.msg(p, PoliciaAyudante.hasItems(p) ? "Los ayudantes se retiraron y guardan lo que picaron. Pulsa U para recogerlo" : "Los ayudantes se retiraron");
                ch = true;
            }
        } else if (d.getInt("alb_hcd") > 0) {
            int c = d.getInt("alb_hcd") - 1;
            d.putInt("alb_hcd", c);
            if (c == 0) { PoliciaMod.msg(p, "Ayudantes listos"); ch = true; }
        }
        if (ch || p.tickCount % 20 == 0) send(p);
    }

    @SubscribeEvent
    public void toss(ItemTossEvent e) {
        if (isPick(e.getEntity().getItem())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void breakBlock(BlockEvent.BreakEvent e) {
        if (LIVE.contains(e.getPos().asLong())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void frame(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract e) {
        if (!isPick(e.getItemStack())) return;
        if (e.getTarget() instanceof net.minecraft.world.entity.decoration.ItemFrame || e.getTarget() instanceof net.minecraft.world.entity.decoration.ArmorStand) e.setCanceled(true);
    }

    @SubscribeEvent
    public void death(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && isAlb(p)) reset(p);
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { reset(p); PoliciaAyudante.BANKS.remove(p.getUUID()); PoliciaTractor.INVS.remove(p.getUUID()); }
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            reset(p);
            if (isAlb(p)) send(p);
        }
    }
}
