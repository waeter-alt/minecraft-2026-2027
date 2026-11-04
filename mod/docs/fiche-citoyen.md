# Fiche citoyen : checklist de couverture

Fenêtres d'origine : `core/client/gui/citizen/` (`MainWindowCitizen`, `HappinessWindowCitizen`, `JobWindowCitizen`, `RequestWindowCitizen`, `FamilyWindowCitizen`, la barre d'onglets `nav.xml`). La nouvelle fiche est `client/hut/CitizenScreen`. `BOWindowMixin` l'ouvre à la place de `MainWindowCitizen`.

## Ce que l'écran permet aujourd'hui

| # | Fonction | Origine | Dans la nouvelle fiche |
|---|---|---|---|
| 1 | Nom, sexe | `main.xml` | En-tête ; le sexe ne sert à rien en jeu, **retiré** |
| 2 | Santé (cœurs), faim (cuisses) | `createHealthBar`, `createSaturationBar` | Page Fiche, avec les icônes du jeu |
| 3 | Bonheur (smileys) | `createHappinessBar` | Page Fiche : une barre sur 10, de la couleur de l'état |
| 4 | Icône de statut (dort, mange…) | `STATUS_ICON` | Page Fiche, en phrase (« dort ») |
| 5 | 11 compétences avec leur niveau | `createSkillContent` | Page Fiche : une grille, les deux compétences du métier en avant |
| 6 | +/- sur les compétences (créatif seulement) | `AdjustSkillCitizenMessage` | **Retiré** : triche de test, inutile aux joueurs |
| 7 | Métier : compétences principale et secondaire, avec leurs complémentaire et opposée | `job.xml`, `updateJobPage` | Page Fiche : ★ sur les deux compétences du métier. Complémentaire et opposée **retirées** (trop fin pour nos joueurs) |
| 8 | Facteurs de bonheur, avec leur description | `updateHappiness` | Page Bonheur : nom, effet en couleur, description |
| 9 | Demandes du citoyen ; les remplir depuis son inventaire | `RequestWindowCitizen.fulfill` | Page Demandes, avec un bouton « Donner » quand on a l'objet |
| 10 | Famille : parents, partenaire, enfants, frères et sœurs | `FamilyWindowCitizen` | Page Famille ; un clic ouvre leur fiche |
| 11 | Inventaire du citoyen | `OpenInventoryMessage(colony, name, entityId)` | Outil « Inventaire » |

## Ce que la nouvelle fiche ajoute

- **Le lieu de travail et la maison**, cliquables : ils ouvrent la fiche du bâtiment.
- **Les pastilles de la fiche hutte** (Malade, doléances, Malheureux, Content) ; un clic ouvre le dialogue pour régler le problème.
- **Le visage** du citoyen, rendu depuis son modèle.
