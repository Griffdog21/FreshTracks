package com.freshtracks.weather;

import com.freshtracks.rig.RideableSurface;
import com.freshtracks.rig.Surface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A mountain service road in winter: the gravel showing through a skin of snow packed down by snowcats and
 * snowmobiles, tyre ruts running along {@link #AXIS}. Layers like snow, so it sits flush with the snow around it, and
 * skis run on it like packed snow.
 */
public class SnowyRoadBlock extends SnowLayerBlock implements RideableSurface {
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

	public SnowyRoadBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.defaultBlockState().setValue(AXIS, Direction.Axis.Z));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(AXIS);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = super.getStateForPlacement(context);
		return state == null ? null : state.trySetValue(AXIS, context.getHorizontalDirection().getAxis());
	}

	/** Packed hard: it doesn't melt under the lamps along the road the way loose snow does. */
	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return false;
	}

	@Override
	public double rideHeight(BlockState state, BlockGetter level, BlockPos pos) {
		return state.getValue(LAYERS) / 8.0;
	}

	@Override
	public Surface surface(BlockState state) {
		return Surface.PACKED;
	}
}
