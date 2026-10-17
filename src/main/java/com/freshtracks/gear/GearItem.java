package com.freshtracks.gear;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A piece of ski clothing: how warm it keeps you, and what else it's good for. */
public class GearItem extends Item {
	public enum Perk {
		NONE, GOGGLES, SKI_BOOTS, BOARD_BOOTS, RACE
	}

	private final int warmth;
	private final Perk perk;

	public GearItem(Item.Properties properties, int warmth, Perk perk) {
		super(properties);
		this.warmth = warmth;
		this.perk = perk;
	}

	public int warmth() {
		return warmth;
	}

	public Perk perk() {
		return perk;
	}

	/** Total warmth of what an entity is wearing (leather counts a little too). */
	public static int warmthOf(LivingEntity entity) {
		int total = 0;
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack stack = entity.getItemBySlot(slot);
			if (stack.getItem() instanceof GearItem gear) {
				total += gear.warmth + extraWarmth(stack);
			} else if (stack.is(net.minecraft.tags.ItemTags.FREEZE_IMMUNE_WEARABLES)) {
				total += 1;
			}
		}
		return total;
	}

	private static int extraWarmth(ItemStack stack) {
		com.freshtracks.economy.Tune tune = stack.get(com.freshtracks.registry.ModComponents.TUNE);
		return tune == null ? 0 : tune.warmth();
	}

	public static boolean hasPerk(LivingEntity entity, Perk perk) {
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.FEET}) {
			if (entity.getItemBySlot(slot).getItem() instanceof GearItem gear && gear.perk == perk) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		int total = warmth + extraWarmth(stack);
		if (total > 0) {
			builder.accept(Component.translatable("tooltip.freshtracks.warmth")
				.append(Component.literal(" " + "❄".repeat(total)).withStyle(extraWarmth(stack) > 0 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA)));
		}
		if (perk != Perk.NONE) {
			builder.accept(Component.translatable("tooltip.freshtracks.perk." + perk.name().toLowerCase()).withStyle(ChatFormatting.GRAY));
		}
	}
}
