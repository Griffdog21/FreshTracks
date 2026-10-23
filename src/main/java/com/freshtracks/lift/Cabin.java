package com.freshtracks.lift;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * A gondola's cabin paint. AUTO is the usual white cabin with a red band (the Sky Cab's are its own four colours);
 * BLACK is the black cabin with a white swoosh many newer gondolas wear. New entries go at the end: the ordinal is sent
 * over the network.
 */
public enum Cabin implements StringRepresentable {
	AUTO("auto"),
	BLACK("black");

	public static final Codec<Cabin> CODEC = StringRepresentable.fromEnum(Cabin::values);

	private final String name;

	Cabin(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public static Cabin byId(int id) {
		Cabin[] v = values();
		return id >= 0 && id < v.length ? v[id] : AUTO;
	}

	public static Cabin byName(String name) {
		for (Cabin c : values()) {
			if (c.name.equalsIgnoreCase(name)) {
				return c;
			}
		}
		return AUTO;
	}
}
