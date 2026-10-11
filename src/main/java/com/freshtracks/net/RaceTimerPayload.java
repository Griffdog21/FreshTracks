package com.freshtracks.net;

import com.freshtracks.FreshTracks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: your race clock. {@code state} 0 = started, 1 = finished in {@code centis} hundredths of a second
 * ({@code best} = it's a personal best), 2 = cancelled.
 */
public record RaceTimerPayload(int state, int centis, boolean best) implements CustomPacketPayload {
	public static final int START = 0;
	public static final int FINISH = 1;
	public static final int CANCEL = 2;
	public static final Type<RaceTimerPayload> TYPE = new Type<>(FreshTracks.id("race_timer"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RaceTimerPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, RaceTimerPayload::state,
		ByteBufCodecs.VAR_INT, RaceTimerPayload::centis,
		ByteBufCodecs.BOOL, RaceTimerPayload::best,
		RaceTimerPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
