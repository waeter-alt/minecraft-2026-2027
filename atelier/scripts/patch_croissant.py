# [claude-build]
"""Compare deux versions de croissant.nbt et ecrit un correctif avec seulement les blocs modifies.
Usage : patch_croissant.py <ancien.nbt>"""
import sys
from collections import Counter
from pathlib import Path

import nbtlib
from nbtlib import Compound, Int, List, String

R = Path(__file__).resolve().parent.parent
ST = R / "launcher/instances/Build-1.21.1/minecraft/saves/Nouveau monde/datapacks/atelier/data/atelier/structure"
ORIGINE = (-52, 80, -52)


def lire(c):
    f = nbtlib.load(c)
    pal = [(str(p["Name"]), tuple(sorted((str(k), str(v)) for k, v in p.get("Properties", {}).items()))) for p in f["palette"]]
    return {tuple(int(v) for v in b["pos"]): pal[int(b["state"])] for b in f["blocks"]}


avant, apres = lire(sys.argv[1]), lire(R / "exports/croissant.nbt")
diff = {p: s for p, s in apres.items() if avant.get(p) != s}
SOL = 6                                                # sous le sol, le terrain du monde est de la glace compacte
diff.update({p: (("minecraft:packed_ice", ()) if p[1] < SOL else ("minecraft:air", ())) for p in avant if p not in apres})
print("blocs modifies :", len(diff), Counter(s[0].split(":")[1] for s in diff.values()).most_common(7))
xs, ys, zs = zip(*diff)
x0, y0, z0 = min(xs), min(ys), min(zs)
taille = (max(xs) - x0 + 1, max(ys) - y0 + 1, max(zs) - z0 + 1)
palette, index, liste = [], {}, []
for (x, y, z) in sorted(diff):
    s = diff[(x, y, z)]
    if s not in index:
        index[s] = len(palette)
        c = Compound({"Name": String(s[0])})
        if s[1]:
            c["Properties"] = Compound({k: String(v) for k, v in s[1]})
        palette.append(c)
    liste.append(Compound({"pos": List[Int]([Int(x - x0), Int(y - y0), Int(z - z0)]), "state": Int(index[s])}))
racine = nbtlib.File({"DataVersion": Int(3955), "size": List[Int]([Int(v) for v in taille]), "palette": List[Compound](palette), "blocks": List[Compound](liste), "entities": List[Compound]([])}, gzipped=True)
racine.save(R / "exports/croissant_maj.nbt")
racine.save(ST / "croissant_maj.nbt")
wx, wy, wz = ORIGINE[0] + x0, ORIGINE[1] + y0, ORIGINE[2] + z0
print(f"POSE : /place template atelier:croissant_maj {wx} {wy} {wz}")
print("relu :", [int(v) for v in nbtlib.load(R / 'exports/croissant_maj.nbt')["size"]], len(liste), "blocs")
