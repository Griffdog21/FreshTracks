package com.freshtracks.lift;

import com.freshtracks.FreshTracks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Every lift in a dimension, saved with the world (data/freshtracks/lifts.dat). */
public final class LiftData extends SavedData {
	private record Saved(List<LiftLine> lines, Map<UUID, Double> phases) {
	}

	private static final Codec<Saved> SAVED_CODEC = RecordCodecBuilder.create(i -> i.group(
		LiftLine.CODEC.listOf().optionalFieldOf("lines", List.of()).forGetter(Saved::lines),
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.DOUBLE).optionalFieldOf("phases", Map.of()).forGetter(Saved::phases)
	).apply(i, Saved::new));

	public static final Codec<LiftData> CODEC = SAVED_CODEC.xmap(LiftData::new, LiftData::toSaved);

	public static final SavedDataType<LiftData> TYPE = new SavedDataType<>(
		FreshTracks.id("lifts"), LiftData::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	final Map<UUID, LiftLine> lines = new LinkedHashMap<>();
	final Map<UUID, Double> phases = new LinkedHashMap<>();

	public LiftData() {
	}

	private LiftData(Saved saved) {
		for (LiftLine line : saved.lines()) {
			lines.put(line.id, line);
		}
		phases.putAll(saved.phases());
	}

	private Saved toSaved() {
		return new Saved(new ArrayList<>(lines.values()), new LinkedHashMap<>(phases));
	}
}
