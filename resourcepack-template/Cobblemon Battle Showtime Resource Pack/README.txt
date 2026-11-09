COBBLEMON BATTLE SHOWTIME RESOURCE PACK: battle music for Cobblemon Battle Showtime
=================================================================================
Version: v1

The resource pack of the Cobblemon Battle Showtime mod: it brings the battle music the mod plays.

1. Put .ogg files in the folders of assets/cobblemon_battle_showtime/sounds/music/:

   lobby/    before a battle, in mods that have a battle lobby (Phantasmon), looped, picked at random
   battle/   from the intro to the end of the battle, looped, picked at random
   victory/  on a win, played once
   defeat/   on a loss, played once

   Nothing to declare: every .ogg dropped there is used. An empty folder means silence at that moment.

2. Format: Ogg Vorbis (.ogg). Convert an .mp3 or .wav with Audacity (File > Export > OGG) or an online converter.
   File names in lower case, without spaces or accents (for example rival_battle.ogg).

3. In Minecraft: Options > Resource Packs > enable "Cobblemon Battle Showtime Resource Pack". After adding or removing files,
   press F3 + T to reload (no need to restart the game).

The volume follows Minecraft's Music slider and Showtime's own music volume; the game's normal music stops during a
staged battle. An older "Phantasmon Music" pack (files under assets/phantasmon/sounds/music/) still works.
Mind the rights: only share this pack with music you are allowed to distribute.

Naming (optional, shown nicely in game): <game>_<title>.ogg, for example oras_vs_wally.ogg is shown as "ORAS · Vs Wally".
Game codes: rb, rby, gsc, rse, frlg, dp, pt, dppt, hgss, bw, b2w2, xy, oras, sm, usum, lgpe, swsh, bdsp, pla, plza, sv,
colosseum, xd, stadium, stadium2, pbr, pmdeos, pokken, puzzleleague, pinball, pinballrs, ranger, champions, reborn.
tools/music_names.py in the mod's repository renames a folder of downloaded tracks to this convention.

Versions: the version is in pack.mcmeta's description and on the Version line above. Raise it (v2, v3…) whenever
tracks are added, removed, renamed or moved between folders.
