# Cobblemon Battle Showtime

**Turn every Cobblemon battle into a show.** A client-side Fabric mod for Minecraft 1.21.1 and Cobblemon 1.8.1: a
cinematic camera, drawn intros, 883 redrawn moves, grand Z-Moves, Mega Evolution and Terastallization set pieces,
animated trainers, battle ambiances and your own battle music.

It is **client-side only**: install it on your game and it works on any Cobblemon server, with nothing to add on the
server and nothing for the other players to install.

## Features

- **Cinematic camera**: low shots at the Pokémon's height, drifting slowly and joined by cuts, as in the stadium
  games; each move is filmed behind the attacker, then cuts to the target on the hit. It keeps clear of blocks and
  people. Switch to the free camera at any time with one click.
- **Sixteen battle intros**: player-versus-player battles open on an intro after a Pokémon game, from the Game Boy
  to today; pick your favourites, by generation, in the menu.
- **883 redrawn moves**: every move gets its own effect, built from Minecraft particles, blocks and items (no asset
  taken from the games). 180 are hand-made; the rest are composed from each move's own data: type, category, punch,
  bite, sound, wave, projectile, healing… Pokémon without an attack animation lunge, recoil or hop instead, and
  sometimes cry.
- **34 grand Z-Moves**, in three acts and far bigger than any move.
- **Mega Evolution, Primal Reversion, Z-Power and Terastallization** get their own set piece.
- **Poké Balls**: trainers throw and recall in time with Cobblemon; send-out, recall and knock-out have their effects,
  tinted after the ball.
- **Lasting effects drawn on the field**: weather, terrains, Trick Room and the other rooms, entry hazards, screens on
  each side, effects on a Pokémon (Substitute, Leech Seed, confusion…) and end-of-turn damage.
- **Ten battle ambiances**: disco, cosmos, storm, spectral, nature, night, arcade, fairy, silver screen, corrupted.
  Resource packs can add more.
- **Animated trainers** (with Player Animation Library): guard, throw, recall, attack order, victory and defeat, with
  several animation profiles to pick from.
- **Your own battle music**: drop `.ogg` files in a resource pack for battles, victories and defeats; pick,
  skip and set the volume right from the battle screen.

| Battle | Intro | Camera | Music | Moves, balls, effects | Mega / Z / Tera |
|---|---|---|---|---|---|
| Player vs player | Yes | Yes | Yes | Yes | Yes |
| NPC trainer | No | Yes | Yes | Yes | Yes |
| Wild Pokémon | No | No | No | No | No |

Mega, Z and Tera set pieces play when the server allows these mechanics (base Cobblemon does not; servers such as Cobblemon Academy do). The server never waits for the show: battles run at their usual pace and the show plays alongside.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.18.1 or later for Minecraft 1.21.1.
2. Put in your `mods` folder:
   - [Fabric API](https://modrinth.com/mod/fabric-api)
   - [Cobblemon](https://modrinth.com/mod/cobblemon) 1.8.1 or later
   - Cobblemon Battle Showtime
3. Optional:
   - [Player Animation Library](https://modrinth.com/mod/playeranimator) (also bundled in Emotecraft): animated
     trainers. Without it, everything else works.
   - [Mod Menu](https://modrinth.com/mod/modmenu): opens the settings from the mod list.
   - No resource pack is needed: battle music comes from a pack you make yourself (see below).
   - [Cobblemon Battle Extras](https://modrinth.com/mod/cobblemon-battle-extras): works alongside; Showtime's battle
     panel moves aside to make room for it.

## How to use it

Press **N** (rebind it in Options → Controls) to open the Showtime menu: trainer animation profile, ambiance, music
and settings, with a live preview in your own world.

In battle, the panel at the top right of the battle screen gives you:

- the track playing: click it for the list of tracks (scroll with the mouse wheel), click a track to play it;
- `−` / bar / `+`: music volume (click on the bar to set it);
- `⏭`: next track;
- the camera button: cinematic ⇄ free.

### Settings

For each kind of battle (player vs player, NPC trainer): cinematic camera at the start or free camera, intro, music,
Poké Ball effects. Plus the music volume, on top of Minecraft's Music slider. Everything is in the menu, and saved in
`config/cobblemon_battle_showtime.json`.

### Adding your own music

1. Copy `resourcepack-template/Cobblemon Battle Showtime Resource Pack` (from the
   [GitHub repository](https://github.com/TrAdrienGit/Cobblemon-Battle-Showtime)) into your `resourcepacks` folder.
2. Drop `.ogg` files in `assets/cobblemon_battle_showtime/sounds/music/` under `battle`, `victory`
   or `defeat`.
3. Enable the pack in game (or press F3 + T if it is already on).

A track from the pack replaces the music the server plays through Cobblemon; with no pack, Cobblemon's music stays.

## Compatibility

- Client-side only; any Cobblemon server, vanilla or modded.
- Works with shaders (Iris).
- [Phantasmon](https://github.com/TrAdrienGit/Phantasmon-Client) uses it for its own battles.

## Development

- JDK 25 to run Gradle (Loom), Java 21 bytecode. `./gradlew build publishToMavenLocal`: Phantasmon builds against the
  jar published to Maven local.
- Moves: `python tools/moves.py` rewrites `assets/cobblemon_battle_showtime/moves/` from `tools/move_data.json`
  (taken from Cobblemon's battle engine). A resource pack can add or replace a move.
- Developer documentation: [`Documentation/`](Documentation/README.md); coding agents start with [`CLAUDE.md`](CLAUDE.md).
- Rule: every change keeps Phantasmon battles intact. Phantasmon uses only the public API (the `api` package and the
  public methods of `BattleCinematic`, `BattleCameraDirector`, `BattleShowtime`).

## License

Apache License 2.0, see [`LICENSE`](./LICENSE). Free to use, modify and redistribute; forks and redistributions must
keep [`NOTICE`](./NOTICE) and credit the original author.

Cobblemon Battle Showtime is a fan project, not affiliated with Nintendo, The Pokémon Company, Game Freak or the
Cobblemon team.
