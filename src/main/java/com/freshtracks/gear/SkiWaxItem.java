package com.freshtracks.gear;

import com.freshtracks.item.RigItem;
import com.freshtracks.registry.ModComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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

/** Hold skis or a board in your other hand and use the wax: faster glide for a good while. */
public class SkiWaxItem extends Item {
	/** One bar of wax: twenty minutes of riding. Stacks up to an hour. */
	public static final int TICKS_PER_BAR = 24000;
	public static final int MAX_TICKS = 72000;

	public SkiWaxItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack wax = player.getItemInHand(hand);
		InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		ItemStack target = player.getItemInHand(other);
		if (!(target.getItem() instanceof RigItem)) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("message.freshtracks.wax.howto").withStyle(ChatFormatting.YELLOW));
			}
			return InteractionResult.FAIL;
		}
		int current = target.getOrDefault(ModComponents.WAX, 0);
		if (current >= MAX_TICKS) {
			return InteractionResult.FAIL;
		}
		if (!level.isClientSide()) {
			target.set(ModComponents.WAX, Math.min(MAX_TICKS, current + TICKS_PER_BAR));
			wax.consume(1, player);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEYCOMB_WAX_ON, SoundSource.PLAYERS, 1f, 1.2f);
			player.sendOverlayMessage(Component.translatable("message.freshtracks.wax.done", minutes(target.get(ModComponents.WAX)))
				.withStyle(ChatFormatting.AQUA));
		}
		return InteractionResult.SUCCESS;
	}

	public static int minutes(Integer ticks) {
		return ticks == null ? 0 : (int) Math.ceil(ticks / 1200.0);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.freshtracks.wax").withStyle(ChatFormatting.GRAY));
	}
}
