package com.example.policia;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/** Construcciones: comisaria, obra y casino subterraneo de tres plantas. */
public class PoliciaStruct {
    static final String[] SN = {"comisaria", "obra", "casino"};
    static final int[] W = {15, 17, 13};
    static final int[] D = {11, 13, 13};

    static void init() {
        MinecraftForge.EVENT_BUS.register(new Ev());
    }

    // ------------------------------------------------------------------ registro de sitios

    public static class Sites extends SavedData {
        final List<List<Long>> l = new ArrayList<>();

        Sites() {
            for (int i = 0; i < 3; i++) l.add(new ArrayList<>());
        }

        static Sites get(ServerLevel sl) {
            return sl.getServer().overworld().getDataStorage().computeIfAbsent(Sites::load, Sites::new, "policia_sites");
        }

        static Sites load(CompoundTag t) {
            Sites s = new Sites();
            for (int i = 0; i < 3; i++) for (long v : t.getLongArray("k" + i)) s.l.get(i).add(v);
            return s;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            for (int i = 0; i < 3; i++) {
                long[] a = new long[l.get(i).size()];
                for (int j = 0; j < a.length; j++) a[j] = l.get(i).get(j);
                t.putLongArray("k" + i, a);
            }
            return t;
        }

        void add(int k, BlockPos p) {
            l.get(k).add(p.asLong());
            setDirty();
        }

        BlockPos nearest(int k, BlockPos from) {
            BlockPos best = null;
            double bd = Double.MAX_VALUE;
            for (long v : l.get(k)) {
                BlockPos p = BlockPos.of(v);
                double d = p.distSqr(from);
                if (d < bd) {
                    bd = d;
                    best = p;
                }
            }
            return best;
        }

        boolean near(int k, BlockPos from, int r) {
            BlockPos p = nearest(k, from);
            return p != null && p.distSqr(from) < (double) r * r;
        }
    }

    // ------------------------------------------------------------------ utilidades

    static void set(ServerLevel sl, int x, int y, int z, Block b) {
        sl.setBlock(new BlockPos(x, y, z), b.defaultBlockState(), 2);
    }

    static void set(ServerLevel sl, int x, int y, int z, BlockState b) {
        sl.setBlock(new BlockPos(x, y, z), b, 2);
    }

    static void fill(ServerLevel sl, int x0, int y0, int z0, int x1, int y1, int z1, Block b) {
        BlockState s = b.defaultBlockState();
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) sl.setBlock(new BlockPos(x, y, z), s, 2);
    }

    static int ground(ServerLevel sl, int x, int z) {
        sl.getChunk(x >> 4, z >> 4);
        return sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    /** Devuelve el nivel del suelo (el mas alto de las muestras) o -999 si el terreno no sirve. */
    static int survey(ServerLevel sl, int x0, int z0, int w, int d) {
        int mn = 9999, mx = -9999;
        int[] xs = {x0, x0 + w / 2, x0 + w - 1};
        int[] zs = {z0, z0 + d / 2, z0 + d - 1};
        for (int x : xs) {
            for (int z : zs) {
                int y = ground(sl, x, z);
                BlockState b = sl.getBlockState(new BlockPos(x, y, z));
                if (b.isAir() || !b.getFluidState().isEmpty()) return -999;
                mn = Math.min(mn, y);
                mx = Math.max(mx, y);
            }
        }
        if (mx - mn > 5 || mx < sl.getMinBuildHeight() + 30) return -999;
        return mx;
    }

    static void prepare(ServerLevel sl, int x0, int z0, int w, int d, int g) {
        for (int x = x0; x < x0 + w; x++) {
            for (int z = z0; z < z0 + d; z++) {
                for (int y = g; y > g - 14; y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState b = sl.getBlockState(p);
                    if (b.isAir() || !b.getFluidState().isEmpty() || b.canBeReplaced() || b.is(net.minecraft.tags.BlockTags.LEAVES) || b.is(net.minecraft.tags.BlockTags.LOGS)) {
                        sl.setBlock(p, Blocks.STONE_BRICKS.defaultBlockState(), 2);
                    } else {
                        break;
                    }
                }
                for (int y = g + 1; y <= g + 10; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!sl.getBlockState(p).isAir()) sl.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    static PoliciaBoss.BossNpc npc(ServerLevel sl, int kind, double x, double y, double z, float yaw, BlockPos home, int r) {
        PoliciaBoss.BossNpc b = new PoliciaBoss.BossNpc(PoliciaBoss.BOSS.get(), sl);
        b.setKind(kind);
        b.moveTo(x, y, z, yaw, 0.0f);
        b.setYHeadRot(yaw);
        b.setYBodyRot(yaw);
        if (home != null) b.setHome(home, r);
        sl.addFreshEntity(b);
        return b;
    }

    static void captive(ServerLevel sl, double x, double y, double z) {
        PoliciaMision.Pj c = new PoliciaMision.Pj(PoliciaMision.COM.get(), sl);
        c.setKind(9);
        c.setCaptive(true);
        c.getPersistentData().putBoolean("pol_cas", true);
        c.moveTo(x, y, z, 0.0f, 0.0f);
        sl.addFreshEntity(c);
    }

    static void chest(ServerLevel sl, int x, int y, int z, String table) {
        BlockPos p = new BlockPos(x, y, z);
        sl.setBlock(p, Blocks.CHEST.defaultBlockState(), 3);
        BlockEntity be = sl.getBlockEntity(p);
        if (be instanceof RandomizableContainerBlockEntity c) c.setLootTable(new ResourceLocation(table), sl.random.nextLong());
    }

    // ------------------------------------------------------------------ comisaria

    static void comisaria(ServerLevel sl, int x0, int z0, int g) {
        prepare(sl, x0 - 1, z0 - 1, 17, 13, g);
        int x1 = x0 + 14, z1 = z0 + 10;
        fill(sl, x0, g, z0, x1, g, z1, Blocks.POLISHED_ANDESITE);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                if (!edge) continue;
                for (int y = g + 1; y <= g + 4; y++) {
                    Block b = y == g + 1 ? Blocks.BLUE_CONCRETE : Blocks.WHITE_CONCRETE;
                    boolean win = (y == g + 2 || y == g + 3) && ((((x - x0) % 3 == 1) && (z == z0 || z == z1)) || (((z - z0) % 3 == 1) && (x == x0 || x == x1)));
                    if (win) b = Blocks.BLUE_STAINED_GLASS;
                    set(sl, x, y, z, b);
                }
            }
        }
        fill(sl, x0 + 6, g + 1, z1, x0 + 8, g + 3, z1, Blocks.AIR);
        fill(sl, x0, g + 5, z0, x1, g + 5, z1, Blocks.BLUE_CONCRETE);
        fill(sl, x0 - 1, g + 5, z0 - 1, x1 + 1, g + 5, z0 - 1, Blocks.WHITE_CONCRETE);
        fill(sl, x0 - 1, g + 5, z1 + 1, x1 + 1, g + 5, z1 + 1, Blocks.WHITE_CONCRETE);
        for (int lx : new int[]{3, 7, 11}) for (int lz : new int[]{3, 7}) set(sl, x0 + lx, g + 4, z0 + lz, Blocks.SEA_LANTERN);
        // tabique con hueco hacia el despacho
        fill(sl, x0 + 1, g + 1, z0 + 4, x0 + 13, g + 3, z0 + 4, Blocks.QUARTZ_BLOCK);
        fill(sl, x0 + 6, g + 1, z0 + 4, x0 + 8, g + 3, z0 + 4, Blocks.AIR);
        // despacho del jefe
        fill(sl, x0 + 5, g + 1, z0 + 3, x0 + 9, g + 1, z0 + 3, Blocks.DARK_OAK_PLANKS);
        set(sl, x0 + 7, g + 1, z0 + 1, Blocks.DARK_OAK_SLAB);
        set(sl, x0 + 1, g + 1, z0 + 1, Blocks.BOOKSHELF);
        set(sl, x0 + 1, g + 2, z0 + 1, Blocks.BOOKSHELF);
        set(sl, x0 + 13, g + 1, z0 + 1, Blocks.BOOKSHELF);
        set(sl, x0 + 13, g + 2, z0 + 1, Blocks.BOOKSHELF);
        // sala principal
        for (int dx : new int[]{3, 4, 10, 11}) set(sl, x0 + dx, g + 1, z0 + 7, Blocks.DARK_OAK_SLAB);
        set(sl, x0 + 1, g + 1, z0 + 6, Blocks.BARREL);
        set(sl, x0 + 13, g + 1, z0 + 6, Blocks.BARREL);
        set(sl, x0 + 1, g + 1, z0 + 9, Blocks.CRAFTING_TABLE);
        set(sl, x0 + 13, g + 1, z0 + 9, Blocks.CRAFTING_TABLE);
        // farolas de entrada
        set(sl, x0 + 5, g + 1, z1 + 1, Blocks.BLUE_CONCRETE);
        set(sl, x0 + 5, g + 2, z1 + 1, Blocks.LANTERN);
        set(sl, x0 + 9, g + 1, z1 + 1, Blocks.BLUE_CONCRETE);
        set(sl, x0 + 9, g + 2, z1 + 1, Blocks.LANTERN);
        BlockPos home = new BlockPos(x0 + 7, g + 1, z0 + 7);
        npc(sl, 0, x0 + 7.5, g + 1.5, z0 + 1.5, 0.0f, null, 0);
        npc(sl, 5, x0 + 4.5, g + 1, z0 + 8.5, 0.0f, home, 6);
        npc(sl, 5, x0 + 10.5, g + 1, z0 + 8.5, 0.0f, home, 6);
        npc(sl, 5, x0 + 7.5, g + 1, z0 + 6.5, 0.0f, home, 6);
        Sites.get(sl).add(0, new BlockPos(x0 + 7, g + 1, z1));
    }

    // ------------------------------------------------------------------ obra

    static void obra(ServerLevel sl, int x0, int z0, int g) {
        prepare(sl, x0 - 1, z0 - 1, 19, 15, g);
        int x1 = x0 + 16, z1 = z0 + 12;
        fill(sl, x0, g, z0, x1, g, z1, Blocks.COARSE_DIRT);
        for (int x = x0; x <= x1; x += 2) for (int z = z0; z <= z1; z += 3) set(sl, x, g, z, Blocks.GRAVEL);
        // valla perimetral con puerta abierta al sur
        for (int x = x0; x <= x1; x++) {
            if (x < x0 + 7 || x > x0 + 9) sl.setBlock(new BlockPos(x, g + 1, z1), Blocks.OAK_FENCE.defaultBlockState(), 3);
            sl.setBlock(new BlockPos(x, g + 1, z0), Blocks.OAK_FENCE.defaultBlockState(), 3);
        }
        for (int z = z0; z <= z1; z++) {
            sl.setBlock(new BlockPos(x0, g + 1, z), Blocks.OAK_FENCE.defaultBlockState(), 3);
            sl.setBlock(new BlockPos(x1, g + 1, z), Blocks.OAK_FENCE.defaultBlockState(), 3);
        }
        // caseta del capataz
        fill(sl, x0 + 2, g + 1, z0 + 2, x0 + 6, g + 3, z0 + 6, Blocks.OAK_PLANKS);
        fill(sl, x0 + 3, g + 1, z0 + 3, x0 + 5, g + 3, z0 + 5, Blocks.AIR);
        fill(sl, x0 + 2, g + 4, z0 + 2, x0 + 6, g + 4, z0 + 6, Blocks.SPRUCE_PLANKS);
        fill(sl, x0 + 4, g + 1, z0 + 6, x0 + 4, g + 2, z0 + 6, Blocks.AIR);
        set(sl, x0 + 3, g + 2, z0 + 2, Blocks.GLASS);
        set(sl, x0 + 5, g + 2, z0 + 2, Blocks.GLASS);
        set(sl, x0 + 2, g + 2, z0 + 4, Blocks.GLASS);
        set(sl, x0 + 4, g + 3, z0 + 4, Blocks.LANTERN);
        set(sl, x0 + 3, g + 1, z0 + 3, Blocks.CRAFTING_TABLE);
        set(sl, x0 + 5, g + 1, z0 + 3, Blocks.BARREL);
        // obra a medio hacer
        int bx = x0 + 9, bz = z0 + 2;
        for (int[] c : new int[][]{{0, 0}, {6, 0}, {0, 6}, {6, 6}}) {
            fill(sl, bx + c[0], g + 1, bz + c[1], bx + c[0], g + 5, bz + c[1], Blocks.BRICKS);
        }
        fill(sl, bx, g, bz, bx + 6, g, bz + 6, Blocks.STONE_BRICKS);
        fill(sl, bx, g + 1, bz, bx + 6, g + 2, bz, Blocks.BRICKS);
        fill(sl, bx, g + 1, bz, bx, g + 2, bz + 6, Blocks.BRICKS);
        fill(sl, bx + 6, g + 1, bz + 3, bx + 6, g + 1, bz + 6, Blocks.BRICKS);
        fill(sl, bx + 1, g + 6, bz, bx + 5, g + 6, bz, Blocks.OAK_SLAB);
        for (int y = g + 1; y <= g + 7; y++) {
            set(sl, bx + 7, y, bz + 1, Blocks.SCAFFOLDING);
            set(sl, bx + 7, y, bz + 5, Blocks.SCAFFOLDING);
        }
        fill(sl, bx + 7, g + 7, bz + 1, bx + 7, g + 7, bz + 5, Blocks.SCAFFOLDING);
        // materiales
        chest(sl, x0 + 1, g + 1, z0 + 9, "minecraft:chests/village/village_mason");
        chest(sl, x0 + 2, g + 1, z0 + 9, "minecraft:chests/village/village_toolsmith");
        fill(sl, x0 + 4, g + 1, z0 + 10, x0 + 5, g + 2, z0 + 10, Blocks.OAK_PLANKS);
        set(sl, x0 + 7, g + 1, z0 + 10, Blocks.BARREL);
        set(sl, x0 + 14, g + 1, z0 + 10, Blocks.STONE_BRICKS);
        set(sl, x0 + 14, g + 1, z0 + 11, Blocks.STONE_BRICKS);
        BlockPos home = new BlockPos(x0 + 8, g + 1, z0 + 7);
        npc(sl, 1, x0 + 4.5, g + 1, z0 + 4.5, 0.0f, null, 0);
        npc(sl, 6, x0 + 11.5, g + 1, z0 + 9.5, 0.0f, home, 8);
        npc(sl, 6, x0 + 13.5, g + 1, z0 + 8.5, 0.0f, home, 8);
        npc(sl, 6, x0 + 8.5, g + 1, z0 + 10.5, 0.0f, home, 8);
        Sites.get(sl).add(1, new BlockPos(x0 + 8, g + 1, z1));
    }

    // ------------------------------------------------------------------ casino

    static void casino(ServerLevel sl, int x0, int z0, int g) {
        prepare(sl, x0 - 1, z0 - 1, 15, 15, g);
        int x1 = x0 + 12, z1 = z0 + 12;
        fill(sl, x0, g - 18, z0, x1, g + 5, z1, Blocks.STONE_BRICKS);
        for (int k = 0; k < 4; k++) {
            int fy = g - 6 * k;
            fill(sl, x0 + 1, fy + 1, z0 + 1, x1 - 1, fy + 4, z1 - 1, Blocks.AIR);
            Block carpet = k == 3 ? Blocks.PURPLE_CARPET : Blocks.RED_CARPET;
            fill(sl, x0 + 1, fy + 1, z0 + 1, x1 - 1, fy + 1, z1 - 1, carpet);
            for (int lx : new int[]{4, 8}) for (int lz : new int[]{4, 8}) set(sl, x0 + lx, fy + 4, z0 + lz, Blocks.SEA_LANTERN);
            if (k < 3) {
                fill(sl, x0 + 5, fy + 1, z0 + 5, x0 + 7, fy + 1, z0 + 7, Blocks.RED_CONCRETE);
                set(sl, x0 + 6, fy + 1, z0 + 6, Blocks.GOLD_BLOCK);
            }
        }
        // fachada: esquinas doradas y entrada
        for (int[] c : new int[][]{{0, 0}, {12, 0}, {0, 12}, {12, 12}}) fill(sl, x0 + c[0], g + 1, z0 + c[1], x0 + c[0], g + 4, z0 + c[1], Blocks.GOLD_BLOCK);
        fill(sl, x0 + 5, g + 1, z1, x0 + 7, g + 3, z1, Blocks.AIR);
        set(sl, x0 + 4, g + 3, z1 + 1, Blocks.LANTERN);
        set(sl, x0 + 8, g + 3, z1 + 1, Blocks.LANTERN);
        fill(sl, x0 + 4, g + 4, z1, x0 + 8, g + 4, z1, Blocks.GOLD_BLOCK);
        // escalera de mano de arriba a abajo
        BlockState lad = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
        for (int y = g - 17; y <= g + 4; y++) set(sl, x0 + 1, y, z0 + 1, lad);

        // planta 1: cofres sencillos
        int f1 = g - 6;
        chest(sl, x0 + 3, f1 + 1, z0 + 9, "minecraft:chests/village/village_toolsmith");
        chest(sl, x0 + 9, f1 + 1, z0 + 3, "minecraft:chests/village/village_toolsmith");
        chest(sl, x0 + 10, f1 + 1, z0 + 10, "minecraft:chests/village/village_weaponsmith");

        // planta 2: unos pocos ladrones
        int f2 = g - 12;
        BlockPos h2 = new BlockPos(x0 + 6, f2 + 1, z0 + 6);
        npc(sl, 4, x0 + 3.5, f2 + 1, z0 + 8.5, 0.0f, h2, 6);
        npc(sl, 4, x0 + 9.5, f2 + 1, z0 + 4.5, 90.0f, h2, 6);
        npc(sl, 4, x0 + 8.5, f2 + 1, z0 + 9.5, 180.0f, h2, 6);
        chest(sl, x0 + 2, f2 + 1, z0 + 10, "minecraft:chests/simple_dungeon");
        chest(sl, x0 + 10, f2 + 1, z0 + 2, "minecraft:chests/simple_dungeon");

        // planta 3: sala del jefe
        int f3 = g - 18;
        set(sl, x0 + 10, f3 + 1, z0 + 10, Blocks.DARK_OAK_SLAB);
        set(sl, x0 + 9, f3 + 1, z0 + 11, Blocks.GOLD_BLOCK);
        set(sl, x0 + 11, f3 + 1, z0 + 11, Blocks.GOLD_BLOCK);
        BlockPos h3 = new BlockPos(x0 + 9, f3 + 1, z0 + 9);
        npc(sl, 2, x0 + 10.5, f3 + 1.5, z0 + 10.5, 135.0f, null, 0);
        npc(sl, 4, x0 + 8.5, f3 + 1, z0 + 10.5, 135.0f, h3, 4);
        npc(sl, 4, x0 + 10.5, f3 + 1, z0 + 8.5, 135.0f, h3, 4);
        set(sl, x0 + 9, f3 + 1, z0 + 2, Blocks.BARREL);
        npc(sl, 3, x0 + 10.5, f3 + 1, z0 + 2.5, 90.0f, null, 0);
        // celda con rehenes
        for (int x = x0 + 2; x <= x0 + 6; x++) if (x != x0 + 4) {
            set(sl, x, f3 + 1, z0 + 9, Blocks.IRON_BARS);
            set(sl, x, f3 + 2, z0 + 9, Blocks.IRON_BARS);
        }
        for (int z = z0 + 10; z <= z0 + 11; z++) {
            for (int y = f3 + 1; y <= f3 + 2; y++) {
                set(sl, x0 + 2, y, z, Blocks.IRON_BARS);
                set(sl, x0 + 6, y, z, Blocks.IRON_BARS);
            }
        }
        captive(sl, x0 + 3.5, f3 + 1, z0 + 10.5);
        captive(sl, x0 + 5.5, f3 + 1, z0 + 10.5);
        chest(sl, x0 + 11, f3 + 1, z0 + 5, "minecraft:chests/buried_treasure");
        chest(sl, x0 + 11, f3 + 1, z0 + 6, "minecraft:chests/simple_dungeon");
        chest(sl, x0 + 7, f3 + 1, z0 + 11, "minecraft:chests/buried_treasure");
        Sites.get(sl).add(2, new BlockPos(x0 + 6, g + 1, z1 + 1));
    }

    // ------------------------------------------------------------------ colocacion

    static boolean place(ServerLevel sl, int k, int cx, int cz, boolean force) {
        int x0 = cx - W[k] / 2, z0 = cz - D[k] / 2;
        int g = survey(sl, x0 - 1, z0 - 1, W[k] + 2, D[k] + 2);
        if (g < 0) {
            if (!force) return false;
            g = ground(sl, cx, cz);
        }
        switch (k) {
            case 0: comisaria(sl, x0, z0, g); break;
            case 1: obra(sl, x0, z0, g); break;
            default: casino(sl, x0, z0, g); break;
        }
        return true;
    }

    static void tryGen(ServerPlayer sp, ServerLevel sl) {
        Sites st = Sites.get(sl);
        for (int k = 0; k < 3; k++) {
            if (st.near(k, sp.blockPosition(), 320)) continue;
            if (sl.random.nextInt(100) >= 45) continue;
            for (int i = 0; i < 4; i++) {
                double ang = sl.random.nextDouble() * Math.PI * 2.0;
                double d = 70.0 + sl.random.nextDouble() * 60.0;
                int x = sp.getBlockX() + (int) (Math.cos(ang) * d);
                int z = sp.getBlockZ() + (int) (Math.sin(ang) * d);
                if (place(sl, k, x, z, false)) return;
            }
        }
    }

    public static class Ev {
        @SubscribeEvent
        public void sPtick(TickEvent.PlayerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp)) return;
            if (sp.tickCount % 600 != 321) return;
            ServerLevel sl = sp.serverLevel();
            if (sl.dimension() != Level.OVERWORLD) return;
            try {
                tryGen(sp, sl);
            } catch (RuntimeException ex) {
                // se reintenta mas tarde
            }
        }

        @SubscribeEvent
        public void sCmd(RegisterCommandsEvent e) {
            e.getDispatcher().register(Commands.literal("pestructura").requires(s -> s.hasPermission(2))
                    .then(Commands.argument("tipo", StringArgumentType.word()).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        String t = StringArgumentType.getString(c, "tipo");
                        int k = -1;
                        for (int i = 0; i < 3; i++) if (SN[i].equals(t)) k = i;
                        if (k < 0) {
                            c.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Use: comisaria, obra o casino"));
                            return 0;
                        }
                        Direction f = p.getDirection();
                        place(p.serverLevel(), k, p.getBlockX() + f.getStepX() * 14, p.getBlockZ() + f.getStepZ() * 14, true);
                        c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Construccion colocada"), false);
                        return 1;
                    })));
        }
    }
}
