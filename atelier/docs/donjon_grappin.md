<!-- [claude-build] genere par sources/donjon_grappin.py -->
# Donjon n°1 — La Gorge suspendue (grappin)

Monde `saves/Nouveau monde (1)` (superflat par défaut, sol à Y-61). Structure de 100 × 120 × 140, posée en -50 -61 -70. Il faut le mod Grappin 1.1 (et NeoForge).

## Principe

- Une gorge de jungle fermée, avec l'Arbre-Mère au centre, qui monte du fond jusqu'au plafond. On avance d'île en île au grappin. Sans grappin, on ne quitte pas la corniche de départ (vérifié).
- Au fond, une fosse d'eau trouble : y tomber renvoie au dernier point de passage (départ, P4, J, RA, A1).
- L'Arbre-Mère cache les îles d'en face, et le trace monte en tournant. Les raccourcis possibles sautent au plus une ou deux îles, avec des tirs à la limite de la portée.
- Le grappin est donné à l'entrée à qui n'en a pas. Les créatures n'apparaissent pas (blocs de commande, 21 commandes sous le sol de la salle).

## Épreuves

- **1. Le premier saut** (depart → P1) : un vide de 8 blocs, au même niveau : on apprend le grappin.
- **2. Les piliers montants** (P1 → P2 → P3 → P4) : chaque pilier 5 à 6 blocs plus haut que le précédent.
- **3. Les deux voies** (P4 → J) : en bas, 3 piliers le long de la cascade (C1, C2, C3) ; en haut, 3 îles suspendues au plafond (H1, H2, H3) ; on peut changer de voie.
- **4. La traversée** (J → RA) : le plus long vide du donjon, en descendant.
- **5. Les racines suspendues** (RA → R1 → R2) : deux petites îles pendues au plafond, 7 blocs plus haut à chaque fois.
- **6. L'ascension** (R2 → A1 → F) : deux corniches accrochées aux parois, jusqu'à l'arche de la salle.
- **7. La Salle du Gardien** (F → salle) : grande salle naturelle, colonnes de troncs, sol de mousse ; le boss viendra plus tard.

## Coordonnées

- Entrée, au sol, côté ouest : -50 -60 -28. Escalier en 5 volées jusqu'à la corniche de départ.
- Corniche de départ : -36 -20 -18.
- Salle du Gardien : centre -6 26 47, entrée par l'arche -24 26 25.

| Île | Où l'on se tient |
|---|---|
| depart | -36 -20 -18 |
| P1 | -25 -19 -28 |
| P2 | -12 -14 -47 |
| P3 | 1 -8 -50 |
| P4 | 23 -2 -41 |
| C1 | 29 0 -25 |
| C2 | 28 1 -14 |
| C3 | 21 3 -1 |
| H1 | 35 6 -33 |
| H2 | 36 10 -14 |
| H3 | 28 12 2 |
| J | 9 6 10 |
| RA | -10 -2 8 |
| R1 | -26 5 12 |
| R2 | -30 13 2 |
| A1 | -37 21 12 |
| F | -25 26 19 |

Plan : `exports/donjon_grappin_plan.png` (réservé aux organisateurs).

## Contrôle

`sources/verif_grappin.py exports/donjon_grappin.nbt exports/donjon_grappin_cibles.json` : impossible sans grappin, possible avec, et la liste de tous les tirs possibles entre îles (portée 30, ligne de vue, passage du corps, bond d'arrivée du grappin 1.1).
