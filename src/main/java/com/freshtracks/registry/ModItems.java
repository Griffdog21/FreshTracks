package com.freshtracks.registry;

import com.freshtracks.FreshTracks;
import com.freshtracks.gear.GearItem;
import com.freshtracks.gear.GearMaterials;
import com.freshtracks.gear.SkiWaxItem;
import com.freshtracks.item.RigItem;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import com.freshtracks.lift.LiftCableItem;
import com.freshtracks.rig.RigKind;
import com.freshtracks.rig.RigStats;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class ModItems {
	public static final List<Item> CREATIVE_ORDER = new ArrayList<>();

	public static final Item WOODEN_SKIS = rig("wooden_skis", RigKind.SKIS, new RigStats(0.85f, 0.85f, 1.0f, 0.85f, 0.9f, 0.85f), 150, Rarity.COMMON);
	public static final Item ALPINE_SKIS = rig("alpine_skis", RigKind.SKIS, new RigStats(1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f), 450, Rarity.COMMON);
	public static final Item RACE_SKIS = rig("race_skis", RigKind.SKIS, new RigStats(1.15f, 1.2f, 0.8f, 0.8f, 0.9f, 0.8f), 550, Rarity.UNCOMMON);
	public static final Item POWDER_SKIS = rig("powder_skis", RigKind.SKIS, new RigStats(1.0f, 0.9f, 0.95f, 1.6f, 1.0f, 0.95f), 450, Rarity.UNCOMMON);
	public static final Item PARK_SKIS = rig("park_skis", RigKind.SKIS, new RigStats(0.95f, 0.95f, 1.15f, 1.0f, 1.2f, 1.25f), 400, Rarity.UNCOMMON);
	public static final Item SNURFER = rig("snurfer", RigKind.SNOWBOARD, new RigStats(0.8f, 0.8f, 0.9f, 1.0f, 0.9f, 0.9f), 150, Rarity.COMMON);
	public static final Item ALL_MOUNTAIN_BOARD = rig("all_mountain_board", RigKind.SNOWBOARD, new RigStats(1.0f, 0.95f, 1.0f, 1.1f, 1.0f, 1.05f), 450, Rarity.COMMON);
	public static final Item POWDER_BOARD = rig("powder_board", RigKind.SNOWBOARD, new RigStats(0.98f, 0.85f, 0.95f, 1.7f, 1.0f, 1.0f), 450, Rarity.UNCOMMON);
	public static final Item PARK_BOARD = rig("park_board", RigKind.SNOWBOARD, new RigStats(0.92f, 0.9f, 1.1f, 1.0f, 1.25f, 1.35f), 400, Rarity.UNCOMMON);
	// Stats: speed, grip, turn, flotation, pop, spin (, push)
	public static final Item CARVING_SKIS = rig("carving_skis", RigKind.SKIS, new RigStats(1.05f, 1.25f, 1.3f, 0.85f, 0.9f, 0.9f), 500, Rarity.UNCOMMON);
	public static final Item FREERIDE_SKIS = rig("freeride_skis", RigKind.SKIS, new RigStats(1.05f, 1.05f, 0.95f, 1.35f, 1.05f, 1.0f), 500, Rarity.UNCOMMON);
	public static final Item TOURING_SKIS = rig("touring_skis", RigKind.SKIS, new RigStats(0.95f, 1.0f, 1.0f, 1.2f, 0.95f, 0.9f, 1.5f), 400, Rarity.UNCOMMON);
	public static final Item RETRO_SKIS = rig("retro_skis", RigKind.SKIS, new RigStats(1.0f, 0.95f, 1.1f, 0.95f, 1.1f, 1.15f), 350, Rarity.RARE);
	public static final Item FREERIDE_BOARD = rig("freeride_board", RigKind.SNOWBOARD, new RigStats(1.05f, 1.0f, 0.95f, 1.45f, 1.0f, 1.0f), 500, Rarity.UNCOMMON);
	public static final Item CARVING_BOARD = rig("carving_board", RigKind.SNOWBOARD, new RigStats(1.1f, 1.25f, 1.25f, 0.85f, 0.85f, 0.85f), 500, Rarity.UNCOMMON);
	public static final Item SPLITBOARD = rig("splitboard", RigKind.SNOWBOARD, new RigStats(0.95f, 1.0f, 1.0f, 1.35f, 0.95f, 0.95f, 1.6f), 400, Rarity.UNCOMMON);
	public static final Item RETRO_BOARD = rig("retro_board", RigKind.SNOWBOARD, new RigStats(0.95f, 0.95f, 1.05f, 1.0f, 1.15f, 1.2f), 350, Rarity.RARE);

	public static final Item LIFT_CABLE = register("lift_cable", LiftCableItem::new, new Item.Properties().stacksTo(16));

	// Gear. Warmth counts toward staying unfrozen in blizzards and cold snaps.
	public static final Item SKI_GOGGLES = gear("ski_goggles", ArmorType.HELMET, 9, 1, 0f, GearMaterials.WOOL, 1, GearItem.Perk.GOGGLES);
	public static final Item SKI_HELMET = gear("ski_helmet", ArmorType.HELMET, 15, 2, 0.5f, GearMaterials.IRON, 1, GearItem.Perk.GOGGLES);
	public static final Item BEANIE = gear("beanie", ArmorType.HELMET, 7, 1, 0f, GearMaterials.WOOL, 2, GearItem.Perk.NONE);
	public static final Item SKI_JACKET = gear("ski_jacket", ArmorType.CHESTPLATE, 11, 3, 0f, GearMaterials.WOOL, 3, GearItem.Perk.NONE);
	public static final Item PUFFER_JACKET = gear("puffer_jacket", ArmorType.CHESTPLATE, 11, 3, 0f, GearMaterials.WOOL, 4, GearItem.Perk.NONE);
	public static final Item SHELL_JACKET = gear("shell_jacket", ArmorType.CHESTPLATE, 13, 3, 0f, GearMaterials.LEATHER, 2, GearItem.Perk.NONE);
	public static final Item ANORAK = gear("anorak", ArmorType.CHESTPLATE, 11, 3, 0f, GearMaterials.WOOL, 3, GearItem.Perk.NONE);
	public static final Item RETRO_JACKET = gear("retro_jacket", ArmorType.CHESTPLATE, 11, 3, 0f, GearMaterials.WOOL, 3, GearItem.Perk.NONE);
	public static final Item RACE_SUIT = gear("race_suit", ArmorType.CHESTPLATE, 9, 2, 0f, GearMaterials.LEATHER, 1, GearItem.Perk.RACE);
	public static final Item SNOW_PANTS = gear("snow_pants", ArmorType.LEGGINGS, 11, 2, 0f, GearMaterials.WOOL, 2, GearItem.Perk.NONE);
	public static final Item SKI_BOOTS = gear("ski_boots", ArmorType.BOOTS, 13, 2, 0f, GearMaterials.LEATHER, 1, GearItem.Perk.SKI_BOOTS);
	public static final Item SNOWBOARD_BOOTS = gear("snowboard_boots", ArmorType.BOOTS, 13, 1, 0f, GearMaterials.LEATHER, 1, GearItem.Perk.BOARD_BOOTS);
	public static final Item SKI_POLES = register("ski_poles", Item::new, new Item.Properties().stacksTo(1).durability(250));
	public static final Item SKI_WAX = register("ski_wax", SkiWaxItem::new, new Item.Properties().stacksTo(16));
	public static final Item HOT_COCOA = register("hot_cocoa", Item::new, new Item.Properties().stacksTo(16)
		.food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.6f).alwaysEdible().build(),
			Consumables.defaultDrink().onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(ModEffects.WARMTH, 6000))).build())
		.usingConvertsTo(Items.BOWL));

	private ModItems() {
	}

	private static Item rig(String name, RigKind kind, RigStats stats, int durability, Rarity rarity) {
		return register(name, p -> new RigItem(kind, stats, p), new Item.Properties().stacksTo(1).durability(durability).rarity(rarity));
	}

	private static Item gear(String name, ArmorType type, int durability, int defense, float toughness, TagKey<Item> repair, int warmth, GearItem.Perk perk) {
		ArmorMaterial material = GearMaterials.of(name, type, durability, defense, toughness, repair);
		return register(name, p -> new GearItem(p, warmth, perk), new Item.Properties().humanoidArmor(material, type));
	}

	public static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, FreshTracks.id(name));
		Item item = factory.apply(properties.setId(key));
		Registry.register(BuiltInRegistries.ITEM, key, item);
		CREATIVE_ORDER.add(item);
		return item;
	}

	public static void init() {
	}
}
