# Compatibility

Other mods hook the same Cobblemon classes. When an in-game result contradicts the code, scan the modpack's jars for
mixins into the same class before changing the logic.

| Mod | What it does to us | How Showtime copes |
|---|---|---|
| **DeltaClient** (Cobblemon Academy modpack) | Cancels, under conditions, Cobblemon's battle initialize, message and make-choice handlers to show its own battle screen. | Those three are hooked at **HEAD, priority 500**: injections at HEAD run by increasing priority, so ours run before its cancel (at 1500 nothing was seen). Battle start also remembers the packet at HEAD; if the handler's end is never reached, `ClassicBattles.tick` stages the battle on the next tick. |
| **Iris** (shaders) | Different render pipeline. | Effects draw with our own render types (`ShowtimeRenderTypes`). Bent elbows and knees are turned off under shaders (they blackened the skin's outer layer). |
| **Sodium** | Chunk rendering. | The cosmos ambiance hides the whole world instead of only the field's circle. |
| **Without Iris** | Strict `MultiBufferSource` building. | Never hold a `VertexConsumer` across a call that asks for another buffer (rendering a block or item does): "Not building!" crash. |
| **Player Animation Library / Emotecraft** | Optional; Emotecraft bundles the library. | Detected by mod id `playeranimator`; `PlayerAnimatorEmotes` loaded by name; our layer sits above Emotecraft's. Without it, no trainer animations, nothing else changes. |
| **Cobblemon Battle Extras** | Draws team columns, a stat panel and tooltips over the battle screen. | The battle panel is compact and shifted when it is present, and drawn in an early `afterRender` phase so its tooltips cover the panel. |
| **Cobblemon Battle Extras, own team column** | Shows Poké Balls; a Pokémon's portrait only on hover or when active. | Its own design, not Showtime's. Phantasmon forces portraits in its battles; Showtime leaves it alone. |
| **Mod Menu** | Optional. | `ShowtimeModMenu` opens the N menu from the mod list. |
| **catchindicator, ShoulderSurfing** | Hook Cobblemon's naming and the camera. | No conflict known; first suspects if names or the camera misbehave. |
| **Phantasmon** | Drives its own battles through the API. | See api.md; its packets are flagged foreign, and the classic path skips them. |
| **LeagueWay** (Cobblemon Academy 2.0's client mod, source not public; audited on 0.7.0, 2026-11-19) | When Cobblemon's battle screen opens against a named opponent (PvP, NPC), cancels `Minecraft.setScreen` and shows its own intro (`LeagueBattleStartScreen`), which also starts its own battle music; then reopens the battle screen. Its battle handler mixins only observe. | One intro plays, the player's pick in the Intros page ("LeagueWay's intro", shown only when it is loaded). Showtime's (default): `MinecraftLeagueWayMixin` (HEAD, priority 500, before LeagueWay's) writes the current `CobblemonClient.battle` into its public `LeagueBattleStartScreen.lastBattleObject`, which it reads as "intro already played", so it lets the screen through and its music never starts. LeagueWay's: no Showtime intro; our battle music waits for its intro to close, then starts only if `LeagueBattleStartScreen.hasActiveSound()` is false. All by reflection (`LeagueWayCompat`); if a later version renames those, both intros play again, nothing breaks. |

## Cobblemon specifics worth knowing

- A player's Pokémon is named in messages through `cobblemon.battle.owned_pokemon` (owner, then Pokémon): unwrap it
  before comparing names. The bare name is used for wild Pokémon only.
- Move animations ("action effects") are run by the server; clients only get poses, sounds and particles.
- Lasting effects come as translatable keys: `cobblemon.battle.<start|end|activate|damage|fieldstart|weather|sidestart…>.<id>`.
- End-of-turn hurts have action effects only for Leech Seed, Fire Spin and Infestation (`damages/damage_<id>.json` in
  Cobblemon's data); Magma Storm has none, Showtime plays Fire Spin's.
- Base Cobblemon has no Mega, Z or Tera; servers add them (Cobblemon Academy does).
