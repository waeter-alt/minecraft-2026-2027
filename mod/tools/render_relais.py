"""Isometric voxel renders of the five Relais commercial blueprints (NOT in-game screenshots).

Run from the repo root:  python -I -X utf8 mod/tools/render_relais.py
Needs `pip install nbtlib pillow numpy` and the vanilla 1.21.1 client jar from the Gradle cache (block textures are read
straight from it; nothing is copied into the repo). Writes screenshots/relais/relais_niveau1..5.png and relais_planche.png.
The camera looks from the north-east, so the door (north face) and the open side of the stable are visible.
"""
import glob
import io
import math
import os
import sys
import zipfile

import nbtlib
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), '..', '..')
BLUEPRINTS = os.path.join(ROOT, 'mod', 'src', 'main', 'resources', 'blueprints', 'interregne', 'interregne', 'commerce')
OUT = os.path.join(ROOT, 'screenshots', 'relais')
S = 22  # pixels per half block width
H = S * 1.6  # vertical pixels per block
FACE_SHADE = {'top': 1.0, 'east': 0.78, 'north': 0.60}

TITLES = {1: 'Niveau 1 - comptoir couvert', 2: 'Niveau 2 - halle, auvent et ecurie', 3: 'Niveau 3 - etage, volets et enseigne',
          4: 'Niveau 4 - cour, chariot et abreuvoir', 5: 'Niveau 5 - caravanserail'}


def find_jar():
    hits = glob.glob(os.path.expanduser('~/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar'))
    if not hits:
        sys.exit('vanilla client jar not found in the Gradle cache (run a Gradle build once)')
    return hits[0]


class Textures:
    def __init__(self):
        self.zip = zipfile.ZipFile(find_jar())
        self.cache = {}

    def get(self, name):
        if name not in self.cache:
            try:
                data = self.zip.read('assets/minecraft/textures/block/%s.png' % name)
                img = Image.open(io.BytesIO(data)).convert('RGBA')
                img = img.crop((0, 0, img.width, img.width))  # animated strips: first frame
                if img.width != 16:
                    img = img.resize((16, 16), Image.NEAREST)
            except KeyError:
                own = os.path.join(ROOT, 'mod', 'src', 'main', 'resources', 'assets', 'interregne', 'textures', 'block', name + '.png')
                if os.path.exists(own):
                    self.cache[name] = Image.open(own).convert('RGBA')
                    return self.cache[name]
                img = Image.new('RGBA', (16, 16), (255, 0, 255, 255))
                print('missing texture', name)
            self.cache[name] = img
        return self.cache[name]

    def tint(self, name, rgb):
        key = name + str(rgb)
        if key not in self.cache:
            arr = np.array(self.get(name)).astype(float)
            arr[..., :3] *= np.array(rgb) / 255.0
            self.cache[key] = Image.fromarray(arr.clip(0, 255).astype('uint8'), 'RGBA')
        return self.cache[key]

    def solid(self, rgba):
        key = 'solid' + str(rgba)
        if key not in self.cache:
            self.cache[key] = Image.new('RGBA', (16, 16), rgba)
        return self.cache[key]


T = None

MATERIAL = {
    'spruce': ('spruce_planks',) * 3, 'dark_oak': ('dark_oak_planks',) * 3, 'brick': ('bricks',) * 3,
    'stone_brick': ('stone_bricks',) * 3, 'quartz': ('quartz_block_top', 'quartz_block_side', 'quartz_block_bottom'),
    'cobblestone': ('cobblestone',) * 3, 'deepslate_tile': ('deepslate_tiles',) * 3, 'oak': ('oak_planks',) * 3,
}

SPECIAL = {
    'barrel': ('barrel_top', 'barrel_side', 'barrel_bottom'),
    'hay_block': ('hay_block_top', 'hay_block_side', 'hay_block_top'),
    'crafting_table': ('crafting_table_top', 'crafting_table_side', 'oak_planks'),
    'melon': ('melon_top', 'melon_side', 'melon_top'),
    'pumpkin': ('pumpkin_top', 'pumpkin_side', 'pumpkin_top'),
    'composter': ('composter_top', 'composter_side', 'composter_bottom'),
    'water_cauldron': ('water_still', 'cauldron_side', 'cauldron_bottom'),
    'campfire': ('campfire_log', 'campfire_log', 'campfire_log'),
    'lantern': ('lantern', 'lantern', 'lantern'),
    'chain': ('chain', 'chain', 'chain'),
    'bookshelf': ('oak_planks', 'bookshelf', 'oak_planks'),
    'red_carpet': ('red_wool',) * 3, 'white_carpet': ('white_wool',) * 3, 'blue_carpet': ('blue_wool',) * 3,
    'red_bed': ('red_wool', 'red_wool', 'oak_planks'), 'blue_bed': ('blue_wool', 'blue_wool', 'oak_planks'),
    'cartography_table': ('cartography_table_top', 'cartography_table_side1', 'dark_oak_planks'),
    'lectern': ('lectern_top', 'lectern_sides', 'lectern_base'), 'loom': ('loom_top', 'loom_side', 'loom_bottom'),
    'smoker': ('smoker_top', 'smoker_side', 'smoker_bottom'), 'furnace': ('furnace_top', 'furnace_side', 'furnace_top'),
    'grindstone': ('grindstone_round', 'grindstone_side', 'grindstone_round'), 'bell': ('gold_block',) * 3,
    'flower_pot': ('flower_pot',) * 3, 'polished_andesite': ('polished_andesite',) * 3,
}


def face_textures(name, props):
    """(top, side, bottom) images for a block name."""
    n = name.split(':')[1]
    for suffix in ('_stairs', '_slab', '_trapdoor', '_fence_gate', '_fence', '_wall'):
        if n.endswith(suffix):
            base = n[: -len(suffix)]
            if suffix == '_trapdoor':
                tex = (base + '_trapdoor',) * 3
            elif base in MATERIAL:
                tex = MATERIAL[base]
            else:
                tex = (base,) * 3
            return tuple(T.get(t) for t in tex)
    if n in SPECIAL:
        top, side, bottom = SPECIAL[n]
        return T.get(top), T.get(side), T.get(bottom)
    if n.endswith('_log'):
        return T.get(n + '_top'), T.get(n), T.get(n + '_top')
    if n.endswith('_wall_banner'):
        c = n[: -len('_wall_banner')]
        return (T.get(c + '_wool'),) * 3
    if n == 'relais_commercial':
        return T.get('relais_commercial_top'), T.get('relais_commercial_side'), T.get('relais_commercial_bottom')
    if n == 'glass_pane':
        return (T.get('glass'),) * 3
    if n == 'water':
        return (T.solid((40, 90, 200, 150)),) * 3
    if n == 'grass_block':
        return T.tint('grass_block_top', (125, 180, 80)), T.get('grass_block_side'), T.get('dirt')
    return (T.get(n),) * 3


def boxes(name, props):
    """Axis-aligned boxes (x0, y0, z0, x1, y1, z1 in 1/16 block) with a texture slot index: 0 top, 1 side, 2 bottom handled by the renderer."""
    n = name.split(':')[1]
    f = props.get('facing', 'north')
    if n.endswith('_stairs'):
        top = props.get('half') == 'top'
        base = (0, 8, 0, 16, 16, 16) if top else (0, 0, 0, 16, 8, 16)
        y0, y1 = (0, 8) if top else (8, 16)
        step = {'north': (0, y0, 8, 16, y1, 16), 'south': (0, y0, 0, 16, y1, 8), 'east': (0, y0, 0, 8, y1, 16), 'west': (8, y0, 0, 16, y1, 16)}[f]
        return [base, step]
    if n.endswith('_slab'):
        k = props.get('type', 'bottom')
        return [(0, 8, 0, 16, 16, 16) if k == 'top' else (0, 0, 0, 16, 16, 16) if k == 'double' else (0, 0, 0, 16, 8, 16)]
    if n.endswith('_trapdoor'):
        if props.get('open') == 'true':
            return [{'north': (0, 0, 13, 16, 16, 16), 'south': (0, 0, 0, 16, 16, 3), 'east': (0, 0, 0, 3, 16, 16), 'west': (13, 0, 0, 16, 16, 16)}[f]]
        return [(0, 13, 0, 16, 16, 16) if props.get('half') == 'top' else (0, 0, 0, 16, 3, 16)]
    arms = {'north': (7, 0, 0, 9, 0, 8), 'south': (7, 0, 8, 9, 0, 16), 'east': (8, 0, 7, 16, 0, 9), 'west': (0, 0, 7, 8, 0, 9)}
    if n.endswith('_fence'):
        out = [(6, 0, 6, 10, 16, 10)]
        for d, a in arms.items():
            if props.get(d) == 'true':
                for y0, y1 in ((6, 9), (12, 15)):
                    out.append((a[0], y0, a[2], a[3], y1, a[5]))
        return out
    if n.endswith('_fence_gate'):
        if f in ('north', 'south'):
            return [(0, 5, 7, 2, 16, 9), (14, 5, 7, 16, 16, 9), (2, 6, 7, 14, 9, 9), (2, 12, 7, 14, 15, 9)]
        return [(7, 5, 0, 9, 16, 2), (7, 5, 14, 9, 16, 16), (7, 6, 2, 9, 9, 14), (7, 12, 2, 9, 15, 14)]
    if n.endswith('_wall'):
        out = [(4, 0, 4, 12, 16 if props.get('up') == 'true' else 14, 12)]
        warm = {'north': (5, 0, 0, 11, 14, 8), 'south': (5, 0, 8, 11, 14, 16), 'east': (8, 0, 5, 16, 14, 11), 'west': (0, 0, 5, 8, 14, 11)}
        for d, b in warm.items():
            if props.get(d, 'none') != 'none':
                out.append(b)
        return out
    if n == 'glass_pane':
        out = [(7, 0, 7, 9, 16, 9)]
        for d, a in arms.items():
            if props.get(d) == 'true':
                out.append((a[0], 0, a[2], a[3], 16, a[5]))
        return out
    if n == 'lantern':
        return [(5, 1, 5, 11, 8, 11)] if props.get('hanging') == 'true' else [(5, 0, 5, 11, 7, 11)]
    if n == 'chain':
        return [(7, 0, 7, 9, 16, 9)]
    if n == 'ladder':
        return [{'north': (0, 0, 14, 16, 16, 16), 'south': (0, 0, 0, 16, 16, 2), 'east': (0, 0, 0, 2, 16, 16), 'west': (14, 0, 0, 16, 16, 16)}[f]]
    if n.endswith('_wall_banner'):
        return [{'north': (-2, -24, 14, 18, 16, 15), 'south': (-2, -24, 1, 18, 16, 2), 'east': (1, -24, -2, 2, 16, 18), 'west': (14, -24, -2, 15, 16, 18)}[f]]
    if n.endswith('_carpet'):
        return [(0, 0, 0, 16, 1, 16)]
    if n.endswith('_bed'):
        return [(0, 3, 0, 16, 9, 16)]
    if n == 'lectern':
        return [(0, 0, 0, 16, 12, 16)]
    if n == 'bell':
        return [(4, 3, 4, 12, 13, 12)]
    if n == 'flower_pot':
        return [(5, 0, 5, 11, 6, 11)]
    if n == 'water':
        return [(0, 0, 0, 16, 14, 16)]
    if n == 'campfire':
        return [(0, 0, 0, 16, 7, 16)]
    if n == 'water_cauldron':
        return [(0, 0, 0, 16, 14, 16)]
    return [(0, 0, 0, 16, 16, 16)]


YAW = math.radians(28)  # camera azimuth, from north towards east (45 would be the classic isometric corner view)
CX, CZ = math.sin(YAW), -math.cos(YAW)  # horizontal direction towards the camera
RX, RZ = -math.cos(YAW), -math.sin(YAW)  # screen right


def depth(x, z):
    return x * CX + z * CZ


def project(x, y, z):
    return ((x * RX + z * RZ) * S * 1.4142, depth(x, z) * S * 0.7071 - y * H)


def blit_face(canvas, tex, o, a, b, crop, shade, alpha_mult=1.0):
    """Paste `tex` (cropped) on the screen parallelogram o + u*a + v*b, u and v in 0..1."""
    cw, ch = max(crop[2] - crop[0], 1), max(crop[3] - crop[1], 1)
    sub = tex.crop((crop[0], crop[1], crop[0] + cw, crop[1] + ch))
    pts = [o, (o[0] + a[0], o[1] + a[1]), (o[0] + b[0], o[1] + b[1]), (o[0] + a[0] + b[0], o[1] + a[1] + b[1])]
    x0, x1 = int(min(p[0] for p in pts)) - 1, int(max(p[0] for p in pts)) + 2
    y0, y1 = int(min(p[1] for p in pts)) - 1, int(max(p[1] for p in pts)) + 2
    w, h = x1 - x0, y1 - y0
    if w <= 0 or h <= 0:
        return
    det = a[0] * b[1] - a[1] * b[0]
    if abs(det) < 1e-6:
        return
    # screen -> uv
    ia, ib = b[1] / det, -b[0] / det
    ic, id_ = -a[1] / det, a[0] / det
    # input pixel = (u*cw, v*ch) where u = ia*(X+x0-ox) + ib*(Y+y0-oy)
    ox, oy = o
    cu = lambda X, Y: ia * (X + x0 - ox) + ib * (Y + y0 - oy)
    cv = lambda X, Y: ic * (X + x0 - ox) + id_ * (Y + y0 - oy)
    coef = (ia * cw, ib * cw, (ia * (x0 - ox) + ib * (y0 - oy)) * cw,
            ic * ch, id_ * ch, (ic * (x0 - ox) + id_ * (y0 - oy)) * ch)
    out = sub.transform((w, h), Image.AFFINE, coef, resample=Image.NEAREST)
    # mask: the transform fills outside with transparent, but NEAREST bleeds on edges; clip with the uv square
    arr = np.array(out).astype(float)
    yy, xx = np.mgrid[0:h, 0:w]
    u = ia * (xx + x0 - ox) + ib * (yy + y0 - oy)
    v = ic * (xx + x0 - ox) + id_ * (yy + y0 - oy)
    inside = (u >= -0.06) & (u < 1.06) & (v >= -0.06) & (v < 1.06)
    arr[..., :3] *= shade
    arr[..., 3] *= inside * alpha_mult
    out = Image.fromarray(arr.clip(0, 255).astype('uint8'), 'RGBA')
    canvas.alpha_composite(out, (x0, y0)) if x0 >= 0 and y0 >= 0 and x1 <= canvas.width and y1 <= canvas.height else _safe_paste(canvas, out, x0, y0)


def _safe_paste(canvas, img, x0, y0):
    cx0, cy0 = max(x0, 0), max(y0, 0)
    cx1, cy1 = min(x0 + img.width, canvas.width), min(y0 + img.height, canvas.height)
    if cx1 <= cx0 or cy1 <= cy0:
        return
    canvas.alpha_composite(img.crop((cx0 - x0, cy0 - y0, cx1 - x0, cy1 - y0)), (cx0, cy0))


def draw_box(canvas, bx, by, bz, box, textures, ox, oy, cull, translucent, shade_extra=1.0):
    x0, y0, z0, x1, y1, z1 = box
    # global corner coordinates in blocks
    gx0, gx1 = bx + x0 / 16, bx + x1 / 16
    gy0, gy1 = by + y0 / 16, by + y1 / 16
    gz0, gz1 = bz + z0 / 16, bz + z1 / 16
    top, side, bottom = textures
    alpha = 0.55 if translucent else 1.0

    def P(x, y, z):
        px, py = project(x, y, z)
        return (px + ox, py + oy)

    def span(p, q):
        return (q[0] - p[0], q[1] - p[1])

    # top face: u along +x, v along +z
    if not cull.get('top'):
        o = P(gx0, gy1, gz0)
        blit_face(canvas, top, o, span(o, P(gx1, gy1, gz0)), span(o, P(gx0, gy1, gz1)), (x0, z0, x1, z1), FACE_SHADE['top'] * shade_extra, alpha)
    # east face (+x): u runs from +z to -z, v from the top down
    if not cull.get('east'):
        o = P(gx1, gy1, gz1)
        blit_face(canvas, side, o, span(o, P(gx1, gy1, gz0)), span(o, P(gx1, gy0, gz1)), (16 - z1, 16 - y1, 16 - z0, 16 - y0), FACE_SHADE['east'] * shade_extra, alpha)
    # north face (-z): u runs from +x to -x
    if not cull.get('north'):
        o = P(gx1, gy1, gz0)
        blit_face(canvas, side, o, span(o, P(gx0, gy1, gz0)), span(o, P(gx1, gy0, gz0)), (16 - x1, 16 - y1, 16 - x0, 16 - y0), FACE_SHADE['north'] * shade_extra, alpha)


def load(level):
    root = nbtlib.load(os.path.join(BLUEPRINTS, 'relais%d.blueprint' % level))
    sx, sy, sz = int(root['size_x']), int(root['size_y']), int(root['size_z'])
    palette = []
    for e in root['palette']:
        props = {str(k): str(v) for k, v in e['Properties'].items()} if 'Properties' in e else {}
        palette.append((str(e['Name']), props))
    idx = []
    for v in root['blocks']:
        v = int(v) & 0xffffffff
        idx.append(v >> 16)
        idx.append(v & 0xffff)
    off = root['optional_data']['structurize']['primary_offset']
    blocks = {}
    for y in range(sy):
        for z in range(sz):
            for x in range(sx):
                i = idx[(y * sz + z) * sx + x]
                if i:
                    blocks[(x, y, z)] = palette[i]
    return blocks, (sx, sy, sz), (int(off['x']), int(off['y']), int(off['z']))


FULL_OPAQUE_SKIP = ('_carpet', '_bed', 'lectern', 'bell', 'flower_pot', 'ladder', '_stairs', '_slab', '_trapdoor', '_fence', '_wall', 'glass_pane', 'lantern', 'chain', 'banner', 'water', 'campfire', 'cauldron')


def is_opaque_cube(entry):
    if not entry:
        return False
    n = entry[0].split(':')[1]
    return not any(k in n for k in FULL_OPAQUE_SKIP) and n != 'relais_commercial'


def render_level(level, margin=3):
    blocks, (sx, sy, sz), anchor = load(level)
    cut = int(os.environ.get('CUT', '999'))  # CUT=n: leave out everything from height n up, to look inside
    scene = {p: e for p, e in blocks.items() if p[1] < cut}
    # a flat grass lawn under and around the building (not part of the blueprint)
    for x in range(-margin, sx + margin):
        for z in range(-margin, sz + margin):
            scene[(x, -1, z)] = ('minecraft:grass_block', {})
    # canvas size from the projected extent
    corners = [project(x, y, z) for x in (-margin, sx + margin) for z in (-margin, sz + margin) for y in (-1, sy + 3)]
    minx, maxx = min(c[0] for c in corners), max(c[0] for c in corners)
    miny, maxy = min(c[1] for c in corners), max(c[1] for c in corners)
    W, Hh = int(maxx - minx) + 40, int(maxy - miny) + 40
    ox, oy = -minx + 20, -miny + 20
    canvas = Image.new('RGBA', (W, Hh), (0, 0, 0, 0))
    # opaque blocks in painter order, then translucent (water, glass) on top
    order = sorted(scene.keys(), key=lambda p: (round(depth(p[0], p[2]) * 1.4142 + p[1], 3), p[1]))
    cache = {}
    for pass_ in (0,):
        for p in order:
            entry = scene[p]
            name, props = entry
            n = name.split(':')[1]
            translucent = n in ('glass_pane', 'water')
            key = (name, tuple(sorted(props.items())))
            if key not in cache:
                cache[key] = (face_textures(name, props), boxes(name, props))
            textures, bxs = cache[key]
            x, y, z = p
            cull = {}
            if is_opaque_cube(entry):
                cull = {'top': is_opaque_cube(scene.get((x, y + 1, z))), 'east': is_opaque_cube(scene.get((x + 1, y, z))),
                        'north': is_opaque_cube(scene.get((x, y, z - 1)))}
            for box in bxs:
                tex = textures
                if n.endswith('_log') or n == 'hay_block':
                    axis = props.get('axis', 'y')
                    top_img, side_img, _ = textures
                    # axis x: the +x face shows the end grain; axis z: the -z face
                    tex = (side_img if axis != 'y' else top_img, side_img, side_img)
                    if axis == 'x':
                        draw_box_axis(canvas, x, y, z, box, textures, ox, oy, cull, 'east')
                        continue
                    if axis == 'z':
                        draw_box_axis(canvas, x, y, z, box, textures, ox, oy, cull, 'north')
                        continue
                if n.endswith('_wall_banner'):
                    bn = n[: -len('_wall_banner')]
                    banner = tuple(T.tint('white_wool', {'red': (190, 40, 40), 'white': (240, 240, 240)}.get(bn, (190, 40, 40))) for _ in range(3))
                    draw_box(canvas, x, y, z, box, banner, ox, oy, cull, False)
                    continue
                if n == 'water':
                    draw_box(canvas, x, y, z, box, (T.solid((50, 110, 215, 255)),) * 3, ox, oy, cull, True)
                    continue
                if n == 'lantern':
                    draw_box(canvas, x, y, z, box, (T.solid((250, 190, 90, 255)),) * 3, ox, oy, cull, False)
                    continue
                if n == 'chain':
                    draw_box(canvas, x, y, z, box, (T.solid((60, 62, 70, 255)),) * 3, ox, oy, cull, False)
                    continue
                draw_box(canvas, x, y, z, box, tex, ox, oy, cull, translucent)
    return canvas, anchor


def draw_box_axis(canvas, x, y, z, box, textures, ox, oy, cull, end_face):
    """Logs and hay laid on the x or z axis: end grain on the matching visible face, bark elsewhere."""
    top_img, side_img, _ = textures
    # draw as bark everywhere first, then overpaint the end face
    draw_box(canvas, x, y, z, box, (side_img, side_img, side_img), ox, oy, {**cull, end_face: True}, False)
    x0, y0, z0, x1, y1, z1 = box

    def P(a, b, c):
        px, py = project(a, b, c)
        return (px + ox, py + oy)

    gx1, gz0 = x + x1 / 16, z + z0 / 16
    if end_face == 'east' and not cull.get('east'):
        o = P(gx1, y + y1 / 16, z + z1 / 16)
        a = (P(gx1, y + y1 / 16, z + z0 / 16)[0] - o[0], P(gx1, y + y1 / 16, z + z0 / 16)[1] - o[1])
        b = (P(gx1, y + y0 / 16, z + z1 / 16)[0] - o[0], P(gx1, y + y0 / 16, z + z1 / 16)[1] - o[1])
        blit_face(canvas, top_img, o, a, b, (0, 0, 16, 16), FACE_SHADE['east'], 1.0)
    if end_face == 'north' and not cull.get('north'):
        o = P(x + x1 / 16, y + y1 / 16, gz0)
        a = (P(x + x0 / 16, y + y1 / 16, gz0)[0] - o[0], P(x + x0 / 16, y + y1 / 16, gz0)[1] - o[1])
        b = (P(x + x1 / 16, y + y0 / 16, gz0)[0] - o[0], P(x + x1 / 16, y + y0 / 16, gz0)[1] - o[1])
        blit_face(canvas, top_img, o, a, b, (0, 0, 16, 16), FACE_SHADE['north'], 1.0)


def finish(canvas):
    """Sky gradient behind the cut-out render, cropped to the content."""
    bbox = canvas.getbbox()
    pad = 24
    box = (max(bbox[0] - pad, 0), max(bbox[1] - pad, 0), min(bbox[2] + pad, canvas.width), min(bbox[3] + pad, canvas.height))
    canvas = canvas.crop(box)
    w, h = canvas.size
    grad = np.zeros((h, w, 4), dtype='uint8')
    top, bot = np.array([150, 190, 235]), np.array([222, 232, 242])
    for yy in range(h):
        t = yy / max(h - 1, 1)
        grad[yy, :, :3] = (top * (1 - t) + bot * t).astype('uint8')
    grad[..., 3] = 255
    bg = Image.fromarray(grad, 'RGBA')
    bg.alpha_composite(canvas)
    band = Image.new('RGB', (w, 50), (150, 190, 235))
    out = Image.new('RGB', (w, h + 50))
    out.paste(band, (0, 0))
    out.paste(bg.convert('RGB'), (0, 50))
    return out


def label(img, text, size=26):
    try:
        font = ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf', size)
    except OSError:
        font = ImageFont.load_default()
    d = ImageDraw.Draw(img)
    d.text((14, 10), text, fill=(25, 30, 45), font=font)
    return img


def main():
    global T
    T = Textures()
    os.makedirs(OUT, exist_ok=True)
    levels = [int(a) for a in sys.argv[1:]] or [1, 2, 3, 4, 5]
    images = {}
    for lvl in levels:
        canvas, _ = render_level(lvl)
        img = finish(canvas)
        images[lvl] = img
        label(img.copy(), TITLES[lvl]).save(os.path.join(OUT, 'relais_niveau%d%s.png' % (lvl, '_coupe' if os.environ.get('CUT') else '')))
        print('level', lvl, img.size)
    if len(levels) == 5:
        height = max(i.height for i in images.values())
        widths = sum(i.width for i in images.values())
        scale = 0.62
        sheet = Image.new('RGB', (int((widths) * scale) + 30 * 6, int(height * scale) + 70), (232, 238, 245))
        x = 30
        for lvl in range(1, 6):
            im = images[lvl].resize((int(images[lvl].width * scale), int(images[lvl].height * scale)), Image.LANCZOS)
            sheet.paste(im, (x, 60 + (int(height * scale) - im.height)))
            d = ImageDraw.Draw(sheet)
            try:
                font = ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf', 22)
            except OSError:
                font = ImageFont.load_default()
            d.text((x + 6, 18), TITLES[lvl], fill=(25, 30, 45), font=font)
            x += im.width + 30
        sheet.save(os.path.join(OUT, 'relais_planche.png'))
        print('sheet', sheet.size)


if __name__ == '__main__':
    main()
