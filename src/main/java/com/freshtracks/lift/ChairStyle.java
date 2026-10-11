package com.freshtracks.lift;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * Lift types. Snowmass alone has doubles, quads, six-packs, a gondola and a surface lift up the Cirque.
 * New types go at the end: the ordinal is what goes over the network.
 */
public enum ChairStyle implements StringRepresentable {
	DOUBLE("double", 2, 2.6, 1.1, Grip.FIXED, 0.15, 8.0),
	TRIPLE("triple", 3, 2.6, 1.6, Grip.FIXED, 0.15, 8.0),
	QUAD("quad", 4, 2.6, 2.1, Grip.FIXED, 0.15, 9.0),
	SIX("six", 6, 2.7, 3.1, Grip.DETACHABLE, 0.3, 12.0),
	GONDOLA("gondola", 6, 3.0, 1.9, Grip.DETACHABLE, 0.3, 16.0),
	QUAD_EXPRESS("quad_express", 4, 2.6, 2.1, Grip.DETACHABLE, 0.3, 11.0),
	T_BAR("t_bar", 2, LiftLine.TERMINAL_ROPE, 1.6, Grip.SURFACE, 0.15, 10.0),
	PLATTER("platter", 1, LiftLine.TERMINAL_ROPE, 0.0, Grip.SURFACE, 0.15, 10.0),
	/** Eight-passenger cabins (Elk Camp Gondola). */
	GONDOLA_8("gondola_8", 8, 3.0, 2.3, Grip.DETACHABLE, 0.28, 30.0),
	/** Groups of cabins on a rope that slows to a crawl whenever a group is in a station (Sky Cab). */
	PULSE_GONDOLA("pulse_gondola", 6, 3.0, 1.9, Grip.PULSE, 0.24, 8.0);

	/**
	 * How carriers hold the rope. Fixed-grip chairs never let go, so they load at rope speed. Detachable carriers
	 * unclip in the terminals and crawl past the loading spot. Surface lifts pull riders up the snow on their skis.
	 */
	public enum Grip {
		FIXED, DETACHABLE, SURFACE, PULSE
	}

	public static final Codec<ChairStyle> CODEC = StringRepresentable.fromEnum(ChairStyle::values);

	private final String name;
	public final int seats;
	/** How far below the rope the seat (rider's feet line) hangs. Surface lifts: down to the snow at the terminals. */
	public final double hanger;
	/** Seat bench width in blocks. */
	public final double width;
	public final Grip grip;
	/** Rope speed a new lift of this type starts with, blocks per tick. */
	public final double defaultSpeed;
	/** Distance between carriers on the line, blocks. */
	public final double defaultSpacing;

	ChairStyle(String name, int seats, double hanger, double width, Grip grip, double defaultSpeed, double defaultSpacing) {
		this.name = name;
		this.seats = seats;
		this.hanger = hanger;
		this.width = width;
		this.grip = grip;
		this.defaultSpeed = defaultSpeed;
		this.defaultSpacing = defaultSpacing;
	}

	public boolean detachable() {
		return grip == Grip.DETACHABLE;
	}

	public boolean surface() {
		return grip == Grip.SURFACE;
	}

	public boolean pulse() {
		return grip == Grip.PULSE;
	}

	/** Enclosed cabins rather than open chairs. */
	public boolean cabin() {
		return this == GONDOLA || this == GONDOLA_8 || this == PULSE_GONDOLA;
	}

	public double seatOffset(int seat) {
		if (cabin()) {
			// two benches facing each other: this is the spread along a bench
			int perBench = Math.max(1, seats / 2);
			int col = seat % perBench;
			return (col - (perBench - 1) / 2.0) * Math.min(0.55, width / perBench);
		}
		if (seats <= 1) {
			return 0;
		}
		double step = width / seats;
		return -width / 2 + step * (seat + 0.5);
	}

	public Component displayName() {
		return Component.translatable("lift_style.freshtracks." + name);
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public static ChairStyle byId(int id) {
		ChairStyle[] v = values();
		return id >= 0 && id < v.length ? v[id] : QUAD;
	}

	public static ChairStyle byName(String name) {
		for (ChairStyle style : values()) {
			if (style.name.equalsIgnoreCase(name)) {
				return style;
			}
		}
		return QUAD;
	}

	public ChairStyle next() {
		ChairStyle[] v = values();
		return v[(ordinal() + 1) % v.length];
	}
}
