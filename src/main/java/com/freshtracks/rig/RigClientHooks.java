package com.freshtracks.rig;

import net.minecraft.world.entity.Entity;

/**
 * Bridge from shared code to client-only code (effects, camera, networking from the rider's client).
 * The client initializer installs the real implementation; on a dedicated server these do nothing.
 */
public final class RigClientHooks {
	public interface Hooks {
		void afterLocalStep(RigEntity rig);

		void effects(RigEntity rig);

		void onRiderRemoved(RigEntity rig, Entity passenger);

		float cameraFollow();
	}

	private static Hooks hooks = new Hooks() {
		@Override
		public void afterLocalStep(RigEntity rig) {
		}

		@Override
		public void effects(RigEntity rig) {
		}

		@Override
		public void onRiderRemoved(RigEntity rig, Entity passenger) {
		}

		@Override
		public float cameraFollow() {
			return 1f;
		}
	};

	private RigClientHooks() {
	}

	public static void install(Hooks implementation) {
		hooks = implementation;
	}

	static void afterLocalStep(RigEntity rig) {
		hooks.afterLocalStep(rig);
	}

	static void effects(RigEntity rig) {
		hooks.effects(rig);
	}

	static void onRiderRemoved(RigEntity rig, Entity passenger) {
		hooks.onRiderRemoved(rig, passenger);
	}

	static float cameraFollow() {
		return hooks.cameraFollow();
	}
}
