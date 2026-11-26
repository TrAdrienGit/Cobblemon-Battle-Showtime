package com.mystaria.showtime.capture;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.cobblemon.mod.common.client.net.animation.PlayPosableAnimationHandler;
import com.cobblemon.mod.common.client.net.effect.SpawnSnowstormEntityParticleHandler;
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket;
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

/**
 * A move's Cobblemon timeline ({@code data/cobblemon/action_effects/moves/<move>.json}) replayed on the client as the
 * server would send it in a battle: the poses and MoLang (the user's rush, the target's recoil) and the particle
 * packets, which Showtime reads as its cues (actor, target, hit). Timed as Cobblemon times it: pauses, an animation's
 * delay, sequences, parallels. The battle's own conditions are taken as a hit that lands and hurts.
 *
 * <p>Only what reaches a client matters here: sounds, holds and moves of the entities are left out.
 */
final class Timeline {

	/** One packet to dispatch, {@code tick} ticks after the move starts. */
	record Event(int tick, Consumer<Minecraft> action) {
	}

	private final int user;
	private final int target;
	private final List<Event> events = new ArrayList<>();

	private Timeline(int user, int target) {
		this.user = user;
		this.target = target;
	}

	/** The move's timeline, or null when Cobblemon has none for it (then a generic one plays in a battle). */
	static List<Event> load(MinecraftServer server, String move, int user, int target) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobblemon", "action_effects/moves/" + move + ".json");
		Optional<Resource> resource = server.getResourceManager().getResource(id);
		if (resource.isEmpty()) {
			return null;
		}
		try (Reader reader = resource.get().openAsReader()) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			Timeline timeline = new Timeline(user, target);
			timeline.walk(root.getAsJsonArray("timeline"), 0);
			timeline.events.sort((a, b) -> Integer.compare(a.tick(), b.tick()));
			return timeline.events;
		} catch (Exception e) {
			return null;
		}
	}

	/** Plays the keyframes one after the other from {@code t}; returns when the last one is done. */
	private int walk(JsonArray frames, int t) {
		if (frames == null) {
			return t;
		}
		for (JsonElement element : frames) {
			t = play(element.getAsJsonObject(), t);
		}
		return t;
	}

	private int play(JsonObject frame, int t) {
		String type = text(frame, "type", "");
		switch (type) {
			case "pause" -> {
				return t + ticks(frame, "pause");
			}
			case "sequence" -> {
				return walk(frame.getAsJsonArray("keyframes"), t);
			}
			case "parallel" -> {
				int end = t;
				JsonArray frames = frame.getAsJsonArray("keyframes");
				if (frames != null) {
					for (JsonElement element : frames) {
						end = Math.max(end, play(element.getAsJsonObject(), t));
					}
				}
				return end;
			}
			case "fork" -> {
				walk(frame.getAsJsonArray("keyframes"), t);
				return t;
			}
			case "animation" -> {
				LinkedHashSet<String> names = new LinkedHashSet<>(strings(frame, "animation"));
				for (int entity : who(frame)) {
					events.add(new Event(t, mc -> PlayPosableAnimationHandler.INSTANCE.handle(
							new PlayPosableAnimationPacket(entity, names, List.of()), mc)));
				}
				return t + ticks(frame, "delay");
			}
			case "entity_molang" -> {
				List<String> expressions = new ArrayList<>();
				for (String expression : strings(frame, "expressions")) {
					if (!expression.contains("q.sound")) {
						expressions.add(expression);
					}
				}
				if (!expressions.isEmpty()) {
					for (int entity : who(frame)) {
						events.add(new Event(t, mc -> PlayPosableAnimationHandler.INSTANCE.handle(
								new PlayPosableAnimationPacket(entity, new LinkedHashSet<>(), expressions), mc)));
					}
				}
				return t;
			}
			case "entity_particles" -> {
				ResourceLocation effect = ResourceLocation.tryParse(text(frame, "effect", ""));
				if (effect != null) {
					List<String> locators = frame.has("locators") ? strings(frame, "locators") : List.of("target");
					List<String> targetLocators = frame.has("targetLocators") ? strings(frame, "targetLocators") : null;
					for (int entity : who(frame)) {
						Integer other = targetLocators == null ? null : entity == user ? target : user;
						events.add(new Event(t, mc -> SpawnSnowstormEntityParticleHandler.INSTANCE.handle(
								new SpawnSnowstormEntityParticlePacket(effect, entity, locators, other,
										targetLocators == null ? List.of() : targetLocators), mc)));
					}
				}
				return t + ticks(frame, "delay");
			}
			default -> {
				return t;
			}
		}
	}

	/**
	 * The entities a keyframe plays on, from its condition (Cobblemon's default: the user). Conditions on a miss or on
	 * no damage never hold here: the move lands and hurts.
	 */
	private int[] who(JsonObject frame) {
		String condition = text(frame, "entityCondition", "q.entity.is_user").replace(" ", "");
		if (condition.contains("q.missed(q.entity.uuid)==true") || condition.contains("q.hurt(q.entity.uuid)==false")) {
			return new int[0];
		}
		boolean onTarget = condition.contains("is_user==false") || condition.contains("!q.entity.is_user");
		boolean onUser = !onTarget && condition.contains("is_user");
		if (onTarget) {
			return new int[] {target};
		}
		return onUser ? new int[] {user} : new int[] {user, target};
	}

	private static int ticks(JsonObject frame, String key) {
		try {
			return Math.round(Float.parseFloat(text(frame, key, "0")) * 20);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static String text(JsonObject frame, String key, String fallback) {
		JsonElement value = frame.get(key);
		return value == null || value.isJsonNull() ? fallback : value.getAsString();
	}

	private static List<String> strings(JsonObject frame, String key) {
		List<String> list = new ArrayList<>();
		JsonElement value = frame.get(key);
		if (value instanceof JsonArray array) {
			for (JsonElement element : array) {
				list.add(element.getAsString());
			}
		} else if (value != null && !value.isJsonNull()) {
			list.add(value.getAsString());
		}
		return list;
	}
}
