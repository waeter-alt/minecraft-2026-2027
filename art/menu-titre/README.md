# Écran titre animé (coffre qui s'ouvre)

Le fond de l'écran titre est une animation FancyMenu au format **AFMA** : une intro jouée une fois
(la table de la taverne, le coffre s'ouvre, la caméra plonge, le logo s'écrit) puis une boucle de 8 s
(intérieur du coffre, logo « Surcharge arcanique »). Le logo et le sous-titre sont dans l'animation ;
les boutons sont ceux de FancyMenu (layout `kodistan_title.txt`).

Pas de vidéo : le fond vidéo de FancyMenu 3.10 exige le mod Watermedia, l'AFMA est natif.

## Sources
- `ouverture.webm` : ouverture du coffre capturée depuis le site (`site/main.js`).
- `t37.jpg` : dernière image de l'ouverture (coffre ouvert), fond de la boucle.
- `rendu.html` : dessine le logo + les effets par-dessus, image par image.

## Régénérer (Git Bash, depuis ce dossier)
1. `python recevoir.py` (reçoit les images sur le port 8091, les range dans `frames/`)
2. `python -m http.server 8094 --bind 127.0.0.1`
3. `bash rendre-tout.sh` (Chrome sans fenêtre, relance tout seul s'il cale ; ~160 + 150 images)
4. Réduire en 960x540 (intro une image sur deux) dans `frames_960/` — voir l'historique de la PR.
5. Encoder :
   ```
   M=../../mod/run/client/mods
   java -Xmx6g -cp "$M/fancymenu_neoforge_3.10.0_MC_1.21.1.jar;$M/konkrete_neoforge_1.9.9_MC_1.21.jar" \
     de.keksuccino.fancymenu.util.resource.resources.texture.afma.creator.AfmaEncodeStandaloneTool \
     --main frames_960/main --intro frames_960/intro --output out/ecran_titre.afma \
     --preset smallest_file --frame-time 50 --intro-frame-time 100 --loop-count 0
   ```
   (chemins Windows dans `-cp` si `java` ne trouve pas la classe)
6. Copier `out/ecran_titre.afma` dans `mod/env/config/fancymenu/assets/kodistan/` **et**
   `launcher/pack/config/fancymenu/assets/kodistan/`, puis `launcher/scripts/refresh-index.ps1`.

`frames/`, `frames_960/` et `out/` ne vont pas dans git.
