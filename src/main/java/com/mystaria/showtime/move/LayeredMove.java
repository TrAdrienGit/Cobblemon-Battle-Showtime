package com.mystaria.showtime.move;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A move drawn from its {@link MoveSpec}: its layers, each a {@link Motifs motif} in one phase, on Cobblemon's own
 * timeline. The user winds up ({@code wind}), the effect travels ({@code travel}), and the hit ({@code hit}) lands on
 * Cobblemon's hit cue — its impact particle, about 1.2 s in for a move without its own animation; a move whose cue
 * never comes missed and shows no hit. Moves on the user alone play their {@code self} layers for {@link #SELF} ticks.
 */
final class LayeredMove extends MoveEffect {

	static final int WIND = 12;
	static final int ARRIVE = 20;
	/** The hit waits for Cobblemon's cue at most until then. */
	private static final int HIT_LATEST = 34;
	static final int AFTER = 22;
	static final int SELF = 34;

	final MoveSpec spec;
	final int color;
	final int accent;
	final float size;
	final RandomSource random = RandomSource.create();
	private final boolean self;
	private final boolean status;
	private boolean hit;
	private int hitAge = -1;

	LayeredMove(String move, Entity user, MoveSpec spec) {
		super(move, user);
		this.spec = spec;
		this.color = MoveSpec.color(spec.color, 0xFFFFFF);
		this.accent = MoveSpec.color(spec.accent, 0xFFFFFF);
		this.size = Mth.clamp(spec.size, 0.3f, 4f) * Mth.clamp(0.6f + 0.25f * user.getBbHeight(), 0.7f, 1.6f);
		boolean selfOnly = !spec.layers.isEmpty();
		for (MoveSpec.Layer layer : spec.layers) {
			selfOnly &= "self".equals(layer.phase);
		}
		this.self = selfOnly;
		this.status = "status".equals(spec.category);
	}

	@Override
	protected void cue(String part, Entity source, Entity targeted) {
		Entity found = targeted != null && targeted != user ? targeted : source != null && source != user ? source : null;
		if (found != null) {
			target = found;
			hit = true;
		}
	}

	@Override
	protected boolean awaitingHit() {
		return !self && hitAge < 0;
	}

	@Override
	protected void tick() {
		age++;
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		if (!self) {
			if (target == null && targetPoint == null && age == WIND) {
				target = MoveEffects.defaultTarget(user, level);
			}
			if (targetPoint != null && age == ARRIVE) {
				hit = true; // preview: always a hit
			}
			if (hitAge < 0 && age >= ARRIVE && (hit || (status && age >= ARRIVE + 2) || age >= HIT_LATEST)) {
				hitAge = age;
				hit |= status;
			}
		}
		for (MoveSpec.Layer layer : spec.layers) {
			Motifs.Context context = context(layer, 1f, null, null, null);
			if (context != null) {
				Motifs.tick(context, level);
			}
		}
	}

	@Override
	protected boolean finished() {
		if (user.isRemoved() || age > 140) {
			return true;
		}
		return self ? age > SELF + longest("self") : hitAge >= 0 && age - hitAge > AFTER + longest("hit");
	}

	/** How far past the phase's usual length its layers run (delays, longer durations). */
	private int longest(String phase) {
		int extra = 0;
		int usual = phase.equals("self") ? SELF : AFTER;
		for (MoveSpec.Layer layer : spec.layers) {
			if (phase.equals(layer.phase)) {
				extra = Math.max(extra, layer.delay + (layer.duration > 0 ? layer.duration : usual) - usual);
			}
		}
		return extra;
	}

	@Override
	protected void render(PoseStack pose, MultiBufferSource buffers, Vec3 camera, float partialTick) {
		for (MoveSpec.Layer layer : spec.layers) {
			Motifs.Context context = context(layer, partialTick, pose, buffers, camera);
			if (context != null) {
				Motifs.render(context);
			}
		}
	}

	/** The layer's context this frame, or null when its phase isn't playing. */
	private Motifs.Context context(MoveSpec.Layer layer, float partialTick, PoseStack pose, MultiBufferSource buffers, Vec3 camera) {
		float t = age - 1 + partialTick;
		float start;
		int length;
		switch (layer.phase == null ? "hit" : layer.phase) {
			case "wind" -> {
				start = 0;
				length = WIND;
			}
			case "travel" -> {
				start = WIND;
				length = ARRIVE - WIND;
			}
			case "self" -> {
				start = 0;
				length = SELF;
			}
			default -> {
				if (hitAge < 0 || !hit) {
					return null;
				}
				start = hitAge;
				length = AFTER;
			}
		}
		if (layer.phase != null && layer.phase.equals("travel") && hitAge >= 0 && age > hitAge + 2) {
			return null;
		}
		start += layer.delay;
		if (layer.duration > 0) {
			length = layer.duration;
		}
		float local = t - start;
		if (local < 0 || local > length) {
			return null;
		}
		return new Motifs.Context(this, layer, pose, buffers, camera, local, local / length, length);
	}

	// ---------------------------------------------------------------- where

	Vec3 mouth() {
		Vec3 base = user.position().add(0, user.getBbHeight() * 0.62, 0);
		Vec3 toward = aim().subtract(base).multiply(1, 0, 1);
		toward = toward.lengthSqr() < 1.0E-4 ? user.getViewVector(1f).multiply(1, 0, 1) : toward;
		toward = toward.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : toward.normalize();
		return base.add(toward.scale(user.getBbWidth() * 0.55 + 0.25 * size));
	}

	Vec3 aim() {
		if (target != null && !target.isRemoved()) {
			return bodyCentre(target);
		}
		if (targetPoint != null) {
			return targetPoint;
		}
		return user.position().add(user.getViewVector(1f).multiply(6, 0, 6)).add(0, user.getBbHeight() * 0.5, 0);
	}

	Vec3 aimFeet() {
		return target != null && !target.isRemoved() ? target.position() : aim().subtract(0, 0.6, 0);
	}

	float targetWidth() {
		return target != null && !target.isRemoved() ? target.getBbWidth() : 0.8f;
	}

	float targetHeight() {
		return target != null && !target.isRemoved() ? target.getBbHeight() : 1f;
	}

	Entity user() {
		return user;
	}

}
