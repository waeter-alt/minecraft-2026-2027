# [claude-build]
"""
col_vents.py - Arene PvP "Le Col des Vents" (theme Air) : citadelle blanche sur sol plat, jeu en hauteur.
Cercle de 252 blocs de diametre, centre ENTRE 4 blocs. 7 phases, chacune verifiee avant la suivante.

  .venv\\Scripts\\python.exe sources\\col_vents.py --monde Air --centre 0 0 --sol 86
Si le monde n'existe pas encore, seul exports/ est ecrit (pas de datapack).
Controle d'acces a pied : .venv\\Scripts\\python.exe sources\\verif_acces.py exports\\col_vents.nbt exports\\col_vents_cibles.json
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
ap.add_argument("--monde", default="Air")
ap.add_argument("--centre", nargs=2, type=int, default=[0, 0], metavar=("X", "Z"))
ap.add_argument("--sol", type=int, default=86, help="Y du bloc de surface du monde")
ap.add_argument("--sous-sol", default="calcite")
ap.add_argument("--surface", default="calcite")
ARGS = ap.parse_args()

NOM = "col_vents"
RACINE = Path(__file__).resolve().parent.parent
SAVE = RACINE / "launcher/instances/Build-1.21.1/minecraft/saves" / ARGS.monde
MONDE_OK = (SAVE / "level.dat").exists()
DATAPACK = SAVE / "datapacks/atelier"
SORTIES = [RACINE / "exports" / f"{NOM}.nbt"]
if MONDE_OK:
    SORTIES.append(DATAPACK / "data/atelier/structure" / f"{NOM}.nbt")
FONCTION = DATAPACK / "data/atelier/function" / f"{NOM}_effacer.mcfunction"
PLAN = RACINE / "exports" / f"{NOM}_plan.png"
LISTE_SALLES = RACINE / "salles_cachees_air.md"
CIBLES = RACINE / "exports" / f"{NOM}_cibles.json"

DATA_VERSION = 3955
SX = SZ = 252
SY = 80
SOL = 12                         # y local du sol (= Y ARGS.sol dans le monde)
C = 126                          # centre geometrique (entre 4 blocs)
CEN = 126.0
R = 126.0
D1 = SOL + 20                    # passerelles
D2 = SOL + 38                    # iles hautes
D3 = SOL + 56                    # sommet de l'Oeil
H_MUR = SOL + 14
H_PYL = SOL + 26
R_MUR = 121.5
R_PERIM = 114.0
CENTRE_MONDE = tuple(ARGS.centre)
ORIGINE = (CENTRE_MONDE[0] - C, ARGS.sol - SOL, CENTRE_MONDE[1] - C)
rng = random.Random(25203)


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
    """coordonnees locales d'une porte : al = distance au centre, sp = decalage lateral"""
    return {0: (C + al, C + sp), 90: (C - 1 - sp, C + al), 180: (C - 1 - al, C - 1 - sp), 270: (C + sp, C - 1 - al)}[g]


UNITE = {0: (1, 0), 90: (0, 1), 180: (-1, 0), 270: (0, -1)}
PORTES = {0: "E", 90: "S", 180: "O", 270: "N"}
FACE = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}
FACE_ECH = {(-1, 0): "east", (1, 0): "west", (0, -1): "south", (0, 1): "north"}

# ====================================================================== blocs
CALC = S("calcite")
QB = S("quartz_bricks")
QL = S("smooth_quartz")
QP = S("quartz_pillar", axis="y")
DIO = S("polished_diorite")
CUIV = S("oxidized_cut_copper")
CUIV_C = S("oxidized_chiseled_copper")
GRILLE = S("oxidized_copper_grate")
VERRE = S("light_blue_stained_glass")
VERRE_B = S("white_stained_glass")
LAINE = S("white_wool")
LUM = S("sea_lantern")
SOUL = S("soul_sand")
BULLE = S("bubble_column", drag=False)
TRAPPE = S("birch_trapdoor", facing="north", half="top", open=False, powered=False, waterlogged=False)
LANT = S("lantern", hanging=False, waterlogged=False)
PARATON = S("lightning_rod", facing="up", powered=False, waterlogged=False)

T_MUR = [(45, CALC), (30, QB), (15, DIO), (10, QL)]
T_SOL = [(60, CALC), (25, S("diorite")), (15, S("snow_block"))]
T_AVENUE = [(50, QL), (30, QB), (20, DIO)]
T_PERIM = [(60, S("polished_andesite")), (40, DIO)]
T_ROCHE = [(45, S("tuff")), (25, S("andesite")), (20, CALC), (10, S("diorite"))]
T_ILE = [(50, S("snow_block")), (35, CALC), (15, QL)]
T_NUAGE = [(70, LAINE), (20, S("snow_block")), (10, CALC)]
T_SALLE = [(50, QB), (25, QL), (15, CUIV), (10, S("oxidized_copper"))]
T_LAINE = [(100, LAINE)]

NON_SOLIDES = {"air", "bubble_column", "ladder", "birch_trapdoor", "birch_door", "lantern", "lightning_rod"}
INTERDITS = {"chest", "trapped_chest", "barrel", "hopper", "dropper", "dispenser", "furnace", "blast_furnace",
             "smoker", "spawner", "ender_chest", "lava", "fire", "soul_fire"}

SALLES = []          # (num, coffre_local, acces)
ECHELLES = []
PHASES = []
CIBLES_ACCES = {}    # nom -> [x, y, z] (pieds du joueur)
APPARITIONS = []
occ = {}             # cases du sol reservees (x, z) -> role


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


def colonne_vent(x, z, y_haut, porte_sens, cotes=1):
    """colonne de bulles (monte le joueur) dans un tube de verre ; sommet affleurant le plancher"""
    put(x, SOL, z, SOUL)
    for y in range(SOL + 1, y_haut + 1):
        put(x, y, z, BULLE)


def salle(num, x0, x1, z0, z1, yf, h, coffre, acces, style=T_SALLE, garder=()):
    """interieur x0..x1, z0..z1, sol en yf, air yf+1..yf+h, plafond yf+h+1"""
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


# ====================================================================== verifications
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
            err.append(f"depasse l'enceinte : {n} en {p} (d={dist(x, z):.2f})")
        if n == "bubble_column":
            if nom((x, y - 1, z)) not in ("bubble_column", "soul_sand"):
                err.append(f"colonne de vent sans base en {p} ({nom((x, y - 1, z))})")
            for q in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1)):
                if nom(q) not in ("bubble_column", "birch_door") and not solide(q):
                    err.append(f"eau non contenue {p} -> {q} ({nom(q)})")
        elif n == "birch_door":
            half = dict(s[1])["half"]
            autre = (x, y + 1, z) if half == "lower" else (x, y - 1, z)
            if nom(autre) != "birch_door":
                err.append(f"porte incomplete en {p}")
        elif n == "lantern" and not solide((x, y - 1, z)):
            err.append(f"lanterne dans le vide en {p}")
    for e, sp in ECHELLES:
        if nom(e) == "ladder" and not solide(sp):
            err.append(f"echelle {e} sans support ({nom(sp)})")
    for num, (cx, cy, cz), _ in SALLES:
        if nom((cx, cy, cz)) != "air" or nom((cx, cy + 1, cz)) != "air" or not solide((cx, cy - 1, cz)):
            err.append(f"salle {num} : emplacement du coffre non degage en {(cx, cy, cz)}")
    PHASES.append((phase, len(blocs), len(err)))
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
        if d < R_MUR:
            if d >= R_PERIM:
                t = T_PERIM
            elif d < 24 or avenue(x, z):
                t = T_AVENUE
            else:
                t = T_SOL
            put(x, SOL, z, choix(t, h01(x, 0, z, 1)))
            continue
        a = ang(x, z)
        for y in range(SOL, H_MUR):
            bande = (y - SOL) in (5, 6) and int(a * 3) % 4 == 0
            put(x, y, z, VERRE if bande and d < 123 else choix(T_MUR, h01(x, y, z, 2)))
        put(x, H_MUR, z, QL)
        if d >= 124.5:
            put(x, H_MUR + 1, z, QB)
            if int(a * 1.5) % 2 == 0:
                put(x, H_MUR + 2, z, CALC)

# pilastres lumineux sur la face interieure
for a in range(0, 360, 15):
    if min(adiff(a, g) for g in PORTES) < 12:
        continue
    px, pz = cellule(120.8, a)
    for y in range(SOL + 1, H_MUR - 1):
        put(px, y, pz, QP)
    put(px, H_MUR - 1, pz, LUM)

for g in PORTES:
    u = UNITE[g]
    for al in range(113, 125):                          # pylones
        for sp in list(range(5, 12)) + list(range(-12, -5)):
            x, z = gl(al, sp, g)
            for y in range(SOL + 1, H_PYL + 1):
                e = y - SOL
                put(x, y, z, CUIV if e in (10, 20) else QB if (al + sp + y) % 5 else CALC)
            bord = al in (113, 124) or sp in (5, 11, -12, -6)
            put(x, H_PYL + 1, z, CUIV if bord else CUIV_C)
            if not bord:
                put(x, H_PYL + 2, z, CUIV)
        # le fronton entre les pylones est bati plus bas
    for sp in range(-4, 4):                             # arche fermee cote exterieur par du verre
        for al in range(118, 126):
            x, z = gl(al, sp, g)
            if dist(x, z) >= R:
                continue
            haut = 9 - (1 if sp in (-4, 3) else 0)
            for y in range(SOL + 1, SOL + 1 + haut):
                put(x, y, z, VERRE if al >= 124 else AIR)
            if al < 124:
                put(x, SOL, z, choix(T_AVENUE, h01(x, 0, z, 1)))
    for sp in range(-5, 5):                             # fronton a rosace
        for al in (120, 121, 122):
            x, z = gl(al, sp, g)
            for y in range(SOL + 10, SOL + 23):
                rosace = math.hypot(sp + 0.5, y - (SOL + 16)) < 3.2 and al == 120
                put(x, y, z, VERRE if rosace else CUIV if y == SOL + 22 else QB)
    for s in (13, -14):                                 # echelles vers le chemin de ronde
        al = 121
        while dist(*gl(al, s, g)) >= R_MUR:
            al -= 1
        x, z = gl(al, s, g)
        echelle(x, z, SOL + 1, H_MUR, u)
verifier("1. sol, enceinte, portes")

# ====================================================================== 2. l'Oeil du cyclone
H_OEIL = SOL + 60


def tot_rampe(a, k):
    return (a - 5) % 360 + 360 * k


for x in range(C - 23, C + 23):
    for z in range(C - 23, C + 23):
        d = dist(x, z)
        a = ang(x, z)
        if d < 13:
            put(x, SOL, z, (LUM if h01(x, 3, z, 4) < 0.04 else CUIV if int(d) % 4 == 1 else QL))
        if 13 <= d < 16:
            for y in range(SOL, H_OEIL + 1):
                e = y - SOL
                fen = adiff(a, round(a / 30) * 30) < 3.5 and e % 18 in (8, 9, 10, 11, 12)
                vue = y > D3 and y <= D3 + 3 and adiff(a, 15 + round((a - 15) / 30) * 30) < 5
                if vue:
                    continue
                put(x, y, z, VERRE if fen else CUIV if e % 18 == 0 else choix(T_MUR, h01(x, y, z, 5)))
            put(x, H_OEIL + 1, z, CUIV)
        if 8 <= d < 13:                                 # rampe en spirale : +1 tous les 20 degres
            for k in range(4):
                tot = tot_rampe(a, k)
                if tot > 1120:
                    continue
                h = int(tot // 20)
                if h <= 0:
                    continue
                put(x, SOL + h, z, LUM if h01(x, h, z, 6) < 0.03 else QB)
                if d < 9:
                    put(x, SOL + h + 1, z, VERRE_B)
        if d < 13:                                      # plancher du sommet (trou au-dessus de la fin de rampe)
            trou = d >= 8 and (a >= 340 or a <= 46)
            if not trou:
                put(x, D3, z, QL)
        if 16 <= d < 22:                                # balcons
            for dk in (D1, D2):
                put(x, dk, z, QL)
                if d >= 21:
                    put(x, dk + 1, z, VERRE_B)
            if d < 18.5:
                for dk in (D1, D2):
                    if int(a) % 30 < 6:
                        put(x, dk - 1, z, QB)
                        if d < 17.5:
                            put(x, dk - 2, z, QB)

# entrees au sol (4 arches) et portes vers les balcons (a 45 degres, la ou la rampe passe a la bonne cote)
for x in range(C - 17, C + 17):
    for z in range(C - 17, C + 17):
        d = dist(x, z)
        a, b = dxz(x, z)
        if 13 <= d < 16.5:
            if abs(a) <= 3 or abs(b) <= 3:
                for y in range(SOL + 1, SOL + 6):
                    put(x, y, z, AIR)
                put(x, SOL, z, QL)
            if adiff(ang(x, z), 45) <= 7:
                for dk in (D1, D2):
                    put(x, dk, z, QL)
                    for y in range(dk + 1, dk + 4):
                        put(x, y, z, AIR)

# colonne de vent centrale (2x2) dans un tube de verre
for x in range(C - 2, C + 2):
    for z in range(C - 2, C + 2):
        if x in (C - 2, C + 1) or z in (C - 2, C + 1):
            for y in range(SOL + 1, D3):
                put(x, y, z, VERRE)
        else:
            colonne_vent(x, z, D3, (1, 0))
porte(C - 2, SOL + 1, C - 1, (1, 0))

# couronne du sommet
for a in range(0, 360, 45):
    x, z = cellule(14.5, a + 22.5)
    for y in range(H_OEIL + 2, H_OEIL + 5):
        put(x, y, z, QP)
    put(x, H_OEIL + 5, z, PARATON)
CIBLES_ACCES["sommet de l'Oeil"] = [C - 10, D3 + 1, C]
verifier("2. Oeil du cyclone")

# ====================================================================== 3. moulins et passerelles
MOULINS = {
    "NE": dict(a=315, forme="rond", ri=7, ro=9, H=D2 + 14, ailes=4, toit="cone"),
    "SE": dict(a=45, forme="carre", ri=7, ro=8, H=D2 + 10, ailes=4, toit="plat"),
    "SO": dict(a=135, forme="octo", ri=7, ro=9, H=D2 + 6, ailes=2, toit="ruine"),
    "NO": dict(a=225, forme="rond", ri=6, ro=8, H=D2 + 20, ailes=4, toit="phare"),
}


def mesure(f, dx, dz):
    a, b = abs(dx), abs(dz)
    if f == "rond":
        return math.hypot(dx, dz)
    if f == "carre":
        return max(a, b)
    return max(a, b, (a + b) / 1.414)


for q, m in MOULINS.items():
    m["c"] = cellule(70, m["a"])


def bati(x, z):
    if dist(x, z) < 22.5:
        return True
    for m in MOULINS.values():
        if mesure(m["forme"], x - m["c"][0], z - m["c"][1]) <= m["ro"] + 1:
            return True
    return False


def dans_moulin(x, z, marge):
    """vrai si la case est dans l'interieur d'un moulin, reduit de marge"""
    for m in MOULINS.values():
        if mesure(m["forme"], x - m["c"][0], z - m["c"][1]) <= m["ri"] - marge:
            return True
    return False


def moulin(q, m):
    mx, mz = m["c"]
    f, ri, ro, H = m["forme"], m["ri"], m["ro"], m["H"]
    for x in range(mx - ro - 1, mx + ro + 2):
        for z in range(mz - ro - 1, mz + ro + 2):
            k = mesure(f, x - mx, z - mz)
            if k > ro:
                continue
            occ[(x, z)] = "moulin"
            if k > ri:
                hm = H - int(9 * bruit(x, z, 4, 11)) if m["toit"] == "ruine" else H
                for y in range(SOL + 1, hm + 1):
                    e = y - SOL
                    fen = e % 9 in (5, 6) and (x + z) % 6 < 2
                    put(x, y, z, VERRE if fen else CUIV if e % 18 == 0 else choix(T_MUR, h01(x, y, z, 12)))
            else:
                put(x, SOL, z, QL)
                for y in (D1, D2, H):
                    if (x, z) != (mx + 1, mz):
                        put(x, y, z, QL)
    for y in range(SOL + 1, H + 2):                     # pilier central + echelle
        put(mx, y, mz, QP)
    echelle(mx + 1, mz, SOL + 1, H + 1, (-1, 0))
    # porte au sol, cote centre
    n = math.hypot(C - mx, C - mz)
    ux, uz = (C - mx) / n, (C - mz) / n
    for t in [ri - 1 + i * 0.5 for i in range(10)]:
        for s in (-1, 0, 1):
            x = math.floor(mx + 0.5 + ux * t - uz * s)
            z = math.floor(mz + 0.5 + uz * t + ux * s)
            for y in range(SOL + 1, SOL + 5):
                if not vide((x, y, z)):
                    put(x, y, z, AIR)
    # toits
    if m["toit"] == "cone":
        for k in range(1, ro + 1):
            for x in range(mx - ro, mx + ro + 1):
                for z in range(mz - ro, mz + ro + 1):
                    if ro - k - 1 < mesure(f, x - mx, z - mz) <= ro - k + 0.5:
                        put(x, H + k, z, CUIV)
        put(mx, H + ro + 1, mz, PARATON)
    elif m["toit"] == "plat":
        for x in range(mx - ro, mx + ro + 1):
            for z in range(mz - ro, mz + ro + 1):
                if mesure(f, x - mx, z - mz) > ri and (x + z) % 2 == 0:
                    put(x, H + 1, z, CALC)
    elif m["toit"] == "phare":
        for x in range(mx - ro, mx + ro + 1):
            for z in range(mz - ro, mz + ro + 1):
                k = mesure(f, x - mx, z - mz)
                if ri < k <= ro:
                    put(x, H + 1, z, VERRE_B)
                if 2.5 < k <= 3.5 and not (x > mx and abs(z - mz) <= 0):
                    for y in range(H + 1, H + 5):
                        put(x, y, z, VERRE)
                if k <= 3.5:
                    put(x, H + 5, z, CUIV)
        put(mx, H + 2, mz, LUM)
        put(mx, H + 6, mz, PARATON)
    # ailes dans le plan x = cte, sur la face tournee vers l'exterieur
    sg = 1 if mx > C else -1
    px = mx + sg * (ro + 2)
    hy = H - 4
    L = 13 if m["ailes"] == 4 else 9
    for x in range(mx + sg * ro, px + sg, sg):
        put(x, hy, mz, CUIV)
    dirs = [(1, 0), (0, 1), (-1, 0), (0, -1)][: m["ailes"]]
    for dy, dz in dirs:
        for t in range(1, L + 1):
            for w in range(3):
                oy, oz = (dz * w, dy * w)                # decalage perpendiculaire a la pale
                y, z = hy + dy * t + (oy if t > 2 else 0), mz + dz * t + (-oz if t > 2 else 0)
                if t <= 2 and w:
                    continue
                cadre = t <= 2 or w == 0 or t % 3 == 0
                put(px, y, z, CUIV if cadre else LAINE)
    CIBLES_ACCES[f"sommet du moulin {q}"] = [mx - 3, H + 1, mz]


for q, m in MOULINS.items():
    moulin(q, m)


def pont(r0, r1, a, y):
    """passerelle droite radiale, de r0 a r1 selon l'angle a"""
    ax, az = point(r0, a)
    ux, uz = math.cos(math.radians(a)), math.sin(math.radians(a))
    L = r1 - r0
    for x in range(SX):
        for z in range(SZ):
            px, pz = x + 0.5 - ax, z + 0.5 - az
            t = px * ux + pz * uz
            s = abs(-px * uz + pz * ux)
            if t < 0 or t > L or s > 2.6:
                continue
            if s <= 1.6:
                put(x, y, z, choix(T_AVENUE, h01(x, y, z, 13)))
                for k in (1, 2, 3):
                    if not vide((x, y + k, z)):
                        put(x, y + k, z, AIR)
            elif not bati(x, z) and vide((x, y, z)) and nom((x, y + 1, z)) is None:
                put(x, y + 1, z, VERRE_B)


def arc(a0, a1, y):
    """passerelle circulaire a r=70, de a0 a a1 (degres croissants)"""
    for x in range(SX):
        for z in range(SZ):
            d = dist(x, z)
            if not 67.5 <= d <= 72.5 or (ang(x, z) - a0) % 360 > a1 - a0:
                continue
            if 68.5 <= d <= 71.5:
                if dans_moulin(x, z, 1):
                    continue
                put(x, y, z, choix(T_AVENUE, h01(x, y, z, 14)))
                for k in (1, 2, 3):
                    if not vide((x, y + k, z)):
                        put(x, y + k, z, AIR)
            elif not bati(x, z) and nom((x, y + 1, z)) is None:
                put(x, y + 1, z, VERRE_B)


def pile(r, a, y):
    px, pz = point(r, a)
    for x, z in disque(px, pz, 1.6):
        occ[(x, z)] = "pile"
        for yy in range(SOL + 1, y):
            put(x, yy, z, QP if (x + z) % 2 else QB)


for a in (45, 135, 225, 315):
    pont(20, 64, a, D1)
for q in ("NE", "SO"):
    pont(20, 64, MOULINS[q]["a"], D2)
arc(-45, 45, D1)
arc(45, 135, D1)
arc(225, 315, D1)
arc(135, 150, D1)
arc(205, 225, D1)
for a in (20, 70, 110, 250, 290, 340):
    pile(70, a, D1)
for a in (45, 135, 225, 315):
    pile(36, a, D1)
    pile(50, a, D1)

# parcours : plots suspendus de la passerelle cassee vers le nuage de la salle 4
for i in range(7):
    px, pz = point(70, 176 - i * 4.1)
    for x, z in disque(px, pz, 1.6):
        put(x, D1, z, choix(T_ILE, h01(x, 1, z, 15)))
verifier("3. moulins et passerelles")

# ====================================================================== 4. iles et colonnes de vent
ILES = {}
for g in PORTES:
    tx, tz = gl(41, 0, g)
    cx, cz = tx + 0.5, tz + 0.5
    ILES[g] = (tx, tz)
    for x, z in disque(cx, cz, 13):
        d = math.hypot(x + 0.5 - cx, z + 0.5 - cz)
        rl = 10 + 3 * bruit(x, z, 6, 20 + g)
        if d > rl:
            continue
        put(x, D2, z, choix(T_ILE, h01(x, 2, z, 21)))
        prof = min(12, int((rl - d) * (0.9 + 0.5 * bruit(x, z, 3, 22))))
        for k in range(1, prof + 1):
            put(x, D2 - k, z, choix(T_ROCHE, h01(x, k, z, 23)))
    pont(20, 34, g, D2)
    # temple brise sur l'ile
    for i, a in enumerate((30, 120, 210, 300)):
        ax, az = math.floor(cx + 6.5 * math.cos(math.radians(a + g))), math.floor(cz + 6.5 * math.sin(math.radians(a + g)))
        for y in range(D2 + 1, D2 + 1 + (7 if i else 3)):
            put(ax, y, az, QP)
        if i:
            put(ax, D2 + 8, az, CUIV)
    # tube de verre et colonne
    u = UNITE[g]
    for a_ in (-1, 0, 1):
        for b_ in (-1, 0, 1):
            x, z = tx + a_, tz + b_
            if a_ == 0 and b_ == 0:
                continue
            for y in range(SOL + 1, D2):
                if vide((x, y, z)):
                    put(x, y, z, VERRE)
    colonne_vent(tx, tz, D2, u)
    dx_, dz_ = gl(40, 0, g)
    porte(dx_, SOL + 1, dz_, u)
    CIBLES_ACCES[f"ile {PORTES[g]}"] = list(gl(47, -3, g)[:1]) + [D2 + 1] + list(gl(47, -3, g)[1:])
verifier("4. iles et colonnes de vent")

# ====================================================================== 5. mer de nuages (labyrinthes)
H_LABY = 4
LABY = {}


def labyrinthe(q, sx, sz):
    def dedans(x, z):
        a, b = dxz(x, z)
        return 84 <= dist(x, z) <= 113 and sx * a >= 8 and sz * b >= 8

    cases = set()
    for i in range(63):
        for j in range(63):
            x0, z0 = 4 * i, 4 * j
            if all(dedans(x, z) for x, z in ((x0, z0), (x0 + 4, z0), (x0, z0 + 4), (x0 + 4, z0 + 4))):
                cases.add((i, j))
    # plus grande composante
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
    passages = set()
    vus = {min(cases)}
    pile_ = [min(cases)]
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
    for i, j in sorted(cases):                          # quelques boucles pour le PvP
        for c in ((i + 1, j), (i, j + 1)):
            if c in cases and rng.random() < 0.09:
                passages.add(frozenset(((i, j), c)))
    murs = set()
    for i, j in cases:
        for a in range(5):
            for b in range(5):
                x, z = 4 * i + a, 4 * j + b
                occ[(x, z)] = "laby"
                put(x, SOL, z, choix(T_NUAGE, h01(x, 0, z, 30)))
                if a in (0, 4) or b in (0, 4):
                    murs.add((x, z))

    def trou(c, d):
        i, j = c
        for k in (1, 2, 3):
            if d == (1, 0):
                murs.discard((4 * i + 4, 4 * j + k))
            elif d == (-1, 0):
                murs.discard((4 * i, 4 * j + k))
            elif d == (0, 1):
                murs.discard((4 * i + k, 4 * j + 4))
            else:
                murs.discard((4 * i + k, 4 * j))

    for p in passages:
        a, b = sorted(p)
        trou(a, (b[0] - a[0], b[1] - a[1]))
    # entrees : 3 cote centre, 3 cote enceinte, bien reparties
    cand_in, cand_out = [], []
    for c in sorted(cases):
        for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (c[0] + d[0], c[1] + d[1]) in cases:
                continue
            r0 = dist(4 * c[0] + 2, 4 * c[1] + 2)
            r1 = dist(4 * c[0] + 2 + 4 * d[0], 4 * c[1] + 2 + 4 * d[1])
            (cand_in if r1 < r0 else cand_out).append((ang(4 * c[0] + 2, 4 * c[1] + 2), c, d))
    entrees = set()
    for liste in (cand_in, cand_out):
        liste.sort()
        for k in range(3):
            _, c, d = liste[(2 * k + 1) * len(liste) // 6]
            trou(c, d)
            entrees.add(c)
    for x, z in murs:
        for y in range(SOL + 1, SOL + 1 + H_LABY):
            put(x, y, z, choix(T_NUAGE, h01(x, y, z, 31)))
    # neige poudreuse : on s'y enfonce d'un bloc
    for i, j in sorted(cases):
        if rng.random() < 0.07 and (i, j) not in entrees:
            put(4 * i + 2, SOL, 4 * j + 2, S("powder_snow"))
    LABY[q] = dict(cases=cases, passages=passages, entrees=entrees)


for q, sx, sz in (("NE", 1, -1), ("SE", 1, 1), ("SO", -1, 1), ("NO", -1, -1)):
    labyrinthe(q, sx, sz)
verifier("5. mer de nuages")

# ====================================================================== 6. salles cachees
Y_SOUS = SOL - 7

# 1 : trappe au fond d'un cul-de-sac du labyrinthe NE
L = LABY["NE"]
impasses = [c for c in L["cases"] if sum(c in p for p in L["passages"]) == 1 and c not in L["entrees"]]
ci, cj = min(impasses, key=lambda c: abs(dist(4 * c[0] + 2, 4 * c[1] + 2) - 98) + adiff(ang(4 * c[0] + 2, 4 * c[1] + 2), 315))
tx, tz = 4 * ci + 2, 4 * cj + 2
salle(1, tx, tx + 6, tz - 3, tz + 3, Y_SOUS, 4, (tx + 6, tz), "labyrinthe NE : trappe dans un cul-de-sac, échelle vers le bas",
      garder=[(tx, tz)])
echelle(tx, tz, Y_SOUS + 1, SOL, (-1, 0), trappes=(SOL,))

# 2 : chicane dans le pylone de la porte Sud, puis echelle vers une salle sous le pylone
g = 90
xs = [gl(al, sp, g)[0] for al in (114, 118) for sp in (7, 11)]
zs = [gl(al, sp, g)[1] for al in (114, 118) for sp in (7, 11)]
cx2, cz2 = gl(114, 9, g)
salle(2, min(xs), max(xs), min(zs), max(zs), Y_SOUS, 4, (cx2, cz2),
      "porte Sud : fente dans le pylône (chicane d'un bloc), échelle au bout, salle dessous")
for al, sp in ((113, 6), (114, 6), (115, 6), (115, 7), (115, 8), (115, 9), (116, 9), (117, 9), (118, 9)):
    x, z = gl(al, sp, g)
    for y in range(SOL + 1, SOL + 4):
        put(x, y, z, AIR)
    put(x, SOL, z, QL)
x, z = gl(118, 9, g)
echelle(x, z, Y_SOUS + 1, SOL, UNITE[g])

# 3 : sous l'Oeil, trappe dans le sol de la salle centrale
salle(3, C - 5, C + 5, C - 5, C + 4, Y_SOUS, 4, (C + 5, C - 1), "Œil du cyclone : trappe dans le sol, à l'ouest du tube",
      garder=[(C - 5, C - 1)])
echelle(C - 5, C - 1, Y_SOUS + 1, SOL, (-1, 0), trappes=(SOL,))

# 4 : salle dans un nuage, au bout du parcours de plots
salle(4, 53, 59, 123, 129, D1, 4, (56, 124), "passerelle cassée Sud-Ouest : plots suspendus jusqu'au nuage",
      style=T_LAINE)
for x in (55, 56, 57):
    put(x, D1, 130, QL)
    for y in range(D1 + 1, D1 + 4):
        put(x, y, 130, AIR)
for x, z in disque(56.5, 126.5, 8.5):
    for y in range(D1 - 2, D1 + 8):
        if z >= 130 or nom((x, y, z)) is not None:      # jamais dans l'interieur de la salle (air explicite)
            continue
        if math.hypot(x + 0.5 - 56.5, z + 0.5 - 126.5, (y - D1 - 3) * 1.3) < 7.5 + 2 * bruit(x + y, z, 3, 40):
            put(x, y, z, LAINE)

# 5 : sous le cone du moulin NE
m = MOULINS["NE"]
mx, mz = m["c"]
for x, z in ((mx - 5, mz - 2), (mx - 5, mz + 2), (mx + 4, mz + 3), (mx + 4, mz - 3)):
    put(x, m["H"] + 1, z, LANT)
put(mx - 5, m["H"], mz, CUIV_C)
put(mx - 5, m["H"] + 1, mz, AIR)
put(mx - 5, m["H"] + 2, mz, AIR)
SALLES.append((5, (mx - 5, m["H"] + 1, mz), "moulin NE : tout en haut de l'échelle, sous le cône de cuivre"))

# 6 : dans la roche de l'ile Est, trappe sur l'ile
xa, za = gl(44, 2, 0)
xb, zb = gl(48, 6, 0)
salle(6, xa, xb, za, zb, D2 - 6, 4, (xb, za + 2), "île Est : trappe dans le sol de l'île, échelle dans la roche",
      style=T_ROCHE, garder=[(xa, za + 2)])
echelle(xa, za + 2, D2 - 5, D2, (-1, 0), trappes=(D2,))

# 7 : a mi-hauteur de la colonne de vent Nord, porte dans le tube
tx, tz = ILES[270]
salle(7, tx + 2, tx + 6, tz - 2, tz + 2, SOL + 9, 4, (tx + 6, tz),
      "colonne de vent Nord : porte dans le tube à mi-montée, côté est (il faut se diriger vers elle en montant)")
porte(tx + 1, SOL + 10, tz, (1, 0))

# 8 : dans le pylone de la porte Ouest, par une fente au bout du chemin de ronde
g = 180
xs = [gl(al, sp, g)[0] for al in (116, 120) for sp in (7, 10)]
zs = [gl(al, sp, g)[1] for al in (116, 120) for sp in (7, 10)]
cx8, cz8 = gl(116, 8, g)
salle(8, min(xs), max(xs), min(zs), max(zs), H_MUR, 4, (cx8, cz8),
      "porte Ouest : fente au bout du chemin de ronde, dans le pylône")
for al, sp in ((122, 11), (122, 10), (121, 10)):
    x, z = gl(al, sp, g)
    for y in range(H_MUR + 1, H_MUR + 4):
        put(x, y, z, AIR)
verifier("6. salles cachees")

# ====================================================================== 7. decoration
hautes = set()
for (x, y, z), s in blocs.items():
    if y > SOL + 6 and s != AIR:
        hautes.add((x, z))


def libre(x, z, marge=0):
    if not 24 <= dist(x, z) <= 112 or avenue(x, z, 2):
        return False
    for a in range(-marge, marge + 1):
        for b in range(-marge, marge + 1):
            if (x + a, z + b) in occ or not vide((x + a, SOL + 1, z + b)):
                return False
    return True


poses = []
for _ in range(600):                                    # colonnes debout et brisees
    x, z = cellule(rng.uniform(26, 110), rng.uniform(0, 360))
    if len(poses) >= 44 or not libre(x, z, 2) or any(math.hypot(x - a, z - b) < 9 for a, b in poses):
        continue
    poses.append((x, z))
    brisee = rng.random() < 0.45
    h = rng.randint(3, 6) if brisee else rng.randint(8, 15)
    for a, b in ((-1, 0), (1, 0), (0, -1), (0, 1), (0, 0)):
        put(x + a, SOL + 1, z + b, QB)
    for y in range(SOL + 2, SOL + 1 + h):
        put(x, y, z, QP)
    if brisee:
        for _k in range(4):
            a, b = rng.randint(-3, 3), rng.randint(-3, 3)
            if vide((x + a, SOL + 1, z + b)) and (x + a, z + b) not in occ:
                put(x + a, SOL + 1, z + b, QP if rng.random() < 0.5 else S("diorite"))
    else:
        put(x, SOL + 1 + h, z, CUIV_C)
        put(x, SOL + 2 + h, z, LUM)


def nuage(cx, cz, y0, rx, ry):
    cells = list(disque(cx, cz, rx))
    for x, z in cells:
        for a in (-3, 0, 3):
            for b in (-3, 0, 3):
                if (x + a, z + b) in hautes:
                    return False
        if dist(x, z) >= R_MUR - 1:
            return False
    for x, z in cells:
        d = math.hypot(x + 0.5 - cx, z + 0.5 - cz) / rx
        e = int(ry * (1 - d * d) * (0.6 + 0.8 * bruit(x, z, 4, 41)))
        for y in range(y0 - e // 2, y0 + e + 1):
            put(x, y, z, LAINE)
    return True


n_nuages = 0
for _ in range(500):
    px, pz = point(rng.uniform(26, 112), rng.uniform(0, 360))
    y0 = rng.choice((SOL + 28, SOL + 44, SOL + 60))
    if n_nuages < 34 and nuage(px, pz, y0, rng.uniform(5, 11), rng.uniform(2, 4)):
        n_nuages += 1

# nappes de nuage au sol, sur les cases libres
for x in range(SX):
    for z in range(SZ):
        if libre(x, z) and bruit(x, z, 9, 42) > 0.74:
            put(x, SOL + 1, z, LAINE)
            if bruit(x, z, 9, 42) > 0.84:
                put(x, SOL + 2, z, LAINE)

# reverberes le long des avenues
for g in PORTES:
    for al in range(30, 112, 12):
        for sp in (-5, 4):
            x, z = gl(al, sp, g)
            if vide((x, SOL + 1, z)):
                for y in range(SOL + 1, SOL + 4):
                    put(x, y, z, QP)
                put(x, SOL + 4, z, LUM)

# 10 apparitions sur le chemin au pied du mur
for i in range(10):
    a = 9 + 36 * i
    x, z = cellule(117.5, a)
    for u, v in disque(x + 0.5, z + 0.5, 1.6):
        put(u, SOL, v, CUIV)
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
racine = nbtlib.File({
    "DataVersion": Int(DATA_VERSION),
    "size": List[Int]([Int(SX), Int(SY), Int(SZ)]),
    "palette": List[Compound](palette),
    "blocks": List[Compound](liste),
    "entities": List[Compound]([]),
}, gzipped=True)
for chemin in SORTIES:
    chemin.parent.mkdir(parents=True, exist_ok=True)
    racine.save(chemin)

ox, oy, oz = ORIGINE
if MONDE_OK:
    lignes = [f"# [claude-build] Efface / aplanit l'emprise de {NOM} : {ARGS.sous_sol} Y{oy}..{oy + SOL - 1}, "
              f"{ARGS.surface} Y{oy + SOL}, air Y{oy + SOL + 1}..{oy + SY - 1}.",
              f"# Se placer pres du centre ({CENTRE_MONDE[0]} ~ {CENTRE_MONDE[1]}). 2 /fill par couche (< 32768 blocs)."]
    for y in range(oy, oy + SY):
        bloc = (f"minecraft:{ARGS.surface}" if y == oy + SOL else
                f"minecraft:{ARGS.sous_sol}" if y < oy + SOL else "minecraft:air")
        lignes.append(f"fill {ox} {y} {oz} {ox + SX // 2 - 1} {y} {oz + SZ - 1} {bloc}")
        lignes.append(f"fill {ox + SX // 2} {y} {oz} {ox + SX - 1} {y} {oz + SZ - 1} {bloc}")
    lignes.append(f'tellraw @s {{"text":"Arene {NOM} effacee","color":"aqua"}}')
    FONCTION.parent.mkdir(parents=True, exist_ok=True)
    FONCTION.write_text("\n".join(lignes) + "\n", encoding="utf-8")
    mcmeta = DATAPACK / "pack.mcmeta"
    if not mcmeta.exists():
        mcmeta.write_text('{"pack": {"pack_format": 48, "description": "Atelier [claude-build]"}}\n', encoding="utf-8")

# cibles pour le controle d'acces
SALLES.sort()
depart = gl(100, 0, 0)
x_w, z_w = gl(118, 16, 0)
while dist(x_w, z_w) < 123:
    x_w += 1
CIBLES_ACCES["chemin de ronde"] = [x_w, H_MUR + 1, z_w]
CIBLES_ACCES.update({f"salle {n}": list(p) for n, p, _ in SALLES})
CIBLES_ACCES.update({f"apparition {i + 1}": list(p) for i, p in enumerate(APPARITIONS)})
CIBLES.write_text(json.dumps({"_marque": "[claude-build]", "sol": SOL, "depart": [depart[0], SOL + 1, depart[1]],
                              "cibles": CIBLES_ACCES}, ensure_ascii=False, indent=1), encoding="utf-8")

md = ["<!-- [claude-build] genere par sources/col_vents.py -->", "# Salles cachées — Le Col des Vents", "",
      f"Monde `saves/{ARGS.monde}`, arène centrée en X={CENTRE_MONDE[0]} Z={CENTRE_MONDE[1]}, sol à Y={ARGS.sol}. "
      "Coordonnées = bloc où poser le coffre (case vide).", "",
      "| N° | Coffre (X Y Z) | Accès |", "|---|---|---|"]
for num, (x, y, z), acces in SALLES:
    wx, wy, wz = monde(x, y, z)
    md.append(f"| {num} | {wx} {wy} {wz} | {acces} |")
md += ["", "## Apparitions (10)", ""]
md += [f"- {i + 1} : {' '.join(str(v) for v in monde(*p))}" for i, p in enumerate(APPARITIONS)]
LISTE_SALLES.write_text("\n".join(md) + "\n", encoding="utf-8")

# plan vu de dessus
try:
    from PIL import Image, ImageDraw
    COUL = {"calcite": (225, 225, 220), "smooth_quartz": (240, 236, 230), "quartz_bricks": (232, 228, 220),
            "white_wool": (250, 250, 250), "snow_block": (245, 250, 252), "polished_diorite": (195, 195, 195),
            "diorite": (190, 190, 190), "polished_andesite": (135, 138, 136), "oxidized_cut_copper": (80, 165, 135),
            "oxidized_chiseled_copper": (70, 185, 150), "oxidized_copper": (85, 165, 140),
            "light_blue_stained_glass": (120, 190, 240), "white_stained_glass": (210, 225, 235),
            "sea_lantern": (170, 230, 230), "tuff": (105, 105, 95), "andesite": (130, 130, 130),
            "bubble_column": (40, 90, 230), "soul_sand": (90, 70, 55), "powder_snow": (205, 230, 255)}
    E = 4
    img = Image.new("RGB", (SX * E + 20, SZ * E + 60), (24, 34, 52))
    dr = ImageDraw.Draw(img)
    hauts = {}
    for (x, y, z), s in blocs.items():
        if s != AIR and y > hauts.get((x, z), (-99, None))[0]:
            hauts[(x, z)] = (y, s[0].split(":")[1])
    for (x, z), (y, n) in hauts.items():
        c = COUL.get(n, (150, 150, 150))
        k = 0.62 + 0.38 * min(1, max(0, (y - SOL) / 45))
        dr.rectangle([10 + x * E, 30 + z * E, 10 + x * E + E - 1, 30 + z * E + E - 1], fill=tuple(int(v * k) for v in c))
    for num, (x, y, z), _ in SALLES:
        dr.ellipse([10 + x * E - 9, 30 + z * E - 9, 10 + x * E + 9, 30 + z * E + 9], outline=(230, 40, 90), width=3)
        dr.text((10 + x * E + 11, 30 + z * E - 6), str(num), fill=(230, 40, 90))
    dr.text((10, 8), "LE COL DES VENTS - vue de dessus (nord en haut). Cercles = salles cachees", fill=(220, 235, 255))
    img.save(PLAN)
except ImportError:
    print("Pillow absent : pas de plan")

print(f"Arene ecrite : {len(liste)} blocs, palette {len(palette)}, taille {SX}x{SY}x{SZ}")
print(f"Colonnes {len(poses)}, nuages {n_nuages}, monde trouve : {MONDE_OK}")
for num, (x, y, z), acces in SALLES:
    print(f"  salle {num}: coffre {monde(x, y, z)}  -- {acces}")
print(f"POSE : /tp @s {CENTRE_MONDE[0]} {ARGS.sol + 70} {CENTRE_MONDE[1]}   puis   "
      f"/place template atelier:{NOM} {ORIGINE[0]} {ORIGINE[1]} {ORIGINE[2]}")
