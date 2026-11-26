# Assaut : règles du combat

Règles validées par Duncan le 2026-11-25. Code : `src/main/java/fr/kodistan/interregne/war/` (`WarAssaults`, `AssaultGoal`, `AssaultRangedGoal`). Les chiffres sont des valeurs de départ, à revoir à l'équilibrage (`equilibrage.md`, section « Guerre : assaut »).

## L'idée

Les défenseurs tuent, les attaquants détruisent. Un assaut est un duel entre la troupe d'assaut et la garnison du défenseur, et les bâtiments servent de score. La garnison est la défense qui tient quand les joueurs du défenseur ne sont pas connectés.

## Déroulé

1. **Approche** : les troupes apparaissent au point de ralliement (bout de la route de guerre) et marchent vers la colonie.
2. **Brèche** : la muraille, s'il y en a une, est la première cible. On peut la frapper même si des gardes sont vivants.
3. **Combat** : un garde du défenseur qui se bat (il a donné ou reçu un coup d'une troupe d'assaut dans les 10 dernières secondes) et qui se trouve à 24 blocs ou moins du bâtiment visé **fige** ce bâtiment : il ne perd plus de points de structure, mais garde ses dégâts. Un garde emmuré ou hors d'atteinte ne se bat pas, donc il ne bloque rien.
4. **Destruction** : les bâtiments tombent du plus proche au plus éloigné du point de ralliement. Quand les gardes reviennent, on repasse en phase 3.
5. **Hôtel de ville** : dernière cible, pas verrouillée. Sa chute donne la victoire immédiate.

## Qui les troupes ciblent

Par ordre de priorité :

1. les gardes du défenseur ;
2. les joueurs ennemis : membres de la colonie qui défend, ou d'une colonie en guerre ouverte contre l'attaquant ;
3. la muraille, puis les bâtiments, puis l'hôtel de ville.

- Les neutres, les alliés qui ne sont pas eux-mêmes en guerre et les civils ne sont jamais ciblés, **sauf celui qui frappe le premier**.
- **Aggro partagée** : une troupe frappée appelle les troupes de son assaut dans un rayon de 16 blocs.
- Le légionnaire va chercher ses cibles (16 blocs) et lâche une cible qui s'éloigne à plus de 32 blocs.
- L'archère cherche ses cibles à 24 blocs. Elle tire si la cible est à 16 blocs ou moins et visible, sinon elle se rapproche. Elle riposte contre qui la frappe.

## Chrono

- Départ : 8 minutes.
- Chaque bâtiment ou muraille tombé rajoute 90 secondes, chaque garde tué 30 secondes. Le temps restant ne dépasse jamais 12 minutes.
- À zéro, l'assaut est **repoussé** : les troupes survivantes rentrent aux tours.
- Le chrono s'affiche dans une barre en haut de l'écran pour les membres des deux colonies connectés.

## Garnison

- Pas d'achat de garde pendant un assaut sur sa propre colonie : la garnison qui défend est celle qu'on avait avant.
- Le plafond de la garnison reste celui des tours (inchangé).

## Fins d'assaut

- Hôtel de ville tombé : victoire de l'attaquant.
- Toutes les troupes mortes : repoussé.
- Chrono à zéro : repoussé.
- Rappel des troupes par l'attaquant, ou arrêt du serveur.

Les messages d'assaut vont aux membres connectés des deux colonies.
