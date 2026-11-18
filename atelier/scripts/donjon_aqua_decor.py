# [claude-build]
"""donjon_aqua_decor.py - decor du Sanctuaire des Marees dans le style des monuments oceaniques de Minecraft : briques de prismarine, prismarine sombre en bandeaux,
lanternes de mer encastrees (murs, sols, plafonds), pilastres, eponges mouillees dans les salles cachees, noyau d'or de la salle du boss. Un petit herbier pousse
dans la Colonnade seulement. Execute par donjon_aqua.py avant l'eclairage et l'ecriture."""
import numpy as np

SHELL = {"prismarine_bricks", "prismarine", "dark_prismarine"}
SPECIAUX = {"lever", "stone_button", "stone_pressure_plate", "oak_wall_sign", "oak_trapdoor", "ladder", "lodestone", "iron_bars", "iron_trapdoor"}
PORTE_CASES = {tuple(c) for p in PORTES.values() for c in p["cases"]}
P_B, P_R, P_N, LANTERNE = S("prismarine_bricks"), S("prismarine"), S("dark_prismarine"), S("sea_lantern")
cb = Counter()


def dans(q):
    return 0 <= q[0] < SX and 0 <= q[1] < SY and 0 <= q[2] < SZ


def voisins6(p):
    x, y, z = p
    return ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1))


def pres_porte(p, r=2):
    x, y, z = p
    return any(q in PORTE_CASES for q in ((x + r, y, z), (x - r, y, z), (x, y, z + r), (x, y, z - r), (x, y + r, z), (x, y - r, z)))


SALLES_NOYEES = [n_ for n_ in SALLES if n_ != "S1"]
EPONGE = {c["boite"] for c in CACHEES.values() if c["eau"]}


def boite_de(p):
    x, y, z = p
    for (x0, x1, y0, y1, z0, z1) in EPONGE:
        if x0 - 1 <= x <= x1 + 1 and y0 - 1 <= y <= y1 + 1 and z0 - 1 <= z <= z1 + 1:
            return True
    return False


# ---------------------------------------------------------------- restyler la coque : sols en damier, bandeaux sombres, lanternes de mer encastrees, plafond a grille de lanternes
for p in list(blocs):
    if nom(p) not in SHELL or p in PORTE_CASES:
        continue
    x, y, z = p
    ints = [q for q in voisins6(p) if dans(q) and C[q]]
    if not ints:
        continue
    pres_special = any(nom(q) in SPECIAUX for q in voisins6(p)) or pres_porte(p, 1)
    dessus, dessous = (x, y + 1, z) in ints, (x, y - 1, z) in ints
    if dessus:                                                     # sol
        if boite_de(p):
            b = S("wet_sponge")
            cb["eponges"] += 1
        elif x % 8 == 4 and z % 8 == 4 and not pres_special:
            b = LANTERNE
            cb["lanternes_sol"] += 1
        else:
            b = P_N if (x // 3 + z // 3) % 2 == 0 else P_B
    elif dessous:                                                  # plafond
        if x % 6 == 3 and z % 6 == 3 and not pres_special:
            b = LANTERNE
            cb["lanternes_plafond"] += 1
        else:
            b = P_R if h01(x, y, z, 910) < .25 else P_B
    else:                                                          # mur
        if (x + z) % 8 == 0 and y in (47, 53) and not pres_special:
            b = LANTERNE
            cb["lanternes_mur"] += 1
        elif (y - SOL) % 6 in (1, 2):
            b = P_N                                                # bandeau sombre
        else:
            b = P_R if h01(x, y, z, 911) < .2 else P_B
    put(x, y, z, b)

# ---------------------------------------------------------------- pilastres le long des murs des salles noyees (prismarine, bague sombre, lanterne de mer a mi-hauteur)
for n_ in SALLES_NOYEES:
    x0, x1, z0, z1, top = SALLES[n_]
    cotes = [((x, z0), (0, -1)) for x in range(x0 + 3, x1 - 2, 8)] + [((x, z1), (0, 1)) for x in range(x0 + 3, x1 - 2, 8)] + \
            [((x0, z), (-1, 0)) for z in range(z0 + 3, z1 - 2, 8)] + [((x1, z), (1, 0)) for z in range(z0 + 3, z1 - 2, 8)]
    for ((x, z), (dx, dz)) in cotes:
        if any(blocs.get((x, y, z)) != EAU for y in range(SOL + 1, top + 1)) or any(nom((x + dx, y, z + dz)) not in SHELL for y in range(SOL + 1, top + 1)):
            continue
        if any(nom((x + a, y, z + b)) in SPECIAUX for a in (-1, 0, 1) for b in (-1, 0, 1) for y in range(SOL + 1, top + 1)):
            continue
        for y in range(SOL + 1, top + 1):
            k = y - SOL
            put(x, y, z, LANTERNE if k == 7 else (P_N if k in (1, 2, top - SOL - 1, top - SOL) else P_B))
        cb["pilastres"] += 1

# ---------------------------------------------------------------- noyau d'or de la salle du boss : un cube de 2 x 2 x 2 dans le mur nord, entre deux lanternes de mer
for (x, y, z) in [(x_, y_, z_) for x_ in (100, 101) for y_ in (SOL + 1, SOL + 2) for z_ in (118, 119)]:
    if (x, y, z) in blocs:
        put(x, y, z, S("gold_block"))
        cb["or"] += 1
for (x, y, z) in ((99, SOL + 1, 119), (102, SOL + 1, 119), (99, SOL + 2, 119), (102, SOL + 2, 119)):
    if (x, y, z) in blocs:
        put(x, y, z, LANTERNE)
for x in (100, 101):                                               # un cadre de prismarine sombre autour
    for z in (118, 119):
        for y in (SOL + 3,):
            if (x, y, z) in blocs:
                put(x, y, z, P_N)

# ---------------------------------------------------------------- herbier de la Colonnade (S2) : algues, herbes, quelques coraux ; ailleurs, rien ne pousse
CORAUX = ["tube", "brain", "bubble", "fire", "horn"]
x0, x1, z0, z1, top = SALLES["S2"]
for (x, y, z) in [p for p, s in blocs.items() if s == EAU and x0 <= p[0] <= x1 and z0 <= p[2] <= z1 and p[1] <= 60]:
    sous = (x, y - 1, z)
    if nom(sous) not in SHELL or sous[1] > 41 or any(nom((x + a, y, z + b)) in ("oak_trapdoor", "oak_wall_sign") for a in (-1, 0, 1) for b in (-1, 0, 1)):
        continue
    haut = 0
    while blocs.get((x, y + haut, z)) == EAU and haut < 14:
        haut += 1
    if haut < 5:
        continue
    n = bruit(x, z, 7, 900)
    r = h01(x, y, z, 901)
    if n > .66 and r < .4:
        h = min(2 + int(h01(x, y, z, 902) * 6), haut - 3)
        for k in range(h - 1):
            put(x, y + k, z, S("kelp_plant"))
        put(x, y + h - 1, z, S("kelp", age=int(10 + h01(x, y, z, 903) * 14)))
        cb["algues"] += 1
    elif n > .5 and r < .55:
        put(x, y, z, S("seagrass"))
        cb["herbe"] += 1
    elif r > .985:
        c = CORAUX[int(h01(x, y, z, 905) * 5)]
        put(x, y, z, S(f"{c}_coral_block"))
        put(x, y + 1, z, S(f"{CORAUX[int(h01(x, y, z, 907) * 5)]}_coral_fan", waterlogged=True))
        cb["coraux"] += 1
print("[4] decor :", dict(cb), flush=True)
