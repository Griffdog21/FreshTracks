package com.freshtracks.registry;

import com.freshtracks.FreshTracks;
import com.freshtracks.lift.block.LiftTerminalBlockEntity;
import com.freshtracks.lift.block.LiftTowerHeadBlockEntity;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	public static final BlockEntityType<LiftTerminalBlockEntity> LIFT_TERMINAL = register("lift_terminal", LiftTerminalBlockEntity::new, ModBlocks.LIFT_TERMINAL);
	public static final BlockEntityType<LiftTowerHeadBlockEntity> LIFT_TOWER_HEAD = register("lift_tower_head", LiftTowerHeadBlockEntity::new, ModBlocks.LIFT_TOWER_HEAD);
	public static final BlockEntityType<com.freshtracks.resort.SnowGunBlockEntity> SNOW_GUN = register("snow_gun",
		com.freshtracks.resort.SnowGunBlockEntity::new, ModBlocks.SNOW_GUN, ModBlocks.SNOW_GUN_TOWER);
	public static final BlockEntityType<com.freshtracks.resort.TrailMapBlockEntity> TRAIL_MAP = register("trail_map",
		com.freshtracks.resort.TrailMapBlockEntity::new, ModBlocks.TRAIL_MAP);
	public static final BlockEntityType<com.freshtracks.resort.TrailSignBlockEntity> TRAIL_SIGN = register("trail_sign",
		com.freshtracks.resort.TrailSignBlockEntity::new, ModBlocks.TRAIL_SIGN);

	private ModBlockEntities() {
	}

	private static <T extends BlockEntity> BlockEntityType<T> register(String name, BlockEntityType.BlockEntitySupplier<T> factory, Block... blocks) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, FreshTracks.id(name), new BlockEntityType<>(factory, Set.of(blocks)));
	}

	public static void init() {
	}
}
