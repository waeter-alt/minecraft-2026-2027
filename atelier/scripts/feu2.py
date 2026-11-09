# [claude-build]
"""
feu2.py - Arene Feu, 2e de la serie des 7, Ø104 : terrain accidente, rivieres et fosses de lave, souterrain,
cercle de feu elargi (rayon 22), 6 tours. Sol Y86, centre X0 Z0. Style calme : grandes plaques, formes naturelles.
2026-11-08 : caverne centrale sous le cercle de feu avec un lac de lave (Y64), parois du souterrain explicites
(melange blackstone, netherrack, basalte, magma, briques craquelees en plaques et veines) : plus de terre visible quel que soit le monde.
  .venv\\Scripts\\python.exe sources\\feu2.py
Controle : sources\\verif_acces.py exports\\feu2.nbt exports\\feu2_cibles.json
"""
import json
import math
import random
from pathlib import Path

import nbtlib
import numpy as np
from nbtlib import Compound, Int, List, String

RACINE = Path(__file__).resolve().parent.parent
SAVES = RACINE / "launcher/instances/Build-1.21.1/minecraft/saves"
SAVE = SAVES / "Arene Feu"
NOM = "feu2"
SX = SZ = 104
SOL = 18
SY = 66
BAS_EXT = 12                                           # le fichier descend de 12 blocs sous Y68 pour loger le lac (y locaux negatifs)
C = 52
CEN = 52.0
R = 52.0
R_MUR = 48.0
R_FEU = 22.0                                           # etait 19
ORIGINE = (-52, 86 - SOL, -52)
PORTE = 270                                            # nord, vers le hub
rng = random.Random(7042)


def S(n, **p):
    return ("minecraft:" + n, tuple(sorted((k, str(v).lower() if isinstance(v, bool) else str(v)) for k, v in p.items())))


AIR = S("air")
NETH, BLK, PBK, BKB, CBK = S("netherrack"), S("blackstone"), S("polished_blackstone"), S("polished_blackstone_bricks"), S("cracked_polished_blackstone_bricks")
BAS, SBAS, MAGMA, OBS, CRY = S("basalt", axis="y"), S("smooth_basalt"), S("magma_block"), S("obsidian"), S("crying_obsidian")
NB, RNB, CNB, GILD = S("nether_bricks"), S("red_nether_bricks"), S("cracked_nether_bricks"), S("gilded_blackstone")
SSOIL, SSAND, CDIRT = S("soul_soil"), S("soul_sand"), S("coarse_dirt")
LAVE, CHUTE = S("lava", level=0), S("lava", level=8)
FEU_N, FEU_A = S("fire", age=0), S("soul_fire")
SHROOM = S("shroomlight")
LANT = S("soul_lantern", hanging=False, waterlogged=False)
LANT_P = S("soul_lantern", hanging=True, waterlogged=False)
CHAINE = S("chain", axis="y", waterlogged=False)
BARRIERE = S("barrier", waterlogged=False)
TRAPPE = S("dark_oak_trapdoor", facing="north", half="top", open=False, powered=False, waterlogged=False)
DALLE = S("polished_blackstone_slab", type="bottom", waterlogged=False)
LUMIERE = S("light", level=15, waterlogged=False)
blocs = {}
occ = {}
SALLES, TOURS, ACCES, APPS = [], [], [], []
CIBLES = {}


def put(x, y, z, s):
    if x != int(x) or y != int(y) or z != int(z):
        raise ValueError(f"coordonnee non entiere {(x, y, z)}")
    x, y, z = int(x), int(y), int(z)
    if not (0 <= x < SX and -BAS_EXT <= y < SY and 0 <= z < SZ):
        raise ValueError(f"hors gabarit {(x, y, z)} {s[0]}")
    blocs[(x, y, z)] = s


def nom(p):
    s = blocs.get(p)
    return None if s is None else s[0].split(":")[1]


def vide(p):
    n = nom(p)
    return (p[1] > SOL) if n is None else n == "air"


NON_SOLIDES = {"air", "lava", "fire", "soul_fire", "soul_lantern", "chain", "light", "ladder", "dark_oak_trapdoor"}


def solide(p):
    n = nom(p)
    return (p[1] <= SOL) if n is None else n not in NON_SOLIDES


def dist(x, z):
    return math.hypot(x + .5 - CEN, z + .5 - CEN)


def ang(x, z):
    return math.degrees(math.atan2(z + .5 - CEN, x + .5 - CEN)) % 360


def adiff(a, b):
    return abs((a - b + 180) % 360 - 180)


def point(r, a):
    a = math.radians(a)
    return CEN + r * math.cos(a), CEN + r * math.sin(a)


def cellule(r, a):
    x, z = point(r, a)
    return math.floor(x), math.floor(z)


def h01(x, y, z, g=0):
    h = (int(x) * 374761393 + int(y) * 1442695041 + int(z) * 668265263 + g * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h ^= h >> 16
    return h / 0xFFFFFFFF


def bruit(x, z, cel, g):
    gx, gz = x / cel, z / cel
    i, j = math.floor(gx), math.floor(gz)
    fx, fz = gx - i, gz - j
    sx, sz = fx * fx * (3 - 2 * fx), fz * fz * (3 - 2 * fz)
    a, b = h01(i, 0, j, g), h01(i + 1, 0, j, g)
    c, d = h01(i, 0, j + 1, g), h01(i + 1, 0, j + 1, g)
    t = a + (b - a) * sx
    return t + ((c + (d - c) * sx) - t) * sz


def disque(cx, cz, r):
    for x in range(math.floor(cx - r - 1), math.ceil(cx + r) + 1):
        for z in range(math.floor(cz - r - 1), math.ceil(cz + r) + 1):
            if math.hypot(x + .5 - cx, z + .5 - cz) <= r:
                yield x, z


def gl(al, sp, g):
    return {0: (C + al, C + sp), 90: (C - 1 - sp, C + al), 180: (C - 1 - al, C - 1 - sp), 270: (C + sp, C - 1 - al)}[g % 360]


def monde(x, y, z):
    return ORIGINE[0] + x, ORIGINE[1] + y, ORIGINE[2] + z


def percer(cx, cz, a, r0, r1, y, h, w=1, sol=None):
    ux, uz = math.cos(math.radians(a)), math.sin(math.radians(a))
    r_ = r0
    while r_ <= r1:
        for s in range(-w, w + 1):
            x = math.floor(cx + .5 + ux * r_ - uz * s)
            z = math.floor(cz + .5 + uz * r_ + ux * s)
            for yy in range(y, y + h):
                put(x, yy, z, AIR)
            if sol:
                put(x, y - 1, z, sol)
        r_ += 0.4


# ====================================================================== plan : emplacements fixes
ANG_TOURS = [15, 75, 135, 195, 255, 315]
ANG_RIV = [45, 165, 285]
PUITS, CRATERE, FAILLE = 105, 225, 345                 # 3 acces ; le 4e descend dans la tour de 15 degres
R_T = 33
CENT_T = {a: cellule(R_T, a) for a in ANG_TOURS}
FOSSES = [(42, 105, 3.2), (42, 225, 3.2), (42, 328, 3.0), (41, 45 + 20, 2.6), (41, 165 - 20, 2.6)]
PONTS_RIV = [(a, 36) for a in ANG_RIV]

# ====================================================================== 1. terrain
ix, iz = np.meshgrid(np.arange(SX), np.arange(SZ), indexing="ij")
RR = np.hypot(ix + .5 - CEN, iz + .5 - CEN)
N1 = np.array([[bruit(x, z, 15, 1) for z in range(SZ)] for x in range(SX)])
N2 = np.array([[bruit(x, z, 7, 2) for z in range(SZ)] for x in range(SX)])
hf = np.clip((N1 - 0.3) * 34 + (N2 - 0.5) * 6, 0, 12)
H = np.rint(hf).astype(int)
H[RR < 25] = 0
H[RR >= 47] = np.minimum(H[RR >= 47], 4)
axes = np.minimum(np.abs(ix + .5 - CEN), np.abs(iz + .5 - CEN))
H[(axes <= 3.5) & (ix < 200)] = np.minimum(H[(axes <= 3.5) & (ix < 200)], 2)
for a, c in CENT_T.items():
    H[np.hypot(ix - c[0], iz - c[1]) <= 8] = 0
for a in (PUITS, CRATERE, FAILLE):
    x, z = cellule(33, a)
    H[np.hypot(ix - x, iz - z) <= 7] = 0
for r_, a, rad in FOSSES:
    x, z = cellule(r_, a)
    H[np.hypot(ix - x, iz - z) <= rad + 3] = 0
for a, r_ in PONTS_RIV:
    for da in (-8, 0, 8):
        x, z = cellule(r_, a + da)
        H[np.hypot(ix - x, iz - z) <= 5] = 0
H[(RR >= 38) & (axes <= 7) & (iz > 60)] = 0            # massif de la salle du mur, au sud
H[(RR >= 36) & (axes <= 7) & (iz < 44)] = 0            # arrivee de la porte, au nord
for _ in range(14):                                    # pentes praticables : 1 bloc de marche au plus
    P = np.pad(H, 1, mode="edge")
    H = np.minimum(H, np.minimum.reduce([P[:-2, 1:-1], P[2:, 1:-1], P[1:-1, :-2], P[1:-1, 2:]]) + 1)
Hm = SOL + H


def Hs(x, z):
    return int(Hm[x, z])


lave_sol = np.zeros((SX, SZ), bool)
for a in ANG_RIV:                                      # rivieres de lave : de l'anneau au rempart
    pts = [point(r_, a + 9 * math.sin(r_ / 6)) for r_ in np.arange(21, 47.5, 0.5)]
    for px, pz in pts:
        lave_sol |= np.hypot(ix + .5 - px, iz + .5 - pz) <= 1.9
for r_, a, rad in FOSSES:
    px, pz = point(r_, a)
    lave_sol |= np.hypot(ix + .5 - px, iz + .5 - pz) <= rad
lave_sol |= (RR >= 14) & (RR < R_FEU)
lave_sol &= RR < 47.5
ponts_cells = set()
for a, r_ in PONTS_RIV:                                # ponts : passent toutes les rivieres
    x0 = a + 9 * math.sin(r_ / 6)
    for da in np.arange(-9, 9.2, 0.4):
        px, pz = point(r_, x0 + da)
        ponts_cells |= {c for c in disque(px, pz, 1.7)}
for g in (0, 90, 180, 270):
    ponts_cells |= {gl(al, sp, g) for al in range(12, 24) for sp in range(-3, 3)}

for x in range(SX):
    for z in range(SZ):
        d = RR[x, z]
        if d >= R_MUR + 0.5:
            continue
        if lave_sol[x, z]:
            put(x, SOL, z, AIR)
            for y in range(SOL + 1, Hs(x, z) + 1):
                pass
            put(x, SOL - 1, z, LAVE)
            put(x, SOL - 2, z, LAVE)
            put(x, SOL - 3, z, BLK)
            continue
        h = Hs(x, z)
        n = bruit(x, z, 12, 3)
        chemin = axes[x, z] <= 2.6 or abs(d - 24) < 1.6
        if d < 14:
            s = PBK
        elif chemin:
            s = PBK if bruit(x, z, 9, 4) < 0.66 else BKB
        elif n > 0.6:
            s = SSOIL                                 # le tiers de moins : plaques isolees au lieu de grandes surfaces
        elif n < 0.26:
            s = BLK
        elif bruit(x, z, 10, 5) > 0.7:
            s = SBAS
        else:
            s = NETH
        put(x, h, z, s)
        for y in range(h - 1, SOL - 1, -1):
            put(x, y, z, BLK if bruit(x, y * 3, 8, 6) > 0.5 else NETH)
print("[1] terrain :", len(blocs), "blocs, hauteur max", int(H.max()), flush=True)


# ====================================================================== 2. rempart et porte
for x in range(SX):
    for z in range(SZ):
        d = RR[x, z]
        if d < R_MUR or d >= R:
            continue
        a = ang(x, z)
        hm = SOL + 13 + int(3 * bruit(a, 0, 11, 7))
        for y in range(SOL, hm + 1):
            put(x, y, z, BKB if bruit(a / 2, y, 8, 8) > 0.45 else NB)
        put(x, hm + 1, z, RNB)
        if d >= 50.5 and int(a * 1.5) % 2 == 0:
            put(x, hm + 2, z, BLK)
        if d >= 51:
            for y in range(hm + 2, SY):
                if vide((x, y, z)):
                    put(x, y, z, BARRIERE)
for sp in range(-9, 9):
    for al in range(43, 52):
        x, z = gl(al, sp, PORTE)
        if dist(x, z) >= R:
            continue
        cote = sp < -5 or sp > 4
        for y in range(SOL + 1, SOL + 16):
            if cote or y > SOL + 9:
                put(x, y, z, RNB if y == SOL + 15 else BKB if (y - SOL) % 5 else NB)
            else:
                put(x, y, z, AIR)
        if not cote:
            put(x, SOL, z, PBK)
        occ[(x, z)] = "porte"
for sp in (-8, 7):
    x, z = gl(44, sp, PORTE)
    put(x, SOL + 16, z, MAGMA)
print("[2] rempart :", len(blocs), flush=True)


# ====================================================================== 3. souterrain
FOND = 6
HAUT = 13
LACS = [(60, 26), (165, 26), (285, 26)]
gal = {}
for x in range(SX):
    for z in range(SZ):
        d = RR[x, z]
        if 27.5 <= d <= 38.5 + 1.2 * (bruit(x, z, 7, 9) - 0.5):
            gal[(x, z)] = (FOND + (1 if bruit(x, z, 8, 10) > 0.7 else 0), HAUT + int(2 * bruit(x, z, 6, 11)))
for (x, z), (f, h) in gal.items():
    a = ang(x, z)
    d = RR[x, z]
    lac = any(adiff(a, c) <= s for c, s in LACS)
    pont = abs(d - 33) < 1.4
    if lac and not pont:
        put(x, FOND, z, LAVE)
        put(x, FOND - 1, z, LAVE)
        put(x, FOND - 2, z, BLK)
        for y in range(FOND + 1, h + 1):
            put(x, y, z, AIR)
        put(x, h + 1, z, SBAS)
        continue
    put(x, f, z, BLK if bruit(x, z, 9, 12) > 0.45 else BAS)
    if lac and pont:
        f = FOND
        put(x, FOND, z, BKB)
    for y in range(f + 1, h + 1):
        put(x, y, z, AIR)
    put(x, h + 1, z, SBAS if bruit(x, z, 7, 13) > 0.4 else MAGMA)
for c, s in LACS:                                      # sauts sur le pont du lac central : 2 trous
    if c == 165:
        for da in (-9, 9):
            for x, z in disque(*point(33, c + da), 1.2):
                if (x, z) in gal:
                    put(x, FOND + 1, z, AIR)
                    put(x, FOND, z, LAVE)
                    put(x, FOND - 1, z, LAVE)
                    put(x, FOND - 2, z, BLK)
# chutes de lave confinees : source dans une encoche du plafond, colonne jusqu'au lac
n_ch = 0
for c, s in LACS:
    for da in (-12, 11):
        x, z = cellule(30.5 if da < 0 else 35.5, c + da)
        if nom((x, FOND, z)) == "lava":
            h = gal[(x, z)][1]
            for y in range(FOND + 1, h + 1):
                put(x, y, z, CHUTE)
            put(x, h + 1, z, LAVE)
            n_ch += 1
for i in range(40):                                    # piliers et stalactites de basalte
    a = rng.uniform(0, 360)
    x, z = cellule(rng.uniform(29, 37), a)
    if (x, z) in gal and nom((x, FOND, z)) != "lava" and abs(RR[x, z] - 33) > 2 and not any(adiff(a, b) < 8 for b in (15, PUITS, CRATERE, FAILLE)):
        f, h = gal[(x, z)]
        if i % 3 == 0:
            for y in range(f + 1, h + 1):
                put(x, y, z, BAS)
        else:
            for y in range(h, h - 2 - i % 2, -1):
                put(x, y, z, BAS)
print("[3] souterrain :", len(gal), "colonnes, chutes de lave", n_ch, flush=True)

# ====================================================================== 3b. caverne centrale et lac de lave (2026-11-08)
# Sous le centre : lac de lave irregulier, plages, falaises, corniche qui fait le tour (contournement sans lave),
# 2 rampes de la plage a la corniche, pont de blackstone en partie effondre, ilots pour sauter, ilot central, 3 chutes confinees.
rng_c = random.Random(8080)                            # tirage propre a la caverne : le decor de surface reste identique
Y_LAVE = FOND - 10                                     # surface du lac : Y64
Y_HAUT = FOND + 4                                      # plafond : air jusqu'a Y78, 5 blocs de roche sous le cercle de feu
Y_HAUT_SALLE = FOND + 2                                # sous la salle cachee de l'autel : air jusqu'a Y76 (12 blocs au-dessus de la lave)
SALLE3 = {(x, z) for x in range(C + 1, C + 12) for z in range(C + 1, C + 12)}
CAV, LAC, ILOT, PONT, PIERRES, CORNICHE = {}, set(), set(), set(), set(), set()
TUNNELS = [30, 125, 240, 330]
RAMPES = [(80, 40), (280, 40)]                         # angle de depart, ouverture : le sol monte de la plage a la corniche


def circ(a, cel, g):
    r_ = math.radians(a)
    return bruit(20 + 12 * math.cos(r_), 20 + 12 * math.sin(r_), cel, g)


def r_lac(a):
    return 13.6 + 6 * (circ(a, 9, 51) - .5) + 2.5 * (circ(a, 4, 52) - .5)


def r_plage(a):
    return r_lac(a) + 2.2 + 2.5 * circ(a, 6, 53)


def r_cav(a):
    return 21.2 + 3 * (circ(a, 7, 54) - .5)


for x in range(C - 25, C + 25):
    for z in range(C - 25, C + 25):
        d, a = dist(x, z), ang(x, z)
        rc = r_cav(a)
        if d >= rc:
            continue
        haut = Y_HAUT_SALLE if (x, z) in SALLE3 else Y_HAUT - (1 if bruit(x, z, 5, 55) > 0.72 else 0)
        rampe = next((min(1, (a - a0) % 360 / ouv) for a0, ouv in RAMPES if (a - a0) % 360 <= ouv), None)
        if d < r_lac(a):
            sol_ = Y_LAVE
            LAC.add((x, z))
            put(x, Y_LAVE - 2, z, BLK)
            put(x, Y_LAVE - 1, z, LAVE)
            put(x, Y_LAVE, z, LAVE)
        elif d < r_plage(a):
            sol_ = Y_LAVE                               # plage de blackstone, au ras de la lave
        elif rampe is not None:
            sol_ = Y_LAVE + round((FOND - Y_LAVE) * rampe)
        elif d < rc - 3:
            continue                                    # falaise : la roche reste
        else:
            sol_ = FOND
            CORNICHE.add((x, z))
        CAV[(x, z)] = (sol_, haut)
        if (x, z) not in LAC:
            put(x, sol_, z, BLK if bruit(x, z, 6, 56) > 0.35 else BAS)
        for y in range(sol_ + 1, haut + 1):
            put(x, y, z, AIR)
for a in TUNNELS:                                      # les galeries de l'anneau debouchent sur la corniche
    percer(CEN - .5, CEN - .5, a, r_cav(a) - 2.5, 29, FOND + 1, 4, 1, BLK)
# ilot central : point fort, piliers et braseros
for x, z in disque(CEN, CEN, 3.9):
    if math.hypot(x + .5 - CEN, z + .5 - CEN) <= 3.2 + 0.8 * bruit(x, z, 3, 57):
        put(x, Y_LAVE + 1, z, PBK if bruit(x, z, 4, 58) > 0.4 else BLK)
        put(x, Y_LAVE, z, BLK)
        ILOT.add((x, z))
for u, v, h in ((-3, -1, 4), (2, -3, 2), (1, 2, 5)):
    for y in range(Y_LAVE + 2, Y_LAVE + 2 + h):
        put(C + u, y, C + v, BKB if y < Y_LAVE + 1 + h else CBK)
for u, v in ((-1, 2), (2, 0)):
    put(C + u, Y_LAVE + 2, C + v, NETH)
    put(C + u, Y_LAVE + 3, C + v, FEU_N)
# pont de blackstone en partie effondre, d'une plage a l'autre en passant par l'ilot (deux trous a sauter)
for sens in (20, 200):
    ux, uz = math.cos(math.radians(sens)), math.sin(math.radians(sens))
    t = 3.0
    while t < r_plage(sens) + 0.5:
        trou = 6.2 <= t <= 7.6 if sens == 20 else 8.4 <= t <= 10.8
        for s in (-0.6, 0.6):
            x, z = math.floor(CEN + ux * t - uz * s), math.floor(CEN + uz * t + ux * s)
            if (x, z) in LAC and (x, z) not in ILOT and not trou:
                put(x, Y_LAVE, z, BLK if bruit(x, z, 3, 59) > 0.3 else CBK)
                PONT.add((x, z))
        t += 0.4
# ilots pour sauter, de la plage sud-est jusqu'a l'ilot
for k, t in enumerate((12.6, 9.3, 6.0)):
    px, pz = point(t, 112)
    for x, z in ((math.floor(px), math.floor(pz)), (math.floor(px) + 1, math.floor(pz)), (math.floor(px), math.floor(pz) + 1), (math.floor(px) + 1, math.floor(pz) + 1)):
        if (x, z) in LAC and (x, z) not in ILOT:
            put(x, Y_LAVE - 2, z, BLK)
            put(x, Y_LAVE - 1, z, BAS)
            put(x, Y_LAVE, z, BAS)
            PIERRES.add((x, z))
# chutes de lave confinees : source dans une encoche du plafond, colonne jusqu'au lac
n_ch2 = 0
for r_, a in ((8, 150), (10, 262), (7, 330)):
    x, z = cellule(r_, a)
    if (x, z) in LAC and (x, z) not in PONT | PIERRES | ILOT:
        haut = CAV[(x, z)][1]
        for y in range(Y_LAVE + 1, haut + 1):
            put(x, y, z, CHUTE)
        put(x, haut + 1, z, LAVE)
        n_ch2 += 1
# stalactites de basalte
for i in range(160):
    a = rng_c.uniform(0, 360)
    x, z = cellule(rng_c.uniform(4, 19), a)
    if (x, z) not in CAV or (x, z) in CORNICHE or (x, z) in PONT or (x, z) in ILOT or any(nom((x + u, CAV[(x, z)][1], z + v)) == "lava" for u in (0, 1) for v in (0, 1)):
        continue                                        # jamais sur une chute de lave : la source doit rester
    sol_, haut = CAV[(x, z)]
    long_ = rng_c.randint(2, 5)
    for k in range(long_):
        for u, v in ((0, 0), (1, 0), (0, 1), (1, 1)) if k < long_ // 2 else ((0, 0),):
            if (x + u, z + v) in CAV and haut - k > CAV[(x + u, z + v)][0] + 3:
                put(x + u, haut - k, z + v, BAS)
CIBLES["îlot central"] = [C + 2, Y_LAVE + 2, C - 2]
for a in (20, 200):
    x, z = cellule(r_plage(a) - 0.8, a)
    CIBLES[f"plage, angle {a}"] = [x, Y_LAVE + 1, z]
for a in TUNNELS:
    x, z = cellule(r_cav(a) - 1.5, a)
    CIBLES[f"corniche, débouché de la galerie {a}"] = [x, FOND + 1, z]
print("[3b] caverne :", len(CAV), "colonnes, lac", len(LAC), "cases, chutes", n_ch2, flush=True)



def spirale(cx, cz, r0, r1, ybas, montee, pitch, a0, plein, dalle):
    pas = 360 / (2 * pitch)
    tmax = montee * 2
    for x in range(math.floor(cx - r1 - 1), math.ceil(cx + r1) + 2):
        for z in range(math.floor(cz - r1 - 1), math.ceil(cz + r1) + 2):
            d = math.hypot(x - cx, z - cz)
            if not r0 <= d <= r1:
                continue
            t0 = (math.degrees(math.atan2(z - cz, x - cx)) - a0) % 360
            k = 0
            while True:
                tot = t0 + 360 * k
                k += 1
                if tot > tmax * pas + pas:
                    break
                h2 = min(tmax, int(tot // pas))
                if h2 == 0:
                    continue
                if h2 % 2:
                    put(x, ybas + 1 + (h2 - 1) // 2, z, dalle)
                else:
                    put(x, ybas + h2 // 2, z, plein)


def plancher(cx, cz, y, ro, montee, a0, pitch, mat):
    pas = 360 / (2 * pitch)
    for x in range(math.floor(cx - ro - 1), math.ceil(cx + ro) + 2):
        for z in range(math.floor(cz - ro - 1), math.ceil(cz + ro) + 2):
            d = math.hypot(x - cx, z - cz)
            if d > ro:
                continue
            t0 = (math.degrees(math.atan2(z - cz, x - cx)) - a0) % 360
            dernier = t0 + 360 * ((montee * 2 * pas - t0) // 360)
            if 1.6 <= d <= 3.7 and dernier >= (montee - 5) * 2 * pas:
                continue
            put(x, y, z, mat)


def puits_helice(cx, cz, ytop, a0):
    """colimacon creuse dans la roche, du souterrain jusqu'a ytop"""
    h = ytop - FOND
    for x, z in disque(cx + .5, cz + .5, 3.9):
        for y in range(FOND, ytop + 1):
            put(x, y, z, AIR)
        put(x, FOND, z, BKB)
    for x, z in disque(cx + .5, cz + .5, 1.5):
        for y in range(FOND + 1, ytop + 1):
            put(x, y, z, BAS)
    spirale(cx, cz, 1.6, 3.7, FOND, h, 7, a0, PBK, DALLE)


# ====================================================================== 4. tours
STYLES = [("carre", SOL + 24, BKB, NB, "tour de garde"), ("rond", SOL + 18, BLK, CBK, "tour en ruine"), ("rond", SOL + 34, BAS, SBAS, "flèche de basalte"),
          ("carre", SOL + 28, NB, RNB, "tour de nether bricks"), ("carre", SOL + 15, CBK, BLK, "poste de guet"), ("rond", SOL + 31, OBS, CRY, "tour d'obsidienne")]


USE = {"polished_blackstone_bricks": CBK, "nether_bricks": CNB, "red_nether_bricks": CNB, "basalt": SBAS, "blackstone": CBK, "obsidian": CRY,
       "cracked_polished_blackstone_bricks": BLK}


def use(mat, x, y, z, g):
    """pierre usee par grandes plaques"""
    return USE.get(mat[0].split(":")[1], mat) if bruit(x + 2 * y, z - y, 5, g) > 0.56 else mat


def tour(i, a):
    cx, cz = CENT_T[a]
    forme, H_, mat, band, nomt = STYLES[i]
    ro, ri = 5.6, 3.8
    garde_toit = nomt == "tour de nether bricks"       # la salle 1 est sous son toit
    g = 20 + i
    for x in range(cx - 7, cx + 8):
        for z in range(cz - 7, cz + 8):
            m = math.hypot(x - cx, z - cz) if forme == "rond" else max(abs(x - cx), abs(z - cz))
            if m > ro:
                continue
            occ[(x, z)] = "tour"
            if m > ri:
                # sommet ronge : le mur s'arrete a hauteur variable, parfois sous la plateforme
                hm = H_ + 2 if garde_toit else H_ + 3 - int(11 * bruit(x, z, 5, g))
                for y in range(SOL + 1, hm + 1):
                    e = y - SOL
                    breche = e > 5 and y < H_ - 1 and bruit(math.degrees(math.atan2(z - cz, x - cx)) / 9, e / 2, 4, g + 40) > 0.74
                    if breche:
                        put(x, y, z, AIR)
                    else:
                        put(x, y, z, use(band if e % 9 == 0 else mat, x, y, z, g))
            else:
                put(x, SOL, z, PBK)
                for y in range(SOL + 1, H_ + 3):
                    put(x, y, z, AIR)
    for x, z in disque(cx + .5, cz + .5, 1.5):
        for y in range(SOL + 1, H_):
            put(x, y, z, band if y % 8 == 0 else mat)
    a0 = (a + 170) % 360
    spirale(cx, cz, 1.6, 3.7, SOL, H_ - SOL, 7, a0, PBK, DALLE)
    plancher(cx, cz, H_, 3.9, H_ - SOL, a0, 7, PBK)
    percer(cx, cz, a0 - 12, 3.0, 8.4, SOL + 1, 3, sol=PBK)
    # meurtrieres : fentes de 1 sur 2, deux par face, tous les 6 blocs
    for e in range(6, H_ - SOL - 1, 6):
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for sp in (-2, 2):
                for r_ in range(3, 8):
                    x, z = cx + dx * r_ + dz * sp, cz + dz * r_ + dx * sp
                    m = math.hypot(x - cx, z - cz) if forme == "rond" else max(abs(x - cx), abs(z - cz))
                    if ri < m <= ro + 0.5:
                        put(x, SOL + e, z, AIR)
                        put(x, SOL + e + 1, z, AIR)
    # eboulis au pied, morceaux tombes
    for x in range(cx - 10, cx + 11):
        for z in range(cz - 10, cz + 11):
            m = math.hypot(x - cx, z - cz)
            if 6 <= m <= 9.5 and bruit(x, z, 3, g + 7) > 0.62 and dist(x, z) < 47 and (x, z) not in occ and not lave_sol[x, z]:
                dd = adiff(math.degrees(math.atan2(z - cz, x - cx)), a0 - 12)
                if dd > 30 and vide((x, Hs(x, z) + 1, z)):
                    put(x, Hs(x, z) + 1, z, use(mat, x, 0, z, g))
                    if bruit(x, z, 2, g + 8) > 0.7:
                        put(x, Hs(x, z) + 2, z, mat)
    x, z = math.floor(cx + 3 * math.cos(math.radians(a + 90))), math.floor(cz + 3 * math.sin(math.radians(a + 90)))
    if vide((x, H_ + 1, z)) and solide((x, H_, z)):
        put(x, H_ + 1, z, LANT)
    TOURS.append((nomt, (cx, H_ + 1, cz - 2 if nomt else cz), H_ - SOL, (cx, SOL + 1, cz)))
    bon = [(x, z) for x, z in disque(cx + .5, cz + .5, 3.9) if nom((x, H_, z)) in ("polished_blackstone",) and vide((x, H_ + 1, z)) and vide((x, H_ + 2, z))]
    u = max(bon, key=lambda c: (math.hypot(c[0] - cx, c[1] - cz), c))
    CIBLES[f"sommet : {nomt}"] = [u[0], H_ + 1, u[1]]


for i, a in enumerate(ANG_TOURS):
    tour(i, a)
print("[4] tours :", len(TOURS), flush=True)

# ====================================================================== 5. acces au souterrain
# a) descente dans la tour de garde : le sol de la tour s'ouvre sur un colimacon
cx, cz = CENT_T[15]
a1 = 40
puits_helice(cx, cz, SOL, a1)
plancher(cx, cz, SOL, 3.9, SOL - FOND, a1, 7, PBK)
u = max(((x, z) for x, z in disque(cx + .5, cz + .5, 3.9) if nom((x, SOL, z)) == "polished_blackstone" and vide((x, SOL + 1, z))), key=lambda c: (math.hypot(c[0] - cx, c[1] - cz), c))
ACCES.append(("Descente dans la tour de garde", (u[0], SOL + 1, u[1])))
# b) puits : colimacon a ciel ouvert, margelle
cx, cz = cellule(33, PUITS)
puits_helice(cx, cz, SOL + 1, 300)
for x, z in disque(cx + .5, cz + .5, 5):
    if math.hypot(x - cx, z - cz) > 3.9:
        put(x, SOL, z, BKB)
ACCES.append(("Puits à colimaçon", (cx + 5, SOL + 1, cz)))
occ.update({c: "acces" for c in disque(cx + .5, cz + .5, 6)})
# c) cratere : entonnoir, puis colimacon au fond
cx, cz = cellule(33, CRATERE)
for x, z in disque(cx + .5, cz + .5, 7.5):
    e = math.hypot(x - cx, z - cz)
    fond_c = SOL - max(0, min(5, int(7 - e)))
    for y in range(fond_c, SOL + 1):
        put(x, y, z, AIR)
    put(x, fond_c - 1, z, BAS if e < 4 else MAGMA if e < 5 else BLK)
    Hm[x, z] = fond_c - 1
puits_helice(cx, cz, SOL - 6, 90)
ACCES.append(("Cratère", (cx + 8, SOL + 1, cz)))
occ.update({c: "acces" for c in disque(cx + .5, cz + .5, 9)})
# d) faille : escalier a ciel ouvert, du haut de la rive jusqu'au souterrain
ux, uz = math.cos(math.radians(FAILLE)), math.sin(math.radians(FAILLE))
fx, fz = point(46, FAILLE)
for k in range(15):
    px, pz = fx - ux * k, fz - uz * k
    y = SOL - k
    for s in (-1.4, 0, 1.4):
        x, z = math.floor(px - uz * s), math.floor(pz + ux * s)
        for yy in range(max(y, FOND), SOL + 8):
            if nom((x, yy, z)) is not None or yy <= SOL:
                put(x, yy, z, AIR)
        put(x, max(y - 1, FOND), z, BKB)
ACCES.append(("Faille", (math.floor(fx), SOL, math.floor(fz))))
CIBLES["bas de la faille"] = [cellule(33, FAILLE)[0], FOND + 1, cellule(33, FAILLE)[1]]
print("[5] acces :", len(ACCES), flush=True)

# ====================================================================== 6. ponts, centre
for x, z in ponts_cells:
    if lave_sol[x, z] and dist(x, z) < 47.5:
        put(x, SOL, z, BKB if bruit(x, z, 7, 14) > 0.4 else BAS)
for x, z in disque(CEN, CEN, 8):
    put(x, SOL + 1, z, PBK if dist(x, z) > 5.5 else BKB)
for i in range(-2, 2):                                 # brasero central, bas et sobre (la colonne d'obsidienne est retiree, 2026-11-08)
    for j in range(-2, 2):
        bord = i in (-2, 1) or j in (-2, 1)
        put(C + i, SOL + 2, C + j, BKB if bord else NETH)
        put(C + i, SOL + 3, C + j, (CBK if (i + j) % 2 else BKB) if bord else FEU_N)
for i, j in ((-3, -3), (2, -3), (-3, 2), (2, 2)):    # quatre poteaux de basalte poli, une lanterne sur chacun
    for y in range(SOL + 2, SOL + 5):
        put(C + i, y, C + j, S("polished_basalt", axis="y"))
    put(C + i, SOL + 5, C + j, LANT)
for a in (45, 135, 225):
    x, z = cellule(11.5, a)
    put(x, SOL, z, SSAND)
    put(x, SOL + 1, z, FEU_A)
reserver_c = {c: "centre" for c in disque(CEN, CEN, 23)}
occ.update(reserver_c)

# ====================================================================== 7. salles cachees (les 4 conservees)
T_SALLE = [BKB, NB]


def salle(num, x0, x1, z0, z1, yf, coffre, acces, garder=()):
    h = 4
    garder = set(garder)
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            for y in range(yf, yf + h + 2):
                dedans = x0 <= x <= x1 and z0 <= z <= z1
                if dedans and yf < y <= yf + h:
                    put(x, y, z, AIR)
                elif dedans and y == yf:
                    put(x, y, z, PBK)
                else:
                    put(x, y, z, T_SALLE[0] if bruit(x, y, 5, 15 + num) > 0.4 else T_SALLE[1])
    cx, cz = coffre
    put(cx, yf, cz, GILD)
    for x, z in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
        if (x, z) not in garder and math.hypot(x - cx, z - cz) > 1.5:
            put(x, yf + 1, z, LANT)
    mx, mz = (x0 + x1) // 2, (z0 + z1) // 2
    put(mx, yf + h + 1, mz, SHROOM)
    SALLES.append((num, (cx, yf + 1, cz), acces))


def echelle(x, z, y0, y1, sup):
    for y in range(y0, y1 + 1):
        put(x, y, z, S("ladder", facing={(-1, 0): "east", (1, 0): "west", (0, -1): "south", (0, 1): "north"}[sup], waterlogged=False))
        sp = (x + sup[0], y, z + sup[1])
        if nom(sp) is None or nom(sp) in NON_SOLIDES:
            put(*sp, BKB)


# 1 : en haut de la tour de nether bricks, sous un toit
cx, cz = CENT_T[195]
H_ = STYLES[3][1]
for x in range(cx - 5, cx + 6):
    for z in range(cz - 5, cz + 6):
        put(x, H_ + 4, z, RNB)
put(cx - 3, H_, cz, GILD)
put(cx - 3, H_ + 1, cz, AIR)
put(cx - 3, H_ + 2, cz, AIR)
SALLES.append((1, (cx - 3, H_ + 1, cz), "En haut de la tour de nether bricks, sous le toit"))
# 2 : sous une trappe, entre deux collines
tx, tz = cellule(43, 22)
for x, z in disque(tx + .5, tz + .5, 2.5):
    occ[(x, z)] = "salle"
salle(2, tx - 2, tx + 3, tz - 3, tz + 2, SOL - 8, (tx + 3, tz), "Trappe dans le sol, à l'est, échelle", garder=[(tx, tz), (tx + 1, tz)])
Hm[tx, tz] = SOL
for y in range(SOL + 1, SOL + 14):
    put(tx, y, tz, AIR)
echelle(tx, tz, SOL - 7, SOL - 1, (1, 0))
put(tx, SOL, tz, TRAPPE)
# 3 : sous l'autel, stele creuse
sx_, sz_ = C + 6, C + 6
salle(3, sx_ - 3, sx_ + 3, sz_ - 3, sz_ + 3, SOL - 8, (sx_ - 3, sz_), "Sous l'autel : la stèle sud-est est creuse, ouverte à l'ouest, échelle", garder=[(sx_, sz_), (sx_ + 1, sz_)])
for i in (-1, 0, 1):
    for j in (-1, 0, 1):
        for y in range(SOL + 1, SOL + 6):
            put(sx_ + i, y, sz_ + j, RNB if y == SOL + 5 else BKB)
for y in range(SOL - 3, SOL + 3):
    put(sx_, y, sz_, AIR)
echelle(sx_, sz_, SOL - 7, SOL + 1, (1, 0))
for y in (SOL + 1, SOL + 2):
    put(sx_ - 1, y, sz_, AIR)
# 4 : massif contre le rempart, en face de la porte, derriere un pan de mur decale
g = 90
for al in range(38, 48):
    for sp in range(-5, 5):
        x, z = gl(al, sp, g)
        put(x, SOL, z, PBK)
        for y in range(SOL + 1, SOL + 14):
            put(x, y, z, AIR)
        if al >= 40:
            for y in range(SOL + 1, SOL + 8):
                put(x, y, z, BKB if bruit(x, y, 5, 19) > 0.4 else NB)
        occ[(x, z)] = "massif"
pts = [gl(al, sp, g) for al in (41, 45) for sp in (-2, 2)]
salle(4, min(p[0] for p in pts), max(p[0] for p in pts), min(p[1] for p in pts), max(p[1] for p in pts), SOL, gl(45, 0, g), "Massif contre le rempart, au sud : ouverture derrière le pan de mur décalé", garder=[gl(41, 0, g)])
x, z = gl(40, 0, g)
for y in (SOL + 1, SOL + 2):
    put(x, y, z, AIR)
for sp in range(-3, 3):
    x, z = gl(38, sp, g)
    for y in range(SOL + 1, SOL + 5):
        put(x, y, z, BKB)
print("[7] salles :", len(SALLES), flush=True)

# ====================================================================== 8. decoration, apparitions, eclairage
n = 0
for i in range(3000):                                  # eperons de basalte et feux sur netherrack
    x, z = cellule(rng.uniform(25, 46), rng.uniform(0, 360))
    if n >= 26 or (x, z) in occ or lave_sol[x, z] or axes[x, z] < 5 or abs(RR[x, z] - 24) < 2.5:
        continue
    if any((x + a, z + b) in occ or lave_sol[x + a, z + b] for a in (-3, 0, 3) for b in (-3, 0, 3)):
        continue
    y0 = Hs(x, z)
    if nom((x, y0, z)) in (None, "air") or not vide((x, y0 + 1, z)):
        continue
    if n % 5 == 4 and nom((x, y0, z)) == "netherrack":
        put(x, y0 + 1, z, FEU_N)
    else:
        r0, h = rng.uniform(1.3, 2.4), rng.randint(4, 9)
        tx, tz = rng.uniform(-.2, .2), rng.uniform(-.2, .2)
        for k in range(h):
            r2 = r0 * (1 - k / h) ** 0.8
            ax, az = x + .5 + tx * k, z + .5 + tz * k
            for u, v in disque(ax, az, r2 + 0.2):
                put(u, Hs(u, v) + 1 + k, v, BAS if k % 5 < 4 else SBAS)
    for a in range(-3, 4):
        for b in range(-3, 4):
            occ[(x + a, z + b)] = "deco"
    n += 1
n_deco = n
for i in range(10):
    a = 9 + 36 * i
    for dr in (0, 1, -1, 2, -2):
        x, z = cellule(24 + dr, a)
        if not lave_sol[x, z] and vide((x, Hs(x, z) + 1, z)) and vide((x, Hs(x, z) + 2, z)):
            break
    y = Hs(x, z)
    put(x, y, z, GILD)
    APPS.append((x, y + 1, z))

# ====================================================================== 8b. parois du souterrain : roche explicite, en plaques et en veines
# Sans cela, les parois sont celles du monde : blackstone dans Arene Feu, terre dans Arene Semaine.
SOUS = {(x, y, z) for (x, y, z), s in blocs.items() if y < SOL and (s[0].split(":")[1] in ("air", "ladder", "fire", "soul_fire", "soul_lantern", "chain", "dark_oak_trapdoor")
                                                              or (s[0].split(":")[1] == "lava" and y < SOL - 3))}
PAR = set()
for x, y, z in SOUS:
    for q in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
        if q not in blocs and -BAS_EXT <= q[1] < SOL and 0 <= q[0] < SX and 0 <= q[2] < SZ:
            PAR.add(q)


def b3(x, y, z, cel, g):
    return (bruit(x, z + 1.7 * y, cel, g) + bruit(x + 1.3 * y, z - 0.6 * y, cel, g + 1)) / 2


cases = sorted(PAR)
nb = len(cases)
sols = {q for q in cases if (q[0], q[1] + 1, q[2]) in SOUS}
choix = {q: CBK for q in sorted(cases, key=lambda q: -b3(*q, 4, 61))[:round(.05 * nb)]}                  # plaques de briques craquelees
reste = [q for q in cases if q not in choix]
choix.update({q: MAGMA for q in sorted((q for q in reste if q not in sols), key=lambda q: abs(b3(*q, 6, 62) - .5))[:round(.10 * nb)]})   # veines de magma, jamais au sol
reste = [q for q in reste if q not in choix]
choix.update({q: BAS for q in sorted(reste, key=lambda q: abs(b3(*q, 8, 63) - .5))[:round(.20 * nb)]})   # veines de basalte
reste = sorted((q for q in reste if q not in choix), key=lambda q: b3(*q, 9, 64))
coupe = round(len(reste) * 35 / 65)
choix.update({q: (BLK if i < coupe else NETH) for i, q in enumerate(reste)})                            # grandes plaques
for q, s in choix.items():
    put(*q, s)
STATS_PAROIS = {k: sum(1 for s in choix.values() if s[0] == "minecraft:" + k) for k in ("blackstone", "netherrack", "basalt", "magma_block", "cracked_polished_blackstone_bricks")}
print("[8b] parois explicites :", nb, STATS_PAROIS, flush=True)

SOURCES = {"soul_lantern": 10, "shroomlight": 15, "lava": 15, "light": 15, "magma_block": 3, "fire": 15, "soul_fire": 10}
TRANSP = NON_SOLIDES | {"barrier", "polished_blackstone_slab", "shroomlight"}
SANS_SPAWN = TRANSP | {"magma_block"}


def calcul():
    op = np.zeros((SX, SY + BAS_EXT, SZ), dtype=bool)  # indice = y + BAS_EXT
    op[:, :SOL + 1 + BAS_EXT, :] = True
    L = np.zeros((SX, SY + BAS_EXT, SZ), dtype=np.int8)
    for (x, y, z), s in blocs.items():
        n = s[0].split(":")[1]
        op[x, y + BAS_EXT, z] = n not in TRANSP
        if n in SOURCES:
            L[x, y + BAS_EXT, z] = SOURCES[n]
            op[x, y + BAS_EXT, z] = False
    for _ in range(14):
        N = L.copy()
        np.maximum(N[1:], L[:-1] - 1, out=N[1:])
        np.maximum(N[:-1], L[1:] - 1, out=N[:-1])
        np.maximum(N[:, 1:], L[:, :-1] - 1, out=N[:, 1:])
        np.maximum(N[:, :-1], L[:, 1:] - 1, out=N[:, :-1])
        np.maximum(N[:, :, 1:], L[:, :, :-1] - 1, out=N[:, :, 1:])
        np.maximum(N[:, :, :-1], L[:, :, 1:] - 1, out=N[:, :, :-1])
        N[op] = 0
        L = N
    return L


def trav(p):
    n = nom(p)
    return (p[1] > SOL) if n is None else n in ("air", "light")


coffres = {(p[0], p[2]) for _, p, _ in SALLES}
total = 0
for passe in range(60):
    L = calcul()
    noires = sorted((x, y, z) for (x, y, z), s in blocs.items() if s[0].split(":")[1] not in SANS_SPAWN and y + 2 < SY and dist(x, z) < 50.5 and trav((x, y + 1, z)) and trav((x, y + 2, z)) and L[x, y + 1 + BAS_EXT, z] == 0)
    if not noires:
        break
    pris = []
    for x, y, z in noires:
        if (x, z) in coffres or any(abs(x - a) < 10 and abs(z - b) < 10 and abs(y - c) < 5 for a, c, b in pris):
            continue
        pris.append((x, y, z))
        put(x, y + 1, z, LUMIERE)
        total += 1
if noires:
    raise SystemExit(f"surfaces noires {noires[:5]}")
print("[8] lumieres", total, "| deco", n_deco, flush=True)

err = []
for p, s in blocs.items():
    x, y, z = p
    n = s[0].split(":")[1]
    st = dict(s[1])
    if n in ("water", "chest", "barrel", "hopper", "dropper", "dispenser", "spawner", "trapped_chest") or (n in ("soul_sand", "soul_soil") and p in PAR):
        err.append(f"interdit {n} {p}")
    if n != "air" and dist(x, z) > R + 1e-9:
        err.append(f"depasse {n} {p}")
    if n == "lava":
        if st.get("level") == "8":
            if nom((x, y - 1, z)) != "lava":
                err.append(f"chute sans arrivee {p}")
            if nom((x, y + 1, z)) != "lava":
                err.append(f"chute sans source {p}")
        else:
            for q in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1), (x, y - 1, z)):
                if nom(q) != "lava" and not solide(q):
                    err.append(f"lave non contenue {p} -> {q} ({nom(q)})")
    if n == "fire" and nom((x, y - 1, z)) not in ("netherrack", "magma_block"):
        err.append(f"feu mal pose {p}")
    if n == "soul_fire" and nom((x, y - 1, z)) not in ("soul_sand", "soul_soil"):
        err.append(f"feu des ames mal pose {p}")
    if n == "soul_lantern" and not solide((x, y - 1, z)) and st["hanging"] == "false":
        err.append(f"lanterne en l'air {p}")
for num, (cx, cy, cz), _ in SALLES:
    if nom((cx, cy, cz)) != "air" or nom((cx, cy + 1, cz)) != "air" or not solide((cx, cy - 1, cz)):
        err.append(f"salle {num} coffre {(cx, cy, cz)} {nom((cx, cy, cz))}")
print("[controle] erreurs :", len(err), err[:10], flush=True)
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
    liste.append(Compound({"pos": List[Int]([Int(x), Int(y + BAS_EXT), Int(z)]), "state": Int(index[s])}))
racine = nbtlib.File({"DataVersion": Int(3955), "size": List[Int]([Int(SX), Int(SY + BAS_EXT), Int(SZ)]), "palette": List[Compound](palette), "blocks": List[Compound](liste), "entities": List[Compound]([])}, gzipped=True)
racine.save(RACINE / "exports" / f"{NOM}.nbt")
D = SAVE / "datapacks/atelier"
if (SAVE / "level.dat").exists():
    (D / "data/atelier/structure").mkdir(parents=True, exist_ok=True)
    racine.save(D / "data/atelier/structure" / f"{NOM}.nbt")
    if not (D / "pack.mcmeta").exists():
        (D / "pack.mcmeta").write_text('{"pack": {"pack_format": 48, "description": "Atelier [claude-build]"}}\n', encoding="utf-8")
else:
    print("ATTENTION : monde Arene Feu introuvable, structure ecrite seulement dans exports/")
ci = dict(CIBLES)
ci.update({f"salle {n}": list(p) for n, p, _ in SALLES})
ci.update({f"apparition {i + 1}": list(p) for i, p in enumerate(APPS)})
for k, (t, p) in enumerate(ACCES):
    ci[f"accès : {t}"] = list(p)
for a in (60, 165, 285, 15):
    x, z = cellule(33 if a != 15 else 28.5, a)
    ys = [y for y in range(FOND, FOND + 4) if solide((x, y, z)) and vide((x, y + 1, z))]
    ci[f"souterrain, angle {a}"] = [x, (ys[0] if ys else FOND) + 1, z]
for a in (30, 90, 150, 210, 330):                      # chaque secteur entre deux rivieres
    x, z = cellule(25.5, a)
    ci[f"secteur {a}"] = [x, Hs(x, z) + 1, z]
gx, gz = gl(46, 0, PORTE)
# contournement : on bouche le pont, les ilots et l'ilot central ; plages et corniches doivent rester atteignables
bloque = [[x, y, z] for x, z in PONT | PIERRES for y in (Y_LAVE + 1, Y_LAVE + 2)] + [[x, y, z] for x, z in ILOT for y in (Y_LAVE + 2, Y_LAVE + 3)]
def haut_(p_):                                         # les cibles suivent le decalage du fichier
    return [p_[0], p_[1] + BAS_EXT, p_[2]]


(RACINE / "exports" / f"{NOM}_contournement_cibles.json").write_text(json.dumps({"_marque": "[claude-build]", "sol": SOL + BAS_EXT, "sauts_longs": True, "depart": haut_([gx, SOL + 1, gz]),
    "cibles": {k: haut_(v) for k, v in ci.items() if k.startswith(("plage", "corniche"))}, "bloquer": [haut_(b) for b in bloque]}, ensure_ascii=False), encoding="utf-8")
# epaisseur de roche au-dessus de la caverne et des galieries qui y menent
EP, EP_FEU, EP_SALLE = 99, 99, 99
for x in range(C - 30, C + 30):
    for z in range(C - 30, C + 30):
        creux = [y for y in range(-BAS_EXT, SOL - 3) if (x, y, z) in SOUS and nom((x, y, z)) == "air"]
        if not creux or dist(x, z) >= 27:
            continue
        y0 = max(creux) + 1
        k = 0
        while y0 + k < SOL + 2 and solide((x, y0 + k, z)):
            k += 1
        if (x, z) in SALLE3:
            EP_SALLE = min(EP_SALLE, k)                 # jusqu'au plancher de la salle cachee, pas jusqu'a la surface
            continue
        EP = min(EP, k)
        if 14 <= dist(x, z) < R_FEU:
            EP_FEU = min(EP_FEU, k)
print("[mesure] roche jusqu'a la surface : au moins", EP, "| sous le cercle de feu :", EP_FEU, "| jusqu'a la salle cachee :", EP_SALLE, flush=True)
(RACINE / "exports" / f"{NOM}_cibles.json").write_text(json.dumps({"_marque": "[claude-build]", "sol": SOL + BAS_EXT, "sauts_longs": True, "depart": haut_([gx, SOL + 1, gz]),
    "cibles": {k: haut_(v) for k, v in ci.items()}}, ensure_ascii=False, indent=1), encoding="utf-8")


def w(p):
    return " ".join(str(v) for v in monde(*p))


md = ["<!-- [claude-build] genere par sources/feu2.py -->", "# Arène Feu — version Ø104", "", "Monde `saves/Arene Feu`, centre X0 Z0, sol Y86. Porte au nord.", "",
      f"- **Cercle de feu :** rayon 19 avant, {R_FEU:g} après.", "", "## Accès au souterrain", ""]
md += [f"- {t} : {w(p)}" for t, p in ACCES]
md += ["", "## Tours", "", "| Tour | Pied (X Y Z) | Hauteur | Origine |", "|---|---|---|---|"]
md += [f"| {t} | {w(pied)} | {h} blocs | {'ancienne, refaite' if i < 2 else 'nouvelle'} |" for i, (t, top, h, pied) in enumerate(TOURS)]
md += ["", "## Salles cachées", "", "| N° | Coffre (X Y Z) | Accès |", "|---|---|---|"]
md += [f"| {n} | {w(p)} | {a} |" for n, p, a in sorted(SALLES)]
md += ["", "Apparitions : " + " ; ".join(w(p) for p in APPS), "", "## Ce qui a été modifié", "",
       "- Labyrinthes supprimés, terrain accidenté à pentes praticables, soul soil réduit à des plaques isolées.",
       "- 3 rivières de lave de l'anneau au rempart, 5 fosses, avec ponts ; lave en source dans des lits fermés.",
       f"- Souterrain en anneau, lacs de lave, {n_ch} chutes confinées, pont à 2 sauts, 4 accès.",
       "- 6 tours à colimaçon, de 15 à 34 blocs.", "", "## Écarts par rapport au prompt", "",
       "- Emprise de 52 blocs au lieu de 126 : tout est resserré pour tenir en Ø104.",
       "- Arène reconstruite dans un nouveau monde à partir de la version Ø104, et non modifiée sur place. Les 2 tours anciennes sont refaites avec un escalier, comme demandé pour toutes les tours.",
       "- Construction par structure (.nbt + /place) au lieu de /fill en jeu.",
       "- Les 4 salles sont gardées ; 2 ont une échelle d'accès, ce ne sont pas des tours.",
       ]
md += ["", "## Souterrain (2026-11-08)", "",
       "### Terre remplacée", "",
       "- Avant : la structure ne fixait pas les parois du souterrain. Dans le monde `Arene Feu` on y voyait le sol du monde (4 434 blocs de blackstone) ; dans `Arene Semaine`, posé sur de la terre, 4 437 blocs de terre.",
       f"- Après : {nb} blocs de paroi font partie de l'arène, quel que soit le monde : " + ", ".join(f"{k.replace('cracked_polished_blackstone_bricks', 'briques craquelées').replace('magma_block', 'magma').replace('basalt', 'basalte')} {v} ({round(100 * v / nb)} %)" for k, v in STATS_PAROIS.items()) + ".",
       "- Répartition en plaques (blackstone, netherrack, briques craquelées) et en veines (basalte, magma). Le magma n'est jamais au sol : seulement parois et plafonds. Ni soul sand ni soul soil.",
       "- Autres blocs naturels de la surface visibles dans le souterrain : aucun, dans les deux mondes (ni pierre, ni ardoise, ni gravier, ni minerai). Aucun coffre ni conteneur dans l'emprise.", "",
       "### Caverne et lac", "",
       f"- Caverne sous le centre de l'arène, rayon de 19 à 23 blocs autour de X{monde(C, 0, C)[0]} Z{monde(C, 0, C)[2]} ; air de Y{monde(0, Y_LAVE + 1, 0)[1]} à Y{monde(0, Y_HAUT, 0)[1]} (Y{monde(0, Y_HAUT_SALLE, 0)[1]} sous la salle cachée de l'autel).",
       f"- Lac de lave irrégulier : {round(sum(2 * r_lac(a) for a in range(0, 360, 5)) / 72)} blocs de large en moyenne, de {round(2 * min(r_lac(a) for a in range(0, 360, 5)))} à {round(2 * max(r_lac(a) for a in range(0, 360, 5)))} selon la direction, surface Y{monde(0, Y_LAVE, 0)[1]}, profondeur 2 ; {len(LAC)} cases.",
       f"- Plafond au moins {Y_HAUT_SALLE - Y_LAVE} blocs au-dessus de la lave, stalactites de basalte, {n_ch2} chutes de lave confinées qui tombent dans le lac.",
       "- Rives : plages de blackstone au ras de la lave, falaises, corniche étroite tout autour (on contourne le lac sans toucher la lave), 2 rampes de la plage à la corniche.",
       "- Traversée : pont de blackstone effondré par endroits (2 sauts), îlots de basalte pour sauter ; îlot central avec piliers et deux braseros.",
       f"- {len(TUNNELS)} galeries relient l'anneau souterrain à la corniche (angles {', '.join(map(str, TUNNELS))}). Avant, aucune galerie n'allait vers le centre.",
       f"- Roche entre la caverne (ou ses galeries) et la surface : au moins {EP} blocs ; sous le cercle de feu : {EP_FEU} blocs. Sous la salle cachée de l'autel : {EP_SALLE} blocs jusqu'à son plancher, la salle reste intacte.", "",
       "### Centre de l'arène", "",
       f"- La grande colonne d'obsidienne (22 blocs de haut) est remplacée, à la demande de l'utilisateur, par un brasero bas en briques de blackstone avec un feu au milieu, entouré de quatre poteaux de basalte poli portant une lanterne des âmes, en {w((C, SOL + 2, C))}.", "",
       "### Écarts par rapport au prompt", "",
       "- Les valeurs à compléter ont été prises dans le projet : Minecraft 1.21.1, centre X0 Z0, lac de 25 à 30 blocs.",
       "- Le fichier de l'arène descend de 12 blocs (il commence à Y56 au lieu de Y68) pour loger le lac sous le souterrain existant. Au sol et au-dessus, un seul bloc change : un bloc de lumière invisible déplacé par le calcul d'éclairage.",
       "- Les berges des rivières de lave de surface (derrière la lave) restent celles du monde : elles font partie de la surface, que le prompt interdit de modifier.",
       "- Diamètre du lac : la forme irrégulière donne 20 blocs à l'endroit le plus étroit, sous les 25 demandés.",
       "- Travaux faits par le générateur et un correctif à poser, pas par /fill ni /forceload. La surface n'est pas touchée.",
       "- L'arène dans le monde Arene Semaine et son zip ne sont pas encore mis à jour."]
(RACINE / "arene_feu.md").write_text("\n".join(md) + "\n", encoding="utf-8")

from PIL import Image, ImageDraw
COUL = {"netherrack": (120, 40, 40), "blackstone": (45, 40, 48), "polished_blackstone": (60, 55, 65), "polished_blackstone_bricks": (70, 65, 75), "cracked_polished_blackstone_bricks": (66, 60, 70),
        "basalt": (80, 80, 85), "smooth_basalt": (62, 62, 68), "magma_block": (190, 70, 20), "lava": (255, 110, 0), "soul_soil": (80, 62, 50), "soul_sand": (95, 75, 60), "nether_bricks": (60, 30, 35),
        "red_nether_bricks": (90, 15, 20), "obsidian": (25, 15, 40), "crying_obsidian": (70, 20, 120), "gilded_blackstone": (180, 140, 40), "shroomlight": (245, 170, 80)}
CACHE = {"air", "light", "barrier", "soul_lantern", "chain", "ladder", "fire", "soul_fire"}
s_ = 5
Wd, Hh = (SX + SZ) * s_ + 40, (SX + SZ) * s_ // 2 + SY * s_ + 60
img = Image.new("RGB", (Wd, Hh), (150, 120, 100))
dr = ImageDraw.Draw(img)


def ouvert(p):
    n = nom(p)
    return (p[1] > SOL) if n is None else n in CACHE


vis = [(p, s[0].split(":")[1]) for p, s in blocs.items() if s[0].split(":")[1] not in CACHE and p[1] >= SOL - 3]
vis.sort(key=lambda t: (t[0][0] + t[0][2], t[0][1]))
for (x, y, z), n in vis:
    c = COUL.get(n, (150, 150, 150))
    px = (x - z) * s_ + SZ * s_ + 20
    py = (x + z) * s_ // 2 - y * s_ + SY * s_ + 20
    if ouvert((x, y + 1, z)):
        dr.polygon([(px, py), (px + s_, py + s_ // 2), (px, py + s_), (px - s_, py + s_ // 2)], fill=c)
    if ouvert((x + 1, y, z)):
        dr.polygon([(px + s_, py + s_ // 2), (px, py + s_), (px, py + 2 * s_), (px + s_, py + s_ + s_ // 2)], fill=tuple(int(v * .78) for v in c))
    if ouvert((x, y, z + 1)):
        dr.polygon([(px - s_, py + s_ // 2), (px, py + s_), (px, py + 2 * s_), (px - s_, py + s_ + s_ // 2)], fill=tuple(int(v * .62) for v in c))
img.save(RACINE / "exports" / f"{NOM}_vue.png")
print(f"Ecrit : {len(liste)} blocs, palette {len(palette)}, taille {SX}x{SY}x{SZ}")
print(f"POSE : /tp @s 0 140 0   puis   /place template atelier:{NOM} {ORIGINE[0]} {ORIGINE[1] - BAS_EXT} {ORIGINE[2]}")
