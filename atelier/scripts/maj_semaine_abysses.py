# [claude-build]
"""Met a jour les Abysses dans le monde final Arene Semaine : correctif (ancienne version posee -> version actuelle), copie de la nouvelle
structure, fonction de pose, puis assechement (voir sources/assecher.py : /place garde l'eau du monde dans les marches, bougies, lanternes...).
  .venv\\Scripts\\python.exe sources\\maj_semaine_abysses.py [<ancienne version posee .nbt> <suffixe>]
  - 1re retouche (2026-11-08) : base sauvegardes/abysses_avant_retouche.nbt, sans suffixe -> semaine_maj_abysses (posee)
  - 2e retouche (2026-11-08) : base sauvegardes/abysses_retouche1.nbt, suffixe 2 -> semaine_maj_abysses2 (posee sans assechement :
    l'eau est entree, d'ou semaine_assecher_abysses)
En jeu, dans Arene Semaine : /reload puis /function atelier:semaine_maj_abysses<suffixe>   (une seule fois, sans pause)
Assecher seulement (salles et escalier noyes, eau devant le portail) : /reload puis /function atelier:semaine_assecher_abysses"""
import re
import shutil
import subprocess
import sys
from pathlib import Path

import nbtlib

sys.path.insert(0, str(Path(__file__).resolve().parent))
from assecher import assecher  # noqa: E402

R = Path(__file__).resolve().parent.parent
D = R / "launcher/instances/Build-1.21.1/minecraft/saves/Arene Semaine/datapacks/atelier/data/atelier"
BASE, SUF = (sys.argv[1], sys.argv[2]) if len(sys.argv) > 2 else ("sauvegardes/abysses_avant_retouche.nbt", "")
NOMC, FN = f"semaine_abysses{SUF}", f"semaine_maj_abysses{SUF}"
ORIGINE = (-52, 68, 204)                               # Abysses : centre 0 256, gabarit Y68
DEHORS = "fill -16 87 190 15 100 208 air replace minecraft:water"    # l eau sortie par le portail, jusqu aux coins de la boite (8512 blocs, sous la limite de /fill)
r = subprocess.run([sys.executable, str(R / "sources/patch_structure.py"), BASE, "exports/abysses.nbt", "Arene Semaine",
                    NOMC, *map(str, ORIGINE), "18", "dirt"], capture_output=True, text=True, encoding="utf-8", errors="replace", cwd=R)
print(r.stdout.strip().splitlines()[0][:170])
m = re.search(r"POSE : /(place template atelier:\S+ -?\d+ -?\d+ -?\d+)", r.stdout)
assert m, r.stdout + r.stderr
SEC = assecher("exports/abysses.nbt", "Arene Semaine", "semaine_abysses_air", *ORIGINE)
shutil.copyfile(R / "exports/abysses.nbt", D / "structure/abysses.nbt")     # une pose complete future prendra la nouvelle version
(D / f"function/{FN}.mcfunction").write_text("\n".join([
    "# [claude-build] Met a jour les Abysses du monde final.",
    "forceload add -60 180 59 315",
    'tellraw @a {"text":"Abysses : 15 secondes, ne mets pas le jeu en pause.","color":"gold"}', f"schedule function atelier:{FN}_2 300t"]) + "\n", encoding="utf-8")
(D / f"function/{FN}_2.mcfunction").write_text("\n".join([
    "# [claude-build] Pose le correctif des Abysses, puis asseche ce qui doit etre sec.", m.group(1), *SEC, DEHORS,
    "kill @e[type=item]", f"schedule function atelier:{FN}_3 60t"]) + "\n", encoding="utf-8")
(D / f"function/{FN}_3.mcfunction").write_text("\n".join([
    "# [claude-build] Nettoie (algues arrachees, eau qui coulait encore), puis decharge.", DEHORS, "kill @e[type=item]", "forceload remove all",
    'tellraw @a {"text":"Les Abysses sont a jour !","color":"green"}']) + "\n", encoding="utf-8")
(D / "function/semaine_assecher_abysses.mcfunction").write_text("\n".join([
    "# [claude-build] Asseche les Abysses : air dans toutes les salles, blocs secs reposes, eau retiree devant le portail.",
    "forceload add -60 180 59 315",
    'tellraw @a {"text":"Assechement : 15 secondes, ne mets pas le jeu en pause.","color":"gold"}', "schedule function atelier:semaine_assecher_abysses_2 300t"]) + "\n",
    encoding="utf-8")
(D / "function/semaine_assecher_abysses_2.mcfunction").write_text("\n".join([
    "# [claude-build] Air partout ou le fichier veut de l'air ou un bloc sec, puis les blocs secs, puis l'eau du dehors.", *SEC, DEHORS,
    "kill @e[type=item]", "schedule function atelier:semaine_assecher_abysses_3 100t"]) + "\n", encoding="utf-8")
(D / "function/semaine_assecher_abysses_3.mcfunction").write_text("\n".join([
    "# [claude-build] L'eau qui coulait encore, puis decharge.", DEHORS, "kill @e[type=item]", "forceload remove all",
    'tellraw @a {"text":"Les Abysses sont au sec !","color":"green"}']) + "\n", encoding="utf-8")
for f in (FN, f"{FN}_2", f"{FN}_3", "semaine_assecher_abysses", "semaine_assecher_abysses_2", "semaine_assecher_abysses_3"):
    print(f"--- {f}.mcfunction")
    print((D / "function" / f"{f}.mcfunction").read_text(encoding="utf-8").strip())
g = nbtlib.load(D / f"structure/{NOMC}.nbt")
print(NOMC, "taille", [int(v) for v in g["size"]], "blocs", len(g["blocks"]), "entites", len(g["entities"]))
