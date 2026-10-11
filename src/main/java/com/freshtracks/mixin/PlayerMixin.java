package com.freshtracks.mixin;

import com.freshtracks.lift.LiftSeatEntity;
import com.freshtracks.rig.RigEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
	/** On skis, sneak is the brake. The rig itself lets you step out once you've stopped. */
	@Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
	private void freshtracks$keepBindingsOn(CallbackInfoReturnable<Boolean> cir) {
		if (((Player) (Object) this).getVehicle() instanceof RigEntity) {
			cir.setReturnValue(false);
		}
		// On a chairlift a tap of sneak would drop you off the chair; you have to mean it (hold it).
		if (((Player) (Object) this).getVehicle() instanceof LiftSeatEntity seat && !seat.bailOutHeld()) {
			cir.setReturnValue(false);
		}
	}
}
