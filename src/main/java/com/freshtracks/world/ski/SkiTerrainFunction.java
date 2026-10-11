package com.freshtracks.world.ski;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.Interval;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * {@code {"type": "freshtracks:ski_terrain", "field": ...}}: the Ski Mountains terrain as density functions, so the
 * game's own noise generator builds it (surface rules, ores, carvers, structures all still apply). Fields:
 * <ul>
 *   <li>{@code density}: solid below the ground height, air above (the noise router's final density);</li>
 *   <li>{@code height}: the ground height itself (surface level for the material rules);</li>
 *   <li>{@code elevation}, {@code steepness}, {@code moisture}, {@code variety}: -1..1 climate values the biome
 *   source picks biomes with (continentalness, erosion, humidity, weirdness);</li>
 *   <li>{@code depth}: how far below the ground, 0 at the surface (the climate depth).</li>
 * </ul>
 */
public record SkiTerrainFunction(Field field) implements DensityFunction {
	public enum Field implements StringRepresentable {
		DENSITY("density"), HEIGHT("height"), ELEVATION("elevation"), STEEPNESS("steepness"), MOISTURE("moisture"), VARIETY("variety"), DEPTH("depth");

		public static final Codec<Field> CODEC = StringRepresentable.fromEnum(Field::values);
		private final String name;

		Field(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}

		boolean usesY() {
			return this == DENSITY || this == DEPTH;
		}
	}

	public static final MapCodec<SkiTerrainFunction> CODEC = Field.CODEC.fieldOf("field").xmap(SkiTerrainFunction::new, SkiTerrainFunction::field);

	@Override
	public DensitySampler compileSampler(DensityFunction.CompileContext context) {
		return new Sampler(SkiTerrain.of(context), field);
	}

	@Override
	public DensityFunction rewriteChildren(DfRewriteRule rule) {
		return this;
	}

	@Override
	public Interval range() {
		return switch (field) {
			case DENSITY -> Interval.of(-4f, 4f);
			case HEIGHT -> Interval.of(-64f, (float) SkiTerrain.MAX_HEIGHT);
			default -> Interval.of(-1f, 1f);
		};
	}

	@Override
	public @DensityFunction.Axes int domainAxes() {
		return field.usesY() ? DensityFunction.ALL_AXES : DensityFunction.AXIS_X | DensityFunction.AXIS_Z;
	}

	@Override
	public MapCodec<SkiTerrainFunction> codec() {
		return CODEC;
	}

	private record Sampler(SkiTerrain terrain, Field field) implements DensitySampler {
		@Override
		public void sampleVolume(SamplerContext context, DensityBuffer output, DensityVolume volume) {
			for (int z = 0; z < volume.sizeZ(); z++) {
				int blockZ = volume.blockZ(z);
				for (int x = 0; x < volume.sizeX(); x++) {
					int blockX = volume.blockX(x);
					if (!field.usesY()) {
						output.setRange(volume.indexUnchecked(x, 0, z), volume.sizeY(), sampleValue(context, blockX, 0, blockZ));
						continue;
					}
					double height = terrain.height(blockX, blockZ);
					for (int y = 0; y < volume.sizeY(); y++) {
						output.set(volume.indexUnchecked(x, y, z), fromHeight(height, volume.blockY(y)));
					}
				}
			}
		}

		@Override
		public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
			return switch (field) {
				case DENSITY, DEPTH -> fromHeight(terrain.height(blockX, blockZ), blockY);
				case HEIGHT -> (float) terrain.height(blockX, blockZ);
				case ELEVATION -> (float) terrain.elevation(blockX, blockZ);
				case STEEPNESS -> (float) terrain.steepness(blockX, blockZ);
				case MOISTURE -> (float) terrain.moisture(blockX, blockZ);
				case VARIETY -> (float) terrain.variety(blockX, blockZ);
			};
		}

		private float fromHeight(double height, int blockY) {
			if (field == Field.DEPTH) {
				return (float) Mth.clamp((height - blockY) / 64.0, -1.0, 1.0);
			}
			// solid (positive) below the ground; the surface sits exactly at the height
			return (float) Mth.clamp((height - blockY) / 16.0, -4.0, 4.0);
		}
	}
}
