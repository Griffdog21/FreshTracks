package com.freshtracks.client.hud;

import com.freshtracks.net.RaceTimerPayload;
import com.freshtracks.race.RaceTiming;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/** The race clock at the top of the screen: runs while you're on a timed course, then shows your time. */
public final class RaceHud {
	private static final int SHOW_RESULT_TICKS = 120;
	private static boolean running;
	private static long startNanos;
	private static int resultCentis = -1;
	private static boolean resultBest;
	private static int resultTicks;

	private RaceHud() {
	}

	public static void handle(RaceTimerPayload payload) {
		switch (payload.state()) {
			case RaceTimerPayload.START -> {
				running = true;
				startNanos = System.nanoTime();
				resultTicks = 0;
			}
			case RaceTimerPayload.FINISH -> {
				running = false;
				resultCentis = payload.centis();
				resultBest = payload.best();
				resultTicks = SHOW_RESULT_TICKS;
			}
			default -> {
				running = false;
				resultTicks = 0;
			}
		}
	}

	public static void tick() {
		if (resultTicks > 0) {
			resultTicks--;
		}
		if (Minecraft.getInstance().level == null) {
			running = false;
			resultTicks = 0;
		}
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gui.hud.isHidden() || (!running && resultTicks <= 0)) {
			return;
		}
		Font font = mc.font;
		int width = graphics.guiWidth();
		String text;
		int color;
		if (running) {
			int centis = (int) ((System.nanoTime() - startNanos) / 10_000_000L);
			text = RaceTiming.format(centis);
			color = 0xFFFFFFFF;
		} else {
			text = RaceTiming.format(resultCentis) + (resultBest ? "  PB!" : "");
			int a = Mth.clamp(resultTicks * 10, 8, 255);
			color = (a << 24) | (resultBest ? 0x7CFF8A : 0xFFD166);
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(width / 2f, 22);
		graphics.pose().scale(2.0f, 2.0f);
		graphics.centeredText(font, text, 0, 0, color);
		graphics.pose().popMatrix();
	}
}
