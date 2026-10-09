package com.example.policia;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Comisaria importada de un esquema: datos comprimidos y pegado. */
public final class PoliciaSchem {
    private PoliciaSchem() {}

    public static final int W = 31, H = 22, L = 23;
    static final String[] PAL = {
        "grass",
        "gray_concrete",
        "quartz_block",
        "spruce_door[facing=south,half=lower,hinge=right,open=true]",
        "spruce_door[facing=south,half=lower,hinge=left,open=true]",
        "bookshelf",
        "oak_trapdoor[facing=north,half=top,open=true]",
        "oak_door[facing=south,half=lower,hinge=right,open=true]",
        "oak_wall_sign[facing=west]",
        "quartz_stairs[facing=south,half=bottom]",
        "oak_wall_sign[facing=east]",
        "birch_stairs[facing=west,half=top]",
        "blue_wall_banner[facing=west]",
        "birch_stairs[facing=south,half=top]",
        "birch_stairs[facing=east,half=top]",
        "birch_slab[type=top]",
        "oak_door[facing=east,half=lower,hinge=right,open=true]",
        "quartz_stairs[facing=north,half=bottom]",
        "blue_concrete",
        "cobblestone_wall",
        "smooth_stone_slab[type=bottom]",
        "stone_brick_slab[type=top]",
        "oak_door[facing=south,half=lower,hinge=left,open=false]",
        "stone_brick_stairs[facing=south,half=bottom]",
        "blue_wall_banner[facing=south]",
        "spruce_stairs[facing=north,half=bottom]",
        "end_rod[facing=up]",
        "birch_stairs[facing=north,half=top]",
        "spruce_door[facing=east,half=lower,hinge=right,open=false]",
        "spruce_door[facing=north,half=lower,hinge=right,open=true]",
        "jungle_leaves[persistent=true]",
        "grass_block",
        "spruce_door[facing=south,half=upper,hinge=right,open=true]",
        "spruce_door[facing=south,half=upper,hinge=left,open=true]",
        "black_stained_glass",
        "oak_door[facing=south,half=upper,hinge=right,open=true]",
        "black_carpet",
        "oak_door[facing=east,half=upper,hinge=right,open=true]",
        "stone_brick_stairs[facing=east,half=bottom]",
        "oak_door[facing=south,half=upper,hinge=left,open=false]",
        "end_rod[facing=down]",
        "spruce_door[facing=east,half=upper,hinge=right,open=false]",
        "spruce_door[facing=north,half=upper,hinge=right,open=true]",
        "birch_slab[type=bottom]",
        "yellow_wall_banner[facing=east]",
        "oak_wall_sign[facing=north]",
        "stone_brick_stairs[facing=north,half=bottom]",
        "yellow_wall_banner[facing=south]",
        "sea_lantern",
        "stone_brick_stairs[facing=west,half=bottom]",
        "black_stained_glass_pane",
        "spruce_planks",
        "iron_bars",
        "yellow_wall_banner[facing=west]",
        "ladder[facing=east]",
        "quartz_stairs[facing=east,half=top]",
        "quartz_stairs[facing=south,half=top]",
        "white_carpet",
        "quartz_stairs[facing=west,half=top]",
        "oak_stairs[facing=north,half=bottom]",
        "spruce_door[facing=east,half=lower,hinge=right,open=true]",
        "ladder[facing=west]",
        "oak_door[facing=east,half=lower,hinge=left,open=true]",
        "birch_stairs[facing=south,half=bottom]",
        "oak_stairs[facing=east,half=bottom]",
        "oak_wall_sign[facing=south]",
        "birch_stairs[facing=north,half=bottom]",
        "spruce_door[facing=east,half=upper,hinge=right,open=true]",
        "oak_door[facing=east,half=upper,hinge=left,open=true]",
        "yellow_wall_banner[facing=north]",
        "smooth_stone_slab[type=top]",
        "smooth_stone",
        "polished_andesite",
        "spruce_door[facing=east,half=lower,hinge=left,open=true]",
        "rail[shape=south_east]",
        "rail[shape=south_west]",
        "rail[shape=north_east]",
        "rail[shape=north_west]",
        "redstone_torch",
        "spruce_door[facing=east,half=upper,hinge=left,open=true]",
        "red_carpet"
    };
    static final String DATA = "A 37B 11B2 8B 9C2DEC17 10CF 6G F3CFHIJKLC 10CF 5MN 3FCFHOP2LC 10CF 5MPIJK Q 2IRKLC 5S 4CT4 2MP 4C 6C 5U 3BC 2VT 2MOP4C3HWC3B 4U 4C TXT 3Y5CN 3F2C 5U 4C 12CPIJK FC 5U 4CT3 9CP 5C 2U4 4C6Z2a C aCbP2L 2C 5U 4C9cCdC9 5U 3B 7B 5B 7B 4U 30U 30U 30SU13 3U5SU5 3Ue13 3e11 3Uef11e 3ef3e2U5 3Ue13 3e4U2 8U14 3U4 76B 11B2 8B 9C2ghC4i2C5i2C4 10CF 9F2CFj 3kC 10CF 10kCFjk 2kC 10C 12l 6C 5S 4C 3T 8C 6C 9BCVm 5k 3kC3jnC3B 9C T 10Ck 4FC 10C 12C 6i 10C 12C 6C 10C3 2C 2o C oCk 2k 2i 10C3i2C4pCqC9 9B 7B 5B 7B 97S 21S 198B 2U2 7B2 8B 9C8i2C5i2C4 10Ca 10FCr 5i 10C 12Cr 5i 10C 11IC 6i 5S 4C 3T 8Cs t2 2C 9BC 12C8B 9CuT 10C v 2v C 10CV 11C 6i 10C 12C 6C 10C3 2C9 6i 10C3i2C10i2Ci2C 9B 7B 5B 7B 97S 21S 198B 11B2 8B 9C21 10Co 10FC 6C 10C 12C 6C 10C 12C 6C 5S 4C 3T 8C 6C 9BC 7w2 3wC7B 9C T 10C 6C 10C xV 9C 6C 10C 12C 6C 10C6F7C 6C 10C21 9B 7B 5B 7B 97S 21S 198B23 8BC21B 8BC21B 8BC21B 8BC21B 4S 3BC21B 8BC 2VC17B 8BC TXC17B 8BC 3C12w2C3B 8BC16w2C3B 8BC21B 8B 2C 13CSC3B 8B23 97S 21S 198y23 8y 21y 8y 21y 8y 21y 8y 21y 4S11u2S10 3y 4Sz5Vm z13S! 2y 3#Sz5 T z13S$ 2y 3MSz5 3z13S! 2y 3MSz21S 3y 3MSz21S 3y 3MSz21S 3y 3MSz21Sy4 3MSz21S 7#Sz21S 8Sz21S 8S23 19vY6v 322Si2Si2Si2S2 2S10 8iP2y 5T 3y%&FS2(3S! 7iP y T T T 3y )FS4(S$ 7iP y T 3T 3y*K 2+,S(S! 7Sy y T5 3y 3S6 8i 12y y2S 3aS 8i 16-I.K i 8Sy y3 y3 y3 y2Sb 3i 8i t&)yt&)yt&)yt&)SOP2 S 8i / )y/ )y/ )y/ )SN 3i 8iF: )y: )y: )y: )SF;K i 8Si4Si3Si3Si3Si4S 349Si2Si2Si2S2 2S10 8ik y 9y 2FS 4S! 7i 2y 3T 5y 2aS4 S$ 7ik y 9y 4<,S S! 7Sy y 9y 3S6 8i 12y y2S 3oS 8i 16= 4i 8Sy y3 y3 y3 y2S 4i 8i 3ky 2ky 2ky 2kSk k S 8i 4y 3y 3y 3Sk 3i 8iF 2ky 2ky 2ky 2kSF 3i 8Si4Si3Si3Si3Si4S 349Si2Si2Si2S2 2S10 8i 2y 3> 5y 2FS2 3S! 7i 2y 2#Ts 4y 2oS4 S$ 7i 2y 3v 5y 3wS,SwS! 7Sy3 9y 3S6 8i 12y4Sv 2vS 8i t 13IS 4i 8Sy16S 4i 8i 4y 3y 3y 3S 4S 8i 4y 3y 3y 3S 4i 8i 4y 3y 3y 3SF 3i 8Si4Si3Si3Si3Si4S 317?13 18?S23 7?S?2@?9@?2@2S?3S! 6?S?w@?3@?3w?@?3@S3?S$ 6?S?w@?7w?@?3@S,S?S! 6?S@3?9@?3@S5 7?S?w?5w?4@w@3?4S? 6?S?w?5w?5w?2@?4S? 6?S@U5@11?4S? 6?S?4@?3@?3@?3@?4S? 6?S?w2?@?w?@?w?@?w?@?4S? 6?S?4@?3@?3@?3@?4S? 6?S23? 6?25 253U14 17U 30U U23 6U U 9@2 6U5 6U U U 7@2 6[3U2 6U U @6 3U 6],[U2 6U U @6U 9[3U2 6U U @6U 5U 7U U 4U U @6U 5U 7U U 4U U @6U 13U U 4U U @6U 5@2 6U U 4U U @6 3U 2@2 3T 2U U 4U U U 19U U 4U U23 U 4U 25U 5U25 327^_ 29{| 6[3 12} 4} 10~,[ 13'A 2'A 11[3 13'A 2'A 27'A4 27'A 2'A 27'A 2'A 7^_ 17} 4} 6{| 3T 462?5 26?U3? 26?U3? 26?U3? 26?5 121T 712T 712T 711T2 712T 30T 681T 712T2 711} 1651";
    static final String[] BAN = {
        "2,5,8,ms:11;flo:4;ts:11;bs:11;mr:4",
        "2,5,9,ts:0;rs:0;hhb:11;ms:0;ls:0;bo:11",
        "2,5,10,ts:0;bs:0;mr:11;rs:0;ls:0;bo:11",
        "9,8,8,ms:11;flo:4;ts:11;bs:11;mr:4",
        "10,8,7,ms:11;flo:4;ts:11;bs:11;mr:4",
        "10,8,9,ms:11;flo:4;ts:11;bs:11;mr:4",
        "11,8,8,ms:11;flo:4;ts:11;bs:11;mr:4",
        "22,2,6,ms:11;flo:4;ts:11;bs:11;mr:4",
        "23,2,8,ms:11;flo:4;ts:11;bs:11;mr:4",
        "26,2,8,ms:11;flo:4;ts:11;bs:11;mr:4",
        "2,5,11,ls:0;bs:0;bo:11",
        "2,5,12,bs:0;cs:0;ts:0;bo:11",
        "2,5,13,bs:0;ts:0;ls:0;bo:11",
        "2,5,14,ls:0;bs:0;ms:0;ts:0;bo:11",
        "2,5,15,ms:11;flo:4;ts:11;bs:11;mr:4",
        "14,5,18,ms:11;flo:4;ts:11;bs:11;mr:4",
        "15,5,18,ts:0;rs:0;hhb:11;ms:0;ls:0;bo:11",
        "16,5,18,ts:0;bs:0;mr:11;rs:0;ls:0;bo:11",
        "17,5,18,ls:0;bs:0;bo:11",
        "18,5,18,bs:0;cs:0;ts:0;bo:11",
        "19,5,18,bs:0;ts:0;ls:0;bo:11",
        "20,5,18,ls:0;bs:0;ms:0;ts:0;bo:11",
        "21,8,11,ms:11;flo:4;ts:11;bs:11;mr:4",
        "21,5,18,ms:11;flo:4;ts:11;bs:11;mr:4",
        "24,8,11,ms:11;flo:4;ts:11;bs:11;mr:4"
    };

    public static void paste(ServerLevel sl, int ox, int oy, int oz) {
        BlockState[] st = new BlockState[PAL.length];
        for (int i = 0; i < PAL.length; i++) {
            try {
                st[i] = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), PAL[i], false).blockState();
            } catch (Exception ex) {
                st[i] = Blocks.AIR.defaultBlockState();
            }
        }
        String alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz!#$%&()*+,-./:;<=>?@[]^_{|}~";
        int pos = 0, n = DATA.length(), cell = 0;
        List<BlockPos> fix = new ArrayList<>();
        while (pos < n) {
            char c = DATA.charAt(pos++);
            int ex = 0;
            if (c == '\'') {
                c = DATA.charAt(pos++);
                ex = alpha.length();
            }
            int cnt = 0;
            while (pos < n && Character.isDigit(DATA.charAt(pos))) cnt = cnt * 10 + (DATA.charAt(pos++) - '0');
            if (cnt == 0) cnt = 1;
            BlockState s = c == ' ' ? null : st[alpha.indexOf(c) + ex];
            for (int k = 0; k < cnt; k++, cell++) {
                if (s == null) continue;
                int x = cell % W, z = (cell / W) % L, y = cell / (W * L);
                BlockPos p = new BlockPos(ox + x, oy + y, oz + z);
                sl.setBlock(p, s, 2);
                Block bl = s.getBlock();
                if (bl instanceof StairBlock || bl instanceof IronBarsBlock || bl instanceof WallBlock) fix.add(p);
            }
        }
        for (BlockPos p : fix) {
            BlockState o = sl.getBlockState(p);
            sl.setBlock(p, Block.updateFromNeighbourShapes(o, sl, p), 2);
        }
        for (String b : BAN) {
            try {
                String[] a = b.split(",", 4);
                BlockPos p = new BlockPos(ox + Integer.parseInt(a[0]), oy + Integer.parseInt(a[1]), oz + Integer.parseInt(a[2]));
                BlockEntity be = sl.getBlockEntity(p);
                if (be == null) continue;
                CompoundTag t = be.saveWithoutMetadata();
                ListTag lt = new ListTag();
                for (String q : a[3].split(";")) {
                    String[] kv = q.split(":");
                    CompoundTag e = new CompoundTag();
                    e.putString("Pattern", kv[0]);
                    e.putInt("Color", Integer.parseInt(kv[1]));
                    lt.add(e);
                }
                t.put("Patterns", lt);
                be.load(t);
                be.setChanged();
            } catch (Exception ex) {
                // decoracion opcional
            }
        }
    }
}
