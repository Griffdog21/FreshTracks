package com.freshtracks.advancement;

import com.freshtracks.FreshTracks;
import com.freshtracks.gear.GearItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Fresh Tracks advancements (data/freshtracks/advancement). Events: click_in, lift, build_lift, yard_sale, big_air,
 * trick_1000, speed_100, fresh_tracks, race_finish, dressed.
 */
public final class ModCriteria {
	public static final RideTrigger RIDE = Registry.register(BuiltInRegistries.TRIGGER_TYPES, FreshTracks.id("ride"), new RideTrigger());

	private ModCriteria() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 40 != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (dressed(player)) {
					fire(player, "dressed");
				}
			}
		});
	}

	public static void fire(ServerPlayer player, String event) {
		RIDE.trigger(player, event);
	}

	/** Goggles or a helmet, jacket, snow pants and ski or snowboard boots. */
	private static boolean dressed(ServerPlayer player) {
		return GearItem.hasPerk(player, GearItem.Perk.GOGGLES)
			&& player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof GearItem
			&& player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof GearItem
			&& player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof GearItem;
	}
}
