# [claude-build]
"""
arene_feu.py - Arene de FEU (theme feu / lave / Nether), cercle de 200 blocs de diametre.

Pour une dizaine de joueurs en PvP. Contenu :
  - sol en 3 zones (roche noire, netherrack rouge, sable des ames bleu) ;
  - volcan central (cratere de lave, 4 escaliers) avec SALLE SECRETE a l'interieur ;
  - anneau de lave traverse par 4 ponts ;
  - 10 points d'apparition le long du mur ;
  - obstacles : piliers de basalte, ruines, champignons carmin geants, pics d'obsidienne,
    mares de lave, feux eternels, fissures de magma ;
  - mur d'enceinte de 18 blocs avec couloir secret interieur (meurtrieres) et chemin de ronde ;
  - PASSAGES SECRETS : reseau de tunnels souterrains (anneau + rayons), trappes cachees,
    rideaux de lianes dans le mur, lianes grimpantes vers le chemin de ronde.

Sortie : .nbt (structure vanilla, DataVersion 3955 = 1.21.1) dans le datapack ET dans exports/,
+ fonction atelier:arene_feu_effacer, + plan vu de dessus (exports/arene_feu_plan.png).

Pose (coordonnees absolues, centre de l'arene en X=0 Z=0) :
  /tp @s 0 230 0
  /place template atelier:arene_feu -100 178 -100
Lancer avec : .venv\\Scripts\\python.exe sources\\arene_feu.py
"""
import math
import random
from collections import Counter
from pathlib import Path

import nbtlib
from nbtlib import Compound, Int, List, String

NOM = "arene_feu"
RACINE = Path(__file__).resolve().parent.parent
DATAPACK = RACINE / "launcher/instances/Build-1.21.1/minecraft/saves/Atelier/datapacks/atelier"
SORTIES = [DATAPACK / "data/atelier/structure" / f"{NOM}.nbt", RACINE / "exports" / f"{NOM}.nbt"]
FONCTION = DATAPACK / "data/atelier/function" / f"{NOM}_effacer.mcfunction"
PLAN = RACINE / "exports" / f"{NOM}_plan.png"

DATA_VERSION = 3955
R = 100                 # rayon
C = R                   # centre local (x et z)
SX = SZ = 2 * R + 1     # 201
SY = 45                 # hauteur du gabarit
SOL = 10                # y local du sol (= Y 188 dans le monde)
ORIGINE = (-100, 178, -100)
GRAINE = 1337
rng = random.Random(GRAINE)


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


def dist(x, z):
    return math.hypot(x - C, z - C)


def angle(x, z):
    return math.degrees(math.atan2(z - C, x - C)) % 360


def adiff(a, b):
    return abs((a - b + 180) % 360 - 180)


def polar(r, a):
    a = math.radians(a)
    return C + r * math.cos(a), C + r * math.sin(a)


def proj(x, z, a):
    """(distance le long du rayon d'angle a, distance perpendiculaire)"""
    a = math.radians(a)
    dx, dz = x - C, z - C
    return dx * math.cos(a) + dz * math.sin(a), abs(-dx * math.sin(a) + dz * math.cos(a))


def cardinal(x, z):
    """(along, perp) par rapport aux 4 axes cardinaux"""
    dx, dz = abs(x - C), abs(z - C)
    return max(dx, dz), min(dx, dz)


def h01(x, y, z, graine=0):
    h = (x * 374761393 + y * 1442695041 + z * 668265263 + graine * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h ^= h >> 16
    return h / 0xFFFFFFFF


def bruit(x, z, cellule, graine):
    gx, gz = x / cellule, z / cellule
    i, j = math.floor(gx), math.floor(gz)
    fx, fz = gx - i, gz - j
    sx, sz = fx * fx * (3 - 2 * fx), fz * fz * (3 - 2 * fz)
    a, b = h01(i, 0, j, graine), h01(i + 1, 0, j, graine)
    c, d = h01(i, 0, j + 1, graine), h01(i + 1, 0, j + 1, graine)
    return (a + (b - a) * sx) + ((c + (d - c) * sx) - (a + (b - a) * sx)) * sz


def choix(table, u):
    total = sum(p for p, _ in table)
    u *= total
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


# ---------------------------------------------------------------- palettes
SOMBRE = [(45, S("blackstone")), (25, S("basalt", axis="y")), (18, S("smooth_basalt")),
          (8, S("polished_blackstone")), (4, S("magma_block"))]
ROUGE = [(76, S("netherrack")), (6, S("magma_block")), (8, S("nether_wart_block")), (10, S("red_nether_bricks"))]
AME = [(55, S("soul_soil")), (40, S("soul_sand")), (5, S("bone_block", axis="y"))]
VOLCAN = [(35, S("basalt", axis="y")), (30, S("blackstone")), (20, S("magma_block")), (13, S("netherrack")),
          (2, S("gilded_blackstone"))]
MUR = [(78, S("nether_bricks")), (17, S("cracked_nether_bricks")), (5, S("red_nether_bricks"))]
TUNNEL_PAROI = [(50, S("blackstone")), (30, S("polished_blackstone_bricks")), (15, S("basalt", axis="y")),
                (5, S("gilded_blackstone"))]
TUNNEL_SOL = [(80, S("polished_blackstone_bricks")), (20, S("cracked_polished_blackstone_bricks"))]
DALLE = [(85, S("polished_blackstone_bricks")), (15, S("cracked_polished_blackstone_bricks"))]
TRAPPE = S("crimson_trapdoor", facing="north", half="top", open=False)
FENCE = S("nether_brick_fence")
LANTERNE = S("soul_lantern", hanging=False)


def zone(x, z):
    n = bruit(x, z, 24, 11)
    return "sombre" if n < 0.38 else ("rouge" if n < 0.70 else "ame")


def bloc_sol(x, z):
    u = h01(x, SOL, z, 1)
    zn = zone(x, z)
    if zn == "sombre":
        return choix(SOMBRE, u)
    if zn == "rouge":
        if bruit(x, z, 7, 23) > 0.62:
            return S("crimson_nylium")
        return choix(ROUGE, u)
    return choix(AME, u)


# ---------------------------------------------------------------- constantes de plan
R_LAVE = (26, 31)              # anneau de lave
R_VOLCAN = 22
R_SALLE = 8                    # salle secrete du volcan
R_MUR = 95                     # debut du mur
R_COULOIR = (97, 99)           # couloir secret dans le mur
H_MUR = 28                     # sommet du mur (y local)
SPAWNS = [k * 36 for k in range(10)]
R_SPAWN = 86
A_TRAPPES_ANNEAU = [30, 102, 174, 246, 318]     # trappes vers le tunnel annulaire
R_TUNNEL = (58, 61)
A_RAYONS_INT = [45, 135, 225, 315]              # tunnels anneau -> chambre centrale
A_RAYONS_EXT = [18, 108, 198, 288]              # tunnels anneau -> puits dans le mur
A_RIDEAUX = [9, 81, 153, 225, 297]              # rideaux de lianes (entree du couloir du mur)
A_GRIMPE = [60, 168, 276]                       # lianes grimpantes vers le chemin de ronde
A_ENTREE_VOLCAN = 45


def h_cone(d):
    return SOL + round(16 * (R_VOLCAN - d) / 17)


def h_chemin(a):
    return 26 if a <= 5 else h_cone(a)


# ================================================================ 1. sol
for x in range(SX):
    for z in range(SZ):
        if dist(x, z) <= R + 0.5:
            put(x, SOL, z, bloc_sol(x, z))

# ================================================================ 2. anneau de lave + ponts + chemins
for x in range(SX):
    for z in range(SZ):
        d = dist(x, z)
        along, perp = cardinal(x, z)
        if R_LAVE[0] - 1 <= along <= R_LAVE[1] + 1 and perp <= 2 and R_LAVE[0] - 1 <= d <= R_LAVE[1] + 1.5:
            put(x, SOL, z, choix(DALLE, h01(x, SOL, z, 2)))          # pont
            if perp == 2 and R_LAVE[0] <= d <= R_LAVE[1]:
                put(x, SOL + 1, z, FENCE)
        elif R_LAVE[0] <= d <= R_LAVE[1]:
            put(x, SOL, z, S("lava"))
            put(x, SOL - 1, z, S("blackstone"))
        elif perp <= 1 and R_VOLCAN < along and d < R_MUR:
            put(x, SOL, z, choix(DALLE, h01(x, SOL, z, 2)))          # chemins cardinaux

# ================================================================ 3. volcan
for x, z in list(disque(C, C, R_VOLCAN)):
    d = dist(x, z)
    if d < 4.5:
        haut = 24
    elif d < 6:
        haut = 27
    else:
        haut = h_cone(d)
    for y in range(SOL, haut + 1):
        s = choix(VOLCAN, h01(x, y, z, 3)) if y >= haut - 1 else S("blackstone")
        put(x, y, z, s)
    if d < 4.5:
        put(x, 25, z, S("lava"))                                     # lac du cratere
    if 4.5 <= d < 6:
        put(x, 27, z, S("polished_blackstone_bricks") if h01(x, 27, z, 4) < 0.6 else S("magma_block"))

# escaliers d'acces au cratere (4 cardinaux, largeur 3)
FACE_MONTEE = {(1, 0): "west", (-1, 0): "east", (0, 1): "north", (0, -1): "south"}
for (sx, sz), facing in FACE_MONTEE.items():
    for a in range(5, R_VOLCAN + 1):
        for p in (-1, 0, 1):
            x, z = (C + sx * a + (p if sz else 0), C + sz * a + (p if sx else 0))
            top = h_chemin(a)
            for y in range(SOL, top):
                put(x, y, z, S("blackstone"))
            put(x, top, z, choix(DALLE, h01(x, top, z, 5)))
            for y in range(top + 1, 28):
                if (x, y, z) in blocs:
                    put(x, y, z, AIR)
            if a > 5 and h_chemin(a - 1) == top + 1:
                put(x, top + 1, z, S("polished_blackstone_brick_stairs", facing=facing, half="bottom"))

# salle secrete dans le volcan
for x, z in list(disque(C, C, R_SALLE + 1)):
    d = dist(x, z)
    for y in range(SOL + 1, SOL + 6):
        if d <= R_SALLE:
            put(x, y, z, AIR)
        else:
            u = h01(x, y, z, 6)
            s = S("gilded_blackstone") if u < 0.2 else S("polished_blackstone_bricks")
            if y == SOL + 4 and adiff(angle(x, z), 0) % 45 < 4:
                s = S("shroomlight")
            put(x, y, z, s)
    if d <= R_SALLE:
        put(x, SOL, z, S("polished_blackstone_bricks") if (x + z) % 2 else S("chiseled_polished_blackstone"))
put(C, SOL + 1, C, S("soul_campfire", lit=True, facing="north"))
for a in (45, 135, 225, 315):
    x, z = polar(6, a)
    put(round(x), SOL + 1, round(z), LANTERNE)
    put(round(x), SOL + 5, round(z), S("shroomlight"))

# galerie d'entree cachee (angle 45) avec rideau de lianes
for x in range(C - 25, C + 26):
    for z in range(C - 25, C + 26):
        t, perp = proj(x, z, A_ENTREE_VOLCAN)
        if 7.5 <= t <= R_VOLCAN + 0.8 and perp <= 1.0:
            put(x, SOL + 1, z, AIR)
            put(x, SOL + 2, z, AIR)
            if 17.5 <= t < 18.5:
                put(x, SOL + 1, z, S("weeping_vines", age=10))
                put(x, SOL + 2, z, S("weeping_vines_plant"))
                put(x, SOL + 3, z, S("blackstone"))

# ================================================================ 4. mur d'enceinte + couloir secret
for x in range(SX):
    for z in range(SZ):
        d = dist(x, z)
        if not (R_MUR <= d <= R + 0.5):
            continue
        a = angle(x, z)
        arc = a * math.pi / 180 * 99.5
        couloir = R_COULOIR[0] <= d < R_COULOIR[1]
        for y in range(SOL, H_MUR + 1):
            if couloir and SOL + 1 <= y <= SOL + 3:
                put(x, y, z, AIR)
                continue
            u = h01(x, y, z, 7)
            if y == SOL and couloir:
                s = choix(DALLE, u)
            elif y == SOL + 4 and couloir and int(arc) % 8 == 0:
                s = S("shroomlight")                                   # eclairage du couloir
            elif y == H_MUR:
                s = S("polished_blackstone_bricks")
            elif adiff(a, round(a / 15) * 15) < 0.9:
                s = S("chiseled_nether_bricks") if y % 4 == 0 else S("red_nether_bricks")   # contreforts
            elif y in (16, 17, 22, 23):
                s = S("red_nether_bricks")
            else:
                s = choix(MUR, u)
            if d < R_COULOIR[0]:                                         # face interieure
                if y == SOL + 2 and adiff(a, round(a / 5) * 5) < 0.6:
                    s = FENCE                                            # meurtrieres
                elif y in (19, 25) and adiff(a, round(a / 6) * 6 + 3) < 0.5:
                    s = S("shroomlight")
            put(x, y, z, s)
        # creneaux
        if d >= 98.8 and int(arc) % 3 < 2:
            put(x, H_MUR + 1, z, S("nether_bricks"))
            if int(arc) % 18 == 0:
                put(x, H_MUR + 2, z, LANTERNE)
        elif d < 96:
            put(x, H_MUR + 1, z, FENCE)

# rideaux de lianes : entrees du couloir secret
for x in range(SX):
    for z in range(SZ):
        d = dist(x, z)
        if R_MUR <= d < R_COULOIR[0] and any(adiff(angle(x, z), a) < 0.6 for a in A_RIDEAUX):
            put(x, SOL + 1, z, S("weeping_vines", age=10))
            put(x, SOL + 2, z, S("weeping_vines_plant"))

# lianes grimpantes vers le chemin de ronde
GRIMPE = []
for a in A_GRIMPE:
    x, z = (round(v) for v in polar(94.3, a))
    while dist(x, z) >= R_MUR:
        x, z = (round(v) for v in polar(dist(x, z) - 0.7, a))
    GRIMPE.append((x, z))
    for y in range(SOL + 1, H_MUR):
        put(x, y, z, S("twisting_vines_plant"))
    put(x, H_MUR, z, S("twisting_vines", age=10))
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if (x + dx, H_MUR + 1, z + dz) in blocs:
                put(x + dx, H_MUR + 1, z + dz, AIR)
            for k in (2, 3):
                if (x + dx * k, H_MUR + 1, z + dz * k) in blocs and blocs[(x + dx * k, H_MUR + 1, z + dz * k)] == FENCE:
                    put(x + dx * k, H_MUR + 1, z + dz * k, AIR)

# ================================================================ 5. points d'apparition
SPAWN_POS = []
for i, a in enumerate(SPAWNS):
    cx, cz = polar(R_SPAWN, a)
    cx, cz = round(cx), round(cz)
    SPAWN_POS.append((cx, cz))
    for x, z in disque(cx, cz, 3.5):
        put(x, SOL, z, S("polished_blackstone_bricks"))
    put(cx, SOL, cz, S("gilded_blackstone"))
    for dx, dz in ((2, 2), (2, -2), (-2, 2), (-2, -2)):
        put(cx + dx, SOL + 1, cz + dz, S("polished_blackstone_brick_wall"))
        put(cx + dx, SOL + 2, cz + dz, LANTERNE)
    # bouclier cote centre
    vers_c = math.degrees(math.atan2(C - cz, C - cx))
    for x, z in disque(cx, cz, 6.2):
        rr = math.hypot(x - cx, z - cz)
        if 5.2 <= rr and adiff(math.degrees(math.atan2(z - cz, x - cx)), vers_c) < 38:
            put(x, SOL + 1, z, S("polished_blackstone_bricks"))
            put(x, SOL + 2, z, S("polished_blackstone_brick_wall"))

# ================================================================ 6. tunnels souterrains
TUN = set()
for x in range(SX):
    for z in range(SZ):
        d = dist(x, z)
        if d <= 6:
            TUN.update((x, y, z) for y in range(2, 6))
        if R_TUNNEL[0] <= d < R_TUNNEL[1]:
            TUN.update((x, y, z) for y in range(2, 5))
        for a in A_RAYONS_INT:
            t, p = proj(x, z, a)
            if 5 <= t <= 59 and p <= 1.2:
                TUN.update((x, y, z) for y in range(2, 5))


def trouve_puits(a):
    """case du couloir (97<=d<99) dont la voisine vers l'exterieur est dans la peau exterieure du mur"""
    ex, ez = polar(98.3, a)
    ca = math.cos(math.radians(a))
    sa = math.sin(math.radians(a))
    dirs = [((1 if ca > 0 else -1), 0), (0, (1 if sa > 0 else -1))]
    if abs(sa) > abs(ca):
        dirs.reverse()
    meilleurs = []
    for x in range(round(ex) - 3, round(ex) + 4):
        for z in range(round(ez) - 3, round(ez) + 4):
            if not (R_COULOIR[0] <= dist(x, z) < R_COULOIR[1]):
                continue
            for rang, (sdx, sdz) in enumerate(dirs):
                if R_COULOIR[1] <= dist(x + sdx, z + sdz) <= R:
                    meilleurs.append((rang, proj(x, z, a)[1], x, z, (sdx, sdz)))
    meilleurs.sort()
    _, _, x, z, sup = meilleurs[0]
    return x, z, sup


PUITS_MUR = [trouve_puits(a) for a in A_RAYONS_EXT]
for a, (px, pz, _) in zip(A_RAYONS_EXT, PUITS_MUR):
    tmax = proj(px, pz, a)[0]
    for x in range(SX):
        for z in range(SZ):
            t, p = proj(x, z, a)
            if 59 <= t <= tmax + 0.5 and p <= 1.2:
                TUN.update((x, y, z) for y in range(2, 5))
    TUN.update((px, y, pz) for y in range(2, 5))

for (x, y, z) in TUN:
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                q = (x + dx, y + dy, z + dz)
                if q in TUN or q in blocs:
                    continue
                u = h01(*q, 8)
                if dy == -1 and (x + dx, y, z + dz) in TUN or q[1] == 1:
                    s = choix(TUNNEL_SOL, u)
                elif dy == 1 and (x + dx, y, z + dz) in TUN:
                    s = S("shroomlight") if u < 0.07 else S("blackstone")
                else:
                    s = choix(TUNNEL_PAROI, u)
                put(*q, s)
for q in TUN:
    put(*q, AIR)
for a in range(0, 360, 90):
    x, z = polar(3, a + 45)
    put(round(x), 2, round(z), LANTERNE)


# ---------------------------------------------------------------- puits a echelle
NON_SOLIDES = {"air", "lava", "fire", "soul_fire", "weeping_vines", "weeping_vines_plant", "twisting_vines",
               "twisting_vines_plant", "ladder", "crimson_trapdoor", "soul_lantern", "soul_campfire",
               "nether_brick_fence"}
FACE_ECHELLE = {(-1, 0): "east", (1, 0): "west", (0, -1): "south", (0, 1): "north"}
ECHELLES = []


def puits(x, z, y0, y1, sup, trappes=()):
    sdx, sdz = sup
    for y in range(y0, y1 + 1):
        if y in trappes:
            put(x, y, z, TRAPPE)
            continue
        put(x, y, z, S("ladder", facing=FACE_ECHELLE[sup]))
        ECHELLES.append(((x, y, z), (x + sdx, y, z + sdz)))
        sp = (x + sdx, y, z + sdz)
        actuel = blocs.get(sp)
        if actuel is None or actuel[0].split(":")[1] in NON_SOLIDES:
            put(*sp, S("polished_blackstone_bricks"))       # support plein (pilier dans le tunnel si besoin)
        if y < SOL:                                           # gaine du puits sous terre
            for ddx, ddz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (x + ddx, y, z + ddz)
                if q not in blocs and (ddx, ddz) != sup:
                    put(*q, S("blackstone"))


def dir_ext(a):
    ca, sa = math.cos(math.radians(a)), math.sin(math.radians(a))
    return ((1 if ca > 0 else -1), 0) if abs(ca) >= abs(sa) else (0, (1 if sa > 0 else -1))


PUITS_ANNEAU = []
for a in A_TRAPPES_ANNEAU:
    x, z = (round(v) for v in polar(59.5, a))
    PUITS_ANNEAU.append((x, z))
    puits(x, z, 2, SOL, dir_ext(a), trappes=(SOL,))
for (x, z, sup) in PUITS_MUR:
    puits(x, z, 2, H_MUR, sup, trappes=(SOL, H_MUR))
PUITS_SALLE = (C - 5, C)
puits(C - 5, C, 2, SOL, (-1, 0), trappes=(SOL,))

# ================================================================ 7. decor / obstacles
reserve = set()


def reserver(cx, cz, r):
    for x, z in disque(cx, cz, r):
        reserve.add((x, z))


for x in range(SX):
    for z in range(SZ):
        d = dist(x, z)
        along, perp = cardinal(x, z)
        if d < 34 or d > 90 or perp <= 3:
            reserve.add((x, z))
for cx, cz in SPAWN_POS:
    reserver(cx, cz, 8)
for cx, cz in PUITS_ANNEAU + GRIMPE:
    reserver(cx, cz, 4)
for a in A_RIDEAUX:
    reserver(*polar(92, a), 4)

pris = []          # (x, z, rayon)


def libre(cx, cz, r):
    if any(math.hypot(cx - x, cz - z) < r + rr + 2 for x, z, rr in pris):
        return False
    return all((x, z) not in reserve for x, z in disque(cx, cz, r + 1))


def f_piliers(cx, cz):
    out = []
    for _ in range(rng.randint(1, 4)):
        x, z = cx + rng.randint(-2, 2), cz + rng.randint(-2, 2)
        h = rng.randint(3, 10)
        for y in range(SOL + 1, SOL + h):
            out.append((x, y, z, S("magma_block") if rng.random() < 0.08 else S("basalt", axis="y")))
        out.append((x, SOL + h, z, S("polished_basalt", axis="y")))
    return out, 3


def f_ruine(cx, cz):
    out = []
    a = rng.uniform(0, math.pi)
    L = rng.randint(4, 8)
    vus = set()
    for i in range(-L * 2, L * 2 + 1):
        t = i / 4
        x, z = round(cx + t * math.cos(a)), round(cz + t * math.sin(a))
        if (x, z) in vus:
            continue
        vus.add((x, z))
        if rng.random() < 0.12:
            continue
        h = max(1, 3 + rng.randint(-1, 1) - (1 if abs(t) > L / 2 - 1 else 0))
        for y in range(SOL + 1, SOL + 1 + h):
            out.append((x, y, z, choix(MUR, rng.random())))
        if rng.random() < 0.35:
            out.append((x, SOL + 1 + h, z, FENCE))
    return out, L / 2 + 1


def f_champignon(cx, cz):
    out = []
    h = rng.randint(5, 8)
    rc = rng.choice((2, 2.6, 3))
    top = SOL + h
    for y in range(SOL + 1, top):
        out.append((cx, y, cz, S("crimson_stem", axis="y")))
    chapeau = set()
    for x, z in disque(cx, cz, rc):
        r = math.hypot(x - cx, z - cz)
        chapeau.add((x, top, z))
        if r <= rc - 1:
            chapeau.add((x, top + 1, z))
        if r > rc - 1:
            chapeau.add((x, top - 1, z))
            if rng.random() < 0.5:
                chapeau.add((x, top - 2, z))
    for (x, y, z) in chapeau:
        out.append((x, y, z, S("shroomlight") if rng.random() < 0.12 else S("nether_wart_block")))
    for (x, y, z) in chapeau:
        if (x, y - 1, z) not in chapeau and (x, z) != (cx, cz) and rng.random() < 0.3:
            L = rng.randint(1, 3)
            for k in range(1, L + 1):
                out.append((x, y - k, z, S("weeping_vines", age=10) if k == L else S("weeping_vines_plant")))
    return out, rc + 1


def f_obsidienne(cx, cz):
    out = []
    rb = rng.uniform(1.5, 2.3)
    h = rng.randint(5, 10)
    for k in range(h):
        r = rb * (1 - k / h)
        for x, z in disque(cx, cz, max(r, 0.4)):
            out.append((x, SOL + 1 + k, z, S("crying_obsidian") if rng.random() < 0.15 else S("obsidian")))
    return out, rb + 1


def f_mare(cx, cz):
    out = []
    r = rng.uniform(1.5, 3)
    for x, z in disque(cx, cz, r + 1.2):
        if math.hypot(x - cx, z - cz) <= r:
            out.append((x, SOL, z, S("lava")))
            out.append((x, SOL - 1, z, S("blackstone")))
        else:
            out.append((x, SOL, z, S("magma_block")))
    return out, r + 2


def f_feu(cx, cz):
    out = []
    ame = zone(cx, cz) == "ame"
    for x, z in disque(cx, cz, rng.uniform(1.5, 2.5)):
        out.append((x, SOL, z, S("soul_soil") if ame else S("netherrack")))
        if rng.random() < 0.6:
            out.append((x, SOL + 1, z, S("soul_fire") if ame else S("fire")))
    return out, 3


def f_fissure(cx, cz):
    out = []
    a = rng.uniform(0, 2 * math.pi)
    x, z = float(cx), float(cz)
    for _ in range(rng.randint(8, 14)):
        out.append((round(x), SOL, round(z), S("magma_block")))
        a += rng.uniform(-0.6, 0.6)
        x, z = x + math.cos(a), z + math.sin(a)
    return out, 7


TYPES = [(30, f_piliers), (24, f_ruine), (14, f_champignon), (10, f_obsidienne),
         (9, f_mare), (8, f_feu), (5, f_fissure)]
compte = Counter()
for essai in range(4000):
    if len(pris) >= 120:
        break
    d = math.sqrt(rng.uniform(34 ** 2, 90 ** 2))
    cx, cz = (round(v) for v in polar(d, rng.uniform(0, 360)))
    f = choix(TYPES, rng.random())
    etat = rng.getstate()
    out, r = f(cx, cz)
    cols = {(x, z) for x, _, z, _ in out}
    if not libre(cx, cz, r) or any(c in reserve for c in cols) or any(
            any(math.hypot(x - px, z - pz) < pr + 1 for px, pz, pr in pris) for x, z in cols):
        continue
    for x, y, z, s in out:
        put(x, y, z, s)
    pris.append((cx, cz, r))
    compte[f.__name__] += 1

# ruines qui masquent les trappes de l'anneau (cote centre)
for (x0, z0), a in zip(PUITS_ANNEAU, A_TRAPPES_ANNEAU):
    cx, cz = polar(57, a)
    ta = math.radians(a + 90)
    for i in range(-2, 3):
        x, z = round(cx + i * math.cos(ta)), round(cz + i * math.sin(ta))
        for y in range(SOL + 1, SOL + 3 + (1 if i == 0 else 0)):
            put(x, y, z, choix(MUR, h01(x, y, z, 9)))


# ================================================================ 8. verifications internes

def nom(p):
    s = blocs.get(p)
    return None if s is None else s[0].split(":")[1]


def solide(p):
    n = nom(p)
    if n is None:
        return p[1] <= SOL      # hors gabarit : terrain du monde (herbe) jusqu'au sol, air au-dessus
    return n not in NON_SOLIDES


erreurs = []
for p, s in blocs.items():
    x, y, z = p
    n = s[0].split(":")[1]
    if n == "lava":
        for q in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1), (x, y - 1, z)):
            if nom(q) != "lava" and not solide(q):
                erreurs.append(f"lave non contenue en {p} vers {q} ({nom(q)})")
    elif n in ("fire",):
        if nom((x, y - 1, z)) not in ("netherrack", "magma_block"):
            erreurs.append(f"feu sans netherrack en {p}")
    elif n == "soul_fire":
        if nom((x, y - 1, z)) not in ("soul_soil", "soul_sand"):
            erreurs.append(f"feu des ames mal pose en {p}")
    elif n in ("weeping_vines", "weeping_vines_plant"):
        au = (x, y + 1, z)
        if nom(au) != "weeping_vines_plant" and not solide(au):
            erreurs.append(f"liane pendante sans support en {p}")
    elif n in ("twisting_vines", "twisting_vines_plant"):
        de = (x, y - 1, z)
        if nom(de) != "twisting_vines_plant" and not solide(de):
            erreurs.append(f"liane grimpante sans support en {p}")
for e, sp in ECHELLES:
    if not solide(sp):
        erreurs.append(f"echelle {e} sans support ({nom(sp)})")
if erreurs:
    print(f"{len(erreurs)} ERREUR(S) :")
    for e in erreurs[:30]:
        print("  ", e)
    raise SystemExit(1)

# ================================================================ 9. ecriture du .nbt
palette, index = [], {}
liste = []
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

# fonction d'effacement (remet le terrain plat : herbe jusqu'a Y188, air au-dessus)
ox, oy, oz = ORIGINE
lignes = [f"# [claude-build] Efface l'arene {NOM} : remet herbe (Y{oy}..{oy + SOL}) et air (Y{oy + SOL + 1}..{oy + SY - 1}).",
          "# Lancer depuis le centre (le volume doit etre charge) : /function atelier:" + f"{NOM}_effacer"]
for y in range(oy, oy + SY):
    bloc = "minecraft:grass_block" if y <= oy + SOL else "minecraft:air"
    lignes.append(f"fill {ox} {y} {oz} {ox + C} {y} {oz + SZ - 1} {bloc}")
    lignes.append(f"fill {ox + C + 1} {y} {oz} {ox + SX - 1} {y} {oz + SZ - 1} {bloc}")
lignes.append(f'tellraw @s {{"text":"Arene {NOM} effacee","color":"gold"}}')
FONCTION.parent.mkdir(parents=True, exist_ok=True)
FONCTION.write_text("\n".join(lignes) + "\n", encoding="utf-8")

# ================================================================ 10. plan vu de dessus
try:
    from PIL import Image, ImageDraw

    COUL = {
        "lava": (255, 110, 0), "magma_block": (190, 70, 20), "netherrack": (120, 40, 40),
        "crimson_nylium": (150, 25, 30), "nether_wart_block": (130, 10, 10), "red_nether_bricks": (90, 10, 15),
        "blackstone": (45, 40, 48), "basalt": (75, 75, 80), "smooth_basalt": (60, 60, 65),
        "polished_blackstone": (60, 55, 65), "polished_blackstone_bricks": (85, 78, 90),
        "cracked_polished_blackstone_bricks": (80, 72, 85), "chiseled_polished_blackstone": (95, 88, 100),
        "gilded_blackstone": (180, 140, 40), "soul_soil": (80, 62, 50), "soul_sand": (95, 75, 60),
        "bone_block": (220, 215, 195), "nether_bricks": (60, 30, 35), "cracked_nether_bricks": (55, 28, 32),
        "chiseled_nether_bricks": (70, 35, 40), "obsidian": (25, 15, 40), "crying_obsidian": (70, 20, 120),
        "crimson_stem": (110, 40, 60), "shroomlight": (255, 170, 80), "polished_basalt": (95, 95, 100),
        "fire": (255, 200, 40), "soul_fire": (60, 220, 230), "nether_brick_fence": (60, 30, 35),
        "soul_lantern": (90, 230, 240), "weeping_vines": (180, 30, 30), "weeping_vines_plant": (180, 30, 30),
        "twisting_vines": (20, 160, 150), "twisting_vines_plant": (20, 160, 150),
        "crimson_trapdoor": (0, 255, 255), "ladder": (160, 120, 60),
    }
    E = 4
    img = Image.new("RGB", (SX * E * 2 + 30, SZ * E + 70), (20, 20, 24))
    dr = ImageDraw.Draw(img)
    hauts = {}
    for (x, y, z), s in blocs.items():
        if s != AIR and y > hauts.get((x, z), (-1, None))[0]:
            hauts[(x, z)] = (y, s[0].split(":")[1])
    for (x, z), (y, n) in hauts.items():
        c = COUL.get(n, (128, 128, 128))
        k = 0.75 + 0.25 * min(1, (y - SOL) / 18) if y > SOL else 0.75
        dr.rectangle([x * E, 40 + z * E, x * E + E - 1, 40 + z * E + E - 1], fill=tuple(int(v * k) for v in c))
    ox2 = SX * E + 30
    for x in range(SX):
        for z in range(SZ):
            if dist(x, z) <= R + 0.5:
                c = (40, 40, 44)
                if (x, 3, z) in TUN:
                    c = (230, 120, 40)
                elif nom((x, 1, z)) is not None or nom((x, 6, z)) is not None:
                    c = (70, 60, 60)
                dr.rectangle([ox2 + x * E, 40 + z * E, ox2 + x * E + E - 1, 40 + z * E + E - 1], fill=c)
    for i, (x, z) in enumerate(SPAWN_POS):
        for o in (0, ox2):
            dr.ellipse([o + x * E - 7, 40 + z * E - 7, o + x * E + 7, 40 + z * E + 7], outline=(255, 255, 0), width=2)
        dr.text((x * E - 3, 40 + z * E - 6), str(i + 1), fill=(255, 255, 0))
    marques = [(p, (0, 255, 255)) for p in PUITS_ANNEAU + [(px, pz) for px, pz, _ in PUITS_MUR] + [PUITS_SALLE]]
    marques += [(p, (60, 255, 120)) for p in GRIMPE]
    marques += [((round(polar(96, a)[0]), round(polar(96, a)[1])), (255, 80, 200)) for a in A_RIDEAUX]
    marques += [((round(polar(18, A_ENTREE_VOLCAN)[0]), round(polar(18, A_ENTREE_VOLCAN)[1])), (255, 80, 200))]
    for (x, z), c in marques:
        for o in (0, ox2):
            dr.rectangle([o + x * E - 5, 40 + z * E - 5, o + x * E + 5, 40 + z * E + 5], outline=c, width=2)
    dr.text((10, 8), "ARENE FEU - surface   (nord en haut, X vers la droite)", fill=(255, 220, 180))
    dr.text((ox2, 8), "souterrain : tunnels secrets (orange)", fill=(255, 220, 180))
    dr.text((10, SZ * E + 48), "jaune = apparitions 1-10   cyan = trappes/echelles   rose = rideaux de lianes   vert = lianes grimpantes",
            fill=(230, 230, 230))
    img.save(PLAN)
except ImportError:
    print("Pillow absent : pas de plan PNG")

# ================================================================ resume
print(f"Arene ecrite : {len(liste)} blocs, palette {len(palette)}, taille {SX}x{SY}x{SZ}")
print("Obstacles :", dict(compte))
print("Apparitions (X Y Z monde) :")
for i, (x, z) in enumerate(SPAWN_POS):
    print(f"  {i + 1:2d}: {x + ox} {oy + SOL + 1} {z + oz}")
print("Trappes anneau (X Z) :", [(x + ox, z + oz) for x, z in PUITS_ANNEAU])
print("Puits du mur (X Z) :", [(x + ox, z + oz) for x, z, _ in PUITS_MUR])
print("Lianes grimpantes (X Z) :", [(x + ox, z + oz) for x, z in GRIMPE])
print("Rideaux du mur (X Z) :", [(round(polar(96, a)[0]) + ox, round(polar(96, a)[1]) + oz) for a in A_RIDEAUX])
