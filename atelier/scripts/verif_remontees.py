# [claude-build] Teste chaque remontee seule (les autres bloquees) et dessine une vue 3D de l'arene.
import json
import math
import re
import sys
from collections import deque

import nbtlib
from PIL import Image, ImageDraw

f = nbtlib.load("exports/arene_air.nbt")
conf = json.load(open("exports/arene_air_cibles.json", encoding="utf-8"))
pal = [str(p["Name"]).split(":")[1] for p in f["palette"]]
SX, SY, SZ = [int(v) for v in f["size"]]
SOL, HA = conf["sol"], conf["plateau"]
base = {}
for e in f["blocks"]:
    base[tuple(int(v) for v in e["pos"])] = pal[int(e["state"])]

src = open("sources/verif_acces.py", encoding="utf-8").read()
LIANES = {"twisting_vines", "twisting_vines_plant"}
PASS = {"air", "ladder", "birch_trapdoor", "birch_door", "bubble_column", "light", "powder_snow", "white_wall_banner", "light_blue_wall_banner"} | LIANES
NOSUP = {"air", "ladder", "bubble_column", "lantern", "birch_door", "lightning_rod", "light", "chain", "white_wall_banner", "light_blue_wall_banner"} | LIANES


def bfs(grid, start):
    def n(p):
        return grid.get(p, "air" if p[1] > SOL else "calcite")

    def ps(p):
        return n(p) in PASS

    def deb(p):
        return ps(p) and ps((p[0], p[1] + 1, p[2]))

    def sup(p):
        return n((p[0], p[1] - 1, p[2])) not in NOSUP or n(p) in ("ladder", "bubble_column") or n(p) in LIANES

    vus = {start}
    fi = deque([start])
    while fi:
        x, y, z = p = fi.popleft()
        v = []
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y, z + dz)
            if deb(q) and sup(q):
                v.append(q)
            elif deb(q):
                k = y
                while k > 0 and deb((q[0], k, q[2])) and not sup((q[0], k, q[2])):
                    k -= 1
                if deb((q[0], k, q[2])):
                    v.append((q[0], k, q[2]))
            else:
                q2 = (x + dx, y + 1, z + dz)
                if deb(q2) and ps((x, y + 2, z)) and sup(q2):
                    v.append(q2)
        if n(p) == "bubble_column" and n((x, y + 1, z)) == "bubble_column":
            v.append((x, y + 1, z))
        if n(p) == "ladder" or n((x, y - 1, z)) == "ladder" or n(p) in LIANES:
            for dy in (1, -1):
                q = (x, y + dy, z)
                if deb(q) or (ps(q) and dy == -1):
                    v.append(q)
        for q in v:
            if q not in vus and 0 <= q[1] < SY + 3:
                vus.add(q)
                fi.append(q)
    return vus


noms = list(conf["cibles"])
routes = sorted({int(re.search(r"\d+", k).group()) for k in noms if k.startswith("remontée")})
bas = {i: tuple(conf["cibles"][f"remontée {i} bas"]) for i in routes}
haut = {i: tuple(conf["cibles"][f"remontée {i} haut"]) for i in routes}
ok = True
for i in routes:
    g = dict(base)
    for j in routes:
        if j == i:
            continue
        bx, _, bz = bas[j]
        for x in range(bx - 1, bx + 2):                # on mure le pied de chaque autre remontee
            for z in range(bz - 1, bz + 2):
                for y in range(SOL + 1, SOL + 5):
                    g[(x, y, z)] = "stone"
        hx, _, hz = haut[j]
    # les autres moyens sont retires completement
    for p, b in base.items():
        if p[1] >= HA - 1:
            continue
        d = min(math.hypot(p[0] - bas[i][0], p[2] - bas[i][2]), 99)
        if d > 9 and (b in ("ladder", "bubble_column") or b in LIANES or b == "quartz_stairs" or b == "smooth_quartz_slab"):
            g[p] = "stone" if b != "quartz_stairs" else "air"
    v = bfs(g, bas[i])
    r = haut[i] in v
    ok &= r
    print(f"  remontee {i:>2} {bas[i]} -> {haut[i]} : {'OK' if r else 'ECHEC'}")
print("toutes les remontees marchent seules :", ok)

# ---- vue 3D isometrique
COUL = {"calcite": (222, 222, 216), "smooth_quartz": (238, 234, 228), "quartz_bricks": (230, 226, 218), "quartz_block": (235, 231, 224),
        "quartz_pillar": (236, 232, 226), "chiseled_quartz_block": (226, 222, 212), "white_wool": (252, 252, 252), "snow_block": (242, 250, 253),
        "white_concrete": (214, 218, 220), "light_gray_concrete": (140, 142, 138), "smooth_stone": (165, 165, 165), "diorite": (200, 200, 198),
        "waxed_oxidized_copper": (82, 165, 138), "waxed_oxidized_cut_copper": (78, 158, 132), "waxed_oxidized_chiseled_copper": (70, 180, 150),
        "waxed_oxidized_copper_grate": (70, 150, 125), "light_blue_stained_glass": (120, 190, 240), "cyan_stained_glass": (60, 170, 190),
        "white_stained_glass": (205, 222, 235), "purpur_block": (170, 125, 170), "purpur_pillar": (170, 125, 170), "sea_lantern": (170, 235, 235),
        "quartz_stairs": (235, 231, 224), "smooth_quartz_slab": (238, 234, 228)}
CACHE = {"air", "light", "barrier", "chain", "lantern", "end_rod", "lightning_rod", "white_stained_glass_pane", "ladder"}
s = 4
W = (SX + SZ) * s + 40
Hh = (SX + SZ) * s // 2 + SY * s + 60
img = Image.new("RGB", (W, Hh), (135, 190, 235))
dr = ImageDraw.Draw(img)
pts = [(p, b) for p, b in base.items() if b not in CACHE and not (b == "calcite" and p[1] < SOL)]
pts.sort(key=lambda t: (t[0][0] + t[0][2], t[0][1]))


def ouvert(p):
    return base.get(p, "air" if p[1] > SOL else "x") in CACHE


for (x, y, z), b in pts:
    c = COUL.get(b, (180, 180, 180))
    px = (x - z) * s + SZ * s + 20
    py = (x + z) * s // 2 - y * s + SY * s + 20
    top = [(px, py), (px + s, py + s // 2), (px, py + s), (px - s, py + s // 2)]
    if ouvert((x, y + 1, z)):
        dr.polygon(top, fill=c)
    if ouvert((x + 1, y, z)):
        dr.polygon([(px + s, py + s // 2), (px, py + s), (px, py + 2 * s), (px + s, py + s + s // 2)], fill=tuple(int(v * .78) for v in c))
    if ouvert((x, y, z + 1)):
        dr.polygon([(px - s, py + s // 2), (px, py + s), (px, py + 2 * s), (px - s, py + s + s // 2)], fill=tuple(int(v * .62) for v in c))
img.save("exports/arene_air_vue.png")
print("vue :", img.size)
