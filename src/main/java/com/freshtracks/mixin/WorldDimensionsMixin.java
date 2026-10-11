package com.freshtracks.mixin;

import com.freshtracks.FreshTracks;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The game calls any overworld with its own dimension type "experimental" and asks for a backup every time such a world
 * is opened. Fresh Tracks' world types (Ski Mountains, Snowmass) are only taller, not experimental, so they count as
 * stable: no scary warning on every load.
 */
@Mixin(WorldDimensions.class)
public abstract class WorldDimensionsMixin {
	@Inject(method = "isStableOverworld", at = @At("HEAD"), cancellable = true)
	private static void freshtracks$ourWorldTypesAreStable(LevelStem dimension, CallbackInfoReturnable<Boolean> cir) {
		if (dimension.type().unwrapKey().map(key -> key.identifier().getNamespace().equals(FreshTracks.MOD_ID)).orElse(false)) {
			cir.setReturnValue(true);
		}
	}
}
