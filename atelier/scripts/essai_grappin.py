# [claude-build]
"""Terrain d'essai du grappin 2.0 (balancier) : deux tours de 20 blocs separees par un vide de 48, une poutre a 36 de haut
et 6 points d'ancrage dessous (a 8 blocs d'ecart), plus un ancrage haut sur la tour d'arrivee pour tester la corde courte.
Ecrit dans exports/ et dans le datapack du monde du donjon. Pose : /place template atelier:essai_grappin 100 -61 0"""
from pathlib import Path

import nbtlib
from nbtlib import Compound, Int, List, String

R = Path(__file__).resolve().parent.parent
MONDE = R / "launcher/instances/Build-1.21.1/minecraft/saves/Nouveau monde (1)"
SX, SY, SZ = 60, 40, 40
blocs = {}


def S(n, **p):
    return ("minecraft:" + n if ":" not in n else n, tuple(sorted((k, str(v).lower()) for k, v in p.items())))


PIERRE, MOUSSE, BOIS, ANC, LANT = S("mossy_stone_bricks"), S("moss_block"), S("dark_oak_log", axis="x"), S("grappin:ancrage"), S("lantern", hanging=False)
for x0 in (0, 54):                                     # tours de depart et d'arrivee
    for x in range(x0, x0 + 6):
        for z in range(15, 25):
            for y in range(0, 20):
                blocs[(x, y, z)] = PIERRE
            blocs[(x, 20, z)] = MOUSSE
    blocs[(x0 + 2, 21, 15)] = LANT
for x in range(6, 54):                                 # poutre
    for z in (19, 20):
        blocs[(x, 36, z)] = BOIS
for x in (6, 53):                                      # piliers de la poutre
    for y in range(0, 36):
        blocs[(x, y, 19)] = S("dark_oak_log", axis="y")
for x in range(7, 53):                                 # dessous de la poutre vide (efface les anciens ancrages en reposant)
    blocs[(x, 35, 19)] = S("air")
for x in (12, 20, 28, 36, 44, 51):                     # 6 ancrages sous la poutre, tous les 8 blocs (2026-11-09 : 12 etait trop loin)
    blocs[(x, 35, 19)] = ANC
for y in range(21, 31):                                # mur au-dessus de la tour d'arrivee, ancrage en haut
    for z in range(15, 25):
        blocs[(59, y, z)] = PIERRE
blocs[(58, 30, 20)] = ANC
pal, idx, liste = [], {}, []
for p in sorted(blocs):
    s = blocs[p]
    if s not in idx:
        idx[s] = len(pal)
        c = Compound({"Name": String(s[0])})
        if s[1]:
            c["Properties"] = Compound({k: String(v) for k, v in s[1]})
        pal.append(c)
    liste.append(Compound({"pos": List[Int]([Int(v) for v in p]), "state": Int(idx[s])}))
f = nbtlib.File({"DataVersion": Int(3955), "size": List[Int]([Int(SX), Int(SY), Int(SZ)]), "palette": List[Compound](pal),
                 "blocks": List[Compound](liste), "entities": List[Compound]([])}, gzipped=True)
f.save(R / "exports/essai_grappin.nbt")
f.save(MONDE / "datapacks/atelier/data/atelier/structure/essai_grappin.nbt")
print("essai_grappin :", len(liste), "blocs ; POSE : /place template atelier:essai_grappin 100 -61 0 ; depart : /tp @s 103 -40 20 -90 0")
