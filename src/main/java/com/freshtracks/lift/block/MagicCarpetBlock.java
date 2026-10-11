package com.freshtracks.lift.block;

import com.freshtracks.rig.RideableSurface;
import com.freshtracks.rig.Surface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A magic carpet: the conveyor belt on beginner hills. Anything standing on it, skis and all, gets carried along
 * the way the arrows point. Chain them up a gentle slope.
 */
public class MagicCarpetBlock extends Block implements RideableSurface {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final double SPEED = 0.13;
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);

	public MagicCarpetBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
		// The belt is only 2 pixels thick, so riders stand "inside" its block space: carry anyone standing on it.
		if (entity.getY() > pos.getY() + 0.3 || entity.isPassenger() || entity.isNoGravity()) {
			return;
		}
		Direction facing = state.getValue(FACING);
		Vec3 dir = new Vec3(facing.getStepX(), 0, facing.getStepZ());
		Vec3 motion = entity.getDeltaMovement();
		double along = motion.dot(dir);
		if (along < SPEED) {
			double push = Math.min(0.06, SPEED - along);
			entity.setDeltaMovement(motion.add(dir.scale(push)));
		}
	}

	@Override
	public double rideHeight(BlockState state, BlockGetter level, BlockPos pos) {
		return 2.0 / 16.0;
	}

	@Override
	public Surface surface(BlockState state) {
		return Surface.HARD;
	}

	@Override
	public Vec3 carry(BlockState state) {
		Direction facing = state.getValue(FACING);
		return new Vec3(facing.getStepX() * SPEED, 0, facing.getStepZ() * SPEED);
	}
}
