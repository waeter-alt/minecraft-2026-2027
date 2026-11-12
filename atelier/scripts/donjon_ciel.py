# [claude-build]
"""
donjon_ciel.py - Donjon n°3 « L'Archipel des Nuees » (2026-11-11) : un temple celeste en ruine, eclate en iles flottantes, ~32 min.
La Paravoile (mod Grappin 2.6.0) est donnee a l'entree. On monte a l'interieur des tours (escaliers, leviers), on redescend en planant d'ile en ile ;
tomber dans le vide ramene au dernier point de passage. Pas de grappin, ni elytres, ni autre moyen de voler. Pas de coffre, de monstre ni de boss.
Gabarit 280 x 120 x 280. Plan : docs/donjon_ciel_plan.md.
  test-local\\venv\\Scripts\\python.exe atelier\\scripts\\donjon_ciel.py
En jeu : /reload puis /function atelier:ciel_pose ; remise a zero : /function atelier:ciel_reset
Le monde est designe par son nom en jeu (--monde, « Donjon 3 » par defaut) ; il doit exister (cree dans le client de test) pour recevoir le datapack.
"""
import argparse
import json
import sys
import math
from collections import Counter, deque
from pathlib import Path

import nbtlib
from nbtlib import Byte, Compound, Double, Float, Int, List, String

ap = argparse.ArgumentParser()
ap.add_argument("--monde", default="Donjon 3", help="nom du monde tel qu'il s'affiche en jeu")
ap.add_argument("--zip", action="store_true", help="ecrit aussi atelier/mondes/<monde>.zip : un monde pret a copier dans saves/")
args = ap.parse_args()
RACINE = Path(__file__).resolve().parent.parent                      # atelier/
SAVES = RACINE.parent / "mod/run/client/saves"

sys.path.insert(0, str(Path(__file__).resolve().parent))
import monde_atelier                                                  # cree le monde de test s'il n'existe pas

SAVE = monde_atelier.trouver_ou_creer_monde(args.monde, SAVES)
print(f"[0] monde « {args.monde} » -> dossier {SAVE.name}", flush=True)
NOM = "ciel"
SX, SY, SZ = 280, 120, 280
ORIGINE = (100, -61, 100)
VIDE_Y = 14                                                        # sous ce niveau (Y local), on est « tombe » : retour au dernier point de passage


def S(n, **p):
    n = n if ":" in n else "minecraft:" + n
    return (n, tuple(sorted((k, str(v).lower() if isinstance(v, bool) else str(v)) for k, v in p.items())))


blocs, NBT = {}, {}
ENTITES = []                                                       # entites posees avec la structure (les anneaux de vol : des blocs d'affichage, sans collision)


def put(x, y, z, s):
    x, y, z = int(x), int(y), int(z)
    if not (0 <= x < SX and 0 <= y < SY and 0 <= z < SZ):
        raise ValueError(f"hors gabarit {(x, y, z)} {s[0]}")
    blocs[(x, y, z)] = s


def nom(p):
    s = blocs.get(p)
    return None if s is None else s[0].split(":")[-1]


def retire(x, y, z):
    blocs.pop((int(x), int(y), int(z)), None)
    NBT.pop((int(x), int(y), int(z)), None)


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


def pick(liste, v):
    tot = sum(p for _, p in liste)
    v *= tot
    for b, p in liste:
        v -= p
        if v < 0:
            return b
    return liste[-1][0]


# ====================================================================== palette
BRIQUES = [(S("stone_bricks"), 6), (S("mossy_stone_bricks"), 3), (S("cracked_stone_bricks"), 2)]
ROCHE = [(S("stone"), 5), (S("andesite"), 3), (S("cobblestone"), 2), (S("tuff"), 2), (S("mossy_cobblestone"), 1)]
HERBE, TERRE, MOUSSE = S("grass_block", snowy=False), S("dirt"), S("moss_block")
SOL_A, SOL_B = S("polished_andesite"), S("stone_bricks")
BARRES = S("iron_bars", waterlogged=False)
LANTERNE = S("lantern", hanging=False, waterlogged=False)
ROCHES = {"stone", "andesite", "cobblestone", "tuff", "mossy_cobblestone", "dirt", "grass_block"}


def mur(x, y, z):
    return pick(BRIQUES, h01(x, y, z, 11))


def roche(x, y, z):
    return pick(ROCHE, h01(x, y, z, 12))


def escalier(face):
    return S("stone_brick_stairs", facing=face, half="bottom", shape="straight", waterlogged=False)


def signe(x, y, z, facing, lignes):
    assert all(len(l) <= 14 for l in lignes) and len(lignes) <= 4, ("panneau trop large (14 caracteres max par ligne)", lignes)
    put(x, y, z, S("oak_wall_sign", facing=facing, waterlogged=False))
    msg = [String(json.dumps({"text": l or ""}, ensure_ascii=False)) for l in (list(lignes) + ["", "", "", ""])[:4]]
    face = Compound({"messages": List[String](msg), "color": String("black"), "has_glowing_text": Byte(0)})
    NBT[(x, y, z)] = Compound({"id": String("minecraft:sign"), "is_waxed": Byte(1), "front_text": face, "back_text": face})


def levier(x, y, z, facing):
    put(x, y, z, S("lever", face="wall", facing=facing, powered=False))


def bouton(x, y, z, facing):
    put(x, y, z, S("stone_button", face="wall", facing=facing, powered=False))


def lumiere(x, y, z, niveau=14):
    put(x, y, z, S("light", level=niveau, waterlogged=False))


# ====================================================================== iles
ILES = {}                                                          # nom -> description
INTERIEUR = set()                                                  # cases d'air interieures (tours, salles) : controlees pour la lumiere
PORTES = {}                                                        # nom -> {"cases": [...], "leviers": [...]}
POINTS = {}                                                        # cibles du verificateur (pieds)
CACHEES = {}
VOLS_CHEMINS = {}                                                  # vols qui doivent passer par des points de passage : "A>B" -> [[x, z], ...]


def ile(nom_, cx, cz, r, T, rect=None, ordre=None):
    """Ile flottante : dessus herbeux a Y = T, corps de roche en paraboloide dessous. rect=(ax, bz) donne une ile rectangulaire (demi-cotes)."""
    ILES[nom_] = {"cx": cx, "cz": cz, "r": r, "T": T, "rect": rect, "ordre": ordre, "tours": []}
    g = sum(ord(c) for c in nom_)
    ax, bz = rect if rect else (r, r)
    marge = int(max(ax, bz) * 1.25) + 2
    for x in range(cx - marge, cx + marge + 1):
        for z in range(cz - marge, cz + marge + 1):
            dx, dz = x - cx, z - cz
            if rect:
                d = max(abs(dx) / ax, abs(dz) / bz) * (1 + 0.12 * (2 * bruit(x, z, 7, g) - 1))
            else:
                d = math.hypot(dx, dz) / (r * (1 + 0.16 * (2 * bruit(x, z, 8, g) - 1)))
            if d > 1:
                continue
            prof = int(1.2 * min(ax, bz) * (1 - d * d) * (0.85 + 0.3 * bruit(x, z, 5, g + 1))) + 2
            for y in range(T - prof, T + 1):
                if y == T:
                    b = MOUSSE if h01(x, y, z, 13) < .12 else HERBE
                elif y >= T - 2:
                    b = TERRE
                else:
                    b = roche(x, y, z)
                put(x, y, z, b)


def dessus(nom_, x, z):
    """Vrai si (x, z) est sur le dessus de l'ile (herbe ou autre sol pose a Y = T)."""
    return (x, ILES[nom_]["T"], z) in blocs


def point_sol(nom_, dx=0, dz=0):
    """Case de pieds libre sur l'ile, la plus proche de (centre + dx, centre + dz) : sol a Y = T, deux cases d'air au dessus."""
    ile_ = ILES[nom_]
    T = ile_["T"]
    meilleur = None
    for ddx in range(-8, 9):
        for ddz in range(-8, 9):
            x, z = ile_["cx"] + dx + ddx, ile_["cz"] + dz + ddz
            if (x, T, z) in blocs and (x, T + 1, z) not in blocs and (x, T + 2, z) not in blocs and (x, T + 3, z) not in blocs:
                d = abs(ddx) + abs(ddz)
                if meilleur is None or d < meilleur[0]:
                    meilleur = (d, (x, T + 1, z))
    assert meilleur, ("pas de case libre", nom_, dx, dz)
    return meilleur[1]


def interieur(x0, x1, y0, y1, z0, z1):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                INTERIEUR.add((x, y, z))


COTES = {"n": (0, -1), "s": (0, 1), "e": (1, 0), "o": (-1, 0)}
FACE_VERS = {"n": "north", "s": "south", "e": "east", "o": "west"}


def tour(nom_, cx, cz, hs, n, hl, entree, sortie, portes=True, c0=0):
    """Tour carree sur l'ile `nom_` : interieur de (2hs+1)² cases, n niveaux de hl blocs ; un escalier le long d'un mur par niveau (cote k mod 4), balcon au sommet.
    Avec portes=True, la montee de chaque niveau est fermee par une herse (2 barres de fer dans l'ouverture du plancher) que fait ceder un levier du meme niveau.
    Renvoie les cibles utiles."""
    T = ILES[nom_]["T"]
    x0, x1, z0, z1 = cx - hs, cx + hs, cz - hs, cz + hs
    top = T + n * hl                                               # couche du plancher du balcon
    info = {"cx": cx, "cz": cz, "hs": hs, "n": n, "hl": hl, "top": top, "c0": c0, "entree": entree, "sortie": sortie, "portes": portes, "leviers": []}
    ILES[nom_]["tours"].append(info)
    # murs (jusqu'au parapet : 2 blocs au dessus du plancher du balcon) ; l'interieur est vide
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            bord = x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1)
            for y in range(T + 1, top + 3):
                if bord:
                    coin = x in (x0 - 1, x1 + 1) and z in (z0 - 1, z1 + 1)
                    put(x, y, z, S("chiseled_stone_bricks") if (coin and (y - T) % 5 == 0) else mur(x, y, z))
                else:
                    retire(x, y, z)
    # planchers (le sol du niveau 0 est le dessus de l'ile)
    for k in range(0, n + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                put(x, T + k * hl, z, SOL_A if (x + z + k) % 2 == 0 else SOL_B)
    # escaliers et ouvertures ; une herse par niveau
    cotes = [((x0, z0), (0, 1), "south"), ((x0, z1), (1, 0), "east"), ((x1, z1), (0, -1), "north"), ((x1, z0), (-1, 0), "west")]
    leviers, grilles = [], []
    for k in range(n):
        (sx, sz), (dx, dz), face = cotes[(k + c0) % 4]
        yk = T + k * hl
        cases = [(sx + dx * i, sz + dz * i) for i in range(hl)]
        for i in range(hl - 1):
            x, z = cases[i]
            put(x, yk + 1 + i, z, escalier(face))
            retire(x, yk + hl, z)                                  # l'escalier perce le plancher du niveau suivant
        if portes:
            x, z = cases[hl - 2]
            gr = [(x, yk + hl, z), (x, yk + hl + 1, z)]
            for c in gr:
                put(*c, BARRES)
            lx, lz, lf = [(x1, cz + 2, "west"), (cx + 2, z0, "south"), (x0, cz - 2, "east"), (cx - 2, z1, "north")][(k + c0) % 4]   # sur le mur d'en face de l'escalier
            levier(lx, yk + 2, lz, lf)
            leviers.append((lx, yk + 2, lz))
            grilles.append(gr)
            PORTES[f"{nom_}_{k + 1}"] = {"cases": [list(c) for c in gr], "leviers": [list(leviers[-1])], "tour": nom_}
    # entree (3 de large, 3 de haut) et sortie du balcon (5 de large, parapet ouvert)
    ex, ez = COTES[entree]
    for o in (-1, 0, 1):
        for y in range(T + 1, T + 4):
            if ex:
                retire(cx + ex * (hs + 1), y, cz + o)
            else:
                retire(cx + o, y, cz + ez * (hs + 1))
    sx_, sz_ = COTES[sortie]
    for o in range(-2, 3):
        for y in (top + 1, top + 2):
            if sx_:
                retire(cx + sx_ * (hs + 1), y, cz + o)
            else:
                retire(cx + o, y, cz + sz_ * (hs + 1))
    # lanternes et lumiere : chaque niveau
    for k in range(n + 1):
        w = T + k * hl + 1
        libres = [(x1, w, z0), (x0, w, z1), (x0, w, z0), (x1, w, z1)]
        posees = 0
        for (x, y, z) in libres:
            if (x, y, z) not in blocs and posees < 2:
                put(x, y, z, LANTERNE)
                posees += 1
        if k < n:
            lumiere(cx, w + 2, cz)
            interieur(x0, x1, w, w + hl - 2, z0, z1)
        else:
            interieur(x0, x1, w, w + 1, z0, z1)
    info["leviers"] = leviers
    return {"balcon": (cx, top + 1, cz), "leviers": leviers, "top": top, "niveaux": [(cx, T + k * hl + 1, cz) for k in range(n + 1)]}


def sous_sol(nom_, px, pz, T, num):
    """Salle cachee de 9 x 9 x 4 sous le dessus de l'ile. On y descend par un escalier (vers +x) que ferme une dalle de 3 cases au niveau du sol ;
    un bouton de pierre discret, colle contre un petit bloc de mur voisin, fait disparaitre la dalle."""
    cx0, cz0 = px + 4, pz - 4
    for x in range(cx0, cx0 + 9):
        for z in range(cz0, cz0 + 9):
            for y in range(T - 6, T):
                if y == T - 6:
                    put(x, y, z, S("wet_sponge") if (x + z) % 5 == 0 else SOL_A)
                elif y == T - 1:
                    put(x, y, z, mur(x, y, z))
                else:
                    retire(x, y, z)
    interieur(cx0, cx0 + 8, T - 5, T - 2, cz0, cz0 + 8)
    for i in range(5):                                             # escalier qui descend vers +x : marche i a Y = T-1-i ; trois cases libres au dessus (la tete doit passer quand on descend d'un cran)
        x, ys = px + i, T - 1 - i
        for y in (ys + 1, ys + 2, ys + 3):
            if y < T:
                retire(x, y, pz)
        put(x, ys, pz, escalier("west"))
    interieur(px + 2, px + 4, T - 4, T - 2, pz, pz)
    cap = [(px, T, pz), (px + 1, T, pz), (px + 2, T, pz)]
    for c in cap:
        assert c in blocs, ("dalle hors de l'ile", c)
        put(*c, SOL_A)
    put(px + 2, T + 1, pz + 2, mur(px + 2, T + 1, pz + 2))        # le bloc d'appui, puis le bouton contre sa face nord
    bouton(px + 2, T + 1, pz + 1, "north")
    CACHEES[num] = {"boite": (cx0, cx0 + 8, T - 5, T - 2, cz0, cz0 + 8), "bouton": (px + 2, T + 1, pz + 1), "cap": cap, "ile": nom_, "eau": False, "voile": False,
                    "coffre": (cx0 + 1, T - 5, cz0 + 7), "mobs": (cx0 + 6, T - 5, cz0 + 2),
                    "acces": f"{nom_} : dalle du sol qui s'efface (bouton de pierre discret au pied d'un bloc de mur)"}
    PORTES[f"H{num}"] = {"cases": [list(c) for c in cap], "leviers": [[px + 2, T + 1, pz + 1]], "ouvre": "air", "cachee": num}
    POINTS[f"H{num}"] = (cx0 + 4, T - 5, cz0 + 4)


# ---------------------------------------------------------------- les iles du parcours (x, z, rayon, dessus Y)
ORDRE = ["D", "P1", "P2", "P3", "AI", "G1", "G2", "G3", "MV", "RL", "CP", "CI", "BE", "BO"]
DEF = {"D": (30, 36, 14, 98), "P1": (79, 30, 5, 87), "P2": (131, 42, 5, 72), "P3": (177, 32, 5, 58), "AI": (234, 38, 17, 45),
       "G1": (241, 95, 7, 56), "G2": (236, 142, 7, 52), "G3": (242, 190, 7, 48), "MV": (232, 247, 17, 44),
       "RL": (167, 250, 7, 50), "CI": (50, 246, 16, 44), "BE": (70, 185, 15, 58), "BO": (140, 140, 30, 70)}
for k, (cx, cz, r, T) in DEF.items():
    ile(k, cx, cz, r, T, ordre=ORDRE.index(k))
ile("CP", 113, 250, 0, 44, rect=(11, 5), ordre=ORDRE.index("CP"))
ile("HA", 200, 68, 8, 62, ordre=4.5)
ile("HB", 196, 224, 8, 54, ordre=8.5)
print("[1] iles :", len(ILES), "| blocs", len(blocs), flush=True)

# ---------------------------------------------------------------- tours
CIB = {}
CIB["AI"] = tour("AI", 234, 38, 5, 3, 10, "o", "s", portes=True)
for k_, (cx, cz) in (("G1", (241, 95)), ("G2", (236, 142)), ("G3", (242, 190))):
    CIB[k_] = tour(k_, cx, cz, 3, 2, 6, "n", "s", portes=False, c0=2)
CIB["MV"] = tour("MV", 232, 247, 5, 3, 8, "n", "o", portes=False)
CIB["RL"] = tour("RL", 167, 250, 3, 2, 6, "e", "o", portes=False)
CIB["CP"] = tour("CP", 108, 250, 3, 3, 6, "e", "o", portes=False)
CIB["CI"] = tour("CI", 50, 246, 5, 4, 8, "n", "n", portes=False, c0=1)
CIB["BE"] = tour("BE", 70, 185, 5, 3, 10, "o", "e", portes=False, c0=1)
print("[2] tours posees | blocs", len(blocs), flush=True)

exec(compile((Path(__file__).resolve().parent / "donjon_ciel_decor.py").read_text(encoding="utf-8"), "donjon_ciel_decor.py", "exec"), globals())
exec(compile((Path(__file__).resolve().parent / "donjon_ciel_tours.py").read_text(encoding="utf-8"), "donjon_ciel_tours.py", "exec"), globals())
exec(compile((Path(__file__).resolve().parent / "donjon_ciel_fin.py").read_text(encoding="utf-8"), "donjon_ciel_fin.py", "exec"), globals())
