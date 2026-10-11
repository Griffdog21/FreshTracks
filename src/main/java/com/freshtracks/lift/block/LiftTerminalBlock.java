package com.freshtracks.lift.block;

import com.freshtracks.lift.LiftLine;
import com.freshtracks.lift.LiftManager;
import com.freshtracks.lift.LiftPermissions;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A lift terminal: the concrete pad under a bullwheel. Place one at the bottom and one at the top, then join
 * them with a Lift Cable. The bullwheel, posts and roof are drawn by the block entity renderer.
 *
 * <p>Use: the lift panel (name, type, speed, spacing, open/closed). Sneak-use: open or close the lift. Name tag:
 * rename. Clock: change speed. Minecart: change the lift type. Changes need {@link LiftPermissions#canEdit}.
 */
public class LiftTerminalBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 10, 16);
	private static final double[] SPEEDS = {0.1, 0.15, 0.25, 0.3, 0.4};

	/** Client hook: open the lift panel for the lift at this terminal; false if the client doesn't know that lift. */
	public static Predicate<BlockPos> openPanel = pos -> false;

	public LiftTerminalBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new LiftTerminalBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server)) {
			if (!player.isSecondaryUseActive()) {
				openPanel.test(pos);
			}
			return InteractionResult.SUCCESS;
		}
		LiftLine line = LiftManager.findByTerminal(server, pos);
		if (line == null) {
			player.sendOverlayMessage(Component.translatable("message.freshtracks.lift.unlinked").withStyle(ChatFormatting.YELLOW));
			return InteractionResult.SUCCESS;
		}
		if (player.isSecondaryUseActive()) {
			if (!(player instanceof ServerPlayer sp) || !LiftPermissions.canEdit(sp)) {
				player.sendOverlayMessage(status(line));
				return InteractionResult.SUCCESS;
			}
			line.running = !line.running;
			LiftManager.changed(server, line);
			player.sendOverlayMessage(Component.translatable(line.running ? "message.freshtracks.lift.started" : "message.freshtracks.lift.stopped", line.name)
				.withStyle(line.running ? ChatFormatting.GREEN : ChatFormatting.RED));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (stack.getItem() instanceof com.freshtracks.lift.LiftCableItem) {
			return InteractionResult.PASS; // let the cable do the linking
		}
		boolean tool = stack.is(Items.NAME_TAG) || stack.is(Items.CLOCK) || stack.is(Items.MINECART);
		if (!(level instanceof ServerLevel server)) {
			return tool ? InteractionResult.SUCCESS : InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		LiftLine line = LiftManager.findByTerminal(server, pos);
		if (line == null || !tool) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!(player instanceof ServerPlayer sp) || !LiftPermissions.canEdit(sp)) {
			player.sendOverlayMessage(Component.translatable("message.freshtracks.lift.no_permission").withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}
		if (stack.is(Items.NAME_TAG) && stack.getCustomName() != null) {
			line.name = stack.getCustomName().getString();
			LiftManager.changed(server, line);
			player.sendOverlayMessage(Component.translatable("message.freshtracks.lift.renamed", line.name).withStyle(ChatFormatting.AQUA));
		} else if (stack.is(Items.CLOCK)) {
			int index = 0;
			for (int i = 0; i < SPEEDS.length; i++) {
				if (Math.abs(SPEEDS[i] - line.speed) < 0.01) {
					index = (i + 1) % SPEEDS.length;
				}
			}
			line.speed = SPEEDS[index];
			LiftManager.changed(server, line);
			player.sendOverlayMessage(status(line));
		} else if (stack.is(Items.MINECART)) {
			LiftManager.restyle(server, line, line.style.next());
			player.sendOverlayMessage(status(line));
		}
		return InteractionResult.SUCCESS;
	}

	public static Component status(LiftLine line) {
		String kmh = String.valueOf(Math.round(line.speed * 72));
		return Component.literal(line.name).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
			.append(Component.literal("  ").append(line.style.displayName()).append("  " + kmh + " km/h  ").withStyle(ChatFormatting.WHITE))
			.append(Component.translatable(line.running ? "message.freshtracks.lift.running" : "message.freshtracks.lift.closed")
				.withStyle(line.running ? ChatFormatting.GREEN : ChatFormatting.RED));
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		LiftLine line = LiftManager.findByTerminal(level, pos);
		if (line != null) {
			LiftManager.remove(level, line.id);
		}
	}
}
