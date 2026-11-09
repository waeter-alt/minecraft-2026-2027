# [claude-build]
"""Assechement apres un correctif pose sur de l'eau : /place template garde l'eau du monde dans les blocs qui peuvent en contenir
(marches, bougies, lanternes, chaines...). Un tel bloc pose sur l'ancienne eau devient une source dans une salle seche.
Ecrit deux structures pour un monde :
  <nom>_vide : de l'air sur chaque case d'air du fichier et sur chaque bloc voulu sec (waterlogged=false)
  <nom>_secs : ces blocs secs seuls, a reposer sur l'air (ils restent secs)
Usage : assecher.py <structure.nbt> "<monde>" <nom> <x> <y> <z>     (origine de la structure dans ce monde)"""
import sys
from pathlib import Path

import nbtlib
from nbtlib import Compound, Int, List, String

R = Path(__file__).resolve().parent.parent


def ecrire(cases, monde, nom_, taille):
    palette, index, liste = [], {}, []
    for p in sorted(cases, key=lambda q: (q[1], q[2], q[0])):
        s = cases[p]
        if s not in index:
            index[s] = len(palette)
            c = Compound({"Name": String(s[0])})
            if s[1]:
                c["Properties"] = Compound({k: String(v) for k, v in s[1]})
            palette.append(c)
        liste.append(Compound({"pos": List[Int]([Int(v) for v in p]), "state": Int(index[s])}))
    f = nbtlib.File({"DataVersion": Int(3955), "size": List[Int]([Int(v) for v in taille]), "palette": List[Compound](palette),
                     "blocks": List[Compound](liste), "entities": List[Compound]([])}, gzipped=True)
    f.save(R / "exports" / f"{nom_}.nbt")
    d = R / "launcher/instances/Build-1.21.1/minecraft/saves" / monde / "datapacks/atelier/data/atelier/structure"
    if d.exists():
        f.save(d / f"{nom_}.nbt")
    return len(liste)


def assecher(fichier, monde, nom_, ox, oy, oz):
    f = nbtlib.load(R / fichier)
    pal = [(str(q["Name"]), tuple(sorted((str(k), str(v)) for k, v in q.get("Properties", {}).items()))) for q in f["palette"]]
    vide, secs = {}, {}
    for b in f["blocks"]:
        p = tuple(int(v) for v in b["pos"])
        s = pal[int(b["state"])]
        if s[0] == "minecraft:air":
            vide[p] = s
        elif ("waterlogged", "false") in s[1]:
            vide[p] = ("minecraft:air", ())
            secs[p] = s
    m = tuple(min(p[k] for p in vide) for k in range(3))
    t_ = tuple(max(p[k] for p in vide) - m[k] + 1 for k in range(3))
    n1 = ecrire({(x - m[0], y - m[1], z - m[2]): s for (x, y, z), s in vide.items()}, monde, f"{nom_}_vide", t_)
    n2 = ecrire({(x - m[0], y - m[1], z - m[2]): s for (x, y, z), s in secs.items()}, monde, f"{nom_}_secs", t_)
    o = (ox + m[0], oy + m[1], oz + m[2])
    print(f"{nom_}_vide : {n1} cases d'air | {nom_}_secs : {n2} blocs secs | taille {t_}")
    return [f"place template atelier:{nom_}_vide {o[0]} {o[1]} {o[2]}", f"place template atelier:{nom_}_secs {o[0]} {o[1]} {o[2]}"]


if __name__ == "__main__":
    for ligne in assecher(sys.argv[1], sys.argv[2], sys.argv[3], *(int(v) for v in sys.argv[4:7])):
        print("POSE :", ligne)
