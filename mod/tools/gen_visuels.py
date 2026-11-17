#!/usr/bin/env python3
"""
Génère les visuels « finis » du mod Interrègne (remplace les placeholders) :
  - icônes d'objets et du HUD (16x16),
  - textures + modèles 3D des blocs : poste commercial, guichet impérial, banque régionale, comptoir régional, 5 caisses,
  - skins du Légionnaire et de l'Archer (64x32, mise en page « armure » de HumanoidModel).

Tout est dessiné par code (aucune dépendance à un état local) : relancer le script réécrit les fichiers à l'identique.
    python mod/tools/gen_visuels.py
Aperçu : python mod/tools/preview_visuels.py  (planche dans mod/tools/out/).
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/interregne"
TEX = ROOT / "textures"
MODELS = ROOT / "models"


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def save(img, rel):
    p = TEX / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    img.save(p)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)


def mix(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3)) + (255,)


# ---------------------------------------------------------------- palette or / cuir / etc.
OUT = rgb("3a220c")
GOLD = {"d": rgb("8f5a12"), "g": rgb("dca01e"), "l": rgb("ffd23c"), "h": rgb("fff3a8")}
BROWN = {"d": rgb("5c3418"), "m": rgb("96592c"), "l": rgb("c4864a")}
PURPLE = {"d": rgb("3f1766"), "m": rgb("7a3cb8"), "l": rgb("b784f0")}
RED = {"d": rgb("6e1018"), "m": rgb("b0202c"), "l": rgb("e04450")}
STEEL = {"d": rgb("565d6e"), "m": rgb("a0a8b8"), "l": rgb("e4eaf4")}
CYAN = {"d": rgb("1e6e96"), "m": rgb("50bee0"), "l": rgb("c8ffff")}


class Sprite:
    def __init__(self, w=16, h=16):
        self.im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.w, self.h = w, h

    def px(self, x, y, c):
        x, y = int(x), int(y)
        if 0 <= x < self.w and 0 <= y < self.h:
            self.im.putpixel((x, y), c)

    def get(self, x, y):
        return self.im.getpixel((x, y))

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.px(x, y, c)

    def outline(self, c=OUT):
        """Contour 1 px autour de tout ce qui est opaque."""
        src = self.im.copy()
        for y in range(self.h):
            for x in range(self.w):
                if src.getpixel((x, y))[3] == 0:
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        nx, ny = x + dx, y + dy
                        if 0 <= nx < self.w and 0 <= ny < self.h and src.getpixel((nx, ny))[3] > 0:
                            self.px(x, y, c)
                            break

    def stamp(self, ox, oy, rows, c, hi=None):
        """Pose un motif ('x' = pixel). hi = couleur d'un relief décalé d'1 px en bas (effet gravé)."""
        if hi:
            for y, row in enumerate(rows):
                for x, ch in enumerate(row):
                    if ch == "x":
                        self.px(ox + x, oy + y + 1, hi)
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch == "x":
                    self.px(ox + x, oy + y, c)


CROWN = ["x.x.x.x", "xxxxxxx", "xxxxxxx"]


def disc(s, cx, cy, r, ramp, light=(-0.7, -0.7)):
    """Disque ombré (lumière en haut à gauche) avec jante : pièces."""
    for y in range(s.h):
        for x in range(s.w):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d > r:
                continue
            if d > r - 1.1:
                s.px(x, y, ramp["d"])
                continue
            if d > r - 2.2:
                dot = (dx * light[0] + dy * light[1]) / max(d, 0.001)
                s.px(x, y, ramp["h"] if dot > 0.55 else ramp["l"] if dot > 0.05 else ramp["g"] if dot > -0.5 else ramp["d"])
            else:
                s.px(x, y, ramp["g"] if (dx + dy) > -2.2 else ramp["l"])


def make_coin(big=True):
    s = Sprite()
    disc(s, 8, 8, 6.4, GOLD)
    s.stamp(5, 5, CROWN, GOLD["d"])
    s.outline()
    return s.im


def bag(widths, top, base, cx=7.5):
    s = Sprite()
    for i, hw in enumerate(widths):
        y = top + i
        x0, x1 = int(round(cx - hw)), int(round(cx + hw - 1))
        for x in range(x0, x1 + 1):
            t = (x - x0) / max(1, x1 - x0)
            s.px(x, y, base["l"] if t < 0.22 else base["m"] if t < 0.7 else base["d"])
    return s


def make_bourse():
    # bourse de velours rouge, cordon et fermoir en or, une couronne qui dépasse
    s = bag([2.5, 3, 2, 3.5, 4.5, 5, 5.5, 5.5, 5, 4], 4, RED)
    s.rect(6, 6, 9, 6, GOLD["l"])  # cordon
    s.px(7, 7, GOLD["g"]); s.px(8, 7, GOLD["g"]); s.px(7, 8, GOLD["d"]); s.px(8, 8, GOLD["d"])
    s.stamp(6, 10, ["x.x", "xxx"], GOLD["l"])
    s.px(5, 4, GOLD["l"]); s.px(10, 4, GOLD["l"])
    s.outline()
    return s.im


def make_sac():
    # gros sac de toile plein de couronnes, lien rouge
    s = bag([3, 3.5, 3, 4, 5, 6, 6.5, 7, 7, 6.5, 5.5], 3, {"l": BROWN["l"], "m": mix(BROWN["l"], BROWN["m"], 0.6), "d": BROWN["m"]})
    for x, y in ((5, 2), (7, 1), (9, 2), (6, 1), (8, 2)):
        s.px(x, y, GOLD["l"])
    s.px(7, 2, GOLD["g"]); s.px(8, 1, GOLD["g"])
    s.rect(5, 5, 10, 5, RED["m"])
    s.px(7, 6, RED["m"]); s.px(8, 7, RED["d"])
    s.stamp(6, 9, CROWN, BROWN["d"])
    s.outline()
    return s.im


def make_lingot():
    s = Sprite()
    # dessus (trapèze clair) puis face avant (plus sombre)
    for y in range(4, 8):
        k = y - 4
        for x in range(4 - k // 2 - 0, 12 + (k + 1) // 2 + 1):
            s.px(x, y, GOLD["l"])
    for y in range(8, 12):
        for x in range(3 if y < 11 else 4, 14 if y < 11 else 13):
            s.px(x, y, GOLD["g"])
    s.rect(3, 11, 13, 11, GOLD["d"])
    s.rect(5, 4, 10, 4, GOLD["h"])
    s.rect(3, 8, 13, 8, GOLD["h"])
    s.rect(3, 9, 3, 10, GOLD["l"])
    s.stamp(6, 5, ["x.x.x", "xxxxx"], GOLD["g"])
    s.stamp(6, 9, ["x.x.x", "xxxxx"], GOLD["d"])
    s.outline()
    return s.im


def make_coffret():
    s = Sprite()
    s.rect(2, 8, 13, 13, PURPLE["m"])
    s.rect(2, 8, 3, 13, PURPLE["l"])
    s.rect(12, 8, 13, 13, PURPLE["d"])
    s.rect(2, 13, 13, 13, PURPLE["d"])
    s.rect(3, 4, 12, 7, PURPLE["m"])
    s.rect(3, 4, 12, 4, PURPLE["l"])
    s.rect(2, 7, 13, 8, GOLD["l"])  # liseré or entre couvercle et corps
    s.rect(2, 13, 13, 13, GOLD["d"])
    for x in (4, 11):
        s.rect(x, 4, x, 12, GOLD["g"])  # sangles
    s.rect(7, 6, 8, 10, GOLD["l"])
    s.rect(7, 8, 8, 9, GOLD["d"])
    s.px(7, 6, GOLD["h"])
    s.outline()
    return s.im


def make_tresor():
    s = Sprite()
    # coffre ouvert, débordant d'or, gemme draconique
    s.rect(1, 9, 14, 14, rgb("6a3a1a"))
    s.rect(1, 9, 14, 9, GOLD["l"])
    s.rect(1, 14, 14, 14, GOLD["d"])
    for x in (2, 7, 12):
        s.rect(x, 9, x + 1, 14, GOLD["g"])
    s.rect(2, 3, 13, 6, CYAN["d"])           # couvercle relevé
    s.rect(3, 4, 12, 5, CYAN["m"])
    s.rect(2, 3, 13, 3, GOLD["l"])
    s.rect(2, 6, 13, 6, GOLD["d"])
    for x, y in ((3, 8), (5, 7), (7, 8), (9, 7), (11, 8), (4, 8), (6, 8), (8, 8), (10, 8), (12, 8)):
        s.px(x, y, GOLD["l"] if (x + y) % 2 else GOLD["g"])
    s.px(5, 7, GOLD["h"]); s.px(9, 7, GOLD["h"])
    s.rect(7, 7, 8, 8, CYAN["l"])            # gemme
    s.px(7, 7, rgb("ffffff"))
    s.outline()
    for x, y in ((0, 2), (15, 4), (14, 1)):  # étincelles
        s.px(x, y, CYAN["l"])
    return s.im


# ---------------------------------------------------------------- icônes HUD
def make_hud_bank():
    s = Sprite()
    s.rect(1, 13, 14, 14, STEEL["m"])
    s.rect(2, 12, 13, 12, STEEL["l"])
    for x in (3, 6, 9, 12):
        s.rect(x, 6, x + 1, 11, GOLD["l"])
        s.px(x + 1, 7, GOLD["g"]); s.rect(x + 1, 8, x + 1, 11, GOLD["g"])
    s.rect(2, 5, 13, 5, GOLD["g"])
    s.rect(3, 4, 12, 4, GOLD["l"])
    s.rect(5, 3, 10, 3, GOLD["l"])
    s.rect(7, 2, 8, 2, GOLD["h"])
    s.outline()
    return s.im


def make_hud_region():
    s = Sprite()
    for y in range(2, 14):
        k = max(0, y - 8)
        for x in range(3 + k, 13 - k):
            t = (x - 3) / 9
            s.px(x, y, RED["l"] if t < 0.25 else RED["m"] if t < 0.7 else RED["d"])
    s.rect(3, 2, 12, 2, GOLD["l"])
    s.rect(7, 4, 8, 10, GOLD["l"])
    s.rect(5, 6, 10, 7, GOLD["l"])
    s.px(7, 5, GOLD["h"])
    s.outline()
    return s.im


def make_hud_influence():
    s = Sprite()
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        r = 6.6 if i % 2 == 0 else 2.8
        pts.append((8 + r * math.cos(a), 8 + r * math.sin(a)))
    from PIL import ImageDraw
    d = ImageDraw.Draw(s.im)
    d.polygon(pts, fill=PURPLE["m"])
    for y in range(16):
        for x in range(16):
            c = s.get(x, y)
            if c[3]:
                s.px(x, y, PURPLE["l"] if (x + y) < 13 else PURPLE["d"] if (x + y) > 18 else PURPLE["m"])
    s.rect(7, 7, 8, 8, rgb("fbeaff"))
    s.outline(rgb("1d0a33"))
    return s.im


# ---------------------------------------------------------------- textures procédurales de blocs
def jitter(c, rnd, a=0.07):
    return shade(c, 1 + rnd.uniform(-a, a))


def planks(base, n=4, vertical=False, seed=1, seam=0.6):
    rnd = random.Random(seed)
    im = Sprite().im
    size = 16 // n
    for b in range(n):
        bc = shade(base, 1 + rnd.uniform(-0.1, 0.1))
        for i in range(16):
            for j in range(size):
                x, y = (b * size + j, i) if vertical else (i, b * size + j)
                c = jitter(bc, rnd, 0.05)
                if (i + (3 if b % 2 else 0)) % 8 == 0 and rnd.random() < 0.9:
                    c = shade(bc, 0.82)
                if j == size - 1:
                    c = shade(bc, seam)
                elif j == 0:
                    c = shade(bc, 1.12)
                im.putpixel((x, y), c)
    return im


def stone(base, seed=2, blocks=True, speck=0.08):
    rnd = random.Random(seed)
    im = Sprite().im
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), jitter(base, rnd, speck))
    if blocks:
        for y in range(16):
            for x in range(16):
                if y % 8 == 0 or (x + (4 if (y // 8) % 2 else 0)) % 8 == 0 and y % 8 != 0:
                    im.putpixel((x, y), shade(base, 0.72))
                elif y % 8 == 1:
                    im.putpixel((x, y), shade(base, 1.1))
    return im


def bricks(brick, mortar, seed=3):
    rnd = random.Random(seed)
    im = Sprite().im
    for y in range(16):
        row = y // 4
        for x in range(16):
            off = 4 if row % 2 else 0
            if y % 4 == 3 or (x + off) % 8 == 7:
                im.putpixel((x, y), mortar)
            else:
                bc = shade(brick, 1 + 0.08 * ((row * 7 + (x + off) // 8) % 3 - 1))
                im.putpixel((x, y), jitter(bc, rnd, 0.05))
    return im


def ore_stone(base, flecks, seed=4):
    im = stone(base, seed, blocks=False, speck=0.1)
    rnd = random.Random(seed + 10)
    for _ in range(7):
        x, y = rnd.randint(1, 14), rnd.randint(1, 14)
        c = rnd.choice(flecks)
        im.putpixel((x, y), c)
        if rnd.random() < 0.6:
            im.putpixel((x + 1, y), shade(c, 0.85))
    return im


def straw(seed=5):
    rnd = random.Random(seed)
    im = Sprite().im
    cols = [rgb("e8c04a"), rgb("d4a52e"), rgb("f2d56a"), rgb("c28f22")]
    for x in range(16):
        c = cols[(x * 7 + rnd.randint(0, 2)) % 4]
        for y in range(16):
            im.putpixel((x, y), jitter(c, rnd, 0.06))
        if x % 4 == 1:
            for y in range(16):
                im.putpixel((x, y), shade(c, 0.8))
    return im


def log_ends(seed=6):
    im = Sprite().im
    rnd = random.Random(seed)
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), rgb("5a3a1c"))
    for cx, cy in ((4, 4), (11, 4), (4, 11), (11, 11)):
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - cx + 0.5, y - cy + 0.5)
                if d < 3.9:
                    im.putpixel((x, y), rgb("c89458") if int(d) % 2 == 0 else rgb("a8763c"))
                elif d < 4.6:
                    im.putpixel((x, y), rgb("3e2610"))
    return im


def stripes(c1, c2, w=4, vertical=True):
    im = Sprite().im
    for y in range(16):
        for x in range(16):
            k = (x if vertical else y) // w
            c = c1 if k % 2 == 0 else c2
            im.putpixel((x, y), shade(c, 1.1 if ((x if vertical else y) % w) == 0 else 1))
    return im


def metal_plate(base, seed=7):
    rnd = random.Random(seed)
    im = Sprite().im
    for y in range(16):
        for x in range(16):
            c = jitter(base, rnd, 0.03)
            if x in (0, 15) or y in (0, 15):
                c = shade(base, 0.72)
            elif x in (1, 14) or y in (1, 14):
                c = shade(base, 1.18)
            im.putpixel((x, y), c)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        im.putpixel((x, y), shade(base, 1.35)); im.putpixel((x + 1 if x < 8 else x - 1, y), shade(base, 0.7))
    return im


def sign_board(arrow_right=True, seed=8):
    im = planks(rgb("a77438"), 3, seed=seed)
    px = im.load()
    col = rgb("f3e4b8")
    shaft = list(range(2, 12))
    y = 7
    for x in shaft:
        px[x if arrow_right else 15 - x, y] = col
        px[x if arrow_right else 15 - x, y + 1] = col
    for k in range(3):
        for dy in (-k, k + 1):
            xx = 12 - k
            px[xx if arrow_right else 15 - xx, y + dy] = col
    px[13 if arrow_right else 2, y] = col; px[13 if arrow_right else 2, y + 1] = col
    for x in range(16):
        px[x, 0] = shade(rgb("a77438"), 0.55); px[x, 15] = shade(rgb("a77438"), 0.55)
    return im


def lantern():
    im = Sprite().im
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), rgb("2a2a30") if (x < 2 or x > 13 or y < 2 or y > 13 or x in (7, 8)) else rgb("ffd75a") if (x + y) % 5 else rgb("fff3b0"))
    return im


def barrel_side(seed=9):
    im = planks(rgb("8a5a2a"), 4, vertical=True, seed=seed)
    for y in (2, 13):
        for x in range(16):
            im.putpixel((x, y), rgb("4a4e58"))
            im.putpixel((x, y + 1 if y == 2 else y - 1), rgb("6e7482"))
    return im


def barrel_top():
    im = Sprite().im
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            im.putpixel((x, y), rgb("4a4e58") if d > 7.4 else rgb("6e7482") if d > 6.2 else rgb("8a5a2a") if int(d) % 3 else rgb("a8763c"))
    return im


def marble(seed=10):
    rnd = random.Random(seed)
    im = Sprite().im
    for y in range(16):
        for x in range(16):
            v = 1 + rnd.uniform(-0.03, 0.03) + (0.07 if (x * 3 + y * 5) % 17 == 0 else 0)
            im.putpixel((x, y), shade(rgb("ece6da"), v))
    return im


def column(seed=11):
    im = Sprite().im
    for y in range(16):
        for x in range(16):
            t = abs((x - 7.5) / 7.5)
            c = shade(rgb("e6dfce"), 1.05 - 0.22 * t * t)
            if x % 5 == 2:
                c = shade(c, 0.92)
            im.putpixel((x, y), c)
    return im


def vault_door():
    im = metal_plate(rgb("6a7080"), 12)
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 3.2 < d < 5.4:
                px[x, y] = GOLD["g"] if (x + y) % 3 else GOLD["l"]
            elif d <= 3.2:
                px[x, y] = GOLD["d"] if d > 1.5 else GOLD["h"]
    for k in range(-5, 6):
        px[int(7.5 + k * 0.0), int(7.5 + k)] = px[int(7.5 + k * 0.0), int(7.5 + k)]
    return im


def purple_panel(seed=13):
    rnd = random.Random(seed)
    im = Sprite().im
    for y in range(16):
        for x in range(16):
            c = jitter(PURPLE["m"], rnd, 0.04)
            if y in (0, 15) or x in (0, 15):
                c = GOLD["g"]
            elif y in (1, 14) or x in (1, 14):
                c = GOLD["d"]
            elif (x + y) % 8 == 0:
                c = shade(c, 1.15)
            im.putpixel((x, y), c)
    return im


def dark_purple(seed=14):
    rnd = random.Random(seed)
    im = Sprite().im
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), jitter(PURPLE["d"], rnd, 0.06))
    return im


def gold_tex(seed=15):
    return metal_plate(GOLD["g"], seed)


def shelf(seed=16):
    im = planks(rgb("8a5a2a"), 2, seed=seed)
    px = im.load()
    cols = [RED["m"], rgb("3a8a4a"), rgb("3a6ab0"), GOLD["l"], PURPLE["l"]]
    for k, x0 in enumerate(range(1, 15, 3)):
        c = cols[k % 5]
        for y in range(2, 7):
            px[x0, y] = c; px[x0 + 1, y] = shade(c, 0.8)
        for y in range(10, 15):
            px[x0, y] = cols[(k + 2) % 5]; px[x0 + 1, y] = shade(cols[(k + 2) % 5], 0.8)
    return im


def sheaf():
    return straw(21)


# ---------------------------------------------------------------- modèles JSON
def faces_auto(b, tex, only=None, cull=False):
    """Faces d'un cuboïde avec UV 'projetées' (la texture se lit comme dans le monde)."""
    x1, y1, z1, x2, y2, z2 = b
    f = {
        "up": [x1, z1, x2, z2],
        "down": [x1, 16 - z2, x2, 16 - z1],
        "north": [16 - x2, 16 - y2, 16 - x1, 16 - y1],
        "south": [x1, 16 - y2, x2, 16 - y1],
        "west": [z1, 16 - y2, z2, 16 - y1],
        "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1],
    }
    out = {}
    for k, uv in f.items():
        t = tex.get(k, tex.get("all")) if isinstance(tex, dict) else tex
        if t is None:
            continue
        face = {"uv": [round(v, 3) for v in uv], "texture": "#" + t}
        out[k] = face
    return out


def box(b, tex, name=None):
    e = {"from": list(b[:3]), "to": list(b[3:]), "faces": faces_auto(b, tex)}
    if name:
        e["name"] = name
    return e


def write_model(name, textures, elements, item_parent=True, gui="side", display=None):
    m = {"textures": {k: "interregne:block/" + v for k, v in textures.items()}, "elements": elements}
    m["parent"] = "minecraft:block/block"
    if display:
        m["display"] = display
    m["textures"]["particle"] = m["textures"][next(iter(textures))]
    (MODELS / "block").mkdir(parents=True, exist_ok=True)
    (MODELS / "block" / f"{name}.json").write_text(json.dumps(m, indent=2), encoding="utf-8")
    (MODELS / "item" / f"{name}.json").write_text(json.dumps({"parent": f"interregne:block/{name}"}, indent=2), encoding="utf-8")


def bt(img, name):
    save(img, f"block/{name}.png")


# ---- caisses
CRATE_KINDS = {
    "ble": dict(panel=lambda: straw(30), top=lambda: straw(31), bump=lambda: straw(32), label="sacs de blé"),
    "bois": dict(panel=lambda: planks(rgb("b07f44"), 4, seed=33), top=log_ends, bump=lambda: planks(rgb("c08f50"), 2, seed=34), label="rondins"),
    "brique": dict(panel=lambda: bricks(rgb("a8452f"), rgb("c9b8a0"), 35), top=lambda: bricks(rgb("b34c34"), rgb("c9b8a0"), 36), bump=lambda: bricks(rgb("b34c34"), rgb("c9b8a0"), 37), label="briques"),
    "minerai": dict(panel=lambda: ore_stone(rgb("4a4d56"), [GOLD["l"], rgb("55c3e6"), rgb("d85a5a")], 38), top=lambda: ore_stone(rgb("5a5d66"), [GOLD["l"], rgb("55c3e6"), rgb("d85a5a"), rgb("8be0a0")], 39), bump=lambda: ore_stone(rgb("5e616b"), [GOLD["l"], rgb("55c3e6")], 40), label="minerai"),
    "pierre": dict(panel=lambda: stone(rgb("8a8d94"), 41), top=lambda: stone(rgb("9a9da4"), 42, blocks=False), bump=lambda: stone(rgb("a2a5ac"), 43), label="pierre"),
}


def crate_model(kind):
    tex = {"cadre": "caisse_cadre", "panneau": f"caisse_{kind}", "contenu": f"caisse_{kind}_top", "relief": f"caisse_{kind}_relief"}
    E = []
    c = {"all": "cadre"}
    for x in (0, 14):
        for z in (0, 14):
            E.append(box((x, 0, z, x + 2, 16, z + 2), c))        # 4 montants
    E.append(box((2, 0, 2, 14, 2, 14), c))                      # plancher
    p = {"all": "panneau"}
    E.append(box((2, 2, 1, 14, 14, 2), p)); E.append(box((2, 2, 14, 14, 14, 15), p))
    E.append(box((1, 2, 2, 2, 14, 14), p)); E.append(box((14, 2, 2, 15, 14, 14), p))
    E.append(box((2, 14, 0, 14, 16, 2), c)); E.append(box((2, 14, 14, 14, 16, 16), c))   # liteaux hauts
    E.append(box((0, 14, 2, 2, 16, 14), c)); E.append(box((14, 14, 2, 16, 14 + 2, 14), c))
    E.append(box((2, 2, 2, 14, 13, 14), {"all": "contenu"}))     # contenu
    r = {"all": "relief"}
    for (x, z, w, h) in ((3, 3, 5, 15), (9, 4, 4, 14.5), (4, 9, 4, 14.5), (9, 9, 4, 15)):
        E.append(box((x, 13, z, x + w, h, z + w), r))
    # sangle de renfort (relief sur les côtés)
    E.append(box((6, 2, 0.5, 10, 14, 1), c)); E.append(box((6, 2, 15, 10, 14, 15.5), c))
    E.append(box((0.5, 2, 6, 1, 14, 10), c)); E.append(box((15, 2, 6, 15.5, 14, 10), c))
    write_model(f"caisse_{kind}", tex, E)


# ---- poste commercial (relais de caravaniers)
def poste_model():
    tex = {"socle": "poste_socle", "bois": "poste_bois", "gauche": "poste_panneau_g", "droite": "poste_panneau_d",
           "lanterne": "poste_lanterne", "caisse": "poste_caisse", "tonneau": "poste_tonneau", "tonneau_top": "poste_tonneau_top"}
    E = [box((0, 0, 0, 16, 2, 16), {"all": "socle"}),
         box((1, 2, 1, 15, 3, 15), {"all": "socle"}),
         box((7, 3, 7, 9, 13, 9), {"all": "bois"}),                                   # mât
         box((6.2, 3, 6.2, 9.8, 4.5, 9.8), {"all": "bois"}),
         # panneaux de direction perpendiculaires, flèches opposées de chaque côté
         box((1, 10.5, 9, 15, 14.5, 10), {"all": "bois", "south": "droite", "north": "gauche"}),
         box((9, 6.5, 1, 10, 10.5, 15), {"all": "bois", "east": "droite", "west": "gauche"}),
         # lanterne au sommet
         box((6.5, 13, 6.5, 9.5, 15.5, 9.5), {"all": "lanterne"}),
         box((6, 15.5, 6, 10, 16, 10), {"all": "bois"}),
         # marchandises
         box((1, 3, 1, 6, 8, 6), {"all": "caisse"}),
         box((1.5, 8, 1.5, 5.5, 10, 5.5), {"all": "caisse"}),
         box((10.5, 3, 10.5, 15, 9, 15), {"all": "tonneau", "up": "tonneau_top", "down": "tonneau_top"}),
         box((11.5, 3, 2, 14.5, 5.5, 5), {"all": "caisse"})]
    write_model("poste_commercial", tex, E)
    write_model("relais_commercial", tex, E)


def guichet_model():
    tex = {"pourpre": "guichet_pourpre", "sombre": "guichet_sombre", "marbre": "guichet_marbre", "or": "guichet_or"}
    E = [box((0, 0, 0, 16, 8, 16), {"all": "pourpre"}),
         box((0, 0, 0, 16, 1.5, 16), {"all": "or"}),
         box((0, 8, 0, 16, 10, 16), {"all": "marbre", "down": "sombre"}),
         ]
    for x in (0, 14):
        for z in (0, 14):
            E.append(box((x, 10, z, x + 2, 16, z + 2), {"all": "or"}))               # 4 colonnes d'angle
    E.append(box((0, 14, 0, 16, 16, 16), {"all": "pourpre", "down": "sombre"}))      # linteau/toit
    E.append(box((0, 13.4, 0, 16, 14, 16), {"all": "or"}))
    E.append(box((2, 10, 0.4, 14, 13.4, 0.8), {"all": "sombre"}))                    # fond de fenêtre
    for k in range(5):                                                               # barreaux dorés des 4 côtés
        x = 3.2 + k * 2.4
        E.append(box((x, 10, 0.2, x + 0.6, 13.4, 0.8), {"all": "or"}))
        E.append(box((x, 10, 15.2, x + 0.6, 13.4, 15.8), {"all": "or"}))
        E.append(box((0.2, 10, x, 0.8, 13.4, x + 0.6), {"all": "or"}))
        E.append(box((15.2, 10, x, 15.8, 13.4, x + 0.6), {"all": "or"}))
    E.append(box((6, 10, 6, 10, 11, 10), {"all": "or"}))                             # piles de couronnes
    E.append(box((6.5, 11, 6.5, 9.5, 12, 9.5), {"all": "or"}))
    write_model("guichet_imperial", tex, E)


def banque_model():
    tex = {"pierre": "banque_pierre", "colonne": "banque_colonne", "porte": "banque_porte", "or": "banque_or", "toit": "banque_toit"}
    E = [box((0, 0, 0, 16, 1.5, 16), {"all": "pierre"}), box((0.8, 1.5, 0.8, 15.2, 2.5, 15.2), {"all": "pierre"}),
         box((3, 2.5, 3, 13, 11, 13), {"all": "pierre", "south": "porte"}),                       # corps
         box((3, 2.5, 3, 13, 11, 13), {"all": "pierre", "north": "porte"}) if False else None]
    E = [e for e in E if e]
    # portes sur les 4 faces (symétrique : la banque n'a pas d'orientation)
    for face, b in (("north", (5.5, 2.5, 2.7, 10.5, 8.5, 3.1)), ("south", (5.5, 2.5, 12.9, 10.5, 8.5, 13.3)),
                    ("west", (2.7, 2.5, 5.5, 3.1, 8.5, 10.5)), ("east", (12.9, 2.5, 5.5, 13.3, 8.5, 10.5))):
        E.append(box(b, {"all": "porte"}))
    for a in (1.2, 12.8):                                                                         # colonnes (4 coins)
        for z in (1.2, 12.8):
            E.append(box((a, 2.5, z, a + 2, 11, z + 2), {"all": "colonne", "up": "pierre", "down": "pierre"}))
    E.append(box((0.4, 11, 0.4, 15.6, 12.6, 15.6), {"all": "pierre"}))                           # entablement
    E.append(box((0.4, 12.6, 0.4, 15.6, 13, 15.6), {"all": "or"}))
    E.append(box((2, 13, 2, 14, 14.4, 14), {"all": "pierre"}))
    E.append(box((4, 14.4, 4, 12, 15.6, 12), {"all": "toit"}))
    E.append(box((6.5, 15.6, 6.5, 9.5, 16, 9.5), {"all": "or"}))
    write_model("banque_regionale", tex, E)


def depot_model():
    tex = {"bois": "depot_bois", "auvent": "depot_auvent", "etagere": "depot_etagere", "or": "depot_or", "caisse": "depot_caisse"}
    E = [box((0, 0, 0, 16, 7, 16), {"all": "bois", "up": "bois"}),
         box((0, 7, 0, 16, 8.5, 16), {"all": "or", "up": "bois"}),
         box((1, 8.5, 1, 15, 9, 15), {"all": "bois"})]
    for x in (0, 14.5):
        for z in (0, 14.5):
            E.append(box((x, 8.5, z, x + 1.5, 16, z + 1.5), {"all": "bois"}))       # montants d'auvent
    E.append(box((0, 14, 0, 16, 15.2, 16), {"all": "auvent"}))
    E.append(box((0, 12.6, 0, 16, 14, 1.2), {"all": "auvent"})); E.append(box((0, 12.6, 14.8, 16, 14, 16), {"all": "auvent"}))
    E.append(box((0, 12.6, 1.2, 1.2, 14, 14.8), {"all": "auvent"})); E.append(box((14.8, 12.6, 1.2, 16, 14, 14.8), {"all": "auvent"}))
    E.append(box((3, 15.2, 3, 13, 16, 13), {"all": "auvent"}))
    E.append(box((2, 9, 2, 14, 12, 3), {"all": "etagere"})); E.append(box((2, 9, 13, 14, 12, 14), {"all": "etagere"}))
    E.append(box((6, 9, 6, 10, 11, 10), {"all": "caisse"})); E.append(box((10.5, 9, 10.5, 13, 10.5, 13), {"all": "or"}))
    E.append(box((3, 9, 10.5, 5.5, 10.5, 13), {"all": "caisse"}))
    write_model("depot_regional", tex, E)


# ---------------------------------------------------------------- skins 64x32 (mise en page armure)
SKIN = rgb("e0ac86")
SKIN_D = rgb("c18862")
BLACK = rgb("1c1216")
WHITE = rgb("f4f4f4")


class Skin:
    def __init__(self):
        self.im = Image.new("RGBA", (64, 32), (0, 0, 0, 0))

    def box_faces(self, u, v, w, h, d):
        return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
                "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}

    def fill(self, rect, fn):
        x0, y0, w, h = rect
        for j in range(h):
            for i in range(w):
                c = fn(i, j, w, h)
                if c:
                    self.im.putpixel((x0 + i, y0 + j), c)


def noise(c, rnd, a=0.05):
    return shade(c, 1 + rnd.uniform(-a, a))


def skin_legionnaire():
    rnd = random.Random(101)
    s = Skin()
    N = lambda c: noise(c, rnd)

    # TÊTE : casque d'acier à cimier rouge, visage ouvert, paragnathides
    f = s.box_faces(0, 0, 8, 8, 8)
    s.fill(f["top"], lambda i, j, w, h: N(RED["m"]) if 3 <= i <= 4 else N(STEEL["m"]))
    s.fill(f["bottom"], lambda i, j, w, h: N(STEEL["d"]))
    s.fill(f["back"], lambda i, j, w, h: N(RED["m"]) if (3 <= i <= 4 and j < 6) else N(STEEL["m"]) if j < 6 else N(SKIN_D))
    s.fill(f["right"], lambda i, j, w, h: N(STEEL["m"]) if (j < 3 or i > 5 or (j < 6 and i > 4)) else N(SKIN))
    s.fill(f["left"], lambda i, j, w, h: N(STEEL["m"]) if (j < 3 or i < 2 or (j < 6 and i < 3)) else N(SKIN))

    def face(i, j, w, h):
        if j < 3:
            return N(STEEL["l"]) if j == 0 else N(STEEL["m"])
        if j == 3:
            return GOLD["g"]                                    # bandeau doré
        if i in (3, 4) and j < 6:
            return N(STEEL["m"])                                # nasal
        if j == 5 and i in (1, 2, 5, 6):
            return BLACK if i in (2, 5) else WHITE
        if j >= 6 and 2 <= i <= 5 and j == 6 and i in (3, 4):
            return SKIN_D
        if j == 7 and 2 <= i <= 5:
            return rgb("6a3a2a")
        return N(SKIN) if 1 <= i <= 6 else N(STEEL["m"])
    s.fill(f["front"], face)

    # CORPS : tunique rouge, ceinture dorée, cuirasse d'acier avec couronne
    f = s.box_faces(16, 16, 8, 12, 4)
    s.fill(f["top"], lambda i, j, w, h: N(STEEL["m"]))
    s.fill(f["bottom"], lambda i, j, w, h: N(BROWN["d"]))
    def torso(i, j, w, h, front=True):
        if j == 9:
            return GOLD["l"] if i % 3 else GOLD["g"]            # ceinture
        if j == 10:
            return GOLD["d"]
        if j >= 11:
            return N(RED["d"])                                  # pteruges
        if j < 8:
            c = N(STEEL["m"]) if (j % 3 != 2) else N(STEEL["d"])  # plates horizontales
            if front and 2 <= i <= 5 and 1 <= j <= 4:
                return GOLD["l"] if CROWN_FRONT.get((i - 2, j - 1)) else N(STEEL["l"])
            return c
        return N(RED["m"])
    CROWN_FRONT = {(0, 0): 1, (3, 0): 1, (1, 1): 1, (2, 1): 1, (0, 2): 1, (1, 2): 1, (2, 2): 1, (3, 2): 1, (0, 3): 1, (1, 3): 1, (2, 3): 1, (3, 3): 1}
    s.fill(f["front"], lambda i, j, w, h: torso(i, j, w, h, True))
    s.fill(f["back"], lambda i, j, w, h: (RED["l"] if (j < 8 and i in (3, 4)) else torso(i, j, w, h, False)))  # cape rouge au dos
    s.fill(f["right"], lambda i, j, w, h: torso(i, j, w, h, False))
    s.fill(f["left"], lambda i, j, w, h: torso(i, j, w, h, False))

    # BRAS : épaulière acier, manche rouge, brassard acier, main
    def arm(i, j, w, h):
        if j < 3:
            return N(STEEL["l"]) if j == 0 else N(STEEL["m"])
        if j == 3:
            return GOLD["g"]
        if j < 7:
            return N(RED["m"])
        if j < 10:
            return N(STEEL["m"]) if j != 7 else GOLD["d"]
        return N(SKIN)
    f = s.box_faces(40, 16, 4, 12, 4)
    for k in ("top", "bottom"):
        s.fill(f[k], lambda i, j, w, h: N(STEEL["m"]) if k == "top" else N(SKIN))
    for k in ("front", "back", "right", "left"):
        s.fill(f[k], arm)

    # JAMBES : cuir brun, jambières d'acier, bottes
    def leg(i, j, w, h):
        if j < 2:
            return N(RED["d"])
        if j < 8:
            return N(BROWN["m"])
        if j < 10:
            return N(STEEL["m"]) if j == 8 else N(STEEL["d"])
        return N(BROWN["d"])
    f = s.box_faces(0, 16, 4, 12, 4)
    s.fill(f["top"], lambda i, j, w, h: N(BROWN["m"]))
    s.fill(f["bottom"], lambda i, j, w, h: N(BROWN["d"]))
    for k in ("front", "back", "right", "left"):
        s.fill(f[k], leg)
    return s.im


def skin_archer():
    rnd = random.Random(202)
    s = Skin()
    N = lambda c: noise(c, rnd)
    GREEN = {"d": rgb("1f4a24"), "m": rgb("2f6a34"), "l": rgb("4a9050")}
    LEAF = rgb("7ab05a")

    # TÊTE : capuchon vert forêt, ouverture sur le visage, masque de tissu sombre
    f = s.box_faces(0, 0, 8, 8, 8)
    for k in ("top", "back", "right", "left"):
        s.fill(f[k], lambda i, j, w, h, k=k: N(GREEN["m"]) if (i + j) % 5 else N(GREEN["l"]))
    s.fill(f["bottom"], lambda i, j, w, h: N(GREEN["d"]))
    s.fill(f["right"], lambda i, j, w, h: N(GREEN["d"]) if i < 2 else (N(SKIN) if (i > 5 and 2 <= j <= 5) else N(GREEN["m"])))
    s.fill(f["left"], lambda i, j, w, h: N(GREEN["d"]) if i > 5 else (N(SKIN) if (i < 2 and 2 <= j <= 5) else N(GREEN["m"])))

    def face(i, j, w, h):
        if j == 0 or i in (0, 7):
            return N(GREEN["d"])                                # bord du capuchon
        if j == 1:
            return N(GREEN["m"])
        if 2 <= j <= 5 and 1 <= i <= 6:
            if j == 3 and i in (2, 5):
                return BLACK if True else WHITE
            if j == 3 and i in (1, 6):
                return WHITE
            return N(SKIN)
        if j >= 6:
            return N(GREEN["d"]) if (i + j) % 2 else N(rgb("2a2f2a"))  # cache-nez
        return N(GREEN["m"])
    s.fill(f["front"], face)
    # plume de chasseur sur le capuchon
    for (x, y) in ((11, 3), (12, 2), (12, 1)):
        s.im.putpixel((x, y), rgb("d84a3a"))

    # CORPS : chemise verte, gilet de cuir, bandoulière de carquois
    f = s.box_faces(16, 16, 8, 12, 4)
    s.fill(f["top"], lambda i, j, w, h: N(GREEN["m"]))
    s.fill(f["bottom"], lambda i, j, w, h: N(BROWN["d"]))
    def torso(i, j, w, h, front=True):
        if j == 9:
            return BROWN["d"] if i not in (3, 4) else GOLD["l"]  # ceinture + boucle
        if j == 10:
            return BROWN["d"]
        if j >= 11:
            return N(GREEN["d"])
        if front and 3 <= i <= 4 and j < 9:
            return N(BROWN["l"]) if j % 2 == 0 else N(BROWN["d"])   # laçage
        if front and abs(i - (j - 1)) <= 0 and j < 8:
            return N(rgb("3a2412"))                                 # bandoulière en diagonale
        if (i < 3 or i > 4) and j >= 1:
            return N(BROWN["m"]) if (front or True) else N(GREEN["m"])
        return N(GREEN["m"])
    s.fill(f["front"], lambda i, j, w, h: torso(i, j, w, h, True))
    s.fill(f["back"], lambda i, j, w, h: (N(rgb("3a2412")) if (i - (7 - j) == 0 and j < 8) else N(BROWN["m"]) if j > 0 else N(GREEN["m"])) if j < 9 else torso(i, j, w, h, False))
    s.fill(f["right"], lambda i, j, w, h: torso(i, j, w, h, False))
    s.fill(f["left"], lambda i, j, w, h: torso(i, j, w, h, False))
    # carquois dans le dos : plumes
    for (x, y) in ((37, 21), (38, 22), (36, 22), (37, 23)):
        s.im.putpixel((x, y), rgb("e8e2c8"))

    # BRAS : manches vertes, bracelet de cuir, mains
    def arm(i, j, w, h):
        if j < 7:
            return N(GREEN["m"]) if j > 0 else N(GREEN["l"])
        if j < 10:
            return N(BROWN["m"]) if j != 7 else BROWN["d"]
        return N(SKIN)
    f = s.box_faces(40, 16, 4, 12, 4)
    s.fill(f["top"], lambda i, j, w, h: N(GREEN["l"]))
    s.fill(f["bottom"], lambda i, j, w, h: N(SKIN))
    for k in ("front", "back", "right", "left"):
        s.fill(f[k], arm)

    # JAMBES : pantalon olive, bandes de cuir, bottes brunes souples
    def leg(i, j, w, h):
        if j < 7:
            return N(rgb("4a5530"))
        if j < 9:
            return N(rgb("4a5530")) if j == 7 else BROWN["d"]
        return N(BROWN["m"]) if j < 11 else N(BROWN["d"])
    f = s.box_faces(0, 16, 4, 12, 4)
    s.fill(f["top"], lambda i, j, w, h: N(rgb("4a5530")))
    s.fill(f["bottom"], lambda i, j, w, h: N(BROWN["d"]))
    for k in ("front", "back", "right", "left"):
        s.fill(f[k], leg)
    return s.im


# ---------------------------------------------------------------- orchestration
def main():
    # objets
    save(make_coin(), "item/couronne.png")
    save(make_lingot(), "item/lingot_imperial.png")
    save(make_bourse(), "item/bourse.png")
    save(make_sac(), "item/sac_de_couronnes.png")
    save(make_coffret(), "item/coffret_imperial.png")
    save(make_tresor(), "item/tresor_du_dragon.png")
    # HUD
    save(make_coin(), "gui/hud_coin.png")
    save(make_bourse(), "gui/hud_purse.png")
    save(make_hud_bank(), "gui/hud_bank.png")
    save(make_hud_region(), "gui/hud_region.png")
    save(make_hud_influence(), "gui/hud_influence.png")

    # caisses
    bt(planks(rgb("5a3a1c"), 4, vertical=True, seed=50), "caisse_cadre")
    for kind, d in CRATE_KINDS.items():
        bt(d["panel"](), f"caisse_{kind}")
        bt(d["top"](), f"caisse_{kind}_top")
        bt(d["bump"](), f"caisse_{kind}_relief")
        crate_model(kind)
    # poste commercial
    bt(stone(rgb("85878d"), 60), "poste_socle")
    bt(planks(rgb("6a4424"), 3, vertical=True, seed=61), "poste_bois")
    bt(sign_board(True, 62), "poste_panneau_d")
    bt(sign_board(False, 63), "poste_panneau_g")
    bt(lantern(), "poste_lanterne")
    bt(planks(rgb("b88a4c"), 4, seed=64), "poste_caisse")
    bt(barrel_side(), "poste_tonneau")
    bt(barrel_top(), "poste_tonneau_top")
    poste_model()
    # guichet
    bt(purple_panel(), "guichet_pourpre")
    bt(dark_purple(), "guichet_sombre")
    bt(marble(), "guichet_marbre")
    bt(gold_tex(), "guichet_or")
    guichet_model()
    # banque
    bt(stone(rgb("c9c1b0"), 70, speck=0.04), "banque_pierre")
    bt(column(), "banque_colonne")
    bt(vault_door(), "banque_porte")
    bt(metal_plate(GOLD["g"], 71), "banque_or")
    bt(stone(rgb("5a6a8a"), 72, blocks=False), "banque_toit")
    banque_model()
    # comptoir
    bt(planks(rgb("94602c"), 3, seed=80), "depot_bois")
    bt(stripes(rgb("c0342c"), rgb("f0e6cc"), 4, True), "depot_auvent")
    bt(shelf(), "depot_etagere")
    bt(metal_plate(GOLD["g"], 81), "depot_or")
    bt(planks(rgb("b88a4c"), 2, seed=82), "depot_caisse")
    depot_model()

    # skins des troupes
    save(skin_legionnaire(), "entity/war/legionnaire.png")
    save(skin_archer(), "entity/war/archer.png")

    # anciennes textures devenues inutiles
    for old in ("block/caisse_bottom.png", "block/banque_regionale.png", "block/banque_regionale_top.png", "block/banque_regionale_front.png",
                "block/guichet_imperial.png", "block/guichet_imperial_top.png", "block/comptoir_regional.png",
                "block/comptoir_regional_top.png", "block/depot_regional_side.png", "block/depot_regional_front.png",
                "block/depot_regional_top.png", "block/depot_regional_bottom.png", "block/relais_commercial_side.png",
                "block/relais_commercial_front.png", "block/relais_commercial_top.png", "block/relais_commercial_bottom.png"):
        p = TEX / old
        if p.exists():
            p.unlink()
    print("ok")


if __name__ == "__main__":
    main()
