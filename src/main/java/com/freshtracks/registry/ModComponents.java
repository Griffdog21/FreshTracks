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

	private ModComponents() {
	}

	public static void init() {
	}
}
