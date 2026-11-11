package com.mystaria.showtime.ambiance;

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

import com.mystaria.showtime.api.ShowtimeHooks;
import com.mystaria.showtime.config.ShowtimeConfig;

/**
 * Plays an {@link Ambiance} around a battle's field (phases A1-A2, 2026-10-30), on this
 * client only, from the battle's start to its end — then the world is exactly as before: everything is drawn or read
 * through here, nothing of the world is changed. The field is the middle of the player trainers, as wide as they are
 * apart. Which ambiance: one another mod imposes ({@link ShowtimeHooks#ambianceOverride}: Phantasmon's lobby, a
 * scripted event), else the player's choice in Showtime's menu. Client thread only.
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
	/**
	 * A battle seen from afar, not watched (2026-11-02): its decorations stand around its field only, and what
	 * changes the whole world (time, sky, fog, weather, filter, sound, the world hidden) applies only while this player
	 * stands inside the field's circle.
	 */
	private static boolean distant;
	/** Distant mode: where the field's sides stand (its Pokémon), asked every tick. */
	private static java.util.function.Supplier<List<Vec3>> distantSpots;
	/** Distant mode: whether the player was inside the circle at the last tick. */
	private static boolean inside;
	private static long ticks;
	private static long startDayTime;
	private static int previewLeft;
	private static int nextStrike;
	private static int flashColor;
	private static int flashLeft;
	private static int nextBoltId = -2_000_000;
	private static final Random RANDOM = new Random();
	private static final List<Floating> FLOATING = new ArrayList<>();
	/** The camera this frame, from the field's centre: beams and sprites fade near it. */
	private static Vec3 cameraRel = Vec3.ZERO;
	/** Sprites (Arcade) and critters (Meadow): where each turns round the field. */
	private record Orbiter(float angle, float radius, float height, float phase, int kind) {
	}

	private static final List<Orbiter> SPRITES = new ArrayList<>();
	/** Corrupted's veins: segments on the ground (x0, z0, x1, z1, distance from their root), built once. */
	private static final List<double[]> VEINS = new ArrayList<>();
	private static long nextFlicker = -1;
	/** When each of the ambiance's ambient sounds plays next (ticks). */
	private static long[] nextAmbientSound = new long[0];
	/** Fireworks waiting their tick: when, where (relative to the field), and the next far-off one. */
	private record PendingFirework(long at, Vec3 where) {
	}

	private static final List<PendingFirework> FIREWORKS = new ArrayList<>();

	/** The line between the two trainers (horizontal, unit) and half its length: the court lies along it. */
	private static Vec3 fieldAxis = new Vec3(1, 0, 0);
	private static double fieldHalf = 6;

	/** A wormhole opened by a moment: when (ticks), or -1. */
	private static long wormholeBurst = -1;

	/** Notes of a moment's tune waiting their tick. */
	private record PendingNote(long at, String sound, float pitch, float volume) {
	}

	private static final List<PendingNote> NOTES = new ArrayList<>();

	/** Confetti in the air: born when, from where, how fast, its colour, its tumbling. */
	private record Confetti(long born, Vec3 from, Vec3 speed, int color, float spin, float phase) {
	}

	private static final List<Confetti> CONFETTI = new ArrayList<>();
	private static final int CONFETTI_LIFE = 260;

	/** Camera flashes in the stands: where (relative to the field) and when. */
	private record CameraFlash(Vec3 at, long born) {
	}

	private static final List<CameraFlash> CAMERA_FLASHES = new ArrayList<>();
	/** The crowd cheers until then (ticks). */
	private static long cheerUntil;
	/** The crowd's excitement, 0..1, this tick and the last (eased toward cheering, fading slowly after). */
	private static float crowdExcite = 0.12f;
	private static float crowdExcitePrev = 0.12f;
	/** The LED ribbon's white flash, fading. */
	private static float ledFlash;
	/** When a wave started running round the stands, or -1. */
	private static long crowdWave = -1;
	/** Where the followspots aim now (eased toward each Pokémon), relative to the field. */
	private static final Vec3[] SPOT_AIM = new Vec3[2];
	private static long nextFirework = -1;
	/** Film: the scratch running (screen x 0..1, ticks left) and a speck of dust (x, y, ticks left). */
	private static float scratchX;
	private static int scratchLeft;
	private static float dustX;
	private static float dustY;
	private static int dustLeft;
	private static int flickerLeft;
	private static final List<Orbiter> CRITTERS = new ArrayList<>();
	/** A moment's big text (Arcade's "CRITICAL!"), and how long it stays. */
	private static String hudText;
	private static int hudTextColor;
	private static int hudTextLeft;
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
	/** Showtime put a screen filter on (and takes it off at the end). */
	private static boolean filterOn;
	private static int momentFilterLeft;

	private BattleAmbiance() {
	}

	// ---------------------------------------------------------------- life

	/** A battle with these player trainers starts: the ambiance chosen (or imposed) plays around them. */
	public static void start(Collection<UUID> battleTrainers) {
		String id = ShowtimeHooks.ambianceOverride();
		if (id == null) {
			id = ownChoice();
		}
		begin(Ambiances.get(id), battleTrainers, 0);
	}

	/**
	 * A battle seen from afar (Phantasmon's field view, not watched): its ambiance — {@code id}, null or empty for this
	 * player's own — restricted to its field, whose sides {@code spots} gives. Never over this player's own battle.
	 */
	public static void startDistant(String id, java.util.function.Supplier<List<Vec3>> spots) {
		if (active() && !distant) {
			return;
		}
		begin(Ambiances.get(id == null || id.isEmpty() ? ownChoice() : id), List.of(), 0);
		if (active == null) {
			return;
		}
		distant = true;
		distantSpots = spots;
		inside = true; // corrected at the first tick: off if the player stands outside
		updateField(Minecraft.getInstance().level);
	}

	/** The player's own ambiance, or none once it is reserved and no longer unlocked for them. */
	private static String ownChoice() {
		String id = ShowtimeConfig.ambiance();
		return Ambiances.usable(id) ? id : NONE;
	}

	/** The battle seen from afar is over (or no longer seen). */
	public static void endDistant() {
		if (distant) {
			end();
		}
	}

	/** Whether the world-wide layers apply: always for this player's own battle, inside the field when distant. */
	private static boolean global() {
		return active() && (!distant || inside);
	}

	/** The menu's preview: around the player for 30 s ({@code none} stops it). */
	public static void preview(String id) {
		Minecraft mc = Minecraft.getInstance();
		begin(Ambiances.get(id), mc.player == null ? List.of() : List.of(mc.player.getUUID()), PREVIEW_TICKS);
	}

	/** The menu's preview is over: ended, but never a battle's ambiance (one seen from afar included). */
	public static void endPreview() {
		if (previewLeft > 0) {
			end();
		}
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
		distant = false;
		distantSpots = null;
		inside = false;
		previewLeft = 0;
		flashLeft = 0;
		FLOATING.clear();
		FIREWORKS.clear();
		NOTES.clear();
		CONFETTI.clear();
		CAMERA_FLASHES.clear();
		wormholeBurst = -1;
		cheerUntil = 0;
		crowdExcite = 0.12f;
		crowdExcitePrev = 0.12f;
		ledFlash = 0;
		crowdWave = -1;
		SPOT_AIM[0] = null;
		SPOT_AIM[1] = null;
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
		SPRITES.clear();
		if (ambiance.sprites != null) {
			Ambiance.Sprites sp = ambiance.sprites;
			for (int i = 0; i < sp.count; i++) {
				SPRITES.add(new Orbiter((float) (Math.PI * 2 * i / Math.max(1, sp.count) + RANDOM.nextFloat() * 0.4f),
						between(sp.radius), between(sp.height), RANDOM.nextFloat() * 6.28f, i % Math.max(1, sp.shapes.size())));
			}
		}
		VEINS.clear();
		if (ambiance.veins != null) {
			buildVeins(ambiance.veins);
		}
		nextAmbientSound = new long[ambiance.ambientSounds.size()];
		for (int i = 0; i < nextAmbientSound.length; i++) {
			nextAmbientSound[i] = ticks + 40 + betweenTicks(ambiance.ambientSounds.get(i).every);
		}
		scratchLeft = 0;
		dustLeft = 0;
		FIREWORKS.clear();
		nextFirework = ambiance.fireworks != null && !ambiance.fireworks.every.isEmpty()
				? ticks + 60 + betweenTicks(ambiance.fireworks.every) : -1;
		flickerLeft = 0;
		nextFlicker = ambiance.flicker != null ? ticks + 60 + betweenTicks(ambiance.flicker.every) : -1;
		CRITTERS.clear();
		if (ambiance.critters != null) {
			for (int i = 0; i < ambiance.critters.count; i++) {
				CRITTERS.add(new Orbiter(RANDOM.nextFloat() * 6.28f, ambiance.critters.radius * (0.5f + 0.6f * RANDOM.nextFloat()),
						0.8f + RANDOM.nextFloat() * 2.2f, RANDOM.nextFloat() * 6.28f, i));
			}
		}
		hudTextLeft = 0;
		setFilter(lastingFilter(ambiance));
		if (ambiance.sound != null && ambiance.sound.id != null) {
			sound = new LoopSound(ResourceLocation.parse(ambiance.sound.id), ambiance.sound.volume, ambiance.sound.pitch);
			mc.getSoundManager().play(sound);
		}
	}

	/** Every client tick: the field follows the trainers, particles, floating blocks' flames, lightning. */
	public static void tick() {
		GROUND.clear(); // ground heights are relative to the field's centre, which moves
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
		if (hudTextLeft > 0) {
			hudTextLeft--;
		}
		if (flickerLeft > 0) {
			flickerLeft--;
		}
		if (active != null && active.flicker != null && nextFlicker >= 0 && ticks >= nextFlicker) {
			flickerLeft = active.flicker.ticks;
			nextFlicker = ticks + Math.max(20, betweenTicks(active.flicker.every));
		}
		if (momentFilterLeft > 0 && --momentFilterLeft == 0) {
			setFilter(lastingFilter(active)); // the moment's filter is over: back to the ambiance's own
		}
		updateField(mc.level);
		if (center == null) {
			return;
		}
		if (distant && mc.player != null) {
			double dx = mc.player.getX() - center.x;
			double dz = mc.player.getZ() - center.z;
			boolean now = dx * dx + dz * dz <= fieldRadius * fieldRadius;
			if (now != inside) {
				inside = now;
				// Stepping in or out: the filter and the sound follow.
				setFilter(inside ? lastingFilter(active) : null);
				if (inside && active.sound != null && active.sound.id != null && sound == null) {
					sound = new LoopSound(ResourceLocation.parse(active.sound.id), active.sound.volume, active.sound.pitch);
					mc.getSoundManager().play(sound);
				} else if (!inside) {
					stopSound();
				}
			}
		}
		float budget = particleBudget();
		for (Ambiance.Particles particles : active.particles) {
			spawn(mc.level, particles, budget);
		}
		if (active.film != null) {
			if (scratchLeft > 0) {
				scratchLeft--;
				scratchX += (RANDOM.nextFloat() - 0.5f) * 0.004f;
			} else if (RANDOM.nextFloat() < active.film.scratches) {
				scratchX = 0.1f + RANDOM.nextFloat() * 0.8f;
				scratchLeft = 4 + RANDOM.nextInt(10);
			}
			if (dustLeft > 0) {
				dustLeft--;
			} else if (RANDOM.nextFloat() < active.film.dust) {
				dustX = RANDOM.nextFloat();
				dustY = RANDOM.nextFloat();
				dustLeft = 1 + RANDOM.nextInt(2);
			}
		}
		if (active.fireworks != null && nextFirework >= 0 && ticks >= nextFirework) {
			nextFirework = ticks + Math.max(20, betweenTicks(active.fireworks.every));
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			double far = between(active.fireworks.distance);
			FIREWORKS.add(new PendingFirework(ticks, new Vec3(Math.cos(angle) * far, between(active.fireworks.height), Math.sin(angle) * far)));
		}
		for (int i = NOTES.size() - 1; i >= 0; i--) {
			if (ticks >= NOTES.get(i).at()) {
				PendingNote note = NOTES.remove(i);
				mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
						SoundEvent.createVariableRangeEvent(ResourceLocation.parse(note.sound())), note.pitch(), note.volume()));
			}
		}
		CONFETTI.removeIf(piece -> ticks - piece.born() > CONFETTI_LIFE);
		CAMERA_FLASHES.removeIf(flash -> ticks - flash.born() > 5);
		if (active.crowd != null) {
			crowdExcitePrev = crowdExcite;
			float target = ticks < cheerUntil ? 1f : 0.12f; // never quite still: a murmur of movement between moments
			crowdExcite = target > crowdExcite ? crowdExcite + (target - crowdExcite) * 0.3f : crowdExcite * 0.965f;
			ledFlash *= calm() ? 0.8f : 0.92f;
			float rate = active.crowd.flashes * (calm() ? 0.5f : 1f);
			int count = (int) rate + (RANDOM.nextFloat() < rate - (int) rate ? 1 : 0);
			for (int i = 0; i < count; i++) {
				cameraFlash();
			}
		}
		for (int i = FIREWORKS.size() - 1; i >= 0; i--) {
			if (ticks >= FIREWORKS.get(i).at()) {
				burst(mc.level, FIREWORKS.remove(i).where());
			}
		}
		if (!distant || inside) {
			for (int i = 0; i < nextAmbientSound.length && i < active.ambientSounds.size(); i++) {
				if (ticks >= nextAmbientSound[i]) {
					Ambiance.AmbientSound ambient = active.ambientSounds.get(i);
					nextAmbientSound[i] = ticks + Math.max(20, betweenTicks(ambient.every));
					playAmbient(mc, ambient);
				}
			}
		}
		if (active.floatingBlocks != null && active.floatingBlocks.flame && ticks % 4 == 0) {
			for (Floating floating : FLOATING) {
				Vec3 at = floatingPosition(floating, 0f);
				if (RANDOM.nextInt(3) == 0) {
					mc.level.addParticle(ParticleTypes.SMALL_FLAME, at.x, at.y + 0.55, at.z, 0, 0.005, 0);
				}
			}
		}
		if (active.floatingBlocks != null && active.floatingBlocks.sparkle != null && ticks % 3 == 0) {
			ParticleOptions sparkle = particle(active.floatingBlocks.sparkle);
			for (Floating floating : FLOATING) {
				if (sparkle != null && RANDOM.nextInt(4) == 0) {
					Vec3 at = floatingPosition(floating, 0f);
					mc.level.addParticle(sparkle, at.x + RANDOM.nextGaussian() * 0.6, at.y + 0.5 + RANDOM.nextGaussian() * 0.6,
							at.z + RANDOM.nextGaussian() * 0.6, 0, 0, 0);
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
			case "defeat" -> active.moments.defeat != null ? active.moments.defeat : noCelebration(active.moments.victory);
			default -> null;
		};
		if (moment == null) {
			return;
		}
		ParticleOptions particle = particle(moment.particle);
		if (particle != null) {
			int count = calm() ? moment.count / 2 : moment.count;
			for (int i = 0; i < count; i++) {
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				double r = RANDOM.nextDouble() * fieldRadius * 0.6;
				mc.level.addParticle(particle, center.x + Math.cos(angle) * r, center.y + 0.5 + RANDOM.nextDouble() * 3,
						center.z + Math.sin(angle) * r, (RANDOM.nextDouble() - 0.5) * 0.3, RANDOM.nextDouble() * 0.3,
						(RANDOM.nextDouble() - 0.5) * 0.3);
			}
		}
		if (moment.wormhole) {
			wormholeBurst = ticks;
		}
		if (moment.cheer) {
			cheerUntil = ticks + 70;
			ledFlash = calm() ? 0.4f : 1f;
		}
		if (moment.wave) {
			crowdWave = ticks;
		}
		for (int i = 0; i < (calm() ? moment.cameraFlashes / 2 : moment.cameraFlashes) && active.crowd != null; i++) {
			cameraFlash();
		}
		for (int i = 0; i < moment.notes.size(); i++) {
			float pitch = moment.notes.get(i);
			if (pitch > 0) {
				NOTES.add(new PendingNote(ticks + (long) i * Math.max(1, moment.noteEvery), moment.noteSound, pitch, moment.noteVolume));
			}
		}
		if (moment.pyro) {
			pyro(mc.level);
		}
		if (moment.confetti > 0) {
			launchConfetti(moment.confetti);
		}
		for (int i = 0; i < moment.fireworks; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			double r = fieldRadius * (0.3 + RANDOM.nextDouble() * 0.9);
			FIREWORKS.add(new PendingFirework(ticks + i * 6L + RANDOM.nextInt(4),
					new Vec3(Math.cos(angle) * r, 9 + RANDOM.nextDouble() * 6, Math.sin(angle) * r)));
		}
		if (moment.flash != null) {
			flashColor = Ambiance.color(moment.flash, 0xFFFFFF);
			flashLeft = FLASH_TICKS;
		}
		if (moment.text != null) {
			hudText = moment.text;
			hudTextColor = Ambiance.color(moment.textColor, 0xFFFFFF);
			hudTextLeft = 26;
		}
		if (moment.lightning) {
			strike(mc.level);
		}
		if (moment.sound != null) {
			mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
					SoundEvent.createVariableRangeEvent(ResourceLocation.parse(moment.sound)), moment.pitch, moment.volume));
		}
		if (moment.filter != null && !calm()) {
			setFilter(moment.filter);
			momentFilterLeft = Math.max(1, moment.filterTicks);
		}
	}

	/** The calm setting was flipped (from the menu, maybe mid-battle): the lasting filter follows at once. */
	public static void calmChanged() {
		if (active() && momentFilterLeft == 0 && (!distant || inside)) {
			setFilter(lastingFilter(active));
		}
	}

	/**
	 * Victory's moment played for a loss (an ambiance without a defeat of its own): its particles, flash and filter, but
	 * no fireworks, no banner and no victory sound (2026-11-10: Sakura shot fireworks on a defeat).
	 */
	private static Ambiance.Moment noCelebration(Ambiance.Moment victory) {
		if (victory == null) {
			return null;
		}
		Ambiance.Moment copy = new Ambiance.Moment();
		copy.particle = victory.particle;
		copy.count = victory.count;
		copy.flash = victory.flash;
		copy.lightning = victory.lightning;
		copy.filter = victory.filter;
		copy.filterTicks = victory.filterTicks;
		return copy;
	}

	/** Calm effects (a setting): no strobe or flickers, soft flashes, no moment filters, half the particles. */
	private static boolean calm() {
		return ShowtimeConfig.calmEffects();
	}

	/** The ambiance's own filter for the whole battle, as the calm setting allows it. */
	private static String lastingFilter(Ambiance ambiance) {
		if (calm() && ambiance.calmFilter != null) {
			return "none".equals(ambiance.calmFilter) ? null : ambiance.calmFilter;
		}
		return ambiance.filter;
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
			((com.mystaria.showtime.mixin.GameRendererAccessor) mc.gameRenderer).showtime$loadEffect(ResourceLocation.parse(id));
			filterOn = true;
		} catch (RuntimeException ex) {
			filterOn = false;
		}
	}

	private static void updateField(ClientLevel level) {
		List<Vec3> spots = new ArrayList<>();
		if (distantSpots != null) {
			spots.addAll(distantSpots.get());
		}
		for (UUID uuid : trainers) {
			Player player = ShowtimeHooks.player(level, uuid);
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
		double lowest = Double.MAX_VALUE;
		for (Vec3 spot : spots) {
			sum = sum.add(spot);
			lowest = Math.min(lowest, spot.y);
		}
		// The battle's Pokémon too: in a hollow below their trainers, the field is down where they fight (2026-11-10).
		com.mystaria.showtime.api.BattleStage stage = com.mystaria.showtime.battle.BattleCameraDirector.stage();
		if (stage != null) {
			for (boolean local : new boolean[] {true, false}) {
				net.minecraft.world.entity.Entity pokemon = stage.activePokemon(local);
				if (pokemon != null && !pokemon.isRemoved() && pokemon.position().distanceToSqr(sum.scale(1.0 / spots.size())) < 48 * 48) {
					lowest = Math.min(lowest, pokemon.getY());
				}
			}
		}
		// Across the field, the centre; in height, the lowest of the trainers and Pokémon: the ambiance stands on the
		// floor of the battle, not halfway up to trainers on a ledge.
		Vec3 middle = sum.scale(1.0 / spots.size());
		center = new Vec3(middle.x, lowest, middle.z);
		float apart = 0f;
		for (Vec3 spot : spots) {
			apart = Math.max(apart, (float) spot.distanceTo(center));
		}
		fieldRadius = Math.max(8f, apart + 5f);
		if (spots.size() >= 2) {
			Vec3 across = spots.get(1).subtract(spots.get(0));
			across = new Vec3(across.x, 0, across.z);
			if (across.length() > 0.5) {
				fieldAxis = across.normalize();
				fieldHalf = across.length() / 2;
			}
		}
	}

	// ---------------------------------------------------------------- what the world reads (mixins)

	/** The day time this client shows (sun, moon, stars, light), or {@code world} outside an ambiance with a time. */
	public static long dayTime(long world) {
		if (!global() || active.time == null) {
			return world;
		}
		return startDayTime + (long) (ticks * active.time.speed);
	}

	public static Vec3 skyColor(Vec3 world) {
		return blend(world, global() && active.sky != null ? active.sky.skyColor : null);
	}

	public static Vec3 cloudColor(Vec3 world) {
		return blend(world, global() && active.sky != null ? active.sky.cloudColor : null);
	}

	/** The fog colour over the world's ({@code rgb} 0..1, changed in place). */
	public static void fogColor(float[] rgb) {
		if (!global() || active.sky == null || active.sky.fogColor == null) {
			return;
		}
		Vec3 blended = blend(new Vec3(rgb[0], rgb[1], rgb[2]), active.sky.fogColor);
		rgb[0] = (float) blended.x;
		rgb[1] = (float) blended.y;
		rgb[2] = (float) blended.z;
	}

	/** Fog start / end in blocks, or NaN for the world's. */
	public static float fogStart() {
		return global() && active.sky != null && active.sky.fogStart != null ? active.sky.fogStart : Float.NaN;
	}

	public static float fogEnd() {
		return global() && active.sky != null && active.sky.fogEnd != null ? active.sky.fogEnd : Float.NaN;
	}

	/** {@code LevelRendererHideWorldMixin}: no block of the world drawn. */
	public static boolean hidesWorld() {
		return global() && active.hideWorld;
	}

	/** Rain shown (0..1), or {@code world}'s. */
	public static float rainLevel(float world) {
		return global() && active.sky != null && active.sky.rain != null ? active.sky.rain : world;
	}

	public static float thunderLevel(float world) {
		return global() && active.sky != null && active.sky.thunder != null ? active.sky.thunder : world;
	}

	public static float starBrightness(float world) {
		return global() && active.sky != null && active.sky.stars != null ? active.sky.stars : world;
	}

	/** The clouds' height: NaN hides them. */
	public static float cloudHeight(float world) {
		if (!global() || active.sky == null) {
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
		cameraRel = camera.subtract(center);
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		if (active.moon != null || active.volcano != null || active.stripedSun != null || active.mirage != null
				|| !active.wormholes.isEmpty()) {
			// Far things on the sky go without fog (2026-11-10): an ambiance's close fog swallowed them (Inferno's
			// volcano, Spectral's moon). Their own batch, so the fog comes back for everything else.
			buffers.endBatch();
			float fogStart = com.mojang.blaze3d.systems.RenderSystem.getShaderFogStart();
			com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(Float.MAX_VALUE);
			if (active.moon != null) {
				drawMoonHalo(pose, buffers, active.moon);
			}
			if (active.volcano != null) {
				drawVolcano(pose, buffers, active.volcano, time, true);
			}
			if (active.mirage != null) {
				drawMirage(pose, buffers, active.mirage, time);
			}
			if (active.stripedSun != null) {
				drawStripedSun(pose, buffers, active.stripedSun, true);
			}
			for (Ambiance.Wormhole wormhole : active.wormholes) {
				drawWormhole(pose, buffers, wormholePlace(wormhole), wormhole, wormhole.size, time, 0);
			}
			buffers.endBatch();
			if (active.moon != null) {
				drawMoonDisc(pose, buffers, active.moon);
			}
			if (active.volcano != null) {
				drawVolcano(pose, buffers, active.volcano, time, false);
			}
			if (active.stripedSun != null) {
				drawStripedSun(pose, buffers, active.stripedSun, false);
			}
			for (Ambiance.Wormhole wormhole : active.wormholes) {
				drawWormhole(pose, buffers, wormholePlace(wormhole), wormhole, wormhole.size, time, 1);
			}
			buffers.endBatch();
			for (Ambiance.Wormhole wormhole : active.wormholes) {
				drawWormhole(pose, buffers, wormholePlace(wormhole), wormhole, wormhole.size, time, 2);
			}
			buffers.endBatch();
			com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(fogStart);
		}
		if (active.islands != null || active.island != null || active.crowd != null || active.lightTowers != null) {
			// Solid scenery first, writing depth: the glows drawn after are hidden behind it where they should be.
			buffers.endBatch();
			if (active.islands != null) {
				drawIslands(pose, buffers, active.islands, time, false);
			}
			if (active.island != null) {
				drawFieldIsland(pose, buffers, active.island, time, false);
			}
			if (active.crowd != null) {
				drawStands(pose, buffers, active.crowd, time, false);
			}
			if (active.lightTowers != null) {
				drawLightTowers(pose, buffers, active.lightTowers, time, false);
			}
			buffers.endBatch();
		}
		if (active.backdrop != null) {
			drawBackdrop(pose, buffers, active.backdrop, time);
		}
		if (active.galaxy != null) {
			drawGalaxy(pose, buffers, active.galaxy, time);
		}
		if (!active.nebulae.isEmpty()) {
			drawNebulae(pose, buffers, time);
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
		if (active.danceFloor != null) {
			drawDanceFloor(pose, buffers, active.danceFloor, time);
		}
		if (active.neonGrid != null) {
			drawNeonGrid(pose, buffers, active.neonGrid, time);
		}
		if (active.mirrorBall != null) {
			drawMirrorBallDots(pose, buffers, active.mirrorBall, time);
		}
		if (active.sprites != null) {
			drawSprites(pose, buffers, active.sprites, time);
		}
		if (active.veins != null) {
			drawVeins(pose, buffers, active.veins, time);
		}
		if (active.skyCracks != null) {
			drawSkyCracks(pose, buffers, active.skyCracks, time);
		}
		if (active.wisps != null) {
			drawWisps(pose, buffers, active.wisps, time);
		}
		if (active.bubbles != null) {
			drawBubbles(pose, buffers, active.bubbles, time);
		}
		if (active.caustics != null) {
			drawCaustics(pose, buffers, active.caustics, time);
		}
		if (active.glints != null) {
			drawGlints(pose, buffers, active.glints, time);
		}
		if (active.islands != null) {
			drawIslands(pose, buffers, active.islands, time, true);
		}
		if (active.island != null) {
			drawFieldIsland(pose, buffers, active.island, time, true);
		}
		if (wormholeBurst >= 0 && !active.wormholes.isEmpty()) {
			drawWormholeBurst(pose, buffers, time, partialTick, true);
		}
		if (active.court != null) {
			drawCourt(pose, buffers, active.court);
		}
		if (active.lightTowers != null) {
			drawLightTowers(pose, buffers, active.lightTowers, time, true);
		}
		if (active.followspots != null && active.lightTowers != null) {
			drawFollowspots(pose, buffers, active.followspots, active.lightTowers);
		}
		if (active.crowd != null) {
			drawStands(pose, buffers, active.crowd, time, true);
		}
		if (!CAMERA_FLASHES.isEmpty()) {
			drawCameraFlashes(pose, buffers, time);
		}
		if (active.shafts != null) {
			drawShafts(pose, buffers, active.shafts, time);
		}
		if (active.embers != null) {
			drawEmbers(pose, buffers, active.embers, time);
		}
		if (active.lanterns != null) {
			drawLanterns(pose, buffers, active.lanterns, time, true);
		}
		if (active.mushrooms != null) {
			drawMushrooms(pose, buffers, active.mushrooms, time, true);
		}
		if (active.glitchCubes != null) {
			drawGlitchCubes(pose, buffers, active.glitchCubes, true);
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
		// Solid colours after the light: the mirror ball itself, the critters.
		if (active.mirrorBall != null) {
			drawMirrorBall(pose, buffers, active.mirrorBall, time);
		}
		if (active.critters != null) {
			drawCritters(pose, buffers, active.critters, time);
		}
		if (active.glitchCubes != null) {
			drawGlitchCubes(pose, buffers, active.glitchCubes, false);
		}
		if (active.ghosts != null) {
			drawGhosts(pose, buffers, active.ghosts, time);
		}
		if (active.mushrooms != null) {
			drawMushrooms(pose, buffers, active.mushrooms, time, false);
		}
		if (active.kelp != null) {
			drawKelp(pose, buffers, active.kelp, time);
		}
		if (active.palms != null) {
			drawPalms(pose, buffers, active.palms, time);
		}
		if (active.birds != null) {
			drawBirds(pose, buffers, active.birds, time);
		}
		if (active.fogWall != null) {
			drawFogWall(pose, buffers, active.fogWall, time);
		}
		if (wormholeBurst >= 0 && !active.wormholes.isEmpty()) {
			drawWormholeBurst(pose, buffers, time, partialTick, false);
		}
		if (!CONFETTI.isEmpty()) {
			drawConfetti(pose, buffers, time);
		}
		if (active.lanterns != null) {
			drawLanterns(pose, buffers, active.lanterns, time, false);
		}
		if (active.mist != null) {
			drawMist(pose, buffers, active.mist, time);
		}
		buffers.endBatch();
		if (floatingState != null) {
			for (Floating floating : FLOATING) {
				Vec3 world = floatingPosition(floating, partialTick);
				// Shrinks away near the camera: one drifting across a low battle shot filled the screen (2026-11-09).
				float shrink = (float) Mth.clamp((world.add(0, 0.5, 0).distanceTo(camera) - 1.2) / 2.3, 0, 1);
				if (shrink <= 0.01f) {
					continue;
				}
				Vec3 at = world.subtract(center);
				pose.pushPose();
				pose.translate(at.x, at.y + 0.5, at.z);
				pose.scale(shrink, shrink, shrink);
				pose.translate(-0.5, -0.5, -0.5);
				mc.getBlockRenderer().renderSingleBlock(floatingState, pose, buffers, 0xF000F0, OverlayTexture.NO_OVERLAY);
				pose.popPose();
			}
			buffers.endBatch();
		}
		pose.popPose();
	}

	/** HUD pass: the flash of a strong moment. */
	public static void renderHud(GuiGraphics graphics) {
		if (active() && active.strobe != null && !active.strobe.colors.isEmpty() && active.strobe.every > 0 && !calm()) {
			long beat = ticks / active.strobe.every;
			float within = (ticks % active.strobe.every) / (float) active.strobe.every;
			int color = Ambiance.color(active.strobe.colors.get((int) (beat % active.strobe.colors.size())), 0xFFFFFF);
			int alpha = Math.round(255 * active.strobe.alpha * (1f - within) * (1f - within));
			if (alpha > 0) {
				graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (alpha << 24) | color);
			}
		}
		if (active() && active.film != null) {
			drawFilm(graphics, active.film);
		}
		if (hudTextLeft > 0 && hudText != null) {
			drawHudText(graphics);
		}
		if (flickerLeft > 0 && active() && active.flicker != null && !calm()) {
			int dark = Math.round(255 * Mth.clamp(active.flicker.alpha, 0f, 1f) * (flickerLeft % 2 == 0 ? 1f : 0.7f));
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), dark << 24);
		}
		if (flashLeft <= 0) {
			return;
		}
		int alpha = Math.round((calm() ? 45f : 150f) * flashLeft / FLASH_TICKS);
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
			VertexConsumer glow = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
			int color = Ambiance.color(backdrop.starColor, 0xFFFFFF);
			int[] colors = backdrop.starColors.stream().mapToInt(c -> Ambiance.color(c, 0xFFFFFF)).toArray();
			RandomSource seeded = RandomSource.create(1234L);
			float spin = time / 1200f * backdrop.spin * (float) Math.PI * 2;
			for (int i = 0; i < backdrop.stars; i++) {
				double yaw = seeded.nextDouble() * Math.PI * 2 + spin;
				double pitch = backdrop.starsAround ? Math.asin(seeded.nextDouble() * 2 - 1) : Math.asin(seeded.nextDouble() * 1.8 - 0.8);
				// Mostly tiny, a few bright: a sky, not confetti.
				float roll = seeded.nextFloat();
				float size = (roll > 0.97f ? 0.35f : 0.06f + roll * roll * 0.18f) * backdrop.starSize;
				float twinkle = 0.6f + 0.4f * Mth.sin(time * 0.1f + i);
				Vec3 dir = new Vec3(Math.cos(pitch) * Math.cos(yaw), Math.sin(pitch), Math.cos(pitch) * Math.sin(yaw));
				int c = colors.length > 0 ? colors[i % colors.length] : color;
				quadFacing(glow, m, dir.scale(r * 0.94), dir, size * r / 30f, c, (int) (220 * twinkle));
			}
		}
	}

	private static void drawRainbow(PoseStack pose, MultiBufferSource buffers, Ambiance.Rainbow rainbow, Vec3 camera) {
		int[] bands = { 0xFF3B30, 0xFF9500, 0xFFCC00, 0x34C759, 0x32ADE6, 0x5856D6, 0xAF52DE };
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		// Behind the field as the player saw it at the start, and fixed there.
		Vec3 back = rainbowBack;
		Vec3 across = new Vec3(-back.z, 0, back.x);
		Vec3 origin = back.scale(rainbow.radius * 0.6);
		int alpha = (int) (255 * Mth.clamp(rainbow.alpha, 0f, 1f));
		bow(out, m, origin, across, rainbow.radius, rainbow.radius * 0.035f, bands, alpha);
		if (rainbow.doubled) {
			// The second bow: further out, wider bands, fainter, red on the inside.
			int[] reversed = new int[bands.length];
			for (int b = 0; b < bands.length; b++) {
				reversed[b] = bands[bands.length - 1 - b];
			}
			bow(out, m, origin, across, rainbow.radius * 1.32f, rainbow.radius * 0.05f, reversed, Math.round(alpha * 0.4f));
		}
	}

	/**
	 * One bow: its colours blend into each other (one stop per band, in the band's middle), and its two edges fade to
	 * nothing, so it reads as light, not as painted stripes.
	 */
	private static void bow(VertexConsumer out, Matrix4f m, Vec3 origin, Vec3 across, float radius, float width, int[] bands,
			int alpha) {
		int stops = bands.length + 2;
		float[] r = new float[stops];
		int[] c = new int[stops];
		int[] a = new int[stops];
		r[0] = radius;
		c[0] = bands[0];
		r[stops - 1] = radius - bands.length * width;
		c[stops - 1] = bands[bands.length - 1];
		for (int b = 0; b < bands.length; b++) {
			r[b + 1] = radius - (b + 0.5f) * width;
			c[b + 1] = bands[b];
			a[b + 1] = alpha;
		}
		int segments = 64;
		for (int k = 0; k < stops - 1; k++) {
			for (int s = 0; s < segments; s++) {
				double a0 = Math.PI * s / segments;
				double a1 = Math.PI * (s + 1) / segments;
				// The bow's feet fade into the ground haze.
				float f0 = (float) Mth.clamp(Math.sin(a0) * 4, 0, 1);
				float f1 = (float) Mth.clamp(Math.sin(a1) * 4, 0, 1);
				vertex(out, m, origin.add(across.scale(Math.cos(a0) * r[k])).add(0, Math.sin(a0) * r[k], 0), c[k], Math.round(a[k] * f0));
				vertex(out, m, origin.add(across.scale(Math.cos(a0) * r[k + 1])).add(0, Math.sin(a0) * r[k + 1], 0), c[k + 1], Math.round(a[k + 1] * f0));
				vertex(out, m, origin.add(across.scale(Math.cos(a1) * r[k + 1])).add(0, Math.sin(a1) * r[k + 1], 0), c[k + 1], Math.round(a[k + 1] * f1));
				vertex(out, m, origin.add(across.scale(Math.cos(a1) * r[k])).add(0, Math.sin(a1) * r[k], 0), c[k], Math.round(a[k] * f1));
			}
		}
	}

	private static void drawSpotlights(PoseStack pose, MultiBufferSource buffers, Ambiance.Spotlights spotlights, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		float ring = fieldRadius + spotlights.ring;
		for (int i = 0; i < spotlights.count; i++) {
			// All round the field, or gathered in an arc on one side (the sun's).
			double baseAngle = spotlights.sector > 0
					? Math.toRadians(spotlights.facing + spotlights.sector * ((spotlights.count == 1 ? 0.5 : i / (double) (spotlights.count - 1)) - 0.5))
					: Math.PI * 2 * i / spotlights.count;
			Vec3 base = new Vec3(Math.cos(baseAngle) * ring, 0, Math.sin(baseAngle) * ring);
			double sweep = time / 1200.0 * spotlights.sweep * Math.PI * 2 + i * 1.7;
			// Leaning in over the field and swaying.
			Vec3 lean = new Vec3(-Math.cos(baseAngle), 0, -Math.sin(baseAngle)).scale(0.45 + 0.25 * Math.sin(sweep))
					.add(new Vec3(-Math.sin(baseAngle), 0, Math.cos(baseAngle)).scale(0.5 * Math.cos(sweep * 0.7)));
			Vec3 top = base.add(lean.scale(spotlights.height)).add(0, spotlights.height, 0);
			int color = Ambiance.color(spotlights.colors.get(i % spotlights.colors.size()), 0xFFFFFF);
			if (spotlights.fromSky) {
				// Sun rays (2026-11-10): all fanning out of the sun, high up on its side of the sky, each landing on
				// the ground beyond the field, at its own distance, drifting slowly. Not pillars round the field, nor on it.
				double facing = Math.toRadians(spotlights.facing);
				Vec3 sun = new Vec3(Math.cos(facing), 0, Math.sin(facing)).scale(fieldRadius + spotlights.height)
						.add(0, spotlights.height * 1.4, 0);
				double spread = spotlights.sector > 0 ? Math.toRadians(spotlights.sector) : Math.PI / 2;
				double landAngle = facing + spread * ((spotlights.count == 1 ? 0.5 : i / (double) (spotlights.count - 1)) - 0.5)
						+ 0.05 * Math.sin(sweep);
				double landDistance = fieldRadius + spotlights.ring + 6 * hashFloat(i * 7577 + 3);
				Vec3 ground = onGround(Math.cos(landAngle) * landDistance, Math.sin(landAngle) * landDistance);
				beam(out, m, sun, ground, spotlights.width * 0.6f, spotlights.width * 3.5f, color, spotlights.alpha);
			} else {
				beam(out, m, base, top, spotlights.width * 0.25f, spotlights.width * 2.5f, color, spotlights.alpha);
			}
		}
	}

	private static void drawFloorLights(PoseStack pose, MultiBufferSource buffers, Ambiance.FloorLights lights, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		for (int i = 0; i < lights.count; i++) {
			// Each spot runs its own loop over the floor: different radii, speeds and directions.
			double turn = time / 1200.0 * lights.speed * Math.PI * 2 * (i % 2 == 0 ? 1 : -0.7);
			double radius = fieldRadius * (0.25 + 0.6 * ((i * 0.37) % 1.0));
			double angle = Math.PI * 2 * i / lights.count + turn;
			// On the ground itself, wherever it is (2026-11-10): each point of the spot is laid on the block surface
			// below it, so the spot follows hollows, steps and slopes. Well over that surface: at the blocks' own height
			// they flickered with them (2026-10-30).
			Vec3 at = onGround(Math.cos(angle) * radius, Math.sin(angle) * radius);
			int color = Ambiance.color(lights.colors.get(i % lights.colors.size()), 0xFFFFFF);
			int segments = 20;
			Vec3[] rim = new Vec3[segments + 1];
			for (int s = 0; s <= segments; s++) {
				double a = Math.PI * 2 * s / segments;
				rim[s] = onGround(at.x + Math.cos(a) * lights.size, at.z + Math.sin(a) * lights.size);
			}
			for (int s = 0; s < segments; s++) {
				vertex(out, m, at, color, lights.alpha);
				vertex(out, m, at, color, lights.alpha);
				vertex(out, m, rim[s + 1], color, 0);
				vertex(out, m, rim[s], color, 0);
			}
		}
	}

	/** Ground heights by block column, relative to the field's centre, for this frame (cleared each tick). */
	private static final java.util.Map<Long, Double> GROUND = new java.util.HashMap<>();

	/**
	 * A point at {@code (x, z)} from the field's centre, laid on the top of the first solid block below the field's
	 * height (from a little above it, so a step up counts), just over it. No ground within reach: the centre's height.
	 */
	private static Vec3 onGround(double x, double z) {
		return new Vec3(x, groundAt(x, z) + 0.12, z);
	}

	private static double groundAt(double x, double z) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || center == null) {
			return 0;
		}
		int bx = Mth.floor(center.x + x);
		int bz = Mth.floor(center.z + z);
		long key = ((long) bx << 32) ^ (bz & 0xFFFFFFFFL);
		Double cached = GROUND.get(key);
		if (cached != null) {
			return cached;
		}
		double found = 0;
		int top = Mth.floor(center.y) + 4;
		net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
		for (int y = top; y >= top - 16; y--) {
			pos.set(bx, y, bz);
			BlockState state = level.getBlockState(pos);
			var shape = state.getCollisionShape(level, pos);
			if (!shape.isEmpty()) {
				found = y + shape.max(net.minecraft.core.Direction.Axis.Y) - center.y;
				break;
			}
		}
		GROUND.put(key, found);
		return found;
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
		// One buffer at a time: asking for another render type finishes the previous one (without Iris at least).
		RenderType coreType = RenderType.entityCutoutNoCull(ResourceLocation.withDefaultNamespace("textures/block/black_concrete.png"));
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
			VertexConsumer core = buffers.getBuffer(coreType);
			for (int s = 0; s < steps; s++) {
				coreQuad(core, m, left[s], right[s], right[s + 1], left[s + 1]);
			}
			VertexConsumer glow = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
			for (int s = 0; s < steps; s++) {
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
	 * in from below and out above, flickering on its own — light, not cloth (2026-10-30).
	 */
	private static void drawAurora(PoseStack pose, MultiBufferSource buffers, Ambiance.Aurora aurora, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
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
		RenderType solidType = RenderType.entityCutoutNoCull(ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png"));
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
			if (!planet.glow) {
				int[] bands = planet.bands.stream().mapToInt(c -> Ambiance.color(c, 0x9FB8FF)).toArray();
				sphere(buffers.getBuffer(solidType), m, at, planet.size, color, light, false, bands);
			}
			VertexConsumer glow = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
			if (planet.atmosphere != null) {
				// The atmosphere: a soft rim, brighter on the lit side.
				Vec3 toCamera = cameraRel.subtract(at).normalize();
				glowDisc(glow, m, at.add(light.scale(planet.size * 0.12)), toCamera, planet.size * 1.28f,
						Ambiance.color(planet.atmosphere, 0x9FD8FF), 80);
			}
			if (planet.glow) {
				sphere(glow, m, at, planet.size, color, light, true, null);
				// Its halo: a bigger, faint glowing sphere, and a wide soft glow round it.
				sphere(glow, m, at, planet.size * 1.6f, color, light, true, null);
				glowDisc(glow, m, at, cameraRel.subtract(at).normalize(), planet.size * 4f, color, 50);
			}
			if (planet.ring != null) {
				int ringColor = Ambiance.color(planet.ring, 0xD8C8A0);
				int segments = 48;
				for (int s = 0; s < segments; s++) {
					double a0 = Math.PI * 2 * s / segments;
					double a1 = Math.PI * 2 * (s + 1) / segments;
					float inner = planet.size * 1.4f;
					float outer = planet.size * 2.2f;
					Vec3 tilt = new Vec3(0, planet.tilt, 0);
					vertex(glow, m, at.add(Math.cos(a0) * inner, Math.cos(a0) * inner * tilt.y, Math.sin(a0) * inner), ringColor, 120);
					vertex(glow, m, at.add(Math.cos(a0) * outer, Math.cos(a0) * outer * tilt.y, Math.sin(a0) * outer), ringColor, 40);
					vertex(glow, m, at.add(Math.cos(a1) * outer, Math.cos(a1) * outer * tilt.y, Math.sin(a1) * outer), ringColor, 40);
					vertex(glow, m, at.add(Math.cos(a1) * inner, Math.cos(a1) * inner * tilt.y, Math.sin(a1) * inner), ringColor, 120);
				}
			}
		}
	}

	/**
	 * A sphere. Solid ones are lit by {@code light} with a soft terminator and, for a gas giant, coloured by latitude
	 * with {@code bands} blended smoothly per vertex (2026-11-10: a colour per facet on a coarse sphere showed a grid).
	 */
	private static void sphere(VertexConsumer out, Matrix4f m, Vec3 at, float radius, int color, Vec3 light, boolean glowing, int[] bands) {
		int rings = glowing ? 12 : 24;
		int segments = glowing ? 20 : 40;
		for (int r = 0; r < rings; r++) {
			double t0 = Math.PI * r / rings;
			double t1 = Math.PI * (r + 1) / rings;
			for (int s = 0; s < segments; s++) {
				double p0 = Math.PI * 2 * s / segments;
				double p1 = Math.PI * 2 * (s + 1) / segments;
				double[][] corners = { {t0, p0}, {t0, p1}, {t1, p1}, {t1, p0} };
				for (double[] c : corners) {
					Vec3 n = unit(c[0], c[1]);
					Vec3 v = at.add(n.scale(radius));
					if (glowing) {
						vertex(out, m, v, color, 70);
						continue;
					}
					int base = bandColor(bands, color, c[0] / Math.PI);
					float lit = (float) Mth.clamp((n.dot(light) + 0.15) / 1.15, 0, 1);
					float shade = 0.10f + 0.90f * lit * lit * (3 - 2 * lit);
					out.addVertex(m, (float) v.x, (float) v.y, (float) v.z)
							.setColor((int) ((base >> 16 & 255) * shade), (int) ((base >> 8 & 255) * shade), (int) ((base & 255) * shade), 255)
							.setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 1, 0);
				}
			}
		}
	}

	/** The band colour at a latitude ({@code k}: 0 at one pole, 1 at the other), blended between neighbouring bands. */
	private static int bandColor(int[] bands, int color, double k) {
		if (bands == null || bands.length == 0) {
			return color;
		}
		if (bands.length == 1) {
			return bands[0];
		}
		double at = Mth.clamp(k, 0, 1) * (bands.length - 1);
		int low = Math.min(bands.length - 2, (int) at);
		float t = (float) (at - low);
		t = t * t * (3 - 2 * t);
		return lerpColor(bands[low], bands[low + 1], t);
	}

	private static Vec3 unit(double theta, double phi) {
		return new Vec3(Math.sin(theta) * Math.cos(phi), Math.cos(theta), Math.sin(theta) * Math.sin(phi));
	}

	private static void drawStreaks(PoseStack pose, MultiBufferSource buffers, float partialTick) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
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
	 * far end — no hard-edged rectangle seen (2026-10-30).
	 */
	private static void beam(VertexConsumer out, Matrix4f m, Vec3 from, Vec3 to, float startWidth, float endWidth, int color) {
		beam(out, m, from, to, startWidth, endWidth, color, 70);
	}

	/**
	 * A light cone, brightest at {@code from}, fading along it and across it, at most {@code alpha}; it fades out as the
	 * camera comes near it (2026-11-10: a camera inside a beam saw only white).
	 */
	private static void beam(VertexConsumer out, Matrix4f m, Vec3 from, Vec3 to, float startWidth, float endWidth, int color, int alpha) {
		float near = (float) Mth.clamp((distanceToSegment(cameraRel, from, to) - Math.max(startWidth, endWidth) - 1.5) / 6.0, 0, 1);
		int core = Math.round(Mth.clamp(alpha, 0, 255) * near);
		if (core <= 1) {
			return;
		}
		Vec3 axis = to.subtract(from).normalize();
		Vec3 side = axis.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 other = axis.cross(side).normalize();
		int planes = 6;
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

	private static double distanceToSegment(Vec3 point, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double t = Mth.clamp(point.subtract(a).dot(ab) / Math.max(1.0E-6, ab.lengthSqr()), 0, 1);
		return point.distanceTo(a.add(ab.scale(t)));
	}

	/** 0 right at the camera, 1 from a few blocks away: shapes melt away rather than fill the screen. */
	private static float nearFade(Vec3 at, double from, double over) {
		return (float) Mth.clamp((at.distanceTo(cameraRel) - from) / over, 0, 1);
	}

	/** A flat quad lying on the ground, its four corners each on the surface below them. */
	private static void groundQuad(VertexConsumer out, Matrix4f m, double x0, double z0, double x1, double z1, int color, int alpha) {
		vertex(out, m, onGround(x0, z0), color, alpha);
		vertex(out, m, onGround(x1, z0), color, alpha);
		vertex(out, m, onGround(x1, z1), color, alpha);
		vertex(out, m, onGround(x0, z1), color, alpha);
	}

	/** A thin line on the ground, from one point to another, {@code width} wide. */
	private static void groundLine(VertexConsumer out, Matrix4f m, double x0, double z0, double x1, double z1, double width, int color,
			int alpha0, int alpha1) {
		double dx = x1 - x0;
		double dz = z1 - z0;
		double length = Math.sqrt(dx * dx + dz * dz);
		double nx = -dz / length * width / 2;
		double nz = dx / length * width / 2;
		vertex(out, m, onGround(x0 + nx, z0 + nz), color, alpha0);
		vertex(out, m, onGround(x1 + nx, z1 + nz), color, alpha1);
		vertex(out, m, onGround(x1 - nx, z1 - nz), color, alpha1);
		vertex(out, m, onGround(x0 - nx, z0 - nz), color, alpha0);
	}

	/** Disco's floor: tiles on the ground lighting up on the beat, each with its colour and its own rhythm. */
	private static void drawDanceFloor(PoseStack pose, MultiBufferSource buffers, Ambiance.DanceFloor floor, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		float radius = floor.radius > 0 ? floor.radius : fieldRadius * 0.95f;
		int beat = Math.max(1, floor.beat);
		long number = (long) (time / beat);
		float within = (time % beat) / beat;
		int n = (int) Math.ceil(radius / floor.tile);
		double gap = floor.tile * 0.06;
		for (int ix = -n; ix < n; ix++) {
			for (int iz = -n; iz < n; iz++) {
				double x0 = ix * floor.tile;
				double z0 = iz * floor.tile;
				double cx = x0 + floor.tile / 2;
				double cz = z0 + floor.tile / 2;
				double dist = Math.sqrt(cx * cx + cz * cz);
				if (dist > radius) {
					continue;
				}
				int seed = ix * 73856093 ^ iz * 19349663;
				float roll = hashFloat(seed + (int) number * 83492791);
				if (roll > 0.42f) {
					continue; // dark this beat
				}
				int color = Ambiance.color(floor.colors.get(Math.floorMod(seed + (int) number, floor.colors.size())), 0xFFFFFF);
				float edge = (float) Mth.clamp((radius - dist) / 3.0, 0, 1);
				int alpha = Math.round(floor.alpha * (1f - 0.65f * within) * edge);
				groundQuad(out, m, x0 + gap, z0 + gap, x0 + floor.tile - gap, z0 + floor.tile - gap, color, alpha);
			}
		}
	}

	/** Arcade's grid: lines on the ground fading into the distance, a wave of the accent colour running out. */
	private static void drawNeonGrid(PoseStack pose, MultiBufferSource buffers, Ambiance.NeonGrid grid, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		float extent = fieldRadius * grid.extent;
		int lines = (int) Math.ceil(extent / grid.spacing);
		int color = Ambiance.color(grid.color, 0x00FFD5);
		int accent = Ambiance.color(grid.accent, 0xFF00C8);
		float wave = (time / 20f * grid.pulse) % (extent + 6f);
		for (int axis = 0; axis < 2; axis++) {
			for (int l = -lines; l <= lines; l++) {
				double fixed = l * grid.spacing;
				for (double run = -extent; run < extent; run += 1.0) {
					double ax = axis == 0 ? fixed : run;
					double az = axis == 0 ? run : fixed;
					double bx = axis == 0 ? fixed : run + 1;
					double bz = axis == 0 ? run + 1 : fixed;
					double da = Math.sqrt(ax * ax + az * az);
					double db = Math.sqrt(bx * bx + bz * bz);
					if (da > extent && db > extent) {
						continue;
					}
					boolean inWave = Math.abs(da - wave) < 1.6;
					int c = inWave ? accent : color;
					int a0 = Math.round(grid.alpha * (float) Mth.clamp(1 - da / extent, 0, 1) * (inWave ? 1.4f : 1f));
					int a1 = Math.round(grid.alpha * (float) Mth.clamp(1 - db / extent, 0, 1) * (inWave ? 1.4f : 1f));
					groundLine(out, m, ax, az, bx, bz, 0.07, c, Math.min(255, a0), Math.min(255, a1));
				}
			}
		}
	}

	/** The mirror ball's light dots: thrown down from the ball, turning with it, landing on the ground. */
	private static void drawMirrorBallDots(PoseStack pose, MultiBufferSource buffers, Ambiance.MirrorBall ball, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		double turn = time / 1200.0 * ball.speed * Math.PI * 2;
		for (int i = 0; i < ball.dots; i++) {
			double azimuth = i * 2.399963 + turn;
			double drop = Math.toRadians(18 + 62 * hashFloat(i * 7919 + 13));
			double reach = Math.min(fieldRadius * 1.4, ball.height / Math.tan(drop));
			double x = Math.cos(azimuth) * reach;
			double z = Math.sin(azimuth) * reach;
			int color = Ambiance.color(ball.colors.get(i % ball.colors.size()), 0xFFFFFF);
			Vec3 at = onGround(x, z);
			int segments = 10;
			float size = 0.32f + 0.18f * hashFloat(i * 104729);
			Vec3 previous = null;
			for (int sgm = 0; sgm <= segments; sgm++) {
				double a = Math.PI * 2 * sgm / segments;
				Vec3 rim = onGround(x + Math.cos(a) * size, z + Math.sin(a) * size);
				if (previous != null) {
					vertex(out, m, at, color, ball.alpha);
					vertex(out, m, at, color, ball.alpha);
					vertex(out, m, rim, color, 0);
					vertex(out, m, previous, color, 0);
				}
				previous = rim;
			}
		}
		// A soft round glow round the ball (2026-11-10: a flat quad showed as a big square).
		Vec3 centre = new Vec3(0, ball.height, 0);
		glowDisc(out, m, centre, cameraRel.subtract(centre).normalize(), ball.size * 2.4f, 0xFFF4E0, 70);
	}

	/** The sky's distance for nebulae and the galaxy: inside the star sphere, outside everything else. */
	private static float skyRadius() {
		return active.backdrop != null ? Math.max(active.backdrop.radius, fieldRadius + 6f) * 0.88f : 50f;
	}

	/** Nebulae: clusters of soft glowing puffs, mixing two colours, far away on the sky. */
	private static void drawNebulae(PoseStack pose, MultiBufferSource buffers, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		float r = skyRadius();
		int index = 0;
		for (Ambiance.Nebula nebula : active.nebulae) {
			double yaw = Math.toRadians(nebula.yaw) + time / 1200.0 * (active.backdrop != null ? active.backdrop.spin : 0) * Math.PI * 2;
			double pitch = Math.toRadians(nebula.pitch);
			Vec3 dir = new Vec3(Math.cos(pitch) * Math.cos(yaw), Math.sin(pitch), Math.cos(pitch) * Math.sin(yaw));
			Vec3 u = dir.cross(new Vec3(0, 1, 0));
			u = u.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : u.normalize();
			Vec3 v = dir.cross(u).normalize();
			int a = Ambiance.color(nebula.color, 0x7A3DFF);
			int b = Ambiance.color(nebula.accent, 0x3DD0FF);
			for (int p = 0; p < nebula.puffs; p++) {
				int seed = index * 7919 + p * 104729;
				// Puffs gather toward the middle, in a stretched, uneven shape.
				double spread = nebula.size * Math.sqrt(hashFloat(seed));
				double angle = hashFloat(seed + 1) * Math.PI * 2;
				Vec3 at = dir.scale(r).add(u.scale(Math.cos(angle) * spread * 1.5)).add(v.scale(Math.sin(angle) * spread * 0.8));
				float size = nebula.size * (0.25f + 0.45f * hashFloat(seed + 2));
				int color = lerpColor(a, b, hashFloat(seed + 3));
				float breathe = 0.85f + 0.15f * Mth.sin(time / 60f + p);
				glowDisc(out, m, at, dir.scale(-1), size, color, Math.round(nebula.alpha * breathe));
			}
			index++;
		}
	}

	/** The galaxy's band: a soft glowing strip along a great circle, sprinkled with fine stars. */
	private static void drawGalaxy(PoseStack pose, MultiBufferSource buffers, Ambiance.Galaxy galaxy, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		float r = skyRadius() * 1.02f;
		int color = Ambiance.color(galaxy.color, 0xC8D0FF);
		double tilt = Math.toRadians(galaxy.tilt);
		double yaw = Math.toRadians(galaxy.yaw);
		int glows = 60;
		for (int i = 0; i < glows; i++) {
			Vec3 dir = galaxyPoint(i * Math.PI * 2 / glows, 0, tilt, yaw);
			glowDisc(out, m, dir.scale(r), dir.scale(-1), r * 0.11f, color, galaxy.alpha);
		}
		for (int i = 0; i < galaxy.stars; i++) {
			double along = hashFloat(i * 31 + 7) * Math.PI * 2;
			double off = (hashFloat(i * 31 + 8) - 0.5) * 0.22 * (0.4 + hashFloat(i * 31 + 9));
			Vec3 dir = galaxyPoint(along, off, tilt, yaw);
			float twinkle = 0.55f + 0.45f * Mth.sin(time * 0.08f + i);
			quadFacing(out, m, dir.scale(r * 0.99), dir, r / 30f * 0.05f, 0xF0F4FF, Math.round(170 * twinkle));
		}
	}

	/** A point on the galaxy's great circle ({@code along}), {@code off} radians to one side of it. */
	private static Vec3 galaxyPoint(double along, double off, double tilt, double yaw) {
		Vec3 flat = new Vec3(Math.cos(along) * Math.cos(off), Math.sin(off), Math.sin(along) * Math.cos(off));
		// Tilt round the X axis, then turn round the vertical.
		double y = flat.y * Math.cos(tilt) - flat.z * Math.sin(tilt);
		double z = flat.y * Math.sin(tilt) + flat.z * Math.cos(tilt);
		double x = flat.x;
		return new Vec3(x * Math.cos(yaw) - z * Math.sin(yaw), y, x * Math.sin(yaw) + z * Math.cos(yaw));
	}

	private static int lerpColor(int a, int b, float t) {
		int r = (int) Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF);
		int g = (int) Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF);
		int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
		return r << 16 | g << 8 | bl;
	}

	/** Mist: translucent layers just over the ground, their thickness changing in slow drifting patches. */
	private static void drawMist(PoseStack pose, MultiBufferSource buffers, Ambiance.Mist mist, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.TINT);
		Matrix4f m = pose.last().pose();
		float radius = mist.radius > 0 ? mist.radius : fieldRadius * 1.5f;
		int color = Ambiance.color(mist.color, 0xB0C0C8);
		double cell = 2.0;
		int n = (int) Math.ceil(radius / cell);
		double t = time / 20.0 * mist.speed;
		for (int layer = 0; layer < Math.max(1, mist.layers); layer++) {
			double lift = 0.15 + mist.height * layer / Math.max(1, mist.layers);
			for (int ix = -n; ix < n; ix++) {
				for (int iz = -n; iz < n; iz++) {
					double x0 = ix * cell;
					double z0 = iz * cell;
					double[][] corners = { {x0, z0}, {x0 + cell, z0}, {x0 + cell, z0 + cell}, {x0, z0 + cell} };
					for (double[] c : corners) {
						double d = Math.sqrt(c[0] * c[0] + c[1] * c[1]);
						float edge = (float) Mth.clamp((radius - d) / 4.0, 0, 1);
						double patch = 0.5 + 0.25 * Math.sin(c[0] / mist.scale + t + layer * 1.7)
								+ 0.25 * Math.cos(c[1] / mist.scale * 0.8 - t * 0.7 + layer);
						Vec3 at = onGround(c[0], c[1]).add(0, lift, 0);
						int alpha = Math.round(mist.alpha * (float) patch * edge * nearFade(at, 0.5, 2.5));
						vertex(out, m, at, color, alpha);
					}
				}
			}
		}
	}

	/** Corrupted's veins: from each rift's foot (or from round the field), a few branches crawling toward it. */
	private static void buildVeins(Ambiance.Veins veins) {
		if (veins.scatter > 0) {
			// Cracks over the whole floor, each from a random point, heading anywhere.
			for (int r = 0; r < veins.scatter; r++) {
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				double dist = Math.sqrt(RANDOM.nextDouble()) * fieldRadius * 1.4;
				for (int b = 0; b < veins.branches; b++) {
					crawl(veins, Math.cos(angle) * dist, Math.sin(angle) * dist, RANDOM.nextDouble() * Math.PI * 2);
				}
			}
			return;
		}
		List<Double> roots = new ArrayList<>();
		if (!RIFT_ANGLES.isEmpty()) {
			for (float angle : RIFT_ANGLES) {
				roots.add((double) angle);
			}
		} else {
			for (int i = 0; i < 5; i++) {
				roots.add(Math.PI * 2 * i / 5);
			}
		}
		for (double root : roots) {
			for (int b = 0; b < veins.branches; b++) {
				double radius = fieldRadius + 3f;
				crawl(veins, Math.cos(root) * radius, Math.sin(root) * radius, root + Math.PI + (RANDOM.nextDouble() - 0.5) * 1.2);
			}
		}
	}

	/** One vein, crawling from (x, z) roughly toward {@code heading}, bending as it goes. */
	private static void crawl(Ambiance.Veins veins, double x, double z, double heading) {
		double walked = 0;
		while (walked < veins.length) {
			heading += (RANDOM.nextDouble() - 0.5) * 0.9;
			double step = 0.8 + RANDOM.nextDouble() * 0.6;
			double nx = x + Math.cos(heading) * step;
			double nz = z + Math.sin(heading) * step;
			VEINS.add(new double[] {x, z, nx, nz, walked});
			x = nx;
			z = nz;
			walked += step;
		}
	}

	private static void drawVeins(PoseStack pose, MultiBufferSource buffers, Ambiance.Veins veins, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		int color = Ambiance.color(veins.color, 0xFF2A8A);
		int core = veins.core != null ? Ambiance.color(veins.core, 0xFFD060) : -1;
		double t = time / 20.0 * veins.pulse;
		for (double[] v : VEINS) {
			float taper = (float) Mth.clamp(1 - v[4] / veins.length, 0.15, 1);
			float pulse = (float) (0.45 + 0.55 * Math.max(0, Math.sin(v[4] * 0.9 - t * 3)));
			int alpha = Math.round(veins.alpha * taper * pulse);
			groundLine(out, m, v[0], v[1], v[2], v[3], 0.08 + 0.12 * taper, color, alpha, alpha);
			if (core >= 0) {
				groundLine(out, m, v[0], v[1], v[2], v[3], 0.03 + 0.04 * taper, core, Math.round(alpha * 0.9f), Math.round(alpha * 0.9f));
			}
		}
	}

	/** Cracks in the sky: jagged lines on the sky's sphere, a thin hot core in a wide soft glow, pulsing. */
	private static void drawSkyCracks(PoseStack pose, MultiBufferSource buffers, Ambiance.SkyCracks cracks, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		int color = Ambiance.color(cracks.color, 0xFF3070);
		float r = 48f;
		for (int c = 0; c < cracks.count; c++) {
			double yaw = hashFloat(c * 911 + 1) * Math.PI * 2;
			double pitch = Math.toRadians(15 + 55 * hashFloat(c * 911 + 2));
			Vec3 dir = new Vec3(Math.cos(pitch) * Math.cos(yaw), Math.sin(pitch), Math.cos(pitch) * Math.sin(yaw));
			Vec3 u = dir.cross(new Vec3(0, 1, 0)).normalize();
			Vec3 v = dir.cross(u).normalize();
			float pulse = 0.6f + 0.4f * Mth.sin(time / 9f + c * 1.3f);
			double heading = hashFloat(c * 911 + 3) * Math.PI * 2;
			Vec3 at = dir.scale(r);
			int steps = 9;
			for (int s = 0; s < steps; s++) {
				heading += (hashFloat(c * 911 + 10 + s) - 0.5) * 1.6;
				double step = cracks.size / steps;
				Vec3 next = at.add(u.scale(Math.cos(heading) * step)).add(v.scale(Math.sin(heading) * step));
				float width = 0.25f * (1 - s / (float) steps) + 0.05f;
				skyStroke(out, m, at, next, dir, width * 6, color, Math.round(cracks.alpha * 0.25f * pulse));
				skyStroke(out, m, at, next, dir, width, 0xFFF0F4, Math.round(cracks.alpha * pulse));
				at = next;
			}
		}
	}

	/** A stroke on the sky between two points, facing the field. */
	private static void skyStroke(VertexConsumer out, Matrix4f m, Vec3 a, Vec3 b, Vec3 facing, float width, int color, int alpha) {
		Vec3 along = b.subtract(a).normalize();
		Vec3 side = along.cross(facing).normalize().scale(width / 2);
		vertex(out, m, a.add(side), color, alpha);
		vertex(out, m, b.add(side), color, alpha);
		vertex(out, m, b.subtract(side), color, 0);
		vertex(out, m, a.subtract(side), color, 0);
		vertex(out, m, a.subtract(side), color, 0);
		vertex(out, m, b.subtract(side), color, 0);
		vertex(out, m, b.add(side), color, alpha);
		vertex(out, m, a.add(side), color, alpha);
	}

	/**
	 * Corrupted's cubes: each stays a while somewhere, then jumps elsewhere, flickering just before. {@code glow}: the
	 * coloured shell (light pass); otherwise the black body (solid pass).
	 */
	private static void drawGlitchCubes(PoseStack pose, MultiBufferSource buffers, Ambiance.GlitchCubes cubes, boolean glow) {
		VertexConsumer out = buffers.getBuffer(glow ? com.mystaria.showtime.ShowtimeRenderTypes.GLOW : com.mystaria.showtime.ShowtimeRenderTypes.TINT);
		Matrix4f m = pose.last().pose();
		int color = Ambiance.color(cubes.color, 0xFF2A8A);
		int jump = Math.max(10, cubes.jump);
		for (int i = 0; i < cubes.count; i++) {
			int offset = (int) (hashFloat(i * 131 + 7) * jump);
			int epoch = (int) ((ticks + offset) / jump);
			int within = (int) ((ticks + offset) % jump);
			if (within > jump - 6 && within % 2 == 0) {
				continue; // flickering before the jump
			}
			int seed = i * 7919 + epoch * 104729;
			double angle = hashFloat(seed) * Math.PI * 2;
			double radius = Mth.lerp(hashFloat(seed + 1), cubes.radius.get(0), cubes.radius.get(1));
			double height = Mth.lerp(hashFloat(seed + 2), cubes.height.get(0), cubes.height.get(1));
			float size = Mth.lerp(hashFloat(seed + 3), cubes.size.get(0), cubes.size.get(1));
			Vec3 centre = new Vec3(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
			if (nearFade(centre, 1.5, 2) <= 0.05f) {
				continue;
			}
			// A small twitch now and then: a glitch shifting it sideways for a tick.
			int now = (int) ticks;
			if (hashFloat(seed + now) > 0.93f) {
				centre = centre.add(0.3 * (hashFloat(seed + now + 1) - 0.5), 0, 0.3 * (hashFloat(seed + now + 2) - 0.5));
			}
			float s = glow ? size * 0.5f + 0.06f : size * 0.5f;
			cube(out, m, centre, s, glow ? color : 0x0A0410, glow ? 70 : 235);
		}
	}

	private static void cube(VertexConsumer out, Matrix4f m, Vec3 c, float h, int color, int alpha) {
		double x0 = c.x - h, x1 = c.x + h, y0 = c.y - h, y1 = c.y + h, z0 = c.z - h, z1 = c.z + h;
		double[][][] faces = {
				{{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}}, {{x0, y0, z1}, {x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}},
				{{x0, y0, z0}, {x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}}, {{x1, y0, z0}, {x1, y0, z1}, {x1, y1, z1}, {x1, y1, z0}},
				{{x0, y0, z0}, {x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}}, {{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}}};
		for (double[][] face : faces) {
			for (double[] p : face) {
				vertex(out, m, new Vec3(p[0], p[1], p[2]), color, alpha);
			}
		}
	}

	/** Will-o'-wisps: a bright flickering core in a soft halo, wandering slowly round the field. */
	private static void drawWisps(PoseStack pose, MultiBufferSource buffers, Ambiance.Wisps wisps, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		int a = Ambiance.color(wisps.color, 0x5CFFD0);
		int b = Ambiance.color(wisps.accent, 0x3DB8FF);
		for (int i = 0; i < wisps.count; i++) {
			double t = time / 20.0;
			double angle = hashFloat(i * 17 + 1) * Math.PI * 2 + t * 0.08 * (i % 2 == 0 ? 1 : -1) + Math.sin(t * 0.3 + i) * 0.3;
			double radius = Mth.lerp(hashFloat(i * 17 + 2), wisps.radius.get(0), wisps.radius.get(1)) + Math.sin(t * 0.4 + i * 2) * 1.5;
			double height = Mth.lerp(hashFloat(i * 17 + 3), wisps.height.get(0), wisps.height.get(1)) + Math.sin(t * 0.9 + i) * 0.4;
			Vec3 at = onGround(Math.cos(angle) * radius, Math.sin(angle) * radius).add(0, height, 0);
			float fade = nearFade(at, 1.0, 2.0);
			if (fade <= 0.03f) {
				continue;
			}
			float flicker = 0.7f + 0.3f * Mth.sin(time * 0.7f + i * 3) * Mth.sin(time * 0.31f + i);
			if (wisps.blink) {
				// A firefly: lit for a second or two, then dark for a while, each on its own beat.
				float beat = (time / 20f + hashFloat(i * 17 + 5) * 7f) % 4f;
				flicker = beat < 1.6f ? Mth.sin(beat / 1.6f * Mth.PI) : 0f;
				if (flicker <= 0.02f) {
					continue;
				}
			}
			Vec3 toCamera = cameraRel.subtract(at).normalize();
			int color = lerpColor(a, b, hashFloat(i * 17 + 4));
			glowDisc(out, m, at, toCamera, 0.9f * wisps.size, color, Math.round(55 * flicker * fade));
			glowDisc(out, m, at, toCamera, 0.28f * wisps.size, 0xF0FFFA, Math.round(230 * flicker * fade));
		}
	}

	/** 9x9 ghost silhouette: '#' body, '.' empty, 'o' eyes (left empty). */
	private static final String[] GHOST = {"..#####..", ".#######.", "##o###o##", "##o###o##", "#########",
			"#########", "#########", "##.###.##", "#...#...#"};

	/** Ghosts: pale silhouettes far beyond the field, drifting sideways, fading in and out at their own pace. */
	private static void drawGhosts(PoseStack pose, MultiBufferSource buffers, Ambiance.Ghosts ghosts, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.TINT);
		Matrix4f m = pose.last().pose();
		int color = Ambiance.color(ghosts.color, 0xD8F4FF);
		for (int i = 0; i < ghosts.count; i++) {
			double cycle = 300 + 200 * hashFloat(i * 29 + 1);
			double phase = ((time + hashFloat(i * 29 + 2) * cycle) % cycle) / cycle;
			float visible = (float) Math.max(0, Math.sin(phase * Math.PI));
			if (visible < 0.03f) {
				continue;
			}
			double angle = hashFloat(i * 29 + 3) * Math.PI * 2 + phase * 0.5;
			double radius = Mth.lerp(hashFloat(i * 29 + 4), ghosts.radius.get(0), ghosts.radius.get(1));
			Vec3 base = onGround(Math.cos(angle) * radius, Math.sin(angle) * radius)
					.add(0, 1.0 + Math.sin(time / 25.0 + i) * 0.4 + phase * 1.5, 0);
			Vec3 toCamera = cameraRel.subtract(base);
			Vec3 right = new Vec3(toCamera.z, 0, -toCamera.x);
			right = right.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : right.normalize();
			Vec3 up = new Vec3(0, 1, 0);
			double px = ghosts.size / GHOST.length;
			int alpha = Math.round(ghosts.alpha * visible);
			for (int row = 0; row < GHOST.length; row++) {
				// The tail sways: lower rows shift a little sideways.
				double sway = Math.sin(time / 8.0 + row * 0.6 + i) * 0.04 * row;
				for (int col = 0; col < GHOST[row].length(); col++) {
					if (GHOST[row].charAt(col) != '#') {
						continue;
					}
					Vec3 corner = base.add(right.scale((col - 4.5) * px + sway)).add(up.scale((GHOST.length - row) * px));
					vertex(out, m, corner, color, alpha);
					vertex(out, m, corner.add(right.scale(px)), color, alpha);
					vertex(out, m, corner.add(right.scale(px)).add(up.scale(-px)), color, alpha);
					vertex(out, m, corner.add(up.scale(-px)), color, alpha);
				}
			}
		}
	}

	/** Soap bubbles: a thin bright rim, a faint body and a glint, rising and swaying, popping at the top. */
	private static void drawBubbles(PoseStack pose, MultiBufferSource buffers, Ambiance.Bubbles bubbles, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		int color = Ambiance.color(bubbles.color, 0xE8F4FF);
		int[] sheen = { 0xFFB0E0, 0xB0FFE8, 0xB8C8FF };
		for (int i = 0; i < bubbles.count; i++) {
			double cycle = 160 + 120 * hashFloat(i * 41 + 1);
			double phase = ((time + hashFloat(i * 41 + 2) * cycle) % cycle) / cycle;
			int round = (int) ((time + hashFloat(i * 41 + 2) * cycle) / cycle);
			int seed = i * 41 + round * 7307;
			double angle = hashFloat(seed + 3) * Math.PI * 2;
			double radius = Mth.lerp(hashFloat(seed + 4), bubbles.radius.get(0), bubbles.radius.get(1));
			double sway = Math.sin(time / 15.0 + i) * 0.6;
			Vec3 at = onGround(Math.cos(angle) * radius + sway, Math.sin(angle) * radius).add(0, 0.5 + phase * bubbles.height, 0);
			float size = Mth.lerp(hashFloat(seed + 5), bubbles.size.get(0), bubbles.size.get(1));
			// Fades in from the ground, pops (grows a hair and vanishes) at the very top.
			float life = (float) Mth.clamp(Math.min(phase / 0.1, (1 - phase) / 0.04), 0, 1);
			float pop = phase > 0.96 ? 1 + (float) (phase - 0.96) * 6 : 1;
			float fade = life * nearFade(at, 0.5, 2.0);
			if (fade <= 0.03f) {
				continue;
			}
			Vec3 normal = cameraRel.subtract(at).normalize();
			Vec3 u = normal.cross(new Vec3(0, 1, 0)).normalize();
			if (u.lengthSqr() < 1.0E-6) {
				u = new Vec3(1, 0, 0);
			}
			Vec3 v = normal.cross(u).normalize();
			float r = size * pop;
			int segments = 20;
			for (int s = 0; s < segments; s++) {
				double a0 = Math.PI * 2 * s / segments;
				double a1 = Math.PI * 2 * (s + 1) / segments;
				// The rim shimmers in pastel colours round the bubble, as a soap film does.
				int c0 = lerpColor(color, sheen[(s * 3 / segments) % 3], 0.6f);
				Vec3 in0 = at.add(u.scale(Math.cos(a0) * r * 0.86)).add(v.scale(Math.sin(a0) * r * 0.86));
				Vec3 in1 = at.add(u.scale(Math.cos(a1) * r * 0.86)).add(v.scale(Math.sin(a1) * r * 0.86));
				Vec3 out0 = at.add(u.scale(Math.cos(a0) * r)).add(v.scale(Math.sin(a0) * r));
				Vec3 out1 = at.add(u.scale(Math.cos(a1) * r)).add(v.scale(Math.sin(a1) * r));
				int rim = Math.round(120 * fade);
				vertex(out, m, in0, c0, 0);
				vertex(out, m, out0, c0, rim);
				vertex(out, m, out1, c0, rim);
				vertex(out, m, in1, c0, 0);
				// The body: barely there, a little brighter toward the rim.
				vertex(out, m, at, color, 0);
				vertex(out, m, at, color, 0);
				vertex(out, m, in1, color, Math.round(18 * fade));
				vertex(out, m, in0, color, Math.round(18 * fade));
			}
			Vec3 glint = at.add(u.scale(-r * 0.45)).add(v.scale(r * 0.45));
			glowDisc(out, m, glint, normal, r * 0.22f, 0xFFFFFF, Math.round(200 * fade));
		}
	}

	/**
	 * Mushrooms in clumps round the field: a stem and a domed cap, softly lit from within (solid pass), and a glow on the
	 * ground and round the cap that breathes slowly (light pass).
	 */
	private static void drawMushrooms(PoseStack pose, MultiBufferSource buffers, Ambiance.Mushrooms mushrooms, float time,
			boolean glow) {
		VertexConsumer out = buffers.getBuffer(glow ? com.mystaria.showtime.ShowtimeRenderTypes.GLOW : com.mystaria.showtime.ShowtimeRenderTypes.TINT);
		Matrix4f m = pose.last().pose();
		for (int c = 0; c < mushrooms.clumps; c++) {
			double angle = Math.PI * 2 * (c + hashFloat(c * 53 + 1) * 0.6) / mushrooms.clumps;
			double ring = fieldRadius + mushrooms.ring + hashFloat(c * 53 + 2) * 2;
			int color = Ambiance.color(mushrooms.colors.get(c % Math.max(1, mushrooms.colors.size())), 0xFF9AD8);
			int many = 1 + (int) (hashFloat(c * 53 + 3) * 3);
			for (int k = 0; k < many; k++) {
				double x = Math.cos(angle) * ring + (hashFloat(c * 53 + 10 + k) - 0.5) * 1.2;
				double z = Math.sin(angle) * ring + (hashFloat(c * 53 + 20 + k) - 0.5) * 1.2;
				float size = mushrooms.size * (0.6f + 0.7f * hashFloat(c * 53 + 30 + k));
				Vec3 foot = onGround(x, z);
				if (nearFade(foot, 0.5, 1.5) <= 0.05f) {
					continue;
				}
				float breathe = 0.7f + 0.3f * Mth.sin(time / 30f + c + k);
				Vec3 cap = foot.add(0, size * 1.1, 0);
				if (glow) {
					glowDisc(out, m, foot.add(0, 0.03, 0), new Vec3(0, 1, 0), size * 2.2f, color, Math.round(60 * breathe));
					glowDisc(out, m, cap, cameraRel.subtract(cap).normalize(), size * 1.6f, color, Math.round(45 * breathe));
				} else {
					mushroom(out, m, foot, size, color);
				}
			}
		}
	}

	/**
	 * One mushroom. TINT writes no depth, so the parts go back to front as seen from above (where the camera nearly always
	 * is): the cap's underside, then the stem, then the dome.
	 */
	private static void mushroom(VertexConsumer out, Matrix4f m, Vec3 foot, float size, int color) {
		int sides = 10;
		double stem = size * 0.16;
		double stemTop = size * 1.1;
		double cap = size * 0.62;
		int stemColor = 0xF4ECE0;
		int under = lerpColor(color, 0x302030, 0.55f);
		double[] rings = { 1.0, 0.85, 0.55, 0.0 };
		double[] lift = { 0.0, 0.28, 0.5, 0.6 };
		for (int part = 0; part < 3; part++) {
			for (int s = 0; s < sides; s++) {
				double a0 = Math.PI * 2 * s / sides;
				double a1 = Math.PI * 2 * (s + 1) / sides;
				if (part == 0) {
					Vec3 u0 = foot.add(Math.cos(a0) * cap, stemTop, Math.sin(a0) * cap);
					Vec3 u1 = foot.add(Math.cos(a1) * cap, stemTop, Math.sin(a1) * cap);
					Vec3 mid = foot.add(0, stemTop - size * 0.05, 0);
					vertex(out, m, u0, under, 255);
					vertex(out, m, mid, under, 255);
					vertex(out, m, mid, under, 255);
					vertex(out, m, u1, under, 255);
				} else if (part == 1) {
					Vec3 b0 = foot.add(Math.cos(a0) * stem, 0, Math.sin(a0) * stem);
					Vec3 b1 = foot.add(Math.cos(a1) * stem, 0, Math.sin(a1) * stem);
					vertex(out, m, b0, stemColor, 255);
					vertex(out, m, b1, stemColor, 255);
					vertex(out, m, b1.add(0, stemTop, 0), stemColor, 255);
					vertex(out, m, b0.add(0, stemTop, 0), stemColor, 255);
				} else {
					// A dome in three rings, lighter toward the top.
					for (int r = 0; r < rings.length - 1; r++) {
						int lo = lerpColor(color, 0xFFFFFF, r * 0.18f);
						int hi = lerpColor(color, 0xFFFFFF, (r + 1) * 0.18f);
						Vec3 c0 = foot.add(Math.cos(a0) * cap * rings[r], stemTop + size * lift[r], Math.sin(a0) * cap * rings[r]);
						Vec3 c1 = foot.add(Math.cos(a1) * cap * rings[r], stemTop + size * lift[r], Math.sin(a1) * cap * rings[r]);
						Vec3 d0 = foot.add(Math.cos(a0) * cap * rings[r + 1], stemTop + size * lift[r + 1], Math.sin(a0) * cap * rings[r + 1]);
						Vec3 d1 = foot.add(Math.cos(a1) * cap * rings[r + 1], stemTop + size * lift[r + 1], Math.sin(a1) * cap * rings[r + 1]);
						vertex(out, m, c0, lo, 255);
						vertex(out, m, c1, lo, 255);
						vertex(out, m, d1, hi, 255);
						vertex(out, m, d0, hi, 255);
					}
				}
			}
		}
	}

	/** Old film, over the screen: the light breathing, now and then a scratch running down, a speck of dust. */
	private static void drawFilm(GuiGraphics graphics, Ambiance.Film film) {
		int w = graphics.guiWidth();
		int h = graphics.guiHeight();
		float breathe = calm() ? 0f : film.flicker * (0.5f + 0.5f * hashFloat((int) ticks * 31 + 7));
		int dark = Math.round(255 * Mth.clamp(breathe, 0, 1));
		if (dark > 0) {
			graphics.fill(0, 0, w, h, dark << 24);
		}
		if (scratchLeft > 0) {
			int x = Math.round(scratchX * w);
			int light = (scratchLeft % 3 == 0) ? 0x50FFFFFF : 0x38FFFFFF;
			graphics.fill(x, 0, x + 1, h, light);
			// Scratches are not straight: a short kink partway down.
			int kink = Math.round(h * (0.3f + 0.4f * hashFloat(Math.round(scratchX * 1000))));
			graphics.fill(x + 1, kink, x + 2, Math.min(h, kink + h / 6), light);
		}
		if (dustLeft > 0) {
			int x = Math.round(dustX * w);
			int y = Math.round(dustY * h);
			int s = 1 + Math.round(hashFloat(Math.round(dustX * 997)) * 2);
			graphics.fill(x, y, x + s + 1, y + s, 0xB0101010);
			graphics.fill(x + s, y + 1, x + s + 2, y + s + 1, 0x80101010);
		}
	}

	/** One of the ambiance's occasional sounds, from a random side of the camera, not too close. */
	private static void playAmbient(Minecraft mc, Ambiance.AmbientSound ambient) {
		if (ambient.id == null) {
			return;
		}
		Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
		double angle = RANDOM.nextDouble() * Math.PI * 2;
		float pitch = ambient.pitch.isEmpty() ? 1f : between(ambient.pitch);
		mc.getSoundManager().play(new net.minecraft.client.resources.sounds.SimpleSoundInstance(
				SoundEvent.createVariableRangeEvent(ResourceLocation.parse(ambient.id)), SoundSource.AMBIENT, ambient.volume,
				pitch, RandomSource.create(RANDOM.nextLong()), camera.x + Math.cos(angle) * 4, camera.y + 1,
				camera.z + Math.sin(angle) * 4));
	}

	/** A real firework burst at {@code where} (relative to the field), its colours from the ambiance's fireworks. */
	private static void burst(ClientLevel level, Vec3 where) {
		List<String> names = active != null && active.fireworks != null ? active.fireworks.colors
				: List.of("#ff6a8a", "#ffd060", "#7ad8ff", "#ffffff");
		it.unimi.dsi.fastutil.ints.IntArrayList colors = new it.unimi.dsi.fastutil.ints.IntArrayList();
		for (int i = 0; i < 2 && !names.isEmpty(); i++) {
			colors.add(Ambiance.color(names.get(RANDOM.nextInt(names.size())), 0xFFFFFF));
		}
		net.minecraft.world.item.component.FireworkExplosion.Shape[] shapes = {
				net.minecraft.world.item.component.FireworkExplosion.Shape.LARGE_BALL,
				net.minecraft.world.item.component.FireworkExplosion.Shape.SMALL_BALL,
				net.minecraft.world.item.component.FireworkExplosion.Shape.STAR,
				net.minecraft.world.item.component.FireworkExplosion.Shape.BURST };
		net.minecraft.world.item.component.FireworkExplosion explosion = new net.minecraft.world.item.component.FireworkExplosion(
				shapes[RANDOM.nextInt(shapes.length)], colors, it.unimi.dsi.fastutil.ints.IntList.of(0xFFF4E0), true, RANDOM.nextBoolean());
		Vec3 at = center.add(where);
		level.createFireworks(at.x, at.y, at.z, 0, 0, 0, List.of(explosion));
	}

	/** Embers (or plankton): small glows rising and swaying, their colour moving from {@code color} to {@code accent}. */
	private static void drawEmbers(PoseStack pose, MultiBufferSource buffers, Ambiance.Embers embers, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		int young = Ambiance.color(embers.color, 0xFFC040);
		int old = Ambiance.color(embers.accent, 0xFF3010);
		double seconds = embers.height / Math.max(0.05f, embers.rise);
		for (int i = 0; i < embers.count; i++) {
			double cycle = seconds * 20 * (0.7 + 0.6 * hashFloat(i * 61 + 1));
			double clock = time + hashFloat(i * 61 + 2) * cycle;
			double phase = (clock % cycle) / cycle;
			int round = (int) (clock / cycle);
			int seed = i * 61 + round * 4099;
			double angle = hashFloat(seed + 3) * Math.PI * 2;
			double radius = Mth.lerp(hashFloat(seed + 4), embers.radius.get(0), embers.radius.get(1));
			double swirl = Math.sin(time / 12.0 + i) * 0.5 * phase;
			Vec3 at = onGround(Math.cos(angle) * radius + swirl, Math.sin(angle) * radius + Math.cos(time / 14.0 + i) * 0.5 * phase)
					.add(0, 0.2 + phase * embers.height, 0);
			float life = (float) Mth.clamp(Math.min(phase / 0.08, (1 - phase) / 0.35), 0, 1);
			float twinkle = 0.75f + 0.25f * Mth.sin(time * 0.9f + i * 2.3f);
			float fade = life * twinkle * nearFade(at, 0.4, 1.5);
			if (fade <= 0.03f) {
				continue;
			}
			Vec3 toCamera = cameraRel.subtract(at).normalize();
			int color = lerpColor(young, old, (float) phase);
			glowDisc(out, m, at, toCamera, embers.size * 3.5f, color, Math.round(70 * fade));
			glowDisc(out, m, at, toCamera, embers.size, lerpColor(color, 0xFFFFFF, 0.5f), Math.round(230 * fade));
		}
	}

	/**
	 * The volcano: far off at {@code yaw}, a dark cone rising over the horizon (solid pass); its crater's glow, two lava
	 * streams down its flanks, a plume of light and a red glow along the horizon (light pass).
	 */
	private static void drawVolcano(PoseStack pose, MultiBufferSource buffers, Ambiance.Volcano volcano, float time, boolean glow) {
		VertexConsumer out = buffers.getBuffer(glow ? com.mystaria.showtime.ShowtimeRenderTypes.GLOW : com.mystaria.showtime.ShowtimeRenderTypes.TINT);
		Matrix4f m = pose.last().pose();
		double yaw = Math.toRadians(volcano.yaw);
		Vec3 dir = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		Vec3 facing = dir.scale(-1);
		float s = volcano.size;
		Vec3 base = dir.scale(skyRadius()).add(0, -s * 0.25, 0);
		Vec3 crater = base.add(0, s, 0);
		int color = Ambiance.color(volcano.color, 0xFF5A10);
		float pulse = 0.8f + 0.2f * Mth.sin(time / 17f);
		if (!glow) {
			int rock = 0x1A0806;
			int ridge = 0x2A0E08;
			// The cone: a wide base narrowing to the crater's lips.
			Vec3 l0 = base.add(side.scale(-s * 1.6));
			Vec3 r0 = base.add(side.scale(s * 1.6));
			Vec3 l1 = crater.add(side.scale(-s * 0.32));
			Vec3 r1 = crater.add(side.scale(s * 0.32));
			vertex(out, m, l0, rock, 255);
			vertex(out, m, r0, rock, 255);
			vertex(out, m, r1, ridge, 255);
			vertex(out, m, l1, ridge, 255);
			return;
		}
		glowDisc(out, m, base.add(0, s * 0.1, 0), facing, s * 3.2f, color, Math.round(55 * pulse));
		glowDisc(out, m, crater, facing, s * 0.55f, lerpColor(color, 0xFFD060, 0.4f), Math.round(170 * pulse));
		glowDisc(out, m, crater.add(0, s * 0.7, 0), facing, s * 1.1f, color, Math.round(45 * pulse));
		// Lava streams: from the crater's lips down each flank, wavering a little.
		for (int k = -1; k <= 1; k += 2) {
			Vec3 at = crater.add(side.scale(k * s * 0.22)).add(dir.scale(-0.5));
			for (int step = 0; step < 6; step++) {
				Vec3 next = at.add(side.scale(k * s * (0.14 + 0.05 * Math.sin(step * 1.7 + k)))).add(0, -s * 0.13, 0);
				skyStroke(out, m, at, next, facing, s * 0.05f, 0xFFB040, Math.round(200 * pulse * (1 - step / 7f)));
				skyStroke(out, m, at, next, facing, s * 0.16f, color, Math.round(60 * pulse * (1 - step / 7f)));
				at = next;
			}
		}
	}

	/** Caustics: a web of bright wavering lines on the floor, from two drifting interference patterns. */
	private static void drawCaustics(PoseStack pose, MultiBufferSource buffers, Ambiance.Caustics caustics, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		int color = Ambiance.color(caustics.color, 0xA8F4FF);
		float radius = Math.min(caustics.radius > 0 ? caustics.radius : fieldRadius * 1.35f, 22f);
		double cell = 0.6;
		int n = (int) Math.ceil(radius / cell);
		double t = time / 20.0 * caustics.speed;
		double s = caustics.scale;
		float[][] light = new float[2 * n + 1][2 * n + 1];
		for (int ix = -n; ix <= n; ix++) {
			for (int iz = -n; iz <= n; iz++) {
				double x = ix * cell;
				double z = iz * cell;
				double v1 = Math.sin(x * 0.9 * s + t * 1.1) + Math.sin(z * 1.1 * s - t * 0.9)
						+ Math.sin((x * 0.6 - z * 0.8) * 1.3 * s + t * 0.7);
				double v2 = Math.sin((x * 0.7 + z * 0.5) * 1.7 * s - t * 1.3) + Math.sin(z * 0.5 * s + x * 0.3 * s + t * 0.5);
				// Exponent 3 (2026-11-10): at 6 the lines were thinner than a cell and vanished between the samples.
				double c1 = Math.pow(1 - Math.abs(Math.sin(v1 * 1.1)), 3);
				double c2 = Math.pow(1 - Math.abs(Math.sin(v2 * 1.3 + 0.7)), 3);
				double d = Math.sqrt(x * x + z * z);
				float edge = (float) Mth.clamp((radius - d) / 3.0, 0, 1);
				light[ix + n][iz + n] = (float) Math.max(c1, c2 * 0.8) * edge;
			}
		}
		for (int ix = 0; ix < 2 * n; ix++) {
			for (int iz = 0; iz < 2 * n; iz++) {
				float a = light[ix][iz];
				float b = light[ix + 1][iz];
				float c = light[ix + 1][iz + 1];
				float d = light[ix][iz + 1];
				if (a + b + c + d < 0.08f) {
					continue; // most of the floor is between the lines
				}
				double x0 = (ix - n) * cell;
				double z0 = (iz - n) * cell;
				vertex(out, m, onGround(x0, z0), color, Math.round(caustics.alpha * a));
				vertex(out, m, onGround(x0 + cell, z0), color, Math.round(caustics.alpha * b));
				vertex(out, m, onGround(x0 + cell, z0 + cell), color, Math.round(caustics.alpha * c));
				vertex(out, m, onGround(x0, z0 + cell), color, Math.round(caustics.alpha * d));
			}
		}
	}

	/** Shafts of light from above, all leaning the same way, swaying and breathing. */
	private static void drawShafts(PoseStack pose, MultiBufferSource buffers, Ambiance.Shafts shafts, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		int color = Ambiance.color(shafts.color, 0xBFF4FF);
		double lean = Math.tan(Math.toRadians(shafts.tilt)) * shafts.height;
		for (int i = 0; i < shafts.count; i++) {
			double angle = hashFloat(i * 73 + 1) * Math.PI * 2;
			double radius = Mth.lerp(hashFloat(i * 73 + 2), shafts.ring.get(0), shafts.ring.get(1));
			double sway = Math.sin(time / 50.0 + i * 1.9) * 1.6;
			Vec3 foot = onGround(Math.cos(angle) * radius + sway, Math.sin(angle) * radius);
			Vec3 top = foot.add(lean, shafts.height, lean * 0.3);
			float breathe = 0.6f + 0.4f * Mth.sin(time / 37f + i * 2.1f);
			beam(out, m, top, foot, shafts.width * 0.7f, shafts.width * 1.3f, color, Math.round(shafts.alpha * breathe));
		}
	}

	/** Kelp: ribbons rooted beyond the field, swaying more toward their tips, darker at the root. */
	private static void drawKelp(PoseStack pose, MultiBufferSource buffers, Ambiance.Kelp kelp, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.TINT);
		Matrix4f m = pose.last().pose();
		int color = Ambiance.color(kelp.color, 0x1F6A48);
		int root = lerpColor(color, 0x000000, 0.45f);
		int tip = lerpColor(color, 0x90E0A0, 0.35f);
		int segments = 10;
		for (int i = 0; i < kelp.count; i++) {
			double angle = Math.PI * 2 * (i + hashFloat(i * 89 + 1) * 0.7) / kelp.count;
			double radius = fieldRadius + Mth.lerp(hashFloat(i * 89 + 2), kelp.ring.get(0), kelp.ring.get(1));
			float height = Mth.lerp(hashFloat(i * 89 + 3), kelp.height.get(0), kelp.height.get(1));
			Vec3 foot = onGround(Math.cos(angle) * radius, Math.sin(angle) * radius);
			if (nearFade(foot.add(0, height / 2, 0), 1.5, 2) <= 0.05f) {
				continue;
			}
			Vec3 across = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
			Vec3 prev = foot;
			for (int s = 1; s <= segments; s++) {
				float f = s / (float) segments;
				double bend = Math.sin(time / 22.0 + i * 1.3 + f * 2.2) * 1.2 * f * f;
				Vec3 next = foot.add(across.scale(bend)).add(0, height * f, 0);
				float w0 = 0.32f * (1 - (s - 1) / (float) segments * 0.6f);
				float w1 = 0.32f * (1 - f * 0.6f);
				int c0 = lerpColor(root, tip, (s - 1) / (float) segments);
				int c1 = lerpColor(root, tip, f);
				// Flat ribbon facing the field's centre, with a slight twist so it never vanishes edge-on.
				Vec3 width0 = across.scale(w0).add(0, 0, 0);
				Vec3 width1 = across.scale(w1);
				vertex(out, m, prev.subtract(width0), c0, 235);
				vertex(out, m, prev.add(width0), c0, 235);
				vertex(out, m, next.add(width1), c1, 235);
				vertex(out, m, next.subtract(width1), c1, 235);
				prev = next;
			}
		}
	}

	/**
	 * Paper lanterns rising: an eight-sided body, its frame darker at top and bottom (solid pass), and the warm glow of
	 * its flame round it (light pass). Each fades in near the ground and out high in the sky.
	 */
	private static void drawLanterns(PoseStack pose, MultiBufferSource buffers, Ambiance.Lanterns lanterns, float time, boolean glow) {
		VertexConsumer out = buffers.getBuffer(glow ? com.mystaria.showtime.ShowtimeRenderTypes.GLOW : com.mystaria.showtime.ShowtimeRenderTypes.TINT);
		Matrix4f m = pose.last().pose();
		double seconds = lanterns.height / Math.max(0.05f, lanterns.rise);
		for (int i = 0; i < lanterns.count; i++) {
			double cycle = seconds * 20 * (0.8 + 0.4 * hashFloat(i * 97 + 1));
			double clock = time + hashFloat(i * 97 + 2) * cycle;
			double phase = (clock % cycle) / cycle;
			int round = (int) (clock / cycle);
			int seed = i * 97 + round * 3571;
			double angle = hashFloat(seed + 3) * Math.PI * 2;
			double radius = Mth.lerp(hashFloat(seed + 4), lanterns.radius.get(0), lanterns.radius.get(1));
			double drift = phase * 4;
			Vec3 at = onGround(Math.cos(angle) * radius + drift, Math.sin(angle) * radius + drift * 0.4)
					.add(Math.sin(time / 30.0 + i) * 0.3, 1.5 + phase * lanterns.height, 0);
			float life = (float) Mth.clamp(Math.min(phase / 0.06, (1 - phase) / 0.25), 0, 1);
			float fade = life * nearFade(at, 0.8, 2);
			if (fade <= 0.03f) {
				continue;
			}
			int color = Ambiance.color(lanterns.colors.get(i % Math.max(1, lanterns.colors.size())), 0xFFB060);
			float flame = 0.85f + 0.15f * Mth.sin(time * 0.6f + i * 1.7f);
			float s = lanterns.size;
			if (glow) {
				Vec3 toCamera = cameraRel.subtract(at).normalize();
				glowDisc(out, m, at, toCamera, s * 3.2f, color, Math.round(70 * fade * flame));
				glowDisc(out, m, at, toCamera, s * 1.1f, lerpColor(color, 0xFFF4D0, 0.5f), Math.round(120 * fade * flame));
				continue;
			}
			int alpha = Math.round(235 * fade);
			int body = lerpColor(color, 0xFFF0C0, 0.35f * flame);
			int frame = lerpColor(color, 0x401808, 0.6f);
			int sides = 8;
			double r = s * 0.5;
			double h = s * 1.2;
			for (int k = 0; k < sides; k++) {
				double a0 = Math.PI * 2 * k / sides;
				double a1 = Math.PI * 2 * (k + 1) / sides;
				// Slightly wider in the middle: three bands, the middle one brightest.
				double[] ys = { -h / 2, -h / 6, h / 6, h / 2 };
				double[] rs = { r * 0.82, r, r, r * 0.86 };
				int[] cs = { frame, body, body, frame };
				for (int b = 0; b < 3; b++) {
					int lo = b == 0 ? frame : body;
					int hi = b == 2 ? frame : body;
					vertex(out, m, at.add(Math.cos(a0) * rs[b], ys[b], Math.sin(a0) * rs[b]), lo, alpha);
					vertex(out, m, at.add(Math.cos(a1) * rs[b], ys[b], Math.sin(a1) * rs[b]), lo, alpha);
					vertex(out, m, at.add(Math.cos(a1) * rs[b + 1], ys[b + 1], Math.sin(a1) * rs[b + 1]), hi, alpha);
					vertex(out, m, at.add(Math.cos(a0) * rs[b + 1], ys[b + 1], Math.sin(a0) * rs[b + 1]), hi, alpha);
				}
				// The top, closed.
				vertex(out, m, at.add(0, h / 2, 0), frame, alpha);
				vertex(out, m, at.add(0, h / 2, 0), frame, alpha);
				vertex(out, m, at.add(Math.cos(a1) * rs[3], h / 2, Math.sin(a1) * rs[3]), cs[3], alpha);
				vertex(out, m, at.add(Math.cos(a0) * rs[3], h / 2, Math.sin(a0) * rs[3]), cs[3], alpha);
			}
		}
	}

	/** Glints: four-pointed stars flashing for a moment here and there in the air, each at its own beat. */
	private static void drawGlints(PoseStack pose, MultiBufferSource buffers, Ambiance.Glints glints, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		int color = Ambiance.color(glints.color, 0xE8F8FF);
		for (int i = 0; i < glints.count; i++) {
			double cycle = 40 + 50 * hashFloat(i * 19 + 1);
			double clock = time + hashFloat(i * 19 + 2) * cycle;
			double phase = (clock % cycle) / cycle;
			if (phase > 0.25) {
				continue; // dark most of the time: a glint is brief
			}
			int seed = i * 19 + (int) (clock / cycle) * 6007;
			double angle = hashFloat(seed + 3) * Math.PI * 2;
			double radius = Mth.lerp(hashFloat(seed + 4), glints.radius.get(0), glints.radius.get(1));
			Vec3 at = onGround(Math.cos(angle) * radius, Math.sin(angle) * radius)
					.add(0, Mth.lerp(hashFloat(seed + 5), glints.height.get(0), glints.height.get(1)), 0);
			float flash = (float) Math.sin(phase / 0.25 * Math.PI);
			float fade = flash * nearFade(at, 0.5, 1.5);
			if (fade <= 0.03f) {
				continue;
			}
			Vec3 normal = cameraRel.subtract(at).normalize();
			Vec3 u = normal.cross(new Vec3(0, 1, 0));
			u = u.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : u.normalize();
			Vec3 v = normal.cross(u).normalize();
			float s = glints.size * (0.6f + 0.4f * flash);
			int alpha = Math.round(230 * fade);
			// Two thin diamonds crossing: the star's four points.
			for (Vec3[] axes : new Vec3[][] { {u, v}, {v, u} }) {
				Vec3 along = axes[0].scale(s);
				Vec3 across = axes[1].scale(s * 0.08);
				vertex(out, m, at.add(along), color, 0);
				vertex(out, m, at.add(across), color, alpha);
				vertex(out, m, at.subtract(along), color, 0);
				vertex(out, m, at.subtract(across), color, alpha);
			}
			glowDisc(out, m, at, normal, s * 0.6f, color, Math.round(90 * fade));
		}
	}

	/**
	 * The fog wall: a ring of mist round the field, thickest at the bottom, its thickness drifting round. It thins away
	 * when the camera comes near it or goes past it, so it never stands between the camera and the battle.
	 */
	private static void drawFogWall(PoseStack pose, MultiBufferSource buffers, Ambiance.FogWall wall, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.TINT);
		Matrix4f m = pose.last().pose();
		int color = Ambiance.color(wall.color, 0xE4EEF8);
		float radius = wall.radius > 0 ? wall.radius : fieldRadius + 8f;
		double camera = Math.sqrt(cameraRel.x * cameraRel.x + cameraRel.z * cameraRel.z);
		float open = (float) Mth.clamp((radius - camera - 3) / 4, 0, 1);
		if (open <= 0.02f) {
			return;
		}
		int segments = 64;
		double[] rows = { 0, 0.15, 0.4, 0.7, 1.0 };
		float[] thick = { 1f, 0.9f, 0.6f, 0.25f, 0f };
		double t = time / 20.0 * wall.speed;
		for (int s = 0; s < segments; s++) {
			double a0 = Math.PI * 2 * s / segments;
			double a1 = Math.PI * 2 * (s + 1) / segments;
			float d0 = (float) (0.65 + 0.35 * Math.sin(a0 * 5 + t * 0.6) * Math.sin(a0 * 3 - t * 0.4));
			float d1 = (float) (0.65 + 0.35 * Math.sin(a1 * 5 + t * 0.6) * Math.sin(a1 * 3 - t * 0.4));
			Vec3 g0 = onGround(Math.cos(a0) * radius, Math.sin(a0) * radius).add(0, -1, 0);
			Vec3 g1 = onGround(Math.cos(a1) * radius, Math.sin(a1) * radius).add(0, -1, 0);
			for (int r = 0; r < rows.length - 1; r++) {
				int lo0 = Math.round(wall.alpha * thick[r] * d0 * open);
				int lo1 = Math.round(wall.alpha * thick[r] * d1 * open);
				int hi0 = Math.round(wall.alpha * thick[r + 1] * d0 * open);
				int hi1 = Math.round(wall.alpha * thick[r + 1] * d1 * open);
				vertex(out, m, g0.add(0, wall.height * rows[r], 0), color, lo0);
				vertex(out, m, g1.add(0, wall.height * rows[r], 0), color, lo1);
				vertex(out, m, g1.add(0, wall.height * rows[r + 1], 0), color, hi1);
				vertex(out, m, g0.add(0, wall.height * rows[r + 1], 0), color, hi0);
			}
		}
	}

	/** The mirage: a band of pale "water" all round the horizon, shimmering, fading above and below. */
	private static void drawMirage(PoseStack pose, MultiBufferSource buffers, Ambiance.Mirage mirage, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		int color = Ambiance.color(mirage.color, 0xBFE0FF);
		float r = skyRadius();
		int segments = 96;
		for (int s = 0; s < segments; s++) {
			double a0 = Math.PI * 2 * s / segments;
			double a1 = Math.PI * 2 * (s + 1) / segments;
			float w0 = (float) (0.6 + 0.4 * Math.sin(a0 * 23 + time / 6.0) * Math.sin(a0 * 7 - time / 11.0));
			float w1 = (float) (0.6 + 0.4 * Math.sin(a1 * 23 + time / 6.0) * Math.sin(a1 * 7 - time / 11.0));
			Vec3 p0 = new Vec3(Math.cos(a0) * r, 0, Math.sin(a0) * r);
			Vec3 p1 = new Vec3(Math.cos(a1) * r, 0, Math.sin(a1) * r);
			double h = mirage.height;
			int mid0 = Math.round(mirage.alpha * w0);
			int mid1 = Math.round(mirage.alpha * w1);
			vertex(out, m, p0.add(0, -h * 0.3, 0), color, 0);
			vertex(out, m, p1.add(0, -h * 0.3, 0), color, 0);
			vertex(out, m, p1.add(0, h * 0.3, 0), color, mid1);
			vertex(out, m, p0.add(0, h * 0.3, 0), color, mid0);
			vertex(out, m, p0.add(0, h * 0.3, 0), color, mid0);
			vertex(out, m, p1.add(0, h * 0.3, 0), color, mid1);
			vertex(out, m, p1.add(0, h, 0), color, 0);
			vertex(out, m, p0.add(0, h, 0), color, 0);
		}
	}

	/** Birds: dark V shapes circling high up, their wings beating now and then, gliding the rest of the time. */
	private static void drawBirds(PoseStack pose, MultiBufferSource buffers, Ambiance.Birds birds, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.TINT);
		Matrix4f m = pose.last().pose();
		int color = Ambiance.color(birds.color, 0x2A2018);
		for (int i = 0; i < birds.count; i++) {
			double radius = Mth.lerp(hashFloat(i * 23 + 1), birds.radius.get(0), birds.radius.get(1));
			double height = Mth.lerp(hashFloat(i * 23 + 2), birds.height.get(0), birds.height.get(1));
			double angle = hashFloat(i * 23 + 3) * Math.PI * 2 + time / 20.0 * (3.0 / radius);
			Vec3 at = new Vec3(Math.cos(angle) * radius, height + Math.sin(time / 40.0 + i) * 1.5, Math.sin(angle) * radius);
			Vec3 heading = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
			Vec3 side = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			// Beating for a second every few, gliding with wings slightly raised otherwise.
			double beat = (time / 20.0 + i * 1.7) % 4.0;
			double lift = beat < 1.0 ? Math.sin(beat * Math.PI * 4) * 0.45 : 0.15;
			float s = birds.size;
			for (int k = -1; k <= 1; k += 2) {
				Vec3 tip = at.add(side.scale(k * s)).add(0, lift * s, 0);
				Vec3 elbow = at.add(side.scale(k * s * 0.45)).add(0, (lift * 0.5 + 0.12) * s, 0);
				Vec3 back = heading.scale(-0.12 * s);
				vertex(out, m, at.add(heading.scale(0.1 * s)), color, 240);
				vertex(out, m, elbow, color, 240);
				vertex(out, m, tip, color, 240);
				vertex(out, m, at.add(back), color, 240);
			}
		}
	}

	/**
	 * The 80s sun: horizontal slices of a disc, yellow at the top melting into pink, the lower half cut by bands that
	 * widen toward the horizon (solid pass); a warm glow round it (light pass).
	 */
	private static void drawStripedSun(PoseStack pose, MultiBufferSource buffers, Ambiance.StripedSun sun, boolean glow) {
		VertexConsumer out = buffers.getBuffer(glow ? com.mystaria.showtime.ShowtimeRenderTypes.GLOW : com.mystaria.showtime.ShowtimeRenderTypes.TINT);
		Matrix4f m = pose.last().pose();
		double yaw = Math.toRadians(sun.yaw);
		Vec3 dir = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		float s = sun.size;
		Vec3 centre = dir.scale(skyRadius()).add(0, s * 0.45, 0);
		int top = Ambiance.color(sun.top, 0xFFE14A);
		int bottom = Ambiance.color(sun.bottom, 0xFF3A8A);
		if (glow) {
			glowDisc(out, m, centre, dir.scale(-1), s * 2.4f, lerpColor(top, bottom, 0.6f), 70);
			return;
		}
		int slices = 48;
		for (int k = 0; k < slices; k++) {
			double y0 = s - 2.0 * s * k / slices;
			double y1 = s - 2.0 * s * (k + 1) / slices;
			// Below the middle, gaps: each band a little wider than the one above.
			if (y1 < 0) {
				double depth = -y1 / s;
				int band = (int) (depth * sun.stripes);
				double within = depth * sun.stripes - band;
				if (within < 0.15 + 0.5 * depth) {
					continue;
				}
			}
			double w0 = Math.sqrt(Math.max(0, s * s - y0 * y0));
			double w1 = Math.sqrt(Math.max(0, s * s - y1 * y1));
			int c0 = lerpColor(top, bottom, (float) ((s - y0) / (2 * s)));
			int c1 = lerpColor(top, bottom, (float) ((s - y1) / (2 * s)));
			vertex(out, m, centre.add(side.scale(-w0)).add(0, y0, 0), c0, 255);
			vertex(out, m, centre.add(side.scale(w0)).add(0, y0, 0), c0, 255);
			vertex(out, m, centre.add(side.scale(w1)).add(0, y1, 0), c1, 255);
			vertex(out, m, centre.add(side.scale(-w1)).add(0, y1, 0), c1, 255);
		}
	}

	/** Palm silhouettes: a curved trunk tapering up, a crown of drooping fronds. */
	private static void drawPalms(PoseStack pose, MultiBufferSource buffers, Ambiance.Palms palms, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.TINT);
		Matrix4f m = pose.last().pose();
		int color = Ambiance.color(palms.color, 0x1A0628);
		for (int i = 0; i < palms.count; i++) {
			double angle = Math.PI * 2 * (i + hashFloat(i * 31 + 1) * 0.6) / palms.count;
			double radius = fieldRadius + Mth.lerp(hashFloat(i * 31 + 2), palms.ring.get(0), palms.ring.get(1));
			float height = Mth.lerp(hashFloat(i * 31 + 3), palms.height.get(0), palms.height.get(1));
			Vec3 foot = onGround(Math.cos(angle) * radius, Math.sin(angle) * radius);
			if (nearFade(foot.add(0, height / 2, 0), 2, 2) <= 0.05f) {
				continue;
			}
			double lean = (hashFloat(i * 31 + 4) - 0.5) * 2;
			Vec3 bendDir = new Vec3(-Math.sin(angle), 0, Math.cos(angle)).scale(lean);
			Vec3 toCamera = cameraRel.subtract(foot);
			Vec3 facing = new Vec3(toCamera.z, 0, -toCamera.x);
			facing = facing.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : facing.normalize();
			// Trunk: a strip facing the camera, bending as it rises.
			Vec3 prev = foot;
			int segments = 8;
			for (int k = 1; k <= segments; k++) {
				double f = k / (double) segments;
				Vec3 next = foot.add(bendDir.scale(f * f * 1.6)).add(0, height * f, 0);
				double w0 = 0.22 * (1 - (k - 1) / (double) segments * 0.5);
				double w1 = 0.22 * (1 - f * 0.5);
				vertex(out, m, prev.add(facing.scale(-w0)), color, 255);
				vertex(out, m, prev.add(facing.scale(w0)), color, 255);
				vertex(out, m, next.add(facing.scale(w1)), color, 255);
				vertex(out, m, next.add(facing.scale(-w1)), color, 255);
				prev = next;
			}
			// Fronds: arcs out and down from the top, swaying a little; tilted so they read from any side.
			Vec3 crown = prev;
			int fronds = 7;
			for (int f = 0; f < fronds; f++) {
				double a = Math.PI * 2 * f / fronds + hashFloat(i * 31 + 10 + f) * 0.5;
				Vec3 out1 = new Vec3(Math.cos(a), 0, Math.sin(a));
				Vec3 wide = out1.cross(new Vec3(0, 1, 0)).normalize().add(0, 0.6, 0).normalize();
				double sway = Math.sin(time / 30.0 + i + f) * 0.15;
				Vec3 p = crown;
				int steps = 5;
				for (int k = 1; k <= steps; k++) {
					double g = k / (double) steps;
					Vec3 q = crown.add(out1.scale(2.6 * g)).add(0, 0.7 * g - 2.2 * g * g + sway * g, 0);
					double w0 = 0.42 * Math.sin(Math.PI * (k - 1) / steps + 0.25);
					double w1 = 0.42 * Math.sin(Math.PI * g + 0.25) * (k == steps ? 0.2 : 1);
					vertex(out, m, p.add(wide.scale(-w0)), color, 255);
					vertex(out, m, p.add(wide.scale(w0)), color, 255);
					vertex(out, m, q.add(wide.scale(w1)), color, 255);
					vertex(out, m, q.add(wide.scale(-w1)), color, 255);
					p = q;
				}
			}
		}
	}

	// ---------------------------------------------------------------- Ultra Space (2026-11-10)

	/** Where scenery is lit from: up, a little to one side, so solid shapes read in three dimensions. */
	private static final Vec3 SUN_SIDE = new Vec3(0.45, 0.75, 0.35).normalize();

	/** A face's colour, lit by how much it turns toward {@link #SUN_SIDE}. */
	private static int shade(int color, Vec3 normal, float ambient) {
		double light = Math.max(0, normal.dot(SUN_SIDE));
		return lerpColor(0x000000, color, (float) (ambient + (1 - ambient) * light));
	}

	private static Vec3 wormholePlace(Ambiance.Wormhole wormhole) {
		double yaw = Math.toRadians(wormhole.yaw);
		double pitch = Math.toRadians(wormhole.pitch);
		return new Vec3(Math.cos(pitch) * Math.cos(yaw), Math.sin(pitch), Math.cos(pitch) * Math.sin(yaw)).scale(skyRadius());
	}

	/**
	 * A wormhole facing the field (or the camera, for a moment's). In three layers, so it reads as depth: 0 the light
	 * behind (a halo, spiral arms of light winding in, colour shifting toward the middle), 1 the dark core, 2 what is in
	 * front (a bright rim round the core, motes of light being drawn in along the arms).
	 */
	private static void drawWormhole(PoseStack pose, MultiBufferSource buffers, Vec3 at, Ambiance.Wormhole wormhole, float size,
			float time, int layer) {
		drawWormhole(pose, buffers, at, at.normalize().scale(-1).add(0, 0.25, 0).normalize(), wormhole, size, time, layer);
	}

	private static void drawWormhole(PoseStack pose, MultiBufferSource buffers, Vec3 at, Vec3 normal, Ambiance.Wormhole wormhole,
			float size, float time, int layer) {
		if (size <= 0.01f) {
			return;
		}
		VertexConsumer out = buffers.getBuffer(layer == 1 ? com.mystaria.showtime.ShowtimeRenderTypes.TINT : com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		Vec3 u = normal.cross(new Vec3(0, 1, 0));
		u = u.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : u.normalize();
		Vec3 v = normal.cross(u).normalize();
		int color = Ambiance.color(wormhole.color, 0x3DF0FF);
		int accent = Ambiance.color(wormhole.accent, 0xFF4FD8);
		double turn = time / 1200.0 * wormhole.spin * Math.PI * 2;
		double twist = Math.PI * 2.4 * Math.signum(wormhole.spin == 0 ? 1 : wormhole.spin);
		int arms = Math.max(1, wormhole.arms);
		if (layer == 0) {
			glowDisc(out, m, at, normal, size * 1.9f, color, 38);
			glowDisc(out, m, at, normal, size * 0.5f, accent, 90);
			int steps = 28;
			for (int a = 0; a < arms; a++) {
				double base = Math.PI * 2 * a / arms + turn;
				for (int s = 0; s < steps; s++) {
					double t0 = s / (double) steps;
					double t1 = (s + 1) / (double) steps;
					double th0 = base + t0 * twist;
					double th1 = base + t1 * twist;
					double r0 = size * (1 - 0.74 * t0);
					double r1 = size * (1 - 0.74 * t1);
					Vec3 radial0 = u.scale(Math.cos(th0)).add(v.scale(Math.sin(th0)));
					Vec3 radial1 = u.scale(Math.cos(th1)).add(v.scale(Math.sin(th1)));
					Vec3 p0 = at.add(radial0.scale(r0));
					Vec3 p1 = at.add(radial1.scale(r1));
					Vec3 w0 = radial0.scale(size * 0.2 * (1 - t0 * 0.6));
					Vec3 w1 = radial1.scale(size * 0.2 * (1 - t1 * 0.6));
					int c0 = lerpColor(color, accent, (float) t0);
					int c1 = lerpColor(color, accent, (float) t1);
					// 2026-11-10: at 22..142 the arms barely showed; only the core and rim read.
					int a0 = (int) Math.round(60 + 140 * t0);
					int a1 = (int) Math.round(60 + 140 * t1);
					for (int half = -1; half <= 1; half += 2) {
						vertex(out, m, p0, c0, a0);
						vertex(out, m, p0.add(w0.scale(half)), c0, 0);
						vertex(out, m, p1.add(w1.scale(half)), c1, 0);
						vertex(out, m, p1, c1, a1);
					}
				}
			}
			return;
		}
		int segments = 40;
		if (layer == 1) {
			// The core: black at the heart, a deep violet toward its edge.
			for (int i = 0; i < segments; i++) {
				double a0 = Math.PI * 2 * i / segments;
				double a1 = Math.PI * 2 * (i + 1) / segments;
				double r = size * 0.27;
				vertex(out, m, at, 0x05020F, 255);
				vertex(out, m, at, 0x05020F, 255);
				vertex(out, m, at.add(u.scale(Math.cos(a1) * r)).add(v.scale(Math.sin(a1) * r)), 0x1A0838, 255);
				vertex(out, m, at.add(u.scale(Math.cos(a0) * r)).add(v.scale(Math.sin(a0) * r)), 0x1A0838, 255);
			}
			return;
		}
		Vec3 front = normal.scale(0.05);
		float flicker = 0.85f + 0.15f * Mth.sin(time / 5f);
		for (int i = 0; i < segments; i++) {
			double a0 = Math.PI * 2 * i / segments;
			double a1 = Math.PI * 2 * (i + 1) / segments;
			double[] radii = { 0.22, 0.27, 0.34 };
			int[] alphas = { 0, Math.round(235 * flicker), 0 };
			int rim = lerpColor(0xFFFFFF, color, 0.35f);
			for (int k = 0; k < 2; k++) {
				Vec3 i0 = at.add(front).add(u.scale(Math.cos(a0) * size * radii[k])).add(v.scale(Math.sin(a0) * size * radii[k]));
				Vec3 i1 = at.add(front).add(u.scale(Math.cos(a1) * size * radii[k])).add(v.scale(Math.sin(a1) * size * radii[k]));
				Vec3 o0 = at.add(front).add(u.scale(Math.cos(a0) * size * radii[k + 1])).add(v.scale(Math.sin(a0) * size * radii[k + 1]));
				Vec3 o1 = at.add(front).add(u.scale(Math.cos(a1) * size * radii[k + 1])).add(v.scale(Math.sin(a1) * size * radii[k + 1]));
				vertex(out, m, i0, rim, alphas[k]);
				vertex(out, m, o0, rim, alphas[k + 1]);
				vertex(out, m, o1, rim, alphas[k + 1]);
				vertex(out, m, i1, rim, alphas[k]);
			}
		}
		// Motes drawn in along the arms, speeding up as they near the core.
		for (int i = 0; i < 18; i++) {
			double t = ((time / 140.0) + i / 18.0) % 1.0;
			t = t * t;
			double th = Math.PI * 2 * (i % arms) / arms + turn + t * twist + 0.15;
			double r = size * (1 - 0.72 * t);
			Vec3 p = at.add(front).add(u.scale(Math.cos(th) * r)).add(v.scale(Math.sin(th) * r));
			glowDisc(out, m, p, normal, size * 0.05f, lerpColor(0xFFFFFF, accent, (float) t), (int) Math.round(200 * Math.sin(t * Math.PI)));
		}
	}

	/** A moment's wormhole: tears open over the field facing the camera, holds, then snaps shut. */
	private static void drawWormholeBurst(PoseStack pose, MultiBufferSource buffers, float time, float partialTick, boolean glow) {
		float age = time - wormholeBurst;
		if (age > 50) {
			wormholeBurst = -1;
			return;
		}
		float open = age < 10 ? 1 - (float) Math.pow(1 - age / 10f, 3) : age > 38 ? Math.max(0, 1 - (age - 38) / 12f) : 1f;
		Ambiance.Wormhole wormhole = active.wormholes.get(0);
		Vec3 at = new Vec3(0, 7.5, 0);
		Vec3 normal = cameraRel.subtract(at).normalize();
		float size = 3.4f * open;
		if (glow) {
			drawWormhole(pose, buffers, at, normal, wormhole, size, time * 4, 0);
			drawWormhole(pose, buffers, at, normal, wormhole, size, time * 4, 2);
		} else {
			drawWormhole(pose, buffers, at, normal, wormhole, size, time * 4, 1);
		}
	}

	/** One floating island's crystals: {base, tip} pairs and whether each takes the accent colour. */
	private static List<Vec3[]> islandCrystals(int island, float size) {
		List<Vec3[]> crystals = new ArrayList<>();
		int count = 2 + (int) (hashFloat(island * 211 + 1) * 3);
		for (int c = 0; c < count; c++) {
			double angle = hashFloat(island * 211 + 10 + c) * Math.PI * 2;
			double out = size * 0.55 * hashFloat(island * 211 + 20 + c);
			Vec3 base = new Vec3(Math.cos(angle) * out, 0, Math.sin(angle) * out);
			double height = size * (0.5 + 0.6 * hashFloat(island * 211 + 30 + c));
			Vec3 lean = new Vec3(Math.cos(angle), 0, Math.sin(angle)).scale(height * 0.3 * (out / Math.max(0.1, size * 0.55)));
			crystals.add(new Vec3[] {base, base.add(lean).add(0, height, 0)});
		}
		return crystals;
	}

	/**
	 * A crystal (2026-11-10: four plain sides read as low-poly): six sides turned at random, swelling a little to a
	 * shoulder, then a faceted point; dark at the root, clearer up its length, nearly white at the tip, each face lit
	 * by its own angle.
	 */
	private static void crystal(VertexConsumer out, Matrix4f m, Vec3 base, Vec3 tip, float width, int color, double turn) {
		Vec3[] frame = crystalFrame(base, tip);
		Vec3 axis = tip.subtract(base);
		Vec3 shoulder = base.add(axis.scale(0.72));
		int sides = 6;
		int root = lerpColor(color, 0x000000, 0.45f);
		int body = lerpColor(color, 0xFFFFFF, 0.1f);
		int point = lerpColor(color, 0xFFFFFF, 0.55f);
		for (int k = 0; k < sides; k++) {
			double a0 = turn + Math.PI * 2 * k / sides;
			double a1 = turn + Math.PI * 2 * (k + 1) / sides;
			Vec3 d0 = frame[0].scale(Math.cos(a0)).add(frame[1].scale(Math.sin(a0)));
			Vec3 d1 = frame[0].scale(Math.cos(a1)).add(frame[1].scale(Math.sin(a1)));
			Vec3 faceNormal = d0.add(d1).normalize();
			float light = (float) (0.5 + 0.5 * Math.max(0, faceNormal.dot(SUN_SIDE)));
			// Every other face a touch clearer: facets catching the light differently.
			float facet = k % 2 == 0 ? 1f : 0.86f;
			int lo = lerpColor(0x000000, root, light * facet);
			int mid = lerpColor(0x000000, body, light * facet);
			int hi = lerpColor(0x000000, point, Math.min(1f, light * facet + 0.2f));
			vertex(out, m, base.add(d0.scale(width)), lo, 255);
			vertex(out, m, base.add(d1.scale(width)), lo, 255);
			vertex(out, m, shoulder.add(d1.scale(width * 1.08)), mid, 255);
			vertex(out, m, shoulder.add(d0.scale(width * 1.08)), mid, 255);
			vertex(out, m, shoulder.add(d0.scale(width * 1.08)), mid, 255);
			vertex(out, m, shoulder.add(d1.scale(width * 1.08)), mid, 255);
			vertex(out, m, tip, hi, 255);
			vertex(out, m, tip, hi, 255);
		}
	}

	/** Two directions across a crystal's length. */
	private static Vec3[] crystalFrame(Vec3 base, Vec3 tip) {
		Vec3 axis = tip.subtract(base);
		Vec3 a = axis.cross(new Vec3(0, 1, 0));
		a = a.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : a.normalize();
		Vec3 b = axis.cross(a).normalize();
		return new Vec3[] {a, b};
	}

	/** A crystal's light: two edges catching it as bright lines, a soft glow round its point. */
	private static void crystalGlint(VertexConsumer out, Matrix4f m, Vec3 base, Vec3 tip, float width, int color, float strength,
			double turn) {
		Vec3[] frame = crystalFrame(base, tip);
		Vec3 axis = tip.subtract(base);
		Vec3 shoulder = base.add(axis.scale(0.72));
		int edge = lerpColor(color, 0xFFFFFF, 0.6f);
		for (int k : new int[] {0, 2}) {
			double a = turn + Math.PI * 2 * k / 6;
			Vec3 d = frame[0].scale(Math.cos(a)).add(frame[1].scale(Math.sin(a)));
			Vec3 from = base.add(d.scale(width * 1.02)).add(axis.scale(0.15));
			Vec3 to = shoulder.add(d.scale(width * 1.1));
			Vec3 side = axis.cross(cameraRel.subtract(from));
			side = side.lengthSqr() < 1.0E-8 ? frame[1].scale(0.03) : side.normalize().scale(width * 0.12);
			int alpha = Math.round(110 * strength);
			vertex(out, m, from.subtract(side), edge, 0);
			vertex(out, m, from.add(side), edge, 0);
			vertex(out, m, to.add(side), edge, alpha);
			vertex(out, m, to.subtract(side), edge, alpha);
			vertex(out, m, to.subtract(side), edge, alpha);
			vertex(out, m, to.add(side), edge, alpha);
			vertex(out, m, tip.add(side.scale(0.3)), edge, alpha);
			vertex(out, m, tip.subtract(side.scale(0.3)), edge, alpha);
		}
		glowDisc(out, m, tip, cameraRel.subtract(tip).normalize(), (float) axis.length() * 0.35f, color, Math.round(60 * strength));
	}

	/**
	 * Islands floating in the void: a rocky top with a lip, rock tapering to a jagged point below, crystals growing on
	 * top (solid pass, writing depth); the crystals' glow (light pass). They bob and turn slowly.
	 */
	private static void drawIslands(PoseStack pose, MultiBufferSource buffers, Ambiance.Islands islands, float time, boolean glow) {
		VertexConsumer out = buffers.getBuffer(glow ? com.mystaria.showtime.ShowtimeRenderTypes.GLOW : com.mystaria.showtime.ShowtimeRenderTypes.OPAQUE);
		Matrix4f m = pose.last().pose();
		int rock = Ambiance.color(islands.rock, 0x2A1F4A);
		int top = Ambiance.color(islands.top, 0x5A3F9A);
		int crystal = Ambiance.color(islands.crystal, 0x7FF8FF);
		int accent = Ambiance.color(islands.accent, 0xFF7AE8);
		for (int i = 0; i < islands.count; i++) {
			double angle = Math.PI * 2 * (i + hashFloat(i * 97 + 1) * 0.6) / islands.count;
			double distance = Mth.lerp(hashFloat(i * 97 + 2), islands.distance.get(0), islands.distance.get(1));
			double height = Mth.lerp(hashFloat(i * 97 + 3), islands.height.get(0), islands.height.get(1)) + Math.sin(time / 70.0 + i) * 0.6;
			float size = Mth.lerp(hashFloat(i * 97 + 4), islands.size.get(0), islands.size.get(1));
			Vec3 c = new Vec3(Math.cos(angle) * distance, height, Math.sin(angle) * distance);
			double spin = time / 1200.0 * 0.3 * Math.PI * 2 * (i % 2 == 0 ? 1 : -1) + hashFloat(i * 97 + 5) * 6;
			List<Vec3[]> crystals = islandCrystals(i, size);
			if (glow) {
				float pulse = 0.75f + 0.25f * Mth.sin(time / 25f + i);
				for (int k = 0; k < crystals.size(); k++) {
					Vec3 base = c.add(rotateY(crystals.get(k)[0], spin)).add(0, -0.1, 0);
					Vec3 tip = c.add(rotateY(crystals.get(k)[1], spin));
					crystalGlint(out, m, base, tip, size * 0.12f * crystalWidth(i, k), k % 2 == 0 ? crystal : accent, pulse, spin + k);
				}
				Vec3 under = c.add(0, -size * 1.9, 0);
				glowDisc(out, m, under, cameraRel.subtract(under).normalize(), size * 0.9f, accent, 30);
				continue;
			}
			int sides = 9;
			Vec3[][] rings = new Vec3[4][sides];
			double[] ys = { 0, -0.25, -0.9, 0 };
			for (int k = 0; k < sides; k++) {
				double a = Math.PI * 2 * k / sides + spin;
				double r = size * (0.75 + 0.25 * hashFloat(i * 97 + 40 + k));
				Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
				rings[0][k] = c.add(dir.scale(r));
				rings[1][k] = c.add(dir.scale(r * 1.05)).add(0, ys[1] * size, 0);
				rings[2][k] = c.add(dir.scale(r * (0.5 + 0.2 * hashFloat(i * 97 + 60 + k)))).add(0, ys[2] * size * (0.9 + 0.3 * hashFloat(i * 97 + 70 + k)), 0);
			}
			Vec3 tip = c.add((hashFloat(i * 97 + 80) - 0.5) * size * 0.6, -size * (1.7 + 0.5 * hashFloat(i * 97 + 81)), (hashFloat(i * 97 + 82) - 0.5) * size * 0.6);
			Vec3 crown = c.add(0, size * 0.08, 0);
			for (int k = 0; k < sides; k++) {
				int n = (k + 1) % sides;
				// The top: a low dome, its sectors a little different from each other.
				int topColor = lerpColor(top, 0xFFFFFF, 0.08f * hashFloat(i * 97 + 90 + k));
				vertex(out, m, crown, topColor, 255);
				vertex(out, m, crown, topColor, 255);
				vertex(out, m, rings[0][n], topColor, 255);
				vertex(out, m, rings[0][k], topColor, 255);
				Vec3 outward = rings[1][k].add(rings[1][n]).scale(0.5).subtract(c);
				outward = new Vec3(outward.x, 0, outward.z).normalize();
				int lip = shade(lerpColor(top, rock, 0.45f), outward.add(0, 0.2, 0).normalize(), 0.5f);
				quad(out, m, rings[0][k], rings[0][n], rings[1][n], rings[1][k], lip);
				int side = shade(rock, outward.add(0, -0.3, 0).normalize(), 0.45f);
				quad(out, m, rings[1][k], rings[1][n], rings[2][n], rings[2][k], side);
				int deep = shade(lerpColor(rock, 0x000000, 0.35f), outward.add(0, -0.7, 0).normalize(), 0.45f);
				quad(out, m, rings[2][k], rings[2][n], tip, tip, deep);
			}
			for (int k = 0; k < crystals.size(); k++) {
				Vec3 base = c.add(rotateY(crystals.get(k)[0], spin)).add(0, -0.1, 0);
				Vec3 end = c.add(rotateY(crystals.get(k)[1], spin));
				crystal(out, m, base, end, size * 0.12f * crystalWidth(i, k), k % 2 == 0 ? crystal : accent, spin + k);
			}
		}
	}

	/** Crystals of one cluster are not all alike: some slender, some stout. */
	private static float crystalWidth(int group, int k) {
		return 0.7f + 0.6f * hashFloat(group * 389 + k * 41 + 7);
	}

	private static Vec3 rotateY(Vec3 p, double angle) {
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		return new Vec3(p.x * cos - p.z * sin, p.y, p.x * sin + p.z * cos);
	}

	private static void quad(VertexConsumer out, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
		vertex(out, m, a, color, 255);
		vertex(out, m, b, color, 255);
		vertex(out, m, c, color, 255);
		vertex(out, m, d, color, 255);
	}

	/** The field island's outline: a little irregular, so it reads as rock, not as a disc. */
	private static double islandEdge(Ambiance.Island island, double angle) {
		double r = fieldRadius + island.margin;
		return r * (0.93 + 0.05 * Math.sin(3 * angle + 1) * Math.cos(5 * angle) + 0.02 * Math.sin(11 * angle));
	}

	/**
	 * The field's island. Solid pass: its top laid on the real ground (hidden but still there: people stand on it), in
	 * tiles of slightly different shades, then its rock hanging below in rings to a point, crystals round its rim and
	 * hanging under it. Light pass: hexagons across the top, a wave of light running out from the middle and a few
	 * cells sparkling, a glowing rim, the crystals' glow.
	 */
	private static void drawFieldIsland(PoseStack pose, MultiBufferSource buffers, Ambiance.Island island, float time, boolean glow) {
		VertexConsumer out = buffers.getBuffer(glow ? com.mystaria.showtime.ShowtimeRenderTypes.GLOW : com.mystaria.showtime.ShowtimeRenderTypes.OPAQUE);
		Matrix4f m = pose.last().pose();
		int top = Ambiance.color(island.top, 0x241A52);
		int line = Ambiance.color(island.line, 0x7FF8FF);
		int rock = Ambiance.color(island.rock, 0x1A1238);
		int crystal = Ambiance.color(island.crystal, 0x7FF8FF);
		int accent = Ambiance.color(island.accent, 0xFF7AE8);
		float depth = island.depth > 0 ? island.depth : Math.max(10f, fieldRadius * 1.1f);
		int sectors = 64;
		double reach = fieldRadius + island.margin;
		int rings = (int) Math.ceil(reach);
		Vec3 sink = new Vec3(0, -0.03, 0);
		if (!glow) {
			for (int j = 0; j < rings; j++) {
				for (int k = 0; k < sectors; k++) {
					double a0 = Math.PI * 2 * k / sectors;
					double a1 = Math.PI * 2 * (k + 1) / sectors;
					double f0 = j / (double) rings;
					double f1 = (j + 1) / (double) rings;
					double e0 = islandEdge(island, a0);
					double e1 = islandEdge(island, a1);
					Vec3 p00 = onGround(Math.cos(a0) * e0 * f0, Math.sin(a0) * e0 * f0).add(sink);
					Vec3 p01 = onGround(Math.cos(a1) * e1 * f0, Math.sin(a1) * e1 * f0).add(sink);
					Vec3 p11 = onGround(Math.cos(a1) * e1 * f1, Math.sin(a1) * e1 * f1).add(sink);
					Vec3 p10 = onGround(Math.cos(a0) * e0 * f1, Math.sin(a0) * e0 * f1).add(sink);
					int tile = lerpColor(top, 0x4A3A9A, 0.3f * hashFloat(j * 131 + k * 7 + 3));
					quad(out, m, p00, p01, p11, p10, tile);
				}
			}
			// The rock below: the rim on the ground, a lip, then narrower and narrower rings to a point.
			Vec3[][] ring = new Vec3[4][sectors];
			for (int k = 0; k < sectors; k++) {
				double a = Math.PI * 2 * k / sectors;
				double e = islandEdge(island, a);
				Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
				ring[0][k] = onGround(dir.x * e, dir.z * e).add(sink);
				ring[1][k] = dir.scale(e * 1.02).add(0, ring[0][k].y - 0.7, 0);
				ring[2][k] = dir.scale(e * (0.72 + 0.1 * hashFloat(k * 17 + 1))).add(0, -depth * (0.35 + 0.08 * hashFloat(k * 17 + 2)), 0);
				ring[3][k] = dir.scale(e * (0.32 + 0.12 * hashFloat(k * 17 + 3))).add(0, -depth * (0.72 + 0.1 * hashFloat(k * 17 + 4)), 0);
			}
			Vec3 point = new Vec3(0, -depth * 1.05, 0);
			for (int k = 0; k < sectors; k++) {
				int n = (k + 1) % sectors;
				Vec3 outward = new Vec3(Math.cos(Math.PI * 2 * (k + 0.5) / sectors), 0, Math.sin(Math.PI * 2 * (k + 0.5) / sectors));
				quad(out, m, ring[0][k], ring[0][n], ring[1][n], ring[1][k], shade(lerpColor(top, rock, 0.5f), outward.add(0, 0.1, 0).normalize(), 0.5f));
				quad(out, m, ring[1][k], ring[1][n], ring[2][n], ring[2][k], shade(rock, outward.add(0, -0.4, 0).normalize(), 0.42f));
				quad(out, m, ring[2][k], ring[2][n], ring[3][n], ring[3][k], shade(lerpColor(rock, 0x000000, 0.2f), outward.add(0, -0.8, 0).normalize(), 0.42f));
				quad(out, m, ring[3][k], ring[3][n], point, point, shade(lerpColor(rock, 0x000000, 0.4f), outward.add(0, -1.2, 0).normalize(), 0.42f));
			}
		}
		// Crystals round the rim, leaning outward, and hanging under the rock.
		for (int c = 0; c < island.crystals; c++) {
			double a = Math.PI * 2 * (c + hashFloat(c * 59 + 1) * 0.5) / Math.max(1, island.crystals);
			double e = islandEdge(island, a) * 0.96;
			Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
			Vec3 foot = onGround(dir.x * e, dir.z * e).add(0, -0.15, 0);
			int many = 1 + (int) (hashFloat(c * 59 + 2) * 3);
			for (int k = 0; k < many; k++) {
				double h = 1.0 + 1.6 * hashFloat(c * 59 + 10 + k);
				Vec3 side = new Vec3(-dir.z, 0, dir.x).scale((k - (many - 1) / 2.0) * 0.45);
				Vec3 base = foot.add(side);
				Vec3 tip = base.add(dir.scale(h * (0.25 + 0.2 * k))).add(0, h, 0);
				if (nearFade(base.add(0, h / 2, 0), 1.0, 1.5) <= 0.05f) {
					continue;
				}
				int color = (c + k) % 3 == 0 ? accent : crystal;
				float width = (float) (0.12 + 0.06 * h) * crystalWidth(c, k);
				if (glow) {
					float pulse = 0.7f + 0.3f * Mth.sin(time / 20f + c * 1.7f + k);
					crystalGlint(out, m, base, tip, width, color, pulse, c * 7 + k);
				} else {
					crystal(out, m, base, tip, width, color, c * 7 + k);
				}
			}
			// One hanging under the island for every other rim crystal.
			if (c % 2 == 0) {
				Vec3 hang = dir.scale(e * 0.62).add(0, -depth * 0.36, 0);
				Vec3 end = hang.add(0, -depth * (0.18 + 0.15 * hashFloat(c * 59 + 20)), 0);
				if (glow) {
					crystalGlint(out, m, hang, end, 0.35f, c % 4 == 0 ? accent : crystal, 1f, c);
				} else {
					crystal(out, m, hang, end, 0.35f, c % 4 == 0 ? accent : crystal, c);
				}
			}
		}
		if (!glow) {
			return;
		}
		// Hexagons over the top: faint outlines everywhere, cells filling with light as the wave passes.
		double h = Math.max(0.6, island.hex);
		double t = time / 20.0 * island.pulse;
		int span = (int) Math.ceil(reach / h) + 1;
		for (int q = -span; q <= span; q++) {
			for (int r = -span; r <= span; r++) {
				double x = h * Math.sqrt(3) * (q + r / 2.0);
				double z = h * 1.5 * r;
				double d = Math.sqrt(x * x + z * z);
				double edge = islandEdge(island, Math.atan2(z, x));
				if (d > edge - h) {
					continue;
				}
				float fade = (float) Mth.clamp((edge - h - d) / 3.0, 0, 1);
				double wave = Math.pow(Math.max(0, Math.sin(d * 0.55 - t * 2.2)), 4);
				double sparkle = hashFloat(q * 7919 + r * 104729 + (int) (t * 0.8) * 31) > 0.95 ? 0.8 : 0;
				float lit = (float) Math.max(wave, sparkle);
				Vec3[] corners = new Vec3[6];
				for (int k = 0; k < 6; k++) {
					double a = Math.toRadians(60 * k - 30);
					corners[k] = onGround(x + Math.cos(a) * h * 0.96, z + Math.sin(a) * h * 0.96).add(0, 0.01, 0);
				}
				for (int k = 0; k < 3; k++) {
					Vec3 a = corners[k];
					Vec3 b = corners[k + 1];
					groundLine(out, m, a.x, a.z, b.x, b.z, 0.05, line, Math.round((26 + 90 * lit) * fade), Math.round((26 + 90 * lit) * fade));
				}
				if (lit > 0.05f) {
					Vec3 middle = onGround(x, z).add(0, 0.01, 0);
					int alpha = Math.round(55 * lit * fade);
					int cell = lerpColor(line, accent, (float) hashFloat(q * 31 + r * 17));
					for (int k = 0; k < 6; k++) {
						vertex(out, m, middle, cell, alpha);
						vertex(out, m, middle, cell, alpha);
						vertex(out, m, corners[(k + 1) % 6], cell, 0);
						vertex(out, m, corners[k], cell, 0);
					}
				}
			}
		}
		// The rim glowing softly all round.
		for (int k = 0; k < sectors; k++) {
			double a0 = Math.PI * 2 * k / sectors;
			double a1 = Math.PI * 2 * (k + 1) / sectors;
			double e0 = islandEdge(island, a0) - 0.2;
			double e1 = islandEdge(island, a1) - 0.2;
			groundLine(out, m, Math.cos(a0) * e0, Math.sin(a0) * e0, Math.cos(a1) * e1, Math.sin(a1) * e1, 0.35, line, 70, 70);
		}
	}

	// ---------------------------------------------------------------- Champion's Stage (2026-11-10)

	/** The court's half-length (along the trainers' axis) and half-width. */
	private static double[] courtSize() {
		double length = fieldHalf + 0.5;
		return new double[] {length, Math.max(4, length * 0.62)};
	}

	/** A point on the court: {@code along} the trainers' axis, {@code across} it (relative to the field, on the ground). */
	private static Vec3 courtPoint(double along, double across) {
		Vec3 side = new Vec3(-fieldAxis.z, 0, fieldAxis.x);
		Vec3 flat = fieldAxis.scale(along).add(side.scale(across));
		return onGround(flat.x, flat.z);
	}

	/** A court line laid block by block, so it follows the ground: a bright core in a soft gold glow. */
	private static void courtLine(VertexConsumer out, Matrix4f m, Ambiance.Court court, double a0, double c0, double a1, double c1) {
		int color = Ambiance.color(court.color, 0xF4F0E0);
		int accent = Ambiance.color(court.accent, 0xFFCF4A);
		double length = Math.hypot(a1 - a0, c1 - c0);
		int steps = Math.max(1, (int) Math.ceil(length));
		Vec3 side = new Vec3(-fieldAxis.z, 0, fieldAxis.x);
		for (int s = 0; s < steps; s++) {
			double f0 = s / (double) steps;
			double f1 = (s + 1) / (double) steps;
			Vec3 p = fieldAxis.scale(Mth.lerp(f0, a0, a1)).add(side.scale(Mth.lerp(f0, c0, c1)));
			Vec3 q = fieldAxis.scale(Mth.lerp(f1, a0, a1)).add(side.scale(Mth.lerp(f1, c0, c1)));
			groundLine(out, m, p.x, p.z, q.x, q.z, 0.6, accent, Math.round(court.alpha * 0.25f), Math.round(court.alpha * 0.25f));
			groundLine(out, m, p.x, p.z, q.x, q.z, 0.14, color, court.alpha, court.alpha);
		}
	}

	/** The court: border, centre line, centre circle and spot, a box at each trainer's end. */
	private static void drawCourt(PoseStack pose, MultiBufferSource buffers, Ambiance.Court court) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		double[] size = courtSize();
		double l = size[0];
		double w = size[1];
		courtLine(out, m, court, -l, -w, l, -w);
		courtLine(out, m, court, -l, w, l, w);
		courtLine(out, m, court, -l, -w, -l, w);
		courtLine(out, m, court, l, -w, l, w);
		courtLine(out, m, court, 0, -w, 0, w);
		for (double end : new double[] {-1, 1}) {
			double inner = end * (l - 2.4);
			courtLine(out, m, court, inner, -1.6, inner, 1.6);
			courtLine(out, m, court, inner, -1.6, end * l, -1.6);
			courtLine(out, m, court, inner, 1.6, end * l, 1.6);
		}
		for (double radius : new double[] {w * 0.38, 0.5}) {
			int segments = radius > 1 ? 40 : 12;
			for (int s = 0; s < segments; s++) {
				double t0 = Math.PI * 2 * s / segments;
				double t1 = Math.PI * 2 * (s + 1) / segments;
				courtLine(out, m, court, Math.cos(t0) * radius, Math.sin(t0) * radius, Math.cos(t1) * radius, Math.sin(t1) * radius);
			}
		}
	}

	/** Inner radius of the stands. */
	private static float standsInner(Ambiance.Crowd crowd) {
		return crowd.distance > 0 ? crowd.distance : fieldRadius + 5f;
	}

	/** Whether the camera is in or behind the stands in that direction: then that part is left out, never in the way. */
	private static boolean standsBlocked(double angle, double radius) {
		double flat = Math.sqrt(cameraRel.x * cameraRel.x + cameraRel.z * cameraRel.z);
		if (flat < radius - 2) {
			return false;
		}
		double toward = Math.atan2(cameraRel.z, cameraRel.x);
		return Math.abs(Mth.wrapDegrees(Math.toDegrees(angle - toward))) < 30;
	}

	/** How excited the crowd is, 0..1, at {@code time}: eased between ticks so nothing in the stands jumps. */
	private static float crowdExcitement(float time) {
		return Mth.lerp(time - ticks, crowdExcitePrev, crowdExcite);
	}

	/** How high the wave lifts the people at {@code angle} (0..1); 0 when no wave runs. */
	private static float waveLift(double angle, float time) {
		if (crowdWave < 0) {
			return 0;
		}
		double age = time - crowdWave;
		if (age < 0 || age > 220) {
			return 0;
		}
		double front = Math.atan2(fieldAxis.z, fieldAxis.x) + age * 0.06;
		double behind = Mth.positiveModulo(front - angle, Math.PI * 2);
		// Up just as the front reaches them, down again over the next few seats.
		double lift = behind < 0.05 ? behind / 0.05 : Math.exp(-Math.pow((behind - 0.05) / 0.28, 2));
		return (float) (lift * Mth.clamp((220 - age) / 30, 0, 1));
	}

	/** A small round glow facing the camera, eight-sided: cheap enough for hundreds. */
	private static void smallGlow(VertexConsumer out, Matrix4f m, Vec3 centre, float radius, int color, int alpha) {
		Vec3 normal = cameraRel.subtract(centre).normalize();
		Vec3 u = normal.cross(new Vec3(0, 1, 0));
		u = u.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : u.normalize();
		Vec3 v = normal.cross(u).normalize();
		for (int i = 0; i < 8; i++) {
			double a0 = Math.PI * 2 * i / 8;
			double a1 = Math.PI * 2 * (i + 1) / 8;
			vertex(out, m, centre, color, alpha);
			vertex(out, m, centre, color, alpha);
			vertex(out, m, centre.add(u.scale(Math.cos(a1) * radius)).add(v.scale(Math.sin(a1) * radius)), color, 0);
			vertex(out, m, centre.add(u.scale(Math.cos(a0) * radius)).add(v.scale(Math.sin(a0) * radius)), color, 0);
		}
	}

	/**
	 * The arena (2026-11-10 rework: bright blocky people looked cheap). An arena at night: dark tiers rising all round
	 * under a canopy, a parapet in front carrying an LED ribbon in each trainer's colour, and a crowd in silhouette, its
	 * life in the lights: phones and glow sticks held up in the side's colours, swaying, raised higher as the crowd gets
	 * excited (eased, never switched), a wave running round the stands on victory.
	 * <p>
	 * Solid pass ({@code glow} false): tiers, parapet, back wall, canopy, the people. Light pass: the LED ribbon, light
	 * strips along the rows, lamps under the canopy's edge, the lights in the crowd.
	 */
	private static void drawStands(PoseStack pose, MultiBufferSource buffers, Ambiance.Crowd crowd, float time, boolean glow) {
		VertexConsumer out = buffers.getBuffer(glow ? com.mystaria.showtime.ShowtimeRenderTypes.GLOW : com.mystaria.showtime.ShowtimeRenderTypes.OPAQUE);
		Matrix4f m = pose.last().pose();
		float r0 = standsInner(crowd);
		int side0 = Ambiance.color(crowd.sides.get(0), 0x2A5AD0);
		int side1 = Ambiance.color(crowd.sides.size() > 1 ? crowd.sides.get(1) : crowd.sides.get(0), 0xD03A3A);
		float excite = crowdExcitement(time);
		int segments = 96;
		double outer = r0 + crowd.rows * crowd.rowDepth;
		double top = crowd.rows * crowd.rise;
		// The architecture, segment by segment.
		for (int k = 0; k < segments; k++) {
			double a0 = Math.PI * 2 * k / segments;
			double a1 = Math.PI * 2 * (k + 1) / segments;
			double mid = (a0 + a1) / 2;
			if (standsBlocked(mid, r0)) {
				continue;
			}
			Vec3 d0 = new Vec3(Math.cos(a0), 0, Math.sin(a0));
			Vec3 d1 = new Vec3(Math.cos(a1), 0, Math.sin(a1));
			double b0 = onGround(d0.x * r0, d0.z * r0).y;
			double b1 = onGround(d1.x * r0, d1.z * r0).y;
			int seat = d0.add(d1).dot(fieldAxis) >= 0 ? side1 : side0;
			boolean aisle = k % 12 == 0;
			double front = r0 - 0.1;
			if (glow) {
				// The LED ribbon: the side's colour, a chase running round, brighter with the crowd, flashing white.
				float chase = (float) (0.5 + 0.5 * Math.sin(mid * 10 - time * 0.15));
				float lift = waveLift(mid, time);
				float bright = Mth.clamp(0.3f + 0.3f * chase + 0.35f * excite + 0.4f * lift, 0, 1);
				int color = lerpColor(lerpColor(seat, 0xFFFFFF, 0.2f), 0xFFFFFF, Math.min(1, ledFlash * 0.75f));
				color = lerpColor(color, 0xFFCF4A, lift * 0.7f);
				int alpha = Math.round(210 * bright);
				// Clearly in front of the parapet (2026-11-10: in its very plane, the two fought for depth and the
				// ribbon showed dark hatching).
				double led = front - 0.08;
				quad4(out, m, d0.scale(led).add(0, b0 + 0.35, 0), d1.scale(led).add(0, b1 + 0.35, 0),
						d1.scale(led).add(0, b1 + 0.8, 0), d0.scale(led).add(0, b0 + 0.8, 0), color, alpha);
				// Its bloom, just around it.
				for (int edge = -1; edge <= 1; edge += 2) {
					double y = edge < 0 ? 0.35 : 0.8;
					quad4(out, m, d0.scale(led - 0.02).add(0, b0 + y, 0), d1.scale(led - 0.02).add(0, b1 + y, 0),
							d1.scale(led - 0.02).add(0, b1 + y + edge * 0.35, 0), d0.scale(led - 0.02).add(0, b0 + y + edge * 0.35, 0),
							color, alpha / 3, 0);
				}
				if (aisle) {
					continue;
				}
				for (int j = 1; j < crowd.rows; j++) {
					double ri = r0 + j * crowd.rowDepth - 0.08;
					double y = j * crowd.rise;
					quad4(out, m, d0.scale(ri).add(0, b0 + y - 0.06, 0), d1.scale(ri).add(0, b1 + y - 0.06, 0),
							d1.scale(ri).add(0, b1 + y, 0), d0.scale(ri).add(0, b0 + y, 0), 0x9FC4FF, 40);
				}
				// Lamps along the canopy's front edge, every other segment.
				if (k % 2 == 0) {
					Vec3 lamp = new Vec3(Math.cos(mid), 0, Math.sin(mid)).scale(outer - 3.4).add(0, (b0 + b1) / 2 + top + 2.5, 0);
					smallGlow(out, m, lamp, 0.5f, 0xFFF0D0, 90);
					smallGlow(out, m, lamp, 0.12f, 0xFFFFFF, 240);
				}
				continue;
			}
			// The parapet in front of the first row.
			quad(out, m, d0.scale(front).add(0, b0 - 0.6, 0), d1.scale(front).add(0, b1 - 0.6, 0), d1.scale(front).add(0, b1 + 1.0, 0),
					d0.scale(front).add(0, b0 + 1.0, 0), 0x10131C);
			quad(out, m, d0.scale(front).add(0, b0 + 1.0, 0), d1.scale(front).add(0, b1 + 1.0, 0), d1.scale(r0).add(0, b1 + 1.0, 0),
					d0.scale(r0).add(0, b0 + 1.0, 0), 0x1A1E2A);
			for (int j = 0; j < crowd.rows; j++) {
				double ri = r0 + j * crowd.rowDepth;
				double ro = ri + crowd.rowDepth;
				double y0 = j * crowd.rise;
				double y1 = (j + 1) * crowd.rise;
				int riser = aisle ? 0x1E2230 : 0x161A24;
				int tread = aisle ? 0x232838 : lerpColor(seat, 0x0A0C12, j % 2 == 0 ? 0.74f : 0.8f);
				quad(out, m, d0.scale(ri).add(0, b0 + y0 - 0.6, 0), d1.scale(ri).add(0, b1 + y0 - 0.6, 0), d1.scale(ri).add(0, b1 + y1, 0),
						d0.scale(ri).add(0, b0 + y1, 0), riser);
				quad(out, m, d0.scale(ri).add(0, b0 + y1, 0), d1.scale(ri).add(0, b1 + y1, 0), d1.scale(ro).add(0, b1 + y1, 0),
						d0.scale(ro).add(0, b0 + y1, 0), tread);
			}
			// Back wall, canopy over the top rows.
			quad(out, m, d0.scale(outer).add(0, b0 - 1, 0), d1.scale(outer).add(0, b1 - 1, 0), d1.scale(outer).add(0, b1 + top + 3.2, 0),
					d0.scale(outer).add(0, b0 + top + 3.2, 0), 0x0E1018);
			quad(out, m, d0.scale(outer).add(0, b0 + top + 3.2, 0), d1.scale(outer).add(0, b1 + top + 3.2, 0),
					d1.scale(outer - 3.6).add(0, b1 + top + 2.6, 0), d0.scale(outer - 3.6).add(0, b0 + top + 2.6, 0), 0x12151F);
		}
		// The people.
		for (int j = 0; j < crowd.rows; j++) {
			double radius = r0 + j * crowd.rowDepth + 0.55;
			int count = (int) (Math.PI * 2 * radius / Math.max(0.4, crowd.spacing));
			for (int p = 0; p < count; p++) {
				double angle = Math.PI * 2 * (p + (j % 2) * 0.5) / count;
				int segment = (int) Math.floor(angle / (Math.PI * 2) * segments) % segments;
				if (segment % 12 == 0 || standsBlocked(angle, r0)) {
					continue;
				}
				int id = j * 4096 + p;
				if (hashFloat(id * 3 + 1) < 0.06f) {
					continue; // an empty seat
				}
				Vec3 dir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
				double base = onGround(dir.x * r0, dir.z * r0).y + (j + 1) * crowd.rise;
				Vec3 at = dir.scale(radius).add(0, base, 0);
				if (nearFade(at.add(0, 0.6, 0), 1.0, 1.5) <= 0.05f) {
					continue;
				}
				float s = 0.9f + 0.2f * hashFloat(id * 3 + 2);
				float own = hashFloat(id * 3 + 3);
				float lift = waveLift(angle, time);
				// Everyone moves on their own beat; how much depends on the crowd's mood, eased.
				float mood = Mth.clamp(excite * (0.6f + 0.5f * own) + lift, 0, 1);
				double bounce = mood * Math.abs(Math.sin(time * 0.42 + id * 0.7)) * 0.16 + lift * 0.45;
				double sway = Math.sin(time * 0.045 + id) * 0.03;
				Vec3 right = new Vec3(-dir.z, 0, dir.x);
				Vec3 foot = at.add(dir.scale(-0.05)).add(right.scale(sway)).add(0, bounce, 0);
				float arms = Mth.clamp(mood * 1.4f - own * 0.4f + lift, 0, 1);
				int seat = dir.dot(fieldAxis) >= 0 ? side1 : side0;
				double shoulders = 0.32 * s;
				Vec3 neck = foot.add(0, shoulders, 0);
				Vec3 head = neck.add(0, 0.16 * s, 0);
				Vec3 hand = null;
				double handSide = own < 0.5 ? -1 : 1;
				if (arms > 0.05f) {
					double wave = Math.sin(time * 0.3 + id) * 0.08 * arms;
					hand = foot.add(right.scale(handSide * (0.2 + wave) * s)).add(0, (0.28 + 0.55 * arms) * s, 0);
				}
				if (glow) {
					if (hashFloat(id * 3 + 8) > 0.45f) {
						continue; // no light in hand
					}
					boolean phone = hashFloat(id * 3 + 9) < 0.45f;
					int color = phone ? 0xE8F0FF : lerpColor(seat, 0xFFFFFF, 0.35f);
					Vec3 light = hand != null ? hand : foot.add(dir.scale(-0.12)).add(right.scale(handSide * 0.12 * s)).add(0, 0.28 * s, 0);
					light = light.add(right.scale(Math.sin(time * 0.1 * (1 + 2 * mood) + id) * 0.1 * (0.3 + mood)));
					float twinkle = phone ? 0.85f + 0.15f * Mth.sin(time * 0.2f + id) : 0.6f + 0.4f * Mth.sin(time * 0.15f + id * 1.3f);
					float strength = (0.45f + 0.55f * mood) * twinkle;
					smallGlow(out, m, light, phone ? 0.2f : 0.28f, color, Math.round(100 * strength));
					smallGlow(out, m, light, 0.06f, lerpColor(color, 0xFFFFFF, 0.5f), Math.round(235 * strength));
					continue;
				}
				int body = lerpColor(lerpColor(0x161922, seat, 0.12f), 0x2A2E3A, own * 0.6f);
				double low = 0.25 * s;
				double high = 0.18 * s;
				quad(out, m, foot.add(right.scale(-low)), foot.add(right.scale(low)), neck.add(right.scale(high)), neck.add(right.scale(-high)), body);
				for (int i = 0; i < 8; i++) {
					double a0 = Math.PI * 2 * i / 8;
					double a1 = Math.PI * 2 * (i + 1) / 8;
					double r = 0.14 * s;
					Vec3 p0 = head.add(right.scale(Math.cos(a0) * r)).add(0, Math.sin(a0) * r, 0);
					Vec3 p1 = head.add(right.scale(Math.cos(a1) * r)).add(0, Math.sin(a1) * r, 0);
					quad(out, m, head, head, p1, p0, body);
				}
				if (hand != null) {
					Vec3 shoulder = foot.add(right.scale(handSide * 0.15 * s)).add(0, shoulders * 0.85, 0);
					Vec3 w = right.scale(0.04 * s);
					quad(out, m, shoulder.subtract(w), shoulder.add(w), hand.add(w), hand.subtract(w), body);
				}
			}
		}
	}

	/** A quad of one colour, its alpha as given. */
	private static void quad4(VertexConsumer out, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, int alpha) {
		quad4(out, m, a, b, c, d, color, alpha, alpha);
	}

	/** A quad fading from {@code alphaAB} along its first edge to {@code alphaCD} along its last. */
	private static void quad4(VertexConsumer out, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, int alphaAB, int alphaCD) {
		vertex(out, m, a, color, alphaAB);
		vertex(out, m, b, color, alphaAB);
		vertex(out, m, c, color, alphaCD);
		vertex(out, m, d, color, alphaCD);
	}

	/** A camera flash somewhere in the stands. */
	private static void cameraFlash() {
		Ambiance.Crowd crowd = active.crowd;
		int row = RANDOM.nextInt(Math.max(1, crowd.rows));
		double angle = RANDOM.nextDouble() * Math.PI * 2;
		float r0 = standsInner(crowd);
		Vec3 dir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
		double base = onGround(dir.x * r0, dir.z * r0).y + (row + 1) * crowd.rise;
		CAMERA_FLASHES.add(new CameraFlash(dir.scale(r0 + row * crowd.rowDepth + 0.45).add(0, base + 0.55, 0), ticks));
	}

	private static void drawCameraFlashes(PoseStack pose, MultiBufferSource buffers, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		for (CameraFlash flash : CAMERA_FLASHES) {
			float age = time - flash.born();
			if (age < 0 || age > 5 || standsBlocked(Math.atan2(flash.at().z, flash.at().x), standsInner(active.crowd))) {
				continue;
			}
			float strength = 1 - age / 5f;
			Vec3 normal = cameraRel.subtract(flash.at()).normalize();
			Vec3 u = normal.cross(new Vec3(0, 1, 0));
			u = u.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : u.normalize();
			Vec3 v = normal.cross(u).normalize();
			int alpha = Math.round(240 * strength);
			for (Vec3[] axes : new Vec3[][] { {u, v}, {v, u} }) {
				Vec3 along = axes[0].scale(0.55);
				Vec3 across = axes[1].scale(0.05);
				vertex(out, m, flash.at().add(along), 0xFFFFFF, 0);
				vertex(out, m, flash.at().add(across), 0xFFFFFF, alpha);
				vertex(out, m, flash.at().subtract(along), 0xFFFFFF, 0);
				vertex(out, m, flash.at().subtract(across), 0xFFFFFF, alpha);
			}
			glowDisc(out, m, flash.at(), normal, 0.9f, 0xEEF4FF, Math.round(170 * strength));
		}
	}

	/** Where light tower {@code k} stands (on the ground) and the horizontal direction it faces (toward the field). */
	private static Vec3[] tower(Ambiance.LightTowers towers, int k) {
		double distance = towers.distance > 0 ? towers.distance
				: active.crowd != null ? standsInner(active.crowd) + active.crowd.rows * active.crowd.rowDepth + 3 : fieldRadius + 10;
		double axis = Math.atan2(fieldAxis.z, fieldAxis.x);
		double angle = axis + Math.PI / 4 + Math.PI * 2 * k / Math.max(1, towers.count);
		Vec3 dir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
		return new Vec3[] {onGround(dir.x * distance, dir.z * distance), dir.scale(-1)};
	}

	/** Light tower {@code k}'s lamp bank: its centre, the way it faces (down toward the field), its right and up. */
	private static Vec3[] lampBank(Ambiance.LightTowers towers, int k) {
		Vec3[] t = tower(towers, k);
		Vec3 facing = t[1];
		Vec3 right = new Vec3(-facing.z, 0, facing.x);
		Vec3 normal = facing.add(0, -0.42, 0).normalize();
		Vec3 up = right.cross(normal).normalize();
		if (up.y < 0) {
			up = up.scale(-1);
		}
		Vec3 centre = t[0].add(0, towers.height + 1.0, 0).add(facing.scale(0.6));
		return new Vec3[] {centre, normal, right, up};
	}

	/**
	 * Light towers. Solid pass: four lattice legs narrowing upward, X braces between them every few blocks, a dark
	 * plate behind the lamps. Light pass: a bank of five by three lamps, each with its halo, a big glow round the bank
	 * flaring when it faces the camera, a soft wide beam falling on the field.
	 */
	private static void drawLightTowers(PoseStack pose, MultiBufferSource buffers, Ambiance.LightTowers towers, float time, boolean glow) {
		VertexConsumer out = buffers.getBuffer(glow ? com.mystaria.showtime.ShowtimeRenderTypes.GLOW : com.mystaria.showtime.ShowtimeRenderTypes.OPAQUE);
		Matrix4f m = pose.last().pose();
		int frame = Ambiance.color(towers.frame, 0x2A2E38);
		int lamp = Ambiance.color(towers.lamp, 0xFFF6E0);
		for (int k = 0; k < towers.count; k++) {
			Vec3[] t = tower(towers, k);
			Vec3 base = t[0];
			Vec3 facing = t[1];
			Vec3 right = new Vec3(-facing.z, 0, facing.x);
			Vec3[] bank = lampBank(towers, k);
			if (nearFade(base.add(0, towers.height / 2, 0), 2, 2) <= 0.05f && !glow) {
				continue;
			}
			if (!glow) {
				int levels = Math.max(2, (int) (towers.height / 2.5));
				Vec3[][] corners = new Vec3[levels + 1][4];
				for (int l = 0; l <= levels; l++) {
					double f = l / (double) levels;
					double w = Mth.lerp(f, 1.0, 0.45);
					double y = towers.height * f;
					corners[l][0] = base.add(right.scale(-w)).add(facing.scale(-w)).add(0, y, 0);
					corners[l][1] = base.add(right.scale(w)).add(facing.scale(-w)).add(0, y, 0);
					corners[l][2] = base.add(right.scale(w)).add(facing.scale(w)).add(0, y, 0);
					corners[l][3] = base.add(right.scale(-w)).add(facing.scale(w)).add(0, y, 0);
				}
				int dark = lerpColor(frame, 0x000000, 0.3f);
				for (int l = 0; l < levels; l++) {
					for (int c = 0; c < 4; c++) {
						int n = (c + 1) % 4;
						strip(out, m, corners[l][c], corners[l + 1][c], 0.12, frame);
						strip(out, m, corners[l][c], corners[l + 1][n], 0.05, dark);
						strip(out, m, corners[l][n], corners[l + 1][c], 0.05, dark);
						strip(out, m, corners[l + 1][c], corners[l + 1][n], 0.06, frame);
					}
				}
				Vec3 back = bank[0].subtract(bank[1].scale(0.12));
				Vec3 r = bank[2].scale(2.3);
				Vec3 u = bank[3].scale(1.5);
				quad(out, m, back.subtract(r).subtract(u), back.add(r).subtract(u), back.add(r).add(u), back.subtract(r).add(u), frame);
				// The arm holding the bank, from the mast's top.
				strip(out, m, corners[levels][0].add(corners[levels][2]).scale(0.5), back, 0.18, frame);
				continue;
			}
			Vec3 toCamera = cameraRel.subtract(bank[0]).normalize();
			for (int x = 0; x < 5; x++) {
				for (int y = 0; y < 3; y++) {
					Vec3 c = bank[0].add(bank[2].scale((x - 2) * 0.9)).add(bank[3].scale((y - 1) * 0.9)).add(bank[1].scale(0.06));
					Vec3 r = bank[2].scale(0.32);
					Vec3 u = bank[3].scale(0.32);
					vertex(out, m, c.subtract(r).subtract(u), lamp, 255);
					vertex(out, m, c.add(r).subtract(u), lamp, 255);
					vertex(out, m, c.add(r).add(u), lamp, 255);
					vertex(out, m, c.subtract(r).add(u), lamp, 255);
					glowDisc(out, m, c, cameraRel.subtract(c).normalize(), 1.0f, lamp, 55);
				}
			}
			glowDisc(out, m, bank[0], toCamera, 6f, lamp, 30);
			float flare = (float) Math.max(0, toCamera.dot(bank[1]));
			if (flare > 0.3f) {
				glowDisc(out, m, bank[0], toCamera, 13f * flare, lamp, Math.round(45 * flare * flare));
			}
			Vec3 target = onGround(-facing.x * 3, -facing.z * 3);
			beam(out, m, bank[0].add(bank[1].scale(0.4)), target, 2.4f, 7.5f, lamp, towers.beam);
		}
	}

	/** A thin flat bar between two points, turned to face the camera (truss members). */
	private static void strip(VertexConsumer out, Matrix4f m, Vec3 a, Vec3 b, double width, int color) {
		Vec3 along = b.subtract(a);
		Vec3 side = along.cross(cameraRel.subtract(a));
		side = side.lengthSqr() < 1.0E-8 ? new Vec3(width, 0, 0) : side.normalize().scale(width / 2);
		quad(out, m, a.subtract(side), a.add(side), b.add(side), b.subtract(side), color);
	}

	/** Followspots: from two opposite light towers onto each battling Pokémon, a pool of light at its feet. */
	private static void drawFollowspots(PoseStack pose, MultiBufferSource buffers, Ambiance.Followspots spots, Ambiance.LightTowers towers) {
		com.mystaria.showtime.api.BattleStage stage = com.mystaria.showtime.battle.BattleCameraDirector.stage();
		if (stage == null) {
			return;
		}
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		int color = Ambiance.color(spots.color, 0xFFF4D0);
		for (int k = 0; k < 2; k++) {
			net.minecraft.world.entity.Entity pokemon = stage.activePokemon(k == 0);
			if (pokemon == null || pokemon.isRemoved()) {
				continue;
			}
			Vec3 target = pokemon.position().subtract(center);
			if (target.horizontalDistance() > fieldRadius * 1.5) {
				continue;
			}
			// Eased: the spot glides after its Pokémon instead of snapping to it.
			SPOT_AIM[k] = SPOT_AIM[k] == null ? target : SPOT_AIM[k].lerp(target, 0.08);
			Vec3 pool = onGround(SPOT_AIM[k].x, SPOT_AIM[k].z);
			Vec3 from = lampBank(towers, (k * Math.max(1, towers.count / 2)) % Math.max(1, towers.count))[0];
			beam(out, m, from, pool.add(0, 0.2, 0), 0.35f, 1.9f, color, spots.alpha);
			glowDisc(out, m, pool.add(0, 0.04, 0), new Vec3(0, 1, 0), 2.4f, color, Math.round(spots.alpha * 1.5f));
		}
	}

	/** Flame and spark fountains shooting up from the court's four corners. */
	private static void pyro(ClientLevel level) {
		double[] size = active.court != null ? courtSize() : new double[] {fieldRadius * 0.6, fieldRadius * 0.6};
		ParticleOptions flame = particle("minecraft:flame");
		ParticleOptions spark = particle("minecraft:firework");
		ParticleOptions lava = particle("minecraft:lava");
		for (int a = -1; a <= 1; a += 2) {
			for (int c = -1; c <= 1; c += 2) {
				Vec3 at = center.add(courtPoint(a * (size[0] + 0.8), c * (size[1] + 0.8)));
				for (int i = 0; i < 36 && flame != null; i++) {
					level.addParticle(flame, at.x, at.y + 0.2, at.z, RANDOM.nextGaussian() * 0.02, 0.25 + RANDOM.nextDouble() * 0.35,
							RANDOM.nextGaussian() * 0.02);
				}
				for (int i = 0; i < 18 && spark != null; i++) {
					level.addParticle(spark, at.x, at.y + 0.2, at.z, RANDOM.nextGaussian() * 0.06, 0.35 + RANDOM.nextDouble() * 0.4,
							RANDOM.nextGaussian() * 0.06);
				}
				for (int i = 0; i < 4 && lava != null; i++) {
					level.addParticle(lava, at.x, at.y + 0.3, at.z, 0, 0, 0);
				}
			}
		}
		Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
				SoundEvent.createVariableRangeEvent(ResourceLocation.parse("minecraft:item.firecharge.use")), 0.8f, 0.6f));
	}

	/** Confetti: half drifting down from above the court, half shot up from its corners like cannons. */
	private static void launchConfetti(int count) {
		List<String> names = active.confettiColors.isEmpty() ? List.of("#ffcf4a", "#f4f0e0") : active.confettiColors;
		double[] size = courtSize();
		for (int i = 0; i < count; i++) {
			int color = Ambiance.color(names.get(RANDOM.nextInt(names.size())), 0xFFCF4A);
			float spin = 0.15f + RANDOM.nextFloat() * 0.35f;
			float phase = RANDOM.nextFloat() * 6.28f;
			long born = ticks + RANDOM.nextInt(12);
			if (i % 2 == 0) {
				Vec3 from = courtPoint(RANDOM.nextGaussian() * size[0] * 0.6, RANDOM.nextGaussian() * size[1] * 0.6)
						.add(0, 11 + RANDOM.nextDouble() * 5, 0);
				CONFETTI.add(new Confetti(born, from, new Vec3(RANDOM.nextGaussian() * 0.04, 0, RANDOM.nextGaussian() * 0.04), color, spin, phase));
			} else {
				int corner = RANDOM.nextInt(4);
				Vec3 from = courtPoint((corner < 2 ? -1 : 1) * (size[0] + 0.8), (corner % 2 == 0 ? -1 : 1) * (size[1] + 0.8)).add(0, 0.8, 0);
				Vec3 inward = new Vec3(-from.x, 0, -from.z).normalize();
				Vec3 speed = inward.scale(0.1 + RANDOM.nextDouble() * 0.12)
						.add(RANDOM.nextGaussian() * 0.05, 0.75 + RANDOM.nextDouble() * 0.4, RANDOM.nextGaussian() * 0.05);
				CONFETTI.add(new Confetti(born, from, speed, color, spin, phase));
			}
		}
	}

	/**
	 * Confetti pieces: slowed by the air, falling at a gentle pace, swaying, tumbling (their shine flickering as they
	 * turn); once down they lie flat on the ground, then fade.
	 */
	private static void drawConfetti(PoseStack pose, MultiBufferSource buffers, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.TINT);
		Matrix4f m = pose.last().pose();
		double drag = 0.06;
		for (Confetti piece : CONFETTI) {
			double t = time - piece.born();
			if (t < 0) {
				continue;
			}
			double slowed = (1 - Math.exp(-drag * t)) / drag;
			double settle = Math.min(1, t / 30);
			Vec3 at = piece.from().add(piece.speed().scale(slowed)).add(
					Math.sin(t * 0.12 + piece.phase()) * 0.35 * settle, -0.028 * t, Math.cos(t * 0.1 + piece.phase()) * 0.35 * settle);
			double ground = onGround(at.x, at.z).y + 0.03;
			boolean landed = at.y < ground;
			if (landed) {
				at = new Vec3(at.x, ground, at.z);
			}
			float fade = (float) Mth.clamp((CONFETTI_LIFE - t) / 30.0, 0, 1) * nearFade(at, 0.3, 1.0);
			if (fade <= 0.03f) {
				continue;
			}
			Vec3 u;
			Vec3 v;
			float shine;
			if (landed) {
				double a = piece.phase() * 6;
				u = new Vec3(Math.cos(a), 0, Math.sin(a));
				v = new Vec3(-Math.sin(a), 0, Math.cos(a));
				shine = 0.75f;
			} else {
				double th = piece.spin() * t;
				double ps = piece.spin() * 0.7 * t + piece.phase();
				u = new Vec3(Math.cos(th), 0.5 * Math.sin(th), 0.3 * Math.sin(th)).normalize();
				v = new Vec3(-0.3 * Math.sin(ps), Math.cos(ps), Math.sin(ps)).normalize();
				shine = (float) Math.abs(Math.cos(th * 1.3 + piece.phase()));
			}
			int color = lerpColor(lerpColor(piece.color(), 0x000000, 0.45f), lerpColor(piece.color(), 0xFFFFFF, 0.25f), shine);
			int alpha = Math.round(255 * fade);
			Vec3 w = u.scale(0.09);
			Vec3 h = v.scale(0.05);
			vertex(out, m, at.subtract(w).subtract(h), color, alpha);
			vertex(out, m, at.add(w).subtract(h), color, alpha);
			vertex(out, m, at.add(w).add(h), color, alpha);
			vertex(out, m, at.subtract(w).add(h), color, alpha);
		}
	}

	private static Vec3 moonPlace(Ambiance.Moon moon) {
		double yaw = Math.toRadians(moon.yaw);
		double pitch = Math.toRadians(moon.pitch);
		return new Vec3(Math.cos(pitch) * Math.cos(yaw), Math.sin(pitch), Math.cos(pitch) * Math.sin(yaw)).scale(skyRadius());
	}

	private static void drawMoonHalo(PoseStack pose, MultiBufferSource buffers, Ambiance.Moon moon) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Vec3 at = moonPlace(moon);
		glowDisc(out, pose.last().pose(), at, at.normalize().scale(-1), moon.size * moon.halo, Ambiance.color(moon.color, 0xF4F0E0),
				moon.haloAlpha);
	}

	/** The moon's disc: solid, slightly darker toward one side, with a few soft grey seas. */
	private static void drawMoonDisc(PoseStack pose, MultiBufferSource buffers, Ambiance.Moon moon) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.TINT);
		Matrix4f m = pose.last().pose();
		Vec3 at = moonPlace(moon);
		Vec3 normal = at.normalize().scale(-1);
		Vec3 u = normal.cross(new Vec3(0, 1, 0)).normalize();
		Vec3 v = normal.cross(u).normalize();
		int color = Ambiance.color(moon.color, 0xF4F0E0);
		int segments = 32;
		for (int i = 0; i < segments; i++) {
			double a0 = Math.PI * 2 * i / segments;
			double a1 = Math.PI * 2 * (i + 1) / segments;
			int rim = moon.seas ? lerpColor(color, 0x707070, (float) (0.25 + 0.25 * Math.cos(a0 - 0.8))) : color;
			vertex(out, m, at, color, 255);
			vertex(out, m, at, color, 255);
			vertex(out, m, at.add(u.scale(Math.cos(a1) * moon.size)).add(v.scale(Math.sin(a1) * moon.size)), rim, 255);
			vertex(out, m, at.add(u.scale(Math.cos(a0) * moon.size)).add(v.scale(Math.sin(a0) * moon.size)), rim, 255);
		}
		// Seas: a few soft grey patches, a hair in front of the disc.
		for (int sea = 0; sea < (moon.seas ? 4 : 0); sea++) {
			Vec3 c = at.add(normal.scale(0.05)).add(u.scale((hashFloat(sea * 3 + 1) - 0.5) * moon.size))
					.add(v.scale((hashFloat(sea * 3 + 2) - 0.5) * moon.size));
			float r = moon.size * (0.15f + 0.12f * hashFloat(sea * 3 + 3));
			for (int i = 0; i < 16; i++) {
				double a0 = Math.PI * 2 * i / 16;
				double a1 = Math.PI * 2 * (i + 1) / 16;
				vertex(out, m, c, 0x9A9A90, 70);
				vertex(out, m, c, 0x9A9A90, 70);
				vertex(out, m, c.add(u.scale(Math.cos(a1) * r)).add(v.scale(Math.sin(a1) * r)), 0x9A9A90, 0);
				vertex(out, m, c.add(u.scale(Math.cos(a0) * r)).add(v.scale(Math.sin(a0) * r)), 0x9A9A90, 0);
			}
		}
	}

	/** A round glow facing the camera: bright in the middle, fading to nothing at its rim. */
	private static void glowDisc(VertexConsumer out, Matrix4f m, Vec3 centre, Vec3 normal, float radius, int color, int alpha) {
		Vec3 u = normal.cross(new Vec3(0, 1, 0));
		u = u.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : u.normalize();
		Vec3 v = normal.cross(u).normalize();
		int segments = 24;
		for (int i = 0; i < segments; i++) {
			double a0 = Math.PI * 2 * i / segments;
			double a1 = Math.PI * 2 * (i + 1) / segments;
			vertex(out, m, centre, color, alpha);
			vertex(out, m, centre, color, alpha);
			vertex(out, m, centre.add(u.scale(Math.cos(a1) * radius)).add(v.scale(Math.sin(a1) * radius)), color, 0);
			vertex(out, m, centre.add(u.scale(Math.cos(a0) * radius)).add(v.scale(Math.sin(a0) * radius)), color, 0);
		}
	}

	/** The mirror ball itself: a turning sphere of small square facets, some catching the light. */
	private static void drawMirrorBall(PoseStack pose, MultiBufferSource buffers, Ambiance.MirrorBall ball, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.TINT);
		Matrix4f m = pose.last().pose();
		Vec3 centre = new Vec3(0, ball.height, 0);
		// The cord it hangs from.
		vertex(out, m, centre.add(-0.03, ball.size, 0), 0x303030, 230);
		vertex(out, m, centre.add(0.03, ball.size, 0), 0x303030, 230);
		vertex(out, m, centre.add(0.03, ball.size + 12, 0), 0x303030, 230);
		vertex(out, m, centre.add(-0.03, ball.size + 12, 0), 0x303030, 230);
		double turn = time / 1200.0 * ball.speed * Math.PI * 2;
		int rings = 9;
		int around = 18;
		for (int r = 0; r < rings; r++) {
			double lat0 = Math.PI * r / rings - Math.PI / 2;
			double lat1 = Math.PI * (r + 1) / rings - Math.PI / 2;
			for (int a = 0; a < around; a++) {
				double lon0 = Math.PI * 2 * a / around + turn;
				double lon1 = Math.PI * 2 * (a + 1) / around + turn;
				float glint = hashFloat(r * 31 + a * 17 + (int) (time / 3) % 7 * 1013);
				int shade = glint > 0.86f ? 0xFFFFFF : (r + a) % 2 == 0 ? 0xB8C0CC : 0x8890A0;
				vertex(out, m, sphere(centre, ball.size, lat0, lon0), shade, 255);
				vertex(out, m, sphere(centre, ball.size, lat0, lon1), shade, 255);
				vertex(out, m, sphere(centre, ball.size, lat1, lon1), shade, 255);
				vertex(out, m, sphere(centre, ball.size, lat1, lon0), shade, 255);
			}
		}
	}

	private static Vec3 sphere(Vec3 centre, double radius, double lat, double lon) {
		return centre.add(Math.cos(lat) * Math.cos(lon) * radius, Math.sin(lat) * radius, Math.cos(lat) * Math.sin(lon) * radius);
	}

	/** 8-bit sprites, a 7x7 grid each: '.' empty, letters a colour. */
	private static final java.util.Map<String, String[]> SPRITE_ART = java.util.Map.of(
			"coin", new String[] {"..YYY..", ".YyyyY.", "YyYYyyY", "YyYYyyY", "YyYYyyY", ".YyyyY.", "..YYY.."},
			"heart", new String[] {".RR.RR.", "RrrRrrR", "RrwrrrR", "RrrrrrR", ".RrrrR.", "..RrR..", "...R..."},
			"star", new String[] {"...Y...", "..YyY..", "YYyyyYY", ".YyyyY.", "..YyY..", ".YY.YY.", "Y.....Y"},
			"gem", new String[] {".CCCCC.", "CcwcccC", "CccccCC", ".CcccC.", "..CcC..", "...C...", "......."});
	private static final java.util.Map<Character, Integer> SPRITE_COLORS = java.util.Map.of(
			'Y', 0xC88A00, 'y', 0xFFE23D, 'R', 0xA0102A, 'r', 0xFF3050, 'C', 0x0C8CA8, 'c', 0x40F0FF, 'w', 0xFFFFFF);

	/** Arcade's sprites: pixel art floating round the field, always facing the camera, bobbing and turning. */
	private static void drawSprites(PoseStack pose, MultiBufferSource buffers, Ambiance.Sprites sprites, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		for (Orbiter o : SPRITES) {
			double angle = o.angle() + time / 1200.0 * sprites.orbit * Math.PI * 2;
			Vec3 at = new Vec3(Math.cos(angle) * o.radius(), o.height() + Math.sin(time / 18.0 + o.phase()) * sprites.bob,
					Math.sin(angle) * o.radius());
			float fade = nearFade(at, 2.0, 3.0);
			if (fade <= 0.02f) {
				continue;
			}
			Vec3 toCamera = cameraRel.subtract(at);
			Vec3 right = new Vec3(toCamera.z, 0, -toCamera.x);
			right = right.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : right.normalize();
			Vec3 up = new Vec3(0, 1, 0);
			String[] art = SPRITE_ART.getOrDefault(sprites.shapes.get(o.kind() % sprites.shapes.size()), SPRITE_ART.get("star"));
			float px = sprites.pixel;
			for (int row = 0; row < art.length; row++) {
				for (int col = 0; col < art[row].length(); col++) {
					Integer color = SPRITE_COLORS.get(art[row].charAt(col));
					if (color == null) {
						continue;
					}
					Vec3 corner = at.add(right.scale((col - 3.5) * px)).add(up.scale((3.5 - row) * px));
					int alpha = Math.round(230 * fade);
					vertex(out, m, corner, color, alpha);
					vertex(out, m, corner.add(right.scale(px)), color, alpha);
					vertex(out, m, corner.add(right.scale(px)).add(up.scale(-px)), color, alpha);
					vertex(out, m, corner.add(up.scale(-px)), color, alpha);
				}
			}
		}
	}

	/** Meadow's butterflies: two wings flapping, wandering round the field at grass height. */
	private static void drawCritters(PoseStack pose, MultiBufferSource buffers, Ambiance.Critters critters, float time) {
		VertexConsumer out = buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.TINT);
		Matrix4f m = pose.last().pose();
		for (Orbiter o : CRITTERS) {
			double t = time / 20.0;
			double angle = o.angle() + t * 0.18 * (o.kind() % 2 == 0 ? 1 : -1) + Math.sin(t * 0.7 + o.phase()) * 0.4;
			double radius = o.radius() + Math.sin(t * 0.5 + o.phase()) * 2.0;
			double x = Math.cos(angle) * radius;
			double z = Math.sin(angle) * radius;
			Vec3 body = onGround(x, z).add(0, o.height() + Math.sin(t * 1.3 + o.phase()) * 0.5, 0);
			if (nearFade(body, 0.8, 1.2) <= 0.05f) {
				continue;
			}
			Vec3 heading = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
			Vec3 side = new Vec3(heading.z, 0, -heading.x);
			double flap = Math.sin(time * 0.9 + o.phase() * 5) * 1.1;
			int color = Ambiance.color(critters.colors.get(o.kind() % critters.colors.size()), 0xFFFFFF);
			double size = 0.16;
			for (int wing = -1; wing <= 1; wing += 2) {
				Vec3 out1 = side.scale(wing * Math.cos(flap) * size).add(0, Math.abs(Math.sin(flap)) * size, 0);
				vertex(out, m, body.add(heading.scale(size * 0.6)), color, 235);
				vertex(out, m, body.add(heading.scale(size * 0.6)).add(out1), color, 235);
				vertex(out, m, body.add(heading.scale(-size * 0.6)).add(out1), color, 235);
				vertex(out, m, body.add(heading.scale(-size * 0.6)), color, 235);
			}
		}
	}

	/** A moment's big text, popping in and fading out, in Minecraft's pixel font with a hard shadow. */
	private static void drawHudText(GuiGraphics graphics) {
		Minecraft mc = Minecraft.getInstance();
		float life = hudTextLeft / 26f;
		float scale = 4f * (life > 0.85f ? 1f + (life - 0.85f) * 3f : 1f);
		int alpha = Math.round(255 * Math.min(1f, life * 3f));
		if (alpha < 8) {
			return;
		}
		var pose = graphics.pose();
		pose.pushPose();
		pose.translate(graphics.guiWidth() / 2f, graphics.guiHeight() * 0.28f, 400);
		pose.scale(scale, scale, 1f);
		int w = mc.font.width(hudText);
		graphics.drawString(mc.font, hudText, -w / 2 + 1, 1, (alpha << 24), false);
		graphics.drawString(mc.font, hudText, -w / 2, 0, (alpha << 24) | hudTextColor, false);
		pose.popPose();
	}

	private static float hashFloat(int seed) {
		int x = seed * 0x9E3779B1;
		x ^= x >>> 15;
		x *= 0x85EBCA77;
		x ^= x >>> 13;
		return (x & 0xFFFFFF) / (float) 0x1000000;
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

	/**
	 * Most particles an ambiance may spawn per tick, all kinds together (2026-11-10): beyond it, every kind is scaled
	 * down alike. A resource pack's ambiance with heavy rates stays readable and cheap; calm effects halve it.
	 */
	private static final float PARTICLES_PER_TICK = 10f;

	/** The factor applied to the ambiance's particle rates this tick. */
	private static float particleBudget() {
		float total = 0;
		for (Ambiance.Particles particles : active.particles) {
			total += Math.max(0, particles.rate);
		}
		float factor = total > PARTICLES_PER_TICK ? PARTICLES_PER_TICK / total : 1f;
		return calm() ? factor * 0.5f : factor;
	}

	private static void spawn(ClientLevel level, Ambiance.Particles spec, float budget) {
		ParticleOptions particle = particle(spec.type);
		if (particle == null) {
			return;
		}
		float rate = spec.rate * budget;
		int count = (int) rate + (RANDOM.nextFloat() < rate - (int) rate ? 1 : 0);
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

	private static int betweenTicks(List<Integer> range) {
		int min = range.isEmpty() ? 0 : range.get(0);
		int max = range.size() > 1 ? range.get(1) : min;
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
