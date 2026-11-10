"""Writes Showtime's battle ambiances (phase A2) as assets/cobblemon_battle_showtime/ambiances/*.json.

    python tools/ambiances.py

Every key is optional (see Ambiance.java). Particles, sounds and screen filters are Minecraft's own; a resource pack
can add ambiances of its own (assets/<namespace>/ambiances/<id>.json) with its own sounds and textures.
"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "cobblemon_battle_showtime",
                   "ambiances")

# Screen filters: Showtime's own (tools/filters.py; Minecraft 1.21.1 dropped most of its old ones), and Minecraft's invert.
FILTER = "cobblemon_battle_showtime:shaders/post/"
BLACK_WHITE = FILTER + "black_white.json"
PIXELS = FILTER + "pixels.json"
GLITCH = FILTER + "glitch.json"
GLITCH_SOFT = FILTER + "glitch_soft.json"
WOBBLE = FILTER + "wobble.json"
OLD_TV = FILTER + "old_tv.json"
FILM = FILTER + "film.json"
HAZE = FILTER + "haze.json"
INVERT = "minecraft:shaders/post/invert.json"

AMBIANCES = {
    # Disco (2026-11-10, its own signature): a warm violet night, a mirror ball turning over the field and throwing
    # light dots on the ground, a dance floor of tiles lighting up on the beat, soft searchlights round the edge.
    "disco": {
        "time": {"at": 18000, "speed": 0},
        "sky": {"sky_color": "#1c0a26", "fog_color": "#2a0f30", "strength": 0.85, "stars": 0.15, "hide_clouds": True},
        "spotlights": {"count": 8, "colors": ["#ffd23d", "#ff2fa8", "#c070ff", "#fff4e0"], "sweep": 10,
                       "height": 42, "width": 1.4, "alpha": 45, "ring": 6},
        "mirror_ball": {"height": 9, "size": 1.3, "dots": 48, "colors": ["#ffffff", "#fff2c8", "#ffd0f0"], "speed": 4,
                        "alpha": 140},
        "dance_floor": {"tile": 2, "colors": ["#ff2fa8", "#ffd23d", "#b44bff", "#ff8a3d"], "beat": 10, "alpha": 70},
        "particles": [
            {"type": "minecraft:note", "rate": 0.8, "area": "air", "spread": 0.0},
            {"type": "minecraft:wax_on", "rate": 1.2, "area": "field"},
        ],
        "moments": {
            "send_out": {"particle": "minecraft:firework", "count": 25, "sound": "minecraft:entity.firework_rocket.twinkle"},
            "attack": {"particle": "minecraft:note", "count": 8, "sound": "minecraft:block.note_block.bell", "pitch": 1.4},
            "crit": {"particle": "minecraft:firework", "count": 60, "flash": "#ff2fa8",
                     "sound": "minecraft:entity.firework_rocket.blast"},
            "faint": {"particle": "minecraft:end_rod", "count": 30, "flash": "#c070ff",
                      "sound": "minecraft:entity.firework_rocket.large_blast"},
            "victory": {"particle": "minecraft:totem_of_undying", "count": 120, "flash": "#ffd23d",
                        "sound": "minecraft:ui.toast.challenge_complete"},
        },
    },

    # Meadow (2026-11-10): a golden spring morning. Soft sun rays come down on the sun's side, beyond the field
    # (the old ones fell on the field and blinded everyone); butterflies, petals and pollen.
    "nature": {
        "time": {"at": 1000, "speed": 1},
        "sky": {"sky_color": "#9fd8ff", "fog_color": "#f4e6c0", "strength": 0.45},
        "wind": [0.025, 0.008],
        "spotlights": {"count": 6, "colors": ["#fff0b8", "#ffe2a0", "#fff8d8"], "sweep": 0.6, "height": 42, "width": 2.6,
                       "from_sky": True, "alpha": 22, "ring": 4, "sector": 80, "facing": 300},
        "critters": {"kind": "butterfly", "count": 14, "colors": ["#ffd23d", "#ffffff", "#ff9ad5", "#7fd0ff", "#ffa040"],
                     "radius": 13},
        "particles": [
            {"type": "minecraft:cherry_leaves", "rate": 1.5, "area": "air"},
            {"type": "minecraft:spore_blossom_air", "rate": 1.5, "area": "field"},
            {"type": "minecraft:falling_spore_blossom", "rate": 0.5, "area": "sky"},
        ],
        "moments": {
            "attack": {"particle": "minecraft:happy_villager", "count": 6},
            "crit": {"particle": "minecraft:cherry_leaves", "count": 50, "flash": "#fff6c8",
                     "sound": "minecraft:block.cherry_leaves.break"},
            "faint": {"particle": "minecraft:composter", "count": 25},
            "victory": {"particle": "minecraft:cherry_leaves", "count": 140, "sound": "minecraft:entity.player.levelup"},
        },
    },

    # A calm night: deep blue, every star out, fireflies drifting over the grass.
    "night": {
        "time": {"at": 18000, "speed": 0},
        "sky": {"sky_color": "#0b1a3a", "fog_color": "#0f1d36", "strength": 0.75, "stars": 1.0},
        "aurora": {"colors": ["#3dff9a", "#38e0ff", "#a24bff"], "height": 36, "alpha": 100},
        "shooting_stars": {"rate": 0.05, "color": "#e8f0ff"},
        "moon": {"color": "#eef4ff", "yaw": 140, "pitch": 30, "size": 4},
        "wisps": {"color": "#d8ff70", "accent": "#fff090", "count": 14, "radius": [4, 14], "height": [0.4, 2],
                  "size": 0.3, "blink": True},
        "particles": [
            {"type": "minecraft:white_ash", "rate": 2, "area": "air"},
        ],
        "ambient_sounds": [
            {"id": "minecraft:entity.frog.ambient", "every": [120, 300], "volume": 0.3, "pitch": [0.8, 1.1]},
        ],
        "moments": {
            "crit": {"particle": "minecraft:electric_spark", "count": 40, "flash": "#9fc4ff"},
            "faint": {"particle": "minecraft:glow", "count": 30},
            "victory": {"particle": "minecraft:end_rod", "count": 80, "sound": "minecraft:block.amethyst_block.chime"},
        },
    },

    # A thunderstorm: low black clouds, rain, wind, lightning all around, the screen splitting on a critical hit.
    "storm": {
        "time": {"at": 14500, "speed": 0},
        "sky": {"sky_color": "#2a2733", "fog_color": "#26232e", "strength": 0.9, "fog_start": 10, "fog_end": 90,
                "cloud_color": "#3a3740", "cloud_height": 110, "rain": 1.0, "thunder": 1.0},
        "wind": [0.12, 0.04],
        "lightning": {"every": [60, 180]},
        "ambient_sounds": [
            {"id": "minecraft:entity.lightning_bolt.thunder", "every": [160, 360], "volume": 0.3,
             "pitch": [0.5, 0.7]},
        ],
        "particles": [
            {"type": "minecraft:electric_spark", "rate": 1.5, "area": "field", "spread": 0.2},
            {"type": "minecraft:white_ash", "rate": 6, "area": "air"},
            # Gusts sweeping over the field.
            {"type": "minecraft:cloud", "rate": 1.5, "area": "field", "motion": [0.35, 0.0, 0.1], "spread": 0.05},
            {"type": "minecraft:small_gust", "rate": 0.4, "area": "ring"},
        ],
        "moments": {
            "attack": {"particle": "minecraft:electric_spark", "count": 25},
            "crit": {"lightning": True, "flash": "#fff6a0", "filter": GLITCH, "filter_ticks": 12},
            "faint": {"lightning": True, "flash": "#ffffff"},
            "victory": {"lightning": True, "particle": "minecraft:electric_spark", "count": 120},
        },
    },

    # Haunted: a cold night under a pale green moon, thick mist on the ground, will-o'-wisps wandering round the field,
    # ghosts passing far away, candles floating; now and then the light dies for a moment.
    "spectral": {
        "time": {"at": 18000, "speed": 0},
        "sky": {"sky_color": "#18282a", "fog_color": "#203236", "strength": 0.9, "fog_start": 4, "fog_end": 46,
                "stars": 0, "hide_clouds": True},
        "moon": {"color": "#d6ffe2", "yaw": 210, "pitch": 32, "size": 5},
        "mist": {"color": "#a6c8c0", "alpha": 70, "height": 1.4, "layers": 3, "scale": 5, "speed": 0.35},
        "wisps": {"color": "#5cffd0", "accent": "#3db8ff", "count": 10, "radius": [5, 14], "height": [0.8, 3]},
        "ghosts": {"color": "#d8f4ff", "count": 5, "radius": [22, 32], "alpha": 45, "size": 3},
        "flicker": {"every": [160, 360], "alpha": 0.5, "ticks": 5},
        "floating_blocks": {"block": "minecraft:candle[lit=true,candles=1]", "count": 10, "radius": [7, 16],
                            "height": [1.5, 5], "bob": 0.4, "orbit": 0.6, "flame": True},
        "particles": [
            {"type": "minecraft:soul", "rate": 0.3, "area": "field", "motion": [0, 0.03, 0]},
        ],
        "sound": {"id": "minecraft:ambient.soul_sand_valley.loop", "volume": 0.6},
        "ambient_sounds": [
            {"id": "minecraft:ambient.cave", "every": [300, 600], "volume": 0.4, "pitch": [0.8, 1.0]},
        ],
        "moments": {
            "attack": {"particle": "minecraft:soul_fire_flame", "count": 15},
            "crit": {"particle": "minecraft:sculk_soul", "count": 30, "flash": "#2bff9a",
                     "sound": "minecraft:block.sculk_shrieker.shriek", "volume": 0.5},
            "faint": {"particle": "minecraft:soul", "count": 50, "filter": BLACK_WHITE, "filter_ticks": 50,
                      "sound": "minecraft:ambient.soul_sand_valley.mood"},
            "victory": {"particle": "minecraft:soul_fire_flame", "count": 80},
        },
    },

    # Fairy tale: a pastel sky, a soft double rainbow, the sun racing across the sky, fireflies, soap bubbles, glowing
    # mushrooms round the field, sparkling amethyst floating, cherry petals, chimes.
    "fairy": {
        "time": {"at": 0, "speed": 30},
        # Light blue overhead melting into pink at the horizon (Minecraft blends sky colour into fog colour): lilac
        # between the two.
        "sky": {"sky_color": "#b8d4ff", "fog_color": "#ffcfe8", "strength": 0.6, "cloud_color": "#ffd6ee"},
        "rainbow": {"radius": 34, "alpha": 0.45, "doubled": True},
        "floating_blocks": {"block": "minecraft:amethyst_cluster", "count": 7, "radius": [11, 17], "height": [5, 10],
                            "bob": 0.6, "orbit": 0.6, "sparkle": "minecraft:wax_off"},
        "wisps": {"color": "#fff2a0", "accent": "#ffc8f0", "count": 16, "radius": [4, 15], "height": [0.5, 3],
                  "size": 0.35, "blink": True},
        "bubbles": {"color": "#e8f4ff", "count": 14, "radius": [5, 14], "height": 9, "size": [0.25, 0.6]},
        "mushrooms": {"colors": ["#ff9ad8", "#9ad8ff", "#c8a8ff"], "clumps": 12, "ring": 1.5, "size": 0.5},
        "wind": [0.01, 0.0],
        "particles": [
            {"type": "minecraft:cherry_leaves", "rate": 0.8, "area": "air"},
            {"type": "minecraft:spore_blossom_air", "rate": 0.8, "area": "air"},
        ],
        "ambient_sounds": [
            {"id": "minecraft:block.amethyst_block.chime", "every": [60, 160], "volume": 0.35, "pitch": [0.9, 1.5]},
        ],
        "moments": {
            "send_out": {"particle": "minecraft:wax_on", "count": 30, "sound": "minecraft:block.amethyst_block.chime"},
            "attack": {"particle": "minecraft:wax_off", "count": 12},
            "crit": {"particle": "minecraft:end_rod", "count": 50, "flash": "#ffd1f0",
                     "sound": "minecraft:block.amethyst_cluster.break"},
            "victory": {"particle": "minecraft:totem_of_undying", "count": 120,
                        "sound": "minecraft:ui.toast.challenge_complete"},
        },
    },

    # Deep space: every block of the world disappears (drawing only: the ground still holds the trainers), the battle
    # floats in a black sky full of turning stars; a critical hit warps space.
    "cosmos": {
        "hide_world": True,
        "time": {"at": 18000, "speed": 0},
        "sky": {"sky_color": "#020008", "fog_color": "#020008", "strength": 1.0, "stars": 1.0, "hide_clouds": True},
        # Stars all around, below too: the battle floats in space, over a solar system (2026-11-10: finer stars in
        # several colours, nebulae, the galaxy's band, detailed planets; the white specks in front are gone).
        "backdrop": {"box": False, "radius": 60, "stars": 2600, "spin": 0.3, "star_color": "#dfe6ff", "stars_around": True,
                     "star_size": 0.7, "star_colors": ["#ffffff", "#dfe6ff", "#bcd2ff", "#fff1c8", "#ffd2b0"]},
        "galaxy": {"color": "#b8c4ff", "tilt": 38, "yaw": 20, "alpha": 22, "stars": 900},
        "nebulae": [
            {"color": "#7a2dff", "accent": "#ff4fb8", "yaw": 60, "pitch": 28, "size": 16, "puffs": 18, "alpha": 30},
            {"color": "#1f6bff", "accent": "#3df0d8", "yaw": 200, "pitch": 12, "size": 20, "puffs": 20, "alpha": 26},
            {"color": "#ff6a3d", "accent": "#ffd23d", "yaw": 300, "pitch": -24, "size": 13, "puffs": 14, "alpha": 22},
        ],
        # Meteors: big, trailing fire, crossing above and below.
        "shooting_stars": {"rate": 0.06, "color": "#ffd9a0", "size": 4, "trail": "minecraft:flame", "below": True},
        "planets": [
            {"color": "#ffd25a", "size": 7, "orbit": 0, "height": -45, "speed": 0, "glow": True},
            {"color": "#b5683c", "size": 1.4, "orbit": 18, "height": -40, "speed": 3, "bands": ["#b5683c", "#9a5530", "#c47a48"]},
            {"color": "#3d7bff", "size": 2.4, "orbit": 30, "height": -36, "speed": 1.6, "atmosphere": "#9fd8ff",
             "bands": ["#f0f6ff", "#3d7bff", "#3a9a5a", "#3d7bff", "#2a5ad0", "#3d7bff", "#f0f6ff"]},
            {"color": "#d9b77a", "size": 3.6, "orbit": 44, "height": -30, "speed": 0.8, "ring": "#e8d8b0", "tilt": 0.3,
             "bands": ["#e8d2a0", "#d9b77a", "#c8a060", "#e0c48a", "#b88a50", "#d9b77a", "#e8d2a0"]},
            {"color": "#7fd6ff", "size": 2.8, "orbit": 58, "height": -26, "speed": 0.5, "atmosphere": "#bff0ff",
             "bands": ["#9fe4ff", "#7fd6ff", "#6ac4f0", "#7fd6ff"]},
            # Above too: a banded gas giant with its rings and a small moon passing over the field, a pale far sun.
            {"color": "#c46ad6", "size": 6, "orbit": 50, "height": 34, "speed": -0.4, "ring": "#f0c8ff", "tilt": 0.45,
             "atmosphere": "#e8b0ff",
             "bands": ["#f0d0ff", "#c46ad6", "#a050c0", "#d888e8", "#8a40a8", "#c46ad6", "#e0a8f0", "#b060c8", "#f0d0ff"]},
            {"color": "#d8dde8", "size": 1.6, "orbit": 26, "height": 22, "speed": -1.2,
             "bands": ["#d8dde8", "#b8bec8", "#d8dde8", "#c8ced8"]},
            {"color": "#9fd8ff", "size": 3, "orbit": 70, "height": 48, "speed": 0.2, "glow": True},
        ],
        "aurora": {"colors": ["#6a4bff", "#3d8bff", "#c24bff"], "height": 20, "alpha": 40},
        "particles": [
            {"type": "minecraft:reverse_portal", "rate": 1, "area": "ring"},
        ],
        "sound": {"id": "minecraft:block.beacon.ambient", "volume": 0.5, "pitch": 0.6},
        "moments": {
            "send_out": {"particle": "minecraft:reverse_portal", "count": 40, "sound": "minecraft:block.beacon.activate"},
            "crit": {"flash": "#b9a6ff", "filter": WOBBLE, "filter_ticks": 20,
                     "sound": "minecraft:block.respawn_anchor.charge"},
            "faint": {"particle": "minecraft:portal", "count": 60, "sound": "minecraft:block.beacon.deactivate"},
            "victory": {"particle": "minecraft:end_rod", "count": 150, "sound": "minecraft:block.end_portal.spawn",
                        "volume": 0.5},
        },
    },

    # Old film: black and white with grain and a dark rim, a scratch or a speck of dust now and then, the light breathing.
    "silver_screen": {
        "filter": FILM,
        "film": {"scratches": 0.02, "dust": 0.15, "flicker": 0.05},
        "particles": [{"type": "minecraft:white_ash", "rate": 1.5, "area": "air"}],
        "moments": {
            "crit": {"flash": "#ffffff"},
            "victory": {"flash": "#ffffff", "sound": "minecraft:block.note_block.chime"},
        },
    },

    # Arcade (2026-11-10, rebuilt apart from Disco): a retro video game. A neon grid on the ground with a wave running
    # out, 8-bit sprites (coins, hearts, stars, gems) floating round, a black sky with pixel stars, a light CRT look,
    # and big pixel words on the battle's moments.
    "arcade": {
        "filter": OLD_TV,
        "time": {"at": 18000, "speed": 0},
        "sky": {"sky_color": "#04000c", "fog_color": "#08001a", "strength": 0.95, "hide_clouds": True, "stars": 0.9},
        "neon_grid": {"spacing": 2, "color": "#00ffd5", "accent": "#ff00c8", "alpha": 140, "extent": 1.9, "pulse": 6},
        "sprites": {"shapes": ["coin", "heart", "star", "gem"], "count": 12, "radius": [9, 17], "height": [3, 8],
                    "pixel": 0.13, "bob": 0.4, "orbit": 0.35},
        "particles": [
            {"type": "minecraft:electric_spark", "rate": 1, "area": "ring"},
        ],
        "moments": {
            "send_out": {"particle": "minecraft:firework", "count": 20, "sound": "minecraft:block.note_block.bit",
                         "pitch": 1.5, "text": "READY?", "text_color": "#00ffd5"},
            "attack": {"filter": GLITCH, "filter_ticks": 3, "sound": "minecraft:block.note_block.bit", "text": "HIT!",
                       "text_color": "#fff200"},
            "crit": {"filter": INVERT, "filter_ticks": 5, "flash": "#00ffd5", "particle": "minecraft:firework",
                     "count": 40, "sound": "minecraft:block.note_block.bit", "pitch": 2.0, "text": "CRITICAL!",
                     "text_color": "#ff00c8"},
            "faint": {"filter": GLITCH, "filter_ticks": 16, "sound": "minecraft:entity.item.break", "text": "K.O.!",
                      "text_color": "#ff3050"},
            "victory": {"particle": "minecraft:firework", "count": 120, "flash": "#fff200",
                        "sound": "minecraft:ui.toast.challenge_complete", "text": "YOU WIN!", "text_color": "#fff200"},
            "defeat": {"filter": GLITCH, "filter_ticks": 20, "sound": "minecraft:block.note_block.bit", "pitch": 0.5,
                       "text": "GAME OVER", "text_color": "#ff3050"},
        },
    },

    # Corrupted: a world breaking apart, readable: a desaturated violet night, glowing cracks across the sky, veins
    # crawling on the ground from the rifts, black cubes glitching round the field, a violet mist.
    "corrupted": {
        "filter": GLITCH_SOFT,
        # Calm effects: no lasting split, its jumping bands are what a photosensitive player must be spared.
        "calm_filter": "none",
        "rifts": {"count": 5, "color": "#ff2a8a", "height": 8, "width": 1.8, "cycle": 200,
                  "particle": "minecraft:reverse_portal"},
        "time": {"at": 18000, "speed": 0},
        "sky": {"sky_color": "#2b0a35", "fog_color": "#3a1040", "strength": 0.85, "fog_start": 6, "fog_end": 56,
                "stars": 0, "hide_clouds": True},
        "sky_cracks": {"color": "#ff3070", "count": 6, "alpha": 200, "size": 16},
        "veins": {"color": "#ff2a8a", "alpha": 170, "branches": 3, "length": 11, "pulse": 1},
        "glitch_cubes": {"color": "#ff2a8a", "count": 8, "size": [0.6, 1.5], "radius": [6, 15], "height": [2, 7],
                         "jump": 50},
        "mist": {"color": "#6a2a8a", "alpha": 55, "height": 0.8, "layers": 2, "scale": 6, "speed": 0.5},
        "particles": [
            {"type": "minecraft:portal", "rate": 1.5, "area": "field"},
            {"type": "minecraft:crimson_spore", "rate": 1, "area": "air"},
        ],
        "sound": {"id": "minecraft:ambient.crimson_forest.loop", "volume": 0.6},
        "moments": {
            "attack": {"filter": PIXELS, "filter_ticks": 5},
            "crit": {"filter": WOBBLE, "filter_ticks": 16, "flash": "#ff2a8a",
                     "sound": "minecraft:entity.warden.sonic_boom", "volume": 0.4},
            "faint": {"filter": INVERT, "filter_ticks": 10, "particle": "minecraft:sculk_soul", "count": 40},
            "victory": {"filter": OLD_TV, "filter_ticks": 60, "sound": "minecraft:block.end_portal.spawn",
                        "volume": 0.4},
        },
    },
    # Inferno (2026-11-10): a red-black dusk, lava cracks glowing across the floor, embers rising, ash falling, a
    # volcano smouldering on the horizon, the air shimmering with heat.
    "inferno": {
        "filter": HAZE,
        "calm_filter": "none",
        "time": {"at": 13500, "speed": 0},
        "sky": {"sky_color": "#3a0c06", "fog_color": "#5a1a0a", "strength": 0.9, "fog_start": 8, "fog_end": 72,
                "stars": 0, "hide_clouds": True},
        "volcano": {"color": "#ff5a10", "yaw": 40, "size": 16},
        "veins": {"color": "#ff4a08", "core": "#ffd060", "alpha": 190, "branches": 2, "length": 7, "pulse": 0.6,
                  "scatter": 9},
        "embers": {"color": "#ffc040", "accent": "#ff3010", "count": 45, "radius": [3, 16], "height": 10, "size": 0.1,
                   "rise": 1.1},
        "particles": [
            {"type": "minecraft:ash", "rate": 2, "area": "air"},
            {"type": "minecraft:lava", "rate": 0.12, "area": "ring"},
        ],
        "sound": {"id": "minecraft:ambient.basalt_deltas.loop", "volume": 0.6},
        "ambient_sounds": [
            {"id": "minecraft:block.lava.ambient", "every": [80, 200], "volume": 0.5, "pitch": [0.8, 1.1]},
        ],
        "moments": {
            "attack": {"particle": "minecraft:small_flame", "count": 15},
            "crit": {"particle": "minecraft:lava", "count": 25, "flash": "#ff7a20",
                     "sound": "minecraft:entity.blaze.shoot", "volume": 0.6},
            "faint": {"particle": "minecraft:large_smoke", "count": 40, "sound": "minecraft:block.fire.extinguish"},
            "victory": {"particle": "minecraft:flame", "count": 100, "sound": "minecraft:item.firecharge.use"},
        },
    },

    # Abyss (2026-11-10): the sea floor. Deep blue-green water, caustics dancing on the ground, shafts of light slanting
    # down, kelp swaying round the field, bubbles and glowing plankton drifting up, the muffled sound of the deep.
    "abyss": {
        "filter": HAZE,
        "calm_filter": "none",
        "time": {"at": 6000, "speed": 0},
        "sky": {"sky_color": "#0a4a66", "fog_color": "#0b3d55", "strength": 1.0, "fog_start": 2, "fog_end": 44,
                "stars": 0, "hide_clouds": True},
        "caustics": {"color": "#a8f4ff", "alpha": 70, "scale": 1, "speed": 0.8},
        "shafts": {"color": "#bff4ff", "count": 8, "alpha": 28, "width": 2.2, "height": 34, "tilt": 14, "ring": [3, 20]},
        "kelp": {"color": "#1f6a48", "count": 28, "ring": [1.5, 8], "height": [4, 9]},
        "bubbles": {"color": "#d8fbff", "count": 18, "radius": [3, 15], "height": 12, "size": [0.08, 0.25]},
        "embers": {"color": "#9ffcff", "accent": "#3a9cff", "count": 30, "radius": [2, 15], "height": 8, "size": 0.05,
                   "rise": 0.15},
        "sound": {"id": "minecraft:ambient.underwater.loop", "volume": 0.7},
        "ambient_sounds": [
            {"id": "minecraft:ambient.underwater.loop.additions", "every": [100, 240], "volume": 0.6,
             "pitch": [0.9, 1.1]},
            {"id": "minecraft:ambient.underwater.loop.additions.rare", "every": [400, 800], "volume": 0.6},
        ],
        "moments": {
            "attack": {"particle": "minecraft:bubble_pop", "count": 20},
            "crit": {"particle": "minecraft:nautilus", "count": 40, "flash": "#7ff4ff",
                     "sound": "minecraft:block.conduit.attack.target", "volume": 0.6},
            "faint": {"particle": "minecraft:squid_ink", "count": 30},
            "victory": {"particle": "minecraft:glow_squid_ink", "count": 60, "sound": "minecraft:block.conduit.activate"},
        },
    },

    # Sakura Festival (2026-11-10): a spring dusk, cherry petals everywhere, paper lanterns rising into the sky,
    # fireworks bursting far off now and then, and over the field on victory.
    "sakura": {
        "time": {"at": 12600, "speed": 0},
        "sky": {"sky_color": "#5a4a8a", "fog_color": "#ff9a7a", "strength": 0.55, "cloud_color": "#ffb8a0"},
        "lanterns": {"colors": ["#ffb060", "#ff8a60", "#ffd080"], "count": 14, "radius": [6, 22], "height": 30,
                     "size": 0.45, "rise": 0.35},
        "fireworks": {"every": [140, 300], "distance": [28, 40], "height": [18, 28],
                      "colors": ["#ff6a8a", "#ffd060", "#7ad8ff", "#ffffff", "#c88aff"]},
        "wind": [0.02, 0.005],
        "particles": [
            {"type": "minecraft:cherry_leaves", "rate": 2.2, "area": "air"},
            {"type": "minecraft:cherry_leaves", "rate": 0.8, "area": "ring"},
        ],
        "moments": {
            "send_out": {"particle": "minecraft:cherry_leaves", "count": 30},
            "attack": {"particle": "minecraft:cherry_leaves", "count": 12},
            "crit": {"particle": "minecraft:cherry_leaves", "count": 60, "flash": "#ffc0d8",
                     "sound": "minecraft:entity.firework_rocket.twinkle"},
            "faint": {"particle": "minecraft:cherry_leaves", "count": 30},
            "victory": {"fireworks": 8, "particle": "minecraft:cherry_leaves", "count": 80,
                        "sound": "minecraft:entity.firework_rocket.launch"},
        },
    },
    # Blizzard (2026-11-10): a daytime whiteout. Snow driven by the wind, blowing over the ground, ice crystals glinting
    # in the air, a wall of icy fog round the field hiding the world, the wind howling.
    "blizzard": {
        "time": {"at": 6000, "speed": 0},
        "sky": {"sky_color": "#cfe0f0", "fog_color": "#e4eef8", "strength": 0.9, "fog_start": 4, "fog_end": 40,
                "cloud_color": "#d8e4f0"},
        "wind": [0.25, 0.08],
        "fog_wall": {"color": "#e4eef8", "height": 12, "alpha": 150, "speed": 1},
        "mist": {"color": "#f4f8ff", "alpha": 55, "height": 1.6, "layers": 3, "scale": 4, "speed": 2.2},
        "glints": {"color": "#e8f8ff", "count": 24, "radius": [2, 14], "height": [0.5, 6], "size": 0.35},
        "particles": [
            {"type": "minecraft:snowflake", "rate": 5, "area": "air", "motion": [0.3, -0.05, 0.1], "spread": 0.05},
            {"type": "minecraft:snowflake", "rate": 2, "area": "field", "motion": [0.35, 0.0, 0.12], "spread": 0.04},
        ],
        "sound": {"id": "minecraft:item.elytra.flying", "volume": 0.25, "pitch": 0.7},
        "ambient_sounds": [
            {"id": "minecraft:entity.breeze.idle_air", "every": [100, 240], "volume": 0.4, "pitch": [0.6, 0.8]},
        ],
        "moments": {
            "attack": {"particle": "minecraft:snowflake", "count": 20},
            "crit": {"particle": "minecraft:item_snowball", "count": 40, "flash": "#e0f4ff",
                     "sound": "minecraft:block.glass.break", "volume": 0.5},
            "faint": {"particle": "minecraft:snowflake", "count": 50, "sound": "minecraft:block.powder_snow.break"},
            "victory": {"particle": "minecraft:snowflake", "count": 120, "sound": "minecraft:block.amethyst_block.chime"},
        },
    },

    # Mirage (2026-11-10): the desert at noon. A huge pale sun, the air shimmering with heat, sand blowing low over the
    # ground, a shimmering mirage of water all round the horizon, birds circling high.
    "mirage": {
        "filter": HAZE,
        "calm_filter": "none",
        "time": {"at": 6000, "speed": 0},
        "sky": {"sky_color": "#cfe3f2", "fog_color": "#f0d8a8", "strength": 0.8, "fog_start": 10, "fog_end": 90,
                "hide_clouds": True},
        "moon": {"color": "#fff8e0", "yaw": 230, "pitch": 40, "size": 9, "seas": False, "halo": 5, "halo_alpha": 120},
        "mirage": {"color": "#bfe0ff", "alpha": 110, "height": 3},
        "mist": {"color": "#e8c890", "alpha": 45, "height": 0.8, "layers": 2, "scale": 5, "speed": 1.6},
        "birds": {"color": "#2a2018", "count": 4, "radius": [10, 22], "height": [18, 28], "size": 0.9},
        "wind": [0.15, 0.04],
        "particles": [
            {"type": "minecraft:dust_plume", "rate": 0.5, "area": "ring", "motion": [0.3, 0.02, 0.1], "spread": 0.05},
        ],
        "sound": {"id": "minecraft:item.elytra.flying", "volume": 0.12, "pitch": 0.6},
        "moments": {
            "attack": {"particle": "minecraft:dust_plume", "count": 15},
            "crit": {"particle": "minecraft:dust_plume", "count": 40, "flash": "#fff0c0",
                     "sound": "minecraft:block.sand.break"},
            "faint": {"particle": "minecraft:dust_plume", "count": 40},
            "victory": {"particle": "minecraft:wax_on", "count": 80, "sound": "minecraft:entity.player.levelup"},
        },
    },

    # Synthwave (2026-11-10): a retro-futuristic sunset, smooth 80s rather than Arcade's 8-bit. A striped sun on the
    # horizon, a neon grid running out to the distance, palm silhouettes, neon fireworks on victory.
    "synthwave": {
        "time": {"at": 13000, "speed": 0},
        "sky": {"sky_color": "#2a0a4a", "fog_color": "#ff4a8a", "strength": 1.0, "fog_start": 20, "fog_end": 110,
                "stars": 0.6, "hide_clouds": True},
        "striped_sun": {"top": "#ffe14a", "bottom": "#ff3a8a", "yaw": 180, "size": 14, "stripes": 6},
        "neon_grid": {"spacing": 3, "color": "#ff2ad4", "accent": "#2af4ff", "alpha": 150, "extent": 4, "pulse": 8},
        "palms": {"color": "#1a0628", "count": 10, "ring": [6, 14], "height": [6, 9]},
        "fireworks": {"every": [], "colors": ["#ff2ad4", "#2af4ff", "#ffe14a", "#b04aff"]},
        "moments": {
            "attack": {"particle": "minecraft:end_rod", "count": 10},
            "crit": {"particle": "minecraft:electric_spark", "count": 40, "flash": "#ff4af0",
                     "sound": "minecraft:block.beacon.power_select", "volume": 0.6},
            "faint": {"particle": "minecraft:end_rod", "count": 30},
            "victory": {"fireworks": 6, "particle": "minecraft:end_rod", "count": 60,
                        "sound": "minecraft:block.beacon.activate"},
        },
    },
}

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for name, ambiance in AMBIANCES.items():
        with open(os.path.join(OUT, name + ".json"), "w", encoding="utf-8") as file:
            json.dump(ambiance, file, ensure_ascii=False, indent="\t")
            file.write("\n")
    print(len(AMBIANCES), "ambiances:", ", ".join(AMBIANCES))
