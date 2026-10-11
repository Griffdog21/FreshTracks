package com.freshtracks.client.screen;

import com.freshtracks.net.TrailSignPayload;
import com.freshtracks.race.TrailMarkerBlock;
import com.freshtracks.resort.TrailSignBlockEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Name a run and pick its difficulty symbol. */
public class TrailSignScreen extends Screen {
	private final BlockPos pos;
	private String name;
	private TrailMarkerBlock.Symbol symbol;
	private EditBox nameBox;

	public TrailSignScreen(BlockPos pos, String name, TrailMarkerBlock.Symbol symbol) {
		super(Component.translatable("screen.freshtracks.trail_sign"));
		this.pos = pos;
		this.name = name;
		this.symbol = symbol;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - 110;
		int y = this.height / 2 - 60;
		this.nameBox = new EditBox(this.font, left, y + 16, 220, 20, Component.translatable("screen.freshtracks.trail_sign.name"));
		this.nameBox.setMaxLength(TrailSignBlockEntity.MAX_NAME);
		this.nameBox.setValue(this.name);
		this.nameBox.setHint(Component.translatable("screen.freshtracks.trail_sign.hint"));
		this.nameBox.setResponder(value -> this.name = value);
		this.addRenderableWidget(this.nameBox);
		this.addRenderableWidget(CycleButton.builder((TrailMarkerBlock.Symbol s) -> Component.literal(s.label), this.symbol)
			.withValues(TrailMarkerBlock.Symbol.values())
			.create(left, y + 44, 220, 20, Component.translatable("screen.freshtracks.trail_sign.symbol"), (button, value) -> this.symbol = value));
		this.addRenderableWidget(Button.builder(Component.literal("← " + Component.translatable("screen.freshtracks.trail_sign.arrow").getString()),
			button -> this.arrow(true)).bounds(left, y + 70, 108, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal(Component.translatable("screen.freshtracks.trail_sign.arrow").getString() + " →"),
			button -> this.arrow(false)).bounds(left + 112, y + 70, 108, 20).build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.save()).bounds(left, y + 104, 108, 20).build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).bounds(left + 112, y + 104, 108, 20).build());
		this.setInitialFocus(this.nameBox);
	}

	private void arrow(boolean left) {
		String bare = this.name.replace("←", "").replace("→", "").strip();
		this.name = left ? "← " + bare : bare + " →";
		this.nameBox.setValue(this.name);
	}

	private void save() {
		ClientPlayNetworking.send(new TrailSignPayload(this.pos, this.name, this.symbol.ordinal()));
		this.onClose();
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (event.isConfirmation() && this.nameBox.isFocused()) {
			this.save();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 60, 0xFFFFFFFF);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
