<!-- [claude-build] genere par sources/feu2.py -->
# Arène Feu — version Ø104

Monde `saves/Arene Feu`, centre X0 Z0, sol Y86. Porte au nord.

- **Cercle de feu :** rayon 19 avant, 22 après.

## Accès au souterrain

- Descente dans la tour de garde : 34 87 10
- Puits à colimaçon : -4 87 31
- Cratère : -16 87 -24
- Faille : 44 86 -12

## Tours

| Tour | Pied (X Y Z) | Hauteur | Origine |
|---|---|---|---|
| tour de garde | 31 87 8 | 24 blocs | ancienne, refaite |
| tour en ruine | 8 87 31 | 18 blocs | ancienne, refaite |
| flèche de basalte | -24 87 23 | 34 blocs | nouvelle |
| tour de nether bricks | -32 87 -9 | 28 blocs | nouvelle |
| poste de guet | -9 87 -32 | 15 blocs | nouvelle |
| tour d'obsidienne | 23 87 -24 | 31 blocs | nouvelle |

## Salles cachées

| N° | Coffre (X Y Z) | Accès |
|---|---|---|
| 1 | -35 115 -9 | En haut de la tour de nether bricks, sous le toit |
| 2 | 42 79 16 | Trappe dans le sol, à l'est, échelle |
| 3 | 3 79 6 | Sous l'autel : la stèle sud-est est creuse, ouverte à l'ouest, échelle |
| 4 | -1 87 45 | Massif contre le rempart, au sud : ouverture derrière le pan de mur décalé |

Apparitions : 23 87 3 ; 16 87 16 ; 3 87 23 ; -11 87 21 ; -21 87 10 ; -25 87 -4 ; -17 87 -17 ; -4 87 -24 ; 10 87 -22 ; 21 87 -11

## Ce qui a été modifié

- Labyrinthes supprimés, terrain accidenté à pentes praticables, soul soil réduit à des plaques isolées.
- 3 rivières de lave de l'anneau au rempart, 5 fosses, avec ponts ; lave en source dans des lits fermés.
- Souterrain en anneau, lacs de lave, 6 chutes confinées, pont à 2 sauts, 4 accès.
- 6 tours à colimaçon, de 15 à 34 blocs.

## Écarts par rapport au prompt

- Emprise de 52 blocs au lieu de 126 : tout est resserré pour tenir en Ø104.
- Arène reconstruite dans un nouveau monde à partir de la version Ø104, et non modifiée sur place. Les 2 tours anciennes sont refaites avec un escalier, comme demandé pour toutes les tours.
- Construction par structure (.nbt + /place) au lieu de /fill en jeu.
- Les 4 salles sont gardées ; 2 ont une échelle d'accès, ce ne sont pas des tours.

## Souterrain (2026-11-08)

### Terre remplacée

- Avant : la structure ne fixait pas les parois du souterrain. Dans le monde `Arene Feu` on y voyait le sol du monde (4 434 blocs de blackstone) ; dans `Arene Semaine`, posé sur de la terre, 4 437 blocs de terre.
- Après : 6556 blocs de paroi font partie de l'arène, quel que soit le monde : blackstone 2294 (35 %), netherrack 1967 (30 %), basalte 1311 (20 %), magma 656 (10 %), briques craquelées 328 (5 %).
- Répartition en plaques (blackstone, netherrack, briques craquelées) et en veines (basalte, magma). Le magma n'est jamais au sol : seulement parois et plafonds. Ni soul sand ni soul soil.
- Autres blocs naturels de la surface visibles dans le souterrain : aucun, dans les deux mondes (ni pierre, ni ardoise, ni gravier, ni minerai). Aucun coffre ni conteneur dans l'emprise.

### Caverne et lac

- Caverne sous le centre de l'arène, rayon de 19 à 23 blocs autour de X0 Z0 ; air de Y65 à Y78 (Y76 sous la salle cachée de l'autel).
- Lac de lave irrégulier : 26 blocs de large en moyenne, de 20 à 31 selon la direction, surface Y64, profondeur 2 ; 539 cases.
- Plafond au moins 12 blocs au-dessus de la lave, stalactites de basalte, 3 chutes de lave confinées qui tombent dans le lac.
- Rives : plages de blackstone au ras de la lave, falaises, corniche étroite tout autour (on contourne le lac sans toucher la lave), 2 rampes de la plage à la corniche.
- Traversée : pont de blackstone effondré par endroits (2 sauts), îlots de basalte pour sauter ; îlot central avec piliers et deux braseros.
- 4 galeries relient l'anneau souterrain à la corniche (angles 30, 125, 240, 330). Avant, aucune galerie n'allait vers le centre.
- Roche entre la caverne (ou ses galeries) et la surface : au moins 5 blocs ; sous le cercle de feu : 5 blocs. Sous la salle cachée de l'autel : 4 blocs jusqu'à son plancher, la salle reste intacte.

### Centre de l'arène

- La grande colonne d'obsidienne (22 blocs de haut) est remplacée, à la demande de l'utilisateur, par un brasero bas en briques de blackstone avec un feu au milieu, entouré de quatre poteaux de basalte poli portant une lanterne des âmes, en 0 88 0.

### Écarts par rapport au prompt

- Les valeurs à compléter ont été prises dans le projet : Minecraft 1.21.1, centre X0 Z0, lac de 25 à 30 blocs.
- Le fichier de l'arène descend de 12 blocs (il commence à Y56 au lieu de Y68) pour loger le lac sous le souterrain existant. Au sol et au-dessus, un seul bloc change : un bloc de lumière invisible déplacé par le calcul d'éclairage.
- Les berges des rivières de lave de surface (derrière la lave) restent celles du monde : elles font partie de la surface, que le prompt interdit de modifier.
- Diamètre du lac : la forme irrégulière donne 20 blocs à l'endroit le plus étroit, sous les 25 demandés.
- Travaux faits par le générateur et un correctif à poser, pas par /fill ni /forceload. La surface n'est pas touchée.
- L'arène dans le monde Arene Semaine et son zip ne sont pas encore mis à jour.
