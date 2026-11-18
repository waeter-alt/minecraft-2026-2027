# Grappin : nouvelle apparence (design court)

Demandé par Luca le 2026-11-17 : « juste l'esthétique », pas d'idée précise, « rajouter des détails et améliorer son apparence ». Option B retenue par Luca.

## Intention

Aujourd'hui tout le grappin repose sur un seul dessin plat de 16 × 16 (manche en bois, bout de corde, crochet gris) : l'icône, l'objet en main et même le crochet lancé (manche compris, toujours tourné vers la caméra). La corde est un trait marron d'un pixel. On veut un objet qui ait l'air d'un vrai grappin là où on le regarde le plus : en main et pendant le tir.

## Objectifs

- **En main (1re et 3e personne)** : un objet en 3D fait dans Blockbench. Poignée de lanceur en bois cerclée de métal, bobine de corde sur le côté, crochet à trois griffes en fer à l'avant.
- **Dans l'inventaire** : une icône 2D redessinée avec plus de détails, dans le même esprit que le modèle 3D (comme le trident de Minecraft, plat dans l'inventaire, en volume en main).
- **Crochet lancé** : seulement la tête du crochet, en 3D, orientée dans le sens de son vol, griffes vers l'avant, au lieu de l'icône entière face à la caméra.
- **Corde** : une corde tressée (bande épaisse à deux tons de brun en alternance, comme une laisse), avec un léger mou pendant le vol du crochet et tendue pendant la traction.
- Le fichier source Blockbench (`.bbmodel`) est livré pour que Luca puisse l'ouvrir et retoucher.

## Hors sujet

- Le fonctionnement ne change pas : portée, vitesse, magnétisme, traction, touches, ancrages.
- Pas d'animations (griffes qui s'ouvrent, bobine qui tourne) : c'était l'option C, possible plus tard par-dessus.
- Le bloc d'ancrage, la voile et la paravoile gardent leur apparence.

## À éviter (échec même si « ça marche »)

- Un objet en main si gros qu'il cache la visée ou le centre de l'écran.
- Une corde si épaisse ou si foncée qu'elle gêne pour viser l'ancrage suivant.
- Un style qui jure avec Minecraft (textures lisses ou trop détaillées) : on reste en gros pixels.
- Toucher à la boîte de collision ou au comportement du crochet.

## Contraintes

- Minecraft 1.21.1, NeoForge 21.1.216 (figé). Aucune nouvelle dépendance (pas de GeckoLib).
- Le grappin existe en deux copies tenues identiques : le mod `grappin` autonome (`atelier/donjon/arene`, d'où vient le jar testé par le raccourci « Client de test Kodistan ») et sa version intégrée à `mod/` (branche `feature/objets-dans-mod`). Les deux doivent recevoir la même apparence.

## Approches considérées

- **A. 2D soignée** : icône redessinée, crochet seul en vol, corde épaisse. Rapide, mais l'objet reste plat en main.
- **B. 3D en main et en vol, icône 2D, corde tressée** : change vraiment l'impression en main et pendant le tir, sans toucher au fonctionnement. **Retenue par Luca.**
- **C. B + animations** : plus spectaculaire, mais du code de rendu en plus et le plus de risques de défauts visibles seulement en jeu. Reportée.

## Mes choix (à corriger si besoin)

- **Où** : travail sur la branche `feature/grappin-apparence` (worktree `D:\PROJET\wt-grappin-apparence`), partie de `feature/grappin-traction-simple` (grappin 3.0.4) ; jar `grappin-3.1.0.jar` dans `atelier/exports` pour l'essai avec le raccourci de test ; la même chose est ensuite reportée dans `feature/objets-dans-mod`. La PR de cette branche ne porte que l'apparence.
- **Comment** : modèle d'objet en JSON (format « Java Block/Item » de Blockbench) ; l'icône 2D en inventaire via le chargeur `neoforge:separate_transforms` ; le crochet en vol dessiné à partir d'un modèle 3D à part ; la corde dessinée en bande par le code de rendu existant du crochet.
- **Couleurs** : bois brun foncé, métal fer (gris, reflets clairs), corde beige/brun, accord avec l'icône actuelle.
- **Fichiers sources** : `.bbmodel` rangés dans `atelier/modeles/grappin/`.

## Validation

1. Avant le mod : des images d'aperçu du modèle (plusieurs angles) et de la nouvelle icône, montrées à Luca pour qu'il réagisse.
2. Dans le jeu (client de test du dépôt) : 1re personne, 3e personne, inventaire, crochet en vol, corde pendant le vol puis pendant la traction, de jour et dans un endroit sombre du Donjon Suspendu.

## Laissé à celui qui construit

Les proportions exactes, le nombre de cubes, la taille de la texture (16 ou 32 pixels), la façon précise de dessiner le mou de la corde, tant que les objectifs et les « à éviter » sont respectés.
