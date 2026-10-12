package com.freshtracks.village;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A resort workstation (ski bench, board press, lift control, first aid kit). Faces the player who places it; villagers take the job. */
public class WorkstationBlock extends HorizontalDirectionalBlock {
	private static final VoxelShape BENCH = Shapes.or(Block.box(0, 12, 0, 16, 16, 16), Block.box(1, 0, 1, 15, 12, 15));
	private static final Map<Direction, VoxelShape> PANEL = Shapes.rotateHorizontal(Block.box(1, 0, 3, 15, 15, 13));

	public WorkstationBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (state.is(com.freshtracks.registry.ModBlocks.LIFT_CONTROL) || state.is(com.freshtracks.registry.ModBlocks.FIRST_AID_KIT)) {
			return PANEL.get(state.getValue(FACING));
		}
		return BENCH;
	}
}
