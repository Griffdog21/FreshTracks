package com.freshtracks.net;

import com.freshtracks.FreshTracks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Rider's client to server: the rider crashed. */
public record WipeoutPayload(float severity, String reason) implements CustomPacketPayload {
	public static final Type<WipeoutPayload> TYPE = new Type<>(FreshTracks.id("wipeout"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WipeoutPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.FLOAT, WipeoutPayload::severity,
		ByteBufCodecs.stringUtf8(32), WipeoutPayload::reason,
		WipeoutPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
