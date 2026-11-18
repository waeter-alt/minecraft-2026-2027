# [claude-build]
"""donjon_aqua_missions.py - portes, vannes, enigmes, tunnels et salles cachees, cloches d'air, arene du boss et sortie. Execute par donjon_aqua.py.
Tout est sous l'eau : un levier ou un bouton y est detruit par l'eau qui coule dans sa case. Les interrupteurs sont donc des « vannes » : des trappes en bois
(waterloggees, elles se cliquent a la main), lues par les blocs de commande avec open=true."""

BOUTON = lambda f: S("stone_button", face="wall", facing=f, powered=False)  # noqa: E731  (seule la salle cachee 1, au sec, en a un)
PIERRE = S("prismarine_bricks")
MOUSSE = S("prismarine")
PRISME = S("dark_prismarine")
LANT = S("sea_lantern")
VANNES = {}                                                                  # nom -> ((x, y, z), orientation)


def vanne(nom_, x, y, z, facing="north"):
    """Trappe en bois a plat, waterloggee ; fermee au depart. Ouverte par un clic droit : les blocs de commande le lisent puis agissent."""
    VANNES[nom_] = ((x, y, z), facing)
    put(x, y, z, S("oak_trapdoor", half="bottom", facing=facing, open=False, powered=False, waterlogged=True))
    return (x, y, z)


def signe(x, y, z, facing, lignes, couleur="black"):
    assert all(len(l) <= 14 for l in lignes) and len(lignes) <= 4, ("panneau trop large (14 caracteres max par ligne)", lignes)
    put(x, y, z, S("oak_wall_sign", facing=facing, waterlogged=bool(W[x, y, z])))
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
    """Socle plein au milieu d'un bassin : pierre de y0 jusqu'au niveau du sol (43) ; une vanne se pose dessus."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(y0, SOL + 1):
                put(x, y, z, PRISME if y == SOL else MOUSSE)


# ---------------------------------------------------------------- S1 : le Debarcadere (au sec) ; la vanne de la herse est au fond du mini-bassin, entre deux lanternes de mer
vanne("G1", 28, 37, 21)
put(27, 37, 21, LANT)
put(29, 37, 21, LANT)
VANNE_G1 = (28, 37, 21)
signe(28, SOL + 2, 6, "south", ["DEBARCADERE", "Le voile vous", "est remis : il", "garde l'air"], "dark_blue")
signe(30, SOL + 2, 38, "north", ["VANNE DU FOND", "Plongez, puis", "ouvrez la", "trappe du fond"], "dark_blue")
porte("G1", [(x, y, 34) for x in (26, 27, 28) for y in (37, 38, 39)])                                  # herse noyee dans la galerie qui part du mini-bassin
# ---------------------------------------------------------------- S2 : la Colonnade ; la vanne de la porte est au sommet d'une colonne, au milieu de la salle
COLONNES = [(22, 54, 46), (30, 62, 45), (40, 62, 45), (50, 66, 46), (58, 56, 48), (46, 70, 47), (34, 72, 46), (18, 68, 50), (28, 50, 52), (60, 74, 49)]
for (cx, cz, ht) in COLONNES:
    pilier(cx, cz, 41, ht, MOUSSE)
    for dx, dz in ((1, 0), (0, 1)):
        if h01(cx, cz, dx, 810) > .4:
            pilier(cx + dx, cz + dz, 41, ht - 2, PIERRE)
    put(cx, ht + 1, cz, PRISME)
put(40, 46, 62, PRISME)
VANNE_G2 = vanne("G2", 40, 47, 62)                                                                      # sur la colonne (40, 62), dont le sommet est a 46
porte("G2", [(73, y, z) for z in (60, 61, 62) for y in range(SOL + 1, SOL + 5)])
signe(10, SOL + 3, 62, "east", ["LA MAREE OBEIT", "POULPE, RAIE,", "CRABE, ETOILE", ""], "dark_blue")
# ---------------------------------------------------------------- S3 : les Citernes ; deux vannes sur des socles (citernes 1 et 2) ouvrent la grille du tunnel T1
ilot(87, 89, 61, 63, 38)
VANNE_3A = vanne("3a", 88, SOL + 1, 62)
ilot(109, 111, 61, 63, 38)
VANNE_3B = vanne("3b", 110, SOL + 1, 62)
for (px, pz) in ((130, 73), (131, 73), (132, 73), (130, 74), (131, 74), (132, 74), (130, 75), (131, 75), (132, 75)):
    put(px, 38, pz, GRILLE)                                       # la grille au fond du puits de T1
PORTES["G3"] = {"cases": [[px, 38, pz] for px in (130, 131, 132) for pz in (73, 74, 75)], "bloc": GRILLE[0], "props": dict(GRILLE[1]), "eau": True}
signe(90, SOL + 3, 44, "south", ["CITERNES", "Deux vannes,", "levees en meme", "temps = grille"], "dark_blue")
# ---------------------------------------------------------------- S5 : la Bibliotheque noyee ; quatre glyphes marins (vannes) a ouvrir dans l'ordre lu dans la Colonnade
GLYPHES = {"crabe": (110, "red"), "etoile": (116, "orange"), "poulpe": (122, "purple"), "raie": (128, "cyan")}
VANNES_G4 = {}
for nm, (x, coul) in GLYPHES.items():
    for y in (SOL + 2, SOL + 3, SOL + 4):
        put(x, y, 85, S(coul + "_terracotta"))
    VANNES_G4[nm] = vanne("g_" + nm, x, SOL + 2, 86, "south")
    signe(x, SOL + 5, 86, "south", ["GLYPHE", nm.upper(), "", ""], "dark_blue")
porte("G4", [(98, y, z) for z in (100, 101, 102) for y in range(SOL + 1, SOL + 5)])
# ---------------------------------------------------------------- S6 : les Ecluses ; la vanne de l'ilot du bassin B donne la cle et fait ceder la grille au fond du bassin A (entree de T2)
ilot(87, 89, 100, 102, 40)
VANNE_CLE = vanne("cle", 88, SOL + 1, 101)
for px in (65, 66, 67):
    for pz in (99, 100, 101):
        put(px, 34, pz, GRILLE)
PORTES["G5"] = {"cases": [[px, 34, pz] for px in (65, 66, 67) for pz in (99, 100, 101)], "bloc": GRILLE[0], "props": dict(GRILLE[1]), "eau": True}
signe(60, SOL + 3, 96, "east", ["LES ECLUSES", "La vanne de", "l'ilot ouvre", "la grille"], "dark_blue")
# ---------------------------------------------------------------- S8 : la salle des pompes ; trois vannes a ouvrir ensemble ouvrent la grille de T3
LEV_X = [20, 24, 28]
VANNES_P = {}
for x in LEV_X:
    put(x, SOL + 2, 85, PRISME)
    VANNES_P[x] = vanne(f"p{x}", x, SOL + 2, 86, "south")
for px in (39, 40, 41):
    for pz in (107, 108, 109):
        put(px, 38, pz, GRILLE)
PORTES["G6"] = {"cases": [[px, 38, pz] for px in (39, 40, 41) for pz in (107, 108, 109)], "bloc": GRILLE[0], "props": dict(GRILLE[1]), "eau": True}
signe(24, SOL + 4, 86, "south", ["POMPES", "Trois vannes", "a ouvrir", "ensemble"], "dark_blue")
# ---------------------------------------------------------------- S9 : l'antichambre ; herse a cle vers le Coeur
porte("G7", [(50, y, z) for z in (135, 136, 137) for y in range(SOL + 1, SOL + 5)])
put(50, SOL + 2, 138, S("lodestone"))
# ---------------------------------------------------------------- salles cachees : portes camouflees dans le mur et leur commande
for n_, c in CACHEES.items():
    if c["voile"]:
        continue
    pv, axe, lat = c["plan"], c["axe"], c["lat"]
    if axe == "x":
        cases = [(pv, y, z) for z in lat for y in (SOL + 1, SOL + 2, SOL + 3)]
    else:
        cases = [(x, y, pv) for x in lat for y in (SOL + 1, SOL + 2, SOL + 3)]
    porte(f"H{n_}", cases, PIERRE, eau_=c["eau"])
    if c["eau"]:
        x, y, z = c["bouton"]
        vanne(f"h{n_}", x, y, z, c["fb"])
        c["vanne"] = (x, y, z)
    else:
        put(*c["bouton"], BOUTON(c["fb"]))
# ---------------------------------------------------------------- le Coeur du Temple : ilot central, bassin profond, sortie scellee
porte("SORTIE", [(141, y, z) for z in (142, 143, 144) for y in range(SOL + 1, SOL + 5)])        # grille noyee : le couloir derriere est noye lui aussi
BOSS = {"B1": (101, 56, 143), "B2": (72, 50, 143), "B3": (132, 50, 143), "B4": (90, 28, 143), "B5": (112, 28, 143)}
POINTS["S1"] = (28, SOL + 1, 12)
POINTS["arrivee_T1"] = (120, SOL + 1, 100)
POINTS["arrivee_T2"] = (40, SOL + 1, 90)
POINTS["arrivee_T3"] = (40, SOL + 1, 126)
POINTS["Coeur"] = (80, SOL + 1, 143)
POINTS["sortie"] = (145, SOL + 1, 143)

# ---------------------------------------------------------------- cloches d'air : les seuls points ou le voile se recharge (il ne le fait qu'a l'air libre)
CLOCHES = []
YB = 50                                                            # air en y 50 et 51, calotte en 52 ; on y entre par dessous


def cloche(cx, cz, yb=YB):
    """Petit pavillon flottant, pose sur quatre pieds : 5 x 5, calotte pleine, murs de 2 blocs, ouvert dessous. L'air y reste emprisonne ; on y remonte respirer."""
    zone = [(x, y, z) for x in range(cx - 2, cx + 3) for z in range(cz - 2, cz + 3) for y in range(yb, yb + 3)]
    if any(blocs.get(p) != EAU for p in zone):
        return False
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 2, cz + 3):
            dedans = abs(x - cx) <= 1 and abs(z - cz) <= 1
            for y in (yb, yb + 1):
                if dedans:
                    blocs.pop((x, y, z), None)
                    W[x, y, z] = False
                else:
                    put(x, y, z, PRISME if (x + z + y) % 3 == 0 else PIERRE)
            put(x, yb + 2, z, LANT if (x, z) == (cx, cz) else (PRISME if dedans else PIERRE))
    for (x, z) in ((cx - 2, cz - 2), (cx + 2, cz - 2), (cx - 2, cz + 2), (cx + 2, cz + 2)):
        y = yb - 1
        while blocs.get((x, y, z)) == EAU:                         # les pieds descendent jusqu'au fond
            put(x, y, z, PIERRE)
            y -= 1
    CLOCHES.append((cx, yb, cz))
    return True


def cloches_salle(nom_, pas=20):
    x0, x1, z0, z1, top = SALLES[nom_]
    for x in range(x0 + 8, x1 - 6, pas):
        for z in range(z0 + 8, z1 - 6, pas):
            cloche(x, z)


for n_ in ("S2", "S3", "S5", "S6", "S8", "S9"):
    cloches_salle(n_)
for (cx, cz) in ((131, 74), (120, 95), (66, 100), (40, 94), (40, 108), (40, 130)):            # basses, au dessus des entrees et sorties des tunnels : on y respire avant de plonger
    cloche(cx, cz, 44)
# ---------------------------------------------------------------- le Coeur du Temple, noye : piliers ruines (abris contre le rayon du Leviathan) et cloches d'air (refuges)
PILIERS_BOSS = [(90, 139, 38), (112, 139, 34), (90, 150, 44), (112, 150, 36), (101, 144, 50),                  # dans la cuvette (fond a 14)
                (68, 127, 58), (68, 160, 52), (133, 127, 56), (133, 160, 60), (76, 143, 50), (126, 143, 46)]    # sur l'etagere (43)


def pilier_ruine(cx, cz, haut):
    """Colonne ruinee de 3 x 3, du fond de la cuvette ou de l'etagere jusqu'a `haut`, au sommet irregulier ; ne remplace que de l'eau."""
    x0b, x1b, z0b, z1b, fond = POOLS["S10"]
    base = fond if x0b <= cx <= x1b and z0b <= cz <= z1b else SOL
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 1, cz + 2):
            h_ = haut - int(h01(x, z, 0, 931) * 5)
            for y in range(base + 1, h_ + 1):
                if blocs.get((x, y, z)) == EAU:
                    put(x, y, z, MOUSSE if h01(x, y, z, 932) < .3 else PIERRE)


for (px_, pz_, ph_) in PILIERS_BOSS:
    pilier_ruine(px_, pz_, ph_)
CLOCHES_BOSS = []                                                  # (cx, yb, cz) ; celle de (86, 44, 136) est au-dessus de la sortie de la galerie d'entree : on y respire en arrivant
for (cx_, cz_, yb_) in [(86, 136, 44), (72, 136, 60), (72, 152, 56), (100, 126, 62), (100, 162, 58), (130, 136, 60), (130, 153, 56), (113, 143, 52)]:
    if cloche(cx_, cz_, yb_):
        CLOCHES_BOSS.append((cx_, yb_, cz_))
assert len(CLOCHES_BOSS) >= 7, ("cloches de la salle du boss", CLOCHES_BOSS)
print("[3b] salle du boss noyee :", len(PILIERS_BOSS), "piliers,", len(CLOCHES_BOSS), "cloches", flush=True)
print("[3] missions :", len(blocs), "| cloches d'air :", len(CLOCHES), flush=True)


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
