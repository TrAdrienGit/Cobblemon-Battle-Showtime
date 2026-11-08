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

## Camera (`battle/BattleCameraDirector`, `battle/BattleCinematic`)

- Cinematic camera during the battle: wide shots, the attacker then the target, close-ups on hits, switch-ins and
  knock-outs (3 s).
- First throw of a classic battle filmed (not visible in player vs player with an intro, which hides it).
- Free camera ⇄ cinematic with one click on the battle screen; per-battle-kind default in the settings.
- No first-person hand during cinematic shots.

## Intros (`battle/BattleIntroScreen`)

- Five drawn intros after different Pokémon games' styles, picked at random for player vs player. Foreign battles pick
  theirs through the API.
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

- Ten ambiances: disco, cosmos, storm, spectral, nature (meadow), night (starry), arcade, fairy, silver screen
  (old film), corrupted.
- Layers: time and day speed, sky, fog, clouds, stars, a backdrop hiding the world (cosmos), rainbow, spotlights,
  floor lights, floating blocks, lightning, auroras, shooting stars, rifts, strobe, ambient sound, screen filters
  (black and white, pixels, glitch, ripple, old TV).
- Reactions to battle moments: send-out, attack, critical hit, knock-out, victory.
- Seen from afar: a player near a battle's field sees its decorations; the world-wide parts (time, sky, weather,
  filter, sound) apply once inside the field's circle.
- Resource packs can add ambiances. Another mod can force one for a battle (`ShowtimeHooks.setAmbianceOverride`).

## Trainers (`emote/`, needs Player Animation Library)

- Moments: guard (idle loop), throw, recall, attack order, victory, defeat.
- Animation profile: 3 guards, 3 throws, 2 recalls, 3 attack orders, 3 victories, 2 defeats; packs can add more.
- Trainers face their opponent during the battle, whatever their player looks at.
- Without the library, everything else works.

## Music (`audio/`)

- Tracks from resource packs per moment: lobby, intro, battle, victory, defeat (jingles capped at 10 s). The older
  `assets/phantasmon/sounds/music/` layout is still read.
- A pack track replaces the music the server plays through Cobblemon; without one, Cobblemon's stays.
- In battle: current track (click → list, wheel to scroll, click to play), volume − / bar / +, next track.
- Untick tracks in the menu. Volume on top of Minecraft's Music slider.

## Menu and settings (`gui/`, `config/`)

- One key (N, rebindable), also opened from Mod Menu; no commands.
- Sections: trainer animation profile, ambiance, music, settings; live preview in the real world (not during a
  battle).
- Settings per battle kind: cinematic camera at start, intro, music, ball effects. Saved atomically in
  `config/cobblemon_battle_showtime.json` and `config/cobblemon_battle_showtime-music.json`.
- English and French translations.

## Compatibility

- Shaders (Iris): ambiances and effects render correctly; elbows and knees stay straight under shaders (see
  known-bugs.md).
- DeltaClient (Cobblemon Academy modpack): staging survives its cancelled handlers.
- Cobblemon Battle Extras: the panel moves aside and draws under its overlays.
