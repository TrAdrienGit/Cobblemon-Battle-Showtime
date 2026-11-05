# Plan : sortir la mise en scène des combats de Phantasmon

> Rédigé le 2026-10-29 à la demande d'Adrien. Décision côté Phantasmon : D-37
> (`Phantasmon-Client/Documentation/architecture/decisions.md`).

## 1. Objectif

Cobblemon Battle Showtime devient un mod client Fabric autonome, qui ne dépend que de Cobblemon. Il met en scène :

- les combats **Phantasmon**, exactement comme aujourd'hui (aucune régression, c'est la règle absolue) ;
- les combats **Cobblemon classiques** joueur contre joueur et contre dresseurs PNJ (pas les sauvages).

Showtime n'a besoin d'aucun backend.

## 2. Choix d'Adrien (2026-10-29)

| Sujet | Choix |
|---|---|
| Nom | **Cobblemon Battle Showtime** : dossier `Cobblemon-Battle-Showtime`, mod id `cobblemon_battle_showtime` |
| Distribution | **Deux jars séparés** : Phantasmon déclare `cobblemon_battle_showtime` en dépendance obligatoire avec une plage de versions et refuse de démarrer sans lui |
| Combats classiques mis en scène | Joueur contre joueur, dresseurs PNJ. Pas les Pokémon sauvages |
| Intros dessinées (X/Y, Épée/Bouclier…) | Phantasmon (comme aujourd'hui) et PvP classique, tirée au hasard localement |

## 3. Ce qui part, ce qui reste

| Part dans Showtime | Reste dans Phantasmon |
|---|---|
| `BattleCameraDirector` (caméra pendant le combat) | Moteur de combat sur l'hôte (`GhostBattles`, `BattleThread`, acteurs, fabrique) |
| `BattleCinematic` (plans de lancement, flash) + `PhantasmonBattleIntroScreen` (les 5 intros) | Lobby, relais backend, chrono, résultat (`LiveBattleController`) |
| `BattleShowtime` + `ShowtimeOverlay` (Méga, Primo, Z, Téra, halo) | `BattleVisuals`, `BattleFieldScenes` : les entités **locales** de Phantasmon |
| Mixins `CameraMixin`, `GameRendererShowtimeMixin`, `BattleGuiShowtimeMixin`, `EntityGlowMixin` | `ActionEffectPlayer` et ses mixins : rejouer les timelines Cobblemon sans serveur est propre à Phantasmon (en classique, le serveur les joue déjà) |
| Bouton « caméra libre / mise en scène » de l'écran de combat | Boutons musique et chrono (ajoutés par Phantasmon dans la colonne de Showtime) |
| Musiques de combat (`BattleMusic`, réglages, `MusicManagerMixin`, bouton « musique suivante », modèle de pack « Cobblemon Battle Showtime Music ») — ajout d'Adrien | Pilotage du lobby, de la victoire, de la défaite ; écran des musiques (touche N, DA Phantasmon) |
| Aperçus `/phantasmon admin debug intro\|spectacle` → `/showtime preview …` | `FormeChangeVisual` côté relais (le format du paquet `phantasmon:forme_change` reste à Phantasmon) |

## 4. Le contrat entre les deux mods

Showtime ne connaît pas Phantasmon. Sa surface publique, la seule que Phantasmon a le droit d'utiliser : le
paquet `com.mystaria.showtime.api` et les méthodes publiques de `BattleCinematic`, `BattleCameraDirector` et
`BattleShowtime` (paquet `battle`).

- **`BattleStage`** : ce que Showtime a besoin de savoir d'un combat pour le filmer : l'entité à une position
  Showdown (`p1a`, `p2a`…), le Pokémon actif de chaque camp, la position des dresseurs. Phantasmon passe la sienne (`BattleVisuals`) à `BattleCameraDirector.start(stage)` ; en S2, Showtime aura sa
  scène classique (entités envoyées par le serveur).
- **`Transformation`** : Méga, Primo, Z, Téra annoncés par le combat (position, Pokémon, espèce, aspect).
- **`ShowtimeHooks`** : le remplaçant d'un joueur absent du monde (Phantasmon : avatars de hub), les halos Téra à
  garder (combats Phantasmon vus de loin), les boutons ajoutés à la colonne de l'écran de combat
  (**`BattleButton`** : Phantasmon y met musique et chrono autour du bouton caméra).
- **Événements** que la source du combat signale, par les méthodes publiques : début (`start`), choix
  (`onChoosing`), attaque (`onAction`), entrée / K.O. (`onFocus`), transformation (`play`), fin (`stop`, `clear`) ;
  plus `pauseSeconds(aspect)` pour que l'hôte Phantasmon mette son moteur en pause le temps du spectacle, et
  `startIntro(..., onIntroEnd)` pour qu'il enchaîne sa musique. En S2, l'adaptateur classique de Showtime les
  tirera des paquets Cobblemon et ignorera un combat déjà filmé par Phantasmon (pas de double déclenchement).

Garde-fous de compatibilité :

- `depends` dans `fabric.mod.json` de Phantasmon avec une plage (`>=0.1.0 <0.2.0`) ; l'API ne change de façon
  incompatible qu'avec la version mineure ;
- Phantasmon se compile contre le jar publié dans le Maven local (`publishToMavenLocal`) : un changement d'API qui
  casse Phantasmon casse sa compilation ;
- le script de déploiement du client installe les **deux** jars ;
- chaque phase se termine par une validation en jeu d'un combat Phantasmon complet (intro, sorties, caméra, Méga,
  Z, Téra, fin).

## 5. Phases

| Phase | État |
|---|---|
| S0 | **Faite** (2026-10-29) : compile, publiée dans le Maven local |
| S1 | **Validée en jeu** par Adrien (2026-10-29), musiques comprises |
| S2 | **Codée et déployée** (2026-10-29) : `classic/ClassicBattles`, `ClassicStage`, 6 mixins sur les gestionnaires de combat client de Cobblemon ; **validée en jeu** (2026-10-29, musique comprise) |
| S3 | **Codée et déployée** (2026-10-29) : `config/ShowtimeConfig` (par type de combat : caméra, intro, musique ; volume), écran de réglages (Mod Menu), menu des musiques propre à Showtime (touche N, remplacé par celui de Phantasmon quand il est là), petit lecteur en combat (titre → liste déroulante, volume, suivant), README joueur, musiques de victoire et défaite en combat classique ; **validée en jeu** (2026-10-29) |

### S0 — Squelette (pas de changement pour les joueurs)

- Projet Fabric Loom identique à celui de Phantasmon (1.21.1, mappings Mojang, Cobblemon 1.8.1, Java 21 / JDK 25
  pour Gradle), `publishToMavenLocal`, CI GitHub Actions, documentation (`README`, architecture, journal).
- Fini quand : le jar vide se charge dans le modpack.

### S1 — Déménagement (Phantasmon identique)

- Les classes de la colonne de gauche du §3 passent dans Showtime, renommées dans `com.mystaria.showtime` ;
  clés de langue `cobblemon_battle_showtime.*`, sons et textures dans l'espace de noms `cobblemon_battle_showtime`.
- Les appels directs aux classes de Phantasmon (`BattleVisuals.entityAt`, `BattleFieldScenes.owns`,
  `HubAvatars.playerOrAvatar`, `PhantasmonBattleIntroScreen`) sont remplacés par l'API du §4.
- Phantasmon enregistre son `StageProvider`, appelle les événements, ajoute ses boutons, et dépend du jar.
- Les mixins quittent `phantasmon.client.mixins.json` pour celui de Showtime (Phantasmon en aura 16 avec la musique).
- Fini quand : un combat Phantasmon est **identique** à avant, chez l'hôte, chez l'invité et chez un spectateur du
  hub (D-33). Validation en jeu par Adrien avant S2.

### S2 — Combats classiques (PvP et dresseurs PNJ)

- **Vérification d'abord** (le point le plus risqué) :
  - retrouver, côté client, l'entité serveur de chaque Pokémon actif du combat (`ClientBattle` donne l'UUID du
    Pokémon ; reste à voir ce que l'entité synchronisée expose : identifiant de combat, propriétaire…) ;
  - suivre les attaques : pas de paquet « attaque utilisée », mais un message traduisible
    `cobblemon.battle.used_move` / `used_move_on` contient le nom de l'attaque ; les animations du serveur arrivent
    peut-être aussi par entité ;
  - `cobblemon.battle.mega`, `zpower`, `terastallize` arrivent en messages de combat, ce qui suffit à déclencher le
    spectacle.
- **Limite connue** : le serveur ne s'arrête pas pour nous. Le spectacle de transformation et l'intro jouent pendant
  que le combat continue derrière : les paquets reçus pendant ce temps sont retenus puis rejoués juste après
  (comme le fait déjà l'invité Phantasmon pendant l'intro). La forme Méga elle-même dépend du mod serveur qui la
  gère.
- Intro au hasard en PvP uniquement ; caméra et spectacles en PvP et contre les dresseurs PNJ ; rien en sauvage.
- Fini quand : un PvP et un combat de dresseur PNJ sur un serveur Cobblemon sans Phantasmon sont filmés.

### S3 — Réglages et publication

- un écran des musiques propre à Showtime (aujourd'hui seul celui de Phantasmon existe) ;
- `config/cobblemon_battle_showtime.json` : caméra mise en scène par défaut ou non, intros oui / non, par type de
  combat ; écran via Mod Menu.
- Documentation joueur, page Modrinth si Adrien le souhaite.

## 6. Hors de ce plan

- Nouveaux effets (particules d'attaque, arène simulée) : une fois le découpage fini, dans Showtime.
- Moteur d'événements et hubs blancs : `Phantasmon-Events`. Jeu de trahison : `Phantasmon-Trahison`.
