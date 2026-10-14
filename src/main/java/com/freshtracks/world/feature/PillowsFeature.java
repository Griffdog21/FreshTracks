package com.freshtracks.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A pillow line: a few snow-capped boulders stepping down the hill, the kind you drop off one after another.
 * Placed at the surface; each pillow is a rough rock blob under a thick, rounded snow cap.
 */
public record PillowsFeature() implements Feature {
	public static final MapCodec<PillowsFeature> CODEC = MapCodec.unit(PillowsFeature::new);

	@Override
	public MapCodec<PillowsFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int count = 2 + random.nextInt(2);
		double gx = AlpineSurfaceFeature.groundTop(level, origin.getX()+4, origin.getZ()) - AlpineSurfaceFeature.groundTop(level, origin.getX()-4, origin.getZ());
        double gz = AlpineSurfaceFeature.groundTop(level, origin.getX(), origin.getZ()+4) - AlpineSurfaceFeature.groundTop(level, origin.getX(), origin.getZ()-4);
        double slope = Math.hypot(gx,gz);
        if(slope<1||slope>12)return false;
        double dirX=-gx/slope,dirZ=-gz/slope;
		BlockPos centre = origin.offset((int)Math.round(-dirX*7),0,(int)Math.round(-dirZ*7));
		boolean placed = false;
		for (int i = 0; i < count; i++) {
			int ground = AlpineSurfaceFeature.groundTop(level, centre.getX(), centre.getZ());
			BlockPos base = new BlockPos(centre.getX(), ground, centre.getZ());
			placed |= pillow(level, base, 2 + random.nextInt(2), random);
			centre = centre.offset((int)Math.round(dirX * (7 + random.nextInt(3))), 0, (int)Math.round(dirZ * (7 + random.nextInt(3))));
		}
		return placed;
	}

	private static boolean pillow(WorldGenLevel level, BlockPos base, int radius, RandomSource random) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState[] rocks = {Blocks.STONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(), Blocks.GRANITE.defaultBlockState()};
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > radius + 0.4) {
					continue;
				}
				int height = (int) Math.round(Math.sqrt(Math.max(0, 1 - d*d/((radius+.5)*(radius+.5)))) * (1.0 + radius*.45)) + 1;
				int ground = AlpineSurfaceFeature.groundTop(level, base.getX() + dx, base.getZ() + dz);
				for (int dy = 0; dy < height; dy++) {
					pos.set(base.getX() + dx, ground + dy, base.getZ() + dz);
					BlockState here = level.getBlockState(pos);
					if (!here.isAir() && !here.canBeReplaced()) {
						continue;
					}
					boolean cap = dy >= height - (radius >= 3 ? 2 : 1);
					level.setBlock(pos, cap ? Blocks.SNOW_BLOCK.defaultBlockState() : rocks[random.nextInt(rocks.length)], 2);
				}
				pos.set(base.getX() + dx, ground + height, base.getZ() + dz);
				if (level.getBlockState(pos).isAir()) {
					int layers = d < radius - 0.3 ? 4 + random.nextInt(3) : 2;
					level.setBlock(pos, com.freshtracks.registry.ModBlocks.FRESH_POWDER.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers), 2);
				}
			}
		}
		return true;
	}
}
