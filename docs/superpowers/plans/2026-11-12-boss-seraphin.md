# Boss « Séraphin » Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** ajouter au mod Grappin un boss volant, le Séraphin, avec son œuf de spawn, ses trois attaques et sa phase 2.

**Architecture :** une entité `Seraphin` (extends `Monster`, sans gravité) pilotée par des `Goal` (orbite, rayon, volée, balayage) ; les décisions chiffrées (phase, facteur de dégâts, angles de l'éventail) vivent dans une classe pure `SeraphinLogique`, testée en JUnit ; la volée est un projectile `Plume` ; le rendu client est un modèle vanilla codé en Java avec une texture générée par Pillow.

**Tech Stack :** NeoForge 21.1.216 / Minecraft 1.21.1 (inchangés), Java 21, ModDevGradle du mod Grappin, JUnit 5 (tests de la logique pure), Python + Pillow (texture).

**Spec :** `docs/superpowers/specs/2026-11-12-boss-seraphin-design.md`

## Global Constraints

- Tout se passe dans `atelier/donjon/arene` (mod `grappin`, package `fr.atelier.grappin`) ; `neo_version=21.1.216` n'est pas touché.
- Identifiants : entité `grappin:seraphin`, projectile `grappin:plume`, œuf `grappin:seraphin_spawn_egg`.
- Noms et commentaires en français, en-tête `// [claude-build]` sur chaque fichier créé, comme le reste du mod.
- Le test final est un test en jeu par Luca avec `mod/` `runClient` (client du dépôt, environnement complet), jamais l'instance Prism.
- Compiler : JDK 21 du dépôt (`test-local/outils/jdk-21.0.13+9`), `./gradlew build --console=plain -q` depuis `atelier/donjon/arene`, en Git Bash ; client fermé pendant le build ; `.claude/testing.flag` pendant build et tests.
- Nouveau jar `grappin-2.7.0.jar` copié dans `atelier/exports/` (le raccourci de test prend le plus récent).
- Un seul sujet par PR : branche `feature/boss-seraphin`, base `main`, merge décidé par Duncan. Les commits et le push ne se font que sur accord de Luca.
- Valeurs par défaut des réglages (fichier `config/grappin-common.toml`) : vie 300, dégâts du rayon 8, dégâts d'une plume 4, dégâts du balayage 6, facteur de dégâts ailes fermées 0,3, charge du rayon 30 ticks.

## Review Focus

- Difficulté « Paisible » : un `Monster` disparaît tout seul → le Séraphin doit rester (persistant, `shouldDespawnInPeaceful` faux) ou le test d'un joueur en paisible le fait disparaître sans prévenir.
- Cible morte, déconnectée ou en créatif : le boss doit en changer ou se rendormir, sans erreur ni tir dans le vide.
- Barre de vie : si le boss meurt ou est tué par commande, la barre doit disparaître chez tous les joueurs (pas de barre fantôme).
- Plusieurs joueurs : le boss cible le plus proche, et la phase 2 se calcule sur sa vie, pas sur celle d'un joueur.
- Rechargement du monde : l'état de combat (œil ouvert, charge en cours) ne doit pas rester bloqué après sauvegarde/chargement.

---

### Task 1 : Réglages et logique pure, avec JUnit

**Files :**
- Modify : `atelier/donjon/arene/build.gradle` (JUnit 5 : `testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'`, `testRuntimeOnly 'org.junit.platform:junit-platform-launcher'`, `test { useJUnitPlatform() }`)
- Modify : `atelier/donjon/arene/src/main/java/fr/atelier/grappin/Reglages.java`
- Create : `atelier/donjon/arene/src/main/java/fr/atelier/grappin/SeraphinLogique.java`
- Test : `atelier/donjon/arene/src/test/java/fr/atelier/grappin/SeraphinLogiqueTest.java`

**Interfaces :**
- Produces (`Reglages`) : `DoubleValue SERAPHIN_VIE`, `DoubleValue RAYON_DEGATS`, `DoubleValue PLUME_DEGATS`, `DoubleValue BALAYAGE_DEGATS`, `DoubleValue AILES_FERMEES` (facteur 0–1), `IntValue RAYON_CHARGE` (ticks) — section « Séraphin », valeurs par défaut de la section Global Constraints.
- Produces (`SeraphinLogique`, classe `final`, sans import Minecraft) :
  - `static int phase(float vie, float vieMax)` → 1, ou 2 quand `vie <= vieMax / 2`
  - `static double facteurDegats(boolean oeilOuvert, double facteurAilesFermees)` → 1,0 œil ouvert, sinon le facteur
  - `static int nombreRayons(int phase)` → 1 en phase 1, 2 en phase 2
  - `static int recharge(int phase, int baseTicks)` → `baseTicks` en phase 1, `baseTicks * 2 / 3` en phase 2
  - `static double[] anglesEventail(int n, double ouvertureDegres)` → `n` angles en degrés, symétriques autour de 0, de `-ouverture/2` à `+ouverture/2` ; `n = 1` → `{0}` ; `n <= 0` → tableau vide

- [ ] **Step 1 : écrire les tests qui échouent** dans `SeraphinLogiqueTest` : `phase(300f, 300f) == 1`, `phase(151f, 300f) == 1`, `phase(150f, 300f) == 2` (frontière incluse), `phase(0f, 300f) == 2` ; `facteurDegats(true, 0.3) == 1.0`, `facteurDegats(false, 0.3) == 0.3` ; `nombreRayons(1) == 1`, `nombreRayons(2) == 2` ; `recharge(1, 90) == 90`, `recharge(2, 90) == 60` ; `anglesEventail(1, 60)` égale `{0}`, `anglesEventail(0, 60)` est vide, `anglesEventail(5, 60)` vaut `{-30, -15, 0, 15, 30}` (à 1e-9 près).
- [ ] **Step 2 : lancer, vérifier l'échec** — `./gradlew test --console=plain -q` dans `atelier/donjon/arene` ; attendu : erreur de compilation (`SeraphinLogique` n'existe pas).
- [ ] **Step 3 : ajouter les réglages** dans `Reglages.java` (même style que les réglages existants, `B.comment(...).defineInRange(...)`), puis **implémenter `SeraphinLogique`** avec les signatures ci-dessus.
- [ ] **Step 4 : lancer les tests** — même commande ; attendu : tous verts (`BUILD SUCCESSFUL`, code de sortie 0 vérifié, sans `| tail`).
- [ ] **Step 5 : Commit** (sur accord de Luca) — message : « Séraphin : réglages et logique de combat testée ».

---

### Task 2 : L'entité, son œuf de spawn et sa barre de vie

**Files :**
- Create : `src/main/java/fr/atelier/grappin/Seraphin.java`
- Modify : `Grappin.java` (enregistrements), `GrappinClient.java` (rendu provisoire), `src/main/resources/assets/grappin/lang/fr_fr.json` et `en_us.json`

**Interfaces :**
- Consumes : `Reglages.SERAPHIN_VIE` ; `SeraphinLogique.phase(float, float)`.
- Produces : `Seraphin extends Monster` avec
  - `static AttributeSupplier.Builder attributs()` (vie max = `Reglages.SERAPHIN_VIE`, résistance au recul 1.0, vitesse de vol 0.6, suivi 64) ;
  - `boolean oeilOuvert()` et `void setOeilOuvert(boolean)` (donnée synchronisée) ;
  - `int phase()` (appelle `SeraphinLogique.phase(getHealth(), getMaxHealth())`).
  - `Grappin.SERAPHIN` (`DeferredHolder<EntityType<?>, EntityType<Seraphin>>`, taille 3.0 × 3.0, `MobCategory.MONSTER`, `fireImmune()`, `clientTrackingRange(10)`) et `Grappin.SERAPHIN_OEUF` (`DeferredItem<DeferredSpawnEggItem>`, couleurs `0xE8E8E8` et `0x202020`), ajouté à l'onglet « Donjons ».
- [ ] **Step 1 : enregistrer** le type d'entité, l'œuf et les attributs (`EntityAttributeCreationEvent` sur le bus du mod, classe interne dans `Grappin.java`).
- [ ] **Step 2 : implémenter `Seraphin`** : `setNoGravity(true)`, `setPersistenceRequired()`, `shouldDespawnInPeaceful()` faux, `ServerBossEvent` (nom « Séraphin », couleur blanche, barre à encoches), mise à jour à chaque tick, `startSeenByPlayer`/`stopSeenByPlayer` ajoutent/retirent le joueur de la barre, la barre est vidée à `remove`.
- [ ] **Step 3 : rendu provisoire** (rendu vanilla minimal, pour pouvoir voir l'entité avant la Task 5) et ajouter les textes : `entity.grappin.seraphin` = « Séraphin », `item.grappin.seraphin_spawn_egg` = « Œuf d'apparition de Séraphin » (+ équivalents anglais).
- [ ] **Step 4 : compiler** — `./gradlew build --console=plain -q` ; attendu : code de sortie 0.
- [ ] **Step 5 : vérification en jeu rapide** (Luca ou moi via le client du dépôt) : l'œuf est dans l'onglet « Donjons », le Séraphin apparaît, flotte sur place, la barre de vie s'affiche, `/kill` la retire. Commit (sur accord) : « Séraphin : entité, œuf et barre de vie ».

---

### Task 3 : Le projectile « Plume »

**Files :**
- Create : `src/main/java/fr/atelier/grappin/Plume.java`
- Modify : `Grappin.java`, `GrappinClient.java`, `lang/fr_fr.json`, `lang/en_us.json`

**Interfaces :**
- Consumes : `Reglages.PLUME_DEGATS`.
- Produces : `Plume extends AbstractHurtingProjectile` (`Plume(EntityType<? extends Plume>, Level)` et `Plume(Level, Seraphin tireur, Vec3 direction)`), `Grappin.PLUME` (taille 0.3 × 0.3, `MobCategory.MISC`, sans gravité, vitesse ≈ 1.2 bloc/tick, disparaît après 5 s ou au premier bloc). Inflige `Reglages.PLUME_DEGATS` aux joueurs touchés (jamais au Séraphin). Rendu : traînée de particules `END_ROD` côté client, aucun modèle.
- [ ] **Step 1 : enregistrer** le type `grappin:plume` et un rendu vide (`NoopRenderer`).
- [ ] **Step 2 : implémenter** `Plume` (dégâts sur `onHitEntity`, disparition sur `onHitBlock` et à 100 ticks, particules dans `tick()`).
- [ ] **Step 3 : compiler** (`build`, code de sortie 0). Commit (sur accord) : « Séraphin : projectile plume ».

---

### Task 4 : Les attaques, la phase 2 et les points faibles

**Files :**
- Create : `src/main/java/fr/atelier/grappin/SeraphinAttaques.java` (les `Goal` : orbite, rayon, volée, balayage)
- Modify : `Seraphin.java` (`registerGoals`, `hurt`)

**Interfaces :**
- Consumes : `Seraphin.oeilOuvert/setOeilOuvert/phase()`, `Plume(Level, Seraphin, Vec3)`, `Reglages.*`, `SeraphinLogique.*`.
- Produces :
  - `SeraphinAttaques.Orbite` : tourne autour de la cible à 12 blocs de rayon et 9 blocs au-dessus (vitesse de 0.6), la cible étant le joueur le plus proche (`NearestAttackableTargetGoal<Player>` hors créatif/spectateur) ;
  - `SeraphinAttaques.Rayon` : ouvre l'œil et prévient pendant `Reglages.RAYON_CHARGE` ticks (particules et son), tire ensuite `SeraphinLogique.nombreRayons(phase())` rayons (le second décalé de 8°) ; un joueur dont la boîte est à moins de 1 bloc du segment reçoit `Reglages.RAYON_DEGATS` ; referme l'œil à la fin ;
  - `SeraphinAttaques.Volee` : tire une plume par angle de `SeraphinLogique.anglesEventail(5, 60)` autour de la direction vers la cible ;
  - `SeraphinAttaques.Balayage` : si un joueur est à moins de 6 blocs, repousse tous les joueurs de ce rayon (vitesse de recul 1.6, un peu vers le haut) et leur inflige `Reglages.BALAYAGE_DEGATS` ;
  - délais entre attaques : `SeraphinLogique.recharge(phase(), 90)` ticks ;
  - `Seraphin.hurt(DamageSource, float)` multiplie les dégâts par `SeraphinLogique.facteurDegats(oeilOuvert(), Reglages.AILES_FERMEES.get())`.
- [ ] **Step 1 : implémenter les quatre `Goal`** (un seul `Goal` d'attaque actif à la fois, choix pondéré entre rayon et volée ; balayage prioritaire quand un joueur est proche) et les brancher dans `Seraphin.registerGoals`.
- [ ] **Step 2 : implémenter `hurt`** avec le facteur de dégâts.
- [ ] **Step 3 : sauvegarde** — `addAdditionalSaveData`/`readAdditionalSaveData` ne conservent pas d'état de combat : au chargement, l'œil est refermé et le délai remis à zéro (cas « chargement du monde » de Review Focus).
- [ ] **Step 4 : compiler** (code de sortie 0) puis **test en jeu** (Luca) : les trois attaques se déclenchent, la barre baisse, la phase 2 arrive sous la moitié, la mort ne laisse pas de barre. Commit (sur accord) : « Séraphin : attaques, phase 2 et points faibles ».

---

### Task 5 : Le modèle, la texture et le rendu

**Files :**
- Create : `src/main/java/fr/atelier/grappin/SeraphinModele.java`, `SeraphinRendu.java`
- Create : `atelier/scripts/texture_seraphin.py` (génère `assets/grappin/textures/entity/seraphin.png`)
- Modify : `GrappinClient.java` (enregistrement du rendu et de la couche de modèle), supprimer le rendu provisoire de la Task 2

**Interfaces :**
- Consumes : `Seraphin.oeilOuvert()`.
- Produces :
  - `SeraphinModele extends EntityModel<Seraphin>` (`static LayerDefinition creerCouche()`, `static final ModelLayerLocation COUCHE`) : un œil central (cube 8×8×8), trois paires d'ailes à plumes (deux dressées vers le haut, une grande qui retombe) et une paire d'ailes de chauve-souris sur les côtés ;
  - `setupAnim` : battement des ailes en sinus (lent normalement, deux fois plus vite œil ouvert) et pupille qui suit `headPitch`/`netHeadYaw` ;
  - `SeraphinRendu extends MobRenderer<Seraphin, SeraphinModele>` (échelle 1.5, œil lumineux : pleine luminosité quand `oeilOuvert()`) ;
  - `texture_seraphin.py` produit une PNG 128×128 en gravure noir et blanc (traits fins, plumes hachurées, œil avec pupille) ; l'exécuter avec `test-local/venv/Scripts/python.exe`.
- [ ] **Step 1 : générer la texture** et la vérifier visuellement (ouvrir le PNG, comparer à l'image de référence de Luca).
- [ ] **Step 2 : écrire le modèle et le rendu**, enregistrer la couche (`EntityRenderersEvent.RegisterLayerDefinitions`) et le rendu (remplace le rendu provisoire).
- [ ] **Step 3 : compiler** (code de sortie 0).
- [ ] **Step 4 : test en jeu** (Luca) : le Séraphin ressemble à la référence (œil, ailes), les ailes battent, la pupille suit le joueur, l'œil brille pendant le rayon. Itérer sur les proportions selon son retour. Commit (sur accord) : « Séraphin : modèle, texture et rendu ».

---

### Task 6 : Version, jar et PR

**Files :**
- Modify : `atelier/donjon/arene/gradle.properties` (`mod_version=2.7.0`)
- Create : `atelier/exports/grappin-2.7.0.jar`

- [ ] **Step 1 : passer `mod_version` à 2.7.0**, `./gradlew build --console=plain -q` (JDK 21 du dépôt, client fermé, `.claude/testing.flag` pendant le build), puis copier `build/libs/grappin-2.7.0.jar` dans `atelier/exports/`.
- [ ] **Step 2 : test final en jeu** par Luca avec le client du dépôt et le nouveau jar : œuf, combat complet, mort, aucune erreur dans `mod/run/client/logs/latest.log`.
- [ ] **Step 3 : commit, push de `feature/boss-seraphin` et PR vers `main`** (sur accord de Luca) ; description française avec le pourquoi, le test fait et ce qui n'est pas fait (branchement au donjon). Le merge est décidé par Duncan.
