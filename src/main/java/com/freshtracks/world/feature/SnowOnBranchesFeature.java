package com.freshtracks.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/** Snow-loaded trees: a cap of snow on every branch that has open air above it, not just the treetops. */
public record SnowOnBranchesFeature() implements Feature {
	public static final MapCodec<SnowOnBranchesFeature> CODEC = MapCodec.unit(SnowOnBranchesFeature::new);

	@Override
	public MapCodec<SnowOnBranchesFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int baseX = origin.getX() & ~15;
		int baseZ = origin.getZ() & ~15;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
		boolean placed = false;
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = baseX + dx;
				int z = baseZ + dz;
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
				pos.set(x, top - 1, z);
				if (!level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel())) {
					continue;
				}
				for (int y = top - 1; y > top - 28; y--) {
					pos.set(x, y, z);
					BlockState state = level.getBlockState(pos);
					if (!state.is(BlockTags.LEAVES)) {
						continue;
					}
					above.set(x, y + 1, z);
					if (!level.getBlockState(above).isAir()) {
						continue;
					}
					BlockState snow = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, random.nextInt(4) == 0 ? 2 : 1);
					if (snow.canSurvive(level, above)) {
						level.setBlock(above, snow, 2);
						placed = true;
					}
				}
			}
		}
		return placed;
	}
}
