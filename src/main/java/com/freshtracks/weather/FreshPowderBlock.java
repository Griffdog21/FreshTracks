package com.freshtracks.weather;

import com.freshtracks.rig.RideableSurface;
import com.freshtracks.rig.Surface;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fresh powder: snow that fell during a storm and nobody has touched yet. Rides deep and floaty. Riding through
 * it packs it down (your tracks), and when the storm's long gone it settles into ordinary snow.
 */
public class FreshPowderBlock extends SnowLayerBlock implements RideableSurface {
	public FreshPowderBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	/** The visible top: how deep you sink in depends on your speed, which RigMotion works out. */
	@Override
	public double rideHeight(BlockState state, BlockGetter level, BlockPos pos) {
		return state.getValue(LAYERS) / 8.0;
	}

	@Override
	public Surface surface(BlockState state) {
		return state.getValue(LAYERS) >= 5 ? Surface.DEEP_POWDER : Surface.POWDER;
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		SnowEvent today = SnowWeather.event(level);
		if (today.snowing) {
			return;
		}
		// Powder stashes (the bowls, the glades) keep their powder until someone rides it or spring comes.
		if (today != SnowEvent.SPRING_SLUSH && level.getBiome(pos).is(com.freshtracks.world.ModBiomes.POWDER_STASH)) {
			return;
		}
		// How quickly fresh snow settles: slowly on a powder day (it's the whole point), fast in spring sun.
		int odds = switch (today) {
			case POWDER_DAY, COLD_SNAP -> 40;
			case SPRING_SLUSH -> 2;
			default -> 6;
		};
		if (random.nextInt(odds) == 0) {
			settle(level, pos, state, today == SnowEvent.SPRING_SLUSH);
		} else {
			super.randomTick(state, level, pos, random);
		}
	}

	/** Turns this powder into ordinary packed snow (optionally melting a layer). */
	public static void settle(ServerLevel level, BlockPos pos, BlockState state, boolean melt) {
		int layers = state.getValue(LAYERS) - (melt ? 1 : 0);
		if (layers <= 0) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		} else {
			level.setBlock(pos, Blocks.SNOW.defaultBlockState().setValue(LAYERS, layers), Block.UPDATE_ALL);
		}
	}

	/** Skis through powder leave a packed track one layer lower. */
	public static void track(ServerLevel level, BlockPos pos, BlockState state) {
		int layers = state.getValue(LAYERS);
		// a visible groove: two layers deep in deep powder
		int left = layers >= 4 ? layers - 2 : Math.max(1, layers - 1);
		BlockState packed = Blocks.SNOW.defaultBlockState().setValue(LAYERS, left);
		level.setBlock(pos, packed, Block.UPDATE_CLIENTS);
	}
}
