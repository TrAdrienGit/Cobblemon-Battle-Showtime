# Cobblemon Battle Showtime

**Turn every Cobblemon battle into a show.**

Cobblemon Battle Showtime stages your Pokémon battles: a cinematic camera, drawn intros, 883 redrawn moves, grand
Z-Moves, Mega Evolution and Terastallization set pieces, animated trainers, battle ambiances and your own music.

**Client-side only.** Install it on your game: it works on any Cobblemon server, with nothing to add on the server and
nothing for the other players to install.

<!-- GIF: a move filmed by the cinematic camera -->

## 🎬 Cinematic camera

The camera frames each move, follows the attacker to its target and closes in on hits and knock-outs. One click on the
battle screen switches to the free camera, and back.

## ⚔️ 883 redrawn moves

Every move has its own effect, built from Minecraft's own particles, blocks and items. No asset taken from the games.

- 180 moves made by hand, the others composed from each move's own data: type, category, punch, bite, sound, wave,
  projectile, healing…
- **34 Z-Moves in three acts**, far bigger than any move.
- A Pokémon whose model has no attack animation lunges, recoils or hops instead, and sometimes cries.

<!-- GIF: a Z-Move -->

## ✨ Mega Evolution, Z-Power, Terastallization

Mega Evolution, Primal Reversion, Z-Power and Terastallization each get their own set piece.

## 🌩️ A living battlefield

- Weather, terrains, Trick Room and the other rooms, entry hazards, Light Screen and Reflect on each side.
- Substitute, Leech Seed, confusion and other effects shown on the Pokémon, plus end-of-turn damage.
- Poké Balls thrown and recalled in time with Cobblemon. Send-out, recall and knock-out effects are tinted after the
  ball.

## 🪩 Ten battle ambiances

Disco, cosmos, storm, spectral, nature, night, arcade, fairy, silver screen, corrupted. Pick one in the menu.
Resource packs can add more.

<!-- Screenshot: the disco and cosmos ambiances -->

## 🕺 Animated trainers

Trainers guard, throw, recall, order attacks and celebrate or lose, with several animation profiles to pick from.
Needs Player Animation Library, which is also bundled in Emotecraft.

## 🎵 Your own battle music

Drop `.ogg` files in a resource pack for battles, victories and defeats. Pick a track, skip it or set the
volume right from the battle screen. A ready-made pack template is on GitHub.

## What gets staged

| Battle | Intro | Camera | Music | Moves, balls, effects | Mega / Z / Tera |
|---|---|---|---|---|---|
| Player vs player | ✅ | ✅ | ✅ | ✅ | ✅ |
| NPC trainer | — | ✅ | ✅ | ✅ | ✅ |
| Wild Pokémon | — | — | — | — | — |

Mega, Z and Tera set pieces play when the server allows these mechanics (base Cobblemon does not). The server never waits for the show: battles keep their usual pace.

## How to use

- **N** opens the Showtime menu: trainer animations, ambiance, music and settings, with a live preview. The key can be
  changed in Options → Controls. With Mod Menu, the menu also opens from the mod list.
- In battle, the panel at the top right holds the music controls and the camera switch.
- Each kind of battle (player vs player, NPC trainer) has its own settings: camera, intro, music, Poké Ball effects.

## Requirements

- Minecraft 1.21.1, Fabric Loader 0.18.1+
- Fabric API
- Cobblemon 1.8.1+

**Optional:** Player Animation Library or Emotecraft (animated trainers), Mod Menu, Cobblemon Battle Extras (works
alongside).

Works with shaders (Iris).

## Links

- Source code, music pack template and issues: https://github.com/TrAdrienGit/Cobblemon-Battle-Showtime
- License: Apache 2.0

*A fan project, not affiliated with Nintendo, The Pokémon Company, Game Freak or the Cobblemon team.*
