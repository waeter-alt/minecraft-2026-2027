# [claude-build]
"""texture_leviathan.py - genere la texture du Leviathan (256 x 256) : ecailles teal et bleu nuit a taches de bioluminescence, ventre clair, nageoires translucides, dents, œil ambre, perle nacree.
Zones lues par les cubes (texOffs, voir LeviathanModele) :
  ecailles : (0, 0) a (256, 128)   ventre : (0, 130) a (128, 192)   nageoires : (130, 130) a (256, 192)
  dents : (0, 196) a (32, 212)     œil ambre : (40, 196) a (72, 212)   perle : (80, 196) a (130, 246)
  test-local\\venv\\Scripts\\python.exe atelier\\scripts\\texture_leviathan.py
"""
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

SORTIE = Path(__file__).resolve().parent.parent / "donjon/arene/src/main/resources/assets/grappin/textures/entity/leviathan.png"
random.seed(21)
im = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
d = ImageDraw.Draw(im)


def teinte(c, t):
    return tuple(max(0, min(255, int(v * t))) for v in c[:3]) + (255,)


def melange(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


# ecailles : rangees d'ecailles en arcs qui se chevauchent, du teal au bleu nuit, bord clair
TEAL, NUIT = (22, 104, 112), (14, 38, 78)
d.rectangle([0, 0, 255, 127], fill=teinte(NUIT, 1.0))
for rang in range(0, 132, 6):
    for col in range(-4 if (rang // 6) % 2 else 0, 260, 8):
        t = 0.5 + 0.5 * math.sin(col * 0.05 + rang * 0.08)
        c = melange(TEAL, NUIT, t * 0.8 + random.random() * 0.15)
        d.pieslice([col, rang - 4, col + 8, rang + 8], 0, 180, fill=c, outline=teinte(NUIT, 0.7))
        d.arc([col + 1, rang - 3, col + 7, rang + 6], 200, 340, fill=teinte(TEAL, 1.5))
for _ in range(110):                                               # points de bioluminescence
    x, y = random.randint(2, 252), random.randint(2, 124)
    d.ellipse([x - 1, y - 1, x + 1, y + 1], fill=(120, 240, 230, 255))
    d.point((x, y), fill=(220, 255, 250, 255))

# ventre : creme-teal a bandes transversales
d.rectangle([0, 130, 127, 191], fill=(182, 216, 202, 255))
for y in range(132, 192, 4):
    d.line([(0, y), (127, y)], fill=teinte((182, 216, 202), 0.78))
for _ in range(60):
    x, y = random.randint(0, 124), random.randint(132, 188)
    d.point((x, y), fill=teinte((182, 216, 202), 1.12))

# nageoires : membrane teal clair, rayons plus sombres
d.rectangle([130, 130, 255, 191], fill=(46, 150, 158, 255))
for x in range(130, 256, 5):
    d.line([(x, 130), (x + 2, 191)], fill=(14, 70, 96, 255))
for y in range(134, 192, 9):
    d.arc([130, y - 20, 255, y + 8], 200, 340, fill=(90, 205, 205, 255))

# dents (cube fin : 32 x 16)
d.rectangle([0, 196, 31, 211], fill=(240, 232, 205, 255))
d.line([(0, 203), (31, 203)], fill=(196, 184, 150, 255))

# œil ambre
d.rectangle([40, 196, 71, 211], fill=(255, 190, 40, 255))
d.rectangle([44, 200, 67, 207], fill=(255, 238, 150, 255))
d.line([(56, 197), (56, 210)], fill=(80, 40, 0, 255))

# perle : nacre blanche a reflets rose et bleu, cœur lumineux (region 50 x 50 ; un cube 6 x 6 x 6 unites y lit 24 x 12)
for y in range(196, 246):
    for x in range(80, 130):
        r = math.hypot(x - 105, y - 221) / 25.0
        base = melange((255, 250, 252), (176, 200, 255), min(1.0, r))
        teinte_rose = 0.5 + 0.5 * math.sin(x * 0.4 + y * 0.3)
        d.point((x, y), fill=melange(base, (255, 196, 226), teinte_rose * 0.25))

SORTIE.parent.mkdir(parents=True, exist_ok=True)
im.save(SORTIE)
print("Texture ecrite :", SORTIE)
