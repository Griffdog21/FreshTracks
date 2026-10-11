package com.freshtracks.lift;

/** Lift options (wired to the mod config later). */
public final class LiftConfig {
	private static boolean autoClickIn = true;

	private LiftConfig() {
	}

	public static boolean autoClickIn() {
		return autoClickIn;
	}

	public static void setAutoClickIn(boolean value) {
		autoClickIn = value;
	}
}
