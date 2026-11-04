# Embauche et logement : checklist de couverture

Fenêtres d'origine : `WindowHireWorker` et `WindowAssignCitizen`. Les nouvelles fenêtres sont `client/hut/HireScreen` et `client/hut/HousingScreen`, ouvertes par `BOWindowMixin` à leur place. « Gérer » dans la fiche hutte ouvre directement l'embauche.

## Embauche

| Fonction d'origine | Dans la nouvelle fenêtre |
|---|---|
| Un bouton par métier du bâtiment | Un onglet par métier, avec « pris / max », seulement s'il y en a plusieurs |
| Liste mêlée (en poste et candidats) | Deux sections : « En poste », puis « Candidats » |
| Compétences toutes listées, la principale en vert, la secondaire en or | Seules les deux compétences utiles : la principale (★) en pastille verte, la secondaire à côté. Les candidats sont triés : sans emploi d'abord, puis les meilleurs (la principale compte double) |
| Distance au logement | « habite à N blocs », « habite ici », « sans logement » |
| Embaucher | Vert ; gris avec la raison (complet, poste impossible). Un citoyen déjà employé quitte son ancien poste, comme dans l'original |
| Renvoyer | Rouge ; gris pendant un voyage |
| Pause / reprendre, relancer | Identiques ; « Relancer » n'apparaît que pendant une pause, avec son effet expliqué |
| « Montrer les employés » (oui/non) | Case « Montrer ceux qui ont un emploi » |
| Mode d'embauche | « Embauche : selon la colonie / automatique / manuelle ⟳ », expliqué au survol (verrouillé impossible pour un lieu de travail, comme l'original) |

## Logement

| Fonction d'origine | Dans la nouvelle fenêtre |
|---|---|
| Deux listes : habitants et sans-logis | « Habitent ici », puis « Pourraient emménager » (sans logement d'abord, puis ceux qui seraient plus près de leur travail) |
| Distances en rouge au-delà de 300 blocs | « travail à N blocs, trop loin » en rouge |
| Distance plus courte en vert | Pastille « Plus près : avant → après » |
| Loger / faire partir, seulement en mode manuel | Identique ; en automatique les boutons sont gris et le survol dit de passer en manuelle |
| Mode (y compris verrouillé) | « Attribution : … ⟳ », expliqué au survol |
