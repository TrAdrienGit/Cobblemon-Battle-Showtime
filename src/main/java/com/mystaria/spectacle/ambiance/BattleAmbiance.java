package com.mystaria.spectacle.ambiance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.mystaria.spectacle.api.SpectacleHooks;
import com.mystaria.spectacle.config.SpectacleConfig;

/**
 * Plays an {@link Ambiance} around a battle's field (plan-spectacle.md, phases A1-A2, Adrien 2026-10-30), on this
 * client only, from the battle's start to its end — then the world is exactly as before: everything is drawn or read
 * through here, nothing of the world is changed. The field is the middle of the player trainers, as wide as they are
 * apart. Which ambiance: one another mod imposes ({@link SpectacleHooks#ambianceOverride}: Phantasmon's lobby, a
 * scripted event), else the player's choice in Spectacle's menu. Client thread only.
 */
public final class BattleAmbiance {

	public static final String NONE = "none";
	private static final int PREVIEW_TICKS = 600;
	private static final int FLASH_TICKS = 14;

	private record Floating(float angle, float radius, float height, float phase) {
	}

	private static Ambiance active;
	private static List<UUID> trainers = List.of();
	private static Vec3 center;
	private static float fieldRadius = 10f;
	private static long ticks;
	private static long startDayTime;
	private static int previewLeft;
	private static int nextStrike;
	private static int flashColor;
	private static int flashLeft;
	private static int nextBoltId = -2_000_000;
	private static final Random RANDOM = new Random();
	private static final List<Floating> FLOATING = new ArrayList<>();
	private static BlockState floatingState;
	private static LoopSound sound;
	/** Where the rainbow stands, fixed at the start (behind the field, seen from the player). */
	private static Vec3 rainbowBack = new Vec3(0, 0, -1);
	private static final List<Float> RIFT_ANGLES = new ArrayList<>();

	private static final class Streak {
		Vec3 from;
		Vec3 direction;
		int age;
		final int life;

		Streak(Vec3 from, Vec3 direction, int life) {
			this.from = from;
			this.direction = direction;
			this.life = life;
		}
	}

	private static final List<Streak> STREAKS = new ArrayList<>();
	/** Spectacle put a screen filter on (and takes it off at the end). */
	private static boolean filterOn;
	private static int momentFilterLeft;

	private BattleAmbiance() {
	}

	// ---------------------------------------------------------------- life

	/** A battle with these player trainers starts: the ambiance chosen (or imposed) plays around them. */
	public static void start(Collection<UUID> battleTrainers) {
		String id = SpectacleHooks.ambianceOverride();
		if (id == null) {
			id = SpectacleConfig.ambiance();
		}
		begin(Ambiances.get(id), battleTrainers, 0);
	}

	/** {@code /spectacle preview ambiance <id>}: around the player for 30 s ({@code none} stops it). */
	public static void preview(String id) {
		Minecraft mc = Minecraft.getInstance();
		begin(Ambiances.get(id), mc.player == null ? List.of() : List.of(mc.player.getUUID()), PREVIEW_TICKS);
	}

	public static void end() {
		stopSound();
		if (filterOn) {
			Minecraft.getInstance().gameRenderer.shutdownEffect();
			filterOn = false;
		}
		momentFilterLeft = 0;
		active = null;
		trainers = List.of();
		center = null;
		previewLeft = 0;
		flashLeft = 0;
		FLOATING.clear();
	}

	public static boolean active() {
		return active != null && center != null;
	}

	private static void begin(Ambiance ambiance, Collection<UUID> battleTrainers, int preview) {
		end();
		Minecraft mc = Minecraft.getInstance();
		if (ambiance == null || mc.level == null) {
			return;
		}
		active = ambiance;
		trainers = List.copyOf(battleTrainers);
		previewLeft = preview;
		ticks = 0;
		startDayTime = ambiance.time != null && ambiance.time.at != null ? ambiance.time.at : mc.level.getDayTime();
		nextStrike = ambiance.lightning != null ? strikeDelay(ambiance.lightning) : -1;
		updateField(mc.level);
		if (mc.player != null && center != null) {
			Vec3 away = new Vec3(center.x - mc.player.getX(), 0, center.z - mc.player.getZ());
			rainbowBack = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, -1) : away.normalize();
		}
		RIFT_ANGLES.clear();
		STREAKS.clear();
		if (ambiance.rifts != null) {
			for (int i = 0; i < ambiance.rifts.count; i++) {
				RIFT_ANGLES.add((float) (Math.PI * 2 * (i + RANDOM.nextFloat() * 0.6) / ambiance.rifts.count));
			}
		}
		floatingState = null;
		if (ambiance.floatingBlocks != null) {
			floatingState = block(ambiance.floatingBlocks.block);
			Ambiance.FloatingBlocks f = ambiance.floatingBlocks;
			for (int i = 0; i < f.count; i++) {
				FLOATING.add(new Floating((float) (RANDOM.nextFloat() * Math.PI * 2), between(f.radius), between(f.height),
						RANDOM.nextFloat() * 6.28f));
			}
		}
		setFilter(ambiance.filter);
		if (ambiance.sound != null && ambiance.sound.id != null) {
			sound = new LoopSound(ResourceLocation.parse(ambiance.sound.id), ambiance.sound.volume, ambiance.sound.pitch);
			mc.getSoundManager().play(sound);
		}
	}

	/** Every client tick: the field follows the trainers, particles, floating blocks' flames, lightning. */
	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (active == null || mc.level == null || mc.isPaused()) {
			if (active != null && mc.level == null) {
				end();
			}
			return;
		}
		ticks++;
		if (previewLeft > 0 && --previewLeft == 0) {
			end();
			return;
		}
		if (flashLeft > 0) {
			flashLeft--;
		}
		if (momentFilterLeft > 0 && --momentFilterLeft == 0) {
			setFilter(active.filter); // the moment's filter is over: back to the ambiance's own
		}
		updateField(mc.level);
		if (center == null) {
			return;
		}
		for (Ambiance.Particles particles : active.particles) {
			spawn(mc.level, particles);
		}
		if (active.floatingBlocks != null && active.floatingBlocks.flame && ticks % 4 == 0) {
			for (Floating floating : FLOATING) {
				Vec3 at = floatingPosition(floating, 0f);
				if (RANDOM.nextInt(3) == 0) {
					mc.level.addParticle(ParticleTypes.SMALL_FLAME, at.x, at.y + 0.55, at.z, 0, 0.005, 0);
				}
			}
		}
		if (active.rifts != null) {
			ParticleOptions riftParticle = particle(active.rifts.particle);
			for (int i = 0; i < RIFT_ANGLES.size(); i++) {
				float open = riftOpening(i, 0f);
				if (riftParticle != null && open > 0.3f && RANDOM.nextFloat() < open * 0.8f) {
					Vec3 at = riftBase(i).add(center);
					mc.level.addParticle(riftParticle, at.x, at.y + RANDOM.nextDouble() * active.rifts.height, at.z,
							0, 0, 0);
				}
			}
		}
		if (active.shootingStars != null && RANDOM.nextFloat() < active.shootingStars.rate) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			boolean below = active.shootingStars.below && RANDOM.nextBoolean();
			Vec3 from = new Vec3(Math.cos(angle) * fieldRadius * 3, below ? -15 - RANDOM.nextDouble() * 25 : 45 + RANDOM.nextDouble() * 25,
					Math.sin(angle) * fieldRadius * 3);
			Vec3 toward = new Vec3(-Math.cos(angle) + (RANDOM.nextDouble() - 0.5), below ? 0.1 : -0.35, -Math.sin(angle) + (RANDOM.nextDouble() - 0.5));
			STREAKS.add(new Streak(from, toward.normalize().scale(2.2), 18 + RANDOM.nextInt(12)));
		}
		STREAKS.removeIf(streak -> ++streak.age > streak.life);
		ParticleOptions trail = active.shootingStars != null ? particle(active.shootingStars.trail) : null;
		if (trail != null) {
			for (Streak streak : STREAKS) {
				Vec3 head = center.add(streak.from).add(streak.direction.scale(streak.age));
				mc.level.addParticle(trail, head.x, head.y, head.z, 0, 0, 0);
			}
		}
		if (nextStrike > 0 && --nextStrike == 0) {
			strike(mc.level);
			nextStrike = strikeDelay(active.lightning);
		}
	}

	/** A moment of the battle: {@code send_out}, {@code attack}, {@code crit}, {@code faint} or {@code victory}. */
	public static void moment(String kind) {
		Minecraft mc = Minecraft.getInstance();
		if (!active() || active.moments == null || mc.level == null) {
			return;
		}
		Ambiance.Moment moment = switch (kind) {
			case "send_out" -> active.moments.sendOut;
			case "attack" -> active.moments.attack;
			case "faint" -> active.moments.faint;
			case "crit" -> active.moments.crit;
			case "victory" -> active.moments.victory;
			default -> null;
		};
		if (moment == null) {
			return;
		}
		ParticleOptions particle = particle(moment.particle);
		if (particle != null) {
			for (int i = 0; i < moment.count; i++) {
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				double r = RANDOM.nextDouble() * fieldRadius * 0.6;
				mc.level.addParticle(particle, center.x + Math.cos(angle) * r, center.y + 0.5 + RANDOM.nextDouble() * 3,
						center.z + Math.sin(angle) * r, (RANDOM.nextDouble() - 0.5) * 0.3, RANDOM.nextDouble() * 0.3,
						(RANDOM.nextDouble() - 0.5) * 0.3);
			}
		}
		if (moment.flash != null) {
			flashColor = Ambiance.color(moment.flash, 0xFFFFFF);
			flashLeft = FLASH_TICKS;
		}
		if (moment.lightning) {
			strike(mc.level);
		}
		if (moment.sound != null) {
			mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
					SoundEvent.createVariableRangeEvent(ResourceLocation.parse(moment.sound)), moment.pitch, moment.volume));
		}
		if (moment.filter != null) {
			setFilter(moment.filter);
			momentFilterLeft = Math.max(1, moment.filterTicks);
		}
	}

	/** Puts one of Minecraft's post effects on the screen (null: none). */
	private static void setFilter(String id) {
		Minecraft mc = Minecraft.getInstance();
		if (id == null) {
			if (filterOn) {
				mc.gameRenderer.shutdownEffect();
				filterOn = false;
			}
			return;
		}
		try {
			((com.mystaria.spectacle.mixin.GameRendererAccessor) mc.gameRenderer).spectacle$loadEffect(ResourceLocation.parse(id));
			filterOn = true;
		} catch (RuntimeException ex) {
			filterOn = false;
		}
	}

	private static void updateField(ClientLevel level) {
		List<Vec3> spots = new ArrayList<>();
		for (UUID uuid : trainers) {
			Player player = SpectacleHooks.player(level, uuid);
			if (player != null) {
				spots.add(player.position());
			}
		}
		if (spots.isEmpty()) {
			Player self = Minecraft.getInstance().player;
			if (center == null && self != null) {
				center = self.position();
			}
			return;
		}
		Vec3 sum = Vec3.ZERO;
		for (Vec3 spot : spots) {
			sum = sum.add(spot);
		}
		center = sum.scale(1.0 / spots.size());
		float apart = 0f;
		for (Vec3 spot : spots) {
			apart = Math.max(apart, (float) spot.distanceTo(center));
		}
		fieldRadius = Math.max(8f, apart + 5f);
	}

	// ---------------------------------------------------------------- what the world reads (mixins)

	/** The day time this client shows (sun, moon, stars, light), or {@code world} outside an ambiance with a time. */
	public static long dayTime(long world) {
		if (!active() || active.time == null) {
			return world;
		}
		return startDayTime + (long) (ticks * active.time.speed);
	}

	public static Vec3 skyColor(Vec3 world) {
		return blend(world, active() && active.sky != null ? active.sky.skyColor : null);
	}

	public static Vec3 cloudColor(Vec3 world) {
		return blend(world, active() && active.sky != null ? active.sky.cloudColor : null);
	}

	/** The fog colour over the world's ({@code rgb} 0..1, changed in place). */
	public static void fogColor(float[] rgb) {
		if (!active() || active.sky == null || active.sky.fogColor == null) {
			return;
		}
		Vec3 blended = blend(new Vec3(rgb[0], rgb[1], rgb[2]), active.sky.fogColor);
		rgb[0] = (float) blended.x;
		rgb[1] = (float) blended.y;
		rgb[2] = (float) blended.z;
	}

	/** Fog start / end in blocks, or NaN for the world's. */
	public static float fogStart() {
		return active() && active.sky != null && active.sky.fogStart != null ? active.sky.fogStart : Float.NaN;
	}

	public static float fogEnd() {
		return active() && active.sky != null && active.sky.fogEnd != null ? active.sky.fogEnd : Float.NaN;
	}

	/** {@code LevelRendererHideWorldMixin}: no block of the world drawn. */
	public static boolean hidesWorld() {
		return active() && active.hideWorld;
	}

	/** Rain shown (0..1), or {@code world}'s. */
	public static float rainLevel(float world) {
		return active() && active.sky != null && active.sky.rain != null ? active.sky.rain : world;
	}

	public static float thunderLevel(float world) {
		return active() && active.sky != null && active.sky.thunder != null ? active.sky.thunder : world;
	}

	public static float starBrightness(float world) {
		return active() && active.sky != null && active.sky.stars != null ? active.sky.stars : world;
	}

	/** The clouds' height: NaN hides them. */
	public static float cloudHeight(float world) {
		if (!active() || active.sky == null) {
			return world;
		}
		if (active.sky.hideClouds) {
			return Float.NaN;
		}
		return active.sky.cloudHeight != null ? active.sky.cloudHeight : world;
	}

	private static Vec3 blend(Vec3 world, String hex) {
		if (hex == null) {
			return world;
		}
		int rgb = Ambiance.color(hex, 0);
		Vec3 target = new Vec3(((rgb >> 16) & 255) / 255.0, ((rgb >> 8) & 255) / 255.0, (rgb & 255) / 255.0);
		return world.lerp(target, Mth.clamp(active.sky.strength, 0f, 1f));
	}

	// ---------------------------------------------------------------- drawing

	/** World pass (after the entities): backdrop, stars, rainbow, searchlights, floating blocks. */
	public static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (!active() || mc.level == null || context.matrixStack() == null) {
			return;
		}
		float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
		float time = ticks + partialTick;
		Vec3 camera = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		pose.pushPose();
		pose.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		if (active.backdrop != null) {
			drawBackdrop(pose, buffers, active.backdrop, time);
		}
		if (active.rainbow != null) {
			drawRainbow(pose, buffers, active.rainbow, camera);
		}
		if (active.spotlights != null) {
			drawSpotlights(pose, buffers, active.spotlights, time);
		}
		if (active.floorLights != null) {
			drawFloorLights(pose, buffers, active.floorLights, time);
		}
		if (active.rifts != null) {
			drawRifts(pose, buffers, active.rifts, partialTick);
		}
		if (active.aurora != null) {
			drawAurora(pose, buffers, active.aurora, time);
		}
		if (!STREAKS.isEmpty()) {
			drawStreaks(pose, buffers, partialTick);
		}
		if (!active.planets.isEmpty()) {
			drawPlanets(pose, buffers, time);
		}
		buffers.endBatch();
		if (floatingState != null) {
			for (Floating floating : FLOATING) {
				Vec3 at = floatingPosition(floating, partialTick).subtract(center);
				pose.pushPose();
				pose.translate(at.x - 0.5, at.y, at.z - 0.5);
				mc.getBlockRenderer().renderSingleBlock(floatingState, pose, buffers, 0xF000F0, OverlayTexture.NO_OVERLAY);
				pose.popPose();
			}
			buffers.endBatch();
		}
		pose.popPose();
	}

	/** HUD pass: the flash of a strong moment. */
	public static void renderHud(GuiGraphics graphics) {
		if (active() && active.strobe != null && !active.strobe.colors.isEmpty() && active.strobe.every > 0) {
			long beat = ticks / active.strobe.every;
			float within = (ticks % active.strobe.every) / (float) active.strobe.every;
			int color = Ambiance.color(active.strobe.colors.get((int) (beat % active.strobe.colors.size())), 0xFFFFFF);
			int alpha = Math.round(255 * active.strobe.alpha * (1f - within) * (1f - within));
			if (alpha > 0) {
				graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (alpha << 24) | color);
			}
		}
		if (flashLeft <= 0) {
			return;
		}
		int alpha = Math.round(150f * flashLeft / FLASH_TICKS);
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (alpha << 24) | flashColor);
	}

	private static void drawBackdrop(PoseStack pose, MultiBufferSource buffers, Ambiance.Backdrop backdrop, float time) {
		float r = Math.max(backdrop.radius, fieldRadius + 6f);
		float bottom = -backdrop.depth;
		float top = r;
		int tint = Ambiance.color(backdrop.tint, 0xFFFFFF);
		ResourceLocation texture = ResourceLocation.parse(backdrop.texture != null ? backdrop.texture
				: "minecraft:textures/block/white_concrete.png");
		Matrix4f m = pose.last().pose();
		float tile = r / 6f;
		VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
		if (!backdrop.box) {
			drawStars(buffers, m, backdrop, r, time);
			return;
		}
		// The inside of a box: four walls, a floor below the field, a ceiling.
		face(out, m, tint, tile, -r, bottom, -r, r, bottom, -r, r, top, -r, -r, top, -r);
		face(out, m, tint, tile, r, bottom, r, -r, bottom, r, -r, top, r, r, top, r);
		face(out, m, tint, tile, -r, bottom, r, -r, bottom, -r, -r, top, -r, -r, top, r);
		face(out, m, tint, tile, r, bottom, -r, r, bottom, r, r, top, r, r, top, -r);
		face(out, m, tint, tile, -r, bottom, -r, -r, bottom, r, r, bottom, r, r, bottom, -r);
		face(out, m, tint, tile, -r, top, r, -r, top, -r, r, top, -r, r, top, r);
		if (backdrop.floor) {
			// Just over the ground: the trainers stand on the void.
			face(out, m, tint, tile, -r, 0.02f, -r, -r, 0.02f, r, r, 0.02f, r, r, 0.02f, -r);
		}
		drawStars(buffers, m, backdrop, r, time);
	}

	private static void drawStars(MultiBufferSource buffers, Matrix4f m, Ambiance.Backdrop backdrop, float r, float time) {
		if (backdrop.stars > 0) {
			VertexConsumer glow = buffers.getBuffer(com.mystaria.spectacle.SpectacleRenderTypes.GLOW);
			int color = Ambiance.color(backdrop.starColor, 0xFFFFFF);
			RandomSource seeded = RandomSource.create(1234L);
			float spin = time / 1200f * backdrop.spin * (float) Math.PI * 2;
			for (int i = 0; i < backdrop.stars; i++) {
				double yaw = seeded.nextDouble() * Math.PI * 2 + spin;
				double pitch = backdrop.starsAround ? Math.asin(seeded.nextDouble() * 2 - 1) : Math.asin(seeded.nextDouble() * 1.8 - 0.8);
				float size = 0.12f + seeded.nextFloat() * 0.25f;
				float twinkle = 0.6f + 0.4f * Mth.sin(time * 0.1f + i);
				Vec3 dir = new Vec3(Math.cos(pitch) * Math.cos(yaw), Math.sin(pitch), Math.cos(pitch) * Math.sin(yaw));
				quadFacing(glow, m, dir.scale(r * 0.94), dir, size * r / 30f, color, (int) (220 * twinkle));
			}
		}
	}

	private static void drawRainbow(PoseStack pose, MultiBufferSource buffers, Ambiance.Rainbow rainbow, Vec3 camera) {
		int[] bands = { 0xFF3B30, 0xFF9500, 0xFFCC00, 0x34C759, 0x32ADE6, 0x5856D6, 0xAF52DE };
		VertexConsumer out = buffers.getBuffer(com.mystaria.spectacle.SpectacleRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		// Behind the field as the player saw it at the start, and fixed there.
		Vec3 back = rainbowBack;
		Vec3 across = new Vec3(-back.z, 0, back.x);
		Vec3 origin = back.scale(rainbow.radius * 0.6);
		float width = rainbow.radius * 0.035f;
		int alpha = (int) (255 * Mth.clamp(rainbow.alpha, 0f, 1f));
		int segments = 48;
		for (int b = 0; b < bands.length; b++) {
			float inner = rainbow.radius - (b + 1) * width;
			float outer = inner + width;
			for (int s = 0; s < segments; s++) {
				double a0 = Math.PI * s / segments;
				double a1 = Math.PI * (s + 1) / segments;
				vertex(out, m, origin.add(across.scale(Math.cos(a0) * inner)).add(0, Math.sin(a0) * inner, 0), bands[b], alpha);
				vertex(out, m, origin.add(across.scale(Math.cos(a0) * outer)).add(0, Math.sin(a0) * outer, 0), bands[b], alpha);
				vertex(out, m, origin.add(across.scale(Math.cos(a1) * outer)).add(0, Math.sin(a1) * outer, 0), bands[b], alpha);
				vertex(out, m, origin.add(across.scale(Math.cos(a1) * inner)).add(0, Math.sin(a1) * inner, 0), bands[b], alpha);
			}
		}
	}

	private static void drawSpotlights(PoseStack pose, MultiBufferSource buffers, Ambiance.Spotlights spotlights, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.spectacle.SpectacleRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		float ring = fieldRadius + 4f;
		for (int i = 0; i < spotlights.count; i++) {
			double baseAngle = Math.PI * 2 * i / spotlights.count;
			Vec3 base = new Vec3(Math.cos(baseAngle) * ring, 0, Math.sin(baseAngle) * ring);
			double sweep = time / 1200.0 * spotlights.sweep * Math.PI * 2 + i * 1.7;
			// Leaning in over the field and swaying.
			Vec3 lean = new Vec3(-Math.cos(baseAngle), 0, -Math.sin(baseAngle)).scale(0.45 + 0.25 * Math.sin(sweep))
					.add(new Vec3(-Math.sin(baseAngle), 0, Math.cos(baseAngle)).scale(0.5 * Math.cos(sweep * 0.7)));
			Vec3 top = base.add(lean.scale(spotlights.height)).add(0, spotlights.height, 0);
			int color = Ambiance.color(spotlights.colors.get(i % spotlights.colors.size()), 0xFFFFFF);
			if (spotlights.fromSky) {
				// A sunbeam: wide up in the sky, narrowing down on the field.
				Vec3 ground = new Vec3(Math.cos(baseAngle) * fieldRadius * 0.5, 0, Math.sin(baseAngle) * fieldRadius * 0.5);
				beam(out, m, ground, ground.add(lean.scale(-spotlights.height * 0.4)).add(0, spotlights.height, 0),
						spotlights.width * 1.2f, spotlights.width * 3f, color);
			} else {
				beam(out, m, base, top, spotlights.width * 0.25f, spotlights.width * 2.5f, color);
			}
		}
	}

	private static void drawFloorLights(PoseStack pose, MultiBufferSource buffers, Ambiance.FloorLights lights, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.spectacle.SpectacleRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		for (int i = 0; i < lights.count; i++) {
			// Each spot runs its own loop over the floor: different radii, speeds and directions.
			double turn = time / 1200.0 * lights.speed * Math.PI * 2 * (i % 2 == 0 ? 1 : -0.7);
			double radius = fieldRadius * (0.25 + 0.6 * ((i * 0.37) % 1.0));
			double angle = Math.PI * 2 * i / lights.count + turn;
			// Well over the ground: at the blocks' own height they flickered with them (Adrien 2026-10-30).
			Vec3 at = new Vec3(Math.cos(angle) * radius, 0.12, Math.sin(angle) * radius);
			int color = Ambiance.color(lights.colors.get(i % lights.colors.size()), 0xFFFFFF);
			int segments = 20;
			for (int s = 0; s < segments; s++) {
				double a0 = Math.PI * 2 * s / segments;
				double a1 = Math.PI * 2 * (s + 1) / segments;
				vertex(out, m, at, color, lights.alpha);
				vertex(out, m, at, color, lights.alpha);
				vertex(out, m, at.add(Math.cos(a1) * lights.size, 0, Math.sin(a1) * lights.size), color, 0);
				vertex(out, m, at.add(Math.cos(a0) * lights.size, 0, Math.sin(a0) * lights.size), color, 0);
			}
		}
	}

	private static Vec3 riftBase(int i) {
		float angle = RIFT_ANGLES.get(i);
		float ring = fieldRadius + 3f;
		return new Vec3(Math.cos(angle) * ring, 0.5, Math.sin(angle) * ring);
	}

	/** 0 closed .. 1 wide open: each rift opens, holds, closes, rests, out of step with the others. */
	private static float riftOpening(int i, float partialTick) {
		int cycle = Math.max(40, active.rifts.cycle);
		float phase = ((ticks + partialTick) / cycle + i * 0.37f) % 1f;
		float open = phase < 0.15f ? phase / 0.15f : phase < 0.55f ? 1f : phase < 0.7f ? 1f - (phase - 0.55f) / 0.15f : 0f;
		return open * open * (3 - 2 * open);
	}

	private static void drawRifts(PoseStack pose, MultiBufferSource buffers, Ambiance.Rifts rifts, float partialTick) {
		int color = Ambiance.color(rifts.color, 0xB400FF);
		VertexConsumer glow = buffers.getBuffer(com.mystaria.spectacle.SpectacleRenderTypes.GLOW);
		VertexConsumer core = buffers.getBuffer(RenderType.entityCutoutNoCull(
				ResourceLocation.withDefaultNamespace("textures/block/black_concrete.png")));
		Matrix4f m = pose.last().pose();
		for (int i = 0; i < RIFT_ANGLES.size(); i++) {
			float open = riftOpening(i, partialTick);
			if (open <= 0.01f) {
				continue;
			}
			Vec3 base = riftBase(i);
			// Facing the field's centre.
			Vec3 across = new Vec3(-base.z, 0, base.x).normalize();
			RandomSource jag = RandomSource.create(i * 7919L);
			int steps = 10;
			Vec3[] left = new Vec3[steps + 1];
			Vec3[] right = new Vec3[steps + 1];
			for (int s = 0; s <= steps; s++) {
				float along = s / (float) steps;
				float wide = (float) Math.sin(Math.PI * along) * rifts.width * open * (0.6f + jag.nextFloat() * 0.6f);
				float wobble = (jag.nextFloat() - 0.5f) * 0.6f;
				Vec3 mid = base.add(across.scale(wobble)).add(0, along * rifts.height * (0.6 + 0.4 * open), 0);
				left[s] = mid.add(across.scale(-wide / 2));
				right[s] = mid.add(across.scale(wide / 2));
			}
			for (int s = 0; s < steps; s++) {
				coreQuad(core, m, left[s], right[s], right[s + 1], left[s + 1]);
				// The burning rim, out on both sides.
				Vec3 outL = across.scale(-0.35 * open);
				Vec3 outR = across.scale(0.35 * open);
				vertex(glow, m, left[s], color, 220);
				vertex(glow, m, left[s].add(outL), color, 0);
				vertex(glow, m, left[s + 1].add(outL), color, 0);
				vertex(glow, m, left[s + 1], color, 220);
				vertex(glow, m, right[s], color, 220);
				vertex(glow, m, right[s].add(outR), color, 0);
				vertex(glow, m, right[s + 1].add(outR), color, 0);
				vertex(glow, m, right[s + 1], color, 220);
			}
		}
	}

	private static void coreQuad(VertexConsumer out, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
		for (Vec3 v : new Vec3[] { a, b, c, d }) {
			out.addVertex(m, (float) v.x, (float) v.y, (float) v.z).setColor(20, 0, 30, 255).setUv(0, 0)
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 1, 0);
		}
	}

	/**
	 * An aurora: hundreds of thin vertical rays along wavy bands high behind the field, each soft at its sides, fading
	 * in from below and out above, flickering on its own — light, not cloth (Adrien 2026-10-30).
	 */
	private static void drawAurora(PoseStack pose, MultiBufferSource buffers, Ambiance.Aurora aurora, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.spectacle.SpectacleRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		int bands = 3;
		int rays = 140;
		float span = fieldRadius * 6f;
		RandomSource seeded = RandomSource.create(4242L);
		for (int b = 0; b < bands; b++) {
			int color = Ambiance.color(aurora.colors.get(b % aurora.colors.size()), 0x3DFF9A);
			float depth = fieldRadius * (2.5f + b * 0.9f);
			float base = aurora.height + b * 5f;
			for (int i = 0; i < rays; i++) {
				float x = -span / 2 + span * (i + seeded.nextFloat()) / rays;
				float z = depth + 7f * Mth.sin(x * 0.06f + time * 0.012f + b * 1.7f) + 3f * Mth.sin(x * 0.17f - time * 0.02f);
				float height = 9f + 6f * Mth.sin(x * 0.11f - time * 0.017f + b) + seeded.nextFloat() * 4f;
				float flicker = 0.35f + 0.65f * Math.max(0f, Mth.sin(time * (0.05f + seeded.nextFloat() * 0.08f) + i));
				float wave = 0.5f + 0.5f * Mth.sin(x * 0.05f + time * 0.01f + b * 2.3f);
				int alpha = (int) (aurora.alpha * flicker * wave);
				if (alpha < 4) {
					continue;
				}
				float width = 0.5f + seeded.nextFloat() * 0.6f;
				Vec3 foot = rotate(new Vec3(x, base, z));
				Vec3 mid = foot.add(0, height * 0.25, 0);
				Vec3 top = foot.add(0, height, 0);
				Vec3 sideways = rotate(new Vec3(width, 0, 0));
				// Each ray: fading in from its foot, brightest a quarter up, fading out to its top; soft both sides.
				for (int half = -1; half <= 1; half += 2) {
					Vec3 s = sideways.scale(half);
					vertex(out, m, foot, color, 0);
					vertex(out, m, foot.add(s), color, 0);
					vertex(out, m, mid.add(s), color, 0);
					vertex(out, m, mid, color, alpha);
					vertex(out, m, mid, color, alpha);
					vertex(out, m, mid.add(s), color, 0);
					vertex(out, m, top.add(s), color, 0);
					vertex(out, m, top, color, 0);
				}
			}
		}
	}

	/** From the rainbow's frame (z away from the player at the start) to the world's. */
	private static Vec3 rotate(Vec3 local) {
		Vec3 back = rainbowBack;
		Vec3 across = new Vec3(-back.z, 0, back.x);
		return across.scale(local.x).add(0, local.y, 0).add(back.scale(local.z));
	}

	/** A solar system: shaded spheres (lit from the first sun) turning around the field, suns glowing, rings. */
	private static void drawPlanets(PoseStack pose, MultiBufferSource buffers, float time) {
		Matrix4f m = pose.last().pose();
		VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(
				ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png")));
		VertexConsumer glow = buffers.getBuffer(com.mystaria.spectacle.SpectacleRenderTypes.GLOW);
		List<Vec3> places = new ArrayList<>();
		Vec3 sun = null;
		for (Ambiance.Planet planet : active.planets) {
			double angle = time / 1200.0 * planet.speed * Math.PI * 2 + planet.orbit * 0.37;
			Vec3 at = new Vec3(Math.cos(angle) * planet.orbit, planet.height, Math.sin(angle) * planet.orbit);
			places.add(at);
			if (planet.glow && sun == null) {
				sun = at;
			}
		}
		for (int p = 0; p < active.planets.size(); p++) {
			Ambiance.Planet planet = active.planets.get(p);
			Vec3 at = places.get(p);
			int color = Ambiance.color(planet.color, 0x9FB8FF);
			Vec3 light = sun == null || planet.glow ? new Vec3(0, 1, 0) : sun.subtract(at).normalize();
			sphere(planet.glow ? glow : solid, m, at, planet.size, color, light, planet.glow);
			if (planet.glow) {
				// Its halo: a bigger, faint glowing sphere.
				sphere(glow, m, at, planet.size * 1.6f, color, light, true);
			}
			if (planet.ring != null) {
				int ringColor = Ambiance.color(planet.ring, 0xD8C8A0);
				int segments = 48;
				for (int s = 0; s < segments; s++) {
					double a0 = Math.PI * 2 * s / segments;
					double a1 = Math.PI * 2 * (s + 1) / segments;
					float inner = planet.size * 1.4f;
					float outer = planet.size * 2.2f;
					Vec3 tilt = new Vec3(0, 0.25, 0);
					vertex(glow, m, at.add(Math.cos(a0) * inner, Math.cos(a0) * inner * tilt.y, Math.sin(a0) * inner), ringColor, 120);
					vertex(glow, m, at.add(Math.cos(a0) * outer, Math.cos(a0) * outer * tilt.y, Math.sin(a0) * outer), ringColor, 40);
					vertex(glow, m, at.add(Math.cos(a1) * outer, Math.cos(a1) * outer * tilt.y, Math.sin(a1) * outer), ringColor, 40);
					vertex(glow, m, at.add(Math.cos(a1) * inner, Math.cos(a1) * inner * tilt.y, Math.sin(a1) * inner), ringColor, 120);
				}
			}
		}
	}

	private static void sphere(VertexConsumer out, Matrix4f m, Vec3 at, float radius, int color, Vec3 light, boolean glowing) {
		int rings = 12;
		int segments = 20;
		for (int r = 0; r < rings; r++) {
			double t0 = Math.PI * r / rings;
			double t1 = Math.PI * (r + 1) / rings;
			for (int s = 0; s < segments; s++) {
				double p0 = Math.PI * 2 * s / segments;
				double p1 = Math.PI * 2 * (s + 1) / segments;
				Vec3[] corners = { unit(t0, p0), unit(t0, p1), unit(t1, p1), unit(t1, p0) };
				for (Vec3 n : corners) {
					Vec3 v = at.add(n.scale(radius));
					if (glowing) {
						vertex(out, m, v, color, 70);
					} else {
						float shade = 0.18f + 0.82f * (float) Math.max(0, n.dot(light));
						int r8 = (int) (((color >> 16) & 255) * shade);
						int g8 = (int) (((color >> 8) & 255) * shade);
						int b8 = (int) ((color & 255) * shade);
						out.addVertex(m, (float) v.x, (float) v.y, (float) v.z).setColor(r8, g8, b8, 255).setUv(0, 0)
								.setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 1, 0);
					}
				}
			}
		}
	}

	private static Vec3 unit(double theta, double phi) {
		return new Vec3(Math.sin(theta) * Math.cos(phi), Math.cos(theta), Math.sin(theta) * Math.sin(phi));
	}

	private static void drawStreaks(PoseStack pose, MultiBufferSource buffers, float partialTick) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.spectacle.SpectacleRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		int color = Ambiance.color(active.shootingStars.color, 0xE8F0FF);
		for (Streak streak : STREAKS) {
			float age = streak.age + partialTick;
			Vec3 head = streak.from.add(streak.direction.scale(age));
			Vec3 tail = head.subtract(streak.direction.scale(Math.min(age, 8f)));
			int alpha = (int) (230 * (1f - age / streak.life));
			Vec3 side = streak.direction.cross(new Vec3(0, 1, 0)).normalize().scale(0.18 * active.shootingStars.size);
			vertex(out, m, head.add(side), color, alpha);
			vertex(out, m, head.subtract(side), color, alpha);
			vertex(out, m, tail.subtract(side.scale(0.1)), color, 0);
			vertex(out, m, tail.add(side.scale(0.1)), color, 0);
		}
	}

	/**
	 * A beam of light: six planes around its axis, each bright on the axis and fading to nothing at its edges and its
	 * far end — no hard-edged rectangle seen (Adrien 2026-10-30).
	 */
	private static void beam(VertexConsumer out, Matrix4f m, Vec3 from, Vec3 to, float startWidth, float endWidth, int color) {
		Vec3 axis = to.subtract(from).normalize();
		Vec3 side = axis.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 other = axis.cross(side).normalize();
		int planes = 6;
		int core = 70;
		for (int p = 0; p < planes; p++) {
			double angle = Math.PI * p / planes;
			Vec3 across = side.scale(Math.cos(angle)).add(other.scale(Math.sin(angle)));
			for (int half = -1; half <= 1; half += 2) {
				Vec3 a = across.scale(half);
				vertex(out, m, from, color, core);
				vertex(out, m, from.add(a.scale(startWidth)), color, 0);
				vertex(out, m, to.add(a.scale(endWidth)), color, 0);
				vertex(out, m, to, color, 0);
			}
		}
	}

	private static void quadFacing(VertexConsumer out, Matrix4f m, Vec3 at, Vec3 normal, float size, int color, int alpha) {
		Vec3 u = normal.cross(new Vec3(0, 1, 0));
		u = u.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : u.normalize();
		Vec3 v = normal.cross(u).normalize();
		vertex(out, m, at.add(u.scale(-size)).add(v.scale(-size)), color, alpha);
		vertex(out, m, at.add(u.scale(size)).add(v.scale(-size)), color, alpha);
		vertex(out, m, at.add(u.scale(size)).add(v.scale(size)), color, alpha);
		vertex(out, m, at.add(u.scale(-size)).add(v.scale(size)), color, alpha);
	}

	private static void vertex(VertexConsumer out, Matrix4f m, Vec3 at, int color, int alpha) {
		out.addVertex(m, (float) at.x, (float) at.y, (float) at.z)
				.setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, alpha);
	}

	private static void face(VertexConsumer out, Matrix4f m, int tint, float tile, float x0, float y0, float z0, float x1,
			float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3) {
		corner(out, m, tint, x0, y0, z0, 0, tile);
		corner(out, m, tint, x1, y1, z1, tile, tile);
		corner(out, m, tint, x2, y2, z2, tile, 0);
		corner(out, m, tint, x3, y3, z3, 0, 0);
	}

	private static void corner(VertexConsumer out, Matrix4f m, int tint, float x, float y, float z, float u, float v) {
		out.addVertex(m, x, y, z).setColor((tint >> 16) & 255, (tint >> 8) & 255, tint & 255, 255).setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 1, 0);
	}

	// ---------------------------------------------------------------- helpers

	private static void spawn(ClientLevel level, Ambiance.Particles spec) {
		ParticleOptions particle = particle(spec.type);
		if (particle == null) {
			return;
		}
		int count = (int) spec.rate + (RANDOM.nextFloat() < spec.rate - (int) spec.rate ? 1 : 0);
		float windX = active.wind != null && active.wind.size() > 0 ? active.wind.get(0) : 0f;
		float windZ = active.wind != null && active.wind.size() > 1 ? active.wind.get(1) : 0f;
		for (int i = 0; i < count; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			double r;
			double y;
			switch (spec.area) {
				case "ring" -> {
					r = fieldRadius + 2 + RANDOM.nextDouble() * 4;
					y = RANDOM.nextDouble() * 3;
				}
				case "air" -> {
					r = Math.sqrt(RANDOM.nextDouble()) * fieldRadius;
					y = 2 + RANDOM.nextDouble() * 6;
				}
				case "sky" -> {
					r = Math.sqrt(RANDOM.nextDouble()) * fieldRadius * 1.6;
					y = 12 + RANDOM.nextDouble() * 12;
				}
				default -> {
					r = Math.sqrt(RANDOM.nextDouble()) * fieldRadius;
					y = 0.1 + RANDOM.nextDouble() * 1.5;
				}
			}
			float mx = spec.motion.size() > 0 ? spec.motion.get(0) : 0f;
			float my = spec.motion.size() > 1 ? spec.motion.get(1) : 0f;
			float mz = spec.motion.size() > 2 ? spec.motion.get(2) : 0f;
			level.addParticle(particle, center.x + Math.cos(angle) * r, center.y + y, center.z + Math.sin(angle) * r,
					mx + windX + (RANDOM.nextDouble() - 0.5) * spec.spread, my + (RANDOM.nextDouble() - 0.5) * spec.spread,
					mz + windZ + (RANDOM.nextDouble() - 0.5) * spec.spread);
		}
	}

	private static ParticleOptions particle(String id) {
		if (id == null) {
			return null;
		}
		var type = BuiltInRegistries.PARTICLE_TYPE.get(ResourceLocation.tryParse(id));
		return type instanceof ParticleOptions options ? options : null;
	}

	private static Vec3 floatingPosition(Floating floating, float partialTick) {
		Ambiance.FloatingBlocks f = active.floatingBlocks;
		float time = ticks + partialTick;
		double angle = floating.angle() + time / 1200.0 * f.orbit * Math.PI * 2;
		double bob = Math.sin(time * 0.06 + floating.phase()) * f.bob;
		return center.add(Math.cos(angle) * floating.radius(), floating.height() + bob, Math.sin(angle) * floating.radius());
	}

	/** A lightning bolt of this client only (never sent anywhere), around the field. */
	private static void strike(ClientLevel level) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		if (bolt == null) {
			return;
		}
		double angle = RANDOM.nextDouble() * Math.PI * 2;
		double r = fieldRadius + 4 + RANDOM.nextDouble() * 10;
		bolt.moveTo(center.x + Math.cos(angle) * r, center.y, center.z + Math.sin(angle) * r);
		bolt.setVisualOnly(true);
		bolt.setId(nextBoltId--);
		level.addEntity(bolt);
	}

	private static int strikeDelay(Ambiance.Lightning lightning) {
		int min = lightning.every.size() > 0 ? lightning.every.get(0) : 100;
		int max = lightning.every.size() > 1 ? lightning.every.get(1) : min;
		return min + RANDOM.nextInt(Math.max(1, max - min + 1));
	}

	private static float between(List<Float> range) {
		float min = range.isEmpty() ? 0 : range.get(0);
		float max = range.size() > 1 ? range.get(1) : min;
		return min + RANDOM.nextFloat() * (max - min);
	}

	private static BlockState block(String text) {
		try {
			return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), text, false).blockState();
		} catch (CommandSyntaxException ex) {
			return Blocks.AIR.defaultBlockState();
		}
	}

	private static void stopSound() {
		if (sound != null) {
			Minecraft.getInstance().getSoundManager().stop(sound);
			sound = null;
		}
	}

	/** The ambiance's background sound, looping, not positioned. */
	private static final class LoopSound extends AbstractTickableSoundInstance {

		LoopSound(ResourceLocation id, float volume, float pitch) {
			super(SoundEvent.createVariableRangeEvent(id), SoundSource.AMBIENT, RandomSource.create());
			this.looping = true;
			this.delay = 0;
			this.volume = volume;
			this.pitch = pitch;
			this.relative = true;
			this.attenuation = Attenuation.NONE;
		}

		@Override
		public void tick() {
			if (BattleAmbiance.sound != this) {
				stop();
			}
		}
	}
}
