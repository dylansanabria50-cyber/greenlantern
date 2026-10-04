package com.example.greenlantern;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Luz real alrededor del traje: un bloque de luz invisible sigue al jugador y se retira al apagar el traje. */
public final class SuitLight {
    private record Spot(ResourceKey<Level> dim, BlockPos pos) { }

    private static final Map<UUID, Spot> SPOTS = new HashMap<>();
    private static final int LEVEL = 14;

    private SuitLight() { }

    private static boolean canUse(BlockState s) {
        return s.isAir() || s.getFluidState().getType() == Fluids.WATER && s.is(Blocks.WATER);
    }

    public static void update(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos target = BlockPos.containing(p.getX(), p.getY() + 1.0, p.getZ());
        Spot cur = SPOTS.get(p.getUUID());
        if (cur != null && cur.dim().equals(level.dimension()) && cur.pos().equals(target)
                && level.getBlockState(target).is(Blocks.LIGHT)) {
            return;
        }
        BlockPos chosen = null;
        BlockPos[] tries = {target, target.above(), target.below()};
        for (BlockPos t : tries) {
            if (canUse(level.getBlockState(t))) {
                chosen = t;
                break;
            }
        }
        if (chosen == null) {
            if (cur != null && cur.dim().equals(level.dimension()) && level.getBlockState(cur.pos()).is(Blocks.LIGHT)
                    && cur.pos().distSqr(target) <= 9.0) {
                return;
            }
            clear(p);
            return;
        }
        if (cur != null && cur.dim().equals(level.dimension()) && cur.pos().equals(chosen)
                && level.getBlockState(chosen).is(Blocks.LIGHT)) {
            return;
        }
        clear(p);
        boolean water = level.getBlockState(chosen).is(Blocks.WATER);
        BlockState light = Blocks.LIGHT.defaultBlockState()
                .setValue(LightBlock.LEVEL, LEVEL)
                .setValue(LightBlock.WATERLOGGED, water);
        level.setBlock(chosen, light, 3);
        SPOTS.put(p.getUUID(), new Spot(level.dimension(), chosen));
    }

    public static void clear(ServerPlayer p) {
        Spot s = SPOTS.remove(p.getUUID());
        if (s == null) return;
        MinecraftServer server = p.getServer();
        if (server == null) return;
        ServerLevel lv = server.getLevel(s.dim());
        if (lv == null) return;
        BlockState st = lv.getBlockState(s.pos());
        if (st.is(Blocks.LIGHT)) {
            boolean water = st.getValue(LightBlock.WATERLOGGED);
            lv.setBlock(s.pos(), water ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        }
    }
}
