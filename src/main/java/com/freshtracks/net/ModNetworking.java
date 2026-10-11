package com.freshtracks.net;

import com.freshtracks.race.TrailMarkerBlock;
import com.freshtracks.resort.TrailSignBlock;
import com.freshtracks.resort.TrailSignBlockEntity;
import com.freshtracks.rig.RigEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

public final class ModNetworking {
	/** XP budget per player per minute from tricks, so nobody farms levels by bunny hopping. */
	private static final int XP_PER_MINUTE = 120;
	private static final Map<UUID, long[]> XP_WINDOW = new HashMap<>();

	private ModNetworking() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(RiderStatePayload.TYPE, RiderStatePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TrickPayload.TYPE, TrickPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(WipeoutPayload.TYPE, WipeoutPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TrailSignPayload.TYPE, TrailSignPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(LiftEditPayload.TYPE, LiftEditPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LiftSyncPayload.TYPE, LiftSyncPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LiftPhasePayload.TYPE, LiftPhasePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(WeatherPayload.TYPE, WeatherPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RaceTimerPayload.TYPE, RaceTimerPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TrailMapPayload.TYPE, TrailMapPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(RiderStatePayload.TYPE, (payload, context) -> {
			if (context.player().getVehicle() instanceof RigEntity rig && rig.getId() == payload.rigId()) {
				rig.applyRiderState(payload.lean(), payload.flip(), payload.flags(), payload.grab());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(TrickPayload.TYPE, (payload, context) -> onTrick(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(TrailSignPayload.TYPE, (payload, context) -> onTrailSign(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(LiftEditPayload.TYPE, (payload, context) -> com.freshtracks.lift.LiftManager.edit(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(WipeoutPayload.TYPE, (payload, context) -> {
			if (context.player().getVehicle() instanceof RigEntity rig) {
				rig.yardSale(context.player(), Mth.clamp(payload.severity(), 0f, 1.5f));
			}
		});
	}

	private static void onTrailSign(ServerPlayer player, TrailSignPayload payload) {
		ServerLevel level = player.level();
		BlockPos pos = payload.pos();
		if (player.distanceToSqr(Vec3.atCenterOf(pos)) > 10 * 10 || !player.mayBuild() || !level.mayInteract(player, pos)) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof TrailSignBlock) || !(level.getBlockEntity(pos) instanceof TrailSignBlockEntity sign)) {
			return;
		}
		TrailMarkerBlock.Symbol[] symbols = TrailMarkerBlock.Symbol.values();
		TrailMarkerBlock.Symbol symbol = symbols[Mth.clamp(payload.symbol(), 0, symbols.length - 1)];
		if (state.getValue(TrailSignBlock.SYMBOL) != symbol) {
			level.setBlock(pos, state.setValue(TrailSignBlock.SYMBOL, symbol), Block.UPDATE_ALL);
		}
		sign.setName(payload.name());
	}

	private static void onTrick(ServerPlayer player, TrickPayload payload) {
		if (!(player.getVehicle() instanceof RigEntity) && player.getVehicle() != null) {
			return;
		}
		int points = Mth.clamp(payload.points(), 0, 100_000);
		if (payload.airTicks() >= 30) {
			com.freshtracks.advancement.ModCriteria.fire(player, "big_air");
		}
		if (points >= 1000) {
			com.freshtracks.advancement.ModCriteria.fire(player, "trick_1000");
		}
		long now = player.level().getGameTime();
		long[] window = XP_WINDOW.computeIfAbsent(player.getUUID(), k -> new long[]{now, 0});
		if (now - window[0] > 1200) {
			window[0] = now;
			window[1] = 0;
		}
		int xp = Mth.clamp(points / 150, 1, 30);
		xp = (int) Math.min(xp, XP_PER_MINUTE - window[1]);
		if (xp > 0 && com.freshtracks.FreshTracksConfig.trickXp) {
			window[1] += xp;
			player.giveExperiencePoints(xp);
		}
		if (points >= 400) {
			String name = payload.name().length() > 60 ? payload.name().substring(0, 60) : payload.name();
			Component shout = Component.literal(player.getPlainTextName() + " ").withStyle(ChatFormatting.AQUA)
				.append(Component.literal(name).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
				.append(Component.literal("  " + String.format("%,d", points)).withStyle(ChatFormatting.GOLD));
			for (ServerPlayer other : player.level().players()) {
				if (other != player && other.distanceToSqr(player) < 96 * 96) {
					other.sendSystemMessage(shout, true);
				}
			}
		}
	}
}
