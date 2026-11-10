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
		/** Star size factor (2026-11-10: 1 made them read as confetti; finer looks like a sky). */
		public float starSize = 1f;
		/** When set, stars take these colours in turn (white, blue, gold…) instead of {@code starColor}. */
		public List<String> starColors = List.of();
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
		/** Colour bands from pole to pole (a gas giant); empty: one colour. */
		public List<String> bands = List.of();
		/** A thin glowing rim round it (its atmosphere). */
		public String atmosphere;
		/** Its ring's tilt, 0 (flat) to 1. */
		public float tilt = 0.25f;
	}

	/** An aurora: curtains of light waving high over the field. */
	/** A nebula: a cloud of soft glowing puffs far away in the sky (Cosmos, 2026-11-10). */
	public static final class Nebula {
		public String color = "#7a3dff";
		public String accent = "#3dd0ff";
		/** Where it is, in degrees round the field and above the horizon. */
		public float yaw;
		public float pitch = 20f;
		/** Its size, in blocks at the sky's distance. */
		public float size = 18f;
		public int puffs = 16;
		public int alpha = 34;
	}

	/** The galaxy's band: a glowing strip of dense stars across the sky. */
	public static final class Galaxy {
		public String color = "#c8d0ff";
		/** The band's tilt, in degrees, and where it crosses the horizon. */
		public float tilt = 35f;
		public float yaw;
		public int alpha = 26;
		public int stars = 700;
	}

	/** Low mist drifting over the ground, following it, thicker in patches (2026-11-10). */
	public static final class Mist {
		public String color = "#b0c0c8";
		public int alpha = 60;
		/** How high it rises over the ground, in blocks, and in how many layers. */
		public float height = 1.2f;
		public int layers = 2;
		/** 0: the field's size and a half. */
		public float radius;
		/** Size of its patches, in blocks, and how fast they drift. */
		public float scale = 6f;
		public float speed = 0.4f;
	}

	/** Glowing veins on the ground, spreading from the rifts (or from round the field), a pulse running along (Corrupted). */
	public static final class Veins {
		public String color = "#ff2a8a";
		public int alpha = 170;
		public int branches = 3;
		public float length = 11f;
		public float pulse = 1f;
	}

	/** Jagged glowing cracks across the sky (Corrupted). */
	public static final class SkyCracks {
		public String color = "#ff3070";
		public int count = 6;
		public int alpha = 200;
		/** Length of each crack on the sky, in blocks. */
		public float size = 16f;
	}

	/** Black cubes floating round the field that glitch: jump elsewhere, flicker (Corrupted). */
	public static final class GlitchCubes {
		public String color = "#ff2a8a";
		public int count = 8;
		public List<Float> size = List.of(0.6f, 1.5f);
		public List<Float> radius = List.of(6f, 15f);
		public List<Float> height = List.of(2f, 7f);
		/** Ticks between two jumps. */
		public int jump = 50;
	}

	/** Will-o'-wisps: small flames of light drifting round the field, flickering (Spectral). */
	public static final class Wisps {
		public String color = "#5cffd0";
		public String accent = "#3db8ff";
		public int count = 10;
		public List<Float> radius = List.of(5f, 14f);
		public List<Float> height = List.of(0.8f, 3f);
		/** Size factor: 1 a will-o'-wisp, about 0.3 a firefly. */
		public float size = 1f;
		/** Blinking on and off, as fireflies do, instead of a steady flicker. */
		public boolean blink;
	}

	/** Pale ghost silhouettes drifting far beyond the field, fading in and out (Spectral). */
	public static final class Ghosts {
		public String color = "#d8f4ff";
		public int count = 5;
		public List<Float> radius = List.of(22f, 32f);
		public int alpha = 45;
		/** Height of a ghost, in blocks. */
		public float size = 3f;
	}

	/** A moon on the sky, with a halo. */
	public static final class Moon {
		public String color = "#f4f0e0";
		public float yaw = 200f;
		public float pitch = 35f;
		public float size = 5f;
	}

	/** Brief dips into darkness now and then, as if the light died for a moment (Spectral). */
	public static final class Flicker {
		public List<Integer> every = List.of(140, 320);
		public float alpha = 0.45f;
		public int ticks = 4;
	}

	public static final class Aurora {
		public List<String> colors = List.of("#3dff9a", "#38e0ff", "#a24bff");
		public float height = 38f;
		public int alpha = 90;
	}

	/** A rainbow arching over the field, behind the opponent. */
	public static final class Rainbow {
		public float radius = 30f;
		public float alpha = 0.45f;
		/** A fainter second bow outside the first, its colours reversed, as in a real double rainbow. */
		public boolean doubled;
	}

	/** Soap bubbles rising slowly round the field, catching the light, popping at the top (Fairy Tale). */
	public static final class Bubbles {
		public String color = "#e8f4ff";
		public int count = 14;
		public List<Float> radius = List.of(5f, 14f);
		public float height = 9f;
		public List<Float> size = List.of(0.25f, 0.6f);
	}

	/** Small glowing mushrooms in clumps round the field (Fairy Tale). */
	public static final class Mushrooms {
		public List<String> colors = List.of("#ff9ad8", "#9ad8ff", "#c8a8ff");
		public int clumps = 12;
		/** Distance beyond the field's edge, in blocks. */
		public float ring = 1.5f;
		public float size = 0.5f;
	}

	/** Old film over the screen: a scratch running down now and then, specks of dust, the light breathing. */
	public static final class Film {
		/** Chance per tick that a scratch starts. */
		public float scratches = 0.02f;
		/** Chance per tick of a speck of dust. */
		public float dust = 0.15f;
		/** How much the light breathes, 0..1. */
		public float flicker = 0.05f;
	}

	/** A sound playing now and then somewhere round the camera: distant thunder, a chime, a whisper. */
	public static final class AmbientSound {
		public String id;
		/** Ticks between two: [min, max]. */
		public List<Integer> every = List.of(200, 400);
		public float volume = 0.5f;
		/** Pitch: [min, max]. */
		public List<Float> pitch = List.of(1f, 1f);
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
		/** Brightness at the beam's base, 0 to 255 (2026-11-10: capped, they blinded). */
		public int alpha = 70;
		/** How far beyond the field the bases stand, in blocks. */
		public float ring = 4f;
		/** When over 0: the bases gather in an arc this many degrees wide, around {@code facing} (the sun's side). */
		public float sector = 0f;
		public float facing = 0f;
	}

	/** Particles: {@code area} field (on the ground), ring (around), air (above), sky (high up). */
	/** A mirror ball over the field, throwing light dots over the ground (Disco, 2026-11-10). */
	public static final class MirrorBall {
		public float height = 9f;
		public float size = 1.4f;
		public int dots = 44;
		public List<String> colors = List.of("#ffffff");
		/** Turns per minute. */
		public float speed = 4f;
		public int alpha = 150;
	}

	/** Coloured tiles laid on the ground round the field, lighting up on a beat (Disco). */
	public static final class DanceFloor {
		public float tile = 2f;
		/** 0: the field's own size. */
		public float radius;
		public List<String> colors = List.of("#ff2fa8", "#ffd23d", "#b44bff", "#2fd5ff");
		/** Ticks per beat. */
		public int beat = 10;
		public int alpha = 80;
	}

	/** Glowing lines on the ground in a grid, fading into the distance, a wave running through them (Arcade). */
	public static final class NeonGrid {
		public float spacing = 2f;
		public String color = "#00ffd5";
		public String accent = "#ff00c8";
		public int alpha = 150;
		/** Its extent, in field radii. */
		public float extent = 1.8f;
		/** Blocks per second of the wave. */
		public float pulse = 6f;
	}

	/** Floating 8-bit sprites (coin, heart, star, gem), turning round the field, facing the camera (Arcade). */
	public static final class Sprites {
		public List<String> shapes = List.of("coin", "heart", "star", "gem");
		public int count = 10;
		public List<Float> radius = List.of(9f, 16f);
		public List<Float> height = List.of(3f, 8f);
		/** One pixel's size, in blocks. */
		public float pixel = 0.12f;
		public float bob = 0.4f;
		public float orbit = 0.4f;
	}

	/** Small living things fluttering round the field: {@code butterfly} (Meadow). */
	public static final class Critters {
		public String kind = "butterfly";
		public int count = 12;
		public List<String> colors = List.of("#ffd23d", "#ffffff", "#ff9ad5", "#7fd0ff");
		public float radius = 14f;
	}

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
		/** A particle glinting round each block now and then (crystals sparkling); null: none. */
		public String sparkle;
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
		/** Big text shown for a moment over the screen ("CRITICAL!"), in Minecraft's pixel font. */
		public String text;
		public String textColor = "#ffffff";
	}

	/** The battle's moments an ambiance answers: a Pokémon sent out, a move used, a critical hit, a faint, the end. */
	public static final class Moments {
		public Moment sendOut;
		public Moment attack;
		public Moment crit;
		public Moment faint;
		public Moment victory;
		/** This player lost (falls back to {@code victory} when an ambiance has no defeat of its own). */
		public Moment defeat;
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
	public MirrorBall mirrorBall;
	public Mist mist;
	public Veins veins;
	public SkyCracks skyCracks;
	public GlitchCubes glitchCubes;
	public Wisps wisps;
	public Ghosts ghosts;
	public Moon moon;
	public Flicker flicker;
	public Bubbles bubbles;
	public Mushrooms mushrooms;
	public Film film;
	public List<AmbientSound> ambientSounds = List.of();
	public List<Nebula> nebulae = List.of();
	public Galaxy galaxy;
	public DanceFloor danceFloor;
	public NeonGrid neonGrid;
	public Sprites sprites;
	public Critters critters;
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
