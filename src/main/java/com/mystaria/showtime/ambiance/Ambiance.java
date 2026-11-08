package com.mystaria.showtime.ambiance;

import java.util.List;

/**
 * A battle ambiance (phase A1, 2026-10-30), read from
 * {@code assets/<namespace>/ambiances/<id>.json}: layers of decoration, each optional, played on this client only
 * around the battle's field while it lasts. Gson fills the fields (snake_case in the file); colours are
 * {@code "#rrggbb"} strings, a lone value or a {@code [min, max]} pair where a range is allowed.
 */
public final class Ambiance {

	/** The day: {@code at} the time of day it starts from (0 morning, 6000 noon, 13000 dusk, 18000 midnight), {@code speed} how fast it then runs (0 stopped, 1 normal, 40 a day in half a minute). */
	public static final class Time {
		public Long at;
		public float speed = 1f;
	}

	/** Sky, fog, clouds and stars, blended by {@code strength} over what the world shows. */
	public static final class Sky {
		public String skyColor;
		public String fogColor;
		public float strength = 1f;
		/** Fog distance in blocks (start, end); null keeps the world's. */
		public Float fogStart;
		public Float fogEnd;
		/** Star brightness 0..1; null keeps the world's. */
		public Float stars;
		public boolean hideClouds;
		public String cloudColor;
		public Float cloudHeight;
		/** Rain and thunder shown (0..1), real Minecraft rain with its sound; null keeps the world's weather. */
		public Float rain;
		public Float thunder;
	}

	/** A shell around the field hiding the world beyond {@code radius}: the battle seems to float in it. */
	public static final class Backdrop {
		/** A texture (the End's sky, a skybox...), tinted; null: plain {@code tint}. */
		public String texture;
		public String tint = "#ffffff";
		public float radius = 40f;
		/** How far below the field it reaches (blocks): the ground beyond the field disappears too. */
		public float depth = 12f;
		/** Stars drawn on it, turning around the field ({@code spin} turns per minute). */
		public int stars;
		public float spin;
		public String starColor = "#ffffff";
		/** A floor of the backdrop under the field too: the ground disappears, the trainers stand on the void. */
		public boolean floor;
		/** The textured box itself; false: only its stars, around the field. */
		public boolean box = true;
		/** Stars below the field too (all around), not only in the sky. */
		public boolean starsAround;
	}

	/** Coloured light spots turning on the ground (a night club's floor). */
	public static final class FloorLights {
		public int count = 8;
		public List<String> colors = List.of("#ff2fa8", "#2fd5ff", "#b44bff", "#ffe23d");
		/** Turns per minute. */
		public float speed = 8f;
		/** Spot radius in blocks. */
		public float size = 2.2f;
		public int alpha = 140;
	}

	/** The screen pulsing in colours on a beat. */
	public static final class Strobe {
		public List<String> colors = List.of("#ffffff");
		/** Ticks between pulses. */
		public int every = 10;
		public float alpha = 0.12f;
	}

	/** Rifts tearing open around the field, then closing. */
	public static final class Rifts {
		public int count = 4;
		public String color = "#b400ff";
		public float height = 7f;
		public float width = 1.6f;
		/** Ticks of one open-close cycle. */
		public int cycle = 220;
		public String particle = "minecraft:reverse_portal";
	}

	/** Shooting stars crossing the sky; bigger ones with a trail are meteors. */
	public static final class ShootingStars {
		/** Chance per tick. */
		public float rate = 0.04f;
		public String color = "#e8f0ff";
		/** Width multiplier (meteors: 3 to 5). */
		public float size = 1f;
		/** Particles left behind (meteors: flame, smoke). */
		public String trail;
		/** Also crossing below the field (the void all around). */
		public boolean below;
	}

	/** A planet (or a sun) of a solar system around or under the field. */
	public static final class Planet {
		public String color = "#9fb8ff";
		/** Its radius in blocks. */
		public float size = 2f;
		/** Distance from the field's centre. */
		public float orbit = 20f;
		/** Height above (or, negative, below) the field. */
		public float height = -12f;
		/** Turns per minute around the field. */
		public float speed = 0.5f;
		/** A sun: glowing, unshaded. */
		public boolean glow;
		/** A ring around it (Saturn). */
		public String ring;
	}

	/** An aurora: curtains of light waving high over the field. */
	public static final class Aurora {
		public List<String> colors = List.of("#3dff9a", "#38e0ff", "#a24bff");
		public float height = 38f;
		public int alpha = 90;
	}

	/** A rainbow arching over the field, behind the opponent. */
	public static final class Rainbow {
		public float radius = 30f;
		public float alpha = 0.45f;
	}

	/** Searchlight beams around the field, sweeping the sky. */
	public static final class Spotlights {
		public int count = 6;
		public List<String> colors = List.of("#ffffff");
		/** Sweeps per minute. */
		public float sweep = 6f;
		public float height = 40f;
		public float width = 1.2f;
		/** Coming down from the sky (sunbeams) instead of rising from around the field. */
		public boolean fromSky;
	}

	/** Particles: {@code area} field (on the ground), ring (around), air (above), sky (high up). */
	public static final class Particles {
		public String type = "minecraft:end_rod";
		/** Per tick (fractions spawn now and then). */
		public float rate = 1f;
		public String area = "field";
		public List<Float> motion = List.of(0f, 0f, 0f);
		/** Random spread added to the motion. */
		public float spread = 0.02f;
	}

	/** Blocks floating and bobbing around the field (lit candles, lanterns...). */
	public static final class FloatingBlocks {
		public String block = "minecraft:candle[lit=true]";
		public int count = 12;
		/** Distance from the field's centre: [min, max]. */
		public List<Float> radius = List.of(6f, 14f);
		/** Height above the ground: [min, max]. */
		public List<Float> height = List.of(2f, 6f);
		public float bob = 0.35f;
		/** Turns per minute around the field. */
		public float orbit = 0.5f;
		/** A flame on top (candles). */
		public boolean flame;
	}

	/** Lightning striking around the field now and then (visual only). */
	public static final class Lightning {
		/** Ticks between strikes: [min, max]. */
		public List<Integer> every = List.of(100, 240);
	}

	/** A looping sound in the background. */
	public static final class Sound {
		public String id;
		public float volume = 0.5f;
		public float pitch = 1f;
	}

	/**
	 * What a moment of the battle adds: a burst of particles, a flash, a strike, a sound, a screen filter for a short
	 * while.
	 */
	public static final class Moment {
		public String particle;
		public int count = 30;
		public String flash;
		public boolean lightning;
		public String sound;
		public float volume = 1f;
		public float pitch = 1f;
		public String filter;
		public int filterTicks = 20;
	}

	/** The battle's moments an ambiance answers: a Pokémon sent out, a move used, a critical hit, a faint, the end. */
	public static final class Moments {
		public Moment sendOut;
		public Moment attack;
		public Moment crit;
		public Moment faint;
		public Moment victory;
	}

	public Time time;
	public Sky sky;
	public Backdrop backdrop;
	public Rainbow rainbow;
	public Spotlights spotlights;
	public List<Particles> particles = List.of();
	public FloatingBlocks floatingBlocks;
	public Lightning lightning;
	/**
	 * No block of the world drawn at all (2026-10-30): only the trainers, the Pokémon and the ambiance remain,
	 * floating in its sky. The world itself is untouched (collisions, movements).
	 */
	public boolean hideWorld;
	public FloorLights floorLights;
	public Strobe strobe;
	public Rifts rifts;
	public ShootingStars shootingStars;
	public List<Planet> planets = List.of();
	public Aurora aurora;
	/** Blows the particles (blocks per tick, x then z). */
	public List<Float> wind;
	public Sound sound;
	/**
	 * A screen filter for the whole battle: one of Minecraft's post effects ({@code minecraft:shaders/post/desaturate.json}
	 * black and white, {@code bits.json} pixels, {@code deconverge.json} split colours, {@code wobble.json},
	 * {@code ntsc.json} old TV, {@code invert.json}, {@code phosphor.json}...) or a resource pack's.
	 */
	public String filter;
	public Moments moments;

	static int color(String hex, int fallback) {
		if (hex == null || hex.length() != 7 || hex.charAt(0) != '#') {
			return fallback;
		}
		try {
			return Integer.parseInt(hex.substring(1), 16);
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}
}
