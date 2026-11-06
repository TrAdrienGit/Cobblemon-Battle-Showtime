package com.mystaria.showtime.move;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Plan P1.3: a Pokémon called back in battle — it dissolves into motes drawn along Cobblemon's own beam to its trainer,
 * a ring closes in around it and a spark marks the ball's return. Added to Cobblemon's beam, on this client only.
 * Client thread only.
 */
public final class BallRecall {

	private static final int TICKS = 16;
	private static final int KNOCK_OUT_TICKS = 26;
	private static final int ASH = 0xC8C8D8;
	private static final int MOTES = 22;
	private static final Vec3 UP = new Vec3(0, 1, 0);

	private static final List<Recall> PLAYING = new ArrayList<>();

	private static final class Recall {
		final PokemonEntity entity;
		final Entity thrower;
		final int color;
		final boolean knockedOut;
		/** Kept for the last ticks, when Cobblemon may already have removed the entity. */
		Vec3 feet;
		float size;
		int age;

		Recall(PokemonEntity entity, Entity thrower, int color, boolean knockedOut) {
			this.knockedOut = knockedOut;
			this.entity = entity;
			this.thrower = thrower;
			this.color = color;
			this.feet = entity.position();
			this.size = Mth.clamp(entity.getBbHeight(), 1f, 4f);
		}
	}

	private BallRecall() {
	}

	/** {@code entity} starts going back to {@code thrower} (null when unknown: the motes rise instead). */
	public static void start(PokemonEntity entity, Entity thrower, boolean knockedOut) {
		// A K.O. has lost its colour: ash instead of the ball's light.
		int color = knockedOut ? ASH : BallOpening.color(entity.getExposedBall().getName().getPath());
		PLAYING.add(new Recall(entity, thrower, color, knockedOut));
	}

	public static void tick() {
		PLAYING.removeIf(recall -> {
			if (!recall.entity.isRemoved()) {
				recall.feet = recall.entity.position();
			}
			return ++recall.age > (recall.knockedOut ? KNOCK_OUT_TICKS : TICKS);
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
		VertexConsumer out = buffers.getBuffer(Draw.GLOW);
		Matrix4f m = pose.last().pose();
		for (Recall recall : PLAYING) {
			draw(out, m, recall, Mth.clamp((recall.age + partialTick) / (recall.knockedOut ? KNOCK_OUT_TICKS : TICKS), 0, 1), camera, partialTick);
		}
		buffers.endBatch();
		pose.popPose();
	}

	private static void draw(VertexConsumer out, Matrix4f m, Recall recall, float t, Vec3 camera, float partialTick) {
		float size = recall.size;
		Vec3 center = recall.feet.add(0, size / 2, 0);
		Vec3 hand = recall.thrower != null && !recall.thrower.isRemoved()
				? recall.thrower.getEyePosition(partialTick).subtract(0, 0.5, 0)
				: center.add(0, size * 1.5, 0);
		int color = recall.color;

		if (recall.knockedOut) {
			// The blow: a white burst, heavy waves on the ground, ash slowly rising.
			if (t < 0.22f) {
				float k = t / 0.22f;
				Draw.halo(out, m, center, camera, 0, size * (1.1f + 1.3f * k), 0xFFFFFF, (int) (220 * (1 - k)));
			}
			for (int wave = 0; wave < 3; wave++) {
				float k = Mth.clamp((t - wave * 0.1f) / 0.6f, 0, 1);
				if (k > 0 && k < 1) {
					Draw.ring(out, m, recall.feet.add(0, 0.06, 0), UP, size * (0.4f + 2.4f * k), 0.22f, color, (int) (200 * (1 - k)));
				}
			}
			for (int i = 0; i < MOTES; i++) {
				float k = Mth.clamp((t - i * 0.012f) / 0.75f, 0, 1);
				if (k > 0 && k < 1) {
					double angle = i * 2.4 + k;
					double radius = size * (0.3 + 0.5 * ((i * 7 % MOTES) / (double) MOTES)) * (0.6 + k);
					Vec3 at = recall.feet.add(Math.cos(angle) * radius, size * (0.1 + 1.1 * k), Math.sin(angle) * radius);
					Draw.halo(out, m, at, camera, 0, 0.1f, color, (int) (210 * (1 - k)));
				}
			}
			return;
		}
		// A soft glow on the Pokémon while it dissolves.
		float glow = t < 0.5f ? t / 0.5f : 1 - (t - 0.5f) / 0.5f;
		Draw.halo(out, m, center, camera, 0, size * 0.9f, color, (int) (150 * glow));

		// A ring closes in at its feet.
		if (t < 0.7f) {
			float k = t / 0.7f;
			Draw.ring(out, m, recall.feet.add(0, 0.06, 0), UP, size * (1.1f - 0.9f * k), 0.13f, color, (int) (210 * Math.sin(Math.PI * k)));
		}
		// Motes leave the body, faster and faster, towards the trainer.
		for (int i = 0; i < MOTES; i++) {
			float k = Mth.clamp((t - i * 0.022f) / 0.5f, 0, 1);
			if (k <= 0 || k >= 1) {
				continue;
			}
			double angle = i * 2.4;
			double spread = size * 0.45 * (0.4 + 0.6 * ((i * 7 % MOTES) / (double) MOTES));
			Vec3 from = center.add(Math.cos(angle) * spread, size * (((i * 5 % MOTES) / (double) MOTES) - 0.5) * 0.8, Math.sin(angle) * spread);
			Vec3 at = from.lerp(hand, k * k);
			Vec3 behind = from.lerp(hand, Math.max(0, k * k - 0.12));
			Draw.streak(out, m, at, behind, camera, 0.05f, color, (int) (235 * (1 - k * 0.4f)));
			Draw.halo(out, m, at, camera, 0, 0.09f, i % 3 == 0 ? 0xFFFFFF : color, 230);
		}
		// The spark as the last of it reaches the ball.
		if (t > 0.72f) {
			float k = (t - 0.72f) / 0.28f;
			Draw.halo(out, m, hand, camera, 0, 0.25f + 0.55f * k, 0xFFFFFF, (int) (230 * (1 - k)));
			Draw.halo(out, m, hand, camera, 0, 0.5f + 0.8f * k, color, (int) (170 * (1 - k)));
		}
	}
}
