package com.freshtracks.world.feature;

import com.freshtracks.registry.ModBlocks;
import com.freshtracks.world.IcicleBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** Icicles hanging under overhangs and ledges near the surface. */
public record IciclesFeature() implements Feature {
	public static final MapCodec<IciclesFeature> CODEC = MapCodec.unit(IciclesFeature::new);

	@Override
	public MapCodec<IciclesFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean placed = false;
		for (int i = 0; i < 48; i++) {
			int x = origin.getX() + random.nextInt(9) - 4;
			int z = origin.getZ() + random.nextInt(9) - 4;
			int y = origin.getY() + random.nextInt(9) - 6;
			pos.set(x, y, z);
			BlockState here = level.getBlockState(pos);
			if (!here.isAir()) {
				continue;
			}
			BlockState ceiling = level.getBlockState(pos.above());
			if (!ceiling.isFaceSturdy(level, pos.above(), net.minecraft.core.Direction.DOWN)) {
				continue;
			}
			int length = 1 + random.nextInt(3);
			for (int k = 0; k < length; k++) {
				pos.set(x, y - k, z);
				if (!level.getBlockState(pos).isAir()) {
					break;
				}
				boolean tip = k == length - 1;
				level.setBlock(pos, ModBlocks.ICICLE.defaultBlockState().setValue(IcicleBlock.TIP, tip), 2);
				placed = true;
			}
		}
		return placed;
	}
}
