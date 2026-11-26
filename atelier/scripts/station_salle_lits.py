# [claude-build]
"""station_salle_lits.py - la salle des lits du secteur A (Filtration) refaite (demande de Luca : « deux gros blocs de sable, c'est moche »).

Les trois lits gardent leur emprise (4 x 28, de y 61 a 68) : ce sont les murs du slalom de la ligne verte. Mais ce ne sont plus des blocs de sable vitres :
  - de vraies cuves de filtration : socle de cuivre, cotes de cuivre tous les 4 blocs, entre elles des vitres qui montrent les couches
    (gravier, sable, anthracite, puis l'eau a filtrer), un rebord de cuivre et un dessus en grille de cuivre (on voit l'eau dedans) ;
  - une conduite Create sur le dessus de chaque lit, avec une descente dans la cuve tous les 4 blocs ;
  - au bout libre de chaque lit, une pompe de la station (donjon:pompe_station) qui le nourrit, sortie tournee vers la cuve ;
  - des manometres (donjon:cadran) sur les cotes des lits et sur les longs murs de la salle, sous les tuyaux.
Pur calcul, comme station_create : a appeler a la fin de station_create.salles_des_pompes(parts, infos) :
    station_salle_lits.refaire(parts, infos)
Renvoie {"pompes": [(p, facing)], "cadrans": [p]} (pour le datapack, si on veut mettre les pompes en marche au retour de la pompe A).
"""
import station_vannes
from suspendu_outils import S

LITS = ((30, 33, 3, 30), (44, 47, 15, 42), (58, 61, 3, 30))     # x0, x1, z0, z1 (station_secteurs.LITS)
Y_SOL, Y_HAUT = 60, 74                                           # sol de la salle ; le dessus des lits : ils montent jusqu'au plafond (Luca, 25/11), le slalom est obligatoire
LIBRE = ("minecraft:water", "minecraft:air")
EAU = S("minecraft:water", level=0)
CUIVRE = S("create:copper_casing")
VITRE = S("create:framed_glass")
GRILLE = S("minecraft:waxed_copper_grate", waterlogged=True)
REBORD = S("minecraft:waxed_cut_copper")
COUCHES = {61: S("minecraft:gravel"), 62: S("minecraft:gravel"), 63: S("minecraft:sand"), 64: S("minecraft:sand"),
           65: S("minecraft:coal_block")}                                         # de bas en haut : gravier, sable, anthracite, puis l'eau a filtrer jusqu'au plafond


def _tuyau(axe, eau=True):
    ouvert = {"x": dict(east=True, west=True, north=False, south=False, up=False, down=False),
              "y": dict(up=True, down=True, north=False, south=False, east=False, west=False),
              "z": dict(north=True, south=True, east=False, west=False, up=False, down=False)}[axe]
    return S("create:fluid_pipe", waterlogged=eau, **ouvert)


def _libre(blocs, p):
    b = blocs.get(p)
    return b is not None and b[0] in LIBRE


def _place_pompe(blocs, p):
    """Le modele de la pompe deborde sur les 3 x 2 x 3 cases autour de son bloc : libres, et le sol dessous plein."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            sol = blocs.get((p[0] + dx, p[1] - 1, p[2] + dz))
            if sol is None or sol[0] in LIBRE:
                return False
            if any(not _libre(blocs, (p[0] + dx, p[1] + dy, p[2] + dz)) for dy in (0, 1)):
                return False
    return True


def _cadran(blocs, p, face, poses):
    """Un manometre en p, dos au mur (la case derriere doit etre pleine)."""
    dx, dz = {"east": (-1, 0), "west": (1, 0), "south": (0, -1), "north": (0, 1)}[face]
    dos = blocs.get((p[0] + dx, p[1], p[2] + dz))
    if _libre(blocs, p) and dos is not None and dos[0] not in LIBRE:
        blocs[p] = S("donjon:cadran", facing=face, waterlogged=True)
        poses.append(p)


def cuve(blocs, x0, x1, z0, z1):
    """Un lit : une cuve de filtration de 4 x 28, socle et rebord de cuivre, cotes tous les 4, vitres entre elles, couches dedans, grille dessus."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            bord_x, bord_z = x in (x0, x1), z in (z0, z1)
            cote = (z - z0) % 4 == 0 or bord_z
            for y in range(Y_SOL + 1, Y_HAUT + 1):
                if y == Y_SOL + 1 and (bord_x or bord_z):
                    b = CUIVRE                                                           # le socle
                elif y == Y_HAUT:
                    b = REBORD if (bord_x or bord_z) else GRILLE                         # le rebord, la grille par-dessus l'eau
                elif bord_x or bord_z:
                    b = CUIVRE if cote else VITRE                                        # les cotes, et les vitres entre elles
                elif y in (66, 71) and (z - z0) % 4 == 2:
                    b = S("minecraft:light", level=15, waterlogged=True)                 # l'eau enfermee de la cuve n'est pas dans le noir (verif_station_suite)
                else:
                    b = COUCHES.get(y, EAU)
                blocs[(x, y, z)] = b


BARRAGES = ((32, 31, 42), (45, 3, 14), (59, 31, 42))          # x, z0, z1 : un barrage de planches dans chaque passage entre deux lits, en travers du slalom


def barrages(blocs):
    """Les barrages de planches (donjon:planche_station) : de plancher a plafond dans chaque passage du slalom, que la Cape lestee brise d'un tourbillon. Renvoie [(case, axe)]."""
    planches = []
    for (x, z0, z1) in BARRAGES:
        for z in range(z0, z1 + 1):
            for y in range(Y_SOL + 1, Y_HAUT + 1):
                if _libre(blocs, (x, y, z)):
                    blocs[(x, y, z)] = station_vannes.planche("z", True)
                    planches.append(((x, y, z), "z"))
    return planches


def refaire(parts, infos):
    blocs = parts["station_a"]
    pompes, cadrans = [], []
    for (x0, x1, z0, z1) in LITS:
        cuve(blocs, x0, x1, z0, z1)
        xc = x0 + 1                                                                      # la conduite, au-dessus d'une des deux colonnes du milieu
        for z in range(z0, z1 + 1):
            if _libre(blocs, (xc, Y_HAUT + 1, z)):
                blocs[(xc, Y_HAUT + 1, z)] = _tuyau("z")
        for z in range(z0 + 2, z1, 4):                                                   # les descentes dans la cuve, a travers la grille
            blocs[(xc, Y_HAUT, z)] = _tuyau("y")
        for z in range(z0 + 4, z1, 8):                                                   # des manometres sur les cotes, des deux cotes du lit
            _cadran(blocs, (x0 - 1, 64, z), "west", cadrans)
            _cadran(blocs, (x1 + 1, 64, z), "east", cadrans)
        # la pompe, au bout libre du lit (l'autre bout touche le mur), sa bride de sortie contre la cuve
        for (zp, face) in ((z1 + 2, "north"), (z0 - 2, "south")):
            p = (xc, Y_SOL + 1, zp)
            if _place_pompe(blocs, p):
                blocs[p] = S("donjon:pompe_station", facing=face, en_marche=False, waterlogged=True)
                pompes.append((p, face))
                break
    planches = barrages(blocs)
    ax0, ax1, az0, az1, _, ay1 = infos["a"]["a3"]                                     # station_secteurs.A3 : x0, x1, z0, z1, y0, y1
    for x in range(ax0 + 4, ax1, 8):                                                     # des manometres sur les longs murs, a mi-hauteur
        _cadran(blocs, (x, ay1 - 6, az0), "south", cadrans)
        _cadran(blocs, (x, ay1 - 6, az1), "north", cadrans)
    return {"pompes": pompes, "cadrans": cadrans, "planches": planches}
