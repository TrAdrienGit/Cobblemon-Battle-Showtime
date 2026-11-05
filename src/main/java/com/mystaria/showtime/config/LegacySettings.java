package com.mystaria.showtime.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Carries over the settings saved under the mod's former name, Cobblemon Spectacle (D-40): each file moves to its new
 * name once, and the ambiance ids inside follow the new namespace. Nothing happens when there is nothing to carry.
 */
public final class LegacySettings {

	private static final Logger LOG = LoggerFactory.getLogger(LegacySettings.class);
	private static final String LEGACY_NAMESPACE = "cobblemon_spectacle";
	private static final String NAMESPACE = "cobblemon_battle_showtime";

	private LegacySettings() {
	}

	/** Moves {@code <former name><suffix>.json} to the current name, unless the current one already exists. */
	public static void migrate(String suffix) {
		Path config = FabricLoader.getInstance().getConfigDir();
		Path legacy = config.resolve(LEGACY_NAMESPACE + suffix + ".json");
		Path current = config.resolve(NAMESPACE + suffix + ".json");
		if (!Files.exists(legacy) || Files.exists(current)) {
			return;
		}
		try {
			Files.writeString(current, Files.readString(legacy).replace(LEGACY_NAMESPACE + ":", NAMESPACE + ":"));
			Files.delete(legacy);
			LOG.info("Settings carried over from {} to {}", legacy.getFileName(), current.getFileName());
		} catch (IOException ex) {
			LOG.warn("Cannot carry over {}", legacy, ex);
		}
	}
}
