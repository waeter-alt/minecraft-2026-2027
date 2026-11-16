# [claude-build]
"""suspendu_ss2_deco.py - Donjon Suspendu, sous-sol 2 : la roche, les grottes organiques, les rivieres de lave, les stalactites, stalagmites, lianes et cristaux.
Pur calcul (les cellules sont des (x, z) du gabarit, les blocs des (nom, proprietes) de suspendu_outils) : `suspendu_ss2_blocs.construire` s'en sert pour poser tout cela."""
import math
from collections import deque

from suspendu_outils import S, h01

VOISINS = ((1, 0), (-1, 0), (0, 1), (0, -1))


def bruit(x, z, e, g=0):
    """Bruit de valeur lisse entre 0 et 1, de longueur d'onde `e` blocs (deux cases voisines different de moins de 1,5 / e)."""
    fx, fz = x / e, z / e
    x0, z0 = math.floor(fx), math.floor(fz)
    tx, tz = fx - x0, fz - z0
    tx, tz = tx * tx * (3 - 2 * tx), tz * tz * (3 - 2 * tz)
    a, b = h01(x0, 0, z0, g), h01(x0 + 1, 0, z0, g)
    c, d = h01(x0, 0, z0 + 1, g), h01(x0 + 1, 0, z0 + 1, g)
    return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz


def dist_seg(px, pz, a, b):
    ax, az = a
    bx, bz = b
    dx, dz = bx - ax, bz - az
    n = dx * dx + dz * dz
    t = 0 if n == 0 else max(0.0, min(1.0, ((px - ax) * dx + (pz - az) * dz) / n))
    return math.hypot(px - (ax + t * dx), pz - (az + t * dz))


# ---------------------------------------------------------------- la roche
NOMS_FOND = {"minecraft:deepslate", "minecraft:blackstone", "minecraft:basalt", "minecraft:cobbled_deepslate", "minecraft:magma_block", "minecraft:lava"}


def roche_mur(x, y, z, yf2):
    """La roche des parois et de la voute selon la hauteur : de la roche noire au fond de la fosse, de la pierre ordinaire au milieu, de la voute claire (dripstone, calcite) en haut."""
    r = h01(x, y, z, 5)
    if y <= yf2 - 24:
        return S("deepslate") if r < 0.45 else S("cobbled_deepslate") if r < 0.7 else S("blackstone") if r < 0.85 else S("tuff") if r < 0.93 else S("smooth_basalt")
    if y >= yf2 + 10:
        return S("stone") if r < 0.4 else S("andesite") if r < 0.55 else S("tuff") if r < 0.75 else S("dripstone_block") if r < 0.9 else S("calcite")
    return S("stone") if r < 0.58 else S("andesite") if r < 0.76 else S("tuff") if r < 0.9 else S("mossy_cobblestone") if r < 0.96 else S("dripstone_block")


def sol_ile(x, z):
    """La surface des iles, des grottes et du pont (dessous) : de la roche, avec des plaques de mousse."""
    if bruit(x, z, 7, 31) > 0.64 and h01(x, 1, z, 32) < 0.75:
        return S("moss_block")
    r = h01(x, 0, z, 5)
    return S("stone") if r < 0.6 else S("andesite") if r < 0.8 else S("tuff") if r < 0.95 else S("mossy_cobblestone")


def fond_roche(x, z):
    """Le fond de la fosse : roche noire."""
    r = h01(x, 0, z, 6)
    return S("deepslate") if r < 0.4 else S("blackstone") if r < 0.65 else S("basalt", axis="y") if r < 0.85 else S("cobbled_deepslate")


# ---------------------------------------------------------------- les rivieres de lave
def trace_riviere(points, largeur, graine, pas=0.7):
    """Echantillons (x, z, rayon) le long d'une courbe sinueuse qui suit la ligne brisee `points` (en blocs) : la riviere serpente et sa largeur varie."""
    out, total = [], 0.0
    for a, b in zip(points, points[1:]):
        longueur = math.dist(a, b)
        n = max(1, int(longueur / pas))
        ux, uz = (b[0] - a[0]) / longueur, (b[1] - a[1]) / longueur
        for i in range(n):
            s = total + i * pas
            decal = 2.4 * math.sin(s / 6.5 + graine) + 1.2 * math.sin(s / 2.9 + 2 * graine)
            r = largeur / 2 * (1 + 0.3 * math.sin(s / 4.3 + 3 * graine))
            out.append((a[0] + ux * i * pas - uz * decal, a[1] + uz * i * pas + ux * decal, r))
        total += longueur
    return out


def cases_riviere(echantillons, permises):
    lave = set()
    for (x, z, r) in echantillons:
        reach = math.ceil(r) + 1
        for cx in range(int(x) - reach, int(x) + reach + 1):
            for cz in range(int(z) - reach, int(z) + reach + 1):
                if (cx, cz) in permises and (cx + 0.5 - x) ** 2 + (cz + 0.5 - z) ** 2 <= r * r:
                    lave.add((cx, cz))
    return lave


# ---------------------------------------------------------------- les grottes
def accessible(libres, depart, cibles):
    """Les `cibles` (cellules) sont-elles toutes atteignables depuis `depart` en marchant sur les cellules `libres` (voisinage a 4) ?"""
    vu, file_ = {depart}, deque([depart])
    while file_:
        c = file_.popleft()
        for dx, dz in VOISINS:
            n = (c[0] + dx, c[1] + dz)
            if n in libres and n not in vu:
                vu.add(n)
                file_.append(n)
    return all(c in vu for c in cibles)


def creuser_grotte(nom, m, noyau, dist, vers_local, cibles):
    """La grotte d'une montagne, de forme organique : une chambre au contour ondule, des lobes qui s'y ajoutent, un tunnel qui serpente jusqu'a la bordure (et s'evase a l'entree), un plafond irregulier,
    un sol qui ondule de 0 a 2 blocs (jamais plus d'une marche entre deux cases) et un ruisseau de lave au fond. `cibles` : cellules ou l'on doit pouvoir aller (levier, coffre).
    Renvoie {'cases': {(x, z): (decalage du sol, hauteur du plafond au dessus de YF2)}, 'chambre', 'tunnel', 'lave', 'entree'}."""
    g = ord(nom)
    (cpx, rx, rz) = m["chambre"]
    c0x, c0z = vers_local(*cpx)
    vx, vz = vers_local(*m["vers"])
    long = math.hypot(vx - c0x, vz - c0z)
    ux, uz = (vx - c0x) / long, (vz - c0z) / long
    anneau = m["anneau"]
    profonde = lambda c: c in noyau and dist[c] >= anneau + 2                    # une epaisseur de roche entre la chambre et la bordure
    # la chambre : un contour ondule, plus des lobes
    chambre = {}
    for c in noyau:
        if not profonde(c):
            continue
        x, z = c[0] + 0.5, c[1] + 0.5
        dx, dz = (x - c0x) / rx, (z - c0z) / rz
        d = math.hypot(dx, dz)
        theta = math.atan2(dz, dx)
        reff = 1 + 0.24 * math.sin(3 * theta + g) + 0.16 * math.sin(5 * theta + 2 * g) + 0.1 * math.sin(7 * theta + 3 * g) + 0.5 * (bruit(x, z, 4, g) - 0.5)
        if d <= reff:
            chambre[c] = d / reff
    for (angle, facteur, rayon) in m["lobes"]:
        lx = c0x + math.cos(math.radians(angle)) * rx * facteur
        lz = c0z + math.sin(math.radians(angle)) * rz * facteur
        for c in noyau:
            if not profonde(c):
                continue
            x, z = c[0] + 0.5, c[1] + 0.5
            d = math.hypot(x - lx, z - lz) / (rayon * (1 + 0.35 * (bruit(x, z, 3.5, g + 1) - 0.5)))
            if d <= 1:
                chambre[c] = min(chambre.get(c, 9), 0.5 + d / 2)               # les lobes ont un plafond plus bas que le coeur
    # la sortie : on marche de la chambre vers l'ancrage jusqu'a quitter le noyau
    d_bord = 0.0
    while (math.floor(c0x + ux * d_bord), math.floor(c0z + uz * d_bord)) in noyau:
        d_bord += 0.5
    px_, pz_ = -uz, ux
    signe = 1 if g % 2 else -1
    chemin = [(c0x, c0z), (c0x + ux * 0.3 * d_bord + px_ * 3.5 * signe, c0z + uz * 0.3 * d_bord + pz_ * 3.5 * signe),
              (c0x + ux * 0.62 * d_bord - px_ * 3.5 * signe, c0z + uz * 0.62 * d_bord - pz_ * 3.5 * signe),
              (c0x + ux * (d_bord - 3), c0z + uz * (d_bord - 3)), (c0x + ux * (d_bord + 25), c0z + uz * (d_bord + 25))]
    tunnel = set()
    s = 0.0
    for a, b in zip(chemin, chemin[1:]):
        n = max(1, int(math.dist(a, b) / 0.5))
        for i in range(n + 1):
            x, z = a[0] + (b[0] - a[0]) * i / n, a[1] + (b[1] - a[1]) * i / n
            w = 1.7 + 0.45 * math.sin(s / 3 + g) + 0.9 * max(0.0, (s - (d_bord - 5)) / 5)          # il s'evase a l'entree
            for cx in range(int(x) - 4, int(x) + 5):
                for cz in range(int(z) - 4, int(z) + 5):
                    if (cx, cz) in noyau and (cx + 0.5 - x) ** 2 + (cz + 0.5 - z) ** 2 <= w * w:
                        tunnel.add((cx, cz))
            s += math.dist(a, b) / n
    cave = dict.fromkeys(chambre, 0)
    for c in tunnel:
        cave.setdefault(c, 0)
    # le plafond et le sol
    cases = {}
    for c in cave:
        x, z = c[0] + 0.5, c[1] + 0.5
        dn = chambre.get(c)
        if c in tunnel and (dn is None or dn > 0.85):
            plafond = 4 + round(1.5 * bruit(x, z, 5, g + 9)) + (1 if dist[c] <= anneau + 3 else 0)
        else:
            plafond = 5 + round(4 * max(0.0, 1 - dn) ** 0.75 + 1.4 * (bruit(x, z, 3.5, g + 3) - 0.5))
        dtun = min(dist_seg(x, z, a, b) for a, b in zip(chemin, chemin[1:]))
        f = 0 if c in tunnel else round(2 * bruit(x, z, 6, g + 5) * min(1.0, dtun / 5))
        cases[c] = [f, max(plafond, f + 4)]
    for _ in range(4):                                                         # jamais plus d'une marche entre deux cases voisines
        for c in cave:
            for dx, dz in VOISINS:
                n = (c[0] + dx, c[1] + dz)
                if n in cases and cases[c][0] - cases[n][0] > 1:
                    cases[c][0] = cases[n][0] + 1
    # le ruisseau de lave : un arc au fond de la chambre, du cote oppose a la sortie ; il ne coupe jamais le chemin vers les cibles
    angle_sortie = math.atan2(uz, ux)
    lave = set()
    rayon_arc = 0.8
    largeur_lave = max(1.2, min(1.8, rx / 6))
    t = angle_sortie + math.pi - 0.85
    while t <= angle_sortie + math.pi + 0.85:
        ax = c0x + math.cos(t) * rx * rayon_arc
        az = c0z + math.sin(t) * rz * rayon_arc + 0.9 * math.sin(t * 5 + g)
        for c in chambre:
            if profonde(c) and dist[c] >= anneau + 3 and (c[0] + 0.5 - ax) ** 2 + (c[1] + 0.5 - az) ** 2 <= largeur_lave ** 2 and c not in tunnel:
                lave.add(c)
        t += 0.12
    lave -= {(cible[0] + dx, cible[1] + dz) for cible in cibles for dx in range(-3, 4) for dz in range(-3, 4)}       # jamais a moins de 3 blocs du levier ni du coffre
    entree = min(tunnel, key=lambda c: (dist[c], c))
    if lave and not accessible({c for c in cave if c not in lave}, entree, cibles):
        lave = set()
    return {"cases": {c: tuple(v) for c, v in cases.items()}, "chambre": set(chambre), "tunnel": tunnel, "lave": lave, "entree": entree}


# ---------------------------------------------------------------- les stalactites, stalagmites, lianes
def _epaisseur(j, n):
    """L'epaisseur du bloc a j blocs de la pointe, dans une colonne de dripstone de n blocs : pointe, tronc effile, milieu, base (la racine, si la colonne fait au moins 3 blocs)."""
    if j == 0:
        return "tip"
    if j == 1:
        return "frustum"
    return "base" if j == n - 1 else "middle"


def stalactite(blocs, x, y_haut, z, n):
    """Une stalactite de n blocs qui pend du plafond ; y_haut : le bloc d'air juste sous le plafond."""
    for i in range(n):
        blocs[(x, y_haut - i, z)] = S("pointed_dripstone", vertical_direction="down", thickness=_epaisseur(n - 1 - i, n), waterlogged=False)


def stalagmite(blocs, x, y_bas, z, n):
    """Une stalagmite de n blocs qui monte du sol ; y_bas : le bloc d'air juste au dessus du sol."""
    for i in range(n):
        blocs[(x, y_bas + i, z)] = S("pointed_dripstone", vertical_direction="up", thickness=_epaisseur(n - 1 - i, n), waterlogged=False)


def liane(blocs, x, y_haut, z, n, baies):
    """Une liane qui pend du plafond (age maximal : elle ne pousse plus), avec des baies lumineuses si `baies`. Renvoie les cases a baies (elles eclairent)."""
    avec = []
    for i in range(n - 1):
        blocs[(x, y_haut - i, z)] = S("cave_vines_plant", berries=bool(baies and i % 2))
        if baies and i % 2:
            avec.append((x, y_haut - i, z))
    blocs[(x, y_haut - (n - 1), z)] = S("cave_vines", age=25, berries=bool(baies))
    if baies:
        avec.append((x, y_haut - (n - 1), z))
    return avec
