# [claude-build]
"""donjon_temple_decor3.py - decor de l'aile du boss, meme direction artistique que le reste : un immense temple de jungle abandonne
(nervures de pierre moussue, racines geantes, lianes, feuillage, arbres qui poussent sur les plateformes). Voir donjon_temple_decor.py."""

cb = Counter()
FEU = S("jungle_leaves", persistent=True, distance=1, waterlogged=False)
STONE_ROCK = [(S("stone"), 3), (S("andesite"), 2), (MCB, 3), (S("cobblestone"), 1)]
# --- nervures de la voute : pierre moussue et fissuree, quelques lanternes encore accrochees, lianes et racines qui pendent
for z in (141, 153, 167, 181, 193):
    for x in range(HX0, HX1 + 1):
        t = VOUTE[x]
        for k in (0, 1):
            if h01(x, t - k, z, 480) > .08 and pose(x, t - k, z, pick([(MSB, 4), (CSB, 3), (SB, 2), (MCB, 2)], h01(x, k, z, 481))):
                cb["nervures"] += 1
        if x % 11 == 4 and libre(x, t - 2, z) and plein(x, t - 1, z):
            put(x, t - 2, z, S("lantern", hanging=True, waterlogged=False))
            cb["lanternes"] += 1
        if x % 5 == 2 and plein(x, t - 1, z):                      # lianes qui pendent des nervures
            for k in range(2, 3 + int(h01(x, z, 482) * 11)):
                if not libre(x, t - k, z):
                    break
                put(x, t - k, z, S("cave_vines_plant", berries=h01(x, k, z, 483) < .35))
            if libre(x, t - k, z):
                put(x, t - k, z, S("cave_vines", age=25, berries=True))
            cb["lianes"] += 1
for x in (HX0 + 12, 47, HX1 - 12):                                 # grandes nervures dans l'autre sens, au sommet
    for z in range(HZ0, HZ1 + 1):
        t = VOUTE[x]
        if h01(x, z, 0, 484) > .1 and pose(x, t, z, pick([(MSB, 4), (CSB, 3), (MCB, 2)], h01(x, z, 1, 485))):
            cb["nervures"] += 1
# --- racines geantes qui pendent de la voute, en grappes
for x in range(HX0 + 2, HX1 - 1):
    for z in range(HZ0 + 2, HZ1 - 1):
        if h01(x, z, 2, 486) < .012:
            t = VOUTE[x]
            long_ = 6 + int(h01(x, z, 3, 487) * 14)
            for dx, dz in ((0, 0), (1, 0), (0, 1), (-1, 0)):
                for k in range(long_ - (abs(dx) + abs(dz)) * 2):
                    p_ = (x + dx, t - k, z + dz)
                    if not libre(*p_):
                        break
                    put(*p_, RAC if k == 0 else MRAC)
            cb["racines"] += 1
# --- contreforts de pierre moussue contre les grandes parois, du fond jusqu'a la voute, envahis de lianes
for z in range(HZ0 + 8, HZ1 - 6, 12):
    for (x, f) in ((HX0, "east"), (HX1 - 1, "west")):
        for dz in range(3):
            for dx in range(2):
                for y in range(8, VOUTE[x + dx] - 3):
                    if h01(x + dx, y, z + dz, 488) > .04 and pose(x + dx, y, z + dz, CHB if (y % 10 == 5) else pick([(MSB, 4), (CSB, 3), (MCB, 2), (SB, 1)], h01(x + dx, y, z + dz, 489))):
                        cb["contreforts"] += 1
# --- lianes sur les parois ouest, est et sud du hall
for y0_ in range(30, 86, 7):
    for z in range(HZ0 + 1, HZ1, 3):
        for (x, f) in ((HX0, "west"), (HX1, "east")):
            if h01(x + y0_, z, 4, 490) < .34:
                for k in range(2 + int(h01(x, z + y0_, 5, 491) * 8)):
                    if not libre(x, y0_ - k, z) or not plein(x + (-1 if f == "west" else 1), y0_ - k, z):
                        break
                    put(x, y0_ - k, z, vigne(f))
                    cb["lianes"] += 1
# --- stalagmites de roche moussue qui sortent de la lave (loin des plateformes), jamais dans le noir complet
for x in range(HX0 + 2, HX1 - 1):
    for z in range(HZ0 + 2, HZ1 - 1):
        if h01(x, z, 0, 460) > .0055 or not (nom((x, 6, z)) == "lava" and nom((x, 7, z)) == "lava"):
            continue
        if math.hypot(x - GX, z - GZ) <= G_RAYON_BRAS + 3:
            continue                                                # pas de roche dans le volume du Gardien
        h_ = 3 + int(h01(x, z, 1, 461) * 11)
        for y in range(6, 8 + h_):
            r_ = 1 if y < 8 + h_ // 2 else 0
            for dx in range(-r_, r_ + 1):
                for dz in range(-r_, r_ + 1):
                    p_ = (x + dx, y, z + dz)
                    if abs(dx) + abs(dz) <= r_ and ((p_ in blocs and nom(p_) == "lava") or (C[p_] and p_ not in blocs)):
                        put(*p_, pick(STONE_ROCK, h01(*p_, 3)))
        if libre(x, 8 + h_, z) and h_ > 5:
            put(x, 8 + h_, z, FEU if h01(x, z, 6, 492) < .5 else AZAL)
        cb["stalagmites"] += 1
# --- plateformes : bordure de pierre moussue, mousse, herbe, buissons, arbres rabougris ; racines et lianes dessous
for n_ in PLATS:
    x0, x1, z0, z1, top, rn = PLATS[n_]
    cx_, cz_ = CENTRES[n_]
    for (x, z) in PC[n_]:
        bord = any((x + dx, z + dz) not in PC[n_] for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        d = math.hypot(x - cx_, z - cz_)
        s = blocs.get((x, top, z))
        if s is None or s[0].split(":")[1] not in PEINTURE:
            continue
        v = bruit(x, z, 3, 471)
        if bord:
            put(x, top, z, CHB if h01(x, z, 0, 470) < .2 else pick([(MCB, 3), (MSB, 3), (CSB, 2)], h01(x, z, 1, 474)))
        elif d <= 1.5:
            put(x, top, z, CHB)
        elif 2.5 <= d <= 3.5 and n_ != "D":
            put(x, top, z, MSB if (x + z) % 2 else CSB)
        elif n_ == "D" and abs(x - 47) <= 1 and z <= 144:
            put(x, top, z, MSB)
        else:
            put(x, top, z, MOUSSE if v > .6 else CSB if v < .22 else pick([(MSB, 4), (SB, 2), (MCB, 2)], h01(x, z, 2, 475)))
        if not bord and d > 3.6 and libre(x, top + 1, z):           # la jungle reprend ses droits
            w = h01(x, z, 3, 476)
            if w < .12:
                put(x, top + 1, z, S("fern"))
            elif w < .2:
                put(x, top + 1, z, S("short_grass"))
            elif w < .23:
                put(x, top + 1, z, FEU)
            elif w < .25:
                put(x, top + 1, z, AZAL)
            elif w < .3:
                put(x, top + 1, z, S("moss_carpet"))
        bas = top
        while (x, bas - 1, z) in blocs and bas > 1:
            bas -= 1
        if libre(x, bas - 1, z):
            w = h01(x, z, 4, 477)
            if w < .1:
                put(x, bas - 1, z, S("pointed_dripstone", thickness="tip", vertical_direction="down", waterlogged=False))
            elif w < .3:
                long_ = 2 + int(h01(x, z, 5, 478) * 6)
                for k in range(1, long_ + 1):
                    if not libre(x, bas - k, z):
                        break
                    put(x, bas - k, z, RACP if k == long_ else MRAC)   # les racines pendantes ne s'accrochent qu'a un bloc plein
            elif w < .4:
                put(x, bas - 1, z, FEU)
                if libre(x, bas - 2, z) and h01(x, z, 6, 479) < .5:
                    put(x, bas - 2, z, FEU)
    cb["plateformes"] += 1
# --- l'arche d'entree sur la plateforme de depart : deux colonnes moussues et un linteau, envahis de lianes
for xx in (43, 49):
    for y in range(41, 48):
        pose(xx, y, 134, CHB if y % 3 == 0 else pick([(MSB, 3), (CSB, 2), (MCB, 1)], h01(xx, y, 134, 493)))
for xx in range(43, 50):
    pose(xx, 47, 134, MSB if xx not in (43, 49) else CHB)
for xx in (42, 50):
    for y in range(41, 45):
        pose(xx, y, 135, mur_poteau("mossy_stone_brick_wall") if y < 44 else S("lantern", hanging=False, waterlogged=False))
for (xx, f) in ((43, "west"), (49, "east")):
    for y in range(41, 47):
        if h01(xx, y, 134, 494) < .7:
            pose(xx + (-1 if f == "west" else 1), y, 134, vigne("east" if f == "west" else "west"))
print("   decor boss :", dict(cb), flush=True)
