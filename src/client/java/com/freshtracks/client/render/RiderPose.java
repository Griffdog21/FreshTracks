package com.freshtracks.client.render;

import com.freshtracks.rig.Grab;
import com.freshtracks.rig.RiderAction;
import com.freshtracks.rig.RigEntity;
import com.freshtracks.rig.RigKind;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

/**
 * What the rider's model needs to know about the rig under them, attached to the player's render state: the stance, and
 * what the arms and legs are doing ({@code action}, {@code actionTime} ticks into it), and whether they have poles.
 */
public record RiderPose(RigKind kind, float rigYaw, float lean, float flip, int flags, Grab grab, float age, float slopePitch, float slopeRoll,
	RiderAction action, float actionTime, boolean poles) {
	public static final RenderStateDataKey<RiderPose> KEY = RenderStateDataKey.create(() -> "freshtracks:rider_pose");

	public boolean has(int flag) {
		return (flags & flag) != 0;
	}

	public boolean airborne() {
		return has(RigEntity.FLAG_AIR);
	}
}
