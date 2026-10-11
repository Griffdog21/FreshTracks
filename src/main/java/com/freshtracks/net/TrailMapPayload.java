package com.freshtracks.net;

import com.freshtracks.FreshTracks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: one piece of a trail map picture (PNG bytes) and the world area it shows. A server with a map (the
 * Snowmass add-on) sends its map to players as they join, so the boards show it without anything extra on the client.
 * Where the picture sits in the world: its left edge is at x = x1 and its right edge at x = x0, its top at z = z1 and its
 * bottom at z = z0 (so a map drawn looking uphill to the north, like most resort maps, has x1 < x0 and z1 > z0... any way
 * round works).
 */
public record TrailMapPayload(String mapId, int part, int parts, byte[] data, double x0, double z0, double x1, double z1) implements CustomPacketPayload {
	/** Bytes per piece: well under the 1 MiB packet limit. */
	public static final int PART_SIZE = 512 * 1024;
	public static final Type<TrailMapPayload> TYPE = new Type<>(FreshTracks.id("trail_map"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TrailMapPayload> CODEC = StreamCodec.of(
		(buf, p) -> {
			buf.writeUtf(p.mapId, 64);
			buf.writeVarInt(p.part);
			buf.writeVarInt(p.parts);
			buf.writeByteArray(p.data);
			buf.writeDouble(p.x0);
			buf.writeDouble(p.z0);
			buf.writeDouble(p.x1);
			buf.writeDouble(p.z1);
		},
		buf -> new TrailMapPayload(buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(PART_SIZE + 16),
			buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
