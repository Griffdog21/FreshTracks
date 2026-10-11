package com.freshtracks.race;

import com.freshtracks.rig.RideableSurface;
import com.freshtracks.rig.Surface;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A start or finish line painted on the snow. Lay a row of them across a run: riding over a start line starts your
 * clock, a finish line stops it. Rides like packed snow and doesn't trip anyone walking.
 */
public class TimingLineBlock extends Block implements RideableSurface {
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);
	private final boolean finish;

	public TimingLineBlock(boolean finish, BlockBehaviour.Properties properties) {
		super(properties);
		this.finish = finish;
	}

	public boolean isFinish() {
		return finish;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	public double rideHeight(BlockState state, BlockGetter level, BlockPos pos) {
		return 1.0 / 16.0;
	}

	@Override
	public Surface surface(BlockState state) {
		return Surface.PACKED;
	}
}
