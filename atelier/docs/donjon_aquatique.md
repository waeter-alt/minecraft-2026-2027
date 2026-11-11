<!-- [claude-build] fiche du donjon aquatique, ecrite par scripts/fiche_donjon_aqua.py -->
# Donjon aquatique « Le Sanctuaire des Marées »

Monde « Donjon 2 » (dossier `Donjon 2 (1)`, superflat, sol à Y-61 ; le Temple des Mille Racines est dans un autre monde, « Donjon 1 »), Minecraft 1.21.1 + NeoForge 21.1.216 + mod **Grappin 2.5.0** (il apporte le Voile des Profondeurs ; obligatoire chez chaque joueur). Structure `aqua` 150 × 70 × 170, posée en 150 -61 260 (x 150 à 299, z 260 à 429). Générateur : `scripts/donjon_aqua.py`, contrôle : `scripts/verif_donjon_aqua.py`, plan : `donjon_aquatique_plan.md`.

Un monument océanique englouti, dans le style de ceux de Minecraft : briques de prismarine, prismarine et prismarine sombre en bandeaux, sols en damier, lanternes de mer encastrées dans les murs, les sols et les plafonds, pilastres, éponges mouillées dans les salles cachées, noyau d'or dans la salle du boss. Les salles sont **noyées jusqu'au plafond** : on nage presque partout. Il ne reste à l'air que le Débarcadère (le départ), la salle du boss, les salles cachées d'air et des cloches d'air flottantes. Pas de grappin (aucun ancrage), pas de conduit, pas de lave, ni coffre, ni conteneur, ni générateur, ni monstre, ni boss.

**État : généré et contrôlé par script, pas encore testé en jeu.** Les durées de parcours sont des estimations.

## Poser et remettre à zéro

- Le générateur crée lui-même le datapack `atelier` du monde visé (dossiers et `pack.mcmeta`) ; si le monde n'existe pas, il le dit et n'écrit que `atelier/exports`. Le monde est désigné par son nom en jeu (`--monde "Donjon 2"` par défaut), pas par le nom de son dossier.
- Datapack créé pendant que le monde est ouvert : quitter le monde et y revenir pour qu'il soit pris en compte.
- Pose : `/reload` puis `/function atelier:aqua_pose` (ne pas mettre en pause ; la zone au-dessus du sol est effacée avant la pose, on peut donc reposer une nouvelle version).
- Entrée : `/tp @s 178 -17 272`.
- Remise à zéro (portes refermées, leviers baissés, trappe refermée, clé retirée, compteurs à zéro) : `/function atelier:aqua_reset`.
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

## Cloches d'air : les seuls points de recharge

Le voile ne se recharge qu'à l'air libre (12 s pour une recharge complète). Dans des salles noyées jusqu'au plafond, il faut donc des endroits où l'on peut remonter respirer : **23 cloches d'air**, de petits pavillons de prismarine de 5 × 5, ouverts par dessous, posés sur quatre pieds, avec une lanterne de mer au plafond. L'air y reste emprisonné ; on y entre en nageant par dessous. Il y en a une tous les 20 blocs dans chaque salle (calotte à Y local 52), et six basses (calotte à 46) juste au dessus de l'entrée ou de la sortie de chaque tunnel. On respire aussi dans le mini-bassin du départ, dans le bassin du boss et sous le plafond des salles cachées.

Ce n'est pas tout à fait « sans poche d'air » comme demandé : sans elles, le donjon serait infaisable (40 s de réserve, 55 s au maximum avec l'air de base). À toi de décider si on en veut moins.

| # | Position (centre, à hauteur de l'air) |
|---|---|
| 1 | 168 -11 312 |
| 2 | 168 -11 332 |
| 3 | 188 -11 312 |
| 4 | 188 -11 332 |
| 5 | 208 -11 312 |
| 6 | 234 -11 312 |
| 7 | 234 -11 332 |
| 8 | 254 -11 312 |
| 9 | 254 -11 332 |
| 10 | 274 -11 312 |
| 11 | 274 -11 332 |
| 12 | 258 -11 354 |
| 13 | 278 -11 354 |
| 14 | 218 -11 354 |
| 15 | 238 -11 354 |
| 16 | 168 -11 354 |
| 17 | 188 -11 354 |
| 18 | 168 -11 390 |
| 19 | 188 -11 390 |
| 20 | 281 -17 334 |
| 21 | 270 -17 355 |
| 22 | 216 -17 360 |
| 23 | 190 -17 368 |

## Parcours (≈ 27 min sans les salles cachées, ≈ 31 min avec quelques-unes ; estimation)

Presque rien ne se fait à pied. Les interrupteurs sont des **vannes** : des trappes en bois posées à plat sous l'eau, qui se cliquent à la main (clic droit). Un levier ou un bouton ne tient pas sous l'eau, qui le détruit en coulant dans sa case ; une trappe se waterlogue, elle reste.

| # | Section | Durée | Ce qu'on y fait | Porte de sortie |
|---|---|---|---|---|
| 1 | Le Débarcadère | 2 min | Seule partie à sec. On reçoit le voile. Une vanne au fond du petit bassin, entre deux lanternes de mer (178 -24 281) : un clic et la herse de la galerie noyée se lève. Première plongée. | G1, herse 176 -24 294 → 178 -22 294 |
| 2 | La Colonnade | 4 min | Grande salle noyée, semée de colonnes en prismarine. La vanne est au sommet de la colonne centrale (190 -14 322). Le panneau du mur ouest donne l'ordre des glyphes pour la Bibliothèque. | G2, herse 223 -17 320 → 223 -14 322 |
| 3 | Les Citernes | 4 min | Trois citernes profondes reliées par des passages. Deux vannes sur des socles (238 -17 322 et 260 -17 322), à ouvrir toutes les deux. | G3, grille au fond de la troisième citerne, 280 -23 333 → 282 -23 335 |
| 4 | Tunnel T1 | 1 min | **Voile obligatoire.** Depuis la cloche au dessus du puits : 44 pas, 20 s en nage ordinaire. Sortie dans le bassin de la Bibliothèque. | — |
| 5 | La Bibliothèque noyée | 4 min | Quatre glyphes de terre cuite sur le mur nord, chacun avec sa vanne : à ouvrir dans l'ordre lu dans la Colonnade (les vannes se referment seules). Une erreur remet à zéro (note grave). | G4, herse 248 -17 360 → 248 -14 362 |
| 6 | Les Écluses | 4 min | Sur le socle du bassin B, une vanne (238 -17 361) donne la **Clé du Récif** et fait céder la grille au fond du bassin A. | G5, grille au fond du bassin A, 215 -27 359 → 217 -27 361 |
| 7 | Tunnel T2 | 1 min | **Voile obligatoire.** 59 pas, 27 s. | — |
| 8 | La salle des Pompes | 3 min | Trois vannes sur le mur nord (170 -16 346, 174 -16 346, 178 -16 346), toutes à ouvrir. | G6, grille dans le second bassin, 189 -23 367 → 191 -23 369 |
| 9 | Tunnel T3 | 1 min | **Voile obligatoire.** 57 pas, 26 s, avec une descente de 12 blocs. | — |
| 10 | L'Antichambre | 1 min | Herse à clé : se présenter devant, la Clé du Récif en main (elle est consommée). Sans la clé, un message l'indique. Derrière, une galerie noyée mène sous la salle du boss : 61 pas, 28 s (sans pression, mais longue). | G7, herse 200 -17 395 → 200 -14 397 |
| 11 | Le Cœur du Temple | 2 min hors combat | On émerge dans le bassin du boss (salle à l'air libre, îlot central, rives sèches, noyau d'or dans le mur nord). | SORTIE, grille 291 -17 402 → 291 -14 404 |

Le parcours n'est pas un simple couloir : l'ordre des glyphes se lit dans la section 2 et sert dans la 5, la clé se prend dans la 6 et sert dans la 10, les deux vannes des Citernes se font dans l'ordre qu'on veut, et huit salles cachées s'ouvrent en chemin. Mais les portes principales se franchissent toujours dans le même ordre (voir « Écarts »).

## Les passages qui exigent le voile

Trajet complet = de la cloche d'air la plus proche de l'entrée, jusqu'à celle de la sortie (la sortie des salles cachées est à l'air).

| Passage | Grille d'entrée | Tunnel | Total | Nage ordinaire | Nage rapide | Pourquoi on ne passe pas sans voile |
|---|---|---|---|---|---|---|
| T1 | 280 -23 333 → 282 -23 335 | 40 blocs | 44 pas | 20 s | 11 s | Zone de pression sur toute la longueur : mort en quelques secondes, bien avant la sortie. |
| T2 | 215 -27 359 → 217 -27 361 | 46 blocs | 59 pas | 27 s | 15 s | Zone de pression sur toute la longueur : mort en quelques secondes, bien avant la sortie. |
| T3 | 189 -23 367 → 191 -23 369 | 48 blocs | 57 pas | 26 s | 14 s | Zone de pression sur toute la longueur : mort en quelques secondes, bien avant la sortie. |
| Salle cachée 5 | puits en 180 -21 316 | 35 blocs | 49 pas | 22 s | 12 s | Même zone de pression. |
| Salle cachée 6 | puits en 246 -23 318 | 31 blocs | 49 pas | 22 s | 12 s | Même zone de pression. |
| Salle cachée 7 | puits en 274 -25 358 | 33 blocs | 40 pas | 18 s | 10 s | Même zone de pression. |
| Salle cachée 8 | puits en 270 -43 402 | 15 blocs | 36 pas | 16 s | 9 s | Même zone de pression. |

Avec le voile (40 s de réserve, puis 15 s d'air) : le plus long trajet prend 28 s en nage ordinaire. Il reste de la marge, mais pas de quoi hésiter ou faire demi-tour au milieu. Le contrôle refuse tout trajet qui dépasserait 32 s (80 % de la réserve) et toute vanne à plus de 35 pas d'une prise d'air.

## Solutions

1. Débarcadère : plonger dans le petit bassin, ouvrir la vanne du fond (clic droit sur la trappe).
2. Colonnade : nager jusqu'à la colonne centrale, ouvrir la vanne à son sommet.
3. Citernes : ouvrir les deux vannes (socles des citernes 1 et 2), puis plonger au fond de la citerne 3.
4. Bibliothèque : **Poulpe, Raie, Crabe, Étoile**.
5. Écluses : ouvrir la vanne du socle du bassin B (clé et grille), puis plonger dans le bassin A jusqu'au puits.
6. Pompes : ouvrir les trois vannes, puis plonger dans le second bassin.
7. Antichambre : clé en main devant la herse, puis suivre la galerie noyée jusqu'au bassin du boss.

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
- Le bassin du Cœur et la galerie qui y mène ne sont pas des zones de pression : on peut y plonger sans voile, avec son seul souffle.

## Salles cachées (8, dont 4 qui exigent le voile)

Chacune fait 9 × 9 × 5, vide, éclairée. La 1 est à sec (porte dans le mur du Débarcadère, bouton de pierre). Les 2, 3 et 4 sont noyées, avec une rangée d'air sous le plafond, sol en éponge mouillée, porte camouflée en prismarine et vanne dans la salle voisine. Les 5 à 8 sont des salles d'air au bout d'un tunnel noyé (voile obligatoire). Emplacement libre pour un coffre et pour des monstres indiqués ci-dessous.

| # | Accès | Voile | Vanne, bouton ou puits | Emplacement du coffre | Emplacement des monstres |
|---|---|---|---|---|---|
| 1 | S1 : faux mur de prismarine dans le mur est du Débarcadère (bouton de pierre discret, à sec) | non | bouton 195 -16 278 | 203 -17 277 | 207 -17 273 |
| 2 | S3 : faux mur dans le mur nord de la salle des Citernes (vanne discrète, salle noyée) | non | vanne 242 -16 304 | 243 -17 281 | 247 -17 277 |
| 3 | S3 : faux mur dans le mur nord, plus loin (vanne discrète, salle noyée) | non | vanne 262 -16 304 | 263 -17 281 | 267 -17 277 |
| 4 | S6 : faux mur dans le mur ouest des Écluses (vanne discrète, salle noyée) | non | vanne 210 -16 362 | 199 -17 361 | 203 -17 357 |
| 5 | S2 : puits au fond du grand bassin de la Colonnade, tunnel submergé vers une salle cachée d'air | oui | puits 180 -21 316 | 165 -37 319 | 170 -37 314 |
| 6 | S3 : puits dans la première citerne, tunnel submergé | oui | puits 246 -23 318 | 233 -37 321 | 238 -37 316 |
| 7 | S5 : puits dans le bassin de la bibliothèque, tunnel submergé | oui | puits 274 -25 358 | 257 -37 361 | 262 -37 356 |
| 8 | S10 : galerie sous le bord du bassin du Cœur, tunnel submergé | oui | puits 270 -43 402 | 277 -37 405 | 282 -37 400 |

## Contrôles automatiques

- Générateur : eau confinée (aucune source au contact d'une case d'air sur le côté ou dessous), aucun bloc interdit (coffre, conteneur, générateur, conduit, lave, colonne de bulles, ancrage), aucun levier ni bouton au contact de l'eau, panneaux d'au plus 14 caractères par ligne, chaque panneau et bouton a son appui. 0 erreur.
- Éclairage : lumière propagée depuis les lanternes de mer encastrées, puis blocs `light` invisibles (immergés dans l'eau) là où il manquait. Lumière minimale 5 sur toutes les cases libres, eau comprise ; les monstres n'apparaissent qu'à 0.
- Vérificateur : résultat **OK**. Avec le voile, chaque étape donne exactement les cibles prévues, aucune plus tôt. Sans voile et toutes portes ouvertes, on n'atteint que : H1, H2, H3, S1, bouton_H1, vanne_3a, vanne_3b, vanne_G1, vanne_G2, vanne_H2, vanne_H3 — rien au-delà du premier tunnel, aucune salle cachée sous l'eau.

## Écarts et points à savoir

- **Pas testé en jeu** : ni la pose, ni les blocs de commande, ni le ressenti des tunnels. À faire dans le client du dépôt.
- Le voile vient de la PR #12 (Grappin 2.5.0), pas encore fusionnée : cette branche est empilée dessus.
- La salle du boss garde ses rives sèches et son air libre ; le Débarcadère aussi. Tout le reste est noyé.
- Pas de cascades ni de courants : une cascade est de l'eau qui coule, donc non confinée, et un courant demande des colonnes de bulles, exclues. L'ambiance repose sur le décor de monument et la lumière.
- Un petit herbier (algues, herbes, quelques coraux) ne pousse que dans la Colonnade ; ailleurs, le décor reste celui d'un monument.
- Parcours principal en chaîne (G1 à G7 dans l'ordre) ; la non-linéarité tient aux renvois entre salles (glyphes, clé) et aux salles cachées, pas à des embranchements.
- Le vérificateur ne simule pas les sauts d'un bloc à l'autre ni les perles de l'Ender ; il suppose qu'on ne casse et ne pose aucun bloc.
