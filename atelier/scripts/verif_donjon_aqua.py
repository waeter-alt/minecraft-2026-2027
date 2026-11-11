# [claude-build]
"""
verif_donjon_aqua.py - Controle du Sanctuaire des Marees : ce qu'on atteint, etape par etape, avec ou sans le Voile des Profondeurs.
Deplacements : marche, marches de 1, chutes (6 blocs, ou dans l'eau), echelles, nage a six voisins. Les tunnels submerges sont des « zones de pression » :
sans le voile sur la tete on y prend des degats de noyade a chaque demi-seconde, donc elles sont infranchissables (on les interdit dans le calcul).
Controles : (1) avec le voile, chaque etape donne exactement les cibles attendues, ni plus tot ni moins ; (2) sans voile, portes toutes ouvertes,
on ne depasse jamais les tunnels (et on ne rejoint aucune salle cachee sous l'eau) ; (3) budget d'air : chaque tunnel doit tenir dans la reserve du voile.
  test-local\\venv\\Scripts\\python.exe atelier\\scripts\\verif_donjon_aqua.py
"""
import json
import sys
from collections import deque
from pathlib import Path

import nbtlib
import numpy as np

RACINE = Path(__file__).resolve().parent.parent
conf = json.loads((RACINE / "exports/aqua_cibles.json").read_text(encoding="utf-8"))
f = nbtlib.load(RACINE / "exports/aqua.nbt")
SX, SY, SZ = (int(v) for v in f["size"])
pal = [str(p["Name"]).split(":")[1] for p in f["palette"]]
pal_eau = [str(p.get("Properties", {}).get("waterlogged", "false")) == "true" for p in f["palette"]]
ETAT = np.full((SX, SY, SZ), -1, dtype=np.int32)
for b in f["blocks"]:
    x, y, z = (int(v) for v in b["pos"])
    ETAT[x, y, z] = int(b["state"])
_m = np.load(RACINE / "exports/aqua_vide.npz")
INT = np.unpackbits(_m["C"])[:SX * SY * SZ].astype(bool).reshape(SX, SY, SZ)

PASSE = {"air", "light", "water", "lever", "stone_button", "stone_pressure_plate", "ladder", "kelp", "kelp_plant", "seagrass", "tall_seagrass", "sea_pickle", "glow_lichen",
         "vine", "chain", "candle", "lantern", "moss_carpet"}
GRIMPE = {"ladder"}
pas_i = np.array([n in PASSE or n.endswith(("_wall_sign", "_coral_fan", "_coral_wall_fan", "_coral", "_carpet")) for n in pal])
eau_i = np.array([n in ("water", "kelp", "kelp_plant", "seagrass", "tall_seagrass") or e for n, e in zip(pal, pal_eau)])
grimpe_i = np.array([n in GRIMPE for n in pal])
PRES = np.zeros((SX, SY, SZ), dtype=bool)
for (x0, x1, y0, y1, z0, z1) in conf["pression"]:
    PRES[x0:x1 + 1, y0:y1 + 1, z0:z1 + 1] = True


def grilles(ouvertes, vide_a=False):
    e = np.maximum(ETAT, 0)
    passe = np.where(ETAT < 0, INT, pas_i[e])
    eau = np.where(ETAT < 0, False, eau_i[e])
    grimpe = np.where(ETAT < 0, False, grimpe_i[e])
    for n in ouvertes:
        p = conf["portes"][n]
        for c in p["cases"]:
            passe[tuple(c)] = True
            eau[tuple(c)] = bool(p.get("eau"))
    if vide_a:
        x0, x1, y0, y1, z0, z1 = conf["bassin_a"]
        sub = eau[x0:x1 + 1, y0:y1 + 1, z0:z1 + 1]
        pure = (ETAT[x0:x1 + 1, y0:y1 + 1, z0:z1 + 1] >= 0) & (np.array([n == "water" for n in pal])[np.maximum(ETAT[x0:x1 + 1, y0:y1 + 1, z0:z1 + 1], 0)])
        eau[x0:x1 + 1, y0:y1 + 1, z0:z1 + 1] = sub & ~pure
    solide = ~passe & ~eau
    return passe, eau, solide, grimpe


def parcours(ouvertes, voile, vide_a=False, depart=None):
    passe, eau, solide, grimpe = grilles(ouvertes, vide_a)
    bloque = np.zeros_like(PRES) if voile else PRES
    P, E, SO, G, B = (a.tolist() for a in (passe, eau, solide, grimpe, bloque))

    def ok(x, y, z):
        if not (0 <= x < SX and 1 <= y < SY - 2 and 0 <= z < SZ):
            return False
        if not P[x][y][z] or B[x][y][z]:
            return False
        if E[x][y][z]:
            return True
        return P[x][y + 1][z] and not B[x][y + 1][z]

    def appui(x, y, z):
        return E[x][y][z] or SO[x][y - 1][z] or E[x][y - 1][z] or G[x][y][z] or G[x][y - 1][z]

    vu = set()
    file = deque()

    def ajoute(c):
        if c not in vu:
            vu.add(c)
            file.append(c)

    ajoute(tuple(depart or conf["entree"]))
    while file:
        x, y, z = file.popleft()
        if E[x][y - 1][z] and ok(x, y - 1, z):                           # debout a la surface : on plonge
            ajoute((x, y - 1, z))
        if E[x][y][z]:
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                if ok(x + dx, y + dy, z + dz):
                    ajoute((x + dx, y + dy, z + dz))
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if not (0 <= nx < SX and 0 <= nz < SZ):
                continue
            if ok(nx, y, nz):
                if appui(nx, y, nz):
                    ajoute((nx, y, nz))
                else:
                    yy = y
                    while yy > 1 and not appui(nx, yy, nz) and ok(nx, yy - 1, nz):
                        yy -= 1
                    if appui(nx, yy, nz) and (y - yy <= 6 or E[nx][yy][nz]):
                        ajoute((nx, yy, nz))
            if ok(nx, y + 1, nz) and appui(nx, y + 1, nz) and y + 2 < SY and P[x][y + 2][z] and not B[x][y + 2][z]:
                ajoute((nx, y + 1, nz))
        if G[x][y][z] or G[x][y - 1][z]:
            if ok(x, y + 1, z):
                ajoute((x, y + 1, z))
            if ok(x, y - 1, z):
                ajoute((x, y - 1, z))
    return vu


def atteint(vu, cible, r):
    cx, cy, cz = cible
    return any((x - cx) ** 2 + (y - cy) ** 2 + (z - cz) ** 2 <= r * r for (x, y, z) in vu if abs(x - cx) <= r and abs(z - cz) <= r and abs(y - cy) <= r)


CIB = conf["cibles"]


def rayon(c):
    if c.startswith(("plaque",)) or (c[0] == "H" and c[1:].isdigit()):
        return 1.8
    if c.startswith(("levier", "bouton", "glyphe")):
        return 3.0
    return 2.5


# (portes ouvertes cumulees, ecluse videe ?, cibles qui deviennent atteignables a cette etape, avec le voile)
ETAPES = [
    ([], False, ["S1", "levier_G1", "bouton_H1"]),
    (["G1", "H1"], False, ["bouton_G2", "H1", "H5"]),
    (["G1", "G2", "H1"], False, ["levier_3a", "levier_3b", "bouton_H2", "bouton_H3", "H6"]),
    (["G1", "G2", "H1", "H2", "H3"], False, ["H2", "H3"]),
    (["G1", "G2", "H1", "H2", "H3", "G3"], False, ["arrivee_T1", "glyphe_poulpe", "glyphe_raie", "glyphe_crabe", "glyphe_etoile", "H7"]),
    (["G1", "G2", "H1", "H2", "H3", "G3", "G4"], False, ["levier_ecluse", "plaque_cle", "bouton_H4"]),
    (["G1", "G2", "H1", "H2", "H3", "G3", "G4", "H4"], False, ["H4"]),
    (["G1", "G2", "H1", "H2", "H3", "G3", "G4", "H4", "G5"], True, ["arrivee_T2", "levier_p20", "levier_p24", "levier_p28"]),
    (["G1", "G2", "H1", "H2", "H3", "G3", "G4", "H4", "G5", "G6"], False, ["arrivee_T3"]),
    (["G1", "G2", "H1", "H2", "H3", "G3", "G4", "H4", "G5", "G6", "G7"], False, ["Coeur", "H8"] + [f"B{i}" for i in range(1, 6)]),
    (["G1", "G2", "H1", "H2", "H3", "G3", "G4", "H4", "G5", "G6", "G7", "SORTIE"], False, ["sortie"]),
]
ok_ = True
deja = []
for k, (ouv, vide, nouvelles) in enumerate(ETAPES):
    deja = deja + nouvelles
    plus_tard = [c for c in CIB if c not in deja]
    vu = parcours(ouv, True, vide)
    manque = [c for c in deja if not atteint(vu, CIB[c], rayon(c))]
    trop = [c for c in plus_tard if atteint(vu, CIB[c], rayon(c))]
    bon = not manque and not trop
    ok_ &= bon
    print(f"etape {k} portes {ouv[-2:] or '-'} [voile] : {len(vu)} cases | manque {manque} | trop tot {trop} -> {'OK' if bon else 'ECHEC'}", flush=True)
# sans voile, toutes les portes ouvertes : on ne doit pas passer les tunnels submerges
TOUTES = list(conf["portes"])
vu0 = parcours(TOUTES, False, True)
permis = {"S1", "levier_G1", "bouton_H1", "H1", "bouton_G2", "levier_3a", "levier_3b", "bouton_H2", "bouton_H3", "H2", "H3"}
sans = sorted(c for c in CIB if atteint(vu0, CIB[c], rayon(c)))
en_trop = sorted(set(sans) - permis)
print("sans voile, toutes portes ouvertes :", sans, "->", "OK" if not en_trop else f"ECHEC (en trop : {en_trop})")
ok_ &= not en_trop
# le puits des salles cachees sous l'eau et la grille des tunnels sont a l'interieur d'une zone de pression : verifier qu'aucune case d'eau d'un tunnel n'echappe aux boites
passe, eau, solide, grimpe = grilles(TOUTES, False)
hors = []
for nom_, som in conf["tunnels"].items():
    for a, b in zip(som[:-1], som[1:]):
        for x in range(min(a[0], b[0]), max(a[0], b[0]) + 1):
            for y in range(min(a[1], b[1]), max(a[1], b[1]) + 1):
                for z in range(min(a[2], b[2]), max(a[2], b[2]) + 1):
                    if not PRES[x, y, z]:
                        hors.append((nom_, x, y, z))
print("axes des tunnels hors zone de pression :", hors[:5] or "aucun", "->", "OK" if not hors else "ECHEC")
ok_ &= not hors

# budget d'air : plus court chemin (a six voisins) dans l'eau entre l'entree en surface et la sortie en surface de chaque tunnel
RES, AIR, RECUP = 40.0, 15.0, 12.0
VIT_N, VIT_R = 2.2, 4.0                                                   # nage ordinaire / nage tres rapide (blocs par seconde)
passe, eau, solide, grimpe = grilles(TOUTES, False)
E = eau.tolist()


def plus_court(a, b):
    dist = {a: 0}
    file = deque([a])
    while file:
        p = file.popleft()
        if p == b:
            return dist[p]
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            q = (p[0] + d[0], p[1] + d[1], p[2] + d[2])
            if q not in dist and 0 <= q[0] < SX and 0 <= q[1] < SY and 0 <= q[2] < SZ and E[q[0]][q[1]][q[2]]:
                dist[q] = dist[p] + 1
                file.append(q)
    return None


TRAJETS = {"T1": ((131, 43, 74), (120, 43, 95)), "T2": ((66, 43, 100), (40, 43, 94)), "T3": ((40, 43, 108), (40, 43, 130))}
for n_, c in conf["cachees_detail"].items():
    if c.get("puits"):
        px, fp, pz = c["puits"]
        x0, x1, y0, y1, z0, z1 = c["boite"]
        TRAJETS[f"H{n_}"] = ((px, 43, pz), ((x0 + x1) // 2, y0 - 1, (z0 + z1) // 2))
print(f"voile : reserve {RES:.0f} s + air {AIR:.0f} s ; nage ordinaire {VIT_N} b/s, rapide {VIT_R} b/s")
BILAN = {}
for n_, (a, b) in TRAJETS.items():
    d = plus_court(a, b)
    if d is None:
        print(f"trajet {n_} : INTROUVABLE dans l'eau {a} -> {b} -> ECHEC")
        ok_ = False
        continue
    tn, tr = d / VIT_N, d / VIT_R
    BILAN[n_] = {"blocs": d, "ordinaire_s": round(tn), "rapide_s": round(tr)}
    bon = tn <= RES * 0.8 and tr >= 6.0
    ok_ &= bon
    print(f"trajet {n_} : {d} blocs sous l'eau | {tn:.0f} s en nage ordinaire, {tr:.0f} s en nage rapide | reserve {RES:.0f} s -> {'OK' if bon else 'ECHEC'}"
          f" (sans voile : {AIR:.0f} s d'air mais degats de pression des l'entree : infranchissable)")
(RACINE / "exports/aqua_verif.json").write_text(json.dumps({"_marque": "[claude-build]", "resultat": "OK" if ok_ else "ECHEC", "sans_voile": sans, "trajets": BILAN,
                                                            "reserve_s": RES, "air_s": AIR}, ensure_ascii=False), encoding="utf-8")
print("RESULTAT :", "OK" if ok_ else "ECHEC")
sys.exit(0 if ok_ else 1)
