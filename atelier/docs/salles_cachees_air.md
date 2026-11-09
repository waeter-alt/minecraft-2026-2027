<!-- [claude-build] genere par sources/arene_air.py -->
# Salles cachées et remontées — arène Air

Monde `saves/Arene Air`, arène centrée en X=0 Z=0. Y_BAS=86, Y_HAUT=104. Coordonnées du coffre = case vide où le poser.

## Salles cachées

| N° | Coffre (X Y Z) | Accès |
|---|---|---|
| 1 | 18 133 -22 | En haut de la tour NE : échelle jusqu'à la chambre sous le toit de cuivre |
| 2 | 47 98 32 | Trappe dans le sol de la promenade (SE), échelle vers la salle suspendue sous le plateau |
| 3 | -1 87 70 | Faux mur sous le grand escalier Sud : un pan de mur décalé masque une fente sur le flanc est, côté zone basse |
| 4 | 60 105 60 | Dans le nuage de l'île SE : on traverse le rideau de neige poudreuse sur sa face nord |
| 5 | -60 93 -57 | Sous l'île NO : l'échelle du petit kiosque descend dans la roche de l'île |
| 6 | 79 114 -24 | Au bout du parcours qui part de l'île NE : temple de l'îlot suspendu, ouvert à l'ouest |
| 7 | -39 87 -39 | Zone basse, sous le quadrant NO : temple en ruine, porte tournée vers le centre |
| 8 | 2 98 5 | Sous le sanctuaire : la stèle sud-est est creuse, ouverte à l'ouest, échelle vers la crypte |

## Moyens de remonter

| Type | Bas (X Y Z) | Haut (X Y Z) |
|---|---|---|
| Grand escalier (entrée Est) | 86 87 0 | 67 105 0 |
| Grand escalier (entrée Sud) | -1 87 86 | -1 105 67 |
| Grand escalier (entrée Ouest) | -87 87 -1 | -68 105 -1 |
| Grand escalier (entrée Nord) | 0 87 -87 | 0 105 -68 |
| Escalier en colimaçon dans une colonne porteuse | 27 87 3 | 29 105 3 |
| Escalier en colimaçon dans une colonne porteuse | -28 87 -4 | -30 105 -3 |
| Ascenseur à bulles fermé | -11 87 26 | -11 105 26 |
| Ascenseur à bulles fermé | 10 87 -27 | 10 105 -27 |
| Lianes le long d'un pilier | -27 87 10 | -25 105 10 |
| Lianes le long d'un pilier | 26 87 -11 | 24 105 -11 |
| Échelle dans la tour NE | 22 87 -22 | 22 105 -22 |
| Échelle dans la tour SE | 22 87 21 | 22 105 21 |
| Échelle dans la tour SO | -21 87 21 | -21 105 21 |
| Échelle dans la tour NO | -21 87 -22 | -21 105 -22 |

## Apparitions (10, sur la promenade)

- 1 : 59 105 9
- 2 : 42 105 42
- 3 : 9 105 59
- 4 : -28 105 53
- 5 : -54 105 27
- 6 : -60 105 -10
- 7 : -43 105 -43
- 8 : -10 105 -60
- 9 : 27 105 -54
- 10 : 53 105 -28

## Ce qui a été construit

- Cité suspendue Ø192, plateau à 18 blocs au-dessus de la zone basse, porté par 36 colonnes et 2 colonnes creuses à colimaçon.
- Îlot central : sanctuaire du vent, 8 colonnes, couronne brisée, sphère de verre suspendue par des chaînes, 4 ponts à câbles.
- 4 tours de styles différents : flèche à toit de cuivre (NE), phare (SE), ruine à morceaux flottants (SO), tour de cuivre couronnée de paratonnerres (NO).
- 4 labyrinthes, un par quadrant (béton, verre, cuivre, quartz), murs de 5 blocs, impasses ouvertes sur le vide : 10.
- 4 îles flottantes reliées par des ponts à chaînes, 4 îlots atteints par parcours ou passage étroit, pierres flottantes au-dessus de l'anneau de vide.
- Enceinte en colonnade ajourée, 4 grands escaliers monumentaux avec arcs de triomphe, 24 colonnes debout, brisées ou flottantes sur le plateau.
- Zone basse : mer de nuages, 16 colonnes tombées, débris, temple en ruine, mur de périmètre de 6 blocs.

## Écarts par rapport au prompt

- Diamètre 192 au lieu de 252, comme autorisé : pour 10 joueurs, il faut qu'ils se croisent.
- Construction par structure (.nbt + /place) au lieu de /fill en jeu : les 9 phases sont dans le script, chacune vérifiée.
- Les 4 entrées partent de l'intérieur du mur bas : un accès depuis l'extérieur irait contre « on ne peut pas sortir » et « rien hors de l'emprise ». Les portes du mur sont fermées par du verre.
- Paroi invisible (blocs barrière) en haut du mur bas, pour qu'on ne sorte pas en sautant d'une île.
- « Faux mur » réalisé en chicane : Minecraft vanilla n'a pas de bloc traversable qui imite un mur.
- « Nuages traversables » : la neige poudreuse est interdite sous les chutes, donc elle ne sert que de rideau à couvert (salle 4). Les nuages en laine sont solides.
- Éclairage : lanternes et lanternes de mer, complétées par 493 blocs de lumière invisibles calculés pour qu'aucune surface ne reste noire.
- Bannières blanches et bleu clair unies, sans motif.
