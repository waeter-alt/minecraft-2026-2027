"""Isometric voxel renders of the five Banque régionale blueprints with their vault filled (NOT in-game screenshots).

Run from the repo root:  python -I -X utf8 mod/tools/render_bank.py [fill 0..1, default 1.0] [levels...]
Same renderer as the Relais (render_relais.py). The hoard blocks are drawn as simple coloured boxes (their real models are
in tools/gen_vault_assets.py); what the picture shows is where the vault holds what, and how full it is.
Writes screenshots/banque/banque_niveau1..5.png and banque_planche.png (or banque_niveauN_<fill>.png for a partial fill).
"""
import json
import os
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbtlib  # noqa: E402

import render_relais as RR  # noqa: E402

ROOT = RR.ROOT
RR.BLUEPRINTS = os.path.join(ROOT, 'mod', 'src', 'main', 'resources', 'blueprints', 'interregne', 'interregne', 'regions')
RR.OUT = os.path.join(ROOT, 'screenshots', 'banque')
RR.TITLES = {1: 'Niveau 1 - comptoir de change', 2: 'Niveau 2 - banque de pierre', 3: 'Niveau 3 - banque a etage', 4: 'Niveau 4 - hotel des finances',
             5: 'Niveau 5 - forteresse du tresor'}
VAULT = os.path.join(ROOT, 'mod', 'src', 'main', 'resources', 'data', 'interregne', 'bank_vault.json')

FILL = 1.0
COLOURS = {'pile': (240, 190, 50, 255), 'ingots': (250, 205, 70, 255), 'chest': (125, 85, 48, 255), 'gems': (60, 210, 130, 255), 'jewels': (200, 40, 70, 255)}
HEIGHT = {'pile': (2, 4, 6, 9, 11, 14), 'ingots': (4, 6, 9, 11, 12, 13), 'chest': (9, 10, 11, 12, 13, 14), 'gems': (5, 6, 7, 8, 9, 10), 'jewels': (4, 5, 6, 8, 9, 10)}

_face, _boxes = RR.face_textures, RR.boxes


def face_textures(name, props):
    if name.startswith('hoard:'):
        return (RR.T.solid(COLOURS[name[6:]]),) * 3
    if name == 'minecraft:quartz_block':
        return RR.T.get('quartz_block_top'), RR.T.get('quartz_block_side'), RR.T.get('quartz_block_bottom')
    if name == 'minecraft:iron_door':
        return (RR.T.get('iron_door_bottom'),) * 3
    if name == 'interregne:banque_regionale':
        return RR.T.get('banque_regionale_top'), RR.T.get('banque_regionale'), RR.T.get('banque_regionale')
    return _face(name, props)


def boxes(name, props):
    if name.startswith('hoard:'):
        h = HEIGHT[name[6:]][int(props['stage']) - 1]
        return [(2, 0, 2, 14, h, 14)]
    return _boxes(name, props)


RR.face_textures, RR.boxes = face_textures, boxes


def load(level):
    root = nbtlib.load(os.path.join(RR.BLUEPRINTS, 'banque%d.blueprint' % level))
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
    with open(VAULT, encoding='utf8') as f:
        slots = json.load(f)[str(level)]
    for s in slots:
        if FILL > 0 and FILL >= s['from']:
            stage = max(1, min(6, int(-(-(FILL - s['from']) / s['span'] * 6 // 1))))
            dx, dy, dz = s['pos']
            blocks[(hx + dx, hy + dy, hz + dz)] = ('hoard:' + s['kind'], {'stage': str(stage)})
    return blocks, (sx, sy, sz), (hx, hy, hz)


RR.load = load

if __name__ == '__main__':
    args = sys.argv[1:]
    if args and '.' in args[0]:
        FILL = float(args.pop(0))
    sys.argv = [sys.argv[0]] + args
    RR.main()
    suffix = '' if FILL >= 1 else '_%d' % round(FILL * 100)
    for f in os.listdir(RR.OUT):
        if f.startswith('relais_'):
            target = os.path.join(RR.OUT, 'banque_' + f[len('relais_'):].replace('.png', suffix + '.png'))
            if os.path.exists(target):
                os.remove(target)
            shutil.move(os.path.join(RR.OUT, f), target)
