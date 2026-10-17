package com.freshtracks.client.render;

import com.freshtracks.FreshTracks;
import com.freshtracks.resort.TrailMapBlock;
import com.freshtracks.resort.TrailMapBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The trail map board: a resort map in a timber frame on two posts, four blocks across. The map comes from the server
 * (TrailMaps); with it, a red dot marks the board's own spot on the mountain. Without one, a plain resort map.
 */
public class TrailMapRenderer implements BlockEntityRenderer<TrailMapBlockEntity, TrailMapRenderer.State> {
	private static final Identifier BLANK = FreshTracks.id("textures/entity/trail_map_blank.png");
	private static final Identifier FRAME = FreshTracks.id("textures/entity/trail_map_frame.png");
	private static final Identifier DOT = FreshTracks.id("textures/entity/trail_map_dot.png");
	private static final float W = 4.0f;
	private static final float H = 4.0f;
	private static final float BOTTOM = 1.3f;
	private static final float BORDER = 0.14f;

	public static class State extends BlockEntityRenderState {
		Direction facing = Direction.NORTH;
		Identifier texture = BLANK;
		float mapHeight = H;
		float dotU = -1;
		float dotV = -1;
	}

	public TrailMapRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(TrailMapBlockEntity be, State state, float partialTicks, Vec3 cameraPosition,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
		if (be.getBlockState().getBlock() instanceof TrailMapBlock) {
			state.facing = be.getBlockState().getValue(TrailMapBlock.FACING);
		}
		TrailMaps.Map2 map = TrailMaps.get(be.mapId());
		if (map == null) {
			state.texture = BLANK;
			state.mapHeight = H;
			state.dotU = -1;
			state.dotV = -1;
		} else {
			// picture left edge = x1, right edge = x0, top = z1, bottom = z0 (resort maps look uphill)
			double x = be.getBlockPos().getX() + 0.5;
			double z = be.getBlockPos().getZ() + 0.5;
			state.texture = map.texture();
			state.mapHeight = Math.clamp(W / map.aspectRatio(), 1f, H);
			state.dotU = (float) ((map.x1() - x) / (map.x1() - map.x0()));
			state.dotV = (float) ((map.z1() - z) / (map.z1() - map.z0()));
			if (be.markerU() >= 0 && be.markerU() <= 1 && be.markerV() >= 0 && be.markerV() <= 1) {
				state.dotU = be.markerU();
				state.dotV = be.markerV();
			}
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.5f, 0f, 0.5f);
		// local +Z (the board's front) turns to face the block's facing; +X is then the viewer's right
		poseStack.rotateDegrees(Axis.YP, (float) Math.toDegrees(Math.atan2(state.facing.getStepX(), state.facing.getStepZ())));
		int light = state.lightCoords;
		float top = BOTTOM + state.mapHeight;
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(FRAME), (pose, buf) -> {
			// posts, the back panel, and the frame round the map
			box(pose, buf, light, -W / 2 - 0.25f, 0f, -0.12f, -W / 2 - 0.05f, top + 0.35f, 0.08f);
			box(pose, buf, light, W / 2 + 0.05f, 0f, -0.12f, W / 2 + 0.25f, top + 0.35f, 0.08f);
			box(pose, buf, light, -W / 2 - BORDER, BOTTOM - BORDER, -0.1f, W / 2 + BORDER, top + BORDER, -0.02f);
			box(pose, buf, light, -W / 2 - BORDER, top, -0.02f, W / 2 + BORDER, top + BORDER, 0.04f);
			box(pose, buf, light, -W / 2 - BORDER, BOTTOM - BORDER, -0.02f, W / 2 + BORDER, BOTTOM, 0.04f);
			box(pose, buf, light, -W / 2 - BORDER, BOTTOM, -0.02f, -W / 2, top, 0.04f);
			box(pose, buf, light, W / 2, BOTTOM, -0.02f, W / 2 + BORDER, top, 0.04f);
			// a little roof to keep the snow off
			box(pose, buf, light, -W / 2 - 0.45f, top + 0.35f, -0.35f, W / 2 + 0.45f, top + 0.45f, 0.3f);
		});
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(state.texture), (pose, buf) ->
			quad(pose, buf, light, -W / 2, BOTTOM, W / 2, top, 0.001f, 0f, 0f, 1f, 1f));
		if (state.dotU >= 0 && state.dotU <= 1 && state.dotV >= 0 && state.dotV <= 1) {
			float cx = -W / 2 + state.dotU * W;
			float cy = top - state.dotV * state.mapHeight;
			float r = 0.055f;
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(DOT), (pose, buf) ->
				quad(pose, buf, light, cx - r, cy - r, cx + r, cy + r, 0.006f, 0f, 0f, 1f, 1f));
		}
		poseStack.popPose();
	}

	/** A flat quad facing +Z (towards the viewer) from (x0, y0) to (x1, y1), texture u0..u1 left to right, v0..v1 top to bottom. */
	private static void quad(PoseStack.Pose pose, VertexConsumer buf, int light, float x0, float y0, float x1, float y1, float z,
		float u0, float v0, float u1, float v1) {
		vertex(pose, buf, light, x0, y1, z, u0, v0, 0, 0, 1);
		vertex(pose, buf, light, x0, y0, z, u0, v1, 0, 0, 1);
		vertex(pose, buf, light, x1, y0, z, u1, v1, 0, 0, 1);
		vertex(pose, buf, light, x1, y1, z, u1, v0, 0, 0, 1);
	}

	/** An axis-aligned box with the frame texture stretched over each face. */
	private static void box(PoseStack.Pose pose, VertexConsumer buf, int light, float x0, float y0, float z0, float x1, float y1, float z1) {
		// front (+z) and back (-z)
		vertex(pose, buf, light, x0, y1, z1, 0, 0, 0, 0, 1);
		vertex(pose, buf, light, x0, y0, z1, 0, 1, 0, 0, 1);
		vertex(pose, buf, light, x1, y0, z1, 1, 1, 0, 0, 1);
		vertex(pose, buf, light, x1, y1, z1, 1, 0, 0, 0, 1);
		vertex(pose, buf, light, x1, y1, z0, 0, 0, 0, 0, -1);
		vertex(pose, buf, light, x1, y0, z0, 0, 1, 0, 0, -1);
		vertex(pose, buf, light, x0, y0, z0, 1, 1, 0, 0, -1);
		vertex(pose, buf, light, x0, y1, z0, 1, 0, 0, 0, -1);
		// sides (-x, +x)
		vertex(pose, buf, light, x0, y1, z0, 0, 0, -1, 0, 0);
		vertex(pose, buf, light, x0, y0, z0, 0, 1, -1, 0, 0);
		vertex(pose, buf, light, x0, y0, z1, 1, 1, -1, 0, 0);
		vertex(pose, buf, light, x0, y1, z1, 1, 0, -1, 0, 0);
		vertex(pose, buf, light, x1, y1, z1, 0, 0, 1, 0, 0);
		vertex(pose, buf, light, x1, y0, z1, 0, 1, 1, 0, 0);
		vertex(pose, buf, light, x1, y0, z0, 1, 1, 1, 0, 0);
		vertex(pose, buf, light, x1, y1, z0, 1, 0, 1, 0, 0);
		// top (+y) and bottom (-y)
		vertex(pose, buf, light, x0, y1, z0, 0, 0, 0, 1, 0);
		vertex(pose, buf, light, x0, y1, z1, 0, 1, 0, 1, 0);
		vertex(pose, buf, light, x1, y1, z1, 1, 1, 0, 1, 0);
		vertex(pose, buf, light, x1, y1, z0, 1, 0, 0, 1, 0);
		vertex(pose, buf, light, x0, y0, z1, 0, 0, 0, -1, 0);
		vertex(pose, buf, light, x0, y0, z0, 0, 1, 0, -1, 0);
		vertex(pose, buf, light, x1, y0, z0, 1, 1, 0, -1, 0);
		vertex(pose, buf, light, x1, y0, z1, 1, 0, 0, -1, 0);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buf, int light, float x, float y, float z, float u, float v,
		float nx, float ny, float nz) {
		buf.addVertex(pose, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 128;
	}
}
