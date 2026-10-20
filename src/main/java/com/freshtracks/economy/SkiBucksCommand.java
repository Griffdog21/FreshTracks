package com.freshtracks.economy;

import com.mojang.brigadier.CommandDispatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/** /skibucks: your wallet, pass, days and streak, and today's challenges. /skibucks top: who's on the mountain. */
public final class SkiBucksCommand {
	private SkiBucksCommand() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("skibucks")
			.executes(c -> show(c.getSource().getPlayerOrException()))
			.then(Commands.literal("top").executes(c -> top(c.getSource()))));
		var board = Commands.literal("leaderboard").executes(c -> leaderboard(c.getSource(), null));
		for (Board b : Board.values()) {
			board.then(Commands.literal(b.key).executes(c -> leaderboard(c.getSource(), b)));
		}
		dispatcher.register(board);
	}

	private static int show(ServerPlayer player) {
		SkierProfile p = Economy.profile(player);
		PassTier tier = PassTier.of(p);
		line(player, Component.literal("── Ski Bucks ──").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		line(player, Component.literal("Wallet: ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal(SkiBucks.format(SkiBucks.wallet(player))).withStyle(ChatFormatting.GOLD))
			.append(Component.literal("   Earned in all: " + SkiBucks.format(p.lifetimeEarned)).withStyle(ChatFormatting.GRAY)));
		line(player, Component.literal("Pass: ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal(tier.title).withStyle(tier.color, ChatFormatting.BOLD))
			.append(Component.literal("   Days on the mountain: " + p.daysSkied + "   Streak: " + p.streak + " (best " + p.bestStreak + ")").withStyle(ChatFormatting.GRAY)));
		PassTier next = tier.next();
		if (next != null) {
			int days = Math.max(0, next.days - p.daysSkied);
			long bucks = Math.max(0, next.lifetime - p.lifetimeEarned);
			line(player, Component.literal("Next: ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(next.title).withStyle(next.color))
				.append(Component.literal(" - " + (days > 0 ? days + " more day" + (days == 1 ? "" : "s") : "days done")
					+ ", " + (bucks > 0 ? SkiBucks.format(bucks) + " more Ski Bucks" : "earnings done")).withStyle(ChatFormatting.GRAY)));
		}
		int minutes = p.rideTicksToday / 1200;
		line(player, Component.literal(p.todayCounted ? "Today counts as a ski day." : "Today: " + minutes + " of 10 minutes riding to count the day.")
			.withStyle(ChatFormatting.DARK_AQUA));
		line(player, Component.literal("Today's challenges:").withStyle(ChatFormatting.YELLOW));
		Challenges.Kind[] today = Challenges.today(p.challengeDay);
		for (int i = 0; i < today.length; i++) {
			Challenges.Kind k = today[i];
			boolean done = (p.challengesDone & (1 << i)) != 0;
			int have = Math.min(k.target, p.progress[i] / k.scale());
			MutableComponent row = Component.literal((done ? " ✔ " : " • ") + k.describe()).withStyle(done ? ChatFormatting.GREEN : ChatFormatting.WHITE);
			row.append(Component.literal(done ? "" : "  " + SkiBucks.format(have) + "/" + SkiBucks.format(k.target)).withStyle(ChatFormatting.GRAY));
			row.append(Component.literal("  +" + k.reward).withStyle(ChatFormatting.GOLD));
			line(player, row);
		}
		line(player, Component.literal(" All three: +" + Challenges.ALL_BONUS + " more").withStyle(ChatFormatting.DARK_GRAY));
		if (!p.records.graduated) {
			SkiSchool.Lesson lesson = SkiSchool.next(p);
			if (lesson != null) {
				line(player, Component.literal("Ski School " + SkiSchool.count(p) + "/" + SkiSchool.Lesson.values().length + ": next - " + lesson.text)
					.withStyle(ChatFormatting.AQUA));
			}
		}
		if (p.ticketsFound > 0 || !p.owned.isEmpty()) {
			line(player, Component.literal("Golden Tickets found: " + p.ticketsFound + "   Collection: " + p.owned.size() + " pieces").withStyle(ChatFormatting.GRAY));
		}
		return 1;
	}

	private static int top(CommandSourceStack source) {
		List<ServerPlayer> players = new ArrayList<>(source.getServer().getPlayerList().getPlayers());
		players.sort(Comparator.comparingLong((ServerPlayer pl) -> Economy.profile(pl).lifetimeEarned).reversed());
		source.sendSystemMessage(Component.literal("── On the mountain now ──").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		for (ServerPlayer pl : players) {
			SkierProfile p = Economy.profile(pl);
			PassTier t = PassTier.of(p);
			source.sendSystemMessage(Component.literal(pl.getPlainTextName() + "  ").withStyle(ChatFormatting.WHITE)
				.append(Component.literal(t.title).withStyle(t.color))
				.append(Component.literal("  " + p.daysSkied + " days, " + SkiBucks.format(p.lifetimeEarned) + " earned").withStyle(ChatFormatting.GRAY)));
		}
		return players.size();
	}

	/** The leaderboards: what's ranked and how it reads. */
	public enum Board {
		EARNED("earned", "Most Ski Bucks earned", p -> p.lifetimeEarned, v -> SkiBucks.format(v)),
		DAYS("days", "Most days on the mountain", p -> p.daysSkied, v -> v + " days"),
		STREAK("streak", "Best streak", p -> p.bestStreak, v -> v + " days"),
		VERTICAL("vertical", "Most vertical skied", p -> p.verticalTotal, v -> SkiBucks.format(v) + " blocks"),
		TRICK("trick", "Biggest trick", p -> p.records.bestTrick, v -> SkiBucks.format(v) + " pts"),
		AIR("air", "Longest air", p -> p.records.longestAir, v -> String.format(java.util.Locale.ROOT, "%.1f s", v / 20.0)),
		SPEED("speed", "Top speed", p -> p.records.topKmh, v -> v + " km/h");

		public final String key;
		public final String title;
		public final java.util.function.ToLongFunction<SkierProfile> value;
		public final java.util.function.LongFunction<String> format;

		Board(String key, String title, java.util.function.ToLongFunction<SkierProfile> value, java.util.function.LongFunction<String> format) {
			this.key = key;
			this.title = title;
			this.value = value;
			this.format = format;
		}

		/** The top n skiers on this board (named, with a score above zero). */
		public List<SkierProfile> top(net.minecraft.server.MinecraftServer server, int n) {
			return SkierData.get(server).all().stream().filter(p -> !p.records.name.isEmpty() && value.applyAsLong(p) > 0)
				.sorted(Comparator.comparingLong(value).reversed()).limit(n).toList();
		}
	}

	private static int leaderboard(CommandSourceStack source, Board only) {
		for (Board b : Board.values()) {
			if (only != null && b != only) {
				continue;
			}
			int n = only == null ? 3 : 10;
			List<SkierProfile> top = b.top(source.getServer(), n);
			source.sendSystemMessage(Component.literal("── " + b.title + " ──").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			if (top.isEmpty()) {
				source.sendSystemMessage(Component.literal("  nobody yet: be the first").withStyle(ChatFormatting.DARK_GRAY));
			}
			for (int i = 0; i < top.size(); i++) {
				SkierProfile p = top.get(i);
				MutableComponent row = Component.literal(" " + (i + 1) + ". ").withStyle(i == 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY)
					.append(Component.literal(p.records.name).withStyle(ChatFormatting.WHITE))
					.append(Component.literal("  " + b.format.apply(b.value.applyAsLong(p))).withStyle(ChatFormatting.AQUA));
				if (b == Board.TRICK && !p.records.bestTrickName.isEmpty()) {
					row.append(Component.literal("  " + p.records.bestTrickName).withStyle(ChatFormatting.DARK_GRAY));
				}
				source.sendSystemMessage(row);
			}
		}
		if (only == null) {
			source.sendSystemMessage(Component.literal("Full lists: /leaderboard earned|days|streak|vertical|trick|air|speed").withStyle(ChatFormatting.DARK_GRAY));
		}
		return 1;
	}

	private static void line(ServerPlayer player, Component c) {
		player.sendSystemMessage(c);
	}
}
