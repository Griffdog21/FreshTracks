package com.freshtracks.client.mixin;

import com.freshtracks.client.render.RigPose;
import com.freshtracks.client.render.RiderPose;
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
			state.setData(RiderPose.KEY, new RiderPose(rig.kind(), rigYaw, rig.lean(partialTicks), rig.flip(partialTicks), rig.renderFlags(), rig.renderGrab(), state.ageInTicks,
				rig.slopePitch(partialTicks) * 0.55f, rig.slopeRoll(partialTicks) * 0.75f));
			if (rig.hasPoles()) {
				// One pair of poles in either hand: draw a pole in each hand.
				net.minecraft.world.item.ItemStack poles = entity.getMainHandItem().is(com.freshtracks.registry.ModItems.SKI_POLES)
					? entity.getMainHandItem() : entity.getOffhandItem();
				var resolver = ((LivingEntityRendererAccessor) (Object) this).freshtracks$itemModelResolver();
				resolver.updateForLiving(state.rightHandItemState, poles, net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity);
				resolver.updateForLiving(state.leftHandItemState, poles, net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND, entity);
			}
		} else {
			state.setData(RiderPose.KEY, null);
		}
	}

	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("HEAD"))
	private void freshtracks$leanAndFlip(AvatarRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		RiderPose pose = state.getData(RiderPose.KEY);
		if (pose != null) {
			RigPose.apply(poseStack, pose.rigYaw(), pose.lean(), pose.flip(), pose.slopePitch(), pose.slopeRoll());
		}
	}
}
