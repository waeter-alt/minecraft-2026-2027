# Fiche hutte : checklist de couverture

Fenêtre principale d'une hutte avec ouvriers : `windowhutworkerplaceholder.xml` (qui inclut `layouthutpageactions` → `layouthutpageactionsmin` → `layouthutpageactionsminwoinv`). Classes : `AbstractModuleWindow` → `AbstractWindowModuleBuilding` → `AbstractWindowWorkerModuleBuilding` → `WindowHutWorkerModulePlaceholder`, dans `sources/minecolonies/.../core/client/gui/`.

Tout ce qui suit doit exister dans la nouvelle fiche.

## Ce que l'écran permet aujourd'hui

| # | Fonction | Contrôle d'origine | Effet (message ou fenêtre) |
|---|---|---|---|
| 1 | Voir le nom et le niveau | `name` (texte rouge) | `getBuildingDisplayName()` + `getBuildingLevel()` |
| 2 | Renommer la hutte | `editName` (crayon) | ouvre `WindowHutNameEntry` |
| 3 | Voir les ouvriers affectés | liste `workers`, « métier : nom » | tous les `WorkerBuildingModuleView` : une hutte peut avoir plusieurs métiers |
| 4 | Embaucher / gérer les ouvriers | `hire` (« Gérer ») | ouvre `WindowHireWorker` ; refusé au niveau 0 (`allowsAssignment`) avec un message dans le chat |
| 5 | Rappeler les ouvriers à la hutte | `recall` | `RecallCitizenMessage` |
| 6 | Construire, améliorer, réparer | `build` | ouvre `WindowBuildBuilding` (déjà rhabillée par `WindowBuildBuildingMixin` : nos prix) |
| 7 | Annuler un chantier en cours | `build` (libellé changé) | `BuildRequestMessage` en mode BUILD (construction ou amélioration), REPAIR ou REMOVE (démolition) selon `isBuilding` / `isRepairing` / `isDeconstructing` |
| 8 | Priorité de collecte par les livreurs, de 0 (jamais) à 10 | `deliveryPrioDown` / `deliveryPrioUp` + `prioValue` | `ChangeDeliveryPriorityMessage(building, up)` |
| 9 | Faire collecter maintenant | `forcePickup` | `ForcePickupMessage` |
| 10 | Ouvrir le coffre de la hutte | `inventory` | `OpenInventoryMessage` (conteneur vanilla) |
| 11 | Voir l'inventaire total (hutte + ouvriers) | `allinventory` | ouvre `WindowHutAllInventory` ; dans l'entrepôt, notre mixin ouvre `WindowWarehouseTerminal` à la place |
| 12 | Aide sur le bâtiment | `info` (cachet rouge) | ouvre `WindowInfo` ; visible seulement si la traduction `...info.<bâtiment>.0` existe |
| 13 | Passer aux modules du bâtiment | onglets latéraux (icône + infobulle), avec un son de page | `view.getWindow().open()` pour chaque module avec `isPageVisible()` ; l'onglet « principal » revient ici |

Les modules sont, selon la hutte : réglages, recettes, stock minimum, outils, tâches, statistiques, liste de ressources, ordres de travail, couriers, mineurs, journal d'expédition, recherche…

## Variantes qui partagent la même coquille (à traiter après)

- **Maison** (`WindowHutLiving`, `windowhuthome.xml`) : résidents x / max, affecter (`assign`), rappeler (`RecallCitizenHutMessage`).
- **Caserne** (`WindowBarracksBuilding`) : positions des tours, espions (payés en or).
- **Bâtisseur** (`WindowHutBuilderModule`) : même fenêtre ; ses ressources sont dans un module.
- **Hôtel de ville** : fenêtre à part (phase 1, hub).

## Ce qui ne sert plus dans le modpack (traque)

Les bâtisseurs ne sont plus utilisés : construction, amélioration, réparation et démolition passent par nos chantiers payés et chronométrés (`InstantBuilder`, `ConstructionSite`, `DemolitionSite`).

| Élément | Décision |
|---|---|
| Le bâtisseur affecté au chantier | **Retiré** (décidé par l'utilisateur le 2026-11-04) |
| Le choix du bâtisseur dans `WindowBuildBuilding` | À retirer quand on refera cette fenêtre |
| Réparer, démolir | **Gardés** : nos chantiers les gèrent, avec un prix |
| Annuler un chantier (#7) | **Retiré** : un chantier payé va jusqu'au bout (décidé le 2026-11-04) |
| Priorité de collecte et « Faire collecter » (#8, #9) | **Retirés** : `MagicNetwork` vide les huttes vers l'entrepôt toutes les `SWEEP_SECONDS` (10 s), et livre les demandes sans livreur |
| La cabane du bâtisseur dans le catalogue | **Retirée** (`CatalogueIndex.HIDDEN`, 2026-11-04) |
| La cabane du livreur dans le catalogue | **Retirée** : plus aucun rôle avec le réseau magique (2026-11-04) |

## Icônes

Règle : une icône du jeu quand elle a du sens, sinon du pixel art. Les icônes de la maquette sont dans `mod/docs/icons/` (extraites des jars, à ne pas redistribuer).

| Où | Icône | Source |
|---|---|---|
| En-tête | le bloc de la hutte (pain dans la maquette) | objet du jeu |
| Modules (navigation) | l'icône du module lui-même : maison, établi, étagère, engrenage, barres | `minecolonies:textures/gui/modules/*.png`, en pixel art, déjà utilisées par MineColonies, donc pas d'icône générique |
| Coffre / Tout le stock / Aide | coffre, `modules/inventory`, livre | jeu / MineColonies |
| Ouvrier | son visage, découpé dans son skin | MineColonies |
| Production | l'objet produit | jeu |
| Prix | Couronne, caisse régionale, horloge | Interrègne / jeu |
| Demande | `modules/requests` (enveloppe) | MineColonies |
| Collecte | wagonnet | jeu |

## Ce que la nouvelle fiche ajoute

- **L'état du bâtiment en une ligne**, en haut (vert / ambre / rouge) : il travaille, il attend quelque chose, il est bloqué.
- **Les ouvriers en cartes** : nom, métier, ce qu'il fait (travaille, dort, attend une demande), avec ses doléances (lien avec `client/hud/Grievances`).
- **Les demandes en cours** de la hutte : objet, quantité, depuis quand, et qui va la remplir (entrepôt, Intendance avec son délai et son prix, ou un joueur pour les caisses). Aujourd'hui c'est caché dans un module.
- **La production** par minute, avec nos chiffres (`BuildWindowPricing.addProductionRows`).
- **L'amélioration dans notre économie** : prix en Couronnes et caisses (`CrateRows.forCrates`), durée, ce que le niveau suivant débloque. En chantier : une barre de progression et « Annuler ».
- **Les modules deviennent la navigation de gauche** (icône + mot), comme les catégories du catalogue, au lieu des onglets anonymes sur le bord.
- **Mode hub** : un fil d'Ariane « Colonie › Bâtiments › … » quand la fiche est ouverte depuis le hub ; Échap y revient.
