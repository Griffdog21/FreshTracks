package com.freshtracks.item;

import com.freshtracks.registry.ModEntities;
import com.freshtracks.registry.ModSounds;
import com.freshtracks.rig.RigEntity;
import com.freshtracks.rig.RigKind;
import com.freshtracks.rig.RigStats;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/** Skis or a snowboard. Use it to click in; sneak while (nearly) stopped to step out. */
public class RigItem extends Item {
	private final RigKind kind;
	private final RigStats stats;

	public RigItem(RigKind kind, RigStats stats, Item.Properties properties) {
		super(properties);
		this.kind = kind;
		this.stats = stats;
	}

	public RigKind kind() {
		return kind;
	}

	public RigStats stats() {
		return stats;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player.isPassenger()) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide()) {
			RigEntity rig = new RigEntity(ModEntities.RIG, level);
			// Point the skis where they fit: facing a wall, they swing round instead of poking into it.
			Float yaw = com.freshtracks.rig.RigMotion.freeHeading(level, player.getX(), player.getY(), player.getZ(), player.getYRot());
			if (yaw == null) {
				player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.freshtracks.no_room"));
				return InteractionResult.FAIL;
			}
			double surfaceY = Math.max(player.getY(), com.freshtracks.rig.SurfaceSampler.ground(level, player.getX(), player.getZ(), player.getY()).height());
			rig.snapTo(player.getX(), surfaceY, player.getZ(), yaw, 0f);
			rig.setItem(stack);
			if (!level.noCollision(rig, rig.getBoundingBox())) {
				return InteractionResult.FAIL;
			}
			level.addFreshEntity(rig);
			if (!player.startRiding(rig)) {
				rig.discard();
				return InteractionResult.FAIL;
			}
			stack.shrink(1);
			if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
				com.freshtracks.advancement.ModCriteria.fire(sp, "click_in");
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.CLICK_IN, SoundSource.PLAYERS, 0.9f, 1.0f);
			level.gameEvent(player, GameEvent.ENTITY_MOUNT, player.position());
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.freshtracks.click_in").withStyle(ChatFormatting.GRAY));
		Integer wax = stack.get(com.freshtracks.registry.ModComponents.WAX);
		if (wax != null && wax > 0) {
			builder.accept(Component.translatable("tooltip.freshtracks.waxed", com.freshtracks.gear.SkiWaxItem.minutes(wax)).withStyle(ChatFormatting.AQUA));
		}
		com.freshtracks.economy.Tune tune = stack.get(com.freshtracks.registry.ModComponents.TUNE);
		RigStats s = tune == null ? stats : tune.apply(stats);
		if (tune != null) {
			builder.accept(Component.literal("Pro tune").withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		stat(builder, "speed", s.speed());
		stat(builder, "grip", s.grip());
		stat(builder, "turn", s.turn());
		stat(builder, "float", s.flotation());
		stat(builder, "pop", s.pop());
		stat(builder, "spin", s.spin());
	}

	private static void stat(Consumer<Component> builder, String name, float value) {
		int pips = Math.max(1, Math.min(10, Math.round(value * 5f)));
		String bar = "■".repeat(pips) + "□".repeat(Math.max(0, 8 - pips));
		builder.accept(Component.translatable("tooltip.freshtracks.stat." + name)
			.append(Component.literal(" " + bar).withStyle(value > 1.05f ? ChatFormatting.AQUA : value < 0.95f ? ChatFormatting.DARK_GRAY : ChatFormatting.WHITE)));
	}
}
