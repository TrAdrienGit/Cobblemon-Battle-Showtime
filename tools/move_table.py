"""Writes Documentation/attaques.csv: the moves played in competitive (real Smogon usage) and the ones Showtime redraws.

Inputs (downloaded by hand, not kept in the repo):
  - Smogon "chaos" stats: https://www.smogon.com/stats/<month>/chaos/gen9ou-1695.json and gen9nationaldex-1630.json
  - Showdown's move data: https://play.pokemonshowdown.com/data/moves.json
  - Cobblemon's jar, for the French names (assets/cobblemon/lang/fr_fr.json)
A move's usage = share of the Pokémon (weighted, as Smogon does) carrying it. Listed: every move at 1 % or more in
either tier, plus every redrawn move. Excel-friendly: ';' separated, UTF-8 with BOM.

Run: python tools/move_table.py <stats dir> <month> <cobblemon jar>
"""
import csv
import json
import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(__file__))
import moves as redrawn  # noqa: E402

HERE = os.path.dirname(__file__)
OUT = os.path.join(HERE, "..", "Documentation", "attaques.csv")
CATEGORIES = {"Physical": "Physique", "Special": "Spéciale", "Status": "Statut"}
TYPES_FR = {
    "Normal": "Normal", "Fire": "Feu", "Water": "Eau", "Electric": "Électrik", "Grass": "Plante", "Ice": "Glace",
    "Fighting": "Combat", "Poison": "Poison", "Ground": "Sol", "Flying": "Vol", "Psychic": "Psy", "Bug": "Insecte",
    "Rock": "Roche", "Ghost": "Spectre", "Dragon": "Dragon", "Dark": "Ténèbres", "Steel": "Acier", "Fairy": "Fée",
    "Stellar": "Stellaire",
}
ARCHETYPES_FR = {
    "projectile": "projectile", "beam": "rayon", "contact": "contact", "ground": "sol", "sky": "ciel", "buff": "boost",
    "heal": "soin", "protect": "protection", "hazard": "piège", "status": "statut", "sound": "son",
}


def usage(path):
    data = json.load(open(path, encoding="utf-8"))["data"]
    total = 0.0
    counts = {}
    for mon in data.values():
        total += sum(mon.get("Abilities", {}).values())
        for move, weight in mon.get("Moves", {}).items():
            if move:
                counts[move] = counts.get(move, 0.0) + weight
    return {move: 100.0 * weight / total for move, weight in counts.items()}


def main():
    stats_dir, month, jar = sys.argv[1], sys.argv[2], sys.argv[3]
    ou = usage(os.path.join(stats_dir, "gen9ou-1695.json"))
    natdex = usage(os.path.join(stats_dir, "gen9nationaldex-1630.json"))
    data = json.load(open(os.path.join(stats_dir, "moves.json"), encoding="utf-8"))
    with zipfile.ZipFile(jar) as z:
        fr = json.loads(z.read("assets/cobblemon/lang/fr_fr.json").decode("utf-8"))
    ids = {m for m, u in ou.items() if u >= 1} | {m for m, u in natdex.items() if u >= 1} | set(redrawn.MOVES) | {"shadowball"}
    rows = []
    for move in ids:
        info = data.get(move)
        if info is None:
            continue
        spec = redrawn.MOVES.get(move)
        motifs = "fait main" if move == "shadowball" else " + ".join(layer["motif"] for layer in spec[2]) if spec else ""
        rows.append({
            "type": TYPES_FR.get(info["type"], info["type"]),
            "categorie": CATEGORIES.get(info["category"], info["category"]),
            "puissance": info.get("basePower") or "",
            "nom_fr": fr.get("cobblemon.move." + move, ""),
            "nom_en": info["name"],
            "id": move,
            "usage_ou_pct": round(ou.get(move, 0.0), 2),
            "usage_natdex_pct": round(natdex.get(move, 0.0), 2),
            "refaite": "oui" if spec or move == "shadowball" else "non",
            "motifs": motifs,
        })
    rows.sort(key=lambda r: (r["type"], r["categorie"], -(r["puissance"] or 0), r["nom_fr"] or r["nom_en"]))
    with open(OUT, "w", encoding="utf-8-sig", newline="") as out:
        writer = csv.DictWriter(out, fieldnames=list(rows[0].keys()), delimiter=";")
        writer.writeheader()
        writer.writerows(rows)
    missing = sorted((r for r in rows if r["refaite"] == "non"), key=lambda r: -max(r["usage_ou_pct"], r["usage_natdex_pct"]))
    print(len(rows), "moves, stats", month, "-", sum(r["refaite"] == "oui" for r in rows), "redrawn")
    print("most used not redrawn:", ", ".join(f'{r["id"]} {max(r["usage_ou_pct"], r["usage_natdex_pct"]):.1f}%' for r in missing[:40]))
    unused = sorted((r for r in rows if r["refaite"] == "oui" and max(r["usage_ou_pct"], r["usage_natdex_pct"]) < 0.5), key=lambda r: r["id"])
    print("redrawn but under 0.5 %:", ", ".join(r["id"] for r in unused))


if __name__ == "__main__":
    main()
