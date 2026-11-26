# Outils de test du mod Interrègne

Ce guide explique comment vérifier un changement **sans jouer à la main** : tests de logique, captures d'écrans automatiques, scénarios de colonie accélérés. Il est écrit pour les contributeurs comme pour leurs agents (Claude Code ou autre).

Règle d'or : on ne dit pas « c'est fait » sur un changement de gameplay ou d'interface sans être passé par l'outil qui correspond (tableau ci-dessous). Ces outils ont trouvé des bugs que personne n'aurait vus avant de jouer : une quinzaine de textes qui débordaient ou se chevauchaient dans les écrans, des villageois affamés à côté de 517 plats cuisinés, un bouton « Tout régler » qui faisait payer le prix complet pour une seule livraison.

| Je change… | Outil | Durée |
|---|---|---|
| de la logique pure (prix, porte-monnaie, malus) | JUnit | ~20 s |
| de la logique serveur sans colonie (paiements, paquets) | GameTests | ~1 min |
| un écran, un HUD, un menu | UiShot | 1–3 min |
| un comportement de colonie (production, faim, guerre…) | Scénario | ~36 s par jour de jeu |

Toutes les commandes se lancent depuis `mod/`, en **Git Bash** (pas PowerShell).

---

## 0. Avant tout test

**Ferme le client Minecraft avant de builder.** Windows verrouille les fichiers tant que le jeu tourne, et Gradle échoue.

```bash
./gradlew build --console=plain -q
```

**Les mondes de test ne sont pas dans git** (`mod/run/` est ignoré) mais ils s'installent tout seuls : `./gradlew syncEnv` (ou simplement `runClient`) télécharge `SandboxV2` (le monde de test par défaut : superflat, colonie montée par Duncan), `ColoniesV2`, `Routes_A`, `Arene Hub` et `rpgtest` dans `mod/run/client/saves/`. Pour en partager un nouveau : `./gradlew publishSave -Psave="Nom"` (voir « Environnement partagé » dans `CLAUDE.md`).

**Signale qu'un test tourne.** Pendant qu'un build, un serveur ou un client de test tourne, crée le fichier `.claude/testing.flag` à la racine du repo, et supprime-le dès que c'est fini (règle du `CLAUDE.md`). Si ton agent affiche une barre de statut, il peut s'en servir pour indiquer « tests en cours ».

**Lire les logs de Minecraft** : grep les prend pour du binaire, utilise toujours `grep -a`. Lis le log une seule fois à la fin, ne le suis pas en continu.

---

## 1. JUnit : la logique pure

```bash
./gradlew test
```

- Le jeu est chargé (`unitTest` de ModDevGradle), donc on peut utiliser les classes Minecraft et la config du mod. Les valeurs par défaut de la config sont chargées par `Defaults.load()`.
- Tests existants (`mod/src/test/`) :
  - `WalletTest` : aucune pièce n'est jamais créée ni perdue ;
  - `PricesTest` : le marché ne peut pas être exploité, une amélioration coûte toujours plus cher que la précédente, frais, Influence ;
  - `MalusTest` : les malus des villageois malades ou affamés.
- Résultats : `build/test-results/test/*.xml`.

## 2. GameTests : la logique serveur

```bash
./gradlew runGameTestServer > run/gametest.log 2>&1
grep -a -E "GAME TESTS COMPLETE|tests failed|failed at" run/gametest.log
```

- Tests dans `dev/gametest/EconomyTests`, structure vide `data/interregne/structure/empty.nbt`.
- Il n'y a **aucune colonie** sur ce serveur : pour tout ce qui en demande une, utilise un scénario (section 4).
- Joueur de test : `FakePlayerFactory`, **pas** `makeMockServerPlayerInLevel` (nos paquets de synchronisation à la connexion le font planter).

## 3. UiShot : les écrans photographiés automatiquement

Le client ouvre le monde, affiche chaque écran demandé avec des données factices, le photographie à plusieurs tailles et échelles d'interface, puis écrit un rapport. Personne au clavier.

**Étapes**

1. Build (client fermé).
2. Supprime `run/client/uishot/done.txt` s'il existe (un vieux rapport ferait croire que c'est fini).
3. Écris `run/client/uishot/request.properties` :

   ```properties
   world=SandboxV2
   screen=hub:all,building:farmer
   # optionnel :
   only=farmer-fields
   sizes=1553x1011@4,1553x1011@2
   hover=120,80
   # commandes serveur jouées une fois la colonie chargée, avant les captures ({colony} = id de la colonie) :
   commands=garnison poser {colony} academie 3;garnison poser {colony} tir 2
   ```

   `commands=` sert à préparer l'état voulu sans toucher à la save (ex. `/garnison poser` pose une académie, un champ de tir, une caserne ou une tour de garde finie au niveau demandé, pour les cibles `block:garnison*`).

   Valeurs possibles pour `screen=` (liste séparée par des virgules) :
   - `hub:<page|all>`
   - `hubtool:<clipboard|permissions|alliances|appearance|mercenary|stats|all>`
   - `building:<type|all>` : vue d'ensemble + chaque page de la colonne de gauche, nommées `<type>-<page>` (ex. `farmer-fields`)
   - `citizen:<n>`, `hud`, `catalogue`
   - `banner:<bois|pierre|ble|brique|minerai|all>` : le panneau de la colonie (celui qui descend en haut de l'écran à l'entrée d'une colonie), posé en position de repos avec le nom « Brumeval »
   - `block:<bank|regionalbank|comptoir|market|throne|crops|all>`
   - `menu` : écran titre (`title`) et, si on est dans un monde, menu pause (`pause`) ; c'est ainsi qu'on vérifie un layout FancyMenu (voir `fancymenu.md`)
   - `list` : liste juste les types de bâtiments ; `none` : ouvre seulement le monde

   Tailles par défaut : `1553x1011@4,1553x1011@2,854x480@2`. **Vérifie toujours `1553x1011@4`** : c'est la configuration de jeu de Duncan, la plus serrée.

4. Lance le client : `./gradlew runClient --console=plain > run/client-session.log 2>&1`
5. Attends `run/client/uishot/done.txt`.

**Lire le résultat**

- La première ligne de `done.txt` est `Layout problems: N`. Sous chaque capture, les lignes qui commencent par `!` (`cut`, `cropped`, `offscreen`, `overlap`, `on item`) signalent un texte tronqué, rogné, hors écran ou qui chevauche autre chose. **Lis ces lignes d'abord** : c'est plus rapide que regarder les images, et ça attrape chaque texte coupé.
- Les images sont dans `run/client/uishot/out/` : regarde-les pour l'aspect général. Astuce : assemble-les en planches de 4 (un petit script Python + PIL) plutôt que de les ouvrir une par une.
- `BOScreen` dans le nom de classe = écran encore dans l'ancien style MineColonies.

**Pièges**

- `out/` est **vidé au début de chaque lancement** : copie ailleurs ce que tu veux garder avant de relancer.
- Un client déjà lancé consomme `request.properties` tout de suite : écris le fichier avant de lancer le client, ou une fois que c'est bien le client qui doit le lire qui tourne.
- Hover, clics et ressenti restent à tester à la main.

**Code** : `client/dev/UiShot` (déroulé), `UiScenarios` (cibles et données factices : **ajoute une cible pour chaque nouvel écran**), `DevOverrides` (valeurs factices), `LayoutProbe` + mixin `GuiGraphicsProbeMixin` (détection des débordements), `mixin/client/MouseHandlerMixin` (souris figée).

## 4. Scénarios : une colonie qui tourne en accéléré

Pour tout ce qui a besoin d'une vraie colonie. Le jeu tourne aussi vite que la machine le permet (~36 s par journée de jeu), prend des mesures à intervalles réguliers, et finit par des lignes PASS / FAIL.

1. **Copie le monde** (le scénario le modifie) :

   ```bash
   rm -rf run/client/saves/Scenario_sandbox
   cp -r run/client/saves/SandboxV2 run/client/saves/Scenario_sandbox
   rm -f run/client/saves/Scenario_sandbox/session.lock
   ```

2. Écris `run/client/scenario/request.properties` :

   ```properties
   scenario=restaurant
   ticks=24000
   every=2400
   ```

   (`24000` ticks = un jour Minecraft ; `every` = intervalle entre deux lignes de mesures.)

   Scénarios disponibles : `restaurant`, `restaurant-healthy`, `intendance`, `apiary`, `production`, `war`, `war-archers`, `wall-spawn`, `mur-route` (route posée, muraille tracée dessus puis construite, route tracée ensuite vers la colonie murée ; `ticks=24000`, `every=2000`, tourne aussi sur `runServer` sans joueur).

3. Pour que le client ouvre le monde tout seul, écris aussi `run/client/uishot/request.properties` :

   ```properties
   world=Scenario_sandbox
   screen=none
   ```

4. Lance `./gradlew runClient`, attends `run/client/scenario/done.txt` : mise en place, mesures dans le temps, puis PASS / FAIL.

**Ajouter un scénario** : une classe qui implémente `Scenario` dans `dev/`, puis une ligne dans la table `SCENARIOS` de `ScenarioRunner`. Une classe par chaîne de production ou par mécanique.

---

## 4 bis. Commerce : commandes d'admin sur un vrai monde

Pour tester contrats et caravanes sans client, sur un serveur de test (`./gradlew runServer` avec un monde à deux colonies reliées par une route, par exemple `ColoniesV2` copié dans `run/server/world`), via rcon ou la console. Les colonies sont données par numéro (`/mc colony list`).

```
/diplomatie allier 1 2          # alliance, requise pour commercer
/commerce relais 1 2            # pose un Relais niveau 2 dans la colonie 1 (idem colonie 2)
/commerce caisses 1 bois 40     # range 40 caisses de bois à l'entrepôt de la colonie 1
/commerce tresor 2 1000         # 1000 Couronnes au trésor régional de la colonie 2
/commerce offre 1 2 bois 10 5   # la colonie 1 propose 10 caisses de bois à 5 pièce à la colonie 2
/commerce offre 1 2 bois 10 5 3 # idem avec une escorte de palier 3 (0 à 5), payée au trésor régional du vendeur
/commerce accepter 2 1          # la colonie 2 accepte le contrat n° 1 : la caravane part
/commerce retirer 1 2           # la colonie 1 retire son offre n° 2 (l'escorte payée est rendue)
/commerce etat                  # stocks, trésors et contrats (progression sur la route)
/commerce chantier 1 [niveau]   # pose un Relais non construit et construit le niveau voulu (1 par défaut, hôtel de ville relevé si besoin)
/commerce empire marches        # les Marchés impériaux connus, du plus proche de toi au plus loin
/commerce empire 1 route        # la colonie 1 ouvre sa route impériale vers le Marché le plus proche (payée par son trésor)
/commerce empire 1 vendre bois 20   # une caravane de 20 caisses part vendre à l'Empire (route ouverte et Relais exigés)
```

Vente à l'Empire : les `ImperialTradeTests` (GameTests) couvrent la route payée par le trésor (et refusée sans fonds, remboursée si elle échoue), la caravane payée au prix du stock à l'arrivée et non au départ, son arrivée par le tick, son pillage, et la Lenteur selon le nombre de caisses (plafonnée). Une vraie route ne se teste qu'en jeu : `/commerce relais 1 1`, `/commerce tresor 1 1000`, un bloc `marche_imperial` posé, puis `/commerce empire 1 route` ; la route se construit seule et une caravane peut ensuite partir. UiShot : `screen=block:relais-empire-ferme` (et `-pauvre`, `-ouvert`, `-chantier`, `-aucun`) et `screen=hud-caisses`.

Une caravane parcourt la route à `caravanBlocksPerSecond` (3 par défaut). Sans joueur à moins de `caravanShowDistance` blocs, elle n'avance que sur le papier ; l'animal n'apparaît que près d'un joueur (couvert par `CommerceTests`, lancés avec `runGameTestServer`). `/diplomatie rompre 1 2` pendant le trajet rend les caisses au vendeur et le prix à l'acheteur.

Le Relais commercial se construit depuis un pack de blueprints à nous, `Couronne Vacante`, ajouté à tous les styles du catalogue. Ses blueprints sont générés par `python -I -X utf8 mod/tools/gen_relais_blueprints.py` (`pip install nbtlib`), rendus en PNG isométriques (voxels, pas des captures en jeu) par `mod/tools/render_relais.py` vers `screenshots/relais/` ; `RelaisBlueprintTest` (JUnit) vérifie qu'ils se chargent et contiennent la hutte. Écran : `screen=block:relais` dans UiShot. Écran des routes (clic sur un Poste commercial dont la route est à améliorer) : `screen=block:route` (variantes `-pauvre`, `-travaux`, `-max`).

Caravane : une mule avec coffre et son conducteur (citoyen MineColonies générique) à un bloc d'elle, plus les soldats de l'escorte quand le contrat en a. `GameTests` : mule + conducteur, mort du conducteur, soldats qui suivent et ne reviennent pas une fois morts, entités orphelines retirées, et un duel palier 1 contre un joueur en fer (le palier 1 doit le laisser à moins de 8 PV ou le tuer). `EscortTest` (JUnit) couvre les paliers et la sauvegarde. Touche `M` en jeu : carte du royaume.

---

## 5. Outils de routes (à venir)

RouteBench (tracé de route testé hors du jeu en ~2 s), TerrainDump (copie d'un vrai terrain) et RouteShot (captures des routes en jeu) sont sur la branche `feature/route-commerciale-test`, pas encore sur `main`. Leur mode d'emploi sera ajouté ici avec la PR des routes.

## 6. Contenu de test : des mondes prêts, pas des scripts

Si ta PR apporte un monde ou une structure (donjon, arène…), livre un **dossier de sauvegarde prêt à copier** dans `mod/run/client/saves/`. Personne ne doit avoir à créer un monde ou taper une commande pour tester, même « juste une minute ».

## Bot de playtest (`/playtest`)

Pour jouer ta colonie face à une colonie pilotée par le serveur, sans second client. Le bot n'est pas un joueur : il passe par les mêmes chemins admin que `/commerce` et `/diplomatie`, donc il **ne teste pas** les règles que ces chemins sautent (coût en Influence, rang requis) : ça reste un test à la main.

- `/playtest start <humain> <bot>` (op) : pose l'alliance, un Relais niveau 1 et 5000 au trésor aux deux colonies, puis le bot joue la colonie `<bot>`. Il faut un monde qui a déjà une route entre les deux (ex. `ColoniesV2`).
- Toutes les 5 s le bot accepte les offres qui lui sont faites et garde **une** offre ouverte vers toi (bois, blé, pierre en rotation, 5 caisses à 10).
- `/playtest etat` : contrats du bot et derniers événements ; les événements s'affichent aussi dans le chat des ops. `/playtest stop` l'arrête.
- Pilotable par Claude avec le MCP `playtest` (`mod/tools/playtest_mcp`).
