# [claude-build]
"""icone_paravoile.py - dessine en pixel art les deux textures de la Paravoile :
- l'icone de l'objet (16 x 16) : textures/item/paravoile.png ;
- la texture de la voile deployee (128 x 64, disposition des deux boites du modele) : textures/entity/paravoile.png.
  test-local\\venv\\Scripts\\python.exe atelier\\scripts\\icone_paravoile.py"""
from pathlib import Path

from PIL import Image

RACINE = Path(__file__).resolve().parent.parent
RES = RACINE / "grappin/src/main/resources/assets/grappin/textures"
LEG = {".": None, "o": (28, 40, 78, 255), "b": (46, 98, 176, 255), "c": (96, 164, 226, 255), "j": (240, 196, 72, 255), "w": (122, 84, 50, 255), "x": (84, 56, 32, 255)}
ICONE = [
    "................",
    "................",
    "....oooooooo....",
    "..ooccbbbbbboo..",
    ".ocbbbbjjbbbbbo.",
    "ocbbbbbjjbbbbbbo",
    "ojjjjjjjjjjjjjjo",
    "o.w..........w.o",
    ".ow..........wo.",
    "..ow........wo..",
    "...xw......wx...",
    "....xw....wx....",
    ".....xw..wx.....",
    "......xwwx......",
    ".......xx.......",
    "................",
]
img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
for y, ligne in enumerate(ICONE):
    for x, c in enumerate(ligne):
        if LEG[c]:
            img.putpixel((x, y), LEG[c])
(RES / "item").mkdir(parents=True, exist_ok=True)
img.save(RES / "item/paravoile.png")

# voile deployee : la boite de la toile (24 x 1 x 16, depart en 0,0) puis les montants (1 x 21 x 1, depart en 0,20)
B, C, J, O, W, X = LEG["b"], LEG["c"], LEG["j"], LEG["o"], LEG["w"], LEG["x"]
tex = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
for j in range(16):                                              # dessus (16..40, 0..16) : bleu, bordure et croix dorees, reflets
    for i in range(24):
        c = B
        if i in (0, 23) or j in (0, 15):
            c = J
        elif i in (11, 12) or j in (7, 8):
            c = J if (i in (11, 12) and j in (7, 8)) else C
        elif (i + j) % 7 == 0:
            c = C
        tex.putpixel((16 + i, j), c)
        tex.putpixel((40 + i, j), O if (i + j) % 2 else B)       # dessous (40..64, 0..16) : bleu sombre
for i in range(0, 80):                                           # tranches (v = 16) : bordure doree
    if i < 16 or 16 <= i < 40 or 40 <= i < 56 or 56 <= i < 80:
        tex.putpixel((i, 16), J)
for j in range(20, 42):                                          # montants : bois
    for i in range(0, 4):
        tex.putpixel((i, j), W if (i + j) % 3 else X)
(RES / "entity").mkdir(parents=True, exist_ok=True)
tex.save(RES / "entity/paravoile.png")
print("ok")
