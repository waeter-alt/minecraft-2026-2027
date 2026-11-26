# Handoff : boucler la boucle colonies / diplomatie / guerre

*Brief pour l'agent qui prend la suite. Rédigé le 2026-11-23 par la session de Duncan. À lire en entier avant de toucher au code.*

## 0. Contexte en trois lignes

Projet Kodistan / mod NeoForge « Interrègne » (`mod/`), serveur communautaire de 5 à 10 joueurs, partie de 7 jours. Duncan veut que le bloc **MineColonies / diplomatie / guerre** soit complètement fini et que tous ses systèmes soient branchés entre eux. Les donjons sont à Luca, ne pas y toucher. Toutes les décisions de design sont **déjà validées** par Duncan : elles sont dans `mod/docs/boucle-de-jeu-colonies.md` (fichier non suivi par git dans le dossier de travail de Duncan, voir §6). Ton travail est de les implémenter, pas de les rediscuter.

## 1. Règles du dépôt (CLAUDE.md, à suivre à la lettre)

- Jamais de push sur `main`, jamais de `--force`, jamais de `--no-verify`. **Une branche et une PR par sujet.** Le **merge est décidé par Duncan**, jamais par toi.
- Ne touche pas à `neo_version` dans `mod/gradle.properties`.
- Gradle en **Git Bash** depuis `mod/`, client Minecraft fermé : `./gradlew build --console=plain -q`.
- Un build qui passe n'est pas une feature qui marche : teste réellement (JUnit, GameTests, scénarios de colonie, UiShot pour l'UI). Mode d'emploi : `mod/docs/outils-de-test.md` et `mod/docs/plan-de-test-playtest.md`.
- Pendant un build ou un test, lève `.claude/testing.flag` et retire-le à la fin.
- Avant de dire « prêt » ou d'ouvrir une PR : `./gradlew checkEnv` (un `PROBLÈME` bloque).
- Commits en français, courts, qui expliquent le pourquoi. Pas de refactor au-delà de la tâche.
- Un sous-agent n'appelle jamais `mcp__savvy-progress__progress`, seulement `mcp__savvy-progress__step`.
- UI : fiche hutte commune (`HutBody`), prix dans le bouton, lisible à toute échelle de GUI.

## 2. Décisions validées (ne pas rouvrir)

**Guerre**
- Déclaration : coûte de l'Influence (existe), plus une **route de guerre niveau 1 bâtie automatiquement aux frais du déclarant** s'il n'existe pas déjà de route entre les deux colonies. Pas les fonds = pas de guerre. La **marche des troupes attend la fin de la route**. Les routes **restent** à jamais, y compris après une rupture d'alliance.
- Fin de guerre, trois issues : (1) la paix du déclarant (existe), (2) la **capitulation** du défenseur, qui paie un tribut en couronnes, (3) la **chute de l'hôtel de ville** : victoire immédiate, la guerre se ferme.
- Victoire : le **butin est pris au trésor régional du perdant**, plafonné, avec une **réserve inviolable** (aujourd'hui il est créé de rien), plus de l'**Influence** pour le vainqueur.
- **Bouclier de 2 h** pour le perdant : plus d'assaut possible contre lui pendant ce temps.
- Raids de pillards MineColonies : coupés.

**Aide de clan** (modèle Clash of Clans) : un allié envoie un contingent qui ne sert **qu'en défense**, se **téléporte directement dans l'enceinte** du défenseur (pas de marche), et **disparaît à la fin de la guerre**. Détails non tranchés, prends ma recommandation et liste-les dans la PR : contingent prélevé sur le stock de l'allié, morts perdus, taille plafonnée par la garnison de l'allié, une aide par allié et par guerre.

**Or et Influence : deux monnaies séparées.** Influence = interactions entre joueurs (arène, diplomatie, victoires de guerre). Or = ce que fait la colonie. **Le commerce rapporte de l'or, jamais de l'Influence.** Sources d'or : vente des surplus à la citadelle impériale (marché impérial, prix qui baisse avec le stock), soit par allers-retours à pied alourdis par les caisses, soit par une **route commerciale automatique vers la citadelle** (même logique que les routes entre joueurs, mais sans confirmation : l'Empire accepte toujours) ; contrats entre joueurs ; donjons (côté Luca, hors périmètre) ; butin de guerre ; primes et paris d'arène. Tous les montants dans une config réglable par les admins.

## 3. Ce qui existe (vérifié le 2026-11-23, à reconfirmer en lisant le code)

- `politics/Diplomacy.java` : règles entre colonies (proposer, accepter, retirer, rompre, déclarer la guerre, paix). `Alliances.java` et `Wars.java` ne gardent que l'état. `Diplomacy.mayAssault` est le verrou côté guerre. `Config.WAR_DECLARATION_COST` et `Config.WAR_NOTICE_SECONDS`.
- `war/WarAssaults.java` : lancement (`launch`, refuse sans guerre ouverte ni en zone sûre), destruction (`destroyBuilding`, butin `LOOT_PER_LEVEL = 25` par niveau, crédité via `RegionalTreasury.credit`, **sans débit du défenseur**), reconstruction (`onRebuildAll`, bouton « Tout reconstruire », déjà livré). Troupes : `EntLegionnaire`, `EntArcher`, `AssaultGoal`, `AssaultRangedGoal`.
- `routes/RoadDecay.java` : fait **encore** disparaître la route à la rupture, appelé depuis `Diplomacy.apply` (cas `BREAK`) et la commande admin `rompre`. **Contraire à la décision.**
- `commerce/Contracts.java` : le commerce exige une alliance et une route (`Alliances.areAllied` + `RouteRegistry.pathBetween`). `region/ImperialMarket.java` : marché impérial existant.
- `politics/Influence.java` : sources actuelles trouvées dans le code = présence (+1), dîme, édits. **La guerre, le commerce, la diplomatie et l'arène n'en donnent pas aujourd'hui** (et je n'ai pas trouvé d'arène dans `pvp/`, qui ne contient que `DeathRule` et les zones sûres : à vérifier avant de la brancher).
- UI : `client/hut/HubDiplomacyPage`, `ClientDiplomacy`, payloads `DiplomacyActionPayload` / `DiplomacySyncPayload`.
- Raids : `dobarbariansspawn = false` dans `mod/env/config/minecolonies-server.toml`.
- **Non confirmé** : la garnison achetée (gardes achetés depuis l'Académie et l'Archerie, caserne, tours, décidée le 19/11). Un `grep` de « garnison » ne trouve presque rien dans `mod/src`. À vérifier d'abord (branche mergée `feature/garnison-gardes-achetes`, doc `mod/docs/embauche.md`).

## 4. Ordre des PR (une branche chacune)

0. **Doc** : `mod/docs/boucle-de-jeu-colonies.md` + ce handoff, branche `docs/boucle-de-jeu-colonies`.
1. **Cohérence** : `RoadDecay` ne s'applique plus à la rupture, raids coupés, état réel de la garnison (livrer ce qui manque ou le signaler à Duncan).
2. **Route de guerre automatique** + marche sur route. Skin de route martial (deepslate, blackstone par touches, lanternes d'âme), à valider avec Duncan.
3. **Fin de guerre** : capitulation + tribut, chute de l'hôtel de ville, bouclier de 2 h, butin pris au perdant avec plafond et réserve.
4. **Influence branchée** : victoire de guerre, diplomatie, arène seulement si elle existe.
5. **Or de la citadelle** : vente au marché impérial, route commerciale automatique.
6. **Aide de clan.**
7. **Scénario de bout en bout** : alliance, commerce, rupture, guerre, aide, victoire, reconstruction, sur le patron des scénarios existants.

## 5. Garde-fous

- Passage d'exploration **lecture seule** avant de coder dès que tu touches aux internals MineColonies ou à une zone inconnue (les troupes d'attaque sont des raiders MineColonies : les gardes natifs les combattent déjà). Si un choix d'architecture te semble avoir des conséquences durables et n'est pas couvert par §2, **arrête-toi et demande à Duncan**.
- Un écart entre ce brief et le code (fichier introuvable, comportement différent) : rapporte-le, ne l'enterre pas. Ce brief vient d'une lecture rapide.
- Dis explicitement ce qui est livré, ce qui est à moitié fait et ce qui n'a pas été testé en jeu.
- Pas de nouvelle monnaie, pas de conversion or ↔ Influence.

## 6. Mise en place

- Le dossier de travail de Duncan contient **43 fichiers modifiés ou non suivis** (art, grappin, FancyMenu, blueprints) qui ne sont pas ton sujet. Ne les touche pas, ne les commite pas. Travaille dans un **worktree neuf** depuis `origin/main`.
- `mod/docs/boucle-de-jeu-colonies.md` et ce fichier sont non suivis dans ce dossier. Copie-les depuis le chemin absolu du dossier de Duncan pour la PR 0 : `C:\Users\waete\Documents\Projet-Freelance\duncan-lab\side-projects\Minecraft 2026-2027\mod\docs\`.
