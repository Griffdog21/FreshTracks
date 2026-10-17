package com.freshtracks.economy;

import net.minecraft.ChatFormatting;

/**
 * Your pass: it grows with days on the mountain (a day counts once you've ridden 10 minutes on it) and the Ski Bucks
 * you've earned in all. The best gear in the shops waits behind the higher passes - weeks of skiing for the top ones.
 */
public enum PassTier {
	DAY_TICKET("Day Ticket", 0, 0, ChatFormatting.WHITE),
	LOCAL("Local", 3, 1_500, ChatFormatting.GREEN),
	SEASON_PASS("Season Pass", 7, 6_000, ChatFormatting.AQUA),
	SKI_BUM("Ski Bum", 14, 20_000, ChatFormatting.BLUE),
	LEGEND("Legend", 30, 60_000, ChatFormatting.LIGHT_PURPLE),
	FOUNDER("Founders' Circle", 60, 150_000, ChatFormatting.GOLD);

	public final String title;
	public final int days;
	public final long lifetime;
	public final ChatFormatting color;

	PassTier(String title, int days, long lifetime, ChatFormatting color) {
		this.title = title;
		this.days = days;
		this.lifetime = lifetime;
		this.color = color;
	}

	public static PassTier of(SkierProfile p) {
		PassTier best = DAY_TICKET;
		for (PassTier t : values()) {
			if (p.daysSkied >= t.days && p.lifetimeEarned >= t.lifetime) {
				best = t;
			}
		}
		return best;
	}

	public PassTier next() {
		return this == FOUNDER ? null : values()[ordinal() + 1];
	}

	public String requirement() {
		return days == 0 ? "anyone" : "ski " + days + " days and earn " + SkiBucks.format(lifetime) + " Ski Bucks";
	}
}
