package com.freshtracks.rig;

/** One tick of the rider's controls, read on the rider's own client. */
public record RiderInput(boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean brake, boolean grab) {
	public static final RiderInput NONE = new RiderInput(false, false, false, false, false, false, false);

	public int steer() {
		return (right ? 1 : 0) - (left ? 1 : 0);
	}

	public int pitch() {
		return (forward ? 1 : 0) - (back ? 1 : 0);
	}
}
