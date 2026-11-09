<!-- [claude-build] genere par sources/piege.py -->
# Arène piège — Le Laboratoire du Scientifique

Monde `saves/Arene Piege`, centre X0 Z0, sol Y86, porte au nord.

## Pièges non mortels

Plaque de pression sur une dalle fissurée, bloc de commande caché dessous.

| Effet | Plaque (X Y Z) |
|---|---|
| Lenteur | 24 87 5 |
| Faim | 13 87 20 |
| Obscurité | -6 87 24 |
| Nausée | -21 87 13 |
| Fatigue | -25 87 -6 |
| Lévitation | -14 87 -21 |
| Pluie de flèches | 5 87 -25 |
| Retour à la porte | 20 87 -14 |
| Cécité | 29 87 29 |
| Repérage | -30 87 29 |
| Faiblesse | -30 87 -30 |
| Poison léger | 29 87 -30 |

## Piège mortel

- 5 dalles piégées dans la grande salle, autour du socle du trésor : 7 87 0 ; 2 87 7 ; -7 87 4 ; -6 87 -6 ; 4 87 -7.
- Le plancher de la grande salle disparaît, et la chute finit dans la lave (fosse de 14 blocs).
- Remise en état : bouton à l'entrée, en 6 87 -41.

Apparitions : 37 87 5 ; 26 87 26 ; 6 87 39 ; -19 87 35 ; -36 87 18 ; -38 87 -6 ; -27 87 -27 ; -7 87 -40 ; 18 87 -36 ; 35 87 -19

- Remise en état automatique : le plancher et la plaque reviennent seuls environ 10 secondes après le déclenchement. Le bouton reste utilisable.

## Usine

- Roue dentée géante sous la voûte de la grande salle, sur un roulement Create.
- Couloirs nord et sud : tapis roulant vers l'extérieur (96 tr/min), 3 marteaux-pilons chacun, refuges sur les côtés, frises d'engrenages.
- Marteaux : cloche, puis descente environ 1 seconde plus tard. Le coup tue. Cycle de 6 secondes.
- 4 presses mécaniques dans la cour, qui tournent à vide (plus de lingots, à la demande de l'utilisateur).

## À savoir

- Il faut NeoForge et Create pour ouvrir ce monde. Les moteurs créatifs font tourner les machines sans carburant.
- Sur un serveur : `enable-command-block=true`.
- Le fonctionnement de la redstone se vérifie en jeu.

## Le lieu de vie du scientifique (2026-11-07)

### Plateforme tournante

- Avant : la roue dentée géante tournait sous la voûte, roulement en 0 96 0, roue à Y95.
- Après : roue à Y76, roulement en 0 75 0, moteur créatif en 0 74 0 (1 tr/min), tous deux cachés dans le socle du trésor.
- Chute depuis le plancher de la grande salle : 10 blocs, soit 7 points de dégâts (3,5 cœurs) sur 20. La lave reste 4 blocs plus bas.
- Mécanisme : moteur créatif sous un roulement mécanique Create ; la roue est tenue par une colle Create (une entité). Aucune commande, aucune coordonnée absolue : la roue s'assemble seule quand le moteur tourne.

### Voie de retour depuis la roue

- corniche dans la fosse, tunnel vers l'est, échelle dans une conduite de cuivre, sortie en hauteur dans le laboratoire (saut de 3 blocs).
- Tracé : 14 77 0 → 21 77 0 → 22 77 0 → 22 90 0 → 21 90 0 → 20 87 0.
- Seul accès : sauter du bord de la roue sur la corniche. La sortie est à 3 blocs du sol du laboratoire : impossible d'y remonter depuis la salle.

### Zones

| Zone | Point de repère (X Y Z) | Objets posés | Contenu |
|---|---|---|---|
| Laboratoire principal | 25 87 -1 | 22 | paillasses, alambics, chaudrons (eau, lave, poudreuse), cuves éclairées, tuyaux de cuivre et chaînes |
| Salle des expériences ratées | 17 87 17 | 22 | cage de barreaux, cuves brisées, sculk qui gagne le sol, blocs noircis, toiles, toit effondré |
| Chaîne de production et atelier | 4 87 24 | 26 | tapis, marteaux-pilons, pistons, lampes, ampoules de cuivre, grilles |
| Réserve | -18 87 17 | 21 | caisses empilées, bottes de foin, fer, escalier vers les toits |
| Bureau et bibliothèque | -25 87 -1 | 20 | étagères, bibliothèques sculptées, pupitres, table de cartographie, balcon, passage secret |
| Quartiers de vie | -18 87 -18 | 21 | faux lit défait, fumoir, four, feu de camp, table, chaudron de toilette |
| Salle des machines | -6 87 -25 | 26 | générateur en cuivre, pistons, lampes, ampoules de cuivre, tapis et marteaux |
| Serre | 17 87 -18 | 23 | verrière, mousse, azalées, cultures, plantes en pots, feuillage qui déborde |
| Observatoire | 24 100 9 | lunette, paratonnerre, mannequin | point haut sur les toits |
| Grande salle (cœur du réacteur) | 10 87 3 | trésor, passerelle au-dessus | piège mortel inchangé |

### Passages ajoutés

- Conduit ouest : cour - passage secret de la bibliothèque : -33 87 6 → -36 87 6 (1 bloc de haut : se placer sous la trappe ouverte, la fermer, puis ramper).
- Conduit est : salle des expériences ratées - cour : 28 87 17 → 31 87 17 (1 bloc de haut : se placer sous la trappe ouverte, la fermer, puis ramper).
- Passerelle métallique : -15 94 0 → 14 94 0 (au-dessus de la grande salle, relie les toits du bureau et du laboratoire).
- Escalier est (cour - toits) : 42 87 6 → 36 94 6 (escalier vers les toits).
- Escalier ouest (cour - toits) : -43 87 -7 → -37 94 -7 (escalier vers les toits).
- Escalier de la réserve (réserve - toits) : -18 87 20 → -18 94 26 (escalier vers les toits).
- Passage secret de la bibliothèque : -32 87 10 → -33 87 6 (derrière une rangée d'étagères, ouvert au sud, mène au conduit ouest).
- Balcon de la bibliothèque (mezzanine) : -28 87 6 → -32 91 8 (échelle contre les étagères).
- Trappes et tunnel sous le plancher : -21 86 -15 → -21 86 6 (trappe dans le sol des quartiers de vie, échelle, tunnel, trappe dans le bureau).
- Observatoire : 23 94 7 → 24 100 9 (sur les toits, échelle contre un pilier).
- Arches entre toutes les salles voisines ; le bureau et les quartiers de vie ne forment plus qu'un espace ; chaque salle a au moins deux accès.

### Pièges après les travaux

| Piège | Plaque (X Y Z) | Habillage | État |
|---|---|---|---|
| Lenteur | 24 87 5 | 2 élément(s) | mécanisme inchangé (commande relue dans le fichier) ; à essayer en jeu |
| Faim | 13 87 20 | 3 élément(s) | mécanisme inchangé (commande relue dans le fichier) ; à essayer en jeu |
| Obscurité | -6 87 24 | 1 élément(s) | mécanisme inchangé (commande relue dans le fichier) ; à essayer en jeu |
| Nausée | -21 87 13 | 2 élément(s) | mécanisme inchangé (commande relue dans le fichier) ; à essayer en jeu |
| Fatigue | -25 87 -6 | 3 élément(s) | mécanisme inchangé (commande relue dans le fichier) ; à essayer en jeu |
| Lévitation | -14 87 -21 | 2 élément(s) | mécanisme inchangé (commande relue dans le fichier) ; à essayer en jeu |
| Pluie de flèches | 5 87 -25 | 1 élément(s) | mécanisme inchangé (commande relue dans le fichier) ; à essayer en jeu |
| Retour à la porte | 20 87 -14 | 2 élément(s) | mécanisme inchangé (commande relue dans le fichier) ; à essayer en jeu |
| Cécité | 29 87 29 | 3 élément(s) | mécanisme inchangé (commande relue dans le fichier) ; à essayer en jeu |
| Repérage | -30 87 29 | 3 élément(s) | mécanisme inchangé (commande relue dans le fichier) ; à essayer en jeu |
| Faiblesse | -30 87 -30 | 3 élément(s) | mécanisme inchangé (commande relue dans le fichier) ; à essayer en jeu |
| Poison léger | 29 87 -30 | 3 élément(s) | mécanisme inchangé (commande relue dans le fichier) ; à essayer en jeu |
| 5 dalles mortelles et plancher qui se remet seul | voir plus haut | aucun | les commandes ne pendent plus sous le plancher : la chaîne enterrée de l'usine surveille les 5 dalles ; on tombe sur la roue ou dans la lave |
| 6 marteaux-pilons, tapis | couloirs nord et sud | aucun | tuent maintenant (avant : 3 cœurs et une poussée), à la demande de l'utilisateur |

### Entités décoratives (fixes, invulnérables)

- Cadre (experience_bottle) : Laboratoire principal, en 28 88 -11
- Cadre (glass_bottle) : Laboratoire principal, en 32 88 -8
- Cadre (ender_eye) : Salle des expériences ratées, en 28 88 13
- Cadre (map) : Réserve, en -14 88 28
- Cadre (writable_book) : Bureau et bibliothèque, en -31 88 11
- Cadre (clock) : Quartiers de vie, en -14 88 -29
- Tableau (kebab) : Quartiers de vie, en -10 88 -19
- Cadre (wheat_seeds) : Serre, en 13 88 -29
- Mannequin en blouse blanche : Laboratoire principal, en 21 87 -5
- Mannequin en blouse blanche (spyglass en main) : Observatoire, en 25 100 8
- Colle Create de la roue (invisible) : 0 76 0

### Écarts par rapport au prompt

- Il n'y avait pas de plateforme tournante : c'est la roue dentée géante de l'usine qui a été descendue et sert de plateforme.
- Les valeurs à compléter du prompt ont été prises dans le projet : Minecraft 1.21.1, centre X0 Z0, sol Y86, chute de 10 blocs.
- Travaux faits par le générateur et un correctif à poser, pas par /fill ni /forceload. `doFireTick false` est ajouté à la chaîne de commandes de l'usine.
- Pas de vrai lit : un lit fixerait le point de réapparition des joueurs dans l'arène et casserait le retour au hub. Le lit défait est en laine et tapis.
- Aucun coffre ni conteneur ; les postes de travail sont vides.
- Les pièges et la rotation sont vérifiés dans les fichiers, puis dans la sauvegarde après la pose. Le déclenchement de chaque piège en jeu reste à faire par un joueur.
- Les deux conduits de ventilation obligent à ramper (fermer la trappe au-dessus de sa tête) : le contrôle d'accès à pied ne les compte pas.
- Demandes ajoutées après la première pose : plus de lingots sur les presses, plus de blocs de commande sous le plancher de la grande salle, marteaux mortels.
- L'arène dans le monde Arene Semaine et son zip ne sont pas encore mis à jour.
