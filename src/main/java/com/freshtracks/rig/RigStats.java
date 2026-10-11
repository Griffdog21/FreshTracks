package com.freshtracks.rig;

/**
 * How a pair of skis or a board rides. 1.0 is an all-mountain baseline.
 *
 * @param speed   less drag, higher top speed
 * @param grip    edge hold on hardpack and ice
 * @param turn    tighter carving radius
 * @param flotation how well it rides on top of powder instead of bogging down
 * @param pop     ollie and kicker launch strength
 * @param spin    spin and flip rate in the air
 */
public record RigStats(float speed, float grip, float turn, float flotation, float pop, float spin, float push) {
	public static final RigStats BASELINE = new RigStats(1f, 1f, 1f, 1f, 1f, 1f, 1f);

	public RigStats(float speed, float grip, float turn, float flotation, float pop, float spin) {
		this(speed, grip, turn, flotation, pop, spin, 1f);
	}

	public RigStats times(float speed, float grip, float turn, float pop, float push) {
		return new RigStats(this.speed * speed, this.grip * grip, this.turn * turn, flotation, this.pop * pop, spin, this.push * push);
	}
}
