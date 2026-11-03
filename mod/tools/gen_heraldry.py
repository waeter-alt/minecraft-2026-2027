"""
Generates L'Interregne's heraldry: banner and shield pattern textures, banner_pattern JSONs,
the no_item_required tag and the lang entries (fr_fr, en_us).

Every emblem is drawn with plain geometry on an 18x24 logical grid, supersampled then thresholded
so the result stays crisp at Minecraft's banner resolution. Run from anywhere:

    python mod/tools/gen_heraldry.py [preview.png]
"""
import json
import math
import os
import random
import sys

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "interregne")
DATA = os.path.join(ROOT, "data")

W, H = 18, 24      # logical emblem grid
SS = 12            # supersampling factor

# Banner front face: x 1..20, y 1..40 (vanilla layout). Emblem sits at x 2..19, y 9..32, like vanilla emblems.
BANNER_X, BANNER_Y = 2, 9
# Shield front face: x 2..11, y 2..21. Emblem at half scale (9x12), centred.
SHIELD_X, SHIELD_Y = 2, 6


class Pen:
    """Draws in logical units on a supersampled canvas."""

    def __init__(self):
        self.img = Image.new("L", (W * SS, H * SS), 0)
        self.d = ImageDraw.Draw(self.img)

    def _p(self, pts):
        return [(x * SS, y * SS) for x, y in pts]

    def poly(self, pts, on=True):
        self.d.polygon(self._p(pts), fill=255 if on else 0)

    def rect(self, x0, y0, x1, y1, on=True):
        self.d.rectangle([x0 * SS, y0 * SS, x1 * SS - 1, y1 * SS - 1], fill=255 if on else 0)

    def circle(self, cx, cy, r, on=True):
        self.d.ellipse([(cx - r) * SS, (cy - r) * SS, (cx + r) * SS, (cy + r) * SS], fill=255 if on else 0)

    def ellipse(self, cx, cy, rx, ry, angle=0.0, on=True):
        pts = []
        for i in range(48):
            t = 2 * math.pi * i / 48
            x, y = rx * math.cos(t), ry * math.sin(t)
            c, s = math.cos(angle), math.sin(angle)
            pts.append((cx + x * c - y * s, cy + x * s + y * c))
        self.poly(pts, on)

    def line(self, pts, width, on=True):
        self.d.line(self._p(pts), fill=255 if on else 0, width=int(width * SS), joint="curve")
        for x, y in (pts[0], pts[-1]):
            self.circle(x, y, width / 2, on)

    def mask(self, w, h):
        """Downsamples to w x h and thresholds to a hard mask."""
        small = self.img.resize((w, h), Image.BOX)
        return small.point(lambda v: 255 if v >= 120 else 0)


# --- Emblems -----------------------------------------------------------------------------------

def couronne(p):
    p.poly([(2, 19), (1.5, 8), (5.5, 12.5), (9, 5), (12.5, 12.5), (16.5, 8), (16, 19)])
    p.circle(1.8, 7.2, 1.6)
    p.circle(9, 4.2, 1.8)
    p.circle(16.2, 7.2, 1.6)
    p.rect(1, 19, 17, 22)
    for x in (4, 8, 12):          # jewels cut into the band
        p.rect(x + 0.5, 15.5, x + 2.5, 17.5, on=False)


def etoile(p):
    cx, cy, ro, ri = 9, 12.5, 9.2, 3.9
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        r = ro if i % 2 == 0 else ri
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    p.poly(pts)


def region_bois(p):
    p.poly([(7.5, 14), (10.5, 14), (11, 20.5), (13.5, 23), (4.5, 23), (7, 20.5)])  # trunk and roots
    p.circle(9, 6.5, 5)
    p.circle(4.8, 10.5, 4)
    p.circle(13.2, 10.5, 4)
    p.circle(9, 11.5, 4)
    p.line([(9, 15.5), (6, 12.5)], 1, on=False)      # branches showing through the foliage
    p.line([(9, 14.5), (12, 11.5)], 1, on=False)


def region_brique(p):
    top, rows, bh, gap = 5, 4, 3, 1
    for r in range(rows):
        y = top + r * (bh + gap)
        offset = 0 if r % 2 == 0 else -3
        x = offset
        while x < W:
            x0, x1 = max(x, 0.5), min(x + 5, W - 0.5)
            if x1 - x0 >= 1.5:
                p.rect(x0, y, x1, y + bh)
            x += 6
    # crenellated cap
    for x in (0.5, 6.5, 12.5):
        p.rect(x, 1, x + 5, 4)


def region_ble(p):
    tie = (9, 15.5)
    for k in range(5):
        ang = (k - 2) * 0.42
        s, c = math.sin(ang), math.cos(ang)
        ex, ey = tie[0] + 10 * s, tie[1] - 10 * c
        p.ellipse(ex, ey, 1.2, 3.3, ang)                        # ear
        p.line([(ex - 3 * s, ey + 3 * c), tie], 0.9)             # stalk
    for x in (4.2, 6.6, 9, 11.4, 13.8):                          # stalks below the tie
        p.line([tie, (x, 23.2)], 1.1)
    p.rect(6.4, 14.4, 11.6, 16.8)                                # tie


def region_pierre(p):
    p.poly([(3, 22), (11.5, 3), (18, 22)])
    p.line([(0, 22.5), (5, 10.5), (10.5, 22.5)], 2.2, on=False)  # gap behind the small peak
    p.poly([(0, 22), (5, 11.5), (10, 22)])
    p.line([(9.6, 7.8), (11.5, 9.6), (13.2, 8)], 1.1, on=False)   # snow cap
    p.rect(0, 22, 18, 23.8)


def region_minerai(p):
    p.line([(2.5, 22), (12, 8.5)], 2.4)                         # handle
    head = []
    for i in range(13):
        a = math.radians(205 + i * 10)
        head.append((11.5 + 8.5 * math.cos(a), 13.5 + 8.5 * math.sin(a)))
    p.line(head, 2.8)                                            # curved head
    p.line([head[0], (head[0][0] - 0.6, head[0][1] + 2)], 1.6)    # pointed ends
    p.line([head[-1], (head[-1][0] + 2, head[-1][1] + 0.6)], 1.6)
    for cx, cy, r in ((13.5, 17.5, 2.8), (16, 21.5, 2.1), (10.8, 21.5, 2.1)):       # ore cluster
        p.poly([(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)])


def dragon(p):
    # wing, raised behind the body
    p.poly([(7, 12), (10, 5), (17.5, 0.5), (16.5, 5), (17.5, 6.5), (15.5, 9), (16, 11), (13, 12.5), (12.5, 14)])
    p.line([(10, 6), (15.5, 3)], 0.6, on=False)
    # body
    p.ellipse(9.5, 15, 5, 3, -0.15)
    # neck and head, facing left
    p.line([(7, 14), (4.5, 9), (4.5, 5.5)], 2.6)
    p.poly([(6, 3.5), (3.5, 2.8), (0.2, 4.2), (0.2, 5.3), (2.6, 5.6), (0.6, 6.7), (1.2, 7.4), (4, 7.1), (6.2, 7)])
    p.poly([(5.2, 3.6), (8.5, 0.6), (6.8, 4)])                 # horn
    p.rect(2.6, 4.2, 3.6, 5.2, on=False)                         # eye
    # legs
    p.poly([(6, 16.5), (8, 16.5), (7.5, 20), (8.8, 21), (5.8, 21), (6.3, 19.5)])
    p.poly([(11.5, 16.5), (13.5, 16), (13.5, 20), (14.8, 21), (11.8, 21), (12.3, 19.5)])
    # tail, curling down with a spade tip
    p.line([(14, 15.5), (16.5, 18), (16, 21), (12, 22.5), (8, 22.8)], 1.5)
    p.poly([(9, 22.8), (6.2, 21), (4.5, 23), (6.2, 24)])
    # spines along the back
    for x, y in ((8.5, 11.7), (11, 11.8)):
        p.poly([(x - 1, y + 0.5), (x, y - 1.5), (x + 1, y + 0.5)])


def chaines(p):
    def chain(a, b, gap):
        (x0, y0), (x1, y1) = a, b
        ang = math.atan2(y1 - y0, x1 - x0)
        n = 4
        for i in range(n):
            t = (i + 0.5) / n
            cx, cy = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            if gap:
                p.ellipse(cx, cy, 4.2, 3.2, ang, on=False)
            else:
                p.ellipse(cx, cy, 3.9, 2.5, ang)
                p.ellipse(cx, cy, 2.3, 0.9, ang, on=False)

    chain((1, 1), (17, 23.5), False)
    chain((17, 1), (1, 23.5), True)
    chain((17, 1), (1, 23.5), False)


def engrenage(p):
    cx, cy, teeth = 9, 12.5, 8
    for i in range(teeth):
        a = 2 * math.pi * i / teeth + math.pi / 8
        c, s = math.cos(a), math.sin(a)
        pts = []
        for along, side in ((5.5, -1.7), (8.9, -1.3), (8.9, 1.3), (5.5, 1.7)):
            pts.append((cx + along * c - side * s, cy + along * s + side * c))
        p.poly(pts)
    p.circle(cx, cy, 6.6)
    p.circle(cx, cy, 3.1, on=False)


def tour(p):
    p.poly([(4.5, 8), (13.5, 8), (14.5, 23), (3.5, 23)])        # body, slightly flared
    p.rect(2, 5, 16, 9)                                          # parapet
    for x in (2, 7.5, 13):
        p.rect(x, 1, x + 3, 5)                                   # merlons
    p.rect(3.5, 8.5, 14.5, 9.5, on=False)                        # corbel line
    p.rect(8.3, 11, 9.7, 14.5, on=False)                         # arrow slit
    p.rect(7, 18.5, 11, 23, on=False)                            # door
    p.circle(9, 18.5, 2, on=False)


PATTERNS = [
    # id, drawer, fr name, fr gender ("m", "f", "fp"), en name
    ("couronne", couronne, "Couronne impériale", "f", "Imperial Crown"),
    ("etoile", etoile, "Étoile d'Influence", "f", "Star of Influence"),
    ("region_bois", region_bois, "Arbre du Bois", "m", "Timber Tree"),
    ("region_brique", region_brique, "Mur de Brique", "m", "Brick Wall"),
    ("region_ble", region_ble, "Gerbe de Blé", "f", "Wheat Sheaf"),
    ("region_pierre", region_pierre, "Mont de Pierre", "m", "Stone Mountain"),
    ("region_minerai", region_minerai, "Pioche du Minerai", "f", "Ore Pickaxe"),
    ("dragon", dragon, "Dragon", "m", "Dragon"),
    ("chaines", chaines, "Chaînes croisées", "fp", "Crossed Chains"),
    ("engrenage", engrenage, "Engrenage", "m", "Gear"),
    ("tour", tour, "Tour", "f", "Castle Tower"),
]

COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
          "purple", "blue", "brown", "green", "red", "black"]
EN_COLORS = ["White", "Orange", "Magenta", "Light Blue", "Yellow", "Lime", "Pink", "Gray", "Light Gray", "Cyan",
             "Purple", "Blue", "Brown", "Green", "Red", "Black"]
FR_COLORS = {
    "m": ["blanc", "orange", "magenta", "bleu clair", "jaune", "vert clair", "rose", "gris", "gris clair", "cyan",
          "violet", "bleu", "marron", "vert", "rouge", "noir"],
    "f": ["blanche", "orange", "magenta", "bleu clair", "jaune", "vert clair", "rose", "grise", "gris clair", "cyan",
          "violette", "bleue", "marron", "verte", "rouge", "noire"],
    "fp": ["blanches", "orange", "magenta", "bleu clair", "jaunes", "vert clair", "roses", "grises", "gris clair",
           "cyan", "violettes", "bleues", "marron", "vertes", "rouges", "noires"],
}


def textured(mask, seed):
    """White mask with vanilla-like grain, transparent elsewhere."""
    rnd = random.Random(seed)
    out = Image.new("RGBA", mask.size, (0, 0, 0, 0))
    px, m = out.load(), mask.load()
    for y in range(mask.size[1]):
        for x in range(mask.size[0]):
            if m[x, y]:
                v = rnd.randint(234, 250)
                px[x, y] = (v, v, v, 255)
    return out


def build(pid, drawer):
    pen = Pen()
    drawer(pen)
    big = textured(pen.mask(W, H), pid)
    banner = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    banner.paste(big, (BANNER_X, BANNER_Y))
    back = big.transpose(Image.FLIP_LEFT_RIGHT)          # back face is the mirror of the front (x 22..41)
    banner.paste(back, (42 - (BANNER_X + W - 1), BANNER_Y))
    shield = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    shield.paste(textured(pen.mask(W // 2, H // 2), pid + "s"), (SHIELD_X, SHIELD_Y))
    return banner, shield


def main():
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    for sub in ("banner", "shield"):
        os.makedirs(os.path.join(ASSETS, "textures", "entity", sub), exist_ok=True)
    os.makedirs(os.path.join(DATA, "interregne", "banner_pattern"), exist_ok=True)
    os.makedirs(os.path.join(DATA, "minecraft", "tags", "banner_pattern"), exist_ok=True)

    lang = {"fr_fr": {}, "en_us": {}}
    sheet = Image.new("RGBA", (len(PATTERNS) * 26 * 6, 46 * 6 + 30 * 6), (40, 40, 48, 255))
    for i, (pid, drawer, fr, gender, en) in enumerate(PATTERNS):
        banner, shield = build(pid, drawer)
        banner.save(os.path.join(ASSETS, "textures", "entity", "banner", pid + ".png"))
        shield.save(os.path.join(ASSETS, "textures", "entity", "shield", pid + ".png"))
        key = "block.interregne.banner." + pid
        with open(os.path.join(DATA, "interregne", "banner_pattern", pid + ".json"), "w", encoding="utf-8") as f:
            json.dump({"asset_id": "interregne:" + pid, "translation_key": key}, f, indent=2)
            f.write("\n")
        for c, color in enumerate(COLORS):
            lang["fr_fr"][key + "." + color] = fr + " " + FR_COLORS[gender][c]
            lang["en_us"][key + "." + color] = EN_COLORS[c] + " " + en

        if preview:
            base = Image.new("RGBA", (20, 40), (40, 60, 140, 255))
            tint = Image.new("RGBA", (20, 40), (235, 200, 70, 255))
            face = banner.crop((1, 1, 21, 41))
            base.paste(tint, (0, 0), face)
            sheet.paste(base.resize((120, 240), Image.NEAREST), (i * 156 + 18, 18))
            sh = Image.new("RGBA", (10, 20), (140, 30, 30, 255))
            sh.paste(Image.new("RGBA", (10, 20), (240, 240, 240, 255)), (0, 0), shield.crop((2, 2, 12, 22)))
            sheet.paste(sh.resize((60, 120), Image.NEAREST), (i * 156 + 48, 290))

    with open(os.path.join(DATA, "minecraft", "tags", "banner_pattern", "no_item_required.json"), "w", encoding="utf-8") as f:
        json.dump({"replace": False, "values": ["interregne:" + p[0] for p in PATTERNS]}, f, indent=2)
        f.write("\n")

    for code, entries in lang.items():
        path = os.path.join(ASSETS, "lang", code + ".json")
        with open(path, encoding="utf-8") as f:
            current = json.load(f)
        current = {k: v for k, v in current.items() if not k.startswith("block.interregne.banner.")}
        current.update(entries)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(current, f, indent=2, ensure_ascii=False)
            f.write("\n")

    if preview:
        sheet.save(preview)


if __name__ == "__main__":
    main()
