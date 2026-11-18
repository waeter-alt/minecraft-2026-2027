"""Pixel-art textures, block models and blockstate of the vault hoard (the wealth that fills a regional bank's vault).

Run from the repo root:  python -I -X utf8 mod/tools/gen_vault_assets.py   (needs pillow)
Writes into the mod resources:
  textures/block/vault_*.png                  coins, gold bars, wood, brass, velvet, five gems
  models/block/vault/<kind>_<stage>.json      five kinds x six stages
  blockstates/tresor_de_la_banque.json        picks the model from the block's `kind`, `stage` and `facing`
Kinds: pile (coins), ingots (stacked gold bars), chest (open, overflowing), gems (crystal cluster), jewels (cushion with
ring, goblet, necklace, crown). Stage 1 is a little, stage 6 is a lot: the vault grows by raising the stages.
"""
import json
import os
import random

from PIL import Image

RES = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'interregne')
TEX = os.path.join(RES, 'textures', 'block')
MODELS = os.path.join(RES, 'models', 'block', 'vault')
STATES = os.path.join(RES, 'blockstates', 'tresor_de_la_banque.json')

KINDS = ('pile', 'ingots', 'chest', 'gems', 'jewels')
STAGES = 6


# ---------------------------------------------------------------- textures
def rgba(c):
    return (c[0], c[1], c[2], 255)


def shade(c, f):
    return rgba(tuple(max(0, min(255, int(v * f))) for v in c))


def save(img, name):
    os.makedirs(TEX, exist_ok=True)
    img.save(os.path.join(TEX, 'vault_%s.png' % name))


def coins():
    img = Image.new('RGBA', (16, 16), shade((190, 140, 30), 0.8))
    rnd = random.Random(7)
    for _ in range(11):
        cx, cy = rnd.randrange(1, 15), rnd.randrange(1, 15)
        base = rnd.choice(((255, 205, 60), (240, 185, 45), (255, 225, 110)))
        for dx in range(-2, 3):
            for dy in range(-2, 3):
                if dx * dx + dy * dy <= 4:
                    x, y = (cx + dx) % 16, (cy + dy) % 16
                    img.putpixel((x, y), shade(base, 1.0 if dx + dy < 1 else 0.82))
        img.putpixel((cx % 16, cy % 16), shade(base, 0.7))
        img.putpixel(((cx - 1) % 16, (cy - 1) % 16), rgba((255, 245, 180)))
    return img


def bars():
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            row = y // 4
            off = (row % 2) * 4
            edge = (y % 4 == 3) or ((x + off) % 8 == 7)
            hi = (y % 4 == 0) and not edge
            img.putpixel((x, y), shade((150, 100, 20), 1) if edge else rgba((255, 232, 120)) if hi else rgba((246, 190, 48)))
    return img


def wood():
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            seam = y % 5 == 4
            img.putpixel((x, y), rgba((70, 44, 26)) if seam else rgba((112, 72, 40)) if (x * 3 + y) % 7 else rgba((132, 90, 52)))
    return img


def brass():
    img = Image.new('RGBA', (16, 16), rgba((214, 168, 60)))
    for i in range(16):
        img.putpixel((i, 0), rgba((255, 226, 130)))
        img.putpixel((i, 15), rgba((140, 98, 30)))
    for x, y in ((3, 8), (12, 8)):
        img.putpixel((x, y), rgba((120, 84, 24)))
    return img


def velvet():
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), rgba((150, 24, 40)) if (x + y) % 4 else rgba((116, 16, 32)))
    for i in range(16):
        img.putpixel((i, 0), rgba((220, 170, 60)))
        img.putpixel((i, 15), rgba((220, 170, 60)))
        img.putpixel((0, i), rgba((220, 170, 60)))
        img.putpixel((15, i), rgba((220, 170, 60)))
    return img


def gem(base):
    img = Image.new('RGBA', (16, 16), shade(base, 0.8))
    for y in range(16):
        for x in range(16):
            d = min(x, y, 15 - x, 15 - y)
            if (x - 8) * (x - 8) + (y - 8) * (y - 8) < 36 and ((x + y) % 8 < 4):
                img.putpixel((x, y), shade(base, 1.0 if d % 2 else 1.18))
            if d == 0:
                img.putpixel((x, y), shade(base, 0.55))
    for x, y in ((4, 4), (5, 4), (4, 5), (11, 10)):
        img.putpixel((x, y), rgba((255, 255, 255)))
    return img


def textures():
    save(coins(), 'coins')
    save(bars(), 'bars')
    save(wood(), 'wood')
    save(brass(), 'brass')
    save(velvet(), 'velvet')
    for name, colour in (('emerald', (30, 200, 110)), ('diamond', (110, 230, 240)), ('amethyst', (150, 80, 215)),
                         ('ruby', (215, 35, 60)), ('sapphire', (50, 100, 230))):
        save(gem(colour), name)


# ---------------------------------------------------------------- models
GEMS = ('emerald', 'ruby', 'diamond', 'amethyst', 'sapphire')


def box(frm, to, tex, rot=None):
    face = {'uv': [0, 0, 16, 16], 'texture': '#' + tex}
    element = {'from': [round(v, 3) for v in frm], 'to': [round(v, 3) for v in to], 'faces': {d: dict(face) for d in ('north', 'south', 'east', 'west', 'up', 'down')}}
    if rot:
        axis, angle = rot
        element['rotation'] = {'origin': [(frm[0] + to[0]) / 2, (frm[1] + to[1]) / 2, (frm[2] + to[2]) / 2], 'axis': axis, 'angle': angle}
    return element


def gem_at(x, y, z, size, colour, axis='y'):
    h = size / 2
    return box((x - h, y, z - h), (x + h, y + size, z + h), colour, (axis, 45))


def model(elements, used):
    return {'textures': {**{t: 'interregne:block/vault_' + t for t in used}, 'particle': 'interregne:block/vault_%s' % ('coins' if 'coins' in used else sorted(used)[0])},
            'elements': elements}


def pile(s):
    el = []
    layers = {
        1: [(5, 0, 5, 9, 1, 9), (8, 0, 8, 12, 1, 12), (3, 0, 8, 7, 1, 12)],
        2: [(3, 0, 3, 13, 2, 13)],
        3: [(2, 0, 2, 14, 3, 14), (4, 3, 4, 12, 5, 12)],
        4: [(1, 0, 1, 15, 3, 15), (3, 3, 3, 13, 6, 13), (6, 6, 6, 10, 7, 10)],
        5: [(0, 0, 0, 16, 4, 16), (2, 4, 2, 14, 8, 14), (5, 8, 5, 11, 10, 11)],
        6: [(0, 0, 0, 16, 5, 16), (1, 5, 1, 15, 9, 15), (3, 9, 3, 13, 12, 13), (6, 12, 6, 10, 14, 10)],
    }[s]
    for a in layers:
        el.append(box(a[:3], a[3:], 'coins'))
    used = {'coins'}
    if s >= 5:
        el.append(gem_at(5, 8 if s == 5 else 9, 11, 3, 'ruby'))
        used.add('ruby')
    if s == 6:
        el.append(gem_at(11, 9, 4, 3, 'emerald'))
        el.append(gem_at(8, 14, 8, 3, 'diamond'))
        used |= {'emerald', 'diamond'}
    return model(el, used)


def ingots(s):
    el = []
    tiers = min(4, s)
    for t in range(tiers):
        n = 4 - t
        x0 = 8 - n * 1.9
        for i in range(n):
            el.append(box((x0 + i * 3.8 + 0.2, t * 2.5, 3), (x0 + i * 3.8 + 3.6, t * 2.5 + 2.3, 13), 'bars'))
    if s >= 5:
        el.append(box((1, 0, 1), (5, 2.3, 7), 'bars'))
        el.append(box((11, 0, 9), (15, 2.3, 15), 'bars'))
    if s == 6:
        el.append(box((6.2, 10, 4), (9.8, 12.3, 12), 'bars', ('y', 22.5)))
        el.append(box((1, 2.5, 1.5), (5, 4.8, 7.5), 'bars'))
    return model(el, {'bars'})


def chest(s):
    el = [box((1, 0, 2), (15, 2, 14), 'wood'),
          box((1, 2, 2), (3, 8, 14), 'wood'), box((13, 2, 2), (15, 8, 14), 'wood'),
          box((3, 2, 2), (13, 8, 3.2), 'wood'), box((3, 2, 12.8), (13, 8, 14), 'wood'),
          box((1, 0, 2), (3, 8.4, 3.4), 'brass'), box((13, 0, 2), (15, 8.4, 3.4), 'brass'),
          box((1, 0, 12.6), (3, 8.4, 14), 'brass'), box((13, 0, 12.6), (15, 8.4, 14), 'brass'),
          # the lid standing open at the back
          box((1, 8, 13.4), (15, 15, 15), 'wood', ('x', -22.5)), box((1, 14, 13.8), (15, 15.2, 15.4), 'brass', ('x', -22.5))]
    fill = (8, 7 + s * 0.9)
    el.append(box((3, 2, 3), (13, fill[1], 13), 'coins'))
    used = {'wood', 'brass', 'coins'}
    if s >= 3:
        el.append(box((4, fill[1], 4), (12, fill[1] + 1.6, 12), 'coins'))
    if s >= 4:
        el.append(box((3, 0, 0), (8, 0.8, 2.4), 'coins'))
        el.append(box((9.5, 0, 0.5), (13.5, 0.8, 2), 'coins'))
    if s >= 5:
        el.append(gem_at(6, fill[1] + 1.4, 7, 3, 'ruby'))
        el.append(gem_at(10, fill[1] + 1.4, 9, 3, 'emerald'))
        el.append(box((5, 0, 14.3), (11, 0.8, 16), 'coins'))
        used |= {'ruby', 'emerald'}
    if s == 6:
        el.append(box((5, fill[1] + 1.6, 5), (11, fill[1] + 3.2, 11), 'coins'))
        el.append(gem_at(8, fill[1] + 3.2, 8, 3, 'diamond'))
        el.append(box((0, 0, 3), (1.4, 0.8, 6), 'coins'))
        used |= {'diamond'}
    return model(el, used)


def gems(s):
    spots = [(8, 8, 6, 'emerald'), (5, 5, 8, 'ruby'), (11, 5, 4, 'diamond'), (4, 11, 6, 'amethyst'), (11, 11, 7, 'sapphire'), (8, 3, 5, 'diamond')]
    el = [box((2, 0, 2), (14, 1, 14), 'coins')] if s >= 3 else []
    used = {'coins'} if s >= 3 else set()
    for i in range(s):
        x, z, size, colour = spots[i]
        el.append(gem_at(x, 0.5 if s >= 3 else 0, z, size, colour, 'y'))
        used.add(colour)
    if s >= 5:
        el.append(gem_at(8, 7, 8, 3, 'ruby', 'x'))
        used.add('ruby')
    return model(el, used or {'diamond'})


def jewels(s):
    el = [box((1, 0, 1), (15, 3, 15), 'velvet')]
    used = {'velvet', 'brass', 'bars'}
    # ring
    el.append(box((3, 3, 3), (6, 4.2, 6), 'bars'))
    el.append(gem_at(4.5, 4.2, 4.5, 1.6, 'ruby'))
    used.add('ruby')
    if s >= 2:  # goblet
        el.append(box((11, 3, 3), (12, 6, 4), 'bars'))
        el.append(box((9.8, 6, 2.8), (13.2, 8.5, 5.2), 'bars'))
        el.append(box((10.2, 8.5, 3.2), (12.8, 8.9, 4.8), 'coins'))
        used.add('coins')
    if s >= 3:  # necklace laid in a curve, pendant in the middle
        for i, (x, z) in enumerate(((3, 9), (4.5, 10.4), (6.2, 11.2), (8, 11.5), (9.8, 11.2), (11.5, 10.4), (13, 9))):
            el.append(box((x - 0.6, 3, z - 0.6), (x + 0.6, 3.8, z + 0.6), 'bars'))
        el.append(gem_at(8, 3.4, 12.4, 2.2, 'sapphire'))
        used.add('sapphire')
    if s >= 4:  # crown
        el.append(box((5, 3, 6.5), (11, 6.2, 9.5), 'brass'))
        for x in (5.4, 7.2, 8.8, 10.2):
            el.append(box((x - 0.5, 6.2, 7.5), (x + 0.5, 8.2, 8.5), 'bars'))
        el.append(gem_at(8, 3.6, 6.1, 1.8, 'emerald'))
        used.add('emerald')
    if s >= 5:  # second ring and a loose gem pile
        el.append(box((2.5, 3, 12.5), (4.5, 4, 14.5), 'bars'))
        el.append(gem_at(12.5, 3, 12.5, 2.4, 'diamond'))
        el.append(gem_at(10.5, 3, 11, 1.8, 'amethyst'))
        used |= {'diamond', 'amethyst'}
    if s >= 6:  # the crown grows a jewelled band and a scepter lies across the cushion
        el.append(box((5, 6.2, 6.5), (11, 7.2, 9.5), 'coins'))
        el.append(gem_at(8, 7.2, 8, 2, 'ruby'))
        el.append(box((2, 3, 1.8), (14, 4, 2.8), 'brass'))
        el.append(gem_at(14.3, 3.2, 2.3, 2.2, 'emerald'))
        used.add('emerald')
    return model(el, used)


BUILDERS = {'pile': pile, 'ingots': ingots, 'chest': chest, 'gems': gems, 'jewels': jewels}


def main():
    textures()
    os.makedirs(MODELS, exist_ok=True)
    variants = {}
    for kind in KINDS:
        for s in range(1, STAGES + 1):
            name = '%s_%d' % (kind, s)
            with open(os.path.join(MODELS, name + '.json'), 'w', encoding='utf8') as f:
                json.dump(BUILDERS[kind](s), f, indent=1)
            for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
                variants['kind=%s,stage=%d,facing=%s' % (kind, s, facing)] = {'model': 'interregne:block/vault/' + name, 'y': y}
    with open(STATES, 'w', encoding='utf8') as f:
        json.dump({'variants': variants}, f, indent=1)
    print('textures, %d models, blockstate' % (len(KINDS) * STAGES))


if __name__ == '__main__':
    main()
