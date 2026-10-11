package com.freshtracks.registry;

import com.freshtracks.FreshTracks;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public final class ModEffects {
	/** Toasty inside: no freezing in blizzards and cold snaps. From hot cocoa or a lodge fireplace. */
	public static final Holder<MobEffect> WARMTH = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, FreshTracks.id("warmth"),
		new SimpleEffect(MobEffectCategory.BENEFICIAL, 0xF2A65A));

	private ModEffects() {
	}

	public static void init() {
	}

	static final class SimpleEffect extends MobEffect {
		SimpleEffect(MobEffectCategory category, int color) {
			super(category, color);
		}
	}
}
