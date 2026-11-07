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

# ---------------------------------------------------------------- every other move (Adrien 2026-11-07)
# The moves above are composed by hand. Every other move of the battle engine is composed here from its own data
# (tools/move_data.json, exported from Cobblemon's Showdown: type, category, flags, target, effects) and its name,
# so each still gets a look that matches what it is: a punch is a fist, a sound move rings, a pulse throws rings, a
# draining move heals its user, a stat move glows on its user...
DATA = os.path.join(os.path.dirname(__file__), "move_data.json")


def has(name, *words):
    return any(w in name for w in words)


# Element worn on a blow, by type.
TYPE_HIT = {
    "fire": L("flames_on"), "electric": L("sparks_on"), "water": L("splash"), "ice": L("freeze"), "poison": L("splat"),
    "ground": L("cracks"), "rock": L("burst", size=1.1), "psychic": L("rays", size=0.7), "fairy": L("rays", size=0.7),
    "ghost": L("wisps", duration=16), "dragon": L("burst", size=1.2), "grass": L("seeds", duration=14),
}
TYPE_AURA = {
    "fire": ["minecraft:flame"], "electric": ["minecraft:electric_spark"], "water": ["minecraft:splash"],
    "ice": ["minecraft:snowflake"], "ghost": ["minecraft:soul"], "dragon": ["minecraft:dragon_breath"],
    "poison": ["minecraft:witch"], "fighting": ["minecraft:crit"],
}


def blow(name, m):
    """The blow of a contact move, from its flags first, then its name."""
    f = m["flags"]
    if m["multihit"] and "punch" not in f:
        return "barrage"
    if "punch" in f:
        return "fist"
    if "bite" in f or has(name, "fang", "bite", "crunch", "jaw"):
        return "bite"
    if has(name, "kick", "stomp", "foot", "heel", "knee"):
        return "kick"
    if "slicing" in f or has(name, "blade", "sword", "cut", "slash", "scythe", "edge", "cleave"):
        return "sword" if has(name, "sword", "blade", "edge", "cleave") else "slash"
    if has(name, "claw", "scratch", "swipe", "fury"):
        return "claw"
    if has(name, "tail"):
        return "tail"
    if has(name, "whip", "lash", "vine", "wrap", "bind", "squeeze", "constrict", "tentacle"):
        return "whip"
    if has(name, "horn", "drill", "peck", "jab", "spear", "needle", "pin", "sting", "beak", "poke", "lance"):
        return "stab"
    if has(name, "wing", "fly", "aerial", "air", "bird"):
        return "wings"
    if has(name, "hammer", "club", "bone"):
        return "hammer"
    return "impact"


def projectile(name, m):
    """What a ranged move throws, from its flags first, then its name and type."""
    f, t = m["flags"], m["type"]
    if "sound" in f:
        return [L("sound_rings", "travel", duration=14), L("burst")]
    if "pulse" in f:
        return [L("gather", "wind"), L("dark_rings" if t == "dark" else "beam_rings", "travel", duration=12), L("burst")]
    if "wind" in f or has(name, "storm", "gust", "hurricane", "twister", "tornado", "cyclone", "whirl"):
        return [L("tornado", duration=22)]
    if has(name, "beam", "ray", "laser", "cannon", "blast") and t != "fire" and not has(name, "bubble"):
        return [L("gather", "wind"), L("ice_beam" if t == "ice" else "beam", "travel"), L("burst")]
    if "bullet" in f or has(name, "ball", "sphere", "orb", "bomb", "bullet", "shot", "seed", "egg"):
        motif = "orb_leaves" if t == "grass" else "homing" if t in ("fighting", "psychic") else "orb"
        return [L(motif, "travel"), L("burst")]
    if t == "fire":
        return [L("stream_fire", "travel")] if m["target"] == "normal" else [L("heat_wave", "travel", duration=20)]
    if t == "water":
        return [L("jets_water", "travel", count=2), L("splash")] if m["target"] == "normal" else [L("wave", "travel", duration=20), L("splash")]
    if t == "electric":
        return [L("sparks_on", "wind", duration=10), L("bolt", "travel"), L("sparks_on")]
    if t == "grass":
        return [L("orb_leaves", "travel"), L("seeds", duration=14)]
    if t == "psychic":
        return [L("psy_shards", "travel"), L("rays", size=0.7)]
    if t == "dragon":
        return [L("gather", "wind"), L("beam", "travel"), L("burst", size=1.2)]
    if t == "ghost" or t == "dark":
        return [L("gather", "wind"), L("dark_rings", "travel", duration=12), L("burst")]
    if t == "ground":
        return [L("cracks", "travel", duration=10), L("pillars")]
    if t == "rock":
        return [L("rise_rocks")] if m["category"] == "physical" else [L("fall_rocks", count=5, duration=22)]
    if t == "ice":
        return [L("ice_beam", "travel"), L("freeze")]
    if t == "fairy":
        return [L("rays", "wind"), L("orb", "travel"), L("rays")]
    if t == "poison":
        return [L("lob", "travel"), L("splat"), L("cloud", duration=12, size=0.8)]
    if t == "steel":
        return [L("gather", "wind"), L("beam", "travel"), L("burst")]
    if t == "flying":
        return [L("crescents", "travel"), L("burst")]
    if t == "bug":
        return [L("orb", "travel"), L("burst")]
    return [L("gather", "wind"), L("orb", "travel"), L("burst")]


def attack(name, m):
    t = m["type"]
    contact = "contact" in m["flags"]
    if m["category"] == "physical" and contact:
        layers = charge(blow(name, m), *([TYPE_HIT[t]] if t in TYPE_HIT else []), aura=TYPE_AURA.get(t) if m["priority"] <= 0 and has(name, "charge", "rush", "crash", "flare", "volt", "wild") else None)
        if m["recoil"] or has(name, "slam", "crash", "press", "body"):
            layers.append(L("quake", size=0.5))
    elif m["category"] == "physical":
        if t == "ground" or has(name, "quake", "bulldoze", "magnitude", "earth"):
            layers = [L("quake", "travel", duration=22), L("cracks")]
        elif t == "rock" or has(name, "rock", "stone", "boulder"):
            layers = [L("fall_rocks", count=5, duration=22)]
        elif t == "ice" and has(name, "shard", "icicle", "spike", "avalanche", "ice"):
            layers = [L("fall_rocks", count=4, block="minecraft:packed_ice", duration=18), L("freeze")]
        else:
            layers = projectile(name, m)
    else:
        layers = projectile(name, m)
    if m["drain"]:
        layers.append(L("heal", delay=6, duration=16))
    if m["ohko"]:
        layers.append(L("rays", size=1.4))
    return layers


SIDE = {
    "reflect": "wall", "lightscreen": "wall", "auroraveil": "wall", "safeguard": "dome", "mist": "cloud",
    "tailwind": "tornado", "luckychant": "rays", "matblock": "wall", "quickguard": "dome", "wideguard": "wall",
    "craftyshield": "dome",
}
STATUS_LOOK = {
    "slp": [L("cloud", duration=20, size=0.7), L("notes", duration=24)],
    "par": [L("bolt", "travel"), L("sparks_on", duration=24)],
    "brn": [L("wisps", "travel", duration=20), L("flames_on", duration=18)],
    "psn": [L("lob", "travel"), L("splat"), L("cloud", duration=16, size=0.8)],
    "tox": [L("lob", "travel"), L("splat"), L("cloud", duration=20, size=0.9)],
    "frz": [L("ice_beam", "travel"), L("freeze")],
}


def status(name, m):
    t = m["type"]
    if "protect" in name or name in ("endure", "kingsshield", "spikyshield", "banefulbunker", "obstruct", "silktrap", "burningbulwark"):
        return [L("dome", "self")]
    if m["weather"] or m["terrain"] or m["pseudo"]:
        return [L("cloud", "self", size=2.0, duration=24), L("rays", "self", duration=16)]
    if m["side"]:
        side = m["side"].lower()
        if side in ("spikes", "toxicspikes", "stealthrock", "stickyweb"):
            return [L("caltrops", duration=26)]
        return [L(SIDE.get(side, "dome"), "self", duration=20)]
    if m["heal"] and m["target"] in ("self", "allies", "allySide", "adjacentAllyOrSelf"):
        return [L("heal", "self", particles=["minecraft:happy_villager"])]
    boosts = m["selfBoost"]
    if boosts and m["target"] == "self" or (boosts and not m["targetBoost"] and m["target"] in ("self", "allySide")):
        layers = [L("aura", "self", particles=TYPE_AURA.get(t, ["minecraft:crit"]))]
        if boosts.get("atk", 0) >= 2:
            layers.append(L("swords", "self", delay=10))
        elif boosts.get("def", 0) > 0 or boosts.get("spd", 0) > 0:
            layers.append(L("shell", "self", duration=12, delay=14))
        elif boosts.get("spe", 0) > 0 or "dance" in m["flags"]:
            layers.append(L("dance", "self"))
        return layers
    if m["status"] in STATUS_LOOK:
        return STATUS_LOOK[m["status"]]
    if m["volatile"] in ("confusion", "attract"):
        return [L("psy_shards", "travel"), L("notes", duration=20)]
    if m["volatile"] in ("taunt", "torment", "encore", "disable"):
        return [L("taunt", duration=24)]
    if m["targetBoost"]:
        return [L("glare", duration=20)] if "sound" not in m["flags"] else [L("sound_rings", "travel", duration=14)]
    if "sound" in m["flags"]:
        return [L("sound_rings", "travel", duration=14), L("notes", duration=16)]
    if m["target"] == "self":
        return [L("aura", "self", particles=TYPE_AURA.get(t, ["minecraft:end_rod"]))]
    return [L("glare", duration=20)]


def auto_moves():
    with open(DATA, encoding="utf-8") as source:
        data = json.load(source)
    moves = {}
    for move, m in sorted(data.items()):
        if move in MOVES or move == "shadowball":
            continue
        name = m["name"].lower()
        category = {"physical": P, "special": S}.get(m["category"], T)
        layers = status(name, m) if category == T else attack(name, m)
        moves[move] = (m["type"], category, layers, {})
    return moves


# ---------------------------------------------------------------- Z-Moves (Adrien 2026-11-07: grandiose)
# Each Z-Move stacks three acts, bigger and longer than any ordinary move: the user charges up in a pillar of its
# type (aura, rays, gathering light), a huge attack crosses the field, then the target is struck by a finale (a
# ring of rays, a giant burst, the ground shaking) — twice the size of a normal move.
Z_OPEN = [L("engulf", "wind", duration=30, size=1.6), L("rays", "wind", duration=30, size=1.8), L("gather", "wind", duration=30, size=1.6)]
Z_FINALE = [L("rays", delay=4, duration=26, size=2.2), L("burst", delay=6, size=2.4), L("quake", delay=8, duration=20, size=1.2)]

# The attack itself, by Z-Move.
Z_ATTACK = {
    "breakneckblitz": [L("dash", "travel", duration=10), L("impact", size=2), L("barrage", size=1.6)],
    "alloutpummeling": [L("homing", "travel", size=2), L("barrage", size=1.8), L("fist", delay=10, size=2.2)],
    "supersonicskystrike": [L("tornado", "travel", duration=20, size=1.4), L("dash", "travel", duration=6), L("wings", size=2), L("impact", size=2)],
    "aciddownpour": [L("lob", "travel", size=2), L("splat", size=2.2), L("cloud", duration=30, size=2.2)],
    "tectonicrage": [L("cracks", "travel", duration=14, size=1.8), L("pillars", size=2), L("quake", duration=26, size=1.6)],
    "continentalcrush": [L("fall_rocks", count=10, block="minecraft:stone", duration=30, size=2.2), L("impact", size=2)],
    "savagespinout": [L("web", "travel", duration=14, size=1.8), L("spin", duration=20, size=1.6), L("impact", size=1.8)],
    "neverendingnightmare": [L("dark_rings", "travel", duration=18, size=2), L("shadow_hand", size=2), L("wisps", duration=30, size=2)],
    "corkscrewcrash": [L("spin", "wind", duration=16, size=1.4), L("dash", "travel"), L("stab", size=2.2), L("impact", size=2)],
    "infernooverdrive": [L("orb", "travel", size=2.6), L("flames_on", duration=28, size=2.2), L("heat_wave", duration=20, size=1.8)],
    "hydrovortex": [L("wave", "travel", duration=22, size=1.8), L("tornado", duration=22, size=1.6, particles=["minecraft:splash"]), L("splash", size=2.2)],
    "bloomdoom": [L("orb_leaves", "travel", size=2.2), L("seeds", duration=30, size=2.2), L("rays", size=1.6)],
    "gigavolthavoc": [L("bolt", "travel", size=2), L("sky_bolt", duration=26, size=1.8), L("sparks_on", duration=26, size=2.2)],
    "shatteredpsyche": [L("lift", duration=24, size=1.8), L("psy_shards", size=2.2), L("impact", delay=16, size=2)],
    "subzeroslammer": [L("ice_beam", "travel", size=2), L("fall_rocks", count=8, block="minecraft:packed_ice", duration=26, size=2), L("freeze", size=2.2)],
    "devastatingdrake": [L("engulf", "wind", duration=20, size=2, particles=["minecraft:dragon_breath"]), L("dash", "travel"), L("bite", size=2.2), L("burst", size=2)],
    "blackholeeclipse": [L("dark_rings", "travel", duration=20, size=2.2), L("orb", size=3, color="#1A0A24"), L("cloud", duration=30, size=2.4)],
    "twinkletackle": [L("rays", "travel", duration=14, size=2), L("dash", "travel"), L("impact", size=2), L("rays", size=2)],
    # Signature Z-Moves.
    "catastropika": [L("sparks_on", "wind", duration=20, size=2), L("dash", "travel"), L("impact", size=2.4), L("sky_bolt", duration=20, size=2)],
    "10000000voltthunderbolt": [L("bolt", "travel", size=2.4), L("bolt", "travel", delay=4, size=2), L("sky_bolt", duration=24, size=2), L("sparks_on", size=2.4)],
    "stokedsparksurfer": [L("wave", "travel", duration=20, size=1.6), L("bolt", size=2), L("sparks_on", duration=24, size=2)],
    "sinisterarrowraid": [L("lob", "travel", size=1.4), L("stab", size=2), L("stab", delay=6, size=1.6), L("wisps", duration=24, size=2)],
    "maliciousmoonsault": [L("lift", "wind", duration=16, size=1.6), L("dash", "travel"), L("impact", size=2.4), L("flames_on", duration=24, size=2)],
    "oceanicoperetta": [L("notes", "wind", duration=24, size=1.8), L("orb", "travel", size=2.6), L("splash", size=2.4), L("wave", duration=20, size=1.6)],
    "guardianofalola": [L("fall_rocks", count=1, block="minecraft:chiseled_stone_bricks", duration=30, size=4), L("impact", size=2.4)],
    "pulverizingpancake": [L("lift", "wind", duration=18, size=1.8), L("impact", size=3), L("quake", duration=30, size=2)],
    "genesissupernova": [L("orb", "travel", size=2.4), L("star_fire", size=2.4), L("psy_shards", duration=30, size=2.2)],
    "splinteredstormshards": [L("rise_rocks", size=2.4), L("fall_rocks", count=8, duration=26, size=1.8), L("rays", size=1.8)],
    "letssnuggleforever": [L("shadow_hand", "travel", size=1.8), L("barrage", size=2), L("cloud", duration=24, size=1.8)],
    "clangoroussoulblaze": [L("sound_rings", "travel", duration=20, size=2.2), L("meteors", count=6, duration=26), L("burst", size=2.2)],
    "searingsunrazesmash": [L("solar_charge", "wind", duration=30, size=2), L("dash", "travel"), L("impact", size=2.6), L("flames_on", duration=24, size=2.2)],
    "menacingmoonrazemaelstrom": [L("moon", "wind", duration=30, size=2.2), L("beam", "travel", size=2.6), L("dark_rings", duration=24, size=2.2)],
    "lightthatburnsthesky": [L("solar_charge", "wind", duration=30, size=2.4), L("beam", "travel", size=3), L("star_fire", size=2.6)],
    "soulstealing7starstrike": [L("shadow_hand", "travel", size=2), L("barrage", size=2.2), L("meteors", count=7, duration=28, size=1.4)],
}


# Type and category of each Z-Move, from the battle engine (Z-Moves are left out of move_data.json).
Z_INFO = {
    "10000000voltthunderbolt": ("electric", S),
    "aciddownpour": ("poison", P),
    "alloutpummeling": ("fighting", P),
    "blackholeeclipse": ("dark", P),
    "bloomdoom": ("grass", P),
    "breakneckblitz": ("normal", P),
    "catastropika": ("electric", P),
    "clangoroussoulblaze": ("dragon", S),
    "continentalcrush": ("rock", P),
    "corkscrewcrash": ("steel", P),
    "devastatingdrake": ("dragon", P),
    "genesissupernova": ("psychic", S),
    "gigavolthavoc": ("electric", P),
    "guardianofalola": ("fairy", S),
    "hydrovortex": ("water", P),
    "infernooverdrive": ("fire", P),
    "letssnuggleforever": ("fairy", P),
    "lightthatburnsthesky": ("psychic", S),
    "maliciousmoonsault": ("dark", P),
    "menacingmoonrazemaelstrom": ("ghost", S),
    "neverendingnightmare": ("ghost", P),
    "oceanicoperetta": ("water", S),
    "pulverizingpancake": ("normal", P),
    "savagespinout": ("bug", P),
    "searingsunrazesmash": ("steel", P),
    "shatteredpsyche": ("psychic", P),
    "sinisterarrowraid": ("ghost", P),
    "soulstealing7starstrike": ("ghost", P),
    "splinteredstormshards": ("rock", P),
    "stokedsparksurfer": ("electric", S),
    "subzeroslammer": ("ice", P),
    "supersonicskystrike": ("flying", P),
    "tectonicrage": ("ground", P),
    "twinkletackle": ("fairy", P),
}


def z_moves():
    moves = {}
    for move, layers in Z_ATTACK.items():
        kind, category = Z_INFO[move]
        moves[move] = (kind, category, Z_OPEN + layers + Z_FINALE, {"size": 1.6})
    return moves


def main():
    os.makedirs(ROOT, exist_ok=True)
    for name in os.listdir(ROOT):
        if name.endswith(".json"):
            os.remove(os.path.join(ROOT, name))
    auto = auto_moves()
    zmoves = z_moves()
    for move, (kind, category, layers, extra) in {**MOVES, **auto, **zmoves}.items():
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
    print(len(MOVES), "moves by hand,", len(auto), "composed from their data,", len(zmoves), "Z-Moves")


if __name__ == "__main__":
    main()
