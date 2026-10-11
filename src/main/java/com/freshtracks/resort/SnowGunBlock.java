package com.freshtracks.resort;

import com.freshtracks.registry.ModBlockEntities;
import com.freshtracks.registry.ModParticles;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A snowmaking gun. The fan gun sits on a little carriage at the side of the run; the tower gun is a nozzle head on
 * top of a tall pole. In AUTO they run at night, like the real thing; use one to switch it ON / OFF / AUTO. A running
 * gun blows a plume of snow across the run and slowly builds up the snow where it lands.
 */
public class SnowGunBlock extends BaseEntityBlock {
	public enum Mode implements StringRepresentable {
		AUTO("auto"), ON("on"), OFF("off");

		private final String name;

		Mode(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	public static final EnumProperty<Mode> MODE = EnumProperty.create("mode", Mode.class);
	private static final Map<Direction, VoxelShape> FAN = Shapes.rotateHorizontal(
		Shapes.or(Block.box(3, 0, 2, 13, 4, 14), Block.box(7, 4, 7, 9, 10, 9), Block.box(3, 10, 2, 13, 16, 14)));
	private static final Map<Direction, VoxelShape> HEAD = Shapes.rotateHorizontal(
		Shapes.or(Block.box(7, 0, 7, 9, 6, 9), Block.box(6, 5, 2, 10, 9, 11)));

	/** True for the tower gun's nozzle head, false for the fan gun. */
	private final boolean tower;

	public SnowGunBlock(boolean tower, BlockBehaviour.Properties properties) {
		super(properties);
		this.tower = tower;
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MODE, Mode.AUTO));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, MODE);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// blow away from whoever placed it (aim it across the run)
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return (tower ? HEAD : FAN).get(state.getValue(FACING));
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		Mode next = switch (state.getValue(MODE)) {
			case AUTO -> Mode.ON;
			case ON -> Mode.OFF;
			case OFF -> Mode.AUTO;
		};
		if (!level.isClientSide()) {
			level.setBlock(pos, state.setValue(MODE, next), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, next == Mode.OFF ? 0.6f : 1.0f);
			player.sendOverlayMessage(Component.translatable("message.freshtracks.snow_gun." + next.getSerializedName()));
		}
		return InteractionResult.SUCCESS;
	}

	/** Whether the gun is making snow right now. */
	public static boolean running(Level level, BlockState state) {
		return switch (state.getValue(MODE)) {
			case ON -> true;
			case OFF -> false;
			case AUTO -> {
				long t = level.getOverworldClockTime() % 24000L;
				yield t > 12800 && t < 23300;
			}
		};
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return state.getValue(MODE) != Mode.OFF;
	}

	/** Builds up the snow where the plume lands: one more layer on a random spot ahead. */
	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!running(level, state)) {
			return;
		}
		Direction f = state.getValue(FACING);
		Direction side = f.getClockWise();
		int ahead = (tower ? 6 : 4) + random.nextInt(tower ? 14 : 11);
		int lateral = random.nextInt(7) - 3;
		int x = pos.getX() + f.getStepX() * ahead + side.getStepX() * lateral;
		int z = pos.getZ() + f.getStepZ() * ahead + side.getStepZ() * lateral;
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
		BlockPos top = new BlockPos(x, y - 1, z);
		BlockState here = level.getBlockState(top);
		if (here.getBlock() instanceof SnowLayerBlock) {
			int layers = here.getValue(SnowLayerBlock.LAYERS);
			if (layers < 8) {
				level.setBlock(top, here.setValue(SnowLayerBlock.LAYERS, layers + 1), Block.UPDATE_CLIENTS);
			}
		} else if (here.isFaceSturdy(level, top, Direction.UP) && level.getBlockState(top.above()).isAir()) {
			level.setBlock(top.above(), Blocks.SNOW.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SnowGunBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (!level.isClientSide() || type != ModBlockEntities.SNOW_GUN) {
			return null;
		}
		return (lvl, pos, st, be) -> plume(lvl, pos, st);
	}

	/** The plume: a fan of snow thrown out and up, drifting down over the run. Only drawn when someone's near. */
	private void plume(Level level, BlockPos pos, BlockState state) {
		if (!running(level, state) || level.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 72, e -> true) == null) {
			return;
		}
		RandomSource r = level.getRandom();
		Direction f = state.getValue(FACING);
		double fx = f.getStepX();
		double fz = f.getStepZ();
		double ox = pos.getX() + 0.5 + fx * (tower ? 0.5 : 0.45);
		double oy = pos.getY() + (tower ? 0.45 : 0.82);
		double oz = pos.getZ() + 0.5 + fz * (tower ? 0.5 : 0.45);
		int count = tower ? 6 : 12;
		for (int i = 0; i < count; i++) {
			double speed = (tower ? 0.7 : 0.85) + r.nextDouble() * 0.45;
			double spread = tower ? 0.1 : 0.2;
			double vx = fx * speed + (-fz) * (r.nextDouble() - 0.5) * spread * 2;
			double vz = fz * speed + fx * (r.nextDouble() - 0.5) * spread * 2;
			double vy = (tower ? 0.12 : 0.3) + r.nextDouble() * 0.18;
			level.addParticle(ModParticles.SNOW_SPRAY, ox, oy, oz, vx, vy, vz);
		}
		// the misty cloud that hangs over a running gun, drifting out over the run
		for (int i = 0; i < (tower ? 1 : 3); i++) {
			double d = 2 + r.nextDouble() * (tower ? 10 : 14);
			level.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, ox + fx * d + (r.nextDouble() - 0.5) * 2,
				oy + 0.5 + r.nextDouble() * 2.5, oz + fz * d + (r.nextDouble() - 0.5) * 2, fx * 0.05, 0.01, fz * 0.05);
		}
	}
}
