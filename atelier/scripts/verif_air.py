# [claude-build]
"""Relit une arene sous-marine et mesure, pour chaque case d'eau, le trajet a la nage jusqu'a l'air le plus proche.
Air : une colonne de bulles, ou le dessous d'une salle d'air. Sans air, un joueur tient 15 secondes.
Usage : verif_air.py <structure.nbt> [distance maximale, 30 par defaut]"""
import sys
from collections import Counter, deque

import nbtlib

f = nbtlib.load(sys.argv[1])
LIM = int(sys.argv[2]) if len(sys.argv) > 2 else 30
pal = [str(p["Name"]).split(":")[1] for p in f["palette"]]
G = {tuple(int(v) for v in b["pos"]): pal[int(b["state"])] for b in f["blocks"]}
EAU = {"water", "kelp", "kelp_plant", "seagrass", "light", "sea_pickle", "bubble_column"}
cases = {p for p, n in G.items() if n in EAU}
N6 = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
bulles = {p for p in cases if G[p] == "bubble_column"}
trous = {p for p in cases if G.get((p[0], p[1] + 1, p[2])) == "air"}
print("cases d'eau", len(cases), "bulles", len(bulles), "dessous de salles d'air", len(trous))
D = {p: 0 for p in bulles | trous}
file = deque(D)
while file:
    p = file.popleft()
    for d in N6:
        q = (p[0] + d[0], p[1] + d[1], p[2] + d[2])
        if q in cases and q not in D:
            D[q] = D[p] + 1
            file.append(q)
sans = cases - set(D)
top = max(D.values())
loin = [p for p, v in D.items() if v > LIM]
print("sans chemin vers l'air", len(sans), list(sans)[:4])
print("trajet le plus long", top, "blocs, en", [p for p, v in D.items() if v == top][:3])
print("repartition par tranches de 5", sorted(Counter(v // 5 * 5 for v in D.values()).items()))
print(f"au-dela de {LIM} blocs :", len(loin), loin[:5])
print("RESULTAT :", "air partout a portee" if not sans and not loin else "ZONES SANS AIR")
sys.exit(1 if sans or loin else 0)
