# [claude-build]
"""Dessine exports/piege_coupe.png : l'arene piege vue de dessus au niveau des joueurs (sans toits), et la fosse avec la roue en encart.
Usage : coupe_piege.py"""
from pathlib import Path

import nbtlib
from PIL import Image, ImageDraw, ImageFont

R = Path(__file__).resolve().parent.parent
f = nbtlib.load(R / "exports/piege.nbt")
pal = [str(p["Name"]).split(":")[1] for p in f["palette"]]
G = {tuple(int(v) for v in b["pos"]): pal[int(b["state"])] for b in f["blocks"]}
SOL = 18
COUL = {"belt": (40, 40, 45), "creative_motor": (150, 70, 200), "brass_casing": (215, 170, 70), "copper_casing": (190, 110, 80), "andesite_casing": (170, 150, 120),
        "stone_pressure_plate": (230, 60, 50), "glass": (190, 225, 240), "tinted_glass": (60, 50, 70), "iron_bars": (110, 115, 120), "bookshelf": (135, 95, 55),
        "chiseled_bookshelf": (150, 105, 60), "sculk": (15, 70, 85), "sculk_catalyst": (30, 100, 110), "blackstone": (45, 40, 50), "moss_block": (90, 125, 50),
        "oak_leaves": (60, 110, 40), "farmland": (110, 75, 45), "spruce_planks": (115, 85, 50), "stripped_spruce_wood": (125, 95, 60), "hay_block": (200, 170, 50),
        "waxed_cut_copper": (195, 110, 80), "waxed_copper_grate": (185, 105, 75), "waxed_copper_bulb": (240, 190, 110), "smooth_quartz": (235, 230, 225),
        "sea_lantern": (210, 240, 230), "lava": (255, 110, 0), "polished_andesite": (165, 165, 160), "stone_brick_stairs": (150, 150, 150), "ladder": (160, 120, 60),
        "oak_trapdoor": (150, 115, 70), "red_wool": (170, 40, 40), "campfire": (230, 120, 40), "furnace": (100, 100, 100), "smoker": (95, 85, 75)}
s_ = 8
img = Image.new("RGB", (104 * s_ + 300, 104 * s_ + 40), (30, 30, 34))
dr = ImageDraw.Draw(img)
police = ImageFont.truetype("arial.ttf", 14)
for x in range(104):
    for z in range(104):
        c = None
        for y in (SOL + 3, SOL + 2, SOL + 1, SOL):
            n = G.get((x, y, z))
            if n and n not in ("air", "light", "barrier"):
                c = COUL.get(n, (125, 125, 125) if y > SOL else (190, 186, 176))
                break
        if c:
            dr.rectangle([x * s_, z * s_, x * s_ + s_ - 1, z * s_ + s_ - 1], fill=c)
ox, oy, e = 104 * s_ + 20, 40, 9                         # encart : la fosse a Y local 8, avec la roue
dr.text((ox, 10), "La fosse, 10 blocs sous le plancher", fill=(255, 255, 255), font=police)
for x in range(52 - 15, 52 + 15):
    for z in range(52 - 15, 52 + 15):
        n = G.get((x, 8, z)) or G.get((x, 3, z))
        if n and n not in ("air", "light"):
            dr.rectangle([ox + (x - 37) * e, oy + (z - 37) * e, ox + (x - 37) * e + e - 1, oy + (z - 37) * e + e - 1], fill=COUL.get(n, (120, 120, 120)))
dr.text((ox, oy + 30 * e + 10), "Laiton : la roue. Orange : la lave.\nA droite : corniche et tunnel du retour.", fill=(255, 255, 255), font=police)
dr.text((10, 104 * s_ + 12), "Niveau des joueurs, sans les toits. Nord en haut.", fill=(255, 255, 255), font=police)
img.save(R / "exports/piege_coupe.png")
print("coupe ecrite")
