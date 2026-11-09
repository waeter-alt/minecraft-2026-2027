# [claude-build]
"""Dessine exports/croissant_sol.png : le sol du labyrinthe vu de dessus (glace compactee, glace bleue, plaques non glissantes).
Usage : plan_glace.py"""
from pathlib import Path

import nbtlib
from PIL import Image, ImageDraw, ImageFont

R = Path(__file__).resolve().parent.parent
f = nbtlib.load(R / "exports/croissant.nbt")
pal = [str(p["Name"]).split(":")[1] for p in f["palette"]]
G = {tuple(int(v) for v in b["pos"]): pal[int(b["state"])] for b in f["blocks"]}
SOL = 6
COUL = {"packed_ice": (150, 190, 235), "blue_ice": (40, 90, 220), "snow_block": (250, 250, 250), "calcite": (205, 205, 200), "powder_snow": (255, 200, 210)}
s_ = 8
img = Image.new("RGB", (104 * s_, 104 * s_ + 70), (40, 44, 52))
dr = ImageDraw.Draw(img)
police = ImageFont.truetype("arial.ttf", 15)
for x in range(104):
    for z in range(104):
        haut = G.get((x, SOL + 1, z))
        sol = G.get((x, SOL, z))
        if haut not in (None, "air", "light", "snow", "soul_lantern", "barrier"):
            c = (95, 100, 110)                           # mur, tour, obstacle
        elif sol in COUL:
            c = COUL[sol]
        elif sol:
            c = (175, 180, 190)
        else:
            continue
        dr.rectangle([x * s_, z * s_, x * s_ + s_ - 1, z * s_ + s_ - 1], fill=c)
for i, (nomc, c) in enumerate((("glace compactée", COUL["packed_ice"]), ("glace bleue", COUL["blue_ice"]), ("neige (non glissant)", COUL["snow_block"]),
                               ("calcite (non glissant)", COUL["calcite"]), ("poudreuse (hors labyrinthe)", COUL["powder_snow"]))):
    px, py = 12 + (i % 3) * 270, 104 * s_ + 12 + (i // 3) * 26
    dr.rectangle([px, py + 2, px + 16, py + 18], fill=c)
    dr.text((px + 24, py), nomc, fill=(255, 255, 255), font=police)
img.save(R / "exports/croissant_sol.png")
print("plan ecrit")
