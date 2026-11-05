package com.mystaria.showtime.move;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.mystaria.showtime.api.BattleStage;
import com.mystaria.showtime.battle.BattleCameraDirector;

/**
 * Lasting effects on the battlefield (Adrien 2026-11-02), drawn for as long as they last — whatever set them, a move or
 * an ability. Cobblemon announces each start and end to the client as a battle message, read here:
 * <ul>
 * <li>the field ({@code cobblemon.battle.fieldstart|fieldend.<id>}): the four terrains (one at a time), Trick Room,
 * Magic Room, Wonder Room, Gravity;</li>
 * <li>the weather ({@code cobblemon.battle.weather.<id>.start|end}): sun, rain, sandstorm, snow and hail;</li>
 * <li>each side ({@code cobblemon.battle.sidestart|sideend.ally|opponent.<id>}, "ally" being this client's side):
 * Stealth Rock, Spikes and Toxic Spikes (by layers), Sticky Web, Reflect, Light Screen, Aurora Veil, Tailwind,
 * Safeguard, Mist.</li>
 * </ul>
 * A side's effects stand around its active Pokémon (or its trainer); the field's in the middle of both. Client thread.
 */
public final class FieldEffects {

	private enum Side { ALLY, OPPONENT }

	private static String terrain;
	private static String weather;
	private static final java.util.Set<String> ROOMS = new java.util.HashSet<>();
	private static final Map<Side, Map<String, Integer>> SIDES = new EnumMap<>(Side.class);
	private static final RandomSource RANDOM = RandomSource.create();
	private static int ticks;
	/** Shown by {@code /showtime preview field}: around the player when no battle is on. */
	private static boolean previewing;

	private FieldEffects() {
	}

	// ---------------------------------------------------------------- state

	public static void onMessages(List<Component> messages) {
		for (Component message : messages) {
			if (message.getContents() instanceof TranslatableContents contents) {
				onKey(contents.getKey());
			}
			for (Component sibling : message.getSiblings()) {
				if (sibling.getContents() instanceof TranslatableContents contents) {
					onKey(contents.getKey());
				}
			}
		}
	}

	private static void onKey(String key) {
		if (!key.startsWith("cobblemon.battle.")) {
			return;
		}
		String[] p = key.substring("cobblemon.battle.".length()).split("\\.");
		switch (p[0]) {
			case "fieldstart", "fieldend" -> {
				if (p.length < 2) {
					return;
				}
				boolean start = p[0].equals("fieldstart");
				String id = p[1];
				if (id.endsWith("terrain")) {
					terrain = start ? id : id.equals(terrain) ? null : terrain;
				} else if (start) {
					ROOMS.add(id);
				} else {
					ROOMS.remove(id);
				}
			}
			case "weather" -> {
				if (p.length >= 3) {
					if (p[2].equals("start")) {
						weather = p[1];
					} else if (p[2].equals("end") && p[1].equals(weather)) {
						weather = null;
					}
				}
			}
			case "sidestart", "sideend" -> {
				if (p.length < 3) {
					return;
				}
				Side side = p[1].equals("ally") ? Side.ALLY : Side.OPPONENT;
				Map<String, Integer> effects = SIDES.computeIfAbsent(side, s -> new HashMap<>());
				if (p[0].equals("sidestart")) {
					effects.merge(p[2], 1, Integer::sum);
				} else {
					effects.remove(p[2]);
				}
			}
			default -> {
			}
		}
	}

	public static void reset() {
		previewing = false;
		terrain = null;
		weather = null;
		ROOMS.clear();
		SIDES.clear();
	}

	private static boolean any() {
		return terrain != null || weather != null || !ROOMS.isEmpty() || SIDES.values().stream().anyMatch(m -> !m.isEmpty());
	}

	// ---------------------------------------------------------------- where

	private static Vec3 sideAnchor(BattleStage stage, Side side) {
		Vec3 found = null;
		if (stage != null) {
			Entity active = stage.activePokemon(side == Side.ALLY);
			found = active != null ? active.position() : stage.trainerPosition(side == Side.ALLY);
		}
		Entity player = Minecraft.getInstance().player;
		if (found == null && previewing && player != null) {
			Vec3 ahead = player.getViewVector(1f).multiply(1, 0, 1).normalize();
			found = side == Side.ALLY ? player.position().add(ahead.scale(1.5)) : player.position().add(ahead.scale(7));
		}
		return found;
	}

	/** The middle of the field and its half length (from one side to the other), or null. */
	private static Vec3[] field(BattleStage stage) {
		Vec3 a = sideAnchor(stage, Side.ALLY);
		Vec3 b = sideAnchor(stage, Side.OPPONENT);
		if (a == null && b == null) {
			return null;
		}
		if (a == null || b == null) {
			Vec3 one = a != null ? a : b;
			return new Vec3[] { one, new Vec3(0, 0, 4) };
		}
		Vec3 half = b.subtract(a).scale(0.5);
		return new Vec3[] { a.add(half), half };
	}

	// ---------------------------------------------------------------- particles

	public static void tick() {
		ticks++;
		ClientLevel level = Minecraft.getInstance().level;
		BattleStage stage = BattleCameraDirector.stage();
		if (level == null || !any()) {
			return;
		}
		Vec3[] field = field(stage);
		if (field == null) {
			return;
		}
		double radius = field[1].length() + 3;
		if (terrain != null) {
			ParticleOptions p = switch (terrain) {
				case "grassyterrain" -> ParticleTypes.HAPPY_VILLAGER;
				case "electricterrain" -> ParticleTypes.ELECTRIC_SPARK;
				case "psychicterrain" -> ParticleTypes.ENCHANT;
				default -> ParticleTypes.END_ROD;
			};
			for (int i = 0; i < 2; i++) {
				Vec3 at = field[0].add(onDisc(radius));
				level.addParticle(p, at.x, at.y + 0.1, at.z, 0, 0.03, 0);
			}
		}
		if (weather != null) {
			switch (weather) {
				case "sandstorm" -> {
					for (int i = 0; i < 6; i++) {
						Vec3 at = field[0].add(onDisc(radius)).add(0, RANDOM.nextDouble() * 4, 0);
						level.addParticle(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState()), at.x, at.y, at.z, 0.4, 0, 0.1);
					}
				}
				case "snow", "hail" -> {
					for (int i = 0; i < 6; i++) {
						Vec3 at = field[0].add(onDisc(radius)).add(0, 6, 0);
						level.addParticle(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 0.02, -0.12, 0.02);
					}
				}
				case "raindance" -> {
					for (int i = 0; i < 3; i++) {
						Vec3 at = field[0].add(onDisc(radius));
						level.addParticle(ParticleTypes.SPLASH, at.x, at.y + 0.1, at.z, 0, 0.1, 0);
					}
				}
				default -> {
				}
			}
		}
		for (Map.Entry<Side, Map<String, Integer>> entry : SIDES.entrySet()) {
			Vec3 anchor = sideAnchor(stage, entry.getKey());
			if (anchor == null) {
				continue;
			}
			if (entry.getValue().containsKey("tailwind")) {
				Vec3 other = sideAnchor(stage, entry.getKey() == Side.ALLY ? Side.OPPONENT : Side.ALLY);
				Vec3 dir = other == null ? new Vec3(0, 0, 1) : other.subtract(anchor).normalize();
				Vec3 at = anchor.add(onDisc(2)).add(0, RANDOM.nextDouble() * 2, 0);
				level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, dir.x * 0.3, 0, dir.z * 0.3);
			}
			if (entry.getValue().containsKey("mist") && ticks % 2 == 0) {
				Vec3 at = anchor.add(onDisc(2));
				level.addParticle(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 0, 0.01, 0);
			}
		}
	}

	private static Vec3 onDisc(double radius) {
		double angle = RANDOM.nextDouble() * Math.PI * 2;
		double r = Math.sqrt(RANDOM.nextDouble()) * radius;
		return new Vec3(Math.cos(angle) * r, 0, Math.sin(angle) * r);
	}

	// ---------------------------------------------------------------- drawing

	public static void render(WorldRenderContext context) {
		if (!any() || context.matrixStack() == null) {
			return;
		}
		BattleStage stage = BattleCameraDirector.stage();
		Vec3[] field = field(stage);
		if (field == null) {
			return;
		}
		float t = ticks + context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		pose.pushPose();
		pose.translate(-camera.x, -camera.y, -camera.z);
		MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
		Matrix4f m = pose.last().pose();
		float radius = (float) field[1].length() + 3;
		Vec3 ground = field[0].add(0, 0.04, 0);
		if (terrain != null) {
			drawTerrain(pose, buffers, camera, ground, radius, t);
		}
		for (String room : ROOMS) {
			drawRoom(buffers.getBuffer(Draw.GLOW), m, camera, ground, field[1], radius, room, t);
		}
		if (weather != null) {
			drawWeather(buffers.getBuffer(Draw.GLOW), m, camera, ground, radius, t);
		}
		for (Map.Entry<Side, Map<String, Integer>> entry : SIDES.entrySet()) {
			Vec3 anchor = sideAnchor(stage, entry.getKey());
			Vec3 other = sideAnchor(stage, entry.getKey() == Side.ALLY ? Side.OPPONENT : Side.ALLY);
			if (anchor == null) {
				continue;
			}
			Vec3 forward = other == null ? new Vec3(0, 0, 1) : other.subtract(anchor).multiply(1, 0, 1).normalize();
			for (Map.Entry<String, Integer> effect : entry.getValue().entrySet()) {
				drawSide(pose, buffers, camera, anchor, forward, effect.getKey(), effect.getValue(), t);
			}
		}
		buffers.endBatch();
		pose.popPose();
	}

	private static void drawTerrain(PoseStack pose, MultiBufferSource buffers, Vec3 camera, Vec3 ground, float radius, float t) {
		Matrix4f m = pose.last().pose();
		int color = switch (terrain) {
			case "grassyterrain" -> 0x48D040;
			case "electricterrain" -> 0xFFE030;
			case "psychicterrain" -> 0xFF50B0;
			default -> 0xFFA8E8;
		};
		VertexConsumer glow = buffers.getBuffer(Draw.GLOW);
		// A glow on the ground, brighter at its rim, breathing.
		float breath = 0.85f + 0.15f * Mth.sin(t * 0.08f);
		for (int i = 0; i < 4; i++) {
			Draw.ring(glow, m, ground, new Vec3(0, 1, 0), radius * (0.25f + 0.25f * i), radius * 0.13f, color, (int) ((40 + 15 * i) * breath));
		}
		Draw.ring(glow, m, ground, new Vec3(0, 1, 0), radius, 0.25f, color, (int) (160 * breath));
		switch (terrain) {
			case "grassyterrain" -> {
				// Real grass and flowers sprouting on the field.
				RandomSource spots = RandomSource.create(7);
				BlockState[] plants = { Blocks.SHORT_GRASS.defaultBlockState(), Blocks.SHORT_GRASS.defaultBlockState(),
						Blocks.DANDELION.defaultBlockState(), Blocks.POPPY.defaultBlockState(), Blocks.CORNFLOWER.defaultBlockState() };
				for (int i = 0; i < 40; i++) {
					double angle = spots.nextDouble() * Math.PI * 2;
					double r = Math.sqrt(spots.nextDouble()) * radius;
					block(pose, buffers, plants[i % plants.length], ground.add(Math.cos(angle) * r, 0.3, Math.sin(angle) * r), 0.6f, spots.nextFloat() * 360);
				}
			}
			case "electricterrain" -> {
				RandomSource jag = RandomSource.create((long) (t / 3));
				for (int i = 0; i < 6; i++) {
					Vec3 a = ground.add(onDiscWith(jag, radius));
					Vec3 b = a.add(jag.nextGaussian() * 1.2, 0, jag.nextGaussian() * 1.2);
					Motifs.bolt(glow, m, camera, a, b, t + i, 0.03f, color, i);
					glow = buffers.getBuffer(Draw.GLOW);
				}
			}
			case "psychicterrain" -> {
				// Turning rune rings on the ground.
				for (int i = 0; i < 3; i++) {
					float r = radius * (0.35f + 0.25f * i);
					Draw.arc(glow, m, ground.add(0, 0.02, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), r, t * 0.02f * (i % 2 == 0 ? 1 : -1) + i, t * 0.02f * (i % 2 == 0 ? 1 : -1) + i + 5f, 1f, 0.12f, 0xFFB0E8, 170);
				}
			}
			default -> {
				// Misty: soft pink puffs hanging low.
				for (int i = 0; i < 14; i++) {
					double angle = i * 0.45 + t * 0.003;
					double r = radius * (0.3 + 0.6 * ((i * 0.37) % 1));
					Draw.halo(glow, m, ground.add(Math.cos(angle) * r, 0.4, Math.sin(angle) * r), camera, 0, 1.2f, color, 50);
				}
			}
		}
	}

	private static Vec3 onDiscWith(RandomSource random, double radius) {
		double angle = random.nextDouble() * Math.PI * 2;
		double r = Math.sqrt(random.nextDouble()) * radius;
		return new Vec3(Math.cos(angle) * r, 0, Math.sin(angle) * r);
	}

	/** Trick Room and the like: a glowing box over the whole field, its walls shimmering. */
	private static void drawRoom(VertexConsumer glow, Matrix4f m, Vec3 camera, Vec3 ground, Vec3 half, float radius, String room, float t) {
		int color = switch (room) {
			case "trickroom" -> 0xA040FF;
			case "magicroom" -> 0xA060FF;
			case "wonderroom" -> 0x60C0FF;
			default -> 0xB0B0B0;
		};
		if (room.equals("gravity")) {
			for (int i = 0; i < 20; i++) {
				double angle = i * 1.3;
				double r = radius * ((i * 0.41) % 1);
				float fall = (t * 0.15f + i * 0.37f) % 1f;
				Vec3 top = ground.add(Math.cos(angle) * r, 5 * (1 - fall), Math.sin(angle) * r);
				Draw.streak(glow, m, top, top.add(0, 1.2, 0), camera, 0.05f, color, 140);
			}
			return;
		}
		// A cube over the whole field (Adrien 2026-11-02: a cube, not a long box), walls tiled with squares as in the
		// games — the tiles light up one after another; Trick Room in violet.
		Vec3 along = half.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : half.multiply(1, 0, 1).normalize();
		Vec3 across = along.cross(new Vec3(0, 1, 0)).normalize();
		float s = Math.max(5f, radius) * 1.5f; // 50 % bigger (Adrien 2026-11-02)
		Vec3 base = ground.subtract(0, 0.02, 0);
		Vec3 x = along.scale(s);
		Vec3 z = across.scale(s);
		Vec3 y = new Vec3(0, s, 0);
		Vec3 centre = base.add(0, s, 0);
		float pulse = 0.8f + 0.2f * Mth.sin(t * 0.08f);
		int tiles = 8;
		// Each face: its centre and two half-sides.
		Vec3[][] faces = {
				{ base, x, z }, { base.add(y.scale(2)), x, z },
				{ centre.add(x), z, y }, { centre.subtract(x), z, y },
				{ centre.add(z), x, y }, { centre.subtract(z), x, y } };
		int face = 0;
		for (Vec3[] f : faces) {
			Vec3 c = f[0];
			Vec3 u = f[1];
			Vec3 v = f[2];
			// Tiles: a faint checkerboard, a wave of light running across it.
			for (int a = 0; a < tiles; a++) {
				for (int b = 0; b < tiles; b++) {
					Vec3 tile = c.add(u.scale((a + 0.5) * 2.0 / tiles - 1)).add(v.scale((b + 0.5) * 2.0 / tiles - 1));
					float wave = 0.5f + 0.5f * Mth.sin(t * 0.12f - (a + b) * 0.6f + face);
					int alpha = (int) (((a + b) % 2 == 0 ? 26 : 10) + 30 * wave * wave);
					Draw.panel(glow, m, tile, u.scale(0.9 / tiles), v.scale(0.9 / tiles), color, (int) (alpha * pulse));
				}
			}
			// Grout lines.
			for (int i = 0; i <= tiles; i++) {
				double k = i * 2.0 / tiles - 1;
				int alpha = (int) ((i == 0 || i == tiles ? 220 : 90) * pulse);
				Draw.streak(glow, m, c.add(u.scale(k)).subtract(v), c.add(u.scale(k)).add(v), camera, 0.03f, color, alpha);
				Draw.streak(glow, m, c.add(v.scale(k)).subtract(u), c.add(v.scale(k)).add(u), camera, 0.03f, color, alpha);
			}
			face++;
		}
	}

	private static void drawWeather(VertexConsumer glow, Matrix4f m, Vec3 camera, Vec3 ground, float radius, float t) {
		switch (weather) {
			case "sunnyday" -> {
				// Warm shafts of light on the field.
				for (int i = 0; i < 6; i++) {
					double angle = i * 1.05 + t * 0.004;
					Vec3 foot = ground.add(Math.cos(angle) * radius * 0.6, 0, Math.sin(angle) * radius * 0.6);
					Draw.streak(glow, m, foot, foot.add(4, 14, 2), camera, 1.2f, 0xFFD060, (int) (40 + 20 * Mth.sin(t * 0.05f + i)));
				}
				Draw.halo(glow, m, ground.add(0, 0.1, 0), camera, 0, radius, 0xFFC040, 30);
			}
			case "raindance" -> {
				RandomSource drops = RandomSource.create(3);
				for (int i = 0; i < 90; i++) {
					Vec3 at = ground.add(onDiscWith(drops, radius));
					float fall = (t * 0.25f + drops.nextFloat()) % 1f;
					Vec3 head = at.add(0, 7 * (1 - fall), 0);
					Draw.streak(glow, m, head, head.add(0.1, 0.9, 0.05), camera, 0.02f, 0x9CC8FF, 150);
				}
			}
			case "sandstorm" -> {
				for (int i = 0; i < 30; i++) {
					double h = (i * 0.37 % 1) * 4;
					double angle = i * 0.8;
					double sweep = (t * 0.08 + i * 0.13) % 2 - 1;
					Vec3 at = ground.add(Math.cos(angle) * radius * sweep, h, Math.sin(angle) * radius * 0.5);
					Draw.streak(glow, m, at, at.add(-1.6, 0.1, -0.3), camera, 0.06f, 0xD8B070, 110);
				}
				Draw.halo(glow, m, ground.add(0, 1.5, 0), camera, 0, radius * 1.2f, 0xC89850, 35);
			}
			default -> {
				// Snow and hail: a cold haze; the flakes are particles.
				Draw.halo(glow, m, ground.add(0, 1.5, 0), camera, 0, radius * 1.2f, 0xCFEFFF, 35);
				if (weather.equals("hail")) {
					RandomSource stones = RandomSource.create(5);
					for (int i = 0; i < 30; i++) {
						Vec3 at = ground.add(onDiscWith(stones, radius));
						float fall = (t * 0.3f + stones.nextFloat()) % 1f;
						Draw.halo(glow, m, at.add(0, 7 * (1 - fall), 0), camera, 0, 0.12f, 0xFFFFFF, 200);
					}
				}
			}
		}
	}

	private static void drawSide(PoseStack pose, MultiBufferSource buffers, Vec3 camera, Vec3 anchor, Vec3 forward, String effect,
			int layers, float t) {
		Matrix4f m = pose.last().pose();
		Vec3 side = forward.cross(new Vec3(0, 1, 0)).normalize();
		// Hazards lie on the ground in front of the side's Pokémon (where the next one will land).
		Vec3 ahead = anchor.add(forward.scale(1.5));
		switch (effect) {
			case "stealthrock" -> {
				for (int i = 0; i < 6; i++) {
					double angle = Math.PI * 2 * i / 6 + t * 0.01;
					Vec3 at = anchor.add(Math.cos(angle) * 2.2, 1.6 + 0.3 * Math.sin(t * 0.05 + i), Math.sin(angle) * 2.2);
					block(pose, buffers, Blocks.COBBLESTONE.defaultBlockState(), at, 0.4f, t * 0.5f + i * 60, 45, 30 + i * 9);
					Draw.halo(buffers.getBuffer(Draw.GLOW), m, at, camera, 0, 0.45f, 0xC8A858, 60);
				}
			}
			case "spikes", "toxicspikes" -> {
				BlockState spike = effect.equals("spikes") ? Blocks.POINTED_DRIPSTONE.defaultBlockState() : Blocks.AMETHYST_CLUSTER.defaultBlockState();
				RandomSource spots = RandomSource.create(effect.hashCode() + anchor.hashCode() % 7);
				int count = 4 * Math.min(3, layers);
				for (int i = 0; i < count; i++) {
					Vec3 at = ahead.add(side.scale(spots.nextGaussian() * 1.4)).add(forward.scale(spots.nextGaussian() * 0.8)).add(0, 0.25, 0);
					block(pose, buffers, spike, at, 0.5f, spots.nextFloat() * 360);
				}
				if (effect.equals("toxicspikes")) {
					Draw.halo(buffers.getBuffer(Draw.GLOW), m, ahead.add(0, 0.1, 0), camera, 0, 2f, 0xA040D0, 40);
				}
			}
			case "stickyweb" -> {
				VertexConsumer glow = buffers.getBuffer(Draw.GLOW);
				Vec3 centre = ahead.add(0, 0.05, 0);
				for (int i = 0; i < 8; i++) {
					double angle = Math.PI * 2 * i / 8;
					Draw.streak(glow, m, centre, centre.add(Math.cos(angle) * 2, 0, Math.sin(angle) * 2), camera, 0.025f, 0xF0F0F0, 170);
				}
				for (int ring = 1; ring <= 3; ring++) {
					Draw.ring(glow, m, centre, new Vec3(0, 1, 0), ring * 0.6f, 0.02f, 0xF0F0F0, 150);
				}
			}
			case "reflect", "lightscreen", "auroraveil", "safeguard" -> {
				int color = switch (effect) {
					case "reflect" -> 0xFF90E0;
					case "lightscreen" -> 0xFFF080;
					case "safeguard" -> 0x90E0FF;
					default -> 0x80FFD0;
				};
				VertexConsumer glow = buffers.getBuffer(Draw.GLOW);
				Vec3 wall = anchor.add(forward.scale(1.2)).add(0, 1.2, 0);
				float shimmer = 0.75f + 0.25f * Mth.sin(t * 0.12f + effect.hashCode());
				if (effect.equals("auroraveil")) {
					for (int i = 0; i < 16; i++) {
						Vec3 base = wall.add(side.scale((i - 7.5) * 0.25)).add(0, -1, 0);
						int hue = i % 3 == 0 ? 0x80FFD0 : i % 3 == 1 ? 0x80B0FF : 0xE090FF;
						Draw.streak(glow, m, base, base.add(0, 2.2 + 0.3 * Math.sin(t * 0.1 + i), 0), camera, 0.14f, hue, (int) (110 * shimmer));
					}
				} else {
					Draw.panel(glow, m, wall, side.scale(1.6), new Vec3(0, 1.2, 0), color, (int) (40 * shimmer));
					Vec3[] c = { wall.subtract(side.scale(1.6)).add(0, -1.2, 0), wall.add(side.scale(1.6)).add(0, -1.2, 0),
							wall.add(side.scale(1.6)).add(0, 1.2, 0), wall.subtract(side.scale(1.6)).add(0, 1.2, 0) };
					for (int i = 0; i < 4; i++) {
						Draw.streak(glow, m, c[i], c[(i + 1) % 4], camera, 0.04f, color, (int) (200 * shimmer));
					}
				}
			}
			case "tailwind" -> {
				VertexConsumer glow = buffers.getBuffer(Draw.GLOW);
				for (int i = 0; i < 8; i++) {
					float p = (t * 0.05f + i / 8f) % 1f;
					Vec3 at = anchor.add(side.scale((i - 3.5) * 0.4)).add(0, 0.5 + (i % 3) * 0.5, 0).add(forward.scale(-1 + p * 4));
					Draw.streak(glow, m, at, at.subtract(forward.scale(1.2)), camera, 0.04f, 0xE0F0FF, (int) (160 * Math.sin(p * Math.PI)));
				}
			}
			default -> {
			}
		}
	}

	private static void block(PoseStack pose, MultiBufferSource buffers, BlockState state, Vec3 at, float scale, float yaw) {
		block(pose, buffers, state, at, scale, yaw, 0, 0);
	}

	private static void block(PoseStack pose, MultiBufferSource buffers, BlockState state, Vec3 at, float scale, float yaw, float pitch, float roll) {
		pose.pushPose();
		pose.translate(at.x, at.y, at.z);
		pose.mulPose(Axis.YP.rotationDegrees(yaw));
		pose.mulPose(Axis.XP.rotationDegrees(pitch));
		pose.mulPose(Axis.ZP.rotationDegrees(roll));
		pose.scale(scale, scale, scale);
		pose.translate(-0.5, -0.5, -0.5);
		Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers, 0xF000F0, OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}

	// ---------------------------------------------------------------- preview

	/** {@code /showtime preview field <id>}: toggles one effect for a look, without a battle (around the player). */
	public static boolean preview(String id) {
		String key = switch (id) {
			case "grassyterrain", "electricterrain", "psychicterrain", "mistyterrain", "trickroom", "magicroom", "wonderroom", "gravity" -> "cobblemon.battle.fieldstart." + id;
			case "sunnyday", "raindance", "sandstorm", "snow", "hail" -> "cobblemon.battle.weather." + id + ".start";
			case "stealthrock", "spikes", "toxicspikes", "stickyweb", "reflect", "lightscreen", "auroraveil", "tailwind", "safeguard", "mist" -> "cobblemon.battle.sidestart.opponent." + id;
			case "clear" -> null;
			default -> "";
		};
		if (key == null) {
			reset();
			return true;
		}
		if (key.isEmpty()) {
			return false;
		}
		previewing = true;
		onKey(key);
		return true;
	}

	public static List<String> previewIds() {
		return List.of("grassyterrain", "electricterrain", "psychicterrain", "mistyterrain", "trickroom", "magicroom", "wonderroom",
				"gravity", "sunnyday", "raindance", "sandstorm", "snow", "hail", "stealthrock", "spikes", "toxicspikes", "stickyweb",
				"reflect", "lightscreen", "auroraveil", "tailwind", "safeguard", "mist", "clear");
	}
}
