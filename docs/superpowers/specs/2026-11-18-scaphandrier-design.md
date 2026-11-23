# Le Scaphandrier rouillé, mini-boss du Donjon de la Station (design)

Demandé par Luca le 2026-11-18 : un mini-boss pour le donjon 2 (la Station), fait comme le Rôdeur avec Blockbench. Choix de Luca : le Scaphandrier ; « l'animation et le design OK », mais pas d'astuce des jets de vapeur (« trop complexe ») : à la place, **le harpon coincé** (option A).

## Intention

La Station est une station de pompage abandonnée et noyée (piscine, chaufferie, turbines, machines Create), dont le boss final est le Léviathan. Le mini-boss en est l'ancien ouvrier devenu une armure de plongée vivante : humain d'origine, mécanique, très différent du Rôdeur (une bête) et du Léviathan (un serpent).

## Objectifs

- **Allure** (Blockbench) : environ 2,5 blocs de haut, massif. Gros casque rond en laiton à trois hublots, celui de face lumineux (vert pâle) ; combinaison de cuivre vert-de-grisé à rivets, collerette boulonnée ; tuyaux d'air arrachés qui pendent dans le dos ; semelles de plomb ; une grosse clé à molette dans une main, un lance-harpon à chaîne sur l'autre bras.
- **Combat**, au fond de la salle des chaudières noyée (secteur B), environ 200 points de vie :
  - **Il marche au fond**, lent et lourd (pas de nage, pas de flottaison).
  - **Coup de clé** au corps à corps, lourd, qui repousse.
  - **Harpon** : environ toutes les 8 s, il vise (cliquet sonore, environ 1 s), puis tire une chaîne en ligne droite. **Touché** : le joueur est tiré vers lui pendant 1,5 s. **Raté** (la chaîne frappe un bloc, par exemple une chaudière derrière laquelle on s'est caché) : il reste **coincé 3 s** à tirer dessus, et prend des **dégâts doublés** pendant ce temps.
  - **Hublot** : il se fissure au fil des dégâts (deux fissures visibles), puis éclate quand il est presque mort ; c'est un effet visuel, pas une règle de combat.
  - **Mort** : il tombe à genoux, un flot de bulles sort du casque, il s'effondre.
- **Animations** : repos, marche au fond, coup de clé, visée, tir, coincé (tire sur la chaîne), mort.

## Hors sujet

- Les jets de vapeur et toute autre mécanique de salle.
- La construction de la Station elle-même (une autre session) : ici le monstre et sa pose.

## À éviter

- Un harpon impossible à esquiver ou à voir venir.
- Un monstre qui flotte, nage ou sort de l'eau comme un noyé.
- Un mini-boss qui ne meurt jamais (la suite du donjon ne doit pas en dépendre sans repli).

## Contraintes

- Minecraft 1.21.1, NeoForge 21.1.216 figé, aucune dépendance en plus.
- Mod `donjon` 4.0.0 (`atelier/donjon/arene`, mod_id `donjon`, paquet Java `fr.atelier.grappin`, ressources dans `assets/donjon`) : branche `feature/scaphandrier` (worktree `D:\PROJET\wt-scaphandrier`), partie de `feature/grappin-casier`. Jar d'essai `donjon-4.1.0.jar`.
- La pose dans la salle des chaudières passe par `feature/donjon-station` (session à part) : on lui donne un point d'apparition et une étiquette, on ne touche pas à ses fichiers sans coordination.

## Approches (déjà tranchées)

Même chaîne que le Rôdeur : script → `.bbmodel` vérifié dans Blockbench → Java généré (`bbmodel_vers_java.py --nom Scaphandrier`), entité `Monster` avec états synchronisés et logique pure testée. Le harpon est un projectile à chaîne dessiné comme la corde du grappin.

## Mes choix (à corriger si besoin)

- Identifiant `donjon:scaphandrier`, texture à lui (laiton, cuivre vert-de-gris, plomb, caoutchouc), 128 × 128.
- Valeurs de départ (config) : 200 PV, clé 9 dégâts, vitesse 0,18 au fond, harpon toutes les 160 ticks, visée 20 ticks, portée 16 blocs, traction 30 ticks à 0,6 bloc/tick, coincé 60 ticks, dégâts ×2 coincé.
- Gravité « lestée » : il ignore la poussée de l'eau (il tombe au fond et y marche).
- Il garde l'étiquette donnée par le datapack qui le fait apparaître ; sa mort est détectée par son absence, comme le Rôdeur.

## Validation

Captures Blockbench (modèle, chaque animation) avant le jeu ; tests (structure du modèle, règles du harpon et du « coincé ») ; essai en jeu dans un bassin d'essai (œuf d'apparition) avant la pose dans la Station.
