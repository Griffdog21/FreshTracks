package com.freshtracks.client.lift;

import com.freshtracks.lift.LiftLine;
import com.freshtracks.lift.LiftSeatEntity;
import com.freshtracks.net.LiftPhasePayload;
import com.freshtracks.net.LiftSyncPayload;
import com.freshtracks.rig.RigEntity;
import com.freshtracks.rig.RigMotion;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The client's copy of every lift in the current dimension, with the rope position predicted between syncs. */
public final class ClientLifts {
	public static final class Entry {
		public final int netId;
		public LiftLine line;
		public double phase;
		public double phaseO;
		public float speed;
		/** Rope position error from the last server sync, worked off a little each tick so nothing ever jumps. */
		double correction;

		Entry(int netId, LiftLine line, double phase, float speed) {
			this.netId = netId;
			this.line = line;
			this.phase = phase;
			this.phaseO = phase;
			this.speed = speed;
		}

		/** Rope position at render time, unwrapped so it never jumps backwards across the loop seam. */
		public double phase(float partialTick) {
			double delta = phase - phaseO;
			double loop = line.cycleLength();
			if (delta < -loop / 2) {
				delta += loop;
			}
			return phaseO + delta * partialTick;
		}
	}

	private static final Map<Integer, Entry> LINES = new HashMap<>();

	private ClientLifts() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(LiftSyncPayload.TYPE, (payload, context) -> onSync(payload));
		ClientPlayNetworking.registerGlobalReceiver(LiftPhasePayload.TYPE, (payload, context) -> onPhase(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LINES.clear());
		LiftSeatEntity.clientLocator = (seat, partialTick) -> {
			Entry entry = LINES.get(seat.lineNetId());
			if (entry == null) {
				return null;
			}
			return entry.line.seatPos(entry.phase(partialTick), seat.chairIndex(), seat.seatIndex());
		};
		RigEntity.towLocator = (rig, netId, carrier, slot) -> {
			Entry entry = LINES.get(netId);
			if (entry == null || !entry.line.style.surface()) {
				return null;
			}
			LiftLine line = entry.line;
			double s = line.chairS(entry.phase, carrier);
			if (s > line.unloadS() + 0.3) {
				return null; // the bar has let go at the top
			}
			Vec3 at = line.towPoint(s, slot);
			Vec3 dir = line.ropeAt(s).direction();
			Vec3 flat = new Vec3(dir.x, 0, dir.z).normalize();
			double v = entry.speed * line.rateAt(s);
			return new RigMotion.Tow(at.x, at.z, flat.x * v, flat.z * v, RigMotion.yawOf(flat.x, flat.z));
		};
	}

	/** Ticks until the next carrier reaches loop position {@code s}, or -1 if the lift is stopped. */
	public static double ticksUntil(Entry entry, double s) {
		if (entry.speed < 1.0e-4) {
			return -1;
		}
		double best = Double.MAX_VALUE;
		for (int chair = 0; chair < entry.line.chairCount(); chair++) {
			best = Math.min(best, entry.line.ropeUntil(entry.phase, chair, s));
		}
		return best / entry.speed;
	}

	public static @Nullable Entry byNetId(int netId) {
		return LINES.get(netId);
	}

	private static void onSync(LiftSyncPayload payload) {
		Map<Integer, Entry> fresh = new HashMap<>();
		for (LiftSyncPayload.Entry e : payload.entries()) {
			Entry old = LINES.get(e.netId());
			Entry entry = new Entry(e.netId(), e.line(), e.phase(), e.speed());
			if (old != null && old.line.cycleLength() == e.line().cycleLength()) {
				// keep motion smooth across a resync
				entry.phase = old.phase;
				entry.phaseO = old.phaseO;
				correct(entry, e.phase());
			}
			fresh.put(e.netId(), entry);
		}
		LINES.clear();
		LINES.putAll(fresh);
		LiftSounds.invalidate();
	}

	private static void onPhase(LiftPhasePayload payload) {
		for (int i = 0; i < payload.netIds().length; i++) {
			Entry entry = LINES.get(payload.netIds()[i]);
			if (entry != null) {
				entry.speed = payload.speeds()[i];
				correct(entry, payload.phases()[i]);
			}
		}
	}

	private static void correct(Entry entry, double serverPhase) {
		double loop = entry.line.cycleLength();
		double error = Mth.positiveModulo(serverPhase - entry.phase + loop / 2, loop) - loop / 2;
		if (Math.abs(error) > 6.0) {
			entry.phase = Mth.positiveModulo(serverPhase, loop);
			entry.phaseO = entry.phase;
			entry.correction = 0;
		} else {
			// worked off gradually in tick()
			entry.correction = error;
		}
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.isPaused()) {
			return;
		}
		for (Entry entry : LINES.values()) {
			if (entry.line.style.pulse() && entry.line.running) {
				// mirror the server's slow-down in the stations between syncs
				double target = entry.line.pulseTarget(entry.phase, entry.line.speed);
				entry.speed = (float) (entry.speed < target ? Math.min(target, entry.speed + 0.004) : Math.max(target, entry.speed - 0.004));
			}
			entry.phaseO = entry.phase;
			double step = Mth.clamp(entry.correction * 0.06, -0.012, 0.012);
			entry.correction -= step;
			entry.phase = Mth.positiveModulo(entry.phase + entry.speed + step, entry.line.cycleLength());
			if (entry.phase < entry.phaseO) {
				entry.phaseO -= entry.line.cycleLength();
			}
		}
	}

	public static Collection<Entry> all() {
		return LINES.values();
	}

	public static @Nullable Entry byTerminal(BlockPos pos) {
		for (Entry entry : LINES.values()) {
			if (entry.line.bottom.equals(pos) || entry.line.top.equals(pos)) {
				return entry;
			}
		}
		return null;
	}

	public static @Nullable Entry byTower(BlockPos pos) {
		for (Entry entry : LINES.values()) {
			if (entry.line.towers.contains(pos)) {
				return entry;
			}
		}
		return null;
	}

	public static Vec3 towerDirection(Entry entry, BlockPos tower) {
		var pts = entry.line.ropePoints();
		Vec3 at = Vec3.atBottomCenterOf(tower).add(0, LiftLine.HEAD_ROPE, 0);
		int k = 0;
		for (int i = 0; i < pts.size(); i++) {
			if (pts.get(i).distanceToSqr(at) < 0.01) {
				k = i;
				break;
			}
		}
		Vec3 before = pts.get(Math.max(0, k - 1));
		Vec3 after = pts.get(Math.min(pts.size() - 1, k + 1));
		Vec3 d = after.subtract(before);
		d = new Vec3(d.x, 0, d.z);
		return d.lengthSqr() > 1.0e-6 ? d.normalize() : new Vec3(0, 0, 1);
	}
}
