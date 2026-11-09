# [claude-build]
"""Correctif generique : ne garde que les blocs modifies entre deux versions d'une structure.
Usage : patch_structure.py <ancien.nbt> <nouveau.nbt> <dossier du monde> <nom du correctif> <x> <y> <z> <sol local> <bloc sous le sol>"""
import sys
from collections import Counter
from pathlib import Path

import nbtlib
from nbtlib import Compound, Double, Int, List, String

ancien, nouveau, monde, nomc = sys.argv[1:5]
ox, oy, oz, sol = (int(v) for v in sys.argv[5:9])
bas = "minecraft:" + sys.argv[9]
R = Path(__file__).resolve().parent.parent


NBT = {}


def lire(c):
    f = nbtlib.load(c)
    pal = [(str(p["Name"]), tuple(sorted((str(k), str(v)) for k, v in p.get("Properties", {}).items()))) for p in f["palette"]]
    NBT.update({tuple(int(v) for v in b["pos"]): b["nbt"] for b in f["blocks"] if "nbt" in b})
    return {tuple(int(v) for v in b["pos"]): (pal[int(b["state"])], str(b.get("nbt", ""))) for b in f["blocks"]}


avant, apres = lire(ancien), lire(nouveau)
diff = {p: s for p, s in apres.items() if avant.get(p) != s}
diff.update({p: (((bas, ()) if p[1] <= sol else ("minecraft:air", ())), "") for p in avant if p not in apres})
print("blocs modifies :", len(diff), Counter(s[0][0].split(":")[1] for s in diff.values()).most_common(6))
xs, ys, zs = zip(*diff)
x0, y0, z0 = min(xs), min(ys), min(zs)
taille = (max(xs) - x0 + 1, max(ys) - y0 + 1, max(zs) - z0 + 1)
palette, index, liste = [], {}, []
for (x, y, z) in sorted(diff):
    s, n_ = diff[(x, y, z)]
    if s not in index:
        index[s] = len(palette)
        c = Compound({"Name": String(s[0])})
        if s[1]:
            c["Properties"] = Compound({k: String(v) for k, v in s[1]})
        palette.append(c)
    e = Compound({"pos": List[Int]([Int(x - x0), Int(y - y0), Int(z - z0)]), "state": Int(index[s])})
    if n_ and (x, y, z) in NBT:
        e["nbt"] = NBT[(x, y, z)]
    liste.append(e)
ents = []                                               # les entites nouvelles suivent le correctif (colle Create)
vieilles = {(str(e["nbt"]), str(e["pos"])) for e in nbtlib.load(ancien).get("entities", [])}   # meme entite = meme contenu ET meme position
for e in nbtlib.load(nouveau).get("entities", []):
    if (str(e["nbt"]), str(e["pos"])) not in vieilles:
        e["pos"] = List[Double]([Double(float(e["pos"][i]) - o) for i, o in enumerate((x0, y0, z0))])
        e["blockPos"] = List[Int]([Int(int(e["blockPos"][i]) - o) for i, o in enumerate((x0, y0, z0))])
        ents.append(e)
racine = nbtlib.File({"DataVersion": Int(3955), "size": List[Int]([Int(v) for v in taille]), "palette": List[Compound](palette), "blocks": List[Compound](liste), "entities": List[Compound](ents)}, gzipped=True)
racine.save(R / "exports" / f"{nomc}.nbt")
racine.save(R / "launcher/instances/Build-1.21.1/minecraft/saves" / monde / "datapacks/atelier/data/atelier/structure" / f"{nomc}.nbt")
print(f"POSE : /place template atelier:{nomc} {ox + x0} {oy + y0} {oz + z0}")
