# [claude-build]
"""suspendu_ss2_blocs.py - Donjon Suspendu, sous-sol 2 (salle -2) : la construction (les blocs) d'apres le plan de suspendu_ss2.py. Pur calcul : renvoie les blocs et les reperes, sans ecrire de fichier.
Hauteurs (Y du gabarit) : YF2 = le sol des iles, 47 sous celui du rez-de-chaussee ; les iles, les plateformes des piliers, l'entree et le couloir sont a YF2 ; la fosse descend jusqu'au socle, 45 plus bas
(tomber tue) et son fond est un banc de roche noire creuse de rivieres de lave ; plafond de la salle a YF2 + 12, orne de stalactites et de lianes (suspendu_ss2_deco). Les ancrages cyan ne sont PAS dans la structure : une fonction de datapack les pose quand on tire leur levier, et avec chacun de ceux qui sont au dessus du vide, un pilier (`piliers_cyan`)."""
import math
from collections import deque
from functools import lru_cache

import suspendu_salle1 as rdc
import suspendu_ss1_blocs as ss1
import suspendu_ss2_deco as deco
from suspendu_outils import AIR, S, h01
from suspendu_ss2 import (ANCRAGE_BLEU_ILE, COFFRE_CLE_3, COFFRES, COULOIR, CYAN, ENNEMIS, ILES, LARGEUR_COULOIR, LARGEUR_CELLULES, LEVIERS, MONTAGNES, PILIERS, PONT, PROFONDEUR_CELLULES, RIVIERES,
                         X0_SALLE, Z0_SALLE, vers_cellule, vers_local, vers_px)

YF2 = ss1.YF - 19                        # 47 sous le rez-de-chaussee (28 avant : on a remonte la salle de 9 pour que la fosse puisse etre plus profonde)
Y_FOND = -7                              # le bloc de sol au fond de la fosse : juste au dessus du socle (le socle est en Y -8 du gabarit), le plus profond possible
PROF_ABIME = YF2 - Y_FOND                # 45 de chute
Z_ARRIVEE = rdc.DZ + rdc.ESCALIERS["c2"] + rdc.NB_MARCHES["c2"]     # la case qui suit la derniere marche de l'escalier de la porte 2 : le couloir commence la
HAUT_SALLE2 = 12                         # air jusqu'a YF2 + 12
HAUT_COULOIR2 = 8
HAUT_ANCRAGE_PILIER = 4                  # l'ancrage d'un pilier : 4 au dessus de sa plateforme
HAUT_ANCRAGE_CYAN = 3                    # l'ancrage d'un levier : 3 au dessus du sol de l'ile visee, comme ceux de la corniche et des fosses du sous-sol 1
LUM_NIVEAU, LUM_PAS = ss1.LUM_NIVEAU, ss1.LUM_PAS
PAS_LANTERNE = 5
NATUREL = {"abime", "ile", "pilier", "pont", "massif", "grotte"}


def dans_ellipse(px, py, e):
    (c0, c1), rx, ry = e
    return ((px - c0) / rx) ** 2 + ((py - c1) / ry) ** 2 <= 1


def region(px, py, ile):
    if "ellipse" in ile:
        return dans_ellipse(px, py, ile["ellipse"])
    return rdc.dans_polygone(px, py, ile["polygone"])


def sol_genre(genre, x, z):
    if genre == "couloir":
        return rdc.sol(x, z)
    if genre == "abime":
        return deco.fond_roche(x, z)
    if genre == "pilier":
        return ss1.roche(x, 0, z)
    return deco.sol_ile(x, z)                                                   # iles, pont (dessous), massifs et grottes : de la roche avec des plaques de mousse


def mur_genre(genre, x, y, z):
    return deco.roche_mur(x, y, z, YF2) if genre in NATUREL else rdc.mur(x, y, z)


def cellule_de(sp):
    return vers_cellule(*sp)


@lru_cache(maxsize=None)
def construire():
    """Renvoie (blocs, infos) de la salle -2 : blocs {(x, y, z): etat} dans le repere du gabarit (l'air est ecrit explicitement), infos : reperes."""
    cases = {}
    for x in range(X0_SALLE, X0_SALLE + LARGEUR_CELLULES):
        for z in range(Z0_SALLE, Z0_SALLE + PROFONDEUR_CELLULES):
            cases[(x, z)] = [Y_FOND, YF2 + HAUT_SALLE2, "abime"]
    ile_de = {}                                                                 # la cellule -> le nom de son ile
    for c in list(cases):
        px, py = vers_px(*c)
        for nom, ile in ILES.items():
            if region(px, py, ile):
                cases[c] = [YF2, YF2 + HAUT_SALLE2, "ile"]
                ile_de[c] = nom
                break
        else:
            ligne, largeur = PONT
            if min(rdc.dist_segment(px, py, a, b) for a, b in zip(ligne, ligne[1:])) <= largeur / 2:
                cases[c] = [YF2, YF2 + HAUT_SALLE2, "pont"]                         # un pont de bois au dessus du vide (et non une chaussee de roche) : voir plus bas
                ile_de[c] = "PONT"
    # les montagnes (les iles des leviers 2 et 5) : une bordure plate, un massif plein jusqu'au plafond, et une grotte creusee au coeur (chambre en dome et tunnel jusqu'a la bordure)
    montagnes = {}
    for nom, m in MONTAGNES.items():
        cellules = {c for c, n in ile_de.items() if n == nom}
        dist, file_ = {}, deque()
        for c in cellules:
            if any((c[0] + dx, c[1] + dz) not in cellules for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                dist[c] = 1
                file_.append(c)
        while file_:
            c = file_.popleft()
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                n = (c[0] + dx, c[1] + dz)
                if n in cellules and n not in dist:
                    dist[n] = dist[c] + 1
                    file_.append(n)
        noyau = {c for c in cellules if dist[c] > m["anneau"]}
        cibles = [cellule_de(LEVIERS[m["levier"]])] + [cellule_de(COFFRES[i]) for i in m["coffres"]]
        grotte = deco.creuser_grotte(nom, m, noyau, dist, vers_local, cibles)
        for c in noyau:
            cases[c] = [YF2, YF2, "massif"]                                     # plein jusqu'au plafond : pas d'air au dessus du sol
        for c, (f, h) in grotte["cases"].items():
            cases[c] = [YF2 + f - (1 if c in grotte["lave"] else 0), YF2 + h, "grotte"]     # le ruisseau de lave est creuse un bloc plus bas que le sol de la grotte
        montagnes[nom] = {"noyau": sorted(noyau), "chambre": sorted(grotte["chambre"]), "tunnel": sorted(grotte["tunnel"]), "lave": sorted(grotte["lave"]), "entree": grotte["entree"], "dist": dist}
    pilier_cases = []
    for k, sp in enumerate(PILIERS):                                            # les piliers : une plateforme 5 x 5 sans les coins, au niveau des iles, au dessus du vide
        cx, cz = cellule_de(sp)
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                c = (cx + dx, cz + dz)
                if abs(dx) == 2 and abs(dz) == 2:
                    continue
                if c in cases and cases[c][2] == "abime":
                    cases[c] = [YF2, YF2 + HAUT_SALLE2, "pilier"]
        pilier_cases.append((cx, cz))
    # le couloir : de la fin de l'escalier de la porte 2, vers l'est le long du sous-sol 1, puis vers le nord jusqu'a l'entree (rien dans la roche de l'escalier : z <= 105 a l'ouest)
    couloir = set()
    for x in range(6, X0_SALLE + 1):
        for z in range(60, 125):
            if (x, z) in cases or (x < 30 and z < Z_ARRIVEE):
                continue
            if min(rdc.dist_segment(x + 0.5, z + 0.5, a, b) for a, b in zip(COULOIR, COULOIR[1:])) <= LARGEUR_COULOIR / 2:
                cases[(x, z)] = [YF2, YF2 + HAUT_COULOIR2, "couloir"]
                couloir.add((x, z))
    lave_grotte = {c for m in montagnes.values() for c in m["lave"]}
    air = set()
    for (cx, cz), (sol, haut, genre) in cases.items():
        for y in range(sol + 1, haut + 1):
            air.add((cx, y, cz))
        if genre == "pont":                                                     # sous le tablier, le vide de l'abime
            for y in range(Y_FOND + 1, sol):
                air.add((cx, y, cz))
    blocs = {v: AIR for v in air}
    for (cx, cz), (sol, haut, genre) in cases.items():
        blocs[(cx, sol, cz)] = sol_genre(genre, cx, cz)
        if genre == "grotte" and (cx, cz) in lave_grotte:
            blocs[(cx, sol, cz)] = S("lava", level=0)
        if genre == "pont":
            blocs[(cx, Y_FOND, cz)] = deco.fond_roche(cx, cz)                   # le fond de la fosse, sous le pont
        blocs[(cx, haut + 1, cz)] = mur_genre(genre, cx, haut + 1, cz)
    for (x, y, z) in air:                                                       # les parois : tout voisin d'un voxel d'air qui n'en est pas un
        genre = cases[(x, z)][2]
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + dx, y + dy, z + dz)
            if n in air or n in blocs or (n[0] < 30 and n[2] < Z_ARRIVEE):                    # le tunnel de l'escalier de la porte 2 (x < 30, avant Z_ARRIVEE) vient du rez-de-chaussee : on n'y ecrit rien
                continue
            colonne = cases.get((n[0], n[2]))
            if colonne and colonne[2] == "pilier" and n[1] < colonne[0]:       # le flanc d'un pilier : briques de pierre, une bande sculptee tous les 4 blocs
                blocs[n] = S("chiseled_stone_bricks") if (colonne[0] - n[1]) % 4 == 0 else (S("cracked_stone_bricks") if h01(n[0], n[1], n[2], 7) < 0.12 else S("stone_bricks"))
                continue
            blocs[n] = mur_genre(genre, n[0], n[1], n[2])
    # les ancrages toujours actifs : les piliers et un point sur l'ile du levier 1, accroches au plafond par une chaine
    ancrages_bleus = []
    for (cx, cz) in pilier_cases:
        ancrages_bleus.append((cx, YF2 + HAUT_ANCRAGE_PILIER, cz))
    bx, bz = cellule_de(ANCRAGE_BLEU_ILE)
    ancrages_bleus.append((bx, YF2 + HAUT_ANCRAGE_PILIER, bz))
    for (x, y, z) in ancrages_bleus:
        for yy in range(y + 1, cases[(x, z)][1] + 1):
            blocs[(x, yy, z)] = S("chain", axis="y", waterlogged=False)
        blocs[(x, y, z)] = S("grappin:ancrage")
    # les ancrages cyan : pas dans la structure (invisibles jusqu'au levier) ; on en garde les reperes
    ancrages_cyan = {k: [(vers_cellule(*sp)[0], YF2 + HAUT_ANCRAGE_CYAN, vers_cellule(*sp)[1]) for sp in liste] for k, liste in CYAN.items()}
    # un ancrage cyan au dessus du vide (sans sol dessous) apparait avec un pilier, de la meme forme que les piliers bleus (5 x 5 sans les coins) : du fond de l'abime au niveau des iles, avec sa lumiere.
    # La structure ne contient rien de tout cela (l'abime reste vide jusqu'au levier) : `dessus` donne le bloc du dessus de chaque case, comme celui des piliers bleus.
    piliers_cyan = {}
    for k, liste in ancrages_cyan.items():
        for (x, y, z) in liste:
            if cases[(x, z)][2] != "abime":
                continue
            empreinte = sorted((x + dx, z + dz) for dx in range(-2, 3) for dz in range(-2, 3)
                               if not (abs(dx) == 2 and abs(dz) == 2) and cases.get((x + dx, z + dz), (0, 0, ""))[2] == "abime")
            piliers_cyan.setdefault(k, []).append({"ancrage": (x, y, z), "cases": empreinte, "dessus": [(cx, cz, ss1.roche(cx, 0, cz)[0]) for (cx, cz) in empreinte], "lumiere": (x, YF2 + 2, z)})
    # le fond de la fosse : un banc de roche noire (un bloc au dessus du socle) creuse de rivieres de lave qui serpentent ; la lave est posee sur le socle, entouree de roche : elle ne coule pas
    cases_fond = {c for c, v in cases.items() if v[2] in ("abime", "pont")}
    interdit = {(x + dx, z + dz) for liste in piliers_cyan.values() for p in liste for (x, _, z) in [p["ancrage"]] for dx in range(-4, 5) for dz in range(-4, 5)}    # jamais sous un pilier qui apparait
    lave_fond = set()
    for k, (chemin, largeur) in enumerate(RIVIERES):
        lave_fond |= deco.cases_riviere(deco.trace_riviere([vers_local(*q) for q in chemin], largeur, k * 1.7 + 0.5), cases_fond - interdit)
    for c in cases_fond:
        if c in lave_fond:
            blocs[(c[0], Y_FOND, c[1])] = S("lava", level=0)
        else:
            blocs[(c[0], Y_FOND + 1, c[1])] = deco.fond_roche(c[0], c[1])
    for c in cases_fond - lave_fond:                                            # les berges de la lave : du magma
        if any((c[0] + dx, c[1] + dz) in lave_fond for dx, dz in deco.VOISINS) and h01(c[0], 0, c[1], 61) < 0.5:
            blocs[(c[0], Y_FOND + 1, c[1])] = S("magma_block")
    # les leviers (au sol), les coffres jaunes (vides)
    leviers = {}
    for k, sp in LEVIERS.items():
        x, z = cellule_de(sp)
        leviers[k] = (x, cases[(x, z)][0] + 1, z)
        blocs[leviers[k]] = S("lever", face="floor", facing="north", powered=False)
    coffres = []
    for sp in COFFRES:
        x, z = cellule_de(sp)
        coffres.append((x, cases[(x, z)][0] + 1, z))
        blocs[coffres[-1]] = S("chest", facing="south", type="single", waterlogged=False)
    cx_k, cz_k = cellule_de(COFFRE_CLE_3)
    coffre_cle = (cx_k, cases[(cx_k, cz_k)][0] + 1, cz_k)
    ennemis = {nom: [(vers_cellule(*sp)[0], cases[vers_cellule(*sp)][0] + 1, vers_cellule(*sp)[1]) for sp in liste] for nom, liste in ENNEMIS.items()}
    reserves = {(p[0], p[2]) for p in [*leviers.values(), *coffres, coffre_cle]}
    # le pont delabre (de l'ile du levier 4 a l'ile du coffre, au dessus du vide) : un tablier de planches de trois bois, avec des planches manquantes (jamais deux cote a cote : on les enjambe), des poutres
    # dessous (aux endroits ou le tablier est entier), des pieux casses qui pendent, une barriere de bois a moitie arrachee sur les bords et quelques lanternes posees sur les poteaux
    pont = sorted(c for c, n in ile_de.items() if n == "PONT")
    occupe = reserves | {(m[0], m[2]) for liste in ennemis.values() for m in liste}
    trous = set()
    for (x, z) in pont:
        entier = all(cases.get((x + dx, z + dz), (0, 0, ""))[2] == "pont" for dx in (-1, 0, 1) for dz in (-1, 0, 1))      # ni sur un bord, ni a la jonction avec une ile
        if entier and (x, z) not in occupe and h01(x, 0, z, 11) < 0.09 and not any((x + dx, z + dz) in trous for dx in (-1, 0, 1) for dz in (-1, 0, 1)):
            trous.add((x, z))
    for (x, z) in pont:
        r = h01(x, 1, z, 12)
        if (x, z) in trous:
            blocs[(x, YF2, z)] = AIR
        else:
            blocs[(x, YF2, z)] = (S("oak_planks") if r < 0.5 else S("spruce_planks") if r < 0.8 else S("dark_oak_planks") if r < 0.95 else S("stripped_oak_log", axis="x"))
    poutres = {(x, z) for (x, z) in pont if x % 5 == 0 and not any((x, z + dz) in trous for dz in (-1, 0, 1))}
    for (x, z) in poutres:
        blocs[(x, YF2 - 1, z)] = S("stripped_spruce_log", axis="z")
    for (x, z) in poutres:                                                      # des pieux casses : ils pendent sous les poutres du bord et s'arretent en l'air
        if any(cases.get((x + dx, z + dz), (0, 0, ""))[2] == "abime" for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            bas = YF2 - 2 - (3 + int(h01(x, 2, z, 14) * 10))
            for y in range(YF2 - 2, max(bas, Y_FOND), -1):
                blocs[(x, y, z)] = S("oak_log", axis="y")
    rampe = {(x, z) for (x, z) in pont if (x, z) not in occupe and h01(x, 3, z, 13) < 0.72
             and any(cases.get((x + dx, z + dz), (0, 0, ""))[2] == "abime" for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    for (x, z) in rampe:
        cotes = {nom: ("true" if (x + dx, z + dz) in rampe else "false") for nom, (dx, dz) in (("east", (1, 0)), ("west", (-1, 0)), ("south", (0, 1)), ("north", (0, -1)))}
        blocs[(x, YF2 + 1, z)] = S("oak_fence", waterlogged=False, **cotes)
    lanternes_pont = []
    for (x, z) in sorted(rampe):
        if h01(x, 4, z, 15) < 0.2 and not any(abs(x - p[0]) + abs(z - p[1]) <= 6 for p in lanternes_pont):
            blocs[(x, YF2 + 2, z)] = S("lantern", hanging=False, waterlogged=False)
            lanternes_pont.append((x, z))
    # la lumiere : des blocs de lumiere invisibles sur les iles et les piliers (un par groupe de cases a moins de LUM_PAS), des lanternes suspendues dans le couloir
    lumieres, sources = [], []
    sources += [(x, YF2 + 2, z) for (x, z) in lanternes_pont]
    for nom, m in montagnes.items():                                            # les grottes : des lanternes suspendues au plafond du dome, loin les unes des autres
        posees_g = []
        for c in sorted(m["chambre"], key=lambda q: (-cases[q][1], q)):
            if all(abs(c[0] - p[0]) + abs(c[1] - p[1]) > 7 for p in posees_g):
                posees_g.append(c)
        for (x, z) in posees_g:
            blocs[(x, cases[(x, z)][1], z)] = S("lantern", hanging=True, waterlogged=False)
            sources.append((x, cases[(x, z)][1], z))
    libres = sorted(c for c, (s, h, g) in cases.items() if g in ("ile", "pilier", "pont", "grotte") and c not in reserves)
    posees = []
    while True:
        sombres = [c for c in libres if not any(abs(c[0] - p[0]) + abs(c[1] - p[1]) <= LUM_PAS for p in posees)]
        if not sombres:
            break
        posees.append(max(sombres, key=lambda q: (min([abs(q[0] - p[0]) + abs(q[1] - p[1]) for p in posees] + [99]), q)))
    for (x, z) in posees:
        y = cases[(x, z)][0] + 2
        if blocs.get((x, y, z)) == AIR:
            blocs[(x, y, z)] = S("light", level=LUM_NIVEAU, waterlogged=False)
            lumieres.append((x, y, z))
            sources.append((x, y, z, LUM_NIVEAU))
    lanternes = []
    a_couvrir = sorted(couloir)
    while True:
        sombres = [c for c in a_couvrir if not any(abs(c[0] - p[0]) + abs(c[1] - p[1]) <= PAS_LANTERNE for p in lanternes)]
        if not sombres:
            break
        lanternes.append(max(sombres, key=lambda q: (min([abs(q[0] - p[0]) + abs(q[1] - p[1]) for p in lanternes] + [99]), q)))
    for (x, z) in lanternes:
        ly = YF2 + 5
        for y in range(ly + 1, YF2 + HAUT_COULOIR2 + 1):
            blocs[(x, y, z)] = S("chain", axis="y", waterlogged=False)
        blocs[(x, ly, z)] = S("lantern", hanging=True, waterlogged=False)
        sources.append((x, ly, z))
    # la decoration : stalactites sous la voute, stalagmites au fond de la fosse, lianes a baies lumineuses, et dans les grottes des stalactites, des stalagmites au bord, des lianes et des cristaux d'amethyste
    proteges = set()                                                            # colonnes ou l'on ne pose rien : chaines des ancrages, leviers, coffres, ennemis
    for (x, y, z) in [*ancrages_bleus, *(a for liste in ancrages_cyan.values() for a in liste), *leviers.values(), *coffres, coffre_cle, *(m for liste in ennemis.values() for m in liste)]:
        proteges |= {(x + dx, z + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1)}
    proteges |= interdit
    deco_nb = {"stalactites": 0, "stalagmites": 0, "lianes": 0, "cristaux": 0}
    baies = []

    def libre(cellules):
        return all(blocs.get(q) == AIR for q in cellules)

    for (x, z), (sol, haut, genre) in sorted(cases.items()):
        if (x, z) in proteges or genre not in ("abime", "pont", "ile", "pilier"):
            continue
        grappe = deco.bruit(x, z, 9, 41)
        if h01(x, 3, z, 42) < 0.002 + 0.045 * grappe ** 2:                      # une stalactite (au plus 5 de long : sa pointe reste a plus de 7 au dessus des iles, hors de portee du crochet)
            n = min(5, 1 + int(h01(x, 4, z, 43) * (1 + 4 * grappe)))
            if libre([(x, haut - i, z) for i in range(n)]):
                deco.stalactite(blocs, x, haut, z, n)
                deco_nb["stalactites"] += 1
                continue
        if h01(x, 5, z, 44) < 0.003 + 0.03 * deco.bruit(x, z, 11, 45) ** 2:     # une liane a baies
            n = 2 + int(h01(x, 6, z, 46) * 4)
            if libre([(x, haut - i, z) for i in range(n)]):
                baies += deco.liane(blocs, x, haut, z, n, h01(x, 7, z, 47) < 0.6)
                deco_nb["lianes"] += 1
    for c in sorted(cases_fond - lave_fond):                                    # les stalagmites du fond : des colonnes hautes, en groupes
        if c in proteges or blocs.get((c[0], Y_FOND + 1, c[1]), AIR)[0] in ("minecraft:air", "minecraft:magma_block"):
            continue
        g2 = deco.bruit(c[0], c[1], 8, 51)
        if h01(c[0], 3, c[1], 52) < 0.004 + 0.12 * g2 ** 2:
            n = 2 + int(h01(c[0], 4, c[1], 53) * (2 + 14 * g2))
            if libre([(c[0], Y_FOND + 2 + i, c[1]) for i in range(n)]):
                deco.stalagmite(blocs, c[0], Y_FOND + 2, c[1], n)
                deco_nb["stalagmites"] += 1
    for nom, m in montagnes.items():
        cave = set(m["chambre"]) | set(m["tunnel"])
        proteges_g = set()
        for (x, y, z) in [leviers[MONTAGNES[nom]["levier"]], *(coffres[i] for i in MONTAGNES[nom]["coffres"])]:
            proteges_g |= {(x + dx, z + dz) for dx in range(-2, 3) for dz in range(-2, 3)}
        ex, ez = m["entree"]
        proteges_g |= {(ex + dx, ez + dz) for dx in range(-2, 3) for dz in range(-2, 3)}
        lave_cases = set(m["lave"])
        cristaux = 0
        for (x, z) in sorted(cave):
            sol, haut, _ = cases[(x, z)]
            clair = haut - sol                                                  # blocs d'air au dessus du sol
            if (x, z) in lave_cases:
                sources.append((x, sol, z))                                     # la lave eclaire
                continue
            if (x, z) in proteges_g:
                continue
            if h01(x, 5, z, 71) < 0.1 and clair >= 5:                           # des stalactites : il reste toujours 3 blocs de libres au dessus du sol
                n = min(1 + int(h01(x, 6, z, 72) * 3), clair - 3)
                if n >= 1 and libre([(x, haut - i, z) for i in range(n)]):
                    deco.stalactite(blocs, x, haut, z, n)
                    deco_nb["stalactites"] += 1
                    continue
            if h01(x, 7, z, 73) < 0.04 and clair >= 6:                          # des lianes
                n = min(2 + int(h01(x, 8, z, 74) * 3), clair - 3)
                if libre([(x, haut - i, z) for i in range(n)]):
                    baies += deco.liane(blocs, x, haut, z, n, True)
                    deco_nb["lianes"] += 1
                    continue
            contre_mur = [(dx, dz) for dx, dz in deco.VOISINS if (x + dx, z + dz) not in cave]
            if contre_mur and (x, z) not in m["tunnel"]:
                if h01(x, 9, z, 75) < 0.16 and libre([(x, sol + 1 + i, z) for i in range(3)]):       # des stalagmites contre la paroi
                    deco.stalagmite(blocs, x, sol + 1, z, 1 + int(h01(x, 10, z, 76) * 3))
                    deco_nb["stalagmites"] += 1
                elif h01(x, 11, z, 77) < 0.2 and cristaux < 18:                  # des cristaux d'amethyste fixes a la paroi
                    dx, dz = contre_mur[0]
                    y = sol + 1 + int(h01(x, 12, z, 78) * 2)
                    mur = (x + dx, y, z + dz)
                    if blocs.get((x, y, z)) == AIR and blocs.get(mur) != AIR:
                        face = {(1, 0): "west", (-1, 0): "east", (0, 1): "north", (0, -1): "south"}[(dx, dz)]
                        blocs[(x, y, z)] = S("amethyst_cluster", facing=face, waterlogged=False)
                        blocs[mur] = S("amethyst_block")
                        sources.append((x, y, z, 5))
                        cristaux += 1
                        deco_nb["cristaux"] += 1
    sources += [(x, y, z, 14) for (x, y, z) in baies]                           # les baies lumineuses eclairent
    iles = {}
    for nom, ile in ILES.items():
        cellules = [c for c, n in ile_de.items() if n == nom]
        if cellules:
            mx = sum(c[0] for c in cellules) / len(cellules)
            mz = sum(c[1] for c in cellules) / len(cellules)
            proche = min(cellules, key=lambda c: (c[0] - mx) ** 2 + (c[1] - mz) ** 2)
            iles[nom] = {"centre": (proche[0], YF2 + 1, proche[1]), "cases": len(cellules)}
    iles["PONT"] = {"centre": next(((c[0], YF2 + 1, c[1]) for c, n in ile_de.items() if n == "PONT"), None), "cases": sum(1 for n in ile_de.values() if n == "PONT")}
    infos = {"cases": {c: tuple(v) for c, v in cases.items()}, "ile_de": ile_de, "iles": iles, "couloir": sorted(couloir), "pilier_cases": pilier_cases, "ancrages_bleus": ancrages_bleus, "ancrages_cyan": ancrages_cyan, "piliers_cyan": piliers_cyan, "lum_niveau": LUM_NIVEAU, "lave_fond": sorted(lave_fond), "deco": deco_nb,
             "leviers": leviers, "coffres": coffres, "coffre_cle": coffre_cle, "ennemis": ennemis, "montagnes": {k: {n: v[n] for n in ("noyau", "chambre", "tunnel", "lave", "entree")} for k, v in montagnes.items()}, "lumieres": lumieres, "sources": sources, "lanternes": lanternes,
             "arrivee": (14, YF2 + 2, Z_ARRIVEE), "entree": (X0_SALLE + 2, YF2 + 1, cellule_de(ILES["E"]["ellipse"][0])[1]), "yf2": YF2, "y_fond": Y_FOND, "plafond": YF2 + HAUT_SALLE2}
    return blocs, infos
