package com.freshtracks.economy;

import com.freshtracks.registry.ModItems;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;

/**
 * Ski Bucks as notes in your pockets: 1s, 100s and 1,000s. Paying out hands over the fewest notes; when you pick
 * something in a shop the keeper makes change, so any price you can afford is counted out from whatever notes you hold.
 */
public final class SkiBucks {
	private SkiBucks() {
	}

	public static String format(long n) {
		return String.format(Locale.ROOT, "%,d", n);
	}

	public static int value(ItemStack stack) {
		return stack.getItem() instanceof BillItem bill ? bill.value() * stack.getCount() : 0;
	}

	public static long wallet(ServerPlayer player) {
		long total = 0;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			total += value(inv.getItem(i));
		}
		return total;
	}

	/** Hands {@code amount} to the player in notes no bigger than {@code largest}; what won't fit waits on the ground for them. */
	public static void give(ServerPlayer player, long amount, int largest) {
		for (int denom : new int[]{1000, 100, 1}) {
			if (denom > Math.max(1, largest)) {
				continue;
			}
			long n = amount / denom;
			amount -= n * denom;
			Item item = note(denom);
			while (n > 0) {
				int count = (int) Math.min(n, 64);
				n -= count;
				ItemStack stack = new ItemStack(item, count);
				if (!player.getInventory().add(stack) && !stack.isEmpty()) {
					dropFor(player, stack);
				}
			}
		}
	}

	/** Leaves a stack on the ground that only this player can pick up, and that never despawns. */
	public static void dropFor(ServerPlayer player, ItemStack stack) {
		ItemEntity drop = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack.copy());
		drop.setTarget(player.getUUID());
		drop.setUnlimitedLifetime();
		player.level().addFreshEntity(drop);
	}

	static Item note(int denom) {
		return denom >= 1000 ? ModItems.SKI_BUCK_THOUSAND : denom >= 100 ? ModItems.SKI_BUCK_HUNDRED : ModItems.SKI_BUCK;
	}

	/** Re-issues the player's notes as the fewest bills. */
	public static void tidy(ServerPlayer player) {
		long total = takeAll(player);
		give(player, total, 1000);
	}

	private static long takeAll(ServerPlayer player) {
		var inv = player.getInventory();
		long total = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.getItem() instanceof BillItem) {
				total += value(s);
				inv.setItem(i, ItemStack.EMPTY);
			}
		}
		return total;
	}

	/**
	 * Makes change so a shop price can be paid: if the wallet holds enough in total, the exact notes the price asks
	 * for are counted out first and the rest re-issued as the fewest bills. Returns false (and changes nothing) if the
	 * wallet is short.
	 */
	public static boolean makeChange(ServerPlayer player, ItemStack a, ItemStack b) {
		long price = value(a) + value(b);
		if (price <= 0 || wallet(player) < price) {
			return false;
		}
		long total = takeAll(player);
		for (ItemStack c : new ItemStack[]{a, b}) {
			if (c.getItem() instanceof BillItem bill) {
				give(player, (long) value(c), bill.value());
			}
		}
		give(player, total - price, 1000);
		return true;
	}

	static long costValue(ItemCost c) {
		return c != null && c.item().value() instanceof BillItem bill ? (long) bill.value() * c.count() : 0;
	}

	public static boolean isBills(ItemCost c) {
		return c.item().value() instanceof BillItem;
	}

	/** A price as a merchant cost: ones up to 64; hundreds plus up to 64 ones; thousands plus hundreds. */
	public static ItemCost[] cost(int price) {
		if (price <= 64) {
			return new ItemCost[]{new ItemCost(ModItems.SKI_BUCK, Math.max(1, price)), null};
		}
		if (price < 1000) {
			int h = price / 100;
			int o = price % 100;
			if (h == 0) {
				return new ItemCost[]{new ItemCost(ModItems.SKI_BUCK, 64), new ItemCost(ModItems.SKI_BUCK, price - 64)};
			}
			if (o > 64) {
				h++;
				o = 0;
			}
			return new ItemCost[]{new ItemCost(ModItems.SKI_BUCK_HUNDRED, h), o > 0 ? new ItemCost(ModItems.SKI_BUCK, o) : null};
		}
		if (price >= 65_000) {
			int t = Math.min(128, price / 1000);
			return new ItemCost[]{new ItemCost(ModItems.SKI_BUCK_THOUSAND, 64), new ItemCost(ModItems.SKI_BUCK_THOUSAND, t - 64)};
		}
		int t = Math.min(64, price / 1000);
		int h = (price % 1000) / 100;
		return new ItemCost[]{new ItemCost(ModItems.SKI_BUCK_THOUSAND, t), h > 0 ? new ItemCost(ModItems.SKI_BUCK_HUNDRED, h) : null};
	}

	/** What a price actually comes to once it's in notes (prices round to what the notes can say). */
	public static long priced(int price) {
		ItemCost[] c = cost(price);
		return costValue(c[0]) + costValue(c[1]);
	}

	public static Optional<ItemCost> second(ItemCost[] c) {
		return Optional.ofNullable(c[1]);
	}
}
