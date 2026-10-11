package com.freshtracks.client.dev;

import com.freshtracks.client.lift.ClientLifts;
import com.freshtracks.lift.LiftSeatEntity;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Dev bridge only: logs, every rendered frame while riding a lift, where the rider is drawn and where their seat on
 * the drawn chair is. If the two drift apart or the rider's motion jerks between frames, the lift "shudders".
 */
public final class FrameProbe {
	private static final StringBuilder LOG = new StringBuilder();
	private static boolean recording;

	private FrameProbe() {
	}

	static synchronized String handle(Map<String, String> q) {
		switch (q.getOrDefault("action", "get")) {
			case "start" -> {
				LOG.setLength(0);
				LOG.append("nanos,gametime,px,py,pz,sx,sy,sz\n");
				recording = true;
				return "{\"ok\":true}";
			}
			case "stop" -> {
				recording = false;
				return "{\"ok\":true}";
			}
			default -> {
				return "{\"csv\":" + DevBridge.json(LOG.toString()) + "}";
			}
		}
	}

	/** Called once per frame from the lift renderer. */
	public static synchronized void frame(float partial) {
		if (!recording) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !(mc.player.getVehicle() instanceof LiftSeatEntity seat)) {
			return;
		}
		ClientLifts.Entry entry = ClientLifts.byNetId(seat.lineNetId());
		if (entry == null) {
			return;
		}
		Vec3 p = mc.player.getPosition(partial);
		Vec3 s = entry.line.seatPos(entry.phase(partial), seat.chairIndex(), seat.seatIndex());
		LOG.append(String.format(Locale.ROOT, "%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f\n", System.nanoTime(), (double) mc.level.getGameTime() + partial,
			p.x, p.y, p.z, s.x, s.y, s.z));
		if (LOG.length() > 4_000_000) {
			recording = false;
		}
	}
}
