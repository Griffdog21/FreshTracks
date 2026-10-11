package com.freshtracks.command;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds a repeatable test run in front of a player: a start pad, a mellow groomer, a steeper pitch, a kicker
 * with a gap and a landing, a powder field, an ice patch, a tree-lined glade and a run-out. Used for play
 * testing and handy for map builders who want a quick practice slope.
 */
public final class TestSlopeBuilder {
	public static final int LENGTH = 250;
	public static final int HALF_WIDTH = 7;
	private static final int STEEP_HALF_WIDTH = 22;
	/** Steep variant: a black-diamond pitch then a double-black wall, for testing edges and hop turns. */
	private static boolean steep;

	private TestSlopeBuilder() {
	}

	/** Height of the snow surface, relative to the top of the start pad, at distance i down the run. */
	public static double profile(int i) {
		double h = 0;
		for (int k = 0; k < i; k++) {
			h -= slopeAt(k);
		}
		return h + kicker(i);
	}

	private static double slopeAt(int i) {
		if (steep) {
			if (i < 10) {
				return 0.0;
			}
			if (i < 40) {
				return 0.45;     // blue roll-in
			}
			if (i < 120) {
				return 0.95;     // black: 43 degrees
			}
			if (i < 160) {
				return 1.25;     // double black: 51 degrees
			}
			if (i < 200) {
				return 0.6;
			}
			return 0.05;
		}
		if (i < 10) {
			return 0.0;          // start pad
		}
		if (i < 50) {
			return 0.25;         // green: 14 degrees
		}
		if (i < 82) {
			return 0.55;         // blue/black pitch: 29 degrees
		}
		if (i < 92) {
			return 0.15;         // run-in to the kicker
		}
		if (i < 100) {
			return 0.15;         // kicker sits on top of this
		}
		if (i < 104) {
			return 1.6;          // knuckle: the ground drops away
		}
		if (i < 140) {
			return 0.62;         // landing
		}
		if (i < 180) {
			return 0.33;         // powder field
		}
		if (i < 190) {
			return 0.3;          // ice patch
		}
		if (i < 222) {
			return 0.4;          // glade
		}
		return 0.05;             // run-out
	}

	/** The kicker: a ramp built on top of the slope, 8 long, about 2.6 high at the lip. */
	private static double kicker(int i) {
		if (steep) {
			return 0;
		}
		if (i >= 92 && i < 100) {
			double t = (i - 92 + 1) / 8.0;
			return 2.6 * t * t * 0.6 + 2.6 * t * 0.4;
		}
		return 0;
	}

	public static void build(ServerLevel level, BlockPos origin, Direction forward, boolean steepRun) {
		steep = steepRun;
		build(level, origin, forward);
		steep = false;
	}

	public static void build(ServerLevel level, BlockPos origin, Direction forward) {
		Direction side = forward.getClockWise();
		int bottomY = origin.getY() - (steep ? 176 : 96);
		for (int i = 0; i < LENGTH; i++) {
			double h = profile(i);
			boolean powder = !steep && i >= 140 && i < 180;
			boolean ice = !steep && i >= 180 && i < 190;
			boolean glade = !steep && i >= 190 && i < 222;
			int half = steep ? STEEP_HALF_WIDTH : HALF_WIDTH;
			for (int w = -half - 3; w <= half + 3; w++) {
				boolean onRun = Math.abs(w) <= half;
				// Banks: the edges of the run rise a little so you don't fall off.
				double bank = onRun ? 0 : (Math.abs(w) - half) * 0.9;
				double surface = origin.getY() + h + bank;
				if (i >= 92 && i < 100 && !onRun) {
					surface -= kicker(i); // the kicker is only on the run itself
				}
				int top = Mth.floor(surface);
				int layers = (int) Math.round((surface - top) * 8.0);
				if (layers >= 8) {
					top++;
					layers = 0;
				}
				BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
				for (int y = bottomY; y < top; y++) {
					pos.set(origin).move(forward, i).move(side, w).setY(y);
					BlockState fill = y >= top - 2 ? (ice && onRun ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.SNOW_BLOCK.defaultBlockState())
						: Blocks.STONE.defaultBlockState();
					level.setBlock(pos, fill, Block.UPDATE_CLIENTS);
				}
				pos.set(origin).move(forward, i).move(side, w).setY(top);
				if (powder && onRun) {
					// Untracked fresh powder, flush with the rest of the run (at least half a block deep).
					level.setBlock(pos, com.freshtracks.registry.ModBlocks.FRESH_POWDER.defaultBlockState()
						.setValue(SnowLayerBlock.LAYERS, Math.max(4, layers)), Block.UPDATE_CLIENTS);
				} else if (layers > 0 && !(ice && onRun)) {
					level.setBlock(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers), Block.UPDATE_CLIENTS);
				} else {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				for (int y = top + 1; y < top + 12; y++) {
					pos.set(origin).move(forward, i).move(side, w).setY(y);
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				// Trees in the glade, and lining the run
				boolean treeSpot = (glade && onRun && Math.floorMod(i * 7 + w * 13, 23) == 0 && Math.abs(w) > 1)
					|| (!onRun && Math.abs(w) == half + 2 && i % 9 == 4 && i > 10);
				if (treeSpot) {
					pos.set(origin).move(forward, i).move(side, w).setY(top);
					spruce(level, pos.immutable());
				}
			}
		}
	}

	private static void spruce(ServerLevel level, BlockPos base) {
		int height = 7;
		for (int y = 0; y < height; y++) {
			level.setBlock(base.above(y), Blocks.SPRUCE_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		BlockState leaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
		for (int y = 2; y <= height + 1; y++) {
			int radius = y >= height ? 0 : (height - y) / 2 + (y % 2 == 0 ? 1 : 0);
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.abs(dx) + Math.abs(dz) > radius + 1 || (dx == 0 && dz == 0 && y < height)) {
						continue;
					}
					BlockPos p = base.offset(dx, y, dz);
					if (level.getBlockState(p).isAir()) {
						level.setBlock(p, leaves, Block.UPDATE_CLIENTS);
					}
				}
			}
		}
	}
}
