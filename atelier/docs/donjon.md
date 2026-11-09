<!-- [claude-build] fiche du donjon n°1 refait, ecrite par sources/fiche_donjon2.py -->
# Donjon n°1 « La Gorge suspendue » (version 2, 2026-11-09)

Monde « Donjon 1 » (dossier `Nouveau monde (1)`), Minecraft 1.21.1 + NeoForge + mod Grappin 2.3.0 (obligatoire chez chaque joueur). Structure `donjon2` 160 × 120 × 176, posée en -75 -61 -85 (x -75 à 84, z -85 à 90). Générateur : `sources/donjon2.py`, contrôle : `sources/verif_donjon2.py`.

## Poser et remettre à zéro

- Pose (une seule fois, sans mettre en pause) : `/reload` puis `/function atelier:donjon_pose`. La fonction charge la zone, démolit l'ancien donjon (air sur chacun de ses blocs), pose le nouveau, remet tout à zéro et libère les chunks.
- Entrée : `/tp @s -77 -60 -4 -90 0` (on entre vers l'est).
- Remise à zéro entre deux parties : `/function atelier:donjon_reset` (portes fermées, leviers baissés, énigme, clé retirée des inventaires, stalactites et passerelle remises).

## Le grappin (mod Grappin 2.3.0)

- Clic droit : lance le crochet (portée 30 blocs). Il ne tient que sur les **points d'ancrage** (bloc métallique faiblement lumineux) ; il les attrape s'il passe à moins de 1,5 bloc. Sur tout autre bloc il rebondit.
- Accroché, la corde garde sa longueur : on se balance. Les touches de déplacement donnent un petit élan le long de l'arc.
- Maintenir **saut** : treuil, la corde se rembobine (6 blocs/s). **Molette** : allonge ou raccourcit la corde.
- **S'accroupir** : lâcher, en gardant sa vitesse, avec un petit saut vers le haut (réglage `sautAuLacher`, 0,42 = un saut normal). Clic droit accroché : lâcher et relancer aussitôt (enchaîner en l'air).
- Pas de dégâts de chute tant qu'on est accroché ; ils reviennent dès qu'on lâche. Le crochet ne traverse pas les blocs et ne blesse personne.
- Technique : lâcher en haut de l'arc, du côté où l'on va, puis tirer vers l'ancrage suivant avant de redescendre.
- Réglages : `config/grappin-common.toml` (portée, aimant, gravité du balancier, élan des touches, vitesse et saut au lâcher, treuil, molette).

## Le parcours (≈ 31 min pour qui découvre)

| # | Section | Ce qu'on y fait | Temps |
|---|---|---|---|
| 1 | Le Seuil des Racines | Escalier d'entrée (le grappin est donné en entrant). Fosse d'eau de 10 blocs de large : 3 ancrages bas pour apprendre le balancier. Une chute ne coûte que l'échelle. | 2 min |
| 2 | La Galerie des Échos | La grille de sortie (G1) s'ouvre quand les 3 leviers sont levés : un en vue près de la grille, un caché derrière des lianes au fond d'une alcôve, un sur une corniche haute qu'on n'atteint qu'avec l'ancrage du plafond. | 3 min |
| 3 | Le Gouffre de l'Arbre-Mère | 8 ancrages en arc autour de l'Arbre-Mère (écarts de 9 à 10 blocs), puis treuil jusqu'à la corniche haute du nord. Une chute dans la fosse renvoie à la corniche d'entrée. | 5 min |
| 4 | La Cascade | 8 piliers en spirale au-dessus du bassin, puis 2 balanciers vers l'alcôve cachée derrière la chute d'eau : son bouton ouvre la grille G2 (à l'est, par les marches de la corniche de départ). Une chute renvoie à la corniche de départ. | 3 min |
| 5 | Le Jardin des Symboles | Carrefour. 4 socles à bouton sous 4 fresques. Il faut les presser dans l'ordre du nombre de lanternes posées sous les mêmes fresques dans les salles 1 à 4. Une erreur remet à zéro (son grave), la réussite ouvre le passage du puits (son clair). | 4 min |
| 6 | La Clé de l'Arbre | On saute dans le puits (bassin au fond), on prend la Clé de racine sur la plaque du sanctuaire, puis on remonte avec 3 ancrages scellés dans les parois et le treuil. | 4 min |
| 7 | La Salle des Lianes | Grande salle à piliers (combats plus tard). La porte du sud ne s'ouvre qu'avec la Clé de racine en main, devant la serrure ; la clé est alors reprise. | 2 min |
| 8 | Les Ponts Brisés | Passerelle de 3 blocs à 48 au-dessus d'une fosse : 2 stalactites qui tombent quand on passe dessous, 2 tronçons qui cèdent 1/2 s après le passage (ils reviennent 2 s plus tard), 3 plaques empoisonnées, une brèche de 9 blocs à franchir à l'ancrage. Une chute renvoie au début de la passerelle. | 3 min |
| 9 | L'Ascension des Racines | Salle verticale : 6 ancrages en zigzag (écarts de 10 à 14 blocs) et 2 corniches de repos, jusqu'à la corniche de sortie à 40 blocs au-dessus de l'eau. Une chute ramène dans l'eau du bas. | 4 min |
| 10 | La Salle du Gardien | Grande salle à colonnes de troncs, estrade à l'ouest : vide, le boss viendra plus tard. | 1 min |
| | **Total** | sans les salles cachées ni les combats | **≈ 31 min** |

Bases : marche 4,3 blocs/s ; un balancier visé et réussi 10 à 15 s ; une chute 30 à 45 s perdues ; une énigme 1 à 3 min.

Ordre imposé : 1 → 2 → 3 → 4 → 5, puis 6 (la clé) et retour au 5, puis 7 → 8 → 9 → 10. Les sections 5, 6 et 7 forment un embranchement : on doit revenir sur ses pas.

## Solutions (réservé aux organisateurs)

- **Grille G1 (Galerie)** : 3 leviers. L1 en -20 -39 -8 (près de la grille), L2 en -38 -39 -21 (alcôve, derrière les lianes), L3 en -22 -32 13 (corniche haute, par l'ancrage G du plafond).
- **Grille G2 (Cascade)** : bouton en -11 -15 -73, au fond de l'alcôve derrière la chute d'eau (ancrages C1 puis C2).
- **Énigme du Jardin** : fresques et lanternes : racine (2 lanternes, salle 1), goutte (4 lanternes, salle 2), oeil (1 lanterne, salle 3), feuille (3 lanternes, salle 4). Ordre : **oeil → racine → feuille → goutte**. Socles : feuille 45 -10 -69, oeil 52 -10 -69, goutte 59 -10 -69, racine 66 -10 -69 (de l'ouest à l'est : feuille, œil, goutte, racine).
- **Clé de racine** : plaque du sanctuaire en 55 -47 -9, au fond du puits. Elle se donne une fois par joueur tant qu'il ne l'a pas.
- **Porte à clé G4** : se tenir devant la serrure (pierre magnétique) vers 38 -12 36 avec la clé en main.

## Les points d'ancrage

| Nom | Où | Coordonnées |
|---|---|---|
| T1 | Seuil (tutoriel) | -64 -44 -4 |
| T2 | Seuil (tutoriel) | -61 -44 -2 |
| T3 | Seuil (tutoriel) | -58 -44 -4 |
| G | Galerie, plafond (levier L3) | -28 -29 11 |
| A1 | Gouffre | -5 -25 -8 |
| A2 | Gouffre | -3 -23 -17 |
| A3 | Gouffre | -5 -21 -26 |
| A4 | Gouffre | 1 -20 -34 |
| A5 | Gouffre | 10 -19 -37 |
| A6 | Gouffre | 19 -18 -33 |
| A7 | Gouffre | 25 -16 -25 |
| A8 | Gouffre | 22 -11 -33 |
| H3 | salle cachée 3 | 30 -21 -15 |
| C1 | Cascade | 1 -5 -69 |
| C2 | Cascade | -6 -5 -67 |
| K1 | Puits de la clé | 46 -33 -16 |
| K2 | Puits de la clé | 65 -23 -20 |
| K3 | Puits de la clé | 49 -11 -24 |
| H7 | salle cachée 7 | 54 14 19 |
| P1 | Ponts Brisés (brèche) | 0 -3 62 |
| Z1 | Ascension | -30 0 64 |
| Z2 | Ascension | -39 4 68 |
| Z3 | Ascension | -45 11 60 |
| Z4 | Ascension | -38 17 55 |
| Z5 | Ascension | -35 24 43 |
| Z6 | Ascension | -42 33 41 |

26 ancrages en tout, aucun ailleurs.

## Les 8 salles cachées (vides)

| # | Accès | Intérieur (x y z min → max) | Coffre | Zone des mobs |
|---|---|---|---|---|
| 1 | Seuil des Racines : rideau de racines pendantes au fond de la salle, côté nord (on passe à travers) | -69 -54 10 → -61 -50 18 (9×9×5) | -68 -54 17 | autour de -65 -54 14 |
| 2 | Galerie des Échos : trappe dans le sol d'une alcôve, échelle | -39 -48 1 → -30 -44 10 (10×10×5) | -38 -48 9 | autour de -34 -48 5 |
| 3 | Gouffre : ancrage caché dans une niche de la paroi est, qu'on ne voit que depuis la fin de la chaîne d'ancrages | 33 -26 -19 → 41 -22 -11 (9×9×5) | 40 -26 -12 | autour de 37 -26 -15 |
| 4 | Cascade : derrière la chute d'eau, au fond de l'alcôve du bouton | -15 -16 -82 → -7 -12 -75 (9×8×5) | -14 -16 -81 | autour de -11 -16 -79 |
| 5 | Jardin : mur de bibliothèques qui s'ouvre avec un levier caché derrière les lianes d'un pilier | 73 -12 -57 → 81 -8 -47 (9×11×5) | 80 -12 -48 | autour de 77 -12 -52 |
| 6 | Puits de la clé : bouton caché sous un champignon géant, en bas du puits | 69 -47 -23 → 77 -43 -13 (9×11×5) | 76 -47 -14 | autour de 73 -47 -18 |
| 7 | Salle des Lianes : ancrage dans la voûte, au-dessus d'un trou ; on remonte au treuil | 50 10 15 → 60 14 25 (11×11×5) | 59 10 24 | autour de 53 10 18 |
| 8 | Ascension : rideau de lianes qu'on escalade, paroi sud | -39 -3 81 → -29 1 87 (11×7×5) | -38 -3 86 | autour de -34 -3 84 |

Chaque salle a une seule entrée (qui sert de sortie) et une lanterne. Rien n'y est posé : ni coffre, ni mob, ni générateur.

## Les anciens raccourcis et ce qui les bloque

| Ancien raccourci (version 1, finie en 5 min) | Remède |
|---|---|
| Le crochet s'accrochait à n'importe quel bloc | Il ne tient que sur les points d'ancrage, posés seulement là où le balancier fait partie du parcours |
| Traction en ligne droite sur 30 blocs | Balancier : la corde garde sa longueur, il faut de l'élan et enchaîner |
| Sections ouvertes les unes sur les autres | Salles fermées, coque de roche de 4 blocs, reliées par des tunnels à portes |
| Rien à chercher | 3 leviers, un bouton caché, une énigme, une clé à aller chercher (retours en arrière obligatoires) |
| Chute sans conséquence | Fosses qui renvoient au début de la section ; pièges sur les ponts |

Vérification (`verif_donjon2.py`) : à chaque étape, portes fermées, on calcule tout ce qu'on atteint à pied, en sautant et au grappin. Réglage réaliste (balancier de 14 blocs) : tout ce qui doit l'être est atteint. Réglage généreux (38 blocs, élan compris) : rien au-delà d'une porte fermée. Sans grappin, on ne passe même pas la fosse du Seuil. La sortie n'est pas visible depuis l'entrée.

## Mécanismes

49 blocs de commande en 2 chaînes enterrées sous les sols du Jardin et de la Salle des Lianes (aucun conteneur, donc pas de distributeur). Ils règlent aussi `doMobSpawning false` et `commandBlockOutput false`. Tout le donjon est éclairé (blocs de lumière invisibles), l'eau est contenue (vérifié par le générateur).

## Écarts avec la demande

- Le mur de bibliothèques de la salle cachée 5 s'ouvre par commande (le bloc disparaît), pas avec des pistons ; le levier est caché derrière des lianes, pas dans un pot de fleurs (Minecraft ne permet pas d'y poser un levier).
- Salle cachée 1 : rideau de lianes à baies au lieu d'un faux mur de mousse.
- Les combats (salle 7) et le Gardien ne sont pas comptés dans le temps : les mobs viendront plus tard.
- La redstone et les blocs de commande ne se vérifient qu'en jeu.
