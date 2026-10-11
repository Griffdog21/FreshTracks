package com.freshtracks.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A wind lip: snow the wind has sculpted into a natural kicker on a slope. It ramps up along the fall line and
 * ends in a sharp lip, so you can launch off it.
 */
public record WindLipFeature() implements Feature {
	public static final MapCodec<WindLipFeature> CODEC = MapCodec.unit(WindLipFeature::new);

	@Override
	public MapCodec<WindLipFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int x0 = origin.getX();
		int z0 = origin.getZ();
		int h0 = AlpineSurfaceFeature.groundTop(level, x0, z0);
		// Which way is downhill? Compare a few blocks out in each direction.
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		int best = -1;
		int bestDrop = 1;
		for (int i = 0; i < dirs.length; i++) {
			int h = AlpineSurfaceFeature.groundTop(level, x0 + dirs[i][0] * 6, z0 + dirs[i][1] * 6);
			int drop = h0 - h;
			if (drop > bestDrop && drop < 8) {
				bestDrop = drop;
				best = i;
			}
		}
		if (best < 0) {
			return false;
		}
		int fx = dirs[best][0];
		int fz = dirs[best][1];
		int length = 5 + random.nextInt(3);
		double lipHeight = 1.6 + random.nextDouble() * 1.2;
		int width = 2 + random.nextInt(2);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < length; i++) {
			double t = (i + 1) / (double) length;
			double rise = lipHeight * (t * t * 0.55 + t * 0.45);
			for (int w = -width; w <= width; w++) {
				int x = x0 + fx * i + (fz != 0 ? w : 0);
				int z = z0 + fz * i + (fx != 0 ? w : 0);
				int ground = AlpineSurfaceFeature.groundTop(level, x, z);
				double surface = h0 - (i * bestDrop / 6.0) + rise;
				if (surface <= ground) {
					continue;
				}
				int full = (int) Math.floor(surface);
				for (int y = ground; y < full; y++) {
					pos.set(x, y, z);
					if (level.getBlockState(pos).isAir() || level.getBlockState(pos).canBeReplaced()) {
						level.setBlock(pos, Blocks.SNOW_BLOCK.defaultBlockState(), 2);
					}
				}
				int layers = (int) Math.round((surface - full) * 8);
				pos.set(x, full, z);
				if (layers > 0 && layers < 8 && level.getBlockState(pos).isAir()) {
					level.setBlock(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers), 2);
				}
			}
		}
		return true;
	}
}
