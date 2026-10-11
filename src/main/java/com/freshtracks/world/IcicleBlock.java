package com.freshtracks.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** An icicle hanging from a ledge. Breaks if what it hangs from is gone. */
public class IcicleBlock extends Block {
	public static final BooleanProperty TIP = BooleanProperty.create("tip");
	private static final VoxelShape BODY = Block.column(6, 0, 16);
	private static final VoxelShape POINT = Block.column(3, 6, 16);

	public IcicleBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(TIP, true));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(TIP);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(TIP) ? POINT : BODY;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos above = pos.above();
		BlockState up = level.getBlockState(above);
		return up.getBlock() instanceof IcicleBlock || up.isFaceSturdy(level, above, Direction.DOWN);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (direction == Direction.UP && !canSurvive(state, level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		if (direction == Direction.DOWN) {
			return state.setValue(TIP, !(neighbourState.getBlock() instanceof IcicleBlock));
		}
		return state;
	}
}
