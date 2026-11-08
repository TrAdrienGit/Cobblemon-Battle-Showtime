# Known bugs and limits

Each entry: what happens, where, and how to fix it. Remove an entry when its fix is checked in game; add one as soon
as a bug is found and not fixed right away.

## To verify

| Item | Why it is open |
|---|---|
| Mega / Z / Tera set pieces in classic battles | The fix (owned Pokémon names unwrapped, handlers at HEAD) can only be checked on a server that allows these mechanics, such as Cobblemon Academy with DeltaClient. Base Cobblemon has none. |
| First-throw camera shot of a classic NPC battle | Written, never seen in game (in player vs player the intro hides it). |
| Side effects for a spectator | Screens and hazards are placed "ally / opponent" from the local player's view; a spectator of a classic battle may see them swapped. Check what Cobblemon sends spectators; if needed, derive the side from the message's `p1` / `p2`. |

## Bugs

| Bug | Effect | How to fix |
|---|---|---|
| Player vs player intro always shows 6 Poké Balls for the opponent | Visual only | Count the opponent's team when Cobblemon sends it (battle initialize packet), or hide the counter in the classic intro. |
| Choosing "no ambiance" in the menu near a battle seen from afar stops that battle's ambiance | Rare | In `BattleAmbiance.preview`, do nothing when the id is `none` and a non-preview ambiance plays. |
| Cobblemon logs an error at every move for models whose data names an animation they lack (Moltres' "special") | Log noise, not ours | Cobblemon's own lookup; Showtime caches its own check (`MoveLunge.KNOWN`). Report upstream. |

## Performance

| Item | Effect | How to fix |
|---|---|---|
| `TrainerEmotes.facingYaw` scans every entity, per trainer and per frame, when the opponent is not a player | Light cost in a crowded world | Cache the opponent entity by UUID, refreshed every second. |
| `PlayerAnimatorEmotes` checks "shaders on?" by reflection for every bent bone | Light cost | Read Iris' state once per frame and keep it. |

## Polish

| Item | Effect | How to fix |
|---|---|---|
| Close-up shots read the Pokémon's position without interpolation between ticks | Slight judder when the Pokémon flies or moves | Use `entity.getPosition(partialTick)` in `closeUp`, `following` and `actionShot`. |
| Menu preview framed with the FOV setting, not the effective FOV (sprint, effects) | Character slightly off-centre while sprinting | Read the FOV through `GameRenderer#getFov`. |
| Trainers out of sight at a battle's end stay in memory until the next battle | None visible | Clear a trainer's state at `end()` when unseen for a while. |
| Leftover preview code from the removed commands (`MoveEffects.preview`, `FieldEffects.preview`, `PokemonEffects.preview`, `BattleCinematic.preview`, `BattleShowtime.preview`, `TrainerEmotes.preview`) | Dead code | Delete it, or wire it to a move preview in the menu (see roadmap.md). |

## Limits (by design or external)

| Limit | Reason |
|---|---|
| Elbows and knees stay straight under shader packs | Bending (bendy-lib) turned the skin's outer layer black under shaders. Revisit if a way to bend without it appears. |
| The cosmos ambiance hides the whole world as soon as the player is inside the field's circle | With Sodium, hiding only the blocks inside the circle is not simple. |
| Cobblemon's move sounds and poses are kept | They come from the server; only particles are replaced. |
| Spectators of a foreign battle don't get the battle's ambiance | The API only forces it on the players; the driving mod would need to pass it on. |
