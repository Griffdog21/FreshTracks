package com.freshtracks.mixin;

import com.freshtracks.world.ModBiomes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shares the cold mountain slots of the overworld with Fresh Tracks biomes. The split goes by temperature and humidity,
 * which change slowly across the world, so each mountain range gets big connected areas instead of speckles. Every
 * vanilla biome still generates. The world preset stays "minecraft:overworld", so removing the mod only changes new
 * chunks.
 *
 * <pre>
 * slopes (cold):   humidity 0 snowy slopes | 1 POWDER BOWLS | 2 FROSTED GLADES | 3 grove | 4 LARCH BASIN
 * peaks:           frozen: ALPENGLOW (jagged spots) / GLACIER (smooth spots); cold+dry: same; otherwise vanilla
 * high plateaus:   frozen & mid humidity ASPEN HOLLOW, frozen & wet LARCH BASIN, cold & dry meadows ASPEN HOLLOW
 * </pre>
 */
@Mixin(OverworldBiomeBuilder.class)
public abstract class OverworldBiomeBuilderMixin {
	@Inject(method = "pickSlopeBiome", at = @At("HEAD"), cancellable = true)
	private void freshtracks$slopes(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness, CallbackInfoReturnable<ResourceKey<Biome>> cir) {
		if (temperatureIndex >= 3) {
			return;
		}
		switch (humidityIndex) {
			case 1 -> cir.setReturnValue(ModBiomes.POWDER_BOWLS);
			case 2 -> cir.setReturnValue(ModBiomes.FROSTED_GLADES);
			case 4 -> cir.setReturnValue(ModBiomes.LARCH_BASIN);
			default -> {
			}
		}
	}

	@Inject(method = "pickPeakBiome", at = @At("HEAD"), cancellable = true)
	private void freshtracks$peaks(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness, CallbackInfoReturnable<ResourceKey<Biome>> cir) {
		if (temperatureIndex == 0 || (temperatureIndex == 1 && humidityIndex <= 1)) {
			cir.setReturnValue(weirdness.max() < 0L ? ModBiomes.ALPENGLOW_PEAKS : ModBiomes.GLACIER);
		}
	}

	@Inject(method = "pickPlateauBiome", at = @At("HEAD"), cancellable = true)
	private void freshtracks$plateaus(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness, CallbackInfoReturnable<ResourceKey<Biome>> cir) {
		if (temperatureIndex == 0 && humidityIndex == 2) {
			cir.setReturnValue(ModBiomes.ASPEN_HOLLOW);
		} else if (temperatureIndex == 0 && humidityIndex >= 3) {
			cir.setReturnValue(ModBiomes.LARCH_BASIN);
		} else if (temperatureIndex == 1 && humidityIndex == 0 && weirdness.max() < 0L) {
			cir.setReturnValue(ModBiomes.ASPEN_HOLLOW);
		}
	}
}
