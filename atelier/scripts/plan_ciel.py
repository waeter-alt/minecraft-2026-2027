# [claude-build]
"""plan_ciel.py - plan de l'Archipel des Nuees vu de dessus (hauteur en nuances, vols prevus en traits) et de cote (coupe par projection) : exports/ciel_plan.png.
Reserve aux organisateurs : il montre toutes les solutions.
  test-local\\venv\\Scripts\\python.exe atelier\\scripts\\plan_ciel.py"""
import json
from pathlib import Path

import nbtlib
import numpy as np
from PIL import Image, ImageDraw

RACINE = Path(__file__).resolve().parent.parent
conf = json.loads((RACINE / "exports/ciel_cibles.json").read_text(encoding="utf-8"))
ver = json.loads((RACINE / "exports/ciel_verif.json").read_text(encoding="utf-8")) if (RACINE / "exports/ciel_verif.json").exists() else {"vols": {}}
f = nbtlib.load(RACINE / "exports/ciel.nbt")
SX, SY, SZ = (int(v) for v in f["size"])
pal = [str(p["Name"]).split(":")[1] for p in f["palette"]]
H = np.full((SX, SZ), -1, dtype=np.int32)
NAME = np.empty((SX, SZ), dtype=object)
PROJ = np.full((SX, SY), -1, dtype=np.int32)
for b in f["blocks"]:
    x, y, z = (int(v) for v in b["pos"])
    n = pal[int(b["state"])]
    if n in ("light", "air"):
        continue
    if y > H[x, z]:
        H[x, z] = y
        NAME[x, z] = n
    PROJ[x, y] = 1
E = 4
img = Image.new("RGB", (SX * E + 20, SZ * E + 20 + SY * 2 + 40), (14, 16, 28))
px = img.load()
for x in range(SX):
    for z in range(SZ):
        if H[x, z] < 0:
            continue
        t = H[x, z] / SY
        n = NAME[x, z]
        c = (int(70 + 150 * t), int(120 + 110 * t), int(70 + 130 * t))
        if n in ("grass_block", "moss_block", "short_grass"):
            c = (int(60 + 40 * t), int(130 + 90 * t), int(60 + 40 * t))
        elif n in ("quartz_bricks", "smooth_quartz", "quartz_pillar", "chiseled_quartz_block"):
            c = (235, 235, 225)
        elif n in ("iron_bars",):
            c = (200, 60, 60)
        for a in range(E):
            for b in range(E):
                px[10 + x * E + a, 10 + z * E + b] = c
d = ImageDraw.Draw(img)
iles = conf["iles"]
for k, i in iles.items():
    d.text((10 + i["cx"] * E - 6, 10 + i["cz"] * E - 6), k, fill=(255, 255, 0))
cp = {k: v for k, v in conf["cp"].items()}
for a_b in ver.get("paires_realistes", []):
    a, b = a_b.split(">")
    if iles[a]["ordre"] is not None and iles[b]["ordre"] is not None and 0 < iles[b]["ordre"] - iles[a]["ordre"] <= 1.0:
        d.line((10 + iles[a]["cx"] * E, 10 + iles[a]["cz"] * E, 10 + iles[b]["cx"] * E, 10 + iles[b]["cz"] * E), fill=(255, 120, 40), width=2)
oy = SZ * E + 30
for x in range(SX):
    for y in range(SY):
        if PROJ[x, y] > 0:
            for a in range(E):
                px[10 + x * E + a, oy + (SY - 1 - y) * 2] = (160, 190, 210)
d.text((10, oy + SY * 2 + 4), "coupe de profil (x horizontal, y vertical, projection)", fill=(200, 200, 200))
(RACINE / "exports").mkdir(exist_ok=True)
img.save(RACINE / "exports/ciel_plan.png")
print("ok", img.size)
