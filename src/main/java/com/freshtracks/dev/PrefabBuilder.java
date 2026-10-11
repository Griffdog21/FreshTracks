package com.freshtracks.dev;

import com.freshtracks.FreshTracks;
import com.freshtracks.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.core.FrontAndTop;
import net.minecraft.world.level.block.JigsawBlock;

/**
 * Development only: builds the resort buildings block by block and saves them as structure templates
 * (run/saves/<world>/generated/freshtracks/structure/*.nbt, then copied into data/freshtracks/structure).
 * Local coordinates: x east, y up, z south, from the template's minimum corner.
 */
final class PrefabBuilder {
	static final String[] NAMES = {"village/ski_shop", "village/board_shop", "ski_lodge"};

	private final ServerLevel level;
	private final BlockPos origin;

	private PrefabBuilder(ServerLevel level, BlockPos origin) {
		this.level = level;
		this.origin = origin;
	}

	static Vec3i size(String name) {
		return switch (name) {
			case "village/ski_shop", "village/board_shop" -> new Vec3i(10, 10, 9);
			case "ski_lodge" -> new Vec3i(17, 16, 15);
			default -> throw new IllegalArgumentException(name);
		};
	}

	private static final java.util.List<Object[]> PENDING = new java.util.ArrayList<>();

	static {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (java.util.Iterator<Object[]> it = PENDING.iterator(); it.hasNext(); ) {
				Object[] job = it.next();
				int left = (Integer) job[0] - 1;
				job[0] = left;
				if (left <= 0) {
					it.remove();
					((Runnable) job[1]).run();
				}
			}
		});
	}

	/** Clears the area and builds the prefab now; saves it ten ticks later, once summoned entities are in the world. */
	static Component buildAndSave(ServerLevel level, BlockPos origin, String name) {
		Vec3i size = size(name);
		PrefabBuilder b = new PrefabBuilder(level, origin);
		b.fill(-2, 0, -2, size.getX() + 1, size.getY() + 2, size.getZ() + 1, Blocks.AIR.defaultBlockState());
		b.fill(-2, -1, -2, size.getX() + 1, -1, size.getZ() + 1, Blocks.SNOW_BLOCK.defaultBlockState());
		level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new net.minecraft.world.phys.AABB(net.minecraft.world.phys.Vec3.atCenterOf(origin.offset(-3, -1, -3)),
			net.minecraft.world.phys.Vec3.atCenterOf(origin.offset(size).offset(3, 3, 3))), e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(e -> e.discard());
		switch (name) {
			case "village/ski_shop" -> b.shop(true);
			case "village/board_shop" -> b.shop(false);
			case "ski_lodge" -> b.lodge();
			default -> {
			}
		}
		PENDING.add(new Object[]{10, (Runnable) () -> save(level, origin, size, name)});
		return Component.literal("Built " + name + "; saving in a moment");
	}

	private static void save(ServerLevel level, BlockPos origin, Vec3i size, String name) {
		StructureTemplateManager manager = level.getServer().getStructureTemplateManager();
		Identifier id = FreshTracks.id(name);
		StructureTemplate template = manager.getOrCreate(id);
		int entities = level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
			net.minecraft.world.phys.AABB.encapsulatingFullBlocks(origin, origin.offset(size).offset(-1, -1, -1)),
			e -> !(e instanceof net.minecraft.world.entity.player.Player)).size();
		template.fillFromWorld(level, origin, size, true, List.of(Blocks.STRUCTURE_VOID));
		template.setAuthor("Fresh Tracks");
		boolean ok = manager.save(id);
		FreshTracks.LOG.info("{} {} ({}x{}x{}, {} entities)", ok ? "Saved" : "Could not save", id, size.getX(), size.getY(), size.getZ(), entities);
	}

	// ------------------------------------------------------------------------------------------ helpers

	private BlockPos at(int x, int y, int z) {
		return origin.offset(x, y, z);
	}

	private void set(int x, int y, int z, BlockState state) {
		level.setBlock(at(x, y, z), state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	private void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
					set(x, y, z, state);
				}
			}
		}
	}

	private static BlockState stairs(Block block, Direction facing, Half half) {
		return block.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, half);
	}

	private static BlockState log(Block block, Direction.Axis axis) {
		return block.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
	}

	private static BlockState facing(Block block, Direction dir) {
		return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, dir);
	}

	private void door(int x, int y, int z, Direction facing, DoorHingeSide hinge) {
		BlockState d = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HINGE, hinge);
		set(x, y, z, d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		set(x, y + 1, z, d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
	}

	private void bed(int x, int y, int z, Direction headDir, Block bed) {
		BlockState s = bed.defaultBlockState().setValue(BedBlock.FACING, headDir);
		set(x, y, z, s.setValue(BedBlock.PART, BedPart.FOOT));
		BlockPos head = new BlockPos(x, y, z).relative(headDir);
		set(head.getX(), head.getY(), head.getZ(), s.setValue(BedBlock.PART, BedPart.HEAD));
	}

	private void sign(int x, int y, int z, Direction facing, DyeColor color, String... lines) {
		set(x, y, z, Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing));
		if (level.getBlockEntity(at(x, y, z)) instanceof SignBlockEntity sign) {
			Component[] messages = new Component[4];
			for (int i = 0; i < 4; i++) {
				messages[i] = Component.literal(i < lines.length ? lines[i] : "");
			}
			List<Component> list = List.of(messages);
			sign.updateText(t -> new SignText(list, list, color, true), SignTextSlot.FRONT);
			sign.setWaxed(true);
		}
	}

	private void frame(int x, int y, int z, Direction facing, String item) {
		BlockPos p = at(x, y, z);
		command("summon minecraft:item_frame " + p.getX() + " " + p.getY() + " " + p.getZ()
			+ " {Facing:" + facing.get3DDataValue() + "b,Fixed:1b,Invulnerable:1b,Item:{id:\"" + item + "\",count:1}}");
	}

	private void villager(int x, int y, int z, String profession) {
		BlockPos p = at(x, y, z);
		command("summon minecraft:villager " + p.getX() + ".5 " + p.getY() + " " + p.getZ() + ".5 {VillagerData:{type:\"minecraft:snow\",profession:\""
			+ profession + "\",level:1},Xp:1,PersistenceRequired:1b}");
	}

	private void jigsaw(int x, int y, int z, Direction out) {
		set(x, y, z, Blocks.JIGSAW.defaultBlockState().setValue(JigsawBlock.ORIENTATION, FrontAndTop.fromFrontAndTop(out, Direction.UP)));
		if (level.getBlockEntity(at(x, y, z)) instanceof JigsawBlockEntity jigsaw) {
			jigsaw.setName(Identifier.withDefaultNamespace("building_entrance"));
			jigsaw.setTarget(Identifier.withDefaultNamespace("building_entrance"));
			jigsaw.setPool(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TEMPLATE_POOL, Identifier.withDefaultNamespace("empty")));
			jigsaw.setFinalState("minecraft:structure_void");
			jigsaw.setJoint(JigsawBlockEntity.JointType.ALIGNED);
			jigsaw.setChanged();
		}
	}

	private void command(String cmd) {
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), cmd);
	}

	// ----------------------------------------------------------------------------------------- shops

	/**
	 * Village shop, 10 x 10 x 9. Door on the west wall (x = 1) with the street jigsaw in front of it at (0, 0, 4).
	 * Walls x 1..8, z 0..8; gable roof with the ridge running east-west; a sign over the door; the workstation, a bed
	 * and gear on display (frames only hang on solid planks, never on glass).
	 */
	private void shop(boolean ski) {
		BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
		BlockState post = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y);
		BlockState beamX = log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X);
		BlockState stone = Blocks.COBBLESTONE.defaultBlockState();
		BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
		// floor and foundation
		fill(1, 0, 0, 8, 0, 8, stone);
		fill(2, 0, 1, 7, 0, 7, planks);
		// walls y 1..3, stone course at y 1
		for (int y = 1; y <= 3; y++) {
			BlockState wall = y == 1 ? stone : planks;
			fill(1, y, 0, 8, y, 0, wall);
			fill(1, y, 8, 8, y, 8, wall);
			fill(1, y, 0, 1, y, 8, wall);
			fill(8, y, 0, 8, y, 8, wall);
			for (int[] c : new int[][]{{1, 0}, {8, 0}, {1, 8}, {8, 8}, {5, 0}, {5, 8}}) {
				set(c[0], y, c[1], post);
			}
		}
		// top plates, and close the gable ends at plate height
		fill(1, 4, 0, 8, 4, 0, beamX);
		fill(1, 4, 8, 8, 4, 8, beamX);
		fill(1, 4, 1, 1, 4, 7, planks);
		fill(8, 4, 1, 8, 4, 7, planks);
		// windows: side walls near the back, back wall, front wall beside the door
		for (int x : new int[]{6, 7}) {
			set(x, 2, 0, pane);
			set(x, 2, 8, pane);
		}
		set(8, 2, 2, pane);
		set(8, 2, 6, pane);
		set(1, 2, 2, pane);
		set(1, 2, 6, pane);
		// door + entrance step + jigsaw
		door(1, 1, 4, Direction.WEST, DoorHingeSide.LEFT);
		jigsaw(0, 0, 4, Direction.WEST);
		// gable roof, ridge along x at z = 4
		for (int i = 0; i <= 4; i++) {
			int y = 4 + i;
			for (int x = 0; x <= 9; x++) {
				if (i < 4) {
					set(x, y, i, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, Half.BOTTOM));
					set(x, y, 8 - i, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH, Half.BOTTOM));
				} else {
					set(x, y, 4, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
				}
			}
			if (i >= 1 && i < 4) {
				for (int z = i; z <= 8 - i; z++) {
					set(1, y, z, planks);
					set(8, y, z, planks);
				}
			}
		}
		set(1, 7, 4, planks);
		set(8, 7, 4, planks);
		set(1, 6, 4, pane);
		set(8, 6, 4, pane);
		for (int x = 0; x <= 9; x++) {
			set(x, 9, 4, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 2));
		}
		// ceiling beam and light
		fill(2, 4, 4, 7, 4, 4, beamX);
		set(4, 3, 4, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		// sign over the door, lantern beside it
		sign(0, 3, 4, Direction.WEST, ski ? DyeColor.RED : DyeColor.PURPLE, "", ski ? "SKI SHOP" : "BOARD SHOP", ski ? "tunes & rentals" : "boards & outerwear", "");
		set(0, 2, 2, Blocks.SPRUCE_FENCE.defaultBlockState());
		set(0, 1, 2, Blocks.SPRUCE_FENCE.defaultBlockState());
		set(0, 3, 2, Blocks.LANTERN.defaultBlockState());
		// interior (x 2..7, z 1..7)
		set(7, 1, 2, facing(ski ? ModBlocks.SKI_BENCH : ModBlocks.BOARD_PRESS, Direction.WEST));
		set(7, 1, 1, Blocks.BARREL.defaultBlockState());
		bed(6, 1, 6, Direction.EAST, ski ? Blocks.BED.pick(DyeColor.RED) : Blocks.BED.pick(DyeColor.CYAN));
		if (ski) {
			set(2, 1, 7, Blocks.BARREL.defaultBlockState());
			fill(3, 1, 3, 5, 1, 5, Blocks.CARPET.pick(DyeColor.RED).defaultBlockState());
			fill(4, 1, 4, 4, 1, 4, Blocks.CARPET.pick(DyeColor.WHITE).defaultBlockState());
			frame(2, 2, 1, Direction.SOUTH, "freshtracks:alpine_skis");
			frame(3, 2, 1, Direction.SOUTH, "freshtracks:race_skis");
			frame(4, 2, 1, Direction.SOUTH, "freshtracks:powder_skis");
			frame(2, 2, 7, Direction.NORTH, "freshtracks:ski_poles");
			frame(3, 2, 7, Direction.NORTH, "freshtracks:ski_goggles");
			frame(7, 2, 4, Direction.WEST, "freshtracks:ski_wax");
		} else {
			set(2, 1, 7, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.NORTH));
			fill(3, 1, 3, 5, 1, 5, Blocks.CARPET.pick(DyeColor.PURPLE).defaultBlockState());
			fill(4, 1, 4, 4, 1, 4, Blocks.CARPET.pick(DyeColor.CYAN).defaultBlockState());
			frame(2, 2, 1, Direction.SOUTH, "freshtracks:all_mountain_board");
			frame(3, 2, 1, Direction.SOUTH, "freshtracks:powder_board");
			frame(4, 2, 1, Direction.SOUTH, "freshtracks:park_board");
			frame(2, 2, 7, Direction.NORTH, "freshtracks:beanie");
			frame(3, 2, 7, Direction.NORTH, "freshtracks:snowboard_boots");
			frame(7, 2, 4, Direction.WEST, "freshtracks:ski_jacket");
		}
	}

	// ----------------------------------------------------------------------------------------- lodge

	/**
	 * Ski lodge, 17 x 16 x 15. A-frame with the ridge running north-south and the big gable window facing the deck
	 * (south). Ski shop on the west side, board shop on the east, fireplace on the back wall, lift desk by the door,
	 * a sleeping loft over the back, and three workers.
	 */
	private void lodge() {
		BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
		BlockState dark = Blocks.DARK_OAK_PLANKS.defaultBlockState();
		BlockState post = log(Blocks.SPRUCE_LOG, Direction.Axis.Y);
		BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
		BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
		BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
		// foundation + floor (building z 1..10) and the deck (z 11..14)
		fill(0, 0, 0, 16, 0, 10, bricks);
		fill(2, 0, 2, 14, 0, 9, planks);
		fill(0, 0, 11, 16, 0, 14, planks);
		// deck railing with an opening in the middle
		for (int x = 0; x <= 16; x++) {
			if (x < 7 || x > 9) {
				set(x, 1, 14, Blocks.SPRUCE_FENCE.defaultBlockState());
			}
		}
		for (int z = 11; z <= 14; z++) {
			set(0, 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
			set(16, 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
		}
		// deck benches (looking out at the view) and lanterns on the corner posts
		for (int x : new int[]{2, 3, 13, 14}) {
			set(x, 1, 13, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH, Half.BOTTOM));
		}
		set(0, 2, 14, Blocks.LANTERN.defaultBlockState());
		set(16, 2, 14, Blocks.LANTERN.defaultBlockState());
		// A-frame: roof stairs from x = 0 / 16 at y = 1 up to the ridge at x = 8, y = 9, covering z 0..11,
		// with a solid layer under the stairs (column x has its under-layer at y = min(x, 16 - x))
		for (int i = 0; i <= 8; i++) {
			int y = 1 + i;
			for (int z = 0; z <= 11; z++) {
				if (i < 8) {
					set(i, y, z, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST, Half.BOTTOM));
					set(16 - i, y, z, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, Half.BOTTOM));
					if (i > 0) {
						set(i, y - 1, z, i == 1 ? post : dark);
						set(16 - i, y - 1, z, i == 1 ? post : dark);
					}
				} else {
					set(8, y, z, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
					set(8, y - 1, z, dark);
				}
			}
		}
		for (int z = 0; z <= 11; z++) {
			set(8, 10, z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 3));
		}
		// clear the inside under the roof (z 1..10)
		for (int x = 2; x <= 14; x++) {
			int h = Math.min(x, 16 - x);
			fill(x, 1, 1, x, h - 1, 10, Blocks.AIR.defaultBlockState());
		}
		// gable walls: back (z = 1) and front (z = 10)
		for (int x = 1; x <= 15; x++) {
			int h = Math.min(x, 16 - x);
			for (int y = 1; y <= h - 1; y++) {
				set(x, y, 1, planks);
				set(x, y, 10, planks);
			}
		}
		// front: big gable window, posts, door, sign board
		for (int x = 3; x <= 13; x++) {
			int h = Math.min(x, 16 - x);
			for (int y = 2; y <= h - 1; y++) {
				set(x, y, 10, pane);
			}
		}
		for (int x : new int[]{2, 5, 11, 14}) {
			int h = Math.min(x, 16 - x);
			for (int y = 1; y <= h - 1; y++) {
				set(x, y, 10, post);
			}
		}
		fill(3, 1, 10, 13, 1, 10, planks);
		fill(7, 2, 10, 9, 5, 10, planks);
		door(8, 1, 10, Direction.SOUTH, DoorHingeSide.LEFT);
		sign(8, 4, 11, Direction.SOUTH, DyeColor.BLUE, "~ FRESH ~", "TRACKS", "SKI LODGE", "");
		// back: fireplace and chimney (smoke from a campfire on top)
		fill(6, 1, 1, 10, 3, 1, cobble);
		fill(7, 1, 0, 9, 3, 0, cobble);
		set(8, 1, 1, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.FACING, Direction.SOUTH));
		set(8, 2, 1, Blocks.AIR.defaultBlockState());
		fill(8, 4, 0, 8, 11, 0, cobble);
		set(8, 12, 0, Blocks.CAMPFIRE.defaultBlockState());
		set(7, 1, 2, stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST, Half.BOTTOM));
		set(9, 1, 2, stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST, Half.BOTTOM));
		// seating facing the fire and a rug
		set(6, 1, 4, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, Half.BOTTOM));
		set(10, 1, 4, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, Half.BOTTOM));
		set(7, 1, 5, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, Half.BOTTOM));
		set(9, 1, 5, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, Half.BOTTOM));
		fill(6, 1, 6, 10, 1, 8, Blocks.CARPET.pick(DyeColor.RED).defaultBlockState());
		fill(7, 1, 7, 9, 1, 7, Blocks.CARPET.pick(DyeColor.WHITE).defaultBlockState());
		// ski shop (west side): frames hang on the roof's under-layer
		set(4, 1, 4, facing(ModBlocks.SKI_BENCH, Direction.EAST));
		set(4, 1, 3, Blocks.BARREL.defaultBlockState());
		frame(3, 2, 5, Direction.EAST, "freshtracks:race_skis");
		frame(3, 2, 7, Direction.EAST, "freshtracks:powder_skis");
		frame(3, 2, 9, Direction.EAST, "freshtracks:park_skis");
		frame(4, 3, 6, Direction.EAST, "freshtracks:ski_goggles");
		bed(3, 1, 3, Direction.NORTH, Blocks.BED.pick(DyeColor.RED));
		// board shop (east side)
		set(12, 1, 4, facing(ModBlocks.BOARD_PRESS, Direction.WEST));
		set(12, 1, 3, Blocks.BARREL.defaultBlockState());
		frame(13, 2, 5, Direction.WEST, "freshtracks:powder_board");
		frame(13, 2, 7, Direction.WEST, "freshtracks:park_board");
		frame(13, 2, 9, Direction.WEST, "freshtracks:all_mountain_board");
		frame(12, 3, 6, Direction.WEST, "freshtracks:ski_jacket");
		bed(13, 1, 3, Direction.NORTH, Blocks.BED.pick(DyeColor.PURPLE));
		// lift desk by the door
		set(10, 1, 9, facing(ModBlocks.LIFT_CONTROL, Direction.WEST));
		bed(5, 1, 9, Direction.WEST, Blocks.BED.pick(DyeColor.YELLOW));
		// lights
		set(8, 8, 6, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		fill(8, 7, 3, 8, 7, 8, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
		set(5, 4, 6, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		set(11, 4, 6, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		fill(5, 5, 6, 11, 5, 6, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
		// workers
		villager(5, 1, 6, "freshtracks:ski_tech");
		villager(11, 1, 6, "freshtracks:board_tech");
		villager(10, 1, 8, "freshtracks:liftie");
	}
}
