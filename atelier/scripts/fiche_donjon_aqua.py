# [claude-build]
"""fiche_donjon_aqua.py - ecrit docs/donjon_aquatique.md a partir de exports/aqua_cibles.json et exports/aqua_verif.json (coordonnees du monde = locales + origine).
Lancer apres donjon_aqua.py puis verif_donjon_aqua.py :
  test-local\\venv\\Scripts\\python.exe atelier\\scripts\\fiche_donjon_aqua.py"""
import json
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
conf = json.loads((RACINE / "exports/aqua_cibles.json").read_text(encoding="utf-8"))
verif = json.loads((RACINE / "exports/aqua_verif.json").read_text(encoding="utf-8"))
OX, OY, OZ = conf["origine"]
TX, TY, TZ = conf["taille"]


def w(p):
    return f"{p[0] + OX} {p[1] + OY} {p[2] + OZ}"


def boite(c):
    a, b = c["cases"][0], c["cases"][-1]
    return f"{w(a)} → {w(b)}"


C, P, V = conf["cibles"], conf["portes"], verif["trajets"]
H = conf["cachees_detail"]
L = []
a = L.append
a("<!-- [claude-build] fiche du donjon aquatique, ecrite par scripts/fiche_donjon_aqua.py -->")
a("# Donjon aquatique « Le Sanctuaire des Marées »")
a("")
a(f"Monde « Donjon 2 » (dossier `Donjon 2 (1)`, superflat, sol à Y-61 ; le Temple des Mille Racines est dans un autre monde, « Donjon 1 »), Minecraft 1.21.1 + NeoForge 21.1.216 + mod **Grappin 2.5.0** (il apporte le Voile des Profondeurs ; "
  f"obligatoire chez chaque joueur). Structure `aqua` {TX} × {TY} × {TZ}, posée en {OX} {OY} {OZ} (x {OX} à {OX + TX - 1}, z {OZ} à {OZ + TZ - 1}). "
  "Générateur : `scripts/donjon_aqua.py`, contrôle : `scripts/verif_donjon_aqua.py`, plan : `donjon_aquatique_plan.md`.")
a("")
a("Un monument océanique englouti, dans le style de ceux de Minecraft : briques de prismarine, prismarine et prismarine sombre en bandeaux, sols en damier, "
  "lanternes de mer encastrées dans les murs, les sols et les plafonds, pilastres, éponges mouillées dans les salles cachées, noyau d'or dans la salle du boss. "
  "Les salles sont **noyées jusqu'au plafond** : on nage presque partout. Il ne reste à l'air que le Débarcadère (le départ), la salle du boss, les salles cachées d'air "
  "et des cloches d'air flottantes. Pas de grappin (aucun ancrage), pas de conduit, pas de lave, ni coffre, ni conteneur, ni générateur, ni monstre, ni boss.")
a("")
a("**État : généré et contrôlé par script, pas encore testé en jeu.** Les durées de parcours sont des estimations.")
a("")
a("## Poser et remettre à zéro")
a("")
a("- Le générateur crée lui-même le datapack `atelier` du monde visé (dossiers et `pack.mcmeta`) ; si le monde n'existe pas, il le dit et n'écrit que `atelier/exports`. "
  "Le monde est désigné par son nom en jeu (`--monde \"Donjon 2\"` par défaut), pas par le nom de son dossier.")
a("- Datapack créé pendant que le monde est ouvert : quitter le monde et y revenir pour qu'il soit pris en compte.")
a("- Pose : `/reload` puis `/function atelier:aqua_pose` (ne pas mettre en pause ; la zone au-dessus du sol est effacée avant la pose, on peut donc reposer une nouvelle version).")
a(f"- Entrée : `/tp @s {w(conf['entree'])}`.")
a("- Remise à zéro (portes refermées, leviers baissés, trappe refermée, clé retirée, compteurs à zéro) : `/function atelier:aqua_reset`.")
a("- Les blocs de commande règlent `doMobSpawning` sur `false` dans tout le monde et `commandBlockOutput` sur `false`.")
a("")
a("## Le Voile des Profondeurs (lu dans le code du mod)")
a("")
a("- Objet `grappin:voile`, un casque. Il est **donné automatiquement** en entrant dans le Débarcadère (une fois : pas si on l'a déjà sur la tête ou dans l'inventaire).")
a("- Tant que les yeux sont sous l'eau et qu'il reste de la réserve, l'air du porteur est maintenu au maximum ; la réserve dure **40 s** (`voileReserveSecondes`).")
a("- À l'air libre il se recharge entièrement en **12 s** (`voileRechargeSecondes`). Il prévient (message et son) sous **10 s** (`voileAlerteSecondes`).")
a("- Réserve vide : le voile ne protège plus, on se noie normalement (15 s d'air, puis les dégâts). Les coups reçus n'usent pas le voile.")
a("")
a("### Aucune autre façon de respirer sous l'eau")
a("")
a("- Dans tout le gabarit, les effets Respiration aquatique et Puissance du conduit sont effacés à chaque tick (potions, casque de tortue, conduit).")
a(f"- Les tunnels submergés sont des **zones de pression** ({len(conf['pression'])} boîtes) : toutes les demi-secondes, tout joueur qui s'y trouve **sans le voile sur la tête** "
  "prend 3 points de dégâts de noyade (un joueur à 20 PV meurt en quelques secondes, soit une dizaine de blocs de nage au plus ; cadence exacte à mesurer en jeu). "
  "Un casque enchanté Respiration ne sert donc à rien : c'est le voile qui est contrôlé, pas l'air.")
a("- Limites connues : les dégâts ne touchent pas un joueur en créatif ou en spectateur ; rien n'empêche un joueur en survie de casser les murs "
  "(prévoir le mode aventure) ; une perle de l'Ender peut franchir une grille ouverte à vue, pas un tunnel coudé.")
a("")
a("## Cloches d'air : les seuls points de recharge")
a("")
a("Le voile ne se recharge qu'à l'air libre (12 s pour une recharge complète). Dans des salles noyées jusqu'au plafond, il faut donc des endroits où l'on peut remonter respirer : "
  f"**{len(conf['cloches'])} cloches d'air**, de petits pavillons de prismarine de 5 × 5, ouverts par dessous, posés sur quatre pieds, avec une lanterne de mer au plafond. "
  "L'air y reste emprisonné ; on y entre en nageant par dessous. Il y en a une tous les 20 blocs dans chaque salle (calotte à Y local 52), et six basses (calotte à 46) "
  "juste au dessus de l'entrée ou de la sortie de chaque tunnel. On respire aussi dans le mini-bassin du départ, dans le bassin du boss et sous le plafond des salles cachées.")
a("")
a("Ce n'est pas tout à fait « sans poche d'air » comme demandé : sans elles, le donjon serait infaisable (40 s de réserve, 55 s au maximum avec l'air de base). À toi de décider si on en veut moins.")
a("")
a("| # | Position (centre, à hauteur de l'air) |")
a("|---|---|")
for i_, c_ in enumerate(conf["cloches"], 1):
    a(f"| {i_} | {w(c_)} |")
a("")
a("## Parcours (≈ 27 min sans les salles cachées, ≈ 31 min avec quelques-unes ; estimation)")
a("")
a("Presque rien ne se fait à pied. Les interrupteurs sont des **vannes** : des trappes en bois posées à plat sous l'eau, qui se cliquent à la main (clic droit). "
  "Un levier ou un bouton ne tient pas sous l'eau, qui le détruit en coulant dans sa case ; une trappe se waterlogue, elle reste.")
a("")
a("| # | Section | Durée | Ce qu'on y fait | Porte de sortie |")
a("|---|---|---|---|---|")
a(f"| 1 | Le Débarcadère | 2 min | Seule partie à sec. On reçoit le voile. Une vanne au fond du petit bassin, entre deux lanternes de mer ({w(C['vanne_G1'])}) : un clic et la herse de la galerie noyée se lève. Première plongée. | G1, herse {boite(P['G1'])} |")
a(f"| 2 | La Colonnade | 4 min | Grande salle noyée, semée de colonnes en prismarine. La vanne est au sommet de la colonne centrale ({w(C['vanne_G2'])}). "
  "Le panneau du mur ouest donne l'ordre des glyphes pour la Bibliothèque. | "
  f"G2, herse {boite(P['G2'])} |")
a(f"| 3 | Les Citernes | 4 min | Trois citernes profondes reliées par des passages. Deux vannes sur des socles ({w(C['vanne_3a'])} et {w(C['vanne_3b'])}), à ouvrir toutes les deux. | "
  f"G3, grille au fond de la troisième citerne, {boite(P['G3'])} |")
a(f"| 4 | Tunnel T1 | 1 min | **Voile obligatoire.** Depuis la cloche au dessus du puits : {V['T1']['total']} pas, {V['T1']['ordinaire_s']} s en nage ordinaire. Sortie dans le bassin de la Bibliothèque. | — |")
a(f"| 5 | La Bibliothèque noyée | 4 min | Quatre glyphes de terre cuite sur le mur nord, chacun avec sa vanne : à ouvrir dans l'ordre lu dans la Colonnade (les vannes se referment seules). Une erreur remet à zéro (note grave). | "
  f"G4, herse {boite(P['G4'])} |")
a(f"| 6 | Les Écluses | 4 min | Sur le socle du bassin B, une vanne ({w(C['vanne_cle'])}) donne la **Clé du Récif** et fait céder la grille au fond du bassin A. | G5, grille au fond du bassin A, {boite(P['G5'])} |")
a(f"| 7 | Tunnel T2 | 1 min | **Voile obligatoire.** {V['T2']['total']} pas, {V['T2']['ordinaire_s']} s. | — |")
a(f"| 8 | La salle des Pompes | 3 min | Trois vannes sur le mur nord ({w(C['vanne_p20'])}, {w(C['vanne_p24'])}, {w(C['vanne_p28'])}), toutes à ouvrir. | "
  f"G6, grille dans le second bassin, {boite(P['G6'])} |")
a(f"| 9 | Tunnel T3 | 1 min | **Voile obligatoire.** {V['T3']['total']} pas, {V['T3']['ordinaire_s']} s, avec une descente de 12 blocs. | — |")
a(f"| 10 | L'Antichambre | 1 min | Herse à clé : se présenter devant, la Clé du Récif en main (elle est consommée). Sans la clé, un message l'indique. Derrière, une galerie noyée mène sous la salle du boss : {V['G7']['total']} pas, {V['G7']['ordinaire_s']} s (sans pression, mais longue). | G7, herse {boite(P['G7'])} |")
a(f"| 11 | Le Cœur du Temple | 2 min hors combat | On émerge dans le bassin du boss (salle à l'air libre, îlot central, rives sèches, noyau d'or dans le mur nord). | SORTIE, grille {boite(P['SORTIE'])} |")
a("")
a("Le parcours n'est pas un simple couloir : l'ordre des glyphes se lit dans la section 2 et sert dans la 5, la clé se prend dans la 6 et sert dans la 10, "
  "les deux vannes des Citernes se font dans l'ordre qu'on veut, et huit salles cachées s'ouvrent en chemin. "
  "Mais les portes principales se franchissent toujours dans le même ordre (voir « Écarts »).")
a("")
a("## Les passages qui exigent le voile")
a("")
a("Trajet complet = de la cloche d'air la plus proche de l'entrée, jusqu'à celle de la sortie (la sortie des salles cachées est à l'air).")
a("")
a("| Passage | Grille d'entrée | Tunnel | Total | Nage ordinaire | Nage rapide | Pourquoi on ne passe pas sans voile |")
a("|---|---|---|---|---|---|---|")
for n_, g in (("T1", "G3"), ("T2", "G5"), ("T3", "G6")):
    a(f"| {n_} | {boite(P[g])} | {V[n_]['blocs']} blocs | {V[n_]['total']} pas | {V[n_]['ordinaire_s']} s | {V[n_]['rapide_s']} s | Zone de pression sur toute la longueur : mort en quelques secondes, bien avant la sortie. |")
for k in ("5", "6", "7", "8"):
    n_ = f"H{k}"
    a(f"| Salle cachée {k} | puits en {w(H[k]['puits'])} | {V[n_]['blocs']} blocs | {V[n_]['total']} pas | {V[n_]['ordinaire_s']} s | {V[n_]['rapide_s']} s | Même zone de pression. |")
a("")
a("Avec le voile (40 s de réserve, puis 15 s d'air) : le plus long trajet prend 28 s en nage ordinaire. Il reste de la marge, mais pas de quoi hésiter ou faire demi-tour au milieu. "
  "Le contrôle refuse tout trajet qui dépasserait 32 s (80 % de la réserve) et toute vanne à plus de 35 pas d'une prise d'air.")
a("")
a("## Solutions")
a("")
a("1. Débarcadère : plonger dans le petit bassin, ouvrir la vanne du fond (clic droit sur la trappe).")
a("2. Colonnade : nager jusqu'à la colonne centrale, ouvrir la vanne à son sommet.")
a("3. Citernes : ouvrir les deux vannes (socles des citernes 1 et 2), puis plonger au fond de la citerne 3.")
a("4. Bibliothèque : **Poulpe, Raie, Crabe, Étoile**.")
a("5. Écluses : ouvrir la vanne du socle du bassin B (clé et grille), puis plonger dans le bassin A jusqu'au puits.")
a("6. Pompes : ouvrir les trois vannes, puis plonger dans le second bassin.")
a("7. Antichambre : clé en main devant la herse, puis suivre la galerie noyée jusqu'au bassin du boss.")
a("")
a("## Salle du boss")
a("")
a("| Repère | Position | Rôle |")
a("|---|---|---|")
BOSS_ROLE = {"B1": "Îlot central (apparition du boss)", "B2": "Rive ouest, côté entrée", "B3": "Rive est, côté sortie", "B4": "Sous l'eau, à mi-profondeur, côté ouest",
             "B5": "Sous l'eau, à mi-profondeur, côté est"}
for k, v in conf["boss"].items():
    a(f"| {k} | {w(v)} | {BOSS_ROLE[k]} |")
a(f"| Sortie | {w(conf['points']['sortie'])} | Derrière la grille scellée |")
a("")
a("- Ouvrir la sortie (à relier à la mort du boss) : `/function atelier:aqua_ouvrir_sortie_boss`.")
a("- La refermer : `/function atelier:aqua_fermer_sortie_boss`.")
a("- Le bassin du Cœur et la galerie qui y mène ne sont pas des zones de pression : on peut y plonger sans voile, avec son seul souffle.")
a("")
a("## Salles cachées (8, dont 4 qui exigent le voile)")
a("")
a("Chacune fait 9 × 9 × 5, vide, éclairée. La 1 est à sec (porte dans le mur du Débarcadère, bouton de pierre). Les 2, 3 et 4 sont noyées, avec une rangée d'air sous le plafond, sol en éponge mouillée, porte camouflée en prismarine et vanne dans la salle voisine. Les 5 à 8 sont des salles d'air au bout d'un tunnel noyé (voile obligatoire). Emplacement libre pour un coffre et pour des monstres indiqués ci-dessous.")
a("")
a("| # | Accès | Voile | Vanne, bouton ou puits | Emplacement du coffre | Emplacement des monstres |")
a("|---|---|---|---|---|---|")
for k, c in H.items():
    acces = f"puits {w(c['puits'])}" if c["voile"] else (f"vanne {w(C['vanne_H' + k])}" if c["eau"] else f"bouton {w(C['bouton_H' + k])}")
    a(f"| {k} | {c['acces']} | {'oui' if c['voile'] else 'non'} | {acces} | {w(c['coffre'])} | {w(c['mobs'])} |")
a("")
a("## Contrôles automatiques")
a("")
a(f"- Générateur : eau confinée (aucune source au contact d'une case d'air sur le côté ou dessous), aucun bloc interdit (coffre, conteneur, générateur, conduit, lave, "
  "colonne de bulles, ancrage), aucun levier ni bouton au contact de l'eau, panneaux d'au plus 14 caractères par ligne, chaque panneau et bouton a son appui. 0 erreur.")
a("- Éclairage : lumière propagée depuis les lanternes de mer encastrées, puis blocs `light` invisibles (immergés dans l'eau) là où il manquait. "
  "Lumière minimale 5 sur toutes les cases libres, eau comprise ; les monstres n'apparaissent qu'à 0.")
a(f"- Vérificateur : résultat **{verif['resultat']}**. Avec le voile, chaque étape donne exactement les cibles prévues, aucune plus tôt. "
  f"Sans voile et toutes portes ouvertes, on n'atteint que : {', '.join(verif['sans_voile'])} — rien au-delà du premier tunnel, aucune salle cachée sous l'eau.")
a("")
a("## Écarts et points à savoir")
a("")
a("- **Pas testé en jeu** : ni la pose, ni les blocs de commande, ni le ressenti des tunnels. À faire dans le client du dépôt.")
a("- Le voile vient de la PR #12 (Grappin 2.5.0), pas encore fusionnée : cette branche est empilée dessus.")
a("- La salle du boss garde ses rives sèches et son air libre ; le Débarcadère aussi. Tout le reste est noyé.")
a("- Pas de cascades ni de courants : une cascade est de l'eau qui coule, donc non confinée, et un courant demande des colonnes de bulles, exclues. "
  "L'ambiance repose sur le décor de monument et la lumière.")
a("- Un petit herbier (algues, herbes, quelques coraux) ne pousse que dans la Colonnade ; ailleurs, le décor reste celui d'un monument.")
a("- Parcours principal en chaîne (G1 à G7 dans l'ordre) ; la non-linéarité tient aux renvois entre salles (glyphes, clé) et aux salles cachées, pas à des embranchements.")
a("- Le vérificateur ne simule pas les sauts d'un bloc à l'autre ni les perles de l'Ender ; il suppose qu'on ne casse et ne pose aucun bloc.")
(RACINE / "docs/donjon_aquatique.md").write_text("\n".join(L) + "\n", encoding="utf-8")
print("ecrit docs/donjon_aquatique.md :", len(L), "lignes")
