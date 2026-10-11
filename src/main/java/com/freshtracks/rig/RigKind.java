package com.freshtracks.rig;

/** What you're standing on: two skis, or one board ridden sideways. */
public enum RigKind {
	SKIS,
	SNOWBOARD;

	public boolean isBoard() {
		return this == SNOWBOARD;
	}
}
