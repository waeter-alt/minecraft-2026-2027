# [claude-build]
"""
arene_glace.py - Arene PvP "Glace" : cercle de 252 blocs (centre entre 4 blocs), lac gele, trone, pics de glace,
crevasses, grotte de glace, labyrinthes a coeur solide. Aucune eau, aucun bloc ice. 7 phases verifiees.
  .venv\\Scripts\\python.exe sources\\arene_glace.py --monde "Arene Glace" --centre 0 0 --sol 86
Controle : .venv\\Scripts\\python.exe sources\\verif_acces.py exports\\arene_glace.nbt exports\\arene_glace_cibles.json
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
ap.add_argument("--monde", default="Arene Glace")
ap.add_argument("--centre", nargs=2, type=int, default=[0, 0], metavar=("X", "Z"))
ap.add_argument("--sol", type=int, default=86)
ap.add_argument("--sous-sol", default="packed_ice")
ap.add_argument("--surface", default="snow_block")
ARGS = ap.parse_args()

NOM = "arene_glace"
RACINE = Path(__file__).resolve().parent.parent
SAVE = RACINE / "launcher/instances/Build-1.21.1/minecraft/saves" / ARGS.monde
MONDE_OK = (SAVE / "level.dat").exists()
DATAPACK = SAVE / "datapacks/atelier"
SORTIES = [RACINE / "exports" / f"{NOM}.nbt"] + ([DATAPACK / "data/atelier/structure" / f"{NOM}.nbt"] if MONDE_OK else [])
FONCTION = DATAPACK / "data/atelier/function" / f"{NOM}_effacer.mcfunction"
PLAN = RACINE / "exports" / f"{NOM}_plan.png"
VUE = RACINE / "exports" / f"{NOM}_vue.png"
LISTE_SALLES = RACINE / "salles_cachees_glace.md"
CIBLES = RACINE / "exports" / f"{NOM}_cibles.json"

DATA_VERSION = 3955
SX = SZ = 252
SY = 84
SOL = 18
C = 126
CEN = R = 126.0
R_MUR = 121.5
R_LAC = 34
CENTRE_MONDE = tuple(ARGS.centre)
ORIGINE = (CENTRE_MONDE[0] - C, ARGS.sol - SOL, CENTRE_MONDE[1] - C)
rng = random.Random(25227)


# ====================================================================== outils
def S(nom_, **p):
    return ("minecraft:" + nom_, tuple(sorted((k, str(v).lower() if isinstance(v, bool) else str(v)) for k, v in p.items())))


AIR = S("air")
blocs = {}


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


FACE_ECH = {(-1, 0): "east", (1, 0): "west", (0, -1): "south", (0, 1): "north"}

# ====================================================================== blocs
NEIGE = S("snow_block")
PACK = S("packed_ice")
BLEU = S("blue_ice")
CAL = S("calcite")
DIO = S("polished_diorite")
QZ = S("quartz_block")
QP = S("quartz_pillar", axis="y")
QS = S("smooth_quartz")
QB = S("quartz_bricks")
WC = S("white_concrete")
LBC = S("light_blue_concrete")
G_B = S("light_blue_stained_glass")
G_W = S("white_stained_glass")
SB = S("stone_bricks")
DSB = S("deepslate_bricks")
DST = S("deepslate_tiles")
PDS = S("polished_deepslate")
CDS = S("cobbled_deepslate")
TUF = S("tuff")
TUFB = S("tuff_bricks")
PRI = S("prismarine")
DPRI = S("dark_prismarine")
SLOG = S("spruce_log", axis="y")
SLOGS = S("stripped_spruce_log", axis="y")
SPL = S("spruce_planks")
LUM = S("sea_lantern")
FROG = S("pearlescent_froglight", axis="y")
SLAN = S("soul_lantern", hanging=False, waterlogged=False)
SLAN_P = S("soul_lantern", hanging=True, waterlogged=False)
CHAINE = S("chain", axis="y", waterlogged=False)
BARS = S("iron_bars", waterlogged=False)
TRAPPE = S("spruce_trapdoor", facing="north", half="top", open=False, powered=False, waterlogged=False)
LUMIERE = S("light", level=11, waterlogged=False)   # 11 : ne fait pas fondre la neige
BARRIERE = S("barrier", waterlogged=False)
POUDRE = S("powder_snow")
DALLE = S("smooth_quartz_slab", type="bottom", waterlogged=False)
SPF = S("spruce_fence", waterlogged=False)


def COUCHE(n):
    return S("snow", layers=n)


T_NEIGE = [(72, NEIGE), (14, PACK), (8, CAL), (6, DIO)]
T_SENTIER = [(40, DIO), (30, PACK), (20, WC), (10, CAL)]
T_LAC = [(55, PACK), (30, BLEU), (15, LBC)]
T_LISIERE = [(55, NEIGE), (20, CDS), (15, TUF), (10, PACK)]
T_FOND = [(50, PACK), (30, BLEU), (20, CDS)]
T_GLACE = [(60, PACK), (40, BLEU)]
T_MUR = [(28, PACK), (20, BLEU), (22, NEIGE), (15, CAL), (15, DIO)]
T_RUINE = [(35, DSB), (25, S("cracked_deepslate_bricks")), (20, TUFB), (20, CDS)]
T_GIVRE = [(45, DIO), (30, CAL), (25, SB)]
T_SALLE = [(40, DIO), (25, PACK), (20, QB), (15, DSB)]
T_ROCHE = [(55, PACK), (25, BLEU), (20, NEIGE)]

NON_SOLIDES = {"air", "snow", "powder_snow", "ladder", "light", "soul_lantern", "chain", "spruce_trapdoor"}
INTERDITS = {"chest", "trapped_chest", "barrel", "hopper", "dropper", "dispenser", "furnace", "blast_furnace", "smoker",
             "spawner", "ender_chest", "lava", "fire", "soul_fire", "ice", "water", "frosted_ice"}
SOURCES = {"sea_lantern": 15, "pearlescent_froglight": 15, "soul_lantern": 10, "light": 11}

SALLES, ECHELLES, APPARITIONS, PIEGES = [], [], [], []
CIBLES_ACCES = {}
occ = {}

# ====================================================================== champs du terrain
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


def champ_ligne(ligne):
    d = np.full((SX, SZ), 1e9)
    tt = np.zeros((SX, SZ))
    for i, (px, pz) in enumerate(ligne):
        dd = np.hypot(ix + 0.5 - px, iz + 0.5 - pz)
        m = dd < d
        d[m] = dd[m]
        tt[m] = i / (len(ligne) - 1)
    return d, tt


CREV_SE = ligne_polaire([(41, 18), (50, 26), (58, 22), (63, 9)])
CREV_NO = ligne_polaire([(41, 205), (49, 197), (58, 204), (63, 192)])
N1 = np.array([[bruit(x, z, 23, 1) for z in range(SZ)] for x in range(SX)])
N2 = np.array([[bruit(x, z, 9, 2) for z in range(SZ)] for x in range(SX)])
N3 = np.array([[bruit(x, z, 6, 3) for z in range(SZ)] for x in range(SX)])
lac = RR + 3 * (N3 - 0.5) < R_LAC
ilot = RR < 12.5

LABYS = {"SE": dict(c=52, span=20, coeur=SB, peau=[(60, PACK), (40, BLEU)], cap=NEIGE),
         "SO": dict(c=122, span=20, coeur=DSB, peau=[(65, NEIGE), (35, CAL)], cap=PACK),
         "NO": dict(c=225, span=24, coeur=QZ, peau=[(70, BLEU), (30, PACK)], cap=NEIGE),
         "NE": dict(c=302, span=20, coeur=DSB, peau=[(50, NEIGE), (50, DIO)], cap=BLEU)}


def adiff_np(a):
    return np.abs((TH - a + 180) % 360 - 180)


laby_zone = np.zeros((SX, SZ), bool)
for q in LABYS.values():
    laby_zone |= (RR >= 71) & (RR <= 107.5) & (adiff_np(q["c"]) <= q["span"] + 2)
axes = np.minimum(np.abs(DX), np.abs(DZ))
sentier = (axes <= 3.6 + 1.0 * (N3 - 0.5)) & (RR >= R_LAC) & (RR < 122)
for r0 in (69, 109.5):
    sentier |= np.abs(RR - r0) <= 2.2 + 1.2 * (N3 - 0.5)

COLLINES = [(90, 14, 21, 13), (54, 200, 0, 0), (46, 225, 0, 0), (96, 108, 11, 6), (86, 190, 9, 5), (114, 30, 9, 6),
            (114, 210, 9, 6), (114, 300, 8, 5), (46, 120, 8, 5), (112, 130, 8, 6), (44, 300, 12, 7), (52, 240, 12, 6)]
TOURS = {"NE": dict(a=315, r=58), "SE": dict(a=45, r=58), "SO": dict(a=135, r=58), "NO": dict(a=225, r=58)}
PLATS = []
for t in TOURS.values():
    t["c"] = cellule(t["r"], t["a"])
    PLATS.append((*t["c"], 13))
PIC_SALLE = cellule(50, 76)
RUINES = cellule(52, 112)
CONGERE = cellule(86, 215) if False else cellule(52, 172)
PLATS += [(*PIC_SALLE, 11), (*RUINES, 15)]

f = lisse(0, 4, axes - 4) * lisse(37, 44, RR) * lisse(0, 3, 121 - RR)
f *= 1 - ((np.abs(RR - 69) < 3.5) * 1.0) * 0.9
f *= 1 - ((np.abs(RR - 109.5) < 3.5) * 1.0) * 0.9
hf = np.clip(4.8 * np.clip(N1 - 0.3, 0, 1) * 2 + 2.4 * (N2 - 0.45), 0, None)
for r_, a_, rad, h in COLLINES:
    if rad:
        px, pz = point(r_, a_)
        d = np.hypot(ix + 0.5 - px, iz + 0.5 - pz) * (0.9 + 0.2 * N2)
        hf = hf + h * np.cos(np.clip(d / rad, 0, 1) * math.pi / 2) ** 2
cgx, cgz = CONGERE
hf = hf + 13 * np.cos(np.clip(np.hypot(ix + .5 - cgx, iz + .5 - cgz) * (0.9 + 0.2 * N2) / 15, 0, 1) * math.pi / 2) ** 2
for cx, cz, rad in PLATS:
    f = f * lisse(rad, rad + 5, np.hypot(ix + 0.5 - cx, iz + 0.5 - cz))
f = f * (1 - laby_zone)
Hm = SOL + np.rint(hf * f).astype(int)
Hm = np.where(ilot, SOL + 2, np.where(lac, SOL, Hm))

crevasse = np.zeros((SX, SZ), bool)
for ligne in (CREV_SE, CREV_NO):
    d, tt = champ_ligne(ligne)
    w = 3.2 + 1.5 * N3
    prof = 11 * np.sin(np.clip(tt, 0, 1) * math.pi) * lisse(0, 1.6, w - d)
    m = d < w
    Hm = np.where(m, SOL - np.rint(prof).astype(int), Hm)
    crevasse |= m

# glacier a cascade gelee : plateau, falaise au sud, cuvette de glace
HCX, HCZ = cellule(90, 14)
BZ0 = HCZ + 2
Y_CH = SOL + 13
for x in range(HCX - 11, HCX + 12):
    for z in range(HCZ - 9, BZ0):
        Hm[x, z] = max(Hm[x, z], Y_CH + (1 if abs(x - HCX) > 6 else 2))
for x in range(HCX - 9, HCX + 10):
    for z in range(BZ0, BZ0 + 12):
        Hm[x, z] = SOL


def Hs(x, z):
    return int(Hm[x, z])


def reserver(cx, cz, r, role="reserve"):
    for c in disque(cx + 0.5, cz + 0.5, r):
        occ[c] = role


def libre(x, z, m=0):
    for a in range(-m, m + 1):
        for b in range(-m, m + 1):
            p = (x + a, z + b)
            if not (0 <= p[0] < SX and 0 <= p[1] < SZ) or p in occ or sentier[p] or RR[p] >= 120 or lac[p] or crevasse[p]:
                return False
    return True


def echelle(x, z, y0, y1, sup, trappes=(), mat=DIO):
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


def stalactite(x, y, z, n, g=0):
    """pend sous un plafond : case y juste sous le bloc"""
    for k in range(n):
        if not vide((x, y - k, z)):
            break
        put(x, y - k, z, BLEU if h01(x, y - k, z, g) < 0.5 else PACK)


def solide(p):
    n = nom(p)
    if n is None:
        return p[1] <= SOL
    return n not in NON_SOLIDES


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
        if n == "snow":
            b = nom((x, y - 1, z))
            if b in ("packed_ice", "blue_ice", "barrier", "ice") or (not solide((x, y - 1, z)) and b != "spruce_trapdoor"):
                err.append(f"couche de neige sur {b} en {p}")
        elif n == "soul_lantern":
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


# ====================================================================== 1. terrain, lac, crevasses, grotte
for x in range(SX):
    for z in range(SZ):
        d = RR[x, z]
        if d >= R_MUR + 1:
            continue
        h = Hs(x, z)
        u = h01(x, 1, z, 5)
        if lac[x, z] and not ilot[x, z]:
            fente = abs(bruit(x, z, 11, 40) - 0.5) < 0.018 or abs(bruit(x, z, 17, 41) - 0.5) < 0.012
            spir = (TH[x, z] + d * 7) % 90 < 4
            put(x, h, z, NEIGE if fente else LBC if spir else choix(T_LAC, u))
            continue
        if crevasse[x, z]:
            t = T_FOND
        elif sentier[x, z] and d > R_LAC:
            t = T_SENTIER
        elif d > 106:
            t = T_LISIERE
        else:
            t = T_NEIGE
        put(x, h, z, choix(t, u))
        for y in range(h - 1, SOL - 1, -1):
            put(x, y, z, NEIGE if y >= h - 2 else PACK if h01(x, y, z, 6) < 0.8 else BLEU)
        for y in range(h + 1, SOL + 1):
            put(x, y, z, AIR)
for x in range(1, SX - 1):                             # parois des crevasses : veines de glace bleue et de pierre
    for z in range(1, SZ - 1):
        if crevasse[x, z]:
            h = Hs(x, z)
            for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                h2 = Hs(x + a, z + b)
                for y in range(h + 1, h2 + 1):
                    if nom((x + a, y, z + b)) in (None, "packed_ice", "snow_block"):
                        u = bruit(x + y, z, 4, 42)
                        put(x + a, y, z + b, BLEU if u > 0.62 else CDS if u < 0.3 else PACK)

# grotte de glace
CVX, CVZ = cellule(70, 250)
CVY = 9
CAVE = {}
for x in range(CVX - 28, CVX + 29):
    for z in range(CVZ - 22, CVZ + 23):
        e = ((x - CVX) / 24) ** 2 + ((z - CVZ) / 18) ** 2 + 0.35 * (bruit(x, z, 7, 8) - 0.5)
        if e > 1.3:
            continue
        if e > 1:
            for y in range(CVY - 7, CVY + 7):
                put(x, y, z, BLEU if bruit(x + y, z, 5, 9) > 0.55 else PACK)
            continue
        fond = CVY - 4 + int(2 * bruit(x, z, 6, 10))
        haut = CVY + 3 + int(3 * (1 - e)) + int(2 * bruit(x, z, 5, 11))
        CAVE[(x, z)] = (fond, haut)
        for y in range(fond - 2, haut + 3):
            if y <= fond or y > haut:
                put(x, y, z, BLEU if bruit(x + y, z, 5, 12) > 0.5 else PACK)
            else:
                put(x, y, z, AIR)

ENT_X, ENT_Z = CVX + 34, CVZ
for k in range(0, SOL - (CVY - 5)):
    x = ENT_X - k
    y = SOL - 1 - k
    for s in range(-2, 3):
        z = ENT_Z + s
        if abs(s) == 2:
            for yy in range(y, min(y + 5, SOL + 1)):
                put(x, yy, z, BLEU if h01(x, yy, z, 14) < 0.5 else PACK)
        else:
            put(x, y, z, S("deepslate_brick_stairs", facing="east", half="bottom", shape="straight", waterlogged=False))
            put(x, y - 1, z, DSB)
            for yy in range(y + 1, min(y + 5, SOL + 1)):
                put(x, yy, z, AIR)
            if y + 5 <= SOL:
                put(x, y + 5, z, PACK)
    if (x, ENT_Z) in CAVE and k > 6 and y <= CAVE[(x, ENT_Z)][0] + 1:
        break
for s in (-1, 0, 1):
    for x in range(ENT_X + 1, ENT_X + 3):
        put(x, SOL, ENT_Z + s, DSB)
for dz_ in (-3, 3):
    for y in range(SOL + 1, SOL + 7):
        put(ENT_X, y, ENT_Z + dz_, BLEU if y % 2 else PACK)
for s in range(-3, 4):
    put(ENT_X, SOL + 7, ENT_Z + s, BLEU)
    stalactite(ENT_X, SOL + 6, ENT_Z + s, 1 + (s % 2))
reserver(ENT_X - 6, ENT_Z, 11, "grotte")
for x in range(ENT_X - 14, ENT_X + 3):
    for z in range(ENT_Z - 3, ENT_Z + 4):
        sentier[x, z] = False
verifier("1. terrain, lac, grotte")

# ====================================================================== 2. rempart et 4 portes
for x in range(SX):
    for z in range(SZ):
        d = RR[x, z]
        if d < R_MUR or d >= R:
            continue
        a = TH[x, z]
        hm = SOL + 15 + int(4 * bruit(a * 1.1, 0, 7, 20))
        crenel = int(a * 1.6) % 2 == 0 and d >= 124
        for y in range(SOL, hm + 1):
            bande = int(a / 7) * 13 + (y - SOL) // 3
            put(x, y, z, choix(T_MUR, h01(bande, 0, 0, 22)))
        put(x, hm + 1, z, NEIGE if d < 124 else DIO)
        if crenel:
            put(x, hm + 2, z, DIO)
            put(x, hm + 3, z, NEIGE)
        if d >= 125:
            for y in range(hm + 4, SY):
                put(x, y, z, BARRIERE)
            if not crenel:
                for y in range(hm + 2, hm + 4):
                    put(x, y, z, BARRIERE)
for x in range(SX):                                    # corniche et stalactites sur la face interieure
    for z in range(SZ):
        if not R_MUR - 1 <= RR[x, z] < R_MUR:
            continue
        a = TH[x, z]
        hm = SOL + 15 + int(4 * bruit(a * 1.1, 0, 7, 20))
        put(x, hm, z, BLEU)
        put(x, hm - 1, z, PACK)
        if h01(x, 4, z, 26) < 0.5:
            stalactite(x, hm - 2, z, 1 + int(4 * h01(x, 5, z, 27)), 28)

for a in range(6, 360, 18):                            # contreforts et congeres au pied du rempart
    if min(adiff(a, g) for g in (0, 90, 180, 270)) < 11:
        continue
    px, pz = point(119.8, a)
    hc = 7 + int(9 * h01(a, 0, 0, 18))
    for x, z in disque(px, pz, 3.1):
        if RR[x, z] < R_MUR:
            for y in range(Hs(x, z) + 1, SOL + hc + int(3 * h01(x, 1, z, 17))):
                put(x, y, z, choix(T_ROCHE, h01(x, y, z, 16)))
            occ[(x, z)] = "contrefort"
    for x, z in disque(*point(116.5, a + 3), 3.6):
        if RR[x, z] < R_MUR and not sentier[x, z] and h01(x, 2, z, 15) < 0.7:
            for y in range(Hs(x, z) + 1, Hs(x, z) + 2 + int(2 * h01(x, 3, z, 14))):
                put(x, y, z, NEIGE)


def pic(cx, cz, r0, h, g, tx=0.0, tz=0.0, y0=None, table=T_GLACE):
    """pic de glace : cone penche, pose sur le relief"""
    y0 = Hs(cx, cz) if y0 is None else y0
    for k in range(h + 1):
        t = k / h
        r_ = r0 * (1 - t) ** 0.85
        ax, az = cx + .5 + tx * k, cz + .5 + tz * k
        y = y0 + k
        if y >= SY:
            break
        for x, z in disque(ax, az, r_ + 0.4):
            if 0 <= x < SX and 0 <= z < SZ and RR[x, z] < R_MUR - 0.5 and y > Hs(x, z):
                if math.hypot(x + .5 - ax, z + .5 - az) <= r_ + 0.35 * (bruit(x + k, z, 3, g) - 0.3):
                    put(x, y, z, choix(table, bruit(x + y, z - y, 4, g)))


for g in (0, 90, 180, 270):
    for al in range(112, 126):                         # passage ferme par une herse
        for sp in range(-5, 5):
            x, z = gl(al, sp, g)
            if dist(x, z) >= R:
                continue
            hh = SOL + 9 + int(3 * (1 - ((sp + 0.5) / 5) ** 2))
            if al >= 119:
                put(x, SOL, z, DIO)
                for y in range(SOL + 1, hh + 1):
                    put(x, y, z, AIR if al < 123 else PACK if al > 123 else BARS)
    for sp in (9, -10):                                # pics geants encadrant l'entree
        tx, tz = gl(114, sp, g)
        pic(tx, tz, 4.8, 30, 30 + g, y0=SOL)
        reserver(tx, tz, 8, "porte")
    for sp in range(-9, 9):                            # arche de glace
        for al in (113, 114, 115):
            x, z = gl(al, sp, g)
            base = SOL + 11 + int(4 * (1 - ((sp + 0.5) / 9) ** 2))
            for y in range(base, SOL + 19):
                put(x, y, z, NEIGE if y == SOL + 18 else BLEU if (sp + y) % 3 else PACK)
            if sp % 2 == 0:
                stalactite(x, base - 1, z, 1 + (sp % 4 == 0))
    for sp in (-6, 5):
        for k in (0, 4):
            x, z = gl(108 + k, sp, g)
            for y in range(SOL + 1, SOL + 6):
                put(x, y, z, DPRI if y == SOL + 1 else PRI)
            put(x, SOL + 6, z, SLAN)
    for sp in range(-4, 4, 3):
        x, z = gl(114, sp, g)
        for y in range(SOL + 15, SOL + 7, -1):
            if vide((x, y, z)) and solide((x, y + 1, z)):
                put(x, y, z, CHAINE)
                put(x, y - 1, z, SLAN_P)
                break
    reserver(*gl(116, 0, g), 8, "porte")
verifier("2. rempart, 4 portes")


# ====================================================================== 3. tours, pics, zone centrale
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


def pic_creux(cx, cz, rext, h, a0, g, effile):
    """pic geant creux, escalier interieur, terrasse puis aiguille"""
    for y in range(SOL, SOL + h + 1):
        t = (y - SOL) / h
        r_ = rext * (1 - (0.42 if effile else 0.3) * t) + (2.6 * (1 - t / 0.14) ** 2 if t < 0.14 else 0)
        for x, z in disque(cx + .5, cz + .5, r_ + 1):
            d = math.hypot(x - cx, z - cz)
            if d <= 4.3:
                put(x, y, z, DIO if y == SOL else AIR)
            elif d <= r_ * (0.92 + 0.16 * bruit(x + y, z, 4, g)):
                fen = (y - SOL) % 9 == 4 and h01(x, y, z, g) < 0.3
                put(x, y, z, G_B if fen else choix(T_GLACE, bruit(x + y, z - y, 4, g)))
    for y in range(SOL + 1, SOL + h + 1):
        for x, z in disque(cx + .5, cz + .5, 1.5):
            put(x, y, z, QP)
    spirale(cx, cz, 1.5, 4.3, SOL, h, 9, a0, QS, DALLE)
    pas = 20
    ro = rext * (1 - (0.42 if effile else 0.3))
    for x, z in disque(cx + .5, cz + .5, ro + 1):
        d = math.hypot(x - cx, z - cz)
        la = math.degrees(math.atan2(z - cz, x - cx)) % 360
        t0 = (la - a0) % 360
        dernier = t0 + 360 * ((h * 2 * pas - t0) // 360)
        if 1.5 <= d <= 4.3 and dernier >= (h - 4) * 2 * pas:
            continue
        put(x, SOL + h, z, QS)
        if d > ro - 0.3:
            put(x, SOL + h + 1, z, BLEU)
    for x, z in disque(cx + .5, cz + .5, 1.5):
        put(x, SOL + h, z, QP)
    put(cx, SOL + h + 1, cz, FROG)
    percer(cx, cz, a0 - 8, 3.4, rext + 3.8, SOL + 1, 3, sol=DIO)
    for a in range(0, 360, 90):                        # couronne d'aiguilles autour de la terrasse
        px, pz = cx + (ro - 0.5) * math.cos(math.radians(a + 45)), cz + (ro - 0.5) * math.sin(math.radians(a + 45))
        pic(math.floor(px), math.floor(pz), 1.6, 12 + (a // 90) * 2, g + a, y0=SOL + h + 1)


def tour_fort(cx, cz, forme, ri, ro, H_, a_porte, mur, band, ruine, cle):
    for x in range(cx - ro - 1, cx + ro + 2):
        for z in range(cz - ro - 1, cz + ro + 2):
            m = math.hypot(x - cx, z - cz) if forme == "rond" else max(abs(x - cx), abs(z - cz))
            if m > ro:
                continue
            if m > ri:
                hm = H_ + 1 + (int(7 * bruit(x, z, 3, 44)) if ruine else (x + z) % 2 * 2)
                for y in range(SOL + 1, hm + 1):
                    e = y - SOL
                    fen = e % 8 in (4, 5) and (x + z) % 7 < 2 and e < H_ - SOL
                    put(x, y, z, G_B if fen else band if e % 10 == 0 else choix(mur, h01(x, y, z, 45)))
            else:
                put(x, SOL, z, DST)
                for y in range(SOL + 1, H_):
                    put(x, y, z, AIR)
                put(x, SOL + 12, z, SPL)
                put(x, H_, z, DIO)
    for y in range(SOL + 1, H_ + 2):
        put(cx, y, cz, SLOGS)
    echelle(cx + 1, cz, SOL + 1, H_ + 1, (-1, 0), mat=SPL)
    percer(cx, cz, a_porte, ri - 1, ro * 1.45 + 1.5, SOL + 1, 3, sol=DST)
    for y in (SOL + 1, SOL + 13, H_ + 1):
        for dx, dz in ((3, 3), (-3, -3)):
            if vide((cx + dx, y, cz + dz)):
                put(cx + dx, y, cz + dz, SLAN)
    for x in range(cx - ro - 2, cx + ro + 3):          # stalactites sous les corniches
        for z in range(cz - ro - 2, cz + ro + 3):
            m = math.hypot(x - cx, z - cz) if forme == "rond" else max(abs(x - cx), abs(z - cz))
            if ro < m <= ro + 1 and h01(x, 7, z, 46) < 0.45:
                put(x, SOL + 20, z, PACK)
                stalactite(x, SOL + 19, z, 1 + int(3 * h01(x, 8, z, 47)))
    CIBLES_ACCES[f"haut de la tour {cle}"] = [cx - 2, H_ + 1, cz]


pic_creux(*TOURS["NE"]["c"], 9.5, 28, 150, 100, False)
pic_creux(*TOURS["NO"]["c"], 9.0, 33, 40, 120, True)
CIBLES_ACCES["sommet du pic creux NE"] = [TOURS["NE"]["c"][0] - 4, SOL + 29, TOURS["NE"]["c"][1] - 2]
CIBLES_ACCES["sommet du pic creux NO"] = [TOURS["NO"]["c"][0] - 4, SOL + 34, TOURS["NO"]["c"][1] - 2]
tour_fort(*TOURS["SE"]["c"], "carre", 5, 7, SOL + 30, 225, T_RUINE, PDS, False, "SE")
tour_fort(*TOURS["SO"]["c"], "rond", 5, 7, SOL + 26, 315, [(50, CAL), (30, QB), (20, DIO)], BLEU, True, "SO")
for t in TOURS.values():
    reserver(*t["c"], 13, "tour")

# ilot, trone de glace et aiguilles
for x, z in disque(CEN, CEN, 12.6):
    d = dist(x, z)
    h = SOL + 2
    put(x, h, z, QS if d < 5.5 else CAL if d < 9 else NEIGE)
    put(x, h - 1, z, PACK)
    put(x, h - 2, z, PACK)
    if d < 8.5:
        put(x, h + 1, z, DALLE if d >= 7.5 else QB)
    if d < 7.5 and ((ang(x, z) + d * 16) % 60 < 14):
        put(x, h + 1, z, BLEU)
for x, z in disque(CEN, CEN, 14.4):                    # marche d'un bloc entre le lac et l'ilot
    if dist(x, z) > 12.6:
        put(x, SOL + 1, z, CAL if int(ang(x, z) / 10) % 2 else NEIGE)
        Hm[x, z] = SOL + 1
Y_T = SOL + 3
for i in range(-3, 3):                                 # trone, dos au nord, marches au sud
    for j in range(-7, -2):
        x, z = C + i, C + j
        put(x, Y_T + 1, z, DPRI if -3 <= i <= 2 else QZ)
        put(x, Y_T + 2, z, BLEU)
    for y in range(Y_T + 2, Y_T + 14):
        h = Y_T + 13 - 3 * abs(i + 0.5)
        if y <= h:
            put(C + i, y, C - 7, BLEU if (y + i) % 3 else PACK)
            put(C + i, y, C - 8, PACK)
for i in (-4, 3):
    for j in range(-8, -2):
        for y in (Y_T + 1, Y_T + 2, Y_T + 3):
            put(C + i, y, C + j, QZ if y < Y_T + 3 else BLEU)
put(C - 1, Y_T + 3, C - 5, AIR)
put(C, Y_T + 3, C - 5, AIR)
for a in (0, 60, 120, 180, 240, 300):
    cx, cz = cellule(10.5, a + 30)
    if a + 30 not in (270, 90):
        pic(cx, cz, 2.7, 13 + (a // 60) % 3 * 4, 60 + a // 60, y0=SOL + 2)
for a in (20, 100, 160, 200, 340):
    x, z = cellule(9, a)
    put(x, SOL + 3, z, FROG)
reserver(C, C, R_LAC + 3, "lac")
CIBLES_ACCES["ilot du trone"] = [C + 7, SOL + 3, C + 7]

# restes pris dans le lac : colonnes brisees, proue d'une epave de sapin
for a in (35, 125, 215, 305):
    x, z = cellule(24, a)
    for cx_, cz_ in disque(x + .5, z + .5, 1.6):
        for y in range(SOL + 1, SOL + 2 + int(6 * h01(cx_, 0, cz_, 62))):
            put(cx_, y, cz_, choix(T_GIVRE, h01(cx_, y, cz_, 63)))
x, z = cellule(24, 80)
for k in range(9):
    for s in range(-2 + k // 4, 3 - k // 4):
        put(x + k, SOL + 1, z + s, SPL)
        if abs(s) == 2 - k // 4:
            put(x + k, SOL + 2, z + s, SLOG)
for y in range(SOL + 2, SOL + 11):
    put(x + 3, y, z, SLOGS)
for s in range(-3, 4):
    put(x + 3, SOL + 8, z + s, SLOGS)

# grands pics du champ de bataille
GRANDS = [(44, 345, 6, 30), (46, 165, 6, 26), (86, 7 + 180, 5, 24), (96, 353, 6, 32), (92, 89 + 80, 5, 22), (96, 262, 6, 30), (43, 262, 5, 24), (44, 104, 5, 22)]
for i, (r_, a_, rad, h) in enumerate(GRANDS):
    x, z = cellule(r_, a_)
    if libre(x, z, 3):
        pic(x, z, rad, h, 70 + i, tx=rng.uniform(-.12, .12), tz=rng.uniform(-.12, .12))
        reserver(x, z, rad + 3, "pic")
pic(*PIC_SALLE, 10.5, 34, 80, y0=SOL)
reserver(*PIC_SALLE, 15, "pic")
verifier("3. tours, pics, centre")

# ====================================================================== 4. labyrinthes
LABY = {}


def labyrinthe(k, q):
    c0, span = q["c"], q["span"]

    def dedans(x, z):
        return 73 <= RR[x, z] <= 105.5 and adiff(TH[x, z], c0) <= span

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
    for x, z in murs:
        coeur = x % 6 == 0 or z % 6 == 0
        for y in range(SOL + 1, SOL + 6):
            put(x, y, z, q["coeur"] if coeur else choix(q["peau"], h01(x, y, z, 70)))
        put(x, SOL + 6, z, q["cap"])
    imp = [c for c in sorted(cases) if sum(c in p for p in passages) == 1 and c not in entrees]
    for i, j in imp:
        x, z = 6 * i + 3, 6 * j + 3
        if h01(i, 0, j, 72) < 0.6:
            put(x, SOL + 1, z, DIO)
            put(x, SOL + 2, z, SLAN)
    mid = min((c for c in sorted(cases) if c not in imp), key=lambda c: abs(ang(6 * c[0] + 3, 6 * c[1] + 3) - c0) + abs(dist(6 * c[0] + 3, 6 * c[1] + 3) - 89))
    CIBLES_ACCES[f"labyrinthe {k}"] = [6 * mid[0] + 3, SOL + 1, 6 * mid[1] + 3]
    LABY[k] = dict(cases=cases, imp=imp, passages=passages, entrees=entrees)


for k, q in LABYS.items():
    labyrinthe(k, q)
print("   labyrinthes :", {k: (len(v["cases"]), len(v["imp"])) for k, v in LABY.items()})
verifier("4. labyrinthes")


# ====================================================================== 5. parcours et ponts
def pont(p0, p1, y, demi, table, garde=None):
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
                if crevasse[x, z]:
                    stalactite(x, y - 1, z, 1 + int(3 * h01(x, 2, z, 76)))
            elif s <= demi + 1 and garde and vide((x, y + 1, z)):
                put(x, y, z, choix(table, h01(x, y, z, 77)))
                put(x, y + 1, z, garde)


# ponts de glace sur les crevasses, plateformes glissantes, poutre etroite
mx, mz = CREV_SE[len(CREV_SE) // 3]
a0 = math.degrees(math.atan2(mz - CEN, mx - CEN)) % 360
rr0 = math.hypot(mx - CEN, mz - CEN)
pont(point(rr0 - 9, a0), point(rr0 + 9, a0), SOL + 1, 1.6, T_GLACE, BARS)
PADS = []
mx, mz = CREV_SE[2 * len(CREV_SE) // 3]
a0 = math.degrees(math.atan2(mz - CEN, mx - CEN)) % 360
rr0 = math.hypot(mx - CEN, mz - CEN)
for k, dr in enumerate((-9, -4.5, 0, 4.5, 9)):
    px, pz = point(rr0 + dr, a0)
    for x, z in disque(px, pz, 2.0):
        put(x, SOL + 1, z, BLEU)
mx, mz = CREV_NO[len(CREV_NO) // 2]
a0 = math.degrees(math.atan2(mz - CEN, mx - CEN)) % 360
rr0 = math.hypot(mx - CEN, mz - CEN)
pont(point(rr0 - 10, a0), point(rr0 + 10, a0), SOL + 1, 0.6, T_GLACE)
mx, mz = CREV_NO[len(CREV_NO) // 5]
a0 = math.degrees(math.atan2(mz - CEN, mx - CEN)) % 360
rr0 = math.hypot(mx - CEN, mz - CEN)
pont(point(rr0 - 8, a0), point(rr0 + 8, a0), SOL + 1, 1.6, T_GLACE, BARS)


def pic_plat(cx, cz, ycap, rc, g):
    """pic a sommet plat pour sauter dessus"""
    y0 = Hs(cx, cz)
    for y in range(y0 + 1, ycap):
        t = (y - y0) / max(1, ycap - y0)
        r_ = (rc + 2.2) - 2.2 * t
        for x, z in disque(cx + .5, cz + .5, r_):
            if y > Hs(x, z):
                put(x, y, z, choix(T_GLACE, bruit(x + y, z, 3, g)))
    for x, z in disque(cx + .5, cz + .5, rc):
        put(x, ycap, z, BLEU if (x + z) % 2 else PACK)


P0 = point(50, 318)
P1 = point(73, 353)
NCH = 12
Y_CAB = SOL + NCH + 1
PICS = []
for i in range(NCH):
    t_ = (i + 0.3) / (NCH + 0.7)
    cx = math.floor(P0[0] + (P1[0] - P0[0]) * t_ + 0.8 * math.sin(i * 1.7))
    cz = math.floor(P0[1] + (P1[1] - P0[1]) * t_ + 0.8 * math.cos(i * 1.3))
    PICS.append((cx, cz))
    pic_plat(cx, cz, SOL + 2 + i, 2.1, 80 + i)
    reserver(cx, cz, 6, "parcours")
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
                    put(x, y, z, PRI if (x + z) % 2 else DIO)
                else:
                    put(x, y, z, choix(style, h01(x, y, z, 91 + num)))
    cx, cz = coffre
    put(cx, yf, cz, DPRI)
    libres = garder | {coffre}
    for x, z in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
        if (x, z) not in libres and math.hypot(x - cx, z - cz) > 1.5:
            put(x, yf + 1, z, SLAN)
    mx, mz = (x0 + x1) // 2, (z0 + z1) // 2
    put(mx, yf + h + 1, mz, FROG)
    if (mx, mz) not in libres and h >= 4:
        put(mx, yf + h, mz, CHAINE)
        put(mx, yf + h - 1, mz, SLAN_P)
    for x in range(x0, x1 + 1):                        # stalactites et frise de verre
        for z in range(z0, z1 + 1):
            if (x, z) not in libres and math.hypot(x - cx, z - cz) > 1.3 and h01(x, 1, z, 92) < 0.16 and vide((x, yf + h, z)):
                stalactite(x, yf + h, z, 1 + int(2 * h01(x, 2, z, 93)))
    for x in range(x0, x1 + 1):
        for z in (z0 - 1, z1 + 1):
            if (x + num) % 3 == 0:
                put(x, yf + 3, z, G_B)
    for z in range(z0, z1 + 1):
        for x in (x0 - 1, x1 + 1):
            if (z + num) % 3 == 0:
                put(x, yf + 3, z, G_B)
    SALLES.append((num, (cx, yf + 1, cz), acces))


# 1 : derriere la cascade gelee du glacier (la coulee est separee de la falaise par un couloir d'un bloc)
ZF = BZ0 - 1                                           # derniere rangee de la falaise
salle(1, HCX - 3, HCX + 3, ZF - 7, ZF - 1, SOL, 4, (HCX, ZF - 7), "Derrière la cascade gelée du glacier sud-est : couloir d'un bloc entre la coulée et la falaise, entrée côté est",
      garder=[(HCX, ZF - 1)])
for y in range(SOL + 1, SOL + 4):
    put(HCX, y, ZF, AIR)
put(HCX, SOL, ZF, DIO)
for x in range(HCX - 6, HCX + 8):                      # couloir derriere la coulee
    put(x, SOL, BZ0, PACK)
    for y in range(SOL + 1, SOL + 4):
        put(x, y, BZ0, AIR)
for x in range(HCX - 6, HCX + 7):                      # coulee : du haut de la falaise jusqu'au sol
    for y in range(SOL + 1, Y_CH + 2):
        put(x, y, BZ0 + 1, BLEU if bruit(x, y, 3, 95) > 0.42 else PACK)
        if bruit(x, y * 2, 4, 96) > 0.55:
            put(x, y, BZ0 + 2, BLEU if bruit(x, y, 2, 97) > 0.5 else PACK)
    for y in range(SOL + 4, Y_CH + 2):
        put(x, y, BZ0, PACK)
for y in range(SOL + 1, SOL + 4):                      # le couloir est ferme a l'ouest
    put(HCX - 7, y, BZ0, PACK)
    put(HCX - 7, y, BZ0 + 1, PACK)
CIBLES_ACCES["cuvette du glacier"] = [HCX, SOL + 1, BZ0 + 8]

# 2 : dans le grand pic
cx, cz = PIC_SALLE
salle(2, cx - 2, cx + 2, cz - 2, cz + 2, SOL, 4, (cx + 2, cz + 2), "À l'intérieur du grand pic de glace : fente d'un bloc, face nord",
      style=T_GLACE)
percer(cx, cz, 270, 2.2, 15.5, SOL + 1, 2, w=0, sol=DIO)

# 3 : sous le lac gele, par le puits de l'ilot
sx_, sz_ = C, C - 16
salle(3, sx_ - 3, sx_ + 3, sz_ - 3, sz_ + 3, SOL - 9, 4, (sx_ - 3, sz_), "Sous le lac gelé : puits à échelle derrière le trône, au nord de l'îlot, puis galerie",
      style=[(50, BLEU), (30, PACK), (20, DPRI)], garder=[(sx_, sz_ + 3)])
LX, LZ = C, C - 10
echelle(LX, LZ, SOL - 8, SOL + 2, (1, 0), mat=DIO)
for y in (SOL + 3, SOL + 4, SOL + 5):
    put(LX, y, LZ, AIR)
for zz in range(sz_ + 4, LZ + 1):                      # galerie du puits vers la salle
    put(LX, SOL - 9, zz, DIO)
    for y in (SOL - 8, SOL - 7, SOL - 6):
        if (LX, y, zz) != (LX, y, LZ):
            put(LX, y, zz, AIR)
    for s in (-1, 1):
        if not (LX + s == LX + 1 and zz == LZ):
            for y in (SOL - 8, SOL - 7, SOL - 6):
                put(LX + s, y, zz, PACK)
    if zz != LZ:
        put(LX, SOL - 5, zz, PACK)

# 4 : sous une trappe couverte de neige
tx, tz = cellule(115, 62)
reserver(tx, tz, 3, "salle")
for u, v in disque(tx + .5, tz + .5, 2.3):
    Hm[u, v] = SOL
    put(u, SOL, v, NEIGE)
    for y in range(SOL + 1, SOL + 8):
        if nom((u, y, v)) is not None:
            put(u, y, v, AIR)
    put(u, SOL + 1, v, COUCHE(1))
salle(4, tx - 3, tx + 3, tz - 3, tz + 3, SOL - 8, 4, (tx - 3, tz), "Lisière sud-est : trappe sous la neige (casser la couche de neige, puis ouvrir la trappe), échelle",
      garder=[(tx, tz), (tx + 1, tz)])
echelle(tx, tz, SOL - 7, SOL - 1, (1, 0))
put(tx, SOL, tz, TRAPPE)
TRAPPE_NEIGE = (tx, SOL + 1, tz)

# 5 : au fond de la crevasse nord-ouest
mx, mz = map(math.floor, CREV_NO[len(CREV_NO) * 2 // 3])
yf = Hs(mx, mz)
x0 = mx + 5
salle(5, x0, x0 + 6, mz - 3, mz + 3, yf, 4, (x0 + 6, mz), "Au fond de la crevasse nord-ouest : descendre par une extrémité, puis ouverture dans la paroi est",
      style=[(50, CDS), (30, BLEU), (20, PACK)])
for x in range(mx, x0):
    put(x, yf, mz, CDS)
    for y in (yf + 1, yf + 2, yf + 3):
        put(x, y, mz, AIR)

# 6 : congere creuse
cx, cz = CONGERE
salle(6, cx - 3, cx + 3, cz - 3, cz + 3, SOL, 5, (cx - 3, cz), "Congère creuse à l'ouest : tunnel d'un bloc de large, entrée côté sud",
      style=[(70, NEIGE), (30, PACK)])
z = cz + 4
while Hs(cx, z) > SOL + 2 and z < cz + 22:
    z += 1
for zz in range(cz + 4, z + 2):
    put(cx, SOL, zz, PACK)
    put(cx, SOL + 1, zz, AIR)
    put(cx, SOL + 2, zz, AIR)
    if zz <= z - 1:
        put(cx, SOL + 3, zz, NEIGE)
        for s in (-1, 1):
            put(cx + s, SOL + 1, zz, NEIGE)
            put(cx + s, SOL + 2, zz, NEIGE)

# 7 : refuge au bout du parcours de pics
cx, cz = math.floor(P1[0]), math.floor(P1[1])
px, pz = PICS[-1]
for x, z in disque(cx + .5, cz + .5, 6.5):
    put(x, Y_CAB, z, BLEU)
for k2 in range(Y_CAB - SOL):
    y = Y_CAB - 1 - k2
    for x, z in disque(cx + .5, cz + .5, 2.5 + 3 * (k2 / (Y_CAB - SOL))):
        if y > Hs(x, z):
            put(x, y, z, choix(T_GLACE, bruit(x + y, z, 3, 98)))
reserver(cx, cz, 10, "refuge")
salle(7, cx - 2, cx + 2, cz - 2, cz + 2, Y_CAB, 4, (cx + 2, cz), "Au bout du parcours de pics de glace : refuge de sapin perché, ouvert vers le dernier pic",
      style=[(60, SPL), (40, SLOG)])
sens = (-1, 0) if abs(px - cx) >= abs(pz - cz) and px < cx else (1, 0) if abs(px - cx) >= abs(pz - cz) else (0, -1) if pz < cz else (0, 1)
dx, dz = sens
for s in (-1, 0, 1):
    for y in (Y_CAB + 1, Y_CAB + 2, Y_CAB + 3):
        put(cx + dx * 3 + dz * s, y, cz + dz * 3 + dx * s, AIR)
    put(cx + dx * 3 + dz * s, Y_CAB, cz + dz * 3 + dx * s, SPL)
if sens == (1, 0):
    SALLES.pop()
    put(cx + 2, Y_CAB, cz, SPL)
    put(cx - 2, Y_CAB, cz, DPRI)
    SALLES.append((7, (cx - 2, Y_CAB + 1, cz), "Au bout du parcours de pics de glace : refuge de sapin perché, ouvert vers le dernier pic"))

# 8 : dans la grotte de glace
yf = CAVE[(CVX, CVZ)][0]
rz0 = CVZ - 27
salle(8, CVX - 3, CVX + 3, rz0, rz0 + 5, yf, 4, (CVX, rz0), "Dans la grotte de glace : galerie au nord, derrière un rideau de stalactites",
      style=[(50, BLEU), (30, PACK), (20, DSB)], garder=[(CVX, rz0 + 5)])
zz = rz0 + 6
while zz < CVZ:
    cf = CAVE.get((CVX, zz))
    put(CVX, yf, zz, PACK)
    for y in (yf + 1, yf + 2, yf + 3):
        put(CVX, y, zz, AIR)
    if cf is None:
        put(CVX, yf + 4, zz, BLEU)
        for s in (-1, 1):
            for y in (yf + 1, yf + 2, yf + 3):
                put(CVX + s, y, zz, BLEU)
    elif cf[0] <= yf + 1 and zz > CVZ - 12:
        break
    zz += 1
verifier("6. salles cachees")


# ====================================================================== 7. decoration, neige, eclairage
def colonne(cx, cz, h, brisee, glace):
    y0 = Hs(cx, cz)
    for x, z in disque(cx + .5, cz + .5, 1.6):
        hh = h if not brisee else h - int(4 * h01(x, 0, z, 110))
        for y in range(y0 + 1, y0 + hh + 1):
            put(x, y, z, choix(T_GLACE if glace else T_GIVRE, h01(x, y, z, 111)))
    if not brisee:
        for x, z in disque(cx + .5, cz + .5, 2.6):
            put(x, y0 + h + 1, z, NEIGE if glace else QS)


def colonne_tombee(cx, cz, a, L):
    ux, uz = math.cos(a), math.sin(a)
    for i in range(L):
        if i % 5 == 4:
            continue
        x, z = math.floor(cx + i * ux), math.floor(cz + i * uz)
        y = Hs(x, z)
        for u, v in ((0, 0), (1, 0), (0, 1), (1, 1)):
            put(x + u, y + 1, z + v, choix(T_GIVRE, h01(x, u, z, 113)))
            if h01(x, v, z, 114) < 0.5:
                put(x + u, y + 2, z + v, NEIGE)


# ruines a moitie enfouies (quadrant SO)
tx, tz = RUINES
reserver(tx, tz, 15, "ruines")
for x in range(tx - 12, tx + 13):
    for z in range(tz - 10, tz + 8):
        bord = abs(x - tx) == 12 or z in (tz - 10, tz + 7)
        if bord and not (z == tz - 10 and abs(x - tx) <= 2):
            h = 2 + int(6 * bruit(x, z, 4, 120))
            for y in range(SOL + 1, SOL + 1 + h):
                put(x, y, z, choix(T_RUINE, h01(x, y, z, 121)))
        elif h01(x, 2, z, 122) < 0.35:
            put(x, SOL, z, DST)
        elif bruit(x, z, 5, 123) > 0.6:
            for y in range(SOL + 1, SOL + 3):
                put(x, y, z, NEIGE)
for i in (-8, -3, 3, 8):
    for j in (-6, 3):
        for y in range(SOL + 1, SOL + 3 + int(8 * h01(i, 0, j, 124))):
            put(tx + i, y, tz + j, PDS)

# 4 pieges de neige poudreuse, en lisiere, sur des coins sans passage
for a_ in (22, 112, 202, 292):
    best = None
    for da in range(0, 30, 2):
        for s in (1, -1):
            for r_ in (117, 115, 113):
                x, z = cellule(r_, a_ + s * da)
                if libre(x, z, 3) and Hs(x, z) >= SOL and best is None:
                    best = (x, z)
    if best:
        x, z = best
        h = Hs(x, z)
        for cx_, cz_ in disque(x + .5, z + .5, 1.6):
            put(cx_, h, cz_, POUDRE)
            put(cx_, h - 1, cz_, POUDRE)
            put(cx_, h - 2, cz_, PACK)
            for y in range(h + 1, h + 4):
                if nom((cx_, y, cz_)) is not None:
                    put(cx_, y, cz_, AIR)
        put(x + 2, h, z, POUDRE)                       # marche de sortie : 1 bloc de fond
        put(x + 2, h - 1, z, PACK)
        reserver(x, z, 4, "piege")
        PIEGES.append((x, h, z))

n_pic = n_col = n_bois = 0
for i in range(9000):
    x, z = cellule(rng.uniform(37, 118.5), rng.uniform(0, 360))
    if n_pic >= 75:
        break
    if not libre(x, z, 2) or Hs(x, z) < SOL:
        continue
    rad = rng.uniform(1.8, 4.2)
    pic(x, z, rad, int(rad * rng.uniform(3, 5.5)), 200 + i % 90, tx=rng.uniform(-.2, .2), tz=rng.uniform(-.2, .2))
    reserver(x, z, rad + 2, "pic")
    n_pic += 1
for i in range(5000):
    x, z = cellule(rng.uniform(36, 118), rng.uniform(0, 360))
    if n_col >= 62:
        break
    if not libre(x, z, 2) or Hs(x, z) < SOL:
        continue
    if rng.random() < 0.72:
        colonne(x, z, rng.randint(5, 13), rng.random() < 0.5, rng.random() < 0.5)
    else:
        a = rng.uniform(0, 6.28)
        if not libre(math.floor(x + 9 * math.cos(a)), math.floor(z + 9 * math.sin(a)), 2):
            continue
        colonne_tombee(x, z, a, 10)
    reserver(x, z, 4, "colonne")
    n_col += 1
for i in range(9000):                                  # congeres, rochers, sapins morts, debris
    x, z = cellule(rng.uniform(35, 119), rng.uniform(0, 360))
    if n_bois >= 300:
        break
    if not libre(x, z, 1) or Hs(x, z) < SOL:
        continue
    u = rng.random()
    if u < 0.55:
        r_ = rng.uniform(1.6, 3.2)
        for cx_, cz_ in disque(x + .5, z + .5, r_):
            if libre(cx_, cz_):
                h = Hs(cx_, cz_)
                for y in range(h + 1, h + 1 + max(1, round((r_ - math.hypot(cx_ - x, cz_ - z)) * 1.1))):
                    put(cx_, y, cz_, NEIGE)
    elif u < 0.8:
        r_ = rng.uniform(1.3, 2.4)
        for cx_, cz_ in disque(x + .5, z + .5, r_):
            if libre(cx_, cz_):
                h = Hs(cx_, cz_)
                n = max(1, round((r_ - math.hypot(cx_ - x, cz_ - z)) * 1.4))
                for y in range(h + 1, h + 1 + n):
                    put(cx_, y, cz_, NEIGE if y == h + n else choix([(50, CDS), (30, TUF), (20, DSB)], h01(cx_, y, cz_, 130)))
    else:                                              # sapin mort
        y0 = Hs(x, z)
        h = rng.randint(5, 10)
        for y in range(y0 + 1, y0 + 1 + h):
            put(x, y, z, SLOG)
        for d_ in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if rng.random() < 0.6:
                yb = y0 + rng.randint(3, h)
                for k in (1, 2):
                    if vide((x + d_[0] * k, yb, z + d_[1] * k)):
                        put(x + d_[0] * k, yb, z + d_[1] * k, S("spruce_log", axis="x" if d_[0] else "z"))
    reserver(x, z, 3, "decor")
    n_bois += 1

# grotte : piliers de glace, stalactites, soul lanterns
for (x, z), (fond, haut) in CAVE.items():
    u = h01(x, 4, z, 140)
    if u < 0.1 and vide((x, haut, z)):
        stalactite(x, haut, z, 1 + int(3 * h01(x, 5, z, 141)))
    elif u < 0.13 and vide((x, fond + 1, z)):
        for y in range(fond + 1, fond + 2 + int(2 * h01(x, 6, z, 142))):
            put(x, y, z, BLEU)
for i in range(16):
    x = CVX + rng.randint(-20, 20)
    z = CVZ + rng.randint(-14, 14)
    if (x, z) in CAVE and abs(z - CVZ) > 2 and abs(x - CVX) > 2:
        fond, haut = CAVE[(x, z)]
        if haut - fond >= 6:
            for y in range(fond + 1, haut + 1):
                put(x, y, z, BLEU if y % 2 else PACK)
            put(x, fond + 3, z, FROG)
for ligne, n_ in ((CREV_SE, 8), (CREV_NO, 8)):         # crevasses : stalactites et lanternes de mer dans les parois
    for i in range(1, n_):
        px, pz = ligne[len(ligne) * i // n_]
        x, z = math.floor(px), math.floor(pz)
        h = Hs(x, z)
        if h < SOL - 3:
            for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                k = 1
                while k < 7 and vide((x + a * k, h + 4, z + b * k)):
                    k += 1
                if k < 7:
                    put(x + a * k, h + 4, z + b * k, LUM)
                    break

# neige au sol (couches, sur des blocs qui les portent)
for x in range(SX):
    for z in range(SZ):
        if RR[x, z] >= 121 or lac[x, z]:
            continue
        y = Hs(x, z)
        if nom((x, y, z)) not in ("snow_block", "calcite", "polished_diorite", "cobbled_deepslate", "tuff", "white_concrete", "deepslate_tiles") or not vide((x, y + 1, z)):
            continue
        u = bruit(x, z, 5, 150)
        if u > 0.45 and not sentier[x, z] or h01(x, 3, z, 151) < 0.12:
            put(x, y + 1, z, COUCHE(1 + int(max(0, u - 0.45) * 7)))
for i in range(10):                                    # apparitions, sur le chemin exterieur
    a = 9 + 36 * i
    for dr in (0, -1, 1, -2, 2):
        x, z = cellule(109.5 + dr, a)
        if nom((x, Hs(x, z) + 1, z)) in (None, "air", "snow"):
            break
    y = Hs(x, z)
    put(x, y, z, DPRI)
    put(x, y + 1, z, AIR)
    put(x, y + 2, z, AIR)
    APPARITIONS.append((x, y + 1, z))
for k, v in CIBLES_ACCES.items():
    if k.startswith("labyrinthe"):
        put(v[0], v[1], v[2], AIR)
        put(v[0], v[1] + 1, v[2], AIR)
for y in range(CAVE[(CVX, CVZ)][0] + 1, CAVE[(CVX, CVZ)][0] + 4):
    put(CVX, y, CVZ, AIR)
verifier("7a. decoration, neige")

# eclairage calcule, puis neige retiree autour de toute source
TRANSP = NON_SOLIDES | {"barrier", "iron_bars", "light_blue_stained_glass", "white_stained_glass", "smooth_quartz_slab",
                        "deepslate_brick_stairs", "spruce_fence", "sea_lantern", "pearlescent_froglight"}
SANS_SPAWN = {"barrier", "iron_bars", "light_blue_stained_glass", "white_stained_glass", "smooth_quartz_slab",
              "deepslate_brick_stairs", "spruce_fence"}
coffres = {(p[0], p[2]) for _, p, _ in SALLES}
total_lum = 0


def calcul_lumiere():
    opaque = np.zeros((SX, SY, SZ), dtype=bool)
    opaque[:, :SOL + 1, :] = True
    L = np.zeros((SX, SY, SZ), dtype=np.int8)
    for (x, y, z), s in blocs.items():
        n = s[0].split(":")[1]
        opaque[x, y, z] = n not in TRANSP
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


def traversable(p):
    n = nom(p)
    return (p[1] > SOL) if n is None else n in ("air", "snow", "light")


def surfaces_noires(L):
    noires = []
    for (x, y, z), s in blocs.items():
        n = s[0].split(":")[1]
        if n in NON_SOLIDES or n in SANS_SPAWN or y + 2 >= SY or RR[x, z] >= 124.5:
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
        if (x, z) in coffres or any(abs(x - a) < 13 and abs(z - b) < 13 and abs(y - c) < 6 for a, c, b in pris):
            continue
        pris.append((x, y, z))
        put(x, y + 1, z, LUMIERE)
        total_lum += 1
print(f"   eclairage : {total_lum} points ajoutes, {tour_n} passes, surfaces noires restantes = {len(noires)}", flush=True)
if noires:
    print("   exemple :", noires[:6])
    raise SystemExit("il reste des surfaces noires")

sources = [p for p, s in blocs.items() if s[0].split(":")[1] in SOURCES and s[0] != "minecraft:light"]
pres = set()
for x, y, z in sources:
    for a in range(-3, 4):
        for b in range(-3, 4):
            for c in range(-3, 4):
                pres.add((x + a, y + b, z + c))
retirees = 0
for p, s in list(blocs.items()):
    if s[0] == "minecraft:snow" and p in pres and p != TRAPPE_NEIGE:
        put(*p, AIR)
        retirees += 1
if TRAPPE_NEIGE in pres:
    print("   attention : une source est a moins de 3 blocs de la trappe enneigee")
print(f"   couches de neige retirees pres des lumieres : {retirees}, restantes : {sum(1 for s in blocs.values() if s[0] == 'minecraft:snow')}")
verifier("7b. eclairage")

# ====================================================================== cibles et ecriture
CIBLES_ACCES["grotte"] = [CVX, CAVE[(CVX, CVZ)][0] + 1, CVZ]
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
    lignes.append(f'tellraw @s {{"text":"Arene {NOM} effacee","color":"aqua"}}')
    FONCTION.parent.mkdir(parents=True, exist_ok=True)
    FONCTION.write_text("\n".join(lignes) + "\n", encoding="utf-8")
    mc = DATAPACK / "pack.mcmeta"
    if not mc.exists():
        mc.write_text('{"pack": {"pack_format": 48, "description": "Atelier [claude-build]"}}\n', encoding="utf-8")

SALLES.sort()


def w(p):
    return " ".join(str(v) for v in monde(*p))


md = ["<!-- [claude-build] genere par sources/arene_glace.py -->", "# Salles cachées — arène Glace", "",
      f"Monde `saves/{ARGS.monde}`, arène centrée en X={CENTRE_MONDE[0]} Z={CENTRE_MONDE[1]}, sol à Y={ARGS.sol}. "
      "Coordonnées = case vide où poser le coffre.", "", "| N° | Coffre (X Y Z) | Accès |", "|---|---|---|"]
md += [f"| {n} | {w(p)} | {a} |" for n, p, a in SALLES]
md += ["", "## Pièges de powder snow", "", "2 blocs de fond, 1 bloc sur la marche de sortie (côté est).", ""]
md += [f"- Piège {i + 1} : {w(p)}" for i, p in enumerate(PIEGES)]
md += ["", "## Apparitions (10, sur le chemin extérieur)", ""] + [f"- {i + 1} : {w(p)}" for i, p in enumerate(APPARITIONS)]
md += ["", "## Ce qui a été construit", "",
       f"- Enceinte Ø{SX} (centre entre 4 blocs), rempart de glacier de 4,5 blocs d'épaisseur et de 15 à 19 blocs de haut, bandes de glace, neige et pierre claire, créneaux, stalactites, contreforts, congères au pied.",
       "- 4 portes monumentales : arche de glace, 2 pics géants, herse en barreaux, soul lanterns.",
       "- Centre : lac gelé à fissures de neige, îlot, trône de glace, aiguilles, colonnes prises dans la glace, épave de sapin.",
       "- 4 tours : pics creux à escalier (NE, NO), donjon d'ardoise (SE), tour givrée en ruine (SO).",
       f"- 4 labyrinthes à cœur solide, 5 blocs de haut ; {n_pic} pics, {n_col} colonnes debout, brisées ou tombées, congères, rochers, sapins morts.",
       "- 2 crevasses avec ponts de glace, plateformes de glace bleue et poutre étroite ; glacier à cascade gelée ; ruines enfouies ; grotte de glace à stalactites et piliers.",
       f"- Parcours de {NCH} pics jusqu'à un refuge perché ; {len(SALLES)} salles cachées ; {len(PIEGES)} pièges.",
       "", "## Écarts par rapport au prompt", "",
       "- Construction par structure (.nbt + /place) au lieu de /fill en jeu : les 7 phases sont dans le script, chacune vérifiée.",
       "- Aucune eau liquide : le lac et les cascades sont en glace compacte et bleue, ce qui évite qu'elle gèle ou coule.",
       "- Portes fermées par la herse et un bouchon de glace : personne ne sort. Une paroi invisible (blocs barrière) au-dessus du rempart ferme aussi le haut.",
       "- Salle 4 : la neige couvre la trappe, il faut donc la casser pour ouvrir ; en mode aventure, cette salle est fermée. ",
       "- Les couches de neige ne tiennent pas sur la glace compacte ou bleue : elles sont seulement sur neige et pierre.",
       f"- Éclairage : lanternes de mer, froglights et soul lanterns, complétés par {total_lum} blocs de lumière invisibles de niveau 11. La neige fond au-dessus de 11 : ces blocs ne la font pas fondre, et la règle des 3 blocs ne s'applique donc qu'aux vraies sources, où les couches ont été retirées.",
       ]
LISTE_SALLES.write_text("\n".join(md) + "\n", encoding="utf-8")

from PIL import Image, ImageDraw
COUL = {"snow_block": (240, 248, 252), "snow": (245, 250, 253), "packed_ice": (150, 190, 240), "blue_ice": (90, 150, 240), "calcite": (222, 222, 216),
        "polished_diorite": (195, 195, 195), "quartz_block": (235, 231, 224), "quartz_pillar": (236, 232, 226), "smooth_quartz": (238, 234, 228),
        "quartz_bricks": (230, 226, 218), "white_concrete": (214, 218, 220), "light_blue_concrete": (70, 170, 220), "light_blue_stained_glass": (120, 190, 240),
        "stone_bricks": (125, 125, 125), "deepslate_bricks": (72, 72, 76), "cracked_deepslate_bricks": (66, 66, 70), "deepslate_tiles": (58, 58, 62),
        "polished_deepslate": (76, 76, 80), "cobbled_deepslate": (80, 80, 84), "tuff": (108, 108, 98), "tuff_bricks": (100, 100, 92),
        "prismarine": (90, 165, 150), "dark_prismarine": (50, 95, 80), "spruce_log": (60, 42, 25), "stripped_spruce_log": (120, 90, 55),
        "spruce_planks": (110, 80, 50), "sea_lantern": (170, 235, 235), "pearlescent_froglight": (235, 215, 235), "iron_bars": (150, 150, 150)}
VOILE = {"air", "light", "barrier", "ladder", "soul_lantern", "chain", "spruce_trapdoor"}
E = 4
img = Image.new("RGB", (SX * E + 20, SZ * E + 44), (24, 30, 44))
dr = ImageDraw.Draw(img)
hauts = {}
for (x, y, z), s in blocs.items():
    n = s[0].split(":")[1]
    if n in VOILE or y > SOL + 24:
        continue
    if y > hauts.get((x, z), (-99, None))[0]:
        hauts[(x, z)] = (y, n)
for (x, z), (y, n) in hauts.items():
    c = COUL.get(n, (170, 170, 170))
    k = 0.6 if y < SOL - 2 else 0.8 + 0.2 * min(1, max(0, (y - SOL) / 20))
    dr.rectangle([10 + x * E, 34 + z * E, 10 + x * E + E - 1, 34 + z * E + E - 1], fill=tuple(int(v * k) for v in c))
for x, y, z in PIEGES:
    dr.rectangle([10 + x * E - 8, 34 + z * E - 8, 10 + x * E + 8, 34 + z * E + 8], outline=(255, 170, 30), width=3)
for num, (x, y, z), _ in SALLES:
    dr.ellipse([10 + x * E - 9, 34 + z * E - 9, 10 + x * E + 9, 34 + z * E + 9], outline=(235, 30, 90), width=3)
    dr.text((10 + x * E + 11, 34 + z * E - 6), str(num), fill=(255, 60, 110))
dr.text((10, 10), "ARENE GLACE - vue de dessus (nord en haut). Cercles = salles cachees, carres orange = pieges", fill=(220, 235, 255))
img.save(PLAN)

CACHE = VOILE | {"snow"}
s_ = 3
Wd = (SX + SZ) * s_ + 40
Hh = (SX + SZ) * s_ // 2 + SY * s_ + 60
img = Image.new("RGB", (Wd, Hh), (120, 150, 185))
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

print(f"Arene ecrite : {len(liste)} blocs, palette {len(palette)}, taille {SX}x{SY}x{SZ}, pics {n_pic}, colonnes {n_col}, decor {n_bois}, pieges {len(PIEGES)}, monde present : {MONDE_OK}")
for num, p, acces in SALLES:
    print(f"  salle {num}: {monde(*p)}")
print(f"POSE : /tp @s {CENTRE_MONDE[0]} {ARGS.sol + 66} {CENTRE_MONDE[1]}   puis   /place template atelier:{NOM} {ORIGINE[0]} {ORIGINE[1]} {ORIGINE[2]}")
