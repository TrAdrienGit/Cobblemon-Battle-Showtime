package com.mystaria.showtime.audio;

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
import net.minecraft.resources.ResourceLocation;

/**
 * The player's choices among the resource pack's music files (2026-10-05: everyone keeps the tracks they like):
 * unticked files, favourites, and "only play my favourites" (2026-11-09). Stored per player in
 * {@code config/cobblemon_battle_showtime-music.json} (read from Phantasmon's former {@code config/phantasmon-music.json}
 * until first saved); a file not listed is on, so new files dropped in the pack play straight away.
 */
public final class MusicSettings {

	private static final Logger LOG = LoggerFactory.getLogger(MusicSettings.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static Stored stored;

	/** The file's content; fields missing in older files read as empty / off. */
	private static final class Stored {
		Set<String> disabled = new TreeSet<>();
		Set<String> favorites = new TreeSet<>();
		boolean onlyFavorites;
	}

	private MusicSettings() {
	}

	public static boolean enabled(ResourceLocation file) {
		return !get().disabled.contains(file.toString());
	}

	public static void setEnabled(ResourceLocation file, boolean enabled) {
		setEnabled(java.util.List.of(file), enabled);
	}

	/** Ticks or unticks every file given (the menu's "Enable all" / "Disable all" on the tracks shown). */
	public static void setEnabled(Collection<ResourceLocation> files, boolean enabled) {
		for (ResourceLocation file : files) {
			if (enabled) {
				get().disabled.remove(file.toString());
			} else {
				get().disabled.add(file.toString());
			}
		}
		save();
	}

	public static boolean favorite(ResourceLocation file) {
		return get().favorites.contains(file.toString());
	}

	public static void setFavorite(ResourceLocation file, boolean favorite) {
		if (favorite) {
			get().favorites.add(file.toString());
		} else {
			get().favorites.remove(file.toString());
		}
		save();
	}

	/** Whether only favourite tracks are picked (in a folder holding at least one ticked favourite). */
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
			com.mystaria.showtime.config.LegacySettings.migrate("-music");
			Path path = Files.exists(path()) ? path() : FabricLoader.getInstance().getConfigDir().resolve("phantasmon-music.json");
			if (Files.exists(path)) {
				try {
					Stored read = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Stored.class);
					if (read != null) {
						stored.disabled.addAll(read.disabled == null ? Set.of() : read.disabled);
						stored.favorites.addAll(read.favorites == null ? Set.of() : read.favorites);
						stored.onlyFavorites = read.onlyFavorites;
					}
				} catch (IOException | RuntimeException ex) {
					LOG.warn("Cannot read {}: all music files on", path, ex);
				}
			}
		}
		return stored;
	}

	private static void save() {
		Path path = path();
		try {
			com.mystaria.showtime.config.ShowtimeConfig.write(path, GSON.toJson(stored));
		} catch (IOException ex) {
			LOG.warn("Cannot save {}", path, ex);
		}
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("cobblemon_battle_showtime-music.json");
	}
}
