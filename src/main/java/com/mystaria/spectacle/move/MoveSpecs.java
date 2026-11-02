package com.mystaria.spectacle.move;

import java.io.Reader;
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
 * The redrawn moves known: every {@code assets/<namespace>/moves/<move>.json} of the loaded resource packs — Spectacle's
 * own and any a pack adds (a later pack wins) — read again on every resource reload. Keyed by the move's id alone.
 */
public final class MoveSpecs {

	private static final Logger LOG = LoggerFactory.getLogger(MoveSpecs.class);
	private static final Gson GSON = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
	private static Map<String, MoveSpec> loaded = Map.of();

	private MoveSpecs() {
	}

	public static void register() {
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return ResourceLocation.fromNamespaceAndPath("cobblemon_spectacle", "moves");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				Map<String, MoveSpec> read = new TreeMap<>();
				for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources("moves",
						path -> path.getPath().endsWith(".json")).entrySet()) {
					ResourceLocation file = entry.getKey();
					String path = file.getPath();
					String move = path.substring("moves/".length(), path.length() - ".json".length());
					try (Reader reader = entry.getValue().openAsReader()) {
						MoveSpec spec = GSON.fromJson(reader, MoveSpec.class);
						if (spec != null) {
							read.put(move, spec);
						}
					} catch (Exception ex) {
						LOG.warn("Unreadable move effect {}", file, ex);
					}
				}
				loaded = read;
				LOG.info("{} redrawn move(s)", read.size());
			}
		});
	}

	public static MoveSpec get(String move) {
		return loaded.get(move);
	}

	public static java.util.Set<String> ids() {
		return loaded.keySet();
	}
}
