package com.freshtracks.resort;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jspecify.annotations.Nullable;

/**
 * Asphalt with the double yellow centre line painted on it. A road's line runs at any angle, so each block carries the
 * piece of it that crosses that block: in at one edge and out at another, at one of five points along each edge
 * ({@link #POINTS}, in sixteenths of a block). Neighbouring blocks share the point on their common edge, so a line
 * laid along a road's real centreline ({@link #seg}) runs on unbroken through the curves instead of stepping block by
 * block. Placed by hand it runs straight through the middle, the way the player faces.
 */
public class RoadLineBlock extends Block {
	/**
	 * Where a line can cross a block's edge, in sixteenths along it: kept three in from the corners so the painted pair
	 * (a 6/16 wide band) never runs off the side of a block its line doesn't cross.
	 */
	public static final double[] POINTS = {3, 5.5, 8, 10.5, 13};
	/** Edge points: side * 5 + k, the sides in order north (z = 0), east (x = 16), south (z = 16), west (x = 0). */
	public static final int EDGE_POINTS = 4 * POINTS.length;
	private static final int[][] PAIRS;
	private static final int[][] INDEX = new int[EDGE_POINTS][EDGE_POINTS];

	static {
		java.util.List<int[]> pairs = new java.util.ArrayList<>();
		for (int[] row : INDEX) {
			java.util.Arrays.fill(row, -1);
		}
		for (int a = 0; a < EDGE_POINTS; a++) {
			for (int b = a + 1; b < EDGE_POINTS; b++) {
				if (a / POINTS.length != b / POINTS.length) {
					INDEX[a][b] = INDEX[b][a] = pairs.size();
					pairs.add(new int[]{a, b});
				}
			}
		}
		PAIRS = pairs.toArray(new int[0][]);
	}

	public static final IntegerProperty SEG = IntegerProperty.create("seg", 0, PAIRS.length - 1);

	public RoadLineBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(SEG, straight(Direction.Axis.Z)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SEG);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(SEG, straight(context.getHorizontalDirection().getAxis()));
	}

	/** The line straight through the middle of the block, along an axis. */
	public static int straight(Direction.Axis axis) {
		int mid = POINTS.length / 2;
		return axis == Direction.Axis.X ? seg(3 * POINTS.length + mid, POINTS.length + mid) : seg(mid, 2 * POINTS.length + mid);
	}

	/** The piece of line between two edge points, or -1 if both are on the same edge. */
	public static int seg(int a, int b) {
		return a < 0 || b < 0 || a >= EDGE_POINTS || b >= EDGE_POINTS ? -1 : INDEX[a][b];
	}

	/** The two edge points a piece of line joins. */
	public static int[] ends(int seg) {
		return PAIRS[seg];
	}

	/** An edge point's place on the block's top, in sixteenths: {x, z}. */
	public static double[] at(int point) {
		double k = POINTS[point % POINTS.length];
		return switch (point / POINTS.length) {
			case 0 -> new double[]{k, 0};
			case 1 -> new double[]{16, k};
			case 2 -> new double[]{k, 16};
			default -> new double[]{0, k};
		};
	}

	/** The edge point nearest a crossing on one side (0 north, 1 east, 2 south, 3 west), {@code along} 0..1 that edge. */
	public static int point(int side, double along) {
		double at = along * 16;
		int best = 0;
		for (int k = 1; k < POINTS.length; k++) {
			if (Math.abs(POINTS[k] - at) < Math.abs(POINTS[best] - at)) {
				best = k;
			}
		}
		return side * POINTS.length + best;
	}
}
