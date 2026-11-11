# [claude-build]
"""donjon_aqua_decor.py - decor du Sanctuaire des Marees : lanternes de mer dans les murs, algues, herbiers, coraux, pickles, lichen lumineux,
colonnes brisees dans les bassins, mousse et lianes dans les salles seches, totems. Execute par donjon_aqua.py avant l'eclairage et l'ecriture."""
import numpy as np

SHELL = {"prismarine_bricks", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "dark_prismarine", "mossy_cobblestone", "sandstone", "calcite"}
SPECIAUX = {"lever", "stone_button", "stone_pressure_plate", "oak_wall_sign", "ladder", "lodestone", "iron_bars"}
PORTE_CASES = {tuple(c) for p in PORTES.values() for c in p["cases"]}
DIL = np.zeros((SX, SY, SZ), dtype=bool)
for (x0, x1, y0, y1, z0, z1) in PRESSION:
    DIL[max(x0 - 3, 0):x1 + 4, max(y0 - 3, 0):y1 + 4, max(z0 - 3, 0):z1 + 4] = True     # abords des tunnels : on n'y met rien de solide
CORAUX = ["tube", "brain", "bubble", "fire", "horn"]
cb = Counter()


def eau_(q):
    s_ = blocs.get(q)
    return s_ is not None and (s_[0] == "minecraft:water" or dict(s_[1]).get("waterlogged") == "true")


def pres_proche(p, r=2):
    x, y, z = p
    for q in ((x + r, y, z), (x - r, y, z), (x, y, z + r), (x, y, z - r), (x, y + r, z), (x, y - r, z)):
        if q in PORTE_CASES:
            return True
    return False


def voisins6(p):
    x, y, z = p
    return ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1))


# ---------------------------------------------------------------- lanternes de mer encastrees dans les murs (une par case de 6 x 6 x 6, la mieux placee)
meilleur = {}
for (x, y, z), s in list(blocs.items()):
    if nom((x, y, z)) not in SHELL:
        continue
    adj_int = [q for q in voisins6((x, y, z)) if 0 <= q[0] < SX and 0 <= q[1] < SY and 0 <= q[2] < SZ and C[q] and (q not in blocs or eau_(q))]
    if not adj_int:
        continue
    if any(nom(q) in SPECIAUX or q in PORTE_CASES for q in voisins6((x, y, z))) or pres_proche((x, y, z), 2):
        continue
    k = (x // 6, y // 6, z // 6)
    v = h01(x, y, z, 910)
    if k not in meilleur or v > meilleur[k][0]:
        meilleur[k] = (v, (x, y, z))
for _, p in meilleur.values():
    put(*p, S("sea_lantern"))
    cb["lanternes"] += 1

# ---------------------------------------------------------------- herbier et recif au fond de chaque bassin (eau libre, hors tunnels)
eau_cases = [p for p, s in blocs.items() if s == EAU]
for (x, y, z) in eau_cases:
    sous = (x, y - 1, z)
    if nom(sous) not in SHELL or DIL[x, y, z]:
        continue
    haut = 0
    while blocs.get((x, y + haut, z)) == EAU and haut < 20:
        haut += 1
    if haut < 3:
        continue
    n = bruit(x, z, 7, 900)
    r = h01(x, y, z, 901)
    if n > .6 and r < .55:                                         # forets d'algues
        h = min(2 + int(h01(x, y, z, 902) * 8), haut - 2)
        for k in range(h - 1):
            put(x, y + k, z, S("kelp_plant"))
        put(x, y + h - 1, z, S("kelp", age=int(10 + h01(x, y, z, 903) * 14)))
        cb["algues"] += 1
    elif n > .45 and r < .7:
        if h01(x, y, z, 904) < .35 and haut >= 3:
            put(x, y, z, S("tall_seagrass", half="lower"))
            put(x, y + 1, z, S("tall_seagrass", half="upper"))
        else:
            put(x, y, z, S("seagrass"))
        cb["herbe"] += 1
    elif r > .975:                                                 # petit recif : un bloc de corail et ce qui pousse dessus
        c = CORAUX[int(h01(x, y, z, 905) * 5)]
        put(x, y, z, S(f"{c}_coral_block"))
        t = h01(x, y, z, 906)
        if t < .4:
            put(x, y + 1, z, S(f"{c}_coral_fan", waterlogged=True))
        elif t < .75:
            put(x, y + 1, z, S(f"{CORAUX[int(h01(x, y, z, 907) * 5)]}_coral", waterlogged=True))
        else:
            put(x, y + 1, z, S("sea_pickle", pickles=1 + int(h01(x, y, z, 908) * 4), waterlogged=True))
        cb["coraux"] += 1
    elif r < .02:
        put(x, y, z, S("sea_pickle", pickles=1 + int(h01(x, y, z, 909) * 4), waterlogged=True))
        cb["pickles"] += 1

# ---------------------------------------------------------------- lichen lumineux sur les parois immergees
FACES = {(1, 0, 0): "east", (-1, 0, 0): "west", (0, 0, 1): "south", (0, 0, -1): "north"}
for (x, y, z) in eau_cases:
    if blocs.get((x, y, z)) != EAU or DIL[x, y, z] or h01(x, y, z, 920) > .035:
        continue
    for d, f_ in FACES.items():
        q = (x + d[0], y, z + d[2])
        if nom(q) in SHELL and not pres_proche((x, y, z), 2):
            prop = {k: False for k in ("down", "up", "east", "west", "north", "south")}
            prop[f_] = True
            put(x, y, z, S("glow_lichen", waterlogged=True, **prop))
            cb["lichen_eau"] += 1
            break

# ---------------------------------------------------------------- colonnes brisees au milieu des grands bassins
for nm_, (x0, x1, z0, z1, fond) in POOLS.items():
    if nm_ in ("P1", "P8a", "P8b", "P9", "B"):
        continue
    for essai in range(26):
        x = x0 + 2 + int(h01(x0, z0, essai, 930) * (x1 - x0 - 3))
        z = z0 + 2 + int(h01(x1, z1, essai, 931) * (z1 - z0 - 3))
        if any(blocs.get((x + a, SOL, z + b)) != EAU for a in (-1, 0, 1) for b in (-1, 0, 1)) or DIL[x, SOL, z] or DIL[x, fond + 2, z]:
            continue
        if any(nom((x + a, y, z + b)) not in (None, "water", "kelp", "kelp_plant", "seagrass", "tall_seagrass") for a in (-1, 0, 1) for b in (-1, 0, 1) for y in range(fond + 1, SOL + 2)):
            continue
        ht = fond + 3 + int(h01(x, z, essai, 932) * (SOL + 3 - fond - 3))
        if nm_ == "S5" and abs(x - 120) < 4:                        # on laisse libre la sortie du tunnel T1
            continue
        for y in range(fond + 1, ht + 1):
            put(x, y, z, S("mossy_stone_bricks") if y < ht else S("chiseled_stone_bricks") if h01(x, y, z, 933) > .5 else S("cracked_stone_bricks"))
        cb["colonnes"] += 1

# ---------------------------------------------------------------- salles seches : mousse au sol, lianes au plafond, totems
secs = [p for p, s in blocs.items() if nom(p) in ("mossy_stone_bricks", "prismarine_bricks", "stone_bricks", "cracked_stone_bricks") and p[1] == SOL]
for (x, y, z) in secs:
    p = (x, y + 1, z)
    if p in blocs or not C[p] or any(blocs.get((x + a, y + 1, z + b)) is not None and nom((x + a, y + 1, z + b)) != "air" for a in (-1, 0, 1) for b in (-1, 0, 1)):
        continue
    if p in PORTE_CASES or pres_proche(p, 1):
        continue
    n = bruit(x, z, 5, 940)
    if n > .6 and h01(x, y, z, 941) < .55:
        put(*p, S("moss_carpet"))
        cb["mousse"] += 1
    elif h01(x, y, z, 942) < .006:
        put(*p, S("dead_" + CORAUX[int(h01(x, y, z, 943) * 5)] + "_coral_fan", waterlogged=False))
        cb["coraux_secs"] += 1
for (x, y, z), s in list(blocs.items()):                          # lianes sous les plafonds secs
    if nom((x, y, z)) in SHELL and y >= SOL + 4 and h01(x, y, z, 950) < .045 and (x, y - 1, z) not in blocs and C[x, y - 1, z] and not W[x, y - 1, z]:
        if any(nom(q) in SPECIAUX for q in voisins6((x, y - 1, z))) or (x, y - 1, z) in PORTE_CASES:
            continue
        for k in range(1, 2 + int(h01(x, y, z, 951) * 4)):
            q = (x, y - k, z)
            if q in blocs or not C[q] or W[q]:
                break
            put(*q, S("vine", up=True, down=False, north=False, south=False, east=False, west=False))
            cb["lianes"] += 1
TOTEMS = [(16, 10), (40, 34), (14, 48), (68, 46), (82, 46), (138, 46), (104, 88), (138, 88), (14, 124), (44, 148), (68, 124), (138, 150)]
for (tx, tz) in TOTEMS:
    if not all(blocs.get((tx + a, SOL, tz + b)) is not None and nom((tx + a, SOL, tz + b)) in SHELL and nom((tx + a, SOL + 1, tz + b)) in (None, "air") for a in (0, 1) for b in (0, 1)):
        continue
    if not all(C[tx + a, SOL + 1, tz + b] for a in (0, 1) for b in (0, 1)):
        continue
    for a in (0, 1):
        for b in (0, 1):
            put(tx + a, SOL + 1, tz + b, S("chiseled_stone_bricks"))
    for k, bl in enumerate(("dark_prismarine", "prismarine_bricks", "dark_prismarine", "sea_lantern")):
        put(tx, SOL + 2 + k, tz, S(bl))
    cb["totems"] += 1
print("[4] decor :", dict(cb), flush=True)
