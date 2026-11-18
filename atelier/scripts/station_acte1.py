# [claude-build]
"""station_acte1.py - Donjon de la Station (le donjon 2), actes I et II d'apres le dossier de conception (Donjon de la Station - dossier de conception.pdf, 2026-11-17) :
le pavillon d'entree sur la plaine, l'escalier de 24 marches, le Hall des Bassins (trois volants : le plongeon au grappin, le bassin de 50 m et son embuscade, la tour du toboggan au grappin),
le bassin d'essai (la bouche du Drain, a construire plus tard), le Vestiaire et son casier, le Sas (une vanne a trois temoins), et la Grande Cuve (60 de diametre, sol a 30, plafond a 96)
avec sa Coursive a 84, ses quatre bouches scellees (A, C, B, D : les secteurs viendront plus tard), ses colonnes de tuyaux, ses reperes HAUT / MEDIAN / BAS et ses temoins des pompes.
Coordonnees du gabarit (240 x 112 x 240) ; nord = z decroissant. Pur calcul : renvoie (blocs, infos) ; l'eau de la Cuve n'est pas dans la structure (la mise en eau la remplit)."""
import math
from functools import lru_cache

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
BASSIN50 = (96, 145, 190, 203)                 # le bassin de 50 m, peu profond (3)
FOND50 = SOL - 3
TOUR = (162, 185)                              # la tour du toboggan : 16 de diametre (noyau de rayon 4, corniches jusqu'a 8)
R_NOYAU = 4.0
CORNICHES_TOUR = (("L1", 180, 86), ("L2", 270, 89), ("L3", 0, 91))       # azimut (0 = nord, 90 = est), sol
SOMMET_TOUR = 94
BASSIN_ESSAI = (170, 177, 159, 166)            # 8 x 8, plein (3 de profondeur) : la bouche du Drain
CUVE = (120.0, 95.0)
R_CUVE, R_COURSIVE = 30.0, 35.0
FOND_CUVE, PLAFOND_CUVE = 30, 96               # air de 31 a 95
NIVEAUX = {"haut": 76, "median": 58, "bas": 42}
BOUCHES = {"A": (270, 64, 70), "C": (0, 48, 54), "B": (90, 32, 38), "D": (40, 31, 36)}       # azimut, y bas, y haut : scellees tant que les secteurs n'existent pas
TUYAUX = (22, 110, 160, 200, 250, 320)         # les six colonnes de tuyaux de la Cuve (azimuts)
PLONGEOIRS = (60, 135, 225, 300)               # les ouvertures du garde-corps de la Coursive, avec un plongeoir
LEVIERS = {1: (77, SOL + 1, 180), 2: (147, SOL + 1, 196), 3: (162, SOMMET_TOUR + 1, 185)}
TEMOINS_SAS = ((118, 91, 157), (120, 91, 157), (122, 91, 157))
TEMOINS_POMPES = ((118, 91, 130), (120, 91, 130), (122, 91, 130))
CASIER = (34, SOL + 1, 175)                    # le casier de la cape (une case de la rangee nord du Vestiaire)
EMBUSCADE = ((106, FOND50 + 1, 195), (121, FOND50 + 1, 199), (136, FOND50 + 1, 194), (128, FOND50 + 1, 192))   # 3 zombies puis un squelette


def az(x, z, c):
    return math.degrees(math.atan2(x + 0.5 - c[0], -(z + 0.5 - c[1]))) % 360


def r_de(x, z, c):
    return math.hypot(x + 0.5 - c[0], z + 0.5 - c[1])


def carrelage(x, z):
    return S("smooth_quartz") if (x // 2 + z // 2) % 2 else S("white_concrete")


def mur_hall(x, y, z):
    if y == SOL + 2:
        return S("light_blue_terracotta")                          # la bande de « terre cuite cyan clair » a hauteur d'epaule
    if (x % 8 == 0 or z % 8 == 0) and y < PLAFOND:
        return S("quartz_pillar", axis="y")                        # les pilastres repetes
    return S("quartz_bricks")


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
    sol(PLONGEON[0], PLONGEON[1], FOND_PLONGEON, PLONGEON[2], PLONGEON[3], lambda x, z: S("light_blue_terracotta") if (x + z) % 6 == 0 else S("white_concrete"))
    sol(BASSIN50[0], BASSIN50[1], FOND50, BASSIN50[2], BASSIN50[3], lambda x, z: S("light_blue_terracotta") if (z - BASSIN50[2]) % 3 == 1 and x % 2 else S("white_concrete"))
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

    # ---------------------------------------------------------- la tuyauterie apparente du Hall : des conduites de cuivre sous le plafond
    for z in (HALL[2] + 1, HALL[3] - 1):
        for x in range(HALL[0], HALL[1] + 1):
            if blocs.get((x, PLAFOND - 1, z)) == AIR:
                blocs[(x, PLAFOND - 1, z)] = S("waxed_cut_copper") if x % 9 else S("waxed_copper_block")

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
    for (x, z, face) in ((BASSIN50[0], (BASSIN50[2] + BASSIN50[3]) // 2, "east"), (BASSIN50[1], (BASSIN50[2] + BASSIN50[3]) // 2, "west")):
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
                    blocs[(x, y, z)] = S("light_blue_terracotta") if bande else S("quartz_bricks")
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
        blocs[(x, y, z)] = S("grappin:ancrage")

    # ---------------------------------------------------------- les volants (des leviers), les temoins du Sas et des pompes, la vanne du Sas, le casier
    for k, p in LEVIERS.items():
        blocs[p] = S("lever", face="floor", facing="north", powered=False)
    for p in TEMOINS_SAS + TEMOINS_POMPES:
        blocs[p] = S("waxed_copper_bulb", lit=False, powered=False)
    vanne = [(x, y, SAS[3]) for x in range(SAS[0], SAS[1] + 1) for y in range(SOL + 1, SOL + HAUT_SAS + 1)]
    for p in vanne:
        blocs[p] = S("iron_bars", waterlogged=False)
    for z in (175, 194):                                                          # deux rangees de casiers d'acier dans le Vestiaire
        for x in range(20, 50):
            if x % 6 == 2:
                continue
            for y in (SOL + 1, SOL + 2):
                blocs[(x, y, z)] = S("iron_block")
            blocs[(x, SOL + 3, z)] = S("light_gray_concrete")
    blocs[CASIER] = S("iron_block")

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
                        blocs[(x, y, z)] = S("iron_bars", waterlogged=False)          # la vanne de la bouche, scellee
                        cases.append((x, y, z))
                    elif d < 8:
                        blocs[(x, y, z)] = S("waxed_cut_copper")                      # son cadre de cuivre
        bouches[nom] = cases
    ancrages_cuve = []
    for nom, y in (("A", BOUCHES["A"][2] + 4), ("C", BOUCHES["C"][2] + 4)):
        a = math.radians(BOUCHES[nom][0])
        x, z = int(math.floor(CUVE[0] + 30.4 * math.sin(a))), int(math.floor(CUVE[1] - 30.4 * math.cos(a)))
        blocs[(x, y, z)] = S("grappin:ancrage")
        ancrages_cuve.append((f"cuve_{nom}", x, y, z))

    # ---------------------------------------------------------- l'eau de la Cuve, par couches (la mise en eau la pose) : pour chaque y, les rangees (x0, x1, z) de l'interieur
    rangees = {}
    for (x, z) in cuve_int:
        rangees.setdefault(z, []).append(x)
    couches = []
    for z in sorted(rangees):
        xs = sorted(rangees[z])
        couches.append((xs[0], xs[-1], z))
    infos = {"entree": (119, PLAINE + 1, PAVILLON[3] - 1), "leviers": LEVIERS, "temoins_sas": TEMOINS_SAS, "temoins_pompes": TEMOINS_POMPES, "vanne_sas": vanne, "casier": CASIER,
             "embuscade": EMBUSCADE, "bassin50": BASSIN50, "fond50": FOND50, "ancrages": ancrages + ancrages_cuve, "plateformes": {n: ((x0 + x1) // 2, y + 1, (z0 + z1) // 2) for n, ((x0, x1, z0, z1), y) in PLATEFORMES.items()},
             "corniches": {n: (x, y + 1, z) for n, (x, y, z) in corniches.items()}, "sommet": (TOUR[0], SOMMET_TOUR + 1, TOUR[1]), "cuve": {"centre": CUVE, "rayon": R_CUVE, "fond": FOND_CUVE, "rangees": couches,
             "niveaux": NIVEAUX}, "coursive": (120, SOL + 1, 128), "bouches": bouches, "plongeoirs": plongeoirs, "sources": sources, "marches": [(x, y + 1, z) for (z, y) in marches for x in range(ESCALIER_X[0], ESCALIER_X[1] + 1)],
             "bassin_essai": BASSIN_ESSAI}
    return blocs, infos
