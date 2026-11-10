# Cobblemon Battle Showtime

**Turn every Cobblemon battle into a show.**

Cobblemon Battle Showtime stages your Pokémon battles like a 3D Pokémon game: a cinematic camera, sixteen battle
intros, 883 redrawn moves, grand Z-Moves, Mega Evolution and Terastallization set pieces, animated trainers, battle
ambiances and your own music.

**Client-side only.** Install it on your game: it works on any Cobblemon server, with nothing to add on the server and
nothing for the other players to install.

<!-- GIF: a move filmed by the cinematic camera -->

## A cinematic camera

Low shots at the Pokémon's height that drift slowly, joined by clean cuts, as in the stadium games. Each move is filmed
behind the attacker, then cuts to the target the moment the hit lands. The camera keeps clear of trees, walls and
people, and never shakes. One click switches to the free camera, and back.

## Console like battle intros

Every player vs player battle opens on an intro, each after a different Pokémon game, from the Game Boy to today:
classic transitions redrawn over a 3D battlefield (a clock sweep that turns Game Boy greens into colour, a staircase
of blocks, blinds, a rolling ball), stadium-style match cards and entrances filmed in your own world, a vortex, a
lightning surge, a twisting world, a showdown with no cut at all…

Pick the ones you like in the menu: filters by generation, favourites, "only play my favourites", and a preview of
each one.

<!-- GIF: two or three intros -->

## A lot of redrawn moves !

Every move has its own effect, built from Minecraft's own particles, blocks and items. No asset taken from the games.

- 180 moves made by hand, the others composed from each move's own data: type, category, punch, bite, sound, wave,
  projectile, healing…
- **34 Z-Moves in three acts**, far bigger than any move.
- A Pokémon whose model has no attack animation lunges, recoils or hops instead, and sometimes cries.

<!-- GIF: a Z-Move -->

## Mega Evolution, Z-Power, Terastallization compatibility

Mega Evolution, Primal Reversion, Z-Power and Terastallization each get their own set piece.

## A living battlefield

- Weather, terrains, Trick Room and the other rooms, entry hazards, Light Screen and Reflect on each side.
- Substitute, Leech Seed, confusion and other effects shown on the Pokémon, plus end-of-turn damage.
- Poké Balls thrown and recalled in time with Cobblemon. Send-out, recall and knock-out effects are tinted after the
  ball.

## Battle ambiances

Disco, cosmos, storm, spectral, nature, night, arcade, fairy, silver screen, corrupted. Pick one in the menu. They
settle on the floor of the battle, even in a hollow or on uneven ground. Resource packs can add more.

<!-- Screenshot: the disco and cosmos ambiances -->

## Animated trainers

Trainers guard, throw, recall, order attacks and celebrate or lose, with several animation profiles to pick from.
Needs Player Animation Library, which is also bundled in Emotecraft.

## Your own battle music

Showtime plays the music of your own resource pack, by moment: battle, victory, defeat. In the menu, browse your
tracks by game generation, star your favourites, play only them, or switch whole groups on and off. In battle, pick a
track, skip it or set the volume right from the battle screen.

No music is included (game soundtracks belong to their owners): a ready-made, empty pack template and its guide are on
GitHub. Name your files `<game>_<title>.ogg` (`oras_vs_wally.ogg` shows as "ORAS · Vs Wally"); the repository's tools
can rename your files and even out their volume.

## What gets staged

| Battle | Intro | Camera | Music | Moves, balls, effects | Mega / Z / Tera |
|---|---|---|---|---|---|
| Player vs player | ✅ | ✅ | ✅ | ✅ | ✅ |
| NPC trainer | — | ✅ | ✅ | ✅ | ✅ |
| Wild Pokémon | — | — | — | — | — |

Mega, Z and Tera set pieces play when the server allows these mechanics (base Cobblemon does not). The server never
waits for the show: battles keep their usual pace.

## How to use

- **N** opens the Showtime menu: settings, trainer animations, ambiance, music and intros, with a live preview. The key
  can be changed in Options → Controls. With Mod Menu, the menu also opens from the mod list.
- In battle, the panel at the top right holds the music controls and the camera switch.
- Each kind of battle (player vs player, NPC trainer) has its own settings: camera, intro, music, Poké Ball effects.
- Showtime tells you in the chat when a new version is out (can be switched off in the settings, see below).

## Network and privacy

Showtime connects to one place only, on its own: at game launch it asks Modrinth's public API
(`api.modrinth.com`) whether a newer version of Showtime exists. The request carries the installed version of
Showtime in its User-Agent and, like any connection, your IP address; it sends no username, no account data and nothing
about your game. Turn it off in the menu (**N** → Settings → "Check for updates at launch"): then Showtime makes no
connection at all. Everything else it does is local: it only reads what the server you joined already sends you.

## Dependencies

**Required mods**

| Mod | Version |
|---|---|
| [Fabric Loader](https://fabricmc.net/use/) | 0.18.1 or later, Minecraft 1.21.1, Java 21 |
| [Fabric API](https://modrinth.com/mod/fabric-api) | any for 1.21.1 |
| [Cobblemon](https://modrinth.com/mod/cobblemon) | 1.8.1 or later |

**Optional mods**

| Mod | Adds |
|---|---|
| [Player Animation Library](https://modrinth.com/mod/playeranimator) (or Emotecraft, which bundles it) | Animated trainers. Without it, everything else works. |
| [Mod Menu](https://modrinth.com/mod/modmenu) | Opens the Showtime menu from the mod list. |
| [Cobblemon Battle Extras](https://modrinth.com/mod/cobblemon-battle-extras) | Works alongside: the battle panel makes room for it. With its server side, the opponent's team size shows in the intros. |
| [Iris](https://modrinth.com/mod/iris) | Works with shaders. |

**Resource packs:** none needed. Showtime only uses its own assets and Cobblemon's. Battle music comes from a resource
pack you make yourself (see above); without one, Cobblemon's own battle music plays.

## How it was made

Showtime was developed with the help of generative AI coding tools, under its author's direction and testing in game.
Its effects are drawn in code from Minecraft's own particles, blocks and items; it contains no game assets and no
music.

## Links

- Source code, music pack template and issues: https://github.com/TrAdrienGit/Cobblemon-Battle-Showtime
- License: Apache 2.0

*A fan project, not affiliated with Nintendo, The Pokémon Company, Game Freak or the Cobblemon team.*
