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
