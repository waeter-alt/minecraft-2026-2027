# [claude-build]
"""suspendu_salle1.py - Donjon Suspendu, salle 1 (rez-de-chaussee) : la salle centrale a huit cotes (environ 30 de large, 14 de haut), son couloir d'entree au sud, ses trois portes de pierre au nord
(1 au nord-est, 2 au nord-ouest, 3 au milieu) et les couloirs derriere : 1 et 2 finissent par un escalier qui descend (vers les sous-sols, a construire), 3 est droit (suite a definir).
Plan : atelier/docs/donjon_suspendu/plan_salle1_A.png, d'apres le croquis de Luca. Le rez-de-chaussee est en surface : le sol de la salle est a Y0 (le niveau de l'herbe), le batiment est au dessus,
les escaliers creusent la roche en dessous. Pur calcul : renvoie les blocs et les reperes, sans ecrire de fichier.
Coordonnees : (x, y, z) du gabarit du donjon ; le plan (x, z en blocs, origine au coin nord-ouest de la salle) est decale de (DX, DZ)."""
import math
from functools import lru_cache

from suspendu_outils import AIR, S, h01

DX, DZ = 60, 40                          # decalage plan -> gabarit
Y0 = 85                                  # Y du sol du rez-de-chaussee (le bloc d'herbe du superflat profond, voir donjon_suspendu.py)
HAUT_SALLE, HAUT_COULOIR, HAUT_ENTREE = 14, 8, 6      # hauteur libre au dessus du sol
HAUT_PORTE = 8                           # hauteur des portes de pierre (la dalle monte dans le linteau pour s'ouvrir)
PAS_LANTERNE = 5                         # dans les couloirs : une lanterne suspendue (a une chaine) a moins de 5 blocs de toute case du sol (les coudes allongent le trajet de la lumiere)
PAS_SOL = 9                              # dans la salle : un feu de sol (lanterne de mer encastree dans le dallage) a moins de 9 blocs de toute case ; au sol, de la lumiere 5 au moins
SALLE = [(6, 0), (20, 0), (30, 13), (27, 26), (17.5, 33), (12.5, 33), (0, 19), (0, 7)]
PORTES = {"1": ("c1", (20, 0), (30, 13)), "2": ("c2", (0, 7), (6, 0)), "3": ("c3", (6, 0), (20, 0))}      # nom: (couloir, extremites du mur dans le plan)
COULOIRS = {"entree": {"rect": (12.0, 17.0, 33.0, 47.0), "haut": HAUT_ENTREE},
            "c1": {"ligne": [(25, 6), (34, -3), (41.5, -4.5), (47.5, -2), (44.5, 11), (44.5, 19)], "larg": 7, "haut": HAUT_COULOIR},
            "c2": {"ligne": [(2.5, 5.5), (-5, -3), (-13.5, -4.5), (-17, -2), (-30, -4), (-41, -3), (-45.5, 3), (-45.5, 19)], "larg": 7, "haut": HAUT_COULOIR},      # prolonge vers l'ouest : la grande salle du sous-sol 1 barre le chemin droit
            "c3": {"ligne": [(13, 0), (13, -14)], "larg": 12, "haut": HAUT_COULOIR}}
ESCALIERS = {"c1": 11, "c2": 11}         # couloir: premiere ligne (en Z du plan) des marches ; elles descendent vers le sud, 12 marches
NB_MARCHES = {"c1": 27, "c2": 46}                                   # marches de chaque escalier : celui de la porte 1 descend de 27 jusqu'au sol du sous-sol 1 (28 sous le rez-de-chaussee) ; celui de la porte 2 descend de 46 jusqu'au sol du sous-sol 2 (qui a ete remonte de 9 blocs pour que le fond de la fosse puisse descendre jusqu'au socle)
                                                                    # (56 sous le rez-de-chaussee), par l'ouest : le couloir 2 est prolonge vers l'ouest au niveau du rez-de-chaussee, hors de l'emprise de la grande salle du sous-sol 1
NB_BATIMENT = 12                                                    # les 12 premieres marches sont dans le batiment du rez-de-chaussee ; les suivantes sont un tunnel dans la roche
TUNNEL_X = {"c1": (101, 107), "c2": (11, 17)}                       # colonnes du tunnel de chaque escalier (7 de large, dans l'axe du couloir)
HAUT_TUNNEL = 6                                                     # air au dessus d'une marche du tunnel (3 avant) : un grand escalier, haut de plafond
RANG_BANDE = 4                                                      # tous les 4 rangs de marches : une bande de briques sculptees sur les parois et le plafond, et une lanterne dans chaque paroi
ZOMBIES = [(5.1, 12), (24.2, 12.9), (20, 22.8)]
COFFRE = (14.5, 14.5)
CENTRE = (15, 16)                        # centre de la salle : le combat commence quand un joueur s'en approche
RAYON_COMBAT = 13
ENTREE_PLAN = (14.5, 46.5)               # derniere case du couloir d'entree (dehors, sur le sol de la plaine)


def vers_plan(cx, cz):
    return cx + 0.5 - DX, cz + 0.5 - DZ


def vers_case(x, z):
    return math.floor(x + DX), math.floor(z + DZ)


def dans_polygone(px, pz, poly):
    dedans = False
    n = len(poly)
    for i in range(n):
        (x0, z0), (x1, z1) = poly[i], poly[(i + 1) % n]
        if (z0 > pz) != (z1 > pz) and px < (x1 - x0) * (pz - z0) / (z1 - z0) + x0:
            dedans = not dedans
    return dedans


def dist_segment(px, pz, a, b):
    (x0, z0), (x1, z1) = a, b
    dx, dz = x1 - x0, z1 - z0
    t = 0 if dx == dz == 0 else max(0.0, min(1.0, ((px - x0) * dx + (pz - z0) * dz) / (dx * dx + dz * dz)))
    return math.hypot(px - (x0 + t * dx), pz - (z0 + t * dz))


def dist_ligne(px, pz, ligne):
    return min(dist_segment(px, pz, a, b) for a, b in zip(ligne, ligne[1:]))


def _cases_couloir(c):
    plage = range(-64, 64)
    cases = set()
    if "rect" in c:
        x0, x1, z0, z1 = c["rect"]
        for cx in range(DX + math.floor(x0), DX + math.ceil(x1) + 1):
            for cz in range(DZ + math.floor(z0), DZ + math.ceil(z1) + 1):
                px, pz = vers_plan(cx, cz)
                if x0 <= px <= x1 and z0 <= pz <= z1:
                    cases.add((cx, cz))
        return cases
    for cx in range(DX + plage.start, DX + plage.stop):
        for cz in range(DZ - 24, DZ + 50):
            px, pz = vers_plan(cx, cz)
            if dist_ligne(px, pz, c["ligne"]) <= c["larg"] / 2:
                cases.add((cx, cz))
    return cases


def _voisines(cases, rayon):
    return {(x + dx, z + dz) for (x, z) in cases for dx in range(-rayon, rayon + 1) for dz in range(-rayon, rayon + 1)}


def distance_octogonale(px, pz, c):
    dx, dz = abs(px - c[0]), abs(pz - c[1])
    return max(dx, dz, (dx + dz) * 0.7071)


def sol(x, z):
    """Dallage : un emblem octogonal autour du coffre (un disque de pierre sombre, un anneau de briques sculptees), puis pierre polie et briques par carres de 3."""
    d = distance_octogonale(*vers_plan(x, z), COFFRE)
    if d < 2.5:
        return S("polished_deepslate")
    if d < 3.5:
        return S("chiseled_stone_bricks")
    return S("polished_andesite") if (x // 3 + z // 3) % 2 == 0 else S("stone_bricks")


def mur(x, y, z):
    r = h01(x, y, z, 7)
    return S("stone_bricks") if r < 0.70 else (S("cracked_stone_bricks") if r < 0.82 else (S("mossy_stone_bricks") if r < 0.94 else S("stone")))


CADRE = S("chiseled_stone_bricks")                                            # l'arc qui encadre la dalle
# la couleur de chaque porte : (dalle, spirale gravee au centre, nom de la couleur) ; la cle qui l'ouvre porte la meme couleur
COULEURS_PORTES = {"1": ("blue_concrete", "white_concrete", "bleue"), "2": ("yellow_concrete", "black_concrete", "jaune"), "3": ("red_concrete", "gold_block", "rouge")}
MATERIAUX_PORTES = {nom: (S(d), S(s)) for nom, (d, s, _) in COULEURS_PORTES.items()}


def profil(largeur, u):
    """Hauteur libre de la porte a la position u (en blocs, 0 au milieu) : droite sur les cotes (5), arrondie vers le milieu (jusqu'a HAUT_PORTE) : une porte en arc."""
    r = max(0.0, 1 - (u / (largeur / 2)) ** 2)
    return min(HAUT_PORTE, 5 + round(3 * math.sqrt(r)))


def spirale():
    """La spirale du centre de chaque porte : {(iu, iv)} dans un carre de 7 x 7 (iu le long de la porte, iv de bas en haut, 0 au milieu), un trait d'un bloc, un vide entre deux tours."""
    x = y = 0
    pts = [(0, 0)]
    dirs = [(1, 0), (0, 1), (-1, 0), (0, -1)]
    for k, n in enumerate((2, 2, 4, 4, 6, 6)):
        for _ in range(n):
            x, y = x + dirs[k % 4][0], y + dirs[k % 4][1]
            pts.append((x, y))
    return {(px - 1, py - 1) for px, py in pts}


SPIRALE = spirale()


@lru_cache(maxsize=None)
def construire():
    """Renvoie (blocs, infos) : blocs {(x, y, z): etat} et infos (cases, reperes, sources de lumiere)."""
    salle = set()
    for cx in range(DX - 8, DX + 40):
        for cz in range(DZ - 2, DZ + 40):
            if dans_polygone(*vers_plan(cx, cz), SALLE):
                salle.add((cx, cz))
    couloirs = {nom: _cases_couloir(c) for nom, c in COULOIRS.items()}
    air = {c: HAUT_SALLE for c in salle}
    for nom, cases in couloirs.items():
        for c in cases:
            air[c] = max(air.get(c, 0), COULOIRS[nom]["haut"])
    # les cases des portes : cases du couloir juste au dehors du mur de la salle ; pour chaque porte, la position u de chaque case le long du mur (0 au milieu) et sa largeur
    cases_portes, reperes = {}, {}
    for nom, (couloir, a, b) in PORTES.items():
        cases_portes[nom] = {c for c in couloirs[couloir] if c not in salle and dist_segment(*vers_plan(*c), a, b) <= 1.6}
        longueur = math.hypot(b[0] - a[0], b[1] - a[1])
        d = ((b[0] - a[0]) / longueur, (b[1] - a[1]) / longueur)
        plans = {c: vers_plan(*c) for c in cases_portes[nom]}
        mx = sum(p[0] for p in plans.values()) / len(plans)
        mz = sum(p[1] for p in plans.values()) / len(plans)
        u = {c: (p[0] - mx) * d[0] + (p[1] - mz) * d[1] for c, p in plans.items()}
        reperes[nom] = {"u": u, "largeur": max(u.values()) - min(u.values()) + 1, "centre": (mx, mz)}
    toutes_portes = set().union(*cases_portes.values())
    # les murs : deux cases d'epaisseur autour de l'air
    hauteur_mur = {}
    for (x, z), h in air.items():
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                c = (x + dx, z + dz)
                if c not in air:
                    hauteur_mur[c] = max(hauteur_mur.get(c, 0), h)
    for c in list(hauteur_mur):                                    # le couloir d'entree reste ouvert au bout (sur la plaine)
        px, pz = vers_plan(*c)
        if pz >= COULOIRS["entree"]["rect"][3] and COULOIRS["entree"]["rect"][0] <= px <= COULOIRS["entree"]["rect"][1]:
            del hauteur_mur[c]
    blocs = {}
    dalles = {n: [] for n in PORTES}                              # les blocs de chaque dalle : (x, y, z, nom du bloc) ; a l'ouverture, ils deviennent des blocs affiches qui glissent vers le haut
    for (x, z), h in air.items():
        blocs[(x, Y0, z)] = sol(x, z)
        if (x, z) in toutes_portes:                               # une porte : dalle coloree en arc, spirale au milieu, cadre d'un autre bloc, mur au dessus
            nom = next(n for n, cs in cases_portes.items() if (x, z) in cs)
            u = reperes[nom]["u"][(x, z)]
            haut = profil(reperes[nom]["largeur"], u)
            for yr in range(1, HAUT_PORTE + 1):
                if yr <= haut:
                    blocs[(x, Y0 + yr, z)] = MATERIAUX_PORTES[nom][1] if (math.floor(u + 0.5), yr - 4) in SPIRALE else MATERIAUX_PORTES[nom][0]
                    dalles[nom].append((x, Y0 + yr, z, blocs[(x, Y0 + yr, z)][0]))
                else:
                    blocs[(x, Y0 + yr, z)] = CADRE if yr == haut + 1 else mur(x, Y0 + yr, z)
            for y in range(Y0 + HAUT_PORTE + 1, Y0 + HAUT_SALLE + 2):
                blocs[(x, y, z)] = CADRE if (y == Y0 + haut + 1) else mur(x, y, z)
            continue
        for y in range(Y0 + 1, Y0 + h + 1):
            blocs[(x, y, z)] = AIR
        blocs[(x, Y0 + h + 1, z)] = mur(x, Y0 + h + 1, z)
    for (x, z), h in hauteur_mur.items():
        for y in range(Y0, Y0 + h + 2):
            blocs[(x, y, z)] = mur(x, y, z)
    # au dessus d'un couloir plus bas que la salle (le couloir d'entree), le mur de la salle se prolonge : sinon on voit le toit de la salle a travers
    for (x, z), h in air.items():
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, z + dz)
            hn = air.get(n)
            if hn is not None and hn < h and n not in toutes_portes:
                for y in range(Y0 + hn + 2, Y0 + h + 2):
                    blocs.setdefault((n[0], y, n[1]), mur(n[0], y, n[1]))
    # les pilastres : 24 colonnes engagees dans les murs de la salle, loin des ouvertures ; elles rythment les murs
    ouvertures = {c for c in air if c not in salle} | toutes_portes
    bords = sorted(c for c in salle if any((c[0] + ddx, c[1] + ddz) not in air for ddx, ddz in ((1, 0), (-1, 0), (0, 1), (0, -1))))
    pilastres = {}
    for c in bords:
        if any(max(abs(c[0] - o[0]), abs(c[1] - o[1])) <= 4 for o in ouvertures):
            continue
        px, pz = vers_plan(*c)
        secteur = math.floor(math.degrees(math.atan2(pz - CENTRE[1], px - CENTRE[0])) / 15)
        pilastres.setdefault(secteur, c)
    for (x, z) in pilastres.values():
        for y in range(Y0 + 1, Y0 + HAUT_SALLE + 1):
            blocs[(x, y, z)] = CADRE if y in (Y0 + 1, Y0 + HAUT_SALLE - 1) else S("stone_bricks")
    # l'escalier de chaque couloir : de Y0 - 1 vers le bas, vers le sud ; on creuse la roche, le sol du couloir disparait. Une rangee = une marche : 7 de large, un tapis central de pierre polie
    marches, cavite, lignes = {}, {}, {}
    for nom, z_plan in ESCALIERS.items():
        cz0 = DZ + z_plan
        marches[nom] = []
        for (cx, cz) in sorted(couloirs[nom]):
            i = cz - cz0
            if 0 <= i < min(NB_MARCHES[nom], NB_BATIMENT) and (cx, cz) not in toutes_portes:
                y = Y0 - 1 - i
                for yy in range(y + 1, Y0 + 1):
                    blocs[(cx, yy, cz)] = AIR
                    cavite[(cx, yy, cz)] = nom
                lignes.setdefault((nom, i), []).append((cx, y, cz))
        if nom in TUNNEL_X:                                       # la suite, hors du batiment : un tunnel dans la roche, aussi large que l'escalier et haut de HAUT_TUNNEL
            for i in range(NB_BATIMENT, NB_MARCHES[nom]):
                y = Y0 - 1 - i
                for cx in range(TUNNEL_X[nom][0], TUNNEL_X[nom][1] + 1):
                    for yy in range(y + 1, y + HAUT_TUNNEL + 1):
                        blocs[(cx, yy, cz0 + i)] = AIR
                        cavite[(cx, yy, cz0 + i)] = nom
                    lignes.setdefault((nom, i), []).append((cx, y, cz0 + i))
    for (nom, i), cellules in sorted(lignes.items()):
        milieu = (min(c[0] for c in cellules) + max(c[0] for c in cellules)) / 2
        for (cx, y, cz) in cellules:
            blocs[(cx, y, cz)] = S("polished_andesite_stairs" if abs(cx - milieu) <= 1.5 else "stone_brick_stairs", facing="north", half="bottom", shape="straight", waterlogged=False)
            marches[nom].append((cx, y + 1, cz))
    # l'habillage : toute case voisine de l'air de l'escalier (parois, plafond, fond) devient du mur, sinon on voit la terre et la roche ; l'escalier de la porte 1 continue dans le sous-sol 1, on ne ferme pas son bout
    derniere = {nom: DZ + ESCALIERS[nom] + NB_MARCHES[nom] - 1 for nom in ESCALIERS}
    for (x, y, z), nom in sorted(cavite.items()):
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + dx, y + dy, z + dz)
            if n in cavite or n in blocs or (nom in TUNNEL_X and n[2] > derniere[nom]):
                continue
            rang = n[2] - (DZ + ESCALIERS[nom])
            blocs[n] = CADRE if (rang % RANG_BANDE == 0 and (dx != 0 or dy > 0)) else mur(*n)
    # lumiere : des lanternes suspendues a une chaine, sur une grille ; des lanternes de mer dans les parois des escaliers
    sources = []
    cases_marches = {(m[0], m[2]) for liste in marches.values() for m in liste}
    # la salle : des feux de sol (lanternes de mer encastrees dans le dallage) : quatre en croix autour du coffre, puis ce qu'il faut pour couvrir les coins ; au plafond, seulement un lustre
    reserves = {vers_case(*COFFRE)} | {vers_case(*z) for z in ZOMBIES} | set(pilastres.values())
    feux = []
    for k in range(4):                                                  # quatre feux en croix a 4 du coffre : les branches de l'emblem du sol
        a_ = k * math.pi / 2
        c = vers_case(COFFRE[0] + 4 * math.cos(a_), COFFRE[1] + 4 * math.sin(a_))
        if c in salle and c not in reserves:
            feux.append(c)
    candidats = sorted(c for c in salle if c not in reserves)
    while True:                                                         # le plus eloigne d'abord : on ajoute un feu au point le moins eclaire, tant qu'une case est a plus de PAS_SOL d'un feu
        loin = max(candidats, key=lambda c: (min(abs(c[0] - f[0]) + abs(c[1] - f[1]) for f in feux) if feux else 99, c))
        if feux and min(abs(loin[0] - f[0]) + abs(loin[1] - f[1]) for f in feux) <= PAS_SOL:
            break
        feux.append(loin)
    for (x, z) in feux:
        blocs[(x, Y0, z)] = S("sea_lantern")
        sources.append((x, Y0, z))
    lustre = []
    cx_ch, cz_ch = vers_case(*COFFRE)
    for ddx, ddz in ((0, 0), (2, 0), (-2, 0), (0, 2), (0, -2)):
        x, z = cx_ch + ddx, cz_ch + ddz
        ly = Y0 + 9 + (1 if (ddx, ddz) != (0, 0) else 0)
        for y in range(ly + 1, Y0 + HAUT_SALLE + 1):
            blocs[(x, y, z)] = S("chain", axis="y", waterlogged=False)
        blocs[(x, ly, z)] = S("lantern", hanging=True, waterlogged=False)
        sources.append((x, ly, z))
        lustre.append((x, ly, z))
    # les couloirs : des lanternes suspendues a une chaine, glouton : une lanterne des qu'aucune autre n'est a moins de PAS_LANTERNE blocs (en distance de Manhattan)
    posees = []
    couvrir = sorted(c for c in air if c not in salle and c not in toutes_portes and c not in cases_marches)

    def eclaire(c):
        return any(abs(c[0] - px) + abs(c[1] - pz) <= PAS_LANTERNE for px, pz in posees) or any(abs(c[0] - f[0]) + abs(c[1] - f[1]) <= PAS_SOL for f in feux)

    while True:                                                         # on ajoute une lanterne au point le moins eclaire, tant qu'une case n'est pas couverte (un feu de la salle compte pour les cases proches)
        sombres = [c for c in couvrir if not eclaire(c)]
        if not sombres:
            break
        x, z = max(sombres, key=lambda c: (min([abs(c[0] - px) + abs(c[1] - pz) for px, pz in posees] + [99]), c))
        h = air[(x, z)]
        posees.append((x, z))
        ly = Y0 + min(5, h - 2)
        for y in range(ly + 1, Y0 + h + 1):
            blocs[(x, y, z)] = S("chain", axis="y", waterlogged=False)
        blocs[(x, ly, z)] = S("lantern", hanging=True, waterlogged=False)
        sources.append((x, ly, z))
    for (nom, i), cellules in sorted(lignes.items()):               # une lanterne dans une niche de chaque paroi, tous les 4 rangs, et toujours au dernier rang de chaque escalier (le bas du tunnel de la porte 2 ne doit pas etre sombre)
        if i % RANG_BANDE != 2 and i != NB_MARCHES[nom] - 1:
            continue
        for cote in (min(c[0] for c in cellules) - 1, max(c[0] for c in cellules) + 1):
            p = (cote, cellules[0][1] + 3, cellules[0][2])
            blocs[p] = S("lantern", hanging=False, waterlogged=False)
            sources.append(p)
    infos = {"dalles": dalles, "feux": feux, "lustre": lustre, "pilastres": sorted(pilastres.values()), "salle": salle, "couloirs": couloirs, "cases_portes": cases_portes, "reperes": reperes, "marches": marches, "sources": sources, "air": air,
             "coffre": (cx_ch, Y0 + 1, cz_ch), "zombies": [(vers_case(*z)[0], Y0 + 1, vers_case(*z)[1]) for z in ZOMBIES],
             "entree": (vers_case(*ENTREE_PLAN)[0], Y0 + 1, vers_case(*ENTREE_PLAN)[1]), "centre": (vers_case(*CENTRE)[0], Y0 + 1, vers_case(*CENTRE)[1])}
    return blocs, infos
