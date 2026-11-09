# [claude-build]
"""Compare deux versions d'arene_nature.nbt et ecrit une petite structure avec seulement les blocs qui changent.
Usage : patch_grotte_nature.py <ancien.nbt>
Sert a corriger une arene deja posee sans toucher au reste (coffres compris)."""
import sys
from collections import Counter
from pathlib import Path

import nbtlib
from nbtlib import Compound, Int, List, String

RACINE = Path(__file__).resolve().parent.parent
NOUVEAU = RACINE / "exports/arene_nature.nbt"
SORTIES = [RACINE / "exports/arene_nature_grotte.nbt",
           RACINE / "launcher/instances/Build-1.21.1/minecraft/saves/Arene Nature/datapacks/atelier/data/atelier/structure/arene_nature_grotte.nbt"]
ORIGINE = (-126, 68, -126)


def lire(chemin):
    f = nbtlib.load(chemin)
    pal = []
    for p in f["palette"]:
        props = tuple(sorted((str(k), str(v)) for k, v in p.get("Properties", {}).items()))
        pal.append((str(p["Name"]), props))
    return {tuple(int(v) for v in b["pos"]): pal[int(b["state"])] for b in f["blocks"]}, f


avant, _ = lire(sys.argv[1])
apres, fn = lire(NOUVEAU)
diff = {p: s for p, s in apres.items() if avant.get(p) != s}
perdus = [p for p in avant if p not in apres]
print("blocs differents :", len(diff), "| blocs de l'ancienne version absents de la nouvelle :", len(perdus))
if perdus:
    raise SystemExit("cas non prevu : des blocs disparaissent")
xs, ys, zs = zip(*diff)
x0, y0, z0 = min(xs), min(ys), min(zs)
taille = (max(xs) - x0 + 1, max(ys) - y0 + 1, max(zs) - z0 + 1)
print("boite locale :", (x0, y0, z0), "taille", taille)
print("hauteurs touchees (Y monde) :", ORIGINE[1] + min(ys), "a", ORIGINE[1] + max(ys))
print("nouveaux blocs :", Counter(s[0].split(":")[1] for s in diff.values()).most_common(12))
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
racine = nbtlib.File({"DataVersion": Int(int(fn["DataVersion"])), "size": List[Int]([Int(v) for v in taille]),
                      "palette": List[Compound](palette), "blocks": List[Compound](liste),
                      "entities": List[Compound]([])}, gzipped=True)
for c in SORTIES:
    racine.save(c)
wx, wy, wz = ORIGINE[0] + x0, ORIGINE[1] + y0, ORIGINE[2] + z0
print(f"POSE : /forceload add {wx} {wz} {wx + taille[0] - 1} {wz + taille[2] - 1}")
print(f"       /tp @s {wx + taille[0] // 2} 100 {wz + taille[2] // 2}")
print(f"       /place template atelier:arene_nature_grotte {wx} {wy} {wz}")
chk = nbtlib.load(SORTIES[0])
print("relu :", [int(v) for v in chk["size"]], len(chk["blocks"]), "blocs,", len(chk["entities"]), "entites")
