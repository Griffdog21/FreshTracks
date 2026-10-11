package com.freshtracks.world.ski;

import com.freshtracks.FreshTracks;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * The ground height of the Ski Mountains world type: broad valleys, and mountain ranges whose flanks fall away in long,
 * even pitches you can ride for a minute or more, steepening toward the ridgelines (the expert stuff) and topping out
 * in craggy summits. Everything is a pure function of (x, z) and the world seed.
 *
 * <p>Shape: domain-warped coordinates; a very low-frequency "massif" field decides where the ranges stand; ridged noise
 * inside them draws the ridgelines; the profile is raised to a power so slopes are concave (steep up top, long gentle
 * run-outs at the bottom, like real mountains); then rolls and gullies on top.
 */
public final class SkiTerrain {
	/** Valley floor height. */
	public static final double VALLEY = 72;
	/** How much higher the tallest summits get. */
	public static final double RELIEF = 330;
	public static final double MAX_HEIGHT = 440;

	private static final Map<Long, SkiTerrain> BY_KEY = new ConcurrentHashMap<>();

	private final SimplexNoise warpX;
	private final SimplexNoise warpZ;
	private final SimplexNoise warpFineX;
	private final SimplexNoise warpFineZ;
	private final SimplexNoise massif;
	private final SimplexNoise massifFine;
	private final SimplexNoise ridgeMain;
	private final SimplexNoise ridgeSide;
	private final SimplexNoise rolls;
	private final SimplexNoise bumps;
	private final SimplexNoise moisture;
	private final SimplexNoise variety;
	private final ThreadLocal<Cache> cache = ThreadLocal.withInitial(Cache::new);

	private SkiTerrain(RandomSource random) {
		this.warpX = new SimplexNoise(random);
		this.warpZ = new SimplexNoise(random);
		this.warpFineX = new SimplexNoise(random);
		this.warpFineZ = new SimplexNoise(random);
		this.massif = new SimplexNoise(random);
		this.massifFine = new SimplexNoise(random);
		this.ridgeMain = new SimplexNoise(random);
		this.ridgeSide = new SimplexNoise(random);
		this.rolls = new SimplexNoise(random);
		this.bumps = new SimplexNoise(random);
		this.moisture = new SimplexNoise(random);
		this.variety = new SimplexNoise(random);
	}

	/** The terrain for the world being compiled (one shared instance per seed). */
	public static SkiTerrain of(DensityFunction.CompileContext context) {
		return byKey(context.createRandom(FreshTracks.id("ski_terrain")).nextLong());
	}

	/** The same terrain from a world seed directly (dev tools): matches what world generation builds. */
	public static SkiTerrain forSeed(long seed) {
		RandomSource random = WorldgenRandom.Algorithm.XOROSHIRO.newInstance(seed).forkPositional().fromHashOf(FreshTracks.id("ski_terrain"));
		return byKey(random.nextLong());
	}

	private static SkiTerrain byKey(long key) {
		if (BY_KEY.size() > 8) {
			BY_KEY.clear();
		}
		return BY_KEY.computeIfAbsent(key, k -> new SkiTerrain(RandomSource.create(k)));
	}

	// --------------------------------------------------------------------------------------------- fields

	/** Ground height at (x, z), blocks. */
	public double height(int x, int z) {
		Cache c = cache.get();
		int slot = ((x * 73856093) ^ (z * 19349663)) & (Cache.SIZE - 1);
		if (c.valid[slot] && c.xs[slot] == x && c.zs[slot] == z) {
			return c.heights[slot];
		}
		double h = compute(x, z);
		c.xs[slot] = x;
		c.zs[slot] = z;
		c.heights[slot] = h;
		c.valid[slot] = true;
		return h;
	}

	private double compute(int x, int z) {
		double wx = x + 220 * warpX.get(x / 1400.0, z / 1400.0) + 30 * warpFineX.get(x / 300.0, z / 300.0);
		double wz = z + 220 * warpZ.get(x / 1400.0, z / 1400.0) + 30 * warpFineZ.get(x / 300.0, z / 300.0);
		// Where the ranges stand: 0 = open valley country, 1 = the heart of a range. Very low frequency, so whole
		// ranges rise together and flanks are long. Past the top of the ramp the ground keeps climbing, ever more
		// slowly, so range tops never flatten into mesas.
		double m = 0.5 + 0.5 * (0.75 * massif.get(wx / 4200.0, wz / 4200.0) + 0.25 * massifFine.get(wx / 1700.0, wz / 1700.0));
		double raw = Math.max(0, (m - 0.30) / (0.85 - 0.30));
		double lift = raw < 1 ? raw : 1 + 0.35 * Math.tanh((raw - 1) / 0.35);
		// Concave base: long gentle run-outs at the bottom, steeper toward the top.
		double h = VALLEY + MASSIF_RELIEF * Math.pow(lift, 1.5);
		// A network of spur ridges and drainages across the whole range (ridged noise): the long fall lines run
		// down the spurs and gullies from the summits to the valley; the crests are the steep expert terrain.
		double r1 = 1 - Math.abs(ridgeMain.get(wx / 2600.0, wz / 2600.0));
		double r2 = 1 - Math.abs(ridgeSide.get(wx / 900.0, wz / 900.0));
		double ridge = 0.75 * Math.pow(r1, 1.3) + 0.25 * Math.pow(r2, 1.3);
		h += RIDGE_RELIEF * lift * ridge;
		// Rolls on the slopes and gentle swells in the valleys.
		h += (2.5 + 4.5 * lift) * rolls.get(x / 230.0, z / 230.0) + 7 * (1 - lift) * rolls.get(x / 700.0 + 91.7, z / 700.0 - 13.1)
			+ 1.1 * bumps.get(x / 55.0, z / 55.0);
		// the highest summits taper toward the build limit instead of being cut off flat
		double knee = MAX_HEIGHT - 70;
		return h < knee ? h : knee + 70 * Math.tanh((h - knee) / 70);
	}

	/** The broad mountain body and the ridges on top of it (they add up to {@link #RELIEF}). */
	private static final double MASSIF_RELIEF = 200;
	private static final double RIDGE_RELIEF = 230;

	/** Steepness at (x, z) as rise over run, measured across 8 blocks. */
	public double slope(int x, int z) {
		double gx = (height(x + 4, z) - height(x - 4, z)) / 8.0;
		double gz = (height(x, z + 4) - height(x, z - 4)) / 8.0;
		return Math.sqrt(gx * gx + gz * gz);
	}

	/** -1 (dry, open) .. 1 (wet, wooded): decides trees vs open bowls at the same height. */
	public double moisture(int x, int z) {
		return Mth.clamp(0.75 * moisture.get(x / 900.0, z / 900.0) + 0.25 * moisture.get(x / 210.0 + 37.1, z / 210.0 - 11.3), -1, 1);
	}

	/** A second, independent -1..1 field for mixing biomes. */
	public double variety(int x, int z) {
		return Mth.clamp(variety.get(x / 700.0, z / 700.0), -1, 1);
	}

	/** Height mapped to -1 (valley floor) .. 1 (highest summits): the biome source's continentalness. */
	public double elevation(int x, int z) {
		return Mth.clamp((height(x, z) - VALLEY) / RELIEF * 2 - 1, -1, 1);
	}

	/** Steepness mapped to -1 (flat) .. 1 (45 degrees or more): the biome source's erosion. */
	public double steepness(int x, int z) {
		double degrees = Math.toDegrees(Math.atan(slope(x, z)));
		return Mth.clamp(degrees / 45.0 * 2 - 1, -1, 1);
	}

	private static double smoothstep(double edge0, double edge1, double x) {
		double t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
		return t * t * (3 - 2 * t);
	}

	/** Direct-mapped per-thread cache: chunk generation asks for the same columns over and over. */
	private static final class Cache {
		static final int SIZE = 1 << 14;
		final int[] xs = new int[SIZE];
		final int[] zs = new int[SIZE];
		final double[] heights = new double[SIZE];
		final boolean[] valid = new boolean[SIZE];
	}
}
