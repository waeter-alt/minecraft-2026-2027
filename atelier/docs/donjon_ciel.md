<!-- [claude-build] genere par scripts/fiche_donjon_ciel.py -->
# Donjon n°3 — L'Archipel des Nuées (paravoile)

Monde « Donjon 3 » (superflat, sol à Y-61). Structure `ciel` de 280 × 120 × 280, posée en 100 -61 100. Il faut le mod Grappin 2.6.1 (NeoForge 21.1.216).

## Pose

Le générateur écrit la structure et le datapack dans le monde « Donjon 3 » (il doit exister : à créer dans le client de test, en superflat). Ensuite, en jeu : `/reload`, puis

```
/function atelier:ciel_pose
```

Le jeu se fige un moment après 30 secondes de chargement : ne pas mettre en pause. Entrée : `/tp @s 130 38 140`. Remise à zéro : `/function atelier:ciel_reset`.

## La paravoile

Donnée à l'entrée (à qui ne l'a pas déjà). Elle se tient en main. En l'air, Espace la déploie ; Espace ou s'accroupir la range. Le regard dirige : tête baissée on pique, tête haute on freine.
Réglages par défaut : vol de **10 s**, recharge de 6 s au sol, 7,2 blocs/s en avant, 1,8 bloc/s de descente à plat (finesse 4 pour 1), donc au mieux 72 blocs pour 18 de chute.
Elle ne fait que descendre : on remonte à l'intérieur des tours. Sont retirés dans tout le donjon : élytres, perles d'Ender, charges de vent, fruits de chorus, trident, effets de chute lente et de lévitation.

## Les vols (le plus long vol réaliste de chaque paire, d'après le vérificateur)

| Vol | Longueur et chute |
|---|---|
| D → P1 | 33.0 blocs, chute de 11, 4.6 s sur 10 s |
| P1 → P2 | 44.9 blocs, chute de 15, 6.2 s sur 10 s |
| P2 → P3 | 41.3 blocs, chute de 14, 5.7 s sur 10 s |
| P3 → AI | 38.8 blocs, chute de 13, 5.4 s sur 10 s |
| AI → HA | 45.3 blocs, chute de 16, 6.3 s sur 10 s |
| AI → G1 | 49.0 blocs, chute de 22, 6.8 s sur 10 s |
| G1 → G2 | 47.1 blocs, chute de 19, 6.5 s sur 10 s |
| G2 → G3 | 49.5 blocs, chute de 19, 6.9 s sur 10 s |
| G3 → MV | 47.9 blocs, chute de 19, 6.7 s sur 10 s |
| MV → HB | 39.6 blocs, chute de 17, 5.5 s sur 10 s |
| MV → RL | 55.2 blocs, chute de 21, 7.7 s sur 10 s |
| HB → RL | 29.8 blocs, chute de 10, 4.1 s sur 10 s |
| RL → CP | 58.8 blocs, chute de 21, 8.2 s sur 10 s |
| CP → CI | 43.0 blocs, chute de 21, 6.0 s sur 10 s |
| CI → BE | 48.7 blocs, chute de 21, 6.8 s sur 10 s |
| BE → BO | 56.6 blocs, chute de 21, 7.9 s sur 10 s |

Un vol est « réaliste » s'il fait au plus 60 blocs, perd entre un tiers de sa longueur et sa longueur, et que le trajet est libre de tout bloc. Le contrôle « généreux » (80 blocs, au moins un quart de chute, sans obstacle) ne doit ouvrir aucun raccourci.

## Parcours (≈ 32 min ; estimation)

| # | Section | Ce qu'on y fait | Porte ou vol suivant |
|---|---|---|---|
| 1 | Le Débarcadère | Départ, don de la paravoile, panneaux d'explication sur la stèle (130 38 140). | Vol vers P1 : 33.0 blocs, chute de 11, 4.6 s sur 10 s |
| 2 | Les Pierres suspendues | Trois îlots (P1, P2, P3), une lanterne au centre de chacun. On y plane sans montée. | P1→P2, P2→P3, P3→AI |
| 3 | La tour de l'Aiguille | Les glyphes ouvrent la porte ; puis trois niveaux à monter, chacun fermé par une herse qu'un levier du même niveau fait céder. Balcon à 334 15 138. | Vol vers G1 : 49.0 blocs, chute de 22, 6.8 s sur 10 s |
| 4 | Le Moulin des Vents | Trois girouettes (leviers au sommet des tours de G1, G2, G3) ouvrent la herse du moulin. | G1→G2→G3→MV, puis MV→RL |
| 5 | Le Relais | Tour de recharge sur le grand vide de MV à CP (90 blocs, coupés en deux vols). Balcon à 267 2 350. | RL→CP : 58.8 blocs, chute de 21, 8.2 s sur 10 s |
| 6 | Les Colonnes | Trois colonnes de quartz sur la ligne droite et trois anneaux de vol à traverser (nord, sud, nord) ; la herse de la tour de la plateforme s'ouvre au troisième. | CP→CI : 43.0 blocs, chute de 21, 6.0 s sur 10 s |
| 7 | La Citadelle haute | Un levier dans l'aile ouest fait céder la herse de la crypte (aile est) ; le jeu des lampes de la crypte donne la **Clé des Nuées**. Porte de la tour à clé (en main, devant la porte). Balcon à 150 16 346. | CI→BE : 48.7 blocs, chute de 21, 6.8 s sur 10 s |
| 8 | Le Belvédère | Dernière tour, sans herse : on monte, on se lance. Balcon à 170 28 285. | BE→BO : 56.6 blocs, chute de 21, 7.9 s sur 10 s |
| 9 | Le Cœur des Nuées | Arène à ciel ouvert sur la grande île (240 11 240) : place de quartz, douze colonnes, estrade. Sortie scellée. | — |

Un îlot caché (HA, entre AI et G1) et un autre (HB, entre MV et RL) sont des détours facultatifs : on y va depuis la tour d'en face, on en repart du toit de la cabane par l'échelle.

## Les énigmes

Quatre énigmes s'ajoutent aux herses et aux leviers du parcours. Les états sont communs à tous les joueurs du monde (comme les herses) ; `/function atelier:ciel_reset` remet tout à zéro.

1. **Les glyphes de l'Aiguille.** Sur la place du Débarcadère, une stèle du ciel (panneau à 136 39 134) donne l'ordre : **Vent, Pluie, Nuage, Foudre**. Au pied de la tour de l'Aiguille, quatre boutons de pierre portent ces noms (Vent 342 -14 135, Pluie 342 -14 137, Nuage 342 -14 139, Foudre 342 -14 141) : il faut les presser dans cet ordre pour ouvrir la porte de la tour. Une erreur remet la série à zéro.

2. **Les anneaux de vol (Colonnes).** Trois anneaux de lueur dorée sur la ligne de vol du Relais vers la plateforme, dont les centres sont 250 -3 345, 240 -8 355, 230 -13 345. Cadres de blocs dorés de 11 × 11, ouverture de 9 × 9 : assez large pour qu'un vol à plat passe les trois sans piquer. Les colonnes de quartz sont rangées de chaque côté de la ligne de vol, pas dessus. On les traverse en planant, dans cet ordre ; chacun traversé devient vert. Le troisième ouvre la herse de la tour de la plateforme. Si on rate un anneau, on atterrit quand même sur la plateforme : le levier de rappel (220 -16 347) ramène au Relais et remet les anneaux à zéro.

3. **Les lampes de la crypte (Citadelle).** Cinq lampes sur le mur du fond, avec une bande de couleur dessous (de haut en bas, du nord au sud : rouge, bleu, vert clair, jaune, violet) et un bouton chacune. Le bouton « start » (164 -15 343, mur nord) fait s'allumer les lampes une à une : **bleu, jaune, rouge, violet, vert clair**. Pendant le spectacle, les boutons sont sans effet ; ensuite on presse les boutons dans le même ordre. Une erreur remet à zéro (on relance avec « start »). Réussir donne la **Clé des Nuées**.

4. **Les trois éclats.** Un levier donne un éclat d'améthyste nommé (une seule fois par joueur : chacun prend les siens) : Éclat de Brume (P2, 232 12 142), Éclat de Foudre (RL, 262 -9 349), Éclat des Nuées (BE, 168 -1 292). Les trois ensemble dans l'inventaire d'un joueur proche de l'autel de l'arène (240 11 244) sont consommés et **ouvrent la sortie du boss**. Si on arrive dans l'arène sans eux, un **levier de retour** (222 10 246, panneau « Il manque des éclats ? ») ramène aussitôt les joueurs proches au Débarcadère pour refaire le trajet (les herses déjà ouvertes le restent) ; il retire aussi les éclats et les étiquettes de ceux qui le tirent, pour qu'ils puissent les reprendre.

Ces énigmes sont vérifiées pour l'accès (boutons, leviers, autel atteignables, anneaux traversables par un vol réaliste) ; leur logique de blocs de commande n'a pas été essayée en jeu.

## La ville dans les airs (décoration)

Chaque tour est un lieu de vie avec son rôle, ses couleurs et son bois :

| Tour | Étages (du bas vers le haut), puis terrasse | Bois et couleurs |
|---|---|---|
| L'Aiguille (AI) | bibliothèque, salle des cartes, observatoire (télescope, étoiles), terrasse | épicéa, bleu |
| Maisons de G1, G2, G3 | salon / chambre ; chambre / atelier ; atelier / salon | bouleau jaune, cerisier rose, chêne vert |
| Le Moulin (MV) | salle de la meule, marché, grenier | chêne noir, brun et jaune |
| Le Relais (RL) | taverne (comptoir, tables), chambres de voyageurs | acajou, rouge |
| La gare des Colonnes (CP) | gare (bancs), marché, salle des cartes | acacia, orange |
| La Citadelle (CI) | grand hall, salle du conseil (table longue), chapelle, bibliothèque | chêne noir, violet et gris |
| L'Orangerie du Belvédère (BE) | jardin, salon de thé, jardin haut | bouleau, vert clair et cyan |

Chaque niveau a en plus un tapis, des poutres et un lustre, et des fenêtres à jardinière (sauf au rez-de-chaussée, où la jardinière gênerait le tour de la tour). Devant chaque porte : un store rayé, une enseigne (« Observatoire », « Taverne du Relais »...), des lanternes et des pots. Les balcons ont des créneaux, des bancs, des jardinières, une lanterne à chaque coin et un fanion. Rien n'est posé sur le côté de l'escalier, celui d'où l'on arrive, ni devant les entrées et sorties : le vérificateur le contrôle.

Sur les grandes îles (Débarcadère, Aiguille, Moulin, Citadelle, Belvédère, arène), des maisons à toit de pente avec cheminée et intérieur meublé, des étals de marché à auvent rayé, des arbres, des bancs et des puits. Des fleurs poussent dans l'herbe de toutes les îles. L'Aiguille, le Moulin et le Belvédère ont été agrandies (rayons 17, 17 et 15) pour faire de la place.

## Solutions

1. Débarcadère : sauter dans le vide, ouvrir la paravoile (Espace) et viser P1.
2. Pierres : P1, P2, P3 (en piquant un peu pour atteindre P2, qui est la plus longue).
3. Aiguille : presser les glyphes dans l'ordre de la stèle du ciel, puis tirer le levier de chaque niveau (mur d'en face de l'escalier), monter au balcon, plonger vers G1.
4. Girouettes : lever au sommet de chaque tour de G1, G2, G3 (monter au balcon, levier sur le mât) ; puis le moulin s'ouvre.
5. Moulin : monter au balcon, plonger vers le Relais, puis du Relais vers la plateforme des Colonnes en traversant les trois anneaux ; monter dans la tour de la plateforme.
6. Citadelle : levier de l'aile ouest, puis le jeu des lampes dans la crypte (la clé arrive en main), puis la clé en main devant la porte de la tour ; monter au balcon.
7. Belvédère : monter au balcon et plonger vers l'arène.

## Coordonnées

| Île | Sol (pieds) | Rayon | Hauteur du balcon |
|---|---|---|---|
| D | 130 38 140 | 14 | — |
| P1 | 178 27 130 | 5 | — |
| P2 | 230 12 142 | 5 | — |
| P3 | 276 -2 132 | 5 | — |
| AI | 325 -15 146 | 17 | 334 15 138 |
| G1 | 340 -4 195 | 7 | 343 8 195 |
| G2 | 335 -8 242 | 7 | 338 4 242 |
| G3 | 341 -12 290 | 7 | 344 0 290 |
| MV | 323 -16 355 | 17 | 332 8 347 |
| RL | 266 -10 350 | 7 | 267 2 350 |
| CP | 221 -16 350 | rect [11, 5] | 208 2 350 |
| CI | 145 -16 358 | 16 | 150 16 346 |
| BE | 161 -2 291 | 15 | 170 28 285 |
| BO | 240 10 246 | 30 | — |
| HA | 300 2 168 | 8 | — |
| HB | 296 -6 324 | 8 | — |

## Boss

Position du boss (centre de l'estrade) : `240 11 240`. La sortie est une herse de 3 × 3 devant la chambre du trésor (240 10 220) : `/function atelier:ciel_ouvrir_sortie_boss` l'ouvre (à relier à la mort du boss), `/function atelier:ciel_fermer_sortie_boss` la referme. Emplacement de coffre dans la chambre : `238 10 222`. Aucun monstre ni boss n'est posé.

## Salles cachées (5)

| # | Accès | Emplacement du coffre | Emplacement des monstres |
|---|---|---|---|
| 1 | D : dalle du sol qui s'efface (bouton de pierre discret au pied d'un bloc de mur) ; bouton 121 38 137 | 124 32 139 | 129 32 134 |
| 2 | AI : dalle du sol qui s'efface (bouton de pierre discret au pied d'un bloc de mur) ; bouton 325 -15 139 | 328 -21 141 | 333 -21 136 |
| 3 | CI : dalle du sol qui s'efface (bouton de pierre discret au pied d'un bloc de mur) ; bouton 143 -16 347 | 146 -22 349 | 151 -22 344 |
| HA | îlot isolé : vol depuis la tour voisine, cabane de 9 × 9 × 5 (échelle jusqu'au toit) | 297 2 171 | 302 2 165 |
| HB | îlot isolé : vol depuis la tour voisine, cabane de 9 × 9 × 5 (échelle jusqu'au toit) | 293 -6 327 | 298 -6 321 |

Chaque salle fait 9 × 9 × 4 ou 5, éclairée. Trois sont sous le sol de l'île (D, AI, CI) : une dalle de 3 cases au niveau du sol s'efface quand on appuie sur le bouton de pierre posé au pied d'un petit bloc de mur ; l'escalier descend dessous.

## Contrôles

- Générateur : 0 erreur (aucun bloc interdit, aucun levier ni bouton ni panneau ni échelle sans appui, panneaux d'au plus 14 caractères par ligne, aucun bloc sous la ligne de chute, chaînes de blocs de commande toutes dans la roche, toutes les cases intérieures éclairées à 5 au moins).
- `verif_donjon_ciel.py` : OK. 8 étapes : —, H2, H2, H2, H2, H2, H3, H3.
  - Avec le vol réaliste, tout ce qui est prévu s'atteint, la chambre du boss reste fermée tant que la sortie l'est.
  - À chaque étape, le vol généreux n'atteint rien de ce que le réaliste n'atteint pas encore ; aucune île n'est sautée.
  - Sans paravoile (toutes portes ouvertes), on ne quitte pas le Débarcadère.
  - Aucune impasse : de chaque île atteinte, on rejoint l'arène du boss.
- Pas de test en jeu : ni la physique du vol, ni les blocs de commande, ni les tours n'ont été essayés ; les distances sont à confirmer par un vrai essai.

## Retour du vide

Chaque île a un numéro, mémorisé par joueur dans le tableau `ciel` quand on se tient dessus. Sous Y local 14, les blocs de commande téléportent au point de passage de la dernière île visitée (au sol, hors des tours). La chute ne fait pas de dégâts. Le point de réapparition du joueur suit aussi les îles : en arrivant sur une île, il est reporté à son point de passage, donc une mort (ou une déconnexion suivie d'une mort) ramène à la dernière île visitée.

## Écarts avec le plan

- **Trois îlots**, pas cinq, dans les Pierres suspendues : en ligne, plus serrés, un vol de P(k) vers P(k+2) deviendrait possible (raccourci).
- **Les girouettes se font dans l'ordre G1, G2, G3**, pas dans n'importe quel ordre : on ne remonte jamais, donc on ne peut pas revenir en arrière d'un îlot à l'autre.
- **La grande traversée** n'est pas un vide de 90 blocs coupé par un îlot au milieu : c'est MV→RL (54 blocs) puis RL→CP (51), avec le Relais comme tour de recharge.
- **Cinq salles cachées** (trois sous l'île, deux îlots), pas six.
- **La Citadelle** : la clé s'obtient au jeu des lampes de la crypte, pas dans des salles à fouiller.
- **Les éclats** ne sont pas sur le chemin obligatoire : Pierres (P2), Relais (RL) et Belvédère (BE) se visitent de toute façon, mais il faut penser à tirer les trois leviers (rien ne force à le faire avant l'arène) ; la sortie du boss reste scellée sans eux.
- **Le vol réaliste** accepte jusqu'à 60 blocs de trajet ; le plus long vol prévu fait 58.8 blocs (8.2 s sur 10 s).
- La voile n'est pas visible à la première personne.
- Le mod passe en **2.6.1** : la voile n'est plus rangée dès qu'on effleure un bloc, il faut rester au sol pendant 3 ticks de suite (avant, un seul tick suffisait, côté serveur comme côté joueur).
