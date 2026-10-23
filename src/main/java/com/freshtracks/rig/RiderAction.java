package com.freshtracks.rig;

/**
 * What the rider's arms and legs are doing right now, for drawing them: the stroke of a double-pole push, a skating
 * stride, a pole planted to start a turn, a herringbone step up a rise. The rider's client works it out with the
 * physics and sends it on, packed with how many ticks it has been going, so everyone sees the same motion.
 */
public enum RiderAction {
	NONE,
	/** Both poles reach forward, plant and push back (one stroke). */
	DOUBLE_POLE,
	/** A skating stride pushing off the left ski, then the right. */
	SKATE_LEFT,
	SKATE_RIGHT,
	/** The left / right pole tapped into the snow to start a turn that way. */
	PLANT_LEFT,
	PLANT_RIGHT,
	/** Climbing a rise with the tips apart: a step with the left ski, then the right. */
	HERRINGBONE_LEFT,
	HERRINGBONE_RIGHT;

	private static final RiderAction[] VALUES = values();

	public static RiderAction byId(int id) {
		return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
	}

	/** The action and how long it has been going, in one byte for the network (ticks cap at 15). */
	public static byte pack(RiderAction action, int ticks) {
		return (byte) (action.ordinal() & 0x0F | Math.min(15, Math.max(0, ticks)) << 4);
	}

	public static RiderAction action(byte packed) {
		return byId(packed & 0x0F);
	}

	public static int ticks(byte packed) {
		return packed >> 4 & 0x0F;
	}

	public boolean herringbone() {
		return this == HERRINGBONE_LEFT || this == HERRINGBONE_RIGHT;
	}

	public boolean skate() {
		return this == SKATE_LEFT || this == SKATE_RIGHT;
	}
}
