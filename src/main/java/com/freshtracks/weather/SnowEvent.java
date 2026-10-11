package com.freshtracks.weather;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * The mountain's weather for a day. One is rolled every dawn (see {@link SnowWeather}). Effects only show where it's
 * cold enough to snow, the rest of the world just gets the matching vanilla weather.
 */
public enum SnowEvent implements StringRepresentable {
	//                 id              snow   thunder windMin windMax gusty  fog   cold accum maxLayers lifts  surface
	CLEAR("clear", false, false, 0.00f, 0.20f, false, 0.00f, 0, 0, 0, 1.00f, Mod.NONE),
	BLUEBIRD("bluebird", false, false, 0.00f, 0.10f, false, 0.00f, 0, 0, 0, 1.00f, Mod.NONE),
	POWDER_DAY("powder_day", false, false, 0.00f, 0.15f, false, 0.00f, 1, 0, 0, 1.00f, Mod.NONE),
	SNOWSTORM("snowstorm", true, false, 0.20f, 0.50f, false, 0.30f, 2, 2, 3, 1.00f, Mod.NONE),
	BLIZZARD("blizzard", true, false, 0.60f, 1.00f, true, 0.85f, 4, 4, 5, 0.50f, Mod.NONE),
	THUNDERSNOW("thundersnow", true, true, 0.40f, 0.70f, true, 0.50f, 3, 3, 4, 0.00f, Mod.NONE),
	HIGH_WIND("high_wind", false, false, 0.55f, 1.00f, true, 0.10f, 1, 0, 0, 0.75f, Mod.NONE),
	COLD_SNAP("cold_snap", false, false, 0.10f, 0.30f, false, 0.00f, 3, 0, 0, 1.00f, Mod.ICY),
	FLAT_LIGHT("flat_light", false, false, 0.00f, 0.10f, false, 0.70f, 0, 0, 0, 1.00f, Mod.NONE),
	SPRING_SLUSH("spring_slush", false, false, 0.00f, 0.20f, false, 0.00f, 0, 0, 0, 1.00f, Mod.SLUSHY);

	public static final Codec<SnowEvent> CODEC = StringRepresentable.fromEnum(SnowEvent::values);

	public enum Mod {
		NONE, ICY, SLUSHY
	}

	private final String id;
	public final boolean snowing;
	public final boolean thunder;
	public final float windMin;
	public final float windMax;
	public final boolean gusty;
	/** 0 = clear air .. 1 = total whiteout. */
	public final float fog;
	/** Warmth points you need to stay unfrozen outside. */
	public final int cold;
	/** Snow layers placed per tick around each player while it lasts. */
	public final int accumulation;
	public final int maxLayers;
	/** Lift speed multiplier (0 = closed). */
	public final float lifts;
	public final Mod surface;

	SnowEvent(String id, boolean snowing, boolean thunder, float windMin, float windMax, boolean gusty, float fog, int cold,
		int accumulation, int maxLayers, float lifts, Mod surface) {
		this.id = id;
		this.snowing = snowing;
		this.thunder = thunder;
		this.windMin = windMin;
		this.windMax = windMax;
		this.gusty = gusty;
		this.fog = fog;
		this.cold = cold;
		this.accumulation = accumulation;
		this.maxLayers = maxLayers;
		this.lifts = lifts;
		this.surface = surface;
	}

	@Override
	public String getSerializedName() {
		return id;
	}

	public String translationKey() {
		return "weather.freshtracks." + id;
	}

	public boolean bluebird() {
		return this == BLUEBIRD || this == POWDER_DAY || this == COLD_SNAP;
	}

	public static SnowEvent byId(int id) {
		SnowEvent[] v = values();
		return id >= 0 && id < v.length ? v[id] : CLEAR;
	}
}
