# [claude-build]
"""
arenes_mini.py - Les 4 arenes en version Ø104 pour le monde Arene Hub : Feu, Air, Nature, Glace.
Par arene : piece centrale, 2 labyrinthes, 2 tours, 4 salles cachees, 10 apparitions, une porte vers le hub.
  .venv\\Scripts\\python.exe sources\\arenes_mini.py
Controle : sources\\verif_acces.py exports\\mini_<theme>.nbt exports\\mini_<theme>_cibles.json
"""
import json
import math
import random
from collections import deque
from pathlib import Path

import nbtlib
import numpy as np
from nbtlib import Compound, Int, List, String

RACINE = Path(__file__).resolve().parent.parent
SAVE = RACINE / "launcher/instances/Build-1.21.1/minecraft/saves/Arene Hub"
STRUCT = SAVE / "datapacks/atelier/data/atelier/structure"
SX = SZ = 104
SY = 62
SOL = 18
C = 52
CEN = 52.0
R = 52.0
R_MUR = 48.0


def S(nom_, **p):
    return ("minecraft:" + nom_, tuple(sorted((k, str(v).lower() if isinstance(v, bool) else str(v)) for k, v in p.items())))


AIR = S("air")
blocs = {}
occ = {}
ECHELLES, SALLES, APPS = [], [], []
CIBLES = {}
rng = random.Random(1)


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


def gl(al, sp, g):
    return {0: (C + al, C + sp), 90: (C - 1 - sp, C + al), 180: (C - 1 - al, C - 1 - sp), 270: (C + sp, C - 1 - al)}[g % 360]


FACE = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}
FACE_ECH = {(-1, 0): "east", (1, 0): "west", (0, -1): "south", (0, 1): "north"}
VERS_CENTRE = {0: "west", 90: "north", 180: "east", 270: "south"}
VERS_DEHORS = {0: "east", 90: "south", 180: "west", 270: "north"}

BARRIERE = S("barrier", waterlogged=False)
CHAINE = S("chain", axis="y", waterlogged=False)
LANT = S("lantern", hanging=False, waterlogged=False)
LANT_P = S("lantern", hanging=True, waterlogged=False)
SLAN = S("soul_lantern", hanging=False, waterlogged=False)
TRAPPE = S("oak_trapdoor", facing="north", half="top", open=False, powered=False, waterlogged=False)
LAVE = S("lava")
EAU = S("water", level=0)
LUM = S("sea_lantern")
SHROOM = S("shroomlight")
TAPIS = S("moss_carpet")
LILY = S("lily_pad")
QP = S("quartz_pillar", axis="y")
QS = S("smooth_quartz")
QB = S("quartz_bricks")
QC = S("chiseled_quartz_block")
QZ = S("quartz_block")
CAL = S("calcite")
SST = S("smooth_stone")
WC = S("white_concrete")
LGC = S("light_gray_concrete")
LAINE = S("white_wool")
G_C = S("cyan_stained_glass")
G_B = S("light_blue_stained_glass")
VITRE = S("white_stained_glass_pane")
CUC = S("waxed_oxidized_cut_copper")
CU = S("waxed_oxidized_copper")
PUR = S("purpur_block")
BLK = S("blackstone")
PBK = S("polished_blackstone")
BKB = S("polished_blackstone_bricks")
CBK = S("cracked_polished_blackstone_bricks")
GILD = S("gilded_blackstone")
NB = S("nether_bricks")
RNB = S("red_nether_bricks")
BAS = S("basalt", axis="y")
OBS = S("obsidian")
CRY = S("crying_obsidian")
NETH = S("netherrack")
MAGMA = S("magma_block")
SOULS = S("soul_sand")
SOULSOIL = S("soul_soil")
FEU_B = S("fire", age=0)
FEU_A = S("soul_fire")
BARS = S("iron_bars", waterlogged=False)
MSB = S("mossy_stone_bricks")
MCB = S("mossy_cobblestone")
CSB = S("cracked_stone_bricks")
SB = S("stone_bricks")
MOSS = S("moss_block")
GRASS = S("grass_block", snowy=False)
POD = S("podzol", snowy=False)
CDIRT = S("coarse_dirt")
RDIRT = S("rooted_dirt")
MUD = S("mud")
CLAY = S("clay")
NEIGE = S("snow_block")
PACK = S("packed_ice")
BLEU = S("blue_ice")
DIO = S("polished_diorite")
DSB = S("deepslate_bricks")
DPRI = S("dark_prismarine")
PRI = S("prismarine")
FROG = S("pearlescent_froglight", axis="y")
POUDRE = S("powder_snow")
SPL = S("spruce_planks")
SLOG = S("spruce_log", axis="y")


def LOG(n, axis="y"):
    return S(f"{n}_log", axis=axis)


def WOOD(n):
    return S(f"{n}_wood", axis="y")


def F(n):
    return S(f"{n}_leaves", distance=1, persistent=True, waterlogged=False)


def COUCHE(n):
    return S("snow", layers=n)


PLANTES = {"short_grass", "fern", "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "oxeye_daisy", "cornflower", "lily_of_the_valley"}
PASSE = PLANTES | {"air", "vine", "moss_carpet", "light", "lily_pad", "ladder", "snow", "powder_snow", "twisting_vines", "twisting_vines_plant", "bubble_column", "birch_door", "water",
                   "fire", "soul_fire", "hanging_roots", "glow_lichen"}
NON_SOLIDES = PASSE | {"lantern", "soul_lantern", "chain", "oak_trapdoor", "white_stained_glass_pane"}
INTERDITS = {"chest", "trapped_chest", "barrel", "hopper", "dropper", "dispenser", "furnace", "blast_furnace", "smoker", "spawner", "bee_nest", "beehive", "ice"}
SOL_PLANTE = {"grass_block", "dirt", "coarse_dirt", "podzol", "moss_block", "rooted_dirt", "mud"}


def solide(p):
    n = nom(p)
    return (p[1] <= SOL) if n is None else n not in NON_SOLIDES


def doux(t, x, z):
    """sol calme : un bloc dominant, le second en grandes plaques"""
    return t[1][1] if bruit(x, z, 9, 61) > 0.64 else t[0][1]


def reserver(cx, cz, r, role="r"):
    for c in disque(cx + .5, cz + .5, r):
        occ[c] = role


def libre(x, z, m=0):
    for a in range(-m, m + 1):
        for b in range(-m, m + 1):
            p = (x + a, z + b)
            if not (0 <= p[0] < SX and 0 <= p[1] < SZ) or p in occ or dist(*p) > 46.5:
                return False
    return True


def echelle(x, z, y0, y1, sup, mat):
    for y in range(y0, y1 + 1):
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
            x = math.floor(cx + .5 + ux * r_ - uz * s)
            z = math.floor(cz + .5 + uz * r_ + ux * s)
            for yy in range(y, y + h):
                put(x, yy, z, AIR)
            if sol:
                put(x, y - 1, z, sol)
        r_ += 0.4


def vigne(x, y, z, sens, n):
    s = S("vine", **{FACE[sens]: True})
    while y > SOL and not solide((x + sens[0], y, z + sens[1])):
        y -= 1
    for k in range(n):
        if vide((x, y - k, z)):
            put(x, y - k, z, s)
        else:
            break


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


def salle(cfg, num, x0, x1, z0, z1, yf, coffre, acces, garder=()):
    h = 4
    style = cfg["salle"]
    garder = set(garder)
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            for y in range(yf, yf + h + 2):
                dedans = x0 <= x <= x1 and z0 <= z <= z1
                if dedans and yf < y <= yf + h:
                    put(x, y, z, AIR)
                elif dedans and y == yf:
                    put(x, y, z, cfg["dalle"][(x + z) % 2])
                else:
                    put(x, y, z, choix(style, h01(x, y, z, 77 + num)))
    cx, cz = coffre
    put(cx, yf, cz, cfg["coffre"])
    libres = garder | {coffre}
    for x, z in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
        if (x, z) not in libres and math.hypot(x - cx, z - cz) > 1.5:
            put(x, yf + 1, z, cfg["lanterne"])
    mx, mz = (x0 + x1) // 2, (z0 + z1) // 2
    put(mx, yf + h + 1, mz, cfg["lum"])
    if (mx, mz) not in libres:
        put(mx, yf + h, mz, CHAINE)
        put(mx, yf + h - 1, mz, S(cfg["lanterne"][0].split(":")[1], hanging=True, waterlogged=False))
    for x in range(x0, x1 + 1):
        for z in (z0 - 1, z1 + 1):
            if (x + num) % 3 == 0:
                put(x, yf + 3, z, cfg["verre"])
    for z in range(z0, z1 + 1):
        for x in (x0 - 1, x1 + 1):
            if (z + num) % 3 == 0:
                put(x, yf + 3, z, cfg["verre"])
    SALLES.append((num, (cx, yf + 1, cz), acces))


# ====================================================================== structure commune
def murs(cfg):
    g0 = cfg["porte"]
    t = cfg["mur"]
    hbase = cfg["hmur"]
    for x in range(SX):
        for z in range(SZ):
            d = dist(x, z)
            if d >= R or d < R_MUR:
                continue
            a = ang(x, z)
            hm = hbase + int(3 * bruit(a, 0, 9, 20))
            crenel = int(a * 1.5) % 2 == 0 and d >= 50.5
            baie = cfg["nom"] == "Air" and abs(a % 12 - 6) < 3.8
            for y in range(SOL, hm + 1):
                if baie and SOL + 3 <= y <= hm - 2 and d >= 49:
                    put(x, y, z, G_B if d < 50.5 else S("white_stained_glass"))
                elif baie and SOL + 3 <= y <= hm - 2:
                    put(x, y, z, AIR)
                else:
                    put(x, y, z, choix(t, h01(int(a / 6) * 13 + (y - SOL) // 3, x % 2, z % 2, 22)))
            put(x, hm + 1, z, cfg["accent"])
            if crenel:
                put(x, hm + 2, z, choix(t, h01(x, 1, z, 23)))
            if d >= 51:
                for y in range(hm + 2, SY):
                    if vide((x, y, z)):
                        put(x, y, z, BARRIERE)
    for sp in range(-9, 9):                            # porte vers le hub
        for al in range(43, 52):
            x, z = gl(al, sp, g0)
            if dist(x, z) >= R:
                continue
            cote = sp < -5 or sp > 4
            for y in range(SOL + 1, SOL + 15):
                if cote or y > SOL + 9:
                    put(x, y, z, cfg["accent"] if y == SOL + 14 else choix(t, h01(x, y, z, 24)))
                else:
                    put(x, y, z, AIR)
            if not cote:
                put(x, SOL, z, doux(cfg["chemin"], x, z))
            occ[(x, z)] = "porte"
    for sp in (-8, 7):
        x, z = gl(44, sp, g0)
        put(x, SOL + 15, z, cfg["lum"])
    reserver(*gl(40, 0, g0), 7, "porte")


def labyrinthes(cfg):
    U, epais = cfg["U"], cfg["epais"]
    res = []
    for c0 in (cfg["porte"] + 45, cfg["porte"] + 225):
        c0 %= 360

        def dedans(x, z):
            return 26.5 <= dist(x, z) <= 46.8 and adiff(ang(x, z), c0) <= 36

        cases = set()
        for i in range(0, SX // U):
            for j in range(0, SZ // U):
                x0, z0 = U * i, U * j
                m = 1 if epais == 3 else 0
                if all(dedans(x, z) for x, z in ((x0 - m, z0 - m), (x0 + U + m, z0 - m), (x0 - m, z0 + U + m), (x0 + U + m, z0 + U + m))):
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
        murs_ = set()
        inter = list(range(2, U - 1)) if epais == 3 else list(range(1, U))
        ep = (-1, 0, 1) if epais == 3 else (0,)
        for i, j in cases:
            for a in range(-1 if epais == 3 else 0, U + (2 if epais == 3 else 1)):
                for b in range(-1 if epais == 3 else 0, U + (2 if epais == 3 else 1)):
                    x, z = U * i + a, U * j + b
                    occ[(x, z)] = "laby"
                    if a not in inter or b not in inter:
                        murs_.add((x, z))
            for a in range(-3, U + 4):
                for b in range(-3, U + 4):
                    occ.setdefault((U * i + a, U * j + b), "abords")

        def trou(c, d):
            i, j = c
            for t_ in ep:
                for n_ in inter:
                    if d == (1, 0):
                        murs_.discard((U * i + U + t_, U * j + n_))
                    elif d == (-1, 0):
                        murs_.discard((U * i + t_, U * j + n_))
                    elif d == (0, 1):
                        murs_.discard((U * i + n_, U * j + U + t_))
                    else:
                        murs_.discard((U * i + n_, U * j + t_))

        for p in passages:
            a, b = sorted(p)
            trou(a, (b[0] - a[0], b[1] - a[1]))
        bords = []
        for c in sorted(cases):
            for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if (c[0] + d[0], c[1] + d[1]) not in cases:
                    r0 = dist(U * c[0] + 2, U * c[1] + 2)
                    r1 = dist(U * c[0] + 2 + U * d[0], U * c[1] + 2 + U * d[1])
                    bords.append((r1 < r0, ang(U * c[0] + 2, U * c[1] + 2), c, d))
        entrees = set()
        for dedans_ in (True, False):
            l_ = sorted(b for b in bords if b[0] == dedans_)
            for k in (len(l_) // 4, 3 * len(l_) // 4):
                if l_:
                    _, _, c, d = l_[k]
                    trou(c, d)
                    entrees.add(c)
        B = cfg["base"]
        for x, z in murs_:
            coeur = x % U == 0 or z % U == 0
            for y in range(B + 1, B + 6):
                put(x, y, z, cfg["coeur"] if coeur or epais == 1 and h01(x, y, z, 30) < 0.6 else choix(cfg["peau"], h01(x, y, z, 31)))
            put(x, B + 6, z, cfg["cap"])
        imp = [c for c in sorted(cases) if sum(c in p for p in passages) == 1 and c not in entrees]
        res.append(dict(cases=cases, imp=imp, U=U))
        ctr = min(sorted(cases), key=lambda c: abs(dist(U * c[0] + 2, U * c[1] + 2) - 36) + adiff(ang(U * c[0] + 2, U * c[1] + 2), c0) / 4)
        CIBLES[f"labyrinthe {len(res)}"] = [U * ctr[0] + 2, B + 1, U * ctr[1] + 2]
    return res


def tour(cfg, a, forme, H_, t, band, cle, sommet_abri):
    B = cfg["base"]
    cx, cz = cellule(33, a)
    ri, ro = 3, 5
    reserver(cx, cz, 8, "tour")
    for x in range(cx - ro - 1, cx + ro + 2):
        for z in range(cz - ro - 1, cz + ro + 2):
            m = math.hypot(x - cx, z - cz) if forme == "rond" else max(abs(x - cx), abs(z - cz))
            if m > ro:
                continue
            if m > ri:
                for y in range(SOL + 1, H_ + 4):
                    e = y - B
                    fen = e % 7 in (3, 4) and (x + z) % 5 < 2 and y < H_ and y > B
                    put(x, y, z, cfg["verre"] if fen else band if e % 8 == 0 else choix(t, h01(x, y, z, 40)))
            else:
                put(x, SOL, z, doux(cfg["chemin"], x, z))
                for y in range(SOL + 1, H_ + 4):
                    put(x, y, z, AIR)
                put(x, B, z, doux(cfg["chemin"], x, z))
                put(x, H_, z, doux(cfg["chemin"], x, z))
                if sommet_abri:
                    put(x, H_ + 4, z, band)
    if sommet_abri:
        for x in range(cx - ro, cx + ro + 1):
            for z in range(cz - ro, cz + ro + 1):
                m = math.hypot(x - cx, z - cz) if forme == "rond" else max(abs(x - cx), abs(z - cz))
                if m <= ro:
                    put(x, H_ + 4, z, band)
                if m <= ro - 2:
                    put(x, H_ + 5, z, cfg["accent"])
    else:
        for x in range(cx - ro, cx + ro + 1):
            for z in range(cz - ro, cz + ro + 1):
                m = math.hypot(x - cx, z - cz) if forme == "rond" else max(abs(x - cx), abs(z - cz))
                if ri < m <= ro and (x + z) % 2 == 0:
                    put(x, H_ + 4, z, band)
                elif ri < m <= ro:
                    pass
        percer(cx, cz, a + 90, ri - 1, ro * 1.45 + 1, H_ + 1, 2)
    for y in range(SOL + 1, H_ + 2):
        put(cx, y, cz, band if y % 6 == 0 else choix(t, h01(0, y, 0, 44)))
    echelle(cx + 1, cz, SOL + 1, H_ + 1, (-1, 0), cfg["coeur"])
    acc = (a + 180) % 360
    percer(cx, cz, acc, ri - 1, ro * 1.45 + 1.5, B + 1, 3)
    if B != SOL:
        percer(cx, cz, acc, ri - 1, ro * 1.45 + 1.5, SOL + 1, 3)
    for y in (B + 1, H_ + 1):
        put(cx - 2, y, cz + 2, cfg["lanterne"]) if vide((cx - 2, y, cz + 2)) else None
    CIBLES[f"haut de la tour {cle}"] = [cx - 1, H_ + 1, cz - 1]
    return cx, cz, H_


def salle_trappe(cfg, num, labs, couverture, texte):
    B = cfg["base"]
    L = labs[0]
    U = L["U"]
    i, j = min(L["imp"], key=lambda c: abs(dist(U * c[0] + 2, U * c[1] + 2) - 37))
    tx, tz = U * i + 2, U * j + 2
    yf = B - 8
    salle(cfg, num, tx - 2, tx + 3, tz - 3, tz + 2, yf, (tx + 3, tz), texte, garder=[(tx, tz), (tx + 1, tz)])
    echelle(tx, tz, yf + 1, B - 1, (1, 0), cfg["coeur"])
    put(tx, B, tz, TRAPPE)
    if couverture:
        put(tx, B + 1, tz, couverture)
    return tx, tz


def salle_centre(cfg, num, texte, y_sol):
    """stele creuse au sud-est du centre, echelle vers une salle sous le centre"""
    B = cfg["base"]
    sx_, sz_ = C + 6, C + 6
    yf = B - 8
    salle(cfg, num, sx_ - 3, sx_ + 3, sz_ - 3, sz_ + 3, yf, (sx_ - 3, sz_), texte, garder=[(sx_, sz_), (sx_ + 1, sz_)])
    for i in (-1, 0, 1):
        for j in (-1, 0, 1):
            for y in range(y_sol + 1, y_sol + 6):
                put(sx_ + i, y, sz_ + j, cfg["accent"] if y == y_sol + 5 else cfg["coeur"])
    for y in range(B - 3, y_sol + 3):
        put(sx_, y, sz_, AIR)
    echelle(sx_, sz_, yf + 1, y_sol + 1, (1, 0), cfg["coeur"])
    for y in (y_sol + 1, y_sol + 2):
        put(sx_ - 1, y, sz_, AIR)


def salle_mur(cfg, num, texte):
    """salle dans un massif contre le rempart, en face de la porte, cachee derriere un pan de mur decale"""
    B = cfg["base"]
    g = (cfg["porte"] + 180) % 360
    pts = [gl(al, sp, g) for al in (41, 45) for sp in (-2, 2)]
    xs, zs = [p[0] for p in pts], [p[1] for p in pts]
    for al in range(38, 48):
        for sp in range(-5, 5):
            x, z = gl(al, sp, g)
            for y in range(B + 1, B + 8):
                if al >= 40:
                    put(x, y, z, choix(cfg["mur"], h01(x, y, z, 50)))
            put(x, B, z, cfg["dalle"][0])
            occ[(x, z)] = "massif"
    salle(cfg, num, min(xs), max(xs), min(zs), max(zs), B, gl(45, 0, g), texte, garder=[gl(41, 0, g)])
    x, z = gl(40, 0, g)
    for y in (B + 1, B + 2):
        put(x, y, z, AIR)
    for sp in range(-3, 3):                            # pan decale
        x, z = gl(38, sp, g)
        for y in range(B + 1, B + 5):
            put(x, y, z, choix(cfg["mur"], h01(x, y, z, 51)))
    reserver(*gl(36, 0, g), 4, "massif")


def lumiere(cfg):
    src = cfg.get("sources", {"sea_lantern": 15, "lantern": 15, "soul_lantern": 10, "shroomlight": 15, "lava": 15, "light": 15, "pearlescent_froglight": 15})
    lum = S("light", level=cfg.get("niveau", 15), waterlogged=False)
    src = dict(src)
    src["light"] = cfg.get("niveau", 15)
    transp = NON_SOLIDES | {"barrier", "iron_bars", "cyan_stained_glass", "light_blue_stained_glass", "orange_stained_glass", "lime_stained_glass",
                            "smooth_quartz_slab", "oak_slab", "quartz_stairs", "blackstone_stairs", "stone_brick_stairs", "sea_lantern", "shroomlight", "water", "lava", "white_stained_glass"}
    sans = transp | {"oak_fence", "spruce_fence"}
    total = 0

    def calc():
        op = np.zeros((SX, SY, SZ), dtype=bool)
        op[:, :SOL + 1, :] = True
        L = np.zeros((SX, SY, SZ), dtype=np.int8)
        for (x, y, z), s in blocs.items():
            n = s[0].split(":")[1]
            op[x, y, z] = n not in transp and not n.endswith("_leaves")
            if n in src:
                L[x, y, z] = src[n]
                op[x, y, z] = False
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
        return (p[1] > SOL) if n is None else n in PASSE and n != "water"

    coffres = {(p[0], p[2]) for _, p, _ in SALLES}
    for passe in range(30):
        L = calc()
        noires = sorted((x, y, z) for (x, y, z), s in blocs.items()
                        if s[0].split(":")[1] not in sans and not s[0].endswith("_leaves") and y + 2 < SY and dist(x, z) < 50.5
                        and trav((x, y + 1, z)) and trav((x, y + 2, z)) and L[x, y + 1, z] == 0)
        if not noires:
            break
        pris = []
        for x, y, z in noires:
            if (x, z) in coffres or any(abs(x - a) < 10 and abs(z - b) < 10 and abs(y - c) < 6 for a, c, b in pris):
                continue
            pris.append((x, y, z))
            put(x, y + 1, z, lum)
            total += 1
    if noires:
        raise SystemExit(f"surfaces noires : {noires[:6]}")
    return total


def verifier(phase):
    err = []
    for p, s in blocs.items():
        x, y, z = p
        n = s[0].split(":")[1]
        st = dict(s[1])
        if n in INTERDITS or n.endswith("shulker_box"):
            err.append(f"interdit {n} en {p}")
        if n != "air" and dist(x, z) > R + 1e-9:
            err.append(f"depasse : {n} en {p}")
        if n.endswith("_leaves") and st.get("persistent") != "true":
            err.append(f"feuille en {p}")
        if n in ("lava", "water"):
            for q in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1), (x, y - 1, z)):
                if nom(q) not in ("lava", "water") and not solide(q):
                    err.append(f"{n} non contenue {p} -> {q} ({nom(q)})")
        if n == "bubble_column":
            if nom((x, y - 1, z)) not in ("bubble_column", "soul_sand"):
                err.append(f"colonne sans base {p}")
            for q in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1), (x, y + 1, z)):
                if nom(q) not in ("bubble_column", "birch_door") and not solide(q):
                    err.append(f"eau de colonne non contenue {p} -> {q}")
        if n in PLANTES and nom((x, y - 1, z)) not in SOL_PLANTE:
            err.append(f"plante {n} sur {nom((x, y - 1, z))} en {p}")
        if n == "lily_pad" and blocs.get((x, y - 1, z)) != EAU:
            err.append(f"nenuphar sans eau en {p}")
        if n == "snow" and (nom((x, y - 1, z)) in ("packed_ice", "blue_ice", "barrier") or (not solide((x, y - 1, z)) and nom((x, y - 1, z)) != "oak_trapdoor")):
            err.append(f"neige sur {nom((x, y - 1, z))} en {p}")
        if n == "fire" and nom((x, y - 1, z)) not in ("netherrack", "magma_block"):
            err.append(f"feu mal pose en {p}")
        if n == "soul_fire" and nom((x, y - 1, z)) not in ("soul_sand", "soul_soil"):
            err.append(f"feu des ames mal pose en {p}")
        if n in ("lantern", "soul_lantern"):
            q = (x, y + 1, z) if st["hanging"] == "true" else (x, y - 1, z)
            if not solide(q) and nom(q) != "chain":
                err.append(f"lanterne sans appui en {p}")
    for e, sp in ECHELLES:
        if nom(e) == "ladder" and not solide(sp):
            err.append(f"echelle {e} sans support")
    for num, (cx, cy, cz), _ in SALLES:
        if nom((cx, cy, cz)) != "air" or nom((cx, cy + 1, cz)) != "air" or not solide((cx, cy - 1, cz)):
            err.append(f"salle {num} : coffre non degage {(cx, cy, cz)} {nom((cx, cy, cz))}")
    print(f"   [{phase}] blocs={len(blocs)} erreurs={len(err)}", flush=True)
    if err:
        for e in err[:14]:
            print("   !", e)
        raise SystemExit(1)


def apparitions(cfg):
    B = cfg["base"]
    for i in range(10):
        a = 9 + 36 * i
        for dr in (0, 1, -1, 2, -2, 3):
            x, z = cellule(24 + dr, a)
            if nom((x, B, z)) not in (None, "air") and vide((x, B + 1, z)) and vide((x, B + 2, z)) and solide((x, B, z)):
                break
        put(x, B, z, cfg["coffre"])
        put(x, B + 1, z, AIR)
        put(x, B + 2, z, AIR)
        APPS.append((x, B + 1, z))


# ====================================================================== 4 themes
def sol_plat(cfg):
    for x in range(SX):
        for z in range(SZ):
            d = dist(x, z)
            if d >= R_MUR + 0.5:
                continue
            a, b = x + .5 - CEN, z + .5 - CEN
            t = cfg["chemin"] if min(abs(a), abs(b)) <= 2.6 or abs(d - 23.5) < 2 else cfg["sol"]
            put(x, SOL, z, doux(t, x, z))


def ponts(cfg, y, table):
    for g in (0, 90, 180, 270):
        for al in range(12, 21):
            for sp in range(-3, 3):
                x, z = gl(al, sp, g)
                put(x, y, z, table[0][1])
                for k in (1, 2, 3):
                    if not vide((x, y + k, z)):
                        put(x, y + k, z, AIR)


def feu(cfg):
    sol_plat(cfg)
    for x, z in disque(CEN, CEN, 19.6):                # fosse de lave autour de l'autel
        d = dist(x, z)
        if 14 <= d < 19:
            put(x, SOL, z, AIR)
            put(x, SOL - 1, z, LAVE)
            put(x, SOL - 2, z, LAVE)
            put(x, SOL - 3, z, BLK)
        elif d < 14:
            put(x, SOL, z, PBK)
    ponts(cfg, SOL, [(60, BKB), (40, CBK)])
    for x, z in disque(CEN, CEN, 7.5):
        put(x, SOL + 1, z, PBK if dist(x, z) > 5 else BKB)
    for i in range(-2, 2):
        for j in range(-2, 2):
            for y in range(SOL + 2, SOL + 24):
                if y < SOL + 22 or (abs(i + .5) < 1 and abs(j + .5) < 1):
                    put(C + i, y, C + j, CRY if (y + i + j) % 7 == 0 else OBS)
    put(C, SOL + 24, C, MAGMA)
    for a in (45, 135, 225):
        x, z = cellule(11.5, a)
        for u, v in disque(x + .5, z + .5, 1.2):
            put(u, SOL + 1, v, RNB)
        put(x, SOL + 1, z, SOULS)
        put(x, SOL + 2, z, FEU_A)
    reserver(C, C, 20, "centre")
    return SOL


def feu_deco(cfg):
    n = 0
    for i in range(2500):
        x, z = cellule(rng.uniform(21, 46), rng.uniform(0, 360))
        if n >= 34 or not libre(x, z, 1):
            continue
        u = rng.random()
        if u < 0.45:                                   # stalagmite de basalte
            h = rng.randint(3, 8)
            for cx, cz in disque(x + .5, z + .5, 1.5):
                hh = max(1, h - int(2.5 * math.hypot(cx - x, cz - z)))
                for y in range(SOL + 1, SOL + 1 + hh):
                    put(cx, y, cz, BAS if h01(cx, y, cz, 5) < 0.7 else BLK)
        elif u < 0.7:                                  # mare de lave bordee
            for cx, cz in disque(x + .5, z + .5, 2.3):
                d = math.hypot(cx - x, cz - z)
                if libre(cx, cz):
                    if d <= 1.2:
                        put(cx, SOL, cz, AIR)
                        put(cx, SOL - 1, cz, LAVE)
                        put(cx, SOL - 2, cz, BLK)
                    else:
                        put(cx, SOL, cz, MAGMA)
        elif u < 0.85:                                 # colonne brisee
            for cx, cz in disque(x + .5, z + .5, 1.4):
                for y in range(SOL + 1, SOL + 2 + rng.randint(2, 7)):
                    put(cx, y, cz, NB if h01(cx, y, cz, 6) < 0.6 else RNB)
        else:                                          # brasier
            put(x, SOL, z, NETH)
            put(x, SOL + 1, z, FEU_B)
        reserver(x, z, 3.5, "deco")
        n += 1


def air(cfg):
    B = cfg["base"]
    for x in range(SX):                                # zone basse : mer de nuages
        for z in range(SZ):
            if dist(x, z) < R_MUR + 0.5:
                put(x, SOL, z, WC if bruit(x, z, 7, 1) > 0.5 else NEIGE_SOL)
    TROUS = [(32, 15, 4.5), (32, 105, 4.5), (32, 195, 4.5), (32, 285, 4.5)]
    TC = [(*point(r, a), rad) for r, a, rad in TROUS]
    g0 = cfg["porte"]
    for x in range(SX):
        for z in range(SZ):
            d = dist(x, z)
            if d > 47.9 or 13 <= d < 19:
                continue
            if any(math.hypot(x + .5 - a, z + .5 - b) <= rad * (0.85 + 0.3 * bruit(x, z, 3, 2)) for a, b, rad in TC):
                continue
            a_, b_ = x + .5 - CEN, z + .5 - CEN
            t = cfg["chemin"] if min(abs(a_), abs(b_)) <= 2.6 or abs(d - 23.5) < 2 else cfg["sol"]
            put(x, B, z, doux(t, x, z))
            put(x, B - 1, z, SST)
    ponts(cfg, B, [(60, QS), (40, QB)])
    for g in (0, 90, 180, 270):                        # cables des ponts
        for sp in (-4, 3):
            for al in (12, 16, 20):
                x, z = gl(al, sp, g)
                for y in range(B, B + 6 + (al % 8 == 4) * -2):
                    put(x, y, z, QP)
                put(x, B + 6 + (al % 8 == 4) * -2, z, CUC)
    # escalier vers le plateau, depuis la porte
    for al in range(30, 49):
        for sp in range(-4, 4):
            x, z = gl(al, sp, g0)
            put(x, B, z, AIR)
            put(x, B - 1, z, AIR)
            occ[(x, z)] = "escalier"
    for k in range(13):
        al = 43 - k
        for sp in range(-3, 3):
            x, z = gl(al, sp, g0)
            for y in range(SOL + 1, SOL + 1 + k):
                put(x, y, z, QB)
            put(x, SOL + 1 + k, z, S("quartz_stairs", facing=VERS_CENTRE[g0], half="bottom", shape="straight", waterlogged=False))
    for sp in range(-3, 3):
        x, z = gl(30, sp, g0)
        put(x, B, z, QS)
        put(x, B - 1, z, QB)
    # colonnes porteuses
    for r_, pas in ((21, 45), (41, 30)):
        for k in range(360 // pas):
            a = k * pas + pas / 2
            x, z = cellule(r_, a)
            if (x, z) in occ or nom((x, B, z)) in (None, "air"):
                continue
            for cx, cz in disque(x + .5, z + .5, 1.5):
                for y in range(SOL + 1, B - 1):
                    put(cx, y, cz, QP)
    for x, z in disque(CEN, CEN, 13):                  # ile centrale : socle, colonnes, sphere
        d = dist(x, z)
        for k in range(1, int(9 * (1 - (d / 13) ** 2)) + 2):
            put(x, B - k, z, CAL if h01(x, k, z, 4) < 0.7 else G_C)
    for a in range(22, 360, 45):
        px, pz = point(10.5, a)
        for x, z in disque(px, pz, 1.2):
            for y in range(B + 1, B + 11):
                put(x, y, z, QP)
    for x, z in disque(CEN, CEN, 12):
        d = dist(x, z)
        a_, b_ = abs(x + .5 - CEN), abs(z + .5 - CEN)
        if d >= 8.5 or a_ <= 1 or b_ <= 1:
            put(x, B + 11, z, CUC if d >= 10 else QS)
    Y = B + 7
    for x in range(C - 4, C + 4):
        for z in range(C - 4, C + 4):
            for y in range(Y - 4, Y + 4):
                e = math.hypot(x + .5 - CEN, y + .5 - Y, z + .5 - CEN)
                if 2.2 <= e <= 3.2:
                    put(x, y, z, G_C if (x + y + z) % 3 else G_B)
                elif e <= 1.2:
                    put(x, y, z, LUM)
    for y in range(Y + 3, B + 11):
        for x, z in ((C - 1, C - 1), (C, C)):
            if vide((x, y, z)):
                put(x, y, z, CHAINE)
    reserver(C, C, 20, "centre")
    # ascenseur a bulles et lianes
    a1, a2 = (g0 + 60) % 360, (g0 + 300) % 360
    cx, cz = cellule(23, a1)
    reserver(cx, cz, 5, "asc")
    for i in range(-1, 2):
        for j in range(-1, 2):
            if i or j:
                for y in range(SOL + 1, B + 3):
                    put(cx + i, y, cz + j, QS if y in (B, B - 1) else G_C)
            put(cx + i, B + 3, cz + j, QC)
    put(cx, SOL, cz, SOULS)
    for y in range(SOL + 1, B + 3):
        put(cx, y, cz, S("bubble_column", drag=False))
    ux, uz = (1 if C > cx else -1, 0) if abs(C - cx) >= abs(C - cz) else (0, 1 if C > cz else -1)
    for yb in (SOL + 1, B + 1):
        for k, half in ((0, "lower"), (1, "upper")):
            put(cx + ux, yb + k, cz + uz, S("birch_door", facing=FACE[(ux, uz)], half=half, hinge="left", open=True, powered=False))
    CIBLES["ascenseur (bas)"] = [cx + 2 * ux, SOL + 1, cz + 2 * uz]
    CIBLES["ascenseur (haut)"] = [cx + 2 * ux, B + 1, cz + 2 * uz]
    cx, cz = cellule(23, a2)
    reserver(cx, cz, 5, "liane")
    for i in range(-1, 2):
        for j in range(-1, 2):
            for y in range(SOL + 1, B + 5):
                put(cx + i, y, cz + j, QP)
    ux, uz = (1 if C > cx else -1, 0) if abs(C - cx) >= abs(C - cz) else (0, 1 if C > cz else -1)
    vx, vz = cx + 2 * ux, cz + 2 * uz
    for y in range(SOL + 1, B):
        put(vx, y, vz, S("twisting_vines_plant"))
    put(vx, B, vz, S("twisting_vines", age=25))
    px, pz = uz, ux
    for s in (-2, 2):
        for y in range(B + 1, B + 5):
            put(vx + ux + px * s, y, vz + uz + pz * s, QP)
    for i in range(-1, 3):
        for s in range(-2, 3):
            put(vx + ux * i + px * s, B + 5, vz + uz * i + pz * s, CUC)
    CIBLES["pied des lianes"] = [vx + ux, SOL + 1, vz + uz]
    return B


NEIGE_SOL = S("snow_block")


def air_deco(cfg):
    B = cfg["base"]
    n = 0
    for i in range(2500):
        x, z = cellule(rng.uniform(21, 46), rng.uniform(0, 360))
        if n >= 22 or not libre(x, z, 1) or nom((x, B, z)) in (None, "air"):
            continue
        h = rng.randint(3, 11)
        for cx, cz in disque(x + .5, z + .5, 1.4):
            if nom((cx, B, cz)) not in (None, "air"):
                for y in range(B + 1, B + 1 + h - int(3 * h01(cx, 0, cz, 7))):
                    put(cx, y, cz, QP)
        if rng.random() < 0.4:
            for cx, cz in disque(x + .5, z + .5, 1.4):
                for y in range(B + h + 3, B + h + 6):
                    put(cx, y, cz, QP)
        reserver(x, z, 3.5, "deco")
        n += 1
    for x in range(SX):                                # nuages en bas
        for z in range(SZ):
            if dist(x, z) < 47.5 and vide((x, SOL + 1, z)) and (x, z) not in occ:
                u = bruit(x, z, 8, 8)
                if u > 0.55 and nom((x, SOL + 1, z)) is None:
                    for y in range(SOL + 1, SOL + 1 + int((u - 0.5) * 9)):
                        put(x, y, z, LAINE)


def nature(cfg):
    sol_plat(cfg)
    for x, z in disque(CEN, CEN, 19.6):                # douves et ile
        d = dist(x, z)
        if 14 <= d < 19:
            put(x, SOL, z, AIR)
            put(x, SOL - 1, z, EAU)
            put(x, SOL - 2, z, EAU)
            put(x, SOL - 3, z, CLAY)
            if h01(x, 3, z, 5) < 0.12:
                put(x, SOL, z, LILY)
        elif d < 14:
            put(x, SOL, z, MOSS if bruit(x, z, 7, 62) > 0.5 else GRASS)
    ponts(cfg, SOL, [(60, S("spruce_planks")), (40, S("oak_planks"))])
    h = 24                                             # arbre-monde creux
    a0 = 200
    for y in range(SOL, SOL + h + 1):
        t = (y - SOL) / h
        r_ = 6.6 - 1.4 * t + (2.5 * (1 - t / 0.14) ** 2 if t < 0.14 else 0)
        for x, z in disque(CEN, CEN, r_ + 1):
            d = dist(x, z)
            if d <= 4.3:
                put(x, y, z, S("oak_planks") if y == SOL else AIR)
            elif d <= r_ * (0.93 + 0.14 * bruit(x + y, z, 4, 7)):
                put(x, y, z, MOSS if h01(x, y, z, 8) < 0.07 else WOOD("oak"))
    for y in range(SOL + 1, SOL + h + 1):
        for x, z in disque(CEN, CEN, 1.5):
            put(x, y, z, WOOD("oak"))
    spirale(CEN - .5, CEN - .5, 1.6, 4.3, SOL, h, 9, a0, S("oak_planks"), S("oak_slab", type="bottom", waterlogged=False))
    percer(CEN - .5, CEN - .5, a0 - 8, 3.4, 11, SOL + 1, 3)
    for x, z in disque(CEN, CEN, 6):
        d = dist(x, z)
        t0 = (math.degrees(math.atan2(z - C + .5, x - C + .5)) - a0) % 360
        dernier = t0 + 360 * ((h * 40 - t0) // 360)
        if 1.6 <= d <= 4.3 and dernier >= (h - 4) * 40:
            continue
        put(x, SOL + h, z, S("oak_planks"))
    for x, z in disque(CEN, CEN, 1.5):
        put(x, SOL + h, z, WOOD("oak"))
    for x, z in disque(CEN, CEN, 6.2):
        if dist(x, z) > 5.2:
            put(x, SOL + h + 1, z, S("oak_fence", waterlogged=False))
    put(C, SOL + h + 1, C, LANT)
    CIBLES["sommet de l'arbre-monde"] = [C - 4, SOL + h + 1, C + 1]
    for i in range(8):                                 # couronne
        a = math.radians(45 * i + 15)
        px, pz = CEN + 10 * math.cos(a), CEN + 10 * math.sin(a)
        for x in range(math.floor(px - 8), math.ceil(px + 8)):
            for z in range(math.floor(pz - 8), math.ceil(pz + 8)):
                for y in range(SOL + h + 4, SOL + h + 13):
                    e = ((x + .5 - px) / 7.5) ** 2 + ((z + .5 - pz) / 7.5) ** 2 + ((y - SOL - h - 8) / 4) ** 2
                    if e < 0.8 + 0.4 * bruit(x + y, z, 3, 9) and nom((x, y, z)) is None and dist(x, z) < 47:
                        u = h01(x, y, z, 10)
                        put(x, y, z, SHROOM if u < 0.012 else F(("oak", "azalea", "flowering_azalea")[int(u * 11) % 3]))
    reserver(C, C, 20, "centre")
    return SOL


def nature_deco(cfg):
    n = 0
    for i in range(3000):
        x, z = cellule(rng.uniform(21, 46.5), rng.uniform(0, 360))
        if n >= 42 or not libre(x, z, 1):
            continue
        u = rng.random()
        if u < 0.6:                                    # arbre
            b, fl = rng.choice((("oak", ["oak"]), ("dark_oak", ["dark_oak"]), ("spruce", ["spruce"]), ("jungle", ["jungle"]), ("cherry", ["cherry"]), ("oak", ["azalea", "flowering_azalea"])))
            h = rng.randint(5, 10)
            for y in range(SOL + 1, SOL + 1 + h):
                put(x, y, z, LOG(b))
            rc = rng.uniform(3, 4.6)
            for cx, cz in disque(x + .5, z + .5, rc + 1):
                for y in range(SOL + h - 1, SOL + h + 4):
                    e = (math.hypot(cx - x, cz - z) / rc) ** 2 + ((y - SOL - h - 1) / 2.8) ** 2
                    if e < 1 and nom((cx, y, cz)) is None and dist(cx, cz) < 47.5:
                        w = h01(cx, y, cz, 11)
                        put(cx, y, cz, SHROOM if w < 0.015 else F(fl[int(w * 7) % len(fl)]))
        elif u < 0.85:                                 # colonne moussue
            h = rng.randint(4, 9)
            for cx, cz in disque(x + .5, z + .5, 1.4):
                for y in range(SOL + 1, SOL + 1 + h - int(3 * h01(cx, 0, cz, 12))):
                    put(cx, y, cz, MSB if h01(cx, y, cz, 13) < 0.6 else MCB)
            vigne(x + 2, SOL + h - 1, z, (-1, 0), h - 2)
        else:                                          # rocher moussu
            for cx, cz in disque(x + .5, z + .5, 1.8):
                put(cx, SOL + 1, cz, MCB)
            put(x, SOL + 2, z, MOSS)
        reserver(x, z, 3, "deco")
        n += 1
    fleurs = ["dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "oxeye_daisy", "cornflower", "lily_of_the_valley"]
    for x in range(SX):
        for z in range(SZ):
            if nom((x, SOL, z)) in SOL_PLANTE and vide((x, SOL + 1, z)) and dist(x, z) < 47.5:
                u = h01(x, 8, z, 14)
                if bruit(x, z, 8, 64) < 0.5:
                    continue                           # l'herbe haute pousse par touffes, pas partout
                if u < 0.22:
                    put(x, SOL + 1, z, S("short_grass"))
                elif u < 0.27:
                    put(x, SOL + 1, z, S("fern"))
                elif u < 0.29:
                    put(x, SOL + 1, z, S(fleurs[int(bruit(x, z, 14, 65) * 16) % 8]))
    for x in range(SX):                                # lianes sur le rempart
        for z in range(SZ):
            if 47 <= dist(x, z) < 48 and h01(x, 2, z, 16) < 0.4 and vide((x, SOL + 9, z)):
                for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if dist(x + a, z + b) >= 48 and solide((x + a, SOL + 9, z + b)):
                        vigne(x, SOL + 9, z, (a, b), 4 + int(3 * h01(x, 3, z, 17)))
                        break


def pic(cx, cz, r0, h, g, y0=SOL):
    for k in range(h + 1):
        r_ = r0 * (1 - k / h) ** 0.85
        for x, z in disque(cx + .5, cz + .5, r_ + 0.35):
            y = y0 + 1 + k
            if 0 <= x < SX and 0 <= z < SZ and dist(x, z) < 47.5 and y < SY:
                put(x, y, z, BLEU if bruit(x + y, z - y, 3, g) > 0.45 else PACK)


def glace(cfg):
    sol_plat(cfg)
    for x, z in disque(CEN, CEN, 20):                  # lac gele
        d = dist(x, z)
        put(x, SOL, z, BLEU if bruit(x, z, 11, 63) > 0.6 else PACK)
    for x, z in disque(CEN, CEN, 8.6):                 # ilot et trone
        d = dist(x, z)
        put(x, SOL + 1, z, QS if d < 5 else CAL)
    for x, z in disque(CEN, CEN, 7):
        put(x, SOL + 2, z, QS if dist(x, z) < 4 else DIO)
    for i in range(-3, 3):
        for j in range(-6, -2):
            put(C + i, SOL + 3, C + j, DPRI)
            put(C + i, SOL + 4, C + j, BLEU)
        for y in range(SOL + 4, SOL + 15):
            if y <= SOL + 14 - 3 * abs(i + .5):
                put(C + i, y, C - 6, BLEU if (y + i) % 3 else PACK)
                put(C + i, y, C - 7, PACK)
    put(C - 1, SOL + 4, C - 3, DPRI)
    put(C, SOL + 4, C - 3, DPRI)
    for a in (30, 150, 210, 330):
        x, z = cellule(13, a)
        pic(x, z, 2.6, 13 + int(a / 90), 30 + a)
    reserver(C, C, 21, "centre")
    return SOL


def glace_deco(cfg):
    n = 0
    for i in range(3000):
        x, z = cellule(rng.uniform(21, 46.5), rng.uniform(0, 360))
        if n >= 44 or not libre(x, z, 1):
            continue
        u = rng.random()
        if u < 0.6:
            r0 = rng.uniform(1.6, 3.4)
            pic(x, z, r0, int(r0 * rng.uniform(3, 5.5)), 100 + i % 50)
        elif u < 0.8:
            h = rng.randint(4, 9)
            for cx, cz in disque(x + .5, z + .5, 1.4):
                for y in range(SOL + 1, SOL + 1 + h - int(3 * h01(cx, 0, cz, 18))):
                    put(cx, y, cz, DIO if h01(cx, y, cz, 19) < 0.6 else CAL)
        else:
            r_ = rng.uniform(1.8, 2.8)
            for cx, cz in disque(x + .5, z + .5, r_):
                for y in range(SOL + 1, SOL + 1 + max(1, round((r_ - math.hypot(cx - x, cz - z)) * 1.2))):
                    put(cx, y, cz, NEIGE)
        reserver(x, z, 3, "deco")
        n += 1
    k = 0
    for a in (20, 200):                                # 2 pieges de powder snow
        for da in range(0, 90, 3):
            x, z = cellule(44, a + da)
            if libre(x, z, 3) and k < 2:
                for cx, cz in disque(x + .5, z + .5, 1.6):
                    put(cx, SOL, cz, POUDRE)
                    put(cx, SOL - 1, cz, POUDRE)
                    put(cx, SOL - 2, cz, PACK)
                put(x + 2, SOL, z, POUDRE)
                put(x + 2, SOL - 1, z, PACK)
                reserver(x, z, 4, "piege")
                PIEGES.append((x, SOL, z))
                k += 1
                break


PIEGES = []


def neige(cfg):
    for x in range(SX):
        for z in range(SZ):
            if dist(x, z) < 47.5 and nom((x, SOL, z)) in ("snow_block", "calcite", "polished_diorite") and vide((x, SOL + 1, z)):
                if bruit(x, z, 12, 20) > 0.55:
                    put(x, SOL + 1, z, COUCHE(1))


THEMES = {
    "feu": dict(nom="Feu", porte=270, origine=(-52, 68, 114), base=SOL, hmur=SOL + 12, mur=[(40, BKB), (30, NB), (20, RNB), (10, BLK)], accent=RNB,
                sol=[(40, NETH), (25, SOULSOIL), (20, BLK), (15, BAS)], chemin=[(55, PBK), (30, BKB), (15, CBK)], lum=SHROOM, verre=S("orange_stained_glass"),
                lanterne=SLAN, coeur=BKB, peau=[(50, NB), (30, RNB), (20, BKB)], cap=RNB, U=4, epais=1, salle=[(50, BKB), (25, NB), (25, BLK)], dalle=(PBK, GILD),
                coffre=GILD, construire=feu, deco=feu_deco, tours=(("carre", SOL + 22, [(50, BAS), (50, BLK)], RNB), ("rond", SOL + 26, [(60, NB), (40, RNB)], OBS))),
    "air": dict(nom="Air", porte=180, origine=(114, 68, -52), base=SOL + 14, hmur=SOL + 22, mur=[(45, QB), (30, CAL), (25, QS)], accent=CUC,
                sol=[(45, QB), (30, SST), (25, CAL)], chemin=[(60, QS), (40, WC)], lum=LUM, verre=G_C, lanterne=LANT, coeur=QZ, peau=[(50, QZ), (30, QB), (20, CAL)], cap=CUC,
                U=4, epais=1, salle=[(50, QB), (30, QS), (20, CUC)], dalle=(QS, CUC), coffre=QC, construire=air, deco=air_deco,
                tours=(("rond", SOL + 36, [(60, QZ), (40, QB)], CUC), ("carre", SOL + 34, [(60, WC), (40, QB)], PUR))),
    "nature": dict(nom="Nature", porte=0, origine=(-218, 68, -52), base=SOL, hmur=SOL + 12, mur=[(40, MSB), (25, MCB), (20, CSB), (15, SB)], accent=MOSS,
                   sol=[(62, GRASS), (20, MOSS), (10, POD), (8, RDIRT)], chemin=[(45, CDIRT), (30, MCB), (25, S("packed_mud"))], lum=SHROOM, verre=S("lime_stained_glass"),
                   lanterne=LANT, coeur=LOG("oak"), peau=[(60, F("oak")), (40, F("azalea"))], cap=F("oak"), U=5, epais=3, salle=[(50, MSB), (30, S("mud_bricks")), (20, MCB)],
                   dalle=(MOSS, MCB), coffre=S("chiseled_stone_bricks"), construire=nature, deco=nature_deco,
                   tours=(("rond", SOL + 22, [(60, MSB), (40, MCB)], S("chiseled_stone_bricks")), ("carre", SOL + 26, [(55, MSB), (45, CSB)], LOG("spruce")))),
    "glace": dict(nom="Glace", porte=90, origine=(-52, 68, -218), base=SOL, hmur=SOL + 12, mur=[(35, PACK), (25, BLEU), (20, NEIGE), (20, DIO)], accent=NEIGE,
                  sol=[(72, NEIGE), (14, PACK), (14, CAL)], chemin=[(45, DIO), (30, PACK), (25, WC)], lum=FROG, verre=G_B, lanterne=SLAN, coeur=DSB,
                  peau=[(55, PACK), (45, NEIGE)], cap=BLEU, U=5, epais=3, salle=[(45, DIO), (30, PACK), (25, DSB)], dalle=(PRI, DIO), coffre=DPRI, construire=glace, deco=glace_deco,
                  niveau=11, tours=(("rond", SOL + 24, [(60, PACK), (40, BLEU)], BLEU), ("carre", SOL + 22, [(60, DSB), (40, S("polished_deepslate"))], DPRI))),
}
TEXTES = {
    "feu": ("En haut de la tour : échelle jusqu'à la chambre sous le toit", "Trappe au fond d'un cul-de-sac du labyrinthe", "Sous l'autel : la stèle sud-est est creuse, ouverte à l'ouest, échelle", "Massif contre le rempart, en face de la porte : ouverture derrière le pan de mur décalé"),
    "air": ("En haut de la tour : échelle jusqu'à la chambre sous le toit", "Trappe au fond d'un cul-de-sac : salle suspendue sous le plateau", "Sous le sanctuaire : la stèle sud-est est creuse, ouverte à l'ouest, échelle", "Massif contre le rempart, en face de la porte : ouverture derrière le pan de mur décalé"),
    "nature": ("En haut de la tour : échelle jusqu'à la chambre sous le toit", "Trappe sous le tapis de mousse, au fond d'un cul-de-sac (casser le tapis, puis ouvrir)", "Sous l'île : la stèle sud-est est creuse, ouverte à l'ouest, échelle", "Massif contre le rempart, en face de la porte : ouverture derrière le pan de mur décalé"),
    "glace": ("En haut de la tour : échelle jusqu'à la chambre sous le toit", "Trappe sous la neige, au fond d'un cul-de-sac (casser la neige, puis ouvrir)", "Sous le lac gelé : la stèle sud-est est creuse, ouverte à l'ouest, échelle", "Massif contre le rempart, en face de la porte : ouverture derrière le pan de mur décalé"),
}


def fabrique(cle, cfg):
    global rng
    blocs.clear()
    occ.clear()
    ECHELLES.clear()
    SALLES.clear()
    APPS.clear()
    CIBLES.clear()
    PIEGES.clear()
    rng = random.Random(hash(cle) % 1000 if False else sum(map(ord, cle)))
    print("==", cfg["nom"], flush=True)
    B = cfg["base"]
    y_sol = cfg["construire"](cfg)
    murs(cfg)
    verifier("sol, murs, centre")
    t1, t2 = (cfg["porte"] + 135) % 360, (cfg["porte"] + 315) % 360
    f1, h1, m1, b1 = cfg["tours"][0]
    f2, h2, m2, b2 = cfg["tours"][1]
    cx, cz, H1 = tour(cfg, t1, f1, h1, m1, b1, "1", True)
    tour(cfg, t2, f2, h2, m2, b2, "2", False)
    labs = labyrinthes(cfg)
    print("   labyrinthes :", [(len(l["cases"]), len(l["imp"])) for l in labs])
    verifier("tours, labyrinthes")
    T = TEXTES[cle]
    put(cx - 2, H1, cz, cfg["coffre"])
    put(cx - 2, H1 + 1, cz, AIR)
    put(cx - 2, H1 + 2, cz, AIR)
    SALLES.append((1, (cx - 2, H1 + 1, cz), T[0]))
    couv = TAPIS if cle == "nature" else COUCHE(1) if cle == "glace" else None
    salle_trappe(cfg, 2, labs, couv, T[1])
    salle_centre(cfg, 3, T[2], y_sol)
    salle_mur(cfg, 4, T[3])
    verifier("salles")
    cfg["deco"](cfg)
    apparitions(cfg)
    if cle == "glace":
        neige(cfg)
    for k, v in CIBLES.items():
        if k.startswith("labyrinthe"):
            put(v[0], v[1], v[2], AIR)
            put(v[0], v[1] + 1, v[2], AIR)
    n_l = lumiere(cfg)
    if cle == "glace":
        for p, s in list(blocs.items()):
            if s[0] == "minecraft:snow":
                x, y, z = p
                pres = any(nom((x + a, y + b, z + c)) in ("sea_lantern", "pearlescent_froglight", "soul_lantern") for a in range(-3, 4) for b in range(-3, 4) for c in range(-3, 4))
                if pres and nom((x, y - 1, z)) != "oak_trapdoor":
                    put(*p, AIR)
    verifier("decor, lumiere")
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
    racine = nbtlib.File({"DataVersion": Int(3955), "size": List[Int]([Int(SX), Int(SY), Int(SZ)]), "palette": List[Compound](palette), "blocks": List[Compound](liste),
                          "entities": List[Compound]([])}, gzipped=True)
    racine.save(RACINE / "exports" / f"mini_{cle}.nbt")
    if STRUCT.exists():
        racine.save(STRUCT / f"mini_{cle}.nbt")
    x, z = gl(48, 0, cfg["porte"])
    cib = dict(CIBLES)
    cib.update({f"salle {n}": list(p) for n, p, _ in SALLES})
    cib.update({f"apparition {i + 1}": list(p) for i, p in enumerate(APPS)})
    (RACINE / "exports" / f"mini_{cle}_cibles.json").write_text(json.dumps({"_marque": "[claude-build]", "sol": SOL, "depart": [x, SOL + 1, z], "cibles": cib}, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"   ecrit : {len(liste)} blocs, palette {len(palette)}, lumieres ajoutees {n_l}", flush=True)
    return dict(nom=cfg["nom"], origine=cfg["origine"], salles=sorted(SALLES), apps=list(APPS), pieges=list(PIEGES), blocs=dict(blocs))


RES = {k: fabrique(k, v) for k, v in THEMES.items()}


def w(o, p):
    return f"{o[0] + p[0]} {o[1] + p[1]} {o[2] + p[2]}"


md = ["<!-- [claude-build] genere par sources/arenes_mini.py -->", "# Salles cachées — monde Arene Hub (arènes Ø104)", "",
      "Coordonnées dans le monde Arene Hub. Coffre = case vide où le poser. Le spawn du hub est en 0 87 20.", ""]
for k, r in RES.items():
    o = r["origine"]
    md += [f"## {r['nom']}", "", "| N° | Coffre (X Y Z) | Accès |", "|---|---|---|"]
    md += [f"| {n} | {w(o, p)} | {a} |" for n, p, a in r["salles"]]
    md += ["", "Apparitions : " + " ; ".join(w(o, p) for p in r["apps"]), ""]
    if r["pieges"]:
        md += ["Pièges de powder snow : " + " ; ".join(w(o, p) for p in r["pieges"]), ""]
(RACINE / "salles_cachees_hub.md").write_text("\n".join(md) + "\n", encoding="utf-8")

from PIL import Image, ImageDraw
COUL = {"polished_blackstone": (60, 55, 65), "polished_blackstone_bricks": (70, 65, 75), "cracked_polished_blackstone_bricks": (66, 60, 70), "blackstone": (45, 40, 48), "nether_bricks": (60, 30, 35),
        "red_nether_bricks": (90, 15, 20), "netherrack": (120, 40, 40), "soul_soil": (80, 62, 50), "soul_sand": (95, 75, 60), "basalt": (80, 80, 85), "obsidian": (25, 15, 40), "crying_obsidian": (70, 20, 120),
        "magma_block": (190, 70, 20), "lava": (255, 110, 0), "gilded_blackstone": (180, 140, 40), "shroomlight": (245, 170, 80), "quartz_block": (235, 231, 224), "quartz_pillar": (236, 232, 226),
        "smooth_quartz": (238, 234, 228), "quartz_bricks": (230, 226, 218), "chiseled_quartz_block": (226, 222, 212), "calcite": (222, 222, 216), "smooth_stone": (165, 165, 165), "white_concrete": (214, 218, 220),
        "white_wool": (250, 250, 250), "snow_block": (240, 248, 252), "waxed_oxidized_cut_copper": (78, 158, 132), "cyan_stained_glass": (60, 170, 190), "light_blue_stained_glass": (120, 190, 240),
        "mossy_stone_bricks": (108, 118, 92), "mossy_cobblestone": (100, 115, 85), "cracked_stone_bricks": (115, 115, 115), "stone_bricks": (125, 125, 125), "moss_block": (88, 125, 45), "grass_block": (96, 150, 62),
        "podzol": (92, 66, 34), "coarse_dirt": (112, 82, 56), "rooted_dirt": (130, 100, 72), "packed_mud": (140, 104, 78), "water": (50, 100, 205), "oak_wood": (105, 80, 50), "oak_leaves": (70, 130, 45),
        "azalea_leaves": (95, 140, 50), "flowering_azalea_leaves": (150, 130, 130), "dark_oak_leaves": (45, 100, 35), "spruce_leaves": (45, 90, 60), "jungle_leaves": (55, 150, 40), "cherry_leaves": (240, 170, 200),
        "packed_ice": (150, 190, 240), "blue_ice": (90, 150, 240), "polished_diorite": (195, 195, 195), "deepslate_bricks": (72, 72, 76), "dark_prismarine": (50, 95, 80), "prismarine": (90, 165, 150), "light_blue_concrete": (70, 170, 220)}
CACHE = {"air", "barrier", "light", "lantern", "soul_lantern", "chain", "white_stained_glass_pane", "ladder", "fire", "soul_fire", "snow", "short_grass", "fern", "moss_carpet", "vine", "lily_pad"} | PLANTES
s_ = 4
cw = (SX + SZ) * s_ + 20
ch = (SX + SZ) * s_ // 2 + SY * s_ + 40
img = Image.new("RGB", (cw * 2, ch * 2), (130, 175, 215))
for i, (k, r) in enumerate(RES.items()):
    dr = ImageDraw.Draw(img)
    ox, oy = (i % 2) * cw, (i // 2) * ch
    B = r["blocs"]

    def ouvert(p):
        s = B.get(p)
        return (p[1] > SOL) if s is None else s[0].split(":")[1] in CACHE

    vis = [(p, s[0].split(":")[1]) for p, s in B.items() if s[0].split(":")[1] not in CACHE]
    vis.sort(key=lambda t: (t[0][0] + t[0][2], t[0][1]))
    for (x, y, z), n in vis:
        c = COUL.get(n, (170, 170, 170))
        px = ox + (x - z) * s_ + SZ * s_ + 10
        py = oy + (x + z) * s_ // 2 - y * s_ + SY * s_ + 20
        if ouvert((x, y + 1, z)):
            dr.polygon([(px, py), (px + s_, py + s_ // 2), (px, py + s_), (px - s_, py + s_ // 2)], fill=c)
        if ouvert((x + 1, y, z)):
            dr.polygon([(px + s_, py + s_ // 2), (px, py + s_), (px, py + 2 * s_), (px + s_, py + s_ + s_ // 2)], fill=tuple(int(v * .78) for v in c))
        if ouvert((x, y, z + 1)):
            dr.polygon([(px - s_, py + s_ // 2), (px, py + s_), (px, py + 2 * s_), (px - s_, py + s_ + s_ // 2)], fill=tuple(int(v * .62) for v in c))
    dr.text((ox + 12, oy + 8), r["nom"], fill=(255, 255, 255))
img.save(RACINE / "exports" / "mini_vue.png")
print("Fini.")
