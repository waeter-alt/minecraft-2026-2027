<!-- [claude-build] plan du donjon n°2 « Le Temple des Mille Racines » (a valider avant construction) -->
# Donjon n°2 — « Le Temple des Mille Racines » (plan à valider)

Style : jungle, comme le donjon n°1 (roche en grandes plaques, briques moussues et fissurées, mousse, lichen luisant, lianes à baies lumineuses, troncs et racines de jungle et de palétuvier, champignons lumineux). Concept différent de la Gorge suspendue : ici on **descend** un temple enseveli sous une colline de jungle, par étages de salles et de couloirs, avec embranchements et retours en arrière. Le grappin sert dans trois grandes salles verticales seulement (Grappin 2.4.0 : clic maintenu = traction, relâché = on pend, ESPACE ou Maj = lâcher, molette = corde).

## Cadre

- Monde « Donjon 2 » neuf, superflat, sol à Y-61, dans l'instance de test (`test-local`).
- Gabarit prévu : **130 × 100 × 130** blocs. La colline de surface fait partie de la structure (la structure est « enfouie » dans sa propre terre). Entrée au sommet, salle du Gardien tout en bas.
- Scripts : `scripts/donjon_temple.py` (générateur) et `scripts/verif_donjon_temple.py` (contrôles), construits à partir des fonctions de `donjon2.py` (roche, salles voûtées, ancrages, salles cachées). Les chemins de monde sont à adapter à l'arborescence `test-local/launcher/instances/…`.
- Mécanismes par blocs de commande (comme le n°1) : portes, plaques, leviers, retour au dernier point de passage après une chute. Pas de coffre, conteneur, spawner ni mob : ni dispensers ni tonneaux ; les pièges sont des blocs qui tombent ou cèdent.
- Eau et lave : confinées. Éclairage calculé partout (aucune apparition hostile naturelle).

## Le parcours en 12 sections (≈ 31 min pour qui découvre)

| # | Section | Ce qu'on y fait | Temps |
|---|---|---|---|
| 1 | Le Parvis | Sommet de la colline : ruines de marches, stèles. On descend l'escalier d'entrée ; le grappin est donné en entrant. | 2 min |
| 2 | Le Vestibule des Fresques | Hub de l'étage 1. 3 portes scellées vers 3 ailes ; 4 fresques de symboles à observer (elles servent aux sections 3, 4 et 6). | 3 min |
| 3 | L'Aile des Serpents | Salle à mosaïque de dalles : marcher sur les bonnes dalles dans l'ordre de la fresque (une erreur : dalle empoisonnée et retour au seuil). Récompense : **Idole du Serpent**. | 3 min |
| 4 | L'Aile des Miroirs d'Eau | Bassin confiné traversé par des racines de palétuvier et des corniches mouillées (parkour, 8 sauts) ; un levier caché sous l'eau derrière une chute. Récompense : **Idole du Jaguar**. | 3 min |
| 5 | Le Puits des Lianes | Grande salle verticale : tutoriel de 3 ancrages bas au-dessus d'une eau peu profonde, puis 6 ancrages en zigzag et 2 corniches de repos jusqu'en haut. Chute : retour à l'eau du bas. Récompense : **Idole de l'Aigle**. | 4 min |
| 6 | Le Sanctuaire des Trois Idoles | Retour au Vestibule : 3 socles à bouton à presser dans l'ordre indiqué par les fresques (une erreur remet à zéro). Ouvre le puits de descente vers l'étage 2. | 2 min |
| 7 | La Salle des Colonnes Brisées | Grande salle à piliers pour les combats (mobs plus tard), porte de sortie close sans la **Clé de racine**. Compté en partie seulement. | 2 min |
| 8 | Le Couloir des Pièges | Passerelle étroite : stalactites qui tombent, tronçons qui cèdent puis reviennent, 3 plaques empoisonnées, une brèche à franchir à l'ancrage. Chute : retour au début. | 3 min |
| 9 | Le Grand Gouffre Intérieur | Caverne centrale : ponts de racines brisés, 7 ancrages en arc au-dessus de la fosse, treuil jusqu'à la corniche d'en face. Chute : retour à la corniche de départ. | 4 min |
| 10 | La Crypte des Racines | Embranchement : sanctuaire de la **Clé de racine**, remontée par 3 ancrages scellés dans les parois, retour à la salle 7 pour ouvrir sa porte (retour en arrière obligatoire). | 3 min |
| 11 | L'Antichambre | Serrure à clé (porte de sortie de la 7) puis couloir de statues menant à l'arche du Gardien. | 1 min |
| 12 | La Salle du Gardien | Grande salle à colonnes de troncs, estrade au fond : vide, le boss viendra plus tard. | 1 min |
| | **Total** | sans les salles cachées ni les combats complets | **≈ 31 min** |

Bases : marche 4,3 blocs/s, sprint 5,6 blocs/s, balancier réussi 10 à 15 s, chute 30 à 45 s perdues, énigme 1 à 3 min.
Ordre imposé : 1 → 2 → (3, 4, 5 dans l'ordre voulu) → 6 → 7 → 8 → 9 → 10 → retour à 7 → 11 → 12.

## Ce qui empêche de le finir en 5 minutes

- Les trois idoles sont toutes nécessaires et se trouvent dans trois ailes fermées ; le sanctuaire (6) ne s'ouvre pas sans elles.
- La clé n'est qu'en 10, après la 9 ; la porte qu'elle ouvre est dans la 7, derrière soi : retour en arrière obligatoire.
- Les ancrages n'existent que dans les sections 5, 9 et 10 (≈ 20 au total), et un vérificateur calcule pour chacun tout ce qu'on peut atteindre en se balançant (rayon de corde, élan au lâcher, marge) : aucun ne doit mener au-delà d'une porte fermée ni sauter une section.
- Sections fermées, séparées par au moins 4 blocs de roche ; parois sans rebord, plafonds clos, pas de passage par-dessus les murs ; la sortie n'est jamais visible depuis l'entrée.
- Les trois énigmes sont de nature différente : ordre de dalles (3), parcours et levier caché (4), ordre de boutons déduit des fresques (6).

## Salles cachées (8, au moins 7 × 7 × 4, vides)

Chacune : un emplacement dégagé pour ton coffre, une zone de combat, une sortie. Ni coffre ni mob.

| # | Où | Accès |
|---|---|---|
| 1 | Parvis | Trappe sous une dalle moussue, derrière une stèle |
| 2 | Vestibule | Faux mur de briques fissurées, levier dissimulé dans une fresque |
| 3 | Aile des Serpents | Bibliothèque en pierre qui pivote (bouton caché) |
| 4 | Aile des Miroirs d'Eau | Passage derrière la chute d'eau |
| 5 | Puits des Lianes | Ancrage caché dans une alcôve de lianes |
| 6 | Colonnes Brisées | Trappe au sol sous un pilier effondré |
| 7 | Gouffre | Corniche invisible depuis l'entrée, atteinte par un ancrage isolé |
| 8 | Crypte | Faux mur de racines, plaque de pression sous la mousse |

## Livrables

- `exports/donjon_temple.nbt`, plan PNG, `exports/donjon_temple_cibles.json`, fonctions de pose et de remise à zéro.
- `docs/donjon_temple.md` : fonctionnement, plan par section avec temps et total, solutions des énigmes, clé et leviers, liste des ancrages, salles cachées (coordonnées, accès, coffre, zone de mobs), résumé et écarts.

## Phases (vérification après chacune ; plan validé avant de construire)

1. Plan validé (ce document). 2. Structure et parcours principal. 3. Énigmes, mécanismes, portes, pièges. 4. Sections de grappin et ancrages. 5. Salles cachées. 6. Détails, décoration, éclairage. 7. Contrôle anti-raccourcis. Un test en jeu par toi après les phases 2 à 4.
