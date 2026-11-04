# Hub de la colonie (hôtel de ville) : checklist de couverture

Fenêtres d'origine : `core/client/gui/townhall/` (`WindowMainPage`, `WindowInfoPage`, `WindowCitizenPage`, `WindowStatsPage`, `WindowSettings`, `WindowPermissionsPage`, `WindowAlliancePage`). La nouvelle fenêtre est `client/hut/ColonyHubScreen`. `BOWindowMixin` l'ouvre à la place de `WindowMainPage`.

## Ce que l'hôtel de ville permet aujourd'hui

| # | Fonction | Origine | Dans le hub (v1) |
|---|---|---|---|
| 1 | Renommer la colonie | `WindowTownHallNameEntry` | ✎ dans l'en-tête |
| 2 | Style de construction, textures, style des noms, couleur d'équipe, bannière | `WindowMainPage` | Outil « Apparence » : ouvre l'ancienne page |
| 3 | Mercenaires | `WindowTownHallMercenary` | **À décider** : payés en émeraudes, hors de notre économie. En attendant, on garde l'accès par « Apparence » |
| 4 | Carte de la colonie | `WindowColonyMap` | Outil « Carte », débloqué au niveau 2. Plus de carte à poser dans le coffre : le serveur dessine lui-même les tuiles depuis le terrain chargé (`colony/ColonyMaps`). Au niveau 1, un message dit d'améliorer l'hôtel de ville |
| 5 | Patreon, changement de spécialisation (bouton vide) | `WindowMainPage` | **Retirés** : publicité et bouton mort |
| 6 | Ordres de travail des bâtisseurs (priorité, suppression) | `WindowInfoPage` | **Retiré** : nos chantiers sont payés et chronométrés, il n'y a plus de file de bâtisseurs |
| 7 | Liste des citoyens, rappeler un citoyen | `WindowCitizenPage` | Page Citoyens ; un clic ouvre la fiche citoyen (qui a déjà le bouton Rappeler de sa hutte) |
| 8 | Statistiques | `WindowStatsPage` | Outil « Statistiques » : ancienne page, à refaire en phase 3 |
| 9 | Réglages de la colonie | `WindowSettings` | Outil « Réglages » : ancienne page |
| 10 | Permissions et rangs | `WindowPermissionsPage` | Outil « Permissions » : ancienne page |
| 11 | Alliances et téléportation | `WindowAlliancePage` | Outil « Alliances » : ancienne page, à refaire avec la diplomatie |

## Style

Le hub a son propre cadre : velours bleu nuit, double bordure dorée, bannière et nom de la colonie en grand, et le niveau de l'hôtel de ville en médaillon. Les pages sont des onglets, les outils des icônes à droite. Les huttes restent en parchemin, pour qu'on sente qu'on est au siège de la colonie.

## Ce que le hub ajoute

- **Vue d'ensemble** : l'état de la colonie en une ligne (vert, ambre ou rouge), puis des tuiles : citoyens, bonheur moyen, doléances, demandes, chantiers. Et un accès à la fiche de l'hôtel de ville pour l'améliorer.
- **Bâtiments** : chaque bâtiment avec son icône, son niveau en couleurs et son état. Les problèmes sont en tête ; un clic ouvre sa fiche.
- **Citoyens** : chaque citoyen avec son visage, son métier et ses pastilles. Les problèmes sont en tête ; un clic ouvre sa fiche.
- **Demandes** : toutes les demandes ouvertes de la colonie, avec le bâtiment qui attend.
- Depuis le hub, Échap dans une fiche revient au hub, et la fiche affiche « Colonie › » devant son titre.
