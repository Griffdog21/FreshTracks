package com.freshtracks.client.mixin;

import com.freshtracks.client.RideCamera;
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
		forwards.set(0, 0, -1).rotate(rotation);
		up.set(0, 1, 0).rotate(rotation);
		left.set(-1, 0, 0).rotate(rotation);
		matrixPropertiesDirty |= 3;
	}
}
