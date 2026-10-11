package com.freshtracks.client.mixin;

import com.freshtracks.rig.RigEntity;
import com.freshtracks.rig.RiderInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@Shadow public ClientInput input;

	/** Hands the rider's keys to the rig, the same way vanilla feeds a boat. */
	@Inject(method = "rideTick", at = @At("TAIL"))
	private void freshtracks$feedRig(CallbackInfo ci) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		if (self.getControlledVehicle() instanceof RigEntity rig) {
			Input keys = this.input.keyPresses;
			boolean grab = Minecraft.getInstance().options.keyAttack.isDown();
			rig.setRiderInput(new RiderInput(keys.forward(), keys.backward(), keys.left(), keys.right(), keys.jump(), keys.shift(), grab));
		}
	}
}
