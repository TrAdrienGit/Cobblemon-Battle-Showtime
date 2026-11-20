package com.mystaria.showtime.ambiance;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Transitions between two ambiances (2026-11-20): when the ambiance imposed changes during a battle (a Pokémon Reborn
 * field turning into another), a short scene of two or three seconds plays instead of a cut behind a flash. The
 * camera keeps its own course: the place changes around the battle.
 * <p>
 * One JSON file per transition in {@code transitions/}: the ambiances it goes from and to, what of the old place stays
 * a while to leave in its own way (crystals shattering, glowing veins dying out), how long the sky, fog and mist take to
 * turn into the new ones, and cues on a timeline (a moment's effects, dust rising from the floor).
 */
public final class Transitions {

	private static final Logger LOG = LoggerFactory.getLogger(Transitions.class);
	private static final Gson GSON = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
	private static final String FOLDER = "transitions";

	/** A transition, as written in its file. */
	public static final class Transition {
		/** The ambiances it goes from and to (ids, "*" for any). */
		public List<String> from = List.of();
		public List<String> to = List.of();
		/** How long it plays, in ticks. */
		public int ticks = 50;
		/** Ticks the sky and fog take to turn from the old ambiance's into the new one's. */
		public int blend = 40;
		/** Ticks the new ambiance's mist takes to rise. */
		public int arrive = 30;
		/** The old ambiance's props that stay to leave in their own way, by kind ("crystal"). */
		public Map<String, Leaving> leaving = Map.of();
		/** Ticks the old ambiance's glowing veins take to die out (0: gone at once). */
		public int veinsFade;
		public List<Cue> cues = List.of();
		/** The camera meanwhile (null: it keeps its course). */
		public Camera camera;
		/** Ids qualified with the file's namespace (set at load). */
		transient List<String> fromIds = List.of();
		transient List<String> toIds = List.of();
	}

	/** The camera during a transition: pulled back to take in the whole field, shaken. */
	public static final class Camera {
		/** How long it films the whole field (0: the transition's length). */
		public float seconds;
		/** How much of the field it frames, as a share of the field's reach. */
		public float reach = 1f;
		/** The shake: how far it moves at first, in blocks, and over how long it dies out. */
		public float shake;
		public float shakeSeconds = 1f;
	}

	/** How the old props of one kind leave. */
	public static final class Leaving {
		/** "shatter": each one flares, then bursts into shards and is gone. */
		public String exit = "shatter";
		/** Over how many ticks they go, one after another, from the start. */
		public int spread = 20;
		/** Ticks one takes to flare, then to break apart. */
		public int flare = 3;
		public int breakTicks = 7;
		/** The block whose shards fly when one breaks, its sound, and a sparkle. */
		public String shards = "minecraft:amethyst_block";
		public int shardCount = 14;
		public String sound = "minecraft:block.amethyst_cluster.break";
		public float volume = 0.7f;
		public String sparkle;
	}

	/** A cue on the timeline: a moment's effects ({@link Ambiance.Moment}) at a tick, plus dust from the floor. */
	public static final class Cue extends Ambiance.Moment {
		public int at;
		/** Puffs of dust rising from the floor all over the field and round it. */
		public int dust;
		public String dustParticle = "minecraft:large_smoke";
		/** How far round the field the dust rises, as a share of the field's reach. */
		public float dustRadius = 1.4f;
		/** Bits of a block falling from above all over the field, and the block. */
		public int debris;
		public String debrisBlock = "minecraft:amethyst_block";
		/** Bits of a block blown outward from the field's centre, and the block. */
		public int burst;
		public String burstBlock = "minecraft:amethyst_block";
		/** A new jolt of the camera: how far, in blocks, and over how long it dies out. */
		public float shake;
		public float shakeSeconds = 0.6f;
	}

	private static List<Transition> all = List.of();

	private Transitions() {
	}

	/** Reads every transition file (with the ambiances, at each resource reload). */
	static void load(ResourceManager manager) {
		List<Transition> read = new ArrayList<>();
		for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources(FOLDER, path -> path.getPath().endsWith(".json")).entrySet()) {
			String namespace = entry.getKey().getNamespace() + ":";
			try (Reader reader = entry.getValue().openAsReader()) {
				Transition transition = GSON.fromJson(JsonParser.parseReader(reader), Transition.class);
				if (transition == null) {
					continue;
				}
				transition.fromIds = transition.from.stream().map(id -> qualify(namespace, id)).toList();
				transition.toIds = transition.to.stream().map(id -> qualify(namespace, id)).toList();
				read.add(transition);
			} catch (Exception ex) {
				LOG.warn("Unreadable transition {}", entry.getKey(), ex);
			}
		}
		all = List.copyOf(read);
		LOG.info("{} ambiance transition(s)", read.size());
	}

	private static String qualify(String namespace, String id) {
		return "*".equals(id) || id.contains(":") ? id : namespace + id;
	}

	/**
	 * The transition from the ambiance {@code from} to {@code to} (ids; a preset counts for its ambiance), or null: the
	 * first naming both exactly, else one with a "*" on a side.
	 */
	public static Transition find(String from, String to) {
		if (from == null || to == null) {
			return null;
		}
		Transition loose = null;
		for (Transition transition : all) {
			boolean fromExact = transition.fromIds.contains(from);
			boolean toExact = transition.toIds.contains(to);
			if (fromExact && toExact) {
				return transition;
			}
			if (loose == null && (fromExact || transition.fromIds.contains("*")) && (toExact || transition.toIds.contains("*"))) {
				loose = transition;
			}
		}
		return loose;
	}
}
