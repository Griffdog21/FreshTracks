package com.freshtracks.economy;

import com.freshtracks.registry.ModItems;
import java.time.LocalDate;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Where Ski Bucks come from: tricks (as before), every bit of vertical you ski, the first chair of the day, each day
 * on the mountain (more on a streak), three daily challenges, and - very rarely - a Golden Ticket.
 */
public final class Economy {
	/** Vertical blocks skied per Ski Buck (a top-to-bottom Big Burn lap is worth about a dozen). */
	private static final double VERTICAL_PER_BUCK = 200;
	/** Riding time that makes a day count (10 minutes). */
	private static final int DAY_TICKS = 12_000;
	private static final int FIRST_CHAIR = 5;

	private Economy() {
	}

	/** Trick pay: a Ski Buck per 1,000 points up to 3,000 points, then a little more for bigger tricks, at most 6. */
	public static int trickBucks(int points) {
		if (points <= 0) {
			return 0;
		}
		if (points <= 3000) {
			return points / 1000;
		}
		return Math.min(6, 3 + (int) Math.round(Math.sqrt(points - 3000) / 25));
	}

	/** Ski Bucks from tricks per day before the rest pay a quarter (skiing, challenges and streaks aren't capped). */
	public static final int TRICK_DAILY_FULL = 40;

	public static String today() {
		return LocalDate.now().toString();
	}

	public static SkierProfile profile(ServerPlayer player) {
		SkierProfile p = SkierData.get(player.level().getServer()).profile(player.getUUID());
		roll(p);
		String name = player.getPlainTextName();
		if (!name.equals(p.records.name)) {
			p.records.name = name;
			SkierData.get(player.level().getServer()).setDirty();
		}
		return p;
	}

	private static void dirty(ServerPlayer player) {
		SkierData.get(player.level().getServer()).setDirty();
	}

	/** New day: reset the ride clock and the challenges. */
	private static void roll(SkierProfile p) {
		String day = today();
		if (!day.equals(p.today)) {
			p.today = day;
			p.rideTicksToday = 0;
			p.todayCounted = false;
		}
		if (!day.equals(p.challengeDay)) {
			p.challengeDay = day;
			p.progress = new int[Challenges.PER_DAY];
			p.challengesDone = 0;
		}
	}

	/** Pays the player and counts it toward their pass. {@code why} shows above the hotbar (null: quietly). */
	public static void earn(ServerPlayer player, int amount, String why) {
		if (amount <= 0) {
			return;
		}
		SkierProfile p = profile(player);
		PassTier before = PassTier.of(p);
		// into this run's pocket: paid out in one go when you stop riding, not a bill in your hand every few seconds
		p.records.pocket += amount;
		p.pocketWhy.merge(why == null ? "tricks" : category(why), amount, Integer::sum);
		p.lifetimeEarned += amount;
		dirty(player);
		long now = player.level().getGameTime();
		if (now - p.lastPayMessage > 160) {
			p.lastPayMessage = now;
			player.sendOverlayMessage(Component.literal("This run: +" + SkiBucks.format(p.records.pocket) + " Ski Bucks").withStyle(ChatFormatting.GOLD));
		}
		checkTier(player, p, before);
	}

	private static String category(String why) {
		if (why.contains("streak") || why.contains("day on the mountain")) {
			return "day bonus";
		}
		if (why.contains("first chair")) {
			return "first chair";
		}
		if (why.contains("sweep")) {
			return "challenge";
		}
		return why;
	}

	/** Pays out the run's pocket once you've been off your skis or board for a few seconds (stopped, or on a lift). */
	public static void init() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 10 != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				SkierProfile p = profile(player);
				if (p.records.pocket <= 0 || player.level().getGameTime() - p.lastRideTick < 60) {
					continue;
				}
				payOut(player, p);
			}
		});
	}

	private static void payOut(ServerPlayer player, SkierProfile p) {
		int amount = p.records.pocket;
		p.records.pocket = 0;
		dirty(player);
		SkiBucks.give(player, amount, 1000);
		StringBuilder parts = new StringBuilder();
		for (var e : p.pocketWhy.entrySet()) {
			parts.append(parts.isEmpty() ? "" : "  ·  ").append(e.getKey()).append(' ').append(SkiBucks.format(e.getValue()));
		}
		p.pocketWhy.clear();
		player.sendSystemMessage(Component.literal("Run paid: +" + SkiBucks.format(amount) + " Ski Bucks").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
			.append(Component.literal(parts.isEmpty() ? "" : "   " + parts).withStyle(ChatFormatting.GRAY)));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 0.8f);
	}

	private static void checkTier(ServerPlayer player, SkierProfile p, PassTier before) {
		PassTier now = PassTier.of(p);
		if (now.ordinal() > before.ordinal()) {
			player.sendSystemMessage(Component.literal("Pass upgraded: ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(now.title).withStyle(now.color, ChatFormatting.BOLD))
				.append(Component.literal("  New gear is unlocked in the shops.").withStyle(ChatFormatting.GRAY)));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1f);
			for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
				if (other != player) {
					other.sendSystemMessage(Component.literal(player.getPlainTextName() + " reached ").withStyle(ChatFormatting.GRAY)
						.append(Component.literal(now.title).withStyle(now.color)));
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ hooks

	/** A landed trick (the server already checked it's a real ride). */
	public static void trick(ServerPlayer player, int points, int airTicks, String name) {
		SkierProfile p = profile(player);
		int bucks = trickBucks(points);
		if (!today().equals(p.trickDay)) {
			p.trickDay = today();
			p.trickBucksToday = 0;
			p.toldTrickCap = false;
		}
		if (p.trickBucksToday >= TRICK_DAILY_FULL) {
			bucks = (bucks + 3) / 4;
			if (bucks > 0 && !p.toldTrickCap) {
				p.toldTrickCap = true;
				player.sendSystemMessage(Component.literal("You've earned " + TRICK_DAILY_FULL
					+ " Ski Bucks from tricks today; more tricks pay a quarter until tomorrow. Skiing vertical and challenges still pay in full.")
					.withStyle(ChatFormatting.GRAY));
			}
		}
		p.trickBucksToday += bucks;
		if (bucks > 0) {
			earn(player, bucks, null); // the trick HUD shows the payout already
		}
		if (points > p.records.bestTrick) {
			p.records.bestTrick = points;
			p.records.bestTrickName = name.length() > 48 ? name.substring(0, 48) : name;
			dirty(player);
		}
		if (airTicks > p.records.longestAir && airTicks < 400) {
			p.records.longestAir = airTicks;
			dirty(player);
		}
		SkiSchool.complete(player, p, SkiSchool.Lesson.TRICK);
		progress(player, p, Challenges.Kind.TRICKS, 1, false);
		progress(player, p, Challenges.Kind.POINTS, points, false);
		progress(player, p, Challenges.Kind.BIG_TRICK, points >= Challenges.Kind.BIG_TRICK.target ? 1 : 0, false);
		progress(player, p, Challenges.Kind.HANG_TIME, airTicks, true);
		String n = name.toLowerCase(java.util.Locale.ROOT);
		if (n.contains("press") || n.contains("butter") || n.contains("roll")) {
			progress(player, p, Challenges.Kind.BUTTERS, 1, false);
			SkiSchool.complete(player, p, SkiSchool.Lesson.BUTTER);
		}
		if (points >= 1500 && player.getRandom().nextInt(1500) == 0) {
			goldenTicket(player, p, "off a " + SkiBucks.format(points) + "-point trick");
		}
	}

	/** Every server tick a player rides skis or a board. */
	public static void ride(ServerPlayer player, Vec3 moved, double speed) {
		SkierProfile p = profile(player);
		p.lastRideTick = player.level().getGameTime();
		if (speed > 0.05) {
			SkiSchool.complete(player, p, SkiSchool.Lesson.CLICK_IN);
			p.rideTicksToday++;
			progress(player, p, Challenges.Kind.RIDE_TIME, 1, false);
			if (!p.todayCounted && p.rideTicksToday >= DAY_TICKS) {
				countDay(player, p);
			}
		}
		if (moved.y > -3 && moved.y < 3) {
			// net descent: bumps on the way down cancel out (a short climb can owe a few blocks, never more)
			p.verticalCarry = Math.max(-4, p.verticalCarry - moved.y);
			while (p.verticalCarry >= 1) {
				p.verticalCarry -= 1;
				p.verticalTotal++;
				progress(player, p, Challenges.Kind.VERTICAL, 1, false);
				p.pendingVertical++;
			}
			// pay vertical as it adds up, in small batches so the hotbar isn't spammed
			int owed = (int) (p.pendingVertical / VERTICAL_PER_BUCK);
			if (owed >= 1) {
				p.pendingVertical -= (int) (owed * VERTICAL_PER_BUCK);
				earn(player, owed, "vertical");
			}
		}
		// the rig's smoothed speed: a single-tick jump (clicking in, a teleport) doesn't count as a top speed
		int kmh = (int) (speed * 72);
		if (p.verticalTotal >= 200) {
			SkiSchool.complete(player, p, SkiSchool.Lesson.VERTICAL);
		}
		if (kmh >= 50 && kmh < 200) {
			SkiSchool.complete(player, p, SkiSchool.Lesson.SPEED);
		}
		if (kmh > p.records.topKmh && kmh < 160) {
			p.records.topKmh = kmh;
			dirty(player);
		}
		if (kmh > 0 && kmh < 200) {
			progress(player, p, Challenges.Kind.SPEED, kmh, true);
		}
	}

	/** Boarded a lift: the first one of the day pays the first-chair bonus. */
	public static void lift(ServerPlayer player) {
		SkierProfile p = profile(player);
		progress(player, p, Challenges.Kind.LIFTS, 1, false);
		SkiSchool.complete(player, p, SkiSchool.Lesson.LIFT);
		if (!today().equals(p.bonusDay)) {
			p.bonusDay = today();
			dirty(player);
			earn(player, FIRST_CHAIR, "first chair of the day");
		}
	}

	private static void countDay(ServerPlayer player, SkierProfile p) {
		PassTier before = PassTier.of(p);
		p.todayCounted = true;
		String yesterday = LocalDate.now().minusDays(1).toString();
		p.streak = yesterday.equals(p.lastDay) ? p.streak + 1 : 1;
		p.bestStreak = Math.max(p.bestStreak, p.streak);
		p.lastDay = p.today;
		p.daysSkied++;
		dirty(player);
		int bonus = 10 + 3 * Math.min(p.streak - 1, 5);
		player.sendSystemMessage(Component.literal("Day " + p.daysSkied + " on the mountain").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
			.append(Component.literal(p.streak > 1 ? "  ·  " + p.streak + "-day streak" : "").withStyle(ChatFormatting.YELLOW)));
		earn(player, bonus, p.streak > 1 ? p.streak + "-day streak" : "a day on the mountain");
		if (p.streak > 0 && p.streak % 7 == 0) {
			goldenTicket(player, p, "for a " + p.streak + "-day streak");
		}
		checkTier(player, p, before);
	}

	/** Adds to a challenge (or raises it to {@code value} for best-of kinds) and pays out when it's done. */
	private static void progress(ServerPlayer player, SkierProfile p, Challenges.Kind kind, int value, boolean best) {
		Challenges.Kind[] today = Challenges.today(p.challengeDay);
		for (int i = 0; i < today.length; i++) {
			if (today[i] != kind || (p.challengesDone & (1 << i)) != 0) {
				continue;
			}
			p.progress[i] = best ? Math.max(p.progress[i], value) : p.progress[i] + value;
			if (p.progress[i] >= kind.target * kind.scale()) {
				p.challengesDone |= 1 << i;
				player.sendSystemMessage(Component.literal("Challenge complete: ").withStyle(ChatFormatting.GREEN)
					.append(Component.literal(kind.describe()).withStyle(ChatFormatting.WHITE)));
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
				earn(player, kind.reward, "challenge");
				if ((p.challengesDone & 7) == 7 && (p.challengesDone & 0x80) == 0) {
					p.challengesDone |= 0x80;
					player.sendSystemMessage(Component.literal("All three of today's challenges done!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
					earn(player, Challenges.ALL_BONUS, "daily sweep");
					if (player.getRandom().nextInt(40) == 0) {
						goldenTicket(player, p, "for sweeping the daily challenges");
					}
				}
			}
			dirty(player);
		}
	}

	private static void goldenTicket(ServerPlayer player, SkierProfile p, String how) {
		p.ticketsFound++;
		dirty(player);
		ItemStack ticket = new ItemStack(ModItems.GOLDEN_TICKET);
		if (!player.getInventory().add(ticket)) {
			SkiBucks.dropFor(player, ticket);
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1.2f);
		for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
			other.sendSystemMessage(Component.literal("★ " + player.getPlainTextName() + " found a Golden Ticket " + how + "!").withStyle(ChatFormatting.GOLD));
		}
	}
}
