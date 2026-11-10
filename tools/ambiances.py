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
        "particles": [
            {"type": "minecraft:glow", "rate": 0.5, "area": "field", "motion": [0, 0.01, 0], "spread": 0.03},
            {"type": "minecraft:white_ash", "rate": 2, "area": "air"},
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

    # Haunted: green-black fog closing in, lit candles floating and bobbing around, souls rising; a faint drains the colour.
    "spectral": {
        "time": {"at": 18000, "speed": 0},
        "sky": {"sky_color": "#08140f", "fog_color": "#0d2a1f", "strength": 0.95, "fog_start": 2, "fog_end": 42,
                "stars": 0, "hide_clouds": True},
        "floating_blocks": {"block": "minecraft:candle[lit=true,candles=1]", "count": 18, "radius": [6, 16],
                            "height": [1.5, 6], "bob": 0.4, "orbit": 0.6, "flame": True},
        "particles": [
            {"type": "minecraft:soul", "rate": 0.6, "area": "field", "motion": [0, 0.03, 0]},
            {"type": "minecraft:sculk_soul", "rate": 0.2, "area": "ring", "motion": [0, 0.04, 0]},
        ],
        "sound": {"id": "minecraft:ambient.soul_sand_valley.loop", "volume": 0.6},
        "moments": {
            "attack": {"particle": "minecraft:soul_fire_flame", "count": 15},
            "crit": {"particle": "minecraft:sculk_soul", "count": 30, "flash": "#2bff9a",
                     "sound": "minecraft:block.sculk_shrieker.shriek", "volume": 0.5},
            "faint": {"particle": "minecraft:soul", "count": 50, "filter": BLACK_WHITE, "filter_ticks": 50,
                      "sound": "minecraft:ambient.soul_sand_valley.mood"},
            "victory": {"particle": "minecraft:soul_fire_flame", "count": 80},
        },
    },

    # Fairy tale: pastel sky, a rainbow over the field, the sun racing across the sky, glittering spores, amethyst floating.
    "fairy": {
        "time": {"at": 0, "speed": 30},
        "sky": {"sky_color": "#ffc3e6", "fog_color": "#ffe0f2", "strength": 0.6, "cloud_color": "#ffd6ee"},
        "rainbow": {"radius": 34, "alpha": 0.5},
        "floating_blocks": {"block": "minecraft:amethyst_cluster", "count": 7, "radius": [11, 17], "height": [5, 10],
                            "bob": 0.6, "orbit": 0.6},
        "wind": [0.01, 0.0],
        "particles": [
            {"type": "minecraft:spore_blossom_air", "rate": 1.5, "area": "air"},
            {"type": "minecraft:wax_on", "rate": 1.0, "area": "field"},
            {"type": "minecraft:end_rod", "rate": 0.3, "area": "air", "motion": [0, 0.02, 0]},
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
        # Stars all around, below too: the battle floats in space, over a solar system.
        "backdrop": {"box": False, "radius": 60, "stars": 2200, "spin": 0.3, "star_color": "#dfe6ff", "stars_around": True},
        # Meteors: big, trailing fire, crossing above and below.
        "shooting_stars": {"rate": 0.08, "color": "#ffd9a0", "size": 4, "trail": "minecraft:flame", "below": True},
        "planets": [
            {"color": "#ffd25a", "size": 7, "orbit": 0, "height": -45, "speed": 0, "glow": True},
            {"color": "#b5683c", "size": 1.4, "orbit": 18, "height": -40, "speed": 3},
            {"color": "#3d7bff", "size": 2.4, "orbit": 30, "height": -36, "speed": 1.6},
            {"color": "#d9b77a", "size": 3.6, "orbit": 44, "height": -30, "speed": 0.8, "ring": "#e8d8b0"},
            {"color": "#7fd6ff", "size": 2.8, "orbit": 58, "height": -26, "speed": 0.5},
            # Above too (2026-10-30): a gas giant and a small moon passing over the field, a pale far sun.
            {"color": "#c46ad6", "size": 5, "orbit": 50, "height": 34, "speed": -0.4, "ring": "#f0c8ff"},
            {"color": "#d8dde8", "size": 1.6, "orbit": 26, "height": 22, "speed": -1.2},
            {"color": "#9fd8ff", "size": 3, "orbit": 70, "height": 48, "speed": 0.2, "glow": True},
        ],
        "aurora": {"colors": ["#6a4bff", "#3d8bff", "#c24bff"], "height": 20, "alpha": 60},
        "particles": [
            {"type": "minecraft:end_rod", "rate": 0.6, "area": "air", "motion": [0, 0.005, 0], "spread": 0.01},
            {"type": "minecraft:reverse_portal", "rate": 2, "area": "ring"},
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

    # Old film: black and white for the whole battle, dust in the air.
    "silver_screen": {
        "filter": BLACK_WHITE,
        "particles": [{"type": "minecraft:white_ash", "rate": 3, "area": "air"}],
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

    # Corrupted: a black-red void, colours split for good, the world warping on every big hit.
    "corrupted": {
        "filter": GLITCH_SOFT,
        "rifts": {"count": 5, "color": "#ff1040", "height": 8, "width": 1.8, "cycle": 200,
                  "particle": "minecraft:reverse_portal"},
        "time": {"at": 18000, "speed": 0},
        "sky": {"sky_color": "#1a0000", "fog_color": "#120000", "strength": 1.0, "fog_start": 4, "fog_end": 50,
                "stars": 0, "hide_clouds": True},
        "particles": [
            {"type": "minecraft:portal", "rate": 3, "area": "field"},
            {"type": "minecraft:crimson_spore", "rate": 3, "area": "air"},
        ],
        "sound": {"id": "minecraft:ambient.crimson_forest.loop", "volume": 0.6},
        "moments": {
            "attack": {"filter": PIXELS, "filter_ticks": 5},
            "crit": {"filter": WOBBLE, "filter_ticks": 16, "flash": "#ff0033",
                     "sound": "minecraft:entity.warden.sonic_boom", "volume": 0.4},
            "faint": {"filter": INVERT, "filter_ticks": 10, "particle": "minecraft:sculk_soul", "count": 40},
            "victory": {"filter": OLD_TV, "filter_ticks": 60, "sound": "minecraft:block.end_portal.spawn",
                        "volume": 0.4},
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
