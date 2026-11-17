# Plan de test du playtest multi : diplomatie, commerce, routes

Tout ce qui est listé ici a été vérifié **sans client réel** (serveur de test piloté en rcon, GameTests, JUnit, captures d'écran des écrans). Ce plan couvre ce qui n'a **jamais été joué à la main**. Branches : diplomatie = PR #35, commerce + routes = PR #36 (empilée sur #35).

Cocher `[x]` quand c'est bon, noter ce qui cloche à côté (message exact, capture si possible).

## 0. Préparation

- **Monde** : `ColoniesV2` (2 colonies, « Vielle Ville » n° 1 et « Ville Neuve » n° 2, déjà reliées par une route de 480 blocs), à copier dans `mod/run/client/saves/` (ou le serveur de test). Pour la section guerre/pillage il faut une **troisième colonie** à plus de 128 blocs des autres (fondation en créatif possible, voir `outils-de-test.md`).
- **Joueurs** : au moins 2 (un membre de chaque colonie). Idéalement 3 ou 4 pour la guerre.
- **Argent** : un joueur sans Couronnes, un avec beaucoup. Raccourcis admin (op) : `/couronnes frapper`, `/commerce tresor <colonie> <montant>`, `/commerce caisses <colonie> <type> <n>`.
- **Raccourcis de test** (op) : `/diplomatie allier|rompre|guerre|paix|etat <a> <b> [immediat]`, `/commerce relais|chantier|offre|accepter|retirer|etat`, `/routes liste|niveau|ameliorer|fixer`. Voir `outils-de-test.md`.
- **Réglage utile** : dans la config serveur, mettre `caravanBlocksPerSecond` à 10 et `roadUpgradeSeconds` à 10 (minimum autorisé) pour ne pas attendre.
- Noter la version du jeu et qui joue quoi. Garder la console serveur ouverte pour les erreurs.

## 1. Diplomatie (PR #35)

Jamais cliqué dans le vrai écran. Hôtel de ville > onglet Diplomatie.

- [ ] **A1 Proposer** : joueur 1 clique « Proposer » sur Ville Neuve. Joueur 2 voit « Proposition reçue » (message chat + écran à jour sans rouvrir).
- [ ] **A2 Accepter / refuser** : joueur 2 accepte → les deux sont « Alliés ». Refaire avec un refus, puis avec un retrait par l'émetteur.
- [ ] **A3 Rompre** : bouton avec confirmation. Après rupture, la route entre les deux colonies disparaît progressivement (chunks chargés), plus d'alliance.
- [ ] **A4 Droits** : un membre sans le rang requis ne peut-il pas agir ? (le code vérifie `EDIT_PERMISSIONS`, à confirmer avec un simple citoyen/visiteur.)
- [ ] **A5 Guerre, coût** : « Guerre · 20 ★ » demande une 2e confirmation (« Confirmer · 20 ★ »). Avec moins de 20 d'Influence : bouton grisé ; le serveur refuse aussi si l'écran est périmé. L'Influence baisse de 20 pour celui qui déclare, pas pour les autres.
- [ ] **A6 Préavis** : compte à rebours « ouverte dans m:ss » côté attaquant et défenseur, message chat au départ, message « guerre ouverte » à la fin du préavis (3 min).
- [ ] **A7 Règles** : pas de guerre contre un allié ; pas d'alliance en temps de guerre ; seul celui qui a déclaré voit « Faire la paix ».
- [ ] **A8 Assaut** : un assaut contre une colonie sans guerre ouverte est refusé ; `/war assault` de dev reste possible. (Il n'y a **pas encore d'interface** pour lancer un assaut depuis une guerre : normal.)
- [ ] **A9 Disparition de l'ancien écran Alliances de MineColonies** : plus d'entrée « Alliances » dans le hub. La téléportation chez un allié n'existe plus (connu).

## 2. Relais commercial : construction (PR #36)

- [ ] **B1 Catalogue** : dans la baguette de construction, le Relais apparaît dans **chaque style** (catégorie Logistique), avec nom et description, mais **pas** de style « Couronne Vacante » dans la galerie des styles.
- [ ] **B2 Pose** : le placer depuis le catalogue, hors d'une colonie et dans une colonie (il doit n'être accepté que dans la colonie), puis demander le chantier. La construction instantanée le bâtit sans bâtisseur.
- [ ] **B3 Apparence** : le bloc-hutte (cube à enseigne marchande, 4 orientations, objet en main/inventaire) est lisible ; le bâtiment du niveau 1 est joli et la hutte accessible à l'intérieur.
- [ ] **B4 Améliorations 1 → 5** : monter jusqu'au niveau 5 (coûts MineColonies/mod). À chaque niveau, regarder le résultat (compare à `screenshots/relais/relais_planche.png`) : rien ne flotte, la hutte reste en place, la cour du niveau 4 et le caravansérail du niveau 5 ne gênent pas.
- [ ] **B5 Terrains** : essayer sur pente, sur eau proche, près d'arbres.

## 3. Relais commercial : contrats

Prérequis : colonies alliées et reliées, Relais construit (niveau ≥ 1) dans les deux, caisses à l'entrepôt du vendeur, argent chez l'acheteur.

- [ ] **C1 Fenêtre** : clic droit sur la hutte ouvre « Relais commercial » (et non l'ancien écran). En construction : message « doit être terminé ».
- [ ] **C2 Offre** : choisir acheteur (flèches), type de caisse, quantité, prix, puis « Proposer ». Refus lisibles : pas assez de caisses, trop de contrats, pas allié, pas de route.
- [ ] **C3 Capacité** : 2 contrats par niveau du Relais ; essayer d'en faire un de trop.
- [ ] **C4 Acceptation** : le joueur 2 voit l'offre dans son Relais, « Accepter ». Les caisses sortent de l'entrepôt du vendeur **sans qu'il ait rien déposé**. Le prix part du trésor régional de l'acheteur d'abord, puis de sa bourse. Cas : acheteur sans assez d'argent (refus), vendeur dont l'entrepôt s'est vidé entre-temps.
- [ ] **C5 Retrait** : le vendeur retire une offre ; l'acheteur peut la décliner.
- [ ] **C6 Livraison** : à l'arrivée, caisses à l'entrepôt de l'acheteur, prix au trésor du vendeur, messages chat aux deux colonies. Entrepôt plein : la caravane attend et réessaie.
- [ ] **C7 Entrepôt lointain** : vendeur et acheteur éloignés de tous les joueurs : l'offre et l'acceptation marchent quand même (les chunks de l'entrepôt sont chargés à la demande).
- [ ] **C8 Rupture en route** : rompre l'alliance pendant le trajet : caisses rendues au vendeur, prix rendu à l'acheteur, caravane qui disparaît.
- [ ] **C9 Redémarrage** : couper et relancer le serveur pendant un trajet : le contrat reprend, la caravane réapparaît sans doublon.
- [ ] **C10 Plusieurs contrats** à la fois, dans les deux sens, sans mélange.

## 4. Caravane : modèles, carte, escorte

- [ ] **D1 Modèle** : près de la route, on voit une **mule avec coffre** et un **conducteur** (visage/habits de citoyen MineColonies) qui marchent côte à côte, sans glisser, sans rester dans le sol, avec animation de marche. Contrôler les **chapeaux/textures** du conducteur et des soldats (point non vérifié).
- [ ] **D2 Hauteur du terrain** : sur une pente ou un pont, la mule et le conducteur restent à la bonne hauteur (non ajustée dans le code).
- [ ] **D3 Pas d'accès** : impossible de monter la mule ou d'ouvrir son coffre.
- [ ] **D4 Apparition** : s'éloigner à plus de 100 blocs : les animaux disparaissent, le contrat continue ; revenir : ils réapparaissent à la bonne position.
- [ ] **D5 Carte du royaume** : touche **M** (reconfigurable) et bouton du Relais. On voit colonies, routes, caravanes (nom du contrat, sens). Zoom molette, glisser pour déplacer, Début pour tout voir. Vue par **un joueur d'une autre colonie**.
- [ ] **D6 Escorte, achat** : choisir un palier à l'offre : coût indiqué (150 / 400 / 900 / 2000 / 4500), plafonné par le niveau du Relais, payé trésor puis bourse. Retirer l'offre avant départ = remboursé ; rupture avant départ = remboursé ; après départ = non.
- [ ] **D7 Escorte, comportement** : les soldats marchent avec la caravane, s'arrêtent pour combattre, ne reviennent pas une fois morts.
- [ ] **D8 Escorte, force** : un joueur seul équipé de base peut-il vaincre le **palier 1** ? (le test simulé dit que presque jamais : à trancher, voir `equilibrage.md`). Essayer aussi le palier 3 contre 2 joueurs.

## 5. Pillage et guerre (nécessite 3 colonies)

- [ ] **E1 Sans guerre** : un joueur d'une colonie tierce ne peut **pas** blesser la mule ni le conducteur.
- [ ] **E2 En guerre** : colonie tierce **en guerre ouverte** avec le vendeur ou l'acheteur : elle peut attaquer la caravane. Tant qu'un soldat vit, mule et conducteur sont intouchables ; après leur mort, tuer la mule ou le conducteur = contrat perdu, caisses au sol, prix rendu à l'acheteur, messages aux deux colonies.
- [ ] **E3 Membres des colonies du contrat** : le vendeur et l'acheteur ne peuvent pas blesser leur propre caravane.
- [ ] **E4 Ramassage** : les caisses lâchées sont ramassables et utilisables normalement.

## 6. Routes : amélioration (PR #36)

- [ ] **F1 Écran** : clic sur un Poste commercial dont la route est à améliorer ouvre l'écran « Route » (niveau, les cinq niveaux, prix du prochain). Poste trop loin de la route (> 16 blocs) : pas d'écran ; > 12 blocs du poste : la requête est ignorée.
- [ ] **F2 Bouton** : « Améliorer au niveau N · prix » paie (trésor puis bourse) et lance les travaux ; grisé avec le manque en rouge si trop cher ; grisé pendant les travaux ; l'écran se met à jour à la fin. Sneak + clic = raccourci direct.
- [ ] **F3 Créatif** : gratuit pour un joueur créatif.
- [ ] **F4 Apparence par niveau** : monter de 1 à 5 et regarder surface, bordures, lampadaires, **ponts** et **tunnels** à chaque palier. Le niveau 5 est-il « voie romaine » ? Y a-t-il des blocs qui flottent, des trous, des lampadaires dans le chemin ?
- [ ] **F5 Colonie** : les cases à l'intérieur des terres d'une colonie restent au niveau 1 (connu) ; les portes de muraille et le raccordement ne sont pas abîmés.
- [ ] **F6 Constructions voisines** : une construction de joueur en blocs identiques collée à la route n'est pas abîmée (risque connu).
- [ ] **F7 Rupture** : rompre l'alliance sur une route améliorée : elle s'efface (chunks chargés). Piles de pont et murs de tunnel peuvent rester (connu).
- [ ] **F8 Plusieurs joueurs** : deux joueurs qui cliquent le bouton en même temps n'achètent qu'une fois.

## 7. Effets des routes sur les joueurs

À tester avec **n'importe quel joueur** (aucune appartenance de colonie requise), niveaux 1 à 5.

- [ ] **G1 Vitesse** : sensation de +10 % (niv. 1) à +50 % (niv. 5) en marchant sur la route, retour normal à pied hors route.
- [ ] **G2 Autres bonus** : saut plus haut (niv. 3+), montée des marches sans sauter (niv. 5), chute de 1 à 3 blocs sans dégât, régénération (niv. 4+) et résistance (niv. 5).
- [ ] **G3 Retrait** : quitter la route, mourir, se déconnecter, changer de dimension (Nether) : plus aucun bonus restant. Se reconnecter sur la route : bonus de nouveau.
- [ ] **G4 Mobiles** : monture, bateau, élytres, sneak, sprint : pas de bug étrange (vitesse cumulée, tremblements).
- [ ] **G5 Joueur d'une autre colonie**, et joueur sans colonie : les effets s'appliquent aussi.
- [ ] **G6 Potions** : un joueur avec une potion plus forte ou plus longue ne la perd pas en marchant sur la route.
- [ ] **G7 Performance** : 4 joueurs sur des routes en même temps, avec une caravane : pas de ralentissement visible du serveur.

## 8. Écrans, interface et divers

- [ ] **H1 Tailles d'interface** : refaire C1, D5, F1 à l'échelle GUI 2 et 4 : aucun texte coupé ou qui se chevauche.
- [ ] **H2 Messages** : relire les messages chat (alliance, guerre, contrat, caravane, travaux) : fautes, doublons, messages en trop.
- [ ] **H3 Console serveur** : aucune exception du mod pendant toute la session (grep `fr.kodistan`). Le message « `null1.blueprint` NoSuchFile » au premier Relais est du bruit connu.
- [ ] **H4 Hutte et blocs** : la hutte ne se casse pas en laissant un objet inutilisable ; elle est dans l'onglet créatif « commerce ».

## Ordre conseillé (≈ 1 h 30)

1. Section 0, puis 1 (A1 à A3) pour avoir des alliés.
2. 2 (B1 à B4), 3 (C1 à C6) et 4 (D1 à D5) dans la foulée : c'est le fil rouge.
3. Section 6 puis 7 pendant qu'une caravane marche.
4. Sections 1 (A5 à A8) et 5 en dernier, avec la 3e colonie et plusieurs joueurs.
5. 4 (D6 à D8) pour l'escorte, puis H1 à H4.

## Ce qui n'est volontairement pas à tester

Interface pour lancer un assaut après une déclaration de guerre (pas encore faite), équilibrage des prix des contrats (voir `equilibrage.md`), téléportation chez un allié (supprimée), caravanes via une colonie intermédiaire (non gérées).
