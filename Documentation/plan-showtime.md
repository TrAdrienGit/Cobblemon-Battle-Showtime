# Plan — Showtime, deuxième vague (emotes, ambiances, attaques)

> Demandé par Adrien le 2026-10-30. Showtime reste autonome : rien ne passe par le backend, tout est joué localement
> par chaque client à partir des événements du combat qu'il reçoit déjà (combats Cobblemon classiques comme combats
> Ghost pilotés par Phantasmon).

## Choix d'Adrien (2026-10-30)

- **Ambiance** : chaque joueur choisit la sienne (réglage local de Showtime). Surcouches :
  - Phantasmon : dans le lobby, une ambiance commune choisie pour les deux joueurs (et ses spectateurs) ;
  - Phantasmon Events, plus tard : ambiances imposées pour des matchs scénarisés (lore).
  Showtime expose donc une API « ambiance imposée pour ce combat » qui l'emporte sur le réglage local.
- **Emotes** : Emotecraft (déjà dans le modpack, avec sa bibliothèque d'animation de joueur) en dépendance
  **facultative** : présent, les dresseurs s'animent ; absent, Showtime marche sans emotes.
- **Attaques** : **tout refaire** pour le top Smogon, même les attaques que Cobblemon anime déjà, dans un style
  unique et fidèle aux jeux (recréation en particules, caméra et mouvements ; aucun asset extrait des jeux).

## Ordre

| Phase | Contenu | Statut |
|---|---|---|
| E1 | Emotes : moteur (playerAnimator, facultatif), garde d'attente et moments joués automatiquement (lancer, rappel — un échange = rappel puis lancer —, attaque, victoire, défaite), une animation par moment, `/showtime preview emote <moment>` ; dresseurs tournés vers l'adversaire | validé 2026-10-30 |
| E2 | Profil d'animation : variantes `<moment>_<style>` (3 gardes, 3 lancers, 2 rappels, 3 attaques, 3 victoires, 2 défaites ; un pack de ressources peut en ajouter), écran « Profil d'animation du dresseur » (réglages de Showtime) avec aperçu en boucle sur son propre modèle, choix enregistré dans `config/cobblemon_battle_showtime.json`, `ShowtimeHooks.setTrainerProfiles` pour les autres dresseurs | validé 2026-10-30 |
| E3 | Phantasmon : profil envoyé au backend à la connexion et à chaque changement (`POST /trainer-profile`), ceux des autres dresseurs récupérés au début du combat et à la demande, gardés une minute (`GET /trainer-profiles`, table `trainer_profiles`, V20) ; menu de Showtime sur sa seule touche (N) | validé 2026-10-30 |
| A1 | Ambiances : moteur par couches, décrites en JSON (`assets/<ns>/ambiances/*.json`, un pack peut en ajouter) — heure et vitesse du temps, ciel, brouillard, nuages, étoiles, fond qui cache le monde (cosmos) avec étoiles tournantes, arc-en-ciel, projecteurs, particules et vent, blocs flottants, foudre, bruit d'ambiance, filtre d'écran ; réponses aux moments du combat (lancer, attaque, coup critique, K.O., victoire : particules, flash, foudre, son, filtre bref) ; choix dans le menu (N), `ShowtimeHooks.setAmbianceOverride`, `/showtime preview ambiance <id>` | validé 2026-10-30 |
| A2 | Ambiances de départ (`tools/ambiances.py`) : disco, prairie, nuit étoilée, orage, spectral, féerique, cosmos, vieux film, arcade, corrompu ; filtres d'écran de Showtime (`tools/filters.py` : noir et blanc, pixels, glitch, ondulation, vieux téléviseur — Minecraft 1.21.1 n'a plus les siens) ; plus tard : effacement du monde (cosmos), failles, taches au sol, stroboscope, aurore en rayons, étoiles filantes et météores, système solaire, vraie pluie | validé 2026-10-30 |
| A3 | Phantasmon : liste « Ambiance » dans le lobby (l'un ou l'autre joueur choisit, `BattleLobbySetAmbiance`), imposée aux deux au lancement (`BattleSessionStarted.ambiance`, `ShowtimeHooks.setAmbianceOverride`) ; pas encore aux spectateurs | fait 2026-10-30 |
| P1 | Poké Balls et transitions dans le combat : lancer, ouverture, rappel, changement de Pokémon, K.O., capture — détail à valider avec Adrien | à faire |
| M1 | Attaques : top d'usage Smogon (National Dex et OU, Gen 9), inventaire de l'existant Cobblemon (136 animations). **Essai de remplacement (2026-11-01) : Ball'Ombre** — les particules d'une attaque refaite (`cobblemon:<attaque>_*`, envoyées par le serveur ou rejouées par Phantasmon) sont jetées à l'arrivée (`MoveParticleOverrideMixin`) et Showtime dessine la sienne, rythmée par ces mêmes paquets (`move/MoveEffects`, `move/ShadowBall`) ; pose et sons de Cobblemon gardés ; `/showtime preview move shadowball` | essai validé 2026-11-01 (preview et combat : particules remplacées, la caméra attend la fin) |
| M2 | Premier lot : 180 attaques (dont Ball'Ombre) — toutes celles à 1 % d'usage ou plus en Gen 9 OU et National Dex (statistiques Smogon d'octobre 2026, `Documentation/attaques.csv` par `tools/move_table.py`) et d'autres courantes (`tools/moves.py` → `assets/cobblemon_battle_showtime/moves/<attaque>.json`), composées de motifs (`move/Motifs`, 71 : coups — croix, griffes, épée, rafale de poings, coup de pied, crocs, marteau, queue, fouet, ailes… —, projectiles, rayons, jets, vagues, pièces, météores, rochers, tornade, étoile de Déflagration, Lune… ; vrais blocs et objets de Minecraft) en phases (`move/LayeredMove` : élan, trajet, coup, sur soi) ; physique = au corps à corps, spéciale = à distance (Adrien 2026-11-01) ; démarrage sur la pose de l'attaque (`MoveAnimationMixin`), coup sur l'impact de Cobblemon (`impact_<type>`, jeté) ; un pack peut ajouter ou remplacer une attaque | à tester |
| F1 | Effets persistants (Adrien 2026-11-02), lus dans les messages de combat de Cobblemon, quelle qu'en soit la source (attaque ou talent) : terrains (herbu : vraies herbes et fleurs ; électrique ; psychique : runes ; brumeux), Distorsion, Zone Magique, Zone Étrange, Gravité (boîte ou traits sur le terrain), météo (soleil, pluie, tempête de sable, neige, grêle), et par camp : Piège de Roc, Picots et Pics Toxiques (par couches), Toile Gluante, Protection, Mur Lumière, Voile Aurore, Vent Arrière, Rune Protect, Brume (`move/FieldEffects`, salles en cube carrelé) ; `/showtime preview field <effet>` (`clear` pour effacer) | validé 2026-11-02 en aperçu (à revoir en vrais matchs) |
| F2 | Effets persistants sur un Pokémon (`move/PokemonEffects`, messages `start|end|activate.<id>`) : Clonage, Vampigraine, confusion, Provoc, Tourmente, Encore, Bâillement, Requiem (une perle par tour restant), Stockage (1 à 3), Malédiction, Racines, Anneau Hydro, Puissance, Vol Magnétik, Chargeur, Salaison, Paléosynthèse, Charge Quantique, Torche, Attraction, Cauchemar, Entrave, Anti-Soin, Embargo, Affilage, Anti-Air, Goudronnage, Octoprise, Ultime Bastion, Lévikinésie, Brouhaha, Prescience… ; finissent avec leur message ou quand le Pokémon quitte le terrain ; `/showtime preview status <effet>` | validé 2026-11-02 en aperçu (à revoir en vrais matchs) |
| M3+ | Retouches attaque par attaque après les retours d'Adrien, effets faits main pour les plus marquantes, lots suivants | à faire |

## E1 — comment c'est fait

- `emote/TrainerEmotes` : la file des moments de chaque dresseur, la garde en boucle entre eux ; `PlayerAnimatorEmotes`
  (chargé seulement si playerAnimator est là) joue les animations sur une couche au-dessus de celle d'Emotecraft.
- `classic/ClassicEmotes` : nourri par les gestionnaires de paquets de Cobblemon, combats Showtime **et** combats Ghost
  de Phantasmon (mêmes gestionnaires) ; combats sauvages exclus ; seuls les dresseurs joueurs (et avatars) s'animent.
- Animations : `assets/cobblemon_battle_showtime/player_animations/*.json`, écrites par `tools/trainer_animations.py`
  (poses en radians). Références : vidéos de Pokémon Battle Revolution et Colosseum envoyées par Adrien (garde des
  dresseurs, ordre d'attaque bras tendu, lancers par-dessus l'épaule). `torso` déplace tout le joueur (pitch positif =
  en arrière, y positif = en l'air) ; coudes et genoux par `bend` (signes vérifiés en jeu).
- Lus **en tête** des gestionnaires de début de combat et de messages, priorité **500** : sur le serveur Cobblemon
  Academy, DeltaClient annule ces gestionnaires pour son propre écran de combat, et les injections en tête passent par
  ordre de priorité croissant (à 1500, après DeltaClient, l'attaque et la victoire n'étaient jamais vues).
- Le message « X utilise Y » d'un combat entre joueurs nomme « Pokémon de Dresseur » (`owned_pokemon`) : le dresseur
  est retrouvé par le nom du propriétaire.
- Intro : les deux dresseurs sont dessinés quelle que soit la distance (`EntityRendererCinematicMixin`), la caméra
  reculant au-delà de la distance d'affichage des entités.
- `LivingEntityRendererFacingMixin` : pendant le combat, chaque dresseur est dessiné tourné vers son adversaire.

## Menu (Adrien 2026-11-06)

`gui/ShowtimeMenuScreen`, sur la touche N et par Mod Menu : un menu propre à Showtime, organisé comme une app web (catégories à gauche, sections, liste de choix). Il reprend de Cobblemon la palette, les flèches et le bouton retour, pas la disposition de son PC. Pour les animations et les ambiances, une fenêtre sur le vrai monde (`gui/MenuPreview`) montre le dresseur avec la sélection. Pas d'aperçu pendant un combat. À terme, les commandes `/showtime preview` disparaîtront : ce sont des commandes de debug.

## P1 — décisions (Adrien 2026-11-06)

- Moments : lancer, ouverture, rappel et changement d'abord, puis K.O. **Pas de capture.**
- Style fidèle aux jeux Pokémon, couleur liée au type ou à la Ball ; la vraie Ball du Pokémon à l'écran.
- Caméra : grand plan à l'ouverture du match et aux K.O. ; discrète pour les changements.
- **Règle du mod : un seul menu, une seule touche (N), aucune commande.** Tout réglage va dans ce menu, quitte à y créer un onglet.

**P1.1, calage (validé 2026-11-06)** : le dresseur suit le signal de Cobblemon, le mode de faisceau synchronisé de l'entité (`classic/BallSignals`, 1 = lancé, 3 = rappel). Le paquet de changement ne convient pas : il arrive une fois le rappel fini, juste quand la Ball part. La Ball vole 0,5 s sans nous attendre, donc un lancer démarre après son élan (`TrainerEmotes.THROW_START`). Les combats Ghost utilisent le même signal : leurs entités, sans propriétaire ni combat, nomment le dresseur par la cible du faisceau.

**P1.2, ouverture (validé 2026-11-06)** : `move/BallOpening`, en combat, à l'instant où la Ball de Cobblemon s'ouvre : flash, rayons, colonne de lumière, deux ondes au sol, particules qui montent, teintés selon la Ball. Le flash couvre l'apparition du Pokémon, sans vraie silhouette de lumière.

**P1.3, rappel et changement (validé 2026-11-06)** : `move/BallRecall`, ajouté au faisceau de Cobblemon : halo, anneau qui se referme, particules aspirées vers le dresseur, éclat à l'arrivée. La caméra des changements reste le plan rapproché en fondu qui existait déjà.

**P1.4, K.O. (validé 2026-11-06)** : un rappel alors qu'un Pokémon actif est à 0 PV est un K.O. (`BallSignals`, Cobblemon ne l'annonce qu'après le rappel). `BallRecall` joue alors un éclat blanc, trois ondes lourdes et des cendres, et `BattleCameraDirector.onKnockOut` coupe sur un plan rapproché de 3 s. Pas de vraie chute du modèle : c'est l'animation de Cobblemon.

## Reste à faire

- **Ambiance vue de loin** (Adrien 2026-11-02, fait 2026-11-03, validé 2026-11-06) : un joueur qui voit le terrain d'un combat sans le regarder reçoit son ambiance (`BattleFieldAmbiance`) ; `BattleAmbiance.startDistant` la joue autour du terrain seulement, et ce qui change tout le monde (heure, ciel, brouillard, météo, filtre, son, monde caché de Cosmos) seulement quand il se tient dans le cercle du terrain. Limite : avec Sodium, effacer les blocs **à l'intérieur** du cercle seulement n'est pas possible simplement — Cosmos cache tout le monde dès qu'on entre dans le cercle.

- Plan « Vos regards se croisent » **supprimé** (Adrien 2026-11-01 : les dresseurs y restaient invisibles chez lui) :
  l'intro commence directement à son balayage, depuis la vue du joueur.
- Sous shaders, coudes et genoux restent droits (bendy-lib noircissait la seconde couche du skin) : à revoir si un
  moyen de garder les flexions apparaît.
- Spectateurs d'un combat Ghost : leur transmettre l'ambiance du lobby.

## Points vérifiés (validés le 2026-11-06)

- Emotecraft / bibliothèque d'animation : jouer une animation sur un **autre** joueur (et sur un avatar de Hub)
  côté client, sans passer par le serveur.
- Shaders (Iris) : les ambiances restent correctes avec et sans shaders.

## Point à vérifier

- Effets d'attaque Cobblemon : les « action effects » sont des données serveur ; sur un serveur multijoueur, les
  nôtres ne peuvent que s'y ajouter si le client ne peut pas les couper.
