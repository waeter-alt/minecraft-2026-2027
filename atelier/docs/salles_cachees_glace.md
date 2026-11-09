<!-- [claude-build] genere par sources/arene_glace.py -->
# Salles cachées — arène Glace

Monde `saves/Arene Glace`, arène centrée en X=0 Z=0, sol à Y=86. Coordonnées = case vide où poser le coffre.

| N° | Coffre (X Y Z) | Accès |
|---|---|---|
| 1 | 87 87 15 | Derrière la cascade gelée du glacier sud-est : couloir d'un bloc entre la coulée et la falaise, entrée côté est |
| 2 | 14 87 50 | À l'intérieur du grand pic de glace : fente d'un bloc, face nord |
| 3 | -3 78 -16 | Sous le lac gelé : puits à échelle derrière le trône, au nord de l'îlot, puis galerie |
| 4 | 50 79 101 | Lisière sud-est : trappe sous la neige (casser la couche de neige, puis ouvrir la trappe), échelle |
| 5 | -42 77 -24 | Au fond de la crevasse nord-ouest : descendre par une extrémité, puis ouverture dans la paroi est |
| 6 | -55 87 7 | Congère creuse à l'ouest : tunnel d'un bloc de large, entrée côté sud |
| 7 | 74 100 -9 | Au bout du parcours de pics de glace : refuge de sapin perché, ouvert vers le dernier pic |
| 8 | -24 75 -93 | Dans la grotte de glace : galerie au nord, derrière un rideau de stalactites |

## Pièges de powder snow

2 blocs de fond, 1 bloc sur la marche de sortie (côté est).

- Piège 1 : 108 91 39
- Piège 2 : -40 87 108
- Piège 3 : -109 86 -40
- Piège 4 : 39 86 -109

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

- Enceinte Ø252 (centre entre 4 blocs), rempart de glacier de 4,5 blocs d'épaisseur et de 15 à 19 blocs de haut, bandes de glace, neige et pierre claire, créneaux, stalactites, contreforts, congères au pied.
- 4 portes monumentales : arche de glace, 2 pics géants, herse en barreaux, soul lanterns.
- Centre : lac gelé à fissures de neige, îlot, trône de glace, aiguilles, colonnes prises dans la glace, épave de sapin.
- 4 tours : pics creux à escalier (NE, NO), donjon d'ardoise (SE), tour givrée en ruine (SO).
- 4 labyrinthes à cœur solide, 5 blocs de haut ; 75 pics, 62 colonnes debout, brisées ou tombées, congères, rochers, sapins morts.
- 2 crevasses avec ponts de glace, plateformes de glace bleue et poutre étroite ; glacier à cascade gelée ; ruines enfouies ; grotte de glace à stalactites et piliers.
- Parcours de 12 pics jusqu'à un refuge perché ; 8 salles cachées ; 4 pièges.

## Écarts par rapport au prompt

- Construction par structure (.nbt + /place) au lieu de /fill en jeu : les 7 phases sont dans le script, chacune vérifiée.
- Aucune eau liquide : le lac et les cascades sont en glace compacte et bleue, ce qui évite qu'elle gèle ou coule.
- Portes fermées par la herse et un bouchon de glace : personne ne sort. Une paroi invisible (blocs barrière) au-dessus du rempart ferme aussi le haut.
- Salle 4 : la neige couvre la trappe, il faut donc la casser pour ouvrir ; en mode aventure, cette salle est fermée. 
- Les couches de neige ne tiennent pas sur la glace compacte ou bleue : elles sont seulement sur neige et pierre.
- Éclairage : lanternes de mer, froglights et soul lanterns, complétés par 1209 blocs de lumière invisibles de niveau 11. La neige fond au-dessus de 11 : ces blocs ne la font pas fondre, et la règle des 3 blocs ne s'applique donc qu'aux vraies sources, où les couches ont été retirées.
