package com.freshtracks.client.dev;

import com.freshtracks.rig.RigEntity;
import com.freshtracks.rig.RigMotion;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;

/** Per-tick log of the rider's physics while a test runs, as CSV (dev bridge only). */
final class Telemetry {
	private static final StringBuilder LOG = new StringBuilder();
	private static boolean recording;

	private Telemetry() {
	}

	static synchronized String handle(Map<String, String> q) {
		switch (q.getOrDefault("action", "get")) {
			case "start" -> {
				LOG.setLength(0);
				LOG.append("tick,x,y,z,speed,kmh,heading,edge,lean,skid,grounded,surface,spin,flip,events,top,sink,yaw_vel\n");
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

	static synchronized void tick(Minecraft mc) {
		if (!recording || mc.player == null || !(mc.player.getVehicle() instanceof RigEntity rig)) {
			return;
		}
		RigMotion m = rig.motion;
		RigMotion.Events e = m.events;
		StringBuilder ev = new StringBuilder();
		if (e.launched) {
			ev.append("launch ");
		}
		if (e.popped) {
			ev.append("pop ");
		}
		if (e.landed) {
			ev.append("land(").append(String.format(Locale.ROOT, "%.2f", e.landingImpact)).append(") ");
		}
		if (e.trick != null) {
			ev.append("trick[").append(e.trick.name()).append(':').append(e.trick.points()).append("] ");
		}
		if (e.crashed) {
			ev.append("CRASH(").append(e.crashReason).append(") ");
		}
		if (e.wallBonk) {
			ev.append("bonk ");
		}
		// the visible top of the snow (or block) the rig is over: y below it means the skis sink in
		double top = com.freshtracks.rig.SurfaceSampler.column(mc.level, net.minecraft.util.Mth.floor(rig.getX()),
			net.minecraft.util.Mth.floor(rig.getZ()), rig.getY()).top();
		double yawVel = m.speed() > 1.0e-4 ? com.freshtracks.rig.RigMotion.yawOf(m.vx, m.vz) : m.heading;
		LOG.append(String.format(Locale.ROOT, "%d,%.3f,%.3f,%.3f,%.4f,%.1f,%.1f,%.3f,%.1f,%.2f,%b,%s,%.1f,%.1f,%s,%.3f,%.3f,%.1f\n",
			mc.level.getGameTime(), rig.getX(), rig.getY(), rig.getZ(), m.speed(), m.speed3() * 72, m.heading, m.edge, m.lean, m.skid,
			m.grounded, m.surface, m.tricks.spin, m.tricks.flip, ev.toString().trim(), top, m.powderSink, yawVel));
		if (LOG.length() > 4_000_000) {
			recording = false;
		}
	}
}
