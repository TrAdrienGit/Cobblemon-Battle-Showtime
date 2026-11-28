# Building and testing

## Build

```bash
./gradlew build
```

JDK 25 to run Gradle, Java 21 bytecode. The jar is `build/libs/cobblemon-battle-showtime-<version>.jar` (not the
`-sources` one). `./gradlew build publishToMavenLocal` when a dependent mod must compile against it.

There are no automated tests: the mod is visual and depends on real server packets. Testing is in game.

## Test setups

- **Solo, classic battles**: a single-player world with Cobblemon. NPC trainer battles and wild battles (which must stay
  untouched).
- **Player vs player**: two clients on one server (a local dedicated server, or LAN). Needed for intros, both sides of
  every effect, and spectators.
- **Mega / Z / Tera**: only on a server that allows them, such as Cobblemon Academy (which also brings DeltaClient).
- **Foreign battles**: a dependent mod's battles (Phantasmon).
- **Minimal instance before a release**: Fabric API + Cobblemon + Showtime only. It must start and stage battles
  without Player Animation Library, Mod Menu or any other optional mod.

Replaying a battle in Cobblemon's own Showdown engine (Node) helps when a damage or message question comes up, without
a game client.

## Picturing battle scenes without playing (scene capture)

`python tools/scene_capture.py` (`--kinds preset,stadium,ambiance`, `--only id1,id2`, `--extra scenes.tsv`) takes a
picture of every battle scene, for the choice cards of a battle lobby (2026-11-28). Same dev game and flat world as the
move capture (`mode=scenes` in `capture.properties`, `SceneCapture` in `src/capture`): two Pokémon face to face, each
scene built whole around the stage's middle as a battle builds it (Showtime's own start, by reflection; the camera
goes back to its spot once it is built), a screenshot without the HUD after `--settle` ticks (80), then the next one.
`--view distant` shows it as a battle seen from afar instead (`BattleAmbiance.startDistant`: only the field dressed). A preset brings its own stadium, a stadium is shown alone, an ambiance
alone over the flat world. `--extra` adds another mod's scenes (`kind<TAB>id<TAB><ambiance>|<stadium>` lines, League
Reborn fields for Phantasmon). Output: `run-capture/scenes/<kind>/<ns>/<name>.png` (960x540), and
`run-capture/capture-done.txt` (one line per scene, "nothing shown" when it resolved to nothing).

## Filming moves without playing (move capture)

`python tools/move_capture.py flamethrower thunderbolt` (or `--file list.txt`, or `--all` for every redrawn move)
films moves with nobody at the keyboard, to look at them or compare a redraw before and after. It runs
`./gradlew runCapture`: a dev game with Fabric API, Cobblemon, Showtime and a small capture mod (`src/capture`, its own source
set, never in the shipped jar). The game opens or makes a flat world in `run-capture/`, puts two Pokémon face to face
(`--user`, `--target`, Cobblemon properties), replays each move's Cobblemon timeline
(`data/cobblemon/action_effects/moves/<move>.json`, or the user's own `<move>_<species>.json` first: the poses, the
user's rush, the particle packets Showtime reads as cues) as a battle's server would send it, pipes the frames to
ffmpeg and quits. A move without a timeline (most of them) gets its pose and the hit cue about when a battle's would
land. Wild Pokémon and an earlier run's stage are cleared first.

Output, in `run-capture/captures/`: `<move>.mp4` (20 fps, 960x540) and `<move>_sheet.jpg` (4 frames a second, 6 a
row). About 4 s a move once the game is up (about 40 s to start). Camera: `--distance`, `--height`, `--gap`.

- The game runs on Java 21 (Cobblemon refuses any other) through a Gradle toolchain launcher; Gradle stays on 25.
- The Kotlin Gradle plugin is applied only so that Loom renames the Kotlin metadata of Cobblemon's classes (its
  reflection reads them; without it the dev game crashes on intermediary names). `kotlin.stdlib.default.dependency`
  is off, so the published POM is unchanged. After adding or upgrading the plugin, delete
  `.gradle/loom-cache/remapped_mods` once: Loom does not remap the cached jars again by itself.
- Kotlin and GraalJS are `localRuntime` dependencies: the dev game does not read Cobblemon's nested jars.

## Checklist after a change

Run the lines your change touches; before a release, run all.

1. Classic NPC battle: the camera films moves, the first throw, switches and knock-outs; the free camera button works.
2. Player vs player: an intro plays, both trainers face each other, the battle screen comes back after it.
3. Moves: a physical, a special and a status move; a Pokémon without attack animation lunges; a Z-Move.
4. Poké Balls: send-out, recall, knock-out effects tinted after the ball; the "Ball effects" switch turns them off.
5. Field: a weather (and a primal one), a terrain, Trick Room, hazards and screens on the right side; Court Change.
6. Pokémon effects: Substitute, Leech Seed hurt, Fire Spin and Magma Storm hurts, Perish Song.
7. Set pieces on a server that allows them: Mega, Z, Tera (glow kept after a switch).
8. Ambiance: pick one in the menu, battle, check its moments; walk near a battle seen from afar.
9. Music: a pack track per moment, list, next, volume; without a pack, Cobblemon's music stays.
10. Menu: opens with N and from Mod Menu, preview works outside a battle, settings survive a restart.
11. With shaders (Iris) and without: no crash, ambiances correct.
12. Disconnect mid-battle and join again: nothing from the old battle remains.
13. Logs: no repeated error from Showtime during a battle.
