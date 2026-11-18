# [claude-build] Modele 3D anime du casier de vestiaire (Donjon de la Station, vestiaire de piscine) pour Blockbench : un casier de metal
# peint d'un bloc de large sur deux de haut, porte a fentes d'aeration (vraies levres en relief), poignee, serrure, plaque, tablette
# et patere a l'interieur. La porte pivote sur sa charniere gauche (animations ouvrir / fermer).
# Ecrit atelier/modeles/station/casier.bbmodel (format « Modded Entity », box UV, comme donjon_3d.py), une texture par couleur
# (bleu_clair, cyan, vert, jaune, orange), l'icone de l'objet et la texture de particules du bloc. Ensuite :
#   python atelier/scripts/bbmodel_vers_java.py atelier/modeles/station/casier.bbmodel atelier/donjon/arene/src/main/java/fr/atelier/grappin --nom Casier
# Une fois retouche dans Blockbench, le .bbmodel est la source : ne plus relancer ce script.
import json
import random
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from donjon_3d import Modele, bbmodel  # noqa: E402  (meme format de .bbmodel que la porte et le levier)

RACINE = Path(__file__).resolve().parents[2]
SORTIE = RACINE / "atelier/modeles/station"
ASSETS = RACINE / "atelier/donjon/arene/src/main/resources/assets/grappin/textures"

# Peinture de chaque couleur, du plus sombre au plus clair (ordre de CasierCouleur.java).
COULEURS = {"bleu_clair": [(62, 112, 150), (96, 156, 200), (132, 188, 228), (190, 224, 246)],
            "cyan": [(18, 104, 114), (36, 154, 164), (78, 198, 204), (164, 236, 236)],
            "vert": [(46, 104, 48), (76, 150, 68), (116, 190, 98), (182, 228, 160)],
            "jaune": [(150, 112, 18), (212, 170, 38), (244, 208, 78), (255, 240, 170)],
            "orange": [(150, 64, 18), (216, 106, 34), (244, 146, 68), (255, 204, 150)]}
CHROME = [(92, 96, 104), (150, 156, 164), (200, 206, 212), (244, 246, 248)]
SOCLE = [(40, 42, 46), (56, 58, 64), (72, 74, 80)]
PLAQUE = [(226, 226, 218), (40, 40, 46)]

FACE_AVANT = 1                     # ordre des faces de Modele.texture : nord, sud (+z, l'avant du casier), est, ouest, dessus, dessous
FENTES_HAUT = (2, 4, 6, 8)         # rangees des fentes d'aeration sur la porte (texels depuis le haut de la porte, 29 de haut)
FENTES_BAS = (20, 22, 24, 26)


def melange(c1, c2, k):
    return tuple(int(c1[i] + (c2[i] - c1[i]) * k) for i in range(3))


def peintre(matiere, pal):
    """f(face, x, y, l, h, alea) -> couleur d'un texel ; le bord d'une face est un ton plus sombre (tole pliee)."""
    def peinture(face, x, y, l, h, a):
        if face != 4 and (x in (0, l - 1) or y in (0, h - 1)) and l > 2 and h > 2:
            return pal[1]
        return pal[2] if a.random() < 0.9 else melange(pal[2], pal[1], 0.25)  # tole peinte : lisse, a peine marquee

    def interieur(face, x, y, l, h, a):
        fond = melange(pal[1], (0, 0, 0), 0.22)
        return fond if a.random() < 0.9 else melange(fond, pal[1], 0.4)

    def porte(face, x, y, l, h, a):
        if face != FACE_AVANT:
            return interieur(face, x, y, l, h, a)
        dans_fente = 3 <= x <= l - 4
        if dans_fente and (y in FENTES_HAUT or y in FENTES_BAS):
            return (24, 28, 34)                                               # l'ouverture de la fente, dans le noir
        if dans_fente and (y - 1 in FENTES_HAUT or y - 1 in FENTES_BAS):
            return pal[3]                                                     # la tole emboutie sous la fente accroche la lumiere
        if x in (0, l - 1) or y in (0, h - 1):
            return pal[1]
        if x in (1, l - 2) or y in (1, h - 2):
            return pal[3]                                                     # le pli du bord de la porte
        return pal[2] if a.random() < 0.9 else melange(pal[2], pal[1], 0.25)

    def chrome(face, x, y, l, h, a):
        return CHROME[3] if face == 4 else CHROME[a.choice((1, 2, 2, 3))]

    def serrure(face, x, y, l, h, a):
        if face == FACE_AVANT and l >= 2 and x == l // 2 and y == h // 2:
            return (20, 20, 24)                                               # trou de la cle
        return chrome(face, x, y, l, h, a)

    def socle(face, x, y, l, h, a):
        return SOCLE[a.choice((0, 1, 1, 2))]

    def plaque(face, x, y, l, h, a):
        if face == FACE_AVANT and 0 < y < h - 1 and x in (1, 3, 4):
            return PLAQUE[1]                                                  # un numero, vu de loin
        return PLAQUE[0]

    def caoutchouc(face, x, y, l, h, a):
        return COULEURS["orange"][1]

    return {"peinture": peinture, "interieur": interieur, "porte": porte, "chrome": chrome, "serrure": serrure, "socle": socle,
            "plaque": plaque, "caoutchouc": caoutchouc}[matiere]


class ModeleCasier(Modele):
    """Comme Modele, mais le pinceau sait quelle face il peint (les fentes ne vont que sur l'avant de la porte)."""

    def texture(self, places, pal):
        img = Image.new("RGBA", (self.taille, self.taille), (0, 0, 0, 0))
        p = img.load()
        for k, ((_, _, mat, de, a, _), (u, v)) in enumerate(zip(self.cubes, places)):
            w, h, d = (-(-(a[i] - de[i]) // 1) for i in range(3))
            w, h, d = int(w), int(h), int(d)
            f = peintre(mat, pal)
            alea = random.Random(k * 7919)
            faces = ((u + d, v + d, w, h), (u + 2 * d + w, v + d, w, h), (u, v + d, d, h), (u + d + w, v + d, d, h),
                     (u + d, v, w, d), (u + d + w, v, w, d))
            for face, (fu, fv, fl, fh) in enumerate(faces):
                for yy in range(fh):
                    for xx in range(fl):
                        p[fu + xx, fv + yy] = f(face, xx, yy, fl, fh, alea) + (255,)
        return img


def casier():
    m = ModeleCasier("casier", 256)
    m.os_("racine", None, (0, 0, 0))
    m.os_("caisse", "racine", (0, 0, 0))
    m.os_("porte", "caisse", (-7, 16, 8))                                       # charniere : bord gauche de la porte, face avant
    # La caisse : deux flancs pleine hauteur, dos, toit, socle en retrait ; l'interieur se voit porte ouverte.
    m.cube("caisse", "flanc_g", "peinture", (-8, 0, -8), (-7, 32, 8))
    m.cube("caisse", "flanc_d", "peinture", (7, 0, -8), (8, 32, 8))
    m.cube("caisse", "toit", "peinture", (-7, 31, -8), (7, 32, 8))
    m.cube("caisse", "dos", "interieur", (-7, 2, -8), (7, 31, -7))
    m.cube("caisse", "fond", "interieur", (-7, 2, -7), (7, 3, 7))
    m.cube("caisse", "socle", "socle", (-7, 0, -8), (7, 2, 7))
    m.cube("caisse", "tablette", "interieur", (-7, 25, -7), (7, 26, 6))
    m.cube("caisse", "patere", "chrome", (-0.5, 21, -7), (0.5, 22, -5))
    m.cube("caisse", "patere_bout", "chrome", (-0.5, 22, -5.5), (0.5, 23, -4.5))
    # La porte : tole de 14 x 29, levres des fentes d'aeration en relief, poignee a droite, serrure, plaque numerotee.
    m.cube("porte", "battant", "porte", (-7, 2, 7), (7, 31, 8))
    for k, rang in enumerate(FENTES_HAUT + FENTES_BAS):
        y = 31 - rang                                                           # haut de la fente (la porte va de y=2 a y=31)
        m.cube("porte", f"levre{k}", "peinture", (-4, y - 0.5, 8), (4, y, 8.5))
    m.cube("porte", "plaque", "plaque", (-2.5, 18.5, 8), (2.5, 21, 8.5))
    m.cube("porte", "serrure", "serrure", (3.5, 19, 8), (5.5, 21, 8.5))
    m.cube("porte", "patte_haut", "chrome", (4.5, 16, 8), (5.5, 17, 9))
    m.cube("porte", "patte_bas", "chrome", (4.5, 12, 8), (5.5, 13, 9))
    m.cube("porte", "poignee", "chrome", (4.5, 11.5, 9), (5.5, 17.5, 10))
    m.cube("porte", "bracelet", "caoutchouc", (3.75, 17.5, 8.5), (5.25, 19, 9))   # le bracelet de la cle, comme a la piscine
    anim = {
        "ouvrir": (0.5, "hold", {"porte": {"rotation": [(0, (0, 0, 0)), (0.3, (0, -118, 0)), (0.42, (0, -106, 0)), (0.5, (0, -110, 0))]}}),
        "fermer": (0.5, "hold", {"porte": {"rotation": [(0, (0, -110, 0)), (0.32, (0, 4, 0)), (0.42, (0, -2, 0)), (0.5, (0, 0, 0))]}}),
    }
    return m, anim


def icone(pal):
    """Icone 16 x 16 de l'objet : le casier vu de face, fentes, poignee, socle."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = img.load()
    for y in range(16):
        for x in range(4, 12):
            p[x, y] = pal[1] + (255,)
    for y in range(1, 14):
        for x in range(5, 11):
            p[x, y] = pal[2] + (255,)
    for y in (2, 4, 10, 12):
        for x in range(6, 10):
            p[x, y] = (24, 28, 34, 255)
    for y in (6, 7, 8):
        p[9, y] = CHROME[3] + (255,)
    p[7, 6], p[8, 6] = PLAQUE[0] + (255,), PLAQUE[0] + (255,)
    for x in range(5, 11):
        p[x, 14], p[x, 15] = SOCLE[1] + (255,), SOCLE[0] + (255,)
    for y in range(16):
        p[4, y], p[11, y] = pal[0] + (255,), pal[0] + (255,)
    return img


def particules(pal):
    img = Image.new("RGBA", (16, 16))
    alea = random.Random(7)
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), (pal[2] if alea.random() < 0.8 else pal[alea.choice((1, 3))]) + (255,))
    return img


def main():
    SORTIE.mkdir(parents=True, exist_ok=True)
    (ASSETS / "entity").mkdir(parents=True, exist_ok=True)
    m, anim = casier()
    places = m.ranger()
    for couleur, pal in COULEURS.items():
        m.texture(places, pal).save(ASSETS / "entity" / f"casier_{couleur}.png")
    (SORTIE / "casier.bbmodel").write_text(json.dumps(bbmodel(m, places, m.texture(places, COULEURS["bleu_clair"]), anim), indent=1) + "\n",
                                           encoding="utf-8")
    icone(COULEURS["bleu_clair"]).save(ASSETS / "item" / "casier.png")
    particules(COULEURS["bleu_clair"]).save(ASSETS / "block" / "casier.png")
    print("casier.bbmodel, textures casier_<couleur>, icone et particules ecrits")


if __name__ == "__main__":
    main()
