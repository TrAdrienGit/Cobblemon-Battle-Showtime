# Completed features

Everything here is in 1.0.0 and was checked in game unless marked otherwise. When a feature lands, add it here and
remove it from roadmap.md.

## Battles staged

| Battle | Intro | Camera | Music | Moves, balls, effects | Mega / Z / Tera |
|---|---|---|---|---|---|
| Player vs player | Yes | Yes | Yes | Yes | Yes (when the server allows them) |
| NPC trainer | No | Yes | Yes | Yes | Yes (when the server allows them) |
| Wild Pokémon | No (by design) | No | No | No | No |
| Foreign battle through the API | Driven by that mod | Yes | Yes | Yes | Yes |

## Camera (`battle/BattleCameraDirector`, `battle/BattleCameraV2`, `battle/BattleCinematic`)

- **Second camera (v2, default since 2026-11-09, `BattleCameraV2`)**, after Pokémon Battle Revolution: low shots at the
  Pokémon's height, each drifting slowly, joined by clean cuts. A move is a three-quarter shot behind the attacker,
  then a cut onto the target when the hit lands (Cobblemon's impact cue, or 1.6 s), held while it reacts. Shots are
  checked before they start (sampled along their length; inside blocks or blocks hiding the subjects → a mirrored,
  closer or higher variant); while one plays, a spring arm keeps the eye in front of anything that comes between it
  and its subject; the field's anchors and the eye and aim are damped springs, so lunges and bobs don't shake the
  frame. People around the field (trainers, players, NPCs) count as obstacles too. Validated in game 2026-11-09.
- **Camera version setting:** "Battle camera: latest (v2) / v1 / v2". "Latest" (the default, stored as 0) always
  plays the newest camera, so a fresh install gets each new version; a number stays on that one. The first camera is
  kept as a fallback.
- **First camera (v1):**

- Cinematic camera during the battle: wide shots, the attacker then the target, close-ups on hits, switch-ins and
  knock-outs (3 s).
- First throw of a classic battle filmed (not visible in player vs player with an intro, which hides it).
- Free camera ⇄ cinematic with one click on the battle screen; per-battle-kind default in the settings.
- Hiding the battle screen with Cobblemon's R key frees the camera too; showing it again brings the cinematic camera
  back, unless the player had switched it off themselves (2026-11-11).
- No first-person hand during cinematic shots.

## Intros (`battle/BattleIntroScreen`)

- **Sixteen intros**, each with its own length and the generation of the game it is after:
  - 2D: X/Y (eyes-meet box, diamond wipe, panel), Sword/Shield (diagonal VS split), Diamond/Pearl (Poké Ball wipe,
    burst, platforms), Emerald (rolling ball), Battle Revolution **match card** (blue stage, team cards, VS, ball wipe),
    Sun/Moon **vortex** (rainbow burst, the opponent posing in a turning vortex, their throw), Ultra Sun/Moon
    **surge** (colour starburst, out of the white into a ring of lightning, iris closing on a Poké Ball), Omega Ruby/Alpha
    Sapphire **silhouette** (checker of diamonds, balls rolling across the black, the silhouette taking its colours).
  - Retro revisits, the old 2D transitions redrawn over a 3D battlefield (2026-11-10): Red/Blue **clock** (two hands
    sweeping to black, the field in Game Boy greens, the colour flooding in), Gold/Silver **stairs** (flashes, a
    staircase of raised blocks, warm Johto light, silhouettes taking colour), Diamond/Pearl **blinds** (two flashes,
    a spinning Poké Ball, blinds sliding shut and open). Their shared battlefield (`backdrop`, `platform`) has a layered
    sky, sun, clouds, hills, mown ground and grass, and platforms with shadow and thickness.
  - World camera: Colosseum **twisting world** (the view spinning into a spiral, a wide shot of the opponent, both
    from the ground), Black/White (zoom into the opponent), Battle Revolution **entrance** (wide orbit under
    "<biome> Colosseum", close shots with names and lines), Battle Revolution **face-off** (montage of rising and
    arcing close shots, both in profile round a "VS"), Scarlet/Violet **showdown** (over each shoulder, name tags, a
    crane rising on "Battle start!", no screen at all).
- **Intros page in the menu**, as the music's: generation filters, favourites, on / off, "only play my favourites",
  enable / disable all, and a preview (in a world, outside a battle; back to the page after). A player vs player battle
  picks among the player's enabled intros; all off = no intro. Choices in `config/cobblemon_battle_showtime-intros.json`.
- Battles staged through the API (Phantasmon) keep the intro their mod picks: the ordinal is shared, new intros always
  go at the end of `BattleCinematic.Intro`.
- The trainers are drawn at any distance during the intro; the stand-ins keep their own angle.
- The battle runs underneath; Cobblemon's battle screen comes back when the intro ends.

## Moves (`move/`)

- 883 moves redrawn: 180 hand-tuned specs, 669 composed from each move's data, 34 Z-Moves in three acts (charge,
  attack, finale; bigger and longer). Shadow Ball is fully hand-made (`ShadowBall`).
- More than 70 motifs: blows, claws, swords, punches, kicks, fangs, hammers, tails, wings, projectiles, beams, jets, waves,
  coins, meteors, rocks, tornadoes, the Fire Blast star, the Moon…, with real Minecraft blocks and items.
- Physical moves strike up close, special moves travel; timing follows Cobblemon's own cues.
- Pokémon whose model has no attack animation lunge (physical), recoil and thrust (special) or hop (status), with a
  40 % chance of a cry.
- Resource packs can add or replace a move (`assets/<namespace>/moves/<move>.json`).

## Poké Balls (`classic/BallSignals`, `move/BallOpening`, `move/BallRecall`)

- Trainers throw and recall in time with Cobblemon's beam.
- Send-out: flash, rays, light column, ground waves, rising particles, tinted after the ball.
- Recall: halo, closing ring, particles drawn back to the trainer.
- Knock-out: white burst, heavy waves, ashes, close-up.
- "Ball effects" switch per battle kind (the trainer animations and shots stay). No capture effects (by design).

## Lasting effects

- **Field** (`move/FieldEffects`): grassy (real grass and flowers), electric, psychic (runes) and misty terrains; Trick
  Room, Magic Room, Wonder Room, Gravity; sun, rain, sandstorm, snow, hail, and the primal weathers (drawn as sun and
  rain); per side: Stealth Rock, Spikes and Toxic Spikes by layer, Sticky Web, Reflect, Light Screen, Aurora Veil,
  Tailwind, Safeguard, Mist. Court Change swaps the sides.
- **On one Pokémon** (`move/PokemonEffects`): Substitute, Leech Seed, confusion, Taunt, Torment, Encore, Yawn, Perish
  Song (one pearl per turn left), Stockpile (1-3), Curse, Ingrain, Aqua Ring, Focus Energy, Magnet Rise, Charge, Salt
  Cure, Protosynthesis, Quark Drive, Flash Fire, Attract, Nightmare, Disable, Heal Block, Embargo, Laser Focus, Smack
  Down, Tar Shot, Octolock, No Retreat, Telekinesis, Uproar, Syrup Bomb, Future Sight, Doom Desire, Imprison. They end
  with their message or when the Pokémon leaves the field.
- **End-of-turn damage**: Cobblemon's own effects for Leech Seed, Fire Spin and Infestation; Magma Storm plays Fire
  Spin's.

## Set pieces (`battle/BattleShowtime`, `battle/ShowtimeOverlay`)

- Mega Evolution, Primal Reversion, Z-Power and Terastallization each have a set piece on both clients; the battle
  screen steps aside meanwhile.
- A terastallized Pokémon glows in its Tera type's colour.

## Ambiances (`ambiance/`)

- Eighteen ambiances: disco, cosmos, storm, spectral, nature (meadow), night (starry), arcade, fairy, silver screen
  (old film), corrupted, inferno, abyss, sakura (festival), blizzard, mirage, synthwave, ultra space, champion
  (Champion's Stage).
- Layers: time and day speed, sky, fog, clouds, stars, a backdrop hiding the world (cosmos), rainbow, spotlights,
  floor lights, floating blocks (they shrink away near the camera), lightning, auroras, shooting stars, rifts, strobe,
  ambient sound and occasional sounds, screen filters (black and white, pixels, glitch, ripple, old TV, old film);
  since the 2026-11-10 quality pass also a mirror ball and dance floor, a neon grid, 8-bit sprites, butterflies,
  nebulae, a galaxy band, detailed planets, ground mist, veins, sky cracks, glitch cubes, will-o'-wisps and fireflies,
  ghosts, a moon, darkness flickers, soap bubbles, glowing mushrooms, a double rainbow, film scratches and dust,
  embers, a volcano, lava cracks, sea-floor caustics, light shafts, kelp, rising paper lanterns, real fireworks,
  ice glints, a fog wall, a horizon mirage, circling birds, a striped 80s sun, palm silhouettes, wormholes, floating
  islands, the field's own floating island (its top laid on the hidden ground), a court marked between the trainers,
  stands with a cheering crowd and camera flashes, light towers, followspots on the Pokémon, flame fountains,
  confetti, a fanfare. Far things on the sky
  (moon, volcano, sun, mirage) are drawn without fog, so a close fog never swallows them.
- Particle budget: at most 10 ambiance particles per tick, all kinds together; beyond it every kind is scaled down.
- Reactions to battle moments: send-out, attack, critical hit, knock-out, victory.
- Placed on the floor of the battle (2026-11-10): its height is the lowest of the trainers and the active Pokémon,
  and floor lights are laid point by point on the block surface below them, following hollows, steps and slopes.
- Seen from afar: a player near a battle's field sees its decorations; the world-wide parts (time, sky, weather,
  filter, sound) apply once inside the field's circle.
- Resource packs can add ambiances. Another mod can force one for a battle (`ShowtimeHooks.setAmbianceOverride`).

## Trainers (`emote/`, needs Player Animation Library)

- Moments: guard (idle loop), throw, recall, attack order, victory, defeat.
- Animation profile: 3 guards, 3 throws, 2 recalls, 3 attack orders, 3 victories, 2 defeats; packs can add more.
- Trainers face their opponent during the battle, whatever their player looks at.
- Without the library, everything else works.

## Music (`audio/`)

- Tracks from resource packs per moment: battle (from the intro on), victory, defeat (jingles capped at 10 s). A mod
  with a battle lobby can turn on a lobby track (see api.md). The older
  `assets/phantasmon/sounds/music/` layout is still read.
- A pack track replaces the music the server plays through Cobblemon; without one, Cobblemon's stays.
- In battle: current track (click → list, wheel to scroll, click to play), volume − / bar / +, next track.
- Music page of the menu: filter by generation (All, Gen I to IX, Other for spin-offs and fan games), from the
  `<game>_` prefix of the file names, and a Favourites filter. Untick tracks there, star favourites, enable or
  disable every track shown at once, and "Only play my favourites" (a folder without a ticked favourite plays all its
  ticked tracks). Volume on top of Minecraft's Music slider.

## Menu and settings (`gui/`, `config/`)

- One key (N, rebindable), also opened from Mod Menu; no commands.
- Sections: trainer animation profile, ambiance, music, settings; live preview in the real world (not during a
  battle).
- Lists scroll with the mouse wheel and a draggable scrollbar (click on its track to jump).
- Settings per battle kind: cinematic camera at start, intro, music, ball effects.
- Calm effects (global, off by default): no strobe, no darkness flickers, flashes much softer (ambiance moments and
  the intro's closing flash), no screen filter on battle moments, an ambiance's moving filter dropped
  (`calm_filter`), half the ambiance particles. For photosensitive players and anyone who wants a quieter show. Saved atomically in
  `config/cobblemon_battle_showtime.json` and `config/cobblemon_battle_showtime-music.json`.
- English and French translations.

## Update check (`UpdateChecker`)

- At launch, off the main thread, Modrinth's public API is asked for the latest Fabric 1.21.1 version of
  `cobblemon-battle-showtime`. A newer one gets one chat message per session, on joining a world, with a link to the
  page. Silent without network or while the project is not published (404). Switch in the menu settings. Not tested
  against a real release yet.

## Compatibility

- Shaders (Iris): ambiances and effects render correctly; elbows and knees stay straight under shaders (see
  known-bugs.md).
- DeltaClient (Cobblemon Academy modpack): staging survives its cancelled handlers.
- Cobblemon Battle Extras: the panel moves aside and draws under its overlays.
