"""Generates the five Relais commercial blueprints (one pack, shared by every colony style).

Run from the repo root:  python -I -X utf8 mod/tools/gen_relais_blueprints.py
Needs `pip install nbtlib`. The output is committed, so this is only needed to change the building.
`mod/tools/render_relais.py` draws the result as isometric PNGs.

The building is a caravan relay, vanilla blocks only (the pack is shared by every style):
  1  covered stall with a counter and stock
  2  closed hall with a striped awning, plus an open stable under the same roof
  3  two-storey hall with shutters, hanging sign, hay and barrels, bigger stable
  4  the same hall behind a walled yard: gate with a gold plaque, wagon, trough, lanterns
  5  caravanserai: long two-storey hall, gatehouse with banners and gold sign, warehouse, stable arcade, paved court with a well
Everything is described in coordinates relative to the hut (x east, y up, z south). The hut stays at the origin on
every level, the door is on the north face (z < 0), the floor is y=0 and the hut sits at y=1. The hut is always
inside the hall, a few blocks behind the entrance.

Layout of a .blueprint: gzip NBT; `blocks` packs two palette indices per int, ordered y, then z, then x.
The hut block sits at `primary_offset`; its block entity tells MineColonies which building it is and the box to build.
"""
import os
import nbtlib
from nbtlib import Compound, List, String, Int, Short, Byte, IntArray

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'blueprints', 'interregne', 'interregne')
CAT = os.path.join(OUT, 'commerce')

M = 'minecraft:'
WALL = M + 'oak_planks'  # plastered-looking timber infill
TRIM = M + 'dark_oak_planks'
POST = M + 'dark_oak_log'
STONE = M + 'stone_bricks'
ROOF = 'deepslate_tile'  # stairs and slabs of the roofs
ROOF_FULL = M + 'deepslate_tiles'
FLOOR = M + 'spruce_planks'


class Grid:
    """Sparse block grid keyed by (x, y, z); a block is (name, sorted property pairs)."""

    def __init__(self):
        self.d = {}

    def put(self, x, y, z, name, **props):
        self.d[(x, y, z)] = (name, tuple(sorted(props.items())))

    def air(self, x, y, z):
        self.d.pop((x, y, z), None)

    def get(self, x, y, z):
        return self.d.get((x, y, z))

    def fill(self, x0, x1, y0, y1, z0, z1, name, **props):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.put(x, y, z, name, **props)

    def carve(self, x0, x1, y0, y1, z0, z1):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.air(x, y, z)

    def ring(self, x0, x1, z0, z1, y0, y1, name, **props):
        """Perimeter walls of a rectangle."""
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    for y in range(y0, y1 + 1):
                        self.put(x, y, z, name, **props)

    def stair(self, x, y, z, mat, facing, half='bottom'):
        self.put(x, y, z, M + mat + '_stairs', facing=facing, half=half, shape='straight', waterlogged='false')

    def slab(self, x, y, z, mat, kind='bottom'):
        self.put(x, y, z, M + mat + '_slab', type=kind, waterlogged='false')

    def log(self, x, y, z, axis='y', name=POST):
        self.put(x, y, z, name, axis=axis)

    def posts(self, x, z, y0, y1):
        self.fill(x, x, y0, y1, z, z, POST, axis='y')

    def beam(self, x0, x1, y, z0, z1, axis):
        self.fill(x0, x1, y, y, z0, z1, POST, axis=axis)

    def barrel(self, x, y, z, facing='up'):
        self.put(x, y, z, M + 'barrel', facing=facing, open='false')

    def hay(self, x, y, z, axis='y'):
        self.put(x, y, z, M + 'hay_block', axis=axis)

    def lantern(self, x, y, z, hanging=False):
        self.put(x, y, z, M + 'lantern', hanging='true' if hanging else 'false', waterlogged='false')

    def chain(self, x, y, z):
        self.put(x, y, z, M + 'chain', axis='y', waterlogged='false')

    def trapdoor(self, x, y, z, facing, open_=True, half='bottom', mat='spruce'):
        self.put(x, y, z, M + mat + '_trapdoor', facing=facing, half=half, open='true' if open_ else 'false',
                 powered='false', waterlogged='false')

    def banner(self, x, y, z, facing, colour='red'):
        self.put(x, y, z, M + colour + '_wall_banner', facing=facing)

    def fence(self, x, y, z, mat='dark_oak'):
        self.put(x, y, z, M + mat + '_fence', north='false', south='false', east='false', west='false', waterlogged='false')

    def pane(self, x, y, z):
        self.put(x, y, z, M + 'glass_pane', north='false', south='false', east='false', west='false', waterlogged='false')

    def gate(self, x, y, z, facing, mat='dark_oak', open_=False):
        self.put(x, y, z, M + mat + '_fence_gate', facing=facing, open='true' if open_ else 'false', in_wall='false', powered='false')

    def wall(self, x, y, z, mat='stone_brick'):
        self.put(x, y, z, M + mat + '_wall', north='none', south='none', east='none', west='none', up='true', waterlogged='false')

    def water(self, x, y, z):
        self.put(x, y, z, M + 'water', level='0')

    def lamp_post(self, x, z, height=2):
        for y in range(1, height + 1):
            self.fence(x, y, z)
        self.lantern(x, height + 1, z)

    def window(self, x, y, z, h=1):
        for k in range(h):
            self.pane(x, y + k, z)

    def gable(self, slope, a0, a1, v0, v1, y, over=1, ridge_over=1):
        """Shallow gable roof (rises one block every two), stairs and slabs.
        `slope`: axis the roof falls along ('z' or 'x'); a0..a1: wall span on it; v0..v1: wall span along the ridge;
        y: height of the first roof row, directly above the top wall row. The gable ends are filled with wall."""
        low, high = ('north', 'south') if slope == 'z' else ('west', 'east')

        def at(a, v, yy):
            return (v, yy, a) if slope == 'z' else (a, yy, v)

        i = 0
        while True:
            lo, hi = a0 - over + i, a1 + over - i
            if lo > hi:
                break
            hb = y + i // 2
            for v in range(v0 - ridge_over, v1 + ridge_over + 1):
                if lo == hi:
                    if i % 2 == 0:
                        self.slab(*at(lo, v, hb), ROOF)
                    else:
                        self.put(*at(lo, v, hb), ROOF_FULL)
                elif i % 2 == 0:
                    self.slab(*at(lo, v, hb), ROOF)
                    self.slab(*at(hi, v, hb), ROOF)
                else:
                    self.stair(*at(lo, v, hb), ROOF, low)
                    self.stair(*at(hi, v, hb), ROOF, high)
            for end in (v0, v1):
                for a in {lo, hi}:
                    if a0 <= a <= a1:
                        for yy in range(y, hb):
                            self.put(*at(a, end, yy), WALL)
            i += 1
        return y + (i - 1) // 2  # ridge height


def hash2(x, z):
    return ((x * 73856093) ^ (z * 19349663)) & 0xffff


def apron(g, x0, x1, z0, z1, y=0):
    """Weathered ground: cobblestone, gravel and stone bricks mixed by a fixed hash."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if g.get(x, y, z) is None:
                h = hash2(x, z) % 7
                g.put(x, y, z, M + ('gravel' if h < 3 else 'cobblestone' if h < 5 else 'stone_bricks'))


def awning(g, x0, x1, z_wall, y, depth=2):
    """Striped red and white awning, falling away to the north from a wall at z_wall."""
    for x in range(x0, x1 + 1):
        red = (x - x0) % 2 == 0
        g.stair(x, y, z_wall - 1, 'brick' if red else 'quartz', 'north')
        for d in range(2, depth + 1):
            g.slab(x, y, z_wall - d, 'brick' if red else 'quartz', 'bottom')


def timber_hall(g, x0, x1, z0, z1, wall_top, floors=1):
    """Walls of a half-timbered hall: planks, stone base, dark corner posts and a beam band between floors."""
    g.ring(x0, x1, z0, z1, 1, wall_top, WALL)
    g.ring(x0, x1, z0, z1, 1, 1, STONE)
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        g.posts(x, z, 1, wall_top)
    if floors == 2:
        mid = 1 + (wall_top - 1) // 2 + 1
        for x in range(x0, x1 + 1):
            g.log(x, mid, z0, 'x')
            g.log(x, mid, z1, 'x')
        for z in range(z0, z1 + 1):
            g.log(x0, mid, z, 'z')
            g.log(x1, mid, z, 'z')
        g.fill(x0 + 1, x1 - 1, mid, mid, z0 + 1, z1 - 1, FLOOR)
        return mid
    return None


def stall_goods(g, x, z):
    g.barrel(x, 1, z)
    g.barrel(x, 2, z)
    g.barrel(x + 1, 1, z)
    g.hay(x + 1, 1, z + 1)


def level1(g):
    # ground: planks inside, stone border, a small cobbled apron
    g.fill(-2, 2, 0, 0, -2, 4, STONE)
    g.fill(-1, 1, 0, 0, -1, 3, FLOOR)
    apron(g, -4, 4, -4, 6)
    # posts and walls: closed at the back and on the sides, open at the front
    for x in (-2, 2):
        for z in (-2, 1, 4):
            g.posts(x, z, 1, 3)
        g.fill(x, x, 1, 3, 2, 3, WALL)
        g.fence(x, 1, -1)
        g.fence(x, 1, 0)
    g.fill(-1, 1, 1, 3, 4, 4, WALL)
    g.pane(0, 2, 4)
    g.beam(-2, 2, 3, -2, -2, 'x')
    g.gable('x', -2, 2, -2, 4, 4)
    # counter and goods on the east side, entrance gap on the west
    g.fill(1, 2, 1, 1, -2, -2, M + 'stripped_spruce_log', axis='x')
    g.put(1, 2, -2, M + 'melon')
    g.put(2, 2, -2, M + 'pumpkin')
    g.lantern(-2, 2, -2)
    # stock at the back
    g.barrel(-1, 1, 3)
    g.barrel(-1, 2, 3)
    g.barrel(0, 1, 3)
    g.hay(1, 1, 3)
    g.hay(1, 1, 2)
    g.hay(1, 2, 3)
    g.lantern(0, 2, 3)
    # outside: hay, a barrel, a lantern post by the counter
    g.hay(-3, 1, 0)
    g.hay(-3, 1, -1)
    g.barrel(3, 1, 3)
    g.lamp_post(3, -3)


def stable(g, x0, x1, z0, z1, wall_top, roof_y_unused=None):
    """Open stable at the east end of the hall: dirt floor, back wall, posts, rail and stalls. The roof is shared with the hall."""
    g.fill(x0, x1, 0, 0, z0, z1, M + 'coarse_dirt')
    g.fill(x0, x1, 1, wall_top, z1, z1, WALL)
    g.fill(x1, x1, 1, wall_top, z1 - 2, z1, WALL)
    for x, z in ((x1, z0), (x1, z1 - 3), (x1, z1), (x0 + 1, z0)):
        g.posts(x, z, 1, wall_top)
    g.beam(x0 + 1, x1, wall_top, z0, z0, 'x')
    for x in range(x0 + 2, x1):
        g.fence(x, 1, z0)
    g.fence(x1, 1, z0 + 1)
    g.fence(x1, 1, z0 + 2)
    # stalls and feed
    for z in range(z0 + 2, z1 - 1):
        g.fence(x0 + 3, 1, z)
    for x in range(x0 + 1, x1):
        g.hay(x, 1, z1 - 1)
    g.hay(x1 - 1, 2, z1 - 1)
    g.barrel(x0 + 1, 1, z0 + 2)


def level2(g):
    # main hall x -4..4, z -2..4; open stable x 5..9; one continuous roof, ridge along x
    g.fill(-4, 4, 0, 0, -2, 4, FLOOR)
    apron(g, -7, 11, -7, 7)
    timber_hall(g, -4, 4, -2, 4, 4)
    # entrance and windows
    g.carve(-1, 1, 1, 2, -2, -2)
    g.beam(-1, 1, 3, -2, -2, 'x')
    for x in (-3, 3):
        g.window(x, 2, -2)
    for x in (-2, 0, 2):
        g.window(x, 2, 4)
    g.window(-4, 2, 1)
    g.window(-4, 2, 2)
    # partition with the stable, and the stable itself
    g.fill(4, 4, 1, 4, -1, 3, WALL)
    g.carve(4, 4, 1, 2, 1, 2)
    stable(g, 5, 9, -2, 4, 4)
    g.gable('z', -2, 4, -4, 9, 5)
    # awning over the entrance on two posts
    awning(g, -3, 3, -3, 3, 3)
    for x in (-3, 3):
        g.fence(x, 1, -5)
        g.fence(x, 2, -5)
    g.lantern(-2, 2, -5, True)
    g.lantern(2, 2, -5, True)
    # inside: counter, stock
    g.put(-3, 1, 0, M + 'crafting_table')
    g.barrel(-3, 1, 3)
    g.barrel(-3, 2, 3)
    g.barrel(-2, 1, 3)
    g.hay(3, 1, 3)
    g.hay(3, 1, 2)
    g.lantern(0, 3, 2, True)
    # outside west: stacked goods and a lantern post
    g.hay(-6, 1, 0)
    g.hay(-6, 1, 1)
    g.hay(-6, 2, 0)
    g.barrel(-6, 1, -2)
    g.barrel(-5, 1, -2)
    g.barrel(-6, 1, 3)
    g.lamp_post(-5, -4)


def hall3(g, sign):
    """Two-storey hall x -5..5, z -3..5 with a bigger open stable x 6..10 (shared by levels 3 and 4)."""
    g.fill(-5, 5, 0, 0, -3, 5, FLOOR)
    apron(g, -9, 13, -8, 8)
    mid = timber_hall(g, -5, 5, -3, 5, 6, floors=2)
    # entrance, a gallery beam over it, windows with shutters on both floors
    g.carve(-1, 1, 1, 2, -3, -3)
    g.beam(-1, 1, 3, -3, -3, 'x')
    for x in (-3, 3):
        g.window(x, 2, -3, 1)
        g.window(x, 5, -3, 1)
        for dx in (-1, 1):
            g.trapdoor(x + dx, 2, -4, 'north')
            g.trapdoor(x + dx, 5, -4, 'north')
    for x in (-1, 1):
        g.window(x, 5, -3)
    g.window(0, 5, -3)
    for x in (-3, 0, 3):
        g.window(x, 2, 5)
        g.window(x, 5, 5)
    for z in (0, 2):
        g.window(-5, 2, z)
        g.window(-5, 5, z)
    # partition with the stable, and the stable
    g.fill(5, 5, 1, 6, -2, 4, WALL)
    g.carve(5, 5, 1, 2, 1, 2)
    stable(g, 6, 10, -3, 5, 6)
    g.gable('z', -3, 5, -5, 10, 7)
    # awning on posts over the front
    awning(g, -2, 2, -4, 3, 3)
    for x in (-3, 3):
        g.fence(x, 1, -6)
        g.fence(x, 2, -6)
        g.fence(x, 3, -6)
    for x in (-2, 2):
        g.lantern(x, 2, -6, True)
    # stock in the hall: barrels in the back, hay bales, a counter by the door
    for z in (3, 4):
        g.barrel(-4, 1, z)
        g.barrel(-4, 2, z)
        g.barrel(-3, 1, z)
    g.barrel(-3, 2, 4)
    g.hay(4, 1, 4)
    g.hay(4, 1, 3)
    g.hay(4, 2, 4)
    g.hay(3, 1, 4)
    g.put(-4, 1, -1, M + 'crafting_table')
    g.lantern(0, 3, 2, True)
    for y in range(1, mid + 1):
        g.put(0, y, 4, M + 'ladder', facing='north', waterlogged='false')
    g.lantern(0, 6, 2, True)
    # outside west: stacked goods
    g.hay(-7, 1, 0)
    g.hay(-7, 1, 1)
    g.hay(-7, 2, 0)
    g.hay(-7, 1, 3)
    g.barrel(-7, 1, -2)
    g.barrel(-7, 2, -2)
    g.barrel(-6, 1, -2)
    g.barrel(-6, 1, 5)
    g.barrel(-7, 1, 5)
    g.lamp_post(6, -5, 3)
    # chimney through the south slope
    for y in range(6, 11):
        g.put(-3, y, 4, M + 'bricks')
    if sign:
        # hanging sign on a post at the west front corner
        for y in range(1, 6):
            g.fence(-8, y, -5)
        for x in (-7, -6, -5):
            g.fence(x, 5, -5)
        g.chain(-7, 4, -5)
        g.chain(-5, 4, -5)
        g.fill(-7, -5, 2, 3, -5, -5, TRIM)
        g.put(-6, 3, -5, M + 'gold_block')


def level3(g):
    hall3(g, True)


def wagon(g, x0, z0):
    """A covered wagon, 5 long by 3 wide plus a shaft to the east, wheels as open trapdoors."""
    y = 1
    for dx in range(5):
        for dz in range(3):
            g.put(x0 + dx, y + 1, z0 + dz, M + 'spruce_planks')
    for dx in range(5):
        g.fence(x0 + dx, y, z0 + 1, 'spruce')
    for dx in (0, 4):
        g.trapdoor(x0 + dx, y, z0 - 1, 'north')
        g.trapdoor(x0 + dx, y + 1, z0 - 1, 'north')
        g.trapdoor(x0 + dx, y, z0 + 3, 'south')
        g.trapdoor(x0 + dx, y + 1, z0 + 3, 'south')
    g.hay(x0, y + 2, z0 + 1, 'x')
    g.hay(x0 + 1, y + 2, z0 + 1, 'x')
    g.barrel(x0 + 1, y + 2, z0)
    g.barrel(x0 + 1, y + 2, z0 + 2)
    for dx in (2, 3):
        for dz in range(3):
            g.put(x0 + dx, y + 2, z0 + dz, M + 'white_wool')
        g.put(x0 + dx, y + 3, z0 + 1, M + 'white_wool')
    g.fence(x0 + 5, y + 1, z0 + 1, 'spruce')
    g.fence(x0 + 6, y + 1, z0 + 1, 'spruce')


def trough(g, x0, x1, z):
    """Wooden trough of still water: a closed ring of planks, water at floor level inside."""
    for x in range(x0 - 1, x1 + 2):
        for zz in (z - 1, z + 1):
            g.put(x, 1, zz, M + 'spruce_planks')
        g.put(x, 1, z, M + 'spruce_planks')
    for x in range(x0, x1 + 1):
        g.water(x, 1, z)


def paved(g, x0, x1, z0, z1):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            h = hash2(x, z) % 9
            g.put(x, 0, z, M + ('gravel' if h < 2 else 'cobblestone' if h < 6 else 'stone_bricks'))


def level4(g):
    hall3(g, False)
    # yard in front: paved, closed by a low stone wall, a gate and corner lanterns
    paved(g, -9, 12, -12, -4)
    for x in range(-1, 2):
        for z in range(-12, -3):
            g.put(x, 0, z, STONE)
    for x in range(-9, 13):
        g.wall(x, 1, -12)
    for z in range(-12, -4):
        g.wall(-9, 1, z)
        g.wall(12, 1, z)
    g.carve(-1, 1, 1, 1, -12, -12)
    for x in (-2, 2):
        g.posts(x, -12, 1, 4)
    g.beam(-2, 2, 5, -12, -12, 'x')
    g.chain(-1, 4, -12)
    g.chain(1, 4, -12)
    g.fill(-1, 1, 3, 3, -12, -12, TRIM)
    g.put(0, 3, -12, M + 'gold_block')
    g.lantern(-2, 6, -12)
    g.lantern(2, 6, -12)
    for x, z in ((-9, -12), (12, -12), (-9, -5), (12, -5)):
        g.posts(x, z, 1, 3)
        g.lantern(x, 4, z)
    # wagon, trough, stock
    wagon(g, 3, -10)
    trough(g, -7, -5, -9)
    g.barrel(8, 1, -11)
    g.barrel(9, 1, -11)
    g.barrel(8, 2, -11)
    g.hay(10, 1, -10)
    g.hay(10, 1, -11)
    g.hay(10, 2, -11)
    g.barrel(10, 1, -7)
    g.barrel(11, 1, -7)
    g.put(-8, 1, -6, M + 'composter', level='4')
    for x in (-3, 3):
        g.lamp_post(x, -6)
        g.lamp_post(x, -9)


def hearth(g, cx, z, top):
    """A real fireplace in the back wall: brick surround, a lit campfire in the niche, the flue running up through both floors."""
    for x in (cx - 1, cx + 1):
        g.fill(x, x, 1, 3, z, z, M + 'bricks')
    g.put(cx, 3, z, M + 'bricks')
    g.put(cx, 1, z, M + 'campfire', lit='true', facing='south', signal_fire='false', waterlogged='false')
    g.fill(cx, cx, 4, top, z, z, M + 'bricks')
    g.stair(cx - 1, 4, z, 'brick', 'east')
    g.stair(cx + 1, 4, z, 'brick', 'west')
    g.stair(cx - 1, 1, z - 1, 'stone_brick', 'east')  # raised hearth stones in front
    g.stair(cx + 1, 1, z - 1, 'stone_brick', 'west')


def tower(g, x0, z0):
    """A 3x3 corner tower: stone, arrow slits, a crenellated top with a lantern."""
    g.fill(x0, x0 + 2, 1, 7, z0, z0 + 2, STONE)
    g.fill(x0, x0 + 2, 1, 1, z0, z0 + 2, M + 'cobblestone')
    for y in (4, 5):
        for dx, dz in ((1, 0), (1, 2), (0, 1), (2, 1)):
            g.pane(x0 + dx, y, z0 + dz)
    g.fill(x0, x0 + 2, 8, 8, z0, z0 + 2, TRIM)
    for dx in range(3):
        for dz in range(3):
            if (dx + dz) % 2 == 0 and not (dx == 1 and dz == 1):
                g.wall(x0 + dx, 9, z0 + dz)
    g.lantern(x0 + 1, 9, z0 + 1)


def furnish_hall(g, X0, X1, HZ0, HZ1):
    """Ground floor: reception with counter and shelves, a dining corner, the stair, the fireplace. Upper floor: dormitories,
    a long table and a library. Nothing stands in the door lane (x -1..1) or in the way to the hut."""
    # reception: runner from the door to the hut, counter behind it, shelves along the back wall
    for x in (-1, 0, 1):
        for z in (-2, -1):
            g.put(x, 1, z, M + 'red_carpet')
    g.put(-1, 1, 0, M + 'red_carpet')
    g.put(1, 1, 0, M + 'red_carpet')
    for x in (-5, -4, -3, -2, -1, 1, 2, 3, 4, 5):
        g.put(x, 1, 2, TRIM)
    g.put(-3, 2, 2, M + 'lectern', facing='north')
    g.put(3, 2, 2, M + 'lectern', facing='north')
    g.lantern(-1, 2, 2)
    g.lantern(1, 2, 2)
    for x in (-5, -4, -3, 3, 4, 5):
        g.barrel(x, 1, 4)
        g.put(x, 2, 4, M + 'bookshelf')
        g.put(x, 3, 4, M + 'bookshelf')
    g.put(-1, 1, 4, M + 'cartography_table')
    g.put(1, 1, 4, M + 'loom', facing='north')
    g.lantern(0, 4, 3, hanging=True)
    # dining corner (west): a long table with benches
    for z in (-1, 0, 1):
        g.slab(-7, 1, z, 'spruce', 'top')
        g.stair(-8, 1, z, 'spruce', 'east')
        g.stair(-6, 1, z, 'spruce', 'west')
    g.lantern(-7, 2, 0)
    g.barrel(-9, 1, -2)
    g.barrel(-9, 2, -2)
    g.barrel(-9, 1, 2)
    # the stair to the upper floor, rising westward along the back wall: blocks (-5,1)..(-8,4)
    for k in range(4):
        g.stair(-5 - k, 1 + k, 3, 'spruce', 'west')
    g.carve(-8, -6, 5, 5, 3, 3)
    g.fill(-9, -9, 1, 3, 3, 3, M + 'barrel', facing='up', open='false')
    # market table and display in the middle of the east wing
    for x in (4, 5, 6):
        g.slab(x, 1, -1, 'dark_oak', 'top')
    g.put(4, 2, -1, M + 'melon')
    g.put(5, 2, -1, M + 'pumpkin')
    g.lantern(6, 2, -1)
    g.lantern(5, 4, 0, hanging=True)
    g.lantern(-7, 4, 0, hanging=True)
    g.lantern(0, 4, -2, hanging=True)
    # fireplace corner (east) with two armchairs and a rug; stock along the east wall
    hearth(g, 8, HZ1 - 1, 13)
    for x in (7, 9):
        g.stair(x, 1, 2, 'dark_oak', 'south')
    for x in (7, 8, 9):
        g.put(x, 1, 3, M + ('red_carpet' if x == 8 else 'blue_carpet'))
    for z in range(-2, 3):
        g.barrel(X1 - 1, 1, z)
        if z % 2 == 0:
            g.barrel(X1 - 1, 2, z)
        else:
            g.hay(X1 - 1, 2, z)
    g.hay(X1 - 2, 1, -2)
    g.barrel(X1 - 2, 1, -1)
    # upper floor: dormitories at both ends, a long table with benches, a library on the back wall
    for z in (-2, 0):
        g.put(-8, 6, z, M + 'red_bed', facing='west', part='foot', occupied='false')
        g.put(-9, 6, z, M + 'red_bed', facing='west', part='head', occupied='false')
    g.lantern(-9, 6, -1)
    g.barrel(-9, 6, 1)
    for z in (-2, 0, 2):
        g.put(X1 - 2, 6, z, M + 'red_bed', facing='east', part='foot', occupied='false')
        g.put(X1 - 1, 6, z, M + 'red_bed', facing='east', part='head', occupied='false')
    g.lantern(X1 - 1, 6, -1)
    g.lantern(X1 - 1, 6, 1)
    for x in range(-3, 4):
        g.slab(x, 6, 1, 'dark_oak', 'bottom')
        g.stair(x, 6, 0, 'spruce', 'south')
        g.stair(x, 6, 2, 'spruce', 'north')
    for x in (-2, 2):
        g.lantern(x, 7, 1)
    for x in (1, 2, 4, 5):
        g.put(x, 6, 4, M + 'bookshelf')
        g.put(x, 7, 4, M + 'bookshelf')
    g.put(-4, 6, 4, M + 'lectern', facing='south')
    for x in (-6, 6):
        g.banner(x, 7, HZ0 + 1, 'south')


def level5(g):
    X0, X1 = -10, 12
    GZ = -14                     # gate wall
    HZ0, HZ1 = -3, 5             # hall
    # ground: stone and cobble, a polished avenue from the gate to the porch with a border of stone bricks
    for x in range(X0 - 1, X1 + 2):
        for z in range(GZ - 1, HZ1 + 2):
            h = hash2(x, z) % 11
            g.put(x, 0, z, STONE if h < 6 else M + 'cobblestone' if h < 9 else M + 'andesite')
    paved(g, X0 + 1, X1 - 1, GZ + 1, HZ0 - 1)
    for x in range(-2, 3):
        for z in range(GZ - 1, HZ0 - 3):
            g.put(x, 0, z, M + ('polished_andesite' if abs(x) < 2 else 'stone_bricks'))
    g.fill(X0, X1, 0, 0, HZ0, HZ1, FLOOR)
    # hall: two storeys, long north facade
    mid = timber_hall(g, X0, X1, HZ0, HZ1, 7, floors=2)
    g.carve(-1, 1, 1, 3, HZ0, HZ0)
    g.beam(-1, 1, 4, HZ0, HZ0, 'x')
    for x in (-8, -6, -4, 4, 6, 8, 10):
        for y0 in (2, 6):
            g.window(x, y0, HZ0, 2)
            for dx in (-1, 1):
                g.trapdoor(x + dx, y0, HZ0 - 1, 'north')
                g.trapdoor(x + dx, y0 + 1, HZ0 - 1, 'north')
    for x in range(-9, 12, 3):
        g.window(x, 2, HZ1, 2)
        g.window(x, 6, HZ1, 2)
    for z in (0, 2):
        for x in (X0, X1):
            g.window(x, 2, z, 2)
            g.window(x, 6, z, 2)
    # entrance wing: a projecting gabled porch with the way into the hall
    PZ = HZ0 - 4
    g.ring(-3, 3, PZ, HZ0, 1, 7, WALL)
    g.ring(-3, 3, PZ, HZ0, 1, 1, STONE)
    for x, z in ((-3, PZ), (3, PZ), (-3, HZ0), (3, HZ0)):
        g.posts(x, z, 1, 7)
    g.fill(-2, 2, 0, 0, PZ + 1, HZ0 - 1, STONE)
    g.carve(-1, 1, 1, 3, PZ, PZ)
    g.beam(-1, 1, 4, PZ, PZ, 'x')
    g.carve(-1, 1, 1, 3, HZ0, HZ0)
    g.beam(-1, 1, 4, HZ0, HZ0, 'x')
    for x in (-2, 2):
        g.window(x, 2, PZ, 2)
    g.fill(-1, 1, 5, 5, PZ, PZ, M + 'gold_block')      # sign over the door, set into the wall
    g.carve(-1, 1, 6, 7, PZ, PZ)
    g.window(0, 6, PZ, 2)
    for x in (-1, 1):
        g.fill(x, x, 6, 7, PZ, PZ, WALL)
    g.gable('z', HZ0, HZ1, X0, X1, 8)
    g.gable('x', -3, 3, PZ, HZ0, 8)
    for x in (-2, 2):
        g.banner(x, 4, PZ - 1, 'north')
    for x in (-2, 2):                                  # porch: a bench each side under a lantern
        g.stair(x, 1, PZ + 2, 'spruce', 'east' if x < 0 else 'west')
        g.lantern(x, 2, PZ + 2)
    g.barrel(-2, 1, HZ0 - 1)
    g.barrel(2, 1, HZ0 - 1)
    furnish_hall(g, X0, X1, HZ0, HZ1)
    for x in (-6, 6):
        g.banner(x, 5, HZ0 - 1, 'north')
    # curtain wall: two blocks of stone and a parapet, closed except at the gate
    for x in range(X0, X1 + 1):
        if not -3 <= x <= 3:
            g.fill(x, x, 1, 2, GZ, GZ, STONE)
            g.wall(x, 3, GZ)
    for z in range(GZ, HZ0):
        for x in (X0, X1):
            g.fill(x, x, 1, 2, z, z, STONE)
            g.wall(x, 3, z)
    for x in (X0, X1):
        tower(g, x - (0 if x == X0 else 2), GZ)
        g.posts(x, HZ0 - 1, 1, 3)
        g.lantern(x, 4, HZ0 - 1)
    # gatehouse: lower than the hall, so the court stays visible from the road; a raised portcullis under the lintel
    g.ring(-3, 3, GZ, GZ + 2, 1, 5, WALL)
    g.ring(-3, 3, GZ, GZ + 2, 1, 3, STONE)
    for x, z in ((-3, GZ), (3, GZ), (-3, GZ + 2), (3, GZ + 2)):
        g.posts(x, z, 1, 5)
    g.carve(-1, 1, 1, 3, GZ, GZ + 2)
    g.fill(-1, 1, 4, 4, GZ, GZ + 2, TRIM)
    g.fill(-1, 1, 5, 5, GZ, GZ, M + 'gold_block')
    g.gable('x', -3, 3, GZ, GZ + 2, 6)
    for x in (-1, 0, 1):
        g.fence(x, 3, GZ)
    g.lantern(0, 3, GZ + 1, hanging=True)
    g.lantern(0, 3, GZ + 2, hanging=True)
    for x in (-2, 2):
        g.banner(x, 4, GZ - 1, 'north')
        g.lamp_post(x * 2, GZ - 2, 2)
    # warehouse (west, back half of the court): stone walls, a wide door on the court side, barrels
    WX0, WX1, WZ0, WZ1 = -9, -4, -10, -5
    g.fill(WX0, WX1, 0, 0, WZ0, WZ1, FLOOR)
    g.ring(WX0, WX1, WZ0, WZ1, 1, 4, STONE)
    for x, z in ((WX0, WZ0), (WX1, WZ0), (WX0, WZ1), (WX1, WZ1)):
        g.posts(x, z, 1, 4)
    g.carve(WX1, WX1, 1, 3, WZ0 + 2, WZ0 + 3)
    g.fill(WX1, WX1, 4, 4, WZ0 + 2, WZ0 + 3, POST, axis='z')
    g.window(WX0 + 2, 2, WZ0)
    g.window(WX0 + 3, 2, WZ0)
    g.window(WX0, 2, WZ0 + 2)
    g.gable('x', WX0, WX1, WZ0, WZ1, 5)
    for x in range(WX0 + 1, WX1):
        for z in (WZ1 - 1, WZ1 - 2):
            g.barrel(x, 1, z)
            if (x + z) % 2:
                g.barrel(x, 2, z)
    for x in range(WX0 + 1, WX0 + 3):
        g.hay(x, 1, WZ0 + 1)
        g.hay(x, 2, WZ0 + 1)
    g.lantern(WX0 + 2, 3, WZ0 + 3, hanging=True)
    g.lantern(WX0 + 1, 3, WZ0 + 1)
    g.barrel(WX1 + 1, 1, WZ0 + 1)
    g.barrel(WX1 + 1, 2, WZ0 + 1)
    g.barrel(WX1 + 1, 1, WZ0 + 4)
    g.hay(WX1 + 1, 1, WZ0 + 5)
    # stable arcade (east, back half of the court): open to the north, hay and stalls
    SX0, SX1, SZ0, SZ1 = 7, 11, -10, -5
    g.fill(SX0, SX1, 0, 0, SZ0, SZ1, M + 'coarse_dirt')
    g.fill(SX0, SX1, 1, 4, SZ1, SZ1, WALL)
    g.fill(SX0, SX0, 1, 4, SZ0, SZ1, WALL)
    g.fill(SX1, SX1, 1, 4, SZ0, SZ1, WALL)
    g.carve(SX0, SX0, 1, 2, SZ0 + 1, SZ0 + 2)
    for x, z in ((SX0, SZ0), (SX1, SZ0), (SX0, SZ1), (SX1, SZ1), (SX0 + 2, SZ0)):
        g.posts(x, z, 1, 4)
    g.beam(SX0, SX1, 4, SZ0, SZ0, 'x')
    g.gable('x', SX0, SX1, SZ0, SZ1, 5)
    for x in (SX0 + 1, SX0 + 3, SX1 - 1):
        g.fence(x, 1, SZ0)
    g.fence(SX0 + 3, 1, SZ0 + 1)
    g.fence(SX0 + 3, 1, SZ0 + 2)
    g.fence(SX0 + 3, 1, SZ0 + 3)
    for x in (SX0 + 1, SX0 + 2):
        g.hay(x, 1, SZ1 - 1)
    g.hay(SX0 + 1, 2, SZ1 - 1)
    g.hay(SX1 - 1, 1, SZ1 - 1)
    g.hay(SX1 - 1, 1, SZ1 - 2)
    g.barrel(SX1 - 1, 1, SZ0 + 1)
    g.lantern(SX0 + 2, 3, SZ1 - 1, True)
    # court: a covered well in the middle of the avenue (a lane each side and a free row before and after it), a market
    # stall under a striped awning, a trough, lamp posts
    for x in range(-1, 2):
        for z in (-10, -9):
            g.put(x, 1, z, STONE)
            g.slab(x, 4, z, 'dark_oak')
    g.water(0, 1, -10)
    g.water(0, 1, -9)
    for x, z in ((-1, -10), (1, -10), (-1, -9), (1, -9)):
        g.fence(x, 2, z)
        g.fence(x, 3, z)
    for x in (4, 6):
        g.posts(x, -11, 1, 3)
    for x in (4, 5, 6):
        g.slab(x, 1, -10, 'dark_oak', 'top')
    awning(g, 4, 6, -9, 4, 3)
    g.put(4, 2, -10, M + 'melon')
    g.put(5, 2, -10, M + 'pumpkin')
    g.barrel(6, 2, -10)
    g.lantern(5, 3, -11, hanging=True)
    trough(g, 8, 9, -12)
    for x, z in ((-4, -12), (-4, -5), (4, -5), (4, -12)):
        g.lamp_post(x, z)
    g.hay(-6, 1, -12)
    g.hay(-6, 2, -12)
    g.hay(-7, 1, -12)
    g.barrel(-8, 1, -12)
    g.barrel(-8, 1, -11)
    g.barrel(-8, 2, -12)

# cells a visitor must be able to reach on top of the hut: the upper floor, the dining corner, the hearth, the warehouse, the stable
MUST = {5: [(-8, 5, 3), (-4, 6, 1), (-7, 1, -2), (8, 1, 3), (-6, 1, -8), (9, 1, -8), (-9, 6, 3), (9, 6, 3)]}

LEVELS = {1: level1, 2: level2, 3: level3, 4: level4, 5: level5}


class Palette:
    def __init__(self):
        self.entries = []
        self.index = {}
        self.get(M + 'air', ())

    def get(self, name, props):
        key = (name, props)
        if key not in self.index:
            tag = Compound({'Name': String(name)})
            if props:
                tag['Properties'] = Compound({k: String(v) for k, v in props})
            self.index[key] = len(self.entries)
            self.entries.append(tag)
        return self.index[key]


CONNECT = {
    M + 'dark_oak_fence': 'fence', M + 'spruce_fence': 'fence',
    M + 'glass_pane': 'pane', M + 'stone_brick_wall': 'wall',
}
SOLID = {WALL, TRIM, POST, STONE, ROOF_FULL, M + 'stripped_spruce_log', M + 'bricks', M + 'cobblestone', M + 'gold_block'}


def connect(g):
    """Fences, panes and walls join their neighbours (a blueprint stores the shape, nothing recomputes it for us)."""
    dirs = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}
    updates = {}
    for (x, y, z), (name, props) in g.d.items():
        kind = CONNECT.get(name)
        if not kind:
            continue
        p = dict(props)
        for d, (dx, dz) in dirs.items():
            n = g.get(x + dx, y, z + dz)
            ok = bool(n) and (n[0] == name or n[0] in SOLID or (kind == 'fence' and n[0].endswith('_fence_gate'))
                              or (kind == 'wall' and n[0].endswith('_wall')))
            p[d] = ('low' if ok else 'none') if kind == 'wall' else ('true' if ok else 'false')
        if kind == 'wall':
            n, s, e, w = (p[k] != 'none' for k in ('north', 'south', 'east', 'west'))
            straight = (n and s and not e and not w) or (e and w and not n and not s)
            p['up'] = 'false' if straight and not g.get(x, y + 1, z) else 'true'
        updates[(x, y, z)] = (name, tuple(sorted(p.items())))
    g.d.update(updates)


FREE_WORDS = ('_door', 'fence_gate', 'ladder', 'banner', 'carpet', 'button', 'pressure_plate', '_sign', 'torch')


def is_free(entry):
    """True when a body can stand inside this block (open trapdoors and every door or gate count as free)."""
    if entry is None:
        return True
    name, props = entry
    if name.endswith('_trapdoor'):
        return dict(props).get('open') == 'true'
    return any(w in name for w in FREE_WORDS)


def reachable(g, hut=(0, 1, 0), margin=4):
    """Walk from outside the building to the hut: stands on solid blocks, steps up one block, drops up to three, climbs
    ladders, goes through doors and gates, is stopped by everything else. Returns (set of standing cells, True when a cell
    next to the hut at its height can be reached). The ground outside the grid is solid."""
    xs, ys, zs = [k[0] for k in g.d], [k[1] for k in g.d], [k[2] for k in g.d]
    bx0, bx1, bz0, bz1, top = min(xs) - margin, max(xs) + margin, min(zs) - margin, max(zs) + margin, max(ys) + 2

    def free(x, y, z):
        return y > 0 and is_free(g.get(x, y, z))

    def ladder(x, y, z):
        e = g.get(x, y, z)
        return bool(e) and e[0].endswith('ladder')

    def stands(x, y, z):
        if not (bx0 <= x <= bx1 and bz0 <= z <= bz1 and 1 <= y <= top):
            return False
        return free(x, y, z) and free(x, y + 1, z) and (not free(x, y - 1, z) or ladder(x, y, z) or y == 1 and g.get(x, 0, z) is None)

    seen, stack = set(), []
    for x in range(bx0, bx1 + 1):
        for z in (bz0, bz1):
            stack.append((x, 1, z))
    for z in range(bz0, bz1 + 1):
        for x in (bx0, bx1):
            stack.append((x, 1, z))
    stack = [c for c in stack if stands(*c)]
    seen.update(stack)
    while stack:
        x, y, z = stack.pop()
        nxt = []
        if ladder(x, y, z) or ladder(x, y + 1, z):
            nxt.append((x, y + 1, z))
        if ladder(x, y - 1, z) or ladder(x, y, z):
            nxt.append((x, y - 1, z))
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            nxt.append((nx, y, nz))
            tall = g.get(nx, y, nz)  # fences and walls reach 1.5 blocks: no jumping onto them
            if free(x, y + 2, z) and not (tall and any(k in tall[0] for k in ('_fence', '_wall'))):
                nxt.append((nx, y + 1, nz))
            for drop in (1, 2, 3):
                if all(free(nx, y - k, nz) for k in range(0, drop)):
                    nxt.append((nx, y - drop, nz))
        for c in nxt:
            if c not in seen and stands(*c):
                seen.add(c)
                stack.append(c)
    hx, hy, hz = hut
    ok = any((hx + dx, hy, hz + dz) in seen for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    return seen, ok


def assert_access(g, label, must=()):
    """Fails the generation when the hut cannot be walked to from outside, or when one of `must` (extra cells) cannot."""
    seen, ok = reachable(g)
    missing = [c for c in must if c not in seen]
    if not ok or missing:
        raise SystemExit('%s: the hut %s, unreachable cells: %s' % (label, 'is reachable' if ok else 'CANNOT be reached from outside', missing))


def build(level):
    g = Grid()
    LEVELS[level](g)
    g.put(0, 1, 0, 'interregne:relais_commercial', facing='north')
    connect(g)
    assert_access(g, 'relais%d' % level, MUST.get(level, ()))
    xs, ys, zs = [k[0] for k in g.d], [k[1] for k in g.d], [k[2] for k in g.d]
    x0, x1, y0, y1, z0, z1 = min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)
    sx, sy, sz = x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1
    hx, hy, hz = -x0, -y0 + 1, -z0
    pal = Palette()
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
    return name, root, (sx, sy, sz)


if __name__ == '__main__':
    os.makedirs(CAT, exist_ok=True)
    for lvl in LEVELS:
        name, root, size = build(lvl)
        nbtlib.File(root, gzipped=True).save(os.path.join(CAT, name + '.blueprint'))
        print('wrote', name, size)
