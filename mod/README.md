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
| `./gradlew test` | Tests JUnit (pièces, règles de prix) | — |
| `./gradlew runGameTestServer` | GameTests (comptoir de banque avec un faux joueur) | — |

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

## Fonctionnalité 2 : production autonome (2026-11-04)

Règle de l'utilisateur :
- **Un bâtiment qui produit une matière première suit le modèle hybride.** La hutte produit elle-même selon son niveau, et le monde autour n'est qu'un décor vivant.
- **Un bâtiment qui transforme ou qui prospecte reçoit toutes les clés pour tourner seul.**

Code dans `src/main/java/fr/kodistan/interregne/production/`.

| Bâtiment | Ce qu'il fait seul | Décor |
|---|---|---|
| Éleveurs (poules, vaches, moutons, cochons, lapins) | viande et sous-produits par niveau | enclos repeuplé à chaque amélioration |
| Pêcheur | morue et saumon | eau du plan colmatée |
| Ferme | sa culture (choisie avant construction) | champs d'épouvantail incassables, mûrs, qui renforcent les fermes de la même culture |
| Bûcheron | vrai travail ; forêt plantée à la construction, pousse accélérée | — |
| Restaurant | cuisine sans combustible, menu automatique, cuit le surplus de l'entrepôt | — |
| Plantation | canne à sucre, cactus, bambou ; chaque bloc de champ ajoute sa plante | champs poussés d'un coup, incassables, jamais récoltés |
| Apiculteur | rayons et fioles de miel | une ruche par niveau, avec abeilles et fleurs, incassable et non récoltable |
| Fleuriste | fleurs de son niveau, sauf celles exclues | une fleur sur chaque parterre, incassable |
| Artisans (tous) | transforment le surplus de l'entrepôt avec leurs recettes activées : stock cible 64, réserve de 16 par ingrédient, combustible pour le four, recettes réversibles ignorées | — |
| Scierie, tailleur de pierre | recettes de base fournies (planches, bâtons, briques de pierre) | — |
| Tamiseur | reçoit gratuitement ses tamis selon son niveau | — |

Les mines et les carrières passent par l'Intendance : leurs fournitures se règlent en or.

## Fonctionnalité 3 : l'Intendance (2026-11-04)

L'onglet Intendance de l'hôtel de ville règle en un clic, en Couronnes et en Influence, ce que la colonie réclame :
- **Doléances** : soigner les malades (`cureCost`, 20) et nourrir les affamés (`mealCost`, 3).
- **Fournitures** : acheter et livrer ce que personne dans la colonie ne fournit, au double du prix (`markup`, 2.0). Les objets rares ne se vendent pas : il faut les apporter.
- **Paiement** :
  - l'or vient du trésor de la colonie, complété par la bourse du joueur ;
  - l'Influence vient du joueur qui clique, 1 point par 10 Couronnes (`crownsPerInfluence`) ;
  - c'est toujours plus cher que de construire le bon bâtiment.
- **Réglage d'une seule ligne** : chaque ligne de la liste a son propre bouton.
- **Achat automatique d'origine** (délai de grâce et barre jaune) : désactivé par défaut (`[intendance] enabled = false`).

Code : `colony/IntendanceDesk.java`, `colony/Intendance.java` et la page Intendance de `client/hut/ColonyHubScreen.java`.
