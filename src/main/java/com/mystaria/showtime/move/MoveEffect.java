package com.mystaria.showtime.move;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** One move being played by Showtime in place of Cobblemon's particles (see {@link MoveEffects}). */
public abstract class MoveEffect {

	final String move;
	protected final Entity user;
	protected Entity target;
	/** Where it goes when no target entity is known (a target that can't be found). */
	protected Vec3 targetPoint;
	protected int age;

	protected MoveEffect(String move, Entity user) {
		this.move = move;
		this.user = user;
	}

	/**
	 * One of Cobblemon's particles of this move arrived (dropped, not drawn): {@code part} is the name after the move
	 * ({@code actorcharge}, {@code targetimpact}…), {@code source} the entity it was spawned on, {@code targeted} the
	 * one it aims at (or null).
	 */
	protected abstract void cue(String part, Entity source, Entity targeted);

	protected abstract void tick();

	protected abstract boolean finished();

	protected abstract void render(PoseStack pose, MultiBufferSource buffers, Vec3 camera, float partialTick);

	/** Still waiting for its hit: Cobblemon's generic hit particle on another Pokémon is this move's. */
	protected boolean awaitingHit() {
		return true;
	}

	boolean involves(Entity source, Entity targeted) {
		return source == user || targeted == user || (target != null && (source == target || targeted == target))
				|| (target == null && source != null);
	}

	/** A point in the middle of the entity's body. */
	protected static Vec3 bodyCentre(Entity entity) {
		return entity.position().add(0, entity.getBbHeight() * 0.5, 0);
	}
}
