# [claude-build]
"""
arene_nature.py - Arene PvP "Nature sauvage" : cercle de 252 blocs (centre entre 4 blocs), arbre-monde, riviere,
labyrinthes de haies, grotte luxuriante, ruines. 7 phases verifiees, lumiere calculee.
  .venv\\Scripts\\python.exe sources\\arene_nature.py --monde "Arene Nature" --centre 0 0 --sol 86
Controle : .venv\\Scripts\\python.exe sources\\verif_acces.py exports\\arene_nature.nbt exports\\arene_nature_cibles.json
"""
import argparse
import json
import math
import random
from collections import deque
from pathlib import Path

import nbtlib
import numpy as np
from nbtlib import Compound, Int, List, String

ap = argparse.ArgumentParser()
ap.add_argument("--monde", default="Arene Nature")
ap.add_argument("--centre", nargs=2, type=int, default=[0, 0], metavar=("X", "Z"))
ap.add_argument("--sol", type=int, default=86)
ap.add_argument("--sous-sol", default="dirt")
ap.add_argument("--surface", default="grass_block")
ARGS = ap.parse_args()

NOM = "arene_nature"
RACINE = Path(__file__).resolve().parent.parent
SAVE = RACINE / "launcher/instances/Build-1.21.1/minecraft/saves" / ARGS.monde
MONDE_OK = (SAVE / "level.dat").exists()
DATAPACK = SAVE / "datapacks/atelier"
SORTIES = [RACINE / "exports" / f"{NOM}.nbt"] + ([DATAPACK / "data/atelier/structure" / f"{NOM}.nbt"] if MONDE_OK else [])
FONCTION = DATAPACK / "data/atelier/function" / f"{NOM}_effacer.mcfunction"
PLAN = RACINE / "exports" / f"{NOM}_plan.png"
VUE = RACINE / "exports" / f"{NOM}_vue.png"
LISTE_SALLES = RACINE / "salles_cachees_nature.md"
CIBLES = RACINE / "exports" / f"{NOM}_cibles.json"

DATA_VERSION = 3955
SX = SZ = 252
SY = 84
SOL = 18
W = SOL - 1                   # niveau de l'eau
C = 126
CEN = R = 126.0
R_MUR = 121.5
CENTRE_MONDE = tuple(ARGS.centre)
ORIGINE = (CENTRE_MONDE[0] - C, ARGS.sol - SOL, CENTRE_MONDE[1] - C)
rng = random.Random(25226)


# ====================================================================== outils
def S(nom_, **p):
    return ("minecraft:" + nom_, tuple(sorted((k, str(v).lower() if isinstance(v, bool) else str(v)) for k, v in p.items())))


AIR = S("air")
blocs = {}


def put(x, y, z, s):
    x, y, z = int(x), int(y), int(z) if (x == int(x) and y == int(y) and z == int(z)) else 1 / 0
    if not (0 <= x < SX and 0 <= y < SY and 0 <= z < SZ):
        raise ValueError(f"hors gabarit {(x, y, z)} {s[0]}")
    blocs[(x, y, z)] = s


def nom(p):
    s = blocs.get(p)
    return None if s is None else s[0].split(":")[1]


def vide(p):
    n = nom(p)
    if n is None:
        return p[1] > SOL
    return n == "air"


def dxz(x, z):
    return x + 0.5 - CEN, z + 0.5 - CEN


def dist(x, z):
    return math.hypot(*dxz(x, z))


def ang(x, z):
    a, b = dxz(x, z)
    return math.degrees(math.atan2(b, a)) % 360


def adiff(a, b):
    return abs((a - b + 180) % 360 - 180)


def point(r, a):
    a = math.radians(a)
    return CEN + r * math.cos(a), CEN + r * math.sin(a)


def cellule(r, a):
    x, z = point(r, a)
    return math.floor(x), math.floor(z)


def h01(x, y, z, g=0):
    h = (x * 374761393 + y * 1442695041 + z * 668265263 + g * 2246822519) & 0xFFFFFFFF
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


def choix(table, u):
    tot = sum(p for p, _ in table)
    u *= tot
    for p, s in table:
        if u < p:
            return s
        u -= p
    return table[-1][1]


def disque(cx, cz, r):
    for x in range(math.floor(cx - r - 1), math.ceil(cx + r) + 1):
        for z in range(math.floor(cz - r - 1), math.ceil(cz + r) + 1):
            if math.hypot(x + 0.5 - cx, z + 0.5 - cz) <= r:
                yield x, z


def monde(x, y, z):
    return ORIGINE[0] + x, ORIGINE[1] + y, ORIGINE[2] + z


def gl(al, sp, g):
    return {0: (C + al, C + sp), 90: (C - 1 - sp, C + al), 180: (C - 1 - al, C - 1 - sp), 270: (C + sp, C - 1 - al)}[g]


FACE = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}
FACE_ECH = {(-1, 0): "east", (1, 0): "west", (0, -1): "south", (0, 1): "north"}
VERS_CENTRE = {0: "west", 90: "north", 180: "east", 270: "south"}
DIR = {v: k for k, v in FACE.items()}

# ====================================================================== blocs
MSB = S("mossy_stone_bricks")
SB = S("stone_bricks")
CSB = S("cracked_stone_bricks")
CHB = S("chiseled_stone_bricks")
MCB = S("mossy_cobblestone")
COB = S("cobblestone")
PIERRE = S("stone")
MOSS = S("moss_block")
MUDB = S("mud_bricks")
PMUD = S("packed_mud")
MUD = S("mud")
RDIRT = S("rooted_dirt")
CDIRT = S("coarse_dirt")
POD = S("podzol", snowy=False)
GRASS = S("grass_block", snowy=False)
DIRT = S("dirt")
CLAY = S("clay")
GRAVEL = S("gravel")
MROOT = S("mangrove_roots", waterlogged=False)
MMROOT = S("muddy_mangrove_roots", axis="y")
SHROOM = S("shroomlight")
LANT = S("lantern", hanging=False, waterlogged=False)
LANT_P = S("lantern", hanging=True, waterlogged=False)
CHAINE = S("chain", axis="y", waterlogged=False)
EAU = S("water", level=0)
CHUTE = S("water", level=8)
EAU1 = S("water", level=1)
TRAPPE = S("oak_trapdoor", facing="north", half="top", open=False, powered=False, waterlogged=False)
LUMIERE = S("light", level=15, waterlogged=False)
BARRIERE = S("barrier", waterlogged=False)
TAPIS = S("moss_carpet")
LILY = S("lily_pad")
STEM = S("mushroom_stem")
RED = S("red_mushroom_block")
BROWN = S("brown_mushroom_block")


def LOG(n, axis="y"):
    return S(f"{n}_log", axis=axis)


def WOOD(n):
    return S(f"{n}_wood", axis="y")


def PL(n):
    return S(f"{n}_planks")


def F(n):
    return S(f"{n}_leaves", distance=1, persistent=True, waterlogged=False)


def ST(n, sens, half="bottom"):
    return S(f"{n}_stairs", facing=sens, half=half, shape="straight", waterlogged=False)


def DALLE(n):
    return S(f"{n}_slab", type="bottom", waterlogged=False)


def FENCE(n):
    return S(f"{n}_fence", waterlogged=False)


T_RUINE = [(45, MSB), (25, SB), (20, CSB), (10, MCB)]
T_RUINE2 = [(40, MCB), (30, MSB), (20, COB), (10, CSB)]
T_MUR = [(40, MSB), (25, MCB), (20, CSB), (15, SB)]
T_SALLE = [(40, MSB), (25, MUDB), (20, CSB), (15, MCB)]
T_SENTIER = [(45, CDIRT), (25, PMUD), (15, MCB), (15, S("gravel"))]
T_HERBE = [(62, GRASS), (20, MOSS), (10, POD), (8, RDIRT)]
T_LISIERE = [(45, POD), (25, CDIRT), (20, GRASS), (10, MOSS)]
T_BERGE = [(45, MUD), (30, MOSS), (25, RDIRT)]
T_LABY = [(70, GRASS), (30, MOSS)]
T_ILOT = [(55, MOSS), (35, GRASS), (10, POD)]
T_LIT = [(40, GRAVEL), (30, CLAY), (30, MUD)]

SOL_PLANTE = {"grass_block", "dirt", "coarse_dirt", "podzol", "moss_block", "rooted_dirt", "mud", "muddy_mangrove_roots"}
PLANTES = {"short_grass", "fern", "tall_grass", "large_fern", "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet",
           "oxeye_daisy", "cornflower", "lily_of_the_valley", "rose_bush", "peony", "lilac", "red_mushroom", "brown_mushroom"}
SANS_COLLISION = PLANTES | {"air", "vine", "glow_lichen", "cave_vines", "cave_vines_plant", "moss_carpet", "hanging_roots",
                            "spore_blossom", "light", "pink_petals", "lily_pad", "seagrass", "water", "ladder"}
NON_SOLIDES = SANS_COLLISION | {"lantern", "chain", "oak_trapdoor", "big_dripleaf", "big_dripleaf_stem", "small_dripleaf",
                                "azalea", "flowering_azalea"}
INTERDITS = {"chest", "trapped_chest", "barrel", "hopper", "dropper", "dispenser", "furnace", "blast_furnace", "smoker",
             "spawner", "ender_chest", "lava", "fire", "soul_fire", "bee_nest", "beehive"}

SALLES, ECHELLES, APPARITIONS = [], [], []
CIBLES_ACCES = {}
occ = {}


# ====================================================================== 1. champs du terrain
ix, iz = np.meshgrid(np.arange(SX), np.arange(SZ), indexing="ij")
DX, DZ = ix + 0.5 - CEN, iz + 0.5 - CEN
RR = np.hypot(DX, DZ)
TH = np.degrees(np.arctan2(DZ, DX)) % 360


def lisse(a, b, v):
    t = np.clip((v - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def ligne_polaire(pts):
    c = [point(r, a) for r, a in pts]
    out = []
    for i in range(len(c) - 1):
        p0, p1, p2, p3 = c[max(i - 1, 0)], c[i], c[i + 1], c[min(i + 2, len(c) - 1)]
        for k in range(24):
            t = k / 24
            out.append(tuple(0.5 * ((2 * p1[j]) + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t * t
                                    + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t ** 3) for j in (0, 1)))
    out.append(c[-1])
    return out


def distance_ligne(ligne):
    d = np.full((SX, SZ), 1e9)
    for px, pz in ligne[::2]:
        d = np.minimum(d, np.hypot(ix + 0.5 - px, iz + 0.5 - pz))
    return d


RIV_SO = ligne_polaire([(123, 167), (102, 158), (82, 168), (62, 157), (44, 150), (31, 144)])
RIV_NE = ligne_polaire([(31, 324), (44, 330), (62, 337), (82, 348), (102, 338), (123, 347)])
RAVINE = ligne_polaire([(40, 24), (50, 28), (60, 24), (66, 13)])
d_riv = np.minimum(distance_ligne(RIV_SO), distance_ligne(RIV_NE))
d_rav = distance_ligne(RAVINE)
N1 = np.array([[bruit(x, z, 23, 1) for z in range(SZ)] for x in range(SX)])
N2 = np.array([[bruit(x, z, 9, 2) for z in range(SZ)] for x in range(SX)])
N3 = np.array([[bruit(x, z, 6, 3) for z in range(SZ)] for x in range(SX)])
larg = 3.6 + 2.2 * N1
eau = (d_riv < larg) & (RR < 122)
douves = (RR + 3 * (N3 - 0.5) >= 26) & (RR + 3 * (N3 - 0.5) < 34)
eau |= douves
ETANGS = [(62, 95, 6), (96, 190, 6.5), (92, 282, 5.5), (52, 303, 6), (112, 75, 5), (47, 176, 5)]
for r_, a_, rad in ETANGS:
    px, pz = point(r_, a_)
    eau |= np.hypot(ix + 0.5 - px, iz + 0.5 - pz) + 3 * (N3 - 0.5) < rad

LABYS = {"SE": dict(c=52, span=20, bois="oak", feuilles=["oak", "azalea"], coeur=LOG("oak")),
         "SO": dict(c=122, span=20, bois="dark_oak", feuilles=["dark_oak", "oak"], coeur=SB),
         "NO": dict(c=225, span=24, bois="jungle", feuilles=["jungle", "flowering_azalea"], coeur=LOG("jungle")),
         "NE": dict(c=302, span=20, bois="spruce", feuilles=["spruce", "azalea"], coeur=MSB)}


def adiff_np(a):
    return np.abs((TH - a + 180) % 360 - 180)


laby_zone = np.zeros((SX, SZ), bool)
for q in LABYS.values():
    laby_zone |= (RR >= 71) & (RR <= 107.5) & (adiff_np(q["c"]) <= q["span"] + 2)

axes = np.minimum(np.abs(DX), np.abs(DZ))
sentier = (axes <= 3.6 + 1.0 * (N3 - 0.5)) & (RR >= 33) & (RR < 122)
for r0 in (69, 109.5):
    sentier |= np.abs(RR - r0) <= 2.2 + 1.2 * (N3 - 0.5)

# collines : (r, angle, rayon, hauteur)
COLLINES = [(90, 14, 21, 13), (54, 200, 16, 13), (52, 238, 13, 5), (42, 300, 12, 6), (96, 108, 11, 5), (86, 190, 9, 4),
            (114, 30, 9, 5), (114, 210, 9, 5), (114, 300, 8, 4), (46, 115, 8, 4), (112, 130, 8, 5)]
PLATS = []   # (cx, cz, rayon) : emplacements forces au niveau du sol

TOURS = {"NE": dict(a=315, r=58), "SE": dict(a=45, r=58), "SO": dict(a=135, r=58), "NO": dict(a=225, r=58)}
for t in TOURS.values():
    t["c"] = cellule(t["r"], t["a"])
    PLATS.append((*t["c"], 13))
JUNGLE = cellule(48, 70)
EPICEA = cellule(54, 290)
CERISIER = cellule(94, 95)
TEMPLE = cellule(52, 112)
PLATS += [(*JUNGLE, 11), (*EPICEA, 12), (*CERISIER, 8), (*TEMPLE, 17)]

f = lisse(0, 4, axes - 4) * lisse(1.5, 6, d_riv - larg) * lisse(35, 41, RR) * lisse(0, 3, 121 - RR)
f *= 1 - ((np.abs(RR - 69) < 3.5) * 1.0) * 0.9
f *= 1 - ((np.abs(RR - 109.5) < 3.5) * 1.0) * 0.9
hf = 4.4 * np.clip(N1 - 0.3, 0, 1) * 2 + 2.2 * (N2 - 0.45)
hf = np.clip(hf, 0, None)
for r_, a_, rad, h in COLLINES:
    px, pz = point(r_, a_)
    d = np.hypot(ix + 0.5 - px, iz + 0.5 - pz) * (0.9 + 0.2 * N2)
    hf = hf + h * np.cos(np.clip(d / rad, 0, 1) * math.pi / 2) ** 2
for cx, cz, rad in PLATS:
    d = np.hypot(ix + 0.5 - cx, iz + 0.5 - cz)
    f = f * lisse(rad, rad + 5, d)
f = f * (1 - laby_zone)
Hm = SOL + np.rint(hf * f).astype(int)
ile = RR < 26
Hm = np.where(ile, SOL + np.rint(3.2 * lisse(26, 15, RR) + 0.8 * N2 * lisse(26, 18, RR)).astype(int), Hm)
for g_ in range(4):                                   # arrivee des ponts au niveau du sol
    pass
Hm = np.where(ile & (axes <= 5) & (RR >= 18), SOL, Hm)
prof = 5.5 * lisse(0, 3, 3.6 - d_rav) * (0.8 + 0.4 * N3)
Hm = Hm - np.rint(prof).astype(int) * (d_rav < 3.6)
Hm = np.where(eau, SOL, Hm)

# colline a cascade : plateau, falaise au sud, bassin
HC = cellule(90, 14)
HCX, HCZ = HC
BZ0 = HCZ + 2                                          # premiere rangee du bassin
Y_CH = SOL + 12
for x in range(HCX - 10, HCX + 11):
    for z in range(HCZ - 9, BZ0):
        Hm[x, z] = max(Hm[x, z], Y_CH + (1 if abs(x - HCX) > 6 else 2))
BX0, BX1, BZ1 = HCX - 7, HCX + 7, BZ0 + 10
for x in range(BX0, BX1 + 1):
    for z in range(BZ0 - 1, BZ1 + 1):
        if math.hypot((x - HCX) / 8.5, (z - (BZ0 + 4)) / 6.8) <= 1.05 or z < BZ0 + 2:
            eau[x, z] = True
            Hm[x, z] = SOL


def Hs(x, z):
    return int(Hm[x, z])


def zone_plantable(x, z):
    return not eau[x, z] and not sentier[x, z] and RR[x, z] < 120


def reserver(cx, cz, r, role="reserve"):
    for c in disque(cx + 0.5, cz + 0.5, r):
        occ[c] = role


def libre(x, z, m=0):
    for a in range(-m, m + 1):
        for b in range(-m, m + 1):
            p = (x + a, z + b)
            if not (0 <= p[0] < SX and 0 <= p[1] < SZ) or p in occ or eau[p] or sentier[p] or RR[p] >= 120:
                return False
    return True


def echelle(x, z, y0, y1, sup, trappes=(), mat=MSB):
    for y in range(y0, y1 + 1):
        if y in trappes:
            put(x, y, z, TRAPPE)
            continue
        put(x, y, z, S("ladder", facing=FACE_ECH[sup], waterlogged=False))
        sp = (x + sup[0], y, z + sup[1])
        ECHELLES.append(((x, y, z), sp))
        if nom(sp) is None or nom(sp) in NON_SOLIDES:
            put(*sp, mat)


def percer(cx, cz, a, r0, r1, y, h, w=1, sol=None):
    ux, uz = math.cos(math.radians(a)), math.sin(math.radians(a))
    r_ = r0
    while r_ <= r1:
        for s in range(-w, w + 1):
            x = math.floor(cx + 0.5 + ux * r_ - uz * s)
            z = math.floor(cz + 0.5 + uz * r_ + ux * s)
            for yy in range(y, y + h):
                put(x, yy, z, AIR)
            if sol:
                put(x, y - 1, z, sol)
        r_ += 0.4


def vigne(x, y, z, sens, n):
    """lianes accrochees sur la face 'sens' (le mur est du cote sens) a partir de y vers le bas"""
    s = S("vine", **{FACE[sens]: True})
    while y > SOL and not solide((x + sens[0], y, z + sens[1])):
        y -= 1                                         # la premiere liane doit toucher le mur
    for k in range(n):
        if vide((x, y - k, z)):
            put(x, y - k, z, s)
        else:
            break


def lichen(x, y, z, face):
    if vide((x, y, z)):
        put(x, y, z, S("glow_lichen", waterlogged=False, **{face: True}))


def solide(p):
    n = nom(p)
    if n is None:
        return p[1] <= SOL
    return n not in NON_SOLIDES


def sous(p):
    n = nom((p[0], p[1] - 1, p[2]))
    return n if n is not None else ("grass_block" if p[1] - 1 <= SOL else "air")


def verifier(phase):
    err = []
    for p, s in blocs.items():
        x, y, z = p
        n = s[0].split(":")[1]
        st = dict(s[1])
        if n in INTERDITS or n.endswith("shulker_box"):
            err.append(f"bloc interdit {n} en {p}")
        if n != "air" and dist(x, z) > R + 1e-9:
            err.append(f"depasse l'enceinte : {n} en {p}")
        if n.endswith("_leaves") and st.get("persistent") != "true":
            err.append(f"feuille non persistante en {p}")
        if n == "water":
            lv = st["level"]
            if lv == "0":
                for q in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1), (x, y - 1, z)):
                    gorge = q in blocs and dict(blocs[q][1]).get("waterlogged") == "true"
                    if nom(q) != "water" and not solide(q) and not gorge:
                        err.append(f"eau non contenue {p} -> {q} ({nom(q)})")
            elif nom((x, y - 1, z)) != "water":
                err.append(f"chute sans arrivee en {p}")
        elif n in PLANTES:
            if st.get("half") == "upper":
                if nom((x, y - 1, z)) != n:
                    err.append(f"plante double incomplete en {p}")
            elif n in ("red_mushroom", "brown_mushroom"):
                if sous(p) != "podzol":
                    err.append(f"champignon hors podzol en {p}")
            elif sous(p) not in SOL_PLANTE:
                err.append(f"plante {n} sur {sous(p)} en {p}")
        elif n in ("azalea", "flowering_azalea"):
            if sous(p) not in SOL_PLANTE | {"clay"}:
                err.append(f"azalee sur {sous(p)} en {p}")
        elif n == "lily_pad":
            b = blocs.get((x, y - 1, z))
            if b != EAU:
                err.append(f"nenuphar sans eau source en {p}")
        elif n == "moss_carpet":
            if sous(p) == "air":
                err.append(f"tapis dans le vide en {p}")
        elif n in ("big_dripleaf", "big_dripleaf_stem"):
            if sous(p) not in {"big_dripleaf_stem", "clay", "moss_block", "dirt", "mud", "grass_block", "gravel"} or sous(p) == "gravel":
                err.append(f"grande feuille sur {sous(p)} en {p}")
        elif n == "small_dripleaf":
            if st["half"] == "lower" and sous(p) not in ("clay", "moss_block"):
                err.append(f"petite feuille sur {sous(p)} en {p}")
        elif n in ("cave_vines", "cave_vines_plant"):
            if nom((x, y + 1, z)) != "cave_vines_plant" and not solide((x, y + 1, z)):
                err.append(f"baies sans accroche en {p}")
        elif n in ("spore_blossom", "hanging_roots"):
            if not solide((x, y + 1, z)):
                err.append(f"{n} sans plafond en {p}")
        elif n == "vine":
            for k, v in st.items():
                if v == "true" and k in DIR:
                    d = DIR[k]
                    q = (x + d[0], y, z + d[1])
                    au_dessus = blocs.get((x, y + 1, z))
                    if not solide(q) and not (au_dessus and au_dessus[0].endswith(":vine") and dict(au_dessus[1]).get(k) == "true"):
                        err.append(f"liane sans mur en {p}")
        elif n == "glow_lichen":
            for k, v in st.items():
                if v == "true" and k in DIR and not solide((x + DIR[k][0], y, z + DIR[k][1])):
                    err.append(f"lichen sans mur en {p}")
                if v == "true" and k == "down" and not solide((x, y - 1, z)):
                    err.append(f"lichen sans sol en {p}")
                if v == "true" and k == "up" and not solide((x, y + 1, z)):
                    err.append(f"lichen sans plafond en {p}")
        elif n == "lantern":
            q = (x, y + 1, z) if st["hanging"] == "true" else (x, y - 1, z)
            if not solide(q) and nom(q) != "chain":
                err.append(f"lanterne sans appui en {p}")
    for e, sp in ECHELLES:
        if nom(e) == "ladder" and not solide(sp):
            err.append(f"echelle {e} sans support ({nom(sp)})")
    for num, (cx, cy, cz), _ in SALLES:
        if nom((cx, cy, cz)) != "air" or nom((cx, cy + 1, cz)) != "air" or not solide((cx, cy - 1, cz)):
            err.append(f"salle {num} : coffre non degage en {(cx, cy, cz)} ({nom((cx, cy, cz))})")
    print(f"[phase] {phase:<30} blocs={len(blocs):>7}  erreurs={len(err)}", flush=True)
    if err:
        for e in err[:25]:
            print("   !", e)
        raise SystemExit(1)


# ---- ecriture du terrain
for x in range(SX):
    for z in range(SZ):
        d = RR[x, z]
        if d >= R_MUR + 1:
            continue
        if eau[x, z]:
            fond = W - 2 - int(2 * N2[x, z])
            put(x, SOL, z, AIR)
            for y in range(fond + 1, W + 1):
                put(x, y, z, EAU)
            put(x, fond, z, choix(T_LIT, h01(x, 0, z, 4)))
            continue
        h = Hs(x, z)
        u = h01(x, 1, z, 5)
        bord = any(0 <= x + a < SX and 0 <= z + b < SZ and eau[x + a, z + b] for a, b in ((2, 0), (-2, 0), (0, 2), (0, -2)))
        if sentier[x, z] and d > 33:
            t = T_SENTIER
        elif bord:
            t = T_BERGE
        elif ile[x, z]:
            t = T_ILOT
        elif laby_zone[x, z]:
            t = T_LABY
        elif d > 106:
            t = T_LISIERE
        else:
            t = T_HERBE
        put(x, h, z, choix(t, u))
        for y in range(h - 1, SOL, -1):
            put(x, y, z, RDIRT if h01(x, y, z, 6) < 0.15 else DIRT)
        if h > SOL:
            put(x, SOL, z, DIRT)
        for y in range(h + 1, SOL + 1):
            put(x, y, z, AIR)
for x in range(SX):                                   # parois de la ravine : roche et mousse
    for z in range(SZ):
        if d_rav[x, z] < 5 and not eau[x, z]:
            h = Hs(x, z)
            for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                h2 = Hs(x + a, z + b)
                for y in range(h + 1, h2):
                    if nom((x + a, y, z + b)) in ("dirt", "rooted_dirt", None):
                        put(x + a, y, z + b, choix(T_RUINE2, h01(x, y, z, 7)))


def cascade(xc, zc, sortie, ytop, w):
    """colonne d'eau en (xc, zc) +/- w, sources dans l'encoche du cote 'sortie'"""
    tx, tz = sortie[1], sortie[0]
    for s in range(-w, w + 1):
        x, z = xc + tx * s, zc + tz * s
        sx_, sz_ = x + sortie[0], z + sortie[1]
        for y in range(ytop + 2, W, -1):
            put(x, y, z, AIR)
        put(sx_, ytop, sz_, EAU)
        put(sx_, ytop - 1, sz_, MSB)
        put(sx_, ytop + 1, sz_, MCB)
        put(sx_ + sortie[0], ytop, sz_ + sortie[1], MSB)
        put(x, ytop, z, EAU1)
        for y in range(ytop - 1, W, -1):
            put(x, y, z, CHUTE)
        if nom((x, W, z)) != "water":
            put(x, W, z, EAU)
            put(x, W - 1, z, CLAY)
    for s in (-w - 1, w + 1):
        put(xc + tx * s + sortie[0], ytop, zc + tz * s + sortie[1], MSB)
        put(xc + tx * s + sortie[0], ytop + 1, zc + tz * s + sortie[1], MCB)


# bassin et cascade de la colline SE (la cascade tombe vers le sud)
CASC = (HCX, BZ0 - 1)

# grotte luxuriante
CVX, CVZ = cellule(70, 250)
CVY = 9
CAVE_FOND = {}
for x in range(CVX - 28, CVX + 29):
    for z in range(CVZ - 22, CVZ + 23):
        e = ((x - CVX) / 24) ** 2 + ((z - CVZ) / 18) ** 2
        e += 0.35 * (bruit(x, z, 7, 8) - 0.5)
        if e > 1.28:
            continue
        if e > 1:
            for y in range(CVY - 7, CVY + 7):
                put(x, y, z, choix([(50, PIERRE), (30, MOSS), (20, MCB)], h01(x, y, z, 9)))
            continue
        fond = CVY - 4 + int(2 * bruit(x, z, 6, 10))
        haut = CVY + 3 + int(3 * (1 - e)) + int(2 * bruit(x, z, 5, 11))
        CAVE_FOND[(x, z)] = (fond, haut)
        for y in range(fond - 2, haut + 3):
            if y <= fond or y > haut:                  # la couche "fond" est le sol de mousse
                put(x, y, z, MOSS if y == fond or y == haut + 1 else choix([(60, PIERRE), (40, MCB)], h01(x, y, z, 12)))
            else:
                put(x, y, z, AIR)
for (x, z), (fond, haut) in CAVE_FOND.items():        # mares de la grotte
    if bruit(x, z, 9, 13) > 0.68:
        ok = all((x + a, z + b) in CAVE_FOND and CAVE_FOND[(x + a, z + b)][0] >= fond for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if ok:
            put(x, fond, z, CLAY)
for (x, z), (fond, haut) in list(CAVE_FOND.items()):
    if nom((x, fond, z)) == "clay" and all(solide((x + a, fond + 1, z + b)) or nom((x + a, fond, z + b)) == "clay" and False for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
        pass

# escalier d'entree de la grotte, depuis la surface
ENT_X, ENT_Z = CVX + 34, CVZ
for k in range(0, SOL - (CVY - 5)):
    x = ENT_X - k
    y = SOL - 1 - k
    for s in range(-2, 3):
        z = ENT_Z + s
        if abs(s) == 2:
            for yy in range(y, min(y + 5, SOL + 1)):
                put(x, yy, z, choix(T_RUINE, h01(x, yy, z, 14)))
        else:
            put(x, y, z, S("mossy_stone_brick_stairs", facing="east", half="bottom", shape="straight", waterlogged=False))
            put(x, y - 1, z, MSB)
            for yy in range(y + 1, min(y + 5, SOL + 1)):
                put(x, yy, z, AIR)
            if y + 5 <= SOL:
                put(x, y + 5, z, MSB)
    if (x, ENT_Z) in CAVE_FOND and k > 6:
        fond = CAVE_FOND[(x, ENT_Z)][0]
        if y <= fond + 1:
            break
for s in (-1, 0, 1):
    for x in range(ENT_X + 1, ENT_X + 3):
        put(x, SOL, ENT_Z + s, MCB)
for dz_ in (-3, 3):                                    # arche d'entree
    for y in range(SOL + 1, SOL + 6):
        put(ENT_X, y, ENT_Z + dz_, choix(T_RUINE, h01(y, 0, dz_, 15)))
for s in range(-3, 4):
    put(ENT_X, SOL + 6, ENT_Z + s, MSB)
reserver(ENT_X - 6, ENT_Z, 11, "grotte")
for x in range(ENT_X - 14, ENT_X + 3):
    for z in range(ENT_Z - 3, ENT_Z + 4):
        sentier[x, z] = False
verifier("1. terrain, riviere, grotte")

# ====================================================================== 2. rempart et 4 portes
for x in range(SX):
    for z in range(SZ):
        d = RR[x, z]
        if d < R_MUR or d >= R:
            continue
        a = TH[x, z]
        n = bruit(a * 1.1, 0, 7, 20)
        hm = SOL + 15 + int(4 * n) + int(2.4 * h01(int(a * 1.6), 0, 0, 19))
        breche = bruit(a * 1.1, 3, 9, 21) > 0.68
        coupe = SOL + 7 + int(5 * bruit(a * 2, 5, 3, 22)) if breche else hm + 1
        for y in range(SOL, hm + 1):
            if y >= coupe:
                put(x, y, z, MROOT if h01(x, y, z, 23) < 0.55 else LOG("mangrove") if h01(x, y, z, 24) < 0.5 else MMROOT)
            else:
                put(x, y, z, MCB if y == SOL else choix(T_MUR, h01(x, y, z, 25)))
        if h01(x, 0, z, 26) < 0.6:
            put(x, hm + 1, z, MOSS)
        if d >= 125:
            for y in range(hm + 2, SY):
                put(x, y, z, BARRIERE)
for a in range(6, 360, 18):                            # contreforts et eboulis contre le rempart
    if min(adiff(a, g) for g in (0, 90, 180, 270)) < 11 or adiff(a, 167) < 9 or adiff(a, 347) < 9:
        continue
    px, pz = point(119.8, a)
    hc = 7 + int(9 * h01(a, 0, 0, 18))
    for x, z in disque(px, pz, 3.1):
        if RR[x, z] < R_MUR and not eau[x, z]:
            for y in range(Hs(x, z) + 1, SOL + hc + int(3 * h01(x, 1, z, 17))):
                put(x, y, z, choix(T_RUINE2, h01(x, y, z, 16)))
            put(x, SOL + hc + 3, z, TAPIS) if False else None
            occ[(x, z)] = "contrefort"
    for x, z in disque(*point(116.5, a + 3), 3.4):     # eboulis
        if RR[x, z] < R_MUR and not eau[x, z] and not sentier[x, z] and h01(x, 2, z, 15) < 0.55:
            for y in range(Hs(x, z) + 1, Hs(x, z) + 1 + int(1 + 2 * h01(x, 3, z, 14))):
                put(x, y, z, choix(T_RUINE2, h01(x, y, z, 13)))
for x in range(SX):                                   # lianes sur la face interieure du rempart
    for z in range(SZ):
        if not (R_MUR - 1.5 <= RR[x, z] < R_MUR) or eau[x, z]:
            continue
        for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if RR[x + a, z + b] >= R_MUR and h01(x, 2, z, 27) < 0.34:
                ys = [y for y in range(SY - 1, SOL, -1) if solide((x + a, y, z + b))]
                if ys:
                    top = ys[0]
                    vigne(x, top, z, (a, b), 4 + int(6 * h01(x, 3, z, 28)))
                    break

BOIS_PORTE = {0: ("jungle", "jungle"), 90: ("oak", "azalea"), 180: ("dark_oak", "dark_oak"), 270: ("mangrove", "mangrove")}
for g, (b, fl) in BOIS_PORTE.items():
    for al in range(112, 126):                        # passage vers le rempart, ferme au fond par des racines
        for sp in range(-5, 5):
            x, z = gl(al, sp, g)
            if dist(x, z) >= R:
                continue
            hh = SOL + 9 + int(3 * (1 - ((sp + 0.5) / 5) ** 2))
            if al >= 120:
                put(x, SOL, z, MCB)
                for y in range(SOL + 1, hh + 1):
                    put(x, y, z, MROOT if al >= 123 else AIR)
    for sp, rad in ((8, 3.6), (-9, 3.6)):             # troncs geants formant le portail
        tx, tz = gl(115, sp, g)
        for y in range(SOL, SOL + 28):
            r_ = rad * (1.5 if y < SOL + 3 else 1 - 0.3 * (y - SOL) / 28)
            for x, z in disque(tx + .5, tz + .5, r_):
                if dist(x, z) < R_MUR - 0.5:
                    put(x, y, z, WOOD(b) if y < SOL + 26 else LOG(b))
        reserver(tx, tz, 8, "porte")
    for sp in range(-9, 9):                           # linteau de pierre et de racines
        for al in (114, 115, 116):
            x, z = gl(al, sp, g)
            base = SOL + 12 + int(3 * (1 - ((sp + 0.5) / 9) ** 2))
            for y in range(base, SOL + 19):
                put(x, y, z, MROOT if y >= SOL + 17 else choix(T_RUINE, h01(x, y, z, 29)))
    cx, cz = gl(115, 0, g)
    for dx, dz in ((0, 0), (9, 0), (-9, 0), (0, 9), (0, -9)):
        px, pz = cx + dx, cz + dz
        if dist(px, pz) < 116:
            for x, z in disque(px + .5, pz + .5, 6.5):
                for y in range(SOL + 20, SOL + 28):
                    e = math.hypot(x - px, z - pz) / 6.5 + abs(y - (SOL + 24)) / 5
                    if e < 1.1 and nom((x, y, z)) is None and dist(x, z) < 121:
                        put(x, y, z, F(fl))
    for sp in range(-5, 5, 3):                        # lanternes et lianes sous le linteau
        x, z = gl(115, sp, g)
        for y in range(SOL + 15, SOL + 8, -1):
            if vide((x, y, z)) and solide((x, y + 1, z)):
                put(x, y, z, CHAINE)
                put(x, y - 1, z, LANT_P)
                break
    reserver(*gl(118, 0, g), 8, "porte")
verifier("2. rempart, 4 portes")


# ====================================================================== 3. arbres geants, tours, centre
def spirale(cx, cz, r0, r1, ybas, montee, pitch, a0, plein, dalle):
    pas = 360 / (2 * pitch)
    tmax = montee * 2
    for x in range(math.floor(cx - r1 - 1), math.ceil(cx + r1) + 2):
        for z in range(math.floor(cz - r1 - 1), math.ceil(cz + r1) + 2):
            d = math.hypot(x - cx, z - cz)
            if not r0 <= d <= r1:
                continue
            la = math.degrees(math.atan2(z - cz, x - cx)) % 360
            t0 = (la - a0) % 360
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


def feuillage(cx, cy, cz, rx, ry, rz, feuilles, g, lum=0.012):
    for x in range(math.floor(cx - rx - 2), math.ceil(cx + rx) + 3):
        for z in range(math.floor(cz - rz - 2), math.ceil(cz + rz) + 3):
            if not (0 <= x < SX and 0 <= z < SZ) or RR[x, z] >= 124:
                continue
            for y in range(math.floor(cy - ry - 1), min(SY - 1, math.ceil(cy + ry) + 1)):
                e = ((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy) / ry) ** 2 + ((z + .5 - cz) / rz) ** 2
                if e <= 0.72 + 0.5 * bruit(x + y, z - y, 3, g) and nom((x, y, z)) is None and y > SOL:
                    u = h01(x, y, z, g)
                    if e < 0.6 and u < lum:
                        put(x, y, z, SHROOM)
                    else:
                        put(x, y, z, F(feuilles[int(u * 7.3) % len(feuilles)]))


def racines(cx, cz, r0, n, longueur, bois, y0=None, g=30):
    for i in range(n):
        a = 360 * i / n + 12 * h01(i, 0, g, 1)
        ax, az = math.cos(math.radians(a)), math.sin(math.radians(a))
        for k in range(int(longueur)):
            t = k / longueur
            a2 = math.radians(a + 26 * math.sin(t * 3 + i))
            x = math.floor(cx + .5 + (r0 + k) * math.cos(a2))
            z = math.floor(cz + .5 + (r0 + k) * math.sin(a2))
            if not (0 <= x < SX and 0 <= z < SZ) or RR[x, z] >= 120 or eau[x, z] or sentier[x, z]:
                continue
            h = Hs(x, z) + max(0, round(4 * (1 - t) ** 1.5))
            for yy in range(Hs(x, z) + 1, h + 1):
                put(x, yy, z, bois if h01(x, yy, z, g) < 0.7 else MROOT)
            if k < longueur * 0.6:
                put(x + 1, h, z, bois) if nom((x + 1, h, z)) is None else None


def arbre_geant(cx, cz, rad, h, bois, feuilles, rc, g):
    """tronc plein evase, branches et couronne"""
    y0 = Hs(cx, cz)
    for y in range(y0, SOL + h + 1):
        t = (y - y0) / max(1, SOL + h - y0)
        r_ = rad * (1 - 0.5 * t) + (3.5 * (1 - t / 0.14) ** 2 if t < 0.14 else 0)
        for x, z in disque(cx + .5, cz + .5, r_ + 1):
            if math.hypot(x - cx, z - cz) <= r_ * (0.9 + 0.2 * bruit(x + y, z, 4, g)):
                put(x, y, z, S(f"{bois}_wood", axis="y") if h01(x, y, z, g) < 0.85 else MOSS)
    racines(cx, cz, rad, 9, rad + 8, S(f"{bois}_wood", axis="y"), g=g)
    for i in range(5):
        a = math.radians(72 * i + 20)
        for k in range(int(rc * 0.8)):
            y = SOL + h - 5 + k // 3
            x, z = math.floor(cx + .5 + k * math.cos(a)), math.floor(cz + .5 + k * math.sin(a))
            if RR[x, z] < 121:
                put(x, y, z, S(f"{bois}_wood", axis="y"))
        px, pz = cx + rc * 0.7 * math.cos(a), cz + rc * 0.7 * math.sin(a)
        feuillage(px, SOL + h + 2, pz, rc * 0.55, 5, rc * 0.55, feuilles, g + i)
    feuillage(cx + .5, SOL + h + 5, cz + .5, rc * 0.8, 7, rc * 0.8, feuilles, g + 7)


def tour_arbre(cx, cz, rext, h, bois, feuilles, a0, g):
    """arbre geant creux : escalier interieur, terrasse au sommet"""
    wood = S(f"{bois}_wood", axis="y")
    for y in range(SOL, SOL + h + 1):
        t = (y - SOL) / h
        r_ = rext - 1.4 * t + (3 * (1 - t / 0.12) ** 2 if t < 0.12 else 0)
        for x, z in disque(cx + .5, cz + .5, r_ + 1):
            d = math.hypot(x - cx, z - cz)
            if d <= 4.3:
                put(x, y, z, S(f"{bois}_planks") if y == SOL else AIR)
            elif d <= r_ * (0.92 + 0.16 * bruit(x + y, z, 4, g)):
                put(x, y, z, wood if h01(x, y, z, g) < 0.9 else MOSS)
    for y in range(SOL + 1, SOL + h + 1):
        for x, z in disque(cx + .5, cz + .5, 1.5):
            put(x, y, z, wood)
    spirale(cx, cz, 1.5, 4.3, SOL, h, 9, a0, S(f"{bois}_planks"), DALLE(bois))
    pas = 20
    for x, z in disque(cx + .5, cz + .5, rext + 1.5):
        d = math.hypot(x - cx, z - cz)
        la = math.degrees(math.atan2(z - cz, x - cx)) % 360
        t0 = (la - a0) % 360
        dernier = t0 + 360 * ((h * 2 * pas - t0) // 360)
        if 1.5 <= d <= 4.3 and dernier >= (h - 4) * 2 * pas:
            continue
        put(x, SOL + h, z, S(f"{bois}_planks"))
        if d > rext - 0.4:
            put(x, SOL + h + 1, z, FENCE(bois))
    for x, z in disque(cx + .5, cz + .5, 1.5):
        put(x, SOL + h, z, wood)
    put(cx, SOL + h + 1, cz, LANT)
    percer(cx, cz, a0 - 8, 3.4, rext + 3.6, SOL + 1, 3)
    racines(cx, cz, rext, 8, rext + 7, wood, g=g)
    for i in range(6):
        a = math.radians(60 * i)
        feuillage(cx + 9 * math.cos(a), SOL + h + 8, cz + 9 * math.sin(a), 8, 3.5, 8, feuilles, g + i)
    for i in range(4):                                 # branches porteuses de la couronne
        a = math.radians(90 * i + 45)
        for k in range(int(rext), 14):
            x, z = math.floor(cx + .5 + k * math.cos(a)), math.floor(cz + .5 + k * math.sin(a))
            put(x, SOL + h + (k - int(rext)) // 2 + 1, z, wood)


def mesure(f_, dx, dz):
    a, b = abs(dx), abs(dz)
    return math.hypot(dx, dz) if f_ == "rond" else max(a, b)


def tour_pierre(cx, cz, forme, ri, ro, H_, a_porte, ruine):
    for x in range(cx - ro - 1, cx + ro + 2):
        for z in range(cz - ro - 1, cz + ro + 2):
            m = mesure(forme, x - cx, z - cz)
            if m > ro:
                continue
            if m > ri:
                hm = H_ + 1 + (int(7 * bruit(x, z, 3, 40)) if ruine else (x + z) % 2 * 2)
                for y in range(SOL + 1, hm + 1):
                    e = y - SOL
                    fen = e % 8 in (4, 5, 6) and (x + z) % 7 < 2 and e < H_ - SOL
                    put(x, y, z, AIR if fen else CHB if e % 10 == 0 else choix(T_RUINE, h01(x, y, z, 41)))
            else:
                put(x, SOL, z, MCB)
                for y in range(SOL + 1, H_):
                    put(x, y, z, AIR)
                put(x, SOL + 12, z, PL("spruce"))
                put(x, H_, z, MSB)
    for y in range(SOL + 1, H_ + 2):
        put(cx, y, cz, CHB if y % 6 == 0 else SB)
    echelle(cx + 1, cz, SOL + 1, H_ + 1, (-1, 0))
    percer(cx, cz, a_porte, ri - 1, ro * 1.45 + 1.5, SOL + 1, 3, sol=MCB)
    for y in (SOL + 1, SOL + 13, H_ + 1):
        for dx, dz in ((3, 3), (-3, -3)):
            if vide((cx + dx, y, cz + dz)):
                put(cx + dx, y, cz + dz, LANT)
    for x in range(cx - ro - 1, cx + ro + 2):          # lianes sur les murs
        for z in range(cz - ro - 1, cz + ro + 2):
            m = mesure(forme, x - cx, z - cz)
            if ro < m <= ro + 1 and h01(x, 7, z, 42) < 0.4:
                for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if mesure(forme, x + a - cx, z + b - cz) <= ro:
                        vigne(x, H_ - int(5 * h01(x, 8, z, 43)), z, (a, b), 5 + int(9 * h01(x, 9, z, 44)))
                        break
    CIBLES_ACCES[f"haut de la tour {'SE' if ruine else 'SO'}"] = [cx - 2, H_ + 1, cz]


# arbre-monde central
A0 = 180
WT_H = 46
WT_R = 9.5
bois_m = S("oak_wood", axis="y")
for y in range(SOL, SOL + WT_H + 1):
    t = (y - SOL) / WT_H
    r_ = WT_R - 2.6 * t + (5.5 * (1 - t / 0.16) ** 2 if t < 0.16 else 0)
    for x, z in disque(CEN, CEN, r_ + 1):
        d = dist(x, z)
        if d <= 6.2:
            put(x, y, z, PL("oak") if y == SOL else AIR)
        elif d <= r_ * (0.92 + 0.16 * bruit(x + y, z, 5, 50)):
            put(x, y, z, MOSS if h01(x, y, z, 51) < 0.08 else bois_m)
for y in range(SOL + 1, SOL + WT_H + 1):
    for x, z in disque(CEN, CEN, 1.6):
        put(x, y, z, bois_m)
spirale(CEN - .5, CEN - .5, 1.7, 6.2, SOL, WT_H, 12, A0, PL("oak"), DALLE("oak"))
percer(CEN - .5, CEN - .5, A0 - 6, 5.2, 16, SOL + 1, 4, w=2)
for x, z in disque(CEN, CEN, 6.8):                    # terrasse du sommet, tremie au-dessus de l'escalier
    d = dist(x, z)
    t0 = (ang(x, z) - A0) % 360
    dernier = t0 + 360 * ((WT_H * 2 * 15 - t0) // 360)
    if 1.7 <= d <= 6.2 and dernier >= (WT_H - 4) * 30:
        continue
    put(x, SOL + WT_H, z, PL("oak"))
# balcon a mi-hauteur : le colimacon passe a la cote SOL+24 pour l'angle A0
Y_B = SOL + 24
percer(CEN - .5, CEN - .5, A0 + 8, 5.2, 11.6, Y_B + 1, 3, w=1, sol=PL("oak"))
for x, z in disque(CEN, CEN, 16):
    d = dist(x, z)
    if 9.5 <= d <= 15.5:
        if nom((x, Y_B, z)) is None:
            put(x, Y_B, z, PL("spruce"))
        if d >= 14.8:
            put(x, Y_B + 1, z, FENCE("spruce"))
    if 9.5 <= d <= 15.5 and int(ang(x, z) / 30) % 3 == 0 and d < 11:
        put(x, Y_B - 1, z, bois_m)
for a in range(0, 360, 45):
    x, z = cellule(15, a + 22)
    for y in range(Y_B + 1, Y_B + 4):
        put(x, y, z, LOG("spruce"))
    put(x, Y_B + 4, z, LANT)
    x, z = cellule(10.5, a)
    for k in range(5):
        put(*cellule(10.5 + k, a)[:1], Y_B - 1 - (4 - k) // 2, cellule(10.5 + k, a)[1], bois_m)
for i in range(12):                                   # grandes branches et couronne
    a = 30 * i + 7
    n = 20 + 6 * (i % 2)
    for k in range(9, n):
        x, z = cellule(k, a + 8 * math.sin(k / 5 + i))
        y = SOL + 36 + round(9 * (k / n) ** 0.7) + i % 3
        for u, v in ((0, 0), (1, 0), (0, 1)):
            if RR[x + u, z + v] < 40:
                put(x + u, y, z + v, bois_m)
    px, pz = point(n - 2, a)
    feuillage(px, SOL + 48 + i % 3, pz, 10, 5.5, 10, ["oak", "azalea", "flowering_azalea", "jungle"], 60 + i, lum=0.02)
feuillage(CEN, SOL + 60, CEN, 17, 7, 17, ["oak", "azalea", "flowering_azalea"], 80, lum=0.02)
for x, z in disque(CEN, CEN, 7.5):                    # air au-dessus de la terrasse
    for y in range(SOL + WT_H + 1, SOL + WT_H + 5):
        if nom((x, y, z)) is not None:
            put(x, y, z, AIR)
for x, z in disque(CEN, CEN, 6.8):
    if dist(x, z) > 6:
        put(x, SOL + WT_H + 1, z, FENCE("oak"))
put(C, SOL + WT_H + 1, C, LANT)
CIBLES_ACCES["sommet de l'arbre-monde"] = [C - 5, SOL + WT_H + 1, C - 2]
CIBLES_ACCES["balcon de l'arbre-monde"] = [C + 13, Y_B + 1, C]
racines(CEN - .5, CEN - .5, 12, 12, 12, bois_m, g=52)
reserver(C, C, 26, "centre")

# anneau de ruines sur l'ile
for a in range(10, 360, 40):
    if min(adiff(a, g) for g in (0, 90, 180, 270)) < 12:
        continue
    x, z = cellule(21.5, a)
    h = Hs(x, z)
    hh = rng.randint(4, 10)
    for cx_, cz_ in disque(x + .5, z + .5, 1.5):
        for y in range(h + 1, h + 1 + hh):
            put(cx_, y, cz_, CHB if y == h + 1 else choix(T_RUINE, h01(cx_, y, cz_, 53)))

tour_arbre(*TOURS["NE"]["c"], 8.5, 26, "mangrove", ["mangrove", "azalea"], 150, 100)
tour_arbre(*TOURS["NO"]["c"], 8.5, 30, "dark_oak", ["dark_oak", "flowering_azalea"], 40, 120)
CIBLES_ACCES["sommet de l'arbre creux NE"] = [TOURS["NE"]["c"][0] + 6, SOL + 27, TOURS["NE"]["c"][1]]
CIBLES_ACCES["sommet de l'arbre creux NO"] = [TOURS["NO"]["c"][0] + 6, SOL + 31, TOURS["NO"]["c"][1]]
tour_pierre(*TOURS["SE"]["c"], "rond", 5, 7, SOL + 26, 225, True)
tour_pierre(*TOURS["SO"]["c"], "carre", 5, 7, SOL + 30, 315, False)
for t in TOURS.values():
    reserver(*t["c"], 13, "tour")
arbre_geant(*JUNGLE, 7, 34, "jungle", ["jungle", "oak"], 15, 140)
arbre_geant(*EPICEA, 6, 40, "spruce", ["spruce"], 11, 150)
arbre_geant(*CERISIER, 3.5, 17, "cherry", ["cherry"], 13, 160)
for c, r_ in ((JUNGLE, 15), (EPICEA, 14), (CERISIER, 8)):
    reserver(*c, r_, "geant")
verifier("3. arbres geants, tours, centre")

# ====================================================================== 4. labyrinthes de haies
LABY = {}


def labyrinthe(k, q):
    c0, span = q["c"], q["span"]

    def dedans(x, z):
        return 73 <= RR[x, z] <= 105.5 and adiff(TH[x, z], c0) <= span and not eau[x, z]

    cases = set()
    for i in range(1, 41):
        for j in range(1, 41):
            x0, z0 = 6 * i, 6 * j
            if all(dedans(x, z) for x, z in ((x0 - 1, z0 - 1), (x0 + 7, z0 - 1), (x0 - 1, z0 + 7), (x0 + 7, z0 + 7))):
                cases.add((i, j))
    restant, meilleur = set(cases), set()
    while restant:
        s = restant.pop()
        comp, fi = {s}, deque([s])
        while fi:
            i, j = fi.popleft()
            for v in ((i + 1, j), (i - 1, j), (i, j + 1), (i, j - 1)):
                if v in restant:
                    restant.discard(v)
                    comp.add(v)
                    fi.append(v)
        if len(comp) > len(meilleur):
            meilleur = comp
    cases = meilleur
    passages, vus, pile_ = set(), {min(cases)}, [min(cases)]
    while pile_:
        i, j = pile_[-1]
        v = [c for c in ((i + 1, j), (i - 1, j), (i, j + 1), (i, j - 1)) if c in cases and c not in vus]
        if not v:
            pile_.pop()
            continue
        c = rng.choice(v)
        passages.add(frozenset(((i, j), c)))
        vus.add(c)
        pile_.append(c)
    for i, j in sorted(cases):
        for c in ((i + 1, j), (i, j + 1)):
            if c in cases and rng.random() < 0.1:
                passages.add(frozenset(((i, j), c)))
    murs = set()
    for i, j in cases:
        for a in range(-1, 8):
            for b in range(-1, 8):
                x, z = 6 * i + a, 6 * j + b
                occ[(x, z)] = "laby"
                if a in (-1, 0, 1, 5, 6, 7) or b in (-1, 0, 1, 5, 6, 7):
                    murs.add((x, z))
        for a in range(-4, 11):
            for b in range(-4, 11):
                occ.setdefault((6 * i + a, 6 * j + b), "abords")

    def trou(c, d):
        i, j = c
        for t_ in (-1, 0, 1):
            for n_ in (2, 3, 4):
                if d == (1, 0):
                    murs.discard((6 * i + 6 + t_, 6 * j + n_))
                elif d == (-1, 0):
                    murs.discard((6 * i + t_, 6 * j + n_))
                elif d == (0, 1):
                    murs.discard((6 * i + n_, 6 * j + 6 + t_))
                else:
                    murs.discard((6 * i + n_, 6 * j + t_))

    for p in passages:
        a, b = sorted(p)
        trou(a, (b[0] - a[0], b[1] - a[1]))
    cand_in, cand_out = [], []
    for c in sorted(cases):
        for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (c[0] + d[0], c[1] + d[1]) in cases:
                continue
            r0 = dist(6 * c[0] + 3, 6 * c[1] + 3)
            r1 = dist(6 * c[0] + 3 + 6 * d[0], 6 * c[1] + 3 + 6 * d[1])
            (cand_in if r1 < r0 else cand_out).append((ang(6 * c[0] + 3, 6 * c[1] + 3), c, d))
    entrees = set()
    for liste_ in (cand_in, cand_out):
        liste_.sort()
        for n_ in range(2):
            _, c, d = liste_[(2 * n_ + 1) * len(liste_) // 4]
            trou(c, d)
            entrees.add(c)
    fl = q["feuilles"]
    for x, z in murs:
        coeur = x % 6 == 0 or z % 6 == 0
        for y in range(SOL + 1, SOL + 6):
            put(x, y, z, q["coeur"] if coeur else F(fl[int(h01(x, y, z, 70) * 5) % len(fl)]))
        put(x, SOL + 6, z, F(fl[int(h01(x, 6, z, 71) * 5) % len(fl)]))
    imp = [c for c in sorted(cases) if sum(c in p for p in passages) == 1 and c not in entrees]
    for i, j in imp:                                   # fond d'impasse : colonne moussue et lanterne
        x, z = 6 * i + 3, 6 * j + 3
        if h01(i, 0, j, 72) < 0.5:
            put(x, SOL + 1, z, MSB)
            put(x, SOL + 2, z, LANT)
    mid = min((c for c in sorted(cases) if c not in imp), key=lambda c: abs(ang(6 * c[0] + 3, 6 * c[1] + 3) - c0) + abs(dist(6 * c[0] + 3, 6 * c[1] + 3) - 89))
    CIBLES_ACCES[f"labyrinthe {k}"] = [6 * mid[0] + 3, SOL + 1, 6 * mid[1] + 3]
    LABY[k] = dict(cases=cases, imp=imp, passages=passages, entrees=entrees)


for k, q in LABYS.items():
    labyrinthe(k, q)
print("   labyrinthes :", {k: (len(v["cases"]), len(v["imp"])) for k, v in LABY.items()})
verifier("4. labyrinthes")


# ====================================================================== 5. parcours et ponts
def pont(p0, p1, y, demi, table, rail, poteau):
    (ax, az), (bx, bz) = p0, p1
    L = math.hypot(bx - ax, bz - az)
    ux, uz = (bx - ax) / L, (bz - az) / L
    for x in range(math.floor(min(ax, bx) - demi - 2), math.ceil(max(ax, bx) + demi) + 3):
        for z in range(math.floor(min(az, bz) - demi - 2), math.ceil(max(az, bz) + demi) + 3):
            px, pz = x + .5 - ax, z + .5 - az
            t_ = px * ux + pz * uz
            s = abs(-px * uz + pz * ux)
            if t_ < -0.5 or t_ > L + 0.5:
                continue
            if s <= demi:
                put(x, y, z, choix(table, h01(x, y, z, 75)))
                for k2 in (1, 2, 3):
                    if nom((x, y + k2, z)) not in (None, "air"):
                        put(x, y + k2, z, AIR)
            elif s <= demi + 1:
                if nom((x, y, z)) in (None, "air"):
                    put(x, y, z, poteau)
                    if rail and 1 < t_ < L - 1:
                        put(x, y + 1, z, rail)


PONTS = {0: ([(70, PL("jungle")), (30, LOG("jungle", "x"))], FENCE("jungle"), LOG("jungle")),
         90: ([(60, PL("spruce")), (40, PL("oak"))], FENCE("spruce"), LOG("spruce")),
         180: ([(45, MSB), (30, CSB), (25, MCB)], S("mossy_stone_brick_wall"), MSB),
         270: ([(55, MROOT), (45, LOG("mangrove", "x"))], FENCE("mangrove"), LOG("mangrove"))}
for g, (tb, rail, pot) in PONTS.items():
    pont(point(36, g), point(23.5, g), SOL, 3.1, tb, rail, pot)
    for r_ in (24, 35):
        for s in (-5, 4):
            x, z = gl(r_, s, g)
            for y in range(SOL + 1, SOL + 5):
                put(x, y, z, pot)
            put(x, SOL + 5, z, LANT)


def sur_ligne(ligne, r_):
    return min(ligne, key=lambda p: abs(math.hypot(p[0] - CEN, p[1] - CEN) - r_))


for ligne in (RIV_SO, RIV_NE):                         # ponts des chemins de ronde sur la riviere
    for r_ in (69, 109.5):
        px, pz = sur_ligne(ligne, r_)
        a0 = math.degrees(math.atan2(pz - CEN, px - CEN)) % 360
        dl = math.degrees(11 / r_)
        pont(point(r_, a0 - dl), point(r_, a0 + dl), SOL, 2.1, [(60, PL("oak")), (40, PL("spruce"))], FENCE("oak"), LOG("oak"))

# nenuphars sur la riviere (Sud-Ouest) et sur les douves
px, pz = sur_ligne(RIV_SO, 90)
a0 = math.degrees(math.atan2(pz - CEN, px - CEN)) % 360
for k in range(-9, 10, 2):
    x, z = cellule(90 + 0.6 * math.sin(k), a0 + math.degrees(k / 90))
    if blocs.get((x, W, z)) == EAU and vide((x, SOL, z)):
        put(x, SOL, z, LILY)
for a in range(0, 360, 3):
    x, z = cellule(30 + 2.6 * math.sin(a), a)
    if blocs.get((x, W, z)) == EAU and vide((x, SOL, z)) and h01(a, 0, 0, 76) < 0.5:
        put(x, SOL, z, LILY)
for r_, a_, rad in ETANGS:
    for x, z in disque(*point(r_, a_), rad):
        if blocs.get((x, W, z)) == EAU and vide((x, SOL, z)) and h01(x, 0, z, 77) < 0.16:
            put(x, SOL, z, LILY)


def champi(cx, cz, ycap, rc, rouge=True):
    y0 = W - 3
    b = cx, cz
    fond = Hs(cx, cz)
    if eau[cx, cz]:
        fond = W - 4
    for y in range(fond + 1, ycap):
        put(cx, y, cz, STEM)
    m = RED if rouge else BROWN
    for x, z in disque(cx + .5, cz + .5, rc):
        put(x, ycap, z, m)
    if rouge:
        for x, z in disque(cx + .5, cz + .5, rc + 1.2):
            if rc < math.hypot(x - cx, z - cz) and nom((x, ycap - 1, z)) in (None, "air"):
                put(x, ycap - 1, z, m)


# champignons geants de la rive nord-est jusqu'a la cabane (salle 7), au-dessus de la riviere
P0 = point(50, 318)
P1 = point(73, 353)
NCH = 11
LCH = math.hypot(P1[0] - P0[0], P1[1] - P0[1])
Y_CAB = SOL + NCH + 1
CHAMPIS = []
for i in range(NCH):
    t_ = (i + 0.3) / (NCH + 0.7)
    cx = math.floor(P0[0] + (P1[0] - P0[0]) * t_ + 0.8 * math.sin(i * 1.7))
    cz = math.floor(P0[1] + (P1[1] - P0[1]) * t_ + 0.8 * math.cos(i * 1.3))
    CHAMPIS.append((cx, cz))
    champi(cx, cz, SOL + 2 + i, 2.2, rouge=i % 2 == 0)
    reserver(cx, cz, 5, "champi")

# grandes feuilles (dripleaf) dans le bassin de la cascade, et branche au-dessus
for k, x in enumerate(range(BX0 + 2, BX1 - 1, 3)):
    z = BZ0 + 5 + (k % 2)
    if blocs.get((x, W, z)) == EAU:
        fy = [y for y in range(W, 0, -1) if nom((x, y, z)) != "water"][0]
        put(x, fy, z, CLAY)
        for y in range(fy + 1, W + 1):
            put(x, y, z, S("big_dripleaf_stem", facing="north", waterlogged=True))
        put(x, SOL, z, S("big_dripleaf", facing="north", tilt="none", waterlogged=False))

# branche jetee au-dessus d'une douve, et tronc couche sur la riviere NE
pont(point(38, 200), point(23.5, 200), SOL + 1, 0.7, [(100, LOG("dark_oak", "x"))], None, LOG("dark_oak"))
pont(point(38, 20), point(23.5, 20), SOL + 1, 0.7, [(100, LOG("oak", "x"))], None, LOG("oak"))
verifier("5. parcours, ponts")


# ====================================================================== 6. salles cachees
def salle(num, x0, x1, z0, z1, yf, h, coffre, acces, style=T_SALLE, garder=()):
    garder = set(garder)
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            for y in range(yf, yf + h + 2):
                dedans = x0 <= x <= x1 and z0 <= z <= z1
                if dedans and yf < y <= yf + h:
                    put(x, y, z, AIR)
                elif dedans and y == yf:
                    put(x, y, z, MOSS if h01(x, 0, z, 90) < 0.4 else MCB if (x + z) % 2 else MSB)
                else:
                    put(x, y, z, choix(style, h01(x, y, z, 91 + num)))
    cx, cz = coffre
    put(cx, yf, cz, CHB)
    libres = garder | {coffre}
    for x, z in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
        if (x, z) not in libres and math.hypot(x - cx, z - cz) > 1.5:
            put(x, yf + 1, z, LANT)
    mx, mz = (x0 + x1) // 2, (z0 + z1) // 2
    put(mx, yf + h + 1, mz, SHROOM)
    if (mx, mz) not in libres and h >= 4:
        put(mx, yf + h, mz, CHAINE)
        put(mx, yf + h - 1, mz, LANT_P)
    for x in range(x0, x1 + 1):                        # racines au plafond, tapis de mousse, lichen
        for z in range(z0, z1 + 1):
            u = h01(x, 1, z, 92)
            if (x, z) in libres or math.hypot(x - cx, z - cz) <= 1.2:
                continue
            if u < 0.18 and vide((x, yf + h, z)):
                put(x, yf + h, z, S("hanging_roots", waterlogged=False))
            elif u > 0.72 and vide((x, yf + 1, z)):
                put(x, yf + 1, z, TAPIS)
    for z in range(z0, z1 + 1, 2):
        lichen(x0, yf + 3, z, "west") if (x0, z) not in libres else None
    SALLES.append((num, (cx, yf + 1, cz), acces))


# 1 : derriere la cascade de la colline SE
cascade(CASC[0], CASC[1], (0, -1), Y_CH, 1)
yf = W
salle(1, HCX - 3, HCX + 3, CASC[1] - 8, CASC[1] - 2, yf, 4, (HCX, CASC[1] - 8), "Derrière la cascade de la colline sud-est : on traverse le rideau d'eau depuis le bassin",
      garder=[(HCX, CASC[1] - 2), (HCX - 1, CASC[1] - 2), (HCX + 1, CASC[1] - 2)])
for s in (-1, 0, 1):
    x = HCX + s
    put(x, W, CASC[1] - 1, MSB)
    for y in (W + 1, W + 2, W + 3):
        put(x, y, CASC[1] - 1, AIR)
    put(x, W + 4, CASC[1] - 1, MSB)
    put(x, W, CASC[1] - 2, MSB)
for s in (-2, 2):
    for y in range(W, W + 5):
        put(HCX + s, y, CASC[1] - 1, MSB)
        put(HCX + s, y, CASC[1], MSB)

# 2 : dans le tronc du geant de la jungle
cx, cz = JUNGLE
a2 = 250
salle(2, cx - 2, cx + 2, cz - 2, cz + 2, SOL, 4, (cx + 2, cz + 2), "À l'intérieur du tronc géant de la jungle : fente entre deux racines, face nord",
      style=[(70, WOOD("jungle")), (30, MOSS)])
percer(cx, cz, a2, 2.2, 15, SOL + 1, 2, w=0, sol=PL("jungle"))

# 3 : sous les racines de l'epicea geant
cx, cz = EPICEA
yf = SOL - 9
HX = cx + 10
salle(3, HX - 3, HX + 3, cz - 3, cz + 3, yf, 4, (HX - 3, cz), "Sous les racines de l'épicéa géant : puits à échelle sous l'arche de racines, côté est",
      style=[(50, RDIRT), (30, MUDB), (20, MSB)], garder=[(HX, cz), (HX + 1, cz)])
echelle(HX, cz, yf + 1, Hs(HX, cz), (1, 0), mat=RDIRT)
for y in range(Hs(HX, cz) + 1, Hs(HX, cz) + 5):
    put(HX, y, cz, AIR)
for dz_ in (-2, 2):
    for dx_ in (-2, 2):
        for y in range(Hs(HX, cz) + 1, Hs(HX, cz) + 4):
            put(HX + dx_, y, cz + dz_, WOOD("spruce"))
for dx_ in range(-2, 3):
    for dz_ in range(-2, 3):
        put(HX + dx_, Hs(HX, cz) + 4, cz + dz_, MROOT)

# temple en ruine, 4 : derriere un rideau de lianes
tx, tz = TEMPLE
for x in range(tx - 12, tx + 13):
    for z in range(tz - 10, tz + 7):
        put(x, SOL, z, MCB if h01(x, 2, z, 95) < 0.5 else MSB)
        bord = abs(x - tx) == 12 or z in (tz - 10, tz + 6)
        if bord:
            h = 3 + int(6 * bruit(x, z, 4, 96)) if z != tz + 6 else 7
            if z == tz - 10 and abs(x - tx) <= 2:
                continue
            for y in range(SOL + 1, SOL + 1 + h):
                put(x, y, z, choix(T_RUINE, h01(x, y, z, 97)))
for i in (-8, -3, 3, 8):
    for j in (-6, 2):
        h = 4 + int(8 * h01(i, 0, j, 98))
        for y in range(SOL + 1, SOL + 1 + h):
            put(tx + i, y, tz + j, S("mossy_stone_brick_wall") if False else MSB)
        vigne(tx + i + 1, SOL + h, tz + j, (-1, 0), h - 1)
salle(4, tx - 3, tx + 3, tz + 7, tz + 12, SOL, 4, (tx + 3, tz + 12), "Temple en ruine : le mur du fond a une ouverture cachée derrière le rideau de lianes",
      garder=[(tx, tz + 7)])
for y in (SOL + 1, SOL + 2):
    put(tx, y, tz + 6, AIR)
for x in (tx - 1, tx, tx + 1):
    for y in range(SOL + 6, SOL, -1):
        put(x, y, tz + 5, S("vine", south=True))

# 5 : sous une trappe couverte de mousse, en lisiere sud-est
tx, tz = cellule(115, 62)
reserver(tx, tz, 3, "salle")
for u, v in disque(tx + .5, tz + .5, 2.3):
    if Hs(u, v) != SOL:
        Hm[u, v] = SOL
    put(u, SOL, v, MOSS)
    for y in range(SOL + 1, SOL + 7):
        if nom((u, y, v)) is not None:
            put(u, y, v, AIR)
    put(u, SOL + 1, v, TAPIS)
salle(5, tx - 3, tx + 3, tz - 3, tz + 3, SOL - 8, 4, (tx - 3, tz), "Lisière sud-est : trappe sous le tapis de mousse (casser le tapis, puis ouvrir la trappe), échelle",
      garder=[(tx, tz), (tx + 1, tz)])
echelle(tx, tz, SOL - 7, SOL - 1, (1, 0))
put(tx, SOL, tz, TRAPPE)

# 6 : colline creuse du nord-ouest
cx, cz = cellule(54, 200)
salle(6, cx - 3, cx + 3, cz - 3, cz + 3, SOL, 5, (cx - 3, cz), "Colline creuse à l'ouest : tunnel d'un bloc de large côté sud, caché par les racines",
      style=[(45, RDIRT), (30, MUDB), (25, MCB)])
z = cz + 4
while Hs(cx, z) > SOL + 2 and z < cz + 20:
    z += 1
for zz in range(cz + 4, z + 2):
    put(cx, SOL, zz, PMUD)
    put(cx, SOL + 1, zz, AIR)
    put(cx, SOL + 2, zz, AIR)
    if zz <= z - 1:
        put(cx, SOL + 3, zz, MUDB)
        put(cx - 1, SOL + 1, zz, MUDB)
        put(cx + 1, SOL + 1, zz, MUDB)
        put(cx - 1, SOL + 2, zz, MUDB)
        put(cx + 1, SOL + 2, zz, MUDB)

# 7 : cabane au bout du parcours de champignons
cx, cz = math.floor(P1[0]), math.floor(P1[1])
px, pz = CHAMPIS[-1]
for x, z in disque(cx + .5, cz + .5, 6.5):
    put(x, Y_CAB, z, RED)
for x, z in disque(cx + .5, cz + .5, 2):
    for y in range(Hs(x, z) + 1, Y_CAB):
        put(x, y, z, STEM)
reserver(cx, cz, 9, "cabane")
salle(7, cx - 2, cx + 2, cz - 2, cz + 2, Y_CAB, 4, (cx + 2, cz), "Au bout du parcours de champignons géants : cabane perchée, ouverte vers le dernier chapeau",
      style=[(60, PL("spruce")), (40, LOG("spruce"))])
for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
    pass
sens = (-1, 0) if abs(px - cx) >= abs(pz - cz) and px < cx else (1, 0) if abs(px - cx) >= abs(pz - cz) else (0, -1) if pz < cz else (0, 1)
dx, dz = sens
for s in (-1, 0, 1):
    for y in (Y_CAB + 1, Y_CAB + 2, Y_CAB + 3):
        put(cx + dx * 3 + dz * s, y, cz + dz * 3 + dx * s, AIR)
    put(cx + dx * 3 + dz * s, Y_CAB, cz + dz * 3 + dx * s, PL("spruce"))
# 8 : dans la grotte
yf = CAVE_FOND[(CVX, CVZ)][0] + 0
rz0 = CVZ - 27
salle(8, CVX - 3, CVX + 3, rz0, rz0 + 5, yf, 4, (CVX, rz0), "Dans la grotte luxuriante : couloir au nord, derrière la paroi de lichen",
      style=[(50, MOSS), (30, MCB), (20, MSB)], garder=[(CVX, rz0 + 5)])
zz = rz0 + 6
while zz < CVZ:
    cf = CAVE_FOND.get((CVX, zz))
    put(CVX, yf, zz, MOSS)
    for y in (yf + 1, yf + 2, yf + 3):
        put(CVX, y, zz, AIR)
    if cf is None:
        put(CVX, yf + 4, zz, MCB)
        put(CVX - 1, yf + 1, zz, MCB)
        put(CVX + 1, yf + 1, zz, MCB)
        put(CVX - 1, yf + 2, zz, MCB)
        put(CVX + 1, yf + 2, zz, MCB)
        put(CVX - 1, yf + 3, zz, MCB)
        put(CVX + 1, yf + 3, zz, MCB)
    elif cf[0] <= yf + 1 and zz > CVZ - 12:
        break
    zz += 1
for p, s in list(blocs.items()):                       # lichen dont le mur a ete perce par une porte
    if s[0].endswith(":glow_lichen"):
        for k, v in s[1]:
            if v == "true" and k in DIR and not solide((p[0] + DIR[k][0], p[1], p[2] + DIR[k][1])):
                put(*p, AIR)
verifier("6. salles cachees")


# ====================================================================== 7. vegetation, decoration, eclairage
def arbre(x, z, h, rx, bois, feuilles, g):
    y0 = Hs(x, z)
    if sous((x, y0 + 1, z)) not in SOL_PLANTE and sous((x, y0 + 1, z)) not in ("gravel", "packed_mud"):
        return False
    for y in range(y0 + 1, y0 + h + 1):
        put(x, y, z, LOG(bois))
    for k, (dx, dz) in enumerate(((1, 0), (-1, 0), (0, 1), (0, -1))):
        if h >= 9 and h01(x, k, z, g) < 0.6:
            for i in range(1, 4):
                put(x + dx * i, y0 + h - 3 + i // 2, z + dz * i, LOG(bois))
    feuillage(x + .5, y0 + h + 1, z + .5, rx, rx * 0.6, rx, feuilles, g)
    return True


def colonne(cx, cz, h, brisee):
    y0 = Hs(cx, cz)
    for x, z in disque(cx + .5, cz + .5, 1.6):
        for y in range(y0, y0 + h + 1):
            hh = h if not brisee else h - int(4 * h01(x, 0, z, 110))
            if y <= y0 + hh:
                put(x, y, z, CHB if y == y0 + 1 else choix(T_RUINE, h01(x, y, z, 111)))
    for dx, dz in ((2, 0), (-2, 0), (0, 2), (0, -2)):
        if vide((cx + dx, y0 + h, cz + dz)) and h01(cx, dx, cz + dz, 112) < 0.8:
            vigne(cx + dx, y0 + h - 1, cz + dz, (-dx // 2, -dz // 2), h - 1)


def colonne_tombee(cx, cz, a, L):
    ux, uz = math.cos(a), math.sin(a)
    for i in range(L):
        if i % 5 == 4:
            continue
        x, z = math.floor(cx + i * ux), math.floor(cz + i * uz)
        y = Hs(x, z)
        for u, v in ((0, 0), (1, 0), (0, 1), (1, 1)):
            put(x + u, y + 1, z + v, choix(T_RUINE, h01(x, u, z, 113)))
            if h01(x, v, z, 114) < 0.5:
                put(x + u, y + 2, z + v, MSB)


raisons = {}


def compte(k):
    raisons[k] = raisons.get(k, 0) + 1


arbres = 0
TYPES = [("oak", ["oak"], 6, 9), ("dark_oak", ["dark_oak"], 7, 10), ("spruce", ["spruce"], 9, 14), ("jungle", ["jungle"], 10, 15),
         ("oak", ["azalea", "flowering_azalea"], 5, 7), ("cherry", ["cherry"], 6, 8), ("mangrove", ["mangrove"], 6, 9)]
for i in range(9000):
    r_ = rng.uniform(37, 118.5)
    x, z = cellule(r_, rng.uniform(0, 360))
    if arbres >= 135:
        break
    if not libre(x, z, 2) or Hs(x, z) < SOL:
        compte("occ" if (x, z) in occ else "eau" if eau[x, z] else "sentier" if sentier[x, z] else "laby" if laby_zone[x, z] else "marge")
        continue
    b, fl, h0, h1 = TYPES[rng.randrange(len(TYPES))]
    if b == "mangrove" and not any(0 <= x + a < SX and eau[x + a, z] for a in (-8, 8)):
        b, fl = "oak", ["oak"]
    if arbre(x, z, rng.randint(h0, h1), rng.uniform(3.6, 6), b, fl, 200 + i % 90):
        reserver(x, z, 4.2, "arbre")
        arbres += 1
    else:
        compte("sol " + str(nom((x, Hs(x, z), z))))
print("   arbres refuses :", raisons)

n_col = 0
for i in range(5000):
    x, z = cellule(rng.uniform(36, 118), rng.uniform(0, 360))
    if not libre(x, z, 2) or Hs(x, z) < SOL:
        continue
    if rng.random() < 0.72:
        colonne(x, z, rng.randint(5, 13), rng.random() < 0.5)
    else:
        a = rng.uniform(0, 6.28)
        fin = (math.floor(x + 9 * math.cos(a)), math.floor(z + 9 * math.sin(a)))
        if not libre(*fin, 2):
            continue
        colonne_tombee(x, z, a, 10)
    reserver(x, z, 4, "colonne")
    n_col += 1
    if n_col >= 64:
        break

for i in range(50):                                    # champignons geants isoles
    x, z = cellule(rng.uniform(38, 118), rng.uniform(0, 360))
    if libre(x, z, 3) and Hs(x, z) >= SOL:
        champi(x, z, Hs(x, z) + rng.randint(5, 8), rng.uniform(2.2, 3.4), rouge=i % 2 == 0)
        reserver(x, z, 5, "champi")

# sous-bois : buissons, rochers moussus, souches, troncs couches
n_bois = 0
for i in range(9000):
    x, z = cellule(rng.uniform(35, 119), rng.uniform(0, 360))
    if not libre(x, z, 1) or Hs(x, z) < SOL:
        continue
    u = rng.random()
    y0 = Hs(x, z)
    if u < 0.55:                                       # buisson
        fl = rng.choice((["azalea", "flowering_azalea"], ["oak"], ["jungle"], ["dark_oak"], ["azalea"]))
        r_ = rng.uniform(1.4, 2.7)
        for cx_, cz_ in disque(x + .5, z + .5, r_):
            if libre(cx_, cz_) :
                h = Hs(cx_, cz_)
                for y in range(h + 1, h + 1 + max(1, round((r_ - math.hypot(cx_ - x, cz_ - z)) * 1.2))):
                    put(cx_, y, cz_, F(fl[int(h01(cx_, y, cz_, 140) * 3) % len(fl)]))
    elif u < 0.8:                                      # rocher
        r_ = rng.uniform(1.3, 2.4)
        for cx_, cz_ in disque(x + .5, z + .5, r_):
            if libre(cx_, cz_):
                h = Hs(cx_, cz_)
                for y in range(h + 1, h + 1 + max(1, round((r_ - math.hypot(cx_ - x, cz_ - z)) * 1.4))):
                    put(cx_, y, cz_, MOSS if y == h + 1 + round((r_ - math.hypot(cx_ - x, cz_ - z)) * 1.4) - 1 else MCB)
    elif u < 0.9:                                      # souche
        b = rng.choice(("oak", "spruce", "dark_oak"))
        for y in range(y0 + 1, y0 + 3):
            put(x, y, z, LOG(b))
        put(x, y0 + 3, z, TAPIS)
    else:                                              # tronc couche
        b = rng.choice(("oak", "spruce", "jungle"))
        ax_ = rng.choice(("x", "z"))
        for k in range(rng.randint(4, 8)):
            cx_, cz_ = (x + k, z) if ax_ == "x" else (x, z + k)
            if libre(cx_, cz_):
                put(cx_, Hs(cx_, cz_) + 1, cz_, LOG(b, ax_))
                if h01(cx_, 1, cz_, 141) < 0.5:
                    put(cx_, Hs(cx_, cz_) + 2, cz_, TAPIS)
    reserver(x, z, 3, "sous-bois")
    n_bois += 1
    if n_bois >= 420:
        break
print("   sous-bois :", n_bois)

# petits arbres sur le rempart
for a in range(12, 360, 45):
    if min(adiff(a, g) for g in (0, 90, 180, 270)) < 12 or adiff(a, 167) < 8 or adiff(a, 347) < 8:
        continue
    x, z = cellule(123.3, a)
    ys = [y for y in range(SY - 1, SOL, -1) if solide((x, y, z)) and nom((x, y, z)) != "barrier"]
    if ys:
        top = ys[0]
        for y in range(top + 1, top + 5):
            if nom((x, y, z)) in (None, "air", "moss_block"):
                put(x, y, z, LOG("oak"))
        for u, v in disque(x + .5, z + .5, 3.2):
            for y in range(top + 3, top + 7):
                if nom((u, y, v)) is None and RR[u, v] < 124.5 and math.hypot(u - x, v - z) + abs(y - top - 5) < 4.4:
                    put(u, y, v, F("azalea"))

# cascades du rempart : la riviere nait d'une encoche du mur
sx0, sz0 = RIV_SO[0]
xc = math.floor(sx0)
zc = math.floor(sz0)
while RR[xc, zc] >= R_MUR - 0.4:                       # premiere case devant le mur, cote interieur
    xc += 1
for s in range(-3, 4):
    for x in range(xc - 3, xc):
        for y in range(SOL, SOL + 17):
            if RR[x, zc + s] < R - 0.1 and nom((x, y, zc + s)) in (None, "air", "moss_block"):
                put(x, y, zc + s, MSB)
for z in range(zc - 2, zc + 3):
    fy = W - 2
    for y in range(fy, W + 1):
        put(xc, y, z, EAU if y > fy else CLAY)
    if vide((xc, SOL, z)) is False:
        put(xc, SOL, z, AIR)
cascade(xc, zc, (-1, 0), SOL + 12, 1)

# la grotte : plafond de baies, spores, feuilles, azalees
for (x, z), (fond, haut) in CAVE_FOND.items():
    u = h01(x, 4, z, 120)
    if not vide((x, haut, z)) or not vide((x, fond + 1, z)):
        continue
    if u < 0.14:
        n = 2 + int(5 * h01(x, 5, z, 121))
        for k in range(n):
            y = haut - k
            if y <= fond + 3 or not vide((x, y, z)):
                break
            fin = k == n - 1 or y - 1 <= fond + 3
            put(x, y, z, S("cave_vines", age=25, berries=True) if fin else S("cave_vines_plant", berries=h01(x, y, z, 122) < 0.6))
    elif u < 0.17:
        put(x, haut, z, S("spore_blossom"))
    elif u < 0.24:
        put(x, haut, z, S("hanging_roots", waterlogged=False))
    v = h01(x, 6, z, 123)
    b = nom((x, fond, z))
    if v < 0.06 and b in ("moss_block", "clay"):
        put(x, fond + 1, z, S("azalea") if v < 0.03 else S("flowering_azalea"))
    elif v < 0.13 and b in ("moss_block", "clay") and vide((x, fond + 2, z)):
        put(x, fond + 1, z, S("small_dripleaf", half="lower", facing="north", waterlogged=False))
        put(x, fond + 2, z, S("small_dripleaf", half="upper", facing="north", waterlogged=False))
    elif v < 0.19 and b == "moss_block" and vide((x, fond + 2, z)) and vide((x, fond + 3, z)):
        put(x, fond + 1, z, S("big_dripleaf_stem", facing="east", waterlogged=False))
        put(x, fond + 2, z, S("big_dripleaf", facing="east", tilt="none", waterlogged=False))
    elif v < 0.42 and b == "moss_block":
        put(x, fond + 1, z, TAPIS)
    elif v < 0.5 and b == "moss_block":
        put(x, fond + 1, z, S("short_grass"))
for (x, z), (fond, haut) in CAVE_FOND.items():         # lichen lumineux sur les parois
    for y in (fond + 2, fond + 3):
        for a, b, face in ((1, 0, "east"), (-1, 0, "west"), (0, 1, "south"), (0, -1, "north")):
            if solide((x + a, y, z + b)) and h01(x, y, z, 124) < 0.3 and nom((x + a, y, z + b)) not in ("light",):
                lichen(x, y, z, face)
                break

# sol : herbes, fougeres, fleurs, tapis, buissons
FLEURS = ["dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "oxeye_daisy", "cornflower", "lily_of_the_valley"]
DOUBLES = ["tall_grass", "large_fern", "rose_bush", "peony", "lilac"]
for x in range(SX):
    for z in range(SZ):
        if RR[x, z] >= 121 or eau[x, z]:
            continue
        y = Hs(x, z)
        if nom((x, y, z)) not in SOL_PLANTE or not vide((x, y + 1, z)):
            continue
        u = h01(x, 8, z, 130)
        b = nom((x, y, z))
        chem = sentier[x, z]
        if chem:
            if u < 0.06:
                put(x, y + 1, z, TAPIS)
            continue
        if b == "podzol" and u < 0.05:
            put(x, y + 1, z, S("red_mushroom" if u < 0.025 else "brown_mushroom"))
        elif u < 0.16:
            put(x, y + 1, z, S("fern" if bruit(x, z, 12, 131) > 0.5 else "short_grass"))
        elif u < 0.30:
            put(x, y + 1, z, S("short_grass"))
        elif u < 0.35:
            put(x, y + 1, z, S(FLEURS[int(bruit(x, z, 8, 132) * 16) % len(FLEURS)]))
        elif u < 0.385 and vide((x, y + 2, z)):
            n = DOUBLES[int(h01(x, 9, z, 133) * 5)]
            put(x, y + 1, z, S(n, half="lower"))
            put(x, y + 2, z, S(n, half="upper"))
        elif u < 0.47 and b in ("moss_block", "rooted_dirt", "grass_block"):
            put(x, y + 1, z, TAPIS)
        elif u < 0.475:
            put(x, y + 1, z, S("azalea"))
        elif u < 0.48:
            put(x, y + 1, z, S("flowering_azalea"))
        elif u < 0.53 and b == "grass_block":
            put(x, y + 1, z, S("pink_petals", facing="north", flower_amount=3))

for i in range(10):                                    # apparitions, sur le chemin exterieur
    a = 9 + 36 * i
    for dr in (0, -1, 1, -2, 2):
        x, z = cellule(109.5 + dr, a)
        if not eau[x, z] and nom((x, Hs(x, z) + 1, z)) in (None, "air", "moss_carpet"):
            break
    y = Hs(x, z)
    put(x, y, z, CHB)
    put(x, y + 1, z, AIR)
    put(x, y + 2, z, AIR)
    APPARITIONS.append((x, y + 1, z))
for k, v in CIBLES_ACCES.items():                      # la decoration ne doit pas boucher les points de controle
    if k.startswith("labyrinthe"):
        put(v[0], v[1], v[2], AIR)
        put(v[0], v[1] + 1, v[2], AIR)
verifier("7a. vegetation, decoration")

# eclairage calcule
SOURCES = {"sea_lantern": 15, "lantern": 15, "shroomlight": 15, "light": 15, "glow_lichen": 7, "cave_vines": 14, "cave_vines_plant": 14}
TRANSP = NON_SOLIDES | {"barrier", "mangrove_roots", "water", "shroomlight"}
SANS_SPAWN = {"barrier", "mangrove_roots", "water", "oak_slab", "spruce_slab", "dark_oak_slab", "mangrove_slab", "mossy_stone_brick_stairs",
              "oak_fence", "spruce_fence", "jungle_fence", "mangrove_fence", "dark_oak_fence", "mossy_stone_brick_wall", "red_mushroom_block_x"}
coffres = {(p[0], p[2]) for _, p, _ in SALLES}
total_lum = 0


def calcul_lumiere():
    opaque = np.zeros((SX, SY, SZ), dtype=bool)
    opaque[:, :SOL + 1, :] = True
    L = np.zeros((SX, SY, SZ), dtype=np.int8)
    for (x, y, z), s in blocs.items():
        n = s[0].split(":")[1]
        opaque[x, y, z] = n not in TRANSP and not n.endswith("_leaves")
        if n in SOURCES:
            L[x, y, z] = SOURCES[n] if n != "cave_vines_plant" or dict(s[1]).get("berries") == "true" else 0
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


def traversable(p):
    n = nom(p)
    return (p[1] > SOL) if n is None else n in SANS_COLLISION and n != "water"


def surfaces_noires(L):
    noires = []
    for (x, y, z), s in blocs.items():
        n = s[0].split(":")[1]
        if n in NON_SOLIDES or n in SANS_SPAWN or n.endswith("_leaves") or y + 2 >= SY or RR[x, z] >= 124.5:
            continue
        if traversable((x, y + 1, z)) and traversable((x, y + 2, z)) and L[x, y + 1, z] == 0:
            noires.append((x, y, z))
    return sorted(noires)


for tour_n in range(40):
    Lm = calcul_lumiere()
    noires = surfaces_noires(Lm)
    if not noires:
        break
    pris = []
    for x, y, z in noires:
        if (x, z) in coffres or any(abs(x - a) < 10 and abs(z - b) < 10 and abs(y - c) < 6 for a, c, b in pris):
            continue
        pris.append((x, y, z))
        n = nom((x, y + 1, z))
        if n and n != "air" and dict(blocs[(x, y + 1, z)][1]).get("half") == "lower":
            put(x, y + 2, z, AIR)
        put(x, y + 1, z, LUMIERE)
        total_lum += 1
print(f"   eclairage : {total_lum} points ajoutes, {tour_n} passes, surfaces noires restantes = {len(noires)}", flush=True)
if noires:
    print("   exemple :", noires[:6])
    raise SystemExit("il reste des surfaces noires")
verifier("7b. eclairage")

# ====================================================================== cibles et ecriture
CIBLES_ACCES["grotte"] = [CVX, CAVE_FOND[(CVX, CVZ)][0] + 1, CVZ]
CIBLES_ACCES.update({f"salle {n}": list(p) for n, p, _ in SALLES})
CIBLES_ACCES.update({f"apparition {i + 1}": list(p) for i, p in enumerate(APPARITIONS)})
depart = gl(112, 0, 0)
CIBLES.write_text(json.dumps({"_marque": "[claude-build]", "sol": SOL, "depart": [depart[0], Hs(*depart) + 1, depart[1]],
                              "cibles": CIBLES_ACCES}, ensure_ascii=False, indent=1), encoding="utf-8")

palette, index, liste = [], {}, []
for (x, y, z) in sorted(blocs, key=lambda p: (p[1], p[2], p[0])):
    s = blocs[(x, y, z)]
    if s not in index:
        index[s] = len(palette)
        c = Compound({"Name": String(s[0])})
        if s[1]:
            c["Properties"] = Compound({k: String(v) for k, v in s[1]})
        palette.append(c)
    liste.append(Compound({"pos": List[Int]([Int(x), Int(y), Int(z)]), "state": Int(index[s])}))
racine = nbtlib.File({"DataVersion": Int(DATA_VERSION), "size": List[Int]([Int(SX), Int(SY), Int(SZ)]),
                      "palette": List[Compound](palette), "blocks": List[Compound](liste),
                      "entities": List[Compound]([])}, gzipped=True)
for chemin in SORTIES:
    chemin.parent.mkdir(parents=True, exist_ok=True)
    racine.save(chemin)

ox, oy, oz = ORIGINE
if MONDE_OK:
    lignes = [f"# [claude-build] Aplanit l'emprise de {NOM}. 2 /fill par couche (< 32768 blocs). Se placer pres du centre."]
    for y in range(oy, oy + SY):
        bloc = (f"minecraft:{ARGS.surface}" if y == oy + SOL else f"minecraft:{ARGS.sous_sol}" if y < oy + SOL else "minecraft:air")
        lignes.append(f"fill {ox} {y} {oz} {ox + SX // 2 - 1} {y} {oz + SZ - 1} {bloc}")
        lignes.append(f"fill {ox + SX // 2} {y} {oz} {ox + SX - 1} {y} {oz + SZ - 1} {bloc}")
    lignes.append(f'tellraw @s {{"text":"Arene {NOM} effacee","color":"green"}}')
    FONCTION.parent.mkdir(parents=True, exist_ok=True)
    FONCTION.write_text("\n".join(lignes) + "\n", encoding="utf-8")
    mc = DATAPACK / "pack.mcmeta"
    if not mc.exists():
        mc.write_text('{"pack": {"pack_format": 48, "description": "Atelier [claude-build]"}}\n', encoding="utf-8")

SALLES.sort()


def w(p):
    return " ".join(str(v) for v in monde(*p))


md = ["<!-- [claude-build] genere par sources/arene_nature.py -->", "# Salles cachées — arène Nature", "",
      f"Monde `saves/{ARGS.monde}`, arène centrée en X={CENTRE_MONDE[0]} Z={CENTRE_MONDE[1]}, sol à Y={ARGS.sol}. "
      "Coordonnées = case vide où poser le coffre.", "", "| N° | Coffre (X Y Z) | Accès |", "|---|---|---|"]
md += [f"| {n} | {w(p)} | {a} |" for n, p, a in SALLES]
md += ["", "## Apparitions (10, sur le chemin extérieur)", ""] + [f"- {i + 1} : {w(p)}" for i, p in enumerate(APPARITIONS)]
md += ["", "## Ce qui a été construit", "",
       f"- Enceinte Ø{SX} (centre entre 4 blocs), rempart de 4,5 blocs d'épaisseur et de 15 à 20 blocs de haut, brèches comblées par des racines, lianes, arbres sur le mur.",
       "- 4 portes monumentales : arche de pierre moussue, 2 troncs géants par porte, racines entrelacées.",
       f"- Centre : arbre-monde creux de {WT_H} blocs, escalier intérieur, balcon, terrasse au sommet, île entourée de douves, ruines, 4 ponts différents.",
       "- Rivière en deux bras reliés par les douves, née d'une cascade dans le rempart ; 2 cascades, bassin, 6 étangs, nénuphars.",
       "- 4 tours : arbres creux en palétuvier (NE) et en chêne noir (NO), tours de pierre ronde en ruine (SE) et carrée (SO).",
       "- 4 labyrinthes de haies à cœur solide (troncs ou pierre), 5 blocs de haut, couverts de feuilles.",
       f"- Géants de la jungle, de l'épicéa et du cerisier, {arbres} arbres, {n_col} colonnes debout, brisées ou tombées, champignons géants, collines, ravine, temple en ruine.",
       "- Grotte luxuriante : baies lumineuses, spores, azalées, feuilles tombantes, lichen, entrée par un escalier.",
       "", "## Écarts par rapport au prompt", "",
       "- Construction par structure (.nbt + /place) au lieu de /fill en jeu : les 7 phases sont dans le script, chacune vérifiée.",
       "- Portes : le fond de chaque passage est fermé par des racines, pour que personne ne sorte. Une paroi invisible (blocs barrière) au-dessus du rempart empêche aussi de sortir en grimpant aux lianes.",
       "- Salle 5 : le tapis de mousse couvre la trappe, il faut donc le casser pour l'ouvrir ; en mode aventure, cette salle est fermée.",
       "- Les chutes d'eau utilisent de l'eau qui coule, confinée par des encoches ; la rivière elle-même est en eau source immobile.",
       f"- Éclairage : lanternes, shroomlight et baies, complétés par {total_lum} blocs de lumière invisibles calculés pour qu'aucune surface ne reste noire.",
       "- `/gamerule doFireTick false` et `/forceload` sont à taper en jeu (commandes fournies).",
       ]
LISTE_SALLES.write_text("\n".join(md) + "\n", encoding="utf-8")

from PIL import Image, ImageDraw
COUL = {"grass_block": (96, 150, 62), "moss_block": (88, 125, 45), "podzol": (92, 66, 34), "coarse_dirt": (112, 82, 56), "rooted_dirt": (130, 100, 72),
        "mud": (62, 52, 54), "packed_mud": (140, 104, 78), "dirt": (120, 86, 60), "gravel": (130, 125, 122), "clay": (150, 155, 165),
        "water": (50, 100, 205), "mossy_stone_bricks": (108, 118, 92), "stone_bricks": (125, 125, 125), "cracked_stone_bricks": (115, 115, 115),
        "mossy_cobblestone": (100, 115, 85), "cobblestone": (120, 120, 120), "chiseled_stone_bricks": (130, 130, 130), "mud_bricks": (150, 110, 85),
        "oak_leaves": (70, 130, 45), "dark_oak_leaves": (45, 100, 35), "spruce_leaves": (45, 90, 60), "jungle_leaves": (55, 150, 40),
        "mangrove_leaves": (85, 140, 45), "cherry_leaves": (240, 170, 200), "azalea_leaves": (95, 140, 50), "flowering_azalea_leaves": (150, 130, 130),
        "oak_wood": (105, 80, 50), "spruce_wood": (60, 42, 25), "jungle_wood": (90, 70, 35), "dark_oak_wood": (55, 40, 25), "mangrove_wood": (90, 60, 40),
        "cherry_wood": (60, 35, 45), "oak_log": (105, 80, 50), "mangrove_roots": (80, 60, 40), "red_mushroom_block": (200, 45, 45),
        "brown_mushroom_block": (150, 110, 80), "mushroom_stem": (200, 195, 185), "oak_planks": (160, 130, 80), "spruce_planks": (110, 80, 50),
        "shroomlight": (245, 170, 80), "lily_pad": (40, 140, 40)}
PLANTES_V = PLANTES | {"moss_carpet", "pink_petals", "light", "barrier", "vine", "glow_lichen", "ladder", "lantern", "chain", "seagrass"}
E = 4
img = Image.new("RGB", (SX * E + 20, SZ * E + 44), (24, 34, 28))
dr = ImageDraw.Draw(img)
hauts = {}
for (x, y, z), s in blocs.items():
    n = s[0].split(":")[1]
    if n in PLANTES_V or n == "air" or y > SOL + 26:
        continue
    if y > hauts.get((x, z), (-99, None))[0]:
        hauts[(x, z)] = (y, n)
for (x, z), (y, n) in hauts.items():
    c = COUL.get(n, (150, 150, 150))
    k = 0.72 + 0.28 * min(1, max(0, (y - SOL) / 20))
    dr.rectangle([10 + x * E, 34 + z * E, 10 + x * E + E - 1, 34 + z * E + E - 1], fill=tuple(int(v * k) for v in c))
for num, (x, y, z), _ in SALLES:
    dr.ellipse([10 + x * E - 9, 34 + z * E - 9, 10 + x * E + 9, 34 + z * E + 9], outline=(255, 235, 60), width=3)
    dr.text((10 + x * E + 11, 34 + z * E - 6), str(num), fill=(255, 235, 60))
dr.text((10, 10), "ARENE NATURE - vue de dessus sous la canopee (nord en haut). Cercles jaunes = salles cachees", fill=(220, 240, 220))
img.save(PLAN)

CACHE = PLANTES_V | {"air", "big_dripleaf_stem", "hanging_roots", "cave_vines", "cave_vines_plant", "spore_blossom", "azalea", "flowering_azalea"}
s_ = 3
Wd = (SX + SZ) * s_ + 40
Hh = (SX + SZ) * s_ // 2 + SY * s_ + 60
img = Image.new("RGB", (Wd, Hh), (150, 200, 235))
dr = ImageDraw.Draw(img)


def ouvert(p):
    n = nom(p)
    return (p[1] > SOL) if n is None else n in CACHE


vis = [(p, s[0].split(":")[1]) for p, s in blocs.items() if s[0].split(":")[1] not in CACHE]
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
img.save(VUE)

print(f"Arene ecrite : {len(liste)} blocs, palette {len(palette)}, taille {SX}x{SY}x{SZ}, arbres {arbres}, colonnes {n_col}, monde present : {MONDE_OK}")
for num, p, acces in SALLES:
    print(f"  salle {num}: {monde(*p)}")
print(f"POSE : /tp @s {CENTRE_MONDE[0]} {ARGS.sol + 66} {CENTRE_MONDE[1]}   puis   /place template atelier:{NOM} {ORIGINE[0]} {ORIGINE[1]} {ORIGINE[2]}")
