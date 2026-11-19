"""Writes Showtime's battle stadiums and presets (2026-11-19, the split: Documentation/stadiums.md).

    python tools/stadiums.py

A stadium is the place: what replaces or hides the world round the field ("hide_world", a cave's vault, an island, a
court and stands). Same format as an ambiance (see Ambiance.java); combined with one, its keys replace the ambiance's.
A preset is a stadium and an ambiance under one name: Cosmos, Ultra Space and Champion's Stage keep their old ids as
presets, so a player who chose them sees no change.

    assets/cobblemon_battle_showtime/stadiums/<id>.json
    assets/cobblemon_battle_showtime/presets/<id>.json
"""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "cobblemon_battle_showtime")

STADIUMS = {
    # The void (Cosmos): every block of the world hidden, the battle floating among stars, over a solar system.
    "void": {
        "hide_world": True,
        "backdrop": {"box": False, "radius": 60, "stars": 2600, "spin": 0.3, "star_color": "#dfe6ff", "stars_around": True,
                     "star_size": 0.7, "star_colors": ["#ffffff", "#dfe6ff", "#bcd2ff", "#fff1c8", "#ffd2b0"]},
        "galaxy": {"color": "#b8c4ff", "tilt": 38, "yaw": 20, "alpha": 22, "stars": 900},
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
    },

    # Ultra Island (Ultra Space): the field on a floating island of violet stone, hexagons of light, crystals round its
    # rim; islands drifting round it in a starry void.
    "ultra_island": {
        "hide_world": True,
        "backdrop": {"box": False, "radius": 60, "stars": 1200, "spin": 0.15, "stars_around": True, "star_size": 0.7,
                     "star_colors": ["#ffffff", "#bff8ff", "#ffc8f4", "#fff4b0", "#c8b8ff"]},
        "islands": {"rock": "#2a1f4a", "top": "#5a3f9a", "crystal": "#7ff8ff", "accent": "#ff7ae8", "count": 8,
                    "distance": [22, 40], "height": [-16, 16], "size": [2.5, 6]},
        "island": {"top": "#241a52", "line": "#7ff8ff", "rock": "#1a1238", "crystal": "#7ff8ff", "accent": "#ff7ae8",
                   "margin": 3, "crystals": 14, "hex": 1.6, "pulse": 1},
    },

    # Arena (Champion's Stage): a court marked between the trainers, the stands all round with their crowd, light
    # towers.
    "arena": {
        "court": {"color": "#f4f0e0", "accent": "#ffcf4a", "alpha": 150},
        "crowd": {"rows": 5, "row_depth": 1.2, "rise": 0.8, "spacing": 0.75, "sides": ["#2a5ad0", "#d03a3a"],
                  "flashes": 0.5},
        "light_towers": {"count": 4, "height": 22, "frame": "#2a2e38", "lamp": "#fff6e0", "beam": 18},
    },

    # Cave: the world hidden, a vault of stone round the field (uneven walls, a roof, pillars, a floor). Its rock takes
    # the ambiance's light and, when it gives one, its stone colour; its roof, walls and floor are the props' anchors.
    "cave": {
        "hide_world": True,
        "cavern": {"rock": "#5a4e42", "floor": "#463d33", "light": "#ffd9a0", "light_strength": 0.4, "ambient": 0.32,
                   "pillars": 6},
    },

    # Volcanic Den: a higher, wider vault over a lake of lava, the field an island in it, lavafalls down the walls,
    # gold heaps and a dragon's bones on the shore.
    "volcanic_den": {
        "hide_world": True,
        "cavern": {"rock": "#4a2418", "floor": "#3a1c12", "light": "#ff7a30", "light_strength": 0.6, "ambient": 0.3,
                   "margin": 14, "height": 18, "pillars": 4,
                   "lava": {"color": "#ff4a08", "core": "#ffd060", "crust": "#2a0e06", "gap": 3, "width": 30,
                            "falls": 6},
                   "hoard": {"gold": "#ffcf4a", "piles": 10, "bones": True}},
    },

    # Sea: the world hidden, the field on a small islet of sand with rocks on its shore, water rolling out to the
    # horizon. The ambiance gives the water its colour and state ("water": frozen for icy, oily for murk water).
    "sea": {
        "hide_world": True,
        "sea": {"sand": "#d9c48f", "rock": "#6e675c", "water": "#2f8fc0", "deep": "#0e3f66", "foam": "#f2fbff",
                "margin": 3, "radius": 96, "wave": 0.32, "speed": 1, "rocks": 7},
    },

    # Seabed: a second stadium rather than a mode of the sea, so a player can pick either place with any ambiance. The
    # field on the sand at the bottom, dunes and rocks round it, the surface rippling bright overhead, fish circling.
    "seabed": {
        "hide_world": True,
        "sea": {"sand": "#c9b98a", "rock": "#5a6460", "water": "#3fa9d8", "deep": "#0a2a44", "under": True,
                "depth": 14, "radius": 72, "wave": 0.3, "speed": 0.7, "rocks": 7},
    },

    # --- League Reborn's lands and halls (2026-11-19): outdoor places (land) and rooms (hall). The ambiance on top
    # sets them on fire (blaze), snows them in (snow_cover), cuts the power (power_cut), recolours pools (water).

    # Meadow: rolling green hills, a few trees, blue ranges far off (grassy, flower gardens, misty, rainbow, stars).
    "meadow": {
        "hide_world": True,
        "land": {"ground": ["#5a8a32", "#4e7e2c", "#6a9a3a"], "flat": 4, "hills": 5, "radius": 110,
                 "ranges": {"color": "#6a8aa0", "height": 22, "distance": 95, "layers": 2},
                 "trees": {"kind": "broadleaf", "count": 22, "ring": [12, 48], "height": [6, 10]},
                 "rocks": {"color": "#8a8478", "count": 6, "ring": [5, 30], "size": [0.4, 1.0]}},
    },

    # Forest: tall trees crowding round the field, their crowns closing overhead (forest; ablaze for burning).
    "forest": {
        "hide_world": True,
        "land": {"ground": ["#3e5a26", "#4a3a24", "#55702e"], "flat": 3, "hills": 2, "radius": 70,
                 "trees": {"kind": "broadleaf", "count": 70, "ring": [2.5, 28], "height": [10, 16], "canopy": True,
                           "leaves": ["#2f6a24", "#3a7a2a", "#24581e", "#4a8030"], "trunk": "#4a3420"},
                 "rocks": {"color": "#6a6a5a", "count": 8, "ring": [2, 20], "size": [0.4, 1.2]}},
    },

    # Badlands: baked red earth split by cracks glowing with heat, rock needles, red ranges (super-heated).
    "badlands": {
        "hide_world": True,
        "land": {"ground": ["#b07a4a", "#9a5a34", "#c89060"], "flat": 4, "hills": 6, "radius": 110,
                 "cracks": {"color": "#ff5a10", "core": "#ffd060", "count": 30, "ring": [0.5, 22]},
                 "rocks": {"color": "#8a5a3a", "count": 16, "spires": 6, "ring": [5, 40], "size": [0.6, 2]},
                 "ranges": {"color": "#a0603a", "height": 26, "distance": 90, "layers": 2}},
    },

    # Swamp: a dark marsh, pools of bog water, dead trees (swamp; poisoned for corrosive and corrosive mist).
    "swamp": {
        "hide_world": True,
        "land": {"ground": ["#3a3e22", "#2e3a1e", "#4a4a2a"], "style": "marsh", "flat": 3, "hills": 1.2, "radius": 80,
                 "pools": {"color": "#2e3a24", "rim": "#24281a", "count": 12, "ring": [2, 26], "size": [1.5, 4.5]},
                 "trees": {"kind": "dead", "count": 14, "ring": [5, 35], "height": [5, 9], "trunk": "#3a3024"}},
    },

    # Desert: sand dunes rolling away, a few rocks, sandy ranges far off (desert).
    "desert": {
        "hide_world": True,
        "land": {"ground": ["#e2c48a", "#d4b070", "#ecd6a0"], "style": "dunes", "grain": 0.08, "flat": 4, "hills": 6,
                 "radius": 120,
                 "rocks": {"color": "#b08a5a", "count": 8, "spires": 3, "ring": [6, 40], "size": [0.6, 1.8]},
                 "ranges": {"color": "#c89a6a", "height": 18, "distance": 100, "layers": 2}},
    },

    # Canyon: boulders and rock needles everywhere, cliffs close round (rocky).
    "canyon": {
        "hide_world": True,
        "land": {"ground": ["#8a7a6a", "#7a6a5a", "#9a8a78"], "flat": 3, "hills": 7, "radius": 100,
                 "rocks": {"color": "#7a6a5c", "count": 45, "spires": 14, "ring": [2, 36], "size": [0.5, 2.2]},
                 "ranges": {"color": "#8a6a54", "height": 34, "distance": 75, "layers": 3}},
    },

    # Scrapyard: a dump: rusted barrels, scrap, sludge puddles, dead trees (wasteland).
    "scrapyard": {
        "hide_world": True,
        "land": {"ground": ["#4a4232", "#3a3a30", "#5a4a34"], "flat": 3, "hills": 3, "radius": 90,
                 "junk": {"count": 34, "ring": [2, 26]},
                 "pools": {"color": "#4a5a1a", "rim": "#2a2a1a", "count": 6, "ring": [3, 20], "size": [1, 2.5]},
                 "rocks": {"color": "#5a5048", "count": 14, "ring": [3, 30], "size": [0.5, 1.6]},
                 "trees": {"kind": "dead", "count": 6, "ring": [8, 30], "height": [4, 7], "trunk": "#3a3430"}},
    },

    # Summit: the field on a mountain top, the ground falling away into cliffs, a sea of clouds below, ranges round
    # the horizon (mountain; snowed in for snowy mountain).
    "summit": {
        "hide_world": True,
        "land": {"ground": ["#7a7a70", "#6a6a62", "#5a7a4a"], "style": "summit", "flat": 5, "hills": 3, "radius": 60,
                 "rocks": {"color": "#6e6a64", "count": 14, "spires": 5, "ring": [2, 12], "size": [0.5, 1.6]},
                 "clouds": {"depth": 24},
                 "ranges": {"color": "#5a6a80", "height": 40, "distance": 110, "layers": 3}},
    },

    # Temple: a ring of white columns on a marble summit above the clouds (holy, fairy tale).
    "temple": {
        "hide_world": True,
        "land": {"ground": ["#e8e2d4", "#d8d0c0", "#f0eadc"], "grain": 0.06, "style": "summit", "flat": 9, "hills": 2,
                 "radius": 60,
                 "rocks": {"color": "#c8c0b0", "count": 0},
                 "columns": {"count": 12, "distance": 5, "height": 9},
                 "clouds": {"color": "#ffffff", "shade": "#e0d8f0", "depth": 18}},
    },

    # Ashen Shore: the sea stadium on grey ash sand, cold dark water (ashen beach).
    "ashen_shore": {
        "hide_world": True,
        "sea": {"sand": "#8a8680", "rock": "#4a4644", "water": "#4a6a7a", "deep": "#1e2a34", "foam": "#d0d4d8",
                "margin": 4, "radius": 96, "wave": 0.25, "speed": 0.8, "rocks": 9},
    },

    # Factory Hall: a plant: walls, beams, hanging lamps, a conveyor carrying crates round the field, gears, presses
    # (factory; stopped and dark for short-circuit).
    "factory_hall": {
        "hide_world": True,
        "hall": {"floor": "#4a4c50", "wall": "#3a3e46", "ceiling": "#24262c", "lamp": "#ffe8b0", "margin": 13,
                 "height": 14, "machines": {"gears": 6, "presses": 4}},
    },

    # Chessboard: a hall floored with a giant, level checkerboard (big squares), giant pieces standing round the field (chess).
    "chessboard": {
        "hide_world": True,
        "hall": {"floor": "#e8e2d6", "checker": "#2a2630", "tile": 4, "flat": True, "wall": "#2a2440",
                 "ceiling": "#1a1628", "lamp": "#f0e8ff", "margin": 14, "height": 18, "pieces": {"count": 16}},
    },

    # Big Top: a circus tent, red and white, sawdust on the floor, the crowd in the stands round the ring (big top).
    "big_top": {
        "hide_world": True,
        "hall": {"shape": "tent", "floor": "#c8a070", "stripes": ["#d02a2a", "#f4f0e0"], "lamp": "#fff0c0",
                 "margin": 16, "height": 14},
        "crowd": {"rows": 4, "row_depth": 1.2, "rise": 0.8, "spacing": 0.75, "sides": ["#d02a2a", "#f4f0e0"],
                  "flashes": 0.3},
    },

    # Mirror Hall: walls of mirrors catching the room's light, a dark polished checkerboard (mirror arena).
    "mirror_hall": {
        "hide_world": True,
        "hall": {"floor": "#2a2a34", "checker": "#3a3a48", "tile": 3, "flat": True, "wall": "#9aa8c0",
                 "ceiling": "#1a1a24", "lamp": "#e8f0ff", "mirrors": True, "margin": 11, "height": 12},
    },
}

PRESETS = {
    "cosmos": {"stadium": "void", "ambiance": "cosmos"},
    "ultra_space": {"stadium": "ultra_island", "ambiance": "ultra_space"},
    "champion": {"stadium": "arena", "ambiance": "champion"},
    "grotto": {"stadium": "cave", "ambiance": "cave"},
    "crystal_grotto": {"stadium": "cave", "ambiance": "crystal_cavern"},
    "dark_grotto": {"stadium": "cave", "ambiance": "dark_crystal_cavern"},
    "dragon_lair": {"stadium": "volcanic_den", "ambiance": "dragons_den"},
    "open_sea": {"stadium": "sea", "ambiance": "water_surface"},
    "polluted_sea": {"stadium": "sea", "ambiance": "murkwater"},
    "frozen_sea": {"stadium": "sea", "ambiance": "icy"},
    "deep_sea": {"stadium": "seabed", "ambiance": "underwater"},
    "sunny_meadow": {"stadium": "meadow", "ambiance": "grassy"},
    "blooming_garden": {"stadium": "meadow", "ambiance": "flower_garden_5"},
    "misty_meadow": {"stadium": "meadow", "ambiance": "misty"},
    "rainbow_meadow": {"stadium": "meadow", "ambiance": "rainbow"},
    "starry_meadow": {"stadium": "meadow", "ambiance": "starlight"},
    "deep_forest": {"stadium": "forest", "ambiance": "forest"},
    "wildfire": {"stadium": "forest", "ambiance": "burning"},
    "scorched_badlands": {"stadium": "badlands", "ambiance": "superheated"},
    "bog": {"stadium": "swamp", "ambiance": "swamp"},
    "poison_marsh": {"stadium": "swamp", "ambiance": "corrosive"},
    "toxic_fog": {"stadium": "swamp", "ambiance": "corrosive_mist"},
    "sand_dunes": {"stadium": "desert", "ambiance": "desert"},
    "rocky_canyon": {"stadium": "canyon", "ambiance": "rocky"},
    "junkyard": {"stadium": "scrapyard", "ambiance": "wasteland"},
    "grey_shore": {"stadium": "ashen_shore", "ambiance": "ashen_beach"},
    "peak": {"stadium": "summit", "ambiance": "mountain"},
    "snowy_peak": {"stadium": "summit", "ambiance": "snowy_mountain"},
    "sky_temple": {"stadium": "temple", "ambiance": "holy"},
    "storybook_court": {"stadium": "temple", "ambiance": "fairy_tale"},
    "plant": {"stadium": "factory_hall", "ambiance": "factory"},
    "blackout": {"stadium": "factory_hall", "ambiance": "short_circuit"},
    "grand_chessboard": {"stadium": "chessboard", "ambiance": "chess_board"},
    "circus": {"stadium": "big_top", "ambiance": "big_top"},
    "hall_of_mirrors": {"stadium": "mirror_hall", "ambiance": "mirror"},
}


def write(folder, entries):
    out = os.path.join(ROOT, folder)
    os.makedirs(out, exist_ok=True)
    for name, data in entries.items():
        with open(os.path.join(out, name + ".json"), "w", encoding="utf-8") as file:
            json.dump(data, file, ensure_ascii=False, indent="\t")
            file.write("\n")
    print(len(entries), folder + ":", ", ".join(entries))


if __name__ == "__main__":
    write("stadiums", STADIUMS)
    write("presets", PRESETS)
