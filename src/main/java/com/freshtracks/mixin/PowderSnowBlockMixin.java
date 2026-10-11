package com.freshtracks.mixin;

import com.freshtracks.gear.GearItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.PowderSnowBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PowderSnowBlock.class)
public abstract class PowderSnowBlockMixin {
	/** Ski and snowboard boots keep you on top of powder snow, like leather boots. */
	@Inject(method = "canEntityWalkOnPowderSnow", at = @At("RETURN"), cancellable = true)
	private static void freshtracks$bootsFloat(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && entity instanceof LivingEntity living
			&& living.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof GearItem gear
			&& (gear.perk() == GearItem.Perk.SKI_BOOTS || gear.perk() == GearItem.Perk.BOARD_BOOTS)) {
			cir.setReturnValue(true);
		}
	}
}
