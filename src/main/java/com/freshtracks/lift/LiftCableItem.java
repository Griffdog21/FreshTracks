package com.freshtracks.lift;

import com.freshtracks.lift.block.LiftTerminalBlock;
import com.freshtracks.lift.block.LiftTowerHeadBlock;
import com.freshtracks.registry.ModComponents;
import com.freshtracks.registry.ModSounds;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A spool of haul rope. Click one lift terminal, optionally click tower heads in order, then click the other
 * terminal: the lift is strung, towers are built where needed, and chairs start running.
 */
public class LiftCableItem extends Item {
	public record Link(BlockPos start, List<BlockPos> towers) {
		public static final Codec<Link> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("start").forGetter(Link::start),
			BlockPos.CODEC.listOf().optionalFieldOf("towers", List.of()).forGetter(Link::towers)
		).apply(i, Link::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Link> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, Link::start,
			BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(256)), Link::towers,
			Link::new);
	}

	public LiftCableItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState state = level.getBlockState(pos);
		Player player = context.getPlayer();
		ItemStack stack = context.getItemInHand();
		boolean terminal = state.getBlock() instanceof LiftTerminalBlock;
		boolean head = state.getBlock() instanceof LiftTowerHeadBlock;
		if (!terminal && !head) {
			return InteractionResult.PASS;
		}
		if (!(level instanceof ServerLevel server) || player == null) {
			return InteractionResult.SUCCESS;
		}
		Link link = stack.get(ModComponents.LIFT_LINK);
		if (terminal) {
			if (LiftManager.findByTerminal(server, pos) != null) {
				player.sendOverlayMessage(Component.translatable("message.freshtracks.cable.already_linked").withStyle(ChatFormatting.RED));
				return InteractionResult.FAIL;
			}
			if (link == null) {
				stack.set(ModComponents.LIFT_LINK, new Link(pos.immutable(), List.of()));
				player.sendOverlayMessage(Component.translatable("message.freshtracks.cable.started").withStyle(ChatFormatting.AQUA));
				level.playSound(null, pos, ModSounds.CHAIR_CLACK, SoundSource.BLOCKS, 0.6f, 1.4f);
				return InteractionResult.SUCCESS;
			}
			if (link.start().equals(pos)) {
				stack.remove(ModComponents.LIFT_LINK);
				player.sendOverlayMessage(Component.translatable("message.freshtracks.cable.cancelled"));
				return InteractionResult.SUCCESS;
			}
			if (!(level.getBlockState(link.start()).getBlock() instanceof LiftTerminalBlock)) {
				stack.remove(ModComponents.LIFT_LINK);
				player.sendOverlayMessage(Component.translatable("message.freshtracks.cable.start_gone").withStyle(ChatFormatting.RED));
				return InteractionResult.FAIL;
			}
			double distance = Math.sqrt(link.start().distSqr(pos));
			if (distance < 12) {
				player.sendOverlayMessage(Component.translatable("message.freshtracks.cable.too_short").withStyle(ChatFormatting.RED));
				return InteractionResult.FAIL;
			}
			if (distance > 1200) {
				player.sendOverlayMessage(Component.translatable("message.freshtracks.cable.too_long").withStyle(ChatFormatting.RED));
				return InteractionResult.FAIL;
			}
			BlockPos bottom = link.start().getY() <= pos.getY() ? link.start() : pos.immutable();
			BlockPos top = bottom.equals(link.start()) ? pos.immutable() : link.start();
			List<BlockPos> towers = new ArrayList<>(link.towers());
			if (!bottom.equals(link.start())) {
				java.util.Collections.reverse(towers);
			}
			boolean autoTowers = towers.isEmpty();
			if (autoTowers) {
				towers = TowerBuilder.plan(server, bottom, top, ChairStyle.QUAD);
				TowerBuilder.build(server, towers);
			}
			int count = LiftManager.lines(server).size() + 1;
			LiftLine line = new LiftLine(UUID.randomUUID(), "Lift " + count, bottom, top, towers, ChairStyle.QUAD,
				ChairStyle.QUAD.defaultSpeed, ChairStyle.QUAD.defaultSpacing, true);
			if (autoTowers) {
				TowerBuilder.clearCorridor(server, line);
			}
			LiftManager.add(server, line);
			LiftSetup.placeMarks(server, line);
			stack.remove(ModComponents.LIFT_LINK);
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
			if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
				com.freshtracks.advancement.ModCriteria.fire(sp, "build_lift");
			}
			player.sendSystemMessage(Component.translatable("message.freshtracks.cable.done", line.name, towers.size(), Math.round(line.upLength()))
				.withStyle(ChatFormatting.GREEN));
			level.playSound(null, pos, ModSounds.LIFT_BELL, SoundSource.BLOCKS, 1.0f, 1.0f);
			return InteractionResult.SUCCESS;
		}
		// Tower head
		if (link == null) {
			player.sendOverlayMessage(Component.translatable("message.freshtracks.cable.need_terminal").withStyle(ChatFormatting.YELLOW));
			return InteractionResult.FAIL;
		}
		List<BlockPos> towers = new ArrayList<>(link.towers());
		if (!towers.contains(pos)) {
			towers.add(pos.immutable());
		}
		stack.set(ModComponents.LIFT_LINK, new Link(link.start(), towers));
		player.sendOverlayMessage(Component.translatable("message.freshtracks.cable.tower_added", towers.size()).withStyle(ChatFormatting.AQUA));
		level.playSound(null, pos, ModSounds.CHAIR_CLACK, SoundSource.BLOCKS, 0.5f, 1.6f);
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.isSecondaryUseActive() && stack.has(ModComponents.LIFT_LINK)) {
			stack.remove(ModComponents.LIFT_LINK);
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("message.freshtracks.cable.cancelled"));
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return stack.has(ModComponents.LIFT_LINK);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		Link link = stack.get(ModComponents.LIFT_LINK);
		if (link == null) {
			builder.accept(Component.translatable("tooltip.freshtracks.cable.howto").withStyle(ChatFormatting.GRAY));
		} else {
			builder.accept(Component.translatable("tooltip.freshtracks.cable.linking", link.start().toShortString(), link.towers().size())
				.withStyle(ChatFormatting.AQUA));
		}
	}
}
