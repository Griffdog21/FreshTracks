package com.freshtracks.registry;

import com.freshtracks.FreshTracks;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModParticles {
	/** Snow thrown up by an edge or a landing. */
	public static final SimpleParticleType SNOW_SPRAY = register("snow_spray", false);
	/** Fine snow streaming along on the wind. */
	public static final SimpleParticleType WIND_SNOW = register("wind_snow", true);
	/** Diamond dust: ice crystals glittering in cold, clear air. */
	public static final SimpleParticleType SPARKLE = register("sparkle", true);
	/** Breath in the cold air: a little cloud in front of the face. */
	public static final SimpleParticleType BREATH = register("breath", false);

	private ModParticles() {
	}

	private static SimpleParticleType register(String name, boolean alwaysShow) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, FreshTracks.id(name), FabricParticleTypes.simple(alwaysShow));
	}

	public static void init() {
	}
}
