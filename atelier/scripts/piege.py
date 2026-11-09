# [claude-build]
"""
piege.py - Arene piege, 4e de la serie des 7, Ø104 : "Le Laboratoire du Scientifique" (refait le 2026-11-07 a partir de la Salle des Mecanismes).
Lieu de vie d'un scientifique : laboratoire, experiences ratees, machines, reserve, bibliotheque, quartiers de vie, serre, observatoire.
La roue dentee geante tourne maintenant dans la fosse, 10 blocs sous la grande salle : elle peut sauver celui qui tombe.
Plaques de pression sur blocs de commande (effets non mortels), un seul piege mortel : le plancher de la grande salle
s'efface au-dessus d'une fosse de lave. Usine Create en marche : moteurs, tapis roulants, presses, roue geante, marteaux-pilons.
Sol Y86, centre X0 Z0, porte au nord.
  .venv\\Scripts\\python.exe sources\\piege.py
Controle : sources\\verif_acces.py exports\\piege.nbt exports\\piege_cibles.json
Le fonctionnement de la redstone ne se verifie qu'en jeu.
"""
import json
import math
import random
from pathlib import Path

import nbtlib
import numpy as np
from nbtlib import Byte, Compound, Double, Float, Int, List, String

RACINE = Path(__file__).resolve().parent.parent
SAVE = RACINE / "launcher/instances/Build-1.21.1/minecraft/saves/Arene Piege"
NOM = "piege"
SX = SZ = 104
SOL = 18
SY = SOL + 18
C = 52
CEN = 52.0
R = 52.0
ORIGINE = (-52, 86 - SOL, -52)
PORTE = 270
rng = random.Random(4042)


def S(n, **p):
    n = n if ":" in n else "minecraft:" + n
    return (n, tuple(sorted((k, str(v).lower() if isinstance(v, bool) else str(v)) for k, v in p.items())))


AIR = S("air")
SB, CSB, CHB, SST = S("stone_bricks"), S("cracked_stone_bricks"), S("chiseled_stone_bricks"), S("smooth_stone")
PAND, AND = S("polished_andesite"), S("andesite")
GOLD, BASALT = S("gold_block"), S("polished_basalt", axis="y")
LANT = S("lantern", hanging=False, waterlogged=False)
LAVE = S("lava", level=0)
BARRIERE = S("barrier", waterlogged=False)
PLAQUE = S("stone_pressure_plate", powered=False)
LUMIERE = S("light", level=15, waterlogged=False)
CASING, BRASS, COPPER = S("create:andesite_casing"), S("create:brass_casing"), S("create:copper_casing")
blocs = {}
NBT = {}
PIEGES, SALLES, APPS = [], [], []
ENT = []
LIGNES = []
occ = {}
CIBLES = {}


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


def solide(p):
    n = nom(p)
    return (p[1] <= SOL) if n is None else n not in ("air", "lava", "lantern", "light", "stone_pressure_plate", "stone_button", "chain")


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


def commande(x, y, z, cmd, sorte="command_block", facing="up", auto=False):
    put(x, y, z, S(sorte, conditional=False, facing=facing))
    NBT[(x, y, z)] = Compound({"id": String("minecraft:command_block"), "Command": String(cmd), "auto": Byte(1 if auto else 0), "TrackOutput": Byte(0),
                               "conditionMet": Byte(0), "powered": Byte(0), "UpdateLastExecution": Byte(1)})


def moteur(x, y, z, facing, rpm):
    put(x, y, z, S("create:creative_motor", facing=facing))
    NBT[(x, y, z)] = Compound({"id": String("create:creative_motor"), "ScrollValue": Int(rpm)})


def courroie(cases, facing):
    for i, (x, y, z) in enumerate(cases):
        part = "start" if i == 0 else "end" if i == len(cases) - 1 else "middle"
        put(x, y, z, S("create:belt", casing=False, facing=facing, part=part, slope="horizontal", waterlogged=False))


# ====================================================================== 1. sol, rempart, murs
for x in range(SX):
    for z in range(SZ):
        d = dist(x, z)
        if d >= R:
            continue
        a = ang(x, z)
        if d >= 48:
            hm = SOL + 12 + int(2 * bruit(a, 0, 13, 1))
            for y in range(SOL, hm + 1):
                put(x, y, z, SB if bruit(a / 2, y, 7, 2) > 0.42 else AND)
            put(x, hm + 1, z, SST)
            if d >= 50.5 and int(a * 1.5) % 2 == 0:
                put(x, hm + 2, z, SB)
            if d >= 51:
                for y in range(hm + 2, SY):
                    if vide((x, y, z)):
                        put(x, y, z, BARRIERE)
        elif d >= 16.5:
            put(x, SOL, z, SB if bruit(x, z, 9, 3) < 0.64 else SST)
# anneau interieur (r 34-36), parois des salles, anneau de la grande salle
H_MUR = SOL + 7
for x in range(SX):
    for z in range(SZ):
        d, a = dist(x, z), ang(x, z)
        mur = 34 <= d < 36 or 15 <= d < 16.5
        cloison = 16.5 <= d < 34 and any(adiff(a, 22.5 + 45 * k) < 57.3 / d for k in range(8))
        if not (mur or cloison):
            continue
        porte_ext = 34 <= d < 36 and any(adiff(a, 45 * k) * math.pi / 180 * d < 2 for k in range(8))
        porte_int = 15 <= d < 16.5 and any(adiff(a, 90 * k) * math.pi / 180 * d < 2 for k in range(4))
        for y in range(SOL + 1, H_MUR + 1):
            if (porte_ext or porte_int) and y <= SOL + 3:
                continue
            put(x, y, z, SST if y == H_MUR else SB if bruit(x + y, z, 8, 4) > 0.42 else AND)
        if porte_ext or porte_int:
            put(x, SOL, z, CHB)
for sp in range(-9, 9):                                # porte nord
    for al in range(43, 52):
        x, z = gl(al, sp, PORTE)
        if dist(x, z) >= R:
            continue
        cote = sp < -5 or sp > 4
        for y in range(SOL + 1, SOL + 15):
            if cote or y > SOL + 9:
                put(x, y, z, BRASS if y == SOL + 14 else SB if (y - SOL) % 5 else AND)
            else:
                put(x, y, z, AIR)
        if not cote:
            put(x, SOL, z, PAND if al % 2 else SB)
        occ[(x, z)] = "porte"
print("[1] murs :", len(blocs), flush=True)

# ====================================================================== 2. grande salle et fosse
for x in range(C - 16, C + 16):
    for z in range(C - 16, C + 16):
        d = dist(x, z)
        if d < 15:
            put(x, SOL, z, PAND)
            for y in range(4, SOL):
                put(x, y, z, AIR)
            put(x, 3, z, LAVE)
            put(x, 2, z, LAVE)
            put(x, 1, z, S("blackstone"))
for x in range(C - 3, C + 3):                          # socle du tresor, debout au milieu de la fosse
    for z in range(C - 3, C + 3):
        if dist(x, z) < 2.8:
            for y in range(2, SOL + 2):
                put(x, y, z, SB)
            put(x, SOL + 2, z, CHB)
for x, z in ((C - 1, C - 1), (C, C - 1), (C - 1, C), (C, C)):
    put(x, SOL + 3, z, GOLD)
TX, TZ = C + 7, C                                      # la mauvaise dalle, a l'est du socle
put(TX, SOL + 1, TZ, PLAQUE)
x0, x1, z0, z1 = C - 15, C + 14, C - 15, C + 14
# les 5 dalles mortelles sont surveillees par la chaine enterree de l'usine (4c) : plus aucun bloc de commande ne pend dans la fosse
# remise en etat automatique : 26 repeteurs caches sous le sol, environ 10 secondes
ZR = C + 22
XS, XE = C - 13, C + 13
for x in range(XS, XE):
    put(x, SOL - 2, ZR, S("repeater", delay=4, facing="west", locked=False, powered=False))
put(XS - 1, SOL - 2, ZR, S("stone"))
commande(XE, SOL - 2, ZR, f"fill ~{x0 - XE} ~2 ~{z0 - ZR} ~{x1 - XE} ~2 ~{z1 - ZR} minecraft:polished_andesite replace minecraft:air", facing="south")
commande(XE, SOL - 2, ZR + 1, f"setblock ~{TX - XE} ~3 ~{TZ - ZR - 1} minecraft:stone_pressure_plate", "chain_command_block", "south", True)
commande(XE, SOL - 2, ZR + 2, f"setblock ~{XS - 1 - XE} ~ ~{-2} minecraft:stone", "chain_command_block", "south", True)
commande(XE, SOL - 2, ZR + 3, "kill @e[type=item,distance=..70]", "chain_command_block", "south", True)
# 4 dalles de plus autour du tresor : meme effondrement, meme minuteur
DALLES = [cellule(8, a) for a in (70, 150, 225, 305)]
for i, (dx_, dz_) in enumerate(DALLES):
    put(dx_, SOL + 1, dz_, PLAQUE)
    commande(XE, SOL - 2, ZR + 4 + i, f"setblock ~{dx_ - XE} ~3 ~{dz_ - ZR - 4 - i} minecraft:stone_pressure_plate", "chain_command_block", "south", True)
# remise en etat : bouton a l'entree, 3 commandes a la chaine
BX, BZ = gl(40, 0, PORTE)
BX, BZ = BX + 6, BZ
put(BX, SOL, BZ, CHB)
put(BX, SOL + 1, BZ, S("stone_button", face="floor", facing="north", powered=False))
commande(BX, SOL - 1, BZ, f"fill ~{x0 - BX} ~1 ~{z0 - BZ} ~{x1 - BX} ~1 ~{z1 - BZ} minecraft:polished_andesite replace minecraft:air", facing="down")
commande(BX, SOL - 2, BZ, f"setblock ~{TX - BX} ~3 ~{TZ - BZ} minecraft:stone_pressure_plate", "chain_command_block", "down", True)
commande(BX, SOL - 3, BZ, "kill @e[type=item,distance=..70]", "chain_command_block", "down", True)
for i, (dx_, dz_) in enumerate(DALLES):
    commande(BX, SOL - 4 - i, BZ, f"setblock ~{dx_ - BX} ~{5 + i} ~{dz_ - BZ} minecraft:stone_pressure_plate", "chain_command_block", "down", True)
print("[2] grande salle :", len(blocs), flush=True)

# ====================================================================== 3. pieges non mortels
EFFETS = [("Faiblesse", "effect give @a[distance=..6] minecraft:weakness 20 1"), ("Lenteur", "effect give @a[distance=..6] minecraft:slowness 15 2"),
          ("Cécité", "effect give @a[distance=..7] minecraft:blindness 8 0"), ("Nausée", "effect give @a[distance=..6] minecraft:nausea 12 0"),
          ("Fatigue", "effect give @a[distance=..6] minecraft:mining_fatigue 20 1"), ("Lévitation", "effect give @a[distance=..3] minecraft:levitation 3 1"),
          ("Obscurité", "effect give @a[distance=..8] minecraft:darkness 10 0"), ("Faim", "effect give @a[distance=..6] minecraft:hunger 15 1"),
          ("Repérage", "effect give @a[distance=..6] minecraft:glowing 30 0"), ("Poison léger", "effect give @a[distance=..4] minecraft:poison 4 0"),
          ("Pluie de flèches", "execute as @a[distance=..5] at @s run summon minecraft:arrow ~ ~6 ~ {Motion:[0.0d,-0.8d,0.0d],pickup:0b}"),
          ("Retour à la porte", None)]
gx, gz = gl(46, 0, PORTE)
EMPL = [(25, 45 * k + 12) for k in range(8)] + [(42, 45 + 90 * k) for k in range(4)]
rng.shuffle(EFFETS)
for (r_, a), (nomp, cmd) in zip(EMPL, EFFETS):
    x, z = cellule(r_, a)
    put(x, SOL, z, CSB)                                # indice : une dalle fissurée
    put(x, SOL + 1, z, PLAQUE)
    if cmd is None:
        cmd = f"tp @a[distance=..4] ~{gx - x} ~2 ~{gz - z}"
    commande(x, SOL - 1, z, cmd)
    PIEGES.append((nomp, (x, SOL + 1, z)))
    for u in range(x - 2, x + 3):
        for v in range(z - 2, z + 3):
            occ[(u, v)] = "piege"
print("[3] pieges :", len(PIEGES), flush=True)

# ====================================================================== 4. decor mecanique Create (immobile)
for a in (22.5, 112.5, 202.5, 292.5):                  # machines de la cour
    x, z = cellule(42, a)
    if any((x + i, z + j) in occ for i in (-2, 0, 2) for j in (-2, 0, 2)):
        x, z = cellule(43, a + 6)
    for i in (-1, 0, 1):
        for j in (-1, 0, 1):
            put(x + i, SOL + 1, z + j, CASING)
            occ[(x + i, z + j)] = "machine"
    for y in range(SOL + 2, SOL + 7):
        put(x, y, z, S("create:shaft", axis="y"))
    put(x, SOL + 4, z, S("create:cogwheel", axis="y"))
    put(x, SOL + 7, z, S("create:large_cogwheel", axis="y"))
    moteur(x, SOL + 2, z, "up", 16)
for a in (45, 135, 225, 315):                          # arbres sur l'anneau de la grande salle
    x, z = cellule(15.7, a)
    moteur(x, H_MUR + 1, z, "up", 16)
    put(x, H_MUR + 2, z, S("create:cogwheel", axis="y"))
for k in range(0, 360, 30):                            # engrenages dans le rempart, cote interieur
    x, z = cellule(47.5, k + 15)
    if (x, z) not in occ and not any(adiff(k + 15, c) < 12 for c in (270,)):
        axe = "x" if abs(math.cos(math.radians(k + 15))) > 0.7 else "z"
        put(x, SOL + 6, z, S("create:large_cogwheel", axis=axe))
        put(x, SOL + 5, z, S("create:shaft", axis=axe))
        c_, s_a = math.cos(math.radians(k + 15)), math.sin(math.radians(k + 15))
        if axe == "x":
            mx, mz, mf = x + (1 if c_ > 0 else -1), z, "west" if c_ > 0 else "east"
        else:
            mx, mz, mf = x, z + (1 if s_a > 0 else -1), "north" if s_a > 0 else "south"
        if nom((mx, SOL + 6, mz)) in ("stone_bricks", "andesite"):
            moteur(mx, SOL + 6, mz, mf, 16)
print("[4] decor Create :", len(blocs), flush=True)

# ====================================================================== 4b. decor : cloitre, salles amenagees, voute, grandes machines
BARS = S("iron_bars", waterlogged=False)
SLANT = S("lantern", hanging=True, waterlogged=False)
CHAINE = S("chain", axis="y", waterlogged=False)
for k in range(0, 360, 15):                            # cloitre : colonnes et galerie couverte contre le rempart
    a = k + 7.5
    if adiff(a, 270) < 16:
        continue
    x, z = cellule(44.5, a)
    if (x, z) in occ:
        continue
    for u, v in ((0, 0), (1, 0), (0, 1), (1, 1)):
        for y in range(SOL + 1, SOL + 7):
            put(x + u, y, z + v, CHB if y in (SOL + 1, SOL + 6) else SB)
for x in range(SX):
    for z in range(SZ):
        d, a = dist(x, z), ang(x, z)
        if 44 <= d < 48 and adiff(a, 270) >= 16 and (x, z) not in occ and vide((x, SOL + 7, z)):
            put(x, SOL + 7, z, SST)
for k in range(0, 360, 20):                            # pilastres du rempart
    a = k + 5
    if adiff(a, 270) < 14:
        continue
    x, z = cellule(47.3, a)
    for y in range(SOL + 1, SOL + 7):
        if vide((x, y, z)):
            put(x, y, z, CHB if y == SOL + 6 else SB)
for x in range(SX):                                    # toits des salles, avec un oculus a barreaux
    for z in range(SZ):
        d = dist(x, z)
        if 16.5 <= d < 34 and nom((x, SOL + 2, z)) in (None, "air"):
            ouvert = min(math.hypot(x + .5 - (CEN + 25 * math.cos(math.radians(45 * k))), z + .5 - (CEN + 25 * math.sin(math.radians(45 * k)))) for k in range(8)) < 2.6
            put(x, H_MUR, z, BARS if ouvert else SB)
for k in range(8):                                     # une lanterne suspendue par salle
    x, z = cellule(30, 45 * k)
    for y in (H_MUR - 1, H_MUR - 2):
        put(x, y, z, CHAINE)
    put(x, H_MUR - 3, z, SLANT)
for k in range(8):                                     # voute de la grande salle : colonnes, couronne, rayons, lustre
    a = 22.5 + 45 * k
    x, z = cellule(13.7, a)
    for u, v in ((0, 0), (1, 0), (0, 1), (1, 1)):
        for y in range(4, H_MUR + 2):
            put(x + u, y, z + v, CHB if y in (SOL + 1, H_MUR + 1) else SB)
for x in range(C - 16, C + 16):
    for z in range(C - 16, C + 16):
        d = dist(x, z)
        if 12.5 <= d < 15:
            put(x, H_MUR + 2, z, SST)
for y in range(H_MUR + 1, SOL + 5, -1):
    put(C, y, C, CHAINE)
put(C, SOL + 5, C, SLANT)
for a in (67.5, 157.5, 247.5, 337.5):                  # grandes machines Create dans la cour
    x, z = cellule(40, a)
    if any((x + i, z + j) in occ for i in (-2, 2) for j in (-2, 2)):
        continue
    for i in range(-2, 3):
        for j in range(-2, 3):
            put(x + i, SOL + 1, z + j, CASING)
            occ[(x + i, z + j)] = "machine"
    for j in (-2, 2):
        for y in range(SOL + 2, SOL + 7):
            put(x, y, z + j, S("create:fluid_tank", bottom=y == SOL + 2, shape="window", top=y == SOL + 6))
    courroie([(x + i, SOL + 2, z) for i in range(-2, 3)], "east")     # vraie ligne de presse : lingot -> plaque
    moteur(x + 2, SOL + 2, z + 1, "north", 32)
    put(x, SOL + 4, z, S("create:mechanical_press", facing="north"))
    moteur(x, SOL + 4, z + 1, "north", 32)
    moteur(x, SOL + 5, z, "up", 16)
    put(x, SOL + 6, z, S("create:shaft", axis="y"))
    put(x, SOL + 7, z, S("create:large_cogwheel", axis="y"))
    LIGNES.append((x, z))
print("[4b] decor :", len(blocs), flush=True)

# ====================================================================== 4c. usine : roue geante, couloirs a tapis, marteaux-pilons
# plateforme tournante : la roue dentee geante, descendue dans la fosse, 10 blocs sous le plancher de la grande salle.
# Celui qui tombe du centre peut atterrir dessus ; entre les rayons et autour, c'est toujours la lave.
# Le roulement et son moteur sont caches dans le socle du tresor ; la tranche du socle tourne avec la roue.
YW = SOL - 10
ROUE_AVANT = (C, H_MUR + 3, C)
ROUE = set()
for x in range(C - 13, C + 14):
    for z in range(C - 13, C + 14):
        u, v = x - C, z - C
        r_, a = math.hypot(u, v), math.degrees(math.atan2(v, u)) % 360
        bras = any(abs(u * math.sin(math.radians(b)) - v * math.cos(math.radians(b))) < 1 and u * math.cos(math.radians(b)) + v * math.sin(math.radians(b)) > 0 for b in range(0, 360, 60))
        if r_ <= 3.2 or (r_ < 9.5 and bras) or 9.5 <= r_ <= 11 or (11 < r_ <= 12 and a % 30 < 15):
            ROUE.add((x, z))
vus_, pile_ = {(C, C)}, [(C, C)]
while pile_:
    x, z = pile_.pop()
    for q in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
        if q in ROUE and q not in vus_:
            vus_.add(q)
            pile_.append(q)
assert vus_ == ROUE, "roue en plusieurs morceaux"
SOCLE = {(x, z) for x in range(C - 3, C + 3) for z in range(C - 3, C + 3) if dist(x, z) < 2.8}
assert SOCLE <= ROUE, "le socle depasse du moyeu"
for x, z in ROUE:
    assert vide((x, YW, z)) or (x, z) in SOCLE, (x, z, nom((x, YW, z)))
    for q in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
        assert q in ROUE or vide((q[0], YW, q[1])), f"la roue touche {q}"
    put(x, YW, z, COPPER if math.hypot(x - C, z - C) <= 3.2 else BRASS)
put(C, YW - 1, C, S("create:mechanical_bearing", facing="up"))
moteur(C, YW - 2, C, "up", 1)
ENT.append(Compound({"pos": List[Double]([Double(C + .5), Double(YW), Double(C + .5)]), "blockPos": List[Int]([Int(C), Int(YW), Int(C)]),
                     "nbt": Compound({"id": String("create:super_glue"), "From": List[Double]([Double(-12.5), Double(0), Double(-12.5)]),
                                      "To": List[Double]([Double(12.5), Double(1), Double(12.5)])})}))

# couloirs nord et sud : tapis vers l'exterieur, 3 marteaux, refuges, frise d'engrenages
XA, XB = C - 2, C + 1                                  # les 4 voies du tapis
U0, U1 = 16, 33
MARTEAUX = []
REFUGES = (21, 22, 27, 28)
for sens, face in ((-1, "north"), (1, "south")):
    def Zc(u):
        return C - 1 - u if sens < 0 else C + u
    for x in range(XA, XB + 1):
        courroie([(x, SOL, Zc(u)) for u in range(U0, U1 + 1)], face)
    moteur(XB + 1, SOL, Zc(U1), "west", 96 if sens < 0 else -96)
    for u in range(U0, U1 + 1):
        z = Zc(u)
        for x, xm in ((XA - 2, XA - 1), (XB + 2, XB + 1)):
            for q in ((x, z), (xm, z)):
                assert occ.get(q) in (None, "piege") and nom((q[0], SOL + 1, q[1])) != "stone_pressure_plate", (q, occ.get(q))
            for y in range(SOL + 1, SOL + 7):
                if u in REFUGES and y <= SOL + 2:
                    put(x, y, z, AIR)
                    continue
                put(x, y, z, CASING if u in (U0, U1) else SB)
            for y in (SOL + 1, SOL + 2):
                put(xm, y, z, AIR if u in REFUGES else CASING if u in (U0, U1) else SB)
            if u in REFUGES:
                put(x, SOL, z, CHB)
                put(xm, SOL, z, CHB)
    for x, axe_m, f in ((XA - 1, XA - 2, "east"), (XB + 1, XB + 2, "west")):   # frise : grande, petite, petite, grande...
        u = 18
        moteur(axe_m, SOL + 4, Zc(u), f, 16)
        while u <= 30:
            put(x, SOL + 4, Zc(u), S("create:large_cogwheel", axis="x"))
            if u < 30:
                put(x, SOL + 5, Zc(u + 1), S("create:cogwheel", axis="x"))
                put(x, SOL + 5, Zc(u + 2), S("create:cogwheel", axis="x"))
            u += 3
    for u in (19, 24, 31):
        za, zb = sorted((Zc(u), Zc(u + 1)))
        for x in range(XA - 1, XB + 2):
            for z in (za, zb):
                put(x, SOL + 6, z, BRASS)
        for x in range(XA, XB + 1):
            for z in (za, zb):
                put(x, SOL + 4, z, BRASS)
                put(x, SOL + 5, z, BRASS)
        MARTEAUX.append((za, zb, sens))

# horloge : une chaine de blocs de commande sous le sol, cycle de 6 secondes
CY, CZ, CX0, T = SOL - 4, C + 20, 12, 120
CMD = []


def rel(x, y, z):                                      # depuis un bloc de commande, ~ part du centre du bloc
    px = CX0 + len(CMD)
    return f"~{x - px} ~{y - CY} ~{z - CZ}"


def quand(tick):
    return f"execute if score #usine usine matches {tick % T}"


CMD.append("gamerule commandBlockOutput false")
CMD.append("gamerule doFireTick false")
CMD.append("scoreboard objectives add usine dummy")
CMD.append("scoreboard players add #usine usine 1")
CMD.append(f"execute if score #usine usine matches {T}.. run scoreboard players set #usine usine 0")
for i, (za, zb, sens) in enumerate(sorted(MARTEAUX, key=lambda m: (m[2], m[0]))):
    t0 = 20 * i
    boite = f"@a[dx={XB - XA},dy=2,dz=1,gamemode=!spectator]"

    def couche(y, bloc):
        px = CX0 + len(CMD)
        return f"fill ~{XA - px} ~{y - CY} ~{za - CZ} ~{XB - px} ~{y - CY} ~{zb - CZ} {bloc}"
    CMD.append(f"{quand(t0 - 20)} positioned {rel(C, SOL + 3, za)} run playsound minecraft:block.bell.use master @a[distance=..24] ~ ~ ~ 1 0.6")
    CMD.append(f"{quand(t0)} run {couche(SOL + 3, 'create:brass_casing')}")
    CMD.append(f"{quand(t0 + 3)} positioned {rel(XA, SOL + 1, za)} align xyz run damage {boite} 1000 minecraft:falling_anvil")    # le marteau tue
    CMD.append(f"{quand(t0 + 3)} run {couche(SOL + 2, 'create:brass_casing')}")
    CMD.append(f"{quand(t0 + 3)} positioned {rel(C, SOL + 2, za)} run playsound minecraft:block.anvil.land master @a[distance=..24] ~ ~ ~ 1 0.5")
    CMD.append(f"{quand(t0 + 6)} run {couche(SOL + 1, 'create:brass_casing')}")
    CMD.append(f"{quand(t0 + 26)} run {couche(SOL + 1, 'minecraft:air')}")
    CMD.append(f"{quand(t0 + 36)} run {couche(SOL + 2, 'minecraft:air')}")
    CMD.append(f"{quand(t0 + 46)} run {couche(SOL + 3, 'minecraft:air')}")
# plus de lingots sur les lignes de presse (demande de l'utilisateur) : les presses tournent a vide


def dalle(px_, pz_, action):
    return f"execute if block {rel(px_, SOL + 1, pz_)} minecraft:stone_pressure_plate[powered=true] {action}"


for px_, pz_ in [(TX, TZ)] + DALLES:                   # dalles mortelles : son, minuteur de remise en etat, puis le plancher disparait
    CMD.append(dalle(px_, pz_, f"positioned {rel(px_, SOL + 1, pz_)} run playsound minecraft:entity.wither.spawn master @a[distance=..60] ~ ~ ~ 1 0.6"))
    CMD.append(dalle(px_, pz_, f"run setblock {rel(XS - 1, SOL - 2, ZR)} minecraft:redstone_block"))
    CMD.append(dalle(px_, pz_, f"run fill {rel(x0, SOL, z0)} {rel(x1, SOL, z1)} minecraft:air replace minecraft:polished_andesite"))
for i, c in enumerate(CMD):
    x = CX0 + i
    assert (x, CY, CZ) not in blocs, (x, CY, CZ)
    commande(x, CY, CZ, c, "repeating_command_block" if i == 0 else "chain_command_block", "east", True)
print("[4c] usine :", len(blocs), "lignes de presse", len(LIGNES), "marteaux", len(MARTEAUX), "commandes", len(CMD), flush=True)

# ====================================================================== 4d. le lieu de vie du scientifique
GARDE = set()
DECO = set()
CIBLES_SCI = {}
PASSAGES_DOC, ZONES_DOC, ENT_DOC = [], [], []
WCU, WGR = S("waxed_cut_copper"), S("waxed_copper_grate", waterlogged=False)
BULBE = S("waxed_copper_bulb", lit=True, powered=False)
VITRE, SQ = S("glass"), S("smooth_quartz")
TRAPPE_H = S("oak_trapdoor", facing="north", half="top", open=False, powered=False, waterlogged=False)


def garder(x, z, r=2):
    for u in range(x - r, x + r + 1):
        for v in range(z - r, z + r + 1):
            GARDE.add((u, v))


def salon(x, z):
    return round(ang(x, z) / 45) % 8


OX, OZ = cellule(26, 22.5)                             # observatoire, sur les toits
# --- 2. voie de retour : corniche dans la fosse, tunnel, puits a echelle qui debouche en hauteur dans le laboratoire
XP = C + 22
for z in (C - 1, C):
    put(C + 14, YW, z, PAND)                            # la corniche, a 1 bloc du bord de la roue
    for x in range(C + 15, XP):
        put(x, YW, z, PAND)
        put(x, YW + 1, z, AIR)
        put(x, YW + 2, z, AIR)
put(XP, YW, C, PAND)
for y in range(YW + 1, SOL + 5):
    put(XP, y, C, S("ladder", facing="west", waterlogged=False))
for x in (XP - 1, XP, XP + 1):                          # le puits remonte dans une grosse conduite de cuivre
    for z in (C - 1, C, C + 1):
        for y in range(SOL + 1, SOL + 7):
            if (x, z) == (XP, C) and y < SOL + 6:
                continue
            if (x, z) == (XP - 1, C) and y in (SOL + 4, SOL + 5):
                put(x, y, z, AIR)                       # sortie a 3 blocs du sol : on ne peut pas y remonter depuis la salle
                continue
            put(x, y, z, WGR if y == SOL + 2 else WCU)
garder(XP - 1, C, 3)
PASSAGES_DOC.append(("Voie de retour depuis la roue", [(C + 14, YW + 1, C), (XP - 1, YW + 1, C), (XP, YW + 1, C), (XP, SOL + 4, C), (XP - 1, SOL + 4, C), (XP - 2, SOL + 1, C)],
                     "corniche dans la fosse, tunnel vers l'est, échelle dans une conduite de cuivre, sortie en hauteur dans le laboratoire (saut de 3 blocs)"))

# --- 3. salles ouvertes : arches, demi-murs, vitres, grilles, fusion bureau + quartiers de vie
CLOISONS = {22.5: "glass", 67.5: "iron_bars", 112.5: "iron_bars", 157.5: None, 202.5: "fusion", 247.5: "tinted_glass", 292.5: "iron_bars", 337.5: "glass"}
for cb, mat in CLOISONS.items():
    for x in range(SX):
        for z in range(SZ):
            d, a = dist(x, z), ang(x, z)
            if not (16.5 <= d < 34 and adiff(a, cb) < 57.3 / d):
                continue
            if mat == "fusion":
                if 19 <= d < 32:
                    for y in range(SOL + 1, H_MUR):
                        put(x, y, z, AIR)
                continue
            arche = abs(d - 26) < 1.6
            if arche:
                for y in range(SOL + 1, SOL + 4):
                    put(x, y, z, AIR)
                put(x, SOL, z, CHB)
            if mat and 18 <= d < 33:
                for y in range(SOL + 4 if arche else SOL + 3, H_MUR):
                    put(x, y, z, BARS if mat == "iron_bars" else S(mat))
    if mat != "fusion":
        garder(*cellule(26, cb), 3)
for k in range(8):                                     # devant chaque porte, le passage reste libre
    garder(*cellule(32.5, 45 * k), 2)
    if k % 2 == 0:
        garder(*cellule(18, 45 * k), 2)
for u in REFUGES:
    for z in (C - 1 - u, C + u):
        garder(XA - 3, z, 1)
        garder(XB + 3, z, 1)


def conduit(z, sens):
    """Conduit de ventilation d'1 bloc de haut a travers le mur exterieur. On ferme la trappe au-dessus de sa tete pour ramper."""
    murs = [x for x in range(SX) if 34 <= dist(x, z) < 36 and (x + .5 - CEN) * sens > 0]
    x_in, x_out = (min(murs) - 1, max(murs) + 1) if sens > 0 else (max(murs) + 1, min(murs) - 1)
    for x in murs:
        put(x, SOL + 1, z, AIR)
        put(x, SOL + 2, z, WGR)
    for x, vers in ((x_in, "east" if sens > 0 else "west"), (x_out, "west" if sens > 0 else "east")):
        assert vide((x, SOL + 1, z)) and solide((x, SOL, z)), ("entree de conduit", x, z)
        put(x, SOL + 2, z, S("oak_trapdoor", facing=vers, half="bottom", open=True, powered=False, waterlogged=False))
        garder(x, z, 1)
    return murs, x_in, x_out


PRES_CONDUITS = set()
for zc, sens, nomc in ((C + 6, -1, "Conduit ouest : cour - passage secret de la bibliothèque"), (C + 17, 1, "Conduit est : salle des expériences ratées - cour")):
    murs, xi, xo = conduit(zc, sens)
    PRES_CONDUITS |= {(x + u, zc + v) for x in murs for u in range(-3, 4) for v in range(-3, 4)}
    PASSAGES_DOC.append((nomc, [(xi, SOL + 1, zc), (xo, SOL + 1, zc)], "1 bloc de haut : se placer sous la trappe ouverte, la fermer, puis ramper"))
for x in range(SX):                                    # fenetres a barreaux dans l'anneau exterieur, vitres dans l'anneau de la grande salle
    for z in range(SZ):
        d, a = dist(x, z), ang(x, z)
        if 34 <= d < 36 and all(adiff(a, 45 * k) * math.pi / 180 * d >= 5 for k in range(8)) and int(a / 3) % 4 == 1 and (x, z) not in PRES_CONDUITS:
            for y in range(SOL + 2, SOL + 5):
                put(x, y, z, BARS)
        if 15 <= d < 16.5 and all(adiff(a, 90 * k) * math.pi / 180 * d >= 3 for k in range(4)) and int(a / 6) % 2 == 0:
            for y in range(SOL + 2, SOL + 5):
                put(x, y, z, VITRE)
for x in range(SX):                                    # verrieres sur le laboratoire et la serre, toit effondre sur les experiences ratees
    for z in range(SZ):
        d = dist(x, z)
        if 16.5 <= d < 34 and nom((x, H_MUR, z)) in ("stone_bricks", "iron_bars"):
            k = salon(x, z)
            if k in (0, 7):
                put(x, H_MUR, z, VITRE)
            elif k == 1 and bruit(x, z, 5, 31) > 0.64 and math.hypot(x - OX, z - OZ) > 4:
                put(x, H_MUR, z, AIR)
for x in range(C - 16, C + 16):                        # passerelle metallique au-dessus de la grande salle, d'est en ouest
    for z in (C - 1, C):
        if dist(x, z) < 15:
            put(x, H_MUR, z, WCU)
    for z in (C - 2, C + 1):
        if dist(x, z) < 14.5:
            put(x, H_MUR + 1, z, BARS)
    for z in range(C - 2, C + 2):
        if nom((x, H_MUR + 2, z)) == "smooth_stone":
            put(x, H_MUR + 2, z, AIR)                   # la couronne s'ouvre au passage de la passerelle
blocs.pop((C, H_MUR + 1, C), None)                      # le lustre pend maintenant sous la passerelle
PASSAGES_DOC.append(("Passerelle métallique", [(C - 15, H_MUR + 1, C), (C + 14, H_MUR + 1, C)], "au-dessus de la grande salle, relie les toits du bureau et du laboratoire"))
CIBLES_SCI["passerelle"] = [C + 5, H_MUR + 1, C]


def escalier(cases, facing, palier=None, nomc=""):
    for i, (x, z) in enumerate(cases):
        assert (x, z) not in occ and vide((x, SOL + 1, z)), ("escalier", nomc, x, z, occ.get((x, z)))
        for y in range(SOL + 1, SOL + 1 + i):
            put(x, y, z, SB)
        put(x, SOL + 1 + i, z, S("stone_brick_stairs", facing=facing, half="bottom", shape="straight", waterlogged=False))
        for y in (SOL + 2 + i, SOL + 3 + i):
            if y == H_MUR and not vide((x, y, z)):
                put(x, y, z, AIR)                       # tremie dans le toit
        occ[(x, z)] = "escalier"
        garder(x, z, 1)
    if palier:
        x, z = palier
        for y in range(SOL + 1, H_MUR + 1):
            put(x, y, z, SB)
        occ[(x, z)] = "escalier"
    PASSAGES_DOC.append((nomc, [(cases[0][0], SOL + 1, cases[0][1]), (cases[-1][0], H_MUR + 1, cases[-1][1])], "escalier vers les toits"))


escalier([(94 - i, C + 6) for i in range(7)], "west", (87, C + 6), "Escalier est (cour - toits)")
escalier([(9 + i, C - 7) for i in range(7)], "east", (16, C - 7), "Escalier ouest (cour - toits)")
escalier([(34, 72 + i) for i in range(7)], "south", None, "Escalier de la réserve (réserve - toits)")
assert not vide((34, H_MUR, 79)), "pas de toit en haut de l'escalier de la reserve"
CIBLES_SCI.update({"toit par l'escalier est": [86, H_MUR + 1, C + 6], "toit par l'escalier ouest": [17, H_MUR + 1, C - 7], "toit par l'escalier de la reserve": [34, H_MUR + 1, 79]})

# bibliotheque : rangee d'etageres, passage secret derriere, balcon (mezzanine) au-dessus
SC = 23
corr = [(x, z) for x in range(19, SC) for z in range(55, 63) if dist(x, z) < 34 and salon(x, z) == 4 and vide((x, SOL + 1, z))]
for z in range(55, 63):
    if dist(SC, z) < 34:
        for y in range(SOL + 1, SOL + 4):
            put(SC, y, z, S("bookshelf"))
for x in range(19, SC):
    if dist(x, 54) < 34 and vide((x, SOL + 1, 54)):
        for y in range(SOL + 1, SOL + 4):
            put(x, y, 54, S("bookshelf"))
for x, z in corr + [(SC, z) for z in range(55, 63) if dist(SC, z) < 34]:
    put(x, SOL + 4, z, S("spruce_planks"))
    GARDE.add((x, z))
for z in range(55, 63):
    if dist(SC, z) < 34 and z != 58:
        put(SC, SOL + 5, z, S("spruce_fence", east=False, north=False, south=False, west=False, waterlogged=False))
for y in range(SOL + 1, SOL + 5):
    put(SC + 1, y, 58, S("ladder", facing="east", waterlogged=False))
garder(SC + 1, 58, 1)
PASSAGES_DOC.append(("Passage secret de la bibliothèque", [(20, SOL + 1, 62), (19, SOL + 1, 58)], "derrière une rangée d'étagères, ouvert au sud, mène au conduit ouest"))
PASSAGES_DOC.append(("Balcon de la bibliothèque (mezzanine)", [(SC + 1, SOL + 1, 58), (20, SOL + 5, 60)], "échelle contre les étagères"))
CIBLES_SCI.update({"passage secret de la bibliotheque": [20, SOL + 1, 60], "balcon de la bibliotheque": [20, SOL + 5, 60]})

# trappes et tunnel sous le plancher : quartiers de vie - bureau
for z in range(37, 59):
    assert all((31, y, z) not in blocs for y in (SOL - 3, SOL - 2, SOL - 1)), ("tunnel", z)
    put(31, SOL - 3, z, PAND)
    put(31, SOL - 2, z, AIR)
    put(31, SOL - 1, z, AIR)
for zt, fa in ((37, "south"), (58, "north")):
    put(31, SOL, zt, TRAPPE_H)
    put(31, SOL - 1, zt, S("ladder", facing=fa, waterlogged=False))
    put(31, SOL - 2, zt, S("ladder", facing=fa, waterlogged=False))
    garder(31, zt, 1)
PASSAGES_DOC.append(("Trappes et tunnel sous le plancher", [(31, SOL, 37), (31, SOL, 58)], "trappe dans le sol des quartiers de vie, échelle, tunnel, trappe dans le bureau"))
CIBLES_SCI["tunnel sous le plancher"] = [31, SOL - 2, 48]

# observatoire sur les toits, entre le laboratoire et les experiences ratees
YO = H_MUR + 6
for x in range(OX - 2, OX + 3):
    for z in range(OZ - 2, OZ + 3):
        assert not vide((x, H_MUR, z)), ("observatoire sans toit", x, z)
        bord = x in (OX - 2, OX + 2) or z in (OZ - 2, OZ + 2)
        coin = x in (OX - 2, OX + 2) and z in (OZ - 2, OZ + 2)
        if coin:
            for y in range(H_MUR + 1, YO):
                put(x, y, z, S("polished_andesite"))
        put(x, YO, z, WCU)
        if (x, z) == (OX - 2, OZ - 2):
            put(x, YO + 1, z, S("polished_andesite"))   # poteau plein : l'echelle du haut ne tient pas sur des barreaux
        elif bord and (x, z) != (OX - 1, OZ - 2):
            put(x, YO + 1, z, BARS)
for y in range(H_MUR + 1, YO + 2):
    put(OX - 1, y, OZ - 2, S("ladder", facing="east", waterlogged=False))
put(OX, YO + 1, OZ, S("spruce_fence", east=False, north=False, south=False, west=False, waterlogged=False))
put(OX, YO + 2, OZ, S("end_rod", facing="north"))
put(OX + 1, YO + 1, OZ + 1, S("lightning_rod", facing="up", powered=False, waterlogged=False))
CIBLES_SCI["observatoire"] = [OX + 1, YO + 1, OZ]
PASSAGES_DOC.append(("Observatoire", [(OX - 1, H_MUR + 1, OZ - 2), (OX, YO + 1, OZ)], "sur les toits, échelle contre un pilier"))

# --- 5. amenagement des zones
def pile(*b):
    return lambda f: list(b)


CHA = S("chain", axis="y", waterlogged=False)
BREW = S("brewing_stand", has_bottle_0=False, has_bottle_1=False, has_bottle_2=False)
FENCE = S("spruce_fence", east=False, north=False, south=False, west=False, waterlogged=False)


def cuve(c):
    return pile(S("sea_lantern"), S(f"{c}_stained_glass"), S(f"{c}_stained_glass"), WCU)


ZONES = {
    0: ("Laboratoire principal", [pile(SQ, BREW), pile(S("water_cauldron", level=3)), pile(SQ, S("candle", candles=3, lit=True, waterlogged=False)), cuve("cyan"),
                                  pile(S("lava_cauldron")), pile(SQ, S("end_rod", facing="up")), cuve("magenta"), pile(S("powder_snow_cauldron", level=3)),
                                  pile(WGR, CHA, CHA, CHA, CHA, CHA), pile(S("crafting_table")), pile(SQ, BREW), cuve("lime")]),
    1: ("Salle des expériences ratées", [pile(S("tinted_glass")), pile(S("sculk_catalyst", bloom=False)), pile(S("cobbled_deepslate"), S("cobweb")), pile(S("blackstone")),
                                         pile(BARS, BARS, BARS), pile(S("lime_stained_glass"), S("tinted_glass")), pile(S("basalt", axis="y"), S("basalt", axis="y")),
                                         pile(S("cracked_deepslate_bricks")), pile(S("sculk"), S("sculk_vein", down=True, east=False, north=False, south=False, up=False, west=False, waterlogged=False))]),
    2: ("Chaîne de production et atelier", [lambda f: [S("piston", facing="up", extended=False), S("redstone_lamp", lit=False)], lambda f: [S("observer", facing=f, powered=False)],
                                            pile(BULBE), pile(WGR, BULBE), pile(S("waxed_copper_block"), S("lightning_rod", facing="up", powered=False, waterlogged=False)),
                                            pile(S("redstone_lamp", lit=False), S("piston", facing="up", extended=False)), pile(CASING, S("create:cogwheel", axis="y"))]),
    3: ("Réserve", [pile(S("spruce_planks")), pile(S("spruce_planks"), S("spruce_planks")), pile(S("stripped_spruce_wood", axis="y"), S("spruce_planks"), S("stripped_spruce_wood", axis="y")),
                    pile(S("hay_block", axis="y")), pile(S("iron_block")), pile(S("spruce_planks"), S("stripped_spruce_wood", axis="y")), pile(S("hay_block", axis="y"), S("spruce_planks"))]),
    4: ("Bureau et bibliothèque", [pile(S("bookshelf"), S("bookshelf"), S("bookshelf")),
                                   lambda f: [S("chiseled_bookshelf", facing=f, slot_0_occupied=True, slot_1_occupied=False, slot_2_occupied=True, slot_3_occupied=True, slot_4_occupied=False, slot_5_occupied=True)] * 2,
                                   lambda f: [S("lectern", facing=f, has_book=False, powered=False)], pile(S("cartography_table")),
                                   pile(S("spruce_planks"), S("candle", candles=2, lit=True, waterlogged=False)), pile(S("bookshelf"), S("bookshelf"), S("lantern", hanging=False, waterlogged=False))]),
    5: ("Quartiers de vie", [lambda f: [S("smoker", facing=f, lit=False)], lambda f: [S("furnace", facing=f, lit=False)], lambda f: [S("campfire", facing=f, lit=True, signal_fire=False, waterlogged=False)],
                             pile(S("oak_planks"), S("flower_pot")), pile(S("water_cauldron", level=2)), pile(S("red_wool"), S("white_carpet")), pile(FENCE),
                             lambda f: [S("spruce_stairs", facing=f, half="bottom", shape="straight", waterlogged=False)], pile(SQ, S("potted_red_tulip")), pile(S("bookshelf"))]),
    6: ("Salle des machines", None),
    7: ("Serre", [pile(S("moss_block"), S("flowering_azalea")), pile(S("farmland", moisture=7), S("wheat", age=7)), pile(S("potted_fern")),
                  pile(S("oak_leaves", distance=1, persistent=True, waterlogged=False), S("oak_leaves", distance=1, persistent=True, waterlogged=False)), pile(S("composter", level=0)),
                  pile(S("moss_block"), S("potted_azure_bluet")), pile(S("farmland", moisture=7), S("carrots", age=7)), pile(S("potted_oak_sapling"))]),
}
ZONES[6] = ("Salle des machines", ZONES[2][1])


def cases_zone(k):
    out = []
    for x in range(SX):
        for z in range(SZ):
            d = dist(x, z)
            if not (17.6 <= d < 33.6) or salon(x, z) != k:
                continue
            if k in (2, 6) and 47 < x < 56:
                continue
            if (x, z) in occ or (x, z) in GARDE or not (vide((x, SOL + 1, z)) and vide((x, SOL + 2, z))) or not solide((x, SOL, z)):
                continue
            out.append((x, z))
    return out


def vers_mur(x, z, y=SOL + 1):
    for dx, dz, f in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
        q = (x + dx, y, z + dz)
        if not vide(q) and (q[0], q[2]) not in DECO and nom(q) not in ("ladder", "oak_trapdoor"):
            return f, (dx, dz)
    return None, None


def poser(x, z, pile_):
    for i, b in enumerate(pile_):
        put(x, SOL + 1 + i, z, b)
    occ[(x, z)] = "decor"
    DECO.add((x, z))


# generateur central de la salle des machines, dans la baie ouest
LIBRES6 = set(cases_zone(6))
GX, GZ = min(((x, z) for x, z in LIBRES6 if x < C and all((x + u, z + v) in LIBRES6 for u in (-1, 0, 1) for v in (-1, 0, 1))),
             key=lambda c: abs(dist(*c) - 28))
for x in range(GX - 1, GX + 2):
    for z in range(GZ - 1, GZ + 2):
        assert (x, z) not in occ and (x, z) not in GARDE and vide((x, SOL + 1, z)), ("generateur", x, z)
        centre = (x, z) == (GX, GZ)
        poser(x, z, [BULBE if centre else WCU, BULBE if centre else WGR, WCU, S("lightning_rod", facing="up", powered=False, waterlogged=False) if centre else CHA])
for k, (nomz, props) in ZONES.items():
    cases = cases_zone(k)
    n = 0
    for x, z in sorted(cases, key=lambda c: (ang(*c), dist(*c))):
        f, _ = vers_mur(x, z)
        if f is None or h01(x, k, z, 41) > 0.74 or (x, z) in DECO:
            continue
        poser(x, z, props[n % len(props)](f))
        n += 1
    ilots = 0
    for x, z in sorted(cases, key=lambda c: h01(c[0], 7, c[1], 42)):
        if ilots >= 6 or (x, z) in DECO or any((x + u, z + v) in DECO or (x + u, z + v) in GARDE for u in range(-2, 3) for v in range(-2, 3)):
            continue
        poser(x, z, props[(n + ilots) % len(props)]("north"))
        ilots += 1
    cx, cz = min(((x, z) for x, z in cases_zone(k)), key=lambda c: abs(dist(*c) - 25) + abs(adiff(ang(*c), 45 * k)) / 10)
    CIBLES_SCI[f"zone : {nomz}" + (" (baie ouest)" if k in (2, 6) and cx < C else "")] = [cx, SOL + 1, cz]
    ZONES_DOC.append((nomz, (cx, SOL + 1, cz), n + ilots))
for x in range(SX):                                    # sculk qui gagne le sol, briques fissurees, toiles d'araignee dans les coins hauts
    for z in range(SZ):
        d = dist(x, z)
        if not 16.5 <= d < 36:
            continue
        k = salon(x, z)
        if k == 1 and nom((x, SOL, z)) in ("stone_bricks", "smooth_stone") and bruit(x, z, 6, 43) > 0.55:
            put(x, SOL, z, S("sculk") if bruit(x, z, 3, 44) > 0.35 else S("blackstone"))
        for y in range(SOL + 1, H_MUR):
            if nom((x, y, z)) == "stone_bricks" and bruit(x + y, z, 4, 45) > 0.72:
                put(x, y, z, CSB)
        if k in (1, 3) and vide((x, SOL + 5, z)) and (x, z) not in GARDE:
            murs_ = sum(1 for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) if not vide((x + dx, SOL + 5, z + dz)))
            if murs_ >= 2 and h01(x, 5, z, 46) < 0.5:
                put(x, SOL + 5, z, S("cobweb"))
cage = None
for x, z in sorted(cases_zone(1), key=lambda c: h01(c[0], 1, c[1], 47)):
    if all((x + u, z + v) in set(cases_zone(1)) and (x + u, z + v) not in DECO for u in range(-2, 3) for v in range(-2, 3)):
        cage = (x, z)
        break
if cage:                                               # une cage de barreaux, le sculk s'en echappe
    x0, z0 = cage
    for u in range(-1, 2):
        for v in range(-1, 2):
            if (u, v) == (0, 0):
                poser(x0, z0, [S("sculk_catalyst", bloom=False)])
            else:
                poser(x0 + u, z0 + v, [BARS, BARS, BARS])
    for u in range(-2, 3):
        for v in range(-2, 3):
            put(x0 + u, SOL, z0 + v, S("blackstone") if max(abs(u), abs(v)) == 2 else S("sculk"))

# --- 6. habillage des pieges : chaque plaque devient une fuite ou un dispositif de securite
COUL_PIEGE = {"Faiblesse": "gray", "Lenteur": "light_blue", "Cécité": "black", "Nausée": "green", "Fatigue": "yellow", "Lévitation": "white", "Obscurité": "black",
              "Faim": "brown", "Repérage": "yellow", "Poison léger": "lime", "Pluie de flèches": "red", "Retour à la porte": "purple"}
HABILLAGE = []
for nomp, (x, y, z) in PIEGES:
    poses = 0
    for (u, v), b in (((1, 1), [S(f"{COUL_PIEGE[nomp]}_stained_glass"), S("tinted_glass")]), ((-1, -1), [WGR, CHA]), ((1, -1), [S(f"{COUL_PIEGE[nomp]}_stained_glass")])):
        if vide((x + u, SOL + 1, z + v)) and vide((x + u, SOL + 2, z + v)) and solide((x + u, SOL, z + v)) and (x + u, z + v) not in GARDE:
            for i, bb in enumerate(b):
                put(x + u, SOL + 1 + i, z + v, bb)
            poses += 1
    HABILLAGE.append((nomp, (x, y, z), poses))


# --- entites decoratives (fixes, invulnerables)
def cadre_ou_tableau(k, sorte, quoi, rang=0):
    vus = 0
    for x, z in sorted(cases_zone(k), key=lambda c: (abs(dist(*c) - 30), ang(*c))):
        if not vide((x, SOL + 2, z)):
            continue
        f, dd = vers_mur(x, z, SOL + 2)
        if f is None:
            continue
        dx, dz = dd
        if vus < rang:
            vus += 1
            continue
        base = {"pos": List[Double]([Double(x + .5), Double(SOL + 2.5), Double(z + .5)]), "blockPos": List[Int]([Int(x), Int(SOL + 2), Int(z)])}
        if sorte == "cadre":
            f3 = {(1, 0): 4, (-1, 0): 5, (0, 1): 2, (0, -1): 3}[(dx, dz)]
            nbt = Compound({"id": String("minecraft:item_frame"), "Facing": Byte(f3), "Item": Compound({"id": String(quoi), "count": Int(1)}), "Fixed": Byte(1), "Invulnerable": Byte(1)})
        else:
            f2 = {(1, 0): 1, (-1, 0): 3, (0, 1): 2, (0, -1): 0}[(dx, dz)]
            nbt = Compound({"id": String("minecraft:painting"), "facing": Byte(f2), "variant": String(quoi), "Invulnerable": Byte(1)})
        ENT.append(Compound({**base, "nbt": nbt}))
        ENT_DOC.append((("Cadre" if sorte == "cadre" else "Tableau") + f" ({quoi.split(':')[1]})", ZONES[k][0], (x, SOL + 2, z)))
        GARDE.add((x, z))
        return


def mannequin(x, y, z, yaw, mains, ou):
    ENT.append(Compound({"pos": List[Double]([Double(x + .5), Double(y), Double(z + .5)]), "blockPos": List[Int]([Int(x), Int(y), Int(z)]),
                         "nbt": Compound({"id": String("minecraft:armor_stand"), "Invulnerable": Byte(1), "DisabledSlots": Int(4144959), "ShowArms": Byte(1),
                                          "Rotation": List[Float]([Float(yaw), Float(0)]),
                                          "ArmorItems": List[Compound]([Compound({}), Compound({}), Compound({"id": String("minecraft:leather_chestplate"), "count": Int(1),
                                                                        "components": Compound({"minecraft:dyed_color": Compound({"rgb": Int(16777215)})})}), Compound({})]),
                                          "HandItems": List[Compound]([Compound({"id": String(mains), "count": Int(1)}) if mains else Compound({}), Compound({})])})}))
    ENT_DOC.append(("Mannequin en blouse blanche" + (f" ({mains.split(':')[1]} en main)" if mains else ""), ou, (x, y, z)))


cadre_ou_tableau(0, "cadre", "minecraft:experience_bottle")
cadre_ou_tableau(0, "cadre", "minecraft:glass_bottle", 3)
cadre_ou_tableau(1, "cadre", "minecraft:ender_eye")
cadre_ou_tableau(3, "cadre", "minecraft:map")
cadre_ou_tableau(4, "cadre", "minecraft:writable_book")
cadre_ou_tableau(4, "tableau", "minecraft:wasteland", 4)
cadre_ou_tableau(5, "cadre", "minecraft:clock")
cadre_ou_tableau(5, "tableau", "minecraft:kebab", 3)
cadre_ou_tableau(5, "tableau", "minecraft:aztec", 6)
cadre_ou_tableau(7, "cadre", "minecraft:wheat_seeds")
lx, lz = next((x, z) for x, z in sorted(cases_zone(0), key=lambda c: abs(dist(*c) - 22)) if all((x + u, z + v) not in DECO for u in (-1, 0, 1) for v in (-1, 0, 1)))
mannequin(lx, SOL + 1, lz, 90, None, "Laboratoire principal")
GARDE.add((lx, lz))
mannequin(OX + 1, YO + 1, OZ - 1, 180, "minecraft:spyglass", "Observatoire")
print("[4d] scientifique :", len(blocs), "decor", len(DECO), "entites", len(ENT), "passages", len(PASSAGES_DOC), flush=True)

# ====================================================================== 5. salles cachees (une trappe derriere une fausse dalle), apparitions, lumiere
for i in range(10):
    a = 9 + 36 * i
    for dr in (0, 1, -1, 2, -2):
        x, z = cellule(40 + dr, a)
        if (x, z) not in occ and vide((x, SOL + 1, z)) and solide((x, SOL, z)):
            break
    put(x, SOL, z, CHB)
    APPS.append((x, SOL + 1, z))

SOURCES = {"lantern": 15, "lava": 15, "light": 15}
TRANSP = {"air", "lantern", "light", "barrier", "lava", "stone_pressure_plate", "stone_button", "shaft", "cogwheel", "large_cogwheel", "chain", "iron_bars", "anvil", "stonecutter", "lectern", "cauldron",
          "skeleton_skull", "stone_brick_slab", "oak_slab", "flywheel", "mechanical_press", "mechanical_mixer", "fluid_tank", "steam_engine", "basin", "repeater", "belt", "creative_motor", "mechanical_bearing"}
SOURCES.update({"sea_lantern": 15, "end_rod": 14, "waxed_copper_bulb": 15, "campfire": 15, "candle": 9})
for s in set(blocs.values()):
    n = s[0].split(":")[1]
    if n != "tinted_glass" and (n.endswith(("glass", "_pane", "_carpet", "_trapdoor", "_stairs", "_fence", "candle", "cauldron", "_grate", "ladder", "_rod", "cobweb", "_vein", "_bars"))
                                or n.startswith("potted_") or n in ("brewing_stand", "flower_pot", "campfire", "wheat", "carrots", "flowering_azalea", "lectern", "composter", "chain", "skeleton_skull")):
        TRANSP.add(n)
SANS_SPAWN = TRANSP | {"gearbox"}


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
    return (p[1] > SOL) if n is None else n in ("air", "light")


total = 0
for passe in range(60):
    L = calcul()
    noires = sorted((x, y, z) for (x, y, z), s in blocs.items() if s[0].split(":")[1] not in SANS_SPAWN and y > 3 and y + 2 < SY and dist(x, z) < 50.5 and trav((x, y + 1, z)) and trav((x, y + 2, z)) and L[x, y + 1, z] == 0)
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
print("[5] lumieres", total, flush=True)

err = []
for p, s in blocs.items():
    x, y, z = p
    n = s[0].split(":")[1]
    if n != "air" and dist(x, z) > R + 1e-9:
        err.append(f"depasse {n} {p}")
    if n == "lava":
        for q in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1), (x, y - 1, z)):
            if nom(q) != "lava" and not solide(q):
                err.append(f"lave non contenue {p} -> {q} ({nom(q)})")
    if n == "stone_pressure_plate" and not solide((x, y - 1, z)):
        err.append(f"plaque sans support {p}")
    if n == "ladder":                                  # une echelle tombe si son appui est des barreaux, une chaine ou une barriere
        d = {"north": (0, 1), "south": (0, -1), "east": (-1, 0), "west": (1, 0)}[dict(s[1])["facing"]]
        q = (x + d[0], y, z + d[1])
        if not solide(q) or nom(q) in ("iron_bars", "chain", "ladder") or str(nom(q)).endswith(("_fence", "_trapdoor")):
            err.append(f"echelle sans appui {p} -> {nom(q)}")
    if n in ("chest", "barrel", "hopper", "dropper", "dispenser", "spawner", "water", "shulker_box") or n.endswith("_bed"):
        err.append(f"interdit {n} {p}")
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
racine = nbtlib.File({"DataVersion": Int(3955), "size": List[Int]([Int(SX), Int(SY), Int(SZ)]), "palette": List[Compound](palette), "blocks": List[Compound](liste), "entities": List[Compound](ENT)}, gzipped=True)
racine.save(RACINE / "exports" / f"{NOM}.nbt")
D = SAVE / "datapacks/atelier"
if (SAVE / "level.dat").exists():
    (D / "data/atelier/structure").mkdir(parents=True, exist_ok=True)
    racine.save(D / "data/atelier/structure" / f"{NOM}.nbt")
    if not (D / "pack.mcmeta").exists():
        (D / "pack.mcmeta").write_text('{"pack": {"pack_format": 48, "description": "Atelier [claude-build]"}}\n', encoding="utf-8")
else:
    print("ATTENTION : monde Arene Piege introuvable")
ci = {"grande salle": [C + 10, SOL + 1, C + 3], "dalle du piege mortel": [TX, SOL + 1, TZ], **{f"dalle mortelle {i + 2}": [x, SOL + 1, z] for i, (x, z) in enumerate(DALLES)}, "bouton de remise en etat": [BX + 1, SOL + 1, BZ],
      "tapis nord": [C, SOL + 1, C - 1 - 22], "tapis sud": [C, SOL + 1, C + 22], "refuge nord ouest": [XA - 3, SOL + 1, C - 1 - 21], "refuge nord est": [XB + 3, SOL + 1, C - 1 - 27],
      "refuge sud ouest": [XA - 3, SOL + 1, C + 27], "refuge sud est": [XB + 3, SOL + 1, C + 21], **CIBLES_SCI}
for k in range(8):
    x, z = cellule(25, 45 * k)
    ci[f"salle {k + 1}"] = [x, SOL + 1, z]
ci.update({f"piège : {n}": list(p) for n, p in PIEGES})
ci.update({f"apparition {i + 1}": list(p) for i, p in enumerate(APPS)})
gx2, gz2 = gl(46, 0, PORTE)
# retour depuis la roue : depart sur la roue, arrivee dans le laboratoire ; la corniche ne doit pas etre atteignable depuis la porte
(RACINE / "exports" / f"{NOM}_retour_cibles.json").write_text(json.dumps({"_marque": "[claude-build]", "sol": SOL, "depart": [C + 8, YW + 1, C],
    "cibles": {"corniche": [C + 14, YW + 1, C], "sortie du puits dans le laboratoire": [XP - 2, SOL + 1, C], "grande salle": [C + 10, SOL + 1, C + 3]}}, ensure_ascii=False, indent=1), encoding="utf-8")
(RACINE / "exports" / f"{NOM}_corniche_cibles.json").write_text(json.dumps({"_marque": "[claude-build]", "sol": SOL, "depart": [gl(46, 0, PORTE)[0], SOL + 1, gl(46, 0, PORTE)[1]],
    "cibles": {"corniche (ne doit PAS etre atteignable depuis la porte)": [C + 14, YW + 1, C]}}, ensure_ascii=False, indent=1), encoding="utf-8")
(RACINE / "exports" / f"{NOM}_cibles.json").write_text(json.dumps({"_marque": "[claude-build]", "sol": SOL, "depart": [gx2, SOL + 1, gz2], "cibles": ci}, ensure_ascii=False, indent=1), encoding="utf-8")


def w(p):
    return " ".join(str(v) for v in monde(*p))


md = ["<!-- [claude-build] genere par sources/piege.py -->", "# Arène piège — Le Laboratoire du Scientifique", "", "Monde `saves/Arene Piege`, centre X0 Z0, sol Y86, porte au nord.", "",
      "## Pièges non mortels", "", "Plaque de pression sur une dalle fissurée, bloc de commande caché dessous.", "", "| Effet | Plaque (X Y Z) |", "|---|---|"]
md += [f"| {n} | {w(p)} |" for n, p in PIEGES]
md += ["", "## Piège mortel", "", "- 5 dalles piégées dans la grande salle, autour du socle du trésor : " + " ; ".join(w((x, SOL + 1, z)) for x, z in [(TX, TZ)] + DALLES) + ".",
       "- Le plancher de la grande salle disparaît, et la chute finit dans la lave (fosse de 14 blocs).",
       f"- Remise en état : bouton à l'entrée, en {w((BX, SOL + 1, BZ))}.", "", "Apparitions : " + " ; ".join(w(p) for p in APPS), "",
       "- Remise en état automatique : le plancher et la plaque reviennent seuls environ 10 secondes après le déclenchement. Le bouton reste utilisable.", "", "## Usine", "", "- Roue dentée géante sous la voûte de la grande salle, sur un roulement Create.",
       "- Couloirs nord et sud : tapis roulant vers l'extérieur (96 tr/min), 3 marteaux-pilons chacun, refuges sur les côtés, frises d'engrenages.",
       "- Marteaux : cloche, puis descente environ 1 seconde plus tard. Le coup tue. Cycle de 6 secondes.",
       "- " + str(len(LIGNES)) + " presses mécaniques dans la cour, qui tournent à vide (plus de lingots, à la demande de l'utilisateur).", "",
       "## À savoir", "", "- Il faut NeoForge et Create pour ouvrir ce monde. Les moteurs créatifs font tourner les machines sans carburant.",
       "- Sur un serveur : `enable-command-block=true`.", "- Le fonctionnement de la redstone se vérifie en jeu."]

FR_ZONE = {"Laboratoire principal": "paillasses, alambics, chaudrons (eau, lave, poudreuse), cuves éclairées, tuyaux de cuivre et chaînes",
           "Salle des expériences ratées": "cage de barreaux, cuves brisées, sculk qui gagne le sol, blocs noircis, toiles, toit effondré",
           "Chaîne de production et atelier": "tapis, marteaux-pilons, pistons, lampes, ampoules de cuivre, grilles",
           "Réserve": "caisses empilées, bottes de foin, fer, escalier vers les toits",
           "Bureau et bibliothèque": "étagères, bibliothèques sculptées, pupitres, table de cartographie, balcon, passage secret",
           "Quartiers de vie": "faux lit défait, fumoir, four, feu de camp, table, chaudron de toilette",
           "Salle des machines": "générateur en cuivre, pistons, lampes, ampoules de cuivre, tapis et marteaux",
           "Serre": "verrière, mousse, azalées, cultures, plantes en pots, feuillage qui déborde"}
md += ["", "## Le lieu de vie du scientifique (2026-11-07)", "",
       "### Plateforme tournante", "",
       f"- Avant : la roue dentée géante tournait sous la voûte, roulement en {w(ROUE_AVANT)}, roue à Y{monde(0, ROUE_AVANT[1] - 1, 0)[1]}.",
       f"- Après : roue à Y{monde(0, YW, 0)[1]}, roulement en {w((C, YW - 1, C))}, moteur créatif en {w((C, YW - 2, C))} (1 tr/min), tous deux cachés dans le socle du trésor.",
       f"- Chute depuis le plancher de la grande salle : {SOL - YW} blocs, soit {SOL - YW - 3} points de dégâts ({str((SOL - YW - 3) / 2).replace('.', ',')} cœurs) sur 20. La lave reste {YW - 4} blocs plus bas.",
       "- Mécanisme : moteur créatif sous un roulement mécanique Create ; la roue est tenue par une colle Create (une entité). Aucune commande, aucune coordonnée absolue : la roue s'assemble seule quand le moteur tourne.",
       "", "### Voie de retour depuis la roue", ""]
nom_r, pts_r, txt_r = PASSAGES_DOC[0]
md += [f"- {txt_r}.", "- Tracé : " + " → ".join(w(p_) for p_ in pts_r) + ".", "- Seul accès : sauter du bord de la roue sur la corniche. La sortie est à 3 blocs du sol du laboratoire : impossible d'y remonter depuis la salle.",
       "", "### Zones", "", "| Zone | Point de repère (X Y Z) | Objets posés | Contenu |", "|---|---|---|---|"]
md += [f"| {z_[0]} | {w(z_[1])} | {z_[2]} | {FR_ZONE.get(z_[0], '')} |" for z_ in ZONES_DOC]
md += [f"| Observatoire | {w((OX, YO + 1, OZ))} | lunette, paratonnerre, mannequin | point haut sur les toits |",
       f"| Grande salle (cœur du réacteur) | {w((C + 10, SOL + 1, C + 3))} | trésor, passerelle au-dessus | piège mortel inchangé |",
       "", "### Passages ajoutés", ""]
md += [f"- {n_} : " + " → ".join(w(p_) for p_ in pts) + f" ({txt})." for n_, pts, txt in PASSAGES_DOC[1:]]
md += ["- Arches entre toutes les salles voisines ; le bureau et les quartiers de vie ne forment plus qu'un espace ; chaque salle a au moins deux accès.",
       "", "### Pièges après les travaux", "", "| Piège | Plaque (X Y Z) | Habillage | État |", "|---|---|---|---|"]
md += [f"| {n_} | {w(p_)} | {k_} élément(s) | mécanisme inchangé (commande relue dans le fichier) ; à essayer en jeu |" for n_, p_, k_ in HABILLAGE]
md += ["| 5 dalles mortelles et plancher qui se remet seul | voir plus haut | aucun | les commandes ne pendent plus sous le plancher : la chaîne enterrée de l'usine surveille les 5 dalles ; on tombe sur la roue ou dans la lave |",
       "| 6 marteaux-pilons, tapis | couloirs nord et sud | aucun | tuent maintenant (avant : 3 cœurs et une poussée), à la demande de l'utilisateur |",
       "", "### Entités décoratives (fixes, invulnérables)", ""]
md += [f"- {n_} : {o_}, en {w(p_)}" for n_, o_, p_ in ENT_DOC]
md += ["- Colle Create de la roue (invisible) : " + w((C, YW, C)), "",
       "### Écarts par rapport au prompt", "",
       "- Il n'y avait pas de plateforme tournante : c'est la roue dentée géante de l'usine qui a été descendue et sert de plateforme.",
       "- Les valeurs à compléter du prompt ont été prises dans le projet : Minecraft 1.21.1, centre X0 Z0, sol Y86, chute de 10 blocs.",
       "- Travaux faits par le générateur et un correctif à poser, pas par /fill ni /forceload. `doFireTick false` est ajouté à la chaîne de commandes de l'usine.",
       "- Pas de vrai lit : un lit fixerait le point de réapparition des joueurs dans l'arène et casserait le retour au hub. Le lit défait est en laine et tapis.",
       "- Aucun coffre ni conteneur ; les postes de travail sont vides.",
       "- Les pièges et la rotation sont vérifiés dans les fichiers, puis dans la sauvegarde après la pose. Le déclenchement de chaque piège en jeu reste à faire par un joueur.",
       "- Les deux conduits de ventilation obligent à ramper (fermer la trappe au-dessus de sa tête) : le contrôle d'accès à pied ne les compte pas.",
       "- Demandes ajoutées après la première pose : plus de lingots sur les presses, plus de blocs de commande sous le plancher de la grande salle, marteaux mortels.",
       "- L'arène dans le monde Arene Semaine et son zip ne sont pas encore mis à jour."]
(RACINE / "arene_piege.md").write_text("\n".join(md) + "\n", encoding="utf-8")

from PIL import Image, ImageDraw
COUL = {"stone_bricks": (125, 125, 125), "cracked_stone_bricks": (105, 105, 105), "chiseled_stone_bricks": (135, 135, 135), "smooth_stone": (165, 165, 165), "andesite": (135, 135, 130),
        "polished_andesite": (150, 150, 145), "gold_block": (245, 205, 60), "lava": (255, 110, 0), "andesite_casing": (170, 150, 120), "brass_casing": (200, 160, 80), "copper_casing": (190, 110, 80),
        "belt": (55, 55, 60), "creative_motor": (120, 70, 170), "mechanical_bearing": (170, 130, 70), "shaft": (140, 140, 150), "cogwheel": (150, 110, 70), "large_cogwheel": (150, 110, 70), "gearbox": (170, 150, 120), "stone_pressure_plate": (225, 80, 60), "stone_button": (60, 200, 90)}
CACHE = {"air", "light", "barrier", "lantern"}
s_ = 5
Wd, Hh = (SX + SZ) * s_ + 40, (SX + SZ) * s_ // 2 + SY * s_ + 60
img = Image.new("RGB", (Wd, Hh), (190, 170, 130))
dr = ImageDraw.Draw(img)


def ouvert(p):
    n = nom(p)
    return (p[1] > SOL) if n is None else n in CACHE


vis = [(p, s[0].split(":")[1]) for p, s in blocs.items() if s[0].split(":")[1] not in CACHE and p[1] >= SOL]
vis.sort(key=lambda t: (t[0][0] + t[0][2], t[0][1]))
for (x, y, z), n in vis:
    c = COUL.get(n, (160, 160, 160))
    px = (x - z) * s_ + SZ * s_ + 20
    py = (x + z) * s_ // 2 - y * s_ + SY * s_ + 20
    if ouvert((x, y + 1, z)):
        dr.polygon([(px, py), (px + s_, py + s_ // 2), (px, py + s_), (px - s_, py + s_ // 2)], fill=c)
    if ouvert((x + 1, y, z)):
        dr.polygon([(px + s_, py + s_ // 2), (px, py + s_), (px, py + 2 * s_), (px + s_, py + s_ + s_ // 2)], fill=tuple(int(v * .78) for v in c))
    if ouvert((x, y, z + 1)):
        dr.polygon([(px - s_, py + s_ // 2), (px, py + s_), (px, py + 2 * s_), (px - s_, py + s_ + s_ // 2)], fill=tuple(int(v * .62) for v in c))
img.save(RACINE / "exports" / f"{NOM}_vue.png")
print(f"Ecrit : {len(liste)} blocs, palette {len(palette)}, taille {SX}x{SY}x{SZ}, blocs avec donnees {len(NBT)}, entites {len(ENT)}")
print(f"POSE : /tp @s 0 110 0   puis   /place template atelier:{NOM} {ORIGINE[0]} {ORIGINE[1]} {ORIGINE[2]}")
