# [claude-build]
"""
donjon_temple_decor.py - Passe de decoration du Temple des Mille Racines (2026-11-09).
Execute par donjon_temple.py avec ses variables globales (blocs, C, put, S, bruit, h01, PORTES, ANCRAGES, POINTS...), apres le parcours et avant
le decor aleatoire et l'eclairage. Regle d'or : la decoration ne doit jamais fermer un passage, cacher un mecanisme ni toucher un ancrage ;
tout ce qui est solide est donc pose seulement sur des cases libres et non reservees (RES), et le verificateur est relance apres.
"""
import math

import numpy as np

# ---------------------------------------------------------------- outils
FULL = ("stone", "brick", "log", "wood", "planks", "terracotta", "moss_block", "tuff", "copper", "deepslate", "blackstone", "prismarine", "mud", "obsidian",
        "nether_brick", "dirt", "gravel", "basalt", "andesite", "cobblestone", "froglight", "shroomlight", "sea_lantern", "bone_block", "dripstone_block",
        "bookshelf", "lodestone", "magma", "leaves")
NOT_FULL = ("stairs", "slab", "wall", "button", "plate", "carpet", "pointed", "trapdoor", "fence", "bars")
PEINTURE = {"stone", "andesite", "tuff", "mossy_cobblestone", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "moss_block", "rooted_dirt", "gravel", "cobblestone"}
RES = np.zeros((SX, SY, SZ), dtype=bool)                          # cases a ne jamais toucher (passages, mecanismes, ancrages, positions du boss)


def plein(x, y, z):
    n = nom((x, y, z))
    return n is not None and any(k in n for k in FULL) and not any(k in n for k in NOT_FULL)


def libre(x, y, z):
    return 0 <= x < SX and 0 <= y < SY and 0 <= z < SZ and C[x, y, z] and (x, y, z) not in blocs and not RES[x, y, z]


def pose(x, y, z, s):
    if libre(x, y, z):
        put(x, y, z, s)
        return True
    return False


def reserve(x0, x1, y0, y1, z0, z1):
    RES[max(0, x0):x1 + 1, max(0, y0):y1 + 1, max(0, z0):z1 + 1] = True


def pick(liste, v):
    """Choisit dans [(bloc, poids), ...] avec v dans [0, 1)."""
    tot = sum(p for _, p in liste)
    v *= tot
    for b, p in liste:
        v -= p
        if v < 0:
            return b
    return liste[-1][0]


def stairs(nom_, facing, haut=False):
    return S(nom_, facing=facing, half="top" if haut else "bottom", shape="straight", waterlogged=False)


def slab(nom_, haut=False):
    return S(nom_, type="top" if haut else "bottom", waterlogged=False)


def mur_poteau(nom_):
    return S(nom_, up=True, north="none", south="none", east="none", west="none", waterlogged=False)


def carpet(c):
    return S(c + "_carpet")


def wall_banner(c, facing):
    return S(c + "_wall_banner", facing=facing)


def wall_torch(facing, ame=False):
    return S("soul_wall_torch" if ame else "wall_torch", facing=facing)


def lichen(face):
    return S("glow_lichen", **{k: (k == face) for k in ("down", "up", "north", "south", "east", "west")}, waterlogged=False)


OPP = {"east": "west", "west": "east", "north": "south", "south": "north"}

# ---------------------------------------------------------------- reservations : tout ce qui ne doit pas etre gene
for (n_, x, y, z) in ANCRAGES:                                     # cylindre de balancement sous chaque ancrage
    reserve(x - 2, x + 2, y - 7, y + 1, z - 2, z + 2)
cles = list(POINTS.values()) + list(PLAQUES_E) + [PLAQUE_IDOLE, PLAQUE_CLE, BOUTON_H3, BOUTON_H8] + [v[:3] for v in LEVIERS.values()] + list(SOCLES.values())
cles += [(x, 63, z) for (x, z) in DALLES] + list(BOSS.values()) + [CLE_ZONE, (44, 42, 92)]
for (x, y, z) in cles:
    reserve(x - 2, x + 2, y - 1, y + 5, z - 2, z + 2)
for n_, p in PORTES.items():
    xs, ys, zs = zip(*[tuple(c) for c in p["cases"]])
    reserve(min(xs) - 2, max(xs) + 2, min(ys) - 1, max(ys) + 2, min(zs) - 2, max(zs) + 2)
for (x, y, z) in BOSS.values():                                    # la position du boss doit rester degagee sur 8 blocs
    reserve(x - 3, x + 3, y, y + 8, z - 3, z + 3)
MOUCHES = [(35, 44, 63, 68, 20, 26), (86, 100, 63, 68, 20, 26), (70, 76, 63, 68, 28, 40), (42, 48, 63, 68, 28, 40), (61, 69, 63, 68, 28, 40),
           (28, 36, 63, 68, 20, 26), (59, 64, 41, 46, 74, 80), (42, 48, 41, 46, 54, 62), (42, 50, 41, 46, 86, 99), (44, 48, 41, 46, 130, 140),
           (36, 46, 71, 76, 194, 197), (36, 44, 71, 76, 198, 204), (104, 110, 47, 52, 96, 108), (60, 80, 41, 46, 60, 64), (56, 66, 83, 86, 52, 56),
           (72, 78, 63, 68, 46, 52)]
for m in MOUCHES:
    reserve(*m)
for x in range(0, SX):                                             # les piliers de saut et corniches de l'aile des Miroirs
    pass
for (px, pz, top) in PILIERS_W:
    reserve(px - 1, px + 2, top, top + 4, pz - 1, pz + 2)
reserve(8, 12, 64, 69, 22, 26)

# ---------------------------------------------------------------- palettes : une seule direction artistique, temple de jungle abandonne
# (briques de pierre moussues et fissurees, cobble, mousse, mud bricks pour l'aile des Serpents ; aucune autre famille de blocs)
CBL = S("cobblestone")
MUDB = S("mud_bricks")
PALETTES = {
    "temple": dict(mur=[(SB, 3), (MSB, 4), (CSB, 2), (MCB, 2), (CBL, 1)], bande=CHB, nervure=MSB, base=MCB, plinthe=SB, accent=MOUSSE),
    "serpent": dict(mur=[(MUDB, 3), (MSB, 3), (CSB, 2), (MCB, 1)], bande=CHB, nervure=MUDB, base=MCB, plinthe=MUDB, accent=MOUSSE),
    "eau": dict(mur=[(MSB, 4), (MCB, 3), (CSB, 2), (SB, 1)], bande=CHB, nervure=MCB, base=MCB, plinthe=MSB, accent=MOUSSE),
    "colonnes": dict(mur=[(SB, 3), (CSB, 3), (S("andesite"), 1), (MSB, 2)], bande=CHB, nervure=S("polished_andesite"), base=S("andesite"), plinthe=SB, accent=MOUSSE),
    "crypte": dict(mur=[(MCB, 3), (CBL, 2), (S("andesite"), 1), (MSB, 2), (CSB, 1)], bande=CSB, nervure=MCB, base=CBL, plinthe=MCB, accent=MOUSSE),
    "boss": dict(mur=[(MSB, 4), (CSB, 3), (SB, 2), (MCB, 2)], bande=CHB, nervure=MSB, base=MCB, plinthe=SB, accent=MOUSSE),
}
# (nom, boite, sol, palette, options)
SALLES = [
    ("parvis", (54, 76, 83, 99, 54, 76), 82, "temple", dict(ciel=True)),
    ("vestibule", (40, 90, 63, 73, 14, 34), 62, "temple", dict()),
    ("serpents", (96, 122, 63, 73, 8, 40), 62, "serpent", dict()),
    ("miroirs", (8, 34, 63, 73, 8, 40), None, "eau", dict()),
    ("colonnes", (30, 62, 41, 52, 57, 90), 40, "colonnes", dict()),
    ("antichambre", (42, 50, 41, 50, 94, 99), 40, "temple", dict()),
    ("crypte", (98, 122, 27, 56, 104, 124), None, "crypte", dict()),
    ("sortie", (34, 46, 71, 77, 205, 211), 70, "boss", dict()),
    ("boss", (16, 78, 6, 92, 134, 196), None, "boss", dict()),
]
compte = Counter()


def peindre_mur(x, y, z, pal, fl, i, haut):
    """Bloc d'un mur selon sa hauteur et sa position le long du mur."""
    h = y - fl if fl is not None else y
    if fl is not None and h <= 0:
        return pal["base"]
    if fl is not None and h == 1:
        return pal["plinthe"]
    if h % 10 == 5:
        return pal["bande"]
    if i % 7 == 0 and h % 10 not in (4, 6):
        return pal["nervure"]
    return pick(pal["mur"], (bruit(x + i * .21, y * .6, 4, 410) * 0.65 + h01(x, y, z, 411) * 0.35))


def peindre_salle(nom_, b, fl, pal_n, opt):
    x0, x1, y0, y1, z0, z1 = b
    pal = PALETTES[pal_n]
    faces = []
    for (x, z) in [(x0 - 1, zz) for zz in range(z0, z1 + 1)] + [(x1 + 1, zz) for zz in range(z0, z1 + 1)]:
        for y in range(y0, y1 + 1):
            faces.append((x, y, z, z))
    for (z, x) in [(z0 - 1, xx) for xx in range(x0, x1 + 1)] + [(z1 + 1, xx) for xx in range(x0, x1 + 1)]:
        for y in range(y0, y1 + 1):
            faces.append((x, y, z, x))
    for (x, y, z, i) in faces:
        s = blocs.get((x, y, z))
        if s is not None and s[0].split(":")[1] in PEINTURE and not RES[x, y, z]:
            put(x, y, z, peindre_mur(x, y, z, pal, fl if fl is not None else y0, i, y1))
            compte["murs"] += 1


for (n_, b, fl, pn, op) in SALLES:
    peindre_salle(n_, b, fl, pn, op)
print("   decor : murs peints", compte["murs"], flush=True)
