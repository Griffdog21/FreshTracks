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
	public static final Identifier GONDOLA_BLACK_TEXTURE = FreshTracks.id("textures/entity/lift/gondola_black.png");
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
	/** The skiers riding the lifts: a seated player model in ski gear, and the skis or board hanging off their boots. */
	private static net.minecraft.client.model.player.PlayerModel riderModel;
	private static net.minecraft.client.renderer.entity.state.AvatarRenderState riderPose;
	private static com.freshtracks.client.render.RigRenderer.RigModel riderSkis;
	private static com.freshtracks.client.render.RigRenderer.RigModel riderBoard;
	private static final Identifier[] RIDER_SKINS = new Identifier[8];
	private static final net.minecraft.world.item.Item[] RIDER_SKIS = {com.freshtracks.registry.ModItems.ALPINE_SKIS, com.freshtracks.registry.ModItems.CARVING_SKIS,
		com.freshtracks.registry.ModItems.POWDER_SKIS, com.freshtracks.registry.ModItems.FREERIDE_SKIS, com.freshtracks.registry.ModItems.RACE_SKIS,
		com.freshtracks.registry.ModItems.PARK_SKIS};
	private static final net.minecraft.world.item.Item[] RIDER_BOARDS = {com.freshtracks.registry.ModItems.ALL_MOUNTAIN_BOARD,
		com.freshtracks.registry.ModItems.FREERIDE_BOARD, com.freshtracks.registry.ModItems.PARK_BOARD};

	static {
		for (int i = 0; i < RIDER_SKINS.length; i++) {
			RIDER_SKINS[i] = FreshTracks.id("textures/entity/rider/rider_" + i + ".png");
		}
	}
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
		riderModel = new net.minecraft.client.model.player.PlayerModel(set.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER), false);
		riderPose = new net.minecraft.client.renderer.entity.state.AvatarRenderState();
		riderPose.isPassenger = true;
		riderPose.speedValue = 1f;
		riderPose.showHat = true;
		riderSkis = new com.freshtracks.client.render.RigRenderer.RigModel(set.bakeLayer(com.freshtracks.client.render.RigModels.SKIS));
		riderBoard = new com.freshtracks.client.render.RigRenderer.RigModel(set.bakeLayer(com.freshtracks.client.render.RigModels.BOARD));
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
				case GONDOLA, GONDOLA_8 -> line.cabin == com.freshtracks.lift.Cabin.BLACK ? GONDOLA_BLACK_TEXTURE : GONDOLA_TEXTURE;
				case PULSE_GONDOLA -> PULSE_TEXTURE;
				case SIX, QUAD_EXPRESS -> EXPRESS_TEXTURE;
				default -> CHAIR_TEXTURE;
			};
			Set<Integer> busy = occupied.getOrDefault(entry.netId, Set.of());
			double time = (double) level.getGameTime() + partial;
			for (int chair = 0; chair < line.chairCount(); chair++) {
				if (line.name.equals("Sky Cab") && line.style == ChairStyle.PULSE_GONDOLA) {
					// each group of three is one colour, like the real Skittles
					texture = SKITTLES_TEXTURES[(chair / LiftLine.PULSE_CABINS) % SKITTLES_TEXTURES.length];
				}
				double chairS = line.chairS(phase, chair);
				LiftLine.RopePoint rope = line.ropeAt(chairS);
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
				int riders = busy.contains(chair) ? 0 : riders(line, entry.netId, chair, chairS);
				if (riders > 0) {
					drawRiders(out, poseStack, line, entry.netId, chair, riders, time, BlockPos.containing(p.x, p.y - line.style.hanger + 0.5, p.z), level);
				}
				poseStack.scale(-1f, -1f, 1f);
				if (line.style == ChairStyle.GONDOLA_8) {
					poseStack.scale(1.2f, 1f, 1.15f); // the eight-seat cabins are a size up
				}
				int light = light(level, BlockPos.containing(p.x, p.y - line.style.hanger + 0.5, p.z));
				out.submitModel(model, new ChairState(busy.contains(chair) || riders > 0 ? barDown(line, chairS) : 0f, swing), poseStack, texture, light, OverlayTexture.NO_OVERLAY, 0);
				if (line.style.cabin()) {
					out.submitModel(gondolaGlass, Unit.INSTANCE, poseStack, GONDOLA_GLASS_TEXTURE, light, OverlayTexture.NO_OVERLAY, 0);
				}
				poseStack.popPose();
			}
		}
	}

	/** How many made-up skiers ride this chair right now (0 for most, and for any chair outside its trip up). */
	private static int riders(LiftLine line, int netId, int chair, double s) {
		double share = com.freshtracks.FreshTracksConfig.liftRiders;
		if (share <= 0 || line.style.seats < 1) {
			return 0;
		}
		long h = mix(netId * 0x9E3779B97F4A7C15L + chair * 0xC2B2AE3D27D4EB4FL);
		if ((h & 0xFFFF) / 65536.0 >= share) {
			return 0;
		}
		double loop = line.loopLength();
		double sinceLoad = ((s - line.loadS()) % loop + loop) % loop;
		double upTrip = ((line.unloadS() - line.loadS()) % loop + loop) % loop;
		if (sinceLoad < 1.5 || sinceLoad > upTrip - 0.5) {
			return 0; // empty from the top round to the loading point
		}
		int seats = line.style.seats;
		double r = (h >>> 16 & 0xFFFF) / 65536.0;
		return r < 0.4 ? 1 : r < 0.75 ? Math.min(2, seats) : 1 + (int) ((h >>> 32 & 0xFF) % seats);
	}

	/**
	 * Other skiers on the lift, the way a lift is on any day the mountain's open: some of the chairs (and cabins) going up
	 * carry one to a full load of riders in ski gear, boards and skis hanging off their boots; they ride from the loading
	 * point to the top and get off there. The same chair always carries the same people, and a chair someone is really
	 * riding has no one made up on it. Drawn in the chair's own frame, so they swing with it.
	 */
	private static void drawRiders(SubmitNodeCollector out, PoseStack poseStack, LiftLine line, int netId, int chair, int count, double time,
		BlockPos lightAt, ClientLevel level) {
		long h = mix(netId * 0x9E3779B97F4A7C15L + chair * 0xC2B2AE3D27D4EB4FL);
		int seats = line.style.seats;
		int first = (int) ((h >>> 40 & 0xFF) % Math.max(1, seats - count + 1));
		int light = light(level, lightAt);
		for (int k = 0; k < count; k++) {
			int seat = first + k;
			long rh = mix(h + seat * 0x632BE59BD9B4E019L);
			double back = line.style.cabin() ? (seat < Math.max(1, seats / 2) ? 0.6 : -0.6) : 0.15;
			poseStack.pushPose();
			// the seat, in the chair's frame, where a real rider's feet go (LiftLine.seatPos): across, down the hanger, a little back
			poseStack.translate(line.style.seatOffset(seat), -line.style.hanger, back);
			if (line.style.cabin() && seat >= Math.max(1, seats / 2)) {
				poseStack.rotateDegrees(Axis.YP, 180f); // the front bench faces back across the cabin
			}
			poseStack.scale(-1f, -1f, 1f);
			poseStack.translate(0f, -1.501f, 0f);
			riderPose.yRot = (float) ((rh >>> 8 & 0xFF) / 255.0 * 70 - 35);
			riderPose.xRot = (float) ((rh >>> 16 & 0xFF) / 255.0 * 30 - 10);
			riderModel.setupAnim(riderPose);
			// sat on a chair, not a horse: knees down, boots together, and now and then a lazy swing of the legs
			float swingLegs = (rh >>> 44 & 0x3) == 0 ? Mth.sin((float) (time * 0.09 + (rh >>> 48 & 0xFF))) * 0.16f : 0f;
			float legs = -0.95f + (float) ((rh >>> 52 & 0xFF) / 255.0 * 0.2 - 0.1);
			riderModel.rightLeg.xRot = legs + swingLegs;
			riderModel.leftLeg.xRot = legs - swingLegs;
			riderModel.rightLeg.yRot = 0.04f;
			riderModel.leftLeg.yRot = -0.04f;
			riderModel.rightLeg.zRot = 0.02f;
			riderModel.leftLeg.zRot = -0.02f;
			out.submitModel(riderModel, riderPose, poseStack, RIDER_SKINS[(int) ((rh >>> 24) & 0x7)], light, OverlayTexture.NO_OVERLAY, 0);
			if (!line.style.cabin()) {
				// skis hanging level off the boots, tips a little down; a board hangs off the front foot, across and nose down
				boolean board = (rh >>> 32 & 0x3) == 0;
				float footY = (12f + 12f * Mth.cos(legs)) / 16f;
				float footZ = 12f * Mth.sin(legs) / 16f;
				if (board) {
					poseStack.translate(0.12f, footY + 0.12f, footZ - 0.02f);
					poseStack.rotateDegrees(Axis.YP, 62f);
					poseStack.rotateDegrees(Axis.XP, 24f);
					poseStack.translate(0f, 0f, 4.5f / 16f); // the front binding on the boot, the rest of the board behind it
				} else {
					poseStack.translate(0f, footY + 0.14f, footZ + 0.04f);
					poseStack.rotateDegrees(Axis.XP, 14f);
				}
				net.minecraft.world.item.Item item = board ? RIDER_BOARDS[(int) ((rh >>> 36) % RIDER_BOARDS.length)] : RIDER_SKIS[(int) ((rh >>> 36) % RIDER_SKIS.length)];
				com.freshtracks.client.render.RigRenderState skiState = new com.freshtracks.client.render.RigRenderState();
				out.submitModel(board ? riderBoard : riderSkis, skiState, poseStack, com.freshtracks.client.render.RigRenderer.textureFor(item), light, OverlayTexture.NO_OVERLAY, 0);
			}
			poseStack.popPose();
		}
	}

	/** A well-mixed 64-bit hash (splitmix64's finaliser). */
	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/**
	 * How far a ridden chair's safety bar is down: riders pull it down once they're clear of the loading station and
	 * push it up again as the top comes near ("prepare to unload"), the same on the way back down.
	 */
	static float barDown(LiftLine line, double s) {
		double up = line.upLength();
		if (s <= up) {
			// (some lifts load round the bullwheel, just before the loop comes back to the start)
			double loop = line.loopLength();
			double sinceLoad = ((s - line.loadS()) % loop + loop) % loop;
			return (float) (smooth(sinceLoad, 6, 14) * (1 - smooth(s - line.unloadS(), -24, -15)));
		}
		double down = s - up;
		return (float) smooth(down, 10, 18) * (float) (1 - smooth(down, up - 26, up - 17));
	}

	private static double smooth(double x, double a, double b) {
		double t = Mth.clamp((x - a) / (b - a), 0, 1);
		return t * t * (3 - 2 * t);
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
