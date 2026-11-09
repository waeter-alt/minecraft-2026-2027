# [claude-build]
"""
verif_donjon2.py - Controle du donjon refait (donjon2) : ce qu'on atteint, etape par etape, portes fermees.
Deplacements : marche, marches de 1, chutes, sauts jusqu'a ~3,6 blocs, echelles, lianes, nage. Grappin : un ancrage est atteint si on le voit
a moins de 30 blocs ; depuis l'ancrage on atteint les cases visibles a moins de R blocs (sous l'ancrage), et un autre ancrage a moins de R.
Deux reglages : realiste (R = 14, le parcours doit passer) et genereux (R = 38, rien ne doit passer une porte fermee).
  .venv\\Scripts\\python.exe sources\\verif_donjon2.py
"""
import json
import sys
from collections import deque
from pathlib import Path

import nbtlib
import numpy as np

RACINE = Path(__file__).resolve().parent.parent
conf = json.loads((RACINE / "exports/donjon2_cibles.json").read_text(encoding="utf-8"))
f = nbtlib.load(RACINE / "exports/donjon2.nbt")
SX, SY, SZ = (int(v) for v in f["size"])
pal = [str(p["Name"]).split(":")[1] for p in f["palette"]]
NOMS = np.full((SX, SY, SZ), -1, dtype=np.int32)
for b in f["blocks"]:
    x, y, z = (int(v) for v in b["pos"])
    NOMS[x, y, z] = int(b["state"])
PASSE = {"air", "light", "water", "cave_vines", "cave_vines_plant", "hanging_roots", "vine", "moss_carpet", "fern", "cobweb", "candle", "lever",
         "stone_button", "stone_pressure_plate", "ladder", "brown_carpet", "white_carpet", "skeleton_skull", "chain", "spruce_trapdoor"}
GRIMPE = {"ladder", "vine", "cave_vines", "cave_vines_plant", "water"}
pas_i = np.array([n in PASSE for n in pal])
grimpe_i = np.array([n in GRIMPE for n in pal])
eau_i = np.array([n == "water" for n in pal])
ANC = {(int(a[1]), int(a[2]), int(a[3])): a[0] for a in conf["ancrages"]}


def grilles(ouvertes):
    passe = np.where(NOMS < 0, True, pas_i[np.maximum(NOMS, 0)])
    grimpe = np.where(NOMS < 0, False, grimpe_i[np.maximum(NOMS, 0)])
    for n in ouvertes:
        for c in conf["portes"][n]["cases"]:
            passe[tuple(c)] = True
    return passe, grimpe


def noeuds(passe, grimpe):
    tete = np.zeros_like(passe)
    tete[:, :-1] = passe[:, 1:]
    dessous = np.zeros_like(passe)                  # porte par ce qui est dessous
    dessous[:, 1:] = ~passe[:, :-1] | grimpe[:, :-1]
    return passe & tete & (dessous | grimpe)


def vue(passe, p, cibles):
    """Pour chaque cible (n x 3, centres), la ligne depuis p ne traverse aucun bloc plein (sauf les cases de depart et d'arrivee)."""
    if len(cibles) == 0:
        return np.zeros(0, dtype=bool)
    s = np.linspace(0.04, 0.96, 72)[None, :, None]
    pts = p[None, None, :] + (cibles[:, None, :] - p[None, None, :]) * s
    c = np.floor(pts).astype(int)
    c[..., 0] = np.clip(c[..., 0], 0, SX - 1)
    c[..., 1] = np.clip(c[..., 1], 0, SY - 1)
    c[..., 2] = np.clip(c[..., 2], 0, SZ - 1)
    ok = passe[c[..., 0], c[..., 1], c[..., 2]]
    dep = np.floor(p).astype(int)
    fin = np.floor(cibles).astype(int)[:, None, :]
    ok |= (c == dep).all(-1) | (c == fin).all(-1)
    return ok.all(1)


def parcours(ouvertes, grappin=True, R=14.0):
    passe, grimpe = grilles(ouvertes)
    for a in ANC:                                   # un ancrage est un bloc plein
        passe[a] = False
    N = noeuds(passe, grimpe)
    pts = np.argwhere(N)
    centres = pts + np.array([.5, .9, .5])
    yeux = pts + np.array([.5, 1.6, .5])
    vus, portees = {}, {}
    if grappin:
        for a in ANC:
            ca = np.array(a) + .5
            d = np.linalg.norm(yeux - ca, axis=1)
            m = np.nonzero(d <= 30)[0]
            vus[a] = set(map(tuple, pts[m[vue(passe, ca, yeux[m])]]))
            d2 = np.linalg.norm(centres - ca, axis=1)
            m = np.nonzero((d2 <= R) & (pts[:, 1] <= a[1] + (2 if R > 20 else -1)))[0]
            portees[a] = [tuple(v) for v in pts[m[vue(passe, ca, centres[m])]]]
    sautables = [(dx, dz) for dx in range(-3, 4) for dz in range(-3, 4) if 4 <= dx * dx + dz * dz <= 13]
    vu = set()
    file = deque()

    def ajoute(c):
        if c not in vu:
            vu.add(c)
            file.append(c)

    def chute(x, y, z):
        while y > 0 and not N[x, y, z]:
            if not passe[x, y, z]:
                return None
            y -= 1
        return (x, y, z) if N[x, y, z] else None

    ajoute(tuple(conf["entree"]))
    anc_faits = set()
    while True:
        while file:
            x, y, z = file.popleft()
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, nz = x + dx, z + dz
                if not (0 <= nx < SX and 0 <= nz < SZ):
                    continue
                if N[nx, y, nz]:
                    ajoute((nx, y, nz))
                elif passe[nx, y, nz] and y + 1 < SY and passe[nx, y + 1, nz]:
                    c = chute(nx, y, nz)
                    if c:
                        ajoute(c)
                if y + 2 < SY and N[nx, y + 1, nz] and passe[x, y + 2, z]:
                    ajoute((nx, y + 1, nz))
            if grimpe[x, y, z] or (y > 0 and grimpe[x, y - 1, z]):
                if y + 2 < SY and passe[x, y + 1, z] and passe[x, y + 2, z]:
                    ajoute((x, y + 1, z))
                if y > 0 and N[x, y - 1, z]:
                    ajoute((x, y - 1, z))
            if y > 0 and not passe[x, y - 1, z] and y + 3 < SY and passe[x, y + 2, z]:
                bord = any(0 <= x + a < SX and 0 <= z + b < SZ and passe[x + a, y, z + b] and not N[x + a, y, z + b]
                           for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                if bord:
                    for dx, dz in sautables:
                        nx, nz = x + dx, z + dz
                        if not (0 <= nx < SX and 0 <= nz < SZ):
                            continue
                        for dy in range(1, -5, -1):
                            ty = y + dy
                            if not (0 < ty < SY - 3) or not N[nx, ty, nz]:
                                continue
                            yt = max(y, ty)
                            libre = all(passe[int(round(x + dx * t)), yt + 1, int(round(z + dz * t))] and passe[int(round(x + dx * t)), yt + 2, int(round(z + dz * t))]
                                        for t in (.34, .67))
                            if libre:
                                ajoute((nx, ty, nz))
                            break
        nouveaux = []
        for a in ANC:
            if a in anc_faits or not grappin:
                continue
            if vus[a] & vu or any(b in anc_faits and np.linalg.norm(np.subtract(a, b)) <= R and vue(passe, np.array(b) + np.array([.5, -2.0, .5]), np.array([np.array(a) + .5]))[0]
                                  for b in anc_faits):
                nouveaux.append(a)
        if not nouveaux:
            break
        for a in nouveaux:
            anc_faits.add(a)
            for c in portees[a]:
                ajoute(c)
    return vu, anc_faits


def atteint(vu, cible, r=4.0):
    cx, cy, cz = cible
    return any((x - cx) ** 2 + (y - cy) ** 2 + (z - cz) ** 2 <= r * r for (x, y, z) in vu if abs(x - cx) <= r and abs(z - cz) <= r)


CIB = conf["cibles"]


def rayon(c):
    return 2.5 if c[0] in "SH" and c[1:].isdigit() else 4.0


ETAPES = [
    ([], ["S1", "S2", "L1", "L2", "L3", "H1", "H2"]),
    (["G1"], ["S3", "S4", "BG2", "H3", "H4"]),
    (["G1", "G2"], ["S5", "S7", "socle_oeil", "socle_racine", "socle_feuille", "socle_goutte", "levier_H5", "serrure", "H7"]),
    (["G1", "G2", "G3", "H5"], ["S6", "plaque_cle", "bouton_H6", "H5"]),
    (["G1", "G2", "G3", "H5", "G4", "H6"], ["S8", "S9", "S10", "H6", "H8"]),
]
ok = True
deja = []
for k, (ouv, nouvelles) in enumerate(ETAPES):
    deja = deja + nouvelles
    plus_tard = [c for c in CIB if c not in deja]
    for mode, R in (("realiste", 14.0), ("genereux", 38.0)):
        vu, anc = parcours(ouv, True, R)
        manque = [c for c in deja if not atteint(vu, CIB[c], rayon(c))]
        trop = [c for c in plus_tard if atteint(vu, CIB[c], rayon(c))]
        bon = (not manque if mode == "realiste" else True) and not trop
        ok &= bon
        print(f"etape {k} portes ouvertes {ouv or '-'} [{mode}] : {len(vu)} cases, ancrages {sorted(ANC[a] for a in anc)}"
              f" | manque {manque if mode == 'realiste' else '(non compte)'} | trop tot {trop} -> {'OK' if bon else 'ECHEC'}", flush=True)
vu, anc = parcours([], False)
sans = [c for c in CIB if atteint(vu, CIB[c], rayon(c))]
print("sans grappin, portes fermees :", sans, "->", "OK" if set(sans) <= {"S1", "H1"} else "ECHEC")
ok &= set(sans) <= {"S1", "H1"}
for nom_ in ("chaine_s3", "chaine_s9"):
    ch = conf[nom_]
    print(nom_, "ecarts :", [round(float(np.linalg.norm(np.subtract(ch[i + 1], ch[i]))), 1) for i in range(len(ch) - 1)])
print("RESULTAT :", "OK" if ok else "ECHEC")
sys.exit(0 if ok else 1)
