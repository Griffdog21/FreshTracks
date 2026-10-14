package com.freshtracks.client;

import com.freshtracks.FreshTracks;
import com.freshtracks.client.dev.DevBridge;
import com.freshtracks.client.hud.TrickHud;
import com.freshtracks.client.particle.SnowParticles;
import com.freshtracks.client.weather.ClientWeather;
import com.freshtracks.client.weather.WeatherHud;
import com.freshtracks.registry.ModParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import com.freshtracks.client.lift.ClientLifts;
import com.freshtracks.client.lift.LiftModels;
import com.freshtracks.client.lift.LiftRenderer;
import com.freshtracks.client.lift.TerminalModels;
import com.freshtracks.client.lift.TerminalRenderer;
import com.freshtracks.client.lift.TowerHeadRenderer;
import com.freshtracks.lift.ChairStyle;
import com.freshtracks.registry.ModBlockEntities;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.entity.NoopRenderer;
import com.freshtracks.client.render.RigModels;
import com.freshtracks.client.render.RigRenderer;
import com.freshtracks.registry.ModEntities;
import com.freshtracks.rig.RigClientHooks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

public class FreshTracksClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModelLayerRegistry.registerModelLayer(RigModels.SKIS, RigModels::skis);
		ModelLayerRegistry.registerModelLayer(RigModels.BOARD, RigModels::board);
		EntityRendererRegistry.register(ModEntities.RIG, RigRenderer::new);
		EntityRendererRegistry.register(ModEntities.LIFT_SEAT, NoopRenderer::new);
		for (ChairStyle style : ChairStyle.values()) {
			ModelLayerRegistry.registerModelLayer(LiftModels.CHAIRS.get(style), () -> LiftModels.chair(style));
		}
		ModelLayerRegistry.registerModelLayer(LiftModels.GONDOLA_GLASS, LiftModels::gondolaGlass);
		ModelLayerRegistry.registerModelLayer(LiftModels.BULLWHEEL, LiftModels::bullwheel);
		ModelLayerRegistry.registerModelLayer(TerminalModels.HOUSING, TerminalModels::housing);
		ModelLayerRegistry.registerModelLayer(TerminalModels.MID_HOUSING, TerminalModels::midHousing);
		ModelLayerRegistry.registerModelLayer(TerminalModels.FIXED, TerminalModels::fixed);
		ModelLayerRegistry.registerModelLayer(LiftModels.TOWER_HEAD, LiftModels::towerHead);
		BlockEntityRendererRegistry.register(ModBlockEntities.LIFT_TERMINAL, TerminalRenderer::new);
		BlockEntityRendererRegistry.register(ModBlockEntities.LIFT_TOWER_HEAD, TowerHeadRenderer::new);
		BlockEntityRendererRegistry.register(ModBlockEntities.TRAIL_SIGN, com.freshtracks.client.render.TrailSignRenderer::new);
		BlockEntityRendererRegistry.register(ModBlockEntities.TRAIL_MAP, com.freshtracks.client.render.TrailMapRenderer::new);
		com.freshtracks.client.render.TrailMaps.init();
		com.freshtracks.lift.block.LiftTerminalBlock.openPanel = pos -> {
			ClientLifts.Entry entry = ClientLifts.byTerminal(pos);
			if (entry == null) {
				return false;
			}
			net.minecraft.client.Minecraft.getInstance().gui.setScreen(new com.freshtracks.client.screen.LiftPanelScreen(pos, entry.line));
			return true;
		};
		com.freshtracks.resort.TrailSignBlock.openEditor = pos -> {
			net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
			if (mc.level != null && mc.level.getBlockEntity(pos) instanceof com.freshtracks.resort.TrailSignBlockEntity sign) {
				var symbol = mc.level.getBlockState(pos).getValue(com.freshtracks.resort.TrailSignBlock.SYMBOL);
				mc.execute(() -> mc.gui.setScreen(new com.freshtracks.client.screen.TrailSignScreen(pos, sign.name(), symbol)));
			}
		};
		LevelRenderEvents.COLLECT_SUBMITS.register(LiftRenderer::collect);
		LevelRenderEvents.COLLECT_SUBMITS.register(com.freshtracks.client.weather.Aurora::collect);
		ClientLifts.init();
		RigClientHooks.install(ClientRig.INSTANCE);
		RideCamera.init();

		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, FreshTracks.id("weather_overlay"), WeatherHud::renderOverlay);
		HudElementRegistry.attachElementAfter(VanillaHudElements.SUBTITLES, FreshTracks.id("tricks"), TrickHud::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.SUBTITLES, FreshTracks.id("weather"), WeatherHud::renderHud);
		HudElementRegistry.attachElementAfter(VanillaHudElements.SUBTITLES, FreshTracks.id("race"), com.freshtracks.client.hud.RaceHud::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.SUBTITLES, FreshTracks.id("lift"), com.freshtracks.client.hud.LiftHud::render);
		// One line of help under our blocks' names (tooltip.freshtracks.<block>, resort signs share one).
		net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem)) {
				return;
			}
			net.minecraft.resources.Identifier id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
			if (!id.getNamespace().equals("freshtracks")) {
				return;
			}
			String key = blockItem.getBlock() instanceof com.freshtracks.resort.ResortSignBlock ? "tooltip.freshtracks.resort_sign" : "tooltip.freshtracks." + id.getPath();
			if (net.minecraft.locale.Language.getInstance().has(key)) {
				lines.add(Math.min(1, lines.size()), net.minecraft.network.chat.Component.translatable(key).withStyle(net.minecraft.ChatFormatting.GRAY));
			}
		});
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(com.freshtracks.net.RaceTimerPayload.TYPE,
			(payload, context) -> com.freshtracks.client.hud.RaceHud.handle(payload));
		ParticleProviderRegistry.getInstance().register(ModParticles.SNOW_SPRAY, SnowParticles.Spray.Provider::new);
		ParticleProviderRegistry.getInstance().register(ModParticles.WIND_SNOW, SnowParticles.WindSnow.Provider::new);
		ParticleProviderRegistry.getInstance().register(ModParticles.SPARKLE, SnowParticles.Sparkle.Provider::new);
		ClientWeather.init();

		// Lifts move first, so seats (and the riders on them) are placed from this tick's rope position and stay
		// glued to the chairs drawn from it; ticking lifts after the entities left riders one tick behind.
		ClientTickEvents.START_CLIENT_TICK.register(ClientLifts::tick);
		ClientTickEvents.START_CLIENT_TICK.register(DevBridge::startTick);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			ClientRig.INSTANCE.tickCamera(mc);
			ClientWeather.tick(mc);
			com.freshtracks.client.lift.LiftSounds.tick(mc);
			com.freshtracks.client.hud.LiftHud.tick(mc);
			TrickHud.tick();
			com.freshtracks.client.hud.RaceHud.tick();
			com.freshtracks.client.weather.Aurora.tick(mc);
			DevBridge.endTick(mc);
		});
		DevBridge.start();
	}
}
