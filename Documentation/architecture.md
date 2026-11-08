# Architecture

## Packages

All under `com.mystaria.showtime`.

| Package | Role |
|---|---|
| (root) | `CobblemonBattleShowtime`: client entrypoint; registers the key (N), tick, render passes, screen buttons, emotes. `ShowtimeRenderTypes`: render types drawn with depth and without culling. |
| `api` | Public surface for other mods: `ShowtimeHooks`, `BattleStage`, `BattleButton`, `Transformation`. See api.md. |
| `battle` | The show around the battle: `BattleCameraDirector` (camera during the battle), `BattleCinematic` (launch shots, intro timing), `BattleIntroScreen` (the drawn intros), `BattleShowtime` + `ShowtimeOverlay` (Mega, Primal, Z-Power, Tera set pieces), `BattleScreenButtons` (the panel on the battle screen). |
| `classic` | Classic Cobblemon battles: `ClassicBattles` (detects and stages them), `ClassicStage` (a `BattleStage` built from the client battle), `ClassicEmotes` (trainer moments from battle messages), `BallSignals` (throw / recall / knock-out from the Poké Ball beam). |
| `move` | Everything drawn on the field: `MoveEffects` (move dispatcher), `LayeredMove` + `MoveSpec(s)` + `Motifs` (data-driven moves), `ShadowBall` (hand-made), `MoveLunge` (fallback motion), `BallOpening` / `BallRecall`, `FieldEffects` (weather, terrains, rooms, hazards, screens), `PokemonEffects` (effects on one Pokémon), `Draw` (drawing helpers). |
| `ambiance` | `Ambiance` (one ambiance read from JSON), `Ambiances` (the registry, reloaded with resources), `BattleAmbiance` (plays one around a field, also seen from afar). |
| `emote` | `TrainerEmotes` (the moments queue per trainer, facing), `EmotePlayer` + `PlayerAnimatorEmotes` (the optional Player Animation Library backend). |
| `audio` | `BattleMusic` (tracks from resource packs, per moment), `MusicSettings` (unticked tracks). |
| `config` | `ShowtimeConfig` (settings file), `LegacySettings` (migration from older files). |
| `gui` | `ShowtimeMenuScreen` (the N menu), `MenuPreview` (live preview camera), `MusicScreen`, `ShowtimeModMenu`. |
| `mixin` | All mixins, listed below. |

## The battle flow (classic battle)

1. **Start.** `ClassicBattleInitializeHandlerMixin` (HEAD, priority 500) remembers the packet; `ClassicBattles`
   decides whether to stage it (not wild, not a foreign packet) and builds a `ClassicStage`. If another mod cancelled
   the handler before its end, `ClassicBattles.tick` stages it on the next tick.
2. **Intro** (player vs player, if enabled): `BattleCinematic` plays its shots, then `BattleIntroScreen`. The server
   does not wait: the battle runs underneath and Cobblemon's battle screen comes back at the end.
3. **Each message** (`ClassicBattleMessageHandlerMixin`, HEAD, 500) is read by:
   - `ClassicBattles`: who uses which move on whom → `BattleCameraDirector` shots; Mega / Z / Tera → `BattleShowtime`;
   - `ClassicEmotes`: trainer moments (attack order, victory, defeat);
   - `FieldEffects` and `PokemonEffects`: lasting effects start and end from Cobblemon's translatable message keys
     (`cobblemon.battle.<kind>.<id>`).
4. **Moves.** Cobblemon's move animations are server-run "action effects". The client receives the Pokémon's pose
   (`PlayPosableAnimationPacket`) and particles (`SpawnSnowstormEntityParticlePacket`).
   - `MoveAnimationMixin` → `MoveEffects.onAnimation` starts our effect for a redrawn move, and `MoveLunge` moves the
     whole model when it has no animation of that name.
   - `MoveParticleOverrideMixin` → `MoveEffects.intercept` drops the move's own particles (`cobblemon:<move>_<part>`)
     and the generic hit (`impact_<type>`), using them as timing cues: start, travel, hit.
   - Particles named after a move but sent outside that move (end-of-turn hurts, abilities) are left to Cobblemon.
5. **Poké Balls.** `PokemonClientDelegateBallMixin` reads the entity's beam mode: 1 = sent out, 3 = recalled.
   `BallSignals` turns it into throw / recall / knock-out, then `BallOpening`, `BallRecall`, trainer emotes and camera
   shots follow.
6. **End.** `ClassicBattleEndHandlerMixin` → victory or defeat moments, music jingle, then everything is reset.

A foreign battle (through the API) follows the same steps, except that the mod driving it gives the stage
(`BattleStage`), the intro and the end. Its packets are recognised by `ShowtimeHooks.handlingForeignPacket()`, so the
classic path ignores them.

## Per-frame work

- `tick()` on the client tick: camera director, effects, ambiance, emotes, music.
- World render (Fabric `WorldRenderEvents`): move, field and Pokémon effects, ambiance decorations.
- `GameRendererShowtimeMixin` / `CameraMixin`: the cinematic camera replaces the player's view; no hand drawn.
- `BattleScreenButtons`: the panel on Cobblemon's `BattleGUI`, drawn in an early `afterRender` phase so that other
  mods' overlays (Battle Extras' tooltips) cover it.

## Mixins

| Mixin | Target | Why |
|---|---|---|
| `ClassicBattleInitializeHandlerMixin`, `ClassicBattleMessageHandlerMixin`, `ClassicBattleMakeChoiceHandlerMixin` | Cobblemon's battle packet handlers | Read the battle. HEAD, priority 500: DeltaClient cancels these three (see compatibility.md). |
| `ClassicBattleSwitchPokemonHandlerMixin`, `ClassicBattleFaintHandlerMixin`, `ClassicBattleEndHandlerMixin` | same | Switch-in and faint close-ups, battle end. After Cobblemon's handler; nothing known cancels them. |
| `ClassicBattleMusicHandlerMixin` | Cobblemon's battle music handler | Cobblemon's battle music gives way to the resource pack's in a staged battle. |
| `EmoteBattleInitializeHandlerMixin`, `EmoteBattleMessageHandlerMixin` | same | Trainer moments, same constraints. |
| `MoveAnimationMixin`, `MoveParticleOverrideMixin` | Cobblemon's pose and particle handlers | Start our moves, drop Cobblemon's particles for them. |
| `PokemonClientDelegateBallMixin` | Pokémon client delegate | The beam mode: throw and recall timing. |
| `PokemonLungeRenderMixin` | Pokémon renderer | Applies `MoveLunge`. |
| `CameraMixin`, `GameRendererShowtimeMixin`, `GameRendererAccessor` | Camera, game renderer | Cinematic camera, no hand, FOV. |
| `EntityRendererCinematicMixin` | Entity renderer | Trainers drawn at any distance during the intro. |
| `LivingEntityRendererFacingMixin` | Living entity renderer | Trainers face their opponent; the menu preview's angle. |
| `EntityGlowMixin` | Entity | A terastallized Pokémon glows in its Tera type's colour, client side only. |
| `BattleGuiShowtimeMixin` | Cobblemon's `BattleGUI` | Hidden while a set piece plays. |
| `MusicManagerMixin` | Minecraft's music manager | Our track instead of the game's music during a battle. |
| `ClientLevelAmbianceMixin`, `ClientLevelDataAmbianceMixin`, `DimensionSpecialEffectsAmbianceMixin`, `FogRendererAmbianceMixin`, `LevelWeatherAmbianceMixin` | Level, sky, fog, weather | Ambiance time, sky, fog and weather. |
| `LevelRendererHideWorldMixin`, `BlockEntityRenderDispatcherHideWorldMixin` | Level renderer | The "cosmos" ambiance hides the world. |

## Threads and lifetimes

- Everything runs on the client thread. Packet handlers are already on it when Cobblemon calls them.
- Per-battle state is reset at battle start (in case the previous one ended without an end packet: server stopped,
  disconnect), at battle end, on disconnect and on a world change.
- Entities are looked up by id each tick and may vanish (out of range, switched out). Effects drop themselves when
  their entity is gone.

## Tools (`tools/`)

| Script | Writes |
|---|---|
| `moves.py` | `assets/cobblemon_battle_showtime/moves/*.json` from `move_data.json` (hand-tuned specs + composed ones + Z-Moves) |
| `move_table.py` | `Documentation/move-usage.csv` (Smogon usage, used to prioritise polish) |
| `ambiances.py` | `assets/cobblemon_battle_showtime/ambiances/*.json` |
| `filters.py` | The screen filter shaders under `assets/cobblemon_battle_showtime/shaders/` |
| `trainer_animations.py` | `assets/cobblemon_battle_showtime/player_animations/*.json` |
| `icon.py` | The mod icon |
