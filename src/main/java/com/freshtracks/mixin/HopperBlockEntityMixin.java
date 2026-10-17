package com.freshtracks.mixin;

import com.freshtracks.item.RigItem;
import com.freshtracks.registry.ModItems;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Owner-only crash gear and overflow rewards must not be siphoned into a hopper or hopper minecart. */
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
    @Inject(method="addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z", at=@At("HEAD"), cancellable=true)
    private static void freshtracks$protectDrops(Container container, ItemEntity drop, CallbackInfoReturnable<Boolean> result) {
        if (((ItemEntityAccessor) drop).freshtracks$pickupOwner() != null
                && (drop.getItem().getItem() instanceof com.freshtracks.economy.BillItem || drop.getItem().getItem() instanceof RigItem)) {
            result.setReturnValue(false);
        }
    }
}
