package com.freshtracks.dev;

import com.freshtracks.registry.ModBlocks;
import com.freshtracks.weather.GroomedSnowBlock;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Dev-only tools for building show resorts (the test world): groom a run into corduroy, and shape a terrain park
 * jump out of snow. Driven by tools/dev/build_demo.py through the dev bridge.
 *
 * <pre>
 * /ftbuild groom x0 z0 x1 z1 halfWidth      corduroy along a line, halfWidth blocks either side
 * /ftbuild kicker x z yaw height length width   a snow ramp whose lip is at (x, z), launching towards yaw
 * </pre>
 */
public final class ResortBuildCommands {
	private ResortBuildCommands() {
	}

	public static void init() {
		if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
			return;
		}
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("ftbuild")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("groom").then(Commands.argument("args", StringArgumentType.greedyString()).executes(ctx -> {
				double[] a = numbers(StringArgumentType.getString(ctx, "args"), 5);
				int n = groom(ctx.getSource().getLevel(), a[0], a[1], a[2], a[3], a[4]);
				ctx.getSource().sendSuccess(() -> Component.literal("Groomed " + n + " columns"), false);
				return n;
			})))
			.then(Commands.literal("kicker").then(Commands.argument("args", StringArgumentType.greedyString()).executes(ctx -> {
				double[] a = numbers(StringArgumentType.getString(ctx, "args"), 6);
				int n = kicker(ctx.getSource().getLevel(), a[0], a[1], a[2], a[3], a[4], a[5]);
				ctx.getSource().sendSuccess(() -> Component.literal("Kicker: " + n + " columns"), false);
				return n;
			})))));
	}

	private static double[] numbers(String text, int count) {
		String[] parts = text.trim().split("\\s+");
		double[] out = new double[count];
		for (int i = 0; i < count; i++) {
			out[i] = Double.parseDouble(parts[i]);
		}
		return out;
	}

	/** The top snow of the column at (x, z): the snow layer / powder block, or the space above a full snow block. */
	private static BlockPos topSnow(ServerLevel level, int x, int z) {
		// thin snow (one layer) doesn't block motion, so the heightmap stops under it: look at the block above too
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos above = new BlockPos(x, y, z);
		if (level.getBlockState(above).getBlock() instanceof SnowLayerBlock) {
			return above;
		}
		return above.below();
	}

	/** Turns the snow along a line into corduroy, the lines running along it (the way the snowcat drove). */
	static int groom(ServerLevel level, double x0, double z0, double x1, double z1, double half) {
		double dx = x1 - x0;
		double dz = z1 - z0;
		double len = Math.max(1.0e-6, Math.hypot(dx, dz));
		double ux = dx / len;
		double uz = dz / len;
		Direction.Axis axis = Math.abs(ux) >= Math.abs(uz) ? Direction.Axis.X : Direction.Axis.Z;
		Set<Long> done = new HashSet<>();
		int count = 0;
		for (double d = 0; d <= len; d += 0.5) {
			for (double l = -half; l <= half; l += 0.5) {
				int x = Mth.floor(x0 + ux * d - uz * l);
				int z = Mth.floor(z0 + uz * d + ux * l);
				if (!done.add(((long) x << 32) | (z & 0xFFFFFFFFL))) {
					continue;
				}
				BlockPos pos = topSnow(level, x, z);
				BlockState state = level.getBlockState(pos);
				BlockState groomed = ModBlocks.GROOMED_SNOW.defaultBlockState().setValue(GroomedSnowBlock.AXIS, axis);
				if (state.getBlock() instanceof SnowLayerBlock) {
					level.setBlock(pos, groomed.setValue(SnowLayerBlock.LAYERS, state.getValue(SnowLayerBlock.LAYERS)), Block.UPDATE_CLIENTS);
					count++;
				} else if ((state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) && level.getBlockState(pos.above()).isAir()) {
					level.setBlock(pos.above(), groomed.setValue(SnowLayerBlock.LAYERS, 1), Block.UPDATE_CLIENTS);
					count++;
				}
			}
		}
		return count;
	}

	/**
	 * A terrain park jump: a ramp of packed snow rising steeper towards its lip at (x, z), rounded off at the sides,
	 * {@code height} tall at the lip, {@code length} long, {@code width} wide; you launch towards {@code yaw}.
	 */
	static int kicker(ServerLevel level, double x, double z, double yaw, double height, double length, double width) {
		double rad = Math.toRadians(yaw);
		double ux = -Math.sin(rad);
		double uz = Math.cos(rad);
		double half = width / 2;
		Map<Long, Double> rise = new HashMap<>();
		for (double a = -length; a <= 0; a += 0.25) {
			for (double l = -half; l <= half; l += 0.25) {
				int bx = Mth.floor(x + ux * a - uz * l);
				int bz = Mth.floor(z + uz * a + ux * l);
				double t = (a + length) / length;
				double side = 1.0 - Math.pow(Math.abs(l) / half, 4);
				double r = height * Math.pow(t, 1.6) * side;
				rise.merge(((long) bx << 32) | (bz & 0xFFFFFFFFL), r, Math::max);
			}
		}
		BlockState packed = Blocks.SNOW_BLOCK.defaultBlockState();
		for (Map.Entry<Long, Double> e : rise.entrySet()) {
			int bx = (int) (e.getKey() >> 32);
			int bz = (int) (long) e.getKey();
			BlockPos pos = topSnow(level, bx, bz);
			BlockState state = level.getBlockState(pos);
			double groundTop = state.getBlock() instanceof SnowLayerBlock ? pos.getY() + state.getValue(SnowLayerBlock.LAYERS) / 8.0 : pos.getY() + 1.0;
			double top = groundTop + e.getValue();
			int base = state.getBlock() instanceof SnowLayerBlock ? pos.getY() : pos.getY() + 1;
			int full = Mth.floor(top);
			for (int y = base; y < full; y++) {
				level.setBlock(new BlockPos(bx, y, bz), packed, Block.UPDATE_CLIENTS);
			}
			int layers = (int) Math.round((top - full) * 8);
			BlockPos cap = new BlockPos(bx, full, bz);
			if (layers > 0 && full >= base) {
				level.setBlock(cap, ModBlocks.GROOMED_SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, Math.min(8, layers))
					.setValue(GroomedSnowBlock.AXIS, Math.abs(ux) >= Math.abs(uz) ? Direction.Axis.X : Direction.Axis.Z), Block.UPDATE_CLIENTS);
			} else if (full >= base && level.getBlockState(cap).getBlock() instanceof SnowLayerBlock) {
				level.setBlock(cap, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		return rise.size();
	}
}
