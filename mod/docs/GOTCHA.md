# Pièges connus

À lire avant de toucher à du code qui pose des blocs à la main (routes, murailles, structures générées).

## Escaliers : où les poser, et dans quel sens

Ce bug a été « corrigé » deux fois à tort avant d'être compris. La règle ci-dessous est vérifiée dans le code
vanilla (`StairBlock`, NeoForge 21.1.216), pas devinée.

### Ce qu'est vraiment un escalier (HALF = BOTTOM, SHAPE = STRAIGHT)

Un escalier posé au niveau `Y` occupe :

- une **dalle pleine** sur toute la case, de `Y` à `Y + 0,5` ;
- une **demi-case haute** de `Y + 0,5` à `Y + 1`, du côté indiqué par `FACING`.

Donc :

- son point le plus haut (`Y + 1`) est **le même qu'un bloc plein posé en `Y`** ;
- `FACING` = **le côté où se trouve la partie haute = le sens de la montée**. Un escalier `FACING=north` se monte en
  marchant vers le nord. (C'est aussi ce que donne la pose à la main : `FACING` = la direction où regarde le joueur
  qui le pose en montant.)

### La règle

Entre deux cases voisines de niveaux `L` et `L + 1`, l'escalier va :

1. sur la case **la plus haute**, à **sa** hauteur (`L + 1`) — jamais sur la case basse ;
2. avec `FACING` tourné **vers le haut**, c'est-à-dire à l'opposé de la case basse.

Ça vaut dans les deux sens de parcours. En **montée**, la case haute est la case d'arrivée ; en **descente**, c'est la
case de **départ** (celle d'avant la marche), et `FACING` pointe vers l'arrière du sens de marche.

### Pourquoi c'est contre-intuitif

On pense « la marche est là où l'on change de niveau » et on pose l'escalier sur la case où le niveau vient de
changer, en le tournant vers le haut. En montée ça tombe juste par hasard (la case qui vient de changer est la case
haute). En descente, la case qui vient de changer est la case **basse** : l'escalier se retrouve un bloc trop bas.
Résultat : une vraie marche pleine d'un bloc en haut de la descente, puis une bosse d'un demi-bloc en bas — toute
la volée est décalée d'un cran vers le bas (« marches trop basses »).

### Exemple

Route vers l'est, niveaux de plancher `[A=66, B=65, C=64]` (descente) :

| case | bon placement | ancien placement (faux) |
|------|---------------|-------------------------|
| A    | escalier en Y=66, `FACING=west` | chemin en 66 |
| B    | escalier en Y=65, `FACING=west` | escalier en 65, `FACING=west` |
| C    | chemin en 64 | escalier en 64, `FACING=west` |

Hauteurs de marche avec le bon placement : 67 → 66,5 → 66 → 65,5 → 65. Avec l'ancien : 67 → **66** (chute d'un bloc
plein) → 65,5 → 65 → 64,5 → **65** (bosse en bas).

En montée vers l'est `[64, 65, 66]` : escaliers sur B (Y=65) et C (Y=66), tous deux `FACING=east`.

### Autres pièges liés

- **Bosse d'une seule case** (`L, L+1, L`) : la case du milieu devrait monter d'un côté et descendre de l'autre, un
  escalier ne peut pas faire les deux. Pour les routes, le profil ne peut pas en produire : `ProfileSolver` interdit
  de redescendre moins de deux cases après une montée (et l'inverse).
- **Direction à prendre** : celle de la transition entre les deux cases concernées (case basse → case haute), pas la
  direction de la case suivante — elles diffèrent dans un virage.
- **Virages** : deux escaliers voisins à angle droit prennent tout seuls une forme `INNER`/`OUTER` (`updateShape`
  vanilla). Normal, à ne pas « corriger ».
- `dirt_path` fait 15/16 de haut : la jonction chemin/escalier a un écart d'1/16, invisible, ce n'est pas un bug.
- **Murailles — un bloc posé plusieurs fois au même endroit** : dans `WallBlocks.of()`, `clear()` ajoute une étape
  « air » avant le bloc du corps du mur partout où il y avait de l'herbe, une fleur ou un arbre. Les escaliers des
  montées (`wayUp`) remplaçaient seulement la **première** étape à cette position (l'air), le bloc du mur passait
  ensuite et rebouchait la marche ou l'espace dégagé au-dessus. Toute surcharge doit remplacer **toutes** les étapes
  d'une position.

### Fichiers concernés

- `src/main/java/fr/kodistan/interregne/routes/RouteBuilder.java` — constructeur, tableau `stairs` (routes : chemin,
  tunnel, pont).
- `src/main/java/fr/kodistan/interregne/walls/WallBlocks.java` — `stairs()` (volées des montées de muraille, qui
  posent déjà l'escalier au niveau haut de chaque marche, `FACING` vers le haut) et l'application des surcharges dans
  `of()`.


## Routes : lire le monde sans le charger, sans bloquer le serveur

Rencontrés en réécrivant le tracé des routes (`routes/`, `routes/plan/`). Chacun a coûté un essai en jeu.

- **`level.getHeight(type, x, z)` sur un chunk non chargé répond le bas du monde** (-64), sans erreur ni chargement.
  Une route dont une extrémité est loin de tout joueur se retrouvait avec un sol à -65 et « aucun tracé possible ».
  Passer par le chunk : `level.getChunk(x >> 4, z >> 4).getHeight(type, x & 15, z & 15)` (qui, lui, le charge), ou
  `RouteProbe.of`.
- **`ServerChunkCache.getChunkFuture` appelé sur le thread serveur attend le chunk** (`managedBlock`) : ce n'est une
  vraie promesse que demandé depuis un autre thread. Pour charger sans bloquer le tick : poser un ticket
  (`addRegionTicket`), puis `CompletableFuture.supplyAsync(() -> chunks.getChunkFuture(...), Util.backgroundExecutor())
  .thenCompose(f -> f)`, et relever les résultats au tick suivant. Sans ticket à soi, le chunk est relâché avant la fin.
- **Charger un chunk coûte ~25 ms de thread serveur** sur ce modpack (désérialisation), qu'il soit généré ou non :
  arpenter une bande de 1 400 chunks en les chargeant prenait 39 s. `RouteSurvey` lit donc les chunks déjà sauvés
  directement dans leur fichier de région (`chunks.chunkMap.read(pos)` + `RouteProbe.read`, palettes décodées sur des
  threads de travail) : 1 400 chunks en moins d'une seconde, sans rien charger. Seuls les chunks jamais générés
  passent par le vrai chargement (il faut bien les générer).
- **Ne jamais poser une route d'après un relevé ancien** : `RouteBuilder.slice` relit le monde au moment de poser
  chaque case. C'est ce qui permet au chantier de ne garder chargés que les chunks autour de ses deux fronts, et
  c'est ce qui évite qu'un chunk généré entre-temps (arbres, structures) ne soit ignoré.
- **Feuilles en l'air après un abattage** : retirer les feuilles « connectées » à un arbre déshabille les arbres
  voisins (les houppiers d'une forêt se touchent) ; n'en retirer qu'à courte distance en laisse flotter. La règle
  juste est celle du jeu : une feuille vit à 6 feuilles au plus d'un tronc. `TreeFelling.orphans` la recalcule tout de
  suite pour l'ensemble des troncs abattus d'un coup.
- **`dirt_path` redevient de la terre sous un bloc plein** (une barrière compte) : toujours dégager au-dessus avant de
  poser le chemin, et ne pas poser de garde-corps sur du chemin.

### Régler ou vérifier le tracé sans lancer le jeu

Le planificateur (`routes/plan/`) est du Java pur, sans aucune classe Minecraft : il se teste en quelques secondes.

- `./gradlew test` : `RoutePlannerTest` (canyon → tablier de niveau d'une rive à l'autre, rivière, crête → tunnel,
  lac contourné, règles de marche).
- Sur du vrai relief : `dev/TerrainDump` écrit le relief d'une zone dans un fichier (`routedev/request.properties`
  côté serveur), `RouteBench` (dans `src/test`) trace des routes dessus et rend une image carte + profil par route.
- En jeu, sans personne au clavier : `client/dev/RouteShot` ouvre un monde, construit les routes demandées, se place
  aux points de vue que chaque route propose (ponts, tunnels, volées de marches) et capture
  (`routeshot/request.properties` côté client, détail dans la javadoc de la classe).
