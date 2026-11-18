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
