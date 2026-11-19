# [claude-build]
"""station_create.py - les machines Create (6.0.9) du Donjon de la Station : volants a tourner (valve handles), lignes d'arbres et d'engrenages
qui tournent (moteurs creatifs caches), pompes et tuyaux, turbines du secteur C et de la Cheminee (grandes roues dentees, qu'un moteur pose par le datapack met en route).
Pur calcul : des blocs a ajouter aux dicts de station_acte1 et station_secteurs, et les lignes de datapack qui lisent un volant.

Un volant Create garde, tant qu'on le tourne, un compteur `InUse` dans son entite de bloc : le datapack le lit (`data get block ... InUse`) et cumule les ticks
ou il est tourne ; au-dela de SEUIL_VOLANT, le volant est « tourne ». Les blocs Create poses sans proprietes prennent leurs valeurs par defaut (waterlogged...)."""
import math

from suspendu_outils import AIR, S

SEUIL_VOLANT = 30                       # ticks cumules de rotation pour qu'un volant compte (1,5 s)
COULEURS_VOLANTS = {"v1": "red", "v2": "yellow", "v3": "light_blue", "pA": "lime", "pB": "orange", "pC": "cyan"}


def volant(couleur, haut="up", eau=False):
    return S(f"create:{couleur}_valve_handle", facing=haut, waterlogged=eau)


def lignes_volant(cle, pos_txt, fonction, drapeau, condition="", refus=None):
    """Tick : si le volant `cle` (drapeau 0) est tourne, on cumule ; au seuil, `fonction`. `refus` : fonction appelee si on le tourne alors que `condition` n'est pas remplie."""
    h, c = f"#h{cle}", f"#c{cle}"
    lignes = [f"execute if score {drapeau} station matches 0 store result score {h} station run data get block {pos_txt} InUse",
              f"execute if score {drapeau} station matches 0 if score {h} station matches 1.. {condition} run scoreboard players add {c} station 1",
              f"execute if score {drapeau} station matches 0 if score {h} station matches 1.. {condition} positioned {pos_txt} run particle minecraft:crit ~0.5 ~0.8 ~0.5 0.3 0.2 0.3 0.05 2",
              f"execute if score {drapeau} station matches 0 if score {c} station matches {SEUIL_VOLANT}.. run function atelier:{fonction}"]
    if refus:
        neg = condition.replace("if score", "unless score", 1) if condition.startswith("if ") else condition
        lignes.append(f"execute if score {drapeau} station matches 0 if score {h} station matches 1.. {neg} run function atelier:{refus}")
    return [" ".join(l.split()) for l in lignes]


def ligne_machine(blocs, depart, axe, longueur, moteur=True, motif="cog"):
    """Une ligne d'arbre le long de `axe` ('x' ou 'z'), depuis `depart`, avec un moteur creatif au bout (cache dans le mur) : des engrenages tous les 3, une grande roue tous les 9.
    Tout tourne des que la structure est posee. Renvoie les cases posees."""
    x, y, z = depart
    dx, dz = (1, 0) if axe == "x" else (0, 1)
    poses = []
    if moteur:
        blocs[(x - dx, y, z - dz)] = S("create:creative_motor", facing="east" if axe == "x" else "south")
    for i in range(longueur):
        p = (x + dx * i, y, z + dz * i)
        if motif == "cog" and i % 9 == 4:
            b = S("create:large_cogwheel", axis=axe)
        elif motif == "cog" and i % 3 == 1:
            b = S("create:cogwheel", axis=axe)
        elif motif == "pompe" and i % 6 == 3:
            b = S("create:mechanical_pump", facing="east" if axe == "x" else "south")
        else:
            b = S("create:shaft", axis=axe)
        blocs[p] = b
        poses.append(p)
    return poses


def tuyau(blocs, cases, axe):
    """Des tuyaux Create droits (les deux bouts ouverts) sur `cases`, le long de `axe` ('x', 'y' ou 'z')."""
    ouvert = {"x": dict(east=True, west=True, north=False, south=False, up=False, down=False),
              "y": dict(up=True, down=True, north=False, south=False, east=False, west=False),
              "z": dict(north=True, south=True, east=False, west=False, up=False, down=False)}[axe]
    for p in cases:
        blocs[p] = S("create:fluid_pipe", **ouvert)


def libre(blocs, p):
    return blocs.get(p) == AIR


def decor_acte1(blocs, infos, a1):
    """Le Hall, la Cuve : volants, conduites qui tournent sous le plafond, tuyaux des colonnes de la Cuve, stations de pompage sur la Coursive."""
    for k, p in infos["leviers"].items():
        blocs[p] = volant(COULEURS_VOLANTS[f"v{k}"])
    x0, x1, z0, z1 = a1.HALL
    yh = a1.PLAFOND - 2                                                                     # sous le plafond, le long des murs nord et sud : arbres et engrenages qui tournent
    for z in (z0 + 1, z1 - 1):
        ligne_machine(blocs, (x0 + 1, yh, z), "x", x1 - x0 - 1)
        tuyau(blocs, [(x, yh + 1, z) for x in range(x0 + 1, x1) if libre(blocs, (x, yh + 1, z))], "x")
    cx, cz = a1.CUVE
    for k in a1.TUYAUX:                                                                     # les six colonnes de la Cuve : de vrais tuyaux Create
        a = math.radians(k)
        px, pz = int(math.floor(cx + 28.4 * math.sin(a))), int(math.floor(cz - 28.4 * math.cos(a)))
        for x in (px, px + 1):
            for zz in (pz, pz + 1):
                for y in range(a1.FOND_CUVE + 1, a1.SOL):
                    blocs[(x, y, zz)] = S("create:copper_casing") if y % 8 == 0 else S("create:fluid_pipe", up=True, down=True, north=False, south=False, east=False, west=False)
    stations = []
    for nom, a_deg in (("A", 270), ("C", 0), ("B", 90)):                                   # une station de pompage sur la Coursive, au-dessus de chaque bouche
        a = math.radians(a_deg)
        ux, uz = math.sin(a), -math.cos(a)
        bx, bz = int(math.floor(cx + 33.5 * ux)), int(math.floor(cz + 33.5 * uz))
        axe = "z" if abs(ux) > abs(uz) else "x"
        tx, tz = (0, 1) if axe == "z" else (1, 0)
        depart = (bx - 3 * tx, a1.SOL + 1, bz - 3 * tz)
        poses = ligne_machine(blocs, depart, axe, 7, motif="pompe")
        for p in poses:
            blocs[(p[0], p[1] + 1, p[2])] = S("create:fluid_pipe", up=True, down=True, north=False, south=False, east=False, west=False) if p == poses[3] else blocs.get((p[0], p[1] + 1, p[2]), AIR)
        stations.append((nom, poses))
    infos["stations_pompage"] = stations
    balcons = {}
    for nom in ("A", "C"):                                                                  # des balcons sous les bouches A et C (le dossier) : on y prend pied quand l'eau est plus bas
        a_deg, y0, _ = a1.BOUCHES[nom]
        cases = []
        for x in range(int(cx - 31), int(cx + 32)):
            for zz in range(int(cz - 31), int(cz + 32)):
                r = math.hypot(x + 0.5 - cx, zz + 0.5 - cz)
                d = abs((math.degrees(math.atan2(x + 0.5 - cx, -(zz + 0.5 - cz))) % 360 - a_deg + 180) % 360 - 180)
                if 26.5 <= r < 30 and d < 5.5 and blocs.get((x, y0 - 1, zz)) == AIR:
                    blocs[(x, y0 - 1, zz)] = S("create:copper_tiles") if (x + zz) % 2 else S("minecraft:smooth_quartz")
                    cases.append((x, y0, zz))
        balcons[nom] = cases
    infos["balcons"] = balcons


def decor_secteurs(parts, infos):
    _decor_secteurs(parts, infos)
    salles_des_pompes(parts, infos)
    blocs_blockbench(parts, infos)


def _decor_secteurs(parts, infos):
    """Les secteurs : volants des pompes, turbines en grandes roues Create (sans moteur : le datapack le pose au lancement), machines qui tournent."""
    A, B, C, D = infos["a"], infos["b"], infos["c"], infos["d"]
    A["volant_pos"] = (15, 62, 23)                                                          # pompe A (sous l'eau) : le volant vert, sur son socle (pose dans salles_des_pompes)
    parts["station_b"][B["levier"]] = volant(COULEURS_VOLANTS["pB"])
    B["volant_pos"] = B["levier"]
    parts["station_c"][C["levier"]] = volant(COULEURS_VOLANTS["pC"])
    C["volant_pos"] = C["levier"]
    # secteur C : l'arbre central en arbres Create, coupe aux grilles (trois troncons : chaque turbine a le sien) ; chaque turbine, deux grandes roues dentees
    cx, cz = int(C["puits"][0]), int(C["puits"][1])
    grilles = set(C["grilles"])
    moteurs = {}
    for (x, y, z), b in list(parts["station_c"].items()):
        if (x, z) == (cx, cz) and b[0] == "minecraft:waxed_copper_block":
            parts["station_c"][(x, y, z)] = S("minecraft:waxed_copper_block") if y in grilles else S("create:shaft", axis="y", waterlogged=True)
    bas = [48] + [g + 1 for g in sorted(grilles)]
    for k, pale in C["pales"].items():
        for p in pale["croix"] + pale["diag"]:
            if parts["station_c"].get(p, ("",))[0] == "minecraft:waxed_cut_copper":
                parts["station_c"][p] = S("minecraft:water", level=0)
        _, yt, _ = pale["centre"]
        for y in (yt, yt + 1):
            parts["station_c"][(cx, y, cz)] = S("create:large_cogwheel", axis="y", waterlogged=True)
        moteurs[k] = (cx, bas[k - 1], cz)
    C["moteurs"] = moteurs
    # la Cheminee : sa turbine aussi
    tc = D["turbine"]["centre"]
    for p in D["turbine"]["croix"] + D["turbine"]["diag"]:
        if parts["station_cheminee"].get(p, ("",))[0] == "minecraft:waxed_cut_copper":
            parts["station_cheminee"][p] = S("minecraft:water", level=0)
    parts["station_cheminee"][tc] = S("create:large_cogwheel", axis="y", waterlogged=True)
    parts["station_cheminee"][(tc[0], tc[1] + 1, tc[2])] = S("create:large_cogwheel", axis="y", waterlogged=True)
    D["moteur"] = (tc[0], tc[1] - 1, tc[2])
    parts["station_cheminee"][D["moteur"]] = S("create:shaft", axis="y", waterlogged=True)
    # secteur B : la halle haute a ses machines (arbres et engrenages sous les poutres), les chaudieres leurs cuves Create
    bx0, bx1, bz0, bz1, _, by1 = B["b3"]                                                 # (x0, x1, z0, z1, y0, y1)
    ligne_machine(parts["station_b"], (bx0 + 1, by1 - 2, bz0 + 1), "x", bx1 - bx0 - 1)
    ligne_machine(parts["station_b"], (bx0 + 1, by1 - 2, bz1 - 1), "x", bx1 - bx0 - 1)
    for (x0, x1, _, _, z0, z1) in B["vapeur"]:                                         # sous les passerelles a vapeur : des ventilateurs Create tournes vers le haut
        parts["station_b"][((x0 + x1) // 2, 62, (z0 + z1) // 2)] = S("create:encased_fan", facing="up")
    # secteur A : des pompes et des tuyaux le long du plafond de la salle des lits
    ax0, ax1, az0, az1, _, ay1 = A["a3"]                                                 # (x0, x1, z0, z1, y0, y1)
    ligne_machine(parts["station_a"], (ax0 + 1, ay1 - 1, az0 + 2), "x", ax1 - ax0 - 1, motif="pompe")
    tuyau(parts["station_a"], [(x, ay1, az0 + 2) for x in range(ax0 + 1, ax1)], "x")


# ================================================================= les salles des pompes, le chemin pour y aller, le decor des secteurs (refonte du 18/11)
SOLIDE_NON = ("minecraft:water", "minecraft:air", "minecraft:light")


def _bloc(parts, p):
    for n in parts:
        if p in parts[n]:
            return parts[n][p]
    return None


def _sol(parts, x, z, y_haut, y_bas):
    """Le bloc de sol (le premier solide sous l'eau ou l'air) dans la colonne (x, z), en descendant de y_haut : y, ou None."""
    for y in range(y_haut, y_bas - 1, -1):
        b, dessus = _bloc(parts, (x, y, z)), _bloc(parts, (x, y + 1, z))
        if b is not None and b[0] not in SOLIDE_NON and dessus is not None and dessus[0] in ("minecraft:water", "minecraft:air"):
            return y
    return None


def ligne_au_sol(parts, nom, points, y_haut, y_bas, bloc):
    """Une ligne de couleur au sol, de point en point (segments droits en x puis en z) : on remplace le bloc de sol, sauf les marches, dalles, lampes et plaques."""
    poses = []
    for (xa, za), (xb, zb) in zip(points, points[1:]):
        cases = [(x, za) for x in range(min(xa, xb), max(xa, xb) + 1)] + [(xb, z) for z in range(min(za, zb), max(za, zb) + 1)]
        for (x, z) in cases:
            y = _sol(parts, x, z, y_haut, y_bas)
            if y is None:
                continue
            b = parts[nom].get((x, y, z))
            if b is None or any(m in b[0] for m in ("stairs", "slab", "lantern", "froglight", "cut_copper", "grate", "light")):
                continue
            parts[nom][(x, y, z)] = bloc
            poses.append((x, y, z))
    return poses


def salles_des_pompes(parts, infos):
    A, B, C = infos["a"], infos["b"], infos["c"]
    pompes = {}
    # --- secteur A : des lits de filtration vitres, une grande machine dans la salle de la pompe
    chemin_a = []                                                                           # plus de ligne de couleur au sol (demande de Luca)
    for (x0, x1, z0, z1) in ((30, 33, 3, 30), (44, 47, 15, 42), (58, 61, 3, 30)):           # les lits : une cuve de verre cerclee de cuivre, on voit le sable et le gravier
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                bord_x, bord_z = x in (x0, x1), z in (z0, z1)
                for y in range(61, 69):
                    if y == 68 or (bord_x and bord_z):
                        parts["station_a"][(x, y, z)] = S("create:copper_casing")
                    elif bord_x or bord_z:
                        parts["station_a"][(x, y, z)] = S("create:framed_glass")
    ax0, ax1, az0, az1, _, ay1 = A["a3"]                                                 # (x0, x1, z0, z1, y0, y1)
    for z in (az0 + 1, az1 - 1):                                                                # des tuyaux le long des deux longs murs
        tuyau(parts["station_a"], [(x, ay1 - 3, z) for x in range(ax0 + 1, ax1) if parts["station_a"].get((x, ay1 - 3, z), ("",))[0] == "minecraft:water"], "x")
    pompes["A"] = salle_pompe_a(parts["station_a"], A)
    # --- secteur B : des machines dans les chaudieres
    chemin_b = []
    bx0, bx1, bz0, bz1, _, by1 = B["b2"]                                                 # (x0, x1, z0, z1, y0, y1)
    ligne_machine(parts["station_b"], (bx0 + 1, by1, bz0 + 2), "x", bx1 - bx0 - 1)
    ligne_machine(parts["station_b"], (bx0 + 1, by1, bz1 - 2), "x", bx1 - bx0 - 1)
    tuyau(parts["station_b"], [(x, 46, 80) for x in range(202, 222) if parts["station_b"].get((x, 46, 80), ("",))[0] == "minecraft:water"], "x")
    moteur_b = (224, 69, 91)
    ligne_machine(parts["station_b"], (224, 69, 92), "z", 10, moteur=False, motif="pompe")
    pompes["B"] = {"moteur": moteur_b, "facing": "south", "volant": B["volant_pos"]}
    # --- secteur C : la salle de la pompe a sa machine et ses cuves
    moteur_c = (112, 93, 4)
    ligne_machine(parts["station_c"], (113, 93, 4), "x", 15, moteur=False, motif="pompe")
    for x in (117, 121, 125):
        parts["station_c"][(x, 86, 11)] = S("create:fluid_tank", bottom=True, top=True, shape="window")
        tuyau(parts["station_c"], [(x, 87, 11), (x, 88, 11), (x, 89, 11), (x, 90, 11), (x, 91, 11), (x, 92, 11)], "y")
    pompes["C"] = {"moteur": moteur_c, "facing": "east", "volant": C["volant_pos"]}
    # --- le Poste : des compteurs Create au pied du tableau de bord
    for z in (41, 43, 47, 49):
        parts["station_cheminee"][(145, 85, z)] = S("create:speedometer", facing="east")
    infos["pompes"] = pompes
    infos["chemins"] = {"A": chemin_a, "B": chemin_b}
    import station_salle_lits                                                               # la salle des lits refaite : de vraies cuves de filtration (module d'une autre session)
    infos["salle_lits"] = station_salle_lits.refaire(parts, infos)
    # des panneaux debout (le datapack les pose) : (case, rotation 0 sud / 4 ouest / 8 nord / 12 est, lignes, sous l'eau)
    infos["panneaux_secteurs"] = [
        ((82, 64, 93), 12, ("A  FILTRATION", "Pompe A :", "au bout,", "a l'ouest"), True),
        ((75, 64, 89), 0, ("Galerie", "au bout : la", "salle des lits", ""), True),
        ((74, 61, 40), 0, ("Salle des lits", "gardiens !", "Pompe A :", "a l'ouest"), True),
        ((19, 61, 26), 12, ("POMPE A", "tourne le", "volant vert", ""), True),
        ((155, 32, 93), 4, ("B  CHAUFFERIE", "contre le courant :", "marche au fond", ""), True),
        ((192, 40, 93), 4, ("3 plaques :", "tiens-toi debout", "dessus, dans", "l'ordre 1 2 3"), True),
        ((197, 58, 87), 12, ("POMPE B", "au bout, a l'est", "au grappin", "volant orange"), False),
        ((120, 48, 57), 0, ("C  TURBINES", "nage contre une", "turbine pour", "la lancer"), True),
        ((120, 86, 12), 0, ("POMPE C", "volant cyan", "", ""), False)]


def salle_pompe_a(blocs, A):
    """La salle de la Pompe A (sous l'eau) refaite : une machine Create au centre (socle de cuivre, arbre vertical, deux grandes roues, quatre cuves et leurs colonnes
    de tuyaux jusqu'au plafond), le volant vert sur un socle devant elle, des murs de carreaux de cuivre et de vitres eclairees, des poutres au plafond.
    Le moteur (sous l'arbre) n'est pose qu'au retour de la pompe : alors tout tourne."""
    EAU = S("minecraft:water", level=0)
    x0, x1, z0, z1, y0, y1 = 2, 20, 12, 34, 61, 72                                           # l'interieur de la salle (sol a 60)
    for p in list(A["pompe"]):                                                              # l'ancien corps de pompe (une colonne de cuivre) s'en va
        blocs[p] = EAU
    for (x, y, z) in [(x, y, z) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1) for z in range(z0, z1 + 1)]:
        if blocs.get((x, y, z), ("",))[0].startswith("create:") and (x, y, z) != (15, 62, 23):
            blocs[(x, y, z)] = EAU                                                          # l'ancienne ligne de machines du plafond
    cx, cz = 10, 23
    for x in range(cx - 2, cx + 3):                                                         # le socle 5 x 5
        for z in range(cz - 2, cz + 3):
            blocs[(x, y0, z)] = S("create:copper_casing")
    for y in range(y0 + 1, y0 + 7):                                                         # l'arbre et ses deux grandes roues
        blocs[(cx, y, cz)] = S("create:large_cogwheel", axis="y") if y in (y0 + 2, y0 + 5) else S("create:shaft", axis="y")
    blocs[(cx, y0 + 7, cz)] = S("create:copper_casing")
    for (x, z) in ((cx - 2, cz - 2), (cx + 2, cz - 2), (cx - 2, cz + 2), (cx + 2, cz + 2)):  # quatre cuves aux angles, et leurs colonnes de tuyaux jusqu'au plafond
        blocs[(x, y0 + 1, z)] = S("create:fluid_tank", bottom=True, top=True, shape="window")
        for y in range(y0 + 2, y1 + 1):
            blocs[(x, y, z)] = S("create:fluid_pipe", up=True, down=True, north=False, south=False, east=False, west=False)
    for x in range(cx - 2, cx + 3):                                                         # une couronne de tuyaux sous le plafond, qui relie les colonnes
        for z in (cz - 2, cz + 2):
            if x not in (cx - 2, cx + 2):
                blocs[(x, y1, z)] = S("create:fluid_pipe", east=True, west=True, north=False, south=False, up=False, down=False)
    for z in range(cz - 1, cz + 2):
        for x in (cx - 2, cx + 2):
            blocs[(x, y1, z)] = S("create:fluid_pipe", north=True, south=True, east=False, west=False, up=False, down=False)
    blocs[(15, 61, 23)] = S("create:copper_casing")                                         # le socle du volant, et le volant vert
    blocs[(15, 62, 23)] = volant(COULEURS_VOLANTS["pA"], eau=True)
    for (x, z) in ((cx - 3, cz - 3), (cx + 3, cz - 3), (cx - 3, cz + 3), (cx + 3, cz + 3), (16, 21), (16, 25)):
        blocs[(x, y0 - 1, z)] = S("minecraft:verdant_froglight", axis="y")                  # des feux verts dans le sol autour de la machine
    for x in range(x0 - 1, x1 + 2):                                                         # les murs : carreaux de cuivre en bas, une bande de cuivre, des vitres eclairees en haut
        for z in range(z0 - 1, z1 + 2):
            if x0 <= x <= x1 and z0 <= z <= z1:
                continue
            for y in range(y0, y1 + 1):
                if blocs.get((x, y, z), ("",))[0] in ("minecraft:water", "minecraft:air") or (x == x1 + 1 and 20 <= z <= 26 and y <= 66):
                    continue                                                                # le passage vers la salle des lits reste ouvert
                if y <= y0 + 2:
                    blocs[(x, y, z)] = S("create:copper_tiles")
                elif y == y0 + 3:
                    blocs[(x, y, z)] = S("create:copper_casing")
                elif y0 + 6 <= y <= y0 + 8 and (x + z) % 4 == 0:
                    blocs[(x, y, z)] = S("create:framed_glass")
                    ex = (x + (1 if x > x1 else -1 if x < x0 else 0), y, z + (1 if z > z1 else -1 if z < z0 else 0))
                    blocs[ex] = S("minecraft:verdant_froglight", axis="y")
                else:
                    blocs[(x, y, z)] = S("minecraft:smooth_sandstone")
    for x in range(x0, x1 + 1, 4):                                                          # des poutres de cuivre sous le plafond
        for z in range(z0, z1 + 1):
            if blocs.get((x, y1 + 1, z)) is not None:
                blocs[(x, y1 + 1, z)] = S("create:copper_casing")
    A["pompe"] = [(cx, y0 + 3, cz)]
    return {"moteur": (cx, y0, cz), "facing": "up", "volant": (15, 62, 23)}


# ================================================================= les blocs Blockbench du grappin 3.10.0 : pompe de station, chaudiere, manometre
LIBRE = ("minecraft:water", "minecraft:air")


def _poser_si(parts, nom, p, bloc, dos=None):
    """Pose `bloc` en p si la case est de l'eau ou de l'air (et, pour un bloc mural, si la case `dos` est pleine). Renvoie True si pose."""
    b = parts[nom].get(p)
    if b is None or b[0] not in LIBRE:
        return False
    if dos is not None:
        d = _bloc(parts, dos)
        if d is None or d[0] in LIBRE:
            return False
    parts[nom][p] = bloc
    return True


def blocs_blockbench(parts, infos):
    eau = lambda nom, p: parts[nom].get(p, ("",))[0] == "minecraft:water"            # noqa: E731
    pompes = infos["pompes"]
    for k, nom, p, face in (("A", "station_a", (16, 61, 19), "west"), ("B", "station_b", (224, 64, 95), "west"), ("C", "station_c", (124, 86, 7), "west")):
        dans_eau = eau(nom, p)
        if _poser_si(parts, nom, p, S("donjon:pompe_station", facing=face, en_marche=False, waterlogged=dans_eau)):
            pompes[k]["pompe_bb"] = (p, face, dans_eau)
    chaudieres = []
    for nom, p, face in (("station_b", (194, 58, 74), "south"), ("station_b", (200, 58, 74), "south"), ("station_b", (206, 58, 74), "south"),
                         ("station_b", (194, 58, 117), "north"), ("station_b", (200, 58, 117), "north"), ("station_b", (206, 58, 117), "north"),
                         ("station_b", (192, 40, 73), "south"), ("station_b", (192, 40, 118), "north"), ("station_b", (226, 40, 118), "north")):
        if _poser_si(parts, nom, p, S("donjon:chaudiere", facing=face, allumee=True, waterlogged=eau(nom, p))):
            chaudieres.append(p)
    cadrans = []
    for nom, p, face in (("station_cheminee", (145, 87, 42), "east"), ("station_cheminee", (145, 87, 46), "east"),
                         ("station_a", (2, 64, 20), "east"), ("station_a", (2, 64, 26), "east"),
                         ("station_c", (118, 89, 2), "south"), ("station_c", (122, 89, 2), "south"),
                         ("station_b", (228, 66, 94), "west"), ("station_b", (228, 66, 98), "west")):
        dx, dz = {"east": (-1, 0), "west": (1, 0), "south": (0, -1), "north": (0, 1)}[face]
        if _poser_si(parts, nom, p, S("donjon:cadran", facing=face, waterlogged=eau(nom, p)), dos=(p[0] + dx, p[1], p[2] + dz)):
            cadrans.append(p)
    infos["blockbench"] = {"chaudieres": chaudieres, "cadrans": cadrans, "pompes_reservoir": reservoir_blockbench(parts, infos, cadrans)}


def _vers(p, centre):
    """La direction horizontale (facing) qui regarde de p vers centre."""
    dx, dz = centre[0] - (p[0] + 0.5), centre[1] - (p[2] + 0.5)
    if abs(dx) > abs(dz):
        return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"


def _place_pompe(parts, nom, p):
    """Le modele de la pompe deborde sur les 3 x 2 x 3 cases autour de son bloc : elles doivent etre libres, et le sol dessous plein."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            sol = _bloc(parts, (p[0] + dx, p[1] - 1, p[2] + dz))
            if sol is None or sol[0] in LIBRE:
                return False
            for dy in (0, 1):
                b = _bloc(parts, (p[0] + dx, p[1] + dy, p[2] + dz))
                if b is None or b[0] not in LIBRE:
                    return False
    return True


def reservoir_blockbench(parts, infos, cadrans):
    """Le Reservoir du boss : une couronne de six pompes au sol, tournees vers le centre (elles se mettent en marche quand la troisieme pompe
    des secteurs repart : station_logique), et six manometres sur la paroi, entre elles."""
    R = infos["r"]
    cx, cz = R["centre"]
    y = R["y"][0]
    poses = []
    for az in range(30, 360, 60):
        a = math.radians(az)
        p = (int(math.floor(cx + 37 * math.sin(a))), y, int(math.floor(cz - 37 * math.cos(a))))
        face = _vers(p, (cx, cz))
        if _place_pompe(parts, "station_reservoir", p) and _poser_si(parts, "station_reservoir", p,
                                                                       S("donjon:pompe_station", facing=face, en_marche=False, waterlogged=True)):
            poses.append((p, face))
    for az in range(0, 360, 60):
        a = math.radians(az)
        for r in (43, 42):                                                                    # contre la paroi (rayon 44) : le premier qui a un mur derriere
            p = (int(math.floor(cx + r * math.sin(a))), y + 3, int(math.floor(cz - r * math.cos(a))))
            face = _vers(p, (cx, cz))
            dx, dz = {"east": (-1, 0), "west": (1, 0), "south": (0, -1), "north": (0, 1)}[face]
            if _poser_si(parts, "station_reservoir", p, S("donjon:cadran", facing=face, waterlogged=True), dos=(p[0] + dx, p[1], p[2] + dz)):
                cadrans.append(p)
                break
    return poses
