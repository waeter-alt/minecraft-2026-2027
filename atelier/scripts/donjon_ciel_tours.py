# [claude-build]
"""donjon_ciel_tours.py - la ville dans les airs : chaque tour est un lieu de vie avec son role (observatoire, taverne, moulin, chapelle...), ses couleurs et son mobilier,
des fenetres a jardinieres et une devanture a store ; les grandes iles ont leurs maisons a toit de pente, leurs etals de marche, leurs arbres, leurs bancs et leur puits ;
les balcons ont des creneaux, des lanternes et un fanion. Rien n'est pose sur les cotes des escaliers, devant les entrees et les sorties, ni pres des leviers et des boutons
(le verificateur controle que tout reste franchissable). Execute par donjon_ciel.py apres le decor, avant l'eclairage."""
SIDE_IDX = {"o": 0, "s": 1, "e": 2, "n": 3}
INWARD = {0: (1, 0), 1: (0, -1), 2: (-1, 0), 3: (0, 1)}
VERS_MUR = {0: "west", 1: "south", 2: "east", 3: "north"}          # de la piece vers le mur de ce cote
VERS_PIECE = {0: "east", 1: "north", 2: "west", 3: "south"}
NOM_VEC = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}
cnt_t = Counter()

THEMES = {"AI": dict(w="spruce", c1="blue", c2="white"), "G1": dict(w="birch", c1="yellow", c2="white"), "G2": dict(w="cherry", c1="pink", c2="white"),
          "G3": dict(w="oak", c1="lime", c2="white"), "MV": dict(w="dark_oak", c1="brown", c2="yellow"), "RL": dict(w="jungle", c1="red", c2="white"),
          "CP": dict(w="acacia", c1="orange", c2="white"), "CI": dict(w="dark_oak", c1="purple", c2="gray"), "BE": dict(w="birch", c1="lime", c2="cyan"),
          "D": dict(w="oak", c1="light_blue", c2="white"), "BO": dict(w="spruce", c1="white", c2="light_blue")}
ROLES = {"AI": ["bibliotheque", "cartes", "observatoire"], "G1": ["salon", "chambre"], "G2": ["chambre", "atelier"], "G3": ["atelier", "salon"],
         "MV": ["moulin", "marche", "grenier"], "RL": ["taverne", "chambre"], "CP": ["gare", "marche", "cartes"],
         "CI": ["hall", "conseil", "chapelle", "bibliotheque"], "BE": ["jardin", "salon", "jardin"]}
TITRES = {"AI": ["Observatoire", "de l'Aiguille"], "G1": ["Maison du Vent"], "G2": ["Maison Nuage"], "G3": ["Maison Pluie"], "MV": ["Le Moulin", "des Vents"],
          "RL": ["Taverne", "du Relais"], "CP": ["Gare des", "Colonnes"], "CI": ["Citadelle", "haute"], "BE": ["Orangerie", "du Belvedere"]}
POTS = ["potted_poppy", "potted_dandelion", "potted_blue_orchid", "potted_allium", "potted_azure_bluet", "potted_red_tulip", "potted_pink_tulip", "potted_cornflower",
        "potted_oxeye_daisy", "potted_lily_of_the_valley", "potted_fern", "potted_azalea_bush", "potted_flowering_azalea_bush"]
FLEURS = ["poppy", "dandelion", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley"]
ETAGERE, CARTO = S("bookshelf"), S("cartography_table")
TOUR_NOM = {}                                                       # ile -> theme (les tours des ilots portent le nom de l'ile)


def bois(w):
    return {"pl": S(f"{w}_planks"), "lg": S(f"{w}_log", axis="y"), "sl": S(f"stripped_{w}_log", axis="y"),
            "st": lambda f, h="bottom": S(f"{w}_stairs", facing=f, half=h, shape="straight", waterlogged=False),
            "sb": lambda t="bottom": S(f"{w}_slab", type=t, waterlogged=False),
            "fe": S(f"{w}_fence", north=False, east=False, south=False, west=False, waterlogged=False)}


def pose(c, b):
    if c not in blocs:
        put(*c, b)
        return True
    return False


def pot(x, y, z, g=0):
    pose((x, y, z), S(POTS[int(h01(x, y, z, 50 + g) * len(POTS))]))


def table(x, w, z, p, bougie=True):
    if pose((x, w, z), p["sb"]("top")) and bougie and h01(x, w, z, 41) < .75:
        pose((x, w + 1, z), S("candle", candles=1 + int(h01(x, w, z, 42) * 4), lit=True, waterlogged=False))


def tapis(x, w, z, couleur):
    pose((x, w, z), S(f"{couleur}_carpet"))


# ====================================================================== decoration d'un niveau de tour
def decorer_niveau(nom_, t, k, c1, c2, role):
    T = ILES[nom_]["T"]
    cx, cz, hs, n, hl, c0 = t["cx"], t["cz"], t["hs"], t["n"], t["hl"], t["c0"]
    x0, x1, z0, z1 = cx - hs, cx + hs, cz - hs, cz + hs
    th = THEMES[nom_]
    p = bois(th["w"])
    yk = T + k * hl
    w = yk + 1
    run = (k + c0) % 4 if k < n else None
    arr = (k - 1 + c0) % 4 if k >= 1 else None
    reserve = {s for s in (run, arr) if s is not None}
    if k == 0:
        reserve.add(SIDE_IDX[t["entree"]])
        if run is not None:
            reserve.add((run - 1) % 4)                             # le debut de l'escalier s'aborde par la rangee voisine : on la laisse libre
    if k == n:
        reserve.add(SIDE_IDX[t["sortie"]])
    L = [s for s in range(4) if s not in reserve]

    def rang(s):
        if s == 0:
            return [(x0, z) for z in range(z0 + 1, z1)]
        if s == 1:
            return [(x, z1) for x in range(x0 + 1, x1)]
        if s == 2:
            return [(x1, z) for z in range(z0 + 1, z1)]
        return [(x, z0) for x in range(x0 + 1, x1)]

    def depth(cell, s, d):
        return (cell[0] + INWARD[s][0] * d, cell[1] + INWARD[s][1] * d)

    def etageres(s, haut=2, saute_centre=True):
        cs = rang(s)
        for i, (x, z) in enumerate(cs):
            if saute_centre and i == len(cs) // 2:
                continue
            for y in range(w, w + haut):
                pose((x, y, z), ETAGERE)

    def banc(s, longueur=3, d=0):
        cs = rang(s)
        m = len(cs) // 2
        for i in range(m - longueur // 2, m + longueur // 2 + 1):
            xx, zz = depth(cs[i], s, d)
            pose((xx, w, zz), p["st"](VERS_MUR[s]))

    def coin_libre(coin_s):
        return all(s in L for s in coin_s)

    coins = {(x0, z0): (0, 3), (x0, z1): (0, 1), (x1, z1): (2, 1), (x1, z0): (2, 3)}

    def plantes_aux_coins():
        for coin, ss in coins.items():
            if coin_libre(ss) and (coin[0], w, coin[1]) not in blocs:
                pose((coin[0], w, coin[1]), p["sb"]("top"))
                pot(coin[0], w + 1, coin[1], k)

    if role in ("bibliotheque", "cartes"):
        if role == "bibliotheque":
            for s in L[:2]:
                etageres(s)
            table(cx, w, cz, p)
            pose((cx - 1, w, cz), p["st"]("west"))
            pose((cx + 1, w, cz), p["st"]("east"))
        else:
            if L:
                cs = rang(L[0])
                x_, z_ = cs[len(cs) // 2]
                pose((x_, w, z_), CARTO)
                for i in (1, len(cs) - 2):
                    for y in (w, w + 1):
                        pose((cs[i][0], y, cs[i][1]), ETAGERE)
            for dx in (-1, 0, 1):
                table(cx + dx, w, cz, p, bougie=(dx != 0))
            pose((cx, w, cz + 1), p["st"]("south"))
            pose((cx, w, cz - 1), p["st"]("north"))
    elif role == "observatoire":
        pose((cx, w, cz), S("copper_block"))
        pose((cx, w + 1, cz), S("lightning_rod", facing="up", powered=False, waterlogged=False))
        for (x_, z_) in ((x0 + 1, z0 + 1), (x1 - 1, z0 + 1), (x0 + 1, z1 - 1), (x1 - 1, z1 - 1)):
            if h01(x_, w, z_, 61) < .9:
                pose((x_, w, z_), S("amethyst_cluster", facing="up", waterlogged=False))
        yb = yk + hl - 1
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                if h01(x, yb, z, 62) < .22:
                    pose((x, yb, z), S("end_rod", facing="down"))
        if L:
            banc(L[0])
    elif role == "salon":
        if L:
            banc(L[0])
            cs = rang(L[0])
            xx, zz = depth(cs[len(cs) // 2], L[0], 2)
            table(xx, w, zz, p)
        if len(L) > 1:
            cs = rang(L[1])
            xx, zz = depth(cs[len(cs) // 2], L[1], 0)
            pose((xx, w, zz), p["st"](VERS_MUR[L[1]]))
        plantes_aux_coins()
    elif role == "atelier":
        if L:
            objets = [S("anvil", facing="east"), S("grindstone", face="floor", facing="north"), S("smithing_table"), S("cauldron"), S("loom", facing="south")]
            cs = rang(L[0])
            for i in range(1, len(cs) - 1):
                xx, zz = cs[i]
                pose((xx, w, zz), objets[(i - 1) % len(objets)])
            for i in range(0, len(cs)):
                xx, zz = cs[i]
                pose((xx, w + 2, zz), S("iron_bars", waterlogged=False)) if i % 2 == 0 else None
        if len(L) > 1:
            etageres(L[1], 2, True)
        plantes_aux_coins()
    elif role == "jardin":
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                if (x, w, z) in blocs or max(abs(x - cx), abs(z - cz)) > hs - 1:
                    continue
                r_ = h01(x, w, z, 71)
                if r_ < .55:
                    put(x, w - 1, z, S("moss_block"))
                    g_ = h01(x, w, z, 72)
                    if g_ < .35:
                        put(x, w, z, S(FLEURS[int(h01(x, w, z, 73) * len(FLEURS))]))
                    elif g_ < .55:
                        put(x, w, z, S("fern"))
                    elif g_ < .7 and (x, w + 1, z) not in blocs:
                        put(x, w, z, S("large_fern", half="lower"))
                        put(x, w + 1, z, S("large_fern", half="upper"))
                    elif g_ < .85 and (x, w + 1, z) not in blocs:
                        nm = ["rose_bush", "lilac", "peony"][int(h01(x, w, z, 74) * 3)]
                        put(x, w, z, S(nm, half="lower"))
                        put(x, w + 1, z, S(nm, half="upper"))
        if L:
            banc(L[0], 3)
        plantes_aux_coins()
    elif role == "chambre":
        if L:
            cs = rang(L[0])
            for i in (1, 2):                                       # un lit en laine : oreiller blanc, couverture de la couleur de la tour
                xx, zz = cs[i]
                pose((xx, w, zz), S("white_wool" if i == 1 else f"{c1}_wool"))
            xx, zz = cs[3] if len(cs) > 3 else cs[-1]
            table(xx, w, zz, p)
        if len(L) > 1:
            cs = rang(L[1])
            for i in (1, 2):                                       # une armoire : deux planches de haut, un montant de bois ecorce au milieu
                xx, zz = cs[i]
                for y in (w, w + 1):
                    pose((xx, y, zz), p["pl"])
            plantes_aux_coins()
    elif role == "taverne":
        if L:
            cs = rang(L[0])
            for i in range(0, len(cs) - 1):                        # le comptoir, un cran devant le mur, avec l'etagere a bouteilles derriere ; le bout reste ouvert
                xx, zz = depth(cs[i], L[0], 1)
                pose((xx, w, zz), p["pl"])
                pose((xx, w + 1, zz), S("candle", candles=2, lit=True, waterlogged=False)) if i % 2 == 0 else pot(xx, w + 1, zz, 5)
                bx, bz = cs[i]
                for y in (w, w + 1):
                    pose((bx, y, bz), ETAGERE)
        table(cx, w, cz + 1, p)
        pose((cx - 1, w, cz + 1), p["st"]("west"))
        pose((cx + 1, w, cz + 1), p["st"]("east"))
        plantes_aux_coins()
    elif role == "moulin":
        if L:
            pose((cx, w, cz), S("grindstone", face="floor", facing="east"))
            cs = rang(L[0])
            for i in range(0, len(cs)):
                for y in (w, w + 1):
                    pose((cs[i][0], y, cs[i][1]), S("hay_block", axis="y"))
        if len(L) > 1:
            cs = rang(L[1])
            for i in (1, len(cs) // 2, len(cs) - 2):
                pose((cs[i][0], w, cs[i][1]), S("cauldron"))
        plantes_aux_coins()
    elif role == "marche":
        for si, s in enumerate(L[:2]):
            cs = rang(s)
            for i in range(0, len(cs) - 1):
                xx, zz = depth(cs[i], s, 1)
                pose((xx, w, zz), p["sb"]("bottom"))
                pose((xx, w + 1, zz), S(["melon", "pumpkin", "hay_block", "red_mushroom_block"][(i + si) % 4]) if i % 2 == 0 else S("candle", candles=3, lit=True, waterlogged=False))
            for i in range(0, len(cs)):
                pose((cs[i][0], w + 2, cs[i][1]), S(f"{c1 if i % 2 else c2}_wool"))                    # l'auvent du stand : des rayures sur le mur du fond
        plantes_aux_coins()
    elif role == "grenier":
        for s in L[:2]:
            cs = rang(s)
            for i in range(len(cs)):
                for y in range(w, w + 1 + (1 if i % 2 == 0 else 0)):
                    pose((cs[i][0], y, cs[i][1]), S("hay_block", axis="y"))
        plantes_aux_coins()
    elif role == "hall":
        for s in L[:2]:
            cs = rang(s)
            for i in (1, len(cs) - 2):                             # des piliers de pierre sculptee surmontes d'une lanterne
                xx, zz = cs[i]
                for y in range(w, w + 3):
                    pose((xx, y, zz), S("chiseled_stone_bricks") if y == w + 1 else S("polished_blackstone_bricks"))
                pose((xx, w + 3, zz), LANTERNE)
            for i in (len(cs) // 2,):
                xx, zz = cs[i]
                pose((xx, w + 2, zz), S(f"{c1}_wall_banner", facing=VERS_PIECE[s]))
        for dx in range(-hs + 1, hs):                              # un tapis de procession
            pose((cx + dx, w, cz), S("red_carpet"))
        if L:
            cs = rang(L[-1])
            xx, zz = cs[len(cs) // 2]
            pose((xx, w, zz), S("anvil", facing="north"))
    elif role == "conseil":
        for dx in range(-2, 3):
            table(cx + dx, w, cz, p, bougie=(dx % 2 == 0))
        for dx in range(-2, 3, 2):
            pose((cx + dx, w, cz + 1), p["st"]("south"))
            pose((cx + dx, w, cz - 1), p["st"]("north"))
        for s in L[:2]:
            cs = rang(s)
            pose((cs[len(cs) // 2][0], w + 2, cs[len(cs) // 2][1]), S(f"{c1}_wall_banner", facing=VERS_PIECE[s]))
        plantes_aux_coins()
    elif role == "chapelle":
        if L:
            s = L[0]
            cs = rang(s)
            m = len(cs) // 2
            for i in range(m - 1, m + 2):
                xx, zz = cs[i]
                pose((xx, w, zz), S("quartz_slab", type="bottom", waterlogged=False))
                pose((xx, w + 1, zz), S("candle", candles=3, lit=True, waterlogged=False)) if i != m else pose((xx, w + 1, zz), S("end_rod", facing="up"))
            for rang_i in (2, 3):
                for dx in (-1, 1):
                    xx, zz = depth(cs[m + dx], s, rang_i)
                    pose((xx, w, zz), p["st"](VERS_MUR[s]))
        plantes_aux_coins()
    elif role == "gare":
        for s in L[:2]:
            banc(s, 3)
        if L:
            cs = rang(L[0])
            xx, zz = cs[len(cs) // 2 + 1] if len(cs) > 2 else cs[0]
        plantes_aux_coins()
    # le tapis du milieu (apres le mobilier, il ne comble que les vides)
    rr = min(2, hs - 1)
    cc1, cc2 = (c1, c2)
    for dx in range(-rr, rr + 1):
        for dz in range(-rr, rr + 1):
            tapis(cx + dx, w, cz + dz, cc1 if max(abs(dx), abs(dz)) <= 1 else cc2)
    # poutres, lustre ; des fenetres a jardiniere sur chaque mur
    if k < n:
        yb = yk + hl - 1
        for x in range(x0 + 1, x1):
            pose((x, yb, cz), S(f"stripped_{th['w']}_log", axis="x"))
        for z in range(z0 + 1, z1):
            pose((cx, yb, z), S(f"stripped_{th['w']}_log", axis="z"))
        if hl >= 8:
            pose((cx, yb - 1, cz), S("chain", axis="y", waterlogged=False))
            pose((cx, yb - 2, cz), S("lantern", hanging=True, waterlogged=False))
        else:
            pose((cx, yb - 1, cz), S("lantern", hanging=True, waterlogged=False))
        for s in range(4):
            if (k == 0 and s == SIDE_IDX[t["entree"]]):
                continue
            wx, wz = {0: (x0 - 1, cz), 1: (cx, z1 + 1), 2: (x1 + 1, cz), 3: (cx, z0 - 1)}[s]
            ok_ = all((wx, y, wz) in blocs and nom((wx, y, wz)) in ("stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks") for y in (yk + 3, yk + 4))
            if not ok_:
                continue
            for y in (yk + 3, yk + 4):
                put(wx, y, wz, S("glass_pane"))
            ox, oz = wx - INWARD[s][0], wz - INWARD[s][1]
            if k >= 1:                                              # la jardiniere : au rez-de-chaussee elle gênerait le tour de la tour (la tete passerait dedans)
                put(ox, yk + 2, oz, p["sb"]("top")) if (ox, yk + 2, oz) not in blocs else None
                pot(ox, yk + 3, oz, k + s)
            cnt_t["fenetres"] += 1
    cnt_t["niveaux"] += 1


def devanture(nom_, t):
    """Le store, l'enseigne, les lanternes et les pots devant la porte d'une tour."""
    T = ILES[nom_]["T"]
    th = THEMES[nom_]
    s = SIDE_IDX[t["entree"]]
    cx, cz, hs = t["cx"], t["cz"], t["hs"]
    ax = INWARD[s]                                                  # vers l'interieur ; l'exterieur est l'oppose
    ox, oz = -ax[0], -ax[1]
    px, pz = (0, 1) if ax[0] != 0 else (1, 0)                       # le long du mur
    mx, mz = cx - ax[0] * (hs + 1), cz - ax[1] * (hs + 1)           # la case du mur au milieu de la porte
    for o in range(-2, 3):                                         # le store : une seule rangee, pour laisser le parvis degage (on y atterrit en vol)
        c = (mx + ox + px * o, T + 4, mz + oz + pz * o)
        pose(c, S(f"{th['c1'] if o % 2 else th['c2']}_wool"))
    for o in (-2, 2):
        pose((mx + ox + px * o, T + 3, mz + oz + pz * o), S("lantern", hanging=True, waterlogged=False))
    for o in (-3, 3):
        c = (mx + ox + px * o, T + 1, mz + oz + pz * o)
        efface_herbe(*c)
        pot(*c, 9)
    sx, sz = mx + ox + px * 2, mz + oz + pz * 2
    efface_herbe(sx, T + 2, sz)
    if (sx, T + 2, sz) not in blocs:
        signe(sx, T + 2, sz, NOM_VEC[(ox, oz)], (TITRES[nom_] + ["", "", "", ""])[:4])
    cnt_t["devantures"] += 1


def efface_herbe(x, y, z):
    if nom((x, y, z)) == "short_grass":
        retire(x, y, z)


def balcon(nom_, t):
    """Creneaux, lanternes aux coins, fanion ; bancs et jardinieres sur le plancher du balcon (hors des cotes de l'escalier et de la sortie)."""
    T = ILES[nom_]["T"]
    cx, cz, hs, n, hl, c0, top = t["cx"], t["cz"], t["hs"], t["n"], t["hl"], t["c0"], t["top"]
    x0, x1, z0, z1 = cx - hs, cx + hs, cz - hs, cz + hs
    th = THEMES[nom_]
    p = bois(th["w"])
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            if not (x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1)):
                continue
            if (x, top + 2, z) not in blocs or (x, top + 3, z) in blocs:
                continue
            coin = x in (x0 - 1, x1 + 1) and z in (z0 - 1, z1 + 1)
            if coin and (x, z) != (x1 + 1, z1 + 1):
                put(x, top + 3, z, LANTERNE)
            elif (x + z) % 2 == 0 and not coin:
                put(x, top + 3, z, mur(x, top + 3, z))
                cnt_t["creneaux"] += 1
    fx, fz = x1 + 1, z1 + 1
    if (fx, top + 2, fz) in blocs:
        for y in (top + 3, top + 4, top + 5):
            put(fx, y, fz, S("stone_brick_wall", up=True, north=False, south=False, east=False, west=False, waterlogged=False))
        put(fx, top + 6, fz, S(f"{th['c1']}_banner", rotation=0))
    arr = (n - 1 + c0) % 4
    L = [s for s in range(4) if s != arr and s != SIDE_IDX[t["sortie"]]]
    w = top + 1
    for s in L[:2]:                                                 # un banc le long du parapet
        cs = {0: [(x0, z) for z in range(z0 + 1, z1)], 1: [(x, z1) for x in range(x0 + 1, x1)], 2: [(x1, z) for z in range(z0 + 1, z1)], 3: [(x, z0) for x in range(x0 + 1, x1)]}[s]
        m = len(cs) // 2
        for i in (m - 1, m, m + 1):
            pose((cs[i][0], w, cs[i][1]), p["st"](VERS_MUR[s]))
    for coin, ss in {(x0, z0): (0, 3), (x0, z1): (0, 1), (x1, z1): (2, 1), (x1, z0): (2, 3)}.items():
        if all(s in L for s in ss) and (coin[0], w, coin[1]) not in blocs:
            pose((coin[0], w, coin[1]), p["sb"]("top"))
            pot(coin[0], w + 1, coin[1], 3)
    if nom_ == "AI":
        pose((cx, w, cz), S("copper_block"))
        pose((cx, w + 1, cz), S("lightning_rod", facing="up", powered=False, waterlogged=False))


for nom_, ile_ in ILES.items():
    if nom_ not in ROLES:
        continue
    for t_ in ile_["tours"]:
        th_ = THEMES[nom_]
        for k_ in range(t_["n"]):
            decorer_niveau(nom_, t_, k_, th_["c1"], th_["c2"], ROLES[nom_][k_ % len(ROLES[nom_])])
        decorer_niveau(nom_, t_, t_["n"], th_["c1"], th_["c2"], "terrasse")
        devanture(nom_, t_)
        balcon(nom_, t_)
print("[3b] tours decorees :", dict(cnt_t), "| blocs", len(blocs), flush=True)

# ====================================================================== la ville : maisons, etals, arbres, bancs, puits, fleurs, sur les grandes iles
RES = set()


def reserver(x, z, m=3):
    for dx in range(-m, m + 1):
        for dz in range(-m, m + 1):
            RES.add((x + dx, z + dz))


for v_ in POINTS.values():
    reserver(v_[0], v_[2], 2)
for p_ in PORTES.values():
    for c_ in p_["cases"]:
        reserver(c_[0], c_[2], 1)
    for lv_ in p_["leviers"]:
        reserver(lv_[0], lv_[2], 2)
for c_ in list(LEV_G.values()) + [LEV_RAPPEL, LEV_LW, START_L, ALTAR, LEV_RETOUR] + [e_["levier"] for e_ in ECLATS.values()] + list(BOUTONS_G.values()) + list(BOUTONS_L.values()):
    reserver(c_[0], c_[2], 2 if c_ != ALTAR else 4)
for c_ in CACHEES.values():
    for cc_ in c_["cap"]:
        reserver(cc_[0], cc_[2], 2)
    reserver(c_["bouton"][0], c_["bouton"][2], 2)
for ile_ in ILES.values():
    for t_ in ile_["tours"]:
        reserver(t_["cx"], t_["cz"], t_["hs"] + 2)
        ex_, ez_ = INWARD[SIDE_IDX[t_["entree"]]]
        reserver(t_["cx"] - ex_ * (t_["hs"] + 4), t_["cz"] - ez_ * (t_["hs"] + 4), 2)                  # devant la porte : le store, l'enseigne, les pots
reserver(cxd, czd - 7, 7)
reserver(cxd + 6, czd - 3, 3)
for (px_, pz_) in PILIERS:
    reserver(px_, pz_, 2)


def libre_zone(cx, cz, T, rx, rz, h, rg=None):
    """Zone libre de rx x rz autour de (cx, cz) : du sol sur rg (par defaut toute la zone ; un toit peut deborder dans le vide) et rien de pose sur h cases de haut."""
    rg = rx if rg is None else rg
    for x in range(cx - rx, cx + rx + 1):
        for z in range(cz - rz, cz + rz + 1):
            if (x, z) in RES:
                return False
            if abs(x - cx) <= rg and abs(z - cz) <= rg and ((x, T, z) not in blocs or nom((x, T, z)) in ("sea_lantern",)):
                return False
            for y in range(T + 1, T + h + 1):
                if (x, y, z) in blocs and nom((x, y, z)) not in ("short_grass",) + tuple(FLEURS):
                    return False
    return True


def vide_zone(cx, cz, T, rx, rz, h):
    for x in range(cx - rx, cx + rx + 1):
        for z in range(cz - rz, cz + rz + 1):
            for y in range(T + 1, T + h + 1):
                if (x, y, z) in blocs and nom((x, y, z)) in ("short_grass",) + tuple(FLEURS):
                    retire(x, y, z)
            RES.add((x, z))


def repere(cx, cz, face):
    return {"south": lambda u, v: (cx + u, cz + v), "north": lambda u, v: (cx - u, cz - v), "east": lambda u, v: (cx + v, cz - u), "west": lambda u, v: (cx - v, cz + u)}[face]


def vers(rot, du, dv):
    a, b = rot(0, 0), rot(du, dv)
    return NOM_VEC[(b[0] - a[0], b[1] - a[1])]


def maison(cx, cz, T, face, th, num):
    p = bois(th["w"])
    rot = repere(cx, cz, face)
    c1 = th["c1"]
    for u in range(-2, 3):
        for v in range(-2, 3):
            x, z = rot(u, v)
            put(x, T, z, p["pl"])
            bord = abs(u) == 2 or abs(v) == 2
            for y in range(T + 1, T + 4):
                if bord:
                    put(x, y, z, p["sl"] if abs(u) == 2 and abs(v) == 2 else p["pl"])
                else:
                    retire(x, y, z)
    for (u, v) in ((0, 2),):                                        # la porte : un passage de deux cases
        x, z = rot(u, v)
        retire(x, T + 1, z)
        retire(x, T + 2, z)
    for (u, v) in ((2, 0), (-2, 0), (0, -2)):                        # trois fenetres
        x, z = rot(u, v)
        put(x, T + 2, z, S("glass_pane"))
    for j, (vv, hh) in enumerate(((3, 0), (2, 1), (1, 2), (0, 3))):  # le toit de pente, faitage dans le sens de la largeur
        for sgn in ((1, -1) if vv else (1,)):
            for u in range(-3, 4):
                x, z = rot(u, sgn * vv)
                y = T + 4 + hh
                if j == 3:
                    put(x, y, z, p["sb"]("bottom"))
                else:
                    put(x, y, z, p["st"](vers(rot, 0, -sgn)))
                if abs(u) <= 2:
                    for yy in range(T + 4, y):
                        put(x, yy, z, p["pl"])
    x, z = rot(1, -1)                                               # la cheminee
    for y in range(T + 4, T + 9):
        put(x, y, z, S("bricks"))
    wx, wz = rot(0, 0)
    put(wx, T + 3, wz, S("lantern", hanging=True, waterlogged=False))
    for u in (-1, 1):                                               # bibliotheque et table
        for y in (T + 1, T + 2):
            bx_, bz_ = rot(u, -1)
            put(bx_, y, bz_, ETAGERE)
    tx, tz = rot(0, -1)
    put(tx, T + 1, tz, p["sb"]("top"))
    put(tx, T + 2, tz, S("candle", candles=3, lit=True, waterlogged=False))
    cx_, cz_ = rot(0, 0)
    put(cx_, T + 1, cz_, p["st"](vers(rot, 0, 1)))
    for u in (-1, 0, 1):
        x, z = rot(u, 1)
        put(x, T + 1, z, S(f"{c1}_carpet"))
    for (u, v) in ((-1, 3), (1, 3)):                                # des pots de chaque cote de la porte
        x, z = rot(u, v)
        efface_herbe(x, T + 1, z)
        pot(x, T + 1, z, num)
    cnt_t["maisons"] += 1


def etal(cx, cz, T, face, th):
    p = bois(th["w"])
    rot = repere(cx, cz, face)
    for (u, v) in ((-2, -1), (2, -1), (-2, 1), (2, 1)):
        x, z = rot(u, v)
        for y in (T + 1, T + 2, T + 3):
            put(x, y, z, p["fe"])
    for u in range(-2, 3):
        x, z = rot(u, 1)
        put(x, T + 1, z, p["sb"]("bottom"))
        put(x, T + 2, z, S(["melon", "pumpkin", "hay_block", "red_mushroom_block", "melon"][(u + 2) % 5]) if u % 2 == 0 else S("candle", candles=3, lit=True, waterlogged=False))
        for v in (-1, 0, 1):
            xx, zz = rot(u, v)
            put(xx, T + 4, zz, S(f"{th['c1'] if u % 2 else th['c2']}_wool"))
    x, z = rot(0, 0)
    put(x, T + 3, z, S("lantern", hanging=True, waterlogged=False))
    cnt_t["etals"] += 1


def arbre(cx, cz, T, essence):
    for y in range(T + 1, T + 5):
        put(cx, y, cz, S(f"{essence}_log", axis="y"))
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for dy in range(-1, 3):
                if dx * dx + dz * dz + (dy * 1.3) ** 2 <= 5.2 and (dx, dz) != (0, 0) or (dy >= 1 and (dx, dz) == (0, 0)):
                    c = (cx + dx, T + 4 + dy, cz + dz)
                    if c not in blocs:
                        put(*c, S(f"{essence}_leaves", distance=1, persistent=True, waterlogged=False))
    cnt_t["arbres"] += 1


def banc_ext(cx, cz, T, face, p):
    rot = repere(cx, cz, face)
    for u in (-1, 0, 1):
        x, z = rot(u, 0)
        put(x, T + 1, z, p["st"](vers(rot, 0, -1)))


def puits(cx, cz, T, th):
    p = bois(th["w"])
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if (dx, dz) == (0, 0):
                put(cx, T, cz, S("light_blue_stained_glass"))
            else:
                put(cx + dx, T + 1, cz + dz, S("stone_bricks"))
    for (dx, dz) in ((-1, 0), (1, 0)):
        for y in (T + 2, T + 3):
            put(cx + dx, y, cz + dz, p["fe"])
    for dx in (-1, 0, 1):
        put(cx + dx, T + 4, cz, p["sb"]("bottom"))
    cnt_t["puits"] += 1


def face_vers_centre(x, z, cx, cz):
    dx, dz = cx - x, cz - z
    if abs(dx) > abs(dz):
        return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"


PLAN_VILLE = {"D": dict(maisons=3, etals=1, arbres=2, bancs=2, puits=0), "AI": dict(maisons=3, etals=1, arbres=3, bancs=2, puits=1),
              "MV": dict(maisons=3, etals=2, arbres=2, bancs=2, puits=1), "CI": dict(maisons=4, etals=1, arbres=3, bancs=3, puits=1),
              "BE": dict(maisons=2, etals=1, arbres=2, bancs=2, puits=0), "BO": dict(maisons=0, etals=2, arbres=4, bancs=3, puits=0)}
for nom_, plan_ in PLAN_VILLE.items():
    ile_ = ILES[nom_]
    cx, cz, r, T = ile_["cx"], ile_["cz"], ile_["r"], ile_["T"]
    th = THEMES[nom_]
    cands = []
    for rf in (0.66, 0.5, 0.8, 0.38, 0.58, 0.73):
        for i in range(24):
            a = (i * 2 * math.pi / 24) + rf * 3
            cands.append((round(cx + rf * r * math.cos(a)), round(cz + rf * r * math.sin(a))))
    restants = dict(plan_)
    for (x, z) in cands:                                           # d'abord les maisons, ensuite le reste
        if restants.get("maisons", 0) and libre_zone(x, z, T, 3, 3, 9, 2):
            vide_zone(x, z, T, 3, 3, 9)
            maison(x, z, T, face_vers_centre(x, z, cx, cz), th, restants["maisons"])
            restants["maisons"] -= 1
    for (x, z) in cands:
        face = face_vers_centre(x, z, cx, cz)
        if restants.get("etals", 0) and libre_zone(x, z, T, 3, 2, 5):
            vide_zone(x, z, T, 3, 2, 5)
            etal(x, z, T, face, th)
            restants["etals"] -= 1
        elif restants.get("puits", 0) and libre_zone(x, z, T, 2, 2, 5):
            vide_zone(x, z, T, 2, 2, 5)
            puits(x, z, T, th)
            restants["puits"] -= 1
        elif restants.get("arbres", 0) and libre_zone(x, z, T, 3, 3, 8):
            vide_zone(x, z, T, 3, 3, 8)
            arbre(x, z, T, ["oak", "birch", "cherry"][restants["arbres"] % 3])
            restants["arbres"] -= 1
        elif restants.get("bancs", 0) and libre_zone(x, z, T, 2, 1, 2):
            vide_zone(x, z, T, 2, 1, 2)
            banc_ext(x, z, T, face, bois(th["w"]))
            restants["bancs"] -= 1
nf = 0
for (x, y, z), s in list(blocs.items()):                            # des fleurs dans l'herbe, sur toutes les iles
    if s == S("short_grass") and h01(x, y, z, 91) < .16:
        put(x, y, z, S(FLEURS[int(h01(x, y, z, 92) * len(FLEURS))]))
        nf += 1
print("[3c] ville :", {k_: v_ for k_, v_ in cnt_t.items() if k_ in ("maisons", "etals", "arbres", "puits")}, "| fleurs", nf, "| blocs", len(blocs), flush=True)
