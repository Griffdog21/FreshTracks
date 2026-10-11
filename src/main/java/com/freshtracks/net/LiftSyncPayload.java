package com.freshtracks.net;

import com.freshtracks.FreshTracks;
import com.freshtracks.lift.LiftLine;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: every lift in the player's dimension (sent on join, dimension change and edits). */
public record LiftSyncPayload(List<Entry> entries) implements CustomPacketPayload {
	public record Entry(int netId, LiftLine line, double phase, float speed) {
	}

	public static final Type<LiftSyncPayload> TYPE = new Type<>(FreshTracks.id("lift_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LiftSyncPayload> CODEC = StreamCodec.of(
		(buf, p) -> {
			buf.writeVarInt(p.entries.size());
			for (Entry e : p.entries) {
				buf.writeVarInt(e.netId);
				LiftLine.STREAM_CODEC.encode(buf, e.line);
				buf.writeDouble(e.phase);
				buf.writeFloat(e.speed);
			}
		},
		buf -> {
			int n = Math.min(buf.readVarInt(), 4096);
			List<Entry> list = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				int id = buf.readVarInt();
				LiftLine line = LiftLine.STREAM_CODEC.decode(buf);
				list.add(new Entry(id, line, buf.readDouble(), buf.readFloat()));
			}
			return new LiftSyncPayload(list);
		});

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
