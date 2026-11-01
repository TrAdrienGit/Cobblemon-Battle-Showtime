package com.mystaria.spectacle.move;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

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
 * Moves Spectacle redraws (phase M1, Adrien 2026-11-01). Cobblemon's move animations are "action effects": timelines
 * run by the <b>server</b>, which sends the clients the Pokémon's pose, sounds and particles
 * ({@link SpawnSnowstormEntityParticlePacket}, effect ids {@code cobblemon:<move>_<part>}). The pose and the sounds
 * are kept; the particles of a redrawn move are dropped on arrival ({@code MoveParticleOverrideMixin}) and Spectacle
 * draws its own effect instead, paced by those same packets: the first one starts it, the target's ones say where it
 * goes and whether it hit. The same packets are dispatched locally by Phantasmon for its Ghost battles, so both kinds
 * of battle are covered. Client thread only.
 */
public final class MoveEffects {

	/** Move id as in Cobblemon's particle names (no separator: {@code shadowball}) → its effect, from the user. */
	private static final Map<String, BiFunction<String, Entity, MoveEffect>> OVERRIDES = Map.of(
			"shadowball", ShadowBall::new);

	private static final List<MoveEffect> PLAYING = new ArrayList<>();

	private MoveEffects() {
	}

	/** Whether this Cobblemon particle belongs to a redrawn move (then it is dropped); feeds the move's effect. */
	public static boolean intercept(SpawnSnowstormEntityParticlePacket packet) {
		ResourceLocation id = packet.getEffectId();
		if (!"cobblemon".equals(id.getNamespace())) {
			return false;
		}
		String path = id.getPath();
		int split = path.indexOf('_');
		if (split <= 0) {
			return false;
		}
		String move = path.substring(0, split);
		BiFunction<String, Entity, MoveEffect> factory = OVERRIDES.get(move);
		if (factory == null) {
			return false;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return true;
		}
		Entity source = level.getEntity(packet.getSourceEntityId());
		Entity targeted = packet.getTargetedEntityId() == null ? null : level.getEntity(packet.getTargetedEntityId());
		String part = path.substring(split + 1);
		MoveEffect effect = playing(move, source, targeted);
		if (effect == null) {
			if (source == null || !part.startsWith("actor")) {
				return true;
			}
			effect = factory.apply(move, source);
			PLAYING.add(effect);
		}
		effect.cue(part, source, targeted);
		return true;
	}

	/** {@code /spectacle preview move <move>}: from the player to a point 6 blocks ahead. */
	public static boolean preview(String move) {
		BiFunction<String, Entity, MoveEffect> factory = OVERRIDES.get(move);
		Minecraft mc = Minecraft.getInstance();
		if (factory == null || mc.player == null) {
			return false;
		}
		MoveEffect effect = factory.apply(move, mc.player);
		Vec3 ahead = mc.player.getEyePosition().add(mc.player.getViewVector(1f).multiply(6, 0, 6)).add(0, -0.6, 0);
		effect.previewTarget(ahead);
		PLAYING.add(effect);
		return true;
	}

	public static List<String> ids() {
		return OVERRIDES.keySet().stream().sorted().toList();
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

	public static void tick() {
		PLAYING.removeIf(effect -> {
			effect.tick();
			return effect.finished();
		});
	}

	public static void reset() {
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
