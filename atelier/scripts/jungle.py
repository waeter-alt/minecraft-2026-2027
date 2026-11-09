# [claude-build]
"""
jungle.py - Arene jungle suspendue, 3e de la serie des 7, Ø104. Refaite le 2026-11-07 pour casser la symetrie :
9 arbres de tailles, de troncs et de places differentes, canopees en amas, plateformes et ponts irreguliers,
7 arbres dont on atteint le sommet (colimacon, lianes, branches), liaisons entre sommets. 4 plateformes d'arrivee inchangees.
Point le plus bas Y226 (140 blocs au-dessus du sol Y86), rien au-dessus de Y315.
  .venv\\Scripts\\python.exe sources\\jungle.py
Controle : sources\\verif_acces.py exports\\jungle.nbt exports\\jungle_cibles.json
"""
import json
import math
import random
from pathlib import Path

import nbtlib
import numpy as np
from nbtlib import Compound, Int, List, String

RACINE = Path(__file__).resolve().parent.parent
SAVE = RACINE / "launcher/instances/Build-1.21.1/minecraft/saves/Arene Jungle"
NOM = "jungle"
SX = SZ = 104
SY = 90
Y_SOL, Y_BAS = 86, 226
ORIGINE = (-52, Y_BAS, -52)
CEN = 52.0
C = 52
ETAGES = [10, 30, 50, 70]                              # Y236, Y256, Y276, Y296
rng = random.Random(3042)


def S(n, **p):
    return ("minecraft:" + n, tuple(sorted((k, str(v).lower() if isinstance(v, bool) else str(v)) for k, v in p.items())))


AIR = S("air")
LOG = S("jungle_log", axis="y")
WOOD = S("jungle_wood", axis="y")
PL = S("jungle_planks")
DALLE = S("jungle_slab", type="bottom", waterlogged=False)
FENCE = S("jungle_fence", waterlogged=False)
MOSS, MCB, MSB, CHB = S("moss_block"), S("mossy_cobblestone"), S("mossy_stone_bricks"), S("chiseled_stone_bricks")
TAPIS = S("moss_carpet")
ROOT = S("mangrove_roots", waterlogged=False)
MLOG = S("mangrove_log", axis="y")
SHROOM = S("shroomlight")
LANT = S("lantern", hanging=False, waterlogged=False)
LANT_P = S("lantern", hanging=True, waterlogged=False)
CHAINE = S("chain", axis="y", waterlogged=False)
LUMIERE = S("light", level=15, waterlogged=False)


def F(n):
    return S(f"{n}_leaves", distance=1, persistent=True, waterlogged=False)


blocs = {}
ARBRES, ARRIVEES, SAUTS, CABANES = [], [], [], []
CIBLES = {}
NON_SOLIDES = {"air", "vine", "chain", "lantern", "light", "moss_carpet", "cocoa", "spore_blossom", "cave_vines", "cave_vines_plant", "big_dripleaf", "big_dripleaf_stem", "fern", "short_grass", "bamboo"}


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
    return nom(p) in (None, "air")


def solide(p):
    n = nom(p)
    return n is not None and n not in NON_SOLIDES


def dist(x, z):
    return math.hypot(x + .5 - CEN, z + .5 - CEN)


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


def disque(cx, cz, r):
    for x in range(math.floor(cx - r - 1), math.ceil(cx + r) + 1):
        for z in range(math.floor(cz - r - 1), math.ceil(cz + r) + 1):
            if math.hypot(x + .5 - cx, z + .5 - cz) <= r:
                yield x, z


def monde(x, y, z):
    return ORIGINE[0] + x, ORIGINE[1] + y, ORIGINE[2] + z


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
                if h2 % 2:
                    put(x, ybas + 1 + (h2 - 1) // 2, z, dalle)
                else:
                    put(x, ybas + h2 // 2, z, plein)


# ====================================================================== 1. arbres : chacun sa taille, son port et sa place (refonte du 2026-11-07)
# acces : "colimaçon" (escalier qui traverse le feuillage), "lianes" (lianes tordues qui montent, sans jamais pendre sous l'arene),
# "branches" (branches en escalier, sauts d'une branche a l'autre), None (pas de voie vers le sommet)
ARBRES_DEF = [
    dict(n="Le Grand Fromager", c=(55, 49), k=4, top=84, rond=True, pente=(0, 0), coude=None, acces="colimaçon",
         plat=[(12, 10, 360), (31, 9.5, 250), (47, 11, 300), (68, 8.5, 220)], avant="arbre-monde du centre : décalé, canopée en plusieurs masses, accès au sommet"),
    dict(n="Le Penché", c=(70, 28), k=2, top=80, rond=False, pente=(0.05, -0.03), coude=None, acces="lianes",
         plat=[(24, 7, 360), (52, 6.5, 220)], avant="ancien arbre à 315° : déplacé, tronc incliné"),
    dict(n="Le Bossu", c=(80, 64), k=1, top=64, rond=False, pente=(0, 0), coude=(34, (-0.08, 0.04)), acces="branches",
         plat=[(28, 6.5, 300), (45, 5.5, 230)], avant="ancien arbre à 15° : déplacé, tronc coudé, plus bas"),
    dict(n="Le Pilier", c=(62, 81), k=3, top=76, rond=True, pente=(0, 0), coude=None, acces="colimaçon",
         plat=[(15, 8, 200), (33, 9, 300), (58, 7.5, 240)], avant="ancien arbre à 75° : déplacé, tronc élargi"),
    dict(n="La Fourche", c=(32, 77), k=2, top=70, rond=False, pente=(0, 0), coude=(40, (-0.12, 0.05)), acces="lianes",
         plat=[(22, 7, 360), (40, 6.5, 360), (61, 6, 220)], avant="ancien arbre à 135° : déplacé, tronc coudé"),
    dict(n="La Vigie", c=(41, 18), k=2, top=82, rond=True, pente=(0, 0), coude=None, acces="colimaçon",
         plat=[(27, 8, 300), (50, 7, 220), (70, 6.5, 260)], avant="ancien arbre à 255° : déplacé, tronc rond, poste de guet au sommet"),
    dict(n="Le Rejet", c=(87, 40), k=1, top=56, rond=False, pente=(0.02, 0.04), coude=None, acces=None,
         plat=[(36, 5, 360)], avant="ajouté : jeune arbre"),
    dict(n="La Liane", c=(14, 63), k=1, top=68, rond=False, pente=(0.03, -0.02), coude=None, acces="branches",
         plat=[(30, 6.5, 300), (52, 5, 240)], avant="ajouté"),
    dict(n="Le Mort", c=(30, 57), k=1, top=50, rond=False, pente=(0, 0), coude=(30, (0.1, 0.06)), acces=None,
         plat=[(19, 5.5, 260), (41, 5, 220)], avant="ajouté : arbre mort, sans feuillage"),
]
SUPPRIME = "L'ancien arbre à 195° (ouest-sud-ouest) est supprimé ; les cinq autres arbres du cercle sont déplacés et redessinés."
NON_SOLIDES |= {"twisting_vines", "twisting_vines_plant"}
WALK = set()                                           # cases ou l'on marche : on degage 3 blocs au-dessus
R_ = random.Random(7151)


def axe(t, y):
    cx, cz = t["c"]
    px, pz = t["pente"]
    dx, dz = px * (y - 4), pz * (y - 4)
    if t["coude"] and y > t["coude"][0]:
        y0, (qx, qz) = t["coude"]
        dx, dz = px * (y0 - 4) + qx * (y - y0), pz * (y0 - 4) + qz * (y - y0)
    return cx + .5 + dx, cz + .5 + dz


def adiff(a, b):
    return abs((a - b + 180) % 360 - 180)


def dans(x, z):
    return 0 <= x < SX and 0 <= z < SZ and dist(x, z) < 51.5


def tronc(t):
    mort = t["n"] == "Le Mort"
    for y in range(4, t["top"] + 1):
        ax, az = axe(t, y)
        k = t["k"]
        for x in range(math.floor(ax - k - 2), math.ceil(ax + k + 2)):
            for z in range(math.floor(az - k - 2), math.ceil(az + k + 2)):
                if t["rond"]:
                    ok = math.hypot(x + .5 - ax, z + .5 - az) <= k + .6
                else:
                    ok = max(abs(x + .5 - ax), abs(z + .5 - az)) <= k + .5
                if ok:
                    put(x, y, z, S("stripped_jungle_wood", axis="y") if mort and bruit(x + y, z, 5, 3) > 0.4 else WOOD if bruit(x + y, z, 6, 1) > 0.25 or mort else MOSS)


def racines(t, g):
    r = random.Random(g)
    ax, az = axe(t, 4)
    for i in range(r.randint(4, 9)):
        a = r.uniform(0, 2 * math.pi)
        L, chute = r.uniform(3, 11), r.uniform(0.25, 0.9)
        x, z, y, s = ax + (t["k"] + .8) * math.cos(a), az + (t["k"] + .8) * math.sin(a), 6.0 - r.uniform(0, 2), 0
        while s < L and y >= 0:
            if dans(math.floor(x), math.floor(z)):
                put(math.floor(x), int(y), math.floor(z), ROOT if s > L * 0.55 else MLOG)
            x, z, y, s = x + .7 * math.cos(a), z + .7 * math.sin(a), y - .7 * chute, s + .7
            a += r.uniform(-0.25, 0.25)                 # elles ne partent pas droit


def branches(t, g):
    r = random.Random(g)
    niveaux = [p[0] for p in t["plat"]]
    for i in range(r.randint(2, 5)):
        y = r.randint(14, max(15, t["top"] - 6))
        if any(abs(y - n) < 3 for n in niveaux):
            continue
        a = r.uniform(0, 2 * math.pi)
        L = r.uniform(3, 11)
        ax, az = axe(t, y)
        x, z, yy = ax + (t["k"] + 1) * math.cos(a), az + (t["k"] + 1) * math.sin(a), float(y)
        bout = None
        for s in range(int(L)):
            cx_, cz_ = math.floor(x), math.floor(z)
            if not dans(cx_, cz_):
                break
            put(cx_, int(yy), cz_, S("jungle_log", axis="x" if abs(math.cos(a)) > abs(math.sin(a)) else "z"))
            bout = (x, yy, z)
            x, z, yy = x + math.cos(a), z + math.sin(a), yy + r.uniform(0, 0.35)
        if bout and t["n"] != "Le Mort" and r.random() < 0.6:
            feuillage(bout[0], bout[1] + 1, bout[2], r.uniform(1.8, 3), r.uniform(1.2, 2), g + i, ["jungle", "azalea"])


def feuillage(cx, cy, cz, rx, ry, g, sortes):
    for x in range(math.floor(cx - rx - 1), math.ceil(cx + rx) + 2):
        for z in range(math.floor(cz - rx - 1), math.ceil(cz + rx) + 2):
            if not dans(x, z):
                continue
            for y in range(max(0, math.floor(cy - ry - 1)), min(SY, math.ceil(cy + ry) + 1)):
                e = ((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy) / ry) ** 2 + ((z + .5 - cz) / rx) ** 2
                if e <= 0.75 + 0.5 * bruit(x + y, z - y, 4, g) and nom((x, y, z)) is None:
                    u = h01(x, y, z, g)
                    put(x, y, z, SHROOM if e < 0.5 and u < 0.015 else F(sortes[int(bruit(x, z, 7, g + 1) * 6) % len(sortes)]))


for i, d in enumerate(ARBRES_DEF):
    t = dict(d)
    t["i"] = i
    t["ys"] = t["top"] + 2                             # niveau du sommet praticable
    if t["acces"] == "colimaçon":
        t["r0"] = t["k"] + (1.4 if t["rond"] else 1.0)
        t["r1"] = t["r0"] + 2.6
        t["a0"] = R_.uniform(0, 360)
        t["ybas"] = min(p[0] for p in t["plat"])
    ARBRES.append(t)
    tronc(t)
    racines(t, 100 + i)
print("[1] arbres :", len(ARBRES), "blocs", len(blocs), flush=True)


# ====================================================================== 2. plateformes de formes et de hauteurs variees
def plateforme(t, y, rp, span, g):
    ax, az = axe(t, y)
    inner = t.get("r1", t["k"] + .9) if t["acces"] == "colimaçon" else t["k"] + .9
    if t["acces"] == "colimaçon":
        ac = (t["a0"] + 40 * (y - t["ybas"])) % 360     # l'arc touche toujours l'escalier
    else:
        ac = random.Random(g).uniform(0, 360)
    for x in range(math.floor(ax - rp - 3), math.ceil(ax + rp) + 3):
        for z in range(math.floor(az - rp - 3), math.ceil(az + rp) + 3):
            d = math.hypot(x + .5 - ax, z + .5 - az)
            a = math.degrees(math.atan2(z + .5 - az, x + .5 - ax)) % 360
            lim = rp - 1.6 + 2.6 * bruit(x, z, 4, g)
            if inner < d <= lim and dans(x, z) and (span >= 360 or adiff(a, ac) <= span / 2 + 25 * (bruit(x, z, 3, g + 5) - .5)):
                put(x, y, z, PL if bruit(x, z, 7, g + 1) < 0.62 else MOSS)
                WALK.add((x, y, z))
                if h01(x, y, z, g) < 0.25:
                    put(x, y - 1, z, S("jungle_log", axis="x"))
    return ac


for t in ARBRES:
    t["arcs"] = {}
    for j, (y, rp, span) in enumerate(t["plat"]):
        t["arcs"][y] = plateforme(t, y, rp, span, 10 + 7 * t["i"] + j)
print("[2] plateformes :", len(blocs), flush=True)


# ====================================================================== 3. ponts irreguliers : pentes, longueurs, arbres non voisins
def bord(centre, rp, vers):
    d = math.hypot(vers[0] - centre[0], vers[1] - centre[1])
    return centre[0] + (vers[0] - centre[0]) * (rp - 2.5) / d, centre[1] + (vers[1] - centre[1]) * (rp - 2.5) / d


def pont(p0, p1, y0, y1, sorte):
    (ax, az), (bx, bz) = p0, p1
    L = math.hypot(bx - ax, bz - az)
    assert abs(y1 - y0) <= 0.62 * L, ("pont trop raide", p0, p1, y0, y1)
    ux, uz = (bx - ax) / L, (bz - az) / L
    demi = 0.6 if sorte == "poutre" else 1.6
    trou = (L / 2 - 1.3, L / 2 + 1.3) if sorte == "casse" else None
    cases = {}
    for x in range(math.floor(min(ax, bx) - 4), math.ceil(max(ax, bx) + 5)):
        for z in range(math.floor(min(az, bz) - 4), math.ceil(max(az, bz) + 5)):
            px, pz = x + .5 - ax, z + .5 - az
            tt = px * ux + pz * uz
            s = abs(-px * uz + pz * ux)
            if tt < -0.5 or tt > L + 0.5 or not dans(x, z):
                continue
            if trou and trou[0] <= tt <= trou[1]:
                continue
            y = round(y0 + (y1 - y0) * min(1, max(0, tt / L)))
            if s <= demi:
                put(x, y, z, S("jungle_log", axis="x" if abs(ux) > abs(uz) else "z") if sorte == "poutre" else PL)
                WALK.add((x, y, z))
                cases[(x, z)] = (tt, y)
                for k in (1, 2, 3):
                    if not vide((x, y + k, z)):
                        put(x, y + k, z, AIR)
            elif s <= demi + 1 and sorte == "rail" and 1 < tt < L - 1 and vide((x, y, z)):
                put(x, y, z, PL)
                put(x, y + 1, z, FENCE)
                if int(tt) % 4 == 0 and y + 5 < SY:
                    for k in (2, 3, 4):
                        put(x, y + k, z, CHAINE)
                    put(x, y + 5, z, S("jungle_log", axis="x" if abs(ux) > abs(uz) else "z"))
    if trou:
        a = min((c for c, v in cases.items() if v[0] < trou[0]), key=lambda c: abs(cases[c][0] - trou[0]))
        b = min((c for c, v in cases.items() if v[0] > trou[1]), key=lambda c: abs(cases[c][0] - trou[1]))
        SAUTS.append(dict(d=monde(a[0], cases[a][1] + 1, a[1]), a=monde(b[0], cases[b][1] + 1, b[1]), e=max(abs(a[0] - b[0]), abs(a[1] - b[1])) - 1))
    return [(x, v[1], z) for (x, z), v in cases.items()]


def T(nom_):
    return next(t for t in ARBRES if t["n"] == nom_)


def ext(t, y):
    rp = next(p[1] for p in t["plat"] if p[0] == y)
    return axe(t, y), rp


LIENS = []


def relier(a, ya, b, yb, sorte):
    (ca, ra), (cb, rb) = ext(a, ya), ext(b, yb)
    pont(bord(ca, ra, cb), bord(cb, rb, ca), ya, yb, sorte)
    LIENS.append((a["n"], ya, b["n"], yb, sorte))


Y_ARR = 30
for nomc, a, cible, yc in (("Est", 0, "Le Bossu", 28), ("Sud", 90, "Le Pilier", 33), ("Ouest", 180, "La Liane", 30), ("Nord", 270, "La Vigie", 27)):
    px, pz = point(45.5, a)
    for x, z in disque(px, pz, 6):
        put(x, Y_ARR, z, PL if bruit(x, z, 6, 30) < 0.6 else MOSS)
        put(x, Y_ARR - 1, z, S("jungle_log", axis="x"))
        WALK.add((x, Y_ARR, z))
    for x, z in disque(px, pz, 7):
        if math.hypot(x + .5 - px, z + .5 - pz) > 6 and dans(x, z):
            put(x, Y_ARR, z, PL)
            put(x, Y_ARR + 1, z, FENCE)
    t = T(cible)
    c_, r_ = ext(t, yc)
    pont((px, pz), bord(c_, r_, (px, pz)), Y_ARR, yc, "rail")
    LIENS.append((f"arrivée {nomc}", Y_ARR, cible, yc, "rail"))
    cx, cz = math.floor(px), math.floor(pz)
    put(cx - 3, Y_ARR + 1, cz, LANT) if nom((cx - 3, Y_ARR, cz)) else None
    ARRIVEES.append((nomc, (cx, Y_ARR + 1, cz)))
    CIBLES[f"arrivée {nomc}"] = [cx, Y_ARR + 1, cz]
for a, ya, b, yb, sorte in (("Le Bossu", 28, "Le Grand Fromager", 31, "rail"), ("Le Bossu", 45, "Le Rejet", 36, "nu"), ("Le Rejet", 36, "Le Grand Fromager", 31, "poutre"),
                            ("Le Penché", 52, "Le Grand Fromager", 47, "casse"), ("Le Penché", 24, "La Vigie", 27, "nu"), ("La Vigie", 50, "Le Grand Fromager", 47, "rail"),
                            ("La Vigie", 27, "Le Mort", 19, "poutre"), ("Le Mort", 41, "Le Grand Fromager", 47, "nu"), ("La Liane", 52, "La Fourche", 61, "rail"),
                            ("La Liane", 30, "La Fourche", 22, "nu"), ("La Fourche", 40, "Le Pilier", 33, "casse"), ("Le Pilier", 58, "Le Grand Fromager", 47, "nu"),
                            ("Le Pilier", 15, "Le Grand Fromager", 12, "poutre"), ("Le Mort", 19, "Le Grand Fromager", 12, "nu"), ("La Fourche", 61, "Le Grand Fromager", 68, "rail")):
    relier(T(a), ya, T(b), yb, sorte)
print("[3] ponts :", len(LIENS), "| sauts", len(SAUTS), flush=True)


# ====================================================================== 4. canopees en amas irreguliers, retombees, trous
def canopee(t, g):
    r = random.Random(g)
    ax, az = axe(t, t["top"])
    yc = t["top"] + 1
    R = {4: 12, 3: 9.5, 2: 8, 1: 6.5}[t["k"]]
    sortes = ["jungle", "jungle", "azalea", "flowering_azalea"]
    feuillage(ax, yc, az, R, 3.4, g, sortes)
    for i in range(r.randint(3, 5)):                   # masses decalees : jamais une sphere ni un disque
        a, dd = r.uniform(0, 2 * math.pi), r.uniform(.45, .95) * R
        feuillage(ax + dd * math.cos(a), yc + r.uniform(-3.5, 1.5), az + dd * math.sin(a), r.uniform(.35, .6) * R, r.uniform(1.6, 3), g + 10 + i, sortes)
    feuilles = [(x, y, z) for (x, y, z), s in list(blocs.items()) if s[0].endswith("_leaves") and math.hypot(x + .5 - ax, z + .5 - az) < R * 1.4 and y > yc - 8]
    bas = {}
    for x, y, z in feuilles:
        if (x, z) not in bas or y < bas[(x, z)]:
            bas[(x, z)] = y
    for (x, z), y in bas.items():                      # feuillage qui retombe
        if h01(x, 7, z, g) < 0.16:
            for k in range(1, 2 + int(4 * h01(x, 8, z, g))):
                if y - k >= 0 and vide((x, y - k, z)):
                    put(x, y - k, z, F("jungle"))
    for x, y, z in feuilles:                           # trous dans le feuillage
        if h01(x, y, z, g + 3) < 0.07:
            put(x, y, z, AIR)


for t in ARBRES:
    if t["n"] != "Le Mort":
        canopee(t, 200 + t["i"])
for t in ARBRES:
    branches(t, 300 + t["i"])
for t in ARBRES:                                       # quelques amas sous les plateformes hautes, jamais au meme endroit d'un arbre a l'autre
    if t["n"] == "Le Mort":
        continue
    r = random.Random(400 + t["i"])
    for y, rp, span in t["plat"][1:]:
        if r.random() < 0.7:
            ax, az = axe(t, y - 5)
            a = r.uniform(0, 2 * math.pi)
            feuillage(ax + (rp - 1) * math.cos(a), y - r.uniform(4, 7), az + (rp - 1) * math.sin(a), r.uniform(3, 6), r.uniform(1.8, 3), 420 + t["i"], ["jungle"])
print("[4] canopees :", len(blocs), flush=True)


# ====================================================================== 5. voies vers les sommets, sommets praticables
SOMMETS = {}


def sommet(t, garde):
    ax, az = axe(t, t["top"])
    ys = t["ys"]
    R = 6 if t["k"] >= 3 else 4.5
    for x, z in disque(ax, az, R + 1.5):
        if not dans(x, z):
            continue
        for y in range(ys + 1, SY):
            if str(nom((x, y, z))).endswith("_leaves"):
                put(x, y, z, AIR)
        if (x, z) in garde or nom((x, ys, z)) in ("jungle_planks", "jungle_log", "jungle_slab"):
            continue
        d = math.hypot(x + .5 - ax, z + .5 - az)
        if d <= R - 1.5:
            put(x, ys, z, PL)                           # plancher cache dans les feuilles
            WALK.add((x, ys, z))
        elif d <= R + .5:
            if h01(x, ys, z, 500 + t["i"]) < 0.16:
                put(x, ys, z, AIR)                      # trou : on peut passer a travers la canopee
            else:
                put(x, ys, z, F("jungle"))
                WALK.add((x, ys, z))
    SOMMETS[t["n"]] = (math.floor(ax), ys, math.floor(az), R)


VOIES = {}
for t in ARBRES:
    if not t["acces"]:
        continue
    ax, az = axe(t, t["top"])
    garde = set()
    if t["acces"] == "colimaçon":
        cx_, cz_ = t["c"]
        spirale(cx_, cz_, t["r0"], t["r1"], t["ybas"], t["ys"] - t["ybas"], 9, t["a0"], PL, DALLE)
        for x, z in disque(cx_ + .5, cz_ + .5, t["r1"] + .6):
            garde.add((x, z)) if math.hypot(x + .5 - (cx_ + .5), z + .5 - (cz_ + .5)) >= t["r0"] - .5 else None
            for y in range(t["ybas"], t["ys"] + 3):
                if str(nom((x, y, z))).endswith("_leaves"):
                    put(x, y, z, AIR)
        for (x, y, z), s in list(blocs.items()):
            if t["ybas"] <= y <= t["ys"] and (x, z) in garde and s[0].endswith(("_planks", "_slab")):
                WALK.add((x, y, z))
        VOIES[t["n"]] = f"escalier en colimaçon de Y{Y_BAS + t['ybas']} à Y{Y_BAS + t['ys']}, à travers le feuillage"
    elif t["acces"] == "lianes":
        etapes = [p[0] for p in t["plat"]] + [t["ys"]]
        for j in range(len(etapes) - 1):
            y0, y1 = etapes[j], etapes[j + 1]
            if j + 1 < len(t["plat"]):
                c1, r1_ = ext(t, y1)
                a = math.radians(t["arcs"][y1])
                dd = r1_ + .9
            else:
                c1 = (ax, az)
                a = math.radians(t["arcs"][y0] + 30)
                dd = (6 if t["k"] >= 3 else 4.5) + .9
            x, z = math.floor(c1[0] + dd * math.cos(a)), math.floor(c1[1] + dd * math.sin(a))
            put(x, y0, z, PL)                           # pied de la liane, relie a la plateforme du dessous
            WALK.add((x, y0, z))
            c0 = axe(t, y0)
            px, pz = x + .5, z + .5
            for s in range(6):
                px, pz = px + (c0[0] - px) * .3, pz + (c0[1] - pz) * .3
                if vide((math.floor(px), y0, math.floor(pz))):
                    put(math.floor(px), y0, math.floor(pz), PL)
                    WALK.add((math.floor(px), y0, math.floor(pz)))
            for y in range(y0 + 1, y1 + 1):
                put(x, y, z, S("twisting_vines", age=25) if y == y1 else S("twisting_vines_plant"))
            garde.add((x, z))
            ix, iz = math.floor(x + .5 + (c1[0] - x - .5) / dd * 1.2), math.floor(z + .5 + (c1[1] - z - .5) / dd * 1.2)
            put(ix, y1, iz, PL)                         # palier a l'arrivee de la liane
            WALK.add((ix, y1, iz))
        VOIES[t["n"]] = "lianes tordues le long du tronc, d'une plateforme à l'autre jusqu'au sommet"
    else:
        y0 = min(p[0] for p in t["plat"])
        a0 = random.Random(600 + t["i"]).uniform(0, 360)
        marches = []
        for i_, y in enumerate(range(y0 + 1, t["ys"])):
            cx_, cz_ = axe(t, y)
            a = math.radians(a0 + 34 * i_)
            rr = t["k"] + 2.2
            x, z = math.floor(cx_ + rr * math.cos(a)), math.floor(cz_ + rr * math.sin(a))
            if not dans(x, z) or not vide((x, y, z)) and not str(nom((x, y, z))).endswith("_leaves"):
                continue
            put(x, y, z, S("jungle_log", axis="x" if abs(math.cos(a)) > abs(math.sin(a)) else "z"))
            WALK.add((x, y, z))
            marches.append((x, y, z))
            if y >= t["ys"] - 4:                       # le plancher du sommet s'ouvre au-dessus des dernieres marches
                garde.update((x + u, z + v) for u in (-1, 0, 1) for v in (-1, 0, 1))
        for (x1, y1, z1), (x2, y2, z2) in list(zip(marches, marches[1:])):   # deux marches en diagonale : un bout de branche entre les deux
            if abs(x2 - x1) == 1 and abs(z2 - z1) == 1 and (vide((x2, y1, z1)) or str(nom((x2, y1, z1))).endswith("_leaves")):
                put(x2, y1, z1, S("jungle_log", axis="x"))
                WALK.add((x2, y1, z1))
                marches.append((x2, y1, z1))
        for x, y, z in marches:                        # place pour la tete et pour sauter : on perce plateformes, branches et feuilles autour des marches
            for u in (-1, 0, 1):
                for v in (-1, 0, 1):
                    for k in (1, 2, 3):
                        q = (x + u, y + k, z + v)
                        if q not in marches and not vide(q) and nom(q) not in ("jungle_wood", "moss_block", "stripped_jungle_wood") or str(nom(q)).endswith("_leaves"):
                            put(*q, AIR)
        VOIES[t["n"]] = "branches en escalier autour du tronc, on saute de l'une à l'autre"
    sommet(t, garde)
    sx_, sy_, sz_, R = SOMMETS[t["n"]]
    put(sx_, sy_ + 1, sz_ + 1, LANT) if solide((sx_, sy_, sz_ + 1)) else None
    if t["n"] == "La Vigie":                           # poste de guet
        for u, v in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
            if solide((sx_ + u, sy_, sz_ + v)):
                for y in range(sy_ + 1, min(SY - 1, sy_ + 4)):
                    put(sx_ + u, y, sz_ + v, FENCE)
        for x in range(sx_ - 2, sx_ + 3):
            for z in range(sz_ - 2, sz_ + 3):
                if sy_ + 4 < SY:
                    put(x, sy_ + 4, z, S("jungle_slab", type="bottom", waterlogged=False))
    if t["n"] == "Le Pilier":                          # ruine perchee
        for u, v, h in ((-3, 1, 3), (3, -2, 2), (1, 3, 1)):
            if solide((sx_ + u, sy_, sz_ + v)):
                for y in range(sy_ + 1, min(SY, sy_ + 1 + h)):
                    put(sx_ + u, y, sz_ + v, MSB if y % 2 else MCB)
    CIBLES[f"sommet : {t['n']}"] = [sx_ + 1, sy_ + 1, sz_]
LIENS_HAUT = []
PONTS_HAUT = []
for a, b, sorte in (("Le Grand Fromager", "La Vigie", "rail"), ("Le Penché", "La Vigie", "nu"), ("Le Pilier", "Le Grand Fromager", "rail"),
                    ("La Fourche", "La Liane", "casse"), ("La Fourche", "Le Pilier", "nu")):
    sa, sb = SOMMETS[a], SOMMETS[b]
    ca, cb = (sa[0] + .5, sa[2] + .5), (sb[0] + .5, sb[2] + .5)
    PONTS_HAUT.extend(pont(bord(ca, sa[3] + 1.5, cb), bord(cb, sb[3] + 1.5, ca), sa[1], sb[1], sorte))
    LIENS_HAUT.append((a, b, sorte))
print("[5] sommets :", len(SOMMETS), "| liaisons hautes", len(LIENS_HAUT), flush=True)

# ====================================================================== 6. decor : cabanes, sauts sur feuilles, grandes feuilles, cacao, baies
for t, y, da in ((T("Le Grand Fromager"), 47, 150), (T("La Fourche"), 40, 200)):
    ax, az = axe(t, y)
    rp = next(p[1] for p in t["plat"] if p[0] == y)
    a = math.radians(t["arcs"][y] + da)
    x, z = math.floor(ax + (rp + 2) * math.cos(a)), math.floor(az + (rp + 2) * math.sin(a))
    if dans(x - 3, z - 3) and dans(x + 3, z + 3):
        for u in range(x - 3, x + 4):
            for v in range(z - 3, z + 4):
                put(u, y, v, PL)
                WALK.add((u, y, v))
                if max(abs(u - x), abs(v - z)) == 3:
                    for k in (1, 2, 3):
                        put(u, y + k, v, S("jungle_log", axis="y") if abs(u - x) == 3 and abs(v - z) == 3 else PL)
                put(u, y + 4, v, MOSS)
        for k in (1, 2):
            for u, v in ((x - 3, z), (x + 3, z), (x, z - 3), (x, z + 3)):
                put(u, y + k, v, AIR)
        put(x, y + 3, z, CHAINE)
        put(x, y + 2, z, LANT_P)
        CABANES.append((x, y + 1, z))
        CIBLES[f"cabane ({t['n']})"] = [x + 1, y + 1, z + 1]


def plots(p0, p1, y, pas, rad, table):
    L = math.hypot(p1[0] - p0[0], p1[1] - p0[1])
    n = max(1, round(L / pas))
    prev = None
    for i in range(1, n + 1):
        tt = i / n
        px, pz = p0[0] + (p1[0] - p0[0]) * tt, p0[1] + (p1[1] - p0[1]) * tt
        cur = list(disque(px, pz, rad))
        for x, z in cur:
            if dans(x, z) and vide((x, y, z)):
                put(x, y, z, table)
                WALK.add((x, y, z))
        if prev:
            a, b = min(((u, v) for u in prev for v in cur), key=lambda c: math.hypot(c[0][0] - c[1][0], c[0][1] - c[1][1]))
            SAUTS.append(dict(d=monde(a[0], y + 1, a[1]), a=monde(b[0], y + 1, b[1]), e=max(abs(a[0] - b[0]), abs(a[1] - b[1])) - 1))
        prev = cur
    return cur


for t, y, cible in ((T("Le Rejet"), 36, (95, 30)), (T("Le Mort"), 41, (24, 30))):
    c_, rp = ext(t, y)
    plots(bord(c_, rp, cible), cible, y, 4.3, 1.6, F("jungle"))
    CIBLES[f"plateforme de feuilles ({t['n']})"] = [math.floor(cible[0]), y + 1, math.floor(cible[1])]
for t, y in ((T("Le Pilier"), 33), (T("La Vigie"), 50)):
    c_, rp = ext(t, y)
    a = math.radians(t["arcs"][y] + 70)
    for k in (2, 5):
        x, z = math.floor(c_[0] + (rp + k) * math.cos(a)), math.floor(c_[1] + (rp + k) * math.sin(a))
        if dans(x, z) and vide((x, y, z)):
            put(x, y - 1, z, MOSS)
            put(x, y, z, S("big_dripleaf_stem", facing="north", waterlogged=False))
            put(x, y + 1, z, S("big_dripleaf", facing="north", tilt="none", waterlogged=False))
SAUTS = [s for s in SAUTS if s["e"] >= 1]
for x, y, z in WALK:                                   # rien ne bouche les passages
    for k in (1, 2, 3):
        if y + k < SY and str(nom((x, y + k, z))).endswith("_leaves"):
            put(x, y + k, z, AIR)
FACE = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}
for t in ARBRES:
    k = t["k"]
    for y in range(12, t["top"] - 4, 7):
        ax, az = axe(t, y)
        for (dx, dz), s in FACE.items():
            x, z = math.floor(ax + dx * (k + 1.2)), math.floor(az + dz * (k + 1.2))
            if vide((x, y, z)) and nom((x - dx, y, z - dz)) == "jungle_wood" and h01(x, y, z, 91) < 0.3:
                put(x, y, z, S("cocoa", age=2, facing={"east": "west", "west": "east", "south": "north", "north": "south"}[s]))
for (x, y, z), s in list(blocs.items()):
    n = s[0].split(":")[1]
    if n == "moss_block" and (x, y, z) in WALK and vide((x, y + 1, z)) and h01(x, y, z, 92) < 0.35:
        put(x, y + 1, z, TAPIS)
    if n in ("jungle_planks", "moss_block") and (x, y, z) in WALK and vide((x, y - 1, z)):
        u = h01(x, y, z, 93)
        if u < 0.04:
            put(x, y - 1, z, S("spore_blossom"))
        elif u < 0.12:
            n_ = 1 + int(3 * h01(x, 1, z, 94))
            for k in range(n_):
                if not vide((x, y - 1 - k, z)):
                    break
                if vide((x, y - 2 - k, z)) or k == n_ - 1:
                    put(x, y - 1 - k, z, S("cave_vines", age=25, berries=True) if k == n_ - 1 else S("cave_vines_plant", berries=True))
print("[6] decor :", len(blocs), flush=True)

# ====================================================================== 6. eclairage
SOURCES = {"lantern": 15, "shroomlight": 15, "light": 15, "cave_vines": 14, "cave_vines_plant": 14}
TRANSP = NON_SOLIDES | {"jungle_fence", "jungle_slab", "mangrove_roots", "shroomlight"}
SANS_SPAWN = TRANSP


def calcul():
    op = np.zeros((SX, SY, SZ), dtype=bool)
    L = np.zeros((SX, SY, SZ), dtype=np.int8)
    for (x, y, z), s in blocs.items():
        n = s[0].split(":")[1]
        op[x, y, z] = n not in TRANSP and not n.endswith("_leaves")
        if n in SOURCES:
            L[x, y, z] = SOURCES[n]
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
    return n in (None, "air", "moss_carpet", "light")


total = 0
for passe in range(60):
    L = calcul()
    noires = sorted((x, y, z) for (x, y, z), s in blocs.items() if s[0].split(":")[1] not in SANS_SPAWN and not s[0].endswith("_leaves") and y + 2 < SY and trav((x, y + 1, z)) and trav((x, y + 2, z)) and L[x, y + 1, z] == 0)
    if not noires:
        break
    pris = []
    for x, y, z in noires:
        if any(abs(x - a) < 10 and abs(z - b) < 10 and abs(y - c) < 5 for a, c, b in pris):
            continue
        pris.append((x, y, z))
        put(x, y + 1, z, LUMIERE)
        total += 1
if noires:
    raise SystemExit(f"surfaces noires {noires[:5]}")
print("[6] lumieres", total, flush=True)

err = []
ymin, ymax = 999, -1
for p, s in blocs.items():
    x, y, z = p
    n = s[0].split(":")[1]
    st = dict(s[1])
    if n != "air":
        ymin, ymax = min(ymin, y), max(ymax, y)
    if n != "air" and dist(x, z) > 52:
        err.append(f"hors du cercle {n} {p}")
    if n in ("water", "slime_block", "hay_block", "cobweb", "powder_snow", "scaffolding", "chest", "barrel", "hopper", "spawner", "vine", "weeping_vines", "weeping_vines_plant"):
        err.append(f"interdit {n} {p}")
    if n.endswith("_leaves") and st.get("persistent") != "true":
        err.append(f"feuille {p}")
    if n == "big_dripleaf" and nom((x, y - 1, z)) != "big_dripleaf_stem":
        err.append(f"grande feuille {p}")
    if n == "big_dripleaf_stem" and nom((x, y - 1, z)) != "moss_block":
        err.append(f"tige {p}")
    if n == "cocoa":
        d = {"east": (1, 0), "west": (-1, 0), "south": (0, 1), "north": (0, -1)}[st["facing"]]
        if nom((x + d[0], y, z + d[1])) != "jungle_wood":
            err.append(f"cacao sans tronc {p}")
    if n == "moss_carpet" and vide((x, y - 1, z)):
        err.append(f"tapis en l'air {p}")
    if n in ("cave_vines", "cave_vines_plant") and nom((x, y + 1, z)) != "cave_vines_plant" and not solide((x, y + 1, z)):
        err.append(f"baies sans accroche {p}")
    if n == "twisting_vines_plant" and nom((x, y + 1, z)) not in ("twisting_vines", "twisting_vines_plant"):
        err.append(f"liane coupee {p}")
    if n in ("twisting_vines", "twisting_vines_plant") and not (solide((x, y - 1, z)) or nom((x, y - 1, z)) == "twisting_vines_plant"):
        err.append(f"liane sans pied {p}")
    if n == "spore_blossom" and not solide((x, y + 1, z)):
        err.append(f"spore sans plafond {p}")
    if n == "lantern":
        q = (x, y + 1, z) if st["hanging"] == "true" else (x, y - 1, z)
        if not solide(q) and nom(q) != "chain":
            err.append(f"lanterne {p}")
print(f"[controle] erreurs {len(err)} {err[:8]} | Y bas {Y_BAS + ymin}, Y haut {Y_BAS + ymax}, vide sous l'arene {Y_BAS + ymin - Y_SOL}", flush=True)
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
    racine.save(D / "data/atelier/structure" / f"{NOM}.nbt")
    if not (D / "pack.mcmeta").exists():
        (D / "pack.mcmeta").write_text('{"pack": {"pack_format": 48, "description": "Atelier [claude-build]"}}\n', encoding="utf-8")
else:
    print("ATTENTION : monde Arene Jungle introuvable, structure ecrite seulement dans exports/")
ci = dict(CIBLES)
for t in ARBRES:                                       # une cible par plateforme
    for j, (y, rp, span) in enumerate(t["plat"]):
        ax, az = axe(t, y)
        cand = sorted((p for p in WALK if p[1] == y and math.hypot(p[0] + .5 - ax, p[2] + .5 - az) <= rp + 1 and trav((p[0], y + 1, p[2])) and trav((p[0], y + 2, p[2]))),
                      key=lambda p: (abs(math.hypot(p[0] + .5 - ax, p[2] + .5 - az) - rp + 2), p))
        if cand:
            ci[f"{t['n']}, plateforme Y{Y_BAS + y}"] = [cand[0][0], y + 1, cand[0][2]]
d0 = ARRIVEES[0][1]
(RACINE / "exports" / f"{NOM}_cibles.json").write_text(json.dumps({"_marque": "[claude-build]", "sol": -1, "sauts_longs": True, "depart": list(d0), "cibles": ci}, ensure_ascii=False, indent=1), encoding="utf-8")
# chaque sommet par sa propre voie : les liaisons entre sommets sont bouchees (2 blocs au-dessus du tablier)
(RACINE / "exports" / f"{NOM}_sommets_cibles.json").write_text(json.dumps({"_marque": "[claude-build]", "sol": -1, "sauts_longs": True, "depart": list(d0),
    "cibles": {k: v for k, v in ci.items() if k.startswith("sommet")}, "bloquer": [[x, y + k, z] for x, y, z in PONTS_HAUT for k in (1, 2)]}, ensure_ascii=False), encoding="utf-8")


def w(p):
    return " ".join(str(v) for v in monde(*p))



# symetrie : on compare chaque quadrant a ses reflets (miroirs et rotations), sur les blocs solides
pleins = {(x, y, z) for (x, y, z), s in blocs.items() if s[0].split(":")[1] not in ("air", "light")}


def quadrant(p):
    return (p[0] >= C) * 1 + (p[2] >= C) * 2


SIM = 0
for tr in (lambda x, z: (2 * C - 1 - x, z), lambda x, z: (x, 2 * C - 1 - z), lambda x, z: (2 * C - 1 - x, 2 * C - 1 - z), lambda x, z: (z, x)):
    for q in range(4):
        A = {p for p in pleins if quadrant(p) == q}
        B = {(*tr(p[0], p[2]),) for p in pleins}
        img_ = {(tr(p[0], p[2])[0], p[1], tr(p[0], p[2])[1]) for p in A}
        if img_:
            SIM = max(SIM, len(img_ & pleins) / len(img_))
md = ["<!-- [claude-build] genere par sources/jungle.py -->", "# Arène jungle suspendue", "", "Monde `saves/Arene Jungle`, centre X0 Z0. Refaite le 2026-11-07 pour casser la symétrie et ouvrir les sommets.", "",
      f"- **Sol réel :** Y{Y_SOL}", f"- **Point le plus bas :** Y{Y_BAS + ymin} ({Y_BAS + ymin - Y_SOL} blocs de vide)", f"- **Point le plus haut :** Y{Y_BAS + ymax} (limite Y315)",
      f"- **Symétrie :** au mieux {round(100 * SIM)} % des blocs d'un quadrant se retrouvent dans un autre par miroir ou par rotation.", "",
      "## Plateformes d'arrivée (inchangées)", ""] + [f"- {n} : {w(p)}" for n, p in ARRIVEES]
md += ["", "## Arbres", "", "| Arbre | Pied (X Z) | Tronc | Sommet du tronc | Ce qui a changé | Voie vers le sommet | Sommet praticable |", "|---|---|---|---|---|---|---|"]
for t in ARBRES:
    pied = monde(t["c"][0], 0, t["c"][1])
    port = ("rond " if t["rond"] else "carré ") + f"{2 * t['k'] + 1} blocs" + (", incliné" if t["pente"] != (0, 0) else "") + (", coudé" if t["coude"] else "")
    s_ = SOMMETS.get(t["n"])
    md.append(f"| {t['n']} | {pied[0]} {pied[2]} | {port} | Y{Y_BAS + t['top']} | {t['avant']} | {VOIES.get(t['n'], 'aucune')} | {w(s_[:3]) if s_ else '—'} |")
md += ["", f"- {SUPPRIME}", f"- Hauteurs de tronc : de Y{Y_BAS + min(t['top'] for t in ARBRES)} à Y{Y_BAS + max(t['top'] for t in ARBRES)}.",
       f"- {len(SOMMETS)} arbres sur {len(ARBRES)} ont une voie jusqu'au-dessus de leur canopée.", "",
       "## Liaisons entre sommets", ""] + [f"- {a} ↔ {b} : {'pont à garde-corps' if s == 'rail' else 'pont effondré, un saut au milieu' if s == 'casse' else 'pont sans garde-corps'}" for a, b, s in LIENS_HAUT]
md += ["", "## Ponts", "", "| De | À | Type |", "|---|---|---|"] + [f"| {a} (Y{Y_BAS + ya}) | {b} (Y{Y_BAS + yb}) | {s} |" for a, ya, b, yb, s in LIENS]
md += ["", "## Sauts", "", "| Départ | Arrivée | Écart |", "|---|---|---|"] + [f"| {' '.join(map(str, s['d']))} | {' '.join(map(str, s['a']))} | {s['e']} |" for s in SAUTS]
md += ["", "## Résumé", "",
       "- Arbres déplacés hors du cercle, tailles et troncs variés (ronds, carrés, inclinés, coudés), racines et branches de longueurs et de directions différentes.",
       "- Canopées en plusieurs masses, avec débords, retombées de feuillage et trous ; jamais une sphère ni un disque.",
       "- Plateformes en arcs incomplets, de rayons et de hauteurs différents ; ponts en pente, de longueurs variées, dont plusieurs entre arbres non voisins.",
       "- Sommets aplanis : plancher caché dans les feuilles, bords en feuilles avec des trous ; poste de guet sur la Vigie, ruine perchée sur le Pilier.",
       "- Toutes les feuilles sont persistantes. Rien n'amortit la chute sous l'arène : pas d'eau, de slime, de foin, de toile, de poudreuse, d'échafaudage, ni de liane qui pend.", "",
       "## Écarts par rapport au prompt", "",
       "- Les valeurs à compléter ont été prises dans le projet : Minecraft 1.21.1, centre X0 Z0.",
       "- Lianes de montée : lianes tordues (twisting vines), qui ne poussent que vers le haut, pour qu'aucune ne descende jamais vers le sol. Les vignes ordinaires poussent vers le bas avec le temps.",
       "- Travaux faits par le générateur et un correctif à poser, pas par /fill ni /forceload. L'arène ne contient aucun feu, `doFireTick` n'a donc pas d'effet ici.",
       "- Les anciennes ruines flottantes sont retirées ; une ruine est perchée au sommet du Pilier.",
       f"- Éclairage : lanternes, shroomlight, baies et {total} blocs de lumière invisibles.",
       "- L'arène dans le monde Arene Semaine et son zip ne sont pas encore mis à jour."]
(RACINE / "arene_jungle.md").write_text("\n".join(md) + "\n", encoding="utf-8")

from PIL import Image, ImageDraw
COUL = {"jungle_wood": (90, 70, 35), "jungle_planks": (160, 115, 80), "jungle_log": (90, 70, 35), "moss_block": (88, 125, 45), "jungle_leaves": (55, 150, 40), "azalea_leaves": (95, 140, 50),
        "flowering_azalea_leaves": (150, 130, 130), "mossy_stone_bricks": (108, 118, 92), "mossy_cobblestone": (100, 115, 85), "chiseled_stone_bricks": (130, 130, 130), "mangrove_roots": (80, 60, 40),
        "mangrove_log": (90, 60, 40), "shroomlight": (245, 170, 80), "jungle_slab": (160, 115, 80), "jungle_fence": (140, 100, 70)}
CACHE = {"air", "light", "lantern", "chain", "moss_carpet", "cocoa", "spore_blossom", "cave_vines", "cave_vines_plant", "big_dripleaf", "big_dripleaf_stem"}
s_ = 4
Wd, Hh = (SX + SZ) * s_ + 40, (SX + SZ) * s_ // 2 + SY * s_ + 60
img = Image.new("RGB", (Wd, Hh), (150, 200, 235))
dr = ImageDraw.Draw(img)


def ouvert(p):
    n = nom(p)
    return n is None or n in CACHE


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
img.save(RACINE / "exports" / f"{NOM}_vue.png")
print(f"Ecrit : {len(liste)} blocs, palette {len(palette)}, taille {SX}x{SY}x{SZ}")
print(f"POSE : /tp @s 0 316 0   puis   /place template atelier:{NOM} {ORIGINE[0]} {ORIGINE[1]} {ORIGINE[2]}")
