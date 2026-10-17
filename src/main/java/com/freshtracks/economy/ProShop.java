package com.freshtracks.economy;

import static com.freshtracks.economy.Catalog.branded;
import static com.freshtracks.economy.Catalog.plain;

import com.freshtracks.economy.Catalog.Entry;
import com.freshtracks.registry.ModItems;
import java.util.List;
import net.minecraft.world.item.Rarity;

/**
 * The pro shop every ski and board tech keeps behind the counter: branded, tuned skis, boards and kit, priced in Ski
 * Bucks and gated by pass. The house brands are made up; the rides are real: a tune is a few percent here and there,
 * the legendary pieces a little more, and the Golden pairs are numbered, a handful per world.
 */
public final class ProShop {
	public static final String SKI = "pro_shop_ski";
	public static final String BOARD = "pro_shop_board";

	private ProShop() {
	}

	private static final Entry FOUNDERS_JACKET = new Entry("founders_jacket", branded(ModItems.RETRO_JACKET, "'67 Founders' Jacket", Rarity.EPIC, Tune.warmth(5),
		"A replica of the opening-day patrol jacket"), 25_000, PassTier.FOUNDER, true, 50, 0);

	static void register() {
		Catalog.register(SKI, List.of(
			new Entry("wax_kit", plain(ModItems.SKI_WAX, 4, "Pro Wax Kit", "Four bars of all-temp wax"), 12),
			new Entry("storm_goggles", branded(ModItems.SKI_GOGGLES, "Whiteout Storm Goggles", Rarity.UNCOMMON, Tune.warmth(1),
				"Low-light lens for storm days"), 350, PassTier.LOCAL),
			new Entry("park_twin", branded(ModItems.PARK_SKIS, "Ridgeline Park Twin", Rarity.UNCOMMON, Tune.rig(1f, 1f, 1.03f, 1f, 1.08f, 1.08f),
				"Soft flex, centred mount", "Built for rails and spins"), 450, PassTier.LOCAL),
			new Entry("fat_boy", branded(ModItems.POWDER_SKIS, "Deep Day Fat Boy 118", Rarity.UNCOMMON, Tune.rig(1.03f, 1f, 1f, 1.1f, 1f, 1f),
				"118 underfoot, rockered tip"), 450, PassTier.LOCAL),
			new Entry("charger", branded(ModItems.FREERIDE_SKIS, "Highline Charger 104", Rarity.RARE, Tune.rig(1.05f, 1.04f, 1f, 1.08f, 1.03f, 1f),
				"Titanal laminate, damp at speed"), 1_200, PassTier.SEASON_PASS),
			new Entry("carver", branded(ModItems.CARVING_SKIS, "Corduroy Carver Ti", Rarity.RARE, Tune.rig(1.04f, 1.08f, 1.06f, 1f, 1f, 1f),
				"Short radius, ice-edge hold"), 1_200, PassTier.SEASON_PASS),
			new Entry("pro_helmet", branded(ModItems.SKI_HELMET, "Summit Pro Helmet", Rarity.RARE, Tune.warmth(1),
				"Vented shell, fleece liner"), 900, PassTier.SEASON_PASS),
			new Entry("couloir_pro", branded(ModItems.FREERIDE_SKIS, "Couloir Pro Model", Rarity.RARE, Tune.rig(1.08f, 1.08f, 1.04f, 1.12f, 1.05f, 1.03f),
				"The guides' choice for steeps"), 4_000, PassTier.SKI_BUM),
			new Entry("world_cup_gs", branded(ModItems.RACE_SKIS, "World Cup GS", Rarity.RARE, Tune.rig(1.1f, 1.1f, 1.02f, 1f, 1f, 1f),
				"FIS-legal sidecut, race plate"), 5_000, PassTier.SKI_BUM),
			new Entry("gold_medal_dh", branded(ModItems.RACE_SKIS, "Gold Medal Downhill", Rarity.EPIC, Tune.rig(1.15f, 1.12f, 1f, 1f, 1f, 1f),
				"Hand-finished race stock", "The fastest skis on the mountain"), 15_000, PassTier.LEGEND, false, 25, 0),
			new Entry("golden_skis", branded(ModItems.ALPINE_SKIS, "The Golden Skis", Rarity.EPIC, Tune.rig(1.12f, 1.12f, 1.12f, 1.12f, 1.12f, 1.12f),
				"Gold-leaf topsheet, every stat up", "Ten pairs exist on any mountain"), 40_000, PassTier.FOUNDER, false, 10, 1),
			FOUNDERS_JACKET
		));
		Catalog.register(BOARD, List.of(
			new Entry("board_wax_kit", plain(ModItems.SKI_WAX, 4, "Pro Wax Kit", "Four bars of all-temp wax"), 12),
			new Entry("park_rat", branded(ModItems.PARK_BOARD, "Park Rat Twin", Rarity.UNCOMMON, Tune.rig(1f, 1f, 1.04f, 1f, 1.08f, 1.08f),
				"True twin, buttery flex"), 450, PassTier.LOCAL),
			new Entry("heated_boots", branded(ModItems.SNOWBOARD_BOOTS, "Toasty Heated Boots", Rarity.UNCOMMON, Tune.warmth(2),
				"Battery heaters in the liner"), 800, PassTier.LOCAL),
			new Entry("swallowtail", branded(ModItems.POWDER_BOARD, "Powder Day Swallowtail", Rarity.RARE, Tune.rig(1.03f, 1f, 1f, 1.15f, 1f, 1f),
				"Swallow tail, set-back stance"), 1_200, PassTier.SEASON_PASS),
			new Entry("down_parka", branded(ModItems.PUFFER_JACKET, "Expedition Down Parka", Rarity.RARE, Tune.warmth(3),
				"800-fill down, storm hood"), 1_500, PassTier.SEASON_PASS),
			new Entry("pipe_dragon", branded(ModItems.PARK_BOARD, "Pipe Dragon Pro", Rarity.RARE, Tune.rig(1f, 1f, 1.05f, 1f, 1.12f, 1.12f),
				"Pro model: stiff for big pop"), 4_000, PassTier.SKI_BUM),
			new Entry("big_mountain_gun", branded(ModItems.FREERIDE_BOARD, "Big Mountain Gun 162", Rarity.EPIC, Tune.rig(1.1f, 1.08f, 1f, 1.15f, 1.05f, 1f),
				"Directional, carbon stringers", "For the biggest lines"), 14_000, PassTier.LEGEND, false, 25, 0),
			new Entry("golden_board", branded(ModItems.ALL_MOUNTAIN_BOARD, "The Golden Board", Rarity.EPIC, Tune.rig(1.12f, 1.12f, 1.12f, 1.12f, 1.12f, 1.12f),
				"Gold-leaf topsheet, every stat up", "Ten exist on any mountain"), 40_000, PassTier.FOUNDER, false, 10, 1),
			FOUNDERS_JACKET
		));
	}
}
