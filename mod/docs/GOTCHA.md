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
  escalier ne peut pas faire les deux. On aplatit ces bosses dans le profil avant de poser les escaliers.
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

- `src/main/java/fr/kodistan/interregne/routes/RoutePath.java` — `build()`, tableau `stairs` (routes : chemin, tunnel,
  pont).
- `src/main/java/fr/kodistan/interregne/walls/WallBlocks.java` — `stairs()` (volées des montées de muraille, qui
  posent déjà l'escalier au niveau haut de chaque marche, `FACING` vers le haut) et l'application des surcharges dans
  `of()`.
