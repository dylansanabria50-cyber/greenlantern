package com.example.policia;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Campamentos de bandidos (empalizada, tiendas, jefe y botin) y de ladrones (escondite oscuro con carteristas). */
public class PoliciaCamp {
    static final int N = 19;
    /** Posiciones libres (relativas a la hoguera) donde aparecen los habitantes. */
    static final int[][] SPOTS = {{1, -1}, {-1, -1}, {1, 1}, {-1, 1}, {2, -2}, {-2, -2}, {-2, 3}, {0, 4}, {-4, 4}, {1, 6}, {-3, 6}, {0, -3}};

    static void init() {
        MinecraftForge.EVENT_BUS.register(new Ev());
    }

    static int hash(int x, int z) {
        return ((x * 73856093) ^ (z * 19349663)) >>> 3 & 7;
    }

    static void build(ServerLevel sl, int kind, int x0, int z0, int g) {
        PoliciaStruct.prepareH(sl, x0 - 1, z0 - 1, N + 2, N + 2, g, 6);
        int cx = x0 + 9, cz = z0 + 9;
        boolean bandit = kind == 0;
        for (int x = x0; x < x0 + N; x++) {
            for (int z = z0; z < z0 + N; z++) {
                int dx = x - cx, dz = z - cz, d2 = dx * dx + dz * dz;
                if (d2 > 100) continue;
                int h = hash(x, z);
                Block fl = h < 3 ? Blocks.COARSE_DIRT : h < 5 ? Blocks.GRAVEL : h < 7 ? Blocks.PODZOL : Blocks.DIRT_PATH;
                PoliciaStruct.set(sl, x, g, z, fl);
                if (d2 >= 72 && d2 <= 98 && !(dz > 0 && Math.abs(dx) <= 1)) {
                    if (bandit) {
                        int ht = 3 + (h & 1);
                        for (int y = 1; y <= ht; y++) PoliciaStruct.set(sl, x, g + y, z, Blocks.SPRUCE_LOG);
                        if ((x + z) % 5 == 0) PoliciaStruct.set(sl, x, g + ht + 1, z, Blocks.TORCH);
                    } else {
                        PoliciaStruct.set(sl, x, g + 1, z, h % 3 == 0 ? Blocks.BARREL : Blocks.SPRUCE_PLANKS);
                        if ((x + z) % 6 == 0) PoliciaStruct.set(sl, x, g + 2, z, Blocks.LANTERN);
                    }
                }
            }
        }
        PoliciaStruct.set(sl, cx, g + 1, cz, bandit ? Blocks.CAMPFIRE : Blocks.SOUL_CAMPFIRE);
        PoliciaStruct.set(sl, cx - 2, g + 1, cz, Blocks.OAK_LOG);
        PoliciaStruct.set(sl, cx + 2, g + 1, cz, Blocks.OAK_LOG);
        PoliciaStruct.set(sl, cx, g + 1, cz + 2, Blocks.OAK_LOG);
        Block wool = bandit ? Blocks.RED_WOOL : Blocks.BLACK_WOOL;
        Block wool2 = bandit ? Blocks.BROWN_WOOL : Blocks.GRAY_WOOL;
        tent(sl, cx - 7, cz - 3, g, wool, 0, "minecraft:chests/simple_dungeon");
        tent(sl, cx + 3, cz - 3, g, wool, 1, bandit ? "minecraft:chests/village/village_weaponsmith" : "minecraft:chests/village/village_toolsmith");
        tent(sl, cx - 2, cz - 8, g, wool2, 2, bandit ? "minecraft:chests/abandoned_mineshaft" : "minecraft:chests/buried_treasure");
        if (!bandit) PoliciaStruct.chest(sl, cx - 4, g + 1, cz + 5, "minecraft:chests/village/village_plains_house");
        cage(sl, cx + 3, cz + 3, g);
        PoliciaStruct.captive(sl, cx + 4.5, g + 1.0, cz + 4.5);
        BlockPos c = new BlockPos(cx, g + 1, cz);
        PoliciaStruct.Sites.get(sl).add(3 + kind, c);
        spawnAll(sl, kind, c);
    }

    /** Tienda de 5x5: puerta en el lado indicado (0 = +x, 1 = -x, 2 = +z, 3 = -z), cofre, paja, barril y antorcha. */
    static void tent(ServerLevel sl, int xa, int za, int g, Block wool, int door, String table) {
        int xb = xa + 4, zb = za + 4;
        for (int x = xa; x <= xb; x++) {
            for (int z = za; z <= zb; z++) {
                boolean edge = x == xa || x == xb || z == za || z == zb;
                PoliciaStruct.set(sl, x, g, z, Blocks.SPRUCE_PLANKS);
                PoliciaStruct.set(sl, x, g + 1, z, edge ? wool : Blocks.AIR);
                PoliciaStruct.set(sl, x, g + 2, z, edge ? wool : Blocks.AIR);
                PoliciaStruct.set(sl, x, g + 3, z, wool);
            }
        }
        int mx = (xa + xb) / 2, mz = (za + zb) / 2;
        int dx = door == 0 ? xb : door == 1 ? xa : mx;
        int dz = door == 2 ? zb : door == 3 ? za : mz;
        PoliciaStruct.set(sl, dx, g + 1, dz, Blocks.AIR);
        PoliciaStruct.set(sl, dx, g + 2, dz, Blocks.AIR);
        PoliciaStruct.chest(sl, xa + 1, g + 1, za + 1, table);
        PoliciaStruct.set(sl, xb - 1, g + 1, za + 1, Blocks.HAY_BLOCK);
        PoliciaStruct.set(sl, xb - 1, g + 1, zb - 1, Blocks.BARREL);
        PoliciaStruct.set(sl, xa + 1, g + 1, zb - 1, Blocks.TORCH);
    }

    /** Celda de 3x3 de adoquin con barrotes en la puerta (mira a la hoguera); dentro hay un cautivo. */
    static void cage(ServerLevel sl, int xa, int za, int g) {
        for (int x = xa; x <= xa + 2; x++) {
            for (int z = za; z <= za + 2; z++) {
                boolean edge = x == xa || x == xa + 2 || z == za || z == za + 2;
                PoliciaStruct.set(sl, x, g, z, Blocks.COBBLESTONE);
                PoliciaStruct.set(sl, x, g + 1, z, edge ? Blocks.COBBLESTONE : Blocks.AIR);
                PoliciaStruct.set(sl, x, g + 2, z, edge ? Blocks.COBBLESTONE : Blocks.AIR);
                PoliciaStruct.set(sl, x, g + 3, z, Blocks.COBBLESTONE);
            }
        }
        PoliciaStruct.set(sl, xa, g + 1, za + 1, Blocks.IRON_BARS);
        PoliciaStruct.set(sl, xa, g + 2, za + 1, Blocks.IRON_BARS);
    }

    static void spawnAll(ServerLevel sl, int kind, BlockPos c) {
        int[] ks = kind == 0 ? new int[]{1, 1, 1, 1, 2, 2, 3} : new int[]{0, 0, 0, 0, 1, 1, 2, 2};
        for (int i = 0; i < ks.length; i++) {
            put(sl, ks[i], new BlockPos(c.getX() + SPOTS[i][0], c.getY(), c.getZ() + SPOTS[i][1]), c);
        }
        if (kind == 0 && sl.random.nextInt(3) == 0) put(sl, 4, new BlockPos(c.getX(), c.getY(), c.getZ() - 6), c);
    }

    static void put(ServerLevel sl, int k, BlockPos p, BlockPos home) {
        PoliciaMision.Pj e = PoliciaMision.spawn(sl, null, k, p);
        e.life = Integer.MAX_VALUE;
        e.getPersistentData().putBoolean("pol_camp", true);
        e.restrictTo(home, 11);
    }

    /** Si ya no queda nadie vivo del campamento, vuelve a poblarse (como mucho cada 10 minutos). */
    static void restock(ServerLevel sl, int kind, BlockPos c) {
        AABB bb = new AABB(c).inflate(16.0, 8.0, 16.0);
        for (PoliciaMision.Pj e : sl.getEntitiesOfClass(PoliciaMision.Pj.class, bb)) {
            if (e.isAlive() && e.getPersistentData().getBoolean("pol_camp")) return;
        }
        spawnAll(sl, kind, c);
    }

    public static class Ev {
        @SubscribeEvent
        public void cTick(TickEvent.PlayerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp)) return;
            if (sp.tickCount % 200 != 55) return;
            ServerLevel sl = sp.serverLevel();
            if (sl.dimension() != Level.OVERWORLD || sl.getGameTime() % 12000L >= 200L) return;
            try {
                PoliciaStruct.Sites st = PoliciaStruct.Sites.get(sl);
                for (int k = 0; k < 2; k++) {
                    BlockPos s = st.nearest(3 + k, sp.blockPosition());
                    if (s != null && s.distSqr(sp.blockPosition()) < 48.0 * 48.0) restock(sl, k, s);
                }
            } catch (RuntimeException ex) {
                // se reintenta mas tarde
            }
        }
    }
}
