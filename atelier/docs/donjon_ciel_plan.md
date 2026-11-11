<!-- [claude-build] plan du donjon n°3 « L'Archipel des Nuées », cadré avec Luca le 2026-11-11 ; validé par Luca ; construit par donjon_ciel.py. La fiche de ce qui est construit, avec les écarts avec ce plan, est donjon_ciel.md -->
# Donjon n°3 — « L'Archipel des Nuées » (plan validé)

Un temple céleste en ruine, éclaté en îles flottantes. L'objet du donjon est la **Paravoile** (mod Grappin 2.6.0, branche `feature/paravoile`), remise à l'entrée : une voile de planeur comme dans Breath of the Wild. On monte à l'intérieur des îles, on redescend en planant de l'une à l'autre.

## La paravoile (ce que le donjon peut en attendre)

- Elle se tient en main (principale ou secondaire). En l'air, Espace la déploie ; Espace de nouveau ou s'accroupir la range.
- Réglages par défaut : vol de **10 s**, recharge de 6 s au sol ou dans l'eau, vitesse en avant 0,36 bloc/tick (7,2 blocs/s), descente à plat 0,09 bloc/tick (1,8 bloc/s). Le regard dirige : tête baissée on pique (jusqu'à 1,5 fois plus vite, 3 fois plus bas), tête haute on freine.
- Elle ne fait que descendre. À plat et pleine vitesse, un vol de 10 s couvre au mieux **72 blocs** pour **18 blocs de chute** (finesse 4 pour 1). Piquer ou freiner fait perdre plus de hauteur par bloc parcouru ; rien ne permet d'en gagner.
- Pas de courants ascendants : on ne remonte que par l'intérieur des îles.

## Cadre

- Monde « Donjon 3 », superflat, sol à Y-61, dans le client de test du dépôt (à créer, comme « Donjon 1 » et « Donjon 2 »). NeoForge 21.1.216.
- Gabarit d'environ 230 × 120 × 230, presque vide : ce sont les îles qui comptent. À ajuster quand le tracé sera posé.
- Le ciel est le décor : pas de sol de nuages dangereux. Un bloc de commande ramène au dernier point de passage quiconque descend sous le niveau de la plus basse île (même mécanisme que la gorge du donjon 1).
- Palette : pierre claire et calcite en ruines, quartz, cuivre oxydé, mousse, avec des lanternes à intervalles réguliers. Détails à fixer avec Luca avant le décor.
- Interdits : élytres, plume tombante, perle d'Ender, ancrage de grappin, chorus, coffre, conteneur, générateur, monstre, boss.
- Tout est éclairé (lumière minimale 5 sur toutes les surfaces praticables), îles flottantes comprises.

## Le principe de jeu

- **Monter à l'intérieur** : chaque île a un étage ou une tour, avec escaliers, échelles et salles d'énigme (leviers, boutons, plaques : tout ça tient à l'air libre).
- **Descendre en planant** : chaque île est plus basse que celle d'où l'on part, d'au moins un quart de la distance (sinon le vol est impossible). On ne remonte qu'à pied, à l'intérieur.
- **Varier le vol** : écarts courts, écarts longs, îlot-relais pour recharger la jauge, slalom entre des piliers, atterrissage sur une plateforme étroite qui demande de piquer puis de freiner.
- **Chute dans le vide** : retour au dernier point de passage, sans perte.

## Sections

| # | Section | Mécanisme | Vol suivant (distance, chute) | Temps estimé |
|---|---|---|---|---|
| 1 | Le Débarcadère | Don de la paravoile, panneau d'explication, première île en vue | 25 blocs, 8 de chute | 2 min |
| 2 | Les Pierres suspendues | Chaîne de cinq îlots, atterrissages courts | 25 à 40 blocs | 3 min |
| 3 | La tour de l'Aiguille | Montée intérieure à leviers (3 étages), envol depuis le balcon, entre des arches | 55 blocs, 18 de chute | 4 min |
| 4 | Le Moulin des Vents | Trois girouettes (leviers) sur trois îlots dispersés, dans l'ordre qu'on veut ; elles ouvrent la passerelle | 45 blocs | 5 min |
| 5 | La grande traversée | Vide de 90 blocs, coupé par un îlot-relais où la jauge se recharge | 2 vols de 45 blocs | 4 min |
| 6 | Les Colonnes | Slalom entre des piliers, plateforme d'atterrissage étroite | 50 blocs | 4 min |
| 7 | La Citadelle haute | Clé à trouver dans les salles, porte à clé | — | 5 min |
| 8 | Le Belvédère | Dernière montée intérieure, puis grand vol d'approche | 55 blocs, 20 de chute | 3 min |
| 9 | Le Cœur des Nuées | Salle du boss à ciel ouvert sur la plus grande île | — | 2 min hors combat |

Total ≈ 32 min. Les distances sont des cibles : le vérificateur a le dernier mot (voir plus bas).

## Salles cachées

Six salles de 9 × 9 × 5, chacune avec un emplacement libre pour un coffre : trois dans les îles du parcours (faux mur, bouton de pierre), trois sur des îlots isolés à 40 ou 50 blocs du chemin, qu'on ne rejoint qu'en volant et qu'il faut quitter par un second vol.

## Le contrôle (verif_donjon_ciel.py)

Sur le modèle de `verif_donjon_aqua.py` et `verif_donjon2.py`, mais pour le vol. Le vérificateur simule la physique de la paravoile (mêmes constantes que le mod) :

- **Marche** : cases praticables à pied, avec sauts de 1 bloc, échelles et escaliers ; portes fermées étape par étape.
- **Vol réaliste** : un vol est valide s'il est faisable à vitesse modérée (60 blocs au plus, jauge de 10 s, chute au moins égale à un quart de la distance, au plus égale à la distance), sans obstacle sur la trajectoire.
- **Vol généreux** : vol parfait de 80 blocs. Aucun raccourci ne doit passer, même ici : une île sautée ne doit jamais être accessible.
- **Sans paravoile** : de la première île, rien d'autre n'est joignable que le Débarcadère.
- **Relais** : chaque vol dépasse le budget de la jauge sans relais ; les relais sont bien des îlots où l'on peut se poser et recharger.
- **Hauteur** : chaque île est assez basse pour le vol qui l'atteint ; aucune montée à pied ne contourne une section.

## Phases de construction

1. Tracé des îles (positions, hauteurs) et vérification des vols. 2. Volumes intérieurs, escaliers, échelles, salles d'énigme. 3. Portes, leviers, clé, salle du boss. 4. Blocs de commande (don de la paravoile, retour au dernier point de passage, portes). 5. Décor. 6. Éclairage. 7. Contrôles du générateur. 8. Vérificateur de vol. 9. Fiche, puis test en jeu dans le client du dépôt.

## Organisation et limites

- Le mod (paravoile) et le donjon sont deux sujets, donc deux PR : le donjon s'empile sur celle du mod, et la base de chacune est à décider par Duncan.
- À essayer en jeu avant de figer les distances : le ressenti réel du vol (vitesse, pique, frein) peut demander de retoucher les réglages de la paravoile, donc les écarts ci-dessus.
- La voile n'est pas visible en vue à la première personne ; elle l'est en troisième personne et pour les autres joueurs.
- Pas encore décidés : le nombre exact d'îles, le décor précis, le boss.
