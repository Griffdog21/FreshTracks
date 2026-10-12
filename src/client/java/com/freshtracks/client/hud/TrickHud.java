package com.freshtracks.client.hud;

import com.freshtracks.rig.RigEntity;
import com.freshtracks.rig.TrickTracker;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/** Trick call-outs, combo counter and a small speedometer while riding. */
public final class TrickHud {
	private static final int SHOW_TICKS = 50;
	private static final Deque<Popup> POPUPS = new ArrayDeque<>();
	private static int runScore;
	private static int runScoreTicks;

	private record Popup(String title, String detail, int titleColor, int detailColor, long startTick) {
	}

	/** The running powder-turn line (one line that updates, rather than a popup per turn). */
	private static String powderText = "";
	private static int powderPoints;
	private static long powderTick = -1000;

	private TrickHud() {
	}

	private static long now() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? 0 : mc.level.getGameTime();
	}

	public static void showTrick(TrickTracker.Result result) {
		int skiBucks = result.points() / 100;
		String detail = String.format(Locale.ROOT, "%,d pts  ·  +%,d Ski Bucks", result.points(), skiBucks);
		if (result.combo() > 0) {
			detail += "   x" + (1 + 0.5f * result.combo() == Math.floor(1 + 0.5f * result.combo())
				? String.valueOf((int) (1 + 0.5f * result.combo()))
				: String.format(Locale.ROOT, "%.1f", 1 + 0.5f * result.combo())) + " combo";
		}
		int color = switch (result.landing()) {
			case STOMPED -> 0xFFE45C;
			case CLEAN -> 0xFFFFFF;
			case SKETCHY -> 0xC9C9C9;
		};
		String title = result.name();
		if (result.landing() != TrickTracker.Landing.CLEAN) {
			detail = result.landing().label + "   " + detail;
		}
		push(new Popup(title, detail, color, 0x7FE0FF, now()));
	}

	/** A linked powder turn: updates the powder line under the trick call-outs. */
	public static void showPowderTurn(TrickTracker.Result result) {
		powderText = result.name();
		powderPoints = result.points();
		powderTick = now();
	}

	public static void showWipeout(String reason) {
		String detail = switch (reason == null ? "" : reason) {
			case "wall" -> "Hit something hard";
			case "under-rotated" -> "Didn't come all the way round";
			case "sideways" -> "Landed sideways";
			case "flat" -> "Landed flat";
			default -> "Wipeout";
		};
		push(new Popup("Yard sale!", detail, 0xFF6B5E, 0xFFFFFF, now()));
	}

	public static void showMessage(String text, int color) {
		push(new Popup(text, "", color, 0xFFFFFF, now()));
	}

	public static void endRun(int score) {
		if (score > 0) {
			runScore = score;
			runScoreTicks = 100;
		}
	}

	private static void push(Popup popup) {
		POPUPS.addFirst(popup);
		while (POPUPS.size() > 3) {
			POPUPS.removeLast();
		}
	}

	public static void tick() {
		if (runScoreTicks > 0) {
			runScoreTicks--;
		}
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gui.hud.isHidden()) {
			return;
		}
		Font font = mc.font;
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		long now = now();

		int y = (int) (height * 0.2f);
		int index = 0;
		for (Popup popup : POPUPS) {
			float age = now - popup.startTick() + delta.getGameTimeDeltaPartialTick(false);
			if (age > SHOW_TICKS) {
				continue;
			}
			float alpha = age < 4 ? age / 4f : age > SHOW_TICKS - 10 ? (SHOW_TICKS - age) / 10f : 1f;
			int a = Mth.clamp((int) (alpha * 255), 8, 255);
			float scale = index == 0 ? 1.6f + Math.max(0, 4 - age) * 0.12f : 1.0f;
			graphics.pose().pushMatrix();
			graphics.pose().translate(width / 2f, y);
			graphics.pose().scale(scale, scale);
			graphics.centeredText(font, popup.title(), 0, 0, (a << 24) | popup.titleColor());
			graphics.pose().popMatrix();
			y += (int) (10 * scale) + 2;
			if (!popup.detail().isEmpty()) {
				graphics.centeredText(font, popup.detail(), width / 2, y, (a << 24) | popup.detailColor());
				y += 12;
			}
			y += 4;
			index++;
		}

		float powderAge = now - powderTick + delta.getGameTimeDeltaPartialTick(false);
		if (powderAge < 45) {
			int a = Mth.clamp((int) ((powderAge < 3 ? powderAge / 3f : powderAge > 30 ? (45 - powderAge) / 15f : 1f) * 255), 8, 255);
			String line = powderText + "  +" + powderPoints + "  ·  +" + (powderPoints / 100) + " Ski Bucks";
			float pop = 1.15f + Math.max(0, 3 - powderAge) * 0.08f;
			graphics.pose().pushMatrix();
			graphics.pose().translate(width / 2f, height * 0.2f + 46);
			graphics.pose().scale(pop, pop);
			graphics.centeredText(font, line, 0, 0, (a << 24) | 0xBFEFFF);
			graphics.pose().popMatrix();
		}

		if (runScoreTicks > 0 && runScore > 0) {
			String text = String.format(Locale.ROOT, "Run total  %,d", runScore);
			int a = Mth.clamp(runScoreTicks * 8, 8, 255);
			graphics.centeredText(font, text, width / 2, (int) (height * 0.2f) - 14, (a << 24) | 0xFFD166);
		}

		if (mc.player.getVehicle() instanceof RigEntity rig && com.freshtracks.FreshTracksConfig.showSpeedometer) {
			double kmh = rig.visualSpeed3() * 72.0;
			String speed = com.freshtracks.FreshTracksConfig.speed(rig.visualSpeed3());
			int x = width - font.width(speed) - 8;
			int sy = height - 22;
			graphics.fill(x - 4, sy - 3, width - 4, sy + 10, 0x66000000);
			graphics.text(font, speed, x, sy, kmh > 90 ? 0xFFFF7A5C : kmh > 50 ? 0xFFFFE45C : 0xFFFFFFFF);
			int score = rig.motion.tricks.runScore;
			if (score > 0) {
				String s = String.format(Locale.ROOT, "%,d pts", score);
				int sx = width - font.width(s) - 8;
				graphics.text(font, s, sx, sy - 12, 0xFF7FE0FF);
			}
		}
	}
}
