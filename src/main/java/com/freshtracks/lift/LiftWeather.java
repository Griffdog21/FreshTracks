package com.freshtracks.lift;

import net.minecraft.server.level.ServerLevel;

/** How the weather slows lifts down (wind holds). Filled in by the weather system. */
public final class LiftWeather {
	public interface Provider {
		double speedFactor(ServerLevel level);
	}

	private static Provider provider = level -> 1.0;

	private LiftWeather() {
	}

	public static void install(Provider p) {
		provider = p;
	}

	public static double speedFactor(ServerLevel level) {
		return provider.speedFactor(level);
	}
}
