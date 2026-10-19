package com.freshtracks.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One skier's record on this world: earnings, days on the mountain, streak, today's challenges and collection. */
public final class SkierProfile {
	public static final Codec<SkierProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.LONG.optionalFieldOf("lifetime", 0L).forGetter(p -> p.lifetimeEarned),
		Codec.INT.optionalFieldOf("days", 0).forGetter(p -> p.daysSkied),
		Codec.STRING.optionalFieldOf("today", "").forGetter(p -> p.today),
		Codec.INT.optionalFieldOf("ride_ticks_today", 0).forGetter(p -> p.rideTicksToday),
		Codec.BOOL.optionalFieldOf("today_counted", false).forGetter(p -> p.todayCounted),
		Codec.STRING.optionalFieldOf("last_day", "").forGetter(p -> p.lastDay),
		Codec.INT.optionalFieldOf("streak", 0).forGetter(p -> p.streak),
		Codec.INT.optionalFieldOf("best_streak", 0).forGetter(p -> p.bestStreak),
		Codec.STRING.optionalFieldOf("bonus_day", "").forGetter(p -> p.bonusDay),
		Codec.STRING.optionalFieldOf("challenge_day", "").forGetter(p -> p.challengeDay),
		Codec.INT.listOf().optionalFieldOf("challenge_progress", List.of()).forGetter(p -> toList(p.progress)),
		Codec.INT.optionalFieldOf("challenges_done", 0).forGetter(p -> p.challengesDone),
		Codec.STRING.listOf().optionalFieldOf("owned", List.of()).forGetter(p -> new ArrayList<>(p.owned)),
		Codec.DOUBLE.optionalFieldOf("vertical_carry", 0.0).forGetter(p -> p.verticalCarry),
		Codec.LONG.optionalFieldOf("vertical_total", 0L).forGetter(p -> p.verticalTotal),
		Codec.INT.optionalFieldOf("tickets_found", 0).forGetter(p -> p.ticketsFound)
	).apply(i, SkierProfile::new));

	public long lifetimeEarned;
	public int daysSkied;
	/** The day (yyyy-mm-dd, server time) the ride-time counter is for. */
	public String today = "";
	public int rideTicksToday;
	public boolean todayCounted;
	/** The last day that counted, for the streak. */
	public String lastDay = "";
	public int streak;
	public int bestStreak;
	/** The day the first-chair bonus was last paid. */
	public String bonusDay = "";
	public String challengeDay = "";
	public int[] progress = new int[Challenges.PER_DAY];
	/** Bit per challenge completed today; bit 7: the all-three bonus. */
	public int challengesDone;
	/** Unique catalog items this skier has bought (one each, ever). */
	public final Set<String> owned = new HashSet<>();
	public double verticalCarry;
	public long verticalTotal;
	public int ticketsFound;
	/** Not saved: rate limits for messages. */
	transient long lastPayMessage;
	/** Trick earnings today (the daily cap); not saved, a restart starts the count again. */
	transient String trickDay = "";
	transient int trickBucksToday;
	transient boolean toldTrickCap;
	transient int pendingVertical;

	public SkierProfile() {
	}

	private SkierProfile(long lifetime, int days, String today, int rideTicks, boolean counted, String lastDay, int streak,
		int bestStreak, String bonusDay, String challengeDay, List<Integer> progress, int done, List<String> owned,
		double verticalCarry, long verticalTotal, int tickets) {
		this.lifetimeEarned = lifetime;
		this.daysSkied = days;
		this.today = today;
		this.rideTicksToday = rideTicks;
		this.todayCounted = counted;
		this.lastDay = lastDay;
		this.streak = streak;
		this.bestStreak = bestStreak;
		this.bonusDay = bonusDay;
		this.challengeDay = challengeDay;
		for (int k = 0; k < Math.min(progress.size(), this.progress.length); k++) {
			this.progress[k] = progress.get(k);
		}
		this.challengesDone = done;
		this.owned.addAll(owned);
		this.verticalCarry = verticalCarry;
		this.verticalTotal = verticalTotal;
		this.ticketsFound = tickets;
	}

	private static List<Integer> toList(int[] a) {
		List<Integer> l = new ArrayList<>();
		for (int v : a) {
			l.add(v);
		}
		return l;
	}
}
