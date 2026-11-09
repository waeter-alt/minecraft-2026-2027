# [claude-build]
"""fiche_donjon2.py - Ecrit donjon.md (fiche du donjon refait) a partir de exports/donjon2_cibles.json, en coordonnees du monde."""
import json
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
c = json.loads((RACINE / "exports/donjon2_cibles.json").read_text(encoding="utf-8"))
O = c["origine"]


def w(p):
    return f"{O[0] + p[0]} {O[1] + p[1]} {O[2] + p[2]}"


SECTIONS = [
    ("S1", "Le Seuil des Racines", 2, "Escalier d'entrée (le grappin est donné en entrant). Fosse d'eau de 10 blocs de large : 3 ancrages bas pour apprendre le balancier. Une chute ne coûte que l'échelle."),
    ("S2", "La Galerie des Échos", 3, "La grille de sortie (G1) s'ouvre quand les 3 leviers sont levés : un en vue près de la grille, un caché derrière des lianes au fond d'une alcôve, un sur une corniche haute qu'on n'atteint qu'avec l'ancrage du plafond."),
    ("S3", "Le Gouffre de l'Arbre-Mère", 5, "8 ancrages en arc autour de l'Arbre-Mère (écarts de 9 à 10 blocs), puis treuil jusqu'à la corniche haute du nord. Une chute dans la fosse renvoie à la corniche d'entrée."),
    ("S4", "La Cascade", 3, "8 piliers en spirale au-dessus du bassin, puis 2 balanciers vers l'alcôve cachée derrière la chute d'eau : son bouton ouvre la grille G2 (à l'est, par les marches de la corniche de départ). Une chute renvoie à la corniche de départ."),
    ("S5", "Le Jardin des Symboles", 4, "Carrefour. 4 socles à bouton sous 4 fresques. Il faut les presser dans l'ordre du nombre de lanternes posées sous les mêmes fresques dans les salles 1 à 4. Une erreur remet à zéro (son grave), la réussite ouvre le passage du puits (son clair)."),
    ("S6", "La Clé de l'Arbre", 4, "On saute dans le puits (bassin au fond), on prend la Clé de racine sur la plaque du sanctuaire, puis on remonte avec 3 ancrages scellés dans les parois et le treuil."),
    ("S7", "La Salle des Lianes", 2, "Grande salle à piliers (combats plus tard). La porte du sud ne s'ouvre qu'avec la Clé de racine en main, devant la serrure ; la clé est alors reprise."),
    ("S8", "Les Ponts Brisés", 3, "Passerelle de 3 blocs à 48 au-dessus d'une fosse : 2 stalactites qui tombent quand on passe dessous, 2 tronçons qui cèdent 1/2 s après le passage (ils reviennent 2 s plus tard), 3 plaques empoisonnées, une brèche de 9 blocs à franchir à l'ancrage. Une chute renvoie au début de la passerelle."),
    ("S9", "L'Ascension des Racines", 4, "Salle verticale : 6 ancrages en zigzag (écarts de 10 à 14 blocs) et 2 corniches de repos, jusqu'à la corniche de sortie à 40 blocs au-dessus de l'eau. Une chute ramène dans l'eau du bas."),
    ("S10", "La Salle du Gardien", 1, "Grande salle à colonnes de troncs, estrade à l'ouest : vide, le boss viendra plus tard."),
]
L = ["<!-- [claude-build] fiche du donjon n°1 refait, ecrite par sources/fiche_donjon2.py -->",
     "# Donjon n°1 « La Gorge suspendue » (version 2, 2026-11-09)", "",
     "Monde « Donjon 1 » (dossier `Nouveau monde (1)`), Minecraft 1.21.1 + NeoForge + mod Grappin 2.3.0 (obligatoire chez chaque joueur). "
     f"Structure `donjon2` 160 × 120 × 176, posée en {O[0]} {O[1]} {O[2]} (x {O[0]} à {O[0] + 159}, z {O[2]} à {O[2] + 175}). Générateur : `sources/donjon2.py`, contrôle : `sources/verif_donjon2.py`.",
     "", "## Poser et remettre à zéro", "",
     "- Pose (une seule fois, sans mettre en pause) : `/reload` puis `/function atelier:donjon_pose`. La fonction charge la zone, démolit l'ancien donjon (air sur chacun de ses blocs), pose le nouveau, remet tout à zéro et libère les chunks.",
     "- Entrée : `/tp @s " + w((-2, 1, 81)) + " -90 0` (on entre vers l'est).",
     "- Remise à zéro entre deux parties : `/function atelier:donjon_reset` (portes fermées, leviers baissés, énigme, clé retirée des inventaires, stalactites et passerelle remises).",
     "", "## Le grappin (mod Grappin 2.3.0)", "",
     "- Clic droit : lance le crochet (portée 30 blocs). Il ne tient que sur les **points d'ancrage** (bloc métallique faiblement lumineux) ; il les attrape s'il passe à moins de 1,5 bloc. Sur tout autre bloc il rebondit.",
     "- Accroché, la corde garde sa longueur : on se balance. Les touches de déplacement donnent un petit élan le long de l'arc.",
     "- Maintenir **saut** : treuil, la corde se rembobine (6 blocs/s). **Molette** : allonge ou raccourcit la corde.",
     "- **S'accroupir** : lâcher, en gardant sa vitesse, avec un petit saut vers le haut (réglage `sautAuLacher`, 0,42 = un saut normal). Clic droit accroché : lâcher et relancer aussitôt (enchaîner en l'air).",
     "- Pas de dégâts de chute tant qu'on est accroché ; ils reviennent dès qu'on lâche. Le crochet ne traverse pas les blocs et ne blesse personne.",
     "- Technique : lâcher en haut de l'arc, du côté où l'on va, puis tirer vers l'ancrage suivant avant de redescendre.",
     "- Réglages : `config/grappin-common.toml` (portée, aimant, gravité du balancier, élan des touches, vitesse et saut au lâcher, treuil, molette).",
     "", "## Le parcours (≈ 31 min pour qui découvre)", "", "| # | Section | Ce qu'on y fait | Temps |", "|---|---|---|---|"]
tot = 0
for k, (s, nom, t, txt) in enumerate(SECTIONS, 1):
    L.append(f"| {k} | {nom} | {txt} | {t} min |")
    tot += t
L += [f"| | **Total** | sans les salles cachées ni les combats | **≈ {tot} min** |", "",
      "Bases : marche 4,3 blocs/s ; un balancier visé et réussi 10 à 15 s ; une chute 30 à 45 s perdues ; une énigme 1 à 3 min.", "",
      "Ordre imposé : 1 → 2 → 3 → 4 → 5, puis 6 (la clé) et retour au 5, puis 7 → 8 → 9 → 10. Les sections 5, 6 et 7 forment un embranchement : on doit revenir sur ses pas.", "",
      "## Solutions (réservé aux organisateurs)", ""]
lv = c["leviers"]
L += [f"- **Grille G1 (Galerie)** : 3 leviers. L1 en {w(lv['L1'])} (près de la grille), L2 en {w(lv['L2'])} (alcôve, derrière les lianes), L3 en {w(lv['L3'])} (corniche haute, par l'ancrage G du plafond).",
      f"- **Grille G2 (Cascade)** : bouton en {w(c['cibles']['BG2'])}, au fond de l'alcôve derrière la chute d'eau (ancrages C1 puis C2).",
      "- **Énigme du Jardin** : fresques et lanternes : "
      + ", ".join(f"{s} ({c['ordre_enigme'].index(s) + 1} lanterne{'s' if c['ordre_enigme'].index(s) else ''}, salle {['racine', 'goutte', 'oeil', 'feuille'].index(s) + 1})" for s in ["racine", "goutte", "oeil", "feuille"])
      + f". Ordre : **{' → '.join(c['ordre_enigme'])}**. Socles : " + ", ".join(f"{s} {w(c['cibles']['socle_' + s])}" for s in ["feuille", "oeil", "goutte", "racine"]) + " (de l'ouest à l'est : feuille, œil, goutte, racine).",
      f"- **Clé de racine** : plaque du sanctuaire en {w(c['cibles']['plaque_cle'])}, au fond du puits. Elle se donne une fois par joueur tant qu'il ne l'a pas.",
      f"- **Porte à clé G4** : se tenir devant la serrure (pierre magnétique) vers {w(c['cibles']['serrure'])} avec la clé en main.",
      "", "## Les points d'ancrage", "", "| Nom | Où | Coordonnées |", "|---|---|---|"]
OU = {"T": "Seuil (tutoriel)", "G": "Galerie, plafond (levier L3)", "A": "Gouffre", "C": "Cascade", "K": "Puits de la clé", "P": "Ponts Brisés (brèche)", "Z": "Ascension", "H": "salle cachée"}
for n, x, y, z in c["ancrages"]:
    L.append(f"| {n} | {OU.get(n[0], '?')}{' ' + n[1] if n[0] == 'H' else ''} | {w((x, y, z))} |")
L += ["", f"{len(c['ancrages'])} ancrages en tout, aucun ailleurs.", "", "## Les 8 salles cachées (vides)", "",
      "| # | Accès | Intérieur (x y z min → max) | Coffre | Zone des mobs |", "|---|---|---|---|---|"]
for k, d in c["cachees_detail"].items():
    x0, x1, y0, y1, z0, z1 = d["boite"]
    L.append(f"| {k} | {d['acces']} | {w((x0, y0, z0))} → {w((x1, y1, z1))} ({x1 - x0 + 1}×{z1 - z0 + 1}×{y1 - y0 + 1}) | {w(d['coffre'])} | autour de {w(d['mobs'])} |")
L += ["", "Chaque salle a une seule entrée (qui sert de sortie) et une lanterne. Rien n'y est posé : ni coffre, ni mob, ni générateur.", "",
      "## Les anciens raccourcis et ce qui les bloque", "",
      "| Ancien raccourci (version 1, finie en 5 min) | Remède |", "|---|---|",
      "| Le crochet s'accrochait à n'importe quel bloc | Il ne tient que sur les points d'ancrage, posés seulement là où le balancier fait partie du parcours |",
      "| Traction en ligne droite sur 30 blocs | Balancier : la corde garde sa longueur, il faut de l'élan et enchaîner |",
      "| Sections ouvertes les unes sur les autres | Salles fermées, coque de roche de 4 blocs, reliées par des tunnels à portes |",
      "| Rien à chercher | 3 leviers, un bouton caché, une énigme, une clé à aller chercher (retours en arrière obligatoires) |",
      "| Chute sans conséquence | Fosses qui renvoient au début de la section ; pièges sur les ponts |",
      "", "Vérification (`verif_donjon2.py`) : à chaque étape, portes fermées, on calcule tout ce qu'on atteint à pied, en sautant et au grappin. "
      "Réglage réaliste (balancier de 14 blocs) : tout ce qui doit l'être est atteint. Réglage généreux (38 blocs, élan compris) : rien au-delà d'une porte fermée. "
      "Sans grappin, on ne passe même pas la fosse du Seuil. La sortie n'est pas visible depuis l'entrée.", "",
      "## Mécanismes", "",
      "49 blocs de commande en 2 chaînes enterrées sous les sols du Jardin et de la Salle des Lianes (aucun conteneur, donc pas de distributeur). Ils règlent aussi `doMobSpawning false` "
      "et `commandBlockOutput false`. Tout le donjon est éclairé (blocs de lumière invisibles), l'eau est contenue (vérifié par le générateur).", "",
      "## Écarts avec la demande", "",
      "- Le mur de bibliothèques de la salle cachée 5 s'ouvre par commande (le bloc disparaît), pas avec des pistons ; le levier est caché derrière des lianes, pas dans un pot de fleurs (Minecraft ne permet pas d'y poser un levier).",
      "- Salle cachée 1 : rideau de lianes à baies au lieu d'un faux mur de mousse.",
      "- Les combats (salle 7) et le Gardien ne sont pas comptés dans le temps : les mobs viendront plus tard.",
      "- La redstone et les blocs de commande ne se vérifient qu'en jeu.",
      ]
(RACINE / "donjon.md").write_text("\n".join(L) + "\n", encoding="utf-8")
print("donjon.md :", len(L), "lignes")
