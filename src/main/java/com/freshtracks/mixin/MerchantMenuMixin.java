package com.freshtracks.mixin;

import com.freshtracks.economy.BillItem;
import com.freshtracks.economy.SkiBucks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Picking a Ski Bucks offer makes change first, so the right notes are there for the trade to count out. */
@Mixin(MerchantMenu.class)
public abstract class MerchantMenuMixin extends AbstractContainerMenu {
	@Shadow
	@Final
	private MerchantContainer tradeContainer;

	@Shadow
	public abstract MerchantOffers getOffers();

	protected MerchantMenuMixin(MenuType<?> type, int id) {
		super(type, id);
	}

	@Inject(method = "tryMoveItems", at = @At("HEAD"))
	private void freshtracks$makeChange(int index, CallbackInfo ci) {
		if (index < 0 || index >= getOffers().size() || !(this.slots.get(3).container instanceof Inventory inv)
			|| !(inv.player instanceof ServerPlayer player)) {
			return;
		}
		MerchantOffer offer = getOffers().get(index);
		ItemStack a = offer.getCostA();
		ItemStack b = offer.getCostB();
		if (!(a.getItem() instanceof BillItem) && !(b.getItem() instanceof BillItem)) {
			return;
		}
		// notes already sitting in the payment slots go back in the wallet first
		for (int slot = 0; slot < 2; slot++) {
			ItemStack s = tradeContainer.getItem(slot);
			if (s.getItem() instanceof BillItem) {
				inv.placeItemBackInInventory(tradeContainer.removeItemNoUpdate(slot), net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
		freshtracks$changed = SkiBucks.makeChange(player, a.getItem() instanceof BillItem ? a : ItemStack.EMPTY, b.getItem() instanceof BillItem ? b : ItemStack.EMPTY);
	}

	private boolean freshtracks$changed;

	/** The client guessed at moving the notes itself; after making change, send it what really happened. */
	@Inject(method = "tryMoveItems", at = @At("TAIL"))
	private void freshtracks$resync(int index, CallbackInfo ci) {
		if (freshtracks$changed) {
			freshtracks$changed = false;
			this.sendAllDataToRemote();
		}
	}
}
