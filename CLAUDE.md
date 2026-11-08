# Cobblemon Battle Showtime: agent context

Load this file before working on the mod. It is the short version; the details live in `Documentation/`.

## What it is

A **client-only** Fabric mod (Minecraft 1.21.1, Cobblemon 1.8.1, Java 21) that stages Cobblemon battles: cinematic
camera, drawn intros, 883 redrawn moves, Z-Move / Mega / Tera set pieces, Poké Ball effects, lasting field and
Pokémon effects, battle ambiances, trainer animations and resource-pack battle music. It never needs anything on the
server, and every client stages its own view from the packets Cobblemon already sends.

Two kinds of battle feed it:

- **Classic Cobblemon battles** (player vs player, NPC trainers; wild battles are deliberately left alone), read from
  Cobblemon's client packet handlers (`classic/`, `mixin/Classic*`).
- **Battles staged by another mod through the public API** (`api/`): Phantasmon's locally simulated "Ghost" battles.
  Their packets go through the same Cobblemon handlers, flagged by `ShowtimeHooks.handlingForeignPacket()`.

## Where to read next

| Need | File |
|---|---|
| How the code is organised, the battle flow, the mixins | [`Documentation/architecture.md`](Documentation/architecture.md) |
| What is done (feature list) | [`Documentation/features.md`](Documentation/features.md) |
| Known bugs and how to fix them | [`Documentation/known-bugs.md`](Documentation/known-bugs.md) |
| What to build next | [`Documentation/roadmap.md`](Documentation/roadmap.md) |
| The public API other mods use | [`Documentation/api.md`](Documentation/api.md) |
| JSON formats: moves, ambiances, animations, music, config | [`Documentation/data-formats.md`](Documentation/data-formats.md) |
| Other mods we must live with (DeltaClient, Iris, Battle Extras…) | [`Documentation/compatibility.md`](Documentation/compatibility.md) |
| Building, running, testing in game | [`Documentation/testing.md`](Documentation/testing.md) |
| Publishing on Modrinth / CurseForge | [`Documentation/publication/`](Documentation/publication/) |

## Build

- Gradle needs **JDK 25** to run (Loom); the bytecode targets Java 21. Do not "fix" the toolchain to 21.
- `./gradlew build` → `build/libs/cobblemon-battle-showtime-<version>.jar`.
- `./gradlew build publishToMavenLocal` when a dependent mod (Phantasmon) must compile against the new jar.
- Version: `version` in `gradle.properties`.

## Hard rules

1. **Client only.** No server code, no packets of our own, no commands. Everything is read from what Cobblemon sends.
2. **One key, one menu.** Every setting goes in the menu opened by **N** (`gui/ShowtimeMenuScreen`), also reachable
   from Mod Menu. Never add a chat command.
3. **The public API is a contract.** Dependent mods may only use the `api` package and the public static methods of
   `BattleCinematic`, `BattleCameraDirector` and `BattleShowtime`. Changing them breaks those mods: keep them
   backward compatible, or bump the major version.
4. **Foreign battles stay intact.** Every change must keep battles staged through the API working exactly as before.
5. **Never block the battle.** The server does not wait for the show. Effects play alongside; nothing may hold a packet
   or a choice.
6. **No game assets.** Effects are built from Minecraft's own particles, blocks and items. Nothing ripped from Pokémon
   games.
7. **Optional dependencies stay optional.** Player Animation Library, Mod Menu, Iris, Battle Extras: detect with
   `FabricLoader.isModLoaded`, load their classes by name, degrade silently.
8. **Hook Cobblemon's handlers at HEAD, priority 500.** DeltaClient (Cobblemon Academy modpack) cancels several of
   Cobblemon's battle handlers; injections at RETURN, or at HEAD with a higher priority, never run when it does.
9. **One vertex buffer at a time.** Never keep a `VertexConsumer` across a call that asks the buffer source for another
   one (rendering a block or an item does). It crashes with "Not building!" without Iris.
10. **English everywhere**: code, comments, docs, commit messages. Player-facing text goes through the lang files
    (`en_us.json`, `fr_fr.json`), both kept complete.
11. No personal names in code or docs. Explain a decision by its reason; a date is enough to place it.

## Code conventions

- Package `com.mystaria.showtime`; tabs; one public class per file; client thread unless a comment says otherwise.
- Comments explain *why* (the constraint, the bug avoided); the code says what.
- Static singletons per feature (`MoveEffects`, `FieldEffects`, `BattleAmbiance`…) with `tick()`, `render(...)` and
  `reset()`. Every one is reset when a battle starts and when it ends, on disconnect and on a world change.
- Config writes are atomic (`ShowtimeConfig.write`). A missing or broken file means defaults.
- Generated data is rewritten by the scripts in `tools/`, never edited by hand (see data-formats.md).

## Working on it

- Before saying something works, test it in game: most of the mod is visual and depends on real server packets
  (testing.md has the checklist).
- When a bug is found and not fixed, add it to `Documentation/known-bugs.md` with how to fix it. When a feature lands,
  move it from `roadmap.md` to `features.md`.
- When an in-game result contradicts the code, look for another mod hooking the same Cobblemon class (scan the
  modpack's jars) before changing the logic.
