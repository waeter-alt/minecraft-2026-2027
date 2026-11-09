# [claude-build]
"""Met a jour le monde final Arene Semaine avec les arenes modifiees depuis sa pose (2026-11-06) :
  Croissant (labyrinthe glissant, 2026-11-08), Feu (souterrain au lac de lave et brasero, 2026-11-08),
  Jungle (refonte, 2026-11-07), Piege (lieu de vie du scientifique, 2026-11-07).
Ecrit un correctif par arene (ancienne version posee -> version actuelle), copie les nouvelles structures et ecrit la fonction de pose.
  .venv\\Scripts\\python.exe sources\\maj_semaine.py
En jeu, dans Arene Semaine : /reload puis /function atelier:semaine_maj   (une seule fois, sans pause)
Puis, apres la pose : /function atelier:semaine_propre (objets au sol du Piege, retour au jour)
Le Feu commence maintenant a Y56 (base : ancienne version decalee de 12, sol local 30)."""
import re
import shutil
import subprocess
import sys
from pathlib import Path

import nbtlib

R = Path(__file__).resolve().parent.parent
D = R / "launcher/instances/Build-1.21.1/minecraft/saves/Arene Semaine/datapacks/atelier/data/atelier"
PY = sys.executable


def correctif(ancien, nouveau, nom_, origine, sol, bas):
    r = subprocess.run([PY, str(R / "sources/patch_structure.py"), ancien, nouveau, "Arene Semaine", nom_, *map(str, origine), str(sol), bas],
                       capture_output=True, text=True, encoding="utf-8", errors="replace", cwd=R)
    print(nom_, ":", r.stdout.strip().splitlines()[0][:170])
    m = re.search(r"POSE : (/place template atelier:\S+ -?\d+ -?\d+ -?\d+)", r.stdout)
    assert m, r.stdout + r.stderr
    return m.group(1)[1:]


poses = [correctif("sauvegardes/croissant_avant_glace.nbt", "exports/croissant.nbt", "semaine_croissant", (-52, 80, -308), 6, "dirt"),
         correctif("sauvegardes/feu2_avant_souterrain_decale.nbt", "exports/feu2.nbt", "semaine_feu", (204, 56, -308), 30, "dirt"),
         correctif("sauvegardes/jungle_avant_refonte.nbt", "exports/jungle.nbt", "semaine_jungle", (204, 226, -52), -1, "air"),
         correctif("sauvegardes/piege_avant_scientifique.nbt", "exports/piege.nbt", "semaine_piege", (204, 68, 204), 18, "dirt")]
retour = next(l for l in (D / "function/semaine_3.mcfunction").read_text(encoding="utf-8").splitlines() if l.startswith("place template atelier:retour_jungle "))
# le Feu descend maintenant jusqu'a Y56 : desert aussi dans la tranche Y32-63 (cubes de 32, limite de fillbiome)
biome_feu = [f"fillbiome {x} 32 {z} {x + 31} 63 {z + 31} minecraft:desert" for x in range(192, 320, 32) for z in range(-320, -192, 32)]
for n in ("croissant", "feu2", "jungle", "piege"):     # une pose complete future prendra les nouvelles versions
    shutil.copyfile(R / "exports" / f"{n}.nbt", D / "structure" / f"{n}.nbt")
(D / "function/semaine_maj.mcfunction").write_text("\n".join([
    "# [claude-build] Met a jour le Croissant, le Feu, la Jungle et le Piege du monde final.",
    "forceload add -52 -308 51 -205", "forceload add 184 -328 327 -185", "forceload add 196 -60 315 59", "forceload add 196 196 315 315",
    'tellraw @a {"text":"Mise a jour : 20 secondes, ne mets pas le jeu en pause.","color":"gold"}', "schedule function atelier:semaine_maj_2 400t"]) + "\n", encoding="utf-8")
(D / "function/semaine_maj_2.mcfunction").write_text("\n".join([
    "# [claude-build] Retire l'ancienne roue de l'usine, pose les quatre correctifs, remet l'arche de retour de la Jungle, biome du bas du Feu.",
    "kill @e[type=create:stationary_contraption]", "kill @e[type=create:super_glue]", *poses, retour, *biome_feu,
    "kill @e[type=item]", "schedule function atelier:semaine_maj_3 60t"]) + "\n", encoding="utf-8")
(D / "function/semaine_maj_3.mcfunction").write_text("\n".join([
    "# [claude-build] Nettoie, puis decharge.", "kill @e[type=item]", "forceload remove all",
    'tellraw @a {"text":"Croissant, Feu, Jungle et Piege sont a jour !","color":"green"}']) + "\n", encoding="utf-8")
# apres la mise a jour (2026-11-08) : 4 plaques de fer de l'ancienne usine au sol dans le Piege, le monde reste a minuit,
# et l'utilisateur ne veut plus de parois invisibles autour des arenes (structures d'air sans_barrieres_* ecrites par semaine.py)
sans = [l for i in range(1, 8) for l in (D / f"function/semaine_{i}.mcfunction").read_text(encoding="utf-8").splitlines() if l.startswith("place template atelier:sans_barrieres_")]
assert len(sans) == 4, sans
(D / "function/semaine_propre.mcfunction").write_text("\n".join([
    "# [claude-build] Retire les parois invisibles et les objets au sol du Piege, remet le jour, avant le zip.",
    "forceload add -52 -308 51 -205", "forceload add 204 -308 307 -205", "forceload add 196 196 315 315", "forceload add -308 -52 -205 51",
    'tellraw @a {"text":"Nettoyage : 10 secondes, ne mets pas le jeu en pause.","color":"gold"}', "schedule function atelier:semaine_propre_2 200t"]) + "\n", encoding="utf-8")
(D / "function/semaine_propre_2.mcfunction").write_text("\n".join([
    "# [claude-build] Retire les barrieres (Croissant, Feu, Piege, Portes), nettoie, decharge, remet le jour.", *sans,
    "kill @e[type=item]", "forceload remove all", "time set 6000",
    'tellraw @a {"text":"Monde propre : parois invisibles retirees, il fait jour !","color":"green"}']) + "\n", encoding="utf-8")
for f in ("semaine_maj", "semaine_maj_2", "semaine_maj_3", "semaine_propre", "semaine_propre_2"):
    print(f"--- {f}.mcfunction")
    print((D / "function" / f"{f}.mcfunction").read_text(encoding="utf-8").strip())
for n in ("semaine_croissant", "semaine_feu", "semaine_jungle", "semaine_piege"):
    g = nbtlib.load(D / "structure" / f"{n}.nbt")
    print(n, "taille", [int(v) for v in g["size"]], "blocs", len(g["blocks"]), "entites", len(g["entities"]))
