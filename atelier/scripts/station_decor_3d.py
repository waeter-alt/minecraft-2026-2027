# [claude-build] Blocs de decor du Donjon de la Station (station de pompage steampunk), modeles Blockbench « Java Block » :
#   pompe_station : grosse pompe centrifuge sur socle (carter de cuivre a brides, moteur de fonte, volant, tuyaux), 2,75 x 2 x 1,9 blocs
#                   de modele sur un seul bloc de collision ; le volant est un modele a part, que le jeu fait tourner (PompeRendu) ;
#   chaudiere     : chaudiere de cuivre rivete (porte de foyer, manometre, soupape, cheminee), 2 blocs de haut de modele ;
#   cadran        : manometre mural, cuivre et verre.
# Ecrit atelier/modeles/station/<bloc>.bbmodel (textures incluses) et les textures (16 x 16, a carreler) dans
# assets/donjon/textures/block/station/. Ensuite : atelier/scripts/bbmodel_vers_bloc.py (commandes dans station_decor_modeles.sh).
# Reperes (comme un fourneau de Minecraft) : l'avant regarde le NORD (z petit), le dos est en z = 16 (le mur, pour le cadran).
# Les modeles debordent du bloc (Minecraft accepte -16..32) : les cubes sont coupes aux multiples de 16, pour que chaque morceau
# ait un UV dans 0..16 (projection des coordonnees, comme l'auto-UV de Blockbench, sur des textures qui se carrelent) ;
# « plein » = toute la texture sur la face (cadran). Une fois retouches dans Blockbench, les .bbmodel sont la source.
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
TEXTURES = RACINE / "atelier/donjon/arene/src/main/resources/assets/donjon/textures/block/station"
GRAINE = uuid.UUID("5a7e0000-0b0b-4c1e-9a11-00000000c0de")
FACES = ("north", "east", "south", "west", "up", "down")
COUPE = {0: ("east", "west"), 1: ("up", "down"), 2: ("south", "north")}   # faces interieures d'une coupe (morceau bas, morceau haut)


# ------------------------------------------------------------------------------------------------ textures
def bruit(pal, poids, graine):
    a = random.Random(graine)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), pal[a.choices(range(len(pal)), poids)[0]] + (255,))
    return img


def rivets(base, tete, ombre):
    img = base.copy()
    for y in (2, 10):
        for x in (2, 6, 10, 14):
            xx = (x + (2 if y == 10 else 0)) % 16
            img.putpixel((xx, y), tete + (255,))
            img.putpixel((xx, y + 1), ombre + (255,))
    return img


def textures():
    t = {}
    t["cuivre"] = bruit([(170, 98, 52), (190, 112, 62), (212, 134, 80), (146, 82, 44), (96, 150, 128)], [5, 6, 3, 2, 0.25], 1)
    for x in range(16):                                                         # filets polis
        t["cuivre"].putpixel((x, 3), (232, 160, 104, 255))
        t["cuivre"].putpixel((x, 11), (226, 150, 96, 255))
    t["cuivre_rivets"] = rivets(t["cuivre"], (236, 176, 112), (110, 60, 32))
    t["fonte"] = bruit([(50, 52, 56), (62, 64, 70), (44, 45, 49), (76, 78, 84)], [5, 5, 3, 1], 2)
    t["fonte_rivets"] = rivets(t["fonte"], (150, 120, 60), (34, 34, 38))
    t["laiton"] = bruit([(184, 144, 58), (204, 166, 76), (226, 192, 100), (150, 112, 42)], [5, 5, 2, 2], 3)
    grille = Image.new("RGBA", (16, 16), (0, 0, 0, 0))                           # barreaux de fonte, jours transparents
    for y in range(16):
        for x in range(16):
            if x % 4 in (0, 1) or y % 8 == 0:
                grille.putpixel((x, y), (54, 56, 60, 255) if x % 4 == 0 or y % 8 == 0 else (80, 82, 88, 255))
    t["grille"] = grille
    t["cadran"] = cadran()
    t["foyer_eteint"] = bruit([(40, 30, 28), (58, 40, 34), (30, 24, 24), (86, 40, 26)], [5, 4, 4, 1], 11)
    t["voyant_eteint"] = bruit([(30, 64, 40), (40, 80, 50), (24, 50, 32)], [5, 3, 2], 12)
    voyant = bruit([(120, 255, 140), (170, 255, 180), (90, 230, 110)], [5, 3, 2], 13)
    for y in range(3, 6):
        for x in range(4, 7):
            voyant.putpixel((x, y), (235, 255, 235, 255))                       # reflet du globe
    t["voyant_allume"] = voyant
    return t


def cadran():
    """Cadran de manometre : bord de cuivre, fond creme, graduations, zone rouge, aiguille, reflet du verre."""
    img = Image.new("RGBA", (16, 16), (60, 40, 26, 255))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d < 6.6:
                img.putpixel((x, y), (238, 232, 212, 255))
            elif d < 7.7:
                img.putpixel((x, y), (204, 128, 74, 255))
    for k in range(9):                                                           # graduations de -135 a +135 degres
        ang = math.radians(-135 + k * 33.75)
        img.putpixel((int(8 + 5.6 * math.sin(ang)), int(8 - 5.6 * math.cos(ang))), (30, 30, 30, 255))
    for k in range(3):
        ang = math.radians(75 + k * 25)
        img.putpixel((int(8 + 5.2 * math.sin(ang)), int(8 - 5.2 * math.cos(ang))), (200, 30, 30, 255))   # zone rouge
    for r in range(1, 6):                                                        # aiguille vers 2 h
        ang = math.radians(52)
        img.putpixel((int(8 + r * math.sin(ang)), int(8 - r * math.cos(ang))), (196, 24, 24, 255))
    img.putpixel((7, 7), (30, 30, 30, 255))
    img.putpixel((8, 8), (30, 30, 30, 255))
    for k in range(4):                                                           # reflet du verre, en haut a gauche
        x, y = 3 + k, 6 - k
        r, g, b, _ = img.getpixel((x, y))
        img.putpixel((x, y), (min(255, r + 18), min(255, g + 22), min(255, b + 30), 255))
    return img


def feu():
    """Bande animee de 4 images (feu_allume.png.mcmeta) : braises et flammes derriere la grille du foyer."""
    img = Image.new("RGBA", (16, 64))
    for f in range(4):
        a = random.Random(100 + f)
        for y in range(16):
            for x in range(16):
                chaud = (15 - y) / 15 + a.uniform(-0.25, 0.25) + 0.15 * math.sin(x * 0.9 + f * 1.7)
                c = (255, 236, 150) if chaud > 0.85 else (255, 180, 60) if chaud > 0.55 else (232, 104, 24) if chaud > 0.25 else (150, 40, 12)
                img.putpixel((x, f * 16 + y), c + (255,))
    return img


# ------------------------------------------------------------------------------------------------ modele
class Modele:
    def __init__(self, nom):
        self.nom = nom
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
        if rot is not None:
            axe, angle, o = rot
            rot = (axe, angle, tuple(o) if o else tuple((de[i] + a[i]) / 2 for i in range(3)))   # origine commune aux morceaux
        self._couper(nom, de, a, f, rot)

    def _couper(self, nom, de, a, f, rot):
        for i in range(3):
            for b in (-16.0, 0.0, 16.0, 32.0):
                if de[i] < b < a[i]:
                    bas, haut = COUPE[i]
                    a1, de2 = list(a), list(de)
                    a1[i], de2[i] = b, b
                    self._couper(nom + f"_{'xyz'[i]}a", de, a1, {k: v for k, v in f.items() if k != bas}, rot)
                    self._couper(nom + f"_{'xyz'[i]}b", de2, a, {k: v for k, v in f.items() if k != haut}, rot)
                    return
        self.cubes.append((nom, de, a, f, rot))

    def cylindre_y(self, nom, x0, z0, x1, z1, y0, y1, tex, c=2.0, faces=None):
        """Cylindre vertical a la Minecraft : carre aux coins coupes, en trois cubes qui ne se recouvrent pas."""
        self.cube(nom, (x0 + c, y0, z0), (x1 - c, y1, z1), tex, faces)
        self.cube(nom + "_g", (x0, y0, z0 + c), (x0 + c, y1, z1 - c), tex)
        self.cube(nom + "_d", (x1 - c, y0, z0 + c), (x1, y1, z1 - c), tex)

    def cylindre_x(self, nom, x0, x1, y0, z0, y1, z1, tex, c=2.0):
        """Cylindre couche le long de x (carter de la pompe, brides)."""
        self.cube(nom, (x0, y0 + c, z0), (x1, y1 - c, z1), tex)
        self.cube(nom + "_h", (x0, y1 - c, z0 + c), (x1, y1, z1 - c), tex)
        self.cube(nom + "_b", (x0, y0, z0 + c), (x1, y0 + c, z1 - c), tex)


def plage(a, b):
    """Ramene [a, b] (dans une seule case de 16) dans 0..16 pour l'UV d'une texture qui se carrele."""
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
            origine = list(o)
        elements.append({"name": nom, "box_uv": False, "rescale": False, "locked": False, "from": de, "to": a, "autouv": 0,
                         "color": k % 8, "origin": origine, "rotation": r, "faces": faces, "type": "cube", "uuid": ident(m.nom, k, nom)})
    textures_bb = []
    for n in noms:
        img = tex[n]
        tampon = io.BytesIO()
        img.save(tampon, "PNG")
        textures_bb.append({"path": "", "name": n + ".png", "folder": "block/station", "namespace": "donjon", "id": str(index[n]),
                            "width": img.width, "height": img.height, "uv_width": 16, "uv_height": 16, "particle": False,
                            "render_mode": "default", "visible": True, "internal": True, "saved": True, "uuid": ident(m.nom, "tex", n),
                            "source": "data:image/png;base64," + base64.b64encode(tampon.getvalue()).decode()})
    return {"meta": {"format_version": "4.10", "model_format": "java_block", "box_uv": False}, "name": m.nom, "parent": "block/block",
            "ambientocclusion": False, "front_gui_light": False, "visible_box": [3, 2, 0], "resolution": {"width": 16, "height": 16},
            "elements": elements, "outliner": [e["uuid"] for e in elements], "textures": textures_bb}


def anneau_x(m, nom, x0, x1, cy, cz, r_in, r_out, tex):
    """Anneau dans le plan y-z (axe x), rasterise rangee par rangee : un cube par suite de cases pleines."""
    n = int(math.ceil(r_out))
    for j in range(-n, n):
        debut = None
        for i in range(-n, n + 1):
            d = math.hypot(i + 0.5, j + 0.5) if i < n else 99
            dans = r_in < d < r_out
            if dans and debut is None:
                debut = i
            if not dans and debut is not None:
                m.cube(f"{nom}_{j + n}_{debut + n}", (x0, cy + j, cz + debut), (x1, cy + j + 1, cz + i), tex)
                debut = None


# ------------------------------------------------------------------------------------------------ les blocs
def pompe_station():
    m = Modele("pompe_station")
    m.cube("socle", (-14, 0, -6), (30, 3, 22), "fonte_rivets")
    for k, (x, z) in enumerate(((-13, -5), (28, -5), (-13, 20), (28, 20))):
        m.cube(f"boulon{k}", (x, 3, z), (x + 1, 4, z + 1), "laiton")
    # Carter : cylindre couche (axe x), centre y=14 z=8, rayon 9 ; brides rivetees aux deux bouts.
    m.cylindre_x("carter", -2, 14, 5, -1, 23, 17, "cuivre", c=2.5)
    m.cylindre_x("bride_g", -3, -2, 4, -2, 24, 18, "fonte_rivets", c=3)
    m.cylindre_x("bride_d", 14, 15, 4, -2, 24, 18, "fonte_rivets", c=3)
    m.cube("ceinture", (5, 4.5, -1.5), (7, 23.5, 17.5), "laiton")
    # Refoulement vers l'avant, aspiration vers le haut (brides pour y raccorder des tuyaux).
    m.cube("refoulement", (3, 9, -8), (9, 15, -1), "cuivre")
    m.cube("bride_refoulement", (2, 8, -9), (10, 16, -8), "laiton")
    m.cube("aspiration", (3, 23, 5), (9, 30, 11), "cuivre")
    m.cube("bride_aspiration", (2, 30, 4), (10, 32, 12), "laiton")
    m.cube("collet_aspiration", (2.5, 22.5, 4.5), (9.5, 24, 11.5), "laiton")
    # Moteur de fonte a cerclages de cuivre, accouplement de laiton, voyant de marche sur le dessus.
    m.cube("moteur", (16, 3, 1), (28, 19, 15), "fonte")
    for k, x in enumerate((17.5, 21, 24.5)):
        m.cube(f"cerclage{k}", (x, 2.5, 0.5), (x + 1, 19.5, 15.5), "cuivre")
    m.cube("flasque", (28, 5, 3), (29.5, 17, 13), "fonte_rivets")
    m.cube("accouplement", (14.5, 11, 5), (16, 17, 11), "laiton")
    m.cube("plaque", (19, 9, 0.5), (24, 12, 1), "laiton")
    m.cube("socle_voyant", (21, 19, 6), (24, 20, 9), "fonte")
    m.cube("voyant", (21.5, 20, 6.5), (23.5, 22.5, 8.5), "voyant_eteint")
    # Manometre sur le carter.
    m.cube("tige_cadran", (10, 22, 2), (11, 25, 3), "laiton")
    m.cube("lunette", (8, 25, 1.5), (14, 31, 2.5), "cuivre")
    m.cube("cadran", (8.5, 25.5, 1), (13.5, 30.5, 1.5), "cuivre", faces={"north": ("cadran", "plein")})
    # Arbre et palier du volant (a gauche) ; le volant lui-meme (cubes volant_*) est exporte a part et tourne en jeu.
    m.cube("arbre", (-9, 13, 7), (-3, 15, 9), "fonte")
    m.cube("palier", (-7, 3, 5.5), (-5, 13, 10.5), "fonte_rivets")
    m.cube("chapeau_palier", (-7.5, 12, 5), (-4.5, 16, 11), "laiton")
    anneau_x(m, "volant_jante", -11, -9, 14, 8, 8.2, 10.0, "fonte")
    m.cube("volant_rayon_v", (-10.75, 5, 7.25), (-9.25, 23, 8.75), "fonte")
    m.cube("volant_rayon_h", (-10.75, 13.25, -1), (-9.25, 14.75, 17), "fonte")
    m.cube("volant_rayon_d1", (-10.75, 5, 7.25), (-9.25, 23, 8.75), "fonte", rot=("x", 45, (-10, 14, 8)))
    m.cube("volant_rayon_d2", (-10.75, 5, 7.25), (-9.25, 23, 8.75), "fonte", rot=("x", -45, (-10, 14, 8)))
    m.cube("volant_moyeu", (-11.5, 12, 6), (-8.5, 16, 10), "laiton")
    m.cube("volant_poignee", (-12.5, 21, 7.5), (-11, 23.5, 8.5), "laiton")
    return m


def chaudiere():
    m = Modele("chaudiere")
    m.cube("socle", (0.5, 0, 0.5), (15.5, 2, 15.5), "fonte")
    m.cylindre_y("corps", 1.5, 1.5, 14.5, 14.5, 2, 26, "cuivre_rivets", c=2.5)
    for k, y in enumerate((9, 17, 24)):
        m.cylindre_y(f"cerclage{k}", 1, 1, 15, 15, y, y + 1.5, "fonte", c=2.5)
    m.cylindre_y("dome", 3, 3, 13, 13, 26, 28, "cuivre", c=2)
    m.cube("cheminee", (6, 28, 6), (10, 31, 10), "fonte")
    m.cube("chapeau_cheminee", (5.5, 31, 5.5), (10.5, 32, 10.5), "fonte_rivets")
    m.cube("soupape", (10.5, 28, 4.5), (12, 30.5, 6), "laiton")
    m.cube("foyer", (5, 5.5, 0.9), (11, 9.5, 1.5), "foyer_eteint", sans=("south",))   # braises : feu_allume quand allumee
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


def cadran_mural():
    m = Modele("cadran")
    m.cube("boitier", (4, 3, 13), (12, 13, 16), "cuivre")
    m.cube("boitier_g", (3, 4, 13), (4, 12, 16), "cuivre")
    m.cube("boitier_d", (12, 4, 13), (13, 12, 16), "cuivre")
    m.cube("cadran", (4, 4, 12.5), (12, 12, 13), "cuivre", faces={"north": ("cadran", "plein")})
    m.cube("vis_haut", (7.5, 12.5, 12.6), (8.5, 13.5, 13), "laiton")
    m.cube("raccord", (7, 0, 14), (9, 3, 15.5), "laiton")
    m.cube("robinet", (6, 1, 13), (10, 2, 14), "fonte")
    return m


def main():
    SORTIE.mkdir(parents=True, exist_ok=True)
    TEXTURES.mkdir(parents=True, exist_ok=True)
    tex = textures()
    tex["feu_allume"] = feu()
    for nom, img in tex.items():
        img.save(TEXTURES / f"{nom}.png")
    (TEXTURES / "feu_allume.png.mcmeta").write_text(json.dumps({"animation": {"frametime": 3, "interpolate": True}}) + "\n", encoding="utf-8")
    for fabrique in (pompe_station, chaudiere, cadran_mural):
        m = fabrique()
        (SORTIE / f"{m.nom}.bbmodel").write_text(json.dumps(bbmodel(m, tex), indent=1) + "\n", encoding="utf-8")
        print(m.nom, len(m.cubes), "cubes")


if __name__ == "__main__":
    main()
