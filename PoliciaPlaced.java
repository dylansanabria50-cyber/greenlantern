package com.example.policia;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.Set;

/** Recuerda los bloques que colocan los jugadores, para que el tractor y los ayudantes no los rompan. */
public class PoliciaPlaced extends SavedData {
    final Set<Long> set = new HashSet<>();

    static PoliciaPlaced get(Level l) {
        if (!(l instanceof ServerLevel sl)) return null;
        return sl.getDataStorage().computeIfAbsent(PoliciaPlaced::load, PoliciaPlaced::new, "policia_placed");
    }

    static PoliciaPlaced load(CompoundTag t) {
        PoliciaPlaced p = new PoliciaPlaced();
        for (long l : t.getLongArray("p")) p.set.add(l);
        return p;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        long[] a = new long[set.size()];
        int i = 0;
        for (long l : set) a[i++] = l;
        t.putLongArray("p", a);
        return t;
    }

    static boolean has(Level l, BlockPos bp) {
        PoliciaPlaced p = get(l);
        return p != null && p.set.contains(bp.asLong());
    }

    /** Bloques naturales del mundo (el resto se considera construccion). */
    static boolean natural(BlockState st) {
        if (st.is(BlockTags.BASE_STONE_OVERWORLD) || st.is(BlockTags.BASE_STONE_NETHER) || st.is(BlockTags.DIRT)
                || st.is(BlockTags.SAND) || st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS) || st.is(BlockTags.SNOW)
                || st.is(BlockTags.ICE) || st.is(Tags.Blocks.ORES) || st.is(BlockTags.FLOWERS) || st.is(BlockTags.SAPLINGS)
                || st.is(BlockTags.REPLACEABLE_PLANTS)) return true;
        Block b = st.getBlock();
        return b == Blocks.GRAVEL || b == Blocks.CLAY || b == Blocks.MUD || b == Blocks.MAGMA_BLOCK || b == Blocks.SOUL_SAND
                || b == Blocks.SOUL_SOIL || b == Blocks.END_STONE || b == Blocks.DRIPSTONE_BLOCK || b == Blocks.POINTED_DRIPSTONE
                || b == Blocks.CALCITE || b == Blocks.SANDSTONE || b == Blocks.RED_SANDSTONE || b == Blocks.CACTUS
                || b == Blocks.SUGAR_CANE || b == Blocks.VINE || b == Blocks.PACKED_ICE || b == Blocks.BLUE_ICE
                || b == Blocks.MOSS_BLOCK || b == Blocks.MOSS_CARPET || b == Blocks.GLOW_LICHEN;
    }

    public static class Ev {
        @SubscribeEvent
        public void place(BlockEvent.EntityPlaceEvent e) {
            if (!(e.getEntity() instanceof Player) || !(e.getLevel() instanceof ServerLevel sl)) return;
            PoliciaPlaced p = get(sl);
            if (p == null) return;
            BlockPos bp = e.getPos();
            p.set.add(bp.asLong());
            BlockState st = e.getPlacedBlock();
            if (st.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)) p.set.add(bp.above().asLong());
            if (st.getBlock() instanceof BedBlock && st.hasProperty(BedBlock.FACING)) {
                p.set.add(bp.relative(st.getValue(BedBlock.FACING)).asLong());
            }
            p.setDirty();
        }

        @SubscribeEvent
        public void brk(BlockEvent.BreakEvent e) {
            if (!(e.getLevel() instanceof ServerLevel sl)) return;
            PoliciaPlaced p = get(sl);
            if (p != null && p.set.remove(e.getPos().asLong())) p.setDirty();
        }
    }
}
