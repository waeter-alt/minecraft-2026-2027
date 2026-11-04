# Demandes et presse-papiers : checklist de couverture

Fenêtres d'origine : `WindowRequestDetail` et `WindowClipBoard`. Les nouvelles fenêtres sont `client/hut/RequestScreen` et `client/hut/ClipboardScreen`. `BOWindowMixin` les ouvre à la place des fenêtres d'origine.

## Détail d'une demande

| Fonction d'origine | Dans la nouvelle fenêtre |
|---|---|
| Objet demandé, qui défile entre les objets acceptés | Grand objet qui défile, plus la grille de tous les objets qui conviennent, avec leur info-bulle |
| Texte court et texte long | Titre en gras, et texte long en dessous s'il apporte quelque chose |
| Demandeur | « Pour » |
| Position (coordonnées brutes) | « Où » : le nom du bâtiment et la distance. Un clic ouvre la fiche du bâtiment |
| « Resolver: … » | « Qui s'en charge » |
| Icône de livraison avec info-bulle | Remplacée par la pastille d'état (Personne ne l'a, Pris en charge, En livraison) |
| Bouton remplir | « Donner », en vert ou en gris. Il n'apparaît que depuis la fiche d'un citoyen, ou depuis une fenêtre MineColonies qui sait remettre. La ligne « Sur toi » dit combien d'objets qui conviennent le joueur porte |
| Bouton annuler | « Annuler la demande » en rouge, seulement pour une demande faite directement (pas une sous-demande) |
| Retour | Échap revient à l'écran d'où l'on vient |

Ouverture : clic sur une demande depuis la fiche citoyen (la carte entière ; « Donner » reste un raccourci), la fiche hutte, la page Demandes du hub ou le presse-papiers.

## Presse-papiers

| Fonction d'origine | Dans la nouvelle fenêtre |
|---|---|
| Demandes confiées aux joueurs (résolveurs joueur et « retrying »), ramenées à la demande d'origine | Identique |
| Tri par distance | Identique : les plus proches en premier, avec la distance affichée |
| Bouton « masquer les demandes asynchrones » | Case « Seulement l'urgent ». Les demandes qui peuvent attendre sont marquées « peut attendre » |
| Clic sur une demande | Ouvre le détail ci-dessus |
| Pas de bouton remplir | Identique (on ne remet rien à distance), mais une pastille verte « Tu en as N » montre ce qu'on porte déjà |
