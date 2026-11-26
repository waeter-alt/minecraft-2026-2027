"""Concept renders of the five road levels and the war road (isometric voxel renders, NOT in-game screenshots).

Run from the repo root:  python -I -X utf8 mod/tools/render_routes_concept.py
Reuses the voxel renderer of render_relais.py. Each variant gets two scenes: a stretch of road crossing a river on its
bridge, and the portal of a tunnel. Writes screenshots/routes-concept/*.png. These are proposals to validate, the road
builder does not lay them yet.
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(__file__))
import render_relais as rr  # noqa: E402

OUT = os.path.join(rr.ROOT, 'screenshots', 'routes-concept')

rr.MATERIAL.update({
    'deepslate_brick': ('deepslate_bricks',) * 3, 'polished_blackstone_brick': ('polished_blackstone_bricks',) * 3,
    'smooth_quartz': ('quartz_block_bottom',) * 3, 'tuff_brick': ('tuff_bricks',) * 3, 'mud_brick': ('mud_bricks',) * 3,
})
rr.SPECIAL.update({
    'dirt_path': ('dirt_path_top', 'dirt_path_side', 'dirt'),
    'quartz_block': ('quartz_block_top', 'quartz_block_side', 'quartz_block_bottom'),
    'smooth_quartz': ('quartz_block_bottom',) * 3,
    'chiseled_quartz_block': ('chiseled_quartz_block_top', 'chiseled_quartz_block', 'chiseled_quartz_block_top'),
    'quartz_pillar': ('quartz_pillar_top', 'quartz_pillar', 'quartz_pillar_top'),
    'chiseled_stone_bricks': ('chiseled_stone_bricks',) * 3,
})
rr.FULL_OPAQUE_SKIP = rr.FULL_OPAQUE_SKIP + ('iron_bars', 'leaves', 'soul_lantern')

TITLES = {
    '1': 'Niveau 1 - Sentier',
    '2': 'Niveau 2 - Chemin empierre',
    '3': 'Niveau 3 - Route pavee',
    '4': 'Niveau 4 - Route royale',
    '5': 'Niveau 5 - Voie imperiale',
    'war': 'Route de guerre',
}
SUBTITLES = {
    '1': '3 de large, terre battue, pont de rondins sur pilotis, tunnel etaye',
    '2': '5 de large, pave et gravier, poteaux-lanternes en bois, pont de bois sur piles de pierre',
    '3': '5 de large + bordures, dalles de pierre, reverberes de pierre, bornes, pont a deux arches, portail voute',
    '4': '7 de large, gros paves uses, murets de pierre seche, lanternes en potence de bois, calvaire, pont a refuges, maison de garde a colombages',
    '5': '9 de large + trottoirs, colonnade a lanternes, haies, statues, pont-aqueduc, portail a fronton',
    'war': '5 de large, deepslate et ornieres, potences a lanternes des ames, palissades de pieux, braseros, pont crenele, herse',
}

B = {}


def put(x, y, z, n, **p):
    B[(x, y, z)] = ('minecraft:' + n, {k: (str(v).lower() if isinstance(v, bool) else str(v)) for k, v in p.items()})


def clear(x, y, z):
    B.pop((x, y, z), None)


def h(x, z, s=0):
    v = (x * 73856093) ^ (z * 19349663) ^ (s * 83492791)
    return (v & 0xffff) / 65536.0


def rail_x(x, y, z, n, x0, x1):
    """A fence or wall running along x, joined to its neighbours."""
    props = {'east': x < x1, 'west': x > x0}
    if n.endswith('_wall'):
        props = {'east': 'low' if x < x1 else 'none', 'west': 'low' if x > x0 else 'none', 'up': x in (x0, x1)}
    put(x, y, z, n, **props)


def ground(x0, x1, z0, z1):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            put(x, -1, z, 'grass_block')
            for y in range(-2, -8, -1):
                put(x, y, z, 'dirt')


RIVER = (15, 24)  # water columns; the banks are at 14 and 25
BED = -6
SPRING = -5  # arches spring from the water line


def river(z0, z1):
    for x in range(RIVER[0] - 1, RIVER[1] + 2):
        for z in range(z0, z1 + 1):
            for y in (-1, -2, -3, -4):
                clear(x, y, z)
            if x in (RIVER[0] - 1, RIVER[1] + 1):
                for y in (-2, -3, -4, -5):
                    put(x, y, z, 'sand')
                continue
            put(x, -5, z, 'water')
            put(x, BED, z, 'gravel' if h(x, z, 3) < 0.5 else 'sand')
            put(x, BED - 1, z, 'dirt')
    for x in range(RIVER[0] - 1, RIVER[1] + 2):
        for z in range(z0, z1 + 1):
            for y in (-6, -7):
                if (x, y, z) not in B:
                    put(x, y, z, 'dirt')


def in_river(x):
    return RIVER[0] - 1 <= x <= RIVER[1] + 1


def arch_void(x, y, centres, r):
    """True where a semicircular arch springing from the river bed leaves the bridge body open."""
    for c in centres:
        d = abs(x - c)
        if d < r and y <= SPRING + math.sqrt(r * r - d * d) - 0.6:
            return True
    return False


def tree(x, z):
    for y in range(0, 4):
        put(x, y, z, 'oak_log', axis='y')
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            for y in (3, 4):
                if (dx, dz) != (0, 0) or y == 4:
                    if not (abs(dx) == 1 and abs(dz) == 1 and y == 4):
                        put(x + dx, y, z + dz, 'oak_leaves')
    put(x, 5, z, 'oak_leaves')


# ---------------------------------------------------------------- surfaces (shared by both scenes)

def surface(v, a, b):
    """Block of the road surface at along-road a, across-road b (b = 0 on the centre line)."""
    if v == '1':
        if abs(b) == 1 and h(a, b, 1) < 0.3:
            return 'grass_block'
        return 'coarse_dirt' if h(a, b, 2) < 0.25 else 'dirt_path'
    if v == '2':
        if abs(b) == 2:
            return 'gravel' if h(a, b, 1) < 0.55 else 'coarse_dirt'
        r = h(a, b, 2)
        return 'mossy_cobblestone' if r < 0.15 else 'gravel' if r < 0.3 else 'cobblestone'
    if v == '3':
        r = h(a, b, 2)
        if r < 0.08:
            return 'cracked_stone_bricks'
        return 'stone_bricks' if (a + b) % 2 == 0 else 'andesite'
    if v == '4':
        if abs(b) == 3:
            return 'cobblestone' if h(a, b, 1) < 0.7 else 'mossy_cobblestone'
        r = h(a, b, 2)
        return 'mossy_stone_bricks' if r < 0.15 else 'cracked_stone_bricks' if r < 0.27 else 'polished_andesite' if r < 0.4 else 'stone_bricks'
    if v == '5':
        if abs(b) == 4 or a % 5 == 0:
            return 'polished_deepslate'
        if b == 0 and a % 5 == 0:
            return 'chiseled_stone_bricks'
        return 'smooth_stone'
    if v == 'war':
        r = h(a, b, 2)
        if abs(b) == 2:
            return 'soul_soil' if r < 0.25 else 'coarse_dirt' if r < 0.5 else 'cobbled_deepslate'
        if abs(b) == 1:
            return 'coarse_dirt' if r < 0.4 else 'cobbled_deepslate'
        return 'deepslate_tiles' if r < 0.2 else 'cobbled_deepslate'

HALF = {'1': 1, '2': 2, '3': 2, '4': 3, '5': 4, 'war': 2}


# ---------------------------------------------------------------- scene 1: road and bridge, running along x

LEN = 40


def road_and_bridge(v):
    B.clear()
    w = HALF[v]
    front = {'1': -4, '2': -5, '3': -5, '4': -10, '5': -10, 'war': -7}[v]
    ground(0, LEN - 1, front, 11)
    river(front, 11)
    side = {'1': 0, '2': 1, '3': 1, '4': 3, '5': 3, 'war': 1}[v]

    # the surface itself, the bridge deck included
    for x in range(LEN):
        for z in range(-w, w + 1):
            put(x, -1, z, surface(v, x, z))
            if in_river(x):
                put(x, -2, z, {'1': 'spruce_log', '2': 'dark_oak_planks'}.get(v, 'stone_bricks'), **({'axis': 'z'} if v == '1' else {}))

    if v == '1':
        for x in range(13, 27):
            for z in range(-1, 2):
                put(x, -1, z, 'spruce_slab' if h(x, z, 5) < 0.12 else 'spruce_planks')
                clear(x, -2, z)
        for x in (16, 19, 22):
            for z in (-1, 1):
                for y in range(BED, -1):
                    put(x, y, z, 'spruce_log', axis='y')
            for z in range(-1, 2):
                put(x, -2, z, 'spruce_log', axis='z')
        for x in (13, 26):
            for z in (-2, 2):
                put(x, 0, z, 'spruce_fence')
        for x in range(3, 6):
            put(x, 0, 4, 'spruce_log', axis='x')
        for x, z in ((8, -3), (31, 3), (35, -2)):
            put(x, 0, z, 'cobblestone_slab' if h(x, z) < 0.5 else 'mossy_cobblestone_slab')

    if v == '2':
        for x in range(LEN):
            if not in_river(x) and h(x, 7, 7) < 0.35:
                put(x, -1, 3 * (1 if h(x, 0, 8) < 0.5 else -1), 'gravel')
        for x in range(12, 28):
            for z in range(-2, 3):
                put(x, -1, z, 'dark_oak_planks')
                clear(x, -2, z)
            for z in (-2, 2):
                put(x, -2, z, 'dark_oak_log', axis='x')
            for z in (-3, 3):
                rail_x(x, 0, z, 'dark_oak_fence', 12, 27)
        for x0 in (17, 21):
            for x in (x0, x0 + 1):
                for z in range(-2, 3):
                    for y in range(BED, -2):
                        put(x, y, z, 'cobblestone')
        for x in (12, 27):
            for z in (-3, 3):
                put(x, 0, z, 'dark_oak_log', axis='y')
                put(x, 1, z, 'lantern')
        for x in (16, 23):
            for z in (-3, 3):
                put(x, 1, z, 'lantern')
        for i, x in enumerate((3, 9, 30, 36)):
            z = -3 if i % 2 == 0 else 3
            put(x, 0, z, 'dark_oak_fence')
            put(x, 1, z, 'dark_oak_fence')
            put(x, 2, z, 'lantern')
        put(5, 0, 4, 'barrel')
        put(6, 0, 4, 'hay_block', axis='y')
        put(6, 0, 5, 'hay_block', axis='y')

    if v == '3':
        for x in range(LEN):
            for z in (-3, 3):
                put(x, -1, z, 'stone_bricks')
                put(x, 0, z, 'stone_brick_slab', type='bottom')
        for x in range(12, 28):
            for z in range(-3, 4):
                for y in range(BED, -1):
                    if arch_void(x, y, (16.5, 23.0), 3.2):
                        continue
                    put(x, y, z, 'stone_bricks')
            for z in (-4, 4):
                for y in range(BED, 0):
                    if not arch_void(x, y, (16.5, 23.0), 3.2):
                        put(x, y, z, 'stone_bricks' if y < -1 else 'stone_bricks')
                rail_x(x, 0, z, 'stone_brick_wall', 12, 27)
        for x in (16, 17, 23):
            put(x, -1, -4, 'chiseled_stone_bricks')
        for x in (12, 27):
            for z in (-4, 4):
                put(x, 0, z, 'chiseled_stone_bricks')
                put(x, 1, z, 'lantern')
        for x in (2, 9, 30, 37):
            for z in (-4, 4):
                put(x, 0, z, 'stone_brick_wall', up=True)
                put(x, 1, z, 'stone_brick_wall', up=True)
                put(x, 2, z, 'lantern')
        put(6, 0, -5, 'chiseled_stone_bricks')
        put(6, 1, -5, 'stone_brick_slab', type='bottom')
        put(33, 0, 5, 'chiseled_stone_bricks')
        put(33, 1, 5, 'stone_brick_slab', type='bottom')

    if v == '4':
        piers = ((17, 18), (22, 23))
        for x in range(11, 29):
            for z in range(-4, 5):
                for y in range(BED, -1):
                    if arch_void(x, y, (14.5, 20.0, 25.5), 2.6):
                        continue
                    put(x, y, z, 'mossy_stone_bricks' if y <= -4 and h(x, z * 7 + y, 4) < 0.45 else 'stone_bricks')
            for z in (-4, 4):
                put(x, -1, z, 'stone_bricks')
                put(x, 0, z, 'stone_bricks')
                put(x, 1, z, 'stone_brick_slab', type='bottom')
        for px in piers:
            for sg in (-1, 1):
                # cutwater: a pointed nose against the current, carrying a refuge (a balcony in the parapet)
                for x in px:
                    for z, top in ((5 * sg, -1), (6 * sg, -2)):
                        for y in range(BED, top + 1):
                            put(x, y, z, 'stone_bricks')
                    put(x, -1, 6 * sg, 'stone_brick_stairs', facing='south' if sg < 0 else 'north', half='bottom')
                for x in range(px[0] - 1, px[1] + 2):
                    clear(x, 0, 4 * sg)
                    clear(x, 1, 4 * sg)
                    put(x, -1, 5 * sg, 'stone_bricks')
                    put(x, 0, 6 * sg, 'stone_bricks')
                    put(x, 1, 6 * sg, 'stone_brick_slab', type='bottom')
                for x in (px[0] - 1, px[1] + 1):
                    put(x, 0, 5 * sg, 'stone_bricks')
                    put(x, 1, 5 * sg, 'stone_brick_slab', type='bottom')
                    put(x, 2, 5 * sg, 'lantern')
        for x in (11, 28):
            for z in (-4, 4):
                put(x, 1, z, 'chiseled_stone_bricks')
                put(x, 2, z, 'lantern')
        # dry-stone walls along the fields, with a gate
        for x in range(LEN):
            if 10 <= x <= 29:
                continue
            for z in (-5, 5):
                if x in (4, 35):
                    put(x, 0, z, 'spruce_fence_gate', facing='north')
                    continue
                n = 'mossy_cobblestone_wall' if h(x, z, 6) < 0.35 else 'cobblestone_wall'
                put(x, 0, z, n, east='low' if x not in (9, 39) else 'none', west='low' if x not in (0, 30) else 'none', up=x in (0, 9, 30, 39))
        # wooden lanterns under a little roof
        for i, x in enumerate((1, 8, 31, 38)):
            z = -4 if i % 2 == 0 else 4
            arm = z + (1 if z < 0 else -1)
            for y in range(0, 4):
                put(x, y, z, 'spruce_log', axis='y')
            put(x, 3, arm, 'spruce_slab', type='bottom')
            put(x, 2, arm, 'lantern', hanging=True)
        # banner poles at the bridge heads
        for x in (9, 30):
            for y in range(0, 5):
                put(x, y, -4, 'spruce_log', axis='y')
            put(x, 3, -5, 'red_wall_banner', facing='north')
        # wayside cross
        put(6, 0, -7, 'mossy_cobblestone')
        for y in range(1, 4):
            put(6, y, -7, 'spruce_fence')
        put(5, 3, -7, 'spruce_fence', east=True)
        put(7, 3, -7, 'spruce_fence', west=True)
        put(6, 3, -7, 'spruce_fence', east=True, west=True)
        put(7, 0, -7, 'lantern')
        for x, z in ((2, 8), (36, 8)):
            tree(x, z)

    if v == '5':
        for x in range(LEN):
            for z in (-7, -6, -5, 5, 6, 7):
                put(x, -1, z, 'stone_bricks')
                put(x, 0, z, 'polished_andesite_slab', type='bottom')
        for x in range(10, 30):
            for z in range(-8, 9):
                for y in range(BED, -1):
                    if arch_void(x, y, (13.0, 17.5, 22.0, 26.5), 2.1):
                        continue
                    put(x, y, z, 'stone_bricks')
            for z in (-8, 8):
                put(x, -1, z, 'smooth_quartz')
                put(x, -2, z, 'quartz_block')
                if x % 3 == 1:
                    put(x, 0, z, 'quartz_pillar', axis='y')
                    put(x, 1, z, 'quartz_pillar', axis='y')
                    if x % 6 == 1:
                        put(x, 2, z, 'lantern')
                else:
                    put(x, 0, z, 'iron_bars')
                    put(x, 1, z, 'smooth_quartz_slab', type='bottom')
        for x in (2, 6, 33, 37):
            for z in (-7, 7):
                for y in range(0, 4):
                    put(x, y, z, 'quartz_pillar', axis='y')
                put(x, 4, z, 'chiseled_quartz_block')
                put(x, 5, z, 'lantern')
                if z < 0:
                    put(x, 2, z - 1, 'white_wall_banner', facing='north')
        for x in range(LEN):
            if not (10 <= x <= 29):
                for z in (-9, 9):
                    put(x, 0, z, 'oak_leaves')
        for x in (10, 29):
            for z in (-8, 8):
                put(x, 0, z, 'smooth_stone')
                put(x, 1, z, 'chiseled_quartz_block')
                put(x, 2, z, 'quartz_pillar', axis='y')
                put(x, 3, z, 'quartz_pillar', axis='y')
                put(x, 4, z, 'chiseled_quartz_block')
                put(x, 5, z, 'lantern')

    if v == 'war':
        for x in range(LEN):
            for z in (-3, 3):
                if not in_river(x) and h(x, z, 9) < 0.3:
                    put(x, -1, z, 'coarse_dirt' if h(x, z, 4) < 0.5 else 'soul_soil')
        for x in range(12, 28):
            for z in range(-3, 4):
                put(x, -1, z, 'polished_blackstone_bricks')
                put(x, -2, z, 'deepslate_bricks')
            for z in (-4, 4):
                put(x, -1, z, 'polished_blackstone_bricks')
                put(x, -2, z, 'deepslate_bricks')
                put(x, 0, z, 'polished_blackstone_bricks')
                if x % 2 == 0:
                    put(x, 1, z, 'polished_blackstone_bricks')
                    if x % 6 == 0:
                        put(x, 2, z, 'soul_lantern')
            for z in (-5,):
                if x % 4 == 2:
                    put(x, 0, z, 'red_wall_banner', facing='north')
        for x0 in (16, 22):
            for x in (x0, x0 + 1):
                for z in range(-4, 5):
                    for y in range(BED, -2):
                        put(x, y, z, 'blackstone')
            for z in (-5, 5):
                for x in (x0, x0 + 1):
                    for y in range(BED, -1):
                        put(x, y, z, 'blackstone')
                    put(x, -1, z, 'iron_bars')
        for i, x in enumerate((2, 10, 30, 38)):
            for z, sg in ((-4, -1), (4, 1)):
                if (i + (sg > 0)) % 2:
                    continue
                for y in range(0, 4):
                    put(x, y, z, 'polished_blackstone_wall', up=True)
                put(x, 3, z - sg, 'polished_blackstone_slab', type='bottom')
                put(x, 2, z - sg, 'soul_lantern', hanging=True)
                if sg < 0:
                    put(x, 2, z - 1, 'red_wall_banner', facing='north')
        # palisade of stakes along the road, in short runs
        for x0, x1 in ((0, 4), (32, 36)):
            for z in (-6, 6):
                for x in range(x0, x1 + 1):
                    put(x, 0, z, 'dark_oak_fence', east=x < x1, west=x > x0)
                    if x % 2 == 0:
                        put(x, 1, z, 'dark_oak_fence')
        for x, z in ((7, -5), (33, 4)):
            put(x, 0, z, 'polished_blackstone')
            put(x, 1, z, 'soul_campfire')

# ---------------------------------------------------------------- scene 2: tunnel portal, road running south into a hill

def hill(x0, x1, z0, z1, top):
    for x in range(x0, x1 + 1):
        edge = min(x - x0, x1 - x)
        for z in range(z0, z1 + 1):
            t = min(top, 2 + edge + (1 if h(x, z, 6) < 0.3 else 0), top + (z - z0) // 3)
            for y in range(-3, t + 1):
                n = 'grass_block' if y == t else 'dirt' if y >= t - 2 else ('stone' if h(x, y * 31 + z, 2) < 0.85 else 'andesite')
                put(x, y, z, n)


def tunnel(v):
    B.clear()
    w = HALF[v]
    hgt = {'1': 3, '2': 4, '3': 4, '4': 5, '5': 6, 'war': 4}[v]
    top = hgt + {'1': 3, '2': 3, '3': 4, '4': 8, '5': 8, 'war': 6}[v]
    half = {'1': 10, '2': 10, '3': 11, '4': 13, '5': 15, 'war': 12}[v]
    ground(-half, half, -14, -1)
    hill(-half, half, 0, 12, top)
    for z in range(-14, 13):
        for x in range(-w, w + 1):
            put(x, -1, z, surface(v, z, x))
            if z >= 0:
                for y in range(0, hgt):
                    clear(x, y, z)
    side = {'5': (5, 6, 7)}.get(v, ())
    for z in range(-14, 0):
        for x in side:
            for xx in (x, -x):
                put(xx, -1, z, 'stone_bricks')
                put(xx, 0, z, 'smooth_stone_slab' if v == '4' else 'polished_andesite_slab', type='bottom')
        if v == '3':
            for xx in (3, -3):
                put(xx, -1, z, 'stone_bricks')
                put(xx, 0, z, 'stone_brick_slab', type='bottom')

    def face(x0, x1, y0, y1, n, z=0, skip_opening=True):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                if skip_opening and abs(x) <= w and y < hgt:
                    continue
                put(x, y, z, n)

    if v == '1':
        for y in range(0, 3):
            for x in (-2, 2):
                put(x, y, 0, 'spruce_log', axis='y')
        for x in range(-2, 3):
            put(x, 3, 0, 'spruce_log', axis='x')
        put(-4, 0, -2, 'cobblestone')
        put(3, 0, -3, 'mossy_cobblestone_slab')

    if v == '2':
        face(-3, 3, 0, 4, 'cobblestone')
        for x in range(-2, 3):
            put(x, 4, 0, 'dark_oak_log', axis='x')
        for x in (-3, 3):
            put(x, 5, 0, 'cobblestone_slab', type='bottom')
        put(0, 3, 0, 'lantern', hanging=True)
        for z in (4, 8):
            for x in (-2, 2):
                for y in range(0, 3):
                    put(x, y, z, 'dark_oak_log', axis='y')
            for x in range(-2, 3):
                put(x, 3, z, 'dark_oak_log', axis='x')
        for x in (-4, 4):
            put(x, 0, -3, 'dark_oak_fence')
            put(x, 1, -3, 'dark_oak_fence')
            put(x, 2, -3, 'lantern')

    if v == '3':
        face(-4, 4, 0, 5, 'stone_bricks')
        put(-2, 3, 0, 'stone_brick_stairs', facing='west', half='top')
        put(2, 3, 0, 'stone_brick_stairs', facing='east', half='top')
        put(0, 4, 0, 'chiseled_stone_bricks')
        for x in range(-5, 6):
            put(x, 6, 0, 'stone_brick_slab', type='bottom')
        for x in (-4, 4):
            for y in range(0, 5):
                put(x, y, -1, 'stone_bricks')
            put(x, 5, -1, 'chiseled_stone_bricks')
            put(x, 6, -1, 'lantern')
        for x in (-4, 4):
            put(x, 0, -6, 'stone_brick_wall', up=True)
            put(x, 1, -6, 'stone_brick_wall', up=True)
            put(x, 2, -6, 'lantern')
        put(5, 0, -10, 'chiseled_stone_bricks')
        put(5, 1, -10, 'stone_brick_slab', type='bottom')

    if v == '4':
        for x in range(-5, 6):
            for y in range(0, 7):
                if abs(x) <= w and y < hgt:
                    continue
                put(x, y, 0, 'mossy_stone_bricks' if h(x, y, 8) < 0.25 else 'stone_bricks')
        put(-3, 4, 0, 'stone_brick_stairs', facing='west', half='top')
        put(3, 4, 0, 'stone_brick_stairs', facing='east', half='top')
        put(0, 5, 0, 'chiseled_stone_bricks')
        for x in (-5, 5):
            for y in range(0, 6):
                put(x, y, -1, 'stone_bricks')
            put(x, 6, -1, 'lantern', hanging=True)
        # half-timbered guardhouse over the portal
        for x in range(-6, 7):
            for z in (-1, 0, 1):
                put(x, 7, z, 'stone_bricks')
            for y in (8, 9, 10):
                if abs(x) == 6 or (x in (-2, 2) and y < 10):
                    put(x, y, -1, 'spruce_log', axis='y')
                elif y == 10:
                    put(x, y, -1, 'spruce_log', axis='x')
                elif y == 9 and x in (-4, 4, 0):
                    put(x, y, -1, 'glass_pane')
                else:
                    put(x, y, -1, 'white_terracotta')
                for z in (0, 1):
                    put(x, y, z, 'spruce_planks')
        for x in range(-7, 8):
            put(x, 11, -2, 'deepslate_tile_stairs', facing='south', half='bottom')
            put(x, 11, -1, 'deepslate_tiles')
            put(x, 12, -1, 'deepslate_tile_stairs', facing='south', half='bottom')
            put(x, 12, 0, 'deepslate_tiles')
            put(x, 13, 0, 'deepslate_tile_slab', type='bottom')
            put(x, 11, 0, 'spruce_planks')
            put(x, 11, 1, 'spruce_planks')
        for x in (-4, 4):
            put(x, 9, -2, 'red_wall_banner', facing='north')
        # walls along the approach, a roofed lantern, a wayside cross
        for z in range(-14, -2):
            for x in (-5, 5):
                if z == -8:
                    continue
                n = 'mossy_cobblestone_wall' if h(x, z, 6) < 0.35 else 'cobblestone_wall'
                put(x, 0, z, n, north='low' if z > -14 else 'none', south='low' if z < -3 else 'none', up=z in (-14, -3))
        for x, z in ((-4, -6), (4, -11)):
            arm = x + (1 if x < 0 else -1)
            for y in range(0, 4):
                put(x, y, z, 'spruce_log', axis='y')
            put(arm, 3, z, 'spruce_slab', type='bottom')
            put(arm, 2, z, 'lantern', hanging=True)
        put(7, 0, -9, 'mossy_cobblestone')
        for y in range(1, 4):
            put(7, y, -9, 'spruce_fence')
        put(6, 3, -9, 'spruce_fence', east=True)
        put(8, 3, -9, 'spruce_fence', west=True)
        put(7, 3, -9, 'spruce_fence', east=True, west=True)
        tree(-9, -9)

    if v == '5':
        face(-9, 9, 0, 9, 'quartz_block')
        for x in (-4, 4):
            put(x, 5, 0, 'quartz_stairs', facing='west' if x < 0 else 'east', half='top')
        put(0, 6, 0, 'chiseled_quartz_block')
        for x in (-8, -6, 6, 8):
            put(x, 0, -1, 'smooth_stone')
            for y in range(1, 7):
                put(x, y, -1, 'quartz_pillar', axis='y')
            put(x, 7, -1, 'chiseled_quartz_block')
        for x in range(-9, 10):
            put(x, 8, -1, 'smooth_quartz')
        for x in range(-9, 10):
            if abs(x) == 9:
                put(x, 9, -1, 'smooth_quartz_stairs', facing='east' if x < 0 else 'west', half='bottom')
            else:
                put(x, 9, -1, 'smooth_quartz')
        for x in range(-6, 7):
            put(x, 10, -1, 'smooth_quartz_stairs', facing='east', half='bottom') if x == -6 else \
                put(x, 10, -1, 'smooth_quartz_stairs', facing='west', half='bottom') if x == 6 else put(x, 10, -1, 'smooth_quartz')
        for x in range(-3, 4):
            put(x, 11, -1, 'smooth_quartz_stairs', facing='east', half='bottom') if x == -3 else \
                put(x, 11, -1, 'smooth_quartz_stairs', facing='west', half='bottom') if x == 3 else put(x, 11, -1, 'smooth_quartz')
        put(0, 12, -1, 'chiseled_quartz_block')
        for x in (-7, 7):
            put(x, 6, -2, 'white_wall_banner', facing='north')
        for z in (-6, -12):
            for x in (-7, 7):
                for y in range(0, 4):
                    put(x, y, z, 'quartz_pillar', axis='y')
                put(x, 4, z, 'chiseled_quartz_block')
                put(x, 5, z, 'lantern')
        for z in range(-14, 0):
            for x in (-9, 9):
                put(x, 0, z, 'oak_leaves')
        for x in range(-6, 7):
            if abs(x) <= 4:
                put(x, -1, -1, 'smooth_stone')

    if v == 'war':
        face(-5, 5, 0, 6, 'polished_blackstone_bricks')
        for x in range(-5, 6):
            if x % 2 == 0:
                put(x, 7, 0, 'polished_blackstone_bricks')
        for x in range(-2, 3):
            put(x, 3, 0, 'iron_bars')
            if x % 2 == 0:
                put(x, 2, 0, 'iron_bars')
        put(0, 4, 0, 'chiseled_polished_blackstone')
        for x in (-7, -6, 6, 7):
            for z in (-1, -2):
                for y in range(0, 9):
                    put(x, y, z, 'blackstone' if y < 8 else 'polished_blackstone_bricks')
        for x in (-7, 7):
            for z in (-1, -2):
                put(x, 9, z, 'polished_blackstone_bricks')
        for x in (-6, 6):
            put(x, 6, -3, 'red_wall_banner', facing='north')
        for x in (-3, 3):
            put(x, 5, -1, 'polished_blackstone_slab', type='bottom')
            put(x, 4, -1, 'soul_lantern', hanging=True)
        for x in (-4, 4):
            put(x, 0, -5, 'polished_blackstone')
            put(x, 1, -5, 'soul_campfire')
        for x in (-5, 5):
            for z in range(-13, -7):
                put(x, 0, z, 'dark_oak_fence', north=z > -13, south=z < -8)
                if z % 2 == 0:
                    put(x, 1, z, 'dark_oak_fence')


# ---------------------------------------------------------------- rendering

def render_scene():
    scene = dict(B)
    xs = [p[0] for p in scene]
    ys = [p[1] for p in scene]
    zs = [p[2] for p in scene]
    corners = [rr.project(x, y, z) for x in (min(xs), max(xs) + 1) for z in (min(zs), max(zs) + 1) for y in (min(ys), max(ys) + 3)]
    minx, maxx = min(c[0] for c in corners), max(c[0] for c in corners)
    miny, maxy = min(c[1] for c in corners), max(c[1] for c in corners)
    W, Hh = int(maxx - minx) + 40, int(maxy - miny) + 40
    ox, oy = -minx + 20, -miny + 20
    canvas = Image.new('RGBA', (W, Hh), (0, 0, 0, 0))
    order = sorted(scene.keys(), key=lambda p: (round(rr.depth(p[0], p[2]) * 1.4142 + p[1], 3), p[1]))
    cache = {}
    leaves = (rr.T.tint('oak_leaves', (95, 150, 60)),) * 3
    for p in order:
        name, props = scene[p]
        n = name.split(':')[1]
        if n == 'soul_campfire':
            rr.draw_box(canvas, *p, (1, 0, 1, 15, 4, 15), (rr.T.get('dark_oak_log'),) * 3, ox, oy, {}, False)
            rr.draw_box(canvas, *p, (3, 4, 3, 13, 12, 13), (rr.T.solid((90, 210, 230, 255)),) * 3, ox, oy, {}, False)
            continue
        if n in ('soul_lantern', 'lantern'):
            col = (110, 225, 235, 255) if n == 'soul_lantern' else (250, 190, 90, 255)
            if props.get('hanging') == 'true':
                rr.draw_box(canvas, *p, (7, 9, 7, 9, 16, 9), (rr.T.solid((60, 62, 70, 255)),) * 3, ox, oy, {}, False)
                rr.draw_box(canvas, *p, (5, 2, 5, 11, 9, 11), (rr.T.solid(col),) * 3, ox, oy, {}, False)
            else:
                rr.draw_box(canvas, *p, (5, 0, 5, 11, 7, 11), (rr.T.solid(col),) * 3, ox, oy, {}, False)
            continue
        key = (name, tuple(sorted(props.items())))
        if key not in cache:
            cache[key] = (rr.face_textures(name, props), rr.boxes(name, props))
        textures, bxs = cache[key]
        if n == 'oak_leaves':
            textures = leaves
        x, y, z = p
        cull = {}
        if rr.is_opaque_cube(scene[p]):
            cull = {'top': rr.is_opaque_cube(scene.get((x, y + 1, z))), 'east': rr.is_opaque_cube(scene.get((x + 1, y, z))),
                    'north': rr.is_opaque_cube(scene.get((x, y, z - 1)))}
        for box in bxs:
            if n.endswith('_log') or n.endswith('_pillar'):
                axis = props.get('axis', 'y')
                if axis == 'x':
                    rr.draw_box_axis(canvas, x, y, z, box, textures, ox, oy, cull, 'east')
                    continue
                if axis == 'z':
                    rr.draw_box_axis(canvas, x, y, z, box, textures, ox, oy, cull, 'north')
                    continue
            if n.endswith('_wall_banner'):
                col = {'red': (175, 35, 35), 'white': (240, 240, 235)}.get(n[: -len('_wall_banner')], (175, 35, 35))
                rr.draw_box(canvas, x, y, z, box, (rr.T.tint('white_wool', col),) * 3, ox, oy, cull, False)
                continue
            if n == 'water':
                rr.draw_box(canvas, x, y, z, box, (rr.T.solid((50, 110, 215, 255)),) * 3, ox, oy, cull, True)
                continue
            if n == 'lantern':
                rr.draw_box(canvas, x, y, z, box, (rr.T.solid((250, 190, 90, 255)),) * 3, ox, oy, cull, False)
                continue
            if n == 'chain':
                rr.draw_box(canvas, x, y, z, box, (rr.T.solid((60, 62, 70, 255)),) * 3, ox, oy, cull, False)
                continue
            rr.draw_box(canvas, x, y, z, box, textures, ox, oy, cull, n == 'iron_bars')
    return rr.finish(canvas)


def font(size):
    try:
        return ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf', size)
    except OSError:
        return ImageFont.load_default()


def main():
    rr.T = rr.Textures()
    os.makedirs(OUT, exist_ok=True)
    variants = sys.argv[1:] or ['1', '2', '3', '4', '5', 'war']
    for v in variants:
        road_and_bridge(v)
        a = render_scene()
        tunnel(v)
        b = render_scene()
        scale = 0.75
        a = a.resize((int(a.width * scale), int(a.height * scale)), Image.LANCZOS)
        b = b.resize((int(b.width * scale), int(b.height * scale)), Image.LANCZOS)
        H = max(a.height, b.height)
        sheet = Image.new('RGB', (a.width + b.width + 60, H + 110), (232, 238, 245))
        sheet.paste(a, (20, 90 + H - a.height))
        sheet.paste(b, (40 + a.width, 90 + H - b.height))
        d = ImageDraw.Draw(sheet)
        d.text((22, 12), TITLES[v], fill=(25, 30, 45), font=font(34))
        d.text((24, 56), SUBTITLES[v], fill=(70, 78, 95), font=font(20))
        path = os.path.join(OUT, 'concept-%s.png' % v)
        sheet.save(path)
        print(v, sheet.size, path)


if __name__ == '__main__':
    main()
