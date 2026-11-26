# [claude-build] Les portes et barrages du Donjon de la Station, modeles Blockbench « Java Block » : PLUS AUCUNE grille de bloc vanilla.
#   vanne_station   : la vanne de la Station (une grille a lames dans un cadre de cuivre riveté, d'un bord a l'autre de la case) : ferme les bouches de la Cuve, les jets, les colonnes ;
#                     le datapack la retire (setblock) pour ouvrir. Posee dans le plan x (axis=x) ou z (axis=z).
#   plaque_station  : la plaque de pression lestee (se tenir debout dessus : secteur B) : un bloc plein dont le dessus est une plaque de cuivre a bordure de securite jaune et noire et un voyant
#                     central (ambre quand elle attend, vert allume quand elle est enclenchee), pour qu'on comprenne que c'est interactif (Luca, 25/11) ;
#   planche_station : un barrage de planches clouees en travers d'un passage, que la cape lestee brise d'un coup de tourbillon (tag donjon:cassable).
# Meme fabrique que vestiaire_3d.py (station_decor_3d.py : cubes coupes aux multiples de 16, UV par projection, avant au NORD).
# Ecrit atelier/modeles/station/<bloc>.bbmodel et les textures dans assets/donjon/textures/block/station/. Ensuite :
#   bash atelier/scripts/station_decor_modeles.sh <python>
# Une fois retouches dans Blockbench, les .bbmodel sont la source : ne plus relancer ce script.
import json
import math
import random
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from station_decor_3d import SORTIE, TEXTURES, Modele, bbmodel, bruit, rivets, textures as textures_de_base  # noqa: E402


def textures():
    t = {k: v for k, v in textures_de_base().items() if k in ("cuivre", "cuivre_rivets", "fonte", "fonte_rivets", "laiton")}
    bois = Image.new("RGBA", (16, 16))
    a = random.Random(31)
    for y in range(16):
        for x in range(16):
            veine = 0.5 + 0.5 * math.sin(y * 0.8 + 1.6 * math.sin(x * 0.45))
            c = (int(120 - 26 * veine), int(86 - 20 * veine), int(52 - 14 * veine))
            if a.random() < 0.07:
                c = (78, 54, 32)
            if y % 8 == 0:
                c = (58, 40, 24)                                                        # le joint entre deux planches
            bois.putpixel((x, y), c + (255,))
    t["planche_bois"] = bois
    t["planche_bout"] = bruit([(150, 112, 70), (134, 98, 58), (170, 128, 84)], [4, 4, 2], 33)
    t["clou"] = bruit([(150, 150, 156), (110, 110, 118), (190, 190, 196)], [4, 3, 1], 34)
    t["plaque_haut"] = plaque_haut(False)
    t["plaque_haut_actif"] = plaque_haut(True)
    return t


def plaque_haut(actif):
    """Le dessus d'une plaque lestee (16 x 16, a carreler 3 x 3) : cuivre, bordure de securite jaune et noire en diagonale, un voyant rond au centre, quatre chevrons qui y menent."""
    img = Image.new("RGBA", (16, 16))
    a = random.Random(41)
    for y in range(16):
        for x in range(16):
            cu = (186 + a.randint(-8, 8), 108 + a.randint(-6, 6), 66 + a.randint(-5, 5))
            if x in (0, 1, 14, 15) or y in (0, 1, 14, 15):
                cu = (236, 190, 40) if (x + y) % 6 < 3 else (34, 30, 26)                  # la bordure de securite
            img.putpixel((x, y), cu + (255,))
    centre = (110, 255, 140) if actif else (255, 176, 40)
    halo = (60, 200, 90) if actif else (170, 100, 24)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 2.6:
                img.putpixel((x, y), centre + (255,))
            elif d < 3.8:
                img.putpixel((x, y), halo + (255,))
    for (x, y) in ((7, 3), (8, 3), (7, 12), (8, 12), (3, 7), (3, 8), (12, 7), (12, 8)):  # les chevrons
        img.putpixel((x, y), (70, 40, 24, 255))
    return img


def vanne():
    m = Modele("vanne_station")
    z0, z1 = 5.5, 10.5
    m.cube("cadre_haut", (0, 13, z0), (16, 16, z1), "cuivre_rivets")
    m.cube("cadre_bas", (0, 0, z0), (16, 3, z1), "cuivre_rivets")
    m.cube("montant_g", (0, 3, z0), (2.5, 13, z1), "cuivre")
    m.cube("montant_d", (13.5, 3, z0), (16, 13, z1), "cuivre")
    for k, x in enumerate((3.75, 7.25, 10.75)):                                         # les lames verticales de la grille
        m.cube(f"lame{k}", (x, 3, 7), (x + 1.5, 13, 9), "fonte")
    for k, y in enumerate((5, 9.5)):                                                    # les traverses
        m.cube(f"traverse{k}", (2.5, y, 7.5), (13.5, y + 1.5, 8.5), "fonte_rivets")
    m.cube("axe", (7, 13, 7), (9, 16.5, 9), "laiton")                                   # l'axe de levage, qui depasse en haut
    m.cube("oeil", (6, 15.5, 6.5), (10, 17, 9.5), "laiton")
    for k, (x, y) in enumerate(((1, 1.5), (15, 1.5), (1, 14.5), (15, 14.5))):
        m.cube(f"boulon{k}", (x - 0.6, y - 0.6, z0 - 0.4), (x + 0.6, y + 0.6, z0), "laiton")
    return m


def plaque():
    m = Modele("plaque_station")
    m.cube("plaque", (0, 0, 0), (16, 16, 16), "cuivre", faces={"up": "plaque_haut"})
    return m


def planche():
    m = Modele("planche_station")
    m.cube("poteau_g", (1.5, 0, 5.5), (4.5, 16, 8.5), "planche_bois", faces={"up": "planche_bout", "down": "planche_bout"})
    m.cube("poteau_d", (11.5, 0, 7.5), (14.5, 16, 10.5), "planche_bois", faces={"up": "planche_bout", "down": "planche_bout"})
    for k, (x0, x1, y0, y1, z0, z1) in enumerate(((0, 15, 0.5, 4, 8.5, 10.5), (1, 16, 4.5, 8, 5.5, 7.5), (0, 14, 8.5, 12, 8.5, 10.5), (2, 16, 12.5, 16, 5.5, 7.5))):
        m.cube(f"planche{k}", (x0, y0, z0), (x1, y1, z1), "planche_bois", faces={"east": "planche_bout", "west": "planche_bout"})
    for k, (x, y, z) in enumerate(((3, 2.2, 10.5), (9, 2.2, 10.5), (7, 6.2, 5.5), (13, 6.2, 5.5), (4, 10.2, 10.5), (10, 10.2, 10.5), (8, 14.2, 5.5))):
        m.cube(f"clou{k}", (x - 0.5, y - 0.5, z - (0.4 if z > 8 else 0)), (x + 0.5, y + 0.5, z + (0 if z > 8 else 0.4)), "clou")
    return m


def main():
    tex = textures()
    for nom in ("planche_bois", "planche_bout", "clou", "plaque_haut", "plaque_haut_actif"):
        tex[nom].save(TEXTURES / f"{nom}.png")
    for fabrique in (vanne, planche, plaque):
        m = fabrique()
        (SORTIE / f"{m.nom}.bbmodel").write_text(json.dumps(bbmodel(m, tex), indent=1) + "\n", encoding="utf-8")
        print(m.nom, len(m.cubes), "cubes")


if __name__ == "__main__":
    main()
