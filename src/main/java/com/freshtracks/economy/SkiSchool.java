package com.freshtracks.economy;

import com.freshtracks.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;

/**
 * Ski School: a first day on the mountain in seven short lessons, each paying a little, the last one a graduation
 * pin. New skiers get a nudge toward the next lesson; it never gets in the way of anyone who already knows the ropes.
 */
public final class SkiSchool {
	public enum Lesson {
		CLICK_IN("Click into your skis or board (hold them and right-click)", 5),
		LIFT("Ride a chairlift or the gondola (stand on LOAD HERE)", 5),
		VERTICAL("Ski 200 blocks of vertical", 5),
		SPEED("Hit 50 km/h (hold W to tuck)", 5),
		TRICK("Land a trick (hold Space to load a jump, release to pop)", 5),
		BUTTER("Press or butter (left-click + W or S while riding)", 5),
		SHOP("Buy something from a shop with your Ski Bucks", 5);

		public final String text;
		public final int reward;

		Lesson(String text, int reward) {
			this.text = text;
			this.reward = reward;
		}
	}

	public static final int GRADUATION_BONUS = 15;

	private SkiSchool() {
	}

	public static boolean done(SkierProfile p, Lesson l) {
		return (p.records.lessons & (1 << l.ordinal())) != 0;
	}

	public static Lesson next(SkierProfile p) {
		for (Lesson l : Lesson.values()) {
			if (!done(p, l)) {
				return l;
			}
		}
		return null;
	}

	public static int count(SkierProfile p) {
		return Integer.bitCount(p.records.lessons & ((1 << Lesson.values().length) - 1));
	}

	/** Marks a lesson done (once) and pays for it. */
	static void complete(ServerPlayer player, SkierProfile p, Lesson l) {
		if (p.records.graduated || done(p, l)) {
			return;
		}
		p.records.lessons |= 1 << l.ordinal();
		SkierData.get(player.level().getServer()).setDirty();
		Lesson next = next(p);
		player.sendSystemMessage(Component.literal("Ski School ✔ ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
			.append(Component.literal(l.text.replaceAll(" \\(.*\\)", "")).withStyle(ChatFormatting.WHITE))
			.append(Component.literal("  +" + l.reward + " Ski Bucks  (" + count(p) + "/" + Lesson.values().length + ")").withStyle(ChatFormatting.GOLD)));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8f, 1.4f);
		Economy.earn(player, l.reward, "Ski School");
		if (next != null) {
			player.sendSystemMessage(Component.literal("  Next: " + next.text).withStyle(ChatFormatting.GRAY));
		} else {
			graduate(player, p);
		}
	}

	private static void graduate(ServerPlayer player, SkierProfile p) {
		p.records.graduated = true;
		SkierData.get(player.level().getServer()).setDirty();
		ItemStack pin = new ItemStack(ModItems.COLLECTOR_PIN);
		pin.set(DataComponents.ITEM_NAME, Component.literal("Ski School Graduate Pin"));
		pin.set(DataComponents.RARITY, Rarity.RARE);
		pin.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Earned, not bought").withStyle(ChatFormatting.GRAY))));
		if (!player.getInventory().add(pin)) {
			SkiBucks.dropFor(player, pin);
		}
		player.sendSystemMessage(Component.literal("🎓 You graduated from Ski School!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
			.append(Component.literal("  +" + GRADUATION_BONUS + " Ski Bucks and the Graduate Pin. Now go chase the daily challenges (/skibucks).")
				.withStyle(ChatFormatting.YELLOW)));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
		Economy.earn(player, GRADUATION_BONUS, "Ski School");
	}

	/** A reminder of the next lesson for anyone still in school (on join). */
	public static void remind(ServerPlayer player) {
		SkierProfile p = Economy.profile(player);
		Lesson next = next(p);
		if (p.records.graduated || next == null) {
			return;
		}
		player.sendSystemMessage(Component.literal("Ski School " + count(p) + "/" + Lesson.values().length + ": ").withStyle(ChatFormatting.AQUA)
			.append(Component.literal(next.text).withStyle(ChatFormatting.WHITE))
			.append(Component.literal("  (+" + next.reward + ")").withStyle(ChatFormatting.GOLD)));
	}
}
