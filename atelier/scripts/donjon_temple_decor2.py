# [claude-build]
"""donjon_temple_decor2.py - sols, plafonds, piliers, mobilier et vegetation envahissante (temple de jungle abandonne). Voir donjon_temple_decor.py."""

FLOORPAINT = PEINTURE | {"rooted_dirt"}
FEUIL = S("jungle_leaves", persistent=True, distance=1, waterlogged=False)
AZAL = S("flowering_azalea")


def vigne(face):
    return S("vine", **{k: (k == face) for k in ("north", "south", "east", "west")}, up=False)


def sol_motif(nom_, b, fl, pal_n):
    x0, x1, y0, y1, z0, z1 = b
    pal = PALETTES[pal_n]
    cx, cz = (x0 + x1) / 2, (z0 + z1) / 2
    n = 0
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            s = blocs.get((x, fl, z))
            if s is None or s[0].split(":")[1] not in FLOORPAINT:
                continue
            d = min(x - x0, x1 - x, z - z0, z1 - z)
            r = math.hypot(x + .5 - cx, z + .5 - cz)
            v = bruit(x, z, 4, 421)
            if pal_n == "serpent" and abs(z + .5 - ((z0 + z1) / 2 + 10 * math.sin((x - x0) / 4.3))) <= 1.3 and d >= 2:
                b_ = S("mud_bricks") if (x // 2) % 2 else S("packed_mud")          # le serpent de boue qui traverse la salle
            elif d == 0:
                b_ = pal["bande"]
            elif d == 1:
                b_ = pal["base"]
            elif d <= 3:
                b_ = SB if (x + z) % 2 else MSB
            elif r <= 2.6 and pal_n in ("temple", "boss", "eau"):
                b_ = CHB if r > 1.4 else pal["accent"]
            elif 3.6 <= r <= 4.4 and pal_n in ("temple", "boss", "eau"):
                b_ = pal["bande"]
            elif ((x - cx) + (z - cz)) % 8 < 1 or ((x - cx) - (z - cz)) % 8 < 1:
                b_ = pal["bande"] if h01(x, z, 0, 422) > .35 else CSB
            else:
                b_ = MOUSSE if v > .62 else CSB if v < .2 else pick(pal["mur"], bruit(x, z, 5, 423))   # envahi par la mousse, fissure par les racines
            put(x, fl, z, b_)
            n += 1
    compte["sol"] += n


def plafond(nom_, b, fl, pal_n):
    x0, x1, y0, y1, z0, z1 = b
    for x in range(x0 + 3, x1 - 2, 8):                             # vieilles poutres de jungle, certaines cassees
        for z in range(z0, z1 + 1):
            if h01(x, z, 0, 424) > .12 and pose(x, y1, z, S("stripped_jungle_log", axis="z")):
                compte["poutres"] += 1
    for x in range(x0 + 7, x1 - 4, 14):                            # lustres rouilles : une chaine, parfois sa lanterne
        for z in range(z0 + 4, z1 - 3, 13):
            if libre(x, y1, z) and libre(x, y1 - 1, z) and libre(x, y1 - 2, z) and libre(x, y1 - 3, z):
                put(x, y1, z, CHAINE)
                put(x, y1 - 1, z, CHAINE)
                put(x, y1 - 2, z, CHAINE)
                if h01(x, z, 1, 425) < .6:
                    put(x, y1 - 3, z, S("lantern", hanging=True, waterlogged=False))
                compte["lustres"] += 1


def pilastre(x, z, mur, fl, y1, pal):
    cases = [(x, y, z) for y in range(fl + 1, y1 + 1)]
    if not all(libre(*c) for c in cases):
        return False
    dx, dz = {"east": (1, 0), "west": (-1, 0), "south": (0, 1), "north": (0, -1)}[mur]
    if not all(plein(x + dx, y, z + dz) for y in range(fl + 1, y1 + 1)):
        return False
    haut = y1 - int(3 * h01(x, z, 2, 426) * (h01(x, z, 3, 427) < .3))   # certains pilastres sont cassés
    for (cx_, y, cz_) in cases:
        if y > haut:
            break
        if y == fl + 1:
            s = stairs("stone_brick_stairs", mur)
        elif y == haut and haut == y1:
            s = stairs("stone_brick_stairs", mur, True)
        elif (y - fl) % 5 == 0:
            s = pal["bande"]
        else:
            s = pick([(MSB, 4), (CSB, 2), (MCB, 2), (SB, 1)], h01(x, y, z, 428))
        put(cx_, y, cz_, s)
    compte["pilastres"] += 1
    ix, iz = x - dx, z - dz
    if h01(x, z, 4, 429) < .4 and libre(ix, fl + 4, iz) and plein(x, fl + 4, z):
        put(ix, fl + 4, iz, wall_torch(OPP[mur]))
    return True


def pilastres(nom_, b, fl, pal_n, espace=6):
    x0, x1, y0, y1, z0, z1 = b
    pal = PALETTES[pal_n]
    for i in range(x0 + 4, x1 - 3, espace):
        pilastre(i, z0, "north", fl, y1, pal)
        pilastre(i + 3, z1, "south", fl, y1, pal)
    for j in range(z0 + 4, z1 - 3, espace):
        pilastre(x0, j, "west", fl, y1, pal)
        pilastre(x1, j + 3, "east", fl, y1, pal)


def face_vers(x, z, b):
    x0, x1, y0, y1, z0, z1 = b
    dx, dz = (x0 + x1) / 2 - x, (z0 + z1) / 2 - z
    return ("east" if dx > 0 else "west") if abs(dx) > abs(dz) else ("south" if dz > 0 else "north")


def totem(x, y, z, face):
    if not all(libre(x, y + k, z) for k in range(6)):
        return False
    pile = [S("chiseled_stone_bricks"), S("carved_pumpkin", facing=face), S("stripped_jungle_log", axis="y"), S("mossy_stone_bricks"),
            S("carved_pumpkin", facing=face), slab("jungle_slab")]
    for k, s in enumerate(pile):
        put(x, y + k, z, s)
    for ax in (1, -1):                                             # cornes
        if libre(x + ax, y + 4, z):
            put(x + ax, y + 4, z, stairs("jungle_stairs", "east" if ax > 0 else "west", True))
    for (ax, az, f) in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):   # lianes qui l'envahissent
        for k in range(1, 5):
            if h01(x + ax, y + k, z + az, 460) < .55 and libre(x + ax, y + k, z + az):
                put(x + ax, y + k, z + az, vigne(f))
    compte["totems"] += 1
    return True


def statue(x, y, z, face):
    if not all(libre(x, y + k, z) for k in range(5)):
        return False
    put(x, y, z, S("mossy_stone_bricks"))
    put(x, y + 1, z, mur_poteau("mossy_stone_brick_wall"))
    put(x, y + 2, z, mur_poteau("stone_brick_wall"))
    put(x, y + 3, z, CSB if h01(x, y, z, 7) > .5 else CHB)
    put(x, y + 4, z, S("chiseled_tuff_bricks") if h01(x, y, z, 8) > .5 else CHB)
    for (ax, az, f) in ((1, 0, "east"), (-1, 0, "west")):
        if libre(x + ax, y + 3, z + az) and h01(x + ax, y, z, 9) < .7:
            put(x + ax, y + 3, z + az, stairs("stone_brick_stairs", f, True))
    for (ax, az, f) in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
        for k in range(1, 4):
            if h01(x + ax, y + k, z + az, 461) < .5 and libre(x + ax, y + k, z + az):
                put(x + ax, y + k, z + az, vigne(f))
    compte["statues"] += 1
    return True


def brasero(x, y, z):
    if not all(libre(x, y + k, z) for k in range(3)):
        return False
    put(x, y, z, CHB)
    put(x, y + 1, z, mur_poteau("mossy_stone_brick_wall"))
    put(x, y + 2, z, S("lantern", hanging=False, waterlogged=False) if h01(x, z, 5, 430) < .45 else S("candle", candles=2, lit=False, waterlogged=False))
    compte["braseros"] += 1
    return True


def gravats(nom_, b, fl, densite):
    x0, x1, y0, y1, z0, z1 = b
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if min(x - x0, x1 - x, z - z0, z1 - z) > 3:
                continue
            v = h01(x, fl, z, 430)
            if v < densite and libre(x, fl + 1, z) and plein(x, fl, z):
                t = int(v / densite * 5)
                put(x, fl + 1, z, [slab("mossy_cobblestone_slab"), stairs("stone_brick_stairs", ["north", "south", "east", "west"][int(h01(x, z, 1, 431) * 4)]),
                                   S("mossy_cobblestone"), slab("stone_brick_slab"), S("cobblestone")][t])
                compte["gravats"] += 1


PLANTES = [(S("fern"), 6), (S("short_grass"), 7), (S("poppy"), 1), (S("dandelion"), 1), (S("azure_bluet"), 1), (S("lily_of_the_valley"), 1),
           (S("brown_mushroom"), 1), (S("red_mushroom"), 1), (S("pink_petals", flower_amount=3, facing="north"), 1), (S("moss_carpet"), 4)]


def vegetation(nom_, b, fl, densite):
    x0, x1, y0, y1, z0, z1 = b
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not (libre(x, fl + 1, z) and plein(x, fl, z)):
                continue
            v = h01(x, fl, z, 440)
            if v < densite:
                w = h01(x, z, 2, 441)
                if w < .25 and libre(x, fl + 2, z):
                    put(x, fl + 1, z, S("tall_grass", half="lower"))
                    put(x, fl + 2, z, S("tall_grass", half="upper"))
                elif w < .3 and not RES[x, fl + 1, z]:
                    put(x, fl + 1, z, AZAL)
                else:
                    put(x, fl + 1, z, pick(PLANTES, h01(x, z, 3, 442)))
                compte["plantes"] += 1
    for x in range(x0, x1 + 1):                                    # fleurs de plafond
        for z in range(z0, z1 + 1):
            if libre(x, y1, z) and plein(x, y1 + 1, z) and h01(x, z, 4, 443) < .02:
                put(x, y1, z, S("spore_blossom"))
                compte["plantes"] += 1


def lianes_murs(nom_, b, fl, densite):
    """Lianes qui coulent le long des parois (et du pied des pilastres)."""
    x0, x1, y0, y1, z0, z1 = b
    murs = [(x0, zz, "west", 0, zz) for zz in range(z0, z1 + 1)] + [(x1, zz, "east", 0, zz) for zz in range(z0, z1 + 1)] \
        + [(xx, z0, "north", 1, xx) for xx in range(x0, x1 + 1)] + [(xx, z1, "south", 1, xx) for xx in range(x0, x1 + 1)]
    for (x, z, face, _, i) in murs:
        if h01(x, z, 6, 450) >= densite:
            continue
        top = y1 - int(h01(x, z, 7, 451) * 3)
        for k in range(2 + int(h01(x, z, 8, 452) * 7)):
            y = top - k
            dx, dz = {"west": (-1, 0), "east": (1, 0), "north": (0, -1), "south": (0, 1)}[face]
            if y <= fl or not libre(x, y, z) or not plein(x + dx, y, z + dz):
                break
            put(x, y, z, vigne(face))
            compte["lianes"] += 1


def racines(nom_, b, p):
    """Grosses racines qui pendent de la voute."""
    x0, x1, y0, y1, z0, z1 = b
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if libre(x, y1, z) and plein(x, y1 + 1, z) and h01(x, z, 9, 453) < p:
                long_ = 3 + int(h01(x, z, 10, 454) * 6)
                for k in range(long_):
                    if not libre(x, y1 - k, z):
                        break
                    put(x, y1 - k, z, RAC if k == 0 else MRAC if k < long_ - 1 else RACP)
                compte["racines"] += 1


def feuillage(nom_, b, p, fl):
    """Touffes de feuilles qui percent le plafond et les coins."""
    x0, x1, y0, y1, z0, z1 = b
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if libre(x, y1, z) and plein(x, y1 + 1, z) and h01(x, z, 11, 455) < p:
                r = 1 + int(h01(x, z, 12, 456) * 2)
                for dx in range(-r, r + 1):
                    for dz in range(-r, r + 1):
                        for dy in range(0, 2):
                            if abs(dx) + abs(dz) + dy <= r and h01(x + dx, y1 - dy, z + dz, 457) < .85 and libre(x + dx, y1 - dy, z + dz) and y1 - dy > fl + 6:
                                put(x + dx, y1 - dy, z + dz, FEUIL)
                compte["feuillage"] += 1


def buissons(nom_, b, fl, densite):
    """Buissons de feuilles et d'azalees qui poussent contre les murs et les angles."""
    x0, x1, y0, y1, z0, z1 = b
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if min(x - x0, x1 - x, z - z0, z1 - z) <= 1 and h01(x, z, 13, 458) < densite and libre(x, fl + 1, z) and plein(x, fl, z):
                put(x, fl + 1, z, FEUIL)
                if libre(x, fl + 2, z) and h01(x, z, 14, 459) < .5:
                    put(x, fl + 2, z, FEUIL)
                compte["buissons"] += 1


def stalactites(b, p):
    x0, x1, y0, y1, z0, z1 = b
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            col = np.nonzero(C[x, y0:y1 + 1, z])[0]
            if len(col) and h01(x, z, 6, 450) < p:
                top = y0 + int(col.max())
                if libre(x, top, z) and plein(x, top + 1, z):
                    put(x, top, z, S("pointed_dripstone", thickness="tip", vertical_direction="down", waterlogged=False))
                    compte["stalactites"] += 1


def arbre(x, y, z, h):
    """Grand arbre de jungle qui a pousse dans la cour : tronc, racines, couronne de feuilles."""
    if not all(libre(x, y + k, z) for k in range(h)):
        return False
    for k in range(h):
        put(x, y + k, z, S("jungle_log", axis="y"))
    for (ax, az) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if libre(x + ax, y, z + az):
            put(x + ax, y, z + az, MRAC)
        if libre(x + ax, y + 1, z + az) and h01(x + ax, y, z + az, 462) < .6:
            put(x + ax, y + 1, z + az, MRAC)
    top = y + h
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            for dy in range(-2, 3):
                if dx * dx + dz * dz + dy * dy * 2 <= 9 and h01(x + dx, top + dy, z + dz, 463) < .9 and libre(x + dx, top + dy, z + dz):
                    put(x + dx, top + dy, z + dz, FEUIL)
    compte["arbres"] += 1
    return True


# ---------------------------------------------------------------- application
SALLE = {n_: (b, fl, pn) for (n_, b, fl, pn, op) in SALLES}
FLS = {"miroirs": 62, "crypte": 46, "boss": None}


def fl_de(n_):
    return SALLE[n_][1] if SALLE[n_][1] is not None else FLS[n_]


for n_, (b, fl, pn) in SALLE.items():
    if n_ != "boss" and fl_de(n_) is not None:
        sol_motif(n_, b, fl_de(n_), pn)
for n_ in ("vestibule", "serpents", "miroirs", "colonnes", "antichambre", "sortie"):
    plafond(n_, SALLE[n_][0], fl_de(n_), SALLE[n_][2])
for n_ in ("vestibule", "serpents", "colonnes", "miroirs"):
    pilastres(n_, SALLE[n_][0], fl_de(n_), SALLE[n_][2], 6)
# totems, statues et braseros
for (n_, pts) in (("vestibule", [(41, 33), (89, 33), (41, 15), (89, 15)]), ("serpents", [(97, 9), (121, 9), (121, 39), (97, 39)]),
                  ("colonnes", [(31, 58), (61, 58), (31, 89), (61, 89)]), ("parvis", [(55, 74), (75, 74)])):
    for (x, z) in pts:
        totem(x, fl_de(n_) + 1, z, face_vers(x, z, SALLE[n_][0]))
for (x, z) in ((43, 33), (87, 33), (49, 33), (81, 33), (43, 15), (87, 15)):
    statue(x, 63, z, face_vers(x, z, SALLE["vestibule"][0]))
for (x, z) in ((33, 62), (59, 62), (33, 88), (59, 88)):
    statue(x, 41, z, face_vers(x, z, SALLE["colonnes"][0]))
for (n_, pts) in (("vestibule", [(52, 33), (78, 33), (52, 15), (78, 15), (58, 22), (72, 22)]), ("colonnes", [(38, 72), (54, 72), (46, 60)]),
                  ("serpents", [(100, 24), (118, 24)]), ("parvis", [(60, 60), (70, 60), (60, 70), (70, 70)])):
    for (x, z) in pts:
        brasero(x, fl_de(n_) + 1, z)
for n_, d in (("vestibule", .07), ("serpents", .07), ("colonnes", .08), ("parvis", .1), ("miroirs", .14), ("crypte", .14), ("sortie", .05)):
    gravats(n_, SALLE[n_][0], fl_de(n_), d)
for n_, d in (("vestibule", .16), ("serpents", .2), ("colonnes", .12), ("parvis", .34), ("miroirs", .26), ("crypte", .1), ("sortie", .08), ("antichambre", .04)):
    vegetation(n_, SALLE[n_][0], fl_de(n_), d)
for n_, d in (("vestibule", .14), ("serpents", .16), ("colonnes", .13), ("miroirs", .2), ("parvis", .22), ("antichambre", .1), ("sortie", .1), ("crypte", .1)):
    lianes_murs(n_, SALLE[n_][0], fl_de(n_), d)
for n_, p_ in (("vestibule", .012), ("serpents", .012), ("colonnes", .01), ("miroirs", .016), ("antichambre", .01), ("sortie", .01)):
    racines(n_, SALLE[n_][0], p_)
    feuillage(n_, SALLE[n_][0], p_ * 0.8, fl_de(n_))
for n_, d in (("vestibule", .12), ("serpents", .14), ("colonnes", .1), ("miroirs", .16), ("parvis", .2), ("sortie", .08)):
    buissons(n_, SALLE[n_][0], fl_de(n_), d)
for (x, z, h) in ((57, 57, 11), (73, 57, 13), (57, 73, 10), (73, 73, 12), (65, 75, 9)):
    arbre(x, 83, z, h)
for (b_, p_) in (((72, 96, 41, 73, 46, 68), .05), ((97, 124, 11, 52, 58, 100), .04), ((98, 122, 27, 56, 104, 124), .05), ((30, 62, 41, 52, 57, 90), .02)):
    stalactites(b_, p_)
for z in range(94, 100):                                           # tapis de mousse entre les statues de l'antichambre
    for x in range(45, 48):
        pose(x, 41, z, S("moss_carpet"))
print("   decor :", dict(compte), flush=True)
