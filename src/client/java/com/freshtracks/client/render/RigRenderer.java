package com.freshtracks.client.render;

import com.freshtracks.FreshTracks;
import com.freshtracks.rig.RigEntity;
import com.freshtracks.rig.RigKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;

public class RigRenderer extends EntityRenderer<RigEntity, RigRenderState> {
	private static final Map<Item, Identifier> TEXTURES = new HashMap<>();
	private final RigModel skis;
	private final RigModel board;

	public RigRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.skis = new RigModel(context.bakeLayer(RigModels.SKIS));
		this.board = new RigModel(context.bakeLayer(RigModels.BOARD));
		this.shadowRadius = 0.0f;
	}

	public static Identifier textureFor(Item item) {
		return TEXTURES.computeIfAbsent(item, i -> {
			Identifier key = BuiltInRegistries.ITEM.getKey(i);
			return FreshTracks.id("textures/entity/rig/" + key.getPath() + ".png");
		});
	}

	@Override
	public RigRenderState createRenderState() {
		return new RigRenderState();
	}

	@Override
	public void extractRenderState(RigEntity entity, RigRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.kind = entity.kind();
		state.texture = textureFor(entity.getItem().getItem());
		state.yaw = entity.getYRot(partialTicks);
		state.lean = entity.lean(partialTicks);
		state.flip = entity.flip(partialTicks);
		state.flags = entity.renderFlags();
		state.grab = entity.renderGrab();
		state.ridden = entity.isVehicle();
		state.slopePitch = entity.slopePitch(partialTicks);
		state.slopeRoll = entity.slopeRoll(partialTicks);
	}

	@Override
	protected AABB getBoundingBoxForCulling(RigEntity entity, float partialTicks) {
		return entity.getBoundingBox().inflate(1.5, 1.5, 1.5);
	}

	@Override
	public void submit(RigRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		RigPose.apply(poseStack, state.yaw, state.lean, state.flip, state.slopePitch, state.slopeRoll);
		poseStack.rotateDegrees(Axis.YP, 180f - state.yaw);
		poseStack.scale(-1f, -1f, 1f);
		RigModel model = state.kind == RigKind.SNOWBOARD ? board : skis;
		collector.submitModel(model, state, poseStack, state.texture, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	public static class RigModel extends EntityModel<RigRenderState> {
		public RigModel(ModelPart root) {
			super(root);
		}
	}
}
