# Field ambiances: rework plan (2026-11-19)

> **Status (2026-11-19):** done for every field. Caves (`cavern`), waters (`sea`), lands (`land`) and halls (`hall`)
> are stadiums; each field's ambiance adds on top (see data-formats.md and Events `RebornAmbiance`).

> **Superseded in part by [stadiums.md](stadiums.md)**: what replaces blocks becomes a stadium, field ambiances only
> add on top and attach their props to the surfaces they find. Read that first; the per-field ideas below still hold.

The first 19 field ambiances (caves, waters, nature/fire/poison, machines, mountains; `tools/ambiances.py`) only use
the existing light layers: particles, mist, veins and a filter. Judged too light: they must reach the level of Cosmos
and Ultra Space, where the **world itself is replaced by a built scene**. Keep the ids, names and named moments; rebuild
the content.

## Rule for every field ambiance

- **A set piece that changes the place**: `hide_world` plus a scene built around the field (walls, a sea, a forest, a
  hall), not effects floating in the player's own world.
- **Something always moving** in that scene (waves, gears, falling leaves, lava, fish), visible from the battle camera.
- **Named moments that change the scene** (rocks falling, ice growing, a geyser bursting), not only particles.
- **Transitions must read**: two fields of one family share their scene and change its materials (cave → crystal cave:
  the same dome, crystals growing), so the switch looks like a transformation.

## New shared layers to build (BattleAmbiance + Ambiance)

| Layer | What it draws | Used by |
|---|---|---|
| `dome` | Closed rock vault round the field: uneven walls, stalactites and stalagmites, pillars; colours set per field | caves |
| `crystals` | Large crystal clusters on the walls and floor (reuse `crystal()`/`crystalGlint()`), slow pulse, light beams between them | crystal, dark crystal |
| `lava_lake` | Animated lava surface round the field, lavafalls from the walls, crust plates drifting | dragon's den, superheated (cracks only) |
| `sea` | Water plane to the horizon, rolling waves (vertex displacement), sun glitter; colour, clarity and oil sheen per field | water surface, murkwater, icy (frozen: still plane with cracks and floes) |
| `water_ceiling` | The surface seen from below: rippling bright ceiling, Snell's window | underwater |
| `seabed` | Sand floor with coral and rocks, procedural | underwater |
| `schools` | Fish schools swirling round the field (boids), a large silhouette passing far off | underwater, water surface (under the surface) |
| `trees` | Ring of procedural trees (trunks, layered canopy) closing overhead; `burning: true` puts fire sheets on them and drops burning branches | forest, burning |
| `grass` | Dense animated grass blades with visible wind waves, flowers | grassy, flower gardens |
| `fog_banks` | Rolling volumes of fog crossing the field | misty, corrosive mist |
| `pools` | Bubbling sludge pools, dead trees, rusted barrels round the field | corrosive, murkwater shore |
| `hall` | Industrial hall: walls, catwalks, conveyor belts carrying crates round the field, gears turning, presses stamping on a beat; `broken: true` stops it, hangs sparking cables, red emergency lights | factory, short-circuit |
| `peaks` | The field on a rocky summit: a sea of clouds below, layered ranges in parallax; `snow: true` adds snow caps and plumes blown off the ridges | mountain, snowy mountain |
| several `floating_blocks` | A list instead of one entry (asked before) | all |

## Per field (signature and named moments)

- **Cave**: dark dome, dripping stalactites, a lamp-lit haze. `cave_rumble`: stalactites shake and some fall; `cave_collapse`: rocks crash down round the field, dust fills the dome.
- **Crystal Cavern**: same dome, crystals of every colour growing out of the walls, light beams refracting between them, prism glints on the floor.
- **Dark Crystal Cavern**: black crystals with a violet core pulsing slowly, light dying in waves.
- **Dragon's Den**: dome over a lava lake, lavafalls, piles of gold glinting, a dragon's skeleton (procedural bones) half buried, a breath of heat now and then.
- **Water Surface**: open sea to the horizon, rolling waves, sun glitter, gulls; fish shadows under the surface.
- **Underwater**: seabed, coral, swirling fish schools, the bright surface rippling overhead, a large silhouette passing.
- **Murkwater**: the same sea dark and oily, rainbow slicks, sludge pipes pouring in, dead fish floating.
- **Icy**: the sea frozen, cracked ice plane, floes, aurora. `ice_spikes`: spikes grow out of the ice; `steam`: the ice hisses and clouds rise.
- **Grassy**: rolling meadow of animated grass to the horizon, wind waves, flowers.
- **Forest**: trees closing overhead, god rays, falling leaves, fireflies.
- **Burning**: the forest on fire, burning trees, smoke columns, falling burning branches.
- **Super-Heated**: cracked earth with glowing fissures, a warped white sun. `steam`: a geyser bursts.
- **Corrosive**: sludge pools bubbling, dead trees, rusted barrels, toxic veins.
- **Corrosive Mist**: violet fog banks rolling over the field. `mist_combusted`: the fog explodes in a fireball wave.
- **Misty**: pink fog banks, fairy lights, soft hills.
- **Factory**: the hall running, conveyors, gears, presses on the beat, welding sparks.
- **Short-Circuit**: the hall dead, sparking cables, red emergency lights, flickering darkness. `bzzt0`–`bzzt4`: arcs between pylons, bigger each time.
- **Mountain**: summit over a sea of clouds, ranges in parallax, eagles. `mountain_snow`: snow sweeps in.
- **Snowy Mountain**: the same summit snowed in, snow plumes blown off the ridges.

## Order

One family per working session, tested in game before the next: caves, waters, machines, mountains, nature/fire/poison,
then the fields not done yet (flower gardens 1–5, New World/Starlight, the rest).

The Events side (`RebornAmbiance` field → ambiance mapping, named moments forwarded from every `league_reborn.text.*`)
is done and does not change.
