package com.freshtracks.economy;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A Ski Bucks note: the 1, the 100 and the 1,000. Use a big one to break it into change; sneak-use to tidy every
 * note you carry into the fewest bills (shops make change the same way when you walk up to them).
 */
public class BillItem extends Item {
	private final int value;

	public BillItem(Item.Properties properties, int value) {
		super(properties);
		this.value = value;
	}

	public int value() {
		return value;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		if (player.isShiftKeyDown()) {
			SkiBucks.tidy(sp);
			sp.sendOverlayMessage(Component.literal("Wallet: " + SkiBucks.format(SkiBucks.wallet(sp)) + " Ski Bucks").withStyle(ChatFormatting.GOLD));
		} else if (value > 1) {
			ItemStack stack = player.getItemInHand(hand);
			stack.shrink(1);
			int each = value / 10;
			SkiBucks.give(sp, value, each);
			sp.sendOverlayMessage(Component.literal("Broke a " + SkiBucks.format(value) + " into change").withStyle(ChatFormatting.GOLD));
		} else {
			sp.sendOverlayMessage(Component.literal("Wallet: " + SkiBucks.format(SkiBucks.wallet(sp)) + " Ski Bucks  ·  /skibucks for your pass").withStyle(ChatFormatting.GOLD));
			return InteractionResult.SUCCESS;
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.7f, 1.3f);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.literal("Worth " + SkiBucks.format(value) + (value == 1 ? " Ski Buck" : " Ski Bucks")).withStyle(ChatFormatting.GOLD));
		if (value > 1) {
			builder.accept(Component.literal("Use to break into change").withStyle(ChatFormatting.GRAY));
		}
		builder.accept(Component.literal("Sneak + use to tidy your wallet").withStyle(ChatFormatting.DARK_GRAY));
	}
}
