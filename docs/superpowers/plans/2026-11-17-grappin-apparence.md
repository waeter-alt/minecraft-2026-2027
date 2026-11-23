# Grappin : nouvelle apparence — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** donner au grappin un modèle 3D en main, une icône 2D plus détaillée, un crochet 3D orienté pendant le vol et une corde tressée, sans toucher à son fonctionnement.

**Architecture :** un script Python unique dessine les textures et écrit les modèles JSON (format « Java Block/Item » de Blockbench) ainsi que les fichiers `.bbmodel` à ouvrir dans Blockbench ; un second script fait des images d'aperçu à partir de ces JSON. Côté Java, seul le rendu change : `RenduCrochet` dessine un modèle 3D à part (chargé en « modèle additionnel ») tourné selon le vol, et la corde devient une bande à deux tons, calculée par une petite classe de géométrie testée en JUnit.

**Tech Stack :** Minecraft 1.21.1, NeoForge 21.1.216, Java 21, JUnit 5 (déjà dans `build.gradle`), Python du venv `D:\PROJET\minecraft-2026-2027\test-local\venv\Scripts\python.exe` (Pillow, numpy), Blockbench 5.2.2.

**Spec :** `docs/superpowers/specs/2026-11-17-grappin-apparence-design.md`

Chemins relatifs au worktree `D:\PROJET\wt-grappin-apparence` (branche `feature/grappin-apparence`). `RES` = `atelier/donjon/arene/src/main/resources/assets/grappin`. `JAVA` = `atelier/donjon/arene/src/main/java/fr/atelier/grappin`. `TEST` = `atelier/donjon/arene/src/test/java/fr/atelier/grappin`.

## Global Constraints

- Minecraft 1.21.1, NeoForge 21.1.216 : ne pas toucher `neo_version`. Aucune nouvelle dépendance (pas de GeckoLib).
- Rien du fonctionnement ne change : `Crochet.java`, `GrappinItem.java`, `Reglages.java` et la classe `Traction` ne sont pas modifiés.
- Le bloc d'ancrage, la voile et la paravoile gardent leur apparence.
- Gros pixels, style Minecraft : textures de 16 × 16 (icône) et 32 × 32 (modèles 3D), pas de dégradés lisses.
- Couleurs de départ (celles de l'icône actuelle) : bois `(125,86,50)` / `(90,60,35)`, corde `(200,185,150)`, fer `(165,168,175)` / clair `(215,218,222)` / sombre `(95,98,105)`.
- Commandes Gradle en Git Bash, depuis `atelier/donjon/arene`, avec `JAVA_HOME=/d/PROJET/minecraft-2026-2027/test-local/outils/jdk-21.0.13+9`. Le client Minecraft doit être fermé pendant un build.
- Commits en français, qui expliquent le pourquoi, terminés par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Écart signalé à Luca : le crochet aura **quatre griffes** (une par direction cardinale) au lieu de trois. Un modèle Minecraft ne tourne un cube que sur un seul axe, donc trois griffes à 120° ne sont pas dessinables proprement.

## Review Focus

1. **La vue en 1re personne** : l'objet en main ne doit pas cacher le centre de l'écran (le réticule) ni le bas-centre. → capture en jeu à la tâche 5.
2. **La corde dans le noir** (Donjon Suspendu) : elle doit rester visible sans briller. Elle prend la lumière des deux bouts, comme la laisse. → capture en jeu à la tâche 5.
3. **Une corde très courte ou de longueur nulle** (crochet qui touche un ancrage collé au joueur) : aucun NaN, rien d'aberrant. → `CordeTest.longueurNulleSansNaN`.
4. **Le crochet accroché** : il reste tourné comme pendant son dernier vol et ne pivote pas avec la caméra. → vérification en jeu à la tâche 5.
5. **L'objet par terre, dans un cadre ou porté par un monstre** : le modèle 3D doit rester lisible et à une taille normale. → capture en jeu à la tâche 5 (lâcher l'objet, le mettre dans un cadre).

---

### Task 1 : modèles, textures et fichiers Blockbench (sans Java)

**Files :**
- Create : `atelier/scripts/grappin_apparence.py` (unique source des textures, modèles JSON et `.bbmodel`)
- Create : `atelier/scripts/apercu_modele.py` (rendu d'aperçu d'un modèle JSON)
- Write (générés) : `RES/textures/item/grappin.png` (nouvelle icône 16 × 16), `RES/textures/item/grappin_modele.png` (32 × 32, partagée par les deux modèles 3D), `RES/models/item/grappin.json`, `RES/models/item/grappin_main.json`, `RES/models/item/grappin_icone.json`, `RES/models/item/crochet.json`, `atelier/modeles/grappin/grappin.bbmodel`, `atelier/modeles/grappin/crochet.bbmodel`
- Write (aperçus, non commités) : `atelier/exports/grappin_apercu.png`
- Test : `TEST/ModelesGrappinTest.java`

**Interfaces :**
- Produces : le modèle `grappin:item/crochet` (fichier `RES/models/item/crochet.json`). Dans l'espace du modèle (0–16), **les griffes pointent vers +X**, l'anneau où s'attache la corde est vers x = 0, et le tout est centré en y = z = 8. La tâche 2 en dépend.
- Produces : `grappin_apparence.py` sans argument régénère tout ; `apercu_modele.py <modele.json> <sortie.png>` fait une planche de 3 vues.

- [ ] **Step 1 : écrire le test des ressources** `ModelesGrappinTest` (il lit les fichiers depuis `src/main/resources` avec `java.nio`, sans le jeu, et parse le JSON avec Gson ; si Gson n'est pas sur le classpath des tests, ajouter `testImplementation 'com.google.code.gson:gson:2.11.0'`, une dépendance de test seulement, absente du jar) :
  - `grappinUtiliseSeparateTransforms` : `grappin.json` a `"loader": "neoforge:separate_transforms"`, `base.parent == "grappin:item/grappin_main"`, `perspectives.gui.parent == "grappin:item/grappin_icone"`.
  - `iconeEstPlate` : `grappin_icone.json` a `parent == "minecraft:item/handheld"` et `textures.layer0 == "grappin:item/grappin"`.
  - `modeles3dSansParentGenere` : `grappin_main.json` et `crochet.json` n'ont **pas** de `parent` (un parent `item/generated` ignorerait les cubes), ont `texture_size == [32,32]` et au moins 8 éléments chacun.
  - `texturesExistentEtOntLaBonneTaille` : toute texture citée par les quatre modèles existe sous `textures/` ; `grappin.png` fait 16 × 16 ; `grappin_modele.png` fait 32 × 32 (lecture avec `javax.imageio.ImageIO`).
  - `cubesDansLesLimites` : pour chaque élément, `from` et `to` sont dans [-16, 32] ; `rotation.angle` ∈ {-45, -22.5, 0, 22.5, 45} ; chaque `uv` de face est dans [0, 16] (échelle des UV de Minecraft, quelle que soit la taille de texture).
  - `affichageEnMainDefini` : `grappin_main.json` définit `display` pour `thirdperson_righthand`, `firstperson_righthand`, `ground`, `fixed` et `head`.

- [ ] **Step 2 : lancer le test, il échoue**
  Run : `./gradlew test --console=plain --tests fr.atelier.grappin.ModelesGrappinTest`
  Expected : FAIL (fichiers `grappin_main.json`, `crochet.json`, `grappin_icone.json` absents).

- [ ] **Step 3 : écrire `grappin_apparence.py`**
  - Les cubes sont décrits **une seule fois** en Python (liste de `dict(nom, de, a, uv, rotation)`), puis écrits deux fois : en JSON de modèle et en `.bbmodel`. Pour le format du `.bbmodel` (bloc `meta`, `elements` avec `uuid`, `textures` en PNG base64 dans `source`), partir d'un fichier vide enregistré par Blockbench 5.2.2 en « Java Block/Item » et reprendre son `meta.format_version`.
  - **Grappin en main** (`grappin_main.json`), vertical, tenu par le bas : poignée en bois (environ 2 × 7 × 2) avec deux bagues de fer ; corps du lanceur en fer (environ 3 × 3 × 3) ; bobine de corde sur le côté −X (cylindre approché par 2–3 cubes avec flasques en fer) ; tige de fer ; **quatre griffes** en escalier de petits cubes, une par direction cardinale, recourbées vers le bas. Le `display` reprend les valeurs de `minecraft:item/handheld` (thirdperson_righthand : rotation `[0,-90,55]`, translation `[0,4,0.5]`, échelle `0.85` ; firstperson_righthand : rotation `[0,-90,25]`, translation `[1.13,3.2,1.13]`, échelle `0.68` ; et leurs symétriques à gauche), puis les ajuste pour que le modèle ne dépasse pas l'ancienne icône en 1re personne. `ground` et `fixed` restent en 3D.
  - **Crochet seul** (`crochet.json`) : tige de fer le long de X, anneau en x ≈ 0–2, quatre griffes au bout +X, même texture.
  - **`grappin.json`** : `{"loader":"neoforge:separate_transforms","base":{"parent":"grappin:item/grappin_main"},"perspectives":{"gui":{"parent":"grappin:item/grappin_icone"}}}`.
  - **Icône 16 × 16** : redessinée pixel par pixel dans le même esprit (lanceur, bobine, quatre griffes vues de côté, corde qui pend), avec au moins 3 tons par matière.
  - L'ancien `atelier/scripts/icone_grappin.py` écrit vers un chemin qui n'existe plus : le laisser tel quel (hors sujet).

- [ ] **Step 4 : écrire `apercu_modele.py`** : projection orthographique de chaque face, découpée en un quadrilatère par texel, triée par profondeur et dessinée avec `PIL.ImageDraw.polygon`. Rotations d'élément sur un axe gérées. Planche de 3 vues (3/4 avant, côté, dessus), fond gris `(200,200,200)`, l'icône 16 × 16 agrandie ×8 à côté.

- [ ] **Step 5 : générer, puis relancer le test, il passe**
  Run : `/d/PROJET/minecraft-2026-2027/test-local/venv/Scripts/python.exe -I atelier/scripts/grappin_apparence.py && ./gradlew test --console=plain --tests fr.atelier.grappin.ModelesGrappinTest`
  Expected : PASS.

- [ ] **Step 6 : point d'arrêt avec Luca** : produire `atelier/exports/grappin_apercu.png` (main + crochet + icône), l'envoyer à Luca et lui dire qu'il peut ouvrir `atelier/modeles/grappin/grappin.bbmodel` dans Blockbench. **Ne pas passer à la tâche 2 sans son accord.** Ses retouches se font dans le script (régénérer) ; s'il retouche lui-même dans Blockbench, son export « Java Block/Item » devient la source et le script ne réécrit plus ce modèle.

- [ ] **Step 7 : commit**
```bash
git add atelier/scripts/grappin_apparence.py atelier/scripts/apercu_modele.py atelier/modeles/grappin atelier/donjon/arene/src/main/resources/assets/grappin atelier/donjon/arene/src/test/java/fr/atelier/grappin/ModelesGrappinTest.java
git commit -m "Grappin : modèle 3D en main, icône redessinée, crochet seul (Blockbench)"
```

### Task 2 : le crochet en 3D pendant le vol

**Files :**
- Modify : `JAVA/GrappinClient.java` (classe `Rendus`, classe `RenduCrochet` lignes 127-149)

**Interfaces :**
- Consumes : le modèle `grappin:item/crochet` de la tâche 1 (griffes vers +X).
- Produces : `GrappinClient.Rendus.MODELE_CROCHET : ModelResourceLocation` = `ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "item/crochet"))`.

- [ ] **Step 1 : enregistrer le modèle additionnel** : dans `Rendus`, un abonné `ModelEvent.RegisterAdditional` qui appelle `event.register(MODELE_CROCHET)`.
- [ ] **Step 2 : `RenduCrochet extends EntityRenderer<Crochet>`** (plus `ThrownItemRenderer`) : `render(...)` pousse la pose, tourne comme une flèche (`Axis.YP` de `Mth.lerp(partiel, yRotO, getYRot()) - 90`, puis `Axis.ZP` de `Mth.lerp(partiel, xRotO, getXRot())`), met à l'échelle `ECHELLE_CROCHET = 0.75F`, puis dessine le modèle avec `Minecraft.getInstance().getItemRenderer().render(stack, ItemDisplayContext.NONE, false, pose, tampons, lumiere, OverlayTexture.NO_OVERLAY, modele)`, où `modele = Minecraft.getInstance().getModelManager().getModel(MODELE_CROCHET)` et `stack = crochet.getItem()`. `getTextureLocation` renvoie `TextureAtlas.LOCATION_BLOCKS`. Le dessin de la corde reste celui d'aujourd'hui (il change à la tâche 3).
- [ ] **Step 3 : build**
  Run : `./gradlew build --console=plain -q`
  Expected : aucune erreur ; les tests JUnit existants (`GolemLogiqueTest`, `LeviathanLogiqueTest`, `SeraphinLogiqueTest`, `ModelesGrappinTest`) passent.
- [ ] **Step 4 : commit** — `Grappin : le crochet lancé est un vrai crochet 3D tourné dans le sens du vol`.

### Task 3 : la corde tressée

**Files :**
- Create : `JAVA/Corde.java` (géométrie pure, sans classe Minecraft)
- Modify : `JAVA/GrappinClient.java` (`RenduCrochet.render`)
- Test : `TEST/CordeTest.java`

**Interfaces :**
- Produces : `final class Corde` avec
  - `static final int SEGMENTS = 24;`, `static final float LARGEUR = 0.06F;`
  - `static double mou(boolean accroche, double longueur)` : `0` si accroché (la corde tire, elle est tendue), sinon `Math.min(0.06 * longueur, 1.0)`.
  - `static double[][] points(double dx, double dy, double dz, double mou, int segments)` : `segments + 1` points `{x,y,z}`, du crochet (0,0,0) à la main (dx,dy,dz). Point i, avec t = i/segments : `{dx·t, dy·t − mou·4·t·(1−t), dz·t}`.

- [ ] **Step 1 : écrire `CordeTest`**
  - `extremitesExactes` : `points(3,1,-2, 0.5, 24)` a 25 points ; le premier vaut `{0,0,0}`, le dernier `{3,1,-2}` (à 1e-9 près).
  - `sansMouLigneDroite` : avec `mou = 0`, le point 12 vaut `{1.5, 0.5, -1}`.
  - `mouAuMilieu` : `points(4,0,0, 0.5, 24)[12][1] == -0.5`.
  - `tendueQuandAccrochee` : `mou(true, 20) == 0`.
  - `mouLimite` : `mou(false, 5) == 0.3` ; `mou(false, 30) == 1.0`.
  - `longueurNulleSansNaN` : `points(0,0,0, mou(false,0), 24)` : aucune coordonnée NaN ni infinie.
- [ ] **Step 2 : lancer, il échoue** — Run : `./gradlew test --console=plain --tests fr.atelier.grappin.CordeTest` ; Expected : FAIL (`Corde` absente).
- [ ] **Step 3 : écrire `Corde`** selon les signatures ci-dessus.
- [ ] **Step 4 : lancer, il passe** — même commande ; Expected : PASS.
- [ ] **Step 5 : dessiner la corde dans `RenduCrochet.render`** : remplacer les deux `addVertex` de `RenderType.lines()` par deux bandes croisées (verticale et horizontale, comme la laisse vanilla dans `EntityRenderer.renderLeash`) dans `RenderType.leash()`. Les points viennent de `Corde.points(main − ici, Corde.mou(crochet.estAccroche(), longueur), Corde.SEGMENTS)`, la largeur est `Corde.LARGEUR`, et les couleurs alternent d'un segment à l'autre entre `(139,105,70)` et `(105,78,50)`. La lumière est interpolée entre le bloc du crochet et celui du lanceur (`LightTexture.pack` des deux bouts), comme la laisse.
- [ ] **Step 6 : build** — Run : `./gradlew build --console=plain -q` ; Expected : aucune erreur, tous les tests passent.
- [ ] **Step 7 : commit** — `Grappin : corde tressée à deux tons, avec du mou en vol et tendue en traction`.

### Task 4 : version 3.1.0 et jar d'essai

**Files :**
- Modify : `atelier/donjon/arene/gradle.properties` (`mod_version=3.1.0`)

- [ ] **Step 1** : passer `mod_version` à `3.1.0`, puis `./gradlew build --console=plain -q`.
- [ ] **Step 2** : copier `build/libs/grappin-3.1.0.jar` dans `D:\PROJET\minecraft-2026-2027\atelier\exports\` (c'est là que le raccourci « Client de test Kodistan » prend le jar le plus récent). Expected : `ls atelier/exports/grappin-3.1.0.jar` existe dans le dépôt principal.
- [ ] **Step 3 : commit** — `Grappin 3.1.0 : nouvelle apparence`.

### Task 5 : essai en jeu

- [ ] **Step 1** : créer `.claude/testing.flag`, lancer le client de test (raccourci « Client de test Kodistan »), ouvrir le monde « Donjon Suspendu ».
- [ ] **Step 2 : captures**, une par point : inventaire (icône) ; 1re personne au repos (le réticule doit rester libre, Review Focus 1) ; 3e personne ; crochet en vol (de côté) ; crochet accroché puis caméra tournée (il ne doit pas pivoter, Review Focus 4) ; corde pendant la traction ; corde dans une zone sombre du sous-sol 1 (Review Focus 2) ; objet lâché par terre et dans un cadre (Review Focus 5).
- [ ] **Step 3** : retirer `.claude/testing.flag` ; envoyer les captures à Luca. Les corrections repassent par la tâche concernée (script → régénérer → test → jar 3.1.x).

### Task 6 : report dans `mod/` (branche `feature/objets-dans-mod`)

À faire seulement après l'accord de Luca sur l'essai en jeu.

**Files (worktree `D:\PROJET\wt-objets`) :**
- Copy : les fichiers générés de la tâche 1 vers `mod/src/main/resources/assets/grappin/` (mêmes chemins relatifs) ; `ModelesGrappinTest` vers `mod/src/test/java/fr/kodistan/interregne/grappin/` et `CordeTest` vers `mod/src/test/java/fr/kodistan/interregne/client/grappin/` (même paquet que `Corde`), en adaptant le paquet et le chemin des ressources.
- Create : `mod/src/main/java/fr/kodistan/interregne/client/grappin/Corde.java`
- Modify : `mod/src/main/java/fr/kodistan/interregne/client/grappin/GrappinClient.java` (mêmes changements qu'aux tâches 2 et 3)

- [ ] **Step 1** : copier et adapter, puis `./gradlew build --console=plain -q` depuis `mod/`. Expected : build OK, `ModelesGrappinTest` et `CordeTest` passent.
- [ ] **Step 2 : commit** dans `feature/objets-dans-mod` — `Grappin : reprend la nouvelle apparence (3.1.0)`.

Pousser une branche et ouvrir une PR : seulement à la demande de Luca. `feature/grappin-traction-simple` n'est pas encore sur GitHub, donc la PR de cette branche dépend de la sienne. Le merge vers `main` est décidé par Duncan.
