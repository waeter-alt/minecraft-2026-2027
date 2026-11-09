<!-- [claude-build] genere par sources/croissant.py -->
# Arène glace — le Croissant

Monde `saves/Nouveau monde`, centre X0 Z0, sol Y86. Cercle Ø104, comme le hub.

- **Entrée du labyrinthe :** 31 87 -37 (pointe nord-est)
- **Sortie du labyrinthe :** 31 87 36 (pointe sud-est)
- **Chemin entrée-sortie :** 254 blocs

## Pièges de powder snow (8, hors du labyrinthe)

- 1 : 24 86 10 (au pied de la tour)
- 2 : 11 86 5 (au pied de la tour)
- 3 : 24 86 -11 (au pied de la tour)
- 4 : 2 86 -10 (dans le creux)
- 5 : 35 86 23 (dans le creux)
- 6 : 38 86 -19 (dans le creux)
- 7 : -1 86 7 (dans le creux)
- 8 : 11 86 17 (dans le creux)

## Sol du labyrinthe (2026-11-08)

- 1281 blocs de sol, de l'entrée à la sortie : glace compactée 831 (65 %), glace bleue 322 (25 %), plaques non glissantes 128 (10 % : neige 64, calcite 64).
- Glace bleue : devant les impasses (15), intersections (24), virages serrés (19), couloirs longs (10). Couloirs longs : le dernier passage d'une ligne droite de 4 passages ou plus, juste avant le tournant. Virages serrés : une partie des virages qui s'enchaînent en zigzag.
- Plaques non glissantes isolées, jamais deux côte à côte (ni en diagonale), réparties sans motif.
- Les 8 pièges de poudreuse du labyrinthe sont supprimés : fosses comblées (briques de pierre au fond, glace en surface), sol au même niveau que le couloir. Anciennes positions : -46 86 -10 ; -46 86 15 ; -21 86 15 ; -26 86 -15 ; -11 86 45 ; -31 86 35 ; -6 86 35 ; 4 86 45.
- Congères des culs-de-sac retirées : la neige en couche ne tient pas sur la glace, et le sol devait rester plat.
- Aucun bloc de glace ordinaire : seulement glace compactée et glace bleue.


## Sauts de l'escalier de la tour

| Départ (X Y Z) | Arrivée (X Y Z) | Écart | Montée |
|---|---|---|---|
| 20 90 -3 | 22 91 -2 | 1 | 1 |
| 20 94 2 | 18 95 2 | 1 | 1 |
| 19 99 -3 | 21 100 -3 | 1 | 1 |
| 22 102 0 | 21 103 2 | 1 | 1 |
| 21 111 -2 | 22 112 0 | 1 | 1 |
| 19 115 2 | 17 116 1 | 1 | 1 |
| 20 120 -3 | 22 121 -2 | 1 | 1 |
| 21 123 1 | 19 124 2 | 1 | 1 |

## Ce qui a été construit

- Croissant exact : rayon 52, cercle intérieur de rayon 38 décalé de 20 blocs vers l'est ; 115 cases, couloirs de 2 blocs, murs de 5 blocs plus un chaperon, cœur solide.
- Tour de 40 blocs pour Ø15, 4 volées d'escalier effondré, paliers tous les 10 blocs, 48 meurtrières ouvertes, sommet crénelé.
- 16 pièges de poudreuse à l'origine, 8 depuis le 2026-11-08 (ceux du labyrinthe sont comblés), sans lumière de plus de niveau 11 près de la neige.
- Ajouts demandés après coup : 21 obstacles dans le creux (arches de glace, éclats penchés, rochers enneigés), et 16 pièges au lieu des 10 du prompt.
- Ajouts demandés après coup, repris de l'ancienne arène Glace : rempart de neige et de glace côté est, porte monumentale à l'est (X50 Z0) avec herse levée et pics, pics et congères dans le creux.

## Écarts par rapport au prompt

- Cotes réduites pour tenir en Ø104 : rayons 52 et 38, décalage 20, au lieu de 60, 45 et 25.
- Construction par structure (.nbt + /place) au lieu de /fill en jeu.
- 16 pièges au lieu de 10 au maximum, à la demande de l'utilisateur.
- Sauts sans sprint : 2 blocs d'écart au plus quand on monte d'un bloc.
- Couloirs de 2 blocs de large : au-delà, le labyrinthe perdrait trop de cases à cette taille.
- La poudreuse a une texture proche de la neige, mais pas identique : elle reste repérable de très près.
- Éclairage : soul lanterns et 214 blocs de lumière invisibles de niveau 11, qui ne font pas fondre la neige.
- 2026-11-08 : valeurs à compléter du prompt prises dans le projet (Minecraft 1.21.1, centre X0 Z0). Travaux faits par le générateur et un correctif à poser, pas par /fill ni /forceload. Murs, tour, escalier et pièges extérieurs inchangés.
- 2026-11-08 : la fosse de chaque piège retiré est comblée en briques de pierre (bloc plein, invisible sous la glace) plutôt qu'en neige ou en glace.
- 2026-11-08 : glace bleue un peu plus présente que « quelques endroits » (25 % du sol) : un seul passage par zone, mais les 4 types de zones demandés se croisent souvent dans un labyrinthe de 115 cases.
- L'arène dans le monde Arene Semaine et son zip ne sont pas encore mis à jour.
