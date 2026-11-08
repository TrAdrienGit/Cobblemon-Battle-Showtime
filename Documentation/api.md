# Public API

Another client mod can stage its own battles with Showtime, or plug into it. Phantasmon does both for its locally
simulated battles. **This is a contract:** keep it backward compatible, or bump the major version and tell the
dependent mods.

The allowed surface is:

- the `com.mystaria.showtime.api` package;
- the public static methods of `battle.BattleCinematic`, `battle.BattleCameraDirector` and `battle.BattleShowtime`.

Everything else is internal and may change at any time. All calls on the client thread.

## Depending on Showtime

- Gradle: `./gradlew publishToMavenLocal` in this repository, then `modImplementation` the published jar.
- `fabric.mod.json`: `"depends": { "cobblemon_battle_showtime": ">=1.0.0 <2" }`.

## `ShowtimeHooks`: set once at startup

| Method | Purpose |
|---|---|
| `setPlayerResolver(BiFunction<ClientLevel, UUID, Player>)` | Who stands for a player this world doesn't have (intro opponent, trainer of a battle). Asked only when the world has no such player. |
| `setGlowKeeper(IntPredicate)` | Entity ids whose glowing outline must be kept. |
| `setForeignPacketCheck(BooleanSupplier)` | True while Cobblemon's handlers run a packet of *your* battle: the classic path then ignores it. |
| `setMusicScreen(Function<Screen, Screen>)` | Replace the music screen opened from the menu. |
| `setTrainerProfiles(BiFunction<UUID, String, String>)` | Animation of another trainer for a moment (from your own sync); null = the default. |
| `setLocalProfileListener(Consumer<Map<String,String>>)` | Told when the local player changes their animation profile (to sync it). |
| `setTrainersListener(Consumer<Collection<UUID>>)` | Told which trainers a battle starts with (to fetch their profiles). |
| `setAmbianceOverride(Supplier<String>)` | An ambiance id forced for the current battle, over the player's choice; null = the player's. |
| `addButton(BattleButton)` | Add a button to the battle-screen panel. |

## `BattleStage`

What the camera needs: `pokemonAt(pnx)` (the entity for `p1a`, `p2a`…), `activePokemon(local)`, `trainerPosition(local)`.
Give it when your battle starts.

## `BattleButton`

A record: `order` (the camera button is `CAMERA_ORDER` = 100), `visible`, `label`, `color` (`CYAN`, `GREEN`, `DIM`,
`RED`), and the click action.

## `Transformation`

A set piece request: `pnx`, Pokémon `uuid`, `species`, `aspect`. Aspects: `Z_POWER` (`"zpower"`), `TERA_PREFIX + type`
(`"tera:fire"`), or a form aspect for Mega / Primal (`changesModel(aspect)` tells which change the model).

## Driving a battle

1. Intro (optional): `BattleCinematic.startIntro(opponentUuid, opponentName, opponentTeamSize, ownTeamSize,
   Intro.of(index), onIntroEnd)`. `introEndsAt()` tells when to let your battle start.
2. Camera: `BattleCameraDirector.start(stage)` (or with a `ShowtimeConfig.Kind`), then feed it:
   `onChoosing()`, `onAction(users, targets)` with pnx lists, `onFocus(pnx)`, `onKnockOut(entity)`, `onThrow(entity)`.
3. Send-outs: `BattleCinematic.beginSendOuts(localSpot, opponentSpot)` or `skipSendOuts()`.
4. Set pieces: `BattleShowtime.play(transformation)`; `playing()` and `pauseSeconds(aspect)` to time your battle;
   `onSentOut(entity, uuid)` re-applies a Tera glow after a switch.
5. End: `BattleCameraDirector.stop()`, `BattleCinematic.stop()`, `BattleShowtime.clear()`.

Moves, Poké Balls, field and Pokémon effects need nothing: dispatch Cobblemon's own packets locally through its
handlers (with your foreign-packet check returning true), and Showtime reads them as it does for classic battles.
