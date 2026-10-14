package com.freshtracks.world.feature;

import com.freshtracks.registry.ModBlocks;
import com.freshtracks.world.ModBiomes;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Turns the block staircases of Fresh Tracks biomes into snow slopes. Each one-block step gets a wedge of snow layers
 * in front of it that tapers away at the slope's own steepness, so a hillside reads (and rides) as one smooth
 * surface instead of stairs. Snow is only ever added on top of the ground, never cut away, so cliffs and steps of
 * more than a block stay as they are. (The ground itself comes from the material rules.)
 */
public record AlpineSurfaceFeature() implements Feature {
	public static final MapCodec<AlpineSurfaceFeature> CODEC = MapCodec.unit(AlpineSurfaceFeature::new);
	/** How far a wedge of snow can reach from the step that makes it, blocks. Gentler slopes than 1 in REACH keep a small lip. */
	private static final int REACH = 8;
	/** Columns either side used to measure how steep the slope is. */
	private static final int SLOPE_SPAN = 3;

	@Override
	public MapCodec<AlpineSurfaceFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int baseX = origin.getX() & ~15;
		int baseZ = origin.getZ() & ~15;
		int size = 16 + 2 * REACH;
		int[][] heights = new int[size][size];
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				heights[i][j] = groundTop(level, baseX + i - REACH, baseZ + j - REACH);
			}
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
		boolean placed = false;
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int i = dx + REACH;
				int j = dz + REACH;
				int x = baseX + dx;
				int z = baseZ + dz;
				int top = heights[i][j];
				pos.set(x, top - 1, z);
				Holder<Biome> biome = level.getBiome(pos);
				ModBiomes.Style style = ModBiomes.style(biome);
				if (style == ModBiomes.Style.NONE) {
					continue;
				}
				double fill = wedge(heights, i, j);
				int layers = Mth.clamp((int) Math.round(fill * 8), 0, 8);
				// The bowls and glades hold a stash of fresh powder, deepest where it drifts in.
				boolean stash = style != ModBiomes.Style.GLACIER && biome.is(ModBiomes.POWDER_STASH);
				if (stash) {
					layers = Mth.clamp(layers + 3, 3, 8);
				}
				if (layers == 0) {
					continue; // leave the usual thin snow cover
				}
				pos.set(x, top, z);
				below.set(x, top - 1, z);
				BlockState here = level.getBlockState(pos);
				boolean free = here.isAir() || here.getBlock() instanceof SnowLayerBlock
					|| here.canBeReplaced() && here.getFluidState().isEmpty() && !here.is(BlockTags.LOGS);
				if (!free || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
					continue;
				}
				BlockState snow = stash ? ModBlocks.FRESH_POWDER.defaultBlockState() : Blocks.SNOW.defaultBlockState();
				level.setBlock(pos, snow.setValue(SnowLayerBlock.LAYERS, layers), 2);
				placed = true;
			}
		}
		return com.freshtracks.world.ski.GeneratedParks.decorate(level, baseX, baseZ) || placed;
	}

	/**
	 * How much snow (0..1 blocks) to add on column (i, j): the highest of the wedges that higher columns nearby cast
	 * downhill, each sloping away at the hillside's own gradient.
	 */
	private static double wedge(int[][] h, int i, int j) {
		int top = h[i][j];
		int n = h.length;
		double gx = (h[Math.min(n - 1, i + SLOPE_SPAN)][j] - h[Math.max(0, i - SLOPE_SPAN)][j]) / (2.0 * SLOPE_SPAN);
		double gz = (h[i][Math.min(n - 1, j + SLOPE_SPAN)] - h[i][Math.max(0, j - SLOPE_SPAN)]) / (2.0 * SLOPE_SPAN);
		double slope = Math.max(Math.sqrt(gx * gx + gz * gz), 1.0 / REACH);
		if (slope > 1.1) {
			return 0; // cliffs: every column is its own step, there's nothing to smooth
		}
		double best = top;
		for (int di = -REACH; di <= REACH; di++) {
			for (int dj = -REACH; dj <= REACH; dj++) {
				int hi = h[Mth.clamp(i + di, 0, n - 1)][Mth.clamp(j + dj, 0, n - 1)];
				if (hi <= top) {
					continue;
				}
				double d = Math.sqrt(di * di + dj * dj);
				best = Math.max(best, hi - slope * d);
			}
		}
		return Math.min(1.0, best - top);
	}

	/** y just above the real ground at (x, z): looks through tree trunks, plants and snow layers. */
	public static int groundTop(WorldGenLevel level, int x, int z) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y - 1, z);
		int floor = level.getMinY();
		while (pos.getY() > floor) {
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.canBeReplaced() || state.getBlock() instanceof SnowLayerBlock) {
				pos.move(0, -1, 0);
				continue;
			}
			break;
		}
		return pos.getY() + 1;
	}
}
