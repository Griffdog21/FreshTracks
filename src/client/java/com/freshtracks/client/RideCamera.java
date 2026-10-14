package com.freshtracks.client;

import com.freshtracks.FreshTracks;
import com.freshtracks.FreshTracksConfig;
import com.freshtracks.client.screen.RideSettingsScreen;
import com.freshtracks.rig.RigEntity;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.platform.InputConstants;

/** Per-tick ride feedback; render interpolation never changes the player's aim or base FOV setting. */
public final class RideCamera {
	private static KeyMapping cameraKey;
	private static KeyMapping settingsKey;
	private static CameraType cameraBefore;
	private static CameraType lastCamera;
	private static RigEntity activeRig;
	private static float speed, speedO, bank, bankO, crouch, crouchO, impact, impactO;

	private RideCamera() {}

	public static void init() {
		KeyMapping.Category category = KeyMapping.Category.register(FreshTracks.id("riding"));
		cameraKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.freshtracks.ride_camera", InputConstants.KEY_V, category));
		settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.freshtracks.ride_settings", InputConstants.KEY_J, category));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
			dispatcher.register(literal("ftride").executes(context -> {
				Minecraft.getInstance().schedule(() -> Minecraft.getInstance().gui.setScreen(new RideSettingsScreen()));
				return 1;
			}));
			var camera = literal("ftcamera");
			for (String mode : new String[]{"helmet", "chase", "keep"}) {
				camera.then(literal(mode).executes(context -> { setView(mode); return 1; }));
			}
			dispatcher.register(camera);
		});
	}

	public static boolean active(Minecraft mc) {
		return mc.player != null && mc.getCameraEntity() == mc.player
			&& mc.player.isAlive() && mc.player.getVehicle() instanceof RigEntity && !mc.player.isScoping();
	}

	public static void tick(Minecraft mc) {
		RigEntity rig = mc.player != null && mc.player.getVehicle() instanceof RigEntity r ? r : null;
		if (rig != activeRig) {
			if (activeRig != null && cameraBefore != null) mc.options.setCameraType(cameraBefore);
			cameraBefore = rig == null ? null : mc.options.getCameraType();
			activeRig = rig;
			speed = speedO = bank = bankO = crouch = crouchO = impact = impactO = 0;
			if (rig != null) {
				applyView(mc);
				mc.player.sendOverlayMessage(Component.translatable("ride.freshtracks.hint", cameraKey.getTranslatedKeyMessage(), settingsKey.getTranslatedKeyMessage()));
			}
			lastCamera = mc.options.getCameraType();
		}
		// F5 remains usable, and choosing helmet/chase with it is remembered on the next click-in.
		if (rig != null && lastCamera != mc.options.getCameraType()) {
			if (!"keep".equals(FreshTracksConfig.rideCamera)) {
				FreshTracksConfig.rideCamera = mc.options.getCameraType().isFirstPerson() ? "helmet" : "chase";
				FreshTracksConfig.save();
			}
			lastCamera = mc.options.getCameraType();
		}
		while (cameraKey.consumeClick()) {
			if (rig != null && mc.gui.screen() == null) setView(mc.options.getCameraType().isFirstPerson() ? "chase" : "helmet");
		}
		while (settingsKey.consumeClick()) {
			if (mc.gui.screen() == null) mc.gui.setScreen(new RideSettingsScreen());
		}
		if (rig == null || mc.isPaused()) return;
		speedO = speed; bankO = bank; crouchO = crouch; impactO = impact;
		// Horizontal travel avoids fake speed surges from Minecraft's block-height snow steps.
		speed += ((float) Mth.clamp(rig.visualSpeed(), 0, 2.1) - speed) * 0.22f;
		float targetBank = rig.motion.grounded && rig.motion.tow == null ? Mth.clamp(rig.motion.lean * 0.10f, -4.5f, 4.5f) : 0;
		bank += (targetBank - bank) * 0.28f;
		float targetCrouch = rig.motion.grounded ? (rig.motion.tuck ? 0.20f : 0) + rig.motion.jumpCharge * 0.10f : 0;
		crouch += (targetCrouch - crouch) * 0.25f;
		impact += (ClientRig.INSTANCE.shake - impact) * 0.35f;
	}

	public static void setView(String mode) {
		FreshTracksConfig.rideCamera = mode;
		FreshTracksConfig.autoThirdPerson = "chase".equals(mode);
		FreshTracksConfig.save();
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.player.getVehicle() instanceof RigEntity) applyView(mc);
		lastCamera = mc.options.getCameraType();
	}

	private static void applyView(Minecraft mc) {
		if ("helmet".equals(FreshTracksConfig.rideCamera)) mc.options.setCameraType(CameraType.FIRST_PERSON);
		else if ("chase".equals(FreshTracksConfig.rideCamera)) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
	}

	public static float fovBoost(float partialTicks) {
		Minecraft mc = Minecraft.getInstance();
		if (!active(mc) || "off".equals(FreshTracksConfig.rideEffects)) return 0;
		float t = Mth.clamp((Mth.lerp(partialTicks, speedO, speed) - 0.15f) / 1.35f, 0, 1);
		float boost = 16 * t * t * (3 - 2 * t);
		if ("reduced".equals(FreshTracksConfig.rideEffects)) boost *= 0.3f;
		return boost * mc.options.fovEffectScale().get().floatValue();
	}

	public static float bank(float partialTicks) {
		if (!"full".equals(FreshTracksConfig.rideEffects)) return 0;
		return Mth.lerp(partialTicks, bankO, bank);
	}

	public static float lower(float partialTicks) {
		if (!"full".equals(FreshTracksConfig.rideEffects)) return 0;
		return 0.12f + Mth.lerp(partialTicks, crouchO, crouch) + Mth.lerp(partialTicks, impactO, impact) * 0.045f;
	}
}
