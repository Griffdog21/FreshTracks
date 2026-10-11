package com.freshtracks.world;

import com.freshtracks.FreshTracks;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/** Fresh Tracks biomes (defined in data/freshtracks/worldgen/biome, placed by {@code OverworldBiomeBuilderMixin}). */
public final class ModBiomes {
	public static final ResourceKey<Biome> POWDER_BOWLS = key("powder_bowls");
	public static final ResourceKey<Biome> FROSTED_GLADES = key("frosted_glades");
	public static final ResourceKey<Biome> ALPENGLOW_PEAKS = key("alpenglow_peaks");
	public static final ResourceKey<Biome> GLACIER = key("glacier");
	public static final ResourceKey<Biome> LARCH_BASIN = key("larch_basin");
	public static final ResourceKey<Biome> ASPEN_HOLLOW = key("aspen_hollow");

	/** All of the above. */
	public static final TagKey<Biome> ALPINE = TagKey.create(Registries.BIOME, FreshTracks.id("alpine"));
	/** Biomes where fresh powder keeps (it only goes when ridden or in spring slush). */
	public static final TagKey<Biome> POWDER_STASH = TagKey.create(Registries.BIOME, FreshTracks.id("powder_stash"));

	private ModBiomes() {
	}

	private static ResourceKey<Biome> key(String name) {
		return ResourceKey.create(Registries.BIOME, FreshTracks.id(name));
	}

	public enum Style {
		NONE, SNOWFIELD, PEAKS, GLACIER
	}

	public static Style style(Holder<Biome> biome) {
		if (biome.is(GLACIER)) {
			return Style.GLACIER;
		}
		if (biome.is(ALPENGLOW_PEAKS)) {
			return Style.PEAKS;
		}
		if (biome.is(POWDER_BOWLS) || biome.is(FROSTED_GLADES) || biome.is(LARCH_BASIN) || biome.is(ASPEN_HOLLOW)) {
			return Style.SNOWFIELD;
		}
		return Style.NONE;
	}
}
