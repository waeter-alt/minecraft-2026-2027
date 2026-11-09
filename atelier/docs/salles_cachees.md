<!-- [claude-build] genere par sources/forge_v2.py -->
# Salles cachées — arène forge_v2

Monde « Arene » (dossier `saves/Arene Feu`). Arène centrée en X=0 Z=0, sol à Y=86. Coordonnées = bloc où poser le coffre (case vide, sol en roche noire dorée).

| N° | Coffre (X Y Z) | Accès |
|---|---|---|
| 1 | 69 87 -79 | Labyrinthe NE : impasse dont le mur du fond cache un passage en chicane (faux mur) |
| 2 | -103 81 33 | Labyrinthe SO : trappe dans le sol d'une impasse, échelle vers une salle souterraine |
| 3 | -34 87 -34 | Nord-Ouest : derrière la cascade de lave du grand rocher (passer par le côté, le long de la paroi) |
| 4 | 46 93 33 | Sud-Est : au bout du parcours de piliers montants au-dessus de la lave, porte en haut du rocher |
| 5 | -3 80 -3 | Centre : trappe sur l'autel, juste au nord-est de l'obélisque, échelle vers le bas |
| 6 | -39 119 -45 | Tour d'obsidienne (NO) : monter sur le toit, trappe cachée à côté du brasero, échelle vers la salle |
| 7 | 118 87 -9 | Porte Est, tour de garde côté nord : rideau de lianes rouges sur sa face tournée vers l'arène |
| 8 | -28 81 -2 | Ouest : escalier caché dans une ruine en L près de l'avenue Ouest, il descend sous le sol |
| 9 | -14 87 -57 | Aqueduc nord : trappe dans le tablier (passerelle en hauteur), échelle dans la pile vers la salle |

## Ce qui a été construit

- Enceinte circulaire Ø252 (centre entre 4 blocs, cercle symétrique), mur de 4,5 blocs d'épaisseur, 16 blocs + créneaux, contreforts, bandes de textures ; chemin de ronde praticable.
- 4 portes monumentales (N, S, E, O) : arche, 2 tours de garde de 24 blocs par porte (échelle, toit à braseros), fenêtre de lave derrière des barreaux, braseros, chaînes et lanternes sous l'arche.
- Centre : autel surélevé, obélisque d'obsidienne, braseros de feu des âmes, fosse de lave, 4 ponts à rambardes.
- 4 tours de styles différents (basalte ronde, briques du Nether carrée, blackstone en ruine, obsidienne octogonale de 40 blocs) reliées par un aqueduc en hauteur (Y100) sur arches, avec un saut par quadrant.
- 4 labyrinthes (un par quadrant), murs de 5 blocs, impasses, cellules de lave visibles derrière des barreaux.
- Parcours : fosse de lave à piliers de basalte (NE), piliers montants vers un rocher (SE), rocher à cascade de lave (NO), tranchées (SO) ; plateformes surélevées, colonnes debout / brisées / tombées, ruines, champignons géants carmin et biscornus, mares de lave, fissures de magma, feux éternels.
- 9 salles cachées décorées, chacune avec un emplacement de coffre libre (sol doré).

## Écarts par rapport au prompt

- Construction par structure (.nbt + /place) au lieu de /fill en jeu : les 7 phases sont dans le script, chacune vérifiée (bornes, confinement de la lave, supports, aucun conteneur ni entité, rien hors de l'enceinte).
- « Faux mur » : le Minecraft vanilla n'a pas de bloc traversable qui imite un mur (sans redstone). Il est réalisé en chicane : un pan de mur décalé masque un passage latéral d'un bloc.
- `/gamerule doFireTick false` et `/forceload` sont à taper en jeu (commandes fournies).
- Les blocs avec inventaire sont tous exclus, y compris fourneaux et feux de camp (choix de prudence).
