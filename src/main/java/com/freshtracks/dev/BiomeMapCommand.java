package com.freshtracks.dev;

import com.freshtracks.FreshTracks;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Development only: {@code /ftdev biomemap [samples] [step]} renders the surface biomes around the player to
 * run/biomemap.png (no chunks are generated), and prints how much of the map each biome covers.
 */
public final class BiomeMapCommand {
	private static final Map<String, Integer> COLORS = new HashMap<>();

	static {
		COLORS.put("freshtracks:powder_bowls", 0xFF2E7BFF);
		COLORS.put("freshtracks:frosted_glades", 0xFF19B7A8);
		COLORS.put("freshtracks:alpenglow_peaks", 0xFFFF5FA8);
		COLORS.put("freshtracks:glacier", 0xFF7FF0FF);
		COLORS.put("freshtracks:larch_basin", 0xFFFF9A1F);
		COLORS.put("freshtracks:aspen_hollow", 0xFFFFE03A);
		COLORS.put("minecraft:snowy_slopes", 0xFFE6E6F0);
		COLORS.put("minecraft:grove", 0xFF8FA89C);
		COLORS.put("minecraft:jagged_peaks", 0xFFB8B8C8);
		COLORS.put("minecraft:frozen_peaks", 0xFFC8E0F0);
		COLORS.put("minecraft:stony_peaks", 0xFF8A8A8A);
		COLORS.put("minecraft:meadow", 0xFF9CCF6A);
		COLORS.put("minecraft:cherry_grove", 0xFFF2B6D0);
		COLORS.put("minecraft:snowy_taiga", 0xFF5E7F6E);
		COLORS.put("minecraft:snowy_plains", 0xFFF4F4F4);
	}

	private BiomeMapCommand() {
	}

	public static void init() {
		if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
			return;
		}
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("ftdev")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("prefab")
				.then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
					.suggests((ctx, builder) -> {
						for (String n : PrefabBuilder.NAMES) {
							builder.suggest(n);
						}
						return builder.buildFuture();
					})
					.executes(ctx -> {
						String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
						net.minecraft.core.BlockPos origin = net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition()).offset(3, 0, 3);
						Component result = PrefabBuilder.buildAndSave(ctx.getSource().getLevel(), origin, name);
						ctx.getSource().sendSuccess(() -> result, false);
						FreshTracks.LOG.info("{} at {}", result.getString(), origin.toShortString());
						return 1;
					})))
			.then(Commands.literal("count")
				.then(Commands.argument("radius", IntegerArgumentType.integer(4, 128)).executes(ctx -> count(ctx.getSource(),
					IntegerArgumentType.getInteger(ctx, "radius")))))
			.then(Commands.literal("surface")
				.then(Commands.argument("length", IntegerArgumentType.integer(1, 64)).executes(ctx -> {
					// prints the ride surface every quarter block along the way you're facing
					var src = ctx.getSource();
					var player = src.getPlayerOrException();
					double yaw = Math.toRadians(player.getYRot());
					double fx = -Math.sin(yaw);
					double fz = Math.cos(yaw);
					double y = player.getY();
					StringBuilder sb = new StringBuilder();
					for (double d = 0; d <= IntegerArgumentType.getInteger(ctx, "length"); d += 0.25) {
						double px = player.getX() + fx * d;
						double pz = player.getZ() + fz * d;
						var g = com.freshtracks.rig.SurfaceSampler.ground(src.getLevel(), px, pz, y);
						var c = com.freshtracks.rig.SurfaceSampler.column(src.getLevel(), net.minecraft.util.Mth.floor(px), net.minecraft.util.Mth.floor(pz), y);
						sb.append(String.format(java.util.Locale.ROOT, "%n  d=%.2f z=%.2f h=%.3f top=%.3f grad=(%.2f,%.2f)", d, pz, g.height(), c.top(), g.gradX(), g.gradZ()));
						y = g.height();
					}
					FreshTracks.LOG.info("[surface]{}", sb);
					return 1;
				})))
			.then(Commands.literal("peak")
				.then(Commands.argument("radius", IntegerArgumentType.integer(16, 4096))
					.executes(ctx -> SkiMapCommand.peak(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))))
			.then(Commands.literal("fallline").executes(ctx -> SkiMapCommand.fallLineCommand(ctx.getSource())))
			.then(Commands.literal("skimap")
				.executes(ctx -> SkiMapCommand.run(ctx.getSource(), 4096, 8))
				.then(Commands.argument("size", IntegerArgumentType.integer(256, 16384))
					.executes(ctx -> SkiMapCommand.run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "size"), 8))
					.then(Commands.argument("step", IntegerArgumentType.integer(1, 64))
						.executes(ctx -> SkiMapCommand.run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "size"),
							IntegerArgumentType.getInteger(ctx, "step"))))))
			.then(Commands.literal("biomemap")
				.executes(ctx -> run(ctx.getSource(), 200, 16))
				.then(Commands.argument("samples", IntegerArgumentType.integer(16, 600))
					.executes(ctx -> run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "samples"), 16))
					.then(Commands.argument("step", IntegerArgumentType.integer(4, 256))
						.executes(ctx -> run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "samples"),
							IntegerArgumentType.getInteger(ctx, "step"))))))));
	}

	private static int run(CommandSourceStack source, int samples, int step) {
		ServerLevel level = source.getLevel();
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState randomState = level.getChunkSource().randomState();
		BiomeResolver resolver = generator.getBiomeSource().createCachingResolver(randomState);
		int cx = (int) source.getPosition().x;
		int cz = (int) source.getPosition().z;
		BufferedImage image = new BufferedImage(samples, samples, BufferedImage.TYPE_INT_ARGB);
		Map<String, Integer> counts = new TreeMap<>();
		for (int i = 0; i < samples; i++) {
			for (int j = 0; j < samples; j++) {
				int x = cx + (i - samples / 2) * step;
				int z = cz + (j - samples / 2) * step;
				int y = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
				Holder<Biome> biome = resolver.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(Math.max(y - 2, level.getMinY())), QuartPos.fromBlock(z));
				String id = biome.unwrapKey().map(k -> k.identifier().toString()).orElse("?");
				counts.merge(id, 1, Integer::sum);
				int color = COLORS.getOrDefault(id, fallback(id, y));
				if (i == samples / 2 && j == samples / 2) {
					color = 0xFFFF0000;
				}
				image.setRGB(i, j, color);
			}
		}
		File out = FabricLoader.getInstance().getGameDir().resolve("biomemap.png").toFile();
		try {
			ImageIO.write(image, "png", out);
		} catch (IOException e) {
			source.sendFailure(Component.literal("Could not write " + out + ": " + e.getMessage()));
			return 0;
		}
		int total = samples * samples;
		StringBuilder report = new StringBuilder("Biome map " + (samples * step) + " blocks wide -> " + out.getName() + "\n");
		counts.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue())
			.filter(e -> COLORS.containsKey(e.getKey()) || e.getValue() * 200 >= total)
			.forEach(e -> report.append(String.format("%5.2f%% %s%n", 100.0 * e.getValue() / total, e.getKey())));
		FreshTracks.LOG.info(report.toString());
		source.sendSuccess(() -> Component.literal("Wrote " + out.getName() + " (details in the log)"), false);
		return 1;
	}

	/** Counts Fresh Tracks blocks, snow layers and a few markers in the loaded area around the source. */
	private static int count(CommandSourceStack source, int radius) {
		ServerLevel level = source.getLevel();
		net.minecraft.core.BlockPos centre = net.minecraft.core.BlockPos.containing(source.getPosition());
		Map<String, Integer> counts = new TreeMap<>();
		net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				if (!level.hasChunk((centre.getX() + x) >> 4, (centre.getZ() + z) >> 4)) {
					continue;
				}
				for (int y = -48; y <= 48; y++) {
					pos.set(centre.getX() + x, centre.getY() + y, centre.getZ() + z);
					var state = level.getBlockState(pos);
					String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
					if (id.startsWith("freshtracks:") || id.equals("minecraft:snow") || id.equals("minecraft:powder_snow")
						|| id.equals("minecraft:blue_ice") || id.equals("minecraft:packed_ice") || id.equals("minecraft:cobblestone")) {
						String key = id.equals("minecraft:snow") ? id + "[" + state.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) + "]" : id;
						counts.merge(key, 1, Integer::sum);
					}
				}
			}
		}
		FreshTracks.LOG.info("Block count r={} at {}: {}", radius, centre.toShortString(), counts);
		source.sendSuccess(() -> Component.literal(counts.toString()), false);
		return 1;
	}

	private static int fallback(String id, int y) {
		if (id.contains("ocean") || id.contains("river")) {
			return 0xFF203A80;
		}
		int h = id.hashCode();
		int r = 40 + (h & 0x3F);
		int g = 90 + ((h >> 8) & 0x3F);
		int b = 40 + ((h >> 16) & 0x3F);
		double shade = Math.max(0.6, Math.min(1.2, (y - 40) / 80.0));
		r = (int) Math.min(255, r * shade);
		g = (int) Math.min(255, g * shade);
		b = (int) Math.min(255, b * shade);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}
}
