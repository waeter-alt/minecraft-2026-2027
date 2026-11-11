# [claude-build]
"""donjon_aqua_missions.py - portes, leviers, enigmes, ecluse, tunnels et salles cachees, arene du boss et sortie. Execute par donjon_aqua.py."""

LEV = lambda f: S("lever", face="wall", facing=f, powered=False)           # noqa: E731
BOUTON = lambda f: S("stone_button", face="wall", facing=f, powered=False)  # noqa: E731
BOUTON_SOL = S("stone_button", face="floor", facing="north", powered=False)
PLAQUE = S("stone_pressure_plate", powered=False)
PIERRE = S("stone_bricks")
MOUSSE = S("mossy_stone_bricks")
PRISME = S("prismarine_bricks")


def signe(x, y, z, facing, lignes, couleur="black"):
    assert all(len(l) <= 14 for l in lignes) and len(lignes) <= 4, ("panneau trop large (14 caracteres max par ligne)", lignes)
    put(x, y, z, S("oak_wall_sign", facing=facing, waterlogged=False))
    msg = [String('{"text":"' + (l or "") + '"}') for l in (list(lignes) + ["", "", "", ""])[:4]]
    face = Compound({"messages": List[String](msg), "color": String(couleur), "has_glowing_text": Byte(0)})
    NBT[(x, y, z)] = Compound({"id": String("minecraft:sign"), "is_waxed": Byte(1), "front_text": face, "back_text": face})


def creuser(x0, x1, y0, y1, z0, z1):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                blocs.pop((x, y, z), None)
    boite(x0, x1, y0, y1, z0, z1)


def pilier(x, z, y0, y1, bloc=PIERRE):
    for y in range(y0, y1 + 1):
        put(x, y, z, bloc)


def ilot(x0, x1, z0, z1, y0):
    """Ilot sec dans un bassin : pierre de y0 jusqu'au sol sec (43)."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(y0, SOL + 1):
                put(x, y, z, PRISME if y == SOL else MOUSSE)


# ---------------------------------------------------------------- S1 : le Debarcadere ; le levier de la porte est au fond du mini-bassin (apprendre a plonger et a remonter respirer)
# la vanne du fond : une lanterne de mer sur un socle, au fond du mini-bassin. Pas de levier : l'eau qui coule dans sa case le detruirait (un levier ne se waterlogue pas).
# On ouvre la herse en s'approchant a moins d'un bloc de la lanterne (boite de detection), ce qui oblige a plonger.
put(28, 39, 21, S("dark_prismarine"))
put(28, 40, 21, S("sea_lantern"))
VANNE_G1 = (28, 40, 21)
ZONE_G1 = (27, 39, 20, 29, 41, 22)                                 # boite de detection autour de la lanterne
signe(28, SOL + 2, 6, "south", ["DEBARCADERE", "Le voile vous", "est remis : il", "garde l'air"], "dark_blue")
signe(30, SOL + 2, 38, "north", ["VANNE DU FOND", "Plongez vers", "la lanterne", "du fond"], "dark_blue")
porte("G1", [(x, y, 41) for x in (26, 27, 28) for y in range(SOL + 1, SOL + 5)])
# ---------------------------------------------------------------- S2 : la Colonnade ; le bouton de la porte est sur une colonne au milieu du bassin
COLONNES = [(22, 54, 46), (30, 62, 45), (40, 62, 45), (50, 66, 45), (58, 56, 46), (46, 70, 45), (34, 72, 46), (18, 68, 45)]
for (cx, cz, ht) in COLONNES:
    pilier(cx, cz, 41, ht, MOUSSE)
    for dx, dz in ((1, 0), (0, 1)):
        if h01(cx, cz, dx, 810) > .4:
            pilier(cx + dx, cz + dz, 41, ht - 2, PIERRE)
pilier(41, 62, 41, 44, MOUSSE)                                    # petit escalier de pierre : on sort de l'eau par des marches de un bloc
pilier(42, 62, 41, 43, MOUSSE)
put(40, 46, 62, BOUTON_SOL)                                       # le bouton est sur le haut de la colonne (40,62)
BOUTONS_G2 = (40, 46, 62)
porte("G2", [(73, y, z) for z in (60, 61, 62) for y in range(SOL + 1, SOL + 5)])
signe(10, SOL + 3, 62, "east", ["LA MAREE OBEIT", "POULPE, RAIE,", "CRABE, ETOILE", ""], "dark_blue")
# ---------------------------------------------------------------- S3 : les Citernes ; deux leviers (ilots des citernes 1 et 2) ouvrent la grille du tunnel T1
ilot(87, 89, 61, 63, 38)
pilier(88, 62, SOL + 1, SOL + 2)
put(89, SOL + 2, 62, LEV("east"))
ilot(109, 111, 61, 63, 38)
pilier(110, 62, SOL + 1, SOL + 2)
put(111, SOL + 2, 62, LEV("east"))
LEVIERS["L3a"], LEVIERS["L3b"] = (89, SOL + 2, 62), (111, SOL + 2, 62)
for (px, pz) in ((130, 73), (131, 73), (132, 73), (130, 74), (131, 74), (132, 74), (130, 75), (131, 75), (132, 75)):
    put(px, 38, pz, BARRES_E)                                       # la grille au fond du puits de T1 (eau comprise)
PORTES["G3"] = {"cases": [[px, 38, pz] for px in (130, 131, 132) for pz in (73, 74, 75)], "bloc": BARRES_E[0], "props": dict(BARRES_E[1]), "eau": True}
signe(90, SOL + 3, 44, "south", ["CITERNES", "Deux vannes,", "levees en meme", "temps = grille"], "dark_blue")
# ---------------------------------------------------------------- S5 : la Bibliotheque noyee ; quatre glyphes marins a presser dans l'ordre lu dans la colonnade
GLYPHES = {"crabe": (110, "red"), "etoile": (116, "orange"), "poulpe": (122, "purple"), "raie": (128, "cyan")}
BOUTONS_G4 = {}
for nm, (x, coul) in GLYPHES.items():
    for y in (SOL + 2, SOL + 3, SOL + 4):
        put(x, y, 85, S(coul + "_terracotta"))
    put(x, SOL + 2, 86, BOUTON("south"))
    BOUTONS_G4[nm] = (x, SOL + 2, 86)
    signe(x, SOL + 5, 86, "south", ["GLYPHE", nm.upper(), "", ""], "dark_blue")
porte("G4", [(98, y, z) for z in (100, 101, 102) for y in range(SOL + 1, SOL + 5)])
# ---------------------------------------------------------------- S6 : les Ecluses ; un levier vidange le bassin A, un bouton au fond ouvre la grille de T2, on reinonde
LEVIERS["LE"] = (81, SOL + 2, 100)
pilier(80, 100, SOL + 1, SOL + 2)
put(81, SOL + 2, 100, LEV("east"))
for y in range(35, SOL + 1):
    put(62, y, 98, S("ladder", facing="east", waterlogged=True)) if (62, y, 98) in blocs and nom((62, y, 98)) == "water" else None
for px in (65, 66, 67):
    for pz in (99, 100, 101):
        put(px, 34, pz, BARRES_E)
PORTES["G5"] = {"cases": [[px, 34, pz] for px in (65, 66, 67) for pz in (99, 100, 101)], "bloc": BARRES_E[0], "props": dict(BARRES_E[1]), "eau": True}
ilot(87, 89, 100, 102, 40)                                         # la cle du Recif : plaque sur l'ilot du bassin B
PLAQUE_CLE = (88, SOL + 1, 101)
put(*PLAQUE_CLE, PLAQUE)
signe(80, SOL + 3, 98, "south", ["ECLUSE", "Levier en haut :", "le bassin se vide.", "En bas : il se remplit."], "dark_blue") if False else None
signe(60, SOL + 3, 96, "east", ["ECLUSE", "Haut : on vide", "la grille cede", "Bas : remplit"], "dark_blue")
BASSIN_A = (62, 78, 35, SOL, 90, 112)
# ---------------------------------------------------------------- S8 : la salle des pompes ; trois leviers a lever ensemble ouvrent la grille de T3
LEV_X = [20, 24, 28]
for x in LEV_X:
    put(x, SOL + 2, 85, S("copper_block") if False else PIERRE)
    put(x, SOL + 2, 86, LEV("south"))
    LEVIERS[f"P{x}"] = (x, SOL + 2, 86)
for px in (39, 40, 41):
    for pz in (107, 108, 109):
        put(px, 38, pz, BARRES_E)
PORTES["G6"] = {"cases": [[px, 38, pz] for px in (39, 40, 41) for pz in (107, 108, 109)], "bloc": BARRES_E[0], "props": dict(BARRES_E[1]), "eau": True}
signe(24, SOL + 4, 86, "south", ["POMPES", "Trois leviers,", "tous en haut.", ""], "dark_blue")
# ---------------------------------------------------------------- S9 : l'antichambre ; porte a cle vers le Coeur
porte("G7", [(54, y, z) for z in (135, 136, 137) for y in range(SOL + 1, SOL + 5)])
put(54, SOL + 2, 138, S("lodestone"))
CLE_ZONE = (48, SOL + 1, 133)
# ---------------------------------------------------------------- hidden rooms : portes et boutons des salles seches
for n_, c in CACHEES.items():
    if c["voile"]:
        continue
    pv, axe, lat = c["plan"], c["axe"], c["lat"]
    if axe == "x":
        cases = [(pv, y, z) for z in lat for y in (SOL + 1, SOL + 2, SOL + 3)]
    else:
        cases = [(x, y, pv) for x in lat for y in (SOL + 1, SOL + 2, SOL + 3)]
    porte(f"H{n_}", cases, S("cracked_stone_bricks"))
    put(*c["bouton"], BOUTON(c["fb"]))
# ---------------------------------------------------------------- le Coeur du Temple : ilot central, bassin profond, sortie scellee
ilot(99, 103, 141, 145, 14)
for y in range(15, SOL + 1):
    for (x, z) in ((99, 141), (103, 141), (99, 145), (103, 145)):
        put(x, y, z, PIERRE)
creuser(141, 147, SOL + 1, SOL + 4, 142, 144)
creuser(143, 147, SOL + 1, SOL + 5, 140, 146)
porte("SORTIE", [(141, y, z) for z in (142, 143, 144) for y in range(SOL + 1, SOL + 5)])
BOSS = {"B1": (101, SOL + 1, 143), "B2": (72, SOL + 1, 143), "B3": (132, SOL + 1, 143), "B4": (90, 28, 143), "B5": (112, 28, 143)}
POINTS["S1"] = (28, SOL + 1, 12)
POINTS["arrivee_T1"] = (120, SOL + 1, 100)
POINTS["arrivee_T2"] = (40, SOL + 1, 90)
POINTS["arrivee_T3"] = (40, SOL + 1, 126)
POINTS["Coeur"] = (80, SOL + 1, 143)
POINTS["sortie"] = (145, SOL + 1, 143)
print("[3] missions :", len(blocs), flush=True)


def recoque():
    d = C.copy()
    for _ in range(4):
        n = d.copy()
        n[1:] |= d[:-1]
        n[:-1] |= d[1:]
        n[:, 1:] |= d[:, :-1]
        n[:, :-1] |= d[:, 1:]
        n[:, :, 1:] |= d[:, :, :-1]
        n[:, :, :-1] |= d[:, :, 1:]
        d = n
    n_ = 0
    for x, y, z in zip(*np.nonzero(d & ~C)):
        if (x, y, z) not in blocs:
            put(x, y, z, pick(PAL, bruit(x + y * .5, z - y * .3, 3, 800) * .7 + h01(x, y, z, 801) * .3))
            n_ += 1
    return n_


print("[3b] coquille complementaire :", recoque(), flush=True)
for f_ in ("donjon_aqua_decor.py", "donjon_aqua_lumiere.py", "donjon_aqua_fin.py"):
    p_ = Path(__file__).resolve().parent / f_
    if p_.exists():
        exec(compile(p_.read_text(encoding="utf-8"), f_, "exec"), globals())
