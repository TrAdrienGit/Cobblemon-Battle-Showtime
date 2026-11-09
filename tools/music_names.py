"""Normalised names for the battle music pack: <game acronym>_<what it is>.ogg.

Usage: python music_names.py <music dir> [--apply <out dir>]
Without --apply, prints the mapping. With it, copies every file under its new name into <out dir>/<moment>/.
"""
import re
import shutil
import sys
from pathlib import Path

# Longest phrases first: matched as whole token runs, anywhere in the name.
GAMES = [
    ("brilliant_diamond_shining_pearl", "bdsp"),
    ("omega_ruby_alpha_sapphire", "oras"),
    ("lets_go_pikachu_eevee", "lgpe"),
    ("mystery_dungeon_explorers_of_sky", "pmdeos"),
    ("pinball_ruby_and_sapphire_gba", "pinballrs"),
    ("xd_gale_of_darkness", "xd"),
    ("diamond_pearl_platinum", "dppt"),
    ("ruby_sapphire_emerald", "rse"),
    ("gold_silver_crystal", "gsc"),
    ("silver_gold_crystal", "gsc"),
    ("red_blue_yellow", "rby"),
    ("heartgold_soulsilver", "hgss"),
    ("ultrasun_ultramoon", "usum"),
    ("firered_leafgreen", "frlg"),
    ("black_and_white_2", "b2w2"),
    ("black_2_white_2", "b2w2"),
    ("black_white_2", "b2w2"),
    ("black_and_white", "bw"),
    ("black_white", "bw"),
    ("diamond_and_pearl", "dp"),
    ("diamond_pearl", "dp"),
    ("battle_revolution", "pbr"),
    ("pokken_tournament", "pokken"),
    ("puzzle_league", "puzzleleague"),
    ("legends_arceus", "pla"),
    ("legends_z_a", "plza"),
    ("scarlet_violet", "sv"),
    ("sword_shield", "swsh"),
    ("sun_and_moon", "sm"),
    ("sun_moon", "sm"),
    ("ranger_series", "ranger"),
    ("stadium_2", "stadium2"),
    ("d_p_pt", "dppt"),
    ("red_blue", "rb"),
    ("blue_red", "rb"),
    ("b2_w2", "b2w2"),
    ("hg_ss", "hgss"),
    ("d_p", "dp"),
    ("x_y", "xy"),
    ("colosseum", "colosseum"),
    ("platinum", "pt"),
    ("platin", "pt"),
    ("stadium", "stadium"),
    ("pinball", "pinball"),
    ("reborn", "reborn"),
    ("hgss", "hgss"),
    ("bw2", "b2w2"),
    ("xy", "xy"),
]
# Noise words, removed wherever they are.
NOISE = {"pokemon", "ost", "soundtrack", "music", "hq", "hd", "full", "audio", "restored", "gba", "and", "theme"}
NOISE_RUNS = ["highest_quality", "super_music_collection", "music_collection"]


def rename(stem):
    name = "_" + stem.lower() + "_"
    # Pokémon Champions remixes of X/Y: the source is Champions, X/Y stays in the title.
    if "_pokemon_champions_" in name:
        name = name.replace("_pokemon_champions_", "_").replace("_x_y_", "_xy_")
        game = "champions"
    else:
        game = None
        for phrase, acronym in GAMES:
            token = "_" + phrase + "_"
            if token in name:
                name = name.replace(token, "_", 1)
                game = acronym
                break
    for run in NOISE_RUNS:
        name = name.replace("_" + run + "_", "_")
    words = [w for w in name.strip("_").split("_") if w and w not in NOISE and not w.isdigit()]
    rest = "_".join(words)
    return (game or "misc") + ("_" + rest if rest else "")


def main():
    root = Path(sys.argv[1])
    out = Path(sys.argv[3]) if len(sys.argv) > 3 and sys.argv[2] == "--apply" else None
    for moment in sorted(p for p in root.iterdir() if p.is_dir()):
        taken = {}
        for f in sorted(moment.glob("*.ogg")):
            new = rename(f.stem)
            n = taken.get(new, 0) + 1
            taken[new] = n
            final = new if n == 1 else f"{new}_{n}"
            if out is None:
                print(f"{moment.name}/{f.name}  ->  {final}.ogg")
            else:
                dest = out / moment.name
                dest.mkdir(parents=True, exist_ok=True)
                shutil.copy2(f, dest / (final + ".ogg"))


if __name__ == "__main__":
    main()
