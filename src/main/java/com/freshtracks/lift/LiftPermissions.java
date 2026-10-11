package com.freshtracks.lift;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/** Who may change a lift (open/close, rename, speed, type): creative players, operators, and the single-player host. */
public final class LiftPermissions {
	private LiftPermissions() {
	}

	public static boolean canEdit(ServerPlayer player) {
		return player.isCreative()
			|| player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
			|| player.level().getServer().isSingleplayerOwner(player.nameAndId());
	}
}
