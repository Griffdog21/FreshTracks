package com.freshtracks.lift;

import com.freshtracks.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Plans and builds towers for a lift line so chairs clear the ground: towers go where the rope would sag too low,
 * plus enough of them that no span is longer than {@link #MAX_SPAN}. Like a real ski area, it cuts the lift line:
 * leaves in the chairs' path are cleared (trunks are treated as ground, so the rope goes over them).
 */
public final class TowerBuilder {
	/** Rope must stay this far above the ground (chairs hang ~2.6 below it). */
	public static final double CLEARANCE = 5.6;
	/** Surface lifts: the rope only has to clear the riders' heads, the bars reel out to them. */
	public static final double SURFACE_CLEARANCE = 3.4;
	public static final double MAX_SPAN = 42.0;
	private static final double MIN_GAP = 10.0;
	/** Half-width of the corridor the chairs need, blocks either side of the centre line. */
	private static final double CORRIDOR = LiftLine.TRACK_HALF + 1.4;

	private TowerBuilder() {
	}

	private record Point(double d, double ropeY, boolean fixed) {
	}

	public static List<BlockPos> plan(ServerLevel level, BlockPos bottom, BlockPos top) {
		return plan(level, bottom, top, ChairStyle.QUAD);
	}

	public static List<BlockPos> plan(ServerLevel level, BlockPos bottom, BlockPos top, ChairStyle style) {
		double clearance = style.surface() ? SURFACE_CLEARANCE : CLEARANCE;
		Vec3 a = Vec3.atBottomCenterOf(bottom).add(0, LiftLine.TERMINAL_ROPE, 0);
		Vec3 b = Vec3.atBottomCenterOf(top).add(0, LiftLine.TERMINAL_ROPE, 0);
		double dx = b.x - a.x;
		double dz = b.z - a.z;
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 4) {
			return List.of();
		}
		double ux = dx / length;
		double uz = dz / length;
		int n = (int) Math.ceil(length) + 1;
		double[] ground = new double[n];
		for (int i = 0; i < n; i++) {
			double cx = a.x + ux * i;
			double cz = a.z + uz * i;
			double g = Double.NEGATIVE_INFINITY;
			for (double off = -CORRIDOR; off <= CORRIDOR + 0.01; off += 0.7) {
				int x = Mth.floor(cx - uz * off);
				int z = Mth.floor(cz + ux * off);
				g = Math.max(g, groundIgnoringTrees(level, x, z));
			}
			ground[i] = g;
		}

		double station = LiftLine.STATION_LENGTH;
		List<Point> points = new ArrayList<>();
		points.add(new Point(0, a.y, true));
		if (length > 2 * station + MIN_GAP) {
			points.add(new Point(station, a.y, true));
			points.add(new Point(length - station, b.y, true));
		}
		points.add(new Point(length, b.y, true));

		for (int iteration = 0; iteration < 60; iteration++) {
			int worstSeg = -1;
			double worstD = 0;
			double worst = 0.4;
			for (int s = 0; s < points.size() - 1; s++) {
				Point p = points.get(s);
				Point q = points.get(s + 1);
				double span = q.d - p.d;
				if (span < 2 * MIN_GAP) {
					continue; // too short to fit another tower; live with it
				}
				for (int i = (int) Math.ceil(p.d + 1); i < q.d - 1; i++) {
					if (i < station + 3 || i > length - station - 3) {
						continue; // the rope dips to loading height inside terminals on purpose
					}
					double t = (i - p.d) / span;
					double rope = Mth.lerp(t, p.ropeY, q.ropeY) - 4 * 0.018 * span * t * (1 - t);
					double violation = ground[i] + clearance - rope;
					if (violation > worst) {
						worst = violation;
						worstSeg = s;
						worstD = i;
					}
				}
			}
			if (worstSeg >= 0) {
				Point p = points.get(worstSeg);
				Point q = points.get(worstSeg + 1);
				double d = Mth.clamp(worstD, p.d + MIN_GAP, q.d - MIN_GAP);
				points.add(worstSeg + 1, new Point(d, towerRope(ground, d, clearance), false));
				continue;
			}
			int longest = -1;
			double longestSpan = MAX_SPAN;
			for (int s = 0; s < points.size() - 1; s++) {
				double span = points.get(s + 1).d - points.get(s).d;
				if (span > longestSpan) {
					longestSpan = span;
					longest = s;
				}
			}
			if (longest < 0) {
				break;
			}
			Point p = points.get(longest);
			Point q = points.get(longest + 1);
			double d = (p.d + q.d) / 2;
			double straight = (p.ropeY + q.ropeY) / 2;
			points.add(longest + 1, new Point(d, Math.max(straight, towerRope(ground, d, clearance)), false));
		}

		List<BlockPos> heads = new ArrayList<>();
		for (Point p : points) {
			if (p.fixed) {
				continue;
			}
			int x = Mth.floor(a.x + ux * p.d);
			int z = Mth.floor(a.z + uz * p.d);
			int y = Mth.floor(p.ropeY - LiftLine.HEAD_ROPE + 0.5);
			heads.add(new BlockPos(x, y, z));
		}
		return heads;
	}

	/** Ground height at (x, z), looking through tree trunks (the lift line gets cut). */
	private static int groundIgnoringTrees(ServerLevel level, int x, int z) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y - 1, z);
		while (pos.getY() > level.getMinY() && level.getBlockState(pos).is(BlockTags.LOGS)) {
			pos.move(0, -1, 0);
		}
		return pos.getY() + 1;
	}

	/** Rope height for a tower at d: clear the highest ground nearby, plus a little. */
	private static double towerRope(double[] ground, double d, double clearance) {
		int c = (int) Math.round(d);
		double g = Double.NEGATIVE_INFINITY;
		for (int i = Math.max(0, c - 4); i <= Math.min(ground.length - 1, c + 4); i++) {
			g = Math.max(g, ground[i]);
		}
		return g + clearance + 1.6;
	}

	/** Places pole sections from the ground up to each head, and the head itself. */
	public static void build(ServerLevel level, List<BlockPos> heads) {
		BlockState pole = ModBlocks.LIFT_TOWER.defaultBlockState();
		BlockState head = ModBlocks.LIFT_TOWER_HEAD.defaultBlockState();
		for (BlockPos h : heads) {
			level.setBlock(h, head, Block.UPDATE_ALL);
			BlockPos.MutableBlockPos pos = h.mutable().move(0, -1, 0);
			int guard = 0;
			while (pos.getY() > level.getMinY() && guard++ < 160) {
				BlockState here = level.getBlockState(pos);
				if (!here.isAir() && !here.canBeReplaced() && !here.is(BlockTags.LEAVES) && !here.is(BlockTags.SNOW)) {
					break;
				}
				level.setBlock(pos, pole, Block.UPDATE_ALL);
				pos.move(0, -1, 0);
			}
		}
	}

	/** Cuts the lift line: removes trees (leaves and trunks) where chairs and riders will pass. */
	public static void clearCorridor(ServerLevel level, LiftLine line) {
		double step = 0.8;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (double s = 0; s < line.loopLength(); s += step) {
			LiftLine.RopePoint rope = line.ropeAt(s);
			Vec3 p = rope.pos();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (double dy = -line.style.hanger - 1.2; dy <= 1.6; dy += 1.0) {
						pos.set(Mth.floor(p.x) + dx, Mth.floor(p.y + dy), Mth.floor(p.z) + dz);
						BlockState state = level.getBlockState(pos);
						if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
							level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
						}
					}
				}
			}
		}
	}
}
