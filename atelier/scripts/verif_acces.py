# [claude-build]
"""Relit un .nbt et verifie par parcours en largeur qu'un joueur atteint chaque cible a pied.
Usage : verif_acces.py <structure.nbt> <cibles.json>
Mouvements : marche, marche d'1, chutes, sauts, echelles, lianes, trappes, portes, colonnes de bulles.
Avec "plateau" dans le fichier de cibles, controle aussi que chaque chute fait mal et ne sort pas de l'arene."""
import json
import math
import sys
from collections import deque

import nbtlib

f = nbtlib.load(sys.argv[1])
conf = json.load(open(sys.argv[2], encoding="utf-8"))
pal = [str(p["Name"]).split(":")[1] for p in f["palette"]]
SX, SY, SZ = [int(v) for v in f["size"]]
SOL = conf["sol"]
grid = {}
for e in f["blocks"]:
    x, y, z = (int(v) for v in e["pos"])
    grid[(x, y, z)] = pal[int(e["state"])]

for p_ in conf.get("bloquer", []):                     # pour tester une route seule
    grid[tuple(p_)] = "stone"
LIANES = {"twisting_vines", "twisting_vines_plant", "vine", "cave_vines", "cave_vines_plant", "kelp", "kelp_plant"}
PLANTES = {"short_grass", "fern", "tall_grass", "large_fern", "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "oxeye_daisy",
           "cornflower", "lily_of_the_valley", "rose_bush", "peony", "lilac", "red_mushroom", "brown_mushroom", "moss_carpet", "glow_lichen",
           "hanging_roots", "spore_blossom", "seagrass", "small_dripleaf", "snow"}
FLOTTE = {"lily_pad", "big_dripleaf"}                   # on tient debout dessus, dans la meme case
PASSABLE = {"air", "ladder", "birch_trapdoor", "birch_door", "bubble_column", "pink_petals", "light", "powder_snow",
            "white_wall_banner", "light_blue_wall_banner", "water", "oak_trapdoor", "oak_door", "spruce_trapdoor", "stone_pressure_plate", "stone_button", "black_carpet", "black_candle"} | LIANES | PLANTES | FLOTTE
if "--sans-colonnes" in sys.argv:           # teste les acces si les colonnes de bulles ne marchaient pas
    PASSABLE.discard("bubble_column")
NON_SUPPORT = {"air", "ladder", "bubble_column", "lantern", "birch_door", "lightning_rod", "pink_petals", "light", "chain",
               "white_wall_banner", "light_blue_wall_banner", "oak_trapdoor", "big_dripleaf_stem"} | LIANES | PLANTES | FLOTTE
NON_SUPPORT |= {f"{b}_fence" for b in ("oak", "spruce", "jungle", "dark_oak", "mangrove")} | {"mossy_stone_brick_wall", "white_stained_glass_pane", "iron_bars", "soul_lantern", "spruce_trapdoor", "lava", "blackstone_wall", "fire", "soul_fire", "oak_fence", "stone_pressure_plate", "stone_button"}


def n(p):
    if p in grid:
        return grid[p]
    return "air" if p[1] > SOL else "calcite"


def passable(p):
    return n(p) in PASSABLE


def debout(p):
    return passable(p) and passable((p[0], p[1] + 1, p[2]))


def support(p):
    return n((p[0], p[1] - 1, p[2])) not in NON_SUPPORT or n(p) in ("ladder", "bubble_column", "water") or n(p) in LIANES or n(p) in FLOTTE


def tomber(p):
    x, y, z = p
    for k in range(30):
        q = (x, y - k, z)
        if not debout(q) or q[1] < 0:
            return None
        if support(q):
            return q
    return None


SAUTS = ((2, 0, (1, 0, -1, -2)), (-2, 0, (1, 0, -1, -2)), (0, 2, (1, 0, -1, -2)), (0, -2, (1, 0, -1, -2)),
         (2, 1, (1, 0, -1)), (2, -1, (1, 0, -1)), (-2, 1, (1, 0, -1)), (-2, -1, (1, 0, -1)),
         (1, 2, (1, 0, -1)), (-1, 2, (1, 0, -1)), (1, -2, (1, 0, -1)), (-1, -2, (1, 0, -1)),
         (3, 0, (0, -1)), (-3, 0, (0, -1)), (0, 3, (0, -1)), (0, -3, (0, -1)),
         (3, 1, (0, -1)), (3, -1, (0, -1)), (-3, 1, (0, -1)), (-3, -1, (0, -1)),
         (1, 3, (0, -1)), (-1, 3, (0, -1)), (1, -3, (0, -1)), (-1, -3, (0, -1)),
         (2, 2, (1, 0, -1)), (2, -2, (1, 0, -1)), (-2, 2, (1, 0, -1)), (-2, -2, (1, 0, -1)))

if conf.get("sauts_longs"):                            # sauts sans sprint : 2 blocs d'ecart en montant d'un bloc
    SAUTS = SAUTS + tuple((a * b, c * d, (1, 0, -1)) for a, c in ((3, 0), (3, 1), (3, 2), (2, 3), (1, 3), (0, 3)) for b in (1, -1) for d in (1, -1))
start = tuple(conf["depart"])
assert debout(start) and support(start), (start, n(start), n((start[0], start[1] - 1, start[2])))
vus = {start}
file = deque([start])
while file:
    x, y, z = p = file.popleft()
    voisins = []
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        q = (x + dx, y, z + dz)
        if not (0 <= q[0] < SX and 0 <= q[2] < SZ):
            continue
        if debout(q):
            t = q if support(q) else tomber(q)
            if t:
                voisins.append(t)
        else:
            q2 = (x + dx, y + 1, z + dz)
            if debout(q2) and passable((x, y + 2, z)) and support(q2):
                voisins.append(q2)
    if support(p) and n(p) not in ("ladder", "bubble_column", "water") and n(p) not in LIANES and passable((x, y + 2, z)):
        for dx, dz, dys in SAUTS:
            inter = {(x + round(dx * k), z + round(dz * k)) for k in (1 / 3, 1 / 2, 2 / 3)} - {(x, z), (x + dx, z + dz)}
            if not all(passable((ix, y + k, iz)) for ix, iz in inter for k in (0, 1, 2)):
                continue
            for dy in dys:
                t = (x + dx, y + dy, z + dz)
                if 0 <= t[0] < SX and 0 <= t[2] < SZ and debout(t) and support(t):
                    voisins.append(t)
                    break
    if n((x, y - 1, z)).endswith("_trapdoor"):            # on ouvre la trappe sous ses pieds
        voisins.append((x, y - 1, z))
    if n(p) == "bubble_column" and n((x, y + 1, z)) == "bubble_column":
        voisins.append((x, y + 1, z))                   # la colonne monte
    if n(p) == "ladder" or n((x, y - 1, z)) == "ladder" or n(p).endswith("_trapdoor") or n(p) in LIANES or n(p) == "water":
        for dy in (1, -1):
            q = (x, y + dy, z)
            if debout(q) or (passable(q) and dy == -1):
                voisins.append(q)
    for q in voisins:
        if q not in vus and 0 <= q[1] < SY + 3:
            vus.add(q)
            file.append(q)

print("cases atteignables :", len(vus))
ok = True
for nomc, c in conf["cibles"].items():
    r = tuple(c) in vus
    ok &= r
    print(f"  {nomc:<28} {tuple(c)} : {'OK' if r else 'NON ATTEIGNABLE'}")
if "plateau" in conf:
    AMORTI = {"bubble_column", "water", "ladder", "powder_snow", "slime_block", "hay_block", "cobweb", "scaffolding", "honey_block"} | LIANES
    ha = conf["plateau"]
    mauvais, hauteurs, sorties = {}, {}, 0
    for (x, y, z) in vus:
        if math.hypot(x + .5 - SX / 2, z + .5 - SZ / 2) >= SX / 2 - 1:
            sorties += 1
        if not support((x, y, z)) or n((x, y, z)) != "air":
            continue
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y, z + dz)
            if n(q) != "air" or n((q[0], y + 1, q[2])) != "air" or support(q):
                continue
            k = y
            while k > 0 and n((q[0], k - 1, q[2])) in ("air", "light", "lantern", "chain", "white_wall_banner", "light_blue_wall_banner"):
                k -= 1
            sous = n((q[0], k - 1, q[2]))
            h = y - k
            if h >= 4:
                hauteurs[(y >= ha, min(h // 5 * 5, 40))] = hauteurs.get((y >= ha, min(h // 5 * 5, 40)), 0) + 1
                if sous in AMORTI or sous == "birch_trapdoor" and False:
                    mauvais[(q[0], q[2])] = (sous, y, k)
    print("chutes depuis le plateau ou plus haut (tranche de hauteur -> bords) :",
          sorted((b, v) for (a, b), v in hauteurs.items() if a))
    print("chutes amorties :", len(mauvais), list(mauvais.items())[:8])
    print("cases atteintes hors de l'arene :", sorties)
    ok &= not mauvais and not sorties
print("RESULTAT :", "tout est accessible" if ok else "IL RESTE DES CIBLES INACCESSIBLES")
if "--diag" not in sys.argv:
    sys.exit(0 if ok else 1)
