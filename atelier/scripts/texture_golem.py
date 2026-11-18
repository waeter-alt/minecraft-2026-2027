# [claude-build]
"""texture_golem.py - genere la texture du Gardien des Racines (512 x 512), conçue pour une silhouette lisible : corps en ecorce SOMBRE, tete et cornes en bois CLAIR,
mousse bien verte a la taille, plaque rouge au plastron et poing rouge, yeux ambre lumineux. Chaque zone est lue par les cubes qui y pointent (texOffs, voir GolemModele) :
  ecorce sombre : (0, 0) a (256, 130)    rouge : (0, 130) a (160, 215)    mousse : (0, 224) a (200, 290)
  bois clair (tete, cornes) : (260, 0) a (428, 80)    masque : (260, 100) a (332, 132)    visage : (260, 230) a (356, 272)    creme (corne, crocs) : (330, 100) a (370, 130)
  cernes de rondins : (330, 140) a (382, 190)    ambre (yeux) : (330, 200) a (350, 210)
  test-local\\venv\\Scripts\\python.exe atelier\\scripts\\texture_golem.py
"""
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

SORTIE = Path(__file__).resolve().parent.parent / "donjon/arene/src/main/resources/assets/grappin/textures/entity/golem.png"
random.seed(7)
im = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
d = ImageDraw.Draw(im)


def teinte(c, t):
    return tuple(max(0, min(255, int(v * t))) for v in c[:3]) + (255,)


def ecorce(x0, y0, w, h, base, noeuds=True, lichen=True):
    """Ecorce : bandes verticales variees, fissures sombres, nœuds a anneaux, un peu de lichen."""
    for x in range(w):
        bande = 0.82 + 0.28 * (0.5 + 0.5 * math.sin(x * 0.7 + (x // 9) * 1.9))
        for y in range(h):
            d.point((x0 + x, y0 + y), fill=teinte(base, bande * (0.94 + random.random() * 0.12)))
    for _ in range(w // 3):
        x = x0 + random.randint(0, w - 1)
        y = y0 + random.randint(0, h - 1)
        for k in range(random.randint(8, 30)):
            d.point((x + random.choice([0, 0, 1]), min(y0 + h - 1, y + k)), fill=teinte(base, 0.45))
    if noeuds:
        for _ in range(max(2, w * h // 2200)):
            cx, cy = x0 + random.randint(6, w - 7), y0 + random.randint(6, h - 7)
            for r, t in ((6, 0.55), (4, 0.75), (2, 0.5)):
                d.ellipse([cx - r, cy - r * 1.3, cx + r, cy + r * 1.3], outline=teinte(base, t))
    if lichen:
        for _ in range(w * h // 700):
            x, y = x0 + random.randint(2, w - 3), y0 + random.randint(2, h - 3)
            r_ = random.randint(1, 4)
            d.ellipse([x - r_, y - r_ * 0.7, x + r_, y + r_ * 0.7], fill=teinte((92, 120, 52), random.choice([0.8, 1.0, 1.15])))


def aplat(x0, y0, w, h, base, grain=0.1):
    for x in range(w):
        for y in range(h):
            d.point((x0 + x, y0 + y), fill=teinte(base, 1 - grain + random.random() * grain * 2))


# corps : ecorce SOMBRE
ecorce(0, 0, 256, 130, (92, 58, 30))

for _ in range(90):                                                 # reflets clairs sur les aretes de l'ecorce, et petites entailles horizontales
    x, y = random.randint(2, 252), random.randint(2, 120)
    d.line([(x, y), (x, y + random.randint(5, 18))], fill=teinte((92, 58, 30), 1.45))
for _ in range(40):
    x, y = random.randint(2, 240), random.randint(2, 126)
    d.line([(x, y), (x + random.randint(4, 12), y)], fill=teinte((92, 58, 30), 0.4))

# rouge : peau lisse a taches (plastron, poing, noeuds du dos)
aplat(0, 130, 160, 85, (186, 34, 28), 0.12)
for _ in range(60):
    x, y = random.randint(0, 155), random.randint(132, 210)
    d.ellipse([x, y, x + random.randint(3, 9), y + random.randint(3, 7)], fill=teinte((186, 34, 28), random.choice([0.8, 1.25])))

for _ in range(40):                                                 # taches de sang-de-dragon et reflets sur le rouge
    x, y = random.randint(2, 150), random.randint(134, 208)
    d.ellipse([x, y, x + 2, y + 2], fill=(236, 120, 96, 255))

# mousse : vert vif
aplat(0, 224, 200, 66, (84, 140, 44), 0.22)
for _ in range(140):
    x, y = random.randint(0, 196), random.randint(226, 286)
    d.ellipse([x, y, x + random.randint(2, 6), y + random.randint(2, 5)], fill=teinte((84, 140, 44), random.choice([0.7, 1.3])))

# tete et cornes : bois CLAIR, pour qu'on la distingue du corps ; (cube 11 x 10 x 10 blocs = 44 x 40 x 40 unites : face avant en (300..344, 40..80))
ecorce(260, 0, 168, 80, (158, 112, 66), noeuds=False, lichen=False)
d.rectangle([300, 40, 343, 79], fill=teinte((158, 112, 66), 0.95))
d.rectangle([300, 40, 343, 46], fill=teinte((158, 112, 66), 0.55))                         # arcade sombre
for x in range(302, 342, 5):
    d.line([(x, 47), (x + 1, 79)], fill=teinte((158, 112, 66), 0.8))

# masque rouge revele quand la plaque du visage s'efface (cube 8 x 7 x 1 blocs = 32 x 28 x 4 unites ; face avant en (264..296, 104..132))
aplat(260, 100, 72, 32, (174, 30, 26), 0.1)
d.rectangle([264, 104, 295, 131], fill=teinte((174, 30, 26), 1.0))
d.polygon([(266, 112), (278, 116), (278, 122), (266, 120)], fill=(30, 10, 10, 255))
d.polygon([(294, 112), (282, 116), (282, 122), (294, 120)], fill=(30, 10, 10, 255))
d.rectangle([278, 118, 281, 128], fill=teinte((174, 30, 26), 0.7))
d.line([(266, 128), (294, 128)], fill=(80, 12, 12, 255))

# plaque sculptee du visage (cube 10 x 8,5 x 2 blocs = 40 x 34 x 8 unites ; face avant en (268..308, 238..272)) : bois, arcade en colere, orbites, nez, grande bouche a dents
aplat(260, 230, 96, 42, (140, 96, 56), 0.12)
for x in range(268, 308, 4):
    d.line([(x, 238), (x + 1, 272)], fill=teinte((140, 96, 56), 0.82))
d.rectangle([268, 238, 307, 271], outline=teinte((140, 96, 56), 0.6))
d.polygon([(270, 246), (286, 250), (286, 256), (270, 253)], fill=(40, 24, 12, 255))       # orbites inclinees (les yeux ambre sont devant)
d.polygon([(306, 246), (290, 250), (290, 256), (306, 253)], fill=(40, 24, 12, 255))
d.line([(270, 244), (287, 249)], fill=teinte((140, 96, 56), 0.5))                         # arcades en colere
d.line([(306, 244), (289, 249)], fill=teinte((140, 96, 56), 0.5))
d.line([(270, 245), (287, 250)], fill=teinte((140, 96, 56), 0.45))
d.line([(306, 245), (289, 250)], fill=teinte((140, 96, 56), 0.45))
d.polygon([(272, 262), (304, 262), (301, 270), (275, 270)], fill=(110, 18, 18, 255))      # bouche rouge
for x in range(274, 302, 4):
    d.polygon([(x, 262), (x + 3, 262), (x + 1, 266)], fill=(240, 230, 184, 255))          # dents du haut
    d.polygon([(x + 1, 270), (x + 4, 270), (x + 2, 267)], fill=(240, 230, 184, 255))      # dents du bas
d.line([(280, 255), (284, 261)], fill=teinte((140, 96, 56), 0.5))                         # sillons de chaque cote du nez
d.line([(296, 255), (292, 261)], fill=teinte((140, 96, 56), 0.5))

# creme : corne et crocs
aplat(330, 100, 40, 30, (240, 230, 184), 0.08)
d.line([(331, 112), (369, 112)], fill=(196, 182, 132, 255))

# bouts de rondins a cernes (deux carres de 24 x 24 en (330, 164) et (356, 164))
d.rectangle([330, 140, 380, 163], fill=teinte((92, 58, 30), 1.0))
for x0 in (330, 356):
    d.rectangle([x0, 164, x0 + 23, 187], fill=(206, 166, 106, 255))
    for rayon, c in ((11, (140, 92, 48)), (9, (186, 140, 84)), (7, (140, 92, 48)), (5, (186, 140, 84)), (3, (120, 76, 38)), (1, (90, 56, 28))):
        d.ellipse([x0 + 12 - rayon, 176 - rayon, x0 + 12 + rayon, 176 + rayon], outline=c)
    for a_ in range(0, 360, 45):
        d.line([(x0 + 12, 176), (x0 + 12 + math.cos(math.radians(a_)) * 11, 176 + math.sin(math.radians(a_)) * 11)], fill=(120, 80, 40, 255))
d.rectangle([354, 164, 355, 187], fill=teinte((92, 58, 30), 0.9))

# yeux : ambre vif
d.rectangle([330, 200, 352, 210], fill=(255, 196, 40, 255))
d.rectangle([332, 203, 350, 207], fill=(255, 236, 140, 255))

SORTIE.parent.mkdir(parents=True, exist_ok=True)
im.save(SORTIE)
print("Texture ecrite :", SORTIE)
