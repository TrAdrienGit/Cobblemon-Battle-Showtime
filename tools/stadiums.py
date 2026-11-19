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
}

PRESETS = {
    "cosmos": {"stadium": "void", "ambiance": "cosmos"},
    "ultra_space": {"stadium": "ultra_island", "ambiance": "ultra_space"},
    "champion": {"stadium": "arena", "ambiance": "champion"},
    "grotto": {"stadium": "cave", "ambiance": "cave"},
    "crystal_grotto": {"stadium": "cave", "ambiance": "crystal_cavern"},
    "dark_grotto": {"stadium": "cave", "ambiance": "dark_crystal_cavern"},
    "dragon_lair": {"stadium": "volcanic_den", "ambiance": "dragons_den"},
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
