# [claude-build]
"""
abysses.py - Arene Les Abysses, 5e de la serie des 7, O104 : cite engloutie sous un dome ferme, le danger est de manquer d'air.
On respire dans les cloches (salles au sec ouvertes par le bas) et dans les colonnes de bulles. Conduites a ventilateurs Create.
Au fond de la fosse, le Coeur donne 20 s de respiration aquatique (recharge 60 s). Sol Y86, centre X0 Z0, porte au nord.
2026-11-08 (demande de l'utilisateur) : entree/sortie reconnaissable (lumiere chaude ocre reservee a la sortie : cadre de la porte,
sas, cadre du trou, ligne au plafond du dome, ascenseur de bulles sous le sas) ; tours, palais, cloches, ruines, arches, geysers
et siphons places et dimensionnes de facon irreguliere (plus de symetrie).
2026-11-08, 2e retouche (prompt de l'utilisateur) : portail monumental, escalier et salle d'entree octogonale voutee (famille du tuf,
lumiere chaude, lanternes, bougies, verre clair : reserves a la sortie), phare, chemins au sol ; chaque cloche, tour, palais, ruine,
arche et conduite a son dessin ; nervures du dome irregulieres ; palette elargie, melangee par plaques (style calme du projet).
  .venv\\Scripts\\python.exe sources\\abysses.py
Controles : sources\\verif_acces.py exports\\abysses.nbt exports\\abysses_cibles.json  puis  sources\\verif_air.py exports\\abysses.nbt
"""
import json
from collections import defaultdict
import math
import random
from pathlib import Path

import nbtlib
import numpy as np
from nbtlib import Byte, Compound, Int, List, String

RACINE = Path(__file__).resolve().parent.parent
SAVE = RACINE / "launcher/instances/Build-1.21.1/minecraft/saves/Arene Abysses"
NOM = "abysses"
SX = SZ = 104
SOL = 18
SY = SOL + 38
C = 52
CEN = 52.0
R = 52.0
RD = 47.5                                              # rayon interieur du dome
ORIGINE = (-52, 86 - SOL, -52)
rng = random.Random(5150)


def S(n, **p):
    n = n if ":" in n else "minecraft:" + n
    return (n, tuple(sorted((k, str(v).lower() if isinstance(v, bool) else str(v)) for k, v in p.items())))


AIR, EAU = S("air"), S("water", level=0)
PB, DP, PR = S("prismarine_bricks"), S("dark_prismarine"), S("prismarine")
VERRE, LANT = S("light_blue_stained_glass"), S("sea_lantern")
SABLE, GRAV, ROC = S("sand"), S("gravel"), S("cobbled_deepslate")
LUM = S("light", level=10, waterlogged=True)
OCRE = S("ochre_froglight", axis="y")                  # lumiere chaude : seulement autour de la sortie
# 2e retouche : signature de la sortie (reservee) et palette elargie
TB, TBC, TP, TC = S("tuff_bricks"), S("chiseled_tuff_bricks"), S("polished_tuff"), S("chiseled_tuff")
ESC = S("polished_tuff_stairs", facing="south", half="bottom", shape="straight", waterlogged=False)
VCLAIR, LANTERNE, BOUGIE = S("glass"), S("lantern", hanging=True, waterlogged=False), S("candle", candles=3, lit=True, waterlogged=False)
RESERVES = {"tuff_bricks", "chiseled_tuff_bricks", "polished_tuff", "chiseled_tuff", "polished_tuff_stairs", "ochre_froglight", "lantern", "candle", "glass"}
DT, DBK, DCR, DCH, DPO = S("deepslate_tiles"), S("deepslate_bricks"), S("cracked_deepslate_bricks"), S("chiseled_deepslate"), S("polished_deepslate")
MUD, TEINTE, SCULK, BOUE, LARMES = S("mud_bricks"), S("tinted_glass"), S("sculk"), S("mud"), S("crying_obsidian")
VERT, PERLE = S("verdant_froglight", axis="y"), S("pearlescent_froglight", axis="y")
SOUL_P = S("soul_lantern", hanging=True, waterlogged=False)
CHAINE_AIR, CHAINE_EAU = S("chain", axis="y", waterlogged=False), S("chain", axis="y", waterlogged=True)
LUMINEUX = {"sea_lantern": 15, "ochre_froglight": 15, "verdant_froglight": 15, "pearlescent_froglight": 15, "lantern": 15, "soul_lantern": 10,
            "crying_obsidian": 10, "glow_lichen": 7, "sea_pickle": 12, "candle": 9}
ANCIENNE_PALETTE = {"water", "prismarine_bricks", "light_blue_stained_glass", "dark_prismarine", "sand", "air", "kelp_plant", "gravel", "bubble_column",
                    "cobbled_deepslate", "kelp", "light", "seagrass", "ochre_froglight", "soul_sand", "sea_lantern", "creative_motor", "encased_fan",
                    "chain_command_block", "magma_block", "repeating_command_block"}
CUR = [None]                                           # structure en cours : sert a verifier qu'aucune n'est la copie d'une autre
STRUCTS = defaultdict(dict)
blocs = {}
NBT = {}
occ = {}
APPS, CLOCHES, TOURS, PALAIS, GEYSERS, SIPHONS, BULLES, CONDUITES, RUINES = [], [], [], [], [], [], [], [], []
TROUS = set()
PIT = {}


def put(x, y, z, s):
    if x != int(x) or y != int(y) or z != int(z):
        raise ValueError(f"coordonnee non entiere {(x, y, z)}")
    x, y, z = int(x), int(y), int(z)
    if not (0 <= x < SX and 0 <= y < SY and 0 <= z < SZ):
        raise ValueError(f"hors gabarit {(x, y, z)} {s[0]}")
    blocs[(x, y, z)] = s
    if CUR[0]:
        STRUCTS[CUR[0]][(x, y, z)] = s


def nom(p):
    s = blocs.get(p)
    return None if s is None else s[0].split(":")[1]


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


def plaque(x, y, z, choix, cel=5, g=40):
    """Choisit un bloc par grandes plaques (bruit lisse), jamais bloc par bloc : style calme du projet."""
    b = bruit(x + .6 * y, z - .4 * y, cel, g)
    for seuil, bloc in choix:
        if b < seuil:
            return bloc
    return choix[-1][1]


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


def enceinte(cases, bloc, sauf=()):
    """Entoure une salle d'air : toute case voisine qui n'est pas de l'air devient une paroi."""
    for x, y, z in cases:
        for q in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
            if q in cases or q in sauf or q in blocs or q[1] <= SOL or not (0 <= q[0] < SX and 0 <= q[2] < SZ):
                continue
            put(*q, bloc(q) if callable(bloc) else bloc)


def moteur(x, y, z, facing, rpm):
    put(x, y, z, S("create:creative_motor", facing=facing))
    NBT[(x, y, z)] = Compound({"id": String("create:creative_motor"), "ScrollValue": Int(rpm)})


def commande(x, y, z, cmd, sorte, facing):
    put(x, y, z, S(sorte, conditional=False, facing=facing))
    NBT[(x, y, z)] = Compound({"id": String("minecraft:command_block"), "Command": String(cmd), "auto": Byte(1), "TrackOutput": Byte(0),
                               "conditionMet": Byte(0), "powered": Byte(0), "UpdateLastExecution": Byte(1)})


# hauteur du plafond de chaque colonne
H = np.zeros((SX, SZ), dtype=int)
for x in range(SX):
    for z in range(SZ):
        d = dist(x, z)
        if d < RD:
            H[x, z] = SOL + 10 + int(24 * math.sqrt(max(0, 1 - (d / 48) ** 2)))


def dedans(x, y, z):
    return 0 <= x < SX and 0 <= z < SZ and H[x, z] > 0 and y < H[x, z] and y > PIT.get((x, z), SOL)


# ====================================================================== 1. fond, fosse, rempart
for x in range(SX):
    for z in range(SZ):
        d = dist(x, z)
        if d >= R:
            continue
        if d < RD:
            fond = GRAV if bruit(x, z, 15, 3) > 0.66 else SABLE
            m = bruit(x, z, 13, 41)
            if 17 < d < 27 and m > 0.6:
                fond = SCULK                           # plaques de sculk au bord de la fosse, la ou c'est le plus profond
            elif fond == SABLE and m < 0.2:
                fond = BOUE                            # plaques de vase
            put(x, SOL, z, fond)
        else:
            a = ang(x, z)
            for y in range(SOL, SOL + 15):
                put(x, y, z, DT if y <= SOL + 1 else plaque(x, y, z, [(0.3, PR), (1.01, PB)], 6, 42))
            put(x, SOL + 15, z, DP)
            if d >= 50.5 and int(a * 1.5) % 2 == 0:
                put(x, SOL + 16, z, DP)
for x in range(C - 21, C + 21):                        # fosse en gradins, bord irregulier
    for z in range(C - 21, C + 21):
        d = dist(x, z) + 3 * (bruit(x, z, 9, 5) - .5)
        if d < 17:
            PIT[(x, z)] = SOL - 3 * round(4 * (1 - (d / 17) ** 2) ** .8)
for (x, z), yf in list(PIT.items()):
    if yf == SOL:
        del PIT[(x, z)]
for (x, z), yf in PIT.items():
    for y in range(yf + 1, SOL + 1):
        blocs.pop((x, y, z), None)
    put(x, yf, z, SCULK if yf <= SOL - 9 and bruit(x, z, 5, 44) > 0.6 else plaque(x, yf, z, [(0.28, DT), (1.01, ROC)], 5, 43))
    for q in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
        for y in range(yf + 1, PIT.get(q, SOL) + 1):
            put(q[0], y, q[1], plaque(q[0], y, q[1], [(0.28, DT), (1.01, ROC)], 5, 43))
for x in range(C - 20, C + 20):
    for z in range(C - 20, C + 20):
        if dist(x, z) < 19:
            occ[(x, z)] = "fosse"
print("[1] fond :", len(blocs), "fosse", min(PIT.values()), flush=True)

# ====================================================================== 2. entree nord (2e retouche du 2026-11-08) : portail monumental, escalier, salle octogonale voutee
# Signature de la sortie, reservee a elle seule : famille du tuf (briques, briques sculptees, tuf poli, tuf sculpte, marches),
# froglight ocre (lumiere chaude), lanternes et bougies (flamme), verre clair. Qui voit ces blocs est a la sortie.
HXC, HZC, RS = 52.0, 17.0, 7.0                         # centre et demi-largeur de la salle d'entree ; le trou du plancher est au centre
YS = SOL + 5                                           # plancher de la salle : on y marche a YS + 1
TX0, TX1, TZ0, TZ1 = C - 2, C + 1, 15, 18              # le trou du plancher (4 x 4)
ZONE_SORTIE = {(x, z) for x in range(C - 10, C + 10) for z in range(0, 26)}


def dans_salle(x, z):
    dx, dz = abs(x + .5 - HXC), abs(z + .5 - HZC)
    return dx < RS and dz < RS and dx + dz < RS * 1.43


def voute(x, z):
    """Premier bloc plein au-dessus de l'air de la salle : 4 blocs d'air contre les murs, 8 au centre."""
    r = math.hypot(x + .5 - HXC, z + .5 - HZC) / RS
    return YS + 5 + round(4 * max(0.0, 1 - r * r))


def marche(z):
    """Dessus du sol du couloir : au niveau du dehors jusqu'a z 2, puis une marche par bloc jusqu'au plancher de la salle."""
    return SOL + min(5, max(0, z - 2))


def arc(x):
    """Premier bloc plein au-dessus de l'ouverture du portail (arc brise) : 14 au centre, 9 aux bords."""
    t = min(1.0, abs(x + .5 - C) / 6.5)
    return SOL + 7 + round(7 * (1 - t) ** 0.6)


def front(x):
    return next(z for z in range(0, 6) if dist(x, z) < R)


CUR[0] = "entree"
air = set()
for z in range(0, 10):                                 # couloir et escalier : 6 de large (x 49 a 54), 5 de haut au-dessus des marches
    f = marche(z)
    for x in range(C - 3, C + 3):
        if dist(x, z) >= R:
            continue
        for y in range(SOL, f):
            put(x, y, z, TB)
        put(x, f, z, ESC if 3 <= z <= 7 else TP)
        for y in range(f + 1, f + 6):
            air.add((x, y, z))
for x in range(C - 8, C + 8):                          # salle octogonale voutee
    for z in range(8, 26):
        if dans_salle(x, z):
            for y in range(YS + 1, voute(x, z)):
                air.add((x, y, z))
niche = set()
for x in range(C - 6, C + 6):                          # niche du portail, ouverte sur le dehors
    for z in (0, 1):
        if dist(x, z) < R:
            for y in range(SOL + 1, arc(x)):
                niche.add((x, y, z))
trou = {(x, YS, z) for x in range(TX0, TX1 + 1) for z in range(TZ0, TZ1 + 1)}
TROUS |= trou
for c in air | niche:
    put(*c, AIR)


def paroi_entree(q):
    x, y, z = q
    r = math.hypot(x + .5 - HXC, z + .5 - HZC)
    if dans_salle(x, z) and y <= YS:                   # plancher : tuf poli, anneau de tuf sculpte autour du trou
        return TC if 3.0 <= r < 4.3 else TP
    if dans_salle(x, z):                               # voute : briques de tuf, couronne de tuf poli
        return TP if 4.2 <= r < 5.3 else TB
    if z < 10 and y <= marche(z):
        return TB
    return TP if y == YS + 1 or (z < 10 and y == marche(z) + 1) else TB      # plinthe en tuf poli, murs en briques de tuf


enceinte(air, paroi_entree, trou)
for (x, y, z) in list(air):                            # dans le rempart, les murs et le plafond du couloir passent aussi au tuf
    for q in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z)):
        if q[2] <= 9 and q not in air and q not in niche and nom(q) in ("prismarine_bricks", "prismarine", "dark_prismarine", "deepslate_tiles"):
            put(*q, TP if q[1] == marche(q[2]) + 1 else TB)
n_pil = 0
MURS_SALLE = [(x, z) for x in range(C - 9, C + 9) for z in range(7, 27) if nom((x, YS + 2, z)) == "tuff_bricks"
              and any((q[0], YS + 2, q[1]) in air and dans_salle(*q) for q in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)))]
for th in (23.2, 66.8, 113.2, 156.8, 203.2, 246.8, 293.2, 336.8):     # pilastres aux angles de l'octogone, lumiere chaude a mi-hauteur
    def ecart_angle(m, th=th):
        return adiff(math.degrees(math.atan2(m[1] + .5 - HZC, m[0] + .5 - HXC)) % 360, th)
    px, pz = min(MURS_SALLE, key=ecart_angle)
    if ecart_angle((px, pz)) > 6 or nom((px, YS + 2, pz)) != "tuff_bricks":
        continue
    n_pil += 1
    for y in range(YS + 1, YS + 6):
        if nom((px, y, pz)) in ("tuff_bricks", "polished_tuff"):
            put(px, y, pz, OCRE if y == YS + 3 else TBC)
n_fen = 0
for x in range(C - 9, C + 9):                          # fenetres de verre clair, tournees vers l'arene
    for z in range(7, 27):
        a = math.degrees(math.atan2(z + .5 - HZC, x + .5 - HXC)) % 360
        if not (40 < a < 140 or 165 < a < 195 or a > 345 or a < 15):
            continue
        for y in (YS + 2, YS + 3):
            if nom((x, y, z)) == "tuff_bricks" and any(q in air for q in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1))):
                put(x, y, z, VCLAIR)
                n_fen += 1
for x in (C - 1, C):                                   # cle de voute lumineuse, puis phare : colonne lumineuse jusqu'au dome
    for z in (16, 17):
        put(x, voute(x, z), z, OCRE)
        for y in range(voute(x, z) + 1, H[x, z]):
            put(x, y, z, TBC if (y - YS) % 4 == 0 else OCRE)
for x, z in ((48, 13), (56, 19), (54, 12)):            # lanternes pendues a des chaines
    yv = voute(x, z)
    put(x, yv - 1, z, CHAINE_AIR)
    put(x, yv - 2, z, CHAINE_AIR)
    put(x, yv - 3, z, LANTERNE)
for x, z in ((46, 17), (58, 16), (49, 22), (55, 22), (47, 12)):        # bougies au pied des murs
    if (x, YS + 1, z) in air and nom((x, YS, z)) in ("polished_tuff", "chiseled_tuff"):
        put(x, YS + 1, z, BOUGIE)
for x in range(TX0 - 1, TX1 + 2):                      # cadre lumineux autour du trou, et le meme au fond de l'eau
    for z in range(TZ0 - 1, TZ1 + 2):
        if x in (TX0 - 1, TX1 + 1) or z in (TZ0 - 1, TZ1 + 1):
            put(x, YS, z, OCRE)
            put(x, SOL, z, OCRE)
for x in (TX0, TX0 + 1):                               # moitie ouest du trou : les bulles remontent a la salle ; moitie est : on plonge
    for z in range(TZ0, TZ1 + 1):
        put(x, SOL, z, S("soul_sand"))
        BULLES.append((x, SOL, z, False))
for x, z in ((46, 13), (57, 21), (48, 22)):            # 3 piliers sous la salle, bagues lumineuses
    for y in range(SOL + 1, YS):
        put(x, y, z, OCRE if y == SOL + 3 else TB)
for x, z in ((43, 22), (44, 22), (43, 23), (44, 23)):  # geyser a cote de la salle : de l'air jusqu'en haut du dome, cote nord
    put(x, SOL, z, S("soul_sand"))
    BULLES.append((x, SOL, z, False))
for x in range(C - 9, C + 9):                          # portail : 2 pylones (x 43-45 et 58-60), arc brise, fronton
    pyl = abs(x + .5 - C) > 6
    for z in range(0, 4 if pyl else 2):
        if dist(x, z) >= R:
            continue
        for y in range(SOL + 1, SOL + 22):
            if (x, y, z) in niche or (x, y, z) in air:
                continue
            if pyl:
                if y == SOL + 21:
                    b = OCRE if x in (C - 8, C + 7) and z == 1 else None      # couronne lumineuse
                elif y <= SOL + 2:
                    b = TP
                elif x in (C - 8, C + 7) and z == front(x) and y % 2 == 0 and y <= SOL + 18:
                    b = OCRE                           # filet lumineux sur la face des pylones
                else:
                    b = TBC if (y - SOL) % 5 == 0 or y == SOL + 20 else TB
            else:
                a = arc(x)
                if y == a:
                    b = OCRE if x in (C - 1, C) else TBC                      # claveaux, cle de l'arc lumineuse
                elif y <= SOL + 16:
                    b = TB
                elif y == SOL + 17:
                    b = TBC                            # frise au-dessus du rempart
                else:
                    b = None
            if b:
                put(x, y, z, b)
for x in range(C - 6, C + 6):                          # sol et fond de la niche
    for z in (0, 1):
        if dist(x, z) < R:
            put(x, SOL, z, TP)
    for y in range(SOL + 1, arc(x)):
        if (x, y, 2) not in air:
            put(x, y, 2, TB)
CUR[0] = None
for x in range(C - 10, C + 10):
    for z in range(0, 26):
        occ[(x, z)] = "sas"
for g in (0, 90, 180):                                 # place des conduites (posees au 5), retenue avant le reste
    for sp in range(-12, 10):
        for al in range(43, 48):
            occ[gl(al, sp, g)] = "conduite"
# chemins au sol : du bord de la fosse, du nord-est et de l'ouest jusqu'a la salle (tuf poli, clous de lumiere chaude)
CHEMINS = [[(52, 31), (51, 27), (52, 22)], [(70, 31), (64, 28), (58, 24), (55, 21)], [(33, 36), (40, 35), (45, 33), (46, 26), (49, 21)]]
n_chemin = 0
for pts in CHEMINS:
    cases_c, clous = set(), set()
    for (x0, z0), (x1, z1) in zip(pts, pts[1:]):
        n = 2 * max(abs(x1 - x0), abs(z1 - z0))
        for i in range(n + 1):
            px, pz = x0 + (x1 - x0) * i / n, z0 + (z1 - z0) * i / n
            for u in range(math.floor(px) - 1, math.floor(px) + 3):
                for v in range(math.floor(pz) - 1, math.floor(pz) + 3):
                    if math.hypot(u - px, v - pz) <= 1.0:
                        cases_c.add((u, v))
            if i % 8 == 0:
                clous.add((round(px), round(pz)))
    for u, v in sorted(cases_c):
        if (u, v) in PIT or not dedans(u, SOL + 1, v) or nom((u, SOL, v)) in ("ochre_froglight", "soul_sand") or occ.get((u, v)) not in (None, "sas", "chemin"):
            continue
        put(u, SOL, v, OCRE if (u, v) in clous else TP)
        occ[(u, v)] = "chemin"
        ZONE_SORTIE.add((u, v))
        n_chemin += 1
print("[2] entree :", len(blocs), "pilastres", n_pil, "fenetres", n_fen, "chemin", n_chemin, flush=True)

# ====================================================================== 3. dome : nervures irregulieres (7, largeurs et coupures differentes)
NERVURES = [(14, 1.0, None), (57, 1.4, (0.45, 0.62)), (101, 0.8, None), (148, 1.1, (0.2, 0.33)), (197, 1.2, None), (232, 0.9, (0.55, 0.8)), (318, 1.0, (0.3, 0.42))]


def nervure(a, d):
    return any(adiff(a, ra) * math.pi / 180 * max(d, 1) < lw and not (cp and cp[0] < d / RD < cp[1]) for ra, lw, cp in NERVURES)


for x in range(SX):
    for z in range(SZ):
        if dist(x, z) >= R:
            continue
        for y in range(SOL + 1, SY):
            if dedans(x, y, z) or (x, y, z) in blocs:
                continue
            if dedans(x, y - 1, z) or any(dedans(q[0], y, q[1]) for q in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1))):
                d, a = dist(x, z), ang(x, z)
                ligne = x in (C - 1, C) and z < C - 16 and y > SOL + 15     # ligne chaude au plafond, du centre vers la sortie
                put(x, y, z, OCRE if ligne else DP if nervure(a, d) or y <= SOL + 15 or d < 3 else VERRE)
ZONE_SORTIE |= {(x, z) for x in (C - 1, C) for z in range(0, C - 16)}
print("[3] dome :", len(blocs), flush=True)


# ====================================================================== 4. cloches, tours, palais : places conservees, chacun son dessin
rng_b = random.Random(2611)                            # 2026-11-08 : places et tailles irregulieres


def placer(r, a, rayon, quoi, dr=4.0, da=12.0):
    """Place libre la plus proche de (r, a) parmi des decalages tires au hasard ; rien ne deborde du dome."""
    cands = [(0.0, 0.0)] + [(rng_b.uniform(-dr, dr), rng_b.uniform(-da, da)) for _ in range(400)]
    cands.sort(key=lambda t: abs(t[0]) / dr + abs(t[1]) / da)
    for u, v in cands:
        x, z = cellule(r + u, a + v)
        if libre(x, z, rayon) and dist(x, z) + rayon < RD - 1:
            return x, z
    raise SystemExit(f"pas de place pour {quoi} vers r{r} a{a}")


def forme_dans(dx, dz, r, forme):
    if forme == "carree":
        return max(abs(dx), abs(dz)) < r
    if forme == "ovale":
        return (dx / (r * 1.25)) ** 2 + (dz / (r * .8)) ** 2 < 1
    if forme == "octo":
        return max(abs(dx), abs(dz)) < r and abs(dx) + abs(dz) < r * 1.35
    return math.hypot(dx, dz) < r


def cloche2(cx, cz, yf, forme="ronde", rayon=2.8, haut=3, coiffe=VERRE, sol_=DP, sol_r=None, lum=LANT, penche=(0, 0), anneau=None):
    """Salle d'air ouverte par le bas : l'eau ne monte pas, on respire au-dessus du trou. Forme, taille, coiffe, sol et lumiere au choix."""
    sol_r = sol_r or rayon + (1.0 if forme == "carree" else .8)
    plancher = set()
    for x in range(cx - 7, cx + 7):
        for z in range(cz - 7, cz + 7):
            if forme_dans(x + .5 - cx, z + .5 - cz, sol_r, forme):
                put(x, yf, z, sol_)
                plancher.add((x, z))
    cases = set()
    for k in range(1, haut + 1):
        r = rayon if k < haut else rayon * .72
        ox, oz = penche[0] * ((k - 1) // 2), penche[1] * ((k - 1) // 2)
        for x in range(cx - 6, cx + 6):
            for z in range(cz - 6, cz + 6):
                if forme_dans(x + .5 - cx - ox, z + .5 - cz - oz, r, forme):
                    cases.add((x, yf + k, z))
    for c in cases:
        put(*c, AIR)
    trou_ = {(x, yf, z) for x in (cx - 1, cx) for z in (cz - 1, cz)}
    for c in trou_:
        blocs.pop(c, None)
        plancher.discard((c[0], c[2]))
    TROUS.update(trou_)
    enceinte(cases, (lambda q: anneau if q[1] == yf + 2 else coiffe) if anneau else coiffe, trou_)
    ox, oz = penche[0] * ((haut - 1) // 2), penche[1] * ((haut - 1) // 2)
    tx, tz = cx + ox, cz + oz
    assert (tx, yf + haut, tz) in cases, ("sommet de cloche", cx, cz)
    if isinstance(lum, tuple) and lum[0] == "pendue":
        put(tx, yf + haut, tz, lum[1])
    else:
        put(tx, yf + haut + 1, tz, lum)
    return plancher, (tx, yf + haut + 1, tz)


def pieds(cx, cz, yf, plancher, sorte, m):
    """Pieds d'une cloche, chacune les siens : quatre, trois, deux murets, quatre dont deux casses, ou suspendue a des chaines."""
    def plus_loin(th):
        return max(plancher, key=lambda p: (p[0] + .5 - cx) * math.cos(math.radians(th)) + (p[1] + .5 - cz) * math.sin(math.radians(th)))
    if sorte == "quatre":
        pts = [(plus_loin(t), yf) for t in (45, 135, 225, 315)]
    elif sorte == "trois":
        pts = [(plus_loin(t), yf) for t in (20, 150, 265)]
    elif sorte == "murets":
        a, b = plus_loin(0), plus_loin(180)
        pts = [((a[0], a[1] + k), yf) for k in (-1, 0)] + [((b[0], b[1] + k), yf) for k in (-1, 0)]
    elif sorte == "casses":                            # deux pieds entiers, deux casses ; les morceaux sont tombes au pied
        pts = [(plus_loin(45), yf), (plus_loin(225), yf), (plus_loin(135), SOL + 2), (plus_loin(315), SOL + 3)]
        for (x, z) in (plus_loin(135), plus_loin(315)):
            for u, v in ((x + 1, z), (x, z + 1)):
                if (u, SOL + 1, v) not in blocs and dedans(u, SOL + 1, v):
                    put(u, SOL + 1, v, DCR)
    else:
        pts = []
    for (x, z), haut_ in pts:
        if (x, z) in plancher:
            for y in range(SOL + 1, haut_):
                if (x, y, z) not in blocs:
                    put(x, y, z, m)


# 4 tours creuses ; une colonne de bulles monte a la cloche du sommet. La tour ouest garde les 2 blocs de magma poses par l'utilisateur.
TOURS_DEF = [  # x0, z0, rayon, hauteur, forme, bloc, bloc des plaques, penche (au sommet), fentes, effondrement (hauteur, seuil), cloche du sommet, bulles
    (77, 55, 4.7, 16, "ronde", PB, PR, (0, 0), (7, 8, 12, 13), (3, 0.68), ("ronde", 2.8, VERRE, LANT), None),
    (45, 78, 5.3, 18, "octo", DBK, DCR, (0, 0), (5, 6, 10, 11, 15), (4, 0.6), ("carree", 2.2, TEINTE, VERT), None),
    (26, 55, 4.3, 13, "ronde", MUD, DBK, (0, 0), (), (6, 0.42), ("ovale", 2.2, VERRE, PERLE),
     [((25, 54), False), ((26, 55), False), ((26, 54), True), ((25, 55), True)]),
    (38, 27, 4.9, 15, "carree", DP, PB, (-1, -2), (8, 9), (2, 0.75), ("ronde", 2.5, TEINTE, ("pendue", SOUL_P)), None),
]
for i, (x0, z0, RE, HT, forme, m1, m2, pen, fentes, (hcasse, scasse), (fc, rc, cc, lc), bulles) in enumerate(TOURS_DEF):
    CUR[0] = f"tour {i + 1}"
    reserve(x0, z0, math.ceil(RE) + 1, "tour")
    YB = SOL + HT
    at = ang(x0, z0)
    for y in range(SOL + 1, YB):
        fr = (y - SOL) / HT
        ox, oz = round(pen[0] * fr), round(pen[1] * fr)
        for x in range(x0 - 8, x0 + 8):
            for z in range(z0 - 8, z0 + 8):
                dx, dz = x + .5 - x0 - ox, z + .5 - z0 - oz
                if not forme_dans(dx, dz, RE, forme) or forme_dans(dx, dz, RE - 1.3, forme):
                    continue
                a = math.degrees(math.atan2(dz, dx)) % 360
                porte = adiff(a, (at + 180) % 360) < 26 and y <= SOL + 3
                fente = (y - SOL) in fentes and any(adiff(a, at + b) < 9 for b in (45, 135, 225, 315))
                casse = y >= YB - hcasse and bruit(a + 37 * HT, y * .3, 40, 7) > scasse
                if not (porte or fente or casse):
                    put(x, y, z, (DCH if forme == "octo" else DP) if y in (SOL + 1, YB - 1) else plaque(x, y, z, [(0.4, m2), (1.01, m1)], 4, 60 + i))
    cloche2(x0, z0, YB, fc, rc, 3, cc, DP if i % 2 == 0 else DPO, sol_r=RE + .3, lum=lc)
    for (x, z), tire in (bulles or [((x0 - 1, z0 - 1), False), ((x0, z0 - 1), False), ((x0 - 1, z0), False), ((x0, z0), False)]):
        put(x, SOL, z, S("magma_block") if tire else S("soul_sand"))
        BULLES.append((x, SOL, z, tire))
    if hcasse >= 5:                                    # tour effondree : des blocs tombes au pied, cote exterieur
        for k in range(14):
            th = math.radians(at + rng_b.uniform(-70, 70))
            u, v = math.floor(x0 + .5 + (RE + rng_b.uniform(0.5, 3.0)) * math.cos(th)), math.floor(z0 + .5 + (RE + rng_b.uniform(0.5, 3.0)) * math.sin(th))
            if (u, SOL + 1, v) not in blocs and dedans(u, SOL + 1, v) and (u, v) not in occ:
                put(u, SOL + 1, v, m1 if k % 3 else DCR)
    TOURS.append((x0, z0, YB))
    CUR[0] = None


def palais2(i, cx, cz, hx, hz, ht, var, m1, m2, fen, lum, piliers):
    """Salle au sec sur pilotis, entree par le trou du plancher. Variantes : coin effondre (en L), toit incline, voute en berceau, haute a toit de verre."""
    def haut_(x, z):
        if var == "toit_incline":
            return ht + (x - cx + hx) // 3
        if var == "berceau":
            return ht + round(2 * math.cos((z - cz) / (hz + 1) * math.pi / 2))
        return ht
    cases = set()
    for x in range(cx - hx, cx + hx + 1):
        for z in range(cz - hz, cz + hz + 1):
            if var == "coin_effondre" and x > cx + 1 and z > cz + 1:
                continue
            for y in range(SOL + 6, SOL + 6 + haut_(x, z)):
                cases.add((x, y, z))
    trou_ = {(x, SOL + 5, z) for x in range(cx - 1, cx + 2) for z in range(cz - 1, cz + 2)}
    for c in cases:
        put(*c, AIR)
    toit = {(x, z): SOL + 6 + haut_(x, z) for x, _, z in cases}

    def paroi(q):
        x, y, z = q
        if y == SOL + 5:
            return plaque(x, y, z, [(0.35, m2), (1.01, m1)], 4, 70 + i)
        if (x, z) in toit and y >= toit[(x, z)]:
            if var == "haut":
                return fen if abs(x - cx) < hx and abs(z - cz) < hz else m1
            if var == "coin_effondre":
                return VERRE if abs(x - cx) <= 1 and abs(z - cz) <= 1 else m1
            return m2 if var == "berceau" else plaque(x, y, z, [(0.3, DCR), (1.01, m1)], 3, 80 + i)
        if var == "coin_effondre" and y == SOL + 8 and (x + z) % 3:
            return fen
        if var == "toit_incline" and y in (SOL + 7, SOL + 8) and (x + 2 * z) % 4 in (0, 1):
            return fen
        if var == "berceau" and SOL + 7 <= y <= SOL + 9 and (x - cx) % 4 in (1, 2):
            return fen
        if var == "haut" and SOL + 7 <= y <= SOL + 10 and (x + z) % 5 == 0:
            return fen
        return m1 if y > SOL + 6 else m2
    enceinte(cases, paroi, trou_)
    TROUS.update(trou_)
    if var == "coin_effondre":
        for x, z in ((cx - hx + 1, cz - hz + 1), (cx + hx - 1, cz - hz + 1), (cx - hx + 1, cz + hz - 1)):
            put(x, toit[(x, z)], z, lum)
        for u in range(cx + 2, cx + hx + 4):           # le coin tombe : des blocs au fond
            for v in range(cz + 2, cz + hz + 4):
                if bruit(u, v, 2, 90 + i) > .55 and (u, SOL + 1, v) not in blocs and dedans(u, SOL + 1, v) and (u, v) not in occ:
                    put(u, SOL + 1, v, DCR if (u + v) % 3 == 0 else m1)
    elif var == "toit_incline":
        for x, z in ((cx - 2, cz - 2), (cx + 2, cz + 1), (cx - 1, cz + 3)):
            put(x, toit[(x, z)] - 1, z, lum)
    elif var == "berceau":
        for x in (cx - 2, cx + 3):
            put(x, toit[(x, cz)], cz, lum)
    else:
        for x, z in ((cx - hx - 1, cz), (cx + hx + 1, cz), (cx, cz - hz - 1), (cx, cz + hz + 1)):
            put(x, SOL + 9, z, lum)
    if piliers == "grille":
        pts = [(u, v) for u in (-hx - 1, 0, hx + 1) for v in (-hz - 1, 0, hz + 1) if u or v]
    elif piliers == "coins":
        pts = [(-hx - 1, -hz - 1), (hx + 1, -hz - 1), (-hx - 1, hz + 1), (hx + 1, hz + 1)]
    elif piliers == "rangee":
        pts = [(u, 0) for u in (-hx - 1, -2, 2, hx + 1)] + [(0, -hz - 1), (0, hz + 1)]
    else:
        pts = [(u + a, v + b) for u, v in ((-hx - 1, -hz - 1), (hx, -hz - 1), (-hx - 1, hz)) for a in (0, 1) for b in (0, 1)]
    for u, v in pts:
        for y in range(SOL + 1, SOL + 5):
            if (cx + u, y, cz + v) not in blocs:
                put(cx + u, y, cz + v, m2)
    PALAIS.append((cx, cz))


PALAIS_DEF = [  # x, z, demi-largeurs, hauteur, variante, mur, plinthe et plancher, fenetres, lumiere, pilotis
    (66, 78, 5, 4, 5, "coin_effondre", PB, PR, VERRE, LANT, "grille"),
    (26, 70, 4, 5, 4, "toit_incline", DT, DPO, TEINTE, SOUL_P, "coins"),
    (24, 39, 5, 5, 5, "berceau", MUD, DBK, VERRE, VERT, "rangee"),
    (78, 39, 4, 4, 6, "haut", DP, PB, VERRE, PERLE, "piles"),
]
for i, (cx, cz, hx, hz, ht, var, m1, m2, fen, lum, pil) in enumerate(PALAIS_DEF):
    CUR[0] = f"palais {i + 1}"
    reserve(cx, cz, max(hx, hz) + 2.5, "palais")
    palais2(i, cx, cz, hx, hz, ht, var, m1, m2, fen, lum, pil)
    CUR[0] = None

# 10 cloches d'apparition : memes places (equitables), mais aucune n'a le dessin d'une autre
CLOCHES_DEF = [  # x, z, niveau, forme, rayon, hauteur, coiffe, sol, pieds, lumiere, penche, anneau
    (89, 53, 4, "ronde", 2.8, 3, VERRE, DP, ("quatre", DP), LANT, (0, 0), None),
    (78, 80, 3, "carree", 2.2, 3, TEINTE, DT, ("murets", DBK), VERT, (0, 0), None),
    (61, 90, 5, "ovale", 2.6, 3, VERRE, DPO, ("trois", DT), PERLE, (0, 0), None),
    (39, 90, 4, "ronde", 2.4, 4, VERRE, MUD, ("quatre", MUD), ("pendue", SOUL_P), (0, 0), DP),
    (25, 82, 3, "ronde", 2.9, 3, TEINTE, PB, ("casses", DP), LANT, (1, 0), None),
    (15, 51, 5, "carree", 2.7, 3, VERRE, DBK, ("trois", DBK), VERT, (0, 0), None),
    (22, 26, 4, "ronde", 3.1, 3, VERRE, DT, ("chaines", None), LANT, (0, 0), PR),
    (40, 16, 4, "ovale", 2.3, 3, VERRE, DPO, ("murets", PB), PERLE, (0, -1), None),
    (65, 15, 3, "ronde", 2.6, 4, TEINTE, DP, ("quatre", DBK), ("pendue", SOUL_P), (0, 0), None),
    (79, 27, 5, "carree", 2.4, 4, VERRE, PB, ("casses", PB), LANT, (-1, 1), DT),
]
for i, (x, z, hf, fo, ra, hc, coiffe, sol_, (pi_, pm), lum, pen, ann) in enumerate(CLOCHES_DEF):
    CUR[0] = f"cloche {i + 1}"
    reserve(x, z, 4, "cloche")
    plancher, sommet = cloche2(x, z, SOL + hf, fo, ra, hc, coiffe, sol_, lum=lum, penche=pen, anneau=ann)
    pieds(x, z, SOL + hf, plancher, pi_, pm)
    if pi_ == "chaines":                               # suspendue au dome par deux chaines
        for u in (sommet[0] - 2, sommet[0] + 1):
            if (u, sommet[1] - 1, sommet[2]) in blocs:
                for y in range(sommet[1], H[u, sommet[2]]):
                    if (u, y, sommet[2]) not in blocs:
                        put(u, y, sommet[2], CHAINE_EAU)
    put(x + 1, SOL + hf, z + 1, PB)
    assert nom((x + 1, SOL + hf + 1, z + 1)) == "air" and nom((x + 1, SOL + hf + 2, z + 1)) == "air", ("apparition bouchee", i + 1)
    APPS.append((x + 1, SOL + hf + 1, z + 1))
    CLOCHES.append((x, z))
    CUR[0] = None
print("[4] cloches, tours, palais :", len(blocs), flush=True)

# ====================================================================== 5. conduites : un tube, 9 ventilateurs Create, le courant tourne dans le sens des aiguilles
# Les ventilateurs, les moteurs et l'entree ne bougent pas ; chaque tube a sa matiere, ses fenetres et sa longueur (le courant porte 20 blocs).
SENS = {0: ("south", 9, PB, VERRE, 4), 90: ("west", 6, DT, TEINTE, 3), 180: ("north", 8, DP, VERRE, 5)}
for g, (face, fin, mat, fen, pas) in SENS.items():
    CUR[0] = f"conduite {g}"
    for sp in range(-12, fin + 1):
        for al in range(43, 48):
            x, z = gl(al, sp, g)
            occ[(x, z)] = "conduite"
            for y in range(SOL + 1, SOL + 6):
                bord = al in (43, 47) or y in (SOL + 1, SOL + 5)
                if sp <= -11 or bord:
                    entree = al == 43 and sp in (-9, -8, -7) and y in (SOL + 2, SOL + 3, SOL + 4)
                    if entree:
                        blocs.pop((x, y, z), None)
                    else:
                        put(x, y, z, fen if al == 43 and SOL + 2 <= y <= SOL + 4 and sp % pas else mat)
    for al in (44, 45, 46):
        for y in (SOL + 2, SOL + 3, SOL + 4):
            x, z = gl(al, -11, g)
            put(x, y, z, S("create:encased_fan", facing=face))
            x, z = gl(al, -12, g)
            moteur(x, y, z, face, 256)
    CONDUITES.append((g, gl(45, -8, g), gl(45, fin, g)))
    CUR[0] = None
print("[5] conduites :", len(blocs), flush=True)


# ====================================================================== 6. ruines, arches, rochers, vie marine
def ruine2(k, cx, cz, typ, m1, m2, hx, hz, hh):
    """Ruines, chacune sa forme : en U, mur penche, en L, mur tombe a plat, couloir, colonnes dont une couchee."""
    at = math.radians(ang(cx, cz))
    ux, uz = math.cos(at), math.sin(at)                # vers l'exterieur
    tx_, tz_ = -uz, ux                                 # le long du cercle

    def mur(x, z, ht, decal=None, cap=None):
        for y in range(SOL + 1, SOL + 1 + ht):
            u, v = x, z
            if decal:
                u, v = x + round(decal[0] * ((y - SOL - 1) // 3)), z + round(decal[1] * ((y - SOL - 1) // 3))
            if (u, v) in occ or (u, y, v) in blocs or not dedans(u, y, v):
                continue
            put(u, y, v, (cap or m2) if y == SOL + ht else plaque(u, y, v, [(0.4, m2), (1.01, m1)], 3, 100 + k))
    if typ == "u":                                     # trois murs, ouvert vers le centre
        for x in range(cx - hx, cx + hx + 1):
            for z in range(cz - hz, cz + hz + 1):
                if not (x in (cx - hx, cx + hx) or z in (cz - hz, cz + hz)):
                    continue
                if (x + .5 - cx) * ux + (z + .5 - cz) * uz < -min(hx, hz) + .6:
                    continue
                mur(x, z, 3 + int(hh * bruit(x + z + 7 * k, 0, 6, 9)))
    elif typ == "mur_penche":                          # un mur qui penche vers l'exterieur, des pierres tombees au pied
        for t in range(-hx, hx + 1):
            mur(round(cx + t * tx_), round(cz + t * tz_), hh - abs(t) // 2, decal=(ux, uz))
        for t in (-2, 0, 1):
            u, v = round(cx + t * tx_ - 2 * ux), round(cz + t * tz_ - 2 * uz)
            if (u, SOL + 1, v) not in blocs and (u, v) not in occ and dedans(u, SOL + 1, v):
                put(u, SOL + 1, v, DCR)
    elif typ == "l":                                   # deux murs bas en L
        for x in range(cx - hx, cx + hx + 1):
            mur(x, cz - hz, 2 + int(3 * bruit(x, cz, 3, 21 + k)))
        for z in range(cz - hz + 1, cz + hz + 1):
            mur(cx - hx, z, 2 + int(3 * bruit(cx, z, 3, 22 + k)))
    elif typ == "mur_tombe":                           # salle carree dont le mur exterieur est tombe a plat
        for x in range(cx - hx, cx + hx + 1):
            for z in range(cz - hz, cz + hz + 1):
                if not (x in (cx - hx, cx + hx) or z in (cz - hz, cz + hz)):
                    continue
                if (x + .5 - cx) * ux + (z + .5 - cz) * uz > max(hx, hz) - .6:
                    for s in range(1, 5):              # le mur couche au sol, vers l'exterieur
                        u, v = round(x + s * ux), round(z + s * uz)
                        if (u, SOL + 1, v) not in blocs and (u, v) not in occ and dedans(u, SOL + 1, v):
                            put(u, SOL + 1, v, m2 if s % 2 else m1)
                    continue
                mur(x, z, hh - int(2 * bruit(x, z, 2, 23 + k)))
        mur(cx, cz, hh + 1, cap=DCH)
    elif typ == "couloir":                             # deux murs paralleles de hauteurs differentes
        for t in range(-hx, hx + 1):
            for s, ht in ((-1.6, hh), (1.6, hh - 3)):
                mur(round(cx + t * tx_ + s * ux), round(cz + t * tz_ + s * uz), ht, cap=DCH)
    else:                                              # colonnes de hauteurs differentes, une couchee au sol
        for (a, b), ht in (((-2, -1), hh), ((1, 2), hh - 2), ((2, -2), hh - 4)):
            mur(cx + a, cz + b, ht, cap=DCH)
        for s in range(4):
            u, v = cx - 2 + s, cz + 2
            if (u, SOL + 1, v) not in blocs and (u, v) not in occ and dedans(u, SOL + 1, v):
                put(u, SOL + 1, v, m1)


RUINES_DEF = [  # place (fixe, ou cible a chercher), forme, blocs, demi-tailles, hauteur
    ((80, 68), "u", DBK, DCR, 4, 3, 7),
    ((90, 65), "mur_penche", PB, PR, 3, 1, 7),
    ((13, 65), "l", MUD, DBK, 3, 3, 4),
    ((49, 90), "mur_tombe", DT, DCR, 3, 3, 6),
    ((31, 125), "couloir", DP, PB, 3, 2, 6),
    ((34, 165), "colonnes", PB, DP, 3, 3, 7),
]
for k, (place, typ, m1, m2, hx, hz, hh) in enumerate(RUINES_DEF):
    if k < 4:                                          # places conservees
        cx, cz = place
    else:
        try:
            cx, cz = placer(place[0], place[1], max(hx, hz) + 1, "ruine", dr=7.0, da=35.0)
        except SystemExit:                             # place de secours : n'importe ou sur l'anneau
            for _ in range(1500):
                cx, cz = cellule(rng_b.uniform(21, 43), rng_b.uniform(0, 360))
                if libre(cx, cz, max(hx, hz) + 1) and dist(cx, cz) + max(hx, hz) + 1 < RD - 1:
                    break
            else:
                print("ruine sans place", place)
                continue
    CUR[0] = f"ruine {k + 1}"
    ruine2(k, cx, cz, typ, m1, m2, hx, hz, hh)
    reserve(cx, cz, max(hx, hz) + 1, "ruine")
    RUINES.append((cx, cz))
    CUR[0] = None
for n_a, (g, al0, sens, re_, m1, m2) in enumerate(((0, 31, -1, 6.0, PB, PR), (90, 35, 1, 5.2, DBK, DCR), (180, 33, 1, 5.6, MUD, DBK))):   # 3 arches brisees, chacune a sa facon
    CUR[0] = f"arche {n_a + 1}"
    for al in (al0, al0 + 1):
        for sp in range(-7, 7):
            for y in range(SOL + 1, SOL + 9):
                r_ = math.hypot(sp + .5, y - SOL - 1)
                casse = (sp + .5) * sens > re_ - 3.8 and y > SOL + re_ - 1.6
                if re_ - 1.6 <= r_ < re_ and not casse:
                    x, z = gl(al, sp, g)
                    if (x, z) not in occ:
                        put(x, y, z, plaque(x, y, z, [(0.35, m2), (1.01, m1)], 3, 110 + n_a))
    CUR[0] = None
GEYSERS_DEF = [(44, 12), (43, 49), (35, 84), (44, 123), (42, 160), (44, 197), (43, 233), (45, 307), (44, 339),       # pres du bord, pas devant la sortie
               (21, 17), (22, 63), (20, 109), (23, 151), (21, 197), (22, 243), (20, 286), (21, 330)]                 # autour de la fosse
TAILLES_G = [(2, 2), (1, 2), (2, 3), (2, 2), (3, 2), (1, 1), (2, 2), (2, 1), (3, 3), (2, 2), (1, 2), (2, 3), (2, 2), (1, 1), (3, 2), (2, 2), (2, 1)]
for i, ((r_, a), (gx, gz)) in enumerate(zip(GEYSERS_DEF, TAILLES_G)):     # geysers libres, de tailles differentes
    for da, dr in [(u, v) for u in (0, 5, -5, 10, -10, 15, -15, 20, -20, 25, -25, 30, -30) for v in (0, -1, 1, -2, 2, -3, -4)]:
        x, z = cellule(r_ + dr, a + da)
        if all((u, v) not in occ for u in range(x - 1, x + gx + 1) for v in range(z - 1, z + gz + 1)) and dist(x, z) < RD - 2 - max(gx, gz):
            break
    else:
        print("geyser sans place", r_, a)
        continue
    for u in range(x - 1, x + gx + 1):
        for v in range(z - 1, z + gz + 1):
            occ[(u, v)] = "geyser"
    for u in range(x, x + gx):
        for v in range(z, z + gz):
            put(u, SOL, v, S("soul_sand"))
            BULLES.append((u, SOL, v, False))
    GEYSERS.append((x, z))
n_roc = 0
for _ in range(1500):                                  # rochers aux formes naturelles (plus d essais : la place est plus morcelee)
    x, z = cellule(rng.uniform(21, 45), rng.uniform(0, 360))
    r_ = rng.uniform(1.8, 4.0)
    if n_roc >= 16 or not libre(x, z, r_ + 1):
        continue
    for u in range(x - 6, x + 7):
        for v in range(z - 6, z + 7):
            for y in range(SOL + 1, SOL + 7):
                e = math.hypot((u - x) / r_, (v - z) / r_, (y - SOL) / (r_ * .9))
                if e + .45 * (bruit(u + y, v, 3, 11) - .5) < 1 and dist(u, v) < RD - 1:
                    put(u, y, v, ROC)
    reserve(x, z, r_ + 1, "rocher")
    n_roc += 1
for r_, a in ((10, 24), (12, 113), (11, 206), (12.5, 297)):     # siphons : ils tirent vers le fond de la fosse
    x, z = cellule(r_, a)
    yf = PIT.get((x, z), SOL)
    put(x, yf, z, S("magma_block"))
    BULLES.append((x, yf, z, True))
    SIPHONS.append((x, yf, z))
YC = min(PIT.values())                                 # le Coeur, au fond
for x in range(C - 3, C + 3):
    for z in range(C - 3, C + 3):
        put(x, YC + 1, z, DP)
for x in (C - 1, C):
    for z in (C - 1, C):
        for y in (YC + 2, YC + 3):
            put(x, y, z, LANT)
CUR[0] = "coeur"
for (x, z), hp, m in (((C - 3, C - 3), 4, PB), ((C + 2, C - 3), 2, DBK), ((C - 3, C + 2), 5, PR), ((C + 2, C + 2), 1, DCR)):   # 4 piliers, aucun pareil
    for y in range(YC + 2, YC + 2 + hp):
        put(x, y, z, DCH if y == YC + 1 + hp and hp >= 4 else m)
for x, z in ((C + 1, C + 2), (C + 1, C + 1)):          # le haut du pilier casse est tombe sur la dalle
    put(x, YC + 2, z, DCR)
for x, z in ((C - 3, C), (C + 1, C - 3), (C + 2, C)):  # obsidienne pleureuse dans la dalle
    put(x, YC + 1, z, LARMES)
CUR[0] = None
CY, CZ, CX0 = 2, C, C - 4
CMD = []


def rel(x, y, z):
    px = CX0 + len(CMD)
    return f"~{x - px} ~{y - CY} ~{z - CZ}"


def si_coeur():
    return f"execute unless score #coeur abysses matches 1.. positioned {rel(C, YC + 3, C)} if entity @a[distance=..4,gamemode=!spectator]"


def fill_coeur(bloc):
    px = CX0 + len(CMD)
    return f"fill ~{C - 1 - px} ~{YC + 2 - CY} ~{C - 1 - CZ} ~{C - px} ~{YC + 3 - CY} ~{C - CZ} minecraft:{bloc}"


CMD.append("gamerule commandBlockOutput false")
CMD.append("gamerule doMobSpawning false")
CMD.append("scoreboard objectives add abysses dummy")
CMD.append("execute if score #coeur abysses matches 1.. run scoreboard players remove #coeur abysses 1")
CMD.append(f"execute if score #coeur abysses matches 1 run {fill_coeur('sea_lantern')}")
CMD.append(f"{si_coeur()} run effect give @a[distance=..4,gamemode=!spectator] minecraft:water_breathing 20 0")
CMD.append(f"{si_coeur()} run playsound minecraft:block.conduit.activate master @a[distance=..40] ~ ~ ~ 1 1")
CMD.append(f"{si_coeur()} run {fill_coeur('dark_prismarine')}")
CMD.append(f"{si_coeur()} run scoreboard players set #coeur abysses 1200")
for i, c in enumerate(CMD):
    commande(CX0 + i, CY, CZ, c, "repeating_command_block" if i == 0 else "chain_command_block", "east")
n_algue = 0
for x in range(SX):                                    # grandes prairies d'algues, pas de semis bloc par bloc
    for z in range(SZ):
        if not dedans(x, SOL + 1, z) or (x, z) in PIT or (x, SOL + 1, z) in blocs or (x, z) in occ or nom((x, SOL, z)) != "sand":
            continue
        b = bruit(x, z, 11, 12)
        if b > 0.66:
            ht = 3 + int(9 * bruit(x, z, 4, 13))
            for y in range(SOL + 1, SOL + 1 + ht):
                put(x, y, z, S("kelp", age=25) if y == SOL + ht else S("kelp_plant"))
            n_algue += 1
        elif b > 0.56:
            put(x, SOL + 1, z, S("seagrass"))
BCOL = {(x, z) for x, _, z, _ in BULLES} | {(x, z) for x, _, z in TROUS}
SUPPORTS = {"prismarine_bricks", "prismarine", "dark_prismarine", "deepslate_bricks", "cracked_deepslate_bricks", "mud_bricks", "deepslate_tiles",
            "cobbled_deepslate", "polished_deepslate"}
cands_l, cands_p = [], []                              # lichen luisant sur les murs, cornichons de mer sur les roches : repartis dans toute l'arene
for x in range(1, SX - 1):
    for z in range(1, SZ - 1):
        if (x, z) in BCOL or H[x, z] == 0 or occ.get((x, z)) in ("conduite", "sas", "chemin"):
            continue
        for y in range(PIT.get((x, z), SOL) + 1, min(SOL + 14, H[x, z] - 1)):
            if (x, y, z) in blocs or not dedans(x, y, z):
                continue
            if nom((x, y - 1, z)) == "cobbled_deepslate":
                cands_p.append((h01(x, y, z, 52), x, y, z))
            for q, face in (((x + 1, y, z), "east"), ((x - 1, y, z), "west"), ((x, y, z + 1), "south"), ((x, y, z - 1), "north")):
                if nom(q) in SUPPORTS:
                    cands_l.append((h01(x, y, z, 51), x, y, z, face))
                    break
pris_d = []


def eparpille(cands, n_max, ecart, poser):
    """Prend les candidats dans un ordre tire au hasard (fixe), en gardant un ecart minimal : pas de grappe, pas de motif."""
    n = 0
    for c in sorted(cands):
        x, y, z = c[1:4]
        if n >= n_max or (x, y, z) in blocs or any(abs(x - a) < ecart and abs(z - b) < ecart and abs(y - e) < 3 for a, e, b in pris_d):
            continue
        poser(c)
        pris_d.append((x, y, z))
        n += 1
    return n


n_pickle = eparpille(cands_p, 40, 6, lambda c: put(c[1], c[2], c[3], S("sea_pickle", pickles=3, waterlogged=True)))
n_lichen = eparpille(cands_l, 150, 4, lambda c: put(c[1], c[2], c[3], S("glow_lichen", waterlogged=True, **{c[4]: True})))
print("[6] lichen", n_lichen, "cornichons de mer", n_pickle, flush=True)
print("[6] decor :", len(blocs), "ruines", len(RUINES), "rochers", n_roc, "geysers", len(GEYSERS), "algues", n_algue, flush=True)

# ====================================================================== 7. eau, bulles, lumiere
n_eau = 0
for x in range(SX):
    for z in range(SZ):
        if H[x, z] == 0:
            continue
        for y in range(PIT.get((x, z), SOL) + 1, H[x, z]):
            if (x, y, z) not in blocs:
                put(x, y, z, EAU)
                n_eau += 1
for x, y0, z, tire in BULLES:
    y = y0 + 1
    while nom((x, y, z)) in ("water", "kelp", "kelp_plant", "seagrass"):
        put(x, y, z, S("bubble_column", drag=tire))
        y += 1
print("[7] eau :", n_eau, flush=True)

LIQ = {"water", "kelp", "kelp_plant", "seagrass", "bubble_column", "light", "sea_pickle"}
TRANSP = LIQ | {"air", "light_blue_stained_glass", "creative_motor", "encased_fan", "glass", "chain", "glow_lichen", "candle", "lantern", "soul_lantern",
                "polished_tuff_stairs"}


def liquide(p):
    s = blocs.get(p)
    return s is not None and (s[0].split(":")[1] in LIQ or ("waterlogged", "true") in s[1])

surfaces = []
for (x, y, z), s in blocs.items():
    n = s[0].split(":")[1]
    if n not in TRANSP and y >= SOL - 1 and nom((x, y + 1, z)) == "water" and nom((x, y + 2, z)) == "water":
        surfaces.append((x, y, z))
surfaces.sort()
op = np.zeros((SX, SY, SZ), dtype=bool)
op[:, :SOL + 1, :] = True
src = np.zeros((SX, SY, SZ), dtype=np.int8)
for (x, y, z), s in blocs.items():
    n = s[0].split(":")[1]
    op[x, y, z] = n not in TRANSP and n not in LUMINEUX
    if n in LUMINEUX:
        src[x, y, z] = LUMINEUX[n]


def calcul():
    L = src.copy()
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


total = 0
for passe in range(40):                                # penombre voulue : la lumiere reste basse, et rien au fond de la fosse
    L = calcul()
    noires = [p for p in surfaces if L[p[0], p[1] + 1, p[2]] < 4 and p[1] >= SOL]
    if not noires:
        break
    pris = []
    for x, y, z in noires:
        if any(abs(x - a) < 9 and abs(z - b) < 9 and abs(y - c) < 5 for a, c, b in pris):
            continue
        pris.append((x, y, z))
        put(x, y + 1, z, LUM)
        src[x, y + 1, z] = 10
        total += 1
print("[7] lumieres", total, flush=True)

# ====================================================================== 8. controles : etancheite, bulles, emprise
err = []
for p, s in blocs.items():
    x, y, z = p
    n = s[0].split(":")[1]
    if n != "air" and dist(x, z) > R + 1e-9:
        err.append(f"depasse {n} {p}")
    if liquide(p):
        for q in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1), (x, y - 1, z)):
            m = nom(q)
            if m == "air" or (m is None and q[1] > SOL):
                err.append(f"fuite {p} -> {q} ({m})")
    if n == "air":
        for q in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1), (x, y + 1, z)):
            if liquide(q):
                err.append(f"air touche l'eau {p} -> {q}")
    if n in ("chest", "barrel", "hopper", "dropper", "dispenser", "spawner"):
        err.append(f"interdit {n} {p}")
for p in TROUS:
    if not liquide(p):
        err.append(f"trou bouche {p} {nom(p)}")
for p, s in blocs.items():                             # signature de la sortie : ses blocs ne se trouvent nulle part ailleurs
    if s[0].split(":")[1] in RESERVES and (p[0], p[2]) not in ZONE_SORTIE:
        err.append(f"bloc reserve a la sortie hors de la sortie {s[0]} {p}")


def empreinte(cases_):
    """Forme d'une structure, la meme quelle que soit sa place, son orientation ou son sens (miroir)."""
    formes = []
    for k in range(8):
        pts = []
        for (x, y, z), s in cases_.items():
            a, b = (x, z) if k < 4 else (z, x)
            pts.append((-a if k % 2 else a, y, -b if (k // 2) % 2 else b, s[0]))
        ma, my, mb = min(q[0] for q in pts), min(q[1] for q in pts), min(q[2] for q in pts)
        formes.append(tuple(sorted((a - ma, y - my, b - mb, n) for a, y, b, n in pts)))
    return min(formes)


vus = {}
for nom_s, cases_ in sorted(STRUCTS.items()):          # aucune structure n'est la copie d'une autre
    if nom_s.split()[0] in ("cloche", "tour", "palais", "ruine", "arche", "conduite"):
        e = empreinte(cases_)
        if e in vus:
            err.append(f"{nom_s} est la copie de {vus[e]}")
        vus[e] = nom_s
noms_pal = {s[0].split(":")[-1] for s in blocs.values()}
NOUVEAUX = sorted(noms_pal - ANCIENNE_PALETTE)
quarts = defaultdict(set)
for (x, y, z), s in blocs.items():
    n = s[0].split(":")[-1]
    if n in NOUVEAUX and n not in RESERVES:
        quarts[n].add((x >= C, z >= C))
hors_sortie = [n for n in NOUVEAUX if n not in RESERVES]
if len(hors_sortie) < 8:
    err.append(f"moins de 8 nouveaux blocs hors de la sortie : {hors_sortie}")
if len(set().union(*quarts.values())) < 4:
    err.append("nouveaux blocs absents d'un quart de l'arene")
print("[controle] structures comparees", len(vus), "| nouveaux blocs", len(NOUVEAUX), {n: len(quarts[n]) for n in hors_sortie}, flush=True)
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
    print("ATTENTION : monde Arene Abysses introuvable")

ci = {"salle d'entree": [55, YS + 1, 13], "sous la salle": [C, SOL + 1, 21], "coeur": [C + 3, YC + 2, C + 3]}
ci.update({f"apparition {i + 1}": list(p) for i, p in enumerate(APPS)})
ci.update({f"palais {i + 1}": [x + 1, SOL + 6, z + 1] for i, (x, z) in enumerate(PALAIS)})
ci.update({f"sommet de tour {i + 1}": [x + 1, yb + 1, z] for i, (x, z, yb) in enumerate(TOURS)})
for g, (ax, az), (bx, bz) in CONDUITES:
    ci[f"conduite {g} entree"] = [ax, SOL + 2, az]
    ci[f"conduite {g} sortie"] = [bx, SOL + 2, bz]
(RACINE / "exports" / f"{NOM}_cibles.json").write_text(json.dumps({"_marque": "[claude-build]", "sol": SOL, "depart": [C, SOL + 1, 1], "cibles": ci}, ensure_ascii=False, indent=1), encoding="utf-8")


def w(p):
    return " ".join(str(v) for v in monde(*p))


cpt = defaultdict(int)
for s in blocs.values():
    cpt[s[0].split(":")[-1]] += 1


def ws(p):                                             # meme point dans le monde final Arene Semaine (centre X0 Z256)
    x, y, z = monde(*p)
    return f"{x} {y} {z + 256}"


OU = {
    "deepslate_tiles": "socle du rempart, plaques des gradins de la fosse, sols des cloches 2 et 7, anneau de la cloche 10, murs du palais au toit incliné, conduite sud, ruine au mur tombé",
    "deepslate_bricks": "tour sud (octogone), plaques de la tour ouest, palais en berceau (plinthe, plancher, voûte, pilotis), pieds des cloches 2, 6 et 9, sol de la cloche 6, ruine en U, ruine en L, arches sud et ouest, un pilier du Cœur",
    "cracked_deepslate_bricks": "plaques de la tour sud, morceaux tombés au pied des cloches 5 et 10, toit du palais incliné, coin effondré du palais nord-est, ruines en U, au mur tombé et au mur penché, arche sud, pilier cassé du Cœur",
    "chiseled_deepslate": "bandeaux de la tour sud, chapiteaux des ruines (mur tombé, couloir, colonnes), sommet de 2 piliers du Cœur",
    "polished_deepslate": "sols des cloches 3 et 8, sols des cloches au sommet des tours sud et nord-ouest, plinthe, plancher et pilotis du palais au toit incliné",
    "mud_bricks": "tour ouest (effondrée), cloche 4, palais en berceau, ruine en L, arche ouest",
    "mud": "plaques de vase dans le sable, dans toute l'arène",
    "sculk": "plaques au bord de la fosse et au fond des gradins",
    "prismarine": "plaques du rempart, de la tour est, du palais au coin effondré, de la ruine au mur penché et de l'arche est ; anneau de la cloche 7 ; un pilier du Cœur",
    "tinted_glass": "coiffes des cloches 2, 5 et 9 et des cloches au sommet des tours sud et nord-ouest, fenêtres du palais au toit incliné et de la conduite sud",
    "verdant_froglight": "lumière des cloches 2 et 6, de la cloche de la tour sud et de la voûte du palais en berceau",
    "pearlescent_froglight": "lumière des cloches 3 et 8, de la cloche de la tour ouest et du palais haut",
    "soul_lantern": "lanternes pendues dans les cloches 4 et 9, la cloche de la tour nord-ouest et le palais au toit incliné",
    "chain": "chaînes de la cloche 7 (suspendue au dôme) et des lanternes de la salle d'entrée",
    "crying_obsidian": "dalle du Cœur",
    "glow_lichen": "sur les murs des ruines, des tours, des palais et sur les rochers, dans toute l'arène",
    "sea_pickle": "sur les rochers et les gradins de la fosse",
}
NOMS_FR = {"tuff_bricks": "briques de tuf", "chiseled_tuff_bricks": "briques de tuf sculptées", "polished_tuff": "tuf poli", "chiseled_tuff": "tuf sculpté",
           "polished_tuff_stairs": "marches de tuf poli", "ochre_froglight": "froglight ocre", "lantern": "lanternes", "candle": "bougies", "glass": "verre clair"}
md = ["<!-- [claude-build] genere par sources/abysses.py -->", "# Arène Les Abysses", "",
      "Monde `saves/Arene Abysses` (centre X0 Z0) et monde final `saves/Arene Semaine` (centre X0 Z256 : coordonnées entre parenthèses). "
      "Sol Y86, porte au nord. Le danger : manquer d'air. Minecraft 1.21.1 (NeoForge, Create).", "",
      "## Entrée et sortie", "",
      f"- Portail : de {w((C - 9, SOL, 0))} à {w((C + 8, SOL + 21, 3))} ({ws((C - 9, SOL, 0))} à {ws((C + 8, SOL + 21, 3))}). Ouverture en arc brisé de 12 blocs de large "
      f"et 13 de haut, deux pylônes de 21 blocs couronnés de lumière chaude, un filet lumineux sur chaque pylône, clé de l'arc lumineuse. C'est de loin la plus grande ouverture de l'arène (les arches font au plus 12 × 6).",
      f"- Escalier de tuf poli, 6 de large, puis la salle d'entrée : centre {w((C, YS + 1, 17))} ({ws((C, YS + 1, 17))}).",
      f"- Trou du plancher : de {w((TX0, YS, TZ0))} à {w((TX1, YS, TZ1))} ({ws((TX0, YS, TZ0))} à {ws((TX1, YS, TZ1))}). Moitié ouest : colonne de bulles qui remonte à la salle (on sort sans nager). Moitié est : on plonge.",
      f"- Vus de l'intérieur : le phare, colonne lumineuse du toit de la salle jusqu'au dôme ({w((C - 1, YS + 10, 16))} et au-dessus) ; la ligne de lumière chaude au plafond du dôme, du centre vers la sortie ; "
      f"3 piliers à bague lumineuse sous la salle ; un cadre lumineux au fond de l'eau sous le trou ; un geyser à côté de la salle.",
      f"- Chemins au sol : {len(CHEMINS)} chemins de tuf poli à clous de lumière chaude ({n_chemin} blocs), depuis le bord nord de la fosse ({w((52, SOL, 31))}), le nord-est ({w((70, SOL, 31))}) "
      f"et l'ouest ({w((33, SOL, 36))}) jusque sous la salle.",
      "- Blocs réservés à la sortie (on ne les trouve nulle part ailleurs, le générateur le vérifie) : " + ", ".join(NOMS_FR[n] for n in NOMS_FR)
      + ". La froglight ocre donne la seule lumière chaude de l'arène ; les lanternes et les bougies, les seules flammes.", "",
      "## Pièce d'entrée", "",
      f"- Seule salle octogonale et voûtée : 14 blocs de large, de 4 blocs de haut contre les murs à 8 au centre. Les palais sont rectangulaires ou en L (9 à 11 de large, plafond plat, en pente ou en berceau), "
      "les cloches font 4 à 7 de large et 3 à 4 de haut.",
      f"- Murs en briques de tuf sur une plinthe de tuf poli, {n_pil} pilastres de briques de tuf sculptées avec une lumière chaude, {n_fen} vitres de verre clair tournées vers l'arène, "
      "sol de tuf poli avec un anneau de tuf sculpté et un cadre lumineux autour du trou, couronne de tuf poli dans la voûte, clé de voûte lumineuse, 3 lanternes pendues à des chaînes, 5 groupes de bougies.",
      "- L'ancien sas (rectangle de 12 × 10, 5 de haut, en prismarine sombre) ressemblait aux palais : c'est lui qui a changé.", "",
      f"## Nouveaux blocs ({len(NOUVEAUX)}) et où", ""]
md += [f"- {n} ({cpt[n]}) : {'sortie (réservé)' if n in RESERVES else OU.get(n, '')}" for n in NOUVEAUX]
md += ["", "Les surfaces mélangent leurs blocs par grandes plaques (bruit lisse), jamais bloc par bloc : c'est le style calme du projet, demandé après la remarque « ça fait trop IA ».", "",
       "## Symétries trouvées et comment elles sont cassées", "",
       "- 10 cloches identiques → 10 dessins : ronde, carrée ou ovale, de 2,2 à 3,1 de rayon, 3 ou 4 de haut, coiffe claire, teintée ou à anneau, sol, pieds (4, 3, deux murets, deux pieds cassés, suspendue au dôme par des chaînes), lumière ; 2 penchées. Places et hauteurs gardées : les apparitions restent équitables.",
       "- 4 tours sur le même modèle → ronde à fentes (est), octogonale à bandeaux (sud), ronde effondrée avec ses blocs tombés au pied (ouest), carrée qui penche vers l'extérieur (nord-ouest) ; 13 à 18 de haut ; chaque cloche du sommet a sa forme, sa coiffe et sa lumière.",
       "- 4 palais sur le même modèle → en L au coin effondré (blocs tombés au fond), toit incliné, voûte en berceau, palais haut à toit de verre ; matières, fenêtres, lumières et pilotis différents.",
       f"- Ruines tirées d'un seul modèle → {len(RUINES)} formes : en U, mur penché, en L, salle au mur tombé à plat, couloir de deux murs, colonnes dont une couchée. Deux ruines qui gênaient la sortie ont été déplacées.",
       "- 3 conduites identiques à 90° → prismarine (22 de long, fenêtres tous les 4), ardoise des abîmes en tuiles (19, verre teinté tous les 3), prismarine sombre (21, fenêtres tous les 5). Ventilateurs, moteurs et entrées inchangés.",
       "- Dôme à 8 nervures régulières (étoile) → 7 nervures à angles irréguliers, de largeurs différentes, 4 interrompues.",
       "- Cœur à 4 piliers identiques → 4 hauteurs et matières, un pilier cassé dont le haut est tombé sur la dalle, obsidienne pleureuse dans la dalle. Lanternes et commandes du Cœur inchangées.",
       f"- Geysers tous en 2 × 2 → de 1 × 1 à 3 × 3 ({len(GEYSERS)} geysers, plus celui de la sortie).",
       "- 3 arches du même modèle → rayons et côtés cassés différents (1re retouche), matières différentes.",
       f"- Le générateur compare les {len(vus)} structures (cloches, tours, palais, ruines, arches, conduites), même tournées ou en miroir : aucune n'est la copie d'une autre.", "",
       "## Où respirer", "", "- 10 cloches d'apparition, 4 palais au sec, la salle d'entrée et 4 sommets de tour : on y entre par un trou dans le plancher.",
       f"- {len(GEYSERS) + 1} geysers et les colonnes des tours : on reprend de l'air dans les bulles, qui font aussi monter. La tour ouest a 2 colonnes qui tirent vers le bas (magma posé par l'utilisateur, gardé).",
       f"- {len(SIPHONS)} siphons dans la fosse : on y respire aussi, mais ils tirent vers le fond, sur du magma.",
       "- Contrôle : partout dans l'eau, l'air est à 28 blocs de nage au plus (`sources/verif_air.py`).", "",
       "## Le Cœur", "", f"Au fond de la fosse, en {w((C, YC + 3, C))} ({ws((C, YC + 3, C))}). S'en approcher donne 20 secondes de respiration aquatique. Il s'éteint puis se rallume après 60 secondes.", "",
       "## Conduites", "", "3 tubes à l'est, au sud et à l'ouest, avec 9 ventilateurs Create chacun. Entrée sur le côté, près des ventilateurs ; le courant tourne dans le sens des aiguilles d'une montre.", "",
       "Apparitions : " + " ; ".join(w(p) for p in APPS), "",
       "## Résumé et écarts au prompt (2026-11-08)", "",
       "- Fait : portail monumental et signature de la sortie, salle d'entrée refaite, phare, chemins au sol ; un dessin propre à chaque structure ; palette élargie dans toute l'arène. Contrôles du générateur : aucune fuite d'eau, aucune poche d'air de trop, aucun trou bouché, blocs réservés seulement à la sortie, aucune copie.",
       "- Inchangés : places des cloches, tours et palais (apparitions équitables, chemins praticables conservés), fosse, dôme (sauf nervures), conduites (sauf habillage et longueur), mécanismes (Cœur, ventilateurs, colonnes de bulles).",
       "- Écart : les blocs sont mélangés par plaques, pas bloc par bloc (style calme du projet).",
       "- Écart : la fiche existante `arene_abysses.md` est mise à jour, plutôt que de créer `arene_abysse.md`.",
       "- Écart : travaux faits par le générateur puis posés par un correctif (pas de /fill) ; le chargement des chunks (/forceload) est dans la fonction de pose d'Arene Semaine, retiré à la fin.",
       "- Aucun coffre ni conteneur, aucune salle cachée dans cette arène ; pas de spawner ni de créature (les commandes coupent l'apparition des créatures, et la lumière est posée là où il faisait noir).",
       "- Les ventilateurs Create et le Cœur ne se testent qu'en jeu ; contrôlé après génération : leurs 63 blocs (commandes, ventilateurs, moteurs) et leurs données sont identiques à avant.",
       "- Le monde séparé Arene Abysses garde l'ancienne version ; la nouvelle est posée dans Arene Semaine.", "",
       "## À savoir", "", "- Il faut NeoForge et Create pour ouvrir ce monde.", "- Les blocs de commande coupent l'apparition des créatures (`doMobSpawning false`).", "- Sur un serveur : `enable-command-block=true`."]
(RACINE / "arene_abysses.md").write_text("\n".join(md) + "\n", encoding="utf-8")

from PIL import Image, ImageDraw
COUL = {"prismarine_bricks": (90, 160, 145), "dark_prismarine": (45, 95, 85), "sand": (215, 200, 150), "gravel": (140, 135, 130), "cobbled_deepslate": (75, 75, 80), "sea_lantern": (205, 235, 225),
        "kelp": (60, 120, 50), "kelp_plant": (60, 120, 50), "seagrass": (80, 150, 60), "soul_sand": (85, 65, 50), "magma_block": (200, 80, 20), "encased_fan": (170, 150, 120), "creative_motor": (150, 70, 200),
        "bubble_column": (190, 225, 245), "ochre_froglight": (240, 190, 90), "tuff_bricks": (110, 112, 100), "chiseled_tuff_bricks": (125, 127, 112),
        "polished_tuff": (120, 122, 112), "chiseled_tuff": (118, 120, 108), "polished_tuff_stairs": (120, 122, 112), "deepslate_tiles": (55, 55, 60),
        "deepslate_bricks": (70, 70, 74), "cracked_deepslate_bricks": (64, 64, 68), "chiseled_deepslate": (60, 60, 64), "polished_deepslate": (72, 72, 76),
        "mud_bricks": (140, 105, 80), "mud": (60, 55, 55), "sculk": (15, 55, 70), "tinted_glass": (60, 50, 70), "verdant_froglight": (190, 230, 160),
        "pearlescent_froglight": (240, 220, 230), "soul_lantern": (80, 200, 220), "crying_obsidian": (60, 20, 100), "glow_lichen": (110, 150, 120),
        "sea_pickle": (120, 150, 60), "chain": (60, 60, 70), "lantern": (230, 170, 80), "candle": (230, 220, 200)}
CACHE = {"air", "light", "water", "light_blue_stained_glass"}
s_ = 5
Wd, Hh = (SX + SZ) * s_ + 40, (SX + SZ) * s_ // 2 + SY * s_ + 60
img = Image.new("RGB", (Wd, Hh), (28, 48, 66))
dr = ImageDraw.Draw(img)


def ouvert(p):
    n = nom(p)
    return (p[1] > SOL) if n is None else n in CACHE


vis = [(p, s[0].split(":")[1]) for p, s in blocs.items() if s[0].split(":")[1] not in CACHE and p[1] >= SOL - 12 and not (p[1] > SOL + 15 and dist(p[0], p[2]) > 34 and s[0].endswith("dark_prismarine"))]
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
print(f"Ecrit : {len(liste)} blocs, palette {len(palette)}, taille {SX}x{SY}x{SZ}, avec donnees {len(NBT)}")
print(f"POSE : /tp @s 0 130 0   puis   /place template atelier:{NOM} {ORIGINE[0]} {ORIGINE[1]} {ORIGINE[2]}")
