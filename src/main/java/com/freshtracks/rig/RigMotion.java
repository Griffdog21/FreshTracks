package com.freshtracks.rig;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import com.freshtracks.weather.SnowConditions;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The ski / snowboard physics. Runs on the rider's own client (like a boat), one step per tick, in blocks and
 * ticks. The server only sees the resulting positions.
 *
 * <h2>How turning works</h2>
 * Holding a direction rolls the rig onto that edge over a few ticks ({@link #edge}). The edge sets how hard the
 * skis want to turn (a sidecut radius that shrinks with edge angle), but the snow can only hold so much sideways
 * force ({@link #lateralGrip}): carve within that and the velocity swings round with the skis at no cost;
 * crank past it (or brake-steer, or hit ice) and the skis skid, spray snow and scrub speed.
 */
public final class RigMotion {
	// Gravity along the slope and in the air (blocks/tick^2). Real 1 g would be 0.0245.
	public static final double G_SLOPE = 0.046;
	public static final double G_AIR = 0.046;
	// Quadratic drag coefficients (per block): decel = c * v^2.
	public static final double DRAG = 0.0098;
	public static final double TUCK_DRAG = 0.0058;
	public static final double AIR_DRAG = 0.0030;
	public static final double MAX_SPEED = 2.1;

	// Edges: per-tick change of the edge value (0..1).
	private static final float SKI_ENGAGE = 0.24f;
	private static final float BOARD_ENGAGE = 0.19f;
	private static final float RELEASE = 0.45f;
	// Sidecut radius at full edge, blocks.
	private static final double R_FULL = 5.5;
	// How much of an edge's turning beyond what the snow can hold still rotates the skis (as a skid).
	private static final double OVERSTEER = 0.0;
	// Lateral grip: how much sideways acceleration the snow holds (b/t^2) flat and at full edge.
	private static final double LAT_FLAT = 0.010;
	private static final double LAT_EDGE = 0.045;
	// Turning on the spot at walking speed, deg/tick.
	private static final float PIVOT = 8.0f;

	private static final float HOCKEY_RATE = 13.0f;
	/** Edge check (back + a direction): the skis swing this far across the direction of travel, this fast. */
	private static final float EDGE_CHECK_ANGLE = 78f;
	private static final float EDGE_CHECK_RATE = 10f;
	/** Steering in the air without a trick: degrees per tick, and how far from the direction of travel. */
	private static final float AIR_STEER = 3.0f;
	private static final float AIR_STEER_LIMIT = 70f;
	private static final double SKID_DECEL = 0.010;
	private static final double SKID_DECEL_V = 0.035;

	// Pushing / skating on the flats.
	private static final double PUSH_SKI = 0.0110;
	private static final double PUSH_BOARD = 0.0080;
	private static final double PUSH_MAX = 0.26;

	// Air.
	private static final double POP_BASE = 0.30;
	private static final double POP_CHARGE = 0.17;
	private static final float SPIN_MAX = 23f;
	private static final float SPIN_ACCEL = 4.2f;
	private static final float SPIN_DECAY = 4.0f;
	private static final float FLIP_MAX = 17f;
	private static final float FLIP_ACCEL = 2.5f;
	private static final float FLIP_DECAY = 3.0f;

	// Wind push at full strength, blocks/tick^2.
	private static final double WIND_GROUND = 0.0009;
	private static final double WIND_AIR = 0.0030;

	// Leaving the ground: the snow has to fall away this far below where momentum would carry you (plus a bit
	// more at speed) before you're airborne. Steep pitches and block steps under that are ridden, not jumped.
	private static final double STICK = 0.30;
	private static final double STICK_SPEED = 0.10;

	// Crashes.
	private static final double WALL_CRASH = 0.42;
	private static final double LANDING_CRASH = 1.15;

	public double vx;
	public double vy;
	public double vz;
	/** Where the noses point (Minecraft yaw, degrees). */
	public float heading;
	public boolean switchStance;
	/** -1 (left edge) .. 1 (right edge). */
	public float edge;
	/** Visual lean into the turn, degrees, positive = leaning right. */
	public float lean;
	public float leanO;
	public boolean grounded = true;
	public int groundTicks;
	public int airTicks;
	public float jumpCharge;
	public boolean jumpHeld;
	/** Ticks until the next push stroke can land. */
	public int strokeTimer;
	/** Spin wound up while loading an ollie (A/D held), -1..1. */
	public float windUp;
	/** This air was on purpose (an ollie, or launched up off a lip): flips are allowed. Rolling off a bump isn't. */
	public boolean intendedAir;
	public boolean tuck;
	public boolean plow;
	public boolean hockeyStop;
	/** Back + a direction: swinging the skis across and digging the edges in to scrub speed (or stop). */
	public boolean edgeCheck;
	/** How far below the powder's surface the skis ride right now (you plane up on top once you're moving). */
	public double powderSink;
	/** 0 = bogged down in powder, 1 = planing on top of it. */
	private double powderPlane = 1.0;
	// Powder turns: which edge we're on and where the current turn started.
	private int turnEdge;
	private float turnStartHeading;
	public float spinRate;
	public float flipRate;
	public float flipVisual;
	public float flipVisualO;
	public float skid;
	public Surface surface = Surface.PACKED;
	/** Heading change from steering this tick; the camera follows it. */
	public float steerYaw;
	public boolean initialized;
	public final TrickTracker tricks = new TrickTracker();
	public final Events events = new Events();
	/** Hardware contact is separate from snow carving: heading may rotate without steering the slide. */
	public boolean grinding;
	public boolean buttering;
	public int press;
	public double grindDistance;
	public int grindTicks;
	private @Nullable ParkContact grindContact;
	private int grindDirection = 1;
	private int grindCooldown;
	private float grindSpin;
	private int grindSideTicks;
	private int grindPressTicks;
	private int butterTicks;
	private double butterDistance;
	private float butterSpin;
	private int butterPress;

	public static final class Events {
		public boolean launched;
		public boolean landed;
		public float landingImpact;
		public TrickTracker.Result trick;
		public boolean crashed;
		public float crashSeverity;
		public String crashReason;
		public boolean wallBonk;
		public boolean sank;
		public boolean popped;
		public boolean groundTrick;
		/** A push stroke this tick: 0 none, 1 a skate stride, 2 a double-pole plant. */
		public int stroke;
		/** A stomped landing gave a burst of speed this tick. */
		public boolean boost;
		/** The ollie was timed right at a lip and went higher. */
		public boolean perfectPop;

		void clear() {
			launched = false;
			landed = false;
			landingImpact = 0;
			trick = null;
			crashed = false;
			crashSeverity = 0;
			crashReason = null;
			wallBonk = false;
			sank = false;
			popped = false;
			groundTrick = false;
			stroke = 0;
			boost = false;
			perfectPop = false;
		}
	}

	public double speed() {
		return Math.sqrt(vx * vx + vz * vz);
	}

	public double speed3() {
		return Math.sqrt(vx * vx + vy * vy + vz * vz);
	}

	public float travelHeading() {
		return switchStance ? heading + 180f : heading;
	}

	public void reset(float yaw) {
		vx = vy = vz = 0;
		heading = yaw;
		switchStance = false;
		edge = 0;
		lean = leanO = 0;
		grounded = true;
		groundTicks = airTicks = 0;
		jumpCharge = 0;
		jumpHeld = false;
		spinRate = flipRate = 0;
		flipVisual = flipVisualO = 0;
		skid = 0;
		initialized = true;
		grinding = buttering = false;
		grindContact = null;
		grindCooldown = press = 0;
	}

	/**
	 * A surface lift (T-bar, platter) pulling the rider: where its carrier wants the rider this tick (x, z), how fast
	 * the carrier moves, and which way is uphill along the track.
	 */
	public record Tow(double x, double z, double vx, double vz, float yaw) {
	}

	/** Set by the rig each tick while a surface lift has hold of the rider; null otherwise. */
	public @Nullable Tow tow;

	/** One physics tick. Returns the new position; the caller moves the entity there. */
	public Vec3 step(BlockGetter level, Vec3 pos, RiderInput in, RigStats st, RigKind kind) {
		events.clear();
		leanO = lean;
		flipVisualO = flipVisual;
		steerYaw = 0;
		if (grindCooldown > 0) grindCooldown--;
		Vec3 next;
		if (tow != null) {
			grinding = buttering = false;
			press = 0;
			next = stepTowed(level, pos, tow, kind);
		} else if (grinding) {
			next = stepGrind(level, pos, in, st, kind);
		} else if (grounded) {
			ParkContact contact = grindCooldown == 0
				? ParkContact.find(level, pos.x, pos.z, pos.y - 0.22, pos.y + 0.22, 0.18) : null;
			if (contact != null && speed() > 0.035) {
				if (buttering) finishButter();
				beginGrind(contact, kind);
				next = stepGrind(level, new Vec3(pos.x, contact.height(), pos.z), in, st, kind);
			} else {
				SurfaceSampler.Ground snow = in.grab() && !in.brake() && speed() > 0.035
					? SurfaceSampler.ground(level, pos.x, pos.z, pos.y) : null;
				if (snow != null && snow.surface().snowy) {
					next = stepButter(level, pos, snow, in, st, kind);
				} else {
					if (buttering) finishButter();
					press = 0;
					tricks.releaseGrab();
					next = stepGround(level, pos, in, st, kind);
				}
			}
		} else {
			next = stepAir(level, pos, in, st, kind);
		}
		double sp = speed();
		if (sp > MAX_SPEED) {
			double k = MAX_SPEED / sp;
			vx *= k;
			vz *= k;
		}
		return next;
	}

	// ----------------------------------------------------------------------------------------- ground

	/** Sweep the boots, not just the end of a tick, so fast riders cannot skip a narrow feature. */
	private @Nullable Vec3 tryGrind(BlockGetter level, Vec3 from, Vec3 to, boolean airborne, RigKind kind) {
		if (grindCooldown > 0 || speed() < 0.035 || airborne && vy > 0) return null;
		if (airborne && (Math.abs(Mth.wrapDegrees(flipVisual)) > 65 || vy < -1.25)) return null;
		int steps = Math.max(1, (int) Math.ceil(to.subtract(from).length() / 0.18));
		for (int i = 1; i <= steps; i++) {
			double t = (double) i / steps;
			double x = Mth.lerp(t, from.x, to.x), z = Mth.lerp(t, from.z, to.z);
			double y = Mth.lerp(t, from.y, to.y);
			ParkContact contact = ParkContact.find(level, x, z,
				airborne ? y - 0.04 : y - 0.3, airborne ? from.y + 0.08 : y + 0.9, 0.18);
			if (contact == null) continue;
			double along = contact.axis() == net.minecraft.core.Direction.Axis.X ? vx : vz;
			// A crossing at right angles is not a rail approach. The board can face sideways; its travel cannot.
			if (Math.abs(along) < speed() * 0.45) continue;
			if (!bodyClear(level, x, contact.height(), z)) continue;
			if (buttering) finishButter();
			beginGrind(contact, kind);
			return new Vec3(x, contact.height(), z);
		}
		return null;
	}

	private void beginGrind(ParkContact contact, RigKind kind) {
		grindContact = contact;
		grinding = grounded = true;
		buttering = false;
		grindTicks = grindSideTicks = grindPressTicks = 0;
		grindDistance = 0;
		grindSpin = 0;
		double along = contact.axis() == net.minecraft.core.Direction.Axis.X ? vx : vz;
		grindDirection = along < 0 ? -1 : 1;
		double glide = speed() * 0.98;
		vx = contact.axis() == net.minecraft.core.Direction.Axis.X ? glide * grindDirection : 0;
		vz = contact.axis() == net.minecraft.core.Direction.Axis.Z ? glide * grindDirection : 0;
		vy = powderSink = 0;
		surface = Surface.PARK_SLIDE;
		edge = skid = lean = 0;
		tuck = plow = hockeyStop = edgeCheck = false;
		press = 0;
		resetRotations();
	}

	private Vec3 stepGrind(BlockGetter level, Vec3 pos, RiderInput in, RigStats st, RigKind kind) {
		ParkContact contact = ParkContact.find(level, pos.x, pos.z, pos.y - 0.12, pos.y + 0.12, 0.22);
		if (contact == null || grindContact == null || !grindContact.continues(contact)) {
			finishGrind(kind, true);
			takeoff(pos.y, false);
			return stepAir(level, pos, in, st, kind);
		}
		grindContact = contact;
		grounded = true;
		airTicks = 0;
		groundTicks++;
		grindTicks++;
		tricks.tickGround();
		surface = Surface.PARK_SLIDE;
		edge = skid = 0;
		lean *= 0.6f;
		tuck = plow = hockeyStop = edgeCheck = false;
		press = in.grab() ? in.pitch() : 0;
		tricks.grab = press > 0 ? Grab.FORWARD : press < 0 ? Grab.BACK : Grab.NONE;
		if (press != 0) grindPressTicks++;
		float turn = in.steer() * 6f * st.spin();
		heading = Mth.wrapDegrees(heading + turn);
		steerYaw = turn;
		grindSpin += turn;
		float crossAngle = Math.abs(Mth.wrapDegrees(heading - yawOf(vx, vz))) % 180f;
		if (crossAngle > 45 && crossAngle < 135) grindSideTicks++;
		double glide = speed();
		glide = Math.max(0, glide - 0.0003 - 0.0015 * glide * glide);
		if (in.back() && !in.grab() || in.brake()) glide = Math.max(0, glide - 0.025 - glide * 0.025);
		if (in.forward() && !in.grab() && glide < PUSH_MAX) glide += (kind.isBoard() ? PUSH_BOARD : PUSH_SKI) * st.push();
		vx = contact.axis() == net.minecraft.core.Direction.Axis.X ? glide * grindDirection : 0;
		vz = contact.axis() == net.minecraft.core.Direction.Axis.Z ? glide * grindDirection : 0;
		vy = 0;
		double pop = loadPop(in, st);
		if (pop > 0) {
			finishGrind(kind, false);
			vy = pop;
			takeoff(pos.y, true);
			events.popped = true;
			return pos;
		}
		// Gently center the boots. The original movement direction and speed are retained along the deck.
		double nx = pos.x + vx, nz = pos.z + vz;
		if (contact.axis() == net.minecraft.core.Direction.Axis.X) nz += Mth.clamp(contact.crossCenter() - nz, -0.12, 0.12);
		else nx += Mth.clamp(contact.crossCenter() - nx, -0.12, 0.12);
		int steps = Math.max(1, (int) Math.ceil(glide / 0.18));
		double lastX = pos.x, lastZ = pos.z;
		for (int i = 1; i <= steps; i++) {
			double t = (double) i / steps;
			double sx = Mth.lerp(t, pos.x, nx), sz = Mth.lerp(t, pos.z, nz);
			if (!bodyClear(level, sx, pos.y, sz)) {
				events.wallBonk = true;
				finishGrind(kind, true);
				vx = vz = 0;
				return new Vec3(lastX, pos.y, lastZ);
			}
			ParkContact next = ParkContact.find(level, sx, sz, pos.y - 0.12, pos.y + 0.12, 0.22);
			if (next == null || !contact.continues(next)) {
				grindDistance += Math.hypot(lastX - pos.x, lastZ - pos.z);
				finishGrind(kind, true);
				takeoff(pos.y, false);
				// Stop this tick at the first unsupported point; the next tick handles the landing.
				return new Vec3(sx, pos.y, sz);
			}
			lastX = sx; lastZ = sz;
		}
		grindDistance += glide;
		return new Vec3(nx, contact.height(), nz);
	}

	private void finishGrind(RigKind kind, boolean straighten) {
		if (grinding && grindTicks >= 3 && grindDistance >= 1.0) {
			String name = grindPressTicks > 4 ? "Press Slide" : grindSideTicks > 3
				? kind.isBoard() ? "Boardslide" : "Rail Slide" : "50-50";
			if (grindContact != null) name += grindContact.box() ? " Box" : " Grind";
			events.trick = tricks.groundTrick(name, grindDistance, grindSpin, grindTicks);
			events.groundTrick = true;
		}
		grinding = false;
		grindContact = null;
		grindCooldown = 5;
		press = 0;
		tricks.releaseGrab();
		if (straighten) straightenStance();
	}

	private void straightenStance() {
		if (speed() < 0.001) return;
		float direction = yawOf(vx, vz);
		switchStance = Math.abs(Mth.wrapDegrees(heading - direction)) > 90;
		float target = Mth.wrapDegrees(direction + (switchStance ? 180 : 0));
		steerYaw += Mth.wrapDegrees(target - heading);
		heading = target;
	}

	private double loadPop(RiderInput in, RigStats st) {
		if (in.jump()) {
			jumpHeld = true;
			jumpCharge = Math.min(1f, jumpCharge + 1f / 12f);
			// hold A/D while loading to wind up a spin: you take off already rotating
			windUp = approach(windUp, in.steer(), 0.12f);
		} else if (jumpHeld) {
			jumpHeld = false;
			double pop = (POP_BASE + POP_CHARGE * jumpCharge) * st.pop();
			jumpCharge = 0;
			return pop;
		}
		return 0;
	}

	/** Pressed skis or a snowboard pivot over the snow while momentum keeps moving down the hill. */
	private Vec3 stepButter(BlockGetter level, Vec3 pos, SurfaceSampler.Ground ground, RiderInput in, RigStats st, RigKind kind) {
		if (!buttering) { butterTicks = 0; butterDistance = 0; butterSpin = 0; butterPress = 0; }
		buttering = true;
		butterTicks++;
		groundTicks++;
		airTicks = 0;
		tricks.tickGround();
		surface = SnowConditions.adjust(ground.surface());
		press = in.pitch() == 0 ? (butterPress == 0 ? -1 : butterPress) : in.pitch();
		butterPress = press;
		tricks.grab = press > 0 ? Grab.FORWARD : Grab.BACK;
		tuck = plow = hockeyStop = edgeCheck = false;
		edge = skid = 0;
		lean += (in.steer() * 8f - lean) * 0.25f;
		powderSink *= 0.8;
		float turn = in.steer() * 5.5f * st.spin();
		heading = Mth.wrapDegrees(heading + turn);
		steerYaw = turn;
		butterSpin += turn;
		double gx = ground.gradX(), gz = ground.gradZ(), denom = 1 + gx * gx + gz * gz;
		vx -= G_SLOPE * gx / denom;
		vz -= G_SLOPE * gz / denom;
		decelerate(0.001 + DRAG * 0.65 * speed() * speed());
		double pop = loadPop(in, st);
		Vec3 entry = pop == 0 ? tryGrind(level, pos, new Vec3(pos.x + vx, pos.y, pos.z + vz), false, kind) : null;
		if (entry != null) return entry;
		slopeGx = gx; slopeGz = gz;
		double[] moved = moveHorizontal(level, pos.x, pos.y, pos.z, true);
		double nx = moved[0], nz = moved[1];
		SurfaceSampler.Ground next = SurfaceSampler.ground(level, nx, nz, pos.y);
		double slopeVy = gx * (nx - pos.x) + gz * (nz - pos.z);
		double floor = next.height() - powderSink;
		butterDistance += Math.hypot(nx - pos.x, nz - pos.z);
		if (floor > pos.y + Math.max(0, slopeVy) + SurfaceSampler.MAX_STEP + 0.2) {
			vx = vz = 0;
			events.wallBonk = true;
			finishButter();
			return pos;
		}
		if (events.crashed) {
			buttering = false; press = 0; tricks.releaseGrab();
			return new Vec3(nx, pos.y, nz);
		}
		if (pop > 0 || !next.found() || pos.y + Math.max(vy, slopeVy) - G_AIR - floor > STICK + STICK_SPEED * speed()) {
			finishButter();
			vy = pop > 0 ? Math.max(0, slopeVy) * 0.8 + pop : Math.max(vy, slopeVy);
			takeoff(pos.y, pop > 0);
			events.popped = pop > 0;
			return new Vec3(nx, pos.y, nz);
		}
		vy = floor - pos.y;
		return new Vec3(nx, floor, nz);
	}

	private void finishButter() {
		if (buttering && butterTicks >= 12 && butterDistance >= 1.5) {
			events.trick = tricks.groundTrick(butterPress > 0 ? "Nose Butter" : "Tail Butter", butterDistance, butterSpin, butterTicks);
			events.groundTrick = true;
		}
		buttering = false;
		press = 0;
		tricks.releaseGrab();
		straightenStance();
	}

	private Vec3 stepGround(BlockGetter level, Vec3 pos, RiderInput in, RigStats st, RigKind kind) {
		double x = pos.x;
		double y = pos.y;
		double z = pos.z;
		airTicks = 0;
		groundTicks++;
		tricks.tickGround();
		SurfaceSampler.Ground ground = SurfaceSampler.ground(level, x, z, y);
		surface = SnowConditions.adjust(ground.surface());
		boolean board = kind.isBoard();
		double speed = speed();

		tuck = in.forward() && speed > 0.22 && !in.back();
		// Back on its own: snowplough (skis) or a heel-edge slide (board). Back while steering: an edge check, the
		// way real skiers scrub speed: swing the skis across the hill on that side and dig the edges in.
		edgeCheck = in.back() && in.steer() != 0 && !in.brake() && speed > 0.06;
		plow = in.back() && !board && !in.brake() && in.steer() == 0;
		boolean boardBrake = board && in.back() && !in.brake() && in.steer() == 0;
		hockeyStop = in.brake() && speed > 0.08;

		// Powder: you sink in when you're slow and plane up on top once you get going.
		powderPlane = surface.isPowder() ? smoothstep(0.02, 0.20 / Math.max(0.7, st.flotation()), speed) : 1.0;
		double sinkTarget = surface.isPowder() ? surface.sink * (1.0 - powderPlane) / Math.max(0.7, st.flotation()) : 0.0;
		powderSink += (sinkTarget - powderSink) * 0.15;

		// Edge: release quickly, engage gradually (that's the bite you feel build up).
		float target = in.steer();
		if (hockeyStop) {
			target = 0;
		}
		float engage = edgeCheck ? 0.35f : board ? BOARD_ENGAGE : SKI_ENGAGE;
		if (target == 0 || (edge != 0 && Math.signum(target) != Math.signum(edge))) {
			edge = approach(edge, 0, RELEASE);
			if (target != 0 && edge == 0) {
				edge = approach(edge, target, engage);
			}
		} else {
			edge = approach(edge, target, engage);
		}

		// Gravity pulls along the slope.
		double gx = ground.gradX();
		double gz = ground.gradZ();
		double denom = 1.0 + gx * gx + gz * gz;
		vx += -G_SLOPE * gx / denom;
		vz += -G_SLOPE * gz / denom;
		// Wind leans on you a little, even on the ground.
		vx += SnowConditions.windX() * WIND_GROUND;
		vz += SnowConditions.windZ() * WIND_GROUND;

		// On a conveyor everything below happens relative to the belt.
		Vec3 carry = ground.carry();
		boolean onBelt = carry.lengthSqr() > 0;
		if (onBelt) {
			vx -= carry.x;
			vz -= carry.z;
		}
		speed = speed();

		// Steering: carve within grip, pivot when slow.
		double absEdge = Math.abs(edge);
		double steerSpeed = Math.max(speed, 0.08);
		double latMax = lateralGrip(absEdge, st);
		double omegaGeom = Math.pow(absEdge, 1.2) * steerSpeed / (R_FULL / st.turn());
		double omegaGrip = latMax / steerSpeed;
		// At speed, keyboard edging widens the carve to the available grip instead of instantly turning every
		// held A/D into a sideways skid. Deliberate edge checks and hockey stops still break traction below.
		double omegaCarve = Math.min(omegaGeom, omegaGrip);
		double oversteer = Math.min(Math.max(0.0, omegaGeom - omegaCarve), omegaGrip * OVERSTEER);
		double omegaDemand = (omegaCarve + oversteer) * (tuck ? 0.82 : 1.0);
		float pivot = (float) (PIVOT * (1.0 - smoothstep(0.03, 0.40, speed)));
		float steerDeg = (float) (Math.signum(edge) * Math.toDegrees(omegaDemand)) + in.steer() * pivot;
		float twist = 0;
		float travel = travelHeading();
		float velYaw = speed > 1.0e-4 ? yawOf(vx, vz) : travel;
		if (edgeCheck) {
			// The skis swing across the direction of travel on the side you're steering; the edges bite and skid.
			float wanted = velYaw + in.steer() * EDGE_CHECK_ANGLE;
			twist = Mth.clamp(Mth.wrapDegrees(wanted - travel), -EDGE_CHECK_RATE, EDGE_CHECK_RATE);
			steerDeg = 0;
		} else if (hockeyStop) {
			float side = in.steer() != 0 ? in.steer() : (Mth.wrapDegrees(travel - velYaw) >= 0 ? 1 : -1);
			float wanted = velYaw + side * 80f;
			twist = Mth.clamp(Mth.wrapDegrees(wanted - travel), -HOCKEY_RATE, HOCKEY_RATE);
		} else if (boardBrake && speed > 0.08) {
			float wanted = velYaw + (in.steer() != 0 ? in.steer() : 1) * 60f;
			twist = Mth.clamp(Mth.wrapDegrees(wanted - travel), -7f, 7f);
		}
		float turned = Mth.wrapDegrees(heading + steerDeg + twist);
		boolean blocked = speed < 0.1
			? !skisRestClear(level, x, y, z, turned, gx, gz) && skisRestClear(level, x, y, z, heading, gx, gz)
			: speed < 0.3 && !tipsClear(level, x, y, z, turned) && tipsClear(level, x, y, z, heading);
		if (blocked) {
			// a tip or tail would swing into a wall: it stops against it, like a real ski
			turned = heading;
			steerDeg = 0;
		}
		heading = turned;
		steerYaw = steerDeg;
		travel = travelHeading();

		// Grip: swing the velocity round toward where the skis point. Whatever the edge can't hold skids.
		skid = 0;
		if (speed > 1.0e-4) {
			velYaw = yawOf(vx, vz);
			float slip = Mth.wrapDegrees(travel - velYaw);
			if (Math.abs(slip) > 100f && speed < 0.12 && speed > 0.02 && !hockeyStop && !in.forward()) {
				// Sliding backwards slowly: you're riding switch now. (A puff of wind on the flats doesn't count, and
				// neither does the first moment of pushing off.)
				switchStance = !switchStance;
				travel = travelHeading();
				slip = Mth.wrapDegrees(travel - velYaw);
			}
			double capDeg = Math.toDegrees(omegaGrip * 1.05) + (speed < 0.1 ? 25.0 : 0.0);
			if (edgeCheck) {
				// an edge check skids on purpose: the skis stay across the hill instead of carving round with you
				capDeg = Math.toDegrees(omegaGrip * 0.35);
			}
			float follow = (float) Mth.clamp(slip, -capDeg, capDeg);
			rotateVelocity(follow);
			float rem = slip - follow;
			double sinRem = Math.abs(Math.sin(Math.toRadians(rem)));
			skid = (float) Mth.clamp(sinRem * 1.4, 0.0, 1.0);
			double scrub = sinRem * (SKID_DECEL + SKID_DECEL_V * speed) * (2.0 - surface.grip * 0.8);
			if (surface.isPowder()) {
				scrub *= 0.30; // pressure steers a planing ski through soft snow without acting like a hardpack skid
			}
			if (edgeCheck) {
				scrub *= 0.75; // a strong but progressive stop, not a wall
			}
			rotateVelocity(rem * (edgeCheck ? 0.05f : 0.18f));
			decelerate(scrub);
		}

		// Friction and drag.
		speed = speed();
		double friction = surface.friction;
		double surfaceDrag = surface.drag;
		if (surface.isPowder()) {
			// bogged down when slow, floating and gliding once you're planing
			friction *= 0.85 - 0.60 * powderPlane;
			surfaceDrag = 1.0 + (surface.drag - 1.0) * (1.0 - 0.95 * powderPlane);
		}
		// Tucking buys straight-line speed; rolling onto an edge progressively opens the stance.
		double tuckBlend = tuck ? 1.0 - absEdge * 0.65 : 0.0;
		double dragCoeff = (DRAG + (TUCK_DRAG - DRAG) * tuckBlend) * (1.0 + (surfaceDrag - 1.0) / st.flotation()) / st.speed();
		// A held clean edge preserves glide. Gravity still accelerates down the fall line, while deliberate
		// braking and transverse slides keep their normal losses. This is based on traction, not a speed boost key.
		double cleanCarve = !edgeCheck && !plow && !boardBrake && !hockeyStop && surface.snowy
			? absEdge * smoothstep(0.12, 0.45, speed) * (1.0 - Mth.clamp(skid * 3, 0, 1)) : 0;
		friction *= 1.0 - cleanCarve * 0.55;
		dragCoeff *= 1.0 - cleanCarve * (surface.isPowder() ? 0.38 : 0.32);
		if (plow) {
			friction += 0.006 + 0.020 * speed;
			skid = Math.max(skid, 0.45f);
		}
		if (boardBrake) {
			friction += 0.005 + 0.018 * speed;
			skid = Math.max(skid, 0.5f);
		}
		if (in.brake() && !hockeyStop) {
			friction += 0.02;
		}
		if (edgeCheck) {
			friction += 0.002 + 0.008 * speed;
			skid = Math.max(skid, 0.6f);
		}
		decelerate(friction + dragCoeff * speed * speed);
		// A small release of pressure out of a clean downhill carve. No free acceleration uphill or on flat snow.
		if (!edgeCheck && !plow && !boardBrake && !hockeyStop && Math.abs(edge) > 0.5f && skid < 0.2f && speed > 0.08 && speed < 1.4) {
			double rad = Math.toRadians(travelHeading());
			double downhill = Math.max(0, gx * Math.sin(rad) - gz * Math.cos(rad));
			double drive = (surface.isPowder() ? 0.011 : 0.009) * Math.min(1, downhill * 4) * cleanCarve * (1.0 - speed / 1.5) * st.turn();
			vx += -Math.sin(rad) * drive;
			vz += Math.cos(rad) * drive;
		}

		// Pushing: hold W for rhythmic strokes. With poles in hand it's a strong double-pole plant every half second
		// (good up to ~40 km/h); without them a skate stride; on a board a one-footed skate. Each stroke is a real shove
		// that fades as you get going, so it's punchy from a standstill and still useful across a flat.
		speed = speed();
		if (strokeTimer > 0) {
			strokeTimer--;
		}
		boolean poles = !board && st.push() > 1.2f;
		double strokeCap = board ? 0.30 : poles ? 0.55 : 0.38;
		if (in.forward() && !in.back() && !in.jump() && speed < strokeCap) {
			if (switchStance && speed < 0.04) {
				// pushing off from (nearly) a standstill goes the way the skis point
				switchStance = false;
				travel = travelHeading();
			}
			if (strokeTimer == 0) {
				double base = board ? 0.034 : poles ? 0.085 : 0.046;
				double impulse = base * Math.min(1.3, st.push() / (poles ? 1.45 : 1.0)) * (0.35 + 0.65 * (1 - speed / strokeCap));
				double rad = Math.toRadians(travel);
				vx += -Math.sin(rad) * impulse;
				vz += Math.cos(rad) * impulse;
				strokeTimer = poles ? 11 : 9;
				events.stroke = poles ? 2 : 1;
			}
		} else if (!in.forward()) {
			strokeTimer = Math.min(strokeTimer, 3); // a fresh press strokes almost at once
		}
		if (speed() < 0.003 && ground.slope() < 0.08 && !in.forward()) {
			vx = 0;
			vz = 0;
		}
		if (onBelt) {
			vx = vx * 0.6 + carry.x;
			vz = vz * 0.6 + carry.z;
		}
		if (surface == Surface.WATER && speed() < 0.30) {
			events.sank = true;
		}

		// Ollie: hold jump to load up (A/D at the same time winds up a spin), release to pop.
		double pop = loadPop(in, st);

		// Move sideways, stopping at walls and trees.
		Vec3 deckEntry = pop == 0 ? tryGrind(level, pos, new Vec3(x + vx, y, z + vz), false, kind) : null;
		if (deckEntry != null) return deckEntry;
		slopeGx = gx;
		slopeGz = gz;
		double[] moved = moveHorizontal(level, x, y, z, true);
		double nx = moved[0];
		double nz = moved[1];

		// Follow the ground, or leave it when it falls away faster than momentum can follow: a kicker lip, a
		// knuckle at speed, a ledge. A steady pitch, however steep, keeps you on the snow (the last tick's vertical
		// speed already matches it), so you can carve and hop-turn down it.
		SurfaceSampler.Ground next = SurfaceSampler.ground(level, nx, nz, y);
		double floor = next.height() - powderSink;
		double slopeVy = gx * (nx - x) + gz * (nz - z);
		if (floor > y + Math.max(0.0, slopeVy) + SurfaceSampler.MAX_STEP + 0.2) {
			// That's a wall, not a step: never ride up into it (even when backing out of a tight spot).
			nx = x;
			nz = z;
			vx *= -0.1;
			vz *= -0.1;
			events.wallBonk = events.wallBonk || speed > 0.08;
			next = SurfaceSampler.ground(level, x, z, y);
			floor = next.height() - powderSink;
			slopeVy = 0;
		}
		double expectedY = y + slopeVy;
		double ballisticY = y + Math.max(vy, slopeVy) - G_AIR;
		double fall = ballisticY - floor;
		double ny;
		if (pop > 0) {
			ny = Math.max(y, floor);
			// popped right as the snow drops away (a kicker lip, a ledge, the crest of a roller): extra height
			if (speed > 0.15 && fall > 0.12) {
				pop *= 1.35;
				events.perfectPop = true;
			}
			vy = Math.max(slopeVy, 0) * 0.8 + pop;
			takeoff(ny, true);
			events.popped = true;
			if (Math.abs(windUp) > 0.3f) {
				spinRate = windUp * SPIN_MAX * st.spin() * (kind.isBoard() ? 1.08f : 1f) * 0.85f;
			}
			windUp = 0;
		} else if (!next.found() || (speed > 0.12 && fall > STICK + STICK_SPEED * speed)) {
			ny = expectedY;
			vy = Math.max(vy, slopeVy);
			// Leaving the snow without jumping (a lip, a roll, a cliff) is never a trick by itself: like Steep, you jump
			// at the lip, or tap jump in the air, to throw something. Otherwise the keys just steer the landing.
			takeoff(ny, false);
		} else {
			ny = floor;
			vy = ny - y;
		}
		if (edgeCheck) {
			lean += (in.steer() * 38f - lean) * 0.3f; // hips into the hill, edges dug in
		} else {
			updateLean(st, omegaCarve * Math.signum(edge), speed);
		}
		countPowderTurn(level, nx, ny, nz, speed);
		return new Vec3(nx, ny, nz);
	}

	/**
	 * Linked turns in powder score: each time you roll from one edge to the other after a real turn, at speed, that's
	 * a powder turn. Untracked fresh powder under you counts as first tracks.
	 */
	private void countPowderTurn(BlockGetter level, double x, double y, double z, double speed) {
		int side = edge > 0.45f ? 1 : edge < -0.45f ? -1 : 0;
		if (side == 0 || side == turnEdge) {
			return;
		}
		float turned = Math.abs(Mth.wrapDegrees(travelHeading() - turnStartHeading));
		if (turnEdge != 0 && surface.isPowder() && speed > 0.25 && turned >= 14f && events.trick == null) {
			boolean fresh = false;
			BlockPos.MutableBlockPos under = BlockPos.containing(x, y - 0.05, z).mutable();
			for (int k = 0; k < 3 && !fresh; k++, under.move(0, -1, 0)) {
				fresh = level.getBlockState(under).getBlock() instanceof com.freshtracks.weather.FreshPowderBlock;
			}
			events.trick = tricks.powderTurn(fresh);
		}
		turnEdge = side;
		turnStartHeading = travelHeading();
	}

	/**
	 * Riding a T-bar or platter: the bar does the work. The rider's skis run in the track behind the carrier, flat
	 * and pointing uphill; the snow under them sets the height. Hooking on is a tug, not a teleport.
	 */
	private Vec3 stepTowed(BlockGetter level, Vec3 pos, Tow tow, RigKind kind) {
		grounded = true;
		intendedAir = false;
		airTicks = 0;
		groundTicks++;
		tricks.tickGround();
		resetRotations();
		tuck = false;
		plow = false;
		hockeyStop = false;
		jumpHeld = false;
		jumpCharge = 0;
		skid = 0;
		edge = approach(edge, 0, RELEASE);
		lean *= 0.8f;
		if (switchStance) {
			switchStance = false;
			heading = Mth.wrapDegrees(heading + 180f);
		}
		// Snowboarders ride a tow with the board pointing up the track too (front foot first), close enough here.
		float turn = Mth.clamp(Mth.wrapDegrees(tow.yaw() - heading), -9f, 9f);
		heading = Mth.wrapDegrees(heading + turn);

		double ex = tow.x() - pos.x;
		double ez = tow.z() - pos.z;
		double tvx = tow.vx() + ex * 0.25;
		double tvz = tow.vz() + ez * 0.25;
		double cap = Math.hypot(tow.vx(), tow.vz()) + 0.12;
		double want = Math.hypot(tvx, tvz);
		if (want > cap) {
			tvx *= cap / want;
			tvz *= cap / want;
		}
		vx += (tvx - vx) * 0.5;
		vz += (tvz - vz) * 0.5;
		double nx = pos.x + vx;
		double nz = pos.z + vz;
		SurfaceSampler.Ground ground = SurfaceSampler.ground(level, nx, nz, pos.y + 0.6);
		surface = SnowConditions.adjust(ground.surface());
		double ny = ground.found() ? ground.height() : pos.y;
		vy = ny - pos.y;
		slopeGx = ground.gradX();
		slopeGz = ground.gradZ();
		return new Vec3(nx, ny, nz);
	}

	private void takeoff(double y, boolean onPurpose) {
		intendedAir = onPurpose;
		grounded = false;
		groundTicks = 0;
		airTicks = 0;
		spinRate = 0;
		flipRate = 0;
		tricks.takeoff(switchStance, y);
		events.launched = true;
	}

	/** Sideways grip the snow can give, b/t^2. */
	private double lateralGrip(double absEdge, RigStats st) {
		// once you're planing, powder holds a big surfy turn
		double grip = surface.grip + (surface.isPowder() ? 0.32 * powderPlane : 0.0);
		return (LAT_FLAT + LAT_EDGE * absEdge) * grip * st.grip();
	}

	private void updateLean(RigStats st, double omegaSigned, double speed) {
		// Lean so the rider balances the turn: tan(lean) = lateral accel / g (visual g a bit higher).
		double lateral = omegaSigned * speed;
		double target = Math.toDegrees(Math.atan2(lateral, 0.034));
		target = Mth.clamp(target, -48.0, 48.0);
		if (plow || hockeyStop) {
			target *= 0.4;
		}
		lean += (float) ((target - lean) * 0.3);
	}

	// ----------------------------------------------------------------------------------------- air

	private Vec3 stepAir(BlockGetter level, Vec3 pos, RiderInput in, RigStats st, RigKind kind) {
		float headingBefore = heading;
		double x = pos.x;
		double y = pos.y;
		double z = pos.z;
		airTicks++;
		vy = Math.max(vy - G_AIR, -2.5);
		double speed = speed();
		double airDrag = AIR_DRAG * speed;
		vx *= 1.0 - airDrag;
		vz *= 1.0 - airDrag;
		// Big days in the air drift with the wind.
		vx += SnowConditions.windX() * WIND_AIR;
		vz += SnowConditions.windZ() * WIND_AIR;
		edge = approach(edge, 0, 0.1f);
		lean *= 0.85f;
		jumpCharge = 0;
		tuck = in.forward() && !in.grab();

		// Tricks need intent, like Steep: an ollie or a kicker launch, or tapping jump in the air. Riding off a ledge
		// without that, the keys just steer you toward a good landing and nothing flips you over.
		if (!intendedAir && in.jump() && airTicks > 2) {
			intendedAir = true;
		}
		// Rotations. Give a couple of ticks of grace so a pop doesn't accidentally start a flip.
		float spinMax = SPIN_MAX * st.spin() * (kind.isBoard() ? 1.08f : 1f);
		float flipMax = FLIP_MAX * st.spin();
		if (airTicks > 2 && intendedAir) {
			int steer = in.steer();
			spinRate = steer != 0 ? approach(spinRate, steer * spinMax, SPIN_ACCEL) : approach(spinRate, 0, SPIN_DECAY);
			int pitch = in.pitch();
			if (airTicks > 4 && pitch != 0) {
				flipRate = approach(flipRate, pitch * flipMax, FLIP_ACCEL);
			} else {
				flipRate = approach(flipRate, 0, FLIP_DECAY);
			}
		} else if (airTicks > 2) {
			spinRate = approach(spinRate, 0, SPIN_DECAY);
			flipRate = approach(flipRate, 0, FLIP_DECAY);
			if (in.steer() != 0) {
				float velYaw = speed > 0.05 ? yawOf(vx, vz) : travelHeading();
				float offset = Mth.wrapDegrees(travelHeading() - velYaw);
				float turn = in.steer() * AIR_STEER;
				if (Math.abs(offset + turn) <= AIR_STEER_LIMIT || Math.signum(turn) != Math.signum(offset)) {
					heading = Mth.wrapDegrees(heading + turn);
				}
			}
		}
		if (in.grab()) {
			tricks.startGrab(tricks.grab == Grab.NONE ? Grab.fromInput(in) : tricks.grab);
		} else {
			tricks.releaseGrab();
		}

		// Landing assist: near the ground, ease the rotation to the nearest landable angle.
		SurfaceSampler.Ground below = SurfaceSampler.ground(level, x, z, y);
		double ticksToGround = ticksToFall(y - below.height(), vy);
		if (intendedAir && vy < 0 && ticksToGround < 7) {
			if (in.steer() == 0 && Math.abs(tricks.spin) > 120f) {
				float nearest = Math.round(tricks.spin / 180f) * 180f;
				float err = nearest - tricks.spin;
				if (Math.abs(err) < 60f) {
					spinRate = Mth.clamp(err / (float) Math.max(ticksToGround, 1.0), -spinMax, spinMax);
				}
			}
			if (in.pitch() == 0) {
				float nearest = Math.round(tricks.flip / 360f) * 360f;
				float err = nearest - tricks.flip;
				if (Math.abs(err) < 75f) {
					flipRate = Mth.clamp(err / (float) Math.max(ticksToGround, 1.0), -flipMax, flipMax);
				}
			}
		}
		heading = Mth.wrapDegrees(heading + spinRate);
		// Includes controlled air steering and landing assistance; wrapping must not turn a 359/0 crossing into a full spin.
		steerYaw = Mth.wrapDegrees(heading - headingBefore);
		tricks.spin += spinRate;
		tricks.flip += flipRate;
		flipVisual = tricks.flip;
		tricks.tickAir(y);

		Vec3 deckLanding = tryGrind(level, pos, new Vec3(x + vx, y + vy, z + vz), true, kind);
		if (deckLanding != null) return deckLanding;
		double[] moved = moveHorizontal(level, x, y, z, false);
		double nx = moved[0];
		double nz = moved[1];
		double ny = y + vy;
		if (events.crashed) {
			return new Vec3(nx, ny, nz);
		}

		// Bonk your head on a ceiling.
		if (vy > 0 && !bodyClear(level, nx, ny + 0.1, nz)) {
			vy = 0;
			ny = y;
		}

		SurfaceSampler.Ground ground = SurfaceSampler.ground(level, nx, nz, Math.max(y, ny));
		if (ground.found() && ny <= ground.height() + 0.02 && vy <= 0) {
			ny = ground.height();
			land(ground, kind);
		}
		return new Vec3(nx, ny, nz);
	}

	private static double ticksToFall(double height, double vy) {
		if (height <= 0) {
			return 0;
		}
		// height + vy t - g/2 t^2 = 0
		double a = G_AIR / 2.0;
		double disc = vy * vy + 4 * a * height;
		return (vy + Math.sqrt(disc)) / (2 * a);
	}

	private void land(SurfaceSampler.Ground ground, RigKind kind) {
		grounded = true;
		groundTicks = 0;
		events.landed = true;
		surface = SnowConditions.adjust(ground.surface());
		Vec3 n = ground.normal();
		double vn = vx * n.x + vy * n.y + vz * n.z;
		float impact = (float) Math.max(0, -vn);
		events.landingImpact = impact;
		// Keep the part of the velocity along the slope (a good landing on a downslope keeps all its speed).
		vx -= n.x * vn;
		vy -= n.y * vn;
		vz -= n.z * vn;

		float travel = travelHeading();
		double speed = speed();
		float slipAbs = speed > 0.05 ? Math.abs(Mth.wrapDegrees(travel - yawOf(vx, vz))) : 0f;
		boolean powder = ground.surface().isPowder();
		float spinAbs = Math.abs(tricks.spin);
		// A hop turn: a short, slow hop with the skis swung round (up to a half turn and a bit). The edges bite
		// where they land and you ride off the new way; no switch landing, no crash.
		boolean hopTurn = spinAbs >= 50f && spinAbs < 230f && speed < 0.45 && tricks.airTicks < 18 && Math.abs(tricks.flip) < 60f;
		if (hopTurn) {
			double rad = Math.toRadians(travel);
			double tx = -Math.sin(rad);
			double tz = Math.cos(rad);
			double along = vx * tx + vz * tz;
			double keepSpeed = along > 0 ? along * 0.85 : speed * 0.25;
			vx = tx * keepSpeed;
			vz = tz * keepSpeed;
			skid = 1f;
			resetRotations();
			return;
		}
		if (slipAbs > 110f) {
			// Came round to backwards: that's a switch landing.
			switchStance = !switchStance;
			slipAbs = 180f - slipAbs;
		}
		boolean trickAttempt = spinAbs >= 135f || Math.abs(tricks.flip) >= 90f;
		if (!trickAttempt) {
			// A hop turn or a touchdown off a bump: the skis bite where they point. Keep the speed along them,
			// scrub the sideways part (that's the spray). Only a big sideways slam at speed takes you out.
			float crashAt = (float) (LANDING_CRASH * (powder ? 1.5 : 1.0));
			if (impact > crashAt || (slipAbs > 95f && speed > 0.6)) {
				events.crashed = true;
				events.crashSeverity = Mth.clamp(impact / crashAt + (slipAbs > 95f ? 0.3f : 0f), 0.3f, 1.5f);
				events.crashReason = impact > crashAt ? "flat" : "sideways";
				tricks.bail();
				resetRotations();
				return;
			}
			double rad = Math.toRadians(travelHeading());
			double tx = -Math.sin(rad);
			double tz = Math.cos(rad);
			double along = vx * tx + vz * tz;
			double perpX = vx - tx * along;
			double perpZ = vz - tz * along;
			double keep = along >= 0 ? 0.94 : 0.5;
			vx = tx * along * keep + perpX * 0.25;
			vz = tz * along * keep + perpZ * 0.25;
			if (impact > 0.6f) {
				double loss = Math.min(0.4, (impact - 0.6f) * 0.6);
				vx *= 1.0 - loss;
				vz *= 1.0 - loss;
			}
			if (tricks.airTicks >= 12) {
				events.trick = tricks.land(kind, impact < 0.35f ? TrickTracker.Landing.STOMPED : TrickTracker.Landing.CLEAN);
			}
			resetRotations();
			return;
		}
		boolean rotationsOk = TrickTracker.landable(tricks.spin, tricks.flip, 55f, 70f);
		boolean sideways = slipAbs > 70f && speed > 0.28;
		boolean powderSaves = ground.surface().isPowder();
		float crashImpact = (float) (LANDING_CRASH * (powderSaves ? 1.5 : 1.0));
		if (!rotationsOk || sideways || impact > crashImpact) {
			events.crashed = true;
			events.crashSeverity = Mth.clamp(impact / crashImpact + (rotationsOk ? 0f : 0.4f), 0.3f, 1.5f);
			events.crashReason = !rotationsOk ? "under-rotated" : sideways ? "sideways" : "flat";
			tricks.bail();
			resetRotations();
			return;
		}

		// Hard landings cost speed.
		float soft = powderSaves ? 0.6f : 0.45f;
		if (impact > soft) {
			double loss = Math.min(0.55, (impact - soft) * 0.8);
			vx *= 1.0 - loss;
			vz *= 1.0 - loss;
		}
		TrickTracker.Landing quality;
		float spinRem = Math.abs(tricks.spin) % 180f;
		float spinErr = Math.min(spinRem, 180f - spinRem);
		if (impact < 0.35f && slipAbs < 14f && spinErr < 15f) {
			quality = TrickTracker.Landing.STOMPED;
		} else if (slipAbs > 38f || impact > 0.75f || spinErr > 35f) {
			quality = TrickTracker.Landing.SKETCHY;
		} else {
			quality = TrickTracker.Landing.CLEAN;
		}
		if (tricks.airTicks >= 6) {
			events.trick = tricks.land(kind, quality);
		}
		if (quality == TrickTracker.Landing.STOMPED && tricks.airTicks >= 10) {
			// a stomped landing rides away with a burst of speed: land clean, keep the run flowing
			double k = Math.min(1.12, (speed() + 0.06) / Math.max(0.05, speed()));
			vx *= k;
			vz *= k;
			events.boost = true;
		}
		// Straighten up: the noses follow the direction of travel.
		if (speed > 0.05) {
			float velYaw = yawOf(vx, vz);
			float target = switchStance ? velYaw + 180f : velYaw;
			heading = Mth.wrapDegrees(heading + Mth.wrapDegrees(target - heading) * 0.7f);
		}
		resetRotations();
	}

	private void resetRotations() {
		spinRate = 0;
		flipRate = 0;
		tricks.spin = 0;
		tricks.flip = 0;
		tricks.releaseGrab();
		flipVisual = 0;
		flipVisualO = 0;
	}

	// ----------------------------------------------------------------------------------------- collision

	/** The rider's body from the knees up: what hits trees and walls. */
	public static AABB bodyBox(double x, double y, double z) {
		return new AABB(x - 0.3, y + BODY_CLEARANCE, z - 0.3, x + 0.3, y + 1.75, z + 0.3);
	}

	/** Steps lower than this under the rider are ridden over (the smoothed surface ramps up them); taller is a wall. */
	public static final double BODY_CLEARANCE = 1.15;

	/** Moves by (vx, vz), sliding along walls. Fast head-on hits are crashes. */
	private double[] moveHorizontal(BlockGetter level, double x, double y, double z, boolean onGround) {
		double nx = x + vx;
		double nz = z + vz;
		// Already wedged in (a tip swung against a wall, landed half inside a block, snow piled up around you)? Then
		// that can't hold you: only moves that newly run into something are blocked, so you can always back out.
		boolean checkTips = onGround && tipsClear(level, x, y, z, heading);
		boolean checkFeet = onGround && feetClear(level, x, y, z);
		boolean checkBody = bodyClear(level, x, y, z);
		if (isFree(level, nx, y, nz, checkTips, checkFeet, checkBody)) {
			return new double[]{nx, nz};
		}
		double speedBefore = speed();
		boolean freeX = isFree(level, nx, y, z, checkTips, checkFeet, checkBody);
		boolean freeZ = isFree(level, x, y, nz, checkTips, checkFeet, checkBody);
		double impact;
		if (freeX && !freeZ) {
			impact = Math.abs(vz);
			vz *= -0.15;
			nz = z;
		} else if (freeZ && !freeX) {
			impact = Math.abs(vx);
			vx *= -0.15;
			nx = x;
		} else {
			impact = speedBefore;
			vx *= -0.1;
			vz *= -0.1;
			nx = x;
			nz = z;
		}
		if (impact > WALL_CRASH && impact > speedBefore * 0.55) {
			events.crashed = true;
			events.crashSeverity = (float) Mth.clamp(impact / 0.8, 0.4, 1.5);
			events.crashReason = "wall";
			tricks.bail();
		} else if (impact > 0.08) {
			events.wallBonk = true;
		}
		return new double[]{nx, nz};
	}

	/** Ground slope under the rider this tick, so walls are judged against the hill, not against the feet. */
	private double slopeGx;
	private double slopeGz;

	/** How far the ski tips and tails reach from the boots, blocks. */
	public static final double TIP_REACH = 0.9;

	/** How far a block may stand above the snow line before a resting ski tip would visibly poke into it. */
	private static final double TIP_TOUCH = 0.9;

	/**
	 * A heading near {@code wanted} where the skis' tips and tails sit on the snow without poking into anything at
	 * (x, y, z), trying the player's own facing first, then turning in 45 degree steps. The hill itself doesn't count
	 * as an obstacle (facing down a steep pitch, the snow behind your tails is meant to be higher). Null if there's no
	 * room at all.
	 */
	public static Float freeHeading(BlockGetter level, double x, double y, double z, float wanted) {
		SurfaceSampler.Ground ground = SurfaceSampler.ground(level, x, z, y);
		// Judge from the snow surface you'd stand on (snow layers sit above your feet), not from your feet.
		double base = Math.max(y, ground.height());
		float[] offsets = {0f, 45f, -45f, 90f, -90f, 135f, -135f, 180f};
		for (float off : offsets) {
			float yaw = wanted + off;
			if (skisRestClear(level, x, base, z, yaw, ground.gradX(), ground.gradZ())) {
				return Mth.wrapDegrees(yaw);
			}
		}
		return null;
	}

	/** True if skis resting at (x, y, z) pointing {@code yaw} don't poke into a block (judged against the slope). */
	private static boolean skisRestClear(BlockGetter level, double x, double y, double z, float yaw, double gradX, double gradZ) {
		double rad = Math.toRadians(yaw);
		double fx = -Math.sin(rad);
		double fz = Math.cos(rad);
		for (double reach : new double[]{TIP_REACH, -TIP_REACH, TIP_REACH * 0.5, -TIP_REACH * 0.5}) {
			double px = x + fx * reach;
			double pz = z + fz * reach;
			SurfaceSampler.Column column = SurfaceSampler.column(level, Mth.floor(px), Mth.floor(pz), y + 1.0);
			double rise = gradX * (px - x) + gradZ * (pz - z);
			if (column.found() && column.top() > y + Math.max(0.0, rise) + TIP_TOUCH) {
				return false;
			}
		}
		return true;
	}

	private boolean isFree(BlockGetter level, double x, double y, double z, boolean checkTips, boolean checkFeet, boolean checkBody) {
		return (!checkTips || tipsClear(level, x, y, z, heading)) && (!checkFeet || feetClear(level, x, y, z))
			&& (!checkBody || bodyClear(level, x, y, z));
	}

	/**
	 * The ski tips and tails: they stop at walls too (two blocks or more above the slope there), so they never poke
	 * into a wall or tree; one-block steps are still ridden up.
	 */
	private boolean tipsClear(BlockGetter level, double x, double y, double z, float yaw) {
		double rad = Math.toRadians(yaw);
		double fx = -Math.sin(rad);
		double fz = Math.cos(rad);
		for (double reach : new double[]{TIP_REACH, -TIP_REACH}) {
			double px = x + fx * reach;
			double pz = z + fz * reach;
			SurfaceSampler.Column column = SurfaceSampler.column(level, Mth.floor(px), Mth.floor(pz), y + 1.0);
			double rise = slopeGx * (px - x) + slopeGz * (pz - z);
			if (column.found() && column.top() > y + Math.max(0.0, rise) + SurfaceSampler.MAX_STEP + 0.3) {
				return false;
			}
		}
		return true;
	}

	/**
	 * A column under the rider's feet is a wall if it stands well above where the slope we're riding says the snow
	 * should be (so the uphill side of a steep traverse isn't a wall, but a tree trunk is).
	 */
	private boolean feetClear(BlockGetter level, double x, double y, double z) {
		for (int corner = 0; corner < 4; corner++) {
			double cx = x + ((corner & 1) == 0 ? -0.25 : 0.25);
			double cz = z + ((corner & 2) == 0 ? -0.25 : 0.25);
			SurfaceSampler.Column column = SurfaceSampler.column(level, Mth.floor(cx), Mth.floor(cz), y + 1.0);
			double rise = slopeGx * (cx - x + vx) + slopeGz * (cz - z + vz);
			double expected = y + Math.max(0.0, rise);
			if (column.found() && column.top() > expected + SurfaceSampler.MAX_STEP) {
				return false;
			}
		}
		return true;
	}

	/** The rider's body from the knees up against solid blocks (leaves don't stop you). */
	private static boolean bodyClear(BlockGetter level, double x, double y, double z) {
		AABB box = bodyBox(x, y, z);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minX = Mth.floor(box.minX);
		int maxX = Mth.floor(box.maxX);
		int minY = Mth.floor(box.minY);
		int maxY = Mth.floor(box.maxY);
		int minZ = Mth.floor(box.minZ);
		int maxZ = Mth.floor(box.maxZ);
		for (int bx = minX; bx <= maxX; bx++) {
			for (int by = minY; by <= maxY; by++) {
				for (int bz = minZ; bz <= maxZ; bz++) {
					pos.set(bx, by, bz);
					BlockState state = level.getBlockState(pos);
					if (state.isAir() || state.is(BlockTags.LEAVES)) {
						continue;
					}
					VoxelShape shape = state.getCollisionShape(level, pos);
					if (shape.isEmpty()) {
						continue;
					}
					for (AABB part : shape.toAabbs()) {
						if (part.move(bx, by, bz).intersects(box)) {
							return false;
						}
					}
				}
			}
		}
		return true;
	}

	// ----------------------------------------------------------------------------------------- helpers

	/** Minecraft yaw of a horizontal direction. */
	public static float yawOf(double dx, double dz) {
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}

	private void rotateVelocity(float degrees) {
		if (degrees == 0) {
			return;
		}
		// Increasing yaw turns clockwise seen from above (south -> west), same as heading.
		double rad = Math.toRadians(degrees);
		double cos = Math.cos(rad);
		double sin = Math.sin(rad);
		double nvx = vx * cos - vz * sin;
		double nvz = vx * sin + vz * cos;
		vx = nvx;
		vz = nvz;
	}

	private void decelerate(double amount) {
		double sp = speed();
		if (sp <= 1.0e-6 || amount <= 0) {
			return;
		}
		double ns = Math.max(0, sp - amount);
		double k = ns / sp;
		vx *= k;
		vz *= k;
	}

	private static float approach(float value, float target, float step) {
		if (value < target) {
			return Math.min(value + step, target);
		}
		return Math.max(value - step, target);
	}

	private static double smoothstep(double edge0, double edge1, double x) {
		double t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
		return t * t * (3 - 2 * t);
	}
}
