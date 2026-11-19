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
        "time": {"at": 18000, "speed": 0},
        "sky": {"sky_color": "#020008", "fog_color": "#020008", "strength": 1.0, "stars": 1.0, "hide_clouds": True},
        # The void, its stars, the galaxy and the planets are the "void" stadium (tools/stadiums.py); the preset
        # "cosmos" puts the two together, as before the split (2026-11-19).
        "nebulae": [
            {"color": "#7a2dff", "accent": "#ff4fb8", "yaw": 60, "pitch": 28, "size": 16, "puffs": 18, "alpha": 30},
            {"color": "#1f6bff", "accent": "#3df0d8", "yaw": 200, "pitch": 12, "size": 20, "puffs": 20, "alpha": 26},
            {"color": "#ff6a3d", "accent": "#ffd23d", "yaw": 300, "pitch": -24, "size": 13, "puffs": 14, "alpha": 22},
        ],
        # Meteors: big, trailing fire, crossing above and below.
        "shooting_stars": {"rate": 0.06, "color": "#ffd9a0", "size": 4, "trail": "minecraft:flame", "below": True},
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
        # Higher and softer (2026-11-10: low and wide, its glow washed out the Pokemon in front of it).
        "moon": {"color": "#fff8e0", "yaw": 230, "pitch": 55, "size": 9, "seas": False, "halo": 3, "halo_alpha": 70},
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
    # Ultra Space (2026-11-10): another dimension. The world disappears; the battle stands on a floating island of
    # violet stone, hexagons of light running across it, crystals round its rim and hanging under it. Round it, islands
    # drift and turn in a starry void lit by alien nebulae, three wormholes spiral slowly on the sky, motes of light
    # float up. A critical hit tears a wormhole open over the field.
    "ultra_space": {
        "time": {"at": 6000, "speed": 0},
        "sky": {"sky_color": "#120a36", "fog_color": "#2a1460", "strength": 1.0, "fog_start": 40, "fog_end": 160,
                "stars": 0, "hide_clouds": True},
        "nebulae": [
            {"color": "#2bf0c8", "accent": "#ff4fd8", "yaw": 40, "pitch": 20, "size": 18, "puffs": 18, "alpha": 26},
            {"color": "#ffd23d", "accent": "#ff7a3d", "yaw": 170, "pitch": -20, "size": 16, "puffs": 16, "alpha": 22},
            {"color": "#5a3dff", "accent": "#3df0ff", "yaw": 280, "pitch": 35, "size": 20, "puffs": 20, "alpha": 26},
        ],
        "wormholes": [
            {"color": "#3df0ff", "accent": "#ff4fd8", "yaw": 30, "pitch": 28, "size": 11, "spin": 0.8, "arms": 5},
            {"color": "#ffd23d", "accent": "#ff6a3d", "yaw": 160, "pitch": 12, "size": 5, "spin": -1.4, "arms": 4},
            {"color": "#7a5aff", "accent": "#3df0c8", "yaw": 260, "pitch": 40, "size": 6.5, "spin": 1.1, "arms": 6},
        ],
        "embers": {"color": "#bff8ff", "accent": "#ff8af0", "count": 50, "radius": [2, 20], "height": 14, "size": 0.07,
                   "rise": 0.25},
        "sound": {"id": "minecraft:block.beacon.ambient", "volume": 0.35, "pitch": 0.6},
        "ambient_sounds": [
            {"id": "minecraft:block.amethyst_block.chime", "every": [80, 200], "volume": 0.5, "pitch": [0.5, 0.9]},
        ],
        "moments": {
            "send_out": {"particle": "minecraft:reverse_portal", "count": 30, "sound": "minecraft:entity.enderman.teleport",
                         "volume": 0.5},
            "attack": {"particle": "minecraft:reverse_portal", "count": 15},
            "crit": {"wormhole": True, "flash": "#9ffcff", "particle": "minecraft:end_rod", "count": 30,
                     "sound": "minecraft:block.beacon.power_select", "volume": 0.7},
            "faint": {"particle": "minecraft:reverse_portal", "count": 50, "sound": "minecraft:entity.enderman.teleport"},
            "victory": {"wormhole": True, "particle": "minecraft:end_rod", "count": 80,
                        "sound": "minecraft:block.end_portal.spawn", "volume": 0.4},
        },
    },

    # Champion's Stage (2026-11-10): a league final at nightfall. A court marked on the floor between the trainers;
    # an arena all round in the dark (tiers under a canopy, seats tinted in each trainer's colour on their side, an LED
    # ribbon on the parapet), its crowd in silhouette lit by the phones and glow sticks they hold up, livelier on the
    # big moments; camera flashes, four light towers, followspots gliding after each Pokemon, searchlights sweeping
    # the sky. Flame fountains on a send-out; on victory confetti cannons, fireworks, a fanfare and a wave round the
    # stands. (The first version's bright blocky people looked cheap.)
    "champion": {
        "time": {"at": 12800, "speed": 0},
        "sky": {"sky_color": "#0a1235", "fog_color": "#1a2a5a", "strength": 0.9, "fog_start": 24, "fog_end": 130,
                "stars": 0.5, "hide_clouds": True},
        "followspots": {"color": "#fff4d0", "alpha": 40},
        "spotlights": {"count": 6, "colors": ["#fff2c0", "#ffd24a", "#9fd0ff"], "sweep": 4, "height": 40, "width": 1.2,
                       "alpha": 28, "ring": 12},
        "fireworks": {"every": [], "colors": ["#ffcf4a", "#ffffff", "#2a5ad0", "#d03a3a"]},
        "confetti_colors": ["#ffcf4a", "#f4f0e0", "#2a5ad0", "#d03a3a", "#3ad0a0", "#ff7ae8"],
        "moments": {
            "send_out": {"pyro": True, "cheer": True, "camera_flashes": 25, "flash": "#ffe9a0"},
            "attack": {"camera_flashes": 6},
            "crit": {"cheer": True, "camera_flashes": 40, "flash": "#fff4d0",
                     "sound": "minecraft:entity.firework_rocket.blast", "volume": 0.5},
            "faint": {"cheer": True, "camera_flashes": 30, "particle": "minecraft:firework", "count": 30},
            "victory": {"confetti": 160, "fireworks": 8, "pyro": True, "cheer": True, "wave": True, "camera_flashes": 60,
                        "notes": [0.53, 0.667, 0.794, 1.059, 0, 0, 0.794, 1.059, 1.335, 1.587, 1.587, 1.587], "note_sound": "minecraft:block.note_block.bell",
                        "note_every": 4},
            # A loss: the crowd still cheers the winner, without the champion's fanfare and confetti.
            "defeat": {"cheer": True, "camera_flashes": 30},
        },
    },

    # --- The caves (2026-11-19, rebuilt to the level of Ultra Space): one family, made to follow one another (Phantasmon
    # Events turns Pokémon Reborn's cave fields into them, and a move turns one into the next). Since the split
    # (2026-11-19, Documentation/stadiums.md) the vault is a stadium ("cave", "volcanic_den"): the ambiances only add
    # props on the surfaces they find (stalactites under a ceiling, crystals on walls, floors, else floating), the
    # light and the stone's tint; with the stadium they make the full cave (presets "grotto", "crystal_grotto",
    # "dark_grotto", "dragon_lair"). "cave_rumble" and "cave_collapse" (Events) bring rocks down from the ceiling.

    # Cave: a great grey-brown hollow lit from the field, stalactites everywhere, water dripping into small ripples,
    # dust drifting in the light, every sound echoing.
    "cave": {
        "time": {"at": 18000, "speed": 0},
        "sky": {"sky_color": "#15120f", "fog_color": "#1e1a15", "strength": 1.0, "fog_start": 14, "fog_end": 70,
                "stars": 0, "hide_clouds": True},
        "light": {"color": "#ffd9a0", "strength": 0.4},
        "props": [
            {"kind": "stalactite", "anchors": ["ceiling"], "count": 90, "size": [1.0, 4.0], "color": "#5a4e42",
             "drips": 1.3},
            {"kind": "stalagmite", "anchors": ["floor"], "count": 34, "size": [0.6, 2.2], "color": "#5a4e42"},
        ],
        "mist": {"color": "#6a5e50", "alpha": 40, "height": 0.9, "layers": 2, "scale": 6, "speed": 0.2},
        "embers": {"color": "#f0e0c0", "accent": "#b0a080", "count": 45, "radius": [2, 16], "height": 10, "size": 0.035,
                   "rise": 0.04},
        "particles": [
            {"type": "minecraft:white_ash", "rate": 1.0, "area": "air"},
        ],
        "sound": {"id": "minecraft:ambient.basalt_deltas.loop", "volume": 0.25},
        "ambient_sounds": [
            {"id": "minecraft:ambient.cave", "every": [240, 520], "volume": 0.5, "pitch": [0.8, 1.0]},
            {"id": "minecraft:block.pointed_dripstone.drip_water", "every": [20, 60], "volume": 0.6,
             "pitch": [0.8, 1.2]},
        ],
        "moments": {
            "attack": {"particle": "minecraft:white_ash", "count": 25},
            "crit": {"rockfall": 3, "particle": "minecraft:poof", "count": 25, "flash": "#d8c8a8",
                     "sound": "minecraft:block.pointed_dripstone.land", "volume": 0.8},
            "faint": {"particle": "minecraft:large_smoke", "count": 30, "sound": "minecraft:block.stone.break",
                      "pitch": 0.6},
            "victory": {"particle": "minecraft:poof", "count": 60, "sound": "minecraft:block.stone.place",
                        "pitch": 0.5},
            "named": {
                "cave_rumble": {"rockfall": 8, "particle": "minecraft:white_ash", "count": 80, "filter": WOBBLE,
                                "filter_ticks": 14, "sound": "minecraft:block.pointed_dripstone.fall", "volume": 0.9,
                                "pitch": 0.6},
                "cave_collapse": {"rockfall": 45, "particle": "minecraft:large_smoke", "count": 120, "flash": "#8a7a66",
                                  "filter": WOBBLE, "filter_ticks": 40, "sound": "minecraft:entity.generic.explode",
                                  "volume": 0.8, "pitch": 0.5},
            },
        },
    },

    # Crystal Cavern: the same vault, crystals of every colour grown out of its walls, its roof and its floor, their
    # light washing the rock; prism beams falling onto the field, changing colour; chimes.
    "crystal_cavern": {
        "time": {"at": 18000, "speed": 0},
        "sky": {"sky_color": "#1a1630", "fog_color": "#1e1a36", "strength": 1.0, "fog_start": 14, "fog_end": 72,
                "stars": 0, "hide_clouds": True},
        "light": {"color": "#c8e0ff", "strength": 0.55, "rock": "#4a4468", "floor": "#3a3556"},
        "props": [
            {"kind": "crystal", "anchors": ["ceiling", "wall", "floor", "float"], "count": 46, "size": [1.2, 4.2],
             "colors": ["#8ad8ff", "#ff9af0", "#b0ff9a", "#fff08a", "#c0a0ff"], "glow": 1.1, "pulse": 1, "beams": 8},
            {"kind": "stalactite", "anchors": ["ceiling"], "count": 60, "size": [1.0, 3.6], "color": "#4a4468",
             "drips": 0.8, "drip": "#e0f0ff"},
            {"kind": "stalagmite", "anchors": ["floor"], "count": 20, "size": [0.6, 2.0], "color": "#4a4468"},
        ],
        "veins": {"color": "#8ad8ff", "core": "#ffffff", "alpha": 110, "branches": 3, "length": 8, "pulse": 0.5,
                  "scatter": 8},
        "embers": {"color": "#ffb8f0", "accent": "#8af0ff", "count": 50, "radius": [2, 16], "height": 10, "size": 0.05,
                   "rise": 0.06},
        "particles": [
            {"type": "minecraft:end_rod", "rate": 0.25, "area": "air", "motion": [0, 0.01, 0]},
        ],
        "sound": {"id": "minecraft:ambient.basalt_deltas.loop", "volume": 0.2},
        "ambient_sounds": [
            {"id": "minecraft:block.amethyst_block.chime", "every": [30, 90], "volume": 0.8, "pitch": [0.7, 1.4]},
            {"id": "minecraft:block.pointed_dripstone.drip_water", "every": [40, 100], "volume": 0.5},
        ],
        "moments": {
            "attack": {"particle": "minecraft:wax_off", "count": 20, "sound": "minecraft:block.amethyst_block.hit",
                       "volume": 0.6},
            "crit": {"rockfall": 3, "particle": "minecraft:end_rod", "count": 40, "flash": "#c8e8ff",
                     "sound": "minecraft:block.amethyst_cluster.break", "volume": 0.8},
            "faint": {"particle": "minecraft:wax_off", "count": 40, "sound": "minecraft:block.amethyst_block.break"},
            "victory": {"particle": "minecraft:end_rod", "count": 80, "sound": "minecraft:block.amethyst_block.resonate",
                        "volume": 0.9},
            "named": {
                "cave_rumble": {"rockfall": 8, "particle": "minecraft:wax_off", "count": 80, "filter": WOBBLE,
                                "filter_ticks": 14, "sound": "minecraft:block.amethyst_cluster.fall", "volume": 0.9,
                                "pitch": 0.6},
                "cave_collapse": {"rockfall": 45, "particle": "minecraft:large_smoke", "count": 120, "flash": "#b0a8e8",
                                  "filter": WOBBLE, "filter_ticks": 40,
                                  "sound": "minecraft:block.amethyst_cluster.break", "volume": 1.0, "pitch": 0.5},
            },
        },
    },

    # Dark Crystal Cavern: the crystals gone black, a violet light running slowly round the cave from cluster to
    # cluster and dying between, the rock barely seen, sculk murmuring in the deep.
    "dark_crystal_cavern": {
        "time": {"at": 18000, "speed": 0},
        "sky": {"sky_color": "#07050c", "fog_color": "#0a0710", "strength": 1.0, "fog_start": 10, "fog_end": 60,
                "stars": 0, "hide_clouds": True},
        "light": {"color": "#7a40ff", "strength": 0.45, "rock": "#2a2238", "floor": "#1e1828"},
        "props": [
            {"kind": "crystal", "anchors": ["ceiling", "wall", "floor", "float"], "count": 42, "size": [1.4, 4.6],
             "colors": ["#6a2ad0", "#3a1a8a", "#a060ff", "#2a0a6a"], "glow": 1.3, "pulse": 1, "wave": True,
             "beams": 5},
            {"kind": "stalactite", "anchors": ["ceiling"], "count": 60, "size": [1.0, 3.6], "color": "#2a2238",
             "drips": 0.5, "drip": "#b080ff"},
            {"kind": "stalagmite", "anchors": ["floor"], "count": 20, "size": [0.6, 2.0], "color": "#2a2238"},
        ],
        "veins": {"color": "#5a2aa0", "core": "#b080ff", "alpha": 110, "branches": 3, "length": 9, "pulse": 0.3,
                  "scatter": 8},
        "mist": {"color": "#24183a", "alpha": 70, "height": 1.2, "layers": 3, "scale": 5, "speed": 0.2},
        "flicker": {"every": [200, 420], "alpha": 0.45, "ticks": 6},
        "particles": [
            {"type": "minecraft:reverse_portal", "rate": 0.4, "area": "field"},
        ],
        "sound": {"id": "minecraft:ambient.soul_sand_valley.loop", "volume": 0.35},
        "ambient_sounds": [
            {"id": "minecraft:block.sculk_sensor.clicking", "every": [160, 360], "volume": 0.4, "pitch": [0.6, 0.9]},
            {"id": "minecraft:block.amethyst_block.chime", "every": [120, 300], "volume": 0.4, "pitch": [0.5, 0.7]},
        ],
        "moments": {
            "attack": {"particle": "minecraft:reverse_portal", "count": 20},
            "crit": {"rockfall": 3, "particle": "minecraft:sculk_soul", "count": 30, "flash": "#7a40ff",
                     "sound": "minecraft:block.amethyst_cluster.break", "volume": 0.7, "pitch": 0.6},
            "faint": {"particle": "minecraft:squid_ink", "count": 30, "sound": "minecraft:block.sculk.break"},
            "victory": {"particle": "minecraft:reverse_portal", "count": 80,
                        "sound": "minecraft:block.amethyst_block.resonate", "pitch": 0.6},
            "named": {
                "cave_rumble": {"rockfall": 8, "particle": "minecraft:reverse_portal", "count": 80, "filter": WOBBLE,
                                "filter_ticks": 14, "sound": "minecraft:block.amethyst_cluster.fall", "pitch": 0.5},
                "cave_collapse": {"rockfall": 45, "particle": "minecraft:large_smoke", "count": 120, "flash": "#4a2a7a",
                                  "filter": WOBBLE, "filter_ticks": 40, "sound": "minecraft:entity.generic.explode",
                                  "volume": 0.8, "pitch": 0.5},
            },
        },
    },

    # Dragon's Den: the vault over a lake of lava, the field an island in it. Lava flowing and crusting, lavafalls
    # pouring down the walls, gold heaps glinting on the shore, a dragon's skeleton sunk in the lava with embers in its
    # eyes, heat shimmering, a growl in the deep.
    "dragons_den": {
        "filter": HAZE,
        "calm_filter": "none",
        "time": {"at": 18000, "speed": 0},
        "sky": {"sky_color": "#1a0806", "fog_color": "#2a0e08", "strength": 1.0, "fog_start": 14, "fog_end": 72,
                "stars": 0, "hide_clouds": True},
        "light": {"color": "#ff7a30", "strength": 0.6, "rock": "#4a2418", "floor": "#3a1c12"},
        "props": [
            {"kind": "stalactite", "anchors": ["ceiling"], "count": 60, "size": [1.0, 4.0], "color": "#4a2418"},
        ],
        "embers": {"color": "#ffc040", "accent": "#ff3010", "count": 60, "radius": [3, 18], "height": 12, "size": 0.09,
                   "rise": 0.9},
        "particles": [
            {"type": "minecraft:lava", "rate": 0.2, "area": "ring"},
            {"type": "minecraft:smoke", "rate": 0.8, "area": "ring", "motion": [0, 0.04, 0]},
        ],
        "sound": {"id": "minecraft:ambient.basalt_deltas.loop", "volume": 0.5},
        "ambient_sounds": [
            {"id": "minecraft:block.lava.ambient", "every": [40, 120], "volume": 0.6, "pitch": [0.8, 1.1]},
            {"id": "minecraft:block.lava.pop", "every": [20, 60], "volume": 0.5, "pitch": [0.7, 1.0]},
            {"id": "minecraft:entity.ender_dragon.growl", "every": [600, 1200], "volume": 0.25, "pitch": [0.5, 0.7]},
        ],
        "moments": {
            "attack": {"particle": "minecraft:small_flame", "count": 15},
            "crit": {"rockfall": 3, "particle": "minecraft:dragon_breath", "count": 40, "flash": "#ff7a20",
                     "sound": "minecraft:entity.ender_dragon.flap", "volume": 0.7},
            "faint": {"particle": "minecraft:large_smoke", "count": 40, "sound": "minecraft:block.fire.extinguish"},
            "victory": {"particle": "minecraft:dragon_breath", "count": 90, "sound": "minecraft:entity.ender_dragon.growl",
                        "volume": 0.5},
            "named": {
                "cave_rumble": {"rockfall": 8, "particle": "minecraft:smoke", "count": 80, "filter": WOBBLE,
                                "filter_ticks": 14, "sound": "minecraft:block.pointed_dripstone.fall", "volume": 0.9,
                                "pitch": 0.6},
                "cave_collapse": {"rockfall": 45, "particle": "minecraft:large_smoke", "count": 120, "flash": "#ff6a2a",
                                  "filter": WOBBLE, "filter_ticks": 40, "sound": "minecraft:entity.generic.explode",
                                  "volume": 0.8, "pitch": 0.5},
            },
        },
    },

    # --- The waters (2026-11-19): the open sea, under it, the sea polluted, the sea frozen. A move turns one into
    # another (Dive goes under, Sludge Wave pollutes, Blizzard freezes, Scald thaws). Named moments are Phantasmon
    # Events' field events, played on the ambiance in place when it has one.

    # Water Surface: the open sea at noon. Sunlight dancing on the water, spray blowing over the waves, gulls circling,
    # waves lapping all round.
    "water_surface": {
        "time": {"at": 5000, "speed": 0},
        "sky": {"sky_color": "#7cc4ff", "fog_color": "#bfe6ff", "strength": 0.7, "fog_start": 16, "fog_end": 110,
                "cloud_color": "#ffffff"},
        "caustics": {"color": "#e8fbff", "alpha": 55, "scale": 1.4, "speed": 0.6},
        "mist": {"color": "#e8f6ff", "alpha": 30, "height": 0.6, "layers": 2, "scale": 6, "speed": 1.2},
        "glints": {"color": "#ffffff", "count": 22, "radius": [3, 16], "height": [0.1, 0.4], "size": 0.3},
        "birds": {"color": "#f4f4f0", "count": 5, "radius": [10, 22], "height": [12, 22], "size": 0.8},
        "wind": [0.06, 0.02],
        "particles": [
            {"type": "minecraft:splash", "rate": 2.5, "area": "ring"},
            {"type": "minecraft:bubble_pop", "rate": 0.6, "area": "field"},
        ],
        "sound": {"id": "minecraft:block.water.ambient", "volume": 0.5, "pitch": 0.8},
        "ambient_sounds": [
            {"id": "minecraft:entity.generic.splash", "every": [60, 160], "volume": 0.3, "pitch": [0.6, 0.9]},
            {"id": "minecraft:entity.dolphin.ambient_water", "every": [400, 900], "volume": 0.3},
        ],
        "moments": {
            "send_out": {"particle": "minecraft:splash", "count": 40, "sound": "minecraft:entity.player.splash",
                         "volume": 0.5},
            "attack": {"particle": "minecraft:splash", "count": 25},
            "crit": {"particle": "minecraft:bubble_pop", "count": 50, "flash": "#bff0ff",
                     "sound": "minecraft:entity.player.splash.high_speed", "volume": 0.6},
            "faint": {"particle": "minecraft:bubble", "count": 40, "sound": "minecraft:entity.generic.splash"},
            "victory": {"particle": "minecraft:splash", "count": 120, "sound": "minecraft:entity.dolphin.play"},
            "named": {
                "water_polluted": {"particle": "minecraft:squid_ink", "count": 60, "flash": "#4a5a2a",
                                   "sound": "minecraft:entity.slime.squish", "pitch": 0.6},
                "water_absorbed": {"particle": "minecraft:splash", "count": 30},
            },
        },
    },

    # Underwater: the shallows under the waves, bright and clear (the Abyss is the deep): sunbeams slanting down,
    # caustics on the sand, seaweed swaying, bubbles and plankton rising.
    "underwater": {
        "filter": HAZE,
        "calm_filter": "none",
        "time": {"at": 6000, "speed": 0},
        "sky": {"sky_color": "#2aa0d8", "fog_color": "#1f86b8", "strength": 1.0, "fog_start": 4, "fog_end": 60,
                "stars": 0, "hide_clouds": True},
        "caustics": {"color": "#d8fcff", "alpha": 85, "scale": 1, "speed": 0.9},
        "shafts": {"color": "#e0fbff", "count": 10, "alpha": 34, "width": 2, "height": 30, "tilt": 10, "ring": [2, 18]},
        "kelp": {"color": "#3a9a5a", "count": 22, "ring": [1.5, 8], "height": [2, 6]},
        "bubbles": {"color": "#e8fdff", "count": 24, "radius": [2, 14], "height": 14, "size": [0.08, 0.3]},
        "embers": {"color": "#e8ffff", "accent": "#9fe8ff", "count": 26, "radius": [2, 14], "height": 9, "size": 0.04,
                   "rise": 0.12},
        "particles": [
            {"type": "minecraft:bubble_column_up", "rate": 0.6, "area": "ring"},
        ],
        "sound": {"id": "minecraft:ambient.underwater.loop", "volume": 0.6},
        "ambient_sounds": [
            {"id": "minecraft:ambient.underwater.loop.additions", "every": [80, 200], "volume": 0.6, "pitch": [1.0, 1.2]},
            {"id": "minecraft:entity.dolphin.ambient_water", "every": [300, 700], "volume": 0.3},
        ],
        "moments": {
            "send_out": {"particle": "minecraft:bubble", "count": 40, "sound": "minecraft:block.bubble_column.upwards_inside",
                         "volume": 0.6},
            "attack": {"particle": "minecraft:bubble_pop", "count": 20},
            "crit": {"particle": "minecraft:bubble", "count": 60, "flash": "#bffcff",
                     "sound": "minecraft:block.conduit.attack.target", "volume": 0.6},
            "faint": {"particle": "minecraft:bubble_column_up", "count": 40, "sound": "minecraft:entity.player.hurt_drown"},
            "victory": {"particle": "minecraft:nautilus", "count": 80, "sound": "minecraft:block.conduit.activate"},
            "named": {
                "water_struggle": {"particle": "minecraft:bubble", "count": 40,
                                   "sound": "minecraft:entity.player.hurt_drown", "volume": 0.5},
                "water_polluted": {"particle": "minecraft:squid_ink", "count": 60, "flash": "#3a4a2a",
                                   "sound": "minecraft:entity.slime.squish", "pitch": 0.6},
            },
        },
    },

    # Murkwater: the sea gone foul. A sickly green-yellow sky, a brown-green haze lying on the water, sludge veins
    # oozing across it, toxic bubbles swelling up and bursting, the water slapping thick and slow.
    "murkwater": {
        "time": {"at": 12000, "speed": 0},
        "sky": {"sky_color": "#6a7a3a", "fog_color": "#4a5230", "strength": 0.9, "fog_start": 6, "fog_end": 54,
                "stars": 0, "cloud_color": "#5a5a3a"},
        "mist": {"color": "#5a6a2a", "alpha": 70, "height": 1.0, "layers": 3, "scale": 5, "speed": 0.4},
        "veins": {"color": "#6a2a8a", "core": "#a8e040", "alpha": 120, "branches": 3, "length": 9, "pulse": 0.35,
                  "scatter": 9},
        "bubbles": {"color": "#a8d860", "count": 16, "radius": [2, 14], "height": 2.5, "size": [0.15, 0.45]},
        "particles": [
            {"type": "minecraft:squid_ink", "rate": 0.25, "area": "ring", "motion": [0, 0.01, 0]},
            {"type": "minecraft:mycelium", "rate": 2, "area": "air"},
        ],
        "sound": {"id": "minecraft:block.water.ambient", "volume": 0.4, "pitch": 0.55},
        "ambient_sounds": [
            {"id": "minecraft:block.bubble_column.bubble_pop", "every": [30, 90], "volume": 0.5, "pitch": [0.5, 0.8]},
            {"id": "minecraft:entity.slime.squish", "every": [120, 300], "volume": 0.3, "pitch": [0.5, 0.7]},
        ],
        "moments": {
            "attack": {"particle": "minecraft:squid_ink", "count": 15},
            "crit": {"particle": "minecraft:item_slime", "count": 40, "flash": "#a8e040",
                     "sound": "minecraft:entity.slime.squish", "volume": 0.8, "pitch": 0.6},
            "faint": {"particle": "minecraft:squid_ink", "count": 40, "sound": "minecraft:entity.generic.splash",
                      "pitch": 0.6},
            "victory": {"particle": "minecraft:item_slime", "count": 80, "sound": "minecraft:block.brewing_stand.brew"},
            "named": {
                "toxic_water": {"particle": "minecraft:item_slime", "count": 30, "sound": "minecraft:entity.slime.squish",
                                "pitch": 0.6},
                "toxic_water_dive": {"particle": "minecraft:squid_ink", "count": 40,
                                     "sound": "minecraft:entity.player.hurt_drown", "volume": 0.5},
                "poison_water_heal": {"particle": "minecraft:happy_villager", "count": 20,
                                      "sound": "minecraft:entity.generic.drink"},
            },
        },
    },

    # Icy Field: a frozen lake under a pale winter sky. Ice blocks jutting from the floe, frost glinting, a thin cold
    # mist creeping over the ice, snow drifting, ice tinkling and creaking.
    "icy": {
        "time": {"at": 4500, "speed": 0},
        "sky": {"sky_color": "#bcd8ec", "fog_color": "#dceaf6", "strength": 0.75, "fog_start": 10, "fog_end": 80,
                "cloud_color": "#e8f0f8"},
        "floating_blocks": {"block": "minecraft:packed_ice", "count": 9, "radius": [9, 17], "height": [0.2, 1.5],
                            "bob": 0.1, "orbit": 0, "sparkle": "minecraft:wax_off"},
        "glints": {"color": "#f0fcff", "count": 30, "radius": [2, 15], "height": [0.05, 1.5], "size": 0.3},
        "mist": {"color": "#eef8ff", "alpha": 45, "height": 0.6, "layers": 2, "scale": 5, "speed": 0.6},
        "wind": [0.05, 0.015],
        "particles": [
            {"type": "minecraft:snowflake", "rate": 1.2, "area": "air", "motion": [0.05, -0.02, 0.02]},
        ],
        "sound": {"id": "minecraft:item.elytra.flying", "volume": 0.1, "pitch": 0.6},
        "ambient_sounds": [
            {"id": "minecraft:block.amethyst_block.chime", "every": [80, 200], "volume": 0.4, "pitch": [1.4, 1.9]},
            {"id": "minecraft:block.glass.step", "every": [160, 360], "volume": 0.4, "pitch": [0.5, 0.7]},
        ],
        "moments": {
            "attack": {"particle": "minecraft:snowflake", "count": 20},
            "crit": {"particle": "minecraft:item_snowball", "count": 40, "flash": "#e8f8ff",
                     "sound": "minecraft:block.glass.break", "volume": 0.6},
            "faint": {"particle": "minecraft:snowflake", "count": 40, "sound": "minecraft:block.powder_snow.break"},
            "victory": {"particle": "minecraft:snowflake", "count": 100,
                        "sound": "minecraft:block.amethyst_block.resonate", "pitch": 1.4},
            "named": {
                "ice_spikes": {"particle": "minecraft:item_snowball", "count": 70, "flash": "#dff6ff",
                               "sound": "minecraft:block.glass.break", "volume": 0.9, "pitch": 0.7},
                "steam": {"particle": "minecraft:cloud", "count": 60, "flash": "#ffffff",
                          "sound": "minecraft:block.fire.extinguish", "volume": 0.7},
            },
        },
    },

    # --- Nature, fire and poison (2026-11-19): the grass and the forest catch fire, poison spreads over the grass,
    # the mist turns corrosive or blows away.

    # Grassy Terrain: a bright green summer field. Tall grass swaying round the field, butterflies, pollen and seeds
    # floating, bees humming by.
    "grassy": {
        "time": {"at": 3000, "speed": 0},
        "sky": {"sky_color": "#8ad0ff", "fog_color": "#d8f0c0", "strength": 0.45},
        "kelp": {"color": "#4aa83a", "count": 34, "ring": [1, 7], "height": [1, 2.2]},
        "critters": {"kind": "butterfly", "count": 10, "colors": ["#ffffff", "#ffd23d", "#a8ff60", "#7fd0ff"],
                     "radius": 12},
        "wind": [0.03, 0.01],
        "particles": [
            {"type": "minecraft:spore_blossom_air", "rate": 1.2, "area": "field"},
            {"type": "minecraft:happy_villager", "rate": 0.3, "area": "field"},
        ],
        "ambient_sounds": [
            {"id": "minecraft:entity.bee.pollinate", "every": [140, 320], "volume": 0.3, "pitch": [0.9, 1.1]},
            {"id": "minecraft:block.grass.step", "every": [80, 200], "volume": 0.3, "pitch": [0.7, 0.9]},
        ],
        "moments": {
            "attack": {"particle": "minecraft:composter", "count": 10},
            "crit": {"particle": "minecraft:happy_villager", "count": 40, "flash": "#d8ffb0",
                     "sound": "minecraft:block.grass.break"},
            "faint": {"particle": "minecraft:composter", "count": 30},
            "victory": {"particle": "minecraft:happy_villager", "count": 100, "sound": "minecraft:entity.player.levelup"},
        },
    },

    # Forest: deep in the woods. A green gloom under the canopy, sunbeams breaking through, ferns all round, fireflies
    # and motes drifting, birds and frogs calling.
    "forest": {
        "time": {"at": 4000, "speed": 0},
        "sky": {"sky_color": "#4a7a3a", "fog_color": "#2e4a24", "strength": 0.9, "fog_start": 6, "fog_end": 52,
                "hide_clouds": True},
        "shafts": {"color": "#fff4b0", "count": 9, "alpha": 26, "width": 1.6, "height": 30, "tilt": 8, "ring": [3, 16]},
        "kelp": {"color": "#2a6a2a", "count": 30, "ring": [1.5, 8], "height": [1.5, 4]},
        "wisps": {"color": "#e8ff70", "accent": "#fff0a0", "count": 12, "radius": [3, 13], "height": [0.5, 3],
                  "size": 0.25, "blink": True},
        "mist": {"color": "#5a7a4a", "alpha": 30, "height": 0.8, "layers": 2, "scale": 6, "speed": 0.3},
        "particles": [
            {"type": "minecraft:composter", "rate": 1, "area": "air", "motion": [0.01, -0.02, 0]},
            {"type": "minecraft:spore_blossom_air", "rate": 0.6, "area": "air"},
        ],
        "ambient_sounds": [
            {"id": "minecraft:entity.parrot.ambient", "every": [120, 300], "volume": 0.3, "pitch": [0.9, 1.3]},
            {"id": "minecraft:entity.frog.ambient", "every": [200, 420], "volume": 0.3, "pitch": [0.8, 1.0]},
        ],
        "moments": {
            "attack": {"particle": "minecraft:composter", "count": 15},
            "crit": {"particle": "minecraft:happy_villager", "count": 40, "flash": "#c8f0a0",
                     "sound": "minecraft:block.azalea_leaves.break"},
            "faint": {"particle": "minecraft:composter", "count": 40, "sound": "minecraft:block.wood.break"},
            "victory": {"particle": "minecraft:happy_villager", "count": 100,
                        "sound": "minecraft:block.amethyst_block.chime"},
            "named": {
                "sap_drunk": {"particle": "minecraft:dripping_honey", "count": 20, "sound": "minecraft:block.honey_block.slide"},
            },
        },
    },

    # Burning Field: a wildfire. A sky choked with orange smoke, fire running across the ground, flames all round,
    # embers rising, the air trembling, the fire roaring and crackling.
    "burning": {
        "filter": HAZE,
        "calm_filter": "none",
        "time": {"at": 11500, "speed": 0},
        "sky": {"sky_color": "#8a3a14", "fog_color": "#7a3010", "strength": 0.95, "fog_start": 6, "fog_end": 60,
                "stars": 0, "hide_clouds": True},
        "veins": {"color": "#ff6a10", "core": "#ffe060", "alpha": 170, "branches": 3, "length": 6, "pulse": 0.9,
                  "scatter": 10},
        "embers": {"color": "#ffd040", "accent": "#ff5010", "count": 60, "radius": [2, 16], "height": 12, "size": 0.09,
                   "rise": 1.3},
        "particles": [
            {"type": "minecraft:flame", "rate": 3, "area": "ring", "motion": [0, 0.03, 0]},
            {"type": "minecraft:campfire_cosy_smoke", "rate": 0.4, "area": "ring"},
            {"type": "minecraft:ash", "rate": 2, "area": "air"},
        ],
        "sound": {"id": "minecraft:block.fire.ambient", "volume": 0.9, "pitch": 0.8},
        "ambient_sounds": [
            {"id": "minecraft:block.campfire.crackle", "every": [20, 60], "volume": 0.7, "pitch": [0.8, 1.2]},
            {"id": "minecraft:block.fire.ambient", "every": [30, 80], "volume": 0.6, "pitch": [0.6, 0.9]},
        ],
        "moments": {
            "attack": {"particle": "minecraft:flame", "count": 20},
            "crit": {"particle": "minecraft:lava", "count": 25, "flash": "#ff8a20",
                     "sound": "minecraft:entity.blaze.shoot", "volume": 0.6},
            "faint": {"particle": "minecraft:large_smoke", "count": 40, "sound": "minecraft:block.fire.extinguish"},
            "victory": {"particle": "minecraft:flame", "count": 120, "sound": "minecraft:item.firecharge.use"},
            "named": {
                "field_burned": {"particle": "minecraft:flame", "count": 70, "flash": "#ff7a20",
                                 "sound": "minecraft:item.firecharge.use", "volume": 0.7},
                "rain_snuffed": {"particle": "minecraft:large_smoke", "count": 50,
                                 "sound": "minecraft:block.fire.extinguish"},
                "sand_snuffed": {"particle": "minecraft:dust_plume", "count": 50,
                                 "sound": "minecraft:block.fire.extinguish"},
            },
        },
    },

    # Super-Heated Field: a scorching plain. A white-hot sun, the air shimmering, steam hissing from cracks in the
    # baked ground, a mirage of heat round the horizon.
    "superheated": {
        "filter": HAZE,
        "calm_filter": "none",
        "time": {"at": 6000, "speed": 0},
        "sky": {"sky_color": "#f0d090", "fog_color": "#f4c88a", "strength": 0.85, "fog_start": 8, "fog_end": 70,
                "hide_clouds": True},
        "moon": {"color": "#fff6d0", "yaw": 200, "pitch": 60, "size": 10, "seas": False, "halo": 4, "halo_alpha": 90},
        "mirage": {"color": "#fff0c8", "alpha": 120, "height": 4},
        "veins": {"color": "#c8501a", "core": "#ffb040", "alpha": 110, "branches": 2, "length": 7, "pulse": 0.4,
                  "scatter": 9},
        "embers": {"color": "#fff0c0", "accent": "#ffb060", "count": 30, "radius": [2, 14], "height": 6, "size": 0.05,
                   "rise": 0.6},
        "particles": [
            {"type": "minecraft:white_smoke", "rate": 1.2, "area": "field", "motion": [0, 0.06, 0]},
            {"type": "minecraft:cloud", "rate": 0.3, "area": "ring", "motion": [0, 0.05, 0]},
        ],
        "sound": {"id": "minecraft:item.elytra.flying", "volume": 0.1, "pitch": 0.5},
        "ambient_sounds": [
            {"id": "minecraft:block.fire.extinguish", "every": [80, 200], "volume": 0.3, "pitch": [0.8, 1.1]},
            {"id": "minecraft:block.lava.ambient", "every": [160, 360], "volume": 0.4},
        ],
        "moments": {
            "attack": {"particle": "minecraft:white_smoke", "count": 20},
            "crit": {"particle": "minecraft:cloud", "count": 40, "flash": "#fff4d0",
                     "sound": "minecraft:block.fire.extinguish", "volume": 0.7},
            "faint": {"particle": "minecraft:large_smoke", "count": 40},
            "victory": {"particle": "minecraft:white_smoke", "count": 100, "sound": "minecraft:entity.player.levelup"},
            "named": {
                "steam": {"particle": "minecraft:cloud", "count": 80, "flash": "#ffffff",
                          "sound": "minecraft:block.fire.extinguish", "volume": 0.9},
                "hail_melted": {"particle": "minecraft:cloud", "count": 50, "sound": "minecraft:block.fire.extinguish"},
            },
        },
    },

    # Corrosive Field: poisoned ground. A violet dusk, sludge veins glowing green across the floor, toxic bubbles
    # welling up and bursting, a low poisonous haze, things fizzing and dripping.
    "corrosive": {
        "time": {"at": 12500, "speed": 0},
        "sky": {"sky_color": "#4a2a5a", "fog_color": "#3a2a40", "strength": 0.85, "fog_start": 8, "fog_end": 64,
                "stars": 0, "cloud_color": "#5a3a6a"},
        "veins": {"color": "#8a2aff", "core": "#b8ff40", "alpha": 160, "branches": 3, "length": 9, "pulse": 0.5,
                  "scatter": 10},
        "bubbles": {"color": "#b8ff60", "count": 18, "radius": [2, 15], "height": 2, "size": [0.15, 0.4]},
        "mist": {"color": "#6a4a8a", "alpha": 45, "height": 0.6, "layers": 2, "scale": 6, "speed": 0.4},
        "particles": [
            {"type": "minecraft:item_slime", "rate": 0.4, "area": "field"},
            {"type": "minecraft:witch", "rate": 0.4, "area": "field"},
        ],
        "sound": {"id": "minecraft:ambient.crimson_forest.loop", "volume": 0.3, "pitch": 0.8},
        "ambient_sounds": [
            {"id": "minecraft:block.bubble_column.bubble_pop", "every": [40, 100], "volume": 0.5, "pitch": [0.6, 0.9]},
            {"id": "minecraft:block.brewing_stand.brew", "every": [200, 420], "volume": 0.4},
        ],
        "moments": {
            "attack": {"particle": "minecraft:witch", "count": 15},
            "crit": {"particle": "minecraft:item_slime", "count": 40, "flash": "#b8ff40",
                     "sound": "minecraft:entity.slime.squish", "volume": 0.8},
            "faint": {"particle": "minecraft:witch", "count": 40, "sound": "minecraft:entity.witch.drink"},
            "victory": {"particle": "minecraft:witch", "count": 100, "sound": "minecraft:block.brewing_stand.brew"},
            "named": {
                "corrosion_seared": {"particle": "minecraft:item_slime", "count": 30, "flash": "#8a2aff",
                                     "sound": "minecraft:block.fire.extinguish", "pitch": 0.6},
                "pelt_corroded": {"particle": "minecraft:item_slime", "count": 30,
                                  "sound": "minecraft:block.fire.extinguish", "pitch": 0.6},
                "foul_roots": {"particle": "minecraft:witch", "count": 30, "sound": "minecraft:entity.generic.drink"},
            },
        },
    },

    # Corrosive Mist: a poisonous fog. A thick violet haze closing in round the field, the world drowned in it, toxic
    # spores and motes drifting, a sour murmur in the air.
    "corrosive_mist": {
        "filter": HAZE,
        "calm_filter": "none",
        "time": {"at": 13000, "speed": 0},
        "sky": {"sky_color": "#5a3a6a", "fog_color": "#6a4a78", "strength": 1.0, "fog_start": 2, "fog_end": 26,
                "stars": 0, "hide_clouds": True},
        "fog_wall": {"color": "#6a4a78", "height": 10, "alpha": 150, "speed": 0.5},
        "mist": {"color": "#8a5aa0", "alpha": 80, "height": 2.4, "layers": 4, "scale": 4, "speed": 0.5},
        "embers": {"color": "#d8a0ff", "accent": "#b8ff60", "count": 40, "radius": [2, 14], "height": 6, "size": 0.05,
                   "rise": 0.08},
        "particles": [
            {"type": "minecraft:witch", "rate": 0.6, "area": "air"},
            {"type": "minecraft:warped_spore", "rate": 2, "area": "air"},
        ],
        "sound": {"id": "minecraft:ambient.warped_forest.loop", "volume": 0.4, "pitch": 0.8},
        "ambient_sounds": [
            {"id": "minecraft:entity.witch.ambient", "every": [400, 900], "volume": 0.2, "pitch": [0.6, 0.8]},
        ],
        "moments": {
            "attack": {"particle": "minecraft:witch", "count": 20},
            "crit": {"particle": "minecraft:witch", "count": 50, "flash": "#c080ff",
                     "sound": "minecraft:entity.witch.throw", "volume": 0.7},
            "faint": {"particle": "minecraft:large_smoke", "count": 40, "sound": "minecraft:entity.witch.drink"},
            "victory": {"particle": "minecraft:witch", "count": 100, "sound": "minecraft:block.brewing_stand.brew"},
            "named": {
                "mist_poisoned": {"particle": "minecraft:witch", "count": 80, "flash": "#a060d0",
                                  "sound": "minecraft:entity.witch.throw", "volume": 0.8},
                "mist_combusted": {"particle": "minecraft:explosion", "count": 12, "flash": "#ff8a30",
                                   "filter": WOBBLE, "filter_ticks": 20, "sound": "minecraft:entity.generic.explode",
                                   "volume": 0.9},
            },
        },
    },

    # Misty Terrain: a fairy mist at dawn. Soft pink fog drifting over the ground, pale lights and sparkles floating
    # in it, everything quiet and dreamy.
    "misty": {
        "time": {"at": 23500, "speed": 0},
        "sky": {"sky_color": "#e0c8f0", "fog_color": "#f4d8ec", "strength": 0.85, "fog_start": 4, "fog_end": 50,
                "cloud_color": "#fff0f8"},
        "mist": {"color": "#ffd8f0", "alpha": 75, "height": 1.8, "layers": 4, "scale": 5, "speed": 0.35},
        "glints": {"color": "#fff0ff", "count": 26, "radius": [2, 14], "height": [0.3, 4], "size": 0.3},
        "wisps": {"color": "#ffc8f0", "accent": "#d8c8ff", "count": 12, "radius": [3, 13], "height": [0.5, 3],
                  "size": 0.3, "blink": True},
        "particles": [
            {"type": "minecraft:white_ash", "rate": 1.5, "area": "air"},
            {"type": "minecraft:wax_off", "rate": 0.4, "area": "field"},
        ],
        "ambient_sounds": [
            {"id": "minecraft:block.amethyst_block.chime", "every": [80, 200], "volume": 0.35, "pitch": [1.1, 1.6]},
        ],
        "moments": {
            "send_out": {"particle": "minecraft:wax_off", "count": 30},
            "attack": {"particle": "minecraft:wax_off", "count": 12},
            "crit": {"particle": "minecraft:end_rod", "count": 40, "flash": "#ffd8f4",
                     "sound": "minecraft:block.amethyst_cluster.break"},
            "faint": {"particle": "minecraft:cloud", "count": 30},
            "victory": {"particle": "minecraft:end_rod", "count": 80, "sound": "minecraft:block.amethyst_block.resonate"},
            "named": {
                "misty_status": {"particle": "minecraft:wax_off", "count": 30,
                                 "sound": "minecraft:block.amethyst_block.chime", "pitch": 1.5},
            },
        },
    },

    # --- Machines (2026-11-19): the factory and the factory broken down (an earthquake shorts it out, an electric
    # move gets it running again).

    # Factory: a working plant at night. Hanging lamps, amber warning lights turning, lit floor panels, steam venting
    # round the edges, machines humming and thumping.
    "factory": {
        "time": {"at": 18000, "speed": 0},
        "sky": {"sky_color": "#1a1e26", "fog_color": "#2a2e38", "strength": 1.0, "fog_start": 10, "fog_end": 60,
                "stars": 0, "hide_clouds": True},
        "floating_blocks": {"block": "minecraft:redstone_lamp[lit=true]", "count": 10, "radius": [7, 15],
                            "height": [7, 8], "bob": 0, "orbit": 0},
        "neon_grid": {"spacing": 3, "color": "#ffb020", "accent": "#40c0ff", "alpha": 80, "extent": 1.6, "pulse": 3},
        "spotlights": {"count": 4, "colors": ["#ffa020", "#ff7010"], "sweep": 30, "height": 14, "width": 1, "alpha": 30,
                       "ring": 14},
        "particles": [
            {"type": "minecraft:white_smoke", "rate": 0.8, "area": "ring", "motion": [0, 0.08, 0]},
            {"type": "minecraft:electric_spark", "rate": 0.3, "area": "ring"},
        ],
        "sound": {"id": "minecraft:block.beacon.ambient", "volume": 0.4, "pitch": 0.5},
        "ambient_sounds": [
            {"id": "minecraft:block.piston.extend", "every": [30, 70], "volume": 0.35, "pitch": [0.6, 0.8]},
            {"id": "minecraft:block.piston.contract", "every": [30, 70], "volume": 0.3, "pitch": [0.6, 0.8]},
            {"id": "minecraft:block.anvil.land", "every": [200, 420], "volume": 0.15, "pitch": [0.5, 0.7]},
        ],
        "moments": {
            "send_out": {"particle": "minecraft:white_smoke", "count": 30, "sound": "minecraft:block.piston.extend"},
            "attack": {"particle": "minecraft:electric_spark", "count": 15},
            "crit": {"particle": "minecraft:electric_spark", "count": 50, "flash": "#ffc040",
                     "sound": "minecraft:block.anvil.land", "volume": 0.5},
            "faint": {"particle": "minecraft:large_smoke", "count": 30, "sound": "minecraft:block.piston.contract"},
            "victory": {"particle": "minecraft:electric_spark", "count": 100,
                        "sound": "minecraft:block.beacon.power_select"},
            "named": {
                "stray_electricity": {"particle": "minecraft:electric_spark", "count": 30,
                                      "sound": "minecraft:block.copper_bulb.turn_on"},
            },
        },
    },

    # Short-Circuit: the factory broken down. Darkness, the lamps flickering, sparks raining from cut cables, blue
    # arcs flashing, the picture glitching, dead machines buzzing.
    "short_circuit": {
        "filter": GLITCH_SOFT,
        "calm_filter": "none",
        "time": {"at": 18000, "speed": 0},
        "sky": {"sky_color": "#0a0c12", "fog_color": "#12161e", "strength": 1.0, "fog_start": 4, "fog_end": 40,
                "stars": 0, "hide_clouds": True},
        "floating_blocks": {"block": "minecraft:redstone_lamp[lit=true]", "count": 6, "radius": [7, 15],
                            "height": [7, 8], "bob": 0, "orbit": 0},
        "flicker": {"every": [40, 120], "alpha": 0.6, "ticks": 4},
        "strobe": {"colors": ["#8ad8ff", "#ffffff"], "every": 70, "alpha": 0.08},
        "veins": {"color": "#3a8aff", "core": "#e0f4ff", "alpha": 140, "branches": 3, "length": 8, "pulse": 2,
                  "scatter": 9},
        "particles": [
            {"type": "minecraft:electric_spark", "rate": 2.5, "area": "air", "motion": [0, -0.1, 0]},
            {"type": "minecraft:smoke", "rate": 0.5, "area": "ring", "motion": [0, 0.04, 0]},
        ],
        "sound": {"id": "minecraft:block.beacon.ambient", "volume": 0.3, "pitch": 0.35},
        "ambient_sounds": [
            {"id": "minecraft:block.redstone_torch.burnout", "every": [40, 120], "volume": 0.4, "pitch": [0.8, 1.2]},
            {"id": "minecraft:block.copper_bulb.turn_off", "every": [60, 160], "volume": 0.4},
        ],
        "moments": {
            "attack": {"particle": "minecraft:electric_spark", "count": 25},
            "crit": {"particle": "minecraft:electric_spark", "count": 60, "flash": "#8ad8ff", "filter": GLITCH,
                     "filter_ticks": 8, "sound": "minecraft:entity.lightning_bolt.impact", "volume": 0.4},
            "faint": {"particle": "minecraft:large_smoke", "count": 30, "sound": "minecraft:block.beacon.deactivate"},
            "victory": {"particle": "minecraft:electric_spark", "count": 120, "sound": "minecraft:block.beacon.activate"},
            # Reborn's sparks after each electric move, weakest to strongest.
            "named": {
                "bzzt0": {"particle": "minecraft:electric_spark", "count": 15,
                          "sound": "minecraft:block.redstone_torch.burnout", "volume": 0.5},
                "bzzt1": {"particle": "minecraft:electric_spark", "count": 30,
                          "sound": "minecraft:block.redstone_torch.burnout", "volume": 0.7},
                "bzzt2": {"particle": "minecraft:electric_spark", "count": 20,
                          "sound": "minecraft:block.redstone_torch.burnout", "volume": 0.5, "pitch": 0.7},
                "bzzt3": {"particle": "minecraft:electric_spark", "count": 45, "flash": "#8ad8ff",
                          "sound": "minecraft:entity.lightning_bolt.impact", "volume": 0.3},
                "bzzt4": {"particle": "minecraft:electric_spark", "count": 90, "flash": "#ffffff", "filter": GLITCH,
                          "filter_ticks": 12, "sound": "minecraft:entity.lightning_bolt.impact", "volume": 0.6},
                "stray_electricity": {"particle": "minecraft:electric_spark", "count": 30,
                                      "sound": "minecraft:block.copper_bulb.turn_on"},
            },
        },
    },

    # --- Mountains (2026-11-19): a blizzard snows the peak in, fire melts it again.

    # Mountain: a high peak on a clear day. A deep blue sky, the air thin and bright, eagles circling, gusts of wind
    # and dust sweeping past, goats calling far off.
    "mountain": {
        "time": {"at": 5000, "speed": 0},
        "sky": {"sky_color": "#4a90e0", "fog_color": "#c8dcf0", "strength": 0.6, "fog_start": 30, "fog_end": 140,
                "cloud_color": "#ffffff"},
        "birds": {"color": "#2a2420", "count": 3, "radius": [12, 24], "height": [16, 26], "size": 1.2},
        "wind": [0.12, 0.04],
        "particles": [
            {"type": "minecraft:small_gust", "rate": 0.3, "area": "ring"},
            {"type": "minecraft:dust_plume", "rate": 0.4, "area": "field", "motion": [0.25, 0.02, 0.08], "spread": 0.04},
            {"type": "minecraft:white_ash", "rate": 1.5, "area": "air", "motion": [0.1, 0, 0.03]},
        ],
        "sound": {"id": "minecraft:item.elytra.flying", "volume": 0.18, "pitch": 0.6},
        "ambient_sounds": [
            {"id": "minecraft:entity.goat.ambient", "every": [300, 700], "volume": 0.3, "pitch": [0.8, 1.0]},
            {"id": "minecraft:entity.breeze.idle_air", "every": [120, 300], "volume": 0.3, "pitch": [0.6, 0.8]},
        ],
        "moments": {
            "attack": {"particle": "minecraft:dust_plume", "count": 15},
            "crit": {"particle": "minecraft:gust", "count": 3, "flash": "#e8f0ff",
                     "sound": "minecraft:entity.breeze.shoot", "volume": 0.6},
            "faint": {"particle": "minecraft:poof", "count": 30, "sound": "minecraft:block.stone.break"},
            "victory": {"particle": "minecraft:cloud", "count": 60, "sound": "minecraft:entity.goat.screaming.ambient",
                        "volume": 0.4},
            "named": {
                "mountain_snow": {"particle": "minecraft:snowflake", "count": 120, "flash": "#ffffff",
                                  "sound": "minecraft:block.powder_snow.break", "volume": 0.9},
            },
        },
    },

    # Snowy Mountain: the peak snowed in. A grey-white sky, snow falling steadily and blown off the ridges, frost
    # glinting, a cold mist on the ground, the wind howling.
    "snowy_mountain": {
        "time": {"at": 5500, "speed": 0},
        "sky": {"sky_color": "#b8c8d8", "fog_color": "#e0e8f0", "strength": 0.8, "fog_start": 12, "fog_end": 80,
                "cloud_color": "#e8ecf0"},
        "glints": {"color": "#f4fbff", "count": 20, "radius": [2, 14], "height": [0.1, 3], "size": 0.3},
        "mist": {"color": "#f0f6ff", "alpha": 45, "height": 0.9, "layers": 2, "scale": 5, "speed": 1.4},
        "birds": {"color": "#3a3a40", "count": 2, "radius": [14, 24], "height": [16, 24], "size": 1.1},
        "wind": [0.15, 0.05],
        "particles": [
            {"type": "minecraft:snowflake", "rate": 3, "area": "air", "motion": [0.15, -0.05, 0.05], "spread": 0.04},
            {"type": "minecraft:snowflake", "rate": 0.8, "area": "field", "motion": [0.25, 0.01, 0.08], "spread": 0.03},
        ],
        "sound": {"id": "minecraft:item.elytra.flying", "volume": 0.22, "pitch": 0.65},
        "ambient_sounds": [
            {"id": "minecraft:entity.breeze.idle_air", "every": [100, 240], "volume": 0.35, "pitch": [0.6, 0.8]},
            {"id": "minecraft:entity.goat.screaming.ambient", "every": [600, 1200], "volume": 0.2},
        ],
        "moments": {
            "attack": {"particle": "minecraft:snowflake", "count": 20},
            "crit": {"particle": "minecraft:item_snowball", "count": 40, "flash": "#eef8ff",
                     "sound": "minecraft:block.powder_snow.break", "volume": 0.7},
            "faint": {"particle": "minecraft:snowflake", "count": 50, "sound": "minecraft:block.snow.break"},
            "victory": {"particle": "minecraft:snowflake", "count": 120, "sound": "minecraft:block.amethyst_block.chime"},
            "named": {
                "ice_spikes": {"particle": "minecraft:item_snowball", "count": 60, "flash": "#dff6ff",
                               "sound": "minecraft:block.glass.break", "volume": 0.8},
            },
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
