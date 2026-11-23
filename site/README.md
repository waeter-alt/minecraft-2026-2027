# Site du launcher (one page)

Page unique pour télécharger le launcher du serveur Kodistan. On arrive sur une table de taverne vue du dessus (style Minecraft : blocs, textures pixel-art), on ouvre le coffre à mécanismes, la caméra plonge dedans et un parchemin se déroule avec le bouton de téléchargement et les 4 étapes d'installation.

**Le coffre est le design emblématique du projet** : Duncan veut le réutiliser dans les donjons. Toute retouche de sa forme ou de son animation doit rester compatible avec cette idée (même silhouette, mêmes mécanismes : loquets, engrenages, sceau, deux battants).

## Ce qu'il faut pour travailler dessus

Rien à installer à part ce que le repo demande déjà :

- **Python** (déjà requis par `mod/tools/`) pour servir le dossier en local ;
- **un navigateur** récent (WebGL).

Pas de build, pas de `npm` : trois fichiers statiques. Three.js r170 est chargé depuis jsDelivr via l'import map de `index.html`. La police (Pixelify Sans) vient de Google Fonts. Aucune image : toutes les textures sont dessinées en code, au pixel près.

## Lancer l'aperçu

Depuis la racine du repo :

```bash
python -m http.server 8080 --directory site
```

puis ouvrir http://localhost:8080. Ouvrir `index.html` en double-clic ne marche pas : les modules ES exigent un serveur.

Avec Claude Code (app de bureau) : la config `site-launcher` de `.claude/launch.json` (versionnée) fait la même chose depuis le panneau Browser.

## Outils de polish

| URL | Effet |
| --- | --- |
| `?t=1.8` | fige l'animation d'ouverture à cet instant (en secondes, de 0 à 3.7) ; idéal pour régler une étape image par image |
| `?open` | ouvre directement le coffre, parchemin déplié |
| (aucun) | état normal : coffre fermé |

Repères de la chronologie (`applyTimeline` et `updateCamera` dans `main.js`) :

| Secondes | Ce qui se passe |
| --- | --- |
| 0 → 0.5 | les loquets se rétractent |
| 0.35 → 1.35 | le sceau tourne |
| 1.1 → 1.6 | le sceau s'envole (gerbe d'étincelles à 1.45) |
| 1.35 → 2.5 | les battants s'ouvrent |
| 1.5 → 2.4 | la lumière intérieure monte |
| 2.0 → 3.7 | la caméra plonge dans le coffre |
| 3.7 | le parchemin se déroule (CSS) |

La fermeture (bouton ou Échap) rejoue la même chronologie à l'envers, 1,7 fois plus vite.

## Où toucher quoi

- **`main.js`**
  - En haut : `DOWNLOAD_URL`. **Encore vide** : tant qu'il l'est, le bouton affiche « demande le zip aux organisateurs ». Y mettre l'adresse du zip du launcher.
  - `PAL` : toutes les couleurs des textures (table, coffre, bougie…).
  - `C` / `pixelArt` : les motifs pixel-art (sceau, joyau, flamme…).
  - `vbox()` : la brique de base, une boîte façon Minecraft dont les UV suivent l'échelle du monde (les pixels gardent la même taille partout).
  - Blocs commentés `Table`, `Coffre`, `Couvercle`, `Sceau central`, `Intérieur`, `Bougie`, `Chope`, `Pièces, dés, carte`, `Lumières`, `Particules` : un élément de la scène par bloc. `CH` donne les dimensions du coffre.
  - `LOOK`, `VIEW_DIR`, `DIVE_*`, `BASE_FOV` et `resize()` : cadrage de la caméra (vue de dessus, plongée, recadrage en portrait).
- **`style.css`** : titre, boutons façon Minecraft (`.mc-btn`), parchemin, mobile sous 520 px, `prefers-reduced-motion`.
- **`index.html`** : textes du parchemin et des 4 étapes.

## Vérifier un changement

- Tester sur PC **et** en largeur mobile (375 px) : en portrait, la caméra recadre la table autrement (`resize()`).
- Tester ouverture → Échap → réouverture : la fermeture a déjà été cassée une fois (la caméra restait coincée dans le coffre).
- Console sans erreur.
- Sans WebGL ou sans JavaScript, le parchemin s'affiche directement : ne pas casser ce repli (`body.no-webgl`, `.no-js` dans `style.css`).
- Piège Claude Code : si le panneau Browser est masqué, ses captures d'écran sont figées et trompeuses. Afficher le panneau, ou vérifier par `get_page_text` / lecture de pixels WebGL.

## Défauts connus, à polir

- La mousse de la chope ressemble à un pavé plat.
- Les pièces paraissent grosses une fois le coffre ouvert.
- `DOWNLOAD_URL` à remplir avant la mise en ligne.
- L'hébergement n'est pas choisi (n'importe quel hébergement statique convient : GitHub Pages, Netlify…).
