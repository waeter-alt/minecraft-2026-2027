# Arène PvP, progression et spectacle : décisions de design

Séance de brainstorming du 2026-11-16 (Duncan et Claude). Ce document rassemble **tout ce qui a été décidé**, ce qui reste ouvert, ce qui a été vérifié techniquement et ce qui ne l'a pas été. Rien de tout ça n'est encore implémenté, sauf l'ajout des mods au pack du lanceur (voir § 9).

Légende : **Décidé** = validé par Duncan. **Ouvert** = à trancher. **Vérifié** = lu dans le code des mods ou testé. **Non vérifié** = hypothèse.

---

## 1. Principe général

- Il y a **7 arènes**, une par jour de la semaine, chacune avec un danger propre (Feu, Glace, Jungle suspendue, Piège/Laboratoire, Portes, Ténèbres, Abysses). Les sources sont dans `atelier/` (fiches dans `atelier/docs/arene_*.md`).
- Les **objets de donjon** servent dans l'arène (ex. le voile de respiration aux Abysses).
- Le **loot est déjà assuré par les donjons** : l'arène donne du **prestige, de l'Influence et de l'XP**, pas des objets.
- **Pas de tournois**, **pas de bonus de colonie de 24 h** (idées abandonnées).

## 2. Déroulement d'une partie

- **Une seule arène ouverte par jour**, qui tourne sur sept jours. Pas de dispersion des joueurs.
- **Ouverte en continu** pendant la journée, sans créneau fixe.
- À la mort : retour au **hub**, puis **ré-entrée immédiate** possible dans la même arène.
- **On ne perd rien en mourant** (pas de drop, inventaire conservé).
- Conséquences à prévoir : quelques secondes de **protection à l'entrée** (levée si on attaque), **plusieurs points d'entrée**, série et survie remises à zéro à chaque mort, plafond journalier important.

## 3. Équipement à l'entrée (menu d'entrée)

Le joueur choisit au menu :

- **Kit standard par classe** (une vitrine du mod RPG Series). Les kits sont fournis, identiques pour tous, avec un **arbre de compétences préconfiguré partiel** (un « avant-goût » : tester une classe sans équipement de fin de partie).
- **Son propre équipement**, **sans plafond** (on récompense l'investissement et les builds personnels).
- Les **objets de donjon** peuvent entrer dans les deux modes.
- Le **mode du joueur est visible** (étiquette ou couleur).
- **Munitions limitées** dans l'arène, avec des **bonus à capturer** pour en récupérer. Idée : unifier toutes les runes en un seul objet commun.
- **Sauvegarde et restitution** de l'inventaire (et de l'arbre de compétences) à l'entrée et à la sortie du mode kit, y compris en cas de plantage ou de déconnexion. **Point à rendre très solide.**

## 4. Score et récompenses

- L'arène doit être **le meilleur moyen de gagner de l'Influence**, tout en gardant d'autres sources (quêtes du soir, etc.) moins rentables mais sûres.
- Sources de points :
  - survie passive (faible, seulement en zone active) ;
  - éliminations ;
  - séries façon League of Legends : double, triple, quadruple, penta ;
  - **shutdown** : tuer un joueur en série prend une prime ;
  - ace (équipe entière), première goutte de sang ;
  - **objectifs à capturer et à défendre**, qui pèsent le plus.
- **Valeur d'une élimination à montants fixes** : éliminer un joueur en **kit** rapporte moins, éliminer un joueur venu avec son **propre équipement** rapporte plus. Seul l'éliminateur en profite (l'équipement perso donne déjà un avantage).
- Garde-fous : plafond journalier à rendements décroissants, points décroissants contre un même adversaire, plancher de participation.
- **Ranked** : score avec paliers, titres de cour, **récompenses journalières selon le rang** (non-objets : Couronnes ou Influence). **Ouvert** : rang par joueur seulement, ou aussi par colonie (le score est de toute façon additionné par colonie).
- Enjeux par arène (idées, à concevoir) : poches d'air (Abysses), balises (Ténèbres), roue Create (Piège), forge (Feu), plateformes (Jungle, Portes, Glace).

## 5. Spectacle de l'arène

Direction : **maximiser le spectacle**.

- **Annonceuse façon League of Legends** : entrée en arène, première goutte de sang, double à penta, shutdown, ace, capture d'objectif. Texte à l'écran en parallèle du son.
- Sons ambiants, **foule** qui monte avec l'activité, applaudissements et acclamations à chaque élimination.
- **Effets visuels visibles dans toute l'arène** (gerbes, faisceau au-dessus du joueur en série), légers pour les performances (arènes d'environ 104 blocs).
- **Musique** éventuelle, une piste par arène.
- **Décision de Duncan** : utiliser les voix de l'annonceuse de League of Legends (serveur privé d'une dizaine d'amis, sans but commercial ; il assume le risque). **Les fichiers audio ne doivent pas aller dans le pack public ni dans un dépôt public.**
- Un seul système d'événements alimente score, annonceuse, public, XP.

## 6. XP et niveaux (système maison)

- Un **niveau de joueur** alimente l'arbre de compétences (Skill Tree). Il est **différent de l'XP vanille**.
- **Une seule réserve d'XP** pour les deux arbres (classe et armes).
- **Niveau maximum** calé sur les points nécessaires pour **maxer une classe complète avec ses armes** (aujourd'hui 28 + 12 = 40 dans le datapack de test ; le 12 des armes est un chiffre provisoire).
- Sources d'XP :
  - arène (survie, éliminations) ;
  - donjon terminé ;
  - monstres tués (avec la limite anti-farm du mod) ;
  - MineColonies : poser et améliorer des bâtiments, **plafonné à une récompense par type de bâtiment**, plus hôtel de ville et murailles. **L'XP des bâtiments va à toute la colonie.**
- **Affichage** : barre d'XP **toujours visible** à l'écran, distincte de la barre vanille, et valeurs « +n xp » à chaque gain. Même exigence de qualité que le reste de l'interface (compacte, suit l'échelle, sans chevauchement).
- **Ouvert** : comment les points d'un niveau se répartissent entre l'arbre de classe et celui des armes (automatique ou au choix du joueur ; je penchais pour le choix).
- L'arène devrait rester la source d'XP la plus rentable à l'heure.

## 7. Compétences (Skill Tree) et sorts

### Skill Tree (Vérifié, lu dans le jar de la version 1.6.1)

- Deux catégories Pufferfish : `class_skills` (224 nœuds, 8 racines : arcane, feu, givre, archer, paladin, prêtre, voleur, guerrier ; **une seule classe à la fois**, `exclusive_root`) et `weapon_skills` (48 nœuds, 16 racines d'armes).
- Limites de points d'origine : 13 (classes) et 6 (armes). Une classe compte 28 nœuds. **Décidé** : points limités, le maximum permet de maxer **une classe complète avec ses armes**. On relève les limites par un datapack qui remplace les `category.json`.
- Aujourd'hui les points viennent de `kill_entity` (XP lâchée + vie max / 40, anti-farm 15 par chunk toutes les 5 min, partage d'équipe 200 blocs). **Décidé** : remplacer par le système d'XP maison. Pufferfish a déjà des sources (récolte, artisanat, soins, dégâts, critère d'advancement…) et des commandes (`experience`, `level`, `points`, `skills`, `category`, `open`). Brancher notre système devrait se faire par données, et au plus avec un peu de code dans Interrègne (Non vérifié).
- **Orbe d'Oblivion** (remise à zéro de l'arbre) : **achetable** dans la boutique de la citadelle impériale, et candidat de butin rare.
- Touche d'ouverture de l'arbre : **K** (Vérifié).

### Maître de compétence (Décidé)

- **Un seul PNJ** (pas un par classe), boutique à la **citadelle impériale**.
- Il **remplace le bloc d'enchantement de sorts** de Spell Engine. **Pas de parchemins à trouver ni à stocker.**
- Menu **à onglets** : catalogue des sorts et compétences de chaque classe. On compose sa « commande » de sorts et on les **lie à son arme**.
- **Le joueur ne débloque aucun sort : il paie une prestation** au PNJ. **Le re-lien est payant à chaque fois.** On peut le faire autant de fois qu'on veut, y compris pour changer les emplacements d'une arme déjà utilisée.
- Le **prix suit le palier** du sort (chaque sort a un `tier`). Puits de Couronnes.
- Interface : le prix dans le bouton (vert si payable, grisé sinon), compacte, qui suit l'échelle de l'interface, vérifiée par captures UiShot.
- Les armes des **kits** sont liées d'avance, sans passer par le Maître.
- **Vérifié** (lu dans Spell Engine 1.10.9 et Wizards 3.1.3) : un sort est un fichier de données (école, palier…) ; les listes par livre ou par parchemin sont des tags ; les sorts d'un joueur viennent de **plusieurs sources fusionnées** (conteneur de l'arme tenue, livre, parchemin, conteneur propre au joueur) ; le bloc d'enchantement fonctionne par offres et prérequis.
- **Non vérifié** : comment écrire des sorts dans le conteneur d'une arme (emplacements maximum, écoles autorisées), et si on peut mélanger les écoles ou les classes sur une même arme. **Exploration en lecture seule à faire** (sous-agent Opus ciblé selon la politique de routage), **mise en attente** faute de quota. Sans verrou de classe sur les sorts, un Mage peut acheter des sorts de Paladin : à regarder à l'équilibrage.

## 8. Loot de donjon

- Les coffres de récompense des donjons utiliseront le mod **GachaAddiction** (NeoForge 1.21.1). Les donjons sont **répétables**, on y trouve du matériel RPG Series, **y compris des armes épiques à taux de drop rares**, aussi trouvables dans l'overworld.
- **Vérifié** : GachaAddiction n'ajoute que l'**animation** (rouleau, lueur de rareté, son). Les **taux de drop viennent des tables de butin** que nous écrivons nous-mêmes (travail de datapack : poids de rareté, quelles armes d'Arsenal, quels bijoux, quelles reliques par donjon).
- Risque d'équilibre : l'équipement sans plafond de l'arène récompense le temps passé à farmer les donjons. Les taux rares sont le frein.

## 9. État technique

- **Branche** `feature/mods-rpg-complementaires` (créée depuis `main`), **non commitée**. 17 fiches de `launcher/pack/mods/*.pw.toml` modifiées ou ajoutées, `index.toml` et `pack.toml` recalculés.
- **Pile RPG Series montée** (tous les jars testés compatibles avec NeoForge 21.1.216, minimum demandé 21.1.201) : Spell Engine 1.10.9, Spell Power 1.6.0, Wizards / Paladins & Priests / Rogues & Warriors / Archers 3.1.3, Jewelry 2.5.1, Runes 1.3.2, Arsenal 1.5.0, **Relics 1.4.0**, **Armory 1.5.2**, **Skill Tree 1.6.1**, More RPG Library 2.8.2, Elemental Wizards 3.2.2, Gazebos 2.2.0, **Armor Model API 1.1.0** (nouveau), **Pufferfish's Skills 0.19.2**, **GachaAddiction 1.1.4**. Non touchés : Ranged Weapon API 2.3.3, Structure Pool API, Bundle/Shield API, Better Combat, Critical Strike. AzureLib Armor n'est plus requis (retirable). Non ajouté : Combat Roll (à tester en PvP).
- **Testé** : serveur de test avec les 30 mods démarre sans erreur ; le client ouvre le monde de test et se ferme proprement.
- **Non testé en jeu** : le fonctionnement de l'arbre de compétences et l'effet réel du datapack qui relève les limites de points.
- **Datapack de test** : `mod/run/server/rpgtest/datapacks/rpg_limits/` (copie dans `mod/run/client/saves/rpgtest`). Classes 13 → 28, armes 6 → 12.
- **Plantages du client** : trois plantages natifs du JVM avec la pile complète, **très probablement causés par Overwolf** (sa bibliothèque et ses fils sont chargés dans Minecraft). Avec Overwolf fermé, un essai complet a réussi. Un seul essai, donc probable et pas certain.
- **À faire pour le lanceur final** : les amis de Duncan ont probablement Overwolf. Tester avec Overwolf actif, chercher comment désactiver son support de Minecraft, l'écrire dans le guide joueur.
- **Nettoyage en attente** : 30 jars de test dans `mod/run/client/mods/` (seul `grappin-2.6.1.jar` doit y rester). Ces dossiers sont ignorés par git.

## 10. Questions encore ouvertes

1. Rang ranked par joueur seulement, ou aussi par colonie.
2. Répartition des points d'un niveau entre arbre de classe et arbre d'armes.
3. Source des points d'XP maison : fichier de données seul, ou petit code dans Interrègne (à vérifier).
4. Comment écrire les sorts dans le conteneur d'une arme et jusqu'où mélanger les classes (exploration en attente).
5. Chiffres : plafond journalier, valeur des éliminations kit et équipement perso, prix des sorts par palier, quantité de munitions, limite de points des armes (12 provisoire).
6. Combat Roll, Skill Tree en PvP, équilibrage des sorts et des soins RPG Series : **grosse passe d'équilibrage plus tard**.

## 11. Ordre de travail suggéré (à valider)

1. Valider l'arbre de compétences en jeu (touche K, commandes `/puffish_skills points add …`), puis commiter la branche mods après relecture de Duncan.
2. Exploration en lecture seule du conteneur de sorts, puis conception du Maître de compétence.
3. Système d'XP et de niveaux : moteur, source maison, affichage (barre + « +n xp »).
4. Arène : entrée avec menu kit / perso, score, objectifs, ranked.
5. Spectacle : annonceuse, foule, effets (fichiers audio fournis par Duncan, hors dépôt public).
6. Tables de butin des donjons avec GachaAddiction.
7. Grosse passe d'équilibrage.
