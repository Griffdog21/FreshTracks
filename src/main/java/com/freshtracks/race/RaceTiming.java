package com.freshtracks.race;

import com.freshtracks.net.RaceTimerPayload;
import com.freshtracks.rig.RigEntity;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Race clocks. The server sweeps each rig's path between ticks for start and finish lines (a rig can cover two
 * blocks a tick, so checking only where it ends up would miss a one-block line) and times the run to the
 * hundredth by interpolating where along that path the line was crossed. A course is the pair (start line,
 * finish line), each named by its lowest block, so every lane of a wide line counts as the same course.
 */
public final class RaceTiming {
	private static final int MAX_TICKS = 20 * 60 * 30;
	private static final int OFF_RIG_GRACE = 30;

	private record Run(long startTick, double startFraction, BlockPos startLine) {
	}

	private static final Map<UUID, Run> RUNS = new HashMap<>();
	private static final Map<UUID, Integer> OFF_RIG = new HashMap<>();

	private RaceTiming() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(RaceTiming::tick);
	}

	/** Called by a ridden rig every server tick with where it was and where it is now. */
	public static void sweep(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 to) {
		double dist = to.distanceTo(from);
		int steps = Math.max(1, (int) Math.ceil(dist / 0.4));
		for (int i = 1; i <= steps; i++) {
			double t = i / (double) steps;
			Vec3 p = from.lerp(to, t);
			BlockPos pos = BlockPos.containing(p.x, p.y + 0.02, p.z);
			for (BlockPos check : new BlockPos[]{pos, pos.below()}) {
				BlockState state = level.getBlockState(check);
				if (state.getBlock() instanceof TimingLineBlock line) {
					crossed(level, player, check.immutable(), line.isFinish(), t);
					return;
				}
			}
		}
	}

	private static void crossed(ServerLevel level, ServerPlayer player, BlockPos pos, boolean finish, double fraction) {
		long now = level.getGameTime();
		UUID id = player.getUUID();
		Run run = RUNS.get(id);
		if (!finish) {
			BlockPos line = canonical(level, pos);
			if (run != null && run.startLine().equals(line) && now - run.startTick() < 40) {
				return; // still rolling over the same start line
			}
			RUNS.put(id, new Run(now, fraction, line));
			OFF_RIG.remove(id);
			ServerPlayNetworking.send(player, new RaceTimerPayload(RaceTimerPayload.START, 0, false));
			level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0f, 1.6f);
			return;
		}
		if (run == null) {
			return;
		}
		RUNS.remove(id);
		OFF_RIG.remove(id);
		double ticks = (now + fraction) - (run.startTick() + run.startFraction());
		int centis = (int) Math.round(ticks * 5.0);
		String course = key(run.startLine()) + ">" + key(canonical(level, pos));
		RaceRecords records = level.getDataStorage().computeIfAbsent(RaceRecords.TYPE);
		int[] before = records.record(course, id, player.getPlainTextName(), centis);
		boolean personalBest = before[0] < 0 || centis < before[0];
		boolean courseRecord = before[1] < 0 || centis < before[1];
		ServerPlayNetworking.send(player, new RaceTimerPayload(RaceTimerPayload.FINISH, centis, personalBest));
		com.freshtracks.advancement.ModCriteria.fire(player, "race_finish");
		level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0f, courseRecord ? 2.0f : 1.2f);

		MutableComponent message = Component.literal(player.getPlainTextName()).withStyle(ChatFormatting.AQUA)
			.append(Component.literal(" finished in ").withStyle(ChatFormatting.WHITE))
			.append(Component.literal(format(centis)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		if (courseRecord && before[1] >= 0) {
			message.append(Component.literal("  NEW COURSE RECORD!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
		} else if (personalBest && before[0] >= 0) {
			message.append(Component.literal("  personal best, " + format(before[0] - centis) + " faster").withStyle(ChatFormatting.GREEN));
		}
		for (ServerPlayer other : level.players()) {
			if (other == player || other.distanceToSqr(player) < 160 * 160) {
				other.sendSystemMessage(message);
			}
		}
		StringBuilder board = new StringBuilder("Course leaders: ");
		int place = 1;
		for (Map.Entry<String, Integer> e : records.top(course, 3)) {
			board.append(place++).append(". ").append(e.getKey()).append(' ').append(format(e.getValue())).append("   ");
		}
		player.sendSystemMessage(Component.literal(board.toString().trim()).withStyle(ChatFormatting.GRAY));
	}

	private static void tick(MinecraftServer server) {
		if (RUNS.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Run>> it = RUNS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Run> entry = it.next();
			UUID id = entry.getKey();
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null) {
				it.remove();
				OFF_RIG.remove(id);
				continue;
			}
			long elapsed = player.level().getGameTime() - entry.getValue().startTick();
			int off = player.getVehicle() instanceof RigEntity ? 0 : OFF_RIG.getOrDefault(id, 0) + 1;
			OFF_RIG.put(id, off);
			if (off > OFF_RIG_GRACE || elapsed > MAX_TICKS) {
				it.remove();
				OFF_RIG.remove(id);
				ServerPlayNetworking.send(player, new RaceTimerPayload(RaceTimerPayload.CANCEL, 0, false));
				player.sendSystemMessage(Component.literal("Run cancelled (DNF)").withStyle(ChatFormatting.GRAY), true);
			}
		}
	}

	/** The lowest block (by x, then z, then y) of the connected line that contains {@code pos}. */
	private static BlockPos canonical(ServerLevel level, BlockPos pos) {
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(pos);
		seen.add(pos);
		BlockPos best = pos;
		boolean finish = level.getBlockState(pos).getBlock() instanceof TimingLineBlock line && line.isFinish();
		while (!queue.isEmpty() && seen.size() < 256) {
			BlockPos p = queue.poll();
			if (p.getX() < best.getX() || (p.getX() == best.getX() && (p.getZ() < best.getZ() || (p.getZ() == best.getZ() && p.getY() < best.getY())))) {
				best = p;
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int dy = -1; dy <= 1; dy++) {
						BlockPos n = p.offset(dx, dy, dz);
						if (!seen.contains(n) && level.getBlockState(n).getBlock() instanceof TimingLineBlock other && other.isFinish() == finish) {
							seen.add(n);
							queue.add(n);
						}
					}
				}
			}
		}
		return best;
	}

	private static String key(BlockPos pos) {
		return pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	/** m:ss.cc */
	public static String format(int centis) {
		int minutes = centis / 6000;
		int seconds = (centis / 100) % 60;
		return String.format(Locale.ROOT, "%d:%02d.%02d", minutes, seconds, centis % 100);
	}
}
