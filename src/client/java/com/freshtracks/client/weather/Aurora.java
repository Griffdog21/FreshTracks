package com.freshtracks.client.weather;

import com.freshtracks.weather.SnowEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;

/**
 * Northern lights: shimmering green and violet curtains across the northern sky on some clear, cold nights in snowy
 * country (every other night on bluebird or cold-snap days). Everyone on a server sees the same aurora: whether a
 * night has one comes from the day number. Drawn additively like lightning, far out but inside the fog distance.
 */
public final class Aurora {
	private static final int SEGMENTS = 72;
	private static float intensity;
	private static float forced = -1f;

	private Aurora() {
	}

	/** 0..1 how strong the aurora should be right now (before smoothing). */
	private static float target(Minecraft mc, ClientLevel level) {
		if (mc.player == null || level.isRaining() || !level.dimensionType().hasSkyLight()) {
			return 0f;
		}
		long time = level.getOverworldClockTime();
		long day = Math.floorDiv(time, 24000L);
		float t = Math.floorMod(time, 24000L);
		// dusk 13000 -> full by 14500, fades from 21500 until dawn at 23000
		float night = t < 13000 || t > 23000 ? 0f : Math.min(1f, Math.min((t - 13000f) / 1500f, (23000f - t) / 1500f));
		if (night <= 0f) {
			return 0f;
		}
		SnowEvent event = ClientWeather.event();
		boolean clearSky = event == SnowEvent.CLEAR || event == SnowEvent.BLUEBIRD || event == SnowEvent.COLD_SNAP;
		if (!clearSky) {
			return 0f;
		}
		int seed = (int) (day * 0x9E3779B1L >>> 7);
		boolean auroraNight = Math.floorMod(seed, event == SnowEvent.CLEAR ? 3 : 2) == 0;
		if (!auroraNight) {
			return 0f;
		}
		BlockPos pos = mc.player.blockPosition();
		if (!ClientWeather.coldHere(level, pos)) {
			return 0f;
		}
		return night;
	}

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		float goal = level == null ? 0f : forced >= 0f ? forced : target(mc, level);
		intensity += (goal - intensity) * 0.02f;
		if (intensity < 0.002f && goal == 0f) {
			intensity = 0f;
		}
	}

	public static void collect(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (intensity <= 0.01f || mc.level == null) {
			return;
		}
		float time = (mc.level.getGameTime() % 240000L) + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		int renderBlocks = mc.options.getEffectiveRenderDistance() * 16;
		float radius = Mth.clamp(renderBlocks * 0.62f, 60f, 150f);
		PoseStack poseStack = context.poseStack();
		Vec3 cam = context.levelState().cameraRenderState.pos;
		float baseY = (float) Mth.clamp(110.0 - cam.y, 45.0, 110.0) + 25f; // above the camera, at least 45 up
		float alpha = intensity;
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
			Matrix4fc m = pose.pose();
			curtain(buffer, m, radius, baseY, 34f, time, 0.0f, alpha, 0.0f);
			curtain(buffer, m, radius * 1.12f, baseY + 14f, 26f, time, 2.1f, alpha * 0.7f, 0.35f);
			curtain(buffer, m, radius * 0.9f, baseY - 6f, 20f, time, 4.4f, alpha * 0.5f, 0.7f);
		});
	}

	/**
	 * One curtain: an arc across the northern sky (yaw -80..80 around north), a bright green lower edge that fades
	 * downward, glowing upward into violet and fading out at the top. {@code hue} shifts it a little toward teal.
	 */
	private static void curtain(VertexConsumer buffer, Matrix4fc m, float radius, float baseY, float height, float time,
		float phase, float alpha, float hue) {
		float[] xs = new float[SEGMENTS + 1];
		float[] zs = new float[SEGMENTS + 1];
		float[] ys = new float[SEGMENTS + 1];
		float[] hs = new float[SEGMENTS + 1];
		float[] glow = new float[SEGMENTS + 1];
		for (int i = 0; i <= SEGMENTS; i++) {
			float u = i / (float) SEGMENTS;
			float angle = Mth.lerp(u, -80f, 80f) * Mth.DEG_TO_RAD;
			// slow folds in the curtain, plus a faster ripple
			float fold = Mth.sin(u * 9f + time * 0.004f + phase) * 0.08f + Mth.sin(u * 23f - time * 0.011f + phase * 2f) * 0.03f;
			float r = radius * (1f + fold);
			xs[i] = Mth.sin(angle) * r;
			zs[i] = -Mth.cos(angle) * r; // north is -z
			ys[i] = baseY + Mth.sin(u * 5f + time * 0.003f + phase) * 6f;
			hs[i] = height * (0.75f + 0.25f * Mth.sin(u * 13f + time * 0.007f + phase));
			float shimmer = 0.65f + 0.35f * Mth.sin(u * 40f + time * 0.05f + phase * 3f);
			float ends = Mth.clamp(Math.min(u, 1f - u) * 6f, 0f, 1f); // fade out at both ends of the arc
			glow[i] = alpha * shimmer * ends;
		}
		for (int i = 0; i < SEGMENTS; i++) {
			int j = i + 1;
			// bottom fringe: transparent -> bright green
			quad(buffer, m, xs, ys, zs, i, j, -4f, -4f, 0f, 0f, 0.15f + hue * 0.1f, 1.0f, 0.55f + hue * 0.3f, 0f, glow[i] * 0.9f, glow[j] * 0.9f);
			// body: green -> violet
			quadBody(buffer, m, xs, ys, zs, hs, glow, i, j, hue);
		}
	}

	private static void quad(VertexConsumer b, Matrix4fc m, float[] xs, float[] ys, float[] zs, int i, int j,
		float lowI, float lowJ, float highI, float highJ, float r, float g, float bl, float alphaLow, float alphaHighI, float alphaHighJ) {
		b.addVertex(m, xs[i], ys[i] + lowI, zs[i]).setColor(r, g, bl, alphaLow);
		b.addVertex(m, xs[j], ys[j] + lowJ, zs[j]).setColor(r, g, bl, alphaLow);
		b.addVertex(m, xs[j], ys[j] + highJ, zs[j]).setColor(r, g, bl, alphaHighJ);
		b.addVertex(m, xs[i], ys[i] + highI, zs[i]).setColor(r, g, bl, alphaHighI);
	}

	private static void quadBody(VertexConsumer b, Matrix4fc m, float[] xs, float[] ys, float[] zs, float[] hs, float[] glow, int i, int j, float hue) {
		float gr = 0.15f + hue * 0.1f;
		float gg = 1.0f;
		float gb = 0.55f + hue * 0.3f;
		b.addVertex(m, xs[i], ys[i], zs[i]).setColor(gr, gg, gb, glow[i] * 0.9f);
		b.addVertex(m, xs[j], ys[j], zs[j]).setColor(gr, gg, gb, glow[j] * 0.9f);
		b.addVertex(m, xs[j], ys[j] + hs[j], zs[j]).setColor(0.62f, 0.32f, 1.0f, 0f);
		b.addVertex(m, xs[i], ys[i] + hs[i], zs[i]).setColor(0.62f, 0.32f, 1.0f, 0f);
	}

	/** Dev: force the aurora on for screenshots (0..1, or a negative value for normal behaviour). */
	public static void force(float value) {
		forced = value;
		if (value >= 0f) {
			intensity = value;
		}
	}
}
