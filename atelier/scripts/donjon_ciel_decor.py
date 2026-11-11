# [claude-build]
"""donjon_ciel_decor.py - batiments, enigmes et decor de l'Archipel des Nuees : place de depart et steles, salles cachees, ailes de la Citadelle, girouettes, moulin,
colonnes du slalom, arene du boss, poteaux et ruines. Execute par donjon_ciel.py apres les iles et les tours."""
QUARTZ, QUARTZ_B, QUARTZ_P = S("quartz_bricks"), S("smooth_quartz"), S("quartz_pillar", axis="y")
CALCITE = S("calcite")
TOIT = S("stone_brick_slab", type="bottom", waterlogged=False)


def libre_au_dessus(x, z, T, h=4):
    return all((x, T + k, z) not in blocs for k in range(1, h + 1))


def salle(cx, cz, T, hs, h, entree, grille=None, echelle=False):
    """Petit batiment ferme : interieur (2hs+1)² sur h de haut, toit plat (couche T+h+1), entree de 3 x 3 ; renvoie les cases de l'entree.
    echelle=True : une echelle conduit a une trappe dans le toit (on y marche, c'est un point de depart de vol)."""
    x0, x1, z0, z1 = cx - hs, cx + hs, cz - hs, cz + hs
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            bord = x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1)
            for y in range(T + 1, T + h + 2):
                if y == T + h + 1:
                    put(x, y, z, mur(x, y, z))
                elif bord:
                    put(x, y, z, mur(x, y, z))
                else:
                    retire(x, y, z)
    for x in range(x0, x1 + 1):                                    # sol dallé
        for z in range(z0, z1 + 1):
            put(x, T, z, SOL_A if (x + z) % 2 == 0 else SOL_B)
    ex, ez = COTES[entree]
    porte = []
    for o in (-1, 0, 1):
        for y in range(T + 1, T + 4):
            c = (cx + ex * (hs + 1), y, cz + o) if ex else (cx + o, y, cz + ez * (hs + 1))
            retire(*c)
            porte.append(c)
            if grille:
                put(*c, BARRES)
    interieur(x0, x1, T + 1, T + h, z0, z1)
    for (x, z) in ((x0, z0), (x1, z1), (x0, z1), (x1, z0)):
        if (x, T + 1, z) not in blocs:
            put(x, T + 1, z, LANTERNE)
    if echelle:
        lx = x1 if ex <= 0 else x0
        face = "west" if lx == x1 else "east"
        retire(lx, T + h + 1, cz)
        for y in range(T + 1, T + h + 2):
            put(lx, y, cz, S("ladder", facing=face, waterlogged=False))
    return porte


# ---------------------------------------------------------------- Le Debarcadere : place, steles, panneaux
cxd, czd, Td = ILES["D"]["cx"], ILES["D"]["cz"], ILES["D"]["T"]
for x in range(cxd - 11, cxd + 12):
    for z in range(czd - 11, czd + 12):
        if (x - cxd) ** 2 + (z - czd) ** 2 <= 121 and (x, Td, z) in blocs:
            put(x, Td, z, QUARTZ_B if (x // 2 + z // 2) % 2 == 0 else QUARTZ)
for x in range(cxd - 4, cxd + 5):                                  # la stele : un mur de 9 de large, 4 de haut, au nord de la place
    for y in range(Td + 1, Td + 5):
        put(x, y, czd - 8, mur(x, y, czd - 8) if y < Td + 4 else S("chiseled_stone_bricks"))
signe(cxd - 3, Td + 2, czd - 7, "south", ["PARAVOILE", "", "Saute dans", "le vide"])
signe(cxd, Td + 2, czd - 7, "south", ["En l'air :", "ESPACE =", "ouvre la voile", "(en main)"])
signe(cxd + 3, Td + 2, czd - 7, "south", ["Regard : cap", "Bas = piquer", "Haut = freiner", "Shift = ranger"])
ORDRE_GLYPHES = ["Vent", "Pluie", "Nuage", "Foudre"]
for y in range(Td + 1, Td + 4):                                    # la stele du ciel : l'ordre des glyphes de l'Aiguille
    put(cxd + 6, y, czd - 3, mur(cxd + 6, y, czd - 3))
signe(cxd + 6, Td + 2, czd - 2, "south", ["Le ciel dit :", ORDRE_GLYPHES[0] + ", " + ORDRE_GLYPHES[1] + ",", ORDRE_GLYPHES[2] + ", " + ORDRE_GLYPHES[3], "(dans l'ordre)"])
for (dx, dz) in ((-9, 4), (9, 4), (-9, -6), (9, -6)):
    for y in range(Td + 1, Td + 4):
        put(cxd + dx, y, czd + dz, S("quartz_pillar", axis="y"))
    put(cxd + dx, Td + 4, czd + dz, LANTERNE)

# ---------------------------------------------------------------- salles cachees sous trois iles
sous_sol("D", cxd - 11, czd, Td, 1)
sous_sol("AI", 223, 38, ILES["AI"]["T"], 2)
sous_sol("CI", 41, 246, ILES["CI"]["T"], 3)

# ---------------------------------------------------------------- les girouettes : un levier au sommet de chaque tour des trois ilots
LEV_G = {}
for g_ in ("G1", "G2", "G3"):
    cx, cz, top = ILES[g_]["cx"], ILES[g_]["cz"], ILES[g_]["tours"][0]["top"]
    for y in (top + 1, top + 2, top + 3):
        put(cx, y, cz, S("stone_brick_wall", up=True, north=False, south=False, east=False, west=False, waterlogged=False) if y > top + 1 else mur(cx, y, cz))
    put(cx, top + 4, cz, S("chiseled_stone_bricks"))
    levier(cx, top + 2, cz + 1, "south")
    LEV_G[g_] = (cx, top + 2, cz + 1)
    CIB[g_]["balcon"] = (cx + 2, top + 1, cz)                       # le centre est pris par le mat de la girouette

# ---------------------------------------------------------------- Le Moulin des Vents : la porte de la tour est une herse que les trois leviers ouvrent
cxm, czm, Tm = ILES["MV"]["cx"], ILES["MV"]["cz"], ILES["MV"]["T"]
gm = []
for o in (-1, 0, 1):
    for y in range(Tm + 1, Tm + 4):
        c = (cxm + o, y, czm - 6)
        put(*c, BARRES)
        gm.append(c)
PORTES["G4"] = {"cases": [list(c) for c in gm], "leviers": [list(v) for v in LEV_G.values()]}
hub = (cxm + 8, Tm + 14, czm)                                      # les pales : deux poutres en croix sur la face est, avec des voiles de laine
for k in range(-9, 10):
    for (y, z) in ((hub[1] + k, hub[2]), (hub[1], hub[2] + k)):
        put(hub[0], y, z, S("oak_planks"))
for k in range(2, 10):
    for sgn in (-1, 1):
        for (y, z) in ((hub[1] + sgn * k, hub[2] + 1), (hub[1] + 1, hub[2] + sgn * k)):
            put(hub[0], y, z, S("white_wool"))
put(*hub, S("dark_oak_log", axis="x"))
put(cxm + 7, Tm + 14, czm, S("dark_oak_log", axis="x"))                    # l'axe, de la tour au moyeu

# ---------------------------------------------------------------- La Citadelle haute : ailes ouest (levier) et est (crypte), cle, porte de la tour
cxc, czc, Tc = ILES["CI"]["cx"], ILES["CI"]["cz"], ILES["CI"]["T"]
salle(cxc - 14, czc, Tc, 3, 4, "e")                                # aile ouest : un levier qui fait ceder la herse de la crypte
levier(cxc - 14 - 3, Tc + 2, czc, "east")
LEV_LW = (cxc - 14 - 3, Tc + 2, czc)
porte_crypte = salle(cxc + 14, czc, Tc, 3, 4, "o", grille=True)     # aile est : la crypte, fermee par une herse
# la crypte : cinq lampes (mur du fond) que le panneau fait rallumer dans l'ordre vu, comme au jeu du Simon ; chaque lampe a son bouton et une bande de couleur
xm = cxc + 14 + 4
COULEURS = ["red", "blue", "lime", "yellow", "purple"]
LAMPES, BOUTONS_L = {}, {}
for k in range(1, 6):
    z = czc - 3 + k
    put(xm, Tc + 3, z, S("gray_concrete"))
    put(xm, Tc + 1, z, S(f"{COULEURS[k - 1]}_terracotta"))
    bouton(xm - 1, Tc + 2, z, "west")
    LAMPES[k], BOUTONS_L[k] = (xm, Tc + 3, z), (xm - 1, Tc + 2, z)
SEQUENCE = [2, 4, 1, 5, 3]                                          # ordre dans lequel les lampes s'allument, et dans lequel on presse les boutons
START_L = (cxc + 14, Tc + 2, czc - 3)
bouton(*START_L, "south")
signe(cxc + 14, Tc + 3, czc - 3, "south", ["Presse : les", "lampes disent", "l'ordre"])
PORTES["GC1"] = {"cases": [list(c) for c in porte_crypte], "leviers": [list(LEV_LW)]}
pk = []
for o in (-1, 0, 1):                                               # la porte de la tour de la Citadelle, a cle
    for y in range(Tc + 1, Tc + 4):
        c = (cxc + o, y, czc - 6)
        put(*c, BARRES)
        pk.append(c)
PORTES["GK"] = {"cases": [list(c) for c in pk], "leviers": [list(START_L)] + [list(b) for b in BOUTONS_L.values()], "cle": True}

# ---------------------------------------------------------------- les colonnes du slalom, entre le Relais et la plateforme d'atterrissage
PILIERS = []
for (px_, pz_) in [(x_, z_) for x_ in (156, 148, 140, 132) for z_ in (236, 264)]:                # une colonnade de chaque cote de la ligne de vol, hors de la trajectoire
    for x in range(px_ - 1, px_ + 2):
        for z in range(pz_ - 1, pz_ + 2):
            for y in range(30, 84):
                put(x, y, z, S("quartz_pillar", axis="y") if y % 9 else S("chiseled_quartz_block"))
    PILIERS.append((px_, pz_))
VOLS_CHEMINS["RL>CP"] = [[150, 245], [140, 255], [130, 245]]                    # au centre des trois anneaux : nord, sud, nord

# ---------------------------------------------------------------- les cabanes des ilots caches (salle de 9 x 9 x 5, toit accessible par echelle : c'est le point de depart du vol de retour)
CABANES = {}
for h_, entree in (("HA", "o"), ("HB", "e")):
    cx, cz, T = ILES[h_]["cx"], ILES[h_]["cz"], ILES[h_]["T"]
    salle(cx, cz, T, 4, 5, entree, echelle=True)
    CABANES[h_] = {"boite": (cx - 4, cx + 4, T + 1, T + 5, cz - 4, cz + 4), "coffre": (cx - 3, T + 1, cz + 3), "mobs": (cx + 2, T + 1, cz - 3), "toit": (cx, T + 7, cz)}

# ---------------------------------------------------------------- l'arene du Coeur des Nuees : place de quartz, colonnes, estrade ; la sortie du boss est scellee
cxb, czb, Tb = ILES["BO"]["cx"], ILES["BO"]["cz"], ILES["BO"]["T"]
for x in range(cxb - 14, cxb + 15):
    for z in range(czb - 14, czb + 15):
        d2 = (x - cxb) ** 2 + (z - czb) ** 2
        if d2 <= 169 and (x, Tb, z) in blocs:
            put(x, Tb, z, QUARTZ_B if (x // 2 + z // 2) % 2 == 0 else QUARTZ)
        if d2 <= 25 and (x, Tb, z) in blocs:
            put(x, Tb + 1, z, QUARTZ_B)
for i in range(12):
    a = i * math.pi / 6
    px_, pz_ = round(cxb + 11 * math.cos(a)), round(czb + 11 * math.sin(a))
    for y in range(Tb + 1, Tb + 8):
        put(px_, y, pz_, QUARTZ_P)
    put(px_, Tb + 8, pz_, S("chiseled_quartz_block"))
    put(px_, Tb + 9, pz_, LANTERNE)
for (dx, dz) in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
    put(cxb + dx, Tb + 2, czb + dz, LANTERNE)
porte_sortie = salle(cxb, czb - 20, Tb, 3, 4, "s", grille=True)
PORTES["SORTIE"] = {"cases": [list(c) for c in porte_sortie], "leviers": [], "boss": True}
BOSS = {"boss_pos": (cxb, Tb + 2, czb), "boss_coffre": (cxb - 2, Tb + 1, czb - 20 + 2), "boss_sortie": (cxb, Tb + 1, czb - 20)}

# ---------------------------------------------------------------- poteaux de lanterne, sur les grandes iles
for nom_ in ("D", "AI", "MV", "CI", "BE", "BO"):
    ile_ = ILES[nom_]
    cx, cz, r, T = ile_["cx"], ile_["cz"], ile_["r"], ile_["T"]
    for i in range(8):
        a = i * math.pi / 4 + 0.3
        x, z = round(cx + 0.78 * r * math.cos(a)), round(cz + 0.78 * r * math.sin(a))
        if (x, T, z) in blocs and libre_au_dessus(x, z, T, 5) and not any((x + a_, T + 1, z + b_) in blocs for a_ in (-1, 0, 1) for b_ in (-1, 0, 1)):
            for y in (T + 1, T + 2):
                put(x, y, z, S("stone_brick_wall", up=True, north=False, south=False, east=False, west=False, waterlogged=False))
            put(x, T + 3, z, LANTERNE)
for nom_ in ("P1", "P2", "P3"):                                    # les pierres suspendues : une lanterne sur un petit pilier, au centre
    cx, cz, T = ILES[nom_]["cx"], ILES[nom_]["cz"], ILES[nom_]["T"]
    for y in (T + 1, T + 2):
        put(cx, y, cz, S("stone_brick_wall", up=True, north=False, south=False, east=False, west=False, waterlogged=False))
    put(cx, T + 3, cz, LANTERNE)
# ---------------------------------------------------------------- enigme 1 : les glyphes de l'Aiguille. Quatre boutons au pied d'une stele, a presser dans l'ordre lu sur la stele du Debarcadere ; ils ouvrent la porte de la tour
cxa, cza, Ta = ILES["AI"]["cx"], ILES["AI"]["cz"], ILES["AI"]["T"]
for z in range(cza - 5, cza + 6):
    for y in range(Ta + 1, Ta + 5):
        put(cxa + 9, y, z, mur(cxa + 9, y, z))
BOUTONS_G = {}
for nom_g, z in zip(["Vent", "Pluie", "Nuage", "Foudre"], (cza - 3, cza - 1, cza + 1, cza + 3)):
    bouton(cxa + 8, Ta + 2, z, "west")
    signe(cxa + 8, Ta + 3, z, "west", ["Glyphe", nom_g])
    BOUTONS_G[nom_g] = (cxa + 8, Ta + 2, z)
gg = []
for o in (-1, 0, 1):
    for y in range(Ta + 1, Ta + 4):
        c = (cxa - 6, y, cza + o)
        put(*c, BARRES)
        gg.append(c)
PORTES["GG"] = {"cases": [list(c) for c in gg], "leviers": [list(b) for b in BOUTONS_G.values()]}

# ---------------------------------------------------------------- enigme 2 : les anneaux de vol des Colonnes. Trois anneaux a traverser en planant, dans l'ordre ; ils ouvrent la herse de la tour de la plateforme.
# Un levier de rappel, sur la plateforme, ramene au Relais pour recommencer.
# Les anneaux sont des cadres de blocs (aucune entite, rien qui puisse s'afficher de travers) : ouverture de 9 x 9, cadre de 11 x 11, d'une case d'epaisseur.
# Le jeu ne range plus la voile pour un effleurement (mod 2.6.1), et un vol a plat passe les trois sans piquer.
OCRE = S("ochre_froglight", axis="y")
ANNEAUX = [(150, 245, 58), (140, 255, 53), (130, 245, 48)]          # (x du plan, z du centre, y du centre)
DEMI_ANNEAU = 4
ANNEAUX_CASES = []
for (x, z, y) in ANNEAUX:
    cases = [(x, y + dy, z + dz) for dz in range(-DEMI_ANNEAU - 1, DEMI_ANNEAU + 2) for dy in range(-DEMI_ANNEAU - 1, DEMI_ANNEAU + 2) if max(abs(dz), abs(dy)) == DEMI_ANNEAU + 1]
    for c in cases:
        put(*c, OCRE)
    ANNEAUX_CASES.append(cases)
cxp, czp, Tp = ILES["CP"]["cx"], ILES["CP"]["cz"], ILES["CP"]["T"]
gr = []
for o in (-1, 0, 1):
    for y in range(Tp + 1, Tp + 4):
        c = (108 + 4, y, 250 + o)
        put(*c, BARRES)
        gr.append(c)
PORTES["GR"] = {"cases": [list(c) for c in gr], "leviers": [], "atteindre": True}
for y in (Tp + 1, Tp + 2):
    put(121, y, 247, mur(121, y, 247))
levier(120, Tp + 1, 247, "west")
signe(120, Tp + 2, 247, "west", ["Rappel au", "Relais :", "recommence"])
LEV_RAPPEL = (120, Tp + 1, 247)

# ---------------------------------------------------------------- enigme 4 : les trois eclats. Un levier sur les Pierres (P2), le Relais (RL) et le Belvedere (BE) donne un eclat ; on les depose sur l'autel de l'arene.
ECLATS = {}
cx, cz, T = ILES["P2"]["cx"], ILES["P2"]["cz"], ILES["P2"]["T"]
put(cx, T + 1, cz, mur(cx, T + 1, cz))
levier(cx + 1, T + 1, cz, "east")
ECLATS["brume"] = {"nom": "Éclat de Brume", "levier": (cx + 1, T + 1, cz), "debout": (cx + 1, T + 1, cz + 1), "ile": "P2", "var": "#e1", "facing": "east"}
def levier_exterieur(nom_, exclure):
    """Cherche, sur la face exterieure du mur de la tour de `nom_` (hors du cote `exclure`, celui de l'entree), l'endroit ou il y a le plus de sol autour de la case ou l'on se tient."""
    t = ILES[nom_]["tours"][0]
    T_ = ILES[nom_]["T"]
    meilleur = None
    for cote, (dx, dz) in COTES.items():
        if cote == exclure:
            continue
        for o_ in range(-t["hs"], t["hs"] + 1):
            x, z = t["cx"] + dx * (t["hs"] + 2) + (o_ if dx == 0 else 0), t["cz"] + dz * (t["hs"] + 2) + (o_ if dz == 0 else 0)
            if (x, T_, z) not in blocs or (x, T_ + 1, z) in blocs or (x - dx, T_ + 2, z - dz) not in blocs:
                continue
            sol = sum((x + a_, T_, z + b_) in blocs and (x + a_, T_ + 1, z + b_) not in blocs for a_ in range(-2, 3) for b_ in range(-2, 3))
            if meilleur is None or sol > meilleur[0]:
                meilleur = (sol, (x, T_ + 2, z), FACE_VERS[cote], (x, T_ + 1, z))
    assert meilleur, ("pas de place pour un levier", nom_)
    return meilleur[1:]


T = ILES["RL"]["T"]
lc_, lf_, ls_ = levier_exterieur("RL", "e")
levier(*lc_, lf_)
ECLATS["foudre"] = {"nom": "Éclat de Foudre", "levier": lc_, "debout": ls_, "ile": "RL", "var": "#e2", "facing": lf_}
T = ILES["BE"]["T"]
levier(68, T + 2, 192, "south")
ECLATS["nuees"] = {"nom": "Éclat des Nuées", "levier": (68, T + 2, 192), "debout": (68, T + 1, 192), "ile": "BE", "var": "#e3", "facing": "south"}
ALTAR = (cxb, Tb + 2, czb + 4)
put(*ALTAR, S("chiseled_quartz_block"))
signe(ALTAR[0], ALTAR[1], ALTAR[2] + 1, "south", ["Autel", "3 éclats ici"])
for (dx, dz) in ((-1, 0), (1, 0)):
    put(ALTAR[0] + dx, ALTAR[1], ALTAR[2] + dz, LANTERNE)
# le retour au Debarcadere : sur l'ile du Coeur, un levier ramene tout de suite au debut, pour refaire le trajet (et rechercher les eclats qui manquent)
for y in (Tb + 1, Tb + 2):
    put(cxb - 17, y, czb + 6, mur(cxb - 17, y, czb + 6))
LEV_RETOUR = (cxb - 18, Tb + 1, czb + 6)
levier(*LEV_RETOUR, "west")
signe(cxb - 18, Tb + 2, czb + 6, "west", ["Il manque des", "eclats ? Tire", "le levier pour", "tout refaire"])

# touffes d'herbe sur les dessus (sans toucher aux entrees ni aux interieurs)
nh = 0
for (x, y, z), s in list(blocs.items()):
    if s == HERBE and h01(x, y, z, 31) < .22 and (x, y + 1, z) not in blocs and (x, y + 2, z) not in blocs and (x, y + 3, z) not in blocs:
        if not any(abs(x - t["cx"]) <= t["hs"] + 2 and abs(z - t["cz"]) <= t["hs"] + 2 for i_ in ILES.values() for t in i_["tours"]):
            put(x, y + 1, z, S("short_grass"))
            nh += 1
print("[3] decor : herbes", nh, "| blocs", len(blocs), flush=True)

# ---------------------------------------------------------------- cibles pour le verificateur (cases de pieds)
for n_ in ORDRE + ["HA", "HB"]:
    POINTS[n_] = point_sol(n_, *{"D": (0, 4), "AI": (-9, 8), "MV": (-9, 8), "CI": (-5, 12), "BE": (-8, 6), "BO": (0, 6), "CP": (9, 0)}.get(n_, (0, 0)))
for n_ in ("AI", "G1", "G2", "G3", "MV", "RL", "CP", "CI", "BE"):
    POINTS[f"{n_}_balcon"] = CIB[n_]["balcon"]
for k_, c_ in enumerate(CIB["AI"]["niveaux"]):
    POINTS[f"AI_niv{k_}"] = c_
POINTS["MV_haut"] = CIB["MV"]["balcon"]
POINTS["CI_crypte"] = (cxc + 14, Tc + 1, czc)
POINTS["CI_aile_ouest"] = (cxc - 14, Tc + 1, czc)
POINTS["BO_chambre"] = (cxb, Tb + 1, czb - 20)
for h_ in ("HA", "HB"):
    POINTS[f"{h_}_toit"] = CABANES[h_]["toit"]
    POINTS[f"{h_}_salle"] = (ILES[h_]["cx"], ILES[h_]["T"] + 1, ILES[h_]["cz"])
POINTS["depart"] = POINTS["D"]
POINTS["retour_debut"] = (cxb - 19, Tb + 1, czb + 6)
POINTS["glyphes"] = (cxa + 7, Ta + 1, cza)
POINTS["rappel"] = (119, Tp + 1, 247)
POINTS["cr_lampes"] = (cxc + 14 + 3, Tc + 1, czc)
POINTS["autel"] = (ALTAR[0], ALTAR[1], ALTAR[2] - 1)
for k_, e_ in ECLATS.items():
    POINTS[f"eclat_{e_['ile']}"] = e_["debout"]
PORTES["GR"]["leviers"] = [list(POINTS["CP"])]
