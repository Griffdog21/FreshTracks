package com.freshtracks.command;

import com.freshtracks.lift.ChairStyle;
import com.freshtracks.lift.LiftLine;
import com.freshtracks.lift.LiftManager;
import com.freshtracks.lift.TowerBuilder;
import com.freshtracks.lift.block.LiftTerminalBlock;
import com.freshtracks.registry.ModBlocks;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** /freshtracks ... (operators). */
public final class FreshTracksCommand {
	private FreshTracksCommand() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> register(dispatcher));
	}

	private static int buildSlope(CommandSourceStack source, boolean steep) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = source.getLevel();
		Direction forward = player.getDirection();
		BlockPos origin = player.blockPosition().relative(forward, 3).above(steep ? 175 : 95);
		TestSlopeBuilder.build(level, origin, forward, steep);
		BlockPos start = origin.relative(forward, 4).above(1);
		player.teleportTo(level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, java.util.Set.of(), forward.toYRot(), 10f, true);
		source.sendSuccess(() -> Component.literal("Built a " + (steep ? "steep " : "") + TestSlopeBuilder.LENGTH + "-block test run. Drop in!"), true);
		return 1;
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("freshtracks")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("slope").executes(ctx -> buildSlope(ctx.getSource(), false))
				.then(Commands.literal("steep").executes(ctx -> buildSlope(ctx.getSource(), true))))
			.then(Commands.literal("weather")
				.then(Commands.literal("today").executes(ctx -> {
					var event = com.freshtracks.weather.SnowWeather.event(ctx.getSource().getLevel());
					ctx.getSource().sendSuccess(() -> Component.translatable(event.translationKey()), false);
					return 1;
				}))
				.then(Commands.literal("set")
					.then(Commands.argument("event", StringArgumentType.word())
						.suggests((ctx, builder) -> {
							for (var e : com.freshtracks.weather.SnowEvent.values()) {
								builder.suggest(e.getSerializedName());
							}
							return builder.buildFuture();
						})
						.executes(ctx -> {
							String name = StringArgumentType.getString(ctx, "event");
							for (var e : com.freshtracks.weather.SnowEvent.values()) {
								if (e.getSerializedName().equalsIgnoreCase(name)) {
									com.freshtracks.weather.SnowWeather.set(ctx.getSource().getLevel(), e);
									ctx.getSource().sendSuccess(() -> Component.literal("Mountain weather set: ").append(Component.translatable(e.translationKey())), true);
									return 1;
								}
							}
							ctx.getSource().sendFailure(Component.literal("Unknown weather " + name));
							return 0;
						})))
				.then(Commands.literal("auto")
					.then(Commands.argument("on", com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(ctx -> {
						boolean on = com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "on");
						com.freshtracks.weather.SnowWeather.setAuto(ctx.getSource().getLevel(), on);
						ctx.getSource().sendSuccess(() -> Component.literal(on ? "A new mountain forecast every dawn." : "Mountain weather pinned."), true);
						return 1;
					}))))
			.then(Commands.literal("lift")
				.then(Commands.literal("list").executes(ctx -> {
					ServerLevel level = ctx.getSource().getLevel();
					var lines = LiftManager.lines(level);
					if (lines.isEmpty()) {
						ctx.getSource().sendSuccess(() -> Component.literal("No lifts in this dimension."), false);
					}
					for (LiftLine line : lines) {
						Vec3 load = line.loadPoint();
						ctx.getSource().sendSuccess(() -> LiftTerminalBlock.status(line).copy()
							.append(Component.literal(String.format("  load at %.1f %.1f %.1f", load.x, load.y, load.z))), false);
						StringBuilder pts = new StringBuilder("rope:");
						for (Vec3 p : line.ropePoints().subList(0, Math.min(4, line.ropePoints().size()))) {
							pts.append(String.format(" (%.1f %.1f %.1f)", p.x, p.y, p.z));
						}
						ctx.getSource().sendSuccess(() -> Component.literal(pts.toString()), false);
					}
					return lines.size();
				}))
				.then(Commands.literal("create")
					.then(Commands.argument("name", StringArgumentType.string())
						.then(Commands.argument("from", BlockPosArgument.blockPos())
							.then(Commands.argument("to", BlockPosArgument.blockPos())
								.executes(ctx -> createLift(ctx.getSource(), StringArgumentType.getString(ctx, "name"),
									BlockPosArgument.getLoadedBlockPos(ctx, "from"), BlockPosArgument.getLoadedBlockPos(ctx, "to"), ChairStyle.QUAD))
								.then(Commands.argument("style", StringArgumentType.word())
									.suggests((c, b) -> {
										for (ChairStyle s : ChairStyle.values()) {
											b.suggest(s.getSerializedName());
										}
										return b.buildFuture();
									})
									.executes(ctx -> createLift(ctx.getSource(), StringArgumentType.getString(ctx, "name"),
										BlockPosArgument.getLoadedBlockPos(ctx, "from"), BlockPosArgument.getLoadedBlockPos(ctx, "to"),
										parseStyle(StringArgumentType.getString(ctx, "style")))))))))
				.then(Commands.literal("cabin")
					.then(Commands.argument("paint", StringArgumentType.word())
						.suggests((c, b) -> {
							for (com.freshtracks.lift.Cabin l : com.freshtracks.lift.Cabin.values()) {
								b.suggest(l.getSerializedName());
							}
							return b.buildFuture();
						})
						.then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
							ServerLevel level = ctx.getSource().getLevel();
							String name = StringArgumentType.getString(ctx, "name");
							com.freshtracks.lift.Cabin paint = com.freshtracks.lift.Cabin.byName(StringArgumentType.getString(ctx, "paint"));
							int n = 0;
							for (LiftLine line : LiftManager.lines(level)) {
								if (line.name.equalsIgnoreCase(name) || name.equals("*")) {
									line.cabin = paint;
									LiftManager.changed(level, line);
									n++;
									ctx.getSource().sendSuccess(() -> Component.literal(line.name + " cabins: " + line.cabin.getSerializedName()), true);
								}
							}
							if (n == 0) {
								ctx.getSource().sendFailure(Component.literal("No lift called " + name));
							}
							return n;
						}))))
				.then(Commands.literal("livery")
					.then(Commands.argument("livery", StringArgumentType.word())
						.suggests((c, b) -> {
							for (com.freshtracks.lift.Livery l : com.freshtracks.lift.Livery.values()) {
								b.suggest(l.getSerializedName());
							}
							return b.buildFuture();
						})
						.then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
							ServerLevel level = ctx.getSource().getLevel();
							String name = StringArgumentType.getString(ctx, "name");
							com.freshtracks.lift.Livery livery = com.freshtracks.lift.Livery.byName(StringArgumentType.getString(ctx, "livery"));
							int n = 0;
							for (LiftLine line : LiftManager.lines(level)) {
								if (line.name.equalsIgnoreCase(name) || name.equals("*")) {
									line.livery = livery;
									LiftManager.changed(level, line);
									n++;
									ctx.getSource().sendSuccess(() -> Component.literal(line.name + " terminals: " + line.livery().getSerializedName()), true);
								}
							}
							if (n == 0) {
								ctx.getSource().sendFailure(Component.literal("No lift called " + name));
							}
							return n;
						}))))
				.then(Commands.literal("open")
					.then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> setRunning(ctx.getSource(), StringArgumentType.getString(ctx, "name"), true))))
				.then(Commands.literal("close")
					.then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> setRunning(ctx.getSource(), StringArgumentType.getString(ctx, "name"), false))))
				.then(Commands.literal("remove")
					.then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
						ServerLevel level = ctx.getSource().getLevel();
						String name = StringArgumentType.getString(ctx, "name");
						for (LiftLine line : LiftManager.lines(level)) {
							if (line.name.equalsIgnoreCase(name)) {
								LiftManager.remove(level, line.id);
								ctx.getSource().sendSuccess(() -> Component.literal("Removed " + line.name), true);
								return 1;
							}
						}
						ctx.getSource().sendFailure(Component.literal("No lift called " + name));
						return 0;
					})))));
	}

	private static int setRunning(CommandSourceStack source, String name, boolean running) {
		ServerLevel level = source.getLevel();
		for (LiftLine line : LiftManager.lines(level)) {
			if (line.name.equalsIgnoreCase(name) || name.equals("*")) {
				line.running = running;
				LiftManager.changed(level, line);
				source.sendSuccess(() -> Component.literal(line.name + (running ? " is open" : " is closed")), true);
			}
		}
		return 1;
	}

	private static ChairStyle parseStyle(String name) {
		return ChairStyle.byName(name);
	}

	/** Puts terminals at the two spots (on the surface below them), plans and builds towers, and opens the lift. */
	private static int createLift(CommandSourceStack source, String name, BlockPos from, BlockPos to, ChairStyle style) {
		ServerLevel level = source.getLevel();
		BlockPos a = surface(level, from);
		BlockPos b = surface(level, to);
		BlockPos bottom = a.getY() <= b.getY() ? a : b;
		BlockPos top = bottom == a ? b : a;
		if (Math.sqrt(bottom.distSqr(top)) < 12) {
			source.sendFailure(Component.literal("Terminals must be at least 12 blocks apart."));
			return 0;
		}
		for (BlockPos p : new BlockPos[]{bottom, top}) {
			if (LiftManager.findByTerminal(level, p) != null) {
				source.sendFailure(Component.literal("There's already a lift at " + p.toShortString()));
				return 0;
			}
			level.setBlock(p, ModBlocks.LIFT_TERMINAL.defaultBlockState(), Block.UPDATE_ALL);
		}
		List<BlockPos> towers = TowerBuilder.plan(level, bottom, top, style);
		TowerBuilder.build(level, towers);
		LiftLine line = new LiftLine(UUID.randomUUID(), name, bottom, top, towers, style, style.defaultSpeed, style.defaultSpacing, true);
		TowerBuilder.clearCorridor(level, line);
		LiftManager.add(level, line);
		com.freshtracks.lift.LiftSetup.placeMarks(level, line);
		Vec3 load = line.loadPoint();
		source.sendSuccess(() -> Component.literal(String.format("%s is open: %d towers, %.0f blocks. Board at %.1f %.1f %.1f",
			name, towers.size(), line.upLength(), load.x, load.y, load.z)), true);
		return 1;
	}

	/** First spot on solid ground at (x, z): snow layers, grass and flowers get replaced. */
	public static BlockPos surface(ServerLevel level, BlockPos pos) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(pos.getX(), y, pos.getZ());
		while (p.getY() > level.getMinY() && level.getBlockState(p.below()).canBeReplaced()) {
			p.move(0, -1, 0);
		}
		return p.immutable();
	}
}
