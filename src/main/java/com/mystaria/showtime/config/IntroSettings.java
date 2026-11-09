package com.mystaria.showtime.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * The player's choices among the battle intros (2026-11-09), as for the music: intros switched off, favourites, and
 * "only play my favourites". In {@code config/cobblemon_battle_showtime-intros.json}, by intro name; an intro not
 * listed is on, so new intros play straight away.
 */
public final class IntroSettings {

	private static final Logger LOG = LoggerFactory.getLogger(IntroSettings.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static Stored stored;

	private static final class Stored {
		Set<String> disabled = new TreeSet<>();
		Set<String> favorites = new TreeSet<>();
		boolean onlyFavorites;
	}

	private IntroSettings() {
	}

	public static boolean enabled(String intro) {
		return !get().disabled.contains(intro);
	}

	public static void setEnabled(Collection<String> intros, boolean enabled) {
		for (String intro : intros) {
			if (enabled) {
				get().disabled.remove(intro);
			} else {
				get().disabled.add(intro);
			}
		}
		save();
	}

	public static boolean favorite(String intro) {
		return get().favorites.contains(intro);
	}

	public static void setFavorite(String intro, boolean favorite) {
		if (favorite) {
			get().favorites.add(intro);
		} else {
			get().favorites.remove(intro);
		}
		save();
	}

	public static boolean onlyFavorites() {
		return get().onlyFavorites;
	}

	public static void setOnlyFavorites(boolean only) {
		get().onlyFavorites = only;
		save();
	}

	private static Stored get() {
		if (stored == null) {
			stored = new Stored();
			Path path = path();
			if (Files.exists(path)) {
				try {
					Stored read = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Stored.class);
					if (read != null) {
						stored.disabled.addAll(read.disabled == null ? Set.of() : read.disabled);
						stored.favorites.addAll(read.favorites == null ? Set.of() : read.favorites);
						stored.onlyFavorites = read.onlyFavorites;
					}
				} catch (IOException | RuntimeException ex) {
					LOG.warn("Cannot read {}: every intro on", path, ex);
				}
			}
		}
		return stored;
	}

	private static void save() {
		try {
			ShowtimeConfig.write(path(), GSON.toJson(stored));
		} catch (IOException ex) {
			LOG.warn("Cannot save {}", path(), ex);
		}
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("cobblemon_battle_showtime-intros.json");
	}
}
