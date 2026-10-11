package com.freshtracks.net;

import com.freshtracks.FreshTracks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Rider's client to server: a trick was landed (XP and a shout-out to nearby players). */
public record TrickPayload(String name, int points, int airTicks) implements CustomPacketPayload {
	public static final Type<TrickPayload> TYPE = new Type<>(FreshTracks.id("trick"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TrickPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(96), TrickPayload::name,
		ByteBufCodecs.VAR_INT, TrickPayload::points,
		ByteBufCodecs.VAR_INT, TrickPayload::airTicks,
		TrickPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
