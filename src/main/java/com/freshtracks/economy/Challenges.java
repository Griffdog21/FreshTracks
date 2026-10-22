package com.freshtracks.economy;

/** Three daily challenges, the same for everyone on a given day, each worth a Ski Bucks bounty. */
public final class Challenges {
	public static final int PER_DAY = 3;
	/** Paid once all three are done. */
	public static final int ALL_BONUS = 20;

	public enum Kind {
		VERTICAL("Ski %s blocks of vertical", 1500, 15),
		BIG_TRICK("Land a trick worth %s+ points", 2000, 15),
		TRICKS("Land %s tricks", 15, 10),
		HANG_TIME("Catch %s seconds of air in one jump", 2, 12),
		LIFTS("Ride %s lifts", 6, 10),
		SPEED("Hit %s km/h", 70, 12),
		POINTS("Score %s trick points", 12000, 15),
		RIDE_TIME("Ride for %s minutes", 25, 10),
		BUTTERS("Land %s presses or butters", 5, 12);

		public final String text;
		public final int target;
		public final int reward;

		Kind(String text, int target, int reward) {
			this.text = text;
			this.target = target;
			this.reward = reward;
		}

		public String describe() {
			return String.format(text, SkiBucks.format(target));
		}

		/** Progress values are stored scaled for kinds measured in finer units than their target. */
		public int scale() {
			return switch (this) {
				case HANG_TIME -> 20;
				case RIDE_TIME -> 1200;
				default -> 1;
			};
		}
	}

	private Challenges() {
	}

	/** Today's three, picked from the date so every skier on the server chases the same ones. */
	public static Kind[] today(String day) {
		Kind[] all = Kind.values();
		long h = day.hashCode() * 0x9E3779B97F4A7C15L;
		Kind[] out = new Kind[PER_DAY];
		int n = 0;
		while (n < PER_DAY) {
			h ^= h >>> 29;
			h *= 0xBF58476D1CE4E5B9L;
			Kind k = all[(int) Math.floorMod(h >>> 33, all.length)];
			boolean dup = false;
			for (int i = 0; i < n; i++) {
				dup |= out[i] == k;
			}
			if (!dup) {
				out[n++] = k;
			}
		}
		return out;
	}
}
