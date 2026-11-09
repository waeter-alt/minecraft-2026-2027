# [claude-build] Texture 16x16 du point d'ancrage (mod grappin 2.0) : pierre moussue, anneau de fer, coeur ambre lumineux.
import math
from pathlib import Path
from PIL import Image
R = Path(__file__).resolve().parent.parent
img = Image.new("RGBA", (16, 16))
p = img.load()
for x in range(16):
    for y in range(16):
        h = ((x * 7 + y * 13) * 2654435761 >> 7) % 23
        base = (96 + h, 98 + h, 92 + h) if (x * 3 + y * 5) % 11 else (70 + h, 105 + h, 60 + h)    # pierre, taches de mousse
        p[x, y] = (*base, 255)
        d = math.hypot(x - 7.5, y - 7.5)
        if 4.2 <= d < 6.2:                                                                     # anneau de fer
            c = 175 if (x + y) % 3 else 140
            p[x, y] = (c, c + 4, c + 10, 255)
        elif 3.4 <= d < 4.2 or 6.2 <= d < 6.9:
            p[x, y] = (55, 58, 64, 255)                                                        # ombre de l'anneau
        elif d < 2.2:
            p[x, y] = (255, 196, 90, 255) if d < 1.3 else (225, 150, 60, 255)                  # coeur ambre
img.save(R / "mods_src/grappin/src/main/resources/assets/grappin/textures/block/ancrage.png")
img.resize((128, 128), Image.NEAREST).save(R / "exports/ancrage_icone.png")
print("texture ecrite")
