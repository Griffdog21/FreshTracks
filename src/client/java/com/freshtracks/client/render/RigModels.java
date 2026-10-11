package com.freshtracks.client.render;

import com.freshtracks.FreshTracks;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Model layers for the things under your feet. Model space follows vanilla: -Z is the front (tips / nose),
 * +X is the rider's left, -Y is up. One unit = 1/16 block. The base of the skis sits at y = 0.
 *
 * <p>Texture layout (64x64) is mirrored by {@code tools/art/rigs.py}; change both together.
 */
public final class RigModels {
	public static final ModelLayerLocation SKIS = new ModelLayerLocation(FreshTracks.id("skis"), "main");
	public static final ModelLayerLocation BOARD = new ModelLayerLocation(FreshTracks.id("snowboard"), "main");

	/** Ski length in model units; tips stick out ahead of the toes. */
	public static final float SKI_LENGTH = 26f;

	private RigModels() {
	}

	public static LayerDefinition skis() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		for (int side = 0; side < 2; side++) {
			String name = side == 0 ? "left" : "right";
			float x = side == 0 ? 2.1f : -2.1f;
			PartDefinition ski = root.addOrReplaceChild(name + "_ski",
				CubeListBuilder.create()
					// main body: 2 wide, 1 thick, 24 long (z -12..12), base at y=0
					.texOffs(0, 0).addBox(-1.0f, -1.0f, -11.0f, 2.0f, 1.0f, 23.0f)
					// binding plate
					.texOffs(0, 30).addBox(-1.3f, -2.2f, -2.5f, 2.6f, 1.2f, 6.0f)
					// heel piece
					.texOffs(20, 30).addBox(-1.1f, -3.0f, 3.5f, 2.2f, 2.0f, 1.5f),
				PartPose.offset(x, 0f, 0f));
			// tip curls up (rotate about the front of the ski)
			ski.addOrReplaceChild(name + "_tip",
				CubeListBuilder.create().texOffs(0, 40).addBox(-1.0f, -1.0f, -3.5f, 2.0f, 1.0f, 3.5f),
				PartPose.offsetAndRotation(0f, 0f, -11.0f, -0.6f, 0f, 0f));
			ski.addOrReplaceChild(name + "_tail",
				CubeListBuilder.create().texOffs(12, 40).addBox(-1.0f, -1.0f, 0f, 2.0f, 1.0f, 2.0f),
				PartPose.offsetAndRotation(0f, 0f, 12.0f, 0.22f, 0f, 0f));
		}
		return LayerDefinition.create(mesh, 64, 64);
	}

	public static LayerDefinition board() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition deck = root.addOrReplaceChild("deck",
			CubeListBuilder.create()
				// 7 wide, 1 thick, 21 long; nose at -Z
				.texOffs(0, 0).addBox(-3.5f, -1.0f, -10.5f, 7.0f, 1.0f, 21.0f),
			PartPose.ZERO);
		deck.addOrReplaceChild("nose",
			CubeListBuilder.create().texOffs(0, 23).addBox(-3.5f, -1.0f, -3.0f, 7.0f, 1.0f, 3.0f),
			PartPose.offsetAndRotation(0f, 0f, -10.5f, -0.30f, 0f, 0f));
		deck.addOrReplaceChild("tail",
			CubeListBuilder.create().texOffs(20, 23).addBox(-3.5f, -1.0f, 0f, 7.0f, 1.0f, 3.0f),
			PartPose.offsetAndRotation(0f, 0f, 10.5f, 0.30f, 0f, 0f));
		for (int i = 0; i < 2; i++) {
			float z = i == 0 ? -4.5f : 4.5f;
			String name = i == 0 ? "front" : "back";
			deck.addOrReplaceChild(name + "_binding",
				CubeListBuilder.create()
					.texOffs(0, 30).addBox(-2.6f, -1.8f, -1.6f, 5.2f, 0.8f, 3.2f)
					// highback on the heel side (+X is the rider's heel side when riding regular)
					.texOffs(20, 30).addBox(1.8f, -4.6f, -1.4f, 0.8f, 2.8f, 2.8f)
					// strap
					.texOffs(0, 36).addBox(-2.0f, -2.8f, -1.2f, 4.0f, 1.0f, 2.4f),
				PartPose.offset(0f, 0f, z));
		}
		return LayerDefinition.create(mesh, 64, 64);
	}
}
