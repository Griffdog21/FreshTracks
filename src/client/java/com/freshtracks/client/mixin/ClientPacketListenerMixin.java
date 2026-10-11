package com.freshtracks.client.mixin;

import com.freshtracks.lift.LiftSeatEntity;
import com.freshtracks.rig.RigEntity;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	/** Our own hint when you click into skis or sit on a lift (sneak brakes on skis rather than getting off). */
	@ModifyVariable(method = "handleSetEntityPassengersPacket", at = @At("STORE"), ordinal = 0)
	private String freshtracks$mountHint(String key, @Local(ordinal = 0) Entity vehicle) {
		if (vehicle instanceof RigEntity) {
			return "mount.freshtracks.rig";
		}
		if (vehicle instanceof LiftSeatEntity) {
			return "mount.freshtracks.chair";
		}
		return key;
	}
}
