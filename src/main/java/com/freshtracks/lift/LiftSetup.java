package com.freshtracks.lift;

import com.freshtracks.registry.ModBlocks;
import com.freshtracks.resort.FloorMarkBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Finishing touches when a lift is built: LOAD HERE and WAIT HERE mats at the bottom terminal, like a real lift. */
public final class LiftSetup {
	/** How far behind the loading line the next group waits. */
	private static final double WAIT_BEHIND = 2.0;

	private LiftSetup() {
	}

	public static void placeMarks(LevelAccessor level, LiftLine line) {
		placeMarks(level, line, line.loadS(), line.loadPoint(), line.bottom.getY());
	}

	/** LOAD HERE / WAIT HERE mats at mid station {@code i}. */
	public static void placeMidMarks(LevelAccessor level, LiftLine line, int i) {
		placeMarks(level, line, line.midLoadS(i), line.midLoadPoint(i), line.stations.get(i).getY());
	}

	private static void placeMarks(LevelAccessor level, LiftLine line, double loadS, Vec3 centre, int y) {
		LiftLine.RopePoint rope = line.ropeAt(loadS);
		Vec3 ahead = new Vec3(rope.direction().x, 0, rope.direction().z).normalize();
		Vec3 side = new Vec3(-ahead.z, 0, ahead.x);
		Direction facing = Direction.getApproximateNearest(ahead.x, 0, ahead.z);
		// one mat per block of chair width, centred on the loading spot
		int mats = Math.max(1, (int) Math.round(line.style.width));
		for (int i = 0; i < mats; i++) {
			double lateral = i - (mats - 1) / 2.0;
			Vec3 at = centre.add(side.scale(lateral));
			place(level, BlockPos.containing(at.x, y, at.z), ModBlocks.LOAD_HERE.defaultBlockState().setValue(FloorMarkBlock.FACING, facing), line);
			Vec3 wait = at.subtract(ahead.scale(WAIT_BEHIND));
			place(level, BlockPos.containing(wait.x, y, wait.z), ModBlocks.WAIT_HERE.defaultBlockState().setValue(FloorMarkBlock.FACING, facing), line);
		}
	}

	private static void place(LevelAccessor level, BlockPos pos, BlockState mark, LiftLine line) {
		if (pos.equals(line.bottom)) {
			return;
		}
		// sit on top of whatever is there: find the snow surface within a block or two of the terminal's level
		BlockPos.MutableBlockPos p = pos.mutable();
		for (int dy = 2; dy >= -2; dy--) {
			p.set(pos.getX(), pos.getY() + dy, pos.getZ());
			BlockState here = level.getBlockState(p);
			BlockState below = level.getBlockState(p.below());
			if ((here.isAir() || here.canBeReplaced() || here.getBlock() instanceof FloorMarkBlock)
				&& below.isFaceSturdy(level, p.below(), Direction.UP)) {
				level.setBlock(p, mark, Block.UPDATE_ALL);
				return;
			}
		}
	}

	/** Snaps a rope speed to something tidy (two decimals). */
	static double tidySpeed(double speed) {
		return Math.round(Mth.clamp(speed, 0.05, 0.45) * 100) / 100.0;
	}
}
