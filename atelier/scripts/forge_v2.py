# [claude-build]
"""
forge_v2.py - Arene PvP "Forge v2" : champ de bataille infernal (feu / lave / Nether).
Cercle de 252 blocs de diametre, centre ENTRE 4 blocs (X=400, Z=0 tombe sur un coin de bloc),
Construit en 7 phases, chacune verifiee avant la suivante.

Parametres (monde, centre, sol) en ligne de commande ; par defaut : monde Arene, centre X0 Z0, sol Y187.
  .venv\\Scripts\\python.exe sources\\forge_v2.py --monde Arene --centre 0 0 --sol 187 --sous-sol sandstone
Pose (coordonnees absolues, affichees a la fin du script) :
  /tp @s <cx> <sol+60> <cz>
  /place template atelier:forge_v2 <cx-126> <sol-12> <cz-126>
"""
import argparse
import math
import random
from collections import Counter
from pathlib import Path

import nbtlib
from nbtlib import Compound, Int, List, String

ap = argparse.ArgumentParser()
ap.add_argument("--monde", default="Arene")
ap.add_argument("--centre", nargs=2, type=int, default=[0, 0], metavar=("X", "Z"))
ap.add_argument("--sol", type=int, default=187, help="Y du bloc de surface du monde (herbe)")
ap.add_argument("--sous-sol", default="sandstone", help="bloc sous la surface, pour la fonction d'effacement")
ap.add_argument("--surface", default="grass_block", help="bloc de surface du monde, pour la fonction d'effacement")
ap.add_argument("--demi", action="store_true", help="version a l'echelle 1/2 (diametre 126) -> forge_v2_demi")
ARGS = ap.parse_args()
DEMI = ARGS.demi


def V(plein, demi):
    """cote de la version pleine (Ø252) ou de la demi-version (Ø126)"""
    return demi if DEMI else plein


NOM = V("forge_v2", "forge_v2_demi")
RACINE = Path(__file__).resolve().parent.parent
SAVE = RACINE / "launcher/instances/Build-1.21.1/minecraft/saves" / ARGS.monde
if not (SAVE / "level.dat").exists():
    raise SystemExit(f"monde introuvable : {SAVE}")
DATAPACK = SAVE / "datapacks/atelier"
SORTIES = [DATAPACK / "data/atelier/structure" / f"{NOM}.nbt", RACINE / "exports" / f"{NOM}.nbt"]
FONCTION = DATAPACK / "data/atelier/function" / f"{NOM}_effacer.mcfunction"
PLAN = RACINE / "exports" / f"{NOM}_plan.png"
LISTE_SALLES = RACINE / V("salles_cachees.md", "salles_cachees_demi.md")

DATA_VERSION = 3955
SX = SZ = V(252, 126)
SY = V(56, 44)
SOL = 12                         # y local du sol (= Y ARGS.sol dans le monde)
CEN = V(126.0, 63.0)             # centre geometrique (entre 4 blocs)
R = V(126.0, 63.0)
CENTRE_MONDE = tuple(ARGS.centre)
ORIGINE = (CENTRE_MONDE[0] - int(R), ARGS.sol - SOL, CENTRE_MONDE[1] - int(R))
rng = random.Random(25202)

# ====================================================================== outils
def S(nom, **p):
    nom = nom if ":" in nom else "minecraft:" + nom
    return (nom, tuple(sorted((k, str(v).lower() if isinstance(v, bool) else str(v)) for k, v in p.items())))


AIR = S("air")
blocs = {}


def put(x, y, z, s):
    if not (0 <= x < SX and 0 <= y < SY and 0 <= z < SZ):
        raise ValueError(f"hors gabarit {(x, y, z)} {s[0]}")
    blocs[(x, y, z)] = s


def nom(p):
    s = blocs.get(p)
    return None if s is None else s[0].split(":")[1]


def dxz(x, z):
    return x + 0.5 - CEN, z + 0.5 - CEN


def dist(x, z):
    a, b = dxz(x, z)
    return math.hypot(a, b)


def ang(x, z):
    a, b = dxz(x, z)
    return math.degrees(math.atan2(b, a)) % 360


def adiff(a, b):
    return abs((a - b + 180) % 360 - 180)


def cellule(r, a):
    a = math.radians(a)
    return math.floor(CEN + r * math.cos(a)), math.floor(CEN + r * math.sin(a))


def h01(x, y, z, g=0):
    h = (x * 374761393 + y * 1442695041 + z * 668265263 + g * 2246822519) & 0xFFFFFFFF
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


def choix(table, u):
    tot = sum(p for p, _ in table)
    u *= tot
    for p, s in table:
        if u < p:
            return s
        u -= p
    return table[-1][1]


def disque(cx, cz, r):
    """cellules dont le centre est a <= r de (cx, cz) (coordonnees continues)"""
    for x in range(math.floor(cx - r - 1), math.ceil(cx + r) + 1):
        for z in range(math.floor(cz - r - 1), math.ceil(cz + r) + 1):
            if math.hypot(x + 0.5 - cx, z + 0.5 - cz) <= r:
                yield x, z


def monde(x, y, z):
    return ORIGINE[0] + x, ORIGINE[1] + y, ORIGINE[2] + z


# ====================================================================== blocs
LAVE = S("lava")
LAVE_CHUTE = S("lava", level=8)
FEU = S("fire")
FEU_AME = S("soul_fire")
TRAPPE = S("crimson_trapdoor", facing="north", half="top", open=False)
BARREAUX = S("iron_bars")
CHAINE = S("chain", axis="y")
LANT_SOL = S("soul_lantern", hanging=False)
LANT_PEND = S("lantern", hanging=True)
LANT_AME_PEND = S("soul_lantern", hanging=True)
MURET = S("blackstone_wall")
BRIQ = S("polished_blackstone_bricks")
BRIQ_F = S("cracked_polished_blackstone_bricks")
CISELE = S("chiseled_polished_blackstone")
DORE = S("gilded_blackstone")

T_AVENUE = [(55, BRIQ), (30, BRIQ_F), (8, S("polished_blackstone")), (5, CISELE), (2, DORE)]
T_PERIM = [(45, S("polished_blackstone")), (30, S("blackstone")), (25, S("basalt", axis="y"))]
T_LABY_SOL = [(40, S("soul_soil")), (15, S("soul_sand")), (30, S("blackstone")), (15, S("netherrack"))]
T_LABY_MUR = [(30, BRIQ), (20, BRIQ_F), (18, S("nether_bricks")), (10, S("cracked_nether_bricks")),
              (12, S("blackstone")), (6, S("red_nether_bricks")), (4, DORE)]
T_MUR_BAS = [(55, BRIQ), (30, BRIQ_F), (15, S("blackstone"))]
T_MUR_MIL = [(48, S("nether_bricks")), (16, S("red_nether_bricks")), (20, S("cracked_nether_bricks")),
             (16, S("blackstone"))]
T_ROCHE = [(40, S("basalt", axis="y")), (30, S("blackstone")), (15, S("smooth_basalt")), (10, S("magma_block")),
           (5, DORE)]
T_SALLE = [(50, BRIQ), (20, BRIQ_F), (12, S("blackstone")), (10, DORE), (8, S("crying_obsidian"))]
T_DEBRIS = [(25, BRIQ_F), (20, S("blackstone")), (15, S("cracked_nether_bricks")), (15, S("polished_basalt", axis="y")),
            (10, S("magma_block")), (15, MURET)]

NON_SOLIDES = {"air", "lava", "fire", "soul_fire", "weeping_vines", "weeping_vines_plant", "ladder",
               "crimson_trapdoor", "soul_lantern", "lantern", "chain", "blackstone_wall", "iron_bars",
               "polished_blackstone_brick_stairs", "blackstone_stairs"}
CONTIENT_FLUIDE = {"iron_bars"}          # bloquent la lave sans etre "solides"
INTERDITS = {"chest", "trapped_chest", "barrel", "hopper", "dropper", "dispenser", "furnace", "blast_furnace",
             "smoker", "brewing_stand", "spawner", "trial_spawner", "vault", "crafter", "decorated_pot",
             "chiseled_bookshelf", "lectern", "campfire", "soul_campfire", "jukebox", "ender_chest"}

# ====================================================================== zones
GATES = {0: "E", 90: "S", 180: "O", 270: "N"}


def axe(x, z):
    """(along, perp) par rapport aux 2 axes ; perp = distance a l'axe le plus proche"""
    a, b = dxz(x, z)
    return max(abs(a), abs(b)), min(abs(a), abs(b))


def quadrant(x, z):
    a = ang(x, z)
    return "SE" if a < 90 else ("SO" if a < 180 else ("NO" if a < 270 else "NE"))


def dans_avenue(x, z, marge=0.0):
    along, perp = axe(x, z)
    return perp <= V(4, 3) + marge and along > V(9, 5)


TOURS = {"NE": 315, "SE": 45, "SO": 135, "NO": 225}
R_TOUR = V(60, 30)
R_MUR = V(121.5, 59.5)                   # face interieure de l'enceinte
R_PERIM = V(114, 56)                     # debut du chemin au pied du mur
R_CENTRE = V(21.5, 11.5)                 # bord exterieur de la zone centrale
TABLIER = SOL + V(14, 10)                # tablier de l'aqueduc
CENTRES_TOUR = {}
for q, a in TOURS.items():
    cx, cz = cellule(R_TOUR, a)
    CENTRES_TOUR[q] = (cx + 0.5, cz + 0.5)

occ = {}                                 # colonne -> usage (pour eviter les chevauchements)


def occuper(cells, quoi):
    for c in cells:
        occ[c] = quoi


SALLES = []          # (num, coffre_local, acces, quadrant)
ECHELLES = []        # (pos echelle, pos support)
PHASES = []
FACE_ECH = {(-1, 0): "east", (1, 0): "west", (0, -1): "south", (0, 1): "north"}


def echelle(x, z, y0, y1, sup, trappes=()):
    for y in range(y0, y1 + 1):
        if y in trappes:
            put(x, y, z, TRAPPE)
            continue
        put(x, y, z, S("ladder", facing=FACE_ECH[sup]))
        sp = (x + sup[0], y, z + sup[1])
        ECHELLES.append(((x, y, z), sp))
        if nom(sp) is None or nom(sp) in NON_SOLIDES:
            put(*sp, BRIQ)


def salle(num, x0, x1, z0, z1, yf, h, coffre, acces, garder=(), niche=None, style=T_SALLE):
    """Salle cachee : interieur x0..x1, z0..z1, sol en yf, air yf+1..yf+h, plafond yf+h+1.
    coffre = (x, z) case interieure laissee libre (sol cisele dore dessous)."""
    garder = set(garder)
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            for y in range(yf, yf + h + 2):
                inter = x0 <= x <= x1 and z0 <= z <= z1 and yf < y <= yf + h
                if nom((x, y, z)) == "lava":
                    raise ValueError(f"salle {num} ecrase de la lave en {(x, y, z)}")
                if inter:
                    put(x, y, z, AIR)
                elif y == yf and x0 <= x <= x1 and z0 <= z <= z1:
                    put(x, y, z, CISELE if (x + z) % 2 else S("polished_blackstone"))
                else:
                    put(x, y, z, choix(style, h01(x, y, z, 77 + num)))
    cx, cz = coffre
    put(cx, yf, cz, DORE)
    libres = {(cx, cz)} | garder
    # decor : lanternes au sol dans les coins, plafond lumineux, chaines + lanternes pendantes
    for x, z in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
        if (x, z) not in libres and math.hypot(x - cx, z - cz) > 1.5:
            put(x, yf + 1, z, LANT_SOL)
    mx, mz = (x0 + x1) // 2, (z0 + z1) // 2
    put(mx, yf + h + 1, mz, S("shroomlight"))
    if h >= 4:
        for x, z in ((x0 + 1, mz), (x1 - 1, mz)):
            if x0 <= x <= x1 and (x, z) not in libres and math.hypot(x - cx, z - cz) > 1.5:
                put(x, yf + h, z, CHAINE)
                put(x, yf + h - 1, z, LANT_AME_PEND)
    # bande doree au niveau des yeux sur les murs
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            if not (x0 <= x <= x1 and z0 <= z <= z1) and h01(x, yf + 2, z, 5) < 0.35:
                put(x, yf + 2, z, DORE if h01(x, 1, z, 6) < 0.5 else S("crying_obsidian"))
    # niche de lave derriere des barreaux (salles souterraines)
    if niche:
        side, k = niche
        if side == "W":
            w, l, b = (x0 - 1, yf + 2, k), (x0 - 2, yf + 2, k), (x0 - 3, yf + 2, k)
            cotes = [(x0 - 2, yf + 2, k - 1), (x0 - 2, yf + 2, k + 1)]
        elif side == "E":
            w, l, b = (x1 + 1, yf + 2, k), (x1 + 2, yf + 2, k), (x1 + 3, yf + 2, k)
            cotes = [(x1 + 2, yf + 2, k - 1), (x1 + 2, yf + 2, k + 1)]
        elif side == "N":
            w, l, b = (k, yf + 2, z0 - 1), (k, yf + 2, z0 - 2), (k, yf + 2, z0 - 3)
            cotes = [(k - 1, yf + 2, z0 - 2), (k + 1, yf + 2, z0 - 2)]
        else:
            w, l, b = (k, yf + 2, z1 + 1), (k, yf + 2, z1 + 2), (k, yf + 2, z1 + 3)
            cotes = [(k - 1, yf + 2, z1 + 2), (k + 1, yf + 2, z1 + 2)]
        for p in [b] + cotes + [(l[0], l[1] - 1, l[2]), (l[0], l[1] + 1, l[2])]:
            put(*p, S("blackstone"))
        put(*w, BARREAUX)
        put(*l, LAVE)
    SALLES.append((num, (cx, yf + 1, cz), acces))


# ====================================================================== verifications
def solide(p):
    n = nom(p)
    if n is None:
        return p[1] <= SOL                 # hors gabarit : terrain d'herbe jusqu'au sol, air au-dessus
    return n not in NON_SOLIDES


def verifier(phase):
    err = []
    for p, s in blocs.items():
        x, y, z = p
        n = s[0].split(":")[1]
        if n in INTERDITS or n.endswith("shulker_box"):
            err.append(f"bloc interdit {n} en {p}")
        if n != "air" and dist(x, z) > R + 1e-9:
            err.append(f"depasse l'enceinte : {n} en {p} (d={dist(x, z):.2f})")
        if s == LAVE:
            for q in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1), (x, y - 1, z)):
                if nom(q) != "lava" and not solide(q) and nom(q) not in CONTIENT_FLUIDE:
                    err.append(f"lave non contenue {p} -> {q} ({nom(q)})")
        elif s == LAVE_CHUTE:
            if nom((x, y - 1, z)) != "lava":
                err.append(f"chute de lave sans arrivee en {p}")
        elif s == FEU and nom((x, y - 1, z)) not in ("netherrack", "magma_block"):
            err.append(f"feu sans netherrack en {p} ({nom((x, y - 1, z))})")
        elif s == FEU_AME and nom((x, y - 1, z)) not in ("soul_soil", "soul_sand"):
            err.append(f"feu des ames mal pose en {p}")
        elif n in ("lantern", "soul_lantern") and dict(s[1]).get("hanging") == "true":
            if nom((x, y + 1, z)) in (None, "air"):
                err.append(f"lanterne pendante sans accroche en {p}")
        elif n in ("weeping_vines", "weeping_vines_plant"):
            if nom((x, y + 1, z)) != "weeping_vines_plant" and not solide((x, y + 1, z)):
                err.append(f"liane sans support en {p}")
    for e, sp in ECHELLES:
        if nom(e) == "ladder" and not solide(sp):
            err.append(f"echelle {e} sans support ({nom(sp)})")
    for num, (cx, cy, cz), _ in SALLES:
        if nom((cx, cy, cz)) != "air" or nom((cx, cy + 1, cz)) != "air" or not solide((cx, cy - 1, cz)):
            err.append(f"salle {num} : emplacement du coffre non degage en {(cx, cy, cz)}")
        if not any(nom((cx + a, cy, cz + b)) == "air" for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            err.append(f"salle {num} : coffre inaccessible")
    PHASES.append((phase, len(blocs), len(err)))
    print(f"[phase] {phase:<28} blocs={len(blocs):>7}  erreurs={len(err)}")
    if err:
        for e in err[:25]:
            print("   !", e)
        raise SystemExit(1)


# ====================================================================== PHASE 1 : aplanissement + sol
def texture_sol(x, z):
    u = h01(x, SOL, z, 1)
    n = bruit(x, z, 20, 3)
    if n < 0.32:
        return choix([(45, S("blackstone")), (30, S("basalt", axis="y")), (15, S("smooth_basalt")),
                      (10, S("polished_blackstone"))], u)
    if n < 0.68:
        if bruit(x, z, 8, 9) > 0.66:
            return S("crimson_nylium")
        return choix([(75, S("netherrack")), (12, S("nether_wart_block")), (13, S("red_nether_bricks"))], u)
    if bruit(x, z, 6, 13) > 0.72:
        return S("warped_nylium")
    return choix([(55, S("soul_soil")), (40, S("soul_sand")), (5, S("bone_block", axis="y"))], u)


for x in range(SX):
    for z in range(SZ):
        d = dist(x, z)
        if d > R:
            continue
        along, perp = axe(x, z)
        if d > R_MUR:
            s = choix(T_MUR_BAS, h01(x, SOL, z, 2))
        elif dans_avenue(x, z) and d > R_CENTRE:
            s = choix(T_AVENUE, h01(x, SOL, z, 2))
            occ[(x, z)] = "avenue"
        elif d > R_PERIM:
            s = choix(T_PERIM, h01(x, SOL, z, 2))
            occ[(x, z)] = "perim"
        else:
            s = texture_sol(x, z)
        put(x, SOL, z, s)
verifier("1. aplanissement + sol")


# ====================================================================== PHASE 2 : enceinte + 4 entrees
def gate_local(x, z, g):
    """coordonnees (along, perp signe) dans le repere de la porte g"""
    a, b = dxz(x, z)
    if g == 0:
        return a, b
    if g == 180:
        return -a, -b
    if g == 90:
        return b, -a
    return -b, a


H_MUR = SOL + V(16, 11)
ARCHE = V({0.5: 9, 1.5: 8, 2.5: 7}, {0.5: 7, 1.5: 6, 2.5: 5})
for x in range(SX):
    for z in range(SZ):
        d = dist(x, z)
        if not (R_MUR < d <= R):
            continue
        occ[(x, z)] = "mur"
        a = ang(x, z)
        arc = math.radians(a) * V(124, 61)
        along, perp = axe(x, z)
        porte = perp <= 3
        for y in range(SOL + 1, H_MUR + 1):
            if porte:
                haut = SOL + ARCHE[perp]
                if y <= haut:
                    put(x, y, z, AIR)
                    continue
            u = h01(x, y, z, 3)
            if y <= SOL + 3:
                s = choix(T_MUR_BAS, u)
            elif y in V((SOL + 8, SOL + 9), (SOL + 6, SOL + 7)):
                s = DORE if u < 0.15 else S("red_nether_bricks")
            elif y == H_MUR:
                s = BRIQ
            elif y >= H_MUR - 2:
                s = choix(T_MUR_BAS, u)
            else:
                s = choix(T_MUR_MIL, u) if bruit(arc, y, 5, 4) < 0.6 else choix(T_MUR_BAS, u)
            if d < V(122.5, 60.5) and y == SOL + V(12, 9) and int(arc) % 9 == 0:
                s = S("shroomlight")
            put(x, y, z, s)
        # creneaux exterieurs (quelques-uns brises) + parapet interieur
        if d > V(124.6, 61.6) and int(arc) % 3 < 2 and h01(x, 0, z, 8) > 0.08:
            put(x, H_MUR + 1, z, BRIQ)
            if int(arc) % 21 == 0:
                put(x, H_MUR + 2, z, LANT_SOL)
        elif d < V(122.6, 60.6):
            put(x, H_MUR + 1, z, MURET)

# contreforts interieurs (tous les 8 degres, sauf pres des portes)
for x in range(SX):
    for z in range(SZ):
        d = dist(x, z)
        if not (V(117.5, 56.5) < d <= R_MUR):
            continue
        a = ang(x, z)
        if min(adiff(a, g) for g in GATES) < V(13, 16):
            continue
        c = round(a / V(8, 12)) * V(8, 12)
        larg = abs(math.radians(adiff(a, c)) * d)
        if larg > 1.5:
            continue
        top = SOL + V(14, 9) - int((R_MUR - d) * 2.5)
        occ[(x, z)] = "contrefort"
        for y in range(SOL + 1, top + 1):
            put(x, y, z, choix(T_ROCHE, h01(x, y, z, 9)) if y < top else BRIQ)

# portes monumentales : 2 tours de garde par porte
# GT : cotes des tours de garde dans le repere de la porte (al = distance au centre, sp = ecart a l'axe)
GT = V(dict(al0=111, sp=(4, 11), in_al=(114, 124), in_sp=(5, 10), in_d=125, haut=24, bord=(112, 5, 10, 125),
            mil=(5.5, 9.5), braz=((111.5, 4.5), (111.5, 10.5), (120.5, 4.5), (120.5, 10.5)),
            porte=(116.5, 117.5), ronde_al=(122.5, 123.5), ronde_sp=(4.5, 10.5), ech=(115.5, 9.5, 10.5),
            lave=(111.5, 112.5, 113.5, 7.5, (6.5, 8.5), 3, 9),
            s7_salle=((114.5, 115.5, 116.5, 117.5, 118.5), (5.5, 6.5, 7.5, 8.5)),
            s7_porte=(120.5, 121.5), s7_ech=(120.5, 9.5, 10.5),
            s7_lianes=((5.5, 6.5), (111.5, 112.5, 113.5)), s7_coffre=(118.5, 8.5), s7_lant=(114.5, 8.5),
            s7_chaine=(116.5, 7.5), braz_sol=(106.5, 5.5), chaine_al=123.5, acces=(105, 111, 4, 8)),
          dict(al0=52, sp=(4, 9), in_al=(55, 62), in_sp=(5, 8), in_d=62.5, haut=16, bord=(53, 5, 8, 62.5),
            mil=(5.5, 7.5), braz=((52.5, 4.5), (52.5, 8.5), (59.5, 4.5), (59.5, 8.5)),
            porte=(56.5, 57.5), ronde_al=(60.5, 61.5), ronde_sp=(4.5, 8.5), ech=(56.5, 7.5, 8.5),
            lave=(52.5, 53.5, 54.5, 6.5, (5.5, 7.5), 3, 7),
            s7_salle=((55.5, 56.5, 57.5, 58.5), (5.5, 6.5, 7.5)),
            s7_porte=(), s7_ech=(60.5, 7.5, 8.5),
            s7_lianes=((5.5,), (52.5, 53.5, 54.5)), s7_coffre=(58.5, 7.5), s7_lant=(55.5, 7.5),
            s7_chaine=(57.5, 6.5), braz_sol=(48.5, 4.5), chaine_al=61.5, acces=(49, 52, 4, 6)))
TOUR_GARDE_HAUT = SOL + GT["haut"]
SALLE7_TOUR = (0, -1)                     # porte Est, tour cote nord : salle cachee (rideau de lianes)
for g in GATES:
    for s in (-1, 1):
        cells = []
        for x in range(SX):
            for z in range(SZ):
                al, pp = gate_local(x, z, g)
                if GT["al0"] < al and GT["sp"][0] < s * pp < GT["sp"][1] and dist(x, z) <= R:
                    cells.append((x, z, al, s * pp))
        occuper([(x, z) for x, z, _, _ in cells], "tour_garde")
        speciale = (g, s) == SALLE7_TOUR
        for x, z, al, sp in cells:
            d = dist(x, z)
            interieur = (GT["in_al"][0] < al < GT["in_al"][1] and GT["in_sp"][0] < sp < GT["in_sp"][1]
                         and d < GT["in_d"])
            for y in range(SOL + 1, TOUR_GARDE_HAUT + 1):
                if interieur and y < TOUR_GARDE_HAUT and y != H_MUR:
                    put(x, y, z, AIR)
                    continue
                u = h01(x, y, z, 10 + g)
                if y == TOUR_GARDE_HAUT or y == H_MUR:
                    bloc = BRIQ
                elif y % 6 == 0:
                    bloc = S("red_nether_bricks")
                else:
                    bloc = choix(T_MUR_MIL if sp in GT["mil"] else T_MUR_BAS, u)
                put(x, y, z, bloc)
            # creneaux + braseros aux angles
            b_al, b_s0, b_s1, b_d = GT["bord"]
            bord = al < b_al or sp < b_s0 or sp > b_s1 or d > b_d
            if bord and (int(al) + int(sp)) % 2 == 0:
                put(x, TOUR_GARDE_HAUT + 1, z, BRIQ)
        # repere local -> cellule
        def cel(al, sp):
            for x, z, a2, s2 in cells:
                if abs(a2 - al) < 0.01 and abs(s2 - sp) < 0.01:
                    return x, z
            raise KeyError((g, s, al, sp))
        # braseros sur le toit (4 coins)
        for al, sp in GT["braz"]:
            x, z = cel(al, sp)
            put(x, TOUR_GARDE_HAUT + 1, z, S("netherrack"))
            put(x, TOUR_GARDE_HAUT + 2, z, FEU)
        # porte vers le passage de l'entree
        for al in GT["porte"]:
            x, z = cel(al, 4.5)
            put(x, SOL + 1, z, AIR)
            put(x, SOL + 2, z, AIR)
        # acces au chemin de ronde (des deux cotes) au niveau du mur
        for al in GT["ronde_al"]:
            for sp in GT["ronde_sp"]:
                x, z = cel(al, sp)
                put(x, H_MUR + 1, z, AIR)
                put(x, H_MUR + 2, z, AIR)
        # echelle interieure jusqu'au toit, trou dans le plancher du mur
        e_al, e_sp, e_sup = GT["ech"]
        lx, lz = cel(e_al, e_sp)
        sx, sz = cel(e_al, e_sup)
        echelle(lx, lz, SOL + 1, TOUR_GARDE_HAUT, (sx - lx, sz - lz), trappes=(TOUR_GARDE_HAUT,))
        # lave encadree : fenetre de barreaux sur la face cote arene
        l_face, l_lave, l_dos, l_sp, l_cotes, l_y0, l_y1 = GT["lave"]
        for y in range(SOL + l_y0, SOL + l_y1):
            fx, fz = cel(l_face, l_sp)
            lx2, lz2 = cel(l_lave, l_sp)
            put(fx, y, fz, BARREAUX)
            put(lx2, y, lz2, LAVE)
            for sp2 in l_cotes:
                put(*(lambda c: (c[0], y, c[1]))(cel(l_lave, sp2)), BRIQ)
            put(*(lambda c: (c[0], y, c[1]))(cel(l_dos, l_sp)), BRIQ)
        put(*(lambda c: (c[0], SOL + l_y0 - 1, c[1]))(cel(l_lave, l_sp)), BRIQ)
        put(*(lambda c: (c[0], SOL + l_y1, c[1]))(cel(l_lave, l_sp)), BRIQ)
        # salle cachee n.7 dans la tour speciale
        if speciale:
            xs = [cel(al, sp) for al in GT["s7_salle"][0] for sp in GT["s7_salle"][1]]
            # cloison : la salle occupe le bas (y SOL+1..SOL+4) cote arene ; l'echelle publique reste a part
            for (x, z) in xs:
                for y in range(SOL + 1, SOL + 5):
                    put(x, y, z, AIR)
                put(x, SOL + 5, z, BRIQ)
            if DEMI:
                cloison = [cel(59.5, sp) for sp in (5.5, 6.5, 7.5)]
            else:
                cloison = [cel(al, 9.5) if al != 115.5 else cel(119.5, 9.5)
                           for al in (114.5, 115.5, 116.5, 117.5, 118.5, 119.5)]
                cloison += [cel(119.5, sp) for sp in (5.5, 6.5, 7.5, 8.5, 9.5)]
            for c in cloison:
                for y in range(SOL + 1, SOL + 6):
                    put(c[0], y, c[1], BRIQ)
            # porte publique deplacee plus loin dans la tour (demi : acces seulement par le chemin de ronde)
            for al in GT["porte"]:
                x, z = cel(al, 4.5)
                put(x, SOL + 1, z, BRIQ)
                put(x, SOL + 2, z, BRIQ)
            for al in GT["s7_porte"]:
                x, z = cel(al, 4.5)
                put(x, SOL + 1, z, AIR)
                put(x, SOL + 2, z, AIR)
            # echelle publique deplacee dans la partie haute
            n_al, n_sp, n_sup = GT["s7_ech"]
            lx, lz = cel(n_al, n_sp)
            sx, sz = cel(n_al, n_sup)
            o = cel(e_al, e_sp)
            for y in range(SOL + 1, TOUR_GARDE_HAUT + 1):
                if (o[0], y, o[1]) in blocs and nom((o[0], y, o[1])) == "ladder":
                    put(o[0], y, o[1], AIR if y > SOL + 5 else BRIQ)
            ECHELLES[:] = [e for e in ECHELLES if not (e[0][0] == o[0] and e[0][2] == o[1])]
            echelle(lx, lz, SOL + 1, TOUR_GARDE_HAUT, (sx - lx, sz - lz), trappes=(TOUR_GARDE_HAUT,))
            # rideau de lianes sur la face cote arene
            for sp in GT["s7_lianes"][0]:
                for al in GT["s7_lianes"][1]:
                    x, z = cel(al, sp)
                    put(x, SOL + 3, z, BRIQ)
                    if al == GT["s7_lianes"][1][0]:
                        put(x, SOL + 2, z, S("weeping_vines_plant"))
                        put(x, SOL + 1, z, S("weeping_vines", age=12))
                    else:
                        put(x, SOL + 1, z, AIR)
                        put(x, SOL + 2, z, AIR)
            cx, cz = cel(*GT["s7_coffre"])
            for (x, z) in xs:
                put(x, SOL, z, CISELE if (x + z) % 2 else S("polished_blackstone"))
            put(cx, SOL, cz, DORE)
            px, pz = cel(*GT["s7_lant"])
            put(px, SOL + 1, pz, LANT_SOL)
            mx, mz = cel(*GT["s7_chaine"])
            put(mx, SOL + 4, mz, CHAINE)
            put(mx, SOL + 3, mz, LANT_AME_PEND)
            SALLES.append((7, (cx, SOL + 1, cz),
                           "Porte Est, tour de garde cote nord : rideau de lianes rouges sur sa face tournee vers l'arene"))

    # braseros au sol de part et d'autre de l'avenue, cote interieur de la porte
    for s in (-1, 1):
        for al in (GT["braz_sol"][0],):
            for x in range(SX):
                for z in range(SZ):
                    a2, p2 = gate_local(x, z, g)
                    if abs(a2 - al) < 0.01 and abs(s * p2 - GT["braz_sol"][1]) < 0.01:
                        put(x, SOL + 1, z, BRIQ)
                        put(x, SOL + 2, z, CISELE)
                        put(x, SOL + 3, z, S("netherrack"))
                        put(x, SOL + 4, z, FEU)
                        occ[(x, z)] = "brasero"
    # chaines + lanternes sous l'arche
    for x in range(SX):
        for z in range(SZ):
            a2, p2 = gate_local(x, z, g)
            if abs(a2 - GT["chaine_al"]) < 0.01 and abs(p2) == 0.5:
                put(x, SOL + ARCHE[0.5], z, CHAINE)
                put(x, SOL + ARCHE[0.5] - 1, z, LANT_PEND)
verifier("2. enceinte + 4 entrees")


# ====================================================================== PHASE 3 : structures principales
# --- zone centrale : autel + fosse de lave + 4 ponts
for x in range(SX):
    for z in range(SZ):
        d = dist(x, z)
        if d > V(22.5, 12.5):
            continue
        along, perp = axe(x, z)
        occ[(x, z)] = "centre"
        if d <= V(9.5, 5.5):
            top = SOL + 2 if d <= V(7.5, 4.5) else SOL + 1
            for y in range(SOL - 7, top + 1):
                s = S("blackstone") if y < top else (CISELE if (x + z) % 2 == 0 else S("polished_blackstone"))
                if y == top and V(6.5, 3.5) < d <= V(7.5, 4.5):
                    s = DORE
                put(x, y, z, s)
        elif d <= V(21, 11):
            pont = perp <= 3
            put(x, SOL - 3, z, S("magma_block") if h01(x, 0, z, 11) < 0.3 else S("blackstone"))
            put(x, SOL - 2, z, LAVE)
            put(x, SOL - 1, z, AIR)
            if pont:
                put(x, SOL, z, BRIQ if perp <= 2 else S("polished_blackstone"))
                if perp > 2:
                    put(x, SOL + 1, z, MURET)
                    if int(along) % 4 == 0:
                        put(x, SOL + 2, z, CHAINE)
                        put(x, SOL + 3, z, LANT_AME_PEND if False else LANT_SOL)
                if V(14.5, 7.5) <= along <= V(15.5, 8.5) and perp <= 2:
                    for y in (SOL - 2, SOL - 1):
                        put(x, y, z, S("basalt", axis="y"))
            else:
                put(x, SOL, z, AIR)
        else:
            for y in range(SOL - 3, SOL + 1):
                put(x, y, z, S("blackstone") if y < SOL else BRIQ)
            if perp > 3 and (int(ang(x, z) * 2) % 3 == 0):
                put(x, SOL + 1, z, MURET)
# obelisque (2x2 au centre exact)
C = int(CEN)
H_OBEL = V(15, 10)
for x in (C - 1, C):
    for z in (C - 1, C):
        for y in range(SOL + 3, SOL + H_OBEL):
            put(x, y, z, DORE if y in (SOL + 3, SOL + 9) else (S("crying_obsidian") if h01(x, y, z, 12) < 0.3
                                                                 else S("obsidian")))
        put(x, SOL + H_OBEL, z, S("shroomlight"))
for x, z in V(((122, 122), (129, 122), (122, 129), (129, 129)),
              ((C - 3, C - 3), (C + 2, C - 3), (C - 3, C + 2), (C + 2, C + 2))):
    put(x, SOL + 2, z, S("soul_soil"))
    put(x, SOL + 3, z, FEU_AME)
# salle cachee n.5 sous l'autel (trappe a cote de l'obelisque)
salle(5, C - 3, C + 2, C - 3, C + 2, SOL - 7, 4, coffre=(C - 3, C - 3),
      acces="Centre : trappe sur l'autel, juste au nord-est de l'obelisque, echelle vers le bas",
      garder={(C + 2, C - 2)}, niche=("S", C - 1))
echelle(C + 2, C - 2, SOL - 6, SOL + 2, (1, 0), trappes=(SOL + 2,))

# --- 4 tours
ETAGES = (TABLIER, SOL + V(26, 17))         # planchers interieurs (le 1er au niveau de l'aqueduc)
AQ = V((57.5, 62.5), (27.5, 32.5))          # bande de l'aqueduc
SALLE6_SOL = SOL + V(32, 20)                # plancher de la salle secrete au sommet de la tour d'obsidienne


def tour(q):
    cx, cz = CENTRES_TOUR[q]
    a_tour = TOURS[q]
    style = {"NE": "basalte", "SE": "nether", "SO": "ruine", "NO": "obsidienne"}[q]
    if style == "basalte":
        r, haut = V(6.5, 4.5), SOL + V(34, 22)
        forme = lambda dx, dz: math.hypot(dx, dz) <= r
    elif style == "nether":
        r, haut = V(6.5, 4.5), SOL + V(30, 20)
        forme = lambda dx, dz: max(abs(dx), abs(dz)) <= V(6, 4)
    elif style == "ruine":
        r, haut = V(7, 5), SOL + V(26, 16)
        forme = lambda dx, dz: math.hypot(dx, dz) <= r
    else:
        r, haut = V(6, 4.5), SOL + V(40, 27)
        forme = lambda dx, dz: max(abs(dx), abs(dz)) <= V(5.5, 4.5) and abs(dx) + abs(dz) <= V(8, 6.5)

    def interieur(dx, dz):
        if style == "nether":
            return max(abs(dx), abs(dz)) <= V(4.5, 2.5)
        if style == "obsidienne":
            return max(abs(dx), abs(dz)) <= V(4, 3) and abs(dx) + abs(dz) <= V(6, 4.5)
        return math.hypot(dx, dz) <= r - 1.6

    cells = [(x, z) for x in range(math.floor(cx - 9), math.ceil(cx + 9)) for z in range(math.floor(cz - 9), math.ceil(cz + 9))
             if forme(x + 0.5 - cx, z + 0.5 - cz)]
    occuper(cells, "tour")
    vers_centre = math.degrees(math.atan2(CEN - cz, CEN - cx)) % 360
    for x, z in cells:
        dx, dz = x + 0.5 - cx, z + 0.5 - cz
        a = math.degrees(math.atan2(dz, dx)) % 360
        inter = interieur(dx, dz)
        if style == "ruine":
            h_loc = SOL + V(16, 12) + int(V(10, 4) * bruit(a, 0, 40, 21))
            if adiff(a, vers_centre + 70) < 28:
                h_loc = SOL + V(6, 5) + int(3 * h01(x, 0, z, 22))          # pan effondre
        else:
            h_loc = haut
        for y in range(SOL + 1, h_loc + 1):
            if inter and y < h_loc and y not in ETAGES:
                put(x, y, z, AIR)
                continue
            if inter and y in ETAGES and style == "ruine" and y == ETAGES[1]:
                continue
            u = h01(x, y, z, 30)
            if style == "basalte":
                s = S("polished_basalt", axis="y") if y % 6 == 0 else (S("magma_block") if y % 9 == 4 and u < 0.6
                                                                     else S("basalt", axis="y"))
            elif style == "nether":
                s = S("red_nether_bricks") if y % 5 == 0 else choix([(70, S("nether_bricks")),
                                                                      (30, S("cracked_nether_bricks"))], u)
            elif style == "ruine":
                s = choix([(40, S("blackstone")), (30, BRIQ_F), (20, BRIQ), (10, DORE)], u)
            else:
                s = DORE if y in V((SOL + 10, SOL + 20, SOL + 30), (SOL + 7, SOL + 14, SOL + 21)) else (
                    S("crying_obsidian") if bruit(a * 0.3, y, 3, 31) > 0.7 else S("obsidian"))
            if not inter and y in V((SOL + 5, SOL + 20, SOL + 28), (SOL + 4, SOL + 13, SOL + 18)) and int(a) % 90 < 10:
                s = BARREAUX
            put(x, y, z, s)
        # toit / creneaux
        if style != "ruine" and not inter and (x + z) % 2 == 0:
            put(x, haut + 1, z, {"basalte": S("polished_basalt", axis="y"), "nether": S("red_nether_bricks"),
                                 "obsidienne": S("crying_obsidian")}[style])
    # porte au sol cote centre (2 large, 3 haut)
    for x, z in cells:
        dx, dz = x + 0.5 - cx, z + 0.5 - cz
        a = math.degrees(math.atan2(dz, dx)) % 360
        if not interieur(dx, dz) and adiff(a, vers_centre) < 14:
            for y in range(SOL + 1, SOL + 4):
                put(x, y, z, AIR)
        # portes vers l'aqueduc (au niveau du tablier)
        dd = dist(x, z)
        if not interieur(dx, dz) and AQ[0] <= dd <= AQ[1] and (adiff(a, vers_centre + 90) < 30 or adiff(a, vers_centre - 90) < 30):
            put(x, TABLIER + 1, z, AIR)
            put(x, TABLIER + 2, z, AIR)
            put(x, TABLIER, z, BRIQ)
    # echelle interieure : contre le mur oppose au centre
    opp = (vers_centre + 180) % 360
    best = None
    for x, z in cells:
        dx, dz = x + 0.5 - cx, z + 0.5 - cz
        if not interieur(dx, dz):
            continue
        for sup in FACE_ECH:
            nx, nz = x + sup[0], z + sup[1]
            if (nx, nz) in cells and not interieur(nx + 0.5 - cx, nz + 0.5 - cz):
                a = math.degrees(math.atan2(dz, dx)) % 360
                sc = adiff(a, opp)
                if best is None or sc < best[0]:
                    best = (sc, x, z, sup)
    _, lx, lz, sup = best
    top_ech = haut if style != "ruine" else TABLIER + 1
    if style == "obsidienne":
        top_ech = SALLE6_SOL - 1
    echelle(lx, lz, SOL + 1, top_ech, sup, trappes=() if style in ("ruine", "obsidienne") else (haut,))
    return cells, (lx, lz, sup), interieur, haut, (cx, cz)


INFO_TOURS = {q: tour(q) for q in TOURS}

# details de style
cx, cz = CENTRES_TOUR["NE"]
for a in (0, 90, 180, 270):                   # couronne decalee de 45 deg : l'echelle sort a 315 deg
    rc_ = V(4.6, 2.6)
    x, z = math.floor(cx + rc_ * math.cos(math.radians(a))), math.floor(cz + rc_ * math.sin(math.radians(a)))
    h_ne = INFO_TOURS["NE"][3]
    for y in range(h_ne + 1, h_ne + 4):
        put(x, y, z, S("basalt", axis="y"))
    put(x, h_ne + 4, z, S("soul_soil"))
    put(x, h_ne + 5, z, FEU_AME)
cx, cz = CENTRES_TOUR["SE"]
h_se = INFO_TOURS["SE"][3]
fl = V(2, 1.5)
for k, y in enumerate(range(h_se + 1, h_se + 5)):
    for x in range(math.floor(cx - fl + k * 0.5), math.ceil(cx + fl - k * 0.5)):
        for z in range(math.floor(cz - fl + k * 0.5), math.ceil(cz + fl - k * 0.5)):
            put(x, y, z, S("red_nether_bricks") if k < 3 else S("shroomlight"))
for dx, dz in V(((-7, -7), (6, -7), (-7, 6), (6, 6)), ((-5, -5), (4, -5), (-5, 4), (4, 4))):   # contreforts d'angle
    for y in range(SOL + 1, SOL + V(23, 15)):
        for ex in (0, 1):
            put(math.floor(cx) + dx + ex * (1 if dx < 0 else 0), y, math.floor(cz) + dz + ex * (1 if dz < 0 else 0),
                S("red_nether_bricks"))
# tour en ruine : eboulis autour
cx, cz = CENTRES_TOUR["SO"]
for _ in range(V(70, 25)):
    a, r = rng.uniform(0, 2 * math.pi), rng.uniform(V(7.5, 5.5), V(14, 9))
    x, z = math.floor(cx + r * math.cos(a)), math.floor(cz + r * math.sin(a))
    if occ.get((x, z)) in ("tour", "avenue", "centre") or dist(x, z) > R_PERIM - 1:
        continue
    h = rng.randint(1, 2)
    for y in range(SOL + 1, SOL + 1 + h):
        put(x, y, z, choix([(40, S("blackstone")), (35, BRIQ_F), (25, BRIQ)], rng.random()))
    occ[(x, z)] = "eboulis"
# tour d'obsidienne : salle cachee n.6 au sommet
cells, (lx, lz, sup), inter_f, haut, (cx, cz) = INFO_TOURS["NO"]
inter_cells = [(x, z) for x, z in cells if inter_f(x + 0.5 - cx, z + 0.5 - cz)]
for x, z in inter_cells:
    put(x, SALLE6_SOL, z, S("obsidian"))                         # plancher de la salle
    for y in range(SALLE6_SOL + 1, haut):
        put(x, y, z, AIR)
    put(x, haut, z, S("obsidian"))                               # toit
# conduit de l'echelle publique : du plancher de la salle au toit, isole de la salle
for y in range(SALLE6_SOL, haut + 1):
    put(lx, y, lz, S("ladder", facing=FACE_ECH[sup]) if y < haut else TRAPPE)
    ECHELLES.append(((lx, y, lz), (lx + sup[0], y, lz + sup[1])))
    for v in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        q = (lx + v[0], y, lz + v[1])
        if v != sup and q[0:3:2] in inter_cells:
            put(*q, S("obsidian"))
# trappe cachee dans le toit, de l'autre cote, derriere un brasero
far = max(inter_cells, key=lambda c: math.hypot(c[0] - lx, c[1] - lz))
hx, hz = far
hsup = None
for v in FACE_ECH:
    if (hx + v[0], hz + v[1]) in cells and (hx + v[0], hz + v[1]) not in inter_cells:
        hsup = v
        break
if hsup is None:
    for v in FACE_ECH:
        q = (hx + v[0], hz + v[1])
        if q in inter_cells:
            hsup = v
            for y in range(SALLE6_SOL + 1, haut):
                put(q[0], y, q[1], S("obsidian"))
            break
echelle(hx, hz, SALLE6_SOL + 1, haut, hsup, trappes=(haut,))
bx, bz = hx - hsup[0], hz - hsup[1]
if (bx, bz) in inter_cells:
    put(bx, haut + 1, bz, S("netherrack"))
    put(bx, haut + 2, bz, FEU)
coffre6 = max((c for c in inter_cells if c != (lx, lz) and c != (hx, hz)
               and all((c[0] + v[0], c[1] + v[1]) != (lx, lz) for v in FACE_ECH)),
              key=lambda c: math.hypot(c[0] - hx, c[1] - hz) + math.hypot(c[0] - lx, c[1] - lz))
put(coffre6[0], SALLE6_SOL, coffre6[1], DORE)
mx, mz = math.floor(cx), math.floor(cz)
for (x, z) in inter_cells:
    if math.hypot(x - mx, z - mz) < 1 and (x, z) not in ((hx, hz), coffre6, (lx, lz)):
        put(x, haut - 1, z, CHAINE)
        put(x, haut - 2, z, LANT_AME_PEND)
for (x, z) in inter_cells:
    if (x, z) not in ((hx, hz), coffre6, (lx, lz)) and h01(x, 0, z, 61) < 0.15:
        put(x, SALLE6_SOL + 1, z, LANT_SOL)
SALLES.append((6, (coffre6[0], SALLE6_SOL + 1, coffre6[1]),
               "Tour d'obsidienne (NO) : monter sur le toit, trappe cachee a cote du brasero, echelle vers la salle"))

# --- aqueduc : passerelle en hauteur reliant les 4 tours
PAS_PILE = V(14, 9)
TROUS = [(TOURS[q] + V(22, 28)) % 360 for q in TOURS]
PILE_SALLE = 255
INFRA = TABLIER - 2                                  # dessous du tablier (bas de l'arche)
for x in range(SX):
    for z in range(SZ):
        d = dist(x, z)
        if not (AQ[0] <= d <= AQ[1]) or occ.get((x, z)) == "tour":
            continue
        a = ang(x, z)
        arc = math.radians(a) * R_TOUR
        if any(adiff(a, t) * math.pi / 180 * R_TOUR <= 1.0 for t in TROUS):
            continue                                                # saut de 2 blocs
        along, perp = axe(x, z)
        put(x, TABLIER, z, BRIQ if h01(x, 0, z, 40) > 0.2 else BRIQ_F)
        if d < V(58.3, 28.3) or d > V(61.7, 31.7):
            put(x, TABLIER + 1, z, MURET)
        s_pile = abs(arc - round(arc / PAS_PILE) * PAS_PILE)
        bas = INFRA
        if perp > V(5.5, 4.5) and occ.get((x, z)) not in ("avenue",):
            if s_pile <= 1.5:
                bas = SOL + 1
            elif s_pile <= V(3, 2.5):
                bas = TABLIER - 5
            elif s_pile <= V(4.5, 3.5):
                bas = TABLIER - 3
        for y in range(bas, TABLIER):
            put(x, y, z, choix(T_ROCHE, h01(x, y, z, 41)) if bas == SOL + 1 else S("blackstone"))
        if bas == SOL + 1:
            occ[(x, z)] = "pile"
        if bas == INFRA and s_pile > V(6, 4) and int(arc) % 7 == 0 and perp > V(5.5, 4.5):
            put(x, INFRA - 1, z, CHAINE)
            put(x, INFRA - 2, z, LANT_PEND)
# salle cachee n.9 dans une pile elargie de l'aqueduc (nord)
px, pz = cellule(R_TOUR, PILE_SALLE)
for x in range(px - 4, px + 5):
    for z in range(pz - 3, pz + 4):
        for y in range(SOL + 1, TABLIER):
            put(x, y, z, choix(T_ROCHE, h01(x, y, z, 42)))
        put(x, TABLIER, z, BRIQ)
        occ[(x, z)] = "pile"
salle(9, px - 2, px + 2, pz - 1, pz + 1, SOL, 4, coffre=(px + 2, pz + 1),
      acces="Aqueduc nord : trappe dans le tablier (passerelle en hauteur), echelle dans la pile vers la salle",
      garder={(px - 2, pz - 1)})
echelle(px - 2, pz - 1, SOL + 1, TABLIER, (-1, 0), trappes=(TABLIER,))
for y in range(SOL + 6, TABLIER):
    for v in ((0, -1), (1, 0), (0, 1)):
        put(px - 2 + v[0], y, pz - 1 + v[1], choix(T_ROCHE, h01(px, y, pz, 43)))
verifier("3. tours + centre + aqueduc")


# ====================================================================== PHASE 4 : labyrinthes
U = 4
H_LABY = 5


def masque_laby(x, z, q):
    d = dist(x, z)
    a, b = dxz(x, z)
    return (V(74, 36) <= d <= R_PERIM and min(abs(a), abs(b)) >= V(10, 6) and quadrant(x, z) == q
            and occ.get((x, z)) in (None, "eboulis"))


LABY_INFO = {}
for q in ("NE", "SE", "SO", "NO"):
    valides = set()
    for i in range(SX // U - 1):
        for j in range(SZ // U - 1):
            if all(masque_laby(x, z, q) for x in range(U * i, U * i + U + 1) for z in range(U * j, U * j + U + 1)):
                valides.add((i, j))

    def vois(c):
        i, j = c
        return [(i + 1, j), (i - 1, j), (i, j + 1), (i, j - 1)]

    # salle cachee n.1 (NE) : bloc 2x2 reserve ; cellules de lave scellees
    salle_bloc = set()
    if q == "NE":
        cands = [c for c in sorted(valides) if all((c[0] + a, c[1] + b) in valides for a in (0, 1) for b in (0, 1))
                 and sum(n in valides for k in ((0, 0), (1, 0), (0, 1), (1, 1))
                         for n in vois((c[0] + k[0], c[1] + k[1]))) >= V(10, 9)]
        if not cands:                                      # petit labyrinthe : n'importe quel bloc 2x2
            cands = [c for c in sorted(valides)
                     if all((c[0] + a, c[1] + b) in valides for a in (0, 1) for b in (0, 1))]
        c0 = cands[len(cands) // 2]
        salle_bloc = {(c0[0] + a, c0[1] + b) for a in (0, 1) for b in (0, 1)}
    lave = set()
    pool = [c for c in sorted(valides) if c not in salle_bloc and sum(n in valides for n in vois(c)) == 4
            and not any(n in salle_bloc for n in vois(c))]
    rng.shuffle(pool)
    for c in pool:
        if len(lave) >= V(4, 2):
            break
        if not any(n in lave for n in vois(c)):
            lave.add(c)
    libres = valides - salle_bloc - lave
    # DFS
    depart = min(libres)
    vus, pile, ouverts = {depart}, [depart], set()
    while pile:
        c = pile[-1]
        nv = [n for n in vois(c) if n in libres and n not in vus]
        if not nv:
            pile.pop()
            continue
        n = rng.choice(nv)
        ouverts.add(frozenset((c, n)))
        vus.add(n)
        pile.append(n)
    isoles = libres - vus
    for c in sorted(vus):                                   # quelques boucles
        for n in vois(c):
            if n in vus and frozenset((c, n)) not in ouverts and rng.random() < 0.05:
                ouverts.add(frozenset((c, n)))
    # entrees : cellules du bord vers l'exterieur
    bord = [(c, n) for c in sorted(vus) for n in vois(c) if n not in valides]
    rng.shuffle(bord)
    entrees = []
    for c, n in bord:
        if len(entrees) >= V(4, 3):
            break
        if all(abs(c[0] - e[0][0]) + abs(c[1] - e[0][1]) > V(6, 3) for e in entrees):
            entrees.append((c, n))
    # rendu bloc par bloc
    region = set()
    for (i, j) in valides:
        for x in range(U * i, U * i + U + 1):
            for z in range(U * j, U * j + U + 1):
                region.add((x, z))
    ligne = {(x, z) for (x, z) in region if x % U == 0 or z % U == 0}

    def passage(c, n):
        (i, j), (k, l) = c, n
        if k != i:
            xw = U * max(i, k)
            return [(xw, U * j + t) for t in (1, 2, 3)]
        zw = U * max(j, l)
        return [(U * i + t, zw) for t in (1, 2, 3)]

    ouvert_blocs = set()
    for e in ouverts:
        c, n = tuple(e)
        ouvert_blocs.update(passage(c, n))
    for c, n in entrees:
        ouvert_blocs.update(passage(c, n))
    for (x, z) in region:
        occ[(x, z)] = "laby"
        put(x, SOL, z, choix(T_LABY_SOL, h01(x, SOL, z, 50)))
        if (x, z) in ligne and (x, z) not in ouvert_blocs:
            for y in range(SOL + 1, SOL + H_LABY + 1):
                u = h01(x, y, z, 51)
                s = choix(T_LABY_MUR, u)
                if y == SOL + 4 and u < 0.04:
                    s = S("shroomlight")
                put(x, y, z, s)
            if h01(x, 0, z, 52) < 0.05:
                put(x, SOL + H_LABY + 1, z, MURET)
                put(x, SOL + H_LABY + 2, z, LANT_SOL)
        elif (x, z) not in ligne:
            for y in range(SOL + 1, SOL + H_LABY + 1):
                put(x, y, z, AIR)
    for c in isoles:                                        # cellules isolees -> massif plein
        for x in range(U * c[0] + 1, U * c[0] + U):
            for z in range(U * c[1] + 1, U * c[1] + U):
                for y in range(SOL + 1, SOL + H_LABY + 1):
                    put(x, y, z, choix(T_LABY_MUR, h01(x, y, z, 53)))
    # cellules de lave visibles derriere des barreaux
    for c in lave:
        i, j = c
        for x in range(U * i + 1, U * i + U):
            for z in range(U * j + 1, U * j + U):
                put(x, SOL, z, LAVE)
                put(x, SOL - 1, z, S("magma_block"))
        for n in vois(c):
            if n in vus:
                for (x, z) in passage(c, n):
                    if (x, z) in ouvert_blocs:
                        continue
                    put(x, SOL + 2, z, BARREAUX)
                    put(x, SOL + 3, z, BARREAUX)
    # feux dans quelques impasses
    impasses = [c for c in sorted(vus) if sum(frozenset((c, n)) in ouverts for n in vois(c)) == 1
                and not any(c == e[0] for e in entrees)]
    LABY_INFO[q] = dict(valides=valides, vus=vus, ouverts=ouverts, impasses=impasses, salle_bloc=salle_bloc,
                        lave=lave, entrees=entrees, passage=passage, vois=vois)
    for c in impasses[: len(impasses) // 3]:
        if q == "SO" and c == impasses[0]:
            continue
        x, z = U * c[0] + 1 + rng.randint(0, 2), U * c[1] + 1 + rng.randint(0, 2)
        put(x, SOL, z, S("netherrack"))
        put(x, SOL + 1, z, FEU)

# salle n.1 (NE) : faux mur en chicane depuis une cellule voisine
inf = LABY_INFO["NE"]
blocsalle = inf["salle_bloc"]
i0 = min(c[0] for c in blocsalle)
j0 = min(c[1] for c in blocsalle)
x0, z0 = U * i0 + 1, U * j0 + 1
salle(1, x0, x0 + 6, z0, z0 + 6, SOL, 4, coffre=(x0 + 6, z0 + 6),
      acces="Labyrinthe NE : impasse dont le mur du fond cache un passage en chicane (faux mur)",
      garder={(x0, z0)})
for x in range(x0 - 1, x0 + 8):                               # toit au niveau du haut des murs
    for z in range(z0 - 1, z0 + 8):
        put(x, SOL + 5, z, choix(T_LABY_MUR, h01(x, 5, z, 54)))
voisins_salle = [(c, n) for c in sorted(blocsalle) for n in inf["vois"](c) if n in inf["vus"]]
voisins_salle.sort(key=lambda cn: -int(cn[1] in inf["impasses"]))
c, n = voisins_salle[0]
mur = inf["passage"](c, n)                                    # 3 blocs du mur commun
porte = mur[0]
put(porte[0], SOL + 1, porte[2 - 1] if False else porte[1], AIR)
put(porte[0], SOL + 2, porte[1], AIR)
# chicane : un pan de mur dans le couloir, a 2 blocs devant la porte -> passage lateral d'1 bloc,
# invisible depuis le couloir (on croit voir le mur du fond de l'impasse)
dxn, dzn = n[0] - c[0], n[1] - c[1]
for k in (0, 1):
    bx = porte[0] + 2 * dxn + (0 if dxn else k)
    bz = porte[1] + 2 * dzn + (0 if dzn else k)
    for y in range(SOL + 1, SOL + H_LABY + 1):
        put(bx, y, bz, choix(T_LABY_MUR, h01(bx, y, bz, 55)))
for y in (SOL + 1, SOL + 2):                                  # case juste derriere la porte : degagee
    put(porte[0] - dxn, y, porte[1] - dzn, AIR)

# salle n.2 (SO) : trappe au sol d'une impasse -> salle souterraine
inf = LABY_INFO["SO"]
c = inf["impasses"][0]
xi, zi = U * c[0] + 1, U * c[1] + 1
salle(2, xi, xi + 6, zi, zi + 6, SOL - 6, 4, coffre=(xi + 6, zi + 6),
      acces="Labyrinthe SO : trappe dans le sol d'une impasse, echelle vers une salle souterraine",
      garder={(xi, zi)}, niche=("S", xi + 3))
echelle(xi, zi, SOL - 5, SOL, (-1, 0), trappes=(SOL,))
verifier("4. labyrinthes")


# ====================================================================== PHASE 5 : parcours
def fosse(cells, prof_lave=SOL - 2):
    """fosse de lave : air jusqu'au sol, lave 2 sous le sol, fond en dessous ; bords en briques"""
    cs = set(cells)
    for x, z in cs:
        put(x, prof_lave - 1, z, S("blackstone"))
        put(x, prof_lave, z, LAVE)
        for y in range(prof_lave + 1, SOL + 1):
            put(x, y, z, AIR)
        occ[(x, z)] = "fosse"
    for x, z in cs:
        for v in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
            q = (x + v[0], z + v[1])
            if q not in cs and occ.get(q) != "fosse":
                for y in range(prof_lave - 1, SOL + 1):
                    put(q[0], y, q[1], BRIQ if y == SOL else S("blackstone"))
                occ.setdefault(q, "bord")


def libre_zone(cells):
    return all(occ.get(c) is None for c in cells)


# NE : fosse aux piliers de basalte
R_PARC = V(54, 26.5)                          # limite exterieure des parcours (avant l'aqueduc)
cxn, czn = cellule(V(40, 20), 315)
pit = [(x, z) for x, z in disque(cxn + 0.5, czn + 0.5, V(11, 7)) if
       math.hypot(x + 0.5 - cxn, z + 0.5 - czn) <= V(8, 4.5) + V(3, 2) * bruit(x, z, 5, 70) and libre_zone([(x, z)])
       and dist(x, z) < R_PARC]
fosse(pit)
chemin = V([(cxn - 9 + 3 * k, czn - 1 + (k % 2)) for k in range(7)],
           [(cxn - 6 + 3 * k, czn - 1 + (k % 2)) for k in range(5)])
hauteurs = V([0, 1, 1, 2, 1, 1, 0], [0, 1, 2, 1, 0])
for (x, z), h in zip(chemin, hauteurs):
    if (x, z) not in pit:
        continue
    for y in range(SOL - 3, SOL + h + 1):
        put(x, y, z, S("basalt", axis="y") if y < SOL + h else S("polished_basalt", axis="y"))
for _ in range(V(10, 4)):
    x, z = rng.choice(pit)
    hh = rng.randint(-1, 3)
    for y in range(SOL - 3, SOL + hh + 1):
        put(x, y, z, S("basalt", axis="y"))

# SE : parcours montant au-dessus d'une fosse -> rocher -> salle n.4
cxs, czs = cellule(V(42, 18), V(30, 35))
rx, rz = V((cxs + 4, czs + 6), (cxs + 1, czs - 2))      # coin du rocher
TR = V(9, 7)                                           # cote du rocher
Y4 = SOL + V(6, 4)                                     # plancher de la salle n.4 (= sommet du parcours)
roche = [(x, z) for x in range(rx, rx + TR) for z in range(rz, rz + TR)]
trench = [(x, z) for x in range(V(cxs - 9, rx - 9), rx) for z in range(V(czs - 2, rz - 3), rz + TR)
          if libre_zone([(x, z)]) and dist(x, z) < R_PARC and dist(x, z) > V(26, 13)]
fosse(trench)
for x, z in roche:
    top = SOL + V(11, 9) + int(2 * h01(x, 0, z, 71))
    for y in range(SOL - 3, top + 1):
        put(x, y, z, choix(T_ROCHE, h01(x, y, z, 72)))
    occ[(x, z)] = "rocher"
salle(4, rx + 2, rx + TR - 3, rz + 2, rz + TR - 3, Y4, V(4, 3), coffre=(rx + TR - 3, rz + TR - 3),
      acces="Sud-Est : au bout du parcours de piliers montants au-dessus de la lave, porte en haut du rocher")
for y in (Y4 + 1, Y4 + 2):                    # porte cote ouest, a l'arrivee du parcours
    put(rx + 1, y, rz + 3, AIR)
    put(rx, y, rz + 3, AIR)
put(rx, Y4, rz + 3, S("polished_basalt", axis="y"))
etapes = V([(rx - 2, rz + 3, 5), (rx - 4, rz + 2, 4), (rx - 6, rz + 3, 3), (rx - 8, rz + 4, 2), (rx - 10, rz + 3, 1),
            (rx - 12, rz + 3, 0)],
           [(rx - 2, rz + 3, 3), (rx - 4, rz + 2, 2), (rx - 6, rz + 3, 1), (rx - 8, rz + 3, 0)])
for x, z, h in etapes:
    for y in range(SOL - 3, SOL + h + 1):
        put(x, y, z, S("basalt", axis="y") if y < SOL + h else S("polished_basalt", axis="y"))
    occ[(x, z)] = "pilier"
put(rx - 1, Y4, rz + 3, S("polished_basalt", axis="y"))
for y in range(SOL - 3, Y4):
    put(rx - 1, y, rz + 3, S("basalt", axis="y"))

# NO : rocher a cascade + salle n.3 derriere la chute
cxo, czo = cellule(V(42, 20), 222)
ax0, az0 = V((cxo - 5, czo - 6), (cxo - 4, czo - 4))   # coin du rocher, face avant vers +z
LW, LD = V((11, 7), (9, 6))                            # largeur (x) et profondeur (z) du rocher
zf = az0 + LD                                          # premiere rangee devant la face
HC = V(10, 8)                                          # hauteur de la source au-dessus du sol
for x in range(ax0, ax0 + LW):
    for z in range(az0, az0 + LD):
        top = SOL + HC - 1 + int(2 * h01(x, 0, z, 73))
        for y in range(SOL + 1, top + 1):
            put(x, y, z, choix(T_ROCHE, h01(x, y, z, 74)))
        occ[(x, z)] = "rocher"
salle(3, ax0 + V(3, 2), ax0 + V(7, 6), az0 + 1, az0 + LD - 3, SOL, V(4, 3), coffre=(ax0 + V(3, 2), az0 + 1),
      acces="Nord-Ouest : derriere la cascade de lave du grand rocher (passer par le cote, le long de la paroi)")
xd = ax0 + LW // 2
for z in (zf - 2, zf - 1):                    # porte derriere la chute
    put(xd, SOL + 1, z, AIR)
    put(xd, SOL + 2, z, AIR)
for x in range(ax0 + 1, ax0 + LW - 1):        # corniche en surplomb au-dessus du passage
    for y in (SOL + HC - 2, SOL + HC - 1):
        put(x, y, zf, choix(T_ROCHE, h01(x, y, 7, 75)))
    for y in range(SOL + 1, SOL + HC - 2):
        put(x, y, zf, AIR)
    put(x, SOL, zf, BRIQ)
    occ[(x, zf)] = "rocher"
put(xd, SOL + HC, zf, LAVE)                    # source, encastree
for p in ((xd - 1, SOL + HC, zf), (xd + 1, SOL + HC, zf), (xd, SOL + HC, zf - 1),
          (xd, SOL + HC + 1, zf)):
    put(*p, S("blackstone"))
for y in range(SOL + 1, SOL + HC + 1):
    put(xd, y, zf + 1, LAVE_CHUTE)
for x in range(xd - 1, xd + 2):                # bassin affleurant
    for z in (zf + 1, zf + 2):
        put(x, SOL, z, LAVE)
        put(x, SOL - 1, z, S("blackstone"))
        occ[(x, z)] = "bassin"
for x in range(xd - 2, xd + 3):
    for z in (zf + 1, zf + 2, zf + 3):
        if nom((x, SOL, z)) != "lava":
            put(x, SOL, z, S("magma_block"))
            occ[(x, z)] = "bassin"
put(xd, SOL + 1, zf + 1, LAVE_CHUTE)

# SO : tranchees
pts = [cellule(r, a) for r, a in zip(V((30, 40, 34, 48, 44), (15, 20, 17, 24, 22)), (100, 112, 128, 145, 165))]
tranchee = set()
for (xa, za), (xb, zb) in zip(pts, pts[1:]):
    n = max(abs(xb - xa), abs(zb - za))
    for k in range(n + 1):
        x = round(xa + (xb - xa) * k / n)
        z = round(za + (zb - za) * k / n)
        for a in (-1, 0, 1):
            for b in (-1, 0, 1):
                if libre_zone([(x + a, z + b)]) and dist(x + a, z + b) < R_PARC:
                    tranchee.add((x + a, z + b))
for x, z in tranchee:
    put(x, SOL - 3, z, BRIQ)
    put(x, SOL - 2, z, S("soul_soil"))
    put(x, SOL - 1, z, AIR)
    put(x, SOL, z, AIR)
    occ[(x, z)] = "tranchee"
for x, z in tranchee:
    for v in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        q = (x + v[0], z + v[1])
        if q not in tranchee:
            for y in (SOL - 2, SOL - 1):
                put(q[0], y, q[1], BRIQ_F if h01(q[0], y, q[1], 80) < 0.4 else S("blackstone"))
            put(q[0], SOL, q[1], BRIQ)
            occ.setdefault(q, "bord")
tl = sorted(tranchee)
for k in range(0, len(tl), 23):              # passerelles (remet le sol) + marches
    x, z = tl[k]
    put(x, SOL, z, BRIQ)
    put(x, SOL - 1, z, S("blackstone"))
for k in range(11, len(tl), 29):
    x, z = tl[k]
    put(x, SOL - 1, z, S("blackstone"))      # marche pour sortir
for x, z in list(tranchee)[:: 37]:
    if nom((x, SOL - 1, z)) == "air":
        put(x, SOL - 1, z, FEU_AME)

# plateformes surelevees (relief)
PL = V(3, 2)                                  # demi-longueur des plateformes
for a, r in V(((300, 32), (80, 36), (150, 30), (250, 34), (20, 48), (200, 50)),
              ((300, 17), (80, 19), (150, 16), (250, 18))):
    cx, cz = cellule(r, a)
    cells = [(x, z) for x in range(cx - PL, cx + PL + 1) for z in range(cz - PL + 1, cz + PL)]
    if not libre_zone(cells):
        continue
    h = 2 + (a % 2)
    for x, z in cells:
        for y in range(SOL + 1, SOL + h + 1):
            put(x, y, z, BRIQ if y == SOL + h else S("blackstone"))
        occ[(x, z)] = "plateforme"
    for k in range(h):                        # marches sur un cote
        put(cx - PL - 1 - k, SOL + h - k, cz, BRIQ)
        for y in range(SOL + 1, SOL + h - k):
            put(cx - PL - 1 - k, y, cz, S("blackstone"))
        occ[(cx - PL - 1 - k, cz)] = "plateforme"
    put(cx + PL, SOL + h + 1, cz + PL - 1, MURET)
    put(cx + PL, SOL + h + 2, cz + PL - 1, LANT_SOL)
verifier("5. parcours + relief")


# ====================================================================== PHASE 6 : salle n.8 (escalier cache sous le sol)
SENS = V(1, -1)                               # +1 : l'escalier descend vers l'est ; -1 : vers l'ouest
ex, ez = cellule(V(36, 22), 186)
ex -= 2 * SENS
for k in range(5):                            # escalier descendant
    x = ex + SENS * k
    for y in range(SOL - k - 1, SOL + 1):
        put(x, y, ez, AIR)
    put(x, SOL - 1 - k, ez, S("polished_blackstone_brick_stairs", facing="west" if SENS > 0 else "east",
                              half="bottom"))
    put(x, SOL - 2 - k, ez, BRIQ)
    for dz in (-1, 1):
        for y in range(SOL - 2 - k, SOL + 1):
            put(x, y, ez + dz, BRIQ_F if h01(x, y, dz, 81) < 0.3 else BRIQ)
    occ[(x, ez)] = "escalier"
for k in range(3, 5):                         # plafond sur la partie basse
    put(ex + SENS * k, SOL, ez, BRIQ)
s8x0, s8x1 = sorted((ex + SENS * 5, ex + SENS * 10))
salle(8, s8x0, s8x1, ez - 2, ez + 2, SOL - 6, 4, coffre=(ex + SENS * 10, ez + 2),
      acces="Ouest : escalier cache dans une ruine en L pres de l'avenue Ouest, il descend sous le sol",
      niche=("N", ex + SENS * 8))
for y in range(SOL - 5, SOL):                # ouverture de la salle vers l'escalier (hauteur de tete libre)
    put(ex + SENS * 4, y, ez, AIR)
# ruine en L qui masque l'entree (mur du fond du cote oppose a la descente)
xr0, xr1 = sorted((ex - 2 * SENS, ex + 2 * SENS))
for x in range(xr0, xr1 + 1):
    for y in range(SOL + 1, SOL + 4 - (1 if x == ex + 2 * SENS else 0)):
        put(x, y, ez - 2, choix(T_LABY_MUR, h01(x, y, ez, 82)))
        put(x, y, ez + 2, choix(T_LABY_MUR, h01(x, y, ez + 2, 83)))
for z in range(ez - 2, ez + 3):
    for y in range(SOL + 1, SOL + 4):
        put(ex - 2 * SENS, y, z, choix(T_LABY_MUR, h01(ex, y, z, 84)))
for x in range(xr0, xr1 + 1):
    for z in range(ez - 2, ez + 3):
        occ.setdefault((x, z), "ruine")
put(ex - SENS, SOL, ez, BRIQ)
verifier("6. salles cachees")


# ====================================================================== PHASE 7 : decoration
# zones d'acces a garder degagees (devant les entrees secretes et les portes)
for x in range(SX):
    for z in range(SZ):
        al, sp = gate_local(x, z, SALLE7_TOUR[0])
        a0, a1, s0, s1 = GT["acces"]
        if a0 < al < a1 and s0 < SALLE7_TOUR[1] * sp < s1:
            occ[(x, z)] = "acces"
for q, (cells_t, _, inter_f, _, (tcx, tcz)) in INFO_TOURS.items():
    vc = math.atan2(CEN - tcz, CEN - tcx)
    for k in V(range(7, 12), range(5, 9)):
        for w in (-1.5, -0.5, 0.5, 1.5):
            x = math.floor(tcx + k * math.cos(vc) - w * math.sin(vc))
            z = math.floor(tcz + k * math.sin(vc) + w * math.cos(vc))
            if occ.get((x, z)) is None:
                occ[(x, z)] = "acces"
for x in range(ax0 - 3, ax0 + LW + 3):
    for z in range(zf - 2, zf + 4):
        occ.setdefault((x, z), "acces")


def libre(c):
    return occ.get(c) is None and dist(*c) <= R_PERIM - 1 and dist(*c) > V(23, 13)


def colonne(cx, cz, kind):
    pied = [(cx, cz), (cx + 1, cz), (cx - 1, cz), (cx, cz + 1), (cx, cz - 1)]
    if not all(libre(c) or occ.get(c) == "perim" for c in pied):
        return False
    h = rng.randint(*V((7, 10), (5, 8))) if kind == "debout" else rng.randint(2, 4)
    for x, z in pied:
        put(x, SOL + 1, z, CISELE)
    for y in range(SOL + 2, SOL + h):
        for x, z in pied:
            if kind == "brisee" and (x, z) != (cx, cz) and y > SOL + h - 2 and rng.random() < 0.5:
                continue
            put(x, y, z, S("polished_basalt", axis="y") if (x, z) == (cx, cz) else S("basalt", axis="y"))
    if kind == "debout":
        for x in range(cx - 1, cx + 2):
            for z in range(cz - 1, cz + 2):
                put(x, SOL + h, z, CISELE if (x + z) % 2 else BRIQ)
        put(cx + 1, SOL + h - 1, cz + 1, LANT_PEND)
    occuper(pied, "colonne")
    if kind == "tombee":
        a = rng.choice([(1, 0), (0, 1), (-1, 0), (0, -1)])
        for k in range(2, 8):
            c = (cx + a[0] * k, cz + a[1] * k)
            if not libre(c):
                break
            put(c[0], SOL + 1, c[1], S("crimson_hyphae", axis="x" if a[0] else "z") if k == 7 else
                S("basalt", axis="x" if a[0] else "z"))
            occ[c] = "colonne"
    return True


# colonnes le long des avenues (des deux cotes)
for g in GATES:
    for s in (-1, 1):
        for al in V(range(28, 112, 9), range(15, 55, 8)):
            a = math.radians(g)
            ux, uz = math.cos(a), math.sin(a)
            px, pz = -uz, ux
            cx = math.floor(CEN + ux * (al + 0.5) + px * s * V(7, 5))
            cz = math.floor(CEN + uz * (al + 0.5) + pz * s * V(7, 5))
            colonne(cx, cz, choix([(50, "debout"), (30, "brisee"), (20, "tombee")], rng.random()))
# cercle de colonnes autour de la fosse
for k in range(V(16, 8)):
    a = V(11.25 + 22.5 * k, 22.5 + 45 * k)
    cx, cz = cellule(V(30, 15), a)
    colonne(cx, cz, "debout" if k % 3 else "brisee")


def champignon(cx, cz, rouge):
    h = rng.randint(5, 8)
    rc = rng.choice((2, 2.6, 3))
    pied = list(disque(cx + 0.5, cz + 0.5, rc + 0.5))
    if not all(libre(c) for c in pied):
        return False
    tige = S("crimson_stem" if rouge else "warped_stem", axis="y")
    chap = S("nether_wart_block" if rouge else "warped_wart_block")
    top = SOL + h
    for y in range(SOL + 1, top):
        put(cx, y, cz, tige)
    for x, z in disque(cx + 0.5, cz + 0.5, rc):
        r = math.hypot(x - cx, z - cz)
        put(x, top, z, S("shroomlight") if rng.random() < 0.12 else chap)
        if r <= rc - 1:
            put(x, top + 1, z, chap)
        elif rng.random() < 0.6:
            put(x, top - 1, z, chap)
    occuper(pied, "champignon")
    return True


n_ch = 0
for _ in range(400):
    if n_ch >= V(12, 4):
        break
    c = cellule(rng.uniform(*V((25, 56), (13, 27))), rng.uniform(0, 360))
    if champignon(c[0], c[1], n_ch % 3 != 2):
        n_ch += 1


def ruine(cx, cz):
    a = rng.uniform(0, math.pi)
    L = rng.randint(4, 8)
    pts = []
    for i in range(-L * 2, L * 2 + 1):
        t = i / 4
        c = (math.floor(cx + t * math.cos(a)), math.floor(cz + t * math.sin(a)))
        if c not in pts:
            pts.append(c)
    if not all(libre(c) for c in pts):
        return False
    for x, z in pts:
        if rng.random() < 0.12:
            continue
        h = max(1, 3 + rng.randint(-1, 1))
        for y in range(SOL + 1, SOL + 1 + h):
            put(x, y, z, choix(T_LABY_MUR, rng.random()))
        if rng.random() < 0.3:
            put(x, SOL + 1 + h, z, BARREAUX if rng.random() < 0.3 else MURET)
    occuper(pts, "ruine")
    return True


n_r = 0
for _ in range(1500):
    if n_r >= V(45, 12):
        break
    if ruine(*cellule(rng.uniform(*V((24, 118), (13, 57))), rng.uniform(0, 360))):
        n_r += 1


def mare(cx, cz):
    r = rng.uniform(1.5, 2.6)
    cells = list(disque(cx + 0.5, cz + 0.5, r + 1.2))
    if not all(libre(c) for c in cells):
        return False
    for x, z in cells:
        if math.hypot(x - cx, z - cz) <= r:
            put(x, SOL, z, LAVE)
            put(x, SOL - 1, z, S("blackstone"))
        else:
            put(x, SOL, z, S("magma_block"))
    occuper(cells, "mare")
    return True


n_m = 0
for _ in range(600):
    if n_m >= V(12, 4):
        break
    if mare(*cellule(rng.uniform(*V((24, 112), (13, 54))), rng.uniform(0, 360))):
        n_m += 1

# braseros le long du chemin de ronde au sol (perimetre)
for k in range(V(48, 24)):
    a = V(7.5 * k + 3.75, 15 * k + 7.5)
    if min(adiff(a, g) for g in GATES) < 8:
        continue
    x, z = cellule(V(117.5, 57.5), a)
    if occ.get((x, z)) not in ("perim",):
        continue
    put(x, SOL + 1, z, BRIQ)
    put(x, SOL + 2, z, S("netherrack"))
    put(x, SOL + 3, z, FEU)
    occ[(x, z)] = "brasero"

# fissures de magma
for _ in range(V(30, 8)):
    a = rng.uniform(0, 2 * math.pi)
    x, z = (float(v) for v in cellule(rng.uniform(*V((24, 112), (13, 54))), rng.uniform(0, 360)))
    for _ in range(rng.randint(8, 16)):
        c = (math.floor(x), math.floor(z))
        if libre(c) or occ.get(c) == "perim":
            put(c[0], SOL, c[1], S("magma_block"))
        a += rng.uniform(-0.6, 0.6)
        x, z = x + math.cos(a), z + math.sin(a)

# relief de surface, debris, feux eternels sur les cases libres
for x in range(SX):
    for z in range(SZ):
        c = (x, z)
        if not libre(c) or nom((x, SOL + 1, z)) not in (None, "air"):
            continue
        n = bruit(x, z, 7, 90)
        u = h01(x, 0, z, 91)
        sol = nom((x, SOL, z))
        if sol == "lava":
            continue
        if n > 0.78:                                          # bosses (relief)
            h = 2 if n > 0.88 else 1
            for y in range(SOL + 1, SOL + 1 + h):
                put(x, y, z, choix(T_ROCHE, h01(x, y, z, 92)))
            continue
        if u < 0.02:
            put(x, SOL + 1, z, choix(T_DEBRIS, h01(x, 1, z, 93)))
        elif u < 0.045 and sol == "netherrack":
            put(x, SOL + 1, z, FEU)
        elif u < 0.06 and sol in ("soul_soil", "soul_sand"):
            put(x, SOL + 1, z, FEU_AME)
verifier("7. decoration")

# ====================================================================== ecriture
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
racine = nbtlib.File({
    "DataVersion": Int(DATA_VERSION),
    "size": List[Int]([Int(SX), Int(SY), Int(SZ)]),
    "palette": List[Compound](palette),
    "blocks": List[Compound](liste),
    "entities": List[Compound]([]),
}, gzipped=True)
for chemin in SORTIES:
    chemin.parent.mkdir(parents=True, exist_ok=True)
    racine.save(chemin)

ox, oy, oz = ORIGINE
lignes = [f"# [claude-build] Efface / aplanit l'emprise de {NOM} : {ARGS.sous_sol} Y{oy}..{oy + SOL - 1}, "
          f"{ARGS.surface} Y{oy + SOL}, air Y{oy + SOL + 1}..{oy + SY - 1}.",
          f"# Se placer pres du centre ({CENTRE_MONDE[0]} ~ {CENTRE_MONDE[1]}). 2 /fill par couche (< 32768 blocs)."]
for y in range(oy, oy + SY):
    bloc = (f"minecraft:{ARGS.surface}" if y == oy + SOL else
            f"minecraft:{ARGS.sous_sol}" if y < oy + SOL else "minecraft:air")
    lignes.append(f"fill {ox} {y} {oz} {ox + SX // 2 - 1} {y} {oz + SZ - 1} {bloc}")
    lignes.append(f"fill {ox + SX // 2} {y} {oz} {ox + SX - 1} {y} {oz + SZ - 1} {bloc}")
lignes.append(f'tellraw @s {{"text":"Arene {NOM} effacee","color":"gold"}}')
FONCTION.parent.mkdir(parents=True, exist_ok=True)
FONCTION.write_text("\n".join(lignes) + "\n", encoding="utf-8")

# liste des salles cachees
SALLES.sort()
md = ["<!-- [claude-build] genere par sources/forge_v2.py -->", "# Salles cachées — arène forge_v2", "",
      f"Monde « {nbtlib.load(SAVE / 'level.dat')['Data']['LevelName']} » (dossier `saves/{ARGS.monde}`). Arène centrée en X={CENTRE_MONDE[0]} Z={CENTRE_MONDE[1]}, sol à Y={ARGS.sol}. "
      "Coordonnées = bloc où poser le coffre (case vide, sol en roche noire dorée).", "",
      "| N° | Coffre (X Y Z) | Accès |", "|---|---|---|"]
ACCENTS = [("derriere", "derrière"), ("echelle", "échelle"), ("obelisque", "obélisque"), ("cachee", "cachée"),
           ("a cote", "à côté"), ("cote ", "côté "), ("tournee vers l'arene", "tournée vers l'arène"),
           ("pres de", "près de"), ("le cote,", "le côté,"), ("escalier cache ", "escalier caché ")]
for num, (x, y, z), acces in SALLES:
    wx, wy, wz = monde(x, y, z)
    for a, b in ACCENTS:
        acces = acces.replace(a, b)
    md.append(f"| {num} | {wx} {wy} {wz} | {acces} |")
md += ["", "## Ce qui a été construit", "",
       f"- Enceinte circulaire Ø{int(2 * R)} (centre entre 4 blocs, cercle symétrique), mur de "
       f"{str(R - R_MUR).replace('.', ',')} blocs d'épaisseur, {H_MUR - SOL} blocs + créneaux, contreforts, "
       "bandes de textures ; chemin de ronde praticable.",
       f"- 4 portes monumentales (N, S, E, O) : arche, 2 tours de garde de {GT['haut']} blocs par porte "
       "(échelle, toit à braseros), fenêtre de lave derrière des barreaux, braseros, chaînes et lanternes sous l'arche.",
       "- Centre : autel surélevé, obélisque d'obsidienne, braseros de feu des âmes, fosse de lave, 4 ponts à rambardes.",
       "- 4 tours de styles différents (basalte ronde, briques du Nether carrée, blackstone en ruine, obsidienne "
       f"octogonale de {INFO_TOURS['NO'][3] - SOL} blocs) reliées par un aqueduc en hauteur "
       f"(Y{ORIGINE[1] + TABLIER}) sur arches, avec un saut par quadrant.",
       "- 4 labyrinthes (un par quadrant), murs de 5 blocs, impasses, cellules de lave visibles derrière des barreaux.",
       "- Parcours : fosse de lave à piliers de basalte (NE), piliers montants vers un rocher (SE), rocher à cascade "
       "de lave (NO), tranchées (SO) ; plateformes surélevées, colonnes debout / brisées / tombées, ruines, "
       "champignons géants carmin et biscornus, mares de lave, fissures de magma, feux éternels.",
       f"- {len(SALLES)} salles cachées décorées, chacune avec un emplacement de coffre libre (sol doré).",
       "", "## Écarts par rapport au prompt", "",
       "- Construction par structure (.nbt + /place) au lieu de /fill en jeu : les 7 phases sont dans le script, "
       "chacune vérifiée (bornes, confinement de la lave, supports, aucun conteneur ni entité, rien hors de l'enceinte).",
       "- « Faux mur » : le Minecraft vanilla n'a pas de bloc traversable qui imite un mur (sans redstone). "
       "Il est réalisé en chicane : un pan de mur décalé masque un passage latéral d'un bloc.",
       "- `/gamerule doFireTick false` et `/forceload` sont à taper en jeu (commandes fournies).",
       "- Les blocs avec inventaire sont tous exclus, y compris fourneaux et feux de camp (choix de prudence).",
       ]
LISTE_SALLES.write_text("\n".join(md) + "\n", encoding="utf-8")

# plan vu de dessus
try:
    from PIL import Image, ImageDraw
    COUL = {"lava": (255, 110, 0), "magma_block": (190, 70, 20), "netherrack": (120, 40, 40),
            "crimson_nylium": (150, 25, 30), "warped_nylium": (30, 110, 100), "nether_wart_block": (130, 10, 10),
            "warped_wart_block": (20, 120, 120), "red_nether_bricks": (90, 10, 15), "blackstone": (45, 40, 48),
            "basalt": (75, 75, 80), "smooth_basalt": (60, 60, 65), "polished_blackstone": (60, 55, 65),
            "polished_blackstone_bricks": (85, 78, 90), "cracked_polished_blackstone_bricks": (80, 72, 85),
            "chiseled_polished_blackstone": (95, 88, 100), "gilded_blackstone": (180, 140, 40),
            "soul_soil": (80, 62, 50), "soul_sand": (95, 75, 60), "bone_block": (220, 215, 195),
            "nether_bricks": (60, 30, 35), "cracked_nether_bricks": (55, 28, 32), "obsidian": (25, 15, 40),
            "crying_obsidian": (70, 20, 120), "shroomlight": (255, 170, 80), "polished_basalt": (95, 95, 100),
            "fire": (255, 200, 40), "soul_fire": (60, 220, 230), "soul_lantern": (90, 230, 240),
            "crimson_trapdoor": (0, 255, 255), "iron_bars": (150, 150, 150), "blackstone_wall": (55, 50, 58),
            "crimson_stem": (110, 40, 60), "warped_stem": (40, 90, 90)}
    E = 4
    img = Image.new("RGB", (SX * E + 20, SZ * E + 60), (20, 20, 24))
    dr = ImageDraw.Draw(img)
    hauts = {}
    for (x, y, z), s in blocs.items():
        if s != AIR and y > hauts.get((x, z), (-99, None))[0]:
            hauts[(x, z)] = (y, s[0].split(":")[1])
    for (x, z), (y, n) in hauts.items():
        c = COUL.get(n, (128, 128, 128))
        k = 0.6 + 0.4 * min(1, max(0, (y - SOL + 2) / 20))
        dr.rectangle([10 + x * E, 30 + z * E, 10 + x * E + E - 1, 30 + z * E + E - 1], fill=tuple(int(v * k) for v in c))
    for num, (x, y, z), _ in SALLES:
        dr.ellipse([10 + x * E - 9, 30 + z * E - 9, 10 + x * E + 9, 30 + z * E + 9], outline=(0, 255, 120), width=3)
        dr.text((10 + x * E + 11, 30 + z * E - 6), str(num), fill=(0, 255, 120))
    dr.text((10, 8), "FORGE V2 - vue de dessus (nord en haut). Cercles verts = salles cachees (emplacement coffre)",
            fill=(255, 220, 180))
    img.save(PLAN)
except ImportError:
    print("Pillow absent : pas de plan")

print(f"Arene ecrite : {len(liste)} blocs, palette {len(palette)}, taille {SX}x{SY}x{SZ}")
print(f"Champignons {n_ch}, ruines {n_r}, mares {n_m}")
for num, (x, y, z), acces in SALLES:
    print(f"  salle {num}: coffre {monde(x, y, z)}  -- {acces}")
print(f"POSE : /tp @s {CENTRE_MONDE[0]} {ARGS.sol + 60} {CENTRE_MONDE[1]}   puis   "
      f"/place template atelier:{NOM} {ORIGINE[0]} {ORIGINE[1]} {ORIGINE[2]}")
print(f"FORCELOAD : /forceload add {ORIGINE[0]} {ORIGINE[2]} {ORIGINE[0] + SX - 1} {ORIGINE[2] + SZ - 1}")
