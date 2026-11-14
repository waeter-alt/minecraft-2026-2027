# Boss « Léviathan » (Donjon 2, le Sanctuaire des Marées) — conception

Date : 2026-11-14 · Auteur : Luca (avec Claude) · Cible : mod « Donjon / Arène » (`atelier/donjon/arene`, id `grappin`), générateur `atelier/scripts/donjon_aqua*.py`, NeoForge figé du projet (21.1.216, Minecraft 1.21.1)

## Intention

Le Donjon 2 (Le Sanctuaire des Marées, un monument océanique englouti où l'on respire avec le **Voile des Profondeurs**, le « masque ») a une salle de boss (« Le Cœur du Temple ») mais pas de boss. Luca veut un boss **qu'on combat sous l'eau**, avec une salle du boss **entièrement noyée** et des poches d'air pour respirer, comme dans l'arène Abysses. Il n'a pas d'idée précise du boss et laisse Claude s'inspirer de mods existants (Léviathan de Cataclysm, Gardien Ancestral de Minecraft). Choix fait : **le Léviathan**, un immense serpent de mer qui nage dans toute la salle. En **phase 2, il attaque les cloches d'air**, qui sont les refuges du joueur.

Il doit être très différent des deux premiers boss : le Séraphin (volant, donjon 3) et le Gardien des Racines (fixe à points faibles, donjon 1). Ici le boss **bouge**, et la gestion de l'air est une part du jeu.

## Objectifs

- Un mob « Léviathan » + œuf d'apparition, dans le mod existant : serpent de ~35 blocs, tête, huit anneaux de corps, queue.
- Un combat 100 % sous l'eau, où l'on alterne entre attaquer et remonter respirer dans une cloche d'air (le Voile dure environ 40 s et se recharge en 12 s à l'air libre).
- La salle du boss refaite : entièrement noyée, avec des piliers ruinés comme abris et des cloches d'air en refuges.
- Branchement au donjon : apparition quand les joueurs arrivent dans la salle (annonce puis 5 s), sortie scellée ouverte à sa mort, remise à zéro.
- Tout l'équilibrage (vie, dégâts, durées) dans `config/grappin-common.toml`.

## Non-objectifs

- Pas de nouvel objet ni de nouvelle arme : les joueurs combattent avec ce qu'ils ont pensé à prendre (arc, épée, trident, arbalète).
- Pas de nouvelle mécanique de nage pour le joueur.
- Pas de refonte des autres salles du donjon, de ses tunnels ni de ses énigmes.
- Pas de butin ni de musique.

## Anti-objectifs (ce qui serait un échec même si ça « marche »)

- Un serpent coincé dans un mur ou un pilier, ou qui ne peut plus bouger.
- Des cloches détruites pour de bon : elles se reforment toujours.
- Un boss qu'on tue sans jamais exposer sa perle, ou qu'on tue en restant tout le combat dans une cloche.
- Un joueur qui n'a aucune chance de respirer : au moins une cloche reste utilisable à tout moment.
- De l'eau qui fuit hors de la salle ou dans la galerie d'entrée (toute l'eau du donjon est confinée et contrôlée par le générateur).

## Contraintes

- NeoForge 21.1.216 inchangé ; code dans `atelier/donjon/arene` (package `fr.atelier.grappin`), en-tête `// [claude-build]`, noms et commentaires en français.
- Règles du projet (CLAUDE.md) : une PR = un sujet, jamais de push sur `main`, merge décidé par Duncan, tester en jeu, un script ne suppose aucun état local (il crée dossiers et `pack.mcmeta`).
- Le boss combat à toutes les difficultés, Paisible comprise : dégâts « sans cause » (`DamageSource` `magic()` sans entité), `canAttack` surchargé, `removeWhenFarAway` faux et `requiresCustomPersistence` vrai (un mob invoqué par `/summon` perd sinon son drapeau persistant).
- Dépendance : cette branche part de `feature/boss-golem` (PR #21), qui dépend de `feature/boss-seraphin` (PR #19) ; elle dépendra donc de la #21.

## Approches envisagées

**Le corps**
1. **Pièces de collision qui suivent la tête (choix de Luca, recommandé)** : chaque anneau suit le chemin de la tête avec un décalage (comme le dragon de l'Ender), est une vraie zone de collision, et la perle est une pièce de la tête. Précis, et réutilise ce qui a été fait pour le golem.
2. Une seule grosse boîte et un corps purement visuel : plus simple, mais le corps est intouchable et il a l'air faux dans les courbes.
3. Une chaîne de petits mobs liés : fragile (un maillon bloqué déforme tout).

**Les cloches attaquées en phase 2**
1. **Gérées par le donjon (choix de Luca, recommandé)** : un bloc de commande détecte quand le serpent s'approche d'une cloche et la remplit d'eau 20 s, puis elle se reforme. S'appuie sur les cloches existantes et se règle sans recompiler.
2. Gérées par le mod : le serpent casse lui-même les blocs ; plus de code, et il faut lui apprendre où sont les cloches.

## Le combat

**Le serpent.** Environ 35 blocs : une tête de 5 × 5, huit anneaux de 4 × 4, une queue de 3 × 3. Il nage dans tout le volume de la salle, évite piliers et murs en regardant devant lui, et poursuit le joueur le plus proche (détection sur toute la salle). Les anneaux traversent les blocs (visuel), seule la tête est arrêtée par les murs.

**Ses attaques**
- **Morsure :** il fonce sur le joueur le plus proche et le mord (dégâts réglables).
- **Rayon de l'œil :** il s'immobilise, son œil brille 2 s (avertissement avec particules), puis une ligne droite traverse l'eau et blesse tout joueur sur son passage ; un pilier ruiné arrête le rayon.
- **Coup de queue :** un remous qui repousse fort les joueurs proches de sa queue.
- **Phase 2** (sous la moitié de sa vie) : annonce, aspect changé, attaques plus rapides, il plonge au fond et surgit, **et il attaque les cloches d'air**.

**Les points faibles.** Le corps prend très peu de dégâts (facteur réglable). **La perle sur son front** n'est atteignable que pendant ~6 s après chaque rayon, où le serpent reste étourdi ; une pièce de collision dédiée gonfle et brille à ce moment, avec un message. Les dégâts sur la perle sont multipliés.

**L'air.** Les cloches d'air sont les refuges : trop petites pour que le serpent y entre. En phase 2, une cloche dont le serpent s'approche se remplit d'eau pendant 20 s puis se reforme. Il y a toujours au moins une cloche utilisable.

## La salle du boss refaite

La salle existante est le Cœur du Temple : 79 × 47 blocs (x 62–140, z 120–166 en coordonnées du gabarit), de la surface (y 44) au plafond (y 66), avec une cuvette de 39 × 27 blocs (x 82–120, z 130–156) dont le fond est à y 14, un îlot central, des rives sèches, une galerie d'entrée noyée qui débouche sous la cuvette, et la sortie scellée dans le mur est (`SORTIE`, x 141, z 142–144).

Elle devient **entièrement noyée, du fond de la cuvette (y 14) jusqu'au plafond (y 66)** : plus d'îlot ni de rives sèches (les rives, à y 43, deviennent une étagère sous l'eau). Des piliers ruinés, de hauteurs variées, servent d'abris contre le rayon ; **sept à neuf cloches d'air** (le même type de pavillon de 5 × 5 que le reste du donjon, ouvert dessous) sont posées au sommet de certains piliers et accrochées en hauteur, à des endroits variés. Le serpent doit pouvoir nager sans se coincer (couloirs d'eau de 8 blocs au moins). Le Voile, déjà donné à l'entrée du donjon, suffit : aucune zone de pression n'est ajoutée dans cette salle.

## Intégration au donjon

- **Déclenchement :** quand un joueur débouche dans la salle (après avoir passé la galerie noyée et la herse à clé de l'antichambre), titre d'annonce, 5 s d'attente, puis le serpent surgit du fond. Même schéma que le Séraphin et le Gardien (variable de score, fonctions annonce/invoque/mort/vaincu).
- **Mort :** le mod appelle un tag de fonctions (`grappin:leviathan_mort`) ; le donjon ouvre la sortie (fonction `aqua_ouvrir_sortie_boss` existante) et affiche un message. Comme l'eau est confinée, la sortie doit s'ouvrir sans inonder le reste du donjon (le constructeur choisit : porte noyée, sas, etc.).
- **Cloches de la phase 2 :** un bloc de commande par cloche de la salle détecte le serpent à proximité (variable de score `#phase`), remplit la cloche d'eau, attend 20 s, puis la rétablit.
- **Remise à zéro :** supprime le serpent, annule une invocation en attente, rétablit toutes les cloches, referme la sortie, sans ouvrir la sortie par la mort.

## Précisions (après relecture du document par un constructeur indépendant)

- **L'air des cloches suffit.** Le Voile se recharge dès que les yeux du joueur ne sont plus dans l'eau (`isEyeInFluid(WATER)` faux dans `VoileEvenements`) : l'air emprisonné sous un pavillon compte comme de l'air libre. Rien à changer au Voile.
- **Comment une cloche contient de l'air.** Le pavillon (5 × 5, calotte pleine, murs de 2 blocs) a un intérieur de 3 × 3 × 2 blocs d'air, fermé sur les côtés et au-dessus, ouvert dessous. L'eau n'y monte pas (une source ne coule ni vers le haut ni dans le vide sans bord), c'est le mécanisme du reste du donjon (`cloche()` dans `donjon_aqua_missions.py`).
- **Remplir et rétablir une cloche.** Le générateur connaît le volume intérieur de chaque cloche de la salle. Remplir = `fill` de l'intérieur avec de l'eau (source) ; rétablir = `fill` de ce même intérieur en air, en ne remplaçant que l'eau. Rien à sauvegarder : l'état d'origine d'une cloche est toujours « intérieur en air ».
- **« Toujours une cloche utilisable ».** Garantie par le donjon : un compteur (variable de score) de cloches inondées en même temps, plafonné au tiers des cloches de la salle (8 cloches : 2 au plus), avec un délai de 15 s entre deux inondations ; au moins la moitié reste utilisable. Le mod ne publie rien de plus que la position du serpent et sa phase (le donjon lit `#phase` avec le tag de phase ou par la vie ; voir la fonction de phase 2 au plan).
- **La sortie.** La sortie scellée est dans le mur est de la salle ; au-delà, le donjon se termine (point `sortie`). Choix : le court couloir de sortie (6 blocs) est lui aussi noyé et mène à une petite salle de fin noyée, avec une rangée d'air sous son plafond, pour que l'eau reste confinée ; la grille s'ouvre à la mort du boss et le vérificateur contrôle que l'eau ne s'échappe pas ailleurs.
- **Corps traversant les blocs.** Les anneaux et la queue ne font **aucun dégât de contact** : seules les attaques (morsure, rayon, queue) blessent. Un joueur coincé entre un anneau et un mur ne subit rien. Les pièces de collision servent uniquement à recevoir des coups. Le nombre de pièces à synchroniser reste de dix (huit anneaux, la queue, la perle).
- **Rayon.** Un lancer de rayon sur les blocs solides seulement (collision `COLLIDER`, fluides ignorés) : l'eau ne l'arrête pas, les piliers et les pavillons des cloches l'arrêtent. Placement : au moins un pilier ou une cloche sur chaque ligne droite entre deux grands espaces d'eau, pour que l'abri existe.
- **Multijoueur.** Un à quatre joueurs ; une seule vie, sans mise à l'échelle ; il vise le joueur le plus proche (comme les deux autres boss) et passe sur un joueur nettement plus proche. Vie par défaut : 150 (réduite après test).
- **Dépendance.** Les API réutilisées sont celles du golem : `PartEntity` (`GolemPiece`), pièces repositionnées chaque tick, identifiants réservés par `ENTITY_COUNTER`, tag de mort. Elles ne bougent plus que si la PR #21 change ; la branche suit `feature/boss-golem`.

## Réglages décidés après les tests (Luca)

- **Vie : 150** (réduite après test).
- **Le serpent ne colle plus** : hors attaque, il tourne autour du joueur à 14 blocs, plus lentement ; il ne fonce que pour la morsure et la remontée de la plongée.
- **Cloches** : au plus un tiers inondées en même temps, délai de 15 s entre deux inondations, 20 s inondées.
- **Dépendance connue** : comme les zones de pression du donjon, la logique du boss tourne grâce à une chaîne de blocs de commande près du départ ; si la distance de simulation du serveur est inférieure à environ 7 chunks, elle ne tourne plus au fond de la salle.

## Mes calls (à vérifier)

- Nom : « Léviathan ». Vie 150 par défaut ; perle exposée 6 s après le rayon ; corps à 15 % des dégâts, perle à ×3 ; morsure 8, rayon 12, coup de queue 6 (dégâts de base à régler au test) ; rayon à 2 s d'avertissement ; cloche remplie 20 s.
- Le boss part de la branche `feature/boss-golem` ; PR séparée, dépendant de la #21.
- Version du mod 2.9.0, nouveau jar dans `atelier/exports/`.
- Textes : titre « Le Léviathan se réveille... ».

## Ce qui est laissé au constructeur

- Le modèle (écailles teal et bleu nuit, nageoires dorsales, mâchoire à dents, œil et perle lumineux, taches de bioluminescence) et la texture (générée en Python comme les autres boss).
- Le système de virage et d'évitement des obstacles, la mémoire des positions de la tête qui place les anneaux (identique côté client et serveur).
- Les coordonnées exactes des piliers et des cloches, la forme de la sortie noyée, les valeurs finales de réglage.

## Tests

- Tests JUnit de la logique pure (phase, choix d'attaque, durées, facteur de dégâts, calcul de la position des anneaux le long d'un chemin).
- Compilation ; générateur du donjon sans erreur ; vérificateurs du donjon (eau confinée, cloches utilisables, sortie atteignable seulement après le boss).
- Test en jeu par Luca avec le client du dépôt : apparition, nage, chaque attaque, perle, phase 2 et cloches inondées, respiration, mort et sortie.
