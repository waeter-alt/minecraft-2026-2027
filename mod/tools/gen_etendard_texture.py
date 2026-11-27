"""Texture atlas (64x64, 16x16 tiles) for the siege banner GeckoLib model, built from vanilla 1.21.1 block textures.
Tiles: 0 deepslate_bricks, 1 polished_blackstone, 2 dark_oak_log, 3 dark_oak_log_top, 4 chain (dark iron),
5 soul_lantern, 6 flag cloth (light grey wool, tinted in game), 7 gold_block, 8 iron_block, 9 dark_oak_planks.
Also writes the _glowmask (soul lantern tile only) for GeckoLib's AutoGlowingGeoLayer.
Usage: python -I atlas.py <out_png>
"""
import glob
import io
import os
import sys
import zipfile

from PIL import Image, ImageEnhance

jar = glob.glob(os.path.expanduser('~/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar'))[0]
z = zipfile.ZipFile(jar)


def tex(name):
    img = Image.open(io.BytesIO(z.read('assets/minecraft/textures/block/%s.png' % name))).convert('RGBA')
    return img.crop((0, 0, 16, 16))


tiles = ['deepslate_bricks', 'polished_blackstone', 'dark_oak_log', 'dark_oak_log_top', None, 'soul_lantern', 'white_wool',
         'gold_block', 'iron_block', 'dark_oak_planks']
atlas = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
glow = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
for i, name in enumerate(tiles):
    x, y = (i % 4) * 16, (i // 4) * 16
    if name is None:
        img = Image.new('RGBA', (16, 16), (52, 54, 64, 255))
        for yy in range(0, 16, 4):
            for xx in range(16):
                img.putpixel((xx, yy), (88, 92, 104, 255))
    else:
        img = tex(name)
    if name == 'white_wool':
        img = ImageEnhance.Brightness(img).enhance(1.04)
    if name == 'soul_lantern':
        # the lantern texture has transparent holes; fill them with the soul-fire colour so a cube reads as a lantern
        bg = Image.new('RGBA', (16, 16), (90, 225, 235, 255))
        bg.alpha_composite(img)
        img = bg
        glow.paste(img, (x, y))
    atlas.paste(img, (x, y))
out = sys.argv[1]
os.makedirs(os.path.dirname(out), exist_ok=True)
atlas.save(out)
glow.save(out.replace('.png', '_glowmask.png'))
print('ok', out)
