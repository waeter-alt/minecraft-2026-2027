"""Generates the five Dépôt Régional blueprints (one pack, shared by every colony style) and their code-placed decor.

Run from the repo root:  python -I -X utf8 mod/tools/gen_depot_blueprints.py
Needs `pip install nbtlib`. The output is committed, so this is only needed to change the building.
`mod/tools/render_depot.py` draws the result as isometric PNGs.

The building is a crate factory that turns from a timber hangar into a small steam works, lit with Create blocks that
are only there to look alive (nothing in it produces anything):
  1  open hangar: stacked crates and a shaft line turning under the beams
  2  closed workshop: a belt line along the hall, a flywheel behind a window
  3  manufacture: two bays, belts, depots, chutes and a windmill turning on the east face
  4  factory: two storeys, big cogwheels, fans, tank and pipes, loading dock with crates
  5  steam works: boilers, engines, flywheels, mechanical arms, belts, chimneys, whistle

Same frame as the Relais: x east, y up, z south, hut at the origin on every level (y=1), floor y=0, door on the north face.

Create parts that need a block entity to move are NOT in the blueprint: a builder would place them one block at a time
and a half-built belt chain or windmill breaks itself. They are written to
`data/interregne/depot_decor.json` (positions relative to the hut, still in the north-facing frame) and the mod puts
them down in one go once the level stands: the hidden creative motor that drives every shaft, the belt chains and the
windmill. Shafts, cogwheels, flywheels and the rest are plain blocks in the blueprint and join the motor's network by
themselves.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbtlib  # noqa: E402
from nbtlib import Compound, List, String, Int, Short, Byte, IntArray  # noqa: E402

import gen_relais_blueprints as R  # noqa: E402  (Grid, Palette, connect, roofs: same tools, same conventions)

RES = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources')
CAT = os.path.join(RES, 'blueprints', 'interregne', 'interregne', 'regions')
DECOR = os.path.join(RES, 'data', 'interregne', 'depot_decor.json')

M = 'minecraft:'
C = 'create:'
CASE = C + 'andesite_casing'
BRASS = C + 'brass_casing'
COPPER = C + 'copper_casing'
RAIL = C + 'railway_casing'
IRON = C + 'industrial_iron_block'
STONE = M + 'stone_bricks'
DARK = M + 'dark_oak_planks'
OAK = M + 'oak_planks'
SPRUCE = M + 'spruce_planks'
LOG = M + 'dark_oak_log'
BRICK = M + 'bricks'
FLOOR = M + 'smooth_stone'
COPPER_ROOF = 'weathered_cut_copper'

AXES = ('x', 'y', 'z')


class DepotGrid(R.Grid):
    """The relais grid plus the Create parts, and the list of parts the mod places itself."""

    def __init__(self):
        super().__init__()
        self.decor = []

    # --- plain Create blocks (in the blueprint) ---
    def shaft(self, x, y, z, axis):
        self.put(x, y, z, C + 'shaft', axis=axis)

    def cog(self, x, y, z, axis, large=False):
        self.put(x, y, z, C + ('large_cogwheel' if large else 'cogwheel'), axis=axis)

    def flywheel(self, x, y, z, axis):
        self.put(x, y, z, C + 'flywheel', axis=axis)

    def fan(self, x, y, z, facing):
        self.put(x, y, z, C + 'encased_fan', facing=facing)

    def chute(self, x, y, z, facing='down'):
        self.put(x, y, z, C + 'chute', facing=facing, shape='normal')

    def depot(self, x, y, z):
        self.put(x, y, z, C + 'depot')

    def pipe(self, x, y, z, *sides):
        self.put(x, y, z, C + 'fluid_pipe', **{d: ('true' if d in sides else 'false') for d in ('north', 'south', 'east', 'west', 'up', 'down')})

    def tank(self, x0, x1, y0, y1, z0, z1):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.put(x, y, z, C + 'fluid_tank', top='true' if y == y1 else 'false', bottom='true' if y == y0 else 'false', shape='plain')

    def girder(self, x, y, z, along):
        props = {'x': 'true' if along == 'x' else 'false', 'z': 'true' if along == 'z' else 'false', 'top': 'false', 'bottom': 'false'}
        self.d[(x, y, z)] = (C + 'metal_girder', tuple(sorted(props.items())))

    def arm(self, x, y, z, ceiling=True):
        self.put(x, y, z, C + 'mechanical_arm', ceiling='true' if ceiling else 'false')

    def engine(self, x, y, z, facing):
        self.put(x, y, z, C + 'steam_engine', face='floor', facing=facing)

    def whistle(self, x, y, z, facing):
        self.put(x, y, z, C + 'steam_whistle', facing=facing, size='large')

    # --- parts the mod places (hut-relative y, i.e. y - 1) ---
    def motor_cabinet(self, x, y, z, facing, mat=CASE):
        """A creative motor shut in casing on five sides; the shaft comes out of the sixth. The motor itself is decor."""
        step = {'east': (1, 0, 0), 'west': (-1, 0, 0), 'south': (0, 0, 1), 'north': (0, 0, -1)}[facing]
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            if d != step:
                self.put(x + d[0], y + d[1], z + d[2], mat)
        self.put(x, y, z, mat)  # overwritten by the motor
        self.decor.append((x, y - 1, z, C + 'creative_motor', {'facing': facing}))

    def belt(self, x, y, z, direction, length, casing=False):
        """A belt chain starting at (x,y,z) and running `length` blocks along `direction`; table of casing under it."""
        step = {'east': (1, 0), 'west': (-1, 0), 'south': (0, 1), 'north': (0, -1)}[direction]
        for i in range(length):
            part = 'start' if i == 0 else 'end' if i == length - 1 else 'middle'
            self.decor.append((x + step[0] * i, y - 1, z + step[1] * i, C + 'belt',
                               {'facing': direction, 'slope': 'horizontal', 'part': part, 'casing': 'true' if casing else 'false'}))
            if self.get(x + step[0] * i, y - 1, z + step[1] * i) is None:
                self.put(x + step[0] * i, y - 1, z + step[1] * i, CASE if i % 3 == 0 else STONE)

    def windmill(self, x, y, z, facing='east', radius=2):
        """Bearing on the wall at (x,y,z) with a four-armed sail cross in front of it; assembled by the mod."""
        d = {'east': (1, 0), 'west': (-1, 0), 'south': (0, 1), 'north': (0, -1)}[facing]
        self.decor.append((x, y - 1, z, C + 'windmill_bearing', {'facing': facing}))
        px, pz = d
        side = (0, 1) if px else (1, 0)  # horizontal axis across the face
        ax, az = x + px, z + pz
        self.decor.append((ax, y - 1, az, C + 'sail_frame', {'facing': facing}))
        for k in range(1, radius + 1):
            for sgn in (1, -1):
                for dx, dy, dz in ((side[0] * sgn * k, 0, side[1] * sgn * k), (0, sgn * k, 0)):
                    self.decor.append((ax + dx, y - 1 + dy, az + dz, C + 'white_sail', {'facing': facing}))

    # --- shared pieces ---
    def stack(self, x, z, n=3, kind='barrel'):
        for i in range(n):
            if kind == 'barrel':
                self.barrel(x, 1 + i, z)
            elif kind == 'hay':
                self.hay(x, 1 + i, z)
            else:
                self.put(x, 1 + i, z, OAK)

    def floor(self, x0, x1, z0, z1, mat=FLOOR):
        self.fill(x0, x1, 0, 0, z0, z1, mat)

    def door(self, x0, x1, z, h=3):
        self.carve(x0, x1, 1, h, z, z)

    def roofline(self, y, x0, x1, z, mat=COPPER_ROOF):
        for x in range(x0, x1 + 1):
            self.slab(x, y, z, mat)

    def chimney(self, x, z, y0, y1, mat=BRICK):
        for y in range(y0, y1 + 1):
            self.put(x, y, z, mat)
        self.put(x, y1 + 1, z, M + 'campfire', lit='false', facing='north', signal_fire='false', waterlogged='false')


def hall_walls(g, x0, x1, z0, z1, top, mat, base=STONE, band=None):
    g.ring(x0, x1, z0, z1, 1, top, mat)
    g.ring(x0, x1, z0, z1, 1, 1, base)
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        g.fill(x, x, 1, top, z, z, LOG, axis='y')
    if band:
        g.ring(x0, x1, z0, z1, top, top, band)


def level1(g):
    # Hangar: timber hall with the door in the north gable
    g.floor(-5, 5, -5, 6)
    R.timber_hall(g, -4, 4, -4, 5, 3)
    g.floor(-4, 4, -4, 5, SPRUCE)
    g.door(-1, 1, -4)
    for z in (-1, 2, 4):
        g.window(-4, 2, z)
        g.window(4, 2, z)
    g.gable('z', -4, 5, -4, 4, 4)
    g.put(0, 1, 0, 'interregne:depot_regional', facing='north')
    # a hut-sized counter around the hut, crates and sacks along the walls
    g.stack(-3, -3, 3)
    g.stack(-3, -2, 2)
    g.stack(3, -3, 3, 'hay')
    g.stack(3, 3, 2)
    g.stack(-3, 4, 3, 'plank')
    g.stack(2, 4, 2)
    g.put(1, 1, 3, M + 'composter', level='3')
    # shaft line under the tie beams, driven by a motor shut in a casing cabinet in the west corner
    g.motor_cabinet(-3, 3, 2, 'east')
    for x in range(-2, 3):
        g.shaft(x, 3, 2, 'x')
    g.cog(1, 3, 2, 'x')
    g.cog(1, 3, 3, 'x')  # meshes with the one beside it
    g.lantern(0, 3, 0, hanging=True)
    g.put(0, 4, 0, M + 'chain', axis='y', waterlogged='false')
    R.apron(g, -7, 7, -8, 8)


def level2(g):
    g.floor(-6, 6, -6, 7)
    hall_walls(g, -5, 5, -5, 6, 4, SPRUCE, STONE, DARK)
    g.floor(-5, 5, -5, 6, FLOOR)
    g.door(-1, 1, -5, 3)
    for z in (-2, 1, 4):
        for x in (-5, 5):
            g.window(x, 2, z, 2)
    for x in (-3, 3):
        g.window(x, 2, -5, 2)
    g.gable('z', -5, 6, -5, 5, 5)
    g.put(0, 1, 0, 'interregne:depot_regional', facing='north')
    # shaft line with a flywheel and a belt line to the crates
    g.motor_cabinet(-4, 3, -3, 'east')
    for x in range(-3, 5):
        g.shaft(x, 3, -3, 'x')
    g.flywheel(-1, 3, -3, 'x')
    g.cog(2, 3, -3, 'x', large=True)
    g.belt(4, 2, -3, 'south', 7)
    for z in range(-3, 4):
        g.put(4, 1, z, CASE)
    g.stack(-4, 2, 3)
    g.stack(-4, 3, 2)
    g.stack(-3, 4, 3, 'plank')
    g.stack(2, 4, 3, 'hay')
    g.depot(3, 2, 4)
    g.chute(2, 3, 2, 'down')
    g.put(2, 2, 2, CASE)
    g.lantern(0, 4, 2, hanging=True)
    g.lantern(0, 4, -2, hanging=True)
    g.chimney(-4, 4, 5, 8, STONE)
    R.apron(g, -8, 8, -9, 9)


R.SOLID.update({CASE, BRASS, COPPER, RAIL, IRON, STONE, DARK, OAK, SPRUCE, BRICK, LOG})


def shell(g, x0, x1, z0, z1, top, solid_h=2, glass=('north', 'east'), post=IRON, mat=STONE, band=CASE, step=3):
    """Industrial walls: a stone base, posts every `step` blocks, glass between them on the `glass` faces (the ones the
    street sees) and plain wall on the others, a casing band under a flat roof."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x not in (x0, x1) and z not in (z0, z1):
                continue
            faces = [f for f, hit in (('north', z == z0), ('south', z == z1), ('west', x == x0), ('east', x == x1)) if hit]
            corner = len(faces) == 2
            along = x - x0 if z in (z0, z1) else z - z0
            pillar = corner or along % step == 0
            for y in range(1, top + 1):
                if y == top:
                    g.put(x, y, z, band)
                elif y <= solid_h:
                    g.put(x, y, z, mat if not pillar else post)
                elif pillar:
                    g.put(x, y, z, post)
                elif any(f in glass for f in faces):
                    g.pane(x, y, z)
                else:
                    g.put(x, y, z, mat)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            g.slab(x, top + 1, z, COPPER_ROOF)
    g.ring(x0, x1, z0, z1, top + 1, top + 1, M + 'weathered_cut_copper')
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    g.fill(cx - 1, cx + 1, top + 1, top + 1, cz - 1, cz + 1, M + 'glass')


def windmill_on(g, wall_x, y, z, radius):
    """Bearing one block off the east wall, backed by a stub of wall, so that no sail touches the building."""
    g.put(wall_x, y, z, CASE)
    g.windmill(wall_x + 1, y, z, 'east', radius)


def level3(g):
    g.floor(-7, 7, -6, 8)
    shell(g, -6, 6, -5, 7, 5, post=CASE, step=4)
    g.floor(-6, 6, -5, 7, FLOOR)
    g.door(-1, 1, -5, 4)
    g.put(0, 1, 0, 'interregne:depot_regional', facing='north')
    # main shaft line behind the north glass: motor cabinet, big cog, flywheel
    g.motor_cabinet(-5, 4, -3, 'east', mat=BRASS)
    for x in range(-4, 6):
        g.shaft(x, 4, -3, 'x')
    g.cog(-2, 4, -3, 'x', large=True)
    g.flywheel(1, 4, -3, 'x')
    g.cog(3, 4, -3, 'x')
    # belt along the east glass, fed by its own hidden motor
    g.motor_cabinet(3, 2, -2, 'east', mat=CASE)
    g.belt(4, 2, -2, 'south', 8)
    for z in range(-2, 6):
        g.put(4, 1, z, CASE)
    g.depot(4, 2, 6)
    g.chute(4, 3, 6, 'down')
    g.stack(-5, 4, 3)
    g.stack(-5, 5, 3, 'plank')
    g.stack(-4, 6, 2, 'hay')
    g.stack(0, 6, 3)
    g.stack(1, 6, 2)
    windmill_on(g, 6, 5, 3, 2)
    g.chimney(-5, 6, 7, 10)
    g.chimney(5, 6, 7, 9)
    g.lantern(-2, 5, 0, hanging=True)
    g.lantern(2, 5, 0, hanging=True)
    R.apron(g, -9, 9, -8, 10)


def level4(g):
    g.floor(-8, 8, -7, 9)
    shell(g, -7, 7, -6, 8, 9, solid_h=2, post=CASE, step=5)
    g.floor(-7, 7, -6, 8, FLOOR)
    g.fill(-6, 6, 5, 5, 3, 7, SPRUCE)
    g.fill(-6, -4, 5, 5, -5, 2, SPRUCE)
    g.door(-1, 1, -6, 4)
    g.put(0, 1, 0, 'interregne:depot_regional', facing='north')
    # gear train along the north glass
    g.motor_cabinet(-6, 4, -4, 'east', mat=BRASS)
    for x in range(-5, 7):
        g.shaft(x, 4, -4, 'x')
    for x, big in ((-3, True), (0, True), (4, False)):
        g.cog(x, 4, -4, 'x', large=big)
    g.flywheel(2, 4, -4, 'x')
    # tank and pipes in the NE corner, seen through both glass faces
    g.tank(4, 5, 1, 3, -3, -2)
    for y in range(4, 9):
        g.pipe(4, y, -3, 'up', 'down')
    g.pipe(4, 9, -3, 'down')
    # belt along the east glass, fed by its own hidden motor
    g.motor_cabinet(3, 2, 0, 'east', mat=CASE)
    g.belt(4, 2, 0, 'south', 8)
    for z in range(0, 8):
        g.put(4, 1, z, CASE)
    g.depot(4, 2, 8)
    g.chute(4, 3, 8, 'down')
    g.arm(-3, 4, 0, ceiling=False)
    g.stack(-5, 4, 3)
    g.stack(-5, 5, 3)
    g.stack(-3, 7, 3)
    g.stack(0, 6, 3)
    g.stack(2, 7, 2, 'hay')
    # loading dock out front, with belts running along its sides
    g.fill(-5, 5, 0, 0, -11, -7, CASE)
    g.motor_cabinet(-6, 2, -7, 'east', mat=CASE)
    g.belt(-5, 2, -7, 'north', 5)
    g.motor_cabinet(6, 2, -7, 'west', mat=CASE)
    g.belt(5, 2, -7, 'north', 5)
    for z in range(-11, -6):
        g.put(-5, 1, z, CASE)
        g.put(5, 1, z, CASE)
    for x, z in ((-3, -10), (-2, -10), (-3, -9), (3, -10), (2, -10), (3, -9)):
        g.stack(x, z, 2)
    windmill_on(g, 7, 6, 2, 2)
    g.chimney(-6, 7, 10, 14)
    g.chimney(5, 7, 10, 13)
    g.chimney(-6, -5, 10, 12)
    g.lantern(-2, 4, 0, hanging=True)
    g.lantern(2, 4, 0, hanging=True)
    g.lantern(-3, 8, 4, hanging=True)
    g.lantern(3, 8, 4, hanging=True)
    R.apron(g, -10, 10, -13, 11)


def level5(g):
    g.floor(-10, 10, -9, 11)
    shell(g, -9, 9, -8, 10, 10, solid_h=2, glass=('north', 'east', 'west'), post=BRASS, band=RAIL, step=6)
    g.floor(-9, 9, -8, 10, FLOOR)
    g.fill(-8, 8, 5, 5, -2, 9, SPRUCE)
    g.fill(-8, -4, 5, 5, -7, -3, SPRUCE)
    g.door(-2, 2, -8, 4)
    g.put(0, 1, 0, 'interregne:depot_regional', facing='north')
    # long shaft along the north glass, second floor: cogs and flywheels turning together
    g.motor_cabinet(-8, 6, -6, 'east', mat=BRASS)
    for x in range(-7, 9):
        g.shaft(x, 6, -6, 'x')
    for x in (-5, -1, 3, 7):
        g.cog(x, 6, -6, 'x', large=True)
    for x in (-3, 1, 5):
        g.flywheel(x, 6, -6, 'x')
    # boiler house along the east glass: three tanks side by side, pipes, whistles
    for z0 in (-4, 0, 4):
        g.tank(5, 6, 1, 4, z0, z0 + 1)
        for y in range(5, 9):
            g.pipe(5, y, z0, 'up', 'down')
        g.whistle(7, 5, z0, 'east')
    # belts and arms down the middle of the ground floor, each belt with its own hidden motor
    g.motor_cabinet(-3, 2, -4, 'east', mat=RAIL)
    g.belt(-2, 2, -4, 'south', 9, casing=True)
    g.motor_cabinet(1, 2, -4, 'east', mat=RAIL)
    g.belt(2, 2, -4, 'south', 9, casing=True)
    for z in range(-4, 5):
        g.put(-2, 1, z, RAIL)
        g.put(2, 1, z, RAIL)
    for z in (-1, 2, 5):
        g.arm(0, 4, z, ceiling=True)
        g.put(0, 5, z, RAIL)
    g.depot(-2, 2, 5)
    g.depot(2, 2, 5)
    g.chute(-2, 3, 5, 'down')
    g.chute(2, 3, 5, 'down')
    # loading dock with belts down both sides and stacks of crates
    g.fill(-6, 6, 0, 0, -13, -9, CASE)
    g.motor_cabinet(-7, 2, -9, 'east', mat=RAIL)
    g.belt(-6, 2, -9, 'north', 5)
    g.motor_cabinet(7, 2, -9, 'west', mat=RAIL)
    g.belt(6, 2, -9, 'north', 5)
    for z in range(-13, -8):
        g.put(-6, 1, z, RAIL)
        g.put(6, 1, z, RAIL)
    for x, z in ((-4, -12), (-3, -12), (-2, -12), (2, -12), (3, -12), (4, -12), (-3, -11), (3, -11)):
        g.stack(x, z, 3)
    g.stack(-7, 5, 3)
    g.stack(-7, 8, 3)
    g.stack(7, 8, 3, 'hay')
    windmill_on(g, 9, 7, 2, 3)
    for x, z in ((-7, 8), (0, 8), (7, 8)):
        g.chimney(x, z, 11, 17)
    g.lantern(-4, 5, 0, hanging=True)
    g.lantern(4, 5, 0, hanging=True)
    g.lantern(-4, 9, 4, hanging=True)
    g.lantern(4, 9, 4, hanging=True)
    R.apron(g, -12, 12, -15, 13)


LEVELS = {1: level1, 2: level2, 3: level3, 4: level4, 5: level5}


def build(level):
    g = DepotGrid()
    LEVELS[level](g)
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

    name = 'depot%d' % level
    te = Compound({
        'mirror': Byte(0), 'inWarehouse': Byte(0), 'colony': Int(0), 'main': Byte(0),
        'inventory': List[Compound]([Compound({'empty': Byte(1)}) for _ in range(27)]),
        'type': String('interregne:depot_regional'), 'version': Int(0), 'tagSIze': Int(0), 'Item': String(''),
        'blueprintDataProvider': Compound({
            'corner1': Compound({'x': Int(-hx), 'y': Int(-hy), 'z': Int(-hz)}),
            'corner2': Compound({'x': Int(sx - 1 - hx), 'y': Int(sy - 1 - hy), 'z': Int(sz - 1 - hz)}),
            'schematicName': String(name), 'posTagMap': List[Compound]([])}),
        'pos': Compound({'x': Int(0), 'y': Int(0), 'z': Int(0)}),
        'x': Short(hx), 'y': Short(hy), 'z': Short(hz), 'style': String(''), 'id': String('minecolonies:colonybuilding')})

    root = Compound({
        'size_x': Short(sx), 'size_y': Short(sy), 'size_z': Short(sz),
        'required_mods': List[String]([String('minecolonies'), String('interregne'), String('create')]),
        'entities': List[Compound]([]),
        'blocks': IntArray(packed),
        'tile_entities': List[Compound]([te]),
        'name': String(name), 'palette': List[Compound](pal.entries),
        'version': Byte(1), 'mcversion': Int(2730),
        'optional_data': Compound({'structurize': Compound({'primary_offset': Compound({'x': Int(hx), 'y': Int(hy), 'z': Int(hz)})})}),
    })
    return name, root, (sx, sy, sz), g.decor


if __name__ == '__main__':
    os.makedirs(CAT, exist_ok=True)
    decor = {}
    for lvl in LEVELS:
        name, root, size, parts = build(lvl)
        nbtlib.File(root, gzipped=True).save(os.path.join(CAT, name + '.blueprint'))
        decor[str(lvl)] = [{'pos': [x, y, z], 'block': block, 'props': props} for x, y, z, block, props in parts]
        print('wrote', name, size, len(parts), 'decor parts')
    os.makedirs(os.path.dirname(DECOR), exist_ok=True)
    with open(DECOR, 'w', encoding='utf8') as f:
        json.dump(decor, f, indent=1)
