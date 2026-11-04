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

La clarté d'abord : la même page parchemin que les huttes (menu à gauche, cartes, tuiles). Seul l'en-tête marque le siège de la colonie : un bandeau de cuir, des filets dorés, la bannière de la colonie, son nom en doré, et le niveau de l'hôtel de ville en médaillon de sa couleur (un clic ouvre l'hôtel de ville). Deux pistes ont été essayées puis écartées : le velours bleu et or (texte agrandi, contraste étrange) et le livre ouvert (moins clair).

## Ce que le hub ajoute

- **Vue d'ensemble** : l'état de la colonie en une ligne (vert, ambre ou rouge), puis la carte d'amélioration de l'hôtel de ville (sur trois colonnes, ou empilée quand c'est étroit), puis des tuiles : citoyens, bonheur moyen, doléances (ouvre les citoyens), Intendance (ce qu'elle réglerait), bâtiments, chantiers, mercenaires.
- **Bâtiments** : chaque bâtiment avec son icône, son niveau en couleurs et son état. Les problèmes sont en tête ; un clic ouvre sa fiche.
- **Citoyens** : chaque citoyen avec son visage, son métier et ses pastilles. Les problèmes sont en tête ; un clic ouvre sa fiche.
- **Intendance** (remplace « Demandes », 2026-11-04) :
  - Deux cartes : Doléances (soigner les malades, nourrir les affamés) et Fournitures des travailleurs (ce que personne dans la colonie ne fournit).
  - Chaque carte a un bouton « Tout régler » avec son prix en pièces et en Influence, et « Voir la liste » pour régler ligne par ligne.
  - Le prix est payé par le trésor de la colonie puis la bourse du joueur, plus l'Influence du joueur. C'est volontairement plus cher que de construire le bon bâtiment (`colony/IntendanceDesk`).
  - Le serveur envoie exactement les lignes qu'il réglera. L'achat automatique d'origine est désactivé par défaut.
- **Murailles** : onglet réservé, en préparation.
- **Colonne de gauche** : les pages en haut, les outils (Carte, Statistiques, Réglages, Permissions, Alliances, Apparence) en bas. Les outils passent en grille d'icônes quand la hauteur manque, puis la colonne défile à la molette.
- Depuis le hub, Échap dans une fiche revient au hub, et la fiche affiche « Colonie › » devant son titre.
