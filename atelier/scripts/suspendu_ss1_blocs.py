# [claude-build]
"""suspendu_ss1_blocs.py - Donjon Suspendu, sous-sol 1 : la construction (les blocs) d'apres le plan de suspendu_ss1.py.
Le sous-sol est taille dans la roche du monde (un superflat profond) : la structure ecrit l'air des salles (elle les creuse), habille leurs sols, murs et plafonds, et pose les ancrages, les coffres,
la porte de pierre bleue de la petite salle et la lumiere. Pur calcul : renvoie les blocs et les reperes, sans ecrire de fichier.
Hauteurs (Y du gabarit) : YF = le sol du sous-sol, 28 sous celui du rez-de-chaussee ; grande salle : sol en bandes de YF - 34 a YF - 25 (30 plus bas que le sol des autres espaces, demande de Luca : ses piliers sont 30 plus hauts),
air jusqu'a YF + 22, avec un balcon d'arrivee a YF au bout du passage en arc et une echelle jusqu'au sol ; la ZONE HAUTE (entouree en rouge sur le
dessin : le bulbe, le hall du haut avec ses fosses, le couloir est et la salle de l'enigme) est a YF + 11, bien au dessus de la grande salle, et ne se rejoint qu'au grappin : on y monte de pilier en pilier
(5 piliers de 7 blocs de haut, chacun avec son ancrage 4 au dessus de sa plateforme, plus un ancrage au dessus de la corniche du bulbe) ; hall : air jusqu'a YF + 22, couloir est et salle de l'enigme : YF + 20
(et le plafond de la salle de l'enigme suit le dessous de l'escalier de la porte 1, qui passe au dessus de son angle sud-est) ; petite salle, couloir et passage : sol YF, air sur 8 ; fosses : 12 sous le hall."""
import math
from functools import lru_cache

import suspendu_salle1 as rdc
from suspendu_outils import AIR, S, h01
from suspendu_ss1 import (ANCRAGES, ANCRAGES_PLUS, BLOC_MUSICAL, BULBE, CAVERNE, COFFRE_CLE_2, COFFRE_GRAPPIN, COFFRES, COULOIR_EST, COULOIR_MONSTRES, DEC, ECHELLE, ESTRADE, FOSSES, HALL_HAUT,
                          LARGEUR_COULOIR_MONSTRES, MINI_BOSS, MONSTRES_PETITE_SALLE, MONSTRES_SALLE_MUSICALE, PASSAGE_ARC, PETITE_SALLE, SALLE_MUSICALE, vers_plan)

YF = rdc.Y0 - 28                         # le sol du sous-sol 1 (bloc) : 28 sous le sol du rez-de-chaussee
HAUT_CAVERNE = 22                        # grande salle : air jusqu'a YF + 22 (le toit est a YF + 23 : il reste 4 blocs de roche sous le rez-de-chaussee)
HAUT_PETIT = 8                           # petite salle, couloir, passage
SOL_HALL = YF + 11                       # la zone haute : le bulbe, le hall du haut et le couloir est
SOL_MUSICALE = SOL_HALL                  # et la salle de l'enigme, au meme niveau
HAUT_HALL = 11                           # air jusqu'a YF + 22 (le hall et le bulbe)
HAUT_MUSICALE = 9                        # air jusqu'a YF + 20 (le couloir est, la salle de l'enigme)
PROFONDEUR_FOSSE = 12
PROFONDEUR_SALLE = 30                    # la grande salle est creusee 30 plus bas que son ancien sol (demande de Luca) : ses piliers sont 30 plus hauts
PILIERS_PLUS_SOMMET = (0, 4)             # les deux piliers ajoutes (ANCRAGES_PLUS) : deux marches entre l'estrade (a -7) et les piliers P3 a P5
PILIERS_SOMMET = (12, 9, 9, 7, 6)        # le sommet de la plateforme de chaque pilier (P1 a P5), au dessus de YF : ils gardent leur niveau d'avant, on y arrive en grappin depuis le balcon d'arrivee
BALCON_X, BALCON_Z = (63, 72), (66, 79)  # le balcon d'arrivee (cases du gabarit) : le bout du passage en arc donne dessus, a YF ; de la, une echelle descend jusqu'au sol de la salle
# l'enigme du bloc musical (salle de l'enigme, sur l'estrade de dalles) : 5 blocs musicaux, chacun sur un bloc d'instrument different, avec une lampe de couleur suspendue 2 au dessus ;
# le bouton de depart joue une melodie (les lampes s'allument dans l'ordre ENIGME_SEQUENCE, avec la note de chaque bloc), puis on la rejoue en frappant (clic droit) les blocs dans le meme ordre
ENIGME_SEQUENCE = (2, 4, 1, 5, 3)                                  # les blocs a frapper, dans l'ordre (de 1 a 5, du nord au sud)
ENIGME_NOTES = (6, 9, 11, 13, 16)                                  # la note qu'on entend en frappant chaque bloc (0 a 24, 12 = le milieu) : un clic droit ajoute 1 a la note posee, qui est donc celle-ci moins 1
ENIGME_INSTRUMENTS = (("gold_block", "bell"), ("packed_ice", "chime"), ("emerald_block", "bit"), ("iron_block", "iron_xylophone"), ("bone_block", "xylophone"))
ENIGME_COULEURS = ("red", "blue", "lime", "yellow", "purple")
HAUT_ESTRADE = 26                        # l'estrade du coffre du grappin (en demi-cercle, au sud) : son sol est 26 au dessus du sol de la grande salle (6, puis 20 de plus a la demande de Luca, pour que de l'estrade on atteigne les piliers) ; on y monte par une echelle
HAUT_ANCRAGE = 4                         # l'ancrage d'un pilier : 4 au dessus de sa plateforme (on s'y balance, puis on se lache sur la plateforme)
HAUT_ANCRAGE_CORNICHE = 3                # l'ancrage de la corniche (a l'entree du hall) : 3 au dessus du sol ; le rebord du bulbe le cache a qui est au sol de la grande salle
HAUT_ANCRAGE_FOSSE = 3                   # un ancrage de fosse : au dessus de la rive est, 3 au dessus du sol du hall : du bord ouest on tire dessus, on est tire de l'autre cote de la fosse et on arrive debout sous lui (verifie en jeu)
MARGE_ESCALIER = 3                       # sous un escalier du rez-de-chaussee, l'air du sous-sol s'arrete 3 sous la marche (la roche reste entre les deux)
MARGE_X_ESCALIER = 2                     # et jusqu'a 2 blocs de chaque cote de l'escalier
LARGEUR_BANDE = 100                      # une bande du sol de la grande salle = 100 px (10 blocs) ; une marche de plus a chaque ligne
LUM_NIVEAU, LUM_PAS = 13, 5              # blocs de lumiere invisibles : un a moins de 5 blocs de toute case (les parois et les marches allongent le trajet de la lumiere ; au sol, de la lumiere 5 au moins)
TUNNEL_PROTEGE = (100, 108, 63, 77)      # x min, x max, z min, z max (gabarit) : le tunnel de l escalier de la porte 1 (hors batiment), que le sous-sol ne doit pas toucher
NATUREL = {"caverne", "estrade", "hall", "fosse", "est", "bulbe"}


def vers_sp(cx, cz):
    """Centre d'une cellule du gabarit -> pixel du dessin."""
    xp, zp = cx + 0.5 - rdc.DX, cz + 0.5 - rdc.DZ
    return ((xp - DEC[0]) / ECHELLE, (zp - DEC[1]) / ECHELLE)


def cellule(px, py):
    xp, zp = vers_plan(px, py)
    return (math.floor(xp + rdc.DX), math.floor(zp + rdc.DZ))


def dans_ellipse(px, py, e):
    (c0, c1), rx, ry = e
    return ((px - c0) / rx) ** 2 + ((py - c1) / ry) ** 2 <= 1


def dans_rect(px, py, a, b):
    return a[0] <= px <= b[0] and a[1] <= py <= b[1]


def bande(px, py):
    """La bande du sol de la grande salle : 0 au passage en arc, plus haut vers le nord-ouest, plus bas vers l'estrade du bas."""
    return math.floor((-0.3 * px - py + 661) / LARGEUR_BANDE)


def protege(x, z):
    return TUNNEL_PROTEGE[0] <= x <= TUNNEL_PROTEGE[1] and TUNNEL_PROTEGE[2] <= z <= TUNNEL_PROTEGE[3]


def classer(px, py):
    """(sol, plafond d'air, genre) du point (px, py) du dessin, ou None. L'ordre fixe qui gagne quand deux espaces se touchent."""
    if dans_rect(px, py, PETITE_SALLE[0], PETITE_SALLE[2]):
        return (YF, YF + HAUT_PETIT, "petite")
    if rdc.dans_polygone(px, py, PASSAGE_ARC):
        return (YF, YF + HAUT_PETIT, "passage")
    if rdc.dist_ligne(px, py, COULOIR_MONSTRES) <= LARGEUR_COULOIR_MONSTRES / 2:
        return (YF, YF + HAUT_PETIT, "couloir")
    if dans_rect(px, py, (825, 300), (845, 350)):                                       # le seuil de la salle de l'enigme, a l'est : 5 de large
        return (SOL_MUSICALE, SOL_MUSICALE + HAUT_MUSICALE, "seuil")
    if dans_rect(px, py, SALLE_MUSICALE[0], SALLE_MUSICALE[2]):
        if dans_rect(px, py, BLOC_MUSICAL[0], BLOC_MUSICAL[2]):
            return (SOL_MUSICALE + 1, SOL_MUSICALE + HAUT_MUSICALE, "bloc")
        return (SOL_MUSICALE, SOL_MUSICALE + HAUT_MUSICALE, "musicale")
    en_hall = rdc.dans_polygone(px, py, HALL_HAUT)
    if en_hall or dans_rect(px, py, COULOIR_EST[0], COULOIR_EST[2]):
        for a, b in FOSSES:
            if en_hall and dans_rect(px, py, a, b):
                return (SOL_HALL - PROFONDEUR_FOSSE, SOL_HALL + HAUT_HALL, "fosse")
        return (SOL_HALL, SOL_HALL + HAUT_HALL, "hall") if en_hall else (SOL_HALL, SOL_HALL + HAUT_MUSICALE, "est")
    if dans_ellipse(px, py, BULBE):                                                    # le bulbe : la corniche en haut de la grande salle, a la hauteur du hall
        return (SOL_HALL, SOL_HALL + HAUT_HALL, "bulbe")
    if rdc.dans_polygone(px, py, CAVERNE):
        b = bande(px, py)
        if dans_ellipse(px, py, ESTRADE) and py <= ESTRADE[0][1]:
            return (YF + b - PROFONDEUR_SALLE + HAUT_ESTRADE, YF + HAUT_CAVERNE, "estrade")
        return (YF + b - PROFONDEUR_SALLE, YF + HAUT_CAVERNE, "caverne")
    return None


def roche(x, y, z):
    r = h01(x, y, z, 5)
    return S("stone") if r < 0.6 else (S("andesite") if r < 0.8 else (S("tuff") if r < 0.95 else S("mossy_cobblestone")))


def sol_genre(genre, x, z):
    if genre in NATUREL:
        return roche(x, 0, z)
    if genre == "bloc":
        return S("deepslate_tiles")
    if genre == "plateforme":
        return S("stone_bricks")
    return rdc.sol(x, z)                                               # salles et couloirs : le dallage du rez-de-chaussee


def mur_genre(genre, x, y, z):
    return roche(x, y, z) if genre in NATUREL else rdc.mur(x, y, z)


@lru_cache(maxsize=None)
def construire():
    """Renvoie (blocs, infos) du sous-sol 1 : blocs {(x, y, z): etat} dans le repere du gabarit (l'air est ecrit explicitement), infos : reperes."""
    cases = {}
    for cx in range(rdc.DX - 40, rdc.DX + 60):
        for cz in range(rdc.DZ - 22, rdc.DZ + 76):
            if protege(cx, cz):
                continue
            r = classer(*vers_sp(cx, cz))
            if r:
                cases[(cx, cz)] = list(r)
    # le balcon d'arrivee : le sol reste a YF (le niveau du passage) au bout du passage, le reste de la grande salle est 30 plus bas
    for c, v in cases.items():
        if v[2] == "caverne" and BALCON_X[0] <= c[0] <= BALCON_X[1] and BALCON_Z[0] <= c[1] <= BALCON_Z[1]:
            cases[c] = [YF, v[1], "balcon"]
    # la porte : les deux rangees (z du gabarit 76 et 77) du mur nord de la petite salle, sur toute la largeur de la salle
    colonnes = sorted({cx for (cx, cz) in cases if cz == 77 and cases[(cx, cz)][2] == "petite"})
    centre_x = (colonnes[0] + colonnes[-1]) / 2 + 0.5
    largeur = len(colonnes)
    cases_porte = {(cx, cz) for cx in colonnes for cz in (76, 77)}
    for c in cases_porte:
        cases[c] = [YF, YF + HAUT_PETIT, "porte"]
    # les escaliers du rez-de-chaussee passent au dessus du sous-sol (celui de la porte 1 au dessus de l'angle sud-est de la salle de l'enigme, celui de la porte 2 au dessus de la grande salle) : le plafond
    # suit leur dessous, en gradins, et une case trop basse reste de la roche. Le tunnel de la porte 1 (z >= 63), lui, est protege plus haut.
    marches_rdc = rdc.construire()[1]["marches"]
    for nom, z_max in (("c1", 62), ("c2", 999)):
        dessous = {}
        for (x, y, z) in marches_rdc[nom]:
            if z <= z_max:
                dessous[z] = min(dessous.get(z, 999), y)
        x0, x1 = min(m[0] for m in marches_rdc[nom]) - MARGE_X_ESCALIER, max(m[0] for m in marches_rdc[nom]) + MARGE_X_ESCALIER
        for c, v in list(cases.items()):
            if x0 <= c[0] <= x1 and c[1] in dessous and v[2] not in ("petite", "passage", "couloir", "porte"):
                plafond = dessous[c[1]] - MARGE_ESCALIER
                if v[1] > plafond:
                    if plafond - v[0] < 4:
                        del cases[c]
                    else:
                        v[1] = plafond
    # les piliers de la grande salle : une plateforme (5 x 5, sans les coins) 7 blocs au dessus du sol, avec l'ancrage 4 au dessus ; on n'y monte qu'avec le grappin
    def pilier(sp, sommet):
        cx, cz = cellule(*sp)
        if cases.get((cx, cz), [0, 0, ""])[2] not in ("caverne", "estrade"):
            return None
        haut_p = YF + sommet
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                c = (cx + dx, cz + dz)
                if abs(dx) == 2 and abs(dz) == 2:
                    continue
                if c in cases and cases[c][2] in ("caverne", "estrade"):
                    cases[c] = [haut_p, cases[c][1], "plateforme"]
        return (cx, cz, haut_p)

    ancrages_caverne = [r for r in (pilier(sp, PILIERS_SOMMET[k]) for k, sp in enumerate(ANCRAGES[:5])) if r]
    ancrages_plus = [r for r in (pilier(sp, PILIERS_PLUS_SOMMET[k]) for k, sp in enumerate(ANCRAGES_PLUS)) if r]
    air = set()
    for (cx, cz), (sol, haut, genre) in cases.items():
        for y in range(sol + 1, haut + 1):
            air.add((cx, y, cz))
    blocs = {v: AIR for v in air}
    for (cx, cz), (sol, haut, genre) in cases.items():
        blocs[(cx, sol, cz)] = sol_genre(genre, cx, cz)
        blocs[(cx, haut + 1, cz)] = mur_genre(genre, cx, haut + 1, cz)
    # les murs : tout voisin d'un voxel d'air qui n'en est pas un (la roche du monde est la : on l'habille)
    for (x, y, z) in air:
        genre = cases[(x, z)][2]
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + dx, y + dy, z + dz)
            if n in air or n in blocs or protege(n[0], n[2]):
                continue
            colonne = cases.get((n[0], n[2]))
            if colonne and colonne[2] == "plateforme" and n[1] < colonne[0]:               # le flanc d'un pilier : des briques de pierre, une bande sculptee tous les 4 blocs
                blocs[n] = S("chiseled_stone_bricks") if (colonne[0] - n[1]) % 4 == 0 else (S("cracked_stone_bricks") if h01(n[0], n[1], n[2], 7) < 0.12 else S("stone_bricks"))
                continue
            blocs[n] = mur_genre(genre, n[0], n[1], n[2])
    # la porte (bleue) : dalle en arc, spirale, cadre, mur au dessus
    dalles = []
    dalle_bloc, spirale_bloc = rdc.MATERIAUX_PORTES["1"]
    for (cx, cz) in sorted(cases_porte):
        u = cx + 0.5 - centre_x
        haut_p = rdc.profil(largeur, u)
        for yr in range(1, rdc.HAUT_PORTE + 1):
            y = YF + yr
            if yr <= haut_p:
                blocs[(cx, y, cz)] = spirale_bloc if (math.floor(u + 0.5), yr - 4) in rdc.SPIRALE else dalle_bloc
                dalles.append((cx, y, cz, blocs[(cx, y, cz)][0]))
            else:
                blocs[(cx, y, cz)] = rdc.CADRE if yr == haut_p + 1 else rdc.mur(cx, y, cz)
    # les ancrages : l'anneau de fer, accroche au plafond par une chaine
    ancrages = [(cx, haut_p + HAUT_ANCRAGE, cz) for (cx, cz, haut_p) in ancrages_caverne]
    cx, cz = cellule(*ANCRAGES[5])                                              # au dessus de la corniche, a l'entree du hall
    ancrages.append((cx, SOL_HALL + HAUT_ANCRAGE_CORNICHE, cz))
    for sp in ANCRAGES[6:]:                                                     # les deux ancrages au dessus des fosses
        cx, cz = cellule(*sp)
        ancrages.append((cx, SOL_HALL + HAUT_ANCRAGE_FOSSE, cz))
    ancrages += [(cx, haut_p + HAUT_ANCRAGE, cz) for (cx, cz, haut_p) in ancrages_plus]                  # les deux piliers ajoutes, apres les autres : les indices des ancrages existants ne bougent pas
    for (x, y, z) in ancrages:
        for yy in range(y + 1, cases[(x, z)][1] + 1):
            blocs[(x, yy, z)] = S("chain", axis="y", waterlogged=False)
        blocs[(x, y, z)] = S("grappin:ancrage")
    # l'enigme du bloc musical : 5 blocs musicaux alignes au milieu de l'estrade de dalles, du nord au sud, avec le bouton de depart au bout nord. Il faut de l'air juste au dessus d'un bloc musical (sinon il se tait) :
    # la lampe est 2 au dessus, pendue a une chaine
    plate = sorted(c for c, v in cases.items() if v[2] == "bloc")
    bx0, bx1 = min(c[0] for c in plate), max(c[0] for c in plate)
    bz0 = min(c[1] for c in plate)
    px_e = (bx0 + bx1) // 2
    y_dalle = cases[(px_e, bz0)][0]
    pads, lampes = [], []
    for i in range(len(ENIGME_SEQUENCE)):
        pz_e = bz0 + 4 + 2 * i
        bloc_instrument, nom_instrument = ENIGME_INSTRUMENTS[i]
        blocs[(px_e, y_dalle, pz_e)] = S(bloc_instrument)
        blocs[(px_e, y_dalle + 1, pz_e)] = S("note_block", instrument=nom_instrument, note=ENIGME_NOTES[i] - 1, powered=False)
        blocs[(px_e, y_dalle + 3, pz_e)] = S(ENIGME_COULEURS[i] + "_concrete")
        for y in range(y_dalle + 4, cases[(px_e, pz_e)][1] + 1):
            blocs[(px_e, y, pz_e)] = S("chain", axis="y", waterlogged=False)
        pads.append((px_e, y_dalle + 1, pz_e))
        lampes.append((px_e, y_dalle + 3, pz_e))
    depart_enigme = (px_e, y_dalle + 1, bz0 + 1)
    blocs[depart_enigme] = S("stone_button", face="floor", facing="north", powered=False)
    # l'echelle du balcon : du sol de la salle jusqu'au balcon, contre la paroi ouest du balcon, au milieu ; une lanterne dans une niche de la paroi, de chaque cote, tous les 4 blocs
    cellules_balcon = sorted(c for c, v in cases.items() if v[2] == "balcon")
    rangs_z = sorted({c[1] for c in cellules_balcon})
    z_echelle = rangs_z[len(rangs_z) // 2]
    x_paroi = min(c[0] for c in cellules_balcon if c[1] == z_echelle)
    x_echelle = x_paroi - 1
    bas_b = cases[(x_echelle, z_echelle)][0] + 1
    for y in range(bas_b, YF + 1):
        blocs[(x_echelle, y, z_echelle)] = S("ladder", facing="west", waterlogged=False)
    echelle_balcon = (x_echelle, bas_b, YF, z_echelle)
    niches_balcon = [(x_paroi, y, z_echelle + dz) for y in range(bas_b + 2, YF - 1, 4) for dz in (-1, 1)]
    for p in niches_balcon:
        blocs[p] = S("lantern", hanging=False, waterlogged=False)
    # le garde-corps du balcon : un muret de briques de pierre (1,5 bloc de haut : on ne l'enjambe pas, on ne saute pas par dessus) sur chaque case du bord qui donne sur le vide, sauf la case devant l'echelle ;
    # un tir de grappin vers les piliers passe au dessus
    garde_corps = {c for c in cellules_balcon if c != (x_paroi, z_echelle) and any(cases.get((c[0] + dx, c[1] + dz), (0, 0, ""))[2] == "caverne" for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    for (x, z) in garde_corps:
        cotes = {nom: ("low" if (x + dx, z + dz) in garde_corps else "none") for nom, (dx, dz) in (("east", (1, 0)), ("west", (-1, 0)), ("south", (0, 1)), ("north", (0, -1)))}
        blocs[(x, YF + 1, z)] = S("stone_brick_wall", up=True, waterlogged=False, **cotes)
    # l'echelle de l'estrade : au milieu de sa face nord (contre la paroi de l'estrade, du sol de la grande salle jusqu'au dessus de l'estrade)
    colonne_estrade = cellule(ESTRADE[0][0], ESTRADE[0][1])[0]
    z_haut_estrade = min(z for (x, z), v in cases.items() if x == colonne_estrade and v[2] == "estrade")
    ex_e, ez_e = colonne_estrade, z_haut_estrade - 1
    bas_e = cases[(ex_e, ez_e)][0] + 1
    for y in range(bas_e, cases[(ex_e, z_haut_estrade)][0] + 1):
        blocs[(ex_e, y, ez_e)] = S("ladder", facing="north", waterlogged=False)
    echelle_estrade = (ex_e, bas_e, cases[(ex_e, z_haut_estrade)][0], ez_e)
    niches_estrade = [(ex_e + dx, y, z_haut_estrade) for y in range(bas_e + 2, echelle_estrade[2] - 1, 4) for dx in (-1, 1)]       # l'echelle est longue : une lanterne en niche dans la paroi de l'estrade, de chaque cote, tous les 4 blocs
    for p in niches_estrade:
        blocs[p] = S("lantern", hanging=False, waterlogged=False)
    # l'echelle de secours de chaque fosse : sur sa paroi ouest, a l'angle nord (hors de la ligne de tir), du fond jusqu'a la rive ; on remonte a pied, mais on ne passe pas de l'autre cote
    echelles = []
    for (x0, z0), (x1, z1) in [(cellule(*a), cellule(*b)) for a, b in FOSSES]:
        ex, ez = x0, min(z0, z1) + 2
        bas = cases[(ex, ez)][0] + 1
        for y in range(bas, SOL_HALL + 1):
            blocs[(ex, y, ez)] = S("ladder", facing="east", waterlogged=False)
        echelles.append((ex, bas, SOL_HALL, ez))
    # les coffres (vides ; les deux coffres verts sont remplis par une fonction de datapack)
    coffres = []
    for sp in COFFRES:
        cx, cz = cellule(*sp)
        blocs[(cx, cases[(cx, cz)][0] + 1, cz)] = S("chest", facing="south", type="single", waterlogged=False)
        coffres.append((cx, cases[(cx, cz)][0] + 1, cz))

    def pos(sp):
        cx, cz = cellule(*sp)
        return (cx, cases[(cx, cz)][0] + 1, cz)

    # la lumiere : des blocs de lumiere invisibles, le plus eloigne d'abord, par groupe (les fosses a part)
    lumieres, sources = [], []
    for groupe in ((True, False, False), (False, True, False), (False, False, True), (False, False, False)):                  # fosses, piliers et estrade, zone haute, le reste
        libres = sorted(c for c, (s, h, g) in cases.items() if g != "porte" and (c[0], c[1]) not in {(p[0], p[2]) for p in pads} and (g == "fosse", g in ("plateforme", "estrade", "balcon"), s >= SOL_HALL and g not in ("fosse", "plateforme", "estrade", "balcon")) == groupe)
        posees = []
        while True:
            sombres = [c for c in libres if not any(abs(c[0] - p[0]) + abs(c[1] - p[1]) <= LUM_PAS for p in posees)]
            if not sombres:
                break
            posees.append(max(sombres, key=lambda q: (min([abs(q[0] - p[0]) + abs(q[1] - p[1]) for p in posees] + [99]), q)))
        for (x, z) in posees:
            y = cases[(x, z)][0] + 2
            if blocs.get((x, y, z)) == AIR:
                blocs[(x, y, z)] = S("light", level=LUM_NIVEAU, waterlogged=False)
                lumieres.append((x, y, z))
                sources.append((x, y, z, LUM_NIVEAU))
    for p in niches_balcon + niches_estrade:                                      # les lanternes des echelles du balcon et de l'estrade eclairent le trajet
        lumieres.append(p)
        sources.append(p)
    for (ex, bas, haut_e, ez) in echelles:                                       # un bloc de lumiere au pied de chaque echelle de secours (un coin de fosse, sinon sombre)
        if blocs.get((ex + 1, bas + 1, ez)) == AIR:
            blocs[(ex + 1, bas + 1, ez)] = S("light", level=LUM_NIVEAU, waterlogged=False)
            lumieres.append((ex + 1, bas + 1, ez))
            sources.append((ex + 1, bas + 1, ez, LUM_NIVEAU))
    infos = {"cases": {c: tuple(v) for c, v in cases.items()}, "cases_porte": cases_porte, "dalles": dalles, "colonnes_porte": colonnes, "largeur_porte": largeur, "centre_porte": (centre_x, 77.0),
             "ancrages": ancrages, "coffres": coffres, "coffre_grappin": pos(COFFRE_GRAPPIN), "coffre_cle": pos(COFFRE_CLE_2), "lumieres": lumieres, "sources": sources,
             "monstres_petite": [pos(m) for m in MONSTRES_PETITE_SALLE], "monstres_musicale": [pos(m) for m in MONSTRES_SALLE_MUSICALE], "mini_boss": pos(MINI_BOSS),
             "arrivee": (rdc.DX + 44, YF + 1, 78), "centre_petite": pos(((535 + 640) / 2, (565 + 685) / 2)), "centre_musicale": pos(((490 + 835) / 2, (210 + 418) / 2)),
             "fosses": [(cellule(*a), cellule(*b)) for a, b in FOSSES], "hall_ouest": pos((450, 100)), "hall_milieu": pos((680, 100)), "hall_est": pos((860, 100)),
             "alcove": pos(COFFRE_GRAPPIN), "corniche": pos(ANCRAGES[5]), "plateformes": [(cx, haut_p + 1, cz) for (cx, cz, haut_p) in ancrages_caverne + ancrages_plus], "piliers_plus": len(ancrages_plus),
             "sol_haut": SOL_HALL, "echelles": echelles, "echelle_estrade": echelle_estrade, "echelle_balcon": echelle_balcon, "garde_corps": sorted(garde_corps),
             "enigme": {"pads": pads, "lampes": lampes, "depart": depart_enigme, "sequence": ENIGME_SEQUENCE, "notes": ENIGME_NOTES,
                        "instruments": [n for _, n in ENIGME_INSTRUMENTS], "couleurs": ENIGME_COULEURS}}
    return blocs, infos
