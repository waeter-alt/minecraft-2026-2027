# [claude-build]
"""Reconstitue le monde final (hub + 4 arenes + portes ouvertes ou fermees) et verifie a pied :
du spawn du hub on doit entrer dans chaque arene, et on ne doit sortir nulle part par les portes fermees du Feu."""
import json
import re
from collections import deque
from pathlib import Path

import nbtlib

R = Path(__file__).resolve().parent.parent
import sys
MINI = "--mini" in sys.argv
if MINI:
    ZONES = {"hub": (-114, 78, -114), "mini_feu": (-52, 68, 114), "mini_air": (114, 68, -52), "mini_nature": (-218, 68, -52), "mini_glace": (-52, 68, -218)}
    CENTRES_L = ((0, 166, 52), (166, 0, 52), (-166, 0, 52), (0, -166, 52))
else:
    ZONES = {"hub": (-114, 78, -114), "forge_v2": (-126, 74, 114), "arene_air": (114, 74, -96), "arene_nature": (-366, 68, -126), "arene_glace": (-126, 68, -366)}
    CENTRES_L = ((0, 240, 126), (210, 0, 96), (-240, 0, 126), (0, -240, 126))
grid = {}
for n, (ox, oy, oz) in ZONES.items():
    f = nbtlib.load(R / "exports" / f"{n}.nbt")
    pal = [str(p["Name"]).split(":")[1] for p in f["palette"]]
    for b in f["blocks"]:
        x, y, z = (int(v) for v in b["pos"])
        grid[(ox + x, oy + y, oz + z)] = pal[int(b["state"])]
    print(n, len(f["blocks"]), "blocs")
for ligne in ([] if MINI else (R / "exports/hub_pose.mcfunction").read_text(encoding="utf-8").splitlines()):
    m = re.match(r"fill (-?\d+) (-?\d+) (-?\d+) (-?\d+) (-?\d+) (-?\d+) (\S+)(?: replace (\S+))?", ligne)
    if not m:
        continue
    a = [int(v) for v in m.groups()[:6]]
    bloc = m.group(7).split(":")[-1]
    filtre = m.group(8).split(":")[-1] if m.group(8) else None
    n = 0
    for x in range(min(a[0], a[3]), max(a[0], a[3]) + 1):
        for y in range(min(a[1], a[4]), max(a[1], a[4]) + 1):
            for z in range(min(a[2], a[5]), max(a[2], a[5]) + 1):
                cur = grid.get((x, y, z), "air")
                if filtre is None or cur == filtre:
                    grid[(x, y, z)] = bloc
                    n += 1
    print(" ", ligne[:60], "->", n, "blocs changes")

SOL = 86
PLANTES = {"short_grass", "fern", "tall_grass", "large_fern", "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "oxeye_daisy", "cornflower",
           "lily_of_the_valley", "rose_bush", "peony", "lilac", "red_mushroom", "brown_mushroom", "moss_carpet", "glow_lichen", "hanging_roots", "spore_blossom",
           "small_dripleaf", "snow", "pink_petals", "vine", "light", "powder_snow", "lily_pad", "oak_wall_sign", "water", "fire", "soul_fire"}
PASS = {"air"} | PLANTES
NOSUP = {"air", "lantern", "soul_lantern", "chain", "lava", "fire", "soul_fire", "white_stained_glass_pane", "iron_bars", "light", "oak_fence", "spruce_fence", "blackstone_wall"} | (PLANTES - {"lily_pad"})


def n(p):
    return grid.get(p, "air" if p[1] > SOL else "dirt")


def ps(p):
    return n(p) in PASS


def deb(p):
    return ps(p) and ps((p[0], p[1] + 1, p[2]))


def sup(p):
    return n((p[0], p[1] - 1, p[2])) not in NOSUP


depart = (0, 87, 20)
assert deb(depart) and sup(depart), (n(depart), n((0, 86, 20)))
vus = {depart}
fi = deque([depart])
parent = {}


def hors(p):
    arene = any((p[0] - cx) ** 2 + (p[2] - cz) ** 2 < r ** 2 for cx, cz, r in CENTRES_L)
    return not arene and p[0] ** 2 + p[2] ** 2 >= 52 ** 2 and min(abs(p[0]), abs(p[2])) > 15


fuite = None
while fi:
    x, y, z = fi.popleft()
    if hors((x, y, z)) and fuite is None:
        fuite = (x, y, z)
        print("premiere fuite :", fuite)
        c = fuite
        for _ in range(10):
            print("   ", c, n(c), "sous", n((c[0], c[1] - 1, c[2])))
            c = parent.get(c)
            if c is None:
                break
        break
    if len(vus) > 1500000:
        break
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        q = (x + dx, y, z + dz)
        v = None
        if deb(q) and sup(q):
            v = q
        elif deb(q):
            k = y
            while k > 60 and deb((q[0], k, q[2])) and not sup((q[0], k, q[2])):
                k -= 1
            if deb((q[0], k, q[2])) and y - k <= 5:
                v = (q[0], k, q[2])
        else:
            q2 = (x + dx, y + 1, z + dz)
            if deb(q2) and ps((x, y + 2, z)) and sup(q2):
                v = q2
        if v and v not in vus:
            vus.add(v)
            parent[v] = (x, y, z)
            fi.append(v)
print("cases atteintes a pied :", len(vus))
CENTRES = dict(zip(("Feu", "Air", "Nature", "Glace"), CENTRES_L))
ok = True
for k, (cx, cz, r) in CENTRES.items():
    dedans = [p for p in vus if (p[0] - cx) ** 2 + (p[2] - cz) ** 2 < (r - 8) ** 2]
    print(f"  {k:<7} cases atteintes dans l'arene : {len(dedans)}")
    ok &= len(dedans) > 1500
dehors = [p for p in vus if hors(p)]
print("cases atteintes hors du hub, des avenues et des arenes :", len(dehors), dehors[:5])
ok &= not dehors
print("RESULTAT :", "le hub mene a chaque arene, sans sortie" if ok else "A CORRIGER")
