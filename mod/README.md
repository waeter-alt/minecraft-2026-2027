# L'Interrègne — mod compagnon

Mod NeoForge 1.21.1 (NeoForge 21.1.216) qui modifie MineColonies de l'extérieur, par mixins, sans toucher au mod original.
Il contient du code adapté de MineColonies, d'où la licence GPL-3.0.

## Environnement de test isolé

Tout se lance depuis ce dossier, et rien ne touche à une installation Minecraft existante :

| Commande | Effet | Dossier du jeu |
|---|---|---|
| `./gradlew runClient` | Client de test (compte hors ligne « Dev ») | `run/client/` |
| `./gradlew runServer` | Serveur de test local | `run/server/` |
| `./gradlew build` | Compile le jar du mod | `build/libs/` |

- Le pack de test ne contient que MineColonies, Structurize, BlockUI et Domum Ornamentum, dans les versions exactes du serveur 2025 (dans `libs/`), plus notre mod.
- Pour repartir de zéro, supprime `run/client/saves` ou `run/server/world`.
- Le premier lancement télécharge NeoForge et Minecraft : compte quelques minutes.

## Fonctionnalité 1 : construction instantanée

Les bâtisseurs ne sont plus nécessaires. Le bouton « Construire / Améliorer / Réparer » d'une hutte, et la pose d'une décoration (remparts, routes…), déclenchent directement le chantier :

1. Le joueur paie : par défaut 8 lingots d'or × le niveau visé, 4 pour une réparation ou une décoration. L'or est un remplaçant en attendant les Couronnes ; en créatif, c'est gratuit.
2. Le bâtiment apparaît bloc par bloc, à 20 blocs par tick par défaut.
3. Le chantier se termine comme avec un bâtisseur : niveau mis à jour, événements MineColonies, son et particules.

La démolition passe encore par le système d'origine.

Réglages : `run/<client|server>/…/serverconfig/interregne-server.toml`, section `[instantBuild]`.
