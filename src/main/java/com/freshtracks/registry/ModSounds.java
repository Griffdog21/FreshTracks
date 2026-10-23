package com.freshtracks.registry;

import com.freshtracks.FreshTracks;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
	public static final SoundEvent CLICK_IN = register("rig.click_in");
	public static final SoundEvent CLICK_OUT = register("rig.click_out");
	public static final SoundEvent CARVE = register("rig.carve");
	public static final SoundEvent GLIDE = register("rig.glide");
	public static final SoundEvent POWDER = register("rig.powder");
	public static final SoundEvent SKID = register("rig.skid");
	public static final SoundEvent ICE_SCRAPE = register("rig.ice_scrape");
	public static final SoundEvent ROCK_SCRAPE = register("rig.rock_scrape");
	public static final SoundEvent WIND = register("rig.wind");
	public static final SoundEvent POP = register("rig.pop");
	public static final SoundEvent LAND = register("rig.land");
	public static final SoundEvent LAND_POWDER = register("rig.land_powder");
	public static final SoundEvent WIPEOUT = register("rig.wipeout");
	public static final SoundEvent CORE_SHOT = register("rig.core_shot");
	public static final SoundEvent TRICK = register("rig.trick");
	public static final SoundEvent BONK = register("rig.bonk");
	public static final SoundEvent CHAIR_CLACK = register("lift.chair_clack");
	public static final SoundEvent LIFT_HUM = register("lift.hum");
	public static final SoundEvent LIFT_BELL = register("lift.bell");
	public static final SoundEvent BLIZZARD = register("weather.blizzard");
	/** Ski Patrol's avalanche charges going off up high on a powder morning: a deep thud rolling round the valley. */
	public static final SoundEvent AVALANCHE_CONTROL = register("weather.avalanche_control");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = FreshTracks.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
	}
}
