package com.freshtracks.economy;

import com.freshtracks.rig.RigStats;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A pro-model tune: multipliers on a pair of skis' or a board's stats (1.0 = as the stock model rides), plus extra
 * warmth on a jacket, pants, helmet or boots. Branded shop items carry one.
 */
public record Tune(float speed, float grip, float turn, float flotation, float pop, float spin, float push, int warmth) {
	public static final Tune NONE = new Tune(1, 1, 1, 1, 1, 1, 1, 0);

	public static final Codec<Tune> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.FLOAT.optionalFieldOf("speed", 1f).forGetter(Tune::speed),
		Codec.FLOAT.optionalFieldOf("grip", 1f).forGetter(Tune::grip),
		Codec.FLOAT.optionalFieldOf("turn", 1f).forGetter(Tune::turn),
		Codec.FLOAT.optionalFieldOf("flotation", 1f).forGetter(Tune::flotation),
		Codec.FLOAT.optionalFieldOf("pop", 1f).forGetter(Tune::pop),
		Codec.FLOAT.optionalFieldOf("spin", 1f).forGetter(Tune::spin),
		Codec.FLOAT.optionalFieldOf("push", 1f).forGetter(Tune::push),
		Codec.INT.optionalFieldOf("warmth", 0).forGetter(Tune::warmth)
	).apply(i, Tune::new));

	public static final StreamCodec<ByteBuf, Tune> STREAM_CODEC = ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list()).map(
		l -> new Tune(l.get(0), l.get(1), l.get(2), l.get(3), l.get(4), l.get(5), l.get(6), Math.round(l.get(7))),
		t -> List.of(t.speed, t.grip, t.turn, t.flotation, t.pop, t.spin, t.push, (float) t.warmth));

	/** Skis/board tune: speed, grip, turn, flotation, pop, spin. */
	public static Tune rig(float speed, float grip, float turn, float flotation, float pop, float spin) {
		return new Tune(speed, grip, turn, flotation, pop, spin, 1f, 0);
	}

	public static Tune warmth(int extra) {
		return new Tune(1, 1, 1, 1, 1, 1, 1, extra);
	}

	public RigStats apply(RigStats s) {
		return new RigStats(s.speed() * speed, s.grip() * grip, s.turn() * turn, s.flotation() * flotation, s.pop() * pop,
			s.spin() * spin, s.push() * push);
	}
}
