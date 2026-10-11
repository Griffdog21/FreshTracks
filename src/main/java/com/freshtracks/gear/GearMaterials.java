package com.freshtracks.gear;

import com.freshtracks.FreshTracks;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** Armor materials for ski gear. Each piece has its own look, so each gets its own material/asset. */
public final class GearMaterials {
	private GearMaterials() {
	}

	public static ResourceKey<EquipmentAsset> asset(String name) {
		return ResourceKey.create(EquipmentAssets.ROOT_ID, FreshTracks.id(name));
	}

	/**
	 * @param name       equipment asset (assets/freshtracks/equipment/name.json)
	 * @param durability per-piece multiplier, like vanilla's (leather 5, iron 15)
	 * @param defense    armor points for this piece's slot
	 */
	public static ArmorMaterial of(String name, ArmorType type, int durability, int defense, float toughness, TagKey<Item> repair) {
		return of(name, type, durability, defense, toughness, repair, SoundEvents.ARMOR_EQUIP_LEATHER);
	}

	public static ArmorMaterial of(String name, ArmorType type, int durability, int defense, float toughness, TagKey<Item> repair, Holder<SoundEvent> sound) {
		return new ArmorMaterial(durability, Map.of(type, defense), 14, sound, toughness, 0f, repair, asset(name));
	}

	public static final TagKey<Item> WOOL = ItemTags.WOOL;
	public static final TagKey<Item> LEATHER = ItemTags.REPAIRS_LEATHER_ARMOR;
	public static final TagKey<Item> IRON = ItemTags.REPAIRS_IRON_ARMOR;
}
