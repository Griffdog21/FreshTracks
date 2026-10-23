package com.freshtracks.client.render;

import com.freshtracks.FreshTracks;
import com.freshtracks.net.TrailMapPayload;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** Trail map pictures the server sent (in pieces), turned into textures for the boards. */
public final class TrailMaps {
	/** A received map: its texture and the world area it shows (picture bottom-left = (x0, z0), top-right = (x1, z1)). */
	public record Map2(String id, Identifier texture, double x0, double z0, double x1, double z1, float aspectRatio) {
		/** Drawn to scale over that area (a panorama isn't: it places people from its points instead). */
		boolean linear() {
			return Double.isFinite(x0) && Double.isFinite(x1) && Double.isFinite(z0) && Double.isFinite(z1) && x0 != x1 && z0 != z1;
		}
	}

	private static final Map<String, Map2> MAPS = new HashMap<>();
	private static final Map<String, byte[][]> PENDING = new HashMap<>();
	/** Places on the map picture: world x, z, then u, v, four floats a point. */
	private static final Map<String, float[]> POINTS = new HashMap<>();

	private TrailMaps() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(TrailMapPayload.TYPE, (payload, context) -> receive(payload));
		ClientPlayNetworking.registerGlobalReceiver(com.freshtracks.net.TrailMapPointsPayload.TYPE, (payload, context) -> POINTS.put(payload.mapId(), payload.points()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(TrailMaps::clear));
	}

	public static @Nullable Map2 get(String mapId) {
		return mapId.isEmpty() ? null : MAPS.get(mapId);
	}

	/** This mountain's map (the server sends one), for the pocket map. */
	public static @Nullable Map2 any() {
		return MAPS.isEmpty() ? null : MAPS.values().iterator().next();
	}

	/**
	 * Where a world spot is on the map picture (u, v from its top left), or null if the map can't tell or it's off the
	 * picture. A map drawn to scale is a straight projection; a painted panorama is placed from its nearest known points
	 * by a weighted local fit (the panorama stretches differently in each part of the mountain).
	 */
	public static float @Nullable [] project(Map2 map, double x, double z) {
		float u;
		float v;
		if (map.linear()) {
			u = (float) ((map.x1() - x) / (map.x1() - map.x0()));
			v = (float) ((map.z1() - z) / (map.z1() - map.z0()));
		} else {
			float[] pts = POINTS.get(map.id());
			if (pts == null || pts.length < 12) {
				return null;
			}
			double[] fit = fit(pts, x, z);
			u = (float) fit[0];
			v = (float) fit[1];
		}
		return u < -0.02f || u > 1.02f || v < -0.02f || v > 1.02f ? null : new float[]{u, v};
	}

	/** u and v at (x, z) from an affine fit to the nearest points, the closest counting most. */
	private static double[] fit(float[] pts, double x, double z) {
		int n = pts.length / 4;
		int k = Math.min(n, 10);
		int[] best = new int[k];
		double[] bestD = new double[k];
		java.util.Arrays.fill(bestD, Double.MAX_VALUE);
		for (int i = 0; i < n; i++) {
			double d = (pts[i * 4] - x) * (pts[i * 4] - x) + (pts[i * 4 + 1] - z) * (pts[i * 4 + 1] - z);
			for (int j = 0; j < k; j++) {
				if (d < bestD[j]) {
					System.arraycopy(bestD, j, bestD, j + 1, k - j - 1);
					System.arraycopy(best, j, best, j + 1, k - j - 1);
					bestD[j] = d;
					best[j] = i;
					break;
				}
			}
		}
		// weighted least squares for u = a + b dx + c dz (the same for v), centred on the spot
		double[][] m = new double[3][3];
		double[] bu = new double[3];
		double[] bv = new double[3];
		double scale = Math.sqrt(bestD[k - 1]) + 1;
		for (int j = 0; j < k; j++) {
			int i = best[j];
			double dx = (pts[i * 4] - x) / scale;
			double dz = (pts[i * 4 + 1] - z) / scale;
			double w = 1.0 / (bestD[j] / (scale * scale) + 0.02);
			double[] r = {1, dx, dz};
			for (int a = 0; a < 3; a++) {
				for (int b = 0; b < 3; b++) {
					m[a][b] += w * r[a] * r[b];
				}
				bu[a] += w * r[a] * pts[i * 4 + 2];
				bv[a] += w * r[a] * pts[i * 4 + 3];
			}
		}
		double[] su = solve(m, bu);
		double[] sv = solve(m, bv);
		if (su == null || sv == null) {
			// the nearest points all on one line: their weighted average will do
			double wu = 0;
			double wv = 0;
			double ws = 0;
			for (int j = 0; j < k; j++) {
				double w = 1.0 / (bestD[j] + 1);
				wu += w * pts[best[j] * 4 + 2];
				wv += w * pts[best[j] * 4 + 3];
				ws += w;
			}
			return new double[]{wu / ws, wv / ws};
		}
		return new double[]{su[0], sv[0]};
	}

	/** Solves a 3x3 system by Cramer's rule; null when it's singular. */
	private static double @Nullable [] solve(double[][] m, double[] b) {
		double det = det(m[0][0], m[0][1], m[0][2], m[1][0], m[1][1], m[1][2], m[2][0], m[2][1], m[2][2]);
		if (Math.abs(det) < 1e-9) {
			return null;
		}
		double a = det(b[0], m[0][1], m[0][2], b[1], m[1][1], m[1][2], b[2], m[2][1], m[2][2]) / det;
		double c1 = det(m[0][0], b[0], m[0][2], m[1][0], b[1], m[1][2], m[2][0], b[2], m[2][2]) / det;
		double c2 = det(m[0][0], m[0][1], b[0], m[1][0], m[1][1], b[1], m[2][0], m[2][1], b[2]) / det;
		return new double[]{a, c1, c2};
	}

	private static double det(double a, double b, double c, double d, double e, double f, double g, double h, double i) {
		return a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
	}

	private static void receive(TrailMapPayload p) {
		if (p.parts() < 1 || p.parts() > 64 || p.part() < 0 || p.part() >= p.parts()) {
			return;
		}
		byte[][] pieces = PENDING.computeIfAbsent(p.mapId(), k -> new byte[p.parts()][]);
		if (pieces.length != p.parts()) {
			pieces = new byte[p.parts()][];
			PENDING.put(p.mapId(), pieces);
		}
		pieces[p.part()] = p.data();
		for (byte[] piece : pieces) {
			if (piece == null) {
				return;
			}
		}
		PENDING.remove(p.mapId());
		ByteArrayOutputStream all = new ByteArrayOutputStream();
		for (byte[] piece : pieces) {
			all.writeBytes(piece);
		}
		try {
			NativeImage image = NativeImage.read(all.toByteArray());
			String path = "trail_map/" + p.mapId().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
			Identifier id = FreshTracks.id(path);
			Minecraft mc = Minecraft.getInstance();
			mc.getTextureManager().register(id, new DynamicTexture(() -> "freshtracks trail map " + p.mapId(), image));
			MAPS.put(p.mapId(), new Map2(p.mapId(), id, p.x0(), p.z0(), p.x1(), p.z1(), (float) image.getWidth() / image.getHeight()));
		} catch (Exception e) {
			FreshTracks.LOG.warn("Couldn't read the trail map {} from the server", p.mapId(), e);
		}
	}

	private static void clear() {
		Minecraft mc = Minecraft.getInstance();
		for (Map2 map : MAPS.values()) {
			mc.getTextureManager().release(map.texture());
		}
		MAPS.clear();
		PENDING.clear();
		POINTS.clear();
	}
}
