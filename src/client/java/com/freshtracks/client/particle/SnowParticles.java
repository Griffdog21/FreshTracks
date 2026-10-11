package com.freshtracks.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** The mod's snow particles. */
public final class SnowParticles {
	private SnowParticles() {
	}

	/** A puff of snow kicked up by skis: flies out, slows in the air, sinks, fades. */
	public static class Spray extends SingleQuadParticle {
		private final SpriteSet sprites;
		private final float startSize;

		Spray(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
			super(level, x, y, z, xd, yd, zd, sprites.first());
			this.sprites = sprites;
			this.xd = xd;
			this.yd = yd;
			this.zd = zd;
			this.gravity = 0.09f;
			this.friction = 0.9f;
			this.lifetime = 18 + this.random.nextInt(18);
			this.startSize = 0.16f + this.random.nextFloat() * 0.24f;
			this.quadSize = startSize;
			float tint = 0.80f + this.random.nextFloat() * 0.12f; // a touch of shade so it reads against white snow
			this.setColor(tint, tint + 0.04f, 1.0f);
			this.setSpriteFromAge(sprites);
		}

		@Override
		public void tick() {
			super.tick();
			this.setSpriteFromAge(sprites);
			float life = (float) this.age / this.lifetime;
			this.alpha = 1f - life * life;
			this.quadSize = startSize * (1f + life * 0.6f);
		}

		@Override
		public SingleQuadParticle.Layer getLayer() {
			return SingleQuadParticle.Layer.TRANSLUCENT;
		}

		public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
			@Override
			public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z, double xa, double ya, double za, RandomSource random) {
				return new Spray(level, x, y, z, xa, ya, za, sprites);
			}
		}
	}

	/** Fine snow riding the wind: follows the velocity it's given, barely falls. */
	public static class WindSnow extends SingleQuadParticle {
		WindSnow(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
			super(level, x, y, z, xd, yd, zd, sprites.get(level.getRandom()));
			this.xd = xd;
			this.yd = yd;
			this.zd = zd;
			this.gravity = 0.004f;
			this.friction = 1.0f;
			this.lifetime = 30 + this.random.nextInt(30);
			this.quadSize = 0.035f + this.random.nextFloat() * 0.05f;
			this.hasPhysics = true;
			this.alpha = 0.0f;
		}

		@Override
		public void tick() {
			super.tick();
			float life = (float) this.age / this.lifetime;
			this.alpha = Mth.clamp(Math.min(life * 6f, (1f - life) * 3f), 0f, 0.9f);
			// a little turbulence
			this.yd += (this.random.nextFloat() - 0.5f) * 0.01f;
		}

		@Override
		public SingleQuadParticle.Layer getLayer() {
			return SingleQuadParticle.Layer.TRANSLUCENT;
		}

		public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
			@Override
			public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z, double xa, double ya, double za, RandomSource random) {
				return new WindSnow(level, x, y, z, xa, ya, za, sprites);
			}
		}
	}

	/** A glint of diamond dust: hangs in the air and twinkles. */
	public static class Sparkle extends SingleQuadParticle {
		private final SpriteSet sprites;

		Sparkle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
			super(level, x, y, z, 0, 0, 0, sprites.first());
			this.sprites = sprites;
			this.xd = (this.random.nextFloat() - 0.5f) * 0.01f;
			this.yd = -0.004f;
			this.zd = (this.random.nextFloat() - 0.5f) * 0.01f;
			this.gravity = 0f;
			this.friction = 1f;
			this.lifetime = 40 + this.random.nextInt(40);
			this.quadSize = 0.04f + this.random.nextFloat() * 0.04f;
			this.hasPhysics = false;
		}

		@Override
		public void tick() {
			super.tick();
			float life = (float) this.age / this.lifetime;
			this.alpha = Mth.clamp(Mth.sin(life * Mth.PI) * (0.6f + 0.4f * Mth.sin(this.age * 0.9f)), 0f, 1f);
			this.setSpriteFromAge(sprites);
		}

		@Override
		protected int getLightCoords(float partialTick) {
			return 0xF000F0;
		}

		@Override
		public SingleQuadParticle.Layer getLayer() {
			return SingleQuadParticle.Layer.TRANSLUCENT;
		}

		public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
			@Override
			public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z, double xa, double ya, double za, RandomSource random) {
				return new Sparkle(level, x, y, z, sprites);
			}
		}
	}
}
