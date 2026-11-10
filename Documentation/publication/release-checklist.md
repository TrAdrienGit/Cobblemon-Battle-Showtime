# Publishing on Modrinth and CurseForge: release checklist

Page text for both sites: [`description.md`](description.md). Both accept Markdown; on CurseForge, pick the Markdown
editor when pasting. Replace the `<!-- GIF / Screenshot -->` comments with your images, once they are uploaded to the
gallery.

## Fields to fill in

| Field | Value |
|---|---|
| Name | Cobblemon Battle Showtime |
| Slug / URL | `cobblemon-battle-showtime` |
| Summary (Modrinth, 256 characters max) | Turn every Cobblemon battle into a show: a cinematic camera, 16 battle intros, 883 redrawn moves, grand Z-Moves, Mega and Tera set pieces, animated trainers, ambiances and your own music. Client-side only. |
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
| Icon | **Not `art/Battle_Showtime_Icon.png`**: its background is derived from a generated image (rule 6.2.1) and it shows the Cobblemon logo. The page icon must be made without generative AI, e.g. an in-game capture with the title over it. |

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
- 883 redrawn moves, 34 grand Z-Moves in three acts
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
