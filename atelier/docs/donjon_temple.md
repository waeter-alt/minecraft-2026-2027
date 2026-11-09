<!-- [claude-build] fiche du donjon n°2, ecrite par scripts/fiche_donjon_temple.py -->
# Donjon n°2 « Le Temple des Mille Racines »

Monde « Donjon 2 » (superflat, sol à Y-61), Minecraft 1.21.1 + NeoForge + mod **Grappin 2.4.0** (obligatoire chez chaque joueur). Structure `temple` 130 × 100 × 216, posée en 150 -61 -65 (x 150 à 279, z -65 à 150). Générateur : `scripts/donjon_temple.py`, contrôle : `scripts/verif_donjon_temple.py`, plan de conception : `donjon_temple_plan.md`.

Style jungle, comme la Gorge suspendue : briques de pierre moussues et fissurées, roche en plaques, mousse, lianes à baies lumineuses, racines et troncs de jungle. On **descend** un temple enseveli : Parvis à ciel ouvert tout en haut, trois ailes et un sanctuaire à l'étage 1, puis les salles du bas jusqu'au Gardien.

## Poser et remettre à zéro

- Pose (sans mettre en pause ; on peut la relancer pour poser une nouvelle version : elle efface d'abord toute la zone au-dessus du sol) : `/reload` puis `/function atelier:temple_pose`. Les commandes du temple utilisent des blocs de commande : en solo c'est actif ; sur un serveur, `enable-command-block=true` dans `server.properties`.
- Entrée : `/tp @s 215 22 -3 0 0` (le Parvis, au sommet). Le grappin est donné automatiquement à qui arrive au Parvis sans en avoir.
- Remise à zéro entre deux parties : `/function atelier:temple_reset` (portes fermées, leviers baissés, énigmes et clé remises, pièges et balises réarmés).

## Le grappin (Grappin 2.4.0)

- **Clic droit** (rien de lancé) : lance le crochet (30 blocs). Il ne tient que sur les **points d'ancrage** (bloc métallique faiblement lumineux) ; il en attrape un s'il passe à moins de 1,5 bloc.
- Accroché, **clic droit maintenu** : traction (la corde se rembobine, on est tiré vers l'ancrage). **Clic relâché** : on reste accroché, la corde garde sa longueur, on se balance.
- **ESPACE** (à l'appui) : lâche le crochet, avec un petit saut et la vitesse conservée. S'accroupir lâche aussi. **Molette** : allonge ou raccourcit la corde.
- Pas de dégâts de chute tant qu'on est accroché ; ils reviennent dès le lâcher. Le crochet ne traverse pas les blocs et ne blesse personne.
- Réglages : `config/grappin-common.toml` (portée, aimant, gravité du balancier, élan des touches, vitesse et saut au lâcher, raideur, corde minimale, vitesse du treuil, pas de molette).
- Technique : le balancier est calculé chez le joueur à chaque tick ; ESPACE passe par un petit message client → serveur. Limite connue : maintenir le clic droit pendant un lâcher relance le crochet dans les 4 ticks ; pas testé sur serveur dédié.

## Le parcours (≈ 31 min pour qui découvre)

| # | Section | Ce qu'on y fait | Temps |
|---|---|---|---|
| 1 | Le Parvis | Cour à ciel ouvert au sommet, ruines et stèles ; escalier vers le vestibule. | 2 min |
| 2 | Le Vestibule des Fresques | Hub de l'étage 1 : trois arches vers les ailes, trois socles à bouton sous trois fresques (énigme du sanctuaire), trois balises qui s'allument. | 3 min |
| 3 | L'Aile des Serpents (est) | Six dalles à presser dans l'ordre du serpent peint (couleurs). Une erreur : retour au seuil de l'aile. | 3 min |
| 4 | L'Aile des Miroirs d'Eau (ouest) | Bassin à traverser en 7 sauts sur des piliers moussus ; levier caché derrière des lianes. Une chute : retour sur la rive d'entrée. | 3 min |
| 5 | Le Puits des Lianes (sud) | Échelle jusqu'au fond, 3 ancrages bas puis 4 ancrages en zigzag jusqu'à la corniche de l'idole ; plaque finale. | 4 min |
| 6 | Le Sanctuaire des Trois Idoles | Dans le vestibule : presser les trois socles dans l'ordre jaguar → serpent → aigle ; chaque socle ne marche que si son aile est réussie. Ouvre la descente (porte G6). | 2 min |
| 7 | La Salle des Colonnes Brisées | Grande salle à colonnes cassées (combats plus tard, compté en partie) ; sortie sud fermée par la Clé de racine (porte G7). | 2 min |
| 8 | Le Couloir des Pièges | Passerelle au-dessus d'une fosse : 2 stalactites, 2 tronçons qui cèdent, 3 plaques empoisonnées, brèche de 9 blocs à franchir à l'ancrage P1. Chute : retour au début. | 3 min |
| 9 | Le Grand Gouffre Intérieur | Caverne : 6 ancrages en arc (A1 à A6) au-dessus de la fosse, jusqu'à la corniche d'en face. Chute : retour à la corniche d'arrivée. | 4 min |
| 10 | La Crypte des Racines | On plonge dans la fosse d'eau, on prend la Clé de racine sur l'îlot, on remonte avec 3 ancrages (K1 à K3), puis retour en arrière jusqu'à la salle 7. | 3 min |
| 11 | L'Antichambre | Porte à clé de la salle 7 (serrure en pierre magnétique), couloir de statues, long couloir droit vers l'aile du boss. | 1 min |
| 12 | La Salle du Gardien (aile du boss) | Hall de 63 × 63 au-dessus d'un gouffre de lave : 9 plateformes suspendues, atteintes au grappin ; sortie fermée. Vide, le boss viendra plus tard. | 1 min (hors combat) |
| | **Total** | sans les salles cachées ni les combats complets | **≈ 31 min** |

Estimations calculées (marche 4,3 blocs/s, sprint 5,6, balancier réussi 10 à 15 s, chute 30 à 45 s perdues, énigme 1 à 3 min) : **pas encore confirmées par une vraie partie**. Ordre imposé : 1 → 2 → (3, 4, 5 dans l'ordre voulu) → 6 → 7 → 8 → 9 → 10 → retour à 7 → 11 → 12.

## Solutions (réservé aux organisateurs)

Coordonnées du monde.

- **Aile des Serpents** : le serpent peint est sur le mur nord, de gauche à droite : red → orange → yellow → lime → cyan → purple (terracotta). Chaque dalle a un pilier de sa couleur : red 260 2 -51, orange 256 2 -43, yellow 262 2 -29, lime 252 2 -31, cyan 266 2 -53, purple 268 2 -35. Réussir allume la balise est du vestibule.
- **Aile des Miroirs d'Eau** : le levier est sur la corniche de l'extrémité ouest, derrière un rideau de lianes : 158 4 -41. Il allume la balise ouest.
- **Puits des Lianes** : la plaque de l'idole est sur la corniche du fond : 238 11 2. Elle allume la balise sud.
- **Sanctuaire** : fresques et lanternes (nombre de lanternes sous la fresque = rang) : jaguar 1 (aile des Miroirs), serpent 2 (aile des Serpents), aigle 3 (Puits). Socles (de l'ouest à l'est) : aigle 207 4 -48, jaguar 214 4 -48, serpent 221 4 -48. Ordre : **jaguar → serpent → aigle**. Une erreur remet l'ordre à zéro (son grave) ; un socle d'une aile non réussie ne fait rien. Réussite : la porte G6 (descente) s'ouvre.
- **Clé de racine** : plaque de pression sur l'îlot de la crypte : 267 -29 54 (donnée une fois par joueur). Porte G7 : se tenir devant la serrure (pierre magnétique, 194 -19 27) avec la clé en main.

## Points d'ancrage

| Nom | Où | Coordonnées |
|---|---|---|
| Hc | Puits (caché, vers la salle cachée 5) | 241 8 -5 |
| T1 | Puits (tutoriel) | 230 -8 -10 |
| T2 | Puits (tutoriel) | 235 -5 -15 |
| T3 | Puits (tutoriel) | 238 -1 -13 |
| Z1 | Puits (ascension) | 232 3 -9 |
| Z2 | Puits (ascension) | 227 7 -7 |
| Z3 | Puits (ascension) | 230 9 -1 |
| Z4 | Puits (ascension) | 236 14 -3 |
| P1 | Couloir des pièges (brèche) | 234 -12 12 |
| A1 | Gouffre | 254 -14 5 |
| A2 | Gouffre | 262 -13 1 |
| A3 | Gouffre | 269 -12 7 |
| A4 | Gouffre | 271 -11 15 |
| A5 | Gouffre | 268 -11 23 |
| A6 | Gouffre | 262 -11 28 |
| Hg | Gouffre (isolé, vers la salle cachée 7) | 254 -13 -3 |
| K1 | Crypte (remontée) | 265 -19 48 |
| K2 | Crypte (remontée) | 260 -15 45 |
| K3 | Crypte (remontée) | 256 -10 41 |
| V1 | Salle du boss (chaîne de la voûte) | 181 -13 82 |
| V2 | Salle du boss (chaîne de la voûte) | 199 -9 81 |
| V3 | Salle du boss (chaîne de la voûte) | 209 -11 81 |
| V4 | Salle du boss (chaîne de la voûte) | 183 -9 92 |
| V5 | Salle du boss (chaîne de la voûte) | 205 -9 95 |
| V6 | Salle du boss (chaîne de la voûte) | 175 -3 97 |
| V7 | Salle du boss (chaîne de la voûte) | 189 -3 100 |
| V8 | Salle du boss (chaîne de la voûte) | 197 -1 100 |
| V9 | Salle du boss (chaîne de la voûte) | 216 -1 101 |
| V10 | Salle du boss (chaîne de la voûte) | 211 3 110 |
| V11 | Salle du boss (chaîne de la voûte) | 179 1 113 |
| V12 | Salle du boss (chaîne de la voûte) | 200 11 116 |
| V13 | Salle du boss (chaîne de la voûte) | 206 1 121 |
| V14 | Salle du boss (chaîne de la voûte) | 217 3 120 |
| V15 | Salle du boss (chaîne de la voûte) | 179 11 123 |
| V16 | Salle du boss (chaîne de la voûte) | 206 11 124 |

## La salle du boss (section 12)

Hall de 63 × 63 blocs (x 166 à 228, z 69 à 131), du fond du gouffre (Y -55) à une voûte en berceau qui culmine à Y 31. On y arrive par un long couloir droit au niveau 40 (celui de l'antichambre), qui débouche sur la plateforme de départ. Le fond du gouffre est une nappe de lave **confinée** (Y 6 à 7), à 32 blocs sous le départ. Sept piliers géants contre les parois, certains cassés ; chaînes et lanternes pendues à la voûte.

| Plateforme | Emprise (monde) | Dessus (Y monde) | Cases | Position du boss (monde) |
|---|---|---|---|---|
| Départ (D) | 186..208 × 69..81 | -21 | 279 | — (départ, retour après une chute) |
| P1 | 168..177 × 89..98 | -15 | 92 | 172 -14 93 |
| P2 | 190..197 × 91..98 | -11 | 59 | 193 -10 94 |
| P3 | 212..223 × 88..99 | -13 | 94 | 217 -12 93 |
| P4 | 180..185 × 105..110 | -5 | 32 | 182 -4 107 |
| P5 | 200..209 × 107..117 | -3 | 80 | 204 -2 112 |
| P6 | 216..222 × 109..114 | 1 | 38 | 219 2 111 |
| P7 | 170..179 × 117..126 | -1 | 75 | 174 0 121 |
| P8 | 186..195 × 123..131 | 9 | 84 | 190 10 127 |
| P9 | 208..218 × 125..131 | -1 | 60 | 213 0 128 |

Toutes les plateformes sont à au moins 6 blocs les unes des autres (aucun saut, même en sprint) ; le vérificateur confirme que chacune est rejoignable au grappin depuis au moins 2 autres. Les positions du boss sont au centre de chaque plateforme, dégagées sur au moins 5 blocs de haut. Aucun mob n'est posé.

- **Chute** : une zone (x 166 à 228, y -51 à -31, z 69 à 131) couvre tout le gouffre, au-dessus de la lave et sous la plus basse plateforme. Un joueur qui y entre est téléporté sur la plateforme de départ, tourné vers la salle, avec 3 s de chute lente (pas de dégâts), son crochet supprimé (fonction `atelier:temple_decrocher`, repéré par l'identifiant du joueur) ; les objets qui tombent sont supprimés ; seuls les joueurs sont concernés.
- **Sortie** : porte de barreaux 189 10 134 à 191 13 134, dans le mur sud derrière la plateforme la plus haute (P8), puis un court couloir et une petite salle vide. `/function atelier:temple_ouvrir_sortie_boss` l'ouvre (à relier à la mort du boss) ; `/function atelier:temple_fermer_sortie_boss` la ferme ; `temple_reset` la ferme aussi.

## Salles cachées (8, vides : ni coffre, ni mob, ni spawner)

Chacune fait au moins 9 × 9 × 4 (minimum demandé 7 × 7 × 4), avec une lanterne, un emplacement dégagé pour ton coffre et une zone de combat prévue.

| # | Emprise (du monde) | Accès | Coffre prévu | Zone de mobs prévue |
|---|---|---|---|---|
| 1 | 206 11 -5 → 214 15 3 | Parvis : trappe dans une dalle moussue, échelle | 207 11 1 | 211 11 -2 |
| 2 | 194 2 -63 → 202 6 -55 | Vestibule : faux mur de briques fissurées, levier derrière des lianes | 195 2 -56 | 199 2 -60 |
| 3 | 250 2 -21 → 258 6 -13 | Aile des Serpents : étagère de bibliothèque qui s'ouvre, bouton caché dans le mur | 251 2 -14 | 255 2 -17 |
| 4 | 162 2 -21 → 170 6 -13 | Aile des Miroirs d'Eau : passage derrière un rideau de lianes, au sud | 163 2 -14 | 167 2 -17 |
| 5 | 250 2 -9 → 258 6 -1 | Puits des Lianes : tunnel dans la paroi est, atteint avec un ancrage caché | 257 2 -2 | 253 2 -5 |
| 6 | 187 -30 1 → 195 -27 9 | Salle des Colonnes : trappe sous un pilier effondré, échelle | 188 -30 8 | 192 -30 5 |
| 7 | 250 -20 -19 → 258 -16 -11 | Gouffre : tunnel dans la paroi nord, atteint avec un ancrage isolé | 251 -20 -12 | 255 -20 -15 |
| 8 | 233 -14 38 → 241 -10 46 | Crypte : faux mur de racines, plaque de pression sous la mousse | 234 -14 45 | 238 -14 42 |

Mécanismes cachés : salle 2 (levier derrière les lianes du vestibule, mur ouest), salle 3 (bouton dans l'étagère du mur sud de l'aile des Serpents), salle 8 (plaque cachée au milieu de tapis de mousse, sur la corniche d'entrée de la crypte).

## Anti-raccourcis

`scripts/verif_donjon_temple.py` simule la marche, les sauts, les échelles, la nage et le grappin (réglage réaliste : rayon de corde 14 ; généreux : 38) et vérifie, porte par porte :
- tout ce qui doit être atteignable l'est (grappin réaliste) ;
- rien de ce qui est derrière une porte fermée (G6, G7, salles cachées 2, 3 et 8) n'est atteignable, même en généreux ;
- sans grappin, on n'atteint que le Parvis, le vestibule, les ailes des Serpents et des Miroirs, l'entrée du Puits et les salles cachées 1 et 4 ;
- dans l'aile du boss, sans grappin, aucune plateforme n'est atteignable depuis le départ, et la sortie est inaccessible tant que la porte est fermée (même au grappin) ;
Le résultat actuel est **OK** à chaque étape. Ce que le vérificateur ne couvre pas : la logique des blocs de commande (énigmes, balises), à tester en jeu.

## Décoration (2026-11-09) : temple de jungle abandonné

Une seule direction artistique partout : un temple de jungle que la forêt a repris. Briques de pierre moussues et fissurées, cobble moussu, mousse, briques de boue dans l'aile des Serpents ; aucune autre famille de blocs (pas d'ardoise, de pierre noire, de prismarine, de cuivre ni de lumière d'âme). Une passe dédiée (`scripts/donjon_temple_decor*.py`, lancée par le générateur) habille tout sans jamais gêner un passage, un mécanisme, un ancrage ni la position du boss (cases réservées autour de chacun).
- **Murs** : plinthe, bandeaux sculptés tous les 10 blocs, nervures verticales, patchs de pierre moussue, fissurée ou en cobble (30 000 blocs repeints).
- **Sols** : bordures sculptées, damiers, médaillons et losanges, mousse qui envahit et briques fissurées par les racines ; serpent de boue qui traverse l'aile des Serpents.
- **Végétation envahissante** : lianes sur les parois, les totems et les statues (≈ 940 blocs de lianes), grosses racines et touffes de feuilles qui percent les voûtes, buissons et azalées dans les angles, herbes hautes, fougères, fleurs, champignons, pétales, tapis de mousse, spore blossom ; deux grands arbres de jungle ont poussé dans la cour du Parvis.
- **Ruines** : poutres de jungle cassées, lustres rouillés (chaîne, parfois sa lanterne), pilastres à moitié effondrés, gravats mousseux, statues de gardiens et 13 totems de jungle (visages en citrouille sculptée), braseros éteints ou à lanterne, toiles et ossements.
- **Aile du boss** : voûte de nervures de pierre moussue avec lianes pendantes et lanternes survivantes, racines géantes en grappes, contreforts rongés, plateformes de pierre moussue envahies d'herbe, de buissons et de mousse avec racines, lianes et feuillage dessous, stalagmites de roche moussue dans la lave, arche d'entrée envahie de lianes.

## Écarts par rapport au plan et à la demande

- **Colline** : pas de massif plein ; le temple est une coque (maçonnerie + roche, 4 blocs) autour des salles, pour garder le fichier léger (320 000 blocs). L'extérieur ressemble à un affleurement, pas à une colline.
- **Aile du boss** : ajoutée au sud de l'antichambre (gabarit passé de 130 × 100 × 130 à 130 × 100 × 216) : la salle basse prévue au plan ne pouvait pas contenir un gouffre de 30 blocs ni 50 blocs de haut. L'escalier de descente vers le Gardien est remplacé par un couloir droit.
- **Accès au Parvis** : par `/tp` (comme le n°1) ; pas de chemin à pied depuis le sol extérieur. Dans le serveur, l'entrée sera la Porte des Profondeurs de la citadelle.
- **Ailes** : ouvertes (arches), non scellées ; ce sont les socles du sanctuaire qui imposent de les réussir.
- **Idoles** : remplacées par des drapeaux de commande et des balises lumineuses dans le vestibule (pas d'objet à transporter).
- **Salles cachées** : pas de chute d'eau (accès par lianes, étagère, trappes, ancrages cachés, plaque) pour éviter l'eau mal confinée.
- **Plan PNG** non généré ; durées non confirmées par une partie réelle ; non testé sur serveur dédié.
