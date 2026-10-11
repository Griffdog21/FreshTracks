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
 * Freshly groomed snow: corduroy, the ridged lines a snowcat's tiller leaves. Fast and grippy. The lines run along
 * {@link #AXIS}, which is the way the cat drove (down the fall line).
 */
public class GroomedSnowBlock extends SnowLayerBlock implements RideableSurface {
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

	public GroomedSnowBlock(BlockBehaviour.Properties properties) {
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

	@Override
	public double rideHeight(BlockState state, BlockGetter level, BlockPos pos) {
		return state.getValue(LAYERS) / 8.0;
	}

	@Override
	public Surface surface(BlockState state) {
		return Surface.GROOMED;
	}
}
