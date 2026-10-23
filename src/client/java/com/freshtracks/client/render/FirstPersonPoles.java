package com.freshtracks.client.render;

import com.freshtracks.FreshTracks;
import com.freshtracks.rig.RiderAction;
import com.freshtracks.rig.RigEntity;
import com.freshtracks.rig.RigMotion;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Your own poles in the helmet view while you ski: a gloved hand on each grip, the shafts running down to the snow.
 * They reach and plant and drive back with each double-pole push, a pole flicks forward to start each turn, and in a
 * tuck they go up under the arms. Drawn instead of the usual hands while riding skis with poles (unless you're eating).
 */
public final class FirstPersonPoles {
	private static final Identifier TEXTURE = FreshTracks.id("textures/item/ski_pole_model.png");
	/** Pole parts' places on the texture (16 px): grip, shaft, basket, tip, strap. */
	static final float[] GRIP = {0, 0, 2, 6};
	static final float[] SHAFT = {3, 0, 4, 16};
	static final float[] BASKET = {6, 0, 11, 7};
	static final float[] TIP = {13, 0, 14, 2};
	static final float[] STRAP = {15, 0, 16, 3};

	/** A plain pair of poles to draw in a rider's hands (made on first use: items aren't ready while the game loads). */
	private static net.minecraft.world.item.ItemStack pair;

	private FirstPersonPoles() {
	}

	public static net.minecraft.world.item.ItemStack pair() {
		if (pair == null) {
			pair = new net.minecraft.world.item.ItemStack(com.freshtracks.registry.ModItems.SKI_POLES);
		}
		return pair;
	}

	/** Draws the poles if the player is skiing with them; true when the usual hands should be skipped. */
	public static boolean submit(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState playerState,
		FirstPersonHandsAndItemsRenderState state) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !(mc.player.getVehicle() instanceof RigEntity rig) || rig.kind().isBoard() || !rig.hasPoles()
			|| state.useItemRemainingTicks > 0 || playerState.avatarRenderState == null) {
			return false;
		}
		RigMotion m = rig.motion;
		int light = playerState.avatarRenderState.lightCoords;
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.XP, (state.viewXRot - state.xBob) * 0.1f);
		poseStack.rotateDegrees(Axis.YP, (state.viewYRot - state.yBob) * 0.1f);
		float t = m.actionTicks + partialTicks;
		RiderAction action = m.grounded ? m.action : RiderAction.NONE;
		float sway = Mth.sin((mc.player.tickCount + partialTicks) * 0.25f) * (float) Math.min(1, m.speed() * 2) * 0.012f;
		for (int side = -1; side <= 1; side += 2) {
			boolean right = side > 0;
			// rest: gloves at the bottom corners of the view, poles angled back so the baskets trail behind
			float x = side * 0.42f;
			float y = -0.40f + (right ? sway : -sway);
			float z = -0.70f;
			float tilt = 22f; // shaft lean, degrees: positive sends the tip back under you
			float splay = side * 6f;
			if (m.tuck) {
				// tucked: fists together under the chin, poles up under the arms pointing back
				x = side * 0.13f;
				y = -0.42f;
				z = -0.52f;
				tilt = 72f;
				splay = side * 14f;
			} else if (action == RiderAction.DOUBLE_POLE) {
				// reach, plant ahead, drive down and back out of sight, swing through
				float reach = t < 3f ? t / 3f : t < 7.5f ? 1f - (t - 3f) / 4.5f : 0f;
				float drive = t < 3f ? 0f : t < 7.5f ? (t - 3f) / 4.5f : Math.max(0f, 1f - (t - 7.5f) / 5.5f);
				y += 0.20f * reach - 0.42f * drive;
				z += -0.24f * reach + 0.30f * drive;
				tilt = Mth.lerp(reach, tilt, -18f) + 30f * drive;
			} else if (action == (right ? RiderAction.PLANT_RIGHT : RiderAction.PLANT_LEFT)) {
				// a flick of the wrist: this pole swings forward and taps the snow beside the tip
				float reach = t < 2.5f ? t / 2.5f : Math.max(0f, 1f - (t - 2.5f) / 5.5f);
				y += 0.05f * reach;
				z += -0.16f * reach;
				tilt = Mth.lerp(reach, tilt, -24f);
			} else if (action.herringbone()) {
				// poles planted wide behind to hold each step
				x = side * 0.50f;
				tilt = 34f;
				splay = side * 18f;
				boolean stepping = right == (action == RiderAction.HERRINGBONE_RIGHT);
				y += stepping ? 0.06f * Mth.sin(Math.min(1f, t / 7f) * Mth.PI) : 0f;
			} else if (action.skate()) {
				// arms swing with the stride
				boolean back = right == (action == RiderAction.SKATE_RIGHT);
				float stride = Mth.sin(Math.min(1f, t / 9f) * Mth.PI);
				y += back ? -0.14f * stride : 0.08f * stride;
				z += back ? 0.12f * stride : -0.10f * stride;
			}
			poseStack.pushPose();
			poseStack.translate(x, y, z);
			poseStack.rotateDegrees(Axis.ZP, -splay);
			poseStack.rotateDegrees(Axis.XP, tilt);
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (pose, buf) -> {
				// the glove round the grip, the grip, the strap over the wrist, then shaft, basket and tip
				box(pose, buf, light, -0.055f, -0.02f, -0.055f, 0.055f, 0.13f, 0.055f, GRIP);
				box(pose, buf, light, -0.03f, 0.13f, -0.03f, 0.03f, 0.19f, 0.03f, GRIP);
				box(pose, buf, light, -0.01f, 0.12f, 0.03f, 0.01f, 0.21f, 0.05f, STRAP);
				box(pose, buf, light, -0.012f, -1.02f, -0.012f, 0.012f, -0.02f, 0.012f, SHAFT);
				box(pose, buf, light, -0.075f, -0.98f, -0.075f, 0.075f, -0.965f, 0.075f, BASKET);
				box(pose, buf, light, -0.006f, -1.09f, -0.006f, 0.006f, -1.02f, 0.006f, TIP);
			});
			poseStack.popPose();
		}
		poseStack.popPose();
		return true;
	}

	/** A box with one texture patch (u0, v0, u1, v1 in pixels) on every face. */
	static void box(PoseStack.Pose pose, VertexConsumer buf, int light, float x0, float y0, float z0, float x1, float y1, float z1, float[] uv) {
		float u0 = uv[0] / 16f, v0 = uv[1] / 16f, u1 = uv[2] / 16f, v1 = uv[3] / 16f;
		quad(pose, buf, light, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, u0, v0, u1, v1, 0, 0, 1);
		quad(pose, buf, light, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, u0, v0, u1, v1, 0, 0, -1);
		quad(pose, buf, light, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, u0, v0, u1, v1, -1, 0, 0);
		quad(pose, buf, light, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, u0, v0, u1, v1, 1, 0, 0);
		quad(pose, buf, light, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, u0, v0, u1, v1, 0, 1, 0);
		quad(pose, buf, light, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, u0, v0, u1, v1, 0, -1, 0);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer buf, int light, float ax, float ay, float az, float bx, float by, float bz,
		float cx, float cy, float cz, float dx, float dy, float dz, float u0, float v0, float u1, float v1, float nx, float ny, float nz) {
		vertex(pose, buf, light, ax, ay, az, u0, v0, nx, ny, nz);
		vertex(pose, buf, light, bx, by, bz, u0, v1, nx, ny, nz);
		vertex(pose, buf, light, cx, cy, cz, u1, v1, nx, ny, nz);
		vertex(pose, buf, light, dx, dy, dz, u1, v0, nx, ny, nz);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buf, int light, float x, float y, float z, float u, float v, float nx, float ny, float nz) {
		buf.addVertex(pose, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
	}
}
