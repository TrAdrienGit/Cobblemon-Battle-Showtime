package com.mystaria.showtime.battle;

import java.util.UUID;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.mystaria.showtime.audio.BattleMusic;


/**
 * Battle launch cinematic (2026-10-04, after Pokémon X/Y's trainer battle intro), in two parts:
 *
 * <ol>
 *   <li><b>Intro</b>, played by both players as soon as the battle session starts ({@link #INTRO_END} ms, the
 *   same on both clients and for every {@link Intro} — the host's engine starts with it, and both clients hold the
 *   battle's packets until it ends): "eyes meet" side shot of the two players with a "!" over the opponent, then the
 *   2D part of the drawn {@link Intro} ({@link BattleIntroScreen}), white flash.</li>
 *   <li><b>Send-outs</b>, when the battle's first packet arrives: the opponent's Pokémon comes out first, filmed
 *   from the front, then ours, over our shoulder; the camera then blends back to the player's own view.</li>
 * </ol>
 *
 * The camera is moved by {@code CameraMixin} (after vanilla and other camera mods have placed it), the HUD is
 * hidden while a shot plays. Purely local and cosmetic; client thread only.
 */
public final class BattleCinematic {

	// ---- Intro timeline (ms since the session started) ----
	public static final long EYES_MEET_END = 2000;
	public static final long WIPE_END = 2600;
	public static final long PANEL_START = 2750;
	public static final long FLASH_START = 5200;
	public static final long INTRO_END = 5500;
	private static final long EXCLAMATION_START = 250;
	private static final long HUD_FLASH_MS = 500;

	// ---- Send-out timeline (ms since the battle's first packet) ----
	/** Delays of each side's first send-out, in seconds — the opponent's first, then ours (read by the scene). */
	public static final float OPPONENT_SEND_OUT_DELAY = 0.3f;
	public static final float LOCAL_SEND_OUT_DELAY = 2.1f;
	private static final long SHOT_OPPONENT_END = 2100;
	private static final long SHOT_SELF_END = 4000;
	private static final long BLEND_END = 4700;

	private static long introStart = -1;
	private static UUID opponentUuid;
	private static String opponentName = "?";
	private static int opponentTeamSize;
	private static int ownTeamSize;
	private static boolean sendOutPending;
	private static long sendOutStart = -1;
	private static Vec3 localSpot;
	private static Vec3 opponentSpot;
	private static Boolean savedHideGui;
	private static boolean exclamationSoundPlayed;
	/** Run once when the intro ends ({@link #startIntro}). */
	private static Runnable introEndAction;
	private static boolean introEndDone;
	/** A preview plays: its music stops with it instead of handing over to the battle music. */
	private static boolean previewing;

	private BattleCinematic() {
	}

	public record CameraPose(Vec3 position, float yaw, float pitch, boolean detached) {
	}

	/**
	 * The intros (TODO-26, 2026-10-06), each after a Pokémon game; the backend draws one per battle
	 * ({@code BattleSessionStarted.intro}, the ordinal) so that both players watch the same.
	 */
	public enum Intro {
		/** X/Y: eyes-meet box, diamond wipe, challenger panel on speed lines. */
		XY(6, INTRO_END),
		/** Sword/Shield: diagonal blue/pink "VS" split screen, then the opponent's throw. */
		SWORD_SHIELD(8, INTRO_END),
		/** Diamond/Pearl/Platinum: Poké Ball wipe, radial burst, the field's platforms sliding in. */
		DIAMOND_PEARL(4, INTRO_END),
		/** Emerald: rolling Poké Ball wipe, the screen opening on a line, trainers sliding across. */
		EMERALD(3, INTRO_END),
		/** Black/White: zoom blur into the opponent, white, the camera pulling back from their silhouette. */
		BLACK_WHITE(5, INTRO_END),
		/**
		 * Battle Revolution, the match card (2026-11-09): a glowing blue stage, the two team cards sliding in, then
		 * both trainers face to face round a "VS", and a Poké Ball wipe to black.
		 */
		PBR_CARD(4, 8200),
		/**
		 * Battle Revolution, the entrance (2026-11-09): out of black, a wide shot of the field under the battle's
		 * title, then each trainer filmed close with their name and a line, as the stadium games open a match.
		 */
		PBR_ENTRANCE(4, 9400),
		/**
		 * Sun/Moon, the vortex (2026-11-09): rays and rainbow rings burst over the world, white, then the opponent
		 * posing in a turning vortex, the challenge typed below, their throw.
		 */
		SM_VORTEX(7, 7900),
		/**
		 * Ultra Sun/Ultra Moon, the surge (2026-11-09): a starburst cycling through colours, white, the opponent coming
		 * out of the white in a ring of lightning, the challenge, then an iris closing on a Poké Ball.
		 */
		USUM_SURGE(7, 8300),
		/**
		 * Omega Ruby/Alpha Sapphire, the silhouette (2026-11-09): a checker of diamonds, two Poké Balls rolling
		 * across the black, the panel opening on a line, the opponent's silhouette sliding in, then its colours.
		 */
		ORAS_SILHOUETTE(6, 7600),
		/**
		 * Battle Revolution, the face-off (2026-11-09): a montage of moving close shots on each trainer (rising from
		 * their feet, arcing round them) with their name and line, then both in profile round a "VS".
		 */
		PBR_FACE_OFF(4, 9000),
		/**
		 * Scarlet/Violet, the showdown (2026-11-09): no screen at all, the world only: over one shoulder, then the
		 * other, name tags sliding in, and a crane rising over the field on "Battle start!".
		 */
		SV_SHOWDOWN(9, 7600),
		/**
		 * Red/Blue, the clock (2026-11-10): two hands sweeping the screen to black, then the battlefield in Game Boy
		 * greens, the trainers sliding in as silhouettes, and the colour flooding the whole scene.
		 */
		RBY_CLOCK(1, 7600),
		/**
		 * Gold/Silver, the stairs (2026-11-10): the screen flashing, eaten to black by a staircase of raised blocks,
		 * then warm Johto light, the trainers sliding in as silhouettes and taking their colours.
		 */
		GSC_STAIRS(2, 7600),
		/**
		 * Colosseum, the twisting world (2026-11-10): the camera spinning faster and faster into a spiral, then a wide
		 * shot of the opponent under the challenge, and the two trainers from the ground.
		 */
		COLOSSEUM_SWIRL(3, 8600),
		/**
		 * Diamond/Pearl, the blinds (2026-11-10): two white flashes, a Poké Ball spinning, blinds sliding shut in
		 * turns, then the battlefield opening behind them.
		 */
		DP_BLINDS(4, 7400);

		/** The generation of the game it is after (the menu's filters). */
		public final int generation;
		/** Its length, from the session start (the first {@link #EYES_MEET_END} ms are skipped). */
		public final long length;

		Intro(int generation, long length) {
			this.generation = generation;
			this.length = length;
		}

		/** Ordinals are shared with Phantasmon's backend: new intros only ever go at the end. */
		public static Intro of(int index) {
			Intro[] all = values();
			return index >= 0 && index < all.length ? all[index] : XY;
		}

		/** Its white flash (the stadium intros end on black instead: none). */
		public long flashStart() {
			return length - (INTRO_END - FLASH_START);
		}

		public boolean flashes() {
			return this != PBR_CARD && this != PBR_ENTRANCE && this != USUM_SURGE && this != SV_SHOWDOWN;
		}
	}

	/** End of the Black/White zoom into the opponent (world camera, before the white). */
	public static final long ZOOM_END = 2700;
	private static Intro intro = Intro.XY;

	// =====================================================================
	// Lifecycle
	// =====================================================================

	/**
	 * Battle session started: plays the intro, with the intro music, then the battle music when the intro ends (not
	 * when its file does). {@code onIntroEnd} runs on the client thread when the intro is over; may be null.
	 */
	public static void startIntro(UUID opponent, String name, int teamSize, int ownSize, Intro which, Runnable onIntroEnd) {
		introEndAction = onIntroEnd;
		ownTeamSize = Mth.clamp(ownSize, 0, 6);
		// The "eyes meet" shot is gone (2026-11-01): the intro starts at its wipe, from the player's own view.
		introStart = System.currentTimeMillis() - EYES_MEET_END;
		intro = which == null ? Intro.XY : which;
		entranceAngle = null;
		opponentUuid = opponent;
		opponentName = name == null ? "?" : name;
		opponentTeamSize = teamSize < 0 ? -1 : Mth.clamp(teamSize, 0, 6);
		sendOutPending = true;
		sendOutStart = -1;
		exclamationSoundPlayed = false;
		introEndDone = false;
		previewing = false;
		BattleMusic.play(BattleMusic.Track.BATTLE);
		Minecraft.getInstance().setScreen(new BattleIntroScreen());
	}

	/**
	 * Preview ({@code /showtime preview intro}): plays an intro alone, against the nearest other player (or oneself),
	 * without a battle behind it; the music stops with it.
	 */
	public static void preview(Intro which) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		Player opponent = mc.level.players().stream().filter(p -> p != mc.player)
				.min(java.util.Comparator.comparingDouble(p -> p.distanceToSqr(mc.player))).orElse(mc.player);
		// A preview has no battle: our own team on both sides.
		int own = (int) com.cobblemon.mod.common.client.CobblemonClient.INSTANCE.getStorage().getParty().getSlots().stream()
				.filter(pokemon -> pokemon != null && !pokemon.isFainted()).count();
		startIntro(opponent.getUUID(), opponent.getGameProfile().getName(), own, own, which, null);
		sendOutPending = false;
		previewing = true;
	}

	/**
	 * The intro a player vs player battle opens with, from the player's choices (menu, Intros): an intro switched on,
	 * a favourite when "only my favourites" is on and there is one; null when every intro is switched off (no intro).
	 */
	public static Intro pickIntro() {
		java.util.List<Intro> on = new java.util.ArrayList<>();
		for (Intro each : Intro.values()) {
			if (com.mystaria.showtime.config.IntroSettings.enabled(each.name())) {
				on.add(each);
			}
		}
		if (com.mystaria.showtime.config.IntroSettings.onlyFavorites()
				&& on.stream().anyMatch(each -> com.mystaria.showtime.config.IntroSettings.favorite(each.name()))) {
			on.removeIf(each -> !com.mystaria.showtime.config.IntroSettings.favorite(each.name()));
		}
		return on.isEmpty() ? null : on.get(new java.util.Random().nextInt(on.size()));
	}

	/** The menu's preview of one intro; {@code after} runs when it is over (back to the menu). */
	public static void preview(Intro which, Runnable after) {
		preview(which);
		introEndAction = after;
	}

	/** Local clock time at which the intro is over (engine start / end of the guest's packet hold). */
	public static long introEndsAt() {
		return introStart < 0 ? 0 : introStart + intro.length;
	}

	public static boolean introPlaying() {
		return introStart >= 0 && introElapsed() < intro.length;
	}

	public static long introElapsed() {
		return introStart < 0 ? Long.MAX_VALUE : System.currentTimeMillis() - introStart;
	}

	public static Intro intro() {
		return intro;
	}

	public static UUID opponentUuid() {
		return opponentUuid;
	}

	public static String opponentName() {
		return opponentName;
	}

	public static int ownTeamSize() {
		return ownTeamSize;
	}

	/**
	 * The opponent's team size, or -1 when this client can't know it: Cobblemon only sends a player its opponents'
	 * active Pokémon. Cobblemon Battle Extras, when its server side is installed, sends every team's balls: then its
	 * count is used (read by name; it may arrive after the intro started, so it is asked each time).
	 */
	public static int opponentTeamSize() {
		if (opponentTeamSize >= 0 || opponentUuid == null) {
			return opponentTeamSize;
		}
		try {
			Class<?> data = Class.forName("name.modid.client.net.TeamBallClientData");
			if ((boolean) data.getMethod("hasData", UUID.class).invoke(null, opponentUuid)) {
				Object balls = data.getMethod("getTeamBalls", UUID.class).invoke(null, opponentUuid);
				if (balls instanceof java.util.Map<?, ?> map && !map.isEmpty()) {
					return Math.min(6, map.size());
				}
			}
		} catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
			// Battle Extras isn't installed: unknown.
		}
		return -1;
	}

	/**
	 * The battle's first packet: if an intro just played, the send-outs get staged and filmed. Returns whether
	 * they should be (the scene then delays each side's first send-out).
	 */
	public static boolean beginSendOuts(Vec3 localPokemonSpot, Vec3 opponentPokemonSpot) {
		if (!sendOutPending || localPokemonSpot == null || opponentPokemonSpot == null) {
			sendOutPending = false;
			return false;
		}
		sendOutPending = false;
		localSpot = localPokemonSpot;
		opponentSpot = opponentPokemonSpot;
		sendOutStart = System.currentTimeMillis();
		return true;
	}

	/** No filmed send-outs after this intro (a Cobblemon battle: the server sends its Pokémon out itself). */
	public static void skipSendOuts() {
		sendOutPending = false;
	}

	/** Battle over / connection lost: everything back to normal at once. */
	public static void stop() {
		introStart = -1;
		introEndAction = null;
		sendOutPending = false;
		sendOutStart = -1;
		applyHideGui(false);
		if (Minecraft.getInstance().screen instanceof BattleIntroScreen) {
			Minecraft.getInstance().setScreen(null);
		}
	}

	/** Every client tick: HUD visibility and the end of each part. */
	public static void tick() {
		long now = System.currentTimeMillis();
		if (introStart >= 0 && now - introStart >= intro.length + HUD_FLASH_MS && !sendOutPending) {
			introStart = -1;
		}
		if (sendOutStart >= 0 && now - sendOutStart >= BLEND_END) {
			sendOutStart = -1;
		}
		if (introStart >= 0 && !introEndDone && now - introStart >= intro.length) {
			introEndDone = true;
			if (previewing) {
				previewing = false;
				BattleMusic.stop();
			} else {
				BattleMusic.play(BattleMusic.Track.BATTLE);
			}
			Runnable action = introEndAction;
			introEndAction = null;
			if (action != null) {
				action.run();
			}
		}
		if (introPlaying() && !exclamationSoundPlayed && introElapsed() >= EXCLAMATION_START) {
			exclamationSoundPlayed = true;
			play(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, 0.8f);
		}
		applyHideGui(introPlaying() || (sendOutStart >= 0 && now - sendOutStart < SHOT_SELF_END) || BattleShowtime.playing());
	}

	private static void applyHideGui(boolean hide) {
		var options = Minecraft.getInstance().options;
		if (hide && savedHideGui == null) {
			savedHideGui = options.hideGui;
			options.hideGui = true;
		} else if (!hide && savedHideGui != null) {
			options.hideGui = savedHideGui;
			savedHideGui = null;
		}
	}

	public static void play(SoundEvent sound, float pitch, float volume) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
	}

	// =====================================================================
	// Camera (CameraMixin)
	// =====================================================================

	/** Where the camera should be this frame, or null to leave it where vanilla (and other mods) put it. */
	public static CameraPose cameraPose(Camera camera, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		Player self = mc.player;
		ClientLevel level = mc.level;
		if (self == null || level == null) {
			return null;
		}
		if (introPlaying()) {
			long elapsed = introElapsed();
			if (intro == Intro.COLOSSEUM_SWIRL && elapsed >= EYES_MEET_END) {
				return colosseumShot(level, self, partialTick, elapsed);
			}
			if (intro == Intro.PBR_FACE_OFF && elapsed >= EYES_MEET_END) {
				return faceOffShot(level, self, partialTick, elapsed);
			}
			if (intro == Intro.SV_SHOWDOWN && elapsed >= EYES_MEET_END) {
				return showdownShot(level, self, partialTick, elapsed);
			}
			if (intro == Intro.PBR_ENTRANCE && elapsed >= EYES_MEET_END) {
				return entranceShot(level, self, partialTick, elapsed);
			}
			if (intro == Intro.BLACK_WHITE && elapsed >= EYES_MEET_END) {
				return zoomShot(level, self, partialTick, Math.min(1f, (elapsed - EYES_MEET_END) / (float) (ZOOM_END - EYES_MEET_END)));
			}
			return null;
		}
		if (sendOutStart < 0) {
			return null;
		}
		long t = System.currentTimeMillis() - sendOutStart;
		if (t < SHOT_OPPONENT_END) {
			return opponentPokemonShot(t / (float) SHOT_OPPONENT_END);
		}
		CameraPose shoulder = shoulderShot(self, partialTick, Math.min(1f, (t - SHOT_OPPONENT_END) / (float) (SHOT_SELF_END - SHOT_OPPONENT_END)));
		if (t < SHOT_SELF_END) {
			return shoulder;
		}
		// Blend back to the player's own view.
		float k = (t - SHOT_SELF_END) / (float) (BLEND_END - SHOT_SELF_END);
		float s = k * k * (3 - 2 * k);
		Vec3 position = shoulder.position().lerp(camera.getPosition(), s);
		return new CameraPose(position, Mth.rotLerp(s, shoulder.yaw(), camera.getYRot()),
				Mth.lerp(s, shoulder.pitch(), camera.getXRot()), k < 0.9f);
	}

	/** Side shot of the two trainers facing each other (or of us alone if the opponent isn't loaded), slow push-in. */
	private static CameraPose eyesMeetShot(ClientLevel level, Player self, float partialTick, long elapsed) {
		Vec3 selfEye = self.getEyePosition(partialTick);
		Player opponent = com.mystaria.showtime.api.ShowtimeHooks.player(level, opponentUuid);
		keepVisible(self);
		keepVisible(opponent);
		float push = 1.08f - 0.08f * Math.min(1f, elapsed / (float) EYES_MEET_END);
		if (opponent == null) {
			Vec3 forward = horizontal(self.getViewVector(partialTick));
			Vec3 position = selfEye.add(forward.scale(3.2 * push)).add(0, 0.2, 0);
			return lookAt(position, selfEye.add(0, -0.3, 0), true);
		}
		Vec3 opponentEye = opponent.getEyePosition(partialTick);
		Vec3 middle = selfEye.add(opponentEye).scale(0.5);
		Vec3 axis = horizontal(opponentEye.subtract(selfEye));
		double gap = axis.length();
		Vec3 direction = gap < 1.0E-3 ? horizontal(self.getViewVector(partialTick)) : axis.scale(1 / gap);
		Vec3 side = new Vec3(-direction.z, 0, direction.x);
		// Far apart: over our shoulder, looking at the opponent — a wide side shot made both trainers specks seen from
		// high up (a recording 2026-10-30, the "invisible" trainers).
		if (gap > SIDE_SHOT_MAX_GAP) {
			Vec3 right = new Vec3(-direction.z, 0, direction.x);
			Vec3 feet = self.getPosition(partialTick);
			for (double back : new double[] { 3.2 * push, 2.2, 1.4 }) {
				Vec3 candidate = feet.subtract(direction.scale(back)).add(right.scale(1.0)).add(0, 2.0, 0);
				if (!solid(level, candidate)) {
					return lookAt(candidate, opponentEye.add(0, -0.3, 0), true);
				}
			}
			return lookAt(selfEye.add(0, 0.6, 0), opponentEye, true);
		}
		// Close enough: from the side, back far enough for both to fit the screen (12 blocks at most), on the side that
		// sees both, a little higher if something is in the way.
		Minecraft mc = Minecraft.getInstance();
		double verticalFov = Math.toRadians(mc.options.fov().get());
		double aspect = mc.getWindow().getWidth() / (double) Math.max(1, mc.getWindow().getHeight());
		double halfWidth = Math.atan(Math.tan(verticalFov / 2) * aspect);
		double distance = Mth.clamp((gap / 2 + 1.5) / Math.tan(halfWidth), 3.0, 12.0) * push;
		Vec3 position = null;
		for (double rise : new double[] { 0.5, 2.0, 3.5 }) {
			for (int sign : new int[] { 1, -1 }) {
				Vec3 candidate = middle.add(side.scale(sign * distance)).add(0, rise, 0);
				if (!solid(level, candidate) && sees(level, candidate, selfEye, self) && sees(level, candidate, opponentEye, self)) {
					position = candidate;
					break;
				}
			}
			if (position != null) {
				break;
			}
		}
		if (position == null) {
			Vec3 near = middle.add(side.scale(Math.min(distance, 4.0))).add(0, 0.5, 0);
			position = solid(level, near) ? middle.add(0, 2.5, 0) : near;
		}
		return lookAt(position, middle.add(0, -0.2, 0), true);
	}

	// ---- Battle Revolution, the entrance -------------------------------

	/** The wide shot's starting angle round the field, picked once per intro so that it never jumps. */
	private static Double entranceAngle;

	/**
	 * The entrance's world shots: a slow wide orbit of the field (the midpoint of the two trainers), then a low close
	 * shot on the opponent, then on us, each drifting sideways. Every position is checked for blocks in the way.
	 */
	private static CameraPose entranceShot(ClientLevel level, Player self, float partialTick, long elapsed) {
		Player opponent = com.mystaria.showtime.api.ShowtimeHooks.player(level, opponentUuid);
		keepVisible(self);
		keepVisible(opponent);
		Vec3 mine = self.getPosition(partialTick);
		Vec3 theirs = opponent != null ? opponent.getPosition(partialTick) : mine.add(horizontal(self.getViewVector(partialTick)).scale(6));
		Vec3 center = mine.add(theirs).scale(0.5);
		Vec3 axis = horizontal(theirs.subtract(mine));
		double gap = Math.sqrt(mine.distanceToSqr(theirs));
		if (elapsed < BattleIntroScreen.PBR_OPPONENT) {
			double radius = Math.max(6.0, gap * 0.85 + 3.0);
			double height = 3.2 + gap * 0.12;
			Vec3 look = center.add(0, 1.0, 0);
			if (entranceAngle == null) {
				double side = Math.atan2(axis.x, -axis.z); // a right angle to the trainers' line
				entranceAngle = side;
				for (double offset : new double[] { 0, Math.PI, 0.6, -0.6, Math.PI + 0.6, Math.PI - 0.6, 1.2, -1.2 }) {
					Vec3 candidate = center.add(Math.cos(side + offset) * radius, height, Math.sin(side + offset) * radius);
					if (!solid(level, candidate) && sees(level, candidate, look, self)) {
						entranceAngle = side + offset;
						break;
					}
				}
			}
			double t = (elapsed - BattleIntroScreen.PBR_ARENA) / 1000.0;
			double angle = entranceAngle + Math.toRadians(5.0 * t);
			Vec3 position = center.add(Math.cos(angle) * radius, height - 0.15 * t, Math.sin(angle) * radius);
			return lookAt(position, look, true);
		}
		boolean opponentShot = elapsed < BattleIntroScreen.PBR_SELF;
		Player who = opponentShot && opponent != null ? opponent : self;
		Vec3 at = opponentShot ? theirs : mine;
		Vec3 facing = horizontal((opponentShot ? mine : theirs).subtract(at));
		Vec3 right = new Vec3(-facing.z, 0, facing.x);
		double t = (elapsed - (opponentShot ? BattleIntroScreen.PBR_OPPONENT : BattleIntroScreen.PBR_SELF)) / 1000.0;
		Vec3 face = who.getEyePosition(partialTick).add(0, -0.15, 0);
		for (int sign : new int[] { 1, -1 }) {
			for (double distance : new double[] { 2.4, 1.7 }) {
				Vec3 candidate = at.add(facing.scale(distance)).add(right.scale(sign * (0.9 + 0.12 * t))).add(0, 1.15, 0);
				if (!solid(level, candidate) && sees(level, candidate, face, who)) {
					return lookAt(candidate, face, true);
				}
			}
		}
		return lookAt(at.add(facing.scale(1.6)).add(0, 1.4, 0), face, true);
	}

	/** The two trainers this frame: ours, theirs (or a spot ahead of us when theirs isn't loaded). */
	private record Pair(Player self, Player opponent, Vec3 mine, Vec3 theirs, Vec3 axis) {
	}

	private static Pair pair(ClientLevel level, Player self, float partialTick) {
		Player opponent = com.mystaria.showtime.api.ShowtimeHooks.player(level, opponentUuid);
		keepVisible(self);
		keepVisible(opponent);
		Vec3 mine = self.getPosition(partialTick);
		Vec3 theirs = opponent != null ? opponent.getPosition(partialTick) : mine.add(horizontal(self.getViewVector(partialTick)).scale(6));
		return new Pair(self, opponent, mine, theirs, horizontal(theirs.subtract(mine)));
	}

	/** The first of the candidates that is out of blocks and sees {@code look}; else the last one. */
	private static CameraPose firstClear(ClientLevel level, Player self, Vec3 look, Vec3... candidates) {
		for (Vec3 candidate : candidates) {
			if (!solid(level, candidate) && sees(level, candidate, look, self)) {
				return lookAt(candidate, look, true);
			}
		}
		return lookAt(candidates[candidates.length - 1], look, true);
	}

	/**
	 * Battle Revolution's face-off: four moving close shots (the opponent rising from their feet, then arcing round
	 * them; the same for us), then both in profile from low on the side, for the "VS".
	 */
	private static CameraPose faceOffShot(ClientLevel level, Player self, float partialTick, long elapsed) {
		Pair p = pair(level, self, partialTick);
		long start = BattleIntroScreen.FO_SHOTS;
		if (elapsed >= BattleIntroScreen.FO_VS - 0) {
			float t = (elapsed - BattleIntroScreen.FO_VS) / 1000f;
			Vec3 middle = p.mine().add(p.theirs()).scale(0.5);
			Vec3 side = new Vec3(-p.axis().z, 0, p.axis().x);
			double gap = Math.sqrt(p.mine().distanceToSqr(p.theirs()));
			double distance = Math.max(3.5, gap * 0.75 + 1.5) * (1.0 - 0.05 * t);
			Vec3 look = middle.add(0, 1.2, 0);
			return firstClear(level, self, look, middle.add(side.scale(distance)).add(0, 0.6, 0),
					middle.add(side.scale(-distance)).add(0, 0.6, 0), middle.add(side.scale(distance * 0.7)).add(0, 2.2, 0));
		}
		int shot = (int) Math.max(0, Math.min(3, (elapsed - start) / BattleIntroScreen.FO_SHOT));
		float t = Mth.clamp((elapsed - start - shot * BattleIntroScreen.FO_SHOT) / (float) BattleIntroScreen.FO_SHOT, 0f, 1f);
		float ease = t * t * (3 - 2 * t);
		boolean opponent = shot < 2;
		Player who = opponent && p.opponent() != null ? p.opponent() : self;
		Vec3 at = opponent ? p.theirs() : p.mine();
		Vec3 facing = horizontal((opponent ? p.mine() : p.theirs()).subtract(at));
		Vec3 right = new Vec3(-facing.z, 0, facing.x);
		double eye = who.getEyeHeight();
		if (shot % 2 == 0) {
			// Rising from the feet to the face, in front of them.
			double height = Mth.lerp(ease, 0.25, eye + 0.1);
			Vec3 look = at.add(0, Mth.lerp(ease, 0.3, eye - 0.1), 0);
			return firstClear(level, self, look, at.add(facing.scale(1.9)).add(right.scale(0.5)).add(0, height, 0),
					at.add(facing.scale(1.5)).add(right.scale(-0.5)).add(0, height, 0), at.add(facing.scale(1.2)).add(0, eye + 0.4, 0));
		}
		// Arcing round them at chest height, from their side to their front.
		double angle = Math.toRadians(Mth.lerp(ease, 75, 15));
		Vec3 offset = facing.scale(Math.cos(angle) * 2.2).add(right.scale(Math.sin(angle) * 2.2));
		Vec3 look = at.add(0, eye - 0.25, 0);
		return firstClear(level, self, look, at.add(offset).add(0, 1.3, 0), at.add(offset.scale(0.7)).add(0, 1.3, 0),
				at.add(facing.scale(1.4)).add(0, eye + 0.3, 0));
	}

	/** Where the camera stood when Colosseum's spin began: it turns from there. */
	private static Float swirlYaw;

	/**
	 * Colosseum's twisting world: from the player's eyes the view spins faster and faster (the screen's spiral does
	 * the rest), then a wide low shot of the opponent, then both trainers from the ground, the camera creeping in.
	 */
	private static CameraPose colosseumShot(ClientLevel level, Player self, float partialTick, long elapsed) {
		Pair p = pair(level, self, partialTick);
		if (elapsed < BattleIntroScreen.CO_WIDE) {
			if (swirlYaw == null) {
				swirlYaw = self.getViewYRot(partialTick);
			}
			float t = (elapsed - EYES_MEET_END) / 1000f;
			float yaw = swirlYaw + 160f * t * t * t;
			Vec3 eye = self.getEyePosition(partialTick);
			// First person (not detached): from inside the head, a detached camera drew the player's own body round it.
			return new CameraPose(eye, yaw, self.getViewXRot(partialTick) * (1 - Math.min(1f, t)), false);
		}
		swirlYaw = null;
		Vec3 right = new Vec3(-p.axis().z, 0, p.axis().x);
		if (elapsed < BattleIntroScreen.CO_GROUND) {
			// Wide and low, in front of the opponent, a little to the side.
			float t = (elapsed - BattleIntroScreen.CO_WIDE) / 1000f;
			Vec3 look = p.theirs().add(0, 1.2, 0);
			Vec3 front = p.theirs().subtract(p.axis().scale(5.5 - 0.3 * t)).add(right.scale(1.6)).add(0, 1.0, 0);
			return firstClear(level, self, look, front, p.theirs().subtract(p.axis().scale(3.5)).add(right.scale(1.0)).add(0, 1.4, 0),
					p.theirs().add(0, 3.5, 0));
		}
		// From the ground, on the side: both trainers small in a wide frame, creeping in.
		float t = (elapsed - BattleIntroScreen.CO_GROUND) / 1000f;
		Vec3 middle = p.mine().add(p.theirs()).scale(0.5);
		double gap = Math.sqrt(p.mine().distanceToSqr(p.theirs()));
		double distance = (gap * 0.9 + 3.0) * (1.0 - 0.04 * t);
		Vec3 look = middle.add(0, 1.0, 0);
		return firstClear(level, self, look, middle.add(right.scale(distance)).add(0, 0.35, 0),
				middle.add(right.scale(-distance)).add(0, 0.35, 0), middle.add(right.scale(distance * 0.7)).add(0, 1.8, 0));
	}

	/**
	 * Scarlet/Violet's showdown, no cut to a screen: over our shoulder toward the opponent, then over theirs toward us,
	 * then a crane rising from between them to a high view of the field.
	 */
	private static CameraPose showdownShot(ClientLevel level, Player self, float partialTick, long elapsed) {
		Pair p = pair(level, self, partialTick);
		Vec3 right = new Vec3(-p.axis().z, 0, p.axis().x);
		if (elapsed < BattleIntroScreen.SV_CRANE) {
			boolean ours = elapsed < BattleIntroScreen.SV_REVERSE;
			long from = ours ? EYES_MEET_END : BattleIntroScreen.SV_REVERSE;
			long to = ours ? BattleIntroScreen.SV_REVERSE : BattleIntroScreen.SV_CRANE;
			float t = (elapsed - from) / (float) (to - from);
			Vec3 back = ours ? p.mine() : p.theirs();
			Vec3 ahead = ours ? p.theirs() : p.mine();
			Vec3 forward = ours ? p.axis() : p.axis().scale(-1);
			Vec3 side = ours ? right : right.scale(-1);
			double distance = Mth.lerp(t, 3.6, 2.6);
			Vec3 look = ahead.add(0, 1.4, 0);
			return firstClear(level, self, look, back.subtract(forward.scale(distance)).add(side.scale(0.9)).add(0, 2.0, 0),
					back.subtract(forward.scale(distance * 0.6)).add(side.scale(0.7)).add(0, 2.2, 0),
					back.add(0, 3.0, 0));
		}
		float t = Math.min(1f, (elapsed - BattleIntroScreen.SV_CRANE) / (float) (Intro.SV_SHOWDOWN.length - BattleIntroScreen.SV_CRANE));
		float ease = 1f - (1f - t) * (1f - t);
		Vec3 middle = p.mine().add(p.theirs()).scale(0.5);
		double gap = Math.sqrt(p.mine().distanceToSqr(p.theirs()));
		Vec3 eye = middle.add(right.scale(Mth.lerp(ease, 1.2, 3.0 + gap * 0.5))).add(0, Mth.lerp(ease, 1.2, 5.0 + gap * 0.35), 0);
		Vec3 look = middle.add(0, Mth.lerp(ease, 1.2, 0.4), 0);
		return firstClear(level, self, look, eye, middle.add(0, Mth.lerp(ease, 2.0, 6.0 + gap * 0.3), 0));
	}

	/**
	 * The stadium intros' place: the name of the biome the battle is in ("Plains"). Not a "colosseum": battles happen
	 * anywhere in a Minecraft world.
	 */
	public static String placeName() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return "";
		}
		String biome = mc.level.getBiome(mc.player.blockPosition()).unwrapKey()
				.map(key -> net.minecraft.network.chat.Component.translatable(net.minecraft.Util.makeDescriptionId("biome", key.location())).getString())
				.orElse("");
		return biome;
	}

	/** Beyond this gap between the trainers, the eyes-meet shot is taken over our shoulder instead of from the side. */
	private static final double SIDE_SHOT_MAX_GAP = 16.0;

	/** Black/White: from the end of the eyes-meet shot, rushing at the opponent's face (or ours if not loaded). */
	private static CameraPose zoomShot(ClientLevel level, Player self, float partialTick, float progress) {
		CameraPose start = eyesMeetShot(level, self, partialTick, EYES_MEET_END);
		Player opponent = com.mystaria.showtime.api.ShowtimeHooks.player(level, opponentUuid);
		Player target = opponent != null ? opponent : self;
		Vec3 face = target.getEyePosition(partialTick);
		Vec3 toward = face.subtract(start.position());
		double length = toward.length();
		float k = progress * progress * progress;
		Vec3 position = length < 1.0E-3 ? start.position()
				: start.position().add(toward.scale(k * Math.max(0, (length - 0.9) / length)));
		return lookAt(position, face.add(0, -0.1, 0), true);
	}

	/** Facing the opponent's Pokémon as it comes out, from our side of the field, slow push-in. */
	private static CameraPose opponentPokemonShot(float progress) {
		Vec3 towardUs = horizontal(localSpot.subtract(opponentSpot));
		Vec3 direction = towardUs.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : towardUs.normalize();
		Vec3 side = new Vec3(-direction.z, 0, direction.x);
		double distance = 4.6 - 1.0 * progress;
		Vec3 position = opponentSpot.add(direction.scale(distance)).add(side.scale(1.1)).add(0, 1.4, 0);
		return lookAt(position, opponentSpot.add(0, 0.8, 0), true);
	}

	/** Behind us, over the right shoulder, looking where our Pokémon comes out. */
	private static CameraPose shoulderShot(Player self, float partialTick, float progress) {
		Vec3 feet = self.getPosition(partialTick);
		Vec3 toward = horizontal(opponentSpot.subtract(feet));
		Vec3 forward = toward.lengthSqr() < 1.0E-4 ? horizontal(self.getViewVector(partialTick)) : toward.normalize();
		Vec3 right = new Vec3(-forward.z, 0, forward.x);
		double back = 2.6 - 0.4 * progress;
		Vec3 position = feet.subtract(forward.scale(back)).add(right.scale(1.0)).add(0, 2.0, 0);
		return lookAt(position, localSpot.add(forward.scale(1.5)).add(0, 0.6, 0), true);
	}

	private static CameraPose lookAt(Vec3 position, Vec3 target, boolean detached) {
		Vec3 d = target.subtract(position);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		return new CameraPose(position, yaw, pitch, detached);
	}

	/** Showtime placed the camera this frame (CameraMixin): no first-person hand in front of the shot. */
	private static boolean cameraTaken;

	public static void setCameraTaken(boolean taken) {
		cameraTaken = taken;
	}

	public static boolean cameraTaken() {
		return cameraTaken;
	}

	/**
	 * {@code EntityRendererCinematicMixin}: the two trainers are drawn during the intro whatever the distance — the
	 * camera backs far off to fit them both, beyond the game's entity distance (2026-10-30: both invisible).
	 */
	public static boolean forcedVisible(net.minecraft.world.entity.Entity entity) {
		if (!introPlaying() || !(entity instanceof Player)) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		return entity == mc.player || entity.getUUID().equals(opponentUuid)
				|| entity == com.mystaria.showtime.api.ShowtimeHooks.player(mc.level, opponentUuid);
	}

	private static java.lang.reflect.Method cullingTimeout;
	private static boolean cullingChecked;

	/**
	 * EntityCulling (in the modpack) decides what is visible in the background, from where the camera was: the camera
	 * jumping far away for the intro left the trainers hidden (2026-10-30). Its own API keeps them drawn a while;
	 * called by name, Showtime doesn't need it.
	 */
	private static void keepVisible(net.minecraft.world.entity.Entity entity) {
		if (entity == null) {
			return;
		}
		if (!cullingChecked) {
			cullingChecked = true;
			try {
				cullingTimeout = Class.forName("dev.tr7zw.entityculling.versionless.access.Cullable").getMethod("setTimeout");
			} catch (ReflectiveOperationException | LinkageError ex) {
				cullingTimeout = null; // EntityCulling not installed
			}
		}
		if (cullingTimeout != null && cullingTimeout.getDeclaringClass().isInstance(entity)) {
			try {
				cullingTimeout.invoke(entity);
			} catch (ReflectiveOperationException ex) {
				cullingTimeout = null;
			}
		}
	}

	/** No block in the way from {@code from} to {@code to} (glass and leaves let the eye through). */
	private static boolean sees(ClientLevel level, Vec3 from, Vec3 to, Player self) {
		return level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.VISUAL,
				net.minecraft.world.level.ClipContext.Fluid.NONE, self)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
	}

	private static boolean solid(ClientLevel level, Vec3 position) {
		BlockPos pos = BlockPos.containing(position);
		return level.getBlockState(pos).isCollisionShapeFullBlock(level, pos);
	}

	private static Vec3 horizontal(Vec3 vector) {
		Vec3 flat = new Vec3(vector.x, 0, vector.z);
		return flat.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : flat.normalize();
	}

	// =====================================================================
	// World and HUD overlays
	// =====================================================================

	/** The "!" popping over the opponent's head during the eyes-meet shot. */
	public static void renderWorld(WorldRenderContext context) {
		long elapsed = introElapsed();
		if (!introPlaying() || elapsed < EXCLAMATION_START || elapsed >= EYES_MEET_END || opponentUuid == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Player opponent = com.mystaria.showtime.api.ShowtimeHooks.player(mc.level, opponentUuid);
		if (opponent == null) {
			return;
		}
		float pop = Math.min(1f, (elapsed - EXCLAMATION_START) / 180f);
		float scale = pop < 1f ? 1.35f * pop : 1f + 0.06f * (float) Math.sin((elapsed - EXCLAMATION_START) / 90.0);
		float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(true);
		Vec3 head = opponent.getPosition(partialTick).add(0, opponent.getBbHeight() + 0.75, 0);
		Camera camera = context.camera();
		Vec3 cameraPosition = camera.getPosition();

		PoseStack pose = new PoseStack();
		pose.translate(head.x - cameraPosition.x, head.y - cameraPosition.y, head.z - cameraPosition.z);
		pose.mulPose(camera.rotation());
		float size = 0.06f * scale;
		pose.scale(size, -size, size);
		Font font = mc.font;
		String mark = "!";
		Matrix4f matrix = pose.last().pose();
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		font.drawInBatch(" " + mark + " ", -font.width(" " + mark + " ") / 2f, -4, 0xFFE8362F, false, matrix, buffers,
				Font.DisplayMode.SEE_THROUGH, 0xF0FFFFFF, 0xF000F0);
		buffers.endBatch();
	}

	/** White flash fading out over the world once the intro panel closes. */
	public static void renderHud(GuiGraphics graphics) {
		if (introStart < 0) {
			return;
		}
		long t = introElapsed() - intro.length;
		if (t < 0 || t >= HUD_FLASH_MS) {
			return;
		}
		int alpha = Math.round(255 * (1f - t / (float) HUD_FLASH_MS));
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (alpha << 24) | 0xFFFFFF);
	}
}
