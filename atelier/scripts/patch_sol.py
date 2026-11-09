# [claude-build]
"""Compare les .nbt du hub et des arenes Ø104 avec une version precedente et ecrit des correctifs
qui ne contiennent que les blocs modifies : les coffres deja poses restent en place.
Usage : patch_sol.py <dossier des anciens .nbt>
En jeu : /reload puis /function atelier:sol_doux"""
import sys
from collections import Counter
from pathlib import Path

import nbtlib
from nbtlib import Compound, Int, List, String

R = Path(__file__).resolve().parent.parent
AVANT = Path(sys.argv[1])
D = R / "launcher/instances/Build-1.21.1/minecraft/saves/Arene Hub/datapacks/atelier"
ST, FN = D / "data/atelier/structure", D / "data/atelier/function"
ZONES = {"hub": (-114, 78, -114), "mini_feu": (-52, 68, 114), "mini_air": (114, 68, -52), "mini_nature": (-218, 68, -52), "mini_glace": (-52, 68, -218)}
AIR = ("minecraft:air", ())


def lire(chemin):
    f = nbtlib.load(chemin)
    pal = [(str(p["Name"]), tuple(sorted((str(k), str(v)) for k, v in p.get("Properties", {}).items()))) for p in f["palette"]]
    nbt = {tuple(int(v) for v in b["pos"]) for b in f["blocks"] if "nbt" in b}
    return {tuple(int(v) for v in b["pos"]): pal[int(b["state"])] for b in f["blocks"]}, nbt


f1 = ["# [claude-build] Charge les zones du correctif de sol."]
f2 = ["# [claude-build] Pose le correctif de sol : seuls les blocs modifies changent."]
for n, (ox, oy, oz) in ZONES.items():
    avant, _ = lire(AVANT / f"{n}.nbt")
    apres, panneaux = lire(R / "exports" / f"{n}.nbt")
    diff = {p: s for p, s in apres.items() if avant.get(p) != s and p not in panneaux}
    for p in avant:
        if p not in apres:
            diff[p] = AIR
    print(f"{n:<12} blocs modifies : {len(diff):>6}", Counter(s[0].split(":")[1] for s in diff.values()).most_common(5))
    if not diff:
        continue
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
    racine = nbtlib.File({"DataVersion": Int(3955), "size": List[Int]([Int(v) for v in taille]), "palette": List[Compound](palette),
                          "blocks": List[Compound](liste), "entities": List[Compound]([])}, gzipped=True)
    nom = f"sol_{n}"
    racine.save(R / "exports" / f"{nom}.nbt")
    racine.save(ST / f"{nom}.nbt")
    wx, wy, wz = ox + x0, oy + y0, oz + z0
    f1.append(f"forceload add {wx} {wz} {wx + taille[0] - 1} {wz + taille[2] - 1}")
    f2.append(f"place template atelier:{nom} {wx} {wy} {wz}")
f1 += ['tellraw @s {"text":"Chargement du correctif : 15 secondes. Reste dans le jeu, sans pause, et ne relance pas la commande.","color":"gold"}', "schedule function atelier:sol_doux_pose 300t"]
f2 += ["forceload remove all", 'tellraw @a {"text":"Le sol est simplifie !","color":"green"}']
(FN / "sol_doux.mcfunction").write_text("\n".join(f1) + "\n", encoding="utf-8")
(FN / "sol_doux_pose.mcfunction").write_text("\n".join(f2) + "\n", encoding="utf-8")
print("\n".join(f2[1:-2]))
