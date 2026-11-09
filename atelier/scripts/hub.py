# [claude-build]
"""
hub.py - "Le Carrefour des Elements" : hub circulaire Ø104 en X0 Z0 et 4 avenues vers les 4 arenes (sol Y86).
Nord : Glace, Est : Air, Sud : Feu, Ouest : Nature. Ecrit aussi les fonctions qui posent tout dans le monde.
  .venv\\Scripts\\python.exe sources\\hub.py --monde "Arene Hub"
En jeu : /function atelier:hub_tout
"""
import argparse
import json
import math
import random
import shutil
from pathlib import Path

import nbtlib
import numpy as np
from nbtlib import Byte, Compound, Int, List, String

ap = argparse.ArgumentParser()
ap.add_argument("--monde", default="Arene Hub")
ARGS = ap.parse_args()

NOM = "hub"
RACINE = Path(__file__).resolve().parent.parent
SAVE = RACINE / "launcher/instances/Build-1.21.1/minecraft/saves" / ARGS.monde
MONDE_OK = (SAVE / "level.dat").exists()
DATAPACK = SAVE / "datapacks/atelier"
STRUCT = DATAPACK / "data/atelier/structure"
FONC = DATAPACK / "data/atelier/function"
VUE = RACINE / "exports" / f"{NOM}_vue.png"
PLAN = RACINE / "exports" / f"{NOM}_plan.png"
CIBLES = RACINE / "exports" / f"{NOM}_cibles.json"

SX = SZ = 228
SY = 56
SOL = 8
C = 114
CEN = 114.0
R_HUB = 52
ORIGINE = (-C, 86 - SOL, -C)
rng = random.Random(4444)
GLACE, AIRE, FEU, NATURE = 270, 0, 90, 180


def S(nom_, **p):
    return ("minecraft:" + nom_, tuple(sorted((k, str(v).lower() if isinstance(v, bool) else str(v)) for k, v in p.items())))


AIR = S("air")
blocs = {}
PANNEAUX = {}


def put(x, y, z, s):
    if x != int(x) or y != int(y) or z != int(z):
        raise ValueError(f"coordonnee non entiere {(x, y, z)}")
    x, y, z = int(x), int(y), int(z)
    if not (0 <= x < SX and 0 <= y < SY and 0 <= z < SZ):
        raise ValueError(f"hors gabarit {(x, y, z)} {s[0]}")
    blocs[(x, y, z)] = s


def nom(p):
    s = blocs.get(p)
    return None if s is None else s[0].split(":")[1]


def vide(p):
    n = nom(p)
    return (p[1] > SOL) if n is None else n == "air"


def dist(x, z):
    return math.hypot(x + .5 - CEN, z + .5 - CEN)


def ang(x, z):
    return math.degrees(math.atan2(z + .5 - CEN, x + .5 - CEN)) % 360


def h01(x, y, z, g=0):
    h = (int(x) * 374761393 + int(y) * 1442695041 + int(z) * 668265263 + g * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h ^= h >> 16
    return h / 0xFFFFFFFF


def choix(table, u):
    tot = sum(p for p, _ in table)
    u *= tot
    for p, s in table:
        if u < p:
            return s
        u -= p
    return table[-1][1]


def bruit(x, z, cel, g):
    gx, gz = x / cel, z / cel
    i, j = math.floor(gx), math.floor(gz)
    fx, fz = gx - i, gz - j
    sx, sz = fx * fx * (3 - 2 * fx), fz * fz * (3 - 2 * fz)
    a, b = h01(i, 0, j, g), h01(i + 1, 0, j, g)
    c, d = h01(i, 0, j + 1, g), h01(i + 1, 0, j + 1, g)
    t = a + (b - a) * sx
    return t + ((c + (d - c) * sx) - t) * sz


def doux(t, x, z):
    """sol calme : un bloc dominant, le second en grandes plaques"""
    return t[1][1] if bruit(x, z, 9, 61) > 0.66 else t[0][1]


def disque(cx, cz, r):
    for x in range(math.floor(cx - r - 1), math.ceil(cx + r) + 1):
        for z in range(math.floor(cz - r - 1), math.ceil(cz + r) + 1):
            if math.hypot(x + 0.5 - cx, z + 0.5 - cz) <= r:
                yield x, z


def gl(al, sp, g):
    return {0: (C + al, C + sp), 90: (C - 1 - sp, C + al), 180: (C - 1 - al, C - 1 - sp), 270: (C + sp, C - 1 - al)}[g]


VERS_CENTRE = {0: "west", 90: "north", 180: "east", 270: "south"}
VERS_DEHORS = {0: "east", 90: "south", 180: "west", 270: "north"}

SST = S("smooth_stone")
SB = S("stone_bricks")
AND = S("polished_andesite")
CHB = S("chiseled_stone_bricks")
QZ = S("quartz_block")
QP = S("quartz_pillar", axis="y")
QS = S("smooth_quartz")
QC = S("chiseled_quartz_block")
LUM = S("sea_lantern")
LANT = S("lantern", hanging=False, waterlogged=False)
SLAN = S("soul_lantern", hanging=False, waterlogged=False)
CHAINE = S("chain", axis="y", waterlogged=False)
VITRE = S("white_stained_glass_pane")
G_C = S("cyan_stained_glass")
G_B = S("light_blue_stained_glass")
PACK = S("packed_ice")
BLEU = S("blue_ice")
NEIGE = S("snow_block")
CAL = S("calcite")
LAINE = S("white_wool")
CUC = S("waxed_oxidized_cut_copper")
BLK = S("polished_blackstone")
BLKB = S("polished_blackstone_bricks")
NB = S("nether_bricks")
RNB = S("red_nether_bricks")
BAS = S("basalt", axis="y")
MAGMA = S("magma_block")
SHROOM = S("shroomlight")
LAVE = S("lava")
MSB = S("mossy_stone_bricks")
MCB = S("mossy_cobblestone")
MOSS = S("moss_block")
GRASS = S("grass_block", snowy=False)
CDIRT = S("coarse_dirt")
LOG = S("oak_log", axis="y")


def F(n):
    return S(f"{n}_leaves", distance=1, persistent=True, waterlogged=False)


THEMES = {
    GLACE: dict(nom="GLACE", sol=[(50, PACK), (30, BLEU), (20, CAL)], mur=[(45, PACK), (35, BLEU), (20, NEIGE)], accent=BLEU, verre=G_B, lum=LUM),
    AIRE: dict(nom="AIR", sol=[(55, QS), (25, CAL), (20, CUC)], mur=[(55, QZ), (30, CAL), (15, CUC)], accent=CUC, verre=G_C, lum=LUM),
    FEU: dict(nom="FEU", sol=[(60, BLK), (25, BLKB), (15, RNB)], mur=[(45, BLKB), (35, NB), (20, RNB)], accent=RNB, verre=S("orange_stained_glass"), lum=SHROOM),
    NATURE: dict(nom="NATURE", sol=[(45, MCB), (35, MSB), (20, CDIRT)], mur=[(50, MSB), (30, MCB), (20, SB)], accent=MOSS, verre=S("lime_stained_glass"), lum=SHROOM),
}

# ====================================================================== 1. sol du hub : rose des vents
for x, z in disque(CEN, CEN, R_HUB):
    d, a = dist(x, z), ang(x, z)
    g = int(round(a / 90) * 90) % 360
    t = THEMES[g]
    pointe = 16 + 29 * abs(math.cos(math.radians(2 * a))) ** 6
    if d <= pointe:
        s = t["sol"][0][1]
    elif abs(d - 42) < 0.8:
        s = CHB
    else:
        s = SST
    put(x, SOL, z, s)
    put(x, SOL - 1, z, SB)

# dais central
for x, z in disque(CEN, CEN, 14):
    d = dist(x, z)
    put(x, SOL + 1, z, QS if d < 12.5 else QC if int(ang(x, z) / 11.25) % 2 else QZ)
    if d < 10:
        put(x, SOL + 2, z, QZ)

# ====================================================================== 2. enceinte, colonnade, balcon
for x, z in disque(CEN, CEN, R_HUB):
    d, a = dist(x, z), ang(x, z)
    if d >= 50:
        fen = int(a / 5) % 3 == 1
        for y in range(SOL + 1, SOL + 13):
            put(x, y, z, G_B if fen and SOL + 3 <= y <= SOL + 6 else QC if y == SOL + 12 else SB if y % 3 else AND)
    if 45.5 <= d:
        put(x, SOL + 9, z, QS if int(a / 10) % 2 else SST)
    if 45.5 <= d <= 46.6:
        put(x, SOL + 10, z, VITRE)
for a in range(8, 360, 15):
    x, z = math.floor(CEN + 47.5 * math.cos(math.radians(a))), math.floor(CEN + 47.5 * math.sin(math.radians(a)))
    for cx, cz in disque(x + .5, z + .5, 1.3):
        for y in range(SOL + 1, SOL + 9):
            put(cx, y, cz, QC if y in (SOL + 1, SOL + 8) else QP)
    put(x, SOL + 10, z, QP)
    put(x, SOL + 11, z, LANT)

for g, t in THEMES.items():
    # porte monumentale
    for al in range(44, 53):
        for sp in range(-9, 9):
            x, z = gl(al, sp, g)
            cote = sp < -5 or sp > 4
            for y in range(SOL + 1, SOL + 16):
                if cote or y >= SOL + 9:
                    rose = al in (44, 45) and math.hypot(sp + .5, y - (SOL + 12)) < 2.2
                    put(x, y, z, t["verre"] if rose else t["accent"] if y == SOL + 15 else choix(t["mur"], h01(x, y, z, 5)))
                else:
                    put(x, y, z, AIR)
            put(x, SOL, z, t["sol"][0][1])
    for al in range(46, 50):                           # le balcon traverse la porte
        for sp in range(-9, 9):
            x, z = gl(al, sp, g)
            put(x, SOL + 9, z, QS)
            for y in range(SOL + 10, SOL + 13):
                put(x, y, z, AIR)
    for sp in (-8, 7):
        x, z = gl(45, sp, g)
        put(x, SOL + 16, z, t["lum"])
        put(x, SOL + 17, z, t["accent"])
    for sp, ligne1, ligne2 in ((0, "ARENE", t["nom"]), (-1, "ARENE", t["nom"])):
        x, z = gl(43, sp, g)
        put(x, SOL + 10, z, S("oak_wall_sign", facing=VERS_CENTRE[g], waterlogged=False))
        PANNEAUX[(x, SOL + 10, z)] = (ligne1, ligne2)
    # escalier vers le balcon, a droite de chaque porte
    for k in range(9):
        al = 36 + k
        for sp in (10, 11, 12):
            x, z = gl(al, sp, g)
            for y in range(SOL + 1, SOL + 1 + k):
                put(x, y, z, SB)
            put(x, SOL + 1 + k, z, S("stone_brick_stairs", facing=VERS_DEHORS[g], half="bottom", shape="straight", waterlogged=False))
    for al in (44, 45, 46):
        for sp in (10, 11, 12):
            x, z = gl(al, sp, g)
            if al > 44:
                put(x, SOL + 9, z, QS)
            put(x, SOL + 10, z, AIR)

# ====================================================================== 3. monument des quatre elements
for y in range(SOL + 3, SOL + 36):
    t_ = (y - SOL) / 36
    r_ = 4 - round(3 * t_)
    for i in range(-r_, r_):
        for j in range(-r_, r_):
            put(C + i, y, C + j, QC if (y - SOL) % 8 == 0 else QP if max(abs(i + .5), abs(j + .5)) >= r_ - 1 else QZ)
for y in range(SOL + 36, SOL + 38):
    for i in (-1, 0):
        for j in (-1, 0):
            put(C + i, y, C + j, LUM)


def pic(cx, cz, r0, h):
    for k in range(h + 1):
        r_ = r0 * (1 - k / h) ** 0.85
        for x, z in disque(cx + .5, cz + .5, r_ + 0.3):
            if SOL + 3 + k < SY:
                put(x, SOL + 3 + k, z, BLEU if h01(x, k, z, 8) < 0.55 else PACK)


cx, cz = gl(7, 0, GLACE)                               # glace : cristal
pic(cx, cz, 3, 15)
cx, cz = gl(7, 0, AIRE)                                # air : tourbillon de verre
for k in range(18):
    for tour in (0, 180):
        a = math.radians(k * 38 + tour)
        r_ = 1.2 + k * 0.14
        put(math.floor(cx + .5 + r_ * math.cos(a)), SOL + 3 + k, math.floor(cz + .5 + r_ * math.sin(a)), G_C if k % 2 else G_B)
cx, cz = gl(7, 0, FEU)                                 # feu : flamme de verre et de lumiere
for k in range(14):
    r_ = 2.8 * (1 - k / 14) ** 0.7
    for x, z in disque(cx + .5, cz + .5, r_):
        d = math.hypot(x + .5 - cx - .5, z + .5 - cz - .5)
        put(x, SOL + 3 + k, z, MAGMA if k == 0 else SHROOM if d < r_ * 0.45 else S("orange_stained_glass") if k % 2 else S("red_stained_glass"))
cx, cz = gl(7, 0, NATURE)                              # nature : arbre
for y in range(SOL + 3, SOL + 11):
    put(cx, y, cz, LOG)
for x, z in disque(cx + .5, cz + .5, 4.5):
    for y in range(SOL + 9, SOL + 16):
        if math.hypot(x - cx, z - cz) ** 2 + (y - SOL - 11.5) ** 2 * 1.6 < 19 and nom((x, y, z)) in (None, "air"):
            put(x, y, z, F("azalea" if h01(x, y, z, 9) < 0.6 else "flowering_azalea"))


# ====================================================================== 4. avenues
def mur_avenue(g, al, t, h=3):
    for sp in (-14, 13):
        x, z = gl(al, sp, g)
        for y in range(SOL + 1, SOL + 1 + h):
            put(x, y, z, choix(t["mur"], h01(x, y, z, 10)))


def cone_avenue(cx, cz, r0, h, g_):
    for k in range(h + 1):
        r_ = r0 * (1 - k / h) ** 0.85
        for x, z in disque(cx + .5, cz + .5, r_ + 0.3):
            if SOL + 1 + k < SY:
                put(x, SOL + 1 + k, z, BLEU if h01(x, k, z, g_) < 0.5 else PACK)


for al in range(47, 114):
    for g, t in THEMES.items():
        mur_avenue(g, al, t, 5 if al < 54 else 3)
        if al < 52:
            continue
        for sp in range(-13, 13):
            x, z = gl(al, sp, g)
            if dist(x, z) < R_HUB - 0.5:
                continue
            u = h01(x, 4, z, 11)
            if g == GLACE:
                put(x, SOL, z, doux(t["sol"], x, z) if -5 <= sp <= 4 else NEIGE)
            elif g == AIRE:
                put(x, SOL, z, LAINE if not -5 <= sp <= 4 else QS)
            elif g == FEU:
                put(x, SOL, z, doux(t["sol"], x, z))
            else:
                put(x, SOL, z, doux(t["sol"], x, z) if -5 <= sp <= 4 else MOSS if bruit(x, z, 8, 62) > 0.62 else GRASS)

# Glace : pics de plus en plus grands, soul lanterns
for k, al in enumerate(range(56, 112, 7)):
    for sp in (-10, 9):
        cone_avenue(*gl(al, sp, GLACE), 2.2 + k * 0.28, 6 + k * 3, 20 + k)
    x, z = gl(al + 3, -6, GLACE)
    put(x, SOL + 1, z, SLAN)
    x, z = gl(al + 3, 5, GLACE)
    put(x, SOL + 1, z, SLAN)

# Air : pont qui s'eleve au-dessus des nuages
HA_P = 6
for al in range(54, 112):
    h = min(HA_P, max(0, al - 57), max(0, 108 - al))
    montee = 57 < al <= 57 + HA_P
    descente = 108 - HA_P <= al < 108
    for sp in range(-5, 5):
        x, z = gl(al, sp, AIRE)
        if h == 0:
            continue
        if montee or descente:
            sens = VERS_DEHORS[AIRE] if montee else VERS_CENTRE[AIRE]
            put(x, SOL + h, z, S("quartz_stairs", facing=sens, half="bottom", shape="straight", waterlogged=False))
        else:
            put(x, SOL + h, z, QS)
    if h:
        for sp in (-6, 5):
            x, z = gl(al, sp, AIRE)
            put(x, SOL + h, z, CUC)
            put(x, SOL + h + 1, z, VITRE)
for al in range(62, 104, 10):                          # piles, pylones et chaines
    for sp in (-6, 5):
        x, z = gl(al, sp, AIRE)
        for y in range(SOL + 1, SOL + HA_P):
            put(x, y, z, QP)
        for y in range(SOL + HA_P + 1, SOL + HA_P + 9):
            put(x, y, z, QP)
        put(x, SOL + HA_P + 9, z, LUM)
    for sp in range(-6, 6):
        x, z = gl(al, sp, AIRE)
        put(x, SOL + HA_P + 9, z, CUC)
        if sp in (-3, 2):
            for y in range(SOL + HA_P + 8, SOL + HA_P + 5, -1):
                put(x, y, z, CHAINE)
            put(x, SOL + HA_P + 5, z, S("lantern", hanging=True, waterlogged=False))
for i in range(24):                                    # nuages de part et d'autre
    al = rng.randint(54, 111)
    sp = rng.choice((-1, 1)) * rng.randint(8, 12)
    cx, cz = gl(al, sp, AIRE)
    for x, z in disque(cx + .5, cz + .5, rng.uniform(2, 3.4)):
        for y in range(SOL + 1, SOL + 1 + rng.randint(1, 3)):
            if nom((x, y, z)) in (None, "air") and nom((x, SOL, z)) == "white_wool":
                put(x, y, z, LAINE)

# Feu : pont de pierre noire entre deux fosses de lave
for al in range(54, 112):
    for sp in list(range(-13, -6)) + list(range(6, 13)):
        x, z = gl(al, sp, FEU)
        put(x, SOL, z, AIR)
        put(x, SOL - 1, z, LAVE)
        put(x, SOL - 2, z, BLKB)
    for sp in (-6, 5):
        x, z = gl(al, sp, FEU)
        put(x, SOL, z, BLKB)
        put(x, SOL - 1, z, BLKB)
        put(x, SOL + 1, z, S("blackstone_wall", waterlogged=False))
for sp in list(range(-13, -6)) + list(range(6, 13)):
    for al in (53, 112):
        x, z = gl(al, sp, FEU)
        put(x, SOL, z, BLKB)
        put(x, SOL - 1, z, BLKB)
for al in range(52, 114):                              # bordure exterieure de la lave
    for sp in (-14, 13):
        x, z = gl(al, sp, FEU)
        put(x, SOL, z, BLKB)
        put(x, SOL - 1, z, BLKB)
for al in range(58, 110, 9):                           # piliers de basalte, braises
    for sp in (-10, 9):
        cx, cz = gl(al, sp, FEU)
        for x, z in disque(cx + .5, cz + .5, 1.4):
            for y in range(SOL - 1, SOL + 7):
                put(x, y, z, BAS)
        put(cx, SOL + 7, cz, MAGMA)
        put(cx, SOL + 8, cz, S("soul_lantern", hanging=False, waterlogged=False))
    x, z = gl(al, -6, FEU)
    put(x, SOL + 2, z, S("lantern", hanging=False, waterlogged=False))
    x, z = gl(al, 5, FEU)
    put(x, SOL + 2, z, S("lantern", hanging=False, waterlogged=False))

# Nature : allee de grands arbres, colonnes moussues
for k, al in enumerate(range(57, 112, 9)):
    for sp, bois, fl in ((-10, "oak", ["oak", "azalea"]), (9, "dark_oak", ["dark_oak", "flowering_azalea"])):
        cx, cz = gl(al, sp, NATURE)
        h = 9 + (k % 3) * 2
        for y in range(SOL + 1, SOL + 1 + h):
            put(cx, y, cz, S(f"{bois}_log", axis="y"))
        for x, z in disque(cx + .5, cz + .5, 6):
            for y in range(SOL + h - 2, SOL + h + 5):
                e = math.hypot(x - cx, z - cz) ** 2 / 28 + (y - SOL - h - 1) ** 2 / 11
                if e < 0.8 + 0.4 * h01(x, y, z, 12) and nom((x, y, z)) in (None, "air"):
                    if 0 <= x < SX and 0 <= z < SZ:
                        put(x, y, z, F(fl[int(h01(x, y, z, 13) * 5) % len(fl)]) if h01(x, y, z, 14) > 0.02 else SHROOM)
    if k % 2 == 0 and al + 4 < 113:
        for sp in (-6, 5):
            cx, cz = gl(al + 4, sp, NATURE)
            hh = 3 + (k % 3)
            for y in range(SOL + 1, SOL + 1 + hh):
                put(cx, y, cz, MSB)
            put(cx, SOL + 1 + hh, cz, S("lantern", hanging=False, waterlogged=False))
for g, t in THEMES.items():                            # le bord rond des arenes laisse un jour : on ferme le bout de chaque avenue
    for sp in list(range(-14, -5)) + list(range(5, 14)):
        x, z = gl(113, sp, g)
        put(x, SOL, z, choix(t["mur"], h01(x, 0, z, 15)))
        for y in range(SOL + 1, SOL + 5):
            put(x, y, z, choix(t["mur"], h01(x, y, z, 15)))
BARRIERE = S("barrier", waterlogged=False)              # paroi invisible au-dessus des murs : le decor ne sert pas de marchepied
for g in THEMES:
    for al in range(47, 114):
        for sp in (-14, 13):
            x, z = gl(al, sp, g)
            for y in range(SOL + 1, SY):
                if vide((x, y, z)):
                    put(x, y, z, BARRIERE)
    for sp in list(range(-14, -5)) + list(range(5, 14)):
        x, z = gl(113, sp, g)
        for y in range(SOL + 1, SY):
            if vide((x, y, z)):
                put(x, y, z, BARRIERE)
print("[phase] hub, avenues : blocs =", len(blocs), flush=True)

# ====================================================================== 5. eclairage calcule
SOURCES = {"sea_lantern": 15, "lantern": 15, "soul_lantern": 10, "shroomlight": 15, "lava": 15, "light": 15, "magma_block": 3}
NON_OPAQUE = {"air", "barrier", "lantern", "soul_lantern", "chain", "white_stained_glass_pane", "light", "stone_brick_stairs", "quartz_stairs", "oak_wall_sign",
              "blackstone_wall", "light_blue_stained_glass", "cyan_stained_glass", "orange_stained_glass", "red_stained_glass", "lime_stained_glass"}
SANS_SPAWN = NON_OPAQUE | {"lava"}
LUMIERE = S("light", level=15, waterlogged=False)


def calcul_lumiere():
    opaque = np.zeros((SX, SY, SZ), dtype=bool)
    opaque[:, :SOL + 1, :] = True
    L = np.zeros((SX, SY, SZ), dtype=np.int8)
    for (x, y, z), s in blocs.items():
        n = s[0].split(":")[1]
        opaque[x, y, z] = n not in NON_OPAQUE and not n.endswith("_leaves")
        if n in SOURCES:
            L[x, y, z] = SOURCES[n]
            opaque[x, y, z] = False
    for _ in range(14):
        N = L.copy()
        np.maximum(N[1:], L[:-1] - 1, out=N[1:])
        np.maximum(N[:-1], L[1:] - 1, out=N[:-1])
        np.maximum(N[:, 1:], L[:, :-1] - 1, out=N[:, 1:])
        np.maximum(N[:, :-1], L[:, 1:] - 1, out=N[:, :-1])
        np.maximum(N[:, :, 1:], L[:, :, :-1] - 1, out=N[:, :, 1:])
        np.maximum(N[:, :, :-1], L[:, :, 1:] - 1, out=N[:, :, :-1])
        N[opaque] = 0
        L = N
    return L


total = 0
for passe in range(30):
    L = calcul_lumiere()
    noires = []
    for (x, y, z), s in blocs.items():
        n = s[0].split(":")[1]
        if n in SANS_SPAWN or n.endswith("_leaves") or y + 2 >= SY:
            continue
        if vide((x, y + 1, z)) and vide((x, y + 2, z)) and L[x, y + 1, z] == 0:
            noires.append((x, y, z))
    if not noires:
        break
    pris = []
    for x, y, z in sorted(noires):
        if any(abs(x - a) < 10 and abs(z - b) < 10 and abs(y - c) < 6 for a, c, b in pris):
            continue
        pris.append((x, y, z))
        put(x, y + 1, z, LUMIERE)
        total += 1
print(f"[phase] eclairage : {total} points, surfaces noires restantes = {len(noires)}", flush=True)
if noires:
    raise SystemExit("il reste des surfaces noires")

# verifications
err = []
for p, s in blocs.items():
    x, y, z = p
    n = s[0].split(":")[1]
    if n == "lava":
        for q in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1), (x, y - 1, z)):
            b = nom(q)
            if b != "lava" and (b is None and q[1] > SOL or b in ("air", "light")):
                err.append(f"lave non contenue {p} -> {q} ({b})")
    if n.endswith("_leaves") and dict(s[1]).get("persistent") != "true":
        err.append(f"feuille non persistante en {p}")
    if n in ("chest", "barrel", "hopper", "spawner", "water"):
        err.append(f"bloc interdit {n} en {p}")
print("[phase] verification : erreurs =", len(err), err[:6], flush=True)
if err:
    raise SystemExit(1)

# ====================================================================== ecriture
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
    if (x, y, z) in PANNEAUX:
        l1, l2 = PANNEAUX[(x, y, z)]
        msgs = List[String]([String('""'), String(json.dumps({"text": l1, "bold": True})), String(json.dumps({"text": l2, "bold": True})), String('""')])
        vides = List[String]([String('""')] * 4)
        e["nbt"] = Compound({"id": String("minecraft:sign"), "is_waxed": Byte(1),
                             "front_text": Compound({"messages": msgs, "color": String("black"), "has_glowing_text": Byte(0)}),
                             "back_text": Compound({"messages": vides, "color": String("black"), "has_glowing_text": Byte(0)})})
    liste.append(e)
racine = nbtlib.File({"DataVersion": Int(3955), "size": List[Int]([Int(SX), Int(SY), Int(SZ)]),
                      "palette": List[Compound](palette), "blocks": List[Compound](liste),
                      "entities": List[Compound]([])}, gzipped=True)
sortie = RACINE / "exports" / f"{NOM}.nbt"
racine.save(sortie)

SPAWN = (C, SOL + 1, C + 20)
cibles = {}
for g, t in THEMES.items():
    x, z = gl(113, 0, g)
    cibles[f"bout de l'avenue {t['nom']}"] = [x, SOL + 1, z]
    x, z = gl(47, 0, g)
    cibles[f"balcon au-dessus de la porte {t['nom']}"] = [x, SOL + 10, z]
CIBLES.write_text(json.dumps({"_marque": "[claude-build]", "sol": SOL, "depart": list(SPAWN), "cibles": cibles}, ensure_ascii=False, indent=1), encoding="utf-8")

# fonctions : tout poser en une commande
ZONES = [("hub", -114, 78, -114, 227, 227), ("forge_v2", -126, 74, 114, 251, 251), ("arene_air", 114, 74, -96, 191, 191),
         ("arene_nature", -366, 68, -126, 251, 251), ("arene_glace", -126, 68, -366, 251, 251)]
f1 = ["# [claude-build] Etape 1 : charge les zones, regle le monde, puis lance l'etape 2 dans 30 secondes."]
for n_, x, y, z, dx, dz in ZONES:
    f1.append(f"forceload add {x} {z} {x + dx} {z + dz}")
f1 += ["gamerule doFireTick false", "gamerule doWeatherCycle false", "weather clear", "gamerule doMobSpawning false", "gamerule spawnRadius 0",
       "kill @e[type=!player]", 'tellraw @s {"text":"Chargement des zones, patiente 30 secondes...","color":"gold"}',
       "schedule function atelier:hub_pose 600t"]
f2 = ["# [claude-build] Etape 2 : pose le hub et les 4 arenes, ouvre les portes tournees vers le hub, ferme les autres portes du Feu."]
for n_, x, y, z, dx, dz in ZONES:
    f2.append(f"place template atelier:{n_} {x} {y} {z}")
f2 += ["# Air (porte ouest) : le verre disparait", "fill 114 87 -5 117 92 4 air",
       "# Nature (porte est) : les racines s'ecartent", "fill -117 87 -5 -115 95 4 air replace minecraft:mangrove_roots",
       "# Glace (porte sud) : herse levee, bouchon de glace retire", "fill -5 87 -117 4 95 -115 air",
       "# Feu : portes est, sud et ouest fermees par des barreaux",
       "fill 122 87 236 123 95 243 minecraft:iron_bars replace minecraft:air",
       "fill -124 87 236 -123 95 243 minecraft:iron_bars replace minecraft:air",
       "fill -4 87 362 3 95 363 minecraft:iron_bars replace minecraft:air",
       f"setworldspawn 0 87 20", "tp @a 0 87 20", "forceload remove all",
       'tellraw @a {"text":"Le Carrefour des Elements est pret !","color":"green"}']
(RACINE / "exports" / "hub_tout.mcfunction").write_text("\n".join(f1) + "\n", encoding="utf-8")
(RACINE / "exports" / "hub_pose.mcfunction").write_text("\n".join(f2) + "\n", encoding="utf-8")
if MONDE_OK:
    STRUCT.mkdir(parents=True, exist_ok=True)
    FONC.mkdir(parents=True, exist_ok=True)
    for n_ in ("hub", "forge_v2", "arene_air", "arene_nature", "arene_glace"):
        shutil.copyfile(RACINE / "exports" / f"{n_}.nbt", STRUCT / f"{n_}.nbt")
    shutil.copyfile(RACINE / "exports" / "hub_tout.mcfunction", FONC / "hub_tout.mcfunction")
    shutil.copyfile(RACINE / "exports" / "hub_pose.mcfunction", FONC / "hub_pose.mcfunction")
    mc = DATAPACK / "pack.mcmeta"
    if not mc.exists():
        mc.write_text('{"pack": {"pack_format": 48, "description": "Atelier [claude-build]"}}\n', encoding="utf-8")

# plan et vue
from PIL import Image, ImageDraw
COUL = {"smooth_stone": (165, 165, 165), "stone_bricks": (125, 125, 125), "polished_andesite": (135, 138, 136), "chiseled_stone_bricks": (130, 130, 130),
        "quartz_block": (235, 231, 224), "quartz_pillar": (236, 232, 226), "smooth_quartz": (238, 234, 228), "chiseled_quartz_block": (226, 222, 212),
        "packed_ice": (150, 190, 240), "blue_ice": (90, 150, 240), "snow_block": (240, 248, 252), "calcite": (222, 222, 216), "white_wool": (250, 250, 250),
        "waxed_oxidized_cut_copper": (78, 158, 132), "polished_blackstone": (60, 55, 65), "polished_blackstone_bricks": (70, 65, 75), "nether_bricks": (60, 30, 35),
        "red_nether_bricks": (90, 15, 20), "basalt": (80, 80, 85), "magma_block": (190, 70, 20), "shroomlight": (245, 170, 80), "lava": (255, 110, 0),
        "mossy_stone_bricks": (108, 118, 92), "mossy_cobblestone": (100, 115, 85), "moss_block": (88, 125, 45), "grass_block": (96, 150, 62), "coarse_dirt": (112, 82, 56),
        "oak_leaves": (70, 130, 45), "dark_oak_leaves": (45, 100, 35), "azalea_leaves": (95, 140, 50), "flowering_azalea_leaves": (150, 130, 130), "sea_lantern": (170, 235, 235),
        "cyan_stained_glass": (60, 170, 190), "light_blue_stained_glass": (120, 190, 240), "orange_stained_glass": (230, 120, 30), "red_stained_glass": (200, 40, 40),
        "lime_stained_glass": (120, 200, 40), "oak_log": (105, 80, 50), "dark_oak_log": (55, 40, 25)}
CACHE = {"air", "barrier", "light", "lantern", "soul_lantern", "chain", "white_stained_glass_pane", "oak_wall_sign"}
E = 4
img = Image.new("RGB", (SX * E + 20, SZ * E + 40), (92, 130, 72))
dr = ImageDraw.Draw(img)
hauts = {}
for (x, y, z), s in blocs.items():
    n = s[0].split(":")[1]
    if n not in CACHE and y > hauts.get((x, z), (-99, None))[0]:
        hauts[(x, z)] = (y, n)
for (x, z), (y, n) in hauts.items():
    c = COUL.get(n, (170, 170, 170))
    k = 0.75 + 0.25 * min(1, max(0, (y - SOL) / 20))
    dr.rectangle([10 + x * E, 30 + z * E, 10 + x * E + E - 1, 30 + z * E + E - 1], fill=tuple(int(v * k) for v in c))
dr.text((10, 8), "CARREFOUR DES ELEMENTS (nord en haut) : Glace N, Air E, Feu S, Nature O", fill=(255, 255, 255))
img.save(PLAN)
s_ = 4
Wd = (SX + SZ) * s_ + 40
Hh = (SX + SZ) * s_ // 2 + SY * s_ + 60
img = Image.new("RGB", (Wd, Hh), (120, 170, 110))
dr = ImageDraw.Draw(img)


def ouvert(p):
    n = nom(p)
    return (p[1] > SOL) if n is None else n in CACHE


vis = [(p, s[0].split(":")[1]) for p, s in blocs.items() if s[0].split(":")[1] not in CACHE]
vis.sort(key=lambda t: (t[0][0] + t[0][2], t[0][1]))
for (x, y, z), n in vis:
    c = COUL.get(n, (170, 170, 170))
    px = (x - z) * s_ + SZ * s_ + 20
    py = (x + z) * s_ // 2 - y * s_ + SY * s_ + 20
    if ouvert((x, y + 1, z)):
        dr.polygon([(px, py), (px + s_, py + s_ // 2), (px, py + s_), (px - s_, py + s_ // 2)], fill=c)
    if ouvert((x + 1, y, z)):
        dr.polygon([(px + s_, py + s_ // 2), (px, py + s_), (px, py + 2 * s_), (px + s_, py + s_ + s_ // 2)], fill=tuple(int(v * .78) for v in c))
    if ouvert((x, y, z + 1)):
        dr.polygon([(px - s_, py + s_ // 2), (px, py + s_), (px, py + 2 * s_), (px - s_, py + s_ + s_ // 2)], fill=tuple(int(v * .62) for v in c))
img.save(VUE)
print(f"Hub ecrit : {len(liste)} blocs, palette {len(palette)}, taille {SX}x{SY}x{SZ}, monde present : {MONDE_OK}")
