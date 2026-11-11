# [claude-build]
"""fiche_donjon_ciel.py - ecrit docs/donjon_ciel.md (fiche de l'Archipel des Nuees) a partir de exports/ciel_cibles.json et exports/ciel_verif.json.
Lancer apres donjon_ciel.py et verif_donjon_ciel.py :
  test-local\\venv\\Scripts\\python.exe atelier\\scripts\\fiche_donjon_ciel.py"""
import json
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
conf = json.loads((RACINE / "exports/ciel_cibles.json").read_text(encoding="utf-8"))
ver = json.loads((RACINE / "exports/ciel_verif.json").read_text(encoding="utf-8"))
O = conf["origine"]
SX, SY, SZ = conf["taille"]
P = {k: v for k, v in conf["points"].items()}
V = ver["vols"]
L = []
a = L.append


def w(p):
    return f"{O[0] + p[0]} {O[1] + p[1]} {O[2] + p[2]}"


def vol(k):
    v = V[k]
    return f"{v['L']} blocs, chute de {v['chute']}, {v['L'] / 7.2:.1f} s sur 10 s"


a("<!-- [claude-build] genere par scripts/fiche_donjon_ciel.py -->")
a("# Donjon n°3 — L'Archipel des Nuées (paravoile)")
a("")
a(f"Monde « Donjon 3 » (superflat, sol à Y-61). Structure `ciel` de {SX} × {SY} × {SZ}, posée en {w([0, 0, 0])}. Il faut le mod Grappin 2.6.1 (NeoForge 21.1.216).")
a("")
a("## Pose")
a("")
a("Le générateur écrit la structure et le datapack dans le monde « Donjon 3 » (il doit exister : à créer dans le client de test, en superflat). Ensuite, en jeu : `/reload`, puis")
a("")
a("```")
a("/function atelier:ciel_pose")
a("```")
a("")
a(f"Le jeu se fige un moment après 30 secondes de chargement : ne pas mettre en pause. Entrée : `/tp @s {w(P['D'])}`. Remise à zéro : `/function atelier:ciel_reset`.")
a("")
a("## La paravoile")
a("")
a("Donnée à l'entrée (à qui ne l'a pas déjà). Elle se tient en main. En l'air, Espace la déploie ; Espace ou s'accroupir la range. Le regard dirige : tête baissée on pique, tête haute on freine.")
a("Réglages par défaut : vol de **10 s**, recharge de 6 s au sol, 7,2 blocs/s en avant, 1,8 bloc/s de descente à plat (finesse 4 pour 1), donc au mieux 72 blocs pour 18 de chute.")
a("Elle ne fait que descendre : on remonte à l'intérieur des tours. Sont retirés dans tout le donjon : élytres, perles d'Ender, charges de vent, fruits de chorus, trident, effets de chute lente et de lévitation.")
a("")
a("## Les vols (le plus long vol réaliste de chaque paire, d'après le vérificateur)")
a("")
a("| Vol | Longueur et chute |")
a("|---|---|")
for k in V:
    a(f"| {k.replace('>', ' → ')} | {vol(k)} |")
a("")
a("Un vol est « réaliste » s'il fait au plus 60 blocs, perd entre un tiers de sa longueur et sa longueur, et que le trajet est libre de tout bloc. Le contrôle « généreux » (80 blocs, au moins un quart de chute, sans obstacle) ne doit ouvrir aucun raccourci.")
a("")
a("## Parcours (≈ 32 min ; estimation)")
a("")
a("| # | Section | Ce qu'on y fait | Porte ou vol suivant |")
a("|---|---|---|---|")
a(f"| 1 | Le Débarcadère | Départ, don de la paravoile, panneaux d'explication sur la stèle ({w(P['D'])}). | Vol vers P1 : {vol('D>P1')} |")
a(f"| 2 | Les Pierres suspendues | Trois îlots (P1, P2, P3), une lanterne au centre de chacun. On y plane sans montée. | P1→P2, P2→P3, P3→AI |")
a(f"| 3 | La tour de l'Aiguille | Les glyphes ouvrent la porte ; puis trois niveaux à monter, chacun fermé par une herse qu'un levier du même niveau fait céder. Balcon à {w(P['AI_balcon'])}. | Vol vers G1 : {vol('AI>G1')} |")
a(f"| 4 | Le Moulin des Vents | Trois girouettes (leviers au sommet des tours de G1, G2, G3) ouvrent la herse du moulin. | G1→G2→G3→MV, puis MV→RL |")
a(f"| 5 | Le Relais | Tour de recharge sur le grand vide de MV à CP (90 blocs, coupés en deux vols). Balcon à {w(P['RL_balcon'])}. | RL→CP : {vol('RL>CP')} |")
a(f"| 6 | Les Colonnes | Trois colonnes de quartz sur la ligne droite et trois anneaux de vol à traverser (nord, sud, nord) ; la herse de la tour de la plateforme s'ouvre au troisième. | CP→CI : {vol('CP>CI')} |")
a(f"| 7 | La Citadelle haute | Un levier dans l'aile ouest fait céder la herse de la crypte (aile est) ; le jeu des lampes de la crypte donne la **Clé des Nuées**. Porte de la tour à clé (en main, devant la porte). Balcon à {w(P['CI_balcon'])}. | CI→BE : {vol('CI>BE')} |")
a(f"| 8 | Le Belvédère | Dernière tour, sans herse : on monte, on se lance. Balcon à {w(P['BE_balcon'])}. | BE→BO : {vol('BE>BO')} |")
a(f"| 9 | Le Cœur des Nuées | Arène à ciel ouvert sur la grande île ({w(conf['boss']['boss_pos'])}) : place de quartz, douze colonnes, estrade. Sortie scellée. | — |")
a("")
a("Un îlot caché (HA, entre AI et G1) et un autre (HB, entre MV et RL) sont des détours facultatifs : on y va depuis la tour d'en face, on en repart du toit de la cabane par l'échelle.")
a("")
a("## Les énigmes")
a("")
a("Quatre énigmes s'ajoutent aux herses et aux leviers du parcours. Les états sont communs à tous les joueurs du monde (comme les herses) ; `/function atelier:ciel_reset` remet tout à zéro.")
a("")
ordre = conf["ordre_glyphes"]
bg = conf["boutons_glyphes"]
a(f"1. **Les glyphes de l'Aiguille.** Sur la place du Débarcadère, une stèle du ciel (panneau à {w([conf['points']['D'][0] + 6, conf['points']['D'][1] + 1, conf['points']['D'][2] - 6])}) donne l'ordre : **{', '.join(ordre)}**. "
  f"Au pied de la tour de l'Aiguille, quatre boutons de pierre portent ces noms ({', '.join(f'{k} {w(v)}' for k, v in bg.items())}) : il faut les presser dans cet ordre pour ouvrir la porte de la tour. Une erreur remet la série à zéro.")
a("")
an = conf["anneaux"]
a(f"2. **Les anneaux de vol (Colonnes).** Trois anneaux de lueur dorée sur la ligne de vol du Relais vers la plateforme, dont les centres sont {', '.join(w([x, y, z]) for (x, z, y) in an)}. "
  "Cadres de blocs dorés de 11 × 11, ouverture de 9 × 9 : assez large pour qu'un vol à plat passe les trois sans piquer. Les colonnes de quartz sont rangées de chaque côté de la ligne de vol, pas dessus. "
  "On les traverse en planant, dans cet ordre ; chacun traversé devient vert. Le troisième ouvre la herse de la tour de la plateforme. "
  f"Si on rate un anneau, on atterrit quand même sur la plateforme : le levier de rappel ({w(conf['levier_rappel'])}) ramène au Relais et remet les anneaux à zéro.")
a("")
sl = conf["sequence_lampes"]
couleurs = ["rouge", "bleu", "vert clair", "jaune", "violet"]
a(f"3. **Les lampes de la crypte (Citadelle).** Cinq lampes sur le mur du fond, avec une bande de couleur dessous (de haut en bas, du nord au sud : {', '.join(couleurs)}) et un bouton chacune. "
  f"Le bouton « start » ({w(conf['start_lampes'])}, mur nord) fait s'allumer les lampes une à une : **{', '.join(couleurs[k - 1] for k in sl)}**. "
  "Pendant le spectacle, les boutons sont sans effet ; ensuite on presse les boutons dans le même ordre. Une erreur remet à zéro (on relance avec « start »). Réussir donne la **Clé des Nuées**.")
a("")
ec = conf["eclats"]
a("4. **Les trois éclats.** Un levier donne un éclat d'améthyste nommé (une seule fois par joueur : chacun prend les siens) : " + ", ".join(f"{e['nom']} ({e['ile']}, {w(e['levier'])})" for e in ec.values())
  + f". Les trois ensemble dans l'inventaire d'un joueur proche de l'autel de l'arène ({w(conf['autel'])}) sont consommés et **ouvrent la sortie du boss**. "
  f"Si on arrive dans l'arène sans eux, un **levier de retour** ({w(conf['levier_retour'])}, panneau « Il manque des éclats ? ») ramène aussitôt les joueurs proches au Débarcadère pour refaire le trajet (les herses déjà ouvertes le restent) ; "
  "il retire aussi les éclats et les étiquettes de ceux qui le tirent, pour qu'ils puissent les reprendre.")
a("")
a("Ces énigmes sont vérifiées pour l'accès (boutons, leviers, autel atteignables, anneaux traversables par un vol réaliste) ; leur logique de blocs de commande n'a pas été essayée en jeu.")
a("")
a("## La ville dans les airs (décoration)")
a("")
a("Chaque tour est un lieu de vie avec son rôle, ses couleurs et son bois :")
a("")
a("| Tour | Étages (du bas vers le haut), puis terrasse | Bois et couleurs |")
a("|---|---|---|")
a("| L'Aiguille (AI) | bibliothèque, salle des cartes, observatoire (télescope, étoiles), terrasse | épicéa, bleu |")
a("| Maisons de G1, G2, G3 | salon / chambre ; chambre / atelier ; atelier / salon | bouleau jaune, cerisier rose, chêne vert |")
a("| Le Moulin (MV) | salle de la meule, marché, grenier | chêne noir, brun et jaune |")
a("| Le Relais (RL) | taverne (comptoir, tables), chambres de voyageurs | acajou, rouge |")
a("| La gare des Colonnes (CP) | gare (bancs), marché, salle des cartes | acacia, orange |")
a("| La Citadelle (CI) | grand hall, salle du conseil (table longue), chapelle, bibliothèque | chêne noir, violet et gris |")
a("| L'Orangerie du Belvédère (BE) | jardin, salon de thé, jardin haut | bouleau, vert clair et cyan |")
a("")
a("Chaque niveau a en plus un tapis, des poutres et un lustre, et des fenêtres à jardinière (sauf au rez-de-chaussée, où la jardinière gênerait le tour de la tour). "
  "Devant chaque porte : un store rayé, une enseigne (« Observatoire », « Taverne du Relais »...), des lanternes et des pots. Les balcons ont des créneaux, des bancs, des jardinières, "
  "une lanterne à chaque coin et un fanion. Rien n'est posé sur le côté de l'escalier, celui d'où l'on arrive, ni devant les entrées et sorties : le vérificateur le contrôle.")
a("")
a("Sur les grandes îles (Débarcadère, Aiguille, Moulin, Citadelle, Belvédère, arène), des maisons à toit de pente avec cheminée et intérieur meublé, des étals de marché à auvent rayé, des arbres, "
  "des bancs et des puits. Des fleurs poussent dans l'herbe de toutes les îles. L'Aiguille, le Moulin et le Belvédère ont été agrandies (rayons 17, 17 et 15) pour faire de la place.")
a("")
a("## Solutions")
a("")
a("1. Débarcadère : sauter dans le vide, ouvrir la paravoile (Espace) et viser P1.")
a("2. Pierres : P1, P2, P3 (en piquant un peu pour atteindre P2, qui est la plus longue).")
a("3. Aiguille : presser les glyphes dans l'ordre de la stèle du ciel, puis tirer le levier de chaque niveau (mur d'en face de l'escalier), monter au balcon, plonger vers G1.")
a("4. Girouettes : lever au sommet de chaque tour de G1, G2, G3 (monter au balcon, levier sur le mât) ; puis le moulin s'ouvre.")
a("5. Moulin : monter au balcon, plonger vers le Relais, puis du Relais vers la plateforme des Colonnes en traversant les trois anneaux ; monter dans la tour de la plateforme.")
a("6. Citadelle : levier de l'aile ouest, puis le jeu des lampes dans la crypte (la clé arrive en main), puis la clé en main devant la porte de la tour ; monter au balcon.")
a("7. Belvédère : monter au balcon et plonger vers l'arène.")
a("")
a("## Coordonnées")
a("")
a("| Île | Sol (pieds) | Rayon | Hauteur du balcon |")
a("|---|---|---|---|")
for k in conf["ordre"] + ["HA", "HB"]:
    i = conf["iles"][k]
    bal = P.get(f"{k}_balcon")
    a(f"| {k} | {w(P[k])} | {i['r'] if not i['rect'] else 'rect ' + str(i['rect'])} | {w(bal) if bal else '—'} |")
a("")
a("## Boss")
a("")
b = conf["boss"]
a(f"Position du boss (centre de l'estrade) : `{w(b['boss_pos'])}`. La sortie est une herse de 3 × 3 devant la chambre du trésor ({w(b['boss_sortie'])}) : `/function atelier:ciel_ouvrir_sortie_boss` l'ouvre (à relier à la mort du boss), `/function atelier:ciel_fermer_sortie_boss` la referme. Emplacement de coffre dans la chambre : `{w(b['boss_coffre'])}`. Aucun monstre ni boss n'est posé.")
a("")
a("## Salles cachées (5)")
a("")
a("| # | Accès | Emplacement du coffre | Emplacement des monstres |")
a("|---|---|---|---|")
for k, c in conf["cachees"].items():
    a(f"| {k} | {c['acces']} ; bouton {w(c['bouton'])} | {w(c['coffre'])} | {w(c['mobs'])} |")
for h in ("HA", "HB"):
    c = conf["cabanes"][h]
    a(f"| {h} | îlot isolé : vol depuis la tour voisine, cabane de 9 × 9 × 5 (échelle jusqu'au toit) | {w(c['coffre'])} | {w(c['mobs'])} |")
a("")
a("Chaque salle fait 9 × 9 × 4 ou 5, éclairée. Trois sont sous le sol de l'île (D, AI, CI) : une dalle de 3 cases au niveau du sol s'efface quand on appuie sur le bouton de pierre posé au pied d'un petit bloc de mur ; l'escalier descend dessous.")
a("")
a("## Contrôles")
a("")
a(f"- Générateur : 0 erreur (aucun bloc interdit, aucun levier ni bouton ni panneau ni échelle sans appui, panneaux d'au plus 14 caractères par ligne, aucun bloc sous la ligne de chute, chaînes de blocs de commande toutes dans la roche, toutes les cases intérieures éclairées à 5 au moins).")
a(f"- `verif_donjon_ciel.py` : {'OK' if ver['ok'] else 'ERREURS'}. {len(ver['etapes'])} étapes : {', '.join(str(e['ouvertes'][-1]) if e['ouvertes'] else '—' for e in ver['etapes'])}.")
a("  - Avec le vol réaliste, tout ce qui est prévu s'atteint, la chambre du boss reste fermée tant que la sortie l'est.")
a("  - À chaque étape, le vol généreux n'atteint rien de ce que le réaliste n'atteint pas encore ; aucune île n'est sautée.")
a("  - Sans paravoile (toutes portes ouvertes), on ne quitte pas le Débarcadère.")
a("  - Aucune impasse : de chaque île atteinte, on rejoint l'arène du boss.")
a("- Pas de test en jeu : ni la physique du vol, ni les blocs de commande, ni les tours n'ont été essayés ; les distances sont à confirmer par un vrai essai.")
a("")
a("## Retour du vide")
a("")
a("Chaque île a un numéro, mémorisé par joueur dans le tableau `ciel` quand on se tient dessus. Sous Y local 14, les blocs de commande téléportent au point de passage de la dernière île visitée (au sol, hors des tours). La chute ne fait pas de dégâts. Le point de réapparition du joueur suit aussi les îles : en arrivant sur une île, il est reporté à son point de passage, donc une mort (ou une déconnexion suivie d'une mort) ramène à la dernière île visitée.")
a("")
a("## Écarts avec le plan")
a("")
a("- **Trois îlots**, pas cinq, dans les Pierres suspendues : en ligne, plus serrés, un vol de P(k) vers P(k+2) deviendrait possible (raccourci).")
a("- **Les girouettes se font dans l'ordre G1, G2, G3**, pas dans n'importe quel ordre : on ne remonte jamais, donc on ne peut pas revenir en arrière d'un îlot à l'autre.")
a("- **La grande traversée** n'est pas un vide de 90 blocs coupé par un îlot au milieu : c'est MV→RL (54 blocs) puis RL→CP (51), avec le Relais comme tour de recharge.")
a("- **Cinq salles cachées** (trois sous l'île, deux îlots), pas six.")
a("- **La Citadelle** : la clé s'obtient au jeu des lampes de la crypte, pas dans des salles à fouiller.")
a("- **Les éclats** ne sont pas sur le chemin obligatoire : Pierres (P2), Relais (RL) et Belvédère (BE) se visitent de toute façon, mais il faut penser à tirer les trois leviers (rien ne force à le faire avant l'arène) ; la sortie du boss reste scellée sans eux.")
long_ = max(V.values(), key=lambda v: v["L"])
a(f"- **Le vol réaliste** accepte jusqu'à 60 blocs de trajet ; le plus long vol prévu fait {long_['L']} blocs ({long_['L'] / 7.2:.1f} s sur 10 s).")
a("- La voile n'est pas visible à la première personne.")
a("- Le mod passe en **2.6.1** : la voile n'est plus rangée dès qu'on effleure un bloc, il faut rester au sol pendant 3 ticks de suite (avant, un seul tick suffisait, côté serveur comme côté joueur).")
(RACINE / "docs/donjon_ciel.md").write_text("\n".join(L) + "\n", encoding="utf-8")
print("ok :", len(L), "lignes")
