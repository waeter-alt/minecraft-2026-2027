# [claude-build]
"""icone_voile.py - dessine en pixel art les deux textures du Voile des Profondeurs :
- l'icone de l'objet (16 x 16) : textures/item/voile.png ;
- la couche d'armure portee (64 x 32, disposition standard d'un casque) : textures/models/armor/voile_layer_1.png.
  test-local\\venv\\Scripts\\python.exe atelier\\scripts\\icone_voile.py"""
from pathlib import Path

from PIL import Image

RACINE = Path(__file__).resolve().parent.parent
RES = RACINE / "grappin/src/main/resources/assets/grappin/textures"
LEG = {".": None, "o": (10, 62, 78, 255), "a": (38, 168, 184, 255), "l": (120, 224, 226, 255), "h": (222, 252, 255, 255), "d": (22, 112, 140, 255), "p": (250, 244, 250, 255),
       "q": (236, 164, 196, 255)}
ICONE = [
    "................",
    "....oooooooo....",
    "...oaaaaaaaao...",
    "..oaallllllaao..",
    "..oaaaaaaaaaao..",
    "..oalaaaaaaalo..",
    "..oaaalaaapqao..",
    "..oalaaaaaaaao..",
    "..oaaaaaaahaaao.",
    "...oaalaaaaalao.",
    "...odaaaaaaaao..",
    "...oadaaaaaado..",
    "....odaaaaaado..",
    "....oaoddaodo...",
    ".....o.oo.o.....",
    "................",
]
img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
for y, ligne in enumerate(ICONE):
    for x, c in enumerate(ligne):
        if LEG[c]:
            img.putpixel((x, y), LEG[c])
(RES / "item").mkdir(parents=True, exist_ok=True)
img.save(RES / "item/voile.png")

# couche d'armure : un voile de gaze bleu-vert sur la tete (les pixels sont pleins ou transparents : l'armure n'a pas de demi-transparence)
arm = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
A, L, D, O, P, Q = LEG["a"], LEG["l"], LEG["d"], LEG["o"], LEG["p"], LEG["q"]


def face(x0, y0, w, h, base, motif):
    for j in range(h):
        for i in range(w):
            arm.putpixel((x0 + i, y0 + j), motif(i, j, w, h) or base)


def tissu(i, j, w, h):
    if (i + j) % 5 == 0:
        return L
    if j == h - 1:
        return D
    return None


def dentelle(i, j, w, h):
    """Bas du visage : le voile s'effiloche, avec des trous en damier."""
    if j >= h - 3 and (i + j) % 2 == 0:
        return (0, 0, 0, 0)
    return tissu(i, j, w, h)


face(8, 0, 8, 8, D, lambda i, j, w, h: L if (i * 3 + j) % 7 == 0 else None)         # dessus
face(16, 0, 8, 8, D, lambda i, j, w, h: None)                                       # dessous
face(0, 8, 8, 8, A, tissu)                                                           # cote droit
face(8, 8, 8, 8, A, dentelle)                                                        # face
face(16, 8, 8, 8, A, tissu)                                                          # cote gauche
face(24, 8, 8, 8, A, tissu)                                                          # nuque
arm.putpixel((19, 11), P)                                                            # la perle d'attache, sur le cote gauche
arm.putpixel((20, 11), Q)
for x in range(8, 16):                                                               # liseré sombre au bord du front
    arm.putpixel((x, 8), O)
(RES / "models/armor").mkdir(parents=True, exist_ok=True)
arm.save(RES / "models/armor/voile_layer_1.png")
print("ecrit item/voile.png (16 x 16) et models/armor/voile_layer_1.png (64 x 32)")
