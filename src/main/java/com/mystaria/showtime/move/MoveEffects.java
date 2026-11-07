package com.mystaria.showtime.move;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.BiFunction;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket;
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket;
import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Moves Showtime redraws (M1, M2 — Adrien 2026-11-01). Cobblemon's move animations are "action effects": timelines
 * run by the <b>server</b>, which sends the clients the Pokémon's pose, sounds and particles
 * ({@link SpawnSnowstormEntityParticlePacket}). The pose and the sounds are kept; the particles of a redrawn move are
 * dropped on arrival ({@code MoveParticleOverrideMixin}) and Showtime draws its own effect instead, paced by those
 * same packets. The same packets are dispatched locally by Phantasmon for its Ghost battles, so both kinds of battle are
 * covered.
 *
 * <ul>
 * <li>Start: the move's pose ({@link PlayPosableAnimationPacket}: its first animation is the move's id, sent by every
 * move's timeline, its own or Cobblemon's generic one), or the move's first own particle.</li>
 * <li>Particles dropped: the move's own ({@code cobblemon:<move>_<part>}), and the generic hit
 * ({@code cobblemon:impact_<type>}) on another Pokémon while the move plays — they also tell the target and the hit.</li>
 * </ul>
 * Hand-made effects first ({@link ShadowBall}), then the moves of {@link MoveSpecs}. Client thread only.
 */
public final class MoveEffects {

	/** Hand-made effects, by Cobblemon's move id. */
	private static final Map<String, BiFunction<String, Entity, MoveEffect>> CUSTOM = Map.of(
			"shadowball", ShadowBall::new);

	private static final List<MoveEffect> PLAYING = new ArrayList<>();

	private MoveEffects() {
	}

	private static MoveEffect create(String move, Entity user) {
		BiFunction<String, Entity, MoveEffect> custom = CUSTOM.get(move);
		if (custom != null) {
			return custom.apply(move, user);
		}
		MoveSpec spec = MoveSpecs.get(move);
		return spec == null ? null : new LayeredMove(move, user, spec);
	}

	private static boolean redrawn(String move) {
		return CUSTOM.containsKey(move) || MoveSpecs.get(move) != null;
	}

	/** A Pokémon's pose: if it is a redrawn move's, the effect starts (the pose itself still plays). */
	public static void onAnimation(PlayPosableAnimationPacket packet) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || packet.getAnimation().isEmpty()) {
			return;
		}
		String move = packet.getAnimation().iterator().next();
		if (!redrawn(move)) {
			return;
		}
		Entity user = level.getEntity(packet.getEntityId());
		if (!(user instanceof PokemonEntity) || playingFor(move, user) != null) {
			return;
		}
		MoveEffect effect = create(move, user);
		if (effect != null) {
			PLAYING.add(effect);
		}
	}

	/** Whether this Cobblemon particle belongs to a redrawn move (then it is dropped); feeds the move's effect. */
	public static boolean intercept(SpawnSnowstormEntityParticlePacket packet) {
		ResourceLocation id = packet.getEffectId();
		if (!"cobblemon".equals(id.getNamespace())) {
			return false;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return false;
		}
		Entity source = level.getEntity(packet.getSourceEntityId());
		Entity targeted = packet.getTargetedEntityId() == null ? null : level.getEntity(packet.getTargetedEntityId());
		String path = id.getPath();
		// Cobblemon's generic hit, on the target of a move being redrawn.
		if (path.startsWith("impact_")) {
			MoveEffect effect = hitBy(source);
			if (effect == null) {
				return false;
			}
			effect.cue("impact", source, targeted);
			return true;
		}
		int split = path.indexOf('_');
		if (split <= 0) {
			return false;
		}
		String move = path.substring(0, split);
		if (!redrawn(move)) {
			return false;
		}
		String part = path.substring(split + 1);
		MoveEffect effect = playing(move, source, targeted);
		if (effect == null) {
			if (source == null || !part.startsWith("actor")) {
				return true;
			}
			effect = create(move, source);
			if (effect == null) {
				return false;
			}
			PLAYING.add(effect);
		}
		effect.cue(part, source, targeted);
		return true;
	}

	/** {@code /showtime preview move <move>}: from the player to a point 6 blocks ahead. */
	public static boolean preview(String move) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return false;
		}
		MoveEffect effect = create(move, mc.player);
		if (effect == null) {
			return false;
		}
		Vec3 ahead = mc.player.getEyePosition().add(mc.player.getViewVector(1f).multiply(6, 0, 6)).add(0, -0.6, 0);
		effect.previewTarget(ahead);
		PLAYING.add(effect);
		return true;
	}

	public static List<String> ids() {
		TreeSet<String> ids = new TreeSet<>(CUSTOM.keySet());
		ids.addAll(MoveSpecs.ids());
		return List.copyOf(ids);
	}

	/** The effect of that move already playing for this user (or aimed at this target). */
	private static MoveEffect playing(String move, Entity source, Entity targeted) {
		for (MoveEffect effect : PLAYING) {
			if (effect.move.equals(move) && !effect.finished() && effect.involves(source, targeted)) {
				return effect;
			}
		}
		return null;
	}

	private static MoveEffect playingFor(String move, Entity user) {
		for (MoveEffect effect : PLAYING) {
			if (effect.move.equals(move) && effect.user == user && !effect.finished()) {
				return effect;
			}
		}
		return null;
	}

	/** The latest move playing whose user isn't {@code target}: the one this hit belongs to. */
	private static MoveEffect hitBy(Entity target) {
		for (int i = PLAYING.size() - 1; i >= 0; i--) {
			MoveEffect effect = PLAYING.get(i);
			if (effect.user != target && !effect.finished() && effect.awaitingHit()) {
				return effect;
			}
		}
		return null;
	}

	public static void tick() {
		MoveLunge.tick();
		PLAYING.removeIf(effect -> {
			effect.tick();
			return effect.finished();
		});
	}

	public static void reset() {
		MoveLunge.reset();
		PLAYING.clear();
	}

	public static void render(WorldRenderContext context) {
		if (PLAYING.isEmpty() || context.matrixStack() == null) {
			return;
		}
		float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		pose.pushPose();
		pose.translate(-camera.x, -camera.y, -camera.z);
		MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
		for (MoveEffect effect : PLAYING) {
			effect.render(pose, buffers, camera, partialTick);
		}
		buffers.endBatch();
		pose.popPose();
	}
}
