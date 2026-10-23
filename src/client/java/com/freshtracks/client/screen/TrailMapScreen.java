package com.freshtracks.client.screen;

import com.freshtracks.client.render.TrailMaps;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * The pocket trail map, unfolded: the mountain's map from the server, with a red dot where you are (and an arrow the
 * way you're facing) and your friends nearby as blue dots with their names. Opens on you; scroll to zoom, drag to look
 * around, R to come back to yourself. The game keeps running while you look.
 */
public class TrailMapScreen extends Screen {
	private static final float MAX_ZOOM = 6f;
	/** How far in it opens, and on where. */
	private float zoom = 2.2f;
	private float panU = 0.5f;
	private float panV = 0.5f;
	private boolean following = true;

	public TrailMapScreen() {
		super(Component.translatable("item.freshtracks.pocket_trail_map"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** The map's size on screen at zoom 1 (the whole of it fits). */
	private float[] fitted(TrailMaps.Map2 map) {
		float w = width * 0.96f;
		float h = w / map.aspectRatio();
		if (h > height * 0.86f) {
			h = height * 0.86f;
			w = h * map.aspectRatio();
		}
		return new float[]{w * zoom, h * zoom};
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.fill(0, 0, width, height, 0xE0101418);
		TrailMaps.Map2 map = TrailMaps.any();
		if (map == null || minecraft.player == null) {
			graphics.centeredText(font, Component.translatable("message.freshtracks.no_trail_map"), width / 2, height / 2 - 4, 0xFFFFFFFF);
			return;
		}
		float[] me = TrailMaps.project(map, minecraft.player.getX(), minecraft.player.getZ());
		if (following && me != null) {
			panU = me[0];
			panV = me[1];
		}
		clampPan(map);
		float[] size = fitted(map);
		float cx = width / 2f;
		float cy = height / 2f + 6;
		int x0 = Math.round(cx - panU * size[0]);
		int y0 = Math.round(cy - panV * size[1]);
		int x1 = Math.round(x0 + size[0]);
		int y1 = Math.round(y0 + size[1]);
		graphics.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, 0xFF2A2F36);
		graphics.blit(map.texture(), x0, y0, x1, y1, 0f, 1f, 0f, 1f);
		// friends nearby
		for (Player other : minecraft.level.players()) {
			if (other == minecraft.player) {
				continue;
			}
			float[] at = TrailMaps.project(map, other.getX(), other.getZ());
			if (at != null) {
				int px = Math.round(x0 + at[0] * size[0]);
				int py = Math.round(y0 + at[1] * size[1]);
				dot(graphics, px, py, 3, 0xFF2F7DE1);
				graphics.text(font, other.getName(), px + 5, py - 4, 0xFFFFFFFF, true);
			}
		}
		// you, and which way you're facing
		if (me != null) {
			int px = Math.round(x0 + me[0] * size[0]);
			int py = Math.round(y0 + me[1] * size[1]);
			float[] ahead = TrailMaps.project(map, minecraft.player.getX() - Mth.sin(minecraft.player.getYRot() * Mth.DEG_TO_RAD) * 40,
				minecraft.player.getZ() + Mth.cos(minecraft.player.getYRot() * Mth.DEG_TO_RAD) * 40);
			if (ahead != null) {
				float dx = (ahead[0] - me[0]) * size[0];
				float dy = (ahead[1] - me[1]) * size[1];
				float len = Mth.sqrt(dx * dx + dy * dy);
				if (len > 1e-3f) {
					for (int i = 4; i <= 11; i++) {
						int ax = Math.round(px + dx / len * i);
						int ay = Math.round(py + dy / len * i);
						graphics.fill(ax - 1, ay - 1, ax + 1, ay + 1, 0xFFFFFFFF);
					}
				}
			}
			float pulse = 0.5f + 0.5f * Mth.sin((minecraft.player.tickCount + a) * 0.25f);
			dot(graphics, px, py, 5 + Math.round(pulse * 2), 0x55E2201C);
			dot(graphics, px, py, 4, 0xFFFFFFFF);
			dot(graphics, px, py, 3, 0xFFE2201C);
			graphics.text(font, Component.literal("You are here"), px + 7, py - 4, 0xFFFFFFFF, true);
		}
		graphics.centeredText(font, title, width / 2, 6, 0xFFFFFFFF);
		graphics.centeredText(font, Component.literal(me == null ? "Off the map" : "Scroll to zoom  ·  drag to look around  ·  R: back to you"),
			width / 2, height - 12, 0xFFB8C2CC);
	}

	private static void dot(GuiGraphicsExtractor graphics, int x, int y, int r, int colour) {
		for (int dy = -r; dy <= r; dy++) {
			int half = (int) Math.floor(Math.sqrt(r * r - dy * dy + 0.5));
			graphics.fill(x - half, y + dy, x + half + 1, y + dy + 1, colour);
		}
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		TrailMaps.Map2 map = TrailMaps.any();
		if (map == null || scrollY == 0) {
			return false;
		}
		// zoom about the point under the mouse
		float[] before = fitted(map);
		float cx = width / 2f;
		float cy = height / 2f + 6;
		float mu = panU + (float) (x - cx) / before[0];
		float mv = panV + (float) (y - cy) / before[1];
		zoom = Mth.clamp(zoom * (float) Math.pow(1.2, scrollY), 1f, MAX_ZOOM);
		float[] after = fitted(map);
		panU = mu - (float) (x - cx) / after[0];
		panV = mv - (float) (y - cy) / after[1];
		following = false;
		clampPan(map);
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		TrailMaps.Map2 map = TrailMaps.any();
		if (map == null) {
			return false;
		}
		float[] size = fitted(map);
		panU -= (float) dx / size[0];
		panV -= (float) dy / size[1];
		following = false;
		clampPan(map);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_R) {
			following = true;
			return true;
		}
		return super.keyPressed(event);
	}

	/** Keeps the view on the map: zoomed in, the picture always fills the screen. */
	private void clampPan(TrailMaps.Map2 map) {
		float[] size = fitted(map);
		float halfU = Math.min(0.5f, width / 2f / size[0]);
		float halfV = Math.min(0.5f, (height / 2f - 10) / size[1]);
		panU = Mth.clamp(panU, halfU, 1f - halfU);
		panV = Mth.clamp(panV, halfV, 1f - halfV);
	}
}
