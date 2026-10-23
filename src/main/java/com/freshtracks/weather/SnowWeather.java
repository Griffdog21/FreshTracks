package com.freshtracks.weather;

import com.freshtracks.FreshTracks;
import com.freshtracks.gear.GearItem;
import com.freshtracks.lift.LiftSeatEntity;
import com.freshtracks.lift.LiftWeather;
import com.freshtracks.net.WeatherPayload;
import com.freshtracks.registry.ModBlocks;
import com.freshtracks.registry.ModEffects;
import com.freshtracks.rig.RigEntity;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The mountain weather. Every dawn the overworld rolls a {@link SnowEvent}; the event sets vanilla rain/thunder,
 * blows the wind, piles fresh powder around players, chills the under-dressed, and slows or closes lifts.
 */
public final class SnowWeather {
	private static final Map<SnowEvent, Integer> WEIGHTS = new EnumMap<>(SnowEvent.class);
	private static float gust;
	private static float gustTarget;
	private static int syncTimer;
	private static final RandomSource RANDOM = RandomSource.create();

	static {
		WEIGHTS.put(SnowEvent.CLEAR, 26);
		WEIGHTS.put(SnowEvent.BLUEBIRD, 14);
		WEIGHTS.put(SnowEvent.SNOWSTORM, 15);
		WEIGHTS.put(SnowEvent.BLIZZARD, 7);
		WEIGHTS.put(SnowEvent.THUNDERSNOW, 4);
		WEIGHTS.put(SnowEvent.HIGH_WIND, 9);
		WEIGHTS.put(SnowEvent.COLD_SNAP, 7);
		WEIGHTS.put(SnowEvent.FLAT_LIGHT, 7);
		WEIGHTS.put(SnowEvent.SPRING_SLUSH, 4);
	}

	private SnowWeather() {
	}

	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.dimension() == Level.OVERWORLD) {
				tick(level);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> server.execute(() -> sync(listener.getPlayer())));
		LiftWeather.install(SnowWeather::liftFactor);
	}

	static SnowWeatherData data(ServerLevel level) {
		return level.getServer().overworld().getDataStorage().computeIfAbsent(SnowWeatherData.TYPE);
	}

	public static SnowEvent event(ServerLevel level) {
		return data(level).event;
	}

	// ----------------------------------------------------------------------------------------- daily roll

	private static void tick(ServerLevel level) {
		SnowWeatherData data = data(level);
		long day = Math.floorDiv(level.getOverworldClockTime(), 24000L);
		if (!com.freshtracks.FreshTracksConfig.weatherEvents) {
			// Events switched off: keep the mountain on "clear" and leave vanilla weather alone.
			if (data.event != SnowEvent.CLEAR) {
				data.event = SnowEvent.CLEAR;
				data.setDirty();
			}
		} else if (data.auto && day != data.day) {
			roll(level, data, day);
		}
		updateWind(level, data);
		avalancheControl(level, data);
		if (data.dumpTicks > 0) {
			data.dumpTicks--;
			data.setDirty();
		}
		int accumulation = data.event.accumulation + (data.dumpTicks > 0 ? 6 : 0);
		int maxLayers = data.dumpTicks > 0 ? 4 : data.event.maxLayers;
		for (ServerPlayer player : level.players()) {
			if (accumulation > 0 && com.freshtracks.FreshTracksConfig.powderAccumulation) {
				accumulate(level, player, accumulation, maxLayers);
			}
			if (com.freshtracks.FreshTracksConfig.coldExposure && level.getGameTime() % 20 == player.getId() % 20) {
				chill(level, player, data.event);
			}
		}
		if (++syncTimer >= 20) {
			syncTimer = 0;
			for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
				sync(player);
			}
		}
	}

	private static long nextBoom;
	private static long boomDay = -1;

	/**
	 * Avalanche control: on a powder morning Ski Patrol bombs the steep slopes up high before the upper lifts open, and
	 * the whole mountain hears it, a thud every half minute or two rolling round the valley from first light to about nine.
	 */
	private static void avalancheControl(ServerLevel level, SnowWeatherData data) {
		if (data.event != SnowEvent.POWDER_DAY || level.players().isEmpty()) {
			return;
		}
		long clock = level.getOverworldClockTime();
		if (Math.floorMod(clock, 24000L) > 3000) {
			return;
		}
		long now = level.getGameTime();
		if (now < nextBoom) {
			return;
		}
		nextBoom = now + 600 + RANDOM.nextInt(1800);
		long day = Math.floorDiv(clock, 24000L);
		boolean first = boomDay != day;
		boomDay = day;
		var sound = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(com.freshtracks.registry.ModSounds.AVALANCHE_CONTROL);
		for (ServerPlayer player : level.players()) {
			// heard everywhere at once, from far off: played right at each player, quiet and low
			player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(sound, net.minecraft.sounds.SoundSource.WEATHER,
				player.getX(), player.getY() + 8, player.getZ(), 0.9f, 0.85f + RANDOM.nextFloat() * 0.25f, RANDOM.nextLong()));
			if (first) {
				player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.freshtracks.avalanche_control")
					.withStyle(net.minecraft.ChatFormatting.GRAY), true);
			}
		}
	}

	private static void roll(ServerLevel level, SnowWeatherData data, long day) {
		SnowEvent before = data.event;
		SnowEvent next;
		boolean afterStorm = before == SnowEvent.SNOWSTORM || before == SnowEvent.BLIZZARD || before == SnowEvent.THUNDERSNOW;
		if (afterStorm && RANDOM.nextFloat() < 0.7f) {
			next = SnowEvent.POWDER_DAY;
		} else {
			int total = WEIGHTS.values().stream().mapToInt(Integer::intValue).sum();
			int pick = RANDOM.nextInt(total);
			next = SnowEvent.CLEAR;
			for (Map.Entry<SnowEvent, Integer> e : WEIGHTS.entrySet()) {
				pick -= e.getValue();
				if (pick < 0) {
					next = e.getKey();
					break;
				}
			}
		}
		set(level, next);
		data.yesterday = before;
		data.day = day;
		data.setDirty();
	}

	/** Changes today's weather right now (also used by the command). */
	public static void set(ServerLevel level, SnowEvent event) {
		SnowWeatherData data = data(level);
		data.event = event;
		data.windDir = RANDOM.nextFloat() * 360f;
		data.dumpTicks = event == SnowEvent.POWDER_DAY ? 2400 : 0;
		data.day = Math.floorDiv(level.getOverworldClockTime(), 24000L);
		data.setDirty();
		MinecraftServer server = level.getServer();
		if (event.snowing) {
			server.setWeatherParameters(0, 24000, true, event.thunder);
		} else {
			server.setWeatherParameters(24000, 0, false, false);
		}
		FreshTracks.LOG.info("Mountain weather today: {}", event.getSerializedName());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			sync(player);
			ServerPlayNetworking.send(player, new WeatherPayload(event.ordinal(), 0f, 0f, true));
		}
	}

	public static void setAuto(ServerLevel level, boolean auto) {
		SnowWeatherData data = data(level);
		data.auto = auto;
		data.setDirty();
	}

	// ----------------------------------------------------------------------------------------- wind

	private static void updateWind(ServerLevel level, SnowWeatherData data) {
		SnowEvent e = data.event;
		if (level.getGameTime() % 40 == 0 || gustTarget == 0) {
			float base = Mth.lerp(RANDOM.nextFloat(), e.windMin, e.windMax);
			gustTarget = e.gusty && RANDOM.nextFloat() < 0.35f ? Math.min(1f, base + 0.25f) : base;
			data.windDir += (RANDOM.nextFloat() - 0.5f) * 12f;
		}
		gust += (gustTarget - gust) * 0.05f;
		float rad = data.windDir * Mth.DEG_TO_RAD;
		float k = (float) com.freshtracks.FreshTracksConfig.windStrength;
		SnowConditions.set(e, -Mth.sin(rad) * gust * k, Mth.cos(rad) * gust * k);
	}

	public static float gust() {
		return gust;
	}

	/** Lifts slow down in blizzards, stop in the big gusts, and close for lightning. */
	private static double liftFactor(ServerLevel level) {
		SnowEvent e = event(level);
		if (e.lifts <= 0f) {
			return 0;
		}
		if ((e == SnowEvent.HIGH_WIND || e == SnowEvent.BLIZZARD) && gust > 0.88f) {
			return 0; // wind hold
		}
		return e.lifts;
	}

	// ----------------------------------------------------------------------------------------- snow

	/** Drops fresh powder on random open-sky spots around a player in snowy country. */
	private static void accumulate(ServerLevel level, ServerPlayer player, int count, int maxLayers) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < count; i++) {
			int x = Mth.floor(player.getX()) + RANDOM.nextInt(97) - 48;
			int z = Mth.floor(player.getZ()) + RANDOM.nextInt(97) - 48;
			if (!level.isLoaded(pos.set(x, player.getBlockY(), z))) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			pos.set(x, y, z);
			Biome biome = level.getBiome(pos).value();
			if (!biome.coldEnoughToSnow(pos, level.getSeaLevel())) {
				continue;
			}
			BlockState here = level.getBlockState(pos);
			BlockState below = level.getBlockState(pos.below());
			if (below.getBlock() instanceof SnowLayerBlock && below.getValue(SnowLayerBlock.LAYERS) < 8) {
				// heightmap sits above a snow layer that blocks motion: grow that one
				pos.move(0, -1, 0);
				here = below;
			}
			if (here.isAir()) {
				BlockState snow = ModBlocks.FRESH_POWDER.defaultBlockState();
				if (snow.canSurvive(level, pos) && !below.is(BlockTags.LEAVES)) {
					level.setBlock(pos, snow, Block.UPDATE_ALL);
				}
			} else if (here.getBlock() instanceof SnowLayerBlock) {
				int layers = here.getValue(SnowLayerBlock.LAYERS);
				if (layers < maxLayers) {
					level.setBlock(pos, ModBlocks.FRESH_POWDER.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers + 1), Block.UPDATE_ALL);
				} else if (here.is(Blocks.SNOW)) {
					// old snow under new: freshen it up
					level.setBlock(pos, ModBlocks.FRESH_POWDER.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers), Block.UPDATE_ALL);
				}
			}
		}
	}

	// ----------------------------------------------------------------------------------------- cold

	/** Under-dressed outside on a cold day? You start to freeze (the vanilla powder-snow freeze). */
	private static void chill(ServerLevel level, ServerPlayer player, SnowEvent event) {
		if (event.cold <= 0 || player.isCreative() || player.isSpectator() || !player.canFreeze()) {
			return;
		}
		BlockPos pos = player.blockPosition();
		if (!level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel()) || !level.canSeeSky(pos.above())) {
			return;
		}
		if (player.hasEffect(ModEffects.WARMTH) || nearHeat(level, pos)) {
			return;
		}
		int warmth = GearItem.warmthOf(player);
		// Riding a lift is cold work: you're not moving.
		int need = event.cold + (player.getVehicle() instanceof LiftSeatEntity ? 1 : 0) - (player.getVehicle() instanceof RigEntity ? 1 : 0);
		int deficit = need - warmth;
		if (deficit <= 0) {
			return;
		}
		// Vanilla thaws 2 per tick; outpace that.
		int frozen = player.getTicksFrozen() + 40 + deficit * 12;
		player.setTicksFrozen(Math.min(frozen, player.getTicksRequiredToFreeze() + 60));
		if (player.getTicksFrozen() > player.getTicksRequiredToFreeze() / 2 && RANDOM.nextInt(6) == 0) {
			player.sendOverlayMessage(Component.translatable("message.freshtracks.cold", need).withStyle(ChatFormatting.AQUA));
		}
	}

	private static boolean nearHeat(ServerLevel level, BlockPos pos) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, -2, -4), pos.offset(4, 2, 4))) {
			BlockState state = level.getBlockState(p);
			if (state.is(BlockTags.CAMPFIRES) || state.is(BlockTags.FIRE) || state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK)) {
				return true;
			}
		}
		return false;
	}

	// ----------------------------------------------------------------------------------------- sync

	public static void sync(ServerPlayer player) {
		SnowWeatherData data = data(player.level());
		ServerPlayNetworking.send(player, new WeatherPayload(data.event.ordinal(), SnowConditions.windX(), SnowConditions.windZ(), false));
	}
}
