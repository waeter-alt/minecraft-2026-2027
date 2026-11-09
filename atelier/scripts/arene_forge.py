# [claude-build]
"""
arene_forge.py - Arene n.2 theme FEU / LAVE : "La Forge" (caldeira en gradins).
Cercle de 126 blocs de diametre (rayon 63, circonference 395.8). Pour ~10 joueurs en PvP.

Difference avec arene_feu : ici l'arene est une CUVETTE qui descend vers un lac de lave.
  - T0 (bord, Y188)  : anneau pave, 10 apparitions, braseros ;
  - T1 (Y184)        : forges, enclumes, piliers ;
  - T2 (Y180)        : roche chaude, pics d'obsidienne ;
  - lac de lave central, 3 ponts vers une ile avec une TOUR-FORGE (echelle interieure, sommet pour tirer) ;
  - 6 cascades de lave tombant des falaises dans des bassins ;
  - pierres de gue dans le lac (parcours risque) ;
  - mur d'enceinte de 12 blocs.
PASSAGES SECRETS :
  - 3 rideaux de lianes dans la falaise T0/T1 -> tunnel annulaire cache sous T0 ;
  - de ce tunnel : 3 echelles vers le sommet du mur, 2 puits vers le reseau profond ;
  - reseau profond : anneau sous T2 (3 trappes cachees en surface) + 3 galeries SOUS LE LAC
    vers une chambre sous l'ile, qui remonte par l'echelle dans la tour (trappe) ;
  - 3 colonnes de lianes grimpantes vers le chemin de ronde.

Pose (coordonnees absolues, centre X=200 Z=0) :
  /tp @s 200 230 0
  /place template atelier:arene_forge 137 172 -63
Lancer : .venv\\Scripts\\python.exe sources\\arene_forge.py
"""
import math
import random
from collections import Counter
from pathlib import Path

import nbtlib
from nbtlib import Compound, Int, List, String

NOM = "arene_forge"
RACINE = Path(__file__).resolve().parent.parent
DATAPACK = RACINE / "launcher/instances/Build-1.21.1/minecraft/saves/Atelier/datapacks/atelier"
SORTIES = [DATAPACK / "data/atelier/structure" / f"{NOM}.nbt", RACINE / "exports" / f"{NOM}.nbt"]
FONCTION = DATAPACK / "data/atelier/function" / f"{NOM}_effacer.mcfunction"
PLAN = RACINE / "exports" / f"{NOM}_plan.png"

DATA_VERSION = 3955
R = 63
C = R
SX = SZ = 2 * R + 1      # 127
SY = 36
SOL = 16                 # y local du terrain d'origine (= Y188 monde)
CENTRE_MONDE = (200, 0)
ORIGINE = (CENTRE_MONDE[0] - C, 188 - SOL, CENTRE_MONDE[1] - C)   # (137, 172, -63)
rng = random.Random(4242)


# ---------------------------------------------------------------- outils
def S(nom, **p):
    nom = nom if ":" in nom else "minecraft:" + nom
    return (nom, tuple(sorted((k, str(v).lower() if isinstance(v, bool) else str(v)) for k, v in p.items())))


AIR = S("air")
blocs = {}


def put(x, y, z, s):
    if not (0 <= x < SX and 0 <= y < SY and 0 <= z < SZ):
        raise ValueError(f"hors gabarit : {(x, y, z)} {s[0]}")
    blocs[(x, y, z)] = s


def nom(p):
    s = blocs.get(p)
    return None if s is None else s[0].split(":")[1]


def dist(x, z):
    return math.hypot(x - C, z - C)


def angle(x, z):
    return math.degrees(math.atan2(z - C, x - C)) % 360


def adiff(a, b):
    return abs((a - b + 180) % 360 - 180)


def polar(r, a):
    a = math.radians(a)
    return C + r * math.cos(a), C + r * math.sin(a)


def ipolar(r, a):
    x, z = polar(r, a)
    return round(x), round(z)


def proj(x, z, a):
    a = math.radians(a)
    dx, dz = x - C, z - C
    return dx * math.cos(a) + dz * math.sin(a), abs(-dx * math.sin(a) + dz * math.cos(a))


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
    top = a + (b - a) * sx
    return top + ((c + (d - c) * sx) - top) * sz


def choix(table, u):
    tot = sum(p for p, _ in table)
    u *= tot
    for p, s in table:
        if u < p:
            return s
        u -= p
    return table[-1][1]


def disque(cx, cz, r):
    for x in range(math.floor(cx - r), math.ceil(cx + r) + 1):
        for z in range(math.floor(cz - r), math.ceil(cz + r) + 1):
            if math.hypot(x - cx, z - cz) <= r:
                yield x, z


# ---------------------------------------------------------------- blocs
LAVE = S("lava")
LAVE_CHUTE = S("lava", level=8)
TRAPPE = S("crimson_trapdoor", facing="north", half="top", open=False)
FENCE = S("nether_brick_fence")
LANTERNE = S("soul_lantern", hanging=False)
PAVE_T0 = [(70, S("polished_blackstone_bricks")), (20, S("cracked_polished_blackstone_bricks")),
           (10, S("chiseled_polished_blackstone"))]
SOL_T1 = [(40, S("blackstone")), (25, S("basalt", axis="y")), (15, S("polished_blackstone")),
          (12, S("smooth_basalt")), (8, S("magma_block"))]
SOL_T2 = [(55, S("netherrack")), (15, S("magma_block")), (15, S("crimson_nylium")), (10, S("blackstone")),
          (5, S("nether_wart_block"))]
FALAISE = [(45, S("blackstone")), (35, S("basalt", axis="y")), (12, S("magma_block")), (8, S("gilded_blackstone"))]
MUR = [(60, S("polished_blackstone_bricks")), (20, S("cracked_polished_blackstone_bricks")),
       (12, S("blackstone")), (8, S("gilded_blackstone"))]
TUN_PAROI = [(50, S("blackstone")), (30, S("polished_blackstone_bricks")), (20, S("basalt", axis="y"))]
TUN_SOL = [(80, S("polished_blackstone_bricks")), (20, S("cracked_polished_blackstone_bricks"))]

# ---------------------------------------------------------------- plan
R_ILE, R_LAC, R_T2, R_T1, R_T0 = 6.5, 20, 38, 52, 59      # bornes exterieures
TOP = {"ile": 8, "lac": 5, "T2": 8, "T1": 12, "T0": 16, "mur": 16}
H_MUR = 27
A_PONTS = [90, 210, 330]
A_RAMPES01 = [0, 120, 240]          # T0 -> T1
A_RAMPES12 = [60, 180, 300]         # T1 -> T2
A_CASC01 = [60, 180, 300]           # cascades falaise T0/T1
A_CASC12 = [0, 120, 240]            # cascades falaise T1/T2
A_RIDEAUX = [30, 150, 270]          # rideaux de lianes falaise T0/T1
A_PUITS_MUR = [90, 210, 330]        # tunnel T0 -> echelle vers le sommet du mur
A_GRIMPE = [0, 120, 240]            # lianes grimpantes vers le chemin de ronde
A_TRAPPES_T2 = [40, 160, 280]       # trappes cachees sur T2 -> anneau profond
A_GALERIES = [30, 150, 270]         # galeries sous le lac
A_LIAISONS = [0, 180]               # puits tunnel T0 <-> reseau profond
SPAWNS = [18 + 36 * k for k in range(10)]
R_SPAWN = 55.5


def zone(d):
    if d < R_ILE:
        return "ile"
    if d < R_LAC:
        return "lac"
    if d < R_T2:
        return "T2"
    if d < R_T1:
        return "T1"
    if d < R_T0:
        return "T0"
    return "mur"


def hauteur(x, z):
    d = dist(x, z)
    zn = zone(d)
    top = TOP[zn]
    for a in A_RAMPES01:
        t, p = proj(x, z, a)
        if p <= 1.5 and 48 <= t < 52:
            return 13 + math.floor(t - 48), "rampe"
    for a in A_RAMPES12:
        t, p = proj(x, z, a)
        if p <= 1.5 and 34 <= t < 38:
            return 9 + math.floor(t - 34), "rampe"
    return top, zn


# ================================================================ 1. relief en gradins
for x in range(SX):
    for z in range(SZ):
        d = dist(x, z)
        if d > R + 0.5:
            continue
        top, zn = hauteur(x, z)
        if zn == "lac":
            put(x, 5, z, S("blackstone") if h01(x, 5, z, 1) < 0.7 else S("magma_block"))
            put(x, 6, z, LAVE)
            for y in range(7, SOL + 1):
                put(x, y, z, AIR)
            continue
        for y in range(max(0, top - 4), top + 1):
            u = h01(x, y, z, 2)
            if y < top:
                s = choix(FALAISE, u)
            elif zn == "T0" or zn == "mur":
                s = choix(PAVE_T0, u)
            elif zn == "T1":
                s = choix(SOL_T1, u)
            elif zn == "T2":
                s = choix(SOL_T2, u) if bruit(x, z, 9, 5) > 0.35 else choix(SOL_T1, u)
            elif zn == "ile":
                s = S("chiseled_polished_blackstone") if (x + z) % 3 == 0 else S("polished_blackstone")
            else:  # rampe
                s = choix(PAVE_T0, u)
            put(x, y, z, s)
        for y in range(top + 1, SOL + 1):
            put(x, y, z, AIR)

# ================================================================ 2. ponts + pierres de gue
for a in A_PONTS:
    for x in range(SX):
        for z in range(SZ):
            t, p = proj(x, z, a)
            if R_ILE - 1 <= t <= R_LAC + 0.5 and p <= 2.2 and zone(dist(x, z)) == "lac":
                put(x, 8, z, S("polished_blackstone_bricks") if p <= 1.2 else S("polished_blackstone_brick_wall"))
                if p > 1.2:
                    put(x, 9, z, S("chain", axis="y") if h01(x, 9, z, 3) < 0.5 else LANTERNE)
GUE = []
while len(GUE) < 9:
    x, z = ipolar(rng.uniform(9, 18), rng.uniform(0, 360))
    if any(proj(x, z, a)[1] < 4 for a in A_PONTS) or any(math.hypot(x - gx, z - gz) < 3 for gx, gz in GUE):
        continue
    GUE.append((x, z))
    for y in range(5, 8):
        put(x, y, z, S("basalt", axis="y"))
    put(x, 8, z, S("polished_basalt", axis="y"))

# ================================================================ 3. cascades de lave
CASCADES = []


def cascade(a, r_bord, y_haut, y_bas_pool):
    """source affleurante sur le gradin haut, chute le long de la falaise, bassin sur le gradin bas"""
    # source : case du gradin haut qui n'a QU'UNE voisine plus basse (la chute)
    ex, ez = ipolar(r_bord + 0.5, a)
    cands = []
    for x in range(ex - 3, ex + 4):
        for z in range(ez - 3, ez + 4):
            if hauteur(x, z)[0] != y_haut:
                continue
            vois = [(x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)]
            bas = [v for v in vois if hauteur(*v)[0] < y_haut]
            if len(bas) == 1 and hauteur(*bas[0])[0] == y_bas_pool:
                cands.append((proj(x, z, a)[1], x, z, bas[0]))
    cands.sort()
    _, sx, sz, col = cands[0]
    cx, cz = col
    put(sx, y_haut, sz, LAVE)
    for y in range(y_bas_pool + 1, y_haut + 1):
        put(cx, y, cz, LAVE_CHUTE)
    pc = polar(r_bord - 2.2, a)
    for x, z in disque(pc[0], pc[1], 3.4):
        if hauteur(x, z)[0] != y_bas_pool:
            continue
        if math.hypot(x - pc[0], z - pc[1]) <= 2.3 or (x, z) == col or (x, z) in (
                (cx + 1, cz), (cx - 1, cz), (cx, cz + 1), (cx, cz - 1)) and hauteur(x, z)[0] == y_bas_pool \
                and math.hypot(x - pc[0], z - pc[1]) <= 3:
            put(x, y_bas_pool, z, LAVE)
            put(x, y_bas_pool - 1, z, S("blackstone"))
        else:
            put(x, y_bas_pool, z, S("magma_block"))
    CASCADES.append((sx, sz))


for a in A_CASC01:
    cascade(a, R_T1, 16, 12)
for a in A_CASC12:
    cascade(a, R_T2, 12, 8)

# ================================================================ 4. tour-forge sur l'ile
for x, z in list(disque(C, C, 3.5)):
    d = dist(x, z)
    a = angle(x, z)
    for y in range(9, 30):
        if d <= 2.3:
            put(x, y, z, AIR)
            continue
        if y <= 10 and any(adiff(a, p) < 28 for p in A_PONTS):
            put(x, y, z, AIR)                                          # portes
            continue
        if y in (15, 21, 27) and any(adiff(a, w) < 15 for w in A_GALERIES):
            s = S("iron_bars")
        elif y in (13, 19, 25):
            s = S("red_nether_bricks")
        elif y % 6 == 0 and h01(x, y, z, 4) < 0.5:
            s = S("shroomlight")
        else:
            s = choix(MUR, h01(x, y, z, 5))
        put(x, y, z, s)
for x, z in disque(C, C, 4.5):
    put(x, 30, z, S("polished_blackstone_bricks"))
    if dist(x, z) >= 3.6 and (x + z) % 2 == 0:
        put(x, 31, z, S("polished_blackstone_brick_wall"))
for a in (45, 135, 225, 315):
    x, z = ipolar(4.2, a)
    put(x, 32, z, LANTERNE) if (x, 31, z) in blocs else put(x, 31, z, LANTERNE)
# postes de forge autour de la tour
for a in A_GALERIES:
    for k, (r, s) in enumerate([(5.2, S("anvil", facing="north")), (5.6, S("blast_furnace", facing="north", lit=True)),
                                (5.0, S("lava_cauldron"))]):
        x, z = ipolar(r, a + (k - 1) * 14)
        put(x, 9, z, s)

# ================================================================ 5. mur d'enceinte
for x in range(SX):
    for z in range(SZ):
        d = dist(x, z)
        if not (R_T0 <= d <= R + 0.5):
            continue
        a = angle(x, z)
        arc = math.radians(a) * 62
        for y in range(SOL, H_MUR + 1):
            u = h01(x, y, z, 6)
            if y == H_MUR:
                s = S("polished_blackstone_bricks")
            elif adiff(a, round(a / 20) * 20) < 1.4:
                s = S("red_nether_bricks") if y % 3 else S("gilded_blackstone")
            elif y in (19, 23):
                s = S("red_nether_bricks")
            elif d < 60 and y in (21,) and int(arc) % 7 == 0:
                s = S("shroomlight")
            else:
                s = choix(MUR, u)
            put(x, y, z, s)
        if d >= 62.3 and int(arc) % 3 < 2:
            put(x, H_MUR + 1, z, S("polished_blackstone_bricks"))
            if int(arc) % 15 == 0:
                put(x, H_MUR + 2, z, LANTERNE)
        elif d < 60:
            put(x, H_MUR + 1, z, FENCE)

# ================================================================ 6. tunnels secrets
NON_SOLIDES = {"air", "lava", "fire", "soul_fire", "weeping_vines", "weeping_vines_plant", "twisting_vines",
               "twisting_vines_plant", "ladder", "crimson_trapdoor", "soul_lantern", "chain", "nether_brick_fence",
               "iron_bars", "lava_cauldron", "anvil", "polished_blackstone_brick_wall"}
TUN = set()


def rayon(a, t0, t1, y0, y1, p_max=1.2):
    for x in range(SX):
        for z in range(SZ):
            t, p = proj(x, z, a)
            if t0 <= t <= t1 and p <= p_max:
                TUN.update((x, y, z) for y in range(y0, y1 + 1))


for x in range(SX):
    for z in range(SZ):
        d = dist(x, z)
        if 54.5 <= d < 56.5:
            TUN.update((x, y, z) for y in range(13, 16))          # anneau sous T0
        if 27 <= d < 29.5:
            TUN.update((x, y, z) for y in range(2, 5))            # anneau profond sous T2
        if d <= 3.5:
            TUN.update((x, y, z) for y in range(2, 5))            # chambre sous l'ile
for a in A_RIDEAUX:
    rayon(a, 51.6, 55, 13, 14, 1.0)
for a in A_GALERIES:
    rayon(a, 3, 28, 2, 4)
for a in A_LIAISONS:
    rayon(a, 28, 55.5, 2, 4)
for a in A_PUITS_MUR:
    rayon(a, 55, 61, 13, 15, 1.0)

for (x, y, z) in TUN:
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                q = (x + dx, y + dy, z + dz)
                if q in TUN or (q in blocs and nom(q) not in NON_SOLIDES and nom(q) != "air"):
                    continue
                if q in blocs and nom(q) == "air":
                    continue
                u = h01(*q, 8)
                if dy == -1 and (x + dx, y, z + dz) in TUN:
                    s = choix(TUN_SOL, u)
                elif dy == 1 and (x + dx, y, z + dz) in TUN:
                    s = S("shroomlight") if u < 0.07 else S("blackstone")
                else:
                    s = choix(TUN_PAROI, u)
                put(*q, s)
for q in TUN:
    put(*q, AIR)

# rideaux de lianes (face de la falaise T0/T1)
RIDEAUX = []
for a in A_RIDEAUX:
    for x in range(SX):
        for z in range(SZ):
            t, p = proj(x, z, a)
            if p <= 1.0 and 51.6 <= t <= 55 and dist(x, z) >= R_T1 and dist(x, z) < R_T1 + 1:
                put(x, 13, z, S("weeping_vines", age=10))
                put(x, 14, z, S("weeping_vines_plant"))
                RIDEAUX.append((x, z))

# ---------------------------------------------------------------- puits a echelle
FACE = {(-1, 0): "east", (1, 0): "west", (0, -1): "south", (0, 1): "north"}
ECHELLES = []


def puits(x, z, y0, y1, sup, trappes=()):
    sdx, sdz = sup
    for y in range(y0, y1 + 1):
        if y in trappes:
            put(x, y, z, TRAPPE)
            continue
        put(x, y, z, S("ladder", facing=FACE[sup]))
        sp = (x + sdx, y, z + sdz)
        ECHELLES.append(((x, y, z), sp))
        if nom(sp) is None or nom(sp) in NON_SOLIDES:
            put(*sp, S("polished_blackstone_bricks"))
        for ddx, ddz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + ddx, y, z + ddz)
            if (ddx, ddz) != sup and q not in blocs and y < SOL:
                put(*q, S("blackstone"))


def dir_ext(a):
    ca, sa = math.cos(math.radians(a)), math.sin(math.radians(a))
    return ((1 if ca > 0 else -1), 0) if abs(ca) >= abs(sa) else (0, (1 if sa > 0 else -1))


def case_mur(a):
    """case du mur dont la voisine vers l'exterieur reste dans le mur"""
    sup = dir_ext(a)
    best = None
    for x in range(SX):
        for z in range(SZ):
            if 59.5 <= dist(x, z) < 61.2 and 61.2 <= dist(x + sup[0], z + sup[1]) <= R:
                p = proj(x, z, a)[1]
                if proj(x, z, a)[0] > 0 and (best is None or p < best[0]):
                    best = (p, x, z)
    return best[1], best[2], sup


PUITS = {"tour": (C + 2, C)}
puits(C + 2, C, 2, 29, (1, 0), trappes=(8, 30))
for a in A_TRAPPES_T2:
    x, z = ipolar(28.2, a)
    PUITS[f"T2_{a}"] = (x, z)
    puits(x, z, 2, 8, dir_ext(a), trappes=(8,))
for a in A_LIAISONS:
    x, z = ipolar(55.5, a)
    PUITS[f"liaison_{a}"] = (x, z)
    puits(x, z, 2, 14, dir_ext(a))
for a in A_PUITS_MUR:
    x, z, sup = case_mur(a)
    PUITS[f"mur_{a}"] = (x, z)
    for y in range(13, 16):
        put(x, y, z, AIR)
    puits(x, z, 13, H_MUR, sup, trappes=(H_MUR,))
for a in range(45, 360, 90):
    x, z = ipolar(2.2, a)
    put(x, 2, z, LANTERNE)

# lianes grimpantes vers le chemin de ronde
GRIMPE = []
for a in A_GRIMPE:
    r = 58.6
    x, z = ipolar(r, a)
    while dist(x, z) >= R_T0:
        r -= 0.3
        x, z = ipolar(r, a)
    GRIMPE.append((x, z))
    for y in range(SOL + 1, H_MUR):
        put(x, y, z, S("twisting_vines_plant"))
    put(x, H_MUR, z, S("twisting_vines", age=10))
    for dx in (-2, -1, 0, 1, 2):
        for dz in (-2, -1, 0, 1, 2):
            if (x + dx, H_MUR + 1, z + dz) in blocs:
                put(x + dx, H_MUR + 1, z + dz, AIR)

# ================================================================ 7. apparitions + braseros
SPAWN_POS = []
for a in SPAWNS:
    cx, cz = ipolar(R_SPAWN, a)
    SPAWN_POS.append((cx, cz))
    for x, z in disque(cx, cz, 2.5):
        put(x, 16, z, S("polished_blackstone"))
    put(cx, 16, cz, S("gilded_blackstone"))
    ta = math.radians(a + 90)
    for s in (-3, 3):
        x, z = round(cx + s * math.cos(ta)), round(cz + s * math.sin(ta))
        put(x, 17, z, S("polished_blackstone_brick_wall"))
        put(x, 18, z, LANTERNE)
for k in range(1, 10):
    a = 36 * k
    if any(adiff(a, g) < 8 for g in A_GRIMPE):
        continue
    x, z = ipolar(56, a)
    put(x, 17, z, S("polished_blackstone_bricks"))
    put(x, 18, z, S("lava_cauldron"))

# ================================================================ 8. obstacles sur T1 et T2
reserve = set()


def reserver(cx, cz, r):
    reserve.update(disque(cx, cz, r))


for x in range(SX):
    for z in range(SZ):
        zn = hauteur(x, z)[1]
        if zn not in ("T1", "T2"):
            reserve.add((x, z))
        if any(proj(x, z, a)[1] <= 3.5 and proj(x, z, a)[0] > 0 for a in A_RAMPES01 + A_RAMPES12 + A_PONTS):
            reserve.add((x, z))
        d = dist(x, z)
        if R_T1 - 2 <= d < R_T1 or R_T2 - 2 <= d < R_T2 or R_LAC <= d < R_LAC + 2:
            reserve.add((x, z))                                     # bord des falaises degage
for a in A_CASC01:
    reserver(*polar(R_T1 - 2.2, a), 5)
for a in A_CASC12:
    reserver(*polar(R_T2 - 2.2, a), 5)
for a in A_RIDEAUX:
    reserver(*polar(R_T1 - 2, a), 4)
for k, (x, z) in PUITS.items():
    reserver(x, z, 3)
pris = []


def f_pilier(cx, cz, b):
    out = []
    for _ in range(rng.randint(1, 3)):
        x, z = cx + rng.randint(-1, 1), cz + rng.randint(-1, 1)
        h = rng.randint(3, 7)
        for y in range(b, b + h):
            out.append((x, y, z, S("basalt", axis="y")))
        out.append((x, b + h, z, S("polished_basalt", axis="y")))
    return out, 2


def f_forge(cx, cz, b):
    out = []
    a = rng.uniform(0, math.pi)
    ca, sa = math.cos(a), math.sin(a)
    for i in range(-2, 3):
        x, z = round(cx + i * ca), round(cz + i * sa)
        for y in range(b, b + 3):
            out.append((x, y, z, S("iron_bars") if y == b + 1 and i in (-1, 1) else choix(MUR, rng.random())))
    x, z = round(cx - 2 * sa), round(cz + 2 * ca)
    out.append((x, b, z, S("anvil", facing=rng.choice(["north", "east"]))))
    x, z = round(cx + 2 * sa), round(cz - 2 * ca)
    out.append((x, b, z, S("lava_cauldron")))
    out.append((round(cx + ca * 3), b, round(cz + sa * 3), S("blast_furnace", facing="north", lit=True)))
    return out, 4


def f_mur(cx, cz, b):
    out = []
    a = rng.uniform(0, math.pi)
    L = rng.randint(3, 6)
    vus = set()
    for i in range(-L * 2, L * 2 + 1):
        t = i / 4
        x, z = round(cx + t * math.cos(a)), round(cz + t * math.sin(a))
        if (x, z) in vus:
            continue
        vus.add((x, z))
        h = 2 + rng.randint(0, 2)
        for y in range(b, b + h):
            out.append((x, y, z, choix(MUR, rng.random())))
    return out, L / 2 + 1


def f_obsidienne(cx, cz, b):
    out = []
    rb, h = rng.uniform(1.3, 2.0), rng.randint(4, 8)
    for k in range(h):
        for x, z in disque(cx, cz, max(rb * (1 - k / h), 0.4)):
            out.append((x, b + k, z, S("crying_obsidian") if rng.random() < 0.15 else S("obsidian")))
    return out, rb + 1


def f_brasero(cx, cz, b):
    h = rng.randint(1, 2)
    out = [(cx, b + k, cz, S("polished_blackstone_bricks")) for k in range(h)]
    out.append((cx, b + h, cz, S("lava_cauldron")))
    return out, 1.5


def f_feu(cx, cz, b):
    out = []
    for x, z in disque(cx, cz, rng.uniform(1.2, 2.2)):
        out.append((x, b - 1, z, S("netherrack")))
        if rng.random() < 0.6:
            out.append((x, b, z, S("fire")))
    return out, 3


TYPES = {"T1": [(30, f_pilier), (25, f_forge), (20, f_mur), (15, f_brasero), (10, f_feu)],
         "T2": [(30, f_obsidienne), (25, f_pilier), (15, f_mur), (15, f_feu), (15, f_brasero)]}
compte = Counter()
for _ in range(3000):
    if len(pris) >= 55:
        break
    d = math.sqrt(rng.uniform(R_LAC ** 2, R_T1 ** 2))
    cx, cz = ipolar(d, rng.uniform(0, 360))
    top, zn = hauteur(cx, cz)
    if zn not in TYPES:
        continue
    f = choix(TYPES[zn], rng.random())
    out, r = f(cx, cz, top + 1)
    cols = {(x, z) for x, _, z, _ in out}
    if any(c in reserve or hauteur(*c)[0] != top for c in cols):
        continue
    if any(math.hypot(cx - px, cz - pz) < r + pr + 2 for px, pz, pr in pris):
        continue
    for x, y, z, s in out:
        put(x, y, z, s)
    pris.append((cx, cz, r))
    compte[f.__name__] += 1

# ================================================================ 9. verifications
def solide(p):
    n = nom(p)
    if n is None:
        return p[1] <= SOL
    return n not in NON_SOLIDES or n in ("anvil", "polished_blackstone_brick_wall", "lava_cauldron")


erreurs = []
for p, s in blocs.items():
    x, y, z = p
    n = s[0].split(":")[1]
    if s == LAVE:
        for q in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1), (x, y - 1, z)):
            if nom(q) != "lava" and not solide(q):
                erreurs.append(f"lave non contenue {p} -> {q} ({nom(q)})")
    elif s == LAVE_CHUTE:
        if nom((x, y - 1, z)) != "lava":
            erreurs.append(f"chute de lave sans arrivee {p}")
    elif n == "fire" and nom((x, y - 1, z)) not in ("netherrack", "magma_block"):
        erreurs.append(f"feu mal pose {p}")
    elif n in ("weeping_vines", "weeping_vines_plant"):
        if nom((x, y + 1, z)) != "weeping_vines_plant" and not solide((x, y + 1, z)):
            erreurs.append(f"liane pendante sans support {p}")
    elif n in ("twisting_vines", "twisting_vines_plant"):
        if nom((x, y - 1, z)) != "twisting_vines_plant" and not solide((x, y - 1, z)):
            erreurs.append(f"liane grimpante sans support {p}")
    elif n in ("anvil", "blast_furnace", "lava_cauldron", "soul_lantern") and not solide((x, y - 1, z)):
        erreurs.append(f"{n} pose dans le vide {p}")
for e, sp in ECHELLES:
    if not solide(sp):
        erreurs.append(f"echelle {e} sans support ({nom(sp)})")
# chaque cascade : la source ne doit deborder que vers sa chute
if erreurs:
    print(f"{len(erreurs)} ERREUR(S) :")
    for e in erreurs[:40]:
        print("  ", e)
    raise SystemExit(1)

# ================================================================ 10. ecriture
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
lignes = [f"# [claude-build] Efface l'arene {NOM} : herbe Y{oy}..{oy + SOL}, air Y{oy + SOL + 1}..{oy + SY - 1}.",
          f"# Se placer pres du centre ({CENTRE_MONDE[0]} ~ {CENTRE_MONDE[1]}) avant : /function atelier:{NOM}_effacer"]
for y in range(oy, oy + SY):
    bloc = "minecraft:grass_block" if y <= oy + SOL else "minecraft:air"
    lignes.append(f"fill {ox} {y} {oz} {ox + SX - 1} {y} {oz + SZ - 1} {bloc}")
lignes.append(f'tellraw @s {{"text":"Arene {NOM} effacee","color":"gold"}}')
FONCTION.parent.mkdir(parents=True, exist_ok=True)
FONCTION.write_text("\n".join(lignes) + "\n", encoding="utf-8")

# ================================================================ 11. plan vu de dessus
try:
    from PIL import Image, ImageDraw
    COUL = {
        "lava": (255, 110, 0), "magma_block": (190, 70, 20), "netherrack": (120, 40, 40),
        "crimson_nylium": (150, 25, 30), "nether_wart_block": (130, 10, 10), "red_nether_bricks": (90, 10, 15),
        "blackstone": (45, 40, 48), "basalt": (75, 75, 80), "smooth_basalt": (60, 60, 65),
        "polished_blackstone": (60, 55, 65), "polished_blackstone_bricks": (85, 78, 90),
        "cracked_polished_blackstone_bricks": (80, 72, 85), "chiseled_polished_blackstone": (95, 88, 100),
        "gilded_blackstone": (180, 140, 40), "obsidian": (25, 15, 40), "crying_obsidian": (70, 20, 120),
        "shroomlight": (255, 170, 80), "polished_basalt": (95, 95, 100), "fire": (255, 200, 40),
        "nether_brick_fence": (60, 30, 35), "soul_lantern": (90, 230, 240), "lava_cauldron": (255, 140, 30),
        "anvil": (70, 70, 70), "blast_furnace": (110, 110, 110), "iron_bars": (150, 150, 150),
        "twisting_vines": (20, 160, 150), "twisting_vines_plant": (20, 160, 150),
        "weeping_vines": (180, 30, 30), "weeping_vines_plant": (180, 30, 30),
        "crimson_trapdoor": (0, 255, 255), "polished_blackstone_brick_wall": (85, 78, 90), "chain": (60, 60, 70),
    }
    E = 6
    img = Image.new("RGB", (SX * E * 2 + 30, SZ * E + 70), (20, 20, 24))
    dr = ImageDraw.Draw(img)
    hauts = {}
    for (x, y, z), s in blocs.items():
        if s != AIR and y > hauts.get((x, z), (-1, None))[0]:
            hauts[(x, z)] = (y, s[0].split(":")[1])
    for (x, z), (y, n) in hauts.items():
        c = COUL.get(n, (128, 128, 128))
        k = 0.55 + 0.45 * min(1, max(0, (y - 5) / 14))
        dr.rectangle([x * E, 40 + z * E, x * E + E - 1, 40 + z * E + E - 1], fill=tuple(int(v * k) for v in c))
    o2 = SX * E + 30
    for x in range(SX):
        for z in range(SZ):
            if dist(x, z) <= R + 0.5:
                c = (40, 40, 44)
                if (x, 3, z) in TUN:
                    c = (230, 120, 40)
                elif (x, 14, z) in TUN:
                    c = (240, 200, 60)
                dr.rectangle([o2 + x * E, 40 + z * E, o2 + x * E + E - 1, 40 + z * E + E - 1], fill=c)
    for i, (x, z) in enumerate(SPAWN_POS):
        for o in (0, o2):
            dr.ellipse([o + x * E - 8, 40 + z * E - 8, o + x * E + 8, 40 + z * E + 8], outline=(255, 255, 0), width=2)
        dr.text((x * E - 3, 40 + z * E - 6), str(i + 1), fill=(255, 255, 0))
    marques = [(p, (0, 255, 255)) for p in PUITS.values()] + [(p, (60, 255, 120)) for p in GRIMPE]
    marques += [(ipolar(R_T1 + 0.5, a), (255, 80, 200)) for a in A_RIDEAUX]
    for (x, z), c in marques:
        for o in (0, o2):
            dr.rectangle([o + x * E - 6, 40 + z * E - 6, o + x * E + 6, 40 + z * E + 6], outline=c, width=2)
    dr.text((10, 8), "ARENE FORGE - surface (nord en haut, X vers la droite, plus clair = plus haut)", fill=(255, 220, 180))
    dr.text((o2, 8), "secret : tunnel sous T0 (jaune) / reseau profond sous le lac (orange)", fill=(255, 220, 180))
    dr.text((10, SZ * E + 48), "jaune = apparitions   cyan = trappes/echelles   rose = rideaux de lianes   vert = lianes grimpantes",
            fill=(230, 230, 230))
    img.save(PLAN)
except ImportError:
    print("Pillow absent : pas de plan")

print(f"Arene ecrite : {len(liste)} blocs, palette {len(palette)}, taille {SX}x{SY}x{SZ}")
print("Obstacles :", dict(compte))
print("Apparitions (X Y Z) :", " ".join(f"{i + 1}:({x + ox},{oy + 17},{z + oz})" for i, (x, z) in enumerate(SPAWN_POS)))
print("Puits/trappes (X Z) :", {k: (x + ox, z + oz) for k, (x, z) in PUITS.items()})
print("Rideaux (X Z) :", [(x + ox, z + oz) for x, z in {ipolar(R_T1 + 0.5, a) for a in A_RIDEAUX}])
print("Lianes grimpantes (X Z) :", [(x + ox, z + oz) for x, z in GRIMPE])
