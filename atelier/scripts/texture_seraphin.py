# [claude-build]
"""texture_seraphin.py - genere la texture du Seraphin (256 x 256) : un ange blanc et or, avec un oeil bleu saphir cerne d'or.
Zones (voir SeraphinModele ; 1 unite de modele = 1 pixel, le modele est dessine a l'echelle x2 puis reduit au rendu) :
  plumes : 4 tuiles de 16 px en (0,0), (16,0), (32,0), (48,0) - une plume de base (largeur 6, epaisseur 2) y lit ses faces avant (x 2..7) et arriere (x 10..15) ;
  pointes de plumes : (192,0), 12 px de large (largeur 4, epaisseur 2) ; os des ailes de chauve-souris : (72,90) ; membrane : (0,96) ;
  oeil : (0,160) ; pupille : (80,160) ; paupiere : (110,160) ; cadre, aureole : (160,160).
  test-local\\venv\\Scripts\\python.exe atelier\\scripts\\texture_seraphin.py
"""
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

SORTIE = Path(__file__).resolve().parent.parent / "grappin/src/main/resources/assets/grappin/textures/entity/seraphin.png"
random.seed(11)
im = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
d = ImageDraw.Draw(im)
OR = (222, 184, 74, 255)
OR_SOMBRE = (160, 122, 34, 255)
OR_CLAIR = (250, 226, 140, 255)


def mixe(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def plume(x0, y0, larg, haut, base, pointe):
    """Une face de plume (larg x haut) en (x0, y0) : blanc a reflets, ombre bleutee vers la base (en bas), rachis dore, barbes fines, bout effile et dore (en haut)."""
    cx = (larg - 1) / 2
    for y in range(haut):
        t = y / max(1, haut - 1)                                   # 0 = pointe, 1 = base
        demi = larg / 2 if not pointe else min(larg / 2, 0.6 + t * larg * 1.2)       # la pointe s'effile
        for x in range(larg):
            if abs(x + 0.5 - larg / 2) > demi:
                continue
            c = mixe(base, (176, 192, 224), t * 0.55)              # de plus en plus bleute vers la base
            if x in (0, larg - 1):
                c = mixe(c, (130, 146, 186), 0.55)                 # bords legerement ombres
            elif abs(x - cx) < 0.6:
                c = mixe(OR, OR_SOMBRE, t * 0.5)                   # rachis dore
            elif y % 4 == 0 and abs(x - cx) <= 2.1:
                c = mixe(c, (150, 164, 204), 0.5)                  # barbes
            if pointe and t < 0.2:
                c = mixe(c, OR_CLAIR, 0.8)                         # le bout de la plume dore
            d.point((x0 + x, y0 + y), fill=c)


# plumes de base : 4 tuiles (blanc pur, bleute, creme, nacre)
teintes = [(255, 255, 255), (238, 244, 255), (255, 248, 230), (246, 248, 255)]
for t, teinte in enumerate(teintes):
    u = t * 16
    d.rectangle([u, 0, u + 15, 81], fill=(0, 0, 0, 0))
    d.rectangle([u, 2, u + 15, 80], fill=mixe(teinte, (150, 168, 210), 0.35))      # cotes
    plume(u + 2, 2, 6, 78, teinte, False)                          # face avant
    plume(u + 10, 2, 6, 78, teinte, False)                         # face arriere
    d.rectangle([u, 0, u + 15, 1], fill=OR)                        # dessus et dessous de la plume, dores
# pointes de plumes (largeur 4, epaisseur 2) : 12 px de large, 36 de haut
d.rectangle([192, 0, 203, 40], fill=(0, 0, 0, 0))
d.rectangle([192, 2, 203, 36], fill=mixe((250, 250, 255), (150, 168, 210), 0.35))
plume(194, 2, 4, 34, (252, 252, 255), True)
plume(200, 2, 4, 34, (252, 252, 255), True)
d.rectangle([192, 0, 203, 1], fill=OR_CLAIR)

# os des ailes de chauve-souris (largeur 2, epaisseur 2 : region 8 x 60) en (72, 90) : or
d.rectangle([72, 90, 79, 150], fill=OR)
for y in range(90, 151, 3):
    d.line([(72, y), (79, y)], fill=OR_SOMBRE)

# membrane (largeur 12, epaisseur 1 : region 26 x 56 au plus) en (0, 96) : nacre lavande, nervures et bord festonne dores
d.rectangle([0, 96, 25, 152], fill=(226, 226, 244, 255))
for y in range(96, 153):
    d.line([(0, y), (25, y)], fill=mixe((236, 236, 250), (196, 200, 232), (y - 96) / 56))
for i in range(0, 26, 3):
    d.line([(i, 97), (min(25, i + 5), 152)], fill=(176, 160, 196, 255))
for x in range(0, 26, 4):
    d.arc([x, 146, x + 4, 154], 0, 180, fill=OR_SOMBRE)

# oeil : globe 16 x 16 x 16 en (0, 160) : region 64 x 32 ; la face avant est (16..31, 176..191)
d.rectangle([0, 160, 63, 191], fill=(252, 250, 246, 255))
for _ in range(14):                                                # fins vaisseaux tres pales
    x = random.randint(0, 63)
    y = random.randint(160, 191)
    for _ in range(random.randint(3, 6)):
        d.point((x, y), fill=(232, 196, 196, 255))
        x += random.choice([-1, 0, 1])
        y += random.choice([0, 1])
cx, cy = 23.5, 183.5                                               # iris saphir, anneau d'or, rayons graves
d.ellipse([cx - 7.5, cy - 7.5, cx + 7.5, cy + 7.5], fill=OR)
d.ellipse([cx - 6.5, cy - 6.5, cx + 6.5, cy + 6.5], fill=(40, 96, 200, 255))
d.ellipse([cx - 4.5, cy - 4.5, cx + 4.5, cy + 4.5], fill=(70, 150, 235, 255))
for a in range(0, 360, 15):
    r = math.radians(a)
    d.line([(cx + math.cos(r) * 3, cy + math.sin(r) * 3), (cx + math.cos(r) * 6.3, cy + math.sin(r) * 6.3)], fill=(22, 64, 160, 255))
# pupille : (80, 160), 8 x 8 x 2 : region 20 x 10 ; face avant (2..9, 162..169)
d.rectangle([80, 160, 99, 169], fill=(6, 10, 30, 255))
d.rectangle([82, 162, 89, 169], fill=(6, 10, 30, 255))
d.point((85, 163), fill=(255, 255, 255, 255))
d.point((86, 163), fill=(220, 236, 255, 255))
d.point((85, 164), fill=(220, 236, 255, 255))
# paupiere : (110, 160), 18 x 18 x 2 : region 40 x 20 - peau doree, stries fines
d.rectangle([110, 160, 149, 179], fill=mixe(OR, (255, 240, 200), 0.5))
for y in range(160, 180, 2):
    d.line([(110, y), (149, y)], fill=OR)
for x in range(110, 150, 6):
    d.line([(x, 160), (x - 2, 179)], fill=OR_SOMBRE)
# cadre de l'oeil et aureole : (160, 160) - or strie
d.rectangle([160, 160, 220, 190], fill=OR)
for y in range(160, 191, 3):
    d.line([(160, y), (220, y)], fill=OR_CLAIR)
for x in range(160, 221, 5):
    d.line([(x, 160), (x, 190)], fill=OR_SOMBRE)

SORTIE.parent.mkdir(parents=True, exist_ok=True)
im.save(SORTIE)
print("Texture ecrite :", SORTIE)
