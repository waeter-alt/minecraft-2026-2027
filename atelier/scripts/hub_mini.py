# [claude-build]
"""Installe les arenes Ø104 dans le monde Arene Hub : efface les grandes arenes, pose le hub et les 4 mini-arenes.
En jeu : /function atelier:hub_mini"""
import shutil
from pathlib import Path

R = Path(__file__).resolve().parent.parent
D = R / "launcher/instances/Build-1.21.1/minecraft/saves/Arene Hub/datapacks/atelier"
ST, FN = D / "data/atelier/structure", D / "data/atelier/function"
ANCIENNES = [("feu", -126, 114, 125, 365), ("air", 114, -96, 305, 95), ("nature", -366, -126, -115, 125), ("glace", -126, -366, 125, -115)]
MINI = [("mini_feu", -52, 68, 114), ("mini_air", 114, 68, -52), ("mini_nature", -218, 68, -52), ("mini_glace", -52, 68, -218)]
for n in ("hub", "mini_feu", "mini_air", "mini_nature", "mini_glace"):
    shutil.copyfile(R / "exports" / f"{n}.nbt", ST / f"{n}.nbt")
f0 = ["# [claude-build] Charge les zones, puis lance l'effacement des grandes arenes."]
for n, x0, z0, x1, z1 in ANCIENNES:
    f0.append(f"forceload add {x0} {z0} {x1} {z1}")
f0.append("forceload add -114 -114 113 113")
f0 += ['tellraw @s {"text":"Chargement : 30 secondes, puis le jeu va se figer plusieurs fois. Ne le ferme pas.","color":"gold"}', "schedule function atelier:hub_mini_e1 600t"]
(FN / "hub_mini.mcfunction").write_text("\n".join(f0) + "\n", encoding="utf-8")
for i, (n, x0, z0, x1, z1) in enumerate(ANCIENNES, 1):
    l = [f"# [claude-build] Efface l'ancienne grande arene {n}."]
    mx = (x0 + x1) // 2
    for y in range(68, 152):
        b = "minecraft:grass_block" if y == 86 else "minecraft:dirt" if y < 86 else "minecraft:air"
        l.append(f"fill {x0} {y} {z0} {mx} {y} {z1} {b}")
        l.append(f"fill {mx + 1} {y} {z0} {x1} {y} {z1} {b}")
    l.append(f'tellraw @a {{"text":"Ancienne arene {n} effacee ({i}/4)","color":"gray"}}')
    l.append(f"schedule function atelier:hub_mini_{'e' + str(i + 1) if i < 4 else 'pose'} 200t")
    (FN / f"hub_mini_e{i}.mcfunction").write_text("\n".join(l) + "\n", encoding="utf-8")
p = ["# [claude-build] Pose le hub et les 4 arenes de 104 blocs."]
p.append("place template atelier:hub -114 78 -114")
for n, x, y, z in MINI:
    p.append(f"place template atelier:{n} {x} {y} {z}")
p += ["kill @e[type=item]", "tp @a 0 87 20", "forceload remove all", 'tellraw @a {"text":"Les arenes de 104 blocs sont pretes !","color":"green"}']
(FN / "hub_mini_pose.mcfunction").write_text("\n".join(p) + "\n", encoding="utf-8")
print("fonctions ecrites :", sorted(f.name for f in FN.glob("hub_mini*")))
