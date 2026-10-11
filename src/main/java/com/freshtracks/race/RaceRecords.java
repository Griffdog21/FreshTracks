package com.freshtracks.race;

import com.freshtracks.FreshTracks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Best run times per course and player, saved with the world (data/freshtracks/race_records.dat). */
public final class RaceRecords extends SavedData {
	private record Saved(Map<String, Map<UUID, Integer>> best, Map<UUID, String> names) {
	}

	private static final Codec<Saved> SAVED_CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT)).optionalFieldOf("best", Map.of()).forGetter(Saved::best),
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING).optionalFieldOf("names", Map.of()).forGetter(Saved::names)
	).apply(i, Saved::new));

	public static final Codec<RaceRecords> CODEC = SAVED_CODEC.xmap(RaceRecords::new, RaceRecords::toSaved);
	public static final SavedDataType<RaceRecords> TYPE = new SavedDataType<>(
		FreshTracks.id("race_records"), RaceRecords::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	final Map<String, Map<UUID, Integer>> best = new HashMap<>();
	final Map<UUID, String> names = new HashMap<>();

	public RaceRecords() {
	}

	private RaceRecords(Saved saved) {
		saved.best().forEach((course, times) -> best.put(course, new HashMap<>(times)));
		names.putAll(saved.names());
	}

	private Saved toSaved() {
		return new Saved(best, names);
	}

	/** Records a finished run. Returns {personal best before or -1, course record before or -1}. */
	int[] record(String course, UUID player, String name, int centis) {
		Map<UUID, Integer> times = best.computeIfAbsent(course, k -> new HashMap<>());
		int previous = times.getOrDefault(player, -1);
		int record = times.values().stream().mapToInt(Integer::intValue).min().orElse(-1);
		if (previous < 0 || centis < previous) {
			times.put(player, centis);
			names.put(player, name);
			setDirty();
		}
		return new int[]{previous, record};
	}

	/** The fastest few on a course, fastest first, as "name time" pairs. */
	java.util.List<Map.Entry<String, Integer>> top(String course, int n) {
		return best.getOrDefault(course, Map.of()).entrySet().stream()
			.sorted(Map.Entry.comparingByValue())
			.limit(n)
			.map(e -> Map.entry(names.getOrDefault(e.getKey(), "?"), e.getValue()))
			.toList();
	}
}
