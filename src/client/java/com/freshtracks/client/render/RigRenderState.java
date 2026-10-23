package com.freshtracks.client.render;

import com.freshtracks.rig.Grab;
import com.freshtracks.rig.RigKind;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;

public class RigRenderState extends EntityRenderState {
	public RigKind kind = RigKind.SKIS;
	public Identifier texture;
	public float yaw;
	public float lean;
	public float flip;
	public int flags;
	public Grab grab = Grab.NONE;
	public boolean ridden;
	public float slopePitch;
	public float slopeRoll;
	/** What the rider's legs are doing (skis splay for a skating stride or a herringbone climb), and how long for. */
	public com.freshtracks.rig.RiderAction action = com.freshtracks.rig.RiderAction.NONE;
	public float actionTime;
}
