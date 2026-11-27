# Kodistan 2026-2027 — mod Interrègne

Serveur Minecraft communautaire (Kodistan) et son mod NeoForge custom, **Interrègne**, construit par-dessus MineColonies/Structurize.

## Statut : Alpha (depuis le 2026-11-27)

La boucle complète d'une colonie se joue en survie, sur un monde neuf et à plusieurs : fondation, bâtiments, alliance et route, contrats et caravanes, guerre, assaut et étendard de siège, réapparitions, fin de guerre (capitulation, reddition, chute de l'hôtel de ville), bouclier et reconstruction. Elle a été validée par un pré-playtest à deux clients. On passe aux playtests multijoueurs.

Ce que veut dire « Alpha » :
- le contenu peut encore changer (équilibrage, règles, interface) et les mondes de playtest ne sont pas gardés d'une version à l'autre ;
- version du mod et du pack : `0.1.0-alpha` (`mod/gradle.properties`, `launcher/pack/pack.toml`) ;
- connu, pas encore réglé : un crash serveur ramène le monde à la dernière sauvegarde automatique (jusqu'à 5 min) ; un joueur mort pendant un assaut au moment du crash réapparaît au spawn du monde.

Préparer un monde de playtest : `mod/docs/plan-de-test-playtest.md`, section « 0 bis ».

## Par où commencer

1. **[CLAUDE.md](CLAUDE.md)** : les règles de travail (branche + PR, jamais de push sur `main`, version NeoForge figée, build/test). À lire en premier, que tu codes à la main ou avec un agent.
2. **[mod/docs/outils-de-test.md](mod/docs/outils-de-test.md)** : comment tester (JUnit, GameTests, captures UiShot, scénarios de colonie).
3. **[mod/docs/GOTCHA.md](mod/docs/GOTCHA.md)** : les pièges déjà rencontrés.

## Récupérer la dernière version

```bash
git checkout main
git pull
```

Puis, pour travailler : `git checkout -b feature/nom-court`, et une PR vers `main` à la fin.

## Organisation du repo

| Dossier | Contenu |
|---|---|
| `mod/` | le mod NeoForge (build : `./gradlew build` depuis `mod/`, en Git Bash) |
| `atelier/` | générateurs de donjons/arènes et mondes de test prêts à copier (`atelier/mondes/*.zip`) |
| `docs/` | dossier de projet, design (arène PvP, progression) |
| `launcher/`, `art/` | launcher du serveur, visuels |

## Organisation du code du mod : une feature = son propre coin

Le code est découpé pour que deux personnes (ou deux agents) puissent travailler sur deux features en même temps sans toucher les mêmes fichiers.

- **Un package par feature** dans `mod/src/main/java/fr/kodistan/interregne/` : `economy/`, `walls/`, `war/`, `routes/`, `politics/`, `colony/`, `region/`, `warehouse/`…
- **Réseau : une classe `XNetwork` par feature** (`EconomyNetwork`, `WarNetwork`, `PoliticsNetwork`…). Chacune enregistre ses propres payloads dans sa méthode `register`. Pour ajouter un paquet, tu modifies le `XNetwork` de ta feature, pas un fichier central. L'envoi vers le client passe par `Net.toClient(...)` (`network/Net.java`).
- **Hub de colonie : une classe `HubXPage` par page** (`client/hut/HubOverviewPage`, `HubBuildingsPage`, `HubCitizensPage`, `HubIntendancePage`, `HubWallsPage`). `ColonyHubScreen` ne garde que le cadre et la navigation. Une nouvelle page = une nouvelle classe `HubXPage` + une entrée dans le menu du hub.
