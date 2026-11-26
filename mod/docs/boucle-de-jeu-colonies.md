# Boucle de jeu : colonies, diplomatie, guerre

*Boucle validée par Duncan le 2026-11-23 (choix A à E). Seuls les détails marqués « à régler » restent ouverts. Rien n'est codé à cette date.*

## L'idée en une phrase

Une colonie prospère grâce à son économie et à ses routes. Elle peut s'allier, ce qui ouvre le commerce, ou déclarer la guerre, ce qui ouvre le siège. Chaque issue rapporte ou coûte de l'Influence, et l'Influence décide qui sera couronné.

## Ce qui existe déjà (vérifié dans le code le 2026-11-23)

| Brique | État |
|---|---|
| Alliances (proposer, accepter, retirer, rompre) | En place, onglet Diplomatie de l'hôtel de ville |
| Déclaration de guerre | En place : coûte de l'Influence, préavis, puis guerre ouverte jusqu'à la paix du déclarant |
| Alliance ↔ guerre | En place : les deux s'excluent, on rompt avant de déclarer |
| Commerce (contrats, caravanes) | En place, exige une alliance **et** une route |
| Siège (légionnaires, archères, murailles, ruines) | En place, refusé sans guerre ouverte ni en zone sûre |
| Butin | En place : 25 couronnes par niveau de bâtiment détruit, versées au trésor régional de l'attaquant |
| Bouton « Tout reconstruire » | **Fait** (la note « à faire » de ma mémoire était périmée) |
| Dîme, édits, présence | En place (sources d'Influence) |
| Influence des interactions | **Fait** (26/11) : voir « Sources d'Influence » ci-dessous |

## La boucle proposée

**Paix (état normal)** : on construit, on produit, on commerce avec ses alliés.

1. **Alliance** : proposition, acceptation, puis commerce possible dès qu'une route relie les deux colonies.
2. **Rupture** : l'alliance tombe, les contrats en cours sont remboursés, la route **reste** (décision du 19/11).
3. **Déclaration de guerre** : coût en Influence, plus une route de guerre bâtie automatiquement aux frais du déclarant s'il n'y en a pas. Sans les fonds, pas de guerre. Un préavis court avant l'ouverture.
4. **Guerre ouverte** : les troupes marchent sur la route, assiègent, détruisent, pillent.
5. **Fin de guerre** ⚠ : voir plus bas.
6. **Après-guerre** : le perdant reconstruit en un clic, protégé par un bouclier le temps de souffler.

## Ce qui manque pour que ce soit une vraie boucle

1. **Fin de guerre.** Aujourd'hui seul le déclarant peut y mettre fin, par la paix. Le défenseur ne peut rien faire et il n'y a pas de victoire.
2. **Bouclier du perdant.** Décidé en principe le 04/11, absent du code. Une colonie peut être rasée en boucle.
3. **Butin créé de rien.** Le trésor de l'attaquant est crédité, celui du défenseur n'est pas débité. La décision du 03/11 prévoyait un pillage plafonné avec une réserve inviolable.
4. **Influence non branchée** (réglé, voir « Sources d'Influence »). Elle ne venait que de la présence, de la dîme, des édits et de la victoire.
5. **Route de guerre automatique.** Décidée le 19/11, absente de la déclaration de guerre : les troupes marchent à travers champs.
6. **Rupture d'alliance et routes.** Le code fait encore disparaître la route, contre la décision du 19/11 (« les routes restent »). C'est un reste à corriger.
7. **Garnison achetée.** Gardes achetés depuis l'Académie et l'Archerie, caserne, tours : décidée le 19/11. Je n'ai pas pu confirmer dans le code que tout soit livré, à vérifier.

## Choix à trancher ⚠

**A. Comment une guerre se termine-t-elle ?** ✅ Validé par Duncan : trois issues.
- La paix, proposée par le déclarant (existe déjà).
- La capitulation du défenseur, qui paie un tribut en couronnes.
- La chute de l'hôtel de ville : victoire immédiate de l'attaquant, la guerre se ferme, le perdant reçoit un bouclier.
- La **reddition imposée par le défenseur** ✅ (Duncan, 26/11) : une fois un assaut repoussé, ou après 6 h sans assaut de l'attaquant, le défenseur peut exiger la reddition ; il gagne l'Influence et un tribut plafonné pris au trésor de l'attaquant, qui reçoit le bouclier.
- Un hôtel de ville laissé **en ruine** ne protège plus : les troupes le prennent en restant 60 s cumulées sur place, ce qui vaut chute.
- **Une colonie à la fois** : une défenseuse est réservée pendant 2 h à la première qui l'assaille ; les autres colonies en guerre contre elle attendent.

**B. Que rapporte une victoire ?** ✅ Validé par Duncan : le butin est pris dans le trésor régional du perdant, plafonné, avec une réserve inviolable, plus de l'Influence pour le vainqueur. Le perdant garde de quoi reconstruire.

**C. Combien dure le bouclier ?** ✅ Décidé par Duncan : 2 h.

**D. Où l'on gagne de l'or** ✅ Validé par Duncan. Deux monnaies séparées : l'**Influence** récompense les interactions entre joueurs (arène, diplomatie, victoires de guerre) et décide du couronnement ; l'**or** récompense ce que fait la colonie. Le commerce rapporte donc de l'or, pas de l'Influence.

Sources d'or :
1. **Vente des surplus à la citadelle impériale** (marché impérial, prix qui baisse quand le stock monte). Deux façons d'y livrer :
   - des **allers-retours à pied**, alourdis par le poids des caisses ;
   - une **route commerciale vers la citadelle**, avec la même logique que les routes entre joueurs, mais **automatique** : aucune confirmation, l'Empire accepte toujours.
2. Les routes et contrats entre joueurs (plus rentables, plus exposés).
3. Les donjons (une récompense de boss par nuit et par joueur, côté Luca).
4. Le butin de guerre (pris au perdant, point B).
5. Les primes et les paris de l'arène (l'or circule sans être créé).

Puits d'or : constructions, Intendance, caisses de matériaux, frais de guerre, frais de retrait de la banque, or porté perdu à la mort. Tous les montants dans une config réglable par les admins, pour corriger l'inflation en cours de partie.

**E. Aider un allié attaqué** ✅ Validé par Duncan, sur le modèle de l'aide de clan de Clash of Clans :
- Un allié envoie un **contingent** qui ne sert **qu'en défense**.
- Les troupes **se téléportent directement dans l'enceinte** du défenseur, sans marche.
- Elles **disparaissent à la fin de la guerre**.
- À régler avant de coder, avec ma recommandation : le contingent est prélevé sur le stock de l'allié ; les troupes mortes sont perdues ; la taille est plafonnée (garnison de l'allié) ; une seule aide par allié et par guerre.

## Découpe en PR (une PR = un sujet)

1. **Cohérence** : les routes restent à la rupture, raids de pillards coupés, vérification de la garnison.
2. **Route de guerre automatique** et marche sur route.
3. **Fin de guerre** : capitulation, chute de l'hôtel de ville, bouclier, butin pris au perdant.
4. **Influence branchée** : victoire de guerre, arène, diplomatie (le commerce, lui, rapporte de l'or).
5. **Or de la citadelle** : vente au marché impérial, route commerciale automatique vers la citadelle. *(Fait, voir « Or de la citadelle » ci-dessous.)*
6. **Aide de clan** : contingent allié téléporté en défense.
7. **Test de bout en bout** : un scénario multi-colonies qui joue toute la boucle (alliance, commerce, rupture, guerre, aide, victoire, reconstruction).

La diplomatie et la fin de guerre touchent à une architecture durable : j'attends ta validation avant de les coder.

## Sources d'Influence (PR 4, 26/11)

L'Influence est par joueur et va au **propriétaire de la colonie**, comme la victoire. Le commerce n'en donne jamais, et l'or ne se convertit pas en Influence. Montants réglables dans la config :

| Source | Défaut | Règle |
|---|---|---|
| Victoire de guerre, reddition (déjà là) | 50 | `warVictoryInfluence` |
| Assaut repoussé | 12 | défenseur, à chaque assaut qui finit par la mort de toutes les troupes ou le chrono à zéro (`influenceRepelled`) ; rappel, `stopAll` et arrêt du serveur ne comptent pas |
| Aide de clan utile | 12 | chaque allié dont le contingent était chez le défenseur à la fin d'un assaut repoussé, une fois par allié et par assaut (`influenceAllyAid`) |
| Alliance signée | 10 | les deux colonies, une seule fois par paire et par partie, même après rupture et nouvelle alliance ; mémorisé dans `Alliances` (`influenceAllianceSigned`) |
| Alliance qui dure | 1 | par jour Minecraft complet (24000 ticks) d'alliance active, pour chaque colonie de la paire ; un seul jour payé après une longue interruption, pas de rattrapage (`influenceAllianceDaily`) |

Repères : la déclaration de guerre coûte 20. Un assaut repoussé vaut environ un quart d'une victoire ; l'alliance signée un peu moins. Le raccourci admin `/diplomatie allier` ne verse rien.

## Or de la citadelle (livré)

Le commerce rapporte de l'or, jamais d'Influence. Une colonie vend ses surplus (des caisses) à l'Empire de deux façons.

**Route impériale et caravane.** Onglet « Empire » du Relais commercial :
- « Ouvrir la route impériale » (prix `imperialRoadCost`, pris au trésor régional) trace et construit une route jusqu'à un bloc Marché impérial avec le même mécanisme que la route de guerre (`RoutePlanning` puis `RouteSite`, `RouteBuilder`). Si aucun tracé n'est possible, le trésor est remboursé. Une route ouverte est réutilisée et ne disparaît jamais.
- « Envoyer la caravane » prend jusqu'à `imperialMaxCrates` caisses de l'entrepôt. L'Empire accepte toujours, sans confirmation. À l'arrivée, les caisses vont au stock de l'Empire (`ImperialMarket`) au **prix de vente du Marché à cet instant** (chaque caisse baisse le prix de la suivante) et l'or est versé au trésor de la colonie. Le joueur voit une estimation au départ, avec l'avertissement que le prix final peut baisser.
- La caravane suit les règles des autres : mule, conducteur, visible près d'un joueur, pillable par une colonie en guerre contre le vendeur (les caisses tombent au sol, rien n'est versé). Pas d'escorte pour l'instant. Un contrat impérial est un `Contract` dont l'acheteur vaut `Contract.IMPERIAL` (-1) ; il compte dans la capacité du Relais.

**Point d'arrivée.** Tout ce qui sait où arrive la route est dans `commerce/ImperialGate` : aujourd'hui le bloc Marché impérial le plus proche (ou celui que le joueur choisit quand il y en a plusieurs), connu par `region/MarketSites` (inscrit à la pose ou au premier usage du bloc). Quand la cité aura sa propre colonie admin, c'est cette seule classe qui change. Les routes ouvertes sont gardées par `commerce/ImperialRoutes` (colonie, Marché, cellules du chemin).

**Vente à pied alourdie.** La vente directe au bloc Marché reste telle quelle, mais chaque caisse portée ralentit : rien jusqu'à `cratesSlowness1` caisses, puis un niveau de Lenteur de plus toutes les `cratesPerSlowLevel` caisses, plafonné à `cratesSlowMax` (`CrateEncumbrance.slowLevel`). Le HUD affiche, sous le portefeuille, une petite étiquette avec une caisse et leur nombre, en rouge quand la Lenteur agit. Ce réglage remplace l'ancien `cratesSlowness2`.

**Config** (réglable par les admins) : `imperialRoadCost` (400), `imperialRoadSeconds` (60), `imperialMaxCrates` (64), `cratesSlowness1` (6), `cratesPerSlowLevel` (10), `cratesSlowMax` (4).
