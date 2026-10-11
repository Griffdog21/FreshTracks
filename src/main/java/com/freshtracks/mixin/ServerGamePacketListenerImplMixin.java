package com.freshtracks.mixin;

import com.freshtracks.rig.RigEntity;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	/**
	 * Skis do their own collision on the rider's client (and brush through tree branches on purpose), so the
	 * server's "vehicle moved into a block" check would yank riders back out of every glade.
	 */
	@Inject(method = "isEntityCollidingWithAnythingNew", at = @At("HEAD"), cancellable = true)
	private void freshtracks$trustRigCollision(LevelReader level, Entity entity, AABB oldAABB, double newX, double newY, double newZ,
		CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof RigEntity) {
			cir.setReturnValue(false);
		}
	}
}
