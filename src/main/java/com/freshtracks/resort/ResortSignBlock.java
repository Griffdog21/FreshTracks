package com.freshtracks.resort;

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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A resort sign: SLOW ZONE, CLOSED, EXPERTS ONLY, SINGLES. Placed on the ground it stands on a post; placed against
 * a wall, tree or lift tower it hangs flat on it. The words face whoever placed it.
 */
public class ResortSignBlock extends HorizontalDirectionalBlock {
	public static final BooleanProperty WALL = BooleanProperty.create("wall");
	// Shapes for a sign facing north (front toward -Z), rotated for the others.
	private static final Map<Direction, VoxelShape> STANDING = Shapes.rotateHorizontal(
		Shapes.or(Block.box(0, 6, 7, 16, 16, 8.5), Block.box(7, 0, 8.5, 9, 15, 10.5)));
	private static final Map<Direction, VoxelShape> POST = Shapes.rotateHorizontal(Block.box(7, 0, 7, 9, 16, 10.5));
	private static final Map<Direction, VoxelShape> ON_WALL = Shapes.rotateHorizontal(Block.box(0, 3, 14.5, 16, 13, 16));

	public ResortSignBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WALL, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, WALL);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return placement(this.defaultBlockState(), context);
	}

	static BlockState placement(BlockState base, BlockPlaceContext context) {
		Direction face = context.getClickedFace();
		if (face.getAxis().isHorizontal()) {
			return base.setValue(FACING, face).setValue(WALL, true);
		}
		return base.setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(WALL, false);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shape(state);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(WALL) ? ON_WALL.get(state.getValue(FACING)) : POST.get(state.getValue(FACING));
	}

	static VoxelShape shape(BlockState state) {
		return (state.getValue(WALL) ? ON_WALL : STANDING).get(state.getValue(FACING));
	}
}
