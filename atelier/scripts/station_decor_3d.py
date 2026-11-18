# [claude-build] Blocs de decor du Donjon de la Station (station de pompage / piscine, steampunk), modeles Blockbench « Java Block » :
# pompe (2 blocs de haut), manometre, echelle de bassin, bouee, panneau de profondeur, filtre a sable, chaudiere (2 blocs de haut).
# Ecrit atelier/modeles/station/<bloc>.bbmodel (textures incluses) et les textures de matiere (16 x 16, a carreler) dans
# assets/grappin/textures/block/station/. Ensuite, pour chaque modele :
#   python atelier/scripts/bbmodel_vers_bloc.py atelier/modeles/station/<bloc>.bbmodel
# Reperes du modele (comme un fourneau de Minecraft) : l'avant regarde le NORD (z = 0), le dos est en z = 16 (le mur pour les blocs
# muraux) ; y = 0 a 16 = le bloc, y = 16 a 32 = le bloc du dessus pour les modeles de deux blocs (ou ce qui depasse, pour l'echelle). Les cubes qui traversent y = 16 sont
# coupes en deux ici, pour que l'exporteur n'ait qu'a les repartir entre le bas et le haut. UV : projection des coordonnees (comme
# l'auto-UV de Blockbench) sur des textures qui se carrelent ; « plein » = toute la texture sur la face (cadran, panneau).
# Une fois retouches dans Blockbench, les .bbmodel sont la source : ne plus relancer ce script.
import base64
import io
import json
import math
import random
import uuid
from pathlib import Path

from PIL import Image

RACINE = Path(__file__).resolve().parents[2]
SORTIE = RACINE / "atelier/modeles/station"
TEXTURES = RACINE / "atelier/donjon/arene/src/main/resources/assets/grappin/textures/block/station"
GRAINE = uuid.UUID("5a7e0000-0b0b-4c1e-9a11-00000000c0de")
FACES = ("north", "east", "south", "west", "up", "down")


# ------------------------------------------------------------------------------------------------ textures de matiere
def bruit(pal, poids, graine):
    a = random.Random(graine)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), pal[a.choices(range(len(pal)), poids)[0]] + (255,))
    return img


def textures():
    t = {}
    t["cuivre"] = bruit([(170, 98, 52), (190, 112, 62), (212, 134, 80), (146, 82, 44), (96, 150, 128)], [5, 6, 3, 2, 0.25], 1)
    for x in range(16):                                                         # filets brillants : cuivre poli en bandes
        t["cuivre"].putpixel((x, 3), (232, 160, 104, 255))
        t["cuivre"].putpixel((x, 11), (226, 150, 96, 255))
    t["fonte"] = bruit([(50, 52, 56), (62, 64, 70), (44, 45, 49), (76, 78, 84)], [5, 5, 3, 1], 2)
    t["fonte_rivets"] = t["fonte"].copy()
    for y in (2, 10):
        for x in (2, 6, 10, 14):
            xx = x + (2 if y == 10 else 0)
            t["fonte_rivets"].putpixel((xx % 16, y), (150, 120, 60, 255))                    # tete de rivet en laiton
            t["fonte_rivets"].putpixel((xx % 16, y + 1), (34, 34, 38, 255))                  # son ombre
    t["laiton"] = bruit([(184, 144, 58), (204, 166, 76), (226, 192, 100), (150, 112, 42)], [5, 5, 2, 2], 3)
    t["inox"] = bruit([(178, 186, 192), (196, 204, 210), (222, 228, 232), (150, 158, 166)], [5, 5, 2, 2], 4)
    for x in range(16):
        t["inox"].putpixel((x, 7), (240, 244, 246, 255))                                     # reflet du tube
    t["acier_vert"] = bruit([(56, 92, 80), (66, 106, 92), (48, 80, 70), (86, 126, 110)], [5, 5, 3, 1], 5)
    for y in (1, 14):
        for x in range(1, 16, 4):
            t["acier_vert"].putpixel((x, y), (170, 150, 90, 255))
    t["sable"] = bruit([(214, 196, 140), (198, 180, 124), (228, 214, 164), (170, 150, 104)], [5, 4, 2, 1], 6)
    t["rouge"] = bruit([(198, 34, 34), (214, 46, 44), (176, 26, 28)], [6, 3, 2], 7)
    t["blanc"] = bruit([(236, 236, 230), (246, 246, 242), (216, 218, 212)], [6, 3, 2], 8)
    t["caoutchouc"] = bruit([(36, 36, 38), (46, 46, 48)], [3, 1], 9)
    grille = Image.new("RGBA", (16, 16), (0, 0, 0, 0))                           # grille : barreaux de fonte, jours transparents
    for y in range(16):
        for x in range(16):
            if x % 4 in (0, 1) or y % 8 == 0:
                grille.putpixel((x, y), (54, 56, 60, 255) if x % 4 == 0 or y % 8 == 0 else (80, 82, 88, 255))
    t["grille"] = grille
    cadran = Image.new("RGBA", (16, 16), (40, 34, 26, 255))                      # cadran de manometre : rond, graduations, aiguille rouge
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d < 6.6:
                cadran.putpixel((x, y), (238, 232, 212, 255))
            elif d < 7.6:
                cadran.putpixel((x, y), (204, 166, 76, 255))
    for k in range(9):                                                           # graduations de -135 a +135 degres
        ang = math.radians(-135 + k * 33.75)
        x, y = 8 + 5.6 * math.sin(ang), 8 - 5.6 * math.cos(ang)
        cadran.putpixel((int(x), int(y)), (30, 30, 30, 255))
    for r in range(1, 6):                                                        # aiguille vers 2 h (pression haute)
        ang = math.radians(52)
        cadran.putpixel((int(8 + r * math.sin(ang)), int(8 - r * math.cos(ang))), (196, 24, 24, 255))
    cadran.putpixel((7, 7), (30, 30, 30, 255))
    cadran.putpixel((8, 8), (30, 30, 30, 255))
    for x in range(10, 13):
        cadran.putpixel((x, 11), (196, 24, 24, 255))                             # zone rouge
    t["cadran"] = cadran
    t["carrelage"] = carrelage()
    return t


def carrelage():
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), (178, 186, 190, 255) if x % 4 == 3 or y % 4 == 3 else (236, 242, 244, 255))
    return img


def feu():
    """Bande animee de 4 images (feu.png.mcmeta) : braises et flammes derriere la grille du foyer."""
    img = Image.new("RGBA", (16, 64))
    for f in range(4):
        a = random.Random(100 + f)
        for y in range(16):
            for x in range(16):
                chaud = (15 - y) / 15 + a.uniform(-0.25, 0.25) + 0.15 * math.sin(x * 0.9 + f * 1.7)
                c = (255, 236, 150) if chaud > 0.85 else (255, 180, 60) if chaud > 0.55 else (232, 104, 24) if chaud > 0.25 else (150, 40, 12)
                img.putpixel((x, f * 16 + y), c + (255,))
    return img


CHIFFRES = {"0": ["111", "101", "101", "101", "111"], "1": ["010", "110", "010", "010", "111"], "2": ["111", "001", "111", "100", "111"],
            "4": ["101", "101", "111", "001", "001"], "8": ["111", "101", "111", "101", "111"], "9": ["111", "101", "111", "001", "111"],
            ",": ["0", "0", "0", "1", "1"], "m": ["00000", "00000", "11110", "10101", "10101"], " ": ["0", "0", "0", "0", "0"]}
PROFONDEURS = {"0_9": "0,9 m", "1_2": "1,2 m", "1_8": "1,8 m", "2_4": "2,4 m"}


def panneau(texte):
    """32 x 32 ; la plaque n'utilise que la moitie haute (32 x 16) : carreaux bleus au bord, blanc au milieu, chiffres bleu nuit."""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    for y in range(16):
        for x in range(32):
            bord = x < 2 or x > 29 or y < 2 or y > 13
            joint = x % 8 == 7 or y % 8 == 7
            c = (40, 110, 170) if bord else (240, 244, 246)
            if joint:
                c = tuple(int(v * 0.82) for v in c)
            img.putpixel((x, y), c + (255,))
    largeur = sum(len(CHIFFRES[c][0]) + 1 for c in texte) - 1
    x0, y0 = (32 - largeur) // 2, 5
    for c in texte:
        motif = CHIFFRES[c]
        for dy, ligne in enumerate(motif):
            for dx, v in enumerate(ligne):
                if v == "1":
                    img.putpixel((x0 + dx, y0 + dy), (20, 40, 90, 255))
        x0 += len(motif[0]) + 1
    for y in range(16, 32):
        for x in range(32):
            img.putpixel((x, y), img.getpixel((x, y - 16)))                     # moitie basse = copie (jamais affichee)
    return img


# ------------------------------------------------------------------------------------------------ modele
class Modele:
    def __init__(self, nom, deux_blocs=False):
        self.nom, self.deux_blocs = nom, deux_blocs
        self.cubes = []                              # (nom, de, a, faces {face: (texture, plein)}, rotation (axe, angle, origine) | None)

    def cube(self, nom, de, a, tex, faces=None, rot=None, sans=()):
        """faces : {face: texture} ou {face: (texture, 'plein')} qui remplacent tex ; sans : faces a ne pas creer."""
        f = {}
        for face in FACES:
            if face in sans:
                continue
            v = (faces or {}).get(face, tex)
            f[face] = v if isinstance(v, tuple) else (v, None)
        de, a = [float(v) for v in de], [float(v) for v in a]
        if self.deux_blocs and rot is None and de[1] < 16 < a[1]:               # coupe a la limite des deux blocs
            self.cubes.append((nom + "_bas", de, [a[0], 16.0, a[2]], {k: v for k, v in f.items() if k != "up"}, None))
            self.cubes.append((nom + "_haut", [de[0], 16.0, de[2]], a, {k: v for k, v in f.items() if k != "down"}, None))
        else:
            assert not self.deux_blocs or rot is None or a[1] <= 16 or de[1] >= 16, nom
            self.cubes.append((nom, de, a, f, rot))

    def cylindre(self, nom, x0, z0, x1, z1, y0, y1, tex, c=2.0, faces=None):
        """Cylindre vertical a la Minecraft : un carre aux coins coupes, en trois cubes qui ne se recouvrent pas."""
        self.cube(nom, (x0 + c, y0, z0), (x1 - c, y1, z1), tex, faces)
        self.cube(nom + "_g", (x0, y0, z0 + c), (x0 + c, y1, z1 - c), tex)
        self.cube(nom + "_d", (x1 - c, y0, z0 + c), (x1, y1, z1 - c), tex)


def plage(a, b):
    """Ramene [a, b] (longueur <= 16) dans 0..16 pour l'UV d'une texture qui se carrele."""
    s = math.floor(a / 16) * 16
    if b - s > 16:
        s = b - 16
    return a - s, b - s


def uv(face, de, a):
    x0, x1 = plage(de[0], a[0])
    y0, y1 = plage(de[1], a[1])
    z0, z1 = plage(de[2], a[2])
    return {"north": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
            "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0], "west": [z0, 16 - y1, z1, 16 - y0],
            "up": [x0, z0, x1, z1], "down": [x0, 16 - z1, x1, 16 - z0]}[face]


def ident(*p):
    return str(uuid.uuid5(GRAINE, "/".join(str(x) for x in p)))


def bbmodel(m, tex):
    noms = sorted({t for _, _, _, f, _ in m.cubes for t, _ in f.values()})
    index = {n: i for i, n in enumerate(noms)}
    elements = []
    for k, (nom, de, a, f, rot) in enumerate(m.cubes):
        faces = {}
        for face in FACES:
            if face in f:
                t, plein = f[face]
                faces[face] = {"uv": [0, 0, 16, 16] if plein else uv(face, de, a), "texture": index[t]}
            else:
                faces[face] = {"uv": [0, 0, 0, 0], "texture": None}
        r, origine = [0, 0, 0], [(de[i] + a[i]) / 2 for i in range(3)]
        if rot:
            axe, angle, o = rot
            r["xyz".index(axe)] = angle
            origine = list(o) if o else origine
        elements.append({"name": nom, "box_uv": False, "rescale": False, "locked": False, "from": de, "to": a, "autouv": 0,
                         "color": k % 8, "origin": origine, "rotation": r, "faces": faces, "type": "cube", "uuid": ident(m.nom, k, nom)})
    textures_bb = []
    for n in noms:
        img = tex[n]
        tampon = io.BytesIO()
        img.save(tampon, "PNG")
        textures_bb.append({"path": "", "name": n + ".png", "folder": "block/station", "namespace": "grappin", "id": str(index[n]),
                            "width": img.width, "height": img.height, "uv_width": 16, "uv_height": 16, "particle": n == noms[0],
                            "render_mode": "default", "visible": True, "internal": True, "saved": True, "uuid": ident(m.nom, "tex", n),
                            "source": "data:image/png;base64," + base64.b64encode(tampon.getvalue()).decode()})
    return {"meta": {"format_version": "4.10", "model_format": "java_block", "box_uv": False}, "name": m.nom, "parent": "block/block",
            "ambientocclusion": False, "front_gui_light": False, "visible_box": [1, 2 if m.deux_blocs else 1, 0],
            "resolution": {"width": 16, "height": 16}, "deux_blocs": m.deux_blocs,
            "elements": elements, "outliner": [e["uuid"] for e in elements], "textures": textures_bb}


# ------------------------------------------------------------------------------------------------ les blocs
def pompe():
    m = Modele("pompe", deux_blocs=True)
    m.cube("socle", (1, 0, 1), (15, 2, 15), "fonte")
    m.cylindre("corps", 3, 3, 13, 13, 2, 22, "cuivre")
    for k, y in enumerate((2, 11.5, 20)):
        m.cylindre(f"bride{k}", 2, 2, 14, 14, y, y + 2, "fonte_rivets")
    m.cylindre("chapeau", 4, 4, 12, 12, 22, 25, "fonte", c=1.5)
    m.cube("moteur", (5, 25, 4), (11, 30, 12), "fonte")
    for i in range(3):
        m.cube(f"ailette{i}", (4.5, 25.5 + 1.5 * i, 4.5), (11.5, 26.5 + 1.5 * i, 11.5), "laiton")
    m.cube("arbre", (7, 30, 7), (9, 31, 9), "laiton")
    for cote, (x0, x1, b0, b1) in {"o": (0, 3, 0, 1), "e": (13, 16, 15, 16)}.items():       # sorties a gauche et a droite, a brides
        m.cube(f"tuyau_{cote}", (x0, 6, 6), (x1, 10, 10), "cuivre")
        m.cube(f"bride_{cote}", (b0, 5, 5), (b1, 11, 11), "laiton")
    m.cube("tuyau_dos", (6, 13.5, 13), (10, 17.5, 16), "cuivre")
    m.cube("bride_dos", (5, 12.5, 15), (11, 18.5, 16), "laiton")
    m.cube("lunette", (4.5, 13.5, 1.5), (11.5, 20.5, 2.5), "laiton")                          # manometre de facade
    m.cube("cadran", (5, 14, 1), (11, 20, 1.5), "laiton", faces={"north": ("cadran", "plein")})
    m.cube("tige_cadran", (7.5, 17, 2.5), (8.5, 18, 3), "laiton")
    m.cube("plaque", (6, 6.5, 2.5), (10, 9, 3), "laiton")
    m.cube("vanne_axe", (7, 5, 0.5), (9, 7, 3), "fonte")                                      # volant de vanne
    for nom, de, a in (("haut", (5, 8.5, 0), (11, 9.5, 1)), ("bas", (5, 2.5, 0), (11, 3.5, 1)), ("g", (4.5, 3, 0), (5.5, 9, 1)),
                       ("d", (10.5, 3, 0), (11.5, 9, 1)), ("rayon_v", (7.5, 3.5, 0.2), (8.5, 8.5, 0.8)), ("rayon_h", (5.5, 5.5, 0.2), (10.5, 6.5, 0.8))):
        m.cube(f"volant_{nom}", de, a, "laiton")
    return m


def chaudiere():
    m = Modele("chaudiere", deux_blocs=True)
    m.cube("socle", (0.5, 0, 0.5), (15.5, 2, 15.5), "fonte")
    m.cylindre("corps", 1.5, 1.5, 14.5, 14.5, 2, 26, "fonte_rivets", c=2.5)
    for k, y in enumerate((9, 17, 24)):
        m.cylindre(f"cerclage{k}", 1, 1, 15, 15, y, y + 1.5, "cuivre", c=2.5)
    m.cylindre("dome", 3, 3, 13, 13, 26, 28, "fonte", c=2)
    m.cube("cheminee", (6, 28, 6), (10, 32, 10), "cuivre")
    m.cube("soupape", (10.5, 28, 4.5), (12, 30.5, 6), "laiton")
    m.cube("lueur_feu", (5, 5.5, 0.9), (11, 9.5, 1.5), "feu", sans=("south",))                # braises, lumineuses (lueur_*)
    m.cube("grille_foyer", (5, 5.5, 0.4), (11, 9.5, 0.6), "grille", sans=("south",))
    for nom, de, a in (("haut", (4, 9.5, 0.2), (12, 10.5, 1.5)), ("bas", (4, 4.5, 0.2), (12, 5.5, 1.5)), ("g", (4, 5.5, 0.2), (5, 9.5, 1.5)),
                       ("d", (11, 5.5, 0.2), (12, 9.5, 1.5))):
        m.cube(f"porte_{nom}", de, a, "laiton")
    m.cube("poignee", (12, 7, 0), (13, 8, 1.5), "fonte")
    m.cube("cendrier", (5, 2.5, 0.8), (11, 4, 1.5), "fonte")
    m.cube("lunette", (5, 11, 1), (11, 17, 1.5), "laiton")
    m.cube("cadran", (5.5, 11.5, 0.5), (10.5, 16.5, 1), "laiton", faces={"north": ("cadran", "plein")})
    m.cube("tuyau_dos", (6, 20, 14), (10, 24, 16), "cuivre")
    return m


def filtre():
    m = Modele("filtre")
    m.cube("fond", (0, 0, 0), (16, 1, 16), "fonte")
    m.cube("paroi_n", (1.5, 1, 0.5), (14.5, 14, 1.5), "acier_vert")
    m.cube("paroi_s", (1.5, 1, 14.5), (14.5, 14, 15.5), "acier_vert")
    m.cube("paroi_o", (0.5, 1, 1.5), (1.5, 14, 14.5), "acier_vert")
    m.cube("paroi_e", (14.5, 1, 1.5), (15.5, 14, 14.5), "acier_vert")
    for k, (x, z) in enumerate(((0, 0), (14, 0), (0, 14), (14, 14))):
        m.cube(f"montant{k}", (x, 0, z), (x + 2, 15, z + 2), "fonte_rivets")
    m.cube("sable", (1.5, 1, 1.5), (14.5, 11, 14.5), "sable")
    m.cube("grille", (1.5, 13, 1.5), (14.5, 13.5, 14.5), "grille")
    for k, (de, a) in enumerate((((2, 14, 0.2), (14, 15, 1.5)), ((2, 14, 14.5), (14, 15, 15.8)), ((0.2, 14, 2), (1.5, 15, 14)), ((14.5, 14, 2), (15.8, 15, 14)))):
        m.cube(f"rebord{k}", de, a, "laiton")
    m.cube("tuyau", (6, 2, -1), (10, 6, 0.5), "cuivre")                                       # vidange en facade, a vanne
    m.cube("bride", (5, 1, -1.5), (11, 7, -0.5), "laiton")
    m.cube("vanne_axe", (7.5, 6, -0.5), (8.5, 9, 0.5), "fonte")
    m.cube("volant", (5.5, 9, -1), (10.5, 10, 1), "laiton")
    m.cube("lunette", (10.5, 8, -0.2), (14.5, 12, 0.5), "laiton")
    m.cube("cadran", (11, 8.5, -0.6), (14, 11.5, -0.2), "laiton", faces={"north": ("cadran", "plein")})
    return m


def manometre():
    m = Modele("manometre")
    m.cube("boitier", (4, 3, 13), (12, 13, 16), "laiton")
    m.cube("boitier_g", (3, 4, 13), (4, 12, 16), "laiton")
    m.cube("boitier_d", (12, 4, 13), (13, 12, 16), "laiton")
    m.cube("cadran", (4, 4, 12.5), (12, 12, 13), "laiton", faces={"north": ("cadran", "plein")})
    m.cube("vitre_haut", (4.5, 12, 12.4), (11.5, 12.5, 13), "laiton")
    m.cube("raccord", (7, 0, 14), (9, 3, 15.5), "laiton")
    m.cube("robinet", (6, 1, 13), (10, 2, 14), "fonte")
    return m


def bouee():
    m = Modele("bouee")
    m.cube("crochet", (7, 15, 14.5), (9, 16, 16), "fonte")
    for j in range(14):                                                          # anneau rasterise ligne par ligne
        y = 14 - j                                                               # rangee j depuis le haut, la bouee va de y=1 a y=15
        debut = None
        for i in range(15):
            dans, couleur = False, None
            if i < 14:
                dx, dy = i + 0.5 - 7, j + 0.5 - 7
                d = math.hypot(dx, dy)
                dans = 3.4 < d < 7.05
                ang = (math.degrees(math.atan2(dy, dx)) + 360 + 22.5) % 360
                couleur = "rouge" if int(ang // 45) % 2 == 0 else "blanc"
            if debut is not None and (not dans or couleur != debut[1]):
                m.cube(f"anneau_{j}_{debut[0]}", (1 + debut[0], y, 13), (1 + i, y + 1, 16), debut[1])
                debut = None
            if dans and debut is None:
                debut = (i, couleur)
    m.cube("corde", (1, 7.5, 12.6), (15, 8.5, 13), "blanc")
    return m


def panneau_profondeur():
    m = Modele("panneau_profondeur")
    m.cube("plaque", (0, 4, 15), (16, 12, 16), "carrelage", faces={"north": ("panneau_1_2", "moitie")})
    return m


def echelle_bassin():
    m = Modele("echelle_bassin")                       # un seul bloc ; les mains courantes depassent au-dessus et sur la margelle
    for cote, x0 in (("g", 2), ("d", 12.5)):
        x1 = x0 + 1.5
        m.cube(f"montant_{cote}", (x0, 0, 10.25), (x1, 25, 11.75), "inox")
        m.cube(f"coude1_{cote}", (x0, 24.9, 10.9), (x1, 26.4, 13.4), "inox", rot=("x", -45, (x0 + 0.75, 25.6, 12)))
        m.cube(f"main_courante_{cote}", (x0, 25.5, 12.5), (x1, 27, 19), "inox")
        m.cube(f"coude2_{cote}", (x0, 24.9, 18.3), (x1, 26.4, 20.8), "inox", rot=("x", 45, (x0 + 0.75, 25.6, 19.5)))
        m.cube(f"pied_{cote}", (x0, 16, 19.25), (x1, 25, 20.75), "inox")
        m.cube(f"platine_{cote}", (x0 - 0.5, 16, 18.75), (x1 + 0.5, 16.5, 21.25), "inox")
        m.cube(f"butee_{cote}", (x0, 2, 11.75), (x1, 3.5, 16), "caoutchouc")
    for k, y in enumerate((3, 8, 13)):
        m.cube(f"marche{k}", (3.5, y, 8.5), (12.5, y + 1, 13.5), "inox")
    return m


def main():
    SORTIE.mkdir(parents=True, exist_ok=True)
    TEXTURES.mkdir(parents=True, exist_ok=True)
    tex = textures()
    tex["feu"] = feu()
    for code, texte in PROFONDEURS.items():
        tex[f"panneau_{code}"] = panneau(texte)
    for nom, img in tex.items():
        img.save(TEXTURES / f"{nom}.png")
    (TEXTURES / "feu.png.mcmeta").write_text(json.dumps({"animation": {"frametime": 3, "interpolate": True}}) + "\n", encoding="utf-8")
    for fabrique in (pompe, chaudiere, filtre, manometre, bouee, panneau_profondeur, echelle_bassin):
        m = fabrique()
        modele = bbmodel(m, tex)
        if m.nom == "panneau_profondeur":                                       # la plaque n'utilise que la moitie haute de sa texture
            for e in modele["elements"]:
                e["faces"]["north"]["uv"] = [0, 0, 16, 8]
        (SORTIE / f"{m.nom}.bbmodel").write_text(json.dumps(modele, indent=1) + "\n", encoding="utf-8")
        print(m.nom, len(m.cubes), "cubes")


if __name__ == "__main__":
    main()
