package com.example.greenlantern;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Bateria de Voluntad: clic derecho con el anillo para hacer el juramento y recargar la voluntad. */
public class BatteryBlock extends Block {
    private static final VoxelShape FORMA = Shapes.or(
            Block.box(2.75, 0, 2.75, 13.25, 3, 13.25),
            Block.box(3.5, 3, 3.5, 12.5, 11.25, 12.5),
            Block.box(4.5, 11.25, 4.5, 11.5, 16, 11.5));

    public BatteryBlock(Properties props) { super(props); }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) { return FORMA; }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) Oath.start(sp, pos);
        return InteractionResult.CONSUME;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
        if (r.nextInt(3) != 0) return;
        level.addParticle(RingPowers.GREEN, pos.getX() + 0.3 + r.nextDouble() * 0.4, pos.getY() + 0.2 + r.nextDouble() * 0.6,
                pos.getZ() + 0.3 + r.nextDouble() * 0.4, 0.0, 0.02, 0.0);
    }
}
