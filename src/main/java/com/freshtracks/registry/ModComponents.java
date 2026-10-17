package com.freshtracks.registry;

import com.freshtracks.FreshTracks;
import com.freshtracks.lift.LiftCableItem;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModComponents {
	/** A lift cable that's been clicked on a terminal and is waiting for the other end. */
	public static final DataComponentType<LiftCableItem.Link> LIFT_LINK = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
		FreshTracks.id("lift_link"),
		DataComponentType.<LiftCableItem.Link>builder().persistent(LiftCableItem.Link.CODEC).networkSynchronized(LiftCableItem.Link.STREAM_CODEC).build());

	/** Ticks of riding left on a waxed base. */
	public static final DataComponentType<Integer> WAX = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
		FreshTracks.id("wax"),
		DataComponentType.<Integer>builder().persistent(com.mojang.serialization.Codec.INT).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).build());

	/** A pro-model tune on skis or a board (multiplies the model's stats) or extra warmth on gear: branded shop items. */
	public static final DataComponentType<com.freshtracks.economy.Tune> TUNE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
		FreshTracks.id("tune"),
		DataComponentType.<com.freshtracks.economy.Tune>builder().persistent(com.freshtracks.economy.Tune.CODEC).networkSynchronized(com.freshtracks.economy.Tune.STREAM_CODEC).build());

	/** Which shop catalog entry an item came from (unique and limited-edition items are tracked by it). */
	public static final DataComponentType<String> CATALOG = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
		FreshTracks.id("catalog"),
		DataComponentType.<String>builder().persistent(com.mojang.serialization.Codec.STRING).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8).build());

	private ModComponents() {
	}

	public static void init() {
	}
}
