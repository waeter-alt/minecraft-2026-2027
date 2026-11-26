# [claude-build] La valve de la piscine du Donjon de la Station (Hall des Bassins : trois valves ouvrent la porte du Vestiaire), modele Blockbench anime :
# une tuyauterie de fonte rivetee sur sa platine, une bride de laiton et un volant (anneau octogonal, quatre rayons, moyeu de laiton) peint de la couleur de la valve.
# Un seul clic droit la tourne a fond (animation « tourner », trois tours), puis elle reste tournee ; le voyant vert du socle s'allume.
# Meme fabrique que levier_station_3d.py (donjon_3d.py : format « Modded Entity », box UV, os et animations), un modele, trois textures (rouge, bleue, verte) et un bloc a eux.
# Ecrit atelier/modeles/station/valve_station.bbmodel, textures/entity/valve_station_{rouge,bleu,vert,orange,cyan}.png et l'icone textures/item/valve_station.png. Ensuite :
#   python atelier/scripts/bbmodel_vers_java.py atelier/modeles/station/valve_station.bbmodel atelier/donjon/arene/src/main/java/fr/atelier/grappin --nom ValveStation
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
VERT = [(60, 200, 90), (110, 240, 130), (180, 255, 190), (230, 255, 235)]
PEINTURES = {"rouge": [(110, 18, 18), (150, 28, 26), (182, 40, 36), (214, 70, 60)],
             "bleu": [(18, 48, 110), (28, 74, 160), (44, 104, 202), (92, 154, 236)],
             "vert": [(20, 84, 36), (32, 120, 52), (52, 160, 76), (110, 206, 124)],
             "orange": [(130, 60, 10), (178, 92, 18), (222, 128, 30), (250, 176, 90)],
             "cyan": [(10, 84, 96), (20, 124, 140), (36, 168, 184), (110, 218, 228)]}


def peintre(matiere, peinture):
    """f(face, x, y, l, h, alea) -> couleur ; face : 0 nord, 1 sud, 2 est, 3 ouest, 4 dessus, 5 dessous."""
    def fonte(face, x, y, l, h, a):
        return FONTE[a.choice((0, 1, 1, 2))]

    def fonte_rivets(face, x, y, l, h, a):
        if l > 4 and h > 4 and x in (1, l - 2) and y in (1, h - 2):
            return LAITON[3]
        return fonte(face, x, y, l, h, a)

    def laiton(face, x, y, l, h, a):
        return LAITON[3] if face == 4 and a.random() < 0.2 else LAITON[a.choice((1, 2, 2, 1, 2))]

    def acier(face, x, y, l, h, a):
        return ACIER[3] if x == 0 else ACIER[a.choice((1, 2, 2))]

    def peint(face, x, y, l, h, a):
        return peinture[3] if (x + y) % 5 == 0 and a.random() < 0.5 else peinture[a.choice((1, 2, 2, 1))]

    def lueur(face, x, y, l, h, a):
        return VERT[3] if x == l // 2 and y == h // 2 else VERT[a.choice((0, 1, 2))]

    return {"fonte": fonte, "fonte_rivets": fonte_rivets, "laiton": laiton, "acier": acier, "peint": peint, "lueur": lueur}[matiere]


class ModeleValve(Modele):
    peinture = PEINTURES["rouge"]

    def texture(self, places, gemme=None):
        img = Image.new("RGBA", (self.taille, self.taille), (0, 0, 0, 0))
        p = img.load()
        for k, ((_, _, mat, de, a, _), (u, v)) in enumerate(zip(self.cubes, places)):
            w, h, d = (int(math.ceil(a[i] - de[i])) for i in range(3))
            f = peintre(mat, self.peinture)
            alea = random.Random(k * 7919)
            faces = ((u + d, v + d, w, h), (u + 2 * d + w, v + d, w, h), (u, v + d, d, h), (u + d + w, v + d, d, h), (u + d, v, w, d), (u + d + w, v, w, d))
            for face, (fu, fv, fl, fh) in enumerate(faces):
                for yy in range(fh):
                    for xx in range(fl):
                        p[fu + xx, fv + yy] = f(face, xx, yy, fl, fh, alea) + (255,)
        return img


def valve():
    m = ModeleValve("valve_station", 128)
    m.os_("racine", None, (0, 0, 0))
    m.os_("socle", "racine", (0, 0, 0))
    m.os_("roue", "socle", (0, 12.5, 0))
    m.cube("socle", "platine", "fonte_rivets", (-7, 0, -7), (7, 2, 7))
    for k, (x, z) in enumerate(((-6, -6), (5, -6), (-6, 5), (5, 5))):
        m.cube("socle", f"boulon{k}", "laiton", (x, 2, z), (x + 1, 2.5, z + 1))
    m.cube("socle", "tuyau", "fonte", (-2.5, 2, -2.5), (2.5, 11, 2.5))
    m.cube("socle", "bride_basse", "laiton", (-3.5, 4, -3.5), (3.5, 5.5, 3.5))
    m.cube("socle", "bride_haute", "laiton", (-3.5, 8, -3.5), (3.5, 9.5, 3.5))
    m.cube("socle", "tubulure", "fonte", (-2, 5.5, -7), (2, 8, -2.5))                  # la tubulure qui part vers le mur
    m.cube("socle", "tubulure_bride", "laiton", (-2.75, 5, -7), (2.75, 8.5, -6))
    m.cube("socle", "colonne", "acier", (-0.75, 11, -0.75), (0.75, 12.5, 0.75))
    m.cube("socle", "lunette_voyant", "laiton", (3.5, 2.5, -1.5), (4.5, 5.5, 1.5))
    m.cube("socle", "lueur_voyant", "lueur", (4.5, 3, -1), (5, 5, 1))
    m.cube("roue", "moyeu", "laiton", (-1.75, 11.5, -1.75), (1.75, 14, 1.75))
    m.cube("roue", "ecrou", "acier", (-1, 14, -1), (1, 15, 1))
    for nom, (de, a) in {"jante_n": ((-2.5, 12.2, -6), (2.5, 13.8, -4.7)), "jante_s": ((-2.5, 12.2, 4.7), (2.5, 13.8, 6)),
                         "jante_e": ((4.7, 12.2, -2.5), (6, 13.8, 2.5)), "jante_o": ((-6, 12.2, -2.5), (-4.7, 13.8, 2.5))}.items():
        m.cube("roue", nom, "peint", de, a)
    for nom, (cx, cz, ang) in {"coin_ne": (3.9, -3.9, 45), "coin_se": (3.9, 3.9, -45), "coin_no": (-3.9, -3.9, -45), "coin_so": (-3.9, 3.9, 45)}.items():
        m.cube("roue", nom, "peint", (cx - 2.1, 12.2, cz - 0.65), (cx + 2.1, 13.8, cz + 0.65), ("y", ang))
    m.cube("roue", "rayon_x", "fonte", (-5, 12.6, -0.6), (5, 13.4, 0.6))
    m.cube("roue", "rayon_z", "fonte", (-0.6, 12.6, -5), (0.6, 13.4, 5))
    m.cube("roue", "poignee", "peint", (4.2, 13.8, -0.7), (5.4, 16.2, 0.7))
    anim = {"tourner": (2.0, "hold", {"roue": {"rotation": [(0, (0, 0, 0)), (0.3, (0, 90, 0)), (1.6, (0, 900, 0)), (2.0, (0, 1080, 0))]}})}
    return m, anim


def icone(peinture):
    """Icone 16 x 16 de l'objet : le volant vu de dessus et de trois quarts, sur sa tuyauterie de fonte."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = img.load()
    for x in range(3, 13):
        p[x, 14], p[x, 15] = FONTE[2] + (255,), FONTE[0] + (255,)
    for x in range(6, 10):
        for y in range(8, 14):
            p[x, y] = FONTE[1] + (255,)
    for x in range(5, 11):
        p[x, 11] = LAITON[2] + (255,)
    for x in range(2, 14):
        p[x, 5], p[x, 6] = peinture[2] + (255,), peinture[1] + (255,)
    for y in range(3, 9):
        p[1, y] = p[14, y] = peinture[1] + (255,)
        p[2, y] = p[13, y] = peinture[2] + (255,)
    for x in range(4, 12):
        p[x, 2], p[x, 3] = peinture[2] + (255,), peinture[1] + (255,)
        p[x, 8] = peinture[0] + (255,)
    p[7, 5], p[8, 5], p[7, 6], p[8, 6] = [LAITON[3] + (255,)] * 4
    return img


def main():
    SORTIE.mkdir(parents=True, exist_ok=True)
    (ASSETS / "entity").mkdir(parents=True, exist_ok=True)
    (ASSETS / "item").mkdir(parents=True, exist_ok=True)
    m, anim = valve()
    places = m.ranger()
    for nom, peinture in PEINTURES.items():
        m.peinture = peinture
        tex = m.texture(places)
        tex.save(ASSETS / "entity" / f"valve_station_{nom}.png")
        if nom == "rouge":
            (SORTIE / "valve_station.bbmodel").write_text(json.dumps(bbmodel(m, places, tex, anim), indent=1) + "\n", encoding="utf-8")
    icone(PEINTURES["rouge"]).save(ASSETS / "item" / "valve_station.png")
    print("valve_station.bbmodel, textures entity/valve_station_{rouge,bleu,vert}.png et item/valve_station.png ecrits")


if __name__ == "__main__":
    main()
