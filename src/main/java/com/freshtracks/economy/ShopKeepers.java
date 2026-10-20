package com.freshtracks.economy;

import com.freshtracks.registry.ModComponents;
import com.freshtracks.village.ModVillagers;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Shopkeepers who sell from the catalog lay out their goods fresh for each skier who walks up (so the locks, the
 * collection ticks and the edition numbers are theirs). A villager tagged {@code freshtracks:shop/<list>} sells only
 * that list; ski and board techs keep their own trades and add the pro shop's after them.
 */
public final class ShopKeepers {
	public static final String TAG_PREFIX = "freshtracks:shop/";

	private ShopKeepers() {
	}

	public static void init() {
		ProShop.register();
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((listener, sender, server) ->
			server.execute(() -> SkiSchool.remind(listener.getPlayer())));
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)
				|| !(entity instanceof Villager v) || v.isTrading() || v.isBaby()) {
				return InteractionResult.PASS;
			}
			refresh(sp, v);
			return InteractionResult.PASS;
		});
	}

	public static String shopOf(Villager v) {
		for (String tag : v.entityTags()) {
			if (tag.startsWith(TAG_PREFIX)) {
				return tag.substring(TAG_PREFIX.length());
			}
		}
		var profession = v.getVillagerData().profession();
		if (profession.is(ModVillagers.SKI_TECH)) {
			return ProShop.SKI;
		}
		if (profession.is(ModVillagers.BOARD_TECH)) {
			return ProShop.BOARD;
		}
		return null;
	}

	public static void refresh(ServerPlayer player, Villager v) {
		String shop = shopOf(v);
		if (shop == null || !Catalog.has(shop)) {
			return;
		}
		boolean only = v.entityTags().contains(TAG_PREFIX + shop);
		MerchantOffers offers = new MerchantOffers();
		if (!only) {
			for (MerchantOffer o : v.getOffers()) {
				if (!o.getResult().has(ModComponents.CATALOG)) {
					offers.add(o);
				}
			}
		}
		offers.addAll(Catalog.offers(player, shop));
		v.setOffers(offers);
	}
}
