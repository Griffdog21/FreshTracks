package com.freshtracks.client.mixin;

import com.freshtracks.client.render.RigPose;
import com.freshtracks.client.render.RiderPose;
import com.freshtracks.rig.RiderAction;
import com.freshtracks.rig.RigEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void freshtracks$extractRider(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		if (entity.getVehicle() instanceof RigEntity rig) {
			float rigYaw = rig.getYRot(partialTicks);
			float body = rigYaw + (rig.kind().isBoard() ? 90f : 0f);
			float headRot = state.bodyRot + state.yRot;
			state.bodyRot = body;
			state.yRot = Mth.clamp(Mth.wrapDegrees(headRot - body), -80f, 80f);
			state.isPassenger = false;
			state.isCrouching = false;
			boolean poles = rig.hasPoles();
			byte action = rig.renderAction();
			state.setData(RiderPose.KEY, new RiderPose(rig.kind(), rigYaw, rig.lean(partialTicks), rig.flip(partialTicks), rig.renderFlags(), rig.renderGrab(), state.ageInTicks,
				rig.slopePitch(partialTicks) * 0.55f, rig.slopeRoll(partialTicks) * 0.75f, RiderAction.action(action),
				RiderAction.ticks(action) + partialTicks, poles));
			if (poles) {
				// A skier rides with a pole in each hand, whatever the hotbar has selected: the pole layer draws them down to
				// the snow, and nothing else is held.
				state.rightHandItemState.clear();
				state.leftHandItemState.clear();
			}
		} else {
			state.setData(RiderPose.KEY, null);
		}
	}

	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("HEAD"))
	private void freshtracks$leanAndFlip(AvatarRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		RiderPose pose = state.getData(RiderPose.KEY);
		if (pose != null) {
			RigPose.pressLift(poseStack, pose.flags(), pose.grab());
			RigPose.apply(poseStack, pose.rigYaw(), pose.lean(), pose.flip(), pose.slopePitch(), pose.slopeRoll());
		}
	}
}
