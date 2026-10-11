package com.freshtracks.resort;

import com.freshtracks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Which map a trail map board shows (the picture itself comes from the server, see TrailMapPayload). A board with no
 * map, or one this server didn't send, shows a plain resort map without the "you are here" dot.
 */
public class TrailMapBlockEntity extends BlockEntity {
	private String mapId = "";

	public TrailMapBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.TRAIL_MAP, pos, state);
	}

	public String mapId() {
		return mapId;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("map", mapId);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.mapId = input.getStringOr("map", "");
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
