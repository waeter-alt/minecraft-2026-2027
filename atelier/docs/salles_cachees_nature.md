<!-- [claude-build] genere par sources/arene_nature.py -->
# Salles cachées — arène Nature

Monde `saves/Arene Nature`, arène centrée en X=0 Z=0, sol à Y=86. Coordonnées = case vide où poser le coffre.

| N° | Coffre (X Y Z) | Accès |
|---|---|---|
| 1 | 87 86 14 | Derrière la cascade de la colline sud-est : on traverse le rideau d'eau depuis le bassin |
| 2 | 18 87 47 | À l'intérieur du tronc géant de la jungle : fente entre deux racines, face nord |
| 3 | 25 78 -51 | Sous les racines de l'épicéa géant : puits à échelle sous l'arche de racines, côté est |
| 4 | -17 87 60 | Temple en ruine : le mur du fond a une ouverture cachée derrière le rideau de lianes |
| 5 | 50 79 101 | Lisière sud-est : trappe sous le tapis de mousse (casser le tapis, puis ouvrir la trappe), échelle |
| 6 | -54 87 -19 | Colline creuse à l'ouest : tunnel d'un bloc de large côté sud, caché par les racines |
| 7 | 74 99 -9 | Au bout du parcours de champignons géants : cabane perchée, ouverte vers le dernier chapeau |
| 8 | -24 75 -93 | Dans la grotte luxuriante : couloir au nord, derrière la paroi de lichen |

## Apparitions (10, sur le chemin extérieur)

- 1 : 108 87 17
- 2 : 77 87 77
- 3 : 17 88 108
- 4 : -50 87 97
- 5 : -98 87 49
- 6 : -109 87 -18
- 7 : -78 87 -78
- 8 : -18 87 -109
- 9 : 49 87 -98
- 10 : 97 87 -50

## Ce qui a été construit

- Enceinte Ø252 (centre entre 4 blocs), rempart de 4,5 blocs d'épaisseur et de 15 à 20 blocs de haut, brèches comblées par des racines, lianes, arbres sur le mur.
- 4 portes monumentales : arche de pierre moussue, 2 troncs géants par porte, racines entrelacées.
- Centre : arbre-monde creux de 46 blocs, escalier intérieur, balcon, terrasse au sommet, île entourée de douves, ruines, 4 ponts différents.
- Rivière en deux bras reliés par les douves, née d'une cascade dans le rempart ; 2 cascades, bassin, 6 étangs, nénuphars.
- 4 tours : arbres creux en palétuvier (NE) et en chêne noir (NO), tours de pierre ronde en ruine (SE) et carrée (SO).
- 4 labyrinthes de haies à cœur solide (troncs ou pierre), 5 blocs de haut, couverts de feuilles.
- Géants de la jungle, de l'épicéa et du cerisier, 135 arbres, 60 colonnes debout, brisées ou tombées, champignons géants, collines, ravine, temple en ruine.
- Grotte luxuriante : baies lumineuses, spores, azalées, feuilles tombantes, lichen, entrée par un escalier.

## Écarts par rapport au prompt

- Construction par structure (.nbt + /place) au lieu de /fill en jeu : les 7 phases sont dans le script, chacune vérifiée.
- Portes : le fond de chaque passage est fermé par des racines, pour que personne ne sorte. Une paroi invisible (blocs barrière) au-dessus du rempart empêche aussi de sortir en grimpant aux lianes.
- Salle 5 : le tapis de mousse couvre la trappe, il faut donc le casser pour l'ouvrir ; en mode aventure, cette salle est fermée.
- Les chutes d'eau utilisent de l'eau qui coule, confinée par des encoches ; la rivière elle-même est en eau source immobile.
- Éclairage : lanternes, shroomlight et baies, complétés par 555 blocs de lumière invisibles calculés pour qu'aucune surface ne reste noire.
- `/gamerule doFireTick false` et `/forceload` sont à taper en jeu (commandes fournies).
