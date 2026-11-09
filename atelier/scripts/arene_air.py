# [claude-build]
"""
arene_air.py - Arene PvP "Air" : cite suspendue dans les nuages, cercle de 192 blocs (centre entre 4 blocs).
Zone basse a Y_BAS, plateau suspendu 18 blocs plus haut, vide au milieu du jeu. 9 phases verifiees.
  .venv\\Scripts\\python.exe sources\\arene_air.py --monde "Arene Air" --centre 0 0 --sol 86
Controle : .venv\\Scripts\\python.exe sources\\verif_acces.py exports\\arene_air.nbt exports\\arene_air_cibles.json
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
ap.add_argument("--monde", default="Arene Air")
ap.add_argument("--centre", nargs=2, type=int, default=[0, 0], metavar=("X", "Z"))
ap.add_argument("--sol", type=int, default=86, help="Y_BAS : bloc de surface de la zone basse")
ap.add_argument("--sous-sol", default="calcite")
ap.add_argument("--surface", default="calcite")
ARGS = ap.parse_args()

NOM = "arene_air"
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
SX = SZ = 192
SY = 76
SOL = 12                      # zone basse (Y_BAS)
HA = SOL + 18                 # plateau (Y_HAUT)
C = 96
CEN = R = 96.0
R_BAS = 92.0                  # face interieure du mur bas
R_P = 66.0                    # bord du plateau
R_ENC = 63.0                  # debut de l'enceinte ajouree
R_PROM = 58.0                 # debut de la promenade
R_LOT = 25.0                  # bord interieur du plateau
R_ILOT = 15.0                 # ilot central
CENTRE_MONDE = tuple(ARGS.centre)
ORIGINE = (CENTRE_MONDE[0] - C, ARGS.sol - SOL, CENTRE_MONDE[1] - C)
rng = random.Random(19226)


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


FACE = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}
FACE_ECH = {(-1, 0): "east", (1, 0): "west", (0, -1): "south", (0, 1): "north"}
VERS_CENTRE = {0: "west", 90: "north", 180: "east", 270: "south"}

# ====================================================================== blocs
QZ = S("quartz_block")
QC = S("chiseled_quartz_block")
QPIL = S("quartz_pillar", axis="y")
QB = S("quartz_bricks")
QS = S("smooth_quartz")
CAL = S("calcite")
SST = S("smooth_stone")
WC = S("white_concrete")
LGC = S("light_gray_concrete")
LAINE = S("white_wool")
NEIGE = S("snow_block")
G_W = S("white_stained_glass")
G_LB = S("light_blue_stained_glass")
G_C = S("cyan_stained_glass")
VITRE = S("white_stained_glass_pane")
CU = S("waxed_oxidized_copper")
CUC = S("waxed_oxidized_cut_copper")
CUH = S("waxed_oxidized_chiseled_copper")
CUG = S("waxed_oxidized_copper_grate")
PUR = S("purpur_block")
PURP = S("purpur_pillar", axis="y")
LUM = S("sea_lantern")
SOUL = S("soul_sand")
BULLE = S("bubble_column", drag=False)
CHAINE = S("chain", axis="y", waterlogged=False)
LANT = S("lantern", hanging=False, waterlogged=False)
LANT_P = S("lantern", hanging=True, waterlogged=False)
ROD = S("lightning_rod", facing="up", powered=False, waterlogged=False)
END = S("end_rod", facing="up")
END_B = S("end_rod", facing="down")
TRAPPE = S("birch_trapdoor", facing="north", half="top", open=False, powered=False, waterlogged=False)
LIANE = S("twisting_vines_plant")
LIANE_H = S("twisting_vines", age=25)
NEIGE_P = S("powder_snow")
LUMIERE = S("light", level=15, waterlogged=False)
BARRIERE = S("barrier", waterlogged=False)
DALLE = S("smooth_quartz_slab", type="bottom", waterlogged=False)

T_AVENUE = [(55, QS), (30, QB), (15, CAL)]
T_LOT = [(40, QB), (25, CAL), (20, SST), (15, LGC)]
T_PROM = [(50, SST), (30, WC), (20, LGC)]
T_SOUS = [(70, SST), (30, LGC)]
T_MUR_BAS = [(50, QB), (30, CAL), (20, SST)]
T_ROCHE = [(45, CAL), (25, S("diorite")), (20, QZ), (10, LGC)]
T_ILE = [(50, NEIGE), (30, WC), (20, CAL)]
T_COL = [(70, QPIL), (30, QZ)]
T_DEBRIS = [(40, QZ), (30, QB), (20, CAL), (10, LGC)]
T_SALLE = [(45, QB), (20, QS), (15, CU), (10, PUR), (10, CUC)]
T_NUAGE = [(100, LAINE)]
QUADRANTS = {
    "SE": dict(d=45, laby=[(50, WC), (30, LGC), (20, SST)], cap=CUC, sol=[(60, LGC), (40, WC)]),
    "SO": dict(d=135, laby=[(45, G_W), (35, G_LB), (20, G_C)], cap=QS, sol=[(60, QS), (40, CAL)]),
    "NO": dict(d=225, laby=[(45, CU), (35, CUC), (20, CUG)], cap=QC, sol=[(60, SST), (40, QB)]),
    "NE": dict(d=315, laby=[(50, QZ), (30, QB), (20, CAL)], cap=PUR, sol=[(60, QS), (40, WC)]),
}

NON_SOLIDES = {"air", "bubble_column", "ladder", "birch_trapdoor", "birch_door", "lantern", "lightning_rod", "chain",
               "twisting_vines", "twisting_vines_plant", "powder_snow", "light", "end_rod", "white_wall_banner",
               "light_blue_wall_banner", "white_stained_glass_pane"}
INTERDITS = {"chest", "trapped_chest", "barrel", "hopper", "dropper", "dispenser", "furnace", "blast_furnace",
             "smoker", "spawner", "ender_chest", "lava", "fire", "soul_fire", "water", "slime_block", "hay_block",
             "cobweb", "scaffolding", "honey_block"}

SALLES, ECHELLES, APPARITIONS, REMONTEES = [], [], [], []
CIBLES_ACCES = {}
occ = {}          # cases reservees sur le plateau
occ_bas = set()   # cases reservees en zone basse
deck = {}         # plateau : (x, z) -> zone
ILES = {}


def reserver(cx, cz, r, role="reserve"):
    for c in disque(cx + 0.5, cz + 0.5, r):
        occ[c] = role


def reserver_bas(cx, cz, r):
    occ_bas.update(disque(cx + 0.5, cz + 0.5, r))


def echelle(x, z, y0, y1, sup, trappes=(), mat=QB):
    for y in range(y0, y1 + 1):
        if y in trappes:
            put(x, y, z, TRAPPE)
            continue
        put(x, y, z, S("ladder", facing=FACE_ECH[sup], waterlogged=False))
        sp = (x + sup[0], y, z + sup[1])
        ECHELLES.append(((x, y, z), sp))
        if nom(sp) is None or nom(sp) in NON_SOLIDES:
            put(*sp, mat)


def porte(x, y, z, sens):
    for k, half in ((0, "lower"), (1, "upper")):
        put(x, y + k, z, S("birch_door", facing=FACE[sens], half=half, hinge="left", open=True, powered=False))


def banniere(support_, sens, y, coul="white"):
    x, z = support_[0] + sens[0], support_[1] + sens[1]
    if vide((x, y, z)):
        put(x, y, z, S(f"{coul}_wall_banner", facing=FACE[sens]))


def percer(cx, cz, a, r0, r1, y, h, w=1):
    ux, uz = math.cos(math.radians(a)), math.sin(math.radians(a))
    r_ = r0
    while r_ <= r1:
        for s in range(-w, w + 1):
            x = math.floor(cx + 0.5 + ux * r_ - uz * s)
            z = math.floor(cz + 0.5 + uz * r_ + ux * s)
            for yy in range(y, y + h):
                if not vide((x, yy, z)):
                    put(x, yy, z, AIR)
        r_ += 0.4


def nuage(cx, cy, cz, rx, ry, rz, g, seul_vide=True, table=T_NUAGE):
    for x in range(math.floor(cx - rx - 2), math.ceil(cx + rx) + 3):
        for z in range(math.floor(cz - rz - 2), math.ceil(cz + rz) + 3):
            if not (0 <= x < SX and 0 <= z < SZ) or dist(x, z) >= 94:
                continue
            for y in range(math.floor(cy - ry), math.ceil(cy + ry) + 1):
                if not SOL < y < SY:
                    continue
                e = ((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy) / ry) ** 2 + ((z + .5 - cz) / rz) ** 2
                if y < SOL + 10 and (x, z) in occ_bas:
                    continue
                if e <= 0.55 + 0.75 * bruit(x + y, z - y, 3, g) and (nom((x, y, z)) is None or not seul_vide):
                    put(x, y, z, choix(table, h01(x, y, z, g)))


def salle(num, x0, x1, z0, z1, yf, h, coffre, acces, style=T_SALLE, garder=()):
    garder = set(garder)
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            for y in range(yf, yf + h + 2):
                dedans = x0 <= x <= x1 and z0 <= z <= z1
                if dedans and yf < y <= yf + h:
                    put(x, y, z, AIR)
                elif dedans and y == yf:
                    put(x, y, z, CUC if (x + z) % 2 else QS)
                else:
                    put(x, y, z, choix(style, h01(x, y, z, 77 + num)))
    cx, cz = coffre
    put(cx, yf, cz, CUH)
    libres = garder | {coffre}
    for x, z in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
        if (x, z) not in libres and math.hypot(x - cx, z - cz) > 1.5:
            put(x, yf + 1, z, LANT)
    mx, mz = (x0 + x1) // 2, (z0 + z1) // 2
    put(mx, yf + h + 1, mz, LUM)
    if (mx, mz) not in libres and h >= 4:              # lustre : chaine et lanterne
        put(mx, yf + h, mz, CHAINE)
        put(mx, yf + h - 1, mz, LANT_P)
    for x in range(x0, x1 + 1):                        # frise de cuivre et de verre
        for z in (z0 - 1, z1 + 1):
            if (x + num) % 3 == 0:
                put(x, yf + 3, z, G_C if x % 2 else CUG)
    for z in range(z0, z1 + 1):
        for x in (x0 - 1, x1 + 1):
            if (z + num) % 3 == 0:
                put(x, yf + 3, z, G_C if z % 2 else CUG)
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
        st = dict(s[1])
        if n in INTERDITS or n.endswith("shulker_box") or n.endswith("_bed"):
            err.append(f"bloc interdit {n} en {p}")
        if n != "air" and dist(x, z) > R + 1e-9:
            err.append(f"depasse l'emprise : {n} en {p}")
        if n == "bubble_column":
            if nom((x, y - 1, z)) not in ("bubble_column", "soul_sand"):
                err.append(f"colonne sans base en {p} ({nom((x, y - 1, z))})")
            for q in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1), (x, y + 1, z)):
                if nom(q) not in ("bubble_column", "birch_door") and not solide(q):
                    err.append(f"eau non contenue {p} -> {q} ({nom(q)})")
        elif n == "birch_door":
            autre = (x, y + 1, z) if st["half"] == "lower" else (x, y - 1, z)
            if nom(autre) != "birch_door":
                err.append(f"porte incomplete en {p}")
        elif n == "lantern":
            q = (x, y + 1, z) if st["hanging"] == "true" else (x, y - 1, z)
            if not solide(q) and nom(q) != "chain":
                err.append(f"lanterne sans appui en {p}")
        elif n == "chain":
            if vide((x, y + 1, z)) and vide((x, y - 1, z)):
                err.append(f"chaine isolee en {p}")
        elif n in ("twisting_vines", "twisting_vines_plant"):
            if nom((x, y - 1, z)) != "twisting_vines_plant" and not solide((x, y - 1, z)):
                err.append(f"liane sans base en {p}")
        elif n.endswith("wall_banner"):
            d = {v: k for k, v in FACE.items()}[st["facing"]]
            if not solide((x - d[0], y, z - d[1])):
                err.append(f"banniere sans mur en {p}")
        elif n == "powder_snow":
            haut = (x, y + 1, z) if nom((x, y + 1, z)) != "powder_snow" else (x, y + 2, z)
            bas = (x, y - 1, z) if nom((x, y - 1, z)) != "powder_snow" else (x, y - 2, z)
            if not solide(bas) or not solide(haut):
                err.append(f"neige poudreuse exposee en {p}")
    for e, sp in ECHELLES:
        if nom(e) == "ladder" and not solide(sp):
            err.append(f"echelle {e} sans support ({nom(sp)})")
    for num, (cx, cy, cz), _ in SALLES:
        if nom((cx, cy, cz)) != "air" or nom((cx, cy + 1, cz)) != "air" or not solide((cx, cy - 1, cz)):
            err.append(f"salle {num} : coffre non degage en {(cx, cy, cz)}")
    print(f"[phase] {phase:<34} blocs={len(blocs):>7}  erreurs={len(err)}", flush=True)
    if err:
        for e in err[:25]:
            print("   !", e)
        raise SystemExit(1)


# ====================================================================== plan du plateau
def diag(x, z):
    return 45 + 90 * int(ang(x, z) // 90)


TROUS = [(31, 71, 6.2), (31, 251, 6.2), (62.5, 20, 2.8), (62.5, 115, 2.8), (62.5, 205, 2.8), (62.5, 295, 2.8)]
TROUS_C = [(*point(r, a), rad) for r, a, rad in TROUS]


def zone(x, z):
    d = dist(x, z)
    if d > R_P or d < R_LOT:
        return None
    a, b = dxz(x, z)
    if abs(a) <= 4.5 or abs(b) <= 4.5:
        return "avenue"
    for hx, hz, rad in TROUS_C:
        if math.hypot(x + .5 - hx, z + .5 - hz) <= rad * (0.8 + 0.4 * bruit(x, z, 3, 21)):
            return None
    if d >= R_ENC:
        return "enceinte"
    if d >= R_PROM:
        return "promenade"
    if 37 <= d < 42 and adiff(ang(x, z), diag(x, z)) <= 38:
        return None
    return "laby" if d >= 42 else "lot"


# Emplacements fixes (reserves avant toute decoration)
TOURS = {
    "NE": dict(a=315, forme="rond", ri=4, ro=6, H=HA + 28, toit="cone", mur=[(55, QZ), (30, QB), (15, CAL)]),
    "SE": dict(a=45, forme="carre", ri=4, ro=5, H=HA + 22, toit="phare", mur=[(50, WC), (30, QB), (20, LGC)]),
    "SO": dict(a=135, forme="octo", ri=4, ro=6, H=HA + 13, toit="ruine", mur=[(45, QB), (30, CAL), (25, SST)]),
    "NO": dict(a=225, forme="rond", ri=4, ro=6, H=HA + 24, toit="couronne", mur=[(40, CU), (30, CUC), (15, QB), (15, CUG)]),
}
for t in TOURS.values():
    t["c"] = cellule(31, t["a"])
    reserver(*t["c"], t["ro"] + 3, "tour")
    reserver_bas(*t["c"], t["ro"] + 5)
SLOT = {a: cellule(31, a) for a in (19, 199, 109, 289, 161, 341)}
for a, c in SLOT.items():
    reserver(*c, 8, "remontee")
    reserver_bas(*c, 9)

# ====================================================================== 1. zone basse et mur de perimetre
for x in range(SX):
    for z in range(SZ):
        d = dist(x, z)
        if d >= R:
            continue
        if d < R_BAS:
            n = bruit(x, z, 9, 1)
            put(x, SOL, z, NEIGE if n > 0.62 else WC if n > 0.42 else SST if h01(x, 0, z, 2) < 0.6 else LGC)
            continue
        a = ang(x, z)
        t = (a % 10) / 10
        pil = abs(t - 0.5) > 0.38
        for y in range(SOL, SOL + 7):
            put(x, y, z, QZ if pil else CUC if y == SOL + 6 else choix(T_MUR_BAS, h01(x, y, z, 3)))
        if pil and d < 94:
            put(x, SOL + 7, z, QC)
            if int(a) % 2 == 0:
                put(x, SOL + 8, z, END)
        if d >= 95:                                    # paroi invisible : personne ne sort de l'arene
            for y in range(SOL + 7, SY):
                put(x, y, z, BARRIERE)
verifier("1. zone basse, mur de perimetre")

# ====================================================================== 2. colonnes, plateau, ilot, iles
for x in range(SX):
    for z in range(SZ):
        zn = zone(x, z)
        if zn:
            deck[(x, z)] = zn
for (x, z), zn in deck.items():
    u = h01(x, 1, z, 4)
    if zn == "avenue":
        a, b = dxz(x, z)
        al = max(abs(a), abs(b))
        s = CUC if abs(min(abs(a), abs(b))) < 1 and int(al) % 8 < 3 else LGC if int(al) % 12 == 0 else choix(T_AVENUE, u)
    elif zn == "promenade":
        s = QC if int(ang(x, z) / 3) % 10 == 0 else choix(T_PROM, u)
    elif zn == "enceinte":
        s = QS
    elif zn == "laby":
        s = choix(QUADRANTS[{45: "SE", 135: "SO", 225: "NO", 315: "NE"}[diag(x, z)]]["sol"], u)
    else:
        s = CUC if (ang(x, z) + dist(x, z) * 9) % 60 < 5 else choix(T_LOT, u)
    put(x, HA, z, s)
    bord = any((x + a, z + b) not in deck for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    if not bord or h01(x, 2, z, 5) < 0.5:
        put(x, HA - 1, z, choix(T_SOUS, h01(x, 3, z, 6)))

# poutres sous le plateau : rayons et anneaux, lanternes pendues
for (x, z) in deck:
    d, a = dist(x, z), ang(x, z)
    if a % 15 < 1.4 * 30 / max(d, 1) or abs(d - 46) < 1 or abs(d - 34) < 1:
        put(x, HA - 2, z, CUC if int(d) % 5 == 0 else QS)
for r_ in (34, 46, 60):
    for k in range(0, 360, 15):
        x, z = cellule(r_, k + 7.5)
        if (x, z) in deck and nom((x, HA - 1, z)) and (x, z) not in occ:
            put(x, HA - 2, z, CHAINE)
            put(x, HA - 3, z, CHAINE)
            put(x, HA - 4, z, LANT_P)


def libre_deck(x, z, r):
    return all(c in deck and c not in occ for c in disque(x + 0.5, z + 0.5, r))


COLS = []
for r_, pas, dec in ((31, 30, 0), (47, 20, 10), (61, 15, 7.5)):
    for k in range(int(360 / pas)):
        a = k * pas + dec
        x, z = cellule(r_, a)
        if not libre_deck(x, z, 2.6) or min(adiff(a, g) for g in (0, 90, 180, 270)) < 7:
            continue
        COLS.append((x, z))
        reserver_bas(x, z, 4)
        for cx, cz in disque(x + .5, z + .5, 2.3):
            for y in range(SOL + 1, HA - 1):
                e = min(y - SOL, HA - y)
                if math.hypot(cx - x, cz - z) <= 1.5 or e <= 2:
                    put(cx, y, cz, QC if e <= 1 else choix(T_COL, h01(cx, y, cz, 7)))
        if h01(x, 5, z, 8) < 0.5:
            for dx, dz in ((2, 0), (-2, 0), (0, 2), (0, -2)):
                put(x + dx, SOL + 8, z + dz, LUM)

# ilot central
for x, z in disque(CEN, CEN, R_ILOT + 1):
    d = dist(x, z)
    if d > R_ILOT:
        continue
    prof = int(13 * (1 - (d / R_ILOT) ** 2) * (0.75 + 0.5 * bruit(x, z, 4, 9))) + 1
    for k in range(1, prof + 1):
        put(x, HA - k, z, G_C if h01(x, k, z, 10) < 0.05 else choix(T_ROCHE, h01(x, k, z, 11)))
    if d >= 12.5:
        put(x, HA, z, QC if int(ang(x, z) / 7.5) % 6 == 0 else QS)
    else:
        for y in range(HA, HA + 3):
            put(x, y, z, QZ)
        spirale = (ang(x, z) + d * 16) % 60 < 16
        put(x, HA + 3, z, CUC if spirale else WC if d > 4 else QS)


def ile(cx, cz, rad, y, prof, g, top=T_ILE):
    cells = set()
    for x, z in disque(cx + .5, cz + .5, rad + 2):
        dd = math.hypot(x - cx, z - cz)
        rr = rad * (0.85 + 0.35 * bruit(x, z, 5, g))
        if dd > rr:
            continue
        cells.add((x, z))
        put(x, y, z, choix(top, h01(x, y, z, g)))
        depth = int(prof * (1 - (dd / rr) ** 2) * (0.65 + 0.7 * bruit(x, z, 3, g + 1))) + 1
        for k in range(1, depth + 1):
            put(x, y - k, z, choix(T_ROCHE, h01(x, y - k, z, g + 2)))
    return cells


ILES_DEF = {"SE": (45, 80, 8, HA, 11), "SO": (135, 80, 8, HA + 2, 10), "NO": (225, 80, 8, HA - 3, 13), "NE": (315, 80, 8, HA + 4, 10)}
for i, (k, (a, r_, rad, y, prof)) in enumerate(ILES_DEF.items()):
    cx, cz = cellule(r_, a)
    ILES[k] = dict(c=(cx, cz), y=y, a=a, cells=ile(cx, cz, rad, y, prof, 100 + 7 * i))
    CIBLES_ACCES[f"île {k}"] = None
ILOTS = {}
for i, (k, a, dy) in enumerate((("SE", 70, 3), ("SO", 160, -3), ("NO", 250, 3), ("NE", 342, 5))):
    r_, rad, prof = (81, 4.5, 6) if k != "NE" else (82, 6, 8)
    cx, cz = cellule(r_, a)
    y = ILES[k]["y"] + dy
    ILOTS[k] = dict(c=(cx, cz), y=y, a=a, cells=ile(cx, cz, rad, y, prof, 140 + 7 * i))
verifier("2. colonnes, plateau, iles")

# ====================================================================== 3. enceinte ajouree et 4 entrees
for x in range(SX):
    for z in range(SZ):
        d = dist(x, z)
        if not R_ENC <= d <= R_P or (x, z) not in deck:
            continue
        a, b = dxz(x, z)
        ang_ = ang(x, z)
        if min(abs(a), abs(b)) < 10 or adiff(ang_, diag(x, z)) < 4:
            continue
        t = (ang_ % 10) / 10
        pil = abs(t - 0.5) > 0.36
        if pil:
            for y in range(HA + 1, HA + 10):
                put(x, y, z, QC if y in (HA + 1, HA + 9) else QPIL)
        else:
            yb = HA + 6 + int(2.6 * (2 * abs(t - 0.5) / 0.72) ** 2)
            for y in range(yb, HA + 10):
                put(x, y, z, QB)
            if d >= 65:
                put(x, HA + 1, z, VITRE)
        put(x, HA + 10, z, CUC)
        put(x, HA + 11, z, QS if abs(t - 0.5) < 0.2 else CU)
        if abs(t - 0.5) < 0.04 and d < 65:
            put(x, HA + 12, z, ROD)
            if nom((x, HA + 8, z)) == "quartz_bricks":
                put(x, HA + 6 + 2 - 3, z, CHAINE) if False else None

for k in range(0, 360, 10):                            # banderoles sous les arcades, cote interieur
    x, z = cellule(62.6, k + 5)
    if (x, z) in deck and not vide((x, HA + 8, z + 0)):
        continue
    y0 = cellule(63.6, k + 5)
    if nom((y0[0], HA + 8, y0[1])) == "quartz_bricks":
        put(y0[0], HA + 7, y0[1], CHAINE) if vide((y0[0], HA + 7, y0[1])) else None
        if nom((y0[0], HA + 7, y0[1])) == "chain":
            put(y0[0], HA + 6, y0[1], LANT_P)

for g in (0, 90, 180, 270):
    for al in range(61, 69):                           # palier et arc de triomphe en haut
        for sp in range(-9, 9):
            x, z = gl(al, sp, g)
            if -6 <= sp <= 5:
                put(x, HA, z, QC if al == 68 else QS)
                put(x, HA - 1, z, SST)
                for y in range(HA + 1, HA + 11):
                    put(x, y, z, AIR)
            if al >= 63 and al <= 67:
                if sp < -6 or sp > 5:
                    for y in range(HA - 1, HA + 16):
                        put(x, y, z, CU if y % 5 == 0 else QPIL)
                for y in range(HA + 11, HA + 16):
                    rose = math.hypot(sp + 0.5, y - (HA + 13.5)) < 2.1
                    put(x, y, z, G_C if rose else CUC if y == HA + 15 else QC if y == HA + 11 else QB)
    for sp in (-9, -7, 6, 8):
        x, z = gl(65, sp, g)
        put(x, HA + 16, z, ROD)
    for al in range(69, 86):                           # grand escalier
        k = al - 69
        y = HA - 1 - k
        for sp in range(-7, 7):
            x, z = gl(al, sp, g)
            if -5 <= sp <= 4:
                put(x, y, z, S("quartz_stairs", facing=VERS_CENTRE[g], half="bottom", shape="straight", waterlogged=False))
                for yy in range(SOL + 1, y):
                    put(x, yy, z, QS if sp in (-5, 4) else choix(T_MUR_BAS, h01(x, yy, z, 30)))
            else:
                for yy in range(SOL + 1, y + 2):
                    put(x, yy, z, QPIL if al % 4 == 1 else QB)
                put(x, y + 2, z, CUC)
                if al % 4 == 1 and sp in (-7, 6):
                    put(x, y + 3, z, QPIL)
                    put(x, y + 4, z, LANT)
    for al in range(86, 92):                           # parvis et arche basse
        for sp in range(-9, 9):
            x, z = gl(al, sp, g)
            if -7 <= sp <= 6:
                put(x, SOL, z, QC if (al + sp) % 4 == 0 else QS)
            if al in (87, 88):
                if sp < -5 or sp > 4:
                    for y in range(SOL + 1, SOL + 14):
                        put(x, y, z, CU if y % 6 == 0 else QPIL)
                for y in range(SOL + 10, SOL + 14):
                    put(x, y, z, CUC if y == SOL + 13 else QB)
    for sp in range(-5, 5):                            # porte extérieure fermee par du verre
        for al in range(92, 96):
            x, z = gl(al, sp, g)
            if dist(x, z) < R:
                for y in range(SOL + 1, SOL + 7):
                    put(x, y, z, G_LB)
    for al in range(66, 95):
        for sp in range(-10, 10):
            occ_bas.add(gl(al, sp, g))
    occ.update({gl(al, sp, g): "entree" for al in range(58, 70) for sp in range(-10, 10)})
    CIBLES_ACCES[f"haut de l'escalier {g}"] = None
    REMONTEES.append(("Grand escalier (entrée " + {0: "Est", 90: "Sud", 180: "Ouest", 270: "Nord"}[g] + ")",
                      (*gl(86, 0, g)[:1], SOL + 1, gl(86, 0, g)[1]), (*gl(67, 0, g)[:1], HA + 1, gl(67, 0, g)[1])))
verifier("3. enceinte, 4 entrees")


# ====================================================================== 4. moyens de remonter
def colimacon(cx, cz, a0, num):
    """Colonne porteuse creuse : escalier en colimacon, 2 tours pour 18 blocs, marches de demi-bloc."""
    for x in range(cx - 6, cx + 7):
        for z in range(cz - 6, cz + 7):
            d = math.hypot(x - cx, z - cz)
            la = math.degrees(math.atan2(z - cz, x - cx)) % 360
            if d > 5.6:
                continue
            if d > 4.0:
                for y in range(SOL + 1, HA + 7):
                    e = y - SOL
                    fen = e % 6 in (3, 4) and int(la / 30) % 3 == 0
                    put(x, y, z, G_C if fen else CUC if e % 12 == 0 else QPIL)
            elif d < 1.5:
                for y in range(SOL + 1, HA + 7):
                    put(x, y, z, QPIL)
            else:
                for y in range(SOL + 1, HA + 6):
                    put(x, y, z, AIR)
                put(x, HA + 6, z, QS)
                put(x, SOL, z, QS)
                for k in range(3):
                    tot = (la - a0) % 360 + 360 * k
                    if tot > 739:
                        continue
                    h2 = min(36, int(tot // 20))
                    if h2 == 0:
                        continue
                    if h2 % 2:
                        put(x, SOL + 1 + (h2 - 1) // 2, z, DALLE)
                    else:
                        put(x, SOL + h2 // 2, z, QS)
    percer(cx, cz, a0 + 10, 3.2, 6.4, HA + 1, 3)       # sortie sur le plateau
    percer(cx, cz, a0 - 6, 3.2, 6.4, SOL + 1, 3)       # entree en bas
    ux, uz = math.cos(math.radians(a0 + 10)), math.sin(math.radians(a0 + 10))
    for r_ in (3.5, 4.5, 5.5):                          # seuil du haut
        for s in (-1, 0, 1):
            put(math.floor(cx + .5 + ux * r_ - uz * s), HA, math.floor(cz + .5 + uz * r_ + ux * s), QC)
    for y in range(HA + 7, HA + 20):                   # la colonne continue vers le ciel, brisee au sommet
        for x, z in disque(cx + .5, cz + .5, 4.4):
            d = math.hypot(x - cx, z - cz)
            if y >= HA + 15 + int(5 * bruit(x, z, 3, num)):
                continue
            put(x, y, z, CUC if (y - HA) % 7 == 0 else QPIL if d > 2 else QZ)
    return (math.floor(cx + .5 + ux * 7.5), HA + 1, math.floor(cz + .5 + uz * 7.5))


def ascenseur(cx, cz, u):
    for i in range(-2, 3):
        for j in range(-2, 3):
            coin = abs(i) == 2 and abs(j) == 2
            if coin:
                for y in range(SOL + 1, HA + 6):
                    put(cx + i, y, cz + j, QPIL)
                put(cx + i, HA + 6, cz + j, ROD)
            elif abs(i) <= 1 and abs(j) <= 1 and (i or j):
                for y in range(SOL + 1, HA + 3):
                    put(cx + i, y, cz + j, QS if y in (HA, HA - 1) else G_C if y % 2 else G_LB)
            if max(abs(i), abs(j)) <= 2:
                put(cx + i, HA + 3, cz + j, QC)
                put(cx + i, HA + 5, cz + j, CUC)
                if max(abs(i), abs(j)) <= 1:
                    put(cx + i, HA + 4, cz + j, LUM)
    put(cx, SOL, cz, SOUL)
    for y in range(SOL + 1, HA + 3):
        put(cx, y, cz, BULLE)
    porte(cx + u[0], SOL + 1, cz + u[1], u)
    porte(cx + u[0], HA + 1, cz + u[1], u)
    return (cx + 3 * u[0], HA + 1, cz + 3 * u[1])


def liane(cx, cz, u):
    for i in range(-1, 2):
        for j in range(-1, 2):
            for y in range(SOL + 1, HA + 5):
                put(cx + i, y, cz + j, QC if y in (SOL + 1, HA + 4) else QPIL)
    vx, vz = cx + 2 * u[0], cz + 2 * u[1]
    for y in range(SOL + 1, HA):
        put(vx, y, vz, LIANE)
    put(vx, HA, vz, LIANE_H)
    p = (u[1], u[0])
    for s in (-2, 2):                                  # abri : rien ne peut tomber dans la liane
        x, z = vx + u[0] + p[0] * s, vz + u[1] + p[1] * s
        for y in range(HA + 1, HA + 5):
            put(x, y, z, QPIL)
    for i in range(-1, 3):
        for s in range(-2, 3):
            x, z = vx + u[0] * i + p[0] * s, vz + u[1] * i + p[1] * s
            put(x, HA + 5, z, CUC)
    x, z = vx + u[0] * 3, vz + u[1] * 3
    return (x, HA + 1, z), (vx, SOL + 1, vz)


def vers_centre(c):
    a, b = C - c[0], C - c[1]
    return (1 if a > 0 else -1, 0) if abs(a) >= abs(b) else (0, 1 if b > 0 else -1)


for i, a in enumerate((19, 199)):
    cx, cz = SLOT[a]
    a0 = 260 if a == 19 else 80                        # la sortie donne sur l'avenue la plus proche
    haut = colimacon(cx, cz, a0, 40 + i)
    bas = (math.floor(cx + .5 + 7.5 * math.cos(math.radians(a0 - 6))), SOL + 1, math.floor(cz + .5 + 7.5 * math.sin(math.radians(a0 - 6))))
    REMONTEES.append(("Escalier en colimaçon dans une colonne porteuse", bas, haut))
for a in (109, 289):
    cx, cz = SLOT[a]
    u = vers_centre((cx, cz))
    haut = ascenseur(cx, cz, u)
    REMONTEES.append(("Ascenseur à bulles fermé", (cx + 3 * u[0], SOL + 1, cz + 3 * u[1]), haut))
for a in (161, 341):
    cx, cz = SLOT[a]
    u = vers_centre((cx, cz))
    haut, bas = liane(cx, cz, u)
    REMONTEES.append(("Lianes le long d'un pilier", (bas[0] + u[0], SOL + 1, bas[2] + u[1]), haut))
verifier("4. moyens de remonter")


# ====================================================================== 5. tours et zone centrale
def mesure(f, dx, dz):
    a, b = abs(dx), abs(dz)
    if f == "rond":
        return math.hypot(dx, dz)
    if f == "carre":
        return max(a, b)
    return max(a, b, (a + b) / 1.414)


def tour(k, t):
    mx, mz = t["c"]
    f, ri, ro, H = t["forme"], t["ri"], t["ro"], t["H"]
    for x in range(mx - ro - 1, mx + ro + 2):
        for z in range(mz - ro - 1, mz + ro + 2):
            m = mesure(f, x - mx, z - mz)
            if m > ro:
                continue
            if m > ri:
                hm = H + 2 + int(6 * bruit(x, z, 3, 12)) if t["toit"] == "ruine" else H
                for y in range(SOL + 1, hm + 1):
                    e = y - SOL
                    fen = e % 8 in (4, 5) and (x + z) % 5 < 2 and y != HA
                    put(x, y, z, G_LB if fen else CUC if e % 12 == 0 else choix(t["mur"], h01(x, y, z, 13)))
            else:
                put(x, SOL, z, QS)
                for y in (HA, H):
                    put(x, y, z, QS)
                for y in range(SOL + 1, HA):
                    put(x, y, z, AIR)
                for y in range(HA + 1, H):
                    put(x, y, z, AIR)
    for y in range(SOL + 1, H + 2):
        put(mx, y, mz, QPIL)
    echelle(mx + 1, mz, SOL + 1, H + 1, (-1, 0))
    a_r = t["a"]
    percer(mx, mz, a_r + 180, ri - 1, ro + 1, SOL + 1, 3)
    for a_ in (a_r, a_r + 180, a_r + 90, a_r - 90):
        percer(mx, mz, a_, ri - 1, ro + 1, HA + 1, 3)
    for dx, dz in ((3, 3), (-3, 3), (-3, -3), (3, -3)):
        for y in (SOL + 1, HA + 1, H + 1):
            if mesure(f, dx, dz) <= ri and vide((mx + dx, y, mz + dz)):
                put(mx + dx, y, mz + dz, LANT)
    if t["toit"] == "cone":
        for k2 in range(1, ro + 2):
            for x in range(mx - ro - 1, mx + ro + 2):
                for z in range(mz - ro - 1, mz + ro + 2):
                    m = mesure(f, x - mx, z - mz)
                    if ro + 1 - k2 - 1 < m <= ro + 1 - k2 + 0.6:
                        put(x, H + k2, z, CU if k2 % 2 else CUC)
        put(mx, H + ro + 2, mz, CUH)
        put(mx, H + ro + 3, mz, ROD)
    elif t["toit"] == "phare":
        for x in range(mx - ro - 2, mx + ro + 3):
            for z in range(mz - ro - 2, mz + ro + 3):
                m = mesure(f, x - mx, z - mz)
                if ri < m <= ro:
                    for y in range(H + 1, H + 5):
                        put(x, y, z, G_C if (x + z) % 2 else G_W)
                elif ro < m <= ro + 2:
                    put(x, H, z, QS)
                    if m > ro + 1:
                        put(x, H + 1, z, VITRE)
                if m <= ro + 1:
                    put(x, H + 5, z, PUR)
                if m <= ri - 1:
                    put(x, H + 6, z, PURP)
        put(mx, H + 7, mz, END)
        put(mx, H + 4, mz, LUM)
        percer(mx, mz, 90, ri - 1, ro + 0.5, H + 1, 3, 0)
    elif t["toit"] == "couronne":
        for x in range(mx - ro, mx + ro + 1):
            for z in range(mz - ro, mz + ro + 1):
                m = mesure(f, x - mx, z - mz)
                if ri < m <= ro and (x + z) % 2 == 0:
                    put(x, H + 1, z, CUG)
                    put(x, H + 2, z, CUC)
                    if (x + z) % 4 == 0:
                        put(x, H + 3, z, ROD)
    else:                                              # ruine : morceaux flottants au-dessus
        for i in range(10):
            a_ = rng.uniform(0, 360)
            x, z = mx + round(5 * math.cos(a_)), mz + round(5 * math.sin(a_))
            put(x, H + rng.randint(9, 15), z, choix(T_DEBRIS, rng.random()))
    CIBLES_ACCES[f"sommet de la tour {k}"] = [mx - 2, H + 1, mz]
    REMONTEES.append((f"Échelle dans la tour {k}", (mx + 1, SOL + 1, mz), (mx + 1, HA + 1, mz)))


for k, t in TOURS.items():
    tour(k, t)


def pont(p0, p1, y0, y1, demi=2.1, cables=True, table=T_AVENUE):
    (ax, az), (bx, bz) = p0, p1
    L = math.hypot(bx - ax, bz - az)
    ux, uz = (bx - ax) / L, (bz - az) / L
    lo = demi + 2
    for x in range(math.floor(min(ax, bx) - lo), math.ceil(max(ax, bx) + lo) + 1):
        for z in range(math.floor(min(az, bz) - lo), math.ceil(max(az, bz) + lo) + 1):
            px, pz = x + .5 - ax, z + .5 - az
            t_ = px * ux + pz * uz
            s = abs(-px * uz + pz * ux)
            if t_ < -0.6 or t_ > L + 0.6:
                continue
            y = round(y0 + (y1 - y0) * min(1, max(0, t_ / L)))
            if s <= demi:
                put(x, y, z, choix(table, h01(x, y, z, 14)))
                for k2 in (1, 2, 3):
                    if not vide((x, y + k2, z)):
                        put(x, y + k2, z, AIR)
            elif s <= demi + 1 and vide((x, y, z)) and 0.6 < t_ < L - 0.6:
                put(x, y, z, CUC)
                put(x, y + 1, z, VITRE)
    if not cables or L < 6:
        return
    for sens in (-1, 1):
        off = demi + 1
        pts = []
        for i in range(int(L) + 1):
            t_ = i
            x = math.floor(ax + ux * t_ - uz * off * sens)
            z = math.floor(az + uz * t_ + ux * off * sens)
            y = round(y0 + (y1 - y0) * t_ / L)
            pts.append((x, y, z, 3 + round(6 * (2 * t_ / L - 1) ** 2)))
        for i, (x, y, z, hc) in enumerate(pts):
            if i in (0, len(pts) - 1):
                for yy in range(y, y + 10):
                    put(x, yy, z, QPIL)
                put(x, y + 10, z, END)
            elif 0 <= x < SX and 0 <= z < SZ:
                if vide((x, y + hc, z)):
                    put(x, y + hc, z, CUC)
                if i % 2 == 0:
                    for yy in range(y + 2, y + hc):
                        if vide((x, yy, z)):
                            put(x, yy, z, CHAINE)


for x, z in disque(CEN, CEN, 12.5):                    # sanctuaire : autel, steles, colonnes, couronne, sphere
    d = dist(x, z)
    if d < 3.2:
        put(x, HA + 4, z, QC)
        if d < 2.2:
            put(x, HA + 5, z, CUH)
        if d < 1.2:
            put(x, HA + 6, z, G_C)
for g in (0, 90, 180, 270):
    for al in (12, 11, 10):
        y = HA + 1 + (12 - al)
        for sp in range(-4, 4):
            x, z = gl(al, sp, g)
            put(x, y, z, S("quartz_stairs", facing=VERS_CENTRE[g], half="bottom", shape="straight", waterlogged=False))
            for yy in range(y + 1, HA + 4):
                put(x, yy, z, AIR)
    pont(point(25.5, g), point(14, g), HA, HA, demi=3.1)
for a in [g + s for g in (0, 90, 180, 270) for s in (27, 63)]:
    px, pz = point(11, a)
    for x, z in disque(px, pz, 1.4):
        for y in range(HA + 4, HA + 17):
            put(x, y, z, QC if y in (HA + 4, HA + 16) else QPIL)
for x, z in disque(CEN, CEN, 12.8):
    d = dist(x, z)
    a, b = dxz(x, z)
    if 9.3 <= d or abs(a) <= 1 or abs(b) <= 1:
        if int(ang(x, z) / 30) % 4 != 3 or d < 9.3:
            put(x, HA + 17, z, CUC if d >= 11.8 else QS)
            if d > 10 and int(ang(x, z) / 5) % 4 == 0:
                put(x, HA + 18, z, ROD)
SPH_Y = HA + 11
for x in range(C - 5, C + 5):
    for z in range(C - 5, C + 5):
        for y in range(SPH_Y - 4, SPH_Y + 5):
            e = math.hypot(x + .5 - CEN, y + .5 - SPH_Y, z + .5 - CEN)
            if 2.6 <= e <= 3.6:
                put(x, y, z, G_C if (x + y + z) % 3 else G_LB)
            elif e <= 1.3:
                put(x, y, z, LUM)
for x, z in ((C - 1, C - 1), (C, C - 1), (C - 1, C), (C, C)):
    for y in range(SPH_Y + 4, HA + 17):
        if vide((x, y, z)):
            put(x, y, z, CHAINE)
for sx_, sz_ in ((1, 1), (-1, 1), (-1, -1), (1, -1)):  # 4 steles (celle du sud-est est creuse : salle 8)
    x0, z0 = C + 5 * sx_ - (0 if sx_ > 0 else 1), C + 5 * sz_ - (0 if sz_ > 0 else 1)
    for i in (-1, 0, 1):
        for j in (-1, 0, 1):
            for y in range(HA + 4, HA + 9):
                put(x0 + i, y, z0 + j, CUH if y == HA + 8 else QB)
    put(x0, HA + 9, z0, END)
CIBLES_ACCES["sanctuaire"] = [C + 6, HA + 4, C - 1]
verifier("5. tours, sanctuaire")

# ====================================================================== 6. labyrinthes
LABY = {}


def labyrinthe(k, q):
    d0 = q["d"]

    def dedans(x, z):
        return 42.5 <= dist(x, z) <= 57.6 and adiff(ang(x, z), d0) <= 37 and (x, z) in deck

    cases = set()
    for i in range(48):
        for j in range(48):
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
            if c in cases and rng.random() < 0.08:
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
    # passage direct depuis la passerelle de la tour
    for r_ in np.arange(41.0, 50.0, 0.4):
        for s in (-1.2, 0, 1.2):
            a_ = math.radians(d0)
            x = math.floor(CEN + r_ * math.cos(a_) - s * math.sin(a_))
            z = math.floor(CEN + r_ * math.sin(a_) + s * math.cos(a_))
            murs.discard((x, z))
    # cases ouvertes sur le vide : la dalle manque au fond de certaines impasses
    impasses = [c for c in sorted(cases) if sum(c in p for p in passages) == 1 and c not in entrees
                and dist(4 * c[0] + 2, 4 * c[1] + 2) > 50]
    rng.shuffle(impasses)
    vides = impasses[:3]
    for i, j in vides:
        for a in (1, 2, 3):
            for b in (1, 2, 3):
                x, z = 4 * i + a, 4 * j + b
                deck.pop((x, z), None)
                put(x, HA, z, AIR)
                put(x, HA - 1, z, AIR)
                if nom((x, HA - 2, z)) is not None:
                    put(x, HA - 2, z, AIR)
    for x, z in murs:
        coin = (x % 4 == 0 and z % 4 == 0)
        for y in range(HA + 1, HA + 6):
            s = q["cap"] if y == HA + 5 else QPIL if coin else choix(q["laby"], h01(x, y, z, 31))
            put(x, y, z, s)
    autres = [c for c in sorted(cases) if c not in vides]
    centre_c = min(autres, key=lambda c: abs(dist(4 * c[0] + 2, 4 * c[1] + 2) - 50) + adiff(ang(4 * c[0] + 2, 4 * c[1] + 2), d0) / 4)
    for i, j in sorted(cases):
        if (i + j) % 4 == 0 and (i, j) not in vides:
            put(4 * i + 2, HA + 5, 4 * j, LUM) if (4 * i + 2, HA + 1, 4 * j) in blocs else None
    LABY[k] = dict(cases=cases, n=len(cases), vides=len(vides))
    CIBLES_ACCES[f"labyrinthe {k}"] = [4 * centre_c[0] + 2, HA + 1, 4 * centre_c[1] + 2]


for k, q in QUADRANTS.items():
    labyrinthe(k, q)
print("   labyrinthes :", {k: (v["n"], v["vides"]) for k, v in LABY.items()})
verifier("6. labyrinthes")


# ====================================================================== 7. parcours et passerelles
def parcours(p0, p1, y0, y1, pas=4.0, rad=1.9, table=T_ILE):
    L = math.hypot(p1[0] - p0[0], p1[1] - p0[1])
    n = max(abs(y1 - y0) + 1, round(L / pas))
    for i in range(1, n):
        t_ = i / n
        px, pz = p0[0] + (p1[0] - p0[0]) * t_, p0[1] + (p1[1] - p0[1]) * t_
        y = round(y0 + (y1 - y0) * t_)
        for x, z in disque(px, pz, rad):
            put(x, y, z, choix(table, h01(x, y, z, 41)))
            for k2 in (1, 2, 3):
                if not vide((x, y + k2, z)):
                    put(x, y + k2, z, AIR)
        cx, cz = math.floor(px), math.floor(pz)
        put(cx, y - 1, cz, QZ)
        put(cx, y - 2, cz, CHAINE)
        put(cx, y - 3, cz, LANT_P)


for k, t in TOURS.items():                             # passerelle tour -> labyrinthe, au-dessus du croissant
    a = t["a"]
    pont(point(36.5, a), point(44, a), HA, HA, demi=1.6)
    pont(point(R_P - 1, a), point(74.5, a), HA, ILES[k]["y"], demi=2.1)

# parcours d'ile en ile, passage etroit, pierres flottantes au-dessus de l'anneau de vide
parcours(point(80, 319), point(82, 338.5), ILES["NE"]["y"], ILOTS["NE"]["y"], pas=3.9)
parcours(point(80, 139), point(81, 158), ILES["SO"]["y"], ILOTS["SO"]["y"])
parcours(point(80, 229), point(81, 248), ILES["NO"]["y"], ILOTS["NO"]["y"])
pont(point(79, 50), point(81, 67), ILES["SE"]["y"], ILOTS["SE"]["y"], demi=0.6, cables=False)
for a in (135, 315):
    parcours(point(26.5, a), point(13.5, a), HA, HA, pas=4.3, rad=1.7)
for k in ("SO", "NO"):
    x, z = ILES[k]["c"]
    CIBLES_ACCES[f"îlot {k}"] = [ILOTS[k]["c"][0], ILOTS[k]["y"] + 1, ILOTS[k]["c"][1]]
CIBLES_ACCES["îlot SE (passage étroit)"] = [ILOTS["SE"]["c"][0], ILOTS["SE"]["y"] + 1, ILOTS["SE"]["c"][1]]
verifier("7. parcours")

# ====================================================================== 8. salles cachees
# 1 : en haut de la tour NE, sous le cone
t = TOURS["NE"]
mx, mz = t["c"]
for dx, dz in ((-3, 2), (-3, -2), (2, 3), (2, -3)):
    put(mx + dx, t["H"] + 1, mz + dz, LANT)
put(mx - 3, t["H"], mz, CUH)
put(mx - 3, t["H"] + 1, mz, AIR)
put(mx - 3, t["H"] + 2, mz, AIR)
for sens in ((-1, 0), (0, 1), (0, -1)):
    banniere((mx, mz), sens, t["H"] + 1, "light_blue")
SALLES.append((1, (mx - 3, t["H"] + 1, mz), "En haut de la tour NE : échelle jusqu'à la chambre sous le toit de cuivre"))

# 2 : sous une trappe de la promenade, suspendue sous le plateau
tx, tz = cellule(60.5, 33)
for x, z in disque(tx + .5, tz + .5, 2):
    occ[(x, z)] = "salle"
salle(2, tx - 3, tx + 3, tz - 3, tz + 3, HA - 7, 4, (tx - 3, tz), "Trappe dans le sol de la promenade (SE), échelle vers la salle suspendue sous le plateau",
      garder=[(tx, tz), (tx + 1, tz)])
for y in range(HA - 1, HA - 2 + 1):
    pass
put(tx, HA - 1, tz, AIR)
echelle(tx, tz, HA - 6, HA - 1, (1, 0), trappes=())
put(tx, HA, tz, TRAPPE)

# 3 : faux mur, sous le grand escalier Sud (chicane cote zone basse)
g = 90
pts = [gl(al, sp, g) for al in (70, 78) for sp in (-3, 3)]
xs, zs = [p[0] for p in pts], [p[1] for p in pts]
salle(3, min(xs), max(xs), min(zs), max(zs), SOL, 4, gl(70, 0, g), "Faux mur sous le grand escalier Sud : un pan de mur décalé masque une fente sur le flanc est, côté zone basse",
      garder=[gl(75, 3, g)])
for sp in (4, 5, 6, 7):
    x, z = gl(75, sp, g)
    put(x, SOL, z, QS)
    for y in range(SOL + 1, SOL + 3):
        put(x, y, z, AIR)
    put(x, SOL + 3, z, QB)
for al in range(72, 77):                               # pan decale
    x, z = gl(al, 8, g)
    for y in range(SOL + 1, SOL + 6):
        put(x, y, z, QB)
x, z = gl(72, 7, g)
for y in range(SOL + 1, SOL + 6):
    put(x, y, z, QB)
for al in range(72, 80):
    for sp in range(7, 11):
        occ_bas.add(gl(al, sp, g))

# 4 : dans un nuage, sur l'ile SE, derriere un rideau de neige poudreuse
mx, mz = ILES["SE"]["c"]
y = ILES["SE"]["y"]
cx, cz = mx + 2, mz + 2
nuage(cx, y + 3, cz, 7, 5.5, 7, 60)
salle(4, cx - 2, cx + 2, cz - 2, cz + 2, y, 4, (cx + 2, cz + 2), "Dans le nuage de l'île SE : on traverse le rideau de neige poudreuse sur sa face nord",
      style=T_NUAGE)
for yy in (y + 1, y + 2):
    put(cx, yy, cz - 3, NEIGE_P)
put(cx, y, cz - 3, QS)
for xx, zz in ILES["SE"]["cells"]:                     # le nuage laisse libre le cote du pont et du passage etroit
    if zz < cz - 3 or xx < cx - 3:
        for yy in range(y + 1, y + 5):
            if nom((xx, yy, zz)) == "white_wool":
                put(xx, yy, zz, AIR)
for zz in range(cz - 8, cz - 3):                       # couloir degage devant le rideau
    for xx in (cx - 1, cx, cx + 1):
        for yy in range(y + 1, y + 4):
            if nom((xx, yy, zz)) == "white_wool":
                put(xx, yy, zz, AIR)
        if vide((xx, y, zz)):
            put(xx, y, zz, NEIGE)
for xx in (cx - 1, cx, cx + 1):                        # auvent : le rideau reste a couvert
    for zz in (cz - 3, cz - 4):
        if vide((xx, y + 3, zz)):
            put(xx, y + 3, zz, LAINE)

# 5 : sous l'ile NO
mx, mz = ILES["NO"]["c"]
y = ILES["NO"]["y"]
salle(5, mx - 3, mx + 3, mz - 3, mz + 3, y - 9, 4, (mx - 3, mz), "Sous l'île NO : l'échelle du petit kiosque descend dans la roche de l'île",
      garder=[(mx, mz), (mx + 1, mz)])
for yy in range(y - 3, y):
    put(mx, yy, mz, AIR)
echelle(mx, mz, y - 8, y, (1, 0))
for i, j in ((-2, -2), (-2, 2), (2, -2), (2, 2)):
    for yy in range(y + 1, y + 5):
        put(mx + i, yy, mz + j, QPIL)
for i in range(-3, 4):
    for j in range(-3, 4):
        put(mx + i, y + 5, mz + j, CUC if max(abs(i), abs(j)) == 3 else QS)

# 6 : temple au bout du parcours, sur l'ilot NE
cx, cz = ILOTS["NE"]["c"]
y = ILOTS["NE"]["y"]
salle(6, cx - 2, cx + 2, cz - 2, cz + 2, y, 4, (cx + 2, cz + 2), "Au bout du parcours qui part de l'île NE : temple de l'îlot suspendu, ouvert à l'ouest",
      style=[(45, PUR), (30, QB), (25, QS)])
for zz in (cz - 1, cz, cz + 1):
    for yy in (y + 1, y + 2, y + 3):
        put(cx - 3, yy, zz, AIR)
    put(cx - 3, y, zz, QC)
    for xx in (cx - 4, cx - 5, cx - 6):
        if vide((xx, y, zz)):
            put(xx, y, zz, NEIGE)
        for yy in (y + 1, y + 2, y + 3):
            if not vide((xx, yy, zz)):
                put(xx, yy, zz, AIR)
for i, j in ((-3, -3), (-3, 3), (3, -3), (3, 3)):
    put(cx + i, y + 6, cz + j, PURP)
    put(cx + i, y + 7, cz + j, END)

# 7 : temple en ruine de la zone basse
cx, cz = cellule(50, 225)
for x, z in disque(cx + .5, cz + .5, 11):
    occ_bas.add((x, z))
salle(7, cx - 3, cx + 3, cz - 3, cz + 3, SOL, 5, (cx - 3, cz - 3), "Zone basse, sous le quadrant NO : temple en ruine, porte tournée vers le centre",
      style=[(50, QB), (30, CAL), (20, SST)])
for x, y_ in ((cx + 4, SOL + 1), (cx + 4, SOL + 2), (cx + 4, SOL + 3)):
    for zz in (cz - 1, cz, cz + 1):
        put(x, y_, zz, AIR)
for zz in (cz - 1, cz, cz + 1):
    put(cx + 4, SOL, zz, QC)
for i in range(-6, 7, 4):
    for j in (-6, 6):
        h = 4 + int(6 * h01(i, 0, j, 44))
        for y_ in range(SOL + 1, SOL + 1 + h):
            put(cx + i, y_, cz + j, QPIL)
            put(cx + j, y_, cz + i, QPIL)

# 8 : sous l'autel central, par la stele creuse du sud-est
sx_, sz_ = C + 5, C + 5
salle(8, sx_ - 3, sx_ + 3, sz_ - 3, sz_ + 3, HA - 7, 4, (sx_ - 3, sz_), "Sous le sanctuaire : la stèle sud-est est creuse, ouverte à l'ouest, échelle vers la crypte",
      garder=[(sx_, sz_), (sx_ + 1, sz_)])
for yy in range(HA - 1, HA + 7):
    put(sx_, yy, sz_, AIR)
echelle(sx_, sz_, HA - 6, HA + 5, (1, 0))
for yy in (HA + 4, HA + 5):
    put(sx_ - 1, yy, sz_, AIR)
verifier("8. salles cachees")

# ====================================================================== 9. decoration, nuages, eclairage
def ruine_deck(x, z, h, flottante=False):
    for cx, cz in disque(x + .5, z + .5, 1.5):
        for y in range(HA + 1, HA + 1 + h):
            if vide((cx, y, cz)):
                put(cx, y, cz, QC if y == HA + 1 else QPIL)
    if flottante:
        base = HA + h + 4 + rng.randint(0, 3)
        for cx, cz in disque(x + .5, z + .5, 1.5):
            for y in range(base, base + rng.randint(3, 5)):
                if vide((cx, y, cz)):
                    put(cx, y, cz, QPIL)
        if vide((x, base - 1, z)):
            put(x, base - 1, z, END_B)


# colonnes qui portent le ciel, brisees ou flottantes, sur tout le plateau libre
n_col = 0
for _ in range(900):
    x, z = cellule(rng.uniform(26, 62), rng.uniform(0, 360))
    if not libre_deck(x, z, 2.2) or nom((x, HA + 1, z)) not in (None, "air") or deck.get((x, z)) == "avenue":
        continue
    reserver(x, z, 5)
    ruine_deck(x, z, rng.randint(3, 13), flottante=rng.random() < 0.45)
    n_col += 1

for g in (0, 90, 180, 270):                            # avenues : pilastres, bannieres, arches du vent
    for al in range(29, 58, 7):
        for sp, sens_sp in ((5, -1), (-6, 1)):
            x, z = gl(al, sp, g)
            if (x, z) not in deck or not vide((x, HA + 1, z)):
                continue
            x2, z2 = gl(al, sp + sens_sp, g)
            for y in range(HA + 1, HA + 7):
                put(x, y, z, QPIL)
            put(x, HA + 7, z, LUM)
            put(x, HA + 8, z, ROD)
            banniere((x, z), (x2 - x, z2 - z), HA + 5, "light_blue" if al % 2 else "white")
    al = 50
    for sp in (-6, 5):
        for k2 in (0, 1):
            x, z = gl(al + k2, sp, g)
            for y in range(HA + 1, HA + 12):
                put(x, y, z, QPIL)
    for sp in range(-6, 6):
        for k2 in (0, 1):
            x, z = gl(al + k2, sp, g)
            yb = HA + 11 - round(3 * (abs(sp + 0.5) / 5.5) ** 2) * 0
            arc_h = HA + 8 + round(3 * (1 - ((sp + 0.5) / 5.5) ** 2))
            for y in range(arc_h, HA + 13):
                put(x, y, z, CUC if y == HA + 12 else QB)

# garde-corps par endroits : le reste s'ouvre sur le vide
for (x, z), zn in list(deck.items()):
    if zn not in ("lot", "promenade", "laby") or (x, z) in occ:
        continue
    if not vide((x, HA + 1, z)):
        continue
    if any((x + a, z + b) not in deck and dist(x + a, z + b) < R_ENC for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
        if int(ang(x, z) / 6) % 3:
            put(x, HA + 1, z, VITRE)

# iles : tertres de nuage, ruines, lanternes
for k, i_ in list(ILES.items()) + list(ILOTS.items()):
    cx, cz = i_["c"]
    y = i_["y"]
    cells = sorted(i_["cells"])
    rng.shuffle(cells)
    for x, z in cells[:7]:
        if math.hypot(x - cx, z - cz) > 4 and nom((x, y, z)) in ("snow_block", "white_concrete", "calcite") and vide((x, y + 1, z)) and vide((x, y + 2, z)):
            h = rng.randint(2, 7)
            for yy in range(y + 1, y + 1 + h):
                if vide((x, yy, z)):
                    put(x, yy, z, QPIL)
            if vide((x, y + 1 + h, z)):
                put(x, y + 1 + h, z, LANT)
    nuage(cx, y - 7, cz, 9, 3, 9, 200 + cx)            # voile de nuage sous chaque ile

# zone basse : mer de nuages, colonnes tombees, debris
for x in range(SX):
    for z in range(SZ):
        if dist(x, z) >= R_BAS - 1 or (x, z) in occ_bas or not vide((x, SOL + 1, z)):
            continue
        n = bruit(x, z, 11, 50) * 0.7 + bruit(x, z, 4, 51) * 0.3
        h = int((n - 0.47) * 16) if n > 0.47 else 0
        for y in range(SOL + 1, SOL + 1 + min(h, 5)):
            put(x, y, z, LAINE if y > SOL + 1 or h01(x, y, z, 52) < 0.7 else NEIGE)

n_tomb = 0
for _ in range(400):
    x, z = cellule(rng.uniform(22, 88), rng.uniform(0, 360))
    a = rng.uniform(0, math.pi)
    L = rng.randint(9, 17)
    pts = [(math.floor(x + i * math.cos(a)), math.floor(z + i * math.sin(a))) for i in range(L)]
    if any(c in occ_bas or dist(*c) > 88 for c in pts) or n_tomb >= 16:
        continue
    n_tomb += 1
    for i, (px, pz) in enumerate(pts):
        if i % 6 == 5:
            continue
        for u_, v in ((0, 0), (1, 0), (0, 1), (1, 1)):
            for y in (SOL + 1, SOL + 2):
                if not vide((px + u_, y + 2, pz + v)):
                    put(px + u_, y + 2, pz + v, AIR)
                put(px + u_, y, pz + v, S("quartz_pillar", axis="x") if abs(math.cos(a)) > 0.7 else S("quartz_pillar", axis="z"))
for _ in range(240):
    x, z = cellule(rng.uniform(20, 90), rng.uniform(0, 360))
    if (x, z) not in occ_bas and vide((x, SOL + 1, z)) and nom((x, SOL, z)) not in (None, "air"):
        put(x, SOL + 1, z, choix(T_DEBRIS, rng.random()))

# nuages en volume : sous le bord du plateau, sur le pourtour, et dans le ciel
trous_map = {(x, z) for x in range(SX) for z in range(SZ) if dist(x, z) < R_P and (x, z) not in deck}
n_nu = 0
for _ in range(500):
    r_ = rng.uniform(30, 62)
    x, z = cellule(r_, rng.uniform(0, 360))
    if (x, z) in deck and all(c in deck for c in disque(x + .5, z + .5, 8)) and n_nu < 14:
        nuage(x + .5, HA - 6, z + .5, rng.uniform(5, 8), rng.uniform(1.8, 3), rng.uniform(5, 8), 300 + n_nu)
        n_nu += 1
for i in range(18):                                    # nuages hauts, hors de portee des joueurs
    x, z = cellule(rng.uniform(30, 86), rng.uniform(0, 360))
    nuage(x + .5, HA + rng.randint(29, 37), z + .5, rng.uniform(5, 10), rng.uniform(1.6, 2.6), rng.uniform(5, 10), 400 + i)
for i in range(26):                                    # bancs de nuage au ras de la zone basse, sur le pourtour
    x, z = cellule(rng.uniform(70, 89), rng.uniform(0, 360))
    if (x, z) not in occ_bas:
        nuage(x + .5, SOL + 3, z + .5, rng.uniform(4, 7), rng.uniform(2, 3.2), rng.uniform(4, 7), 500 + i)

# apparitions, sur la promenade
for i in range(10):
    a = 9 + 36 * i
    for dr in (0, -1.5, 1.5, -3, 3):
        x, z = cellule(60.5 + dr, a)
        if (x, z) in deck and vide((x, HA + 1, z)) and vide((x, HA + 2, z)):
            break
    put(x, HA, z, CUH)
    APPARITIONS.append((x, HA + 1, z))
verifier("9a. decoration")

# eclairage : on calcule la lumiere et on complete jusqu'a ce que plus aucune surface ne soit noire
SOURCES = {"sea_lantern": 15, "lantern": 15, "end_rod": 14, "light": 15}
TRANSP = NON_SOLIDES | {"white_stained_glass", "light_blue_stained_glass", "cyan_stained_glass", "smooth_quartz_slab", "quartz_stairs",
                        "waxed_oxidized_copper_grate", "barrier", "sea_lantern"}
SANS_SPAWN = {"white_stained_glass", "light_blue_stained_glass", "cyan_stained_glass", "smooth_quartz_slab", "quartz_stairs",
              "waxed_oxidized_copper_grate", "barrier", "soul_sand", "white_stained_glass_pane"}
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


def surfaces_noires(L):
    noires = []
    pos = sorted(blocs)
    for (x, y, z) in pos:
        n = nom((x, y, z))
        if n in NON_SOLIDES or n in SANS_SPAWN or y + 2 >= SY or dist(x, z) >= 95:
            continue
        if vide((x, y + 1, z)) and vide((x, y + 2, z)) and L[x, y + 1, z] == 0:
            noires.append((x, y, z))
    return noires


for tour_n in range(30):
    Lm = calcul_lumiere()
    noires = surfaces_noires(Lm)
    if not noires:
        break
    pris = []
    for x, y, z in noires:
        if (x, z) in coffres or any(abs(x - a) < 9 and abs(z - b) < 9 and abs(y - c) < 6 for a, c, b in pris):
            continue
        pris.append((x, y, z))
        put(x, y + 1, z, LUMIERE)
        total_lum += 1
print(f"   eclairage : {total_lum} points de lumiere ajoutes, {tour_n} passes, surfaces noires restantes = {len(noires)}", flush=True)
if noires:
    raise SystemExit("il reste des surfaces noires")
verifier("9b. eclairage")

# ====================================================================== cibles et ecriture
for k, i_ in ILES.items():
    cx, cz = i_["c"]
    y = i_["y"]
    x = next((c for c in sorted(i_["cells"], key=lambda c: math.hypot(c[0] - cx, c[1] - cz)) if vide((c[0], y + 1, c[1])) and vide((c[0], y + 2, c[1]))), None)
    CIBLES_ACCES[f"île {k}"] = [x[0], y + 1, x[1]]
for g in (0, 90, 180, 270):
    CIBLES_ACCES[f"haut de l'escalier {g}"] = None
CIBLES_ACCES = {k: v for k, v in CIBLES_ACCES.items() if v}
for i, (t_, bas, haut) in enumerate(REMONTEES):
    CIBLES_ACCES[f"remontée {i + 1} bas"] = list(bas)
    CIBLES_ACCES[f"remontée {i + 1} haut"] = list(haut)
CIBLES_ACCES.update({f"salle {n}": list(p) for n, p, _ in SALLES})
CIBLES_ACCES.update({f"apparition {i + 1}": list(p) for i, p in enumerate(APPARITIONS)})
depart = gl(88, 0, 0)
CIBLES.write_text(json.dumps({"_marque": "[claude-build]", "sol": SOL, "depart": [depart[0], SOL + 1, depart[1]],
                              "plateau": HA, "cibles": CIBLES_ACCES}, ensure_ascii=False, indent=1), encoding="utf-8")

blocs = {p: s for p, s in blocs.items()}
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

SALLES.sort()


def w(p):
    return " ".join(str(v) for v in monde(*p))


md = ["<!-- [claude-build] genere par sources/arene_air.py -->", "# Salles cachées et remontées — arène Air", "",
      f"Monde `saves/{ARGS.monde}`, arène centrée en X={CENTRE_MONDE[0]} Z={CENTRE_MONDE[1]}. "
      f"Y_BAS={ARGS.sol}, Y_HAUT={ARGS.sol + HA - SOL}. Coordonnées du coffre = case vide où le poser.", "",
      "## Salles cachées", "", "| N° | Coffre (X Y Z) | Accès |", "|---|---|---|"]
md += [f"| {n} | {w(p)} | {a} |" for n, p, a in SALLES]
md += ["", "## Moyens de remonter", "", "| Type | Bas (X Y Z) | Haut (X Y Z) |", "|---|---|---|"]
md += [f"| {t_} | {w(b)} | {w(h)} |" for t_, b, h in REMONTEES]
md += ["", "## Apparitions (10, sur la promenade)", ""] + [f"- {i + 1} : {w(p)}" for i, p in enumerate(APPARITIONS)]
md += ["", "## Ce qui a été construit", "",
       f"- Cité suspendue Ø{SX}, plateau à {HA - SOL} blocs au-dessus de la zone basse, porté par {len(COLS)} colonnes et 2 colonnes creuses à colimaçon.",
       "- Îlot central : sanctuaire du vent, 8 colonnes, couronne brisée, sphère de verre suspendue par des chaînes, 4 ponts à câbles.",
       "- 4 tours de styles différents : flèche à toit de cuivre (NE), phare (SE), ruine à morceaux flottants (SO), tour de cuivre couronnée de paratonnerres (NO).",
       f"- 4 labyrinthes, un par quadrant (béton, verre, cuivre, quartz), murs de 5 blocs, impasses ouvertes sur le vide : {sum(v['vides'] for v in LABY.values())}.",
       "- 4 îles flottantes reliées par des ponts à chaînes, 4 îlots atteints par parcours ou passage étroit, pierres flottantes au-dessus de l'anneau de vide.",
       f"- Enceinte en colonnade ajourée, 4 grands escaliers monumentaux avec arcs de triomphe, {n_col} colonnes debout, brisées ou flottantes sur le plateau.",
       f"- Zone basse : mer de nuages, {n_tomb} colonnes tombées, débris, temple en ruine, mur de périmètre de 6 blocs.",
       "", "## Écarts par rapport au prompt", "",
       f"- Diamètre {SX} au lieu de 252, comme autorisé : pour 10 joueurs, il faut qu'ils se croisent.",
       "- Construction par structure (.nbt + /place) au lieu de /fill en jeu : les 9 phases sont dans le script, chacune vérifiée.",
       "- Les 4 entrées partent de l'intérieur du mur bas : un accès depuis l'extérieur irait contre « on ne peut pas sortir » et « rien hors de l'emprise ». Les portes du mur sont fermées par du verre.",
       "- Paroi invisible (blocs barrière) en haut du mur bas, pour qu'on ne sorte pas en sautant d'une île.",
       "- « Faux mur » réalisé en chicane : Minecraft vanilla n'a pas de bloc traversable qui imite un mur.",
       "- « Nuages traversables » : la neige poudreuse est interdite sous les chutes, donc elle ne sert que de rideau à couvert (salle 4). Les nuages en laine sont solides.",
       f"- Éclairage : lanternes et lanternes de mer, complétées par {total_lum} blocs de lumière invisibles calculés pour qu'aucune surface ne reste noire.",
       "- Bannières blanches et bleu clair unies, sans motif.",
       ]
LISTE_SALLES.write_text("\n".join(md) + "\n", encoding="utf-8")

from PIL import Image, ImageDraw
COUL = {"calcite": (222, 222, 216), "smooth_quartz": (238, 234, 228), "quartz_bricks": (230, 226, 218), "quartz_block": (235, 231, 224),
        "quartz_pillar": (236, 232, 226), "chiseled_quartz_block": (226, 222, 212), "white_wool": (250, 250, 250), "snow_block": (242, 250, 253),
        "white_concrete": (214, 218, 220), "light_gray_concrete": (140, 142, 138), "smooth_stone": (165, 165, 165),
        "waxed_oxidized_copper": (82, 165, 138), "waxed_oxidized_cut_copper": (78, 158, 132), "waxed_oxidized_chiseled_copper": (70, 180, 150),
        "waxed_oxidized_copper_grate": (70, 150, 125), "light_blue_stained_glass": (120, 190, 240), "cyan_stained_glass": (60, 170, 190),
        "white_stained_glass": (205, 222, 235), "purpur_block": (170, 125, 170), "purpur_pillar": (170, 125, 170), "sea_lantern": (170, 235, 235)}
E = 5
img = Image.new("RGB", (SX * E + 20, SZ * E + 44), (34, 52, 84))
dr = ImageDraw.Draw(img)
hauts = {}
for (x, y, z), s in blocs.items():
    n = s[0].split(":")[1]
    if n not in ("air", "barrier", "light") and y > hauts.get((x, z), (-99, None))[0] and y < HA + 26:
        hauts[(x, z)] = (y, n)
for (x, z), (y, n) in hauts.items():
    c = COUL.get(n, (170, 170, 170))
    k = 0.42 if y < HA - 8 else 0.72 + 0.28 * min(1, max(0, (y - HA) / 20))
    dr.rectangle([10 + x * E, 34 + z * E, 10 + x * E + E - 1, 34 + z * E + E - 1], fill=tuple(int(v * k) for v in c))
for t_, b, h in REMONTEES[4:]:
    dr.rectangle([10 + h[0] * E - 6, 34 + h[2] * E - 6, 10 + h[0] * E + 6, 34 + h[2] * E + 6], outline=(255, 190, 40), width=2)
for num, (x, y, z), _ in SALLES:
    dr.ellipse([10 + x * E - 10, 34 + z * E - 10, 10 + x * E + 10, 34 + z * E + 10], outline=(235, 30, 90), width=3)
    dr.text((10 + x * E + 12, 34 + z * E - 6), str(num), fill=(255, 60, 110))
dr.text((10, 10), "ARENE AIR - vue de dessus (nord en haut). Sombre = vide au-dessus de la zone basse. Cercles = salles, carres = remontees", fill=(230, 240, 255))
img.save(PLAN)

print(f"Arene ecrite : {len(liste)} blocs, palette {len(palette)}, taille {SX}x{SY}x{SZ}, colonnes {len(COLS)}, deco {n_col}, tombees {n_tomb}")
for num, p, acces in SALLES:
    print(f"  salle {num}: {monde(*p)}")
print(f"POSE : /tp @s {CENTRE_MONDE[0]} {ARGS.sol + 60} {CENTRE_MONDE[1]}   puis   /place template atelier:{NOM} {ORIGINE[0]} {ORIGINE[1]} {ORIGINE[2]}")
