# Publishing on Modrinth and CurseForge: release checklist

Page text for both sites: [`description.md`](description.md). Both accept Markdown; on CurseForge, pick the Markdown
editor when pasting. Replace the `<!-- GIF / Screenshot -->` comments with your images, once they are uploaded to the
gallery.

## Fields to fill in

| Field | Value |
|---|---|
| Name | Cobblemon Battle Showtime |
| Slug / URL | `cobblemon-battle-showtime` |
| Summary (Modrinth, 256 characters max) | Turn every Cobblemon battle into a show: cinematic camera, intros, 883 redrawn moves, grand Z-Moves, Mega and Tera set pieces, animated trainers, ambiances and your own music. Client-side only. |
| Summary (CurseForge) | Same text |
| Project type | Mod |
| Loader | Fabric |
| Minecraft version | 1.21.1 |
| Environment (Modrinth) | Client: required. Server: unsupported (not needed) |
| Categories (Modrinth) | Adventure, Decoration, Utility; Cobblemon add-on if offered |
| Categories (CurseForge) | Addons, Cosmetic, Miscellaneous; Fabric |
| License | Apache-2.0 |
| Source | https://github.com/TrAdrienGit/Cobblemon-Battle-Showtime |
| Issues | https://github.com/TrAdrienGit/Cobblemon-Battle-Showtime/issues |
| Icon | `src/main/resources/assets/cobblemon_battle_showtime/icon.png` |

## Dependencies to declare

| Mod | Type |
|---|---|
| Fabric API | Required |
| Cobblemon (1.8.1+) | Required |
| Player Animation Library (`playeranimator`) | Optional |
| Mod Menu | Optional |
| Cobblemon Battle Extras | Optional (compatible) |

## The file

- Jar: `build/libs/cobblemon-battle-showtime-1.0.0.jar` (not the `-sources` jar).
- Version number: `1.0.0`. Version name: `Cobblemon Battle Showtime 1.0.0`. Channel: Release.
- Game version 1.21.1, loader Fabric.

### Changelog 1.0.0

```
First public release.

- Cinematic battle camera, with a one-click switch to the free camera
- Drawn intros for player-versus-player battles
- 883 redrawn moves, 34 grand Z-Moves in three acts
- Mega Evolution, Primal Reversion, Z-Power and Terastallization set pieces
- Poké Ball throws, recalls and knock-outs, tinted after the ball
- Weather, terrains, rooms, hazards, screens and Pokémon effects drawn on the field
- Ten battle ambiances, more through resource packs
- Animated trainers (with Player Animation Library)
- Your own battle music through a resource pack, with in-battle controls
- One menu (N), no commands
```

## Gallery to prepare

Short GIFs or MP4s (Modrinth takes images and GIFs; CurseForge takes images, and videos through a YouTube link):

1. A move followed by the cinematic camera (Flamethrower or Thunderbolt).
2. A Z-Move, the whole set piece.
3. A Mega Evolution.
4. A player-versus-player intro.
5. Two or three ambiances (disco, cosmos, spectral).
6. Field effects: weather plus Light Screen or Spikes.
7. The N menu with the trainer preview.
8. The battle screen with the music panel.

Record in 1920×1080 if you can, without other mods' overlays, and with the HUD hidden (F1) for the field shots.

## Before you publish

- [ ] `./gradlew build` with JDK 25, then a final check of the jar in a clean instance: Fabric API + Cobblemon + Showtime
      only. Showtime must launch without Player Animation Library, Mod Menu and Phantasmon.
- [ ] Publish a GitHub release `v1.0.0` with the jar, and the music pack template as a zip.
- [ ] Modrinth: create the project, then upload the version. It is reviewed before going public, usually within a few
      days.
- [ ] CurseForge: create the project, then upload the file. It is reviewed before going public.
