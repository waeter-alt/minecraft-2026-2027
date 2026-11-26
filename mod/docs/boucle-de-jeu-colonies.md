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
4. **Influence non branchée.** Elle ne vient que de la présence, de la dîme et des édits. Ni la guerre ni le commerce n'en donnent, alors que c'est elle qui décide du couronnement.
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
5. **Or de la citadelle** : vente au marché impérial, route commerciale automatique vers la citadelle.
6. **Aide de clan** : contingent allié téléporté en défense.
7. **Test de bout en bout** : un scénario multi-colonies qui joue toute la boucle (alliance, commerce, rupture, guerre, aide, victoire, reconstruction).

La diplomatie et la fin de guerre touchent à une architecture durable : j'attends ta validation avant de les coder.
