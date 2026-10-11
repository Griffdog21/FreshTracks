package com.freshtracks.client.screen;

import com.freshtracks.lift.ChairStyle;
import com.freshtracks.lift.LiftLine;
import com.freshtracks.net.LiftEditPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The lift control panel, opened by using a lift terminal: name, lift type, rope speed, carrier spacing and
 * open/closed. The server checks the player is allowed to change lifts.
 */
public class LiftPanelScreen extends Screen {
	private static final double MIN_SPEED = 0.05;
	private static final double MAX_SPEED = 0.45;
	private static final double MIN_SPACING = 5;
	private static final double MAX_SPACING = 40;

	private final BlockPos terminal;
	private final LiftLine line;
	private String name;
	private ChairStyle style;
	private double speed;
	private double spacing;
	private boolean running;
	private EditBox nameBox;
	private ValueSlider speedSlider;
	private ValueSlider spacingSlider;

	public LiftPanelScreen(BlockPos terminal, LiftLine line) {
		super(Component.translatable("screen.freshtracks.lift"));
		this.terminal = terminal;
		this.line = line;
		this.name = line.name;
		this.style = line.style;
		this.speed = line.speed;
		this.spacing = line.spacing;
		this.running = line.running;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - 110;
		int y = this.height / 2 - 92;
		this.nameBox = new EditBox(this.font, left, y + 16, 220, 20, Component.translatable("screen.freshtracks.lift.name"));
		this.nameBox.setMaxLength(48);
		this.nameBox.setValue(this.name);
		this.nameBox.setResponder(value -> this.name = value);
		this.addRenderableWidget(this.nameBox);

		this.addRenderableWidget(CycleButton.builder(ChairStyle::displayName, this.style)
			.withValues(ChairStyle.values())
			.create(left, y + 44, 220, 20, Component.translatable("screen.freshtracks.lift.type"), (button, value) -> {
				// a new lift type starts at its usual speed and spacing; tweak from there
				this.style = value;
				this.speed = value.defaultSpeed;
				this.spacing = value.defaultSpacing;
				this.speedSlider.set(fraction(this.speed, MIN_SPEED, MAX_SPEED));
				this.spacingSlider.set(fraction(this.spacing, MIN_SPACING, MAX_SPACING));
			}));

		this.speedSlider = this.addRenderableWidget(new ValueSlider(left, y + 70, 220, fraction(this.speed, MIN_SPEED, MAX_SPEED)) {
			@Override
			protected void updateMessage() {
				double ms = speedFromSlider() * 20;
				this.setMessage(Component.translatable("screen.freshtracks.lift.speed", String.format("%.1f", ms), Math.round(ms * 3.6)));
			}

			@Override
			protected void applyValue() {
				LiftPanelScreen.this.speed = speedFromSlider();
			}

			private double speedFromSlider() {
				return Math.round(Mth.lerp(this.value, MIN_SPEED, MAX_SPEED) * 200) / 200.0;
			}
		});
		this.spacingSlider = this.addRenderableWidget(new ValueSlider(left, y + 96, 220, fraction(this.spacing, MIN_SPACING, MAX_SPACING)) {
			@Override
			protected void updateMessage() {
				this.setMessage(Component.translatable("screen.freshtracks.lift.spacing", Math.round(Mth.lerp(this.value, MIN_SPACING, MAX_SPACING))));
			}

			@Override
			protected void applyValue() {
				LiftPanelScreen.this.spacing = Math.round(Mth.lerp(this.value, MIN_SPACING, MAX_SPACING));
			}
		});
		this.addRenderableWidget(CycleButton.booleanBuilder(
				Component.translatable("message.freshtracks.lift.running"), Component.translatable("message.freshtracks.lift.closed"), this.running)
			.create(left, y + 122, 220, 20, Component.translatable("screen.freshtracks.lift.status"), (button, value) -> this.running = value));

		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.save()).bounds(left, y + 156, 108, 20).build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).bounds(left + 112, y + 156, 108, 20).build());
		this.setInitialFocus(this.nameBox);
	}

	private static double fraction(double value, double min, double max) {
		return Mth.clamp((value - min) / (max - min), 0, 1);
	}

	private void save() {
		ClientPlayNetworking.send(new LiftEditPayload(this.terminal, this.name, this.style.ordinal(), (float) this.speed, (float) this.spacing, this.running));
		this.onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int y = this.height / 2 - 92;
		graphics.centeredText(this.font, this.title, this.width / 2, y, 0xFFFFFFFF);
		// what the current settings mean for riders
		double rideTicks = this.line.upLength() / Math.max(0.01, this.speed);
		int seconds = (int) Math.round(rideTicks / 20);
		Component info = Component.translatable("screen.freshtracks.lift.info", Math.round(this.line.upLength()), this.line.towers.size(),
			String.format("%d:%02d", seconds / 60, seconds % 60));
		graphics.centeredText(this.font, info, this.width / 2, y + 146 - 1 + 34, 0xFFB8C4D0);
		Component kind = Component.translatable("screen.freshtracks.lift.grip." + this.style.grip.name().toLowerCase(java.util.Locale.ROOT));
		graphics.centeredText(this.font, kind, this.width / 2, y + 146 + 46, 0xFF8FA3B5);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** A 0..1 slider that formats its own label. */
	abstract static class ValueSlider extends AbstractSliderButton {
		ValueSlider(int x, int y, int width, double value) {
			super(x, y, width, 20, Component.empty(), value);
			this.updateMessage();
		}

		void set(double value) {
			this.value = Mth.clamp(value, 0, 1);
			this.applyValue();
			this.updateMessage();
		}
	}
}
