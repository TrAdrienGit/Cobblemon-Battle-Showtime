package com.mystaria.showtime.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;

/**
 * The player's settings (phase S3, Adrien 2026-10-29), in {@code config/cobblemon_battle_showtime.json}: per kind of battle,
 * whether the camera starts staged, whether the intro plays and whether our music plays; and the music's own volume,
 * on top of Minecraft's Music slider. Edited from the settings screen (Mod Menu) and the in-battle player. A missing
 * or broken file means the defaults: everything on, full volume. Also the trainer's animation profile (phase E2).
 */
public final class ShowtimeConfig {

	private static final Logger LOG = LoggerFactory.getLogger(ShowtimeConfig.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** The kinds of battle Showtime stages. */
	public enum Kind {
		/** A battle another mod stages through the API (Phantasmon's Ghost battles). */
		GHOST,
		PVP,
		NPC
	}

	/** One kind of battle's settings. */
	public static final class Battle {
		public boolean stagedCamera = true;
		public boolean intro = true;
		public boolean music = true;
	}

	/** The file's content. */
	private static final class Stored {
		Battle ghost = new Battle();
		Battle pvp = new Battle();
		Battle npc = new Battle();
		float musicVolume = 1f;
		/** The trainer's animation profile (phase E2): moment id → animation name; missing = the default one. */
		Map<String, String> trainerProfile = new HashMap<>();
		/** The battle ambiance chosen (phase A1), an id {@code namespace:name}, or "none". */
		String ambiance = "none";
	}

	private static Stored stored;

	private ShowtimeConfig() {
	}

	public static Battle battle(Kind kind) {
		Stored s = get();
		return switch (kind) {
			case GHOST -> s.ghost;
			case PVP -> s.pvp;
			case NPC -> s.npc;
		};
	}

	/** Our music's volume, 0 to 1, multiplied with Minecraft's Music slider. */
	public static float musicVolume() {
		return get().musicVolume;
	}

	public static void setMusicVolume(float volume) {
		get().musicVolume = Mth.clamp(volume, 0f, 1f);
	}

	/** The animation chosen for {@code moment} ({@code guard}, {@code send_out}...), or null for the default. */
	public static String trainerAnimation(String moment) {
		return get().trainerProfile.get(moment);
	}

	public static void setTrainerAnimation(String moment, String animation) {
		get().trainerProfile.put(moment, animation);
	}

	public static String ambiance() {
		String id = get().ambiance;
		return id == null ? "none" : id;
	}

	public static void setAmbiance(String id) {
		get().ambiance = id;
	}

	/** A copy of the whole profile. */
	public static Map<String, String> trainerProfile() {
		return Map.copyOf(get().trainerProfile);
	}

	private static Stored get() {
		if (stored == null) {
			stored = load();
		}
		return stored;
	}

	private static Stored load() {
		LegacySettings.migrate("");
		Path path = path();
		if (Files.exists(path)) {
			try {
				Stored read = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Stored.class);
				if (read != null) {
					read.ghost = read.ghost == null ? new Battle() : read.ghost;
					read.pvp = read.pvp == null ? new Battle() : read.pvp;
					read.npc = read.npc == null ? new Battle() : read.npc;
					read.musicVolume = Mth.clamp(read.musicVolume, 0f, 1f);
					read.trainerProfile = read.trainerProfile == null ? new HashMap<>() : read.trainerProfile;
					return read;
				}
			} catch (IOException | RuntimeException ex) {
				LOG.warn("Cannot read {}: defaults used", path, ex);
			}
		}
		return new Stored();
	}

	public static void save() {
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(get()), StandardCharsets.UTF_8);
		} catch (IOException ex) {
			LOG.warn("Cannot save {}", path, ex);
		}
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("cobblemon_battle_showtime.json");
	}
}
