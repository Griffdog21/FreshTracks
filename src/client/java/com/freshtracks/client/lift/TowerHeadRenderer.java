package com.freshtracks.client.lift;

import com.freshtracks.FreshTracks;
import com.freshtracks.lift.block.LiftTowerHeadBlockEntity;
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

/** Cross-arm and sheave trains on top of a tower, lined up with the rope. */
public class TowerHeadRenderer implements BlockEntityRenderer<LiftTowerHeadBlockEntity, TowerHeadRenderer.State> {
	private static final Identifier TEXTURE = FreshTracks.id("textures/entity/lift/tower_head.png");
	private final Model.Simple head;

	public static class State extends BlockEntityRenderState {
		float yaw;
	}

	public TowerHeadRenderer(BlockEntityRendererProvider.Context context) {
		this.head = new Model.Simple(context.bakeLayer(LiftModels.TOWER_HEAD), RenderTypes::entityCutout);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(LiftTowerHeadBlockEntity be, State state, float partialTicks, Vec3 cameraPosition,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
		ClientLifts.Entry entry = ClientLifts.byTower(be.getBlockPos());
		if (entry != null) {
			Vec3 d = ClientLifts.towerDirection(entry, be.getBlockPos());
			state.yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		} else {
			state.yaw = 0;
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.5f, 0f, 0.5f);
		poseStack.rotateDegrees(Axis.YP, 180f - state.yaw);
		poseStack.scale(-1f, -1f, 1f);
		collector.submitModel(head, Unit.INSTANCE, poseStack, TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
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
