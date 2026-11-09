package com.mystaria.showtime.move;

import org.joml.Matrix4f;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Shadow Ball, after the main games: dark wisps spiral into the user's mouth and gather into a black orb wrapped in a
 * violet glow, crackling; it is hurled with a wobble, trailing dark smoke, and bursts on the target in a violet
 * shockwave and a spray of shadow. Paced by Cobblemon's own timeline (about two seconds): its first particle starts the
 * charge, the target's particles launch the orb, the impact particle (sent only on a hit) bursts it.
 */
final class ShadowBall extends MoveEffect {

	private static final int VIOLET = 0x9B3CFF;
	private static final int PALE = 0xD9A8FF;
	private static final int CORE = 0x1C0830;
	/** Ticks the orb takes to grow, and to fly. */
	private static final int CHARGE = 30;
	private static final int FLIGHT = 6;
	/** Without a target cue by then, launched at the nearest other Pokémon. */
	private static final int LAUNCH_FALLBACK = 40;
	/** How long the orb may wait at the target for the hit cue before fading (a miss sends none). */
	private static final int HIT_WAIT = 6;
	private static final int BURST = 16;
	private static final int WISPS = 12;

	private final RandomSource random = RandomSource.create();
	private final float radius;
	private int launchAge = -1;
	private int arriveAge = -1;
	private int burstAge = -1;
	private boolean hit;
	private Vec3 launchFrom;

	ShadowBall(String move, Entity user) {
		super(move, user);
		radius = Mth.clamp(0.22f + 0.18f * user.getBbHeight(), 0.28f, 0.8f);
	}

	@Override
	protected void cue(String part, Entity source, Entity targeted) {
		if (part.startsWith("target")) {
			if (target == null) {
				Entity found = targeted != null && targeted != user ? targeted : source != user ? source : null;
				if (found != null) {
					target = found;
				}
			}
			if (launchAge < 0) {
				launch();
			}
			if (part.equals("targetimpact")) {
				hit = true;
			}
		}
	}

	private void launch() {
		launchAge = age;
		launchFrom = orbAtUser(1f);
		arriveAge = age + FLIGHT;
	}

	@Override
	protected void tick() {
		age++;
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		if (launchAge < 0) {
			if (age >= LAUNCH_FALLBACK) {
				if (target == null && targetPoint == null) {
					target = MoveEffects.defaultTarget(user, level);
				}
				launch();
			} else if (targetPoint != null && age >= CHARGE + 4) {
				hit = true; // preview: always a hit
				launch();
			}
		}
		Vec3 orb = orb(1f);
		if (burstAge < 0) {
			if (launchAge < 0) {
				// Charging: violet sparks drawn into the orb.
				if (random.nextFloat() < 0.6f) {
					Vec3 from = orb.add(randomDirection().scale(radius * 4));
					Vec3 pull = orb.subtract(from).scale(0.12);
					level.addParticle(ParticleTypes.WITCH, from.x, from.y, from.z, pull.x, pull.y, pull.z);
				}
			} else {
				// In flight: dark smoke left behind.
				for (int i = 0; i < 3; i++) {
					Vec3 at = orb.add(randomDirection().scale(radius * 0.6));
					level.addParticle(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 0, 0.01, 0);
				}
				level.addParticle(ParticleTypes.WITCH, orb.x, orb.y, orb.z, 0, 0, 0);
				if (age >= arriveAge && (hit || age >= arriveAge + HIT_WAIT)) {
					burstAge = age;
					if (hit) {
						burst(level, orb);
					}
				}
			}
		}
	}

	private void burst(ClientLevel level, Vec3 at) {
		for (int i = 0; i < 40; i++) {
			Vec3 velocity = randomDirection().scale(0.15 + random.nextFloat() * 0.35);
			level.addParticle(i % 3 == 0 ? ParticleTypes.WITCH : ParticleTypes.SQUID_INK, at.x, at.y, at.z,
					velocity.x, velocity.y, velocity.z);
		}
		for (int i = 0; i < 12; i++) {
			Vec3 velocity = randomDirection().scale(0.05);
			level.addParticle(ParticleTypes.SOUL, at.x, at.y, at.z, velocity.x, velocity.y + 0.04, velocity.z);
		}
	}

	@Override
	protected boolean awaitingHit() {
		return burstAge < 0;
	}

	@Override
	protected boolean finished() {
		return user.isRemoved() || age > 160 || (burstAge >= 0 && age - burstAge > BURST);
	}

	// ---------------------------------------------------------------- where

	/** The orb's centre this frame. */
	private Vec3 orb(float partialTick) {
		float t = age + partialTick;
		if (launchAge < 0) {
			return orbAtUser(partialTick);
		}
		Vec3 to = aim();
		float k = Mth.clamp((t - launchAge) / FLIGHT, 0f, 1f);
		Vec3 path = launchFrom.lerp(to, k);
		// A wobble across the path, gone on arrival.
		Vec3 across = to.subtract(launchFrom).cross(new Vec3(0, 1, 0));
		across = across.lengthSqr() < 1.0E-4 ? Vec3.ZERO : across.normalize();
		double wobble = Math.sin(k * Math.PI * 3) * (1 - k) * radius * 0.8;
		return path.add(across.scale(wobble)).add(0, Math.sin(k * Math.PI) * radius, 0);
	}

	/** In front of the user's mouth, towards the target (or where the user looks). */
	private Vec3 orbAtUser(float partialTick) {
		Vec3 base = user.getPosition(partialTick).add(0, user.getBbHeight() * 0.65, 0);
		Vec3 toward = aim().subtract(base).multiply(1, 0, 1);
		if (target == null && targetPoint == null || toward.lengthSqr() < 1.0E-4) {
			toward = user.getViewVector(partialTick).multiply(1, 0, 1);
		}
		toward = toward.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : toward.normalize();
		return base.add(toward.scale(user.getBbWidth() * 0.6 + radius * 1.2));
	}

	private Vec3 aim() {
		if (target != null && !target.isRemoved()) {
			return bodyCentre(target);
		}
		if (targetPoint != null) {
			return targetPoint;
		}
		return user.position().add(user.getViewVector(1f).multiply(6, 0, 6)).add(0, user.getBbHeight() * 0.5, 0);
	}


	private Vec3 randomDirection() {
		return new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
	}

	// ---------------------------------------------------------------- drawing

	@Override
	protected void render(PoseStack pose, MultiBufferSource buffers, Vec3 camera, float partialTick) {
		Matrix4f m = pose.last().pose();
		float t = age + partialTick;
		Vec3 orb = orb(partialTick);
		VertexConsumer glow = buffers.getBuffer(Draw.GLOW);
		if (burstAge >= 0) {
			float k = Mth.clamp((t - burstAge) / BURST, 0f, 1f);
			if (!hit) {
				// Missed: the orb fades out where it went.
				Draw.halo(glow, m, orb, camera, 0, radius * 2.5f * (1 - k), VIOLET, (int) (140 * (1 - k)));
				return;
			}
			float fade = 1 - k;
			// Shockwave: two rings, one facing the camera, one flat on the ground.
			Draw.ring(glow, m, orb, camera.subtract(orb).normalize(), radius * (1.2f + 5f * k), radius * 0.45f, VIOLET,
					(int) (220 * fade));
			Draw.ring(glow, m, orb, new Vec3(0, 1, 0), radius * (1f + 7f * k), radius * 0.6f, PALE, (int) (160 * fade));
			Draw.halo(glow, m, orb, camera, 0, radius * (2f + 4f * k), VIOLET, (int) (200 * fade * fade));
			// The orb, swelling as it breaks.
			if (k < 0.45f) {
				float s = 1 + k * 2.5f;
				Draw.sphere(buffers.getBuffer(Draw.DARK), m, orb, radius * s, CORE, (int) (230 * (1 - k / 0.45f)), false);
			}
			return;
		}
		float grow = launchAge < 0 ? Mth.clamp(t / CHARGE, 0f, 1f) : 1f;
		grow = grow * grow * (3 - 2 * grow);
		float r = radius * Math.max(0.05f, grow);
		float pulse = 1 + 0.08f * Mth.sin(t * 1.3f);
		if (launchAge < 0) {
			// Wisps spiralling in.
			for (int i = 0; i < WISPS; i++) {
				float phase = ((t * 0.045f + i / (float) WISPS) % 1f);
				float distance = radius * (0.8f + 3.2f * (1 - phase));
				float angle = i * 2.4f + t * 0.25f + phase * 4f;
				float tilt = (i % 4 - 1.5f) * 0.6f;
				Vec3 dir = new Vec3(Math.cos(angle), Math.sin(angle * 0.7f + tilt) * 0.6, Math.sin(angle)).normalize();
				Vec3 head = orb.add(dir.scale(distance));
				Vec3 tail = orb.add(new Vec3(Math.cos(angle - 0.5f), dir.y, Math.sin(angle - 0.5f)).normalize().scale(distance + radius * 0.9f));
				Draw.streak(glow, m, head, tail, camera, radius * 0.07f, PALE, (int) (200 * Math.sin(phase * Math.PI)));
			}
		} else {
			// Trail: fading echoes of the orb along its path.
			for (int i = 1; i <= 5; i++) {
				float back = i * 0.6f;
				if (t - back < launchAge) {
					break;
				}
				Vec3 echo = orbAt(t - back);
				Draw.halo(glow, m, echo, camera, 0, r * (1.6f - i * 0.15f), VIOLET, 110 - i * 18);
			}
		}
		// Glow around the orb, then the dark core, then crackles on its surface.
		Draw.halo(glow, m, orb, camera, r * 0.9f, r * 2.4f * pulse, VIOLET, 170);
		Draw.halo(glow, m, orb, camera, r * 0.95f, r * 1.35f, PALE, 120);
		Draw.sphere(glow, m, orb, r * 1.12f * pulse, VIOLET, 40, true);
		Draw.sphere(buffers.getBuffer(Draw.DARK), m, orb, r, CORE, 235, false);
		glow = buffers.getBuffer(Draw.GLOW);
		RandomSource crackle = RandomSource.create((long) (t / 2));
		for (int i = 0; i < 4; i++) {
			Vec3 a = new Vec3(crackle.nextGaussian(), crackle.nextGaussian(), crackle.nextGaussian()).normalize();
			Vec3 b = a.add(new Vec3(crackle.nextGaussian(), crackle.nextGaussian(), crackle.nextGaussian()).scale(0.5)).normalize();
			Draw.streak(glow, m, orb.add(a.scale(r * 1.02f)), orb.add(b.scale(r * 1.4f)), camera, r * 0.05f, PALE, 220);
		}
	}

	/** The orb's centre at an earlier time (the trail), in flight. */
	private Vec3 orbAt(float time) {
		int saved = age;
		age = (int) Math.floor(time);
		Vec3 at = orb(time - age);
		age = saved;
		return at;
	}
}
