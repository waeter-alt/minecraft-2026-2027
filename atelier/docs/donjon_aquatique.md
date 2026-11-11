<!-- [claude-build] fiche du donjon aquatique, ecrite par scripts/fiche_donjon_aqua.py -->
# Donjon aquatique « Le Sanctuaire des Marées »

Monde « Donjon 2 » (dossier `Donjon 2 (1)`, superflat, sol à Y-61 ; le Temple des Mille Racines est dans un autre monde, « Donjon 1 »), Minecraft 1.21.1 + NeoForge 21.1.216 + mod **Grappin 2.5.0** (il apporte le Voile des Profondeurs ; obligatoire chez chaque joueur). Structure `aqua` 150 × 70 × 170, posée en 150 -61 260 (x 150 à 299, z 260 à 429). Générateur : `scripts/donjon_aqua.py`, contrôle : `scripts/verif_donjon_aqua.py`, plan : `donjon_aquatique_plan.md`.

Un temple englouti, à moitié noyé : briques de prismarine et de pierre moussue, prismarine sombre, lanternes de mer encastrées, forêts d'algues, herbiers, coraux, lichen lumineux, colonnes brisées dans les bassins, lianes sous les voûtes. Pas de grappin (aucun ancrage), pas de conduit, pas de lave, ni coffre, ni conteneur, ni générateur, ni monstre, ni boss.

**État : généré et contrôlé par script, pas encore testé en jeu.** Les durées de parcours sont des estimations.

## Poser et remettre à zéro

- Le générateur crée lui-même le datapack `atelier` du monde visé (dossiers et `pack.mcmeta`) ; si le monde n'existe pas, il le dit et n'écrit que `atelier/exports`. Le monde est désigné par son nom en jeu (`--monde "Donjon 2"` par défaut), pas par le nom de son dossier.
- Datapack créé pendant que le monde est ouvert : quitter le monde et y revenir pour qu'il soit pris en compte.
- Pose : `/reload` puis `/function atelier:aqua_pose` (ne pas mettre en pause ; la zone au-dessus du sol est effacée avant la pose, on peut donc reposer une nouvelle version).
- Entrée : `/tp @s 178 -17 272`.
- Remise à zéro (portes refermées, leviers baissés, écluse remplie, clé retirée, compteurs à zéro) : `/function atelier:aqua_reset`.
- Les blocs de commande règlent `doMobSpawning` sur `false` dans tout le monde et `commandBlockOutput` sur `false`.

## Le Voile des Profondeurs (lu dans le code du mod)

- Objet `grappin:voile`, un casque. Il est **donné automatiquement** en entrant dans le Débarcadère (une fois : pas si on l'a déjà sur la tête ou dans l'inventaire).
- Tant que les yeux sont sous l'eau et qu'il reste de la réserve, l'air du porteur est maintenu au maximum ; la réserve dure **40 s** (`voileReserveSecondes`).
- À l'air libre il se recharge entièrement en **12 s** (`voileRechargeSecondes`). Il prévient (message et son) sous **10 s** (`voileAlerteSecondes`).
- Réserve vide : le voile ne protège plus, on se noie normalement (15 s d'air, puis les dégâts). Les coups reçus n'usent pas le voile.

### Aucune autre façon de respirer sous l'eau

- Dans tout le gabarit, les effets Respiration aquatique et Puissance du conduit sont effacés à chaque tick (potions, casque de tortue, conduit).
- Les tunnels submergés sont des **zones de pression** (26 boîtes) : toutes les demi-secondes, tout joueur qui s'y trouve **sans le voile sur la tête** prend 3 points de dégâts de noyade (un joueur à 20 PV meurt en quelques secondes, soit une dizaine de blocs de nage au plus ; cadence exacte à mesurer en jeu). Un casque enchanté Respiration ne sert donc à rien : c'est le voile qui est contrôlé, pas l'air.
- Limites connues : les dégâts ne touchent pas un joueur en créatif ou en spectateur ; rien n'empêche un joueur en survie de casser les murs (prévoir le mode aventure) ; une perle de l'Ender peut franchir une grille ouverte à vue, pas un tunnel coudé.

## Parcours (≈ 27 min sans les salles cachées, ≈ 31 min avec quelques-unes ; estimation)

| # | Section | Durée | Ce qu'on y fait | Porte de sortie |
|---|---|---|---|---|
| 1 | Le Débarcadère | 2 min | On reçoit le voile. Le levier est au fond du petit bassin (174 -21 281) : première plongée. | G1, grille 176 -17 301 → 178 -14 301 |
| 2 | La Colonnade | 4 min | Grand bassin semé de colonnes. On nage jusqu'à la colonne centrale, trois marches, bouton au sommet (190 -15 322). Le panneau du mur ouest donne l'ordre des glyphes pour la Bibliothèque. | G2, grille 223 -17 320 → 223 -14 322 |
| 3 | Les Citernes | 4 min | Trois citernes reliées par des passages inondés. Deux vannes sur des îlots (239 -16 322 et 261 -16 322), à lever toutes les deux. | G3, grille noyée au fond de la troisième citerne, 280 -23 333 → 282 -23 335 |
| 4 | Tunnel T1 | 1 min | **Voile obligatoire.** 48 blocs sous l'eau, 22 s en nage ordinaire. Sortie dans le bassin de la Bibliothèque. | — |
| 5 | La Bibliothèque noyée | 4 min | Quatre glyphes de terre cuite sur le mur nord, à presser dans l'ordre lu dans la Colonnade. Une erreur remet à zéro (son grave). | G4, grille 248 -17 360 → 248 -14 362 |
| 6 | Les Écluses | 4 min | Le levier (231 -16 360) vide le bassin A et fait céder la grille du fond ; on descend par l'échelle. Sur l'îlot du bassin B, une dalle (238 -17 361) donne la **Clé du Récif**. | G5, grille au fond du bassin A, 215 -27 359 → 217 -27 361 |
| 7 | Tunnel T2 | 1 min | **Voile obligatoire.** 56 blocs, 25 s. Le plus long : on arrive avec moins de la moitié de la réserve. | — |
| 8 | La salle des Pompes | 3 min | Trois leviers sur le mur nord (170 -16 346, 174 -16 346, 178 -16 346), tous en haut. | G6, grille noyée dans le second bassin, 189 -23 367 → 191 -23 369 |
| 9 | Tunnel T3 | 1 min | **Voile obligatoire.** 54 blocs, 25 s, avec une descente de 12 blocs. | — |
| 10 | L'Antichambre | 1 min | Porte à clé : se présenter devant, la Clé du Récif en main (elle est consommée). Sans la clé, un message l'indique. | G7, grille 204 -17 395 → 204 -14 397 |
| 11 | Le Cœur du Temple | 2 min hors combat | Salle du boss : îlot central au milieu d'un bassin profond de 29 blocs. | SORTIE, grille 291 -17 402 → 291 -14 404 |

Le parcours n'est pas un simple couloir : l'ordre des glyphes se lit dans la section 2 et sert dans la 5, la clé se prend dans la 6 et sert dans la 10, les deux vannes des Citernes se font dans l'ordre qu'on veut, et huit salles cachées s'ouvrent en chemin. Mais les portes principales se franchissent toujours dans le même ordre (voir « Écarts »).

## Les passages qui exigent le voile

| Passage | Entrée (grille) | Sous l'eau | Nage ordinaire | Nage rapide | Pourquoi on ne passe pas sans voile |
|---|---|---|---|---|---|
| T1 | 280 -23 333 → 282 -23 335 | 48 blocs | 22 s | 12 s | Zone de pression sur toute la longueur : mort en quelques secondes, bien avant la sortie. |
| T2 | 215 -27 359 → 217 -27 361 | 56 blocs | 25 s | 14 s | Zone de pression sur toute la longueur : mort en quelques secondes, bien avant la sortie. |
| T3 | 189 -23 367 → 191 -23 369 | 54 blocs | 25 s | 14 s | Zone de pression sur toute la longueur : mort en quelques secondes, bien avant la sortie. |
| Salle cachée 5 | puits en 180 -21 316 | 36 blocs | 16 s | 9 s | Même zone de pression. |
| Salle cachée 6 | puits en 246 -23 318 | 34 blocs | 15 s | 8 s | Même zone de pression. |
| Salle cachée 7 | puits en 274 -25 358 | 38 blocs | 17 s | 10 s | Même zone de pression. |
| Salle cachée 8 | puits en 270 -43 402 | 38 blocs | 17 s | 10 s | Même zone de pression. |

Avec le voile (40 s de réserve, puis 15 s d'air) : le plus long trajet prend 25 s en nage ordinaire. Il reste de la marge, mais pas de quoi hésiter ou faire demi-tour au milieu. Le contrôle refuse tout tunnel qui dépasserait 32 s (80 % de la réserve).

## Solutions

1. Débarcadère : plonger dans le petit bassin, lever le levier du fond.
2. Colonnade : nager jusqu'à la colonne centrale, monter les trois marches, presser le bouton.
3. Citernes : lever les deux vannes (îlots des citernes 1 et 2), puis plonger au fond de la citerne 3.
4. Bibliothèque : **Poulpe, Raie, Crabe, Étoile**.
5. Écluses : lever le levier (le bassin A se vide, la grille cède), prendre la clé sur l'îlot du bassin B, descendre par l'échelle et plonger dans le puits. Le levier baissé remplit de nouveau le bassin ; la grille reste ouverte.
6. Pompes : les trois leviers en haut, puis plonger dans le second bassin.
7. Antichambre : clé en main devant la grille.

## Salle du boss

| Repère | Position | Rôle |
|---|---|---|
| B1 | 251 -17 403 | Îlot central (apparition du boss) |
| B2 | 222 -17 403 | Rive ouest, côté entrée |
| B3 | 282 -17 403 | Rive est, côté sortie |
| B4 | 240 -33 403 | Sous l'eau, à mi-profondeur, côté ouest |
| B5 | 262 -33 403 | Sous l'eau, à mi-profondeur, côté est |
| Sortie | 295 -17 403 | Derrière la grille scellée |

- Ouvrir la sortie (à relier à la mort du boss) : `/function atelier:aqua_ouvrir_sortie_boss`.
- La refermer : `/function atelier:aqua_fermer_sortie_boss`.
- Le bassin du Cœur n'est pas une zone de pression : on peut y plonger sans voile, avec son seul souffle.

## Salles cachées (8, dont 4 qui exigent le voile)

Chacune fait 9 × 9 × 5, vide, éclairée. Emplacement libre pour un coffre et pour des monstres indiqués ci-dessous.

| # | Accès | Voile | Bouton ou puits | Emplacement du coffre | Emplacement des monstres |
|---|---|---|---|---|---|
| 1 | S1 : faux mur de briques fissurées dans le mur est (bouton discret) | non | bouton 195 -16 278 | 203 -17 277 | 207 -17 273 |
| 2 | S3 : faux mur dans le mur nord, derrière un rideau d'algues (bouton discret) | non | bouton 242 -16 304 | 243 -17 281 | 247 -17 277 |
| 3 | S3 : panneau de pierre dans le mur nord (bouton discret) | non | bouton 262 -16 304 | 263 -17 281 | 267 -17 277 |
| 4 | S6 : paroi coulissante dans le mur ouest (bouton discret) | non | bouton 210 -16 362 | 199 -17 361 | 203 -17 357 |
| 5 | S2 : puits au fond du grand bassin de la colonnade, tunnel submergé vers une salle cachée à l'air libre | oui | puits 180 -21 316 | 165 -37 319 | 170 -37 314 |
| 6 | S3 : puits dans la première citerne, tunnel submergé | oui | puits 246 -23 318 | 233 -37 321 | 238 -37 316 |
| 7 | S5 : puits dans le bassin de la bibliothèque, tunnel submergé | oui | puits 274 -25 358 | 257 -37 361 | 262 -37 356 |
| 8 | S10 : galerie sous le bord du bassin du Cœur, tunnel submergé | oui | puits 270 -43 402 | 277 -37 405 | 282 -37 400 |

## Contrôles automatiques

- Générateur : eau confinée (aucune source au contact d'une case d'air sur le côté ou dessous), aucun bloc interdit (coffre, conteneur, générateur, conduit, lave, colonne de bulles, ancrage), chaque levier, bouton, panneau et dalle a son appui. 0 erreur.
- Éclairage : lumière propagée depuis les lanternes de mer et le lichen, puis blocs `light` invisibles (immergés dans l'eau) là où il manquait. Lumière minimale 5 sur toutes les cases libres, eau comprise ; les monstres n'apparaissent qu'à 0.
- Vérificateur : résultat **OK**. Avec le voile, chaque étape donne exactement les cibles prévues, aucune plus tôt. Sans voile et toutes portes ouvertes, on n'atteint que : H1, H2, H3, S1, bouton_G2, bouton_H1, bouton_H2, bouton_H3, levier_3a, levier_3b, levier_G1 — rien au-delà du premier tunnel, aucune salle cachée sous l'eau.

## Écarts et points à savoir

- **Pas testé en jeu** : ni la pose, ni les blocs de commande, ni le ressenti des tunnels. À faire dans le client du dépôt.
- Le voile vient de la PR #12 (Grappin 2.5.0), pas encore fusionnée : cette branche est empilée dessus.
- Pas de cascades ni de courants : une cascade est de l'eau qui coule, donc non confinée, et un courant demande des colonnes de bulles, exclues. L'ambiance repose sur les bassins, les algues et la lumière.
- Parcours principal en chaîne (G1 à G7 dans l'ordre) ; la non-linéarité tient aux renvois entre salles (glyphes, clé) et aux salles cachées, pas à des embranchements.
- L'écluse : une fois la grille cédée, on peut aussi rebaisser le levier et plonger dans le bassin plein. Le vidage n'est donc obligatoire qu'une fois.
- Le vérificateur ne simule pas les sauts d'un bloc à l'autre ni les perles de l'Ender ; il suppose qu'on ne casse et ne pose aucun bloc.
