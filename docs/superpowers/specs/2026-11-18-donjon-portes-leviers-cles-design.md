# Donjon Suspendu : portes, leviers et clés à l'ambiance du donjon (design)

Demandé par Luca le 2026-11-18 : « pour le donjon 1, changer l'apparence des leviers pour que ça corresponde plus à l'ambiance, également les portes changer leurs textures (créer des nouveaux blocs si nécessaire avec Blockbench) mais garde le code couleurs », puis « ensuite faudra modéliser une texture pour les clés ». Direction proposée et validée : portes de pierre gravée à runes, leviers de racine.

## Intention

Les portes à clé sont en béton coloré (lisse, « plastique ») et les leviers et les clés sont ceux de Minecraft (leviers normaux, crochets de fil renommés « Clé bleu », « Clé jaune », « Clé rouge »). Au milieu des briques moussues, des racines, des lianes et de la lave, ils font tache. On veut qu'ils appartiennent au donjon, en gardant le code couleur qui guide le joueur : bleu (porte 1), jaune (porte 2), rouge (porte 3).

## Objectifs

1. **Portes de pierre gravée à runes** (les 3 portes du rez-de-chaussée et la porte bleue du sous-sol 1) : la couleur principale d'aujourd'hui (le béton) devient une **pierre sombre sculptée, fissurée, avec de la mousse dans les joints et des veines/runes de la couleur de la porte qui brillent** ; la couleur secondaire (le motif) devient un **sceau** en pierre précieuse : saphir (bleu), ambre (jaune), rubis (rouge). Les veines et les sceaux émettent un peu de lumière. Le dessin des portes (arche, motif, ouverture, remise à zéro) ne change pas.
2. **Leviers de racine** (les 5 leviers du sous-sol 2) : un socle de pierre moussue d'où sort une racine tordue en guise de manche, une petite boule d'ambre au bout, qui s'allume quand le levier est baissé. Il se comporte exactement comme un levier de Minecraft (poser, basculer, signal, son) ; les énigmes ne changent pas.
3. **Clés** : trois objets à eux, « Clé bleue », « Clé jaune », « Clé rouge », de vieilles clés en fer forgé à l'anneau orné d'une pierre de la couleur de la porte (saphir, ambre, rubis), avec une icône dessinée et un modèle en main. Elles remplacent les crochets de fil renommés partout (coffres, ouverture des portes, remise à zéro).
4. **Blockbench** : le modèle du levier (deux positions) et celui des clés en main sont faits comme le grappin : un script écrit les modèles et des fichiers `.bbmodel`, vérifiés en les ouvrant dans Blockbench, avec des captures de Blockbench montrées à Luca avant le jeu.

## Hors sujet

- La forme des portes, des salles, les énigmes et les cinématiques.
- Les autres blocs du donjon (murs, sols, décor).

## À éviter

- Perdre le code couleur ou le rendre ambigu (une porte bleue qui paraît violette dans le noir, par exemple).
- Des textures lisses ou trop détaillées : on reste en gros pixels, comme le reste de Minecraft.
- Un levier qu'on ne voit pas dans la pénombre des grottes ou qu'on ne reconnaît pas comme un levier.
- Casser le monde existant : la remise à zéro et la pose du donjon doivent marcher avec les nouveaux blocs.

## Contraintes

- Minecraft 1.21.1, NeoForge 21.1.216 figé, aucune dépendance en plus.
- Les blocs et objets vivent dans le mod `grappin` (`atelier/donjon/arene`) ; le donjon (scripts `atelier/scripts/`) s'en sert. Le donjon a déjà besoin de grappin 3.4.0+ (Rôdeur) : il passera à la version qui apporte ces blocs, déclarée dans `mod/env/mods.txt`.
- Le dossier principal est sur `feature/donjon-suspendu` avec du travail en cours non commité (portes sans cinématique) et sans le dernier `main` : on n'y touche pas.

## Mes choix (à corriger si besoin)

- **Blocs** : `grappin:porte_bleue`, `porte_jaune`, `porte_rouge` (pierre gravée à veines de couleur, lumière 4) et `grappin:sceau_bleu`, `sceau_jaune`, `sceau_rouge` (pierre précieuse taillée, lumière 7) ; `grappin:levier_racine` (même comportement qu'un levier, lumière 6 baissé). Textures 16 × 16 dessinées par script (pixel art), modèles de bloc simples pour les portes et sceaux, modèle Blockbench pour le levier. Objets `grappin:cle_bleue`, `cle_jaune`, `cle_rouge`.
- **Pas de nouvelle mécanique** : les portes sont toujours des blocs posés et retirés par les fonctions ; on remplace seulement les noms de blocs et d'objets dans les scripts du donjon.
- **Branches** : le mod sur `feature/donjon-blocs` (worktree `D:\PROJET\wt-donjon-blocs`, partie de `feature/rodeur-racines`), jar `grappin-3.6.0.jar` ; les scripts du donjon sur une branche partie de `feature/donjon-suspendu` (sans le travail en cours), à fusionner avec elle ensuite. Pour l'essai, le générateur du donjon écrit dans le monde de test du dossier principal.

## Validation

1. Captures **dans Blockbench** du levier (levé, baissé) et des clés, plus une planche des textures des portes et sceaux, envoyées à Luca avant le jeu.
2. Tests : modèles, états de blocs et textures présents et valides (JUnit) ; tests du donjon (les portes utilisent les nouveaux blocs, les leviers et les clés sont détectés par leurs nouveaux noms, la remise à zéro les remet).
3. En jeu : chaque porte, de jour et dans le noir ; ouvrir chaque porte avec sa clé ; actionner les 5 leviers ; remise à zéro.
