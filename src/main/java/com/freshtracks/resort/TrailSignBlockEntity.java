package com.freshtracks.resort;

import com.freshtracks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Holds a trail sign's run name (the symbol lives in the block state). */
public class TrailSignBlockEntity extends BlockEntity {
	public static final int MAX_NAME = 40;
	private String name = "";

	public TrailSignBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.TRAIL_SIGN, pos, state);
	}

	public String name() {
		return name;
	}

	public void setName(String name) {
		String clean = name.strip();
		this.name = clean.length() > MAX_NAME ? clean.substring(0, MAX_NAME) : clean;
		this.setChanged();
		if (this.level != null && !this.level.isClientSide()) {
			this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("name", name);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.name = input.getStringOr("name", "");
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return this.saveCustomOnly(registries);
	}
}
