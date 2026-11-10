# Roadmap

Features not built yet, by priority. Nothing here is promised; each item says what it needs. When one lands, move it to
features.md.

## Next

1. **Hand-made polish of the most used moves.** The 669 composed moves read well but alike. Take the top of
   `Documentation/move-usage.csv` (Smogon usage) and give each a hand-tuned spec in `tools/moves.py`, or a hand-made
   effect class like `ShadowBall` for the signature ones. Check each in a real battle.
2. **Move preview in the menu.** A "Moves" section in the N menu that plays any move on the preview, replacing the
   removed `/showtime preview move` (its code was deleted on 2026-11-10: `MoveEffects.create` and a fixed target
   point ahead of the player are enough to rebuild it).
3. **Release on Modrinth and CurseForge.** Everything is ready in `Documentation/publication/`; gallery to record.

## Later

- **Ambiance for spectators of foreign battles**: an API call so the driving mod can pass the battle's ambiance on.
- **Per-ambiance music**: an ambiance may name a music folder, used when the player's pack has one.
- **More intros**, each after another game's style, and a setting to pick a favourite or random.
- **Capture effects** for wild battles, as an option, if wild battles ever get staged (they are left alone on
  purpose today: they are frequent and short).
- **Spectator camera**: cinematic shots for a player watching a classic battle.
- **Configurable keybind actions** in battle (toggle camera, next track) without the mouse.

## Ideas to evaluate

- NeoForge build (Cobblemon ships for both loaders); needs the mixins checked against NeoForge's Cobblemon.
- Double and triple battles: the camera director handles them, but shots were tuned on singles.
- Accessibility: a "reduced effects" setting (no flashes, no strobe, no screen filters).
