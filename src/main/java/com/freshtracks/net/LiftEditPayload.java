package com.freshtracks.net;

import com.freshtracks.FreshTracks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the lift panel's Save button. The lift is found by one of its terminals; speed is in blocks
 * per tick, spacing in blocks between carriers.
 */
public record LiftEditPayload(BlockPos terminal, String name, int style, float speed, float spacing, boolean running)
	implements CustomPacketPayload {
	public static final Type<LiftEditPayload> TYPE = new Type<>(FreshTracks.id("lift_edit"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LiftEditPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, LiftEditPayload::terminal,
		ByteBufCodecs.stringUtf8(64), LiftEditPayload::name,
		ByteBufCodecs.VAR_INT, LiftEditPayload::style,
		ByteBufCodecs.FLOAT, LiftEditPayload::speed,
		ByteBufCodecs.FLOAT, LiftEditPayload::spacing,
		ByteBufCodecs.BOOL, LiftEditPayload::running,
		LiftEditPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
