"""
Génère les textures de la couronne brisée de l'écran d'attente en assaut (client/war/AssaultDeathScreen).

    python -I -X utf8 mod/tools/gen_crown_textures.py

Écrit dans mod/src/main/resources/assets/interregne/textures/gui/crown/ :
  shard_0..5.png   éclats ternis, chacun sur la toile entière (64x48) : posés au même endroit, ils s'emboîtent
  gold.png         la couronne entière, dorée
  cracks.png       les fissures sombres, aux jointures des éclats
  glow.png         les mêmes fissures, lumineuses, avec un halo
  shine_0..9.png   le reflet qui balaie la couronne dorée, image par image
  slot.png         la silhouette en pointillés du présentoir vide
  pedestal.png     le présentoir (coussin + pied), 64x18
Puis affiche le centre de chaque éclat (pivot de rotation, à recopier dans AssaultDeathScreen.SHARD_CENTERS).
"""
from pathlib import Path

from PIL import Image, ImageDraw

W, H = 64, 48
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/interregne/textures/gui/crown"

# Silhouette : bandeau en bas, cinq pointes, symétrique autour de x = 32
OUTLINE = [(6, 43), (6, 15), (14, 27), (18, 9), (25, 25), (32, 4), (39, 25), (46, 9), (50, 27), (58, 15), (58, 43)]
ORBS = [(6, 13), (18, 7), (32, 3), (46, 7), (58, 13)]
BAND_TOP, BAND_BOTTOM = 31, 43
GEMS = [((32, 37), 3, "ruby"), ((19, 37), 2, "sapphire"), ((45, 37), 2, "emerald")]
SEEDS = [(12, 24), (32, 15), (52, 24), (13, 39), (33, 37), (52, 39)]

GOLD = {"outline": (90, 56, 10), "dark": (168, 116, 26), "mid": (224, 168, 46), "light": (247, 211, 92), "high": (255, 242, 168),
        "ruby": (200, 30, 40), "ruby_l": (255, 110, 110), "sapphire": (40, 80, 210), "sapphire_l": (130, 170, 255),
        "emerald": (30, 150, 70), "emerald_l": (120, 230, 140)}
TARNISHED = {"outline": (46, 40, 34), "dark": (88, 78, 68), "mid": (122, 108, 92), "light": (148, 134, 116), "high": (170, 158, 140),
             "ruby": (96, 60, 58), "ruby_l": (124, 92, 88), "sapphire": (64, 70, 92), "sapphire_l": (92, 100, 120),
             "emerald": (62, 84, 66), "emerald_l": (90, 112, 92)}


def mask():
    img = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(img)
    d.polygon(OUTLINE, fill=255)
    for (x, y) in ORBS:
        d.ellipse((x - 2, y - 2, x + 2, y + 2), fill=255)
    return {(x, y) for y in range(H) for x in range(W) if img.getpixel((x, y)) > 0}


def shade(px, inside):
    """Palette key of each pixel: outline at the edge, facets on the points (lit left), rims on the band, gems."""
    keys = {}
    tips = [x for (x, _) in ORBS]
    for (x, y) in px:
        edge = any((x + dx, y + dy) not in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            keys[(x, y)] = "outline"
            continue
        if any((x - ox) ** 2 + (y - oy) ** 2 <= 4 for (ox, oy) in ORBS):
            orb = min(ORBS, key=lambda o: (x - o[0]) ** 2 + (y - o[1]) ** 2)
            keys[(x, y)] = "high" if (x < orb[0] and y < orb[1]) else "light"
            continue
        if y >= BAND_TOP:
            if y <= BAND_TOP + 1:
                keys[(x, y)] = "light"
            elif y >= BAND_BOTTOM - 1:
                keys[(x, y)] = "dark"
            else:
                keys[(x, y)] = "high" if y == BAND_TOP + 2 and x % 7 == 3 else "mid"
            continue
        tip = min(tips, key=lambda t: abs(x - t))
        keys[(x, y)] = "light" if x < tip else ("mid" if x == tip else "dark")
    for ((gx, gy), r, gem) in GEMS:
        for (x, y) in px:
            dist = (x - gx) ** 2 + (y - gy) ** 2
            if dist <= r * r:
                keys[(x, y)] = gem + "_l" if (x < gx and y < gy) else gem
            elif dist <= (r + 1) * (r + 1) and keys.get((x, y)) != "outline":
                keys[(x, y)] = "dark"
    return keys


def paint(keys, palette, only=None):
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    for (x, y), k in keys.items():
        if only is None or (x, y) in only:
            img.putpixel((x, y), palette[k] + (255,))
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    px = mask()
    keys = shade(px, px)
    owner = {p: min(range(len(SEEDS)), key=lambda i: (p[0] - SEEDS[i][0]) ** 2 + (p[1] - SEEDS[i][1]) ** 2) for p in px}

    paint(keys, GOLD).save(OUT / "gold.png")
    for i in range(len(SEEDS)):
        mine = {p for p in px if owner[p] == i}
        paint(keys, TARNISHED, mine).save(OUT / f"shard_{i}.png")
        cx = sum(p[0] for p in mine) / len(mine) + 0.5
        cy = sum(p[1] for p in mine) / len(mine) + 0.5
        print(f"shard {i}: centre {cx:.1f}, {cy:.1f}")

    seam = {(x, y) for (x, y) in px
            if any((x + dx, y + dy) in px and owner[(x + dx, y + dy)] != owner[(x, y)] for dx, dy in ((1, 0), (0, 1)))}
    cracks = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    for (x, y) in px:
        near = any((x + dx, y + dy) in seam for dx in (-1, 0, 1) for dy in (-1, 0, 1))
        if near and (x, y) not in seam:
            glow.putpixel((x, y), (255, 214, 110, 150))
    for p in seam:
        cracks.putpixel(p, (24, 20, 16, 255))
        glow.putpixel(p, (255, 250, 220, 255))
    cracks.save(OUT / "cracks.png")
    glow.save(OUT / "glow.png")

    frames = 10
    for f in range(frames):
        img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        k = -12 + f * (W + H + 12) / (frames - 1)
        for (x, y) in px:
            d = (x - y * 0.6) - k
            if -5 <= d <= 0:
                img.putpixel((x, y), (255, 255, 240, 200 if d >= -2 else 110))
        img.save(OUT / f"shine_{f}.png")

    slot = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    for (x, y) in px:
        if keys[(x, y)] == "outline" and (x + y) % 3 != 0:
            slot.putpixel((x, y), (255, 240, 200, 110))
    slot.save(OUT / "slot.png")

    ped = Image.new("RGBA", (W, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(ped)
    d.rounded_rectangle((2, 0, 61, 6), radius=3, fill=(110, 22, 30, 255), outline=(52, 10, 14, 255))
    d.line((5, 1, 58, 1), fill=(160, 44, 52, 255))
    for x in (8, 20, 32, 44, 56):
        ped.putpixel((x, 6), (214, 170, 60, 255))
    d.polygon([(18, 7), (46, 7), (42, 13), (22, 13)], fill=(92, 64, 40, 255), outline=(44, 28, 16, 255))
    d.rectangle((14, 13, 49, 17), fill=(74, 50, 30, 255), outline=(44, 28, 16, 255))
    d.line((15, 14, 48, 14), fill=(116, 84, 52, 255))
    ped.save(OUT / "pedestal.png")


if __name__ == "__main__":
    main()
