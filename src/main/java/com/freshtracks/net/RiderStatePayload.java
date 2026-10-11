package com.freshtracks.net;

import com.freshtracks.FreshTracks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Rider's client to server, every tick: how the rider is posed, so other players see the carve and the tricks. */
public record RiderStatePayload(int rigId, float lean, float flip, byte flags, byte grab) implements CustomPacketPayload {
	public static final Type<RiderStatePayload> TYPE = new Type<>(FreshTracks.id("rider_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RiderStatePayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, RiderStatePayload::rigId,
		ByteBufCodecs.FLOAT, RiderStatePayload::lean,
		ByteBufCodecs.FLOAT, RiderStatePayload::flip,
		ByteBufCodecs.BYTE, RiderStatePayload::flags,
		ByteBufCodecs.BYTE, RiderStatePayload::grab,
		RiderStatePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
