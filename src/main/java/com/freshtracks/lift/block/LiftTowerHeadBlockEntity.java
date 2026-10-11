package com.freshtracks.lift.block;

import com.freshtracks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Marker block entity so the head's cross-arm can be rendered at the rope's angle. */
public class LiftTowerHeadBlockEntity extends BlockEntity {
	public LiftTowerHeadBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.LIFT_TOWER_HEAD, pos, state);
	}
}
