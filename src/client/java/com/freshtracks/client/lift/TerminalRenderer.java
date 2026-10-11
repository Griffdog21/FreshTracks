package com.freshtracks.client.lift;

import com.freshtracks.FreshTracks;
import com.freshtracks.lift.LiftLine;
import com.freshtracks.lift.block.LiftTerminalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Bullwheel, posts and roof of a lift terminal, turned to face up the line and spinning with the rope. */
public class TerminalRenderer implements BlockEntityRenderer<LiftTerminalBlockEntity, TerminalRenderer.State> {
	private static final Identifier FRAME_TEXTURE = FreshTracks.id("textures/entity/lift/terminal.png");
	private static final Identifier WHEEL_TEXTURE = FreshTracks.id("textures/entity/lift/bullwheel.png");
	private final Model.Simple frame;
	private final Model.Simple wheel;

	public static class State extends BlockEntityRenderState {
		float yaw;
		float wheelAngle;
		boolean linked;
	}

	public TerminalRenderer(BlockEntityRendererProvider.Context context) {
		this.frame = new Model.Simple(context.bakeLayer(LiftModels.TERMINAL), RenderTypes::entityCutout);
		this.wheel = new Model.Simple(context.bakeLayer(LiftModels.BULLWHEEL), RenderTypes::entityCutout);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(LiftTerminalBlockEntity be, State state, float partialTicks, Vec3 cameraPosition,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
		ClientLifts.Entry entry = ClientLifts.byTerminal(be.getBlockPos());
		state.linked = entry != null;
		if (entry != null) {
			Vec3 up = entry.line.uphillDirection();
			state.yaw = (float) Math.toDegrees(Math.atan2(-up.x, up.z));
			state.wheelAngle = (float) Math.toDegrees(entry.phase(partialTicks) / LiftLine.TRACK_HALF);
			if (entry.line.top.equals(be.getBlockPos())) {
				state.wheelAngle = -state.wheelAngle;
			}
		} else {
			state.yaw = be.getBlockState().getValue(com.freshtracks.lift.block.LiftTerminalBlock.FACING).toYRot();
			state.wheelAngle = 0;
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.5f, 0f, 0.5f);
		poseStack.rotateDegrees(Axis.YP, 180f - state.yaw);
		poseStack.scale(-1f, -1f, 1f);
		collector.submitModel(frame, Unit.INSTANCE, poseStack, FRAME_TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();

		poseStack.pushPose();
		poseStack.translate(0.5f, (float) LiftLine.TERMINAL_ROPE, 0.5f);
		poseStack.rotateDegrees(Axis.YP, 180f - state.yaw + state.wheelAngle);
		poseStack.scale(-1f, -1f, 1f);
		collector.submitModel(wheel, Unit.INSTANCE, poseStack, WHEEL_TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
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
