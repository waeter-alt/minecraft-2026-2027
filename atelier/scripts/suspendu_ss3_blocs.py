# [claude-build]
"""suspendu_ss3_blocs.py - Donjon Suspendu, sous-sol 3 (salle 3, derriere la porte rouge) : l'arene du Gardien des Racines, dessinee pour ce donjon, et le long chemin qui y mene.
L'arene : une caverne ronde (27 de rayon, 80 de haut) dont le fond est un lac de lave ; le Gardien en sort au centre. Une RAMPE EN SPIRALE de 4 de large (rayon 12,5 a 16,5, trois tours et un cinquieme, une marche
par bloc, de 9 a 44 de haut) l'entoure : on la parcourt a pied, elle passe a portee d'epee de ses points faibles (qui sortent de son corps en bourgeons, voir GolemModele), a toutes les orientations. Le GRAPPIN sert a
atteindre ce que la rampe n'atteint pas : le balcon de la sortie (scellee par des barreaux, ouverte a la mort du boss), 15 blocs au dessus du haut de la rampe, et une alcove a coffre dans la paroi ouest.
Le chemin : la porte 3 (rouge) ouvre le couloir 3 du rez-de-chaussee ; son bout, jusque-la ferme, donne sur un palier, puis un escalier de 53 marches (vers l'est, le long du bord nord du gabarit, ou rien d'autre ne passe)
descend jusqu'au niveau du couloir de la salle, puis un long tunnel plat et un couloir menent a un GOUFFRE (de la lave tout au fond), que ne franchit plus qu'un PONT EN RUINE : quatre troncons qui tiennent encore
(le depart, deux ilots, la tete du pont sur la rampe) separes par trois trous de 6 blocs, qu'on ne passe qu'au grappin (deux ancrages par trou, tenus a la voute par des chaines). Quatre corniches a ancrage mural,
dans la paroi de la caverne, sont des refuges et des raccourcis de plus. Pur calcul : renvoie les blocs et les reperes."""
import math
from functools import lru_cache

import suspendu_salle1 as rdc
import suspendu_ss2_deco as deco
from suspendu_outils import AIR, S, h01

Y0 = rdc.Y0                                # le sol du couloir 3 (et du palier)
HAUT_PALIER = rdc.HAUT_COULOIR             # 8, comme le couloir 3
HAUT_TUNNEL = rdc.HAUT_TUNNEL              # 6 au dessus du sol
Z_TUNNEL = (8, 14)                         # les 7 cases de large de l'escalier et du tunnel (z), le long du bord nord du gabarit
X_PALIER, Z_PALIER = (70, 76), (8, 19)     # le palier : dans l'axe du couloir 3 (x 73), de z 19 (la ou l'on perce le mur du bout du couloir) a z 8
X_ESCALIER = 77                            # la premiere marche
Y_SOL_SALLE = 31                           # le sol du tunnel plat, du couloir et du pont
NB_MARCHES = Y0 - 1 - Y_SOL_SALLE          # 53 : de Y0 - 1 (la premiere marche) a Y_SOL_SALLE + 1 ; ensuite le tunnel est plat
X_COULOIR = 279                            # l'axe du couloir de la salle et du centre de l'arene
X_FIN = X_COULOIR + 4                      # le tunnel s'arrete 4 blocs apres l'axe du couloir
RANG_BANDE = rdc.RANG_BANDE

# l'arene
CX, CZ = 279, 66                           # le centre (le Gardien y est enfonce dans la lave)
R_SALLE = 26.5                             # rayon de la caverne (+ 0 a 2,5 d'ondulation des parois)
Y_LAVE = (-4, -3)                          # les deux couches de lave (le socle du monde est en Y -8)
Y_SURFACE = -2                             # le premier bloc d'air au dessus de la lave : les pieds du Gardien
Y_TOP = 79                                 # le haut de la voute au centre (il faut au moins 2 blocs de roche sous l'herbe, Y 85)
R_RAMPE = (12.5, 16.5)                     # la rampe : en dehors des bras du Gardien (11 de rayon), a portee d'epee de ses points faibles
Y_RAMPE_BAS = 9                            # sol de la rampe a son point de depart (azimut 0, plein nord)
PAS_RAMPE = 11                             # de combien elle monte a chaque tour (au plus 12 : un point faible est a portee d'epee d'un sol de 7,6 en dessous a 4,4 au dessus de lui)
THETA_RAMPE = 1152                         # son etendue en degres (3 tours et 72 degres : elle finit sous le balcon, a l'est-nord-est)
BALCON = {"x": (296, 306), "z": (54, 62), "sol": 59, "haut": 70, "ancrage": (297, 63, 58)}                # le balcon de la sortie, a l'est
SORTIE = {"x": 307, "y": (60, 63), "z": (57, 59)}                                                         # la sortie scellee (12 barreaux) et, derriere, la salle de sortie
SALLE_SORTIE = {"x": (308, 314), "y": (60, 64), "z": (54, 62)}
ALCOVE = {"x": (251, 258), "z": (72, 78), "sol": 52, "haut": 62, "ancrage": (256, 57, 75), "coffre": (255, 53, 75)}    # l'alcove a coffre de la paroi ouest, hors de portee de la rampe sans grappin
Z_GORGE = 17                                                                                              # le couloir s'arrete en z 16 : au dela, le gouffre (jusqu'a la caverne)
GORGE = {"x": (272, 286), "haut": 47}                                                                     # 15 de large (+ 0 a 2 d'ondulation), voute a 47 (+ 0 a 3), lave au fond comme dans la caverne
PONT_X = (277, 281)                                                                                       # le pont : 5 de large, au niveau du tunnel
TRONCONS = ((17, 20), (27, 31), (38, 42), (49, 50))                                                       # les morceaux de pont qui tiennent encore (z) : le depart, l'ilot 1, l'ilot 2, la tete du pont (la rampe commence en z 50)
TROUS = ((21, 26), (32, 37), (43, 48))                                                                    # entre eux : trois trous de 6 (un saut au sprint en franchit 4 au mieux)
PONT_ANCRAGES = (("pont_a1", (278, 35, 31)), ("pont_a2", (280, 36, 30)),                                  # deux ancrages par trou, au bout du troncon d'en face, 3 a 4 blocs au dessus du pont
                 ("pont_b1", (278, 35, 42)), ("pont_b2", (280, 36, 41)),
                 ("pont_c1", (278, 35, 52)), ("pont_c2", (280, 36, 51)))
PILIERS = (((279, 29), None), ((279, 40), None), ((279, 24), 10), ((279, 34), 6), ((279, 45), 12))        # les piles du pont : celles des ilots montent jusqu'a lui ; trois moignons dans le vide (hauteur du sommet)
CORNICHES = (("ne", {"x": (294, 300), "z": (42, 48), "sol": 39}), ("se", {"x": (294, 300), "z": (84, 90), "sol": 30}),
             ("so", {"x": (258, 264), "z": (84, 90), "sol": 33}), ("no", {"x": (258, 264), "z": (42, 48), "sol": 36}))       # quatre corniches dans la paroi, a 7 blocs de la rampe : un ancrage encastre dans le mur du fond
TROUS_RAMPE = ((60, 86), (137, 163), (212, 238), (887, 913), (972, 998), (1057, 1083))                   # les trous de l'anneau (degres le long de la spirale, 26 chacun, soit 6 blocs au milieu) : trois en bas, trois en haut ; la bande du milieu (238 a 887), ou l'on se bat, reste entiere
THETA_ENTREE = 720                                                                                        # la tete du pont rejoint la rampe a 720 degres (plein nord, deuxieme tour, sol a 31)
DELTA_ANCRAGE = 13.8                                                                                      # un ancrage (sur une colonne brisee) a 13,8 degres (3,5 blocs) du bord de chaque trou, de chaque cote
RETOUR_RAMPE = (X_COULOIR, Y_SOL_SALLE + 1, 51)                                                           # ou l'on revient si l'on tombe de la rampe (le depart du pont, si l'on tombe du pont)

# le tunnel organique (de l'escalier au couloir) : trois chambres, des fosses de 6 blocs a franchir au grappin, quatre coffres
ZMILIEU = 11                                                                                              # l'axe du tunnel (z)
TROUS_TUNNEL = ((160, 166), (205, 210), (217, 222), (250, 256))                                           # les trous (x), sur toute la largeur : la deuxieme fosse est coupee en deux par un ilot
ILE = (211, 216)                                                                                          # l'ilot de la deuxieme fosse (son coffre) : une pile de briques sous lui
ILE_PILE = (213, 214)
Y_LAVE_FOSSE = (19, 20)                                                                                   # la lave au fond des fosses (11 sous le sol) ; la zone de chute est juste au dessus
Y_ZONE_FOSSE = 22
CHAMBRES = ({"x": (133, 149), "z": (5, 17), "haut": 14},                                                  # la chambre 1 : haute, avec la corniche a coffre
            {"x": (184, 199), "z": (4, 17), "haut": 10},
            {"x": (257, 271), "z": (3, 15), "haut": 11})                                                  # la chambre 3 : apres la derniere fosse, avec un coffre
NICHE_ESCALIER = {"x": (96, 99), "z": (4, 7), "sol": 63, "haut": 67}                                      # une niche sur le flanc nord de l'escalier : le premier coffre (a pied)
CORNICHE_TUNNEL = {"x": (138, 143), "z": (3, 8), "sol": 37, "haut": 43, "ancrage": (141, 41, 6)}          # une corniche haute dans la chambre 1 : le deuxieme coffre (au grappin)
COFFRES_TUNNEL = (("escalier", (97, 64, 5), "south", (97, 64, 6)), ("corniche", (140, 38, 4), "south", (140, 38, 5)),
                  ("ile", (213, 32, 11), "west", (212, 32, 11)), ("chambre3", (262, 32, 4), "south", (262, 32, 5)))      # nom, coffre, orientation, case d'ou l'on l'ouvre
TABLE_BUTIN = "atelier:suspendu_tunnel"                                                                   # la table de butin des quatre coffres (voir donjon_suspendu.py)


def pendre(blocs, x, z, plafond, y_pref):
    """Une lanterne qui pend d'une chaine tenue au plafond (dernier bloc d'air `plafond`) : au plus a y_pref, ou juste sous le plafond si celui-ci est plus bas. Renvoie sa case."""
    y = min(y_pref, plafond - 1)
    for yy in range(y + 1, plafond + 1):
        blocs[(x, yy, z)] = S("chain", axis="y", waterlogged=False)
    blocs[(x, y, z)] = S("lantern", hanging=True, waterlogged=False)
    return (x, y, z)


def _lisse(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def _poids_chambre(x, c):
    a, b = c["x"]
    return _lisse((x - a) / 5) * _lisse((b - x) / 5)


def bords_tunnel(x):
    """Les bords (z) de l'escalier et du tunnel a la colonne x : de 8 a 14 (7 de large) au minimum, elargi par endroits (de 5 a 17), plus encore dans les chambres. Le rez-de-chaussee commence en z 18 jusqu'en x 112 : jusque la, jamais au dela de z 15."""
    if x < X_ESCALIER + 5 or x >= X_FIN - 8:
        mn, mx = Z_TUNNEL
    else:
        mn = Z_TUNNEL[0] - int(3.4 * deco.bruit(x, 0, 8, 101))
        mx = Z_TUNNEL[1] + int(3.4 * deco.bruit(x, 0, 8, 102))
    for c in CHAMBRES:
        w = _poids_chambre(x, c)
        mn = round(mn + (c["z"][0] - mn) * w)
        mx = round(mx + (c["z"][1] - mx) * w)
    return mn, min(mx, 15 if x < 116 else 17)


def haut_tunnel(x, z):
    """L'air au dessus du sol, a la case (x, z) : de 6 a 8 (qui ondule), haut dans les chambres, d'au moins 9 pres des fosses (la chaine d'un ancrage y tient)."""
    if x >= X_FIN - 8:
        return HAUT_TUNNEL
    h = HAUT_TUNNEL + int(3 * deco.bruit(x, z, 6, 103))
    for c in CHAMBRES:
        h = round(h + (c["haut"] - h) * _poids_chambre(x, c))
    if any(a - 6 <= x <= b + 6 for (a, b) in TROUS_TUNNEL):
        h = max(h, 9)
    return h


def dans_trou(x, z):
    """Pas de sol en (x, z) : dans un trou, ou au bord (une case sur trois environ) pour qu'il soit irregulier."""
    for (a, b) in TROUS_TUNNEL:
        if a <= x <= b or (x in (a - 1, b + 1) and not ILE[0] <= x <= ILE[1] and h01(x, z, 0, 111) < 0.3):                 # (l'ilot garde ses bords : on y arrive a 0,75 du bord)
            return True
    return False


def azimut(x, z):
    """L'azimut en degres de la case (x, z) vu du centre : 0 au nord (z decroissant), 90 a l'est."""
    return math.degrees(math.atan2(x + 0.5 - CX, -(z + 0.5 - CZ))) % 360


def sol_rampe(x, z, tour):
    """Le sol de la rampe en (x, z) au tour donne (0 a 3), ou None si elle n'y est pas."""
    theta = azimut(x, z) + 360 * tour
    if theta > THETA_RAMPE:
        return None
    return round(Y_RAMPE_BAS + PAS_RAMPE * theta / 360)


@lru_cache(maxsize=None)
def construire():
    """Renvoie (blocs, infos) de la salle 3 : blocs {(x, y, z): etat} dans le repere du gabarit (l'air est ecrit explicitement), infos : reperes."""
    air, sol_de, sources, marches = set(), {}, [], []
    # le palier : le vestibule de briques derriere la porte (inchange)
    for x in range(X_PALIER[0], X_PALIER[1] + 1):
        for z in range(Z_PALIER[0], Z_PALIER[1] + 1):
            sol_de[(x, z)] = Y0
            for y in range(Y0 + 1, Y0 + HAUT_PALIER + 1):
                air.add((x, y, z))
    # l'escalier (une marche par colonne x, 53 marches) puis le tunnel plat jusqu'au couloir de la salle : une grotte organique (largeur et plafond qui ondulent, trois chambres),
    # coupee par trois fosses de 6 blocs (sur toute la largeur, de la lave au fond) qu'on ne franchit qu'au grappin ; la deuxieme a un ilot au milieu
    colonnes = list(range(X_ESCALIER, X_FIN + 1))
    trou, plafond_t, y_col, bornes = set(), {}, {}, {}
    for x in colonnes:
        i = x - X_ESCALIER
        y_sol = Y0 - 1 - i if i < NB_MARCHES else Y_SOL_SALLE
        y_col[x] = y_sol
        zmin, zmax = bords_tunnel(x)
        bornes[x] = (zmin, zmax)
        for z in range(zmin, zmax + 1):
            plafond_t[(x, z)] = y_sol + haut_tunnel(x, z)
            if dans_trou(x, z):
                trou.add((x, z))
                for y in range(Y_LAVE_FOSSE[1] + 1, plafond_t[(x, z)] + 1):
                    air.add((x, y, z))
            else:
                sol_de[(x, z)] = y_sol
                for y in range(y_sol + 1, plafond_t[(x, z)] + 1):
                    air.add((x, y, z))
    # une niche a coffre sur le flanc de l'escalier (on y va a pied) et une corniche haute dans la chambre 1 (au grappin)
    for cfg in (NICHE_ESCALIER, CORNICHE_TUNNEL):
        for x in range(cfg["x"][0], cfg["x"][1] + 1):
            for z in range(cfg["z"][0], cfg["z"][1] + 1):
                for y in range(cfg["sol"] + 1, cfg["haut"] + 1):
                    air.add((x, y, z))
    blocs = {v: AIR for v in air}
    lave_t = set()
    for (x, z) in trou:
        for y in Y_LAVE_FOSSE:
            blocs[(x, y, z)] = S("lava", level=0)
            lave_t.add((x, y, z))
        for y in range(Y_LAVE_FOSSE[0] - 3, Y_LAVE_FOSSE[0]):
            blocs[(x, y, z)] = deco.fond_roche(x, z)
    variantes = ("stone_stairs", "andesite_stairs", "cobblestone_stairs", "mossy_cobblestone_stairs", "tuff_stairs")
    for (x, z), y in sol_de.items():
        i = x - X_ESCALIER
        if X_PALIER[0] <= x <= X_PALIER[1]:
            blocs[(x, y, z)] = rdc.sol(x, z)
        elif i < NB_MARCHES:                                   # une marche brute : de la roche taillee, moussue par endroits (plus de tapis de pierre polie)
            blocs[(x, y, z)] = S(variantes[int(h01(x, y, z, 121) * len(variantes))], facing="west", half="bottom", shape="straight", waterlogged=False)
            marches.append((x, y + 1, z))
        else:
            blocs[(x, y, z)] = deco.sol_ile(x, z)
    for cfg in (NICHE_ESCALIER, CORNICHE_TUNNEL):
        for x in range(cfg["x"][0], cfg["x"][1] + 1):
            for z in range(cfg["z"][0], cfg["z"][1] + 1):
                blocs[(x, cfg["sol"], z)] = deco.sol_ile(x, z)
    marches = [m for m in marches if blocs.get(m) == AIR]        # la niche de l'escalier a pris la place de quelques marches
    for x in range(ILE_PILE[0], ILE_PILE[1] + 1):              # la pile sous l'ilot : une colonne de briques de la lave au sol
        for z in range(ZMILIEU - 1, ZMILIEU + 2):
            for y in range(Y_LAVE_FOSSE[0], Y_SOL_SALLE):
                u = h01(x, y, z, 122)
                blocs[(x, y, z)] = S("mossy_stone_bricks") if u < 0.3 else (S("cracked_stone_bricks") if u < 0.5 else S("stone_bricks"))
            lave_t.discard((x, Y_LAVE_FOSSE[0], z))
            lave_t.discard((x, Y_LAVE_FOSSE[1], z))
    # le couloir de la salle : du tunnel (z 15) jusqu'au gouffre (z 16, 3 de large, 4 de haut)
    couloir = {(x, y, z) for x in range(X_COULOIR - 1, X_COULOIR + 2) for y in range(Y_SOL_SALLE + 1, Y_SOL_SALLE + 5) for z in range(15, Z_GORGE)}
    for z in range(15, Z_GORGE):
        for x in range(X_COULOIR - 1, X_COULOIR + 2):
            blocs[(x, Y_SOL_SALLE, z)] = rdc.sol(x, z)
    for p in couloir:
        blocs[p] = AIR
    air |= couloir
    # l'habillage : toute case voisine de l'air (ou de la lave) qui n'en est pas devient du mur ; briques sculptees autour du palier, roche brute (de la mousse au pied) apres
    for (x, y, z) in sorted(air | lave_t):
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + dx, y + dy, z + dz)
            if n in air or n in lave_t or n in blocs or (n[2] > Z_PALIER[1] and n[0] < 200):                  # au dela de z 19 a l'ouest : le couloir 3 du rez-de-chaussee (son air s'ouvre sur le palier, ses murs restent les siens)
                continue
            if n[0] <= X_ESCALIER:
                rang = n[0] - X_ESCALIER
                blocs[n] = rdc.CADRE if (rang % RANG_BANDE == 0 and (dz != 0 or dy > 0)) else rdc.mur(*n)
            else:
                pied = n[1] <= y_col.get(n[0], Y_SOL_SALLE) + 1
                blocs[n] = S("moss_block") if (pied and deco.bruit(n[0], n[2], 7, 31) > 0.62 and h01(n[0], n[1], n[2], 33) < 0.8) else deco.roche_mur(n[0], n[1], n[2], n[1] - 5)
    for (x, z) in ((71, 9), (75, 9), (71, 14), (75, 14), (73, 18)):                                 # cinq lanternes suspendues dans le palier
        for y in (Y0 + HAUT_PALIER, Y0 + HAUT_PALIER - 1):
            blocs[(x, y, z)] = S("chain", axis="y", waterlogged=False)
        blocs[(x, Y0 + HAUT_PALIER - 2, z)] = S("lantern", hanging=True, waterlogged=False)
        sources.append((x, Y0 + HAUT_PALIER - 2, z))
    for x in (X_COULOIR - 2, X_COULOIR + 2):                                                         # le couloir est court : une lanterne dans une niche de chaque paroi, a son bout
        p = (x, Y_SOL_SALLE + 3, Z_GORGE - 1)
        blocs[p] = S("lantern", hanging=False, waterlogged=False)
        sources.append(p)
    # le coffre de la niche de l'escalier, celui de la corniche, celui de l'ilot, celui de la chambre 3 : du butin tire d'une table de butin (voir donjon_suspendu.py)
    coffres_t = []
    for nom, (x, y, z), facing, acces in COFFRES_TUNNEL:
        blocs[(x, y, z)] = S("chest", facing=facing, type="single", waterlogged=False)
        coffres_t.append({"nom": nom, "pos": (x, y, z), "acces": acces})
    # les ancrages : deux files (z 9 et 13) de chaque cote de chaque trou, a 3,5 blocs du bord ; l'ancrage de la corniche ; tous tenus au plafond par une chaine
    ancrages_t = []
    for k, (a, b) in enumerate(TROUS_TUNNEL, 1):
        for cote, xa in (("a", a - 4), ("b", b + 4)):
            if k == 3 and cote == "a":
                continue                                                                       # l'ilot n'a que deux ancrages (x 213), qui servent dans les deux sens : arrivee a 0,75 du bord de chaque cote
            if k == 2 and cote == "b":
                xa = b + 3
            for lane in (9, 13):
                ancrages_t.append((f"tunnel_{k}{cote}{lane}", xa, Y_SOL_SALLE + 4, lane))
    ancrages_t.append(("tunnel_corniche", *CORNICHE_TUNNEL["ancrage"]))
    for nom, x, y, z in ancrages_t:
        yh = y
        while blocs.get((x, yh + 1, z)) == AIR:
            yh += 1
        assert blocs.get((x, yh + 1, z)) is not None and yh - y <= 12, (nom, yh)
        for yy in range(y + 1, yh + 1):
            blocs[(x, yy, z)] = S("chain", axis="y", waterlogged=False)
        blocs[(x, y, z)] = S("donjon:ancrage")
    # la lumiere : une lanterne au bout d'une chaine au bord et au milieu de chaque trou ; dans le tunnel, des champignons lumineux dans les parois (tous les 6 rangs, un cote puis l'autre) et des lianes a baies
    protege = set()
    for nom, x, y, z in ancrages_t:
        protege |= {(x + dx, z + dz) for dx in range(-2, 3) for dz in range(-2, 3)}
    for k, (a, b) in enumerate(TROUS_TUNNEL, 1):
        for xl in (a - 2, (a + b) // 2, b + 2):
            y = Y_SOL_SALLE + 6
            yh = y
            while blocs.get((xl, yh + 1, ZMILIEU)) == AIR:
                yh += 1
            assert blocs.get((xl, yh + 1, ZMILIEU)) is not None and yh - y <= 8, (xl, yh)
            for yy in range(y + 1, yh + 1):
                blocs[(xl, yy, ZMILIEU)] = S("chain", axis="y", waterlogged=False)
            blocs[(xl, y, ZMILIEU)] = S("lantern", hanging=True, waterlogged=False)
            sources.append((xl, y, ZMILIEU))
            protege |= {(xl + dx, ZMILIEU + dz) for dx in range(-1, 2) for dz in range(-1, 2)}
    for c in CHAMBRES:                                                                          # une lanterne au bout d'une chaine au milieu de chaque chambre (trois par chambre), une a la sortie de l'escalier
        a, b = c["x"]
        for xl in (a + 3, (a + b) // 2, b - 3):
            sources.append(pendre(blocs, xl, ZMILIEU, plafond_t[(xl, ZMILIEU)], Y_SOL_SALLE + 6))
            protege |= {(xl + dx, ZMILIEU + dz) for dx in range(-1, 2) for dz in range(-1, 2)}
    for xl, zl in ((X_ESCALIER + NB_MARCHES + 2, ZMILIEU), (276, 11), (281, 10)):             # a la sortie de l'escalier, et dans le coude avant le couloir
        sources.append(pendre(blocs, xl, zl, plafond_t[(xl, zl)], Y_SOL_SALLE + 6))
    blocs[(CORNICHE_TUNNEL["x"][1] - 1, CORNICHE_TUNNEL["sol"] + 1, CORNICHE_TUNNEL["z"][0] + 1)] = S("lantern", hanging=False, waterlogged=False)      # une lanterne sur la corniche
    sources.append((CORNICHE_TUNNEL["x"][1] - 1, CORNICHE_TUNNEL["sol"] + 1, CORNICHE_TUNNEL["z"][0] + 1))
    blocs[(NICHE_ESCALIER["x"][1], NICHE_ESCALIER["sol"] + 1, NICHE_ESCALIER["z"][0] + 1)] = S("lantern", hanging=False, waterlogged=False)      # une lanterne dans la niche
    sources.append((NICHE_ESCALIER["x"][1], NICHE_ESCALIER["sol"] + 1, NICHE_ESCALIER["z"][0] + 1))
    for nom, (x, y, z), facing, acces in COFFRES_TUNNEL:
        protege |= {(x + dx, z + dz) for dx in range(-2, 3) for dz in range(-2, 3)}
    for (x, z) in sorted(((x, z) for x in range(CORNICHE_TUNNEL["x"][0], CORNICHE_TUNNEL["x"][1] + 1) for z in range(CORNICHE_TUNNEL["z"][0], CORNICHE_TUNNEL["z"][1] + 1))):
        protege.add((x, z))
    nb_tunnel = {"champignons": 0, "lianes": 0, "stalactites": 0, "stalagmites": 0, "racines": 0}
    for x in colonnes:
        i = x - X_ESCALIER
        if x >= X_FIN - 6:
            continue
        zmin, zmax = bornes[x]
        for cote_nord, rang in ((True, 2), (False, 0)):                                         # un champignon lumineux dans chaque paroi, a 2 blocs au dessus du sol, decales l'un de l'autre (plus serres dans l'escalier : la lumiere descend moins loin)
            if i % (4 if i < NB_MARCHES else 5) == rang:
                p = (x, y_col[x] + 2, zmin - 1 if cote_nord else zmax + 1)
                if blocs.get(p) is not None and blocs[p] != AIR and p not in lave_t:
                    blocs[p] = S("shroomlight")
                    sources.append((*p, 15))
                    nb_tunnel["champignons"] += 1
        for z in range(zmin, zmax + 1):
            if (x, z) in protege or (x, z) in trou:
                continue
            haut = plafond_t[(x, z)]
            if h01(x, 11, z, 131) < 0.012 and all(blocs.get((x, haut - j, z)) == AIR for j in range(4)):                # des lianes a baies lumineuses (elles eclairent)
                n = 2 + int(h01(x, 12, z, 132) * 3)
                for (bx, by, bz) in deco.liane(blocs, x, haut, z, n, True):
                    sources.append((bx, by, bz, 14))
                nb_tunnel["lianes"] += 1
            elif h01(x, 13, z, 133) < 0.05 and all(blocs.get((x, haut - j, z)) == AIR for j in range(3)):               # des stalactites
                deco.stalactite(blocs, x, haut, z, 1 + int(h01(x, 14, z, 134) * 2))
                nb_tunnel["stalactites"] += 1
            elif h01(x, 15, z, 135) < 0.03 and all(blocs.get((x, haut - j, z)) == AIR for j in range(3)):               # des racines qui pendent de la voute
                blocs[(x, haut, z)] = S("hanging_roots", waterlogged=False)
                if h01(x, 16, z, 136) < 0.5:
                    blocs[(x, haut - 1, z)] = S("hanging_roots", waterlogged=False)
                nb_tunnel["racines"] += 1
            elif i >= NB_MARCHES and (z in (zmin, zmax)) and h01(x, 17, z, 137) < 0.1 and blocs.get((x, y_col[x] + 1, z)) == AIR and blocs.get((x, y_col[x] + 2, z)) == AIR:     # des stalagmites contre les parois (jamais au milieu : on passe)
                deco.stalagmite(blocs, x, y_col[x] + 1, z, 1 + int(h01(x, 18, z, 138) * 2))
                nb_tunnel["stalagmites"] += 1
    # le percage du mur du bout du couloir 3 : le palier arrive jusqu'en z 19, deux blocs de mur du rez-de-chaussee sont remplaces (la salle 3 est posee apres lui)
    percage = sorted(p for p in blocs if p[2] in (18, 19) and X_PALIER[0] - 1 <= p[0] <= X_PALIER[1] + 1)

    # ------------------------------------------------------------------ la caverne
    caverne, lave, roche = set(), set(), {}                    # les cases d'air, de lave ; la region de chaque case (pour le choix de la roche)
    rayon, plafond = {}, {}                                    # le rayon de chaque colonne de la caverne ; le dernier bloc d'air de chaque colonne (caverne ou gouffre)
    gorge = {}                                                 # les colonnes du gouffre seul (hors caverne) : leur voute
    for x in range(CX - 31, CX + 32):
        for z in range(CZ - 31, CZ + 32):
            r = math.hypot(x + 0.5 - CX, z + 0.5 - CZ)
            r_loc = R_SALLE + 2.5 * deco.bruit(x, z, 9, 71)
            if r > r_loc:
                continue
            rayon[(x, z)] = r
            plafond[(x, z)] = Y_TOP - int((r / 29) ** 2 * 12)
    for x in range(GORGE["x"][0] - 2, GORGE["x"][1] + 3):                                     # le gouffre : un couloir de roche creuse de la fin du couloir a la caverne, voute plus basse
        for z in range(Z_GORGE, CZ - 20):
            demi = 7 + 1.5 * deco.bruit(x, z, 8, 61)
            if abs(x + 0.5 - CX - 0.5) > demi:
                continue
            haut_g = GORGE["haut"] + int(3 * deco.bruit(x, z, 7, 53))
            if (x, z) in plafond:
                continue
            plafond[(x, z)] = haut_g
            gorge[(x, z)] = haut_g
    for (x, z), haut in plafond.items():
        for y in range(Y_SURFACE, haut + 1):
            caverne.add((x, y, z))
        for y in Y_LAVE:
            lave.add((x, y, z))
        for y in range(-7, Y_LAVE[0]):
            blocs[(x, y, z)] = deco.fond_roche(x, z)
    for p in lave:
        blocs[p] = S("lava", level=0)
    for p in caverne:
        blocs[p] = AIR
    for z in range(15, Z_GORGE):                                  # le sol du couloir, qu'une caverne qui mord sur son bout ne doit pas effacer
        for x in range(X_COULOIR - 1, X_COULOIR + 2):
            blocs[(x, Y_SOL_SALLE, z)] = rdc.sol(x, z)
    # le balcon de la sortie, l'alcove et les corniches : des niches creusees dans la paroi (un sol de briques, de l'air au dessus)
    niches = set()
    corniches = {nom: {**cfg, "haut": cfg["sol"] + 7} for nom, cfg in CORNICHES}
    for cfg in (BALCON, ALCOVE, *corniches.values()):
        for x in range(cfg["x"][0], cfg["x"][1] + 1):
            for z in range(cfg["z"][0], cfg["z"][1] + 1):
                blocs[(x, cfg["sol"], z)] = S("chiseled_stone_bricks") if (x + z) % 4 == 0 else S("stone_bricks")
                for y in range(cfg["sol"] + 1, cfg["haut"] + 1):
                    niches.add((x, y, z))
    sx, (sy0, sy1), (sz0, sz1) = SORTIE["x"], SORTIE["y"], SORTIE["z"]
    sortie = [(sx, y, z) for y in range(sy0, sy1 + 1) for z in range(sz0, sz1 + 1)]
    salle_sortie = set()
    for x in range(SALLE_SORTIE["x"][0], SALLE_SORTIE["x"][1] + 1):
        for z in range(SALLE_SORTIE["z"][0], SALLE_SORTIE["z"][1] + 1):
            blocs[(x, SALLE_SORTIE["y"][0] - 1, z)] = rdc.sol(x, z)
            for y in range(SALLE_SORTIE["y"][0], SALLE_SORTIE["y"][1] + 1):
                salle_sortie.add((x, y, z))
    for p in niches:
        blocs[p] = AIR
    for p in salle_sortie:
        blocs[p] = AIR
    for p in sortie:
        blocs[p] = S("iron_bars", waterlogged=False)
    # les parois : toute case voisine de l'air (ou de la lave) qui n'en est pas devient de la roche (la salle de sortie : le mur du donjon)
    for p in sorted(caverne | niches | lave | salle_sortie):
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (p[0] + dx, p[1] + dy, p[2] + dz)
            if n in blocs:
                continue
            blocs[n] = rdc.mur(*n) if p in salle_sortie else deco.roche_mur(n[0], n[1], n[2], 31)
    # le pont en ruine : quatre troncons de briques (moussues, fendues, bords arraches, marches brisees face aux trous), des piles sous les ilots et des moignons de piles dans le vide
    pont = []                                                      # les cases ou l'on se tient sur ce qui reste du pont
    for (z0, z1) in TRONCONS:
        for z in range(z0, z1 + 1):
            for x in range(PONT_X[0], PONT_X[1] + 1):
                bord = x in PONT_X
                if bord and h01(x, 31, z, 81) < 0.35:                                          # un bord arraché (les trois files du milieu, elles, tiennent toujours)
                    continue
                trou_au_nord = any(t1 == z - 1 for (_, t1) in TROUS)
                trou_au_sud = any(t0 == z + 1 for (t0, _) in TROUS)
                if (trou_au_nord or trou_au_sud) and x != X_COULOIR and h01(x, 32, z, 82) < 0.45:         # face au trou, une marche brisee
                    blocs[(x, Y_SOL_SALLE, z)] = S("stone_brick_stairs", facing="north" if trou_au_nord else "south", half="bottom", shape="straight", waterlogged=False)
                else:
                    t = h01(x, 31, z, 83)
                    blocs[(x, Y_SOL_SALLE, z)] = S("mossy_stone_bricks") if t < 0.22 else (S("cracked_stone_bricks") if t < 0.45 else S("stone_bricks"))
                pont.append((x, Y_SOL_SALLE + 1, z))
    for (cx_, cz_), sommet in PILIERS:
        for x in range(cx_ - 1, cx_ + 2):
            for z in range(cz_ - 1, cz_ + 2):
                for y in range(Y_LAVE[0], (Y_SOL_SALLE - 1 if sommet is None else sommet) + 1):
                    t = h01(x, y, z, 84)
                    blocs[(x, y, z)] = S("mossy_stone_bricks") if t < 0.25 else (S("cracked_stone_bricks") if t < 0.4 else S("stone_bricks"))
    # la rampe : quatre tours empiles (a PAS_RAMPE les uns des autres), une marche par bloc de hauteur
    nb_rampe, bas_rampe, haut_rampe, lumieres, theta_de = 0, 99, -99, [], {}
    for (x, z), r in sorted(rayon.items()):
        if not (R_RAMPE[0] <= r < R_RAMPE[1]):
            continue
        for tour in range(4):
            y = sol_rampe(x, z, tour)
            if y is None:
                continue
            bord = r < R_RAMPE[0] + 0.9 or r > R_RAMPE[1] - 0.9
            theta = azimut(x, z) + 360 * tour
            marge = 4 * h01(x, tour, z, 91) if bord else 0                                                   # les bords des trous sont irreguliers (les deux files du milieu, elles, s'arretent net)
            if any(a - marge <= theta <= b + marge for (a, b) in TROUS_RAMPE):
                continue                                                                                     # un trou : rien sous les pieds, sur toute la largeur de l'anneau
            if bord and abs(theta - THETA_ENTREE) > 14 and h01(x, y, z, 92) < 0.07:
                continue                                                                                     # un bord arraché ici et la (jamais les deux files du milieu : on passe toujours)
            theta_de[(x, y + 1, z)] = round(theta, 1)
            u = h01(x, y, z, 9)
            blocs[(x, y, z)] = (S("chiseled_stone_bricks") if u < 0.7 else S("cracked_stone_bricks")) if bord else (S("mossy_stone_bricks") if u < 0.18 else (S("cracked_stone_bricks") if u < 0.36 else S("stone_bricks")))
            for yy in range(y + 1, y + 5):
                blocs[(x, yy, z)] = AIR
            nb_rampe += 1
            bas_rampe, haut_rampe = min(bas_rampe, y), max(haut_rampe, y)
            if (x * 3 + z * 5 + tour * 7) % 4 == 0 and not bord:           # de la lumiere : un bloc invisible tous les quelques blocs
                blocs[(x, y + 2, z)] = S("light", level=13, waterlogged=False)
                lumieres.append((x, y + 2, z, 13))
    # les ancrages du grappin : deux par trou du pont, au dessus du balcon et de l'alcove, tenus a la voute par une chaine ; quatre dans le mur du fond des corniches
    ancrages, lanternes_pont = [], []
    for nom, (x, y, z) in (*PONT_ANCRAGES, ("balcon", BALCON["ancrage"]), ("alcove", ALCOVE["ancrage"])):
        yh = y
        while blocs.get((x, yh + 1, z)) == AIR:
            yh += 1
        for yy in range(y + 1, yh + 1):
            blocs[(x, yy, z)] = S("chain", axis="y", waterlogged=False)
        blocs[(x, y, z)] = S("donjon:ancrage")
        ancrages.append((nom, x, y, z))
    # les eclats : un morceau d'anneau de moins de 12 cases, coupe du reste (bords arraches des deux cotes), n'est pas un lieu ou l'on se tient : on le retire
    restantes, eclats = set(theta_de), []
    while restantes:
        depart_c = restantes.pop()
        comp, file_ = {depart_c}, [depart_c]
        while file_:
            cx_, cy_, cz_ = file_.pop()
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                for dy in (-1, 0, 1):
                    n = (cx_ + dx, cy_ + dy, cz_ + dz)
                    if n in restantes:
                        restantes.discard(n)
                        comp.add(n)
                        file_.append(n)
        if len(comp) < 12:
            eclats += comp
    for c in eclats:
        blocs[(c[0], c[1] - 1, c[2])] = AIR
        del theta_de[c]
    # l'anneau en ruine : de chaque cote de chaque trou, une colonne brisee de 3 blocs sur la file exterieure, un ancrage a son sommet (3 blocs au dessus du sol de l'anneau)
    for i, (a, b) in enumerate(TROUS_RAMPE, 1):
        for cote, theta in (("a", a - DELTA_ANCRAGE), ("b", b + DELTA_ANCRAGE)):
            tour, trouve = int(theta // 360), None
            for r in (16.0, 15.8, 15.6, 15.4):
                az = math.radians(theta % 360)
                x, z = int(round(CX + r * math.sin(az) - 0.5)), int(round(CZ - r * math.cos(az) - 0.5))
                y = sol_rampe(x, z, tour)
                if y is not None and (x, y + 1, z) in theta_de and blocs.get((x, y + 4, z)) == AIR:
                    trouve = (x, y, z)
                    break
            assert trouve is not None, (i, cote)
            x, y, z = trouve
            for yy in range(y + 1, y + 4):
                u = h01(x, yy, z, 93)
                blocs[(x, yy, z)] = S("mossy_stone_bricks") if u < 0.3 else (S("cracked_stone_bricks") if u < 0.55 else S("stone_bricks"))
            theta_de.pop((x, y + 1, z), None)                                                                   # la colonne occupe cette case : on ne s'y tient plus
            blocs[(x, y + 4, z)] = S("donjon:ancrage")
            ancrages.append((f"rampe_{i}{cote}", x, y + 4, z))
            blocs[(x, y + 5, z)] = S("light", level=12, waterlogged=False)                                       # un bloc de lumiere invisible au dessus de l'ancrage
            lumieres.append((x, y + 5, z, 12))
    nb_rampe = len(theta_de)
    lumieres = [l for l in lumieres if blocs.get(l[:3], ("",))[0] == "minecraft:light"]                          # un bloc de lumiere recouvert par une colonne n'eclaire plus
    blocs[ALCOVE["coffre"]] = S("chest", facing="east", type="single", waterlogged=False)
    for (x, y, z) in ((299, BALCON["sol"] + 1, 56), (303, BALCON["sol"] + 1, 60), (252, ALCOVE["sol"] + 1, 73), (257, ALCOVE["sol"] + 1, 77)):          # deux lanternes sur le balcon, deux dans l'alcove
        blocs[(x, y, z)] = S("lantern", hanging=False, waterlogged=False)
        sources.append((x, y, z))
    refuges = {}                                                                   # les corniches : la case ou l'on se tient pour entrer, le centre
    for nom, cfg in corniches.items():
        x0, x1 = cfg["x"]
        z0, z1 = cfg["z"]
        y = cfg["sol"] + 2
        milieu = ((x0 + x1) / 2, (z0 + z1) / 2)
        r_milieu = math.hypot(milieu[0] + 0.5 - CX, milieu[1] + 0.5 - CZ)
        mur = None
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                v = blocs.get((x, y, z))
                if (x0 <= x <= x1 and z0 <= z <= z1) or v is None or v == AIR:
                    continue
                if not any(x0 <= x + dx <= x1 and z0 <= z + dz <= z1 for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    continue
                if math.hypot(x + 0.5 - CX, z + 0.5 - CZ) <= r_milieu:                    # du cote de la paroi, pas du cote de la caverne
                    continue
                d = abs(x - milieu[0]) + abs(z - milieu[1])
                if mur is None or d < mur[0]:
                    mur = (d, (x, y, z))
        assert mur is not None, nom
        blocs[mur[1]] = S("donjon:ancrage")
        ancrages.append(("corniche_" + nom, *mur[1]))
        refuges[nom] = (int(round(milieu[0])), cfg["sol"] + 1, int(round(milieu[1])))
        for x, z in sorted(((x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)), key=lambda c: abs(c[0] - mur[1][0]) + abs(c[1] - mur[1][2])):
            if blocs.get((x, cfg["sol"] + 1, z)) == AIR:
                blocs[(x, cfg["sol"] + 1, z)] = S("lantern", hanging=False, waterlogged=False)
                sources.append((x, cfg["sol"] + 1, z))
                break
    # la lumiere du pont : une lanterne au bout d'une chaine tous les 5 blocs au dessus du couloir de la salle (de 19 a 34, ou la voute est basse), un bloc de lumiere invisible plus loin (voute trop haute)
    for z in (19, 24, 29, 34, 40, 44, 49):
        y = Y_SOL_SALLE + 6
        yh = y
        while blocs.get((X_COULOIR, yh + 1, z)) == AIR:
            yh += 1
        if yh - y <= 14 and blocs.get((X_COULOIR, yh + 1, z)) is not None:
            for yy in range(y + 1, yh + 1):
                blocs[(X_COULOIR, yy, z)] = S("chain", axis="y", waterlogged=False)
            blocs[(X_COULOIR, y, z)] = S("lantern", hanging=True, waterlogged=False)
            lanternes_pont.append((X_COULOIR, y, z))
        else:
            blocs[(X_COULOIR, y, z)] = S("light", level=15, waterlogged=False)
            lanternes_pont.append((X_COULOIR, y, z, 15))
    sources += lanternes_pont
    # la decoration : stalactites et lianes a baies lumineuses sous la voute (au plus 4 de long, a plus de 10 blocs au dessus de tout sol), mousse dans la roche ; rien pres des ancrages
    proteges = set()
    for (_, x, y, z) in ancrages:
        proteges |= {(x + dx, z + dz) for dx in range(-2, 3) for dz in range(-2, 3)}
    for (x, y, z, *_) in lanternes_pont:
        proteges |= {(x + dx, z + dz) for dx in range(-1, 2) for dz in range(-1, 2)}
    nb_deco = {"stalactites": 0, "lianes": 0}
    baies = []
    for (x, z), haut in sorted(plafond.items()):
        r = rayon.get((x, z))
        if (x, z) in proteges or (r is not None and r > 25) or (r is None and abs(x - CX) > 6):
            continue
        grappe = deco.bruit(x, z, 9, 41)
        if h01(x, 3, z, 42) < 0.004 + 0.06 * grappe ** 2:
            n = 1 + int(h01(x, 4, z, 43) * (1 + 3 * grappe))
            if all(blocs.get((x, haut - i, z)) == AIR for i in range(n)):
                deco.stalactite(blocs, x, haut, z, n)
                nb_deco["stalactites"] += 1
                continue
        if h01(x, 5, z, 44) < 0.004 + 0.03 * deco.bruit(x, z, 11, 45) ** 2:
            n = 2 + int(h01(x, 6, z, 46) * 4)
            if all(blocs.get((x, haut - i, z)) == AIR for i in range(n)):
                baies += deco.liane(blocs, x, haut, z, n, h01(x, 7, z, 47) < 0.6)
                nb_deco["lianes"] += 1
    # la lumiere de la salle : la lave (sources de 15), les baies, les blocs de lumiere de la rampe
    sources += [(x, y, z, 14) for (x, y, z) in baies] + lumieres
    sources += [(x, Y_LAVE[1], z) for (x, y, z) in sorted(lave) if y == Y_LAVE[1] and (x * 5 + z * 3) % 6 == 0]
    centre = (CX, Y_SURFACE, CZ)
    infos = {"arrivee": (73, Y0 + 1, 19), "palier": [(x, Z_PALIER[0], Z_PALIER[1]) for x in X_PALIER], "marches": sorted(marches), "colonnes": colonnes, "sources": sources, "percage": percage,
             "depart_boss": (X_COULOIR, Y_SOL_SALLE + 1, 19), "retour_rampe": RETOUR_RAMPE, "golem": {"centre": centre},
             "rampe": {"cases": nb_rampe, "bas": bas_rampe, "haut": haut_rampe, "rayon": R_RAMPE, "pas": PAS_RAMPE, "theta": THETA_RAMPE, "y0": Y_RAMPE_BAS},
             "plateformes": {"D": {"bbox": (PONT_X[0], PONT_X[1], 49, 53), "dessus": Y_SOL_SALLE, "boss": None}},
             "rampe_trous": [list(q) for q in TROUS_RAMPE], "rampe_theta": sorted([x, y, z, th] for (x, y, z), th in theta_de.items()), "pont": sorted(pont), "trous": [list(t) for t in TROUS], "troncons": [list(t) for t in TRONCONS], "corniches": refuges,
             "ancrages_boss": ancrages + ancrages_t, "zone_chute": ((CX - 28, -6, Z_GORGE), (56, 12, CZ + 28 - Z_GORGE)), "chute_pont_dz": 46 - Z_GORGE,
             "sortie": sortie, "sortie_bloc": "minecraft:iron_bars", "couloir_x": X_COULOIR, "balcon": (302, BALCON["sol"] + 1, 58), "alcove": (255, ALCOVE["sol"] + 1, 74), "coffre": ALCOVE["coffre"],
             "salle_sortie": SALLE_SORTIE, "y_sol_salle": Y_SOL_SALLE, "deco": nb_deco, "tunnel": {"trous": [list(g) for g in TROUS_TUNNEL], "ile": list(ILE), "coffres": coffres_t, "deco": nb_tunnel,
                                  "fosses": [{"zone": ((a - 1, Y_ZONE_FOSSE, 2), (b - a + 2, 3, 17)), "retour": retour} for (a, b), retour in zip(TROUS_TUNNEL, ((a - 3, Y_SOL_SALLE + 1, ZMILIEU) if i != 2 else (215, Y_SOL_SALLE + 1, ZMILIEU + 1) for i, (a, b) in enumerate(TROUS_TUNNEL)))],
                                  "passages": [{"avant": (a - 3, Y_SOL_SALLE + 1, ZMILIEU), "apres": (b + 3, Y_SOL_SALLE + 1, ZMILIEU + 1)} for (a, b) in TROUS_TUNNEL]}}
    return blocs, infos
