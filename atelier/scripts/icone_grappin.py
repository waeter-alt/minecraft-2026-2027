# [claude-build] Dessine l'icone 16x16 du grappin (mod grappin) : manche de bois, corde, crochet de fer a trois pointes.
from pathlib import Path
from PIL import Image
R = Path(__file__).resolve().parent.parent
img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
p = img.load()
BOIS, BOIS_F, CORDE, FER, FER_C, FER_F = (125, 86, 50, 255), (90, 60, 35, 255), (200, 185, 150, 255), (165, 168, 175, 255), (215, 218, 222, 255), (95, 98, 105, 255)
for k in range(5):                                   # manche, en bas a gauche
    p[1 + k, 14 - k] = BOIS
    p[2 + k, 14 - k] = BOIS_F
for k in range(4):                                   # corde enroulee
    p[6 + k, 9 - k] = CORDE
p[7, 9], p[8, 8] = CORDE, CORDE
for y in range(2, 8):                                # tige du crochet
    p[11, y] = FER
    p[12, y] = FER_F
p[11, 2] = FER_C
for x, y in ((9, 3), (8, 2), (8, 1), (14, 3), (15, 2), (15, 1)):   # deux griffes laterales
    p[x if x < 11 else x - 1, y] = FER
for x, y in ((10, 4), (13, 4)):
    p[x, y] = FER_C
p[11, 1], p[12, 1], p[11, 0] = FER, FER, FER_C       # pointe centrale
img.save(R / "mods_src/grappin/src/main/resources/assets/grappin/textures/item/grappin.png")
img.resize((128, 128), Image.NEAREST).save(R / "exports/grappin_icone.png")
print("icone ecrite")
