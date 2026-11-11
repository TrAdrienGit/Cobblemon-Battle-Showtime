package com.mystaria.showtime.ambiance;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * The ambiances known: every {@code assets/<namespace>/ambiances/<id>.json} of the loaded resource packs — Showtime's
 * own and any a pack adds — read again on every resource reload. Ids are {@code <namespace>:<id>}.
 */
public final class Ambiances {

	private static final Logger LOG = LoggerFactory.getLogger(Ambiances.class);
	private static final Gson GSON = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
	private static Map<String, Ambiance> loaded = Map.of();

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
				Map<String, Ambiance> read = new TreeMap<>();
				for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources("ambiances",
						path -> path.getPath().endsWith(".json")).entrySet()) {
					ResourceLocation file = entry.getKey();
					String path = file.getPath();
					String id = file.getNamespace() + ":" + path.substring("ambiances/".length(), path.length() - ".json".length());
					try (Reader reader = entry.getValue().openAsReader()) {
						Ambiance ambiance = GSON.fromJson(reader, Ambiance.class);
						if (ambiance != null) {
							read.put(id, ambiance);
						}
					} catch (Exception ex) {
						LOG.warn("Unreadable ambiance {}", file, ex);
					}
				}
				loaded = read;
				LOG.info("{} battle ambiance(s): {}", read.size(), read.keySet());
			}
		});
	}

	public static Ambiance get(String id) {
		return id == null ? null : loaded.get(id);
	}

	/**
	 * An ambiance's name for the player: {@code <namespace>.ambiance.<name>} (Showtime's are translated), else the id's
	 * name made readable; {@code none} or "" → "None".
	 */
	public static net.minecraft.network.chat.Component displayName(String id) {
		if (id == null || id.isEmpty() || "none".equals(id)) {
			return net.minecraft.network.chat.Component.translatable("cobblemon_battle_showtime.ambiance.none");
		}
		String namespace = id.contains(":") ? id.substring(0, id.indexOf(':')) : "cobblemon_battle_showtime";
		String name = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		String readable = name.replace('_', ' ');
		readable = readable.isEmpty() ? id : readable.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + readable.substring(1);
		return net.minecraft.network.chat.Component.translatableWithFallback(namespace + ".ambiance." + name, readable);
	}

	/** Every id, sorted. */
	public static List<String> ids() {
		return new ArrayList<>(loaded.keySet());
	}

	/** Whether the local player may choose {@code id}: not reserved, or unlocked for them. Unknown ids: no. */
	public static boolean usable(String id) {
		Ambiance ambiance = get(id);
		return ambiance != null && (!ambiance.reserved
				|| com.mystaria.showtime.api.ShowtimeHooks.unlocked(com.mystaria.showtime.api.ShowtimeHooks.AMBIANCE, id));
	}

	/** The ids the local player may choose, sorted (the menu's list). */
	public static List<String> usableIds() {
		return loaded.keySet().stream().filter(Ambiances::usable).toList();
	}

	/** The reserved ids, sorted: what a mod may unlock (Phantasmon's rewards page). */
	public static List<String> reservedIds() {
		return loaded.entrySet().stream().filter(entry -> entry.getValue().reserved).map(Map.Entry::getKey).toList();
	}
}
