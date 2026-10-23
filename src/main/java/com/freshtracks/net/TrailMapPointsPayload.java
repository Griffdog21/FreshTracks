package com.freshtracks.net;

import com.freshtracks.FreshTracks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: where places on the mountain are on a trail map picture, for maps that aren't drawn to scale (a
 * resort's painted panorama). Each point is four floats: world x, world z, and the picture's u, v (0..1 from its top
 * left). The client places anyone on the map from the points nearest them.
 */
public record TrailMapPointsPayload(String mapId, float[] points) implements CustomPacketPayload {
	public static final int MAX_POINTS = 4096;
	public static final Type<TrailMapPointsPayload> TYPE = new Type<>(FreshTracks.id("trail_map_points"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TrailMapPointsPayload> CODEC = StreamCodec.of(
		(buf, p) -> {
			buf.writeUtf(p.mapId, 64);
			buf.writeVarInt(p.points.length / 4);
			for (float f : p.points) {
				buf.writeFloat(f);
			}
		},
		buf -> {
			String id = buf.readUtf(64);
			int n = Math.min(buf.readVarInt(), MAX_POINTS);
			float[] pts = new float[n * 4];
			for (int i = 0; i < pts.length; i++) {
				pts[i] = buf.readFloat();
			}
			return new TrailMapPointsPayload(id, pts);
		});

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
