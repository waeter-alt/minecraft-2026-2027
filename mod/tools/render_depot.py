"""Isometric voxel renders of the five Dépôt Régional blueprints (NOT in-game screenshots).

Run from the repo root:  python -I -X utf8 mod/tools/render_depot.py [levels...]
Same renderer as the Relais (render_relais.py), taught the Create blocks: textures come from the vanilla client jar and
from the Create jar in mod/libs. The parts the mod places itself (hidden motor, belts, windmill: depot_decor.json) are
drawn too, so the picture is the finished building. Writes screenshots/depot/depot_niveau1..5.png and depot_planche.png.
"""
import io
import json
import os
import shutil
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbtlib  # noqa: E402
from PIL import Image  # noqa: E402

import render_relais as RR  # noqa: E402

ROOT = RR.ROOT
RR.BLUEPRINTS = os.path.join(ROOT, 'mod', 'src', 'main', 'resources', 'blueprints', 'interregne', 'interregne', 'regions')
RR.OUT = os.path.join(ROOT, 'screenshots', 'depot')
RR.TITLES = {1: 'Niveau 1 - hangar a caisses', 2: 'Niveau 2 - atelier et convoyeur', 3: 'Niveau 3 - manufacture et moulin',
             4: 'Niveau 4 - usine a deux etages', 5: 'Niveau 5 - usine a vapeur'}
DECOR = os.path.join(ROOT, 'mod', 'src', 'main', 'resources', 'data', 'interregne', 'depot_decor.json')
CREATE_JAR = os.path.join(ROOT, 'mod', 'libs', 'create-1.21.1-6.0.9.jar')

_create = zipfile.ZipFile(CREATE_JAR)
_vanilla_get = RR.Textures.get


def _get(self, name):
    if name not in self.cache:
        try:
            data = _create.read('assets/create/textures/block/%s.png' % name)
            img = Image.open(io.BytesIO(data)).convert('RGBA')
            img = img.crop((0, 0, img.width, img.width))
            self.cache[name] = img.resize((16, 16), Image.NEAREST) if img.width != 16 else img
            return self.cache[name]
        except KeyError:
            pass
    return _vanilla_get(self, name)


RR.Textures.get = _get

GREY = (138, 138, 132, 255)
IRON = (96, 100, 108, 255)
GOLD = (214, 170, 64, 255)
BELT = (52, 52, 60, 255)
SAIL = (236, 232, 222, 255)

_face = RR.face_textures
_boxes = RR.boxes


def face_textures(name, props):
    ns, n = name.split(':')
    if ns == 'interregne' and n == 'depot_regional':
        return RR.T.get('depot_regional_top'), RR.T.get('depot_regional_side'), RR.T.get('depot_regional_bottom')
    if ns == 'create':
        solid = {'shaft': GREY, 'cogwheel': GREY, 'large_cogwheel': GREY, 'flywheel': GOLD, 'belt': BELT, 'creative_motor': IRON,
                 'white_sail': SAIL, 'sail_frame': (150, 110, 70, 255), 'mechanical_arm': GOLD, 'metal_girder': IRON, 'fluid_pipe': (190, 120, 70, 255),
                 'steam_whistle': GOLD, 'depot': GREY, 'chute': GREY, 'steam_engine': GOLD, 'encased_fan': GREY, 'windmill_bearing': GREY}
        if n in solid:
            return (RR.T.solid(solid[n]),) * 3
        if n == 'fluid_tank':
            return RR.T.get('fluid_tank_top'), RR.T.get('fluid_tank'), RR.T.get('fluid_tank_top')
        return (RR.T.get(n),) * 3
    return _face(name, props)


def boxes(name, props):
    ns, n = name.split(':')
    if ns != 'create':
        return _boxes(name, props)
    a = props.get('axis', 'y')
    if n == 'shaft':
        return [{'x': (0, 6, 6, 16, 10, 10), 'y': (6, 0, 6, 10, 16, 10), 'z': (6, 6, 0, 10, 10, 16)}[a]]
    if n in ('cogwheel', 'large_cogwheel', 'flywheel'):
        big = n != 'cogwheel'
        lo, hi = (0, 16) if big else (1, 15)
        thick = {'x': (6, lo, lo, 10, hi, hi), 'y': (lo, 6, lo, hi, 10, hi), 'z': (lo, lo, 6, hi, hi, 10)}[a]
        core = {'x': (0, 6, 6, 16, 10, 10), 'y': (6, 0, 6, 10, 16, 10), 'z': (6, 6, 0, 10, 10, 16)}[a]
        return [core, thick]
    if n == 'belt':
        return [(0, 4, 0, 16, 9, 16)]
    if n in ('depot',):
        return [(1, 0, 1, 15, 4, 15)]
    if n == 'fluid_pipe':
        return [(5, 5, 5, 11, 11, 11)] + [b for d, b in {'north': (5, 5, 0, 11, 11, 5), 'south': (5, 5, 11, 11, 11, 16), 'west': (0, 5, 5, 5, 11, 11),
                                                          'east': (11, 5, 5, 16, 11, 11), 'up': (5, 11, 5, 11, 16, 11), 'down': (5, 0, 5, 11, 5, 11)}.items() if props.get(d) == 'true']
    if n == 'metal_girder':
        return [(0, 12, 4, 16, 16, 12)] if props.get('x') == 'true' else [(4, 12, 0, 12, 16, 16)]
    if n in ('mechanical_arm',):
        return [(4, 8, 4, 12, 16, 12), (7, 0, 7, 9, 8, 9)]
    if n == 'white_sail':
        return [{'east': (6, 0, 0, 10, 16, 16), 'west': (6, 0, 0, 10, 16, 16), 'north': (0, 0, 6, 16, 16, 10), 'south': (0, 0, 6, 16, 16, 10)}.get(props.get('facing', 'east'), (0, 0, 0, 16, 16, 16))]
    if n == 'sail_frame':
        return [(5, 5, 5, 11, 11, 11)]
    if n == 'chute':
        return [(3, 0, 3, 13, 16, 13)]
    if n == 'steam_whistle':
        return [(4, 0, 4, 12, 12, 12)]
    return [(0, 0, 0, 16, 16, 16)]


RR.face_textures = face_textures
RR.boxes = boxes


def load(level):
    root = nbtlib.load(os.path.join(RR.BLUEPRINTS, 'depot%d.blueprint' % level))
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
    hx, hy, hz = int(off['x']), int(off['y']), int(off['z'])
    blocks = {}
    for y in range(sy):
        for z in range(sz):
            for x in range(sx):
                i = idx[(y * sz + z) * sx + x]
                if i:
                    blocks[(x, y, z)] = palette[i]
    with open(DECOR, encoding='utf8') as f:
        decor = json.load(f)[str(level)]
    for part in decor:
        # decor is hut-relative with y counted from the hut, whose cell is y=1 in the grid: hut at (hx, hy, hz)
        dx, dy, dz = part['pos']
        key = (hx + dx, hy + dy, hz + dz)
        if 0 <= key[0] < sx and 0 <= key[1] < sy and 0 <= key[2] < sz:
            blocks[key] = (part['block'], dict(part['props']))
    return blocks, (sx, sy, sz), (hx, hy, hz)


RR.load = load

if __name__ == '__main__':
    RR.main()
    os.makedirs(RR.OUT, exist_ok=True)
    for f in os.listdir(RR.OUT):
        if f.startswith('relais_'):
            target = os.path.join(RR.OUT, 'depot_' + f[len('relais_'):])
            if os.path.exists(target):
                os.remove(target)
            shutil.move(os.path.join(RR.OUT, f), target)
