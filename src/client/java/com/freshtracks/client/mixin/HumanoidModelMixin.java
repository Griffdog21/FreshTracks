package com.freshtracks.client.mixin;

import com.freshtracks.client.render.RiderPose;
import com.freshtracks.rig.Grab;
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

/** Skier and snowboarder stances, tucks, grabs. Applied after vanilla posing, for the body and armor models alike. */
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
		if (grab != Grab.NONE && air) {
			bend = 1.0f;
		}

		if (pose.kind() == RigKind.SNOWBOARD) {
			boardStance(bend, air, grab, bob);
		} else {
			skiStance(bend, tuck, air, grab, bob);
		}
	}

	private void skiStance(float bend, boolean tuck, boolean air, Grab grab, float bob) {
		// Crouch like vanilla sneaking, scaled by the bend: hips back, chest forward.
		float lower = 2.0f + 2.5f * bend;
		body.xRot = 0.25f + 0.55f * bend + bob;
		body.y += lower * 0.75f;
		head.y += lower * 0.8f;
		head.xRot -= 0.25f * bend;
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
			// Hands forward and out, poles planted behind.
			rightArm.xRot = -0.55f - bob * 2f;
			leftArm.xRot = -0.55f + bob * 2f;
			rightArm.yRot = 0.1f;
			leftArm.yRot = -0.1f;
			rightArm.zRot = 0.35f;
			leftArm.zRot = -0.35f;
		}
		if (air && grab != Grab.NONE) {
			reachDown(grab == Grab.LEFT || grab == Grab.BACK, grab == Grab.RIGHT || grab == Grab.PLAIN || grab == Grab.FORWARD);
		}
	}

	private void boardStance(float bend, boolean air, Grab grab, float bob) {
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
