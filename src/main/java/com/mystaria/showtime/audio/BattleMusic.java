package com.mystaria.showtime.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;

import com.mystaria.showtime.config.ShowtimeConfig;

/**
 * Battle music from a resource pack (Adrien 2026-10-05; part of Cobblemon Battle Showtime since 2026-10-29, D-37) — no
 * JSON to write: every {@code .ogg} found in {@code assets/cobblemon_battle_showtime/sounds/music/<folder>/} of any active
 * resource pack is a candidate, one is picked at random (packs made for Phantasmon, under {@code assets/phantasmon/},
 * still count).
 * Folders: {@code lobby} (loops while the lobby is open), {@code intro} (the launch cinematic, then the battle music
 * when it ends), {@code battle} (loops for the whole battle), {@code victory} / {@code defeat} (once at the end). An
 * empty or missing folder just means silence there. Played on the Music volume slider; Minecraft's own background
 * music is held while one plays ({@code MusicManagerMixin}). Client thread only.
 */
public final class BattleMusic {

	public enum Track {
		LOBBY("lobby", true, 0), INTRO("intro", false, 0), BATTLE("battle", true, 0),
		// End-of-battle jingles: 10 s at most, whatever the file's length (Adrien 2026-10-05).
		VICTORY("victory", false, 10), DEFEAT("defeat", false, 10);

		final String folder;
		final boolean loops;
		/** Longest play time in seconds (faded out before), 0 = the whole file. */
		final int maxSeconds;

		Track(String folder, boolean loops, int maxSeconds) {
			this.folder = folder;
			this.loops = loops;
			this.maxSeconds = maxSeconds;
		}
	}

	private static final Logger LOG = LoggerFactory.getLogger(BattleMusic.class);
	private static final Random RANDOM = new Random();
	private static final float FADE_SECONDS = 1.5f;

	private static Track track;
	/** What follows a non-looping track when it ends (INTRO → BATTLE), or null. */
	private static Track next;
	private static MusicInstance playing;
	private static ResourceLocation lastFile;
	private static final List<MusicInstance> fading = new ArrayList<>();
	/** Listening to one file from the music screen; what was playing comes back afterwards. */
	private static boolean previewing;
	private static Track resumeTrack;
	private static Track resumeNext;

	private static ShowtimeConfig.Kind kind = ShowtimeConfig.Kind.GHOST;

	private BattleMusic() {
	}

	/** The track playing (looping or not), or null. */
	public static Track track() {
		return previewing ? null : track;
	}

	/** The file playing for {@link #track()}, or null. */
	public static ResourceLocation current() {
		return track() == null || playing == null || playing.isStopped() ? null : playing.file;
	}

	/**
	 * In-battle player: plays this file of the current track now (instead of a random one); the track goes on as
	 * usual afterwards (a loop picks the next one at random).
	 */
	public static void choose(ResourceLocation file) {
		if (track() == null || file == null) {
			return;
		}
		fadeOutCurrent();
		lastFile = file;
		playing = new MusicInstance(file, track.maxSeconds);
		Minecraft.getInstance().getSoundManager().play(playing);
	}

	/** Plays {@code wanted} (fading the current music out), unless it is already playing. */
	public static void play(Track wanted) {
		play(wanted, null);
	}

	/**
	 * Whose battle the music is for (the player's per-kind setting, {@link ShowtimeConfig}): Showtime's staging of
	 * Cobblemon battles sets PvP / NPC while one runs; anything else (Phantasmon) is a Ghost battle.
	 */
	public static void setKind(ShowtimeConfig.Kind battleKind) {
		kind = battleKind == null ? ShowtimeConfig.Kind.GHOST : battleKind;
	}

	/** Whether the player wants our music for the current kind of battle. */
	public static boolean wanted() {
		return ShowtimeConfig.battle(kind).music;
	}

	/** Plays {@code wanted}, then {@code then} when it ends (if {@code wanted} has no file, {@code then} right away). */
	public static void play(Track wanted, Track then) {
		cancelPreview();
		if (!wanted()) {
			stop(); // music off for this kind of battle (settings)
			return;
		}
		if (wanted == track && playing != null && !playing.isStopped()) {
			return;
		}
		fadeOutCurrent();
		track = wanted;
		next = then;
		if (!startRandom(wanted)) {
			track = then;
			next = null;
			if (then == null || !startRandom(then)) {
				track = null; // nothing in the pack for this: silence, and Minecraft's music may come back
			}
		}
	}

	/** Fades the music out (end of the lobby, of the battle...). */
	public static void stop() {
		cancelPreview();
		fadeOutCurrent();
		track = null;
		next = null;
	}

	/** Whether battle music is on: Minecraft's background music waits meanwhile. */
	public static boolean active() {
		return track != null || previewing || !fading.isEmpty();
	}

	/** Whether a looping track (lobby, battle) or the intro is playing — the skip key acts then. */
	public static boolean skippable() {
		return track != null && !previewing;
	}

	/**
	 * Skip key (Adrien 2026-10-05): another file of the same folder right away (never the same one when there's a
	 * choice); the intro hands over to the battle music. The new title shows in the action bar.
	 */
	public static void skip() {
		if (!skippable()) {
			return;
		}
		fadeOutCurrent();
		if (!track.loops && next != null) {
			track = next;
			next = null;
		}
		if (!startRandom(track)) {
			track = null;
			return;
		}
		var player = Minecraft.getInstance().player;
		if (player != null && lastFile != null) {
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable("cobblemon_battle_showtime.music.now_playing", title(lastFile))
					.withStyle(net.minecraft.ChatFormatting.AQUA), true);
		}
	}

	/** Music screen: listens to one file; {@link #endPreview} brings back what was playing. */
	public static void preview(ResourceLocation file) {
		if (!previewing) {
			resumeTrack = track;
			resumeNext = next;
		}
		previewing = true;
		fadeOutCurrent();
		track = null;
		next = null;
		playing = new MusicInstance(file, 0);
		Minecraft.getInstance().getSoundManager().play(playing);
	}

	public static boolean previewing(ResourceLocation file) {
		return previewing && playing != null && !playing.isStopped() && playing.file.equals(file);
	}

	/** The game moved on (battle started, lobby closed...) while a file was being listened to: the preview just ends. */
	private static void cancelPreview() {
		if (previewing) {
			previewing = false;
			resumeTrack = null;
			resumeNext = null;
			fadeOutCurrent();
		}
	}

	public static void endPreview() {
		if (!previewing) {
			return;
		}
		previewing = false;
		fadeOutCurrent();
		Track resume = resumeTrack;
		Track then = resumeNext;
		resumeTrack = null;
		resumeNext = null;
		if (resume != null) {
			play(resume, then);
		}
	}

	/** Whether a resource pack gives at least one ticked file for this track. */
	public static boolean available(Track wanted) {
		return files(wanted).stream().anyMatch(MusicSettings::enabled);
	}

	/** Every file of a folder, ticked or not (the music screen). */
	public static List<ResourceLocation> allFiles(Track wanted) {
		return files(wanted);
	}

	/** A file's name for display: {@code cobblemon_battle_showtime:music/battle/rival_theme} → {@code rival_theme}. */
	public static String title(ResourceLocation file) {
		String path = file.getPath();
		return path.substring(path.lastIndexOf('/') + 1);
	}

	/** Every client tick: chains tracks (loops pick another file, INTRO hands over to BATTLE). */
	public static void tick() {
		fading.removeIf(MusicInstance::isStopped);
		if (track == null || playing == null) {
			return;
		}
		SoundManager sounds = Minecraft.getInstance().getSoundManager();
		if (playing.isStopped() || !sounds.isActive(playing)) {
			if (playing.startedAt > 0 && System.currentTimeMillis() - playing.startedAt < 1000) {
				return; // just queued: the engine starts it on its own tick
			}
			if (track.loops) {
				if (!startRandom(track)) {
					track = null; // files removed meanwhile (resource packs reloaded)
				}
			} else if (next != null) {
				track = next;
				next = null;
				if (!startRandom(track)) {
					track = null;
				}
			} else {
				track = null;
				playing = null;
			}
		}
	}

	private static void fadeOutCurrent() {
		if (playing != null && !playing.isStopped()) {
			playing.fadeOut();
			fading.add(playing);
		}
		playing = null;
	}

	/** Picks a file of the track's folder (not the one just played, when there's a choice) and starts it. */
	private static boolean startRandom(Track wanted) {
		List<ResourceLocation> files = new ArrayList<>(files(wanted));
		files.removeIf(file -> !MusicSettings.enabled(file)); // unticked in the music screen
		if (files.isEmpty()) {
			playing = null;
			return false;
		}
		ResourceLocation file = files.get(RANDOM.nextInt(files.size()));
		if (files.size() > 1 && file.equals(lastFile)) {
			file = files.get((files.indexOf(file) + 1) % files.size());
		}
		lastFile = file;
		playing = new MusicInstance(file, wanted.maxSeconds);
		Minecraft.getInstance().getSoundManager().play(playing);
		LOG.debug("Battle music {}: {}", wanted, file);
		return true;
	}

	/** Namespaces whose {@code sounds/music/} count: Showtime's own, its former name's and Phantasmon's, for packs made before the rename and the split. */
	private static final java.util.Set<String> NAMESPACES = java.util.Set.of("cobblemon_battle_showtime", "cobblemon_spectacle", "phantasmon");

	/** The track's files, as sound locations ({@code cobblemon_battle_showtime:music/battle/foo} for {@code sounds/music/battle/foo.ogg}). */
	private static List<ResourceLocation> files(Track wanted) {
		List<ResourceLocation> files = new ArrayList<>();
		String root = "sounds/music/" + wanted.folder;
		Minecraft.getInstance().getResourceManager()
				.listResources(root, location -> location.getPath().endsWith(".ogg"))
				.keySet().stream()
				.filter(location -> NAMESPACES.contains(location.getNamespace()))
				.sorted()
				.forEach(location -> {
					String path = location.getPath();
					files.add(ResourceLocation.fromNamespaceAndPath(location.getNamespace(),
							path.substring("sounds/".length(), path.length() - ".ogg".length())));
				});
		return files;
	}

	/** One music file played as is (streamed, not positional), with a fade-out. */
	private static final class MusicInstance extends AbstractTickableSoundInstance {
		private final ResourceLocation file;
		private final long startedAt = System.currentTimeMillis();
		/** Ticks after which it fades out on its own (so the fade ends at the cap), 0 = never. */
		private final int fadeAtTick;
		private int ticks;
		private boolean fadingOut;
		/** 1 until the fade-out; the volume is this times the player's music volume ({@link ShowtimeConfig}). */
		private float fade = 1f;

		MusicInstance(ResourceLocation file, int maxSeconds) {
			super(SoundEvent.createVariableRangeEvent(file), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
			this.file = file;
			this.looping = false;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.volume = Math.max(0.001f, ShowtimeConfig.musicVolume());
			this.fadeAtTick = maxSeconds <= 0 ? 0 : Math.max(1, Math.round((maxSeconds - FADE_SECONDS) * 20));
		}

		/** Not declared in any sounds.json: the file itself is the sound. */
		@Override
		public WeighedSoundEvents resolve(SoundManager manager) {
			this.sound = new Sound(file, ConstantFloat.of(1f), ConstantFloat.of(1f), 1, Sound.Type.FILE, true, false, 16);
			return new WeighedSoundEvents(file, null);
		}

		void fadeOut() {
			fadingOut = true;
		}

		@Override
		public void tick() {
			ticks++;
			if (fadeAtTick > 0 && ticks >= fadeAtTick) {
				fadingOut = true;
			}
			if (fadingOut) {
				fade -= 1f / (FADE_SECONDS * 20f);
				if (fade <= 0f) {
					fade = 0f;
					stop();
				}
			}
			// Never quite 0: Minecraft drops a sound started at volume 0, and the loop would restart it every second.
			volume = Math.max(0.001f, fade * ShowtimeConfig.musicVolume());
		}
	}
}
