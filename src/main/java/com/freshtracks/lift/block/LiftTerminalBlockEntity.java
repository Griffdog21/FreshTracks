package com.freshtracks.lift.block;

import com.freshtracks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The lift itself lives in the dimension's lift data; the renderer finds it by this terminal's position. */
public class LiftTerminalBlockEntity extends BlockEntity {
	public LiftTerminalBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.LIFT_TERMINAL, pos, state);
	}
}
