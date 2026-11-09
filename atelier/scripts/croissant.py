# [claude-build]
"""
croissant.py - Arene glace "Croissant" Ø104 : labyrinthe en croissant de lune, tour a escalier effondre, pieges de poudreuse.
2026-11-08 : labyrinthe glissant (glace compactee, glace bleue aux endroits strategiques, plaques de neige et de calcite) ; plus de poudreuse dans le labyrinthe.
Sol Y86, centre X0 Z0. Aucune echelle, aucune eau, aucun bloc ice. Style calme : grandes plaques, pas de melange bloc par bloc.
  .venv\\Scripts\\python.exe sources\\croissant.py
Controle : sources\\verif_acces.py exports\\croissant.nbt exports\\croissant_cibles.json
"""
import json
import math
import random
from collections import Counter, defaultdict, deque
from pathlib import Path

import nbtlib
import numpy as np
from nbtlib import Compound, Int, List, String

RACINE = Path(__file__).resolve().parent.parent
SAVE = RACINE / "launcher/instances/Build-1.21.1/minecraft/saves/Nouveau monde"
NOM = "croissant"
SX = SZ = 104
SOL = 6
SY = SOL + 48
CEN = 52.0
R_EXT, R_INT, DEC = 52.0, 38.0, 20.0                  # adapte a Ø104 (prompt : 60, 45, 25)
ICX = CEN + DEC                                        # centre du cercle interieur, et de la tour
H_TOUR = 40
ORIGINE = (-52, 86 - SOL, -52)
rng = random.Random(1042)


def S(n, **p):
    return ("minecraft:" + n, tuple(sorted((k, str(v).lower() if isinstance(v, bool) else str(v)) for k, v in p.items())))


AIR = S("air")
NEIGE, PACK, BLEU = S("snow_block"), S("packed_ice"), S("blue_ice")
CAL, DIO, QS, QP = S("calcite"), S("polished_diorite"), S("smooth_quartz"), S("quartz_pillar", axis="y")
SB, DSB, DST = S("stone_bricks"), S("deepslate_bricks"), S("deepslate_tiles")
POUDRE = S("powder_snow")
SLAN = S("soul_lantern", hanging=False, waterlogged=False)
SLAN_P = S("soul_lantern", hanging=True, waterlogged=False)
CHAINE = S("chain", axis="y", waterlogged=False)
LUMIERE = S("light", level=11, waterlogged=False)     # 11 : ne fait pas fondre la neige
blocs = {}


def put(x, y, z, s):
    if x != int(x) or y != int(y) or z != int(z):
        raise ValueError(f"coordonnee non entiere {(x, y, z)}")
    x, y, z = int(x), int(y), int(z)
    if not (0 <= x < SX and 0 <= y < SY and 0 <= z < SZ):
        raise ValueError(f"hors gabarit {(x, y, z)}")
    blocs[(x, y, z)] = s


def nom(p):
    s = blocs.get(p)
    return None if s is None else s[0].split(":")[1]


def vide(p):
    n = nom(p)
    return (p[1] > SOL) if n is None else n == "air"


def solide(p):
    n = nom(p)
    return (p[1] <= SOL) if n is None else n not in ("air", "snow", "powder_snow", "light", "soul_lantern", "chain")


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


def rext(x, z):
    return math.hypot(x + .5 - CEN, z + .5 - CEN)


def rint_(x, z):
    return math.hypot(x + .5 - ICX, z + .5 - CEN)


def dans_croissant(x, z):
    return rext(x, z) < R_EXT and rint_(x, z) >= R_INT


def monde(x, y, z):
    return ORIGINE[0] + x, ORIGINE[1] + y, ORIGINE[2] + z


# ====================================================================== 1. sol
for x in range(SX):
    for z in range(SZ):
        if rext(x, z) < R_EXT:
            put(x, SOL, z, NEIGE)
print("[1] sol :", len(blocs), flush=True)

# ====================================================================== 2. croissant et labyrinthe
U = 5


def case_ok(i, j):
    x0, z0 = 1 + U * i, 51 + U * j
    return all(rext(x, z) <= R_EXT - 2.5 and rint_(x, z) >= R_INT + 2.5 for x in (x0, x0 + 1) for z in (z0, z0 + 1))


cases = {(i, j) for i in range(-1, 22) for j in range(-11, 11) if case_ok(i, j)}
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
depart_c = min(cases)
passages, vus, pile = set(), {depart_c}, [depart_c]
while pile:
    i, j = pile[-1]
    v = [c for c in ((i + 1, j), (i - 1, j), (i, j + 1), (i, j - 1)) if c in cases and c not in vus]
    if not v:
        pile.pop()
        continue
    c = rng.choice(v)
    passages.add(frozenset(((i, j), c)))
    vus.add(c)
    pile.append(c)
for i, j in sorted(cases):                             # quelques boucles
    for c in ((i + 1, j), (i, j + 1)):
        if c in cases and frozenset(((i, j), c)) not in passages and rng.random() < 0.1:
            passages.add(frozenset(((i, j), c)))

couloir = set()


def interieur(c):
    x0, z0 = 1 + U * c[0], 51 + U * c[1]
    return [(x, z) for x in (x0, x0 + 1) for z in (z0, z0 + 1)]


for c in cases:
    couloir.update(interieur(c))
for p in passages:
    a, b = sorted(p)
    x0, z0 = 1 + U * a[0], 51 + U * a[1]
    if b[0] != a[0]:
        couloir.update((x0 + k, z0 + m) for k in range(2, U) for m in (0, 1))
    else:
        couloir.update((x0 + m, z0 + k) for k in range(2, U) for m in (0, 1))

# pointes du croissant : intersection des deux cercles
px_ = (R_EXT ** 2 - R_INT ** 2 + DEC ** 2) / (2 * DEC)
pz_ = math.sqrt(R_EXT ** 2 - px_ ** 2)
POINTES = {"entree": (CEN + px_, CEN - pz_), "sortie": (CEN + px_, CEN + pz_)}
PORTES = {}
for k, (tx, tz) in POINTES.items():
    c = min(cases, key=lambda c: math.hypot(1 + U * c[0] + 1 - tx, 51 + U * c[1] + 1 - tz))
    x, z = 1 + U * c[0] + 1.0, 51 + U * c[1] + 1.0
    L = math.hypot(tx - x, tz - z)
    ux, uz = (tx - x) / L, (tz - z) / L
    t, sortie = 0.0, None
    while t < L + 12:
        dedans = False
        for a in (0, 1):
            for b in (0, 1):
                cx, cz = math.floor(x + ux * t) + a, math.floor(z + uz * t) + b
                if dans_croissant(cx, cz):
                    couloir.add((cx, cz))
                    sortie = (cx, cz)
                    dedans = True
        if not dedans:
            break
        t += 0.5
    PORTES[k] = (c, sortie)

# le chemin d'entree a sortie existe-t-il dans les couloirs ?
def parcours(depart):
    vus, fi = {depart: None}, deque([depart])
    while fi:
        p = fi.popleft()
        for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (p[0] + d[0], p[1] + d[1])
            if q in couloir and q not in vus:
                vus[q] = p
                fi.append(q)
    return vus


relies = parcours(interieur(PORTES["entree"][0])[0])
couloir &= set(relies)                                 # une case touchee en diagonale seulement n'est pas un couloir
for k, (tx, tz) in POINTES.items():
    PORTES[k] = (PORTES[k][0], min((c for c in relies if rext(*c) <= 48.8), key=lambda c: math.hypot(c[0] + .5 - tx, c[1] + .5 - tz)))
a0, b0 = PORTES["entree"][1], PORTES["sortie"][1]
vus = parcours(a0)
if b0 not in vus:
    print("cases", len(cases), "couloir", len(couloir), "atteints", len(vus), "entree", a0, "sortie", b0, "cellules", PORTES)
    raise SystemExit("pas de chemin entre l'entree et la sortie")
chemin, p = [], b0
while p:
    chemin.append(p)
    p = vus[p]
BOUCHES = []                                           # chaque pointe debouche dans le creux, cote tour
for k in ("entree", "sortie"):
    x, z = PORTES[k][1]
    L = math.hypot(ICX - x, CEN - z)
    ux, uz = (ICX - x) / L, (CEN - z) / L
    t = 0.0
    while t < 14:
        px, pz = math.floor(x + .5 + ux * t), math.floor(z + .5 + uz * t)
        for a in (0, 1):
            for b in (0, 1):
                if dans_croissant(px + a, pz + b):
                    couloir.add((px + a, pz + b))
        if not dans_croissant(px, pz):
            break
        t += 0.5
    BOUCHES.append((px, pz))
print(f"[2] cases {len(cases)}, passages {len(passages)}, chemin entree-sortie : {len(chemin)} blocs, couloirs atteints {len(vus)}/{len(couloir)}", flush=True)

specks = 0                                             # plots isoles au bout des pointes : on ne garde que du mur continu
for x in range(SX):
    for z in range(SZ):
        if dans_croissant(x, z) and (x, z) not in couloir:
            voisins = sum(dans_croissant(x + a, z + b) and (x + a, z + b) not in couloir for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if voisins == 0:
                couloir.add((x, z))
                specks += 1
print("[2] plots isoles retires :", specks, flush=True)
for x in range(SX):
    for z in range(SZ):
        if not dans_croissant(x, z) or (x, z) in couloir:
            continue
        peau = any((x + a, z + b) in couloir or not dans_croissant(x + a, z + b) for a in (-1, 0, 1) for b in (-1, 0, 1))
        for y in range(SOL + 1, SOL + 6):
            if not peau:
                put(x, y, z, SB if bruit(x, z, 16, 2) > 0.5 else DSB)
            else:                                     # givre : grandes plaques, avec une bande basse de neige
                n = bruit(x + 2 * (y - SOL), z, 10, 3)
                put(x, y, z, NEIGE if y <= SOL + 1 + int(2 * bruit(x, z, 6, 4)) else BLEU if n > 0.58 else PACK)
        put(x, SOL + 6, z, NEIGE)

# linteaux de glace avec stalactites au-dessus de quelques passages
n_lin = 0
for p in sorted(passages, key=lambda p: sorted(p)):
    if n_lin >= 14 or rng.random() > 0.2:
        continue
    a, b = sorted(p)
    x0, z0 = 1 + U * a[0], 51 + U * a[1]
    for k in (0, 1):
        x, z = (x0 + 3, z0 + k) if b[0] != a[0] else (x0 + k, z0 + 3)
        put(x, SOL + 6, z, PACK)
        put(x, SOL + 5, z, BLEU)
        if k == 0:
            put(x, SOL + 4, z, BLEU)
    n_lin += 1
print("[2] croissant :", len(blocs), "blocs, linteaux", n_lin, flush=True)

# ====================================================================== 3. tour : paliers, escalier effondre, meurtrieres
RO, RI, RP = 7.5, 6.0, 1.6
PAS = 30                                               # 12 positions par tour, 2 manquantes : 10 marches pour 10 blocs
MANQUE = {0: {3, 8}, 1: {2, 6}, 2: {4, 9}, 3: {3, 7}}
A0 = 200                                               # les marches partent a droite de la porte (ouest)


def rt(x, z):
    return math.hypot(x + .5 - ICX, z + .5 - CEN)


def at(x, z):
    return math.degrees(math.atan2(z + .5 - CEN, x + .5 - ICX)) % 360


def pos(x, z):
    return int(((at(x, z) - A0) % 360) // PAS)


cellules = [(x, z) for x in range(int(ICX) - 9, int(ICX) + 9) for z in range(int(CEN) - 9, int(CEN) + 9)]
for x, z in cellules:
    d = rt(x, z)
    if d <= RO:
        for y in range(SOL + 1, SOL + H_TOUR + 1):
            e = y - SOL
            if d > RI:
                put(x, y, z, BLEU if bruit(at(x, z) / 6, e / 3, 3, 5) > 0.5 else PACK)
            elif d < RP:
                put(x, y, z, CAL)
            else:
                put(x, y, z, AIR)
        put(x, SOL, z, DST if d <= RI else PACK)
    if RI < d <= RO:                                   # creneaux
        put(x, SOL + H_TOUR + 1, z, BLEU)
        if int(at(x, z) / 15) % 2 == 0:
            put(x, SOL + H_TOUR + 2, z, BLEU)
            put(x, SOL + H_TOUR + 3, z, NEIGE)

MARCHES = []                                           # (volee, position, hauteur)
for v in range(4):
    n = 0
    for p in range(12):
        if p in MANQUE[v]:
            continue
        n += 1
        MARCHES.append((v, p, 10 * v + n))
for v, p, h in MARCHES:
    y = SOL + h
    mat = (CAL, QS, DSB)[v % 3]
    for x, z in cellules:
        if RP <= rt(x, z) <= RI and pos(x, z) == p:
            put(x, y, z, mat)
for v in range(1, 5):                                  # paliers, percés au-dessus de la fin de la volee
    y = SOL + 10 * v
    debut = min(p for vv, p, h in MARCHES if vv == v - 1 and h - 10 * (v - 1) >= 6)
    trou = set(range(debut, 12))                       # marches manquantes comprises : on saute sous cette ouverture
    for x, z in cellules:
        if RP <= rt(x, z) <= RI and (pos(x, z) not in trou or (v - 1, pos(x, z), 10 * v) in MARCHES):
            put(x, y, z, DST if v < 4 else QS)
            for k in (1, 2):
                if nom((x, y + k, z)) == "packed_ice":
                    put(x, y + k, z, AIR)
# marche d'arrivee : le dernier secteur de chaque volee est au niveau du palier
# meurtrieres : 1 de large, 2 de haut, tout autour, a chaque palier et au pied
n_m = 0
for v in range(0, 4):
    yb = SOL + 10 * v + 2 if v else SOL + 2
    for a in range(10, 360, 30):
        ux, uz = math.cos(math.radians(a)), math.sin(math.radians(a))
        for r_ in (6.2, 6.7, 7.2):
            x, z = math.floor(ICX + r_ * ux), math.floor(CEN + r_ * uz)
            put(x, yb, z, AIR)
            put(x, yb + 1, z, AIR)
        n_m += 1
# porte a l'ouest, face au labyrinthe
for r_ in np.arange(5.2, 8.2, 0.4):
    for s in (-1, 0, 1):
        x, z = math.floor(ICX - r_), math.floor(CEN + s)
        put(x, SOL, z, DST)
        for y in range(SOL + 1, SOL + 4):
            put(x, y, z, AIR)
# debris au pied de l'escalier
for i in range(14):
    a = math.radians(rng.uniform(0, 360))
    r_ = rng.uniform(2.2, 5.4)
    x, z = math.floor(ICX + r_ * math.cos(a)), math.floor(CEN + r_ * math.sin(a))
    if vide((x, SOL + 1, z)) and pos(x, z) not in (0, 1, 2) and abs(x - (ICX - 4)) > 2:
        put(x, SOL + 1, z, CAL if i % 2 else PACK)
for v in range(1, 5):                                  # lanternes des paliers, stalactites sous les paliers
    y = SOL + 10 * v
    for a in (A0 + 100, A0 + 190):
        x, z = math.floor(ICX + 4.5 * math.cos(math.radians(a))), math.floor(CEN + 4.5 * math.sin(math.radians(a)))
        if solide((x, y, z)) and vide((x, y + 1, z)):
            put(x, y + 1, z, SLAN)
print("[3] tour :", len(blocs), "blocs, meurtrieres", n_m, flush=True)

# liste des sauts : marches non contigues
SAUTS = []
cells_de = {}
for v, p, h in MARCHES:
    cells_de[(v, p)] = [(x, z) for x, z in cellules if RP <= rt(x, z) <= RI and pos(x, z) == p]
for (v1, p1, h1), (v2, p2, h2) in zip(MARCHES, MARCHES[1:]):
    if v1 != v2 or p2 == p1 + 1:
        continue
    a, b = cells_de[(v1, p1)], cells_de[(v2, p2)]
    u, w = min(((c, e) for c in a for e in b), key=lambda t: math.hypot(t[0][0] - t[1][0], t[0][1] - t[1][1]))
    ecart = max(abs(u[0] - w[0]), abs(u[1] - w[1])) - 1
    SAUTS.append(dict(depart=monde(u[0], SOL + h1 + 1, u[1]), arrivee=monde(w[0], SOL + h2 + 1, w[1]), ecart=ecart, montee=h2 - h1))
    if not 1 <= ecart <= 3 or (h2 - h1 == 1 and ecart > 2):
        raise SystemExit(f"saut non faisable : {SAUTS[-1]}")
print("[3] sauts :", [(s["ecart"], s["montee"]) for s in SAUTS], flush=True)

# ====================================================================== 3b. rempart, porte, pics (idees de l'ancienne arene Glace)
BARRIERE = S("barrier", waterlogged=False)
BARS = S("iron_bars", waterlogged=False)
H_MUR = SOL + 12


def gate(al, sp):                                      # porte a l'est : al vers l'exterieur, sp en travers
    return 52 + al, 52 + sp


def pic(cx, cz, r0, h, g):
    for k in range(h + 1):
        r_ = r0 * (1 - k / h) ** 0.85
        for x in range(math.floor(cx - r_ - 1), math.ceil(cx + r_) + 2):
            for z in range(math.floor(cz - r_ - 1), math.ceil(cz + r_) + 2):
                if math.hypot(x - cx, z - cz) <= r_ + 0.3 and rext(x, z) < R_EXT and SOL + 1 + k < SY:
                    put(x, SOL + 1 + k, z, BLEU if bruit(x + k, z, 6, g) > 0.5 else PACK)


for x in range(SX):
    for z in range(SZ):
        d = rext(x, z)
        pres_pointe = any(math.hypot(x - a, z - b) < 7 for a, b in BOUCHES + [PORTES["entree"][1], PORTES["sortie"][1]])
        if not 48 <= d < R_EXT or (dans_croissant(x, z) and not ((x, z) in couloir and pres_pointe)):
            continue                                   # le rempart ne touche jamais au labyrinthe, sauf pour fermer le bout des pointes
        if abs(z + .5 - CEN) <= 5 and x > CEN:         # ouverture de la porte
            continue
        if d < 50.2 and pres_pointe:
            continue                                   # le rempart laisse passer les pointes du labyrinthe
        a_ = math.degrees(math.atan2(z + .5 - CEN, x + .5 - CEN)) % 360
        for y in range(SOL + 1, H_MUR + 1):
            put(x, y, z, NEIGE if y <= SOL + 2 + int(2 * bruit(a_, 0, 25, 6)) else BLEU if bruit(a_, y, 14, 7) > 0.55 else PACK)
        put(x, H_MUR + 1, z, NEIGE)
        if d >= 50.5 and int(a_ * 1.5) % 2 == 0:
            put(x, H_MUR + 2, z, DIO)
            put(x, H_MUR + 3, z, NEIGE)
for sp in range(-9, 9):                                # porte monumentale : pylones, arche, herse levee
    for al in range(43, 52):
        x, z = gate(al, sp)
        if rext(x, z) >= R_EXT:
            continue
        cote = sp < -5 or sp > 4
        for y in range(SOL + 1, SOL + 16):
            if cote or y > SOL + 9:
                put(x, y, z, NEIGE if y == SOL + 15 else BLEU if (y - SOL) % 6 < 3 else PACK)
            else:
                put(x, y, z, AIR)
        if not cote:
            put(x, SOL, z, DIO)
for sp in range(-5, 5):                                # dents de la herse levee
    x, z = gate(47, sp)
    put(x, SOL + 9, z, BARS)
    if sp % 2 == 0:
        put(x, SOL + 8, z, BARS)
for sp in (-8, 7):                                     # soul lanterns de chaque cote, a l'interieur
    x, z = gate(42, sp)
    put(x, SOL + 1, z, DIO)
    put(x, SOL + 2, z, SLAN)
pic(*gate(43, -11), 3.6, 24, 40)                       # pics encadrant l'entree
pic(*gate(43, 10), 3.6, 24, 41)
PICS = [(-16, 30, 3.2, 20), (16, 30, 3.2, 18), (-24, 24, 2.6, 14), (24, 22, 2.6, 15), (-9, 37, 2.2, 10), (10, 38, 2.2, 11)]
for i, (dz, dx, r0, h) in enumerate(PICS):             # pics dans le creux, loin de l'axe de la porte et de la tour
    cx, cz = int(CEN) + dx, int(CEN) + dz
    if rint_(cx, cz) < R_INT - 4 and math.hypot(cx - ICX, cz - CEN) > 13:
        pic(cx, cz, r0, h, 42 + i)
for dz, dx in ((-29, 18), (29, 18), (0, 13)):          # congeres
    cx, cz = int(CEN) + dx, int(CEN) + dz
    for x in range(cx - 4, cx + 5):
        for z in range(cz - 4, cz + 5):
            e = math.hypot(x - cx, z - cz)
            if e <= 4 and rint_(x, z) < R_INT - 2 and vide((x, SOL + 1, z)):
                for y in range(SOL + 1, SOL + 1 + max(1, round((4 - e) * 0.7))):
                    put(x, y, z, NEIGE)
for x in range(SX):                                    # paroi invisible au-dessus de tout le pourtour
    for z in range(SZ):
        if 51 <= rext(x, z) < R_EXT and not (abs(z + .5 - CEN) <= 5 and x > CEN):
            for y in range(SOL + 1, SY):
                if vide((x, y, z)):
                    put(x, y, z, BARRIERE)


def libre_sol(p):
    return all(nom((p[0], y, p[1])) in (None, "air", "snow", "powder_snow", "light") for y in (SOL + 1, SOL + 2))


vus_c, fi = {(92, 52)}, deque([(92, 52)])              # on se promene a pied depuis l'interieur, porte fermee pour le test
sortis = 0
while fi:
    x, z = fi.popleft()
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        q = (x + dx, z + dz)
        if q in vus_c or not (0 <= q[0] < SX and 0 <= q[1] < SZ) or not libre_sol(q) or (q[0] > 94 and abs(q[1] + .5 - CEN) <= 5):
            continue
        if rext(*q) >= R_EXT:
            sortis += 1
            continue
        vus_c.add(q)
        fi.append(q)
print("[3b] porte fermee : cases hors du cercle atteintes =", sortis, "| entree", PORTES["entree"][1] in vus_c, "| sortie", PORTES["sortie"][1] in vus_c, flush=True)
if sortis or PORTES["entree"][1] not in vus_c or PORTES["sortie"][1] not in vus_c:
    print("   a corriger")

# ====================================================================== 4. pieges de poudreuse (8)
PIEGES = []
hors_chemin = [c for c in sorted(cases) if not any(p in set(chemin) for p in interieur(c)) and c not in (PORTES["entree"][0], PORTES["sortie"][0])]
rng.shuffle(hors_chemin)
pris = []
for c in hors_chemin:
    x0, z0 = 1 + U * c[0], 51 + U * c[1]
    if len(pris) >= 8:
        break
    if any(math.hypot(x0 - a, z0 - b) < 11 for a, b in pris):
        continue
    pris.append((x0, z0))
    put(x0, SOL, z0, POUDRE)                           # marche : 1 bloc de fond
    put(x0, SOL - 1, z0, PACK)
    put(x0, SOL, z0 + 1, POUDRE)                       # 2 blocs de fond
    put(x0, SOL - 1, z0 + 1, POUDRE)
    PIEGES.append((x0, SOL, z0 + 1))
for a in (70, 150, 290):                               # autour de la base de la tour
    x, z = math.floor(ICX + 11 * math.cos(math.radians(a))), math.floor(CEN + 11 * math.sin(math.radians(a)))
    put(x, SOL, z, POUDRE)
    put(x, SOL - 1, z, PACK)
    put(x + 1, SOL, z, POUDRE)
    put(x + 1, SOL - 1, z, POUDRE)
    PIEGES.append((x + 1, SOL, z))
print("[4] pieges :", len(PIEGES), flush=True)

# ====================================================================== 4b. obstacles et poudreuse en plus, dans le creux
def place_libre(x, z, r):
    for a in range(-r, r + 1):
        for b in range(-r, r + 1):
            p = (x + a, z + b)
            if not (0 <= p[0] < SX and 0 <= p[1] < SZ) or nom((p[0], SOL, p[1])) != "snow_block" or not vide((p[0], SOL + 1, p[1])):
                return False
            if rint_(*p) >= R_INT - 3 or rext(*p) >= 46 or rt(*p) < 11 or abs(p[1] + .5 - CEN) < 5:
                return False
    return True


def obstacle(k, x, z, a, rng):
    if k == 0:                                         # arche de glace : on passe dessous
        long_x = abs(math.cos(a)) < 0.7
        for s in range(-3, 4):
            haut = SOL + 5 - (abs(s) == 3) - (abs(s) == 2) * 0
            for e in (0, 1):
                cx, cz = (x + s, z + e) if long_x else (x + e, z + s)
                if abs(s) == 3:
                    for y in range(SOL + 1, SOL + 5):
                        put(cx, y, cz, PACK)
                else:
                    put(cx, haut, cz, BLEU)
                    if abs(s) == 2:
                        put(cx, haut - 1, cz, PACK)
                put(cx, SOL + 5 + (abs(s) < 2), cz, NEIGE) if abs(s) < 3 else None
    elif k == 1:                                       # eclat de glace penche
        tx, tz = rng.uniform(-.35, .35), rng.uniform(-.35, .35)
        h = rng.randint(7, 10)
        for e in range(h):
            r2 = 1.7 * (1 - e / h) ** 0.8
            ax, az = x + .5 + tx * e, z + .5 + tz * e
            for u in range(math.floor(ax - r2 - 1), math.ceil(ax + r2) + 1):
                for v in range(math.floor(az - r2 - 1), math.ceil(az + r2) + 1):
                    if math.hypot(u + .5 - ax, v + .5 - az) <= r2 + 0.2:
                        put(u, SOL + 1 + e, v, BLEU if e % 4 < 2 else PACK)
    else:                                              # rocher d'ardoise enneige
        r2 = rng.uniform(2.1, 2.9)
        for u in range(x - 3, x + 4):
            for v in range(z - 3, z + 4):
                e = math.hypot(u - x, v - z)
                if e <= r2:
                    n = max(1, round((r2 - e) * 1.3 + 0.6))
                    for y in range(SOL + 1, SOL + 1 + n):
                        put(u, y, v, NEIGE if y == SOL + n else S("cobbled_deepslate"))


OBST = []
for i in range(4000):
    a = math.radians(rng.uniform(0, 360))
    r_ = rng.uniform(12, 33)
    x, z = math.floor(ICX + r_ * math.cos(a)), math.floor(CEN + r_ * math.sin(a))
    if len(OBST) >= 12 or not place_libre(x, z, 3) or any(math.hypot(x - u, z - v) < 9 for u, v in OBST):
        continue
    OBST.append((x, z))
    obstacle(len(OBST) % 3, x, z, a, rng)
n_ajout = 0
for i in range(4000):                                  # 5 pieges de plus, a l'ecart des obstacles : 16 au total
    a = math.radians(rng.uniform(0, 360))
    r_ = rng.uniform(13, 30)
    x, z = math.floor(ICX + r_ * math.cos(a)), math.floor(CEN + r_ * math.sin(a))
    if n_ajout >= 5 or not place_libre(x, z, 2) or any(math.hypot(x - p[0], z - p[2]) < 9 for p in PIEGES):
        continue
    put(x, SOL, z, POUDRE)
    put(x, SOL - 1, z, PACK)
    put(x + 1, SOL, z, POUDRE)
    put(x + 1, SOL - 1, z, POUDRE)
    PIEGES.append((x + 1, SOL, z))
    n_ajout += 1
rng2 = random.Random(2042)                             # 2e vague : tirage a part, l'existant ne bouge pas
n_plus = 0
for i in range(6000):
    a = math.radians(rng2.uniform(0, 360))
    r_ = rng2.uniform(11.5, 34)
    x, z = math.floor(ICX + r_ * math.cos(a)), math.floor(CEN + r_ * math.sin(a))
    if n_plus >= 14 or not place_libre(x, z, 3) or any(math.hypot(x - u, z - v) < 7 for u, v in OBST) or any(math.hypot(x - p[0], z - p[2]) < 5 for p in PIEGES):
        continue
    OBST.append((x, z))
    obstacle(n_plus % 3, x, z, a, rng2)
    n_plus += 1
print("[4b] obstacles", len(OBST), "| pieges au total", len(PIEGES), flush=True)

# ====================================================================== 5. decoration et eclairage
# congeres : quelques couches de neige dans les coins et les culs-de-sac
imp = [c for c in sorted(cases) if sum(c in p for p in passages) == 1]
for c in imp:
    for x, z in interieur(c):
        if nom((x, SOL, z)) == "snow_block" and vide((x, SOL + 1, z)):
            put(x, SOL + 1, z, S("snow", layers=2))
for x in range(SX):
    for z in range(SZ):
        if rext(x, z) < R_EXT - 1 and rint_(x, z) < R_INT and rt(x, z) > 9 and nom((x, SOL, z)) == "snow_block" and bruit(x, z, 9, 9) > 0.62 and vide((x, SOL + 1, z)):
            put(x, SOL + 1, z, S("snow", layers=1))

# ====================================================================== 4c. labyrinthe glissant (2026-11-08) : plus de poudreuse, sol de glace
# Les 8 premiers pieges sont dans le labyrinthe : on comble leurs fosses. Les 8 autres (tour et creux) restent.
PIEGES_LAB, PIEGES_HORS = PIEGES[:8], PIEGES[8:]
for px, py, pz in PIEGES_LAB:
    for z in (pz - 1, pz):
        assert (px, z) in couloir, ("piege hors couloir", px, z)
        put(px, SOL - 1, z, SB)                        # fond plein ; le sol de glace est pose juste apres
VOIS = defaultdict(list)
for p in passages:
    a, b = tuple(p)
    VOIS[a].append(b)
    VOIS[b].append(a)


def cases_passage(a, b):
    a, b = sorted((a, b))
    x0, z0 = 1 + U * a[0], 51 + U * a[1]
    if b[0] != a[0]:
        return [(x0 + k, z0 + m) for k in range(2, U) for m in (0, 1)]
    return [(x0 + m, z0 + k) for k in range(2, U) for m in (0, 1)]


def virage(c):
    v = VOIS[c]
    return len(v) == 2 and (v[0][0] - c[0]) * (v[1][0] - c[0]) + (v[0][1] - c[1]) * (v[1][1] - c[1]) == 0


GLISSE = set()
ZONES_B = Counter()
for c in sorted(cases):
    v = VOIS[c]
    if len(v) >= 3:                                    # intersections
        GLISSE.update(interieur(c))
        ZONES_B["intersections"] += 1
    elif virage(c) and any(virage(n) and set(VOIS[n]) - {c} and (lambda m: (m[0] - n[0], m[1] - n[1]) != (n[0] - c[0], n[1] - c[1]))(next(iter(set(VOIS[n]) - {c}))) for n in v) and h01(c[0], 5, c[1], 79) < 0.6:
        # virages serres : un virage suivi aussitot d'un autre (zigzag), pas tous
        GLISSE.update(interieur(c))
        ZONES_B["virages serrés"] += 1
    elif len(v) == 1:                                  # devant les impasses : on glisse jusqu'au mur
        GLISSE.update(cases_passage(c, v[0]))
        ZONES_B["devant les impasses"] += 1
for c in sorted(cases):                                # couloirs longs : 3 passages droits ou plus, on glisse au-dela du tournant
    for v in VOIS[c]:
        d = (v[0] - c[0], v[1] - c[1])
        prec = (c[0] - d[0], c[1] - d[1])
        if prec in VOIS[c]:
            continue                                   # c n'est pas le debut d'une ligne droite dans ce sens
        ligne = [c]
        while True:
            suiv = (ligne[-1][0] + d[0], ligne[-1][1] + d[1])
            if suiv not in VOIS[ligne[-1]]:
                break
            ligne.append(suiv)
        if len(ligne) >= 5:                            # 4 passages droits ou plus : le dernier, juste avant le tournant
            GLISSE.update(cases_passage(ligne[-2], ligne[-1]))
            ZONES_B["couloirs longs"] += 1
LAB = {(x, z) for x, z in couloir if nom((x, SOL + 1, z)) in (None, "air", "snow", "light", "soul_lantern")}   # on y marche (pas sous le rempart ni la paroi)
GLISSE &= LAB
PLAQUES = set()
for x, z in sorted(LAB - GLISSE, key=lambda c: h01(c[0], 3, c[1], 77)):
    if len(PLAQUES) >= round(0.10 * len(LAB)):
        break
    if any((x + a, z + b) in PLAQUES for a in (-1, 0, 1) for b in (-1, 0, 1)):
        continue                                       # jamais deux plaques cote a cote
    PLAQUES.add((x, z))
for x, z in LAB:
    put(x, SOL, z, (NEIGE if h01(x, 4, z, 78) < 0.5 else CAL) if (x, z) in PLAQUES else BLEU if (x, z) in GLISSE else PACK)
    if nom((x, SOL + 1, z)) == "snow":                 # congeres retirees : le sol reste plat
        put(x, SOL + 1, z, AIR)
SOL_LAB = Counter(nom((x, SOL, z)) for x, z in LAB)
print("[4c] labyrinthe glissant :", len(LAB), "cases de sol", dict(SOL_LAB), "| zones de glace bleue", dict(ZONES_B), "| pieges retires", len(PIEGES_LAB), flush=True)

SOURCES = {"soul_lantern": 10, "light": 11}
TRANSP = {"air", "snow", "powder_snow", "soul_lantern", "chain", "light", "barrier", "iron_bars"}
coffre_rien = set()


def calcul():
    op = np.zeros((SX, SY, SZ), dtype=bool)
    op[:, :SOL + 1, :] = True
    L = np.zeros((SX, SY, SZ), dtype=np.int8)
    for (x, y, z), s in blocs.items():
        n = s[0].split(":")[1]
        op[x, y, z] = n not in TRANSP
        if n in SOURCES:
            L[x, y, z] = SOURCES[n]
            op[x, y, z] = False
    for _ in range(11):
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
    return (p[1] > SOL) if n is None else n in ("air", "snow", "light")


total = 0
for passe in range(30):
    L = calcul()
    noires = sorted((x, y, z) for (x, y, z), s in blocs.items() if s[0].split(":")[1] not in TRANSP and y + 2 < SY and trav((x, y + 1, z)) and trav((x, y + 2, z)) and L[x, y + 1, z] == 0)
    if not noires:
        break
    pris_l = []
    for x, y, z in noires:
        if any(abs(x - a) < 9 and abs(z - b) < 9 and abs(y - c) < 5 for a, c, b in pris_l):
            continue
        pris_l.append((x, y, z))
        put(x, y + 1, z, LUMIERE)
        total += 1
if noires:
    raise SystemExit(f"surfaces noires {noires[:5]}")
retirees = 0
for (x, y, z), s in list(blocs.items()):
    if s[0] == "minecraft:snow" and any(nom((x + a, y + b, z + c)) == "soul_lantern" for a in range(-3, 4) for b in range(-3, 4) for c in range(-3, 4)):
        put(x, y, z, AIR)
        retirees += 1
print(f"[5] lumieres ajoutees {total}, couches retirees pres des lanternes {retirees}", flush=True)

err = []
for x, z in LAB:                                       # labyrinthe : ni poudreuse, ni marche, ni trou
    if nom((x, SOL, z)) not in ("packed_ice", "blue_ice", "snow_block", "calcite") or nom((x, SOL - 1, z)) == "powder_snow" or not vide((x, SOL + 1, z)) and nom((x, SOL + 1, z)) not in ("light", "soul_lantern"):
        err.append(f"sol du labyrinthe {(x, z)} {nom((x, SOL, z))} / dessus {nom((x, SOL + 1, z))}")
for x, z in PLAQUES:
    if any((x + a, z + b) in PLAQUES for a in (-1, 0, 1) for b in (-1, 0, 1) if (a, b) != (0, 0)):
        err.append(f"plaques cote a cote {(x, z)}")
for p, s in blocs.items():
    n = s[0].split(":")[1]
    if n in ("ice", "water", "ladder", "scaffolding", "vine", "chest", "barrel", "hopper", "spawner", "bubble_column", "lava"):
        err.append(f"interdit {n} {p}")
    if n != "air" and rext(p[0], p[2]) >= R_EXT + 0.01:
        err.append(f"hors du cercle {n} {p}")
    if n == "snow" and (nom((p[0], p[1] - 1, p[2])) in ("packed_ice", "blue_ice") or not solide((p[0], p[1] - 1, p[2]))):
        err.append(f"neige mal posee {p}")
print("[controle] erreurs :", len(err), err[:6], flush=True)
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
    liste.append(Compound({"pos": List[Int]([Int(x), Int(y), Int(z)]), "state": Int(index[s])}))
racine = nbtlib.File({"DataVersion": Int(3955), "size": List[Int]([Int(SX), Int(SY), Int(SZ)]), "palette": List[Compound](palette), "blocks": List[Compound](liste), "entities": List[Compound]([])}, gzipped=True)
racine.save(RACINE / "exports" / f"{NOM}.nbt")
D = SAVE / "datapacks/atelier"
if (SAVE / "level.dat").exists():
    (D / "data/atelier/structure").mkdir(parents=True, exist_ok=True)
    (D / "data/atelier/function").mkdir(parents=True, exist_ok=True)
    racine.save(D / "data/atelier/structure" / f"{NOM}.nbt")
    if not (D / "pack.mcmeta").exists():
        (D / "pack.mcmeta").write_text('{"pack": {"pack_format": 48, "description": "Atelier [claude-build]"}}\n', encoding="utf-8")
    ox, oy, oz = ORIGINE
    l = [f"# [claude-build] Aplanit l'emprise de {NOM}."]
    for y in range(oy, oy + SY):
        b = "minecraft:snow_block" if y == 86 else "minecraft:packed_ice" if y < 86 else "minecraft:air"
        l.append(f"fill {ox} {y} {oz} {ox + SX - 1} {y} {oz + SZ - 1} {b}")
    (D / "data/atelier/function" / f"{NOM}_effacer.mcfunction").write_text("\n".join(l) + "\n", encoding="utf-8")
top = (math.floor(ICX + 3.6 * math.cos(math.radians(A0 + 150))), SOL + H_TOUR + 1, math.floor(CEN + 3.6 * math.sin(math.radians(A0 + 150))))
ci = {"sommet de la tour": list(top), "entree du labyrinthe": [PORTES["entree"][1][0], SOL + 1, PORTES["entree"][1][1]], "sortie du labyrinthe": [PORTES["sortie"][1][0], SOL + 1, PORTES["sortie"][1][1]]}
for v in (1, 2, 3):
    a = A0 + 150
    ci[f"palier {v}"] = [math.floor(ICX + 3.6 * math.cos(math.radians(a))), SOL + 10 * v + 1, math.floor(CEN + 3.6 * math.sin(math.radians(a)))]
(RACINE / "exports" / f"{NOM}_cibles.json").write_text(json.dumps({"_marque": "[claude-build]", "sol": SOL, "sauts_longs": True, "depart": [92, SOL + 1, 52], "cibles": ci}, ensure_ascii=False, indent=1), encoding="utf-8")


def w(p):
    return " ".join(str(v) for v in monde(*p))


md = ["<!-- [claude-build] genere par sources/croissant.py -->", "# Arène glace — le Croissant", "",
      "Monde `saves/Nouveau monde`, centre X0 Z0, sol Y86. Cercle Ø104, comme le hub.", "",
      f"- **Entrée du labyrinthe :** {w((PORTES['entree'][1][0], SOL + 1, PORTES['entree'][1][1]))} (pointe nord-est)",
      f"- **Sortie du labyrinthe :** {w((PORTES['sortie'][1][0], SOL + 1, PORTES['sortie'][1][1]))} (pointe sud-est)",
      f"- **Chemin entrée-sortie :** {len(chemin)} blocs", "", f"## Pièges de powder snow ({len(PIEGES_HORS)}, hors du labyrinthe)", ""]
md += [f"- {i + 1} : {w(p)} ({'au pied de la tour' if i < 3 else 'dans le creux'})" for i, p in enumerate(PIEGES_HORS)]
nb_ = len(LAB)
md += ["", "## Sol du labyrinthe (2026-11-08)", "",
       f"- {nb_} blocs de sol, de l'entrée à la sortie : glace compactée {SOL_LAB['packed_ice']} ({round(100 * SOL_LAB['packed_ice'] / nb_)} %), glace bleue {SOL_LAB['blue_ice']} ({round(100 * SOL_LAB['blue_ice'] / nb_)} %), "
       f"plaques non glissantes {SOL_LAB['snow_block'] + SOL_LAB['calcite']} ({round(100 * (SOL_LAB['snow_block'] + SOL_LAB['calcite']) / nb_)} % : neige {SOL_LAB['snow_block']}, calcite {SOL_LAB['calcite']}).",
       "- Glace bleue : " + ", ".join(f"{k} ({v})" for k, v in ZONES_B.items()) + ". Couloirs longs : le dernier passage d'une ligne droite de 4 passages ou plus, juste avant le tournant. Virages serrés : une partie des virages qui s'enchaînent en zigzag.",
       "- Plaques non glissantes isolées, jamais deux côte à côte (ni en diagonale), réparties sans motif.",
       f"- Les {len(PIEGES_LAB)} pièges de poudreuse du labyrinthe sont supprimés : fosses comblées (briques de pierre au fond, glace en surface), sol au même niveau que le couloir. Anciennes positions : " + " ; ".join(w(p) for p in PIEGES_LAB) + ".",
       "- Congères des culs-de-sac retirées : la neige en couche ne tient pas sur la glace, et le sol devait rester plat.",
       "- Aucun bloc de glace ordinaire : seulement glace compactée et glace bleue.", ""]
md += ["", "## Sauts de l'escalier de la tour", "", "| Départ (X Y Z) | Arrivée (X Y Z) | Écart | Montée |", "|---|---|---|---|"]
md += [f"| {' '.join(map(str, s['depart']))} | {' '.join(map(str, s['arrivee']))} | {s['ecart']} | {s['montee']} |" for s in SAUTS]
md += ["", "## Ce qui a été construit", "",
       f"- Croissant exact : rayon {R_EXT:g}, cercle intérieur de rayon {R_INT:g} décalé de {DEC:g} blocs vers l'est ; {len(cases)} cases, couloirs de 2 blocs, murs de 5 blocs plus un chaperon, cœur solide.",
       f"- Tour de {H_TOUR} blocs pour Ø15, 4 volées d'escalier effondré, paliers tous les 10 blocs, {n_m} meurtrières ouvertes, sommet crénelé.",
       f"- {len(PIEGES)} pièges de poudreuse à l'origine, {len(PIEGES_HORS)} depuis le 2026-11-08 (ceux du labyrinthe sont comblés), sans lumière de plus de niveau 11 près de la neige.",
       f"- Ajouts demandés après coup : {len(OBST)} obstacles dans le creux (arches de glace, éclats penchés, rochers enneigés), et 16 pièges au lieu des 10 du prompt.",
       "- Ajouts demandés après coup, repris de l'ancienne arène Glace : rempart de neige et de glace côté est, porte monumentale à l'est (X50 Z0) avec herse levée et pics, pics et congères dans le creux.", "", "## Écarts par rapport au prompt", "",
       "- Cotes réduites pour tenir en Ø104 : rayons 52 et 38, décalage 20, au lieu de 60, 45 et 25.",
       "- Construction par structure (.nbt + /place) au lieu de /fill en jeu.",
       "- 16 pièges au lieu de 10 au maximum, à la demande de l'utilisateur.",
       "- Sauts sans sprint : 2 blocs d'écart au plus quand on monte d'un bloc.",
       "- Couloirs de 2 blocs de large : au-delà, le labyrinthe perdrait trop de cases à cette taille.",
       "- La poudreuse a une texture proche de la neige, mais pas identique : elle reste repérable de très près.",
       f"- Éclairage : soul lanterns et {total} blocs de lumière invisibles de niveau 11, qui ne font pas fondre la neige.",
       "- 2026-11-08 : valeurs à compléter du prompt prises dans le projet (Minecraft 1.21.1, centre X0 Z0). Travaux faits par le générateur et un correctif à poser, pas par /fill ni /forceload. Murs, tour, escalier et pièges extérieurs inchangés.",
       "- 2026-11-08 : la fosse de chaque piège retiré est comblée en briques de pierre (bloc plein, invisible sous la glace) plutôt qu'en neige ou en glace.",
       "- 2026-11-08 : glace bleue un peu plus présente que « quelques endroits » (25 % du sol) : un seul passage par zone, mais les 4 types de zones demandés se croisent souvent dans un labyrinthe de 115 cases.",
       "- L'arène dans le monde Arene Semaine et son zip ne sont pas encore mis à jour.",
       ]
(RACINE / "arene_glace.md").write_text("\n".join(md) + "\n", encoding="utf-8")

from PIL import Image, ImageDraw
COUL = {"snow_block": (240, 248, 252), "snow": (245, 250, 253), "packed_ice": (150, 190, 240), "blue_ice": (90, 150, 240), "calcite": (222, 222, 216), "stone_bricks": (125, 125, 125),
        "deepslate_bricks": (72, 72, 76), "deepslate_tiles": (58, 58, 62), "smooth_quartz": (238, 234, 228), "powder_snow": (255, 180, 120)}
CACHE = {"air", "light", "soul_lantern", "chain", "snow", "barrier"}
s_ = 5
Wd, Hh = (SX + SZ) * s_ + 40, (SX + SZ) * s_ // 2 + SY * s_ + 60
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
img.save(RACINE / "exports" / f"{NOM}_vue.png")
img = Image.new("RGB", (SX * 8 + 20, SZ * 8 + 44), (24, 30, 44))
dr = ImageDraw.Draw(img)
hauts = {}
for (x, y, z), s in blocs.items():
    n = s[0].split(":")[1]
    if n not in CACHE and y <= SOL + 6 and y >= hauts.get((x, z), (-9, 0))[0]:
        hauts[(x, z)] = (y, n)
for (x, z), (y, n) in hauts.items():
    dr.rectangle([10 + x * 8, 34 + z * 8, 17 + x * 8, 41 + z * 8], fill=COUL.get(n, (170, 170, 170)))
for x, z in chemin:
    dr.rectangle([12 + x * 8, 36 + z * 8, 15 + x * 8, 39 + z * 8], fill=(80, 220, 110))
dr.text((10, 10), "Croissant vu du dessus : vert = chemin entree-sortie, orange = pieges", fill=(230, 240, 255))
img.save(RACINE / "exports" / f"{NOM}_plan.png")
print(f"Ecrit : {len(liste)} blocs, palette {len(palette)}, taille {SX}x{SY}x{SZ}")
print(f"POSE : /tp @s 0 140 0   puis   /place template atelier:{NOM} {ORIGINE[0]} {ORIGINE[1]} {ORIGINE[2]}")
