package com.example.policia;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;

/** Refugio nivel 2 (torre de cuatro pisos): datos comprimidos del esquema. Indice = (y * L + z) * W + x. */
public final class PoliciaRef {
    private PoliciaRef() {}

    public static final int W = 27, H = 19, L = 23;
    static final String[] PAL = {
        "spruce_planks",
        "spruce_slab[type=bottom,waterlogged=false]",
        "stripped_spruce_wood[axis=y]",
        "stripped_oak_wood[axis=y]",
        "spruce_trapdoor[facing=west,half=bottom,open=true,powered=false,waterlogged=false]",
        "spruce_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]",
        "spruce_trapdoor[facing=east,half=bottom,open=true,powered=false,waterlogged=false]",
        "barrel[facing=south,open=false]",
        "dark_oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]",
        "furnace[facing=east,lit=false]",
        "dark_oak_fence[east=true,north=false,south=false,waterlogged=false,west=false]",
        "dark_oak_fence[east=true,north=false,south=false,waterlogged=false,west=true]",
        "dark_oak_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]",
        "chest[facing=north,type=left,waterlogged=false]",
        "chest[facing=north,type=right,waterlogged=false]",
        "spruce_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]",
        "bookshelf",
        "chest[facing=south,type=right,waterlogged=false]",
        "chest[facing=south,type=left,waterlogged=false]",
        "spruce_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]",
        "spruce_trapdoor[facing=east,half=top,open=false,powered=false,waterlogged=false]",
        "spruce_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]",
        "spruce_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]",
        "spruce_door[facing=south,half=lower,hinge=right,open=false,powered=false]",
        "spruce_door[facing=south,half=lower,hinge=left,open=false,powered=false]",
        "glass",
        "potted_fern",
        "lantern[hanging=false,waterlogged=false]",
        "cake[bites=0]",
        "stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=tall]",
        "dark_oak_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]",
        "potted_azure_bluet",
        "spruce_door[facing=south,half=upper,hinge=right,open=false,powered=false]",
        "spruce_door[facing=south,half=upper,hinge=left,open=false,powered=false]",
        "spruce_stairs[facing=north,half=top,shape=straight,waterlogged=false]",
        "spruce_slab[type=top,waterlogged=false]",
        "spruce_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]",
        "potted_poppy",
        "spruce_fence[east=true,north=false,south=false,waterlogged=false,west=true]",
        "spruce_fence[east=false,north=true,south=true,waterlogged=false,west=false]",
        "spruce_door[facing=west,half=lower,hinge=right,open=false,powered=false]",
        "spruce_fence[east=true,north=false,south=true,waterlogged=false,west=false]",
        "ladder[facing=north,waterlogged=false]",
        "spruce_door[facing=north,half=lower,hinge=right,open=false,powered=false]",
        "barrel[facing=up,open=false]",
        "beehive[facing=east,honey_level=0]",
        "spruce_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]",
        "blue_wool",
        "blue_bed[facing=west,occupied=false,part=head]",
        "blue_bed[facing=west,occupied=false,part=foot]",
        "spruce_trapdoor[facing=west,half=top,open=false,powered=false,waterlogged=false]",
        "spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]",
        "spruce_door[facing=west,half=upper,hinge=right,open=false,powered=false]",
        "spruce_door[facing=north,half=upper,hinge=right,open=false,powered=false]",
        "spruce_stairs[facing=east,half=top,shape=straight,waterlogged=false]",
        "spruce_stairs[facing=south,half=top,shape=straight,waterlogged=false]",
        "spruce_trapdoor[facing=south,half=bottom,open=false,powered=false,waterlogged=false]",
        "oak_trapdoor[facing=south,half=bottom,open=false,powered=false,waterlogged=false]",
        "spruce_trapdoor[facing=west,half=bottom,open=false,powered=false,waterlogged=false]",
        "red_bed[facing=south,occupied=false,part=foot]",
        "red_bed[facing=south,occupied=false,part=head]",
        "barrel[facing=east,open=false]",
        "beehive[facing=north,honey_level=0]",
        "red_wool",
        "potted_white_tulip",
        "spruce_stairs[facing=east,half=bottom,shape=outer_right,waterlogged=false]",
        "spruce_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]",
        "spruce_stairs[facing=south,half=bottom,shape=outer_right,waterlogged=false]",
        "grass_block[snowy=false]",
        "spruce_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]",
        "spruce_stairs[facing=north,half=bottom,shape=outer_right,waterlogged=false]",
        "spruce_stairs[facing=north,half=bottom,shape=outer_left,waterlogged=false]",
        "grass",
        "tall_grass[half=lower]",
        "dandelion",
        "oxeye_daisy",
        "azure_bluet",
        "cornflower",
        "poppy",
        "tall_grass[half=upper]"
    };
    static final String DATA = " 91A14 13A14 6A21 6A21 6A21 6A21 6A21 6A5 2A14 6A5 2A14 13A14 13A14 13A14 13A14 13A14 13A14 13A6 2A6 13A6 2A6 175B14 13BC12B 6D8C 3EF2G 3CB 6DA3C4H 8I2CB 6DA3C4J 8KLCB 6DA3C4H 8M2CB 6DA3D2BDC 10CB 6DA3D 2BCNOC 4CNOCB 6DP3D 2DC5 2C5D 13BCQ2C 4CRSCB 13BC 10CB 13BCT 9CB 13DCU 9CB 13BCV 9CB 13BC 7EWGCB 13BC5XYC4DB 13DB4D 2DB5 203C2Z8C2 7D8C 4ab 4C 7DA3C4c 10Z 7DA3C4d 8e2Z 7DA3C4b 10Z 7DA3D2 DC 10C 7DP3D 3CNOC 4CNOC 7D 3D 2DC5 2C5D 14CbfC 4CRSC 15Z 10Z 15CT 9C 14DQU 9C 15CV 9Z 15Z 10C 15CZ2C2ghC2ZCD 14D 4D 2D 208C2Z8C2 7D8C 10C 7DA3C4 11Z 7DA3C4d 10Z 7DA3C4 11Z 7DP3D2 DC 10C 7D 3D 3CNOC 4CNOC 7D 3D 2DC5 2C5D 14C 2C 4CF2C 15Z 10Z 15CT 9C 14DQU 9C 15CV 9Z 15Z 10C 15CZ2C2i2C2ZCD 14D 4D 2D 180j14 13jC12j 6D8C 10Cj 6D 3kAC2 11Cj 6D 3kAC2d 10Cj 6D 3kAC2 11Cj 6D 3D2jDC 10Cj 6D 3D 2jC 2C 4C 2Cj 6D 3D 2DC5 2C5D 13jC 2C 4ClbCj 13jC 10Cj 13jC 10Cj 13DC 10Cj 13jC 10Cj 13jC 10Cj 13jC11Dj 13Dj4Dj2Dj5 175D14 13DC12D 6D8CA10CD 6D 4kA13CD 6D 4kA13CD 6D 4kA13CD 6D 3D4CA10CD 6D 3D 2DCA10CD 6D 3D 2DCA10CD 13DCA10CD 13DCA10CD 13DCA10CD 13DCA10CD 13DCA10CD 13DCA10CD 13DC12D 13D14 175B14 13BC12B 6Dm5D2C 3EF2G 3CB 6n 7C 10CB 6n 7o 10CB 6n 7C 10CB 6n 3pmD2CNONOCqC 3CB 6n 3n 2BC9rC2B 6D 3D 2BC2RSsRSC 3CB 13BC 10CB 13BCtu3 5TCB 13BCvwxu 5yCB 13BCvwxu 5yCB 13BCtu3 5VCB 13BC 4EWG 3CB 13BC12B 13B13D 203C2Z8C2 7Bz 3zBDC 4ba 4C 7z 7C 10Z 15! 10Z 15C 10Z 12zBDCNONOCqC 3C 7z 3z 3C9#C2 7B 3B 3C2RSsRSC 3C 15C 10Z 15Zf 9Z 15Z 9bZ 15Z 10Z 15Zf 9Z 15Z 5b 4Z 15CZ10C 27D 203C2Z8C2 8z 3z DC 10C 7z 7C 10Z 15$ 10Z 15C 10Z 12z DCNONOCqC 3C 7z 3z 3C9%C2 15C2RSsRSC 3C 15C 10Z 15Z 10Z 15Z 10Z 15Z 10Z 15Z 10Z 15Z 10Z 15CZ10C 27D 175j14 13jC12j 7z 3z DC 10Cj 6z 6jC 10Cj 13jC 10Cj 13jC 10Cj 11z DC 4CqC 3Cj 6z 3z 2jC12j 13jC2 2& 2C 3Cj 13jC 10Cj 13jC 10Cj 13jC 10Cj 13jC 10Cj 13jC 10Cj 13jC 10Cj 13jC12j 13j13D 175D14 13DC12D 7D7CA10CD 6D 6DCA10CD 6D 6DCA10CD 6D 6DCA10CD 6D 4D3CA4CqCA3CD 6D5 2DCA5CA4CD 13DCA10CD 13DCA10CD 13DCA10CD 13DCA10CD 13DCA10CD 13DCA10CD 13DCA10CD 13DC12D 13D14 175DB13 13BC12B 7B7CT 3EFG 3CB 6B(6BCH 9CB 6B(6BCH 9CB 6B(6BCV 5NONOCB 6B(4B3C3rC2uC5B 6B5 2BC 5CRSRSCB 13BC 10CB 13BCT 9CB 13BCQ 9CB 13BCQ 2)4 3CB 13BCV 2)*2) 3CB 13BC 3)+2) 3CB 13BC,3-.2-,3CB 13BC12B 13DB13 175D 27CZ11 15Z 5b 4Z 15Za 9Z 15Zb 9Z 15C 6NONOC 15C3#C2 C5 15C 5CRSRSC 15Z 10Z 15Z 10Z 15Z/ 9Z 15Z 10Z 15Z 10Z 15Z 10Z 15Z a b 2b 2fZ 15CZ11 14D 188D 27CZ11 15Z 10Z 15Z 10Z 15Z 10Z 15C 6NONOC 15C3%C2 C5 15C 5CRSRSC 15Z 10Z 15Z 10Z 15Z 10Z 15Z 10Z 15Z 10Z 15Z 10Z 15Z 10Z 15CZ11 14D 188Dj13 13jC12j 13jC 10Cj 13jC 10Cj 13jC 10Cj 13jC 10Cj 13jC6 C5j 13jCF5CF4Cj 13jC 10Cj 13jCF10Cj 13jC 10Cj 13jC 10Cj 13jCW10Cj 13jC 10Cj 13jCW10Cj 13jC12j 13Dj13 175D14 13DA12D 13DA12D 13DA12D 13DA12D 13DA12D 13DA12D 13DA12D 13DA12D 13DA12D 13DA12D 13DA12D 13DA12D 13DA12D 13DA12D 13DA12D 13D14 175B14 13B:;10<B 13Bk=10>B 13Bk=10>B 13Bk=10>B 13Bk=10>B 13Bk=10>B 13Bk=10>B 13Bk=10>B 13Bk=10>B 13Bk=10>B 13Bk=10>B 13Bk=10>B 13Bk=10>B 13Bk=10>B 13B?P10@B 13B14 231[4][]^]2 17[^[3_^[3 18][3^[4 18[2 ]^ 2[2 17[][2^2[2{] 17[]2[][^ [2 17][2]2| ][ 18[3}][2^[2 17[4|[3][ 17[][]3[3^ 17[9| 17_][2]2[4 17][2]^[ [][ 291~ ~ ~2 45~ 29~ 23~ 7~ 18~2 ~ 22~ 2~2 2~ 23~ 30~ 19~ ~3 49~ 2~2 21~ 2~ 4~ 141";

    static final String ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz!#$%&()*+,-./:;<=>?@[]^_{|}~";
    private static BlockState[] ST;
    private static int[] CELLS;

    private static synchronized void load() {
        if (CELLS != null) return;
        BlockState[] st = new BlockState[PAL.length];
        for (int i = 0; i < PAL.length; i++) {
            try {
                st[i] = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), PAL[i], false).blockState();
            } catch (Exception ex) {
                st[i] = Blocks.AIR.defaultBlockState();
            }
        }
        int[] c = new int[W * H * L];
        Arrays.fill(c, -1);
        int pos = 0, n = DATA.length(), cell = 0;
        while (pos < n) {
            char ch = DATA.charAt(pos++);
            int cnt = 0;
            while (pos < n && Character.isDigit(DATA.charAt(pos))) cnt = cnt * 10 + (DATA.charAt(pos++) - '0');
            if (cnt == 0) cnt = 1;
            int idx = ch == ' ' ? -1 : ALPHA.indexOf(ch);
            for (int k = 0; k < cnt && cell < c.length; k++, cell++) c[cell] = idx;
        }
        ST = st;
        CELLS = c;
    }

    public static int[] cells() {
        load();
        return CELLS;
    }

    public static BlockState state(int i) {
        load();
        return ST[i];
    }
}
