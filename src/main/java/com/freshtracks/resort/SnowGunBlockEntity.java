package com.freshtracks.resort;

import com.freshtracks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Lets a snow gun tick on the client, where it draws its plume. */
public class SnowGunBlockEntity extends BlockEntity {
	public SnowGunBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SNOW_GUN, pos, state);
	}
}
