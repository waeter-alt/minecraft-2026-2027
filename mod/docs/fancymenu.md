# Menus du jeu avec FancyMenu

L'écran titre (et plus tard l'écran de chargement, les autres menus) est habillé avec **FancyMenu** (+ ses dépendances Konkrete et Melody), côté client uniquement. On ne code pas d'écran Java pour ça : un layout FancyMenu est un simple fichier texte, versionné dans le repo.

Réflexe avant de coder un écran ou un habillage : **vérifier s'il existe déjà un mod qui le fait** (FancyMenu pour les menus, Drippy Loading Screen pour l'écran de chargement…).

## Où sont les fichiers

| Fichier | Rôle |
| --- | --- |
| `config/fancymenu/customizablemenus.txt` | Liste des écrans que FancyMenu a le droit de modifier. **Un écran absent de cette liste ignore son layout**, sans erreur. |
| `config/fancymenu/customization/*.txt` | Les layouts (un par écran). Ex. `kodistan_title.txt` pour `TitleScreen`. |
| `config/fancymenu/options.txt` | Réglages globaux : l'overlay d'édition et l'écran d'accueil de FancyMenu sont coupés. |

Ces fichiers existent en **deux copies identiques** à garder synchronisées :
- `mod/env/config/fancymenu/` : notre environnement de dev (installé par `./gradlew syncEnv`) ;
- `launcher/pack/config/fancymenu/` : ce que reçoivent les joueurs.

Les autres fichiers que FancyMenu génère tout seul (`ui_themes/`, `legacy_checklist.txt`, `custom_gui_screens.txt`, `user_variables.db`) ne se versionnent pas.

## Modifier un menu

1. Édite le layout dans `mod/run/client/config/fancymenu/customization/` (ou dans l'éditeur en jeu, en remettant localement `show_customization_overlay = 'true'` dans `options.txt` ; ne pas versionner ce réglage).
2. **FancyMenu ne relit les layouts qu'au démarrage** : ferme et relance le client pour voir le changement.
3. Capture avec UiShot, `screen=menu` (voir `outils-de-test.md`), aux tailles `1553x1011@4,1553x1011@2`, et regarde les images.
4. Recopie les fichiers modifiés dans `mod/env/config/fancymenu/` **et** `launcher/pack/config/fancymenu/`.
5. Recalcule l'index du pack : `powershell -ExecutionPolicy Bypass -File launcher\scripts\refresh-index.ps1`. Attention : un config du pack écrase la copie du joueur à chaque changement de hash.

## Pièges de syntaxe (texte `text_v2` avec markdown)

- Centrer : un **bloc** `^^^` sur sa propre ligne, avec `%n%` comme saut de ligne dans le fichier : `^^^%n%KODISTAN%n%^^^`. Le `^^^texte^^^` sur une seule ligne s'affiche tel quel.
- Aligner à droite : pareil avec `|||`.
- `%!!...%` est la syntaxe des **polices**, pas du centrage.
- Ancre `top-centered` + `x = -largeur/2` pour un bloc centré. Garde des largeurs raisonnables (300) : à l'échelle GUI 4, une boîte de 400 sort de l'écran.

## État actuel

- Écran titre : fond animé `assets/kodistan/ecran_titre.afma` (le coffre de la taverne s'ouvre une fois, puis boucle sur l'intérieur du coffre avec le logo « Surcharge arcanique » et le sous-titre « LA COURONNE VACANTE »). Le logo est dans l'animation, pas un élément du layout ; les boutons (Solo, Multijoueur, Mods, Options, Quitter, langue, accessibilité) sont ancrés au centre, sous le logo. Logo Minecraft, splash et Realms masqués. Fabrication et régénération : `art/menu-titre/README.md`.
- Menu pause : vanilla.
- Pas de fond vidéo : FancyMenu 3.10 l'exige via le mod Watermedia. L'AFMA (format d'animation de FancyMenu, intro jouée une fois + boucle) est natif.
