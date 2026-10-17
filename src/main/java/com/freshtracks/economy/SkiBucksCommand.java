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

	private static void line(ServerPlayer player, Component c) {
		player.sendSystemMessage(c);
	}
}
