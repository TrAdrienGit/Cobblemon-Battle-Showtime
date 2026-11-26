"""Writes Documentation/move-redraws.md: every move Showtime draws, and which ones were redone with the move capture tool.

Since 2026-11-26 moves are redone in batches of 50, by Smogon National Dex usage (Documentation/move-usage.csv), each
filmed before and after with tools/move_capture.py. Add a batch to BATCHES when it is committed, then run this script.

Inputs:
  - the move files (src/main/resources/assets/cobblemon_battle_showtime/moves/*.json): every move;
  - tools/move_data.json: type and category;
  - Documentation/move-usage.csv: National Dex usage;
  - Cobblemon's jar, for the English and French names (found in the Gradle cache, or given as the first argument).

Run: python tools/move_redraws.py [cobblemon jar]
"""
import csv
import glob
import json
import os
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
MOVES_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "cobblemon_battle_showtime", "moves")
OUT = os.path.join(ROOT, "Documentation", "move-redraws.md")

# Moves redone with the capture tool, batch by batch (in each batch's usage order).
BATCHES = [
    (0, "2026-11-26", ["closecombat"]),
    (1, "2026-11-26", [
        "earthquake", "knockoff", "stealthrock", "uturn", "swordsdance", "roost", "toxic", "rapidspin", "defog",
        "earthpower", "recover", "moonblast", "protect", "substitute", "flamethrower", "flipturn", "stoneedge",
        "focusblast", "voltswitch", "icebeam", "shadowball", "icespinner", "thunderbolt", "calmmind", "suckerpunch",
        "dracometeor", "dragondance", "bodypress", "mysticalfire", "nastyplot", "hurricane", "thunderwave", "taunt",
        "ironhead", "psyshock", "crunch", "headlongrush", "pursuit", "wish", "scald", "terastarstorm", "ivycudgel",
        "spikes", "makeitrain", "futuresight", "sludgebomb", "willowisp", "powerwhip", "psychic", "heatwave",
    ]),
    (2, "2026-11-26", [
        "icepunch", "drainingkiss", "freezedry", "weatherball", "thunderclap", "irondefense", "lowkick", "leechseed",
        "firepunch", "hiddenpowerice", "gigadrain", "aquajet", "psychicnoise", "encore", "bulletpunch", "heavyslam",
        "trick", "superpower", "surgingstrikes", "hiddenpowerfire", "quiverdance", "ceaselessedge", "bravebird",
        "playrough", "outrage", "extremespeed", "dazzlinggleam", "chillyreception", "mirrorcoat", "hex",
        "thunderpunch", "poltergeist", "bugbuzz", "fakeout", "poisonjab", "diamondstorm", "rest", "liquidation",
        "teleport", "solarbeam", "sacredsword", "trickroom", "flareblitz", "whirlwind", "scaleshot", "sludgewave",
        "tripleaxel", "drainpunch", "razorshell", "surf",
    ]),
    (3, "2026-11-26", [
        "synthesis", "rockslide", "dragontail", "bulkup", "ruination", "waterfall", "toxicspikes", "flashcannon",
        "aurasphere", "bitterblade", "facade", "dragonpulse", "fireblast", "nuzzle", "fierydance", "pyroball",
        "healingwish", "dragonclaw", "malignantchain", "storedpower", "grassyglide", "airslash", "painsplit",
        "partingshot", "lightscreen", "darkpulse", "reflect", "haze", "woodhammer", "shadowsneak", "dualwingbeat",
        "courtchange", "slackoff", "overheat", "stickyweb", "roar", "leafstorm", "energyball", "hydropump",
        "hypervoice", "iceshard", "foulplay", "thunder", "acrobatics", "discharge", "mortalspin", "agility",
        "headsmash", "highhorsepower", "moonlight",
    ]),
]

NOTES = {
    "closecombat": "the capture tool's first move, before the batches",
    "heavyslam": "reviewed, drawing kept",
    "solarbeam": "reviewed, drawing kept",
    "shadowball": "its own class until 2026-11-26, now a motif like the others",
    "fireblast": "drawing kept, flames instead of lava smoke",
    "pyroball": "Cinderace only (Scorbunny and Raboot send no cue of their own)",
}


def find_jar():
    if len(sys.argv) > 1:
        return sys.argv[1]
    home = os.path.expanduser("~")
    jars = glob.glob(os.path.join(home, ".gradle", "caches", "modules-2", "files-2.1", "maven.modrinth", "cobblemon",
                                  "*", "*", "cobblemon-*.jar"))
    if not jars:
        sys.exit("Cobblemon's jar not found in the Gradle cache: pass its path as the first argument.")
    return max(jars, key=os.path.getmtime)


def main():
    with zipfile.ZipFile(find_jar()) as z:
        en = json.loads(z.read("assets/cobblemon/lang/en_us.json").decode("utf-8"))
        fr = json.loads(z.read("assets/cobblemon/lang/fr_fr.json").decode("utf-8"))
    data = json.load(open(os.path.join(HERE, "move_data.json"), encoding="utf-8"))
    usage = {}
    with open(os.path.join(ROOT, "Documentation", "move-usage.csv"), encoding="utf-8-sig") as f:
        for row in csv.DictReader(f, delimiter=";"):
            usage[row["id"]] = float(row["usage_natdex_pct"] or 0)
    redone = {}
    for batch, date, moves in BATCHES:
        for move in moves:
            if move in redone:
                sys.exit(f"{move} is in two batches")
            redone[move] = (batch, date)
    moves = sorted(f[:-5] for f in os.listdir(MOVES_DIR) if f.endswith(".json"))
    missing = [m for m in redone if m not in moves]
    if missing:
        sys.exit(f"Redone moves without a move file: {missing}")

    def name(move, lang):
        return lang.get("cobblemon.move." + move) or data.get(move, {}).get("name") or move

    # Most played first (National Dex usage), then the others by name.
    moves.sort(key=lambda m: (-usage.get(m, 0), name(m, en).lower()))
    batch_count = sum(len(ms) for b, _, ms in BATCHES if b > 0)
    lines = [
        "# Move redraws",
        "",
        "Every move Showtime draws, and the ones redone with the move capture tool (`tools/move_capture.py`, filmed",
        "before and after). Since 2026-11-26 moves are redone in batches of 50, by National Dex usage",
        "(`move-usage.csv`). Generated by `tools/move_redraws.py`: add a batch there, then run it; never edit this file",
        "by hand.",
        "",
        f"- Moves: {len(moves)} ({sum(1 for m in moves if m not in data)} of them Z-Moves).",
        f"- Redone with the capture tool: {len(redone)} ({batch_count} in {sum(1 for b in BATCHES if b[0] > 0)} batches"
        f" of 50, plus Close Combat, the tool's first move).",
        f"- Still to redo: {len(moves) - len(redone)}.",
        "",
        "Batch 0 is Close Combat alone. NatDex = share of National Dex teams carrying the move (%), blank when the move",
        "is not in `move-usage.csv`. Rows: most played first, then by name.",
        "",
        "| # | Move | French | Type | Category | NatDex | Redone (capture tool) | Note |",
        "|---|---|---|---|---|---|---|---|",
    ]
    for i, move in enumerate(moves, 1):
        info = data.get(move)
        kind = info["type"].capitalize() if info else ""
        category = info["category"].capitalize() if info else "Z-Move"
        pct = f"{usage[move]:.2f}" if move in usage else ""
        done = f"✅ batch {redone[move][0]} ({redone[move][1]})" if move in redone else ""
        lines.append(f"| {i} | {name(move, en)} | {name(move, fr)} | {kind} | {category} | {pct} | {done} | "
                     f"{NOTES.get(move, '')} |")
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    print(f"{len(moves)} moves, {len(redone)} redone -> {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
