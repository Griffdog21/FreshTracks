package com.freshtracks.rig;

import com.freshtracks.FreshTracks;
import com.freshtracks.gear.GearItem;
import com.freshtracks.item.RigItem;
import com.freshtracks.registry.ModComponents;
import com.freshtracks.registry.ModItems;
import com.freshtracks.registry.ModSounds;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A pair of skis or a snowboard with someone standing on it. Spawned when a player clicks in with a {@link RigItem}
 * and removed (item back in the inventory) when they step out. Like a boat, the rider's client runs the physics.
 */
public class RigEntity extends Entity {
	private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(RigEntity.class, EntityDataSerializers.ITEM_STACK);
	private static final EntityDataAccessor<Float> DATA_LEAN = SynchedEntityData.defineId(RigEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_FLIP = SynchedEntityData.defineId(RigEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(RigEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> DATA_GRAB = SynchedEntityData.defineId(RigEntity.class, EntityDataSerializers.BYTE);
	/** Surface lift pulling this rig: the lift's network id (-1 = none) and carrier * 8 + slot on the bar. */
	private static final EntityDataAccessor<Integer> DATA_TOW_LINE = SynchedEntityData.defineId(RigEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_TOW_CARRIER = SynchedEntityData.defineId(RigEntity.class, EntityDataSerializers.INT);
	/** The rider carries ski poles (anywhere in their inventory: they ride with them in their hands). */
	private static final EntityDataAccessor<Boolean> DATA_POLES = SynchedEntityData.defineId(RigEntity.class, EntityDataSerializers.BOOLEAN);
	/** What the rider's arms and legs are doing (RiderAction packed with its ticks), for everyone else's view. */
	private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(RigEntity.class, EntityDataSerializers.BYTE);

	/** Client hook: where the carrier towing this rig wants it (null if that lift isn't known here yet). */
	public interface TowLocator {
		RigMotion.@Nullable Tow locate(RigEntity rig, int lineNetId, int carrier, int slot);
	}

	public static TowLocator towLocator = (rig, line, carrier, slot) -> null;

	public static final int FLAG_AIR = 1;
	public static final int FLAG_TUCK = 2;
	public static final int FLAG_CROUCH = 4;
	public static final int FLAG_BRAKE = 8;
	public static final int FLAG_SKID = 16;
	public static final int FLAG_SWITCH = 32;
	public static final int FLAG_GRIND = 64;
	public static final int FLAG_BUTTER = 128;

	/** Below this speed (blocks/tick) holding sneak steps you out of your bindings. */
	public static final double STEP_OUT_SPEED = 0.12;

	public final RigMotion motion = new RigMotion();
	private RiderInput input = RiderInput.NONE;
	private @Nullable UUID lastRider;
	private int unriddenTicks;
	private int wearTicks;
	// Remote (not controlled here) smoothing for render.
	private float remoteLean;
	private float remoteLeanO;
	private float remoteFlip;
	private float remoteFlipO;
	private double serverSpeed;
	/**
	 * Where the rig was at the end of the previous server tick. The client drives the rig, so the server only sees
	 * it jump between packets; vanilla resets xo/yo/zo right before tick(), so they can't measure that.
	 */
	private @Nullable Vec3 lastServerPos;
	private Vec3 serverVelocity = Vec3.ZERO;

	public RigEntity(EntityType<? extends RigEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_ITEM, ItemStack.EMPTY);
		builder.define(DATA_LEAN, 0f);
		builder.define(DATA_FLIP, 0f);
		builder.define(DATA_FLAGS, (byte) 0);
		builder.define(DATA_GRAB, (byte) 0);
		builder.define(DATA_TOW_LINE, -1);
		builder.define(DATA_TOW_CARRIER, 0);
		builder.define(DATA_POLES, false);
		builder.define(DATA_ACTION, (byte) 0);
	}

	// ----------------------------------------------------------------------------------------- data

	public ItemStack getItem() {
		return this.entityData.get(DATA_ITEM);
	}

	public void setItem(ItemStack stack) {
		this.entityData.set(DATA_ITEM, stack.copyWithCount(1));
	}

	public RigKind kind() {
		return getItem().getItem() instanceof RigItem rig ? rig.kind() : RigKind.SKIS;
	}

	public RigStats stats() {
		if (!(getItem().getItem() instanceof RigItem rig)) {
			return RigStats.BASELINE;
		}
		com.freshtracks.economy.Tune tune = getItem().get(ModComponents.TUNE);
		return tune == null ? rig.stats() : tune.apply(rig.stats());
	}

	/** The rig's stats with the rider's kit: matching boots, poles in hand (skis), and wax. */
	public RigStats effectiveStats() {
		RigStats stats = stats();
		if (!(this.getControllingPassenger() instanceof LivingEntity rider)) {
			return stats;
		}
		boolean board = kind().isBoard();
		if (GearItem.hasPerk(rider, board ? GearItem.Perk.BOARD_BOOTS : GearItem.Perk.SKI_BOOTS)) {
			stats = stats.times(1f, 1.07f, 1.05f, 1f, 1f);
		}
		if (hasPoles()) {
			stats = stats.times(1f, 1f, 1f, 1.05f, 1.45f);
		}
		if (GearItem.hasPerk(rider, GearItem.Perk.RACE)) {
			stats = stats.times(1.04f, 1f, 1f, 1f, 1f); // skin-tight: less drag
		}
		if (getItem().getOrDefault(ModComponents.WAX, 0) > 0) {
			stats = stats.times(1.07f, 1f, 1f, 1f, 1.1f);
		}
		return stats;
	}

	/**
	 * Skis ridden with poles: the rider carries a pair anywhere in their inventory (you ride with them in your hands,
	 * whatever the hotbar has selected). The server works it out; the rider's own client can tell at once.
	 */
	public boolean hasPoles() {
		if (!(this.getControllingPassenger() instanceof Player rider) || kind().isBoard()) {
			return false;
		}
		if (this.level().isClientSide() && !isDrivenHere()) {
			return this.entityData.get(DATA_POLES);
		}
		return carriesPoles(rider);
	}

	private static boolean carriesPoles(Player rider) {
		if (rider.getOffhandItem().is(ModItems.SKI_POLES)) {
			return true;
		}
		for (ItemStack stack : rider.getInventory().getNonEquipmentItems()) {
			if (stack.is(ModItems.SKI_POLES)) {
				return true;
			}
		}
		return false;
	}

	public int flags() {
		return this.entityData.get(DATA_FLAGS);
	}

	public boolean hasFlag(int flag) {
		return (flags() & flag) != 0;
	}

	/** A surface lift grabs this rig (server). */
	public void setTow(int lineNetId, int carrier, int slot) {
		this.entityData.set(DATA_TOW_CARRIER, carrier * 8 + slot);
		this.entityData.set(DATA_TOW_LINE, lineNetId);
	}

	/** The surface lift lets go (server). */
	public void clearTow() {
		this.entityData.set(DATA_TOW_LINE, -1);
	}

	public boolean isTowed() {
		return this.entityData.get(DATA_TOW_LINE) >= 0;
	}

	public int towLine() {
		return this.entityData.get(DATA_TOW_LINE);
	}

	public int towCarrier() {
		return this.entityData.get(DATA_TOW_CARRIER) / 8;
	}

	public int towSlot() {
		return this.entityData.get(DATA_TOW_CARRIER) % 8;
	}

	/** The tow this rider let go of on their own (Shift), so we stop following it until the server catches up. */
	private int releasedTow = Integer.MIN_VALUE;

	private void updateTow() {
		motion.tow = null;
		int line = towLine();
		if (line < 0) {
			releasedTow = Integer.MIN_VALUE;
			return;
		}
		int key = line * 65536 + this.entityData.get(DATA_TOW_CARRIER);
		if (input.brake()) {
			releasedTow = key;
		}
		if (releasedTow != key) {
			motion.tow = towLocator.locate(this, line, towCarrier(), towSlot());
		}
	}

	public Grab grab() {
		return Grab.byId(this.entityData.get(DATA_GRAB));
	}

	/** Called on the server with what the rider's client says it's doing (for everyone else's view). */
	public void applyRiderState(float lean, float flip, int flags, int grab, byte action) {
		this.entityData.set(DATA_LEAN, Mth.clamp(lean, -80f, 80f));
		this.entityData.set(DATA_FLIP, Mth.wrapDegrees(flip));
		this.entityData.set(DATA_FLAGS, (byte) flags);
		this.entityData.set(DATA_GRAB, (byte) grab);
		this.entityData.set(DATA_ACTION, action);
	}

	/** The rider's arms and legs this tick: RiderAction packed with how long it has been going. */
	public byte renderAction() {
		return isDrivenHere() ? RiderAction.pack(motion.action, motion.actionTicks) : this.entityData.get(DATA_ACTION);
	}

	public void setRiderInput(RiderInput input) {
		this.input = input;
	}

	public RiderInput riderInput() {
		return input;
	}

	/** True on the client that drives this rig. */
	public boolean isDrivenHere() {
		return this.level().isClientSide() && this.isLocalInstanceAuthoritative();
	}

	// Tilt to match the snow under the rig (client only, for drawing): pitch nose-down/up, roll toward the fall line.
	private float slopePitch;
	private float slopePitchO;
	private float slopeRoll;
	private float slopeRollO;

	public float slopePitch(float partialTick) {
		return Mth.lerp(partialTick, slopePitchO, slopePitch);
	}

	public float slopeRoll(float partialTick) {
		return Mth.lerp(partialTick, slopeRollO, slopeRoll);
	}

	private void updateSlopeTilt() {
		slopePitchO = slopePitch;
		slopeRollO = slopeRoll;
		float pitch = 0f;
		float roll = 0f;
		if ((renderFlags() & (FLAG_AIR | FLAG_GRIND)) == 0) {
			SurfaceSampler.Ground ground = SurfaceSampler.ground(this.level(), this.getX(), this.getZ(), this.getY());
			if (ground.found() && Math.abs(this.getY() - ground.height()) < 0.6) {
				double yaw = Math.toRadians(this.getYRot());
				double fx = -Math.sin(yaw);
				double fz = Math.cos(yaw);
				// rise of the snow per block ahead of the rig, and per block to its right
				double riseAhead = ground.gradX() * fx + ground.gradZ() * fz;
				double riseRight = ground.gradX() * -fz + ground.gradZ() * fx;
				pitch = (float) Math.toDegrees(Math.atan(riseAhead));
				roll = (float) Math.toDegrees(Math.atan(riseRight));
			}
		}
		if ((renderFlags() & FLAG_AIR) == 0) {
			if (renderGrab() == Grab.FORWARD) pitch -= 14f;
			if (renderGrab() == Grab.BACK) pitch += 14f;
		}
		slopePitch += (Mth.clamp(pitch, -60f, 60f) - slopePitch) * 0.4f;
		slopeRoll += (Mth.clamp(roll, -60f, 60f) - slopeRoll) * 0.4f;
	}

	public float lean(float partialTick) {
		if (isDrivenHere()) {
			return Mth.lerp(partialTick, motion.leanO, motion.lean);
		}
		return Mth.lerp(partialTick, remoteLeanO, remoteLean);
	}

	public float flip(float partialTick) {
		if (isDrivenHere()) {
			return Mth.lerp(partialTick, motion.flipVisualO, motion.flipVisual);
		}
		return Mth.rotLerp(partialTick, remoteFlipO, remoteFlip);
	}

	public int renderFlags() {
		if (isDrivenHere()) {
			return localFlags();
		}
		return flags();
	}

	public Grab renderGrab() {
		return isDrivenHere() ? motion.tricks.grab : grab();
	}

	public boolean hasFlagOrLocal(int flag) {
		return (renderFlags() & flag) != 0;
	}

	/** Horizontal speed in blocks/tick as this client sees it. */
	public double visualSpeed() {
		if (isDrivenHere()) {
			return motion.speed();
		}
		double dx = this.getX() - this.xo;
		double dz = this.getZ() - this.zo;
		return Math.sqrt(dx * dx + dz * dz);
	}

	public double visualSpeed3() {
		if (isDrivenHere()) {
			return motion.speed3();
		}
		double dx = this.getX() - this.xo;
		double dy = this.getY() - this.yo;
		double dz = this.getZ() - this.zo;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	public int localFlags() {
		int f = 0;
		if (motion.grinding) f |= FLAG_GRIND;
		if (motion.buttering) f |= FLAG_BUTTER;
		if (!motion.grounded) {
			f |= FLAG_AIR;
		}
		if (motion.tuck) {
			f |= FLAG_TUCK;
		}
		if (motion.jumpHeld) {
			f |= FLAG_CROUCH;
		}
		if (motion.plow || motion.hockeyStop || motion.edgeCheck) {
			f |= FLAG_BRAKE;
		}
		if (motion.skid > 0.25f) {
			f |= FLAG_SKID;
		}
		if (motion.switchStance) {
			f |= FLAG_SWITCH;
		}
		return f;
	}

	// ----------------------------------------------------------------------------------------- tick

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			clientTick();
		} else {
			serverTick((ServerLevel) this.level());
		}
	}

	private void clientTick() {
		if (isDrivenHere()) {
			if (!motion.initialized) {
				motion.reset(this.getYRot());
			}
			updateTow();
			motion.poles = hasPoles();
			Vec3 next = motion.step(this.level(), this.position(), input, effectiveStats(), kind());
			Vec3 delta = next.subtract(this.position());
			this.move(MoverType.SELF, delta);
			this.setYRot(motion.heading);
			this.setDeltaMovement(motion.vx, motion.vy, motion.vz);
			this.setOnGround(motion.grounded);
			RigClientHooks.afterLocalStep(this);
		} else {
			motion.initialized = false;
			remoteLeanO = remoteLean;
			remoteFlipO = remoteFlip;
			remoteLean += (this.entityData.get(DATA_LEAN) - remoteLean) * 0.5f;
			remoteFlip = Mth.rotLerp(0.6f, remoteFlip, this.entityData.get(DATA_FLIP));
		}
		updateSlopeTilt();
		RigClientHooks.effects(this);
	}

	private void serverTick(ServerLevel level) {
		Entity rider = this.getFirstPassenger();
		Vec3 now = this.position();
		Vec3 previous = lastServerPos == null ? now : lastServerPos;
		Vec3 moved = now.subtract(previous);
		if (moved.lengthSqr() > 100) {
			moved = Vec3.ZERO; // teleported
			previous = now;
		}
		lastServerPos = now;
		serverVelocity = moved;
		serverSpeed = serverSpeed * 0.6 + moved.horizontalDistance() * 0.4;
		if (rider == null) {
			if (++unriddenTicks > 3) {
				returnItem(level, null);
			}
			return;
		}
		unriddenTicks = 0;
		if (rider instanceof ServerPlayer player) {
			lastRider = player.getUUID();
			if (this.tickCount % 10 == 0) {
				this.entityData.set(DATA_POLES, !kind().isBoard() && carriesPoles(player));
			}
			if (player.isShiftKeyDown() && serverSpeed < STEP_OUT_SPEED) {
				stepOut(player);
				return;
			}
			wearBase(level, player);
			burnWax();
			leaveTracks(level, previous, now);
			com.freshtracks.race.RaceTiming.sweep(level, player, previous, now);
			com.freshtracks.economy.Economy.ride(player, moved, serverSpeed);
			if (moved.horizontalDistance() * 72.0 >= 100.0 && this.tickCount % 10 == 0) {
				com.freshtracks.advancement.ModCriteria.fire(player, "speed_100");
			}
		}
	}

	/** Fresh powder this rig rode through, packed into a track a moment later (so you ride the powder, not your track). */
	private final java.util.ArrayDeque<long[]> pendingTracks = new java.util.ArrayDeque<>();
	private static final int TRACK_DELAY = 10;
	private final java.util.LinkedHashMap<Long, Integer> recentTracks = new java.util.LinkedHashMap<>();

	/** Riding through fresh powder packs it down: your line stays on the mountain. */
	private void leaveTracks(ServerLevel level, Vec3 from, Vec3 to) {
		while (!pendingTracks.isEmpty() && pendingTracks.peekFirst()[1] <= this.tickCount) {
			net.minecraft.core.BlockPos p = net.minecraft.core.BlockPos.of(pendingTracks.pollFirst()[0]);
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(p);
			if (state.getBlock() instanceof com.freshtracks.weather.GroomedSnowBlock) {
				com.freshtracks.weather.GroomedSnowBlock.track(level, p, state);
			} else if (state.getBlock() instanceof com.freshtracks.weather.FreshPowderBlock) {
				com.freshtracks.weather.FreshPowderBlock.track(level, p, state);
			} else if (state.getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock && state.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) >= 3) {
				level.setBlock(p, state.setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, state.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) - 1), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
			}
		}
		if (serverSpeed < 0.08 || hasFlag(FLAG_GRIND)) {
			return;
		}
		// the rig moves up to two blocks a tick, so lay the track along the whole stretch it covered
		double length = from.distanceTo(to);
		int steps = Math.max(1, (int) Math.ceil(length / 0.5));
		for (int i = 1; i <= steps; i++) {
			Vec3 at = from.lerp(to, i / (double) steps);
			net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(at.x, at.y + 0.05, at.z);
			for (int dy = 0; dy >= -1; dy--) {
				net.minecraft.core.BlockPos p = pos.offset(0, dy, 0);
				net.minecraft.world.level.block.state.BlockState st = level.getBlockState(p);
				boolean fresh = st.getBlock() instanceof com.freshtracks.weather.FreshPowderBlock;
				boolean snow = st.getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock && !fresh;
				if (fresh || st.getBlock() instanceof com.freshtracks.weather.GroomedSnowBlock || (snow && st.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) >= 3)) {
					long key = p.asLong();
					if (this.tickCount - recentTracks.getOrDefault(key, -1000) >= 80) {
						recentTracks.put(key, this.tickCount);
						if (recentTracks.size() > 512) recentTracks.remove(recentTracks.keySet().iterator().next());
						pendingTracks.addLast(new long[]{key, this.tickCount + TRACK_DELAY});
					}
					if (fresh && this.getFirstPassenger() instanceof ServerPlayer player) {
						com.freshtracks.advancement.ModCriteria.fire(player, "fresh_tracks");
					}
					break;
				}
			}
		}
	}

	/** Wax wears off as you ride. */
	private void burnWax() {
		if (serverSpeed < 0.05 || this.tickCount % 20 != 0) {
			return;
		}
		int wax = getItem().getOrDefault(ModComponents.WAX, 0);
		if (wax > 0) {
			ItemStack stack = getItem().copy();
			if (wax <= 20) {
				stack.remove(ModComponents.WAX);
			} else {
				stack.set(ModComponents.WAX, wax - 20);
			}
			setItem(stack);
		}
	}

	/** Riding over rocks and dirt slowly dings the base. */
	private void wearBase(ServerLevel level, ServerPlayer player) {
		if (serverSpeed < 0.15 || player.isCreative() || hasFlag(FLAG_GRIND)) {
			return;
		}
		SurfaceSampler.Column column = SurfaceSampler.column(level, Mth.floor(this.getX()), Mth.floor(this.getZ()), this.getY());
		if (!column.found() || !column.surface().wearsBase() || this.getY() - column.top() > 0.3) {
			return;
		}
		if (++wearTicks % 20 == 0) {
			ItemStack stack = getItem().copy();
			if (stack.isDamageableItem()) {
				int damage = stack.getDamageValue() + 1;
				if (damage >= stack.getMaxDamage()) {
					level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.CORE_SHOT, SoundSource.PLAYERS, 1f, 0.8f);
					player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.freshtracks.core_shot"), true);
					stack.setDamageValue(stack.getMaxDamage() - 1);
				} else {
					stack.setDamageValue(damage);
				}
				setItem(stack);
			}
		}
	}

	public void stepOut(ServerPlayer player) {
		player.stopRiding();
		this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.CLICK_OUT, SoundSource.PLAYERS, 0.8f, 1.0f);
		returnItem((ServerLevel) this.level(), player);
	}

	/** Puts the skis back in the rider's inventory (or on the ground) and removes the rig. */
	public void returnItem(ServerLevel level, @Nullable Player player) {
		if (this.isRemoved()) {
			return;
		}
		ItemStack stack = getItem().copy();
		if (player == null && lastRider != null) {
			Player last = level.getPlayerByUUID(lastRider);
			if (last != null && last.isAlive() && last.distanceToSqr(this) < 64 * 64) {
				player = last;
			}
		}
		if (!stack.isEmpty()) {
			if (player == null || !player.getInventory().add(stack)) {
				ItemEntity drop = new ItemEntity(level, this.getX(), this.getY() + 0.3, this.getZ(), stack);
				drop.setTarget(player != null ? player.getUUID() : lastRider);
				drop.setUnlimitedLifetime();
				level.addFreshEntity(drop);
			}
		}
		this.discard();
	}

	/** A wipeout: rider flies off, skis go flying ("yard sale"). */
	public void yardSale(ServerPlayer player, float severity) {
		ServerLevel level = (ServerLevel) this.level();
		com.freshtracks.advancement.ModCriteria.fire(player, "yard_sale");
		Vec3 velocity = serverVelocity;
		player.stopRiding();
		ItemStack stack = getItem().copy();
		if (!stack.isEmpty()) {
			ItemEntity drop = new ItemEntity(level, this.getX(), this.getY() + 0.5, this.getZ(), stack,
				velocity.x * 0.8 + (this.random.nextDouble() - 0.5) * 0.4, 0.35, velocity.z * 0.8 + (this.random.nextDouble() - 0.5) * 0.4);
			drop.setTarget(player.getUUID());
			drop.setUnlimitedLifetime();
			drop.setPickUpDelay(15);
			level.addFreshEntity(drop);
		}
		player.setDeltaMovement(velocity.scale(0.6).add(0, 0.25, 0));
		player.needsSync = true;
		float damage = Math.max(0, (severity - 0.5f) * 6f);
		if (damage > 0.5f) {
			player.hurtServer(level, level.damageSources().flyIntoWall(), damage);
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.WIPEOUT, SoundSource.PLAYERS, 1.0f, 0.9f + this.random.nextFloat() * 0.2f);
		level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY() + 0.6, this.getZ(), 40, 0.6, 0.4, 0.6, 0.15);
		this.discard();
	}

	public double serverSpeed() {
		return serverSpeed;
	}

	// ----------------------------------------------------------------------------------------- riding

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return this.getPassengers().isEmpty() && passenger instanceof Player;
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return this.getFirstPassenger() instanceof Player player ? player : null;
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		double feet = passenger.getVehicleAttachmentPoint(this).y;
		return new Vec3(0, feet + 0.06, 0);
	}

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
		super.positionRider(passenger, moveFunction);
		if (isDrivenHere() && passenger == this.getControllingPassenger() && (motion.grounded || RigClientHooks.cameraFollowsAir())) {
			// Follow ground turns, plus air rotations for the full-motion helmet view. Keep the mouse-look offset.
			passenger.setYRot(passenger.getYRot() + motion.steerYaw * RigClientHooks.cameraFollow());
			passenger.setYHeadRot(passenger.getYHeadRot() + motion.steerYaw * RigClientHooks.cameraFollow());
			motion.steerYaw = 0;
		}
		if (passenger instanceof LivingEntity living) {
			living.setYBodyRot(bodyYaw());
		}
	}

	/** Which way the rider's body faces: along the skis, or sideways on a board. */
	public float bodyYaw() {
		float yaw = this.getYRot();
		if (kind().isBoard()) {
			yaw += 90f;
		}
		return yaw;
	}

	@Override
	public void onPassengerTurned(Entity passenger) {
		if (passenger instanceof LivingEntity living) {
			living.setYBodyRot(bodyYaw());
		}
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (this.level().isClientSide()) {
			RigClientHooks.onRiderRemoved(this, passenger);
		}
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		return new Vec3(this.getX(), this.getY(), this.getZ());
	}

	// ----------------------------------------------------------------------------------------- misc entity

	@Override
	protected AABB makeBoundingBox(Vec3 position) {
		// Sit the box at the rider's waist so the server never sees it clipping the snow on stair-step slopes or banks.
		return this.getDimensions(this.getPose()).makeBoundingBox(position.add(0, RigMotion.BODY_CLEARANCE, 0));
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return false;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		return InteractionResult.PASS;
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return net.minecraft.world.entity.LinearInterpolationHandler.create(this, 2);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 160 * 160;
	}

	@Override
	public ItemStack getPickResult() {
		return getItem().copy();
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		input.read("Item", ItemStack.CODEC).ifPresent(this::setItem);
		input.getString("LastRider").ifPresent(s -> {
			try {
				lastRider = UUID.fromString(s);
			} catch (IllegalArgumentException ignored) {
			}
		});
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		ItemStack stack = getItem();
		if (!stack.isEmpty()) {
			output.store("Item", ItemStack.CODEC, stack);
		}
		if (lastRider != null) {
			output.putString("LastRider", lastRider.toString());
		}
	}

	public static void logState(RigEntity rig) {
		FreshTracks.LOG.debug("rig {} speed {} edge {} air {}", rig.getId(), rig.motion.speed(), rig.motion.edge, !rig.motion.grounded);
	}
}
