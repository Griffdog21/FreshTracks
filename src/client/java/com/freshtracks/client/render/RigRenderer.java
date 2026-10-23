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
import net.minecraft.util.Mth;
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
		byte action = entity.renderAction();
		state.action = (state.flags & RigEntity.FLAG_AIR) != 0 ? com.freshtracks.rig.RiderAction.NONE : com.freshtracks.rig.RiderAction.action(action);
		state.actionTime = com.freshtracks.rig.RiderAction.ticks(action) + partialTicks;
	}

	@Override
	protected AABB getBoundingBoxForCulling(RigEntity entity, float partialTicks) {
		return entity.getBoundingBox().inflate(1.5, 1.5, 1.5);
	}

	@Override
	public void submit(RigRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		RigPose.pressLift(poseStack, state.flags, state.grab);
		RigPose.apply(poseStack, state.yaw, state.lean, state.flip, state.slopePitch, state.slopeRoll);
		poseStack.rotateDegrees(Axis.YP, 180f - state.yaw);
		poseStack.scale(-1f, -1f, 1f);
		RigModel model = state.kind == RigKind.SNOWBOARD ? board : skis;
		collector.submitModel(model, state, poseStack, state.texture, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	public static class RigModel extends EntityModel<RigRenderState> {
		private final ModelPart left;
		private final ModelPart right;

		public RigModel(ModelPart root) {
			super(root);
			this.left = root.hasChild("left_ski") ? root.getChild("left_ski") : null;
			this.right = root.hasChild("right_ski") ? root.getChild("right_ski") : null;
		}

		/**
		 * Skis splay under the feet: a herringbone climb sets them in a V, tips apart and tails together (the stepping
		 * ski lifted), and a skating stride pushes one ski out to the side. (+X is the rider's left, -Z the tips.)
		 */
		@Override
		public void setupAnim(RigRenderState state) {
			super.setupAnim(state);
			if (left == null || right == null) {
				return;
			}
			float t = state.actionTime;
			switch (state.action) {
				case HERRINGBONE_LEFT, HERRINGBONE_RIGHT -> {
					boolean stepLeft = state.action == com.freshtracks.rig.RiderAction.HERRINGBONE_LEFT;
					float step = Mth.sin(Math.min(1f, t / 7f) * Mth.PI);
					left.x += 1.4f;
					right.x -= 1.4f;
					left.yRot = -0.30f;
					right.yRot = 0.30f;
					(stepLeft ? left : right).y -= 1.5f * step;
				}
				case SKATE_LEFT, SKATE_RIGHT -> {
					boolean pushLeft = state.action == com.freshtracks.rig.RiderAction.SKATE_LEFT;
					float stride = Mth.sin(Math.min(1f, t / 9f) * Mth.PI);
					ModelPart ski = pushLeft ? left : right;
					ski.x += (pushLeft ? 4.4f : -4.4f) * stride;
					ski.yRot = (pushLeft ? -0.32f : 0.32f) * stride;
					ski.y -= 0.8f * Math.max(0f, stride - 0.6f) / 0.4f;
				}
				default -> {
				}
			}
		}
	}
}
