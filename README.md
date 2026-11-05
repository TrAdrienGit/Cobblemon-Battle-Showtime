# Cobblemon Battle Showtime

Mod client Fabric (Minecraft 1.21.1, Cobblemon 1.8.1) qui met en scène les combats Pokémon : caméra de cinéma
pendant le combat, intros dessinées, Méga-Évolution, Retour primal, capacités Z et Téracristallisation en grand
spectacle, musiques de combat tirées d'un pack de ressources.

Il marche seul, sur n'importe quel serveur Cobblemon, sans rien installer côté serveur, et avec
[Phantasmon](https://github.com/TrAdrienGit/Phantasmon-Client), qui s'appuie sur lui pour ses propres combats.

## Ce qui est mis en scène

| Combat | Intro | Caméra | Musique | Méga / Z / Téra |
|---|---|---|---|---|
| Joueur contre joueur | Oui, tirée au hasard | Oui | Oui | Oui |
| Dresseur PNJ | Non | Oui | Oui | Oui |
| Pokémon sauvage | Non | Non | Non | Non |
| Combat Phantasmon | Oui | Oui | Oui | Oui |

Le serveur n'attend pas la mise en scène : en PvP, le combat démarre pendant l'intro (l'écran de combat revient à la
fin), et les spectacles se jouent pendant que le combat continue.

## Musiques

1. Copier le modèle `resourcepack-template/Cobblemon Battle Showtime Music` dans le dossier `resourcepacks` de l'instance.
2. Déposer des `.ogg` dans `assets/cobblemon_battle_showtime/sounds/music/<lobby|intro|battle|victory|defeat>/` (voir son
   `LISEZ-MOI.txt`), activer le pack, F3 + T pour recharger.

Un ancien pack « Phantasmon Music » (`assets/phantasmon/…`) marche aussi. Quand le pack fournit une musique, elle
remplace celle que le serveur fait jouer à Cobblemon ; sinon celle de Cobblemon reste.

**En combat**, en haut à droite de l'écran de combat :

- le titre du morceau : un clic ouvre la liste des morceaux du moment (molette pour la faire défiler), un clic sur un
  morceau le joue ;
- `−` / barre / `+` : volume des musiques (un clic sur la barre le règle directement) ;
- `⏭` : morceau suivant ;
- en dessous : caméra cinéma ⇄ libre (et, avec Phantasmon, le chrono).

**Menu des musiques** : touche **N** (réglable dans Options → Commandes). Cocher / décocher les morceaux (un morceau
décoché n'est jamais tiré), les écouter, régler le volume. Avec Phantasmon, c'est son propre menu qui s'ouvre.

## Réglages

Mod Menu → Cobblemon Battle Showtime → Configurer, ou `config/cobblemon_battle_showtime.json`. Pour chaque type de combat
(Phantasmon, joueur contre joueur, dresseur PNJ) : caméra cinéma au départ ou libre, intro (pas pour Phantasmon, où
elle synchronise les deux joueurs), musique. Plus le volume des musiques (qui s'ajoute au curseur « Musique » de
Minecraft). Les morceaux décochés sont dans `config/cobblemon_battle_showtime-music.json`.

## Commandes

| Commande | Effet |
|---|---|
| `/showtime preview intro <xy\|sword_shield\|diamond_pearl\|emerald\|black_white>` | Joue une intro seule, contre le joueur le plus proche |
| `/showtime preview <mega\|primal\|zmove\|tera> [type]` | Joue un spectacle sur le Pokémon le plus proche, sans le modifier |

## Développement

- JDK 25 pour Gradle (Loom), bytecode Java 21. `./gradlew build publishToMavenLocal` : Phantasmon se compile contre
  le jar publié dans le Maven local.
- Plan et état des phases : [`Documentation/plan-decoupage.md`](Documentation/plan-decoupage.md).
- Règle : toute évolution garde les combats Phantasmon intacts. Phantasmon n'utilise que l'API publique (paquet
  `api` et méthodes publiques de `BattleCinematic`, `BattleCameraDirector`, `BattleShowtime`).

## License

Apache License 2.0 — see [`LICENSE`](./LICENSE). Free to use, modify and redistribute; forks and redistributions must keep [`NOTICE`](./NOTICE) and credit the original author.
