package com.freshtracks.lift;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * One lift: a haul rope looping around a bullwheel at each terminal, over a row of towers, with chairs
 * clipped on at a fixed spacing. Everything about where a chair is comes from {@link #phase}-free geometry here;
 * the moving part is a single number (how far the rope has travelled), so client and server always agree.
 *
 * <p>Positions are absolute world coordinates; {@code bottom} is the drive terminal.
 */
public final class LiftLine {
	/** Height of the rope above a terminal block's base. Chairs hang {@link ChairStyle#hanger} below it. */
	public static final double TERMINAL_ROPE = 3.1;
	/** Height of the rope above a tower head block's base. */
	public static final double HEAD_ROPE = 0.95;
	/** How far either side of the centre line the up and down ropes run. */
	public static final double TRACK_HALF = 2.0;
	/** Where riders get picked up / dropped off, measured along the rope from the bullwheel centre. */
	public static final double LOAD_AT = 2.6;
	public static final double UNLOAD_BEFORE = 2.8;
	/** Level rope section inside each terminal (fixed grips and surface lifts). */
	public static final double STATION_LENGTH = 5.5;
	/**
	 * Detachable lifts' stations are longer: the rope runs level through the whole housing, the carriers slowing down,
	 * swinging round the bullwheel and speeding up again before they leave its nose.
	 */
	public static final double STATION_LENGTH_HOUSED = 10.5;
	/** Detachable carriers crawl through the terminals at this fraction of rope speed. */
	public static final double STATION_RATE = 0.2;
	/** How far the slow zone reaches out from each bullwheel along the rope. */
	private static final double SLOW_REACH = 4.2;
	/** Length of rope over which a detachable carrier speeds up or slows down. */
	private static final double DETACH_RAMP = 3.0;

	public final UUID id;
	public String name;
	public final BlockPos bottom;
	public final BlockPos top;
	public final List<BlockPos> towers;
	/**
	 * Mid stations (the middle of each station's platform), in order up the line. The rope runs through them at loading
	 * height; detachable carriers slow down in them; riders can get on there, or hold sneak to get off.
	 */
	public final List<BlockPos> stations;
	public ChairStyle style;
	/** The terminals' paint (see {@link Livery}); AUTO picks one from the name. */
	public Livery livery = Livery.AUTO;
	/** Loading angle around the bottom bullwheel, in degrees before the uphill tangent. */
	public double bottomLoadAngle;
	/** Rope speed when running, blocks per tick. */
	public double speed;
	public double spacing;
	public boolean running;

	// Derived geometry
	private final List<Vec3> points = new ArrayList<>();
	private final List<Vec3> sides = new ArrayList<>();
	private double[] cumulative;
	private double upLength;
	private double loopLength;
	private int chairCount;
	/**
	 * Carrier timing. Each carrier keeps its own clock u, which runs at rope speed; its place on the loop is
	 * s(u). For fixed grips s = u. Detachable carriers run slower than the rope inside the terminals, so there
	 * s(u) is flatter, and one lap of u ({@link #cycleLength}) is longer than the loop. Piecewise: each segment
	 * starts at (segU, segS) and its rate ds/du changes linearly from segR0 to segR1.
	 */
	private double cycleLength;
	/** Distance up the rope (0..upLength) of each mid station. */
	private double[] midS = new double[0];
	private double[] segU = {0};
	private double[] segS = {0};
	private double[] segR0 = {1};
	private double[] segR1 = {1};

	public LiftLine(UUID id, String name, BlockPos bottom, BlockPos top, List<BlockPos> towers, ChairStyle style, double speed, double spacing, boolean running) {
		this(id, name, bottom, top, towers, List.of(), style, speed, spacing, running);
	}

	public LiftLine(UUID id, String name, BlockPos bottom, BlockPos top, List<BlockPos> towers, List<BlockPos> stations, ChairStyle style,
		double speed, double spacing, boolean running) {
		this.id = id;
		this.name = name;
		this.bottom = bottom;
		this.top = top;
		this.towers = List.copyOf(towers);
		this.stations = List.copyOf(stations);
		this.style = style;
		this.speed = speed;
		this.spacing = spacing;
		this.running = running;
		rebuild();
	}

	public static final Codec<LiftLine> CODEC = RecordCodecBuilder.create(i -> i.group(
		UUIDUtil.CODEC.fieldOf("id").forGetter(l -> l.id),
		Codec.STRING.fieldOf("name").forGetter(l -> l.name),
		BlockPos.CODEC.fieldOf("bottom").forGetter(l -> l.bottom),
		BlockPos.CODEC.fieldOf("top").forGetter(l -> l.top),
		BlockPos.CODEC.listOf().fieldOf("towers").forGetter(l -> l.towers),
		BlockPos.CODEC.listOf().optionalFieldOf("stations", List.of()).forGetter(l -> l.stations),
		ChairStyle.CODEC.optionalFieldOf("style", ChairStyle.QUAD).forGetter(l -> l.style),
		Codec.DOUBLE.optionalFieldOf("speed", 0.25).forGetter(l -> l.speed),
		Codec.DOUBLE.optionalFieldOf("spacing", 9.0).forGetter(l -> l.spacing),
		Codec.BOOL.optionalFieldOf("running", true).forGetter(l -> l.running),
		Livery.CODEC.optionalFieldOf("livery", Livery.AUTO).forGetter(l -> l.livery),
		Codec.doubleRange(0, 180).optionalFieldOf("bottom_load_angle", 0.0).forGetter(l -> l.bottomLoadAngle)
	).apply(i, (id, name, bottom, top, towers, stations, style, speed, spacing, running, livery, bottomLoadAngle) -> {
		LiftLine line = new LiftLine(id, name, bottom, top, towers, stations, style, speed, spacing, running);
		line.livery = livery;
		line.bottomLoadAngle = bottomLoadAngle;
		return line;
	}));

	public static final StreamCodec<RegistryFriendlyByteBuf, LiftLine> STREAM_CODEC = StreamCodec.of(
		(buf, l) -> {
			UUIDUtil.STREAM_CODEC.encode(buf, l.id);
			buf.writeUtf(l.name, 64);
			BlockPos.STREAM_CODEC.encode(buf, l.bottom);
			BlockPos.STREAM_CODEC.encode(buf, l.top);
			buf.writeVarInt(l.towers.size());
			for (BlockPos p : l.towers) {
				BlockPos.STREAM_CODEC.encode(buf, p);
			}
			buf.writeVarInt(l.stations.size());
			for (BlockPos p : l.stations) {
				BlockPos.STREAM_CODEC.encode(buf, p);
			}
			buf.writeVarInt(l.style.ordinal());
			buf.writeDouble(l.speed);
			buf.writeDouble(l.spacing);
			buf.writeBoolean(l.running);
			buf.writeVarInt(l.livery.ordinal());
			buf.writeDouble(l.bottomLoadAngle);
		},
		buf -> {
			UUID id = UUIDUtil.STREAM_CODEC.decode(buf);
			String name = buf.readUtf(64);
			BlockPos bottom = BlockPos.STREAM_CODEC.decode(buf);
			BlockPos top = BlockPos.STREAM_CODEC.decode(buf);
			int n = Math.min(buf.readVarInt(), 512);
			List<BlockPos> towers = new ArrayList<>(n);
			for (int k = 0; k < n; k++) {
				towers.add(BlockPos.STREAM_CODEC.decode(buf));
			}
			int ns = Math.min(buf.readVarInt(), 16);
			List<BlockPos> stations = new ArrayList<>(ns);
			for (int k = 0; k < ns; k++) {
				stations.add(BlockPos.STREAM_CODEC.decode(buf));
			}
			ChairStyle style = ChairStyle.byId(buf.readVarInt());
			LiftLine line = new LiftLine(id, name, bottom, top, towers, stations, style, buf.readDouble(), buf.readDouble(), buf.readBoolean());
			line.livery = Livery.byId(buf.readVarInt());
			line.bottomLoadAngle = Mth.clamp(buf.readDouble(), 0, 180);
			return line;
		});

	public static final StreamCodec<RegistryFriendlyByteBuf, List<LiftLine>> LIST_STREAM_CODEC = LiftLine.STREAM_CODEC.apply(ByteBufCodecs.list(4096));

	// ----------------------------------------------------------------------------------------- geometry

	/** How far the rope runs level out of each terminal for this kind of lift. */
	public static double stationLength(ChairStyle style) {
		return style.detachable() || style.cabin() || style.pulse() ? STATION_LENGTH_HOUSED : STATION_LENGTH;
	}

	/** Recomputes the rope path. Call after changing towers or terminals. */
	public void rebuild() {
		points.clear();
		Vec3 start = Vec3.atBottomCenterOf(bottom).add(0, TERMINAL_ROPE, 0);
		Vec3 end = Vec3.atBottomCenterOf(top).add(0, TERMINAL_ROPE, 0);
		List<Vec3> mid = new ArrayList<>();
		for (BlockPos tower : towers) {
			mid.add(Vec3.atBottomCenterOf(tower).add(0, HEAD_ROPE, 0));
		}
		// Inside each terminal the rope runs level for a few blocks (the loading / unloading zone), like the real thing.
		Vec3 firstOut = mid.isEmpty() ? end : mid.getFirst();
		Vec3 lastIn = mid.isEmpty() ? start : mid.getLast();
		points.add(start);
		Vec3 outDir = flat(firstOut.subtract(start));
		double station = stationLength(style);
		if (horizontalDistance(start, firstOut) > station + 2) {
			points.add(start.add(outDir.scale(station)));
		}
		points.addAll(mid);
		Vec3 inDir = flat(end.subtract(lastIn));
		if (horizontalDistance(lastIn, end) > station + 2) {
			points.add(end.subtract(inDir.scale(station)));
		}
		points.add(end);
		cumulative = new double[points.size()];
		for (int k = 1; k < points.size(); k++) {
			cumulative[k] = cumulative[k - 1] + points.get(k).distanceTo(points.get(k - 1));
		}
		upLength = cumulative[points.size() - 1];
		// Side vector at each point: horizontal, to the right of travel uphill (the up rope runs on this side).
		sides.clear();
		for (int k = 0; k < points.size(); k++) {
			Vec3 before = points.get(Math.max(0, k - 1));
			Vec3 after = points.get(Math.min(points.size() - 1, k + 1));
			Vec3 dir = new Vec3(after.x - before.x, 0, after.z - before.z);
			if (dir.lengthSqr() < 1.0e-6) {
				dir = new Vec3(0, 0, 1);
			}
			dir = dir.normalize();
			sides.add(new Vec3(-dir.z, 0, dir.x));
		}
		loopLength = 2 * upLength + 2 * Math.PI * TRACK_HALF;
		midS = new double[stations.size()];
		for (int i = 0; i < midS.length; i++) {
			midS[i] = distanceUpTo(stations.get(i));
		}
		java.util.Arrays.sort(midS);
		buildTiming();
		chairCount = Math.max(2, (int) Math.floor(cycleLength / Math.max(4.0, spacing)));
		if (style.pulse()) {
			// a few groups spread round the loop; spacing sets how many
			int groups = Mth.clamp((int) Math.round(loopLength / Math.max(60.0, spacing * 20)), 2, 4);
			chairCount = groups * PULSE_CABINS;
		}
	}

	private void buildTiming() {
		List<double[]> segs = new ArrayList<>();
		double half = Math.PI * TRACK_HALF;
		if (!style.detachable() || upLength < 2 * (SLOW_REACH + DETACH_RAMP) + 4) {
			segs.add(new double[]{0, loopLength, 1, 1});
		} else {
			double k = STATION_RATE;
			double a = SLOW_REACH;
			double r = DETACH_RAMP;
			// The slow zones round the loop, in order: out of the bottom station, the mid stations on the way up, the top
			// station, the mid stations on the way down, into the bottom station. Full speed between them, with a ramp
			// either side (shorter when two stations are close).
			List<double[]> zones = new ArrayList<>();
			zones.add(new double[]{0, a});
			for (double m : midS) {
				zones.add(new double[]{m - a, m + a});
			}
			zones.add(new double[]{upLength - a, upLength + half + a});
			for (int i = midS.length - 1; i >= 0; i--) {
				double m = upLength + half + (upLength - midS[i]);
				zones.add(new double[]{m - a, m + a});
			}
			zones.add(new double[]{loopLength - half - a, loopLength});
			// merge any that overlap
			List<double[]> merged = new ArrayList<>();
			for (double[] z : zones) {
				if (!merged.isEmpty() && z[0] <= merged.getLast()[1]) {
					merged.getLast()[1] = Math.max(merged.getLast()[1], z[1]);
				} else {
					merged.add(new double[]{Math.max(0, z[0]), Math.min(loopLength, z[1])});
				}
			}
			double s = 0;
			for (int i = 0; i < merged.size(); i++) {
				double z0 = merged.get(i)[0];
				double z1 = merged.get(i)[1];
				if (i > 0) {
					double ramp = Math.min(r, (z0 - merged.get(i - 1)[1]) / 2);
					addSeg(segs, s, z0 - ramp, 1, 1);
					addSeg(segs, z0 - ramp, z0, 1, k);
					s = z0;
				}
				addSeg(segs, s, z1, k, k);
				s = z1;
				if (i + 1 < merged.size()) {
					double ramp = Math.min(r, (merged.get(i + 1)[0] - z1) / 2);
					addSeg(segs, s, s + ramp, k, 1);
					s += ramp;
				}
			}
		}
		int n = segs.size();
		segU = new double[n];
		segS = new double[n];
		segR0 = new double[n];
		segR1 = new double[n];
		double u = 0;
		for (int i = 0; i < n; i++) {
			double[] seg = segs.get(i);
			segU[i] = u;
			segS[i] = seg[0];
			segR0[i] = seg[2];
			segR1[i] = seg[3];
			// rate linear in u: s-length = u-length * (r0 + r1) / 2
			u += (seg[1] - seg[0]) * 2 / (seg[2] + seg[3]);
		}
		cycleLength = u;
	}

	private static void addSeg(List<double[]> segs, double from, double to, double r0, double r1) {
		if (to - from > 1.0e-6) {
			segs.add(new double[]{from, to, r0, r1});
		}
	}

	/** Distance up the rope (0..upLength) of the point on the line nearest to a block (judged from above). */
	private double distanceUpTo(BlockPos pos) {
		double px = pos.getX() + 0.5;
		double pz = pos.getZ() + 0.5;
		double best = Double.MAX_VALUE;
		double bestS = 0;
		for (int k = 1; k < points.size(); k++) {
			Vec3 a = points.get(k - 1);
			Vec3 b = points.get(k);
			double dx = b.x - a.x;
			double dz = b.z - a.z;
			double len2 = dx * dx + dz * dz;
			double t = len2 < 1.0e-9 ? 0 : Mth.clamp(((px - a.x) * dx + (pz - a.z) * dz) / len2, 0, 1);
			double qx = a.x + dx * t - px;
			double qz = a.z + dz * t - pz;
			double d = qx * qx + qz * qz;
			if (d < best) {
				best = d;
				bestS = cumulative[k - 1] + t * (cumulative[k] - cumulative[k - 1]);
			}
		}
		return bestS;
	}

	/** How many mid stations the line has. */
	public int midStations() {
		return midS.length;
	}

	/** Loop position where riders get on at mid station {@code i} (on the way up). */
	public double midLoadS(int i) {
		return midS[i] - 1.5;
	}

	/** Loop position where riders holding sneak get off at mid station {@code i} (on the way up). */
	public double midUnloadS(int i) {
		return midS[i] + 1.5;
	}

	/** Distance up the rope of mid station {@code i}. */
	public double midS(int i) {
		return midS[i];
	}

	/** Where riders stand to board at mid station {@code i}. */
	public Vec3 midLoadPoint(int i) {
		return ropeAt(midLoadS(i)).pos().add(0, -style.hanger, 0);
	}

	private int segmentAtU(double u) {
		int i = segU.length - 1;
		while (i > 0 && u < segU[i]) {
			i--;
		}
		return i;
	}

	/** Loop position of a carrier whose clock reads u. */
	public double sAt(double u) {
		u = Mth.positiveModulo(u, cycleLength);
		int i = segmentAtU(u);
		double length = (i + 1 < segU.length ? segU[i + 1] : cycleLength) - segU[i];
		double du = u - segU[i];
		double r0 = segR0[i];
		double r1 = segR1[i];
		return Math.min(loopLength, segS[i] + r0 * du + (r1 - r0) * du * du / (2 * Math.max(length, 1.0e-6)));
	}

	/** How fast a carrier at loop position s moves, as a fraction of rope speed. */
	public double rateAt(double s) {
		s = Mth.positiveModulo(s, loopLength);
		int i = segS.length - 1;
		while (i > 0 && s < segS[i]) {
			i--;
		}
		double end = i + 1 < segS.length ? segS[i + 1] : loopLength;
		double t = end - segS[i] < 1.0e-6 ? 0 : (s - segS[i]) / (end - segS[i]);
		// rate is linear in u, not s; close enough for speeds shown on screen
		return Mth.lerp(t, segR0[i], segR1[i]);
	}

	/** One lap of a carrier's clock, in blocks of rope travel. Equals the loop length for fixed grips. */
	public double cycleLength() {
		return cycleLength;
	}

	public List<Vec3> ropePoints() {
		return points;
	}

	public double upLength() {
		return upLength;
	}

	public double loopLength() {
		return loopLength;
	}

	public int chairCount() {
		return chairCount;
	}

	/** Exact spacing (in carrier clock) so the chairs divide the cycle evenly. */
	public double chairSpacing() {
		return cycleLength / chairCount;
	}

	public double sagFactor() {
		return 0.018;
	}

	/** A point on the centre line and the side vector at distance d (0..upLength) from the bottom bullwheel. */
	private Vec3[] centreAt(double d) {
		d = Mth.clamp(d, 0, upLength);
		int k = 1;
		while (k < points.size() - 1 && cumulative[k] < d) {
			k++;
		}
		double span = cumulative[k] - cumulative[k - 1];
		double t = span < 1.0e-6 ? 0 : (d - cumulative[k - 1]) / span;
		Vec3 a = points.get(k - 1);
		Vec3 b = points.get(k);
		Vec3 p = a.lerp(b, t);
		double sag = 4 * sagFactor() * span * t * (1 - t);
		p = p.add(0, -sag, 0);
		Vec3 side = sides.get(k - 1).lerp(sides.get(k), t).normalize();
		Vec3 dir = b.subtract(a);
		return new Vec3[]{p, side, dir.lengthSqr() > 0 ? dir.normalize() : new Vec3(0, 0, 1)};
	}

	/** Where the rope is at loop position s, and which way it's moving. */
	public RopePoint ropeAt(double s) {
		s = Mth.positiveModulo(s, loopLength);
		double half = Math.PI * TRACK_HALF;
		if (s < upLength) {
			Vec3[] c = centreAt(s);
			return new RopePoint(c[0].add(c[1].scale(TRACK_HALF)), c[2], true);
		}
		s -= upLength;
		if (s < half) {
			// Round the top bullwheel: from the up side to the down side.
			Vec3[] c = centreAt(upLength);
			double angle = s / TRACK_HALF;
			Vec3 radial = c[1].scale(Math.cos(angle)).add(horizontal(c[2]).scale(Math.sin(angle)));
			Vec3 tangent = c[1].scale(-Math.sin(angle)).add(horizontal(c[2]).scale(Math.cos(angle)));
			return new RopePoint(c[0].add(radial.scale(TRACK_HALF)), tangent, false);
		}
		s -= half;
		if (s < upLength) {
			Vec3[] c = centreAt(upLength - s);
			return new RopePoint(c[0].subtract(c[1].scale(TRACK_HALF)), c[2].scale(-1), false);
		}
		s -= upLength;
		Vec3[] c = centreAt(0);
		double angle = s / TRACK_HALF;
		Vec3 back = horizontal(c[2]).scale(-1);
		Vec3 radial = c[1].scale(-Math.cos(angle)).add(back.scale(Math.sin(angle)));
		Vec3 tangent = c[1].scale(Math.sin(angle)).add(back.scale(Math.cos(angle)));
		return new RopePoint(c[0].add(radial.scale(TRACK_HALF)), tangent, true);
	}

	private static Vec3 flat(Vec3 v) {
		Vec3 h = new Vec3(v.x, 0, v.z);
		return h.lengthSqr() > 1.0e-6 ? h.normalize() : new Vec3(0, 0, 1);
	}

	private static double horizontalDistance(Vec3 a, Vec3 b) {
		double dx = b.x - a.x;
		double dz = b.z - a.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static Vec3 horizontal(Vec3 v) {
		Vec3 h = new Vec3(v.x, 0, v.z);
		return h.lengthSqr() > 1.0e-6 ? h.normalize() : new Vec3(0, 0, 1);
	}

	public record RopePoint(Vec3 pos, Vec3 direction, boolean uphill) {
		public float yaw() {
			return (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
		}
	}

	/** Loop position of chair {@code index} when the rope has travelled {@code phase}. */
	public double chairS(double phase, int index) {
		return sAt(phase + chairOffset(index));
	}

	/** Cabins per group on a pulse gondola, and the gap between cabins in a group. */
	public static final int PULSE_CABINS = 3;
	public static final double PULSE_GAP = 7.0;
	/** A pulse rope crawls at this share of full speed while a group is in a station. */
	public static final double PULSE_CRAWL = 0.07;

	/** Where carrier {@code index} sits on the carrier clock relative to carrier 0. */
	public double chairOffset(int index) {
		if (style.pulse()) {
			int groups = Math.max(1, chairCount / PULSE_CABINS);
			return (index / PULSE_CABINS) * (cycleLength / groups) + (index % PULSE_CABINS) * PULSE_GAP;
		}
		return index * chairSpacing();
	}

	/** Pulse gondolas: the rope speed for this rope position (full speed, or a crawl while a group is in a station). */
	public double pulseTarget(double phase, double full) {
		if (!style.pulse()) {
			return full;
		}
		double zone = stationLength(style) + 4.0;
		double topLo = upLength - zone;
		double topHi = upLength + Math.PI * TRACK_HALF + zone;
		for (int i = 0; i < chairCount; i++) {
			double s = chairS(phase, i);
			if (s < zone || s > loopLength - zone || s > topLo && s < topHi) {
				return full * PULSE_CRAWL;
			}
		}
		return full;
	}

	/** Rope travel until chair {@code index} next reaches loop position {@code s}, in blocks of rope (divide by speed for ticks). */
	public double ropeUntil(double phase, int index, double s) {
		// invert s(u) by bisection: s(u) is monotonic over one cycle
		double u0 = Mth.positiveModulo(phase + chairOffset(index), cycleLength);
		double lo = 0;
		double hi = cycleLength;
		double start = sAt(u0);
		double want = Mth.positiveModulo(s - start, loopLength);
		for (int k = 0; k < 40; k++) {
			double mid = (lo + hi) / 2;
			double travelled = Mth.positiveModulo(sAt(u0 + mid) - start, loopLength);
			if (mid > 0 && travelled < 1.0e-9) {
				travelled = loopLength; // came all the way round
			}
			if (travelled < want) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return hi;
	}

	/** Where a surface lift's rider stands: under the rope, on the snow line between the terminals. */
	public Vec3 towPoint(double s, int slot) {
		RopePoint rope = ropeAt(s);
		Vec3 dir = horizontal(rope.direction());
		Vec3 right = new Vec3(-dir.z, 0, dir.x);
		// riders trail a little behind the carrier: the bar's spring line angles back to them
		return rope.pos().add(right.scale(style.seatOffset(slot))).add(dir.scale(-0.45));
	}

	/** World position of a seat (feet of the rider) on a chair. */
	public Vec3 seatPos(double phase, int chair, int seat) {
		RopePoint rope = ropeAt(chairS(phase, chair));
		Vec3 dir = horizontal(rope.direction());
		Vec3 right = new Vec3(-dir.z, 0, dir.x);
		double lateral = style.seatOffset(seat);
		// gondola riders sit on the cabin's benches: the first half on the back one, the rest on the front one
		double back = style.cabin() ? (seat < Math.max(1, style.seats / 2) ? 0.6 : -0.6) : 0.15;
		return rope.pos().add(0, -style.hanger, 0).add(right.scale(lateral)).add(dir.scale(-back));
	}

	/** Loop position where riders get on (just past the bottom bullwheel, up side). */
	public double loadS() {
		return bottomLoadAngle > 0 ? loopLength - Math.toRadians(bottomLoadAngle) * TRACK_HALF : LOAD_AT;
	}

	/** Loop position where riders get off at the top. */
	public double unloadS() {
		return Math.max(LOAD_AT + 4, upLength - UNLOAD_BEFORE);
	}

	/** Bottom unload point for anyone who rode all the way back down. */
	public double downUnloadS() {
		return loopLength - Math.PI * TRACK_HALF - UNLOAD_BEFORE;
	}

	public Vec3 loadPoint() {
		RopePoint rope = ropeAt(loadS());
		return rope.pos().add(0, -style.hanger, 0);
	}

	/** Loop positions where a chair rolls over a tower's sheaves (both the up and the down side). */
	public double[] towerStations() {
		List<Double> list = new ArrayList<>();
		for (BlockPos tower : towers) {
			Vec3 at = Vec3.atBottomCenterOf(tower).add(0, HEAD_ROPE, 0);
			for (int k = 0; k < points.size(); k++) {
				if (points.get(k).distanceToSqr(at) < 0.01) {
					list.add(cumulative[k]);
					list.add(upLength + Math.PI * TRACK_HALF + (upLength - cumulative[k]));
					break;
				}
			}
		}
		double[] out = new double[list.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = list.get(i);
		}
		return out;
	}

	public Vec3 uphillDirection() {
		return horizontal(points.get(points.size() - 1).subtract(points.get(0)));
	}

	public LiftLine copyWith(List<BlockPos> newTowers) {
		LiftLine copy = new LiftLine(id, name, bottom, top, newTowers, stations, style, speed, spacing, running);
		copy.livery = livery;
		return copy;
	}

	/** The paint the terminals actually wear (AUTO resolved). */
	public Livery livery() {
		return Livery.resolve(livery, style, name);
	}

	/**
	 * Which way a terminal's station runs, flat: from the bullwheel along the level rope section towards the line. The
	 * bottom station points up the line, the top one down it.
	 */
	public Vec3 stationDirection(boolean topTerminal) {
		int n = points.size();
		Vec3 from = topTerminal ? points.get(n - 1) : points.get(0);
		Vec3 to = topTerminal ? points.get(n - 2) : points.get(1);
		return horizontal(to.subtract(from));
	}
}
