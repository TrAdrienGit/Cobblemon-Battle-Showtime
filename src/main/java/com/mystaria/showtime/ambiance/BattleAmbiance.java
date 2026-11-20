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
	/** How far the trainers go before the field follows them, and the steps its size changes by. */
	private static final float FIELD_STEP = 4f;
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
	/** A battle, the preview or a battle seen from afar holds the ambiance: no area's ambiance meanwhile. */
	private static boolean owned;
	/** The ambiance laid on a place (a hub), not on a battle: its box, and what it was started from. */
	private static com.mystaria.showtime.api.AmbianceArea area;
	private static String areaKey;
	private static int areaTicks;
	/** How far from the player an area's particles fall (always in the box): a whole hub would spread them too thin. */
	private static final double AREA_REACH = 24;
	/**
	 * An area is paved with tiles of this side, fixed on the box's grid: each tile shows the ambiance's drawn effects
	 * (wisps, lanterns, sprites...) as one battle field does, so they are as dense everywhere in a big hub. The field's
	 * radius is half of it.
	 */
	private static final double AREA_TILE = 24;
	/** Tiles drawn: those whose centre is this close to the camera. */
	private static final double AREA_DRAW = 44;
	/** From this far, a tile's drawn effects fade out, gone at AREA_DRAW: no tile pops in or out of sight. */
	private static final double AREA_FADE = 30;
	/** At most this many tiles drawn (the nearest), floors of a tall hub included. */
	private static final int AREA_MAX_TILES = 16;
	/** The floors of each tile of the area (heights of the world), read from the blocks now and then. */
	private static final java.util.Map<Long, double[]> TILE_LEVELS = new java.util.HashMap<>();
	/** The height the scene's once-drawn things (spotlights, the sky's) stand on: the place's own, fixed. */
	private static double areaAnchorY;
	/** The fading of the tile drawing (1 outside an area): applied to every vertex's alpha. */
	private static float tileFade = 1;
	/** The tiles drawn this frame (the field's own centre first), and the time shift of the one drawing. */
	private static final List<Vec3> TILES = new ArrayList<>();
	private static float tileShift;
	private static long ticks;
	private static long startDayTime;
	private static int previewLeft;
	private static int nextStrike;
	private static int flashColor;
	/** This player's own battle: its trainers and the ambiance id it shows, kept across a change of ambiance. */
	private static List<UUID> followed;
	private static String followedId;
	private static int followTicks;
	/** Ticks since this player's battle started ({@link #followOverride}). */
	private static int sinceStart;
	private static int flashLeft;
	/** The flash's length when it started (a transition's cue may hold one longer). */
	private static int flashTotal = FLASH_TICKS;
	/**
	 * A transition playing (2026-11-20, see {@link Transitions}): the old ambiance, the props and veins of it that stay a
	 * while to leave in their own way. Its clock is the new ambiance's {@link #ticks}, which starts with it.
	 */
	private static Transitions.Transition transition;
	private static Ambiance outgoing;
	private static final List<Placed> LEAVING = new ArrayList<>();
	private static final java.util.Map<Long, int[]> LEAVING_VEINS = new java.util.LinkedHashMap<>();
	private static int nextBoltId = -2_000_000;
	private static final Random RANDOM = new Random();
	private static final List<Floating> FLOATING = new ArrayList<>();
	/** The camera this frame, from the field's centre: beams and sprites fade near it. */
	private static Vec3 cameraRel = Vec3.ZERO;
	/** This frame's camera (world) and view, for the stadiums built of blocks: what is out of view is skipped. */
	private static Vec3 cameraWorld = Vec3.ZERO;
	private static net.minecraft.client.renderer.culling.Frustum frustum;
	/** This frame's land is drawn in blocks: its grass and flowers are blocks of it, not drawn again. */
	private static boolean landInBlocks;
	/** This frame's sea or cave is drawn in blocks: what it holds is not drawn again (its glitter, its floor). */
	private static boolean seaInBlocks;
	private static boolean caveInBlocks;
	private static boolean hallInBlocks;
/** Drawing a mirror's reflection: the hall's own mirrors fall back to their painted sheen. */
private static boolean reflecting;
/** The wall left out while its reflection is drawn (it lies in the mirror's very plane); -1 for none. */
private static int skipWall = -1;
private static java.lang.reflect.Method irisInUse;
private static Object iris;
private static boolean irisChecked;
	/** Sprites (Arcade) and critters (Meadow): where each turns round the field. */
	private record Orbiter(float angle, float radius, float height, float phase, int kind) {
	}

	private static final List<Orbiter> SPRITES = new ArrayList<>();
	/** Corrupted's veins: segments on the ground (x0, z0, x1, z1, distance from their root), built once. */
	/** Vein blocks, by cell (x << 32 | z, from the centre's block): the sides each runs out of, how far along the vein. */
	private static final java.util.Map<Long, int[]> VEINS = new java.util.LinkedHashMap<>();
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
		BiomeScenes.forget(); // the place is read again for each battle
		String[] want = wanted();
		begin(Ambiances.resolve(want[1], want[0]), battleTrainers, 0);
		owned = true;
		followed = List.copyOf(battleTrainers);
		followedId = want[0] + "|" + want[1];
		sinceStart = 0;
	}

	/**
	 * The stadium and the ambiance this player's battle should show now ({@code [stadium, ambiance]}, 2026-11-19). The
	 * ambiance: the one imposed, else their own. The stadium: the one imposed; else the one a preset imposed as the
	 * ambiance brings (the layer first, then the battle's own); else the player's own.
	 */
	private static String[] wanted() {
		return scene(ShowtimeHooks.ambianceOverride(), ShowtimeHooks.baseAmbianceOverride());
	}

	/**
	 * A scene imposed ({@code imposed}, see {@link Ambiances#scene}; null: none), over the battle's own ({@code base}).
	 * The stadium: the one a mod imposes alone; else the imposed scene's, or the stadium its preset brings; else the
	 * base scene's, or its preset's; else the player's own.
	 */
	private static String[] scene(String imposed, String base) {
		String imposedAmbiance = imposed == null ? "" : Ambiances.sceneAmbiance(imposed);
		String ambiance = imposedAmbiance.isEmpty() ? ownChoice() : imposedAmbiance;
		String stadium = ShowtimeHooks.stadiumOverride();
		if (stadium == null) {
			stadium = Ambiances.sceneStadium(imposed);
		}
		if (stadium == null) {
			stadium = Ambiances.presetStadium(imposedAmbiance);
		}
		if (stadium == null && base != null) {
			stadium = Ambiances.sceneStadium(base);
			if (stadium == null) {
				stadium = Ambiances.presetStadium(Ambiances.sceneAmbiance(base));
			}
		}
		if (stadium == null) {
			stadium = Ambiances.isPreset(imposedAmbiance) ? null : ownStadium();
		}
		if (Ambiances.FIELD_STADIUM.equals(stadium)) {
			String suggested = ShowtimeHooks.fieldStadium();
			stadium = suggested == null ? NONE : suggested;
		}
		return new String[] {stadium, ambiance};
	}

	/** The player's own stadium: the one chosen, else (never chosen) the one their preset brings, else none. */
	/** Whether the player's own choice shows anything (an ambiance, or a stadium alone). */
	public static boolean ownSceneShows() {
		return Ambiances.resolve(ownChoice(), ownStadium()) != null;
	}

	public static String ownStadiumChoice() {
		return ownStadium();
	}

	private static String ownStadium() {
		String[] biome = biomeScene();
		if (biome != null) {
			return biome[0] == null ? NONE : biome[0];
		}
		String chosen = ShowtimeConfig.stadium();
		if (chosen == null) {
			String preset = Ambiances.presetStadium(ownChoice());
			return preset != null ? preset : NONE;
		}
		return NONE.equals(chosen) || Ambiances.isStadium(chosen) ? chosen : NONE;
	}

	/**
	 * The ambiance imposed may change during the battle (2026-11-18: a Pokémon Reborn field changing, or gone and the
	 * battle's own coming back): checked twice a second, the new one replaces the old behind a flash of its colour.
	 * The first second, every tick and with no flash (2026-11-20): what another mod imposes is often only known once
	 * Cobblemon has the battle, a tick after it starts, and the right place should be built behind the intro, not
	 * swapped in when it ends.
	 */
	private static void followOverride() {
		if (followed == null) {
			return;
		}
		sinceStart++;
		if (sinceStart > 20 && ++followTicks % 10 != 0) {
			return;
		}
		String[] want = wanted();
		String id = want[0] + "|" + want[1];
		if (java.util.Objects.equals(id, followedId)) {
			return;
		}
		List<UUID> keep = followed;
		String[] was = followedId == null ? new String[] {null, null} : followedId.split("\\|", 2);
		Ambiance next = Ambiances.resolve(want[1], want[0]);
		boolean intro = sinceStart <= 20 || Minecraft.getInstance().screen instanceof com.mystaria.showtime.battle.BattleIntroScreen;
		// A transition of its own between the two (2026-11-20) plays instead of the cut behind a flash.
		Transitions.Transition play = active != null && next != null && !intro
				? Transitions.find(Ambiances.baseAmbiance(was[1]), Ambiances.baseAmbiance(want[1])) : null;
		Ambiance old = active;
		List<Placed> oldPlaced = List.copyOf(PLACED);
		java.util.Map<Long, int[]> oldVeins = new java.util.LinkedHashMap<>(VEINS);
		begin(next, keep, 0, play != null && (play.holdOld > 0 || java.util.Objects.equals(was[0], want[0]))); // ends the old one: set again below
		owned = true;
		followed = keep;
		followedId = id;
		if (play != null && active != null) {
			startTransition(play, old, oldPlaced, oldVeins);
		} else if (active != null && next != null && !intro) {
			flashColor = Ambiance.color(next.sky != null ? next.sky.fogColor : null, 0xFFFFFF);
			flashLeft = FLASH_TICKS;
			flashTotal = FLASH_TICKS;
		}
	}

	/** The new ambiance is in: what of the old one leaves on its own stays a while, and the cues at 0 play. */
	private static void startTransition(Transitions.Transition play, Ambiance old, List<Placed> oldPlaced, java.util.Map<Long, int[]> oldVeins) {
		transition = play;
		outgoing = old;
		for (Placed placed : oldPlaced) {
			if (play.leaving.containsKey(placed.prop().kind)) {
				LEAVING.add(placed);
			}
		}
		if (play.veinsFade > 0 && old.veins != null) {
			LEAVING_VEINS.putAll(oldVeins);
		}
		if (play.holdOld > 0 || play.cover != null && "spread".equals(play.cover.mode)) {
			com.mystaria.showtime.ambiance.blocks.BlockScenery.pin();
		}
		Transitions.Camera camera = play.camera;
		if (camera != null) {
			float seconds = camera.seconds > 0 ? camera.seconds : play.ticks / 20f;
			com.mystaria.showtime.battle.BattleCameraDirector.onSceneChange(seconds, fieldRadius * camera.reach,
					calm() ? camera.shake * 0.3f : camera.shake, camera.shakeSeconds);
		}
		playCues(Minecraft.getInstance(), 0);
	}

	/** A tick of the transition: its cues, the old props breaking; over once its time is up. */
	private static void tickTransition(Minecraft mc) {
		playCues(mc, ticks);
		int releaseAt = transition.holdOld > 0 ? transition.holdOld
				: transition.cover != null && "spread".equals(transition.cover.mode) ? transition.cover.start + transition.cover.ticks : 0;
		if (ticks >= releaseAt) {
			com.mystaria.showtime.ambiance.blocks.BlockScenery.release();
		}
		for (Transitions.Front front : transition.fronts) {
			runFront(mc, front);
		}
		boolean sounded = false;
		for (Placed placed : LEAVING) {
			Transitions.Leaving how = transition.leaving.get(placed.prop().kind);
			if (how != null && ticks == Math.round(leaveStart(placed, how)) + how.flare) {
				shatter(mc, placed, how, !sounded);
				sounded = how.sound != null;
			}
		}
		if (ticks >= transition.ticks) {
			endTransition();
		}
	}

	private static void endTransition() {
		com.mystaria.showtime.ambiance.blocks.BlockScenery.release();
		transition = null;
		outgoing = null;
		LEAVING.clear();
		LEAVING_VEINS.clear();
	}

	private static void playCues(Minecraft mc, long at) {
		for (Transitions.Cue cue : transition.cues) {
			if (cue.at != at) {
				continue;
			}
			playMoment(mc, cue);
			if (mc.level == null) {
				continue;
			}
			if (cue.flash != null && cue.flashTicks > 0) {
				flashLeft = cue.flashTicks;
				flashTotal = cue.flashTicks;
			}
			if (cue.shake > 0) {
				com.mystaria.showtime.battle.BattleCameraDirector.shake(calm() ? cue.shake * 0.3f : cue.shake, cue.shakeSeconds);
			}
			cueDebris(mc, cue);
			ParticleOptions dust = cue.dust > 0 ? particle(cue.dustParticle) : null;
			if (dust == null) {
				continue;
			}
			int count = calm() ? cue.dust / 2 : cue.dust;
			for (int i = 0; i < count; i++) {
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				double r = Math.sqrt(RANDOM.nextDouble()) * fieldRadius * cue.dustRadius;
				Vec3 at2 = center.add(onGround(Math.cos(angle) * r, Math.sin(angle) * r));
				mc.level.addParticle(dust, at2.x, at2.y + 0.15, at2.z, Math.cos(angle) * 0.04 + (RANDOM.nextDouble() - 0.5) * 0.04,
						0.02 + RANDOM.nextDouble() * 0.05, Math.sin(angle) * 0.04 + (RANDOM.nextDouble() - 0.5) * 0.04);
			}
		}
	}

	/** A tick of a front of particles running over the ground; a sound somewhere on it now and then. */
	private static void runFront(Minecraft mc, Transitions.Front front) {
		if (mc.level == null || ticks < front.start || ticks > front.start + front.ticks) {
			return;
		}
		ParticleOptions particle = particle(front.particle);
		if (particle == null) {
			return;
		}
		float k = Mth.clamp((ticks - front.start) / (float) Math.max(1, front.ticks), 0, 1);
		float run = 1 - (1 - k) * (1 - k);
		double from = fieldRadius * 0.15;
		double reach = front.reach > 0 ? front.reach : fieldRadius * 3;
		double r = front.inward ? reach - (reach - fieldRadius) * run : from + (reach - from) * run;
		int count = (int) Math.min(420, Math.PI * 2 * r * front.density);
		if (calm()) {
			count /= 2;
		}
		float lo = front.height.isEmpty() ? 0 : front.height.get(0);
		float hi = front.height.size() > 1 ? front.height.get(1) : lo;
		double mx = front.motion.size() > 0 ? front.motion.get(0) : 0;
		double my = front.motion.size() > 1 ? front.motion.get(1) : 0;
		double mz = front.motion.size() > 2 ? front.motion.get(2) : 0;
		for (int i = 0; i < count; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			double behind = RANDOM.nextDouble() * front.depth;
			double rr = Math.max(0.5, front.inward ? r + behind : r - behind);
			double x = Math.cos(angle) * rr;
			double z = Math.sin(angle) * rr;
			Vec3 at = center.add(onGround(x, z));
			mc.level.addParticle(particle, at.x, at.y + lo + RANDOM.nextDouble() * (hi - lo), at.z,
					mx + (RANDOM.nextDouble() - 0.5) * 0.02, my, mz + (RANDOM.nextDouble() - 0.5) * 0.02);
		}
		if (front.sound != null && (ticks - front.start) % Math.max(1, front.soundEvery) == 0) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			double rr = Math.min(r, fieldRadius * 1.5);
			Vec3 at = center.add(onGround(Math.cos(angle) * rr, Math.sin(angle) * rr));
			mc.level.playLocalSound(at.x, at.y, at.z, SoundEvent.createVariableRangeEvent(ResourceLocation.parse(front.sound)),
					SoundSource.AMBIENT, front.volume, front.pitch * (0.85f + RANDOM.nextFloat() * 0.3f), false);
		}
	}

	/** A cue's bits of block: falling from above all over the field, and blown out from its centre. */
	private static void cueDebris(Minecraft mc, Transitions.Cue cue) {
		BlockState falling = cue.debris > 0 && cue.debrisBlock != null ? block(cue.debrisBlock) : Blocks.AIR.defaultBlockState();
		if (!falling.isAir()) {
			ParticleOptions bits = new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, falling);
			int count = calm() ? cue.debris / 2 : cue.debris;
			for (int i = 0; i < count; i++) {
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				double r = Math.sqrt(RANDOM.nextDouble()) * fieldRadius * 1.1;
				Vec3 at = center.add(onGround(Math.cos(angle) * r, Math.sin(angle) * r));
				mc.level.addParticle(bits, at.x, at.y + 4 + RANDOM.nextDouble() * 6, at.z, (RANDOM.nextDouble() - 0.5) * 0.05,
						-0.2 - RANDOM.nextDouble() * 0.3, (RANDOM.nextDouble() - 0.5) * 0.05);
			}
		}
		BlockState blown = cue.burst > 0 && cue.burstBlock != null ? block(cue.burstBlock) : Blocks.AIR.defaultBlockState();
		if (!blown.isAir()) {
			ParticleOptions bits = new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, blown);
			int count = calm() ? cue.burst / 2 : cue.burst;
			for (int i = 0; i < count; i++) {
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				double speed = 0.4 + RANDOM.nextDouble() * 0.6;
				Vec3 at = center.add(onGround(Math.cos(angle) * 0.8, Math.sin(angle) * 0.8));
				mc.level.addParticle(bits, at.x, at.y + 0.4 + RANDOM.nextDouble(), at.z, Math.cos(angle) * speed,
						0.25 + RANDOM.nextDouble() * 0.45, Math.sin(angle) * speed);
			}
		}
	}

	/** When an old prop starts to leave (its flare), in the transition's ticks: one after another over the spread. */
	private static double leaveStart(Placed placed, Transitions.Leaving how) {
		return how.spread * hashFloat(placed.index() * 53 + 7);
	}

	/** An old crystal breaks: shards of its block fly, a sparkle, its sound (one a tick at most). */
	private static void shatter(Minecraft mc, Placed placed, Transitions.Leaving how, boolean sound) {
		if (mc.level == null) {
			return;
		}
		Vec3 mid = center.add(placed.base()).add(placed.dir().scale(placed.length() * 0.4));
		BlockState state = how.shards != null ? block(how.shards) : Blocks.AIR.defaultBlockState();
		if (!state.isAir()) {
			ParticleOptions shards = new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, state);
			int count = calm() ? how.shardCount / 2 : how.shardCount;
			for (int i = 0; i < count; i++) {
				mc.level.addParticle(shards, mid.x + RANDOM.nextGaussian() * 0.3, mid.y + RANDOM.nextGaussian() * 0.3,
						mid.z + RANDOM.nextGaussian() * 0.3, RANDOM.nextGaussian() * 0.15, 0.1 + RANDOM.nextDouble() * 0.2,
						RANDOM.nextGaussian() * 0.15);
			}
		}
		ParticleOptions sparkle = particle(how.sparkle);
		for (int i = 0; sparkle != null && i < 5; i++) {
			mc.level.addParticle(sparkle, mid.x + RANDOM.nextGaussian() * 0.4, mid.y + RANDOM.nextGaussian() * 0.4,
					mid.z + RANDOM.nextGaussian() * 0.4, 0, 0.02, 0);
		}
		if (sound && how.sound != null) {
			mc.level.playLocalSound(mid.x, mid.y, mid.z, SoundEvent.createVariableRangeEvent(ResourceLocation.parse(how.sound)),
					SoundSource.AMBIENT, how.volume, 0.75f + RANDOM.nextFloat() * 0.5f, false);
		}
	}

	/**
	 * A transition's sheet over the sea's water (2026-11-20): ice freezing over, a slick spreading, ice thawing back.
	 * Its front runs out from the field, jagged by noise, a bright line on it; patches of its colours with a grain.
	 */
	private static void drawCover(PoseStack pose, MultiBufferSource buffers, Transitions.Cover cover, float time) {
		Ambiance.Sea sea = active.sea != null ? active.sea : outgoing != null ? outgoing.sea : null;
		if (sea == null || sea.under || cover.colors.isEmpty()) {
			return;
		}
		boolean spread = !"retreat".equals(cover.mode);
		float k = Mth.clamp((time - cover.start) / Math.max(1, cover.ticks), 0, 1);
		if (spread && time < cover.start) {
			return;
		}
		float run = 1 - (1 - k) * (1 - k);
		float shown = 1;
		if (spread && k >= 1) {
			shown = 1 - Mth.clamp((time - cover.start - cover.ticks) / Math.max(1, cover.fade), 0, 1);
		}
		if (shown <= 0 || !spread && k >= 1) {
			return;
		}
		double from = fieldRadius + 0.5;
		double reach = Math.min(sea.radius, cover.reach > 0 ? cover.reach : sea.radius);
		double front = from - 3 + (reach - from + 6) * run;
		double y = Math.round(center.y + groundAt(0, 0)) - center.y + 0.04;
		int[] colors = cover.colors.stream().mapToInt(c -> Ambiance.color(c, 0xFFFFFF)).toArray();
		int edge = cover.edge != null ? Ambiance.color(cover.edge, 0xFFFFFF) : -1;
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.TINT);
		Matrix4f m = pose.last().pose();
		List<Double> radii = seaRadii(from, from + 24, reach);
		int segments = 128;
		for (int i = 0; i + 1 < radii.size(); i++) {
			double r0 = radii.get(i);
			double r1 = radii.get(i + 1);
			if (spread ? r0 > front + 3 : r1 < front - 3) {
				continue;
			}
			for (int j = 0; j < segments; j++) {
				double a0 = Math.PI * 2 * j / segments;
				double a1 = Math.PI * 2 * (j + 1) / segments;
				coverCorner(out, m, cover, colors, edge, r0, a0, y, front, spread, shown, time);
				coverCorner(out, m, cover, colors, edge, r0, a1, y, front, spread, shown, time);
				coverCorner(out, m, cover, colors, edge, r1, a1, y, front, spread, shown, time);
				coverCorner(out, m, cover, colors, edge, r1, a0, y, front, spread, shown, time);
			}
		}
	}

	private static void coverCorner(VertexConsumer out, Matrix4f m, Transitions.Cover cover, int[] colors, int edge, double r,
			double a, double y, double front, boolean spread, float shown, float time) {
		double x = Math.cos(a) * r;
		double z = Math.sin(a) * r;
		// The front, jagged: fingers of ice or slick running ahead here and lagging there.
		double jag = 5 * (valueNoise(Math.cos(a) * 2.5 + 7, 3, Math.sin(a) * 2.5 + 7) - 0.5)
				+ 1.5 * (valueNoise(Math.cos(a) * 9 + 3, 5, Math.sin(a) * 9 + 3) - 0.5);
		double inside = spread ? front + jag - r : r - (front + jag);
		float cover01 = (float) Mth.clamp(inside / 1.6, 0, 1);
		int alpha = Math.round(cover.alpha * cover01 * shown);
		double patch = valueNoise(x * 0.09, 11, z * 0.09);
		int color = colors[Math.min(colors.length - 1, (int) (patch * colors.length))];
		if (colors.length > 1) {
			double blend = valueNoise(x * 0.23 + 40, 13, z * 0.23);
			color = lerpColor(color, colors[(int) (blend * colors.length) % colors.length], 0.4f);
		}
		float grain = (float) (valueNoise(x * 1.7, 17, z * 1.7) - 0.5) * 0.18f;
		color = grain > 0 ? lerpColor(color, 0xFFFFFF, grain) : lerpColor(color, 0x000000, -grain);
		if (edge >= 0 && inside > -1 && inside < 2.5) {
			float glow = (float) (1 - Math.abs(inside - 0.7) / 1.8);
			if (glow > 0) {
				color = lerpColor(color, edge, glow * 0.85f);
				alpha = Math.max(alpha, Math.round(cover.alpha * glow * shown));
			}
		}
		vertex(out, m, new Vec3(x, y, z), color, alpha);
	}

	/** How much of the old sky and fog still shows: 1 as the transition starts, 0 once blended into the new. */
	private static float oldShows() {
		if (transition == null || outgoing == null || transition.blend <= 0) {
			return 0;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float k = Mth.clamp((ticks + partial) / transition.blend, 0, 1);
		return 1 - k * k * (3 - 2 * k);
	}

	/** How far the new ambiance's mist has risen: 1 outside a transition. */
	private static float arrived() {
		if (transition == null || transition.arrive <= 0) {
			return 1;
		}
		float k = Mth.clamp(ticks / (float) transition.arrive, 0, 1);
		return k * k * (3 - 2 * k);
	}

	/**
	 * A battle seen from afar (Phantasmon's field view, not watched): its ambiance — {@code id}, null or empty for this
	 * player's own — restricted to its field, whose sides {@code spots} gives. Never over this player's own battle.
	 */
	public static void startDistant(String id, java.util.function.Supplier<List<Vec3>> spots) {
		if (active() && !distant && area == null) {
			return;
		}
		String[] want = scene(id == null || id.isEmpty() ? null : id, null);
		begin(Ambiances.resolve(want[1], want[0]), List.of(), 0);
		owned = true;
		if (active == null) {
			return;
		}
		distant = true;
		distantSpots = spots;
		inside = true; // corrected at the first tick: off if the player stands outside
		updateField(Minecraft.getInstance().level);
		placeProps(Minecraft.getInstance().level, active); // the field is only known now
	}

	/** The player's own ambiance, or none once it is reserved and no longer unlocked for them. */
	private static String ownChoice() {
		String[] biome = biomeScene();
		if (biome != null) {
			return biome[1];
		}
		String id = ShowtimeConfig.ambiance();
		return Ambiances.usable(id) ? id : NONE;
	}

	/** The scene of the place ({@code [stadium, ambiance]}) when "Scene by biome" is on; null: the player's own. */
	private static String[] biomeScene() {
		return com.mystaria.showtime.api.ShowtimeHooks.biomeScenesOn() ? BiomeScenes.scene() : null;
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

	// ---------------------------------------------------------------- an area's ambiance (2026-11-19)

	/**
	 * The ambiance of the place the player stands in (ShowtimeHooks.setAreaAmbiance: a Phantasmon Events hub), asked
	 * twice a second while nothing else holds the ambiance: started when the player walks in, changed when the place's
	 * changes, ended when they walk out. A battle takes over; the place's comes back after it.
	 */
	private static void followArea(Minecraft mc) {
		if (owned || mc.level == null || mc.player == null || ++areaTicks % 10 != 0) {
			return;
		}
		com.mystaria.showtime.api.AmbianceArea wanted = com.mystaria.showtime.api.ShowtimeHooks.areaAmbiance();
		if (wanted != null && (wanted.ambiance() == null || wanted.ambiance().isEmpty()
				|| !wanted.contains(mc.player.getX(), mc.player.getZ()))) {
			wanted = null;
		}
		String key = wanted == null ? null : wanted.toString();
		if (java.util.Objects.equals(key, areaKey)) {
			return;
		}
		if (area != null) {
			end();
		}
		if (wanted == null) {
			return;
		}
		begin(Ambiances.resolve(wanted.ambiance(), NONE), List.of(), 0);
		areaKey = key; // even when unknown: not asked again until the place changes
		if (active == null) {
			return;
		}
		area = wanted;
		inside = true;
		TILE_LEVELS.clear();
		double[] middle = tileLevels(mc.level, 0, 0);
		areaAnchorY = middle != null && middle.length > 0 ? middle[0] : wanted.bottomY();
		// Each tile is a battle field's size: its effects as dense as in a battle.
		fieldRadius = (float) (AREA_TILE / 2);
		center = null;
		trackArea(mc);
		placeProps(mc.level, active);
	}

	/**
	 * The area's field follows the player from tile to tile of its fixed grid (what is drawn is drawn on every tile
	 * near them, so nothing moves), and stands on the floor the player stands on: of two platforms one above the other,
	 * the one they are on, never the top of the world.
	 */
	private static void trackArea(Minecraft mc) {
		if (area == null || mc.player == null || mc.level == null) {
			return;
		}
		double tx = area.centerX() + Math.round((mc.player.getX() - area.centerX()) / AREA_TILE) * AREA_TILE;
		double tz = area.centerZ() + Math.round((mc.player.getZ() - area.centerZ()) / AREA_TILE) * AREA_TILE;
		// In the air (flying, jumping, falling): the floor last stood on, so nothing moves under a flying player; the
		// first time, the floor far below (never the player's height in the air).
		double floor;
		if (center != null && !mc.player.onGround()) {
			floor = center.y;
		} else {
			floor = areaFloor(mc.level, mc.player.getX(), mc.player.getZ(), mc.player.getY() + 0.5, Double.NaN, 64);
			if (Double.isNaN(floor)) {
				floor = center != null ? center.y : mc.player.getY();
			}
		}
		if (center == null || Math.abs(floor - center.y) > 1.5) {
			center = new Vec3(tx, floor, tz);
			clearFloors(); // the ground's heights are kept relative to the field's height
		} else if (tx != center.x || tz != center.z) {
			center = new Vec3(tx, center.y, tz);
		}
	}

	/**
	 * The floor at a column of the world under {@code from} (searched 16 blocks down): the top of the first block one
	 * stands on; {@code fallback} where there is none, or the chunk is not loaded.
	 */
	private static double areaFloor(ClientLevel level, double x, double z, double from, double fallback) {
		return areaFloor(level, x, z, from, fallback, 16);
	}

	private static double areaFloor(ClientLevel level, double x, double z, double from, double fallback, int depth) {
		int bx = Mth.floor(x);
		int bz = Mth.floor(z);
		if (!level.hasChunk(bx >> 4, bz >> 4)) {
			return fallback;
		}
		net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
		int top = Mth.floor(from);
		for (int y = top; y >= top - depth; y--) {
			pos.set(bx, y, bz);
			var shape = level.getBlockState(pos).getCollisionShape(level, pos);
			if (!shape.isEmpty()) {
				return y + shape.max(net.minecraft.core.Direction.Axis.Y);
			}
		}
		return fallback;
	}

	/** The tiles of the area near the camera (the field's own centre first); just the field outside an area. */
	private static void areaTiles(Vec3 camera) {
		TILES.clear();
		if (area == null) {
			TILES.add(center);
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		if (ticks % 100 == 0) {
			TILE_LEVELS.clear(); // built or broken meanwhile
		}
		int reach = (int) Math.ceil(AREA_DRAW / AREA_TILE);
		int ci = (int) Math.round(areaU(camera.x, camera.z) / AREA_TILE);
		int ck = (int) Math.round(areaV(camera.x, camera.z) / AREA_TILE);
		for (int i = ci - reach; i <= ci + reach; i++) {
			for (int k = ck - reach; k <= ck + reach; k++) {
				double x = area.centerX() + i * AREA_TILE;
				double z = area.centerZ() + k * AREA_TILE;
				if (Math.hypot(x - camera.x, z - camera.z) > AREA_DRAW || !area.contains(x, z)) {
					continue;
				}
				double[] floors = tileLevels(level, i, k);
				if (floors == null) {
					continue;
				}
				for (double y : floors) {
					Vec3 tile = new Vec3(x, y, z);
					if (tile.distanceTo(camera) <= AREA_DRAW) {
						TILES.add(tile);
					}
				}
			}
		}
		if (TILES.size() > AREA_MAX_TILES) {
			TILES.sort(java.util.Comparator.comparingDouble(t -> t.distanceToSqr(camera)));
			TILES.subList(AREA_MAX_TILES, TILES.size()).clear();
		}
	}

	/** The grid's axes are the world's: a tile's index along x and z from the area's centre. */
	private static double areaU(double x, double z) {
		return x - area.centerX();
	}

	private static double areaV(double x, double z) {
		return z - area.centerZ();
	}

	/**
	 * The floors of the tile (i, k) of the area, low to high: a top one stands on with two blocks of air over it, not
	 * under a liquid, read on nine columns of the tile; floors within 2.5 blocks of each other are one. Null while the
	 * chunk is not loaded. The same for everyone, wherever they stand: what is drawn on it never moves.
	 */
	private static double[] tileLevels(ClientLevel level, int i, int k) {
		long key = ((long) i << 32) ^ (k & 0xFFFFFFFFL);
		double[] cached = TILE_LEVELS.get(key);
		if (cached != null) {
			return cached;
		}
		double cx = area.centerX() + i * AREA_TILE;
		double cz = area.centerZ() + k * AREA_TILE;
		if (!level.hasChunk(Mth.floor(cx) >> 4, Mth.floor(cz) >> 4)) {
			return null;
		}
		List<Double> found = new ArrayList<>();
		net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
		int top = Mth.floor(area.topY());
		int bottom = Math.max(level.getMinBuildHeight(), Mth.floor(area.bottomY()) - 1);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				double x = cx + dx * AREA_TILE / 3;
				double z = cz + dz * AREA_TILE / 3;
				if (!area.contains(x, z) || !level.hasChunk(Mth.floor(x) >> 4, Mth.floor(z) >> 4)) {
					continue;
				}
				int bx = Mth.floor(x);
				int bz = Mth.floor(z);
				int air = 0;
				for (int y = top; y >= bottom; y--) {
					pos.set(bx, y, bz);
					BlockState state = level.getBlockState(pos);
					var shape = state.getCollisionShape(level, pos);
					if (shape.isEmpty()) {
						air = state.getFluidState().isEmpty() ? air + 1 : -1000; // under a liquid: no floor
						continue;
					}
					if (air >= 2) {
						found.add(y + shape.max(net.minecraft.core.Direction.Axis.Y));
					}
					air = 0;
				}
			}
		}
		found.sort(null);
		List<Double> floors = new ArrayList<>();
		for (double y : found) {
			if (floors.isEmpty() || y - floors.get(floors.size() - 1) > 2.5) {
				floors.add(y);
			}
		}
		double[] result = floors.stream().mapToDouble(Double::doubleValue).toArray();
		TILE_LEVELS.put(key, result);
		return result;
	}

	/**
	 * Draws {@code draw} on every tile of {@link #TILES} (once outside an area): the field's centre moved to the tile's
	 * for the time of it, and each tile's time shifted so the tiles do not move in step.
	 */
	private static void eachTile(PoseStack pose, Vec3 camera, Runnable draw) {
		if (area == null) {
			tileShift = 0;
			draw.run();
			return;
		}
		Vec3 home = center;
		Vec3 homeRel = cameraRel;
		for (Vec3 tile : TILES) {
			center = tile;
			cameraRel = camera.subtract(tile);
			// The tile's own, whoever stands where: a tile never jumps in time when the player walks onto another.
			tileShift = hashFloat((int) Math.round(tile.x / AREA_TILE) * 7919 + (int) Math.round(tile.z / AREA_TILE) * 104729) * 2400f;
			double far = tile.distanceTo(camera);
			tileFade = (float) Mth.clamp((AREA_DRAW - far) / (AREA_DRAW - AREA_FADE), 0, 1);
			pose.pushPose();
			pose.translate(tile.x - home.x, tile.y - home.y, tile.z - home.z);
			draw.run();
			pose.popPose();
		}
		center = home;
		cameraRel = homeRel;
		tileShift = 0;
		tileFade = 1;
	}

	/** The menu's preview: around the player for 30 s ({@code none} stops it). */
	public static void preview(String id) {
		Minecraft mc = Minecraft.getInstance();
		BiomeScenes.forget();
		begin(Ambiances.resolve(id, ownStadium()), mc.player == null ? List.of() : List.of(mc.player.getUUID()), PREVIEW_TICKS);
		owned = true;
	}

	/** The menu's preview is over: ended, but never a battle's ambiance (one seen from afar included). */
	public static void endPreview() {
		if (previewLeft > 0) {
			end();
		}
	}

	public static void end() {
		end(true);
	}

	/** {@code forgetBlocks} false: the stadium's blocks stand in until the next ambiance's are built (a transition). */
	private static void end(boolean forgetBlocks) {
		endTransition();
		followed = null;
		followedId = null;
		owned = false;
		area = null;
		areaKey = null;
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
		ROCKS.clear();
		quakeUntil = 0;
		PLACED.clear();
		anchors = null;
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
		frustum = null;
		landInBlocks = false;
		if (forgetBlocks) {
			com.mystaria.showtime.ambiance.blocks.BlockScenery.clear();
		} else {
			com.mystaria.showtime.ambiance.blocks.BlockScenery.holdOver();
		}
	}

	public static boolean active() {
		return active != null && center != null;
	}

	private static void begin(Ambiance ambiance, Collection<UUID> battleTrainers, int preview) {
		begin(ambiance, battleTrainers, preview, false);
	}

	/** {@code samePlace}: the stadium stays the same, its blocks kept until the new ambiance's are built. */
	private static void begin(Ambiance ambiance, Collection<UUID> battleTrainers, int preview, boolean samePlace) {
		end(!samePlace);
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
		placeProps(mc.level, ambiance);
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
		clearFloors();
		if (ambiance.sound != null && ambiance.sound.id != null) {
			sound = new LoopSound(ResourceLocation.parse(ambiance.sound.id), ambiance.sound.volume, ambiance.sound.pitch);
			mc.getSoundManager().play(sound);
		}
	}

	/** Every client tick: the field follows the trainers, particles, floating blocks' flames, lightning. */
	public static void tick() {
		// Ground heights are relative to the field's centre: cleared when it moves (updateField), and now and then
		// for blocks changed under it.
		if (ticks % 200 == 0) {
			clearFloors();
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null && !mc.isPaused()) {
			followOverride();
			followArea(mc);
		}
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
		trackArea(mc);
		if (center == null) {
			return;
		}
		if (transition != null) {
			tickTransition(mc);
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
		if (!ROCKS.isEmpty() || ticks < quakeUntil) {
			landRocks(mc);
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

	/**
	 * A moment of the battle: {@code send_out}, {@code attack}, {@code crit}, {@code faint} or {@code victory}, or one
	 * of the ambiance's {@code named} moments (another mod's: Phantasmon Events' {@code cave_collapse}).
	 */
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
			default -> active.moments.named == null ? null : active.moments.named.get(kind);
		};
		playMoment(mc, moment);
	}

	/** A moment's effects: one of the battle's, or a transition's cue. */
	private static void playMoment(Minecraft mc, Ambiance.Moment moment) {
		if (moment == null || mc.level == null) {
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
		if (moment.rockfall > 0) {
			rockfall(calm() ? Math.max(1, moment.rockfall / 2) : moment.rockfall);
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
			flashTotal = FLASH_TICKS;
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
		if (area != null) {
			return; // an area's field never moves
		}
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
		// The field stays put (2026-11-19): it moved with every step the trainers took, and the whole place was built
		// again under them. It follows them only once they are 4 blocks off (a jump does not lift it), and its size
		// changes by steps of 4.
		Vec3 middle = sum.scale(1.0 / spots.size());
		Vec3 target = new Vec3(middle.x, lowest, middle.z);
		if (center == null || Math.hypot(target.x - center.x, target.z - center.z) >= FIELD_STEP || target.y < center.y - 0.5
				|| target.y > center.y + 1.5) {
			center = target;
			clearFloors();
		}
		float apart = 0f;
		for (Vec3 spot : spots) {
			apart = Math.max(apart, (float) spot.distanceTo(center));
		}
		float wanted = Math.max(8f, apart + 5f);
		if (wanted > fieldRadius || wanted < fieldRadius - FIELD_STEP) {
			fieldRadius = (float) (Math.ceil(wanted / FIELD_STEP) * FIELD_STEP);
			clearFloors();
		}
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
		return skyShown(world, sky -> sky.skyColor);
	}

	public static Vec3 cloudColor(Vec3 world) {
		return skyShown(world, sky -> sky.cloudColor);
	}

	/** The fog colour over the world's ({@code rgb} 0..1, changed in place). */
	public static void fogColor(float[] rgb) {
		if (!global()) {
			return;
		}
		Vec3 blended = skyShown(new Vec3(rgb[0], rgb[1], rgb[2]), sky -> sky.fogColor);
		rgb[0] = (float) blended.x;
		rgb[1] = (float) blended.y;
		rgb[2] = (float) blended.z;
	}

	/** Fog start / end in blocks, or NaN for the world's. */
	public static float fogStart() {
		return fogShown(sky -> sky.fogStart);
	}

	public static float fogEnd() {
		return fogShown(sky -> sky.fogEnd);
	}

	/** A sky colour (one of {@code pick}) over the world's: the ambiance's, the old one's still showing in a transition. */
	private static Vec3 skyShown(Vec3 world, java.util.function.Function<Ambiance.Sky, String> pick) {
		if (!global()) {
			return world;
		}
		Vec3 now = active.sky != null ? blend(world, active.sky, pick.apply(active.sky)) : world;
		float old = oldShows();
		if (old <= 0) {
			return now;
		}
		Vec3 was = outgoing.sky != null ? blend(world, outgoing.sky, pick.apply(outgoing.sky)) : world;
		return now.lerp(was, old);
	}

	private static float fogShown(java.util.function.Function<Ambiance.Sky, Float> pick) {
		if (!global() || active.sky == null || pick.apply(active.sky) == null) {
			return Float.NaN;
		}
		float now = pick.apply(active.sky);
		float old = oldShows();
		Float was = old > 0 && outgoing.sky != null ? pick.apply(outgoing.sky) : null;
		return was == null ? now : Mth.lerp(old, now, was);
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

	private static Vec3 blend(Vec3 world, Ambiance.Sky sky, String hex) {
		if (hex == null) {
			return world;
		}
		int rgb = Ambiance.color(hex, 0);
		Vec3 target = new Vec3(((rgb >> 16) & 255) / 255.0, ((rgb >> 8) & 255) / 255.0, (rgb & 255) / 255.0);
		return world.lerp(target, Mth.clamp(sky.strength, 0f, 1f));
	}

	// ---------------------------------------------------------------- drawing

	/** World pass (after the entities): backdrop, stars, rainbow, searchlights, floating blocks. */
	/**
	 * In an area, the scene is drawn from the place's own centre (on the floor the player stands on), never from the
	 * player's tile: what is drawn once (spotlights, the sky's) stays put while they walk or fly; the tiles are drawn
	 * where they are.
	 */
	public static void render(WorldRenderContext context) {
		if (area == null || center == null) {
			renderScene(context);
			return;
		}
		Vec3 tile = center;
		center = new Vec3(area.centerX(), areaAnchorY, area.centerZ());
		try {
			renderScene(context);
		} finally {
			center = tile;
		}
	}

	private static void renderScene(WorldRenderContext context) {
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
		cameraWorld = camera;
		frustum = context.frustum();
		areaTiles(camera);
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
		if (active.islands != null || active.island != null || active.crowd != null || active.lightTowers != null
				|| active.cavern != null || active.sea != null || active.land != null || active.hall != null
				|| active.grass != null || active.flowers != null || !PLACED.isEmpty() || !LEAVING.isEmpty() || !ROCKS.isEmpty()) {
			// Solid scenery first, writing depth: the glows drawn after are hidden behind it where they should be.
			buffers.endBatch();
			landInBlocks = false;
			seaInBlocks = false;
			caveInBlocks = false;
			hallInBlocks = false;
			if (active.cavern != null) {
				drawCavern(pose, buffers, active.cavern, time, false);
			}
			if (active.sea != null) {
				drawSea(pose, buffers, active.sea, time, false);
			}
			if (active.land != null) {
				drawLand(pose, buffers, active.land, time, false);
			}
			if (active.hall != null) {
				drawHall(pose, buffers, active.hall, time, false);
			}
			if (active.grass != null && !landInBlocks) {
				eachTile(pose, camera, () -> drawGrass(pose, buffers, active.grass, time + tileShift));
			}
			if (active.flowers != null && !landInBlocks) {
				eachTile(pose, camera, () -> drawFlowers(pose, buffers, active.flowers, time + tileShift));
			}
			drawProps(pose, buffers, time, false);
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
			if (active.hall != null && active.hall.mirrors && mirrorsLive(active.hall)) {
				if (hallInBlocks) {
					drawBlockReflections(pose, buffers, active.hall, partialTick);
				} else {
					drawReflections(pose, buffers, active.hall, time, partialTick);
				}
			}
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
			eachTile(pose, camera, () -> drawFloorLights(pose, buffers, active.floorLights, time + tileShift));
		}
		if (active.danceFloor != null) {
			eachTile(pose, camera, () -> drawDanceFloor(pose, buffers, active.danceFloor, time + tileShift));
		}
		if (active.neonGrid != null) {
			eachTile(pose, camera, () -> drawNeonGrid(pose, buffers, active.neonGrid, time + tileShift));
		}
		if (active.mirrorBall != null) {
			eachTile(pose, camera, () -> drawMirrorBallDots(pose, buffers, active.mirrorBall, time + tileShift));
		}
		if (active.sprites != null) {
			eachTile(pose, camera, () -> drawSprites(pose, buffers, active.sprites, time + tileShift));
		}
		if (active.veins != null) {
			eachTile(pose, camera, () -> drawVeins(pose, buffers, active.veins, VEINS, time + tileShift, 1f));
		}
		if (transition != null && outgoing != null && outgoing.veins != null && !LEAVING_VEINS.isEmpty()) {
			// The old veins die out, flickering.
			float k = Mth.clamp(time / Math.max(1, transition.veinsFade), 0, 1);
			float fade = (1 - k) * (1 - k) * (0.7f + 0.3f * Mth.sin(time * 2.3f));
			if (fade > 0.01f) {
				eachTile(pose, camera, () -> drawVeins(pose, buffers, outgoing.veins, LEAVING_VEINS, time + tileShift, fade));
			}
		}
		if (transition != null && transition.cover != null) {
			drawCover(pose, buffers, transition.cover, time);
		}
		if (active.skyCracks != null) {
			drawSkyCracks(pose, buffers, active.skyCracks, time);
		}
		if (active.wisps != null) {
			eachTile(pose, camera, () -> drawWisps(pose, buffers, active.wisps, time + tileShift));
		}
		if (active.bubbles != null) {
			eachTile(pose, camera, () -> drawBubbles(pose, buffers, active.bubbles, time + tileShift));
		}
		if (active.caustics != null) {
			eachTile(pose, camera, () -> drawCaustics(pose, buffers, active.caustics, time + tileShift));
		}
		if (active.glints != null) {
			eachTile(pose, camera, () -> drawGlints(pose, buffers, active.glints, time + tileShift));
		}
		if (active.islands != null) {
			drawIslands(pose, buffers, active.islands, time, true);
		}
		if (active.island != null) {
			drawFieldIsland(pose, buffers, active.island, time, true);
		}
		if (active.cavern != null) {
			drawCavern(pose, buffers, active.cavern, time, true);
		}
		if (active.sea != null) {
			drawSea(pose, buffers, active.sea, time, true);
		}
		if (active.land != null) {
			drawLand(pose, buffers, active.land, time, true);
		}
		if (active.hall != null) {
			drawHall(pose, buffers, active.hall, time, true);
		}
		if (!PLACED.isEmpty() || !LEAVING.isEmpty()) {
			drawProps(pose, buffers, time, true);
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
			eachTile(pose, camera, () -> drawShafts(pose, buffers, active.shafts, time + tileShift));
		}
		if (active.embers != null) {
			eachTile(pose, camera, () -> drawEmbers(pose, buffers, active.embers, time + tileShift));
		}
		if (active.lanterns != null) {
			eachTile(pose, camera, () -> drawLanterns(pose, buffers, active.lanterns, time + tileShift, true));
		}
		if (active.mushrooms != null) {
			eachTile(pose, camera, () -> drawMushrooms(pose, buffers, active.mushrooms, time + tileShift, true));
		}
		if (active.glitchCubes != null) {
			eachTile(pose, camera, () -> drawGlitchCubes(pose, buffers, active.glitchCubes, true));
		}
		if (active.rifts != null) {
			eachTile(pose, camera, () -> drawRifts(pose, buffers, active.rifts, partialTick));
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
			eachTile(pose, camera, () -> drawMirrorBall(pose, buffers, active.mirrorBall, time + tileShift));
		}
		if (active.critters != null) {
			eachTile(pose, camera, () -> drawCritters(pose, buffers, active.critters, time + tileShift));
		}
		if (active.glitchCubes != null) {
			eachTile(pose, camera, () -> drawGlitchCubes(pose, buffers, active.glitchCubes, false));
		}
		if (active.ghosts != null) {
			eachTile(pose, camera, () -> drawGhosts(pose, buffers, active.ghosts, time + tileShift));
		}
		if (active.mushrooms != null) {
			eachTile(pose, camera, () -> drawMushrooms(pose, buffers, active.mushrooms, time + tileShift, false));
		}
		if (active.kelp != null) {
			eachTile(pose, camera, () -> drawKelp(pose, buffers, active.kelp, time + tileShift));
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
			eachTile(pose, camera, () -> drawLanterns(pose, buffers, active.lanterns, time + tileShift, false));
		}
		if (active.mist != null) {
			eachTile(pose, camera, () -> drawMist(pose, buffers, active.mist, time + tileShift));
		}
		buffers.endBatch();
		if (floatingState != null) {
			eachTile(pose, camera, () -> drawFloating(pose, buffers, camera, partialTick));
			buffers.endBatch();
		}
		pose.popPose();
	}

	/** The floating blocks around the field. */
	private static void drawFloating(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 camera, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		{
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
		}
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
		int alpha = Math.round((calm() ? 45f : 150f) * Math.min(1f, flashLeft / (float) Math.min(FLASH_TICKS, flashTotal)));
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
			VertexConsumer glow = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
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
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
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
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
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
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
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
			if (hole(at)) {
				continue;
			}
			int color = Ambiance.color(lights.colors.get(i % lights.colors.size()), 0xFFFFFF);
			floorDisc(out, m, at.x, at.z, lights.size, color, lights.alpha, 0.12);
		}
	}

	/** Ground heights by block column, relative to the field's centre, for this frame (cleared each tick). */
	private static final java.util.Map<Long, Double> GROUND = new java.util.HashMap<>();

	/**
	 * A point at {@code (x, z)} from the field's centre, just over the floor people see there ({@link #surfaceY}).
	 */
	private static Vec3 onGround(double x, double z) {
		return new Vec3(x, surfaceY(x, z) + 0.12, z);
	}

	/**
	 * The top of the floor people see at {@code (x, z)}: the stadium's ground where one is shown, over the sea its
	 * surface (2026-11-20: past the field the layers followed the hidden world's ground, floating over the stadium's
	 * hills or sunk into them, which made the mist fold into odd shapes); the world's ground otherwise, the top of the
	 * first solid block below the field's height (from a little above it, so a step up counts). No ground within reach:
	 * the centre's height.
	 */
	private static double surfaceY(double x, double z) {
		if (active == null || active.land == null && active.sea == null && active.hall == null) {
			return groundAt(x, z);
		}
		double y = floorY(x, z) - 0.09;
		return active.sea != null && !active.sea.under ? Math.max(y, seaLevel()) : y;
	}

	private static double groundAt(double x, double z) {
		double found = groundOrHole(x, z);
		return Double.isNaN(found) ? 0 : found;
	}

	/**
	 * Whether there is no floor to lay anything on at {@code (x, z)} from the field's centre: in an area only (a hub is
	 * built, with voids, pools and walls in it; a battle field keeps its old ways). Nothing that stands on the ground is
	 * drawn there, nor what rises from it.
	 */
	private static boolean hole(double x, double z) {
		return area != null && Double.isNaN(groundOrHole(x, z));
	}

	private static boolean hole(Vec3 at) {
		return hole(at.x, at.z);
	}

	/** {@link #groundAt}, NaN for a hole: in an area, no floor within reach, a liquid's surface, or inside a wall. */
	private static double groundOrHole(double x, double z) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || center == null) {
			return 0;
		}
		int bx = Mth.floor(center.x + x);
		int bz = Mth.floor(center.z + z);
		long key = ((long) bx << 32) ^ (bz & 0xFFFFFFFFL);
		key = key * 31 + Mth.floor(center.y * 4);
		Double cached = GROUND.get(key);
		if (cached != null) {
			return cached - center.y;
		}
		boolean inArea = area != null;
		double found = inArea ? Double.NaN : 0;
		// In an area, a step up of two blocks at most: higher is a wall or the platform over the player's.
		int top = Mth.floor(center.y) + (inArea ? 2 : 4);
		net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
		// In an area, the tile's floor only (another floor is another tile's): two blocks up, three and a half down.
		int lowest = inArea ? Mth.floor(center.y - 3.5) : top - 16;
		for (int y = top; y >= lowest; y--) {
			pos.set(bx, y, bz);
			BlockState state = level.getBlockState(pos);
			if (inArea && !state.getFluidState().isEmpty()) {
				break; // water, lava: nothing grows on it
			}
			var shape = state.getCollisionShape(level, pos);
			if (!shape.isEmpty()) {
				if (!inArea || y < top) {
					found = y + shape.max(net.minecraft.core.Direction.Axis.Y) - center.y;
				}
				break;
			}
		}
		GROUND.put(key, found + center.y);
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
			VertexConsumer glow = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
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
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
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
			VertexConsumer glow = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
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
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
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
		if (hole(x0, z0) || hole(x1, z0) || hole(x1, z1) || hole(x0, z1)) {
			return;
		}
		decal(out, m, new double[] {x0, x1, x1, x0}, new double[] {z0, z0, z1, z1}, new float[] {alpha, alpha, alpha, alpha}, 4,
				color, 0.12);
	}

	/** A thin line on the ground, from one point to another, {@code width} wide. */
	private static void groundLine(VertexConsumer out, Matrix4f m, double x0, double z0, double x1, double z1, double width, int color,
			int alpha0, int alpha1) {
		double dx = x1 - x0;
		double dz = z1 - z0;
		double length = Math.sqrt(dx * dx + dz * dz);
		double nx = -dz / length * width / 2;
		double nz = dx / length * width / 2;
		if (hole(x0, z0) || hole(x1, z1)) {
			return;
		}
		decal(out, m, new double[] {x0 + nx, x1 + nx, x1 - nx, x0 - nx}, new double[] {z0 + nz, z1 + nz, z1 - nz, z0 - nz},
				new float[] {alpha0, alpha1, alpha1, alpha0}, 4, color, 0.12);
	}
	/** Scratch room for {@link #decal}'s clipping (client thread). */
	private static final double[][] CLIP_X = new double[2][24];
	private static final double[][] CLIP_Z = new double[2][24];
	private static final float[][] CLIP_A = new float[2][24];

	/**
	 * A flat shape on the floor (2026-11-20: the shapes were laid point by point on the ground, so across a step they
	 * slanted into it or floated over the hollow, with hard lines where they went into the blocks): cut along the
	 * blocks' edges, each piece flat on its own block's top, {@code lift} over it. {@code xs, zs, alphas}: a convex polygon's {@code n} corners (at most 8) from the
	 * field's centre and their opacity, blended across it.
	 */
	private static void decal(VertexConsumer out, Matrix4f m, double[] xs, double[] zs, float[] alphas, int n, int color, double lift) {
		double minX = xs[0];
		double maxX = xs[0];
		double minZ = zs[0];
		double maxZ = zs[0];
		for (int i = 1; i < n; i++) {
			minX = Math.min(minX, xs[i]);
			maxX = Math.max(maxX, xs[i]);
			minZ = Math.min(minZ, zs[i]);
			maxZ = Math.max(maxZ, zs[i]);
		}
		int bx0 = Mth.floor(center.x + minX);
		int bx1 = Mth.floor(center.x + maxX - 1.0E-7);
		int bz0 = Mth.floor(center.z + minZ);
		int bz1 = Mth.floor(center.z + maxZ - 1.0E-7);
		for (int bx = bx0; bx <= bx1; bx++) {
			double left = bx - center.x;
			for (int bz = bz0; bz <= bz1; bz++) {
				double near = bz - center.z;
				if (hole(left + 0.5, near + 0.5)) {
					continue;
				}
				int k = n;
				double[] px = xs;
				double[] pz = zs;
				float[] pa = alphas;
				int buffer = 0;
				// Kept by each side of the block in turn: x >= left, x <= left + 1, z >= near, z <= near + 1.
				for (int side = 0; side < 4 && k >= 3; side++) {
					k = clipSide(px, pz, pa, k, CLIP_X[buffer], CLIP_Z[buffer], CLIP_A[buffer], side < 2,
							side < 2 ? (side == 0 ? left : left + 1) : (side == 2 ? near : near + 1), side % 2 == 0);
					px = CLIP_X[buffer];
					pz = CLIP_Z[buffer];
					pa = CLIP_A[buffer];
					buffer = 1 - buffer;
				}
				if (k < 3) {
					continue;
				}
				double y = veinTop(left + 0.5, near + 0.5) + lift;
				for (int i = 1; i + 1 < k; i += 2) {
					int last = Math.min(i + 2, k - 1);
					decalVertex(out, m, px[0], y, pz[0], color, pa[0]);
					decalVertex(out, m, px[i], y, pz[i], color, pa[i]);
					decalVertex(out, m, px[i + 1], y, pz[i + 1], color, pa[i + 1]);
					decalVertex(out, m, px[last], y, pz[last], color, pa[last]);
				}
			}
		}
	}

	/** One side of a block's clip: keeps the part of the polygon on the kept side of {@code bound} along x or z. */
	private static int clipSide(double[] xs, double[] zs, float[] as, int n, double[] ox, double[] oz, float[] oa, boolean alongX,
			double bound, boolean keepAbove) {
		int k = 0;
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			double vi = (alongX ? xs[i] : zs[i]) - bound;
			double vj = (alongX ? xs[j] : zs[j]) - bound;
			boolean inI = keepAbove ? vi >= 0 : vi <= 0;
			boolean inJ = keepAbove ? vj >= 0 : vj <= 0;
			if (inI && k < ox.length) {
				ox[k] = xs[i];
				oz[k] = zs[i];
				oa[k] = as[i];
				k++;
			}
			if (inI != inJ && k < ox.length) {
				double t = vi / (vi - vj);
				ox[k] = xs[i] + (xs[j] - xs[i]) * t;
				oz[k] = zs[i] + (zs[j] - zs[i]) * t;
				oa[k] = (float) (as[i] + (as[j] - as[i]) * t);
				k++;
			}
		}
		return k;
	}

	private static void decalVertex(VertexConsumer out, Matrix4f m, double x, double y, double z, int color, float alpha) {
		vertex(out, m, new Vec3(x, y, z), color, Math.round(alpha));
	}

	/** A round glow lying on the floor ({@link #decal}): {@code alpha} at its middle, nothing at its rim. */
	private static void floorDisc(VertexConsumer out, Matrix4f m, double x, double z, double radius, int color, int alpha, double lift) {
		int segments = radius < 1 ? 12 : 24;
		double[] xs = new double[3];
		double[] zs = new double[3];
		float[] as = {alpha, 0, 0};
		for (int s = 0; s < segments; s++) {
			double a0 = Math.PI * 2 * s / segments;
			double a1 = Math.PI * 2 * (s + 1) / segments;
			xs[0] = x;
			zs[0] = z;
			xs[1] = x + Math.cos(a1) * radius;
			zs[1] = z + Math.sin(a1) * radius;
			xs[2] = x + Math.cos(a0) * radius;
			zs[2] = z + Math.sin(a0) * radius;
			decal(out, m, xs, zs, as, 3, color, lift);
		}
	}


	/** Disco's floor: tiles on the ground lighting up on the beat, each with its colour and its own rhythm. */
	private static void drawDanceFloor(PoseStack pose, MultiBufferSource buffers, Ambiance.DanceFloor floor, float time) {
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
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

	/**
	 * A neon grid on the floor (2026-11-20: on the blocks' grid, as the veins): a line of glowing strips every
	 * {@code spacing} blocks each way, through the blocks' middles, down the side of each step; a brighter wave running
	 * out from the field.
	 */
	private static void drawNeonGrid(PoseStack pose, MultiBufferSource buffers, Ambiance.NeonGrid grid, float time) {
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		float extent = fieldRadius * grid.extent;
		int spacing = Math.max(2, Math.round(grid.spacing));
		int color = Ambiance.color(grid.color, 0x00FFD5);
		int accent = Ambiance.color(grid.accent, 0xFF00C8);
		float wave = (time / 20f * grid.pulse) % (extent + 6f);
		int bx0 = Mth.floor(center.x);
		int bz0 = Mth.floor(center.z);
		int reach = Mth.ceil(extent) + 1;
		double[] below = new double[4];
		for (int dx = -reach; dx <= reach; dx++) {
			boolean onX = Math.floorMod(bx0 + dx, spacing) == 0;
			for (int dz = -reach; dz <= reach; dz++) {
				boolean onZ = Math.floorMod(bz0 + dz, spacing) == 0;
				if (!onX && !onZ) {
					continue;
				}
				double x = bx0 + dx + 0.5 - center.x;
				double z = bz0 + dz + 0.5 - center.z;
				double d = Math.sqrt(x * x + z * z);
				if (d > extent || hole(x, z)) {
					continue;
				}
				boolean inWave = Math.abs(d - wave) < 1.6;
				int alpha = Math.min(255, Math.round(grid.alpha * (float) (1 - d / extent) * (inWave ? 1.4f : 1f)));
				if (alpha < 3) {
					continue;
				}
				// A line along z where x is on the grid (south and north sides), along x where z is.
				int arms = (onX ? 4 | 8 : 0) | (onZ ? 1 | 2 : 0);
				double top = veinTop(x, z);
				for (int side = 0; side < 4; side++) {
					below[side] = (arms & 1 << side) != 0 ? veinTop(x + VEIN_DX[side], z + VEIN_DZ[side]) : top;
				}
				veinBlock(out, m, x, top, z, arms, below, 0.05, inWave ? accent : color, alpha);
			}
		}
	}

	/** The mirror ball's light dots: thrown down from the ball, turning with it, landing on the ground. */
	private static void drawMirrorBallDots(PoseStack pose, MultiBufferSource buffers, Ambiance.MirrorBall ball, float time) {
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
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
			if (hole(at)) {
				continue;
			}
			float size = 0.32f + 0.18f * hashFloat(i * 104729);
			floorDisc(out, m, x, z, size, color, ball.alpha, 0.13);
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
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
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
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
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

	/**
	 * Mist: level sheets of fog lying in the hollows, thick near the ground and thinning upward, their thickness
	 * changing in slow drifting patches. 2026-11-20: sheets following the ground cut through the blocks or stepped up
	 * with them, so every height change showed as a hard line. Now each sheet is level, a fixed height apart, and fades
	 * to nothing where it comes down to the blocks under it: at each corner its opacity follows how high it is over the
	 * highest of the four blocks meeting there, so it is already clear where it would touch a block, a step's face
	 * included, and no edge can show. Seen edge-on a sheet thins too, so the sheets never read as slabs.
	 */
	private static void drawMist(PoseStack pose, MultiBufferSource buffers, Ambiance.Mist mist, float time) {
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.TINT);
		Matrix4f m = pose.last().pose();
		float radius = mist.radius > 0 ? mist.radius : fieldRadius * 1.5f;
		int color = Ambiance.color(mist.color, 0xB0C0C8);
		double height = Math.max(0.5, mist.height);
		int layers = Math.max(1, mist.layers);
		// Sheets close enough to blend into one another, never more than 0.6 apart.
		double step = Mth.clamp(height / layers, 0.25, 0.6);
		// Each sheet as dense as one of the old layers, whatever the spacing.
		float density = (float) (mist.alpha * layers * step / height) * arrived();
		int bx0 = Mth.floor(center.x);
		int bz0 = Mth.floor(center.z);
		int n = Mth.ceil(radius) + 1;
		int size = 2 * n + 1;
		// Corner (i, j) is the blocks' corner at (bx0 - n + i, bz0 - n + j), from the field's centre.
		double[] floor = new double[size * size];
		float[] base = new float[size * size];
		double lowest = Double.MAX_VALUE;
		double highest = -Double.MAX_VALUE;
		for (int i = 0; i < size; i++) {
			double x = bx0 - n + i - center.x;
			for (int j = 0; j < size; j++) {
				double z = bz0 - n + j - center.z;
				double d = Math.sqrt(x * x + z * z);
				if (d > radius + 1.5 || hole(x, z)) {
					continue;
				}
				double top = Math.max(Math.max(veinTop(x - 0.5, z - 0.5), veinTop(x + 0.5, z - 0.5)),
						Math.max(veinTop(x - 0.5, z + 0.5), veinTop(x + 0.5, z + 0.5)));
				floor[i * size + j] = top;
				base[i * size + j] = (float) Mth.clamp((radius - d) / 4.0, 0, 1);
				lowest = Math.min(lowest, top);
				highest = Math.max(highest, top);
			}
		}
		if (lowest > highest) {
			return;
		}
		double t = time / 20.0 * mist.speed;
		float[] alpha = new float[size * size];
		Vec3[] at = new Vec3[size * size];
		// The sheets sit at fixed heights in the world (not over the field's centre), from the lowest floor up.
		int first = Mth.floor((center.y + lowest) / step) + 1;
		int last = Mth.ceil((center.y + highest + height) / step);
		for (int sheet = first; sheet <= last; sheet++) {
			double level = sheet * step - center.y;
			boolean any = false;
			for (int i = 0; i < size; i++) {
				double x = bx0 - n + i - center.x;
				for (int j = 0; j < size; j++) {
					int k = i * size + j;
					alpha[k] = 0;
					at[k] = null;
					if (base[k] <= 0) {
						continue;
					}
					double z = bz0 - n + j - center.z;
					// A slow swell, so the sheets are not dead flat (its height counts in the clearance below).
					double y = level + 0.12 * Math.sin(x * 0.35 + z * 0.27 + t * 0.6 + sheet);
					double clear = y - floor[k];
					if (clear <= 0 || clear >= height) {
						at[k] = new Vec3(x, y, z);
						continue;
					}
					// Clear at the ground, full a little over it, thinning upward and gone at the top.
					double rise = Mth.clamp(clear / 0.45, 0, 1);
					double fall = Mth.clamp((height - clear) / (height * 0.45), 0, 1);
					double thick = rise * rise * (3 - 2 * rise) * fall * fall * (3 - 2 * fall) * (1 - 0.4 * clear / height);
					double patch = 0.5 + 0.25 * Math.sin(x / mist.scale + t + sheet * 0.9)
							+ 0.25 * Math.cos(z / mist.scale * 0.8 - t * 0.7 + sheet * 0.6);
					Vec3 p = new Vec3(x, y, z);
					double dist = Math.max(0.1, p.distanceTo(cameraRel));
					float edgeOn = 0.25f + 0.75f * (float) Mth.clamp(Math.abs(y - cameraRel.y) / dist * 2.5, 0, 1);
					at[k] = p;
					alpha[k] = density * (float) (thick * patch) * base[k] * edgeOn * nearFade(p, 0.5, 2.5);
					any = true;
				}
			}
			if (!any) {
				continue;
			}
			for (int i = 0; i < size - 1; i++) {
				for (int j = 0; j < size - 1; j++) {
					int k00 = i * size + j;
					int k10 = k00 + size;
					if (at[k00] == null || at[k10] == null || at[k10 + 1] == null || at[k00 + 1] == null
							|| alpha[k00] + alpha[k10] + alpha[k10 + 1] + alpha[k00 + 1] < 2) {
						continue;
					}
					vertex(out, m, at[k00], color, Math.round(alpha[k00]));
					vertex(out, m, at[k10], color, Math.round(alpha[k10]));
					vertex(out, m, at[k10 + 1], color, Math.round(alpha[k10 + 1]));
					vertex(out, m, at[k00 + 1], color, Math.round(alpha[k00 + 1]));
				}
			}
		}
	}

	/**
	 * Corrupted's veins: from each rift's foot (or from round the field), a few branches crawling toward it. Laid on the
	 * blocks' grid (2026-11-20: free lines crossed the blocks at any angle, floated over steps and cut into them): runs
	 * of a few blocks, turning at right angles like a circuit, each block knowing which sides it runs out of.
	 */
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

	/** The four sides a vein block runs out of: east, west, south, north (a side's opposite is its index xor 1). */
	private static final int[] VEIN_DX = {1, -1, 0, 0};
	private static final int[] VEIN_DZ = {0, 0, 1, -1};

	/** One vein, crawling block by block from (x, z) roughly toward {@code heading}, in straight runs, never doubling back. */
	private static void crawl(Ambiance.Veins veins, double x, double z, double heading) {
		int cx = Mth.floor(x);
		int cz = Mth.floor(z);
		int walked = 0;
		int last = -1;
		while (walked < veins.length) {
			heading += (RANDOM.nextDouble() - 0.5) * 0.9;
			double c = Math.cos(heading);
			double s = Math.sin(heading);
			boolean alongX = RANDOM.nextDouble() * (Math.abs(c) + Math.abs(s)) < Math.abs(c);
			int dir = alongX ? (c >= 0 ? 0 : 1) : (s >= 0 ? 2 : 3);
			if (last >= 0 && dir == (last ^ 1)) {
				dir = last;
			}
			int run = 2 + RANDOM.nextInt(3);
			for (int k = 0; k < run && walked < veins.length; k++) {
				int nx = cx + VEIN_DX[dir];
				int nz = cz + VEIN_DZ[dir];
				veinLink(cx, cz, dir, walked);
				veinLink(nx, nz, dir ^ 1, walked + 1);
				cx = nx;
				cz = nz;
				walked++;
			}
			last = dir;
		}
	}

	private static void veinLink(int x, int z, int side, int walked) {
		int[] cell = VEINS.computeIfAbsent(((long) x << 32) | (z & 0xFFFFFFFFL), key -> new int[] {0, walked});
		cell[0] |= 1 << side;
		cell[1] = Math.min(cell[1], walked);
	}

	/**
	 * The veins, block by block (cells counted from the block the centre stands in): a glowing strip on each block's top
	 * from its middle out to the sides it runs to, and down the side of a step to the block below; a thin hot core
	 * inside. No two pieces overlap, so the glow is even along a vein.
	 */
	private static void drawVeins(PoseStack pose, MultiBufferSource buffers, Ambiance.Veins veins, java.util.Map<Long, int[]> cells,
			float time, float fade) {
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		int color = Ambiance.color(veins.color, 0xFF2A8A);
		int core = veins.core != null ? Ambiance.color(veins.core, 0xFFD060) : -1;
		double t = time / 20.0 * veins.pulse;
		int bx0 = Mth.floor(center.x);
		int bz0 = Mth.floor(center.z);
		for (var entry : cells.entrySet()) {
			int dx = (int) (entry.getKey() >> 32);
			int dz = (int) (long) entry.getKey();
			int arms = entry.getValue()[0];
			int walked = entry.getValue()[1];
			double x = bx0 + dx + 0.5 - center.x;
			double z = bz0 + dz + 0.5 - center.z;
			if (hole(x, z)) {
				continue;
			}
			float taper = (float) Mth.clamp(1 - walked / veins.length, 0.15, 1);
			float pulse = (float) (0.45 + 0.55 * Math.max(0, Math.sin(walked * 0.9 - t * 3)));
			int alpha = Math.round(veins.alpha * taper * pulse * fade);
			if (alpha < 3) {
				continue;
			}
			double top = veinTop(x, z);
			double[] below = new double[4];
			for (int side = 0; side < 4; side++) {
				below[side] = (arms & 1 << side) != 0 ? veinTop(x + VEIN_DX[side], z + VEIN_DZ[side]) : top;
			}
			veinBlock(out, m, x, top, z, arms, below, 0.09 + 0.05 * taper, color, alpha);
			if (core >= 0) {
				veinBlock(out, m, x, top, z, arms, below, 0.035, core, Math.round(alpha * 0.9f));
			}
		}
	}

	/** The top of the block a vein runs over: the floor people see there, the same all over the block. */
	private static double veinTop(double x, double z) {
		// Each block's top is the same all over it: kept per block for this stadium (the mist asks for hundreds a frame).
		if (BLOCK_TOPS_OWNER != active) {
			BLOCK_TOPS.clear();
			BLOCK_TOPS_OWNER = active;
		}
		int bx = Mth.floor(center.x + x);
		int bz = Mth.floor(center.z + z);
		long key = blockKey(bx, bz) * 31 + Mth.floor(center.y * 4);
		Double top = BLOCK_TOPS.get(key);
		if (top == null) {
			top = surfaceY(bx + 0.5 - center.x, bz + 0.5 - center.z) + center.y;
			BLOCK_TOPS.put(key, top);
		}
		return top - center.y;
	}

	/** {@link #veinTop}'s blocks, for the ambiance they were found under; emptied with the floors. */
	private static final java.util.Map<Long, Double> BLOCK_TOPS = new java.util.HashMap<>();
	private static Ambiance BLOCK_TOPS_OWNER;

	/** One block of vein, half-width {@code w}: the middle square, an arm to each side it runs to, a drop down each step. */
	private static void veinBlock(VertexConsumer out, Matrix4f m, double x, double top, double z, int arms, double[] below,
			double w, int color, int alpha) {
		double y = top + 0.03;
		veinQuad(out, m, x - w, y, z - w, x + w, z + w, color, alpha);
		for (int side = 0; side < 4; side++) {
			if ((arms & 1 << side) == 0) {
				continue;
			}
			int sx = VEIN_DX[side];
			int sz = VEIN_DZ[side];
			// The arm, from the middle square's edge to the block's side.
			double ax0 = sx == 0 ? x - w : x + sx * w;
			double ax1 = sx == 0 ? x + w : x + sx * 0.5;
			double az0 = sz == 0 ? z - w : z + sz * w;
			double az1 = sz == 0 ? z + w : z + sz * 0.5;
			veinQuad(out, m, ax0, y, az0, ax1, az1, color, alpha);
			double drop = below[side] + 0.03;
			if (drop < y - 0.05) {
				// Down the side of the step, just off its face.
				double fx = x + sx * 0.53;
				double fz = z + sz * 0.53;
				Vec3 a = new Vec3(sx == 0 ? x - w : fx, y, sz == 0 ? z - w : fz);
				Vec3 b = new Vec3(sx == 0 ? x + w : fx, y, sz == 0 ? z + w : fz);
				vertex(out, m, a, color, alpha);
				vertex(out, m, b, color, alpha);
				vertex(out, m, new Vec3(b.x, drop, b.z), color, alpha);
				vertex(out, m, new Vec3(a.x, drop, a.z), color, alpha);
			}
		}
	}

	private static void veinQuad(VertexConsumer out, Matrix4f m, double x0, double y, double z0, double x1, double z1, int color,
			int alpha) {
		vertex(out, m, new Vec3(Math.min(x0, x1), y, Math.min(z0, z1)), color, alpha);
		vertex(out, m, new Vec3(Math.max(x0, x1), y, Math.min(z0, z1)), color, alpha);
		vertex(out, m, new Vec3(Math.max(x0, x1), y, Math.max(z0, z1)), color, alpha);
		vertex(out, m, new Vec3(Math.min(x0, x1), y, Math.max(z0, z1)), color, alpha);
	}

	/** Cracks in the sky: jagged lines on the sky's sphere, a thin hot core in a wide soft glow, pulsing. */
	private static void drawSkyCracks(PoseStack pose, MultiBufferSource buffers, Ambiance.SkyCracks cracks, float time) {
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
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
		VertexConsumer out = ambianceBuffer(buffers, glow ? com.mystaria.showtime.ShowtimeRenderTypes.GLOW : com.mystaria.showtime.ShowtimeRenderTypes.TINT);
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
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
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
			if (hole(at)) {
				continue;
			}
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
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.TINT);
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
			if (hole(base)) {
				continue;
			}
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
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
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
			if (hole(at)) {
				continue;
			}
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
		VertexConsumer out = ambianceBuffer(buffers, glow ? com.mystaria.showtime.ShowtimeRenderTypes.GLOW : com.mystaria.showtime.ShowtimeRenderTypes.TINT);
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
				if (hole(foot) || nearFade(foot, 0.5, 1.5) <= 0.05f) {
					continue;
				}
				float breathe = 0.7f + 0.3f * Mth.sin(time / 30f + c + k);
				Vec3 cap = foot.add(0, size * 1.1, 0);
				if (glow) {
					floorDisc(out, m, foot.x, foot.z, size * 2.2, color, Math.round(60 * breathe), 0.14);
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
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
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
			if (hole(at)) {
				continue;
			}
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
		VertexConsumer out = ambianceBuffer(buffers, glow ? com.mystaria.showtime.ShowtimeRenderTypes.GLOW : com.mystaria.showtime.ShowtimeRenderTypes.TINT);
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
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		int color = Ambiance.color(caustics.color, 0xA8F4FF);
		float radius = Math.min(caustics.radius > 0 ? caustics.radius : fieldRadius * 1.35f, 22f);
		// Half a block, lined up with the blocks (2026-11-20): each cell lies flat on one block's top.
		double cell = 0.5;
		int n = (int) Math.ceil(radius / cell);
		double t = time / 20.0 * caustics.speed;
		double s = caustics.scale;
		double gx = Mth.floor(center.x) - center.x;
		double gz = Mth.floor(center.z) - center.z;
		float[][] light = new float[2 * n + 1][2 * n + 1];
		for (int ix = -n; ix <= n; ix++) {
			for (int iz = -n; iz <= n; iz++) {
				double x = gx + ix * cell;
				double z = gz + iz * cell;
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
				double x0 = gx + (ix - n) * cell;
				double z0 = gz + (iz - n) * cell;
				if (hole(x0, z0) || hole(x0 + cell, z0) || hole(x0 + cell, z0 + cell) || hole(x0, z0 + cell)) {
					continue;
				}
				float k = caustics.alpha;
				decal(out, m, new double[] {x0, x0 + cell, x0 + cell, x0}, new double[] {z0, z0, z0 + cell, z0 + cell},
						new float[] {k * a, k * b, k * c, k * d}, 4, color, 0.12);
			}
		}
	}

	/** Shafts of light from above, all leaning the same way, swaying and breathing. */
	private static void drawShafts(PoseStack pose, MultiBufferSource buffers, Ambiance.Shafts shafts, float time) {
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Matrix4f m = pose.last().pose();
		int color = Ambiance.color(shafts.color, 0xBFF4FF);
		double lean = Math.tan(Math.toRadians(shafts.tilt)) * shafts.height;
		for (int i = 0; i < shafts.count; i++) {
			double angle = hashFloat(i * 73 + 1) * Math.PI * 2;
			double radius = Mth.lerp(hashFloat(i * 73 + 2), shafts.ring.get(0), shafts.ring.get(1));
			double sway = Math.sin(time / 50.0 + i * 1.9) * 1.6;
			Vec3 foot = onGround(Math.cos(angle) * radius + sway, Math.sin(angle) * radius);
			if (hole(foot)) {
				continue;
			}
			Vec3 top = foot.add(lean, shafts.height, lean * 0.3);
			float breathe = 0.6f + 0.4f * Mth.sin(time / 37f + i * 2.1f);
			beam(out, m, top, foot, shafts.width * 0.7f, shafts.width * 1.3f, color, Math.round(shafts.alpha * breathe));
		}
	}

	/** Kelp: ribbons rooted beyond the field, swaying more toward their tips, darker at the root. */
	private static void drawKelp(PoseStack pose, MultiBufferSource buffers, Ambiance.Kelp kelp, float time) {
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.TINT);
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
			if (hole(foot) || nearFade(foot.add(0, height / 2, 0), 1.5, 2) <= 0.05f) {
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
		VertexConsumer out = ambianceBuffer(buffers, glow ? com.mystaria.showtime.ShowtimeRenderTypes.GLOW : com.mystaria.showtime.ShowtimeRenderTypes.TINT);
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
			if (hole(at)) {
				continue;
			}
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
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
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
			if (hole(at)) {
				continue;
			}
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
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.TINT);
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
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
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
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.TINT);
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
		VertexConsumer out = ambianceBuffer(buffers, glow ? com.mystaria.showtime.ShowtimeRenderTypes.GLOW : com.mystaria.showtime.ShowtimeRenderTypes.TINT);
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
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.TINT);
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
		VertexConsumer out = ambianceBuffer(buffers, layer == 1 ? com.mystaria.showtime.ShowtimeRenderTypes.TINT : com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
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
		VertexConsumer out = ambianceBuffer(buffers, glow ? com.mystaria.showtime.ShowtimeRenderTypes.GLOW : com.mystaria.showtime.ShowtimeRenderTypes.OPAQUE);
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
			// Per-vertex colour (2026-11-19, as the Reborn stadiums): each corner lit by its own normal, grain over
			// the rock, so the island reads as one rounded stone, not as flat facets.
			int lipRock = lerpColor(top, rock, 0.45f);
			int deepRock = lerpColor(rock, 0x000000, 0.35f);
			int crownColor = ultraTop(top, crown);
			int tipColor = sunlit(stoneGrain(lerpColor(deepRock, 0x000000, 0.2f), tip, 0.15f), new Vec3(0, -1, 0), 0.4f);
			int[][] colors = new int[3][sides];
			for (int k = 0; k < sides; k++) {
				Vec3 outward = rings[1][k].subtract(c);
				outward = new Vec3(outward.x, 0, outward.z).normalize();
				colors[0][k] = ultraTop(top, rings[0][k]);
				colors[1][k] = sunlit(stoneGrain(lipRock, rings[1][k], 0.16f), outward.add(0, 0.1, 0).normalize(), 0.45f);
				colors[2][k] = sunlit(stoneGrain(deepRock, rings[2][k], 0.16f), outward.add(0, -0.6, 0).normalize(), 0.42f);
			}
			for (int k = 0; k < sides; k++) {
				int n = (k + 1) % sides;
				quadColors(out, m, crown, crown, rings[0][n], rings[0][k], crownColor, crownColor, colors[0][n], colors[0][k]);
				int rimK = lerpColor(colors[0][k], colors[1][k], 0.5f);
				int rimN = lerpColor(colors[0][n], colors[1][n], 0.5f);
				quadColors(out, m, rings[0][k], rings[0][n], rings[1][n], rings[1][k], rimK, rimN, colors[1][n], colors[1][k]);
				quadColors(out, m, rings[1][k], rings[1][n], rings[2][n], rings[2][k], colors[1][k], colors[1][n], colors[2][n], colors[2][k]);
				quadColors(out, m, rings[2][k], rings[2][n], tip, tip, colors[2][k], colors[2][n], tipColor, tipColor);
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

	/** Ultra Space's island top at {@code p}: patches of a lighter violet stone, a fine grain over all of it. */
	private static int ultraTop(int top, Vec3 p) {
		double patch = valueNoise(p.x * 0.16 + 5, 0.5, p.z * 0.16 + 9);
		int c = lerpColor(top, 0x4A3A9A, (float) (0.45 * smooth(Mth.clamp(patch * 1.4 - 0.2, 0, 1))));
		return stoneGrain(c, p, 0.14f);
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
		VertexConsumer out = ambianceBuffer(buffers, glow ? com.mystaria.showtime.ShowtimeRenderTypes.GLOW : com.mystaria.showtime.ShowtimeRenderTypes.OPAQUE);
		Matrix4f m = pose.last().pose();
		int top = Ambiance.color(island.top, 0x241A52);
		int line = Ambiance.color(island.line, 0x7FF8FF);
		int rock = Ambiance.color(island.rock, 0x1A1238);
		int crystal = Ambiance.color(island.crystal, 0x7FF8FF);
		int accent = Ambiance.color(island.accent, 0xFF7AE8);
		float depth = island.depth > 0 ? island.depth : Math.max(10f, fieldRadius * 1.1f);
		int sectors = 64;
		double reach = fieldRadius + island.margin;
		Vec3 sink = new Vec3(0, -0.03, 0);
		if (!glow) {
			// The top: block by block on the real ground, each block coloured from where it is.
			drawBlockFloor(out, m, "island", reach, (x, z) -> groundAt(x, z) + 0.09,
					(x, z) -> ultraTop(top, new Vec3(x, groundAt(x, z) + 0.09, z)), null,
					(x, z, r) -> r <= islandEdge(island, Math.atan2(z, x)));
			// The rock below: the rim on the ground, a lip, then narrower and narrower rings to a point, every corner lit
			// by its own normal and grained, darker the deeper it hangs.
			double[] across = {1, 1.02, 0.88, 0.74, 0.56, 0.36, 0.17};
			double[] down = {0, 0, 0.17, 0.34, 0.54, 0.74, 0.92};
			double[] slope = {0.3, 0.1, -0.25, -0.4, -0.6, -0.85, -1.1};
			int levels = across.length;
			Vec3[][] ring = new Vec3[levels][sectors];
			int[][] shadeAt = new int[levels][sectors];
			for (int k = 0; k < sectors; k++) {
				double a = Math.PI * 2 * k / sectors;
				double e = islandEdge(island, a);
				Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
				for (int l = 0; l < levels; l++) {
					if (l == 0) {
						ring[l][k] = onGround(dir.x * e, dir.z * e).add(sink);
					} else if (l == 1) {
						ring[l][k] = dir.scale(e * across[l]).add(0, ring[0][k].y - 0.7, 0);
					} else {
						double jag = 0.1 * (hashFloat(k * 17 + l * 5 + 1) - 0.5);
						ring[l][k] = dir.scale(e * (across[l] + jag)).add(0, -depth * (down[l] + 0.06 * hashFloat(k * 17 + l * 5 + 2)), 0);
					}
					int base = l <= 1 ? lerpColor(top, rock, 0.5f) : lerpColor(rock, 0x000000, 0.45f * (l - 1) / (levels - 1));
					shadeAt[l][k] = sunlit(stoneGrain(base, ring[l][k], 0.18f), dir.add(0, slope[l], 0).normalize(), 0.42f);
				}
			}
			Vec3 point = new Vec3(0, -depth * 1.05, 0);
			int pointColor = sunlit(stoneGrain(lerpColor(rock, 0x000000, 0.5f), point, 0.15f), new Vec3(0, -1, 0), 0.42f);
			for (int k = 0; k < sectors; k++) {
				int n = (k + 1) % sectors;
				for (int l = 0; l + 1 < levels; l++) {
					quadColors(out, m, ring[l][k], ring[l][n], ring[l + 1][n], ring[l + 1][k], shadeAt[l][k], shadeAt[l][n],
							shadeAt[l + 1][n], shadeAt[l + 1][k]);
				}
				quadColors(out, m, ring[levels - 1][k], ring[levels - 1][n], point, point, shadeAt[levels - 1][k],
						shadeAt[levels - 1][n], pointColor, pointColor);
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

	// ---------------------------------------------------------------- Caves (2026-11-19)

	/** A rock coming down from the vault: where it starts, when, when it lands, the floor under it, its size and spin. */
	private record Rock(Vec3 from, long at, long landAt, double floor, float size, float spin) {
	}

	private static final List<Rock> ROCKS = new ArrayList<>();
	private static final double FALL_GRAVITY = 0.035;
	/** The vault shakes until then (a rockfall). */
	private static long quakeUntil;

	private static double cavernRadius(Ambiance.Cavern cavern) {
		return fieldRadius + cavern.margin;
	}

	/** The light on a stadium's materials: the ambiance's when it casts one, else the stadium's own. */
	private static int lightColor(Ambiance.Cavern cavern) {
		return Ambiance.color(active.light != null ? active.light.color : cavern.light, 0xFFD9A0);
	}

	private static float lightStrength(Ambiance.Cavern cavern) {
		return active.light != null ? active.light.strength : cavern.lightStrength;
	}

	/** The vault at {@code angle}, {@code v} from its foot (0) to its top (1): uneven, so it reads as rock. */
	private static Vec3 vaultPoint(Ambiance.Cavern cavern, double angle, double v) {
		double phi = v * Math.PI / 2;
		double bump = 1 + 0.09 * Math.sin(3 * angle + 1.3) * Math.cos(5 * angle + 2 * v) + 0.05 * Math.sin(11 * angle + 7 * v)
				+ 0.04 * Math.sin(17 * angle - 9 * v + 0.5);
		double across = cavernRadius(cavern) * Math.pow(Math.cos(phi), 0.55) * bump;
		double y = -3 + (cavern.height + 3) * Math.sin(phi) * (1 + 0.05 * Math.sin(7 * angle + 3 * v));
		return new Vec3(Math.cos(angle) * across, y, Math.sin(angle) * across);
	}

	/** The vault's height above a point {@code r} blocks from the field's centre. */
	private static double vaultHeightAt(Ambiance.Cavern cavern, double r) {
		double c = Math.pow(Mth.clamp(r / cavernRadius(cavern), 0, 1), 1 / 0.55);
		return -3 + (cavern.height + 3) * Math.sin(Math.acos(c));
	}

	/** The rock's colour at {@code p} facing {@code normal}: dark far from the field, warmed by its light near it. */
	private static int caveLit(Ambiance.Cavern cavern, int color, Vec3 p, Vec3 normal) {
		Vec3 toLight = new Vec3(0, 3, 0).subtract(p);
		double distance = Math.max(1.0E-6, toLight.length());
		double facing = Math.max(0, normal.dot(toLight.scale(1 / distance)));
		float lit = (float) (facing * Mth.clamp(1 - distance / (cavernRadius(cavern) * 1.4), 0, 1));
		int base = lerpColor(0x000000, color, cavern.ambient + (1 - cavern.ambient) * lit);
		return lerpColor(base, lightColor(cavern), lightStrength(cavern) * lit);
	}

	/** A quad with a colour per corner, blended across it. */
	private static void quadColors(VertexConsumer out, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int ca, int cb, int cc, int cd) {
		vertex(out, m, a, ca, 255);
		vertex(out, m, b, cb, 255);
		vertex(out, m, c, cc, 255);
		vertex(out, m, d, cd, 255);
	}

	/**
	 * Stone's grain at {@code p}: the colour made darker or lighter by a smooth noise of three sizes (patches of a few
	 * blocks, then finer), at most {@code amount}; continuous, so it never shows the faces it is drawn on.
	 */
	private static int stoneGrain(int color, Vec3 p, float amount) {
		double n = 0.55 * valueNoise(p.x * 0.28, p.y * 0.28, p.z * 0.28) + 0.3 * valueNoise(p.x * 0.8 + 17, p.y * 0.8, p.z * 0.8)
				+ 0.15 * valueNoise(p.x * 2.1 + 41, p.y * 2.1, p.z * 2.1);
		double d = (n - 0.5) * 2;
		return lerpColor(color, d > 0 ? 0xFFFFFF : 0x000000, amount * (float) Math.min(1, Math.abs(d) * 1.4));
	}

	/** Smooth value noise in 0..1: random values on a lattice, blended with a smoothstep. */
	private static double valueNoise(double x, double y, double z) {
		int x0 = Mth.floor(x);
		int y0 = Mth.floor(y);
		int z0 = Mth.floor(z);
		double fx = smooth(x - x0);
		double fy = smooth(y - y0);
		double fz = smooth(z - z0);
		double c000 = lattice(x0, y0, z0);
		double c100 = lattice(x0 + 1, y0, z0);
		double c010 = lattice(x0, y0 + 1, z0);
		double c110 = lattice(x0 + 1, y0 + 1, z0);
		double c001 = lattice(x0, y0, z0 + 1);
		double c101 = lattice(x0 + 1, y0, z0 + 1);
		double c011 = lattice(x0, y0 + 1, z0 + 1);
		double c111 = lattice(x0 + 1, y0 + 1, z0 + 1);
		double a = Mth.lerp(fx, c000, c100);
		double b = Mth.lerp(fx, c010, c110);
		double c = Mth.lerp(fx, c001, c101);
		double d = Mth.lerp(fx, c011, c111);
		return Mth.lerp(fz, Mth.lerp(fy, a, b), Mth.lerp(fy, c, d));
	}

	private static double smooth(double t) {
		return t * t * (3 - 2 * t);
	}

	private static double lattice(int x, int y, int z) {
		return hashFloat(x * 73856093 ^ y * 19349663 ^ z * 83492791);
	}

	/** A rough cone of rock: six faces of uneven width, darker at the root, lighter (wet) at the point. */
	private static void cone(VertexConsumer out, Matrix4f m, Vec3 base, Vec3 tip, float width, int root, int point, int seed) {
		Vec3[] frame = crystalFrame(base, tip);
		int sides = 6;
		Vec3[] rim = new Vec3[sides];
		for (int k = 0; k < sides; k++) {
			double a = Math.PI * 2 * k / sides + seed;
			double r = width * (0.75 + 0.5 * hashFloat(seed * 7 + k));
			rim[k] = base.add(frame[0].scale(Math.cos(a) * r)).add(frame[1].scale(Math.sin(a) * r));
		}
		for (int k = 0; k < sides; k++) {
			float facet = k % 2 == 0 ? 1f : 0.82f;
			int lo = lerpColor(0x000000, root, facet);
			int hi = lerpColor(0x000000, point, facet);
			vertex(out, m, rim[k], lo, 255);
			vertex(out, m, rim[(k + 1) % sides], lo, 255);
			vertex(out, m, tip, hi, 255);
			vertex(out, m, tip, hi, 255);
		}
	}

	/** A tube of {@code sides} faces from {@code a} to {@code b} (bones, pillars' rings). */
	private static void tube(VertexConsumer out, Matrix4f m, Vec3 a, Vec3 b, double ra, double rb, int color) {
		Vec3[] frame = crystalFrame(a, b);
		int sides = 6;
		for (int k = 0; k < sides; k++) {
			double a0 = Math.PI * 2 * k / sides;
			double a1 = Math.PI * 2 * (k + 1) / sides;
			Vec3 d0 = frame[0].scale(Math.cos(a0)).add(frame[1].scale(Math.sin(a0)));
			Vec3 d1 = frame[0].scale(Math.cos(a1)).add(frame[1].scale(Math.sin(a1)));
			int c = shade(color, d0.add(d1).normalize(), 0.55f);
			quad(out, m, a.add(d0.scale(ra)), a.add(d1.scale(ra)), b.add(d1.scale(rb)), b.add(d0.scale(rb)), c);
		}
	}

	/** A boulder: a rough octahedron turned by {@code spin}. */
	private static void boulder(VertexConsumer out, Matrix4f m, Vec3 c, float size, double spin, int color, int seed) {
		Vec3[] p = new Vec3[6];
		double[][] axes = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		for (int k = 0; k < 6; k++) {
			double r = size * (0.7 + 0.6 * hashFloat(seed * 11 + k));
			p[k] = c.add(rotateY(new Vec3(axes[k][0] * r, axes[k][1] * r * 0.8, axes[k][2] * r), spin));
		}
		int[][] faces = {{0, 2, 4}, {4, 2, 1}, {1, 2, 5}, {5, 2, 0}, {0, 4, 3}, {4, 1, 3}, {1, 5, 3}, {5, 0, 3}};
		for (int[] f : faces) {
			Vec3 normal = p[f[1]].subtract(p[f[0]]).cross(p[f[2]].subtract(p[f[0]])).normalize();
			int shaded = shade(color, normal.y < 0 ? normal.scale(-1) : normal, 0.45f);
			vertex(out, m, p[f[0]], shaded, 255);
			vertex(out, m, p[f[1]], shaded, 255);
			vertex(out, m, p[f[2]], shaded, 255);
			vertex(out, m, p[f[2]], shaded, 255);
		}
	}

	/** A random point under the ceiling round the field (a real one, or a stadium's vault); null under open sky. */
	private static Vec3 ceilingPoint() {
		List<Anchors.Anchor> ceilings = anchors == null ? List.of() : anchors.all(Anchors.Kind.CEILING);
		if (ceilings.isEmpty()) {
			return null;
		}
		Anchors.Anchor a = ceilings.get(RANDOM.nextInt(ceilings.size()));
		return a.at().add((RANDOM.nextDouble() - 0.5) * 1.2, -0.6, (RANDOM.nextDouble() - 0.5) * 1.2);
	}

	/** Rocks start coming down from the ceiling, over the field and round it; the ceiling shakes meanwhile. */
	private static void rockfall(int count) {
		quakeUntil = ticks + 50;
		for (int i = 0; i < count; i++) {
			Vec3 from = ceilingPoint();
			if (from == null) {
				return;
			}
			double floor = groundAt(from.x, from.z) + 0.12;
			if (from.y - floor < 1) {
				continue;
			}
			long at = ticks + i * 3L + RANDOM.nextInt(6);
			long fall = (long) Math.ceil(Math.sqrt(2 * (from.y - floor) / FALL_GRAVITY));
			ROCKS.add(new Rock(from, at, at + fall, floor, 0.25f + RANDOM.nextFloat() * 0.55f, RANDOM.nextFloat() * 6.28f));
		}
		while (ROCKS.size() > 90) {
			ROCKS.remove(0);
		}
	}

	/** Each tick: rocks landing (dust, a crack), dust sifting down while the ceiling shakes. */
	private static void landRocks(Minecraft mc) {
		for (Rock rock : ROCKS) {
			if (ticks == rock.landAt()) {
				Vec3 at = center.add(rock.from().x, rock.floor() + 0.2, rock.from().z);
				for (int k = 0; k < 6; k++) {
					mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.POOF, at.x, at.y, at.z,
							(RANDOM.nextDouble() - 0.5) * 0.15, RANDOM.nextDouble() * 0.08, (RANDOM.nextDouble() - 0.5) * 0.15);
				}
				if (rock.size() > 0.45f || RANDOM.nextInt(3) == 0) {
					mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
							SoundEvent.createVariableRangeEvent(ResourceLocation.parse("minecraft:block.stone.break")),
							0.5f + RANDOM.nextFloat() * 0.3f, 0.5f));
				}
			}
		}
		if (ticks < quakeUntil && !calm()) {
			for (int k = 0; k < 3; k++) {
				Vec3 p = ceilingPoint();
				if (p != null) {
					mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.WHITE_ASH, center.x + p.x, center.y + p.y,
							center.z + p.z, 0, -0.05, 0);
				}
			}
		}
	}

	/**
	 * The cave. Solid pass: the floor laid on the hidden ground, the vault in rings to its top (a face the camera is
	 * outside of is left out: a cutaway, never a wall in front of the lens), stalactites, stalagmites, pillars, the lava
	 * lake and its falls, the hoard and the bones, falling rocks, the crystals. Light pass: water dripping and its
	 * ripples, the crystals' glow and beams, the lava's heat, the gold glinting, a dull glow in the skull's eyes.
	 */
	private static void drawCavern(PoseStack pose, MultiBufferSource buffers, Ambiance.Cavern cavern, float time, boolean glow) {
		if (!glow) {
			// Built of blocks (2026-11-19): the floor, the vault, pillars, dripstone, lava, the hoard, and the ambiance's
			// stalactites and stalagmites (2026-11-20). Asked before any buffer.
			caveInBlocks = com.mystaria.showtime.ambiance.blocks.BlockScenery.drawCave(pose, buffers, center, cameraWorld, frustum,
					cavern, lightStrength(cavern), stalks(), fieldRadius, BattleAmbiance::groundAt);
		}
		VertexConsumer out = ambianceBuffer(buffers, glow ? com.mystaria.showtime.ShowtimeRenderTypes.GLOW : com.mystaria.showtime.ShowtimeRenderTypes.OPAQUE);
		Matrix4f m = pose.last().pose();
		// The ambiance may recolour the stadium's stone (the same vault black in a dark crystal cave).
		int rock = Ambiance.color(active.light != null && active.light.rock != null ? active.light.rock : cavern.rock, 0x3A332C);
		int floorColor = Ambiance.color(active.light != null && active.light.floor != null ? active.light.floor : cavern.floor, 0x2E2924);
		double radius = cavernRadius(cavern);
		Ambiance.LavaLake lava = cavern.lava;
		double lavaIn = lava == null ? Double.MAX_VALUE : fieldRadius + lava.gap;
		double lavaOut = lava == null ? Double.MAX_VALUE : Math.min(radius + 2, lavaIn + lava.width);
		Vec3 up = new Vec3(0, 1, 0);
		if (!glow) {
			if (!caveInBlocks) {
				// The floor, out to the walls: the real blocks' tops, block by block, the lava's ring left for the lava.
				drawBlockFloor(out, m, "cave", radius, (x, z) -> groundAt(x, z) + 0.09, (x, z) -> {
					Vec3 p = new Vec3(x, groundAt(x, z) + 0.09, z);
					return caveLit(cavern, stoneGrain(floorColor, p, 0.16f), p, up);
				}, null, (x, z, r) -> r < lavaIn || r > lavaOut);
				// The vault, coloured per vertex too: its light and grain, strata running round it at uneven heights.
				int stacks = 18;
				int around = 96;
				Vec3[][] grid = new Vec3[stacks + 1][around];
				int[][] colors = new int[stacks + 1][around];
				Vec3 heart = new Vec3(0, cavern.height * 0.25, 0);
				for (int j = 0; j <= stacks; j++) {
					for (int k = 0; k < around; k++) {
						Vec3 p = vaultPoint(cavern, Math.PI * 2 * k / around, j / (double) stacks);
						grid[j][k] = p;
						double strata = Math.sin(p.y * 1.7 + 2.5 * valueNoise(p.x * 0.2, p.y * 0.2, p.z * 0.2));
						int stone = lerpColor(rock, strata > 0 ? 0xFFFFFF : 0x000000, 0.06f * (float) Math.abs(strata));
						colors[j][k] = caveLit(cavern, stoneGrain(stone, p, 0.2f), p, heart.subtract(p).normalize());
					}
				}
				for (int j = 0; j < stacks; j++) {
					for (int k = 0; k < around; k++) {
						int n = (k + 1) % around;
						Vec3 mid = grid[j][k].add(grid[j][n]).add(grid[j + 1][n]).add(grid[j + 1][k]).scale(0.25);
						Vec3 inward = heart.subtract(mid).normalize();
						if (cameraRel.subtract(mid).dot(inward) < 0 || nearFade(mid, 1.5, 2) < 0.5f) {
							continue;
						}
						quadColors(out, m, grid[j][k], grid[j][n], grid[j + 1][n], grid[j + 1][k], colors[j][k], colors[j][n],
								colors[j + 1][n], colors[j + 1][k]);
					}
				}
				// Pillars: a stalactite and a stalagmite grown into one, narrow in the middle.
				double[] heights = {0, 0.12, 0.35, 0.5, 0.65, 0.88, 1};
				double[] widths = {1.6, 1.05, 0.6, 0.5, 0.72, 1.25, 1.9};
				for (int i = 0; i < cavern.pillars; i++) {
					double a = Math.PI * 2 * (i + 0.5 * hashFloat(i * 23 + 1)) / Math.max(1, cavern.pillars) + 0.4;
					double r = radius * (0.74 + 0.1 * hashFloat(i * 23 + 2));
					double x = Math.cos(a) * r;
					double z = Math.sin(a) * r;
					double bottom = groundAt(x, z) - 0.5;
					double top = vaultHeightAt(cavern, r) + 1;
					for (int k = 0; k + 1 < heights.length; k++) {
						Vec3 p0 = new Vec3(x, Mth.lerp(heights[k], bottom, top), z);
						Vec3 p1 = new Vec3(x, Mth.lerp(heights[k + 1], bottom, top), z);
						if (nearFade(p0.add(p1).scale(0.5), 1.5, 2.0) < 0.3f) {
							continue;
						}
						int c = caveLit(cavern, lerpColor(rock, 0xFFFFFF, 0.06f * k), p0, new Vec3(-x, 0, -z).normalize());
						tube(out, m, p0, p1, widths[k], widths[k + 1], c);
					}
				}
				if (lava != null) {
					drawLavaLake(out, m, cavern, lava, lavaIn, lavaOut, time, false);
				}
			}
			if (cavern.hoard != null && !caveInBlocks) {
				drawHoard(out, m, cavern, cavern.hoard, lavaIn, lavaOut, time, false);
			}
		} else {
			// A soft light on the field, where the battle stands.
			floorDisc(out, m, 0, 0, fieldRadius, lightColor(cavern), 16, 0.14);
			if (lava != null) {
				drawLavaLake(out, m, cavern, lava, lavaIn, lavaOut, time, true);
			}
			if (cavern.hoard != null) {
				drawHoard(out, m, cavern, cavern.hoard, lavaIn, lavaOut, time, true);
			}
		}
	}

	// ---------------------------------------------------------------- The sea (2026-11-19, League Reborn's waters)

	private static final Vec3 SUN = new Vec3(0.45, 0.75, 0.35).normalize();
	private static final int[] FISH_COLORS = {0xE8A040, 0xC8DCEA, 0xF06A60, 0xE6E070, 0x7AC8F0};

	/** The water's look this battle: the stadium's colours, as the ambiance sees them (frozen, oily, calm...). */
	private record WaterLook(int water, int deep, int foam, int light, double amp, boolean frozen, boolean oil, float clarity) {
	}

	private static WaterLook waterLook(Ambiance.Sea sea) {
		Ambiance.Water w = active.water;
		int water = Ambiance.color(w != null && w.color != null ? w.color : sea.water, 0x2F8FC0);
		int deep = Ambiance.color(w != null && w.deep != null ? w.deep : sea.deep, 0x0E3F66);
		int foam = Ambiance.color(w != null && w.foam != null ? w.foam : sea.foam, 0xF2FBFF);
		int light = Ambiance.color(w != null ? w.light : null, 0xD8F6FF);
		boolean frozen = w != null && w.frozen;
		double amp = frozen ? 0 : sea.wave * (w != null && w.waves != null ? w.waves : 1);
		return new WaterLook(water, deep, foam, light, amp, frozen, w != null && w.oil, w != null ? w.clarity : 1f);
	}

	/** The water's resting height: just under the field's ground, so the field stays dry. */
	private static double seaLevel() {
		return groundAt(0, 0) - 0.45;
	}

	/** The islet's shore at {@code angle}: the field's edge plus the margin, uneven so it reads as a shore. */
	private static double isletEdge(Ambiance.Sea sea, double angle) {
		double n = valueNoise(Math.cos(angle) * 1.6 + 5, 0.5, Math.sin(angle) * 1.6 + 5);
		return fieldRadius + sea.margin * (0.6 + 0.9 * n);
	}

	/** The islet's top at (x, z): in whole blocks. */
	private static double isletY(Ambiance.Sea sea, double x, double z) {
		return snapToBlock((a, b) -> smoothIsletY(sea, a, b), x, z);
	}

	/** The islet's smooth top at (x, z): the real ground under the field (people stand on it), then a beach. */
	private static double smoothIsletY(Ambiance.Sea sea, double x, double z) {
		double r = Math.sqrt(x * x + z * z);
		double from = fieldRadius + 0.5;
		if (r <= from) {
			return groundAt(x, z) + 0.09;
		}
		double rim = groundAt(x * from / r, z * from / r) + 0.09;
		double edge = isletEdge(sea, Math.atan2(z, x));
		double floor = seaLevel() - 0.8;
		if (r > edge) {
			return floor - (r - edge) * 0.6;
		}
		return Mth.lerp(smooth(Mth.clamp((r - from) / Math.max(0.5, edge - from), 0, 1)), rim, floor);
	}

	/** The seabed at (x, z): in whole blocks. */
	private static double seabedY(double x, double z) {
		return snapToBlock(BattleAmbiance::smoothSeabedY, x, z);
	}

	/** The smooth seabed at (x, z): the real ground under the field, then sand dunes and ripples going gently down. */
	private static double smoothSeabedY(double x, double z) {
		double r = Math.sqrt(x * x + z * z);
		double from = fieldRadius + 0.5;
		if (r <= from) {
			return groundAt(x, z) + 0.09;
		}
		double rim = groundAt(x * from / r, z * from / r) + 0.09;
		double dunes = groundAt(0, 0) + 0.09 + 1.8 * (valueNoise(x * 0.06, 3, z * 0.06) - 0.5)
				+ 0.12 * Math.sin(x * 0.5 + z * 0.15 + 3 * valueNoise(x * 0.1, 7, z * 0.1)) - 0.035 * (r - from);
		return Mth.lerp(smooth(Mth.clamp((r - from) / 5, 0, 1)), rim, dunes);
	}

	/** The water's height above its rest at (x, z): long swells, a cross swell, short chop, a little noise. */
	private static double waveAt(Ambiance.Sea sea, double amp, double x, double z, float time) {
		if (amp <= 0) {
			return 0;
		}
		double t = time * 0.05 * sea.speed;
		double h = 0.5 * Math.sin(x * 0.21 + z * 0.13 - t)
				+ 0.3 * Math.sin(-x * 0.11 + z * 0.29 - t * 0.8 + 1.7)
				+ 0.2 * Math.sin(x * 0.43 - z * 0.37 - t * 1.6 + 0.6)
				+ 0.5 * (valueNoise(x * 0.12 + t * 0.3, t * 0.2, z * 0.12) - 0.5);
		return amp * h;
	}

	/** The sea's radii: about a block apart near the field, wider and wider out to the horizon. */
	private static List<Double> seaRadii(double from, double near, double to) {
		List<Double> radii = new ArrayList<>();
		double step = 1;
		for (double r = from; r < to; r += step) {
			radii.add(r);
			if (r > near) {
				step = Math.min(6, step * 1.12);
			}
		}
		radii.add(to);
		return radii;
	}

	/**
	 * The sea. Above water: the islet (the real ground under the field, a sand beach round it, rocks on its shore), the
	 * water out to the horizon rolling in waves, coloured per vertex (deeper further out, lighter on the crests, lit by
	 * its slope, foam breaking on the shore, rainbow slicks when oily); frozen, a still cracked sheet of ice with floes.
	 * Under water: a sand seabed with dunes and caustics, the surface rippling bright overhead, rocks, schools of fish.
	 * Light pass: the sun glittering on the water or the ice; the light coming down from the surface.
	 */
	private static void drawSea(PoseStack pose, MultiBufferSource buffers, Ambiance.Sea sea, float time, boolean glow) {
		if (!glow) {
			// Built of blocks (2026-11-19): Minecraft's water, coloured as the ambiance sees it. Asked before any buffer.
			WaterLook seen = waterLook(sea);
			seaInBlocks = com.mystaria.showtime.ambiance.blocks.BlockScenery.drawSea(pose, buffers, center, cameraWorld, frustum, sea,
					seen.frozen(), seen.water(), fieldRadius, BattleAmbiance::groundAt);
		}
		if (seaInBlocks && !sea.under) {
			return; // the islet, its rocks and the water are blocks; the sun's glitter would sit under their surface
		}
		VertexConsumer out = ambianceBuffer(buffers, glow ? com.mystaria.showtime.ShowtimeRenderTypes.GLOW : com.mystaria.showtime.ShowtimeRenderTypes.OPAQUE);
		Matrix4f m = pose.last().pose();
		WaterLook look = waterLook(sea);
		if (sea.under) {
			drawSeabed(out, m, sea, look, time, glow);
			return;
		}
		double level = seaLevel();
		if (glow) {
			// The sun glittering on the water (sparks coming and going on the waves), or glinting on the ice.
			int count = (int) ((look.frozen ? 60 : 180) * Mth.clamp(look.clarity, 0, 1));
			for (int i = 0; i < count; i++) {
				double a = Math.PI * 2 * hashFloat(i * 13 + 1) + (look.frozen ? 0 : time * 0.0004);
				double r = fieldRadius + sea.margin + 1 + 50 * Math.pow(hashFloat(i * 13 + 2), 1.6);
				float f = Mth.sin(time * (look.frozen ? 0.06f : 0.22f) + i * 2.39f + hashFloat(i * 13 + 3) * 20);
				if (f < 0.6f) {
					continue;
				}
				double x = Math.cos(a) * r;
				double z = Math.sin(a) * r;
				Vec3 p = new Vec3(x, level + waveAt(sea, look.amp, x, z, time) + (look.frozen ? 0.12 : 0.06), z);
				if (nearFade(p, 1, 2) < 0.5f) {
					continue;
				}
				int alpha = (int) ((f - 0.6f) / 0.4f * 210);
				float size = 0.07f + 0.12f * hashFloat(i * 13 + 4);
				quadFacing(out, m, p, cameraRel.subtract(p).normalize(), size, look.frozen ? 0xDDF6FF : 0xFFFFFF, alpha);
			}
			return;
		}
		Vec3 up = new Vec3(0, 1, 0);
		int sectors = 128;
		double[] edges = new double[sectors];
		double maxEdge = 0;
		for (int k = 0; k < sectors; k++) {
			edges[k] = isletEdge(sea, Math.PI * 2 * k / sectors);
			maxEdge = Math.max(maxEdge, edges[k]);
		}
		// The islet, block by block, wet near the water, sunk under it.
		int sand = Ambiance.color(sea.sand, 0xD9C48F);
		drawBlockFloor(out, m, "islet", maxEdge + 2.5, (a, b) -> isletY(sea, a, b), (x, z) -> {
			double y = smoothIsletY(sea, x, z);
			int c = stoneGrain(sand, new Vec3(x, y, z), 0.12f);
			float wet = (float) Mth.clamp(1 - (y - level) / 0.5, 0, 1);
			c = lerpColor(c, lerpColor(sand, 0x000000, 0.45f), 0.6f * wet);
			if (y < level) {
				c = lerpColor(c, look.frozen ? look.water : look.deep, (float) Mth.clamp((level - y) / 1.2, 0, 0.8));
			}
			return lerpColor(0x000000, c, 0.93f);
		}, null, (x, z, r) -> r <= isletEdge(sea, Math.atan2(z, x)) + 2.5);
		// Rocks on the shore, half in the water.
		int rock = Ambiance.color(sea.rock, 0x6E675C);
		for (int i = 0; i < sea.rocks; i++) {
			double a = Math.PI * 2 * (i + 0.6 * hashFloat(i * 53 + 1)) / Math.max(1, sea.rocks);
			double r = isletEdge(sea, a) + 0.4 * hashFloat(i * 53 + 2) - 0.6;
			Vec3 c = new Vec3(Math.cos(a) * r, level + 0.05, Math.sin(a) * r);
			if (nearFade(c, 1.5, 2) < 0.5f) {
				continue;
			}
			boulder(out, m, c, 0.5f + 0.8f * hashFloat(i * 53 + 3), hashFloat(i * 53 + 4) * 6.28, stoneGrain(rock, c, 0.2f), i);
		}
		// The water: per vertex, its height (waves), its colour (depth, crests, slope, foam on the shore, slicks).
		List<Double> radii = seaRadii(fieldRadius + 0.3, maxEdge + 14, sea.radius);
		int fog = active.sky != null ? Ambiance.color(active.sky.fogColor, -1) : -1;
		Vec3[] inner = null;
		int[] innerColor = null;
		for (double r : radii) {
			Vec3[] ring = new Vec3[sectors];
			int[] ringColor = new int[sectors];
			for (int k = 0; k < sectors; k++) {
				double a = Math.PI * 2 * k / sectors;
				double x = Math.cos(a) * r;
				double z = Math.sin(a) * r;
				double calm = Mth.clamp((r - fieldRadius) / (sea.margin + 6), 0.15, 1);
				double amp = look.amp * calm;
				double h = waveAt(sea, amp, x, z, time);
				ring[k] = new Vec3(x, level + h + (look.frozen ? 0.05 : 0), z);
				float far = (float) Mth.clamp((r - edges[k]) / 45, 0, 1);
				int c;
				if (look.frozen) {
					c = lerpColor(look.water, look.deep, 0.3f * far);
					double snow = valueNoise(x * 0.15, 1, z * 0.15);
					c = lerpColor(c, 0xFFFFFF, 0.55f * (float) smooth(Mth.clamp((snow - 0.55) / 0.25, 0, 1)));
					c = stoneGrain(c, ring[k], 0.06f);
				} else {
					c = lerpColor(look.water, look.deep, (float) Math.sqrt(far));
					float crest = amp > 0 ? (float) Mth.clamp(h / amp, 0, 1) : 0;
					c = lerpColor(c, look.foam, 0.25f * crest * crest);
					double hx = waveAt(sea, amp, x + 0.5, z, time);
					double hz = waveAt(sea, amp, x, z + 0.5, time);
					Vec3 normal = new Vec3((h - hx) * 3, 0.5, (h - hz) * 3).normalize();
					c = lerpColor(0x000000, c, (float) (0.7 + 0.3 * Math.max(0, normal.dot(SUN))));
					// Foam breaking on the shore, coming and going.
					double shore = r - edges[k];
					float foam = (float) (Mth.clamp(1 - Math.abs(shore - 0.4) / 1.8, 0, 1) * (0.55 + 0.45 * Math.sin(time * 0.07 - shore * 2.2)));
					c = lerpColor(c, look.foam, 0.75f * foam);
					if (look.oil) {
						double slick = valueNoise(x * 0.06 + time * 0.002, 11, z * 0.06);
						float mask = (float) smooth(Mth.clamp((slick - 0.5) / 0.25, 0, 1));
						double hue = valueNoise(x * 0.25, 5 + time * 0.004, z * 0.25) * 1.5 + r * 0.02;
						int rainbow = Mth.hsvToRgb((float) (hue - Math.floor(hue)), 0.7f, 0.55f) & 0xFFFFFF;
						c = lerpColor(c, rainbow, 0.45f * mask);
					}
					c = stoneGrain(c, ring[k], 0.05f);
				}
				if (fog >= 0) {
					float haze = (float) Mth.clamp((r - sea.radius * 0.6) / (sea.radius * 0.4), 0, 1);
					c = lerpColor(c, fog, haze * haze);
				}
				ringColor[k] = c;
			}
			if (inner != null) {
				for (int k = 0; k < sectors; k++) {
					int n = (k + 1) % sectors;
					quadColors(out, m, inner[k], inner[n], ring[n], ring[k], innerColor[k], innerColor[n], ringColor[n], ringColor[k]);
				}
			}
			inner = ring;
			innerColor = ringColor;
		}
		if (look.frozen) {
			drawIce(out, m, sea, look, level, maxEdge);
		}
	}

	/** The frozen sea's cracks (thin dark lines wandering out from the islet) and its floes (raised slabs of ice). */
	private static void drawIce(VertexConsumer out, Matrix4f m, Ambiance.Sea sea, WaterLook look, double level, double maxEdge) {
		int crackColor = lerpColor(look.water, look.deep, 0.75f);
		for (int i = 0; i < 26; i++) {
			double a = Math.PI * 2 * hashFloat(i * 61 + 1);
			double r = maxEdge + 0.5 + 30 * hashFloat(i * 61 + 2);
			double x = Math.cos(a) * r;
			double z = Math.sin(a) * r;
			double heading = a + (hashFloat(i * 61 + 3) - 0.5) * 2;
			int segments = 6 + (int) (7 * hashFloat(i * 61 + 4));
			for (int s = 0; s < segments; s++) {
				double length = 1.5 + 1.5 * hashFloat(i * 61 + s * 5 + 7);
				heading += (hashFloat(i * 61 + s * 5 + 8) - 0.5) * 1.1;
				double nx = x + Math.cos(heading) * length;
				double nz = z + Math.sin(heading) * length;
				double w = 0.09 * (1 - 0.6 * s / (double) segments);
				double px = -Math.sin(heading) * w;
				double pz = Math.cos(heading) * w;
				double y = level + 0.07;
				quad(out, m, new Vec3(x + px, y, z + pz), new Vec3(nx + px, y, nz + pz), new Vec3(nx - px, y, nz - pz),
						new Vec3(x - px, y, z - pz), crackColor);
				x = nx;
				z = nz;
			}
		}
		// Floes: irregular slabs, whiter in the middle, their sides blue-grey.
		int sides = 7;
		for (int i = 0; i < 24; i++) {
			double a = Math.PI * 2 * hashFloat(i * 71 + 1);
			double r = maxEdge + 1.5 + 22 * Math.pow(hashFloat(i * 71 + 2), 1.3);
			Vec3 centre = new Vec3(Math.cos(a) * r, level + 0.05 + 0.2 + 0.3 * hashFloat(i * 71 + 3), Math.sin(a) * r);
			if (nearFade(centre, 1.5, 2) < 0.5f) {
				continue;
			}
			double size = 0.8 + 1.6 * hashFloat(i * 71 + 4);
			Vec3[] rim = new Vec3[sides];
			for (int k = 0; k < sides; k++) {
				double ang = Math.PI * 2 * k / sides + hashFloat(i * 71 + 5) * 3;
				double rr = size * (0.65 + 0.5 * hashFloat(i * 71 + k * 3 + 9));
				rim[k] = centre.add(Math.cos(ang) * rr, 0, Math.sin(ang) * rr);
			}
			int top = lerpColor(look.water, 0xFFFFFF, 0.55f);
			int edge = look.water;
			int side = lerpColor(look.water, look.deep, 0.45f);
			for (int k = 0; k < sides; k++) {
				int n = (k + 1) % sides;
				quadColors(out, m, centre, rim[k], rim[n], centre, top, edge, edge, top);
				Vec3 lowK = new Vec3(rim[k].x, level - 0.1, rim[k].z);
				Vec3 lowN = new Vec3(rim[n].x, level - 0.1, rim[n].z);
				quadColors(out, m, rim[k], rim[n], lowN, lowK, side, side, look.deep, look.deep);
			}
		}
	}

	/** Under water: the seabed, the rippling surface overhead, rocks, schools of fish; the light from above. */
	private static void drawSeabed(VertexConsumer out, Matrix4f m, Ambiance.Sea sea, WaterLook look, float time, boolean glow) {
		double ground = groundAt(0, 0);
		double surface = ground + sea.depth;
		double reach = Math.min(sea.radius, 72);
		if (glow) {
			glowDisc(out, m, new Vec3(0, surface - 0.3, 0), new Vec3(0, -1, 0), (float) (sea.depth * 1.1), look.light, 46);
			floorDisc(out, m, 0, 0, fieldRadius, look.light, 14, 0.14);
			return;
		}
		if (!seaInBlocks) {
			int sand = Ambiance.color(sea.sand, 0xD9C48F);
			int sectors = 96;
			// The seabed: sand with ripples and dunes, darker and bluer further out, caustics dancing on it.
			Vec3[] inner = null;
			int[] innerColor = null;
			// Block by block out to 48 blocks, smooth beyond (far out in the dark water).
			double smoothFrom = Math.min(reach, 48);
			drawBlockFloor(out, m, "seabed", smoothFrom, BattleAmbiance::seabedY, (x, z) -> {
				double y = smoothSeabedY(x, z);
				int c = lerpColor(0x000000, stoneGrain(sand, new Vec3(x, y, z), 0.14f), 0.91f);
				float far = (float) Mth.clamp((Math.sqrt(x * x + z * z) - fieldRadius) / 50, 0, 1);
				return lerpColor(c, look.deep, 0.35f + 0.6f * (float) Math.pow(far, 0.8));
			}, (x, z, c) -> {
				float far = (float) Mth.clamp((Math.sqrt(x * x + z * z) - fieldRadius) / 50, 0, 1);
				double n = valueNoise(x * 0.18 + time * 0.012, time * 0.015, z * 0.18);
				double band = 1 - Math.abs(2 * n - 1);
				return lerpColor(c, look.light, 0.3f * (float) (band * band) * (1 - far));
			}, (x, z, r) -> true);
			for (double r : seaRadii(smoothFrom, Math.max(smoothFrom, fieldRadius + 16), reach)) {
				Vec3[] ring = new Vec3[sectors];
				int[] ringColor = new int[sectors];
				for (int k = 0; k < sectors; k++) {
					double a = Math.PI * 2 * k / sectors;
					double x = Math.cos(a) * r;
					double z = Math.sin(a) * r;
					double y = smoothSeabedY(x, z);
					ring[k] = new Vec3(x, y - 0.03, z);
					Vec3 normal = new Vec3(y - smoothSeabedY(x + 0.5, z), 0.5, y - smoothSeabedY(x, z + 0.5)).normalize();
					int c = stoneGrain(sand, ring[k], 0.14f);
					c = lerpColor(0x000000, c, (float) (0.65 + 0.35 * Math.max(0, normal.dot(SUN))));
					float far = (float) Mth.clamp((r - fieldRadius) / 50, 0, 1);
					c = lerpColor(c, look.deep, 0.35f + 0.6f * (float) Math.pow(far, 0.8));
					double n = valueNoise(x * 0.18 + time * 0.012, time * 0.015, z * 0.18);
					double band = 1 - Math.abs(2 * n - 1);
					c = lerpColor(c, look.light, 0.3f * (float) (band * band) * (1 - far));
					ringColor[k] = c;
				}
				if (inner != null) {
					for (int k = 0; k < sectors; k++) {
						int n = (k + 1) % sectors;
						quadColors(out, m, inner[k], inner[n], ring[n], ring[k], innerColor[k], innerColor[n], ringColor[n], ringColor[k]);
					}
				}
				inner = ring;
				innerColor = ringColor;
			}
			// The surface overhead: bright straight above (the sky seen through it), darker towards the sides, rippling.
			inner = null;
			innerColor = null;
			double amp = Math.max(0.15, look.amp * 0.8);
			for (double r : seaRadii(0, 30, reach)) {
				Vec3[] ring = new Vec3[sectors];
				int[] ringColor = new int[sectors];
				for (int k = 0; k < sectors; k++) {
					double a = Math.PI * 2 * k / sectors;
					double x = Math.cos(a) * r;
					double z = Math.sin(a) * r;
					double h = waveAt(sea, amp, x, z, time);
					ring[k] = new Vec3(x, surface + h, z);
					float window = (float) Mth.clamp(r / (sea.depth * 1.6), 0, 1);
					int c = lerpColor(look.light, look.water, window);
					c = lerpColor(c, look.deep, (float) Mth.clamp((r - sea.depth * 1.6) / 40, 0, 0.9));
					c = lerpColor(c, look.light, 0.35f * (float) Mth.clamp(h / amp, 0, 1) * (1 - window * 0.6f));
					ringColor[k] = c;
				}
				if (inner != null) {
					for (int k = 0; k < sectors; k++) {
						int n = (k + 1) % sectors;
						quadColors(out, m, inner[k], inner[n], ring[n], ring[k], innerColor[k], innerColor[n], ringColor[n], ringColor[k]);
					}
				}
				inner = ring;
				innerColor = ringColor;
			}
			int rock = lerpColor(Ambiance.color(sea.rock, 0x6E675C), look.deep, 0.3f);
			for (int i = 0; i < sea.rocks + 4; i++) {
				double a = Math.PI * 2 * (i + 0.6 * hashFloat(i * 53 + 1)) / (sea.rocks + 4);
				double r = fieldRadius + 3 + 15 * hashFloat(i * 53 + 2);
				double x = Math.cos(a) * r;
				double z = Math.sin(a) * r;
				Vec3 c = new Vec3(x, seabedY(x, z), z);
				if (nearFade(c, 1.5, 2) < 0.5f) {
					continue;
				}
				boulder(out, m, c, 0.6f + 1.1f * hashFloat(i * 53 + 3), hashFloat(i * 53 + 4) * 6.28, stoneGrain(rock, c, 0.2f), i);
			}
		}
		// Schools of fish circling the field, each fish a slim body and a tail, its back darker than its belly.
		for (int s = 0; s < 5; s++) {
			double dir = s % 2 == 0 ? 1 : -1;
			double orbit = fieldRadius + 4 + 10 * hashFloat(s * 91 + 1);
			double height = ground + 2.5 + 5 * hashFloat(s * 91 + 2);
			double angle = hashFloat(s * 91 + 3) * 6.28 + dir * time * 0.004 * (1 + hashFloat(s * 91 + 4));
			int color = FISH_COLORS[s % FISH_COLORS.length];
			for (int f = 0; f < 8; f++) {
				double fa = angle + (hashFloat(s * 91 + f * 7 + 10) - 0.5) * 0.25;
				double fr = orbit + (hashFloat(s * 91 + f * 7 + 11) - 0.5) * 3;
				double fy = height + (hashFloat(s * 91 + f * 7 + 12) - 0.5) * 2 + 0.3 * Math.sin(time * 0.05 + f + s);
				Vec3 at = new Vec3(Math.cos(fa) * fr, fy, Math.sin(fa) * fr);
				if (nearFade(at, 1, 1.5) < 0.5f) {
					continue;
				}
				Vec3 heading = new Vec3(-Math.sin(fa) * dir, 0, Math.cos(fa) * dir);
				double size = 0.3 + 0.25 * hashFloat(s * 91 + f * 7 + 13);
				double wag = 0.35 * Math.sin(time * 0.6 + f * 1.3);
				Vec3 side = heading.cross(new Vec3(0, 1, 0)).normalize();
				Vec3 nose = at.add(heading.scale(size));
				Vec3 tail = at.add(heading.scale(-size * 0.9));
				Vec3 top = at.add(0, size * 0.32, 0);
				Vec3 belly = at.add(0, -size * 0.28, 0);
				int back = lerpColor(color, 0x000000, 0.35f);
				int under = lerpColor(color, 0xFFFFFF, 0.3f);
				quadColors(out, m, nose, top, tail, belly, color, back, color, under);
				Vec3 fin = tail.add(heading.scale(-size * 0.45)).add(side.scale(wag * size));
				quadColors(out, m, tail, fin.add(0, size * 0.3, 0), fin.add(0, -size * 0.3, 0), tail, color, back, under, color);
			}
		}
	}

	/** The sea's anchors: the islet's beach (or the seabed round the field) and the air above it. */
	private static Anchors seaAnchors(Ambiance.Sea sea) {
		Anchors result = new Anchors();
		for (int i = 0; i < 160; i++) {
			double a = Math.PI * 2 * hashFloat(i * 19 + 1);
			double x;
			double z;
			double y;
			if (sea.under) {
				double r = fieldRadius + 1 + 14 * hashFloat(i * 19 + 2);
				x = Math.cos(a) * r;
				z = Math.sin(a) * r;
				y = seabedY(x, z);
			} else {
				double r = Mth.lerp(hashFloat(i * 19 + 2), fieldRadius + 0.8, Math.max(fieldRadius + 1, isletEdge(sea, a) - 0.4));
				x = Math.cos(a) * r;
				z = Math.sin(a) * r;
				y = isletY(sea, x, z);
			}
			result.add(Anchors.Kind.FLOOR, new Anchors.Anchor(new Vec3(x, y - 0.12, z), new Vec3(0, 1, 0), 1f));
		}
		result.fillFloat(fieldRadius, groundAt(0, 0));
		return result;
	}

	// ---------------------------------------------------------------- Lands and halls (2026-11-19, League Reborn)

	/** The place's floor at (x, z): a land's, an islet's, a seabed's, a hall's; else the real ground's top. */
	private static double floorY(double x, double z) {
		if (active.land != null) {
			return landY(active.land, x, z);
		}
		if (active.sea != null) {
			return active.sea.under ? seabedY(x, z) : isletY(active.sea, x, z);
		}
		if (active.hall != null) {
			return hallY(x, z);
		}
		return groundAt(x, z) + 0.12;
	}

	/** {@code ring}'s value at {@code t} (0..1): a [min, max] list. */
	private static double within(List<Float> ring, double t) {
		return Mth.lerp(t, ring.get(0), ring.get(ring.size() - 1));
	}

	/** A land's ground at (x, z): in whole blocks. */
	private static double landY(Ambiance.Land land, double x, double z) {
		return snapToBlock((a, b) -> smoothLandY(land, a, b), x, z);
	}

	/** A land's smooth ground at (x, z): the real ground under the field, level a little further, then its relief. */
	private static double smoothLandY(Ambiance.Land land, double x, double z) {
		double r = Math.sqrt(x * x + z * z);
		double from = fieldRadius + 0.5;
		if (r <= from) {
			return groundAt(x, z) + 0.09;
		}
		double rim = groundAt(x * from / r, z * from / r) + 0.09;
		double base = groundAt(0, 0) + 0.09;
		double t = smooth(Mth.clamp((r - from) / Math.max(1, land.flat), 0, 1));
		return Mth.lerp(t, rim, base + landRelief(land, x, z, r - from - land.flat));
	}

	/** The relief beyond the flat, {@code out} blocks past it: hills, dunes, a summit's fall, a marsh's dips. */
	private static double landRelief(Ambiance.Land land, double x, double z, double out) {
		double o = Math.max(0, out);
		double grow = Mth.clamp(o / 12, 0, 1);
		switch (land.style) {
			case "dunes": {
				double crest = 0.5 + 0.5 * Math.sin(x * 0.07 + z * 0.03 + 4 * valueNoise(x * 0.02, 2, z * 0.02));
				return land.hills * crest * crest * grow + 0.06 * Math.sin(x * 0.9 + z * 0.3 + 2 * valueNoise(x * 0.2, 3, z * 0.2));
			}
			case "summit":
				return -Math.pow(o, 1.35) * 0.55 - land.hills * valueNoise(x * 0.08, 4, z * 0.08) * grow;
			case "marsh":
				return land.hills * (valueNoise(x * 0.05, 5, z * 0.05) - 0.5) * grow;
			default:
				return land.hills * 2 * (0.75 * valueNoise(x * 0.035, 6, z * 0.035) + 0.25 * valueNoise(x * 0.09, 7, z * 0.09) - 0.3)
						* grow;
		}
	}

	// ---------------------------------------------------------------- Block floors (2026-11-19)

	/** A block floor's tops and corner colours, by world block: noise is costly and the field stays put. */
	private static final class FloorCache {
		final java.util.Map<Long, Double> tops = new java.util.HashMap<>();
		final java.util.Map<Long, Integer> colors = new java.util.HashMap<>();
	}

	private static final java.util.Map<String, FloorCache> FLOOR_CACHES = new java.util.HashMap<>();

	private static FloorCache floorCache(String layer) {
		FloorCache cache = FLOOR_CACHES.computeIfAbsent(layer, k -> new FloorCache());
		if (cache.tops.size() > 200000 || cache.colors.size() > 200000) {
			cache.tops.clear();
			cache.colors.clear();
		}
		return cache;
	}

	/** Heights and colours are relative to the field: built again when it moves, or the ambiance changes. */
	private static void clearFloors() {
		GROUND.clear();
		BLOCK_TOPS.clear();
		FLOOR_CACHES.clear();
	}

	private static long blockKey(int bx, int bz) {
		return ((long) bx << 32) ^ (bz & 0xFFFFFFFFL);
	}

	/**
	 * The ground at (x, z) in whole blocks: under the field, the real block's top (people stand on it); beyond, the
	 * smooth ground's height at the block's middle, rounded to a whole block above the field's level.
	 */
	private static double snapToBlock(java.util.function.DoubleBinaryOperator smoothY, double x, double z) {
		double cx = Math.floor(center.x + x) + 0.5 - center.x;
		double cz = Math.floor(center.z + z) + 0.5 - center.z;
		double from = fieldRadius + 0.5;
		if (cx * cx + cz * cz <= from * from) {
			return groundAt(x, z) + 0.09;
		}
		double base = groundAt(0, 0);
		return base + Math.floor(smoothY.applyAsDouble(cx, cz) - 0.09 - base + 0.5) + 0.09;
	}

	/** A floor's colour at a block corner (x, z), lit as a top face. */
	private interface FloorColor {
		int at(double x, double z);
	}

	/** A floor's colour at a corner changed every frame (light moving over it), after the cached one. */
	private interface FloorTint {
		int tint(double x, double z, int color);
	}

	/** Whether the block whose middle is (cx, cz), {@code r} from the centre, is part of the floor. */
	private interface FloorCell {
		boolean in(double cx, double cz, double r);
	}

	/**
	 * A floor block by block (2026-11-19): every block of the world's grid within {@code reach} a top at its height
	 * ({@code top}, the floor's height function, already in whole blocks) and sides down to each lower neighbour.
	 * Coloured per vertex: the colour is a function of the corner, shared by the faces meeting there, so the floor's
	 * patches and grain run across the blocks like a texture laid on them. Faces lit like Minecraft's: tops brightest,
	 * the sides by their direction, darker at the foot of a step.
	 */
	private static void drawBlockFloor(VertexConsumer out, Matrix4f m, String layer, double reach,
			java.util.function.DoubleBinaryOperator top, FloorColor color, FloorTint tint, FloorCell cell) {
		drawBlockFloor(out, m, layer, reach, top, color, tint, cell, null, 0);
	}

	/**
	 * The same, paved with {@code tiles} out to {@code paved} blocks from the centre: each tile its own shade over the
	 * floor's colours, and a joint strip along the tile edges, laid just above the top.
	 */
	private static void drawBlockFloor(VertexConsumer out, Matrix4f m, String layer, double reach,
			java.util.function.DoubleBinaryOperator top, FloorColor color, FloorTint tint, FloorCell cell, Ambiance.Tiles tiles,
			double paved) {
		FloorCache cache = floorCache(layer);
		int tileSize = tiles != null ? Math.max(1, tiles.size) : 1;
		int joint = tiles != null ? Ambiance.color(tiles.joint, 0xA8A094) : 0;
		int bx0 = Mth.floor(center.x - reach);
		int bx1 = Mth.floor(center.x + reach);
		int bz0 = Mth.floor(center.z - reach);
		int bz1 = Mth.floor(center.z + reach);
		int w = bx1 - bx0 + 2;
		int h = bz1 - bz0 + 2;
		// Each corner's colour once per frame (tints move), each from the cache.
		int[] corner = new int[w * h];
		boolean[] done = new boolean[w * h];
		for (int bx = bx0; bx <= bx1; bx++) {
			for (int bz = bz0; bz <= bz1; bz++) {
				double ax = bx - center.x;
				double az = bz - center.z;
				double cx = ax + 0.5;
				double cz = az + 0.5;
				double r = Math.sqrt(cx * cx + cz * cz);
				if (r > reach || !cell.in(cx, cz, r)) {
					continue;
				}
				double y = floorTop(cache, top, bx, bz);
				int[] k = new int[4];
				for (int c = 0; c < 4; c++) {
					int ix = bx + (c == 1 || c == 2 ? 1 : 0);
					int iz = bz + (c >= 2 ? 1 : 0);
					int slot = (ix - bx0) * h + (iz - bz0);
					if (!done[slot]) {
						long key = blockKey(ix, iz);
						Integer cached = cache.colors.get(key);
						if (cached == null) {
							cached = color.at(ix - center.x, iz - center.z);
							cache.colors.put(key, cached);
						}
						corner[slot] = tint != null ? tint.tint(ix - center.x, iz - center.z, cached) : cached;
						done[slot] = true;
					}
					k[c] = corner[slot];
				}
				boolean paving = tiles != null && r <= paved;
				if (paving) {
					// Each tile a shade of its own: lighter or darker as a whole, so the tiles read apart.
					int tx = Math.floorDiv(bx, tileSize);
					int tz = Math.floorDiv(bz, tileSize);
					float shade = (hashFloat(tx * 7349 + tz * 3571 + 17) - 0.5f) * 2 * tiles.shade;
					int toward = shade > 0 ? 0xFFFFFF : 0x000000;
					for (int c = 0; c < 4; c++) {
						k[c] = lerpColor(k[c], toward, Math.abs(shade));
					}
				}
				// Corners 0 (ax, az), 1 (ax+1, az), 2 (ax+1, az+1), 3 (ax, az+1).
				Vec3 p0 = new Vec3(ax, y, az);
				Vec3 p1 = new Vec3(ax + 1, y, az);
				Vec3 p2 = new Vec3(ax + 1, y, az + 1);
				Vec3 p3 = new Vec3(ax, y, az + 1);
				quadColors(out, m, p0, p1, p2, p3, k[0], k[1], k[2], k[3]);
				if (paving) {
					drawJoints(out, m, bx, bz, ax, az, y, tileSize, k, joint);
				}
				for (int d = 0; d < 4; d++) {
					int dx = d == 0 ? 1 : d == 1 ? -1 : 0;
					int dz = d == 2 ? 1 : d == 3 ? -1 : 0;
					double below = floorTop(cache, top, bx + dx, bz + dz);
					if (below > y - 0.01) {
						continue;
					}
					Vec3 e0;
					Vec3 e1;
					int q0;
					int q1;
					if (dx == 1) {
						e0 = p1;
						e1 = p2;
						q0 = k[1];
						q1 = k[2];
					} else if (dx == -1) {
						e0 = p3;
						e1 = p0;
						q0 = k[3];
						q1 = k[0];
					} else if (dz == 1) {
						e0 = p2;
						e1 = p3;
						q0 = k[2];
						q1 = k[3];
					} else {
						e0 = p0;
						e1 = p1;
						q0 = k[0];
						q1 = k[1];
					}
					// Minecraft's face light: east and west darker than north and south.
					float face = dx != 0 ? 0.62f : 0.8f;
					double drop = Math.min(y - below, 24);
					float foot = (float) Math.min(0.4, 0.22 * drop);
					int t0 = lerpColor(0x000000, q0, face);
					int t1 = lerpColor(0x000000, q1, face);
					quadColors(out, m, e0, e1, e1.add(0, -drop, 0), e0.add(0, -drop, 0), t0, t1, lerpColor(t1, 0x000000, foot),
							lerpColor(t0, 0x000000, foot));
				}
			}
		}
	}

	/**
	 * The joints along a paved block's tile edges: thin strips just above the top (the block's own half of the joint;
	 * the neighbour draws the other), coloured from the stone's corners toward the joint's colour.
	 */
	private static void drawJoints(VertexConsumer out, Matrix4f m, int bx, int bz, double ax, double az, double y, int size,
			int[] k, int joint) {
		final double w = 0.045;
		double jy = y + 0.004;
		float mix = 0.75f;
		if (Math.floorMod(bx, size) == 0) { // west edge
			quadColors(out, m, new Vec3(ax, jy, az), new Vec3(ax + w, jy, az), new Vec3(ax + w, jy, az + 1), new Vec3(ax, jy, az + 1),
					lerpColor(k[0], joint, mix), lerpColor(k[0], joint, mix), lerpColor(k[3], joint, mix), lerpColor(k[3], joint, mix));
		}
		if (Math.floorMod(bx + 1, size) == 0) { // east edge
			quadColors(out, m, new Vec3(ax + 1 - w, jy, az), new Vec3(ax + 1, jy, az), new Vec3(ax + 1, jy, az + 1),
					new Vec3(ax + 1 - w, jy, az + 1), lerpColor(k[1], joint, mix), lerpColor(k[1], joint, mix),
					lerpColor(k[2], joint, mix), lerpColor(k[2], joint, mix));
		}
		if (Math.floorMod(bz, size) == 0) { // north edge
			quadColors(out, m, new Vec3(ax, jy, az), new Vec3(ax + 1, jy, az), new Vec3(ax + 1, jy, az + w), new Vec3(ax, jy, az + w),
					lerpColor(k[0], joint, mix), lerpColor(k[1], joint, mix), lerpColor(k[1], joint, mix), lerpColor(k[0], joint, mix));
		}
		if (Math.floorMod(bz + 1, size) == 0) { // south edge
			quadColors(out, m, new Vec3(ax, jy, az + 1 - w), new Vec3(ax + 1, jy, az + 1 - w), new Vec3(ax + 1, jy, az + 1),
					new Vec3(ax, jy, az + 1), lerpColor(k[3], joint, mix), lerpColor(k[2], joint, mix), lerpColor(k[2], joint, mix),
					lerpColor(k[3], joint, mix));
		}
	}

	private static double floorTop(FloorCache cache, java.util.function.DoubleBinaryOperator top, int bx, int bz) {
		long key = blockKey(bx, bz);
		Double cached = cache.tops.get(key);
		if (cached == null) {
			cached = top.applyAsDouble(bx + 0.5 - center.x, bz + 0.5 - center.z) - 0.03;
			cache.tops.put(key, cached);
		}
		return cached;
	}

	/**
	 * Marble's veins at (x, z): thin winding lines where a warped wave crosses zero, in world coordinates so they run on
	 * across the tiles; two sets at an angle, the second fainter.
	 */
	private static int veined(int c, int vein, double x, double z) {
		double wx = center.x + x;
		double wz = center.z + z;
		double warp = 3.2 * valueNoise(wx * 0.11, 21, wz * 0.11);
		double a = Math.abs(Math.sin((wx * 0.31 + wz * 0.17) + warp));
		double warp2 = 2.6 * valueNoise(wx * 0.07 + 40, 22, wz * 0.07);
		double b = Math.abs(Math.sin((wx * -0.12 + wz * 0.29) + warp2));
		float strong = (float) Mth.clamp((0.16 - a) / 0.16, 0, 1);
		float faint = (float) Mth.clamp((0.1 - b) / 0.1, 0, 1) * 0.5f;
		return lerpColor(c, vein, 0.7f * Math.max(strong * strong, faint));
	}

	/** A land's ground colour at (x, z): its patches, cliffs on steep slopes, grain, snow, the sun, the haze. */
	private static int landColor(Ambiance.Land land, double x, double z, int c0, int c1, int c2, int cliff, int fog, boolean flatTop) {
		double y = smoothLandY(land, x, z);
		Vec3 p = new Vec3(x, y - 0.03, z);
		Vec3 normal = new Vec3(y - smoothLandY(land, x + 0.5, z), 0.5, y - smoothLandY(land, x, z + 0.5)).normalize();
		double patch = valueNoise(x * 0.06, 9, z * 0.06);
		double patch2 = valueNoise(x * 0.17 + 30, 9, z * 0.17);
		int c = lerpColor(c0, c1, (float) smooth(Mth.clamp((patch - 0.35) / 0.3, 0, 1)));
		c = lerpColor(c, c2, 0.7f * (float) smooth(Mth.clamp((patch2 - 0.55) / 0.25, 0, 1)));
		c = lerpColor(c, cliff, (float) smooth(Mth.clamp((0.85 - normal.y) / 0.25, 0, 1)));
		c = stoneGrain(c, p, land.grain);
		if (land.tiles != null && land.tiles.vein != null) {
			c = veined(c, Ambiance.color(land.tiles.vein, 0x8A8A94), x, z);
		}
		c = snowed(c, p, flatTop ? new Vec3(0, 1, 0) : normal);
		c = sunlit(c, flatTop ? new Vec3(0, 1, 0) : normal, 0.68f);
		if (fog >= 0) {
			double r = Math.sqrt(x * x + z * z);
			float haze = (float) Mth.clamp((r - land.radius * 0.55) / (land.radius * 0.45), 0, 1);
			c = lerpColor(c, fog, haze * haze);
		}
		return c;
	}

	/** The fog colour of the ambiance, or -1: far surfaces fade into it so the horizon has no hard edge. */
	private static int fogColor() {
		return active.sky != null ? Ambiance.color(active.sky.fogColor, -1) : -1;
	}

	/** Sunlit: the colour lit by a surface's normal (ambient {@code low}). */
	private static int sunlit(int color, Vec3 normal, float low) {
		return lerpColor(0x000000, color, (float) (low + (1 - low) * Math.max(0, normal.dot(SUN))));
	}

	/** Snow settling on a surface facing {@code normal} at {@code p}, with the ambiance's {@code snow_cover}. */
	private static int snowed(int color, Vec3 p, Vec3 normal) {
		float cover = active.snowCover;
		if (cover <= 0) {
			return color;
		}
		double n = valueNoise(p.x * 0.12, p.y * 0.12 + 8, p.z * 0.12);
		float amount = (float) smooth(Mth.clamp(n * 0.6 + normal.y * 0.8 - 1.05 + cover * 0.9, 0, 1));
		return lerpColor(color, 0xF4F8FF, amount);
	}

	/**
	 * A land stadium. Solid pass: the ground (patches of its colours, its grain, lit by its slope, snowed, fading into
	 * the fog), the ranges far off, the sea of clouds, trees, rocks, pools, cracks, columns, junk. Light pass: the
	 * cracks' and the pools' glow, bubbles, and the fire when the ambiance sets the place ablaze.
	 */
	private static void drawLand(PoseStack pose, MultiBufferSource buffers, Ambiance.Land land, float time, boolean glow) {
		if (!glow) {
			// Built of Minecraft's blocks (2026-11-19): lit and shaded by shader packs like the world. The smooth land
			// stands in while the blocks are built, in the background. The asking comes before any buffer is (rule 9).
			landInBlocks = com.mystaria.showtime.ambiance.blocks.BlockScenery.drawLand(pose, buffers, center, cameraWorld, frustum,
					land, active.grass, active.flowers, active.snowCover, active.blaze != null, fieldRadius, BattleAmbiance::groundAt);
			if (landInBlocks) {
				if (land.clouds != null) {
					drawCloudSea(ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.OPAQUE), pose.last().pose(), land.clouds,
							groundAt(0, 0) + 0.09, time);
				}
				return;
			}
		}
		VertexConsumer out = ambianceBuffer(buffers, glow ? com.mystaria.showtime.ShowtimeRenderTypes.GLOW : com.mystaria.showtime.ShowtimeRenderTypes.OPAQUE);
		Matrix4f m = pose.last().pose();
		double base = groundAt(0, 0) + 0.09;
		if (glow) {
			if (land.cracks != null) {
				drawCracks(out, m, land.cracks, time, true);
			}
			if (land.pools != null) {
				drawPools(out, m, land.pools, time, true);
			}
			if (active.blaze != null) {
				drawBlaze(out, m, land, active.blaze, time);
			}
			return;
		}
		int fog = fogColor();
		int c0 = Ambiance.color(land.ground.get(0), 0x6A9A3A);
		int c1 = Ambiance.color(land.ground.get(Math.min(1, land.ground.size() - 1)), c0);
		int c2 = Ambiance.color(land.ground.get(land.ground.size() - 1), c1);
		int cliff = Ambiance.color(land.rocks != null ? land.rocks.color : null, lerpColor(c0, 0x6A645C, 0.6f));
		if (active.blaze != null) {
			c0 = lerpColor(c0, 0x2A1E16, 0.65f);
			c1 = lerpColor(c1, 0x3A2A1C, 0.6f);
			c2 = lerpColor(c2, 0x1E1612, 0.7f);
		}
		int sectors = 128;
		Vec3[] inner = null;
		int[] innerColor = null;
		double paved = land.tiles == null ? 0 : land.tiles.radius > 0 ? land.tiles.radius : fieldRadius + land.flat;
		final int g0 = c0;
		final int g1 = c1;
		final int g2 = c2;
		// Blocks out to 56 blocks, the smooth ground beyond (far off, in the haze, nobody tells).
		double smoothFrom = Math.min(land.radius, 56);
		drawBlockFloor(out, m, "land", smoothFrom, (a, b) -> landY(land, a, b),
				(a, b) -> landColor(land, a, b, g0, g1, g2, cliff, fog, true), null, (a, b, r) -> true, land.tiles, paved);
		for (double r : seaRadii(smoothFrom, Math.max(smoothFrom, fieldRadius + land.flat + 16), land.radius)) {
			Vec3[] ring = new Vec3[sectors];
			int[] ringColor = new int[sectors];
			for (int k = 0; k < sectors; k++) {
				double a = Math.PI * 2 * k / sectors;
				double x = Math.cos(a) * r;
				double z = Math.sin(a) * r;
				ring[k] = new Vec3(x, smoothLandY(land, x, z) - 0.03, z);
				ringColor[k] = landColor(land, x, z, c0, c1, c2, cliff, fog, false);
			}
			if (inner != null) {
				for (int k = 0; k < sectors; k++) {
					int n = (k + 1) % sectors;
					quadColors(out, m, inner[k], inner[n], ring[n], ring[k], innerColor[k], innerColor[n], ringColor[n], ringColor[k]);
				}
			}
			inner = ring;
			innerColor = ringColor;
		}
		if (land.ranges != null) {
			drawRanges(out, m, land.ranges, base, fog);
		}
		if (land.clouds != null) {
			drawCloudSea(out, m, land.clouds, base, time);
		}
		if (land.pools != null) {
			drawPools(out, m, land.pools, time, false);
		}
		if (land.cracks != null) {
			drawCracks(out, m, land.cracks, time, false);
		}
		if (land.rocks != null) {
			Ambiance.Rocks rocks = land.rocks;
			int color = Ambiance.color(rocks.color, 0x7A7268);
			for (int i = 0; i < rocks.count + rocks.spires; i++) {
				double a = Math.PI * 2 * hashFloat(i * 67 + 1);
				double r = fieldRadius + within(rocks.ring, Math.sqrt(hashFloat(i * 67 + 2)));
				double x = Math.cos(a) * r;
				double z = Math.sin(a) * r;
				Vec3 at = new Vec3(x, landY(land, x, z), z);
				if (nearFade(at, 1.5, 2) < 0.5f) {
					continue;
				}
				float size = (float) within(rocks.size, hashFloat(i * 67 + 3));
				int c = snowed(stoneGrain(color, at, 0.2f), at, new Vec3(0, 1, 0));
				if (i < rocks.count) {
					boulder(out, m, at.add(0, size * 0.25, 0), size, hashFloat(i * 67 + 4) * 6.28, c, i);
				} else {
					double h = size * (3 + 3 * hashFloat(i * 67 + 5));
					Vec3 tip = at.add((hashFloat(i * 67 + 6) - 0.5) * h * 0.2, h, (hashFloat(i * 67 + 7) - 0.5) * h * 0.2);
					cone(out, m, at.add(0, -0.5, 0), tip, size * 1.1f, lerpColor(c, 0x000000, 0.3f), snowed(c, tip, new Vec3(0, 1, 0)), i);
				}
			}
		}
		if (land.trees != null) {
			drawTrees(out, m, land, land.trees, time);
		}
		if (land.columns != null) {
			drawColumns(out, m, land.columns);
		}
		if (land.junk != null) {
			Ambiance.Junk junk = land.junk;
			for (int i = 0; i < junk.count; i++) {
				double a = Math.PI * 2 * hashFloat(i * 73 + 1);
				double r = fieldRadius + within(junk.ring, Math.sqrt(hashFloat(i * 73 + 2)));
				double x = Math.cos(a) * r;
				double z = Math.sin(a) * r;
				Vec3 at = new Vec3(x, landY(land, x, z) - 0.05, z);
				if (nearFade(at, 1.5, 2) < 0.5f) {
					continue;
				}
				int c = Ambiance.color(junk.colors.get(i % junk.colors.size()), 0x7A4A2A);
				Vec3 tip;
				if (hashFloat(i * 73 + 3) < 0.35) {
					double yaw = hashFloat(i * 73 + 4) * 6.28;
					at = at.add(0, 0.38, 0);
					tip = at.add(Math.cos(yaw) * 1.0, 0, Math.sin(yaw) * 1.0);
				} else {
					tip = at.add(0, 1.0, 0);
				}
				lathe(out, m, at, tip, new double[] {0, 0.36, 0.38, 0.36, 0.38, 0.36, 0}, new double[] {0, 0, 0.2, 0.5, 0.8, 1, 1}, 10, c, 0.25f, i);
			}
		}
	}

	/** Mountain ranges in layers round the horizon, each paler (further) than the one before; snow on the peaks. */
	private static void drawRanges(VertexConsumer out, Matrix4f m, Ambiance.Ranges ranges, double base, int fog) {
		int color = Ambiance.color(ranges.color, 0x5A6A7A);
		int snow = ranges.snow != null ? Ambiance.color(ranges.snow, 0xF4F8FF) : (active.snowCover > 0.3f ? 0xF4F8FF : -1);
		int columns = 160;
		for (int l = ranges.layers - 1; l >= 0; l--) {
			double distance = ranges.distance * (1 + 0.22 * l);
			float far = fog >= 0 ? 0.25f + 0.5f * l / Math.max(1, ranges.layers) : 0;
			Vec3 prevTop = null;
			Vec3 prevFoot = null;
			int prevTopColor = 0;
			int prevFootColor = 0;
			for (int k = 0; k <= columns; k++) {
				double a = Math.PI * 2 * k / columns;
				double cx = Math.cos(a) * 3 + l * 7;
				double cz = Math.sin(a) * 3;
				double ridge = 0.7 * valueNoise(cx, l, cz) + 0.3 * valueNoise(cx * 3.1, l + 5, cz * 3.1);
				double h = ranges.height * (0.25 + Math.pow(ridge, 1.6) * 1.3) * (1 - 0.15 * l);
				Vec3 top = new Vec3(Math.cos(a) * distance, base + h, Math.sin(a) * distance);
				Vec3 foot = new Vec3(Math.cos(a) * distance, base - 30, Math.sin(a) * distance);
				int topColor = lerpColor(color, 0xFFFFFF, 0.12f);
				if (snow >= 0) {
					topColor = lerpColor(topColor, snow, (float) smooth(Mth.clamp((h / ranges.height - 0.55) / 0.35, 0, 1)));
				}
				int footColor = lerpColor(color, 0x000000, 0.25f);
				if (fog >= 0) {
					topColor = lerpColor(topColor, fog, far);
					footColor = lerpColor(footColor, fog, Math.min(1, far + 0.2f));
				}
				if (prevTop != null) {
					quadColors(out, m, prevFoot, foot, top, prevTop, prevFootColor, footColor, topColor, prevTopColor);
				}
				prevTop = top;
				prevFoot = foot;
				prevTopColor = topColor;
				prevFootColor = footColor;
			}
		}
	}

	/** A sea of clouds below a summit, billowing and drifting. */
	private static void drawCloudSea(VertexConsumer out, Matrix4f m, Ambiance.CloudSea clouds, double base, float time) {
		int color = Ambiance.color(clouds.color, 0xF4F8FF);
		int shade = Ambiance.color(clouds.shade, 0xB8C8DC);
		int sectors = 96;
		Vec3[] inner = null;
		int[] innerColor = null;
		for (double r : seaRadii(fieldRadius, fieldRadius + 30, 150)) {
			Vec3[] ring = new Vec3[sectors];
			int[] ringColor = new int[sectors];
			for (int k = 0; k < sectors; k++) {
				double a = Math.PI * 2 * k / sectors;
				double x = Math.cos(a) * r;
				double z = Math.sin(a) * r;
				double n = 0.65 * valueNoise(x * 0.04 + time * 0.002, 2, z * 0.04) + 0.35 * valueNoise(x * 0.11 + time * 0.003, 5, z * 0.11);
				ring[k] = new Vec3(x, base - clouds.depth + 4 * n, z);
				ringColor[k] = lerpColor(shade, color, (float) smooth(Mth.clamp(n * 1.4 - 0.2, 0, 1)));
			}
			if (inner != null) {
				for (int k = 0; k < sectors; k++) {
					int n = (k + 1) % sectors;
					quadColors(out, m, inner[k], inner[n], ring[n], ring[k], innerColor[k], innerColor[n], ringColor[n], ringColor[k]);
				}
			}
			inner = ring;
			innerColor = ringColor;
		}
	}

	/**
	 * A solid of revolution from {@code base} to {@code tip}: radii {@code r} at fractions {@code f} of the length,
	 * {@code sides} round, coloured per vertex (lit by its normal, grained): trunks, barrels, columns, chess pieces.
	 */
	private static void lathe(VertexConsumer out, Matrix4f m, Vec3 base, Vec3 tip, double[] r, double[] f, int sides, int color,
			float grain, double spin) {
		Vec3[] frame = crystalFrame(base, tip);
		Vec3 axis = tip.subtract(base);
		double length = axis.length();
		Vec3 ax = axis.scale(1 / Math.max(1.0E-6, length));
		Vec3[] prev = null;
		int[] prevColor = null;
		for (int i = 0; i < r.length; i++) {
			int i0 = Math.max(0, i - 1);
			int i1 = Math.min(r.length - 1, i + 1);
			double dr = r[i1] - r[i0];
			double dy = (f[i1] - f[i0]) * length;
			Vec3[] ring = new Vec3[sides];
			int[] ringColor = new int[sides];
			for (int k = 0; k < sides; k++) {
				double a = Math.PI * 2 * k / sides + spin;
				Vec3 dir = frame[0].scale(Math.cos(a)).add(frame[1].scale(Math.sin(a)));
				ring[k] = base.add(ax.scale(f[i] * length)).add(dir.scale(r[i]));
				Vec3 normal = dir.scale(dy).add(ax.scale(-dr));
				normal = normal.lengthSqr() < 1.0E-8 ? ax : normal.normalize();
				ringColor[k] = sunlit(stoneGrain(color, ring[k], grain), normal, 0.5f);
			}
			if (prev != null) {
				for (int k = 0; k < sides; k++) {
					int n = (k + 1) % sides;
					quadColors(out, m, prev[k], prev[n], ring[n], ring[k], prevColor[k], prevColor[n], ringColor[n], ringColor[k]);
				}
			}
			prev = ring;
			prevColor = ringColor;
		}
	}

	/** A lumpy ball of leaves, coloured per vertex: lit on top, dark beneath, grained, snowed, charred when ablaze. */
	private static void crown(VertexConsumer out, Matrix4f m, Vec3 c, double radius, double squash, int color, int seed) {
		int stacks = 6;
		int slices = 10;
		Vec3[][] grid = new Vec3[stacks + 1][slices];
		int[][] colors = new int[stacks + 1][slices];
		for (int j = 0; j <= stacks; j++) {
			double v = Math.PI * j / stacks;
			for (int k = 0; k < slices; k++) {
				double u = Math.PI * 2 * k / slices + seed;
				Vec3 dir = new Vec3(Math.sin(v) * Math.cos(u), Math.cos(v), Math.sin(v) * Math.sin(u));
				double lump = 0.8 + 0.4 * valueNoise(dir.x * 2 + seed, dir.y * 2, dir.z * 2);
				Vec3 p = c.add(dir.x * radius * lump, dir.y * radius * lump * squash, dir.z * radius * lump);
				grid[j][k] = p;
				int col = stoneGrain(color, p, 0.18f);
				col = snowed(col, p, dir);
				colors[j][k] = sunlit(col, dir, 0.4f);
			}
		}
		for (int j = 0; j < stacks; j++) {
			for (int k = 0; k < slices; k++) {
				int n = (k + 1) % slices;
				quadColors(out, m, grid[j][k], grid[j][n], grid[j + 1][n], grid[j + 1][k], colors[j][k], colors[j][n],
						colors[j + 1][n], colors[j + 1][k]);
			}
		}
	}

	/** Where tree {@code i} stands and how tall it is: {x, y, z, height}. */
	private static double[] treeSpot(Ambiance.Land land, Ambiance.Trees trees, int i) {
		double a = Math.PI * 2 * (i + 0.7 * hashFloat(i * 83 + 1)) / Math.max(1, trees.count);
		double r = fieldRadius + within(trees.ring, Math.pow(hashFloat(i * 83 + 2), 0.8));
		double x = Math.cos(a) * r;
		double z = Math.sin(a) * r;
		return new double[] {x, landY(land, x, z), z, within(trees.height, hashFloat(i * 83 + 3))};
	}

	/** Trees: broadleaf (a trunk, two or three crowns), pine (stacked cones), dead (bare branches). */
	private static void drawTrees(VertexConsumer out, Matrix4f m, Ambiance.Land land, Ambiance.Trees trees, float time) {
		int trunk = Ambiance.color(trees.trunk, 0x5A3E26);
		boolean ablaze = active.blaze != null;
		if (ablaze) {
			trunk = lerpColor(trunk, 0x1A1410, 0.7f);
		}
		for (int i = 0; i < trees.count; i++) {
			double[] spot = treeSpot(land, trees, i);
			Vec3 foot = new Vec3(spot[0], spot[1] - 0.3, spot[2]);
			double h = spot[3];
			if (nearFade(foot.add(0, h * 0.5, 0), 2, 3) < 0.5f) {
				continue;
			}
			int leaf = Ambiance.color(trees.leaves.get(i % trees.leaves.size()), 0x3F7A2A);
			if (ablaze) {
				leaf = lerpColor(leaf, hashFloat(i * 83 + 9) < 0.5 ? 0x2A1A10 : 0x6A2A0A, 0.75f);
			}
			// Leaning in over the field when it is a canopy, so the crowns close overhead.
			Vec3 lean = trees.canopy ? new Vec3(-spot[0], 0, -spot[2]).normalize().scale(h * 0.28) : Vec3.ZERO;
			double sway = 0.06 * Math.sin(time * 0.03 + i);
			Vec3 top = foot.add(lean.x * 0.6 + sway, h * (trees.kind.equals("pine") ? 0.9 : 0.62), lean.z * 0.6);
			double width = 0.12 + h * 0.025;
			switch (trees.kind) {
				case "pine": {
					lathe(out, m, foot, top, new double[] {width * 1.2, width, width * 0.5}, new double[] {0, 0.3, 1}, 6, trunk, 0.2f, i);
					for (int s = 0; s < 4; s++) {
						Vec3 b = foot.add(0, h * (0.22 + 0.19 * s), 0);
						double rr = h * (0.3 - 0.06 * s);
						lathe(out, m, b, b.add(0, h * 0.34, 0), new double[] {0, rr, rr * 0.75, 0}, new double[] {0, 0.05, 0.25, 1}, 9, leaf,
								0.18f, i + s);
					}
					break;
				}
				case "dead": {
					lathe(out, m, foot, top, new double[] {width * 1.3, width, width * 0.4}, new double[] {0, 0.25, 1}, 6, trunk, 0.25f, i);
					for (int b = 0; b < 5; b++) {
						double ba = hashFloat(i * 83 + b * 5 + 20) * 6.28;
						Vec3 from = foot.add(0, h * (0.3 + 0.12 * b), 0).add(lean.scale(0.3 + 0.1 * b));
						Vec3 to = from.add(Math.cos(ba) * h * 0.25, h * (0.12 + 0.1 * hashFloat(i * 83 + b * 5 + 21)), Math.sin(ba) * h * 0.25);
						lathe(out, m, from, to, new double[] {width * 0.45, width * 0.1}, new double[] {0, 1}, 5, trunk, 0.2f, b);
					}
					break;
				}
				default: {
					lathe(out, m, foot, top, new double[] {width * 1.5, width, width * 0.8}, new double[] {0, 0.2, 1}, 7, trunk, 0.22f, i);
					double crownR = h * (trees.canopy ? 0.36 : 0.28);
					crown(out, m, top.add(0, crownR * 0.4, 0), crownR, 0.75, leaf, i);
					Vec3 side = new Vec3(Math.cos(i * 2.1), 0, Math.sin(i * 2.1)).scale(crownR * 0.55);
					crown(out, m, top.add(side).add(0, crownR * 0.1, 0), crownR * 0.72, 0.8, lerpColor(leaf, 0x000000, 0.08f), i + 7);
					crown(out, m, top.subtract(side).add(lean.scale(0.4)).add(0, crownR * 0.25, 0), crownR * 0.64, 0.8,
							lerpColor(leaf, 0xFFFFFF, 0.06f), i + 13);
					break;
				}
			}
		}
	}

	/** A billboard flame: wide and hot at its root, narrowing to a fading tip, flickering. */
	private static void flame(VertexConsumer out, Matrix4f m, Vec3 at, double width, double height, int color, int core, int alpha) {
		Vec3 toCamera = cameraRel.subtract(at);
		Vec3 right = new Vec3(toCamera.z, 0, -toCamera.x);
		right = right.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : right.normalize().scale(width * 0.5);
		Vec3 tip = at.add(0, height, 0);
		vertex(out, m, at.subtract(right), color, alpha / 2);
		vertex(out, m, at.add(right), color, alpha / 2);
		vertex(out, m, tip.add(right.scale(0.2)), core, 0);
		vertex(out, m, tip.subtract(right.scale(0.2)), core, 0);
		Vec3 mid = at.add(0, height * 0.35, 0);
		vertex(out, m, at.subtract(right.scale(0.5)), core, alpha);
		vertex(out, m, at.add(right.scale(0.5)), core, alpha);
		vertex(out, m, mid.add(right.scale(0.15)), color, alpha / 3);
		vertex(out, m, mid.subtract(right.scale(0.15)), color, alpha / 3);
	}

	/** The place ablaze: flames in the trees' crowns and up their trunks, and licking the ground round the field. */
	private static void drawBlaze(VertexConsumer out, Matrix4f m, Ambiance.Land land, Ambiance.Blaze blaze, float time) {
		int color = Ambiance.color(blaze.color, 0xFF6A10);
		int core = Ambiance.color(blaze.core, 0xFFE060);
		if (land.trees != null) {
			for (int i = 0; i < land.trees.count; i++) {
				double[] spot = treeSpot(land, land.trees, i);
				double h = spot[3];
				for (int f = 0; f < 4; f++) {
					double flicker = 0.75 + 0.25 * Math.sin(time * 0.5 + i * 3 + f * 1.7) * Math.sin(time * 0.23 + f);
					double fa = hashFloat(i * 97 + f) * 6.28;
					double fr = h * 0.22 * hashFloat(i * 97 + f + 9);
					Vec3 at = new Vec3(spot[0] + Math.cos(fa) * fr, spot[1] + h * (0.15 + 0.2 * f), spot[2] + Math.sin(fa) * fr);
					if (nearFade(at, 2, 3) < 0.5f) {
						continue;
					}
					flame(out, m, at, h * 0.3, h * 0.45 * flicker, color, core, (int) (170 * flicker));
				}
			}
		}
		for (int i = 0; i < blaze.flames; i++) {
			double a = Math.PI * 2 * hashFloat(i * 101 + 1);
			double r = fieldRadius + 1.5 + 12 * hashFloat(i * 101 + 2);
			double x = Math.cos(a) * r;
			double z = Math.sin(a) * r;
			Vec3 at = new Vec3(x, floorY(x, z) - 0.05, z);
			if (nearFade(at, 1.5, 2) < 0.5f) {
				continue;
			}
			double flicker = 0.7 + 0.3 * Math.sin(time * 0.6 + i * 2.3) * Math.sin(time * 0.37 + i);
			flame(out, m, at, 0.6 + 0.6 * hashFloat(i * 101 + 3), (0.8 + 1.4 * hashFloat(i * 101 + 4)) * flicker, color, core,
					(int) (190 * flicker));
		}
	}

	/** Pools: sunk discs, darker in the middle, swirling; light pass: their glow and bubbles rising and bursting. */
	private static void drawPools(VertexConsumer out, Matrix4f m, Ambiance.Pools pools, float time, boolean glow) {
		Ambiance.Water look = active.water;
		int color = Ambiance.color(look != null && look.color != null ? look.color : pools.color, 0x3A4A2A);
		int rim = Ambiance.color(pools.rim, 0x2A2A1A);
		int light = look != null && look.light != null ? Ambiance.color(look.light, -1) : (pools.glow != null ? Ambiance.color(pools.glow, -1) : -1);
		int sectors = 24;
		for (int i = 0; i < pools.count; i++) {
			double a = Math.PI * 2 * (i + 0.5 * hashFloat(i * 107 + 1)) / Math.max(1, pools.count);
			double r = fieldRadius + within(pools.ring, hashFloat(i * 107 + 2));
			double cx = Math.cos(a) * r;
			double cz = Math.sin(a) * r;
			double size = within(pools.size, hashFloat(i * 107 + 3));
			double y = floorY(cx, cz) + 0.02;
			Vec3 centre = new Vec3(cx, y, cz);
			if (glow) {
				if (light >= 0) {
					glowDisc(out, m, centre.add(0, 0.03, 0), new Vec3(0, 1, 0), (float) size, light, 40);
				}
				for (int b = 0; b < 4; b++) {
					double phase = (time * 0.012 + hashFloat(i * 107 + b * 3 + 10)) % 1;
					double ba = hashFloat(i * 107 + b * 3 + 11) * 6.28 + Math.floor(time * 0.012 + hashFloat(i * 107 + b * 3 + 10));
					double br = size * 0.6 * hashFloat(i * 107 + b * 3 + 12);
					Vec3 at = centre.add(Math.cos(ba) * br, 0.05 + phase * 0.25, Math.sin(ba) * br);
					float s = (float) (0.06 + 0.16 * phase);
					quadFacing(out, m, at, cameraRel.subtract(at).normalize(), s, light >= 0 ? light : lerpColor(color, 0xFFFFFF, 0.5f),
							(int) (120 * (1 - phase)));
				}
				continue;
			}
			Vec3 prevRing[] = null;
			int[] prevColor = null;
			for (int j = 0; j <= 4; j++) {
				double f = j / 4.0;
				Vec3[] ring = new Vec3[sectors];
				int[] ringColor = new int[sectors];
				for (int k = 0; k < sectors; k++) {
					double ang = Math.PI * 2 * k / sectors;
					double edge = size * (0.75 + 0.5 * valueNoise(Math.cos(ang) * 1.5 + i * 3, 1, Math.sin(ang) * 1.5));
					double px = cx + Math.cos(ang) * edge * f;
					double pz = cz + Math.sin(ang) * edge * f;
					Vec3 p = new Vec3(px, j == 4 ? floorY(px, pz) + 0.01 : y, pz);
					ring[k] = p;
					double swirl = valueNoise(px * 0.6 + time * 0.01, time * 0.008, pz * 0.6);
					int c = lerpColor(lerpColor(color, 0x000000, 0.35f), color, (float) f);
					c = lerpColor(c, lerpColor(color, 0xFFFFFF, 0.3f), 0.5f * (float) swirl);
					ringColor[k] = j == 4 ? rim : c;
				}
				if (prevRing != null) {
					for (int k = 0; k < sectors; k++) {
						int n = (k + 1) % sectors;
						quadColors(out, m, prevRing[k], prevRing[n], ring[n], ring[k], prevColor[k], prevColor[n], ringColor[n], ringColor[k]);
					}
				}
				prevRing = ring;
				prevColor = ringColor;
			}
		}
	}

	/** Fissures: dark lines wandering over the ground (solid), and the glow welling up from them (light, pulsing). */
	private static void drawCracks(VertexConsumer out, Matrix4f m, Ambiance.Cracks cracks, float time, boolean glow) {
		int color = Ambiance.color(cracks.color, 0xFF7A20);
		int core = Ambiance.color(cracks.core, 0xFFE080);
		for (int i = 0; i < cracks.count; i++) {
			double a = Math.PI * 2 * hashFloat(i * 113 + 1);
			double r = fieldRadius + within(cracks.ring, hashFloat(i * 113 + 2));
			double x = Math.cos(a) * r;
			double z = Math.sin(a) * r;
			double heading = a + (hashFloat(i * 113 + 3) - 0.5) * 2.5;
			int segments = 5 + (int) (6 * hashFloat(i * 113 + 4));
			float pulse = 0.65f + 0.35f * Mth.sin(time * 0.05f + i * 1.3f);
			for (int s = 0; s < segments; s++) {
				double length = 1 + 1.4 * hashFloat(i * 113 + s * 5 + 7);
				heading += (hashFloat(i * 113 + s * 5 + 8) - 0.5) * 1.2;
				double nx = x + Math.cos(heading) * length;
				double nz = z + Math.sin(heading) * length;
				double w = (glow ? 0.32 : 0.11) * (1 - 0.6 * s / (double) segments);
				double px = -Math.sin(heading) * w;
				double pz = Math.cos(heading) * w;
				double y0 = floorY(x, z) + (glow ? 0.05 : 0.03);
				double y1 = floorY(nx, nz) + (glow ? 0.05 : 0.03);
				Vec3 a0 = new Vec3(x + px, y0, z + pz);
				Vec3 b0 = new Vec3(nx + px, y1, nz + pz);
				Vec3 b1 = new Vec3(nx - px, y1, nz - pz);
				Vec3 a1 = new Vec3(x - px, y0, z - pz);
				if (glow) {
					int alpha = (int) (150 * pulse);
					vertex(out, m, a0, color, 0);
					vertex(out, m, b0, color, 0);
					vertex(out, m, new Vec3(nx, y1, nz), core, alpha);
					vertex(out, m, new Vec3(x, y0, z), core, alpha);
					vertex(out, m, new Vec3(x, y0, z), core, alpha);
					vertex(out, m, new Vec3(nx, y1, nz), core, alpha);
					vertex(out, m, b1, color, 0);
					vertex(out, m, a1, color, 0);
				} else {
					quad(out, m, a0, b0, b1, a1, lerpColor(color, 0x000000, 0.55f));
				}
				x = nx;
				z = nz;
			}
		}
	}

	/** A temple's columns round the field, fluted shafts on plinths, lintels joining them; some broken if ruined. */
	private static void drawColumns(VertexConsumer out, Matrix4f m, Ambiance.Columns columns) {
		int color = Ambiance.color(columns.color, 0xECE6D8);
		double r = fieldRadius + columns.distance;
		double base = groundAt(0, 0) + 0.06;
		Vec3[] tops = new Vec3[columns.count];
		for (int i = 0; i < columns.count; i++) {
			double a = Math.PI * 2 * i / columns.count;
			double x = Math.cos(a) * r;
			double z = Math.sin(a) * r;
			boolean broken = columns.ruined && i % 3 == 1;
			double h = columns.height * (broken ? 0.35 + 0.3 * hashFloat(i * 127 + 1) : 1);
			Vec3 foot = new Vec3(x, floorY(x, z) - 0.1, z);
			tops[i] = broken ? null : foot.add(0, h, 0);
			if (nearFade(foot.add(0, h * 0.5, 0), 1.5, 2) < 0.5f) {
				continue;
			}
			double w = 0.55;
			lathe(out, m, foot, foot.add(0, h, 0),
					broken ? new double[] {w * 1.5, w * 1.5, w * 1.15, w, w, w * 0.95}
							: new double[] {w * 1.5, w * 1.5, w * 1.15, w, w * 0.92, w * 1.1, w * 1.45, w * 1.45},
					broken ? new double[] {0, 0.05, 0.08, 0.14, 0.9, 1} : new double[] {0, 0.04, 0.07, 0.11, 0.9, 0.94, 0.97, 1},
					16, color, 0.08f, 0);
		}
		for (int i = 0; i < columns.count; i++) {
			Vec3 a = tops[i];
			Vec3 b = tops[(i + 1) % columns.count];
			if (a != null && b != null && nearFade(a.add(b).scale(0.5), 1.5, 2) >= 0.5f) {
				beamBox(out, m, a.add(0, 0.25, 0), b.add(0, 0.25, 0), 0.7, 0.5, color);
			}
		}
	}

	/** A box from {@code a} to {@code b}, {@code w} wide and {@code h} high, lit per vertex (lintels, beams, frames). */
	private static void beamBox(VertexConsumer out, Matrix4f m, Vec3 a, Vec3 b, double w, double h, int color) {
		Vec3 axis = b.subtract(a).normalize();
		Vec3 side = axis.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 up = side.cross(axis).normalize();
		Vec3 sw = side.scale(w / 2);
		Vec3 uh = up.scale(h / 2);
		Vec3[] ca = {a.subtract(sw).subtract(uh), a.add(sw).subtract(uh), a.add(sw).add(uh), a.subtract(sw).add(uh)};
		Vec3[] cb = {b.subtract(sw).subtract(uh), b.add(sw).subtract(uh), b.add(sw).add(uh), b.subtract(sw).add(uh)};
		Vec3[] normals = {up.scale(-1), side, up, side.scale(-1)};
		for (int k = 0; k < 4; k++) {
			int n = (k + 1) % 4;
			Vec3 normal = normals[k];
			quadColors(out, m, ca[k], ca[n], cb[n], cb[k], sunlit(stoneGrain(color, ca[k], 0.08f), normal, 0.5f),
					sunlit(stoneGrain(color, ca[n], 0.08f), normal, 0.5f), sunlit(stoneGrain(color, cb[n], 0.08f), normal, 0.5f),
					sunlit(stoneGrain(color, cb[k], 0.08f), normal, 0.5f));
		}
		int endA = sunlit(color, axis.scale(-1), 0.5f);
		int endB = sunlit(color, axis, 0.5f);
		quadColors(out, m, ca[0], ca[1], ca[2], ca[3], endA, endA, endA, endA);
		quadColors(out, m, cb[0], cb[1], cb[2], cb[3], endB, endB, endB, endB);
	}

	// ---- Halls

	private static double hallHalf(Ambiance.Hall hall) {
		return fieldRadius + hall.margin;
	}

	/** A hall's floor: the real ground under the field, level beyond it. */
	private static double hallY(double x, double z) {
		if (active != null && active.hall != null && active.hall.flat) {
			return groundAt(0, 0) + 0.09;
		}
		return snapToBlock(BattleAmbiance::smoothHallY, x, z);
	}

	private static double smoothHallY(double x, double z) {
		double r = Math.sqrt(x * x + z * z);
		double from = fieldRadius + 0.5;
		if (r <= from) {
			return groundAt(x, z) + 0.09;
		}
		double rim = groundAt(x * from / r, z * from / r) + 0.09;
		return Mth.lerp(smooth(Mth.clamp((r - from) / 2, 0, 1)), rim, groundAt(0, 0) + 0.09);
	}

	/** How lit the hall is at {@code p}: brightest under the middle, dimmer to the walls; dim red light in a power cut. */
	private static float hallLight(Ambiance.Hall hall, Vec3 p) {
		double s = hallHalf(hall);
		float lit = (float) (0.55 + 0.45 * Mth.clamp(1 - Math.sqrt(p.x * p.x + p.z * p.z) / (s * 1.5), 0, 1));
		return active.powerCut != null ? lit * 0.45f : lit;
	}

	/**
	 * A hall. Solid pass: the floor (plain or a checkerboard), the walls (or the tent's striped round wall and cone),
	 * pilasters, the ceiling's beams and lamps, the machines, mirrors, chess pieces. A wall the camera is outside of is
	 * left out. Light pass: the lamps' glow and beams, warning lights, emergency lights and sparks in a power cut.
	 */
	private static void drawHall(PoseStack pose, MultiBufferSource buffers, Ambiance.Hall hall, float time, boolean glow) {
		if (!glow && !reflecting) {
			// Built of blocks (2026-11-19), mirrors included (glass, the room built again mirrored behind it). Asked
			// before any buffer.
			hallInBlocks = com.mystaria.showtime.ambiance.blocks.BlockScenery.drawHall(pose, buffers, center, cameraWorld, frustum,
					hall, active.powerCut != null, fieldRadius, BattleAmbiance::groundAt);
		}
		VertexConsumer out = ambianceBuffer(buffers, glow ? com.mystaria.showtime.ShowtimeRenderTypes.GLOW : com.mystaria.showtime.ShowtimeRenderTypes.OPAQUE);
		Matrix4f m = pose.last().pose();
		double s = hallHalf(hall);
		double base = groundAt(0, 0) + 0.09;
		double top = base + hall.height;
		boolean tent = "tent".equals(hall.shape);
		Ambiance.PowerCut cut = active.powerCut;
		int lamp = Ambiance.color(hall.lamp, 0xFFF0C8);
		if (glow) {
			if (!tent) {
				for (int ix = -1; ix <= 1; ix += 2) {
					for (int iz = -1; iz <= 1; iz += 2) {
						for (int q = 0; q < 2; q++) {
							double lx = ix * s * (0.25 + 0.4 * q);
							double lz = iz * s * (0.25 + 0.4 * (1 - q));
							Vec3 at = new Vec3(lx, top - 1.6, lz);
							if (cut != null) {
								continue;
							}
							glowDisc(out, m, at, new Vec3(0, -1, 0), 1.1f, lamp, 120);
							beam(out, m, at, new Vec3(lx * 1.05, base, lz * 1.05), 0.8f, 4.5f, lamp, 14);
						}
					}
				}
			} else {
				glowDisc(out, m, new Vec3(0, top + hall.height * 0.5, 0), new Vec3(0, -1, 0), 2.5f, lamp, 90);
			}
			if (cut != null) {
				int red = Ambiance.color(cut.color, 0xFF2A1A);
				float blink = Mth.sin(time * 0.15f) > 0 ? 1f : 0.25f;
				for (int w = 0; w < 4; w++) {
					double a = Math.PI / 2 * w;
					Vec3 at = new Vec3(Math.cos(a) * (s - 0.3), base + hall.height * 0.7, Math.sin(a) * (s - 0.3));
					glowDisc(out, m, at, new Vec3(-Math.cos(a), 0, -Math.sin(a)), 0.9f, red, (int) (200 * blink));
					glowDisc(out, m, at, new Vec3(-Math.cos(a), 0, -Math.sin(a)), 3.5f, red, (int) (45 * blink));
				}
				for (int c = 0; c < cut.cables; c++) {
					Vec3 end = cableEnd(hall, c, time);
					if (hashFloat((int) (time / 3) * 31 + c * 7) > 0.72f) {
						quadFacing(out, m, end, cameraRel.subtract(end).normalize(), 0.35f, 0xD8F0FF, 230);
						glowDisc(out, m, end, cameraRel.subtract(end).normalize(), 1.2f, 0x6AA8FF, 70);
					}
				}
			} else if (hall.machines != null) {
				int warn = Ambiance.color(hall.machines.warning, 0xFFB020);
				for (int p = 0; p < hall.machines.presses; p++) {
					Vec3 at = pressSpot(hall, p).add(0, 5.6, 0);
					double turn = time * 0.2 + p;
					Vec3 facing = new Vec3(Math.cos(turn), 0, Math.sin(turn));
					glowDisc(out, m, at.add(facing.scale(0.3)), facing, 0.5f, warn, 200);
					beam(out, m, at, at.add(facing.scale(8)), 0.3f, 2.5f, warn, 18);
				}
			}
			if (hall.mirrors && !hallInBlocks && mirrorsLive(hall)) {
				// The glass: a faint cold sheen over the reflection, brighter towards the top.
				int glass = lerpColor(Ambiance.color(hall.wall, 0x9AA8C0), 0xFFFFFF, 0.3f);
				for (int w = 0; w < 4; w++) {
					double nx = w == 0 ? -1 : w == 1 ? 1 : 0;
					double nz = w == 2 ? -1 : w == 3 ? 1 : 0;
					double d = s - 0.02;
					Vec3 a = nx != 0 ? new Vec3(-nx * d, base - 0.1, -s) : new Vec3(-s, base - 0.1, -nz * d);
					Vec3 b = nx != 0 ? new Vec3(-nx * d, base - 0.1, s) : new Vec3(s, base - 0.1, -nz * d);
					quad4(out, m, a, b, b.add(0, hall.height + 0.1, 0), a.add(0, hall.height + 0.1, 0), glass, 10, 28);
				}
			}
			if (hall.mirrors) {
				for (int i = 0; i < 30; i++) {
					float f = Mth.sin(time * 0.12f + i * 2.7f);
					if (f < 0.7f) {
						continue;
					}
					int w = i % 4;
					double along = (hashFloat(i * 131 + 1) * 2 - 1) * s * 0.9;
					double y = base + 1 + (hall.height - 2) * hashFloat(i * 131 + 2);
					Vec3 at = w == 0 ? new Vec3(s - 0.1, y, along) : w == 1 ? new Vec3(-s + 0.1, y, along)
							: w == 2 ? new Vec3(along, y, s - 0.1) : new Vec3(along, y, -s + 0.1);
					quadFacing(out, m, at, cameraRel.subtract(at).normalize(), 0.25f, 0xFFFFFF, (int) ((f - 0.7f) / 0.3f * 230));
				}
			}
			return;
		}
		// The floor: a vertex every block (every tile corner for a checkerboard), lit by the room's light.
		int floor = Ambiance.color(hall.floor, 0x4A4C50);
		int checker = hall.checker != null ? Ambiance.color(hall.checker, 0x202020) : -1;
		double step = checker >= 0 ? hall.tile : 1;
		int cells = (int) Math.ceil(s / step);
		double extent = tent ? s + 0.5 : s;
		if (hallInBlocks) {
			cells = 0;
		} else if (checker < 0 && !hall.flat) {
			drawBlockFloor(out, m, "hall", extent * 1.42, BattleAmbiance::hallY,
					(x, z) -> floorColor(hall, floor, new Vec3(x, hallY(x, z) - 0.03, z), 0.14f), null,
					(x, z, r) -> tent ? r <= extent : Math.abs(x) < extent && Math.abs(z) < extent);
			cells = 0;
		}
		for (int ix = -cells; ix < cells; ix++) {
			for (int iz = -cells; iz < cells; iz++) {
				double x0 = Math.max(-extent, ix * step);
				double z0 = Math.max(-extent, iz * step);
				double x1 = Math.min(extent, (ix + 1) * step);
				double z1 = Math.min(extent, (iz + 1) * step);
				if (tent && Math.min(x0 * x0, x1 * x1) + Math.min(z0 * z0, z1 * z1) > extent * extent) {
					continue;
				}
				int tile = checker >= 0 && ((ix + iz) & 1) == 1 ? checker : floor;
				Vec3 p00 = new Vec3(x0, hallY(x0, z0) - 0.03, z0);
				Vec3 p10 = new Vec3(x1, hallY(x1, z0) - 0.03, z0);
				Vec3 p11 = new Vec3(x1, hallY(x1, z1) - 0.03, z1);
				Vec3 p01 = new Vec3(x0, hallY(x0, z1) - 0.03, z1);
				float grain = checker >= 0 ? 0.05f : 0.14f;
				quadColors(out, m, p00, p10, p11, p01, floorColor(hall, tile, p00, grain), floorColor(hall, tile, p10, grain),
						floorColor(hall, tile, p11, grain), floorColor(hall, tile, p01, grain));
			}
		}
		if (!hallInBlocks) {
			if (tent) {
				drawTent(out, m, hall, s, base);
			} else {
				drawRoom(out, m, hall, s, base, top);
			}
		}
		if (hall.machines != null) {
			drawMachines(out, m, hall, hall.machines, s, base, cut != null ? 0 : time);
		}
		if (hall.pieces != null) {
			drawPieces(out, m, hall, hall.pieces, s);
		}
		if (cut != null) {
			for (int c = 0; c < cut.cables; c++) {
				Vec3 end = cableEnd(hall, c, time);
				Vec3 from = new Vec3(end.x * 0.95, top - 0.3, end.z * 0.95);
				lathe(out, m, from, end, new double[] {0.06, 0.06}, new double[] {0, 1}, 4, 0x18181C, 0f, 0);
			}
		}
	}

	private static int floorColor(Ambiance.Hall hall, int tile, Vec3 p, float grain) {
		return lerpColor(0x000000, stoneGrain(tile, p, grain), hallLight(hall, p));
	}

	/** Where a cut cable hangs (swinging a little), round the field under the ceiling. */
	private static Vec3 cableEnd(Ambiance.Hall hall, int c, float time) {
		double a = Math.PI * 2 * (c + 0.5 * hashFloat(c * 137 + 1)) / Math.max(1, active.powerCut.cables);
		double r = fieldRadius + 2 + (hall.margin - 4) * hashFloat(c * 137 + 2);
		double swing = 0.25 * Math.sin(time * 0.04 + c);
		double y = groundAt(0, 0) + 2.5 + 3 * hashFloat(c * 137 + 3);
		return new Vec3(Math.cos(a) * r + swing, y, Math.sin(a) * r);
	}

	/**
 * Whether the hall's mirrors really reflect (2026-11-19): with the camera inside the room (outside it, the cutaway
 * opens a wall and the reflections would show through), and, for the smooth hall, no shader pack (Iris draws entities
 * its own way). The hall in blocks reflects under shaders too: its mirrored room is blocks, its people drawn without
 * flipping the triangles (drawBlockReflections).
 */
private static boolean mirrorsLive(Ambiance.Hall hall) {
double s = hallHalf(hall);
double base = groundAt(0, 0) + 0.09;
return Math.abs(cameraRel.x) < s - 0.5 && Math.abs(cameraRel.z) < s - 0.5 && cameraRel.y > base - 0.5
		&& cameraRel.y < base + hall.height - 0.5 && (hallInBlocks || !shadersOn());
}

/**
 * The buffer for one of Showtime's colour-only render types; under a shader pack, its entity-format sibling instead
 * (2026-11-19: packs drew the lightning shader those use as a bolt, so glows were dull and solid shapes flat and dark),
 * the shapes written through {@link com.mystaria.showtime.ShaderPackQuads}. Glows and tints at full light; solid shapes
 * in daylight, no block light, for the pack to light them as the world.
 */
private static VertexConsumer ambianceBuffer(MultiBufferSource buffers, RenderType type) {
if (!shadersOn()) {
	return buffers.getBuffer(type);
}
if (type == com.mystaria.showtime.ShowtimeRenderTypes.GLOW) {
	return new com.mystaria.showtime.ShaderPackQuads(buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.GLOW_SHADERS), 0xF000F0);
}
if (type == com.mystaria.showtime.ShowtimeRenderTypes.TINT) {
	return new com.mystaria.showtime.ShaderPackQuads(buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.TINT_SHADERS), 0xF000F0);
}
if (type == com.mystaria.showtime.ShowtimeRenderTypes.OPAQUE) {
	return new com.mystaria.showtime.ShaderPackQuads(buffers.getBuffer(com.mystaria.showtime.ShowtimeRenderTypes.OPAQUE_SHADERS), 0xF00000);
}
return buffers.getBuffer(type);
}

private static boolean shadersOn() {
if (!irisChecked) {
	irisChecked = true;
	try {
		Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
		iris = api.getMethod("getInstance").invoke(null);
		irisInUse = api.getMethod("isShaderPackInUse");
	} catch (ReflectiveOperationException | LinkageError ex) {
		irisInUse = null; // no Iris: no shader pack
	}
}
try {
	return irisInUse != null && (boolean) irisInUse.invoke(iris);
} catch (ReflectiveOperationException ex) {
	return false;
}
}

/**
 * Real mirrors (2026-11-19): for each wall, the room and everyone in it drawn again mirrored across the wall's plane,
 * so the copy lies behind the wall, seen through its open surface. The room is closed, so the copy only shows where
 * the mirror is. A mirror flips the triangles' winding: front faces are clockwise while it draws. One bounce: the
 * mirrors inside a reflection keep their painted sheen.
 */
private static void drawReflections(PoseStack pose, MultiBufferSource.BufferSource buffers, Ambiance.Hall hall, float time,
	float partialTick) {
Minecraft mc = Minecraft.getInstance();
double s = hallHalf(hall);
List<net.minecraft.world.entity.Entity> inside = new ArrayList<>();
for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
	Vec3 at = entity.getPosition(partialTick).subtract(center);
	if (Math.abs(at.x) < s - 0.3 && Math.abs(at.z) < s - 0.3 && Math.abs(at.y) < hall.height && inside.size() < 24) {
		inside.add(entity);
	}
}
net.minecraft.client.renderer.entity.EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
reflecting = true;
org.lwjgl.opengl.GL11.glFrontFace(org.lwjgl.opengl.GL11.GL_CW);
try {
	for (int w = 0; w < 4; w++) {
		double nx = w == 0 ? -1 : w == 1 ? 1 : 0;
		double nz = w == 2 ? -1 : w == 3 ? 1 : 0;
		skipWall = w;
		pose.pushPose();
		if (nx != 0) {
			pose.translate(-nx * 2 * s, 0, 0);
			pose.scale(-1, 1, 1);
		} else {
			pose.translate(0, 0, -nz * 2 * s);
			pose.scale(1, 1, -1);
		}
		drawHall(pose, buffers, hall, time, false);
		buffers.endBatch();
		for (net.minecraft.world.entity.Entity entity : inside) {
			Vec3 at = entity.getPosition(partialTick).subtract(center);
			try {
				dispatcher.render(entity, at.x, at.y, at.z, entity.getViewYRot(partialTick), partialTick, pose, buffers,
						dispatcher.getPackedLightCoords(entity, partialTick));
			} catch (RuntimeException ex) {
				// A renderer that cannot be drawn twice: that one stays out of the mirror.
			}
		}
		buffers.endBatch();
		pose.popPose();
	}
} finally {
	org.lwjgl.opengl.GL11.glFrontFace(org.lwjgl.opengl.GL11.GL_CCW);
	reflecting = false;
	skipWall = -1;
}
}

/**
 * The people in a hall built of blocks, drawn mirrored behind its glass (2026-11-19): the room itself is already built
 * mirrored there. Eight copies: one behind each mirror, one behind each corner (a reflection of a reflection, a plain
 * half turn). Without a shader pack a copy behind one mirror is a true mirror image (flipped, its triangles wound the
 * other way while it draws); with one, shader packs batch entities and draw them later, after the winding is back, so
 * it is placed and turned as its image would be instead: only its left and right stay unswapped.
 */
private static void drawBlockReflections(PoseStack pose, MultiBufferSource.BufferSource buffers, Ambiance.Hall hall,
	float partialTick) {
Minecraft mc = Minecraft.getInstance();
double s = hallHalf(hall);
double[] planes = com.mystaria.showtime.ambiance.blocks.BlockScenery.hallMirrorPlanes(center, s);
double px0 = planes[0] - center.x;
double px1 = planes[1] - center.x;
double pz0 = planes[2] - center.z;
double pz1 = planes[3] - center.z;
List<net.minecraft.world.entity.Entity> inside = new ArrayList<>();
for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
	Vec3 at = entity.getPosition(partialTick).subtract(center);
	if (at.x > px0 && at.x < px1 && at.z > pz0 && at.z < pz1 && Math.abs(at.y) < hall.height && inside.size() < 24) {
		inside.add(entity);
	}
}
if (inside.isEmpty()) {
	return;
}
boolean shaders = shadersOn();
net.minecraft.client.renderer.entity.EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
for (int fx = -1; fx <= 1; fx++) {
	for (int fz = -1; fz <= 1; fz++) {
		if (fx == 0 && fz == 0) {
			continue;
		}
		double planeX = fx < 0 ? px0 : px1;
		double planeZ = fz < 0 ? pz0 : pz1;
		boolean flipped = (fx != 0) != (fz != 0);
		boolean trueImage = flipped && !shaders;
		if (trueImage) {
			org.lwjgl.opengl.GL11.glFrontFace(org.lwjgl.opengl.GL11.GL_CW);
		}
		try {
			for (net.minecraft.world.entity.Entity entity : inside) {
				Vec3 at = entity.getPosition(partialTick).subtract(center);
				double mx = fx != 0 ? 2 * planeX - at.x : at.x;
				double mz = fz != 0 ? 2 * planeZ - at.z : at.z;
				pose.pushPose();
				float yaw = 0;
				if (trueImage) {
					// The world mirrored across the plane; the entity drawn where it really is.
					pose.translate(fx != 0 ? 2 * planeX : 0, 0, fz != 0 ? 2 * planeZ : 0);
					pose.scale(fx != 0 ? -1 : 1, 1, fz != 0 ? -1 : 1);
					mx = at.x;
					mz = at.z;
				} else {
					// Its image's place, turned as its image faces: across x, a yaw of -φ; across z, 180 - φ; behind a
					// corner, φ + 180. Renderers turn by 180 - φ, so the extra turn is φ less the image's yaw.
					float body = entity instanceof net.minecraft.world.entity.LivingEntity living
							? Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot)
							: entity.getViewYRot(partialTick);
					yaw = !flipped ? 180 : fx != 0 ? 2 * body : 2 * body - 180;
				}
				pose.translate(mx, at.y, mz);
				if (yaw != 0) {
					pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
				}
				try {
					dispatcher.render(entity, 0, 0, 0, entity.getViewYRot(partialTick), partialTick, pose, buffers,
							dispatcher.getPackedLightCoords(entity, partialTick));
				} catch (RuntimeException ex) {
					// A renderer that cannot be drawn twice: that one stays out of the mirror.
				}
				pose.popPose();
			}
			buffers.endBatch();
		} finally {
			if (trueImage) {
				org.lwjgl.opengl.GL11.glFrontFace(org.lwjgl.opengl.GL11.GL_CCW);
			}
		}
	}
}
}

/** Four walls with pilasters, a ceiling with beams and hanging lamps; mirrors on the walls when the hall has them. */
	private static void drawRoom(VertexConsumer out, Matrix4f m, Ambiance.Hall hall, double s, double base, double top) {
		int wall = Ambiance.color(hall.wall, 0x3A3E46);
		int ceiling = Ambiance.color(hall.ceiling, 0x2A2C32);
		int lamp = Ambiance.color(hall.lamp, 0xFFF0C8);
		int steps = (int) Math.ceil(2 * s / 2);
		int rows = (int) Math.ceil(hall.height / 2);
		for (int w = 0; w < 4; w++) {
			// Each wall: its inward normal, and whether the camera is inside it (else it is left out: a cutaway).
			double nx = w == 0 ? -1 : w == 1 ? 1 : 0;
			double nz = w == 2 ? -1 : w == 3 ? 1 : 0;
			Vec3 normal = new Vec3(nx, 0, nz);
			if (w == skipWall || cameraRel.x * -nx + cameraRel.z * -nz > s - 0.5) {
				continue;
			}
			// Real mirrors: the surface is left open, the reflection drawn behind it shows through (drawReflections).
			boolean open = hall.mirrors && !reflecting && mirrorsLive(hall);
			Vec3[][] grid = new Vec3[rows + 1][steps + 1];
			int[][] colors = new int[rows + 1][steps + 1];
			for (int j = 0; j <= rows; j++) {
				for (int k = 0; k <= steps; k++) {
					double along = -s + 2 * s * k / steps;
					double y = base - 0.1 + (hall.height + 0.1) * j / rows;
					Vec3 p = w < 2 ? new Vec3(-nx * s, y, along) : new Vec3(along, y, -nz * s);
					grid[j][k] = p;
					int c;
					if (hall.mirrors) {
						Vec3 view = p.subtract(cameraRel).normalize();
						Vec3 reflected = view.subtract(normal.scale(2 * view.dot(normal)));
						double sheen = 0.5 + 0.5 * Math.sin((reflected.x + reflected.y * 1.3 + reflected.z) * 6);
						c = lerpColor(lerpColor(wall, 0x000000, 0.4f), lerpColor(wall, lamp, 0.6f),
								(float) (0.35 + 0.35 * (reflected.y * 0.5 + 0.5) + 0.3 * sheen * sheen));
						// Dark glass, not plaster (2026-11-19: painted light, the mirrors seen inside a reflection
						// filled it and the real mirrors read as plain walls); darker still inside a reflection.
						c = lerpColor(c, 0x0C0E14, reflecting ? 0.72f : 0.5f);
					} else {
						c = stoneGrain(wall, p, 0.12f);
						c = lerpColor(c, 0x000000, 0.35f * (float) (1 - j / (double) rows) * 0.6f);
					}
					colors[j][k] = lerpColor(0x000000, c, hallLight(hall, new Vec3(p.x * 0.6, p.y, p.z * 0.6)) + 0.1f);
				}
			}
			for (int j = 0; j < rows && !open; j++) {
				for (int k = 0; k < steps; k++) {
					quadColors(out, m, grid[j][k], grid[j][k + 1], grid[j + 1][k + 1], grid[j + 1][k], colors[j][k], colors[j][k + 1],
							colors[j + 1][k + 1], colors[j + 1][k]);
				}
			}
			// Pilasters (or the mirrors' frames) every six blocks.
			int frame = hall.mirrors ? lerpColor(wall, 0xFFD87A, 0.5f) : lerpColor(wall, 0x000000, 0.25f);
			for (double along = -s + 6; along < s - 1; along += 6) {
				Vec3 foot = w < 2 ? new Vec3(-nx * (s - 0.25), base - 0.1, along) : new Vec3(along, base - 0.1, -nz * (s - 0.25));
				if (nearFade(foot.add(0, hall.height * 0.5, 0), 1.5, 2) < 0.5f) {
					continue;
				}
				beamBox(out, m, foot, foot.add(0, hall.height, 0), hall.mirrors ? 0.25 : 0.8, hall.mirrors ? 0.25 : 0.6, frame);
			}
		}
		if (cameraRel.y < top - 0.5) {
			int cells = (int) Math.ceil(s / 2);
			for (int ix = -cells; ix < cells; ix++) {
				for (int iz = -cells; iz < cells; iz++) {
					double x0 = Math.max(-s, ix * 2.0);
					double z0 = Math.max(-s, iz * 2.0);
					double x1 = Math.min(s, ix * 2.0 + 2);
					double z1 = Math.min(s, iz * 2.0 + 2);
					Vec3 p00 = new Vec3(x0, top, z0);
					Vec3 p10 = new Vec3(x1, top, z0);
					Vec3 p11 = new Vec3(x1, top, z1);
					Vec3 p01 = new Vec3(x0, top, z1);
					quadColors(out, m, p00, p10, p11, p01, ceilingColor(hall, ceiling, p00), ceilingColor(hall, ceiling, p10),
							ceilingColor(hall, ceiling, p11), ceilingColor(hall, ceiling, p01));
				}
			}
			int beam = lerpColor(ceiling, 0x000000, 0.3f);
			for (double x = -s + 4; x < s; x += 6) {
				beamBox(out, m, new Vec3(x, top - 0.5, -s), new Vec3(x, top - 0.5, s), 0.6, 1.0, beam);
			}
			for (int ix = -1; ix <= 1; ix += 2) {
				for (int iz = -1; iz <= 1; iz += 2) {
					for (int q = 0; q < 2; q++) {
						double lx = ix * s * (0.25 + 0.4 * q);
						double lz = iz * s * (0.25 + 0.4 * (1 - q));
						lathe(out, m, new Vec3(lx, top, lz), new Vec3(lx, top - 1.6, lz), new double[] {0.04, 0.04, 0.3, 0.9, 0.9},
								new double[] {0, 0.6, 0.65, 0.98, 1}, 10, lerpColor(beam, 0x000000, 0.2f), 0.05f, 0);
					}
				}
			}
		}
	}

	private static int ceilingColor(Ambiance.Hall hall, int ceiling, Vec3 p) {
		return lerpColor(0x000000, stoneGrain(ceiling, p, 0.1f), hallLight(hall, p) * 0.8f);
	}

	/** A circus tent: a round wall in vertical stripes, a striped cone up to the mast, the mast in the middle. */
	private static void drawTent(VertexConsumer out, Matrix4f m, Ambiance.Hall hall, double s, double base) {
		List<String> stripes = hall.stripes != null && !hall.stripes.isEmpty() ? hall.stripes : List.of("#d02a2a", "#f4f0e0");
		int sectors = 64;
		double wallTop = base + hall.height * 0.55;
		double peak = base + hall.height * 1.5;
		int roofRings = 6;
		for (int k = 0; k < sectors; k++) {
			double a0 = Math.PI * 2 * k / sectors;
			double a1 = Math.PI * 2 * (k + 1) / sectors;
			int stripe = Ambiance.color(stripes.get((k / 2) % stripes.size()), 0xD02A2A);
			Vec3 mid = new Vec3(Math.cos((a0 + a1) / 2) * s, wallTop * 0.5 + base * 0.5, Math.sin((a0 + a1) / 2) * s);
			Vec3 inward = new Vec3(-mid.x, 0, -mid.z).normalize();
			boolean wallShown = cameraRel.subtract(mid).dot(inward) >= 0;
			double[] ys = {base - 0.1, (base + wallTop) / 2, wallTop};
			for (int j = 0; j < 2 && wallShown; j++) {
				Vec3 a = new Vec3(Math.cos(a0) * s, ys[j], Math.sin(a0) * s);
				Vec3 b = new Vec3(Math.cos(a1) * s, ys[j], Math.sin(a1) * s);
				Vec3 c = new Vec3(Math.cos(a1) * s, ys[j + 1], Math.sin(a1) * s);
				Vec3 d = new Vec3(Math.cos(a0) * s, ys[j + 1], Math.sin(a0) * s);
				quadColors(out, m, a, b, c, d, tentColor(hall, stripe, a), tentColor(hall, stripe, b), tentColor(hall, stripe, c),
						tentColor(hall, stripe, d));
			}
			if (cameraRel.y > peak) {
				continue;
			}
			for (int j = 0; j < roofRings; j++) {
				double f0 = j / (double) roofRings;
				double f1 = (j + 1) / (double) roofRings;
				double r0 = s * (1 - f0);
				double r1 = s * (1 - f1);
				double y0 = Mth.lerp(f0, wallTop, peak) - 0.6 * Math.sin(Math.PI * f0);
				double y1 = Mth.lerp(f1, wallTop, peak) - 0.6 * Math.sin(Math.PI * f1);
				Vec3 a = new Vec3(Math.cos(a0) * r0, y0, Math.sin(a0) * r0);
				Vec3 b = new Vec3(Math.cos(a1) * r0, y0, Math.sin(a1) * r0);
				Vec3 c = new Vec3(Math.cos(a1) * r1, y1, Math.sin(a1) * r1);
				Vec3 d = new Vec3(Math.cos(a0) * r1, y1, Math.sin(a0) * r1);
				quadColors(out, m, a, b, c, d, tentColor(hall, stripe, a), tentColor(hall, stripe, b), tentColor(hall, stripe, c),
						tentColor(hall, stripe, d));
			}
		}
		lathe(out, m, new Vec3(fieldRadius + 2, base - 0.1, 0), new Vec3(fieldRadius + 2, peak, 0), new double[] {0.35, 0.25},
				new double[] {0, 1}, 8, 0xC8A060, 0.15f, 0);
		lathe(out, m, new Vec3(-fieldRadius - 2, base - 0.1, 0), new Vec3(-fieldRadius - 2, peak, 0), new double[] {0.35, 0.25},
				new double[] {0, 1}, 8, 0xC8A060, 0.15f, 0);
	}

	private static int tentColor(Ambiance.Hall hall, int stripe, Vec3 p) {
		float light = (float) (0.5 + 0.5 * Mth.clamp((p.y - groundAt(0, 0)) / (hall.height * 1.5), 0, 1));
		return lerpColor(0x000000, stoneGrain(stripe, p, 0.06f), light * hallLight(hall, new Vec3(p.x * 0.5, p.y, p.z * 0.5)) + 0.15f);
	}

	/** Where press {@code p} stands: in the hall's corners and along its sides, clear of the belt. */
	private static Vec3 pressSpot(Ambiance.Hall hall, int p) {
		double s = hallHalf(hall);
		double a = Math.PI / 4 + Math.PI * 2 * p / Math.max(1, hall.machines.presses);
		double r = (s - 1.8) * 1.2;
		double x = Mth.clamp(Math.cos(a) * r, -s + 1.8, s - 1.8);
		double z = Mth.clamp(Math.sin(a) * r, -s + 1.8, s - 1.8);
		return new Vec3(x, groundAt(0, 0) + 0.09, z);
	}

	/** The plant: a conveyor loop carrying crates round the field, gears turning on the walls, presses stamping. */
	private static void drawMachines(VertexConsumer out, Matrix4f m, Ambiance.Hall hall, Ambiance.Machines machines, double s, double base,
			float time) {
		int metal = Ambiance.color(machines.metal, 0x6A7078);
		int belt = Ambiance.color(machines.belt, 0x24262A);
		int warning = Ambiance.color(machines.warning, 0xFFB020);
		double d = Math.min(s - 4, fieldRadius + 3.5);
		double perimeter = 8 * d;
		double run = time * 0.05 * machines.speed;
		double beltY = base + 0.8;
		// The belt: short segments round a square loop, ribs running along it (a colour per vertex).
		double seg = 0.25;
		for (double t = 0; t < perimeter; t += seg) {
			Vec3 a = loopPoint(d, t, beltY);
			Vec3 b = loopPoint(d, Math.min(perimeter, t + seg), beltY);
			Vec3 dir = b.subtract(a).normalize();
			Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(0.7);
			if (nearFade(a, 1.2, 1.5) < 0.5f) {
				continue;
			}
			int ca = lerpColor(0x000000, lerpColor(belt, 0x6A6A70, ribs(t - run)), hallLight(hall, a));
			int cb = lerpColor(0x000000, lerpColor(belt, 0x6A6A70, ribs(t + seg - run)), hallLight(hall, b));
			quadColors(out, m, a.subtract(side), b.subtract(side), b.add(side), a.add(side), ca, cb, cb, ca);
			int frame = lerpColor(0x000000, metal, hallLight(hall, a) * 0.8f);
			Vec3 drop = new Vec3(0, -0.8, 0);
			quadColors(out, m, a.add(side), b.add(side), b.add(side).add(drop), a.add(side).add(drop), frame, frame,
					lerpColor(frame, 0x000000, 0.5f), lerpColor(frame, 0x000000, 0.5f));
			quadColors(out, m, a.subtract(side), b.subtract(side), b.subtract(side).add(drop), a.subtract(side).add(drop), frame,
					frame, lerpColor(frame, 0x000000, 0.5f), lerpColor(frame, 0x000000, 0.5f));
		}
		for (double t = 0; t < perimeter; t += 3) {
			Vec3 c = loopPoint(d, ((t + run) % perimeter + perimeter) % perimeter, beltY + 0.35);
			if (nearFade(c, 1.2, 1.5) < 0.5f) {
				continue;
			}
			int crate = Ambiance.color(machines.crates.get((int) (t / 3) % machines.crates.size()), 0xA07A4A);
			beamBox(out, m, c.add(-0.35, 0, 0), c.add(0.35, 0, 0), 0.7, 0.7, lerpColor(0x000000, crate, hallLight(hall, c)));
		}
		// Gears on the walls, turning (meshing pairs turn opposite ways).
		for (int g = 0; g < machines.gears; g++) {
			int w = g % 4;
			double along = (hashFloat(g * 149 + 1) * 1.4 - 0.7) * s;
			double y = base + 4 + 5 * hashFloat(g * 149 + 2);
			double radius = 1.2 + 1.3 * hashFloat(g * 149 + 3);
			Vec3 normal = w == 0 ? new Vec3(-1, 0, 0) : w == 1 ? new Vec3(1, 0, 0) : w == 2 ? new Vec3(0, 0, -1) : new Vec3(0, 0, 1);
			Vec3 c = normal.scale(-(s - 0.3)).add(w < 2 ? new Vec3(0, y, along) : new Vec3(along, y, 0));
			if (cameraRel.dot(normal.scale(-1)) > s - 0.5) {
				continue;
			}
			gear(out, m, c, normal, radius, (g % 2 == 0 ? 1 : -1) * time * 0.03 * machines.speed / radius, metal, hall);
		}
		// Presses: a frame, an anvil, a ram falling fast and rising slowly.
		for (int p = 0; p < machines.presses; p++) {
			Vec3 at = pressSpot(hall, p);
			if (nearFade(at.add(0, 2.5, 0), 1.5, 2) < 0.5f) {
				continue;
			}
			int frame = lerpColor(0x000000, metal, hallLight(hall, at));
			beamBox(out, m, at.add(-1.1, 0, 0), at.add(-1.1, 5.4, 0), 0.4, 0.4, frame);
			beamBox(out, m, at.add(1.1, 0, 0), at.add(1.1, 5.4, 0), 0.4, 0.4, frame);
			beamBox(out, m, at.add(-1.3, 5.4, 0), at.add(1.3, 5.4, 0), 0.6, 0.6, frame);
			beamBox(out, m, at.add(-0.8, 0.4, 0), at.add(0.8, 0.4, 0), 1.2, 0.8, lerpColor(frame, 0x000000, 0.3f));
			double phase = ((time * 0.02 * machines.speed + p * 0.37) % 1 + 1) % 1;
			double down = phase < 0.1 ? phase / 0.1 : 1 - (phase - 0.1) / 0.9;
			double ramY = 4.6 - 3.4 * down;
			beamBox(out, m, at.add(-0.7, ramY, 0), at.add(0.7, ramY, 0), 1.0, 1.0, lerpColor(frame, warning, 0.35f));
		}
	}

	/** The belt's ribs: a smooth stripe every half block. */
	private static float ribs(double t) {
		double v = 0.5 + 0.5 * Math.sin(t * Math.PI * 4);
		return (float) (0.3 * v * v);
	}

	/** A point {@code t} along a square loop of half-size {@code d}. */
	private static Vec3 loopPoint(double d, double t, double y) {
		double side = 2 * d;
		int edge = (int) (t / side) % 4;
		double u = t - Math.floor(t / side) * side - d;
		return switch (edge) {
			case 0 -> new Vec3(u, y, -d);
			case 1 -> new Vec3(d, y, u);
			case 2 -> new Vec3(-u, y, d);
			default -> new Vec3(-d, y, -u);
		};
	}

	/** A gear against a wall: a hub, spokes of shade, teeth round its rim, turned by {@code angle}. */
	private static void gear(VertexConsumer out, Matrix4f m, Vec3 c, Vec3 normal, double radius, double angle, int metal,
			Ambiance.Hall hall) {
		Vec3 u = normal.cross(new Vec3(0, 1, 0)).normalize();
		Vec3 v = new Vec3(0, 1, 0);
		int teeth = 10 + (int) (radius * 4);
		int points = teeth * 4;
		float light = hallLight(hall, new Vec3(c.x * 0.6, c.y, c.z * 0.6));
		int rim = lerpColor(0x000000, metal, light);
		int hub = lerpColor(0x000000, lerpColor(metal, 0xFFFFFF, 0.2f), light);
		for (int k = 0; k < points; k++) {
			double a0 = angle + Math.PI * 2 * k / points;
			double a1 = angle + Math.PI * 2 * (k + 1) / points;
			double r0 = (k % 4 < 2) ? radius * 1.15 : radius;
			Vec3 o0 = c.add(u.scale(Math.cos(a0) * r0)).add(v.scale(Math.sin(a0) * r0));
			Vec3 o1 = c.add(u.scale(Math.cos(a1) * r0)).add(v.scale(Math.sin(a1) * r0));
			Vec3 i0 = c.add(u.scale(Math.cos(a0) * radius * 0.3)).add(v.scale(Math.sin(a0) * radius * 0.3)).add(normal.scale(0.12));
			Vec3 i1 = c.add(u.scale(Math.cos(a1) * radius * 0.3)).add(v.scale(Math.sin(a1) * radius * 0.3)).add(normal.scale(0.12));
			float spoke = (float) (0.5 + 0.5 * Math.cos((a0 - angle) * 5));
			int mid = lerpColor(rim, 0x000000, 0.35f * (1 - spoke));
			quadColors(out, m, i0, i1, o1, o0, hub, hub, k % 4 < 2 ? rim : mid, k % 4 < 2 ? rim : mid);
		}
	}

	/** Giant chess pieces on the board round the field: pawns, rooks, knights, bishops, queens, kings; white and black. */
	private static void drawPieces(VertexConsumer out, Matrix4f m, Ambiance.Hall hall, Ambiance.Pieces pieces, double s) {
		int white = Ambiance.color(pieces.white, 0xECE6DA);
		int black = Ambiance.color(pieces.black, 0x2A2630);
		String order = "RNBQKBNRPPPPPPPP";
		for (int i = 0; i < pieces.count; i++) {
			double a = Math.PI * 2 * (i + 0.5) / pieces.count;
			double r = fieldRadius + 3 + (s - fieldRadius - 5) * hashFloat(i * 151 + 1);
			double x = (Math.floor(Math.cos(a) * r / hall.tile) + 0.5) * hall.tile;
			double z = (Math.floor(Math.sin(a) * r / hall.tile) + 0.5) * hall.tile;
			Vec3 foot = new Vec3(x, hallY(x, z) - 0.05, z);
			char kind = order.charAt(i % order.length());
			double h = switch (kind) {
				case 'P' -> 2.6;
				case 'R' -> 3.2;
				case 'N' -> 3.4;
				case 'B' -> 3.8;
				case 'Q' -> 4.4;
				default -> 4.8;
			};
			h *= 0.5 + hall.tile / 4; // the pieces grow with the squares they stand on
			if (nearFade(foot.add(0, h * 0.5, 0), 1.5, 2) < 0.5f) {
				continue;
			}
			int color = lerpColor(0x000000, x < 0 ? white : black, hallLight(hall, foot) + 0.2f);
			double w = h;
			Vec3 tip = foot.add(0, h, 0);
			double[] baseR = {0.3, 0.3, 0.24, 0.2};
			double[] baseF = {0, 0.06, 0.1, 0.15};
			double[] r2;
			double[] f2;
			switch (kind) {
				case 'P' -> {
					r2 = new double[] {0.13, 0.09, 0.17, 0.1, 0.16, 0.17, 0.12, 0};
					f2 = new double[] {0.28, 0.5, 0.55, 0.6, 0.7, 0.8, 0.92, 1};
				}
				case 'R' -> {
					r2 = new double[] {0.16, 0.16, 0.23, 0.23, 0.18, 0};
					f2 = new double[] {0.3, 0.75, 0.8, 1, 1, 1};
				}
				case 'B' -> {
					r2 = new double[] {0.12, 0.09, 0.17, 0.1, 0.15, 0.12, 0.05, 0.04, 0};
					f2 = new double[] {0.3, 0.55, 0.6, 0.64, 0.75, 0.86, 0.95, 0.97, 1};
				}
				case 'Q' -> {
					r2 = new double[] {0.13, 0.09, 0.17, 0.11, 0.2, 0.15, 0.07, 0};
					f2 = new double[] {0.3, 0.55, 0.6, 0.64, 0.86, 0.9, 0.96, 1};
				}
				case 'N' -> {
					r2 = new double[] {0.14, 0.15, 0};
					f2 = new double[] {0.3, 0.4, 0.42};
				}
				default -> {
					r2 = new double[] {0.13, 0.09, 0.17, 0.11, 0.19, 0.15, 0};
					f2 = new double[] {0.3, 0.55, 0.6, 0.64, 0.86, 0.9, 0.9};
				}
			}
			double[] rr = new double[baseR.length + r2.length];
			double[] ff = new double[baseF.length + f2.length];
			for (int k = 0; k < baseR.length; k++) {
				rr[k] = baseR[k] * w;
				ff[k] = baseF[k];
			}
			for (int k = 0; k < r2.length; k++) {
				rr[baseR.length + k] = r2[k] * w;
				ff[baseF.length + k] = f2[k];
			}
			lathe(out, m, foot, tip, rr, ff, 16, color, 0.04f, 0);
			Vec3 inward = new Vec3(-x, 0, -z).normalize();
			if (kind == 'N') {
				Vec3 neck = foot.add(0, h * 0.42, 0);
				Vec3 head = neck.add(inward.scale(h * 0.12)).add(0, h * 0.42, 0);
				beamBox(out, m, neck, head, h * 0.22, h * 0.26, color);
				beamBox(out, m, head.add(0, -h * 0.05, 0), head.add(inward.scale(h * 0.28)).add(0, -h * 0.12, 0), h * 0.18, h * 0.16,
						color);
			} else if (kind == 'K') {
				Vec3 crossBase = foot.add(0, h * 0.9, 0);
				beamBox(out, m, crossBase, crossBase.add(0, h * 0.2, 0), h * 0.06, h * 0.06, color);
				Vec3 side = new Vec3(-inward.z, 0, inward.x).scale(h * 0.08);
				beamBox(out, m, crossBase.add(0, h * 0.13, 0).subtract(side), crossBase.add(0, h * 0.13, 0).add(side), h * 0.06, h * 0.06,
						color);
			}
		}
	}

	/** Grass blades over the place's ground, a wave of wind running across them. */
	private static void drawGrass(PoseStack pose, MultiBufferSource buffers, Ambiance.Grass grass, float time) {
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.OPAQUE);
		Matrix4f m = pose.last().pose();
		double wx = 1;
		double wz = 0.3;
		if (active.wind != null && active.wind.size() >= 2 && (active.wind.get(0) != 0 || active.wind.get(1) != 0)) {
			wx = active.wind.get(0);
			wz = active.wind.get(1);
		}
		double wl = Math.sqrt(wx * wx + wz * wz);
		wx /= wl;
		wz /= wl;
		int tip = Ambiance.color(grass.tip, 0xB8E070);
		for (int i = 0; i < grass.count; i++) {
			double a = Math.PI * 2 * hashFloat(i * 157 + 1);
			double r = within(grass.ring, Math.sqrt(hashFloat(i * 157 + 2)));
			double x = Math.cos(a) * r;
			double z = Math.sin(a) * r;
			Vec3 at = new Vec3(x, floorY(x, z) - 0.06, z);
			if (hole(x, z) || nearFade(at, 0.8, 1) < 0.5f) {
				continue;
			}
			double h = within(grass.height, hashFloat(i * 157 + 3));
			int color = Ambiance.color(grass.colors.get(i % grass.colors.size()), 0x4A8A2A);
			double yaw = hashFloat(i * 157 + 4) * Math.PI;
			double half = 0.045 + 0.03 * hashFloat(i * 157 + 5);
			double sway = grass.wind * h * (0.12 + 0.2 * Math.sin(time * 0.09 - (x * wx + z * wz) * 0.35) + 0.05 * Math.sin(time * 0.31 + i));
			Vec3 side = new Vec3(Math.cos(yaw) * half, 0, Math.sin(yaw) * half);
			Vec3 top = at.add(wx * sway, h, wz * sway);
			int root = lerpColor(color, 0x000000, 0.45f);
			int head = lerpColor(color, tip, 0.55f);
			quadColors(out, m, at.subtract(side), at.add(side), top, top, root, root, head, head);
		}
	}

	/** Flowers: a stem, then five petals round a golden heart, each petal pale at its root and coloured at its tip. */
	private static void drawFlowers(PoseStack pose, MultiBufferSource buffers, Ambiance.Flowers flowers, float time) {
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.OPAQUE);
		Matrix4f m = pose.last().pose();
		for (int i = 0; i < flowers.count; i++) {
			double a = Math.PI * 2 * hashFloat(i * 163 + 1);
			double r = within(flowers.ring, Math.sqrt(hashFloat(i * 163 + 2)));
			double x = Math.cos(a) * r;
			double z = Math.sin(a) * r;
			Vec3 at = new Vec3(x, floorY(x, z) - 0.05, z);
			if (hole(x, z) || nearFade(at, 0.8, 1) < 0.5f) {
				continue;
			}
			double h = within(flowers.height, hashFloat(i * 163 + 3));
			double sway = 0.05 * Math.sin(time * 0.08 + x * 0.3 + i);
			Vec3 head = at.add(sway, h, sway * 0.4);
			Vec3 stemSide = new Vec3(0.025, 0, 0.025);
			quadColors(out, m, at.subtract(stemSide), at.add(stemSide), head.add(stemSide), head.subtract(stemSide), 0x2A5A1A, 0x2A5A1A,
					0x4A8A2A, 0x4A8A2A);
			int petal = Ambiance.color(flowers.colors.get(i % flowers.colors.size()), 0xFF6A8A);
			int heart = 0xFFD23D;
			double size = 0.1 + 0.08 * hashFloat(i * 163 + 4);
			double spin = hashFloat(i * 163 + 5) * 6.28;
			for (int p = 0; p < 5; p++) {
				double pa = spin + Math.PI * 2 * p / 5;
				Vec3 tipP = head.add(Math.cos(pa) * size, 0.02, Math.sin(pa) * size);
				Vec3 left = head.add(Math.cos(pa - 0.5) * size * 0.55, 0.01, Math.sin(pa - 0.5) * size * 0.55);
				Vec3 right = head.add(Math.cos(pa + 0.5) * size * 0.55, 0.01, Math.sin(pa + 0.5) * size * 0.55);
				quadColors(out, m, head, left, tipP, right, heart, lerpColor(petal, 0xFFFFFF, 0.35f), petal, lerpColor(petal, 0xFFFFFF, 0.35f));
			}
		}
	}

	/** A land's or a hall's anchors: its floor round the field, the air; a hall's walls and ceiling too. */
	private static Anchors placeAnchors(Ambiance ambiance) {
		Anchors result = new Anchors();
		for (int i = 0; i < 160; i++) {
			double a = Math.PI * 2 * hashFloat(i * 19 + 1);
			double r = fieldRadius + 1 + 11 * hashFloat(i * 19 + 2);
			double x = Math.cos(a) * r;
			double z = Math.sin(a) * r;
			double y = ambiance.land != null ? landY(ambiance.land, x, z) : hallY(x, z);
			result.add(Anchors.Kind.FLOOR, new Anchors.Anchor(new Vec3(x, y - 0.12, z), new Vec3(0, 1, 0), 1f));
		}
		if (ambiance.hall != null && !"tent".equals(ambiance.hall.shape)) {
			double s = hallHalf(ambiance.hall);
			double top = groundAt(0, 0) + 0.09 + ambiance.hall.height;
			for (int i = 0; i < 160; i++) {
				double u = (hashFloat(i * 23 + 1) * 2 - 1) * (s - 1);
				double v = (hashFloat(i * 23 + 2) * 2 - 1) * (s - 1);
				result.add(Anchors.Kind.CEILING, new Anchors.Anchor(new Vec3(u, top - 0.1, v), new Vec3(0, -1, 0), 1f));
				int w = i % 4;
				double y = groundAt(0, 0) + 1 + (ambiance.hall.height - 2) * hashFloat(i * 23 + 3);
				Vec3 normal = w == 0 ? new Vec3(-1, 0, 0) : w == 1 ? new Vec3(1, 0, 0) : w == 2 ? new Vec3(0, 0, -1) : new Vec3(0, 0, 1);
				Vec3 p = normal.scale(-(s - 0.1)).add(w < 2 ? new Vec3(0, y, u) : new Vec3(u, y, 0));
				result.add(Anchors.Kind.WALL, new Anchors.Anchor(p, normal, 1f));
			}
		}
		result.fillFloat(fieldRadius, groundAt(0, 0));
		return result;
	}

	// ---------------------------------------------------------------- Props on anchors (2026-11-19)

	/** One prop placed: its kind, the anchor kind it took, where it grows from and which way, its size and colours. */
	private record Placed(Ambiance.Prop prop, Anchors.Kind on, Vec3 base, Vec3 dir, double length, float light, int index) {
	}

	/** The surfaces round the field (scanned or given by the stadium), and the props placed on them. */
	private static Anchors anchors;
	private static final List<Placed> PLACED = new ArrayList<>();

	/** At the start: the anchors (the stadium's vault, else the real blocks), then every prop on its anchors. */
	private static void placeProps(ClientLevel level, Ambiance ambiance) {
		PLACED.clear();
		anchors = null;
		boolean wantsAnchors = !ambiance.props.isEmpty() || ambiance.moments != null;
		if (!wantsAnchors || center == null) {
			return;
		}
		double ground = groundAt(0, 0);
		if (ambiance.cavern != null) {
			anchors = vaultAnchors(ambiance.cavern);
		} else if (ambiance.sea != null) {
			anchors = seaAnchors(ambiance.sea);
		} else if (ambiance.land != null || ambiance.hall != null) {
			anchors = placeAnchors(ambiance);
		} else if (ambiance.hideWorld) {
			// The world hidden by a stadium without surfaces of its own: only its ground (people stand on it) and the air.
			anchors = new Anchors();
			for (int i = 0; i < 160; i++) {
				double a = Math.PI * 2 * hashFloat(i * 19 + 1);
				double r = fieldRadius + 1 + 10 * hashFloat(i * 19 + 2);
				anchors.add(Anchors.Kind.FLOOR, new Anchors.Anchor(onGround(Math.cos(a) * r, Math.sin(a) * r).add(0, -0.12, 0),
						new Vec3(0, 1, 0), 1f));
			}
			anchors.fillFloat(fieldRadius, ground);
		} else {
			List<Vec3> avoid = new ArrayList<>();
			for (UUID id : trainers) {
				net.minecraft.world.entity.player.Player player = level.getPlayerByUUID(id);
				if (player != null) {
					avoid.add(player.position().subtract(center));
				}
			}
			int reach = ambiance.ceilingReach != null ? ambiance.ceilingReach : Anchors.DEFAULT_REACH;
			anchors = Anchors.scan(level, center, fieldRadius, ground, avoid, reach);
		}
		int index = 0;
		for (Ambiance.Prop prop : ambiance.props) {
			int left = prop.count;
			for (String name : prop.anchors) {
				Anchors.Kind kind = Anchors.kind(name);
				while (kind != null && left > 0) {
					Anchors.Anchor anchor = anchors.take(kind);
					if (anchor == null) {
						break;
					}
					left--;
					PLACED.add(place(prop, kind, anchor, index++));
				}
			}
		}
	}

	/** A stadium's vault as anchors: its roof (ceiling), its walls, its floor between the field (or the lava) and them. */
	private static Anchors vaultAnchors(Ambiance.Cavern cavern) {
		Anchors result = new Anchors();
		Vec3 heart = new Vec3(0, cavern.height * 0.3, 0);
		for (int i = 0; i < 420; i++) {
			double a = Math.PI * 2 * hashFloat(i * 29 + 1);
			double v = hashFloat(i * 29 + 2);
			Vec3 p = vaultPoint(cavern, a, v);
			p = new Vec3(p.x * 0.985, p.y, p.z * 0.985);
			if (v > 0.42) {
				result.add(Anchors.Kind.CEILING, new Anchors.Anchor(p.add(0, 0.25, 0), new Vec3(0, -1, 0), 1f));
			} else if (v > 0.06) {
				result.add(Anchors.Kind.WALL, new Anchors.Anchor(p, heart.subtract(p).normalize(), 1f));
			}
		}
		double from = cavern.lava == null ? fieldRadius + 1.2 : fieldRadius + cavern.lava.gap + cavern.lava.width + 0.8;
		double to = cavernRadius(cavern) - 1.5;
		for (int i = 0; i < 200 && from < to; i++) {
			double a = Math.PI * 2 * hashFloat(i * 37 + 3);
			double r = Mth.lerp(hashFloat(i * 37 + 4), from, to);
			result.add(Anchors.Kind.FLOOR, new Anchors.Anchor(onGround(Math.cos(a) * r, Math.sin(a) * r).add(0, -0.12, 0),
					new Vec3(0, 1, 0), 1f));
		}
		result.fillFloat(fieldRadius, groundAt(0, 0));
		return result;
	}

	/** Where a prop grows from and which way: in from a wall and up, down from a ceiling, up from a floor. */
	private static Placed place(Ambiance.Prop prop, Anchors.Kind on, Anchors.Anchor anchor, int index) {
		double length = Mth.lerp(hashFloat(index * 41 + 1), prop.size.get(0), prop.size.get(prop.size.size() - 1));
		Vec3 dir = switch (on) {
			case CEILING -> new Vec3((hashFloat(index * 41 + 2) - 0.5) * 0.15, -1, (hashFloat(index * 41 + 3) - 0.5) * 0.15).normalize();
			case WALL -> anchor.normal().add(0, 0.6, 0).normalize();
			case FLOOR -> new Vec3((hashFloat(index * 41 + 2) - 0.5) * 0.4, 1, (hashFloat(index * 41 + 3) - 0.5) * 0.4).normalize();
			case FLOAT -> new Vec3(0, 1, 0);
		};
		if (on == Anchors.Kind.CEILING) {
			// A low ceiling: never more than a third of the way down to the floor.
			double gap = anchor.at().y - (groundAt(anchor.at().x, anchor.at().z) + 0.12);
			length = Math.min(length, Math.max(0.4, gap / 3));
		}
		return new Placed(prop, on, anchor.at(), dir, length, anchor.light(), index);
	}

	/** The ambiance's stalactites and stalagmites for a cave built of blocks: x, z, length, 1 hanging / 0 rising. */
	private static List<Float> stalks() {
		List<Float> out = new ArrayList<>();
		for (Placed placed : PLACED) {
			String kind = placed.prop().kind;
			if (("stalactite".equals(kind) || "stalagmite".equals(kind)) && placed.on() != Anchors.Kind.WALL) {
				out.add((float) placed.base().x);
				out.add((float) placed.base().z);
				out.add((float) placed.length());
				out.add(placed.on() == Anchors.Kind.CEILING ? 1f : 0f);
			}
		}
		return out;
	}

	/** A prop's rock colour: the stadium's light when there is one, else the world's light where it stands. */
	private static int propLit(Placed placed, int color, Vec3 p, Vec3 normal) {
		if (active.cavern != null) {
			return caveLit(active.cavern, color, p, normal);
		}
		return lerpColor(0x000000, shade(color, normal.y < -0.5 ? new Vec3(0, 1, 0) : normal, 0.6f), placed.light());
	}

	/** The props: rock and crystals (solid pass), the crystals' light, beams and dripping water (light pass); rocks falling. */
	private static void drawProps(PoseStack pose, MultiBufferSource buffers, float time, boolean glow) {
		VertexConsumer out = ambianceBuffer(buffers, glow ? com.mystaria.showtime.ShowtimeRenderTypes.GLOW : com.mystaria.showtime.ShowtimeRenderTypes.OPAQUE);
		Matrix4f m = pose.last().pose();
		Vec3 up = new Vec3(0, 1, 0);
		int beamsLeft = 0;
		Ambiance.Prop beamProp = null;
		for (Placed placed : PLACED) {
			Ambiance.Prop prop = placed.prop();
			Vec3 base = placed.base();
			if (nearFade(base, 1.0, 1.5) < 0.2f) {
				continue;
			}
			switch (prop.kind) {
				case "stalactite", "stalagmite" -> {
					Vec3 tip = base.add(placed.dir().scale(placed.length()));
					if (ticks < quakeUntil && placed.on() == Anchors.Kind.CEILING) {
						double shake = 0.12 * Math.min(1, (quakeUntil - ticks) / 20.0);
						tip = tip.add(Math.sin(time * 1.9 + placed.index()) * shake, 0, Math.cos(time * 2.3 + placed.index() * 0.7) * shake);
					}
					int rock = Ambiance.color(prop.color, 0x5A4E42);
					// In a cave built of blocks, they are built with it (stalks()).
					boolean inBlocks = caveInBlocks && placed.on() != Anchors.Kind.WALL;
					if (!glow && !inBlocks) {
						Vec3 toward = new Vec3(-base.x, 0, -base.z).normalize();
						float width = (float) (0.2 + 0.18 * placed.length());
						cone(out, m, base.subtract(placed.dir().scale(0.3)), tip, width, propLit(placed, rock, base, toward),
								propLit(placed, lerpColor(rock, 0xFFFFFF, 0.28f), tip, toward), placed.index());
					} else if (prop.drips > 0 && placed.on() == Anchors.Kind.CEILING && placed.index() % 3 == 0) {
						drawDrip(out, m, tip, Ambiance.color(prop.drip, 0xA8D8FF), prop.drips, placed.index(), time);
					}
				}
				case "crystal" -> {
					drawCrystalCluster(out, m, placed, time, glow, -1, 0);
					if (glow && prop.beams > 0 && (placed.on() == Anchors.Kind.CEILING || placed.on() == Anchors.Kind.FLOAT)) {
						if (beamProp != prop) {
							beamProp = prop;
							beamsLeft = prop.beams;
						}
						if (beamsLeft-- > 0) {
							drawPrismBeam(out, m, placed, time, -1);
						}
					}
				}
				default -> {
				}
			}
		}
		if (transition != null) {
			drawLeaving(out, m, time, glow);
		}
		if (!glow) {
			int rockColor = 0x5A4E42;
			for (Ambiance.Prop prop : active.props) {
				if (!"crystal".equals(prop.kind)) {
					rockColor = Ambiance.color(prop.color, rockColor);
					break;
				}
			}
			for (Rock r : ROCKS) {
				if (ticks < r.at()) {
					continue;
				}
				double t = Math.min(time - r.at(), r.landAt() - r.at());
				double y = Math.max(r.floor() + r.size() * 0.5, r.from().y - 0.5 * FALL_GRAVITY * t * t);
				Vec3 at = new Vec3(r.from().x, y, r.from().z);
				int c = active.cavern != null ? caveLit(active.cavern, rockColor, at, up) : shade(rockColor, up, 0.6f);
				boulder(out, m, at, r.size(), r.spin() + t * 0.15, c, (int) r.at());
			}
		}
	}

	/**
	 * The old ambiance's props leaving (a transition): each crystal trembles and flares white, then breaks apart and is
	 * gone; the old prism beams fade out.
	 */
	private static void drawLeaving(VertexConsumer out, Matrix4f m, float time, boolean glow) {
		Ambiance.Prop beamProp = null;
		int beamsLeft = 0;
		for (Placed placed : LEAVING) {
			Transitions.Leaving how = transition.leaving.get(placed.prop().kind);
			if (how == null || !"crystal".equals(placed.prop().kind) || nearFade(placed.base(), 1.0, 1.5) < 0.2f) {
				continue;
			}
			double u = time - leaveStart(placed, how);
			float flare = (float) Mth.clamp(u / Math.max(1, how.flare), 0, 1);
			double broken = (u - how.flare) / Math.max(1, how.breakTicks);
			if (broken >= 1) {
				continue;
			}
			float scale = broken <= 0 ? 1f : (float) (1 - broken * broken);
			drawCrystalCluster(out, m, placed, time, glow, scale, broken <= 0 ? flare : (float) (1 - broken));
			Ambiance.Prop prop = placed.prop();
			if (glow && prop.beams > 0 && (placed.on() == Anchors.Kind.CEILING || placed.on() == Anchors.Kind.FLOAT)) {
				if (beamProp != prop) {
					beamProp = prop;
					beamsLeft = prop.beams;
				}
				if (beamsLeft-- > 0 && time < 12) {
					drawPrismBeam(out, m, placed, time, 1 - time / 12f);
				}
			}
		}
	}

	/** A drop of water falling from a tip now and then, a ripple where it lands. */
	private static void drawDrip(VertexConsumer out, Matrix4f m, Vec3 tip, int color, float rate, int index, float time) {
		double floor = groundAt(tip.x, tip.z) + 0.12;
		double period = (60 + 120 * hashFloat(index * 13 + 9)) / rate;
		double t = (time + hashFloat(index * 13 + 10) * period) % period;
		double fall = Math.sqrt(2 * Math.max(0.2, tip.y - floor) / FALL_GRAVITY);
		if (t < fall) {
			Vec3 drop = tip.add(0, -0.5 * FALL_GRAVITY * t * t, 0);
			Vec3 facing = cameraRel.subtract(drop).normalize();
			quadFacing(out, m, drop, facing, 0.035f, color, 230);
			glowDisc(out, m, drop, facing, 0.18f, color, 50);
		} else if (t < fall + 16) {
			double p = (t - fall) / 16;
			ring(out, m, new Vec3(tip.x, floor + 0.02, tip.z), 0.1 + p * 0.7, 0.05, color, (int) (140 * (1 - p)));
		}
	}

	/** How bright a crystal prop shines now: breathing, or lit by the wave running round the field. */
	private static float crystalStrength(Placed placed, float time) {
		Ambiance.Prop prop = placed.prop();
		if (prop.wave) {
			double a = Math.atan2(placed.base().z, placed.base().x);
			return prop.glow * (float) (0.12 + 0.88 * Math.pow(Math.max(0, Math.cos(a - time / 70.0 * prop.pulse)), 4));
		}
		return prop.glow * (0.7f + 0.3f * Mth.sin(time / 20f * prop.pulse + placed.index() * 1.3f));
	}

	/**
	 * A crystal cluster on its anchor (a floating one turns slowly, crystals pointing every way), growing in.
	 * {@code size} 0..1 overrides the growing (a crystal of an ambiance leaving, breaking apart; negative: growing in);
	 * {@code flare} 0..1 makes it tremble and shine white.
	 */
	private static void drawCrystalCluster(VertexConsumer out, Matrix4f m, Placed placed, float time, boolean glow, float size, float flare) {
		Ambiance.Prop prop = placed.prop();
		if (prop.colors.isEmpty()) {
			return;
		}
		int c = placed.index();
		float g;
		if (size >= 0) {
			g = size;
		} else {
			g = Mth.clamp(time / Math.max(1, prop.grow), 0, 1);
			g = g * g * (3 - 2 * g);
		}
		boolean floating = placed.on() == Anchors.Kind.FLOAT;
		Vec3 base = floating ? placed.base().add(0, Math.sin(time / 30.0 + c) * 0.3, 0) : placed.base();
		if (flare > 0) {
			base = base.add(Math.sin(time * 3.1 + c) * 0.05 * flare, 0, Math.cos(time * 2.7 + c * 0.7) * 0.05 * flare);
		}
		double spin = floating ? time / 160.0 + c : 0;
		int color = Ambiance.color(prop.colors.get(c % prop.colors.size()), 0x8AD8FF);
		int accent = Ambiance.color(prop.colors.get((c + 1) % prop.colors.size()), color);
		float strength = crystalStrength(placed, time) * (1 + 2.5f * flare);
		int many = 3 + (int) (hashFloat(c * 71 + 4) * 4);
		double big = placed.length() * (floating ? 0.6 : 1);
		for (int k = 0; k < many; k++) {
			Vec3 jitter = new Vec3(hashFloat(c * 71 + 10 + k) - 0.5, hashFloat(c * 71 + 20 + k) - 0.5,
					hashFloat(c * 71 + 30 + k) - 0.5).scale(1.1);
			double length = big * (k == 0 ? 1 : 0.45 + 0.4 * hashFloat(c * 71 + 40 + k)) * g;
			if (length < 0.05) {
				continue;
			}
			Vec3 dir = floating ? rotateY(jitter.normalize(), spin) : placed.dir().add(jitter.scale(k == 0 ? 0.2 : 0.7)).normalize();
			Vec3 foot = floating ? base : base.add(jitter.scale(0.35));
			Vec3 tip = foot.add(dir.scale(length));
			float width = (float) (length * 0.16) * crystalWidth(c, k);
			int tint = k == 1 ? accent : color;
			if (flare > 0) {
				tint = lerpColor(tint, 0xFFFFFF, 0.6f * flare);
			}
			if (glow) {
				crystalGlint(out, m, foot, tip, width, tint, strength, c * 7 + k);
			} else {
				crystal(out, m, foot, tip, width, prop.wave ? lerpColor(tint, 0x000000, 0.35f * (1 - strength)) : tint, c * 7 + k);
			}
		}
		if (glow) {
			Vec3 normal = floating ? cameraRel.subtract(base).normalize() : placed.dir();
			glowDisc(out, m, base.add(placed.dir().scale(0.3)), normal, (float) big * 1.4f * g, color, Math.round(40 * strength));
		}
	}

	/**
	 * A prism beam from a crystal on a ceiling (or floating) onto the field, swaying and changing colour; {@code fade}
	 * 0..1: going out (a transition; negative: growing in with the crystal).
	 */
	private static void drawPrismBeam(VertexConsumer out, Matrix4f m, Placed placed, float time, float fade) {
		Ambiance.Prop prop = placed.prop();
		int n = prop.colors.size();
		int b = placed.index();
		double a = Math.atan2(placed.base().z, placed.base().x) + Math.PI + Math.sin(time / 110.0 + b) * 0.5;
		double r = fieldRadius * (0.2 + 0.55 * hashFloat(b * 37 + 2));
		Vec3 to = onGround(Math.cos(a) * r, Math.sin(a) * r);
		double phase = time / 90.0 + b * 0.7;
		int c0 = Ambiance.color(prop.colors.get((int) Math.floor(phase) % n), 0xFFFFFF);
		int c1 = Ambiance.color(prop.colors.get(((int) Math.floor(phase) + 1) % n), 0xFFFFFF);
		int color = lerpColor(c0, c1, (float) (phase - Math.floor(phase)));
		float g = fade >= 0 ? fade : Mth.clamp(time / Math.max(1, prop.grow), 0, 1);
		float strength = crystalStrength(placed, time) * g;
		beam(out, m, placed.base(), to, 0.25f, 1.6f, color, Math.round(34 * strength));
		floorDisc(out, m, to.x, to.z, 1.4, color, Math.round(55 * strength), 0.15);
	}

	/** A thin ring lying flat (a ripple). */
	private static void ring(VertexConsumer out, Matrix4f m, Vec3 at, double r, double width, int color, int alpha) {
		int segments = 20;
		for (int k = 0; k < segments; k++) {
			double a0 = Math.PI * 2 * k / segments;
			double a1 = Math.PI * 2 * (k + 1) / segments;
			vertex(out, m, at.add(Math.cos(a0) * r, 0, Math.sin(a0) * r), color, alpha);
			vertex(out, m, at.add(Math.cos(a1) * r, 0, Math.sin(a1) * r), color, alpha);
			vertex(out, m, at.add(Math.cos(a1) * (r + width), 0, Math.sin(a1) * (r + width)), color, 0);
			vertex(out, m, at.add(Math.cos(a0) * (r + width), 0, Math.sin(a0) * (r + width)), color, 0);
		}
	}

	/**
	 * The lava: a ring round the field, its surface flowing (bright veins winding through it, dark crust plates drifting),
	 * its banks; lavafalls pouring down the walls into it. Light pass: the heat glowing over it, the falls' glare and
	 * splash.
	 */
	private static void drawLavaLake(VertexConsumer out, Matrix4f m, Ambiance.Cavern cavern, Ambiance.LavaLake lava, double in,
			double outer, float time, boolean glow) {
		int hot = Ambiance.color(lava.color, 0xFF4A08);
		int core = Ambiance.color(lava.core, 0xFFD060);
		int crust = Ambiance.color(lava.crust, 0x2A0E06);
		int sectors = 96;
		int rings = Math.max(2, (int) Math.ceil((outer - in) / 1.2));
		for (int j = 0; j < rings; j++) {
			for (int k = 0; k < sectors; k++) {
				Vec3[] p = new Vec3[4];
				int[] colors = new int[4];
				double[][] corners = {{j, k}, {j, k + 1}, {j + 1, k + 1}, {j + 1, k}};
				for (int q = 0; q < 4; q++) {
					double r = Mth.lerp(corners[q][0] / rings, in, outer);
					double a = Math.PI * 2 * corners[q][1] / sectors;
					p[q] = new Vec3(Math.cos(a) * r, groundAt(Math.cos(a) * r, Math.sin(a) * r) - 0.35, Math.sin(a) * r);
					double flow = 0.5 + 0.5 * Math.sin(a * 9 + r * 1.7 - time / 14.0 + 2 * Math.sin(a * 3 + time / 50.0));
					int color = lerpColor(hot, core, (float) (flow * flow));
					double plate = Math.sin(a * 5 + time / 90.0) * Math.sin(r * 2.1 - time / 70.0 + a * 2);
					if (plate > 0.5) {
						color = lerpColor(color, crust, (float) Math.min(0.9, (plate - 0.5) * 4));
					}
					colors[q] = color;
				}
				if (glow) {
					for (int q = 0; q < 4; q++) {
						vertex(out, m, p[q].add(0, 0.06, 0), colors[q], 45);
					}
				} else {
					for (int q = 0; q < 4; q++) {
						vertex(out, m, p[q], colors[q], 255);
					}
				}
			}
		}
		if (!glow) {
			// Banks: the floor's edge dropping to the lava, dark and crusted.
			for (int k = 0; k < sectors; k++) {
				double a0 = Math.PI * 2 * k / sectors;
				double a1 = Math.PI * 2 * (k + 1) / sectors;
				for (double r : new double[] {in, outer}) {
					Vec3 t0 = onGround(Math.cos(a0) * r, Math.sin(a0) * r);
					Vec3 t1 = onGround(Math.cos(a1) * r, Math.sin(a1) * r);
					quad(out, m, t0, t1, t1.add(0, -0.5, 0), t0.add(0, -0.5, 0), lerpColor(crust, hot, 0.15f));
				}
			}
		}
		// Lavafalls down the walls.
		for (int i = 0; i < lava.falls; i++) {
			double a = Math.PI * 2 * (i + 0.5 * hashFloat(i * 43 + 1)) / Math.max(1, lava.falls) + 0.2;
			Vec3 side = new Vec3(-Math.sin(a), 0, Math.cos(a));
			int steps = 10;
			double width = 0.7 + 0.5 * hashFloat(i * 43 + 2);
			for (int s = 0; s < steps; s++) {
				double v0 = 0.42 * (1 - s / (double) steps);
				double v1 = 0.42 * (1 - (s + 1) / (double) steps);
				Vec3 p0 = vaultPoint(cavern, a, v0).multiply(0.97, 1, 0.97);
				Vec3 p1 = vaultPoint(cavern, a, v1).multiply(0.97, 1, 0.97);
				double f0 = 0.5 + 0.5 * Math.sin(v0 * 40 + time / 5.0 + i);
				double f1 = 0.5 + 0.5 * Math.sin(v1 * 40 + time / 5.0 + i);
				int c0 = lerpColor(hot, core, (float) f0);
				int c1 = lerpColor(hot, core, (float) f1);
				double w = glow ? width * 2.4 : width;
				int alpha = glow ? 70 : 255;
				vertex(out, m, p0.add(side.scale(-w)), c0, alpha);
				vertex(out, m, p0.add(side.scale(w)), c0, alpha);
				vertex(out, m, p1.add(side.scale(w)), c1, alpha);
				vertex(out, m, p1.add(side.scale(-w)), c1, alpha);
			}
			if (glow) {
				Vec3 foot = vaultPoint(cavern, a, 0).multiply(0.94, 0, 0.94);
				foot = new Vec3(foot.x, groundAt(foot.x, foot.z) - 0.2, foot.z);
				glowDisc(out, m, foot, new Vec3(0, 1, 0), 3f, core, Math.round(70 + 30 * Mth.sin(time / 6f + i)));
			}
		}
	}

	/**
	 * A dragon's hoard between the field and the lava: gold heaps with coins scattered round, glinting; a great skeleton
	 * half sunk in the lava (spine, ribs arching out, the skull with its horns), a dull ember glowing in its eyes.
	 */
	private static void drawHoard(VertexConsumer out, Matrix4f m, Ambiance.Cavern cavern, Ambiance.Hoard hoard, double lavaIn,
			double lavaOut, float time, boolean glow) {
		int gold = Ambiance.color(hoard.gold, 0xFFCF4A);
		double from = fieldRadius + 0.5;
		double to = lavaIn == Double.MAX_VALUE ? cavernRadius(cavern) - 2 : lavaIn - 0.4;
		for (int i = 0; i < hoard.piles; i++) {
			double a = Math.PI * 2 * (i + 0.6 * hashFloat(i * 61 + 1)) / Math.max(1, hoard.piles) + 1.1;
			double r = Mth.lerp(hashFloat(i * 61 + 2), from, Math.max(from, to));
			Vec3 foot = onGround(Math.cos(a) * r, Math.sin(a) * r).add(0, -0.1, 0);
			double h = 0.45 + 0.6 * hashFloat(i * 61 + 3);
			float width = (float) (0.7 + 0.8 * hashFloat(i * 61 + 4));
			if (nearFade(foot, 1.0, 1.5) < 0.3f) {
				continue;
			}
			if (glow) {
				for (int s = 0; s < 3; s++) {
					double blink = Math.pow(Math.max(0, Math.sin(time / 9.0 + hashFloat(i * 61 + 10 + s) * 40)), 10);
					if (blink < 0.05) {
						continue;
					}
					double sa = hashFloat(i * 61 + 20 + s) * Math.PI * 2;
					double sr = width * 0.6 * hashFloat(i * 61 + 30 + s);
					Vec3 spot = foot.add(Math.cos(sa) * sr, h * (1 - sr / width) + 0.05, Math.sin(sa) * sr);
					Vec3 facing = cameraRel.subtract(spot).normalize();
					quadFacing(out, m, spot, facing, 0.06f, 0xFFFFFF, (int) (230 * blink));
					glowDisc(out, m, spot, facing, 0.35f, gold, (int) (110 * blink));
				}
				continue;
			}
			cone(out, m, foot, foot.add(0, h, 0), width, caveLit(cavern, lerpColor(gold, 0x5A3A00, 0.4f), foot, new Vec3(0, 1, 0)),
					caveLit(cavern, lerpColor(gold, 0xFFFFFF, 0.2f), foot, new Vec3(0, 1, 0)), i + 900);
			for (int c = 0; c < 4; c++) {
				double ca = hashFloat(i * 61 + 40 + c) * Math.PI * 2;
				double cr = width * (1.1 + 0.6 * hashFloat(i * 61 + 50 + c));
				Vec3 coin = onGround(foot.x + Math.cos(ca) * cr, foot.z + Math.sin(ca) * cr).add(0, -0.06, 0);
				cube(out, m, coin, 0.07f, caveLit(cavern, gold, coin, new Vec3(0, 1, 0)), 255);
			}
		}
		if (!hoard.bones) {
			return;
		}
		// The skeleton lies along the ring (in the lava when there is some), its head toward the field's side.
		int bone = Ambiance.color(hoard.bone, 0xE4D8BC);
		double rs = lavaIn == Double.MAX_VALUE ? (fieldRadius + cavernRadius(cavern)) / 2 : (lavaIn + lavaOut) / 2;
		double start = 2.3;
		double span = 13 / rs;
		int vertebrae = 18;
		Vec3[] spine = new Vec3[vertebrae];
		for (int k = 0; k < vertebrae; k++) {
			double a = start + span * k / (vertebrae - 1);
			double r = rs + Math.sin(k * 0.5) * 0.6;
			double ground = lavaIn == Double.MAX_VALUE ? groundAt(Math.cos(a) * r, Math.sin(a) * r) + 0.1
					: groundAt(Math.cos(a) * r, Math.sin(a) * r) - 0.25;
			spine[k] = new Vec3(Math.cos(a) * r, ground + 0.15 * Math.sin(k * 0.8), Math.sin(a) * r);
		}
		Vec3 head = spine[0];
		Vec3 along = spine[0].subtract(spine[1]).normalize();
		Vec3 out2 = new Vec3(head.x, 0, head.z).normalize();
		if (glow) {
			// Embers in the empty eyes.
			float ember = 0.6f + 0.4f * Mth.sin(time / 13f);
			for (int side = -1; side <= 1; side += 2) {
				Vec3 eye = head.add(0, 0.85, 0).add(along.scale(0.35)).add(out2.scale(0.32 * side));
				glowDisc(out, m, eye, cameraRel.subtract(eye).normalize(), 0.3f, 0xFF5A10, Math.round(150 * ember));
			}
			return;
		}
		if (nearFade(head, 2, 3) < 0.3f) {
			return;
		}
		for (int k = 0; k + 1 < vertebrae; k++) {
			double w = 0.32 * (1 - 0.6 * k / vertebrae);
			tube(out, m, spine[k], spine[k + 1], w, w * 0.9, caveLit(cavern, bone, spine[k], new Vec3(0, 1, 0)));
			cube(out, m, spine[k].add(0, w * 0.9, 0), (float) (w * 0.55), caveLit(cavern, lerpColor(bone, 0xFFFFFF, 0.15f),
					spine[k], new Vec3(0, 1, 0)), 255);
		}
		// Ribs: pairs arching out and up from the chest, down into the floor or the lava.
		for (int k = 3; k < 11; k++) {
			Vec3 tangent = spine[k + 1].subtract(spine[k - 1]).normalize();
			Vec3 side = tangent.cross(new Vec3(0, 1, 0)).normalize();
			double reach = 1.6 + 1.2 * Math.sin(Math.PI * (k - 3) / 8.0);
			for (int s = -1; s <= 1; s += 2) {
				Vec3 prev = spine[k];
				for (int q = 1; q <= 7; q++) {
					double t = q / 7.0;
					Vec3 p = spine[k].add(side.scale(s * reach * t)).add(0, reach * 1.3 * Math.sin(Math.PI * t * 0.95), 0)
							.add(tangent.scale(-0.4 * t));
					tube(out, m, prev, p, 0.13 * (1 - 0.5 * t) + 0.04, 0.13 * (1 - 0.5 * (t + 1 / 7.0)) + 0.04,
							caveLit(cavern, bone, p, side.scale(-s)));
					prev = p;
				}
			}
		}
		// The skull: the cranium, a long snout, two horns sweeping back.
		Vec3 cranium = head.add(0, 0.7, 0);
		cube(out, m, cranium, 0.55f, caveLit(cavern, bone, cranium, new Vec3(0, 1, 0)), 255);
		tube(out, m, cranium.add(along.scale(0.4)), cranium.add(along.scale(2.1)).add(0, -0.35, 0), 0.45, 0.22,
				caveLit(cavern, bone, cranium, new Vec3(0, 1, 0)));
		for (int s = -1; s <= 1; s += 2) {
			Vec3 root = cranium.add(out2.scale(0.35 * s)).add(0, 0.35, 0);
			Vec3 tip = root.add(along.scale(-1.9)).add(0, 1.1, 0).add(out2.scale(0.5 * s));
			cone(out, m, root, tip, 0.22f, caveLit(cavern, lerpColor(bone, 0x3A2A1A, 0.35f), root, new Vec3(0, 1, 0)),
					caveLit(cavern, bone, tip, new Vec3(0, 1, 0)), 950 + s);
			Vec3 eye = head.add(0, 0.85, 0).add(along.scale(0.35)).add(out2.scale(0.32 * s));
			cube(out, m, eye, 0.13f, 0x0A0604, 255);
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
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
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
	/**
	 * The height the stands stand on: level all round (2026-11-19: following the blocks under each segment, the tiers
	 * rose and fell with the terrain round the arena), at the field's centre.
	 */
	private static double standsBase() {
		return onGround(0, 0).y;
	}

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
		VertexConsumer out = ambianceBuffer(buffers, glow ? com.mystaria.showtime.ShowtimeRenderTypes.GLOW : com.mystaria.showtime.ShowtimeRenderTypes.OPAQUE);
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
			double b0 = standsBase();
			double b1 = b0;
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
			quad(out, m, d0.scale(front).add(0, b0 - 3, 0), d1.scale(front).add(0, b1 - 3, 0), d1.scale(front).add(0, b1 + 1.0, 0),
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
			quad(out, m, d0.scale(outer).add(0, b0 - 3, 0), d1.scale(outer).add(0, b1 - 3, 0), d1.scale(outer).add(0, b1 + top + 3.2, 0),
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
				double base = standsBase() + (j + 1) * crowd.rise;
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
		double base = standsBase() + (row + 1) * crowd.rise;
		CAMERA_FLASHES.add(new CameraFlash(dir.scale(r0 + row * crowd.rowDepth + 0.45).add(0, base + 0.55, 0), ticks));
	}

	private static void drawCameraFlashes(PoseStack pose, MultiBufferSource buffers, float time) {
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
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
		VertexConsumer out = ambianceBuffer(buffers, glow ? com.mystaria.showtime.ShowtimeRenderTypes.GLOW : com.mystaria.showtime.ShowtimeRenderTypes.OPAQUE);
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
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
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
			floorDisc(out, m, pool.x, pool.z, 2.4, color, Math.round(spots.alpha * 1.5f), 0.14);
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
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.TINT);
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
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
		Vec3 at = moonPlace(moon);
		glowDisc(out, pose.last().pose(), at, at.normalize().scale(-1), moon.size * moon.halo, Ambiance.color(moon.color, 0xF4F0E0),
				moon.haloAlpha);
	}

	/** The moon's disc: solid, slightly darker toward one side, with a few soft grey seas. */
	private static void drawMoonDisc(PoseStack pose, MultiBufferSource buffers, Ambiance.Moon moon) {
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.TINT);
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
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.TINT);
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
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.GLOW);
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
		VertexConsumer out = ambianceBuffer(buffers, com.mystaria.showtime.ShowtimeRenderTypes.TINT);
		Matrix4f m = pose.last().pose();
		for (Orbiter o : CRITTERS) {
			double t = time / 20.0;
			double angle = o.angle() + t * 0.18 * (o.kind() % 2 == 0 ? 1 : -1) + Math.sin(t * 0.7 + o.phase()) * 0.4;
			double radius = o.radius() + Math.sin(t * 0.5 + o.phase()) * 2.0;
			double x = Math.cos(angle) * radius;
			double z = Math.sin(angle) * radius;
			Vec3 body = onGround(x, z).add(0, o.height() + Math.sin(t * 1.3 + o.phase()) * 0.5, 0);
			if (hole(body)) {
				continue;
			}
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
				.setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, tileFade < 1 ? Math.round(alpha * tileFade) : alpha);
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
		if (area != null) {
			// Spread over the place near the player: as dense as on a field, so more of them.
			rate *= (float) (AREA_REACH * AREA_REACH / (fieldRadius * fieldRadius));
		}
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
			double px = center.x + Math.cos(angle) * r;
			double pz = center.z + Math.sin(angle) * r;
			double py = center.y + y;
			if (area != null) {
				// Fixed to the place: anywhere in it near the player, on its own ground there (never one height for all).
				Player self = Minecraft.getInstance().player;
				if (self == null) {
					return;
				}
				double reach = Math.sqrt(RANDOM.nextDouble()) * AREA_REACH * (spec.area.equals("sky") ? 1.6 : 1);
				px = self.getX() + Math.cos(angle) * reach;
				pz = self.getZ() + Math.sin(angle) * reach;
				if (!area.contains(px, pz)) {
					continue;
				}
				double floor = areaFloor(level, px, pz, center.y + 3, Double.NaN);
				if (Double.isNaN(floor)) {
					continue; // over a void
				}
				py = floor + (spec.area.equals("ring") ? RANDOM.nextDouble() * 3 : y);
			}
			level.addParticle(particle, px, py, pz,
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
