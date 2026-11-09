# [claude-build]
"""Relit l'arene des Portes et decode chaque commande de portail : ou elle detecte, ou elle envoie, et si la paire est bien aller-retour.
Usage : verif_portes.py <structure.nbt>
Le passage reel d'un joueur se constate en jeu."""
import math
import re
import sys
from collections import Counter

import nbtlib

f = nbtlib.load(sys.argv[1])
pal = [str(p["Name"]).split(":")[1] for p in f["palette"]]
G, D, T = {}, {}, {}
for b in f["blocks"]:
    p = tuple(int(v) for v in b["pos"])
    G[p] = pal[int(b["state"])]
    if "nbt" in b:
        if "Command" in b["nbt"]:
            D[p] = str(b["nbt"]["Command"])
        elif "front_text" in b["nbt"]:
            T[p] = [str(m).strip('"') for m in b["nbt"]["front_text"]["messages"]]
err = []
rep = [p for p, n in G.items() if n == "repeating_command_block"]
assert len(rep) == 1
p, chaine = rep[0], []
while p in D:
    chaine.append(p)
    p = (p[0] + 1, p[1], p[2])
print("chaine", len(chaine), "commandes sur", len(D), "de", chaine[0], "a", chaine[-1])
if len(chaine) != len(D):
    err.append("chaine coupee")
pt = []
for p in chaine:
    m = re.match(r"execute positioned ~(-?\d+) ~(-?\d+) ~(-?\d+) align xyz as @a\[dx=(\d),dy=2,dz=(\d),gamemode=!spectator\] positioned ~(-?[\d.]+) ~(-?\d+) ~(-?[\d.]+) run tp @s ~ ~ ~ (-?\d+) 0", D[p])
    if not m:
        continue
    g = m.groups()
    o = (p[0] + int(g[0]), p[1] + int(g[1]), p[2] + int(g[2]))
    arr = (o[0] + float(g[5]), o[1] + int(g[6]), o[2] + float(g[7]))
    pt.append({"o": o, "dx": int(g[3]), "dz": int(g[4]), "arr": arr, "yaw": int(g[8])})
print("portails decodes", len(pt))
if len(pt) != 16:
    err.append("il faut 16 portails")


def couleur(q):
    x, y, z = q["o"]
    return G.get((x, y - 1, z), "?")


for q in pt:
    x, y, z = q["o"]
    for i in range(q["dx"] + 1):
        for j in range(q["dz"] + 1):
            for k in range(3):
                if G.get((x + i, y + k, z + j)) != "air":
                    err.append(f"ouverture bouchee {(x + i, y + k, z + j)} {G.get((x + i, y + k, z + j))}")
    a = q["arr"]
    c = (math.floor(a[0]), a[1], math.floor(a[2]))
    for k in (0, 1):
        if G.get((c[0], c[1] + k, c[2]), "air") not in ("air",):
            err.append(f"arrivee bouchee {c} {G.get((c[0], c[1] + k, c[2]))}")
    if G.get((c[0], c[1] - 1, c[2]), "air") == "air" and c[1] - 1 > 18:
        err.append(f"arrivee sans sol {c}")
    for r in pt:                                       # on ne doit jamais arriver dans une zone de depart
        ox, oy, oz = r["o"]
        if ox - .5 <= a[0] <= ox + r["dx"] + 1.5 and oz - .5 <= a[2] <= oz + r["dz"] + 1.5 and oy - 1 <= a[1] <= oy + 2:
            err.append(f"arrivee sur un portail {a} -> {r['o']}")
paires = Counter(couleur(q) for q in pt)
print("couleurs", dict(paires))
if set(paires.values()) != {2} or len(paires) != 8:
    err.append("couleurs mal appariees")
for q in pt:
    autre = [r for r in pt if couleur(r) == couleur(q) and r is not q]
    if autre and math.dist(q["arr"], autre[0]["o"]) > 5.5:
        err.append(f"{couleur(q)} n'arrive pas devant l'autre portail : {q['arr']} / {autre[0]['o']}")
print("panneaux", len(T))
if T:
    err.append("il reste des panneaux")
print("ERREURS", len(err), err[:6])
print("RESULTAT :", "8 paires aller-retour" if not err else "A CORRIGER")
sys.exit(1 if err else 0)
