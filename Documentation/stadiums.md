# Stadiums and ambiances: the split (plan, 2026-11-19)

Today one ambiance JSON holds two different things: the **place** (the world hidden, a floor, a vault, an island,
stands) and the **atmosphere** (sky, fog, light, particles, sounds, filters, moments). A scene ambiance hides any arena
a player built, so a gym leader must choose between their own build and a strong effect. The plan: split the two, so
any stadium (or none: the real world) can be combined with any ambiance.

## The rule

- **Stadium**: anything that **replaces or hides** blocks. `hide_world`, its own floor, walls, vault, islands, lava
  lake, stands, court.
- **Ambiance**: anything **added on top** of what is there. Sky, time, fog, light, particles, sounds, ambient sounds,
  filters, moments, and **props** (crystals, stalactites, drips, kelp, mushrooms...) that attach to the surfaces they
  find.

| Today | Stadium | Ambiance |
|---|---|---|
| Cosmos | the void, planets, galaxy | shooting stars, light, moments |
| Ultra Space | field island, floating islands | nebulae, wormholes, motes |
| Champion's Stage | court, stands, crowd, light towers | spotlights, followspots, fireworks, confetti |
| Caves | vault, floor, pillars (Cave); lava lake, hoard, bones (Volcanic Den) | crystals, drips, dust, rockfall |
| Disco, Storm, Spectral, Fairy... | none | all of it |

## Anchors: how an ambiance finds where to put its props

An ambiance never knows the place. It asks for **anchors**: surfaces round the field where a prop can stand, hang or
cling. Anchors come from one interface, `AnchorSource`, with two implementations:

- **The real world** (no stadium): a scan of the blocks round the field.
- **A stadium**: its own built surfaces (the cave's vault, the island's rim), given exactly, no scan.

So the same crystal ambiance grows crystals on a player's stone walls, on the Cave stadium's vault, or on Ultra Space's
island rim.

### Anchor kinds

| Kind | What it is | Typical props |
|---|---|---|
| `floor` | top face of a solid block with air above | stalagmites, standing crystals, mushrooms, gold heaps |
| `wall` | side face of a solid block with air beside it | crystals reaching out, moss, lava seeping |
| `ceiling` | bottom face of a solid block with air below | stalactites, hanging crystals, drips |
| `float` | a point in the air round the field, nothing needed | floating crystals, motes, lanterns |

### The scan (real world)

- **Volume**: a cylinder round the field: radius `fieldRadius + 12`, from 2 blocks under the field's ground to
  **`ceilingReach` blocks above it (default 14)**. Nothing beyond counts.
- **A ceiling 100 blocks up is no ceiling**: it is out of reach, so the battle is treated as under open sky. The same
  for a roof one block above a corner of the arena far from the field: only faces inside the volume count.
- **Seen from the field**: a face counts only if a ray from the field's centre (2 blocks up) reaches it without
  crossing a solid block. Props never grow inside a wall, behind a pillar or in the next room.
- **Not on the battle**: no anchor inside the field's radius (where the Pokémon and trainers stand), and none within
  2 blocks of a trainer.
- **Once, then kept**: scanned when the ambiance starts (spread over a few ticks, as a budget of columns per tick),
  kept for the battle. Blocks broken during a battle are not followed (no battle changes the arena).
- **Spread**: anchors are thinned to a minimum spacing (about 1.5 blocks), then picked by a fixed seed, so the same
  arena always looks the same.

### Each prop declares its anchors in order of preference

```json
"props": [
  {"kind": "crystal", "anchors": ["ceiling", "wall", "floor", "float"], "count": 40, ...},
  {"kind": "stalactite", "anchors": ["ceiling"], "count": 60, ...},
  {"kind": "drip", "anchors": ["ceiling"], ...}
]
```

- The prop takes the anchors of its first kind that has any; if there are fewer than `count`, the rest go to the next
  kind, and so on.
- **A kind with no fallback is simply left out**: under open sky there are no stalactites and no drips; nothing is
  invented.
- `float` is the last resort for props that make sense in the air: floating crystals, slowly turning, round the field
  (as the old Crystal Cavern's amethyst did).

### The cases asked about

- **Crystal cave ambiance, open sky, flat ground**: no ceiling, no wall. Crystals go to `floor` (clusters standing round
  the field, on the ground beyond the field's edge) and the rest `float` round it. Prism beams, which fall from the
  ceiling, fall from the floating crystals instead, or are left out if none float. Drips are left out.
- **Same, open sky, the arena has walls**: crystals on the walls first, then on the floor; no stalactites.
- **A ceiling 100 blocks up** (a huge hall, a cave biome): beyond `ceilingReach`, so it is open sky as above. An
  ambiance may raise its own `ceiling_reach` (up to a hard cap of 32) when it really wants high vaults.
- **A low ceiling (4 blocks)**: stalactites are shortened to fit (at most a third of the gap to the floor), so nothing
  reaches the Pokémon.
- **With the Cave stadium**: the vault gives `ceiling` and `wall` anchors everywhere, so the full cave look comes back,
  identical to today's rebuilt caves.

### What stays world-free

Sky, fog, particles in the air, sounds, filters, moments: they need no anchor and behave as today.

## The light between the two

A stadium's materials take the ambiance's light: the cave rock warmed by lava or tinted by crystals. The ambiance
gives `light: {color, strength, from}` (`from`: the field, the sky, the ground); the stadium reads it to shade its
faces. With no stadium, nothing reads it (Minecraft lights the real blocks), except the props, which use it for their
glow.

## Files and data

- `assets/<ns>/stadiums/<id>.json`: the stadium layers moved out of `Ambiance` (`hide_world`, `cavern` vault and floor,
  `island`, `islands`, `court`, `crowd`, `light_towers`, `backdrop` and planets for Cosmos, the lava lake, the hoard).
  Generated by `tools/stadiums.py`, like the ambiances.
- `assets/<ns>/ambiances/<id>.json`: what is left, plus `props` and `light`.
- `assets/<ns>/presets/<id>.json`: `{"stadium": "...", "ambiance": "..."}`. Cosmos, Ultra Space, Champion's Stage keep
  their names as presets, so a player who used them sees no change.
- Later: a stadium built from a schematic (`.nbt`/`.schem` rendered as blocks without placing them), as its own layer
  type; the anchor scan then runs on the schematic's blocks instead of the world's.

## Menu

The ambiance page gets two choices: **Stadium** (first entry: "None, the world as it is") and **Ambiance**; presets on
top as shortcuts. A stadium can list recommended ambiances, shown first. The preview plays both.

## Public API (backward compatible)

- Keep `ShowtimeHooks.setAmbianceOverride` / `addAmbianceOverride` as they are. An overriding id that names a preset
  sets both stadium and ambiance; one that names an ambiance sets only the ambiance (the stadium stays the player's).
- Add `addStadiumOverride(Supplier<String>)`, with the same live switching (polled twice a second, a flash on change).
- `ambianceMoment(name)` unchanged.

## Phantasmon Events

- A Reborn field imposes its **ambiance only** by default: a gym leader's own arena stays visible.
- A scripted battle may impose a stadium too (a lobby setting: "stadium: Cave", or the gym's preset).
- The field → ambiance mapping (`RebornAmbiance`) is unchanged; a second, optional map gives the stadium a field
  suggests (the caves → Cave, Dragon's Den → Volcanic Den, the waters → a sea stadium...).

## Steps

1. `AnchorSource` + the world scan + `props` (crystal, stalactite, stalagmite, drip) in ambiances; the cave props move
   out of `cavern`.
2. Stadiums: loader, `stadiums/` files, the stadium layers moved out of `Ambiance`, the Cave and Volcanic Den stadiums,
   the stadium as an `AnchorSource`, the shared light.
3. Presets, so Cosmos, Ultra Space and Champion's Stage keep working unchanged; check every existing ambiance renders
   as before (hard rule: foreign battles stay intact).
4. Menu (two choices, presets) and the public API addition; `api.md`, `data-formats.md`, `features.md`.
5. Events: ambiance only by default, stadium optional.
6. Then the remaining Reborn families, built as anchored ambiances (+ a stadium when the field is a place: the sea,
   the factory hall, the summit).
