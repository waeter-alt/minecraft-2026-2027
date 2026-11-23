# Projet Kodistan / mod Interrègne — règles de travail

Ce fichier s'applique à tout le monde qui travaille sur ce repo avec Claude Code (ou un autre agent). Ni Duncan ni les autres contributeurs ne sont développeurs de formation — ces règles remplacent l'expérience qu'on n'a pas, donc on les suit à la lettre plutôt que d'improviser.

Contexte : mod NeoForge custom (`mod/`) pour un serveur Minecraft communautaire annuel (Kodistan), construit par-dessus MineColonies/Structurize. Le reste du repo (`docs/`, `launcher/`, `art/`) accompagne le même projet.

## Règle n°1 — jamais de push direct sur `main`

- Toujours travailler sur une branche dédiée : `git checkout -b feature/nom-court` (ou `fix/...`).
- Push la branche, ouvre une Pull Request vers `main` sur GitHub.
- **Le merge est décidé par Duncan** (owner du repo, voir `.github/CODEOWNERS`) — jamais par toi ou ton agent, même si la PR est verte et review-approuvée par ailleurs.
- Jamais de `git push --force` sur une branche partagée, jamais de réécriture de l'historique de `main`, jamais de `--no-verify`.
- Une PR = un sujet. Ne mélange pas un fix et une feature dans la même PR.

## Avant de coder quoi que ce soit

- Si la tâche touche des internals MineColonies/Structurize, des mixins, ou une zone du code que tu ne connais pas encore : fais d'abord un passage d'exploration (lecture, pas d'écriture), et si un choix d'architecture a des conséquences durables, demande confirmation à Duncan avant de partir sur l'implémentation. C'est le genre de zone qui casse silencieusement et coûte cher à déboguer après coup.
- Ne touche pas à `neo_version` dans `mod/gradle.properties` — cette version de NeoForge est figée pour tout le projet. Si une dépendance tierce ne colle pas avec cette version, c'est elle qu'on downgrade/adapte, jamais NeoForge qu'on monte.
- **Tout développement fait pour ce projet (mod, outils, sandbox type `atelier/`) doit cibler la version de NeoForge figée dans `mod/gradle.properties`** — pas une autre version installée localement par un contributeur. Aucune exception, aucun "pour l'instant" : si une PR arrive sur une version différente, elle n'est pas mergeable telle quelle, il faut la refaire sur la bonne version avant.

## Build & test (depuis `mod/`, en Git Bash — pas PowerShell pour les commandes Gradle)

- Outils de test (JUnit, GameTests, captures UiShot, scénarios de colonie) : mode d'emploi dans `mod/docs/outils-de-test.md`.
- Build : `./gradlew build --console=plain -q`
  - Ferme le client Minecraft avant de builder : Gradle ne peut pas réécrire les classes si le jeu tient les fichiers ouverts (verrou Windows).
- Serveur de test : `./gradlew runServer`
- Client de test : `./gradlew runClient`
- "Build qui passe" n'est pas "la feature marche" : si le changement touche à du gameplay ou de l'UI, teste-le réellement en jeu (ou demande à quelqu'un de le tester) avant de dire que c'est fait.
- **Un script ou outil de test ne doit jamais supposer un état local déjà en place** (dossier datapack monté à la main, save pré-existante, config locale) — sinon ça ne marche que chez son auteur et personne d'autre ne peut reproduire le test. Rencontré sur la PR #5 (`donjon_temple.py`) : le script n'écrivait la structure et les fonctions que si `datapacks/atelier/...` existait déjà dans la save visée, sans jamais le créer lui-même ; sur une save neuve, il échouait en silence, sans la moindre erreur. Deux solutions, au choix de l'auteur : le script crée lui-même tout ce qu'il lui faut (dossiers, `pack.mcmeta`), ou la PR fournit une save/datapack prêt à copier dans `mod/run/client/saves/`.

## Environnement partagé — le repo est la seule source de vérité

Tout ce qui sert à développer ou tester (mods installés, configs, options, mondes de test, outils) doit pouvoir être reconstruit **depuis un simple clone** par `./gradlew syncEnv`. Si une ressource n'existe que sur la machine de quelqu'un, c'est un bug à corriger dans la foulée, pas une surprise à découvrir plus tard. Détail technique : `mod/env.gradle` et le dossier `mod/env/`.

### Mettre à jour mon environnement (séquence pour l'agent)

À suivre dans l'ordre quand l'utilisateur demande de se mettre à jour / d'avoir le même environnement que Duncan, et après chaque `git pull` qui touche `mod/env/`, `launcher/pack/` ou `mod/build.gradle` :

1. **Repo présent ?** Sinon : `git clone https://github.com/waeter-alt/minecraft-2026-2027.git` puis se placer dedans. **JDK 21** requis (`java -version` doit dire 21) ; sinon `winget install EclipseAdoptium.Temurin.21.JDK`, puis rouvrir le terminal.
2. **Protéger le travail en cours** : `git status`. S'il y a des changements non commités, les commiter sur leur branche ou `git stash push -u -m "avant-synchro"` (jamais de `reset`/`clean`).
3. **Récupérer le code** : `git fetch origin`, puis `git checkout main && git pull --ff-only`. Si l'utilisateur travaille sur une branche : `git merge origin/main` dans cette branche.
4. **Synchroniser l'environnement** (Git Bash, depuis `mod/`, client Minecraft fermé) : `./gradlew syncEnv --console=plain`. Le premier passage télécharge les mods et les mondes de test : compte plusieurs minutes. Les passages suivants sont instantanés.
5. **Vérifier** : `./gradlew checkEnv --console=plain` doit se terminer sans ligne `PROBLÈME`. Un `AVERTISSEMENT` se lit et se rapporte, il ne bloque pas.
6. **Compiler** : `./gradlew build --console=plain -q`.
7. **Rapporter** à l'utilisateur ce qui a été installé (lignes `+`, `-`, `↓`), les avertissements restants, et ce qui a échoué. Ne jamais dire « à jour » sans que les étapes 4 à 6 aient réussi.

Aucune étape ne demande à l'utilisateur de créer un monde, copier un fichier ou taper une commande : si une étape exige ça, c'est la séquence qui est cassée, pas l'utilisateur.

### Quand on ajoute une ressource (obligatoire, pour Duncan comme pour Luca, humain ou agent)

- **Nouveau mod tiers** : entrée `launcher/pack/mods/<slug>.pw.toml` (URL + hash, comme les autres) **et** une ligne `client <slug>` (ou `server`/`both`) dans `mod/env/mods.txt`. Mod ou jar versionné dans le repo : ligne `local:<chemin depuis la racine>`.
- **Réglage de config** qui compte pour les tests : `./gradlew captureConfig`, puis commit de `mod/env/config/`.
- **Nouveau monde de test** : `./gradlew publishSave -Psave="Nom du monde"` (client fermé ; demande `gh auth login` une fois), puis commit de `mod/env/saves.json`. Un monde ne va jamais dans git.
- **Toute nouvelle catégorie de ressource** (un outil à installer, un binaire, un dossier hors git…) : l'ajouter à `mod/env.gradle` **et** à cette section, dans la même PR. C'est ce qui évite d'y revenir à la main.
- **Aucun chemin absolu ni réglage propre à une machine** dans un fichier versionné (`gradle.properties`, `settings.json`…).
- **Avant de dire « prêt »**, d'ouvrir une PR ou de répondre à « c'est sur GitHub ? » : lancer `./gradlew checkEnv`. Un `PROBLÈME` bloque ; un `AVERTISSEMENT` se règle (commande ci-dessus) ou se justifie explicitement.

## Barre de progression (savvy-progress)

Au début de toute tâche de plusieurs étapes (même seuil que la liste de tâches), la session principale appelle `mcp__savvy-progress__progress` avec `title`, `total` et `done: 0`, `phase: "plan"` ; met `done` à jour à chaque étape finie ; appelle une dernière fois avec `finished: true`. Pas pour une question simple ni une commande unique. Un sous-agent n'appelle jamais `progress` : il utilise `mcp__savvy-progress__step`. Si l'outil n'est pas installé chez le contributeur, on continue sans (ce n'est pas bloquant).

## Pendant un build/test

- Tant qu'un build, un serveur ou un client de test tourne en arrière-plan, signale-le clairement (par ex. un fichier `.claude/testing.flag` + un indicateur dans le statusline) et retire le signal une fois terminé. Objectif : ne jamais dire "c'est prêt" alors que ça tourne encore.

## Git — hygiène générale

- Avant tout `git reset`/`checkout`/`clean` destructif sur un dossier qui a du travail non commité : vérifie `git status` d'abord, stash (`-u` pour les fichiers non suivis) ou commit ce qui s'y trouve.
- Commits : message court, explique le **pourquoi** plutôt que de reformuler le diff. En français, comme l'historique existant.
- Ne commit jamais de secrets, clés, ou fichiers de config locale (`.claude/` est ignoré par git, sauf `settings.json` et `statusline.sh` qui sont partagés et ne contiennent aucun secret).

## Qui décide quoi

- **Duncan** : direction créative du projet, dernier mot sur ce qui entre dans `main`, test final en jeu.
- **Contributeurs** : implémentation sur les features réparties, PR systématique, jamais de merge solo même en cas d'urgence — on attend ou on relance Duncan.

## Style de code

- Pas d'abstraction ou de refactor au-delà de ce que la tâche demande. Un bug fix n'a pas besoin de nettoyage autour.
- Pas de commentaires qui répètent ce que le code dit déjà ; un commentaire seulement si le POURQUOI n'est pas évident (contrainte cachée, contournement d'un bug précis, invariant subtil).
- Si tu introduis une incohérence ou un truc à moitié fini, dis-le explicitement plutôt que de livrer en silence.

## Nouvelle hutte (bâtiment MineColonies à nous)

Toute nouvelle hutte suit la checklist `mod/docs/procedure-nouvelle-hutte.md` : blueprints des 5 niveaux **à la même taille** (`mod/tools/pad_blueprints.py`), fiche hutte commune (`HutBody`, jamais un écran à part), pose depuis le catalogue, fantôme niveau max des voisins (`/voisins`), tests en jeu. Chaque point vient d'un défaut déjà rencontré en playtest.
