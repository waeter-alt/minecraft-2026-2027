# Gardien des Racines : animations — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** donner au Gardien des Racines 7 animations (repos, regard, émergence, lancer, douleur, rage, mort), avec modèle et animations dans un `.bbmodel` que Luca peut ouvrir et retoucher, sans bouger ses 5 points faibles ni changer le combat.

**Architecture :** `golem_vers_bbmodel.py` porte une fois la géométrie actuelle de `GolemModele.java` dans `golem.bbmodel`, découpée en os, avec les animations de départ. `bbmodel_vers_java.py` traduit le `.bbmodel` en deux classes Java générées (`GolemGeometrie`, `GolemAnimations`). `GolemModele` devient un `HierarchicalModel` écrit à la main qui joue les animations et garde les deux passes de rendu (lumineux / normal). `Golem` déclenche les animations par des événements d'entité, sans toucher à sa logique.

**Tech Stack :** Minecraft 1.21.1, NeoForge 21.1.216, Java 21 (`HierarchicalModel`, `AnimationDefinition`, `AnimationState`, `KeyframeAnimations`), JUnit 5, Python du venv `D:\PROJET\minecraft-2026-2027\test-local\venv\Scripts\python.exe` (Pillow, numpy) avec `unittest`, Blockbench 5.2.2.

**Spec :** `docs/superpowers/specs/2026-11-17-golem-animations-design.md`

Chemins relatifs au worktree `D:\PROJET\wt-golem-anim` (branche `feature/golem-animations`, empilée sur `feature/grappin-apparence`). `JAVA` = `atelier/donjon/arene/src/main/java/fr/atelier/grappin`. `PY` = le python du venv ci-dessus, toujours lancé avec `-I`. Gradle depuis `atelier/donjon/arene`, en Git Bash, `JAVA_HOME=/d/PROJET/minecraft-2026-2027/test-local/outils/jdk-21.0.13+9`.

## Global Constraints

- NeoForge 21.1.216 figé ; aucune nouvelle dépendance (ni GeckoLib ni autre).
- Le combat ne change pas : `Golem.AZIMUTS`, `RAYONS`, `HAUTEURS`, `TAILLES`, `FACTEUR_LARGEUR`, `NOMS_PIECES`, `GolemLogique`, les durées et cadences, l'étiquette `grappin:golem_mort` restent tels quels. `Golem.java` et `GolemComportement.java` ne reçoivent que des déclenchements d'animation, plus la mort à 40 ticks au lieu de 20.
- Les 5 bourgeons (`rouge0..4`), leurs couvercles (`couv0..4`) et leurs nœuds de branche sont dans l'os `fixe`, que **aucune** animation ne touche.
- Au repos, chaque cube garde sa position, sa taille, sa rotation et sa zone de texture d'aujourd'hui (texture `textures/entity/golem.png` 512 × 512 inchangée).
- À garder : yeux, veines et bourgeons toujours lumineux ; teinte `0xFFFFB8A0` en phase 2 ; couvercle invisible et bourgeon qui bat (`1.15 + 0.12·sin(âge·0.4)`) sur la pièce ouverte ; `GolemRendu` (échelle ×4 et ×0,75 en largeur, pas de renversement à la mort, lumière ≥ 8).
- Mouvements lents et lourds : aucune rotation d'animation au-delà de 25° sur le torse, 35° sur la tête, 60° sur un bras.
- Fichiers générés : en-tête `// Genere par atelier/scripts/bbmodel_vers_java.py depuis atelier/modeles/golem/golem.bbmodel : ne pas modifier a la main.`
- Commits en français, avec le pourquoi, terminés par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

### Conventions de repère (une seule, utilisée dans les deux scripts)

- Unités du `.bbmodel` = unités du modèle Java d'aujourd'hui (`U = 4` unités par bloc avant l'échelle ×4 de `GolemRendu`).
- Blockbench (y vers le haut, pieds en y = 0) ↔ Java (y vers le bas, pieds en y = 24) : `x_java = -x_bb`, `y_java = 24 - y_bb` pour la racine et `-Δy` pour les décalages entre os, `z_java = z_bb`.
- Rotations (os, cubes, images clés) : `xRot_java = -rx_bb`, `yRot_java = -ry_bb`, `zRot_java = rz_bb`, en radians dans `PartPose`, en degrés dans `KeyframeAnimations.degreeVec(-rx, -ry, rz)`.
- Positions des images clés : `KeyframeAnimations.posVec(-px, py, pz)` (`posVec` inverse déjà y).
- Le test `test_aller_retour` (tâche 2) vérifie ces conventions de bout en bout.

## Review Focus

1. **Un joueur qui arrive pendant l'émergence ou après** : il ne voit pas le début de l'animation d'émergence (événement déjà passé) ; le Gardien doit alors être simplement au repos, jamais figé dans une pose intermédiaire. → test JUnit `GolemAnimationsTest.emergenceSansEvenementResteAuRepos` (tâche 4).
2. **Deux animations ponctuelles en même temps** (douleur pendant un lancer, rage pendant une douleur) : la plus récente se joue par-dessus, sans saut de pose ; aucune ne reste bloquée. → vérification en jeu, tâche 5 (frapper pendant un lancer).
3. **Le monde rechargé avec un Gardien déjà en phase 2** : pas d'animation de rage au chargement (même règle que l'annonce : `tickCount > 20`). → test JUnit `GolemAnimationsTest.rageAuChargementNeSeJouePas` (tâche 4).
4. **Un point faible ouvert pendant une animation** : il ne doit jamais être masqué par un bras ou la tête. → planche d'images clés vue de chaque point faible (tâche 3) + essai en jeu (tâche 5).
5. **La mort pendant une autre animation** : la mort prend le dessus et le corps n'est retiré qu'après 40 ticks ; l'étiquette de mort part dans `die()` comme avant. → essai en jeu (tâche 5).

---

### Task 1 : le Gardien en os dans `golem.bbmodel` (sans animation) + aperçu

**Files :**
- Create : `atelier/scripts/golem_vers_bbmodel.py`, `atelier/scripts/apercu_bbmodel.py`, `atelier/scripts/tests/test_golem_bbmodel.py`
- Write (généré, commité) : `atelier/modeles/golem/golem.bbmodel`
- Write (non commité) : `atelier/exports/golem_os_apercu.png`

**Interfaces :**
- Produces : `golem.bbmodel` avec les os (noms exacts, uniques dans tout l'arbre) `racine` > {`base`, `fixe`, `corps` > {`tete` > {`machoire`}, `bras_g` > {`main_g`}, `bras_d` > {`poing_d`}, `branches_dos`, `lianes`}}. `base` = taille de mousse, hanches, racines dans la lave, ceinture. `fixe` = `rouge0..4` (lumineux), `couv0..4` et les 4 nœuds de branche de chaque point faible. Cubes lumineux : nom qui commence par `lueur_` (yeux) ou `veine_` (veines de sève) ; bourgeons : `rouge<i>` ; couvercles : `couv<i>`.
- Produces : `golem_vers_bbmodel.py` : `construire() -> dict` (le `.bbmodel`), `main()` l'écrit. `apercu_bbmodel.py <fichier.bbmodel> <sortie.png> [--anim <nom> --temps <s>...]` : planche de vues (face, 3/4, dos), modèle posé à l'instant demandé.

- [ ] **Step 1 : écrire `test_golem_bbmodel.py`** (`unittest`, lit `golem.bbmodel` et, par regex, les constantes de `JAVA/Golem.java`) :
  - `test_os_presents` : les 12 os ci-dessus existent, avec ces parents.
  - `test_points_faibles_dans_fixe_et_a_leur_place` : pour `i` de 0 à 4, `rouge<i>` et `couv<i>` sont enfants de `fixe` ; le centre de `couv<i>` en Java vaut `(cx·4, 24 − cy·4, −cz·4)` à 1e-6 près, avec `rm = RAYONS[i] / FACTEUR_LARGEUR`, `cx = −rm·sin(AZIMUTS[i])`, `cz = rm·cos(AZIMUTS[i])`, `cy = HAUTEURS[i]`, et sa taille `(w, t, w)·4` avec `t = TAILLES[i]`, `w = t / FACTEUR_LARGEUR`.
  - `test_meme_nombre_de_cubes` : le nombre de cubes du `.bbmodel` est égal à celui que produit `creerCouche()` aujourd'hui (compté une fois à la main sur le code actuel et écrit dans le test comme constante `CUBES_ORIGINE`, avec un commentaire qui dit comment il a été compté).
  - `test_texture_et_uv` : `resolution` 512 × 512, `box_uv` partout, texture intégrée identique octet pour octet à `textures/entity/golem.png`.
  - `test_lueurs` : au moins 2 cubes `lueur_`, au moins 1 cube `veine_`, 5 cubes `rouge`.
- [ ] **Step 2 : lancer, il échoue** — Run : `$PY -I -m unittest discover -s atelier/scripts/tests -p "test_golem_bbmodel.py" -v` ; Expected : FAIL / ERROR (fichier absent).
- [ ] **Step 3 : écrire `golem_vers_bbmodel.py`** : traduction ligne à ligne de `GolemModele.creerCouche()` (mêmes boucles, mêmes valeurs, mêmes zones de texture `SOMBRE`, `ROUGE`… comme `uv_offset` en box UV), chaque cube rangé dans l'os de la partie du corps qu'il habille. Pivot de chaque os (en blocs, repère de `cube()`) : `corps` (0, 6, 0), `tete` (0, 31, 2), `machoire` (0, 31, 6), `bras_g` (−11, 28, 0), `main_g` (−12, 10, 2), `bras_d` (11, 28, 0), `poing_d` (10.6, 36, 3), `branches_dos` (0, 26, −5), `lianes` (0, 24, 5), `base`/`fixe`/`racine` (0, 0, 0). Un cube avec rotation garde son centre comme `origin` et `rotation [0, 0, rz]`. Le `.bbmodel` suit le format de `atelier/scripts/grappin_apparence.py` (`format_version` 5.0, `model_format` `"modded_entity"`, `box_uv` vrai, `outliner` qui reflète l'arbre des os).
- [ ] **Step 4 : écrire `apercu_bbmodel.py`** : reprend le dessin de `atelier/scripts/apercu_modele.py` (un quadrilatère par texel, tri par profondeur), en box UV, avec la hiérarchie des os (pivot + rotation de l'os + animation à l'instant demandé, interpolation linéaire). Échelle réduite : un quadrilatère par texel est trop lourd pour 512 × 512 : un quadrilatère par face, couleur moyenne de sa zone de texture.
- [ ] **Step 5 : générer et lancer, il passe** — Run : `$PY -I atelier/scripts/golem_vers_bbmodel.py && $PY -I -m unittest discover -s atelier/scripts/tests -p "test_golem_bbmodel.py" -v` ; Expected : 5 tests OK.
- [ ] **Step 6 : aperçu** — Run : `$PY -I atelier/scripts/apercu_bbmodel.py atelier/modeles/golem/golem.bbmodel atelier/exports/golem_os_apercu.png` ; vérifier à l'œil que la silhouette est celle de la figurine (cornes, poing levé, main pendante, bourgeons autour).
- [ ] **Step 7 : commit** — `Gardien des Racines : le modèle passe dans Blockbench, découpé en os`.

### Task 2 : du `.bbmodel` au Java, `GolemModele` hiérarchique (toujours sans animation)

**Files :**
- Create : `atelier/scripts/bbmodel_vers_java.py`, `atelier/scripts/tests/test_bbmodel_vers_java.py`
- Write (générés, commités) : `JAVA/GolemGeometrie.java`, `JAVA/GolemAnimations.java`
- Modify : `JAVA/GolemModele.java` (réécrit)

**Interfaces :**
- Consumes : `golem.bbmodel` (tâche 1).
- Produces : `bbmodel_vers_java.py <bbmodel> <dossier java>` écrit :
  - `GolemGeometrie` : `static LayerDefinition creerCouche()` (texture 512 × 512) ; `static final String[] LUEURS` (chemins `"corps/tete/lueur_12"`… de chaque cube lumineux, y compris `fixe/rouge<i>`) ; `static final String[] ROUGES` et `COUVERCLES` (5 chemins chacun, dans l'ordre de `Golem.NOMS_PIECES`) ; `static final String TETE = "corps/tete"`.
  - `GolemAnimations` : un `static final AnimationDefinition` par animation du `.bbmodel`, nommé en majuscules (`REPOS`, `EMERGENCE`, `LANCER`, `DOULEUR`, `RAGE`, `MORT`) ; classe vide de champs tant qu'il n'y a pas d'animation.
  - Chaque cube lumineux, rouge, couvercle ou tourné est sa propre `ModelPart` (enfant de son os, nom unique) ; les autres cubes d'un os sont dans le `CubeListBuilder` de l'os.
- Produces : `GolemModele extends HierarchicalModel<Golem>` avec `ModelPart os(String chemin)` (descend par `getChild` sur chaque segment séparé par `/`), et `root()` = la racine du calque.

- [ ] **Step 1 : écrire `test_bbmodel_vers_java.py`** :
  - `test_aller_retour` : un `.bbmodel` minimal (un os `a` pivot (2, 3, 4), rotation (10, 20, 30), un cube de (1,1,1) à (3,2,4) ; une animation `test` de 1 s, une image clé de rotation (5, 6, 7) et une de position (1, 2, 3) à 0,5 s) donne exactement : `PartPose.offsetAndRotation(-2.0F, 21.0F, 4.0F, ` les radians de `(-10, -20, 30)` ; `KeyframeAnimations.degreeVec(-5.0F, -6.0F, 7.0F)` ; `KeyframeAnimations.posVec(-1.0F, 2.0F, 3.0F)` ; `.withLength(1.0F)`.
  - `test_chemins_points_faibles` : sur `golem.bbmodel`, `ROUGES` = `{"fixe/rouge0", …, "fixe/rouge4"}` et `COUVERCLES` pareil avec `couv`.
  - `test_boucle` : une animation dont `loop` vaut `"loop"` produit `.looping()`, les autres non.
- [ ] **Step 2 : lancer, il échoue** — Run : `$PY -I -m unittest discover -s atelier/scripts/tests -p "test_bbmodel_vers_java.py" -v` ; Expected : ERROR (module absent).
- [ ] **Step 3 : écrire `bbmodel_vers_java.py`** selon les conventions de repère ci-dessus ; interpolation `linear` → `AnimationChannel.Interpolations.LINEAR`, `catmullrom` → `CATMULLROM`.
- [ ] **Step 4 : lancer, il passe** — même commande ; Expected : 3 tests OK.
- [ ] **Step 5 : générer et réécrire `GolemModele`** : Run `$PY -I atelier/scripts/bbmodel_vers_java.py atelier/modeles/golem/golem.bbmodel atelier/donjon/arene/src/main/java/fr/atelier/grappin`. `GolemModele` : `COUCHE` inchangée ; `creerCouche()` délègue à `GolemGeometrie.creerCouche()` (l'appel dans `GrappinClient.Rendus.couches` ne change pas) ; `setupAnim` : `root().getAllParts().forEach(ModelPart::resetPose)`, puis couvercles et pulsation comme aujourd'hui (par `os(COUVERCLES[i])` / `os(ROUGES[i])`) ; `renderToBuffer` : passe normale avec les parts de `LUEURS` cachées et la teinte de phase 2, puis passe `FULL_BRIGHT` avec seulement les parts de `LUEURS` visibles (cacher toutes les autres parts le temps de la passe, en gardant les os visibles pour que les enfants soient dessinés).
- [ ] **Step 6 : build** — Run : `./gradlew build --console=plain -q` ; Expected : aucune erreur, tous les tests JUnit passent.
- [ ] **Step 7 : commit** — `Gardien des Racines : le modèle du jeu est généré depuis le fichier Blockbench`.

### Task 3 : les animations dans le `.bbmodel`

**Files :**
- Modify : `atelier/scripts/golem_vers_bbmodel.py` (ajout des animations), `atelier/scripts/tests/test_golem_bbmodel.py`
- Write (générés) : `atelier/modeles/golem/golem.bbmodel`, `JAVA/GolemAnimations.java`
- Write (non commité) : `atelier/exports/golem_animations_apercu.png`

**Interfaces :**
- Produces : animations du `.bbmodel` nommées exactement `repos` (4 s, `loop`), `emergence` (5 s), `lancer` (1,5 s), `douleur` (0,75 s), `rage` (2,5 s), `mort` (2 s) → constantes `REPOS`, `EMERGENCE`, `LANCER`, `DOULEUR`, `RAGE`, `MORT`.

- [ ] **Step 1 : ajouter à `test_golem_bbmodel.py`** :
  - `test_six_animations` : les 6 noms ci-dessus, avec ces durées (à 1e-6), `repos` seul en `loop`.
  - `test_fixe_jamais_anime` : aucun animateur ne vise `fixe` ni un de ses cubes, ni `racine`, ni `base`.
  - `test_angles_lents_et_lourds` : pour chaque image clé de rotation, |angle| ≤ 25 sur `corps`, ≤ 35 sur `tete` et `machoire`, ≤ 60 sur `bras_g`, `main_g`, `bras_d`, `poing_d`, `branches_dos`, `lianes`.
  - `test_boucle_sans_saut` : dans `repos`, les valeurs à 0 s et à 4 s sont égales pour chaque canal.
  - `test_mort_finit_sous_la_lave` : à la fin de `mort`, la position de `corps` a descendu d'au moins 40 unités (10 blocs).
- [ ] **Step 2 : lancer, ils échouent** — Run : `$PY -I -m unittest discover -s atelier/scripts/tests -v` ; Expected : les 5 nouveaux tests FAIL.
- [ ] **Step 3 : écrire les animations dans `golem_vers_bbmodel.py`** :
  - `repos` : `corps` respire (rotation x ±2°, position y ±0,5) ; `tete` lacet ±3° ; `branches_dos` et `lianes` se balancent (z ±4°, déphasés) ; `machoire` ±2°.
  - `emergence` : `corps` part penché en avant (x 20°) et tête baissée, se redresse vers 3 s, `machoire` grande ouverte (25°) et `tete` en arrière (−20°) de 3,2 à 4,2 s (rugissement), retour au repos à 5 s.
  - `lancer` : 0–0,6 s il se ramasse (`corps` x 8°, `bras_g` x −30°) ; 0,6–0,9 s détente (`corps` x −10°, `tete` x −15°, `bras_g` x 40°, `machoire` 15°) ; retour à 1,5 s. La graine part au début (logique inchangée) : l'animation part du même instant.
  - `douleur` : `corps` x −8° et z ±5°, `tete` x −25°, `machoire` 20°, `bras_g` et `bras_d` z ±15° (crispation) ; retour à 0,75 s.
  - `rage` : `corps` x −15°, `bras_g` z −45°, `bras_d` z 30°, `tete` x −30°, `machoire` 30°, tenu de 0,6 à 1,8 s ; retour à 2,5 s.
  - `mort` : `corps` x 25° et descente de 48 unités en 2 s, `tete` x 30°, bras qui retombent (z ±20°).
  Puis régénérer `golem.bbmodel` et `GolemAnimations.java`.
- [ ] **Step 4 : lancer, ils passent** — Run : `$PY -I -m unittest discover -s atelier/scripts/tests -v` ; Expected : tous OK.
- [ ] **Step 5 : planche d'images clés** — Run : `apercu_bbmodel.py` pour chaque animation à 3 instants (début, milieu, fin), vue de face et de dos, assemblées dans `atelier/exports/golem_animations_apercu.png`. Vérifier que les 5 bourgeons restent visibles dans chaque image (Review Focus 4). **Envoyer la planche à Luca, lui dire d'ouvrir `golem.bbmodel` dans Blockbench (onglet Animer), et attendre son accord avant la tâche 4.** S'il retouche dans Blockbench, son fichier devient la source : on ne relance plus `golem_vers_bbmodel.py`, seulement `bbmodel_vers_java.py`.
- [ ] **Step 6 : build et commit** — `./gradlew build --console=plain -q` OK, puis commit `Gardien des Racines : six animations dans Blockbench (repos, émergence, lancer, douleur, rage, mort)`.

### Task 4 : déclencher les animations en jeu

**Files :**
- Create : `JAVA/GolemAnimationsEtat.java` (logique pure : quelle animation démarrer), `atelier/donjon/arene/src/test/java/fr/atelier/grappin/GolemAnimationsTest.java`
- Modify : `JAVA/Golem.java`, `JAVA/GolemComportement.java` (une ligne dans `Lancer.tick`), `JAVA/GolemModele.java` (`setupAnim`)

**Interfaces :**
- Consumes : `GolemAnimations.REPOS…MORT`, `GolemGeometrie.TETE`.
- Produces :
  - `Golem` : constantes `static final byte EV_LANCER = 90, EV_DOULEUR = 91, EV_EMERGENCE = 92` ; champs client `final AnimationState repos, emergence, lancer, douleur, rage, mort` ; `static final int DUREE_MORT = 40`.
  - `GolemAnimationsEtat` (sans Minecraft) : `static boolean jouerRage(boolean avant, boolean apres, int tickCount)` → vrai si `!avant && apres && tickCount > 20` ; `static boolean jouerRepos(boolean emergenceEnCours, boolean mort)` → vrai si ni l'un ni l'autre ; `static float lacetTete(float netHeadYaw)` → borné à [−35, 35] ; `static float tangageTete(float headPitch)` → borné à [−20, 20].

- [ ] **Step 1 : écrire `GolemAnimationsTest`** (JUnit, sans Minecraft) :
  - `rageAuPassageEnPhase2` : `jouerRage(false, true, 500)` vrai.
  - `rageAuChargementNeSeJouePas` : `jouerRage(false, true, 5)` faux ; `jouerRage(true, true, 500)` faux.
  - `emergenceSansEvenementResteAuRepos` : `jouerRepos(false, false)` vrai ; `jouerRepos(true, false)` faux ; `jouerRepos(false, true)` faux.
  - `regardBorne` : `lacetTete(80) == 35`, `lacetTete(-80) == -35`, `lacetTete(10) == 10`, `tangageTete(45) == 20`, `tangageTete(-45) == -20`.
- [ ] **Step 2 : lancer, il échoue** — Run : `./gradlew test --console=plain --tests fr.atelier.grappin.GolemAnimationsTest` ; Expected : échec de compilation (`GolemAnimationsEtat` absente).
- [ ] **Step 3 : écrire `GolemAnimationsEtat`** selon les signatures.
- [ ] **Step 4 : lancer, il passe** — même commande ; Expected : 4 tests OK.
- [ ] **Step 5 : brancher** :
  - `Golem.aiStep`, côté serveur, à l'instant où `emergence = DUREE_EMERGENCE` est posé : `level().broadcastEntityEvent(this, EV_EMERGENCE)`.
  - `GolemComportement.Lancer.tick`, juste avant `playSound(RAVAGER_ATTACK…)` : `golem.level().broadcastEntityEvent(golem, Golem.EV_LANCER)`.
  - `Golem.blesserParPiece` : si `hurt(...)` renvoie vrai, `broadcastEntityEvent(this, EV_DOULEUR)` avant de rendre le résultat.
  - `Golem.handleEntityEvent(byte)` (côté client) : `EV_LANCER` → `lancer.start(tickCount)`, `EV_DOULEUR` → `douleur.start(tickCount)`, `EV_EMERGENCE` → `emergence.start(tickCount)`, `3` (mort) → `mort.start(tickCount)` puis `super` ; autres → `super`.
  - `Golem.onSyncedDataUpdated` : sur `DEUXIEME_PHASE`, côté client, `rage.start(tickCount)` si `GolemAnimationsEtat.jouerRage(ancienne valeur, nouvelle, tickCount)` (garder l'ancienne valeur dans un champ client).
  - `Golem.tick` côté client : `repos.animateWhen(GolemAnimationsEtat.jouerRepos(emergence.isStarted() && emergence.getAccumulatedTime() < 5000, deathTime > 0), tickCount)`.
  - `Golem.tickDeath` : comme `LivingEntity.tickDeath` mais retrait à `DUREE_MORT` ticks (même événement 60 de fumée).
  - `GolemModele.setupAnim` : après `resetPose`, `animate(boss.repos, REPOS, âge)`, puis `EMERGENCE`, `LANCER`, `DOULEUR`, `RAGE`, `MORT` dans cet ordre (la plus importante en dernier) ; puis tête : `os(TETE).yRot += lacetTete(netHeadYaw) * π/180`, `xRot += tangageTete(headPitch) * π/180` (sauf pendant `mort`) ; puis couvercles et pulsation.
- [ ] **Step 6 : build** — `./gradlew build --console=plain -q` ; Expected : aucune erreur, tous les tests passent.
- [ ] **Step 7 : commit** — `Gardien des Racines : les animations se déclenchent avec le combat`.

### Task 5 : jar 3.2.0 et essai en jeu

- [ ] **Step 1** : `mod_version=3.2.0` dans `atelier/donjon/arene/gradle.properties`, `./gradlew build --console=plain -q`, copier `build/libs/grappin-3.2.0.jar` dans `D:\PROJET\minecraft-2026-2027\atelier\exports\`. Commit `Grappin 3.2.0 : Gardien des Racines animé`.
- [ ] **Step 2** : avec l'accord de Luca (un client de test peut être ouvert), lancer `test-local/lancer-client-test.sh`, monde « Donjon Suspendu », salle 3. Vérifier et capturer : émergence ; repos ; regard en tournant autour ; lancer ; douleur sur chacun des 5 points faibles, frappés depuis la rampe comme avant ; frapper pendant un lancer (Review Focus 2) ; rage au passage en phase 2 ; mort (2 s, puis la sortie s'ouvre comme avant, Review Focus 5) ; recharger le monde en phase 2 (pas de rage, Review Focus 3). Sinon, envoyer le jar et la liste à Luca pour qu'il teste.
- [ ] **Step 3** : envoyer les captures à Luca ; les corrections repassent par la tâche concernée.

### Task 6 : report dans `mod/` (branche `feature/objets-dans-mod`)

Après l'accord de Luca sur l'essai en jeu. Dans `D:\PROJET\wt-objets\mod` : générer `GolemGeometrie` et `GolemAnimations` dans le paquet du Gardien de `mod/` (option `--paquet` à ajouter à `bbmodel_vers_java.py`), reporter les changements de `GolemModele`, `Golem`, `GolemComportement`, `GolemAnimationsEtat` et `GolemAnimationsTest` ; `./gradlew build --console=plain -q` OK ; commit `Gardien des Racines : reprend les animations (3.2.0)`.

Pousser et ouvrir une PR : seulement à la demande de Luca ; le merge vers `main` est décidé par Duncan.
