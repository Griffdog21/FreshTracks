package com.freshtracks.client.mixin;

import com.freshtracks.client.weather.ClientWeather;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	/** Blizzards and flat light: the world fades to white a few dozen blocks out. Goggles see twice as far. */
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void freshtracks$whiteout(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount, ClientLevel level,
		CallbackInfoReturnable<FogData> cir) {
		float amount = ClientWeather.fog;
		if (amount < 0.01f) {
			return;
		}
		FogData fog = cir.getReturnValue();
		float clearEnd = fog.environmentalEnd;
		float whiteoutEnd = ClientWeather.goggles ? 52f : 22f;
		float end = Mth.lerp(amount, clearEnd, Math.min(clearEnd, whiteoutEnd));
		fog.environmentalStart = Math.min(fog.environmentalStart, Mth.lerp(amount, fog.environmentalStart, -6f));
		fog.environmentalEnd = Math.min(fog.environmentalEnd, end);
		fog.skyEnd = Math.min(fog.skyEnd, Mth.lerp(amount, fog.skyEnd, end * 1.5f));
		fog.cloudEnd = Math.min(fog.cloudEnd, Mth.lerp(amount, fog.cloudEnd, end * 2f));
		float mix = amount * 0.85f;
		fog.color.set(
			Mth.lerp(mix, fog.color.x(), 0.83f),
			Mth.lerp(mix, fog.color.y(), 0.86f),
			Mth.lerp(mix, fog.color.z(), 0.91f),
			fog.color.w());
	}
}
