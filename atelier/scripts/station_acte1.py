# [claude-build]
"""station_acte1.py - Donjon de la Station (le donjon 2), actes I et II d'apres le dossier de conception (Donjon de la Station - dossier de conception.pdf, 2026-11-17) :
le pavillon d'entree sur la plaine, l'escalier de 24 marches, le Hall des Bassins (trois volants : le plongeon au grappin, le bassin de 50 m et son embuscade, la tour du toboggan au grappin),
le bassin d'essai (la bouche du Drain, a construire plus tard), le Vestiaire et son casier, le Sas (une vanne a trois temoins), et la Grande Cuve (60 de diametre, sol a 30, plafond a 96)
avec sa Coursive a 84, ses quatre bouches scellees (A, C, B, D : les secteurs viendront plus tard), ses colonnes de tuyaux, ses reperes HAUT / MEDIAN / BAS et ses temoins des pompes.
Coordonnees du gabarit (240 x 112 x 240) ; nord = z decroissant. Pur calcul : renvoie (blocs, infos) ; l'eau de la Cuve n'est pas dans la structure (la mise en eau la remplit)."""
import math
from functools import lru_cache

import station_vannes
from suspendu_outils import AIR, S, h01

SX, SY, SZ = 240, 112, 240
PLAINE = 108                                   # le bloc d'herbe de la plaine (monde y 46)
SOL = 84                                       # le sol du Hall, du Sas, du Vestiaire et de la Coursive
PLAFOND = 98                                   # le plafond du Hall (air de 85 a 97)
HALL = (60, 179, 158, 212)                     # interieur (x0, x1, z0, z1) : 120 x 55
VESTIAIRE = (14, 55, 163, 206)                 # 42 x 44
HAUT_VESTIAIRE = 7
COULOIR_V = (56, 59, 182, 186)                 # du Hall au Vestiaire
SAS = (117, 123, 129, 157)                     # 7 de large ; z 157 : le mur nord du Hall, ou est la vanne
HAUT_SAS = 5
PAVILLON = (113, 126, 228, 239)                # la maison de bassin sur la plaine
ESCALIER_X = (117, 122)
ESC_Z0 = 235                                   # la premiere marche (y PLAINE - 1), puis une par bloc vers le nord
NB_MARCHES = 24                                # de y 107 a y 84 : la derniere est au niveau du Hall
PLONGEON = (66, 89, 166, 189)                  # le bassin du plongeon : 24 x 24, creux de 12
FOND_PLONGEON = SOL - 12
PLATEFORMES = {"P1": ((69, 73, 169, 173), SOL - 8), "P2": ((82, 86, 169, 173), SOL - 4), "P3": ((75, 79, 178, 182), SOL)}       # la tour de plongeoirs : +4, +8, +12 au dessus du fond
BASSIN50 = (90, 139, 170, 185)                 # le bassin de 50 m, peu profond (3) : il debouche a l'ouest dans la fosse du plongeoir (le grand bain)
FOND50 = SOL - 3
TOUR = (150, 178)                              # la tour du toboggan, au bout est du bassin : noyau de rayon 4, corniches jusqu'a 8, la goulotte du toboggan autour (rayon 9,5 a 11,5)
R_NOYAU = 4.0
CORNICHES_TOUR = (("L1", 180, 86), ("L2", 270, 89), ("L3", 0, 91))       # azimut (0 = nord, 90 = est), sol
SOMMET_TOUR = 94
BASSIN_ESSAI = (170, 177, 159, 166)            # 8 x 8, plein (3 de profondeur) : la bouche du Drain
TOBOGGAN = {"r": (9.5, 11.5), "haut": SOMMET_TOUR - 3, "marches": 11, "pas": 6, "fin": 270}     # goulotte en spirale : une marche de 1 tous les 6 blocs, du sommet (on y saute de 3 : on ne remonte pas) au bassin, arrivee a l'ouest
CUVE = (120.0, 95.0)
R_CUVE, R_COURSIVE = 30.0, 35.0
FOND_CUVE, PLAFOND_CUVE = 30, 96               # air de 31 a 95
NIVEAUX = {"haut": 76, "median": 58, "bas": 42}
BOUCHES = {"A": (270, 64, 70), "C": (0, 48, 54), "B": (90, 32, 38), "D": (40, 31, 36)}       # azimut, y bas, y haut : scellees tant que les secteurs n'existent pas
TUYAUX = (22, 110, 160, 200, 250, 320)         # les six colonnes de tuyaux de la Cuve (azimuts)
PLONGEOIRS = (60, 135, 225, 300)               # les ouvertures du garde-corps de la Coursive, avec un plongeoir
LEVIERS = {1: (77, SOL + 1, 180), 2: (141, SOL + 1, 188), 3: (150, SOMMET_TOUR + 1, 178)}
TEMOINS_SAS = ((59, SOL + 7, 183), (59, SOL + 7, 184), (59, SOL + 7, 185))   # les trois voyants des valves, au-dessus de la porte du Vestiaire (mur du Hall, a l'entree du couloir)
X_PORTE_VESTIAIRE = 58                         # la porte du Vestiaire ferme le couloir en x 58 (z 182 a 186, 5 de haut)
LEVIER_SAS = (55, SOL + 2, 187)                # le levier de la grande porte (le Sas) : il apparait DANS le Vestiaire quand on a pris la cape, a l'endroit ou Luca l'a deplace a la main (25/11) : sur le mur est, pres de l'entree
LEVIER_SAS_ETAT = "face=wall,facing=west,powered=false"
TEMOINS_POMPES = ((118, 91, 130), (120, 91, 130), (122, 91, 130))
CASIER = (28, SOL + 1, 194)                    # le casier de la cape : l'une des cabines orange du Vestiaire (rangee sud, tournee vers le nord)
COFFRE_PISCINE = (115, FOND50 + 1, 178)         # le coffre qui apparait au fond de la piscine quand l'embuscade est vaincue
EMBUSCADE = ((100, FOND50 + 1, 173), (115, FOND50 + 1, 182), (130, FOND50 + 1, 175), (122, FOND50 + 1, 178),   # un squelette toutes les quatre places :
             (95, FOND50 + 1, 181), (108, FOND50 + 1, 177), (127, FOND50 + 1, 172), (135, FOND50 + 1, 181))   # 6 zombies et 2 squelettes (deux fois plus qu'avant, Luca 26/11)


def az(x, z, c):
    return math.degrees(math.atan2(x + 0.5 - c[0], -(z + 0.5 - c[1]))) % 360


def r_de(x, z, c):
    return math.hypot(x + 0.5 - c[0], z + 0.5 - c[1])


def carrelage(x, z):
    return S("smooth_quartz") if (x // 2 + z // 2) % 2 else S("white_concrete")


DOUCHE = S("donjon:douche", facing="east", coule=False)                         # le pommeau de douche Blockbench (donjon 4.2.0), contre le mur ouest
LAVABO = S("donjon:lavabo", facing="south")                                       # le lavabo Blockbench (donjon 4.2.0), contre le mur nord


def banc(face="east"):
    """Le banc de vestiaire Blockbench (donjon 4.2.0) ; ses raccords (joint_g, joint_d) se calculent en jeu."""
    return S("donjon:banc", facing=face, joint_g=False, joint_d=False)


def amenager_vestiaire(blocs, sources):
    """Le Vestiaire d'une piscine (demande de Luca, 18/11) : douches a l'ouest, lavabos et miroirs au nord, cabines de change au sud, pediluve a l'entree,
    lampes suspendues, serviettes, paniers a linge. Les casiers et les bancs sont deja la (rangees z 175 et 194)."""
    x0, x1, z0, z1 = VESTIAIRE
    y0, y1 = SOL + 1, SOL + HAUT_VESTIAIRE
    vide = lambda q: blocs.get(q) == AIR                                                      # noqa: E731

    def poser(q, b, seulement_vide=True):
        if not seulement_vide or vide(q):
            blocs[q] = b

    # --- les douches : dix cabines le long du mur ouest (x 14 a 18), cloisons de verre bleu, sol en grille, un pommeau chacune
    for z in range(z0 + 1, z1, 4):
        for x in range(x0 + 1, x0 + 5):
            for y in range(y0, y0 + 3):
                blocs[(x, y, z)] = S("light_blue_stained_glass_pane", north=False, south=False, east=True, west=True, waterlogged=False)
    for x in range(x0, x0 + 5):
        for z in range(z0, z1 + 1):
            if blocs.get((x, SOL, z), ("",))[0] != "minecraft:sea_lantern":
                blocs[(x, SOL, z)] = S("waxed_copper_grate", waterlogged=False)
            if blocs.get((x, y0, z), ("",))[0] == "minecraft:potted_fern":
                blocs[(x, y0, z)] = AIR
    for z in range(z0 + 3, z1, 4):
        blocs[(x0, y0 + 3, z)] = DOUCHE
        blocs[(x0 - 1, y0 + 4, z)] = S("waxed_cut_copper")                                     # l'arrivee d'eau dans le mur
    # --- les lavabos et leurs miroirs, le long du mur nord
    for x in range(x0 + 8, x1 - 6, 3):
        blocs[(x, y0, z0)] = LAVABO
        for y in (y0 + 1, y0 + 2):
            blocs[(x, y, z0 - 1)] = S("light_blue_stained_glass")                              # le miroir, dans le mur
        blocs[(x, y0 + 3, z0 - 1)] = S("sea_lantern")
        sources.append((x, y0 + 3, z0 - 1))
    # --- les cabines de change le long du mur sud : cloisons blanches, une porte de bouleau chacune
    for x in range(x0 + 8, x1 - 5, 4):
        for z in range(z1 - 2, z1 + 1):
            for y in range(y0, y0 + 3):
                blocs[(x, y, z)] = S("white_concrete")
        for xx in range(x, x + 4):
            for y in range(y0, y0 + 3):
                if xx == x + 2 and y < y0 + 2:
                    continue
                blocs[(xx, y, z1 - 3)] = S("white_concrete") if (xx + y) % 3 else S("light_blue_concrete")
        blocs[(x + 2, y0, z1 - 3)] = S("birch_door", facing="north", half="lower", hinge="left", open=False, powered=False)
        blocs[(x + 2, y0 + 1, z1 - 3)] = S("birch_door", facing="north", half="upper", hinge="left", open=False, powered=False)
        poser((x + 1, y0, z1 - 1), banc("east"))                                               # un petit banc dans la cabine
        blocs[(x + 2, y0 + 3, z1 - 1)] = S("light", level=12, waterlogged=False)                   # une lumiere (invisible) dans la cabine, porte fermee
        sources.append((x + 2, y0 + 3, z1 - 1, 12))
    # --- le pediluve a l'entree (on y passe les pieds en venant du Hall)
    cx0, cx1, cz0, cz1 = COULOIR_V
    for x in range(x1 - 5, x1 + 1):
        for z in range(cz0, cz1 + 1):
            if blocs.get((x, SOL, z), ("",))[0] != "minecraft:sea_lantern":
                blocs[(x, SOL, z)] = S("water", level=0)
                blocs[(x, SOL - 1, z)] = S("light_blue_concrete")
    # --- des lampes suspendues dans l'allee du milieu, des serviettes sur les bancs, des paniers a linge
    for x in (25, 35, 45):
        blocs[(x, y1, 185)] = S("chain", axis="y", waterlogged=False)
        blocs[(x, y1 - 1, 185)] = S("lantern", hanging=True, waterlogged=False)
    for (x, z, c) in ((23, 175, "white_carpet"), (31, 175, "light_blue_carpet"), (40, 175, "white_carpet"), (27, 194, "light_blue_carpet"), (45, 194, "white_carpet")):
        if blocs.get((x, y0 + 2, z), ("",))[0] == "minecraft:white_concrete" and vide((x, y0 + 3, z)):
            blocs[(x, y0 + 3, z)] = S(c)
    for (x, z) in ((x1 - 1, z0 + 2), (x1 - 2, z0 + 2), (x1 - 1, z1 - 4)):
        poser((x, y0, z), S("barrel", facing="up", open=False))
        poser((x, y0 + 1, z), S("white_carpet"))


def mur_hall(x, y, z):
    if y < SOL:
        return carreau_bassin(x, y, z)                              # les parois des bassins : carreaux bleus
    if y in (SOL + 1, SOL + 2):
        return S("light_blue_concrete") if (x + z) % 2 else S("cyan_concrete")      # la frise de carreaux bleus, a hauteur de hanche
    if y == SOL + 3:
        return S("white_concrete")
    return S("white_concrete") if (x // 2 + y // 2 + z // 2) % 2 else S("smooth_quartz")   # le carrelage blanc


def carreau_bassin(x, y, z):
    return S("white_concrete") if (x + y + z) % 5 == 0 else S("light_blue_concrete")


@lru_cache(maxsize=None)
def construire():
    air, eau, blocs, sources = set(), set(), {}, []

    def creuser(x0, x1, y0, y1, z0, z1):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    air.add((x, y, z))

    # ---------------------------------------------------------- les volumes
    creuser(PAVILLON[0] + 1, PAVILLON[1] - 1, PLAINE + 1, PLAINE + 6, PAVILLON[2] + 1, PAVILLON[3] - 1)
    marches = []
    for k in range(NB_MARCHES):
        z, y = ESC_Z0 - k, PLAINE - 1 - k
        creuser(ESCALIER_X[0], ESCALIER_X[1], y + 1, y + 6, z, z)
        marches.append((z, y))
    creuser(ESCALIER_X[0], ESCALIER_X[1], SOL + 1, SOL + 6, HALL[3] + 1, ESC_Z0 - NB_MARCHES + 1)
    creuser(HALL[0], HALL[1], SOL + 1, PLAFOND - 1, HALL[2], HALL[3])
    creuser(VESTIAIRE[0], VESTIAIRE[1], SOL + 1, SOL + HAUT_VESTIAIRE, VESTIAIRE[2], VESTIAIRE[3])
    creuser(COULOIR_V[0], COULOIR_V[1], SOL + 1, SOL + 5, COULOIR_V[2], COULOIR_V[3])
    creuser(SAS[0], SAS[1], SOL + 1, SOL + HAUT_SAS, SAS[2], SAS[3])
    creuser(PLONGEON[0], PLONGEON[1], FOND_PLONGEON + 1, SOL, PLONGEON[2], PLONGEON[3])
    creuser(BASSIN50[0], BASSIN50[1], FOND50 + 1, SOL, BASSIN50[2], BASSIN50[3])
    for x in range(BASSIN_ESSAI[0], BASSIN_ESSAI[1] + 1):
        for z in range(BASSIN_ESSAI[2], BASSIN_ESSAI[3] + 1):
            for y in range(SOL - 2, SOL + 1):
                eau.add((x, y, z))
    cuve_int = set()
    for x in range(int(CUVE[0] - R_COURSIVE) - 1, int(CUVE[0] + R_COURSIVE) + 2):
        for z in range(int(CUVE[1] - R_COURSIVE) - 1, int(CUVE[1] + R_COURSIVE) + 2):
            r = r_de(x, z, CUVE)
            if r < R_CUVE:
                cuve_int.add((x, z))
                creuser(x, x, FOND_CUVE + 1, PLAFOND_CUVE - 1, z, z)
            elif r < R_COURSIVE:
                creuser(x, x, SOL + 1, PLAFOND_CUVE - 1, z, z)
    for p in air:
        blocs[p] = AIR
    for p in eau:
        blocs[p] = S("water", level=0)

    # ---------------------------------------------------------- les sols
    def sol(x0, x1, y, z0, z1, f=carrelage):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if blocs.get((x, y, z)) is None or (x, y, z) not in air:
                    blocs[(x, y, z)] = f(x, z)

    sol(PAVILLON[0] + 1, PAVILLON[1] - 1, PLAINE, PAVILLON[2] + 1, PAVILLON[3] - 1)
    sol(HALL[0], HALL[1], SOL, HALL[2], HALL[3])
    sol(VESTIAIRE[0], VESTIAIRE[1], SOL, VESTIAIRE[2], VESTIAIRE[3])
    sol(COULOIR_V[0], COULOIR_V[1], SOL, COULOIR_V[2], COULOIR_V[3])
    sol(SAS[0], SAS[1], SOL, SAS[2], SAS[3])
    sol(ESCALIER_X[0], ESCALIER_X[1], SOL, HALL[3] + 1, ESC_Z0 - NB_MARCHES + 1)
    sol(PLONGEON[0], PLONGEON[1], FOND_PLONGEON, PLONGEON[2], PLONGEON[3], lambda x, z: S("blue_concrete") if (x - PLONGEON[0]) in (11, 12) and 4 <= z - PLONGEON[2] <= 19 or (z - PLONGEON[2]) in (11, 12) and 4 <= x - PLONGEON[0] <= 19 else S("light_blue_concrete"))      # le grand bain : une croix de reperes au fond
    lignes = [z for z in range(BASSIN50[2] + 2, BASSIN50[3] - 1, 3)]
    sol(BASSIN50[0], BASSIN50[1], FOND50, BASSIN50[2], BASSIN50[3], lambda x, z: S("black_concrete") if (z in lignes and BASSIN50[0] + 2 <= x <= BASSIN50[1] - 2) or (z in lignes and x == BASSIN50[1] - 2) else S("light_blue_concrete"))      # les lignes de nage noires au fond
    sol(BASSIN_ESSAI[0], BASSIN_ESSAI[1], SOL - 3, BASSIN_ESSAI[2], BASSIN_ESSAI[3], lambda x, z: S("light_blue_terracotta"))
    for (z, y) in marches:
        for x in range(ESCALIER_X[0], ESCALIER_X[1] + 1):
            blocs[(x, y, z)] = S("quartz_stairs", facing="south", half="bottom", shape="straight", waterlogged=False)
    for (x, z) in cuve_int:
        r = r_de(x, z, CUVE)
        blocs[(x, FOND_CUVE, z)] = S("waxed_copper_grate", waterlogged=False) if r < 5 else (S("polished_deepslate") if r < 6.5 else carrelage(x, z))     # la bonde grillee, au centre
    for x in range(int(CUVE[0] - R_COURSIVE) - 1, int(CUVE[0] + R_COURSIVE) + 2):
        for z in range(int(CUVE[1] - R_COURSIVE) - 1, int(CUVE[1] + R_COURSIVE) + 2):
            if R_CUVE <= r_de(x, z, CUVE) < R_COURSIVE:
                blocs[(x, SOL, z)] = carrelage(x, z)

    # ---------------------------------------------------------- les parois (toute case voisine de l'air ou de l'eau qui n'en est pas)
    pav = lambda x, y, z: S("quartz_bricks") if y < PLAINE + 6 else S("smooth_quartz")                          # noqa: E731
    for (x, y, z) in sorted(air | eau):
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + dx, y + dy, z + dz)
            if n in blocs:
                continue
            if y > PLAINE:
                blocs[n] = pav(*n)
            elif dy == 1 and HALL[0] <= n[0] <= HALL[1] and HALL[2] <= n[2] <= HALL[3]:
                blocs[n] = S("smooth_quartz")                                               # le plafond du Hall
            elif r_de(n[0], n[2], CUVE) < R_COURSIVE + 1.5 and n[2] < SAS[2] + 3:
                blocs[n] = (S("cyan_terracotta") if n[1] in NIVEAUX.values() else (S("smooth_quartz") if (n[1] // 2) % 2 else S("white_concrete")))      # la paroi carrelee de la Cuve ; les reperes de niveau
            else:
                blocs[n] = mur_hall(*n)
    # le pavillon : une porte au sud, des fenetres, un toit plat
    for x in range(PAVILLON[0], PAVILLON[1] + 1):
        for z in range(PAVILLON[2], PAVILLON[3] + 1):
            if (x, PLAINE, z) not in blocs:
                blocs[(x, PLAINE, z)] = S("quartz_bricks")
            for y in range(PLAINE + 1, PLAINE + 7):
                bord = x in (PAVILLON[0], PAVILLON[1]) or z in (PAVILLON[2], PAVILLON[3])
                if bord:
                    blocs[(x, y, z)] = S("white_stained_glass") if (y in (PLAINE + 3, PLAINE + 4) and (x + z) % 3) else S("quartz_bricks")
            blocs[(x, PLAINE + 7, z)] = S("smooth_quartz")
    for x in (119, 120):
        for y in (PLAINE + 1, PLAINE + 2, PLAINE + 3):
            blocs[(x, y, PAVILLON[3])] = AIR
            air.add((x, y, PAVILLON[3]))

    # ---------------------------------------------------------- la lumiere froide : des lanternes de mer dans le plafond et des feux de sol, partout
    def lampe(p):
        blocs[p] = S("sea_lantern")
        sources.append(p)

    for x in range(HALL[0] + 3, HALL[1], 7):
        for z in range(HALL[2] + 3, HALL[3], 7):
            lampe((x, PLAFOND, z))
            fz = z + 3
            if blocs.get((x, SOL, fz), AIR) != AIR and (x, SOL + 1, fz) in air and HALL[2] <= fz <= HALL[3]:
                lampe((x, SOL, fz))
    for (x0, x1, y, z0, z1) in ((PLONGEON[0], PLONGEON[1], FOND_PLONGEON, PLONGEON[2], PLONGEON[3]), (BASSIN50[0], BASSIN50[1], FOND50, BASSIN50[2], BASSIN50[3]),
                                (VESTIAIRE[0], VESTIAIRE[1], SOL, VESTIAIRE[2], VESTIAIRE[3])):
        for x in range(x0 + 2, x1, 6):
            for z in range(z0 + 2, z1, 6):
                lampe((x, y, z))
    for x in range(VESTIAIRE[0] + 3, VESTIAIRE[1], 7):
        for z in range(VESTIAIRE[2] + 3, VESTIAIRE[3], 7):
            lampe((x, SOL + HAUT_VESTIAIRE + 1, z))
    for z in range(SAS[2] + 2, SAS[3], 5):
        lampe((120, SOL + HAUT_SAS + 1, z))
    for k in range(0, 360, 20):                                                   # la Coursive : un anneau de lanternes au plafond ; la Cuve : quatre anneaux dans la paroi
        a = math.radians(k)
        lampe((int(CUVE[0] + 32.5 * math.sin(a)), PLAFOND_CUVE, int(CUVE[1] - 32.5 * math.cos(a))))
    for i in (3, 12, 21):                                                         # le plongeon : des lanternes a mi-hauteur dans ses quatre parois (les echelles des coins)
        for p in ((PLONGEON[0] - 1, FOND_PLONGEON + 6, PLONGEON[2] + i), (PLONGEON[1] + 1, FOND_PLONGEON + 6, PLONGEON[2] + i),
                  (PLONGEON[0] + i, FOND_PLONGEON + 6, PLONGEON[2] - 1), (PLONGEON[0] + i, FOND_PLONGEON + 6, PLONGEON[3] + 1)):
            lampe(p)
    for x in range(HALL[0] + 3, HALL[1], 6):                                         # au-dessus des bancs du mur nord
        lampe((x, SOL + 4, HALL[2] - 1))
        if not (ESCALIER_X[0] - 1 <= x <= ESCALIER_X[1] + 1):
            lampe((x, SOL + 4, HALL[3] + 1))                                       # et le long du mur sud
    for (x, z) in ((VESTIAIRE[0] - 1, VESTIAIRE[2] + 1), (VESTIAIRE[0] - 1, VESTIAIRE[3] - 1), (VESTIAIRE[1] + 1, VESTIAIRE[2] + 1), (VESTIAIRE[1] + 1, VESTIAIRE[3] - 1)):
        lampe((x, SOL + 3, z))
    for z in (HALL[2] + 1, HALL[3] - 1):                                            # les deux coins est du Hall
        lampe((HALL[1] - 1, SOL, z))
    for k in range(4, 360, 8):                                                   # le plafond est a 11 blocs : des feux dans le sol de la Coursive, sinon on y marche dans le noir
        a = math.radians(k)
        lampe((int(math.floor(CUVE[0] + 33.2 * math.sin(a))), SOL, int(math.floor(CUVE[1] - 33.2 * math.cos(a)))))
    for y in (36, 48, 60, 72):
        for k in range(10, 360, 30):
            a = math.radians(k + y)
            x, z = int(math.floor(CUVE[0] + 30.4 * math.sin(a))), int(math.floor(CUVE[1] - 30.4 * math.cos(a)))
            if (x, z) not in cuve_int and blocs.get((x, y, z), AIR) != AIR:
                lampe((x, y, z))
    for x in range(ESCALIER_X[0], ESCALIER_X[1] + 1, 5):
        for k in range(2, NB_MARCHES, 5):
            z, y = ESC_Z0 - k, PLAINE - 1 - k
            lampe((x if x == ESCALIER_X[0] else ESCALIER_X[1], y + 7, z))

    # ---------------------------------------------------------- les margelles : une bordure de quartz lisse autour des bassins, puis une bande bleue
    creux_bassins = {(x, z) for (x, y, z) in air if y == SOL and HALL[0] <= x <= HALL[1] and HALL[2] <= z <= HALL[3]}
    for (x, z) in creux_bassins:
        for dx in (-2, -1, 0, 1, 2):
            for dz in (-2, -1, 0, 1, 2):
                q = (x + dx, z + dz)
                if q in creux_bassins or blocs.get((q[0], SOL, q[1])) in (None, AIR) or blocs[(q[0], SOL, q[1])][0] == "minecraft:sea_lantern":
                    continue
                if not (HALL[0] <= q[0] <= HALL[1] and HALL[2] <= q[1] <= HALL[3]):
                    continue
                d = max(abs(dx), abs(dz))
                if d == 1:
                    blocs[(q[0], SOL, q[1])] = S("smooth_quartz")
                elif blocs[(q[0], SOL, q[1])][0] != "minecraft:smooth_quartz":
                    blocs[(q[0], SOL, q[1])] = S("light_blue_concrete")

    # ---------------------------------------------------------- le plongeon : trois plateformes (+4, +8, +12), chacune sur sa colonne, des echelles aux coins
    for nom, ((x0, x1, z0, z1), y) in PLATEFORMES.items():
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                blocs[(x, y, z)] = S("smooth_quartz") if (x + z) % 2 else S("light_blue_terracotta")
        cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
        for yy in range(FOND_PLONGEON + 1, y):
            blocs[(cx, yy, cz)] = S("quartz_pillar", axis="y")
        lampe((x0, y, z0))
    for (x, z, face) in ((PLONGEON[0], PLONGEON[2], "east"), (PLONGEON[1], PLONGEON[2], "west"), (PLONGEON[0], PLONGEON[3], "east"), (PLONGEON[1], PLONGEON[3], "west")):
        for y in range(FOND_PLONGEON + 1, SOL + 1):
            blocs[(x, y, z)] = S("ladder", facing=face, waterlogged=False)
    for (x, z, face) in ((100, BASSIN50[2], "south"), (125, BASSIN50[2], "south"), (100, BASSIN50[3], "north"), (125, BASSIN50[3], "north"), (BASSIN50[1], 172, "west")):
        for y in range(FOND50 + 1, SOL + 1):
            blocs[(x, y, z)] = S("ladder", facing=face, waterlogged=False)

    # ---------------------------------------------------------- la tour du toboggan : un noyau de 8 de diametre (une bande en spirale : le toboggan), trois corniches et le sommet
    tour = {}
    for x in range(TOUR[0] - 9, TOUR[0] + 10):
        for z in range(TOUR[1] - 9, TOUR[1] + 10):
            r = r_de(x, z, TOUR)
            if r <= R_NOYAU:
                for y in range(SOL + 1, SOMMET_TOUR + 1):
                    a = az(x, z, TOUR)
                    bande = r > R_NOYAU - 1 and (a + (y - SOL) * 45) % 120 < 30
                    blocs[(x, y, z)] = S("blue_concrete") if bande else S("white_concrete")
                    tour[(x, y, z)] = True
                blocs[(x, SOMMET_TOUR, z)] = S("smooth_quartz")
    corniches = {}
    for nom, a_deg, y in CORNICHES_TOUR:
        a = math.radians(a_deg)
        cx, cz = round(TOUR[0] - 0.5 + 6 * math.sin(a)), round(TOUR[1] - 0.5 - 6 * math.cos(a))
        for x in range(cx - 2, cx + 3):
            for z in range(cz - 2, cz + 3):
                blocs[(x, y, z)] = S("smooth_quartz") if (x + z) % 2 else S("light_blue_terracotta")
        corniches[nom] = (cx, y, cz)
        lampe((cx - 2, y, cz - 2))
    lampe((TOUR[0] + 2, SOMMET_TOUR, TOUR[1] + 2))

    # ---------------------------------------------------------- le toboggan : une goulotte seche en spirale autour de la tour, du sommet jusqu'au bassin de 50 m (sans eau, demande de Luca).
    # Une marche de 1 tous les 6 blocs : on la descend a pied ; du sommet on y saute de 3 blocs, on ne la remonte donc pas.
    r0, r1 = TOBOGGAN["r"]
    pas_deg = TOBOGGAN["pas"] / (2 * math.pi * (r0 + r1) / 2) * 360
    balayage = TOBOGGAN["marches"] * pas_deg
    depart = (TOBOGGAN["fin"] - balayage) % 360
    toboggan = []
    for x in range(TOUR[0] - 13, TOUR[0] + 14):
        for z in range(TOUR[1] - 13, TOUR[1] + 14):
            r = r_de(x, z, TOUR)
            if not (r0 - 0.6 <= r < r1 + 0.6):
                continue
            for tour_k in (0, 1):
                u = (az(x, z, TOUR) - depart) % 360 + 360 * tour_k
                if u >= balayage:
                    continue
                i = int(u // pas_deg)
                y_sol = TOBOGGAN["haut"] - i
                if r0 <= r < r1:
                    blocs[(x, y_sol, z)] = S("light_blue_concrete") if (x + z) % 3 else S("white_concrete")
                    blocs[(x, y_sol + 1, z)] = AIR
                    air.add((x, y_sol + 1, z))
                    for yy in range(y_sol + 2, max(y_sol + 4, SOL + 1)):
                        if yy <= SOL or blocs.get((x, yy, z)) == AIR:
                            blocs[(x, yy, z)] = AIR
                            air.add((x, yy, z))
                    toboggan.append((x, y_sol + 1, z))
                else:
                    for yy in range(y_sol, max(y_sol + 2, SOL + 1)):
                        blocs[(x, yy, z)] = S("light_blue_concrete")
                    blocs[(x, max(y_sol + 1, SOL + 1), z)] = S("diorite_wall", east="none", west="none", north="none", south="none", up=True, waterlogged=False)     # un muret d'1,5 : on ne l'enjambe pas
                    if y_sol > SOL + 1 and r < r0 and int(u) % 40 < 3:
                        for yy in range(SOL + 1, y_sol):
                            blocs[(x, yy, z)] = S("quartz_pillar", axis="y")           # les piliers qui portent la goulotte
    for k in range(0, 360, 45):                                                   # des lanternes dans la tour, face a la goulotte
        for y in (SOL + 2, SOL + 5, SOL + 8):
            ang = math.radians(k + y * 10)
            lampe((int(math.floor(TOUR[0] + 3.6 * math.sin(ang))), y, int(math.floor(TOUR[1] - 3.6 * math.cos(ang)))))
    # la passerelle du sommet au-dessus du depart de la goulotte (3 blocs plus bas : on saute dedans)
    a = math.radians(depart + pas_deg * 0.3)
    for d in [k * 0.5 for k in range(8, 21)]:
        for e in (-1.0, 0.0, 1.0):
            x = int(math.floor(TOUR[0] + d * math.sin(a) + e * math.cos(a)))
            z = int(math.floor(TOUR[1] - d * math.cos(a) + e * math.sin(a)))
            if r_de(x, z, TOUR) < r0:
                blocs[(x, SOMMET_TOUR, z)] = S("smooth_quartz")
                for yy in (SOMMET_TOUR + 1, SOMMET_TOUR + 2):
                    if blocs.get((x, yy, z)) not in (None,):
                        blocs[(x, yy, z)] = AIR

    # ---------------------------------------------------------- les ancrages du grappin (tenus au plafond par une chaine) : trois au dessus des plateformes, quatre au dessus du bord, un par corniche et le sommet
    ancrages = []
    for nom, ((x0, x1, z0, z1), y) in PLATEFORMES.items():
        ancrages.append((f"plongeon_{nom}", (x0 + x1) // 2, y + 4, (z0 + z1) // 2))
    for nom, (x, z) in (("bord_n", (77, PLONGEON[2] - 3)), ("bord_s", (77, PLONGEON[3] + 3)), ("bord_o", (PLONGEON[0] - 3, 177)), ("bord_e", (PLONGEON[1] + 3, 177))):
        ancrages.append((f"plongeon_{nom}", x, SOL + 4, z))
    for nom, (x, y, z) in corniches.items():
        ancrages.append((f"tour_{nom}", x, y + 4, z))
    ancrages.append(("tour_sommet", TOUR[0], PLAFOND - 1, TOUR[1]))
    for (nom, x, y, z) in ancrages:
        for yy in range(y + 1, PLAFOND):
            if blocs.get((x, yy, z)) == AIR:
                blocs[(x, yy, z)] = S("chain", axis="y", waterlogged=False)
        blocs[(x, y, z)] = S("donjon:ancrage")

    # ---------------------------------------------------------- l'esprit piscine : chaise de maitre-nageur, parasols et transats, serviettes, bancs, plantes
    def poser_si_libre(p, b):
        if blocs.get(p) == AIR and blocs.get((p[0], p[1] - 1, p[2])) not in (None, AIR):
            blocs[p] = b
            return True
        return False

    cx = 112                                                                      # la chaise du maitre-nageur, face au bassin
    for y in (SOL + 1, SOL + 2):
        for x in (cx, cx + 1):
            blocs[(x, y, 188)] = S("white_concrete")
    blocs[(cx, SOL + 3, 188)] = S("quartz_stairs", facing="south", half="bottom", shape="straight", waterlogged=False)
    blocs[(cx + 1, SOL + 3, 188)] = S("quartz_stairs", facing="south", half="bottom", shape="straight", waterlogged=False)
    for x in (cx, cx + 1):
        blocs[(x, SOL + 1, 189)] = S("ladder", facing="south", waterlogged=False)
    blocs[(cx - 1, SOL + 4, 188)] = S("red_wool")
    toiles = ("red_wool", "yellow_wool", "light_blue_wool", "white_wool", "lime_wool")
    for i, x in enumerate((92, 102, 132, 152, 162)):                                # parasols et transats, sur la plage sud
        z = 199
        for y in range(SOL + 1, SOL + 4):
            blocs[(x, y, z)] = S("birch_fence", east=False, west=False, north=False, south=False, waterlogged=False)
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                blocs[(x + dx, SOL + 4, z + dz)] = S(toiles[i % len(toiles)]) if (dx + dz) % 2 == 0 else S("white_wool")
        for dx in (-2, 2):
            blocs[(x + dx, SOL + 1, z - 1)] = S("birch_stairs", facing="south", half="bottom", shape="straight", waterlogged=False)
            blocs[(x + dx, SOL + 1, z)] = S("birch_slab", type="bottom", waterlogged=False)
            blocs[(x + dx, SOL + 1, z + 1)] = S("birch_slab", type="bottom", waterlogged=False)
        poser_si_libre((x, SOL + 1, z + 3), S(("red_carpet", "yellow_carpet", "light_blue_carpet", "orange_carpet", "lime_carpet")[i % 5]))
        poser_si_libre((x + 1, SOL + 1, z + 3), S(("red_carpet", "yellow_carpet", "light_blue_carpet", "orange_carpet", "lime_carpet")[i % 5]))
    for (x0, x1) in ((92, 112), (126, 166)):                                       # des gradins le long du mur nord : trois rangs
        for x in range(x0, x1 + 1):
            for rang in range(3):
                z = HALL[2] + 2 - rang
                for y in range(SOL + 1, SOL + 1 + rang):
                    blocs[(x, y, z)] = S("white_concrete")
                blocs[(x, SOL + 1 + rang, z)] = S("quartz_stairs", facing="north", half="bottom", shape="straight", waterlogged=False) if x % 6 else S("light_blue_concrete")
    for (px, pz) in ((64, 162), (64, 208), (90, 208), (150, 208), (176, 208), (176, 196)):     # des palmiers dans des bacs de sable
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                blocs[(px + dx, SOL + 1, pz + dz)] = S("sand") if (dx, dz) == (0, 0) or True else None
                if (dx, dz) != (0, 0):
                    blocs[(px + dx, SOL + 1, pz + dz)] = S("birch_slab", type="top", waterlogged=False)
        for y in range(SOL + 2, SOL + 8):
            blocs[(px, y, pz)] = S("jungle_log", axis="y")
        for (dx, dz) in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (2, 0), (-2, 0), (0, 2), (0, -2), (3, 0), (-3, 0), (0, 3), (0, -3), (1, 1), (-1, -1), (1, -1), (-1, 1)):
            yy = SOL + 8 if max(abs(dx), abs(dz)) < 2 else SOL + 7
            blocs[(px + dx, yy, pz + dz)] = S("jungle_leaves", persistent=True, distance=1, waterlogged=False)
    for x in (70, 76, 82):                                                         # des plongeoirs au bord du grand bain (des planches au-dessus du vide)
        for z in range(PLONGEON[3] - 2, PLONGEON[3] + 2):
            if blocs.get((x, SOL, z)) == AIR:
                blocs[(x, SOL, z)] = S("quartz_slab", type="top", waterlogged=False)
        blocs[(x, SOL + 1, PLONGEON[3] + 2)] = S("quartz_stairs", facing="south", half="bottom", shape="straight", waterlogged=False)
    bx0, bx1, bz = 164, 174, 202                                                   # la buvette : un comptoir, des tabourets, des fruits
    for x in range(bx0, bx1 + 1):
        blocs[(x, SOL + 1, bz)] = S("stripped_birch_log", axis="x")
        blocs[(x, SOL + 2, bz)] = S("smooth_quartz_slab", type="bottom", waterlogged=False)
        if x % 2 == 0:
            blocs[(x, SOL + 1, bz - 2)] = S("birch_fence", east=False, west=False, north=False, south=False, waterlogged=False)
            blocs[(x, SOL + 2, bz - 2)] = S("birch_pressure_plate", powered=False)
    for (x, b) in ((bx0 + 1, S("melon")), (bx0 + 4, S("pumpkin")), (bx0 + 7, S("hay_block", axis="y"))):
        blocs[(x, SOL + 1, bz + 2)] = b
    for x in range(bx0, bx1 + 1, 2):
        blocs[(x, SOL + 5, bz)] = S("light_blue_wool") if x % 4 else S("white_wool")
    for x in range(64, 80, 3):                                                     # les douches, pres du couloir du Vestiaire
        blocs[(x, SOL + 4, 211)] = S("iron_bars", waterlogged=False)
        blocs[(x, SOL + 3, 211)] = S("lightning_rod", facing="down", powered=False, waterlogged=False)
        blocs[(x, SOL, 210)] = S("waxed_copper_grate", waterlogged=False)
    for (x, z) in ((92, 190), (138, 190), (92, 166), (138, 166)):                     # des bouees de sauvetage (laine rouge et blanche) aux angles du bassin
        poser_si_libre((x, SOL + 1, z), S("red_wool"))
        if blocs.get((x, SOL + 1, z)) == S("red_wool"):
            blocs[(x, SOL + 2, z)] = S("white_carpet")
    for (x, z) in ((HALL[0] + 1, HALL[2] + 1), (HALL[0] + 1, HALL[3] - 1), (HALL[1] - 3, HALL[3] - 1), (114, HALL[3] - 1), (125, HALL[3] - 1), (HALL[1] - 1, 172)):
        poser_si_libre((x, SOL + 1, z), S("potted_fern"))

    # ---------------------------------------------------------- les volants (des leviers), les temoins du Sas et des pompes, la vanne du Sas, le casier
    for p in TEMOINS_SAS + TEMOINS_POMPES:
        blocs[p] = S("waxed_copper_bulb", lit=False, powered=False)
    vanne = [(x, y, SAS[3]) for x in range(SAS[0], SAS[1] + 1) for y in range(SOL + 1, SOL + HAUT_SAS + 1)]
    for p in vanne:
        blocs[p] = station_vannes.BARRIERE                                                  # la porte du Sas (entite donjon:porte_station) ; la barriere ferme le passage
    porte_vestiaire = [(X_PORTE_VESTIAIRE, y, z) for z in range(COULOIR_V[2], COULOIR_V[3] + 1) for y in range(SOL + 1, SOL + 6)]
    for p in porte_vestiaire:
        blocs[p] = station_vannes.BARRIERE
    couleurs = ("bleu_clair", "cyan", "vert", "jaune", "orange")
    for z in (175, 194):                                                          # deux rangees de vrais casiers (donjon:casier) dans le Vestiaire, la porte vers le banc
        face = "south" if z == 175 else "north"
        for x in range(20, 50):
            if x % 6 == 2:
                continue
            c = couleurs[(x // 6) % len(couleurs)]
            blocs[(x, SOL + 1, z)] = S("donjon:casier", facing=face, couleur=c, half="lower", open=False)
            blocs[(x, SOL + 2, z)] = S("donjon:casier", facing=face, couleur=c, half="upper", open=False)
            blocs[(x, SOL + 3, z)] = S("white_concrete")
        for x in range(21, 49):
            if x % 6 != 2:
                blocs[(x, SOL + 1, z + (3 if z == 175 else -3))] = banc("east")
    for (x, z) in ((16, 165), (53, 165), (16, 204), (53, 204), (35, 184), (35, 185)):
        blocs[(x, SOL + 1, z)] = S("potted_fern")
    amenager_vestiaire(blocs, sources)

    # ---------------------------------------------------------- la Cuve : garde-corps et plongeoirs, colonnes de tuyaux, bouches scellees, ancrages au dessus de A et C
    for x in range(int(CUVE[0] - R_COURSIVE) - 1, int(CUVE[0] + R_COURSIVE) + 2):
        for z in range(int(CUVE[1] - R_COURSIVE) - 1, int(CUVE[1] + R_COURSIVE) + 2):
            r = r_de(x, z, CUVE)
            if R_CUVE <= r < R_CUVE + 1:
                a = az(x, z, CUVE)
                if any(abs((a - p + 180) % 360 - 180) < 3.5 for p in PLONGEOIRS):
                    continue
                for y in (SOL + 1, SOL + 2):
                    blocs[(x, y, z)] = S("iron_bars", waterlogged=False)
    plongeoirs = []
    for p in PLONGEOIRS:
        a = math.radians(p)
        for d in (28.5, 29.5):
            x, z = int(math.floor(CUVE[0] + d * math.sin(a))), int(math.floor(CUVE[1] - d * math.cos(a)))
            blocs[(x, SOL, z)] = S("quartz_slab", type="top", waterlogged=False)
            plongeoirs.append((x, SOL + 1, z))
    for k in TUYAUX:
        a = math.radians(k)
        cx, cz = int(math.floor(CUVE[0] + 28.4 * math.sin(a))), int(math.floor(CUVE[1] - 28.4 * math.cos(a)))
        for x in (cx, cx + 1):
            for z in (cz, cz + 1):
                for y in range(FOND_CUVE + 1, SOL):
                    blocs[(x, y, z)] = S("waxed_cut_copper") if y % 8 == 0 else S("waxed_exposed_copper")
    bouches = {}
    for nom, (a_deg, y0, y1) in BOUCHES.items():
        cases = []
        for x in range(int(CUVE[0] - R_CUVE) - 2, int(CUVE[0] + R_CUVE) + 3):
            for z in range(int(CUVE[1] - R_CUVE) - 2, int(CUVE[1] + R_CUVE) + 3):
                r = r_de(x, z, CUVE)
                if not (R_CUVE <= r < R_CUVE + 1.2):
                    continue
                d = abs((az(x, z, CUVE) - a_deg + 180) % 360 - 180)
                for y in range(y0 - 1, y1 + 2):
                    if d < 5.5 and y0 <= y <= y1:
                        cases.append((x, y, z))                                       # la vanne de la bouche, scellee (bloc du mod, posee plus bas)
                    elif d < 8:
                        blocs[(x, y, z)] = S("waxed_cut_copper")                      # son cadre de cuivre
        for p in cases:
            blocs[p] = station_vannes.vanne(p, cases, False)
        bouches[nom] = cases
    ancrages_cuve = []
    for nom, y in (("A", BOUCHES["A"][2] + 4), ("C", BOUCHES["C"][2] + 4)):
        a = math.radians(BOUCHES[nom][0])
        x, z = int(math.floor(CUVE[0] + 30.4 * math.sin(a))), int(math.floor(CUVE[1] - 30.4 * math.cos(a)))
        blocs[(x, y, z)] = S("donjon:ancrage")
        ancrages_cuve.append((f"cuve_{nom}", x, y, z))

    # ---------------------------------------------------------- l'eau de la Cuve, par couches (la mise en eau la pose) : pour chaque y, les rangees (x0, x1, z) de l'interieur
    rangees = {}
    for (x, z) in cuve_int:
        rangees.setdefault(z, []).append(x)
    couches = []
    for z in sorted(rangees):
        xs = sorted(rangees[z])
        couches.append((xs[0], xs[-1], z))
    infos = {"entree": (119, PLAINE + 1, PAVILLON[3] - 1), "leviers": LEVIERS, "temoins_sas": TEMOINS_SAS, "temoins_pompes": TEMOINS_POMPES, "vanne_sas": vanne, "casier": CASIER, "coffre_piscine": COFFRE_PISCINE, "porte_vestiaire": porte_vestiaire, "levier_sas": LEVIER_SAS,
             "portes": {"sas": station_vannes.porte_station(((SAS[0] + SAS[1]) / 2 + 0.5, SAS[3] + 0.5), SOL + 1, SAS[1] - SAS[0] + 1, HAUT_SAS, 0, vanne),
                        "vestiaire": station_vannes.porte_station((X_PORTE_VESTIAIRE + 0.5, (COULOIR_V[2] + COULOIR_V[3]) / 2 + 0.5), SOL + 1, COULOIR_V[3] - COULOIR_V[2] + 1, 5, 90, porte_vestiaire)},
             "embuscade": EMBUSCADE, "bassin50": BASSIN50, "fond50": FOND50, "ancrages": ancrages + ancrages_cuve, "plateformes": {n: ((x0 + x1) // 2, y + 1, (z0 + z1) // 2) for n, ((x0, x1, z0, z1), y) in PLATEFORMES.items()},
             "corniches": {n: (x, y + 1, z) for n, (x, y, z) in corniches.items()}, "sommet": (TOUR[0], SOMMET_TOUR + 1, TOUR[1]), "cuve": {"centre": CUVE, "rayon": R_CUVE, "fond": FOND_CUVE, "rangees": couches,
             "niveaux": NIVEAUX}, "coursive": (120, SOL + 1, 128), "bouches": bouches, "plongeoirs": plongeoirs, "sources": sources, "marches": [(x, y + 1, z) for (z, y) in marches for x in range(ESCALIER_X[0], ESCALIER_X[1] + 1)],
             "bassin_essai": BASSIN_ESSAI}
    import sys
    import station_create
    station_create.decor_acte1(blocs, infos, sys.modules[__name__])
    return blocs, infos
