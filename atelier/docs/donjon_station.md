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
2. **Le Hall des Bassins**, décoré en **piscine** (carrelage blanc, frise bleue, bassins en carreaux bleus, margelles de quartz, chaise de maître-nageur, parasols et transats, serviettes, gradins le long du mur nord, palmiers en bacs, plongeoirs au bord du grand bain, buvette, douches, bouées). Un seul grand bassin, **vide** comme dans le dossier : le **bassin de 50 m** (lignes de nage noires au fond, échelles) débouche à l'ouest dans le **grand bain**, la fosse du plongeoir (creux de 12). Trois volants (leviers), dans n'importe quel ordre, chacun allume un témoin du Sas.
   - **Volant 1, le plongeoir** : la tour de plongeoirs au milieu du grand bain, plateformes P1 à P3 ; le volant est sur P3, **au grappin** (ancrages au-dessus de chaque plateforme et quatre autour), échelles aux coins.
   - **Volant 2, le bassin de 50 m** : embuscade (3 zombies et un squelette) ; le volant, au bout est du bassin, est grippé tant qu'ils vivent.
   - **Volant 3, la tour du toboggan**, au bout est du bassin : trois corniches puis le sommet, **au grappin**. Le **toboggan** est une goulotte **sèche** en spirale autour de la tour (sans eau, demandé par Luca) : depuis la passerelle du sommet on saute dedans (3 blocs plus bas, on ne la remonte donc pas) et on la descend à pied jusqu'au bassin de 50 m. Ses bords sont des murets d'1,5 bloc.
3. **Les trois volants tournés** : cinématique, la vanne du Sas se lève, et **au Vestiaire un casier s'ouvre** avec la **Cape lestée** (le haut du casier s'ouvre aussi, sinon le coffre ne s'ouvrirait pas).
4. **Le Drain** (contournement, pour qui a déjà la cape) : au fond du bassin d'essai, un puits de 4 × 4 descend de 50 blocs (échelle contre la paroi nord), puis un conduit d'environ 80 blocs mène au pied de la Cuve. **Un courant repousse vers le bassin quiconque flotte** ; on passe en descendant l'échelle puis en marchant au fond.
5. **La Grande Cuve** : 60 de diamètre, Coursive, bouches A, B, C, D. **La mise en eau** se déclenche à la première entrée (par le Sas ou par le Drain) : cinématique de 15 s, l'eau monte au niveau **Haut (76)**.
6. **Les bouches des secteurs A, B, C** sont scellées par des barreaux tant que la Cuve est sèche ; **à la fin de la mise en eau, ces barreaux (et ceux du fond de leur couloir) disparaissent pour de bon** : on entre directement (plus de sas à double vanne, demandé par Luca). Quand la Cuve baisse sous une bouche, l'eau du secteur s'en déverse en cascade. La bouche D (la Cheminée) reste fermée jusqu'à la troisième pompe.
7. **Secteur A, la Filtration (nager vite)**, à l'ouest, bouche à 64-70 : la **galerie** (50 blocs, libre : plus de vannes chronométrées, demandé par Luca), la **salle des lits** (55 × 40, trois murets de sable et de gravier ; 4 gardiens et 2 noyés), la **salle de la Pompe A** (2 gardiens ; nager jusqu'au volant, à l'est de la pompe), la **chambre cachée** au sud de la salle des lits (une fente dans le mur ; un coffre).
8. **Secteur B, la Chaufferie (se lester)**, à l'est, bouche à 32-38 : le **collecteur** (28 blocs en pente, des marches, un torrent vers la Cuve qui repousse qui flotte), la **salle des chaudières** (40 × 52, noyée, 4 chaudières, 4 noyés) et ses **trois plaques lestées** (dalles de cuivre 3 × 3 ; il faut s'y tenir debout) : la plaque 1 ouvre la cage de la plaque 2, la 2 celle de la 3, la 3 ouvre une **colonne de bulles** (sable des âmes) vers la **halle haute** (sèche, bassin de trempe, passerelles coupées au-dessus de trous de 5-6 et **quatre ancrages**, jets de vapeur toutes les 4 s qui blessent, 3 blazes). La **Pompe B** est sur le balcon est (levier), au grappin ; un **coffre sur l'îlot**, au grappin. Après la pompe, une échelle redescend vers les chaudières.
9. **Secteur C, les Turbines (tourbillon)**, au nord, bouche à 48-54 : un puits de 36 de diamètre, noyé jusqu'à 82, coupé par **deux grilles de cuivre** dont le trou est bouché de **tartre**. Trois **turbines** sur un arbre central : **nager contre une turbine pendant 2 s la lance** ; elle amène une vague (3 noyés et 1 gardien) ; la première pose le sable des âmes d'une **colonne de bulles** qui monte par les trous. **Nager contre un tartre** le brise, si la turbine de son étage tourne (le second exige aussi que le **Gardien du rotor**, un gardien ancien qui apparaît à l'étage 2, soit mort). La troisième turbine lève la grille de la **salle de la Pompe C** (sèche, à 86 ; échelle depuis la surface ; levier, coffre).
10. **Les pompes** : chacune allume un témoin sur la Coursive. **La première fait baisser la Cuve au niveau Médian (58), la deuxième au niveau Bas (42)**, chaque fois en cinématique (8,5 s, la caméra suit la surface) ; **la troisième lève la vanne D** (cinématique).
11. **La Cheminée**, par la bouche D (au fond, nord-est) : une galerie de 24 blocs (libre), un puits de 14 avec une **rampe en lacets** (des demi-dalles : on la monte sans sauter) et un **courant descendant** qui ramène au fond qui flotte, un **tartre** qui ferme le haut du puits, une **turbine** qui lance une colonne de bulles jusqu'au **bassin du haut** (76), puis un **ancrage au-dessus du rebord** (à 84) et **trois ancrages au-dessus de trous de 6** jusqu'au **Poste** (tomber, c'est retomber dans le bassin).
12. **Le Poste** : cabine vitrée, cadrans, **levier général**. Il ouvre la porte du Poste sur la Coursive (vers un **plongeoir** face au Poste) et **la bonde** de la Cuve : un tourbillon (colonne de bulles descendante, bloc de magma au fond du Réservoir) emporte qui plonge.
13. **Le Réservoir** : 88 de diamètre, 24 de haut, noyé, onze piliers en ruine, quatre bondes de fond, un coffre. Le **Léviathan** (`donjon:leviathan`) surgit à l'arrivée. À sa mort, **l'Exutoire** s'ouvre au sud : un conduit puis un puits d'environ 100 blocs, avec une colonne de bulles, jusqu'à un **lac sur la plaine** (victoire).

## Create et guidage (refonte du 18/11)

- **Create 6.0.9** (déjà dans le pack) : les **volants** sont de vrais volants Create (`valve_handle`) qu'on tourne au clic droit maintenu : rouge (volant 1), jaune (2), bleu clair (3) dans le Hall ; vert (pompe A, sous l'eau), orange (B), cyan (C). Le datapack lit le compteur `InUse` de leur entité de bloc et cumule 1,5 s de rotation. Le levier général du Poste reste un grand levier.
- **Ce qui tourne** : arbres, engrenages et grandes roues sous le plafond du Hall (murs nord et sud), dans la halle haute B et la salle des lits A, avec des moteurs créatifs ; trois **stations de pompage** (moteur, arbre, pompe mécanique, tuyau) sur la Coursive au-dessus des bouches A, B, C ; les six colonnes de la Cuve sont de vrais **tuyaux Create** cerclés de cuivre.
- **Turbines** : l'arbre central du secteur C est en arbres Create coupés aux grilles (un tronçon par étage), chaque turbine est faite de deux grandes roues dentées ; **quand on la lance, le datapack pose son moteur Create** et elle se met à tourner. Même chose pour la turbine de la Cheminée. Ventilateurs Create sous les passerelles à vapeur de la halle B.
- **Balcons** sous les bouches A et C, dans la Cuve (dossier) : on y prend pied quand l'eau est plus bas.
- **Guidage** : une barre en haut de l'écran « Station : pompes n/3 » et, toutes les 2 s, **l'objectif du moment** en barre d'action (volants, cape et Sas, bouches A B C, Cheminée, tourbillon, Léviathan, Exutoire). **Panneaux** : entrée du Hall, Vestiaire, Sas, chacun des trois volants, bouches A, B, C (sur la Coursive), repères HAUT, MÉDIAN, BAS dans la Cuve.
- **Casiers** : un bloc `donjon:casier` (Blockbench) est en cours dans le grappin 3.8.0 ; le Vestiaire l'utilisera dès qu'il existe.

## Retouches à la main (Luca)

Luca peut retoucher le donjon à la main, en jeu. `scripts/capture_retouches_station.py` lit le monde (client fermé), le compare à **ce qui a été posé dans ce monde** (son propre datapack) et garde les différences dans `atelier/donjon/station_retouches.json` ; le générateur les repose en dernier (structure `station_retouches`), par-dessus tout. Exclus : les cases que le datapack change en jouant, l'eau qui coule et les colonnes de bulles. Protégés (jamais gardés s'ils disparaissent) : volants, leviers, ancrages, casier. Non capturés : contenu des coffres, texte des panneaux. À lancer **avant** toute mise à jour du monde.

## Remplacements (en attendant les ajouts au mod du dossier, § 3)

| Dans le dossier | Ici, en datapack |
|---|---|
| Zones de courant | Un joueur qui **flotte** (de l'eau sous les pieds) est poussé (0,8 bloc par tick dans le Drain, 0,5 dans le collecteur, 0,3 vers le bas dans la rampe) ; qui **marche au fond** ou tient une échelle ne l'est pas. Ça tient lieu d'état « lesté ». |
| Signal du tourbillon | **Nager** (la nage rapide, drapeau `is_swimming`) à moins de 4 blocs d'une turbine ou d'un tartre. Pour une turbine : 2 s cumulées (la charge redescend si on s'éloigne). |
| Plaques lestées | Se tenir **debout** sur la dalle de cuivre (pas en flottant au-dessus). |
| Boutons (vannes) | Des **zones** : un bouton ne tient pas sous l'eau. |
| Bond hors de l'eau (≈ 8) | Un **ancrage au-dessus du rebord** de la Cheminée (grappin depuis l'eau : à essayer en jeu, c'est une question ouverte du dossier). |
| Engrenages et turbines qui tournent | Des **particules** et des sons (pas encore d'entités d'affichage). |
| Volants | Des **leviers** (ou une zone pour la Pompe A, sous l'eau). |

## Ce qui est vérifié (sans le jeu)

- `verif_station.py` : escalier de 24 marches ; à pied, on n'atteint ni le volant 1, ni le volant 3, ni la Cuve ; au grappin 3 on atteint les volants ; vanne levée, la Coursive ; lumière 5 partout où l'on marche.
- `verif_station_suite.py` : **à la nage**, pour chaque porte, fermée elle bloque et ouverte elle laisse passer : le Drain mène au pied de la Cuve ; bouches scellées, aucun secteur n'est accessible ; bouches A, B, C, jets 2 et 3, colonne B, tartres 1 et 2, grille de la salle C, vanne D, tartre de la Cheminée, porte du Poste, bonde, Exutoire. **Lumière 5 dans les 307 499 cases d'eau ou d'air** de la suite (lanternes et blocs de lumière invisibles, un tous les 6 blocs).
- `tests/test_station.py` (26 tests) : fonctions appelées existantes, noms de fonction valides (une majuscule fait refuser la fonction), particules du jeu, `fill` ≤ 32 768, variables initialisées et remises à zéro, la Cuve sèche avant la mise en eau, une pompe = un cran, bouches ouvertes pour de bon à la mise en eau, toboggan du sommet au bassin, courants qui ne poussent que qui flotte, échelles adossées, le boss dans une fonction à part, la bonde scellée jusqu'au levier.
- Tous les blocs vanilla et leurs propriétés existent dans Minecraft 1.21.1.

## Écarts et limites (à valider)

- **Pas encore essayé en jeu.** Toutes les distances et durées sont à régler (« À mesurer » du dossier).
- **La Cape lestée** : elle n'existe que dans le jar **grappin 3.5.0**, sur la branche locale `feature/grappin-cape-lestee` (session à part), pas encore dans `mod/env/mods.txt` de cette branche. Sans ce jar, le casier s'ouvre vide.
- **Le Léviathan** : s'il manque dans le jar du grappin, seule la fonction d'apparition est refusée et l'Exutoire s'ouvre tout de suite (avec un message). Il garde son comportement de la PR #22 (phase 2 « vidange » pas faite).
- La **mise en eau** se fait dès l'entrée dans la Cuve (dossier : après le Sas ou par le Drain, conforme). **Trois pompes pour deux crans** : la troisième ne baisse pas l'eau, elle lève la vanne D (le dossier hésite entre les deux).
- Pas de **casier de secours** ni de message « le casier est vide » (il faudrait lire l'emplacement Accessories).
- Panneaux de profondeur, engrenages en entités d'affichage, sons de bourdonnement montant : pas faits.
- Les gabarits sont gros (733 000 blocs) : la pose peut ralentir le jeu une à deux minutes.
