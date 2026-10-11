package com.freshtracks.race;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A trail difficulty marker on a post: green circle, blue square, black diamond, double black, terrain park or
 * closed. Use it with an empty hand to change the symbol.
 */
public class TrailMarkerBlock extends HorizontalDirectionalBlock {
	public enum Symbol implements StringRepresentable {
		GREEN("green", "Green Circle (easier)"),
		BLUE("blue", "Blue Square (more difficult)"),
		BLACK("black", "Black Diamond (most difficult)"),
		DOUBLE_BLACK("double_black", "Double Black Diamond (experts only)"),
		PARK("park", "Terrain Park"),
		CLOSED("closed", "Closed"),
		EXTREME("extreme", "Extreme Terrain (experts only)"),
		LIFT("lift", "Lift");

		private final String name;
		public final String label;

		Symbol(String name, String label) {
			this.name = name;
			this.label = label;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public static final EnumProperty<Symbol> SYMBOL = EnumProperty.create("symbol", Symbol.class);
	private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(
		Shapes.or(Block.box(7, 0, 9, 9, 16, 11), Block.box(1, 4, 8, 15, 16, 9)));
	private static final Map<Direction, VoxelShape> POLES = Shapes.rotateHorizontal(Block.box(7, 0, 9, 9, 16, 11));

	public TrailMarkerBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SYMBOL, Symbol.GREEN));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, SYMBOL);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return POLES.get(state.getValue(FACING));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!player.mayBuild()) {
			return InteractionResult.PASS;
		}
		Symbol[] all = Symbol.values();
		Symbol next = all[(state.getValue(SYMBOL).ordinal() + 1) % all.length];
		if (!level.isClientSide()) {
			level.setBlock(pos, state.setValue(SYMBOL, next), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.WOODEN_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6f, 1.2f);
			player.sendOverlayMessage(Component.literal(next.label));
		}
		return InteractionResult.SUCCESS;
	}
}
