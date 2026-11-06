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
import net.minecraft.world.phys.Vec3;

/**
 * Plan P1.2: a Poké Ball opening in battle, as in the games — a flash in the ball's colour hides the Pokémon while
 * Cobblemon grows it, with rays, a column of light, shockwaves on the ground and rising motes. Added to Cobblemon's own
 * ball and sparks, on this client only. Client thread only.
 */
public final class BallOpening {

	/** Ticks Cobblemon's ball flies before it opens ({@code POKEBALL_AIR_TIME}). */
	private static final int AIR_TICKS = 10;
	private static final int OPEN_TICKS = 22;
	private static final int WHITE = 0xFFFFFF;
	private static final int RAYS = 12;
	private static final int MOTES = 14;
	private static final Vec3 UP = new Vec3(0, 1, 0);

	private static final List<Opening> PLAYING = new ArrayList<>();

	private static final class Opening {
		final PokemonEntity entity;
		final int color;
		int age;

		Opening(PokemonEntity entity, int color) {
			this.entity = entity;
			this.color = color;
		}
	}

	private BallOpening() {
	}

	/** {@code entity}'s ball has just been thrown. */
	public static void start(PokemonEntity entity) {
		PLAYING.add(new Opening(entity, color(entity.getExposedBall().getName().getPath())));
	}

	/** The light of a ball: by its family, white for the ones without a clear colour. */
	static int color(String ball) {
		if (ball.contains("master")) {
			return 0xB060FF;
		}
		if (ball.contains("ultra")) {
			return 0xFFD040;
		}
		if (ball.contains("great")) {
			return 0x50A0FF;
		}
		if (ball.contains("safari") || ball.contains("friend") || ball.contains("nest")) {
			return 0x70E070;
		}
		if (ball.contains("love") || ball.contains("heal")) {
			return 0xFF80C0;
		}
		if (ball.contains("dive") || ball.contains("net") || ball.contains("lure")) {
			return 0x40D8E0;
		}
		if (ball.contains("dusk") || ball.contains("moon")) {
			return 0x7050C0;
		}
		if (ball.contains("poke") || ball.contains("cherish") || ball.contains("repeat") || ball.contains("fast")) {
			return 0xFF5848;
		}
		return WHITE;
	}

	public static void tick() {
		PLAYING.removeIf(opening -> ++opening.age > AIR_TICKS + OPEN_TICKS || opening.entity.isRemoved());
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
		for (Opening opening : PLAYING) {
			float t = (opening.age + partialTick - AIR_TICKS) / OPEN_TICKS;
			if (t >= 0 && t <= 1) {
				draw(out, m, opening, t, camera, partialTick);
			}
		}
		buffers.endBatch();
		pose.popPose();
	}

	private static void draw(VertexConsumer out, Matrix4f m, Opening opening, float t, Vec3 camera, float partialTick) {
		PokemonEntity entity = opening.entity;
		Vec3 feet = entity.getPosition(partialTick);
		float size = Mth.clamp(entity.getBbHeight(), 1f, 4f);
		Vec3 center = feet.add(0, size / 2, 0);
		int color = opening.color;

		// The flash: white at its heart, the ball's colour around it.
		if (t < 0.4f) {
			float k = t / 0.4f;
			int alpha = (int) (255 * (1 - k));
			Draw.halo(out, m, center, camera, 0, size * (0.9f + 0.8f * k), WHITE, alpha);
			Draw.halo(out, m, center, camera, 0, size * (1.5f + 1.6f * k), color, (int) (alpha * 0.8f));
		}
		// The rays burst out.
		if (t < 0.55f) {
			float k = t / 0.55f;
			for (int i = 0; i < RAYS; i++) {
				Vec3 dir = Draw.unit(Math.PI * (0.2 + 0.6 * ((i * 5 % RAYS) / (float) RAYS)), Math.PI * 2 * i / RAYS);
				Vec3 from = center.add(dir.scale(size * 0.25f * k));
				Vec3 to = center.add(dir.scale(size * (0.6f + 1.6f * k)));
				Draw.streak(out, m, from, to, camera, 0.09f, color, (int) (230 * (1 - k)));
			}
		}
		// The column of light.
		if (t < 0.7f) {
			float k = t / 0.7f;
			float fade = k < 0.2f ? k / 0.2f : 1 - (k - 0.2f) / 0.8f;
			Vec3 axis = camera.subtract(feet).multiply(1, 0, 1);
			axis = axis.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : axis.normalize();
			Vec3 side = axis.cross(UP).scale(size * (0.55f - 0.25f * k));
			float height = size * (1.2f + 1.8f * k);
			int alpha = (int) (170 * fade);
			Draw.vertex(out, m, feet.add(side), color, alpha);
			Draw.vertex(out, m, feet.subtract(side), color, alpha);
			Draw.vertex(out, m, feet.subtract(side).add(0, height, 0), color, 0);
			Draw.vertex(out, m, feet.add(side).add(0, height, 0), color, 0);
		}
		// Two shockwaves on the ground.
		Vec3 ground = feet.add(0, 0.06, 0);
		for (int wave = 0; wave < 2; wave++) {
			float k = Mth.clamp((t - wave * 0.16f) / 0.7f, 0, 1);
			if (k > 0 && k < 1) {
				Draw.ring(out, m, ground, UP, size * (0.3f + 1.7f * k), 0.16f, color, (int) (220 * (1 - k)));
			}
		}
		// Motes rise and fade.
		for (int i = 0; i < MOTES; i++) {
			float k = Mth.clamp((t - 0.1f - i * 0.015f) / 0.8f, 0, 1);
			if (k > 0 && k < 1) {
				double angle = i * 2.4 + k * 2.2;
				double radius = size * (0.35 + 0.35 * ((i * 7 % MOTES) / (double) MOTES));
				Vec3 at = feet.add(Math.cos(angle) * radius, size * (0.1 + 1.3 * k), Math.sin(angle) * radius);
				Draw.halo(out, m, at, camera, 0, 0.11f, i % 3 == 0 ? WHITE : color, (int) (240 * (1 - k)));
			}
		}
	}
}
