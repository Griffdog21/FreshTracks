package com.freshtracks.client.mixin;

import com.freshtracks.client.RideCamera;
import com.freshtracks.FreshTracksConfig;
import com.freshtracks.client.render.RigPose;
import com.freshtracks.rig.RigEntity;
import net.minecraft.util.Mth;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Apply before Minecraft extracts projection/culling state, so shaders see the same camera as the terrain. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow @Final private Quaternionf rotation;
	@Shadow @Final private Vector3f forwards;
	@Shadow @Final private Vector3f up;
	@Shadow @Final private Vector3f left;
	@Shadow private int matrixPropertiesDirty;
	@Shadow private Vec3 position;
	@Shadow private float xRot;
	@Shadow private float yRot;
	@Shadow protected abstract void setPosition(Vec3 position);
	@Shadow public abstract boolean isPanoramicMode();

	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void freshtracks$speedFov(float partialTicks, CallbackInfoReturnable<Float> cir) {
		if (isPanoramicMode()) return;
		float original = cir.getReturnValue();
		cir.setReturnValue(Math.min(Math.max(original, 110f), original + RideCamera.fovBoost(partialTicks)));
	}

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void freshtracks$rideView(float partialTicks, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (isPanoramicMode() || !RideCamera.active(mc)) return;
		boolean helmet = mc.options.getCameraType().isFirstPerson();
		if (helmet) setPosition(position.add(0, -RideCamera.lower(partialTicks), 0));
		float bank = RideCamera.bank(partialTicks) * (helmet ? 1 : 0.4f);
		if (mc.options.getCameraType().isMirrored()) bank = -bank;
		rotation.rotateZ(-bank * (float) (Math.PI / 180.0));
		if (helmet && "full".equals(FreshTracksConfig.rideEffects) && mc.player.getVehicle() instanceof RigEntity rig) {
			// Match the rider's world-space flip axis, even while the player looks sideways. A local camera pitch
			// would flip around the wrong axis during a combined spin/flip or free look.
			float flip = rig.flip(partialTicks);
			if (flip != 0) {
				float yaw = rig.getYRot(partialTicks) * (float) (Math.PI / 180.0);
				Quaternionf trick = new Quaternionf().rotationY(-yaw)
					.rotateX(flip * (float) (Math.PI / 180.0)).rotateY(yaw);
				rotation.premul(trick);
				double pivotY = Mth.lerp(partialTicks, rig.yo, rig.getY()) + RigPose.FLIP_PIVOT;
				float headHeight = (float) (position.y - pivotY);
				Vector3f offset = new Vector3f(0, headHeight, 0).rotate(trick);
				setPosition(position.add(offset.x, offset.y - headHeight, offset.z));
			}
		}
		forwards.set(0, 0, -1).rotate(rotation);
		up.set(0, 1, 0).rotate(rotation);
		left.set(-1, 0, 0).rotate(rotation);
		xRot = (float) -Math.toDegrees(Math.asin(Mth.clamp(forwards.y, -1, 1)));
		yRot = (float) Math.toDegrees(Math.atan2(-forwards.x, forwards.z));
		matrixPropertiesDirty |= 3;
	}
}
