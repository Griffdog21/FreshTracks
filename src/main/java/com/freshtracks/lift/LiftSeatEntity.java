package com.freshtracks.lift;

import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * An invisible seat on a lift chair that one rider sits on. It follows the chair exactly (position is computed from
 * the lift's rope position on both client and server), exists only while occupied, and is never saved.
 */
public class LiftSeatEntity extends Entity {
	private static final EntityDataAccessor<Integer> DATA_LINE = SynchedEntityData.defineId(LiftSeatEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_CHAIR = SynchedEntityData.defineId(LiftSeatEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_SEAT = SynchedEntityData.defineId(LiftSeatEntity.class, EntityDataSerializers.INT);

	/** Client hook: where is this seat right now (null if the lift isn't known yet). */
	public interface Locator {
		@Nullable Vec3 locate(LiftSeatEntity seat, float partialTick);
	}

	public static Locator clientLocator = (seat, partial) -> null;

	private UUID lineId = new UUID(0, 0);
	private ItemStack rememberedRig = ItemStack.EMPTY;
	private int emptyTicks;

	public LiftSeatEntity(EntityType<? extends LiftSeatEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_LINE, -1);
		builder.define(DATA_CHAIR, 0);
		builder.define(DATA_SEAT, 0);
	}

	public void setup(UUID lineId, int netId, int chair, int seat) {
		this.lineId = lineId;
		this.entityData.set(DATA_LINE, netId);
		this.entityData.set(DATA_CHAIR, chair);
		this.entityData.set(DATA_SEAT, seat);
	}

	public UUID lineId() {
		return lineId;
	}

	public int lineNetId() {
		return this.entityData.get(DATA_LINE);
	}

	public int chairIndex() {
		return this.entityData.get(DATA_CHAIR);
	}

	public int seatIndex() {
		return this.entityData.get(DATA_SEAT);
	}

	public void setRememberedRig(ItemStack stack) {
		this.rememberedRig = stack.copy();
	}

	public ItemStack rememberedRig() {
		return rememberedRig;
	}

	/**
	 * Seats are placed on the client from its own smooth copy of the rope (see ClientLifts). Position updates from
	 * the server arrive a little late and would yank the seat back each time: ignore them.
	 */
	@Override
	protected net.minecraft.world.entity.InterpolationHandler createInterpolationHandler() {
		return new net.minecraft.world.entity.InterpolationHandler.NoOpInterpolationHandler() {
			@Override
			public boolean interpolateTo(net.minecraft.world.entity.PositionPath position, float yRot, float xRot, boolean hasRotation) {
				return true;
			}
		};
	}

	/** Ticks sneak has to be held to jump off a chair mid-ride. */
	public static final int BAIL_OUT_TICKS = 20;
	private int sneakTicks;

	/** True once the rider has held sneak long enough to mean it. */
	public boolean bailOutHeld() {
		return sneakTicks >= BAIL_OUT_TICKS;
	}

	public int sneakTicks() {
		return sneakTicks;
	}

	@Override
	public void tick() {
		super.tick();
		Vec3 target = locate(1f);
		if (target != null) {
			this.setPos(target);
		}
		sneakTicks = this.getFirstPassenger() != null && this.getFirstPassenger().isShiftKeyDown() ? sneakTicks + 1 : 0;
		if (!this.level().isClientSide()) {
			if (!this.isVehicle()) {
				if (++emptyTicks > 2) {
					this.discard();
				}
			} else {
				emptyTicks = 0;
			}
		}
	}

	public @Nullable Vec3 locate(float partialTick) {
		if (this.level() instanceof ServerLevel server) {
			LiftLine line = LiftManager.line(server, lineId);
			if (line == null) {
				this.ejectPassengers();
				this.discard();
				return null;
			}
			return line.seatPos(LiftManager.phase(server, lineId), chairIndex(), seatIndex());
		}
		return clientLocator.locate(this, partialTick);
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		return new Vec3(0, passenger.getVehicleAttachmentPoint(this).y, 0);
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		// Jumping off the lift: you drop straight down. Brave.
		return new Vec3(this.getX(), this.getY() - 0.4, this.getZ());
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return this.getPassengers().isEmpty();
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		return InteractionResult.PASS;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
