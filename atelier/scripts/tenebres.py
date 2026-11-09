# [claude-build]
"""
tenebres.py - Arene Les Tenebres, 6e de la serie des 7, O104 : cite endormie sous une voute fermee, le danger est de faire du bruit.
Capteurs de sculk : un pas, un coup ou un saut allume le joueur le plus proche, le ralentit et plonge la zone dans l'obscurite.
Accroupi, on ne declenche rien. Les tapis noirs etouffent les pas. Au centre, les hurleurs crient au bruit ; le Warden est coupe (choix de l'utilisateur).
Sol Y86, centre X0 Z0, porte au nord.
  .venv\\Scripts\\python.exe sources\\tenebres.py
Controles : sources\\verif_acces.py exports\\tenebres.nbt exports\\tenebres_cibles.json  puis  sources\\verif_bruit.py exports\\tenebres.nbt
"""
import json
import math
import random
from pathlib import Path

import nbtlib
import numpy as np
from nbtlib import Byte, Compound, Int, List, String

RACINE = Path(__file__).resolve().parent.parent
SAVE = RACINE / "launcher/instances/Build-1.21.1/minecraft/saves/Arene Tenebres"
NOM = "tenebres"
SX = SZ = 104
SOL = 18
SY = SOL + 28
C = 52
CEN = 52.0
R = 52.0
RD = 47.5
ORIGINE = (-52, 86 - SOL, -52)
rng = random.Random(6160)


def S(n, **p):
    n = n if ":" in n else "minecraft:" + n
    return (n, tuple(sorted((k, str(v).lower() if isinstance(v, bool) else str(v)) for k, v in p.items())))


AIR = S("air")
DB, DT, PD, CD = S("deepslate_bricks"), S("deepslate_tiles"), S("polished_deepslate"), S("chiseled_deepslate")
CDS, SCULK, RENF = S("cobbled_deepslate"), S("sculk"), S("reinforced_deepslate")
TAPIS = S("black_carpet")
CAPTEUR = S("sculk_sensor", power=0, sculk_sensor_phase="inactive", waterlogged=False)
HURLEUR = S("sculk_shrieker", can_summon=True, shrieking=False, waterlogged=False)
LANT = S("soul_lantern", hanging=False, waterlogged=False)
BOUGIE = S("black_candle", candles=1, lit=True, waterlogged=False)
blocs = {}
NBT = {}
occ = {}
APPS, TOURS, RUINES, CAPTEURS, HURLEURS = [], [], [], [], []


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


def commande(x, y, z, cmd, sorte="command_block", facing="down", auto=False):
    put(x, y, z, S(sorte, conditional=False, facing=facing))
    NBT[(x, y, z)] = Compound({"id": String("minecraft:command_block"), "Command": String(cmd), "auto": Byte(1 if auto else 0), "TrackOutput": Byte(0),
                               "conditionMet": Byte(0), "powered": Byte(0), "UpdateLastExecution": Byte(1)})


H = np.zeros((SX, SZ), dtype=int)
for x in range(SX):
    for z in range(SZ):
        d = dist(x, z)
        if d < RD:
            H[x, z] = SOL + 13 + int(11 * math.sqrt(max(0, 1 - (d / 48) ** 2)))


def dedans(x, y, z):
    return 0 <= x < SX and 0 <= z < SZ and H[x, z] > 0 and SOL < y < H[x, z]


# ====================================================================== 1. sol, rempart, voute
for x in range(SX):
    for z in range(SZ):
        d = dist(x, z)
        if d >= R:
            continue
        if d < RD:
            put(x, SOL, z, SCULK if bruit(x, z, 13, 3) > 0.63 else DT)
        else:
            a = ang(x, z)
            for y in range(SOL, SOL + 14):
                put(x, y, z, DB)
            put(x, SOL + 14, z, PD)
            if d >= 50.5 and int(a * 1.5) % 2 == 0:
                put(x, SOL + 15, z, PD)
for sp in range(-9, 9):                                # porte nord : passage sec, puis un mur qui coupe le jour
    for z in range(0, 12):
        x = C + sp
        if dist(x, z) >= R:
            continue
        passage = -2 <= sp <= 1
        if passage:
            put(x, SOL, z, PD)
            for y in range(SOL + 1, SOL + 5):
                put(x, y, z, AIR)
        if z < 5:
            for y in range(SOL + 1, SOL + 17):
                if passage and y <= SOL + 4:
                    continue
                cote = sp < -5 or sp > 4
                if cote or y >= SOL + 5:
                    put(x, y, z, PD if y >= SOL + 15 or cote else DB)
for x in range(C - 5, C + 5):                          # couloir fermé jusqu'à la cour
    for z in range(4, 12):
        if not (C - 2 <= x < C + 2):
            for y in range(SOL + 1, SOL + 7):
                put(x, y, z, DB)
        else:
            put(x, SOL + 5, z, DB)
for x in range(C - 5, C + 5):                          # ecran en face de la sortie du couloir
    for y in range(SOL + 1, SOL + 7):
        put(x, y, 15, DB if y < SOL + 6 else PD)
for x in range(C - 6, C + 6):
    for z in range(0, 17):
        occ[(x, z)] = "porte"
for x in range(SX):
    for z in range(SZ):
        if dist(x, z) >= R:
            continue
        for y in range(SOL + 1, SY):
            if dedans(x, y, z) or (x, y, z) in blocs:
                continue
            if dedans(x, y - 1, z) or any(dedans(q[0], y, q[1]) for q in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1))):
                d, a = dist(x, z), ang(x, z)
                cote = any(adiff(a, 45 * k + 22.5) * math.pi / 180 * max(d, 1) < 1 for k in range(8))
                put(x, y, z, PD if cote else DB)
print("[1] rempart et voute :", len(blocs), flush=True)

# ====================================================================== 2. sanctuaire central
for x in range(C - 17, C + 17):
    for z in range(C - 17, C + 17):
        d, a = dist(x, z), ang(x, z)
        if d < 13.5:
            put(x, SOL + 1, z, PD)
            occ[(x, z)] = "sanctuaire"
        if d < 9.5:
            put(x, SOL + 2, z, DB if d >= 8 else CD if d >= 7 else DT)
        if 14 <= d < 15.6:
            occ[(x, z)] = "sanctuaire"
            if any(adiff(a, 90 * k) * math.pi / 180 * d < 2.6 for k in range(4)):
                put(x, SOL, z, CD)
                continue
            for y in range(SOL + 1, SOL + 3 + int(6 * bruit(a, 0, 30, 5))):
                put(x, y, z, DB)
for x in range(C - 2, C + 2):
    for z in range(C - 2, C + 2):
        if dist(x, z) < 2.3:
            put(x, SOL + 3, z, RENF)
for x, z in ((C - 1, C - 1), (C, C - 1), (C - 1, C), (C, C)):
    put(x, SOL + 4, z, RENF)
put(C, SOL + 5, C, LANT)
for a in (45, 135, 225, 315):
    x, z = cellule(6, a)
    put(x, SOL + 3, z, HURLEUR)
    HURLEURS.append((x, SOL + 3, z))
    x, z = cellule(9, a + 45)                          # ces capteurs n'ont pas de commande : ils ne servent qu'a faire hurler
    put(x, SOL + 3 if dist(x, z) < 9.5 else SOL + 2, z, CAPTEUR)
print("[2] sanctuaire :", len(blocs), flush=True)

# ====================================================================== 3. tours delabrees, ruines, stalagmites
for g in (0, 60, 120, 180, 240, 300):
    x0, z0 = cellule(39, g)
    assert libre(x0, z0, 5), ("tour", g)
    reserve(x0, z0, 5, "tour")
    vers_centre = (g + 180) % 360
    YT = SOL + 11
    nord = z0 < C
    for x in range(x0 - 5, x0 + 5):
        for z in range(z0 - 5, z0 + 5):
            r_ = math.hypot(x + .5 - x0, z + .5 - z0)
            a = math.degrees(math.atan2(z + .5 - z0, x + .5 - x0)) % 360
            if r_ < 3.4:
                put(x, YT, z, DT)
            elif r_ < 4.7:
                for y in range(SOL + 1, SOL + 15):
                    porte = adiff(a, vers_centre) < 26 and y <= SOL + 3
                    fente = y in (SOL + 6, SOL + 7, SOL + 13) and any(adiff(a, b) < 8 for b in (g, g + 90, g + 180, g + 270))
                    casse = y >= SOL + 13 and bruit(a, 0, 45, 7) > 0.5
                    if not (porte or fente or casse):
                        put(x, y, z, PD if y == SOL + 1 else DB)
    lz, mur, fa = (z0 - 3, z0 - 4, "south") if nord else (z0 + 2, z0 + 3, "north")
    for y in range(SOL + 1, YT + 1):
        assert nom((x0, y, mur)) is not None, ("echelle sans mur", g, y)
        put(x0, y, lz, S("ladder", facing=fa, waterlogged=False))
    for x in range(x0 - 3, x0 + 3):                    # en haut, le tapis etouffe les pas
        for z in range(z0 - 3, z0 + 3):
            if math.hypot(x + .5 - x0, z + .5 - z0) < 3.4 and (x, z) != (x0, lz):
                put(x, YT + 1, z, TAPIS)
    TOURS.append((x0, z0, YT, lz))
for k in range(10):                                    # maisons sans age : murs ronges, une moitie de toit
    a = 18 + 36 * k
    cx, cz = cellule(28, a)
    hx, hz = (5, 4) if abs(math.cos(math.radians(a))) > abs(math.sin(math.radians(a))) else (4, 5)
    if not libre(cx, cz, 2):
        continue
    for x in range(cx - hx, cx + hx + 1):
        for z in range(cz - hz, cz + hz + 1):
            if (x, z) in occ:
                continue
            bord = x in (cx - hx, cx + hx) or z in (cz - hz, cz + hz)
            if bord:
                centre = abs(x - cx) <= 1 or abs(z - cz) <= 1
                ht = 2 + int(5 * bruit(x + z, 0, 5, 9))
                for y in range(SOL + 1, SOL + 1 + ht):
                    if not (centre and y <= SOL + 3):
                        put(x, y, z, PD if y == SOL + ht else DB)
            elif (x - cx) * (1 if k % 2 else -1) > 0:
                put(x, SOL + 5, z, DT)
    reserve(cx, cz, max(hx, hz), "ruine")
    RUINES.append((cx, cz))


def cone(x, z, r_, ht, bas, bloc=CDS):
    """Stalagmite ou stalactite : un cone irregulier, jamais une colonne droite."""
    for t in range(ht):
        y = bas + (t if bas <= SOL + 2 else -t)
        ra = r_ * (1 - t / ht) ** .8 + .4
        cx_, cz_ = x + 1.5 * (bruit(t, x, 6, 14) - .5), z + 1.5 * (bruit(t, z, 6, 15) - .5)
        for u in range(int(x - r_ - 2), int(x + r_ + 3)):
            for v in range(int(z - r_ - 2), int(z + r_ + 3)):
                if math.hypot(u + .5 - cx_, v + .5 - cz_) < ra and dedans(u, y, v) and (u, y, v) not in blocs:
                    put(u, y, v, bloc)


n_st = 0
for _ in range(400):
    x, z = cellule(rng.uniform(18, 45), rng.uniform(0, 360))
    r_ = rng.uniform(1.6, 3)
    if n_st >= 14 or not libre(x, z, r_ + 2):
        continue
    cone(x, z, r_, rng.randint(6, 13), SOL + 1)
    reserve(x, z, r_ + 1, "stalagmite")
    n_st += 1
for _ in range(36):                                    # stalactites sous la voute, loin de la tete des joueurs
    x, z = cellule(rng.uniform(4, 44), rng.uniform(0, 360))
    if H[x, z]:
        cone(x, z, rng.uniform(1, 2.2), rng.randint(3, 7), int(H[x, z]) - 1)
print("[3] tours, ruines, roches :", len(blocs), "tours", len(TOURS), "ruines", len(RUINES), "stalagmites", n_st, flush=True)

# ====================================================================== 4. apparitions, chemins muets, capteurs
for i in range(10):
    a = 36 * i                                         # la porte nord tombe entre deux apparitions
    for dr, da in [(u, v) for v in (0, 4, -4, 8, -8) for u in (45, 44, 43, 42, 41)]:
        x, z = cellule(dr, a + da)
        if libre(x, z, 1) and vide((x, SOL + 1, z)):
            break
    else:
        raise SystemExit(f"apparition {i} sans place")
    put(x, SOL, z, CD)
    bx, bz = (x + 1, z) if x < C else (x - 1, z)
    put(bx, SOL + 1, bz, BOUGIE)
    reserve(x, z, 1, "apparition")
    APPS.append((x, SOL + 1, z))
for x in range(SX):                                    # un anneau et 4 rayons de tapis : on y court sans bruit, tout le monde le sait
    for z in range(SZ):
        d, a = dist(x, z), ang(x, z)
        anneau = 17 <= d < 19
        rayon = 16 <= d < 46 and any(adiff(a, 90 * k) * math.pi / 180 * d < 1 for k in range(4))
        if (anneau or rayon) and vide((x, SOL + 1, z)) and nom((x, SOL, z)) in ("deepslate_tiles", "sculk", "chiseled_deepslate"):
            put(x, SOL + 1, z, TAPIS)
PAS = 9
for i in range(-7, 8):                                 # capteurs en quinconce
    for j in range(-7, 8):
        x0, z0 = C + PAS * i + (PAS // 2 if j % 2 else 0), C + round(PAS * .87 * j)
        if dist(x0, z0) >= 46.5 or dist(x0, z0) < 16:
            continue
        pris = None
        for u, v in sorted(((u, v) for u in range(-3, 4) for v in range(-3, 4)), key=lambda t: abs(t[0]) + abs(t[1])):
            q = (x0 + u, z0 + v)
            if vide((q[0], SOL + 1, q[1])) and nom((q[0], SOL, q[1])) in ("deepslate_tiles", "sculk") and occ.get(q) in (None, "ruine", "stalagmite"):
                pris = q
                break
        if pris:
            CAPTEURS.append(pris)
for x, z in CAPTEURS:
    put(x, SOL + 1, z, CAPTEUR)
    commande(x, SOL, z, "effect give @p[distance=..9,gamemode=!spectator] minecraft:glowing 6 0")
    commande(x, SOL - 1, z, "effect give @p[distance=..9,gamemode=!spectator] minecraft:slowness 4 1", "chain_command_block", "down", True)
    commande(x, SOL - 2, z, "effect give @a[distance=..12,gamemode=!spectator] minecraft:darkness 6 0", "chain_command_block", "down", True)
    commande(x, SOL - 3, z, "playsound minecraft:entity.warden.heartbeat master @a[distance=..28] ~ ~ ~ 1 1", "chain_command_block", "down", True)
CHAINE = ["gamerule commandBlockOutput false", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doWardenSpawning false", "time set 18000", "difficulty normal"]
for i, c in enumerate(CHAINE):
    commande(C - 2 + i, SOL - 6, C, c, "repeating_command_block" if i == 0 else "chain_command_block", "east", True)
for a in (22.5, 112.5, 202.5, 292.5):                  # rares lueurs sur l'anneau
    x, z = cellule(21, a)
    if vide((x, SOL + 1, z)):
        put(x, SOL + 1, z, LANT)
print("[4] capteurs :", len(CAPTEURS), "commandes", len(NBT), flush=True)

# ====================================================================== 5. controles
err = []
for p, s in blocs.items():
    x, y, z = p
    n = s[0].split(":")[1]
    if n != "air" and dist(x, z) > R + 1e-9:
        err.append(f"depasse {n} {p}")
    if n == "black_carpet" and nom((x, y - 1, z)) in (None, "air") and y - 1 > SOL:
        err.append(f"tapis sans support {p}")
    if n in ("chest", "barrel", "hopper", "dropper", "dispenser", "spawner", "water", "lava"):
        err.append(f"interdit {n} {p}")
for x, z in CAPTEURS:
    if nom((x, SOL, z)) != "command_block":
        err.append(f"capteur sans commande {(x, z)}")
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
    print("ATTENTION : monde Arene Tenebres introuvable")

ci = {"sanctuaire": [C + 3, SOL + 3, C + 3], "bas du sanctuaire": [C + 11, SOL + 2, C], "anneau de tapis": [C + 17, SOL + 1, C]}
ci.update({f"apparition {i + 1}": list(p) for i, p in enumerate(APPS)})
ci.update({f"haut de tour {i + 1}": [x + 1, yt + 1, z] for i, (x, z, yt, lz) in enumerate(TOURS)})
ci.update({f"ruine {i + 1}": [x, SOL + 1, z] for i, (x, z) in enumerate(RUINES)})
(RACINE / "exports" / f"{NOM}_cibles.json").write_text(json.dumps({"_marque": "[claude-build]", "sol": SOL, "depart": [C, SOL + 1, 1], "cibles": ci}, ensure_ascii=False, indent=1), encoding="utf-8")


def w(p):
    return " ".join(str(v) for v in monde(*p))


md = ["<!-- [claude-build] genere par sources/tenebres.py -->", "# Arène Les Ténèbres", "", "Monde `saves/Arene Tenebres`, centre X0 Z0, sol Y86, porte au nord. Le danger : faire du bruit.", "",
      "## Le bruit", "", f"- {len(CAPTEURS)} capteurs de sculk au sol. Un pas, un saut ou un coup à moins de 8 blocs déclenche le capteur.",
      "- Le joueur le plus proche brille 6 secondes à travers les murs et ralentit 4 secondes. Tous ceux à moins de 12 blocs sont plongés dans l'obscurité 6 secondes. Un battement de cœur s'entend à 28 blocs.",
      "- Accroupi, on ne déclenche rien.", "- Les tapis noirs étouffent les pas : un anneau autour du sanctuaire et 4 rayons.", "",
      "## Le sanctuaire", "", "- 4 hurleurs au centre, en " + " ; ".join(w(p) for p in HURLEURS) + ".",
      "- Faire du bruit au centre les fait hurler. Le Warden est coupé : aucun ne sort, même si le cri reste inquiétant.", "",
      "Apparitions : " + " ; ".join(w(p) for p in APPS), "",
      "## À savoir", "", "- Les blocs de commande bloquent le temps à minuit, coupent le Warden et passent la difficulté en Normal.", "- Sur un serveur : `enable-command-block=true`.", "- Le fonctionnement des capteurs se vérifie en jeu."]
(RACINE / "arene_tenebres.md").write_text("\n".join(md) + "\n", encoding="utf-8")

from PIL import Image, ImageDraw
COUL = {"deepslate_bricks": (72, 72, 78), "deepslate_tiles": (55, 55, 60), "polished_deepslate": (88, 88, 92), "chiseled_deepslate": (105, 105, 110), "cobbled_deepslate": (66, 66, 70), "sculk": (15, 60, 72),
        "sculk_sensor": (40, 200, 215), "sculk_shrieker": (230, 225, 170), "black_carpet": (18, 18, 22), "reinforced_deepslate": (110, 125, 115), "soul_lantern": (90, 230, 240), "ladder": (150, 110, 60), "black_candle": (240, 200, 90)}
CACHE = {"air", "light"}
s_ = 5
Wd, Hh = (SX + SZ) * s_ + 40, (SX + SZ) * s_ // 2 + SY * s_ + 60
img = Image.new("RGB", (Wd, Hh), (20, 24, 30))
dr = ImageDraw.Draw(img)


def ouvert(p):
    n = nom(p)
    return (p[1] > SOL) if n is None else n in CACHE


def voute(p):
    return H[p[0], p[2]] > 0 and p[1] >= H[p[0], p[2]] - 7 and p[1] > SOL + 12


vis = [(p, s[0].split(":")[1]) for p, s in blocs.items() if s[0].split(":")[1] not in CACHE and p[1] >= SOL and not voute(p) and not (dist(p[0], p[2]) >= 44 and p[1] > SOL + 13)]
vis.sort(key=lambda t: (t[0][0] + t[0][2], t[0][1]))
for (x, y, z), n in vis:
    c = COUL.get(n, (120, 120, 125))
    px = (x - z) * s_ + SZ * s_ + 20
    py = (x + z) * s_ // 2 - y * s_ + SY * s_ + 20
    if ouvert((x, y + 1, z)) or voute((x, y + 1, z)):
        dr.polygon([(px, py), (px + s_, py + s_ // 2), (px, py + s_), (px - s_, py + s_ // 2)], fill=c)
    if ouvert((x + 1, y, z)):
        dr.polygon([(px + s_, py + s_ // 2), (px, py + s_), (px, py + 2 * s_), (px + s_, py + s_ + s_ // 2)], fill=tuple(int(v * .78) for v in c))
    if ouvert((x, y, z + 1)):
        dr.polygon([(px - s_, py + s_ // 2), (px, py + s_), (px, py + 2 * s_), (px - s_, py + s_ + s_ // 2)], fill=tuple(int(v * .62) for v in c))
img.save(RACINE / "exports" / f"{NOM}_vue.png")
print(f"Ecrit : {len(liste)} blocs, palette {len(palette)}, taille {SX}x{SY}x{SZ}, commandes {len(NBT)}")
print(f"POSE : /tp @s 0 120 0   puis   /place template atelier:{NOM} {ORIGINE[0]} {ORIGINE[1]} {ORIGINE[2]}")
