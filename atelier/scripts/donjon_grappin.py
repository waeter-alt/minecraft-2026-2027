# [claude-build]
"""
donjon_grappin.py - Donjon n°1 « La Gorge suspendue » : une gorge de jungle fermee qu'on ne traverse qu'au grappin (mod grappin 1.1).
Au fond, une fosse d'eau : y tomber renvoie au dernier point de passage. A la fin, une grande salle naturelle (boss plus tard).
Le grappin est donne a l'entree. Pose dans un monde superflat par defaut (herbe a Y-61) : la structure commence au sol.
  .venv\\Scripts\\python.exe sources\\donjon_grappin.py [--monde "Donjon Grappin"]
Controle : sources\\verif_grappin.py exports\\donjon_grappin.nbt exports\\donjon_grappin_cibles.json
Repere local : x 0..99 (ouest -> est), z 0..139 (nord -> sud), y 0..119 ; y 0 = le sol du monde.
"""
import argparse
import json
import math
from pathlib import Path

import nbtlib
import numpy as np
from nbtlib import Byte, Compound, Int, List, String

ap = argparse.ArgumentParser()
ap.add_argument("--monde", default="Donjon Grappin")
args = ap.parse_args()
RACINE = Path(__file__).resolve().parent.parent
SAVE = RACINE / "launcher/instances/Build-1.21.1/minecraft/saves" / args.monde
NOM = "donjon_grappin"
SX, SY, SZ = 100, 120, 140
ORIGINE = (-50, -61, -70)                              # superflat par defaut : herbe a Y-61


def S(n, **p):
    n = n if ":" in n else "minecraft:" + n
    return (n, tuple(sorted((k, str(v).lower() if isinstance(v, bool) else str(v)) for k, v in p.items())))


AIR, EAU = S("air"), S("water", level=0)
PIERRE, ANDE, MCB, TUF, TERRE, GROS, RAC = S("stone"), S("andesite"), S("mossy_cobblestone"), S("tuff"), S("dirt"), S("coarse_dirt"), S("rooted_dirt")
MOUSSE, TAPIS, FOUG, HERBE = S("moss_block"), S("moss_carpet"), S("fern"), S("short_grass")
LOG, BOIS = S("jungle_log", axis="y"), S("jungle_wood", axis="y")
FEUILLE, AZF = S("jungle_leaves", persistent=True, distance=1, waterlogged=False), S("azalea_leaves", persistent=True, distance=1, waterlogged=False)
AZALEE, MRAC, RACP = S("flowering_azalea"), S("mangrove_roots", waterlogged=False), S("hanging_roots", waterlogged=False)
CHAMP, LUM = S("shroomlight"), S("light", level=15, waterlogged=False)
LIERRE_P, LIERRE = S("cave_vines_plant", berries=True), S("cave_vines", age=25, berries=True)
LIERRE_N, LIERRE_NF = S("cave_vines_plant", berries=False), S("cave_vines", age=25, berries=False)
MARCHE_S = S("mossy_cobblestone_stairs", facing="south", half="bottom", shape="straight", waterlogged=False)
MARCHE_N = S("mossy_cobblestone_stairs", facing="north", half="bottom", shape="straight", waterlogged=False)
blocs, NBT = {}, {}


def put(x, y, z, s):
    x, y, z = int(x), int(y), int(z)
    if not (0 <= x < SX and 0 <= y < SY and 0 <= z < SZ):
        raise ValueError(f"hors gabarit {(x, y, z)} {s[0]}")
    blocs[(x, y, z)] = s


def nom(p):
    s = blocs.get(p)
    return None if s is None else s[0].split(":")[1]


def h01(x, y, z, g=0):
    h = (int(x) * 374761393 + int(y) * 1442695041 + int(z) * 668265263 + g * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h ^= h >> 16
    return h / 0xFFFFFFFF


def bruit(a, b, cel, g):
    ga, gb = a / cel, b / cel
    i, j = math.floor(ga), math.floor(gb)
    fa, fb = ga - i, gb - j
    sa, sb = fa * fa * (3 - 2 * fa), fb * fb * (3 - 2 * fb)
    p, q = h01(i, 0, j, g), h01(i + 1, 0, j, g)
    r, s = h01(i, 0, j + 1, g), h01(i + 1, 0, j + 1, g)
    t = p + (q - p) * sa
    return t + ((r + (s - r) * sa) - t) * sb


def roche(x, y, z, g=0):
    """Roche naturelle par grandes plaques (style calme) : pierre dominante, andesite, tuf, pierre moussue ; terre vers le haut."""
    b = bruit(x + .7 * y, z - .5 * y, 7, 30 + g)
    c = bruit(x - .4 * y, z + .8 * y, 9, 40 + g)
    if y > 88 and c > .5:
        return TERRE if b < .6 else RAC
    if b < .22:
        return ANDE
    if b > .8:
        return TUF
    if c > .72:
        return MCB
    return PIERRE


# ====================================================================== 1. la gorge et la salle : volumes creux, coquille de roche
xs, ys, zs = np.meshgrid(np.arange(SX), np.arange(SY), np.arange(SZ), indexing="ij")
nz = np.vectorize(lambda y, z, g: bruit(0, z, 11, g))      # parois verticales : pas de rebord ou se poser
nx = np.vectorize(lambda x, y, g: bruit(x, 0, 11, g))
nc = np.vectorize(lambda x, z, g: bruit(x, z, 13, g))
yy, zz = np.meshgrid(np.arange(SY), np.arange(SZ), indexing="ij")
OUEST = 12 + 3 * nz(yy, zz, 1)                         # bords irreguliers de la gorge
EST = 94 - 3 * nz(yy, zz, 2)
xx2, yy2 = np.meshgrid(np.arange(SX), np.arange(SY), indexing="ij")
NORD = 6 + 3 * nx(xx2, yy2, 3)
SUD = 93 - 3 * nx(xx2, yy2, 4)
xx3, zz3 = np.meshgrid(np.arange(SX), np.arange(SZ), indexing="ij")
PLAFOND = 92 + 7 * nc(xx3, zz3, 5)
gorge = (xs > OUEST[None, :, :]) & (xs < EST[None, :, :]) & (zs > NORD[:, :, None]) & (zs < SUD[:, :, None]) & (ys >= 2) & (ys < PLAFOND[:, None, :])
YH = 86                                                # sol de la salle du boss
dome = YH + 6 + 20 * np.sqrt(np.clip(1 - ((xx3 - 44) / 33.0) ** 2 - ((zz3 - 117) / 20.0) ** 2, 0, 1)) + 3 * nc(xx3, zz3, 6)
salle = (np.abs(xs - 44) + 2 * nc(ys, zs, 7) < 31) & (zs > 99 + 2 * nc(xs, ys, 8)) & (zs < 135 - 2 * nc(xs, ys, 9)) & (ys > YH) & (ys < dome[:, None, :])
creux = gorge | salle
coque = np.zeros_like(creux)
d = creux.copy()
for _ in range(4):                                     # 4 blocs de roche tout autour des creux
    n = d.copy()
    n[1:] |= d[:-1]
    n[:-1] |= d[1:]
    n[:, 1:] |= d[:, :-1]
    n[:, :-1] |= d[:, 1:]
    n[:, :, 1:] |= d[:, :, :-1]
    n[:, :, :-1] |= d[:, :, 1:]
    d = n
coque = d & ~creux
coque[:, 0, :] |= (xs[:, 0, :] <= 98) & (zs[:, 0, :] <= 97)          # dalle de fond sous toute la gorge
for x, y, z in zip(*np.nonzero(coque)):
    put(x, y, z, roche(x, y, z))
bord = coque & (np.roll(gorge, 1, 0) | np.roll(gorge, -1, 0) | np.roll(gorge, 1, 2) | np.roll(gorge, -1, 2) | np.roll(gorge, 1, 1) | np.roll(gorge, -1, 1))
for x, y, z in zip(*np.nonzero(bord)):                 # parois : grandes plaques de mousse, a plat (rien qui depasse)
    if bruit(x + .5 * y, z - .3 * y, 8, 70) > .58:
        put(x, y, z, MOUSSE)
print("[1] gorge et salle :", len(blocs), flush=True)

# ====================================================================== 2. la fosse : eau trouble au fond ; y tomber renvoie au dernier point de passage
FOSSE_Y = 20                                           # en dessous, on est dans la fosse
for x, y, z in zip(*np.nonzero(gorge & (ys >= 2) & (ys <= 3))):
    put(x, y, z, EAU)
for x, z in zip(*np.nonzero(gorge[:, 2, :])):
    put(x, 1, z, GROS if bruit(x, z, 6, 11) > .5 else TERRE)

# ====================================================================== 3. massif ouest : entree au sol, escalier en lacets jusqu'a la corniche de depart
for x in range(0, 12):
    for z in range(30, 66):
        for y in range(0, 50):
            put(x, y, z, roche(x, y, z, 1))
ESC = set()
for x in range(0, 5):                                  # couloir d'entree, ouvert sur le dehors (ouest)
    for z in range(41, 44):
        for y in range(1, 4):
            ESC.add((x, y, z))
for k in range(5):                                     # 5 volees de 8 marches, voies alternees (x 2-4 puis 7-9)
    y0 = 8 * k
    voie = range(2, 5) if k % 2 == 0 else range(7, 10)
    for i in range(8):
        z = 44 + i if k % 2 == 0 else 51 - i
        for x in voie:
            put(x, y0 + 1 + i, z, MARCHE_S if k % 2 == 0 else MARCHE_N)
            for y in range(y0 + 2 + i, y0 + 6 + i):
                ESC.add((x, y, z))
    zp = range(52, 55) if k % 2 == 0 else range(41, 44)                  # palier
    for x in range(2, 10):
        for z in zp:
            put(x, y0 + 8, z, MCB)
            for y in range(y0 + 9, y0 + 13):
                ESC.add((x, y, z))
for x in range(2, 19):                                 # tunnel du dernier palier jusqu a la corniche de depart (y 40), a travers toute la paroi
    for z in range(52, 55):
        put(x, 40, z, MCB)
        for y in range(41, 45):
            ESC.add((x, y, z))
for c in ESC:
    blocs.pop(c, None)
for k, (x, y, z) in enumerate(sorted(ESC)):            # eclairage de l'escalier (lumiere invisible)
    if (x + 3 * y + 5 * z) % 23 == 0 and nom((x, y - 1, z)) is not None:
        put(x, y, z, LUM)
print("[3] entree et escalier :", len(ESC), "cases d'air", flush=True)


# ====================================================================== 4. les iles : piliers, corniches, ile suspendue, grand tronc
ILES = {}


def dessus(x, yf, z, g):
    """Dessus d'une ile : mousse, un peu de tapis de mousse, fougeres, un buisson d'azalee de temps en temps."""
    put(x, yf, z, MOUSSE)
    put(x, yf - 1, z, RAC if bruit(x, z, 3, g) > .5 else TERRE)
    v = h01(x, yf, z, g)
    if v < .22:
        put(x, yf + 1, z, TAPIS)
    elif v < .30:
        put(x, yf + 1, z, FOUG)
    elif v < .33:
        put(x, yf + 1, z, AZALEE)


def ile(nom_, cases, yf, g):
    for x, z in cases:
        dessus(x, yf, z, g)
    cx = round(sum(c[0] for c in cases) / len(cases))
    cz = round(sum(c[1] for c in cases) / len(cases))
    put(cx, yf + 3, cz, LUM)
    ILES[nom_] = {"cases": sorted(cases), "yf": yf, "centre": (cx, cz)}


def disque(cx, cz, r, g=0, bosse=0.0):
    return [(x, z) for x in range(int(cx - r - 2), int(cx + r + 3)) for z in range(int(cz - r - 2), int(cz + r + 3))
            if math.hypot(x + .5 - cx, z + .5 - cz) < r + bosse * (bruit(x, z, 3, g) - .5)]


def pilier(nom_, cx, cz, r, yf, g):
    """Pilier de roche qui monte du fond de la fosse, plus large en bas, chapeau de mousse."""
    for y in range(1, yf):
        rr = r + 1.6 * (1 - y / yf) ** 1.5
        for x, z in disque(cx, cz, rr, g + y // 6, 1.2):
            put(x, y, z, roche(x, y, z, g))
    ile(nom_, disque(cx, cz, r, g, 1.0), yf, g)


def suspendue(nom_, cx, cz, r, yf, g):
    """Ile suspendue : cone de roche et de racines sous un dessus de mousse, tenue au plafond par des cordons de racines."""
    haut = int(2 * r + 4)
    for k in range(1, haut):
        rr = r * (1 - k / haut) ** .8
        for x, z in disque(cx, cz, rr, g + k, .8):
            put(x, yf - k, z, RAC if k < 3 else roche(x, yf - k, z, g))
    for x, z in disque(cx, cz, r * .9, g, .8):          # racines pendantes sous le cone
        if h01(x, 0, z, g) < .3:
            y = yf - 1
            while nom((x, y, z)) is not None and y > yf - haut - 2:
                y -= 1
            for k in range(1 + int(4 * h01(x, 1, z, g))):
                if nom((x, y - k, z)) is None:
                    put(x, y - k, z, MRAC if k < 2 else RACP)
    ile(nom_, disque(cx, cz, r, g, .8), yf, g)
    bord = [c for c in ILES[nom_]["cases"] if math.hypot(c[0] + .5 - cx, c[1] + .5 - cz) > r - 1.2]
    for x, z in bord[:: max(1, len(bord) // 3)][:3]:    # 3 cordons jusqu'au plafond
        y = yf + 1
        while y < SY - 1 and gorge[x, y, z] and (x, y, z) not in blocs:
            put(x, y, z, MRAC)
            y += 1


def corniche(nom_, cx, cz, r, yf, vers, g):
    """Corniche accrochee a une paroi : demi-disque de roche, epais de 4 blocs, qui s'amincit vers le bas."""
    dx, dz = vers                                      # direction de la paroi
    cases = [(x, z) for x, z in disque(cx, cz, r, g, 1.0) if (x + .5 - cx) * dx + (z + .5 - cz) * dz > -1.0]
    for k in range(1, 6):
        for x, z in cases:
            if (x + .5 - cx) * dx + (z + .5 - cz) * dz > -1.0 + 1.2 * k:
                put(x, yf - k, z, roche(x, yf - k, z, g))
            elif k == 1:
                put(x, yf - k, z, roche(x, yf - k, z, g))
    for x, z in cases:                                 # la corniche rejoint la paroi
        for s in range(1, 8):
            u, v = x + dx * s, z + dz * s
            if 0 <= u < SX and 0 <= v < SZ and gorge[u, yf, v]:
                put(u, yf, v, roche(u, yf, v, g))
                put(u, yf - 1, v, roche(u, yf - 1, v, g))
            else:
                break
    ile(nom_, cases, yf, g)


# Le trace fait le tour de l'Arbre-Mere, au centre de la gorge : il cache les iles d'en face. Chaque ile est a 13-26 blocs
# de la suivante et a plus de 30 de celle d'apres ; le trace monte en tournant, et le grappin ne hisse pas a la verticale.
CX, CZ = 53, 50
# depart, epreuve 1 (premier saut), epreuve 2 (piliers montants)
corniche("depart", 16, 53, 5.5, 40, (-1, 0), 1)
pilier("P1", 25, 42, 3.5, 41, 2)
pilier("P2", 38, 24, 3.0, 46, 3)
pilier("P3", 52, 20, 3.0, 52, 4)
pilier("P4", 74, 30, 3.5, 58, 5)
# epreuve 3 : l'Arbre-Mere. Tronc geant du fond de la fosse jusque dans le plafond, trois branches-plateformes cote est
TX, TZ = CX, CZ


def rayon_tronc(y):
    return 12.0 if y < 8 else 9.0 + 3.0 * max(0.0, (14 - y) / 6) + (2.5 * (y - 80) / 14 if y > 80 else 0)


for y in range(1, SY - 1):
    rt = rayon_tronc(y)
    for x, z in disque(TX, TZ, rt, 9 + y // 7, 1.0):
        if gorge[x, y, z] or y < 4:
            put(x, y, z, BOIS if math.hypot(x + .5 - TX, z + .5 - TZ) > rt - 1.2 else LOG)
    if y > 4 and not gorge[TX, y, TZ] and y > 60:
        break
for y in range(10, 92, 7):                             # champilumieres dans l'ecorce, sans motif
    a = math.radians(h01(0, y, 0, 9) * 360)
    rt = rayon_tronc(y) - .6
    put(math.floor(TX + rt * math.cos(a)), y, math.floor(TZ + rt * math.sin(a)), CHAMP)
for k in range(9):                                     # racines du plafond : collees a la voute, on ne peut pas s'y poser
    a = math.radians(40 * k + 20 * h01(k, 0, 0, 16))
    for s in range(10, 26):
        x, z = math.floor(TX + s * math.cos(a)), math.floor(TZ + s * math.sin(a))
        y = math.ceil(PLAFOND[x, z]) - 1
        while y > 60 and not gorge[x, y, z]:
            y -= 1
        if gorge[x, y, z] and (x, y + 1, z) in blocs:
            put(x, y, z, BOIS)


def branche(nom_, angle, yf, g, largeur=30, r1=None):
    rt = rayon_tronc(yf)
    r1 = r1 or rt + 5.0
    cases = []
    for x in range(TX - 16, TX + 17):
        for z in range(TZ - 16, TZ + 17):
            rr = math.hypot(x + .5 - TX, z + .5 - TZ)
            a = math.degrees(math.atan2(z + .5 - TZ, x + .5 - TX)) % 360
            if rt - .5 <= rr < r1 + 1.2 * (bruit(x, z, 3, g) - .5) and abs((a - angle + 180) % 360 - 180) < largeur:
                cases.append((x, z))
    for x, z in cases:
        put(x, yf - 1, z, BOIS)
        put(x, yf - 2, z, BOIS if math.hypot(x + .5 - TX, z + .5 - TZ) < r1 - 2 else FEUILLE)
    ile(nom_, cases, yf, g)
    for x, z in cases:                                 # bordure de feuilles sous la branche
        if math.hypot(x + .5 - TX, z + .5 - TZ) > r1 - 1.5 and nom((x, yf - 3, z)) is None:
            put(x, yf - 3, z, FEUILLE)


# l'Arbre-Mere n'a pas de plateforme : il cache les iles d'en face (pas de raccourci a travers la gorge)
# epreuve 3 : deux voies paralleles jusqu'a J. En bas, la cascade (piliers C) ; en haut, la canopee (iles suspendues H)
pilier("C1", 79, 46, 3.0, 60, 23)
pilier("C2", 78, 57, 3.0, 61, 24)
pilier("C3", 71, 69, 3.0, 63, 26)
suspendue("H1", 85, 38, 3.0, 66, 21)
suspendue("H2", 86, 56, 3.0, 70, 22)
suspendue("H3", 79, 72, 3.0, 72, 27)
pilier("J", 60, 80, 4.0, 66, 25)
# epreuve 4 : la traversee ; epreuve 5 : les racines suspendues ; epreuve 6 : l'ascension finale, par marches de 7 blocs
pilier("RA", 40, 78, 3.5, 58, 31)
suspendue("R1", 25, 82, 2.5, 65, 32)
suspendue("R2", 21, 72, 2.5, 73, 33)
corniche("A1", 15, 83, 4.0, 81, (-1, 0), 35)
corniche("F", 26, 88, 4.5, 86, (0, 1), 37)
print("[4] iles :", len(ILES), "|", len(blocs), flush=True)

# ====================================================================== 5. cascade, arche vers la salle, salle du boss
CASC = (79, 64)
for y in range(8, SY - 1):                             # cascade : une source dans le plafond, l'eau tombe dans la fosse
    if not gorge[CASC[0], y, CASC[1]]:
        put(CASC[0], y, CASC[1], EAU)
        for q in ((CASC[0] + 1, y, CASC[1]), (CASC[0] - 1, y, CASC[1]), (CASC[0], y, CASC[1] + 1), (CASC[0], y, CASC[1] - 1), (CASC[0], y + 1, CASC[1])):
            if not gorge[q] and q not in blocs:
                put(*q, roche(*q))
        break
ARCHE = set()
for x in range(22, 31):                                # arche de la derniere corniche vers la salle, a travers la paroi sud
    for z in range(89, 103):
        h = 87 + round(5 * math.sqrt(max(0.0, 1 - ((x + .5 - 26.5) / 4.5) ** 2)))
        for y in range(87, h + 1):
            ARCHE.add((x, y, z))
        if not salle[x, YH + 1, z] and not gorge[x, 87, z]:
            put(x, YH, z, MOUSSE)
for c in ARCHE:
    blocs.pop(c, None)
for x in range(SX):                                    # sol de la salle : mousse, plaques de terre racinee ; racines et colonnes
    for z in range(SZ):
        if salle[x, YH + 1, z]:
            put(x, YH, z, MOUSSE if bruit(x, z, 6, 41) > .3 else RAC)
            v = h01(x, YH, z, 42)
            if v < .12:
                put(x, YH + 1, z, TAPIS)
            elif v < .15:
                put(x, YH + 1, z, FOUG)
for k, (cx, cz, r) in enumerate(((23, 110, 2.5), (66, 112, 3.0), (31, 128, 2.0), (57, 126, 2.5), (45, 104, 1.5))):     # colonnes de troncs, pas en grille
    for y in range(YH + 1, SY - 1):
        if not salle[cx, y, cz]:
            break
        for x, z in disque(cx, cz, r + (1.2 if y < YH + 3 else 0), 50 + k, .5):
            if salle[x, y, z]:
                put(x, y, z, LOG if math.hypot(x + .5 - cx, z + .5 - cz) < r - .7 else BOIS)
for x in range(14, 75, 7):                             # lumiere de la salle : champilumieres au plafond, lumiere invisible
    for z in range(101, 135, 7):
        y = YH + 1
        while y < SY - 1 and salle[x, y, z]:
            y += 1
        if y < SY - 1 and salle[x, y - 1, z]:
            put(x, y - 1, z, CHAMP)
        if salle[x, YH + 4, z]:
            put(x, YH + 4, z, LUM)
print("[5] salle :", int(salle.sum()), "cases | arche", len(ARCHE), flush=True)

# ====================================================================== 6. lierre lumineux qui pend du plafond, lumiere de la gorge
n_lierre = 0
for x in range(13, 94):
    for z in range(7, 93):
        if bruit(x, z, 5, 60) < .62 or h01(x, 0, z, 61) > .4:
            continue
        y = math.ceil(PLAFOND[x, z]) - 1
        while y > 2 and not gorge[x, y, z]:
            y -= 1
        if (x, y + 1, z) not in blocs or (x, y, z) in blocs:
            continue
        long_ = 3 + int(9 * h01(x, 1, z, 62))
        for k in range(long_):
            if (x, y - k, z) in blocs or not gorge[x, y - k, z]:
                long_ = k
                break
        for k in range(long_):
            baie = h01(x, y - k, z, 63) < .35
            fin = k == long_ - 1
            put(x, y - k, z, (LIERRE if baie else LIERRE_NF) if fin else (LIERRE_P if baie else LIERRE_N))
        n_lierre += 1
for x in range(16, 94, 12):                            # lumiere invisible : pas de recoin noir (les monstres n'apparaissent pas)
    for z in range(10, 93, 12):
        for y in range(10, 95, 12):
            if gorge[x, y, z] and (x, y, z) not in blocs:
                put(x, y, z, LUM)
n_lichen = 0
for x, y, z in zip(*np.nonzero(gorge & (ys > 22))):    # lichen luisant a plat sur les parois : lumiere douce, rien a quoi s'accrocher
    if (x, y, z) in blocs or bruit(x + y, z - y, 4, 71) < .8:
        continue
    for (dx, dy, dz), face in (((1, 0, 0), "east"), ((-1, 0, 0), "west"), ((0, 0, 1), "south"), ((0, 0, -1), "north"), ((0, 1, 0), "up")):
        q = (x + dx, y + dy, z + dz)
        if coque[q] and q in blocs:
            put(x, y, z, S("glow_lichen", waterlogged=False, **{face: True}))
            n_lichen += 1
            break
print("[6] lierre", n_lierre, "lichen", n_lichen, flush=True)

# ====================================================================== 7. blocs de commande : grappin a l'entree, points de passage, retour en cas de chute
POINTS = [("depart", "Depart"), ("P4", "Point de passage : l'Arbre-Mere"), ("J", "Point de passage : la traversee"),
          ("RA", "Point de passage : les racines"), ("A1", "Point de passage : l'ascension"), ("salle", "La Salle du Gardien")]
CX0, CY, CZ = 20, YH - 2, 118                          # chaine enterree sous le sol de la salle
CMD = []


def rel(x, y, z):
    px = CX0 + len(CMD)
    return f"~{x - px} ~{y - CY} ~{z - CZ}"


def boite(nom_):
    if nom_ == "salle":
        return (22, YH + 1, 99), (8, 6, 4)             # entree de la salle
    c = ILES[nom_]["cases"]
    x0, z0 = min(a for a, _ in c), min(b for _, b in c)
    return (x0, ILES[nom_]["yf"] + 1, z0), (max(a for a, _ in c) - x0, 3, max(b for _, b in c) - z0)


def arrivee(nom_):
    if nom_ == "salle":
        return (26, YH + 1, 104)
    cx, cz = ILES[nom_]["centre"]
    return (cx, ILES[nom_]["yf"] + 1, cz)


ENTREE = (0, 1, 41)
CMD.append("gamerule commandBlockOutput false")
CMD.append("gamerule doMobSpawning false")
CMD.append("scoreboard objectives add donjon dummy")
CMD.append(f"execute positioned {rel(*ENTREE)} as @a[dx=4,dy=3,dz=2] unless items entity @s container.* grappin:grappin run give @s grappin:grappin")
CMD.append(f"execute positioned {rel(*ENTREE)} as @a[dx=4,dy=3,dz=2] unless score @s donjon matches 0.. run scoreboard players set @s donjon 0")
for k, (n_, texte) in enumerate(POINTS[1:], 1):
    (bx, by, bz), (dx, dy, dz) = boite(n_)
    CMD.append(f'execute positioned {rel(bx, by, bz)} as @a[dx={dx},dy={dy},dz={dz},scores={{donjon=..{k - 1}}}] run title @s actionbar {{"text":"{texte}","color":"gold"}}')
    CMD.append(f"execute positioned {rel(bx, by, bz)} as @a[dx={dx},dy={dy},dz={dz},scores={{donjon=..{k - 1}}}] run scoreboard players set @s donjon {k}")
FOSSE = (12, 1, 6)                                     # toute la fosse sous Y local 20
for k, (n_, _) in enumerate(POINTS[:-1]):
    ax, ay, az = arrivee(n_)
    CMD.append(f"execute positioned {rel(*FOSSE)} as @a[dx=82,dy={FOSSE_Y - 1},dz=87,scores={{donjon={k}}}] run tp @s ~{ax - FOSSE[0]} ~{ay - FOSSE[1]} ~{az - FOSSE[2]}")
ax, ay, az = arrivee("depart")
CMD.append(f"execute positioned {rel(*FOSSE)} as @a[dx=82,dy={FOSSE_Y - 1},dz=87] unless score @s donjon matches 0..4 run tp @s ~{ax - FOSSE[0]} ~{ay - FOSSE[1]} ~{az - FOSSE[2]}")
for i, c in enumerate(CMD):
    x = CX0 + i
    put(x, CY, CZ, S("repeating_command_block" if i == 0 else "chain_command_block", conditional=False, facing="east"))
    NBT[(x, CY, CZ)] = Compound({"id": String("minecraft:command_block"), "Command": String(c), "auto": Byte(1), "TrackOutput": Byte(0),
                                 "conditionMet": Byte(0), "powered": Byte(0), "UpdateLastExecution": Byte(1)})
    assert not salle[x, CY, CZ] and not gorge[x, CY, CZ]
print("[7] commandes :", len(CMD), flush=True)

# ====================================================================== 8. controles et ecriture
err = []
PASSE = {"air", "light", "moss_carpet", "fern", "short_grass", "flowering_azalea", "cave_vines", "cave_vines_plant", "hanging_roots", "water"}
for n_, d_ in ILES.items():                            # chaque ile a un dessus ou l'on tient debout
    libres = [c for c in d_["cases"] if nom((c[0], d_["yf"] + 1, c[1])) in (None, *PASSE) and nom((c[0], d_["yf"] + 2, c[1])) in (None, *PASSE)]
    if len(libres) < 6:
        err.append(f"ile {n_} : trop peu de place ({len(libres)})")
for (x, y, z), s in blocs.items():
    if s[0].endswith("water") and (x, z) != CASC:
        for q in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1), (x, y - 1, z)):
            if q not in blocs and not (gorge[q] if all(0 <= q[i] < (SX, SY, SZ)[i] for i in range(3)) else False):
                err.append(f"eau qui fuit {(x, y, z)} -> {q}")
print("[controle] erreurs", len(err), err[:6], flush=True)
if err:
    raise SystemExit(1)

palette, index, liste = [], {}, []
for (x, y, z) in sorted(blocs, key=lambda p: (p[1], p[2], p[0])):
    s = blocs[(x, y, z)]
    if s not in index:
        index[s] = len(palette)
        c = Compound({"Name": String(s[0])})
        if s[1]:
            c["Properties"] = Compound({k: String(v) for k, v in s[1]})
        palette.append(c)
    e = Compound({"pos": List[Int]([Int(x), Int(y), Int(z)]), "state": Int(index[s])})
    if (x, y, z) in NBT:
        e["nbt"] = NBT[(x, y, z)]
    liste.append(e)
racine = nbtlib.File({"DataVersion": Int(3955), "size": List[Int]([Int(SX), Int(SY), Int(SZ)]), "palette": List[Compound](palette),
                      "blocks": List[Compound](liste), "entities": List[Compound]([])}, gzipped=True)
racine.save(RACINE / "exports" / f"{NOM}.nbt")
if (SAVE / "level.dat").exists():
    D = SAVE / "datapacks/atelier"
    (D / "data/atelier/structure").mkdir(parents=True, exist_ok=True)
    racine.save(D / "data/atelier/structure" / f"{NOM}.nbt")
    if not (D / "pack.mcmeta").exists():
        (D / "pack.mcmeta").write_text('{"pack": {"pack_format": 48, "description": "Atelier [claude-build]"}}\n', encoding="utf-8")
    print("datapack ecrit dans", args.monde)
else:
    print(f"ATTENTION : monde {args.monde} introuvable (structure seulement dans exports)")
ORDRE = ["depart", "P1", "P2", "P3", "P4", "C1", "C2", "C3", "H1", "H2", "H3", "J", "RA", "R1", "R2", "A1", "F"]
ci = {n_: [ILES[n_]["centre"][0], ILES[n_]["yf"] + 1, ILES[n_]["centre"][1]] for n_ in ORDRE}
ci["salle"] = [44, YH + 1, 117]
(RACINE / "exports" / f"{NOM}_cibles.json").write_text(json.dumps({"_marque": "[claude-build]", "depart": [1, 1, 42], "fosse_y": FOSSE_Y,
                                                                    "ordre": ORDRE + ["salle"], "cibles": ci,
                                                                    "iles": {n_: {"cases": ILES[n_]["cases"], "yf": ILES[n_]["yf"]} for n_ in ILES}},
                                                                   ensure_ascii=False), encoding="utf-8")
print(f"Ecrit : {len(liste)} blocs, palette {len(palette)}, taille {SX}x{SY}x{SZ}")
print(f"POSE : /tp @s 0 60 -80   puis   /place template atelier:{NOM} {ORIGINE[0]} {ORIGINE[1]} {ORIGINE[2]}")


def w(p):
    return f"{ORIGINE[0] + p[0]} {ORIGINE[1] + p[1]} {ORIGINE[2] + p[2]}"


EPREUVES = [("1. Le premier saut", "depart → P1", "un vide de 8 blocs, au même niveau : on apprend le grappin"),
            ("2. Les piliers montants", "P1 → P2 → P3 → P4", "chaque pilier 5 à 6 blocs plus haut que le précédent"),
            ("3. Les deux voies", "P4 → J", "en bas, 3 piliers le long de la cascade (C1, C2, C3) ; en haut, 3 îles suspendues au plafond (H1, H2, H3) ; on peut changer de voie"),
            ("4. La traversée", "J → RA", "le plus long vide du donjon, en descendant"),
            ("5. Les racines suspendues", "RA → R1 → R2", "deux petites îles pendues au plafond, 7 blocs plus haut à chaque fois"),
            ("6. L'ascension", "R2 → A1 → F", "deux corniches accrochées aux parois, jusqu'à l'arche de la salle"),
            ("7. La Salle du Gardien", "F → salle", "grande salle naturelle, colonnes de troncs, sol de mousse ; le boss viendra plus tard")]
md = ["<!-- [claude-build] genere par sources/donjon_grappin.py -->", "# Donjon n°1 — La Gorge suspendue (grappin)", "",
      f"Monde `saves/{args.monde}` (superflat par défaut, sol à Y-61). Structure de {SX} × {SY} × {SZ}, posée en {ORIGINE[0]} {ORIGINE[1]} {ORIGINE[2]}. "
      "Il faut le mod Grappin 1.1 (et NeoForge).", "",
      "## Principe", "",
      "- Une gorge de jungle fermée, avec l'Arbre-Mère au centre, qui monte du fond jusqu'au plafond. On avance d'île en île au grappin. Sans grappin, on ne quitte pas la corniche de départ (vérifié).",
      "- Au fond, une fosse d'eau trouble : y tomber renvoie au dernier point de passage (départ, P4, J, RA, A1).",
      "- L'Arbre-Mère cache les îles d'en face, et le trace monte en tournant. Les raccourcis possibles sautent au plus une ou deux îles, avec des tirs à la limite de la portée.",
      f"- Le grappin est donné à l'entrée à qui n'en a pas. Les créatures n'apparaissent pas (blocs de commande, {len(CMD)} commandes sous le sol de la salle).", "",
      "## Épreuves", ""]
md += [f"- **{a}** ({b}) : {c}." for a, b, c in EPREUVES]
md += ["", "## Coordonnées", "", f"- Entrée, au sol, côté ouest : {w((0, 1, 42))}. Escalier en 5 volées jusqu'à la corniche de départ.",
       f"- Corniche de départ : {w((ILES['depart']['centre'][0], ILES['depart']['yf'] + 1, ILES['depart']['centre'][1]))}.",
       f"- Salle du Gardien : centre {w((44, YH + 1, 117))}, entrée par l'arche {w((26, YH + 1, 95))}.", "",
       "| Île | Où l'on se tient |", "|---|---|"]
md += [f"| {n_} | {w((ILES[n_]['centre'][0], ILES[n_]['yf'] + 1, ILES[n_]['centre'][1]))} |" for n_ in ORDRE]
md += ["", "Plan : `exports/donjon_grappin_plan.png` (réservé aux organisateurs).", "",
       "## Contrôle", "", "`sources/verif_grappin.py exports/donjon_grappin.nbt exports/donjon_grappin_cibles.json` : impossible sans grappin, possible avec, "
       "et la liste de tous les tirs possibles entre îles (portée 30, ligne de vue, passage du corps, bond d'arrivée du grappin 1.1)."]
(RACINE / "donjon_grappin.md").write_text("\n".join(md) + "\n", encoding="utf-8")
