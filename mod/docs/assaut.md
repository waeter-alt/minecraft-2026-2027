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
   - **En ruine** (jamais reconstruit après un assaut précédent) : il reste la dernière cible, mais on le **prend** au lieu de le démolir. Les troupes marchent au centre de la ruine ; la prise avance tant qu'au moins une troupe vivante se tient à moins de 6 blocs du centre (12 blocs de hauteur de tolérance), et il faut **60 s cumulées** (`warRuinCaptureSeconds`). Si la zone se vide, la prise se met en pause sans revenir à zéro. Elle s'affiche dans la barre de chrono (« prise des ruines n % », barre rouge). Une fois complète : butin plafonné payé une fois, victoire par le chemin habituel (Influence, bouclier du perdant). La ruine n'est pas re-détruite. Le chrono de départ gagne la durée de la prise (60 s) pour que les troupes aient le temps de s'y rendre.

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

## Étendard de siège

Code : `war/SiegeBanners` (règles), `war/SiegeBannerEntity` (modèle), `client/war/SiegeBannerFx` (rayon, cercle, repère), `client/war/SiegeBannerRenderer`. Modèle Blockbench : `art/etendard/etendard_siege.bbmodel`, texture régénérable par `mod/tools/gen_etendard_texture.py`.

- **Pose** : automatique au lancement d'un assaut entre deux colonies, là où la route de guerre arrive à `siegeBannerDistance` (40) blocs de la bordure du défenseur, à 11 blocs sur le côté de la route, sur un sol plat (1 bloc de pente au plus), sec, hors de toute colonie. Sans route : sur la ligne attaquant → défenseur. Position annoncée aux deux colonies.
- **Réapparition** : tant qu'il tient, les assaillants tombés dans l'assaut réapparaissent à son pied ; une fois tombé, chez eux. Défenseur et alliés : toujours à l'hôtel de ville.
- **Capture** : défenseurs (et alliés du défenseur) dans le cercle (`siegeBannerRadius` = 6) sans assaillant : le drapeau descend, `siegeBannerCaptureSeconds` (20 s) seul, plus vite à plusieurs (plafond 3). Un assaillant (joueur ou troupe) dans le cercle gèle la capture ; un cercle vide la fait remonter lentement (3× plus lent). À 100 % : cor de raid, annonce, l'étendard disparaît. **Pas de replantation** dans le même assaut.
- **Visible** : rayon aux couleurs de l'assaillant (vu au-delà de la distance d'affichage, clignote pendant une capture), cercle lumineux au sol qui se remplit comme une jauge, repère à l'écran (icône + distance, collé au bord, caché à moins de 32 m). Rendu additif « éclair » vanilla, aucun shader à nous ni passe post-process : n'interfère pas avec le shaderpack d'un joueur.
- **Indestructible** : entité non sauvegardée, ni frappable ni poussable ; elle est recréée quand son chunk se charge.
- **Tests** (op) : `/war assaut-de <attaquant> <défenseur> [légionnaires archers]` (la guerre doit être ouverte : `/diplomatie guerre <a> <b> true`), `/war etendard <défenseur>` (état), `/war etendard <défenseur> figer <pourcent> <état 0-3>` (fige un état pour le regarder ; `-1` rend la main au jeu, état 3 l'abat).

## Verrou d'assaut : une colonie à la fois

- Quand une colonie A lance un assaut contre D, **D est réservée à A pendant 2 h de temps de jeu** (`warAssaultLockMinutes` = 120), comptées depuis ce premier assaut. A peut relancer des assauts contre D pendant la fenêtre, mais elle ne se prolonge pas.
- Les autres colonies en guerre contre D sont refusées : « D est aux prises avec A : encore N min avant de pouvoir l'attaquer. » Le refus tombe au départ de la troupe (le bouton « Lancer l'assaut » est grisé dans la Diplomatie, la raison au survol) et à l'arrivée si la fenêtre s'est fermée entre-temps.
- La fenêtre expirée, le premier qui lance un assaut prend une nouvelle fenêtre de 2 h. Le verrou tombe aussitôt que la guerre A–D se termine (paix, capitulation, reddition, chute). Il est sauvegardé (`Wars`) et survit à un redémarrage.

## Reddition imposée par le défenseur

Bouton « Exiger la reddition » de l'onglet Diplomatie, à côté de « Capituler » (deux clics). Disponible quand, dans cette guerre :

- **(a)** le défenseur a **repoussé** au moins un assaut : un assaut fini sans prise de l'hôtel de ville parce que toutes les troupes sont tombées ou que le chrono a atteint zéro (un rappel par l'attaquant ou un arrêt du serveur ne comptent pas) ; **ou**
- **(b)** l'attaquant n'a lancé **aucun assaut depuis 6 h de temps de jeu** (`warIdleSurrenderHours`), comptées depuis la fin du préavis ou la fin de son dernier assaut. Troupes en marche ou sur le terrain : pas d'inaction, donc indisponible.

Effets : la guerre se ferme comme une défaite de l'attaquant (ses troupes rentrent). Un **tribut** est pris au trésor régional de l'attaquant et versé au défenseur : une part de ce trésor (`warTributePercent`, 25 % par défaut, comme pour la capitulation), sans jamais descendre sous la réserve inviolable du butin (`warTreasuryReserve`) ; il peut donc être partiel ou nul. Le défenseur gagne l'Influence de victoire (`warVictoryInfluence`). Le **bouclier de 2 h va à l'attaquant**, qui a perdu. Bilan de guerre des deux côtés, cause « Reddition imposée ». Indisponible : bouton grisé, le survol dit la condition manquante et le temps d'inaction restant.

## Fins d'assaut

- Hôtel de ville tombé : victoire de l'attaquant.
- Toutes les troupes mortes : repoussé (compte pour la reddition imposée).
- Chrono à zéro : repoussé (idem).
- Rappel des troupes par l'attaquant, ou arrêt du serveur.

Les messages d'assaut vont aux membres connectés des deux colonies.
