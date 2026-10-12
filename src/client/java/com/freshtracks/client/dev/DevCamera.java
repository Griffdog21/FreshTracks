package com.freshtracks.client.dev;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Development only: smooth camera moves for filming, driven through the dev bridge. The camera is the local player
 * (in spectator mode), moved once a tick; the game's own interpolation between ticks makes it glide at any frame rate.
 *
 * <ul>
 * <li>{@code /dolly?pts=x,y,z,yaw,pitch;...&secs=6}: through the key points on a Catmull-Rom curve, eased in and out.</li>
 * <li>{@code /follow?name=<player>&back=6&up=2&side=3&secs=8}: behind (or beside) another player, always looking at
 * them, trailing a little so the camera moves like an operator's.</li>
 * <li>{@code /orbit?x=&y=&z=&r=&h=&from=&to=&secs=}: round a point, looking at it, from angle {@code from} to {@code to}.</li>
 * <li>{@code /camstop}: let go.</li>
 * </ul>
 */
public final class DevCamera {
	private static List<double[]> keys = List.of();
	private static int ticks;
	private static int tick;
	private static String follow;
	private static double back;
	private static double up;
	private static double side;
	private static Vec3 smooth;
	private static double[] orbit;

	private DevCamera() {
	}

	static String dolly(Map<String, String> q) {
		List<double[]> pts = new ArrayList<>();
		for (String p : q.getOrDefault("pts", "").split(";")) {
			if (p.isBlank()) {
				continue;
			}
			String[] v = p.split(",");
			double[] k = new double[5];
			for (int i = 0; i < 5; i++) {
				k[i] = i < v.length ? Double.parseDouble(v[i]) : 0;
			}
			pts.add(k);
		}
		// unwrap the yaws so the camera turns the short way round
		for (int i = 1; i < pts.size(); i++) {
			pts.get(i)[3] = pts.get(i - 1)[3] + Mth.wrapDegrees(pts.get(i)[3] - pts.get(i - 1)[3]);
		}
		stop();
		keys = pts;
		ticks = Math.max(1, (int) Math.round(Double.parseDouble(q.getOrDefault("secs", "6")) * 20));
		tick = 0;
		return "{\"ok\":true,\"keys\":" + pts.size() + "}";
	}

	static String follow(Map<String, String> q) {
		stop();
		follow = q.get("name");
		back = Double.parseDouble(q.getOrDefault("back", "6"));
		up = Double.parseDouble(q.getOrDefault("up", "2"));
		side = Double.parseDouble(q.getOrDefault("side", "0"));
		ticks = (int) Math.round(Double.parseDouble(q.getOrDefault("secs", "30")) * 20);
		tick = 0;
		smooth = null;
		return "{\"ok\":true}";
	}

	static String orbit(Map<String, String> q) {
		stop();
		orbit = new double[]{Double.parseDouble(q.get("x")), Double.parseDouble(q.get("y")), Double.parseDouble(q.get("z")),
			Double.parseDouble(q.getOrDefault("r", "20")), Double.parseDouble(q.getOrDefault("h", "8")),
			Double.parseDouble(q.getOrDefault("from", "0")), Double.parseDouble(q.getOrDefault("to", "90"))};
		ticks = (int) Math.round(Double.parseDouble(q.getOrDefault("secs", "8")) * 20);
		tick = 0;
		return "{\"ok\":true}";
	}

	static String stop() {
		keys = List.of();
		follow = null;
		orbit = null;
		return "{\"ok\":true}";
	}

	/** Busy filming (true while a move is still running). */
	static boolean busy() {
		return !keys.isEmpty() || follow != null || orbit != null;
	}

	static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || !busy()) {
			return;
		}
		if (tick > ticks) {
			stop();
			return;
		}
		double t = ticks == 0 ? 1 : tick / (double) ticks;
		tick++;
		if (!keys.isEmpty()) {
			double e = t * t * (3 - 2 * t); // ease in and out
			double[] k = sample(e);
			place(player, new Vec3(k[0], k[1], k[2]), (float) k[3], (float) k[4]);
		} else if (orbit != null) {
			double e = t * t * (3 - 2 * t);
			double a = Math.toRadians(Mth.lerp(e, orbit[5], orbit[6]));
			Vec3 centre = new Vec3(orbit[0], orbit[1], orbit[2]);
			Vec3 at = centre.add(Math.sin(a) * orbit[3], orbit[4], -Math.cos(a) * orbit[3]);
			lookPlace(player, at, centre);
		} else if (follow != null) {
			Entity target = null;
			for (Player p : mc.level.players()) {
				if (p.getName().getString().equals(follow)) {
					target = p;
				}
			}
			if (target == null) {
				return;
			}
			Entity ride = target.getVehicle() != null ? target.getVehicle() : target;
			Vec3 pos = ride.position();
			Vec3 vel = ride.getDeltaMovement();
			Vec3 dir = new Vec3(vel.x, 0, vel.z);
			if (dir.lengthSqr() < 1.0e-4) {
				float yaw = ride.getYRot();
				dir = new Vec3(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
			}
			dir = dir.normalize();
			Vec3 right = new Vec3(-dir.z, 0, dir.x);
			Vec3 want = pos.subtract(dir.scale(back)).add(right.scale(side)).add(0, up, 0);
			smooth = smooth == null ? want : smooth.lerp(want, 0.18);
			lookPlace(player, smooth, pos.add(0, 1.0, 0));
		}
	}

	private static void lookPlace(LocalPlayer player, Vec3 at, Vec3 target) {
		Vec3 d = target.subtract(at);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
		place(player, at, player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot()), pitch);
	}

	private static void place(LocalPlayer player, Vec3 at, float yaw, float pitch) {
		player.setDeltaMovement(Vec3.ZERO);
		player.setPos(at.x, at.y, at.z);
		player.setYRot(yaw);
		player.setXRot(pitch);
		player.setYHeadRot(yaw);
	}

	/** Catmull-Rom through the key points, u in 0..1 over the whole path. */
	private static double[] sample(double u) {
		int n = keys.size();
		if (n == 1) {
			return keys.getFirst();
		}
		double f = u * (n - 1);
		int i = Math.min(n - 2, (int) Math.floor(f));
		double s = f - i;
		double[] p0 = keys.get(Math.max(0, i - 1));
		double[] p1 = keys.get(i);
		double[] p2 = keys.get(i + 1);
		double[] p3 = keys.get(Math.min(n - 1, i + 2));
		double[] out = new double[5];
		for (int c = 0; c < 5; c++) {
			out[c] = 0.5 * (2 * p1[c] + (-p0[c] + p2[c]) * s + (2 * p0[c] - 5 * p1[c] + 4 * p2[c] - p3[c]) * s * s
				+ (-p0[c] + 3 * p1[c] - 3 * p2[c] + p3[c]) * s * s * s);
		}
		return out;
	}
}
