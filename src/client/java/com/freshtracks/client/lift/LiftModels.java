package com.freshtracks.client.lift;

import com.freshtracks.FreshTracks;
import com.freshtracks.lift.ChairStyle;
import com.freshtracks.lift.LiftLine;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Lift models, built in code. Vanilla model space: -Z is forward (direction of travel), +X is left, +Y is down,
 * 16 units per block. Chair origin = the haul rope; terminal origin = base of the terminal block; tower head
 * origin = base of the head block. Texture layouts are mirrored by {@code tools/art/lifts.py}.
 */
public final class LiftModels {
	public static final Map<ChairStyle, ModelLayerLocation> CHAIRS = new EnumMap<>(ChairStyle.class);
	public static final ModelLayerLocation BULLWHEEL = new ModelLayerLocation(FreshTracks.id("lift_bullwheel"), "main");
	public static final ModelLayerLocation TERMINAL = new ModelLayerLocation(FreshTracks.id("lift_terminal"), "main");
	public static final ModelLayerLocation TOWER_HEAD = new ModelLayerLocation(FreshTracks.id("lift_tower_head"), "main");
	public static final ModelLayerLocation GONDOLA_GLASS = new ModelLayerLocation(FreshTracks.id("lift_gondola_glass"), "main");

	static {
		for (ChairStyle style : ChairStyle.values()) {
			CHAIRS.put(style, new ModelLayerLocation(FreshTracks.id("lift_chair_" + style.getSerializedName()), "main"));
		}
	}

	private LiftModels() {
	}

	/** Bottom of a surface lift's spring box below the rope, blocks. */
	public static final double SPRING_BOX_BOTTOM = 11.0 / 16.0;

	/** Height of the bench top below the rope, in model units. Riders' hips sit here. */
	public static float benchY(ChairStyle style) {
		return (float) ((style.hanger - 0.62) * 16.0);
	}

	public static LayerDefinition chair(ChairStyle style) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		if (style.cabin()) {
			return gondola(mesh);
		}
		if (style.surface()) {
			// the spring box clamped to the rope; the bar's line reels out of its bottom (drawn by LiftRenderer)
			root.addOrReplaceChild("grip", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, -2f, -3f, 3f, 4f, 6f), PartPose.ZERO);
			root.addOrReplaceChild("box", CubeListBuilder.create().texOffs(0, 12).addBox(-2.5f, 2f, -2.5f, 5f, 9f, 5f), PartPose.ZERO);
			return LayerDefinition.create(mesh, 32, 32);
		}
		float w = (float) (style.width * 16.0);
		float seat = benchY(style);
		float half = w / 2f;
		root.addOrReplaceChild("grip", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, -2f, -3f, 3f, 4f, 6f), PartPose.ZERO);
		root.addOrReplaceChild("arm", CubeListBuilder.create().texOffs(20, 0).addBox(-0.75f, 1f, 0f, 1.5f, 1.5f, 6f), PartPose.ZERO);
		float hangLen = seat + 1.5f - 2f;
		root.addOrReplaceChild("hanger", CubeListBuilder.create().texOffs(40, 0).addBox(-0.75f, 2f, 4.6f, 1.5f, hangLen, 1.5f), PartPose.ZERO);
		root.addOrReplaceChild("seat", CubeListBuilder.create().texOffs(0, 40).addBox(-half, seat, -5f, w, 1.5f, 9f), PartPose.ZERO);
		root.addOrReplaceChild("frame", CubeListBuilder.create().texOffs(0, 52).addBox(-half, seat + 1.5f, 3.5f, w, 1f, 1f), PartPose.ZERO);
		root.addOrReplaceChild("backrest", CubeListBuilder.create().texOffs(0, 56).addBox(-half, -11f, 0f, w, 11f, 1.2f),
			PartPose.offsetAndRotation(0f, seat, 3.6f, -0.18f, 0f, 0f));
		PartDefinition bar = root.addOrReplaceChild("bar", CubeListBuilder.create()
				.texOffs(0, 70).addBox(-half, 0f, -10f, w, 1f, 1f)
				.texOffs(0, 74).addBox(-half, 0f, -10f, 1f, 1f, 10f)
				.texOffs(0, 74).addBox(half - 1f, 0f, -10f, 1f, 1f, 10f)
				.texOffs(24, 74).addBox(-0.5f, 0f, -10.5f, 1f, 13f, 1f)
				.texOffs(0, 86).addBox(-half + 2f, 13f, -11.5f, w - 4f, 1f, 2.5f),
			PartPose.offset(0f, seat - 12f, 4.2f));
		return LayerDefinition.create(mesh, 128, 128);
	}

	/**
	 * A modern cabin: charcoal pillars, white and red lower walls, a stepped red roof, benches front and back, a lit
	 * ceiling, ski racks on the sides. Every surface is a real panel, so it looks right from inside
	 * too (the render type doesn't cull, so each box shows both ways). The windows are a separate translucent layer,
	 * {@link #gondolaGlass()}. UV spots mirror GONDOLA_UV in tools/art/lifts.py.
	 */
	private static LayerDefinition gondola(MeshDefinition mesh) {
		PartDefinition root = mesh.getRoot();
		float floor = (float) (ChairStyle.GONDOLA.hanger * 16.0) + 1f;
		float ceil = floor - 34f;
		root.addOrReplaceChild("grip", box(143, 167, -1.5f, -2f, -3f, 3f, 4f, 6f), PartPose.ZERO);
		root.addOrReplaceChild("hanger", box(134, 167, -1f, 2f, -1f, 2f, ceil - 9f - 2f, 2f), PartPose.ZERO);
		root.addOrReplaceChild("fairing", box(7, 167, -3f, ceil - 9f, -6f, 6f, 3f, 12f), PartPose.ZERO);
		root.addOrReplaceChild("roof3", box(105, 101, -9f, ceil - 6f, -8f, 18f, 2f, 16f), PartPose.ZERO);
		root.addOrReplaceChild("roof2", box(0, 101, -14f, ceil - 4f, -12f, 28f, 2f, 24f), PartPose.ZERO);
		root.addOrReplaceChild("roof1", box(0, 0, -18f, ceil - 2f, -16f, 36f, 2f, 32f), PartPose.ZERO);
		root.addOrReplaceChild("ceiling", box(0, 35, -17f, ceil, -15f, 34f, 2f, 30f), PartPose.ZERO);
		root.addOrReplaceChild("light", box(44, 167, -6f, ceil + 2f, -2.5f, 12f, 0.5f, 5f), PartPose.ZERO);
		float[][] corners = {{-17f, -15f}, {15f, -15f}, {-17f, 13f}, {15f, 13f}};
		for (int i = 0; i < corners.length; i++) {
			root.addOrReplaceChild("pillar" + i, box(244, 128, corners[i][0], ceil + 2f, corners[i][1], 2f, 30f, 2f), PartPose.ZERO);
		}
		root.addOrReplaceChild("mullion_r", box(0, 167, -17f, ceil + 2f, -1f, 1f, 16f, 2f), PartPose.ZERO);
		root.addOrReplaceChild("mullion_l", box(0, 167, 16f, ceil + 2f, -1f, 1f, 16f, 2f), PartPose.ZERO);
		root.addOrReplaceChild("wall_front", box(174, 101, -15f, floor - 14f, -15f, 30f, 12f, 1f), PartPose.ZERO);
		root.addOrReplaceChild("wall_back", box(174, 101, -15f, floor - 14f, 14f, 30f, 12f, 1f), PartPose.ZERO);
		root.addOrReplaceChild("wall_right", box(0, 128, -17f, floor - 14f, -13f, 1f, 12f, 26f), PartPose.ZERO);
		root.addOrReplaceChild("wall_left", box(0, 128, 16f, floor - 14f, -13f, 1f, 12f, 26f), PartPose.ZERO);
		root.addOrReplaceChild("plate", box(115, 167, -4f, floor - 12f, -15.6f, 8f, 5f, 0.6f), PartPose.ZERO);
		root.addOrReplaceChild("floor", box(0, 68, -17f, floor - 2f, -15f, 34f, 2f, 30f), PartPose.ZERO);
		root.addOrReplaceChild("belly", box(129, 68, -15f, floor, -13f, 30f, 2f, 26f), PartPose.ZERO);
		// benches facing each other, cushions at knee height
		for (int end = -1; end <= 1; end += 2) {
			float seatZ = end < 0 ? -14f : 8f;
			float backZ = end < 0 ? -14f : 13f;
			String n = end < 0 ? "_front" : "_back";
			root.addOrReplaceChild("bench_seat" + n, box(55, 128, -14f, floor - 10f, seatZ, 28f, 2f, 6f), PartPose.ZERO);
			root.addOrReplaceChild("bench_back" + n, box(124, 128, -14f, floor - 20f, backZ, 28f, 10f, 1f), PartPose.ZERO);
			root.addOrReplaceChild("bench_base" + n, box(183, 128, -13f, floor - 8f, seatZ + 1f, 26f, 6f, 4f), PartPose.ZERO);
		}
		// ski racks outside both side walls
		for (int side = -1; side <= 1; side += 2) {
			float x = side < 0 ? -18f : 17f;
			String n = side < 0 ? "_r" : "_l";
			root.addOrReplaceChild("rack" + n, box(79, 167, x, floor - 9f, -14f, 1f, 1f, 6f), PartPose.ZERO);
			int[][] skis = {{94, 167}, {101, 167}, {108, 167}};
			for (int k = 0; k < skis.length; k++) {
				root.addOrReplaceChild("ski" + n + k, box(skis[k][0], skis[k][1], x + (side < 0 ? -0.5f : 1f), floor - 26f, -13.5f + k * 1.8f,
					0.5f, 22f, 1.5f), PartPose.ZERO);
			}
		}
		return LayerDefinition.create(mesh, 256, 256);
	}

	/** The gondola's window panes, drawn translucent over the cabin. UV spots mirror GLASS_UV in tools/art/lifts.py. */
	public static LayerDefinition gondolaGlass() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		float floor = (float) (ChairStyle.GONDOLA.hanger * 16.0) + 1f;
		float top = floor - 32f;
		root.addOrReplaceChild("front", box(0, 0, -15f, top, -14.75f, 30f, 18f, 0.5f), PartPose.ZERO);
		root.addOrReplaceChild("back", box(0, 0, -15f, top, 14.25f, 30f, 18f, 0.5f), PartPose.ZERO);
		root.addOrReplaceChild("right", box(63, 0, -16.75f, top, -13f, 0.5f, 18f, 26f), PartPose.ZERO);
		root.addOrReplaceChild("left", box(63, 0, 16.25f, top, -13f, 0.5f, 18f, 26f), PartPose.ZERO);
		return LayerDefinition.create(mesh, 128, 64);
	}

	private static CubeListBuilder box(int u, int v, float x, float y, float z, float w, float h, float d) {
		return CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d);
	}

	public static LayerDefinition bullwheel() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		float r = (float) (LiftLine.TRACK_HALF * 16.0);
		int segments = 16;
		float segLen = (float) (2 * Math.PI * r / segments) + 0.6f;
		PartDefinition wheel = root.addOrReplaceChild("wheel", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-3f, -2f, -3f, 6f, 4f, 6f), PartPose.ZERO);
		for (int i = 0; i < segments; i++) {
			double angle = 2 * Math.PI * i / segments;
			wheel.addOrReplaceChild("rim" + i, CubeListBuilder.create().texOffs(0, 12).addBox(-segLen / 2f, -1.5f, -1.5f, segLen, 3f, 3f),
				PartPose.offsetAndRotation((float) (r * Math.sin(angle)), 0f, (float) (r * Math.cos(angle)), 0f, (float) angle, 0f));
		}
		for (int i = 0; i < 4; i++) {
			double angle = Math.PI / 2 * i + Math.PI / 4;
			wheel.addOrReplaceChild("spoke" + i, CubeListBuilder.create().texOffs(0, 20).addBox(-0.75f, -0.75f, 0f, 1.5f, 1.5f, r),
				PartPose.rotation(0f, (float) angle, 0f));
		}
		return LayerDefinition.create(mesh, 64, 64);
	}

	/** Posts, roof and drive house of a terminal. The bullwheel sits at rope height under the roof. */
	public static LayerDefinition terminal() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		float rope = (float) (LiftLine.TERMINAL_ROPE * 16.0);
		float postH = rope + 10f;
		float[][] posts = {{-12f, -12f}, {12f, -12f}, {-12f, 12f}, {12f, 12f}};
		for (int i = 0; i < posts.length; i++) {
			root.addOrReplaceChild("post" + i, CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, -postH, -1.5f, 3f, postH, 3f),
				PartPose.offset(posts[i][0], 0f, posts[i][1]));
		}
		// a bar from the posts up to the wheel's axle
		root.addOrReplaceChild("axle", CubeListBuilder.create().texOffs(16, 0).addBox(-2f, -postH, -2f, 4f, postH - rope - 2f, 4f), PartPose.ZERO);
		// big flat roof over the wheel, Minecraft-blocky, in four panels (keeps the box-UV texture small)
		for (int i = 0; i < 4; i++) {
			float px = (i % 2 == 0) ? -44f : 0f;
			float pz = (i / 2 == 0) ? -44f : 0f;
			root.addOrReplaceChild("roof" + i, CubeListBuilder.create().texOffs(0, 40).addBox(px, -postH - 3f, pz, 44f, 3f, 44f), PartPose.ZERO);
		}
		root.addOrReplaceChild("house", CubeListBuilder.create().texOffs(0, 140).addBox(-14f, -postH - 15f, -14f, 28f, 12f, 28f), PartPose.ZERO);
		return LayerDefinition.create(mesh, 256, 256);
	}

	public static LayerDefinition towerHead() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		float rope = (float) (LiftLine.HEAD_ROPE * 16.0);
		float r = (float) (LiftLine.TRACK_HALF * 16.0);
		// Cross-arm across the line (along X), just under the sheaves.
		root.addOrReplaceChild("arm", CubeListBuilder.create().texOffs(0, 0).addBox(-r - 6f, -rope + 3.5f, -1.5f, 2 * r + 12f, 3f, 3f), PartPose.ZERO);
		for (int side = -1; side <= 1; side += 2) {
			PartDefinition train = root.addOrReplaceChild("train" + (side < 0 ? "r" : "l"), CubeListBuilder.create()
					.texOffs(0, 8).addBox(-1.5f, -1f, -9f, 3f, 2f, 18f),
				PartPose.offset(side * r, -rope + 2.5f, 0f));
			for (int k = 0; k < 4; k++) {
				train.addOrReplaceChild("sheave" + k, CubeListBuilder.create().texOffs(0, 30).addBox(-1f, -1.5f, -1.5f, 2f, 3f, 3f),
					PartPose.offset(0f, -1.5f, -6.75f + 4.5f * k));
			}
		}
		return LayerDefinition.create(mesh, 128, 64);
	}
}
