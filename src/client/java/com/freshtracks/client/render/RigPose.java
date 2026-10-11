package com.freshtracks.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/**
 * The shared whole-body transform for a rig and its rider: lean into the carve (roll about the direction of
 * travel, pivoting at the feet) and flips (pitch about the rider's hips). The rig renderer and the rider's
 * renderer both apply it, so skis stay on the boots through every trick.
 */
public final class RigPose {
	/** Height of the rotation centre for flips, blocks above the snow. */
	public static final float FLIP_PIVOT = 0.95f;
	/** Rotation directions that make "snow rises ahead" lift the tips (checked against screenshots). */
	static final float PITCH_SIGN = -1f;
	static final float ROLL_SIGN = -1f;

	private RigPose() {
	}

	/**
	 * Applies the slope tilt (pitch along the skis, roll across them), lean and flip in world orientation. Leaves the
	 * pose stack yaw-aligned with the world again. Everything pivots at the feet, so boots stay in the bindings.
	 */
	public static void apply(PoseStack poseStack, float yaw, float lean, float flip, float slopePitch, float slopeRoll) {
		if (lean == 0f && flip == 0f && slopePitch == 0f && slopeRoll == 0f) {
			return;
		}
		poseStack.rotateDegrees(Axis.YP, -yaw);
		if (slopePitch != 0f) {
			poseStack.rotateDegrees(Axis.XP, PITCH_SIGN * slopePitch);
		}
		if (slopeRoll != 0f) {
			poseStack.rotateDegrees(Axis.ZP, ROLL_SIGN * slopeRoll);
		}
		if (lean != 0f) {
			poseStack.rotateDegrees(Axis.ZP, lean);
		}
		if (flip != 0f) {
			poseStack.translate(0f, FLIP_PIVOT, 0f);
			poseStack.rotateDegrees(Axis.XP, flip);
			poseStack.translate(0f, -FLIP_PIVOT, 0f);
		}
		poseStack.rotateDegrees(Axis.YP, yaw);
	}
}
