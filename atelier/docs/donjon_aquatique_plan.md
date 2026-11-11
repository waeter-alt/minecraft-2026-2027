<!-- [claude-build] plan du donjon aquatique « Le Sanctuaire des Marées », validé par Luca le 2026-11-10 ; la fiche de ce qui est construit est donjon_aquatique.md -->
# Donjon aquatique — « Le Sanctuaire des Marées » (plan validé)

Un monument océanique englouti, dans le style de ceux de Minecraft, **noyé jusqu'au plafond** (révision du 2026-11-11 : Luca trouvait trop de passages à pied et une déco qui ne ressemblait pas à un temple sous-marin). L'objet du donjon est le **Voile des Profondeurs** (mod Grappin 2.5.0), remis à l'entrée. Règle posée par Luca : aucun autre objet ni effet ne doit permettre de respirer sous l'eau.

## Cadre

- Monde « Donjon 2 » (dossier `Donjon 2 (1)`), superflat, sol à Y-61, dans le client de test du dépôt. NeoForge 21.1.216.
- Gabarit 150 × 70 × 170, posé en 150 -61 260. Le Temple des Mille Racines est dans un autre monde (« Donjon 1 »).
- Palette : briques de prismarine, prismarine, prismarine sombre en bandeaux et en damier au sol, lanternes de mer encastrées, éponges mouillées dans les salles cachées, noyau d'or dans la salle du boss. Un petit herbier dans la Colonnade seulement.
- Interdits : ancrage de grappin, conduit, lave, coffre, conteneur, générateur, monstre, boss, colonne de bulles. Toute l'eau est en sources confinées.
- Tout est éclairé, eau comprise.

## Le principe du voile

- Toutes les salles sont noyées jusqu'au plafond. L'air n'existe plus que dans le Débarcadère (départ), la salle du boss, les salles cachées d'air et des **cloches d'air** flottantes (23) : le voile ne se recharge qu'à l'air libre, sans elles le donjon serait infaisable. Luca avait demandé « sans poche d'air » ; les cloches sont une entorse nécessaire, signalée dans la fiche.
- Les interrupteurs sont des **vannes** : des trappes en bois waterloggées, lues par `open=true`. Un levier ou un bouton est détruit par l'eau qui coule dans sa case.
- Trois tunnels entièrement noyés relient les salles. La longueur seule ne suffit pas à exiger le voile (avec lui on tient environ 55 s, sans lui 15 s : un tunnel de 20 s passerait dans les deux cas en nage rapide). D'où la **pression des profondeurs** : dans un tunnel, qui ne porte pas le voile prend des dégâts de noyade toutes les demi-secondes.
- Chaque trajet complet (de la cloche d'entrée à celle de sortie) dure 20 à 28 s sur 40 s de réserve : de quoi passer, pas de quoi traîner.

## Sections

| # | Section | Mécanisme | Temps estimé |
|---|---|---|---|
| 1 | Le Débarcadère | Don du voile ; vanne au fond d'un petit bassin, herse d'une galerie noyée | 2 min |
| 2 | La Colonnade | Bouton au sommet d'une colonne au milieu du bassin ; panneau de l'ordre des glyphes | 4 min |
| 3 | Les Citernes | Deux vannes sur des îlots ; grille noyée | 4 min |
| 4 | Tunnel T1 | Voile obligatoire | 1 min |
| 5 | La Bibliothèque noyée | Quatre glyphes dans l'ordre | 4 min |
| 6 | Les Écluses | Vanne du socle du bassin B : Clé du Récif et grille du bassin A | 4 min |
| 7 | Tunnel T2 | Voile obligatoire | 1 min |
| 8 | La salle des Pompes | Trois vannes | 3 min |
| 9 | Tunnel T3 | Voile obligatoire, descente profonde | 1 min |
| 10 | L'Antichambre | Porte à clé | 1 min |
| 11 | Le Cœur du Temple | Salle du boss : îlot, bassin profond, sortie scellée | 2 min hors combat |

Total ≈ 27 min, ≈ 31 min avec quelques salles cachées.

## Salles cachées

Huit salles de 9 × 9 × 5 : une à sec derrière un faux mur (bouton de pierre), trois noyées avec une rangée d'air sous le plafond (vanne discrète), quatre salles d'air au bout d'un tunnel noyé (voile obligatoire). Chacune garde un emplacement libre pour un coffre.

## Phases de construction

1. Volumes, bassins, tunnels, coquille. 2. Portes, énigmes, écluses, clé, salle du boss. 3. Blocs de commande (voile, pression, portes). 4. Décor de monument. 5. Éclairage et cloches d'air. 6. Contrôles du générateur (eau, interdits, appuis). 7. Vérificateur d'accès et de budget d'air. 8. Fiche, puis test en jeu.
