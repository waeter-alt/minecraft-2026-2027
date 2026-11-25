"""Sanity checks on blueprints, run by every gen_*_blueprints.py before it saves (a failing check writes nothing).

Standalone:  python -I -X utf8 mod/tools/check_blueprints.py [files or folders...]
With no argument, checks every blueprint under mod/src/main/resources/blueprints. Exit code 1 if anything is wrong.

Checks:
- roof blocks (Domum Ornamentum shingles and vanilla stairs used as roof) face the way the roof RISES. Ground truth: in the
  stock MineColonies huts, 3513 shingles have the next roof row up in their facing direction, 16 the other way. An upside-down
  roof (the bug of 2026-11-26 on the Relais/Banque/Dépôt) shows as rows rising against their facing.
- every shingle has a block under it and right behind it on the ridge side. The stock huts put an upside-down stair there
  (2434 of 5851 shingles, most of the rest a log or planks); without it the roof shows holes between rows (2026-11-26).
- every shingle has its tile entity, otherwise it shows untextured in game.
"""
import os
import sys

import nbtlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
DEFAULT = os.path.join(ROOT, 'mod', 'src', 'main', 'resources', 'blueprints')
SHINGLE = 'domum_ornamentum:shingle'
DIRS = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}
EMPTY = {'minecraft:air', 'structurize:blocksubstitution', 'minecraft:cave_air'}


def decode(root):
    sx, sy, sz = int(root['size_x']), int(root['size_y']), int(root['size_z'])
    pal = [(str(e['Name']), {k: str(v) for k, v in e.get('Properties', {}).items()}) for e in root['palette']]
    raw = []
    for v in root['blocks']:
        v = int(v) & 0xffffffff
        raw += [(v >> 16) & 0xffff, v & 0xffff]
    g, i = {}, 0
    for y in range(sy):
        for z in range(sz):
            for x in range(sx):
                g[(x, y, z)] = pal[raw[i]]
                i += 1
    return g


def is_roof(block):
    """A roof surface block. Upside-down stairs are fillers under a roof, not part of its slope."""
    return (block is not None and (block[0] == SHINGLE or block[0].endswith('_stairs'))
            and block[1].get('half', 'bottom') == 'bottom')


def check(root, label):
    """Returns the list of problems (empty = fine)."""
    g = decode(root)
    problems = []

    def solid(p):
        b = g.get(p)
        return b is not None and b[0] not in EMPTY

    for (x, y, z), (name, pr) in sorted(g.items()):
        if not is_roof((name, pr)) or pr.get('half') != 'bottom' or pr.get('facing') not in DIRS:
            continue
        dx, dz = DIRS[pr['facing']]
        behind_up, front_up = (x + dx, y + 1, z + dz), (x - dx, y + 1, z - dz)
        # a roof row continues upward in the facing direction; an inverted one continues upward on the other side.
        # Stairs are only judged when they sit in a roof (another roof block continues them), not as steps or benches.
        if name != SHINGLE and not (is_roof(g.get(behind_up)) or is_roof(g.get(front_up))):
            continue
        if solid(front_up) and not solid(behind_up) and is_roof(g.get(front_up)):
            problems.append('%s: %s at %s faces %s but the roof rises the other way (upside-down roof)'
                            % (label, name, (x, y, z), pr['facing']))
        if name == SHINGLE:
            # the shingle model is thin: with nothing under it or right behind it (ridge side) the roof shows a hole
            for what, p in (('under', (x, y - 1, z)), ('behind (ridge side)', (x + dx, y, z + dz))):
                if not solid(p):
                    problems.append('%s: shingle at %s has nothing %s it (hole in the roof, add a filler)'
                                    % (label, (x, y, z), what))

    shingles = {p for p, b in g.items() if b[0] == SHINGLE}
    with_te = {(int(t['x']), int(t['y']), int(t['z'])) for t in root.get('tile_entities', [])
               if str(t.get('id', '')) == 'domum_ornamentum:materially_retexturable'}
    for p in sorted(shingles - with_te):
        problems.append('%s: shingle at %s has no tile entity (untextured in game)' % (label, p))
    return problems


def require_ok(root, label):
    """For the generators: abort before anything is written."""
    problems = check(root, label)
    if problems:
        for p in problems[:30]:
            print('PROBLEME', p)
        sys.exit('%s: %d problem(s), nothing written' % (label, len(problems)))


def files(args):
    for a in args or [DEFAULT]:
        if os.path.isdir(a):
            for d, _, names in os.walk(a):
                for n in sorted(names):
                    if n.endswith('.blueprint'):
                        yield os.path.join(d, n)
        else:
            yield a


if __name__ == '__main__':
    total = 0
    for f in files(sys.argv[1:]):
        problems = check(nbtlib.load(f), os.path.relpath(f, ROOT))
        for p in problems[:10]:
            print('PROBLEME', p)
        if len(problems) > 10:
            print('  ... and %d more in %s' % (len(problems) - 10, f))
        total += len(problems)
    print('OK' if not total else '%d problem(s)' % total)
    sys.exit(1 if total else 0)
