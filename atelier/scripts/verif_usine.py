# [claude-build]
"""Relit exports/piege.nbt et controle l'usine : chaine de commandes, tapis, moteurs, engrenages, roue, colle.
Usage : verif_usine.py [fichier.nbt]   (sans argument : exports/piege.nbt)
Ne remplace pas l'essai en jeu : la rotation et la redstone se constatent dans le monde."""
import re
import sys
from collections import Counter
from pathlib import Path

import nbtlib

RACINE = Path(__file__).resolve().parent.parent
f = nbtlib.load(sys.argv[1] if len(sys.argv) > 1 else RACINE / "exports/piege.nbt")
pal = [(str(p["Name"]), {str(k): str(v) for k, v in p.get("Properties", {}).items()}) for p in f["palette"]]
G, D = {}, {}
for b in f["blocks"]:
    p = tuple(int(v) for v in b["pos"])
    G[p] = pal[int(b["state"])]
    if "nbt" in b:
        D[p] = b["nbt"]
err = []
ORI = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def add(p, d):
    return tuple(a + b for a, b in zip(p, d))


# chaine : un seul bloc repetitif, puis des blocs en chaine contigus
rep = [p for p, s in G.items() if s[0].endswith("repeating_command_block")]
if len(rep) != 1:
    err.append(f"blocs repetitifs : {len(rep)}")
p, chaine = rep[0], []
while p in G and "command_block" in G[p][0] and G[p][1].get("facing") == "east":
    chaine.append(p)
    p = add(p, ORI["east"])
print("chaine :", len(chaine), "commandes de", chaine[0], "a", chaine[-1])
zones = Counter()
for p in chaine:
    c = str(D[p]["Command"])
    if int(D[p]["auto"]) != 1:
        err.append(f"chaine sans auto {p}")
    m = re.search(r"fill ~(-?\d+) ~(-?\d+) ~(-?\d+) ~(-?\d+) ~(-?\d+) ~(-?\d+) (\S+)", c)
    if m:
        a = [int(v) for v in m.groups()[:6]]
        lo, hi = add(p, a[:3]), add(p, a[3:])
        zones[(lo[0], hi[0], lo[2], hi[2])] += 1
        if m.group(7) == "create:brass_casing":
            for x in range(lo[0], hi[0] + 1):
                for z in range(lo[2], hi[2] + 1):
                    if G.get((x, lo[1] - (lo[1] - 18), z), ("",))[0] != "create:belt":
                        err.append(f"marteau hors du tapis {(x, z)}")
    m = re.search(r"positioned ~(-?\d+) ~(-?\d+) ~(-?\d+) align xyz", c)
    if m:
        q = add(p, [int(v) for v in m.groups()])
        if G.get((q[0], 18, q[2]), ("",))[0] != "create:belt" or q[1] != 19:
            err.append(f"zone de frappe hors du tapis {q}")
    m = re.search(r"summon minecraft:item ~(-?\d+) ~(-?\d+) ~(-?\d+)", c)
    if m:
        q = add(p, [int(v) for v in m.groups()])
        if G.get((q[0], q[1] - 1, q[2]), ("",))[0] != "create:belt":
            err.append(f"lingot hors du tapis {q}")
print("marteaux : zones", len(zones), "commandes de fill par zone", sorted(set(zones.values())))

# tapis : un debut, une fin, et les parties qui se suivent
vus = 0
for p, s in G.items():
    if s[0] == "create:belt" and s[1]["part"] == "start":
        d, q, n = ORI[s[1]["facing"]], p, 1
        while G.get(q, ("",))[0] == "create:belt" and G[q][1]["part"] != "end":
            q = add(q, d)
            n += 1
        if G.get(q, ("",))[0] != "create:belt":
            err.append(f"tapis coupe {p}")
        vus += 1
print("tapis :", vus, "chaines, blocs", sum(1 for s in G.values() if s[0] == "create:belt"))

# chaque moteur doit toucher une piece qui recoit la rotation sur le bon axe
AXE = {"north": "z", "south": "z", "east": "x", "west": "x", "up": "y", "down": "y"}
sans = []
for p, s in G.items():
    if s[0] != "create:creative_motor":
        continue
    if p not in D or "ScrollValue" not in D[p]:
        err.append(f"moteur sans vitesse {p}")
    q = add(p, ORI[s[1]["facing"]])
    c = G.get(q, ("air", {}))
    n = c[0].split(":")[1]
    a = AXE[s[1]["facing"]]
    ok = (n in ("shaft", "cogwheel", "large_cogwheel") and c[1].get("axis") == a) or (n == "belt" and c[1]["part"] != "middle" and AXE[c[1]["facing"]] != a and a != "y") \
        or (n == "mechanical_press" and AXE[c[1]["facing"]] == a) or n == "mechanical_bearing"
    if not ok:
        sans.append((p, s[1]["facing"], n))
print("moteurs :", sum(1 for s in G.values() if s[0] == "create:creative_motor"), "sans liaison :", sans[:6])
err += [f"moteur sans liaison {m}" for m in sans]

# presses : une case vide puis un tapis, deux blocs plus bas
for p, s in G.items():
    if s[0] == "create:mechanical_press" and G.get(add(p, (0, -2, 0)), ("",))[0] == "create:belt":
        if G.get(add(p, (0, -1, 0)), ("minecraft:air",))[0] not in ("minecraft:air", "minecraft:light"):
            err.append(f"presse bouchee {p}")
print("presses sur tapis :", sum(1 for p, s in G.items() if s[0] == "create:mechanical_press" and G.get(add(p, (0, -2, 0)), ("",))[0] == "create:belt"))

# roue : le roulement regarde vers elle, la colle la couvre
b = [p for p, s in G.items() if s[0] == "create:mechanical_bearing"]
if len(b) != 1 or G.get(add(b[0], ORI[G[b[0]][1]["facing"]]), ("",))[0] == "":
    err.append("roulement sans roue")
e = list(f["entities"])
print("roulement :", b, "entites :", [str(x["nbt"]["id"]) for x in e])
if b:
    y = add(b[0], ORI[G[b[0]][1]["facing"]])[1]
    roue = [p for p, s in G.items() if p[1] == y and s[0] in ("create:brass_casing", "create:copper_casing") and abs(p[0] - b[0][0]) < 13 and abs(p[2] - b[0][2]) < 13]
    print("roue :", len(roue), "blocs, rayon max", max(max(abs(p[0] - b[0][0]), abs(p[2] - b[0][2])) for p in roue))
print("ERREURS", len(err), err[:8])
sys.exit(1 if err else 0)
