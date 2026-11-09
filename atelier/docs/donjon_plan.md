<!-- [claude-build] plan du donjon n°1 refait (phase 2, a valider avant construction) -->
# Donjon n°1 refait — « La Gorge suspendue » (plan à valider)

Monde « Donjon 1 » (dossier `Nouveau monde (1)`), Minecraft 1.21.1 + NeoForge + Grappin 2.2. Même style qu'avant : roche en grandes plaques (pierre, andésite, tuf, pierre moussue), mousse et lichen luisant sur les parois, lianes à baies lumineuses, troncs et racines de jungle et de palétuvier, champilumières, l'Arbre-Mère. En plus : pierre craquelée et moussue, piliers, voûtes, alcôves, éboulis, chaînes, toiles d'araignée, ossements, lanternes, bougies, mobilier abandonné (tables, chaises, lits défaits, établis), fresques. Chaque salle a son décor et son histoire : une expédition d'explorateurs qui a tenté de rejoindre le Gardien.

Emprise prévue : 150 × 110 × 170 blocs (X -75 à 74, Y -61 à 48, Z -85 à 84), à côté du terrain d'essai du grappin, qui reste.

## Le parcours en 10 sections (≈ 31 min pour qui découvre)

| # | Section | Ce qu'on y fait | Temps |
|---|---|---|---|
| 1 | Le Seuil des Racines | Tunnel d'entrée (60 blocs, 15 s), grappin donné. Tutoriel : 3 ancrages au-dessus d'un ruisseau peu profond ; tomber ne coûte que 20 s (on remonte à l'échelle). | 2 min |
| 2 | La Galerie des Échos | La grille de sortie s'ouvre avec 3 leviers : un en vue, un caché sous des lianes, un sur une corniche qu'on n'atteint qu'en se balançant. Allers-retours (≈ 150 blocs, 35 s) et recherche. | 3 min |
| 3 | Le Gouffre de l'Arbre-Mère | Enchaînement de 8 ancrages au-dessus de la fosse, puis treuil pour remonter à une corniche haute. 2 à 3 chutes probables, ≈ 40 s chacune (retour au point de passage du bord). | 5 min |
| 4 | La Cascade | Parkour sur corniches mouillées (8 sauts) et 2 balanciers derrière la chute d'eau ; le bouton qui ouvre la suite est caché derrière l'eau. | 3 min |
| 5 | Le Jardin des Symboles | Carrefour. Énigme : 4 symboles (feuille, goutte, racine, œil) gravés en fresque dans les sections 1 à 4, chacun avec 1 à 4 lanternes = son rang. On appuie sur les 4 socles dans cet ordre ; une erreur remet à zéro. | 4 min |
| 6 | La Clé de l'Arbre | Embranchement depuis le Jardin : puits vertical de 35 blocs (balanciers et treuil) jusqu'au sanctuaire de la clé (« Clé de racine », donnée par commande), puis retour au Jardin. | 4 min |
| 7 | La Salle des Lianes | Grande salle de combat (mobs plus tard) ; la porte de sortie ne s'ouvre qu'avec la Clé de racine (section 6 obligatoire). Compté en partie seulement. | 2 min |
| 8 | Les Ponts Brisés | Passerelles au-dessus du vide avec pièges : stalactites qui tombent, dalles qui s'ouvrent, plaques à poison. Point de passage avant. | 3 min |
| 9 | L'Ascension des Racines | Salle verticale de 50 blocs : balanciers en zigzag et treuil ; une chute renvoie au bas de la salle. | 4 min |
| 10 | Antichambre et Salle du Gardien | Grande salle du boss, vide (boss plus tard). | 1 min |
| | **Total** | sans les salles cachées ni les combats complets | **≈ 31 min** |

Bases des estimations : marche 4,3 blocs/s, sprint 5,6 blocs/s, un balancier visé et réussi ≈ 10 à 15 s, une chute ≈ 30 à 45 s perdues, une énigme ≈ 1 à 3 min de réflexion et d'essais.

## Ce qui empêche de le finir en 5 minutes

- Le crochet ne tient que sur les points d'ancrage, et il n'y en a que là où le balancier fait partie du parcours (≈ 25 dans tout le donjon).
- Les sections sont des salles fermées, séparées par au moins 4 blocs de roche, et reliées par des portes à mécanisme : on ne voit ni n'atteint un ancrage d'une autre section.
- Parois verticales sans rebord, plafonds fermés : pas de passage par-dessus les murs.
- Des portes qui demandent d'aller chercher quelque chose ailleurs (leviers, symboles, clé) : retours en arrière obligatoires, non linéaires.
- La sortie n'est jamais visible depuis l'entrée.
- Contrôle : un nouveau vérificateur calcule, pour chaque ancrage, tout ce qu'on peut atteindre en se balançant (rayon de corde + élan au lâcher, avec une marge) et vérifie qu'aucun ne mène au-delà d'une porte fermée ou ne saute une section.

## Les 8 salles cachées (hors parcours, au moins 7 × 7 × 4, place pour un coffre et des mobs, une sortie)

| # | Où | Accès |
|---|---|---|
| 1 | Seuil des Racines | Faux mur de mousse dans le tunnel (on passe à travers des tapis de mousse sur des lianes) |
| 2 | Galerie des Échos | Trappe sous une racine, dans une alcôve |
| 3 | Gouffre de l'Arbre-Mère | Ancrage caché, visible seulement depuis une corniche : balancier jusqu'à une alcôve dans la paroi |
| 4 | La Cascade | Derrière la chute d'eau |
| 5 | Jardin des Symboles | Mur de bibliothèques qui s'ouvre (pistons) avec un levier dissimulé dans un pot de fleurs |
| 6 | Clé de l'Arbre | Bouton caché dans le puits, sous un champignon géant |
| 7 | Salle des Lianes | Ancrage dans la voûte, accès à une salle au-dessus du plafond |
| 8 | Ascension des Racines | Derrière un rideau de lianes qu'on escalade |

Rien n'y est posé : ni coffre, ni mob, ni générateur. La fiche finale donnera pour chacune ses coordonnées, l'emplacement du coffre et la zone pour les mobs.

## Règles de construction

- Aucun coffre, conteneur (donc pas de distributeur pour les pièges), générateur ni mob.
- Eau et lave confinées (le générateur vérifie chaque bloc d'eau) ; tout le donjon éclairé ; `doMobSpawning false` dans la chaîne de commandes.
- Les mécanismes (portes, énigme, clé, pièges, points de passage) sont faits de blocs de commande et de redstone simple ; ils se testent en jeu.

## Étapes après ta validation

3. Sauvegarde du monde (fermé), puis démolition de l'ancien donjon (aucun coffre ni conteneur dedans).
4. à 8. Structure et parcours, mécanismes, ancrages, salles cachées, décor et éclairage : un seul générateur, posé par une fonction qui charge les chunks et les libère à la fin.
9. Vérification anti-raccourcis, puis la fiche finale `donjon.md`.
