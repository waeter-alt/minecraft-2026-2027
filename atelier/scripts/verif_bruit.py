# [claude-build]
"""Relit l'arene des Tenebres : couverture des capteurs, chemin muet de la porte au centre, lumiere au sol.
Usage : verif_bruit.py <structure.nbt> [sol local, 18 par defaut]
Le declenchement reel des capteurs se constate en jeu."""
import math
import sys
from collections import Counter, deque

import nbtlib

f = nbtlib.load(sys.argv[1])
SOL = int(sys.argv[2]) if len(sys.argv) > 2 else 18
pal = [str(p["Name"]).split(":")[1] for p in f["palette"]]
G, D = {}, {}
for b in f["blocks"]:
    p = tuple(int(v) for v in b["pos"])
    G[p] = pal[int(b["state"])]
    if "nbt" in b:
        D[p] = str(b["nbt"]["Command"])
err = []
capteurs = [p for p, n in G.items() if n == "sculk_sensor"]
hurleurs = [p for p, n in G.items() if n == "sculk_shrieker"]
print("capteurs", len(capteurs), "hurleurs", len(hurleurs), "blocs de commande", len(D), Counter(n for n in G.values() if "command_block" in n))
branches = 0
for x, y, z in capteurs:
    if G.get((x, y - 1, z)) == "command_block":
        branches += 1
        c = [D.get((x, y - 1 - k, z), "") for k in range(4)]
        if not ("glowing" in c[0] and "slowness" in c[1] and "darkness" in c[2] and "playsound" in c[3]):
            err.append(f"chaine incomplete {(x, y, z)}")
        if [G.get((x, y - 1 - k, z)) for k in range(1, 4)] != ["chain_command_block"] * 3:
            err.append(f"chaine coupee {(x, y, z)}")
print("capteurs relies aux commandes", branches, "; capteurs du sanctuaire", len(capteurs) - branches)
for h in hurleurs:
    if not any(math.dist(h, c) <= 8 for c in capteurs):
        err.append(f"hurleur sans capteur proche {h}")

# sol de la cour : part couverte par un capteur a moins de 8 blocs
sol = [(x, z) for x in range(104) for z in range(104) if 16 <= math.hypot(x + .5 - 52, z + .5 - 52) < 47.5 and G.get((x, SOL + 1, z), "air") == "air" and G.get((x, SOL + 2, z), "air") == "air" and G.get((x, SOL, z)) in ("deepslate_tiles", "sculk")]
cap = sorted(capteurs)
couv = sum(1 for x, z in sol if any((x - a) ** 2 + (SOL + 1 - b) ** 2 + (z - c) ** 2 <= 64 for a, b, c in cap))
pc = 100 * couv / max(1, len(sol))
print(f"sol nu couvert : {couv}/{len(sol)} = {pc:.1f} %")
if pc < 95:
    err.append("couverture insuffisante")

# chemin muet : seulement des tapis, depuis la sortie du couloir jusqu'a l'entree du sanctuaire
tapis = {(x, z) for (x, y, z), n in G.items() if n == "black_carpet" and y == SOL + 1}
print("tapis au sol", len(tapis))
vus, file = set(), deque()
dep = sorted(tapis, key=lambda p: math.hypot(p[0] - 52, p[1] - 17))[0]
vus.add(dep)
file.append(dep)
while file:
    x, z = file.popleft()
    for q in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1), (x + 1, z + 1), (x - 1, z - 1), (x + 1, z - 1), (x - 1, z + 1)):
        if q in tapis and q not in vus:
            vus.add(q)
            file.append(q)
print("tapis relies depuis la porte", len(vus), "/", len(tapis), "depart", dep)
if len(vus) < .9 * len(tapis):
    err.append("reseau de tapis coupe")

# lumiere : calcul simple depuis les lanternes et bougies
src = {p: (10 if n == "soul_lantern" else 3) for p, n in G.items() if n in ("soul_lantern", "black_candle")}
opaque = {"deepslate_bricks", "deepslate_tiles", "polished_deepslate", "chiseled_deepslate", "cobbled_deepslate", "sculk", "reinforced_deepslate"}
L = dict(src)
file = deque(src)
while file:
    p = file.popleft()
    for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
        q = (p[0] + d[0], p[1] + d[1], p[2] + d[2])
        if G.get(q, "air" if q[1] > SOL else "x") in opaque or q[1] <= SOL or not (0 <= q[0] < 104 and 0 <= q[2] < 104):
            continue
        if L.get(q, 0) < L[p] - 1:
            L[q] = L[p] - 1
            file.append(q)
noir = sum(1 for x, z in sol if L.get((x, SOL + 1, z), 0) == 0)
print(f"sol sans aucune lumiere : {100 * noir / max(1, len(sol)):.1f} %, lumiere moyenne {sum(L.get((x, SOL + 1, z), 0) for x, z in sol) / max(1, len(sol)):.2f}")
print("ERREURS", len(err), err[:6])
print("RESULTAT :", "bruit et silence en place" if not err else "A CORRIGER")
sys.exit(1 if err else 0)
