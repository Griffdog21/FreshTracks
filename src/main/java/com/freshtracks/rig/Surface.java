package com.freshtracks.rig;

/**
 * What the rig is sliding on. Numbers are per tick, in blocks.
 *
 * friction: flat deceleration; grip: how much of the sideways slide an edge can hold (1 = clean carve);
 * drag: multiplier on air/snow drag; sink: how far the rig sits below the surface; spray: particle amount.
 */
public enum Surface {
	GROOMED(0.0008f, 1.05f, 1.0f, 0.0f, 0.6f, true),
	PACKED(0.0011f, 0.95f, 1.0f, 0.0f, 0.8f, true),
	TRACKED(0.0010f, 1.00f, 1.0f, 0.0f, 0.85f, true),
	CHOPPED(0.0014f, 0.92f, 1.04f, 0.0f, 1.15f, true),
	SKIED_OFF(0.0009f, 0.83f, 1.02f, 0.0f, 0.75f, true),
	/** Packed snow on a cold snap: fast and chattery, hard to hold an edge. */
	ICE_CRUST(0.0007f, 0.62f, 0.95f, 0.0f, 0.4f, true),
	POWDER(0.0022f, 0.80f, 1.30f, 0.08f, 1.6f, true),
	DEEP_POWDER(0.0035f, 0.70f, 1.55f, 0.18f, 2.2f, true),
	SLUSH(0.0040f, 0.85f, 1.25f, 0.05f, 1.0f, true),
	ICE(0.0004f, 0.32f, 0.9f, 0.0f, 0.2f, false),
	WATER(0.0120f, 0.30f, 2.5f, 0.10f, 1.2f, false),
	DIRT(0.0280f, 0.80f, 1.2f, 0.0f, 0.5f, false),
	SAND(0.0400f, 0.70f, 1.4f, 0.04f, 0.6f, false),
	ROCK(0.0350f, 0.55f, 1.1f, 0.0f, 0.3f, false),
	WOOD(0.0150f, 0.65f, 1.0f, 0.0f, 0.1f, false),
	HARD(0.0200f, 0.60f, 1.0f, 0.0f, 0.1f, false),
	PARK_SLIDE(0.0003f, 0.65f, 0.70f, 0f, 0f, false),
	AIR(0f, 0f, 1f, 0f, 0f, false);

	public final float friction;
	public final float grip;
	public final float drag;
	public final float sink;
	public final float spray;
	public final boolean snowy;

	Surface(float friction, float grip, float drag, float sink, float spray, boolean snowy) {
		this.friction = friction;
		this.grip = grip;
		this.drag = drag;
		this.sink = sink;
		this.spray = spray;
		this.snowy = snowy;
	}

	public boolean isPowder() {
		return this == POWDER || this == DEEP_POWDER;
	}

	/** Rocks, dirt and sand scratch the base. */
	public boolean wearsBase() {
		return this == ROCK || this == DIRT || this == SAND || this == HARD;
	}
}
