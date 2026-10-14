package com.freshtracks.rig;

import java.util.ArrayList;
import java.util.List;

/**
 * Names and scores what happened in the air. Lives on the rider's client; landed tricks are reported to the
 * server for XP and for the shout-out other players see.
 */
public final class TrickTracker {
	public static final int COMBO_WINDOW = 70;
	private static final int[] SPIN_POINTS = {0, 100, 250, 450, 700, 1000, 1350, 1700, 2100, 2600};

	public enum Landing {
		STOMPED("Stomped!", 1.3f),
		CLEAN("Clean", 1.0f),
		SKETCHY("Sketchy", 0.5f);

		public final String label;
		public final float multiplier;

		Landing(String label, float multiplier) {
			this.label = label;
			this.multiplier = multiplier;
		}
	}

	public record Result(String name, int points, Landing landing, int combo, int airTicks) {
	}

	// Current air
	public float spin;
	public float flip;
	public int airTicks;
	public int grabTicks;
	public Grab grab = Grab.NONE;
	public Grab longestGrab = Grab.NONE;
	public int longestGrabTicks;
	public boolean switchTakeoff;
	public double takeoffY;
	public double peakY;

	// Combo across landings
	public int combo;
	public int comboTimer;
	public int runScore;
	public int bestTrickPoints;

	// Powder turns linked together
	public static final int POWDER_WINDOW = 80;
	public int powderChain;
	public int powderTimer;

	public void takeoff(boolean switchStance, double y) {
		spin = 0;
		flip = 0;
		airTicks = 0;
		grabTicks = 0;
		grab = Grab.NONE;
		longestGrab = Grab.NONE;
		longestGrabTicks = 0;
		switchTakeoff = switchStance;
		takeoffY = y;
		peakY = y;
	}

	public void tickAir(double y) {
		airTicks++;
		peakY = Math.max(peakY, y);
		if (grab != Grab.NONE) {
			grabTicks++;
			if (grabTicks > longestGrabTicks) {
				longestGrabTicks = grabTicks;
				longestGrab = grab;
			}
		}
	}

	public void tickGround() {
		if (comboTimer > 0 && --comboTimer == 0) {
			combo = 0;
		}
		if (powderTimer > 0 && --powderTimer == 0) {
			powderChain = 0;
		}
	}

	/** One linked turn in powder; the chain grows while you keep linking them (first tracks score half again). */
	public Result powderTurn(boolean fresh) {
		powderChain = powderTimer > 0 ? powderChain + 1 : 1;
		powderTimer = POWDER_WINDOW;
		int points = Math.round(40 * Math.min(powderChain, 8) * (fresh ? 1.5f : 1f));
		runScore += points;
		String name = (fresh ? "First Tracks" : "Powder Turn") + (powderChain > 1 ? " x" + powderChain : "");
		return new Result(name, points, Landing.CLEAN, 0, 0);
	}

	public void startGrab(Grab g) {
		if (grab != g) {
			grab = g;
			grabTicks = 0;
		}
	}

	/** Award travel and rotation, never time spent standing still on a feature. */
	public Result groundTrick(String name, double distance, float rotation, int ticks) {
		int halfTurns = (int) (Math.abs(rotation) / 180f);
		if (halfTurns > 0) name += " " + halfTurns * 180;
		combo = comboTimer > 0 ? combo + 1 : 0;
		comboTimer = COMBO_WINDOW;
		int base = 60 + (int) (Math.min(distance, 40) * 18) + Math.min(halfTurns, 8) * 100;
		int points = Math.round(base * Math.min(1f + combo * 0.5f, 4f));
		runScore += points;
		bestTrickPoints = Math.max(bestTrickPoints, points);
		return new Result(name, points, Landing.CLEAN, combo, 0);
	}

	public void releaseGrab() {
		grab = Grab.NONE;
		grabTicks = 0;
	}

	public static int spinStep(float spinDegrees) {
		return Math.round(Math.abs(spinDegrees) / 180f);
	}

	public static int flipCount(float flipDegrees) {
		return Math.round(Math.abs(flipDegrees) / 360f);
	}

	/** True if the rotation so far can be landed (pointing forward or switch, right side up). */
	public static boolean landable(float spinDegrees, float flipDegrees, float spinTolerance, float flipTolerance) {
		float spinRem = Math.abs(spinDegrees) % 180f;
		float spinErr = Math.min(spinRem, 180f - spinRem);
		float flipRem = Math.abs(flipDegrees) % 360f;
		float flipErr = Math.min(flipRem, 360f - flipRem);
		return spinErr <= spinTolerance && flipErr <= flipTolerance;
	}

	/** Builds the name and score for a landed air. Returns null for air too small to count. */
	public Result land(RigKind kind, Landing landing) {
		int spins = spinStep(spin);
		int flips = flipCount(flip);
		boolean hasGrab = longestGrab != Grab.NONE && longestGrabTicks >= 4;
		boolean bigAir = airTicks >= 26;
		if (spins == 0 && flips == 0 && !hasGrab && !bigAir) {
			return null;
		}

		List<String> parts = new ArrayList<>();
		int points = 0;
		int degrees = spins * 180;
		if (switchTakeoff && (spins > 0 || flips > 0)) {
			parts.add("Switch");
		}
		if (flips > 0) {
			boolean back = flip < 0;
			String prefix = flips == 2 ? "Double " : flips >= 3 ? "Triple " : "";
			if (degrees >= 360) {
				String family = back ? (degrees >= 540 ? "Cork" : "Rodeo") : "Misty";
				parts.add(prefix + family + " " + degrees);
			} else {
				parts.add(prefix + (back ? "Backflip" : "Frontflip"));
				if (degrees == 180) {
					parts.add("180");
				}
			}
			points += 500 * flips + (flips > 1 ? 300 * (flips - 1) : 0);
		} else if (degrees > 0) {
			if (kind.isBoard()) {
				parts.add((spin > 0 ? "Frontside " : "Backside ") + degrees);
			} else {
				parts.add((spin > 0 ? "Right " : "Left ") + degrees);
			}
		}
		points += SPIN_POINTS[Math.min(spins, SPIN_POINTS.length - 1)] + Math.max(0, spins - (SPIN_POINTS.length - 1)) * 500;

		if (hasGrab) {
			parts.add(longestGrab.displayName(kind, longestGrabTicks));
			points += 80 + Math.min(longestGrabTicks * 6, 400);
		}
		if (parts.isEmpty()) {
			parts.add(airTicks >= 40 ? "Huge Air" : "Big Air");
		}
		points += Math.max(0, airTicks - 10) * 4;
		if (switchTakeoff) {
			points = Math.round(points * 1.25f);
		}

		if (comboTimer > 0) {
			combo++;
		} else {
			combo = 0;
		}
		comboTimer = COMBO_WINDOW;
		float comboMult = Math.min(1f + 0.5f * combo, 4f);
		points = Math.round(points * landing.multiplier * comboMult);
		runScore += points;
		bestTrickPoints = Math.max(bestTrickPoints, points);
		return new Result(String.join(" ", parts), points, landing, combo, airTicks);
	}

	public void bail() {
		combo = 0;
		comboTimer = 0;
		powderChain = 0;
		powderTimer = 0;
	}

	public void resetRun() {
		runScore = 0;
		combo = 0;
		comboTimer = 0;
	}
}
