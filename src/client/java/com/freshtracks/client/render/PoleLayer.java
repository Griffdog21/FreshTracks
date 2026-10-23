package com.freshtracks.client.render;

import com.freshtracks.FreshTracks;
import com.freshtracks.rig.RiderAction;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A skier's poles, drawn from each fist down to where the basket is on the snow: trailing behind as you glide, swung
 * ahead and planted for a double-pole push (the basket stays put on the snow while you slide past it), flicked forward
 * to start a turn, set wide behind you on a herringbone climb, tucked up under the arms in an egg. A held pole would
 * only point along the arm, so reaching forward would poke the poles straight ahead like spears.
 */
public class PoleLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = FreshTracks.id("textures/item/ski_pole_model.png");
	/** Pole length, grip to tip, blocks. */
	private static final float LENGTH = 1.22f;

	public PoleLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		RiderPose pose = state.getData(RiderPose.KEY);
		if (pose == null || !pose.poles() || state.isInvisible) {
			return;
		}
		Matrix4f model = new Matrix4f(poseStack.last().pose());
		for (HumanoidArm arm : HumanoidArm.values()) {
			// where the basket is, in the model's space (blocks; y down, the snow at 1.5; -z ahead; +x the rider's left)
			Vector3f basket = basket(pose, arm == HumanoidArm.LEFT ? 1 : -1);
			poseStack.pushPose();
			getParentModel().translateToHand(state, arm, poseStack);
			poseStack.translate(0f, 0.6f, 0f); // into the fist
			Matrix4f hand = new Matrix4f(poseStack.last().pose());
			Vector3f aim = new Matrix4f(hand).invert().mul(model).transformPosition(basket);
			if (aim.lengthSquared() > 1.0e-4f) {
				poseStack.rotate(new Quaternionf().rotationTo(new Vector3f(0f, 1f, 0f), aim.normalize()));
				collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (p, buf) -> {
					// grip in the fist, strap, shaft, basket and tip, running down the pole (+y here, toward the basket)
					FirstPersonPoles.box(p, buf, light, -0.035f, -0.17f, -0.035f, 0.035f, 0.02f, 0.035f, FirstPersonPoles.GRIP);
					FirstPersonPoles.box(p, buf, light, -0.01f, -0.2f, 0.035f, 0.01f, -0.1f, 0.05f, FirstPersonPoles.STRAP);
					FirstPersonPoles.box(p, buf, light, -0.014f, 0.02f, -0.014f, 0.014f, LENGTH - 0.08f, 0.014f, FirstPersonPoles.SHAFT);
					FirstPersonPoles.box(p, buf, light, -0.075f, LENGTH - 0.12f, -0.075f, 0.075f, LENGTH - 0.105f, 0.075f, FirstPersonPoles.BASKET);
					FirstPersonPoles.box(p, buf, light, -0.007f, LENGTH - 0.08f, -0.007f, 0.007f, LENGTH, 0.007f, FirstPersonPoles.TIP);
				});
			}
			poseStack.popPose();
		}
	}

	/** The basket of the pole on one side (1 the rider's left, -1 the right), in model space. */
	static Vector3f basket(RiderPose pose, int side) {
		float t = pose.actionTime();
		RiderAction action = pose.airborne() ? RiderAction.NONE : pose.action();
		boolean tuck = pose.has(com.freshtracks.rig.RigEntity.FLAG_TUCK);
		// gliding: held just off the snow, trailing behind
		float x = side * 0.42f;
		float y = 1.36f;
		float z = 0.45f;
		if (pose.airborne()) {
			x = side * 0.6f;
			y = 1.15f;
			z = 0.55f;
		} else if (tuck) {
			// up under the arms, pointing back
			x = side * 0.32f;
			y = 0.62f;
			z = 1.45f;
		} else {
			switch (action) {
				case DOUBLE_POLE -> {
					if (t < 3f) {
						// swing both baskets forward
						float u = t / 3f;
						z = Mth.lerp(u, 0.45f, -0.55f);
						y = Mth.lerp(u, 1.36f, 1.42f);
					} else if (t < 7.5f) {
						// planted: the skier slides past the baskets
						z = Mth.lerp((t - 3f) / 4.5f, -0.55f, 0.85f);
						y = 1.5f;
					} else {
						// released, swinging back through
						float u = Math.min(1f, (t - 7.5f) / 5.5f);
						z = Mth.lerp(u, 0.85f, 0.45f);
						y = 1.5f - 0.3f * Mth.sin(u * Mth.PI);
					}
					x = side * 0.36f;
				}
				case PLANT_LEFT, PLANT_RIGHT -> {
					if (side == (action == RiderAction.PLANT_LEFT ? 1 : -1)) {
						float reach = t < 2.5f ? t / 2.5f : Math.max(0f, 1f - (t - 2.5f) / 5.5f);
						z = Mth.lerp(reach, 0.45f, -0.7f);
						y = Mth.lerp(reach, 1.36f, 1.5f);
						x = side * Mth.lerp(reach, 0.42f, 0.55f);
					}
				}
				case HERRINGBONE_LEFT, HERRINGBONE_RIGHT -> {
					// set wide behind to hold each step
					x = side * 0.78f;
					y = 1.5f;
					z = 0.62f;
				}
				case SKATE_LEFT, SKATE_RIGHT -> {
					boolean back = side == (action == RiderAction.SKATE_LEFT ? 1 : -1);
					float stride = Mth.sin(Math.min(1f, t / 9f) * Mth.PI);
					z = back ? 0.45f + 0.45f * stride : 0.45f - 0.6f * stride;
					y = back ? 1.5f : 1.3f;
				}
				default -> {
				}
			}
		}
		return new Vector3f(x, y, z);
	}
}
