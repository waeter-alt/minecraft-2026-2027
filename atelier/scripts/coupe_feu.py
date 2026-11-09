# [claude-build]
"""Dessine exports/feu2_caverne.png : la caverne du Feu vue de dessus (sans le plafond), et une coupe verticale est-ouest.
Usage : coupe_feu.py"""
from pathlib import Path

import nbtlib
from PIL import Image, ImageDraw, ImageFont

R = Path(__file__).resolve().parent.parent
f = nbtlib.load(R / "exports/feu2.nbt")
pal = [str(p["Name"]).split(":")[1] for p in f["palette"]]
G = {tuple(int(v) for v in b["pos"]): pal[int(b["state"])] for b in f["blocks"]}
COUL = {"lava": (255, 110, 0), "blackstone": (45, 40, 48), "polished_blackstone": (70, 65, 75), "polished_blackstone_bricks": (85, 80, 90), "cracked_polished_blackstone_bricks": (95, 88, 98),
        "basalt": (110, 110, 118), "netherrack": (125, 45, 45), "magma_block": (200, 80, 25), "fire": (255, 200, 60), "air": None, "light": None}
s_ = 12
Y0, Y1 = 6, 22                                         # du fond du lac a la corniche, dans le fichier
img = Image.new("RGB", (60 * s_ * 2 + 40, 60 * s_ + 60), (25, 22, 26))
dr = ImageDraw.Draw(img)
police = ImageFont.truetype("arial.ttf", 16)
for x in range(22, 82):
    for z in range(22, 82):
        for y in range(Y1, Y0 - 1, -1):                 # premier bloc visible en regardant vers le bas sous la corniche
            n = G.get((x, y, z))
            if n and n not in ("air", "light"):
                c = COUL.get(n, (150, 150, 150))
                k = 0.55 + 0.45 * (y - Y0) / (Y1 - Y0)
                dr.rectangle([(x - 22) * s_, (z - 22) * s_ + 30, (x - 22) * s_ + s_ - 1, (z - 22) * s_ + s_ - 1 + 30], fill=tuple(int(v * k) for v in c))
                break
ox = 60 * s_ + 40
for x in range(22, 82):                                # coupe verticale par le centre, d'ouest en est
    for y in range(0, 44):
        n = G.get((x, y, 52), "dessous" if y < 30 else None)
        if n in (None, "air", "light"):
            continue
        c = (70, 60, 55) if n == "dessous" else COUL.get(n, (150, 150, 150))
        dr.rectangle([ox + (x - 22) * s_, 30 + (43 - y) * s_ // 2 * 2 // 2 * 1, ox + (x - 22) * s_ + s_ - 1, 30 + (43 - y) * s_ // 2 * 2 // 2 * 1 + s_ - 1], fill=c)
dr.text((10, 6), "La caverne vue de dessus (plus clair = plus haut). Nord en haut.", fill=(255, 255, 255), font=police)
dr.text((ox, 6), "Coupe ouest-est par le centre : surface en haut, lac en bas.", fill=(255, 255, 255), font=police)
img.save(R / "exports/feu2_caverne.png")
print("coupe ecrite")
