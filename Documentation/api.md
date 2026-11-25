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
| `setPanelStyle(Supplier<BattlePanelStyle>)` | (1.7.39) The colours of the panel over Cobblemon's battle screen (music, volume, buttons), asked each frame; null = Showtime's own (`BattlePanelStyle.SHOWTIME`). A button accent equal to one of `BattleButton`'s named colours is drawn in the style's matching one. Phantasmon gives the look chosen in its menu. |
| `setTrainerProfiles(BiFunction<UUID, String, String>)` | Animation of another trainer for a moment (from your own sync); null = the default. |
| `setLocalProfileListener(Consumer<Map<String,String>>)` | Told when the local player changes their animation profile (to sync it). |
| `setTrainersListener(Consumer<Collection<UUID>>)` | Told which trainers a battle starts with (to fetch their profiles). |
| `setAmbianceOverride(Supplier<String>)` | An ambiance id forced for the current battle, over the player's choice; null = the player's. Read again twice a second during the battle: a new answer switches the ambiance (behind a flash of its colour). |
| `ambianceMoment(String)` | (1.2.0) Plays the battle ambiance's `named` moment of that name, if it has one (Phantasmon Events: `cave_rumble`, `cave_collapse`). |
| `addAmbianceOverride(Supplier<String>)` | (1.2.0) A layer over that override, asked first while it answers non-null (Phantasmon Events: the Pokémon Reborn field's ambiance); null = the one below comes back. |
| `addStadiumOverride(Supplier<String>)` | (1.3.0) A stadium imposed while it answers non-null (an id, or `none`: the world as it is), read again twice a second like the ambiance. Without one, an imposed ambiance id naming a **preset** (`cosmos`, `champion`, `grotto`...) brings its stadium; one naming a plain ambiance keeps the player's own stadium (a gym's own arena stays visible). |
| Scene ids | (1.3.0) Anywhere an ambiance id is passed (`setAmbianceOverride`, `addAmbianceOverride`, `BattleAmbiance.startDistant`), a **scene** may be passed instead: `<ambiance>|<stadium>` (`Ambiances.scene(ambiance, stadium)`; ambiance empty: the player's own; stadium `none`: the world as it is). Phantasmon's lobby and Events' NPCs pass scenes through their backend this way. |
| `setFieldStadium(Supplier<String>)` | (1.3.0) The stadium the battle field in play suggests (null: none). A scene whose stadium is `field` (`Ambiances.FIELD_STADIUM`) shows it, and the world as it is while the field suggests none. Phantasmon Events: the Reborn cave fields give the Cave, Dragon's Den the Volcanic Den. |
| `addDistantAmbianceOverride(Supplier<String>)` / `setDistantFieldStadium(Supplier<String>)` | (1.8, 2026-11-22) The same two hooks for a battle seen from afar (`BattleAmbiance.startDistant`): asked twice a second, the scene is played again when the answer changes. Phantasmon Events: the Reborn field of the battle seen from afar, so those standing around see it too. A menu preview now stands in for a battle seen from afar only while it lasts; that battle's ambiance comes back after it. |
| `stadiumOverride()`, `baseAmbianceOverride()` | (1.3.0) What is imposed now: the stadium layers' answer, and `setAmbianceOverride`'s answer under the layers. |
| `setAreaAmbiance(Supplier<AmbianceArea>)` | (1.4) The ambiance of the place the local player stands in, asked twice a second outside battles (null: none here). An `AmbianceArea` is an ambiance id and a box of the world (centre, half sizes, yaw, bottom and top). Showtime plays it fixed to the box: the box is paved with 24-block tiles, each floor of each tile (read from the blocks) shows the ambiance's effects as a battle field does, nothing over voids or liquids; its particles fall on the floor near the player; the sky, weather, filter and sound apply while the player is in it; never a stadium. The player's battle, the menu's preview or a battle seen from afar takes over, and the area comes back after. Phantasmon Events: a hub's ambiance. |
| `setBiomeScenes(Boolean)` / `biomeScenesImposed()` / `biomeScenesOn()` | (1.5) Imposes the player's "Scene by biome" box: `true` on, `false` off, `null` gives the choice back (the default). While on, the player's own stadium and ambiance are those of the place they stand in (biome tags `c:`, `cobblemon:`, `minecraft:`, then words of the biome's name, then its temperature and downfall; Nether, End, underground, under water, high up, thunderstorm, night), read again at each battle. It only stands in for the player's own choice: whatever a mod imposes for the battle wins over it, piece by piece (`setAmbianceOverride`: a lobby's or an NPC's scene; `addAmbianceOverride`: a field's; `addStadiumOverride`, `setFieldStadium`), and the biome fills only what nothing imposes. An `AmbianceArea` never reads the biome. |
| `addButton(BattleButton)` | Add a button to the battle-screen panel. |
| `registerIntro(CustomIntro)` | (1.2.0) Add an intro of your own after Showtime's: it is listed in the menu, picked like the others, and played by name. |
| `setUnlocks(BiPredicate<String, String>)` | (1.2.0) Which reserved content the local player may use, asked with `INTRO` or `AMBIANCE` and an id. Reserved content is otherwise hidden from the menus and never picked; content you impose on a battle plays anyway. Default: nothing unlocked. |
| `enableLobbyMusic()` | Turn on the `lobby` music track for your battle lobby: its tab shows in the music menus, and `BattleMusic.play(BattleMusic.Track.LOBBY)` plays it (with fades, volume, favourites). Showtime alone never plays it. |

## `CustomIntro` and `IntroContext` (1.2.0)

An intro from another mod: `id()` (`<namespace>:<name>`, stable: the players' choices use it), `name()`, `length()`
(ms, the closing flash included), `reserved()`, `flashes()` (Showtime's white flash over the last 300 ms), `tick`,
`render` and an optional `camera`. Its clock starts at 0 on its first frame. It draws on Showtime's intro screen over
the world, with the `IntroContext` it is given: the screen size, both names and team sizes, the trainers' on-screen
copies (`drawTrainer`, lit like Showtime's), a Poké Ball (`drawBall`), sounds, and `starIsSelf()`: whether the local
player is the intro's star (see `startIntro` below), so that an intro made for one player shows that player on both
screens.

Reserved ambiances: an ambiance file with `"reserved": true` (see data-formats.md). `Ambiances.usableIds()` lists what
the local player may choose, `Ambiances.reservedIds()` and `BattleCinematic.reservedIntroNames()` what a mod may unlock.

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
   Intro.of(index), onIntroEnd)`. `introEndsAt()` tells when to let your battle start. By name (1.2.0):
   `startIntro(..., String intro, UUID star, onIntroEnd)` plays Showtime's or a registered intro whatever the unlocks
   and returns false for a name this client doesn't know; `star` is whose intro it is (null: the local player if it
   is reserved and unlocked for them, else the opponent). `pickIntroName()` picks among the player's choices, other
   mods' intros included; `introNames()` and `introDisplayName(name)` list and name them.
2. Camera: `BattleCameraDirector.start(stage)` (or with a `ShowtimeConfig.Kind`), then feed it:
   `onChoosing()`, `onAction(users, targets)` with pnx lists, `onFocus(pnx)`, `onKnockOut(entity)`, `onThrow(entity)`.
3. Send-outs: `BattleCinematic.beginSendOuts(localSpot, opponentSpot)` or `skipSendOuts()`.
4. Set pieces: `BattleShowtime.play(transformation)`; `playing()` and `pauseSeconds(aspect)` to time your battle;
   `onSentOut(entity, uuid)` re-applies a Tera glow after a switch.
5. End: `BattleCameraDirector.stop()`, `BattleCinematic.stop()`, `BattleShowtime.clear()`.

Moves, Poké Balls, field and Pokémon effects need nothing: dispatch Cobblemon's own packets locally through its
handlers (with your foreign-packet check returning true), and Showtime reads them as it does for classic battles.
