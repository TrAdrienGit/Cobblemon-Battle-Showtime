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
 * Battle music from a resource pack (2026-10-05; part of Cobblemon Battle Showtime since 2026-10-29, D-37) — no
 * JSON to write: every {@code .ogg} found in {@code assets/cobblemon_battle_showtime/sounds/music/<folder>/} of any active
 * resource pack is a candidate, one is picked at random (packs made for Phantasmon, under {@code assets/phantasmon/},
 * still count).
 * Folders: {@code battle} (loops from the intro to the end of the battle), {@code victory} / {@code defeat} (once at the end). An
 * empty or missing folder just means silence there. Played on the Music volume slider; Minecraft's own background
 * music is held while one plays ({@code MusicManagerMixin}). Client thread only.
 */
public final class BattleMusic {

	/**
	 * {@code LOBBY} only plays for a mod with a battle lobby, which turns it on ({@link
	 * com.mystaria.showtime.api.ShowtimeHooks#enableLobbyMusic}); it is hidden from the menus otherwise.
	 */
	public enum Track {
		// No intro track since 2026-11-09: the battle music starts with the intro, right after the lobby.
		LOBBY("lobby", true, 0), BATTLE("battle", true, 0),
		// End-of-battle jingles: 10 s at most, whatever the file's length (2026-10-05).
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
	/** What follows a non-looping track when it ends , or null. */
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

	/** The tracks shown in the music menus: every one, but the lobby only when a mod turned it on. */
	public static Track[] shownTracks() {
		return java.util.Arrays.stream(Track.values())
				.filter(each -> each != Track.LOBBY || com.mystaria.showtime.api.ShowtimeHooks.lobbyMusicEnabled())
				.toArray(Track[]::new);
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
			if (wanted.loops && available(wanted)) {
				// Every track of the folder unticked: silence on purpose. The track stays held, so neither Minecraft's
				// music nor Cobblemon's comes back; the next play() or stop() ends it.
				next = null;
				return;
			}
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

	/** Whether a track is playing — the skip key acts then. */
	public static boolean skippable() {
		return track != null && !previewing;
	}

	/**
	 * Skip key (2026-10-05): another file of the same folder right away (never the same one when there's a
	 * choice). The new title shows in the action bar.
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
			if (!(track.loops && available(track))) {
				track = null; // a folder all unticked keeps its silence held, as in play()
			}
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
	/**
	 * Whether the resource pack has this folder, ticked or not: then the pack decides, and Cobblemon's own music gives
	 * way even when the player unticked everything there (silence wanted, 2026-11-09).
	 */
	public static boolean available(Track wanted) {
		return !files(wanted).isEmpty();
	}

	/** Every file of a folder, ticked or not (the music screen). */
	public static List<ResourceLocation> allFiles(Track wanted) {
		return files(wanted);
	}

	/** A file's name for display: {@code cobblemon_battle_showtime:music/battle/rival_theme} → {@code rival_theme}. */
	public static String title(ResourceLocation file) {
		String path = file.getPath();
		String name = path.substring(path.lastIndexOf('/') + 1);
		if (name.endsWith(".ogg")) {
			name = name.substring(0, name.length() - 4);
		}
		// The resource pack's convention: <game>_<title>, "oras_vs_wally" → "ORAS · Vs Wally". Other names stay as is.
		int split = name.indexOf('_');
		String game = split > 0 ? GAMES.get(name.substring(0, split)) : null;
		if (game == null) {
			return name;
		}
		StringBuilder title = new StringBuilder(game).append(" · ");
		for (String word : name.substring(split + 1).split("_")) {
			if (!word.isEmpty()) {
				title.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
			}
		}
		return title.toString().trim();
	}

	/** Source game acronyms of the resource pack's file names, as shown. */
	private static final java.util.Map<String, String> GAMES = java.util.Map.ofEntries(
			java.util.Map.entry("rb", "RB"), java.util.Map.entry("rby", "RBY"), java.util.Map.entry("gsc", "GSC"),
			java.util.Map.entry("rse", "RSE"), java.util.Map.entry("frlg", "FRLG"), java.util.Map.entry("dp", "DP"),
			java.util.Map.entry("pt", "Pt"), java.util.Map.entry("dppt", "DPPt"), java.util.Map.entry("hgss", "HGSS"),
			java.util.Map.entry("bw", "BW"), java.util.Map.entry("b2w2", "B2W2"), java.util.Map.entry("xy", "XY"),
			java.util.Map.entry("oras", "ORAS"), java.util.Map.entry("sm", "SM"), java.util.Map.entry("usum", "USUM"),
			java.util.Map.entry("lgpe", "LGPE"), java.util.Map.entry("swsh", "SwSh"), java.util.Map.entry("bdsp", "BDSP"),
			java.util.Map.entry("pla", "PLA"), java.util.Map.entry("plza", "PLZA"), java.util.Map.entry("sv", "SV"),
			java.util.Map.entry("colosseum", "Colosseum"), java.util.Map.entry("xd", "XD"),
			java.util.Map.entry("stadium", "Stadium"), java.util.Map.entry("stadium2", "Stadium 2"),
			java.util.Map.entry("pbr", "PBR"), java.util.Map.entry("pmdeos", "PMD Sky"),
			java.util.Map.entry("pokken", "Pokkén"), java.util.Map.entry("puzzleleague", "Puzzle League"),
			java.util.Map.entry("pinball", "Pinball"), java.util.Map.entry("pinballrs", "Pinball RS"),
			java.util.Map.entry("ranger", "Ranger"), java.util.Map.entry("champions", "Champions"),
			java.util.Map.entry("reborn", "Reborn"));

	/** The generation of each game code (1 to 9); spin-offs and fan games are absent: {@link #OTHER}. */
	private static final java.util.Map<String, Integer> GENERATIONS = java.util.Map.ofEntries(
			java.util.Map.entry("rb", 1), java.util.Map.entry("rby", 1), java.util.Map.entry("frlg", 3),
			java.util.Map.entry("lgpe", 7), java.util.Map.entry("stadium", 1), java.util.Map.entry("gsc", 2),
			java.util.Map.entry("hgss", 4), java.util.Map.entry("stadium2", 2), java.util.Map.entry("rse", 3),
			java.util.Map.entry("oras", 6), java.util.Map.entry("colosseum", 3), java.util.Map.entry("xd", 3),
			java.util.Map.entry("dp", 4), java.util.Map.entry("pt", 4), java.util.Map.entry("dppt", 4),
			java.util.Map.entry("bdsp", 8), java.util.Map.entry("pla", 8), java.util.Map.entry("pbr", 4),
			java.util.Map.entry("bw", 5), java.util.Map.entry("b2w2", 5), java.util.Map.entry("xy", 6),
			java.util.Map.entry("sm", 7), java.util.Map.entry("usum", 7), java.util.Map.entry("swsh", 8),
			java.util.Map.entry("sv", 9), java.util.Map.entry("plza", 9), java.util.Map.entry("champions", 9));

	/** Generation filter value for files outside the main series' generations. */
	public static final int OTHER = 10;

	/** The generation a file's game belongs to (1 to 9), or {@link #OTHER} (spin-offs, fan games, other names). */
	public static int generation(ResourceLocation file) {
		String path = file.getPath();
		String name = path.substring(path.lastIndexOf('/') + 1);
		int split = name.indexOf('_');
		Integer generation = split > 0 ? GENERATIONS.get(name.substring(0, split)) : null;
		return generation == null ? OTHER : generation;
	}

	/** Every client tick: chains tracks (loops pick another file, a one-shot hands over to what follows). */
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
		if (MusicSettings.onlyFavorites() && files.stream().anyMatch(MusicSettings::favorite)) {
			// Only the favourites, where this folder has some; a folder without any keeps all its ticked tracks.
			files.removeIf(file -> !MusicSettings.favorite(file));
		}
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

	/**
	 * Each track's files, listed once per resource reload: listing every pack's resources is costly, and the battle
	 * screen's player asked for them every frame (audit 2026-11-08).
	 */
	private static final java.util.Map<Track, List<ResourceLocation>> FILES = new java.util.EnumMap<>(Track.class);

	static {
		net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
				.registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
					@Override
					public ResourceLocation getFabricId() {
						return ResourceLocation.fromNamespaceAndPath("cobblemon_battle_showtime", "battle_music");
					}

					@Override
					public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
						FILES.clear();
					}
				});
	}

	/** The track's files, as sound locations ({@code cobblemon_battle_showtime:music/battle/foo} for {@code sounds/music/battle/foo.ogg}). */
	private static List<ResourceLocation> files(Track wanted) {
		return FILES.computeIfAbsent(wanted, BattleMusic::listFiles);
	}

	private static List<ResourceLocation> listFiles(Track wanted) {
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
		return List.copyOf(files);
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
