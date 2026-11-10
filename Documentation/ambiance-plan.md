# Ambiance quality pass: plan

Written 2026-11-10 from a recording of every ambiance in a real battle (about 40 s each). Goal: each ambiance
clearly its own, readable (the Pokémon always visible), never blinding, and richer in detail. Some must stay simple:
simple is a quality too (Starry Night, Silver Screen, Storm).

Status: **in progress**. Tick items off here as they land; move finished ambiances to features.md.

Done 2026-11-10 (built, not yet seen in game): E1 soft capped cones with near-camera fade (`beam`), new layers
`mirror_ball`, `dance_floor`, `neon_grid`, `sprites`, `critters` and a moment `text`, a `TINT` render type for solid
shapes; Meadow, Disco and Arcade remade. Validated in game the same day.

Done 2026-11-10 (built, not yet seen): E3 sky layers (`nebulae`, `galaxy`, finer coloured stars), detailed
planets (`bands`, `atmosphere`, ring `tilt`, a soft terminator, a sun glow); Cosmos remade with them, its white specks
removed. Note: the yellow rings seen in the first Cosmos recording were Electric Terrain, not the ambiance.

## What the recording shows

| Ambiance | Seen | Verdict |
|---|---|---|
| Arcade | Purple night, pink / cyan / yellow spotlights, coloured floor spots, floating lamps | Same recipe as Disco: the two can't be told apart. Rebuild around its own theme. |
| Corrupted | Almost black and maroon, a few red streaks, the Pokémon hard to see | Too dark and muddy. Needs readable corruption, not just darkness. |
| Cosmos | Black backdrop, dense white specks like confetti, yellow rings on the ground, one small planet | Backdrop too plain, foreground specks cluttered. Needs a real sky: nebulae, detailed planets. |
| Disco | Purple sky, spotlights, coloured floor spots, floating lamps | Fine, but shares everything with Arcade. Give it its own signature (mirror ball, dance floor). |
| Fairy Tale | Lavender and pink sky, rainbow, floating amethyst, sunset tint | Pleasant, generic. More magic and softness. |
| Meadow | White sky, huge white light shafts in the middle of the field, pink petals | The shafts are far too bright and centred: they blind everyone and wash the Pokémon out. |
| Starry Night | Dark blue night, stars, aurora, shooting stars | Simple and liked. Keep, small touches only. |
| Silver Screen | Black and white film filter | Simple and liked. Keep, small touches only. |
| Spectral | Dark green-grey, a few flames | Not spooky enough. |
| Storm | Dark sky, rain, lightning | Liked as it is. |

## Shared engine work (first: every ambiance benefits)

| # | Work | Why |
|---|---|---|
| E1 | **Soft light cones**: a beam fades along its length and across its width, additive, its brightness capped; no beam may cross the field's middle at Pokémon height. | Meadow's blinding shafts; Disco and Arcade beams look flat. |
| E2 | **Near-camera fade** for every decoration (beams, floating blocks, crystals, sprites), as already done for floating blocks. | Nothing fills the screen during low camera shots. |
| E3 | **Sky dome**: a gradient sky (zenith → horizon) and layered sky objects: nebula clouds (soft colour blobs), planets with shading (lit side, terminator, atmosphere rim, rings), moons. | Cosmos, Night, Fairy, a future Abyss or Inferno. |
| E4 | **Ground mist**: low translucent drifting layers that follow the terrain (as the floor lights already do). | Spectral, Meadow mornings, Corrupted. |
| E5 | **Ground decals on terrain**: grids, runes, cracks, veins, tiles, caustics, laid on the block surface. | Arcade grid, Disco dance floor, Corrupted veins, Abyss caustics, Inferno lava cracks. |
| E6 | **Particle palettes and budgets** per ambiance: fewer, purposeful particles (no generic white specks), each with a size and a life. | Cosmos clutter, readability everywhere. |
| E7 | **Readability rule**: the Pokémon and trainers keep a minimum light level; darkness and filters apply to the world, not to them. | Corrupted, Spectral, Storm at night. |
| E8 | **"Effects intensity" setting** (full / reduced): reduced = no strobe, no flashes, softer filters. | Accessibility (photosensitivity), and players who want calm. |

## Per ambiance

### Arcade: a retro video game (rebuilt)

- **Remove**: spotlights, floor spots and floating lamps (Disco's recipe).
- **Add**: a neon grid laid on the ground and fading into the distance (E5); a black sky with pixel stars and a big
  pixel moon; floating pixel sprites (coins, hearts, stars) in 8-bit shapes; a light CRT filter (scanlines, slight
  curvature, very soft); square pixel particles.
- **Moments**: pixel text pop-ups over the Pokémon on a hit, a crit and a knock-out ("HIT!", "CRITICAL!", "K.O.!"),
  a "YOU WIN" banner on victory, chiptune blips.
- **Palette**: electric cyan, magenta and lime on black. No purple haze.

### Disco: the dance floor (signature)

- **Keep**: spotlights (with E1), the night sky.
- **Add**: a mirror ball turning above the field, throwing moving light dots over the ground and the trainers; a dance
  floor of coloured tiles on the ground pulsing in rhythm (E5); a warm haze.
- **Change**: palette toward gold, magenta, violet and white; fewer, softer floor spots; the floating lamps go.
- **Moments**: the floor flashes on a crit, confetti on victory.

### Corrupted: a world breaking apart

- **Change**: far less dark (E7): a desaturated, violet-tinted world, not a black one.
- **Add**: glowing magenta-red cracks across the sky, sharp and pulsing; corruption veins spreading on the ground from
  the rifts (E5); floating black cubes that glitch (jump, flicker, break into pixels); a soft chromatic aberration
  filter; ground mist in dark violet (E4).
- **Moments**: a glitch burst (screen tear) on a crit, a cube shattering on a knock-out.

### Cosmos: a detailed space

- **Add**: a real backdrop (E3): two or three nebula clouds in violet, blue and teal; a gas giant with bands, a lit side
  and rings; a smaller rocky planet and a moon; a faint galaxy band across the sky; twinkling stars of several sizes.
- **Remove**: the dense white specks in front of the camera (E6); keep a few slow drifting space dust motes.
- **Change**: the rings on the ground thinner and in two or three soft colours.
- **Moments**: a comet crossing on a crit, a supernova flash far away on a knock-out.

### Fairy Tale: soft magic

- **Keep**: the rainbow and the lavender sky.
- **Add**: a double rainbow with soft edges; glowing fireflies; floating bubbles catching the light; drifting flower
  petals; a ring of small glowing mushrooms around the field; sparkles around the floating crystals.
- **Change**: a pastel sky gradient (pink → lilac → light blue) instead of a flat tint.

### Meadow: a bright spring morning

- **Remove**: the central light shafts.
- **Add**: a few soft sun rays coming from the sun's side of the sky, far from the field, low alpha (E1); butterflies;
  drifting pollen and dandelion seeds; a light morning mist on the ground (E4); bird song.
- **Change**: a warm golden-hour palette, not a white sky.

### Starry Night: keep it simple

- **Keep**: everything.
- **Add**: a moon with a soft halo (E3), a few fireflies near the ground, crickets.

### Silver Screen: keep it simple

- **Keep**: the black and white film.
- **Add**: film grain, a soft vignette, a rare scratch line and a slight flicker; a projector sound.

### Spectral: really spooky

- **Add**: thick low ground mist (E4); will-o'-wisps (blue-green flames) drifting slowly round the field;
  translucent ghost silhouettes passing far away; a pale green moon; hanging chains or floating soul lanterns;
  whispers and distant chimes.
- **Change**: a cold palette (sickly green, grey, violet); short darkness flickers, as if the light died for a moment.
- **Moments**: a scream-like wind on a crit, a ghost rising from the Pokémon on a knock-out.

### Storm: as it is

- **Keep**: everything. Optional: puddle shimmer on the ground, a distant rumble between lightnings.

## New ambiances (ideas, each with its own identity)

| Ambiance | Look | Signature |
|---|---|---|
| Inferno | Red-black sky, ash falling, heat haze filter | Glowing lava cracks on the ground (E5), embers rising, a volcano glow on the horizon |
| Abyss | Deep blue-green, as under the sea | Light caustics dancing on the ground (E5), bubbles, light shafts from above, swaying kelp silhouettes |
| Sakura Festival | Spring dusk, warm lanterns | Cherry petals, floating paper lanterns, fireworks on victory |
| Blizzard | Daytime whiteout, cold blue | Snow driven by wind, ice crystals glinting, an icy fog wall round the field |
| Mirage | Desert noon | Heat shimmer, sand gusts, a huge pale sun, a mirage of the field on the horizon |
| Synthwave | Retro-futuristic sunset | A striped sun on the horizon, a neon grid to infinity, palm silhouettes (distinct from Arcade: smooth 80s, not 8-bit) |
| Ultra Space | Inverted, alien colours | Floating islands, slow-turning wormholes, low gravity motes |
| Champion's Stage | A league final | Stand-like light banks round the field, sweeping spotlights, confetti and fireworks on victory |

## Suggested order

1. Engine: E1 soft cones, E2 near-camera fade, E7 readability (they fix the worst problems everywhere).
2. Meadow (blinding), then Disco and Arcade (split their identities).
3. Engine E3 sky dome, then Cosmos.
4. Engine E4 mist and E5 decals, then Corrupted and Spectral.
5. Fairy Tale, then the small touches on Starry Night, Silver Screen and Storm.
6. E6 particle budgets across all, E8 intensity setting.
7. New ambiances, two or three at a time, each recorded in game before the next.
