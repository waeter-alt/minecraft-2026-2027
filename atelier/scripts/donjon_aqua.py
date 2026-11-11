# [claude-build]
"""
donjon_aqua.py - Donjon aquatique « Le Sanctuaire des Marees » (2026-11-10) : un monument oceanique englouti, ~31 min. Les salles sont noyees jusqu'au plafond ;
seuls le Debarcadere (depart), la salle du boss et les cloches d'air (points de recharge du voile) contiennent de l'air.
Le Voile des Profondeurs (mod Grappin 2.5.0) est donne a l'entree ; les tunnels submerges ne se traversent qu'avec lui (« pression des profondeurs »).
Pas de grappin, pas de conduit, pas de lave, ni coffre ni mob ni boss. Eau confinee (controlee). Gabarit 150 x 70 x 170, pose en (150, -61, 260).
Plan : docs/donjon_aquatique_plan.md.
  test-local\\venv\\Scripts\\python.exe atelier\\scripts\\donjon_aqua.py
En jeu : /reload puis /function atelier:aqua_pose ; remise a zero : /function atelier:aqua_reset
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
ap.add_argument("--monde", default="Donjon 2", help="nom du monde tel qu'il s'affiche en jeu")
args = ap.parse_args()
RACINE = Path(__file__).resolve().parent.parent                      # atelier/
SAVES = RACINE.parent / "mod/run/client/saves"


def dossier_du_monde(nom_):
    """Le monde est designe par son nom en jeu (LevelName de level.dat) : le dossier peut porter un autre nom (le monde « Donjon 2 » est dans « Donjon 2 (1) »,
    et le dossier « Donjon 2 » contient le monde « Donjon 1 »). A defaut de monde de ce nom, on prend le dossier de ce nom."""
    for d_ in (sorted(SAVES.iterdir()) if SAVES.exists() else []):
        try:
            if str(nbtlib.load(d_ / "level.dat")["Data"]["LevelName"]) == nom_:
                return d_
        except Exception:
            continue
    return SAVES / nom_


SAVE = dossier_du_monde(args.monde)
print(f"[0] monde « {args.monde} » -> dossier {SAVE.name}", flush=True)
NOM = "aqua"
SX, SY, SZ = 150, 70, 170
ORIGINE = (150, -61, 260)


def S(n, **p):
    n = n if ":" in n else "minecraft:" + n
    return (n, tuple(sorted((k, str(v).lower() if isinstance(v, bool) else str(v)) for k, v in p.items())))


EAU = S("water", level=0)
BARRES = S("iron_bars", waterlogged=False)
BARRES_E = S("iron_bars", waterlogged=True)
# grille posee au sol d'un puits : des barres a plat laissent des trous de 7 pixels (on passe en nageant) ; une trappe de fer fermee, au motif de grille, bouche toute la case
GRILLE = S("iron_trapdoor", half="bottom", facing="north", open=False, powered=False, waterlogged=True)
blocs, NBT = {}, {}


def put(x, y, z, s):
    x, y, z = int(x), int(y), int(z)
    if not (0 <= x < SX and 0 <= y < SY and 0 <= z < SZ):
        raise ValueError(f"hors gabarit {(x, y, z)} {s[0]}")
    blocs[(x, y, z)] = s


def nom(p):
    s = blocs.get(p)
    return None if s is None else s[0].split(":")[-1]


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


# ====================================================================== geometrie
SOL = 43                                                           # bloc de sol des zones seches : on y marche a y = 44 ; la surface de l'eau est aussi a 44 (dernier bloc d'eau y = 43)
HAUT = 58                                                          # plafond des salles
C = np.zeros((SX, SY, SZ), dtype=bool)                             # volumes creux
W = np.zeros((SX, SY, SZ), dtype=bool)                             # cases d'eau (sources)
PRESSION = []                                                      # boites des tunnels : (x0, x1, y0, y1, z0, z1)
PORTES, LEVIERS, POINTS = {}, {}, {}


def boite(x0, x1, y0, y1, z0, z1):
    C[x0:x1 + 1, y0:y1 + 1, z0:z1 + 1] = True


def eau(x0, x1, y0, y1, z0, z1):
    boite(x0, x1, y0, y1, z0, z1)
    W[x0:x1 + 1, y0:y1 + 1, z0:z1 + 1] = True


SALLES = {
    "S1": (10, 45, 6, 38, 69), "S2": (10, 70, 44, 80, HAUT), "S3": (76, 140, 44, 80, HAUT), "S5": (100, 140, 86, 116, HAUT),
    "S6": (60, 96, 86, 116, HAUT), "S8": (10, 45, 86, 116, HAUT), "S9": (10, 45, 122, 150, HAUT), "S10": (62, 140, 120, 166, 66),
}
for n_, (x0, x1, z0, z1, top) in SALLES.items():
    if n_ in ("S1", "S10"):
        boite(x0, x1, SOL + 1, top, z0, z1)                        # le depart et la salle du boss gardent leur volume d'air ; les bassins sont creuses dessous
    else:
        eau(x0, x1, SOL + 1, top, z0, z1)                          # toutes les autres salles sont noyees jusqu'au plafond
POOLS = {                                                          # nom: (x0, x1, z0, z1, fond du bassin) ; l'eau va du fond + 1 a 43
    "P1": (24, 31, 18, 25, 36), "S2": (14, 66, 48, 76, 40), "C1": (80, 97, 48, 76, 38), "C2": (102, 118, 48, 76, 38), "C3": (122, 138, 48, 76, 38),
    "S5": (104, 136, 90, 112, 36), "A": (62, 78, 90, 112, 34), "B": (82, 94, 90, 112, 40), "P8a": (36, 44, 90, 98, 38), "P8b": (36, 44, 104, 112, 38),
    "P9": (36, 44, 126, 134, 38), "S10": (82, 120, 130, 156, 14),
}
for n_, (x0, x1, z0, z1, fond) in POOLS.items():
    eau(x0, x1, fond + 1, SOL, z0, z1)
# passages inondes entre les trois citernes (eau a y 40..43, air au dessus)
for (px0, px1) in ((99, 101), (119, 121)):
    boite(px0, px1, 40, 47, 60, 62)
    W[px0:px1 + 1, 40:SOL + 1, 60:63] = True
# couloirs noyes (3 de large, 4 de haut) entre les salles
COULOIRS = {"G2": (71, 75, SOL + 1, SOL + 4, 60, 62), "G4": (97, 99, SOL + 1, SOL + 4, 100, 102), "G7": (46, 56, SOL + 1, SOL + 4, 135, 137)}
for n_, (x0, x1, y0, y1, z0, z1) in COULOIRS.items():
    eau(x0, x1, y0, y1, z0, z1)


def tube(sommets, r=1, pression=True):
    """Tunnel submerge de (2r+1) de cote le long d'une ligne brisee de sommets (x, y, z) alignes sur les axes ; renvoie sa longueur.
    Avec pression=False c'est un simple passage noye (sans degats pour qui n'a pas le voile)."""
    longueur = 0
    for (a, b) in zip(sommets[:-1], sommets[1:]):
        x0, x1 = sorted((a[0], b[0]))
        y0, y1 = sorted((a[1], b[1]))
        z0, z1 = sorted((a[2], b[2]))
        eau(x0 - r, x1 + r, y0 - r, y1 + r, z0 - r, z1 + r)
        if pression:
            PRESSION.append((x0 - r, x1 + r, y0 - r, y1 + r, z0 - r, z1 + r))
        longueur += abs(b[0] - a[0]) + abs(b[1] - a[1]) + abs(b[2] - a[2])
    return longueur


TUNNELS = {}
TUNNELS["T1"] = [(131, 38, 74), (131, 34, 74), (131, 34, 95), (120, 34, 95), (120, 36, 95)]                  # citerne C3 -> bibliotheque noyee
TUNNELS["T2"] = [(66, 34, 100), (66, 30, 100), (40, 30, 100), (40, 30, 94), (40, 38, 94)]                    # bassin A des ecluses -> salle des pompes
TUNNELS["T3"] = [(40, 38, 108), (40, 26, 108), (40, 26, 130), (40, 38, 130)]                                  # pompes -> antichambre
LONG = {n_: tube(v) for n_, v in TUNNELS.items()}
# passages noyes sans pression : la galerie du depart (du mini-bassin du Debarcadere a la Colonnade) et la galerie qui amene sous le bassin du Coeur
PASSAGES = {"G1": [(27, 38, 26), (27, 38, 42), (27, 45, 42)], "G7": [(55, 46, 136), (55, 38, 136), (84, 38, 136)]}
for n_, v in PASSAGES.items():
    tube(v, pression=False)
# profondeurs de plongee jusqu'a l'entree du tunnel (depuis la surface 44)
PLONGEE = {"T1": 44 - 38, "T2": 44 - 34, "T3": 44 - 38}
SORTIE_T = {"T1": 44 - 36, "T2": 44 - 38, "T3": 44 - 38}
print("[1] tunnels (blocs) :", LONG, flush=True)

# ---------------------------------------------------------------- salles cachees : 4 a sec (porte camouflee) et 4 sous l'eau (tunnel submerge, il faut le voile)
CACHEES = {}
HID_SEC = [  # n, (x0, z0), couloir (axe, lateral, a, b), plan de la porte, bouton (x, y, z, facing)
    (1, (52, 10), ("x", (13, 14, 15), 46, 51), 46, (45, SOL + 2, 18, "west"), "S1 : faux mur de prismarine dans le mur est du Débarcadère (bouton de pierre discret, à sec)"),
    (2, (92, 14), ("z", (95, 96, 97), 23, 43), 43, (92, SOL + 2, 44, "south"), "S3 : faux mur dans le mur nord de la salle des Citernes (vanne discrète, salle noyée)"),
    (3, (112, 14), ("z", (115, 116, 117), 23, 43), 43, (112, SOL + 2, 44, "south"), "S3 : faux mur dans le mur nord, plus loin (vanne discrète, salle noyée)"),
    (4, (48, 94), ("x", (97, 98, 99), 57, 59), 59, (60, SOL + 2, 102, "east"), "S6 : faux mur dans le mur ouest des Écluses (vanne discrète, salle noyée)"),
]
for (n_, (bx, bz), (axe, lat, a, b), pv, (bx_, by_, bz_, fb), acces) in HID_SEC:
    if n_ == 1:                                                    # la salle cachee 1 touche le depart (au sec) ; les autres sont noyees, avec une rangee d'air sous le plafond (on y respire)
        boite(bx, bx + 8, SOL + 1, SOL + 5, bz, bz + 8)
        vol = boite
    else:
        eau(bx, bx + 8, SOL + 1, SOL + 4, bz, bz + 8)
        boite(bx, bx + 8, SOL + 5, SOL + 5, bz, bz + 8)
        vol = eau
    if axe == "x":
        vol(a, b, SOL + 1, SOL + 3, lat[0], lat[-1])
    else:
        vol(lat[0], lat[-1], SOL + 1, SOL + 3, a, b)
    CACHEES[n_] = {"boite": (bx, bx + 8, SOL + 1, SOL + 5, bz, bz + 8), "acces": acces, "bouton": (bx_, by_, bz_), "fb": fb, "plan": pv, "axe": axe, "lat": lat,
                   "voile": False, "eau": n_ != 1, "coffre": (bx + 1, SOL + 1, bz + 7), "mobs": (bx + 5, SOL + 1, bz + 3)}
FLH = 23                                                           # sol des salles cachees sous l'eau
HID_EAU = [  # n, (x0, z0) de la salle, (px, pz) du puits d'entree, fond du bassin d'entree, hauteur du tunnel
    (5, (14, 52), (30, 56, 40), 20, "S2 : puits au fond du grand bassin de la Colonnade, tunnel submergé vers une salle cachée d'air"),
    (6, (82, 54), (96, 58, 38), 20, "S3 : puits dans la première citerne, tunnel submergé"),
    (7, (106, 94), (124, 98, 36), 20, "S5 : puits dans le bassin de la bibliothèque, tunnel submergé"),
    (8, (126, 138), (120, 142, 18), 20, "S10 : galerie sous le bord du bassin du Cœur, tunnel submergé"),
]
for (n_, (bx, bz), (px, pz, fp), yt, acces) in HID_EAU:
    rx, rz = bx + 4, bz + 4
    boite(bx, bx + 8, FLH + 1, FLH + 5, bz, bz + 8)
    if n_ == 8:
        som = [(px, fp, pz), (rx, fp, pz), (rx, fp, rz), (rx, FLH - 1, rz)]
    else:
        som = [(px, fp, pz), (px, yt, pz), (rx, yt, pz), (rx, yt, rz), (rx, FLH - 1, rz)]
    LONG[f"H{n_}"] = tube(som)
    CACHEES[n_] = {"boite": (bx, bx + 8, FLH + 1, FLH + 5, bz, bz + 8), "acces": acces, "voile": True, "eau": False, "coffre": (bx + 1, FLH + 1, bz + 7), "mobs": (bx + 6, FLH + 1, bz + 2),
                   "puits": (px, fp, pz)}
print("[1b] volumes :", int(C.sum()), "cases creuses,", int(W.sum()), "cases d'eau", flush=True)

# ====================================================================== coquille
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
PAL = [(S("prismarine_bricks"), 6), (S("prismarine"), 3), (S("dark_prismarine"), 2)]                  # palette du monument oceanique
for x, y, z in zip(*np.nonzero(COQUE)):
    put(x, y, z, pick(PAL, bruit(x + y * .5, z - y * .3, 3, 800) * .7 + h01(x, y, z, 801) * .3))
for x, y, z in zip(*np.nonzero(C & W)):
    put(x, y, z, EAU)
print("[2] coquille + eau :", len(blocs), flush=True)


def vide(p):
    x, y, z = p
    return 0 <= x < SX and 0 <= y < SY and 0 <= z < SZ and C[x, y, z] and p not in blocs


def porte(n, cases, s=BARRES_E, eau_=None):
    """Une porte par defaut est une herse de barres noyees ; eau_=True pour un mur plein qui, ouvert, laisse place a de l'eau."""
    PORTES[n] = {"cases": [list(c) for c in cases], "bloc": s[0], "props": dict(s[1]), "eau": (dict(s[1]).get("waterlogged") == "true") if eau_ is None else eau_}
    for c in cases:
        put(*c, s)


exec(compile((Path(__file__).resolve().parent / "donjon_aqua_missions.py").read_text(encoding="utf-8"), "donjon_aqua_missions.py", "exec"), globals())
