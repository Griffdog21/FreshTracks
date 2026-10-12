package com.freshtracks.client.lift;

import com.freshtracks.FreshTracks;
import com.freshtracks.lift.ChairStyle;
import com.freshtracks.lift.LiftLine;
import com.freshtracks.lift.LiftSeatEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.freshtracks.rig.RigEntity;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/** Draws every lift's chairs and haul rope in the current dimension (they aren't entities, so this is global). */
public final class LiftRenderer {
	public static final Identifier CHAIR_TEXTURE = FreshTracks.id("textures/entity/lift/chair.png");
	public static final Identifier GONDOLA_TEXTURE = FreshTracks.id("textures/entity/lift/gondola.png");
	public static final Identifier PULSE_TEXTURE = FreshTracks.id("textures/entity/lift/gondola_pulse.png");
	private static final Identifier[] SKITTLES_TEXTURES = {
		FreshTracks.id("textures/entity/lift/gondola_pulse_red.png"), PULSE_TEXTURE,
		FreshTracks.id("textures/entity/lift/gondola_pulse_green.png"), FreshTracks.id("textures/entity/lift/gondola_pulse_yellow.png")
	};
	public static final Identifier GONDOLA_GLASS_TEXTURE = FreshTracks.id("textures/entity/lift/gondola_glass.png");
	public static final Identifier ROPE_TEXTURE = FreshTracks.id("textures/entity/lift/rope.png");
	public static final Identifier EXPRESS_TEXTURE = FreshTracks.id("textures/entity/lift/chair_express.png");
	public static final Identifier SPRING_BOX_TEXTURE = FreshTracks.id("textures/entity/lift/spring_box.png");
	public static final Identifier BAR_TEXTURE = FreshTracks.id("textures/entity/lift/tbar.png");
	public static final Identifier PLATTER_TEXTURE = FreshTracks.id("textures/entity/lift/platter.png");
	private static final double CHAIR_VIEW = 220;
	private static final double ROPE_VIEW = 320;

	private static Map<ChairStyle, ChairModel> chairs;
	private static Model.Simple gondolaGlass;
	private static Model.Simple midHousing;

	private LiftRenderer() {
	}

	/** Per-chair render data: how far the safety bar is down, and how much it swings. */
	public record ChairState(float bar, float swing) {
	}

	public static final class ChairModel extends Model<ChairState> {
		private final ModelPart bar;

		public ChairModel(ModelPart root) {
			super(root, RenderTypes::entityCutout);
			this.bar = root.hasChild("bar") ? root.getChild("bar") : null;
		}

		@Override
		public void setupAnim(ChairState state) {
			super.setupAnim(state);
			if (bar != null) {
				bar.xRot = Mth.lerp(state.bar(), -1.35f, 0.55f);
			}
		}
	}

	private static void ensureModels() {
		if (chairs != null) {
			return;
		}
		EntityModelSet set = Minecraft.getInstance().getEntityModels();
		chairs = new EnumMap<>(ChairStyle.class);
		for (ChairStyle style : ChairStyle.values()) {
			chairs.put(style, new ChairModel(set.bakeLayer(LiftModels.CHAIRS.get(style))));
		}
		gondolaGlass = new Model.Simple(set.bakeLayer(LiftModels.GONDOLA_GLASS), RenderTypes::entityTranslucent);
		midHousing = new Model.Simple(set.bakeLayer(TerminalModels.MID_HOUSING), RenderTypes::entityCutout);
	}

	public static void reload() {
		chairs = null;
	}

	public static void collect(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || ClientLifts.all().isEmpty()) {
			return;
		}
		ensureModels();
		Vec3 cam = context.levelState().cameraRenderState.pos;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		com.freshtracks.client.dev.FrameProbe.frame(partial);
		PoseStack poseStack = context.poseStack();
		SubmitNodeCollector out = context.submitNodeCollector();

		// Which chairs have someone on them (their bar comes down), and which surface lift bars are pulling someone.
		Map<Integer, Set<Integer>> occupied = new HashMap<>();
		Map<Long, List<RigEntity>> towing = new HashMap<>();
		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof LiftSeatEntity seat && seat.isVehicle()) {
				occupied.computeIfAbsent(seat.lineNetId(), k -> new HashSet<>()).add(seat.chairIndex());
			} else if (entity instanceof RigEntity rig && rig.isTowed()) {
				towing.computeIfAbsent(towKey(rig.towLine(), rig.towCarrier()), k -> new ArrayList<>()).add(rig);
			}
		}

		for (ClientLifts.Entry entry : ClientLifts.all()) {
			LiftLine line = entry.line;
			if (!near(line, cam, ROPE_VIEW)) {
				continue;
			}
			double phase = entry.phase(partial);
			drawRopes(out, poseStack, level, line, cam);
			drawMidStations(out, poseStack, level, line, cam, mc.font);
			if (line.style.surface()) {
				drawSurfaceCarriers(out, poseStack, level, entry, phase, partial, cam, towing);
				continue;
			}
			ChairModel model = chairs.get(line.style);
			Identifier texture = switch (line.style) {
				case GONDOLA, GONDOLA_8 -> GONDOLA_TEXTURE;
				case PULSE_GONDOLA -> PULSE_TEXTURE;
				case SIX, QUAD_EXPRESS -> EXPRESS_TEXTURE;
				default -> CHAIR_TEXTURE;
			};
			Set<Integer> busy = occupied.getOrDefault(entry.netId, Set.of());
			double time = (double) level.getGameTime() + partial;
			for (int chair = 0; chair < line.chairCount(); chair++) {
				if (line.name.equals("Sky Cab") && line.style == ChairStyle.PULSE_GONDOLA) {
					texture = SKITTLES_TEXTURES[chair % SKITTLES_TEXTURES.length];
				}
				LiftLine.RopePoint rope = line.ropeAt(line.chairS(phase, chair));
				Vec3 p = rope.pos();
				if (p.distanceToSqr(cam) > CHAIR_VIEW * CHAIR_VIEW) {
					continue;
				}
				// gentle sway; more when the lift is moving fast or it's windy
				float swing = (float) (Math.sin(time * 0.07 + chair * 1.7) * 2.0 + Math.sin(time * 0.031 + chair) * 1.2) * (0.4f + entry.speed * 2f);
				poseStack.pushPose();
				poseStack.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
				poseStack.rotateDegrees(Axis.YP, 180f - rope.yaw());
				poseStack.rotateDegrees(Axis.XP, swing);
				poseStack.scale(-1f, -1f, 1f);
				if (line.style == ChairStyle.GONDOLA_8) {
					poseStack.scale(1.2f, 1f, 1.15f); // the eight-seat cabins are a size up
				}
				int light = light(level, BlockPos.containing(p.x, p.y - line.style.hanger + 0.5, p.z));
				out.submitModel(model, new ChairState(busy.contains(chair) ? 1f : 0f, swing), poseStack, texture, light, OverlayTexture.NO_OVERLAY, 0);
				if (line.style.cabin()) {
					out.submitModel(gondolaGlass, Unit.INSTANCE, poseStack, GONDOLA_GLASS_TEXTURE, light, OverlayTexture.NO_OVERLAY, 0);
				}
				poseStack.popPose();
			}
		}
	}

	/** Mid stations: the terminal housing, open at both ends, over the level stretch of rope through the station. */
	private static void drawMidStations(SubmitNodeCollector out, PoseStack poseStack, ClientLevel level, LiftLine line, Vec3 cam,
		net.minecraft.client.gui.Font font) {
		if (line.stations.isEmpty() || !TerminalRenderer.housed(line.style)) {
			return;
		}
		com.freshtracks.lift.Livery livery = line.livery();
		Identifier texture = TerminalRenderer.housingTexture(livery);
		for (int i = 0; i < line.stations.size() && i < line.midStations(); i++) {
			BlockPos st = line.stations.get(i);
			Vec3 c = Vec3.atBottomCenterOf(st);
			if (c.distanceToSqr(cam) > ROPE_VIEW * ROPE_VIEW) {
				continue;
			}
			LiftLine.RopePoint rope = line.ropeAt(line.midS(i));
			Vec3 flat = new Vec3(rope.direction().x, 0, rope.direction().z);
			Vec3 dir = flat.lengthSqr() > 1.0e-6 ? flat.normalize() : new Vec3(0, 0, 1);
			float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
			int light = light(level, st.above(4));
			poseStack.pushPose();
			poseStack.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
			poseStack.rotateDegrees(Axis.YP, 180f - yaw);
			poseStack.pushPose();
			poseStack.scale(-1f, -1f, 1f);
			out.submitModel(midHousing, Unit.INSTANCE, poseStack, texture, light, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
			TerminalRenderer.submitName(font, line.name, livery, light, poseStack, out, TerminalModels.MID_NAME_ALONG, 2 * TerminalModels.MID_HALF - 24f);
			poseStack.popPose();
		}
	}

	private static long towKey(int netId, int carrier) {
		return ((long) netId << 32) | (carrier & 0xffffffffL);
	}

	/**
	 * T-bars and platters: a spring box on the rope for each carrier, its line reeling out to the bar. A bar that's
	 * pulling someone reaches down behind their thighs (T-bar) or between their legs (platter); an idle one hangs
	 * folded up under the box.
	 */
	private static void drawSurfaceCarriers(SubmitNodeCollector out, PoseStack poseStack, ClientLevel level, ClientLifts.Entry entry,
		double phase, float partial, Vec3 cam, Map<Long, List<RigEntity>> towing) {
		LiftLine line = entry.line;
		ChairModel box = chairs.get(line.style);
		boolean platter = line.style == ChairStyle.PLATTER;
		List<Vec3[]> lines = new ArrayList<>();
		List<Vec3[]> bars = new ArrayList<>();
		for (int carrier = 0; carrier < line.chairCount(); carrier++) {
			double s = line.chairS(phase, carrier);
			LiftLine.RopePoint rope = line.ropeAt(s);
			Vec3 p = rope.pos();
			if (p.distanceToSqr(cam) > CHAIR_VIEW * CHAIR_VIEW) {
				continue;
			}
			poseStack.pushPose();
			poseStack.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
			poseStack.rotateDegrees(Axis.YP, 180f - rope.yaw());
			poseStack.scale(-1f, -1f, 1f);
			out.submitModel(box, new ChairState(0f, 0f), poseStack, SPRING_BOX_TEXTURE, light(level, BlockPos.containing(p)), OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();

			Vec3 flat = new Vec3(rope.direction().x, 0, rope.direction().z);
			Vec3 ahead = flat.lengthSqr() > 1.0e-6 ? flat.normalize() : new Vec3(0, 0, 1);
			Vec3 right = new Vec3(-ahead.z, 0, ahead.x);
			Vec3 boxBottom = p.subtract(0, LiftModels.SPRING_BOX_BOTTOM, 0);
			List<RigEntity> riders = towing.getOrDefault(towKey(entry.netId, carrier), List.of());
			Vec3 barCentre;
			if (riders.isEmpty()) {
				barCentre = boxBottom.subtract(0, platter ? 1.1 : 0.9, 0);
			} else {
				Vec3 sum = Vec3.ZERO;
				for (RigEntity rig : riders) {
					Vec3 at = rig.getPosition(partial);
					Vec3 hip = platter ? at.add(0, 0.62, 0).add(ahead.scale(0.05)) : at.add(0, 0.95, 0).subtract(ahead.scale(0.28));
					// slide each rider's contact point back to the middle of the bar
					sum = sum.add(hip.subtract(right.scale(line.style.seatOffset(rig.towSlot()))));
				}
				barCentre = sum.scale(1.0 / riders.size());
			}
			lines.add(new Vec3[]{boxBottom, barCentre});
			if (platter) {
				bars.add(new Vec3[]{barCentre.subtract(ahead.scale(0.04)), barCentre.add(ahead.scale(0.04)), new Vec3(0.2, 0, 0)});
			} else {
				bars.add(new Vec3[]{barCentre.subtract(right.scale(0.8)), barCentre.add(right.scale(0.8)), new Vec3(0.05, 0, 0)});
			}
		}
		int light = light(level, BlockPos.containing(line.ropePoints().getFirst()).above(1));
		out.submitCustomGeometry(poseStack, RenderTypes.entitySolid(ROPE_TEXTURE), (pose, buffer) -> {
			for (Vec3[] seg : lines) {
				tube(buffer, pose, seg[0].subtract(cam), seg[1].subtract(cam), 0.025f, light);
			}
		});
		out.submitCustomGeometry(poseStack, RenderTypes.entitySolid(platter ? PLATTER_TEXTURE : BAR_TEXTURE), (pose, buffer) -> {
			for (Vec3[] bar : bars) {
				tube(buffer, pose, bar[0].subtract(cam), bar[1].subtract(cam), (float) bar[2].x, light);
			}
		});
	}

	private static boolean near(LiftLine line, Vec3 cam, double range) {
		Vec3 a = line.ropePoints().getFirst();
		Vec3 b = line.ropePoints().getLast();
		// distance from camera to the segment between the terminals, horizontally
		double dx = b.x - a.x;
		double dz = b.z - a.z;
		double len2 = dx * dx + dz * dz;
		double t = len2 < 1e-6 ? 0 : Mth.clamp(((cam.x - a.x) * dx + (cam.z - a.z) * dz) / len2, 0, 1);
		double px = a.x + dx * t - cam.x;
		double pz = a.z + dz * t - cam.z;
		return px * px + pz * pz < range * range;
	}

	public static int light(ClientLevel level, BlockPos pos) {
		return LightCoordsUtil.getLightCoords(level, pos);
	}

	/** Both haul ropes as thin dark tubes following the sag between towers. */
	private static void drawRopes(SubmitNodeCollector out, PoseStack poseStack, ClientLevel level, LiftLine line, Vec3 cam) {
		int light = light(level, BlockPos.containing(line.ropePoints().getFirst()).above(2));
		double step = 1.5;
		double loop = line.loopLength();
		out.submitCustomGeometry(poseStack, RenderTypes.entitySolid(ROPE_TEXTURE), (pose, buffer) -> {
			Vec3 prev = line.ropeAt(0).pos();
			for (double s = step; s <= loop + 0.01; s += step) {
				Vec3 next = line.ropeAt(Math.min(s, loop)).pos();
				if (prev.distanceToSqr(cam) < ROPE_VIEW * ROPE_VIEW || next.distanceToSqr(cam) < ROPE_VIEW * ROPE_VIEW) {
					// camera-relative so far-out coordinates keep their precision
					tube(buffer, pose, prev.subtract(cam), next.subtract(cam), 0.045f, light);
				}
				prev = next;
			}
		});
	}

	private static void tube(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, float r, int light) {
		Vec3 d = b.subtract(a);
		if (d.lengthSqr() < 1e-8) {
			return;
		}
		Vec3 dir = d.normalize();
		Vec3 up = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 s1 = dir.cross(up).normalize().scale(r);
		Vec3 s2 = dir.cross(s1).normalize().scale(r);
		Vec3[] ring = {s1.add(s2), s1.subtract(s2), s1.scale(-1).subtract(s2), s1.scale(-1).add(s2)};
		for (int i = 0; i < 4; i++) {
			Vec3 o1 = ring[i];
			Vec3 o2 = ring[(i + 1) % 4];
			Vec3 n = o1.add(o2).normalize();
			quadVertex(buffer, pose, a.add(o1), n, light, 0, 0);
			quadVertex(buffer, pose, b.add(o1), n, light, 1, 0);
			quadVertex(buffer, pose, b.add(o2), n, light, 1, 1);
			quadVertex(buffer, pose, a.add(o2), n, light, 0, 1);
		}
	}

	private static void quadVertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, Vec3 n, int light, float u, float v) {
		buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
			.setColor(255, 255, 255, 255)
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light)
			.setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
	}
}
