# [claude-build]
"""
portes.py - Arene Les Portes, 7e et derniere de la serie, O104 : 8 salons a themes et un carrefour, relies a pied et par 8 paires de portails fixes.
Une couleur = une paire, aller-retour, toujours la meme destination, a retenir de tete : aucun panneau (choix de l'utilisateur). Le danger : un ennemi peut surgir derriere soi.
Sol Y86, centre X0 Z0, porte au nord.
  .venv\\Scripts\\python.exe sources\\portes.py
Controles : sources\\verif_acces.py exports\\portes.nbt exports\\portes_cibles.json  puis  sources\\verif_portes.py exports\\portes.nbt
"""
import json
import math
import random
from pathlib import Path

import nbtlib
from nbtlib import Byte, Compound, Int, List, String

RACINE = Path(__file__).resolve().parent.parent
SAVE = RACINE / "launcher/instances/Build-1.21.1/minecraft/saves/Arene Porte"
NOM = "portes"
SX = SZ = 104
SOL = 18
SY = SOL + 22
C = 52
CEN = 52.0
R = 52.0
ORIGINE = (-52, 86 - SOL, -52)
PORTE = 270
rng = random.Random(7170)


def S(n, **p):
    n = n if ":" in n else "minecraft:" + n
    return (n, tuple(sorted((k, str(v).lower() if isinstance(v, bool) else str(v)) for k, v in p.items())))


AIR = S("air")
ESB, PUR, QZ, LANT = S("end_stone_bricks"), S("purpur_block"), S("smooth_quartz"), S("sea_lantern")
BARRIERE = S("barrier", waterlogged=False)
blocs = {}
NBT = {}
occ = {}
APPS, TOURS, PORTAILS, CENTRES = [], [], [], {}
MURS = SOL + 7


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


def cellule(r, a):
    a = math.radians(a)
    return math.floor(CEN + r * math.cos(a)), math.floor(CEN + r * math.sin(a))


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


def gl(al, sp, g):
    return {0: (C + al, C + sp), 90: (C - 1 - sp, C + al), 180: (C - 1 - al, C - 1 - sp), 270: (C + sp, C - 1 - al)}[g % 360]


def monde(x, y, z):
    return ORIGINE[0] + x, ORIGINE[1] + y, ORIGINE[2] + z


def libre(cx, cz, r):
    return not any((x, z) in occ for x in range(int(cx - r), int(cx + r) + 1) for z in range(int(cz - r), int(cz + r) + 1))


def reserve(cx, cz, r, quoi):
    for x in range(int(cx - r), int(cx + r) + 1):
        for z in range(int(cz - r), int(cz + r) + 1):
            occ[(x, z)] = quoi


def commande(x, y, z, cmd, sorte, facing):
    put(x, y, z, S(sorte, conditional=False, facing=facing))
    NBT[(x, y, z)] = Compound({"id": String("minecraft:command_block"), "Command": String(cmd), "auto": Byte(1), "TrackOutput": Byte(0),
                               "conditionMet": Byte(0), "powered": Byte(0), "UpdateLastExecution": Byte(1)})


# ====================================================================== 1. les 8 salons : chacun se reconnait a son sol
SALONS = [("Forge", "blackstone", "polished_blackstone_bricks", "cone"), ("Serre", "moss_block", "oak_leaves", "arbre"), ("Bibliothèque", "dark_oak_planks", "bookshelf", "rayon"),
          ("Cristaux", "calcite", "amethyst_block", "cone"), ("Ossuaire", "soul_soil", "bone_block", "amas"), ("Glacier", "packed_ice", "blue_ice", "cone"),
          ("Hall", "polished_diorite", "quartz_block", "amas"), ("Désert", "sandstone", "smooth_sandstone", "amas")]


def salon(x, z):
    return round(ang(x, z) / 45) % 8


for x in range(SX):
    for z in range(SZ):
        d, a = dist(x, z), ang(x, z)
        if d >= R:
            continue
        if d >= 48:
            hm = SOL + 16
            for y in range(SOL, hm + 1):
                put(x, y, z, ESB)
            put(x, hm + 1, z, PUR)
            if d >= 50.5 and int(a * 1.5) % 2 == 0:
                put(x, hm + 2, z, PUR)
            if d >= 51:
                for y in range(hm + 3, SY):
                    put(x, y, z, BARRIERE)
        elif d >= 40:
            put(x, SOL, z, ESB)
        elif d >= 18:
            put(x, SOL, z, S(SALONS[salon(x, z)][1]))
        else:
            put(x, SOL, z, PUR if 14 <= d < 15.5 else QZ)
for x in range(SX):
    for z in range(SZ):
        d, a = dist(x, z), ang(x, z)
        anneau = 38 <= d < 40 or 16 <= d < 18
        cloison = 18 <= d < 38 and any(adiff(a, 22.5 + 45 * k) < 57.3 / d for k in range(8))
        if not (anneau or cloison):
            continue
        ouvert = anneau and any(adiff(a, 45 * k) * math.pi / 180 * d < 2.2 for k in range(8))
        for y in range(SOL + 1, MURS + 1):
            if ouvert and y <= SOL + 4:
                continue
            put(x, y, z, PUR if y == MURS else ESB)
        if ouvert:
            put(x, SOL, z, PUR)
        occ[(x, z)] = "mur"
for sp in range(-9, 9):                                # porte nord
    for al in range(44, 52):
        x, z = gl(al, sp, PORTE)
        if dist(x, z) >= R:
            continue
        cote = sp < -5 or sp > 4
        for y in range(SOL + 1, SOL + 19):
            if cote or y > SOL + 10:
                put(x, y, z, LANT if y == SOL + 18 and sp in (-8, 7) else PUR if y >= SOL + 17 else ESB)
            else:
                put(x, y, z, AIR)
        if not cote:
            put(x, SOL, z, PUR if al % 2 else ESB)
        occ[(x, z)] = "porte"
print("[1] salons et murs :", len(blocs), flush=True)

# ====================================================================== 2. tours : on y monte par l'echelle, ou par un portail
for g, nomt in ((22.5, "Tour sud-est"), (112.5, "Tour sud-ouest"), (202.5, "Tour nord-ouest"), (292.5, "Tour nord-est")):
    x0, z0 = cellule(39, g)
    reserve(x0, z0, 5, "tour")
    YT = SOL + 13
    sud = z0 > C                                       # l'echelle est du cote du centre, la porte vers l'anneau
    dehors = g
    for x in range(x0 - 5, x0 + 5):
        for z in range(z0 - 5, z0 + 5):
            r_ = math.hypot(x + .5 - x0, z + .5 - z0)
            a = math.degrees(math.atan2(z + .5 - z0, x + .5 - x0)) % 360
            if r_ < 3.4:
                for y in range(SOL + 1, YT):
                    blocs.pop((x, y, z), None)
                put(x, SOL, z, QZ)
                put(x, YT, z, QZ)
            elif r_ < 4.8:
                for y in range(SOL + 1, YT + 4):
                    porte = adiff(a, dehors) < 28 and y <= SOL + 3
                    fente = y in (SOL + 7, SOL + 8, YT + 2) and any(adiff(a, b) < 8 for b in (g + 45, g + 135, g + 225, g + 315))
                    casse = y >= YT + 2 and bruit(a, 0, 45, 7) > 0.56
                    if porte or fente or casse:
                        blocs.pop((x, y, z), None)
                    else:
                        put(x, y, z, PUR if y == SOL + 1 else ESB)
                if adiff(a, dehors) < 28:
                    put(x, SOL, z, PUR)
    lz, mur, fa = (z0 - 3, z0 - 4, "south") if sud else (z0 + 2, z0 + 3, "north")
    for y in range(SOL + 1, YT + 1):
        assert nom((x0, y, mur)) is not None, ("echelle sans mur", g, y)
        put(x0, y, lz, S("ladder", facing=fa, waterlogged=False))
    TOURS.append((nomt, x0, z0, YT, lz, sud))
print("[2] tours :", len(blocs), flush=True)

# ====================================================================== 3. portails
COULEURS = {"red": (1, .1, .1), "orange": (1, .55, .05), "yellow": (1, .9, .1), "lime": (.4, 1, .1), "cyan": (.1, .9, .9), "blue": (.15, .3, 1), "purple": (.65, .15, 1), "pink": (1, .45, .75)}
YAW = {(0, 1): 0, (0, -1): 180, (1, 0): -90, (-1, 0): 90}
FACE = {(0, 1): "south", (0, -1): "north", (1, 0): "east", (-1, 0): "west"}


def portail(ox, oz, axe, front, couleur, ici, vers, yb=SOL, sols=True):
    """Ouverture de 2 cases de large et 3 de haut. On arrive 3 blocs devant, tourne vers l'exterieur."""
    bloc = S(f"{couleur}_concrete")
    u = (1, 0) if axe == "x" else (0, 1)
    cases = [(ox, oz), (ox + u[0], oz + u[1])]
    bords = [(ox - u[0], oz - u[1]), (ox + 2 * u[0], oz + 2 * u[1])]
    for x, z in cases + bords:
        put(x, yb, z, bloc)
        put(x, yb + 4, z, bloc)
    put(cases[0][0], yb + 4, cases[0][1], LANT)
    put(cases[1][0], yb + 4, cases[1][1], LANT)
    for x, z in bords:
        for y in range(yb + 1, yb + 4):
            put(x, y, z, bloc)
    for x, z in cases:
        for y in range(yb + 1, yb + 4):
            put(x, y, z, AIR)
    if sols:
        for k in (1, 2):
            for x, z in cases:
                put(x + front[0] * k, yb, z + front[1] * k, bloc)
    PORTAILS.append({"couleur": couleur, "ici": ici, "vers": vers, "o": (ox, yb + 1, oz), "axe": axe, "front": front,
                     "arrivee": ((ox + u[0] + (.5 if axe == "z" else 0) + front[0] * 3) if axe == "z" else ox + 1.0, yb + 1, (oz + .5 + front[1] * 3) if axe == "x" else oz + 1.0)})
    if axe == "z":
        PORTAILS[-1]["arrivee"] = (ox + .5 + front[0] * 3, yb + 1, oz + 1.0)


PAIRES = [("red", 0, 4), ("orange", 1, 5), ("yellow", 2, 6), ("lime", 3, 7)]
for couleur, k1, k2 in PAIRES:                         # un salon et son oppose
    for k, autre in ((k1, k2), (k2, k1)):
        a = 45 * k + 12
        x, z = cellule(29, a)
        if abs(math.cos(math.radians(a))) >= abs(math.sin(math.radians(a))):
            axe, front = "z", (-1 if x > C else 1, 0)
        else:
            axe, front = "x", (0, -1 if z > C else 1)
        portail(x, z, axe, front, couleur, SALONS[k][0], SALONS[autre][0])
        reserve(x, z, 4, "portail")
        reserve(x + front[0] * 3, z + front[1] * 3, 2, "portail")
CENTRE = [("cyan", 45, 0), ("blue", 135, 1), ("purple", 225, 2), ("pink", 315, 3)]
for couleur, a, i in CENTRE:                           # le carrefour et les sommets de tour
    nomt, x0, z0, YT, lz, sud = TOURS[i]
    x, z = cellule(10, a)
    axe, front = "x", (0, -1 if z > C else 1)
    portail(x, z, axe, front, couleur, "Carrefour", nomt)
    reserve(x, z, 3, "portail")
    if sud:                                            # echelle au nord de la tour : portail au sud, on arrive vers le nord
        portail(x0 - 1, z0 + 2, "x", (0, -1), couleur, nomt, "Carrefour", YT, sols=False)
    else:
        portail(x0 - 1, z0 - 3, "x", (0, 1), couleur, nomt, "Carrefour", YT, sols=False)
print("[3] portails :", len(PORTAILS), flush=True)


# ====================================================================== 4. decor de chaque salon, centre, apparitions
def cone(x, z, r_, ht, bloc):
    for t in range(ht):
        ra = r_ * (1 - t / ht) ** .8 + .4
        cx_, cz_ = x + 1.4 * (bruit(t, x, 6, 14) - .5), z + 1.4 * (bruit(t, z, 6, 15) - .5)
        for u in range(int(x - r_ - 2), int(x + r_ + 3)):
            for v in range(int(z - r_ - 2), int(z + r_ + 3)):
                if math.hypot(u + .5 - cx_, v + .5 - cz_) < ra and vide((u, SOL + 1 + t, v)) and (u, v) not in occ:
                    put(u, SOL + 1 + t, v, bloc)


def amas(x, z, r_, bloc):
    for u in range(int(x - r_ - 2), int(x + r_ + 3)):
        for v in range(int(z - r_ - 2), int(z + r_ + 3)):
            for y in range(SOL + 1, SOL + 6):
                e = math.hypot((u - x) / r_, (v - z) / r_, (y - SOL) / (r_ * .9))
                if e + .45 * (bruit(u + y, v, 3, 11) - .5) < 1 and vide((u, y, v)) and (u, v) not in occ:
                    put(u, y, v, bloc)


for i in range(10):
    a = 36 * i
    for da, r_ in [(u, v) for u in (0, 5, -5, 10, -10) for v in (45, 44, 46, 43)]:
        x, z = cellule(r_, a + da)
        if libre(x, z, 1) and vide((x, SOL + 1, z)) and vide((x, SOL + 2, z)):
            break
    else:
        raise SystemExit(f"apparition {i} sans place")
    put(x, SOL, z, PUR)
    reserve(x, z, 1, "apparition")
    APPS.append((x, SOL + 1, z))
for k in range(8):                                     # devant chaque porte, le passage reste libre
    for r_ in (19.5, 36.5):
        x, z = cellule(r_, 45 * k)
        reserve(x, z, 3, "passage")
for k, (nomz, sol_, deco, sorte) in enumerate(SALONS):
    b = S(deco) if deco != "oak_leaves" else S("oak_leaves", distance=1, persistent=True, waterlogged=False)
    n = 0
    for _ in range(600):
        x, z = cellule(rng.uniform(20.5, 35.5), 45 * k + rng.uniform(-18, 18))
        if n >= 8 or not libre(x, z, 1) or nom((x, SOL, z)) != sol_:
            continue
        if sorte == "cone":
            cone(x, z, rng.uniform(1.6, 2.6), rng.randint(5, 9), b)
        elif sorte == "amas":
            amas(x, z, rng.uniform(2, 3.2), b)
        elif sorte == "rayon":
            dx, dz = (1, 0) if n % 2 else (0, 1)
            for i in range(-3, 4):
                for y in range(SOL + 1, SOL + 5):
                    if vide((x + i * dx, y, z + i * dz)) and (x + i * dx, z + i * dz) not in occ:
                        put(x + i * dx, y, z + i * dz, b)
        else:
            for y in range(SOL + 1, SOL + 6):
                put(x, y, z, S("oak_log", axis="y"))
            amas_h = SOL + 6
            for u in range(x - 3, x + 4):
                for v in range(z - 3, z + 4):
                    for y in range(amas_h - 1, amas_h + 3):
                        if math.hypot(u - x, v - z, (y - amas_h) * 1.3) < 3.1 and vide((u, y, v)) and dist(u, v) < 37.5:
                            put(u, y, v, b)
        reserve(x, z, 2, "decor")
        n += 1
    print("   ", nomz, n, flush=True)
n_a = 0
for _ in range(500):                                   # rochers de l'anneau : de quoi se couvrir sans le fermer
    x, z = cellule(rng.uniform(42.5, 45.5), rng.uniform(0, 360))
    if n_a >= 14 or not libre(x, z, 3) or adiff(ang(x, z), PORTE) < 14:
        continue
    amas(x, z, rng.uniform(1.5, 2.1), S("end_stone"))
    reserve(x, z, 4, "rocher")
    n_a += 1
for a in (0, 90, 180, 270):                            # bancs de pierre dans le carrefour
    for da in (-16, 16):
        x, z = cellule(11.5, a + da)
        if libre(x, z, 1):
            amas(x, z, 1.6, PUR)
            reserve(x, z, 2, "banc")
for x in range(C - 3, C + 3):                          # monument du carrefour
    for z in range(C - 3, C + 3):
        if dist(x, z) < 3:
            put(x, SOL + 1, z, PUR)
for x, z in ((C - 1, C - 1), (C, C - 1), (C - 1, C), (C, C)):
    for y in range(SOL + 2, SOL + 9):
        put(x, y, z, S("purpur_pillar", axis="y"))
    put(x, SOL + 9, z, LANT)
print("[4] decor :", len(blocs), flush=True)

# ====================================================================== 5. chaine des portails
CY, CZ, CX0 = SOL - 4, C, C - 34
CMD = ["gamerule commandBlockOutput false", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false", "time set 6000", "difficulty normal"]


def rel(x, y, z):
    px = CX0 + len(CMD)
    return f"~{x - px} ~{y - CY} ~{z - CZ}"


for p in PORTAILS:
    ox, oy, oz = p["o"]
    dxb, dzb = (1, 0) if p["axe"] == "x" else (0, 1)
    autre = next(q for q in PORTAILS if q["couleur"] == p["couleur"] and q is not p)
    ax, ay, az = autre["arrivee"]
    yaw = YAW[autre["front"]]
    sel = f"@a[dx={dxb},dy=2,dz={dzb},gamemode=!spectator]"
    r, g, b = COULEURS[p["couleur"]]
    cx_, cz_ = (ox + 1.0, oz + .5) if p["axe"] == "x" else (ox + .5, oz + 1.0)
    px = CX0 + len(CMD)
    CMD.append(f"particle minecraft:dust{{color:[{r}f,{g}f,{b}f],scale:1.4f}} ~{cx_ - px - .5:.1f} ~{oy + 1 - CY} ~{cz_ - CZ - .5:.1f} {.5 * dxb + .05} .8 {.5 * dzb + .05} 0 3")
    d = f"~{ax - ox} ~{ay - oy} ~{az - oz}"
    CMD.append(f"execute positioned {rel(ox, oy, oz)} align xyz if entity {sel} positioned {d} run playsound minecraft:entity.enderman.teleport master @a[distance=..20] ~ ~ ~ 1 1")
    CMD.append(f"execute positioned {rel(ox, oy, oz)} align xyz as {sel} positioned {d} run tp @s ~ ~ ~ {yaw} 0")
for i, c in enumerate(CMD):
    assert (CX0 + i, CY, CZ) not in blocs
    commande(CX0 + i, CY, CZ, c, "repeating_command_block" if i == 0 else "chain_command_block", "east")
print("[5] commandes :", len(CMD), flush=True)

# ====================================================================== 6. controles
err = []
for p, s in blocs.items():
    x, y, z = p
    n = s[0].split(":")[1]
    if n not in ("air", "barrier") and dist(x, z) > R + 1e-9:
        err.append(f"depasse {n} {p}")
    if n in ("chest", "barrel", "hopper", "dropper", "dispenser", "spawner", "water", "lava"):
        err.append(f"interdit {n} {p}")
for q in PORTAILS:
    ax, ay, az = q["arrivee"]
    for x, z in {(math.floor(ax - .3), math.floor(az - .3)), (math.floor(ax + .29), math.floor(az + .29))}:
        for y in (ay, ay + 1):
            if not vide((x, y, z)):
                err.append(f"arrivee bouchee {q['couleur']} {q['ici']} {(x, y, z)} {nom((x, y, z))}")
        if vide((x, ay - 1, z)):
            err.append(f"arrivee dans le vide {q['couleur']} {q['ici']}")
print("[controle] erreurs", len(err), err[:6], flush=True)
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
    e = Compound({"pos": List[Int]([Int(x), Int(y), Int(z)]), "state": Int(index[s])})
    if (x, y, z) in NBT:
        e["nbt"] = NBT[(x, y, z)]
    liste.append(e)
racine = nbtlib.File({"DataVersion": Int(3955), "size": List[Int]([Int(SX), Int(SY), Int(SZ)]), "palette": List[Compound](palette), "blocks": List[Compound](liste), "entities": List[Compound]([])}, gzipped=True)
racine.save(RACINE / "exports" / f"{NOM}.nbt")
D = SAVE / "datapacks/atelier"
if (SAVE / "level.dat").exists():
    (D / "data/atelier/structure").mkdir(parents=True, exist_ok=True)
    racine.save(D / "data/atelier/structure" / f"{NOM}.nbt")
    if not (D / "pack.mcmeta").exists():
        (D / "pack.mcmeta").write_text('{"pack": {"pack_format": 48, "description": "Atelier [claude-build]"}}\n', encoding="utf-8")
else:
    print("ATTENTION : monde Arene Porte introuvable")

gx, gz = gl(46, 0, PORTE)
ci = {"carrefour": [C + 13, SOL + 1, C]}
for k, s_ in enumerate(SALONS):
    x, z = cellule(21, 45 * k)
    ci[f"salon {s_[0]}"] = [x, SOL + 1, z]
ci.update({f"apparition {i + 1}": list(p) for i, p in enumerate(APPS)})
for q in PORTAILS:
    ax, ay, az = q["arrivee"]
    ci[f"arrivee {q['couleur']} {q['ici']}"] = [math.floor(ax), ay, math.floor(az)]
    ci[f"entree {q['couleur']} {q['ici']}"] = list(q["o"])
(RACINE / "exports" / f"{NOM}_cibles.json").write_text(json.dumps({"_marque": "[claude-build]", "sol": SOL, "depart": [gx, SOL + 1, gz], "cibles": ci}, ensure_ascii=False, indent=1), encoding="utf-8")


def w(p):
    return " ".join(str(v) for v in monde(*p))


FR = {"red": "Rouge", "orange": "Orange", "yellow": "Jaune", "lime": "Vert", "cyan": "Cyan", "blue": "Bleu", "purple": "Violet", "pink": "Rose"}
md = ["<!-- [claude-build] genere par sources/portes.py -->", "# Arène Les Portes", "", "Monde `saves/Arene Porte`, centre X0 Z0, sol Y86, porte au nord. Tout se rejoint à pied : les portails sont des raccourcis.", "",
      "## Les 8 paires", "", "Une couleur relie toujours les deux mêmes lieux, dans les deux sens. Aucun panneau : les joueurs retiennent le réseau de tête. Ce tableau et le plan sont à garder pour les organisateurs.", "", "| Couleur | Lieu A | Lieu B |", "|---|---|---|"]
for c in COULEURS:
    a_, b_ = [q for q in PORTAILS if q["couleur"] == c]
    md.append(f"| {FR[c]} | {a_['ici']} ({w(a_['o'])}) | {b_['ici']} ({w(b_['o'])}) |")
md += ["", "Apparitions : " + " ; ".join(w(p) for p in APPS), "", "## À savoir", "", "- Les blocs de commande bloquent l'heure en plein jour, coupent les créatures et passent la difficulté en Normal.",
       "- Sur un serveur : `enable-command-block=true`.", "- Le passage par un portail se vérifie en jeu."]
(RACINE / "arene_portes.md").write_text("\n".join(md) + "\n", encoding="utf-8")

from PIL import Image, ImageDraw, ImageFont
COUL = {"end_stone_bricks": (205, 200, 150), "purpur_block": (165, 115, 170), "smooth_quartz": (235, 230, 225), "blackstone": (50, 45, 55), "moss_block": (90, 125, 50), "dark_oak_planks": (70, 45, 25), "calcite": (225, 225, 220),
        "soul_soil": (80, 62, 50), "packed_ice": (150, 190, 235), "polished_diorite": (195, 195, 195), "sandstone": (220, 205, 150), "polished_blackstone_bricks": (70, 65, 75), "oak_leaves": (60, 110, 40), "bookshelf": (135, 95, 55),
        "amethyst_block": (150, 100, 200), "bone_block": (225, 220, 195), "blue_ice": (110, 165, 240), "quartz_block": (240, 235, 230), "smooth_sandstone": (225, 210, 160), "sea_lantern": (210, 240, 230)}
for c, v in COULEURS.items():
    COUL[f"{c}_concrete"] = tuple(int(u * 255) for u in v)
s_ = 8
img = Image.new("RGB", (SX * s_, SZ * s_ + 150), (32, 28, 40))
police = ImageFont.truetype("arial.ttf", 15)
dr = ImageDraw.Draw(img)
for x in range(SX):
    for z in range(SZ):
        c = None
        for y in range(SOL + 2, SOL - 1, -1):
            n = nom((x, y, z))
            if n and n not in ("air", "barrier", "dark_oak_wall_sign", "ladder"):
                c = COUL.get(n, (130, 130, 130))
                if y > SOL:
                    c = tuple(int(v * .78) for v in c)
                break
        if c:
            dr.rectangle([x * s_, z * s_, x * s_ + s_ - 1, z * s_ + s_ - 1], fill=c)
for k, s2 in enumerate(SALONS):
    x, z = cellule(24, 45 * k)
    dr.text((x * s_, z * s_), s2[0], fill=(255, 255, 255), font=police, stroke_width=2, stroke_fill=(0, 0, 0), anchor="mm")
dr.text((12, SZ * s_ + 6), "Une couleur = une paire de portails, dans les deux sens. Nord en haut.", fill=(255, 255, 255), font=police)
for i, c in enumerate(COULEURS):
    a_, b_ = [q for q in PORTAILS if q["couleur"] == c]
    px, py = 12 + (i % 2) * 410, SZ * s_ + 32 + (i // 2) * 28
    dr.rectangle([px, py + 2, px + 16, py + 18], fill=COUL[f"{c}_concrete"])
    dr.text((px + 24, py), f"{FR[c]} : {a_['ici']} - {b_['ici']}", fill=(255, 255, 255), font=police)
img.save(RACINE / "exports" / f"{NOM}_plan.png")
print(f"Ecrit : {len(liste)} blocs, palette {len(palette)}, taille {SX}x{SY}x{SZ}, donnees {len(NBT)}")
print(f"POSE : /tp @s 0 120 0   puis   /place template atelier:{NOM} {ORIGINE[0]} {ORIGINE[1]} {ORIGINE[2]}")
