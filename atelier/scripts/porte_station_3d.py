# [claude-build] La porte de la Station (Vestiaire et Sas) : une cloison etanche de station de pompage, en plaques de cuivre rivetees dans un cadre de fonte, avec un hublot de laiton
# et deux feux d'etat ; elle MONTE dans le linteau quand on l'ouvre (animation « ouverture », trois secondes, avec une secousse au depart). PLUS AUCUNE grille vanilla.
# Meme fabrique que la porte du Suspendu (donjon_3d.py : format « Modded Entity », box UV, os et animations), dessinee pour une arche de 8 x 8 blocs :
# le jeu la met a la largeur et a la hauteur de son passage (PorteStation, DonjonLogique.echelle).
# Ecrit atelier/modeles/station/porte_station.bbmodel et textures/entity/porte_station.png. Ensuite :
#   python atelier/scripts/bbmodel_vers_java.py atelier/modeles/station/porte_station.bbmodel atelier/donjon/arene/src/main/java/fr/atelier/grappin --nom PorteStation
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
CUIVRE = [(150, 84, 44), (176, 100, 54), (200, 122, 70), (224, 150, 94)]
VITRE = [(96, 150, 170), (130, 188, 206), (170, 222, 234), (222, 246, 252)]
AMBRE = [(200, 120, 20), (238, 160, 40), (255, 200, 90), (255, 236, 170)]


def peintre(matiere):
    def fonte(face, x, y, l, h, a):
        return FONTE[a.choice((0, 1, 1, 2))]

    def cuivre(face, x, y, l, h, a):
        if l > 6 and h > 6 and ((x in (2, l - 3) and y in (2, h - 3)) or (x in (2, l - 3) and y == h // 2 and l > 20)):
            return LAITON[3]                                                             # rivets
        if x in (0, l - 1) or y in (0, h - 1):
            return CUIVRE[0]                                                             # filet sombre du bord de plaque
        return CUIVRE[a.choice((1, 2, 2, 1, 3 if a.random() < 0.08 else 2))]

    def laiton(face, x, y, l, h, a):
        return LAITON[3] if face == 4 and a.random() < 0.2 else LAITON[a.choice((1, 2, 2, 1))]

    def vitre(face, x, y, l, h, a):
        return VITRE[3] if x == 1 or y == 1 else VITRE[a.choice((0, 1, 1, 2))]

    def lueur(face, x, y, l, h, a):
        return AMBRE[3] if x == l // 2 and y == h // 2 else AMBRE[a.choice((1, 2, 2))]

    return {"fonte": fonte, "cuivre": cuivre, "laiton": laiton, "vitre": vitre, "lueur": lueur}[matiere]


class ModelePorte(Modele):
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


def porte():
    m = ModelePorte("porte_station", 512)
    m.os_("racine", None, (0, 0, 0))
    m.os_("dalle", "racine", (0, 0, 0))
    # le cadre : fixe (la dalle monte dans le linteau)
    m.cube("racine", "cadre_bas", "fonte", (-64, 0, -7), (64, 4, 7))
    m.cube("racine", "montant_g", "fonte", (-64, 4, -7), (-56, 128, 7))
    m.cube("racine", "montant_d", "fonte", (56, 4, -7), (64, 128, 7))
    m.cube("racine", "linteau", "fonte", (-56, 120, -7), (56, 128, 7))
    for nom, (x, y) in {"bride_g": (-60, 64), "bride_d": (60, 64)}.items():
        m.cube("racine", nom, "laiton", (x - 5, y - 20, -7.5), (x + 5, y + 20, 7.5))
    m.cube("racine", "lueur_feu_g", "lueur", (-36, 108, -7.4), (-26, 116, 7.4))                   # les deux feux d'etat, dans le linteau
    m.cube("racine", "lueur_feu_d", "lueur", (26, 108, -7.4), (36, 116, 7.4))
    # la dalle : quatre plaques de cuivre rivetees, un croisillon de fonte, un hublot de laiton
    for nom, (x0, x1, y0, y1) in {"plaque_bg": (-56, -2, 4, 62), "plaque_bd": (2, 56, 4, 62), "plaque_hg": (-56, -2, 66, 106), "plaque_hd": (2, 56, 66, 106)}.items():
        m.cube("dalle", nom, "cuivre", (x0, y0, -4), (x1, y1, 4))
    m.cube("dalle", "montant_central", "fonte", (-2, 4, -5), (2, 106, 5))
    m.cube("dalle", "traverse_centrale", "fonte", (-56, 62, -5), (56, 66, 5))
    m.cube("dalle", "traverse_haute", "fonte", (-56, 106, -5), (56, 110, 5))
    m.cube("dalle", "hublot_cadre_h", "laiton", (-14, 50, -6), (14, 78, 6))
    m.cube("dalle", "hublot_verre", "vitre", (-10, 54, -7), (10, 74, 7))
    for nom, y in (("rivet_bas", 12), ("rivet_haut", 96)):
        for k, xr in enumerate((-44, -20, 20, 44)):
            m.cube("dalle", f"{nom}{k}", "laiton", (xr - 2, y, -5), (xr + 2, y + 4, 5))
    Z = (0, 0, 0)
    anim = {"ouverture": (3.0, "once", {
        "dalle": {"position": [(0, Z), (0.5, (0.8, 0, 0)), (0.7, (-0.8, 0, 0)), (0.9, (0.6, 0, 0)), (1.05, Z), (3.0, (0, 112, 0))]},
    })}
    return m, anim


def main():
    SORTIE.mkdir(parents=True, exist_ok=True)
    m, anim = porte()
    places = m.ranger()
    tex = m.texture(places)
    (ASSETS / "entity").mkdir(parents=True, exist_ok=True)
    tex.save(ASSETS / "entity" / "porte_station.png")
    (SORTIE / "porte_station.bbmodel").write_text(json.dumps(bbmodel(m, places, tex, anim), indent=1) + "\n", encoding="utf-8")
    print("porte_station.bbmodel et textures/entity/porte_station.png ecrits")


if __name__ == "__main__":
    main()
