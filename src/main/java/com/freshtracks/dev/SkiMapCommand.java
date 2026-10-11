package com.freshtracks.dev;

import com.freshtracks.FreshTracks;
import com.freshtracks.world.ski.SkiTerrain;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Development only: {@code /ftdev skimap [size] [step]} draws the Ski Mountains terrain for this world's seed
 * (whatever the world type) to run/skimap.png: shaded relief tinted like trail ratings (green under 15 degrees, blue
 * to 25, black to 35, double black to 45, red cliffs beyond), and measures how long the downhill runs are by
 * following the fall line from many starting points.
 */
public final class SkiMapCommand {
	private SkiMapCommand() {
	}

	static int run(CommandSourceStack source, int size, int step) {
		long seed = source.getLevel().getSeed();
		SkiTerrain terrain = SkiTerrain.forSeed(seed);
		int cx = Mth.floor(source.getPosition().x);
		int cz = Mth.floor(source.getPosition().z);
		int n = size / step;
		BufferedImage image = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
		double minH = Double.MAX_VALUE;
		double maxH = -Double.MAX_VALUE;
		double[][] h = new double[n][n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				h[i][j] = terrain.height(cx - size / 2 + i * step, cz - size / 2 + j * step);
				minH = Math.min(minH, h[i][j]);
				maxH = Math.max(maxH, h[i][j]);
			}
		}
		int[] bands = new int[5];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				int x = cx - size / 2 + i * step;
				int z = cz - size / 2 + j * step;
				double deg = Math.toDegrees(Math.atan(terrain.slope(x, z)));
				int band = deg < 15 ? 0 : deg < 25 ? 1 : deg < 35 ? 2 : deg < 45 ? 3 : 4;
				bands[band]++;
				int[] tint = switch (band) {
					case 0 -> new int[]{120, 200, 120};
					case 1 -> new int[]{110, 150, 235};
					case 2 -> new int[]{80, 80, 90};
					case 3 -> new int[]{40, 40, 45};
					default -> new int[]{220, 70, 60};
				};
				// light from the north-west
				double hx = (h[Math.min(n - 1, i + 1)][j] - h[Math.max(0, i - 1)][j]) / (2.0 * step);
				double hz = (h[i][Math.min(n - 1, j + 1)] - h[i][Math.max(0, j - 1)]) / (2.0 * step);
				double shade = Mth.clamp(0.75 + (-hx - hz) * 0.9, 0.35, 1.15);
				double whiteness = Mth.clamp((h[i][j] - minH) / Math.max(1, maxH - minH), 0, 1) * 0.35;
				int r = (int) Mth.clamp((tint[0] * (1 - whiteness) + 255 * whiteness) * shade, 0, 255);
				int g = (int) Mth.clamp((tint[1] * (1 - whiteness) + 255 * whiteness) * shade, 0, 255);
				int b = (int) Mth.clamp((tint[2] * (1 - whiteness) + 255 * whiteness) * shade, 0, 255);
				image.setRGB(i, j, 0xFF000000 | r << 16 | g << 8 | b);
			}
		}
		// Fall-line runs: start on high ground, step 4 blocks at a time down the steepest way while it's still a ski
		// slope (6..40 degrees), stop when it flattens out or cliffs off.
		List<Integer> runs = new ArrayList<>();
		int longestStartX = cx;
		int longestStartZ = cz;
		int longest = 0;
		for (int i = 0; i < n; i += Math.max(1, n / 40)) {
			for (int j = 0; j < n; j += Math.max(1, n / 40)) {
				int x = cx - size / 2 + i * step;
				int z = cz - size / 2 + j * step;
				if (terrain.height(x, z) < SkiTerrain.VALLEY + 0.45 * SkiTerrain.RELIEF) {
					continue;
				}
				int length = fallLine(terrain, x, z);
				if (length > 0) {
					runs.add(length);
					if (length > longest) {
						longest = length;
						longestStartX = x;
						longestStartZ = z;
					}
				}
			}
		}
		Collections.sort(runs);
		File out = new File(FabricLoader.getInstance().getGameDir().toFile(), "skimap.png");
		try {
			ImageIO.write(image, "png", out);
		} catch (IOException e) {
			source.sendFailure(Component.literal("Could not write " + out + ": " + e.getMessage()));
			return 0;
		}
		int total = n * n;
		String slopes = String.format("slopes: green %d%%, blue %d%%, black %d%%, double %d%%, cliff %d%%", bands[0] * 100 / total,
			bands[1] * 100 / total, bands[2] * 100 / total, bands[3] * 100 / total, bands[4] * 100 / total);
		String runText = runs.isEmpty() ? "no runs" : String.format("runs from %d high starts: median %d blocks, 90th pct %d, longest %d (from %d %d)",
			runs.size(), runs.get(runs.size() / 2), runs.get(runs.size() * 9 / 10), longest, longestStartX, longestStartZ);
		String heights = String.format("heights %.0f..%.0f", minH, maxH);
		FreshTracks.LOG.info("[skimap] {} | {} | {}", heights, slopes, runText);
		source.sendSuccess(() -> Component.literal("Wrote skimap.png. " + heights + "; " + slopes + "; " + runText), false);
		return 1;
	}

	/** {@code /ftdev peak <radius>}: the highest ground within the radius (Ski Mountains terrain), to the log. */
	static int peak(CommandSourceStack source, int radius) {
		SkiTerrain terrain = SkiTerrain.forSeed(source.getLevel().getSeed());
		int cx = Mth.floor(source.getPosition().x);
		int cz = Mth.floor(source.getPosition().z);
		int bx = cx;
		int bz = cz;
		double best = -1;
		for (int dx = -radius; dx <= radius; dx += 8) {
			for (int dz = -radius; dz <= radius; dz += 8) {
				double h = terrain.height(cx + dx, cz + dz);
				if (h > best) {
					best = h;
					bx = cx + dx;
					bz = cz + dz;
				}
			}
		}
		String text = String.format("peak %d %.0f %d", bx, best, bz);
		FreshTracks.LOG.info("[skitools] {}", text);
		source.sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	/** {@code /ftdev fallline}: the fall line from where you stand, every 16 blocks (x y z slope), to the log. */
	static int fallLineCommand(CommandSourceStack source) {
		SkiTerrain terrain = SkiTerrain.forSeed(source.getLevel().getSeed());
		double px = source.getPosition().x;
		double pz = source.getPosition().z;
		StringBuilder sb = new StringBuilder("fallline");
		double travelled = 0;
		for (int step = 0; step < 2000; step++) {
			int ix = Mth.floor(px);
			int iz = Mth.floor(pz);
			double gx = (terrain.height(ix + 12, iz) - terrain.height(ix - 12, iz)) / 24.0;
			double gz = (terrain.height(ix, iz + 12) - terrain.height(ix, iz - 12)) / 24.0;
			double g = Math.sqrt(gx * gx + gz * gz);
			if (step % 8 == 0) {
				sb.append(String.format(" | %d %.0f %d %.2f", ix, terrain.height(ix, iz), iz, g));
			}
			if (g < 0.05 && travelled > 64) {
				break;
			}
			px -= gx / Math.max(g, 1.0e-6) * 2;
			pz -= gz / Math.max(g, 1.0e-6) * 2;
			travelled += 2;
		}
		FreshTracks.LOG.info("[skitools] {}", sb);
		long length = Math.round(travelled);
		source.sendSuccess(() -> Component.literal("fall line traced (" + length + " blocks), see the log"), false);
		return 1;
	}

	/** Horizontal length of the skiable fall line from (x, z), blocks. */
	private static int fallLine(SkiTerrain terrain, int x, int z) {
		double px = x;
		double pz = z;
		int length = 0;
		for (int stepCount = 0; stepCount < 1000; stepCount++) {
			int ix = Mth.floor(px);
			int iz = Mth.floor(pz);
			double gx = (terrain.height(ix + 3, iz) - terrain.height(ix - 3, iz)) / 6.0;
			double gz = (terrain.height(ix, iz + 3) - terrain.height(ix, iz - 3)) / 6.0;
			double g = Math.sqrt(gx * gx + gz * gz);
			double deg = Math.toDegrees(Math.atan(g));
			if (deg > 40 || (deg < 6 && length > 0)) {
				break;
			}
			if (g < 1.0e-6) {
				break;
			}
			px -= gx / g * 4;
			pz -= gz / g * 4;
			length += 4;
		}
		return length;
	}
}
