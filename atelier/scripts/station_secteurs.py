# [claude-build]
"""station_secteurs.py - Donjon de la Station, la suite des actes I et II (station_acte1.py), d'apres le dossier de conception :
le Drain (contournement), les secteurs A (Filtration), B (Chaufferie) et C (Turbines) avec leurs ecluses et leurs pompes, la Cheminee et le Poste (acte IV),
le Reservoir du boss et l'Exutoire (acte V). Coordonnees du gabarit, comme station_acte1. Pur calcul : construire() -> ({structure: blocs}, infos).

Ce que le mod ne sait pas encore faire est remplace en datapack (voir la fiche) :
- les courants : un joueur qui flotte (de l'eau sous les pieds) est pousse ; un joueur qui marche au fond (ou tient une echelle) ne l'est pas : c'est le « leste » ;
- le tourbillon : nager (la nage rapide, le drapeau is_swimming) contre une turbine ou un tartre ;
- les plaques lestees : se tenir debout sur la dalle de cuivre ;
- les boutons (qui ne tiennent pas sous l'eau) : des zones marquees au sol.
"""
import math
from functools import lru_cache

import station_acte1 as a1
from station_acte1 import CUVE, SOL, az, r_de
from suspendu_outils import AIR, S

EAU = S("water", level=0)

# ---------------------------------------------------------------- le Drain : du bassin d'essai du Hall au pied de la Cuve (puits de 4 x 4 a echelle, puis conduit)
DRAIN_PUITS = (172, 175, 159, 162)              # x0, x1, z0, z1 ; de y 31 au fond du bassin d'essai
DRAIN_NS = (172, 175, 108, 158)                 # le conduit vers le nord, y 31..34
DRAIN_EO = (140, 171, 108, 111)                 # le conduit vers l'ouest jusqu'a la Cuve
DRAIN_Y = (31, 34)

# ---------------------------------------------------------------- secteur A : la Filtration (nager vite), a l'ouest
ECL_A = (80, 88, 92, 98, 64, 70)                # x0, x1, z0, z1, y0, y1 : l'ecluse
VANNE_INT_A = 79
VEST_A = (74, 78, 92, 98, 64, 68)
GAL_A = (75, 77, 43, 91, 64, 66)                # la galerie des vannes, vers le nord
VANNES_A = (82, 70, 58, 46)                     # z des 4 vannes
A3 = (22, 76, 3, 42, 61, 74)                    # la salle des lits (sol a 60)
LITS = ((30, 33, 3, 30), (44, 47, 15, 42), (58, 61, 3, 30))
A4 = (2, 20, 12, 34, 61, 72)                    # la salle de la Pompe A
POMPE_A = (8, 23)
CACHE = (47, 57, 47, 51, 62, 64)                # la chambre cachee
FENTE = (52, 43, 46)                            # x, z0, z1 du passage de la fente (y 62..63), vannes en z 44 et 46

# ---------------------------------------------------------------- secteur B : la Chaufferie (se lester), a l'est
ECL_B = (151, 159, 92, 98, 32, 38)
VANNE_INT_B = 160
COLL = (161, 188, 93, 97)                       # le collecteur en pente : sol de 31 a 39
B2 = (189, 228, 70, 121, 40, 50)                # la salle des chaudieres (sol a 39), noyee
CHAUDIERES = ((198, 80), (198, 111), (217, 96), (225, 76))
PLAQUES = ((200, 95), (212, 80), (222, 110))
CAGES = {2: (210, 214, 78, 82, "s"), 3: (220, 224, 108, 112, "n")}       # la cage de la plaque, et le cote du jet
COLONNE_B = (206, 212, 92, 98)                 # 7 x 7 : Luca a elargi le puits a la main (18/11), la grille et la colonne de bulles suivent
B3 = (189, 228, 70, 121, 58, 72)                # la halle haute (sol a 57), seche
TREMPE = (199, 222, 82, 109, 53, 57)            # le bassin de trempe
PASSERELLES = ((190, 198), (205, 208), (215, 217), (223, 227))          # sur z 95..97, sol a 63
Z_PASS = (95, 97)
Y_PASS = 63
ILOT = (202, 204, 86, 88)
SORTIE_B = (190, 72)

# ---------------------------------------------------------------- secteur C : les Turbines (tourbillon), au nord
ECL_C = (117, 123, 55, 63, 48, 54)
VANNE_INT_C = 54
PUITS_C = (120.0, 34.0)
R_PUITS_C = 18.0
EAU_C = 82
GRILLES_C = (62, 76)
TARTRE_C = (108, 112, 32, 36)
TURBINES_C = (52, 68, 80)
SALLE_C = (112, 128, 2, 13, 86, 95)

# ---------------------------------------------------------------- acte IV : la Cheminee et le Poste
CHEM = (160.0, 47.0)
R_CHEM = 7.0
GAL_D = (31.3, 57.0)                            # le long de l'azimut 40, de la paroi de la Cuve au puits
VANNES_D = (37.0, 43.0, 49.0)
CHAMBRE = (145, 179, 36, 56)                    # la chambre seche (sol a 84) et le bassin du haut (70..76) dessous
BASSIN_CHEM = (151, 179, 36, 56)
TROUS = ((151, 156), (160, 165), (169, 174))
POSTE = (145, 150)
COULOIR_POSTE = ((146, 148, 57, 66), (139, 148, 64, 66))
PORTE_POSTE = 143

# ---------------------------------------------------------------- acte V : le Reservoir et l'Exutoire
R_RES = 44.0
RES_Y = (1, 24)
EXU_SUD = (118, 122, 139, 146)
EXU_OUEST = (98, 122, 142, 146)
EXU_PUITS = (98, 102, 142, 146)
LAC = (94, 106, 138, 150)


def cyl(c, r, x_marge=2):
    for x in range(int(c[0] - r) - x_marge, int(c[0] + r) + x_marge + 1):
        for z in range(int(c[1] - r) - x_marge, int(c[1] + r) + x_marge + 1):
            if r_de(x, z, c) < r:
                yield x, z


def tube(a_deg, r0, r1, demi):
    """Les colonnes (x, z) d'un tube droit qui part du centre de la Cuve selon l'azimut a_deg, entre les rayons r0 et r1, de demi-largeur `demi`."""
    a = math.radians(a_deg)
    ux, uz = math.sin(a), -math.cos(a)
    for x in range(int(CUVE[0] - r1) - 2, int(CUVE[0] + r1) + 3):
        for z in range(int(CUVE[1] - r1) - 2, int(CUVE[1] + r1) + 3):
            dx, dz = x + 0.5 - CUVE[0], z + 0.5 - CUVE[1]
            le_long, en_travers = dx * ux + dz * uz, abs(-dx * uz + dz * ux)
            if r0 <= le_long < r1 and en_travers <= demi:
                yield x, z, le_long


STYLES = {
    "station_drain": lambda x, y, z: S("waxed_oxidized_cut_copper") if (x + y + z) % 5 else S("waxed_oxidized_copper"),
    "station_a": lambda x, y, z: S("smooth_sandstone") if y % 4 else S("white_concrete"),
    "station_b": lambda x, y, z: S("waxed_weathered_copper") if (x + z) % 3 else S("bricks"),
    "station_c": lambda x, y, z: S("prismarine_bricks") if y % 6 else S("dark_prismarine"),
    "station_cheminee": lambda x, y, z: S("tuff_bricks") if y % 5 else S("waxed_cut_copper"),
    "station_reservoir": lambda x, y, z: S("deepslate_tiles") if (x + z) % 7 else S("cracked_deepslate_tiles"),
}
LAMPES = {"station_drain": S("sea_lantern"), "station_a": S("verdant_froglight", axis="y"), "station_b": S("ochre_froglight", axis="y"), "station_c": S("sea_lantern"),
          "station_cheminee": S("sea_lantern"), "station_reservoir": S("sea_lantern")}


@lru_cache(maxsize=None)
def construire():
    base, infos1 = a1.construire()
    creux, parts, sources = {}, {}, []          # creux : {case: (structure, bloc)} ; parts : {structure: {case: bloc}}

    def vider(nom, cases, bloc):
        for p in cases:
            creux[p] = (nom, bloc)

    def boite(x0, x1, y0, y1, z0, z1):
        return [(x, y, z) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1) for z in range(z0, z1 + 1)]

    def poser(nom, p, bloc):
        parts.setdefault(nom, {})[p] = bloc

    # ======================================================== les volumes
    # le Drain
    x0, x1, z0, z1 = DRAIN_PUITS
    vider("station_drain", boite(x0, x1, DRAIN_Y[0], a1.SOL - 3, z0, z1), EAU)           # jusqu'au fond du bassin d'essai (perce)
    for (x0, x1, z0, z1) in (DRAIN_NS, DRAIN_EO):
        vider("station_drain", boite(x0, x1, DRAIN_Y[0], DRAIN_Y[1], z0, z1), EAU)
    # secteur A
    ex0, ex1, ez0, ez1, ey0, ey1 = ECL_A
    vider("station_a", boite(ex0, ex1, ey0, ey1, ez0, ez1), EAU)
    vider("station_a", boite(VANNE_INT_A, VANNE_INT_A, ey0, ey1, ez0, ez1), EAU)
    vider("station_a", boite(VEST_A[0], VEST_A[1], VEST_A[4], VEST_A[5], VEST_A[2], VEST_A[3]), EAU)
    vider("station_a", boite(GAL_A[0], GAL_A[1], GAL_A[4], GAL_A[5], GAL_A[2], GAL_A[3]), EAU)
    vider("station_a", boite(A3[0], A3[1], A3[4], A3[5], A3[2], A3[3]), EAU)
    vider("station_a", boite(A4[0], A4[1], A4[4], A4[5], A4[2], A4[3]), EAU)
    vider("station_a", boite(A4[1] + 1, A3[0] - 1, 61, 66, 20, 26), EAU)
    vider("station_a", boite(CACHE[0], CACHE[1], CACHE[4], CACHE[5], CACHE[2], CACHE[3]), EAU)
    vider("station_a", boite(FENTE[0], FENTE[0], 62, 63, FENTE[1], FENTE[2]), EAU)
    # secteur B
    ex0, ex1, ez0, ez1, ey0, ey1 = ECL_B
    vider("station_b", boite(ex0, ex1, ey0, ey1, ez0, ez1), EAU)
    vider("station_b", boite(VANNE_INT_B, VANNE_INT_B, ey0, ey1, ez0, ez1), EAU)
    sol_coll = {}
    for x in range(COLL[0], COLL[1] + 1):
        sol_coll[x] = 31 + (x - COLL[0]) * 8 // (COLL[1] - COLL[0])
        vider("station_b", boite(x, x, sol_coll[x], sol_coll[x] + 5, COLL[2], COLL[3]), EAU)
    vider("station_b", boite(B2[0], B2[1], B2[4], B2[5], B2[2], B2[3]), EAU)
    vider("station_b", boite(COLONNE_B[0], COLONNE_B[1], B2[5] + 1, TREMPE[4] - 1, COLONNE_B[2], COLONNE_B[3]), EAU)
    vider("station_b", boite(B3[0], B3[1], B3[4], B3[5], B3[2], B3[3]), AIR)
    vider("station_b", boite(TREMPE[0], TREMPE[1], TREMPE[4], TREMPE[5], TREMPE[2], TREMPE[3]), EAU)
    sx, sz = SORTIE_B
    vider("station_b", boite(sx, sx, B2[5] + 1, B3[4] - 1, sz, sz), AIR)
    # secteur C
    ex0, ex1, ez0, ez1, ey0, ey1 = ECL_C
    vider("station_c", boite(ex0, ex1, ey0, ey1, ez0, ez1), EAU)
    vider("station_c", boite(ex0, ex1, ey0, ey1, VANNE_INT_C - 3, VANNE_INT_C), EAU)
    for (x, z) in cyl(PUITS_C, R_PUITS_C):
        vider("station_c", boite(x, x, 48, EAU_C, z, z), EAU)
        vider("station_c", boite(x, x, EAU_C + 1, 95, z, z), AIR)
    s0, s1, s2, s3, s4, s5 = SALLE_C
    vider("station_c", boite(s0, s1, s4, s5, s2, s3), AIR)
    vider("station_c", boite(117, 123, s4, s4 + 3, s3 + 1, 15), AIR)
    # la Cheminee
    galerie_d = {}
    for (x, z, ll) in tube(40, GAL_D[0], GAL_D[1], 1.5):
        galerie_d[(x, z)] = ll
        vider("station_cheminee", boite(x, x, 31, 33, z, z), EAU)
    for (x, z) in cyl(CHEM, R_CHEM):
        vider("station_cheminee", boite(x, x, 31, 69, z, z), EAU)
    bx0, bx1, bz0, bz1 = BASSIN_CHEM
    vider("station_cheminee", boite(bx0, bx1, 70, 76, bz0, bz1), EAU)
    vider("station_cheminee", boite(bx0, bx1, 77, 83, bz0, bz1), AIR)
    cx0, cx1, cz0, cz1 = CHAMBRE
    vider("station_cheminee", boite(cx0, cx1, 85, 92, cz0, cz1), AIR)
    for (t0, t1) in TROUS:
        vider("station_cheminee", boite(t0, t1, 84, 84, cz0 + 1, cz1 - 1), AIR)
    for (x0, x1, z0, z1) in COULOIR_POSTE:
        vider("station_cheminee", boite(x0, x1, 85, 88, z0, z1), AIR)
    # le Reservoir et l'Exutoire
    for (x, z) in cyl(CUVE, R_RES):
        vider("station_reservoir", boite(x, x, RES_Y[0], RES_Y[1], z, z), EAU)
    for (x0, x1, z0, z1) in (EXU_SUD, EXU_OUEST):
        vider("station_reservoir", boite(x0, x1, 1, 4, z0, z1), EAU)
    x0, x1, z0, z1 = EXU_PUITS
    vider("station_reservoir", boite(x0, x1, 1, a1.PLAINE - 4, z0, z1), EAU)
    x0, x1, z0, z1 = LAC
    vider("station_reservoir", boite(x0, x1, a1.PLAINE - 3, a1.PLAINE, z0, z1), EAU)

    # on ne touche jamais au vide de l'acte I (la Cuve reste seche avant la mise en eau) ; ses parois, on peut les percer (le Drain, le couloir du Poste)
    for p in list(creux):
        if p in base and base[p][0] == "minecraft:air":
            del creux[p]

    # ======================================================== les parois
    for p, (nom, _) in creux.items():
        poser(nom, p, creux[p][1])
    for p, (nom, _) in sorted(creux.items()):
        x, y, z = p
        for q in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
            if q in creux or q in base or any(q in parts[n] for n in parts):
                continue
            if nom == "station_reservoir" and q[1] >= a1.PLAINE:
                continue
            poser(nom, q, STYLES[nom](*q))

    def bloc(p):
        for n in parts:
            if p in parts[n]:
                return parts[n][p]
        return base.get(p)

    def mur(p):
        b = bloc(p)
        return b is not None and b[0] not in ("minecraft:air", "minecraft:water") and p not in creux

    # ======================================================== la lumiere : des lampes dans les parois, toutes les 6 cases, a la place d'un bloc de paroi
    for nom, cases in parts.items():
        lampe = LAMPES[nom]
        for p in list(cases):
            x, y, z = p
            if p in creux or (x * 7 + y * 3 + z * 5) % 23:
                continue
            voisins = [q for q in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)) if q in creux]
            if voisins:
                cases[p] = lampe
                sources.append(p)

    # ======================================================== les decors et les mecanismes
    infos = {"sources": sources}
    # --- le Drain : une echelle dans le puits (contre la paroi nord), le bassin d'essai perce
    x0, x1, z0, z1 = DRAIN_PUITS
    echelle_drain = [(x0 + 1, y, z1) for y in range(DRAIN_Y[1] + 1, a1.SOL - 2)]
    for p in echelle_drain:
        poser("station_drain", p, S("ladder", facing="north", waterlogged=True))
    infos["drain"] = {"poussees": [((x0, x1, DRAIN_Y[1] + 1, a1.SOL - 3, z0, z1), (0, 0.8, 0)),
                                   ((DRAIN_NS[0], DRAIN_NS[1], DRAIN_Y[0], DRAIN_Y[1], DRAIN_NS[2], z1), (0, 0, 0.8)),
                                   ((DRAIN_EO[0], DRAIN_EO[1], DRAIN_Y[0], DRAIN_Y[1], DRAIN_EO[2], DRAIN_EO[3]), (0.8, 0, 0))],
                      "bouche": ((x0 + x1) // 2, a1.SOL - 3, (z0 + z1) // 2), "arrivee": (DRAIN_EO[0] + 8, DRAIN_Y[0], (DRAIN_EO[2] + DRAIN_EO[3]) // 2)}

    # --- les ecluses : vanne exterieure = la bouche de la Cuve (acte I), vanne interieure = des barreaux
    def barreaux(nom, cases):
        for p in cases:
            poser(nom, p, S("iron_bars", waterlogged=True))
        return cases

    ecluses = {}
    ex0, ex1, ez0, ez1, ey0, ey1 = ECL_A
    ecluses["A"] = {"structure": "station_a", "externe": infos1["bouches"]["A"], "interne": barreaux("station_a", boite(VANNE_INT_A, VANNE_INT_A, ey0, ey1, ez0, ez1)),
                    "sas": boite(ex0, ex1, ey0, ey1, ez0, ez1), "haut": ey1,
                    "zone_ext": (ex1 - 2, 91, ey0, ey1, ez0, ez1), "zone_int": (VEST_A[0], ex0 + 2, ey0, ey1, ez0, ez1)}
    ex0, ex1, ez0, ez1, ey0, ey1 = ECL_B
    ecluses["B"] = {"structure": "station_b", "externe": infos1["bouches"]["B"], "interne": barreaux("station_b", boite(VANNE_INT_B, VANNE_INT_B, ey0, ey1, ez0, ez1)),
                    "sas": boite(ex0, ex1, ey0, ey1, ez0, ez1), "haut": ey1,
                    "zone_ext": (147, ex0 + 2, ey0, ey1, ez0, ez1), "zone_int": (ex1 - 2, VANNE_INT_B + 4, ey0, ey1, ez0, ez1)}
    ex0, ex1, ez0, ez1, ey0, ey1 = ECL_C
    ecluses["C"] = {"structure": "station_c", "externe": infos1["bouches"]["C"], "interne": barreaux("station_c", boite(ex0, ex1, ey0, ey1, VANNE_INT_C, VANNE_INT_C)),
                    "sas": boite(ex0, ex1, ey0, ey1, ez0, ez1), "haut": ey1,
                    "zone_ext": (ex0, ex1, ey0, ey1, ez1 - 2, 67), "zone_int": (ex0, ex1, ey0, ey1, VANNE_INT_C - 3, ez0 + 2)}
    for e in ecluses.values():
        for p in e["sas"]:
            creux[p] = (e["structure"], EAU)
    infos["ecluses"] = ecluses

    # --- secteur A
    vannes_a = []                                                                  # plus de vannes chronometrees (demande de Luca) : la galerie est libre
    for (x0, x1, z0, z1) in LITS:                                                   # les lits de filtration : sable, gravier, une arete de gres
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                for y in range(61, 69):
                    poser("station_a", (x, y, z), S("sand") if y < 65 else (S("gravel") if y < 68 else S("smooth_sandstone")))
    for (x, y, z) in boite(A3[0], A3[1], 60, 60, A3[2], A3[3]) + boite(A4[0], A4[1], 60, 60, A4[2], A4[3]):
        if (x, y, z) in parts["station_a"] and (x, y, z) not in creux and parts["station_a"][(x, y, z)][0] not in ("minecraft:verdant_froglight",):
            parts["station_a"][(x, y, z)] = S("sandstone") if (x + z) % 2 else S("white_concrete")
    px, pz = POMPE_A
    pompe_a = []
    for y in range(61, 67):                                                         # la pompe : un corps de cuivre et son volant
        for (dx, dz) in ((0, 0), (1, 0), (0, 1), (1, 1)):
            poser("station_a", (px + dx, y, pz + dz), S("waxed_copper_block") if y % 2 else S("waxed_cut_copper"))
            pompe_a.append((px + dx, y, pz + dz))
    vannes_cache = []                                                              # la fente de la chambre cachee est libre
    coffre_a = (52, 62, 50)
    poser("station_a", coffre_a, S("chest", facing="north", type="single", waterlogged=True))
    infos["a"] = {"vannes": vannes_a, "depart": (GAL_A[0], GAL_A[1], 64, 66, 88, 91), "relances": [(GAL_A[0], GAL_A[1], 64, 66, z0, z1) for (z0, z1) in ((75, 78), (63, 66), (51, 54))],
                  "a3": A3, "a4": A4, "gardiens_a3": [(40, 66, 10), (52, 66, 36), (66, 66, 12), (36, 70, 38)], "noyes_a3": [(26, 63, 20), (70, 63, 30)],
                  "gardiens_a4": [(6, 66, 15), (6, 66, 31)], "volant": (px + 2, 61, pz, px + 4, 64, pz + 1), "pompe": pompe_a, "fente": (FENTE[0] - 1, FENTE[0] + 1, 62, 63, 40, 42),
                  "vannes_cache": vannes_cache, "coffre": coffre_a}

    # --- secteur B
    for x in range(COLL[0], COLL[1] + 1):                                           # le collecteur : des marches d'escalier (on les monte sans sauter, a contre-courant)
        for z in range(COLL[2], COLL[3] + 1):
            y = sol_coll[x]
            if x > COLL[0] and y > sol_coll[x - 1]:
                poser("station_b", (x, y - 1, z), S("waxed_cut_copper"))
                poser("station_b", (x - 1, y - 1, z), S("waxed_cut_copper_stairs", facing="east", half="bottom", shape="straight", waterlogged=True))
    chaud = []
    for (cx, cz) in CHAUDIERES:
        for (x, z) in cyl((cx + 0.5, cz + 0.5), 3.5, 0):
            for y in range(40, 48):
                bord = r_de(x, z, (cx + 0.5, cz + 0.5)) > 2.6
                poser("station_b", (x, y, z), S("waxed_copper_block") if bord and y not in (43, 44) else (S("iron_block") if bord else S("blast_furnace", facing="north", lit=True)))
                chaud.append((x, y, z))
    plaques = []
    for (px, pz) in PLAQUES:
        cases = []
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                poser("station_b", (px + dx, 39, pz + dz), S("waxed_cut_copper"))
                cases.append((px + dx, 40, pz + dz))
        plaques.append(cases)
    jets = {}
    for k, (x0, x1, z0, z1, cote) in CAGES.items():
        porte = []
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    for y in range(40, 45):
                        p = (x, y, z)
                        poser("station_b", p, S("iron_bars", waterlogged=True))
                        if (cote == "s" and z == z1 or cote == "n" and z == z0) and x0 < x < x1:
                            porte.append(p)
                for y in (45,):
                    poser("station_b", (x, y, z), S("iron_bars", waterlogged=True))
        jets[k] = porte
    vanne_colonne = barreaux("station_b", boite(COLONNE_B[0], COLONNE_B[1], B2[5] + 1, B2[5] + 1, COLONNE_B[2], COLONNE_B[3]))
    cxb, czb = (COLONNE_B[0] + COLONNE_B[1]) // 2, (COLONNE_B[2] + COLONNE_B[3]) // 2
    sable_b = boite(cxb - 1, cxb + 1, 39, 39, czb - 1, czb + 1)                     # le courant qui monte, au centre seulement : on redescend par l anneau calme autour (Luca, 18/11)
    for (x, y, z) in boite(B3[0], B3[1], 57, 57, B3[2], B3[3]):                     # le sol de la halle haute : briques et cuivre
        if (x, y, z) in parts["station_b"] and (x, y, z) not in creux and parts["station_b"][(x, y, z)][0] != "minecraft:ochre_froglight":
            parts["station_b"][(x, y, z)] = S("waxed_cut_copper") if (x + z) % 4 == 0 else S("bricks")
    ancrages_b = []
    for i, (x0, x1) in enumerate(PASSERELLES):
        for x in range(x0, x1 + 1):
            for z in range(Z_PASS[0], Z_PASS[1] + 1):
                poser("station_b", (x, Y_PASS, z), S("waxed_cut_copper") if i else S("bricks"))
        if i:
            ancrages_b.append(("b_pass%d" % i, (x0 + x1) // 2, Y_PASS + 4, (Z_PASS[0] + Z_PASS[1]) // 2))
    for k in range(Y_PASS - 57):                                                    # l'escalier qui monte a la premiere passerelle, contre la paroi ouest
        for z in range(Z_PASS[0], Z_PASS[1] + 1):
            poser("station_b", (PASSERELLES[0][0] + 1 + k, 58 + k, z - 4), S("brick_stairs", facing="east", half="bottom", shape="straight", waterlogged=False))
            for y in range(57, 58 + k):
                poser("station_b", (PASSERELLES[0][0] + 1 + k, y, z - 4), S("bricks"))
    for x in range(PASSERELLES[0][0], PASSERELLES[0][1] + 1):
        for z in range(Z_PASS[0] - 4, Z_PASS[0]):
            if x >= PASSERELLES[0][0] + 1 + Y_PASS - 58:
                poser("station_b", (x, Y_PASS, z), S("bricks"))
    for x in range(ILOT[0], ILOT[1] + 1):
        for z in range(ILOT[2], ILOT[3] + 1):
            poser("station_b", (x, Y_PASS, z), S("waxed_cut_copper"))
    ancrages_b.append(("b_ilot", (ILOT[0] + ILOT[1]) // 2, Y_PASS + 4, (ILOT[2] + ILOT[3]) // 2))
    for (nom, x, y, z) in ancrages_b:                                               # sous une poutre de fer, tenus au plafond par une chaine
        poser("station_b", (x, y, z), S("donjon:ancrage"))
        for yy in range(y + 1, B3[5] + 1):
            poser("station_b", (x, yy, z), S("chain", axis="y", waterlogged=False))
    for z in range(B3[2], B3[3] + 1):
        for (nom, x, y, zz) in ancrages_b:
            poser("station_b", (x, B3[5], z), S("iron_block"))
    coffre_b = ((ILOT[0] + ILOT[1]) // 2, Y_PASS + 1, ILOT[2])
    poser("station_b", coffre_b, S("chest", facing="south", type="single", waterlogged=False))
    levier_b = (PASSERELLES[-1][1] - 1, Y_PASS + 1, Z_PASS[0] + 1)
    poser("station_b", levier_b, S("lever", face="floor", facing="west", powered=False))
    pompe_b = []
    for y in range(Y_PASS + 1, Y_PASS + 6):
        for (dx, dz) in ((0, 0), (0, 1)):
            p = (PASSERELLES[-1][1], y, Z_PASS[0] + 2 + dz)
            poser("station_b", p, S("waxed_copper_block") if y % 2 else S("waxed_cut_copper"))
            pompe_b.append(p)
    sx, sz = SORTIE_B
    echelle_b = [(sx, y, sz) for y in range(B2[5] + 2, B3[4])]
    for p in echelle_b:
        poser("station_b", p, S("ladder", facing="south", waterlogged=False))
    vanne_sortie = barreaux("station_b", [(sx, B2[5] + 1, sz)])
    vapeur = [(PASSERELLES[1][0], PASSERELLES[1][1], Y_PASS + 1, Y_PASS + 2, Z_PASS[0], Z_PASS[1]), (PASSERELLES[2][0], PASSERELLES[2][1], Y_PASS + 1, Y_PASS + 2, Z_PASS[0], Z_PASS[1])]
    infos["b"] = {"collecteur": ((COLL[0], COLL[1], 31, 44, COLL[2], COLL[3]), (-0.5, 0, 0)), "b2": B2, "noyes": [(195, 44, 85), (215, 44, 104), (205, 44, 115), (224, 44, 90)],
                  "plaques": plaques, "jets": jets, "vanne_colonne": vanne_colonne, "sable": sable_b, "b3": B3, "blazes": [(200, 61, 76), (222, 61, 116), (210, 61, 116)],
                  "vapeur": vapeur, "levier": levier_b, "pompe": pompe_b, "vanne_sortie": vanne_sortie, "coffre": coffre_b, "ancrages": ancrages_b,
                  "depart_b3": (192, 58, 100), "chaudieres": chaud}

    # --- secteur C : deux grilles de cuivre (le tartre bouche leur trou), l'arbre des turbines, la salle de la pompe en haut
    cx, cz = PUITS_C
    tartres = {}
    for i, y in enumerate(GRILLES_C):
        cases = []
        for (x, z) in cyl(PUITS_C, R_PUITS_C):
            p = (x, y, z)
            if TARTRE_C[0] <= x <= TARTRE_C[1] and TARTRE_C[2] <= z <= TARTRE_C[3]:
                poser("station_c", p, S("dripstone_block"))
                cases.append(p)
            else:
                poser("station_c", p, S("waxed_copper_grate", waterlogged=True))
        tartres[i + 1] = cases
    axe = [(int(cx), y, int(cz)) for y in range(48, EAU_C + 1)]
    for p in axe:
        poser("station_c", p, S("waxed_copper_block"))
    pales = {}
    for k, y in enumerate(TURBINES_C, start=1):
        croix = [(int(cx) + d, y, int(cz)) for d in (-3, -2, -1, 1, 2, 3)] + [(int(cx), y, int(cz) + d) for d in (-3, -2, -1, 1, 2, 3)]
        diag = [(int(cx) + d, y, int(cz) + e) for d in (-2, -1, 1, 2) for e in (-2, -1, 1, 2) if abs(d) == abs(e)]
        for p in croix:
            poser("station_c", p, S("waxed_cut_copper"))
        pales[k] = {"croix": croix, "diag": diag, "centre": (int(cx), y, int(cz))}
    sable_c = [(x, 47, z) for x in range(TARTRE_C[0] + 1, TARTRE_C[1]) for z in range(TARTRE_C[2] + 1, TARTRE_C[3])]
    s0, s1, s2, s3, s4, s5 = SALLE_C
    for (x, y, z) in boite(s0, s1, s4 - 1, s4 - 1, s2, s3):
        poser("station_c", (x, y, z), S("dark_prismarine") if (x + z) % 2 else S("prismarine_bricks"))
    echelle_c = [(x, y, 16) for x in (119, 120, 121) for y in range(EAU_C - 2, s4)]
    for p in echelle_c:
        poser("station_c", p, S("ladder", facing="south", waterlogged=p[1] <= EAU_C))
    for y in range(EAU_C - 2, s4):
        for x in (119, 120, 121):
            if (x, y, 15) in creux:
                del creux[(x, y, 15)]
            poser("station_c", (x, y, 15), S("prismarine_bricks"))
    grille_salle = barreaux("station_c", boite(117, 123, s4, s4 + 3, 14, 14))
    levier_c = (120, s4, 6)
    poser("station_c", levier_c, S("lever", face="floor", facing="north", powered=False))
    pompe_c = []
    for y in range(s4, s4 + 5):
        for x in (114, 115):
            for z in (5, 6, 7):
                poser("station_c", (x, y, z), S("waxed_copper_block") if y % 2 else S("waxed_cut_copper"))
                pompe_c.append((x, y, z))
    coffre_c = (126, s4, 3)
    poser("station_c", coffre_c, S("chest", facing="west", type="single", waterlogged=False))
    infos["c"] = {"puits": (cx, cz), "rayon": R_PUITS_C, "tartres": tartres, "pales": pales, "sable": sable_c, "centre_tartre": ((TARTRE_C[0] + TARTRE_C[1]) // 2, 0, (TARTRE_C[2] + TARTRE_C[3]) // 2),
                  "grilles": GRILLES_C, "eau": EAU_C, "salle": SALLE_C, "grille_salle": grille_salle, "levier": levier_c, "pompe": pompe_c, "coffre": coffre_c,
                  "vagues": {1: [(112, 54, 40), (128, 54, 28), (126, 54, 44), (114, 56, 26)], 2: [(112, 68, 40), (128, 68, 28), (126, 70, 44), (114, 70, 26)],
                             3: [(112, 79, 40), (128, 79, 28), (126, 79, 44), (114, 79, 26)]}, "rotor": (126, 70, 34)}

    # --- la Cheminee : galerie aux 3 vannes, rampe en lacets (dalles : on la monte sans sauter), tartre, turbine, bassin, chambre aux trous, Poste
    vannes_d = []
    for v in VANNES_D:
        cases = [(x, y, z) for (x, z), ll in galerie_d.items() if v <= ll < v + 1 for y in range(31, 34)]
        pass                                                                       # la galerie de la Cheminee est libre
    rampe = []
    hx, hz = CHEM
    for (x, z) in cyl(CHEM, R_CHEM):
        r = r_de(x, z, CHEM)
        if r < 4.5:
            continue
        theta = math.degrees(math.atan2(x + 0.5 - hx, -(z + 0.5 - hz))) % 360
        for tour in range(3):
            h = 31 + (theta / 360 + tour) * 10
            if h > 60.5:
                continue
            s = round(h * 2) / 2
            n = int(math.floor(s))
            if s == n:
                poser("station_cheminee", (x, n - 1, z), S("tuff_bricks"))
                rampe.append((x, n, z))
            else:
                poser("station_cheminee", (x, n - 1, z), S("tuff_bricks"))
                poser("station_cheminee", (x, n, z), S("tuff_brick_slab", type="bottom", waterlogged=True))
                rampe.append((x, n + 1, z))
    tartre_d = []
    for (x, z) in cyl(CHEM, R_CHEM):
        poser("station_cheminee", (x, 63, z), S("dripstone_block"))
        tartre_d.append((x, 63, z))
    turbine_d = {"centre": (int(hx), 66, int(hz)), "croix": [(int(hx) + d, 66, int(hz)) for d in (-2, -1, 1, 2)] + [(int(hx), 66, int(hz) + d) for d in (-2, -1, 1, 2)],
                 "diag": [(int(hx) + d, 66, int(hz) + e) for d in (-1, 1) for e in (-1, 1)]}
    poser("station_cheminee", turbine_d["centre"], S("waxed_copper_block"))
    for p in turbine_d["croix"]:
        poser("station_cheminee", p, S("waxed_cut_copper"))
    sable_d = [(int(hx) + dx, 63, int(hz) + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1)]
    cx0, cx1, cz0, cz1 = CHAMBRE
    for x in range(cx0, cx1 + 1):                                                   # le sol de la chambre (les trous sont creuses), vitres au plafond
        for z in range(cz0, cz1 + 1):
            if (x, 84, z) not in creux:
                poser("station_cheminee", (x, 84, z), S("polished_tuff") if (x + z) % 2 else S("waxed_cut_copper"))
    ancrages_d = [("d_rebord", 177, 88, 46), ("d_f1", 167, 88, 46), ("d_f2", 158, 88, 46), ("d_poste", 147, 88, 46)]
    for (nom, x, y, z) in ancrages_d:
        poser("station_cheminee", (x, y, z), S("donjon:ancrage"))
        for yy in range(y + 1, 93):
            poser("station_cheminee", (x, yy, z), S("chain", axis="y", waterlogged=False))
    for x in range(POSTE[0], POSTE[1] + 1):                                          # le Poste : une cabine vitree (sa vitre sud regarde la Cuve), des cadrans, le levier general
        for y in (87, 88, 89):
            if (x, y, cz1 + 1) not in creux:
                poser("station_cheminee", (x, y, cz1 + 1), S("light_blue_stained_glass"))
    for z in (40, 44, 48):
        poser("station_cheminee", (POSTE[0] - 1, 87, z), S("target"))
        poser("station_cheminee", (POSTE[0] - 1, 88, z), S("waxed_copper_bulb", lit=True, powered=False))
    levier_g = (POSTE[0] + 2, 85, 46)
    poser("station_cheminee", levier_g, S("lever", face="floor", facing="east", powered=False))
    porte_poste = barreaux("station_cheminee", [(PORTE_POSTE, y, z) for y in range(85, 89) for z in range(64, 67)])
    for p in porte_poste:
        poser("station_cheminee", p, S("iron_bars", waterlogged=False))
    # le plongeoir final, sur la Coursive en face du Poste : une ouverture du garde-corps et deux dalles
    plongeoir = []
    a = math.radians(40)
    for (x, y, z), b in list(base.items()):
        if y in (SOL + 1, SOL + 2) and b[0] == "minecraft:iron_bars" and a1.R_CUVE <= r_de(x, z, CUVE) < a1.R_CUVE + 1 and abs((az(x, z, CUVE) - 40 + 180) % 360 - 180) < 3.5:
            poser("station_cheminee", (x, y, z), AIR)
    for d in (28.5, 29.5):
        x, z = int(math.floor(CUVE[0] + d * math.sin(a))), int(math.floor(CUVE[1] - d * math.cos(a)))
        poser("station_cheminee", (x, SOL, z), S("quartz_slab", type="top", waterlogged=False))
        plongeoir.append((x, SOL + 1, z))
    bonde = [(x, a1.FOND_CUVE, z) for (x, z) in cyl(CUVE, 5.0)]
    puits_bonde = [(x, y, z) for (x, z) in cyl(CUVE, 5.0) for y in range(RES_Y[1] + 1, a1.FOND_CUVE)]
    magma = [(int(CUVE[0]) + dx, 0, int(CUVE[1]) + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1)]
    infos["d"] = {"vanne_d": infos1["bouches"]["D"], "vannes": vannes_d, "depart": [(x, y, z) for (x, z), ll in galerie_d.items() if ll < 35 for y in range(31, 34)],
                  "rampe_boite": (int(hx - R_CHEM) - 1, int(hx + R_CHEM) + 1, 31, 58, int(hz - R_CHEM) - 1, int(hz + R_CHEM) + 1), "rampe": rampe, "tartre": tartre_d,
                  "centre_tartre": (int(hx), 61, int(hz)), "turbine": turbine_d, "sable": sable_d, "ancrages": ancrages_d, "levier": levier_g, "porte_poste": porte_poste,
                  "plongeoir": plongeoir, "bonde": bonde, "puits_bonde": puits_bonde, "magma": magma, "rebord": (177, 85, 46), "poste": (POSTE[0] + 3, 85, 46),
                  "galerie_bas": [(x, 31, z) for (x, z), ll in galerie_d.items()]}

    # --- le Reservoir : onze piliers en ruine, quatre bondes de fond, un coffre ; l'Exutoire ferme
    piliers = [(0, 13), (120, 13), (240, 13)] + [(k * 45 + 22, 30) for k in range(8)]
    for i, (ang, r) in enumerate(piliers):
        a = math.radians(ang)
        pcx, pcz = CUVE[0] + r * math.sin(a), CUVE[1] - r * math.cos(a)
        haut = 10 + (i * 7) % 15
        for (x, z) in cyl((pcx, pcz), 2.6, 0):
            for y in range(1, 1 + haut + ((x + z) % 3)):
                poser("station_reservoir", (x, y, z), S("cracked_deepslate_bricks") if (x + y + z) % 3 else S("mossy_stone_bricks"))
            if (x + z) % 5 == 0:
                poser("station_reservoir", (x, 1 + haut + 1, z), S("sea_lantern"))
                sources.append((x, 1 + haut + 1, z))
    bondes_fond = []
    for ang in (0, 90, 180, 270):
        a = math.radians(ang)
        bx, bz = CUVE[0] + 38 * math.sin(a), CUVE[1] - 38 * math.cos(a)
        for (x, z) in cyl((bx, bz), 3.0, 0):
            poser("station_reservoir", (x, 0, z), S("waxed_copper_grate", waterlogged=False) if r_de(x, z, (bx, bz)) < 2 else S("waxed_cut_copper"))
            bondes_fond.append((x, 0, z))
    for (x, z) in cyl(CUVE, R_RES):
        if (x, 0, z) not in parts["station_reservoir"] or parts["station_reservoir"][(x, 0, z)][0] == "minecraft:deepslate_tiles":
            if (x * 3 + z * 5) % 37 == 0:
                poser("station_reservoir", (x, 0, z), S("sea_lantern"))
                sources.append((x, 0, z))
    coffre_r = (100, 1, 120)
    poser("station_reservoir", coffre_r, S("chest", facing="north", type="single", waterlogged=True))
    exutoire = barreaux("station_reservoir", boite(EXU_SUD[0], EXU_SUD[1], 1, 4, 140, 140))
    x0, x1, z0, z1 = EXU_PUITS
    grille_puits = barreaux("station_reservoir", boite(x0, x1, 6, 6, z0, z1))
    sable_r = boite(x0 + 1, x1 - 1, 0, 0, z0 + 1, z1 - 1)
    x0, x1, z0, z1 = LAC
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            if not (x0 <= x <= x1 and z0 <= z <= z1):
                poser("station_reservoir", (x, a1.PLAINE, z), S("sand"))
    infos["r"] = {"centre": CUVE, "rayon": R_RES, "y": RES_Y, "arrivee": (int(CUVE[0]), 20, int(CUVE[1])), "boss": (int(CUVE[0]) + 10, 12, int(CUVE[1]) - 10), "exutoire": exutoire,
                  "grille_puits": grille_puits, "sable": sable_r, "coffre": coffre_r, "lac": ((LAC[0] + LAC[1]) // 2, a1.PLAINE + 1, (LAC[2] + LAC[3]) // 2), "bondes_fond": bondes_fond}

    # la lumiere dans les grands volumes : des blocs de lumiere invisibles, un tous les 6 blocs dans les trois sens (aucun point a plus de 9 pas d'une source)
    for nom, cases in parts.items():
        for p, b in list(cases.items()):
            x, y, z = p
            if x % 6 == 0 and y % 6 == 3 and z % 6 == 0 and b[0] in ("minecraft:water", "minecraft:air") and p[1] < a1.PLAINE:
                cases[p] = S("light", level=15, waterlogged=b[0] == "minecraft:water")
                sources.append(p)

    for nom, p in (("station_reservoir", (148, 24, 83)), ("station_reservoir", (146, 24, 82)), ("station_reservoir", (147, 24, 84)), ("station_cheminee", (158, 64, 45))):       # deux recoins que la grille n'atteint pas (verif_station_suite)
        if parts[nom].get(p, ("",))[0] == "minecraft:water":
            parts[nom][p] = S("light", level=15, waterlogged=True)
            sources.append(p)

    infos["ancrages"] = ancrages_b + ancrages_d
    infos["creux"] = creux
    import station_create
    station_create.decor_secteurs(parts, infos)
    return parts, infos


def tout():
    """L'acte I et la suite poses ensemble (la suite ecrase l'acte I la ou elle passe) : {case: bloc}."""
    base, _ = a1.construire()
    parts, _ = construire()
    blocs = dict(base)
    for nom in ORDRE:
        blocs.update(parts[nom])
    import station_retouches
    blocs.update(station_retouches.charger())                              # les retouches a la main de Luca, par-dessus tout
    return blocs


ORDRE = ("station_drain", "station_a", "station_b", "station_c", "station_cheminee", "station_reservoir")
