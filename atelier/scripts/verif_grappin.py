# [claude-build]
"""Lecture seule : un donjon de grappin se traverse-t-il SANS grappin (il ne doit pas) et AVEC (il doit) ?
Sans grappin : marche, montee d'un bloc, chute, sauts genereux (4 blocs a plat, 1 bloc vers le haut, 12 vers le bas).
Avec grappin (mod grappin 1.1) : depuis une ile, on vise le bord d'une autre ile (face laterale pres du dessus) ou son dessus
si l'on est plus haut ; le crochet doit voir sa cible (30 blocs au plus), le corps doit passer, et le bond d'arrivee (~2 blocs)
doit suffire pour monter dessus. La fosse (y <= fosse_y dans l'emprise de la gorge) compte comme une chute : on n'y va pas.
Usage : verif_grappin.py <structure.nbt> <cibles.json>"""
import json
import sys
from collections import deque

import nbtlib
import numpy as np

f = nbtlib.load(sys.argv[1])
conf = json.load(open(sys.argv[2], encoding="utf-8"))
SX, SY, SZ = (int(v) for v in f["size"])
PASSE = {"air", "light", "moss_carpet", "fern", "short_grass", "flowering_azalea", "cave_vines", "cave_vines_plant", "hanging_roots", "water", "glow_lichen", "vine"}
pal = [str(p["Name"]).split(":")[1] for p in f["palette"]]
SOL = np.zeros((SX + 2, SY + 3, SZ + 2), dtype=bool)   # decalage de 1 : bord exterieur
EAU = np.zeros_like(SOL)
for b in f["blocks"]:
    x, y, z = (int(v) for v in b["pos"])
    n = pal[int(b["state"])]
    if n not in PASSE:
        SOL[x + 1, y + 1, z + 1] = True
    elif n == "water":
        EAU[x + 1, y + 1, z + 1] = True
SOL[:, 0, :] = True                                    # le sol du monde sous la structure
FY = conf["fosse_y"]


def debout(x, y, z):
    if not (0 <= x < SX and 1 <= y < SY - 1 and 0 <= z < SZ):
        return False
    return SOL[x + 1, y, z + 1] and not SOL[x + 1, y + 1, z + 1] and not SOL[x + 1, y + 2, z + 1] and not EAU[x + 1, y, z + 1]


def mort(x, y, z):
    return y <= FY and x >= 12 and 6 <= z <= 93


def marche(depart, sauts, bloque=frozenset()):
    vus, f_ = {depart}, deque([depart])
    while f_:
        x, y, z = f_.popleft()
        voisins = []
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            u, v = x + dx, z + dz
            if debout(u, y, v):
                voisins.append((u, y, v))
            elif debout(u, y + 1, v) and not SOL[x + 1, y + 2, z + 1]:
                voisins.append((u, y + 1, v))
            elif 0 <= u < SX and 0 <= v < SZ and not SOL[u + 1, y + 1, v + 1] and not SOL[u + 1, y + 2, v + 1]:
                yy = y - 1
                while yy > 0 and not debout(u, yy, v):
                    if SOL[u + 1, yy + 1, v + 1]:
                        break
                    yy -= 1
                if debout(u, yy, v):
                    voisins.append((u, yy, v))
        if sauts and not SOL[x + 1, y + 2, z + 1]:
            for dx in range(-4, 5):
                for dz in range(-4, 5):
                    if dx * dx + dz * dz > 16:
                        continue
                    for dy in range(-12, 2):
                        if debout(x + dx, y + dy, z + dz):
                            voisins.append((x + dx, y + dy, z + dz))
        for q in voisins:
            if q not in vus and not mort(*q) and (q[0], q[2]) not in bloque:
                vus.add(q)
                f_.append(q)
    return vus


ILES = {n: ({tuple(c) for c in d["cases"]}, d["yf"]) for n, d in conf["iles"].items()}
dep = tuple(conf["depart"])


def zone_de(cases_atteintes):
    z_ = set()
    for n, (cs, yf) in ILES.items():
        if any((x, y, z) in cases_atteintes for x, z in cs for y in (yf + 1,)):
            z_.add(n)
    return z_


# ---------------------------------------------------------------- 1. sans grappin
sans = marche(dep, True)
z_sans = zone_de(sans)
cs = conf["cibles"]["salle"]
print("SANS GRAPPIN : cases atteintes", len(sans), "| iles :", sorted(z_sans), "| salle :", tuple(cs) in sans)


# ---------------------------------------------------------------- 2. avec grappin
def faces(n):
    """Points d'accroche sur une ile : faces laterales des bords (pres du dessus) et dessus (au moins 2 cases du bord)."""
    cs_, yf = ILES[n]
    lat, dessus = [], []
    for x, z in cs_:
        if not debout(x, yf + 1, z):
            continue
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if not SOL[x + dx + 1, yf + 1, z + dz + 1] and not SOL[x + dx + 1, yf + 2, z + dz + 1]:
                lat.append(((x + .5 + .499 * dx, yf + .8, z + .5 + .499 * dz), (x, yf + 1, z), (x, yf, z)))
        if all((x + a, z + b) in cs_ for a in (-2, 0, 2) for b in (-2, 0, 2)):
            dessus.append(((x + .5, yf + 1.0, z + .5), (x, yf + 1, z), (x, yf, z)))
    return lat, dessus


def libre_rayon(p0, p1, cible_bloc):
    """Le crochet voit-il sa cible ? Echantillonnage tous les 0,2 bloc, le bloc vise excepte."""
    d = p1 - p0
    L = np.linalg.norm(d, axis=1)
    k = np.arange(0.0, 1.0, 0.2 / 30.0)
    pts = p0[:, None, :] + d[:, None, :] * k[None, :, None] * np.minimum(1.0, (L[:, None] - 0.05) / np.maximum(L[:, None], 1e-6))[:, :, None]
    c = np.floor(pts).astype(int)
    ok_c = (c[..., 0] >= 0) & (c[..., 0] < SX) & (c[..., 1] >= 0) & (c[..., 1] < SY) & (c[..., 2] >= 0) & (c[..., 2] < SZ)
    c = np.clip(c, [-1, -1, -1], [SX, SY, SZ])
    plein = SOL[c[..., 0] + 1, c[..., 1] + 1, c[..., 2] + 1] & ok_c
    vise = (c == cible_bloc[:, None, :]).all(axis=2)
    return ~(plein & ~vise).any(axis=1)


def libre_corps(p0, p1, masque, yfa):
    """Le corps (1,8 de haut) passe-t-il en ligne droite jusqu'a 1,6 bloc de la cible ? L'ile de depart ne gene pas :
    tire vers le bas, on glisse sur son dessus jusqu'au bord avant de descendre."""
    d = p1 - p0
    L = np.linalg.norm(d, axis=1)
    u = d / np.maximum(L, 1e-6)[:, None]
    k = np.arange(0.0, 1.0, 0.25 / 30.0)
    lmax = np.maximum(L - 1.6, 0.0)
    pts = p0[:, None, :] + u[:, None, :] * (k[None, :] * lmax[:, None])[:, :, None]
    bloque = np.zeros(len(p0), dtype=bool)
    for h in (-0.35, 0.4, 1.25):
        c = np.floor(pts + np.array([0, h, 0])).astype(int)
        c = np.clip(c, [-1, -1, -1], [SX, SY, SZ])
        plein = SOL[c[..., 0] + 1, c[..., 1] + 1, c[..., 2] + 1]
        propre = masque[np.clip(c[..., 0], 0, SX - 1), np.clip(c[..., 2], 0, SZ - 1)] & (c[..., 1] <= yfa)
        bloque |= (plein & ~propre).any(axis=1)
    return ~bloque


def grappin(a_n, b_n, atteint):
    """Existe-t-il un tir de l'ile a_n vers l'ile b_n ? Rend (depart, arrivee) ou None."""
    ca, yfa = ILES[a_n]
    srcs = [(x, yfa + 1, z) for x, z in ca if (x, yfa + 1, z) in atteint]
    if not srcs:
        return None
    lat, dessus = faces(b_n)
    cibles = lat + (dessus if yfa > ILES[b_n][1] + 1 else [])
    if not cibles:
        return None
    S = np.array([(x + .5, y + 1.62, z + .5) for x, y, z in srcs])
    C = np.array([c[0] for c in cibles])
    i, j = np.meshgrid(np.arange(len(S)), np.arange(len(C)), indexing="ij")
    i, j = i.ravel(), j.ravel()
    oeil, T = S[i], C[j]
    L = np.linalg.norm(T - oeil, axis=1)
    ok = L <= 29.5
    corps0 = oeil - np.array([0, 1.12, 0])           # pieds + 0,5
    dirv = (T - corps0) / np.maximum(np.linalg.norm(T - corps0, axis=1), 1e-6)[:, None]
    pieds_fin = (T - dirv * 1.6)[:, 1] - 0.5
    hauteur = np.array([c[1][1] for c in cibles])[j]
    ok &= (pieds_fin + 1.9 >= hauteur) & (pieds_fin <= hauteur + 4)
    idx = np.nonzero(ok)[0]
    if len(idx) == 0:
        return None
    idx = idx[np.argsort(L[idx])][:6000]
    blocs_vises = np.array([c[2] for c in cibles])[j[idx]]
    vu = libre_rayon(oeil[idx], T[idx], blocs_vises)
    idx = idx[vu]
    if len(idx) == 0:
        return None
    masque = np.zeros((SX, SZ), dtype=bool)
    for x, z in ca:
        masque[x, z] = True
    passe = libre_corps(corps0[idx], T[idx], masque, yfa)
    idx = idx[passe]
    if len(idx) == 0:
        return None
    k_ = idx[0]
    return srcs[i[k_]], cibles[j[k_]][1], float(L[k_])


atteint = marche(dep, False)
zones = zone_de(atteint)
tirs = []
change = True
while change:
    change = False
    for b_n in sorted(ILES):
        if b_n in zones:
            continue
        for a_n in sorted(zones):
            ca, cb = ILES[a_n][0], ILES[b_n][0]
            if min(abs(xa - xb) + abs(za - zb) for xa, za in list(ca)[::3] for xb, zb in list(cb)[::3]) > 45:
                continue
            r = grappin(a_n, b_n, atteint)
            if r:
                tirs.append((a_n, b_n, r[2]))
                atteint |= marche(r[1], False)
                zones = zone_de(atteint)
                change = True
                break
print("AVEC GRAPPIN : iles atteintes", len(zones), "sur", len(ILES), "| salle :", tuple(cs) in atteint)
for a_n, b_n, L in tirs:
    print(f"  tir {a_n:>6} -> {b_n:<6} {L:5.1f} blocs")
manque = sorted(set(ILES) - zones)
if manque:
    print("  iles jamais atteintes :", manque)
ok_sans = not (z_sans - {"depart"}) and tuple(cs) not in sans
ok_avec = not manque and tuple(cs) in atteint
print("RESULTAT :", "OK - impossible sans grappin, possible avec" if ok_sans and ok_avec else
      ("PROBLEME : on avance sans grappin" if not ok_sans else "PROBLEME : le grappin ne suffit pas"))

# ---------------------------------------------------------------- 3. tous les tirs possibles entre iles, et le plus court chemin
tout = set()
for n, (cs_, yf) in ILES.items():
    tout |= {(x, yf + 1, z) for x, z in cs_ if debout(x, yf + 1, z)}
graphe = {n: [] for n in ILES}
for a_n in sorted(ILES):
    for b_n in sorted(ILES):
        if a_n != b_n:
            r = grappin(a_n, b_n, tout)
            if r:
                graphe[a_n].append((b_n, round(r[2], 1)))
ordre = conf["ordre"]
fin = ordre[-2]                                        # derniere ile avant la salle
dist_, f_ = {"depart": 0}, deque(["depart"])
while f_:
    a_n = f_.popleft()
    for b_n, _ in graphe[a_n]:
        if b_n not in dist_:
            dist_[b_n] = dist_[a_n] + 1
            f_.append(b_n)
print("TIRS POSSIBLES :")
for a_n in ordre[:-1]:
    print(f"  {a_n:>6} -> " + ", ".join(f"{b} ({L})" for b, L in graphe[a_n]))
print("Moins de tirs pour arriver a", fin, ":", dist_.get(fin), "| iles sur le trace prevu :", len(ordre) - 1)
