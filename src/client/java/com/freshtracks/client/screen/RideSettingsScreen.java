package com.freshtracks.client.screen;

import com.freshtracks.FreshTracksConfig;
import com.freshtracks.client.RideCamera;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Small in-game settings panel; preferences apply immediately and persist across sessions. */
public final class RideSettingsScreen extends Screen {
	public RideSettingsScreen() { super(Component.translatable("screen.freshtracks.riding")); }

	@Override protected void init() {
		int x = width / 2 - 140, y = height / 2 - 70;
		addRenderableWidget(CycleButton.builder((String s) -> Component.translatable("ride.freshtracks.camera." + s), FreshTracksConfig.rideCamera)
			.withValues("helmet", "chase", "keep")
			.create(x, y, 280, 20, Component.translatable("ride.freshtracks.camera"), (button, value) -> RideCamera.setView(value)));
		addRenderableWidget(CycleButton.builder((String s) -> Component.translatable("ride.freshtracks.effects." + s), FreshTracksConfig.rideEffects)
			.withValues("full", "reduced", "off")
			.create(x, y + 26, 280, 20, Component.translatable("ride.freshtracks.effects"), (button, value) -> {
				FreshTracksConfig.rideEffects = value; FreshTracksConfig.save();
			}));
		addRenderableWidget(CycleButton.builder((Double d) -> Component.literal(Math.round(d * 100) + "%"), FreshTracksConfig.rideWindVolume)
			.withValues(1.0, 0.5, 0.0)
			.create(x, y + 52, 280, 20, Component.translatable("ride.freshtracks.wind"), (button, value) -> {
				FreshTracksConfig.rideWindVolume = value; FreshTracksConfig.save();
			}));
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).bounds(x + 40, y + 118, 200, 20).build());
	}

	@Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, title, width / 2, height / 2 - 96, 0xFFFFFFFF);
		graphics.centeredText(font, Component.translatable("ride.freshtracks.comfort"), width / 2, height / 2 + 18, 0xFFC2D9E8);
	}
}
