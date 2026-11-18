# Boss « Gardien des Racines » Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** ajouter au mod « Donjon / Arène » un golem d'arbre colossal à pièces de collision, branché à l'arène du temple (donjon 1), dont les points faibles ne se touchent qu'en se balançant au grappin.

**Architecture :** une entité `Golem` (extends `Monster`, multipart NeoForge) enfoncée dans la lave, avec cinq `GolemPiece` (`PartEntity`) placées sur sa surface et tournant avec lui ; une seule pièce est « ouverte » à la fois, choisie du côté opposé au joueur le plus proche par `GolemLogique` (logique pure, JUnit). Le générateur du temple décale les plateformes trop proches de son empreinte et ajoute les blocs de commande d'entrée, d'arc et de mort (même schéma que le Séraphin du donjon 3).

**Tech Stack :** NeoForge 21.1.216 / Minecraft 1.21.1 (inchangés), Java 21, JUnit 5, Python (générateur du temple, vérificateur, texture en Pillow).

**Spec :** `docs/superpowers/specs/2026-11-14-boss-golem-design.md`

## Global Constraints

- Code du boss dans `atelier/donjon/arene` (package `fr.atelier.grappin`), id du mod `grappin` ; `neo_version=21.1.216` n'est pas touché ; en-tête `// [claude-build]` sur chaque fichier créé ; noms et commentaires en français.
- Identifiants : entité `grappin:golem`, projectile `grappin:graine`, œuf `grappin:golem_spawn_egg`, tag de fonctions `grappin:golem_mort`.
- Dégâts « sans cause » (`damageSources().magic()` sans entité) pour combattre à toutes les difficultés, Paisible comprise.
- Valeurs par défaut des réglages (`config/grappin-common.toml`) : vie 240, facteur point faible 3, facteur corps 0,05, ouverture 160 ticks (8 s), tour de rotation 120 ticks pour un demi-tour, dégâts des graines 10, zone de l'explosion 3 blocs, cadence des graines 70 ticks.
- Compiler : JDK 21 du dépôt (`test-local/outils/jdk-21.0.13+9`), `./gradlew build --console=plain -q` depuis `atelier/donjon/arene` (Git Bash), client fermé, `.claude/testing.flag` pendant build et tests ; ne jamais enchaîner avec `| tail` (cacher le code de sortie).
- Nouveau jar `grappin-2.8.0.jar` copié dans `atelier/exports/`.
- Branche `feature/boss-golem` (partie de `feature/boss-seraphin`), une PR dépendant de la #19. Commit et push seulement sur accord de Luca ; le merge est décidé par Duncan.
- Le générateur du temple ne suppose aucun état local : il crée ce dont il a besoin ; le monde « Donjon 1 » est celui du temple.

## Review Focus

- Un seul joueur derrière le golem ou aucune pièce à plus de 90° : le boss ne doit ni se figer ni ouvrir une pièce visible de face ; il prend la pièce la plus opposée (test `GolemLogique`).
- Changement de joueur le plus proche pendant qu'une pièce est ouverte : elle ne change pas de côté avant de se refermer (test d'état dans `GolemComportement`).
- Mort pendant l'émergence ou `/kill` : barre de vie retirée, pièces retirées, tag de mort appelé une seule fois, sortie ouverte seulement si l'état est « boss invoqué ».
- Chargement du monde au milieu du combat : pièces fermées, émergence considérée terminée, pas de boss figé sous la lave.
- Les plateformes déplacées gardent au moins 6 blocs entre elles et chacune reste rejoignable au grappin depuis au moins deux autres (vérificateur du temple) ; la zone de chute ne touche aucune plateforme.

---

### Task 1 : Réglages et logique pure, avec JUnit

**Files :**
- Modify : `atelier/donjon/arene/src/main/java/fr/atelier/grappin/Reglages.java`
- Create : `atelier/donjon/arene/src/main/java/fr/atelier/grappin/GolemLogique.java`
- Test : `atelier/donjon/arene/src/test/java/fr/atelier/grappin/GolemLogiqueTest.java`

**Interfaces :**
- Produces (`Reglages`) : `DoubleValue GOLEM_VIE`, `DoubleValue GOLEM_FACTEUR_POINT`, `DoubleValue GOLEM_FACTEUR_CORPS`, `IntValue GOLEM_OUVERTURE`, `IntValue GOLEM_DEMI_TOUR`, `DoubleValue GRAINE_DEGATS`, `DoubleValue GRAINE_ZONE`, `IntValue GRAINE_CADENCE` (valeurs par défaut des Global Constraints).
- Produces (`GolemLogique`, `final`, sans import Minecraft) :
  - `static double ecartAngulaire(double a, double b)` → écart en degrés, dans [0, 180]
  - `static double tournerVers(double actuel, double voulu, double maxDegres)` → nouvel angle, par le plus court chemin, d'au plus `maxDegres`, ramené dans (-180, 180]
  - `static int choisirPiece(double[] anglesMonde, double angleCible, int derniere, java.util.function.IntUnaryOperator tirage)` → indice d'une pièce à plus de 90° de `angleCible`, différente de `derniere` s'il y en a une autre ; si aucune n'est à plus de 90°, la plus opposée ; `-1` si le tableau est vide ; `tirage.applyAsInt(n)` renvoie un entier dans [0, n)
  - `static double facteurDegats(boolean pieceOuverte, double facteurPiece, double facteurCorps)`
  - `static int dureeOuverture(int phase, int baseTicks)` → `baseTicks` en phase 1, `baseTicks * 3 / 4` en phase 2
  - `static int cadenceGraines(int phase, int baseTicks)` → `baseTicks` en phase 1, `baseTicks / 2` en phase 2
  La phase (1 ou 2) vient de `SeraphinLogique.phase`, qu'on réutilise sans la dupliquer.

- [ ] **Step 1 : écrire les tests qui échouent** dans `GolemLogiqueTest` : `ecartAngulaire(10, 350) == 20`, `ecartAngulaire(0, 180) == 180`, `ecartAngulaire(-90, 90) == 180` ; `tournerVers(0, 90, 30) == 30`, `tournerVers(170, -170, 30) == -160` (par le plus court), `tournerVers(10, 15, 30) == 15` ; `choisirPiece` avec angles `{0, 180, 90}` et cible `0` renvoie l'indice 1 (seule pièce à plus de 90°) ; avec `{0, 170, -170}`, cible `0`, dernière `1` renvoie 2 ; avec `{0, 20, -30}` (aucune éligible) renvoie l'indice de l'écart maximal ; tableau vide renvoie `-1` ; `facteurDegats(true, 3, 0.05) == 3`, `facteurDegats(false, 3, 0.05) == 0.05` ; `dureeOuverture(1, 160) == 160`, `dureeOuverture(2, 160) == 120` ; `cadenceGraines(1, 70) == 70`, `cadenceGraines(2, 70) == 35`.
- [ ] **Step 2 : lancer, vérifier l'échec** — `./gradlew test --console=plain -q` ; attendu : erreur de compilation (`GolemLogique` n'existe pas).
- [ ] **Step 3 : ajouter les réglages** (même style que ceux du Séraphin, section « Gardien ») puis **implémenter `GolemLogique`** avec les signatures ci-dessus.
- [ ] **Step 4 : lancer les tests** — même commande ; attendu : `BUILD SUCCESSFUL`, code de sortie 0 lu, 8 tests au moins verts (en plus de ceux du Séraphin).

---

### Task 2 : L'entité, ses pièces, l'œuf et la barre de vie

**Files :**
- Create : `src/main/java/fr/atelier/grappin/Golem.java`, `GolemPiece.java`
- Modify : `Grappin.java`, `GrappinClient.java` (rendu provisoire), `src/main/resources/assets/grappin/lang/fr_fr.json` et `en_us.json`, create `src/main/resources/assets/grappin/models/item/golem_spawn_egg.json` (parent `minecraft:item/template_spawn_egg`)

**Interfaces :**
- Consumes : `Reglages.GOLEM_VIE`, `SeraphinLogique.phase(float, float)`.
- Produces :
  - `Golem extends Monster` (multipart : `isMultipartEntity()` vrai, `getParts()` renvoie les 5 `GolemPiece`), `static AttributeSupplier.Builder attributs()`, `int pieceOuverte()` (indice de la pièce ouverte, `-1` si aucune, donnée synchronisée), `void ouvrirPiece(int indice)`, `void fermerPieces()`, `int emergence()` (ticks restants d'émergence, `0` = terminé), `boolean deuxiemePhase()` (donnée synchronisée), `DamageSource sourceDegats()`, `static final String[] NOMS_PIECES = {"masque", "plastron", "poing", "dos_g", "dos_d"}`, `static final double[] AZIMUTS = {0, 0, -50, 150, -150}` (degrés par rapport à l'avant du golem), `static final double[] RAYONS = {6.5, 7.5, 11, 6.5, 6.5}`, `static final double[] HAUTEURS = {34, 26, 40, 30, 22}` (au-dessus de la surface de lave) ;
  - `GolemPiece extends PartEntity<Golem>` : boîte 3 × 3, `isPickable()` vrai seulement quand sa pièce est ouverte, `hurt(DamageSource, float)` transmet au golem avec le facteur de pièce ouverte ;
  - `Grappin.GOLEM` (taille 12 × 40, `MobCategory.MONSTER`, `fireImmune()`, `clientTrackingRange(16)`), `Grappin.GOLEM_OEUF` (couleurs `0x6B4423` et `0x9C1B1B`) dans l'onglet « Donjons » ; attributs enregistrés comme ceux du Séraphin.
- [ ] **Step 1 : enregistrer** le type, l'œuf, les attributs et un rendu provisoire (`NoopRenderer`) ; textes `entity.grappin.golem` = « Gardien des Racines » (+ anglais).
- [ ] **Step 2 : implémenter `Golem`** : sans gravité ni recul (`KNOCKBACK_RESISTANCE` 1), persistant, ne disparaît pas en Paisible, barre de vie de boss verte retirée dans `remove`/`stopSeenByPlayer`, vie lue dans `Reglages.GOLEM_VIE`, boîte de collision du corps qui prend `facteurCorps` des dégâts, invulnérable tant que `emergence() > 0`, `getBoundingBoxForCulling()` élargi de 16 blocs. Les pièces sont repositionnées à chaque tick d'après le lacet du golem (`AZIMUTS`, `RAYONS`, `HAUTEURS`).
- [ ] **Step 3 : implémenter `GolemPiece`** : transmet les dégâts reçus à `golem.hurt(source, montant × GOLEM_FACTEUR_POINT)` quand elle est ouverte, ignore tout sinon.
- [ ] **Step 4 : compiler** (`build`, code de sortie 0) puis **vérifier en jeu rapidement** (Luca) : l'œuf est dans « Donjons », le golem apparaît, la barre de vie s'affiche, `/kill @e[type=grappin:golem]` la retire.

---

### Task 3 : Comportement (rotation, points faibles, graines, phase 2, mort)

**Files :**
- Create : `src/main/java/fr/atelier/grappin/GolemComportement.java`, `Graine.java`
- Modify : `Golem.java`, `Grappin.java`, `GrappinClient.java`, lang

**Interfaces :**
- Consumes : `Golem.pieceOuverte/ouvrirPiece/fermerPieces/emergence/phase()`, `GolemLogique.*`, `Reglages.*`.
- Produces :
  - `GolemComportement.Tourner` (goal) : fait pivoter le golem vers le joueur le plus proche à `180 / Reglages.GOLEM_DEMI_TOUR` degrés par tick via `GolemLogique.tournerVers` ;
  - `GolemComportement.Ouvrir` (goal) : toutes les `dureeOuverture(phase, GOLEM_OUVERTURE)` ticks (avec 40 ticks de pause fermée), appelle `GolemLogique.choisirPiece` sur les angles monde des pièces et ouvre la pièce choisie ; une pièce ouverte ne change pas de côté avant de se refermer ; la cible est réévaluée toutes les 40 ticks ;
  - `GolemComportement.Lancer` (goal) : lance une `Graine` du poing toutes les `cadenceGraines(phase, GRAINE_CADENCE)` ticks vers un joueur ; deux graines en éventail en phase 2 ;
  - `Graine extends AbstractHurtingProjectile` (`Graine(Level, Golem, Vec3 direction)`), vitesse constante, explose à l'impact ou après 100 ticks : dégâts `Reglages.GRAINE_DEGATS` aux joueurs dans `GRAINE_ZONE` blocs (source `golem.sourceDegats()`), aucun bloc détruit ; `Grappin.GRAINE` (`MobCategory.MISC`, `noSave()`) ;
  - phase 2 : annonce (titre, rugissement, particules), barre rouge, donnée `deuxiemePhase` ;
  - mort : `Golem.die(DamageSource)` appelle les fonctions du tag `grappin:golem_mort` (même mécanisme que le tag du Séraphin) ; l'émergence (`emergence()` de 100 ticks) fait monter le golem depuis sous la lave sans qu'il puisse être touché.
- [ ] **Step 1 : écrire les goals et le projectile**, les brancher dans `Golem.registerGoals` ; `Graine` : mettre `accelerationPower = 0` et fixer la vitesse par `setDeltaMovement` (le constructeur vanilla ignore sinon la vitesse).
- [ ] **Step 2 : régler les cas limites** : plus de joueur → pièces fermées, plus de graines ; cible créatif ou spectateur ignorée ; état de combat non sauvegardé (au chargement : pièces fermées, émergence terminée).
- [ ] **Step 3 : compiler** (code de sortie 0) puis **test en jeu** (Luca) : le golem tourne, une pièce s'ouvre toujours du côté opposé, les graines partent du poing, une flèche sur la pièce ouverte fait de gros dégâts, ailleurs presque rien, la phase 2 s'annonce, la mort retire la barre.

---

### Task 4 : Modèle, texture et rendu

**Files :**
- Create : `src/main/java/fr/atelier/grappin/GolemModele.java`, `GolemRendu.java`, `atelier/scripts/texture_golem.py` (génère `assets/grappin/textures/entity/golem.png`)
- Modify : `GrappinClient.java` (enregistrement du rendu et de la couche de modèle ; supprime le rendu provisoire)

**Interfaces :**
- Consumes : `Golem.pieceOuverte()`, `Golem.deuxiemePhase()`, `Golem.NOMS_PIECES`.
- Produces : `GolemModele extends EntityModel<Golem>` (`static LayerDefinition creerCouche()`, `static final ModelLayerLocation COUCHE`) d'après la figurine : torse et bras de bois écorcé, deux cornes de branches, masque et plastron rouges, poing droit rouge à corne, jambes en rondins avec cernes, mousse verte ; dessiné à l'échelle 2 comme le Séraphin puis réduit par `GolemRendu` ; chaque pièce faible est un cube rouge recouvert d'une plaque de bois qui s'efface quand la pièce est ouverte (rouge lumineux, `getBlockLightLevel` à 15) ; bras qui bougent, lancer du poing ; `GolemRendu extends MobRenderer<Golem, GolemModele>`, ombre 0, échelle réglée pour que les cotes du modèle correspondent à celles de `Golem` (40 blocs de haut).
- [ ] **Step 1 : générer la texture** (`test-local\venv\Scripts\python.exe atelier\scripts\texture_golem.py`) : écorce brune striée, rouge du masque et du poing, cernes des rondins, mousse ; vérifier l'image.
- [ ] **Step 2 : écrire modèle et rendu**, enregistrer la couche et le rendu.
- [ ] **Step 3 : compiler** (code de sortie 0) puis **test en jeu** (Luca) : la silhouette ressemble à la figurine, la pièce ouverte brille en rouge, les bras bougent ; itérer sur les proportions avec une capture d'écran.

---

### Task 5 : La salle (empreinte du golem et plateformes)

**Files :**
- Modify : `atelier/scripts/donjon_temple.py` (`PLATS`, `PILIERS`, constantes du golem, export JSON), `atelier/scripts/verif_donjon_temple.py`
- Modify : `atelier/exports/temple.nbt`, `temple_cibles.json` (régénérés)

**Interfaces :**
- Produces (`donjon_temple.py`) : constantes `GX, GZ` (centre du golem en coordonnées du gabarit), `G_SURFACE = 8`, `G_RAYON_CORPS = 7`, `G_RAYON_BRAS = 12` ; l'export `temple_cibles.json` contient `golem: {centre: [GX, G_SURFACE, GZ], rayon_corps, rayon_bras, pieces: {nom: [x, y, z]}}` ; une assertion à la génération vérifie que chaque plateforme reste à **au moins 4 blocs** de la tige du golem aux hauteurs de son corps (rayon corps de y 8 à 54, rayon bras de y 30 à 54).
- [ ] **Step 1 : écrire le contrôle qui échoue** — dans `verif_donjon_temple.py`, une vérification « empreinte du golem » : pour chaque plateforme, distance horizontale minimale entre ses cases et `(GX, GZ)` moins le rayon applicable à sa hauteur, `>= 4` ; lancer `test-local\venv\Scripts\python.exe atelier\scripts\verif_donjon_temple.py` ; attendu : échec sur P2, P5 et au moins une autre plateforme.
- [ ] **Step 2 : choisir `GX, GZ`** près du point libre le plus proche de (47, 165) puis **déplacer ou retailler** les plateformes en échec dans `PLATS` (en gardant l'assertion existante `gap >= 6` entre plateformes), en ajustant `EDGES` seulement si une liaison disparaît ; les ancrages sont recalculés par le générateur ; `PILIERS` et `colonne_libre` doivent rester valides.
- [ ] **Step 3 : régénérer** (`donjon_temple.py`), puis relancer `verif_donjon_temple.py` ; attendu : toutes les vérifications existantes passent (chaque plateforme rejoignable depuis au moins deux autres au grappin, sortie atteignable seulement avec le grappin, zone de chute libre) **et** la nouvelle vérification d'empreinte.
- [ ] **Step 4 : contrôle visuel** — regarder la vue générée (`temple_vue`/plan si le script en produit) ou la salle en jeu ; vérifier à la main que chaque tour complet autour du golem reste possible au grappin (anti-objectif de la spec).

---

### Task 6 : Intégration au donjon (déclenchement, arc, fonctions, remise à zéro)

**Files :**
- Modify : `atelier/scripts/donjon_temple.py` (blocs de commande et fonctions), `atelier/scripts/verif_donjon_temple.py` si une vérification de capacité des chaînes existe
- Modify : `atelier/exports/temple.nbt` (régénéré)

**Interfaces :**
- Produces : variable de score `#boss` (objectif `temple`, 0 → 1 → 2 → 3 → 4) ; fonctions `temple_boss_annonce`, `temple_boss_invoque`, `temple_boss_mort`, `temple_boss_vaincu` ; tag `data/grappin/tags/function/golem_mort.json` listant `atelier:temple_boss_mort` ; fonctions existantes réutilisées : `temple_ouvrir_sortie_boss`, `temple_fermer_sortie_boss`, `temple_reset`, `temple_chute_boss`. Le golem apparaît au centre `(GX, G_SURFACE, GZ)` (coordonnées monde = gabarit + origine `(150, -61, -65)`).
- [ ] **Step 1 : écrire le contrôle qui échoue** — assertion à la génération : `len(CMD) <= 32 * len(CHAINES)` avec les nouvelles commandes ; lancer le générateur ; attendu : échec si les chaînes sont pleines (alors ajouter une chaîne ou déplacer des commandes, comme pour les autres chaînes).
- [ ] **Step 2 : ajouter les commandes** : à l'entrée de la zone de la plateforme de départ, si `#boss` vaut 0, appeler `temple_boss_annonce` (met `#boss` à 1, titre « Le Gardien se réveille... », son, planifie `temple_boss_invoque` dans 100 ticks) ; `temple_boss_invoque` (`positioned` d'abord, `execute unless entity @e[type=grappin:golem,distance=..150]` puis `summon grappin:golem`, `#boss` à 2) ; `temple_boss_mort` (appelée par le tag, `execute if score #boss temple matches 2 run function atelier:temple_boss_vaincu`) ; `temple_boss_vaincu` (`#boss` à 3, ouvre la sortie, titre vert). Donner l'arc (`minecraft:bow` Infinity, incassable) et une flèche à tout joueur de la salle qui n'en a pas, par un bloc de commande qui s'exécute en permanence (même schéma que le don de la Paravoile du donjon 3).
- [ ] **Step 3 : écrire le tag** `golem_mort.json` dans le datapack du monde et brancher `temple_reset` : tue `@e[type=grappin:golem]`, `schedule clear` de l'invocation, remet `#boss` à 0 **avant** le `kill` pour que la mort ne rouvre pas la sortie.
- [ ] **Step 4 : régénérer et contrôler** — `donjon_temple.py` sans erreur, `verif_donjon_temple.py` sans erreur ; relire les fichiers `.mcfunction` générés (une commande par ligne, aucune ligne collée) et le tag.

---

### Task 7 : Version, jar, test en jeu et PR

**Files :**
- Modify : `atelier/donjon/arene/gradle.properties` (`mod_version=2.8.0`)
- Create : `atelier/exports/grappin-2.8.0.jar`

- [ ] **Step 1 : passer `mod_version` à 2.8.0**, `./gradlew build --console=plain -q` (code de sortie 0, tests verts), copier `build/libs/grappin-2.8.0.jar` dans `atelier/exports/`.
- [ ] **Step 2 : test final en jeu** par Luca avec le client du dépôt dans « Donjon 1 » (`/reload`, `/function atelier:temple_pose`, attendre le message vert) : entrée dans la salle, arc donné, réveil, émergence, rotation, pièces ouvertes du côté opposé, contournement au grappin, graines, phase 2, mort, sortie ouverte, remise à zéro ; aucune erreur dans `mod/run/client/logs/latest.log`.
- [ ] **Step 3 : relecture finale indépendante** de toute la branche sur le Review Focus, correction des points critiques et importants (chacun avec le test qui échoue d'abord quand c'est testable).
- [ ] **Step 4 : commit, push de `feature/boss-golem` et PR** contre `feature/boss-seraphin` (sur accord de Luca) ; description française avec le pourquoi, le test fait, ce qui ne l'est pas, et la dépendance à la #19. Le merge est décidé par Duncan.
