package com.mystaria.showtime.classic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.client.CobblemonClient;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import com.mystaria.showtime.config.ShowtimeConfig;

/**
 * Living with LeagueWay (2026-11-19), the Cobblemon Academy server's client mod, whose source is not public: whenever
 * Cobblemon's battle screen opens against a named opponent, it takes the screen for its own battle intro and starts its
 * own battle music, then gives the battle screen back. With Showtime's intro that made two intros and two musics.
 *
 * <p>One intro plays, the player's pick ({@link ShowtimeConfig#leagueWayIntro}):
 * <ul>
 * <li>Showtime's (the default): LeagueWay skips its intro for a battle it already introduced, the one held in
 * {@code LeagueBattleStartScreen.lastBattleObject}. We put the current battle there just before the battle screen opens
 * ({@code MinecraftLeagueWayMixin}), so it lets the screen through, and its music, asked for by that intro, never
 * starts.</li>
 * <li>LeagueWay's: Showtime plays no intro of its own, and starts its battle music only when LeagueWay's has not
 * (see {@link ClassicBattles#tick}).</li>
 * </ul>
 *
 * <p>Everything goes through reflection and only when LeagueWay is loaded; if a later LeagueWay renames what we use,
 * nothing breaks: both intros simply play again.
 */
public final class LeagueWayCompat {

	private static final Logger LOG = LoggerFactory.getLogger(LeagueWayCompat.class);
	private static final String INTRO_SCREEN = "net.leagueway.client.gui.LeagueBattleStartScreen";
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("leagueway");

	private static boolean looked;
	private static Field lastBattle;
	private static Method activeSound;

	private LeagueWayCompat() {
	}

	/** Whether LeagueWay is installed. */
	public static boolean loaded() {
		return LOADED;
	}

	/** Whether Showtime's own intro plays: always without LeagueWay, else unless the player picked LeagueWay's. */
	public static boolean showtimeIntro() {
		return !LOADED || !ShowtimeConfig.leagueWayIntro();
	}

	/** Whether LeagueWay's intro plays instead of Showtime's. */
	public static boolean leagueWayIntro() {
		return LOADED && ShowtimeConfig.leagueWayIntro();
	}

	/** Cobblemon's battle screen is about to open: with Showtime's intro picked, tell LeagueWay to skip its own. */
	public static void beforeBattleScreen() {
		if (!showtimeIntro() || !LOADED || !look() || lastBattle == null) {
			return;
		}
		Object battle = CobblemonClient.INSTANCE.getBattle();
		if (battle == null) {
			return;
		}
		try {
			lastBattle.set(null, battle);
		} catch (ReflectiveOperationException | RuntimeException ex) {
			LOG.warn("Could not skip LeagueWay's battle intro", ex);
			lastBattle = null;
		}
	}

	/** Whether LeagueWay's intro screen is up. */
	public static boolean introOpen() {
		Minecraft mc = Minecraft.getInstance();
		return LOADED && mc.screen != null && INTRO_SCREEN.equals(mc.screen.getClass().getName());
	}

	/** Whether LeagueWay's battle music plays (false when it cannot be told). */
	public static boolean musicPlaying() {
		if (!LOADED || !look() || activeSound == null) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(activeSound.invoke(null));
		} catch (ReflectiveOperationException | RuntimeException ex) {
			activeSound = null;
			return false;
		}
	}

	private static boolean look() {
		if (!looked) {
			looked = true;
			try {
				Class<?> screen = Class.forName(INTRO_SCREEN);
				lastBattle = screen.getField("lastBattleObject");
				activeSound = screen.getMethod("hasActiveSound");
			} catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
				LOG.warn("LeagueWay is loaded but its battle intro could not be found: both intros may play", ex);
			}
		}
		return true;
	}
}
