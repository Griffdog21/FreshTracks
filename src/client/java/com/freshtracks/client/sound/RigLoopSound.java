package com.freshtracks.client.sound;

import com.freshtracks.rig.RigEntity;
import java.util.function.ToDoubleFunction;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** A looping sound glued to a rig whose loudness follows the ride (carving hiss, powder whoosh, wind). */
public class RigLoopSound extends AbstractTickableSoundInstance {
	private final RigEntity rig;
	private final ToDoubleFunction<RigEntity> loudness;
	private final ToDoubleFunction<RigEntity> pitchFn;
	private float smoothed;

	public RigLoopSound(RigEntity rig, SoundEvent event, ToDoubleFunction<RigEntity> loudness, ToDoubleFunction<RigEntity> pitch) {
		super(event, SoundSource.PLAYERS, RandomSource.create());
		this.rig = rig;
		this.loudness = loudness;
		this.pitchFn = pitch;
		this.looping = true;
		this.delay = 0;
		this.volume = 0.0001f;
		this.attenuation = SoundInstance.Attenuation.LINEAR;
		this.x = rig.getX();
		this.y = rig.getY();
		this.z = rig.getZ();
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public boolean canPlaySound() {
		return !rig.isSilent();
	}

	@Override
	public void tick() {
		if (rig.isRemoved()) {
			stop();
			return;
		}
		float target = (float) Mth.clamp(loudness.applyAsDouble(rig), 0.0, 1.2);
		smoothed += (target - smoothed) * 0.25f;
		this.volume = Math.max(0.0001f, smoothed);
		this.pitch = (float) Mth.clamp(pitchFn.applyAsDouble(rig), 0.5, 2.0);
		this.x = rig.getX();
		this.y = rig.getY();
		this.z = rig.getZ();
	}
}
