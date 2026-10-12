package com.freshtracks.village;

import com.freshtracks.FreshTracks;
import com.freshtracks.registry.ModBlocks;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.block.Block;

/**
 * Resort jobs. A villager without a job who finds one of these workstations takes the job:
 * Ski Bench = Ski Tech (skis, poles, boots, goggles), Board Press = Board Tech (boards, outerwear), Lift Control = Liftie
 * (lift parts, hot cocoa), First Aid Kit = Ski Patrol (closure fencing, rope line, hazard poles and signs). Their trades
 * are data (data/freshtracks/villager_trade + trade_set).
 */
public final class ModVillagers {
	public static final ResourceKey<VillagerProfession> SKI_TECH = profession("ski_tech", ModBlocks.SKI_BENCH, SoundEvents.VILLAGER_WORK_TOOLSMITH);
	public static final ResourceKey<VillagerProfession> BOARD_TECH = profession("board_tech", ModBlocks.BOARD_PRESS, SoundEvents.VILLAGER_WORK_LEATHERWORKER);
	public static final ResourceKey<VillagerProfession> LIFTIE = profession("liftie", ModBlocks.LIFT_CONTROL, SoundEvents.VILLAGER_WORK_CARTOGRAPHER);
	public static final ResourceKey<VillagerProfession> SKI_PATROL = profession("ski_patrol", ModBlocks.FIRST_AID_KIT, SoundEvents.VILLAGER_WORK_CLERIC);

	private ModVillagers() {
	}

	private static ResourceKey<VillagerProfession> profession(String name, Block workstation, SoundEvent workSound) {
		ResourceKey<PoiType> poi = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, FreshTracks.id(name));
		PoiHelper.register(poi.identifier(), 1, 1, workstation);
		ResourceKey<VillagerProfession> key = ResourceKey.create(Registries.VILLAGER_PROFESSION, FreshTracks.id(name));
		Int2ObjectMap<ResourceKey<TradeSet>> trades = Int2ObjectMap.ofEntries(
			Int2ObjectMap.entry(1, tradeSet(name, 1)),
			Int2ObjectMap.entry(2, tradeSet(name, 2)),
			Int2ObjectMap.entry(3, tradeSet(name, 3)),
			Int2ObjectMap.entry(4, tradeSet(name, 4)),
			Int2ObjectMap.entry(5, tradeSet(name, 5)));
		Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, key, new VillagerProfession(
			Component.translatable("entity.freshtracks.villager." + name),
			holder -> holder.is(poi), holder -> holder.is(poi), ImmutableSet.of(), ImmutableSet.of(), workSound, trades));
		return key;
	}

	private static ResourceKey<TradeSet> tradeSet(String profession, int level) {
		return ResourceKey.create(Registries.TRADE_SET, FreshTracks.id(profession + "/level_" + level));
	}

	public static void init() {
	}
}
