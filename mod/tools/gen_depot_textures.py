"""Pixel-art textures of the Dépôt Régional hut block: a crate stamped with a brass cogwheel on the front.

Run from the repo root:  python -I -X utf8 mod/tools/gen_depot_textures.py   (needs pillow)
Writes depot_regional_front / _side / _top / _bottom.png (16x16) into the mod resources.
"""
import math
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'interregne', 'textures', 'block')

DARK = (52, 40, 32, 255)
WOOD_D = (104, 76, 48, 255)
WOOD = (136, 102, 64, 255)
WOOD_L = (168, 130, 84, 255)
IRON = (86, 90, 98, 255)
IRON_L = (126, 130, 138, 255)
BRASS = (222, 178, 70, 255)
BRASS_L = (250, 222, 120, 255)
BRASS_D = (150, 108, 36, 255)


def planks(img, seed=0):
    for y in range(16):
        for x in range(16):
            row = y % 4
            img.putpixel((x, y), WOOD_D if row == 3 else WOOD_L if row == 0 and (x * 5 + y * 3 + seed) % 7 == 0 else WOOD)


def frame(img):
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, DARK)


def straps(img):
    for y in range(16):
        for x in (3, 4, 11, 12):
            img.putpixel((x, y), IRON_L if x in (3, 11) else IRON)


def cog(img, cx, cy, r_out, r_in):
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx + 0.5, y - cy + 0.5
            d = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            tooth = (round(ang / (math.pi / 4)) % 2 == 0) and abs(ang - round(ang / (math.pi / 4)) * (math.pi / 4)) < 0.38
            reach = r_out if tooth else r_in
            if d <= reach:
                img.putpixel((x, y), BRASS_L if dx + dy < -1 else BRASS_D if dx + dy > 2 else BRASS)
            if d <= 1.6:
                img.putpixel((x, y), DARK)


def save(img, name):
    img.save(os.path.join(OUT, name + '.png'))


front = Image.new('RGBA', (16, 16)); planks(front, 1); frame(front)
for y in range(1, 15):
    for x in (1, 14):
        front.putpixel((x, y), WOOD_D)
cog(front, 8, 8, 5.4, 3.9)
save(front, 'depot_regional_front')

side = Image.new('RGBA', (16, 16)); planks(side, 3); frame(side); straps(side)
save(side, 'depot_regional_side')

top = Image.new('RGBA', (16, 16)); planks(top, 5); frame(top)
for i in range(2, 14):
    top.putpixel((i, 2), WOOD_D); top.putpixel((i, 13), WOOD_D)
save(top, 'depot_regional_top')

bottom = Image.new('RGBA', (16, 16)); planks(bottom, 2); frame(bottom)
save(bottom, 'depot_regional_bottom')
