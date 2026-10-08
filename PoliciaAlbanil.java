package com.example.policia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
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
        int lv, sel, pickT, structT, c0, c1;
        static void enc(AlbSync m, FriendlyByteBuf b) {
            b.writeVarInt(m.lv); b.writeVarInt(m.sel); b.writeVarInt(m.pickT); b.writeVarInt(m.structT); b.writeVarInt(m.c0); b.writeVarInt(m.c1);
        }
        static AlbSync dec(FriendlyByteBuf b) {
            AlbSync m = new AlbSync();
            m.lv = b.readVarInt(); m.sel = b.readVarInt(); m.pickT = b.readVarInt(); m.structT = b.readVarInt(); m.c0 = b.readVarInt(); m.c1 = b.readVarInt();
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
        PoliciaMod.NET.send(PacketDistributor.PLAYER.with(() -> p), m);
    }

    static int lv(ServerPlayer p) { return Math.max(0, Math.min(2, p.getPersistentData().getInt("alb_lv"))); }

    // ---------- acciones ----------
    static void act(ServerPlayer p, int id) {
        CompoundTag d = p.getPersistentData();
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
        if (id >= 10 && id < 20) { // elegir
            int s = id - 10;
            if (s == 2) s = 1;
            if (s == 0 || (s == 1 && lv(p) >= 1)) {
                d.putInt("alb_sel", s);
                PoliciaMod.msg(p, "Habilidad: " + (s == 0 ? NAMES[0] : (lv(p) >= 2 ? NAMES[2] : NAMES[1])));
                PoliciaMod.sync(p);
            }
            return;
        }
        if (!PoliciaMod.on(p)) { PoliciaMod.msg(p, "Activa el modo albanil (B) primero"); return; }
        if (id == 2) {
            int s = d.getInt("alb_sel") == 0 && lv(p) >= 1 ? 1 : 0;
            d.putInt("alb_sel", s);
            PoliciaMod.msg(p, "Habilidad: " + (s == 0 ? NAMES[0] : (lv(p) >= 2 ? NAMES[2] : NAMES[1])));
            PoliciaMod.sync(p);
            return;
        }
        if (id != 1) return;
        int s = d.getInt("alb_sel");
        if (s == 1 && lv(p) < 1) s = 0;
        if (s == 0) {
            if (d.getInt("alb_pick") > 0) { PoliciaMod.msg(p, "El pico ya esta activo"); return; }
            if (d.getInt("alb_c0") > 0) { PoliciaMod.msg(p, "Pico en enfriamiento: " + (d.getInt("alb_c0") + 19) / 20 + " s"); return; }
            d.putInt("alb_pick", PICK_T);
            startPick(p);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.6f, 1.4f);
            PoliciaMod.msg(p, "Pico de albanil: fuerza y prisa minera");
        } else {
            if (d.getInt("alb_st") > 0) { PoliciaMod.msg(p, "La construccion sigue en pie"); return; }
            if (d.getInt("alb_c1") > 0) { PoliciaMod.msg(p, "Construccion en enfriamiento: " + (d.getInt("alb_c1") + 19) / 20 + " s"); return; }
            boolean house = lv(p) >= 2;
            if (house) buildHouse(p); else buildWall(p);
            d.putInt("alb_st", house ? HOUSE_T : WALL_T);
            d.putInt("alb_house", house ? 1 : 0);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.7f, 1.2f);
            PoliciaMod.msg(p, house ? "REFUGIO levantado" : "MURO levantado");
        }
        PoliciaMod.sync(p);
    }

    // ---------- pico ----------
    static boolean isPick(ItemStack s) { return !s.isEmpty() && s.hasTag() && s.getTag().getBoolean("alb_pick"); }

    static ItemStack makePick() {
        ItemStack pk = new ItemStack(Items.DIAMOND_PICKAXE);
        pk.getOrCreateTag().putBoolean("alb_pick", true);
        pk.getOrCreateTag().putBoolean("Unbreakable", true);
        pk.setHoverName(Component.literal("Pico de albanil"));
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
            inv.setItem(slot, makePick());
        }
        effects(p);
    }

    static void keepPick(ServerPlayer p) {
        if (!hasPick(p)) p.getInventory().add(makePick());
        effects(p);
    }

    static void endPick(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        Inventory inv = p.getInventory();
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
            Blocks.GLASS, Blocks.GLOWSTONE, Blocks.RED_BED, Blocks.CHEST);

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
        for (int u = -1; u <= 1; u++) for (int h = 0; h < 3; h++) {
            put(lv, out, at(o, f, r, 2, u, h), Blocks.STONE_BRICKS.defaultBlockState(), 3);
        }
        store(p, out);
    }

    static void buildHouse(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        Direction f = p.getDirection(), r = f.getClockWise();
        BlockPos o = p.blockPosition();
        List<Long> out = new ArrayList<>();
        BlockState plank = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        for (int fw = 2; fw <= 6; fw++) for (int u = -2; u <= 2; u++) {
            put(lv, out, at(o, f, r, fw, u, -1), Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
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
        BlockPos foot = at(o, f, r, 4, -1, 0), head = foot.relative(f);
        if (free(lv, foot) && free(lv, head)) {
            BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, f);
            put(lv, out, head, bed.setValue(BedBlock.PART, BedPart.HEAD), 18);
            put(lv, out, foot, bed.setValue(BedBlock.PART, BedPart.FOOT), 18);
        }
        BlockPos chest = at(o, f, r, 5, 1, 0);
        if (free(lv, chest)) {
            put(lv, out, chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, f.getOpposite()), 3);
            CompoundTag d = p.getPersistentData();
            if (d.contains("alb_chest") && lv.getBlockEntity(chest) instanceof ChestBlockEntity cbe) {
                cbe.load(d.getCompound("alb_chest").copy());
                cbe.setChanged();
            }
        }
        store(p, out);
    }

    static void removeStruct(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        long[] arr = d.getLongArray("alb_w");
        if (arr.length == 0) return;
        ServerLevel lv = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(d.getString("alb_wd"))));
        if (lv == null) lv = p.serverLevel();
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
    }

    // ---------- eventos ----------
    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || !isAlb(p)) return;
        CompoundTag d = p.getPersistentData();
        boolean ch = false;
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
    public void death(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && isAlb(p)) reset(p);
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) reset(p);
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            reset(p);
            if (isAlb(p)) send(p);
        }
    }
}
