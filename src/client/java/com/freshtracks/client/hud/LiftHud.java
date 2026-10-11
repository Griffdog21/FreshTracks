package com.freshtracks.client.hud;

import com.freshtracks.client.lift.ClientLifts;
import com.freshtracks.lift.LiftLine;
import com.freshtracks.lift.LiftSeatEntity;
import com.freshtracks.rig.RigEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Lift help above the hotbar: where to stand, a countdown to the next chair, how long until the top, and how to
 * get off. Everything here is worked out on the client from its copy of the lifts.
 */
public final class LiftHud {
	private static @Nullable Component message;
	private static int colour = 0xFFFFFFFF;

	private LiftHud() {
	}

	public static void tick(Minecraft mc) {
		message = null;
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return;
		}
		if (player.getVehicle() instanceof LiftSeatEntity seat) {
			riding(seat);
			return;
		}
		if (player.getVehicle() instanceof RigEntity rig && rig.isTowed()) {
			ClientLifts.Entry entry = ClientLifts.byNetId(rig.towLine());
			if (entry != null) {
				message = Component.translatable("hud.freshtracks.lift.towed", entry.line.style.displayName());
				colour = 0xFFBFE6FF;
			}
			return;
		}
		if (player.getVehicle() instanceof RigEntity rig && rig.visualSpeed() > 0.25) {
			return; // skiing past the lift base, not queueing for it
		}
		ClientLifts.Entry nearest = null;
		double nearestS = 0;
		double best = 9 * 9;
		for (ClientLifts.Entry entry : ClientLifts.all()) {
			// the bottom loading spot, and the one at each mid station
			for (int m = -1; m < entry.line.midStations(); m++) {
				Vec3 load = m < 0 ? entry.line.loadPoint() : entry.line.midLoadPoint(m);
				double dx = player.getX() - load.x;
				double dz = player.getZ() - load.z;
				double d2 = dx * dx + dz * dz;
				if (d2 < best && Math.abs(player.getY() - load.y) < 4) {
					best = d2;
					nearest = entry;
					nearestS = m < 0 ? entry.line.loadS() : entry.line.midLoadS(m);
				}
			}
		}
		if (nearest == null) {
			return;
		}
		LiftLine line = nearest.line;
		Component name = Component.literal(line.name);
		if (!line.running || nearest.speed < 1.0e-3) {
			message = Component.translatable("hud.freshtracks.lift.closed", name);
			colour = 0xFFFF8A80;
		} else if (line.style.surface() && !(player.getVehicle() instanceof RigEntity)) {
			message = Component.translatable("hud.freshtracks.lift.need_skis", line.style.displayName());
			colour = 0xFFFFE082;
		} else if (inLoadingZone(player.position(), line, nearestS)) {
			double ticks = ClientLifts.ticksUntil(nearest, nearestS);
			if (ticks >= 0 && ticks < 14) {
				message = Component.translatable(line.style.surface() ? "hud.freshtracks.lift.grab" : "hud.freshtracks.lift.sit");
			} else {
				message = Component.translatable("hud.freshtracks.lift.stay", line.style.displayName(), String.format("%.1f", Math.max(0, ticks) / 20.0));
			}
			colour = 0xFF9CFF9C;
		} else {
			message = Component.translatable("hud.freshtracks.lift.go_load", name);
			colour = 0xFFFFFFFF;
		}
	}

	private static void riding(LiftSeatEntity seat) {
		ClientLifts.Entry entry = ClientLifts.byNetId(seat.lineNetId());
		if (entry == null) {
			return;
		}
		LiftLine line = entry.line;
		if (seat.sneakTicks() > 0) {
			int left = Math.max(0, LiftSeatEntity.BAIL_OUT_TICKS - seat.sneakTicks());
			message = Component.translatable("hud.freshtracks.lift.bail", String.format("%.1f", left / 20.0));
			colour = 0xFFFF8A80;
			return;
		}
		double s = line.chairS(entry.phase, seat.chairIndex());
		for (int m = 0; m < line.midStations(); m++) {
			if (s > line.midS(m) - 14 && s < line.midUnloadS(m)) {
				message = Component.translatable("hud.freshtracks.lift.mid_station", Component.literal(line.name));
				colour = 0xFFFFE082;
				return;
			}
		}
		if (s < line.unloadS() && entry.speed > 1.0e-3) {
			int seconds = (int) Math.round(line.ropeUntil(entry.phase, seat.chairIndex(), line.unloadS()) / entry.speed / 20.0);
			message = Component.translatable("hud.freshtracks.lift.riding", Component.literal(line.name), String.format("%d:%02d", seconds / 60, seconds % 60));
		} else {
			message = Component.translatable("hud.freshtracks.lift.riding_down", Component.literal(line.name));
		}
		colour = 0xFFBFE6FF;
	}

	/** Same test the server uses to pick riders up (LiftManager#board), around where a chair crosses the loading spot. */
	private static boolean inLoadingZone(Vec3 at, LiftLine line, double loadS) {
		LiftLine.RopePoint rope = line.ropeAt(loadS);
		Vec3 ahead = new Vec3(rope.direction().x, 0, rope.direction().z).normalize();
		Vec3 side = new Vec3(-ahead.z, 0, ahead.x);
		Vec3 centre = rope.pos().add(0, -line.style.hanger, 0);
		Vec3 rel = at.subtract(centre);
		double fwd = rel.dot(ahead);
		double lateral = line.style.width / 2 + (line.style.surface() ? 1.1 : 0.9);
		return Math.abs(rel.dot(side)) <= lateral && fwd >= -1.8 && fwd <= 1.6;
	}

	private static double groundAt(LiftLine line) {
		return line.bottom.getY();
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (message == null || mc.player == null || mc.gui.hud.isHidden() || mc.gui.screen() != null) {
			return;
		}
		Font font = mc.font;
		int width = font.width(message);
		int x = graphics.guiWidth() / 2 - width / 2;
		int y = graphics.guiHeight() - 86;
		graphics.fill(x - 4, y - 3, x + width + 4, y + 11, 0x88000000);
		graphics.text(font, message, x, y, colour, true);
	}
}
