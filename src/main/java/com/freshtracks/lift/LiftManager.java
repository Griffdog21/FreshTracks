package com.freshtracks.lift;

import com.freshtracks.FreshTracks;
import com.freshtracks.item.RigItem;
import com.freshtracks.net.LiftPhasePayload;
import com.freshtracks.net.LiftSyncPayload;
import com.freshtracks.registry.ModEntities;
import com.freshtracks.registry.ModSounds;
import com.freshtracks.rig.RigEntity;
import com.freshtracks.rig.SurfaceSampler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Runs every lift on the server: moves the rope, scoops up riders at the bottom, drops them off at the top, and
 * keeps clients in sync. Lifts keep running even where no chunks are loaded (only the rope position changes).
 */
public final class LiftManager {
	private static final Map<ServerLevel, Runtime> RUNTIMES = new IdentityHashMap<>();
	private static int nextNetId = 1;

	/** Rope acceleration when starting or stopping, blocks/tick^2. */
	private static final double RAMP = 0.004;
	/** A chair scoops up riders standing up to this far behind / ahead of it as it passes the loading spot. */
	static final double BOARD_BEHIND = 1.8;
	static final double BOARD_AHEAD = 1.6;

	private LiftManager() {
	}

	static final class LineState {
		final int netId;
		double phase;
		double currentSpeed;

		LineState(int netId, double phase) {
			this.netId = netId;
			this.phase = phase;
		}
	}

	record Tow(UUID lineId, int carrier, int slot, RigEntity rig) {
	}

	static final class Runtime {
		final ServerLevel level;
		final LiftData data;
		final Map<UUID, LineState> states = new HashMap<>();
		final Map<Long, List<LiftSeatEntity>> seats = new HashMap<>();
		/** Riders a surface lift is pulling up the hill. */
		final List<Tow> tows = new ArrayList<>();
		int syncTimer;

		Runtime(ServerLevel level) {
			this.level = level;
			this.data = level.getDataStorage().computeIfAbsent(LiftData.TYPE);
			for (LiftLine line : data.lines.values()) {
				states.put(line.id, new LineState(nextNetId++, data.phases.getOrDefault(line.id, 0.0)));
			}
		}
	}

	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(LiftManager::tick);
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> server.execute(() -> sendAll(listener.getPlayer())));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> sendAll(player));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> RUNTIMES.clear());
		// Logging out mid-ride: set the rider down at the top station instead of saving them onto a chair.
		ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> {
			ServerPlayer player = listener.getPlayer();
			if (player.getVehicle() instanceof LiftSeatEntity seat && player.level() instanceof ServerLevel level) {
				LiftLine line = line(level, seat.lineId());
				player.stopRiding();
				seat.discard();
				if (line != null) {
					Vec3 off = line.ropeAt(line.unloadS()).pos().add(0, -line.style.hanger, 0);
					player.teleportTo(off.x, off.y + 0.2, off.z);
					player.resetFallDistance();
				}
			}
		});
	}

	static Runtime runtime(ServerLevel level) {
		return RUNTIMES.computeIfAbsent(level, Runtime::new);
	}

	public static List<LiftLine> lines(ServerLevel level) {
		return new ArrayList<>(runtime(level).data.lines.values());
	}

	public static @Nullable LiftLine line(ServerLevel level, UUID id) {
		return runtime(level).data.lines.get(id);
	}

	public static @Nullable LiftLine lineByNetId(ServerLevel level, int netId) {
		Runtime rt = runtime(level);
		for (Map.Entry<UUID, LineState> e : rt.states.entrySet()) {
			if (e.getValue().netId == netId) {
				return rt.data.lines.get(e.getKey());
			}
		}
		return null;
	}

	public static int netId(ServerLevel level, UUID id) {
		LineState state = runtime(level).states.get(id);
		return state == null ? -1 : state.netId;
	}

	public static double phase(ServerLevel level, UUID id) {
		LineState state = runtime(level).states.get(id);
		return state == null ? 0 : state.phase;
	}

	public static void add(ServerLevel level, LiftLine line) {
		Runtime rt = runtime(level);
		rt.data.lines.put(line.id, line);
		rt.states.computeIfAbsent(line.id, k -> new LineState(nextNetId++, 0));
		rt.data.setDirty();
		broadcast(level);
	}

	public static void changed(ServerLevel level, LiftLine line) {
		line.rebuild();
		Runtime rt = runtime(level);
		LineState state = rt.states.get(line.id);
		if (state != null) {
			state.phase = Mth.positiveModulo(state.phase, line.cycleLength());
		}
		// carriers may have moved or changed type: let go of anyone being towed
		rt.tows.removeIf(tow -> {
			if (tow.lineId().equals(line.id)) {
				tow.rig().clearTow();
				return true;
			}
			return false;
		});
		rt.data.setDirty();
		broadcast(level);
	}

	/**
	 * Changes a lift's type. Carriers change shape (and surface lifts have no seats), so anyone riding is set down at
	 * the bottom loading spot first.
	 */
	public static void restyle(ServerLevel level, LiftLine line, ChairStyle style) {
		if (style == line.style) {
			changed(level, line);
			return;
		}
		Runtime rt = runtime(level);
		Vec3 safe = line.loadPoint();
		rt.seats.entrySet().removeIf(entry -> {
			for (LiftSeatEntity seat : entry.getValue()) {
				if (seat.lineId().equals(line.id)) {
					net.minecraft.world.entity.Entity rider = seat.getFirstPassenger();
					seat.ejectPassengers();
					seat.discard();
					if (rider != null) {
						rider.teleportTo(safe.x, safe.y + 0.1, safe.z);
						rider.resetFallDistance();
					}
				}
			}
			entry.getValue().removeIf(net.minecraft.world.entity.Entity::isRemoved);
			return entry.getValue().isEmpty();
		});
		line.style = style;
		changed(level, line);
		LiftSetup.placeMarks(level, line);
	}

	/** The lift panel's Save button. */
	public static void edit(ServerPlayer player, com.freshtracks.net.LiftEditPayload payload) {
		ServerLevel level = player.level();
		if (player.distanceToSqr(Vec3.atCenterOf(payload.terminal())) > 24 * 24) {
			return;
		}
		LiftLine line = findByTerminal(level, payload.terminal());
		if (line == null) {
			return;
		}
		if (!LiftPermissions.canEdit(player)) {
			player.sendOverlayMessage(Component.translatable("message.freshtracks.lift.no_permission").withStyle(ChatFormatting.RED));
			return;
		}
		String name = payload.name().strip();
		if (!name.isEmpty()) {
			line.name = name.length() > 48 ? name.substring(0, 48) : name;
		}
		line.speed = LiftSetup.tidySpeed(payload.speed());
		line.spacing = Math.round(Mth.clamp(payload.spacing(), 5f, 40f));
		line.running = payload.running();
		restyle(level, line, ChairStyle.byId(payload.style()));
		player.sendOverlayMessage(com.freshtracks.lift.block.LiftTerminalBlock.status(line));
	}

	public static void remove(ServerLevel level, UUID id) {
		Runtime rt = runtime(level);
		LiftLine removed = rt.data.lines.remove(id);
		rt.states.remove(id);
		rt.data.phases.remove(id);
		if (removed == null) {
			return;
		}
		rt.data.setDirty();
		// Let everyone off.
		rt.seats.entrySet().removeIf(entry -> {
			for (LiftSeatEntity seat : entry.getValue()) {
				if (seat.lineId().equals(id)) {
					seat.ejectPassengers();
					seat.discard();
				}
			}
			entry.getValue().removeIf(s -> s.isRemoved());
			return entry.getValue().isEmpty();
		});
		broadcast(level);
	}

	public static @Nullable LiftLine findByTerminal(ServerLevel level, BlockPos pos) {
		for (LiftLine line : runtime(level).data.lines.values()) {
			if (line.bottom.equals(pos) || line.top.equals(pos)) {
				return line;
			}
		}
		return null;
	}

	public static @Nullable LiftLine findByTower(ServerLevel level, BlockPos pos) {
		for (LiftLine line : runtime(level).data.lines.values()) {
			if (line.towers.contains(pos)) {
				return line;
			}
		}
		return null;
	}

	// ----------------------------------------------------------------------------------------- tick

	private static void tick(ServerLevel level) {
		Runtime rt = RUNTIMES.get(level);
		if (rt == null) {
			if (level.getDataStorage().get(LiftData.TYPE) == null) {
				return;
			}
			rt = runtime(level);
		}
		double wind = LiftWeather.speedFactor(level);
		for (LiftLine line : rt.data.lines.values()) {
			LineState state = rt.states.get(line.id);
			double target = line.running ? line.speed * wind * com.freshtracks.FreshTracksConfig.liftSpeed : 0;
			target = line.pulseTarget(state.phase, target);
			state.currentSpeed = state.currentSpeed < target ? Math.min(target, state.currentSpeed + RAMP) : Math.max(target, state.currentSpeed - RAMP);
			double before = state.phase;
			state.phase = Mth.positiveModulo(state.phase + state.currentSpeed, line.cycleLength());
			if (state.currentSpeed > 0) {
				handleStations(rt, line, before, state.phase);
			}
			rt.data.phases.put(line.id, state.phase);
		}
		rt.seats.values().removeIf(list -> {
			list.removeIf(seat -> seat.isRemoved() || !seat.isVehicle());
			return list.isEmpty();
		});
		checkTows(rt);
		if (++rt.syncTimer >= 40) {
			rt.syncTimer = 0;
			if (!rt.data.lines.isEmpty()) {
				LiftPhasePayload payload = phasePayload(rt);
				for (ServerPlayer player : level.players()) {
					ServerPlayNetworking.send(player, payload);
				}
			}
			rt.data.setDirty();
		}
	}

	private static boolean crossed(double before, double after, double mark) {
		if (after >= before) {
			return before < mark && mark <= after;
		}
		return mark > before || mark <= after;
	}

	private static void handleStations(Runtime rt, LiftLine line, double before, double after) {
		boolean surface = line.style.surface();
		for (int chair = 0; chair < line.chairCount(); chair++) {
			double sBefore = line.chairS(before, chair);
			double sAfter = line.chairS(after, chair);
			if (crossed(sBefore, sAfter, line.loadS())) {
				if (surface) {
					hook(rt, line, chair, after);
				} else {
					board(rt, line, chair, after);
				}
			}
			if (crossed(sBefore, sAfter, line.unloadS())) {
				if (surface) {
					letGo(rt, line, chair);
				} else {
					unload(rt, line, chair, after, true);
				}
			}
			if (!surface && crossed(sBefore, sAfter, line.downUnloadS())) {
				unload(rt, line, chair, after, false);
			}
			for (int m = 0; m < line.midStations(); m++) {
				// mid stations: people waiting on the platform get on, riders holding sneak get off
				if (crossed(sBefore, sAfter, line.midLoadS(m))) {
					if (surface) {
						hook(rt, line, chair, after);
					} else {
						board(rt, line, chair, after);
					}
				}
				if (!surface && crossed(sBefore, sAfter, line.midUnloadS(m))) {
					unload(rt, line, chair, after, true, ServerPlayer::isShiftKeyDown);
				}
			}
		}
	}

	// ----------------------------------------------------------------------------------------- surface lifts

	/** A T-bar or platter comes round: whoever is waiting on their skis at the loading spot gets pulled along. */
	private static void hook(Runtime rt, LiftLine line, int chair, double phase) {
		ServerLevel level = rt.level;
		double s = line.chairS(phase, chair);
		Vec3 first = line.towPoint(s, 0);
		Vec3 last = line.towPoint(s, line.style.seats - 1);
		Vec3 centre = first.add(last).scale(0.5);
		double ground = centre.y - LiftLine.TERMINAL_ROPE;
		double reach = line.style.width / 2 + 1.1;
		AABB zone = new AABB(centre.x - reach, ground - 1.5, centre.z - reach, centre.x + reach, ground + 2.0, centre.z + reach);
		List<Player> waiting = level.getEntitiesOfClass(Player.class, zone, p -> p.isAlive() && !p.isSpectator()
			&& (p.getVehicle() == null || p.getVehicle() instanceof RigEntity rig && !rig.isTowed()));
		if (waiting.isEmpty()) {
			return;
		}
		boolean[] used = new boolean[line.style.seats];
		for (Tow tow : rt.tows) {
			if (tow.lineId().equals(line.id) && tow.carrier() == chair && tow.slot() < used.length) {
				used[tow.slot()] = true;
			}
		}
		Vec3 dir = line.ropeAt(s).direction();
		Vec3 right = new Vec3(-dir.z, 0, dir.x).normalize();
		waiting.sort(Comparator.comparingDouble(p -> p.position().subtract(centre).dot(right)));
		int netId = netId(level, line.id);
		boolean hooked = false;
		for (Player player : waiting) {
			if (!(player.getVehicle() instanceof RigEntity rig)) {
				player.sendOverlayMessage(Component.translatable("message.freshtracks.lift.need_skis", line.style.displayName())
					.withStyle(ChatFormatting.YELLOW));
				continue;
			}
			int slot = -1;
			double best = Double.MAX_VALUE;
			for (int k = 0; k < used.length; k++) {
				if (!used[k]) {
					Vec3 at = line.towPoint(s, k);
					double d = (at.x - player.getX()) * (at.x - player.getX()) + (at.z - player.getZ()) * (at.z - player.getZ());
					if (d < best) {
						best = d;
						slot = k;
					}
				}
			}
			if (slot < 0) {
				break;
			}
			used[slot] = true;
			rig.setTow(netId, chair, slot);
			rt.tows.add(new Tow(line.id, chair, slot, rig));
			hooked = true;
			if (player instanceof ServerPlayer sp) {
				com.freshtracks.advancement.ModCriteria.fire(sp, "lift");
			}
		}
		if (hooked) {
			level.playSound(null, centre.x, ground + 1, centre.z, ModSounds.CHAIR_CLACK, SoundSource.BLOCKS, 0.7f, 1.3f);
		}
	}

	/** Top of a surface lift: the bar lets go and the riders ski away. */
	private static void letGo(Runtime rt, LiftLine line, int chair) {
		rt.tows.removeIf(tow -> {
			if (tow.lineId().equals(line.id) && tow.carrier() == chair) {
				tow.rig().clearTow();
				return true;
			}
			return false;
		});
	}

	/** Drops tows that ended some other way: the rider let go (Shift), stepped out, fell behind, or the lift changed. */
	private static void checkTows(Runtime rt) {
		rt.tows.removeIf(tow -> {
			RigEntity rig = tow.rig();
			LiftLine line = rt.data.lines.get(tow.lineId());
			boolean drop = rig.isRemoved() || line == null || !line.style.surface()
				|| !(rig.getFirstPassenger() instanceof ServerPlayer player) || player.isShiftKeyDown();
			if (!drop) {
				double s = line.chairS(rt.states.get(line.id).phase, tow.carrier());
				if (s > line.unloadS() + 1.0) {
					drop = true;
				} else {
					Vec3 at = line.towPoint(s, tow.slot());
					double dx = rig.getX() - at.x;
					double dz = rig.getZ() - at.z;
					drop = dx * dx + dz * dz > 36;
				}
			}
			if (drop && !rig.isRemoved()) {
				rig.clearTow();
			}
			return drop;
		});
	}

	private static long seatKey(LiftLine line, int chair) {
		return ((long) line.id.hashCode() << 20) ^ chair;
	}

	private static void board(Runtime rt, LiftLine line, int chair, double phase) {
		ServerLevel level = rt.level;
		Vec3 centre = line.seatPos(phase, chair, 0).add(line.seatPos(phase, chair, line.style.seats - 1)).scale(0.5);
		Vec3 along = line.ropeAt(line.chairS(phase, chair)).direction();
		Vec3 ahead = new Vec3(along.x, 0, along.z).normalize();
		Vec3 side = new Vec3(-ahead.z, 0, ahead.x);
		double lateral = line.style.width / 2 + 0.9;
		double reach = Math.max(lateral, BOARD_BEHIND) + 0.5;
		AABB zone = new AABB(centre.x - reach, centre.y - 2.4, centre.z - reach, centre.x + reach, centre.y + 1.2, centre.z + reach);
		List<Player> waiting = level.getEntitiesOfClass(Player.class, zone, p -> {
			if (!p.isAlive() || p.isSpectator() || !(p.getVehicle() == null || p.getVehicle() instanceof RigEntity)) {
				return false;
			}
			Vec3 rel = p.position().subtract(centre);
			double fwd = rel.dot(ahead);
			return Math.abs(rel.dot(side)) <= lateral && fwd >= -BOARD_BEHIND && fwd <= BOARD_AHEAD;
		});
		if (waiting.isEmpty()) {
			return;
		}
		List<LiftSeatEntity> taken = rt.seats.computeIfAbsent(seatKey(line, chair), k -> new ArrayList<>());
		boolean[] used = new boolean[line.style.seats];
		for (LiftSeatEntity seat : taken) {
			if (seat.seatIndex() < used.length) {
				used[seat.seatIndex()] = true;
			}
		}
		// Seat people where they stand, left to right.
		Vec3 dir = line.ropeAt(line.chairS(phase, chair)).direction();
		Vec3 right = new Vec3(-dir.z, 0, dir.x).normalize();
		waiting.sort(Comparator.comparingDouble(p -> p.position().subtract(centre).dot(right)));
		int boarded = 0;
		for (Player player : waiting) {
			int seatIndex = nearestFreeSeat(line, phase, chair, player.position(), used);
			if (seatIndex < 0) {
				break;
			}
			used[seatIndex] = true;
			ItemStack skis = ItemStack.EMPTY;
			if (player.getVehicle() instanceof RigEntity rig && player instanceof ServerPlayer sp) {
				skis = rig.getItem().copy();
				rig.stepOut(sp);
			}
			LiftSeatEntity seat = new LiftSeatEntity(ModEntities.LIFT_SEAT, level);
			seat.setup(line.id, netId(level, line.id), chair, seatIndex);
			seat.setPos(line.seatPos(phase, chair, seatIndex));
			seat.setRememberedRig(skis);
			level.addFreshEntity(seat);
			if (player.startRiding(seat, true, true)) {
				if (player instanceof ServerPlayer sp) {
					com.freshtracks.advancement.ModCriteria.fire(sp, "lift");
				}
				taken.add(seat);
				boarded++;
			} else {
				seat.discard();
			}
		}
		if (boarded > 0) {
			level.playSound(null, centre.x, centre.y, centre.z, ModSounds.CHAIR_CLACK, SoundSource.BLOCKS, 0.8f, 1.0f);
		}
	}

	private static int nearestFreeSeat(LiftLine line, double phase, int chair, Vec3 at, boolean[] used) {
		int best = -1;
		double bestDist = Double.MAX_VALUE;
		for (int s = 0; s < used.length; s++) {
			if (used[s]) {
				continue;
			}
			double d = line.seatPos(phase, chair, s).distanceToSqr(at.x, line.seatPos(phase, chair, s).y, at.z);
			if (d < bestDist) {
				bestDist = d;
				best = s;
			}
		}
		return best;
	}

	private static void unload(Runtime rt, LiftLine line, int chair, double phase, boolean atTop) {
		unload(rt, line, chair, phase, atTop, p -> true);
	}

	private static void unload(Runtime rt, LiftLine line, int chair, double phase, boolean atTop,
		java.util.function.Predicate<ServerPlayer> who) {
		List<LiftSeatEntity> taken = rt.seats.get(seatKey(line, chair));
		if (taken == null || taken.isEmpty()) {
			return;
		}
		ServerLevel level = rt.level;
		Vec3 forward = line.ropeAt(line.chairS(phase, chair)).direction();
		forward = new Vec3(forward.x, 0, forward.z).normalize();
		for (LiftSeatEntity seat : new ArrayList<>(taken)) {
			if (!(seat.getFirstPassenger() instanceof ServerPlayer player)) {
				seat.discard();
				continue;
			}
			if (!who.test(player)) {
				continue;
			}
			Vec3 at = seat.position().add(forward.scale(0.8));
			int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
			double y = Math.max(groundY, at.y - 2.0);
			ItemStack rememberedRig = seat.rememberedRig();
			player.stopRiding();
			seat.discard();
			player.teleportTo(at.x, y, at.z);
			// Face down the hill you're about to ride (unload ramps point downhill), else straight ahead.
			Vec3 away = forward;
			SurfaceSampler.Ground ground = SurfaceSampler.ground(level, at.x, at.z, y);
			if (ground.found() && ground.slope() > 0.05) {
				away = new Vec3(-ground.gradX(), 0, -ground.gradZ()).normalize();
			}
			float yaw = (float) Math.toDegrees(Math.atan2(-away.x, away.z));
			player.setYRot(yaw);
			player.setYHeadRot(yaw);
			if (atTop && LiftConfig.autoClickIn() && !rememberedRig.isEmpty()) {
				clickBackIn(level, player, rememberedRig, at.x, y, at.z, yaw, away);
			} else {
				player.setDeltaMovement(forward.scale(0.3));
				player.needsSync = true;
			}
		}
		taken.removeIf(LiftSeatEntity::isRemoved);
		level.playSound(null, seatsCentre(line, phase, chair).x, seatsCentre(line, phase, chair).y, seatsCentre(line, phase, chair).z,
			ModSounds.CHAIR_CLACK, SoundSource.BLOCKS, 0.6f, 1.2f);
	}

	private static Vec3 seatsCentre(LiftLine line, double phase, int chair) {
		return line.seatPos(phase, chair, 0).add(line.seatPos(phase, chair, line.style.seats - 1)).scale(0.5);
	}

	/** Off the chair and straight back on your skis, sliding away down the unload ramp. */
	private static void clickBackIn(ServerLevel level, ServerPlayer player, ItemStack wanted, double x, double y, double z, float yaw, Vec3 forward) {
		int slot = -1;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (ItemStack.isSameItemSameComponents(stack, wanted)) {
				slot = i;
				break;
			}
		}
		if (slot < 0) {
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				if (player.getInventory().getItem(i).getItem() instanceof RigItem) {
					slot = i;
					break;
				}
			}
		}
		if (slot < 0) {
			return;
		}
		ItemStack stack = player.getInventory().getItem(slot);
		RigEntity rig = new RigEntity(ModEntities.RIG, level);
		rig.snapTo(x, y, z, yaw, 0f);
		rig.setItem(stack);
		rig.setDeltaMovement(forward.scale(0.28));
		level.addFreshEntity(rig);
		if (player.startRiding(rig)) {
			stack.shrink(1);
			level.playSound(null, x, y, z, ModSounds.CLICK_IN, SoundSource.PLAYERS, 0.8f, 1.0f);
		} else {
			rig.discard();
		}
	}

	// ----------------------------------------------------------------------------------------- sync

	private static LiftPhasePayload phasePayload(Runtime rt) {
		int n = rt.states.size();
		int[] ids = new int[n];
		double[] phases = new double[n];
		float[] speeds = new float[n];
		int i = 0;
		for (LineState state : rt.states.values()) {
			ids[i] = state.netId;
			phases[i] = state.phase;
			speeds[i] = (float) state.currentSpeed;
			i++;
		}
		return new LiftPhasePayload(ids, phases, speeds);
	}

	private static LiftSyncPayload fullPayload(Runtime rt) {
		List<LiftSyncPayload.Entry> entries = new ArrayList<>();
		for (LiftLine line : rt.data.lines.values()) {
			LineState state = rt.states.get(line.id);
			entries.add(new LiftSyncPayload.Entry(state.netId, line, state.phase, (float) state.currentSpeed));
		}
		return new LiftSyncPayload(entries);
	}

	public static void sendAll(ServerPlayer player) {
		ServerPlayNetworking.send(player, fullPayload(runtime(player.level())));
	}

	public static void broadcast(ServerLevel level) {
		LiftSyncPayload payload = fullPayload(runtime(level));
		for (ServerPlayer player : level.players()) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	public static void log(String message) {
		FreshTracks.LOG.info("[lifts] {}", message);
	}
}
