package com.freshtracks.resort;

import com.freshtracks.race.TrailMarkerBlock;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
 * A trail sign: the run's difficulty symbol and its name, like the ones at the top of every run. Use it to edit the
 * name and symbol; a sign renamed in an anvil starts with that name.
 */
public class TrailSignBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<TrailMarkerBlock.Symbol> SYMBOL = TrailMarkerBlock.SYMBOL;

	/** Client hook: open the sign editor. */
	public static Consumer<BlockPos> openEditor = pos -> {
	};

	public TrailSignBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ResortSignBlock.WALL, false)
			.setValue(SYMBOL, TrailMarkerBlock.Symbol.GREEN));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, ResortSignBlock.WALL, SYMBOL);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return ResortSignBlock.placement(this.defaultBlockState(), context);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return ResortSignBlock.shape(state);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TrailSignBlockEntity(pos, state);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (stack.has(DataComponents.CUSTOM_NAME) && level.getBlockEntity(pos) instanceof TrailSignBlockEntity sign) {
			sign.setName(stack.getHoverName().getString());
		} else if (level.isClientSide() && placer instanceof Player) {
			openEditor.accept(pos);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!player.mayBuild()) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			openEditor.accept(pos);
		}
		return InteractionResult.SUCCESS;
	}
}
