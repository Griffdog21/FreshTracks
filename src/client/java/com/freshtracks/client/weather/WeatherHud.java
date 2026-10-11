package com.freshtracks.client.weather;

import com.freshtracks.lift.LiftSeatEntity;
import com.freshtracks.rig.RigEntity;
import com.freshtracks.weather.SnowConditions;
import com.freshtracks.weather.SnowEvent;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** The dawn forecast banner, the conditions line while riding, sun glare on bluebird days and the goggles view. */
public final class WeatherHud {
	private static SnowEvent banner;
	private static long bannerStart = -1000;

	private WeatherHud() {
	}

	public static void announce(SnowEvent event) {
		Minecraft mc = Minecraft.getInstance();
		banner = event;
		bannerStart = mc.level == null ? 0 : mc.level.getGameTime();
	}

	/** Under the HUD: glare and the goggles' frame. */
	public static void renderOverlay(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		int w = graphics.guiWidth();
		int h = graphics.guiHeight();
		boolean firstPerson = mc.options.getCameraType().isFirstPerson();
		float glare = glare(mc, delta.getGameTimeDeltaPartialTick(false));
		if (glare > 0.01f && !ClientWeather.goggles) {
			int a = (int) (Mth.clamp(glare, 0f, 1f) * 150);
			graphics.fill(0, 0, w, h, (a << 24) | 0xFFF6E8);
		}
		if (firstPerson && ClientWeather.goggles) {
			// a soft lens edge: darker corners and a cool tint
			int tint = (int) (18 + glare * 30);
			graphics.fill(0, 0, w, h, (tint << 24) | 0x3A5FA8);
			int edge = Math.max(8, w / 14);
			graphics.fillGradient(0, 0, w, edge, 0x66000000, 0x00000000);
			graphics.fillGradient(0, h - edge, w, h, 0x00000000, 0x66000000);
		}
	}

	/** How blinding the sun off the snow is right now (0..1). */
	private static float glare(Minecraft mc, float partialTick) {
		SnowEvent event = ClientWeather.event();
		if (!event.bluebird() || ClientWeather.exposure < 0.1f || mc.level.isRaining()) {
			return 0f;
		}
		long time = mc.level.getOverworldClockTime() % 24000L;
		if (time > 12500L) {
			return 0f;
		}
		double angle = (time + partialTick) / 24000.0 * Math.PI * 2.0;
		Vec3 sun = new Vec3(Math.cos(angle), Math.sin(angle), 0).normalize();
		Vec3 look = mc.player.getViewVector(partialTick);
		float toSun = (float) Mth.clamp((look.dot(sun) - 0.55) / 0.45, 0, 1);
		float downAtSnow = (float) Mth.clamp((-look.y - 0.35) / 0.65, 0, 1) * 0.35f;
		return (toSun * toSun * 0.9f + downAtSnow) * ClientWeather.exposure;
	}

	/** On top of the HUD: forecast banner and the riding conditions line. */
	public static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) {
			return;
		}
		Font font = mc.font;
		int w = graphics.guiWidth();
		if (banner != null) {
			float age = mc.level.getGameTime() - bannerStart + delta.getGameTimeDeltaPartialTick(false);
			if (age < 140) {
				float alpha = age < 10 ? age / 10f : age > 110 ? (140 - age) / 30f : 1f;
				int a = Mth.clamp((int) (alpha * 255), 8, 255);
				Component title = Component.translatable(banner.translationKey());
				Component sub = Component.translatable(banner.translationKey() + ".desc");
				graphics.pose().pushMatrix();
				graphics.pose().translate(w / 2f, 34f);
				graphics.pose().scale(2.2f, 2.2f);
				graphics.centeredText(font, title, 0, 0, (a << 24) | colour(banner));
				graphics.pose().popMatrix();
				graphics.centeredText(font, sub, w / 2, 58, (a << 24) | 0xE8F1FF);
			}
		}
		boolean riding = mc.player.getVehicle() instanceof RigEntity || mc.player.getVehicle() instanceof LiftSeatEntity;
		if (riding && ClientWeather.exposure > 0.3f) {
			SnowEvent event = ClientWeather.event();
			String wind = com.freshtracks.FreshTracksConfig.speed(SnowConditions.windStrength() * 70.0 / 72.0);
			String arrow = windArrow(mc);
			Component line = Component.translatable(event.translationKey())
				.append(Component.literal("   wind " + wind + " " + arrow));
			graphics.fill(4, 4, 8 + font.width(line), 16, 0x55000000);
			graphics.text(font, line, 6, 6, 0xFF000000 | colour(event));
			if (mc.player.getVehicle() instanceof LiftSeatEntity && event.lifts < 1f) {
				Component hold = Component.translatable(event.lifts <= 0f ? "message.freshtracks.lift.lightning_hold" : "message.freshtracks.lift.wind_slow");
				graphics.text(font, hold, 6, 20, 0xFFFFD166);
			}
		}
	}

	private static String windArrow(Minecraft mc) {
		float wx = SnowConditions.windX();
		float wz = SnowConditions.windZ();
		if (wx * wx + wz * wz < 0.0004f) {
			return "";
		}
		float windYaw = (float) Math.toDegrees(Math.atan2(-wx, wz));
		float rel = Mth.wrapDegrees(windYaw - mc.player.getYRot());
		String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
		int idx = Math.floorMod(Math.round(rel / 45f), 8);
		return arrows[idx];
	}

	private static int colour(SnowEvent event) {
		return switch (event) {
			case POWDER_DAY -> 0x9CE7FF;
			case BLUEBIRD -> 0x7FC8FF;
			case BLIZZARD, THUNDERSNOW -> 0xD9E6F5;
			case SNOWSTORM -> 0xC9DCF0;
			case HIGH_WIND -> 0xB8F2E6;
			case COLD_SNAP -> 0xA9C7FF;
			case FLAT_LIGHT -> 0xDADADA;
			case SPRING_SLUSH -> 0xFFE29A;
			default -> 0xFFFFFF;
		};
	}
}
