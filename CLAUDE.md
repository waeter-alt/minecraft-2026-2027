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

- Build : `./gradlew build --console=plain -q`
  - Ferme le client Minecraft avant de builder : Gradle ne peut pas réécrire les classes si le jeu tient les fichiers ouverts (verrou Windows).
- Serveur de test : `./gradlew runServer`
- Client de test : `./gradlew runClient`
- "Build qui passe" n'est pas "la feature marche" : si le changement touche à du gameplay ou de l'UI, teste-le réellement en jeu (ou demande à quelqu'un de le tester) avant de dire que c'est fait.

## Pendant un build/test

- Tant qu'un build, un serveur ou un client de test tourne en arrière-plan, signale-le clairement (par ex. un fichier `.claude/testing.flag` + un indicateur dans le statusline) et retire le signal une fois terminé. Objectif : ne jamais dire "c'est prêt" alors que ça tourne encore.

## Git — hygiène générale

- Avant tout `git reset`/`checkout`/`clean` destructif sur un dossier qui a du travail non commité : vérifie `git status` d'abord, stash (`-u` pour les fichiers non suivis) ou commit ce qui s'y trouve.
- Commits : message court, explique le **pourquoi** plutôt que de reformuler le diff. En français, comme l'historique existant.
- Ne commit jamais de secrets, clés, ou fichiers de config locale (`.claude/` est volontairement ignoré par git pour cette raison).

## Qui décide quoi

- **Duncan** : direction créative du projet, dernier mot sur ce qui entre dans `main`, test final en jeu.
- **Contributeurs** : implémentation sur les features réparties, PR systématique, jamais de merge solo même en cas d'urgence — on attend ou on relance Duncan.

## Style de code

- Pas d'abstraction ou de refactor au-delà de ce que la tâche demande. Un bug fix n'a pas besoin de nettoyage autour.
- Pas de commentaires qui répètent ce que le code dit déjà ; un commentaire seulement si le POURQUOI n'est pas évident (contrainte cachée, contournement d'un bug précis, invariant subtil).
- Si tu introduis une incohérence ou un truc à moitié fini, dis-le explicitement plutôt que de livrer en silence.
