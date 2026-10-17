package com.freshtracks.mixin;

import com.freshtracks.economy.Catalog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records catalog sales (collections, numbered editions) as they happen. */
@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerMixin {
	@Inject(method = "notifyTrade", at = @At("TAIL"))
	private void freshtracks$catalogSale(MerchantOffer offer, CallbackInfo ci) {
		if (((AbstractVillager) (Object) this).getTradingPlayer() instanceof ServerPlayer player) {
			Catalog.sold(player, offer);
		}
	}
}
