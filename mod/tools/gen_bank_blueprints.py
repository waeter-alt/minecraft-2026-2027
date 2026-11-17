"""Generates the five Banque régionale blueprints (one pack, shared by every colony style) and their vault layout.

Run from the repo root:  python -I -X utf8 mod/tools/gen_bank_blueprints.py
Needs `pip install nbtlib`. The output is committed, so this is only needed to change the building.
`mod/tools/render_bank.py` draws the result as isometric PNGs.

The building is a bank with a vault room behind its counter hall, vanilla blocks only (the pack is shared by every style):
  1  counter house: a grille, a small vault of seven by four
  2  stone bank: pillared hall, a vault with pedestals for jewels
  3  two storeys, gold sign, a glass-arcade vault wall that shows its hoard from the street
  4  town-house bank: wings, balcony, a big two-row vault
  5  treasury fortress: towers, a gatehouse, a vault the size of a ballroom
The vault room is left empty in the blueprint. Its slots (floor piles, gold bars, open chests, gem clusters, jewel
cushions on pedestals) are listed in `data/interregne/bank_vault.json`, hut-relative and north-facing, and the mod sets
the custom hoard blocks into them as the treasury fills (see Vault.java): a slot shows from `from` (fraction of the
capacity) and reaches its last stage `span` later.

Same frame as the Relais: x east, y up, z south, hut at the origin on every level (y=1), floor y=0, door on the north face.
"""
import json
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbtlib  # noqa: E402
from nbtlib import Compound, List, String, Int, Short, Byte, IntArray  # noqa: E402

import gen_relais_blueprints as R  # noqa: E402

RES = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources')
CAT = os.path.join(RES, 'blueprints', 'interregne', 'interregne', 'regions')
VAULT = os.path.join(RES, 'data', 'interregne', 'bank_vault.json')

M = 'minecraft:'
STONE = M + 'stone_bricks'
POLISHED = M + 'polished_andesite'
POLISHED_B = M + 'polished_blackstone_bricks'
QUARTZ = M + 'quartz_block'
PILLAR = M + 'quartz_pillar'
GOLD = M + 'gold_block'
DARK = M + 'dark_oak_planks'
FLOOR = M + 'smooth_stone'
VAULT_FLOOR = M + 'polished_blackstone_bricks'
GLASS = M + 'glass'
VAULT_DOOR = M + 'waxed_copper_door'
R.SOLID.update({STONE, POLISHED, POLISHED_B, QUARTZ, PILLAR, GOLD, DARK, VAULT_FLOOR, GLASS})


class BankGrid(R.Grid):
    def __init__(self):
        super().__init__()
        self.slots = []

    def vault_door(self, x, z, facing):
        # copper, not iron: an iron door cannot be opened by hand, and its thin leaf looks stuck on in a wall of bars
        self.put(x, 1, z, VAULT_DOOR, facing=facing, half='lower', hinge='left', open='false', powered='false')
        self.put(x, 2, z, VAULT_DOOR, facing=facing, half='upper', hinge='left', open='false', powered='false')

    def slot(self, x, y, z, kind, facing='north'):
        self.slots.append({'x': x, 'y': y, 'z': z, 'kind': kind, 'facing': facing})


def hash2(x, z):
    return ((x * 73856093) ^ (z * 19349663)) & 0xffff


def vault_slots(g, x0, x1, z0, z1, seed, density=1.0, pedestals=True):
    """Fill a vault room (interior x0..x1, z0..z1) with slots. A one-block lane at x=0 stays free to walk to the back."""
    rnd = random.Random(seed)
    cells = []
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x == 0:
                continue
            cells.append((x, z))
    out = []
    for x, z in cells:
        edge_back, edge_w, edge_e = z == z1, x == x0, x == x1
        ped = pedestals and not (edge_back or edge_w or edge_e) and (x + z) % 3 == 0 and z != z0
        if ped:
            g.put(x, 1, z, PILLAR, axis='y')
            out.append((x, 2, z, rnd.choice(('jewels', 'gems', 'jewels')), 'north'))
        elif edge_back:
            out.append((x, 1, z, 'chest', 'north'))
        elif edge_w:
            out.append((x, 1, z, 'chest' if rnd.random() < 0.55 else rnd.choice(('ingots', 'pile')), 'east'))
        elif edge_e:
            out.append((x, 1, z, 'chest' if rnd.random() < 0.55 else rnd.choice(('ingots', 'pile')), 'west'))
        else:
            out.append((x, 1, z, rnd.choice(('pile', 'pile', 'ingots', 'gems' if rnd.random() < 0.3 else 'pile')), 'north'))
    rnd.shuffle(out)
    keep = max(6, int(len(out) * density))
    out = out[:keep]
    # what appears first: the nearest to the entrance lane, so the vault fills from the door to the back wall
    out.sort(key=lambda s: abs(s[0]) * 0.8 + (s[2] - z0) * 0.7 + rnd.random() * 3)
    for i, (x, y, z, kind, facing) in enumerate(out):
        start = 0.0 if i == 0 else round(0.88 * i / len(out), 3)
        span = round(max(0.14, min(0.4, 1 - start)), 3)
        g.slot(x, y, z, kind, facing)
        g.slots[-1]['from'] = start
        g.slots[-1]['span'] = span


def bank(g, p):
    x0, x1 = -p['hw'], p['hw']
    z0, zd, z1 = -p['hd'], p['div'], p['div'] + p['vd'] + 1  # north wall, divider, south wall
    top = p['top']
    g.fill(x0 - 1, x1 + 1, 0, 0, z0 - 1, z1 + 1, FLOOR)
    g.fill(x0, x1, 0, 0, zd, z1, VAULT_FLOOR)
    # walls: stone, polished pillars, a quartz band under the roof
    g.ring(x0, x1, z0, z1, 1, top, STONE)
    for x in range(x0, x1 + 1, 3):
        for z in (z0, z1):
            g.fill(x, x, 1, top, z, z, POLISHED, )
    for z in range(z0, z1 + 1, 3):
        for x in (x0, x1):
            g.fill(x, x, 1, top, z, z, POLISHED)
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        g.fill(x, x, 1, top, z, z, POLISHED)
    g.ring(x0, x1, z0, z1, top, top, QUARTZ)
    g.ring(x0, x1, z0, z1, 1, 1, POLISHED_B)
    # the vault: a solid stone room. The street side (east) is an arcade of full glass blocks between the wall pillars, so the
    # hoard shows from outside without any thin bars; the front wall has two glass teller windows and a copper door
    for z in range(zd + 1, z1):
        if z not in range(z0, z1 + 1, 3):
            for y in range(2, max(3, top - 1)):
                g.put(x1, y, z, GLASS)
    for x in range(x0 + 1, x1):
        for y in range(1, top):
            g.put(x, y, zd, STONE if y > 1 else POLISHED_B)
    for x in (-3, -2, 2, 3):
        if x0 + 1 < x < x1 - 1:
            for y in (2, 3):
                g.put(x, y, zd, GLASS)
    for x in (-1, 1):
        g.fill(x, x, 1, top - 1, zd, zd, PILLAR, axis='y')
    g.carve(0, 0, 1, 3, zd, zd)
    g.vault_door(0, zd, 'north')
    g.put(0, 3, zd, GOLD)
    # windows of the hall
    for z in range(z0 + 2, zd - 1, 3):
        for x in (x0, x1):
            g.carve(x, x, 2, 3, z, z)
            g.pane(x, 2, z)
            g.pane(x, 3, z)
    # entrance, a gold plaque over it and two lamps
    g.carve(-1, 1, 1, 3, z0, z0)
    if top >= 5:
        g.fill(-1, 1, 4, 4, z0, z0, GOLD)
    for x in (-2, 2):
        g.lantern(x, 3, z0 - 1, hanging=False)
        g.put(x, 2, z0 - 1, M + 'dark_oak_fence', north='false', south='false', east='false', west='false', waterlogged='false')
    # the counter, with a glass teller screen and a gold edge
    for x in range(x0 + 2, x1 - 1):
        if abs(x) > 1:
            g.put(x, 1, zd - 2, M + 'dark_oak_planks')
            g.pane(x, 2, zd - 2)
    g.put(x1 - 1, 1, zd - 2, GOLD)
    g.put(x0 + 1, 1, zd - 2, GOLD)
    g.put(0, 1, 0, 'interregne:banque_regionale', facing='north')
    g.gable('x', x0, x1, z0, z1, top + 1)
    if p.get('pillars'):
        for z in range(z0 + 2, zd - 1, 3):
            for x in (x0 + 2, x1 - 2):
                g.fill(x, x, 1, top - 1, z, z, PILLAR, axis='y')
    if p.get('towers'):
        for x in (x0 - 1, x1 + 1):
            for z in (z0 - 1, z1 + 1):
                g.fill(x - 1, x + 1, 1, top + 3, z - 1, z + 1, STONE)
                for dx in (-1, 0, 1):
                    for dz in (-1, 0, 1):
                        if (dx + dz) % 2 == 0:
                            g.put(x + dx, top + 4, z + dz, M + 'stone_brick_wall', north='none', south='none', east='none', west='none', up='true', waterlogged='false')
                g.put(x, top + 4, z, M + 'lantern', hanging='false', waterlogged='false')
    if p.get('wings'):
        for sx in (-1, 1):
            xa = sx * (p['hw'] + 1)
            g.fill(min(xa, xa + sx * 3), max(xa, xa + sx * 3), 1, top - 1, z0 + 1, z0 + 5, STONE)
            g.carve(min(xa, xa + sx * 3) + (1 if sx > 0 else 0), max(xa, xa + sx * 3) - (1 if sx < 0 else 0), 1, top - 2, z0 + 2, z0 + 4)
    if p.get('upper'):
        g.fill(x0 + 1, x1 - 1, top - 3, top - 3, z0 + 1, zd - 1, M + 'spruce_planks')
        g.carve(-1, 1, top - 3, top - 3, z0 + 1, z0 + 3)
        for z in range(z0 + 2, zd - 1, 3):
            for x in (x0, x1):
                g.carve(x, x, top - 2, top - 1, z, z)
                g.pane(x, top - 2, z)
                g.pane(x, top - 1, z)
    g.lantern(0, top - 1, zd - 3, hanging=True)
    vault_slots(g, x0 + 1, x1 - 1, zd + 1, z1 - 1, p['seed'], p.get('density', 1.0), p.get('pedestals', True))
    R.apron(g, x0 - 3, x1 + 3, z0 - 4, z1 + 3)


PARAMS = {
    1: dict(hw=4, hd=4, div=3, vd=4, top=4, seed=11, density=0.55, pedestals=False),
    2: dict(hw=5, hd=5, div=4, vd=5, top=5, seed=22, density=0.6, pillars=True),
    3: dict(hw=6, hd=6, div=5, vd=6, top=7, seed=33, density=0.6, pillars=True, upper=True),
    4: dict(hw=7, hd=7, div=6, vd=7, top=7, seed=44, density=0.62, pillars=True, upper=True, wings=True),
    5: dict(hw=8, hd=8, div=7, vd=8, top=8, seed=55, density=0.65, pillars=True, upper=True, towers=True),
}


def level(n):
    def f(g):
        bank(g, PARAMS[n])
    return f


LEVELS = {n: level(n) for n in PARAMS}


def build(level_n):
    g = BankGrid()
    LEVELS[level_n](g)
    R.connect(g)
    xs, ys, zs = [k[0] for k in g.d], [k[1] for k in g.d], [k[2] for k in g.d]
    x0, x1, y0, y1, z0, z1 = min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)
    sx, sy, sz = x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1
    hx, hy, hz = -x0, -y0 + 1, -z0
    pal = R.Palette()
    structure = [0] * (sx * sy * sz)
    for (x, y, z), (name, props) in g.d.items():
        structure[((y - y0) * sz + (z - z0)) * sx + (x - x0)] = pal.get(name, props)
    packed = []
    for i in range(0, len(structure), 2):
        hi = structure[i]
        lo = structure[i + 1] if i + 1 < len(structure) else 0
        value = (hi << 16) | lo
        if value >= 1 << 31:
            value -= 1 << 32
        packed.append(value)
    name = 'banque%d' % level_n
    te = Compound({
        'mirror': Byte(0), 'inWarehouse': Byte(0), 'colony': Int(0), 'main': Byte(0),
        'inventory': List[Compound]([Compound({'empty': Byte(1)}) for _ in range(27)]),
        'type': String('interregne:banque_regionale'), 'version': Int(0), 'tagSIze': Int(0), 'Item': String(''),
        'blueprintDataProvider': Compound({
            'corner1': Compound({'x': Int(-hx), 'y': Int(-hy), 'z': Int(-hz)}),
            'corner2': Compound({'x': Int(sx - 1 - hx), 'y': Int(sy - 1 - hy), 'z': Int(sz - 1 - hz)}),
            'schematicName': String(name), 'posTagMap': List[Compound]([])}),
        'pos': Compound({'x': Int(0), 'y': Int(0), 'z': Int(0)}),
        'x': Short(hx), 'y': Short(hy), 'z': Short(hz), 'style': String(''), 'id': String('minecolonies:colonybuilding')})
    root = Compound({
        'size_x': Short(sx), 'size_y': Short(sy), 'size_z': Short(sz),
        'required_mods': List[String]([String('minecolonies'), String('interregne')]),
        'entities': List[Compound]([]),
        'blocks': IntArray(packed),
        'tile_entities': List[Compound]([te]),
        'name': String(name), 'palette': List[Compound](pal.entries),
        'version': Byte(1), 'mcversion': Int(2730),
        'optional_data': Compound({'structurize': Compound({'primary_offset': Compound({'x': Int(hx), 'y': Int(hy), 'z': Int(hz)})})}),
    })
    return name, root, (sx, sy, sz), g.slots


if __name__ == '__main__':
    os.makedirs(CAT, exist_ok=True)
    layout = {}
    for lvl in LEVELS:
        name, root, size, slots = build(lvl)
        nbtlib.File(root, gzipped=True).save(os.path.join(CAT, name + '.blueprint'))
        # hut-relative: y counted from the hut (the hut is at y=1)
        layout[str(lvl)] = [{'pos': [s['x'], s['y'] - 1, s['z']], 'kind': s['kind'], 'facing': s['facing'], 'from': s['from'], 'span': s['span']} for s in slots]
        print('wrote', name, size, len(slots), 'vault slots')
    os.makedirs(os.path.dirname(VAULT), exist_ok=True)
    with open(VAULT, 'w', encoding='utf8') as f:
        json.dump(layout, f, indent=1)
