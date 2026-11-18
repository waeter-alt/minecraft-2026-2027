<!-- [claude-build] fiche du Donjon de la Station (le donjon 2), 2026-11-18 ; d'après son dossier de conception (« Donjon de la Station - dossier de conception.pdf ») ; générateur : scripts/donjon_station.py -->
# Donjon de la Station (le donjon 2)

Une station de pompage à l'abandon, sèche au départ, qu'on remet sous eau. Construite d'après son dossier de conception, **en entier** : le Hall et le Vestiaire, la Grande Cuve et sa mise en eau, le Drain, les trois secteurs (A, B, C) et leurs pompes, la Cheminée et le Poste, le Réservoir du boss et l'Exutoire. Tout est en datapack ; ce que le mod ne sait pas encore faire est remplacé (voir « Remplacements »).

Fichiers : `scripts/station_acte1.py` (Hall, Vestiaire, Sas, Cuve), `scripts/station_secteurs.py` (la suite), `scripts/station_logique.py` (les fonctions de la suite), `scripts/station_cine.py` (les cinématiques), `scripts/donjon_station.py` (assemblage, monde, `--zip`). Vérificateurs : `scripts/verif_station.py` (actes I-II, à pied et au grappin), `scripts/verif_station_suite.py` (la suite, à la nage). Tests : `tests/test_station.py`.

## Le monde

- **« Donjon de la Station »**, superflat profond : herbe à **Y 46**. Survie, difficulté normale, apparition devant le pavillon.
- **Monde prêt à copier** : `atelier/mondes/Donjon de la Station.zip`, à extraire dans `mod/run/client/saves/`. La pose se fait toute seule à la première ouverture (30 s de chargement puis la pose par morceaux, une à deux minutes ; ne pas mettre en pause), avec un `/tp` cliquable vers l'entrée.
- Gabarit 240 × 112 × 240 posé en **-120 -62 -238** (monde = local + ce décalage). Entrée du pavillon : **(-1, 47, 0)**. Huit structures : `station_cuve`, `station_hall`, `station_drain`, `station_a`, `station_b`, `station_c`, `station_cheminee`, `station_reservoir`.
- Remise à zéro : `/function atelier:station_reset` (tout se referme, la Cuve se vide, les monstres et le boss disparaissent). Tout reposer : `/scoreboard players set #station atelier 0` puis `/reload`.

## Le parcours

1. **Le pavillon** et **l'escalier de 24 marches** descendent au Hall (sol à Y local 84).
2. **Le Hall des Bassins** : trois volants (leviers), dans n'importe quel ordre, chacun allume un témoin du Sas.
   - **Volant 1, le plongeon** : fosse de 24 × 24, creux de 12, plateformes P1 à P3 ; le volant est sur P3, **au grappin** (ancrages au-dessus de chaque plateforme et quatre sur le bord), échelles aux coins.
   - **Volant 2, le bassin de 50 m** : embuscade (3 zombies et un squelette) ; le volant est grippé tant qu'ils vivent.
   - **Volant 3, la tour du toboggan** : trois corniches puis le sommet, **au grappin**.
3. **Les trois volants tournés** : cinématique, la vanne du Sas se lève, et **au Vestiaire un casier s'ouvre** avec la **Cape lestée** (le haut du casier s'ouvre aussi, sinon le coffre ne s'ouvrirait pas).
4. **Le Drain** (contournement, pour qui a déjà la cape) : au fond du bassin d'essai, un puits de 4 × 4 descend de 50 blocs (échelle contre la paroi nord), puis un conduit d'environ 80 blocs mène au pied de la Cuve. **Un courant repousse vers le bassin quiconque flotte** ; on passe en descendant l'échelle puis en marchant au fond.
5. **La Grande Cuve** : 60 de diamètre, Coursive, bouches A, B, C, D. **La mise en eau** se déclenche à la première entrée (par le Sas ou par le Drain) : cinématique de 15 s, l'eau monte au niveau **Haut (76)**.
6. **Les écluses** des secteurs A, B, C (sas de 9 entre la bouche de la Cuve et une vanne intérieure) : nager devant la bouche ouvre la vanne extérieure (le sas prend le niveau de la Cuve) ; avancer jusqu'au fond du sas la referme et ouvre la vanne intérieure (le sas se remplit). Jamais les deux ensemble : l'eau d'un secteur ne se vide pas dans la Cuve.
7. **Secteur A, la Filtration (nager vite)**, à l'ouest, bouche à 64-70 : la **galerie des vannes** (50 blocs, 4 vannes qui s'ouvrent quand on entre dans la galerie et se referment 5,0 s plus tard ; un relais entre chaque vanne les rouvre), la **salle des lits** (55 × 40, trois murets de sable et de gravier ; 4 gardiens et 2 noyés), la **salle de la Pompe A** (2 gardiens ; nager jusqu'au volant, à l'est de la pompe), la **chambre cachée** au sud de la salle des lits (une fente dans le mur : ses deux vannes s'ouvrent 2,5 s ; un coffre).
8. **Secteur B, la Chaufferie (se lester)**, à l'est, bouche à 32-38 : le **collecteur** (28 blocs en pente, des marches, un torrent vers la Cuve qui repousse qui flotte), la **salle des chaudières** (40 × 52, noyée, 4 chaudières, 4 noyés) et ses **trois plaques lestées** (dalles de cuivre 3 × 3 ; il faut s'y tenir debout) : la plaque 1 ouvre la cage de la plaque 2, la 2 celle de la 3, la 3 ouvre une **colonne de bulles** (sable des âmes) vers la **halle haute** (sèche, bassin de trempe, passerelles coupées au-dessus de trous de 5-6 et **quatre ancrages**, jets de vapeur toutes les 4 s qui blessent, 3 blazes). La **Pompe B** est sur le balcon est (levier), au grappin ; un **coffre sur l'îlot**, au grappin. Après la pompe, une échelle redescend vers les chaudières.
9. **Secteur C, les Turbines (tourbillon)**, au nord, bouche à 48-54 : un puits de 36 de diamètre, noyé jusqu'à 82, coupé par **deux grilles de cuivre** dont le trou est bouché de **tartre**. Trois **turbines** sur un arbre central : **nager contre une turbine pendant 2 s la lance** ; elle amène une vague (3 noyés et 1 gardien) ; la première pose le sable des âmes d'une **colonne de bulles** qui monte par les trous. **Nager contre un tartre** le brise, si la turbine de son étage tourne (le second exige aussi que le **Gardien du rotor**, un gardien ancien qui apparaît à l'étage 2, soit mort). La troisième turbine lève la grille de la **salle de la Pompe C** (sèche, à 86 ; échelle depuis la surface ; levier, coffre).
10. **Les pompes** : chacune allume un témoin sur la Coursive. **La première fait baisser la Cuve au niveau Médian (58), la deuxième au niveau Bas (42)**, chaque fois en cinématique (8,5 s, la caméra suit la surface) ; **la troisième lève la vanne D** (cinématique). Les écluses se referment à chaque changement de niveau.
11. **La Cheminée**, par la bouche D (au fond, nord-est) : une galerie de 24 blocs à **3 vannes en 3,0 s**, un puits de 14 avec une **rampe en lacets** (des demi-dalles : on la monte sans sauter) et un **courant descendant** qui ramène au fond qui flotte, un **tartre** qui ferme le haut du puits, une **turbine** qui lance une colonne de bulles jusqu'au **bassin du haut** (76), puis un **ancrage au-dessus du rebord** (à 84) et **trois ancrages au-dessus de trous de 6** jusqu'au **Poste** (tomber, c'est retomber dans le bassin).
12. **Le Poste** : cabine vitrée, cadrans, **levier général**. Il ouvre la porte du Poste sur la Coursive (vers un **plongeoir** face au Poste) et **la bonde** de la Cuve : un tourbillon (colonne de bulles descendante, bloc de magma au fond du Réservoir) emporte qui plonge.
13. **Le Réservoir** : 88 de diamètre, 24 de haut, noyé, onze piliers en ruine, quatre bondes de fond, un coffre. Le **Léviathan** (`grappin:leviathan`) surgit à l'arrivée. À sa mort, **l'Exutoire** s'ouvre au sud : un conduit puis un puits d'environ 100 blocs, avec une colonne de bulles, jusqu'à un **lac sur la plaine** (victoire).

## Remplacements (en attendant les ajouts au mod du dossier, § 3)

| Dans le dossier | Ici, en datapack |
|---|---|
| Zones de courant | Un joueur qui **flotte** (de l'eau sous les pieds) est poussé (0,8 bloc par tick dans le Drain, 0,5 dans le collecteur, 0,3 vers le bas dans la rampe) ; qui **marche au fond** ou tient une échelle ne l'est pas. Ça tient lieu d'état « lesté ». |
| Signal du tourbillon | **Nager** (la nage rapide, drapeau `is_swimming`) à moins de 4 blocs d'une turbine ou d'un tartre. Pour une turbine : 2 s cumulées (la charge redescend si on s'éloigne). |
| Plaques lestées | Se tenir **debout** sur la dalle de cuivre (pas en flottant au-dessus). |
| Boutons (écluses, vannes) | Des **zones** : un bouton ne tient pas sous l'eau. |
| Bond hors de l'eau (≈ 8) | Un **ancrage au-dessus du rebord** de la Cheminée (grappin depuis l'eau : à essayer en jeu, c'est une question ouverte du dossier). |
| Engrenages et turbines qui tournent | Des **particules** et des sons (pas encore d'entités d'affichage). |
| Volants | Des **leviers** (ou une zone pour la Pompe A, sous l'eau). |

## Ce qui est vérifié (sans le jeu)

- `verif_station.py` : escalier de 24 marches ; à pied, on n'atteint ni le volant 1, ni le volant 3, ni la Cuve ; au grappin 3 on atteint les volants ; vanne levée, la Coursive ; lumière 5 partout où l'on marche.
- `verif_station_suite.py` : **à la nage**, pour chaque porte, fermée elle bloque et ouverte elle laisse passer : le Drain mène au pied de la Cuve ; écluses fermées, aucun secteur n'est accessible ; galerie A, chambre cachée, jets 2 et 3, colonne B, tartres 1 et 2, grille de la salle C, vanne D, vannes de la Cheminée, tartre de la Cheminée, porte du Poste, bonde, Exutoire. **Lumière 5 dans les 307 499 cases d'eau ou d'air** de la suite (lanternes et blocs de lumière invisibles, un tous les 6 blocs).
- `tests/test_station.py` (25 tests) : fonctions appelées existantes, noms de fonction valides (une majuscule fait refuser la fonction), particules du jeu, `fill` ≤ 32 768, variables initialisées et remises à zéro, la Cuve sèche avant la mise en eau, une pompe = un cran, écluses jamais ouvertes des deux côtés, courants qui ne poussent que qui flotte, échelles adossées, le boss dans une fonction à part, la bonde scellée jusqu'au levier.
- Tous les blocs vanilla et leurs propriétés existent dans Minecraft 1.21.1.

## Écarts et limites (à valider)

- **Pas encore essayé en jeu.** Toutes les distances et durées sont à régler (« À mesurer » du dossier).
- **La Cape lestée** : elle n'existe que dans le jar **grappin 3.5.0**, sur la branche locale `feature/grappin-cape-lestee` (session à part), pas encore dans `mod/env/mods.txt` de cette branche. Sans ce jar, le casier s'ouvre vide.
- **Le Léviathan** : s'il manque dans le jar du grappin, seule la fonction d'apparition est refusée et l'Exutoire s'ouvre tout de suite (avec un message). Il garde son comportement de la PR #22 (phase 2 « vidange » pas faite).
- La **mise en eau** se fait dès l'entrée dans la Cuve (dossier : après le Sas ou par le Drain, conforme). **Trois pompes pour deux crans** : la troisième ne baisse pas l'eau, elle lève la vanne D (le dossier hésite entre les deux).
- Les **chronos des vannes** sont communs à tous les joueurs (le dossier les veut par joueur). Rester dans la zone de départ garde les vannes ouvertes.
- Pas de **casier de secours** ni de message « le casier est vide » (il faudrait lire l'emplacement Accessories).
- Panneaux de profondeur, engrenages en entités d'affichage, sons de bourdonnement montant : pas faits.
- Les gabarits sont gros (733 000 blocs) : la pose peut ralentir le jeu une à deux minutes.
