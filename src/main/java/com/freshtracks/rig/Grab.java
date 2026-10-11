package com.freshtracks.rig;

/** Grabs, picked by the direction you hold when you start the grab. */
public enum Grab {
	NONE("", ""),
	// steer 0 / left / right / forward / back
	PLAIN("Safety", "Indy"),
	LEFT("Mute", "Melon"),
	RIGHT("Japan", "Mute"),
	FORWARD("Tip", "Nose"),
	BACK("Tail", "Tail");

	private final String skiName;
	private final String boardName;

	Grab(String skiName, String boardName) {
		this.skiName = skiName;
		this.boardName = boardName;
	}

	public String displayName(RigKind kind, int heldTicks) {
		String base = kind.isBoard() ? boardName : skiName;
		if (heldTicks > 24) {
			if (kind.isBoard() && this == LEFT) {
				return "Method";
			}
			return "Tweaked " + base;
		}
		return base;
	}

	public static Grab fromInput(RiderInput input) {
		if (input.back()) {
			return BACK;
		}
		if (input.forward()) {
			return FORWARD;
		}
		if (input.left()) {
			return LEFT;
		}
		if (input.right()) {
			return RIGHT;
		}
		return PLAIN;
	}

	public static Grab byId(int id) {
		Grab[] values = values();
		return id >= 0 && id < values.length ? values[id] : NONE;
	}
}
