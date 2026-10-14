package com.freshtracks.registry;

import com.freshtracks.FreshTracks;
import com.freshtracks.lift.block.LiftTerminalBlock;
import com.freshtracks.lift.block.LiftTowerBlock;
import com.freshtracks.lift.block.LiftTowerHeadBlock;
import com.freshtracks.lift.block.MagicCarpetBlock;
import com.freshtracks.race.TimingLineBlock;
import com.freshtracks.resort.FloorMarkBlock;
import com.freshtracks.resort.ResortSignBlock;
import com.freshtracks.resort.TrailSignBlock;
import com.freshtracks.race.TrailMarkerBlock;
import com.freshtracks.village.WorkstationBlock;
import com.freshtracks.weather.FreshPowderBlock;
import com.freshtracks.world.IcicleBlock;
import com.freshtracks.world.ModWorldgen;
import java.util.function.Function;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

public final class ModBlocks {
	public static final Block LIFT_TERMINAL = register("lift_terminal", LiftTerminalBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.5f, 6f).sound(SoundType.METAL).noOcclusion());
	public static final Block LIFT_TOWER = register("lift_tower", LiftTowerBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 6f).sound(SoundType.METAL).noOcclusion());
	public static final Block LIFT_TOWER_HEAD = register("lift_tower_head", LiftTowerHeadBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 6f).sound(SoundType.METAL).noOcclusion());

	public static final Block MAGIC_CARPET = register("magic_carpet", MagicCarpetBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.8f).sound(SoundType.WOOL));

	public static final Block FRESH_POWDER = register("fresh_powder", FreshPowderBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.SNOW).mapColor(MapColor.SNOW));
	/** Corduroy: freshly groomed snow, lines along the fall line. */
    public static final Block PARK_RAIL = register("park_rail", p -> new com.freshtracks.resort.ParkFeatureBlock(false,p),
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2f).sound(SoundType.METAL).noOcclusion());
    /** Optional historical styling; generated parks use the neutral box. */
    public static final Block CLASSIC_PARK_BOX = register("classic_park_box", p -> new com.freshtracks.resort.ParkFeatureBlock(true,p),
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2f).sound(SoundType.METAL).noOcclusion());
    public static final Block PARK_BOX = register("park_box", p -> new com.freshtracks.resort.ParkFeatureBlock(true,p),
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2f).sound(SoundType.METAL).noOcclusion());
	public static final Block GROOMED_SNOW = register("groomed_snow", com.freshtracks.weather.GroomedSnowBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.SNOW).mapColor(MapColor.SNOW));
	/** A service road under a skin of packed snow: skiable, the gravel and tyre ruts showing. */
	public static final Block SNOWY_ROAD = register("snowy_road", com.freshtracks.weather.SnowyRoadBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.SNOW).mapColor(MapColor.COLOR_LIGHT_GRAY).sound(SoundType.GRAVEL));

	public static final Block LARCH_NEEDLES = register("larch_needles", p -> new LeavesBlock(AmbientLeavesBlockSoundPlayer.noAmbientSound(), p),
		leaves(MapColor.COLOR_ORANGE), leafItem());
	public static final Block ASPEN_LEAVES = register("aspen_leaves", p -> new LeavesBlock(AmbientLeavesBlockSoundPlayer.noAmbientSound(), p),
		leaves(MapColor.GOLD), leafItem());
	/** Snowmass spruce and fir boughs under a load of snow. */
	public static final Block SNOWY_FIR_NEEDLES = register("snowy_fir_needles", p -> new LeavesBlock(AmbientLeavesBlockSoundPlayer.noAmbientSound(), p),
		leaves(MapColor.COLOR_GREEN), leafItem());
	/** Bare aspen crowns: the aspens have dropped their leaves by ski season. */
	public static final Block ASPEN_TWIGS = register("aspen_twigs", p -> new LeavesBlock(AmbientLeavesBlockSoundPlayer.noAmbientSound(), p),
		leaves(MapColor.COLOR_LIGHT_GRAY), leafItem());
	public static final Block LARCH_SAPLING = register("larch_sapling",
		p -> new SaplingBlock(new TreeGrower("freshtracks_larch", WeightedList.of(ModWorldgen.LARCH), WeightedList.of(), WeightedList.of(), ModWorldgen.LARCH), p),
		sapling(), saplingItem());
	public static final Block ASPEN_SAPLING = register("aspen_sapling",
		p -> new SaplingBlock(new TreeGrower("freshtracks_aspen", WeightedList.of(ModWorldgen.ASPEN), WeightedList.of(), WeightedList.of(), ModWorldgen.ASPEN), p),
		sapling(), saplingItem());
	public static final Block ICICLE = register("icicle", IcicleBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(0.3f).sound(SoundType.GLASS).noOcclusion().noCollision()
			.pushReaction(PushReaction.POPPED));

	public static final Block SKI_BENCH = register("ski_bench", WorkstationBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).noOcclusion().ignitedByLava());
	public static final Block BOARD_PRESS = register("board_press", WorkstationBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).sound(SoundType.METAL).noOcclusion());
	public static final Block LIFT_CONTROL = register("lift_control", WorkstationBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(3.0f).sound(SoundType.METAL).noOcclusion());
	/** The ski patrol's red first-aid cabinet: the Ski Patrol job site. */
	public static final Block FIRST_AID_KIT = register("first_aid_kit", WorkstationBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.5f).sound(SoundType.METAL).noOcclusion());

	public static final Block START_LINE = register("start_line", p -> new TimingLineBlock(false, p),
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.2f).sound(SoundType.WOOL).noOcclusion());
	public static final Block FINISH_LINE = register("finish_line", p -> new TimingLineBlock(true, p),
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.2f).sound(SoundType.WOOL).noOcclusion());
	public static final Block TRAIL_MARKER = register("trail_marker", TrailMarkerBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0f).sound(SoundType.WOOD).noOcclusion().ignitedByLava());

	// Resort furniture: lift loading mats, rope lines for lift mazes, closure fencing and signs.
	public static final Block LOAD_HERE = register("load_here", FloorMarkBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.4f).sound(SoundType.WOOL).noOcclusion());
	public static final Block WAIT_HERE = register("wait_here", FloorMarkBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(0.4f).sound(SoundType.WOOL).noOcclusion());
	public static final Block ROPE_LINE = register("rope_line", FenceBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.8f).sound(SoundType.BAMBOO).noOcclusion().ignitedByLava());
	public static final Block CLOSURE_FENCE = register("closure_fence", IronBarsBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.5f).sound(SoundType.WOOL).noOcclusion());
	public static final Block SLOW_ZONE_SIGN = register("slow_zone_sign", ResortSignBlock::new, sign());
	public static final Block CLOSED_SIGN = register("closed_sign", ResortSignBlock::new, sign());
	public static final Block EXPERTS_ONLY_SIGN = register("experts_only_sign", ResortSignBlock::new, sign());
	public static final Block SINGLES_SIGN = register("singles_sign", ResortSignBlock::new, sign());
	public static final Block TRAIL_SIGN = register("trail_sign", TrailSignBlock::new, sign());
	public static final Block BOUNDARY_SIGN = register("boundary_sign", ResortSignBlock::new, sign());
	public static final Block CLIFF_SIGN = register("cliff_sign", ResortSignBlock::new, sign());
	/** Rope strung between rope-line posts, without a post of its own. */
	public static final Block ROPE_SPAN = register("rope_span", FenceBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(0.4f).sound(SoundType.WOOL).noOcclusion().ignitedByLava());
	public static final Block HAZARD_POLE = register("hazard_pole", com.freshtracks.resort.HazardPoleBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.6f).sound(SoundType.BAMBOO).noOcclusion());
	public static final Block SNOW_GUN = register("snow_gun", p -> new com.freshtracks.resort.SnowGunBlock(false, p),
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.0f).sound(SoundType.METAL).noOcclusion().randomTicks());
	public static final Block SNOW_GUN_TOWER = register("snow_gun_tower", p -> new com.freshtracks.resort.SnowGunBlock(true, p),
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f).sound(SoundType.METAL).noOcclusion().randomTicks());
	public static final Block SNOW_GUN_POLE = register("snow_gun_pole", com.freshtracks.resort.PoleBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f).sound(SoundType.METAL).noOcclusion());
	public static final Block TRAIL_MAP = register("trail_map", com.freshtracks.resort.TrailMapBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD).noOcclusion());
	public static final Block TOWER_PAD = register("tower_pad", com.freshtracks.resort.TowerPadBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(0.8f).sound(SoundType.WOOL).noOcclusion());

	private static BlockBehaviour.Properties sign() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0f).sound(SoundType.WOOD).noOcclusion().ignitedByLava();
	}

	private static BlockBehaviour.Properties leaves(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).strength(0.2f).randomTicks().sound(SoundType.GRASS).noOcclusion()
			.isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p, a) -> false).ignitedByLava().pushReaction(PushReaction.POPPED)
			.isRedstoneConductor((s, l, p) -> false);
	}

	private static BlockBehaviour.Properties sapling() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.GRASS)
			.pushReaction(PushReaction.POPPED);
	}

	private static Item.Properties leafItem() {
		return new Item.Properties().compostable(ContextIntProviders.COMPOSTABLE_LOW);
	}

	private static Item.Properties saplingItem() {
		return new Item.Properties().compostable(ContextIntProviders.COMPOSTABLE_LOW).cookingFuel(ContextIntProviders.COOKING_TIME_DRY_PLANTS);
	}

	private ModBlocks() {
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		return register(name, factory, properties, new Item.Properties());
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties,
		Item.Properties itemProperties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, FreshTracks.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		ModItems.register(name, p -> new BlockItem(block, p), itemProperties.useBlockDescriptionPrefix());
		return block;
	}

	public static void init() {
		FlammableBlockRegistry flammable = FlammableBlockRegistry.getDefaultInstance();
		flammable.add(LARCH_NEEDLES, 30, 60);
		flammable.add(ASPEN_LEAVES, 30, 60);
		flammable.add(SNOWY_FIR_NEEDLES, 30, 60);
		flammable.add(ASPEN_TWIGS, 30, 60);
	}
}
