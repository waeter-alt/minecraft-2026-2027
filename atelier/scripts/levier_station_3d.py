# [claude-build] Le levier de commande du Donjon de la Station (le levier general du Poste, qui ouvre la bonde de la Cuve), modele Blockbench
# anime, dans le style de la station de pompage : socle de fonte rivete, carter, secteur cranté de laiton, long manche d'acier a poignee rouge
# et pommeau de laiton, verrou le long du manche, voyant vert qui s'allume levier tire (cube lueur_*).
# Meme fabrique que le levier de racine du Suspendu (donjon_3d.py : format « Modded Entity », box UV, os et animations tirer / relacher),
# mais un modele, une texture et un bloc a lui : le levier de racine n'est pas touche (ne pas relancer donjon_3d.py pour ca).
# Ecrit atelier/modeles/station/levier_station.bbmodel, textures/entity/levier_station.png et l'icone textures/item/levier_station.png. Ensuite :
#   python atelier/scripts/bbmodel_vers_java.py atelier/modeles/station/levier_station.bbmodel atelier/donjon/arene/src/main/java/fr/atelier/grappin --nom LevierStation
# Une fois retouche dans Blockbench, le .bbmodel est la source : ne plus relancer ce script.
import json
import math
import random
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from donjon_3d import Modele, bbmodel  # noqa: E402

RACINE = Path(__file__).resolve().parents[2]
SORTIE = RACINE / "atelier/modeles/station"
ASSETS = RACINE / "atelier/donjon/arene/src/main/resources/assets/donjon/textures"

FONTE = [(44, 45, 49), (56, 58, 63), (68, 70, 76), (86, 88, 94)]
LAITON = [(150, 112, 42), (184, 144, 58), (210, 172, 82), (236, 206, 120)]
ACIER = [(96, 102, 110), (124, 130, 138), (156, 162, 170), (196, 202, 208)]
ROUGE = [(110, 18, 18), (150, 28, 26), (182, 40, 36), (214, 70, 60)]
VERT = [(60, 200, 90), (110, 240, 130), (180, 255, 190), (230, 255, 235)]


def peintre(matiere):
    """f(face, x, y, l, h, alea) -> couleur ; face : 0 nord, 1 sud, 2 est, 3 ouest, 4 dessus, 5 dessous (ordre de Modele.texture)."""
    def fonte(face, x, y, l, h, a):
        return FONTE[a.choice((0, 1, 1, 2))]

    def fonte_rivets(face, x, y, l, h, a):
        if l > 4 and h > 4 and x in (1, l - 2) and y in (1, h - 2):
            return LAITON[3]                                                     # rivets aux coins
        return fonte(face, x, y, l, h, a)

    def laiton(face, x, y, l, h, a):
        return LAITON[3] if face == 4 and a.random() < 0.2 else LAITON[a.choice((1, 2, 2, 1, 2))]

    def crans(face, x, y, l, h, a):
        if face in (2, 3) and y == 0 and x % 2 == 0:
            return LAITON[0]                                                     # les crans du secteur, ou s'accroche le verrou
        return laiton(face, x, y, l, h, a)

    def acier(face, x, y, l, h, a):
        return ACIER[3] if x == 0 else ACIER[a.choice((1, 2, 2))]

    def rouge(face, x, y, l, h, a):
        return ROUGE[0] if y % 3 == 0 else ROUGE[a.choice((1, 2, 2, 3))]       # poignee guipee

    def plaque(face, x, y, l, h, a):
        if face in (2, 3) and 0 < y < h - 1 and 0 < x < l - 1 and (x + y) % 2 == 0:
            return (40, 34, 24)                                                  # l'inscription, vue de loin
        return LAITON[2]

    def lueur(face, x, y, l, h, a):
        return VERT[3] if x == l // 2 and y == h // 2 else VERT[a.choice((0, 1, 2))]

    return {"fonte": fonte, "fonte_rivets": fonte_rivets, "laiton": laiton, "crans": crans, "acier": acier, "rouge": rouge, "plaque": plaque,
            "lueur": lueur}[matiere]


class ModeleLevier(Modele):
    """Comme Modele (donjon_3d), avec les matieres de la Station et un pinceau qui sait quelle face il peint."""

    def texture(self, places, gemme=None):
        img = Image.new("RGBA", (self.taille, self.taille), (0, 0, 0, 0))
        p = img.load()
        for k, ((_, _, mat, de, a, _), (u, v)) in enumerate(zip(self.cubes, places)):
            w, h, d = (int(math.ceil(a[i] - de[i])) for i in range(3))
            f = peintre(mat)
            alea = random.Random(k * 7919)
            faces = ((u + d, v + d, w, h), (u + 2 * d + w, v + d, w, h), (u, v + d, d, h), (u + d + w, v + d, d, h), (u + d, v, w, d), (u + d + w, v, w, d))
            for face, (fu, fv, fl, fh) in enumerate(faces):
                for yy in range(fh):
                    for xx in range(fl):
                        p[fu + xx, fv + yy] = f(face, xx, yy, fl, fh, alea) + (255,)
        return img


def levier():
    m = ModeleLevier("levier_station", 128)
    m.os_("racine", None, (0, 0, 0))
    m.os_("socle", "racine", (0, 0, 0))
    m.os_("manche", "socle", (0, 9, 0))
    m.cube("socle", "platine", "fonte_rivets", (-7, 0, -7), (7, 2, 7))
    for k, (x, z) in enumerate(((-6, -6), (5, -6), (-6, 5), (5, 5))):
        m.cube("socle", f"boulon{k}", "laiton", (x, 2, z), (x + 1, 2.5, z + 1))
    m.cube("socle", "carter", "fonte", (-3.5, 2, -5), (3.5, 8, 5))
    m.cube("socle", "plaque", "plaque", (3.5, 3, -3), (4, 6.5, 3))
    for cote, x in (("g", -3.5), ("d", 2.5)):                                      # le secteur : deux joues de laiton en arc, crantees
        m.cube("socle", f"secteur_{cote}0", "crans", (x, 8, -6), (x + 1, 10, 6))
        m.cube("socle", f"secteur_{cote}1", "crans", (x, 10, -5), (x + 1, 11.5, 5))
        m.cube("socle", f"secteur_{cote}2", "crans", (x, 11.5, -3.5), (x + 1, 12.5, 3.5))
    m.cube("socle", "axe", "laiton", (-4.5, 8, -1), (4.5, 10, 1))
    m.cube("socle", "lueur_voyant", "lueur", (-1, 4.5, -5.5), (1, 6.5, -5))
    m.cube("socle", "lunette_voyant", "laiton", (-1.5, 4, -5.2), (1.5, 7, -5))
    m.cube("manche", "tige", "acier", (-0.75, 9, -0.75), (0.75, 27, 0.75))
    m.cube("manche", "moyeu", "fonte", (-1.5, 8, -1.5), (1.5, 11, 1.5))
    m.cube("manche", "poignee", "rouge", (-1.25, 22, -1.25), (1.25, 27, 1.25))
    m.cube("manche", "pommeau", "laiton", (-1.5, 27, -1.5), (1.5, 29.5, 1.5))
    m.cube("manche", "verrou", "acier", (-0.25, 11, 1), (0.25, 21, 1.5))
    m.cube("manche", "gachette", "laiton", (-0.5, 20.5, 1), (0.5, 22, 2.5))
    Z = (0, 0, 0)
    anim = {
        "tirer": (0.6, "hold", {"manche": {"rotation": [(0, (-35, 0, 0)), (0.45, (40, 0, 0)), (0.6, (35, 0, 0))]}}),
        "relacher": (0.6, "hold", {"manche": {"rotation": [(0, (35, 0, 0)), (0.45, (-40, 0, 0)), (0.6, (-35, 0, 0))]}}),
    }
    del Z
    return m, anim


def icone():
    """Icone 16 x 16 de l'objet : le manche rouge sur son socle de fonte, le secteur de laiton."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = img.load()
    for x in range(3, 13):
        p[x, 14], p[x, 15] = FONTE[2] + (255,), FONTE[0] + (255,)
    for x in range(5, 11):
        for y in range(11, 14):
            p[x, y] = FONTE[1] + (255,)
    for x, y in ((4, 11), (5, 10), (6, 9), (9, 9), (10, 10), (11, 11)):
        p[x, y] = LAITON[2] + (255,)
    for k in range(9):                                                               # le manche, penche
        x, y = 8 + k * 0.45, 10 - k
        p[int(x), int(y)] = ACIER[2] + (255,)
    for k in range(5, 9):
        x, y = 8 + k * 0.45, 10 - k
        p[int(x), int(y)] = ROUGE[2] + (255,)
        p[int(x) + 1, int(y)] = ROUGE[1] + (255,)
    p[12, 1], p[12, 2], p[13, 1] = LAITON[3] + (255,), LAITON[1] + (255,), LAITON[2] + (255,)
    p[7, 12] = VERT[2] + (255,)
    return img


def main():
    SORTIE.mkdir(parents=True, exist_ok=True)
    m, anim = levier()
    places = m.ranger()
    tex = m.texture(places)
    tex.save(ASSETS / "entity" / "levier_station.png")
    (SORTIE / "levier_station.bbmodel").write_text(json.dumps(bbmodel(m, places, tex, anim), indent=1) + "\n", encoding="utf-8")
    icone().save(ASSETS / "item" / "levier_station.png")
    print("levier_station.bbmodel, textures entity/levier_station.png et item/levier_station.png ecrits")


if __name__ == "__main__":
    main()
