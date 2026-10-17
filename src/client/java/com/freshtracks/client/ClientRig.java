package com.freshtracks.client;

import com.freshtracks.client.hud.TrickHud;
import com.freshtracks.client.sound.RigLoopSound;
import com.freshtracks.net.RiderStatePayload;
import com.freshtracks.net.TrickPayload;
import com.freshtracks.net.WipeoutPayload;
import com.freshtracks.registry.ModSounds;
import com.freshtracks.rig.RigClientHooks;
import com.freshtracks.rig.RigEntity;
import com.freshtracks.rig.RigMotion;
import com.freshtracks.rig.Surface;
import com.freshtracks.rig.SurfaceSampler;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.ToDoubleFunction;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/** Client side of riding: effects for every rig, plus networking and feedback for the rig you are driving. */
public final class ClientRig implements RigClientHooks.Hooks {
	public static final ClientRig INSTANCE = new ClientRig();

	private final Map<RigEntity, RigLoopSound[]> loops = new WeakHashMap<>();
	private final RandomSource random = RandomSource.create();
	/** Screen shake left, 0..1, fed by landings and bonks. */
	public float shake;

	private ClientRig() {
	}

	// --------------------------------------------------------------------------------- local rider

	@Override
	public void afterLocalStep(RigEntity rig) {
		RigMotion m = rig.motion;
		RigMotion.Events e = m.events;
		ClientPlayNetworking.send(new RiderStatePayload(rig.getId(), m.lean, m.flipVisual, (byte) rig.localFlags(), (byte) m.tricks.grab.ordinal()));
		ClientLevel level = (ClientLevel) rig.level();
		if (e.popped) {
			level.playLocalSound(rig.getX(), rig.getY(), rig.getZ(), ModSounds.POP, SoundSource.PLAYERS, 0.7f, 0.9f + random.nextFloat() * 0.2f, false);
		}
		if (e.perfectPop) {
			TrickHud.showMessage("Perfect pop!", 0xFFD84A);
		}
		if (e.stroke == 2) {
			// the pole tips biting into the snow, a puff either side
			level.playLocalSound(rig.getX(), rig.getY(), rig.getZ(), net.minecraft.sounds.SoundEvents.SNOW_STEP, SoundSource.PLAYERS, 0.8f, 1.25f + random.nextFloat() * 0.15f, false);
			burst(level, rig, 5, 0.12);
		} else if (e.stroke == 1) {
			level.playLocalSound(rig.getX(), rig.getY(), rig.getZ(), net.minecraft.sounds.SoundEvents.SNOW_STEP, SoundSource.PLAYERS, 0.55f, 0.9f + random.nextFloat() * 0.15f, false);
			burst(level, rig, 3, 0.1);
		}
		if (e.boost) {
			level.playLocalSound(rig.getX(), rig.getY(), rig.getZ(), ModSounds.TRICK, SoundSource.PLAYERS, 0.35f, 1.5f, false);
		}
		if (e.landed && !e.crashed) {
			boolean powder = m.surface.isPowder();
			float vol = Mth.clamp(e.landingImpact * 1.4f, 0.25f, 1.0f);
			level.playLocalSound(rig.getX(), rig.getY(), rig.getZ(), powder ? ModSounds.LAND_POWDER : ModSounds.LAND, SoundSource.PLAYERS, vol, 0.9f + random.nextFloat() * 0.2f, false);
			shake = Math.max(shake, Mth.clamp((e.landingImpact - 0.3f) * 1.2f, 0f, 0.8f));
			burst(level, rig, powder ? 30 : 14, 0.25 + e.landingImpact * 0.3);
		}
		if (e.trick != null && e.trick.airTicks() == 0 && !e.groundTrick) {
			// a linked powder turn: a softer call-out, a whoosh and a big plume of snow
			TrickHud.showPowderTurn(e.trick);
			ClientPlayNetworking.send(new TrickPayload(e.trick.name(), e.trick.points(), 0));
			level.playLocalSound(rig.getX(), rig.getY(), rig.getZ(), ModSounds.LAND_POWDER, SoundSource.PLAYERS, 0.35f, 1.3f + random.nextFloat() * 0.2f, false);
			powderPlume(level, rig);
		} else if (e.trick != null) {
			TrickHud.showTrick(e.trick);
			ClientPlayNetworking.send(new TrickPayload(e.trick.name(), e.trick.points(), e.trick.airTicks()));
			if (e.trick.points() >= 300) {
				level.playLocalSound(rig.getX(), rig.getY(), rig.getZ(), ModSounds.TRICK, SoundSource.PLAYERS, 0.5f, 1.0f + Math.min(e.trick.combo(), 6) * 0.08f, false);
			}
		}
		if (e.wallBonk) {
			level.playLocalSound(rig.getX(), rig.getY(), rig.getZ(), ModSounds.BONK, SoundSource.PLAYERS, 0.6f, 1.0f, false);
			shake = Math.max(shake, 0.3f);
		}
		if (e.crashed) {
			TrickHud.showWipeout(e.crashReason);
			shake = 1.0f;
			burst(level, rig, 40, 0.5);
			ClientPlayNetworking.send(new WipeoutPayload(e.crashSeverity, e.crashReason == null ? "" : e.crashReason));
		} else if (e.sank) {
			TrickHud.showMessage("Sunk!", 0x66CCFF);
			ClientPlayNetworking.send(new WipeoutPayload(0.2f, "sank"));
		}
	}

	@Override
	public boolean cameraFollowsAir() {
		Minecraft mc = Minecraft.getInstance();
		return RideCamera.active(mc) && mc.options.getCameraType().isFirstPerson()
			&& "full".equals(com.freshtracks.FreshTracksConfig.rideEffects);
	}

	@Override
	public float cameraFollow() {
		return 1f;
	}

	@Override
	public void onRiderRemoved(RigEntity rig, Entity passenger) {
		Minecraft mc = Minecraft.getInstance();
		if (passenger == mc.player) {
			TrickHud.endRun(rig.motion.tricks.runScore);
		}
	}

	/** Riding preferences and interpolated camera feedback. */
	public void tickCamera(Minecraft mc) {
		RideCamera.tick(mc);
		shake *= 0.82f;
		if (shake < 0.01f) shake = 0;
	}

	// --------------------------------------------------------------------------------- every rig

	@Override
	public void effects(RigEntity rig) {
		ClientLevel level = (ClientLevel) rig.level();
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && rig.distanceToSqr(mc.player) > 48 * 48) {
			return;
		}
		ensureLoops(mc, rig);
		boolean air = rig.hasFlagOrLocal(RigEntity.FLAG_AIR);
		double speed = rig.visualSpeed();
		if (air || speed < 0.12) {
			return;
		}
		Surface surface = surfaceUnder(rig);
		if (!surface.snowy) {
			if ((surface == Surface.ROCK || surface == Surface.HARD) && random.nextFloat() < speed) {
				level.addParticle(ParticleTypes.CRIT, rig.getX(), rig.getY() + 0.05, rig.getZ(), 0, 0.1, 0);
			}
			return;
		}
		float lean = rig.lean(1f);
		boolean skidding = rig.hasFlagOrLocal(RigEntity.FLAG_SKID) || rig.hasFlagOrLocal(RigEntity.FLAG_BRAKE);
		double amount = speed * (0.6 + surface.spray) * (skidding ? 3.0 : 1.0) * (0.4 + Math.abs(lean) / 45.0);
		if (surface.isPowder()) {
			// powder throws a proper rooster tail and a bow wave, bigger the faster and harder you turn
			amount = (1.5 + speed * 16.0) * (0.6 + Math.abs(lean) / 30.0) * (skidding ? 1.6 : 1.0);
		} else if (surface.snowy) {
			amount = Math.max(amount, speed * 2.2);
		}
		int count = (int) amount + (random.nextDouble() < amount - (int) amount ? 1 : 0);
		double yaw = Math.toRadians(rig.getYRot());
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		// Spray flies out of the turn, away from the lean.
		double side = lean > 0 ? 1 : -1;
		double rx = -fz * side;
		double rz = fx * side;
		for (int i = 0; i < count; i++) {
			double back = 0.3 + random.nextDouble() * 0.5;
			double px = rig.getX() - fx * back + (random.nextDouble() - 0.5) * 0.4;
			double pz = rig.getZ() - fz * back + (random.nextDouble() - 0.5) * 0.4;
			double out = 0.05 + random.nextDouble() * 0.12 * (skidding ? 2 : 1);
			double vx = rx * out - fx * speed * 0.2 + (random.nextDouble() - 0.5) * 0.05;
			double vz = rz * out - fz * speed * 0.2 + (random.nextDouble() - 0.5) * 0.05;
			double vy = 0.06 + random.nextDouble() * 0.12 * (surface.isPowder() ? 2.6 : 1.0);
			level.addParticle(random.nextFloat() < 0.75f ? com.freshtracks.registry.ModParticles.SNOW_SPRAY : ParticleTypes.ITEM_SNOWBALL,
				px, rig.getY() + 0.08, pz, vx, vy, vz);
		}
		if (surface.isPowder()) {
			// a hanging cloud behind you, and a wave off the front
			for (int k = 0; k < 2 + (int) (speed * 4); k++) {
				level.addParticle(ParticleTypes.CLOUD, rig.getX() - fx * (0.5 + random.nextDouble() * 0.9) + (random.nextDouble() - 0.5) * 0.5,
					rig.getY() + 0.15 + random.nextDouble() * 0.5, rig.getZ() - fz * (0.5 + random.nextDouble() * 0.9) + (random.nextDouble() - 0.5) * 0.5,
					rx * 0.03, 0.04, rz * 0.03);
			}
			if (random.nextFloat() < speed) {
				level.addParticle(com.freshtracks.registry.ModParticles.SNOW_SPRAY, rig.getX() + fx * 0.7, rig.getY() + 0.3, rig.getZ() + fz * 0.7,
					(random.nextDouble() - 0.5) * 0.12, 0.12, (random.nextDouble() - 0.5) * 0.12);
			}
		}
	}

	/** A wave of powder thrown out of the turn, over the rider's head when it's deep. */
	private void powderPlume(ClientLevel level, RigEntity rig) {
		double yaw = Math.toRadians(rig.getYRot());
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double side = rig.lean(1f) > 0 ? 1 : -1;
		double rx = -fz * side;
		double rz = fx * side;
		for (int i = 0; i < 26; i++) {
			double out = 0.08 + random.nextDouble() * 0.2;
			double up = 0.15 + random.nextDouble() * 0.3;
			level.addParticle(random.nextFloat() < 0.8f ? com.freshtracks.registry.ModParticles.SNOW_SPRAY : ParticleTypes.CLOUD,
				rig.getX() + rx * 0.3 - fx * random.nextDouble() * 0.6, rig.getY() + 0.15, rig.getZ() + rz * 0.3 - fz * random.nextDouble() * 0.6,
				rx * out - fx * 0.05, up, rz * out - fz * 0.05);
		}
	}

	private static Surface surfaceUnder(RigEntity rig) {
		if (rig.hasFlagOrLocal(RigEntity.FLAG_GRIND)) return Surface.PARK_SLIDE;
		if (rig.isDrivenHere()) {
			return rig.motion.surface;
		}
		return SurfaceSampler.column(rig.level(), Mth.floor(rig.getX()), Mth.floor(rig.getZ()), rig.getY()).surface();
	}

	private static void burst(ClientLevel level, RigEntity rig, int count, double force) {
		RandomSource r = level.getRandom();
		for (int i = 0; i < count; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double s = force * (0.3 + r.nextDouble() * 0.7);
			level.addParticle(r.nextFloat() < 0.7f ? com.freshtracks.registry.ModParticles.SNOW_SPRAY : ParticleTypes.ITEM_SNOWBALL,
				rig.getX(), rig.getY() + 0.1, rig.getZ(), Math.cos(a) * s * 0.4, 0.1 + r.nextDouble() * force * 0.5, Math.sin(a) * s * 0.4);
		}
	}

	private void ensureLoops(Minecraft mc, RigEntity rig) {
		if (loops.containsKey(rig)) {
			return;
		}
		RigLoopSound[] created = new RigLoopSound[]{
			loop(rig, ModSounds.ICE_SCRAPE, r -> r.hasFlagOrLocal(RigEntity.FLAG_GRIND) ? Math.min(0.8, r.visualSpeed() * 1.3) : 0, r -> 0.75 + r.visualSpeed() * 0.4),
			loop(rig, ModSounds.CARVE, r -> snowLoudness(r, false) * (0.4 + Math.abs(r.lean(1f)) / 50.0), r -> 0.8 + r.visualSpeed() * 0.35),
			loop(rig, ModSounds.POWDER, r -> snowLoudness(r, true), r -> 0.85 + r.visualSpeed() * 0.2),
			loop(rig, ModSounds.SKID, r -> isSkidding(r) ? Math.min(1.0, r.visualSpeed() * 2.2) : 0, r -> 0.9 + r.visualSpeed() * 0.2),
			loop(rig, ModSounds.WIND, r -> windLoudness(r, mc), r -> 0.65 + Mth.clamp(r.visualSpeed(), 0, 2.1) * 0.5),
		};
		loops.put(rig, created);
		for (RigLoopSound sound : created) {
			mc.getSoundManager().play(sound);
		}
	}

	private static double windLoudness(RigEntity rig, Minecraft mc) {
		if (!rig.isVehicle()) return 0;
		double speed = Mth.clamp((rig.visualSpeed() - 0.16) / 1.5, 0, 1);
		double rush = 0.95 * Math.pow(speed, 1.35);
		return rush * (rig.getFirstPassenger() == mc.player ? com.freshtracks.FreshTracksConfig.rideWindVolume : 0.2);
	}

	private static boolean isSkidding(RigEntity rig) {
		return (rig.hasFlagOrLocal(RigEntity.FLAG_SKID) || rig.hasFlagOrLocal(RigEntity.FLAG_BRAKE)) && !rig.hasFlagOrLocal(RigEntity.FLAG_AIR);
	}

	private static double snowLoudness(RigEntity rig, boolean powder) {
		if (rig.hasFlagOrLocal(RigEntity.FLAG_AIR)) {
			return 0;
		}
		Surface surface = surfaceUnder(rig);
		if (!surface.snowy || surface.isPowder() != powder) {
			return 0;
		}
		return Mth.clamp(rig.visualSpeed() * 1.6, 0, 1);
	}

	private static RigLoopSound loop(RigEntity rig, SoundEvent event, ToDoubleFunction<RigEntity> volume, ToDoubleFunction<RigEntity> pitch) {
		return new RigLoopSound(rig, event, volume, pitch);
	}
}
