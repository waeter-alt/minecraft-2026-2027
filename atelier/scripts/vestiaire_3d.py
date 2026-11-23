# [claude-build] Blocs du Vestiaire du Donjon de la Station (vestiaire de piscine), modeles Blockbench « Java Block » :
#   douche : pommeau mural chrome (bras qui sort du mur, pommeau rond perce, conduite qui descend au mitigeur sous le bloc) ;
#   lavabo : vasque de ceramique blanche sur pied, mitigeur chrome, contre le mur, moins d'un bloc de large ;
#   banc   : banc de vestiaire, lattes de bois clair sur deux traverses d'acier ; les pieds (pied_g_*, pied_d_*) sont des pieces a part,
#            que le jeu n'affiche qu'aux bouts d'une rangee de bancs (proprietes joint_g / joint_d).
# Meme fabrique que station_decor_3d.py (cubes coupes aux multiples de 16, UV par projection, avant au NORD, mur en z = 16).
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
from station_decor_3d import SORTIE, TEXTURES, Modele, bbmodel, bruit  # noqa: E402


def textures():
    t = {}
    chrome = bruit([(168, 176, 184), (188, 196, 204), (214, 220, 226), (140, 148, 158)], [5, 5, 2, 2], 21)
    for x in range(16):
        chrome.putpixel((x, 5), (244, 248, 250, 255))                         # reflet du metal poli
        chrome.putpixel((x, 6), (226, 232, 236, 255))
    t["chrome"] = chrome
    t["ceramique"] = bruit([(242, 244, 243), (248, 250, 249), (234, 237, 237)], [8, 3, 1], 22)          # emaillee : presque unie
    bois = Image.new("RGBA", (16, 16))
    a = random.Random(23)
    for y in range(16):
        for x in range(16):
            veine = 0.5 + 0.5 * math.sin(x * 0.7 + 1.3 * math.sin(y * 0.9))
            c = (int(212 - 22 * veine), int(184 - 20 * veine), int(136 - 18 * veine))
            if a.random() < 0.06:
                c = (186, 156, 110)
            bois.putpixel((x, y), c + (255,))
    t["bois_clair"] = bois
    t["acier"] = bruit([(70, 74, 80), (84, 88, 94), (60, 63, 68), (104, 108, 114)], [5, 5, 3, 1], 24)
    pommeau = Image.new("RGBA", (16, 16), (196, 204, 210, 255))                # dessous du pommeau : des trous en rond
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d > 7.5:
                pommeau.putpixel((x, y), (150, 158, 166, 255))
            elif x % 3 == 1 and y % 3 == 1 and d < 6.5:
                pommeau.putpixel((x, y), (40, 44, 50, 255))
    t["pommeau"] = pommeau
    return t


def douche():
    m = Modele("douche")
    m.cube("rosace", (6, 11.5, 15), (10, 15.5, 16), "chrome")
    m.cube("bras", (7.5, 13, 9), (8.5, 14, 15), "chrome")
    m.cube("coude", (7.5, 11.5, 8), (8.5, 14, 9), "chrome")
    m.cylindre_y("pommeau", 5, 4.5, 11, 10.5, 10, 11.5, "chrome", c=1.5)
    m.cube("grille", (5.5, 9.9, 5), (10.5, 10, 10), "chrome", faces={"down": ("pommeau", "plein")})
    m.cube("conduite", (7.5, -6, 14.5), (8.5, 11.5, 15.5), "chrome")          # descend le long du mur jusqu'au mitigeur (sous le bloc)
    m.cube("mitigeur", (5, -9, 13), (11, -6, 15.5), "chrome")
    m.cube("manette", (7, -8.5, 12), (9, -6.5, 13), "chrome")
    m.cube("bouton_g", (4, -8.5, 13.5), (5, -6.5, 14.5), "chrome")
    m.cube("bouton_d", (11, -8.5, 13.5), (12, -6.5, 14.5), "chrome")
    return m


def lavabo():
    m = Modele("lavabo")
    m.cylindre_y("pied", 6, 10, 10, 14, 0, 8, "ceramique", c=1)
    m.cube("fond", (2, 8, 5), (14, 9, 15), "ceramique")
    m.cube("bord_avant", (2, 9, 5), (14, 12, 6), "ceramique")
    m.cube("bord_arriere", (2, 9, 14), (14, 12, 15), "ceramique")
    m.cube("bord_g", (2, 9, 6), (3, 12, 14), "ceramique")
    m.cube("bord_d", (13, 9, 6), (14, 12, 14), "ceramique")
    m.cube("dosseret", (2, 12, 14), (14, 14, 15), "ceramique")
    m.cube("bonde", (7.5, 9, 9.5), (8.5, 9.1, 10.5), "chrome")
    m.cube("robinet_socle", (7, 12, 12.5), (9, 13, 14), "chrome")
    m.cube("robinet_colonne", (7.5, 13, 12.75), (8.5, 15, 13.75), "chrome")
    m.cube("robinet_bec", (7.5, 14, 9.5), (8.5, 15, 12.75), "chrome")
    m.cube("robinet_levier", (7.5, 15, 12), (8.5, 15.5, 15), "chrome")
    return m


def banc():
    m = Modele("banc")
    for k, (z0, z1) in enumerate(((3, 6), (6.5, 9.5), (10, 13))):         # trois lattes sur toute la longueur : les bancs se raccordent
        m.cube(f"latte{k}", (0, 7, z0), (16, 8.5, z1), "bois_clair")
    for k, z in enumerate((3.5, 11.5)):
        m.cube(f"traverse{k}", (0, 6, z), (16, 7, z + 1), "acier")
    for cote, (x0, x1) in (("g", (1, 2.5)), ("d", (13.5, 15))):            # les pieds : pieces a part (seulement au bout d'une rangee)
        m.cube(f"pied_{cote}_avant", (x0, 0, 3.5), (x1, 6, 5), "acier")
        m.cube(f"pied_{cote}_arriere", (x0, 0, 11), (x1, 6, 12.5), "acier")
        m.cube(f"pied_{cote}_semelle", (x0, 0, 3), (x1, 1, 13), "acier")
        m.cube(f"pied_{cote}_entretoise", (x0, 3, 5), (x1, 4, 11), "acier")
    return m


def main():
    tex = textures()
    for nom, img in tex.items():
        img.save(TEXTURES / f"{nom}.png")
    for fabrique in (douche, lavabo, banc):
        m = fabrique()
        (SORTIE / f"{m.nom}.bbmodel").write_text(json.dumps(bbmodel(m, tex), indent=1) + "\n", encoding="utf-8")
        print(m.nom, len(m.cubes), "cubes")


if __name__ == "__main__":
    main()
