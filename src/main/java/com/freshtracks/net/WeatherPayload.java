package com.freshtracks.net;

import com.freshtracks.FreshTracks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client once a second: today's mountain weather and the wind. {@code announce} = it just changed. */
public record WeatherPayload(int event, float windX, float windZ, boolean announce) implements CustomPacketPayload {
	public static final Type<WeatherPayload> TYPE = new Type<>(FreshTracks.id("weather"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WeatherPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, WeatherPayload::event,
		ByteBufCodecs.FLOAT, WeatherPayload::windX,
		ByteBufCodecs.FLOAT, WeatherPayload::windZ,
		ByteBufCodecs.BOOL, WeatherPayload::announce,
		WeatherPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
