package com.freshtracks.rig;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Implemented by Fresh Tracks blocks that ride differently from what their collision shape says. */
public interface RideableSurface {
	/** Height of the ride surface above the block's own y, in blocks (0..1.5). */
	double rideHeight(BlockState state, BlockGetter level, BlockPos pos);

	Surface surface(BlockState state);

	/** Extra velocity this block gives a rig on top of it (conveyors). */
	default Vec3 carry(BlockState state) {
		return Vec3.ZERO;
	}
}
