# Boss « Léviathan » Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** ajouter au mod « Donjon / Arène » un serpent de mer multipart (le Léviathan) qui combat sous l'eau, et refaire la salle du boss du Donjon 2 en salle noyée à cloches d'air, avec les cloches attaquées en phase 2.

**Architecture :** une entité `Leviathan` (extends `Monster`, sans gravité, respire sous l'eau) dont les dix pièces de collision (`PartEntity` : huit anneaux, la queue, la perle) suivent l'historique des positions de la tête, recalculé à l'identique côté client et serveur ; la logique chiffrée vit dans `LeviathanLogique` (pure, JUnit) ; les cloches de phase 2 sont pilotées par les blocs de commande du donjon (`donjon_aqua_fin.py`), pas par le mod.

**Tech Stack :** NeoForge 21.1.216 / Minecraft 1.21.1 (inchangés), Java 21, JUnit 5, Python (générateur du Donjon 2, vérificateur, texture en Pillow).

**Spec :** `docs/superpowers/specs/2026-11-14-boss-leviathan-design.md`

## Global Constraints

- Code du boss dans `atelier/donjon/arene` (package `fr.atelier.grappin`), id du mod `grappin` ; `neo_version=21.1.216` n'est pas touché ; en-tête `// [claude-build]` sur chaque fichier créé ; noms et commentaires en français.
- Identifiants : entité `grappin:leviathan`, œuf `grappin:leviathan_spawn_egg`, tag de fonctions `grappin:leviathan_mort`.
- Dégâts « sans cause » (`damageSources().magic()` sans entité), `canAttack` surchargé, `removeWhenFarAway` faux, `requiresCustomPersistence` vrai, `isInvulnerableTo` vrai pour le feu et l'étouffement.
- Valeurs par défaut des réglages (`config/grappin-common.toml`, vie plafonnée à 1024) : vie 150, perle exposée 120 ticks (6 s), facteur du corps 0,15, facteur de la perle 3, morsure 8, rayon 12, coup de queue 6, rayon préparé 40 ticks (2 s), cloche inondée 400 ticks (20 s).
- Valeurs du combat (dans le code, pas dans la config) : tête 5 × 5, anneaux 4 × 4 (8), queue 3 × 3, perle 2,5 × 2,5 ; pas de l'historique : 6 ticks par section ; portée de détection 128 blocs.
- Compiler : JDK 21 du dépôt (`test-local/outils/jdk-21.0.13+9`), `./gradlew build --console=plain -q` depuis `atelier/donjon/arene` (Git Bash), client fermé, `.claude/testing.flag` pendant build et tests ; ne jamais enchaîner avec `| tail` (cache le code de sortie).
- Nouveau jar `grappin-2.9.0.jar` copié dans `atelier/exports/`.
- Branche `feature/boss-leviathan` (partie de `feature/boss-golem`), une PR dépendant de la #21. Commit et push seulement sur accord de Luca ; le merge est décidé par Duncan.
- Le générateur du Donjon 2 ne suppose aucun état local (il crée dossiers et `pack.mcmeta`) ; toute l'eau reste confinée et contrôlée par le générateur ; au plus la moitié des cloches de la salle est inondée à la fois.
- Le monde du Donjon 2 est « Donjon 2 » ; le nom interne des fonctions reste `aqua_*`.

## Review Focus

- Serpent coincé contre un pilier ou un mur (tête immobile) : il doit se dégager (test `LeviathanLogique.estCoince`).
- Toutes les cloches de la salle inondées en même temps : jamais plus de la moitié (assertion du générateur et plafond dans les commandes).
- Mort pendant l'étourdissement ou `/kill` : barre retirée, tag appelé une seule fois, cloches rétablies à la remise à zéro, sortie ouverte seulement si `#boss` vaut 2.
- Chargement du monde au milieu du combat : perle fermée, historique reconstruit à la position de la tête, pas de serpent figé ni de pièces au loin.
- Eau qui fuit : le vérificateur du Donjon 2 doit rester vert (eau confinée) après la salle noyée et le couloir de sortie noyé.

---

### Task 1 : Réglages et logique pure, avec JUnit

**Files :**
- Modify : `atelier/donjon/arene/src/main/java/fr/atelier/grappin/Reglages.java`
- Create : `.../LeviathanLogique.java`
- Test : `atelier/donjon/arene/src/test/java/fr/atelier/grappin/LeviathanLogiqueTest.java`

**Interfaces :**
- Produces (`Reglages`) : `DoubleValue LEVIATHAN_VIE`, `DoubleValue LEVIATHAN_FACTEUR_PERLE`, `DoubleValue LEVIATHAN_FACTEUR_CORPS`, `IntValue LEVIATHAN_PERLE` (ticks), `DoubleValue MORSURE_DEGATS`, `DoubleValue RAYON_LEVIATHAN_DEGATS`, `DoubleValue QUEUE_DEGATS`, `IntValue RAYON_LEVIATHAN_CHARGE` (ticks) ; valeurs par défaut de Global Constraints.
- Produces (`LeviathanLogique`, `final`, sans import Minecraft) :
  - `enum Attaque { MORSURE, RAYON, QUEUE, PLONGEE }`
  - `static Attaque choisirAttaque(int phase, boolean joueurProcheQueue, java.util.function.IntUnaryOperator tirage)` → `QUEUE` si `joueurProcheQueue` ; sinon `tirage.applyAsInt(100)` : en phase 1, `< 55` → `MORSURE`, sinon `RAYON` ; en phase 2, `< 35` → `MORSURE`, `< 65` → `RAYON`, sinon `PLONGEE`
  - `static boolean perleExposee(int ticksDepuisRayon, int dureeExposition)` → vrai si `0 <= ticksDepuisRayon < dureeExposition`
  - `static int indiceHistorique(int section, int pas, int taille)` → `Math.min(section * pas, taille - 1)`, `0` si `taille <= 0`
  - `static boolean estCoince(double deplacement, int ticks)` → vrai si `ticks >= 40` et `deplacement < 1.0` (déplacement total de la tête sur ces ticks, en blocs)
  - `static int cadence(int phase, int baseTicks)` → `baseTicks` en phase 1, `baseTicks * 2 / 3` en phase 2
  La phase (1 ou 2) vient de `SeraphinLogique.phase`, et le facteur de dégâts de `GolemLogique.facteurDegats(boolean, double, double)` : on réutilise sans dupliquer.

- [ ] **Step 1 : écrire les tests qui échouent** dans `LeviathanLogiqueTest` : `choisirAttaque(1, true, n -> 99) == QUEUE` ; en phase 1 avec `n -> 10` → `MORSURE`, `n -> 54` → `MORSURE`, `n -> 55` → `RAYON`, `n -> 99` → `RAYON` ; en phase 2 `n -> 34` → `MORSURE`, `n -> 35` → `RAYON`, `n -> 64` → `RAYON`, `n -> 65` → `PLONGEE` ; `perleExposee(0, 120)`, `perleExposee(119, 120)` vrais, `perleExposee(120, 120)` et `perleExposee(-1, 120)` faux ; `indiceHistorique(3, 6, 100) == 18`, `indiceHistorique(9, 6, 20) == 19`, `indiceHistorique(2, 6, 0) == 0` ; `estCoince(0.5, 40)` vrai, `estCoince(0.5, 39)` faux, `estCoince(2.0, 100)` faux ; `cadence(1, 90) == 90`, `cadence(2, 90) == 60`.
- [ ] **Step 2 : lancer, vérifier l'échec** — `./gradlew test --console=plain -q` ; attendu : erreur de compilation (`LeviathanLogique` n'existe pas).
- [ ] **Step 3 : ajouter les réglages** (même style que ceux du golem, section « Léviathan ») puis **implémenter `LeviathanLogique`**.
- [ ] **Step 4 : lancer les tests** — attendu : `BUILD SUCCESSFUL`, code de sortie 0 lu, tous les tests verts (ceux du Séraphin et du golem aussi).

---

### Task 2 : L'entité, ses pièces, l'œuf, la barre de vie

**Files :**
- Create : `.../Leviathan.java`, `.../LeviathanPiece.java`
- Modify : `Grappin.java`, `GrappinClient.java` (rendu provisoire), `lang/fr_fr.json`, `lang/en_us.json`, create `models/item/leviathan_spawn_egg.json` (parent `minecraft:item/template_spawn_egg`)

**Interfaces :**
- Consumes : `Reglages.LEVIATHAN_VIE`, `SeraphinLogique.phase(float, float)`, `LeviathanLogique.indiceHistorique`.
- Produces :
  - `Leviathan extends Monster`, multipart (`isMultipartEntity()` vrai, `getParts()` renvoie 10 `LeviathanPiece`), `static AttributeSupplier.Builder attributs()` (vie max `Reglages.LEVIATHAN_VIE`, résistance au recul 1.0, vitesse de nage 0.9, suivi 128) ;
  - `static final int ANNEAUX = 8` ; indices des pièces : `0..7` anneaux, `8` queue, `9` perle (`static final int QUEUE = 8, PERLE = 9`) ; `static final float[] TAILLES = {4,4,4,4,4,4,4,4,3,2.5F}` ;
  - `boolean perleOuverte()` (donnée synchronisée), `void ouvrirPerle()`, `void fermerPerle()`, `boolean deuxiemePhase()` (synchronisée), `int phase()`, `DamageSource sourceDegats()`, `Vec3 positionSection(int section)` (position monde de la section `section`, 0 = premier anneau, d'après l'historique : `historique[LeviathanLogique.indiceHistorique(section + 1, 6, historique.size())]`), `GolemLogique`-style `blesserParPiece(DamageSource, float, int)` (transmet à `hurt` avec le facteur selon la pièce) ;
  - l'historique des positions de la tête : une liste de `Vec3`, la plus récente en tête, 120 éléments au plus, mise à jour chaque tick **côté client et serveur** (dans `aiStep`) ;
  - `Grappin.LEVIATHAN` (`EntityType<Leviathan>`, taille 5 × 5, `MobCategory.MONSTER`, `fireImmune()`, `clientTrackingRange(16)`) et `Grappin.LEVIATHAN_OEUF` (couleurs `0x1B6B6B` et `0xC9B458`) dans l'onglet « Donjons ».
  - `LeviathanPiece extends PartEntity<Leviathan>` : boîte carrée de `TAILLES[indice]`, `isPickable()` toujours vrai pour les anneaux et la queue, vrai pour la perle seulement quand elle est ouverte, `hurt` transmet au serpent (corps : facteur `LEVIATHAN_FACTEUR_CORPS` ; perle ouverte : facteur `LEVIATHAN_FACTEUR_PERLE`), aucun dégât de contact.
- [ ] **Step 1 : enregistrer** le type, l'œuf, les attributs et un rendu provisoire (`NoopRenderer`) ; textes `entity.grappin.leviathan` = « Léviathan » (+ anglais).
- [ ] **Step 2 : implémenter `Leviathan`** : sans gravité, `canBreatheUnderwater()` vrai, `isPushedByFluid()` faux, `fireImmune`, persistant (`removeWhenFarAway` faux, `requiresCustomPersistence` vrai), jamais de disparition en Paisible, barre de vie de boss (bleu, retirée dans `remove`, à zéro dans `die`), boîte de culling élargie de 20 blocs ; identifiants des pièces réservés comme pour `Golem` (`ENTITY_COUNTER`, `setId`) ; pièces repositionnées chaque tick sur `positionSection`.
- [ ] **Step 3 : implémenter `LeviathanPiece`** comme `GolemPiece` (même structure), puis **compiler** (`build`, code de sortie 0).
- [ ] **Step 4 : vérifier en jeu rapidement** (Luca, monde de test aquatique) : l'œuf est dans « Donjons », le serpent apparaît dans l'eau, flotte sur place, la barre de vie s'affiche, `/kill @e[type=grappin:leviathan]` la retire.

---

### Task 3 : La nage (poursuite, évitement des obstacles, dégagement)

**Files :**
- Create : `.../LeviathanComportement.java` (goal `Nager` ; les attaques viennent à la Task 4)
- Modify : `Leviathan.java` (`registerGoals`, ciblage), `Reglages.java` si besoin

**Interfaces :**
- Consumes : `Leviathan.positionSection`, `LeviathanLogique.estCoince`.
- Produces : `LeviathanComportement.Nager` (goal) : vers le joueur le plus proche (cible réévaluée toutes les 40 ticks, joueur nettement plus proche → bascule, `NearestAttackableTargetGoal<Player>` et passage sur un joueur à 0,64 de la distance carrée courante comme `Golem`) ; elle oriente la vitesse par un virage progressif (rotation max 6° par tick), regarde 8 blocs devant avec `level.clip(ClipContext.Block.COLLIDER, Fluid.NONE)` et, si un bloc solide barre la route, tourne du côté libre ; si `estCoince(déplacement sur 40 ticks, 40)`, choisit une direction aléatoire libre pendant 30 ticks ; la tête se déplace par `move(MoverType.SELF, vitesse)` (donc arrêtée par les murs), jamais hors de l'eau (si la tête monte vers la surface, elle redescend).
- [ ] **Step 1 : écrire le goal** et le brancher ; la nage se fait à vitesse `attributs vitesse de nage` (0.9 bloc/tick au maximum, accélération 0,06).
- [ ] **Step 2 : compiler** (code de sortie 0) puis **test en jeu** (Luca) dans une grande salle noyée de test : le serpent poursuit, contourne les piliers sans se coincer, se dégage s'il touche un mur, et ne sort jamais de l'eau.

---

### Task 4 : Les attaques, la perle et la phase 2

**Files :**
- Modify : `LeviathanComportement.java` (goals `Combat`), `Leviathan.java` (`hurt`, `die`, tag de mort, phase 2)

**Interfaces :**
- Consumes : `Leviathan.ouvrirPerle/fermerPerle/phase()`, `LeviathanLogique.choisirAttaque/perleExposee/cadence`, `Reglages.*`.
- Produces :
  - `LeviathanComportement.Combat` (goal, sans drapeaux) : enchaîne les attaques toutes les `cadence(phase, 90)` ticks, la prochaine choisie par `LeviathanLogique.choisirAttaque(phase(), joueurProcheQueue, n -> getRandom().nextInt(n))` ;
  - **Morsure** : il fonce sur la cible, un joueur à moins de 3 blocs de la tête reçoit `Reglages.MORSURE_DEGATS` (source `sourceDegats()`), puis recule ;
  - **Rayon** : immobile, l'œil brille `Reglages.RAYON_LEVIATHAN_CHARGE` ticks (particules le long de la future ligne), puis un rayon de 60 blocs (`level.clip` en `COLLIDER`, fluides ignorés : l'eau ne l'arrête pas, les blocs solides si) blesse (`RAYON_LEVIATHAN_DEGATS`) tout joueur à moins de 1 bloc de la ligne ; ensuite la perle s'ouvre `Reglages.LEVIATHAN_PERLE` ticks et le serpent reste étourdi (immobile) ;
  - **Coup de queue** : un joueur à moins de 6 blocs de la queue est repoussé à 1,6 bloc/tick et reçoit `QUEUE_DEGATS` ;
  - **Plongée** (phase 2 seulement) : il descend au fond de la salle puis remonte à pleine vitesse vers sa cible ;
  - `Leviathan.hurt` : dégâts du corps × `LEVIATHAN_FACTEUR_CORPS`, la perle ouverte × `LEVIATHAN_FACTEUR_PERLE`, `/kill` et les dégâts qui contournent l'invulnérabilité sans réduction ;
  - phase 2 : annonce (titre, rugissement, particules), barre rouge, donnée `deuxiemePhase` synchronisée ;
  - mort : `die` appelle les fonctions du tag `grappin:leviathan_mort` seulement si le serpent est bien mort (`this.dead`) ; l'état de combat (perle, étourdissement, charge) n'est pas sauvegardé : au chargement la perle est fermée.
- [ ] **Step 1 : implémenter les attaques** et la perle (message « La perle est exposée ! » en bas de l'écran aux joueurs à 90 blocs, la pièce gonfle et brille côté rendu à la Task 5).
- [ ] **Step 2 : phase 2, mort, tag** et compiler (code de sortie 0).
- [ ] **Step 3 : test en jeu** (Luca) : morsure, rayon (un pilier l'arrête), perle exposée, coup de queue, plongée en phase 2, mort sans erreur dans les logs.

---

### Task 5 : Modèle, texture et rendu

**Files :**
- Create : `.../LeviathanModele.java`, `LeviathanRendu.java`, `atelier/scripts/texture_leviathan.py` (génère `assets/grappin/textures/entity/leviathan.png`)
- Modify : `GrappinClient.java` (rendu et couche, supprime le rendu provisoire)

**Interfaces :**
- Consumes : `Leviathan.positionSection`, `Leviathan.perleOuverte()`, `Leviathan.deuxiemePhase()`.
- Produces : `LeviathanRendu extends EntityRenderer<Leviathan>` qui dessine, à chaque image, la tête (modèle `tete`) puis chaque section (modèle `anneau`, `queue`) à la position interpolée `positionSection(i)`, orientée vers la section précédente (lacet et tangage), à l'échelle ×1 ; `LeviathanModele` (couche `COUCHE`) avec les trois modèles : tête à mâchoire à dents, œil et **perle** lumineux (pleine luminosité, la perle gonfle et bat quand `perleOuverte()`), nageoire dorsale ; anneau à nageoires et épines ; queue à nageoire en croissant ; écailles teal et bleu nuit, ventre clair, taches de bioluminescence ; phase 2 : teinte rougeâtre. `texture_leviathan.py` (Pillow, `test-local\venv\Scripts\python.exe`).
- [ ] **Step 1 : générer la texture** et la vérifier visuellement.
- [ ] **Step 2 : écrire modèle et rendu**, enregistrer la couche et le rendu.
- [ ] **Step 3 : compiler** (code de sortie 0) puis **test en jeu** (Luca, avec captures d'écran) : le serpent a de la longueur et se courbe, la tête se lit (mâchoire, œil, perle), la perle bat quand elle est ouverte ; itérer sur l'aspect.

---

### Task 6 : La salle noyée et ses cloches

**Files :**
- Modify : `atelier/scripts/donjon_aqua.py` (volumes et eau de `S10`), `atelier/scripts/donjon_aqua_missions.py` (piliers, cloches de la salle, `BOSS`, `ilot`/`creuser` supprimés), `atelier/scripts/donjon_aqua_decor.py` (si le décor de la salle noyée en dépend), `atelier/scripts/verif_donjon_aqua.py`
- Modify : `atelier/exports/aqua.nbt`, `aqua_cibles.json` (régénérés)

**Interfaces :**
- Produces (`donjon_aqua*.py`) : `S10` entièrement noyée : `eau(62, 140, 15, 62, 120, 166)` à la place du volume d'air et de l'îlot (le fond de la cuvette `S10` reste à y 14, les rives à y 43 deviennent une étagère sous l'eau, aucune surface d'air sauf les cloches) ; `PILIERS_BOSS` : liste de 8 à 10 piliers ruinés `(x, z, hauteur)` de hauteurs variées (au moins 5 blocs, au plus 40), sans jamais fermer un couloir d'eau de moins de 8 blocs ; `CLOCHES_BOSS` : 7 à 9 cloches `(cx, yb, cz)` posées au sommet de certains piliers et accrochées en hauteur (réutilise `cloche(cx, cz, yb)` ; les pieds descendent jusqu'au fond) ; l'export `aqua_cibles.json` contient `boss_salle: {cloches: [[cx, yb, cz], ...], piliers: [[x, z, h], ...], centre_spawn: [x, y, z]}` ; une assertion vérifie que chaque grande ligne droite entre deux espaces d'eau est barrée par au moins un pilier ou une cloche et que `len(CLOCHES_BOSS) >= 7`.
- [ ] **Step 1 : écrire le contrôle qui échoue** — dans `verif_donjon_aqua.py`, une vérification « salle du boss » : aucune case d'air hors des cloches dans `S10`, chaque cloche contient 18 blocs d'air (3 × 3 × 2) et son entrée dessous est dans l'eau, au moins 7 cloches ; lancer `test-local\venv\Scripts\python.exe atelier\scripts\verif_donjon_aqua.py` ; attendu : échec (la salle est encore à l'air libre).
- [ ] **Step 2 : refaire la salle** dans le générateur (volumes, eau, piliers, cloches), en gardant intacts la galerie d'entrée noyée et la herse G7.
- [ ] **Step 3 : régénérer** (`donjon_aqua.py`), puis relancer `verif_donjon_aqua.py` ; attendu : toutes les vérifications existantes (eau confinée, cloches atteignables, portes) **et** la nouvelle passent.
- [ ] **Step 4 : contrôle visuel** — en jeu (Luca) ou par une vue du plan : on voit les piliers et les cloches, aucune poche d'air ailleurs, un serpent de 5 blocs peut nager partout (couloirs d'eau de 8 blocs au moins).

---

### Task 7 : La sortie noyée

**Files :**
- Modify : `atelier/scripts/donjon_aqua_missions.py` (couloir de la sortie), `atelier/scripts/donjon_aqua_fin.py` (porte `SORTIE`, fonctions d'ouverture), `verif_donjon_aqua.py`
- Modify : `atelier/exports/aqua.nbt`, `aqua_cibles.json`

**Interfaces :**
- Produces : le couloir derrière `SORTIE` (6 blocs) est noyé et se termine par une cloche d'air et le point `POINTS["sortie"]` ; `PORTES["SORTIE"]` : grille de barreaux noyée (`eau` vrai) ; `aqua_ouvrir_sortie_boss` ouvre la grille en remplaçant les barreaux par de l'eau ; `aqua_fermer_sortie_boss` les remet.
- [ ] **Step 1 : écrire le contrôle qui échoue** — `verif_donjon_aqua.py` : le couloir de sortie est entièrement dans l'eau confinée, la cloche de fin contient de l'air, la sortie est atteignable seulement une fois `SORTIE` ouverte ; attendu : échec avant la modification.
- [ ] **Step 2 : implémenter le couloir noyé et les fonctions**, régénérer, relancer le vérificateur ; attendu : tout vert, y compris « eau confinée ».

---

### Task 8 : Intégration au donjon (déclenchement, phase 2, cloches, mort, remise à zéro)

**Files :**
- Modify : `atelier/scripts/donjon_aqua_fin.py` (blocs de commande et fonctions), `donjon_aqua.py` (docstring), `verif_donjon_aqua.py`
- Modify : `atelier/exports/aqua.nbt`

**Interfaces :**
- Produces : variables de score `#boss` (objectif `aqua` : 0 → 1 → 2 → 3), `#inond` (nombre de cloches inondées), `#c1 … #cN` (minuteurs par cloche) ; fonctions `aqua_boss_annonce`, `aqua_boss_invoque`, `aqua_boss_mort`, `aqua_boss_vaincu`, `aqua_cloche_inonde_<i>`, `aqua_cloche_regen_<i>` ; tag `data/grappin/tags/function/leviathan_mort.json` listant `atelier:aqua_boss_mort` ; `aqua_reset` étendue.
- Le serpent apparaît au sud-ouest de la salle, au fond (`centre_spawn`), orienté vers l'entrée de la salle.
- [ ] **Step 1 : écrire le contrôle qui échoue** — assertion à la génération : `len(CMD) <= 32 * len(CHAINES)` avec les nouvelles commandes, et `nombre_inondables == len(CLOCHES_BOSS) // 2` ; lancer le générateur ; attendu : échec si les chaînes sont pleines.
- [ ] **Step 2 : ajouter les commandes** : à l'arrivée d'un joueur dans la salle (zone `S10`, `#boss` à 0), `aqua_boss_annonce` (met `#boss` à 1, titre « Le Léviathan se réveille... », son, planifie `aqua_boss_invoque` dans 100 ticks) ; `aqua_boss_invoque` (`positioned` d'abord, `execute unless entity @e[type=grappin:leviathan,distance=..150]` puis `summon grappin:leviathan x y z {Rotation:[...]}`, puis `#boss` à 2 seulement si le serpent est là, sinon à 0) ; phase 2 : pour chaque cloche `i`, `execute if score #phase aqua matches 1 if score #inond aqua matches ..<plafond-1> if entity @e[type=grappin:leviathan,distance=..6] positioned <cloche i> run function atelier:aqua_cloche_inonde_<i>` (remplit les 18 blocs d'intérieur d'eau, `#inond` + 1, minuteur `#ci` à 400) ; décompte du minuteur et `aqua_cloche_regen_<i>` (`fill` de l'intérieur en air en ne remplaçant que l'eau, `#inond` − 1) ; `#phase` est mis à 1 par la fonction `aqua_boss_phase2` que l'entité appelle via le tag `grappin:leviathan_phase2` (créé de la même façon que le tag de mort) — ajouter ce tag au mod (appel dans `Leviathan` quand `deuxiemePhase` devient vrai) ; `aqua_boss_mort` (`execute if score #boss aqua matches 2 run function atelier:aqua_boss_vaincu`) et `aqua_boss_vaincu` (`#boss` à 3, `aqua_ouvrir_sortie_boss`, titre vert) ; `aqua_reset` : `#boss` et `#phase` à 0 **avant** `kill @e[type=grappin:leviathan]`, `schedule clear` de l'invocation, rétablit toutes les cloches de la salle, referme la sortie.
- [ ] **Step 3 : écrire le tag de mort et celui de la phase 2** dans le datapack du monde ; **ajouter l'appel du tag de phase 2** dans `Leviathan.java` (même mécanisme que le tag de mort) ; compiler le mod.
- [ ] **Step 4 : régénérer et contrôler** — générateur et vérificateur sans erreur ; relire les `.mcfunction` générés (une commande par ligne, aucune ligne collée) et les tags.

---

### Task 9 : Version, jar, test en jeu, relecture et PR

**Files :**
- Modify : `atelier/donjon/arene/gradle.properties` (`mod_version=2.9.0`)
- Create : `atelier/exports/grappin-2.9.0.jar`

- [ ] **Step 1 : passer `mod_version` à 2.9.0**, `./gradlew build --console=plain -q` (code de sortie 0, tests verts), copier `build/libs/grappin-2.9.0.jar` dans `atelier/exports/`.
- [ ] **Step 2 : test final en jeu** par Luca dans « Donjon 2 » (`/reload`, `/function atelier:aqua_pose`, attendre le message vert) : entrée dans la salle, annonce, 5 s, le serpent surgit, nage, attaques, perle, phase 2 et cloches inondées puis rétablies, respiration dans les cloches, mort, sortie ouverte, remise à zéro ; aucune erreur dans `mod/run/client/logs/latest.log`.
- [ ] **Step 3 : relecture finale indépendante** de toute la branche sur le Review Focus ; correction des points critiques et importants.
- [ ] **Step 4 : commit, push de `feature/boss-leviathan` et PR** contre `feature/boss-golem` (sur accord de Luca) ; description française avec le pourquoi, le test fait, ce qui ne l'est pas, et la dépendance à la #21. Le merge est décidé par Duncan.
