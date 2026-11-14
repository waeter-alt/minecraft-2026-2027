# [claude-build]
"""fiche_donjon_temple.py - ecrit docs/donjon_temple.md a partir de exports/temple_cibles.json (coordonnees du monde = locales + origine)."""
import json
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
conf = json.loads((RACINE / "exports/temple_cibles.json").read_text(encoding="utf-8"))
OX, OY, OZ = conf["origine"]


def w(p):
    return f"{p[0] + OX} {p[1] + OY} {p[2] + OZ}"


C = conf["cibles"]
L = []
a = L.append
a("<!-- [claude-build] fiche du donjon n°2, ecrite par scripts/fiche_donjon_temple.py -->")
a("# Donjon n°2 « Le Temple des Mille Racines »")
a("")
a(f"Monde « Donjon 2 » (superflat, sol à Y-61), Minecraft 1.21.1 + NeoForge 21.1.216 + mod **Grappin 2.4.1** (onglet créatif « Donjons ») (obligatoire chez chaque joueur). Structure `temple` "
  f"{conf['taille'][0]} × {conf['taille'][1]} × {conf['taille'][2]}, posée en {OX} {OY} {OZ} (x {OX} à {OX + conf['taille'][0] - 1}, z {OZ} à {OZ + conf['taille'][2] - 1}). "
  "Générateur : `scripts/donjon_temple.py`, contrôle : `scripts/verif_donjon_temple.py`, plan de conception : `donjon_temple_plan.md`.")
a("")
a("Style jungle, comme la Gorge suspendue : briques de pierre moussues et fissurées, roche en plaques, mousse, lianes à baies lumineuses, racines et troncs de jungle. "
  "On **descend** un temple enseveli : Parvis à ciel ouvert tout en haut, trois ailes et un sanctuaire à l'étage 1, puis les salles du bas jusqu'au Gardien.")
a("")
a("## Poser et remettre à zéro")
a("")
a("- Pose (sans mettre en pause ; on peut la relancer pour poser une nouvelle version : elle efface d'abord toute la zone au-dessus du sol) : `/reload` puis `/function atelier:temple_pose`. Les commandes du temple utilisent des blocs de commande : en solo c'est actif ; "
  "sur un serveur, `enable-command-block=true` dans `server.properties`.")
a(f"- Entrée : `/tp @s {w(conf['entree'])} 0 0` (le Parvis, au sommet). Le grappin est donné automatiquement à qui arrive au Parvis sans en avoir.")
a("- Remise à zéro entre deux parties : `/function atelier:temple_reset` (portes fermées, leviers baissés, énigmes et clé remises, pièges et balises réarmés).")
a("")
a("## Le grappin (Grappin 2.4.1)")
a("")
a("- **Clic droit** (rien de lancé) : lance le crochet (30 blocs). Il ne tient que sur les **points d'ancrage** (bloc métallique faiblement lumineux) ; il en attrape un s'il passe à moins de 1,5 bloc.")
a("- Accroché, **clic droit maintenu** : traction (la corde se rembobine, on est tiré vers l'ancrage). **Clic relâché** : on reste accroché, la corde garde sa longueur, on se balance.")
a("- **ESPACE** (à l'appui) : lâche le crochet, avec un petit saut et la vitesse conservée. S'accroupir lâche aussi. **Molette** : allonge ou raccourcit la corde.")
a("- Pas de dégâts de chute tant qu'on est accroché ; ils reviennent dès le lâcher. Le crochet ne traverse pas les blocs et ne blesse personne.")
a("- Réglages : `config/grappin-common.toml` (portée, aimant, gravité du balancier, élan des touches, vitesse et saut au lâcher, raideur, corde minimale, vitesse du treuil, pas de molette).")
a("- Technique : le balancier est calculé chez le joueur à chaque tick ; ESPACE passe par un petit message client → serveur. Limite connue : maintenir le clic droit pendant un lâcher relance le crochet dans les 4 ticks ; pas testé sur serveur dédié.")
a("")
a("## Le parcours (≈ 31 min pour qui découvre)")
a("")
a("| # | Section | Ce qu'on y fait | Temps |")
a("|---|---|---|---|")
SECT = [
    ("1", "Le Parvis", "Cour à ciel ouvert au sommet, ruines et stèles ; escalier vers le vestibule.", "2 min"),
    ("2", "Le Vestibule des Fresques", "Hub de l'étage 1 : trois arches vers les ailes, trois socles à bouton sous trois fresques (énigme du sanctuaire), trois balises qui s'allument.", "3 min"),
    ("3", "L'Aile des Serpents (est)", "Six dalles à presser dans l'ordre du serpent peint (couleurs). Une erreur : retour au seuil de l'aile.", "3 min"),
    ("4", "L'Aile des Miroirs d'Eau (ouest)", "Bassin à traverser en 7 sauts sur des piliers moussus ; levier caché derrière des lianes. Une chute : retour sur la rive d'entrée.", "3 min"),
    ("5", "Le Puits des Lianes (sud)", "Échelle jusqu'au fond, 3 ancrages bas puis 4 ancrages en zigzag jusqu'à la corniche de l'idole ; plaque finale.", "4 min"),
    ("6", "Le Sanctuaire des Trois Idoles", "Dans le vestibule : presser les trois socles dans l'ordre jaguar → serpent → aigle ; chaque socle ne marche que si son aile est réussie. Ouvre la descente (porte G6).", "2 min"),
    ("7", "La Salle des Colonnes Brisées", "Grande salle à colonnes cassées (combats plus tard, compté en partie) ; sortie sud fermée par la Clé de racine (porte G7).", "2 min"),
    ("8", "Le Couloir des Pièges", "Passerelle au-dessus d'une fosse : 2 stalactites, 2 tronçons qui cèdent, 3 plaques empoisonnées, brèche de 9 blocs à franchir à l'ancrage P1. Chute : retour au début.", "3 min"),
    ("9", "Le Grand Gouffre Intérieur", "Caverne : 6 ancrages en arc (A1 à A6) au-dessus de la fosse, jusqu'à la corniche d'en face. Chute : retour à la corniche d'arrivée.", "4 min"),
    ("10", "La Crypte des Racines", "On plonge dans la fosse d'eau, on prend la Clé de racine sur l'îlot, on remonte avec 3 ancrages (K1 à K3), puis retour en arrière jusqu'à la salle 7.", "3 min"),
    ("11", "L'Antichambre", "Porte à clé de la salle 7 (serrure en pierre magnétique), couloir de statues, long couloir droit vers l'aile du boss.", "1 min"),
    ("12", "La Salle du Gardien (aile du boss)", "Hall de 63 × 63 au-dessus d'un gouffre de lave : 8 plateformes en anneau autour du Gardien des Racines (golem d'arbre qui émerge de la lave), atteintes au grappin ; sortie fermée jusqu'à la mort du Gardien.", "1 min (hors combat)"),
]
for s in SECT:
    a("| " + " | ".join(s) + " |")
a("| | **Total** | sans les salles cachées ni les combats complets | **≈ 31 min** |")
a("")
a("Estimations calculées (marche 4,3 blocs/s, sprint 5,6, balancier réussi 10 à 15 s, chute 30 à 45 s perdues, énigme 1 à 3 min) : **pas encore confirmées par une vraie partie**. "
  "Ordre imposé : 1 → 2 → (3, 4, 5 dans l'ordre voulu) → 6 → 7 → 8 → 9 → 10 → retour à 7 → 11 → 12.")
a("")
a("## Solutions (réservé aux organisateurs)")
a("")
a("Coordonnées du monde.")
a("")
a(f"- **Aile des Serpents** : le serpent peint est sur le mur nord, de gauche à droite : {' → '.join(conf['couleurs'])} (terracotta). Chaque dalle a un pilier de sa couleur : "
  + ", ".join(f"{c} {w((d[0], 63, d[1]))}" for c, d in zip(conf["couleurs"], conf["dalles"])) + ". Réussir allume la balise est du vestibule.")
a(f"- **Aile des Miroirs d'Eau** : le levier est sur la corniche de l'extrémité ouest, derrière un rideau de lianes : {w(C['LW'])}. Il allume la balise ouest.")
a(f"- **Puits des Lianes** : la plaque de l'idole est sur la corniche du fond : {w(C['plaque_idole'])}. Elle allume la balise sud.")
a(f"- **Sanctuaire** : fresques et lanternes (nombre de lanternes sous la fresque = rang) : jaguar 1 (aile des Miroirs), serpent 2 (aile des Serpents), aigle 3 (Puits). "
  f"Socles (de l'ouest à l'est) : aigle {w(C['socle_aigle'])}, jaguar {w(C['socle_jaguar'])}, serpent {w(C['socle_serpent'])}. Ordre : **jaguar → serpent → aigle**. "
  "Une erreur remet l'ordre à zéro (son grave) ; un socle d'une aile non réussie ne fait rien. Réussite : la porte G6 (descente) s'ouvre.")
a(f"- **Clé de racine** : plaque de pression sur l'îlot de la crypte : {w(C['plaque_cle'])} (donnée une fois par joueur). Porte G7 : se tenir devant la serrure (pierre magnétique, {w((44, 42, 92))}) avec la clé en main.")
a("")
a("## Points d'ancrage")
a("")
a("| Nom | Où | Coordonnées |")
a("|---|---|---|")
OU = {"T": "Puits (tutoriel)", "Z": "Puits (ascension)", "Hc": "Puits (caché, vers la salle cachée 5)", "P": "Couloir des pièges (brèche)", "A": "Gouffre", "Hg": "Gouffre (isolé, vers la salle cachée 7)", "K": "Crypte (remontée)", "V": "Salle du boss (chaîne de la voûte)"}
for n_, x, y, z in conf["ancrages"]:
    cle = n_ if n_ in OU else n_[0]
    a(f"| {n_} | {OU.get(cle, '')} | {w((x, y, z))} |")
a("")
PL = conf["plateformes"]
a("## La salle du boss (section 12)")
a("")
a(f"Hall de 63 × 63 blocs (x {16 + OX} à {78 + OX}, z {134 + OZ} à {196 + OZ}), du fond du gouffre (Y {6 + OY}) à une voûte en berceau qui culmine à Y {92 + OY}. "
  "On y arrive par un long couloir droit au niveau 40 (celui de l'antichambre), qui débouche sur la plateforme de départ. Le fond du gouffre est une nappe de lave **confinée** (Y 6 à 7), à 32 blocs sous le départ. Sept piliers géants contre les parois, certains cassés ; chaînes et lanternes pendues à la voûte.")
a("")
a("| Plateforme | Emprise (monde) | Dessus (Y monde) | Cases | Position du boss (monde) |")
a("|---|---|---|---|---|")
for n_, d in PL.items():
    x0, x1, z0, z1 = d["bbox"]
    boss = w(d["boss"]) if d["boss"] else "— (départ, retour après une chute)"
    a(f"| {n_ if n_ != 'D' else 'Départ (D)'} | {w((x0, d['dessus'], z0)).split()[0]}..{w((x1, d['dessus'], z1)).split()[0]} × {w((x0, d['dessus'], z0)).split()[2]}..{w((x1, d['dessus'], z1)).split()[2]} | {d['dessus'] + OY} | {d['cases']} | {boss} |")
a("")
a("Toutes les plateformes sont à au moins 6 blocs les unes des autres (aucun saut, même en sprint) ; le vérificateur confirme que chacune est rejoignable au grappin depuis au moins 2 autres. Les positions du boss sont au centre de chaque plateforme, dégagées sur au moins 5 blocs de haut. Aucun mob n'est posé.")
a("")
(zx, zy, zz), (zdx, zdy, zdz) = conf["zone_chute"]
a(f"- **Chute** : une zone (x {zx + OX} à {zx + zdx + OX}, y {zy + OY} à {zy + zdy + OY}, z {zz + OZ} à {zz + zdz + OZ}) couvre tout le gouffre, au-dessus de la lave et sous la plus basse plateforme. "
  "Un joueur qui y entre est téléporté sur la plateforme de départ, tourné vers la salle, avec 3 s de chute lente (pas de dégâts), son crochet supprimé (fonction `atelier:temple_decrocher`, repéré par l'identifiant du joueur) ; les objets qui tombent sont supprimés ; seuls les joueurs sont concernés.")
a(f"- **Sortie** : porte de barreaux {w((39, 71, 199))} à {w((41, 74, 199))}, dans le mur sud derrière la plateforme la plus haute (P8), puis un court couloir et une petite salle vide. "
  "`/function atelier:temple_ouvrir_sortie_boss` l'ouvre (à relier à la mort du boss) ; `/function atelier:temple_fermer_sortie_boss` la ferme ; `temple_reset` la ferme aussi.")
a("")
a("## Salles cachées (8, vides : ni coffre, ni mob, ni spawner)")
a("")
a("Chacune fait au moins 9 × 9 × 4 (minimum demandé 7 × 7 × 4), avec une lanterne, un emplacement dégagé pour ton coffre et une zone de combat prévue.")
a("")
a("| # | Emprise (du monde) | Accès | Coffre prévu | Zone de mobs prévue |")
a("|---|---|---|---|---|")
for n_, d in conf["cachees_detail"].items():
    x0, x1, y0, y1, z0, z1 = d["boite"]
    a(f"| {n_} | {w((x0, y0, z0))} → {w((x1, y1, z1))} | {d['acces']} | {w(d['coffre'])} | {w(d['mobs'])} |")
a("")
a("Mécanismes cachés : salle 2 (levier derrière les lianes du vestibule, mur ouest), salle 3 (bouton dans l'étagère du mur sud de l'aile des Serpents), "
  "salle 8 (plaque cachée au milieu de tapis de mousse, sur la corniche d'entrée de la crypte).")
a("")
a("## Anti-raccourcis")
a("")
a("`scripts/verif_donjon_temple.py` simule la marche, les sauts, les échelles, la nage et le grappin (réglage réaliste : rayon de corde 14 ; généreux : 38) et vérifie, porte par porte :")
a("- tout ce qui doit être atteignable l'est (grappin réaliste) ;")
a("- rien de ce qui est derrière une porte fermée (G6, G7, salles cachées 2, 3 et 8) n'est atteignable, même en généreux ;")
a("- sans grappin, on n'atteint que le Parvis, le vestibule, les ailes des Serpents et des Miroirs, l'entrée du Puits et les salles cachées 1 et 4 ;")
a("- dans l'aile du boss, sans grappin, aucune plateforme n'est atteignable depuis le départ, et la sortie est inaccessible tant que la porte est fermée (même au grappin) ;")
a("Le résultat actuel est **OK** à chaque étape. Ce que le vérificateur ne couvre pas : la logique des blocs de commande (énigmes, balises), à tester en jeu.")
a("")
a("## Décoration (2026-11-09) : temple de jungle abandonné")
a("")
a("Une seule direction artistique partout : un temple de jungle que la forêt a repris. Briques de pierre moussues et fissurées, cobble moussu, mousse, briques de boue dans l'aile des Serpents ; "
  "aucune autre famille de blocs (pas d'ardoise, de pierre noire, de prismarine, de cuivre ni de lumière d'âme). Une passe dédiée (`scripts/donjon_temple_decor*.py`, lancée par le générateur) habille "
  "tout sans jamais gêner un passage, un mécanisme, un ancrage ni la position du boss (cases réservées autour de chacun).")
a("- **Murs** : plinthe, bandeaux sculptés tous les 10 blocs, nervures verticales, patchs de pierre moussue, fissurée ou en cobble (30 000 blocs repeints).")
a("- **Sols** : bordures sculptées, damiers, médaillons et losanges, mousse qui envahit et briques fissurées par les racines ; serpent de boue qui traverse l'aile des Serpents.")
a("- **Végétation envahissante** : lianes sur les parois, les totems et les statues (≈ 940 blocs de lianes), grosses racines et touffes de feuilles qui percent les voûtes, buissons et azalées dans les angles, "
  "herbes hautes, fougères, fleurs, champignons, pétales, tapis de mousse, spore blossom ; deux grands arbres de jungle ont poussé dans la cour du Parvis.")
a("- **Ruines** : poutres de jungle cassées, lustres rouillés (chaîne, parfois sa lanterne), pilastres à moitié effondrés, gravats mousseux, statues de gardiens et 13 totems de jungle (visages en citrouille sculptée), braseros éteints ou à lanterne, toiles et ossements.")
a("- **Aile du boss** : voûte de nervures de pierre moussue avec lianes pendantes et lanternes survivantes, racines géantes en grappes, contreforts rongés, plateformes de pierre moussue envahies d'herbe, de buissons et de mousse avec racines, lianes et feuillage dessous, "
  "stalagmites de roche moussue dans la lave, arche d'entrée envahie de lianes.")
a("")
a("## Écarts par rapport au plan et à la demande")
a("")
a("- **Colline** : pas de massif plein ; le temple est une coque (maçonnerie + roche, 4 blocs) autour des salles, pour garder le fichier léger (320 000 blocs). L'extérieur ressemble à un affleurement, pas à une colline.")
a("- **Aile du boss** : ajoutée au sud de l'antichambre (gabarit passé de 130 × 100 × 130 à 130 × 100 × 216) : la salle basse prévue au plan ne pouvait pas contenir un gouffre de 30 blocs ni 50 blocs de haut. L'escalier de descente vers le Gardien est remplacé par un couloir droit.")
a("- **Accès au Parvis** : par `/tp` (comme le n°1) ; pas de chemin à pied depuis le sol extérieur. Dans le serveur, l'entrée sera la Porte des Profondeurs de la citadelle.")
a("- **Ailes** : ouvertes (arches), non scellées ; ce sont les socles du sanctuaire qui imposent de les réussir.")
a("- **Idoles** : remplacées par des drapeaux de commande et des balises lumineuses dans le vestibule (pas d'objet à transporter).")
a("- **Salles cachées** : pas de chute d'eau (accès par lianes, étagère, trappes, ancrages cachés, plaque) pour éviter l'eau mal confinée.")
a("- **Plan PNG** non généré ; durées non confirmées par une partie réelle ; non testé sur serveur dédié.")
(RACINE / "docs" / "donjon_temple.md").write_text("\n".join(L) + "\n", encoding="utf-8")
print("ecrit docs/donjon_temple.md,", len(L), "lignes")
