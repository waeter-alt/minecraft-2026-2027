# Refonte complète des interfaces MineColonies — plan

Validé avec l'utilisateur le 2026-11-03. Rien n'est codé : **on démarre par la phase 0** dans une nouvelle session.

## Objectif (mots de l'utilisateur)

> « refonte complète des interfaces, menus, sous-menus, logique UX/UI, psychologie cognitive. On reste dans la même DA, sans trop surcharger, les informations doivent être claires et accessibles. »

## Référence de qualité : ce qui est déjà validé

Le catalogue de la baguette et la galerie de styles ont été jugés « PARFAIT » par l'utilisateur. Ils servent de modèle :

- `mod/src/main/java/fr/kodistan/interregne/client/catalogue/CatalogueScreen.java`
  - Fenêtre parchemin (480×260 maximum) avec un en-tête et une ✕.
  - Navigation à gauche, cartes au centre, panneau de détail à droite.
  - Bouton d'action principal en bas à droite.
- `client/catalogue/StyleScreen.java`
  - Grille de cartes avec images recadrées, puis un panneau de détail.
- `client/hud/InterregneHud.java`
  - `roundedPlate(...)` (public static) dessine les plaques.
  - Palette parchemin : PAPER `0xFFE9D8A6`, EDGE `0xFF4A2E16`, LIGHT `0xFFFFF4D2`, INK `0xFF4A2A10`.
- Exigences de rendu :
  - compact ;
  - suit le réglage de taille d'interface de Minecraft (coordonnées GUI, mise à l'échelle par `pose()`) ;
  - icônes plutôt que texte, sans icônes génériques (chaque icône correspond à son sens) ;
  - tout en français, sans texte coupé.
- Écrans vanilla `Screen` dessinés avec `GuiGraphics` :
  - `renderBackground` = `fill(0,0,w,h,0x88000000)`, sans flou ;
  - `isPauseScreen` = false.
- Maquette HTML de référence : `mod/docs/maquette-baguette.html`. Une maquette doit **toujours être vérifiée dans le panneau navigateur** (capture d'écran) avant d'être montrée.

## Principes UX (à appliquer à chaque écran)

1. Un écran répond à une seule question (« comment va ce bâtiment ? », « que veut ce citoyen ? »). Le détail s'ouvre à la demande.
2. Les mêmes choses sont toujours au même endroit : fermer (✕) en haut à droite, action principale en bas à droite, Échap revient en arrière, jamais de cul-de-sac.
3. Une icône s'accompagne d'un mot, et un nombre d'une unité (Couronnes, caisses, secondes…).
4. Couleurs d'état uniques : vert = OK, ambre = en attente, rouge = bloquant.
5. Pas de surcharge : la hiérarchie se fait par la taille et le gras, pas par plus de cadres.
6. Les coûts sont exprimés dans notre économie : Couronnes et caisses régionales (`CrateRows.forCrates`), pas les listes de matériaux de MineColonies.

## Approche technique : hybride

- **Remplacer** les écrans très utilisés par nos propres `Screen`, avec un contrôle total. Il faut alors renvoyer les bons messages réseau à MineColonies (sources dans `sources/minecolonies`, tag v1.21.1-1.1.1041).
- **Rhabiller** les écrans rares : textures, XML BlockUI, traductions (layouts surchargés sous `assets/minecolonies/gui/`, ou mixins sur les fenêtres).
- Ouvrir nos écrans à la place des leurs : un mixin client sur la méthode `open`/le constructeur de la fenêtre BlockUI concernée, comme `WindowExtendedBuildToolMixin` → `BuildCatalogue.openFor`.

## Décisions du 2026-11-04

- **Maquette d'abord** : on commence par une maquette HTML de la fiche hutte, construite avec les composants du futur kit. Le kit Java (phase 0) est codé d'après la maquette validée, pas l'inverse.
- **Navigation double** :
  - chaque fenêtre reste accessible depuis son bloc ou son citoyen, comme dans MineColonies ;
  - un **hub de colonie** (l'hôtel de ville repensé) donne aussi accès à tout, avec des onglets Bâtiments / Citoyens / Demandes / Économie, depuis n'importe quelle hutte.
  - Conséquence : les écrans de la phase 1 sont pensés comme des pages qui s'ouvrent seules **ou** dans le hub. Le kit prévoit un fil d'Ariane / retour (Échap revient au hub si on en vient).
- **Premier écran de la phase 1 : la fiche hutte.**
- **Traquer ce qui ne sert plus** : les bâtisseurs ne sont plus utilisés dans le modpack, donc tout ce qui s'y rapporte disparaît. Pour chaque écran, la checklist de couverture marque ce qui ne sert plus ; **en cas de doute, on demande à l'utilisateur**.
- **Icônes** : une icône Minecraft ou MineColonies systématiquement quand elle a du sens (les icônes de modules `minecolonies:textures/gui/modules/` couvrent statistiques, réglages, recettes…). Sinon, du pixel art trouvé en ligne ou dessiné. Une icône doit toujours avoir du SENS.

## Phases

### Phase 0 — Kit d'interface commun (environ 1 session)
Extraire et généraliser les éléments du catalogue dans `client/ui/` :
- fenêtre parchemin avec en-tête, ✕ et onglets ;
- navigation à gauche, cartes, panneau de détail ;
- boutons (principal / secondaire / désactivé) et listes avec défilement à la molette ;
- infobulles, lignes d'objets (icône + quantité), lignes de coût, pastilles d'état ;
- barres de progression.

Ensuite, rebrancher `CatalogueScreen` et `StyleScreen` dessus, sans changement visuel.

**Fait le 2026-11-04** (compile, le serveur démarre ; à vérifier en jeu) — `client/ui/` :
- `ParchmentScreen` : la fenêtre (480 × 260 max), le titre, un fil d'Ariane optionnel (`crumb()`), la ✕. Échap et ✕ reviennent au `parent` (le hub, le catalogue…). `renderContent` / `clicked` pour le contenu, `verticalRule`, `detailPanel`, `actionY`.
- `Palette` : les couleurs du parchemin et les trois couleurs d'état.
- `Ui` : `navItem`, `card`, `button` (principal / secondaire / danger / désactivé), `banner` et `chip` (états OK / WAIT / BAD), `progressBar`, `coin`, `priceLine`, `crateLine`, `label`, `wrapped`, `fit`, `inside`.
- `Icon` : un objet du jeu ou une texture en pixel art (`Icon.module("stats")` pour les icônes de modules MineColonies).
- `ScrollGrid` : la grille de cartes avec défilement à la molette et sa barre de défilement.

`CatalogueScreen` et `StyleScreen` sont rebranchés dessus.

### Phase 1 — Écrans de la première heure (2 à 3 sessions)
- Fenêtre principale de hutte (`layouthuts/layouthutpageactions*`, `windowhutworkerplaceholder`) : niveau, ouvriers, amélioration (avec notre prix et notre durée), production.
- Fiche citoyen (`citizen/` : main, happiness, job, requests, family, nav, windowinteraction) : état, besoins, bonheur, doléances.
- Hôtel de ville (`townhall/windowtownhall` + layoutinfo, layoutstats, layoutcitizens, layoutactions, layoutsettings, layoutpermissions, layoutalliance) : vue d'ensemble de la colonie.
- Détail d'une demande et presse-papiers (`windowrequestdetail`, `windowclipboard`), reliés au panneau des doléances (`client/hud/Grievances.java`).
- Embauche et affectation (`windowhireworker`, `windowassigncitizen`).

### Phase 2 — Modules des bâtiments (3 à 4 sessions)
Les modules génériques d'abord, car ils couvrent la plupart des huttes :
- réglages : layoutsettings, layoutboolsetting, layoutintsetting, layoutstringsetting, layoutblocksetting, layoutcraftingsetting, layoutguardtasksetting ;
- listes : layoutfilterablelist, layoutfilterableentitylist, layoutminimumstock, layoutfoodstock ;
- recettes : layoutlistrecipes, layoutdocrafting ;
- ouvriers : layoutcourierassignment, layoutminerassignment ;
- tâches : layouttasklist, layoutworkorders, layoutrequeststree, layoutselectrequest ;
- statistiques : layoutstatsmodule.

**Réglages faits le 2026-11-04** (`client/hut/SettingsScreen`) : une carte par réglage, avec son nom, ce qu'il fait et le contrôle à droite. Oui/Non pour les interrupteurs, tous les choix côte à côte quand ils tiennent (sinon un bouton ⟳ qui passe au suivant), − / + pour les nombres (Maj : par 10). Un réglage indisponible est grisé et le survol dit pourquoi. Les bâtiments qui ont un réglage d'un autre type (bloc, recette, tâche de garde) gardent la page d'origine pour l'instant.

**Listes, stock, statistiques, recettes et tâches faits le 2026-11-04** (base commune `client/hut/ModuleScreen`, aiguillage `ModuleScreens` + `ModuleViewWindowMixin`) :
- `ItemListScreen` (filtres d'objets, et listes de nourriture avec le niveau de maison requis) et `EntityListScreen` (monstres des gardes, avec l'œuf de chaque monstre) : une carte par élément, verte quand elle est cochée, sur deux colonnes. Recherche, filtre Tous / Cochés / Décochés, « Tout cocher », « Tout décocher » (sur ce qui est affiché) et « Par défaut ».
- `MinimumStockScreen` : chaque objet suivi avec sa quantité en piles (− / +, Maj : par 10) et « Retirer ». « + Ajouter un objet » ouvre `ItemPickerScreen`, notre sélecteur : tous les objets, ceux de l'inventaire d'abord, recherche et quantité.
- `StatsScreen` : période en boutons (Hier, 7 jours, 100 jours, Depuis le début), nombre en gras dans sa colonne, les plus grands d'abord, « Masquer les zéros ».
- `RecipesScreen` : sortie ← ingrédients, outil ou bloc requis, ▲ / ▼ (Maj : tout en haut / en bas), Activer / Désactiver, Oublier. « + Apprendre une recette » ouvre la grille d'artisanat de MineColonies.
- `TasksScreen` : la file du bâtiment (livraisons…), avec le trajet, la priorité et l'état. Un clic ouvre la demande.
Pas faits : les ordres de travail (seulement pour le constructeur, retiré), la grille d'artisanat elle-même (`DOCraftingWindow`).

**Cas particuliers faits le 2026-11-04** :
- parler à un citoyen ouvre sa fiche, et le dialogue à 4 réponses est supprimé (les visiteurs de la taverne le gardent pour le recrutement) ;
- maisons (`HousingScreen` et les habitants dans `HutScreen`), menu du restaurant (`MenuScreen`), champs et parcelles (`FieldsScreen`), outil (`ToolScreen`), niveaux et gardes de la mine (`MineLevelsScreen`, `MineGuardsScreen`) et cimetière (`GraveyardScreen`) ;
- `EnchanterScreen` : les lieux de travail où puiser l'expérience, une carte verte par lieu choisi ;
- `WarehouseOptionsScreen` : « Ranger », et « Agrandir » contre un bloc d'émeraude ;
- `ExpeditionScreen` : l'état, la vie, la faim, l'équipement, les combats et le butin ;
- l'affectation du mineur et du coursier passe par `HireScreen` ;
- la boîte aux lettres devient un bon de commande (`ItemPickerScreen`, bouton « Commander ») ;
- « Tout le stock » (`StockScreen`) : tout ce qui est rangé dans les étagères, triable, et un clic entoure les étagères qui contiennent l'objet ;
- « Aide » (`HelpScreen`) : toutes les pages à la suite.

**Fait ensuite, le 2026-11-04** :
- la colonne de gauche d'un bâtiment (`HutNav`) reste en place sur toutes ses pages, et seule la partie droite change ;
- base `ui/PageScreen` (haut fixe, corps qui défile, champ, contrôles), dont héritent `ModuleScreen` et nos fenêtres ;
- hôtel de ville : `PermissionsScreen` (joueurs, rangs et droits, blocs libres, journal), `AlliancesScreen`, `MercenaryScreen` (tuile « Mercenaires » de la vue d'ensemble) ;
- camp de départ : `SuppliesScreen`, un panneau sur le côté qui pilote la fenêtre d'origine sans l'afficher ;
- nos blocs (`client/blocks`) : banque, banque régionale, trône avec le trésor en onglet, marché, comptoir, choix de culture. Le registre de l'entrepôt devient `hut/TerminalScreen`. Les anciennes fenêtres BlockUI et leurs XML sont supprimées.

Restent dans l'ancien style : les réglages de type bloc, recette et tâche de garde ; la caserne (espions, bannière de ralliement) ; la grille d'artisanat (`DOCraftingWindow`) ; la recherche ; les réglages et statistiques de la colonie ; le journal des quêtes ; le choix du style (`WindowSwitchPack`) ; le renommage d'un bâtiment.

Liste d'origine des cas particuliers : layoutbuilderres, layoutenchanter, layoutexpeditionlog, layoutfarmfields, layoutplantationfields, layoutgraveyard, layoutguardlist, layoutminermodule, layouttool, layoutuniversity, layoutwarehouseoptions.

Déjà modifiés par nos mixins (à intégrer, pas à casser) :
- WindowBuildBuildingMixin (prix) ;
- WindowFieldMixin ;
- WindowBuildDecorationMixin ;
- AbstractWindowModuleBuildingMixin (terminal de l'entrepôt) ;
- `WindowWarehouseTerminal`, `WindowThrone`, `WindowImperialTreasury`.

### Phase 3 — Écrans rares (environ 2 sessions)
- `windowresearch` : à fusionner avec le chantier de l'université.
- `windowhutbarracks`, `windowbarracksspies`, `windowbannerrallyguards`.
- `windowquestlog`, `windowsupplystory`, `windowsupplies`.
- `map/`, `analyzer/`, `windowpostbox`, `windowstash`, `windowdecorationcontroller`, `windowhutguide`.
- `windowresourcescroll`, `windowbrowsebuilding`, `windowhuthome`, `windowselectres`, `windowhutnameentry`.
- Les dialogues : `dialogdonecancel`, `windowinfo`, `windowreactivatebuilding`, `windowsuggestbuildtool`, `townhall/window*` colony management.
- Remplacer `WindowBannerPicker` par l'éditeur de drapeau héraldique, qui n'est toujours pas fait.

## Inventaire

90 layouts XML dans `sources/minecolonies/src/main/resources/assets/minecolonies/gui/` :

| Dossier | Nombre |
|---|---|
| racine | 29 |
| layouthuts | 34 |
| townhall | 14 |
| citizen | 7 |
| map | 4 |
| analyzer | 2 |

## Méthode, pour chaque écran

1. Lire le XML et la classe Java de la fenêtre, puis **lister tout ce que l'écran permet de faire**. Cette checklist de couverture protège contre la perte d'une fonction cachée.
2. Faire une maquette HTML, la vérifier dans le panneau navigateur, et obtenir l'accord de l'utilisateur pour les écrans majeurs.
3. Coder sur le kit commun, puis compiler, vérifier le serveur et relancer le client.
4. L'utilisateur teste et envoie ses captures. On itère.

## Risques

- **Perte de fonctions** cachées dans les modules : la checklist de couverture (étape 1) y répond.
- **Messages réseau** à reproduire pour les écrans remplacés : lire l'action d'origine dans les sources avant de recoder.
- **Je ne vois pas le jeu** : les itérations passent par les captures de l'utilisateur, et c'est le facteur de durée principal.
- **Mises à jour de MineColonies** : la version est figée, donc le risque reste faible.
- **Périmètre** : avancer phase par phase, valider à chaque étape et ne pas tout ouvrir en même temps.
