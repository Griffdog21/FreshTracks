package com.freshtracks.weather;

import com.freshtracks.FreshTracks;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Last completed grooming day for each visited chunk; unloading/restarting must not erase daytime tracks. */
public final class GroomingData extends SavedData {
	private static final Codec<GroomingData> CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG)
		.xmap(GroomingData::new, data -> data.days).fieldOf("chunks").codec();
	public static final SavedDataType<GroomingData> TYPE = new SavedDataType<>(FreshTracks.id("grooming"), GroomingData::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
	final Map<String, Long> days;
	public GroomingData() { this(Map.of()); }
	private GroomingData(Map<String, Long> days) { this.days = new HashMap<>(days); }
	long day(long chunk) { return days.getOrDefault(Long.toString(chunk), Long.MIN_VALUE); }
	void finished(long chunk, long day) { days.put(Long.toString(chunk), day); setDirty(); }
}
