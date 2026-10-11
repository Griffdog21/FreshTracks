package com.freshtracks.rig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Turns blocky terrain into a smooth ride surface.
 *
 * <p>Each block column has one "top" (snow layers and powder count by their visible height). The ride surface is
 * never below the top of the column you're over, so skis never sink into the snow. Steps are smoothed the way snow
 * drifts fill them: every column a little higher than its neighbours casts a ramp downhill from its edge, sloping at
 * the hillside's own steepness, and the surface is the highest of those ramps. A staircase rides like an even slope;
 * drops bigger than {@link #SMOOTH_STEP} stay sharp edges (kicker lips, cornices, cliffs).
 */
public final class SurfaceSampler {
	/** Columns more than this far above the rider count as walls, not slope. */
	public static final double MAX_STEP = 1.1;
	/** Columns up to this far above the rider's own still cast a ramp; taller ones are walls. */
	private static final double FLATTEN_STEP = 1.6;
	/** Steps up to this tall get smoothed over; bigger drops are real edges you launch off. */
	private static final double SMOOTH_STEP = 1.3;
	/** How far (columns) a ramp reaches from the step that casts it. */
	private static final int REACH = 4;
	/** Ramps are never gentler than this, so a lone step always gets smoothed within {@link #REACH}. */
	private static final double MIN_RAMP = 0.25;
	/** Nor steeper than this (a 1:1 staircase and a bit). */
	private static final double MAX_RAMP = 1.35;
	private static final double SCAN_UP = 2.6;
	private static final double SCAN_DOWN = 6.0;
	/** Half the distance used to measure the surface's slope. */
	private static final double GRADIENT_STEP = 0.35;

	private SurfaceSampler() {
	}

	public record Column(double top, Surface surface, Vec3 carry, boolean found) {
	}

	/** Ground under one point: surface height, slope and what it's made of. */
	public record Ground(double height, double gradX, double gradZ, Surface surface, Vec3 carry, boolean found) {
		public double slope() {
			return Math.sqrt(gradX * gradX + gradZ * gradZ);
		}

		/** Unit surface normal. */
		public Vec3 normal() {
			return new Vec3(-gradX, 1.0, -gradZ).normalize();
		}
	}

	/** The top of the ride surface in one column, searching around {@code refY}. */
	public static Column column(BlockGetter level, int bx, int bz, double refY) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int top = Mth.floor(refY + SCAN_UP);
		int bottom = Mth.floor(refY - SCAN_DOWN);
		for (int y = top; y >= bottom; y--) {
			pos.set(bx, y, bz);
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}
			Block block = state.getBlock();
			// Snow sitting on branches isn't the ground: look past it (and the leaves) to the real snow below.
			if ((block instanceof SnowLayerBlock || block instanceof RideableSurface) && level.getBlockState(pos.below()).is(BlockTags.LEAVES)) {
				continue;
			}
			if (block instanceof RideableSurface rideable) {
				return new Column(y + rideable.rideHeight(state, level, pos), rideable.surface(state), rideable.carry(state), true);
			}
			if (block instanceof SnowLayerBlock) {
				// Ordinary snow cover rides like packed snow; fresh powder is its own block.
				int layers = state.getValue(SnowLayerBlock.LAYERS);
				return new Column(y + layers / 8.0, Surface.PACKED, Vec3.ZERO, true);
			}
			if (block == Blocks.POWDER_SNOW) {
				// You only sink into powder when you're slow; RigMotion handles that.
				return new Column(y + 1.0, Surface.DEEP_POWDER, Vec3.ZERO, true);
			}
			FluidState fluid = state.getFluidState();
			if (!fluid.isEmpty() && state.getCollisionShape(level, pos).isEmpty()) {
				if (fluid.is(FluidTags.WATER)) {
					return new Column(y + fluid.getHeight(level, pos), Surface.WATER, Vec3.ZERO, true);
				}
				// Lava and other fluids: treat as a hard floor so we never ride "into" them silently.
				return new Column(y + fluid.getHeight(level, pos), Surface.HARD, Vec3.ZERO, true);
			}
			if (state.is(BlockTags.LEAVES)) {
				// Branches whip past; the ground is further down.
				continue;
			}
			VoxelShape shape = state.getCollisionShape(level, pos);
			if (shape.isEmpty()) {
				continue;
			}
			double height = Math.min(shape.max(Direction.Axis.Y), 1.5);
			return new Column(y + height, classify(state), Vec3.ZERO, true);
		}
		return new Column(refY - SCAN_DOWN, Surface.AIR, Vec3.ZERO, false);
	}

	public static Surface classify(BlockState state) {
		if (state.is(Blocks.SNOW_BLOCK) || state.is(BlockTags.SNOW)) {
			return Surface.PACKED;
		}
		if (state.is(BlockTags.ICE)) {
			return Surface.ICE;
		}
		if (state.is(BlockTags.DIRT) || state.is(Blocks.FARMLAND) || state.is(Blocks.DIRT_PATH)) {
			return Surface.DIRT;
		}
		if (state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL)) {
			return Surface.SAND;
		}
		if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)) {
			return Surface.WOOD;
		}
		if (state.is(BlockTags.BASE_STONE_OVERWORLD)) {
			return Surface.ROCK;
		}
		return Surface.HARD;
	}

	/** The ride surface at (x, z), its slope, and what it's made of. */
	public static Ground ground(BlockGetter level, double x, double z, double refY) {
		Patch patch = new Patch(level, Mth.floor(x), Mth.floor(z), refY);
		Column own = patch.column(Mth.floor(x), Mth.floor(z));
		double height = patch.surface(x, z);
		double gradX = (patch.surface(x + GRADIENT_STEP, z) - patch.surface(x - GRADIENT_STEP, z)) / (2 * GRADIENT_STEP);
		double gradZ = (patch.surface(x, z + GRADIENT_STEP) - patch.surface(x, z - GRADIENT_STEP)) / (2 * GRADIENT_STEP);
		return new Ground(height, gradX, gradZ, own.surface(), own.carry(), own.found());
	}

	/** The columns around one point, read once per lookup. */
	private static final class Patch {
		/** Half-size of the window the hillside's slope is fitted over, and the fit's normaliser (sum of dx^2). */
		private static final int FIT = 2;
		private static final double FIT_NORM = (2 * FIT + 1) * (FIT * (FIT + 1) * (2 * FIT + 1) / 3.0);
		private static final int SPAN = REACH + 1 + FIT;
		private static final int SIZE = 2 * SPAN + 1;
		private final BlockGetter level;
		private final int cx;
		private final int cz;
		private final double refY;
		private final Column[] columns = new Column[SIZE * SIZE];
		private final double[] ramps = new double[SIZE * SIZE];

		Patch(BlockGetter level, int cx, int cz, double refY) {
			this.level = level;
			this.cx = cx;
			this.cz = cz;
			this.refY = refY;
			java.util.Arrays.fill(ramps, Double.NaN);
		}

		Column column(int bx, int bz) {
			int i = bx - cx + SPAN;
			int j = bz - cz + SPAN;
			if (i < 0 || j < 0 || i >= SIZE || j >= SIZE) {
				return SurfaceSampler.column(level, bx, bz, refY);
			}
			int k = j * SIZE + i;
			Column c = columns[k];
			if (c == null) {
				c = SurfaceSampler.column(level, bx, bz, refY);
				columns[k] = c;
			}
			return c;
		}

		private boolean usable(Column c) {
			return c.found() && c.top() <= refY + FLATTEN_STEP;
		}

		/** How steep the ramp a column casts is: the hillside's slope there, measured over 4 blocks. */
		private double ramp(int bx, int bz) {
			int i = bx - cx + SPAN;
			int j = bz - cz + SPAN;
			int k = j * SIZE + i;
			boolean cached = i >= 0 && j >= 0 && i < SIZE && j < SIZE;
			if (cached && !Double.isNaN(ramps[k])) {
				return ramps[k];
			}
			// best-fit plane over the 5x5 columns around it: steady over a staircase of uneven treads, still sharp
			// where the pitch changes
			double top = column(bx, bz).top();
			double sx = 0;
			double sz = 0;
			for (int dz = -FIT; dz <= FIT; dz++) {
				for (int dx = -FIT; dx <= FIT; dx++) {
					double t = topOr(bx + dx, bz + dz, top, Math.max(Math.abs(dx), Math.abs(dz))) - top;
					sx += dx * t;
					sz += dz * t;
				}
			}
			double gx = sx / FIT_NORM;
			double gz = sz / FIT_NORM;
			double slope = Mth.clamp(Math.sqrt(gx * gx + gz * gz), MIN_RAMP, MAX_RAMP);
			if (cached) {
				ramps[k] = slope;
			}
			return slope;
		}

		/**
		 * A neighbour's top for slope estimates, {@code distance} columns away; anything further off the column's own
		 * height than even the steepest slope plus a step explains (a cliff, a wall) counts as level with it. Judged
		 * against the column itself, not the rider: on a steep pitch the snow uphill is well above your head and still
		 * slope.
		 */
		private double topOr(int bx, int bz, double fallback, int distance) {
			Column c = column(bx, bz);
			if (!c.found() || Math.abs(c.top() - fallback) > distance * MAX_RAMP + SMOOTH_STEP) {
				return fallback;
			}
			return c.top();
		}

		/** The hillside's slope at a point: blended between the column centres around it, so ramps change smoothly. */
		private double slopeAt(double px, double pz) {
			double fx = px - 0.5;
			double fz = pz - 0.5;
			int x0 = Mth.floor(fx);
			int z0 = Mth.floor(fz);
			double tx = fx - x0;
			double tz = fz - z0;
			double s0 = Mth.lerp(tx, ramp(x0, z0), ramp(x0 + 1, z0));
			double s1 = Mth.lerp(tx, ramp(x0, z0 + 1), ramp(x0 + 1, z0 + 1));
			return Mth.lerp(tz, s0, s1);
		}

		/**
		 * Ride height at (px, pz): the highest of the ramps the higher columns nearby cast, all at this spot's slope
		 * (so one step's ramp hands over to the next without a ledge), never below the column's own top.
		 */
		double surface(double px, double pz) {
			int bx = Mth.floor(px);
			int bz = Mth.floor(pz);
			Column own = column(bx, bz);
			if (!own.found()) {
				return own.top();
			}
			double ownTop = own.top();
			double slope = slopeAt(px, pz);
			double best = ownTop;
			for (int dz = -REACH; dz <= REACH; dz++) {
				for (int dx = -REACH; dx <= REACH; dx++) {
					if (dx == 0 && dz == 0) {
						continue;
					}
					int nx = bx + dx;
					int nz = bz + dz;
					Column c = column(nx, nz);
					if (!usable(c) || c.top() <= ownTop) {
						continue;
					}
					// distance from the point to that column's square
					double ddx = Math.max(0, Math.max(nx - px, px - (nx + 1)));
					double ddz = Math.max(0, Math.max(nz - pz, pz - (nz + 1)));
					double drop = slope * Math.sqrt(ddx * ddx + ddz * ddz);
					if (drop > SMOOTH_STEP) {
						continue; // a ramp only reaches so far down: bigger drops stay edges
					}
					best = Math.max(best, c.top() - drop);
				}
			}
			// at most a step's worth of "drift" on top of the real ground
			return Math.min(best, ownTop + SMOOTH_STEP);
		}
	}
}
