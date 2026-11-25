package com.mystaria.showtime.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;

/**
 * What another mod (Phantasmon) plugs into Showtime, set once at its startup. The rest of the public surface is the
 * public static methods of {@code BattleCinematic}, {@code BattleCameraDirector} and {@code BattleShowtime}.
 * Client thread only.
 */
public final class ShowtimeHooks {

	private static BiFunction<ClientLevel, UUID, Player> playerResolver = (level, uuid) -> null;
	private static IntPredicate glowKeeper = entityId -> false;
	private static BooleanSupplier foreignPacketCheck = () -> false;
	private static java.util.function.Function<net.minecraft.client.gui.screens.Screen, net.minecraft.client.gui.screens.Screen> musicScreen =
			com.mystaria.showtime.gui.MusicScreen::new;
	private static final List<BattleButton> buttons = new ArrayList<>();
	private static java.util.function.BiFunction<UUID, String, String> trainerProfiles = (uuid, moment) -> null;

	private ShowtimeHooks() {
	}

	/**
	 * Who stands for a player that this world doesn't have (the opponent of the intro, the trainer of a battle):
	 * Phantasmon gives its Hub avatars. Asked only when the world has no such player.
	 */
	public static void setPlayerResolver(BiFunction<ClientLevel, UUID, Player> resolver) {
		playerResolver = resolver;
	}

	/** The player {@code uuid} of this world, else the resolver's stand-in, else null. */
	public static Player player(ClientLevel level, UUID uuid) {
		if (level == null || uuid == null) {
			return null;
		}
		Player player = level.getPlayerByUUID(uuid);
		return player != null ? player : playerResolver.apply(level, uuid);
	}

	/**
	 * Entities whose Tera glow survives the end of the filmed battle: Phantasmon's battles seen from afar keep
	 * theirs until their own end.
	 */
	public static void setGlowKeeper(IntPredicate keeper) {
		glowKeeper = keeper;
	}

	public static boolean keepsGlow(int entityId) {
		return glowKeeper.test(entityId);
	}

	/**
	 * Whether the battle packet Cobblemon's client handlers are running right now was handed to them by another mod
	 * for a battle it stages itself (Phantasmon's Ghost battles): Showtime's own staging of Cobblemon battles leaves
	 * it alone.
	 */
	public static void setForeignPacketCheck(BooleanSupplier check) {
		foreignPacketCheck = check;
	}

	public static boolean handlingForeignPacket() {
		return foreignPacketCheck.getAsBoolean();
	}

	/**
	 * The music menu opened from Showtime's menu (key N): Showtime's own by default; Phantasmon gives
	 * its own (same settings and files, Phantasmon's look).
	 */
	public static void setMusicScreen(java.util.function.Function<net.minecraft.client.gui.screens.Screen, net.minecraft.client.gui.screens.Screen> factory) {
		musicScreen = factory;
	}

	public static net.minecraft.client.gui.screens.Screen musicScreen(net.minecraft.client.gui.screens.Screen parent) {
		return musicScreen.apply(parent);
	}

	private static java.util.function.Supplier<BattlePanelStyle> panelStyle = () -> BattlePanelStyle.SHOWTIME;

	/**
	 * The colours of the panel over Cobblemon's battle screen, asked each frame (2026-11-25): Phantasmon gives the
	 * look chosen in its menu. A null answer means Showtime's own.
	 */
	public static void setPanelStyle(java.util.function.Supplier<BattlePanelStyle> style) {
		panelStyle = style;
	}

	public static BattlePanelStyle panelStyle() {
		BattlePanelStyle style = panelStyle.get();
		return style == null ? BattlePanelStyle.SHOWTIME : style;
	}

	/**
	 * Other trainers' animation profiles (phase E3): Phantasmon gives each player's choice for a moment id
	 * ({@code guard}, {@code send_out}...), or null for the default. The local player's own comes from Showtime's
	 * settings.
	 */
	public static void setTrainerProfiles(java.util.function.BiFunction<UUID, String, String> profiles) {
		trainerProfiles = profiles;
	}

	public static String trainerAnimation(UUID trainer, String moment) {
		return trainerProfiles.apply(trainer, moment);
	}

	private static java.util.function.Consumer<java.util.Map<String, String>> localProfileListener = profile -> { };
	private static java.util.function.Consumer<java.util.Collection<UUID>> trainersListener = trainers -> { };

	/** Told the local player's profile (moment id → animation) at startup and whenever they change it: Phantasmon sends it. */
	public static void setLocalProfileListener(java.util.function.Consumer<java.util.Map<String, String>> listener) {
		localProfileListener = listener;
	}

	/** The local player's animation profile, moment id → animation (moments left on the default are absent). */
	public static java.util.Map<String, String> localTrainerProfile() {
		return com.mystaria.showtime.config.ShowtimeConfig.trainerProfile();
	}

	public static void localProfileChanged() {
		localProfileListener.accept(localTrainerProfile());
	}

	/** Told the player trainers of a battle as it starts, so their profiles can be fetched before they act. */
	public static void setTrainersListener(java.util.function.Consumer<java.util.Collection<UUID>> listener) {
		trainersListener = listener;
	}

	public static void trainersStarting(java.util.Collection<UUID> trainers) {
		trainersListener.accept(trainers);
	}

	private static java.util.function.Supplier<String> ambianceOverride = () -> null;

	/**
	 * The battle ambiance imposed on the battle starting (phase A1), over the player's own choice: Phantasmon's lobby
	 * (one ambiance for both players), Phantasmon Events' scripted matches. Null: the player's choice.
	 */
	public static void setAmbianceOverride(java.util.function.Supplier<String> override) {
		ambianceOverride = override;
	}

	/**
	 * (1.2.0, 2026-11-18) Plays one of the battle ambiance's {@code named} moments now, if it has one by that name
	 * (Phantasmon Events: {@code cave_rumble}, {@code cave_collapse}). Client thread.
	 */
	public static void ambianceMoment(String name) {
		com.mystaria.showtime.ambiance.BattleAmbiance.moment(name);
	}

	private static final java.util.List<java.util.function.Supplier<String>> AMBIANCE_LAYERS = new java.util.concurrent.CopyOnWriteArrayList<>();

	/**
	 * (1.2.0, 2026-11-18) An ambiance imposed over {@link #setAmbianceOverride}'s while it answers non-null, read again
	 * during the battle (twice a second): the ambiance changes when the answer does, and the one below comes back when
	 * it answers null. Phantasmon Events: the ambiance of the Pokémon Reborn field in play. First added, first asked.
	 */
	public static void addAmbianceOverride(java.util.function.Supplier<String> layer) {
		AMBIANCE_LAYERS.add(layer);
	}

	/** The ambiance {@link #setAmbianceOverride} imposes, under the layers (the battle's own: a lobby's choice). */
	public static String baseAmbianceOverride() {
		return ambianceOverride.get();
	}

	private static final java.util.List<java.util.function.Supplier<String>> STADIUM_LAYERS = new java.util.concurrent.CopyOnWriteArrayList<>();

	/**
	 * (1.3.0, 2026-11-19) A stadium imposed while it answers non-null (an id, or "none": the world as it is), read again
	 * during the battle like {@link #addAmbianceOverride}. Without one, an imposed ambiance that names a preset brings
	 * its stadium; one that names a plain ambiance keeps the player's own stadium. First added, first asked.
	 */
	public static void addStadiumOverride(java.util.function.Supplier<String> layer) {
		STADIUM_LAYERS.add(layer);
	}

	private static java.util.function.Supplier<String> fieldStadium = () -> null;

	/**
	 * (1.3.0, 2026-11-19) The stadium the battle field in play suggests, read again during the battle (null: none). A
	 * scene whose stadium is {@code "field"} ({@code Ambiances.FIELD_STADIUM}) shows it, and the world as it is while
	 * the field suggests none. Phantasmon Events: the Pokémon Reborn cave fields give the Cave stadium.
	 */
	public static void setFieldStadium(java.util.function.Supplier<String> stadium) {
		fieldStadium = stadium == null ? () -> null : stadium;
	}

	public static String fieldStadium() {
		return fieldStadium.get();
	}

	public static String stadiumOverride() {
		for (java.util.function.Supplier<String> layer : STADIUM_LAYERS) {
			String id = layer.get();
			if (id != null) {
				return id;
			}
		}
		return null;
	}

	public static String ambianceOverride() {
		for (java.util.function.Supplier<String> layer : AMBIANCE_LAYERS) {
			String id = layer.get();
			if (id != null) {
				return id;
			}
		}
		return ambianceOverride.get();
	}

	private static final java.util.List<java.util.function.Supplier<String>> DISTANT_LAYERS = new java.util.concurrent.CopyOnWriteArrayList<>();
	private static java.util.function.Supplier<String> distantFieldStadium = () -> null;

	/**
	 * (1.8, 2026-11-22) Like {@link #addAmbianceOverride}, for a battle seen from afar (not this player's own, not
	 * watched): an ambiance imposed over the one that battle came with while it answers non-null, read again twice a
	 * second. Phantasmon Events: the ambiance of the Pokémon Reborn field in play there, for those standing around.
	 */
	public static void addDistantAmbianceOverride(java.util.function.Supplier<String> layer) {
		DISTANT_LAYERS.add(layer);
	}

	public static String distantAmbianceOverride() {
		for (java.util.function.Supplier<String> layer : DISTANT_LAYERS) {
			String id = layer.get();
			if (id != null) {
				return id;
			}
		}
		return null;
	}

	/** (1.8, 2026-11-22) Like {@link #setFieldStadium}, for a battle seen from afar. */
	public static void setDistantFieldStadium(java.util.function.Supplier<String> stadium) {
		distantFieldStadium = stadium == null ? () -> null : stadium;
	}

	public static String distantFieldStadium() {
		return distantFieldStadium.get();
	}

	private static java.util.function.Supplier<AmbianceArea> areaAmbiance = () -> null;

	/**
	 * (1.4, 2026-11-19) The ambiance of the place the local player stands in ({@link AmbianceArea}), asked twice a
	 * second outside battles; null: none here. Phantasmon Events: the ambiance of the hub the player is in.
	 */
	private static Boolean biomeScenes;

	/**
	 * (1.5.0, 2026-11-19) "Scene by biome" imposed: {@code true} on, {@code false} off, whatever the player ticked;
	 * {@code null} gives the choice back to the player (the default). While imposed, the menu shows the box greyed with
	 * a note. Phantasmon Events: off in a tournament, for instance.
	 */
	public static void setBiomeScenes(Boolean imposed) {
		biomeScenes = imposed;
	}

	/** What a mod imposes: true, false, or null (the player's choice). */
	public static Boolean biomeScenesImposed() {
		return biomeScenes;
	}

	/** Whether the player's own scene follows the biome now: imposed, else their box. */
	public static boolean biomeScenesOn() {
		Boolean imposed = biomeScenes;
		return imposed != null ? imposed : com.mystaria.showtime.config.ShowtimeConfig.biomeScene();
	}

	public static void setAreaAmbiance(java.util.function.Supplier<AmbianceArea> area) {
		areaAmbiance = area == null ? () -> null : area;
	}

	public static AmbianceArea areaAmbiance() {
		try {
			return areaAmbiance.get();
		} catch (RuntimeException ex) {
			return null;
		}
	}

	private static boolean lobbyMusic;

	/**
	 * For a mod with a battle lobby (Phantasmon): turns on the {@code lobby} music folder, which Showtime alone never
	 * plays. Its tab then shows in the music menus; play it with {@code BattleMusic.play(BattleMusic.Track.LOBBY)}.
	 */
	public static void enableLobbyMusic() {
		lobbyMusic = true;
	}

	public static boolean lobbyMusicEnabled() {
		return lobbyMusic;
	}

	// ---- Intros of other mods, reserved content (1.2.0) ----

	/** Kinds of content {@link #setUnlocks} is asked about. */
	public static final String INTRO = "intro";
	public static final String AMBIANCE = "ambiance";

	private static final java.util.Map<String, CustomIntro> intros = new java.util.LinkedHashMap<>();
	private static java.util.function.BiPredicate<String, String> unlocks = (kind, id) -> false;

	/** Adds an intro of another mod, after Showtime's own; one registered again under the same id replaces it. */
	public static void registerIntro(CustomIntro intro) {
		intros.put(intro.id(), intro);
	}

	/** The intros other mods registered, in order. */
	public static java.util.Collection<CustomIntro> customIntros() {
		return java.util.Collections.unmodifiableCollection(intros.values());
	}

	/** A registered intro by id, or null. */
	public static CustomIntro customIntro(String id) {
		return id == null ? null : intros.get(id);
	}

	/**
	 * Which reserved content the local player may use: asked with {@link #INTRO} and an intro id, or {@link #AMBIANCE}
	 * and an ambiance id. Reserved content (a {@link CustomIntro#reserved()} intro, an ambiance whose file says
	 * {@code "reserved": true}) is otherwise hidden from the menus and never picked. Phantasmon answers from the
	 * player's rewards. Content imposed on a battle by the driving mod plays whatever this says.
	 */
	public static void setUnlocks(java.util.function.BiPredicate<String, String> unlocked) {
		unlocks = unlocked;
	}

	public static boolean unlocked(String kind, String id) {
		return unlocks.test(kind, id);
	}

	public static void addButton(BattleButton button) {
		buttons.add(button);
		buttons.sort(Comparator.comparingInt(BattleButton::order));
	}

	public static List<BattleButton> buttons() {
		return buttons;
	}
}
