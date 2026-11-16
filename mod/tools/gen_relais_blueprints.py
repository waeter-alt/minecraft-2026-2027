"""Generates the five Relais commercial blueprints (one pack, shared by every colony style).

Run from the repo root:  python -I -X utf8 mod/tools/gen_relais_blueprints.py
Needs `pip install nbtlib`. The output is committed, so this is only needed to change the building.

Layout of a .blueprint: gzip NBT; `blocks` packs two palette indices per int, ordered y, then z, then x.
The hut block sits at `primary_offset`; its block entity tells MineColonies which building it is and the box to build.
"""
import os
import nbtlib
from nbtlib import Compound, List, String, Int, Short, Byte, IntArray

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'blueprints', 'interregne', 'interregne')
CAT = os.path.join(OUT, 'commerce')

# footprint (x, z) and wall height per level: the building grows, the door stays on the north face
LEVELS = {1: (7, 7, 4), 2: (9, 7, 4), 3: (9, 9, 5), 4: (11, 9, 5), 5: (11, 11, 6)}


class Palette:
    def __init__(self):
        self.entries = []
        self.index = {}
        self.get('minecraft:air')

    def get(self, name, **props):
        key = (name, tuple(sorted(props.items())))
        if key not in self.index:
            tag = Compound({'Name': String(name)})
            if props:
                tag['Properties'] = Compound({k: String(v) for k, v in props.items()})
            self.index[key] = len(self.entries)
            self.entries.append(tag)
        return self.index[key]


def build(level):
    sx, sz, wall = LEVELS[level]
    sy = wall + 3  # floor, walls, roof, lantern
    pal = Palette()
    grid = {}

    def put(x, y, z, idx):
        grid[(x, y, z)] = idx

    planks = pal.get('minecraft:spruce_planks')
    brick = pal.get('minecraft:stone_bricks')
    log = pal.get('minecraft:stripped_spruce_log', axis='y')
    glass = lambda e, n, s, w: pal.get('minecraft:glass_pane', east=e, north=n, south=s, west=w, waterlogged='false')
    slab = pal.get('minecraft:spruce_slab', type='bottom', waterlogged='false')
    barrel = lambda f: pal.get('minecraft:barrel', facing=f, open='false')
    table = pal.get('minecraft:crafting_table')
    lantern = pal.get('minecraft:lantern', hanging='true', waterlogged='false')

    # floor
    for x in range(sx):
        for z in range(sz):
            put(x, 0, z, brick)
    # walls: log corners, planks between, glass windows, a 3-wide door gap on the north face
    door_lo, door_hi = sx // 2 - 1, sx // 2 + 1
    for y in range(1, wall):
        for x in range(sx):
            for z in range(sz):
                edge = x in (0, sx - 1) or z in (0, sz - 1)
                if not edge:
                    continue
                corner = x in (0, sx - 1) and z in (0, sz - 1)
                if corner:
                    put(x, y, z, log)
                elif z == 0 and door_lo <= x <= door_hi and y <= 2:
                    continue
                elif y == 2 and ((x % 3 == 1) if z in (0, sz - 1) else (z % 3 == 1)):
                    n = z != 0
                    s = z != sz - 1
                    e = x != sx - 1
                    w = x != 0
                    # panes join along the wall they sit in
                    if z in (0, sz - 1):
                        put(x, y, z, glass('true', 'false', 'false', 'true'))
                    else:
                        put(x, y, z, glass('false', 'true', 'true', 'false'))
                else:
                    put(x, y, z, planks)
    # roof: flat plank deck with a slab rim
    for x in range(sx):
        for z in range(sz):
            put(x, wall, z, planks)
    for x in range(-0, sx):
        for z in range(sz):
            if x in (0, sx - 1) or z in (0, sz - 1):
                put(x, wall + 1, z, slab)
    put(sx // 2, wall - 1, sz // 2, lantern)
    # interior: stalls and storage grow with the level
    for i in range(1, sx - 1, 2):
        put(i, 1, sz - 2, barrel('north'))
    if level >= 2:
        put(1, 1, 2, table)
    if level >= 3:
        for i in range(1, sx - 1, 2):
            put(i, 1, sz - 4 if sz > 6 else sz - 3, barrel('south'))
    if level >= 4:
        put(sx - 2, 1, 2, table)
    if level >= 5:
        for i in range(2, sx - 2, 3):
            put(i, 1, 1, barrel('south'))

    # the hut: inside, in front of the door
    hx, hy, hz = sx // 2, 1, 2
    hut = pal.get('interregne:relais_commercial', facing='north')
    put(hx, hy, hz, hut)

    structure = [0] * (sx * sy * sz)
    for (x, y, z), idx in grid.items():
        structure[(y * sz + z) * sx + x] = idx
    packed = []
    for i in range(0, len(structure), 2):
        hi = structure[i]
        lo = structure[i + 1] if i + 1 < len(structure) else 0
        value = (hi << 16) | lo
        if value >= 1 << 31:
            value -= 1 << 32
        packed.append(value)

    name = 'relais%d' % level
    te = Compound({
        'mirror': Byte(0), 'inWarehouse': Byte(0), 'colony': Int(0), 'main': Byte(0),
        'inventory': List[Compound]([Compound({'empty': Byte(1)}) for _ in range(27)]),
        'type': String('interregne:relais_commercial'), 'version': Int(0), 'tagSIze': Int(0), 'Item': String(''),
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
    return name, root


os.makedirs(CAT, exist_ok=True)
for lvl in LEVELS:
    name, root = build(lvl)
    nbtlib.File(root, gzipped=True).save(os.path.join(CAT, name + '.blueprint'))
    print('wrote', name)
