# Aide de clan

Une colonie paie, depuis son hôtel de ville, un **contingent de soldats réservé pour un allié**. Ses propres troupes ne bougent pas. Quand l'allié subit un assaut, le contingent apparaît directement dans sa colonie et défend avec ses gardes. C'est le principe des renforts de clan de Clash of Clans.

## Ce qu'on voit

**L'aidant** (onglet Diplomatie, touche K), sur la ligne de chaque allié :
- `Contingent : n/max`, puis le bouton **Remplir**, avec le prix dedans : vert s'il est payable ; gris avec `avoir/prix` en rouge si le trésor ne suffit pas.
- Le prix = soldats manquants × prix d'un soldat. Il est payé par le trésor de la colonie (le même que pour le tribut ou la route de guerre).
- Pas de bouton quand le contingent est complet, ni pendant qu'il est engagé. La ligne dit alors `Contingent engagé : n debout`.

**L'aidé**, sur la même ligne : `Renforts réservés : n` (le contingent que cet allié garde pour lui), ou `Leurs renforts ici : n` pendant qu'il est engagé.

## Déroulé

1. **Apparition** au premier assaut lancé contre l'aidé (un vrai assaut, pas l'assaut de test qu'une colonie lance contre elle-même).
   - Chaque allié qui a une réserve envoie tout son contingent ; il peut y avoir plusieurs aidants.
   - Les soldats apparaissent au pied des tours de l'aidé, comme gardes **invités**, au-delà de la capacité des tours.
   - Messages : « Y envoie n soldat(s) en renfort » chez l'aidé, « Votre contingent est engagé chez X » chez l'aidant.
   - La réserve est « grillée » : elle passe à 0 (moins les soldats qui n'ont pas pu apparaître).
2. Le contingent **reste jusqu'à la fin de la guerre**, quels que soient les assauts suivants.
3. **Fin de guerre** (paix, capitulation, conquête, ou commande admin) : dès que plus personne ne fait la guerre à l'aidé, les invités disparaissent.
   - Les survivants deviennent la nouvelle réserve. Message à l'aidant : « Retour de X : n/m soldat(s) rentrent dans la réserve ».
   - L'aidant peut alors payer pour la remplir à nouveau.
4. **Bilan** du défenseur : une tuile « Renforts alliés », `reçus · n tombés` (une dixième tuile passait sous « Fermer » à l'échelle 4).

Quand la ligne est étroite (échelle 4), le texte se raccourcit (`4/10 · Renforts : 5`) ; le survol donne la phrase complète.

Les invités ont les mêmes exemptions que les gardes achetés : pas de maladie, de sommeil, de deuil ni de tombe. Ils ne comptent ni dans les « Habitants tombés » du défenseur, ni dans ses troupes disponibles pour un assaut. Ils ne prennent aucune place : le défenseur peut encore acheter ses propres gardes.

## Règles provisoires (non précisées par Duncan, à valider)

- Moitié légionnaires, moitié archères ; le soldat en plus va aux légionnaires.
- Config serveur (`serverconfig/interregne-server.toml` du monde, section `death`, à côté des autres réglages de guerre), marquée « provisional » :
  - `allyAidMaxTroops` = 10 : taille maximale d'un contingent ;
  - `allyAidTroopPrice` = 150 couronnes par soldat remis dans la réserve ;
  - `allyAidTroopLevel` = 2 : niveau de troupe des soldats. Ils le gardent, quelles que soient l'académie et l'archerie de l'aidé.
- Rupture d'alliance (bouton Rompre, déclaration de guerre entre les deux, `/diplomatie rompre`) : les deux réserves sont perdues. Un contingent déjà engagé reste jusqu'à la fin de la guerre, mais ses survivants ne reviennent pas dans la réserve.
- Défenseur sans tour de garde construite pour un type de soldat : ces soldats n'apparaissent pas et restent dans la réserve.
- Les invités restent à l'arrêt du serveur : au redémarrage, MineColonies les réaffecte à leur tour (le mixin de capacité s'applique aussi au chargement).
- Les chiffres « reçus / tombés » sont comptés par colonie aidée, pour toute la période où elle est en guerre.

## Code

- `politics/AllyAid` : les règles (sur des ids de colonie, testables sans colonie) et le branchement serveur (apparition, retour, morts).
- `garrison/Garrison.enlistGuest` / `dismiss` : création et retrait d'un invité ; `available` et `depart` ignorent les invités.
- Mixins :
  - `WorkerBuildingModuleMixin` : `assignCitizen` saute le test `isFull()` pour un invité ;
  - `GuardBuildingModuleMixin` : `isFull()` ne compte pas les invités.
- Branchements :
  - `WarAssaults.launch` appelle `AllyAid.onAssault` ;
  - `WarAssaults.onDeath` exclut les invités des « tombés » ;
  - `Diplomacy` : rupture, bilan ;
  - `Wars.defending` : la fin de guerre est détectée par `AllyAid`, toutes les secondes.
- Réseau : action `REFILL_AID` (10), champs `aidOut/aidIn/engagedOut/engagedIn` par entrée, et `aidPrice/aidMax` dans la synchro Diplomatie ; `aidReceived/aidFallen` dans le bilan.
- Tests :
  - `dev/gametest/AllyAidTests` (`./gradlew runGameTestServer`) : or débité, plafond, refus, plusieurs aidants, défenseur sans tour, survivants → réserve, rupture ;
  - UiShot : `hub-diplomacy-aid`, `hub-diplomacy-aid-short`, `war-recap-defense-aid`, `war-recap-defense-aid-win`.
