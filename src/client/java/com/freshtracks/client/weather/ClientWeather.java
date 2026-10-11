package com.freshtracks.client.weather;

import com.freshtracks.gear.GearItem;
import com.freshtracks.net.WeatherPayload;
import com.freshtracks.registry.ModParticles;
import com.freshtracks.registry.ModSounds;
import com.freshtracks.weather.SnowConditions;
import com.freshtracks.weather.SnowEvent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** What the weather looks and sounds like on this client: blowing snow, whiteouts, glare, wind, the forecast. */
public final class ClientWeather {
	private static final RandomSource RANDOM = RandomSource.create();
	/** 0..1, eased: how much whiteout fog is around the camera right now. */
	public static float fog;
	/** 0..1: outdoors in snow country (effects fade in and out with this). */
	public static float exposure;
	public static boolean goggles;
	private static AbstractTickableSoundInstance windLoop;
	private static AbstractTickableSoundInstance blizzardLoop;

	private ClientWeather() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(WeatherPayload.TYPE, (payload, context) -> {
			SnowEvent event = SnowEvent.byId(payload.event());
			SnowConditions.set(event, payload.windX(), payload.windZ());
			if (payload.announce()) {
				WeatherHud.announce(event);
			}
		});
	}

	public static SnowEvent event() {
		return SnowConditions.event();
	}

	public static boolean coldHere(ClientLevel level, BlockPos pos) {
		return level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel());
	}

	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null) {
			fog = 0;
			exposure = 0;
			return;
		}
		SnowEvent event = event();
		BlockPos eye = BlockPos.containing(player.getEyePosition());
		boolean cold = coldHere(level, eye);
		boolean outside = level.canSeeSky(eye);
		exposure += ((cold && outside ? 1f : 0f) - exposure) * 0.05f;
		goggles = GearItem.hasPerk(player, GearItem.Perk.GOGGLES);
		float fogTarget = cold ? event.fog : 0f;
		if (!outside) {
			fogTarget *= 0.35f;
		}
		fog += (fogTarget - fog) * 0.02f;
		ensureSounds(mc);
		if (mc.isPaused() || exposure < 0.05f) {
			return;
		}
		spawnWindSnow(level, player, event);
		spawnSparkle(level, player, event);
	}

	private static void spawnWindSnow(ClientLevel level, LocalPlayer player, SnowEvent event) {
		float wx = SnowConditions.windX();
		float wz = SnowConditions.windZ();
		float strength = SnowConditions.windStrength();
		int count = (int) ((strength - 0.15f) * 10f * exposure) + (event.snowing ? (int) (event.fog * 8f * exposure) : 0);
		for (int i = 0; i < count; i++) {
			double x = player.getX() - wx * 14 + (RANDOM.nextDouble() - 0.5) * 26;
			double y = player.getY() - 1.5 + RANDOM.nextDouble() * 7;
			double z = player.getZ() - wz * 14 + (RANDOM.nextDouble() - 0.5) * 26;
			double speed = 0.35 + strength * 0.7;
			double vx = wx / Math.max(strength, 0.01f) * speed * (0.7 + RANDOM.nextDouble() * 0.6);
			double vz = wz / Math.max(strength, 0.01f) * speed * (0.7 + RANDOM.nextDouble() * 0.6);
			double vy = (event.snowing ? -0.08 : 0.0) + (RANDOM.nextDouble() - 0.5) * 0.04;
			if (strength < 0.15f) {
				vx = (RANDOM.nextDouble() - 0.5) * 0.05;
				vz = (RANDOM.nextDouble() - 0.5) * 0.05;
			}
			level.addParticle(ModParticles.WIND_SNOW, x, y, z, vx, vy, vz);
		}
	}

	private static void spawnSparkle(ClientLevel level, LocalPlayer player, SnowEvent event) {
		if (!event.bluebird() || event.snowing || level.isRaining()) {
			return;
		}
		long time = level.getOverworldClockTime() % 24000L;
		boolean daylight = time < 12500L;
		float chance = (event == SnowEvent.COLD_SNAP ? 0.9f : 0.5f) * exposure * (daylight ? 1f : 0.3f);
		while (chance > 0) {
			if (RANDOM.nextFloat() < chance) {
				Vec3 look = player.getLookAngle();
				double x = player.getX() + look.x * 4 + (RANDOM.nextDouble() - 0.5) * 10;
				double y = player.getEyeY() + (RANDOM.nextDouble() - 0.4) * 4;
				double z = player.getZ() + look.z * 4 + (RANDOM.nextDouble() - 0.5) * 10;
				level.addParticle(ModParticles.SPARKLE, x, y, z, 0, 0, 0);
			}
			chance -= 1f;
		}
	}

	// ----------------------------------------------------------------------------------------- sound

	private static void ensureSounds(Minecraft mc) {
		if (windLoop == null || windLoop.isStopped()) {
			windLoop = new Loop(ModSounds.WIND, () -> Math.max(0f, SnowConditions.windStrength() - 0.2f) * 0.9f * exposure, () -> 0.8f + SnowConditions.windStrength() * 0.3f);
			mc.getSoundManager().play(windLoop);
		}
		if (blizzardLoop == null || blizzardLoop.isStopped()) {
			blizzardLoop = new Loop(ModSounds.BLIZZARD, () -> (event().snowing ? fog : 0f) * 0.8f * exposure, () -> 1f);
			mc.getSoundManager().play(blizzardLoop);
		}
	}

	/** An ambient loop that follows the camera with a volume function. */
	static final class Loop extends AbstractTickableSoundInstance {
		private final java.util.function.Supplier<Float> volumeFn;
		private final java.util.function.Supplier<Float> pitchFn;

		Loop(SoundEvent event, java.util.function.Supplier<Float> volume, java.util.function.Supplier<Float> pitch) {
			super(event, SoundSource.WEATHER, RandomSource.create());
			this.volumeFn = volume;
			this.pitchFn = pitch;
			this.looping = true;
			this.delay = 0;
			this.volume = 0.0001f;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}

		@Override
		public void tick() {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null) {
				this.stop();
				return;
			}
			float target = Mth.clamp(volumeFn.get(), 0f, 1f);
			this.volume = Math.max(0.0001f, this.volume + (target - this.volume) * 0.05f);
			this.pitch = pitchFn.get();
		}
	}
}
