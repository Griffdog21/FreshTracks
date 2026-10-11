package com.freshtracks.client.mixin;

import com.freshtracks.rig.RigEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Shadow public @Nullable LocalPlayer player;

	/** In the air on skis, attack is the grab button: don't swing at things. */
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void freshtracks$grabNotAttack(CallbackInfoReturnable<Boolean> cir) {
		if (player != null && player.getVehicle() instanceof RigEntity rig && !rig.motion.grounded) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void freshtracks$noMiningMidAir(boolean down, CallbackInfo ci) {
		if (player != null && player.getVehicle() instanceof RigEntity rig && !rig.motion.grounded) {
			ci.cancel();
		}
	}
}
