package com.freshtracks.weather;

import com.freshtracks.rig.Surface;

/**
 * What the weather is doing right now, as seen by this side (the client fills it from the server's sync packet,
 * the server sets it directly). Read by the ski physics: wind pushes riders, cold snaps ice up the groomers,
 * spring days turn everything to slush.
 */
public final class SnowConditions {
	private static volatile SnowEvent event = SnowEvent.CLEAR;
	/** Wind as a horizontal vector, blocks/tick worth of "push" at full strength scaled 0..1. */
	private static volatile float windX;
	private static volatile float windZ;

	private SnowConditions() {
	}

	public static void set(SnowEvent e, float wx, float wz) {
		event = e;
		windX = wx;
		windZ = wz;
	}

	public static SnowEvent event() {
		return event;
	}

	public static float windX() {
		return windX;
	}

	public static float windZ() {
		return windZ;
	}

	public static float windStrength() {
		return (float) Math.sqrt(windX * windX + windZ * windZ);
	}

	/** How the day's conditions change the snow under your skis. */
	public static Surface adjust(Surface surface) {
		switch (event.surface) {
			case ICY -> {
				if (surface == Surface.PACKED || surface == Surface.GROOMED || surface == Surface.TRACKED || surface == Surface.CHOPPED || surface == Surface.SKIED_OFF) {
					return Surface.ICE_CRUST;
				}
			}
			case SLUSHY -> {
				if (surface.snowy && surface != Surface.SLUSH) {
					return Surface.SLUSH;
				}
			}
			default -> {
			}
		}
		return surface;
	}
}
