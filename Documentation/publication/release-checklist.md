# Publishing on Modrinth and CurseForge: release checklist

Page text for both sites: [`description.md`](description.md). Both accept Markdown; on CurseForge, pick the Markdown
editor when pasting. Replace the `<!-- GIF / Screenshot -->` comments with your images, once they are uploaded to the
gallery.

## Fields to fill in

| Field | Value |
|---|---|
| Name | Cobblemon Battle Showtime |
| Slug / URL | `cobblemon-battle-showtime` |
| Summary (Modrinth, 256 characters max) | Turn every Cobblemon battle into a show: a cinematic camera, 16 battle intros, 884 redrawn moves, grand Z-Moves, Mega and Tera set pieces, animated trainers, ambiances and your own music. Client-side only. |
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
| Icon | **Not `art/icons/Battle_Showtime_Icon.png`**: its background is derived from a generated image (rule 6.2.1) and it shows the Cobblemon logo. The page icon must be made without generative AI, e.g. an in-game capture with the title over it. |

## Modrinth rules: what to tick and check

Checked against Modrinth's Content Rules (version of 2026-08-13).

| Rule | What it means for Showtime |
|---|---|
| 1.11 Data sent to a remote server must be clearly disclosed | The update check (api.modrinth.com, version + IP) is disclosed in the description's "Network and privacy" section, and can be switched off. Keep that section if the network behaviour changes. |
| 1.3 / 1.9 / 4 Trademarks, endorsement, copyright | No game assets or music in the jar; the description ends with the "not affiliated" notice. Game names are only used to describe which game an intro is after. **The icon shows the Cobblemon logo**: either get the Cobblemon team's permission or remove the logo from the icon, or it may read as an endorsement. |
| 2.1 / 2.2 Clear description, plain text, English | The description says what it adds, why, how to use it, its dependencies and its network use; headers only for headings. |
| 3 Cheats | None: the camera and effects are cosmetic, give no advantage, and only show what the server already sends. |
| 5.1 / 5.9 Metadata and content disclosures | Environment: client required, server unsupported. License Apache-2.0. Tick the content disclosures that apply (see 6). |
| 5.3 Summary | No formatting, does not repeat the title. |
| 5.6 Dependencies on each version | Declare them on the version page (table below). |
| **6.1 AI disclosure** | **Tick "Contains AI-generated content"**: a substantial part of the code, and the description, were produced with generative AI. The description says so ("How it was made"). |
| **6.2.1 No AI images on the page** | **No gallery image, icon or description image may be created or derived from generative AI.** If the icon art (the stadium city) was generated, replace it before publishing: a hand-made icon, or an in-game screenshot. Gallery: in-game recordings only. |
| **6.2.2 Not primarily AI output** | **A project "primarily or entirely" made of AI output may not be published publicly.** Showtime's code was largely written with an AI assistant under your direction: be ready for a reviewer to question it, and be honest if asked. Your design, testing, art direction and choices are your contribution; describe them truthfully. |

## Dependencies to declare

| Mod | Type |
|---|---|
| Fabric API | Required |
| Cobblemon (1.8.1+) | Required |
| Player Animation Library (`playeranimator`) | Optional |
| Mod Menu | Optional |
| Cobblemon Battle Extras | Optional (compatible) |
| Iris | Optional (compatible) |

No resource pack is required (checked 2026-11-10: the code only reads its own assets, Cobblemon's and Minecraft's).

## The file

- Version: publish as **1.1.0** (the camera v2, the intros page and the music favourites came after 1.0.0): set
  `version=1.1.0` in `gradle.properties` and build. The Modrinth version number must be the same, the update check
  compares them.
- Jar: `build/libs/cobblemon-battle-showtime-1.1.0.jar` (not the `-sources` jar).
- Version name: `Cobblemon Battle Showtime 1.1.0`. Channel: Release.
- Game version 1.21.1, loader Fabric.

### Changelog 1.1.0

```
First public release.

- Cinematic battle camera in the stadium games' style: low drifting shots, cuts on the hit,
  keeps clear of blocks and people; the previous camera stays available in the settings
- 16 battle intros, from the Game Boy to today, with an Intros page: generations, favourites, preview
- 884 redrawn moves, 34 grand Z-Moves in three acts
- Mega Evolution, Primal Reversion, Z-Power and Terastallization set pieces
- Poké Ball throws, recalls and knock-outs, tinted after the ball
- Weather, terrains, rooms, hazards, screens and Pokémon effects drawn on the field
- Eighteen battle ambiances, from a disco to a league final, more through resource packs;
  a "calm effects" setting (no strobe, no flashes)
- Animated trainers (with Player Animation Library)
- Your own battle music through a resource pack: in-battle controls, generation filters,
  favourites, enable / disable all
- Ambiances settle on the floor of the battle, even on uneven ground
- One menu (N), no commands; a chat notice when a new version is out
```

### Changelog 1.6.0

Same jar name pattern, `build/libs/cobblemon-battle-showtime-1.6.0.jar`; release `v1.6.0` with the jar and the
template zip. Build it with `./gradlew publishToMavenLocal`: the local version bump only runs on `build`.

```
Stadiums, scene by biome, and LeagueWay compatibility.

- Stadiums are now separate from ambiances: pick a place (void, ultra island, arena, cave,
  volcanic den...) and lay any ambiance over it. The Scene page has three tabs: Ambiances,
  Stadiums and Presets (a stadium and an ambiance under one name)
- Sixteen new stadiums: sea and seabed, meadow, forest, badlands, swamp, desert, canyon,
  scrapyard, summit, temple, ashen shore, factory, chessboard, big top and a hall of mirrors
  that reflects the battle (mirrors off under shader packs)
- 19 new field ambiances, each with its own moments in battle
- Grounds laid block by block on the world's grid, finely shaded; one setting makes every
  stadium smooth. New marble pavement for the temple
- The field follows the trainers more calmly: no rebuild at every step
- New "Scene by biome" setting: the place you stand in picks the stadium and the ambiance
  (plains, ocean, caves, Nether, End, snowy peaks, storms...), modded biomes included
- Hiding the battle screen with Cobblemon's R key frees the camera; showing it again
  brings the cinematic camera back
- The menu key (N) now also closes the menu
- LeagueWay compatibility: one battle intro plays, not two. Showtime's by default; switch
  to LeagueWay's in the Intros page (the setting shows only when LeagueWay is installed)
- For mod developers: the API can add intros drawn by another mod, reserve intros and
  ambiances for some players, impose a stadium, lay an ambiance on an area outside battles,
  and impose or turn off the scene by biome (see Documentation/api.md)

Requires Fabric API and Cobblemon 1.8.1+. Optional: Player Animation Library, Mod Menu.
```

### Changelog 1.7.0

Same jar name pattern, `build/libs/cobblemon-battle-showtime-1.7.0.jar`; release `v1.7.0` with the jar and the
template zip. Build it with `./gradlew publishToMavenLocal`: the local version bump only runs on `build`.

```
Stadiums built of Minecraft's own blocks, shader-friendly.

- Every stadium is now built of real Minecraft blocks, plants and trees: lands, sea, seabed,
  caves and halls. They are lit and shaded by shader packs (Iris) like the world, and built
  in the background so the frame rate holds
- Halls of blocks with pilasters, cornices, wainscots, chandeliers, pedestals and carpets;
  the hall of mirrors now reflects the battle under shader packs too, and is furnished
- Caves of blocks: dripstone stalactites and stalagmites, a gold hoard, fossils in the lava
- Four new hall stadiums: Power Station, Psychic Sanctum, Glitch Room and Negative Hall,
  with their presets (charged station, mind sanctum, glitched room, negative hall)
- Ambiance glows stay bright and emissive under shader packs
- Glowing veins and neon grids follow the blocks: laid on each block's top, down each step
- Mist lies level in the hollows and fades before it reaches the blocks: no more hard lines
- Floor glows, dance floors, caustics and court lines are cut along the blocks' edges, flat
  on each block; every ambiance layer follows the stadium's ground past the field
- An ambiance or stadium imposed by another mod is settled behind the intro: no swap or
  flash when the intro ends
- The smooth grounds and their setting are gone: stadiums are always built of blocks

Requires Fabric API and Cobblemon 1.8.1+. Optional: Player Animation Library, Mod Menu, Iris.
```

## Gallery to prepare

Short GIFs or MP4s (Modrinth takes images and GIFs; CurseForge takes images, and videos through a YouTube link):

1. A move followed by the cinematic camera (Flamethrower or Thunderbolt).
2. A Z-Move, the whole set piece.
3. A Mega Evolution.
4. Two or three intros (one retro revisit, one Battle Revolution, the Scarlet/Violet showdown).
5. Two or three ambiances (disco, cosmos, spectral).
6. Field effects: weather plus Light Screen or Spikes.
7. The N menu with the trainer preview.
8. The battle screen with the music panel.

Record in 1920×1080 if you can, without other mods' overlays, and with the HUD hidden (F1) for the field shots.

## Before you publish

- [ ] `./gradlew build` with JDK 25, then a final check of the jar in a **clean instance**: Fabric API + Cobblemon +
      Showtime only, **no resource pack** (not the test instances' CCC, Academy or music packs). It must launch, stage a
      battle (camera, a move, an intro in player vs player) and open the N menu without Player Animation Library,
      Mod Menu, Battle Extras or Phantasmon. Then add Player Animation Library alone: the trainers animate.
- [ ] Publish a GitHub release `v1.1.0` with the jar, and the empty music pack template as a zip.
- [ ] Modrinth: create the project, then upload the version. It is reviewed before going public, usually within a few
      days.
- [ ] CurseForge: create the project, then upload the file. It is reviewed before going public.
