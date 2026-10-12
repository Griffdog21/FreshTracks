package com.freshtracks.client.lift;

import com.freshtracks.FreshTracks;
import com.freshtracks.lift.ChairStyle;
import com.freshtracks.lift.LiftLine;
import com.freshtracks.lift.Livery;
import com.freshtracks.lift.block.LiftTerminalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Unit;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A lift terminal: a detachable lift's housing up on its columns with the lift's name along both sides, or a fixed-grip
 * lift's bullwheel under its hood, turned to face along the station, with the bullwheel spinning with the rope.
 */
public class TerminalRenderer implements BlockEntityRenderer<LiftTerminalBlockEntity, TerminalRenderer.State> {
	private static final Identifier WHEEL_TEXTURE = FreshTracks.id("textures/entity/lift/bullwheel.png");
	private static final Map<Livery, Identifier> HOUSING_TEXTURES = new EnumMap<>(Livery.class);
	private static final Map<Livery, Identifier> FIXED_TEXTURES = new EnumMap<>(Livery.class);

	static {
		for (Livery livery : Livery.values()) {
			if (livery != Livery.AUTO) {
				HOUSING_TEXTURES.put(livery, FreshTracks.id("textures/entity/lift/housing_" + livery.getSerializedName() + ".png"));
				FIXED_TEXTURES.put(livery, FreshTracks.id("textures/entity/lift/fixed_" + livery.getSerializedName() + ".png"));
			}
		}
	}

	private final Model.Simple housingDrive;
	private final Model.Simple housingReturn;
	private final Model.Simple fixedDrive;
	private final Model.Simple fixedReturn;
	private final Model.Simple wheel;
	private final Font font;

	public static class State extends BlockEntityRenderState {
		float yaw;
		float wheelYaw;
		float wheelAngle;
		boolean housing;
		boolean drive;
		Livery livery = Livery.GRAY;
		@Nullable String name;
		int housingLight;
	}

	public TerminalRenderer(BlockEntityRendererProvider.Context context) {
		this.housingDrive = new Model.Simple(context.bakeLayer(TerminalModels.HOUSING), RenderTypes::entityCutout);
		this.housingReturn = new Model.Simple(hideDrive(context.bakeLayer(TerminalModels.HOUSING)), RenderTypes::entityCutout);
		this.fixedDrive = new Model.Simple(context.bakeLayer(TerminalModels.FIXED), RenderTypes::entityCutout);
		this.fixedReturn = new Model.Simple(hideDrive(context.bakeLayer(TerminalModels.FIXED)), RenderTypes::entityCutout);
		this.wheel = new Model.Simple(context.bakeLayer(LiftModels.BULLWHEEL), RenderTypes::entityCutout);
		this.font = context.font();
	}

	private static ModelPart hideDrive(ModelPart root) {
		root.getChild("drive").visible = false;
		return root;
	}

	public static Identifier housingTexture(Livery livery) {
		return HOUSING_TEXTURES.getOrDefault(livery, HOUSING_TEXTURES.get(Livery.GRAY));
	}

	/** Detachables (chairs and cabins) get a housing; fixed grips and surface lifts the bullwheel under a hood. */
	public static boolean housed(ChairStyle style) {
		return style.detachable() || style.cabin() || style.pulse();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(LiftTerminalBlockEntity be, State state, float partialTicks, Vec3 cameraPosition,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
		// light the housing by the open air above the terminal block, not the pad itself
		state.housingLight = be.getLevel() != null ? LightCoordsUtil.getLightCoords(be.getLevel(), be.getBlockPos().above(4)) : state.lightCoords;
		ClientLifts.Entry entry = ClientLifts.byTerminal(be.getBlockPos());
		if (entry != null) {
			LiftLine line = entry.line;
			boolean top = line.top.equals(be.getBlockPos());
			Vec3 dir = line.stationDirection(top);
			state.yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
			Vec3 up = line.uphillDirection();
			state.wheelYaw = (float) Math.toDegrees(Math.atan2(-up.x, up.z));
			state.wheelAngle = (float) Math.toDegrees(entry.phase(partialTicks) / LiftLine.TRACK_HALF);
			if (top) {
				state.wheelAngle = -state.wheelAngle;
			}
			state.housing = housed(line.style);
			state.livery = line.livery();
			// modern detachables drive from the top; old fixed grips mostly from the bottom
			state.drive = state.housing == top;
			state.name = line.name;
		} else {
			state.yaw = be.getBlockState().getValue(com.freshtracks.lift.block.LiftTerminalBlock.FACING).toYRot();
			state.wheelYaw = state.yaw;
			state.wheelAngle = 0;
			state.housing = false;
			state.livery = Livery.GRAY;
			state.drive = false;
			state.name = null;
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.5f, 0f, 0.5f);
		poseStack.rotateDegrees(Axis.YP, 180f - state.yaw);
		poseStack.pushPose();
		poseStack.scale(-1f, -1f, 1f);
		Model.Simple model = state.housing ? (state.drive ? housingDrive : housingReturn) : (state.drive ? fixedDrive : fixedReturn);
		Identifier texture = (state.housing ? HOUSING_TEXTURES : FIXED_TEXTURES).get(state.livery);
		collector.submitModel(model, Unit.INSTANCE, poseStack, texture, state.housingLight, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
		if (state.housing && state.name != null && !state.name.isBlank()) {
			submitName(state, poseStack, collector, TerminalModels.NAME_ALONG, TerminalModels.NOSE_START - 20f);
		}
		poseStack.popPose();

		poseStack.pushPose();
		poseStack.translate(0.5f, (float) LiftLine.TERMINAL_ROPE, 0.5f);
		poseStack.rotateDegrees(Axis.YP, 180f - state.wheelYaw + state.wheelAngle);
		poseStack.scale(-1f, -1f, 1f);
		collector.submitModel(wheel, Unit.INSTANCE, poseStack, WHEEL_TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}

	/**
	 * The lift's name painted along both sides of the housing's fascia, the way the real ones carry it: capitals on the
	 * grey and silver housings, as written on the charcoal ones. Expects the pose turned to face along the station.
	 */
	void submitName(State state, PoseStack poseStack, SubmitNodeCollector collector, float along, float room) {
		submitName(font, state.name, state.livery, state.housingLight, poseStack, collector, along, room);
	}

	static void submitName(Font font, String name, Livery livery, int light, PoseStack poseStack, SubmitNodeCollector collector, float along, float room) {
		String text = livery == Livery.CHARCOAL ? name : name.toUpperCase(Locale.ROOT);
		FormattedCharSequence line = Component.literal(text).getVisualOrderText();
		int width = Math.max(1, font.width(line));
		// letters up to three quarters of a block tall, shrunk to fit the straight part of the side
		float scale = Math.min(0.75f / 9f, room / 16f / width);
		int color = livery == Livery.SILVER ? 0xFF2B3036 : 0xFFF4F6F8;
		for (int side = -1; side <= 1; side += 2) {
			poseStack.pushPose();
			poseStack.translate(side * (TerminalModels.W / 16f + 0.015f), TerminalModels.NAME_HEIGHT / 16f, -along / 16f);
			poseStack.rotateDegrees(Axis.YP, side * 90f);
			poseStack.scale(scale, -scale, scale);
			collector.submitText(poseStack, -width / 2f, -4f, line, false, Font.DisplayMode.POLYGON_OFFSET, light, color, 0, 0);
			poseStack.popPose();
		}
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 256;
	}
}
