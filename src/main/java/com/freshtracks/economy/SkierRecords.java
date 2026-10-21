package com.freshtracks.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** A skier's personal bests (for the leaderboards) and their Ski School progress. */
public final class SkierRecords {
	public static final Codec<SkierRecords> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.optionalFieldOf("name", "").forGetter(r -> r.name),
		Codec.INT.optionalFieldOf("best_trick", 0).forGetter(r -> r.bestTrick),
		Codec.STRING.optionalFieldOf("best_trick_name", "").forGetter(r -> r.bestTrickName),
		Codec.INT.optionalFieldOf("longest_air", 0).forGetter(r -> r.longestAir),
		Codec.INT.optionalFieldOf("top_kmh", 0).forGetter(r -> r.topKmh),
		Codec.INT.optionalFieldOf("lessons", 0).forGetter(r -> r.lessons),
		Codec.BOOL.optionalFieldOf("graduated", false).forGetter(r -> r.graduated),
		Codec.INT.optionalFieldOf("pocket", 0).forGetter(r -> r.pocket)
	).apply(i, SkierRecords::new));

	/** The skier's name as last seen (leaderboards list offline skiers too). */
	public String name = "";
	public int bestTrick;
	public String bestTrickName = "";
	/** Longest single jump, in ticks of air. */
	public int longestAir;
	public int topKmh;
	/** Bit per Ski School lesson done. */
	public int lessons;
	public boolean graduated;
	/** Ski Bucks earned on the run so far, paid out when the skier stops (kept if they log off mid-run). */
	public int pocket;

	public SkierRecords() {
	}

	private SkierRecords(String name, int bestTrick, String bestTrickName, int longestAir, int topKmh, int lessons, boolean graduated, int pocket) {
		this.pocket = pocket;
		this.name = name;
		this.bestTrick = bestTrick;
		this.bestTrickName = bestTrickName;
		this.longestAir = longestAir;
		this.topKmh = topKmh;
		this.lessons = lessons;
		this.graduated = graduated;
	}
}
