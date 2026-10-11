package com.freshtracks.net;

import com.freshtracks.FreshTracks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the trail sign editor's Done button (run name and symbol). */
public record TrailSignPayload(BlockPos pos, String name, int symbol) implements CustomPacketPayload {
	public static final Type<TrailSignPayload> TYPE = new Type<>(FreshTracks.id("trail_sign"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TrailSignPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, TrailSignPayload::pos,
		ByteBufCodecs.stringUtf8(64), TrailSignPayload::name,
		ByteBufCodecs.VAR_INT, TrailSignPayload::symbol,
		TrailSignPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
