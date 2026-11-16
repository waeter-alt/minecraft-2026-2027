"""Pixel-art textures of the Relais commercial hut block: a caravan crate with a merchant sign on the front.

Run from the repo root:  python -I -X utf8 mod/tools/gen_relais_textures.py   (needs pillow)
Writes relais_commercial_front / _side / _top / _bottom.png (16x16) into the mod resources.
"""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'interregne', 'textures', 'block')

DARK = (62, 38, 22, 255)
WOOD_D = (110, 72, 40, 255)
WOOD = (141, 96, 55, 255)
WOOD_L = (172, 126, 78, 255)
CREAM = (234, 222, 186, 255)
CREAM_D = (205, 189, 148, 255)
RED = (168, 50, 45, 255)
RED_D = (120, 32, 32, 255)
GOLD = (236, 184, 56, 255)
GOLD_L = (255, 231, 130, 255)
GOLD_D = (170, 120, 30, 255)
ROPE = (196, 164, 104, 255)
NAIL = (222, 212, 178, 255)


def planks(img, x0, y0, x1, y1, seed=0):
    """Horizontal planks with a darker seam every 4 rows and a few lighter grain pixels."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            row = (y - y0) % 4
            col = WOOD_D if row == 3 else WOOD
            if row == 0 and (x * 5 + y * 3 + seed) % 7 == 0:
                col = WOOD_L
            img.putpixel((x, y), col)


def frame(img):
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, DARK)


def side():
    img = Image.new('RGBA', (16, 16))
    planks(img, 0, 0, 16, 16, 2)
    # diagonal braces
    for i in range(1, 15):
        img.putpixel((i, i), WOOD_L)
        img.putpixel((15 - i, i), WOOD_L)
        if 2 < i < 13:
            img.putpixel((i + 1, i), DARK)
            img.putpixel((14 - i, i), DARK)
    frame(img)
    for p in ((2, 2), (13, 2), (2, 13), (13, 13)):
        img.putpixel(p, NAIL)
    return img


def top():
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), RED if (x // 3) % 2 == 0 else CREAM)
            if (x // 3) % 2 == 1 and (x + y) % 5 == 0:
                img.putpixel((x, y), CREAM_D)
    # rope crossing the tarp
    for i in range(16):
        img.putpixel((i, 7), ROPE)
        img.putpixel((i, 8), (160, 130, 80, 255))
        img.putpixel((7, i), ROPE)
        img.putpixel((8, i), (160, 130, 80, 255))
    frame(img)
    return img


def bottom():
    img = Image.new('RGBA', (16, 16))
    planks(img, 0, 0, 16, 16, 5)
    frame(img)
    return img


def front():
    img = Image.new('RGBA', (16, 16))
    planks(img, 0, 0, 16, 16, 1)
    # cream board
    for y in range(3, 15):
        for x in range(2, 14):
            img.putpixel((x, y), CREAM if (x + y) % 9 else CREAM_D)
    for x in range(2, 14):
        img.putpixel((x, 3), DARK)
        img.putpixel((x, 14), DARK)
    for y in range(3, 15):
        img.putpixel((2, y), DARK)
        img.putpixel((13, y), DARK)
    # red valance with two tassels, like a market awning
    for x in range(3, 13):
        img.putpixel((x, 4), RED)
        img.putpixel((x, 5), RED if x % 2 else RED_D)
    img.putpixel((4, 6), RED)
    img.putpixel((8, 6), RED)
    img.putpixel((11, 6), RED)
    # gold coin with a highlight and an embossed mark
    cx, cy, r = 7.5, 9.5, 3.6
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= r:
                img.putpixel((x, y), GOLD)
                if d > r - 1.1:
                    img.putpixel((x, y), GOLD_D)
                if x + y < 15 and d < r - 1.5:
                    img.putpixel((x, y), GOLD_L)
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if 1.5 < d < 2.3:
                img.putpixel((x, y), GOLD_D)
    frame(img)
    # chains of the sign
    for p in ((3, 1), (3, 2), (12, 1), (12, 2)):
        img.putpixel(p, ROPE)
    return img


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for name, fn in (('front', front), ('side', side), ('top', top), ('bottom', bottom)):
        fn().save(os.path.join(OUT, 'relais_commercial_%s.png' % name))
        print('wrote relais_commercial_%s.png' % name)
