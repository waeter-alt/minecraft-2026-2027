# [claude-build]
"""
donjon_temple.py - Donjon n°2 « Le Temple des Mille Racines » (2026-11-09) : 12 sections, ~31 min, style jungle, Grappin 2.4.
Plan : docs/donjon_temple_plan.md. Gabarit 130 x 100 x 216, pose en (150, -61, -65) dans le monde « Donjon 2 ».
Eau et lave confinees, aucun coffre ni mob ni spawner. Portes et pieges par blocs de commande (3 chaines enterrees).
  test-local\\venv\\Scripts\\python.exe atelier\\scripts\\donjon_temple.py
En jeu : /reload puis /function atelier:temple_pose ; remise a zero : /function atelier:temple_reset
Controle : atelier\\scripts\\verif_donjon_temple.py
"""
import argparse
import json
import math
from collections import Counter
from pathlib import Path

import nbtlib
import numpy as np
from nbtlib import Byte, Compound, Int, List, String

ap = argparse.ArgumentParser()
ap.add_argument("--monde", default="Donjon 2")
args = ap.parse_args()
RACINE = Path(__file__).resolve().parent.parent                      # atelier/
SAVE = RACINE.parent / "mod/run/client/saves" / args.monde                  # monde du client de test du depot (mod/ runClient)
NOM = "temple"
SX, SY, SZ = 130, 100, 216
ORIGINE = (150, -61, -65)


def S(n, **p):
    n = n if ":" in n else "minecraft:" + n
    return (n, tuple(sorted((k, str(v).lower() if isinstance(v, bool) else str(v)) for k, v in p.items())))


AIR, EAU = S("air"), S("water", level=0)
PIERRE, ANDE, MCB, TUF, RAC = S("stone"), S("andesite"), S("mossy_cobblestone"), S("tuff"), S("rooted_dirt")
MSB, CSB, SB, CHB = S("mossy_stone_bricks"), S("cracked_stone_bricks"), S("stone_bricks"), S("chiseled_stone_bricks")
MOUSSE, FOUG, GRAVIER = S("moss_block"), S("fern"), S("gravel")
LOG, BOIS = S("jungle_log", axis="y"), S("jungle_wood", axis="y")
FEUILLE = S("jungle_leaves", persistent=True, distance=1, waterlogged=False)
MRAC, RACP = S("mangrove_roots", waterlogged=False), S("hanging_roots", waterlogged=False)
LUM = S("light", level=15, waterlogged=False)
ANC = S("grappin:ancrage")
LANT, LANT_P = S("lantern", hanging=False, waterlogged=False), S("lantern", hanging=True, waterlogged=False)
CHAINE = S("chain", axis="y", waterlogged=False)
TOILE, OS, BARRES, PLANCHE = S("cobweb"), S("bone_block", axis="y"), S("iron_bars", waterlogged=False), S("spruce_planks")
LIANE_O = S("vine", east=False, north=False, south=False, up=False, west=True)
blocs, NBT = {}, {}


def put(x, y, z, s):
    x, y, z = int(x), int(y), int(z)
    if not (0 <= x < SX and 0 <= y < SY and 0 <= z < SZ):
        raise ValueError(f"hors gabarit {(x, y, z)} {s[0]}")
    blocs[(x, y, z)] = s


def nom(p):
    s = blocs.get(p)
    return None if s is None else s[0].split(":")[1]


def h01(x, y, z, g=0):
    h = (int(x) * 374761393 + int(y) * 1442695041 + int(z) * 668265263 + g * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h ^= h >> 16
    return h / 0xFFFFFFFF


def bruit(a, b, cel, g):
    ga, gb = a / cel, b / cel
    i, j = math.floor(ga), math.floor(gb)
    fa, fb = ga - i, gb - j
    sa, sb = fa * fa * (3 - 2 * fa), fb * fb * (3 - 2 * fb)
    p, q = h01(i, 0, j, g), h01(i + 1, 0, j, g)
    r, s = h01(i, 0, j + 1, g), h01(i + 1, 0, j + 1, g)
    t = p + (q - p) * sa
    return t + ((r + (s - r) * sa) - t) * sb


def roche(x, y, z, g=0):
    b = bruit(x + .7 * y, z - .5 * y, 7, 30 + g)
    c = bruit(x - .4 * y, z + .8 * y, 9, 40 + g)
    return ANDE if b < .22 else TUF if b > .8 else MCB if c > .72 else PIERRE


def ruine(x, y, z, g=0):
    b = bruit(x + .5 * y, z + .3 * y, 4, 80 + g)
    return MSB if b < .38 else CSB if b > .74 else SB


# ====================================================================== 1. volumes creux
C = np.zeros((SX, SY, SZ), dtype=bool)
ZONES, CACHEES = {}, {}


def boite(x0, x1, y0, y1, z0, z1):
    C[x0:x1 + 1, y0:y1 + 1, z0:z1 + 1] = True


def salle(nom_, x0, x1, z0, z1, ybas, yhaut, g, voute=6, rond=4.0):
    """Caverne naturelle : contour en super-ellipse bosselee, plafond en voute."""
    cx, cz, a, c = (x0 + x1) / 2, (z0 + z1) / 2, (x1 - x0) / 2, (z1 - z0) / 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            e = (abs(x + .5 - cx) / a) ** rond + (abs(z + .5 - cz) / c) ** rond
            if e < 1.0 + .25 * (bruit(x, z, 6, g) - .5):
                C[x, ybas:int(yhaut - voute * min(1.0, e)) + 1, z] = True
    ZONES[nom_] = (x0, x1, ybas, yhaut, z0, z1)


def cachee(n, x0, x1, y0, y1, z0, z1, acces, coffre, mobs):
    boite(x0, x1, y0, y1, z0, z1)
    CACHEES[n] = {"boite": (x0, x1, y0, y1, z0, z1), "acces": acces, "coffre": coffre, "mobs": mobs}


# etage du haut (sol 62) : Parvis (82), escalier, vestibule, trois ailes
boite(54, 76, 83, 99, 54, 76)                                      # S1 Parvis (a ciel ouvert, sol 82)
for z in range(35, 54):                                            # escalier Parvis -> vestibule (on monte vers le sud)
    boite(63, 67, z + 29, z + 32, z, z)
boite(40, 90, 63, 73, 14, 34)                                      # S2 Vestibule (sol 62)
boite(35, 39, 63, 66, 22, 24)                                      # arche ouest
boite(91, 95, 63, 66, 22, 24)                                      # arche est
boite(72, 74, 63, 66, 35, 49)                                      # couloir vers le puits
boite(8, 34, 63, 73, 8, 40)                                        # S4 aile des Miroirs d'Eau (ouest)
boite(8, 31, 50, 62, 8, 40)                                        # son bassin
boite(96, 122, 63, 73, 8, 40)                                      # S3 aile des Serpents (est)
salle("P", 72, 96, 46, 68, 41, 73, 5, voute=3)                     # S5 Puits des Lianes (fond 40)
boite(82, 94, 72, 76, 62, 68)                                      # voute relevee au-dessus de la corniche de l'idole (le plafond naturel est a 70)
# descente vers l'etage du bas (sol 40)
for z in range(35, 57):                                            # couloir du vestibule vers la salle des colonnes (porte G6)
    boite(44, 46, 96 - z + 1, 96 - z + 4, z, z)
boite(30, 62, 41, 52, 57, 90)                                      # S7 Salle des Colonnes Brisees (sol 40)
boite(63, 67, 41, 44, 76, 78)                                      # sortie est vers les ponts (ouverte)
boite(64, 94, 29, 52, 74, 80)                                      # S8 couloir des pieges : passerelle (sol 40) au-dessus d'une fosse
boite(95, 96, 41, 44, 76, 78)                                      # passage vers le gouffre
salle("G", 97, 124, 58, 100, 11, 52, 9, voute=6)                   # S9 Grand Gouffre Interieur (fond 10)
boite(106, 108, 47, 50, 96, 103)                                   # couloir gouffre -> crypte
boite(98, 122, 27, 56, 104, 124)                                   # S10 Crypte des Racines (fosse, fond 26)
boite(45, 47, 41, 44, 91, 93)                                      # couloir a cle (porte G7) vers l'antichambre
boite(42, 50, 41, 50, 94, 99)                                      # S11 Antichambre (sol 40)
boite(45, 47, 41, 44, 100, 133)                                    # couloir droit vers l'aile du boss (sol 40)
HX0, HX1, HZ0, HZ1, HY0, HY1 = 16, 78, 134, 196, 6, 92
boite(HX0, HX1, HY0, HY1, HZ0, HZ1)                                # S12 hall du boss : du fond du gouffre (6) a la voute (92)
VOUTE = {x: HY1 - int(14 * ((x - 47) / 32) ** 2) for x in range(HX0, HX1 + 1)}
for x in range(HX0, HX1 + 1):                                      # voute en berceau
    C[x, VOUTE[x] + 1:HY1 + 1, HZ0:HZ1 + 1] = False
boite(39, 41, 71, 74, 197, 204)                                    # couloir de sortie (porte SORTIE)
boite(34, 46, 71, 77, 205, 211)                                    # salle de sortie, vide
# salles cachees (au moins 7 x 7 x 4)
cachee(1, 56, 64, 72, 76, 60, 68, "Parvis : trappe dans une dalle moussue, échelle", (57, 72, 66), (61, 72, 63))
boite(59, 59, 72, 81, 59, 59)
cachee(2, 44, 52, 63, 67, 2, 10, "Vestibule : faux mur de briques fissurées, levier derrière des lianes", (45, 63, 9), (49, 63, 5))
boite(47, 49, 63, 65, 11, 13)
cachee(3, 100, 108, 63, 67, 44, 52, "Aile des Serpents : étagère de bibliothèque qui s'ouvre, bouton caché dans le mur", (101, 63, 51), (105, 63, 48))
boite(103, 105, 63, 65, 41, 43)
cachee(4, 12, 20, 63, 67, 44, 52, "Aile des Miroirs d'Eau : passage derrière un rideau de lianes, au sud", (13, 63, 51), (17, 63, 48))
boite(14, 16, 63, 65, 41, 43)
cachee(5, 100, 108, 63, 67, 56, 64, "Puits des Lianes : tunnel dans la paroi est, atteint avec un ancrage caché", (107, 63, 63), (103, 63, 60))
boite(90, 99, 63, 66, 59, 61)
cachee(6, 37, 45, 31, 34, 66, 74, "Salle des Colonnes : trappe sous un pilier effondré, échelle", (38, 31, 73), (42, 31, 70))
boite(41, 41, 35, 39, 70, 70)
cachee(7, 100, 108, 41, 45, 46, 54, "Gouffre : tunnel dans la paroi nord, atteint avec un ancrage isolé", (101, 41, 53), (105, 41, 50))
boite(103, 105, 41, 44, 55, 61)
cachee(8, 83, 91, 47, 51, 103, 111, "Crypte : faux mur de racines, plaque de pression sous la mousse", (84, 47, 110), (88, 47, 107))
boite(92, 103, 47, 49, 106, 108)
print("[1] volumes :", int(C.sum()), "cases creuses", flush=True)

# ====================================================================== 2. coquille : maconnerie de temple (1 bloc), puis roche (3 blocs), mousse
d = C.copy()
couches = []
for k in range(4):
    n = d.copy()
    n[1:] |= d[:-1]
    n[:-1] |= d[1:]
    n[:, 1:] |= d[:, :-1]
    n[:, :-1] |= d[:, 1:]
    n[:, :, 1:] |= d[:, :, :-1]
    n[:, :, :-1] |= d[:, :, 1:]
    couches.append(n & ~d)
    d = n
for k, cu in enumerate(couches):
    for x, y, z in zip(*np.nonzero(cu & ~C)):
        put(x, y, z, ruine(x, y, z) if k == 0 else roche(x, y, z))
COQUE = d & ~C
bord = np.zeros_like(C)
bord[1:-1, 1:-1, 1:-1] = COQUE[1:-1, 1:-1, 1:-1] & (C[2:, 1:-1, 1:-1] | C[:-2, 1:-1, 1:-1] | C[1:-1, 1:-1, 2:] | C[1:-1, 1:-1, :-2] | C[1:-1, 2:, 1:-1] | C[1:-1, :-2, 1:-1])
for x, y, z in zip(*np.nonzero(bord)):
    if bruit(x + .5 * y, z - .3 * y, 8, 70) > .6:
        put(x, y, z, MOUSSE)
print("[2] coquille :", len(blocs), flush=True)


def vide(p):
    x, y, z = p
    return 0 <= x < SX and 0 <= y < SY and 0 <= z < SZ and C[x, y, z] and p not in blocs


def sol(x, y, z, s=None):
    put(x, y, z, s or roche(x, y, z))


def plancher(x0, x1, z0, z1, y, f=None):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if C[x, y + 1, z]:
                put(x, y, z, f(x, z) if f else (ruine(x, y, z) if (x + z) % 9 else MOUSSE))


def massif(x0, x1, z0, z1, ybas, ytop, haut=None):
    """Pilier ou corniche pleine du ybas au ytop (dessus en mousse)."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(ybas, ytop + 1):
                put(x, y, z, haut if (y == ytop and haut) else MOUSSE if y == ytop else roche(x, y, z) if (x + y + z) % 3 else MCB)


# ====================================================================== 3. le parcours
PORTES, LEVIERS, ANCRAGES, CHUTES, POINTS = {}, {}, [], {}, {}


def porte(n, cases, s):
    PORTES[n] = {"cases": [list(c) for c in cases], "bloc": s[0], "props": dict(s[1])}
    for c in cases:
        put(*c, s)


def ancrage(n, x, y, z, corde=True):
    put(x, y, z, ANC)
    ANCRAGES.append((n, x, y, z))
    if corde:
        k = y + 1
        while k < SY and vide((x, k, z)):
            put(x, k, z, MRAC if (k - y) % 5 else CHAINE)
            k += 1


def escalier(x0, x1, z, f, direction):
    for x in range(x0, x1 + 1):
        put(x, f, z, S("mossy_stone_brick_stairs", facing=direction, half="bottom", shape="straight", waterlogged=False))


# --- S1 Parvis : sol 82, ruines, stele, escalier vers le vestibule
for x in range(54, 77):
    for z in range(54, 77):
        put(x, 82, z, MOUSSE if bruit(x, z, 4, 11) > .45 else ruine(x, 82, z) if (x + z) % 7 else RAC)
for k, (cx, cz, h) in enumerate(((55, 55, 7), (75, 55, 9), (55, 75, 5), (75, 75, 8), (65, 76, 4), (54, 65, 6), (76, 65, 3))):
    for y in range(83, 83 + h):
        put(cx, y, cz, ruine(cx, y, cz, k))
        if h > 4 and y % 3 == 0:
            put(cx, y, cz, CHB)
for z in range(35, 54):                                            # escalier du Parvis au vestibule
    f = z + 28
    for x in range(63, 68):
        put(x, f, z, S("mossy_stone_brick_stairs", facing="south", half="bottom", shape="straight", waterlogged=False))
POINTS["S1"] = (65, 83, 62)
# --- S2 Vestibule : sol 62, trois arches, fresques et socles de l'enigme du sanctuaire, couloir de descente (porte G6)
plancher(40, 90, 14, 34, 62)
for x in range(40, 91):
    for z in range(14, 35):
        if C[x, 63, z] and (x, 62, z) in blocs and h01(x, z, 0, 3) < .06:
            put(x, 62, z, S("cracked_stone_bricks"))
for z in range(35, 57):                                            # descente vers l'etage du bas, du vestibule (62) a la salle des colonnes (40)
    f = 96 - z
    for x in range(44, 47):
        put(x, f, z, S("mossy_stone_brick_stairs", facing="north", half="bottom", shape="straight", waterlogged=False))
porte("G6", [(x, y, 36) for x in range(44, 47) for y in range(61, 65)], BARRES)
POINTS["S2"] = (65, 63, 28)
SYMBOLES = {                                                       # motifs 5 x 5 (ligne du haut d'abord) ; # = couleur 1, + = couleur 2
    "serpent": ("green_terracotta", "lime_terracotta", ["###..", "..#..", ".##..", ".#...", ".###."]),
    "jaguar": ("orange_terracotta", "black_terracotta", ["#.#.#", "#####", "#+#+#", "#####", ".###."]),
    "aigle": ("light_blue_terracotta", "white_terracotta", ["#...#", "##.##", ".###.", "..#..", ".#.#."]),
}
ORDRE_ENIGME = ["jaguar", "serpent", "aigle"]                      # rang = nombre de lanternes sous la fresque, dans l'aile de chaque symbole


def fresque(sym, x0, y0, z0, axe, rang):
    """Fresque 5 x 5 encadree de briques sculptees ; renvoie les cases des 'rang' lanternes posees dessous."""
    c1, c2, motif = SYMBOLES[sym]
    for i in range(-1, 6):
        for j in range(-1, 6):
            x, z = (x0 + i, z0) if axe == "x" else (x0, z0 + i)
            y = y0 + 4 - j
            if i in (-1, 5) or j in (-1, 5):
                put(x, y, z, CHB)
            else:
                ch = motif[j][i]
                put(x, y, z, S(c1) if ch == "#" else S(c2) if ch == "+" else MSB)
    return [(x0 + i, y0 - 2, z0) if axe == "x" else (x0, y0 - 2, z0 + i) for i in range(rang)]


SOCLES = {}
for sym, x0 in (("aigle", 55), ("jaguar", 62), ("serpent", 69)):    # ordre des socles, de l'ouest a l'est (pas celui de l'enigme)
    fresque(sym, x0, 64, 14, "x", 0)
    for y in (63, 64):
        put(x0 + 2, y, 17, S("polished_andesite"))
    put(x0 + 2, 65, 17, S("stone_button", face="floor", facing="north", powered=False))
    SOCLES[sym] = (x0 + 2, 65, 17)
BALISES = {"fw": (41, 70, 23), "fe": (89, 70, 23), "fp": (73, 70, 34)}      # s'allument quand l'aile est reussie
for p in BALISES.values():
    put(*p, CHB)
# --- S3 Aile des Serpents (est) : sol 62, six dalles a presser dans l'ordre du serpent peint, murs bas
plancher(96, 122, 8, 40, 62)
for x in range(91, 96):
    for z in range(22, 25):
        put(x, 62, z, MSB)
COULEURS = ["red", "orange", "yellow", "lime", "cyan", "purple"]    # l'ordre du serpent peint (de gauche a droite)
DALLES = [(110, 14), (106, 22), (112, 36), (102, 34), (116, 12), (118, 30)]    # dalle k (0..5) = couleur k
for i, c in enumerate(COULEURS):                                   # le serpent peint, mur nord, de gauche a droite
    put(100 + 3 * i, 66, 9, S(c + "_terracotta"))
    put(100 + 3 * i, 67, 9, CHB)
    put(100 + 3 * i, 65, 9, CHB)
PLAQUES_E = []
for i, (px, pz) in enumerate(DALLES):
    put(px, 63, pz, S("stone_pressure_plate", powered=False))
    PLAQUES_E.append((px, 63, pz))
    for y in (63, 64, 65):
        put(px, y, pz + 1, S(COULEURS[i] + "_terracotta"))
    put(px, 66, pz + 1, CHB)
for (x0, x1, z0, z1) in ((100, 104, 18, 18), (108, 114, 26, 26), (104, 108, 30, 30), (114, 118, 18, 18), (110, 110, 28, 34)):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(63, 66):
                if (x, y, z) not in blocs:
                    put(x, y, z, MCB if y < 65 else MSB)
fr_e = fresque("serpent", 96, 65, 33, "z", 2)                      # fresque du serpent, 2 lanternes (rang dans l'enigme du sanctuaire)
for (x, y, z) in fr_e:
    put(x + 1, y, z, LANT)
POINTS["S3"] = (97, 63, 23)
POINTS["S3fin"] = (118, 63, 31)
# --- S4 Aile des Miroirs d'Eau (ouest) : bassin (fond 49, eau 50-52), 7 sauts, levier derriere des lianes
for x in range(8, 32):
    for z in range(8, 41):
        put(x, 49, z, GRAVIER)
        for y in (50, 51, 52):
            put(x, y, z, EAU)
plancher(32, 34, 8, 40, 62)
for x in range(35, 40):
    for z in range(22, 25):
        put(x, 62, z, MSB)
PILIERS_W = [(29, 22, 62), (26, 25, 62), (23, 28, 63), (20, 31, 63), (17, 28, 64), (14, 25, 64)]
for (x, z, top) in PILIERS_W:
    massif(x, x + 1, z, z + 1, 49, top)
massif(8, 12, 22, 26, 49, 64)                                      # corniche du levier
LEVIERS["LW"] = (8, 65, 24, "east")
put(8, 65, 24, S("lever", face="wall", facing="east", powered=False))
for z in (23, 24, 25):                                             # lianes qui cachent le levier
    for y in range(66, 74):
        put(9, y, z, S("cave_vines_plant", berries=h01(9, y, z, 4) < .5))
    put(9, 66, z, S("cave_vines", age=25, berries=True))
CHUTES["S4"] = ((9, 50, 8), (23, 6, 33), (33, 63, 22))             # bassin -> retour sur la rive d'entree
fr_w = fresque("jaguar", 34, 65, 12, "z", 1)                       # fresque du jaguar, 1 lanterne
for (x, y, z) in fr_w:
    put(x - 1, y, z, LANT)
POINTS["S4"] = (33, 63, 23)
POINTS["S4fin"] = (10, 65, 24)
# --- S5 Puits des Lianes : fond d'eau (41-43), rive, corniche d'entree (62) + echelle, tutoriel, 6 ancrages, corniche de l'idole (71)
for x in range(72, 97):
    for z in range(46, 69):
        if C[x, 41, z]:
            put(x, 40, z, GRAVIER)
            for y in (41, 42, 43):
                put(x, y, z, EAU)
massif(72, 78, 46, 50, 40, 62, haut=MSB)                           # corniche d'entree (pilier plein)
for x in range(72, 79):
    for z in range(46, 51):
        put(x, 62, z, MSB)
massif(72, 78, 51, 54, 40, 44, haut=MSB)                           # rive du fond
for y in range(45, 63):
    put(74, y, 51, S("ladder", facing="south", waterlogged=False))
massif(86, 90, 46, 48, 40, 58, haut=MSB)                           # corniche de repos R1 (nord)
massif(72, 75, 60, 64, 40, 66, haut=MSB)                           # corniche de repos R2 (ouest)
massif(84, 92, 66, 68, 40, 71, haut=MSB)                           # corniche de l'idole (sud)
for x in range(84, 93):
    for z in range(66, 69):
        put(x, 71, z, CHB if (x + z) % 2 else S("polished_tuff"))
PLAQUE_IDOLE = (88, 72, 67)
put(*PLAQUE_IDOLE, S("stone_pressure_plate", powered=False))
for (ax, az) in ((85, 67), (91, 67)):
    for y in (72, 73):
        put(ax, y, az, CHB)
    put(ax, 74, az, LANT) if vide((ax, 74, az)) else None
ancrage("Hc", 91, 69, 60)                                         # ancrage cache : mene au tunnel de la salle cachee 5
massif(94, 98, 59, 61, 40, 62, haut=MSB)                           # palier du tunnel de la salle cachee 5
CHAINE_P = [("T1", 80, 53, 55), ("T2", 85, 56, 50), ("T3", 88, 60, 52), ("Z1", 82, 64, 56), ("Z2", 77, 68, 58), ("Z3", 80, 70, 64), ("Z4", 86, 75, 62)]
for (n_, x, y, z) in CHAINE_P:
    ancrage(n_, x, y, z)
fr_p = fresque("aigle", 76, 65, 46, "x", 3)                        # fresque de l'aigle, 3 lanternes : mur nord de la corniche d'entree
for (x, y, z) in fr_p:
    put(x, y, z + 1, LANT)
POINTS["S5"] = (74, 63, 48)
POINTS["S5fin"] = (88, 72, 66)
CHUTES["S5"] = ((72, 41, 46), (24, 2, 23), (75, 45, 52))           # fond d'eau -> rive du fond (on remonte par l'echelle)
# --- S6 : sanctuaire dans le vestibule (socles ci-dessus), porte G6
# --- S7 Salle des Colonnes Brisees : sol 40, colonnes cassees, porte a cle G7 au sud
plancher(30, 62, 57, 90, 40)
for k, (cx, cz, h) in enumerate(((36, 62, 11), (44, 66, 4), (52, 62, 11), (58, 70, 7), (36, 76, 9), (46, 78, 11), (56, 80, 3), (40, 86, 11), (54, 86, 8), (34, 70, 6))):
    for y in range(41, 41 + h):
        for dx in (0, 1):
            for dz in (0, 1):
                put(cx + dx, y, cz + dz, ruine(cx, y, cz, k))
    if h < 11:
        for dx, dz in ((2, 0), (-1, 1), (0, -2)):
            if vide((cx + dx, 41, cz + dz)):
                put(cx + dx, 41, cz + dz, MCB)
for x in range(63, 68):
    for z in range(76, 79):
        put(x, 40, z, MSB)
porte("G7", [(x, y, 92) for x in range(45, 48) for y in range(41, 45)], BARRES)
put(44, 42, 92, S("lodestone"))
CLE_ZONE = (44, 41, 86)
POINTS["S7"] = (46, 41, 60)
POINTS["S7sud"] = (46, 41, 89)
# trappe de la salle cachee 6 : sous un pilier effondre
put(41, 40, 70, S("spruce_trapdoor", facing="south", half="top", open=False, powered=False, waterlogged=False))
for y in range(31, 40):
    put(41, y, 70, S("ladder", facing="east", waterlogged=False))
    if y < 35:
        put(40, y, 70, MSB)                                        # pilier qui porte l'echelle dans la salle
plancher(37, 45, 66, 74, 30)
# --- S8 Couloir des Pieges : passerelle (sol 40) au-dessus d'une fosse d'eau, 2 stalactites, 2 troncons qui cedent, 3 plaques, breche + ancrage
for x in range(64, 95):
    for z in range(74, 81):
        if C[x, 29, z]:
            put(x, 28, z, GRAVIER)
            for y in (29, 30, 31):
                put(x, y, z, EAU)
PONT = {}
for x in range(64, 95):
    if 80 <= x <= 88:                                              # la breche
        continue
    for z in range(76, 79):
        sorte = "T1" if 70 <= x <= 73 else "T2" if 90 <= x <= 93 else "plein"
        put(x, 40, z, PLANCHE if sorte != "plein" else S("spruce_planks") if (x + z) % 4 else S("stripped_spruce_log", axis="x"))
        PONT.setdefault(sorte, []).append((x, 40, z))
for x in range(66, 95, 6):                                         # chaines de la passerelle (decor)
    if 80 <= x <= 88:
        continue
    for z in (75, 79):
        y = 41
        while y < SY - 1 and C[x, y, z] and (x, y, z) not in blocs:
            put(x, y, z, CHAINE)
            y += 1
ancrage("P1", 84, 49, 77)
PLAQUES_POISON = [(76, 41, 77), (92, 41, 76), (66, 41, 78)]
for p in PLAQUES_POISON:
    put(*p, S("stone_pressure_plate", powered=False))
STALACTITES = {}
for n_, (x, z) in (("D1", (68, 77)), ("D2", (79, 77))):
    for xx in range(x - 2, x + 3):
        for zz in range(75, 80):
            for y in range(48, 52):
                if C[xx, y, zz]:
                    put(xx, y, zz, roche(xx, y, zz))
    put(x, 48, z, S("dripstone_block"))
    put(x, 47, z, S("pointed_dripstone", thickness="base", vertical_direction="down", waterlogged=False))
    put(x, 46, z, S("pointed_dripstone", thickness="tip", vertical_direction="down", waterlogged=False))
    STALACTITES[n_] = {"support": (x, 48, z), "zone": (x - 1, 41, z - 1)}
CHUTES["S8"] = ((64, 29, 74), (30, 8, 7), (65, 41, 77))            # fosse -> debut de la passerelle
POINTS["S8"] = (66, 41, 77)
POINTS["S8fin"] = (97, 41, 77)
# --- S9 Grand Gouffre Interieur : fosse d'eau (fond 10, eau 11-13), corniche d'arrivee (40), 7 ancrages en arc, corniche d'en face (46)
for x in range(97, 125):
    for z in range(58, 101):
        if C[x, 11, z]:
            put(x, 10, z, GRAVIER)
            for y in (11, 12, 13):
                put(x, y, z, EAU)
massif(97, 101, 74, 80, 10, 40, haut=MSB)                          # corniche d'arrivee
for x in range(95, 102):
    for z in range(76, 79):
        put(x, 40, z, MSB)
massif(104, 112, 94, 100, 10, 46, haut=MSB)                        # corniche d'en face
massif(103, 105, 58, 61, 36, 40, haut=MSB)                         # palier du tunnel de la salle cachee 7
for x in range(103, 106):
    for z in range(55, 58):
        put(x, 40, z, MSB)
CHAINE_G = [("A1", 104, 47, 70), ("A2", 112, 48, 66), ("A3", 119, 49, 72), ("A4", 121, 50, 80), ("A5", 118, 50, 88), ("A6", 112, 50, 93)]
for (n_, x, y, z) in CHAINE_G:
    ancrage(n_, x, y, z)
ancrage("Hg", 104, 48, 62)                                         # ancrage isole : mene au tunnel de la salle cachee 7
CHUTES["S9"] = ((97, 11, 58), (27, 3, 42), (99, 41, 77))           # fosse -> corniche d'arrivee
POINTS["S9"] = (99, 41, 77)
POINTS["S9fin"] = (108, 47, 97)
# --- S10 Crypte des Racines : fosse d'eau (fond 26, eau 27-30), corniche d'entree (46), ilot de la cle (31), 3 ancrages pour remonter
for x in range(98, 123):
    for z in range(104, 125):
        if C[x, 27, z]:
            put(x, 26, z, GRAVIER)
            for y in (27, 28, 29, 30):
                put(x, y, z, EAU)
massif(100, 110, 104, 108, 26, 46, haut=MSB)                       # corniche d'entree
for x in range(100, 111):
    for z in range(104, 109):
        put(x, 46, z, MSB)
for x in range(106, 109):
    for z in range(101, 104):
        put(x, 46, z, MSB)
massif(114, 120, 116, 122, 26, 31, haut=CHB)                       # ilot du sanctuaire
PLAQUE_CLE = (117, 32, 119)
put(*PLAQUE_CLE, S("stone_pressure_plate", powered=False))
for (x, z) in ((115, 120), (119, 120)):
    for y in (32, 33):
        put(x, y, z, CHB)
    put(x, 34, z, LANT)
for (n_, x, y, z) in (("K1", 115, 42, 113), ("K2", 110, 46, 110), ("K3", 106, 51, 106)):
    ancrage(n_, x, y, z)
POINTS["S10"] = (106, 47, 106)
POINTS["S10cle"] = (117, 32, 118)
# --- S11 Antichambre : couloir de statues (la porte G7 est au nord), puis couloir droit vers l'aile du boss
plancher(42, 50, 94, 99, 40)
for z in (95, 98):                                                 # statues de pierre le long du couloir
    for x in (43, 49):
        for y in range(41, 45):
            put(x, y, z, CHB if y == 44 else MSB)
POINTS["S11"] = (46, 41, 96)
# --- S12 aile du boss : fond du gouffre (lave confinee), 9 plateformes suspendues + depart, piliers, ancrages a chaines, sortie scellee
LAVE = S("lava", level=0)
for x in range(HX0, HX1 + 1):
    for z in range(HZ0, HZ1 + 1):
        put(x, 5, z, S("stone") if h01(x, 5, z, 7) < .55 else S("andesite"))
        for y in (6, 7):
            put(x, y, z, LAVE)
PLATS = {                                                          # nom: (x0, x1, z0, z1, dessus, ruine)
    "D": (36, 58, 134, 146, 40, False),
    "P1": (18, 27, 154, 163, 46, False), "P2": (40, 47, 156, 163, 50, False), "P3": (62, 73, 153, 164, 48, True),
    "P4": (30, 35, 170, 175, 56, False), "P5": (50, 59, 172, 182, 58, True), "P6": (66, 72, 174, 179, 62, False),
    "P7": (20, 29, 182, 191, 60, True), "P8": (36, 45, 188, 196, 70, False), "P9": (58, 68, 190, 196, 60, True),
}
noms_p = list(PLATS)
for i, a_ in enumerate(noms_p):                                    # aucune paire de plateformes a moins de 6 blocs (pas de saut possible)
    for b_ in noms_p[i + 1:]:
        A_, B_ = PLATS[a_], PLATS[b_]
        gap = math.hypot(max(0, max(A_[0], B_[0]) - min(A_[1], B_[1]) - 1), max(0, max(A_[2], B_[2]) - min(A_[3], B_[3]) - 1))
        assert gap >= 6, ("plateformes trop proches", a_, b_, gap)
PC, BAS = {}, {}


def surface(x, y, z, g):
    v = bruit(x, z, 3, 200 + g)
    return MOUSSE if v > .64 else CSB if v < .2 else ruine(x, y, z, g)


def plateforme(nom_, g):
    x0, x1, z0, z1, top, rn = PLATS[nom_]
    cx, cz, a, c = (x0 + x1 + 1) / 2, (z0 + z1 + 1) / 2, (x1 - x0 + 1) / 2, (z1 - z0 + 1) / 2
    cells = {}
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if nom_ == "D":
                e = 0.0 if z <= 146 - int(4 * ((x - 47) / 12.0) ** 2) else 2.0
            else:
                e = (abs(x + .5 - cx) / a) ** 3 + (abs(z + .5 - cz) / c) ** 3 + .45 * (bruit(x, z, 4, g) - .5)
            if e < 1.0:
                cells[(x, z)] = e
    if nom_ == "P8":
        for x in range(39, 42):
            for z in range(193, 197):
                cells[(x, z)] = 0.3
    if rn:                                                         # ruine : trous et un coin effondre, centre intact
        k = int(h01(g, 1, 2) * 4)
        sx, sz = (1 if k & 1 else -1), (1 if k & 2 else -1)
        for (x, z) in list(cells):
            d = math.hypot(x + .5 - cx, z + .5 - cz)
            if d > 2.8 and (bruit(x, z, 3, g + 9) > .72 or sx * (x + .5 - cx) / a + sz * (z + .5 - cz) / c > 1.0):
                del cells[(x, z)]
    cen = min(cells, key=lambda p: (p[0] + .5 - cx) ** 2 + (p[1] + .5 - cz) ** 2)
    vus, file_ = {cen}, [cen]                                      # on ne garde que la partie reliee au centre
    while file_:
        px, pz = file_.pop()
        for q in ((px + 1, pz), (px - 1, pz), (px, pz + 1), (px, pz - 1)):
            if q in cells and q not in vus:
                vus.add(q)
                file_.append(q)
    cells = {p: e for p, e in cells.items() if p in vus}
    bas = top
    for (x, z), e in cells.items():
        prof = 6 - int(3 * (z - 134) / 12) if nom_ == "D" else 2 + int(5 * (1 - min(e, 1.0)))
        for k in range(prof):
            put(x, top - k, z, surface(x, top, z, g) if k == 0 else roche(x, top - k, z, g) if k % 2 else ruine(x, top - k, z, g))
        bas = min(bas, top - prof + 1)
    PC[nom_], BAS[nom_] = cells, bas
    return cen


CENTRES = {n_: plateforme(n_, 11 * i + 3) for i, n_ in enumerate(PLATS)}
assert min(BAS.values()) >= 34, BAS                                # le gouffre (zone de chute) s'arrete sous la plus basse plateforme
PILIERS = [(17, 140, None), (17, 168, 62), (17, 193, None), (75, 140, 74), (75, 160, None), (75, 180, 56), (75, 189, None)]
for (px, pz, h_) in PILIERS:                                       # piliers geants contre les parois, certains casses
    for n_, (x0, x1, z0, z1, top, rn) in PLATS.items():
        assert px + 3 < x0 - 1 or px > x1 + 1 or pz + 3 < z0 - 1 or pz > z1 + 1, ("pilier contre plateforme", px, pz, n_)
    haut = 0
    for x in range(px, px + 3):
        for z in range(pz, pz + 3):
            ft = VOUTE[x] if h_ is None else h_
            haut = ft
            for y in range(5, ft + 1):
                if C[x, y, z] or y == 5:
                    put(x, y, z, CHB if y % 9 == 0 else ruine(x, y, z, 5) if (x + z + y) % 5 else MCB)
    if h_ is not None:
        for (dx, dz) in ((-1, 1), (3, 0), (1, 3)):
            if vide((px + dx, haut + 1, pz + dz)):
                put(px + dx, haut + 1, pz + dz, MCB)
        if vide((px + 1, haut + 1, pz + 1)):
            put(px + 1, haut + 1, pz + 1, LUM)
EDGES = [("D", "P1"), ("D", "P2"), ("D", "P3"), ("P1", "P2"), ("P2", "P3"), ("P1", "P4"), ("P2", "P4"), ("P2", "P5"), ("P3", "P5"), ("P5", "P6"),
         ("P4", "P7"), ("P5", "P8"), ("P5", "P9"), ("P6", "P9"), ("P7", "P8"), ("P8", "P9")]


def colonne_libre(x, y, z):
    """La chaine peut monter du point d'ancrage jusqu'a la voute sans rien toucher."""
    k = y + 1
    while 0 <= k < SY and C[x, k, z]:
        if (x, k, z) in blocs:
            return False
        k += 1
    return k > y + 4


def ancrage_chaine(n, x, y, z):
    put(x, y, z, ANC)
    ANCRAGES.append((n, x, y, z))
    k = y + 1
    while k < SY and vide((x, k, z)):
        put(x, k, z, CHAINE)
        k += 1


def centres_de(n_):
    top = PLATS[n_][4]
    return np.array([[x + .5, top + 1.9, z + .5] for (x, z) in PC[n_]])


def dmin(p, c):
    return float(np.sqrt(((p - c) ** 2).sum(-1)).min())


ANCR_B = []
for (ea, eb) in EDGES:
    ca, cb = centres_de(ea), centres_de(eb)
    ymin = max(PLATS[ea][4], PLATS[eb][4]) + 2
    xs = range(min(PLATS[ea][0], PLATS[eb][0]) - 2, max(PLATS[ea][1], PLATS[eb][1]) + 3)
    zs = range(min(PLATS[ea][2], PLATS[eb][2]) - 2, max(PLATS[ea][3], PLATS[eb][3]) + 3)
    cand = np.array([(x, y, z) for x in xs for z in zs for y in range(ymin, ymin + 7)
                     if HX0 <= x <= HX1 and HZ0 <= z <= HZ1 and C[x, y, z] and (x, y, z) not in blocs])
    p = cand + .5
    dA = np.sqrt(((p[:, None, :] - ca[None, :, :]) ** 2).sum(-1)).min(1)
    dB = np.sqrt(((p[:, None, :] - cb[None, :, :]) ** 2).sum(-1)).min(1)
    cout = np.maximum(dA, dB) + .05 * (cand[:, 1] - ymin)
    if any(a[2] >= ymin and max(dmin(np.array(a[1:]) + .5, ca), dmin(np.array(a[1:]) + .5, cb)) <= 12.5 for a in ANCR_B):
        continue                                                   # un ancrage deja pose convient aux deux plateformes
    for i in np.argsort(cout):
        x, y, z = (int(v) for v in cand[i])
        if colonne_libre(x, y, z) and all(abs(x - o[1]) + abs(z - o[3]) >= 4 or abs(y - o[2]) >= 4 for o in ANCR_B):
            if max(dA[i], dB[i]) > 13.4:
                print("  ! ancrage trop eloigne pour", ea, eb, round(float(max(dA[i], dB[i])), 1))
            n_ = f"V{len(ANCR_B) + 1}"
            ancrage_chaine(n_, x, y, z)
            ANCR_B.append((n_, x, y, z))
            break
    else:
        raise SystemExit(f"pas d'ancrage pour {ea}-{eb}")
BOSS = {}
for n_ in PLATS:
    cx_, cz_ = CENTRES[n_]
    top = PLATS[n_][4]
    if n_ != "D":
        assert all(vide((cx_, top + k, cz_)) for k in range(1, 6)), ("position du boss genee", n_)
        BOSS[n_] = (cx_, top + 1, cz_)
    for (x, z) in PC[n_]:                                          # lumiere invisible sur chaque plateforme
        if (x * 3 + z * 5) % 7 == 0 and vide((x, top + 2, z)) and (x, z) != (cx_, cz_):
            put(x, top + 2, z, LUM)
    ext = sorted(PC[n_], key=lambda q: (q[0], q[1]))               # chaines decoratives aux extremites, avec une lanterne
    for (x, z) in (ext[0], ext[-1], max(PC[n_], key=lambda q: q[1])):
        if (x, z) != (cx_, cz_) and colonne_libre(x, top + 3, z):
            k = top + 4
            while k < SY and vide((x, k, z)):
                put(x, k, z, CHAINE)
                k += 1
            put(x, top + 3, z, LANT_P)
for x in range(HX0, HX1 + 1, 4):                                   # chaines pendues le long de la voute, avec des lanternes
    for z in (140, 152, 168, 184, 194):
        if h01(x, z, 0, 301) < .3:
            top_ = VOUTE[x]
            if C[x, top_, z] and (x, top_, z) not in blocs:
                n_ch = 0
                for k in range(3 + int(8 * h01(x, z, 0, 302))):
                    if not vide((x, top_ - k, z)):
                        break
                    put(x, top_ - k, z, CHAINE)
                    n_ch = k
                if vide((x, top_ - n_ch - 1, z)):
                    put(x, top_ - n_ch - 1, z, S("lantern", hanging=True, waterlogged=False))
porte("SORTIE", [(x, y, 199) for x in range(39, 42) for y in range(71, 75)], BARRES)
plancher(34, 46, 205, 211, 70, lambda x, z: CHB if (x + z) % 2 else S("polished_tuff"))
for z in range(198, 204):
    for x in range(39, 42):
        put(x, 70, z, MSB)
for x in (35, 45):
    for y in range(71, 77):
        put(x, y, 208, CHB if y % 2 else MSB)
POINTS["S12"] = (47, 41, 139)
POINTS["sortie"] = (40, 71, 208)
print("[3b] aile du boss :", {n_: len(PC[n_]) for n_ in PC}, "| ancrages du boss", len(ANCR_B), "| dessous min", min(BAS.values()), flush=True)
# --- acces des salles cachees
for y in range(72, 82):                                            # H1 : echelle sous la trappe du Parvis
    put(59, y, 59, S("ladder", facing="south", waterlogged=False))
put(59, 82, 59, S("spruce_trapdoor", facing="south", half="top", open=False, powered=False, waterlogged=False))
porte("H2", [(x, y, 12) for x in range(47, 50) for y in range(63, 66)], CSB)
LEVIERS["LH2"] = (40, 65, 18, "east")
put(40, 65, 18, S("lever", face="wall", facing="east", powered=False))
for z in (17, 18, 19):
    for y in range(66, 74):
        put(41, y, z, S("cave_vines_plant", berries=False))
porte("H3", [(x, y, 41) for x in range(103, 106) for y in range(63, 66)], S("bookshelf"))
for x in range(98, 111):
    for y in range(63, 67):
        if (x, y, 41) not in blocs:
            put(x, y, 41, S("bookshelf"))
BOUTON_H3 = (101, 64, 40)
put(*BOUTON_H3, S("stone_button", face="wall", facing="north", powered=False))
massif(13, 17, 39, 40, 49, 62, haut=MSB)                           # H4 : palier devant l'ouverture, au sud de l'aile des Miroirs
for (x, z) in ((18, 34), (16, 36)):
    massif(x, x + 1, z, z + 1, 49, 63)
for x in range(14, 17):                                            # rideau de lianes devant l'ouverture
    for y in range(64, 74):
        put(x, y, 40, S("cave_vines_plant", berries=h01(x, y, 40, 6) < .4))
    put(x, 63, 40, S("cave_vines", age=25, berries=True))
porte("H8", [(95, y, z) for z in range(106, 109) for y in range(47, 50)], RAC)
BOUTON_H8 = (102, 47, 107)
put(*BOUTON_H8, S("stone_pressure_plate", powered=False))
for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):                # la plaque se cache au milieu de tapis de mousse
    put(102 + dx, 47, 107 + dz, S("moss_carpet"))
# trou d'acces de la salle cachee 5 (rideau de lianes devant le tunnel) et de la 7 : simples ouvertures, decouvertes avec un ancrage
print("[3] parcours :", len(blocs), "| ancrages", len(ANCRAGES), flush=True)
for f_ in ("donjon_temple_decor.py", "donjon_temple_decor2.py", "donjon_temple_decor3.py"):    # passe de decoration (voir ces fichiers)
    exec(compile((Path(__file__).resolve().parent / f_).read_text(encoding="utf-8"), f_, "exec"), globals())
print("[3c] apres decoration :", len(blocs), "blocs", flush=True)

# ====================================================================== 4. decor
n_dec = Counter()
for x in range(0, SX):
    for z in range(0, SZ):
        col = np.nonzero(C[x, :, z])[0]
        if len(col) == 0:
            continue
        top = col.max()
        if (x, top + 1, z) not in blocs or (x, top, z) in blocs:
            continue
        v = h01(x, top, z, 100)
        if v < .05:
            long_ = 2 + int(5 * h01(x, 1, z, 101))
            for k in range(long_):
                p = (x, top - k, z)
                if p in blocs or not C[p]:
                    break
                put(*p, (RACP if k == long_ - 1 else MRAC) if v < .02 else (S("cave_vines", age=25, berries=h01(x, k, z, 102) < .4) if k == long_ - 1
                                                                             else S("cave_vines_plant", berries=h01(x, k, z, 102) < .4)))
            n_dec["voute"] += 1
        elif v < .065:
            put(x, top, z, TOILE)
            n_dec["toiles"] += 1
for (cx, y, cz, quoi) in [(58, 63, 20, "banc"), (84, 63, 30, "banc"), (47, 41, 80, "os"), (34, 41, 60, "camp"), (110, 63, 20, "os"), (14, 63, 36, "os"),
                          (76, 41, 86, "table"), (100, 41, 90, "os"), (50, 29, 114, "table"), (60, 83, 70, "camp"), (66, 83, 58, "os"), (100, 47, 106, "os")]:
    if quoi == "camp":
        put(cx, y, cz, S("crafting_table"))
        put(cx + 1, y, cz, S("spruce_stairs", facing="east", half="bottom", shape="straight", waterlogged=False))
        put(cx, y, cz + 1, S("brown_carpet"))
        put(cx - 1, y, cz, LANT)
    elif quoi == "os":
        put(cx, y, cz, OS)
        put(cx + 1, y, cz, S("bone_block", axis="x"))
        put(cx, y, cz + 1, S("skeleton_skull", rotation=int(h01(cx, y, cz, 5) * 16)))
    elif quoi == "banc":
        for k in range(3):
            put(cx + k, y, cz, S("spruce_stairs", facing="north", half="bottom", shape="straight", waterlogged=False))
    elif quoi == "table":
        put(cx, y, cz, S("spruce_slab", type="top", waterlogged=False))
        put(cx + 1, y, cz, S("spruce_slab", type="top", waterlogged=False))
        put(cx, y + 1, cz, S("candle", candles=3, lit=True, waterlogged=False))
    n_dec["mobilier"] += 1
for x in range(2, SX - 2, 9):                                      # lumiere invisible partout (aucune apparition de monstre)
    for z in range(2, SZ - 2, 9):
        for y in range(2, SY - 2, 7):
            if C[x, y, z] and (x, y, z) not in blocs and not (HX0 <= x <= HX1 and HZ0 <= z <= HZ1 and y < 36):
                put(x, y, z, LUM)
for n_, (x0, x1, y0, y1, z0, z1) in {k: v["boite"] for k, v in CACHEES.items()}.items():
    lx = (x0 + x1) // 2
    while nom((lx, y1 + 1, (z0 + z1) // 2)) in (None, "light", "cave_vines", "cave_vines_plant", "mangrove_roots", "chain", "ladder", "hanging_roots", "cobweb"):
        lx += 2
    put(lx, y1, (z0 + z1) // 2, LANT_P)
for x in range(54, 77):                                            # Parvis : jungle qui reprend ses droits
    for z in range(54, 77):
        v = h01(x, z, 0, 150)
        if (x, 83, z) not in blocs and v < .05:
            put(x, 83, z, S("flowering_azalea") if v < .015 else FOUG)
print("[4] decor :", dict(n_dec), flush=True)

# ====================================================================== 5. blocs de commande (3 chaines enterrees dans la coque, au-dessus du vestibule)
CHAINES = [(42, 76, 20), (42, 76, 24), (42, 76, 28)]
CMD = []


def rel(x, y, z):
    i = len(CMD)
    c = CHAINES[i // 32]
    return f"~{x - (c[0] + i % 32)} ~{y - c[1]} ~{z - c[2]}"


def plage(cases):
    xs, ys, zs = zip(*cases)
    return (min(xs), min(ys), min(zs)), (max(xs), max(ys), max(zs))


def fill(cases, bloc_):
    (a, b, c), (d_, e, f) = plage(cases)
    return f"fill {rel(a, b, c)} {rel(d_, e, f)} {bloc_}"


def retour(depuis, vers, rayon="1.7"):
    """tp des joueurs a 'rayon' de 'depuis' vers la case 'vers'."""
    return f"positioned {rel(*depuis)} as @a[distance=..{rayon}] run tp @s ~{vers[0] - depuis[0]} ~{vers[1] - depuis[1] - 0.5} ~{vers[2] - depuis[2]}"


CLE = "minecraft:tripwire_hook[minecraft:custom_name='\"Clé de racine\"']"
CLE_DON = "minecraft:tripwire_hook[minecraft:custom_name='\"Clé de racine\"',minecraft:lore=['\"Ouvre la porte de l\\'Antichambre\"']]"
CMD.append("gamerule commandBlockOutput false")
CMD.append("gamerule doMobSpawning false")
CMD.append("scoreboard objectives add temple dummy")
for v_ in ("#se", "#ss", "#porte", "#t1", "#t2", "#fe", "#fw", "#fp"):
    CMD.append(f"scoreboard players add {v_} temple 0")
CMD.append(f"execute positioned {rel(54, 83, 54)} as @a[dx=22,dy=10,dz=22] unless items entity @s container.* grappin:grappin run give @s grappin:grappin")
# aile des Serpents : six dalles dans l'ordre du serpent peint ; une erreur ramene a l'entree de l'aile
ENTREE_E = (97, 63, 23)
for k, p in enumerate(PLAQUES_E, 1):
    on = f"execute if block {rel(*p)} minecraft:stone_pressure_plate[powered=true]"
    if k >= 2:
        CMD.append(f"{on} if score #se temple matches ..{k - 2} " + retour(p, ENTREE_E))
        CMD.append(f"execute if block {rel(*p)} minecraft:stone_pressure_plate[powered=true] if score #se temple matches ..{k - 2} run scoreboard players set #se temple 0")
    CMD.append(f"execute if block {rel(*p)} minecraft:stone_pressure_plate[powered=true] if score #se temple matches {k - 1} run scoreboard players set #se temple {k}")
CMD.append("execute if score #se temple matches 6 run scoreboard players set #fe temple 1")
CMD.append("execute if score #se temple matches 6 run scoreboard players set #se temple 7")
# aile des Miroirs d'Eau : le levier derriere les lianes ; aile du Puits : la plaque de l'idole
CMD.append(f"execute if block {rel(*LEVIERS['LW'][:3])} minecraft:lever[powered=true] run scoreboard players set #fw temple 1")
CMD.append(f"execute if block {rel(*PLAQUE_IDOLE)} minecraft:stone_pressure_plate[powered=true] run scoreboard players set #fp temple 1")
for f_, p in BALISES.items():
    CMD.append(f"execute if score #{f_} temple matches 1 run setblock {rel(*p)} minecraft:shroomlight")
# sanctuaire : trois socles dans l'ordre jaguar, serpent, aigle ; un socle ne marche que si l'aile correspondante est reussie
FLAG = {"jaguar": "#fw", "serpent": "#fe", "aigle": "#fp"}
for k, sym in enumerate(ORDRE_ENIGME, 1):
    b = SOCLES[sym]
    ok_ = f"execute if block {rel(*b)} minecraft:stone_button[powered=true] if score {FLAG[sym]} temple matches 1"
    if k >= 2:
        CMD.append(f"{ok_} if score #ss temple matches ..{k - 2} run playsound minecraft:block.note_block.bass master @a ~ ~ ~ 1 0.6")
        CMD.append(f"execute if block {rel(*b)} minecraft:stone_button[powered=true] if score {FLAG[sym]} temple matches 1 if score #ss temple matches ..{k - 2} run scoreboard players set #ss temple 0")
    CMD.append(f"execute if block {rel(*b)} minecraft:stone_button[powered=true] if score {FLAG[sym]} temple matches 1 if score #ss temple matches {k - 1} run scoreboard players set #ss temple {k}")
CMD.append(f"execute if score #ss temple matches 3 positioned {rel(*SOCLES['serpent'])} run playsound minecraft:block.note_block.chime master @a[distance=..40] ~ ~ ~ 1 1")
CMD.append("execute if score #ss temple matches 3 run " + fill([tuple(c) for c in PORTES["G6"]["cases"]], "minecraft:air"))
CMD.append("execute if score #ss temple matches 3 run scoreboard players set #ss temple 4")
# la cle : l'ilot de la crypte la donne, la porte G7 la prend
CMD.append(f"execute positioned {rel(*PLAQUE_CLE)} as @a[distance=..1.5] unless items entity @s container.* {CLE} run give @s {CLE_DON} 1")
CMD.append(f"execute if score #porte temple matches 0 positioned {rel(*CLE_ZONE)} as @a[dx=4,dy=3,dz=5] if items entity @s weapon.mainhand {CLE} run scoreboard players set #porte temple 1")
CMD.append("execute if score #porte temple matches 1 run " + fill([tuple(c) for c in PORTES["G7"]["cases"]], "minecraft:air"))
CMD.append(f"execute if score #porte temple matches 1 positioned {rel(*CLE_ZONE)} as @a[dx=4,dy=3,dz=5] run clear @s {CLE} 1")
CMD.append("execute if score #porte temple matches 1 run scoreboard players set #porte temple 2")
CMD.append(f"execute if score #porte temple matches 0 positioned {rel(*CLE_ZONE)} as @a[dx=4,dy=3,dz=5] unless items entity @s weapon.mainhand {CLE} run title @s actionbar "
           "{\"text\":\"La porte est fermée à clé (Clé de racine en main)\",\"color\":\"gold\"}")
# couloir des pieges : stalactites, troncons qui cedent, plaques empoisonnees
for n_, st in STALACTITES.items():
    sx, sy, sz = st["support"]
    zx, zy, zz = st["zone"]
    CMD.append(f"execute positioned {rel(zx, zy, zz)} if entity @a[dx=2,dy=2,dz=2] if block {rel(sx, sy, sz)} minecraft:dripstone_block run setblock {rel(sx, sy, sz)} minecraft:air")
for t in ("T1", "T2"):
    v_ = "#" + t.lower()
    (a, b, c), (d_, e, f) = plage(PONT[t])
    CMD.append(f"execute if score {v_} temple matches 0 positioned {rel(a, b + 1, c)} if entity @a[dx={d_ - a},dy=1,dz={f - c}] run scoreboard players set {v_} temple 50")
    CMD.append(f"execute if score {v_} temple matches 1.. run scoreboard players remove {v_} temple 1")
    CMD.append(f"execute if score {v_} temple matches 45 positioned {rel(a, b, c)} run playsound minecraft:block.wood.break master @a[distance=..16] ~ ~ ~ 1 0.6")
    CMD.append(f"execute if score {v_} temple matches 36 run " + fill(PONT[t], "minecraft:air"))
    CMD.append(f"execute if score {v_} temple matches 1 run " + fill(PONT[t], "minecraft:spruce_planks"))
for p in PLAQUES_POISON:
    CMD.append(f"execute if block {rel(*p)} minecraft:stone_pressure_plate[powered=true] positioned {rel(*p)} run effect give @a[distance=..1.5] minecraft:poison 4 1")
# retours de chute
for n_, ((x0, y0, z0), (dx, dy, dz), (tx, ty, tz)) in CHUTES.items():
    assert y0 + dy + 1.5 <= ty, ("zone de chute qui englobe son point d'arrivee (boucle de tp)", n_)
    CMD.append(f"execute positioned {rel(x0, y0, z0)} as @a[dx={dx},dy={dy},dz={dz}] run tp @s ~{tx - x0} ~{ty - y0 - 0.5} ~{tz - z0}")
# gouffre du boss : tout joueur qui descend sous les plateformes revient sur la plateforme de depart ; les objets tombes sont supprimes
ZB = (HX0, 10, HZ0)
ZB_D = (HX1 - HX0, 20, HZ1 - HZ0)
assert ZB[1] + ZB_D[1] + 1.5 <= min(BAS.values()), "la zone de chute touche une plateforme"
assert ZB[1] + ZB_D[1] + 1.5 <= POINTS["S12"][1], "la zone de chute englobe le point d'arrivee"
CMD.append(f"execute positioned {rel(*ZB)} as @a[dx={ZB_D[0]},dy={ZB_D[1]},dz={ZB_D[2]}] run function atelier:temple_chute_boss")
CMD.append(f"execute positioned {rel(*ZB)} run kill @e[type=item,dx={ZB_D[0]},dy={ZB_D[1]},dz={ZB_D[2]}]")
# salles cachees 2, 3 et 8
CMD.append(f"execute if block {rel(*LEVIERS['LH2'][:3])} minecraft:lever[powered=true] run " + fill([tuple(c) for c in PORTES["H2"]["cases"]], "minecraft:air"))
CMD.append(f"execute if block {rel(*BOUTON_H3)} minecraft:stone_button[powered=true] run " + fill([tuple(c) for c in PORTES["H3"]["cases"]], "minecraft:air"))
CMD.append(f"execute if block {rel(*BOUTON_H8)} minecraft:stone_pressure_plate[powered=true] run " + fill([tuple(c) for c in PORTES["H8"]["cases"]], "minecraft:air"))
assert len(CMD) <= 32 * len(CHAINES), len(CMD)
for i, c in enumerate(CMD):
    ch = CHAINES[i // 32]
    x, y, z = ch[0] + i % 32, ch[1], ch[2]
    assert not C[x, y, z] and not C[x, y + 1, z] and (x, y, z) in blocs, ("chaine hors de la roche", x, y, z)
    put(x, y, z, S("repeating_command_block" if i % 32 == 0 else "chain_command_block", conditional=False, facing="east"))
    NBT[(x, y, z)] = Compound({"id": String("minecraft:command_block"), "Command": String(c), "auto": Byte(1), "TrackOutput": Byte(0),
                               "conditionMet": Byte(0), "powered": Byte(0), "UpdateLastExecution": Byte(1)})
print("[5] commandes :", len(CMD), flush=True)

# ====================================================================== 6. controles
err = []
PASSE = {"air", "light", "water", "cave_vines", "cave_vines_plant", "hanging_roots", "vine", "moss_carpet", "fern", "flowering_azalea", "cobweb",
         "lantern", "candle", "lever", "stone_button", "stone_pressure_plate", "ladder", "brown_carpet", "skeleton_skull"}
for (x, y, z), s in blocs.items():
    n = s[0].split(":")[1]
    if n in ("water", "lava"):
        for q in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1), (x, y - 1, z)):
            if q not in blocs:
                if not (0 <= q[0] < SX and 0 <= q[1] < SY and 0 <= q[2] < SZ) or not C[q]:
                    err.append(f"eau qui fuit {(x, y, z)} -> {q}")
                else:
                    err.append(f"eau qui coule {(x, y, z)} -> {q}")
        if n == "water" and (x, y + 1, z) not in blocs and 0 <= y + 1 < SY and not C[x, y + 1, z]:
            err.append(f"eau sans plafond {(x, y, z)}")
    if n in ("chest", "barrel", "hopper", "dropper", "dispenser", "spawner", "trapped_chest", "decorated_pot", "chiseled_bookshelf"):
        err.append(f"interdit {n} {(x, y, z)}")
SOLIDE_NON = PASSE | {"chain", "iron_bars", "spruce_trapdoor", "soul_lantern"}


def solide(q):
    return q in blocs and blocs[q][0].split(":")[1] not in SOLIDE_NON


DIRS = {"east": (1, 0, 0), "west": (-1, 0, 0), "south": (0, 0, 1), "north": (0, 0, -1)}
for (x, y, z), s in blocs.items():
    n, pr = s[0].split(":")[1], dict(s[1])
    sup = []
    if n in ("lever", "stone_button"):
        if pr["face"] == "floor":
            sup = [(0, -1, 0)]
        elif pr["face"] == "wall":
            dx, dy, dz = DIRS[pr["facing"]]
            sup = [(-dx, 0, -dz)]
    elif n == "ladder":
        dx, dy, dz = DIRS[pr["facing"]]
        sup = [(-dx, 0, -dz)]
    elif n == "vine":
        sup = [DIRS[k] for k in DIRS if pr.get(k) == "true"]
    elif n in ("stone_pressure_plate", "candle", "fern", "flowering_azalea", "brown_carpet", "moss_carpet"):
        sup = [(0, -1, 0)]
    elif n in ("lantern", "soul_lantern"):
        if pr["hanging"] == "true":
            if not (solide((x, y + 1, z)) or nom((x, y + 1, z)) == "chain"):
                err.append(f"lanterne sans appui {(x, y, z)}")
        else:
            sup = [(0, -1, 0)]
    elif n == "hanging_roots":
        sup = [(0, 1, 0)]
    elif n in ("cave_vines", "cave_vines_plant"):
        q = (x, y + 1, z)
        if not (solide(q) or nom(q) in ("cave_vines_plant", "cave_vines")):
            err.append(f"liane sans appui {(x, y, z)}")
    for dx, dy, dz in sup:
        if not solide((x + dx, y + dy, z + dz)):
            err.append(f"{n} sans appui {(x, y, z)}")
for n_, x, y, z in ANCRAGES:
    if nom((x, y, z)) != "ancrage":
        err.append(f"ancrage {n_} recouvert")
for n_, c in CACHEES.items():
    x0, x1, y0, y1, z0, z1 = c["boite"]
    if min(x1 - x0 + 1, z1 - z0 + 1) < 7 or y1 - y0 + 1 < 4:
        err.append(f"salle cachee {n_} trop petite")
print("[controle] erreurs", len(err), Counter(e.split(" (")[0] for e in err).most_common(6), err[:6], flush=True)
if err:
    raise SystemExit(1)


# ====================================================================== 7. ecriture
def ecrire(cases, nbt_, nom_, taille):
    palette, index, liste = [], {}, []
    for p in sorted(cases, key=lambda q: (q[1], q[2], q[0])):
        s = cases[p]
        if s not in index:
            index[s] = len(palette)
            c = Compound({"Name": String(s[0])})
            if s[1]:
                c["Properties"] = Compound({k: String(v) for k, v in s[1]})
            palette.append(c)
        e = Compound({"pos": List[Int]([Int(v) for v in p]), "state": Int(index[s])})
        if p in nbt_:
            e["nbt"] = nbt_[p]
        liste.append(e)
    f = nbtlib.File({"DataVersion": Int(3955), "size": List[Int]([Int(v) for v in taille]), "palette": List[Compound](palette),
                     "blocks": List[Compound](liste), "entities": List[Compound]([])}, gzipped=True)
    (RACINE / "exports").mkdir(exist_ok=True)
    f.save(RACINE / "exports" / f"{nom_}.nbt")
    D = SAVE / "datapacks/atelier/data/atelier/structure"
    if D.exists():
        f.save(D / f"{nom_}.nbt")
    return len(liste)


n_total = ecrire(blocs, NBT, NOM, (SX, SY, SZ))
FN = SAVE / "datapacks/atelier/data/atelier/function"
if FN.exists():
    def w(p):
        return " ".join(str(v) for v in (ORIGINE[0] + p[0], ORIGINE[1] + p[1], ORIGINE[2] + p[2]))

    fl = f"forceload add {ORIGINE[0]} {ORIGINE[2]} {ORIGINE[0] + SX - 1} {ORIGINE[2] + SZ - 1}"
    (FN / "temple_pose.mcfunction").write_text("\n".join([
        "# [claude-build] Pose le Temple des Mille Racines (une seule fois, sans pause).", fl,
        'tellraw @a {"text":"Temple : 15 secondes de chargement, puis le jeu va se figer un moment. Ne mets pas en pause.","color":"gold"}',
        "schedule function atelier:temple_pose_2 300t"]) + "\n", encoding="utf-8")
    (FN / "temple_pose_2.mcfunction").write_text("\n".join([
        "# [claude-build] Efface la zone (au dessus du sol), pose la structure, puis remet a zero.",
        *[f"fill {ORIGINE[0] + a} {ORIGINE[1] + b} {ORIGINE[2] + c} {ORIGINE[0] + min(a + 30, SX - 1)} {ORIGINE[1] + min(b + 30, SY - 1)} "
          f"{ORIGINE[2] + min(c + 30, SZ - 1)} minecraft:air" for a in range(0, SX, 31) for b in range(1, SY, 31) for c in range(0, SZ, 31)],
        f"place template atelier:{NOM} {ORIGINE[0]} {ORIGINE[1]} {ORIGINE[2]}", "kill @e[type=item]",
        "function atelier:temple_reset", "schedule function atelier:temple_pose_3 100t"]) + "\n", encoding="utf-8")
    (FN / "temple_pose_3.mcfunction").write_text("\n".join([
        "# [claude-build] Nettoie et decharge.", "kill @e[type=item]", "forceload remove all",
        f'tellraw @a {{"text":"Le temple est pret. Entree : /tp @s {w((65, 83, 62))} 0 0","color":"green"}}']) + "\n", encoding="utf-8")
    sx_, sy_, sz_ = POINTS["S12"]
    (FN / "temple_decrocher.mcfunction").write_text("# [claude-build] Supprime le crochet du grappin du joueur dont l identifiant est passe en parametre.\n"
                                                    "$kill @e[type=grappin:crochet,nbt={Owner:$(uuid)}]\n", encoding="utf-8")
    (FN / "temple_chute_boss.mcfunction").write_text("\n".join([
        "# [claude-build] Un joueur est tombe dans le gouffre du boss : on decroche son grappin, chute lente (pas de degats), retour sur la plateforme de depart.",
        "data modify storage atelier:temple chute.uuid set from entity @s UUID",
        "function atelier:temple_decrocher with storage atelier:temple chute",
        "effect give @s minecraft:slow_falling 3 0 true",
        f"tp @s {ORIGINE[0] + sx_ + .5} {ORIGINE[1] + sy_} {ORIGINE[2] + sz_ + .5} 0 0",
        'title @s actionbar {"text":"Tu es tombé !","color":"red"}']) + "\n", encoding="utf-8")
    pc_ = [tuple(c) for c in PORTES["SORTIE"]["cases"]]
    (a, b, c), (d_, e, f) = plage(pc_)
    (FN / "temple_ouvrir_sortie_boss.mcfunction").write_text(
        f"# [claude-build] Ouvre la sortie du boss (a relier a la mort du boss).\nfill {w((a, b, c))} {w((d_, e, f))} minecraft:air\n"
        f"playsound minecraft:block.iron_door.open master @a {w((a, b, c))} 2 0.6\n", encoding="utf-8")
    (FN / "temple_fermer_sortie_boss.mcfunction").write_text(
        f"# [claude-build] Referme la sortie du boss (remise a zero de la salle).\nfill {w((a, b, c))} {w((d_, e, f))} minecraft:iron_bars[waterlogged=false]\n", encoding="utf-8")
    reset = ["# [claude-build] Remet le temple a zero : portes fermees, leviers baisses, enigmes et cle remises, pieges rearmes."]
    for n_, p in PORTES.items():
        props = ",".join(f"{k}={v}" for k, v in p["props"].items())
        (a, b, c), (d_, e, f) = plage([tuple(x) for x in p["cases"]])
        reset.append(f"fill {w((a, b, c))} {w((d_, e, f))} {p['bloc']}{'[' + props + ']' if props else ''}")
    for n_, (x, y, z, f) in LEVIERS.items():
        reset.append(f"setblock {w((x, y, z))} minecraft:lever[face=wall,facing={f},powered=false]")
    for n_, st in STALACTITES.items():
        sx, sy, sz = st["support"]
        reset.append(f"setblock {w((sx, sy, sz))} minecraft:dripstone_block")
        reset.append(f"setblock {w((sx, sy - 1, sz))} minecraft:pointed_dripstone[thickness=base,vertical_direction=down]")
        reset.append(f"setblock {w((sx, sy - 2, sz))} minecraft:pointed_dripstone[thickness=tip,vertical_direction=down]")
    for t in ("T1", "T2"):
        (a, b, c), (d_, e, f) = plage(PONT[t])
        reset.append(f"fill {w((a, b, c))} {w((d_, e, f))} minecraft:spruce_planks")
    for p in BALISES.values():
        reset.append(f"setblock {w(p)} minecraft:chiseled_stone_bricks")
    reset += ["scoreboard objectives add temple dummy"] + [f"scoreboard players set {v_} temple 0" for v_ in ("#se", "#ss", "#porte", "#t1", "#t2", "#fe", "#fw", "#fp")]
    reset += [f"clear @a {CLE}", 'tellraw @a {"text":"Temple remis a zero.","color":"gray"}']
    (FN / "temple_reset.mcfunction").write_text("\n".join(reset) + "\n", encoding="utf-8")
conf = {"_marque": "[claude-build]", "taille": [SX, SY, SZ], "origine": ORIGINE, "zones": ZONES, "portes": PORTES, "ancrages": ANCRAGES,
        "chutes": {k: v[0:2] for k, v in CHUTES.items()}, "points": POINTS, "cachees": {k: v["boite"] for k, v in CACHEES.items()}, "cachees_detail": CACHEES,
        "leviers": LEVIERS, "ordre_enigme": ORDRE_ENIGME, "couleurs": COULEURS, "dalles": DALLES, "chaine_p": CHAINE_P, "chaine_g": CHAINE_G,
        "entree": [65, 83, 62],
        "plateformes": {n_: {"bbox": PLATS[n_][:4], "dessus": PLATS[n_][4], "boss": list(BOSS[n_]) if n_ in BOSS else None, "cases": len(PC[n_]), "dessous": BAS[n_]} for n_ in PLATS},
        "ancrages_boss": ANCR_B, "aretes": EDGES, "zone_chute": [list(ZB), list(ZB_D)], "debut_boss": list(POINTS["S12"]),
        "cibles": {**{k: list(v) for k, v in POINTS.items()}, **{k: list(v[:3]) for k, v in LEVIERS.items()}, "bouton_H3": BOUTON_H3, "bouton_H8": BOUTON_H8,
                   **{"socle_" + k: v for k, v in SOCLES.items()}, "plaque_cle": PLAQUE_CLE, "serrure": CLE_ZONE, "plaque_idole": PLAQUE_IDOLE,
                   **{f"dalle{i + 1}": p for i, p in enumerate(PLAQUES_E)}, **{f"H{k}": v["coffre"] for k, v in CACHEES.items()}, **{"B" + n_[1:]: list(v) for n_, v in BOSS.items()}}}
np.savez_compressed(RACINE / "exports" / f"{NOM}_vide.npz", C=np.packbits(C.ravel()), taille=np.array([SX, SY, SZ]))     # masque des volumes creux, pour le verificateur
(RACINE / "exports" / f"{NOM}_cibles.json").write_text(json.dumps(conf, ensure_ascii=False), encoding="utf-8")
print(f"Ecrit : {n_total} blocs | commandes {len(CMD)} | portes {list(PORTES)} | ancrages {len(ANCRAGES)}")
