# Portes, leviers et clés du Donjon Suspendu — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal :** 6 blocs de porte (pierre gravée et sceau, ×3 couleurs), un levier de racine et 3 clés dans le mod `grappin`, puis le donjon qui s'en sert.

**Architecture :** un script `atelier/scripts/donjon_blocs.py` dessine les textures 16 × 16, écrit états de blocs, modèles, `.bbmodel` (levier, clés) et tables de butin ; le Java enregistre blocs et objets ; les scripts du donjon remplacent les noms de blocs et d'objets.

**Spec :** `docs/superpowers/specs/2026-11-18-donjon-portes-leviers-cles-design.md`

## Global Constraints

- NeoForge 21.1.216, aucune dépendance en plus ; gros pixels, 16 × 16.
- Couleurs : bleu (porte 1, saphir), jaune (porte 2, ambre), rouge (porte 3, rubis).
- Identifiants : `grappin:porte_bleue|porte_jaune|porte_rouge` (lumière 4), `grappin:sceau_bleu|sceau_jaune|sceau_rouge` (lumière 7), `grappin:levier_racine` (`LeverBlock`, lumière 6 baissé), `grappin:cle_bleue|cle_jaune|cle_rouge`.
- Gradle en Git Bash, `JAVA_HOME=/d/PROJET/minecraft-2026-2027/test-local/outils/jdk-21.0.13+9` ; Python du venv avec `-I`.
- Le dossier principal (travail en cours non commité) n'est touché que pour déclarer le jar dans `mod/env/mods.txt` (commit de ce seul fichier et du jar).

## Review Focus

1. Porte vue dans le noir : veines et sceau lisibles, couleurs distinctes. → captures en jeu (tâche 4).
2. Levier posé au mur ou au plafond (états `face=wall|ceiling`) : modèle correct, pas seulement au sol. → `ModelesDonjonTest.levierTousLesEtats`.
3. Ancienne clé (crochet renommé) dans l'inventaire d'un joueur d'un ancien essai : n'ouvre plus rien. → accepté, signalé à Luca.
4. Remise à zéro : les leviers reviennent levés, les portes et les coffres de clés reviennent. → tests du donjon (tâche 3).
5. Blocs cassés en mode survie par un joueur : ils lâchent leur objet (tables de butin). → `ModelesDonjonTest.tablesDeButin`.

### Task 1 : textures, modèles, Blockbench (sans Java)
- [ ] Test JUnit `ModelesDonjonTest` (écrit d'abord, échoue) : chaque bloc a un état de bloc, des modèles et des textures 16 × 16 ; `levierTousLesEtats` (24 variantes face × facing × powered) ; chaque clé a un modèle d'objet ; `tablesDeButin` (un fichier par bloc).
- [ ] `donjon_blocs.py` écrit tout ; le levier et les clés ont aussi un `.bbmodel` dans `atelier/modeles/donjon/`.
- [ ] Planche d'aperçu des textures + ouverture du levier et des clés dans Blockbench, captures envoyées à Luca. **Arrêt jusqu'à son accord.**
- [ ] Commit.

### Task 2 : blocs et objets dans le mod
- [ ] `DonjonBlocs.java` (registres), `LevierRacine` (sous-classe de `LeverBlock`), langues, onglet créatif ; build OK, tests OK ; commit.

### Task 3 : le donjon s'en sert
- [ ] Branche `feature/donjon-ambiance` (worktree `D:\PROJET\wt-donjon-ambiance`) partie de `feature/donjon-suspendu` ; test du donjon mis à jour d'abord (échoue), puis `COULEURS_PORTES` → `grappin:porte_*` / `grappin:sceau_*`, levier → `grappin:levier_racine`, clés → `grappin:cle_*` ; option `--saves` du générateur pour écrire dans le monde du dossier principal ; 107+ tests OK ; commit.

### Task 4 : jar 3.6.0, déclaration, essai
- [ ] `mod_version=3.6.0`, jar dans `atelier/exports`, déclaré dans `mods.txt` (dossier principal, commit de ce seul fichier + jar), synchro, `checkEnv` ; générateur vers le monde de test ; essai par Luca.

---

## Révision 2 : porte entité et levier animé (remplace les portes en blocs)

### Task 5 : porte et levier dans Blockbench
- [ ] Tests Python d'abord (échouent) : `test_porte_bbmodel.py` (os `racine` > `cadre`, `battant_g` > `sceau_g`, `battant_d` > `sceau_d` ; animation `ouverture` 2,5 s, non bouclée ; battants à ±85° à la fin) ; `test_levier_bbmodel.py` (os `socle`, `manche`, `cliquet`, `cristal` ; animations `tirer` et `relacher` de 0,6 s ; hauteur 28 à 36 unités).
- [ ] `porte_vers_bbmodel.py` et `levier_vers_bbmodel.py` (format `modded_entity`, 16 unités par bloc ; porte dessinée pour une arche de 8 × 8 blocs) ; textures 64 × 64 dessinées par script (une par couleur pour la porte).
- [ ] Ouverture dans Blockbench, captures envoyées à Luca. **Arrêt jusqu'à son accord.** Commit.

### Task 6 : dans le mod
- [ ] Retirer `porte_*`, `sceau_*` (Java, ressources, test).
- [ ] `PorteDonjon` (entité immobile : `Couleur`, `Largeur`, `Hauteur`, `Ouverte` en NBT et synchronisés ; ouverture animée ; ouverte d'emblée si elle l'était au chargement) + `PorteRendu` (échelle `largeur/8`, `hauteur/8`, texture selon la couleur).
- [ ] `LevierRacine` : `LeverBlock` + `EntityBlock`, rendu par `LevierRendu` (bloc dessiné par le code, animation `tirer`/`relacher` au changement de `powered`, boîte de rendu de 2 blocs, sol, mur, plafond).
- [ ] JUnit : `PorteLogiqueTest` (échelle, choix de la texture, pose ouverte au chargement) ; build OK ; commit.

### Task 7 : dans le donjon
- [ ] Tests d'abord : une `grappin:porte_donjon` par arche (couleur, largeur, hauteur, lacet perpendiculaire au mur) ; barrières à la place des dalles ; ouverture = `Ouverte:1b` + barrières retirées ; remise à zéro = barrières reposées + `Ouverte:0b`.
- [ ] Scripts ; 107+ tests OK ; régénération dans le monde de test ; commit.

### Task 8 : jar 3.7.0, déclaration, essai.
