# Gardien des Racines : animations (design)

Demandé par Luca le 2026-11-17 : « utilise Blockbench pour revoir l'apparence du boss du donjon 1 », puis « j'aimerais qu'il ait des animations, comme un vrai mob ». Choix de Luca : **A** (les points faibles ne bougent pas) et **solution 1** (Blockbench + le système d'animation de Minecraft, sans mod en plus).

## Intention

Le Gardien des Racines (`grappin:golem`, boss du Donjon Suspendu et de l'ancien donjon 1, validé par Duncan) est aujourd'hui une statue : environ 70 cubes collés à la racine du modèle (`GolemModele.java`), qui pivote d'un bloc pour faire face au joueur ; seul le point faible ouvert « bat ». On veut qu'il ait l'air vivant, comme un vrai mob, et que son modèle et ses animations se voient et se retouchent dans Blockbench.

## Objectifs

Sept animations, toutes jouées côté client :

| Animation | Quand | Durée (à régler) |
|---|---|---|
| **Repos** | en boucle, tout le temps | 4 s |
| **Regard** | la tête suit le joueur visé (lacet et tangage limités) | continu |
| **Émergence** | pendant la sortie de la lave (les 100 ticks d'`emergence`) : se redresse, rugit | 5 s |
| **Lancer** | quand il lance une graine (`GolemComportement.Lancer`) : prise d'élan, détente | ~1,5 s |
| **Douleur** | quand un point faible ouvert est touché : crispation, tête en arrière | ~0,75 s |
| **Rage** | au passage en phase 2 (`DEUXIEME_PHASE`) : se cabre, bras écartés, rugit | ~2,5 s |
| **Mort** | à la mort : s'affaisse et sombre dans la lave | 2 s |

- Le modèle est reconstruit dans Blockbench **découpé en parties articulées** (os) : torse, tête, mâchoire, chaque bras et chaque main, cornes, branches du dos, mousse, lianes.
- Le fichier Blockbench (`.bbmodel`) est **la source** : modèle et animations s'y voient et s'y retouchent ; un script le traduit en Java pour le jeu.
- **Au repos, la silhouette est celle d'aujourd'hui** : mêmes cubes, mêmes couleurs, même texture (`textures/entity/golem.png`, `atelier/scripts/texture_golem.py`).

## Hors sujet

- Les règles du combat : vie, dégâts, ouverture des points faibles, rythme des graines, émergence, phase 2, étiquette `grappin:golem_mort` — `Golem.java` et `GolemComportement.java` ne changent pas dans leur logique (on y ajoute seulement des signaux pour déclencher les animations).
- Les boîtes de collision des points faibles (`Golem.AZIMUTS`, `RAYONS`, `HAUTEURS`, `TAILLES`, `FACTEUR_LARGEUR`).
- Une nouvelle texture ou une nouvelle silhouette (ce sera un autre chantier si Luca le veut).
- GeckoLib ou tout autre mod.

## À éviter (échec même si « ça marche »)

- **Un point faible qui ne coïncide plus avec sa boîte de collision**, même un instant : les 5 bourgeons, leurs couvercles d'écorce et les branches qui les portent sont rattachés à un os **qui ne bouge jamais**.
- Une animation qui **cache** un point faible ouvert (un bras qui passe devant le plastron, par exemple).
- Des parties qui se décollent visiblement pendant un mouvement (trou entre le torse et la tête, entre le bras et l'épaule).
- Un mouvement si ample qu'il donne le mal de mer au joueur sur la rampe (le Gardien fait plus de 50 blocs de haut) : on reste lent et lourd.
- Perdre ce qui marche aujourd'hui : yeux, veines et bourgeons toujours lumineux ; teinte rougeoyante en phase 2 ; couvercle qui s'efface et cœur qui bat sur le point faible ouvert ; lumière minimale pendant l'émergence.

## Contraintes

- Minecraft 1.21.1, NeoForge 21.1.216 (figé). Animations par le système intégré (`HierarchicalModel`, `AnimationDefinition`, `AnimationState`, `KeyframeAnimations`, celui du Warden).
- Le Gardien existe en deux copies tenues identiques : le mod `grappin` autonome (`atelier/donjon/arene`) et sa version intégrée à `mod/` (branche `feature/objets-dans-mod`).
- Le comportement du boss a été validé par Duncan : rien de ce qu'on change ne doit modifier le combat.

## Approches considérées

1. **Blockbench + animations intégrées à Minecraft** : modèle découpé en os, animations faites sur une ligne de temps, traduites en Java ; aucun mod en plus ; Luca peut tout voir et retoucher. **Retenue par Luca.**
2. **Blockbench + GeckoLib** : plus confortable pour animer, mais un mod de plus pour tout le projet et tous les joueurs, une version compatible avec NeoForge 21.1.216 à trouver, et une décision qui revient à Duncan.
3. **Animations écrites dans le code** (balancements calculés) : rapide pour respirer, très limité pour l'émergence ou la mort, rien à voir ni retoucher dans Blockbench. Ne répond pas à la demande.

## Décidé avec Luca

- Les points faibles ne bougent pas (A) ; tout le reste s'anime autour.
- Solution 1.

## Mes choix (à corriger si besoin)

- **Où** : branche `feature/golem-animations` (worktree `D:\PROJET\wt-golem-anim`), empilée sur `feature/grappin-apparence` pour que le même jar d'essai contienne le grappin 3.1.0 et le Gardien animé ; jar `grappin-3.2.0.jar`. Report dans `mod/` ensuite, comme pour le grappin.
- **Chaîne de fabrication** : (1) un script reprend une fois pour toutes la géométrie actuelle de `GolemModele.java` et écrit `atelier/modeles/golem/golem.bbmodel`, découpé en os, avec des animations de départ ; (2) un second script lit le `.bbmodel` (y compris après des retouches de Luca dans Blockbench) et génère le Java du modèle et des animations. On ne passe pas par l'export Java de Blockbench : notre script garde les rendus lumineux, les couvercles des points faibles et la teinte de phase 2, que l'export ignore.
- **Le lancer** : la graine part toujours du poing droit levé (logique inchangée), qui porte un point faible et ne bouge donc pas. L'animation est celle du reste du corps : il se ramasse, puis « tire » en rejetant les épaules et la tête en arrière, le bras gauche balançant en contrepoids, comme si le poing crachait la graine.
- **Le regard** est calculé (pas une animation de Blockbench) : la tête tourne vers le joueur, au plus 35° de lacet et 20° de tangage. Le masque reste fixe, puisque c'est un bourgeon au bout d'une branche, devant la tête.
- **La mort dure 2 s au lieu de 1 s** (Minecraft retire un mob mort au bout de 20 ticks ; on passe à 40 pour que l'affaissement se voie). Seul effet sur le jeu : le corps reste une seconde de plus. L'étiquette de mort part toujours au même moment.
- **Signaux** : les animations ponctuelles (lancer, douleur, rage, mort, émergence) sont déclenchées par des « événements d'entité » que le serveur envoie au client (`level().broadcastEntityEvent`), sans rien changer à ce qui se passe.

## Validation

1. Après la reconstruction en os : planche d'aperçu « avant / après » au repos (même silhouette), et le `.bbmodel` ouvert dans Blockbench.
2. Après les animations : images clés de chaque animation (début, milieu, fin) sur une planche, et les animations visibles dans Blockbench.
3. Tests automatiques : les 5 bourgeons, leurs couvercles et leurs branches sont dans l'os fixe et à leur place exacte ; aucune animation ne touche l'os fixe ; chaque animation a une durée ; le Java généré compile.
4. En jeu (client de test, monde « Donjon Suspendu ») : émergence, repos, regard, lancer, douleur sur chaque point faible, rage, mort ; points faibles toujours frappables depuis la rampe comme avant.

## Laissé à celui qui construit

Le découpage exact en os, les pivots, les angles et le rythme des animations, tant que les objectifs et les « à éviter » sont respectés et que Luca valide les aperçus.
