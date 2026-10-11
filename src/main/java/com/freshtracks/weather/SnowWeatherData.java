package com.freshtracks.weather;

import com.freshtracks.FreshTracks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** The mountain forecast, saved with the world. */
public final class SnowWeatherData extends SavedData {
	public static final Codec<SnowWeatherData> CODEC = RecordCodecBuilder.create(i -> i.group(
		SnowEvent.CODEC.optionalFieldOf("event", SnowEvent.CLEAR).forGetter(d -> d.event),
		SnowEvent.CODEC.optionalFieldOf("yesterday", SnowEvent.CLEAR).forGetter(d -> d.yesterday),
		Codec.LONG.optionalFieldOf("day", -1L).forGetter(d -> d.day),
		Codec.FLOAT.optionalFieldOf("wind_dir", 0f).forGetter(d -> d.windDir),
		Codec.BOOL.optionalFieldOf("auto", true).forGetter(d -> d.auto),
		Codec.INT.optionalFieldOf("dump_ticks", 0).forGetter(d -> d.dumpTicks)
	).apply(i, SnowWeatherData::new));

	public static final SavedDataType<SnowWeatherData> TYPE = new SavedDataType<>(
		FreshTracks.id("weather"), SnowWeatherData::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	SnowEvent event;
	SnowEvent yesterday;
	long day;
	/** Wind direction in degrees (Minecraft yaw it blows toward). */
	float windDir;
	/** Roll a new event every dawn. Off when an operator pins the weather. */
	boolean auto;
	/** Ticks left of "overnight dump" accumulation at the start of a powder day. */
	int dumpTicks;

	public SnowWeatherData() {
		this(SnowEvent.CLEAR, SnowEvent.CLEAR, -1L, 0f, true, 0);
	}

	private SnowWeatherData(SnowEvent event, SnowEvent yesterday, long day, float windDir, boolean auto, int dumpTicks) {
		this.event = event;
		this.yesterday = yesterday;
		this.day = day;
		this.windDir = windDir;
		this.auto = auto;
		this.dumpTicks = dumpTicks;
	}
}
