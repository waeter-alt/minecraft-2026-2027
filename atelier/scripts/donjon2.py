# [claude-build]
"""
donjon2.py - Donjon n°1 refait, « La Gorge suspendue » (2026-11-09) : 10 sections, ~31 min, grappin 2.2 a balancier.
Le crochet ne tient que sur les points d'ancrage (grappin:ancrage), places seulement la ou le balancier fait partie du parcours.
Sections fermees, separees par la roche, reliees par des portes a mecanisme (blocs de commande). 8 salles cachees vides.
Monde « Donjon 1 » (dossier Nouveau monde (1)), superflat, sol Y-61. Structure 160 x 120 x 176 posee en -75 -61 -85.
  .venv\\Scripts\\python.exe sources\\donjon2.py
En jeu : /reload puis /function atelier:donjon_pose (demolit l'ancien donjon, pose le nouveau). Remise a zero : /function atelier:donjon_reset
Controle : sources\\verif_donjon2.py
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
ap.add_argument("--monde", default="Nouveau monde (1)")
args = ap.parse_args()
RACINE = Path(__file__).resolve().parent.parent
SAVE = RACINE / "launcher/instances/Build-1.21.1/minecraft/saves" / args.monde
NOM = "donjon2"
SX, SY, SZ = 160, 120, 176
ORIGINE = (-75, -61, -85)
ANCIEN = ("donjon_grappin", (-50, -61, -70))          # ancien donjon a demolir


def S(n, **p):
    n = n if ":" in n else "minecraft:" + n
    return (n, tuple(sorted((k, str(v).lower() if isinstance(v, bool) else str(v)) for k, v in p.items())))


AIR, EAU = S("air"), S("water", level=0)
PIERRE, ANDE, MCB, TUF, TERRE, RAC = S("stone"), S("andesite"), S("mossy_cobblestone"), S("tuff"), S("dirt"), S("rooted_dirt")
MSB, CSB, SB, CHB = S("mossy_stone_bricks"), S("cracked_stone_bricks"), S("stone_bricks"), S("chiseled_stone_bricks")
MOUSSE, TAPIS, FOUG = S("moss_block"), S("moss_carpet"), S("fern")
LOG, BOIS = S("jungle_log", axis="y"), S("jungle_wood", axis="y")
FEUILLE = S("jungle_leaves", persistent=True, distance=1, waterlogged=False)
AZF = S("azalea_leaves", persistent=True, distance=1, waterlogged=False)
MRAC, RACP = S("mangrove_roots", waterlogged=False), S("hanging_roots", waterlogged=False)
CHAMP, LUM = S("shroomlight"), S("light", level=15, waterlogged=False)
ANC = S("grappin:ancrage")
LANT, LANT_P = S("lantern", hanging=False, waterlogged=False), S("lantern", hanging=True, waterlogged=False)
CHAINE = S("chain", axis="y", waterlogged=False)
TOILE, OS = S("cobweb"), S("bone_block", axis="y")
BARRES = S("iron_bars", waterlogged=False)
PLANCHE = S("spruce_planks")
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
    """Roche naturelle par grandes plaques (style de la Gorge suspendue)."""
    b = bruit(x + .7 * y, z - .5 * y, 7, 30 + g)
    c = bruit(x - .4 * y, z + .8 * y, 9, 40 + g)
    if b < .22:
        return ANDE
    if b > .8:
        return TUF
    if c > .72:
        return MCB
    return PIERRE


def ruine(x, y, z, g=0):
    """Maconnerie de l'expedition : briques de pierre usees (moussues, craquelees) par plaques."""
    b = bruit(x + .5 * y, z + .3 * y, 4, 80 + g)
    return MSB if b < .38 else CSB if b > .74 else SB


# ====================================================================== 1. volumes creux : salles, tunnels, salles cachees
C = np.zeros((SX, SY, SZ), dtype=bool)
ZONES = {}                                             # nom -> boite (x0, x1, y0, y1, z0, z1) pour les controles et les retours de chute


def salle(nom_, x0, x1, z0, z1, ybas, yhaut, g, voute=6, rond=4.0):
    """Salle naturelle : contour en super-ellipse bosselee, plafond en voute."""
    cx, cz, a, c = (x0 + x1) / 2, (z0 + z1) / 2, (x1 - x0) / 2, (z1 - z0) / 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            e = (abs(x + .5 - cx) / a) ** rond + (abs(z + .5 - cz) / c) ** rond
            if e < 1.0 + .25 * (bruit(x, z, 6, g) - .5):
                haut = int(yhaut - voute * min(1.0, e))
                C[x, ybas:haut + 1, z] = True
    ZONES[nom_] = (x0, x1, ybas, yhaut, z0, z1)


def boite(x0, x1, y0, y1, z0, z1):
    C[x0:x1 + 1, y0:y1 + 1, z0:z1 + 1] = True


salle("S1", 4, 22, 72, 92, 7, 22, 1, voute=4)          # Seuil des Racines (sol 6)
for x in range(7):                                    # entree depuis le dehors (au sol), escalier vers le sol 6
    boite(x, x, min(x, 6) + 1, min(x, 6) + 5, 80, 82)
boite(9, 18, 1, 6, 73, 91)                            # le fosse du tutoriel (eau au fond)
for k in range(15):                                   # escalier S1 -> S2 (de 6 a 20)
    boite(22 + k, 22 + k, 7 + min(k, 14), 11 + min(k, 14), 80, 82)
salle("S2", 36, 56, 60, 100, 21, 34, 2, voute=3)       # Galerie des Echos (sol 20)
boite(36, 40, 21, 25, 80, 82)                         # haut de l'escalier, dans la galerie
boite(52, 64, 21, 24, 79, 81)                         # tunnel vers le gouffre (grille G1)
salle("S3", 60, 106, 42, 122, 1, 64, 3, voute=8)       # Gouffre de l'Arbre-Mere (fosse)
boite(94, 98, 45, 48, 34, 48)                         # passage gouffre -> cascade (ouvert)
salle("S4", 60, 106, 12, 38, 26, 62, 4, voute=6)       # La Cascade (bassin)
boite(100, 118, 49, 52, 18, 20)                       # tunnel cascade -> jardin (grille G2)
salle("S5", 112, 146, 8, 52, 49, 66, 5, voute=4)       # Jardin des Symboles (sol 48)
boite(128, 130, 49, 52, 46, 62)                       # tunnel jardin -> puits (porte G3)
salle("S6", 118, 142, 58, 80, 14, 52, 6, voute=2, rond=3.0)   # Cle de l'Arbre (puits, sol 13)
boite(112, 115, 49, 52, 38, 96)                       # couloir jardin -> salle des lianes (ouvert)
boite(112, 121, 49, 52, 38, 44)
boite(112, 122, 49, 52, 92, 96)
salle("S7", 112, 146, 86, 124, 49, 68, 7, voute=6)     # Salle des Lianes (sol 48)
boite(114, 116, 49, 52, 112, 148)                     # tunnel salle des lianes -> ponts (porte a cle G4)
boite(98, 116, 49, 52, 145, 148)
salle("S8", 60, 106, 130, 164, 21, 64, 8, voute=8)     # Ponts Brises (fosse, ponts a 48)
boite(50, 66, 49, 52, 146, 148)                       # passage ponts -> ascension (ouvert)
salle("S9", 22, 56, 120, 164, 49, 100, 9, voute=4)     # Ascension des Racines (sol 48, eau)
boite(30, 34, 89, 92, 114, 126)                       # passage ascension -> salle du Gardien
salle("S10", 4, 56, 96, 116, 89, 114, 10, voute=8)     # Salle du Gardien (sol 88)
# salles cachees (au moins 7 x 7 x 4 a l'interieur)
CACHEES = {}


def cachee(n, x0, x1, y0, y1, z0, z1, acces, coffre, mobs):
    boite(x0, x1, y0, y1, z0, z1)
    CACHEES[n] = {"boite": (x0, x1, y0, y1, z0, z1), "acces": acces, "coffre": coffre, "mobs": mobs}


cachee(1, 6, 14, 7, 11, 95, 103, "Seuil des Racines : rideau de racines pendantes au fond de la salle, côté nord (on passe à travers)", (7, 7, 102), (10, 7, 99))
boite(9, 11, 7, 9, 92, 95)
cachee(2, 36, 45, 13, 17, 86, 95, "Galerie des Échos : trappe dans le sol d'une alcôve, échelle", (37, 13, 94), (41, 13, 90))
boite(40, 40, 13, 19, 85, 85)
cachee(3, 108, 116, 35, 39, 66, 74, "Gouffre : ancrage caché dans une niche de la paroi est, qu'on ne voit que depuis la fin de la chaîne d'ancrages", (115, 35, 73), (112, 35, 70))
boite(104, 108, 36, 39, 69, 71)
cachee(4, 60, 68, 45, 49, 3, 10, "Cascade : derrière la chute d'eau, au fond de l'alcôve du bouton", (61, 45, 4), (64, 45, 6))
boite(63, 65, 45, 47, 9, 12)
boite(60, 68, 45, 49, 12, 17)                         # alcove du bouton, derriere la cascade
cachee(5, 148, 156, 49, 53, 28, 38, "Jardin : mur de bibliothèques qui s'ouvre avec un levier caché derrière les lianes d'un pilier", (155, 49, 37), (152, 49, 33))
boite(146, 148, 49, 51, 32, 34)
cachee(6, 144, 152, 14, 18, 62, 72, "Puits de la clé : bouton caché sous un champignon géant, en bas du puits", (151, 14, 71), (148, 14, 67))
boite(142, 144, 14, 16, 66, 68)
cachee(7, 125, 135, 71, 75, 100, 110, "Salle des Lianes : ancrage dans la voûte, au-dessus d'un trou ; on remonte au treuil", (134, 71, 109), (128, 71, 103))
boite(129, 130, 66, 70, 104, 105)
cachee(8, 36, 46, 58, 62, 166, 172, "Ascension : rideau de lianes qu'on escalade, paroi sud", (37, 58, 171), (41, 58, 169))
boite(40, 41, 58, 60, 163, 166)
print("[1] volumes :", int(C.sum()), "cases creuses", flush=True)

# ====================================================================== 2. coquille de roche (4 blocs), mousse et lichen
d = C.copy()
for _ in range(4):
    n = d.copy()
    n[1:] |= d[:-1]
    n[:-1] |= d[1:]
    n[:, 1:] |= d[:, :-1]
    n[:, :-1] |= d[:, 1:]
    n[:, :, 1:] |= d[:, :, :-1]
    n[:, :, :-1] |= d[:, :, 1:]
    d = n
COQUE = d & ~C
for x, y, z in zip(*np.nonzero(COQUE)):
    put(x, y, z, roche(x, y, z))
bord = COQUE.copy()
bord[:] = False
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


# ====================================================================== 3. le parcours
PORTES, LEVIERS, ANCRAGES, CHUTES, POINTS = {}, {}, [], {}, {}


def porte(n, cases, s):
    PORTES[n] = {"cases": [list(c) for c in cases], "bloc": s[0], "props": dict(s[1])}
    for c in cases:
        put(*c, s)


def ancrage(n, x, y, z, corde=True):
    """Point d'ancrage pendu au plafond par une corde de racines (ou scelle dans une paroi si corde=False)."""
    put(x, y, z, ANC)
    ANCRAGES.append((n, x, y, z))
    if corde:
        k = y + 1
        while k < SY and vide((x, k, z)):
            put(x, k, z, MRAC if (k - y) % 5 else CHAINE)
            k += 1


# --- S1 Seuil des Racines : sol 6, fosse d'eau du tutoriel (x 9..18), 3 ancrages, echelle de secours
for x in range(0, 7):                                 # escalier d'entree (du sol du monde au sol 6)
    f = min(6, x)
    for z in range(80, 83):
        for y in range(0, f + 1):
            sol(x, y, z, MSB if y == f else None)
for x in range(4, 23):
    for z in range(72, 93):
        if C[x, 7, z] and not (9 <= x <= 18):
            put(x, 6, z, MOUSSE if bruit(x, z, 4, 11) > .4 else RAC)
for x in range(9, 19):
    for z in range(73, 92):
        for y in (1, 2):
            put(x, y, z, EAU)
        put(x, 0, z, S("gravel"))
for y in range(1, 7):
    put(9, y, 82, S("ladder", facing="east", waterlogged=False) if y > 2 else S("ladder", facing="east", waterlogged=True))
for k, x in enumerate((11, 14, 17)):
    ancrage(f"T{k + 1}", x, 17, 82 + (k % 2) * 2 - 1)
POINTS["S1"] = (3, 7, 81)
# --- escalier S1 -> S2
for k in range(15):
    for z in range(80, 83):
        put(22 + k, 6 + min(k, 14), z, S("mossy_stone_brick_stairs", facing="east", half="bottom", shape="straight", waterlogged=False) if k < 14 else MSB)
        for y in range(1, 6 + min(k, 14)):
            if (22 + k, y, z) not in blocs:
                put(22 + k, y, z, ruine(22 + k, y, z))
# --- S2 Galerie des Echos : sol 20, 3 leviers, corniche haute (ancrage), grille G1
for x in range(36, 57):
    for z in range(60, 101):
        if C[x, 21, z]:
            put(x, 20, z, ruine(x, 20, z) if (x + z) % 9 else MOUSSE)
for x in range(51, 57):                               # corniche haute (sol 28), murs lisses dessous
    for z in range(94, 100):
        if C[x, 28, z]:
            for y in range(21, 29):
                put(x, y, z, ruine(x, y, z))
ancrage("G", 47, 32, 96)
LEVIERS["L1"] = (55, 22, 77, "west")                  # a cote de la grille, en vue
LEVIERS["L2"] = (37, 22, 64, "east")                  # au fond d'une alcove, sous des lianes
LEVIERS["L3"] = (53, 29, 98, "north")                 # sur la corniche haute
for (x, y, z, f) in LEVIERS.values():
    put(x, y, z, S("lever", face="floor" if f == "north" else "wall", facing=f, powered=False))
for y in range(21, 26):                               # lianes qui cachent le levier L2
    for z in (63, 64, 65):
        if vide((37, y, z)) and (y, z) != (22, 64):
            put(37, y, z, S("vine", east=False, north=False, south=False, up=False, west=True))
porte("G1", [(58, y, z) for y in range(21, 25) for z in range(79, 82)], BARRES)
for z in range(79, 82):
    for x in range(52, 65):
        put(x, 20, z, MSB)
POINTS["S2"] = (40, 21, 81)
# --- S3 Gouffre de l'Arbre-Mere : fosse d'eau, corniche d'entree (20), Arbre-Mere, 8 ancrages, corniche de sortie (44)
for x in range(60, 107):
    for z in range(42, 123):
        if C[x, 2, z]:
            put(x, 0, z, S("gravel"))
            for y in (1, 2):
                put(x, y, z, EAU)
for x in range(60, 67):                               # corniche d'entree, accrochee a la paroi ouest
    for z in range(75, 86):
        if C[x, 20, z] or x <= 61:
            for y in range(14, 21):
                sol(x, y, z, MOUSSE if y == 20 else None)
TX, TZ = 84, 84
for y in range(1, 70):                                # l'Arbre-Mere, du fond jusque dans la voute, sans plateforme
    rt = 7.0 + (3.0 if y < 6 else 0.0)
    for x in range(TX - 11, TX + 12):
        for z in range(TZ - 11, TZ + 12):
            if math.hypot(x + .5 - TX, z + .5 - TZ) < rt + .8 * (bruit(x + y, z, 3, 12) - .5) and (C[x, y, z] or (x, y, z) in blocs):
                put(x, y, z, BOIS if math.hypot(x + .5 - TX, z + .5 - TZ) > rt - 1.3 else LOG)
CHAINE_S3 = [(70, 36, 77), (72, 38, 68), (70, 40, 59), (76, 41, 51), (85, 42, 48), (94, 43, 52), (100, 45, 60), (97, 50, 52)]
for k, (x, y, z) in enumerate(CHAINE_S3):
    ancrage(f"A{k + 1}", x, y, z)
for x in range(90, 101):                              # corniche de sortie (sol 44), accrochee a la paroi nord
    for z in range(42, 47):
        if C[x, 44, z] or z <= 43:
            for y in range(38, 45):
                sol(x, y, z, MOUSSE if y == 44 else None)
for x in range(94, 99):
    for z in range(36, 45):
        put(x, 44, z, MSB)
ancrage("H3", 105, 40, 70, corde=False)               # ancrage cache dans la niche de la salle cachee 3
CHUTES["S3"] = ((60, 1, 42), (46, 13, 80), (63, 21, 80))
POINTS["S3"] = (63, 21, 80)
# --- S4 La Cascade : bassin (26-27), corniche de depart (44), parkour, 2 ancrages, alcove du bouton derriere l'eau, porte G2
for x in range(60, 107):
    for z in range(12, 39):
        if C[x, 27, z]:
            put(x, 25, z, S("gravel"))
            for y in (26, 27):
                put(x, y, z, EAU)


def pilier_s4(x, z, top, r=1):
    for xx in range(x, x + 1 + r):
        for zz in range(z, z + 1 + r):
            for y in range(26, top + 1):
                put(xx, y, zz, MOUSSE if y == top else MCB if (xx + y) % 3 else roche(xx, y, zz))


for x in range(90, 101):                              # corniche de depart et marches vers la porte G2
    for z in range(30, 39):
        if C[x, 44, z] or z >= 37:
            for y in range(36, 45):
                sol(x, y, z, MOUSSE if y == 44 else None)
for x in range(100, 107):
    for z in range(17, 31):
        if C[x, 48, z] or x >= 105:
            for y in range(36, 49):
                sol(x, y, z, MSB if y == 48 else None)
for k in range(4):                                    # marches de la corniche de depart (44) a la corniche de la porte (48)
    for z in range(28, 31):
        for y in range(36, 46 + k):
            put(96 + k, y, z, MCB)
PARKOUR = [(87, 45, 33), (84, 45, 30), (81, 46, 27), (78, 46, 24), (78, 47, 20), (81, 47, 17), (84, 48, 15), (87, 48, 18)]
# le parcours revient sur lui-meme : 8 sauts, puis 2 balanciers vers l'alcove de la cascade a l'ouest
for (x, y, z) in PARKOUR:
    pilier_s4(x, z, y)
ancrage("C1", 76, 56, 16)
ancrage("C2", 69, 56, 18)
for x in range(60, 68):                               # alcove du bouton derriere la cascade (sol 44)
    for z in range(12, 17):
        if C[x, 45, z] or z <= 13:
            for y in range(36, 45):
                sol(x, y, z, MOUSSE if y == 44 else None)
for y in range(46, 49):                               # fond de l'alcove ; la salle cachee 4 est derriere
    for x in (62, 66):
        put(x, y, 12, MSB)
BOUTON_G2 = (64, 46, 12)
put(64, 46, 11, MSB)
put(*BOUTON_G2, S("stone_button", face="wall", facing="south", powered=False))
CASC = (64, 17)
yc = 45
while yc < SY - 1 and C[CASC[0], yc, CASC[1]]:
    yc += 1
put(CASC[0], yc, CASC[1], EAU)                        # la source de la cascade, dans la voute ; l'eau tombe devant l'alcove
porte("G2", [(109, y, z) for y in range(49, 52) for z in range(18, 21)], BARRES)
for x in range(100, 119):
    for z in range(18, 21):
        put(x, 48, z, MSB)
CHUTES["S4"] = ((60, 26, 12), (46, 10, 26), (96, 45, 36))
POINTS["S4"] = (96, 45, 36)
# --- S5 Jardin des Symboles : sol 48, 4 socles a bouton, fresques, porte G3 vers le puits, couloir vers la salle des lianes
for x in range(112, 147):
    for z in range(8, 53):
        if C[x, 49, z]:
            put(x, 48, z, MOUSSE if bruit(x, z, 5, 51) > .35 else S("grass_block", snowy=False))
            v = h01(x, 48, z, 52)
            if v < .1:
                put(x, 49, z, FOUG)
            elif v < .14:
                put(x, 49, z, S("flowering_azalea"))
SYMBOLES = {                                          # motifs 5 x 5 (ligne du haut d'abord) ; # = couleur 1, + = couleur 2
    "racine": ("brown_terracotta", "dirt", ["..#..", "..#..", ".###.", "#.#.#", "#...#"]),
    "goutte": ("blue_terracotta", "light_blue_terracotta", ["..#..", ".###.", "##+##", "#+++#", ".###."]),
    "oeil": ("black_terracotta", "white_terracotta", [".+++.", "+###+", "+#+#+", "+###+", ".+++."]),
    "feuille": ("green_terracotta", "lime_terracotta", ["...##", "..#+#", ".#+#.", "#+#..", "##..."]),
}
ORDRE_ENIGME = ["oeil", "racine", "feuille", "goutte"]   # rang = nombre de lanternes sous la fresque


def fresque(sym, x0, y0, z0, axe, rang):
    """Fresque 5 x 5 encadree de briques sculptees, et 'rang' lanternes posees dessous."""
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
for k, sym in enumerate(["feuille", "oeil", "goutte", "racine"]):      # ordre des socles, de gauche a droite (pas celui de l'enigme)
    x = 118 + 7 * k
    fresque(sym, x, 51, 9, "x", 0)
    for y in (49, 50):
        put(x + 2, y, 16, S("polished_andesite"))
    SOCLES[sym] = (x + 2, 51, 16)
    put(x + 2, 51, 16, S("stone_button", face="floor", facing="north", powered=False))
porte("G3", [(x, y, 55) for x in range(128, 131) for y in range(49, 52)], MSB)
for x in range(128, 131):
    for z in range(46, 59):
        put(x, 48, z, MSB)
for x in range(112, 116):
    for z in range(38, 97):
        put(x, 48, z, MSB if (x + z) % 5 else MOUSSE)
LEVIER_H5 = (141, 50, 43, "west")
for y in range(49, 55):                               # pilier aux lianes qui cache le levier de la salle cachee 5
    for z in (42, 43, 44):
        put(142, y, z, MSB)
put(*LEVIER_H5[:3], S("lever", face="wall", facing="west", powered=False))
for y in range(50, 54):
    for z in (42, 44):
        if vide((141, y, z)):
            put(141, y, z, S("vine", east=True, north=False, south=False, up=False, west=False))
put(141, 51, 43, S("vine", east=True, north=False, south=False, up=False, west=False))
porte("H5", [(147, y, z) for y in range(49, 52) for z in range(32, 35)], S("bookshelf"))
for z in range(26, 41):                               # bibliotheque autour du passage secret
    for y in range(49, 53):
        if (146, y, z) not in blocs and C[146, y, z] is not None:
            put(146, y, z, S("bookshelf") if 3 <= (z - 26) <= 12 else MSB)
for z in range(32, 35):
    for y in range(49, 52):
        blocs.pop((146, y, z), None)
POINTS["S5"] = (115, 49, 19)
# --- S6 Cle de l'Arbre : puits du sol 48 au sol 13, bassin d'eau en bas, sanctuaire, 3 ancrages pour remonter
for x in range(118, 143):
    for z in range(58, 81):
        if C[x, 14, z]:
            put(x, 13, z, MOUSSE)
            if 57 <= z <= 71 and x <= 134:
                for y in (12, 13):                    # bassin creuse dans le sol (l eau ne deborde pas)
                    put(x, y, z, EAU)
                put(x, 11, z, S("gravel"))
for x in range(126, 135):                             # sanctuaire de la cle
    for z in range(73, 79):
        put(x, 13, z, CHB if (x + z) % 2 else S("polished_tuff"))
PLAQUE_CLE = (130, 14, 76)
put(*PLAQUE_CLE, S("stone_pressure_plate", powered=False))
for (x, z) in ((127, 77), (133, 77)):
    for y in (14, 15):
        put(x, y, z, CHB)
    put(x, 16, z, LANT)
ancrage("K1", 121, 28, 69, corde=False)
ancrage("K2", 140, 38, 65, corde=False)
ancrage("K3", 124, 50, 61, corde=False)
for (n_, x, y, z) in ANCRAGES[-3:]:                   # chaque ancrage du puits est scelle dans un massif de roche
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            for dy in (1, 2):
                if (x + dx, y + dy, z + dz) not in blocs:
                    put(x + dx, y + dy, z + dz, roche(x, y, z))
BOUTON_H6 = (141, 14, 70)
for y in (14, 15, 16):                                # champignon geant ; le bouton est dessous
    for z in range(68, 73):
        put(139, y, z, S("mushroom_stem", up=False, down=False, north=True, south=True, east=True, west=True)) if (y < 16 and z == 70) else None
for x in range(137, 142):
    for z in range(67, 74):
        put(x, 17, z, S("red_mushroom_block", up=True, down=True, north=True, south=True, east=True, west=True))
put(*BOUTON_H6, S("stone_button", face="floor", facing="west", powered=False))
porte("H6", [(143, y, z) for y in range(14, 17) for z in range(66, 69)], MCB)
POINTS["S6"] = (127, 14, 75)
# --- S7 Salle des Lianes : sol 48, piliers, porte a cle G4
for x in range(112, 147):
    for z in range(86, 125):
        if C[x, 49, z]:
            put(x, 48, z, ruine(x, 48, z) if bruit(x, z, 6, 71) > .45 else MOUSSE)
for k, (cx, cz, r) in enumerate(((120, 96, 1.6), (138, 99, 2.2), (124, 116, 2.0), (140, 117, 1.5))):
    for y in range(49, 69):
        for x in range(int(cx - 3), int(cx + 4)):
            for z in range(int(cz - 3), int(cz + 4)):
                if math.hypot(x + .5 - cx, z + .5 - cz) < r and C[x, y, z]:
                    put(x, y, z, ruine(x, y, z, k))
ancrage("H7", 129, 75, 104, corde=False)              # dans la salle cachee 7, au-dessus du trou de la voute
for x in range(129, 131):
    for z in range(104, 106):
        blocs.pop((x, 70, z), None)
porte("G4", [(x, y, 127) for x in range(114, 117) for y in range(49, 52)], BARRES)
for x in range(98, 117):
    for z in range(112, 149):
        if C[x, 49, z] and (x >= 113 or z >= 145):
            put(x, 48, z, MSB)
put(117, 50, 126, S("lodestone"))                     # la serrure
CLE_ZONE = (113, 49, 121)
POINTS["S7"] = (113, 49, 90)
# --- S8 Ponts Brises : passerelle a 48 au-dessus d'une fosse d'eau ; pieges ; un ancrage pour la breche
for x in range(60, 107):
    for z in range(130, 165):
        if C[x, 22, z]:
            put(x, 20, z, S("gravel"))
            for y in (21, 22):
                put(x, y, z, EAU)
for x in range(98, 107):                              # plateforme de depart
    for z in range(143, 152):
        if C[x, 48, z] or x >= 105:
            for y in range(42, 49):
                sol(x, y, z, MSB if y == 48 else None)
PONT = {}
for x in range(60, 98):
    if 71 <= x <= 79:                                 # la breche : il faut l'ancrage
        continue
    for z in range(146, 149):
        sorte = "T1" if 86 <= x <= 89 else "T2" if 61 <= x <= 63 else "plein"
        put(x, 48, z, PLANCHE if sorte != "plein" else S("spruce_planks") if (x + z) % 4 else S("stripped_spruce_log", axis="x"))
        PONT.setdefault(sorte, []).append((x, 48, z))
for x in range(60, 98, 6):                            # chaines qui tiennent la passerelle (decor)
    if 71 <= x <= 79:
        continue
    for z in (145, 149):
        y = 49
        while y < SY - 1 and C[x, y, z] and (x, y, z) not in blocs:
            put(x, y, z, CHAINE)
            y += 1
ancrage("P1", 75, 58, 147)
PLAQUES_POISON = [(82, 49, 147), (84, 49, 146), (66, 49, 148)]
for p in PLAQUES_POISON:
    put(*p, S("stone_pressure_plate", powered=False))
STALACTITES = {}
for n_, (x, z) in (("D1", (93, 147)), ("D2", (67, 147))):
    for xx in range(x - 2, x + 3):                    # surplomb de roche au-dessus du piege
        for zz in range(145, 150):
            for y in range(54, 58):
                if C[xx, y, zz]:
                    put(xx, y, zz, roche(xx, y, zz))
    support = (x, 54, z)
    put(*support, S("dripstone_block"))
    put(x, 53, z, S("pointed_dripstone", thickness="base", vertical_direction="down", waterlogged=False))
    put(x, 52, z, S("pointed_dripstone", thickness="tip", vertical_direction="down", waterlogged=False))
    STALACTITES[n_] = {"support": support, "zone": (x - 1, 49, 146)}
CHUTES["S8"] = ((60, 21, 130), (46, 19, 34), (103, 49, 147))
POINTS["S8"] = (103, 49, 147)
for x in range(50, 67):
    for z in range(146, 149):
        if (x, 48, z) not in PONT["T2"]:              # la dalle piegee T2 reste en planches
            put(x, 48, z, MSB)
for z in range(146, 149):                             # marche entre la passerelle (48) et la plate-forme d'arrivee (50)
    put(57, 49, z, MSB)
    put(58, 49, z, MSB)
# --- S9 Ascension des Racines : sol d'eau (49-50), corniches de repos, 5 ancrages, corniche de sortie (88)
for x in range(22, 57):
    for z in range(120, 165):
        if C[x, 49, z]:
            put(x, 48, z, S("gravel"))
            for y in (49, 50):
                put(x, y, z, EAU)
for x in range(51, 57):                               # plate-forme d'arrivee (sol 50, au-dessus de l'eau)
    for z in range(144, 151):
        put(x, 50, z, MSB)
        for y in (48, 49):
            put(x, y, z, MSB)


def corniche_paroi(x0, x1, z0, z1, top):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(top - 4, top + 1):
                sol(x, y, z, MOUSSE if y == top else None)


corniche_paroi(22, 26, 150, 156, 58)
corniche_paroi(44, 48, 128, 133, 72)
corniche_paroi(28, 36, 120, 123, 88)                  # corniche de sortie
for x in range(30, 35):
    for z in range(114, 123):
        put(x, 88, z, MSB)
CHAINE_S9 = [(45, 61, 149), (36, 65, 153), (30, 72, 145), (37, 78, 140), (40, 85, 128), (33, 94, 126)]
for k, (x, y, z) in enumerate(CHAINE_S9):
    ancrage(f"Z{k + 1}", x, y, z)
for x in (40, 41):                                    # lianes de la salle cachee 8 (on y grimpe), sur la paroi sud
    zm = max(z for z in range(150, SZ) if C[x, 55, z])
    for y in range(51, 58):
        for z in range(zm + 1, 167):
            if (x, y, z) not in blocs:
                put(x, y, z, roche(x, y, z))
        for z in range(150, zm + 1):
            if C[x, y, z] and (x, y, z + 1) in blocs:
                put(x, y, z, S("vine", east=False, north=False, south=True, up=False, west=False))
    boite(x, x, 58, 60, zm, 166)
POINTS["S9"] = (54, 51, 147)
# --- S10 Salle du Gardien : sol 88, colonnes de troncs, estrade
for x in range(4, 57):
    for z in range(96, 117):
        if C[x, 89, z]:
            put(x, 88, z, MOUSSE if bruit(x, z, 7, 91) > .3 else RAC)
for k, (cx, cz, r) in enumerate(((14, 100, 1.8), (46, 101, 2.2), (20, 112, 1.6), (40, 113, 2.0))):
    for y in range(89, SY - 1):
        if not C[int(cx), y, int(cz)]:
            break
        for x in range(int(cx - 3), int(cx + 4)):
            for z in range(int(cz - 3), int(cz + 4)):
                if math.hypot(x + .5 - cx, z + .5 - cz) < r and C[x, y, z]:
                    put(x, y, z, LOG if math.hypot(x + .5 - cx, z + .5 - cz) < r - .7 else BOIS)
for x in range(8, 17):                                # estrade du Gardien (ouest)
    for z in range(102, 111):
        put(x, 89, z, CHB if (x + z) % 2 else S("polished_tuff"))
POINTS["S10"] = (32, 89, 112)
# --- acces des salles cachees 1 et 2
for x in range(9, 12):                                # salle cachee 1 : rideau de lianes a baies devant l'ouverture
    put(x, 9, 93, S("cave_vines_plant", berries=x == 10))
    put(x, 8, 93, S("cave_vines_plant", berries=False))
    put(x, 7, 93, S("cave_vines", age=25, berries=x != 10))
    for y in range(7, 11):
        if (x, y, 92) not in blocs and not C[x, y + 1, 92]:
            pass
for y in range(14, 20):                               # salle cachee 2 : trappe dans le sol de l'alcove, echelle
    put(40, y, 85, S("ladder", facing="south", waterlogged=False))
put(40, 20, 85, S("spruce_trapdoor", facing="south", half="top", open=False, powered=False, waterlogged=False))
put(40, 21, 86, RACP) if vide((40, 21, 86)) and (40, 22, 86) in blocs else None
print("[3] parcours :", len(blocs), "| ancrages", len(ANCRAGES), flush=True)

# ====================================================================== 4. fresques des sections 1 a 4 (les symboles et leur rang)
FRESQUES = {"racine": (5, 9, 72, "x", (0, 1)), "goutte": (36, 23, 70, "z", (1, 0)), "oeil": (60, 23, 83, "z", (1, 0)), "feuille": (92, 47, 38, "x", (0, -1))}
for sym, (x0, y0, z0, axe, (ox, oz)) in FRESQUES.items():
    rang = ORDRE_ENIGME.index(sym) + 1
    for (x, y, z) in fresque(sym, x0, y0, z0, axe, rang):
        put(x + ox, y, z + oz, LANT)

# ====================================================================== 5. decor : voute, racines, lianes, toiles, ossements, mobilier, lumiere
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
        if v < .05:                                   # racines pendantes et lianes a baies, depuis les voutes
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
for (cx, y, cz, quoi) in [(6, 7, 76, "camp"), (20, 7, 88, "camp2"), (40, 21, 96, "os"), (52, 21, 64, "os"), (120, 49, 30, "banc"),
                          (138, 49, 36, "table"), (135, 14, 64, "os"), (125, 49, 112, "os"), (144, 49, 92, "os"), (100, 49, 145, "camp"),
                          (24, 89, 106, "os"), (52, 89, 100, "table")]:
    if quoi == "camp":                                # campement abandonne : table, chaises, lanterne, couverture
        put(cx, y, cz, S("crafting_table"))
        put(cx + 1, y, cz, S("spruce_stairs", facing="west", half="bottom", shape="straight", waterlogged=False))
        put(cx, y, cz + 1, S("brown_carpet"))
        put(cx + 1, y, cz + 1, S("brown_carpet"))
        put(cx - 1, y, cz, LANT)
    elif quoi == "camp2":
        put(cx, y, cz, S("fletching_table"))
        put(cx, y, cz - 1, S("cartography_table"))
        put(cx - 1, y, cz, S("white_carpet"))
    elif quoi == "os":                                # restes d'explorateurs
        put(cx, y, cz, OS)
        put(cx + 1, y, cz, S("bone_block", axis="x"))
        put(cx, y, cz + 1, S("skeleton_skull", rotation=int(h01(cx, y, cz, 5) * 16)))
        put(cx - 1, y, cz, TOILE)
    elif quoi == "banc":
        for k in range(3):
            put(cx + k, y, cz, S("spruce_stairs", facing="north", half="bottom", shape="straight", waterlogged=False))
    elif quoi == "table":
        put(cx, y, cz, S("spruce_slab", type="top", waterlogged=False))
        put(cx + 1, y, cz, S("spruce_slab", type="top", waterlogged=False))
        put(cx, y + 1, cz, S("candle", candles=3, lit=True, waterlogged=False))
        put(cx - 1, y, cz, S("spruce_stairs", facing="east", half="bottom", shape="straight", waterlogged=False))
    n_dec["mobilier"] += 1
n_lum = 0
for x in range(2, SX - 2, 9):                         # lumiere invisible partout (aucune apparition de monstre)
    for z in range(2, SZ - 2, 9):
        for y in range(2, SY - 2, 7):
            if C[x, y, z] and (x, y, z) not in blocs:
                put(x, y, z, LUM)
                n_lum += 1
for n_, (x0, x1, y0, y1, z0, z1) in {k: v["boite"] for k, v in CACHEES.items()}.items():
    put((x0 + x1) // 2, y1, (z0 + z1) // 2, LANT_P)   # une lanterne pendue par salle cachee
print("[5] decor :", dict(n_dec), "| lumieres", n_lum, flush=True)

# ====================================================================== 6. blocs de commande (deux chaines enterrees sous les sols du jardin et de la salle des lianes)
CHAINES = [(114, 45, 30), (114, 45, 105)]               # debut de chaque chaine (x, y, z), le long de +x
CMD = []


def rel(x, y, z):
    i = len(CMD)
    c = CHAINES[i // 32]
    px, py, pz = c[0] + i % 32, c[1], c[2]
    return f"~{x - px} ~{y - py} ~{z - pz}"


def plage(cases):
    xs, ys, zs = zip(*cases)
    return (min(xs), min(ys), min(zs)), (max(xs), max(ys), max(zs))


def fill(cases, bloc_):
    (a, b, c), (d_, e, f) = plage(cases)
    return f"fill {rel(a, b, c)} {rel(d_, e, f)} {bloc_}"


CLE = "minecraft:tripwire_hook[minecraft:custom_name='\"Clé de racine\"']"
CLE_DON = "minecraft:tripwire_hook[minecraft:custom_name='\"Clé de racine\"',minecraft:lore=['\"Ouvre la porte de la Salle des Lianes\"']]"
CMD.append("gamerule commandBlockOutput false")
CMD.append("gamerule doMobSpawning false")
CMD.append("scoreboard objectives add donjon dummy")
for v_ in ("#seq", "#porte", "#t1", "#t2"):
    CMD.append(f"scoreboard players add {v_} donjon 0")
ENTREE = (0, 1, 79)
CMD.append(f"execute positioned {rel(*ENTREE)} as @a[dx=6,dy=8,dz=4] unless items entity @s container.* grappin:grappin run give @s grappin:grappin")
# G1 : les trois leviers
l1, l2, l3 = LEVIERS["L1"][:3], LEVIERS["L2"][:3], LEVIERS["L3"][:3]
CMD.append(f"execute if block {rel(*l1)} minecraft:lever[powered=true] if block {rel(*l2)} minecraft:lever[powered=true] if block {rel(*l3)} minecraft:lever[powered=true] run "
           + fill([tuple(c) for c in PORTES["G1"]["cases"]], "minecraft:air"))
# G2 : le bouton derriere la cascade
CMD.append(f"execute if block {rel(*BOUTON_G2)} minecraft:stone_button[powered=true] run " + fill([tuple(c) for c in PORTES["G2"]["cases"]], "minecraft:air"))
# G3 : l'enigme des symboles (ordre : oeil, racine, feuille, goutte)
for k, sym in enumerate(ORDRE_ENIGME, 1):                # un socle presse trop tot remet a zero ; un socle deja compte ne fait rien
    b = rel(*SOCLES[sym])
    if k >= 2:
        CMD.append(f"execute if block {b} minecraft:stone_button[powered=true] if score #seq donjon matches ..{k - 2} "
                   f"positioned {b} run playsound minecraft:block.note_block.bass master @a[distance=..20] ~ ~ ~ 1 0.6")
        b = rel(*SOCLES[sym])
        CMD.append(f"execute if block {b} minecraft:stone_button[powered=true] if score #seq donjon matches ..{k - 2} run scoreboard players set #seq donjon 0")
    b = rel(*SOCLES[sym])
    CMD.append(f"execute if block {b} minecraft:stone_button[powered=true] if score #seq donjon matches {k - 1} run scoreboard players set #seq donjon {k}")
CMD.append(f"execute if score #seq donjon matches 4 positioned {rel(*SOCLES['oeil'])} run playsound minecraft:block.note_block.chime master @a[distance=..30] ~ ~ ~ 1 1")
CMD.append("execute if score #seq donjon matches 4 run " + fill([tuple(c) for c in PORTES["G3"]["cases"]], "minecraft:air"))
CMD.append("execute if score #seq donjon matches 4 run scoreboard players set #seq donjon 5")
# la cle : le sanctuaire du puits la donne, la porte G4 la prend
CMD.append(f"execute positioned {rel(*PLAQUE_CLE)} as @a[distance=..1.5] unless items entity @s container.* {CLE} run give @s {CLE_DON} 1")
CMD.append(f"execute if score #porte donjon matches 0 positioned {rel(*CLE_ZONE)} as @a[dx=4,dy=3,dz=5] if items entity @s weapon.mainhand {CLE} run scoreboard players set #porte donjon 1")
CMD.append("execute if score #porte donjon matches 1 run " + fill([tuple(c) for c in PORTES["G4"]["cases"]], "minecraft:air"))
CMD.append(f"execute if score #porte donjon matches 1 positioned {rel(*CLE_ZONE)} as @a[dx=4,dy=3,dz=5] run clear @s {CLE} 1")
CMD.append("execute if score #porte donjon matches 1 run scoreboard players set #porte donjon 2")
CMD.append(f"execute if score #porte donjon matches 0 positioned {rel(*CLE_ZONE)} as @a[dx=4,dy=3,dz=5] unless items entity @s weapon.mainhand {CLE} run title @s actionbar "
           "{\"text\":\"La porte est fermée à clé (Clé de racine en main)\",\"color\":\"gold\"}")
# pieges des Ponts Brises : stalactites, dalles qui cedent, plaques empoisonnees
for n_, st in STALACTITES.items():
    sx, sy, sz = st["support"]
    zx, zy, zz = st["zone"]
    CMD.append(f"execute positioned {rel(zx, zy, zz)} if entity @a[dx=2,dy=2,dz=2] if block {rel(sx, sy, sz)} minecraft:dripstone_block run setblock {rel(sx, sy, sz)} minecraft:air")
for t in ("T1", "T2"):
    v_ = "#" + t.lower()
    (a, b, c), (d_, e, f) = plage(PONT[t])
    CMD.append(f"execute if score {v_} donjon matches 0 positioned {rel(a, b + 1, c)} if entity @a[dx={d_ - a},dy=1,dz={f - c}] run scoreboard players set {v_} donjon 50")
    CMD.append(f"execute if score {v_} donjon matches 1.. run scoreboard players remove {v_} donjon 1")
    CMD.append(f"execute if score {v_} donjon matches 45 positioned {rel(a, b, c)} run playsound minecraft:block.wood.break master @a[distance=..16] ~ ~ ~ 1 0.6")
    CMD.append(f"execute if score {v_} donjon matches 36 run " + fill(PONT[t], "minecraft:air"))
    CMD.append(f"execute if score {v_} donjon matches 1 run " + fill(PONT[t], "minecraft:spruce_planks"))
for p in PLAQUES_POISON:
    CMD.append(f"execute if block {rel(*p)} minecraft:stone_pressure_plate[powered=true] positioned {rel(*p)} run effect give @a[distance=..1.5] minecraft:poison 4 1")
# retours de chute (gouffre, cascade, ponts)
for n_, ((x0, y0, z0), (dx, dy, dz), (tx, ty, tz)) in CHUTES.items():
    CMD.append(f"execute positioned {rel(x0, y0, z0)} as @a[dx={dx},dy={dy},dz={dz}] run tp @s ~{tx - x0} ~{ty - y0} ~{tz - z0}")
# salles cachees 5 et 6
CMD.append(f"execute if block {rel(*LEVIER_H5[:3])} minecraft:lever[powered=true] run " + fill([tuple(c) for c in PORTES["H5"]["cases"]], "minecraft:air"))
CMD.append(f"execute if block {rel(*BOUTON_H6)} minecraft:stone_button[powered=true] run " + fill([tuple(c) for c in PORTES["H6"]["cases"]], "minecraft:air"))
for i, c in enumerate(CMD):
    ch = CHAINES[i // 32]
    x, y, z = ch[0] + i % 32, ch[1], ch[2]
    assert not C[x, y, z] and not C[x, y + 1, z] and (x, y, z) in blocs, ("chaine hors de la roche", x, y, z)
    put(x, y, z, S("repeating_command_block" if i % 32 == 0 else "chain_command_block", conditional=False, facing="east"))
    NBT[(x, y, z)] = Compound({"id": String("minecraft:command_block"), "Command": String(c), "auto": Byte(1), "TrackOutput": Byte(0),
                               "conditionMet": Byte(0), "powered": Byte(0), "UpdateLastExecution": Byte(1)})
print("[6] commandes :", len(CMD), flush=True)

# ====================================================================== 7. controles
err = []
PASSE = {"air", "light", "water", "cave_vines", "cave_vines_plant", "hanging_roots", "vine", "moss_carpet", "fern", "flowering_azalea", "cobweb",
         "lantern", "candle", "lever", "stone_button", "stone_pressure_plate", "ladder", "brown_carpet", "white_carpet", "skeleton_skull"}
for (x, y, z), s in blocs.items():
    n = s[0].split(":")[1]
    if n == "water" and (x, z) != CASC:
        for q in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1), (x, y - 1, z)):
            if q not in blocs and not (0 <= q[0] < SX and 0 <= q[1] < SY and 0 <= q[2] < SZ and C[q] and q[1] > y):
                if not (0 <= q[0] < SX and 0 <= q[1] < SY and 0 <= q[2] < SZ) or not C[q]:
                    err.append(f"eau qui fuit {(x, y, z)} -> {q}")
                elif q[1] == y:
                    err.append(f"eau qui coule {(x, y, z)} -> {q}")
    if n in ("chest", "barrel", "hopper", "dropper", "dispenser", "spawner", "trapped_chest", "decorated_pot", "chiseled_bookshelf"):
        err.append(f"interdit {n} {(x, y, z)}")
SOLIDE_NON = PASSE | {"chain", "iron_bars", "spruce_trapdoor"}


def solide(q):
    return q in blocs and blocs[q][0].split(":")[1] not in SOLIDE_NON


DIRS = {"east": (1, 0, 0), "west": (-1, 0, 0), "south": (0, 0, 1), "north": (0, 0, -1)}
for (x, y, z), s in blocs.items():                    # chaque bloc accroche a bien son support
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
    elif n in ("stone_pressure_plate", "candle", "fern", "flowering_azalea", "brown_carpet", "white_carpet", "moss_carpet"):
        sup = [(0, -1, 0)]
    elif n == "lantern":
        sup = [(0, 1, 0)] if pr["hanging"] == "true" else [(0, -1, 0)]
    elif n == "hanging_roots":
        sup = [(0, 1, 0)]
    elif n in ("cave_vines", "cave_vines_plant"):
        q = (x, y + 1, z)
        if not (solide(q) or nom(q) == "cave_vines_plant"):
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
print("[controle] erreurs", len(err), Counter((e.split(" (")[0], e.split(") ")[0].split("(")[1].split(",")[1]) for e in err), err[:3], flush=True)
if err:
    raise SystemExit(1)


# ====================================================================== 8. ecriture : structure, structure de demolition, fonctions, fiche
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
    f.save(RACINE / "exports" / f"{nom_}.nbt")
    D = SAVE / "datapacks/atelier/data/atelier/structure"
    if D.exists():
        f.save(D / f"{nom_}.nbt")
    return len(liste)


n_total = ecrire(blocs, NBT, NOM, (SX, SY, SZ))
anc = nbtlib.load(RACINE / "sauvegardes" / "donjon_grappin_v1.nbt")
pal_a = [str(p["Name"]) for p in anc["palette"]]
vide_a = {tuple(int(v) for v in b["pos"]): AIR for b in anc["blocks"] if pal_a[int(b["state"])] != "minecraft:air"}
n_vide = ecrire(vide_a, {}, "donjon_vide_v1", tuple(int(v) for v in anc["size"]))
FN = SAVE / "datapacks/atelier/data/atelier/function"
FN.mkdir(parents=True, exist_ok=True)


def w(p):
    return (ORIGINE[0] + p[0], ORIGINE[1] + p[1], ORIGINE[2] + p[2])


def ws(p):
    return " ".join(str(v) for v in w(p))


fl = f"forceload add {ORIGINE[0]} {ORIGINE[2]} {ORIGINE[0] + SX - 1} {ORIGINE[2] + SZ - 1}"
(FN / "donjon_pose.mcfunction").write_text("\n".join([
    "# [claude-build] Demolit l'ancien donjon et pose le nouveau (une seule fois, sans pause).", fl,
    'tellraw @a {"text":"Donjon : 15 secondes de chargement, puis le jeu va se figer un moment. Ne mets pas en pause.","color":"gold"}',
    "schedule function atelier:donjon_pose_2 300t"]) + "\n", encoding="utf-8")
(FN / "donjon_pose_2.mcfunction").write_text("\n".join([
    "# [claude-build] Demolition (air sur chaque bloc de l'ancien donjon), puis le nouveau donjon.",
    f"place template atelier:donjon_vide_v1 {ANCIEN[1][0]} {ANCIEN[1][1]} {ANCIEN[1][2]}", "kill @e[type=item]",
    f"place template atelier:{NOM} {ORIGINE[0]} {ORIGINE[1]} {ORIGINE[2]}", "function atelier:donjon_reset",
    "schedule function atelier:donjon_pose_3 100t"]) + "\n", encoding="utf-8")
(FN / "donjon_pose_3.mcfunction").write_text("\n".join([
    "# [claude-build] Nettoie et decharge.", "kill @e[type=item]", "forceload remove all",
    f'tellraw @a {{"text":"Le donjon est pret. Entree : /tp @s {ws((-2, 1, 81))} -90 0","color":"green"}}']) + "\n", encoding="utf-8")
reset = ["# [claude-build] Remet le donjon a zero : portes fermees, leviers baisses, enigme et cle remises, pieges rearmes."]
for n_, p in PORTES.items():
    props = ",".join(f"{k}={v}" for k, v in p["props"].items())
    (a, b, c), (d_, e, f) = plage([tuple(x) for x in p["cases"]])
    reset.append(f"fill {ws((a, b, c))} {ws((d_, e, f))} {p['bloc']}{'[' + props + ']' if props else ''}")
for n_, (x, y, z, f) in list(LEVIERS.items()) + [("H5", LEVIER_H5)]:
    face = "floor" if (n_ == "L3") else "wall"
    reset.append(f"setblock {ws((x, y, z))} minecraft:lever[face={face},facing={f},powered=false]")
for n_, st in STALACTITES.items():
    sx, sy, sz = st["support"]
    reset.append(f"setblock {ws((sx, sy, sz))} minecraft:dripstone_block")
    reset.append(f"setblock {ws((sx, sy - 1, sz))} minecraft:pointed_dripstone[thickness=base,vertical_direction=down]")
    reset.append(f"setblock {ws((sx, sy - 2, sz))} minecraft:pointed_dripstone[thickness=tip,vertical_direction=down]")
for t in ("T1", "T2"):
    (a, b, c), (d_, e, f) = plage(PONT[t])
    reset.append(f"fill {ws((a, b, c))} {ws((d_, e, f))} minecraft:spruce_planks")
reset += ["scoreboard objectives add donjon dummy", "scoreboard players set #seq donjon 0", "scoreboard players set #porte donjon 0",
          "scoreboard players set #t1 donjon 0", "scoreboard players set #t2 donjon 0", f"clear @a {CLE}",
          'tellraw @a {"text":"Donjon remis a zero.","color":"gray"}']
(FN / "donjon_reset.mcfunction").write_text("\n".join(reset) + "\n", encoding="utf-8")
conf = {"_marque": "[claude-build]", "taille": [SX, SY, SZ], "origine": ORIGINE, "zones": ZONES, "portes": PORTES, "ancrages": ANCRAGES,
        "chutes": {k: v[0:2] for k, v in CHUTES.items()}, "points": POINTS, "cachees": {k: v["boite"] for k, v in CACHEES.items()}, "cachees_detail": CACHEES, "leviers": LEVIERS, "ordre_enigme": ORDRE_ENIGME, "fresques": FRESQUES,
        "entree": [0, 1, 81], "chaine_s3": CHAINE_S3, "chaine_s9": CHAINE_S9,
        "cibles": {**{k: list(v) for k, v in POINTS.items()}, **{k: list(v[:3]) for k, v in LEVIERS.items()}, "BG2": BOUTON_G2,
                   **{"socle_" + k: v for k, v in SOCLES.items()}, "plaque_cle": PLAQUE_CLE, "serrure": CLE_ZONE, "levier_H5": LEVIER_H5[:3],
                   "bouton_H6": BOUTON_H6, **{f"H{k}": v["coffre"] for k, v in CACHEES.items()}}}
(RACINE / "exports" / f"{NOM}_cibles.json").write_text(json.dumps(conf, ensure_ascii=False), encoding="utf-8")
print(f"Ecrit : {n_total} blocs | demolition : {n_vide} | commandes {len(CMD)} | portes {list(PORTES)} | ancrages {len(ANCRAGES)}")
print("POSE : /reload puis /function atelier:donjon_pose")
