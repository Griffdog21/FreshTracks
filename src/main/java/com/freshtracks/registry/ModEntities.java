package com.freshtracks.registry;

import com.freshtracks.FreshTracks;
import com.freshtracks.lift.LiftSeatEntity;
import com.freshtracks.rig.RigEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final EntityType<RigEntity> RIG = register("rig",
		EntityType.Builder.<RigEntity>of(RigEntity::new, MobCategory.MISC)
			.sized(0.6f, 0.5f)
			.clientTrackingRange(10)
			.updateInterval(1)
			.noLootTable()
			.noSummon());
	public static final EntityType<LiftSeatEntity> LIFT_SEAT = register("lift_seat",
		EntityType.Builder.<LiftSeatEntity>of(LiftSeatEntity::new, MobCategory.MISC)
			.sized(0.5f, 0.5f)
			.clientTrackingRange(10)
			.updateInterval(20)
			.noLootTable()
			.noSummon());

	private ModEntities() {
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, FreshTracks.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void init() {
	}
}
