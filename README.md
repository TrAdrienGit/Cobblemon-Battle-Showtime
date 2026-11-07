# Cobblemon Battle Showtime

Mod client Fabric (Minecraft 1.21.1, Cobblemon 1.8.1) qui met en scène les combats Pokémon : caméra de cinéma, intros
dessinées, attaques redessinées, Poké Balls, effets de terrain, ambiances, dresseurs animés, Méga-Évolution, Retour
primal, capacités Z et Téracristallisation en grand spectacle, musiques de combat tirées d'un pack de ressources.

Il marche seul, sur n'importe quel serveur Cobblemon, sans rien installer côté serveur, et avec
[Phantasmon](https://github.com/TrAdrienGit/Phantasmon-Client), qui s'appuie sur lui pour ses propres combats.
Sa seule dépendance est Cobblemon ; Emotecraft est facultatif (sans lui, les dresseurs ne s'animent pas).

## Ce qui est mis en scène

| Combat | Intro | Caméra | Musique | Attaques, Balls, effets | Méga / Z / Téra |
|---|---|---|---|---|---|
| Joueur contre joueur | Oui, tirée au hasard | Oui | Oui | Oui | Oui |
| Dresseur PNJ | Non | Oui | Oui | Oui | Oui |
| Pokémon sauvage | Non | Non | Non | Non | Non |
| Combat Phantasmon | Oui | Oui | Oui | Oui | Oui |

Le serveur n'attend pas la mise en scène : en PvP, le combat démarre pendant l'intro (l'écran de combat revient à la
fin), et les spectacles se jouent pendant que le combat continue.

- **Attaques** : 883 attaques redessinées en particules, vrais blocs et objets de Minecraft (aucun asset tiré des
  jeux) : 180 composées à la main, les autres d'après leurs propres données (type, catégorie, coup de poing, morsure,
  son, onde, projectile, soin…), et les 34 capacités Z en trois temps, bien plus grandes. Un Pokémon dont le modèle n'a
  pas d'animation d'attaque se rue, recule ou bondit à la place, et pousse parfois son cri.
- **Poké Balls** : le dresseur lance et rappelle au rythme de Cobblemon ; ouverture, rappel et K.O. ont leurs effets,
  teintés selon la Ball.
- **Effets persistants** : terrains, météo, Distorsion et autres salles, pièges, écrans de chaque camp, effets sur un
  Pokémon (Clonage, Vampigraine, confusion…).
- **Ambiances** : dix ambiances de combat (disco, cosmos, orage, spectral…), choisies dans le menu ; un pack de
  ressources peut en ajouter.
- **Dresseurs** : garde, lancer, rappel, ordre d'attaque, victoire et défaite animés, profil d'animation au choix.

## Menu

Une seule touche, **N** (réglable dans Options → Commandes), et aucune commande : profil d'animation du dresseur,
ambiance, musiques et réglages, avec un aperçu sur le vrai monde. Aussi par Mod Menu.

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

## Réglages

Dans le menu (N), ou `config/cobblemon_battle_showtime.json`. Pour chaque type de combat (Phantasmon, joueur contre
joueur, dresseur PNJ) : caméra cinéma au départ ou libre, intro (pas pour Phantasmon, où elle synchronise les deux
joueurs), musique, effets de Ball. Plus le volume des musiques (qui s'ajoute au curseur « Musique » de Minecraft). Les
morceaux décochés sont dans `config/cobblemon_battle_showtime-music.json`.

## Développement

- JDK 25 pour Gradle (Loom), bytecode Java 21. `./gradlew build publishToMavenLocal` : Phantasmon se compile contre
  le jar publié dans le Maven local.
- Attaques : `python tools/moves.py` réécrit `assets/cobblemon_battle_showtime/moves/` depuis `tools/move_data.json`
  (extrait du moteur de combat de Cobblemon). Un pack de ressources peut ajouter ou remplacer une attaque.
- Plan et état des phases : [`Documentation/plan-showtime.md`](Documentation/plan-showtime.md).
- Règle : toute évolution garde les combats Phantasmon intacts. Phantasmon n'utilise que l'API publique (paquet
  `api` et méthodes publiques de `BattleCinematic`, `BattleCameraDirector`, `BattleShowtime`).

## License

Apache License 2.0 — see [`LICENSE`](./LICENSE). Free to use, modify and redistribute; forks and redistributions must keep [`NOTICE`](./NOTICE) and credit the original author.
