package com.freshtracks.economy;

import com.freshtracks.registry.ModComponents;
import com.freshtracks.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * What the resort shops sell for Ski Bucks, and to whom. Each shop is a list of entries; when a skier walks up, the
 * keeper lays out that list for them: the price in notes, and the gear their pass doesn't reach yet shown sold out
 * with what it takes to unlock it. Unique items (one per skier, ever) and limited editions (numbered, a set count per
 * world) are tracked in {@link SkierData}.
 */
public final class Catalog {
	/**
	 * @param id        stable id (collections and editions are tracked by it)
	 * @param make      the item as sold
	 * @param price     Ski Bucks
	 * @param tier      the pass needed to buy it
	 * @param unique    one per skier
	 * @param edition   how many exist on a world (0: no limit)
	 * @param tickets   Golden Tickets asked on top of the price
	 */
	public record Entry(String id, Supplier<ItemStack> make, int price, PassTier tier, boolean unique, int edition, int tickets, ItemCost payWith) {
		public Entry(String id, Supplier<ItemStack> make, int price, PassTier tier, boolean unique, int edition, int tickets) {
			this(id, make, price, tier, unique, edition, tickets, null);
		}

		public Entry(String id, Supplier<ItemStack> make, int price) {
			this(id, make, price, PassTier.DAY_TICKET, false, 0, 0, null);
		}

		public Entry(String id, Supplier<ItemStack> make, int price, PassTier tier) {
			this(id, make, price, tier, false, 0, 0, null);
		}

		/** The shop buys something: hand over {@code pay}, get {@code make} (the bank buying a Golden Ticket). */
		public static Entry exchange(String id, Supplier<ItemStack> make, ItemCost pay) {
			return new Entry(id, make, 0, PassTier.DAY_TICKET, false, 0, 0, pay);
		}
	}

	private static final Map<String, List<Entry>> SHOPS = new HashMap<>();
	private static final List<java.util.function.Function<String, List<Entry>>> RESOLVERS = new ArrayList<>();
	private static final Map<String, Entry> BY_ID = new HashMap<>();

	private Catalog() {
	}

	public static void register(String shop, List<Entry> entries) {
		SHOPS.put(shop, List.copyOf(entries));
		for (Entry e : entries) {
			BY_ID.put(e.id, e);
		}
	}

	/** Supplies shop lists that aren't registered up front (an add-on's shops, made from its map data). */
	public static void resolver(java.util.function.Function<String, List<Entry>> resolver) {
		RESOLVERS.add(resolver);
	}

	public static synchronized boolean has(String shop) {
		if (!SHOPS.containsKey(shop)) {
			for (var r : RESOLVERS) {
				List<Entry> list = r.apply(shop);
				if (list != null) {
					register(shop, list);
					break;
				}
			}
		}
		return SHOPS.containsKey(shop);
	}

	public static List<Entry> entries(String shop) {
		has(shop);
		return SHOPS.getOrDefault(shop, List.of());
	}

	public static Entry entry(String id) {
		return BY_ID.get(id);
	}

	/** The shop's offers as this skier sees them. */
	public static MerchantOffers offers(ServerPlayer player, String shop) {
		MerchantOffers out = new MerchantOffers();
		for (Entry e : entries(shop)) {
			out.add(offer(player, e));
		}
		return out;
	}

	public static MerchantOffer offer(ServerPlayer player, Entry e) {
		SkierProfile p = Economy.profile(player);
		SkierData data = SkierData.get(player.level().getServer());
		ItemStack stack = e.make.get();
		stack.set(ModComponents.CATALOG, e.id);
		List<Component> lore = new ArrayList<>(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
		boolean locked = false;
		if (e.edition > 0) {
			int sold = data.sold(e.id);
			if (sold >= e.edition) {
				lore.add(Component.literal("Sold out - all " + e.edition + " are taken").withStyle(ChatFormatting.RED));
				locked = true;
			} else {
				lore.add(Component.literal("Limited edition  No. " + (sold + 1) + " of " + e.edition).withStyle(ChatFormatting.GOLD));
			}
		}
		if (e.unique && p.owned.contains(e.id)) {
			lore.add(Component.literal("✔ Already in your collection").withStyle(ChatFormatting.GREEN));
			locked = true;
		}
		PassTier tier = PassTier.of(p);
		if (tier.ordinal() < e.tier.ordinal()) {
			lore.add(Component.literal("🔒 Needs a " + e.tier.title + " pass").withStyle(ChatFormatting.RED));
			lore.add(Component.literal("   " + e.tier.requirement()).withStyle(ChatFormatting.GRAY));
			lore.add(Component.literal("   you: " + p.daysSkied + " days, " + SkiBucks.format(p.lifetimeEarned) + " earned").withStyle(ChatFormatting.DARK_GRAY));
			locked = true;
		} else if (e.tier != PassTier.DAY_TICKET) {
			lore.add(Component.literal(e.tier.title + " pass gear").withStyle(e.tier.color));
		}
		if (!lore.isEmpty()) {
			stack.set(DataComponents.LORE, new ItemLore(lore));
		}
		if (e.payWith != null) {
			MerchantOffer swap = new MerchantOffer(e.payWith, Optional.empty(), stack, 0, 999, 0, 0f);
			if (locked) {
				swap.setToOutOfStock();
			}
			return swap;
		}
		ItemCost[] cost = SkiBucks.cost(e.price);
		Optional<ItemCost> second = e.tickets > 0 ? Optional.of(new ItemCost(ModItems.GOLDEN_TICKET, e.tickets)) : SkiBucks.second(cost);
		ItemCost first = cost[0];
		if (e.tickets > 0 && cost[1] != null) {
			// with a ticket in the second slot the price has to fit in one: round up to whole thousands
			first = new ItemCost(ModItems.SKI_BUCK_THOUSAND, Math.min(64, (e.price + 999) / 1000));
		}
		int max = e.unique || e.edition > 0 ? 1 : 999;
		MerchantOffer offer = new MerchantOffer(first, second, stack, 0, max, 0, 0f);
		if (locked) {
			offer.setToOutOfStock();
		}
		return offer;
	}

	/** A trade went through: record collections and editions, and close the offer if it was one of a kind. */
	public static void sold(ServerPlayer player, MerchantOffer offer) {
		String id = offer.getResult().get(ModComponents.CATALOG);
		Entry e = id == null ? null : BY_ID.get(id);
		if (e == null) {
			return;
		}
		SkierData data = SkierData.get(player.level().getServer());
		SkiSchool.complete(player, Economy.profile(player), SkiSchool.Lesson.SHOP);
		if (e.unique) {
			Economy.profile(player).owned.add(e.id);
			data.setDirty();
		}
		if (e.edition > 0) {
			data.sell(e.id);
		}
		if (e.unique || e.edition > 0) {
			offer.setToOutOfStock();
		}
		if (e.tier.ordinal() >= PassTier.LEGEND.ordinal() || e.edition > 0) {
			for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
				other.sendSystemMessage(Component.literal("★ " + player.getPlainTextName() + " just bought ").withStyle(ChatFormatting.GOLD)
					.append(offer.getResult().getHoverName().copy().withStyle(ChatFormatting.YELLOW)));
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ making items

	/** A branded item: its name (coloured by rarity), a line or two of description, and an optional tune. */
	public static Supplier<ItemStack> branded(Item base, String name, Rarity rarity, Tune tune, String... lore) {
		return () -> {
			ItemStack s = new ItemStack(base);
			s.set(DataComponents.ITEM_NAME, Component.literal(name));
			s.set(DataComponents.RARITY, rarity);
			if (tune != null) {
				s.set(ModComponents.TUNE, tune);
			}
			if (rarity == Rarity.EPIC) {
				s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			if (lore.length > 0) {
				List<Component> l = new ArrayList<>();
				for (String line : lore) {
					l.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
				}
				s.set(DataComponents.LORE, new ItemLore(l));
			}
			return s;
		};
	}

	/** A plain item, optionally renamed (food on a menu: "Pepperoni Slice"). */
	public static Supplier<ItemStack> plain(Item base, int count, String name, String... lore) {
		return () -> {
			ItemStack s = new ItemStack(base, count);
			if (name != null) {
				s.set(DataComponents.ITEM_NAME, Component.literal(name));
			}
			if (lore.length > 0) {
				List<Component> l = new ArrayList<>();
				for (String line : lore) {
					l.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
				}
				s.set(DataComponents.LORE, new ItemLore(l));
			}
			return s;
		};
	}
}
