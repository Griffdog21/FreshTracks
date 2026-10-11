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
}
