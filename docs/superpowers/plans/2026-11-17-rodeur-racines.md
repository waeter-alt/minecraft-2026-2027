# Rôdeur des Racines — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** un mini-boss `grappin:rodeur` (bête de racines à quatre pattes, réveil, morsure, charge, sonné contre un mur, mort), modélisé et animé dans un `.bbmodel` vérifié dans Blockbench, qui remplace le vindicateur provisoire du sous-sol 1 du Donjon Suspendu.

**Architecture :** même chaîne que le Gardien : `rodeur_vers_bbmodel.py` écrit `atelier/modeles/rodeur/rodeur.bbmodel` (os, box UV sur la texture du Gardien, 8 animations) ; `bbmodel_vers_java.py --nom Rodeur` génère `RodeurGeometrie` et `RodeurAnimations` ; `RodeurModele` (`HierarchicalModel`) joue les animations selon un état synchronisé ; `Rodeur` (`Monster`) porte le combat, dont la logique pure est dans `RodeurLogique` (testée en JUnit).

**Tech Stack :** Minecraft 1.21.1, NeoForge 21.1.216, Java 21, JUnit 5, Python du venv (`D:\PROJET\minecraft-2026-2027\test-local\venv\Scripts\python.exe`, `-I`, `unittest`), Blockbench 5.2.2 (ouvert sur le PC de Luca pour vérifier et capturer).

**Spec :** `docs/superpowers/specs/2026-11-17-rodeur-racines-design.md`

Worktree `D:\PROJET\wt-rodeur`, branche `feature/rodeur-racines` (partie de `feature/golem-animations`). `JAVA` = `atelier/donjon/arene/src/main/java/fr/atelier/grappin`, `TEST` = `atelier/donjon/arene/src/test/java/fr/atelier/grappin`, `RES` = `atelier/donjon/arene/src/main/resources/assets/grappin`. Gradle en Git Bash depuis `atelier/donjon/arene`, `JAVA_HOME=/d/PROJET/minecraft-2026-2027/test-local/outils/jdk-21.0.13+9`.

## Global Constraints

- NeoForge 21.1.216 figé, aucune nouvelle dépendance ; le Gardien des Racines, le grappin et leurs fichiers ne changent pas (sauf l'en-tête paramétrable de `bbmodel_vers_java.py`).
- Texture : `textures/entity/golem.png` (512 × 512), zones du Gardien : `SOMBRE (0,0)`, `ROUGE (0,130)`, `MOUSSE (0,224)`, `CLAIR (260,0)`, `CREME (330,100)`, `CERNES (330,140)`, `AMBRE (330,200)`.
- Taille : environ 4 blocs de long, 2,5 de haut (le rendu ne met pas d'échelle : 16 unités = 1 bloc).
- Conventions de repère Blockbench ↔ Java : celles du plan du Gardien (`x_java = -x_bb`, `y_java = 24 - y_bb`, rotations `(-rx, -ry, rz)`), gérées par `bbmodel_vers_java.py`.
- Valeurs de départ (dans `Reglages`, réglables) : `rodeurVie` 150, `rodeurMorsure` 8, `rodeurVitesse` 0,3, `rodeurVitesseCharge` 0,9 bloc/tick, `rodeurPorteeCharge` 24 blocs, `rodeurGrattage` 20 ticks, `rodeurSonne` 60 ticks, `rodeurDelaiCharge` 120 ticks, `rodeurFacteurSonne` 2,0.
- Le monstre apparaît avec l'étiquette `suspendu_ss1m` (fonctions du donjon) ; rien d'autre ne doit dépendre de lui dans le donjon.
- Commits en français avec le pourquoi, terminés par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Charge qui ne touche rien** (joueur esquivé, salle dégagée) : elle s'arrête au bout de `rodeurPorteeCharge` blocs sans le sonner ; jamais de charge infinie. → `RodeurLogiqueTest.chargeSArreteAuBoutDeLaPortee`.
2. **Charge qui frappe un joueur** : dégâts de morsure et recul, elle s'arrête, pas de sonné. → `RodeurLogiqueTest.chargeQuiToucheNeSonnePas`.
3. **Coincé contre un mur au départ de la charge** : il est sonné une seule fois, puis repart normalement (pas de boucle sonné/charge). → `RodeurLogiqueTest.delaiApresSonne`.
4. **Le joueur meurt ou s'éloigne pendant le grattage** : la charge est annulée, il reprend la marche. → essai en jeu (tâche 5).
5. **Rechargement du monde avec le Rôdeur vivant** : il garde sa vie, ne rejoue pas son réveil, n'est jamais figé sonné. → essai en jeu (tâche 5).

---

### Task 1 : le Rôdeur dans Blockbench (modèle + animations), vérifié dans Blockbench

**Files :** Create `atelier/scripts/rodeur_vers_bbmodel.py`, `atelier/scripts/tests/test_rodeur_bbmodel.py` ; Write `atelier/modeles/rodeur/rodeur.bbmodel`.

**Interfaces — Produces :** os (noms uniques) `racine` > `corps` > {`cou` > `tete` > {`machoire`}, `patte_av_g`, `patte_av_d`, `patte_ar_g`, `patte_ar_d`, `queue`, `crete`, `lianes`}. Cubes lumineux : `lueur_*` (yeux, baies). Animations : `repos` (3 s, loop), `marche` (1 s, loop), `morsure` (0,5 s), `grattage` (1 s, loop), `charge` (0,5 s, loop), `sonne` (1,5 s, loop), `reveil` (2,5 s), `mort` (1 s).

- [ ] **Step 1 : tests** `test_rodeur_bbmodel.py` : `test_os` (arbre ci-dessus) ; `test_taille` (boîte englobante au repos : longueur z entre 56 et 72 unités, hauteur entre 32 et 44, largeur x entre 20 et 32) ; `test_lueurs` (2 yeux `lueur_oeil_*`, au moins 3 baies `lueur_baie_*`) ; `test_animations` (8 noms, durées, boucles ci-dessus) ; `test_boucles_sans_saut` (pour chaque animation en boucle, valeurs à 0 et à la fin égales) ; `test_texture` (texture intégrée = `golem.png`, `box_uv` partout) ; `test_pas_de_gros_bloc` (aucun cube de plus de 16 unités dans ses trois dimensions à la fois).
- [ ] **Step 2 : échec** — `$PY -I -m unittest discover -s atelier/scripts/tests -p "test_rodeur*" -v`.
- [ ] **Step 3 : écrire** `rodeur_vers_bbmodel.py` en reprenant l'écriture du `.bbmodel` de `golem_vers_bbmodel.py` (format 4.10, `modded_entity`, box UV, `outliner` imbriqué) : corps en racines tressées (cubes allongés, alternance `SOMBRE`/`CERNES`), dos de `MOUSSE`, tête d'écorce `CLAIR` avec mâchoire `SOMBRE` et crocs `CREME`, yeux `AMBRE` (lueur), crête de petits cubes inclinés, 4 pattes à deux segments, queue de racines, lianes pendantes avec baies `AMBRE` (lueur). Animations : repos (respiration, tête qui renifle), marche (pattes en diagonale opposées ±30°), morsure (tête en avant, mâchoire ouverte 35°), grattage (patte avant droite qui gratte, tête basse), charge (corps bas, pattes en galop ±40°), sonné (tête qui oscille, corps de travers), réveil (part couché sous le sol, se redresse et s'ébroue), mort (s'affaisse sur le flanc, racines qui s'écartent).
- [ ] **Step 4 : générer, passe.**
- [ ] **Step 5 : vérifier dans Blockbench** : ouvrir le fichier avec `C:\Program Files\Blockbench\Blockbench.exe`, demander l'accès à la fenêtre, faire des captures (face, profil, trois-quarts, puis chaque animation à mi-course dans l'onglet Animer) ; corriger ce qui cloche (faces mal texturées, pivots) dans le script ; **envoyer les captures à Luca et attendre son accord.** Si Blockbench refuse le fichier, corriger le format avant tout.
- [ ] **Step 6 : commit** — `Rôdeur des Racines : modèle et animations dans Blockbench`.

### Task 2 : du `.bbmodel` au jeu (modèle, rendu, enregistrement)

**Files :** Modify `atelier/scripts/bbmodel_vers_java.py` (en-tête avec le vrai chemin source) ; Write `JAVA/RodeurGeometrie.java`, `JAVA/RodeurAnimations.java` ; Create `JAVA/RodeurModele.java`, `JAVA/RodeurRendu.java`, `JAVA/Rodeur.java` (squelette d'entité sans combat), `atelier/scripts/tests/test_bbmodel_vers_java.py::test_entete_source` ; Modify `JAVA/Grappin.java`, `JAVA/GrappinClient.java`, `RES/lang/fr_fr.json`, `RES/lang/en_us.json`, `RES/models/item/rodeur_spawn_egg.json`.

**Interfaces — Produces :** `Grappin.RODEUR : DeferredHolder<EntityType<?>, EntityType<Rodeur>>` (id `rodeur`, `MobCategory.MONSTER`, taille 1,6 × 2,4) ; `Grappin.RODEUR_OEUF` ; `Rodeur.attributs()` ; `RodeurModele.COUCHE` ; `Rodeur` porte les `AnimationState` `repos, marche, morsure, grattage, charge, sonne, reveil, mort` (client).

- [ ] **Step 1** : test `test_entete_source` (`java_geometrie` écrit le chemin du `.bbmodel` passé en argument dans l'en-tête, pas celui du Gardien) ; échec, implémentation (paramètre `source`), passe ; régénérer le Gardien (`GolemGeometrie`, `GolemAnimations`, `GolemSquelette` : seul l'en-tête peut changer) et générer le Rôdeur avec `--nom Rodeur`.
- [ ] **Step 2** : `RodeurModele extends HierarchicalModel<Rodeur>` : rendu en deux passes comme `GolemModele` (lueurs `FULL_BRIGHT`) ; `setupAnim` : `resetPose`, `animateWalk(MARCHE, limbSwing, limbSwingAmount, 2, 2.5)`, puis `animate` des autres états, regard sur `cou/tete` borné à ±30°. `RodeurRendu extends MobRenderer` (ombre 1,0, texture `golem.png`).
- [ ] **Step 3** : enregistrement de l'entité, des attributs, de l'œuf (couleurs `0x5A3A1E`, `0x7A9A3A`), du calque et du rendu ; langues (« Rôdeur des Racines », « Œuf d'apparition de Rôdeur des Racines »).
- [ ] **Step 4** : build OK ; commit `Rôdeur des Racines : le modèle Blockbench dans le jeu`.

### Task 3 : la logique du combat (pure)

**Files :** Create `JAVA/RodeurLogique.java`, `TEST/RodeurLogiqueTest.java`.

**Interfaces — Produces :** `enum RodeurLogique.Etat { CHASSE, GRATTE, CHARGE, SONNE }` ; `static Etat suivant(Etat etat, int ticksDansEtat, boolean cibleValide, boolean aPortee, boolean bloque, boolean toucheJoueur, double distanceParcourue, int ticksDepuisCharge, Parametres p)` ; `record Parametres(int grattage, int sonne, int delaiCharge, double portee)` ; `static float degats(Etat etat, float montant, double facteurSonne)`.

- [ ] **Step 1 : tests** : `chasseVersGratteQuandPretEtAPortee` ; `pasDeChargeAvantLeDelai` ; `gratteAnnuleSiCiblePerdue` ; `gratteVersChargeApresGrattage` ; `chargeBloqueeSonne` ; `chargeQuiToucheNeSonnePas` (→ `CHASSE`) ; `chargeSArreteAuBoutDeLaPortee` (→ `CHASSE`) ; `sonneFiniApresDuree` (→ `CHASSE`) ; `delaiApresSonne` (juste après `SONNE`, `ticksDepuisCharge` petit → reste `CHASSE`) ; `degatsDoublesQuandSonne` / `degatsNormauxSinon`.
- [ ] **Step 2 : échec ; Step 3 : écrire ; Step 4 : passe** — `./gradlew test --console=plain --tests fr.atelier.grappin.RodeurLogiqueTest`.
- [ ] **Step 5 : commit** — `Rôdeur des Racines : règles de la charge et du sonné`.

### Task 4 : le combat en jeu

**Files :** Modify `JAVA/Rodeur.java`, `JAVA/Reglages.java`.

- [ ] **Step 1** : `Reglages` : les 9 valeurs des Global Constraints.
- [ ] **Step 2** : `Rodeur` : donnée synchronisée `ETAT` (int) ; buts `FloatGoal`, `MeleeAttackGoal` (actif seulement en `CHASSE`), un but `Charge` qui appelle `RodeurLogique.suivant` à chaque tick (en `GRATTE` : immobile, tourné vers la cible ; en `CHARGE` : `setDeltaMovement` en ligne droite figée au départ, à `rodeurVitesseCharge`, `bloque` = `horizontalCollision`, `toucheJoueur` = un joueur dans la boîte élargie de 0,5 → dégâts `rodeurMorsure` + recul 1,5) ; `hurt` passe par `RodeurLogique.degats` ; à l'enregistrement, l'état n'est jamais sauvé (il repart en `CHASSE`) ; client : `animateWhen` selon `ETAT`, événement `EV_MORSURE = 90` envoyé dans `doHurtTarget`, `reveil` démarré au premier tick client si `tickCount < 5`, `mort` à l'événement 3 ; sons : grognement (`RAVAGER_ROAR`, aigu) au réveil et au grattage, `RAVAGER_STUNNED` au sonné, `ZOGLIN_ATTACK` à la morsure.
- [ ] **Step 3** : build OK, tous les tests passent ; commit `Rôdeur des Racines : réveil, morsure, charge et sonné en jeu`.

### Task 5 : dans le donjon, jar 3.4.0, essai

- [ ] **Step 1** : `mod_version=3.4.0`, build, copie de `grappin-3.4.0.jar` dans `D:\PROJET\minecraft-2026-2027\atelier\exports\`, commit.
- [ ] **Step 2** : branche `feature/donjon-suspendu` (dépôt principal `D:\PROJET\minecraft-2026-2027`, après `git status` propre ou en ne touchant que ce fichier) : dans `atelier/scripts/donjon_suspendu.py`, `summon grappin:rodeur` au lieu du vindicateur (même étiquette, même nom affiché « Rôdeur des Racines », sans les deux lignes de vie) et le son de réveil adapté ; relancer le générateur comme sa fiche l'indique pour réécrire les fonctions ; vérifier par les tests du donjon ; commit à part : `Donjon Suspendu : le Rôdeur des Racines remplace le vindicateur provisoire`.
- [ ] **Step 3** : essai par Luca (ou par moi avec son accord) : réveil, morsure, charge esquivée, charge contre un pilier puis sonné et dégâts doublés, mort puis cinématique du coffre du grappin, remise à zéro (`/function atelier:suspendu_reset`), rechargement du monde.
