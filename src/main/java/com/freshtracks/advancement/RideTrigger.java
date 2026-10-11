package com.freshtracks.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/** One trigger for everything that happens on the mountain: {"trigger": "freshtracks:ride", "conditions": {"event": "lift"}}. */
public class RideTrigger extends SimpleCriterionTrigger<RideTrigger.TriggerInstance> {
	@Override
	public Codec<TriggerInstance> codec() {
		return TriggerInstance.CODEC;
	}

	public void trigger(ServerPlayer player, String event) {
		this.trigger(player, instance -> instance.event().equals(event));
	}

	public record TriggerInstance(Optional<Holder<LootItemCondition>> player, String event) implements SimpleCriterionTrigger.SimpleInstance {
		public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
			LootItemCondition.CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
			Codec.STRING.fieldOf("event").forGetter(TriggerInstance::event)
		).apply(i, TriggerInstance::new));
	}
}
