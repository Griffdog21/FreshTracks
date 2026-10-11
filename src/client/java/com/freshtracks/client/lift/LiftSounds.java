package com.freshtracks.client.lift;

import com.freshtracks.lift.LiftLine;
import com.freshtracks.registry.ModSounds;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** The motor hum at the terminal you're nearest to, and the clack-clack of chairs rolling over tower sheaves. */
public final class LiftSounds {
	private static final Map<Integer, Hum> HUMS = new HashMap<>();
	private static final Map<Integer, double[]> STATIONS = new HashMap<>();
	private static final RandomSource RANDOM = RandomSource.create();

	private LiftSounds() {
	}

	static final class Hum extends AbstractTickableSoundInstance {
		private final int netId;

		Hum(int netId) {
			super(ModSounds.LIFT_HUM, SoundSource.BLOCKS, RandomSource.create());
			this.netId = netId;
			this.looping = true;
			this.delay = 0;
			this.volume = 0.0001f;
			this.attenuation = SoundInstance.Attenuation.LINEAR;
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}

		@Override
		public void tick() {
			Minecraft mc = Minecraft.getInstance();
			ClientLifts.Entry entry = null;
			for (ClientLifts.Entry e : ClientLifts.all()) {
				if (e.netId == netId) {
					entry = e;
				}
			}
			if (entry == null || mc.player == null) {
				this.stop();
				return;
			}
			Vec3 bottom = entry.line.ropePoints().getFirst();
			Vec3 top = entry.line.ropePoints().getLast();
			Vec3 near = mc.player.distanceToSqr(bottom) < mc.player.distanceToSqr(top) ? bottom : top;
			this.x = near.x;
			this.y = near.y;
			this.z = near.z;
			float target = Math.min(1f, entry.speed * 3.2f) * 0.55f;
			this.volume = Math.max(0.0001f, this.volume + (target - this.volume) * 0.1f);
			this.pitch = 0.8f + entry.speed;
		}
	}

	public static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return;
		}
		Vec3 me = mc.player.position();
		for (ClientLifts.Entry entry : ClientLifts.all()) {
			LiftLine line = entry.line;
			Vec3 bottom = line.ropePoints().getFirst();
			Vec3 top = line.ropePoints().getLast();
			boolean nearTerminal = me.distanceToSqr(bottom) < 32 * 32 || me.distanceToSqr(top) < 32 * 32;
			Hum hum = HUMS.get(entry.netId);
			if (nearTerminal && (hum == null || hum.isStopped())) {
				hum = new Hum(entry.netId);
				HUMS.put(entry.netId, hum);
				mc.getSoundManager().play(hum);
			}
			if (entry.speed <= 0.001) {
				continue;
			}
			double[] stations = STATIONS.computeIfAbsent(entry.netId, k -> line.towerStations());
			for (int chair = 0; chair < line.chairCount(); chair++) {
				double before = line.chairS(entry.phaseO, chair);
				double after = line.chairS(entry.phase, chair);
				for (double s : stations) {
					if (crossed(before, after, s)) {
						Vec3 at = line.ropeAt(s).pos();
						if (at.distanceToSqr(me) < 40 * 40) {
							mc.level.playLocalSound(at.x, at.y, at.z, ModSounds.CHAIR_CLACK, SoundSource.BLOCKS, 0.45f,
								0.9f + RANDOM.nextFloat() * 0.2f, false);
						}
					}
				}
			}
		}
	}

	/** Forget cached tower positions (after a lift is edited). */
	public static void invalidate() {
		STATIONS.clear();
	}

	private static boolean crossed(double before, double after, double mark) {
		if (after >= before) {
			return before < mark && mark <= after;
		}
		return mark > before || mark <= after;
	}
}
