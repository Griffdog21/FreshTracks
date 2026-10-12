package com.freshtracks.lift;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * The paint on a lift's terminals. Real lifts are easy to date by it: the grey ribbed housings with a dark glass roof are
 * the 2000s Poma/Leitner-Poma "Omega" terminals, charcoal is the current Leitner-Poma look, silver the Doppelmayr one, and
 * the old fixed-grip chairs wear whatever colour the resort liked. New entries go at the end: the ordinal is sent over the
 * network.
 */
public enum Livery implements StringRepresentable {
	/** Picked from the lift's name, so every lift a player builds doesn't look the same. */
	AUTO("auto"),
	GRAY("gray"),
	CHARCOAL("charcoal"),
	SILVER("silver"),
	GREEN("green"),
	RED("red"),
	BLUE("blue");

	public static final Codec<Livery> CODEC = StringRepresentable.fromEnum(Livery::values);

	private final String name;

	Livery(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public static Livery byId(int id) {
		Livery[] v = values();
		return id >= 0 && id < v.length ? v[id] : AUTO;
	}

	public static Livery byName(String name) {
		for (Livery l : values()) {
			if (l.name.equalsIgnoreCase(name)) {
				return l;
			}
		}
		return AUTO;
	}

	/** The colour a lift actually wears: AUTO becomes one that suits the lift type, the same every time for a name. */
	public static Livery resolve(Livery livery, ChairStyle style, String liftName) {
		if (livery != AUTO) {
			return livery;
		}
		int h = Math.floorMod(liftName.hashCode() * 31 + 7, 1000);
		if (style.detachable() || style.cabin() || style.pulse()) {
			Livery[] pick = {GRAY, CHARCOAL, SILVER};
			return pick[h % pick.length];
		}
		Livery[] pick = {GREEN, BLUE, RED, GRAY};
		return pick[h % pick.length];
	}
}
