# [claude-build]
"""
quatre_vents.py - Arene PvP "Le Temple des Quatre Vents" (theme Air), cercle de 252 blocs, sol plat.
Quatre quartiers : Blizzard (N), Brise (E), Sirocco (S), Orage (O), une fleche centrale, un anneau en hauteur.
  .venv\\Scripts\\python.exe sources\\quatre_vents.py --monde "Arene Air" --centre 0 0 --sol 86
Controle : .venv\\Scripts\\python.exe sources\\verif_acces.py exports\\quatre_vents.nbt exports\\quatre_vents_cibles.json
"""
import argparse
import json
import math
import random
from collections import deque
from pathlib import Path

import nbtlib
from nbtlib import Compound, Int, List, String

ap = argparse.ArgumentParser()
ap.add_argument("--monde", default="Arene Air")
ap.add_argument("--centre", nargs=2, type=int, default=[0, 0], metavar=("X", "Z"))
ap.add_argument("--sol", type=int, default=86)
ap.add_argument("--sous-sol", default="calcite")
ap.add_argument("--surface", default="calcite")
ARGS = ap.parse_args()

NOM = "quatre_vents"
RACINE = Path(__file__).resolve().parent.parent
SAVE = RACINE / "launcher/instances/Build-1.21.1/minecraft/saves" / ARGS.monde
if not (SAVE / "level.dat").exists():
    raise SystemExit(f"monde introuvable : {SAVE}")
DATAPACK = SAVE / "datapacks/atelier"
SORTIES = [RACINE / "exports" / f"{NOM}.nbt", DATAPACK / "data/atelier/structure" / f"{NOM}.nbt"]
FONCTION = DATAPACK / "data/atelier/function" / f"{NOM}_effacer.mcfunction"
PLAN = RACINE / "exports" / f"{NOM}_plan.png"
LISTE_SALLES = RACINE / "salles_cachees_air.md"
CIBLES = RACINE / "exports" / f"{NOM}_cibles.json"

DATA_VERSION = 3955
SX = SZ = 252
SY = 76
SOL = 12
C = 126
CEN = R = 126.0
A = SOL + 24                     # anneau celeste et balcon de la fleche
D_TOP = SOL + 54                 # sommet de la fleche
H_MUR = SOL + 14
H_PYL = SOL + 26
R_MUR = 121.5
R_PERIM = 114.0
R_ANN = 75
Y_SOUS = SOL - 7
CENTRE_MONDE = tuple(ARGS.centre)
ORIGINE = (CENTRE_MONDE[0] - C, ARGS.sol - SOL, CENTRE_MONDE[1] - C)
rng = random.Random(44444)


# ====================================================================== outils
def S(nom_, **p):
    return ("minecraft:" + nom_, tuple(sorted((k, str(v).lower() if isinstance(v, bool) else str(v)) for k, v in p.items())))


AIR = S("air")
blocs = {}


def put(x, y, z, s):
    if not (0 <= x < SX and 0 <= y < SY and 0 <= z < SZ):
        raise ValueError(f"hors gabarit {(x, y, z)} {s[0]}")
    blocs[(x, y, z)] = s


def nom(p):
    s = blocs.get(p)
    return None if s is None else s[0].split(":")[1]


def vide(p):
    return nom(p) in (None, "air")


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


UNITE = {0: (1, 0), 90: (0, 1), 180: (-1, 0), 270: (0, -1)}
FACE = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}
FACE_ECH = {(-1, 0): "east", (1, 0): "west", (0, -1): "south", (0, 1): "north"}

# ====================================================================== blocs
CALC = S("calcite")
QB = S("quartz_bricks")
QL = S("smooth_quartz")
QP = S("quartz_pillar", axis="y")
CUIV = S("oxidized_cut_copper")
CUIV_C = S("oxidized_chiseled_copper")
VERRE = S("light_blue_stained_glass")
VERRE_B = S("white_stained_glass")
LAINE = S("white_wool")
LUM = S("sea_lantern")
SOUL = S("soul_sand")
BULLE = S("bubble_column", drag=False)
TRAPPE = S("birch_trapdoor", facing="north", half="top", open=False, powered=False, waterlogged=False)
LANT = S("lantern", hanging=False, waterlogged=False)
PARATON = S("lightning_rod", facing="up", powered=False, waterlogged=False)
GLACE_B = S("blue_ice")
GLACE = S("packed_ice")
NEIGE = S("snow_block")
CERISIER = S("cherry_planks")
BUCHE = S("cherry_log", axis="y")
FEUILLES = S("cherry_leaves", distance=1, persistent=True, waterlogged=False)
PETALES = S("pink_petals", facing="north", flower_amount=4)
GRES = S("cut_red_sandstone")
GRES_L = S("smooth_red_sandstone")
ARDOISE = S("deepslate_bricks")

T_AVENUE = [(50, QL), (30, QB), (20, S("polished_diorite"))]
T_PERIM = [(60, S("polished_andesite")), (40, S("polished_diorite"))]
T_NUAGE = [(100, LAINE)]
T_SALLE = [(50, QB), (25, QL), (15, CUIV), (10, S("oxidized_copper"))]
QUARTIERS = {
    "E": dict(g=0, nom="Brise", sol=[(75, S("grass_block", snowy=False)), (25, S("moss_block"))],
              mur=[(50, QB), (25, CERISIER), (25, CALC)], acc=CERISIER, roche=[(60, CALC), (40, S("moss_block"))]),
    "S": dict(g=90, nom="Sirocco", sol=[(40, S("red_sandstone")), (30, GRES_L), (30, S("red_sand"))],
              mur=[(50, GRES), (30, GRES_L), (20, S("terracotta"))], acc=S("gold_block"),
              roche=[(60, S("red_sandstone")), (40, GRES_L)]),
    "O": dict(g=180, nom="Orage", sol=[(35, S("deepslate_tiles")), (35, S("cobbled_deepslate")), (30, S("tuff"))],
              mur=[(50, ARDOISE), (30, S("polished_deepslate")), (20, S("tuff_bricks"))], acc=CUIV,
              roche=[(45, S("cobbled_deepslate")), (35, S("tuff")), (20, S("deepslate_tiles"))]),
    "N": dict(g=270, nom="Blizzard", sol=[(55, NEIGE), (30, GLACE), (15, GLACE_B)],
              mur=[(50, CALC), (30, GLACE), (20, QB)], acc=GLACE_B, roche=[(60, GLACE), (40, NEIGE)]),
}
PORTES = {q["g"]: k for k, q in QUARTIERS.items()}

NON_SOLIDES = {"air", "bubble_column", "ladder", "birch_trapdoor", "birch_door", "lantern", "lightning_rod",
               "pink_petals", "powder_snow"}
INTERDITS = {"chest", "trapped_chest", "barrel", "hopper", "dropper", "dispenser", "furnace", "blast_furnace",
             "smoker", "spawner", "ender_chest", "lava", "fire", "soul_fire"}

SALLES, ECHELLES, APPARITIONS = [], [], []
CIBLES_ACCES = {}
occ = {}


def quart(x, z):
    a = ang(x, z)
    return "E" if a >= 315 or a < 45 else "S" if a < 135 else "O" if a < 225 else "N"


def reserver(cx, cz, r, role="reserve"):
    for c in disque(cx + 0.5, cz + 0.5, r):
        occ[c] = role


def echelle(x, z, y0, y1, sup, trappes=()):
    for y in range(y0, y1 + 1):
        if y in trappes:
            put(x, y, z, TRAPPE)
            continue
        put(x, y, z, S("ladder", facing=FACE_ECH[sup], waterlogged=False))
        sp = (x + sup[0], y, z + sup[1])
        ECHELLES.append(((x, y, z), sp))
        if nom(sp) is None or nom(sp) in NON_SOLIDES:
            put(*sp, QB)


def porte(x, y, z, sens):
    for k, half in ((0, "lower"), (1, "upper")):
        put(x, y + k, z, S("birch_door", facing=FACE[sens], half=half, hinge="left", open=True, powered=False))


def salle(num, x0, x1, z0, z1, yf, h, coffre, acces, style=T_SALLE, garder=()):
    garder = set(garder)
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            for y in range(yf, yf + h + 2):
                dedans = x0 <= x <= x1 and z0 <= z <= z1
                if dedans and yf < y <= yf + h:
                    put(x, y, z, AIR)
                elif dedans and y == yf:
                    put(x, y, z, CUIV if (x + z) % 2 else QL)
                else:
                    put(x, y, z, choix(style, h01(x, y, z, 77 + num)))
    cx, cz = coffre
    put(cx, yf, cz, CUIV_C)
    for x, z in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
        if (x, z) not in garder and math.hypot(x - cx, z - cz) > 1.5:
            put(x, yf + 1, z, LANT)
    put((x0 + x1) // 2, yf + h + 1, (z0 + z1) // 2, LUM)
    SALLES.append((num, (cx, yf + 1, cz), acces))


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
        if n in INTERDITS or n.endswith("shulker_box"):
            err.append(f"bloc interdit {n} en {p}")
        if n != "air" and dist(x, z) > R + 1e-9:
            err.append(f"depasse l'enceinte : {n} en {p}")
        if n == "bubble_column":
            if nom((x, y - 1, z)) not in ("bubble_column", "soul_sand"):
                err.append(f"colonne sans base en {p} ({nom((x, y - 1, z))})")
            for q in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1)):
                if nom(q) not in ("bubble_column", "birch_door") and not solide(q):
                    err.append(f"eau non contenue {p} -> {q} ({nom(q)})")
        elif n == "birch_door":
            autre = (x, y + 1, z) if dict(s[1])["half"] == "lower" else (x, y - 1, z)
            if nom(autre) != "birch_door":
                err.append(f"porte incomplete en {p}")
        elif n == "lantern" and not solide((x, y - 1, z)):
            err.append(f"lanterne dans le vide en {p}")
        elif n == "pink_petals" and nom((x, y - 1, z)) not in ("grass_block", "moss_block"):
            err.append(f"petales sans terre en {p}")
    for e, sp in ECHELLES:
        if nom(e) == "ladder" and not solide(sp):
            err.append(f"echelle {e} sans support ({nom(sp)})")
    for num, (cx, cy, cz), _ in SALLES:
        if nom((cx, cy, cz)) != "air" or nom((cx, cy + 1, cz)) != "air" or not solide((cx, cy - 1, cz)):
            err.append(f"salle {num} : coffre non degage en {(cx, cy, cz)}")
    print(f"[phase] {phase:<28} blocs={len(blocs):>7}  erreurs={len(err)}")
    if err:
        for e in err[:25]:
            print("   !", e)
        raise SystemExit(1)


# ====================================================================== 1. sol, enceinte, portes
def avenue(x, z, marge=0.0):
    a, b = dxz(x, z)
    return abs(a) <= 4 + marge or abs(b) <= 4 + marge


for x in range(SX):
    for z in range(SZ):
        d = dist(x, z)
        if d >= R:
            continue
        q = QUARTIERS[quart(x, z)]
        if d < R_MUR:
            t = T_PERIM if d >= R_PERIM else T_AVENUE if d < 24 or avenue(x, z) else q["sol"]
            put(x, SOL, z, choix(t, h01(x, 0, z, 1)))
            continue
        a = ang(x, z)
        for y in range(SOL, H_MUR):
            bande = (y - SOL) in (5, 6) and int(a * 3) % 4 == 0
            put(x, y, z, VERRE if bande and d < 123 else choix(q["mur"], h01(x, y, z, 2)))
        put(x, H_MUR, z, QL)
        if d >= 124.5:
            put(x, H_MUR + 1, z, choix(q["mur"], h01(x, 1, z, 3)))
            if int(a * 1.5) % 2 == 0:
                put(x, H_MUR + 2, z, q["acc"])

for g, k in PORTES.items():
    q = QUARTIERS[k]
    for al in range(114, 121):                          # pylones devant le mur : le chemin de ronde reste libre
        for sp in list(range(5, 12)) + list(range(-12, -5)):
            x, z = gl(al, sp, g)
            for y in range(SOL + 1, H_PYL + 1):
                put(x, y, z, q["acc"] if (y - SOL) in (10, 20) else choix(q["mur"], h01(x, y, z, 4)))
            put(x, H_PYL + 1, z, q["acc"])
            occ[(x, z)] = "pylone"
    for sp in range(-4, 4):
        for al in range(118, 126):
            x, z = gl(al, sp, g)
            if dist(x, z) >= R:
                continue
            for y in range(SOL + 1, SOL + 10):
                put(x, y, z, VERRE if al >= 124 else AIR)
            if al < 124:
                put(x, SOL, z, choix(T_AVENUE, h01(x, 0, z, 1)))
    for sp in range(-5, 5):                             # fronton a rosace
        for al in (119, 120):
            x, z = gl(al, sp, g)
            for y in range(SOL + 10, SOL + 23):
                rosace = math.hypot(sp + 0.5, y - (SOL + 16)) < 3.2
                put(x, y, z, VERRE if rosace else q["acc"] if y == SOL + 22 else choix(q["mur"], h01(x, y, z, 5)))
    for s in (13, -14):
        al = 121
        while dist(*gl(al, s, g)) >= R_MUR:
            al -= 1
        x, z = gl(al, s, g)
        echelle(x, z, SOL + 1, H_MUR, UNITE[g])
        occ[(x, z)] = "echelle"
verifier("1. sol, enceinte, portes")

# ====================================================================== 2. la fleche centrale
DEPART = 285                                            # la rampe passe a la hauteur de l'anneau a 45 degres


def rampe(a):
    for k in range(4):
        tot = (a - DEPART) % 360 + 360 * k
        if tot <= 1080:
            yield int(tot // 20)


for x in range(C - 21, C + 21):
    for z in range(C - 21, C + 21):
        d = dist(x, z)
        a = ang(x, z)
        occ[(x, z)] = "fleche"
        if d < 6:
            put(x, SOL, z, LUM if h01(x, 3, z, 6) < 0.08 else CUIV if int(d) % 2 else QL)
        elif d < 8:
            for y in range(SOL, D_TOP):
                e = y - SOL
                fen = e % 9 in (5, 6) and int(a / 15) % 3 == 0
                put(x, y, z, VERRE if fen else CUIV if e % 18 == 0 else choix(T_AVENUE, h01(x, y, z, 7)))
        elif d < 12:
            for h in rampe(a):
                if h > 0:
                    put(x, SOL + h, z, LUM if h01(x, h, z, 8) < 0.03 else QB)
        elif d < 13:
            for h in rampe(a):
                if abs(h - 24) <= 2:
                    put(x, A, z, QL)
                elif h > 0:
                    put(x, SOL + h + 1, z, VERRE_B)
        if 13 <= d < 20:
            put(x, A, z, QL)
            if d >= 19:
                put(x, A + 1, z, VERRE_B)
        if d < 8:
            put(x, D_TOP, z, QL)

for x in range(C - 9, C + 9):                           # arches au sol, aux 4 points cardinaux
    for z in range(C - 9, C + 9):
        d = dist(x, z)
        a, b = dxz(x, z)
        if 5.5 <= d < 8.5 and (abs(a) <= 2 or abs(b) <= 2):
            for y in range(SOL + 1, SOL + 4):
                put(x, y, z, AIR)
            put(x, SOL, z, QL)

for x in range(C - 2, C + 2):                           # colonne de vent centrale
    for z in range(C - 2, C + 2):
        if x in (C - 2, C + 1) or z in (C - 2, C + 1):
            for y in range(SOL + 1, D_TOP):
                put(x, y, z, VERRE)
        else:
            put(x, SOL, z, SOUL)
            for y in range(SOL + 1, D_TOP + 1):
                put(x, y, z, BULLE)
porte(C - 2, SOL + 1, C - 1, (1, 0))
for a in (45, 135, 225, 315):
    x, z = cellule(6.5, a)
    for y in range(D_TOP + 1, D_TOP + 6):
        put(x, y, z, QP)
    put(x, D_TOP + 6, z, PARATON)
CIBLES_ACCES["sommet de la flèche"] = [C - 4, D_TOP + 1, C]
CIBLES_ACCES["balcon de la flèche"] = [C + 16, A + 1, C]
verifier("2. fleche centrale")

# ====================================================================== 3. tours et anneau celeste
TOURS = {
    "N": dict(a=295, forme="rond", ri=7, ro=9, H=A + 16, toit="cone", porte=150),
    "E": dict(a=25, forme="carre", ri=7, ro=8, H=A + 10, toit="pagode", porte=205),
    "S": dict(a=115, forme="octo", ri=7, ro=9, H=A + 8, toit="ruine", porte=295),
    "O": dict(a=205, forme="rond", ri=6, ro=8, H=A + 20, toit="orage", porte=25),
}


def mesure(f, dx, dz):
    a, b = abs(dx), abs(dz)
    if f == "rond":
        return math.hypot(dx, dz)
    if f == "carre":
        return max(a, b)
    return max(a, b, (a + b) / 1.414)


for t in TOURS.values():
    t["c"] = cellule(R_ANN, t["a"])


def bati(x, z):
    if dist(x, z) < 20.5:
        return True
    return any(mesure(t["forme"], x - t["c"][0], z - t["c"][1]) <= t["ro"] + 1 for t in TOURS.values())


def dans_tour(x, z, marge):
    return any(mesure(t["forme"], x - t["c"][0], z - t["c"][1]) <= t["ri"] - marge for t in TOURS.values())


def tour(k, t):
    mx, mz = t["c"]
    f, ri, ro, H = t["forme"], t["ri"], t["ro"], t["H"]
    q = QUARTIERS[k]
    reserver(mx, mz, ro + 6, "tour")
    for x in range(mx - ro - 1, mx + ro + 2):
        for z in range(mz - ro - 1, mz + ro + 2):
            m = mesure(f, x - mx, z - mz)
            if m > ro:
                continue
            if m > ri:
                hm = H - int(8 * bruit(x, z, 4, 11)) if t["toit"] == "ruine" else H
                for y in range(SOL + 1, hm + 1):
                    e = y - SOL
                    fen = e % 9 in (5, 6) and (x + z) % 6 < 2
                    put(x, y, z, VERRE if fen else q["acc"] if e % 18 == 0 else choix(q["mur"], h01(x, y, z, 12)))
            else:
                put(x, SOL, z, QL)
                for y in (A, H):
                    if (x, z) != (mx + 1, mz):
                        put(x, y, z, QL)
    for y in range(SOL + 1, H + 2):
        put(mx, y, mz, QP)
    echelle(mx + 1, mz, SOL + 1, H + 1, (-1, 0))
    ux, uz = math.cos(math.radians(t["porte"])), math.sin(math.radians(t["porte"]))
    for i in range(int((ro + 1.5 - ri + 1) * 2)):       # porte au sol
        r_ = ri - 1 + i * 0.5
        for s in (-1, 0, 1):
            x = math.floor(mx + 0.5 + ux * r_ - uz * s)
            z = math.floor(mz + 0.5 + uz * r_ + ux * s)
            for y in range(SOL + 1, SOL + 5):
                if not vide((x, y, z)):
                    put(x, y, z, AIR)
    if t["toit"] == "cone":
        for k2 in range(1, ro + 1):
            for x in range(mx - ro, mx + ro + 1):
                for z in range(mz - ro, mz + ro + 1):
                    if ro - k2 - 1 < mesure(f, x - mx, z - mz) <= ro - k2 + 0.5:
                        put(x, H + k2, z, GLACE_B if k2 % 2 else GLACE)
    elif t["toit"] == "pagode":
        for n_, y in ((ro + 2, H + 4), (ro, H + 6), (ro - 3, H + 8)):
            for x in range(mx - n_, mx + n_ + 1):
                for z in range(mz - n_, mz + n_ + 1):
                    put(x, y, z, CERISIER)
        for a_, b_ in ((-ri, -ri), (-ri, ri), (ri, -ri), (ri, ri)):
            for y in range(H + 1, H + 4):
                put(mx + a_, y, mz + b_, BUCHE)
    elif t["toit"] == "orage":
        for x in range(mx - ro, mx + ro + 1):
            for z in range(mz - ro, mz + ro + 1):
                if mesure(f, x - mx, z - mz) > ri and (x + z) % 2 == 0:
                    put(x, H + 1, z, CUIV)
                    if (x + z) % 4 == 0:
                        put(x, H + 2, z, PARATON)
    CIBLES_ACCES[f"sommet de la tour {k}"] = [mx - 3, H + 1, mz]


for k, t in TOURS.items():
    tour(k, t)

# toboggan de glace autour de la tour N (720 degres, de l'anneau jusqu'au sol)
t = TOURS["N"]
mx, mz = t["c"]
for x in range(mx - 15, mx + 16):
    for z in range(mz - 15, mz + 16):
        m = math.hypot(x - mx, z - mz)
        la = math.degrees(math.atan2(z - mz, x - mx)) % 360
        if t["ro"] < m <= t["ro"] + 3.5 or t["ro"] + 3.5 < m <= t["ro"] + 4.5:
            for k2 in range(2):
                tot = (la - 25) % 360 + 360 * k2
                h = int(tot // 30)
                if h <= 0:
                    continue
                if m <= t["ro"] + 3.5:
                    put(x, SOL + h, z, GLACE_B)
                else:
                    put(x, SOL + h + 1, z, VERRE_B)


def pont(r0, r1, a, y):
    ax, az = point(r0, a)
    ux, uz = math.cos(math.radians(a)), math.sin(math.radians(a))
    L = r1 - r0
    for x in range(SX):
        for z in range(SZ):
            px, pz = x + 0.5 - ax, z + 0.5 - az
            t_ = px * ux + pz * uz
            s = abs(-px * uz + pz * ux)
            if t_ < 0 or t_ > L or s > 2.6:
                continue
            if s <= 1.6:
                put(x, y, z, choix(T_AVENUE, h01(x, y, z, 13)))
                for k2 in (1, 2, 3):
                    if not vide((x, y + k2, z)):
                        put(x, y + k2, z, AIR)
            elif not bati(x, z) and vide((x, y, z)) and nom((x, y + 1, z)) is None:
                put(x, y + 1, z, VERRE_B)


def arc(a0, a1, y):
    for x in range(SX):
        for z in range(SZ):
            d = dist(x, z)
            if not R_ANN - 2.5 <= d <= R_ANN + 2.5 or (ang(x, z) - a0) % 360 > a1 - a0:
                continue
            if R_ANN - 1.5 <= d <= R_ANN + 1.5:
                if dans_tour(x, z, 1):
                    continue
                put(x, y, z, choix(T_AVENUE, h01(x, y, z, 14)))
                for k2 in (1, 2, 3):
                    if not vide((x, y + k2, z)):
                        put(x, y + k2, z, AIR)
            elif not bati(x, z) and nom((x, y + 1, z)) is None:
                put(x, y + 1, z, VERRE_B)


def pile(r, a, y):
    px, pz = point(r, a)
    for x, z in disque(px, pz, 1.6):
        occ[(x, z)] = "pile"
        for yy in range(SOL + 1, y):
            put(x, yy, z, QP if (x + z) % 2 else QB)


arc(2, 172, A)
arc(188, 348, A)
for a in (45, 135, 225, 315):
    pont(18, R_ANN - 1, a, A)
    pile(36, a, A)
    pile(55, a, A)
for a in (10, 80, 100, 170, 190, 260, 280, 350):
    pile(R_ANN, a, A)
for a in (-9.5, -5.5, -1.5, 176, 180, 184):             # plots suspendus : anneau casse a l'Est et a l'Ouest
    px, pz = point(R_ANN, a)
    for x, z in disque(px, pz, 1.6):
        put(x, A, z, S("snow_block"))
CIBLES_ACCES["anneau céleste"] = list(cellule(R_ANN, 90)[:1]) + [A + 1] + list(cellule(R_ANN, 90)[1:])
verifier("3. tours et anneau")

# ====================================================================== 4. courants ascendants
COLONNES = {}
for a in (60, 150, 240, 330):
    tx, tz = cellule(R_ANN, a)
    ux, uz = C - tx, C - tz
    u = ((1 if ux > 0 else -1), 0) if abs(ux) >= abs(uz) else (0, (1 if uz > 0 else -1))
    COLONNES[a] = (tx, tz, u)
    reserver(tx, tz, 5, "colonne")
    for i in (-1, 0, 1):
        for j in (-1, 0, 1):
            if i or j:
                for y in range(SOL + 1, A):
                    put(tx + i, y, tz + j, VERRE)
                put(tx + i, A, tz + j, QL)
    put(tx, SOL, tz, SOUL)
    for y in range(SOL + 1, A + 1):
        put(tx, y, tz, BULLE)
    porte(tx + u[0], SOL + 1, tz + u[1], u)
verifier("4. courants ascendants")

# ====================================================================== 5. labyrinthes (Blizzard et Sirocco)
LABY = {}
T_LAB = {"N": [(50, GLACE), (30, NEIGE), (20, GLACE_B)],
         "S": [(40, GRES), (30, GRES_L), (20, S("terracotta")), (10, S("red_sandstone"))]}


def labyrinthe(cle, k, cote):
    g = QUARTIERS[k]["g"]

    def dedans(x, z):
        a, b = dxz(x, z)
        perp = a if g in (90, 270) else b
        return 86 <= dist(x, z) <= 110 and adiff(ang(x, z), g) < 42 and cote * perp >= 7

    cases = set()
    for i in range(63):
        for j in range(63):
            x0, z0 = 4 * i, 4 * j
            if all(dedans(x, z) for x, z in ((x0, z0), (x0 + 4, z0), (x0, z0 + 4), (x0 + 4, z0 + 4))):
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
            if c in cases and rng.random() < 0.09:
                passages.add(frozenset(((i, j), c)))
    murs = set()
    for i, j in cases:
        for a in range(5):
            for b in range(5):
                x, z = 4 * i + a, 4 * j + b
                occ[(x, z)] = "laby"
                if a in (0, 4) or b in (0, 4):
                    murs.add((x, z))

    def trou(c, d):
        i, j = c
        for n_ in (1, 2, 3):
            if d == (1, 0):
                murs.discard((4 * i + 4, 4 * j + n_))
            elif d == (-1, 0):
                murs.discard((4 * i, 4 * j + n_))
            elif d == (0, 1):
                murs.discard((4 * i + n_, 4 * j + 4))
            else:
                murs.discard((4 * i + n_, 4 * j))

    for p in passages:
        a, b = sorted(p)
        trou(a, (b[0] - a[0], b[1] - a[1]))
    cand_in, cand_out = [], []
    for c in sorted(cases):
        for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (c[0] + d[0], c[1] + d[1]) in cases:
                continue
            r0 = dist(4 * c[0] + 2, 4 * c[1] + 2)
            r1 = dist(4 * c[0] + 2 + 4 * d[0], 4 * c[1] + 2 + 4 * d[1])
            (cand_in if r1 < r0 else cand_out).append((ang(4 * c[0] + 2, 4 * c[1] + 2), c, d))
    entrees = set()
    for liste_ in (cand_in, cand_out):
        liste_.sort()
        for n_ in range(2):
            _, c, d = liste_[(2 * n_ + 1) * len(liste_) // 4]
            trou(c, d)
            entrees.add(c)
    for x, z in murs:
        for y in range(SOL + 1, SOL + 5):
            put(x, y, z, choix(T_LAB[k], h01(x, y, z, 31)))
    if k == "N":
        for i, j in sorted(cases):
            if rng.random() < 0.08 and (i, j) not in entrees:
                put(4 * i + 2, SOL, 4 * j + 2, S("powder_snow"))
    LABY[cle] = dict(cases=cases, passages=passages, entrees=entrees)


for cle, k, cote in (("N+", "N", 1), ("N-", "N", -1), ("S+", "S", 1), ("S-", "S", -1)):
    labyrinthe(cle, k, cote)
verifier("5. labyrinthes")

# ====================================================================== 6. salles cachees (2 par quartier)
# 1 : Blizzard, trappe au fond d'un cul-de-sac
L = LABY["N+"]
impasses = [c for c in L["cases"] if sum(c in p for p in L["passages"]) == 1 and c not in L["entrees"]]
ci, cj = min(impasses, key=lambda c: abs(dist(4 * c[0] + 2, 4 * c[1] + 2) - 98))
tx, tz = 4 * ci + 2, 4 * cj + 2
put(tx, SOL, tz, QL)
salle(1, tx, tx + 6, tz - 3, tz + 3, Y_SOUS, 4, (tx + 6, tz), "Blizzard : trappe au fond d'un cul-de-sac du labyrinthe de glace",
      garder=[(tx, tz)])
echelle(tx, tz, Y_SOUS + 1, SOL, (-1, 0), trappes=(SOL,))

# 2 : Blizzard, sous le cone de la tour
t = TOURS["N"]
mx, mz = t["c"]
for x, z in ((mx - 5, mz - 2), (mx - 5, mz + 2), (mx + 4, mz + 3), (mx + 4, mz - 3)):
    put(x, t["H"] + 1, z, LANT)
put(mx - 5, t["H"], mz, CUIV_C)
put(mx - 5, t["H"] + 1, mz, AIR)
put(mx - 5, t["H"] + 2, mz, AIR)
SALLES.append((2, (mx - 5, t["H"] + 1, mz), "Blizzard : tout en haut de l'échelle de la tour, sous le cône de glace"))

# 3 : Brise, trappe dans le plancher de la pagode
px, pz = cellule(55, 340)
reserver(px, pz, 11, "pagode")
for x in range(px - 6, px + 7):
    for z in range(pz - 6, pz + 7):
        put(x, SOL, z, CERISIER)
for a_, b_ in ((-5, -5), (-5, 5), (5, -5), (5, 5)):
    for y in range(SOL + 1, SOL + 7):
        put(px + a_, y, pz + b_, BUCHE)
for n_, y in ((7, SOL + 7), (5, SOL + 9), (3, SOL + 11), (1, SOL + 13)):
    for x in range(px - n_, px + n_ + 1):
        for z in range(pz - n_, pz + n_ + 1):
            put(x, y, z, CERISIER if abs(x - px) == n_ or abs(z - pz) == n_ else FEUILLES)
salle(3, px, px + 6, pz - 3, pz + 3, Y_SOUS, 4, (px + 6, pz), "Brise : trappe dans le plancher de la pagode", garder=[(px, pz)])
echelle(px, pz, Y_SOUS + 1, SOL, (-1, 0), trappes=(SOL,))

# 4 : Brise, nuage au bout de plots partant de l'anneau
for r_ in (79.5, 84.5, 89.5):
    a_, b_ = point(r_, -20)
    for x, z in disque(a_, b_, 1.6):
        put(x, A, z, NEIGE)
a_, b_ = point(R_ANN + 2, -20)
for x, z in disque(a_, b_, 2.2):
    if not vide((x, A + 1, z)):
        put(x, A + 1, z, AIR)
cx, cz = cellule(99, -20)
salle(4, cx - 3, cx + 3, cz - 3, cz + 3, A, 4, (cx + 3, cz), "Brise : plots suspendus partant de l'anneau, jusqu'au nuage",
      style=T_NUAGE)
for z in (cz - 1, cz, cz + 1):
    put(cx - 4, A, z, QL)
    for y in range(A + 1, A + 4):
        put(cx - 4, y, z, AIR)
for x, z in disque(cx - 6.5, cz + 0.5, 2.2):
    put(x, A, z, NEIGE)

# 5 : Sirocco, chicane dans le pylone de la porte Sud
g = 90
xs = [gl(al, sp, g)[0] for al in (114, 118) for sp in (7, 11)]
zs = [gl(al, sp, g)[1] for al in (114, 118) for sp in (7, 11)]
c5 = gl(114, 9, g)
salle(5, min(xs), max(xs), min(zs), max(zs), Y_SOUS, 4, c5, "Sirocco : fente dans le pylône de la porte Sud, échelle au bout du couloir")
for al, sp in ((114, 6), (115, 6), (116, 6), (116, 7), (116, 8), (116, 9), (117, 9), (118, 9)):
    x, z = gl(al, sp, g)
    for y in range(SOL + 1, SOL + 4):
        put(x, y, z, AIR)
    put(x, SOL, z, QL)
x, z = gl(118, 9, g)
echelle(x, z, Y_SOUS + 1, SOL, UNITE[g])

# 6 : Sirocco, puits dans une ruine a moitie enterree
px, pz = cellule(55, 70)
reserver(px, pz, 9, "ruine")
for x in range(px - 5, px + 6):
    for z in range(pz - 5, pz + 6):
        if abs(x - px) == 5 or abs(z - pz) == 5:
            h = 2 + int(4 * h01(x, 0, z, 50))
            if z == pz - 5 and abs(x - px) <= 1:
                continue
            for y in range(SOL + 1, SOL + 1 + h):
                put(x, y, z, choix(QUARTIERS["S"]["mur"], h01(x, y, z, 51)))
        else:
            put(x, SOL, z, GRES)
salle(6, px, px + 6, pz - 3, pz + 3, Y_SOUS, 4, (px + 6, pz), "Sirocco : puits ouvert dans la ruine à moitié enterrée", garder=[(px, pz)])
echelle(px, pz, Y_SOUS + 1, SOL, (-1, 0))

# 7 : Orage, dans le massif rocheux, couloir puis echelle
px, pz = cellule(55, 165)
reserver(px, pz, 14, "massif")
for x, z in disque(px + 0.5, pz + 0.5, 11):
    d = math.hypot(x - px, z - pz)
    h = int(24 * (1 - d / 11.5) * (0.8 + 0.4 * bruit(x, z, 4, 60)))
    for y in range(SOL + 1, SOL + 1 + h):
        put(x, y, z, choix(QUARTIERS["O"]["roche"], h01(x, y, z, 61)))
for x in range(px - 13, px - 1):
    for y in range(SOL + 1, SOL + 4):
        put(x, y, pz, AIR)
for z in (pz + 1, pz + 2):
    for y in range(SOL + 1, SOL + 4):
        put(px - 2, y, z, AIR)
salle(7, px - 3, px + 2, pz - 2, pz + 3, SOL + 10, 4, (px + 2, pz), "Orage : fente à l'ouest du massif rocheux, couloir puis échelle dans la roche",
      garder=[(px - 2, pz + 2)])
echelle(px - 2, pz + 2, SOL + 1, SOL + 11, (0, 1))

# 8 : Orage, porte a mi-hauteur d'un courant ascendant
tx, tz, u = COLONNES[150]
salle(8, tx + 2, tx + 6, tz - 2, tz + 2, SOL + 9, 4, (tx + 6, tz),
      "Orage : porte à mi-hauteur du courant ascendant, côté centre (il faut se diriger vers elle en montant)")
porte(tx + 1, SOL + 10, tz, (1, 0))
verifier("6. salles cachees")

# ====================================================================== 7. decoration
def libre(x, z, marge=0):
    if not 24 <= dist(x, z) <= 112 or avenue(x, z, 2):
        return False
    for a in range(-marge, marge + 1):
        for b in range(-marge, marge + 1):
            if (x + a, z + b) in occ or not vide((x + a, SOL + 1, z + b)):
                return False
    return True


def cone(px, pz, r, h, table, g):
    for x, z in disque(px + 0.5, pz + 0.5, r):
        d = math.hypot(x - px, z - pz)
        hh = int(h * (1 - d / (r + 0.5)) * (0.7 + 0.6 * bruit(x, z, 3, g)))
        for y in range(SOL + 1, SOL + 1 + hh):
            put(x, y, z, choix(table, h01(x, y, z, g)))


def cerisier(px, pz):
    h = rng.randint(5, 8)
    for y in range(SOL + 1, SOL + 1 + h):
        put(px, y, pz, BUCHE)
    for x, z in disque(px + 0.5, pz + 0.5, 4.5):
        for k2 in range(3):
            if math.hypot(x - px, z - pz) <= 4.5 - k2 * 1.3 and vide((x, SOL + h + k2, z)):
                put(x, SOL + h + k2, z, FEUILLES)


poses = {}
for _ in range(2500):
    x, z = cellule(rng.uniform(26, 110), rng.uniform(0, 360))
    k = quart(x, z)
    n = poses.setdefault(k, [])
    if len(n) >= 20 or not libre(x, z, 4) or any(math.hypot(x - a, z - b) < 10 for a, b in n):
        continue
    n.append((x, z))
    if k == "N":
        cone(x, z, rng.uniform(2.5, 4), rng.randint(7, 15), QUARTIERS["N"]["roche"], 70)
    elif k == "E":
        cerisier(x, z)
    elif k == "O":
        cone(x, z, rng.uniform(3, 5), rng.randint(8, 18), QUARTIERS["O"]["roche"], 71)
        y = SOL + 1
        while not vide((x, y, z)):
            y += 1
        put(x, y, z, PARATON)
    else:
        h = rng.randint(3, 11)
        for y in range(SOL + 1, SOL + 1 + h):
            put(x, y, z, GRES if y % 3 else S("gold_block") if h > 8 and y == SOL + h - 1 else GRES_L)

# relief doux et petales sur les cases restees libres
for x in range(SX):
    for z in range(SZ):
        if not libre(x, z):
            continue
        k = quart(x, z)
        q = QUARTIERS[k]
        n = bruit(x, z, 13, 80)
        h = int((n - 0.55) * 14) if n > 0.55 else 0
        for y in range(SOL + 1, SOL + 1 + h):
            put(x, y, z, choix(q["sol"], h01(x, y, z, 81)))
        if k == "E" and h01(x, 0, z, 82) < 0.16:
            haut = SOL + h
            if nom((x, haut, z)) in ("grass_block", "moss_block"):
                put(x, haut + 1, z, PETALES)

for g in PORTES:
    for al in range(28, 112, 12):
        for sp in (-5, 4):
            x, z = gl(al, sp, g)
            if vide((x, SOL + 1, z)) and vide((x, SOL + 2, z)) and (x, z) not in occ:
                for y in range(SOL + 1, SOL + 4):
                    put(x, y, z, QP)
                put(x, SOL + 4, z, LUM)

for i in range(10):
    a = 9 + 36 * i
    x, z = cellule(117.5, a)
    for u_, v in disque(x + 0.5, z + 0.5, 1.6):
        put(u_, SOL, v, CUIV)
    put(x, SOL, z, CUIV_C)
    APPARITIONS.append((x, SOL + 1, z))
verifier("7. decoration")

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
    liste.append(Compound({"pos": List[Int]([Int(x), Int(y), Int(z)]), "state": Int(index[s])}))
racine = nbtlib.File({"DataVersion": Int(DATA_VERSION), "size": List[Int]([Int(SX), Int(SY), Int(SZ)]),
                      "palette": List[Compound](palette), "blocks": List[Compound](liste),
                      "entities": List[Compound]([])}, gzipped=True)
for chemin in SORTIES:
    chemin.parent.mkdir(parents=True, exist_ok=True)
    racine.save(chemin)

ox, oy, oz = ORIGINE
lignes = [f"# [claude-build] Aplanit l'emprise de {NOM}. 2 /fill par couche (< 32768 blocs). Se placer pres du centre."]
for y in range(oy, oy + SY):
    bloc = (f"minecraft:{ARGS.surface}" if y == oy + SOL else f"minecraft:{ARGS.sous_sol}" if y < oy + SOL else "minecraft:air")
    lignes.append(f"fill {ox} {y} {oz} {ox + SX // 2 - 1} {y} {oz + SZ - 1} {bloc}")
    lignes.append(f"fill {ox + SX // 2} {y} {oz} {ox + SX - 1} {y} {oz + SZ - 1} {bloc}")
lignes.append(f'tellraw @s {{"text":"Arene {NOM} effacee","color":"aqua"}}')
FONCTION.parent.mkdir(parents=True, exist_ok=True)
FONCTION.write_text("\n".join(lignes) + "\n", encoding="utf-8")
mcmeta = DATAPACK / "pack.mcmeta"
if not mcmeta.exists():
    mcmeta.write_text('{"pack": {"pack_format": 48, "description": "Atelier [claude-build]"}}\n', encoding="utf-8")

SALLES.sort()
depart = gl(100, 0, 0)
xw, zw = gl(118, 16, 0)
while dist(xw, zw) < 123:
    xw += 1
CIBLES_ACCES["chemin de ronde"] = [xw, H_MUR + 1, zw]
CIBLES_ACCES.update({f"salle {n}": list(p) for n, p, _ in SALLES})
CIBLES_ACCES.update({f"apparition {i + 1}": list(p) for i, p in enumerate(APPARITIONS)})
CIBLES.write_text(json.dumps({"_marque": "[claude-build]", "sol": SOL, "depart": [depart[0], SOL + 1, depart[1]],
                              "cibles": CIBLES_ACCES}, ensure_ascii=False, indent=1), encoding="utf-8")

md = ["<!-- [claude-build] genere par sources/quatre_vents.py -->", "# Salles cachées — Le Temple des Quatre Vents", "",
      f"Monde `saves/{ARGS.monde}`, arène centrée en X={CENTRE_MONDE[0]} Z={CENTRE_MONDE[1]}, sol à Y={ARGS.sol}. "
      "Coordonnées = bloc où poser le coffre (case vide).", "", "| N° | Coffre (X Y Z) | Accès |", "|---|---|---|"]
for num, p, acces in SALLES:
    md.append(f"| {num} | {' '.join(str(v) for v in monde(*p))} | {acces} |")
md += ["", "## Apparitions (10)", ""] + [f"- {i + 1} : {' '.join(str(v) for v in monde(*p))}" for i, p in enumerate(APPARITIONS)]
LISTE_SALLES.write_text("\n".join(md) + "\n", encoding="utf-8")

from PIL import Image, ImageDraw
COUL = {"calcite": (225, 225, 220), "smooth_quartz": (240, 236, 230), "quartz_bricks": (232, 228, 220),
        "white_wool": (250, 250, 250), "snow_block": (240, 248, 252), "packed_ice": (150, 190, 240),
        "blue_ice": (90, 150, 240), "grass_block": (120, 175, 90), "moss_block": (95, 140, 60),
        "cherry_leaves": (240, 170, 200), "pink_petals": (245, 175, 205), "cherry_planks": (225, 175, 165),
        "cherry_log": (70, 45, 55), "red_sandstone": (185, 100, 40), "smooth_red_sandstone": (190, 105, 45),
        "cut_red_sandstone": (180, 95, 40), "red_sand": (195, 110, 50), "terracotta": (150, 90, 65),
        "gold_block": (245, 205, 60), "deepslate_tiles": (55, 55, 60), "cobbled_deepslate": (75, 75, 80),
        "deepslate_bricks": (65, 65, 70), "polished_deepslate": (70, 70, 75), "tuff": (105, 105, 95),
        "oxidized_cut_copper": (80, 165, 135), "oxidized_chiseled_copper": (70, 185, 150),
        "light_blue_stained_glass": (120, 190, 240), "white_stained_glass": (210, 225, 235),
        "sea_lantern": (170, 230, 230), "bubble_column": (40, 90, 230), "polished_andesite": (135, 138, 136)}
E = 4
img = Image.new("RGB", (SX * E + 20, SZ * E + 60), (24, 34, 52))
dr = ImageDraw.Draw(img)
hauts = {}
for (x, y, z), s in blocs.items():
    if s != AIR and y > hauts.get((x, z), (-99, None))[0]:
        hauts[(x, z)] = (y, s[0].split(":")[1])
for (x, z), (y, n) in hauts.items():
    c = COUL.get(n, (160, 160, 160))
    k = 0.65 + 0.35 * min(1, max(0, (y - SOL) / 40))
    dr.rectangle([10 + x * E, 30 + z * E, 10 + x * E + E - 1, 30 + z * E + E - 1], fill=tuple(int(v * k) for v in c))
for num, (x, y, z), _ in SALLES:
    dr.ellipse([10 + x * E - 9, 30 + z * E - 9, 10 + x * E + 9, 30 + z * E + 9], outline=(230, 30, 90), width=3)
    dr.text((10 + x * E + 11, 30 + z * E - 6), str(num), fill=(230, 30, 90))
dr.text((10, 8), "LE TEMPLE DES QUATRE VENTS - vue de dessus (nord en haut). Cercles = salles cachees", fill=(220, 235, 255))
img.save(PLAN)

print(f"Arene ecrite : {len(liste)} blocs, palette {len(palette)}, taille {SX}x{SY}x{SZ}")
print({k: len(v) for k, v in poses.items()})
for num, p, acces in SALLES:
    print(f"  salle {num}: coffre {monde(*p)}  -- {acces}")
print(f"POSE : /tp @s {CENTRE_MONDE[0]} {ARGS.sol + 70} {CENTRE_MONDE[1]}   puis   /place template atelier:{NOM} {ORIGINE[0]} {ORIGINE[1]} {ORIGINE[2]}")
