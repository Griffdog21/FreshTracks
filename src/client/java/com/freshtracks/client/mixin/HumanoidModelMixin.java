package com.freshtracks.client.mixin;

import com.freshtracks.client.render.RiderPose;
import com.freshtracks.rig.Grab;
import com.freshtracks.rig.RiderAction;
import com.freshtracks.rig.RigEntity;
import com.freshtracks.rig.RigKind;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skier and snowboarder stances, tucks, grabs, and what the arms and legs do between turns: double-pole pushes,
 * skating strides, pole plants, herringbone steps. Applied after vanilla posing, for the body and armor models alike.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
	@Shadow @Final public ModelPart head;
	@Shadow @Final public ModelPart body;
	@Shadow @Final public ModelPart rightArm;
	@Shadow @Final public ModelPart leftArm;
	@Shadow @Final public ModelPart rightLeg;
	@Shadow @Final public ModelPart leftLeg;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void freshtracks$ridePose(HumanoidRenderState state, CallbackInfo ci) {
		RiderPose pose = state.getData(RiderPose.KEY);
		if (pose == null) {
			return;
		}
		boolean tuck = pose.has(RigEntity.FLAG_TUCK);
		boolean crouch = pose.has(RigEntity.FLAG_CROUCH);
		boolean air = pose.airborne();
		Grab grab = pose.grab();
		float bob = Mth.sin(pose.age() * 0.35f) * 0.03f;
		// How deep the knees bend: 0 standing tall, 1 deep tuck.
		float bend = tuck ? 1.0f : crouch ? 0.8f : air ? 0.55f : 0.35f;
		if (pose.has(RigEntity.FLAG_BUTTER) || pose.has(RigEntity.FLAG_GRIND)) bend = 0.65f;
		if (grab != Grab.NONE && air) {
			bend = 1.0f;
		}

		if (pose.kind() == RigKind.SNOWBOARD) {
			boardStance(bend, air, grab, bob, pose);
		} else {
			skiStance(bend, tuck, air, grab, bob, pose);
		}
	}

	private void skiStance(float bend, boolean tuck, boolean air, Grab grab, float bob, RiderPose pose) {
		RiderAction action = air ? RiderAction.NONE : pose.action();
		float t = pose.actionTime();
		// a double-pole push folds the body over the poles; a herringbone climb leans into the hill
		float push = action == RiderAction.DOUBLE_POLE ? pushCurve(t) : 0f;
		if (action.herringbone()) bend = Math.max(bend, 0.55f);
		bend = Math.min(1f, bend + push * 0.35f);
		// Crouch like vanilla sneaking, scaled by the bend: hips back, chest forward.
		float lower = 2.0f + 2.5f * bend;
		body.xRot = 0.25f + 0.55f * bend + bob + push * 0.35f;
		body.y += lower * 0.75f;
		head.y += lower * 0.8f;
		head.xRot -= 0.25f * bend + push * 0.3f;
		rightArm.y += lower * 0.75f;
		leftArm.y += lower * 0.75f;
		rightLeg.z += 1.5f + 2.5f * bend;
		leftLeg.z += 1.5f + 2.5f * bend;
		rightLeg.xRot = -0.08f * bend;
		leftLeg.xRot = -0.08f * bend;
		rightLeg.yRot = 0f;
		leftLeg.yRot = 0f;
		rightLeg.zRot = 0.02f;
		leftLeg.zRot = -0.02f;
		if (tuck) {
			// Egg: hands together in front of the face.
			rightArm.xRot = -1.35f;
			leftArm.xRot = -1.35f;
			rightArm.yRot = -0.35f;
			leftArm.yRot = 0.35f;
			rightArm.zRot = 0f;
			leftArm.zRot = 0f;
		} else {
			// Hands forward and out, poles held ready.
			rightArm.xRot = -0.55f - bob * 2f;
			leftArm.xRot = -0.55f + bob * 2f;
			rightArm.yRot = 0.1f;
			leftArm.yRot = -0.1f;
			rightArm.zRot = 0.35f;
			leftArm.zRot = -0.35f;
		}
		switch (action) {
			case DOUBLE_POLE -> {
				// reach both poles forward, plant, drive them back past the hips, then swing through again
				float arm = poleSwing(t);
				rightArm.xRot = arm;
				leftArm.xRot = arm;
				rightArm.zRot = 0.18f;
				leftArm.zRot = -0.18f;
			}
			case PLANT_LEFT, PLANT_RIGHT -> {
				// a flick of the wrist: the pole reaches forward and taps the snow beside the tip
				float reach = t < 2.5f ? t / 2.5f : Math.max(0f, 1f - (t - 2.5f) / 5.5f);
				ModelPart arm = action == RiderAction.PLANT_RIGHT ? rightArm : leftArm;
				arm.xRot = Mth.lerp(reach, arm.xRot, -1.05f);
				arm.zRot *= 1f - reach * 0.5f;
			}
			case SKATE_LEFT, SKATE_RIGHT -> {
				// push off one ski, out to the side and back; the arms swing opposite, like walking
				boolean left = action == RiderAction.SKATE_LEFT;
				float stride = Mth.sin(Math.min(1f, t / 9f) * Mth.PI);
				ModelPart pushLeg = left ? leftLeg : rightLeg;
				pushLeg.zRot += (left ? -0.38f : 0.38f) * stride;
				pushLeg.xRot += 0.25f * stride;
				(left ? rightArm : leftArm).xRot = -0.55f - 0.55f * stride;
				(left ? leftArm : rightArm).xRot = -0.55f + 0.65f * stride;
			}
			case HERRINGBONE_LEFT, HERRINGBONE_RIGHT -> {
				// tips apart, knees in, a step at a time; the poles planted behind to stop a slide back
				boolean left = action == RiderAction.HERRINGBONE_LEFT;
				float step = Mth.sin(Math.min(1f, t / 7f) * Mth.PI);
				rightLeg.yRot = -0.42f;
				leftLeg.yRot = 0.42f;
				rightLeg.zRot = 0.12f;
				leftLeg.zRot = -0.12f;
				ModelPart stepLeg = left ? leftLeg : rightLeg;
				stepLeg.y -= 1.6f * step;
				stepLeg.xRot -= 0.35f * step;
				rightArm.xRot = 0.15f - (left ? 0f : 0.35f * step);
				leftArm.xRot = 0.15f - (left ? 0.35f * step : 0f);
				rightArm.zRot = 0.5f;
				leftArm.zRot = -0.5f;
			}
			default -> {
			}
		}
		if (air && grab != Grab.NONE) {
			reachDown(grab == Grab.LEFT || grab == Grab.BACK, grab == Grab.RIGHT || grab == Grab.PLAIN || grab == Grab.FORWARD);
		}
	}

	/** The arms through one double-pole stroke (13 ticks): reach, plant, drive back, swing through. */
	private static float poleSwing(float t) {
		if (t < 3f) return Mth.lerp(t / 3f, -0.55f, -1.4f);
		if (t < 7.5f) return Mth.lerp((t - 3f) / 4.5f, -1.4f, 0.6f);
		if (t < 9f) return 0.6f;
		return Mth.lerp(Math.min(1f, (t - 9f) / 4f), 0.6f, -0.55f);
	}

	/** How hard the body folds into the stroke: up as the poles bite, gone as they swing through. */
	private static float pushCurve(float t) {
		if (t < 3f) return 0f;
		if (t < 7.5f) return (t - 3f) / 4.5f;
		return Math.max(0f, 1f - (t - 7.5f) / 4f);
	}

	private void boardStance(float bend, boolean air, Grab grab, float bob, RiderPose pose) {
		// Sideways on the board: feet apart along the board, knees bent, arms out for balance, head toward the nose.
		float lower = 1.5f + 2.5f * bend;
		body.xRot = 0.15f + 0.3f * bend + bob;
		body.y += lower * 0.75f;
		head.y += lower * 0.8f;
		rightArm.y += lower * 0.75f;
		leftArm.y += lower * 0.75f;
		rightLeg.z += 1.0f + 1.5f * bend;
		leftLeg.z += 1.0f + 1.5f * bend;
		rightLeg.xRot = -0.1f * bend;
		leftLeg.xRot = -0.1f * bend;
		rightLeg.zRot = 0.28f;
		leftLeg.zRot = -0.28f;
		rightLeg.yRot = 0.15f;
		leftLeg.yRot = -0.15f;
		rightArm.xRot = -0.2f;
		leftArm.xRot = -0.35f;
		rightArm.zRot = 0.9f + bob;
		leftArm.zRot = -0.75f - bob;
		if (!air && pose.action().skate()) {
			// one foot out of the binding, pushing off the snow beside the board
			float stride = Mth.sin(Math.min(1f, pose.actionTime() / 9f) * Mth.PI);
			rightLeg.xRot -= 0.45f * stride;
			rightLeg.z -= 2.0f * stride;
		}
		if (air && grab != Grab.NONE) {
			reachDown(grab == Grab.LEFT || grab == Grab.BACK, grab != Grab.LEFT);
		}
	}

	private void reachDown(boolean leftHand, boolean rightHand) {
		if (rightHand) {
			rightArm.xRot = -0.35f;
			rightArm.zRot = -0.25f;
			rightArm.yRot = 0.0f;
		}
		if (leftHand) {
			leftArm.xRot = -0.35f;
			leftArm.zRot = 0.25f;
			leftArm.yRot = 0.0f;
		}
	}
}
