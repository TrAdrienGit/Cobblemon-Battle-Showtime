package com.mystaria.showtime.ambiance;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * The ambiances, stadiums and presets known, read again on every resource reload from the loaded resource packs
 * (Showtime's own and any a pack adds). Ids are {@code <namespace>:<id>}.
 * <ul>
 * <li>{@code ambiances/<id>.json}: what is added on top of the place (sky, light, particles, sounds, props).</li>
 * <li>{@code stadiums/<id>.json} (2026-11-19): what replaces or hides the place ({@code hide_world}, a vault, an
 * island, a court and stands), in the same format.</li>
 * <li>{@code presets/<id>.json}: {@code {"stadium": ..., "ambiance": ...}}, a pair under one name. Cosmos, Ultra Space
 * and Champion's Stage are presets now, under their old ids, so whoever used them sees no change.</li>
 * </ul>
 * A stadium combined with an ambiance: the ambiance's file with the stadium's keys laid over it, the ambiance's own
 * stadium keys dropped first. With no stadium, the ambiance alone, as written (an older pack's ambiance that hides the
 * world itself still does).
 */
public final class Ambiances {

	private static final Logger LOG = LoggerFactory.getLogger(Ambiances.class);
	private static final Gson GSON = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
	/** The keys that make a place: an ambiance's are dropped under a stadium. */
	private static final Set<String> STADIUM_KEYS = Set.of("hide_world", "cavern", "island", "islands", "court", "crowd",
			"light_towers", "backdrop", "galaxy", "planets");
	public static final String NONE = "none";

	/** A stadium and an ambiance under one name. */
	public record Preset(String stadium, String ambiance) {
	}

	private static Map<String, JsonObject> ambianceFiles = Map.of();
	private static Map<String, JsonObject> stadiumFiles = Map.of();
	private static Map<String, Ambiance> loaded = Map.of();
	private static Map<String, Ambiance> stadiums = Map.of();
	private static Map<String, Preset> presets = Map.of();
	private static final Map<String, Ambiance> COMBINED = new HashMap<>();

	private Ambiances() {
	}

	public static void register() {
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return ResourceLocation.fromNamespaceAndPath("cobblemon_battle_showtime", "ambiances");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				Map<String, JsonObject> ambianceJson = read(manager, "ambiances");
				Map<String, JsonObject> stadiumJson = read(manager, "stadiums");
				Map<String, Ambiance> ambianceRead = new TreeMap<>();
				Map<String, Ambiance> stadiumRead = new TreeMap<>();
				parse(ambianceJson, ambianceRead);
				parse(stadiumJson, stadiumRead);
				Map<String, Preset> presetRead = new TreeMap<>();
				for (Map.Entry<String, JsonObject> entry : read(manager, "presets").entrySet()) {
					JsonObject json = entry.getValue();
					String namespace = entry.getKey().substring(0, entry.getKey().indexOf(':') + 1);
					String stadium = json.has("stadium") ? qualify(namespace, json.get("stadium").getAsString()) : null;
					String ambiance = json.has("ambiance") ? qualify(namespace, json.get("ambiance").getAsString()) : null;
					if (ambiance != null && ambianceRead.containsKey(ambiance)) {
						presetRead.put(entry.getKey(), new Preset(stadium, ambiance));
					} else {
						LOG.warn("Preset {} names an unknown ambiance {}", entry.getKey(), ambiance);
					}
				}
				ambianceFiles = ambianceJson;
				stadiumFiles = stadiumJson;
				loaded = ambianceRead;
				stadiums = stadiumRead;
				presets = presetRead;
				COMBINED.clear();
				LOG.info("{} battle ambiance(s), {} stadium(s), {} preset(s)", ambianceRead.size(), stadiumRead.size(),
						presetRead.size());
			}
		});
	}

	private static Map<String, JsonObject> read(ResourceManager manager, String folder) {
		Map<String, JsonObject> read = new TreeMap<>();
		for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources(folder,
				path -> path.getPath().endsWith(".json")).entrySet()) {
			ResourceLocation file = entry.getKey();
			String path = file.getPath();
			String id = file.getNamespace() + ":" + path.substring(folder.length() + 1, path.length() - ".json".length());
			try (Reader reader = entry.getValue().openAsReader()) {
				JsonElement json = JsonParser.parseReader(reader);
				if (json != null && json.isJsonObject()) {
					read.put(id, json.getAsJsonObject());
				}
			} catch (Exception ex) {
				LOG.warn("Unreadable {} file {}", folder, file, ex);
			}
		}
		return read;
	}

	private static void parse(Map<String, JsonObject> json, Map<String, Ambiance> into) {
		for (Map.Entry<String, JsonObject> entry : json.entrySet()) {
			try {
				Ambiance ambiance = GSON.fromJson(entry.getValue(), Ambiance.class);
				if (ambiance != null) {
					into.put(entry.getKey(), ambiance);
				}
			} catch (Exception ex) {
				LOG.warn("Unreadable ambiance or stadium {}", entry.getKey(), ex);
			}
		}
	}

	private static String qualify(String namespace, String id) {
		return id.contains(":") || NONE.equals(id) ? id : namespace + id;
	}

	/** The ambiance {@code id} as the player would see it chosen alone: a preset brings its stadium along. */
	public static Ambiance get(String id) {
		if (id == null) {
			return null;
		}
		Preset preset = presets.get(id);
		return preset != null ? resolve(preset.ambiance(), preset.stadium()) : loaded.get(id);
	}

	/**
	 * An ambiance in a stadium: {@code ambianceId} (a preset counts for its ambiance) with the stadium
	 * {@code stadiumId}'s place laid over it; null or "none": the ambiance alone. Built once, then kept until reload.
	 */
	public static Ambiance resolve(String ambianceId, String stadiumId) {
		if (ambianceId == null || ambianceId.isEmpty() || NONE.equals(ambianceId)) {
			// No ambiance: the stadium alone, if one is chosen (seen in the menu's preview too).
			return stadium(stadiumId);
		}
		Preset preset = presets.get(ambianceId);
		String ambiance = preset != null ? preset.ambiance() : ambianceId;
		JsonObject stadium = stadiumId == null || NONE.equals(stadiumId) ? null : stadiumFiles.get(stadiumId);
		if (stadium == null) {
			return loaded.get(ambiance);
		}
		JsonObject base = ambianceFiles.get(ambiance);
		if (base == null) {
			return null;
		}
		return COMBINED.computeIfAbsent(stadiumId + "|" + ambiance, key -> {
			JsonObject merged = base.deepCopy();
			STADIUM_KEYS.forEach(merged::remove);
			for (Map.Entry<String, JsonElement> entry : stadium.entrySet()) {
				merged.add(entry.getKey(), entry.getValue().deepCopy());
			}
			try {
				return GSON.fromJson(merged, Ambiance.class);
			} catch (Exception ex) {
				LOG.warn("Ambiance {} in stadium {} unreadable", ambiance, stadiumId, ex);
				return loaded.get(ambiance);
			}
		});
	}

	/**
	 * A scene imposed in one string (2026-11-19, so mods that pass an ambiance id along — a lobby, a backend, an
	 * NPC's file — can pass a stadium too): {@code <ambiance>} or {@code <ambiance>|<stadium>}; the ambiance may be empty
	 * (the player's own), the stadium "none" (the world as it is).
	 */
	public static final String SCENE_SEPARATOR = "|";
	/**
	 * A scene's stadium "field" (2026-11-19): the stadium the battle field in play suggests, as a mod tells it
	 * ({@code ShowtimeHooks.setFieldStadium}: Phantasmon Events' Pokémon Reborn fields, a cave field giving the Cave);
	 * none while the field suggests none. So a scene may impose a field's ambiance alone, or with its stadium.
	 */
	public static final String FIELD_STADIUM = "field";

	public static String scene(String ambiance, String stadium) {
		String a = ambiance == null ? "" : ambiance;
		return stadium == null || stadium.isEmpty() ? a : a + SCENE_SEPARATOR + stadium;
	}

	/** A scene's ambiance part: "" for the player's own. */
	public static String sceneAmbiance(String scene) {
		if (scene == null) {
			return "";
		}
		int bar = scene.indexOf(SCENE_SEPARATOR);
		return bar < 0 ? scene : scene.substring(0, bar);
	}

	/** A scene's stadium part, or null when it names none (then a preset's own, else the player's). */
	public static String sceneStadium(String scene) {
		if (scene == null) {
			return null;
		}
		int bar = scene.indexOf(SCENE_SEPARATOR);
		return bar < 0 || bar == scene.length() - 1 ? null : scene.substring(bar + 1);
	}

	/** A scene's name for the player: the preset's, or "stadium + ambiance", or one of them. */
	public static Component sceneName(String scene, Component own) {
		String ambiance = sceneAmbiance(scene);
		String stadium = sceneStadium(scene);
		Component a = ambiance.isEmpty() ? own : displayName(ambiance);
		if (stadium == null) {
			return a;
		}
		return Component.empty().append(stadiumName(stadium)).append(" + ").append(a);
	}

	/** A stadium alone (no ambiance), or null. */
	public static Ambiance stadium(String id) {
		return id == null ? null : stadiums.get(id);
	}

	/** The plain ambiances a player may choose: no preset, nothing of a place in them. */
	public static List<String> plainAmbianceIds() {
		return loaded.entrySet().stream().filter(entry -> usable(entry.getKey()) && !hasPlace(entry.getValue()))
				.map(Map.Entry::getKey).toList();
	}

	/** Whether an ambiance carries a place of its own (an older pack's that hides the world): listed with presets. */
	public static boolean hasPlace(Ambiance ambiance) {
		return ambiance.hideWorld || ambiance.cavern != null || ambiance.island != null || ambiance.islands != null
				|| ambiance.court != null || ambiance.crowd != null;
	}

	/** The presets a player may choose, then the ambiances that bring a place of their own. */
	public static List<String> scenePresetIds() {
		List<String> ids = new ArrayList<>(presets.keySet().stream().filter(Ambiances::usable).toList());
		loaded.forEach((id, ambiance) -> {
			if (!presets.containsKey(id) && hasPlace(ambiance) && usable(id)) {
				ids.add(id);
			}
		});
		return ids;
	}

	/** The stadium a preset brings, or null when {@code id} is no preset (or one without a stadium). */
	public static String presetStadium(String id) {
		Preset preset = id == null ? null : presets.get(id);
		return preset == null ? null : preset.stadium();
	}

	/** The ambiance a preset brings, or null when {@code id} is no preset. */
	public static String presetAmbiance(String id) {
		Preset preset = id == null ? null : presets.get(id);
		return preset == null ? null : preset.ambiance();
	}

	public static boolean isPreset(String id) {
		return id != null && presets.containsKey(id);
	}

	public static boolean isAmbiance(String id) {
		return id != null && loaded.containsKey(id);
	}

	public static boolean isStadium(String id) {
		return id != null && stadiums.containsKey(id);
	}

	/**
	 * An ambiance's (or preset's) name for the player: {@code <namespace>.ambiance.<name>} (Showtime's are translated),
	 * else the id's name made readable; {@code none} or "" → "None".
	 */
	public static Component displayName(String id) {
		return name("ambiance", id);
	}

	/** A stadium's name: {@code <namespace>.stadium.<name>}, else the id made readable; {@code none} → "None". */
	public static Component stadiumName(String id) {
		return name("stadium", id);
	}

	private static Component name(String kind, String id) {
		if (id == null || id.isEmpty() || NONE.equals(id)) {
			return Component.translatable("cobblemon_battle_showtime." + kind + ".none");
		}
		String namespace = id.contains(":") ? id.substring(0, id.indexOf(':')) : "cobblemon_battle_showtime";
		String name = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		String readable = name.replace('_', ' ');
		readable = readable.isEmpty() ? id : readable.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + readable.substring(1);
		return Component.translatableWithFallback(namespace + "." + kind + "." + name, readable);
	}

	/** Every ambiance and preset id, sorted. */
	public static List<String> ids() {
		Set<String> all = new TreeSet<>(loaded.keySet());
		all.addAll(presets.keySet());
		return new ArrayList<>(all);
	}

	/** Every stadium id, sorted. */
	public static List<String> stadiumIds() {
		return stadiums.entrySet().stream().filter(entry -> !entry.getValue().reserved
				|| com.mystaria.showtime.api.ShowtimeHooks.unlocked(com.mystaria.showtime.api.ShowtimeHooks.AMBIANCE, entry.getKey()))
				.map(Map.Entry::getKey).toList();
	}

	/** The preset ids, sorted. */
	public static List<String> presetIds() {
		return new ArrayList<>(presets.keySet());
	}

	/**
	 * Whether the local player may choose {@code id}: not reserved, or unlocked for them (a preset: its ambiance).
	 * Unknown ids: no.
	 */
	public static boolean usable(String id) {
		if (isPreset(id) && !loaded.containsKey(id)) {
			return usable(presets.get(id).ambiance());
		}
		Ambiance ambiance = loaded.get(id);
		return ambiance != null && (!ambiance.reserved
				|| com.mystaria.showtime.api.ShowtimeHooks.unlocked(com.mystaria.showtime.api.ShowtimeHooks.AMBIANCE, id));
	}

	/** The ids the local player may choose, sorted (the menu's list), presets included. */
	public static List<String> usableIds() {
		return ids().stream().filter(Ambiances::usable).toList();
	}

	/** The reserved ids, sorted: what a mod may unlock (Phantasmon's rewards page). */
	public static List<String> reservedIds() {
		List<String> reserved = new ArrayList<>();
		loaded.forEach((id, ambiance) -> {
			if (ambiance.reserved) {
				reserved.add(id);
			}
		});
		stadiums.forEach((id, stadium) -> {
			if (stadium.reserved) {
				reserved.add(id);
			}
		});
		return reserved;
	}
}
