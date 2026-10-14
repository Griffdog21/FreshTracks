package com.freshtracks.weather;

import com.freshtracks.FreshTracksConfig;
import com.freshtracks.registry.ModBlocks;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Morning snowcat pass with a fixed work budget. Never loads a chunk, changes a building, or restores terrain. */
public final class SlopeGrooming {
	@FunctionalInterface public interface Rule {
		/** Return groomed snow only for an approved run; null leaves this column alone. */
		BlockState groom(ServerLevel level, BlockPos pos, BlockState snow);
	}
	private static final List<Rule> rules = new ArrayList<>();
	private static final Map<ServerLevel, Work> work = new WeakHashMap<>();
	private static final int COLUMNS_PER_TICK = 96;
	private SlopeGrooming() {}
	public static void addRule(Rule rule) { rules.add(rule); }
	public static void init() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, newlyGenerated) -> {
			Work w = work.computeIfAbsent(level, key -> new Work());
			long id = chunk.getPos().pack(); w.loaded.add(id); w.enqueue(id);
		});
		ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
			Work w = work.get(level); if (w != null) w.loaded.remove(chunk.getPos().pack());
		});
		ServerTickEvents.END_LEVEL_TICK.register(SlopeGrooming::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> work.clear());
	}
	private static void tick(ServerLevel level) {
		if (!FreshTracksConfig.autoGroomRuns) return;
		Work w = work.get(level); if (w == null) return;
		long day = Math.floorDiv(level.getOverworldClockTime(), 24000L);
		GroomingData data = level.getDataStorage().computeIfAbsent(GroomingData.TYPE);
		if (day != w.day) {
			w.day = day; w.current = null; w.column = 0; w.queue.clear(); w.queued.clear();
			for (long id : w.loaded) if (data.day(id) != day) w.enqueue(id);
		}
		long start = System.nanoTime();
		for (int count = 0; count < COLUMNS_PER_TICK && System.nanoTime() - start < 2_000_000L; count++) {
			if (w.current == null) {
				if (w.queue.isEmpty()) return;
				long id = w.queue.removeFirst(); w.queued.remove(id);
				if (!w.loaded.contains(id) || data.day(id) == day) continue;
				w.current = new ChunkPos((int) id, (int) (id >> 32)); w.column = 0;
			}
			if (!w.loaded.contains(w.current.pack()) || level.getChunkSource().getChunkNow(w.current.x(), w.current.z()) == null) {
				w.current = null; continue;
			}
			int x = w.current.getMinBlockX() + (w.column & 15), z = w.current.getMinBlockZ() + (w.column >> 4);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
			BlockPos pos = new BlockPos(x, y, z);
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof SnowLayerBlock || state.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK)) {
				BlockState next = state.is(ModBlocks.GROOMED_SNOW) ? state.setValue(GroomedSnowBlock.WEAR, 0) : null;
				for (Rule rule : rules) { BlockState result = rule.groom(level, pos, state); if (result != null) { next = result; break; } }
				if (next != null && !next.equals(state)) level.setBlock(pos, next, Block.UPDATE_CLIENTS);
			}
			if (++w.column == 256) { data.finished(w.current.pack(), day); w.current = null; }
		}
	}
	private static final class Work {
		long day = Long.MIN_VALUE;
		final Set<Long> loaded = new HashSet<>(), queued = new HashSet<>();
		final ArrayDeque<Long> queue = new ArrayDeque<>();
		ChunkPos current;
		int column;
		void enqueue(long id) { if (queued.add(id)) queue.addLast(id); }
	}
}
