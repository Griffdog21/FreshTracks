package com.freshtracks.net;

import com.freshtracks.FreshTracks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client every couple of seconds: where each lift's rope is, so clients never drift. */
public record LiftPhasePayload(int[] netIds, double[] phases, float[] speeds) implements CustomPacketPayload {
	public static final Type<LiftPhasePayload> TYPE = new Type<>(FreshTracks.id("lift_phase"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LiftPhasePayload> CODEC = StreamCodec.of(
		(buf, p) -> {
			buf.writeVarInt(p.netIds.length);
			for (int i = 0; i < p.netIds.length; i++) {
				buf.writeVarInt(p.netIds[i]);
				buf.writeDouble(p.phases[i]);
				buf.writeFloat(p.speeds[i]);
			}
		},
		buf -> {
			int n = Math.min(buf.readVarInt(), 4096);
			int[] ids = new int[n];
			double[] phases = new double[n];
			float[] speeds = new float[n];
			for (int i = 0; i < n; i++) {
				ids[i] = buf.readVarInt();
				phases[i] = buf.readDouble();
				speeds[i] = buf.readFloat();
			}
			return new LiftPhasePayload(ids, phases, speeds);
		});

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
