package com.mystaria.showtime;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mystaria.showtime.config.ShowtimeConfig;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/**
 * Tells the player when a newer version is out (2026-11-09). At launch, off the main thread, Modrinth's public API is
 * asked for the latest Fabric 1.21.1 version of the project; if it is newer than the one installed, one chat message
 * with a link to the project's page shows when the player first joins a world. Silent on any failure: no network, no
 * project yet (404), an unexpected answer. Can be switched off in the menu (settings).
 */
public final class UpdateChecker {

	private static final Logger LOG = LoggerFactory.getLogger(UpdateChecker.class);
	/** The project's slug on Modrinth; change it here if the project is published under another one. */
	private static final String SLUG = "cobblemon-battle-showtime";
	private static final String PAGE = "https://modrinth.com/mod/" + SLUG;
	private static final String API = "https://api.modrinth.com/v2/project/" + SLUG
			+ "/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%221.21.1%22%5D";

	/** The newer version found, or null. Written by the check's thread, read on the client thread. */
	private static volatile String newer;
	private static boolean told;

	private UpdateChecker() {
	}

	public static void register() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(UpdateChecker::tell));
		if (!ShowtimeConfig.checkUpdates()) {
			return;
		}
		String installed = installed();
		Thread thread = new Thread(() -> check(installed), "Showtime update check");
		thread.setDaemon(true);
		thread.start();
	}

	private static String installed() {
		return FabricLoader.getInstance().getModContainer("cobblemon_battle_showtime")
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("0");
	}

	private static void check(String installed) {
		try {
			HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
			HttpRequest request = HttpRequest.newBuilder(URI.create(API)).timeout(Duration.ofSeconds(10))
					// Modrinth asks API users to name themselves.
					.header("User-Agent", "cobblemon-battle-showtime/" + installed + " (update check)").GET().build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				return; // 404 until the project is published
			}
			JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
			String latest = null;
			for (JsonElement each : versions) {
				String number = each.getAsJsonObject().get("version_number").getAsString();
				if (latest == null || compare(number, latest) > 0) {
					latest = number;
				}
			}
			if (latest != null && compare(latest, installed) > 0) {
				newer = latest;
				LOG.info("Cobblemon Battle Showtime {} is available (installed: {})", latest, installed);
			}
		} catch (Exception ex) {
			LOG.debug("Update check failed", ex);
		}
	}

	/** Compares dotted version numbers ("1.10.0" > "1.9.2"); anything after a "-" or "+" is ignored. */
	static int compare(String a, String b) {
		String[] x = a.split("[-+]")[0].split("\\.");
		String[] y = b.split("[-+]")[0].split("\\.");
		for (int i = 0; i < Math.max(x.length, y.length); i++) {
			int diff = Integer.compare(part(x, i), part(y, i));
			if (diff != 0) {
				return diff;
			}
		}
		return 0;
	}

	private static int part(String[] parts, int index) {
		if (index >= parts.length) {
			return 0;
		}
		try {
			return Integer.parseInt(parts[index].replaceAll("\\D", ""));
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	/** Once per game session, on joining a world. */
	private static void tell() {
		var player = net.minecraft.client.Minecraft.getInstance().player;
		if (told || newer == null || player == null || !ShowtimeConfig.checkUpdates()) {
			return;
		}
		told = true;
		Component link = Component.translatable("cobblemon_battle_showtime.update.link").withStyle(style -> style
				.withColor(ChatFormatting.AQUA).withUnderlined(true)
				.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, PAGE))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(PAGE))));
		player.displayClientMessage(Component.translatable("cobblemon_battle_showtime.update.available", newer, installed())
				.withStyle(ChatFormatting.GOLD).append(" ").append(link), false);
	}
}
