package com.freshtracks.client.lift;

import com.freshtracks.FreshTracks;
import com.freshtracks.lift.LiftLine;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Lift terminal housings, modelled on the real ones at Snowmass. A detachable lift's terminal is a long housing up on
 * columns: a ribbed fascia round the outside with rounded ends, a band of dark glass sloping up to a flat crown, the
 * bullwheel and the detach rails inside, the chairs swinging round underneath. Slots in the nose let the rope and the
 * chairs out. Mid stations get the same housing, open at both ends. Fixed-grip chairs and surface lifts have the old
 * style: the bullwheel on a single column under a domed hood.
 *
 * <p>Everything stays clear of the carriers: they hang from the rope (3.1 blocks up, {@link LiftLine#TRACK_HALF} out
 * from the centre line) and reach no higher than just above it, and no further in towards the centre line than 0.45
 * blocks, so the housing starts above the rope's grips and the columns stand on the centre line.
 *
 * <p>Vanilla model space: -Z forward (along the station, towards the line), +X left, +Y down, 16 units a block,
 * origin at the base of the terminal block (or the mid station's platform). UV spots mirror HOUSING_UV and FIXED_UV in
 * tools/art/lifts.py: each material is one painted patch that every box of that material reads from.
 */
public final class TerminalModels {
	public static final ModelLayerLocation HOUSING = new ModelLayerLocation(FreshTracks.id("lift_housing"), "main");
	public static final ModelLayerLocation MID_HOUSING = new ModelLayerLocation(FreshTracks.id("lift_mid_housing"), "main");
	public static final ModelLayerLocation FIXED = new ModelLayerLocation(FreshTracks.id("lift_fixed_terminal"), "main");

	/** Bullwheel radius (the rope's distance from the centre line). */
	static final float R = (float) (LiftLine.TRACK_HALF * 16);
	/** Half the housing's width. */
	static final float W = 48f;
	/** Where the straight sides end and the nose begins, and how far the nose reaches past that. */
	static final float NOSE_START = 136f;
	static final float NOSE = 32f;
	/** Mid stations: half the straight middle, and their noses. */
	static final float MID_HALF = 72f;
	static final float MID_NOSE = 24f;
	/** Heights: the fascia's bottom and top, the top of the glass band, the crown. */
	static final float H0 = 46f;
	static final float H1 = 68f;
	static final float H2 = 81f;
	static final float H3 = 86f;
	/** How far in from the outline the glass band and the metal band reach. */
	static final float GLASS_IN = 12f;
	static final float METAL_IN = 26f;
	/** The middle of the fascia (where the name goes), height and distance along the straight sides. */
	public static final float NAME_HEIGHT = (H0 + H1) / 2f;
	public static final float NAME_ALONG = NOSE_START / 2f;
	public static final float MID_NAME_ALONG = 0f;

	// UV patches (see tools/art/lifts.py HOUSING_UV)
	private static final int[] FASCIA = {0, 0};
	private static final int[] GLASS = {0, 24};
	private static final int[] METAL = {0, 44};
	private static final int[] CROWN = {0, 61};
	private static final int[] CEILING = {0, 107};
	private static final int[] COLUMN = {0, 154};
	private static final int[] CAPITAL = {48, 154};
	private static final int[] FOOTING = {48, 174};
	private static final int[] RAIL = {48, 198};
	private static final int[] DRIVE = {120, 154};
	private static final int[] BADGE = {210, 198};
	// FIXED_UV
	private static final int[] F_COLUMN = {0, 0};
	private static final int[] F_HOOD = {0, 60};
	private static final int[] F_DRIVE = {0, 120};

	private TerminalModels() {
	}

	/** One edge of a housing's outline, in (forward, lateral) model units, going round anticlockwise seen from above. */
	private record Edge(float f0, float l0, float f1, float l1, boolean slot, boolean curve) {
		float length() {
			return (float) Math.hypot(f1 - f0, l1 - l0);
		}

		/** Unit inward normal (forward, lateral). */
		float[] inward() {
			float len = length();
			float tf = (f1 - f0) / len;
			float tl = (l1 - l0) / len;
			return new float[]{-tl, tf};
		}
	}

	/** The terminal housing outline: round at the bullwheel end, straight sides, a flatter rounded nose. */
	private static List<Edge> terminalOutline() {
		List<Edge> edges = new ArrayList<>();
		arc(edges, 0f, W, W, 90, 270, 8, false);
		straight(edges, 0f, -W, NOSE_START, -W);
		arc(edges, NOSE_START, NOSE, W, -90, 90, 6, true);
		straight(edges, NOSE_START, W, 0f, W);
		return edges;
	}

	/** A mid station's outline: straight through the middle, a nose at each end. */
	private static List<Edge> midOutline() {
		List<Edge> edges = new ArrayList<>();
		arc(edges, -MID_HALF, MID_NOSE, W, 90, 270, 6, true);
		straight(edges, -MID_HALF, -W, MID_HALF, -W);
		arc(edges, MID_HALF, MID_NOSE, W, -90, 90, 6, true);
		straight(edges, MID_HALF, W, -MID_HALF, W);
		return edges;
	}

	/** Half an ellipse round (cf, 0) with semi-axes a (forward) and b (lateral), from angle a0 to a1 degrees. */
	private static void arc(List<Edge> edges, float cf, float a, float b, int a0, int a1, int steps, boolean slots) {
		for (int i = 0; i < steps; i++) {
			double t0 = Math.toRadians(a0 + (a1 - a0) * i / (double) steps);
			double t1 = Math.toRadians(a0 + (a1 - a0) * (i + 1) / (double) steps);
			float f0 = cf + (float) (a * Math.cos(t0));
			float l0 = (float) (b * Math.sin(t0));
			float f1 = cf + (float) (a * Math.cos(t1));
			float l1 = (float) (b * Math.sin(t1));
			// the rope (and the chairs on it) leave through slots where it crosses a nose
			boolean slot = slots && crosses(l0, l1, R);
			edges.add(new Edge(f0, l0, f1, l1, slot, true));
		}
	}

	private static boolean crosses(float l0, float l1, float rope) {
		float lo = Math.min(l0, l1) - 3f;
		float hi = Math.max(l0, l1) + 3f;
		return lo <= rope && rope <= hi || lo <= -rope && -rope <= hi;
	}

	/** A straight edge, cut into pieces no longer than 80 units (keeps each box's texture patch small). */
	private static void straight(List<Edge> edges, float f0, float l0, float f1, float l1) {
		float len = (float) Math.hypot(f1 - f0, l1 - l0);
		int n = Math.max(1, (int) Math.ceil(len / 80f));
		for (int i = 0; i < n; i++) {
			float a = i / (float) n;
			float b = (i + 1) / (float) n;
			edges.add(new Edge(f0 + (f1 - f0) * a, l0 + (l1 - l0) * a, f0 + (f1 - f0) * b, l0 + (l1 - l0) * b, false, false));
		}
	}

	/**
	 * A box laid along an edge: {@code len} along it, {@code thick} across it (towards the inside), {@code high} up.
	 * Centred on (forward f, lateral l, height h); tilted up towards the inside by {@code tilt} radians.
	 */
	private static void along(PartDefinition root, String name, int[] uv, float len, float high, float thick, float f, float l, float h,
		float[] inward, float tilt) {
		// model space: x = lateral, y = -height, z = -forward
		float nx = inward[1];
		float nz = -inward[0];
		float yaw = (float) Math.atan2(nx, nz);
		root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(uv[0], uv[1]).addBox(-len / 2f, -high / 2f, -thick / 2f, len, high, thick),
			PartPose.offsetAndRotation(l, -h, -f, tilt, yaw, 0f));
	}

	/** A flat or upright box, axis-aligned with the housing: {@code fl} long forward, {@code ll} wide, {@code hh} tall. */
	private static void block(PartDefinition root, String name, int[] uv, float f, float l, float h, float fl, float ll, float hh) {
		// long side along X keeps the texture patch short: turn it a quarter round when it runs forward
		if (fl > ll) {
			root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(uv[0], uv[1]).addBox(-fl / 2f, -hh / 2f, -ll / 2f, fl, hh, ll),
				PartPose.offsetAndRotation(l, -h, -f, 0f, (float) (Math.PI / 2), 0f));
		} else {
			root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(uv[0], uv[1]).addBox(-ll / 2f, -hh / 2f, -fl / 2f, ll, hh, fl),
				PartPose.offset(l, -h, -f));
		}
	}

	/** Fascia, glass band and metal band round an outline. */
	private static void shell(PartDefinition root, List<Edge> edges) {
		float glassRise = H2 - H1;
		float glassSlope = (float) Math.hypot(GLASS_IN, glassRise);
		float glassTilt = (float) Math.atan2(glassRise, GLASS_IN);
		float metalRise = H3 - H2;
		float metalRun = METAL_IN - GLASS_IN;
		float metalSlope = (float) Math.hypot(metalRun, metalRise);
		float metalTilt = (float) Math.atan2(metalRise, metalRun);
		for (int i = 0; i < edges.size(); i++) {
			Edge e = edges.get(i);
			float len = e.length();
			float[] in = e.inward();
			float mf = (e.f0 + e.f1) / 2f;
			float ml = (e.l0 + e.l1) / 2f;
			if (!e.slot) {
				along(root, "fascia" + i, FASCIA, len + 0.6f, H1 - H0, 2f, mf + in[0], ml + in[1], (H0 + H1) / 2f, in, 0f);
			}
			along(root, "glass" + i, GLASS, len + 0.5f, 1.5f, glassSlope, mf + in[0] * GLASS_IN / 2f, ml + in[1] * GLASS_IN / 2f,
				(H1 + H2) / 2f, in, glassTilt);
			// round the ends the metal band's pieces are shorter (nearer the middle), so they don't stick out past each other
			float shrink = e.curve ? 0.7f : 1f;
			along(root, "metal" + i, METAL, len * shrink + 0.5f, 1.5f, metalSlope, mf + in[0] * (GLASS_IN + METAL_IN) / 2f,
				ml + in[1] * (GLASS_IN + METAL_IN) / 2f, (H2 + H3) / 2f, in, metalTilt);
		}
	}

	/** Flat strips filling half an ellipse round (cf, 0), semi-axes a forward and b across, at height h. */
	private static void fill(PartDefinition root, String name, int[] uv, float cf, float a, float b, boolean forward, float h, float thick, int strips) {
		if (a <= 0.5f || b <= 0.5f) {
			return;
		}
		float step = a / strips;
		for (int k = 0; k < strips; k++) {
			float mid = (k + 0.5f) * step;
			float half = b * (float) Math.sqrt(Math.max(0, 1 - (mid / a) * (mid / a)));
			// a little wider than the exact chord at the strip's middle, so the stepped edge hides under the bands
			half = Math.min(b, half + 1f);
			float f = cf + (forward ? mid : -mid);
			block(root, name + k, uv, f, 0f, h, step + 0.02f, half * 2f, thick);
		}
	}

	// ------------------------------------------------------------------------------------------------ layers

	/** A detachable lift's terminal. Child "drive": the drive house on the roof (shown at the drive end only). */
	public static LayerDefinition housing() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		shell(root, terminalOutline());
		// the crown: flat on top, inside the metal band (in two halves to keep the texture patch small)
		float crownW = W - METAL_IN;
		for (int k = 0; k < 2; k++) {
			block(root, "crown" + k, CROWN, NOSE_START * (k + 0.5f) / 2f, 0f, H3 - 1.2f, NOSE_START / 2f + 0.5f, crownW * 2f, 2f);
		}
		fill(root, "crown_back", CROWN, 0f, crownW, crownW, false, H3 - 1.2f, 2f, 3);
		fill(root, "crown_nose", CROWN, NOSE_START, NOSE - METAL_IN, crownW, true, H3 - 1.2f, 2f, 1);
		// the ceiling seen from underneath, just inside the fascia
		float inner = W - 2f;
		for (int k = 0; k < 2; k++) {
			for (int side = -1; side <= 1; side += 2) {
				block(root, "ceiling" + k + (side < 0 ? "_r" : "_l"), CEILING, NOSE_START * (k + 0.5f) / 2f, side * inner / 2f, H1 - 0.5f,
					NOSE_START / 2f + 0.5f, inner, 1f);
			}
		}
		fill(root, "ceiling_back", CEILING, 0f, inner, inner, false, H1 - 0.5f, 1f, 5);
		fill(root, "ceiling_nose", CEILING, NOSE_START, NOSE - 2f, inner, true, H1 - 0.5f, 1f, 4);
		// columns on the centre line, where no carrier ever passes: under the bullwheel, mid-way, and near the nose
		column(root, "column0", 0f, 10f);
		column(root, "column1", NOSE_START / 2f, -40f);
		column(root, "column2", NOSE_START - 8f, -40f);
		block(root, "footing1", FOOTING, NOSE_START / 2f, 0f, 1f, 18f, 18f, 6f);
		block(root, "footing2", FOOTING, NOSE_START - 8f, 0f, 1f, 18f, 18f, 6f);
		// detach rails over the up and down tracks, above the grips
		float railLen = NOSE_START + 16f;
		for (int k = 0; k < 2; k++) {
			for (int side = -1; side <= 1; side += 2) {
				block(root, "rail" + k + (side < 0 ? "_r" : "_l"), RAIL, railLen * (k + 0.5f) / 2f, side * R, 55f, railLen / 2f + 0.2f, 3f, 2f);
			}
		}
		// a maker's badge on each side, near the nose
		for (int side = -1; side <= 1; side += 2) {
			block(root, "badge" + (side < 0 ? "_r" : "_l"), BADGE, NOSE_START - 4f, side * (W + 0.3f), NAME_HEIGHT, 4f, 1f, 4f);
		}
		// the drive house: motor, gearbox and brakes over the bullwheel
		PartDefinition drive = root.addOrReplaceChild("drive", CubeListBuilder.create(), PartPose.ZERO);
		block(drive, "house", DRIVE, -6f, 0f, H3 + 4f, 30f, 32f, 10f);
		return LayerDefinition.create(mesh, 256, 320);
	}

	private static void column(PartDefinition root, String name, float f, float from) {
		float top = H1 - 1f;
		block(root, name, COLUMN, f, 0f, (from + top) / 2f, 11f, 11f, top - from);
		block(root, name + "_cap", CAPITAL, f, 0f, top - 2f, 15f, 15f, 4f);
	}

	/** A mid station: the same housing, open at both ends, the rope running straight through. */
	public static LayerDefinition midHousing() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		shell(root, midOutline());
		float crownW = W - METAL_IN;
		for (int k = -1; k <= 1; k += 2) {
			block(root, "crown" + (k < 0 ? "_a" : "_b"), CROWN, k * MID_HALF / 2f, 0f, H3 - 1.2f, MID_HALF + 0.5f, crownW * 2f, 2f);
		}
		float inner = W - 2f;
		for (int k = -1; k <= 1; k += 2) {
			for (int side = -1; side <= 1; side += 2) {
				block(root, "ceiling" + k + "_" + side, CEILING, k * MID_HALF / 2f, side * inner / 2f, H1 - 0.5f, MID_HALF + 0.5f, inner, 1f);
			}
		}
		fill(root, "ceiling_a", CEILING, -MID_HALF, MID_NOSE - 2f, inner, false, H1 - 0.5f, 1f, 3);
		fill(root, "ceiling_b", CEILING, MID_HALF, MID_NOSE - 2f, inner, true, H1 - 0.5f, 1f, 3);
		column(root, "column0", 0f, -40f);
		for (int side = -1; side <= 1; side += 2) {
			for (int k = -1; k <= 1; k += 2) {
				block(root, "rail" + k + "_" + side, RAIL, k * MID_HALF / 2f, side * R, 55f, MID_HALF, 3f, 2f);
			}
		}
		return LayerDefinition.create(mesh, 256, 320);
	}

	/**
	 * A fixed-grip (or surface lift) terminal: the bullwheel on one column under a hood, stepped like the old riveted
	 * ones (plates turned an eighth of a turn each step, so it reads round). Child "drive": the motor house on top, at
	 * the drive end.
	 */
	public static LayerDefinition fixed() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("column", CubeListBuilder.create().texOffs(F_COLUMN[0], F_COLUMN[1]).addBox(-6f, -55f, -6f, 12f, 45f, 12f), PartPose.ZERO);
		// the skirt: two halves (keeps the texture patch small), its bottom edge above the grips
		for (int k = 0; k < 2; k++) {
			root.addOrReplaceChild("skirt" + k, CubeListBuilder.create().texOffs(F_HOOD[0], F_HOOD[1]).addBox(-38f, -58f, k == 0 ? -38f : 0f, 76f, 5f, 38f),
				PartPose.ZERO);
		}
		float quarter = (float) (Math.PI / 4);
		for (int k = 0; k < 2; k++) {
			root.addOrReplaceChild("step1_" + k, CubeListBuilder.create().texOffs(F_HOOD[0], F_HOOD[1]).addBox(-34f, -61f, k == 0 ? -34f : 0f, 68f, 3f, 34f),
				PartPose.rotation(0f, quarter, 0f));
		}
		root.addOrReplaceChild("step2", CubeListBuilder.create().texOffs(F_HOOD[0], F_HOOD[1]).addBox(-26f, -64f, -26f, 52f, 3f, 52f), PartPose.ZERO);
		root.addOrReplaceChild("step3", CubeListBuilder.create().texOffs(F_HOOD[0], F_HOOD[1]).addBox(-16f, -67f, -16f, 32f, 3f, 32f),
			PartPose.rotation(0f, quarter, 0f));
		PartDefinition drive = root.addOrReplaceChild("drive", CubeListBuilder.create(), PartPose.ZERO);
		drive.addOrReplaceChild("house", CubeListBuilder.create().texOffs(F_DRIVE[0], F_DRIVE[1]).addBox(-13f, -81f, -10f, 26f, 14f, 20f),
			PartPose.ZERO);
		return LayerDefinition.create(mesh, 256, 256);
	}
}
