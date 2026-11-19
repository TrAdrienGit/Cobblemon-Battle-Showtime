package com.mystaria.showtime.ambiance;

import java.util.List;

/**
 * A battle ambiance (phase A1, 2026-10-30), read from
 * {@code assets/<namespace>/ambiances/<id>.json}: layers of decoration, each optional, played on this client only
 * around the battle's field while it lasts. Gson fills the fields (snake_case in the file); colours are
 * {@code "#rrggbb"} strings, a lone value or a {@code [min, max]} pair where a range is allowed.
 */
public final class Ambiance {

	/**
	 * Reserved (1.2.0): shown in the menu and played as the player's choice only when another mod unlocks it for them
	 * ({@code ShowtimeHooks.setUnlocks}); imposed on a battle, it plays anyway.
	 */
	public boolean reserved;

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
		/** When above 0: that many roots scattered over the field instead of starting from the rifts (Inferno's cracks). */
		public int scatter;
		/** A thin hot line along the middle of each vein; null: none. */
		public String core;
	}

	/** Glowing motes rising from the ground round the field, swaying, cooling as they climb: embers, or plankton. */
	public static final class Embers {
		/** Colour when born, and when about to die. */
		public String color = "#ffc040";
		public String accent = "#ff3010";
		public int count = 40;
		public List<Float> radius = List.of(3f, 16f);
		public float height = 10f;
		public float size = 0.12f;
		/** Blocks per second. */
		public float rise = 1f;
	}

	/** A volcano on the horizon: a dark cone, its crater glowing, lava running down, a plume of light (Inferno). */
	public static final class Volcano {
		public String color = "#ff5a10";
		public float yaw = 40f;
		public float size = 14f;
	}

	/** Light caustics dancing on the ground, as on a sea floor. */
	public static final class Caustics {
		public String color = "#a8f4ff";
		public int alpha = 55;
		/** 0: the field's size and a third. */
		public float radius;
		/** Pattern size factor (above 1: finer) and speed. */
		public float scale = 1f;
		public float speed = 1f;
	}

	/** Shafts of light slanting down from above, all from the same side, swaying slowly (Abyss). */
	public static final class Shafts {
		public String color = "#bff4ff";
		public int count = 8;
		public int alpha = 28;
		public float width = 2.2f;
		public float height = 34f;
		/** Lean from the vertical, in degrees. */
		public float tilt = 14f;
		public List<Float> ring = List.of(3f, 20f);
	}

	/** Kelp swaying round the field (Abyss). */
	public static final class Kelp {
		public String color = "#1f6a48";
		public int count = 28;
		/** Distance beyond the field's edge: [min, max]. */
		public List<Float> ring = List.of(1.5f, 8f);
		public List<Float> height = List.of(4f, 9f);
	}

	/** Paper lanterns rising slowly into the sky, lit from within (Sakura Festival). */
	public static final class Lanterns {
		public List<String> colors = List.of("#ffb060", "#ff8a60", "#ffd080");
		public int count = 14;
		public List<Float> radius = List.of(6f, 22f);
		public float height = 30f;
		public float size = 0.45f;
		/** Blocks per second. */
		public float rise = 0.35f;
	}

	/** Real fireworks bursting far off now and then (Sakura Festival); their colours also serve a moment's fireworks. */
	public static final class Fireworks {
		/** Empty: none far off, the colours only serve a moment's fireworks. */
		public List<Integer> every = List.of(140, 300);
		public List<Float> distance = List.of(28f, 40f);
		public List<Float> height = List.of(18f, 28f);
		public List<String> colors = List.of("#ff6a8a", "#ffd060", "#7ad8ff", "#ffffff", "#c88aff");
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
		/** Its grey seas; false: a plain disc (a pale sun). */
		public boolean seas = true;
		/** The halo's size (in disc radii) and strength. */
		public float halo = 3.5f;
		public int haloAlpha = 60;
	}

	/** Brief star-shaped glints in the air round the field: ice crystals catching the light (Blizzard). */
	public static final class Glints {
		public String color = "#e8f8ff";
		public int count = 24;
		public List<Float> radius = List.of(2f, 14f);
		public List<Float> height = List.of(0.5f, 6f);
		public float size = 0.35f;
	}

	/** A wall of fog round the field, thick at the bottom, drifting: the world beyond disappears (Blizzard). */
	public static final class FogWall {
		public String color = "#e4eef8";
		/** 0: the field's edge plus 8 blocks. */
		public float radius;
		public float height = 12f;
		public int alpha = 150;
		public float speed = 1f;
	}

	/** A desert mirage: a band of shimmering sky-coloured "water" along the horizon (Mirage). */
	public static final class Mirage {
		public String color = "#bfe0ff";
		public int alpha = 110;
		/** Its height over the horizon, in blocks at the sky's distance. */
		public float height = 3f;
	}

	/** Birds circling high over the field, dark V shapes flapping slowly (Mirage). */
	public static final class Birds {
		public String color = "#2a2018";
		public int count = 4;
		public List<Float> radius = List.of(10f, 22f);
		public List<Float> height = List.of(18f, 28f);
		public float size = 0.9f;
	}

	/** An 80s sun on the horizon: a gradient disc, its lower half cut by widening bands (Synthwave). */
	public static final class StripedSun {
		public String top = "#ffe14a";
		public String bottom = "#ff3a8a";
		public float yaw = 180f;
		public float size = 14f;
		public int stripes = 6;
	}

	/** A wormhole on the sky: a slowly turning spiral of light round a dark core and a bright rim (Ultra Space). */
	public static final class Wormhole {
		public String color = "#3df0ff";
		public String accent = "#ff4fd8";
		public float yaw;
		public float pitch = 25f;
		public float size = 8f;
		/** Turns per minute; negative: the other way. */
		public float spin = 1f;
		public int arms = 5;
	}

	/** Islands floating in the void round the field, crystals growing on them (Ultra Space). */
	public static final class Islands {
		public String rock = "#2a1f4a";
		public String top = "#5a3f9a";
		public String crystal = "#7ff8ff";
		public String accent = "#ff7ae8";
		public int count = 7;
		public List<Float> distance = List.of(24f, 44f);
		public List<Float> height = List.of(-14f, 18f);
		public List<Float> size = List.of(2.5f, 6f);
	}

	/**
	 * The field's own ground redrawn as the top of a floating island, its rock hanging below, crystals round its rim
	 * and a hexagon pattern lighting up across it. Meant for an ambiance hiding the world: the battle stands on it.
	 */
	public static final class Island {
		public String top = "#241a52";
		public String line = "#7ff8ff";
		public String rock = "#1a1238";
		public String crystal = "#7ff8ff";
		public String accent = "#ff7ae8";
		/** How far it reaches beyond the field's edge, in blocks. */
		public float margin = 3f;
		/** How deep its rock hangs; 0: about the field's size. */
		public float depth;
		public int crystals = 14;
		/** Hexagon size, in blocks, and how fast the light runs across them. */
		public float hex = 1.6f;
		public float pulse = 1f;
	}

	/**
	 * A cave stadium closed round the field (2026-11-19): its own floor, an uneven rock vault, pillars, and as the cave
	 * asks a lava lake and a dragon's hoard. Its vault gives the ambiance's props their anchors (ceiling, walls, floor).
	 * The rock takes the ambiance's {@link Light} when it has one.
	 */
	public static final class Cavern {
		public String rock = "#3a332c";
		public String floor = "#2e2924";
		/** The light from the field warming the rock nearest it, when the ambiance gives none. */
		public String light = "#ffd9a0";
		public float lightStrength = 0.35f;
		/** How dark the rock is where no light reaches (0..1). */
		public float ambient = 0.3f;
		/** The walls' distance beyond the field's edge, and the vault's height, in blocks. */
		public float margin = 12f;
		public float height = 16f;
		public int pillars = 5;
		public LavaLake lava;
		public Hoard hoard;
	}

	/**
	 * A sea stadium (2026-11-19, League Reborn's waters): the world hidden, the field on a small islet of sand and rock,
	 * water rolling out to the horizon. With {@code under}, the field lies on a sand seabed instead, the surface
	 * rippling {@code depth} blocks overhead. The water's colour, waves and state come from the ambiance's {@link Water}.
	 */
	public static final class Sea {
		public String sand = "#d9c48f";
		public String rock = "#6e675c";
		public String water = "#2f8fc0";
		public String deep = "#0e3f66";
		public String foam = "#f2fbff";
		/** The islet's reach beyond the field's edge, and how far the water goes, in blocks. */
		public float margin = 3f;
		public float radius = 96f;
		/** The waves' height (blocks) and pace. */
		public float wave = 0.32f;
		public float speed = 1f;
		public int rocks = 7;
		public boolean under;
		public float depth = 14f;
	}

	/**
	 * How an ambiance sees the sea it is laid on (2026-11-19). Every field optional: unset, the stadium's own. {@code waves}
	 * multiplies the stadium's; {@code frozen} stills the water into cracked ice with floes; {@code oil} lays rainbow
	 * slicks on it; {@code clarity} (0..1) is how much sun glitters on it; {@code light} is the surface seen from below.
	 */
	public static final class Water {
		public String color;
		public String deep;
		public String foam;
		public Float waves;
		public boolean frozen;
		public boolean oil;
		public float clarity = 1f;
		public String light;
	}

	/**
	 * An outdoor stadium (2026-11-19, League Reborn's lands): the world hidden, the field on ground of its own out to the
	 * horizon, coloured per vertex from {@code ground} (patches blended by noise). {@code style}: {@code plain} (rolling
	 * hills), {@code dunes} (long sand ridges), {@code summit} (the ground falling away into cliffs beyond the flat, a sea
	 * of clouds below), {@code marsh} (nearly flat, dark). The rest are optional dressings round the field.
	 */
	public static final class Land {
		public List<String> ground = List.of("#6a9a3a", "#5a8a32", "#7aa848");
		public float grain = 0.12f;
		/** Blocks beyond the field's edge kept level, the hills' height further out, the ground's reach. */
		public float flat = 4f;
		public float hills = 4f;
		public float radius = 110f;
		public String style = "plain";
		public Ranges ranges;
		public CloudSea clouds;
		public Trees trees;
		public Rocks rocks;
		public Pools pools;
		public Cracks cracks;
		public Columns columns;
		public Junk junk;
		public Tiles tiles;
		/** Built of blocks (2026-11-19): what the ground is made of. Unset: plain grass, dirt and stone. */
		public Ground blocks;
		/** Built of blocks: the plants growing on the ground (grass, flowers, ferns, dead bushes...). */
		public List<Plant> plants;
		/** Built of blocks: the biome whose colours tint the grass, the leaves and the water. */
		public String biome = "minecraft:plains";
	}

	/**
	 * The blocks a land is made of (2026-11-19). {@code surface}: the top, the first everywhere, the second and third in
	 * patches (where the smooth ground had its second and third colours); {@code soil} under it, {@code rock} on cliffs
	 * and deeper, or {@code bands} of rock laid by height (badlands). Ids with properties: {@code oak_log[axis=x]}.
	 */
	public static final class Ground {
		public List<String> surface = List.of("minecraft:grass_block");
		public List<String> soil = List.of("minecraft:dirt");
		public List<String> rock = List.of("minecraft:stone", "minecraft:andesite");
		public List<String> bands;
		/** The mountains far off: their top where it is gentle (unset: the surface's first block). */
		public String far;
	}

	/**
	 * A plant of a land built of blocks (2026-11-19): {@code block} grows on {@code chance} of the ground's blocks that
	 * can hold it, within {@code ring} blocks of the centre (unset: everywhere). Tall plants take their two halves.
	 */
	public static final class Plant {
		public String block = "minecraft:short_grass";
		public float chance = 0.2f;
		public List<Float> ring;
	}

	/** Mountain ranges far off, in layers paler with distance; {@code snow} caps their peaks. */
	public static final class Ranges {
		public String color = "#5a6a7a";
		public String snow;
		public float height = 30f;
		public float distance = 100f;
		public int layers = 3;
	}

	/** A sea of clouds {@code depth} blocks below the summit, drifting. */
	public static final class CloudSea {
		public String color = "#f4f8ff";
		public String shade = "#b8c8dc";
		public float depth = 22f;
	}

	/** Trees round the field. Kinds: {@code broadleaf}, {@code pine}, {@code dead}. {@code canopy}: big ones leaning in. */
	public static final class Trees {
		public String kind = "broadleaf";
		public int count = 40;
		public List<Float> ring = List.of(4f, 30f);
		public List<Float> height = List.of(6f, 11f);
		public String trunk = "#5a3e26";
		public List<String> leaves = List.of("#3f7a2a", "#4e8a30", "#2f6a24");
		public boolean canopy;
		/**
		 * Built of blocks (2026-11-19): the trees' kinds, taken in turn: {@code oak}, {@code birch}, {@code spruce},
		 * {@code dark_oak}, {@code jungle}, {@code acacia}, {@code cherry}, {@code azalea}, {@code dead}. Unset: from
		 * {@code kind} (broadleaf: oak and birch; pine: spruce; dead: dead).
		 */
		public List<String> species;
	}

	/** Boulders and, with {@code spires}, tall needles of rock. */
	public static final class Rocks {
		public String color = "#7a7268";
		public int count = 20;
		public List<Float> ring = List.of(3f, 30f);
		public List<Float> size = List.of(0.5f, 1.8f);
		public int spires;
		/** Built of blocks (2026-11-19): what boulders and spires are made of. Unset: the land's rock. */
		public List<String> blocks;
	}

	/** Pools sunk in the ground (sludge, bog water), bubbling; the ambiance's {@code water} recolours them. */
	public static final class Pools {
		public String color = "#3a4a2a";
		public String rim = "#2a2a1a";
		public String glow;
		public int count = 8;
		public List<Float> ring = List.of(3f, 22f);
		public List<Float> size = List.of(1.5f, 4f);
		/** Built of blocks (2026-11-19): lily pads on this share of the water (0..1). */
		public float lilies;
	}

	/** Fissures in the ground, glowing from below. */
	public static final class Cracks {
		public String color = "#ff7a20";
		public String core = "#ffe080";
		public int count = 24;
		public List<Float> ring = List.of(1f, 26f);
	}

	/**
	 * A paved floor (2026-11-19): tiles of {@code size} blocks laid on the world's grid out to {@code radius} blocks from
	 * the centre (0: the field and its level ground), {@code joint} between them, each tile a shade of its own
	 * ({@code shade}); {@code vein} runs through the stone (marble), none when unset. Always in blocks, a pavement is.
	 */
	public static final class Tiles {
		public int size = 2;
		public float radius;
		public String joint = "#a8a094";
		public String vein;
		public float shade = 0.05f;
		/** Built of blocks (2026-11-19): the paving stones, one per tile. */
		public List<String> blocks = List.of("minecraft:smooth_quartz", "minecraft:calcite", "minecraft:polished_diorite");
	}

	/** A ring of columns round the field (a temple), lintels joining them; {@code ruined}: some broken. */
	public static final class Columns {
		public String color = "#ece6d8";
		public int count = 12;
		public float distance = 6f;
		public float height = 9f;
		public boolean ruined;
		/** Built of blocks (2026-11-19): the shaft, and the base and capital. */
		public String block = "minecraft:quartz_pillar";
		public String capital = "minecraft:chiseled_quartz_block";
	}

	/** Scrap round the field: rusted barrels, lying and standing. */
	public static final class Junk {
		public List<String> colors = List.of("#7a4a2a", "#5a5a50", "#8a6a2a");
		public int count = 24;
		public List<Float> ring = List.of(3f, 24f);
		/** Built of blocks (2026-11-19): the scrap, piled one or two high. */
		public List<String> blocks = List.of("minecraft:barrel[facing=up]", "minecraft:barrel[facing=east]",
				"minecraft:barrel[facing=south]", "minecraft:cauldron", "minecraft:damaged_anvil", "minecraft:chipped_anvil",
				"minecraft:composter", "minecraft:weathered_cut_copper", "minecraft:exposed_copper", "minecraft:oxidized_cut_copper",
				"minecraft:iron_bars", "minecraft:hopper", "minecraft:blast_furnace[facing=north]");
	}

	/**
	 * An indoor stadium (2026-11-19, League Reborn's halls): the world hidden, the field in a room {@code margin} blocks
	 * past its edge. {@code shape}: {@code box} (four walls, a ceiling with beams and lamps) or {@code tent} (a round
	 * striped wall under a cone). Optional: a {@code checker} floor, {@code mirrors} on the walls, {@code machines},
	 * chess {@code pieces}, a {@code flat} floor.
	 */
	public static final class Hall {
		public String shape = "box";
		public String floor = "#4a4c50";
		public String checker;
		public float tile = 2f;
		public String wall = "#3a3e46";
		public String ceiling = "#2a2c32";
		public String lamp = "#fff0c8";
		public List<String> stripes;
		public float margin = 12f;
		public float height = 14f;
		public boolean mirrors;
		/** A level floor everywhere, even under the field (a board must be flat, whatever the blocks under it). */
		public boolean flat;
		public Machines machines;
		public Pieces pieces;
	}

	/** A plant's machines: conveyor belts carrying crates, gears turning on the walls, presses stamping. */
	public static final class Machines {
		public String metal = "#6a7078";
		public String belt = "#24262a";
		public List<String> crates = List.of("#a07a4a", "#8a6a3a", "#b08a50");
		public String warning = "#ffb020";
		public int gears = 6;
		public int presses = 4;
		public float speed = 1f;
	}

	/** Giant chess pieces standing round the field on its board. */
	public static final class Pieces {
		public String white = "#ece6da";
		public String black = "#2a2630";
		public int count = 16;
	}

	/** Grass blades over the place's ground, waving in the wind (2026-11-19). */
	public static final class Grass {
		public List<String> colors = List.of("#4a8a2a", "#5a9a32", "#6aaa3a");
		public String tip = "#b8e070";
		public int count = 2200;
		public List<Float> ring = List.of(0f, 26f);
		public List<Float> height = List.of(0.4f, 0.9f);
		public float wind = 1f;
	}

	/** Flowers over the place's ground (2026-11-19): a stem and a head of petals. */
	public static final class Flowers {
		public List<String> colors = List.of("#ff6a8a", "#ffd23d", "#ffffff", "#b07aff");
		public int count = 120;
		public List<Float> ring = List.of(1f, 24f);
		public List<Float> height = List.of(0.3f, 0.7f);
	}

	/** The place on fire (2026-11-19): trees charred and burning, flames licking the ground round the field. */
	public static final class Blaze {
		public String color = "#ff6a10";
		public String core = "#ffe060";
		public int flames = 60;
	}

	/** The plant's power cut (2026-11-19): machines stopped, lamps out, red emergency lights, sparking cables. */
	public static final class PowerCut {
		public String color = "#ff2a1a";
		public int cables = 8;
	}

	/** The light an ambiance casts (2026-11-19): a stadium's materials take it (cave rock tinted by crystals, lava). */
	public static final class Light {
		public String color = "#ffd9a0";
		public float strength = 0.4f;
		/** Optional: the stone a stadium is built of, as this ambiance sees it (crystal violet, burnt black...). */
		public String rock;
		public String floor;
	}

	/**
	 * Props (2026-11-19): things an ambiance adds on the surfaces round the field, never replacing a block. Each takes
	 * the first of its {@code anchors} kinds that has room ({@code ceiling}, {@code wall}, {@code floor}, {@code float}),
	 * the rest going to the next; a prop whose kinds are all missing is left out (no stalactite under open sky).
	 * Kinds: {@code crystal} (clusters, glowing; beams falling from those on a ceiling or floating), {@code stalactite}
	 * (water dripping from them when {@code drips} > 0), {@code stalagmite}.
	 */
	public static final class Prop {
		public String kind = "crystal";
		public List<String> anchors = List.of("ceiling", "wall", "floor", "float");
		public int count = 30;
		/** Length: [min, max], in blocks. */
		public List<Float> size = List.of(1.2f, 3.6f);
		/** Rock props' colour; crystals' colours (one per cluster, the next as an accent). */
		public String color = "#5a4e42";
		public List<String> colors = List.of("#8ad8ff", "#ff9af0", "#b0ff9a", "#fff08a", "#c0a0ff");
		public float glow = 1f;
		public float pulse = 1f;
		/** The light runs round the field in a slow wave, dying between (dark crystals). */
		public boolean wave;
		public int beams;
		/** Ticks they take to grow in when the ambiance starts. */
		public int grow = 50;
		public float drips;
		public String drip = "#a8d8ff";
	}

	/** A ring of lava round the field, crust plates drifting on it, lavafalls pouring from the walls. */
	public static final class LavaLake {
		public String color = "#ff4a08";
		public String core = "#ffd060";
		public String crust = "#2a0e06";
		/** Gap between the field's edge and the lava, then the lava's width, in blocks. */
		public float gap = 1.5f;
		public float width = 4f;
		public int falls = 5;
	}

	/** A dragon's hoard: gold heaps glinting, and a great skeleton half buried, between the field and the walls. */
	public static final class Hoard {
		public String gold = "#ffcf4a";
		public int piles = 9;
		public boolean bones = true;
		public String bone = "#e4d8bc";
	}

	/** Court markings on the floor along the trainers' axis: the border, a centre line and circle (Champion's Stage). */
	public static final class Court {
		public String color = "#f4f0e0";
		public String accent = "#ffcf4a";
		public int alpha = 150;
	}

	/** Stadium light towers round the field: lattice masts topped with banks of lamps lighting the field. */
	public static final class LightTowers {
		public int count = 4;
		public float height = 22f;
		/** 0: behind the stands. */
		public float distance;
		public String frame = "#2a2e38";
		public String lamp = "#fff6e0";
		/** Strength of the light falling from each bank. */
		public int beam = 18;
	}

	/** Stands round the field full of people, cheering on the big moments, camera flashes going off (Champion's Stage). */
	public static final class Crowd {
		/** 0: the field's edge plus 5 blocks. */
		public float distance;
		public int rows = 5;
		public float rowDepth = 1.2f;
		public float rise = 0.8f;
		/** Room per person along a row, in blocks. */
		public float spacing = 0.75f;
		/** Seat colours of each trainer's side, and the clothes people wear. */
		public List<String> sides = List.of("#2a5ad0", "#d03a3a");
		public List<String> clothes = List.of("#f4f0e0", "#2a2a30", "#ffcf4a", "#3a9a5a", "#8a5ad0", "#e07a3a");
		/** Camera flashes per tick. */
		public float flashes = 0.5f;
	}

	/** Spotlights following each battling Pokémon from the light towers, a pool of light under it. */
	public static final class Followspots {
		public String color = "#fff4d0";
		public int alpha = 40;
	}

	/** Palm tree silhouettes round the field (Synthwave). */
	public static final class Palms {
		public String color = "#1a0628";
		public int count = 10;
		/** Distance beyond the field's edge: [min, max]. */
		public List<Float> ring = List.of(6f, 14f);
		public List<Float> height = List.of(6f, 9f);
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
		/** Fireworks bursting over the field, one after another. */
		public int fireworks;
		/** A wormhole tearing open over the field for a moment (Ultra Space). */
		public boolean wormhole;
		/** Confetti: that many pieces, half falling from above, half shot up from the court's corners. */
		public int confetti;
		/** Rocks coming down from a cave's vault (with a cavern): that many, the vault shaking meanwhile. */
		public int rockfall;
		/** Flame and spark fountains at the court's corners. */
		public boolean pyro;
		/** The crowd cheers (lights up, arms up, jumping) for a few seconds, the LED ribbon flashing. */
		public boolean cheer;
		/** A wave running twice round the stands. */
		public boolean wave;
		/** A burst of camera flashes in the stands. */
		public int cameraFlashes;
		/** A tune: note-block pitches (0.5 to 2; 0 a rest), one every {@code noteEvery} ticks. */
		public List<Float> notes = List.of();
		public String noteSound = "minecraft:block.note_block.bell";
		public int noteEvery = 4;
		public float noteVolume = 0.7f;
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
		/** (2026-11-18) Moments another mod names and plays ({@code BattleAmbiance.moment(name)}): a cave collapsing. */
		public java.util.Map<String, Moment> named = java.util.Map.of();
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
	public Embers embers;
	public Volcano volcano;
	public Caustics caustics;
	public Shafts shafts;
	public Kelp kelp;
	public Lanterns lanterns;
	public Fireworks fireworks;
	public Glints glints;
	public FogWall fogWall;
	public Mirage mirage;
	public Birds birds;
	public StripedSun stripedSun;
	public Palms palms;
	public List<Wormhole> wormholes = List.of();
	public Islands islands;
	public Island island;
	public Cavern cavern;
	public Sea sea;
	public Water water;
	public Land land;
	public Hall hall;
	public Grass grass;
	public Flowers flowers;
	public Blaze blaze;
	public PowerCut powerCut;
	/** How much snow covers a land stadium (0..1): its ground, trees, rocks and ranges whiten. */
	public float snowCover;
	public Light light;
	public List<Prop> props = List.of();
	/** How high above the field a real ceiling still counts for props (default 14, at most 32). */
	public Integer ceilingReach;
	public Court court;
	public LightTowers lightTowers;
	public Crowd crowd;
	public Followspots followspots;
	/** Confetti colours, for a moment's confetti. */
	public List<String> confettiColors = List.of("#ffcf4a", "#f4f0e0", "#2a5ad0", "#d03a3a", "#3ad0a0", "#ff7ae8");
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
	/**
	 * The lasting filter with calm effects on: null keeps {@link #filter}, "none" drops it (one that moves or jumps,
	 * like Corrupted's split colours).
	 */
	public String calmFilter;
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
