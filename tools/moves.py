"""Writes Showtime's redrawn moves (M2): assets/cobblemon_battle_showtime/moves/<move>.json.

Each move stacks a few motifs (see Motifs.java) in phases: "wind" (the user winds up), "travel" (to the target),
"hit" (on Cobblemon's hit cue), "self" (moves on the user alone). Physical moves are blows up close (a dash, then the
blow: slash, fists, hammer, tail…), special ones travel (orbs, beams, jets, waves…), status ones play on the user or
the target (Adrien 2026-11-01: each move its own look). Colours default to the move's type.

The list: every move at 1 % or more in Smogon's Gen 9 OU and National Dex usage (October 2026, see tools/move_table.py
and Documentation/attaques.csv) plus other common ones. Shadow Ball is hand-made (ShadowBall.java) and has no file.
Run: python tools/moves.py
"""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "cobblemon_battle_showtime", "moves")

# type: (colour, accent, particles)
TYPES = {
    "normal": ("#E8E2D0", "#FFFFFF", ["minecraft:crit", "minecraft:poof"]),
    "fire": ("#FF6A1A", "#FFD040", ["minecraft:flame", "minecraft:lava"]),
    "water": ("#2E8BFF", "#B8ECFF", ["minecraft:splash", "minecraft:falling_water"]),
    "electric": ("#FFD81A", "#FFFFB0", ["minecraft:electric_spark", "minecraft:wax_off"]),
    "grass": ("#48C840", "#D0FF88", ["minecraft:happy_villager", "minecraft:composter"]),
    "ice": ("#7FE6FF", "#FFFFFF", ["minecraft:snowflake", "minecraft:end_rod"]),
    "fighting": ("#E8502A", "#FFC080", ["minecraft:crit", "minecraft:poof"]),
    "poison": ("#A040D0", "#E8A0FF", ["minecraft:witch", "minecraft:squid_ink"]),
    "ground": ("#C89A48", "#F0D890", ["minecraft:poof"]),
    "flying": ("#B0D0FF", "#FFFFFF", ["minecraft:cloud", "minecraft:poof"]),
    "psychic": ("#FF4FA8", "#FFC0E8", ["minecraft:enchant", "minecraft:glow"]),
    "bug": ("#A8D020", "#F0FF90", ["minecraft:happy_villager", "minecraft:crit"]),
    "rock": ("#B8A058", "#F0E0A8", ["minecraft:poof"]),
    "ghost": ("#7A4CC8", "#D0B0FF", ["minecraft:soul", "minecraft:witch"]),
    "dragon": ("#6A40FF", "#C8B0FF", ["minecraft:dragon_breath", "minecraft:reverse_portal"]),
    "dark": ("#6A3060", "#E05A90", ["minecraft:squid_ink", "minecraft:smoke"]),
    "steel": ("#B8C8D8", "#FFFFFF", ["minecraft:crit", "minecraft:wax_on"]),
    "fairy": ("#FF8CD8", "#FFE4F6", ["minecraft:end_rod", "minecraft:cherry_leaves"]),
}


def L(motif, phase="hit", **extra):
    layer = {"motif": motif, "phase": phase}
    layer.update(extra)
    return layer


def charge(motif, *hit, dash=True, aura=None):
    """A physical blow: optionally wrapped in an element, a dash to the target, then the blow and its extras."""
    layers = []
    if aura:
        layers.append(L("engulf", "wind", duration=20, particles=aura))
    if dash:
        layers.append(L("dash", "travel"))
    layers.append(L(motif))
    layers.extend(hit)
    return layers


P, S, T = "physical", "special", "status"

# move id (Cobblemon / Showdown): (type, category, layers, extra fields)
MOVES = {
    # ---- ground ----
    "earthquake": ("ground", P, [L("quake", "travel", duration=26), L("cracks"), L("burst", size=1.2)], {"size": 1.3}),
    "bulldoze": ("ground", P, [L("quake", "travel", duration=22), L("cracks")], {}),
    "stompingtantrum": ("ground", P, [L("quake", "travel", duration=22), L("impact", size=1.2)], {}),
    "highhorsepower": ("ground", P, charge("impact", L("cracks"), L("kick", count=2)), {"size": 1.2}),
    "headlongrush": ("ground", P, charge("impact", L("cracks"), L("quake", size=0.7), aura=["minecraft:poof"]), {"size": 1.3}),
    "earthpower": ("ground", S, [L("cracks", "travel", duration=10), L("pillars")], {}),
    "spikes": ("ground", T, [L("caltrops", duration=26)], {"color": "#B0B0B0"}),
    # ---- rock ----
    "stoneedge": ("rock", P, [L("cracks", "travel", duration=8), L("rise_rocks")], {"size": 1.2}),
    "rockslide": ("rock", P, [L("fall_rocks", count=6, duration=26)], {}),
    "headsmash": ("rock", P, charge("impact", L("burst", size=1.5), aura=["minecraft:poof"]), {"size": 1.4}),
    "stealthrock": ("rock", T, [L("rocks_float", duration=30)], {}),
    "diamondstorm": ("rock", P, [L("fall_rocks", count=8, block="minecraft:diamond_block", duration=26), L("rays", size=0.8)], {"color": "#C8F8FF", "accent": "#FFFFFF"}),
    # ---- dark ----
    "knockoff": ("dark", P, charge("knock_item"), {"size": 1.1}),
    "suckerpunch": ("dark", P, [L("shadow_hand", "travel"), L("fist")], {}),
    "crunch": ("dark", P, charge("bite"), {}),
    "darkpulse": ("dark", S, [L("gather", "wind"), L("dark_rings", "travel", duration=12), L("burst")], {}),
    "foulplay": ("dark", P, [L("shadow_hand", "travel"), L("claw"), L("slash", delay=4)], {}),
    "throatchop": ("dark", P, charge("slash", L("impact", size=0.8)), {}),
    "kowtowcleave": ("dark", P, charge("sword", L("slash", delay=3, size=1.2)), {"size": 1.2}),
    "ceaselessedge": ("dark", P, charge("sword", L("caltrops", delay=4, duration=24)), {"size": 1.1}),
    "pursuit": ("dark", P, [L("shadow_hand", "travel"), L("claw")], {}),
    "ruination": ("dark", S, [L("gather", "wind"), L("cloud", duration=24, size=1.4), L("rays", size=0.8)], {}),
    "partingshot": ("dark", T, [L("taunt", duration=24), L("glare")], {}),
    "taunt": ("dark", T, [L("taunt", duration=26)], {"color": "#FF4040"}),
    "nastyplot": ("dark", T, [L("aura", "self", particles=["minecraft:squid_ink"]), L("glare", "self", duration=16)], {}),
    # ---- bug ----
    "uturn": ("bug", P, charge("impact", L("wings", size=0.8)), {}),
    "firstimpression": ("bug", P, charge("bite", L("impact")), {}),
    "xscissor": ("bug", P, charge("cross_slash"), {}),
    "bugbuzz": ("bug", S, [L("sound_rings", "travel", duration=14), L("burst")], {}),
    "quiverdance": ("bug", T, [L("dance", "self", particles=["minecraft:end_rod"]), L("wings", "self", duration=14, delay=10)], {}),
    "stickyweb": ("bug", T, [L("web", duration=30)], {"accent": "#FFFFFF"}),
    # ---- fighting ----
    "closecombat": ("fighting", P, charge("barrage", L("kick", delay=10), dash=True), {}),
    "drainpunch": ("fighting", P, charge("fist", L("heal", delay=6, duration=16)), {"accent": "#A0FF80"}),
    "machpunch": ("fighting", P, charge("fist"), {"accent": "#FFFFFF"}),
    "focusblast": ("fighting", S, [L("gather", "wind"), L("orb_rings", "travel"), L("burst", size=1.4)], {"size": 1.3}),
    "aurasphere": ("fighting", S, [L("gather", "wind"), L("homing", "travel"), L("burst")], {"color": "#4080FF", "accent": "#A0D0FF"}),
    "bodypress": ("fighting", P, charge("impact", L("quake", size=0.6), L("burst", size=1.3)), {"size": 1.3}),
    "superpower": ("fighting", P, charge("barrage", L("impact", size=1.4, delay=8), aura=["minecraft:crit"]), {}),
    "lowkick": ("fighting", P, charge("kick"), {}),
    "sacredsword": ("fighting", P, charge("sword", L("rays", size=0.6)), {"accent": "#FFFFFF", "size": 1.2}),
    "bulkup": ("fighting", T, [L("aura", "self", particles=["minecraft:crit"]), L("shell", "self", duration=12, delay=18)], {}),
    "detect": ("fighting", T, [L("dome", "self"), L("glare", "self", duration=10)], {}),
    # ---- fairy ----
    "moonblast": ("fairy", S, [L("moon", "wind"), L("beam", "travel"), L("rays")], {"size": 1.2, "accent": "#FFFFFF"}),
    "playrough": ("fairy", P, charge("barrage", L("rays", size=0.7), dash=True), {}),
    "dazzlinggleam": ("fairy", S, [L("rays", "wind"), L("rays", "travel"), L("burst")], {}),
    "drainingkiss": ("fairy", S, [L("orb", "travel"), L("heal", delay=4, duration=16)], {}),
    "moonlight": ("fairy", T, [L("moon", "self"), L("heal", "self")], {"color": "#E0E8FF"}),
    # ---- electric ----
    "voltswitch": ("electric", S, [L("gather", "wind"), L("orb", "travel"), L("sparks_on")], {}),
    "thunderbolt": ("electric", S, [L("sparks_on", "wind", duration=12), L("bolt", "travel"), L("sparks_on"), L("burst")], {}),
    "thunder": ("electric", S, [L("sky_bolt", duration=24), L("sparks_on")], {"size": 1.3}),
    "thunderclap": ("electric", S, [L("sky_bolt", duration=16)], {}),
    "risingvoltage": ("electric", S, [L("cracks", "travel"), L("sky_bolt", duration=18)], {}),
    "thunderwave": ("electric", T, [L("bolt", "travel"), L("sparks_on", duration=26)], {}),
    "wildcharge": ("electric", P, charge("impact", L("sparks_on"), aura=["minecraft:electric_spark"]), {}),
    "discharge": ("electric", S, [L("rays", "wind"), L("sparks_on")], {}),
    "thunderpunch": ("electric", P, charge("fist", L("sparks_on")), {}),
    "nuzzle": ("electric", P, charge("impact", L("sparks_on"), dash=True), {"size": 0.8}),
    # ---- water ----
    "flipturn": ("water", P, charge("splash", aura=["minecraft:splash"]), {}),
    "hydropump": ("water", S, [L("gather", "wind"), L("jets_water", "travel", count=3), L("jets_water", count=3, duration=10), L("splash")], {"size": 1.3}),
    "surf": ("water", S, [L("wave", "travel", duration=22), L("splash", size=1.3)], {"size": 1.3}),
    "scald": ("water", S, [L("jets_water", "travel", count=2, particles=["minecraft:cloud"]), L("splash"), L("cloud", duration=14)], {"accent": "#FFFFFF"}),
    "waterfall": ("water", P, charge("splash", L("impact"), aura=["minecraft:splash"]), {}),
    "liquidation": ("water", P, charge("stab", L("splash")), {}),
    "aquajet": ("water", P, charge("splash", aura=["minecraft:splash"]), {}),
    "wavecrash": ("water", P, [L("wave", "travel"), L("impact", size=1.3), L("splash", size=1.3)], {"size": 1.3}),
    "razorshell": ("water", P, charge("slash", L("splash", size=0.8)), {}),
    "surgingstrikes": ("water", P, charge("barrage", L("splash"), dash=True), {}),
    # ---- ice ----
    "icebeam": ("ice", S, [L("gather", "wind"), L("ice_beam", "travel"), L("freeze")], {}),
    "freezedry": ("ice", S, [L("ice_beam", "travel"), L("freeze"), L("cloud", duration=12)], {"accent": "#B0FFFF"}),
    "iceshard": ("ice", P, [L("fall_rocks", count=3, block="minecraft:packed_ice", duration=12), L("impact")], {}),
    "tripleaxel": ("ice", P, charge("kick", L("freeze", delay=10), dash=True), {}),
    "icespinner": ("ice", P, [L("spin", "wind", duration=20), L("dash", "travel"), L("impact"), L("freeze", size=0.7)], {}),
    "iciclecrash": ("ice", P, [L("fall_rocks", count=5, block="minecraft:packed_ice", duration=24)], {}),
    "blizzard": ("ice", S, [L("tornado", duration=28, particles=["minecraft:snowflake"]), L("freeze", delay=10)], {"size": 1.2}),
    "icepunch": ("ice", P, charge("fist", L("freeze", size=0.7)), {}),
    "chillyreception": ("ice", T, [L("tornado", duration=24, particles=["minecraft:snowflake"])], {}),
    "hiddenpowerice": ("ice", S, [L("orb", "travel"), L("freeze", size=0.7)], {}),
    # ---- fire ----
    "flamethrower": ("fire", S, [L("gather", "wind"), L("stream_fire", "travel"), L("stream_fire", duration=10), L("flames_on")], {}),
    "fireblast": ("fire", S, [L("orb", "travel"), L("star_fire", duration=24), L("flames_on")], {"size": 1.3}),
    "flareblitz": ("fire", P, charge("impact", L("flames_on"), L("burst", size=1.3), aura=["minecraft:flame"]), {"size": 1.3}),
    "willowisp": ("fire", T, [L("wisps", duration=30)], {"color": "#5A6CFF", "accent": "#C0E0FF"}),
    "heatwave": ("fire", S, [L("heat_wave", "travel", duration=20), L("flames_on")], {"size": 1.3}),
    "overheat": ("fire", S, [L("engulf", "wind", particles=["minecraft:flame"]), L("stream_fire", "travel", size=1.5), L("star_fire", size=1.2)], {"size": 1.3}),
    "sacredfire": ("fire", P, [L("stream_fire", "travel"), L("flames_on", duration=24), L("rays", size=0.7)], {"accent": "#FFFFFF"}),
    "firepunch": ("fire", P, charge("fist", L("flames_on")), {}),
    "mysticalfire": ("fire", S, [L("stream_fire", "travel"), L("glare"), L("flames_on")], {"color": "#FF4FA8", "accent": "#FFB040"}),
    "pyroball": ("fire", P, [L("engulf", "wind"), L("lob", "travel", count=1), L("star_fire")], {"size": 1.2}),
    "bitterblade": ("fire", P, charge("sword", L("flames_on"), L("heal", delay=6, duration=14)), {}),
    "fierydance": ("fire", S, [L("dance", "wind", duration=12), L("stream_fire", "travel"), L("flames_on")], {}),
    "hiddenpowerfire": ("fire", S, [L("orb", "travel"), L("flames_on")], {}),
    # ---- grass ----
    "gigadrain": ("grass", S, [L("orb_leaves", "travel"), L("heal", delay=4, duration=16)], {}),
    "energyball": ("grass", S, [L("gather", "wind"), L("orb_leaves", "travel"), L("burst")], {}),
    "leafstorm": ("grass", S, [L("tornado", duration=28, block="minecraft:oak_leaves", particles=["minecraft:cherry_leaves"])], {"size": 1.2}),
    "leechseed": ("grass", T, [L("lob", "travel", count=3), L("seeds", duration=28)], {}),
    "powerwhip": ("grass", P, [L("whip", duration=20)], {"size": 1.2}),
    "woodhammer": ("grass", P, charge("hammer", L("burst"), dash=False), {"size": 1.2}),
    "ivycudgel": ("grass", P, charge("hammer", L("seeds", duration=14), dash=False, ), {"size": 1.2}),
    "grassyglide": ("grass", P, charge("impact", L("cracks"), aura=["minecraft:happy_villager"]), {}),
    "solarbeam": ("grass", S, [L("solar_charge", "wind"), L("beam", "travel", size=1.6), L("burst", size=1.4)], {"accent": "#FFFFC0"}),
    "spore": ("grass", T, [L("cloud", duration=28), L("notes", delay=10)], {"color": "#C8E070"}),
    "synthesis": ("grass", T, [L("solar_charge", "self", duration=14), L("heal", "self")], {}),
    "strengthsap": ("grass", T, [L("seeds", duration=18), L("heal", delay=10, duration=16)], {}),
    # ---- psychic ----
    "psychic": ("psychic", S, [L("glare", "wind"), L("lift", duration=26)], {"size": 1.2}),
    "psyshock": ("psychic", S, [L("gather", "wind"), L("psy_shards", "travel"), L("burst")], {}),
    "storedpower": ("psychic", S, [L("aura", "wind"), L("psy_shards", "travel", count=9), L("burst")], {}),
    "psychicnoise": ("psychic", S, [L("sound_rings", "travel", duration=14), L("lift", duration=14)], {}),
    "expandingforce": ("psychic", S, [L("rays", "wind"), L("lift"), L("burst", size=1.4)], {"size": 1.2}),
    "futuresight": ("psychic", S, [L("glare", "wind"), L("orb_rings", "travel"), L("rays")], {"size": 1.2}),
    "calmmind": ("psychic", T, [L("aura", "self", particles=["minecraft:enchant"]), L("glare", "self", duration=12)], {}),
    "trick": ("psychic", T, [L("lift", duration=24), L("knock_item")], {}),
    "teleport": ("psychic", T, [L("aura", "self"), L("rays", "self", duration=10, delay=20)], {}),
    "trickroom": ("psychic", T, [L("wall", "self", size=2.0), L("rays", "self", duration=12)], {}),
    "reflect": ("psychic", T, [L("wall", "self")], {"color": "#FFB0FF"}),
    "lightscreen": ("psychic", T, [L("wall", "self")], {"color": "#FFF0A0"}),
    "mirrorcoat": ("psychic", T, [L("dome", "self"), L("rays", "self", duration=10)], {}),
    "rest": ("psychic", T, [L("notes", "self", duration=30), L("heal", "self")], {"color": "#C0C0FF"}),
    "agility": ("psychic", T, [L("dance", "self"), L("dash", "self", duration=12)], {}),
    "healingwish": ("psychic", T, [L("rays", "self"), L("heal", "self")], {}),
    # ---- ghost ----
    "shadowsneak": ("ghost", P, [L("shadow_hand", "travel", duration=12), L("claw")], {}),
    "poltergeist": ("ghost", P, [L("glare", "wind"), L("knock_item"), L("wisps", duration=16)], {"size": 1.2}),
    "shadowclaw": ("ghost", P, charge("claw"), {}),
    "hex": ("ghost", S, [L("glare", "wind"), L("wisps", duration=22)], {}),
    # ---- dragon ----
    "dracometeor": ("dragon", S, [L("gather", "wind"), L("meteors", count=6, duration=28)], {"size": 1.2}),
    "dragondance": ("dragon", T, [L("dance", "self", particles=["minecraft:dragon_breath"]), L("engulf", "self", duration=14, delay=18)], {}),
    "outrage": ("dragon", P, charge("barrage", L("impact", size=1.3, delay=10), aura=["minecraft:dragon_breath"]), {}),
    "dragondarts": ("dragon", P, [L("psy_shards", "travel", count=2), L("impact"), L("impact", delay=5)], {}),
    "dragonpulse": ("dragon", S, [L("gather", "wind"), L("beam_rings", "travel"), L("burst")], {}),
    "dragontail": ("dragon", P, [L("tail")], {"size": 1.2}),
    "scaleshot": ("dragon", P, [L("psy_shards", "travel", count=5), L("impact")], {}),
    "dragonclaw": ("dragon", P, charge("claw"), {}),
    # ---- flying ----
    "hurricane": ("flying", S, [L("tornado", duration=30, particles=["minecraft:cloud"])], {}),
    "airslash": ("flying", S, [L("crescents", "travel", count=3), L("slash")], {}),
    "bravebird": ("flying", P, charge("wings", L("impact", size=1.3), aura=["minecraft:cloud"]), {"size": 1.3}),
    "dualwingbeat": ("flying", P, charge("wings", L("wings", delay=6)), {}),
    "acrobatics": ("flying", P, [L("spin", "wind"), L("dash", "travel"), L("kick"), L("wings", delay=4)], {}),
    "roost": ("flying", T, [L("wings", "self", duration=14), L("heal", "self")], {}),
    "defog": ("flying", T, [L("tornado", duration=26, particles=["minecraft:cloud"])], {"color": "#E0F0FF"}),
    "tailwind": ("flying", T, [L("dance", "self", particles=["minecraft:cloud"])], {}),
    # ---- poison ----
    "sludgebomb": ("poison", S, [L("lob", "travel", count=3), L("splat")], {}),
    "gunkshot": ("poison", P, [L("lob", "travel", count=5), L("splat", size=1.3)], {}),
    "sludgewave": ("poison", S, [L("wave", "travel", duration=22), L("splat")], {}),
    "toxic": ("poison", T, [L("lob", "travel", count=1), L("cloud", duration=28, particles=["minecraft:witch"])], {}),
    "toxicspikes": ("poison", T, [L("caltrops", duration=26, block="minecraft:amethyst_cluster")], {}),
    "poisonjab": ("poison", P, charge("stab", L("splat", size=0.6)), {}),
    "mortalspin": ("poison", P, [L("spin", "wind", duration=20), L("splat"), L("cloud", duration=12)], {}),
    "malignantchain": ("poison", S, [L("whip", "travel", duration=10), L("cloud", duration=20), L("splat", size=0.7)], {}),
    # ---- steel ----
    "flashcannon": ("steel", S, [L("gather", "wind"), L("beam_rings", "travel"), L("burst", size=1.2)], {}),
    "ironhead": ("steel", P, charge("impact", L("burst"), aura=["minecraft:crit"]), {}),
    "heavyslam": ("steel", P, [L("dash", "travel"), L("hammer", block="minecraft:iron_block"), L("quake", size=0.6)], {"size": 1.3}),
    "bulletpunch": ("steel", P, charge("fist"), {"accent": "#FFFFFF"}),
    "irondefense": ("steel", T, [L("shell", "self")], {}),
    "makeitrain": ("steel", S, [L("coins", "travel", count=10), L("coins", count=18, duration=24)], {"color": "#FFD040", "accent": "#FFF4B0"}),
    "gigatonhammer": ("steel", P, [L("hammer", block="minecraft:iron_block", size=1.4), L("quake", size=0.8)], {"size": 1.3}),
    "steelbeam": ("steel", S, [L("engulf", "wind"), L("beam", "travel", size=1.5), L("burst", size=1.4)], {}),
    # ---- normal ----
    "protect": ("normal", T, [L("dome", "self")], {"color": "#7FD8FF", "accent": "#E0FAFF"}),
    "substitute": ("normal", T, [L("shell", "self"), L("burst", "self", duration=10)], {}),
    "swordsdance": ("steel", T, [L("swords", "self")], {}),
    "recover": ("normal", T, [L("heal", "self", particles=["minecraft:happy_villager"])], {"color": "#FFE070"}),
    "slackoff": ("normal", T, [L("heal", "self", particles=["minecraft:happy_villager"]), L("notes", "self", duration=14)], {"color": "#FFE070"}),
    "wish": ("normal", T, [L("meteors", "self", count=1, duration=16), L("heal", "self")], {"color": "#FFF0A0"}),
    "extremespeed": ("normal", P, [L("dash", "travel", duration=4), L("impact"), L("impact", delay=3, size=0.7), L("rays", size=0.6)], {}),
    "fakeout": ("normal", P, [L("burst", "wind", duration=6), L("fist", size=0.8)], {}),
    "facade": ("normal", P, charge("impact", L("burst"), aura=["minecraft:angry_villager"]), {}),
    "doubleedge": ("normal", P, charge("impact", L("burst", size=1.3), aura=["minecraft:crit"]), {"size": 1.3}),
    "bodyslam": ("normal", P, [L("dash", "travel"), L("impact", size=1.3), L("quake", size=0.5)], {}),
    "hypervoice": ("normal", S, [L("sound_rings", "travel", duration=16), L("burst")], {}),
    "boomburst": ("normal", S, [L("sound_rings", "travel", duration=16, size=1.5), L("rays", size=1.2), L("burst", size=1.4)], {}),
    "rapidspin": ("normal", P, [L("spin", "wind", duration=20), L("dash", "travel"), L("impact")], {}),
    "encore": ("normal", T, [L("notes", duration=28)], {}),
    "yawn": ("normal", T, [L("cloud", duration=20, size=0.7), L("notes", duration=26)], {"color": "#C0C0FF"}),
    "glare": ("normal", T, [L("glare", duration=24)], {"color": "#FFE040"}),
    "haze": ("ice", T, [L("cloud", "self", size=2.0)], {"color": "#D0E0F0"}),
    "roar": ("normal", T, [L("sound_rings", "travel", duration=14, size=1.4)], {}),
    "whirlwind": ("flying", T, [L("tornado", duration=24)], {}),
    "shellsmash": ("normal", T, [L("shell", "self"), L("rays", "self", delay=18, duration=12)], {"color": "#FFB040"}),
    "terablast": ("normal", S, [L("gather", "wind"), L("beam", "travel"), L("rays"), L("burst", size=1.3)], {"accent": "#FFFFFF"}),
    "terastarstorm": ("normal", S, [L("gather", "wind"), L("meteors", count=8, duration=28)], {"color": "#FFFFFF", "accent": "#A0E0FF"}),
    "weatherball": ("normal", S, [L("orb_rings", "travel"), L("burst")], {}),
    "courtchange": ("normal", T, [L("wall", "self", duration=16), L("rays", duration=16)], {}),
    "painsplit": ("normal", T, [L("glare", duration=16), L("heal", "self", delay=8, duration=20)], {}),
}

CATEGORY_FALLBACK = {P: "physical", S: "special", T: "status"}


def main():
    os.makedirs(ROOT, exist_ok=True)
    for name in os.listdir(ROOT):
        if name.endswith(".json"):
            os.remove(os.path.join(ROOT, name))
    for move, (kind, category, layers, extra) in MOVES.items():
        color, accent, particles = TYPES[kind]
        spec = {"category": category, "color": color, "accent": accent, "layers": []}
        for layer in layers:
            layer = dict(layer)
            if "particles" not in layer and layer["phase"] == "hit":
                layer["particles"] = particles
            spec["layers"].append(layer)
        spec.update(extra)
        with open(os.path.join(ROOT, move + ".json"), "w", encoding="utf-8", newline="\n") as out:
            json.dump(spec, out, indent=2)
            out.write("\n")
    print(len(MOVES), "moves")


if __name__ == "__main__":
    main()
