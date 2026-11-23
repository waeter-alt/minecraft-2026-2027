# Boss « Gardien des Racines » (donjon 1, Temple des Mille Racines) — conception

Date : 2026-11-14 · Auteur : Luca (avec Claude) · Cible : mod « Donjon / Arène » (`atelier/donjon/arene`, id `grappin`), générateur `atelier/scripts/donjon_temple.py`, NeoForge figé du projet (21.1.216, Minecraft 1.21.1)

## Intention

Le Donjon 1 (le Temple des Mille Racines) a une arène de boss, sa section 12, mais pas de boss. Luca veut un **golem d'arbre colossal, au centre de la salle, qui tire des projectiles**, dont les **points faibles apparaissent à des endroits différents** : il faut se balancer **au grappin** de plateforme en plateforme pour les atteindre **à l'arc**. Le combat doit faire du grappin et des plateformes la mécanique centrale, et être différent du Séraphin (boss volant du donjon 3, PR #19/#20).

Référence visuelle fournie par Luca : une figurine de golem d'arbre, corps de bois écorcé, deux grandes cornes de branches sur la tête, masque et plastron rouges, bouche rouge, un énorme poing rouge à corne, un bras de bois massif, jambes en rondins avec cernes, mousse verte. La ressemblance n'a pas à être exacte, seulement l'esprit.

## Objectifs

- Un mob « Gardien des Racines » + œuf d'apparition, dans le mod existant.
- Un combat où chaque point faible oblige à changer de plateforme au grappin.
- Branchement à l'arène (section 12) : apparition quand les joueurs commencent le combat, ouverture de la sortie scellée à sa mort.
- Un arc et des flèches fournis aux joueurs à l'entrée de la salle.
- Tout l'équilibrage (vie, dégâts, durées) dans `config/grappin-common.toml`.

## Non-objectifs

- Pas de coffre, de butin spécial ni de musique.
- Pas de colonnes ni de pilier construits pour le golem (il émerge de la lave).
- Pas de nouvelles armes : l'arc suffit.

## Anti-objectifs (ce qui serait un échec même si ça « marche »)

- Un point faible qu'on peut toucher depuis la plateforme où l'on se tient déjà sans jamais utiliser le grappin.
- Un boss qu'on tue en tirant n'importe où sur son corps.
- Un golem qui chevauche les plateformes ou les ancrages, ou que le grappin ne permet pas de contourner.
- Des projectiles qui détruisent la salle (blocs) : seules des dégâts aux joueurs.

## Contraintes

- NeoForge 21.1.216 inchangé, code du boss dans `atelier/donjon/arene` (package `fr.atelier.grappin`), en-tête `// [claude-build]` sur les fichiers créés, noms et commentaires en français.
- Règles du projet (CLAUDE.md) : une PR = un sujet, jamais de push sur `main`, merge décidé par Duncan, tester le gameplay en jeu, ne pas supposer d'état local.
- Le grappin ne s'accroche qu'à des blocs `grappin:ancrage` (portée 30 blocs). Tomber dans le vide de la salle ramène à la plateforme de départ sans dégâts (déjà en place).
- Le boss doit combattre à toutes les difficultés, Paisible comprise (les mondes de donjon sont en Paisible) : dégâts « sans cause », comme le Séraphin (`DamageSource` `magic()` sans entité).
- Dépendance : cette branche part de `feature/boss-seraphin` (PR #19, mod 2.7.0 et renommage du dossier). La PR du golem dépend donc de la #19.

## Approches envisagées

1. **Un seul corps et un calcul d'impact** : la flèche touche le golem, on regarde où. Simple, mais imprécis sur 14 blocs de large et il faut deviner où l'impact tombe.
2. **Des pièces de collision qui suivent le corps (choix de Luca, recommandé)** : le golem est un mob à plusieurs pièces (`PartEntity`, comme le dragon de l'Ender), une par point faible, placées sur sa surface et qui tournent avec lui. Une flèche qui touche une pièce active compte à l'endroit exact.
3. **Des entités cachées pilotées par commandes** : fragile et bricolé, écartée.

## Le combat

**Le golem.** Colossal (environ 40 blocs de haut), enfoncé dans la lave : on voit sa tête, ses épaules, son torse et ses bras ; ses jambes disparaissent dans le gouffre. Il reste au centre de la salle. Un modèle au style de la figurine : bois écorcé brun, cornes de branches, masque et plastron rouges, poing droit rouge à corne, bras gauche de bois, mousse verte.

**La cible et la rotation.** Il tourne lentement (vitesse réglable, de l'ordre d'un demi-tour en 6 s) pour faire face au joueur le plus proche, qu'il réévalue régulièrement.

**Les points faibles (5 pièces).** Masque, plastron, poing rouge, deux nœuds dans le dos. Une seule pièce est ouverte à la fois, rouge lumineuse, pendant environ 8 s, puis elle se referme et une autre s'ouvre. La pièce ouverte est toujours choisie **du côté opposé au joueur le plus proche**, c'est-à-dire dans le dos du golem par rapport à lui, pour qu'il faille changer de côté (donc se balancer) pour la viser. Une flèche qui touche une pièce ouverte inflige les pleins dégâts de l'arc multipliés par un facteur réglable ; une pièce fermée ou le reste du corps ne prend presque rien (les flèches « rebondissent » avec un son).

**Les projectiles.** Le golem lance, depuis son poing rouge, des **graines** : projectiles qui explosent à l'impact ou au bout d'un certain temps, avec dégâts de zone aux joueurs et sans détruire de blocs. Cadence et nombre augmentent en phase 2.

**Phase 2** (sous la moitié de sa vie) : annonce, aspect changé, graines plus rapides, pièces ouvertes moins longtemps.

**L'entrée du combat.** Quand les joueurs entrent sur la plateforme de départ, un titre annonce le réveil du gardien, puis il émerge de la lave en quelques secondes (invulnérable pendant l'émergence). Un arc (infini, incassable) et des flèches sont donnés à chaque joueur à l'entrée de la salle.

**La mort.** Elle appelle le tag de fonctions `grappin:golem_mort` ; le générateur du temple y branche l'ouverture de la sortie scellée (`temple_ouvrir_sortie_boss` existe déjà) et un message de victoire. La remise à zéro du temple supprime le boss, annule une invocation en attente et n'ouvre pas la sortie.

## La salle

Hall de 63 × 63 blocs (x 16–78, z 134–196, y 6–92 en coordonnées du gabarit), fond de lave (lave à y 6–7), plateforme de départ D (dessus 40, au sud) et plateformes P1 à P9 (dessus de 46 à 70), 16 ancrages `V1…V16`. Les plateformes les plus centrales (P2, P5, P4, P6) sont à moins de 4 blocs du centre naturel de la salle : **il faudra calculer l'empreinte du golem et déplacer ou retailler les plateformes concernées**, pour garder entre elles et le corps au moins 4 blocs de marge, et garder le grappin utilisable (portée 30) pour contourner le golem sur tout son tour. Luca autorise ces modifications.

## Parts du « comment » décidées avec Luca

- Le golem émerge de la lave (pas de pilier construit).
- Pièces de collision qui suivent le corps (approche 2).
- Un point faible à la fois, choisi du côté opposé au joueur le plus proche ; les parties rouges de la figurine servent de points faibles.
- Les projectiles partent du poing rouge.

## Mes calls (à vérifier)

- Nom : « Gardien des Racines ».
- 5 pièces faibles, 8 s ouvertes, vie 240 par défaut, facteur de dégâts des points faibles de 3 ; corps presque invulnérable (5 % des dégâts).
- Tour de rotation d'environ 6 s ; graines : explosion de zone de 3 blocs, dégâts 10 par défaut (à régler au test).
- Arc `bow` avec Infinity et incassable, une flèche dans l'inventaire, donné par un bloc de commande à l'entrée de la salle.
- Départ du combat : quand un joueur entre dans la zone de la plateforme de départ.
- Branche `feature/boss-golem` partie de `feature/boss-seraphin` ; PR séparée, dépendant de la #19.

## Précisions (après relecture du document par un constructeur indépendant)

- **Base de code.** La branche part de `feature/boss-seraphin` : le dossier `atelier/donjon/arene` contient déjà tout le nécessaire (infrastructure de boss, `grappin:ancrage`, `Reglages`/config TOML, rendu client, appel de fonctions à la mort via un tag). La PR #20 ne touche que les scripts du donjon 3 (`donjon_ciel*.py`), pas le mod : le golem n'a pas à l'attendre. La PR du golem dépend seulement de la #19.
- **Cotes de départ (mon choix, à ajuster par le constructeur).** Surface de la lave à y = 8 (lave aux couches 6–7 ; ce sont des coordonnées du gabarit, le monde ajoute l'origine). Golem d'environ 14 blocs de large aux épaules et 24 aux bras écartés, dont la tête est à environ y 46–54, donc au niveau de la plateforme de départ (dessus 40) et des plateformes basses ; le haut du poing et les cornes montent jusqu'à environ y 60. Centre au point libre de la salle le plus proche de (47, 165), à fixer après avoir déplacé les plateformes trop proches.
- **Règle de choix du point ouvert.** Direction horizontale du golem vers le joueur le plus proche ; une pièce n'est éligible que si sa direction depuis le centre du golem fait plus de 90° avec celle-ci. Parmi les pièces éligibles, tirage au hasard, jamais deux fois la même d'affilée. Avec plusieurs joueurs répartis autour, un autre joueur peut toucher ce point : c'est voulu (le jeu est coopératif), seul le joueur le plus proche est le « cadre » de la règle. Le joueur le plus proche est réévalué toutes les 2 s ; une pièce déjà ouverte ne change pas de côté avant de se refermer.
- **Multijoueur.** Une seule cible à la fois (la plus proche, réévaluée), vie fixe (pas de mise à l'échelle). L'arc et les flèches sont donnés à l'entrée de la salle, et redonnés à toute personne qui revient sur la plateforme de départ sans arc dans l'inventaire (retour après une chute).
- **Déclenchement et remise à zéro.** Le mod ne fait que le mob. Le générateur du temple (blocs de commande enterrés, comme pour la chute dans le gouffre) détecte l'entrée sur la plateforme de départ et appelle de nouvelles fonctions `temple_boss_annonce`, `temple_boss_invoque`, `temple_boss_mort`, `temple_boss_vaincu` ; existent déjà et sont réutilisées : `temple_ouvrir_sortie_boss`, `temple_fermer_sortie_boss`, `temple_reset`, `temple_chute_boss`. `temple_reset` supprime le boss et annule l'invocation en attente, sans ouvrir la sortie. Même schéma que le donjon n°3 (variable de score `#boss`, 0 → 3), qu'on peut copier.
- **Flèches et dégâts.** Les flèches vanilla (et celles de l'arc donné) heurtent les `PartEntity` par la collision standard ; les dégâts reçus par une pièce sont transmis au boss avec le facteur de sa pièce. Le crochet du grappin ne s'accroche qu'aux blocs d'ancrage : il ne s'accroche jamais au golem, rien à prévoir.
- **Rendu.** Même méthode que le Séraphin : modèle en Java vanilla (pas de GeckoLib), texture de bois générée en Python (Pillow), rendu à l'échelle ; zone de culling élargie pour un corps de 40 blocs. Le Séraphin sert de référence de code, pas de pièce à réutiliser telle quelle.

## Décisions prises après les tests en jeu (Luca, 2026-11-14)

Elles remplacent ce qui précède quand elles le contredisent.

- **Pas d'arc donné.** Les participants doivent penser à en prendre un avant la salle du boss. Aucune commande ne donne d'arme.
- **Déclenchement.** Dès qu'un joueur arrive sur la plateforme de départ de la salle (derrière la porte G7, donc après toutes les salles précédentes du Donjon 1) : titre « Le Gardien se réveille... », **5 secondes**, puis le golem surgit de la lave, tourné vers la plateforme de départ.
- **Rotation lente.** Demi-tour en 15 s (300 ticks) ; presque figé (un dixième) tant qu'un point faible est ouvert, pour avoir le temps de passer dans son dos au grappin.
- **Le corps bloque les flèches**, il faut donc aller dans son dos ; une flèche sur le bois fait un bruit de bois et 15 % des dégâts. Les pièces sont plus grandes (4 à 7 blocs de côté) et la pièce ouverte gonfle et bat, avec un grand pilier de lumière et un message « Point faible ouvert : … » en bas de l'écran.
- **Détection.** Portée de 128 blocs : le golem est 60 blocs sous la plateforme de la sortie.
- **Persistance.** `removeWhenFarAway` faux et `requiresCustomPersistence` vrai : un monstre invoqué par `/summon` perd sinon son drapeau « persistant » et disparaît.
- **Salle.** Huit plateformes en anneau autour du golem (centre en x 47, z 166) : départ D au nord (z 134–146), sortie P8 (à 70) au sud (z 188–196), six autres à l'est et à l'ouest (détail dans `atelier/scripts/donjon_temple.py`) ; marges de 5 à 13 blocs avec ses bras ; pour chaque liaison un ancrage bas et un ancrage haut (jusqu'à y 77), deux raccourcis vers la sortie et deux ancrages très hauts (y 76) juste au-dessus de la sortie, car elle est la plus haute.
- **Visuel.** Refait plusieurs fois d'après des captures de Luca : tête en bois clair à cornes recourbées, plaque de visage sculptée (qui s'efface pour révéler le masque rouge), torse sombre à plastron rouge, bras gauche pendant, bras droit levé portant un poing rouge à corne crème, veines de sève et yeux ambre lumineux.
- **Vocabulaire.** Le donjon s'appelle « Donjon 1 » ; les noms internes `temple_*` (fonctions, générateur) restent pour ne pas casser les mondes.

## Ce qui est laissé au constructeur

- La forme exacte du modèle (cubes, textures de bois générées en Python comme le Séraphin), les dimensions précises, les coordonnées des pièces sur le corps.
- La géométrie de l'empreinte du golem et le déplacement précis des plateformes et des ancrages.
- Les valeurs de réglage finales (au test en jeu).
- Les détails d'animation (bras, poing, émergence).

## Tests

- Tests JUnit de la logique pure (choix de la pièce ouverte du côté opposé, facteur de dégâts, durées, phase).
- Compilation du mod ; générateur du temple sans erreur et vérificateur de la salle (`verif_donjon_temple.py`).
- Test en jeu par Luca avec le client du dépôt : apparition, rotation, points faibles qui s'ouvrent du bon côté, contournement au grappin, flèches, graines, phase 2, mort et sortie.
- Nouveau jar versionné (2.8.0) copié dans `atelier/exports/`.
