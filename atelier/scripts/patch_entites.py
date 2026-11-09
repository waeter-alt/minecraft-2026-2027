# [claude-build]
"""Ecrit une structure qui ne repose que les entites decoratives d'une arene (cadres, tableaux, mannequins), sans la colle Create.
Un bloc deja identique dans le monde sert d'ancre : une structure sans bloc ne se pose pas.
Usage : patch_entites.py <structure.nbt> "<dossier du monde>" <nom du correctif> <x> <y> <z>   (x y z = origine de pose de l'arene)"""
import sys
from pathlib import Path

import nbtlib
from nbtlib import Compound, Double, Int, List

R = Path(__file__).resolve().parent.parent
src, monde, nomc = sys.argv[1:4]
ox, oy, oz = (int(v) for v in sys.argv[4:7])
f = nbtlib.load(R / src)
ents = [e for e in f["entities"] if str(e["nbt"]["id"]) in ("minecraft:item_frame", "minecraft:painting", "minecraft:armor_stand")]
xs, ys, zs = zip(*[[int(v) for v in e["blockPos"]] for e in ents])
x0, y0, z0 = min(xs), min(ys) - 1, min(zs)
taille = (max(xs) - x0 + 1, max(ys) - y0 + 1, max(zs) - z0 + 1)
pal = list(f["palette"])
ancre = next(b for b in f["blocks"] if all(lo <= int(b["pos"][i]) < lo + t for i, (lo, t) in enumerate(zip((x0, y0, z0), taille))) and "nbt" not in b
             and str(pal[int(b["state"])]["Name"]) in ("minecraft:stone_bricks", "minecraft:smooth_stone", "minecraft:polished_andesite"))
etat = pal[int(ancre["state"])]
bloc = Compound({"pos": List[Int]([Int(int(ancre["pos"][i]) - o) for i, o in enumerate((x0, y0, z0))]), "state": Int(0)})
sortie = []
for e in ents:
    e2 = Compound(dict(e))
    e2["pos"] = List[Double]([Double(float(e["pos"][i]) - o) for i, o in enumerate((x0, y0, z0))])
    e2["blockPos"] = List[Int]([Int(int(e["blockPos"][i]) - o) for i, o in enumerate((x0, y0, z0))])
    sortie.append(e2)
racine = nbtlib.File({"DataVersion": Int(3955), "size": List[Int]([Int(v) for v in taille]), "palette": List[Compound]([etat]), "blocks": List[Compound]([bloc]),
                      "entities": List[Compound](sortie)}, gzipped=True)
racine.save(R / "exports" / f"{nomc}.nbt")
racine.save(R / "launcher/instances/Build-1.21.1/minecraft/saves" / monde / "datapacks/atelier/data/atelier/structure" / f"{nomc}.nbt")
g = nbtlib.load(R / "exports" / f"{nomc}.nbt")
print("entites", len(g["entities"]), [str(e["nbt"]["id"]).split(":")[1] for e in g["entities"]], "ancre", str(etat["Name"]))
print(f"POSE : /place template atelier:{nomc} {ox + x0} {oy + y0} {oz + z0}")
