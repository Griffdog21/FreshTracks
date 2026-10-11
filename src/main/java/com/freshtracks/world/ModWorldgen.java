package com.freshtracks.world;

import com.freshtracks.FreshTracks;
import com.freshtracks.world.feature.AlpineSurfaceFeature;
import com.freshtracks.world.feature.IciclesFeature;
import com.freshtracks.world.feature.PillowsFeature;
import com.freshtracks.world.feature.SnowOnBranchesFeature;
import com.freshtracks.world.feature.WindLipFeature;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.Feature;

/** Feature types (the code) for Fresh Tracks worldgen. The configured features live in data/freshtracks/worldgen. */
public final class ModWorldgen {
	public static final ResourceKey<Feature> LARCH = ResourceKey.create(Registries.FEATURE, FreshTracks.id("larch"));
	public static final ResourceKey<Feature> ASPEN = ResourceKey.create(Registries.FEATURE, FreshTracks.id("aspen"));

	private ModWorldgen() {
	}

	public static void init() {
		register("alpine_surface", AlpineSurfaceFeature.CODEC);
		register("snow_on_branches", SnowOnBranchesFeature.CODEC);
		register("pillows", PillowsFeature.CODEC);
		register("wind_lip", WindLipFeature.CODEC);
		register("icicles", IciclesFeature.CODEC);
		// The Ski Mountains world type's terrain, as density functions for the game's noise generator.
		Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, FreshTracks.id("ski_terrain"), com.freshtracks.world.ski.SkiTerrainFunction.CODEC);
	}

	private static void register(String name, MapCodec<? extends Feature> codec) {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, FreshTracks.id(name), codec);
	}
}
