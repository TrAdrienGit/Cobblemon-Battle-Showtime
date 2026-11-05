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

	public static String ambianceOverride() {
		return ambianceOverride.get();
	}

	public static void addButton(BattleButton button) {
		buttons.add(button);
		buttons.sort(Comparator.comparingInt(BattleButton::order));
	}

	public static List<BattleButton> buttons() {
		return buttons;
	}
}
