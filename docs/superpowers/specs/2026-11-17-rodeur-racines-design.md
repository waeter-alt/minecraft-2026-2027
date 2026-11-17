# Le Rôdeur des Racines, mini-boss du sous-sol 1 (design)

Demandé par Luca le 2026-11-17 : « modéliser le mini-boss, créer un monstre organique en rapport avec la DA du donjon », « utilise bien Blockbench pour le créer ». Choix de Luca : idée 1 (le Rôdeur des Racines), combat proposé validé tel quel, solution 1 (fichier Blockbench écrit par script, vérifié et montré dans Blockbench).

## Intention

Le mini-boss du Donjon Suspendu (le « M » du croquis de Luca, au sol de la grande salle du sous-sol 1) est un vindicateur provisoire de 60 points de vie, « Gardien de la salle ». On le remplace par un vrai monstre du donjon, fait des mêmes matières que le boss final (le Gardien des Racines) : il l'annonce, en plus petit et plus rapide, et son combat se sert de la salle et de ses grands piliers.

## Objectifs

- **Allure** : bête à quatre pattes, entre loup et sanglier, environ 4 blocs de long et 2,5 de haut ; corps de racines tressées, dos de mousse avec quelques lianes à baies lumineuses qui pendent, gueule d'écorce à crocs crème, deux yeux ambre lumineux, crête de racines pointues le long de l'échine.
- **Combat**, au sol, sans grappin, environ 150 points de vie (à régler à l'essai) :
  - **Réveil** : il sort d'un tapis de mousse en s'ébrouant et en grognant (il apparaît quand un joueur arrive à moins de 22 blocs, comme le vindicateur aujourd'hui).
  - **Morsure** : attaque rapide au corps à corps.
  - **Charge** : il gratte le sol (environ 1 s, le temps de le voir venir), baisse la tête et fonce en ligne droite vers le joueur visé ; on l'esquive en s'écartant.
  - **Sonné** : si sa charge s'arrête contre un mur ou un pilier, il reste **sonné 3 s** et prend **deux fois plus de dégâts** pendant ce temps.
  - **Mort** : il s'effondre et ses racines se défont ; la cinématique du coffre du grappin part comme aujourd'hui.
- **Animations** (Blockbench, système intégré à Minecraft comme le Gardien) : repos (respire, renifle), marche/course, morsure, grattage, charge, sonné, réveil, mort.
- **Blockbench au centre** : le `.bbmodel` est écrit par script puis **ouvert dans Blockbench** sur le PC de Luca pour le vérifier ; les images montrées à Luca sont des **captures de Blockbench** (modèle texturé, animations jouées). Le fichier devient la source : Luca peut le retoucher, le jeu se régénère depuis lui.

## Hors sujet

- Le reste du sous-sol 1 (énigme musicale, coffres jaunes, salle de l'énigme), le grappin, le Gardien des Racines.
- Une nouvelle texture dessinée à la main (voir mes choix).
- La roulette des coffres (autre chantier).

## À éviter

- Un monstre qui ne ressemble pas au donjon (couleurs ou matières étrangères au Gardien et aux grottes).
- Une charge impossible à esquiver ou à voir venir, ou qui le coince dans un mur sans fin.
- Un mini-boss qui sort de la grande salle, se perd dans l'escalier ou ne meurt jamais (la cinématique du coffre du grappin doit toujours partir).
- Un modèle carré et figé : il doit bouger en permanence.

## Contraintes

- Minecraft 1.21.1, NeoForge 21.1.216 figé ; aucune nouvelle dépendance.
- Le monstre vit dans le mod `grappin` (`atelier/donjon/arene`, id `grappin:rodeur`) ; son remplacement du vindicateur se fait dans les fonctions du donjon (`atelier/scripts/donjon_suspendu.py`, branche `feature/donjon-suspendu`) : il garde l'étiquette `suspendu_ss1m`, dont l'absence déclenche déjà `suspendu_ss1_boss_fin` (coffre et cinématique), et la remise à zéro (`kill @e[tag=suspendu_ss1m]`) marche sans changement.

## Approches considérées (comment utiliser Blockbench)

1. **Fichier Blockbench écrit par script, puis ouvert et vérifié dans Blockbench** : précis, retouchable, captures faites dans Blockbench. **Retenue par Luca.**
2. Construire le modèle à la souris dans Blockbench en pilotant l'écran : des centaines de clics, beaucoup d'erreurs possibles, même fichier au bout.
3. Un script lancé dans Blockbench (plugin) : il faut quand même piloter l'interface, rien de plus que la 1.

## Mes choix (à corriger si besoin)

- **Texture** : le Rôdeur reprend la texture du Gardien (`textures/entity/golem.png` : écorce sombre, bois clair, mousse, crème, ambre). Mêmes couleurs garanties, pas de dessin à faire ; une texture propre pourra venir plus tard (Luca dans Blockbench).
- **Chaîne** : mêmes outils que le Gardien : un script `rodeur_vers_bbmodel.py` écrit le `.bbmodel` une fois, `bbmodel_vers_java.py` (rendu générique) génère `RodeurGeometrie` et `RodeurAnimations`.
- **Branche** : `feature/rodeur-racines` (worktree `D:\PROJET\wt-rodeur`), partie de `feature/golem-animations` ; jar d'essai `grappin-3.4.0.jar`. Le changement des fonctions du donjon (`summon grappin:rodeur` au lieu du vindicateur, plus les lignes de vie) se fait sur `feature/donjon-suspendu`, dans un commit à part.
- **Valeurs de départ** : 150 PV, morsure 8 dégâts, vitesse de marche 0,3, charge à 0,9 bloc/tick sur 24 blocs au plus, grattage 20 ticks, sonné 60 ticks, une charge au plus toutes les 6 s ; résistance au recul. Toutes réglables dans `Reglages` comme pour le Gardien.
- **Sonné contre n'importe quel bloc** (mur ou pilier) : plus simple à comprendre que « seulement les piliers ».

## Validation

1. Captures **dans Blockbench** du modèle (face, profil, dos) et de chaque animation, envoyées à Luca avant de toucher au jeu.
2. Tests automatiques : structure du `.bbmodel` (os, animations, durées) ; logique pure du combat (charge, sonné, multiplicateur de dégâts, délai entre charges) en JUnit.
3. En jeu (Donjon Suspendu, grande salle du sous-sol 1) : réveil, morsure, charge esquivée, charge contre un pilier puis sonné, mort puis cinématique du coffre du grappin, remise à zéro.
