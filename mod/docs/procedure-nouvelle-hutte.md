# Procédure : créer une nouvelle hutte (bâtiment MineColonies à nous)

À suivre **à chaque nouvelle hutte** (Relais commercial, Banque régionale, Dépôt Régional, et les suivantes). Chaque point est né d'un défaut trouvé en jeu sur ces trois-là ; on ne les redécouvre pas hutte après hutte.

## 1. Blueprints : cinq niveaux, **une seule emprise**

- Cinq fichiers `<nom>1.blueprint` à `<nom>5.blueprint` dans `mod/src/main/resources/blueprints/interregne/interregne/<dossier>/`, pack partagé « Couronne Vacante » (hors de tout style).
- **Tous les niveaux ont la même taille, comme les blueprints de MineColonies** (l'entrepôt fait 21×22×29 du niveau 1 au niveau 5, les niveaux bas sont complétés par de l'air). La hutte est au **même endroit** dans tous les fichiers.
- Pourquoi : la prévisualisation du niveau 1 montre alors déjà la place que le bâtiment prendra au niveau 5. Sans ça, un petit bâtiment posé près d'une route l'avale en grandissant (constaté avec le Relais).
- Comment : après avoir généré les blueprints, lancer `python -I -X utf8 mod/tools/pad_blueprints.py <dossier> <nom>` (`pip install nbtlib`). Il cale chaque niveau sur la hutte, calcule la boîte qui contient tous les niveaux et complète avec de l'air. Il peut être relancé sans danger ; `--check` n'écrit rien.
- Le test JUnit du bâtiment doit vérifier : une seule hutte à l'ancre, **même taille à tous les niveaux**, même décalage de l'ancre, de plus en plus de blocs (voir `RelaisBlueprintTest`).

## 2. Pose depuis le catalogue

- Le catalogue ajoute le pack partagé à tous les styles. La pose passe par `SharedPackOrigin` et `WindowExtendedBuildToolPackMixin`, qui envoient le pack d'origine du plan et non le style choisi. À **vérifier en jeu** pour chaque nouvelle hutte : poser depuis la baguette, dans un style quelconque, doit donner un chantier et pas « plan introuvable ».
- La hutte doit être dans `CatalogueIndex` (catégorie, nom, description) et dans `building_info.json`.

## 3. Fantôme du niveau maximum des voisins

MineColonies dessine, quand on prévisualise un blueprint à côté d'un bâtiment existant, ce bâtiment à son **niveau maximum** (`NearBuildPreview` dans `ColonyBlueprintRenderer`). Il faut pour cela :

- `getMaxBuildingLevel()` à 5 sur la classe du bâtiment ;
- un chemin de blueprint qui finit par un chiffre (`relais1.blueprint`) : MineColonies remplace ce chiffre par le niveau max ;
- le plan du niveau max dans le pack partagé ;
- l'outil de construction de MineColonies en main.

À vérifier avec `/voisins` (commande client de dev) : le plan du niveau max doit se charger et la ligne du bâtiment doit apparaître avec son temps de chargement. Puis en jeu : approcher un blueprint du bâtiment, le fantôme niveau 5 doit apparaître.

## 4. Fiche hutte : pas d'écran à part

- Une hutte s'ouvre dans la **fiche hutte commune** (`HutScreen`) : en-tête avec le niveau, navigation à gauche, colonne d'amélioration à droite (niveau, ce qu'il débloque, prix, « Améliorer », « Réparer, démolir… »). Jamais un écran indépendant : le joueur ne doit pas être dépaysé.
- Le contenu propre au bâtiment est un `HutBody` (`client/hut/`), enregistré dans `HutBodies`. Les blocs communs (carte héros, bouton principal, panneau de montants, sélecteurs) viennent de `BodyKit`. Pages supplémentaires = `tabs()` du corps.
- Les réponses du serveur mettent à jour un cache côté client (`HutBodies.receive`), elles ne remplacent pas l'écran : la fiche s'ouvre tout de suite et se remplit.
- Contrôle visuel obligatoire : UiShot aux trois tailles par défaut, **0 problème de mise en page** (`docs/outils-de-test.md`, section 3), et ajouter les scénarios du bâtiment dans `UiScenarios`.
- Règles d'interface : `docs/refonte-ui.md` (responsive sans coupure, prix dans le bouton, icônes qui ont du sens).

## 5. Un bâtiment par colonie

Si la hutte est unique par colonie, le serveur ne sert que la plus haute. Un doublon doit recevoir un message qui le dit (et nomme celui qui est actif), pas « pas encore construit ».

## 6. Tests avant de dire « c'est fait »

- JUnit des blueprints (point 1) et des règles du bâtiment.
- UiShot (point 4).
- **En jeu avec un vrai client** : poser depuis la baguette, ouvrir la fiche, améliorer du niveau 1 au niveau 5 en regardant l'emprise, approcher un autre blueprint pour voir le fantôme, poser un deuxième exemplaire si la hutte est unique.
- Outils : `/playtest hutte <colonie> <banque|depot|relais> <niveau>` pose une hutte sans passer par le catalogue (pour voir sa fiche) ; `/playtest inspecter <colonie>` liste les bâtiments d'une colonie ; `/voisins` (client) contrôle le fantôme niveau max.
