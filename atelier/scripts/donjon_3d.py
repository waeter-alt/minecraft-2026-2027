# [claude-build] Modeles 3D animes du Donjon Suspendu pour Blockbench : la porte (dalle de pierre sculptee de racines, cadre de fer,
# sceau de pierre precieuse, qui MONTE dans le linteau) et le levier de racine (socle sculpte, cliquet, manche de racine, cristal d'ambre).
# Ecrit atelier/modeles/donjon/porte_donjon.bbmodel et levier_racine.bbmodel (format « Modded Entity », box UV, os et animations) et
# leurs textures (une par couleur pour la porte : bleue, jaune, rouge ; le sceau change, la pierre non). Ensuite :
#   python bbmodel_vers_java.py atelier/modeles/donjon/porte_donjon.bbmodel <java> --nom Porte (idem levier --nom Levier).
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
SORTIE = RACINE / "atelier/modeles/donjon"
TEXTURES = RACINE / "atelier/donjon/arene/src/main/resources/assets/grappin/textures/entity"
GRAINE = uuid.UUID("c3d4e5f6-0718-4a29-8b3c-4d5e00000000")

PIERRE = [(46, 46, 52), (58, 58, 64), (70, 70, 76), (84, 84, 90), (100, 100, 106)]
FER = [(48, 50, 56), (78, 80, 88), (112, 114, 122), (156, 158, 166)]
BOIS = [(56, 36, 20), (80, 52, 30), (106, 72, 40), (132, 94, 56)]
MOUSSE = [(50, 82, 28), (72, 110, 38), (94, 138, 50)]
GEMMES = {"bleue": [(20, 44, 130), (36, 90, 210), (90, 160, 255), (190, 230, 255)],
          "jaune": [(130, 76, 8), (210, 146, 26), (255, 200, 64), (255, 240, 170)],
          "rouge": [(100, 10, 20), (180, 26, 38), (236, 74, 70), (255, 176, 156)]}
AMBRE = GEMMES["jaune"]


# ------------------------------------------------------------------------------------------------ matieres (peinture par face)
def peintre(matiere, gemme=None):
    """Renvoie f(x, y, l, h, alea) -> couleur d'un texel (x, y) d'une face de l x h texels."""
    def pierre(x, y, l, h, a):
        if y % 16 == 15 or (x + (8 if (y // 16) % 2 else 0)) % 24 == 23:
            return PIERRE[0]                                                   # joints des blocs taillés
        if y > h - 4 and a.random() < 0.45:
            return MOUSSE[a.randint(0, 2)]                                     # mousse au pied
        return PIERRE[a.choice((1, 2, 2, 3))]

    def pierre_gravee(x, y, l, h, a):
        c = pierre(x, y, l, h, a)
        for k, (cx, f, ph) in enumerate(((l * 0.25, 9.0, 0.0), (l * 0.55, 13.0, 1.7), (l * 0.8, 7.0, 3.1))):
            gx = cx + 5 * math.sin(y / f + ph) + (y / h) * 6 * (1 if k % 2 else -1)   # racine sculptee qui serpente
            if abs(x - gx) < 1.5:
                return PIERRE[0]
            if 1.5 <= x - gx < 2.5:
                return PIERRE[4]                                               # arete eclairee de la gravure
        return c

    def fer(x, y, l, h, a):
        if (x % 8 == 3 and y % 8 == 3):
            return FER[3]                                                      # rivets
        return FER[a.choice((1, 2, 2))]

    def fer_sombre(x, y, l, h, a):
        return FER[a.choice((0, 0, 1))]

    def racine(x, y, l, h, a):
        return BOIS[0] if x % 3 == 0 else BOIS[a.choice((1, 1, 2, 3))]

    def mousse(x, y, l, h, a):
        return MOUSSE[a.randint(0, 2)]

    def facettes(pal):
        def f(x, y, l, h, a):
            u, v = (x + 0.5) / l - 0.5, (y + 0.5) / h - 0.5
            if abs(u) + abs(v) < 0.18:
                return pal[3]
            if u < 0 and v < 0:
                return pal[2]
            if u > 0 and v > 0:
                return pal[0]
            return pal[1]
        return f

    return {"pierre": pierre, "pierre_gravee": pierre_gravee, "fer": fer, "fer_sombre": fer_sombre, "racine": racine,
            "mousse": mousse, "gemme": facettes(gemme or AMBRE), "ambre": facettes(AMBRE)}[matiere]


# ------------------------------------------------------------------------------------------------ modele : os, cubes, box UV
class Modele:
    def __init__(self, nom, taille):
        self.nom, self.taille = nom, taille
        self.os = {}                                   # nom -> (parent, pivot)
        self.cubes = []                                # (os, nom, matiere, de, a, rotation)

    def os_(self, nom, parent, pivot):
        self.os[nom] = (parent, pivot)

    def cube(self, os, nom, matiere, de, a, rotation=None):
        self.cubes.append((os, nom, matiere, [float(v) for v in de], [float(v) for v in a], rotation))

    def ranger(self):
        """Place le patron (box UV) de chaque cube sur la texture, en rangees."""
        x = y = hauteur_rangee = 0
        places = []
        for _, _, _, de, a, _ in self.cubes:
            w, h, d = (math.ceil(a[i] - de[i]) for i in range(3))
            lw, lh = 2 * d + 2 * w, d + h
            if x + lw > self.taille:
                x, y, hauteur_rangee = 0, y + hauteur_rangee, 0
            assert y + lh <= self.taille, f"texture trop petite pour {self.nom}"
            places.append((x, y))
            x += lw
            hauteur_rangee = max(hauteur_rangee, lh)
        return places

    def texture(self, places, gemme=None):
        img = Image.new("RGBA", (self.taille, self.taille), (0, 0, 0, 0))
        p = img.load()
        for k, ((_, _, mat, de, a, _), (u, v)) in enumerate(zip(self.cubes, places)):
            w, h, d = (math.ceil(a[i] - de[i]) for i in range(3))
            f = peintre(mat, gemme)
            alea = random.Random(k * 7919)
            for (fu, fv, fl, fh) in ((u + d, v + d, w, h), (u + 2 * d + w, v + d, w, h), (u, v + d, d, h), (u + d + w, v + d, d, h),
                                     (u + d, v, w, d), (u + d + w, v, w, d)):
                for yy in range(fh):
                    for xx in range(fl):
                        p[fu + xx, fv + yy] = f(xx, yy, fl, fh, alea) + (255,)
        return img


def ident(*p):
    return str(uuid.uuid5(GRAINE, "/".join(str(x) for x in p)))


def faces_box(u, v, de, a):
    w, h, d = (a[i] - de[i] for i in range(3))
    return {"north": {"uv": [u + d, v + d, u + d + w, v + d + h], "texture": 0}, "east": {"uv": [u, v + d, u + d, v + d + h], "texture": 0},
            "south": {"uv": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h], "texture": 0},
            "west": {"uv": [u + d + w, v + d, u + 2 * d + w, v + d + h], "texture": 0},
            "up": {"uv": [u + d + w, v + d, u + d, v], "texture": 0}, "down": {"uv": [u + d + 2 * w, v, u + d + w, v + d], "texture": 0}}


def bbmodel(m, places, texture, animations):
    elements, enfants = [], {n: [] for n in m.os}
    for k, ((os, nom, _, de, a, rot), (u, v)) in enumerate(zip(m.cubes, places)):
        r, origine = [0, 0, 0], list(m.os[os][1])
        if rot:
            axe, angle = rot
            r["xyz".index(axe)] = angle
            origine = [(de[i] + a[i]) / 2 for i in range(3)]
        e = {"name": nom, "box_uv": True, "rescale": False, "locked": False, "from": de, "to": a, "autouv": 0, "color": k % 8,
             "origin": origine, "rotation": r, "uv_offset": [u, v], "faces": faces_box(u, v, de, a), "type": "cube", "uuid": ident(m.nom, k, nom)}
        elements.append(e)
        enfants[os].append(e["uuid"])

    def groupe(n):
        return {"name": n, "origin": list(m.os[n][1]), "color": 0, "uuid": ident(m.nom, "os", n), "export": True, "isOpen": True,
                "locked": False, "visibility": True, "autouv": 0,
                "children": enfants[n] + [groupe(f) for f, (p, _) in m.os.items() if p == n]}
    anims = []
    for nom, (duree, boucle, canaux_os) in animations.items():
        animateurs = {}
        for os, canaux in canaux_os.items():
            kfs = [{"channel": c, "data_points": [{"x": str(x), "y": str(y), "z": str(z)}], "uuid": ident(m.nom, nom, os, c, t), "time": t,
                    "color": -1, "interpolation": "catmullrom"} for c, images in canaux.items() for t, (x, y, z) in images]
            animateurs[ident(m.nom, "os", os)] = {"name": os, "type": "bone", "keyframes": kfs}
        anims.append({"uuid": ident(m.nom, "anim", nom), "name": nom, "loop": boucle, "override": False, "length": duree, "snapping": 20,
                      "selected": False, "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "", "animators": animateurs})
    tampon = io.BytesIO()
    texture.save(tampon, "PNG")
    return {"meta": {"format_version": "4.10", "model_format": "modded_entity", "box_uv": True}, "name": m.nom, "model_identifier": m.nom,
            "modded_entity_version": "Mojang", "modded_entity_flip_y": True, "visible_box": [8, 8, 0], "resolution": {"width": m.taille, "height": m.taille},
            "elements": elements, "outliner": [groupe("racine")],
            "textures": [{"path": "", "name": m.nom + ".png", "folder": "entity", "namespace": "grappin", "id": "0", "width": m.taille,
                          "height": m.taille, "uv_width": m.taille, "uv_height": m.taille, "particle": False, "render_mode": "default",
                          "visible": True, "internal": True, "saved": True, "uuid": ident(m.nom, "tex"),
                          "source": "data:image/png;base64," + base64.b64encode(tampon.getvalue()).decode()}],
            "animations": anims}


# ------------------------------------------------------------------------------------------------ la porte
def porte():
    m = Modele("porte_donjon", 512)
    m.os_("racine", None, (0, 0, 0))
    m.os_("dalle", "racine", (0, 0, 0))
    m.os_("sceau", "dalle", (0, 64, 0))
    m.cube("dalle", "panneau_g", "pierre_gravee", (-60, 4, -4), (-1, 120, 4))
    m.cube("dalle", "panneau_d", "pierre_gravee", (1, 4, -4), (60, 120, 4))
    m.cube("dalle", "joint", "fer_sombre", (-1, 4, -3), (1, 120, 3))
    m.cube("dalle", "cadre_haut", "fer", (-64, 120, -6), (64, 128, 6))
    m.cube("dalle", "cadre_bas", "fer", (-64, 0, -6), (64, 4, 6))
    m.cube("dalle", "montant_g", "fer", (-64, 4, -6), (-60, 120, 6))
    m.cube("dalle", "montant_d", "fer", (60, 4, -6), (64, 120, 6))
    for nom, y in (("traverse_basse", 22), ("traverse_haute", 96)):
        m.cube("dalle", nom, "fer_sombre", (-60, y, -5), (60, y + 6, 5))
        for xr in (-52, -36, -20, 20, 36, 52):                                      # rivets des deux cotes
            for z0 in (-6, 5):
                m.cube("dalle", f"rivet_{nom}", "fer", (xr - 1.5, y + 1.5, z0), (xr + 1.5, y + 4.5, z0 + 1))
    for k, (x0, angle) in enumerate(((-54, 22.5), (44, -22.5), (-30, -22.5), (24, 22.5))):   # racines qui grimpent sur la dalle
        for z0 in (-6, 4):
            m.cube("dalle", f"racine{k}", "racine", (x0, 8, z0), (x0 + 4, 52 + (k % 2) * 10, z0 + 2), ("z", angle))
    m.cube("dalle", "mousse_g", "mousse", (-58, 4, -5), (-30, 7, 5))
    m.cube("dalle", "mousse_d", "mousse", (20, 4, -5), (50, 6, 5))
    m.cube("sceau", "serti_haut", "fer", (-16, 78, -7), (16, 82, 7))
    m.cube("sceau", "serti_bas", "fer", (-16, 46, -7), (16, 50, 7))
    m.cube("sceau", "serti_g", "fer", (-16, 50, -7), (-12, 78, 7))
    m.cube("sceau", "serti_d", "fer", (12, 50, -7), (16, 78, 7))
    m.cube("sceau", "lueur_gemme", "gemme", (-9, 55, -8), (9, 73, 8), ("z", 45))
    Z = (0, 0, 0)
    anim = {"ouverture": (3.0, "once", {
        "sceau": {"position": [(0, Z), (0.2, (0.6, 0, 0)), (0.4, (-0.6, 0, 0)), (0.6, (0.5, 0, 0)), (0.8, Z), (3.0, Z)]},
        "dalle": {"position": [(0, Z), (0.9, Z), (1.05, (1, -1, 0)), (1.2, (-1, 0, 0)), (1.35, Z), (3.0, (0, 136, 0))]},
    })}
    return m, anim


# ------------------------------------------------------------------------------------------------ le levier
def levier():
    m = Modele("levier_racine", 128)
    m.os_("racine", None, (0, 0, 0))
    m.os_("socle", "racine", (0, 0, 0))
    m.os_("cliquet", "socle", (6, 14, 0))
    m.os_("manche", "socle", (0, 14, 0))
    m.os_("cristal", "manche", (0, 30, 0))
    m.cube("socle", "base", "pierre", (-7, 0, -7), (7, 4, 7))
    m.cube("socle", "pilier", "pierre_gravee", (-5, 4, -4), (5, 12, 4))
    m.cube("socle", "chapiteau", "pierre", (-6, 12, -5), (6, 14, 5))
    m.cube("socle", "mousse", "mousse", (-7, 4, 3), (-3, 5, 7))
    for k, (de, a) in enumerate((((-10, 0, -2), (-7, 2, 2)), ((7, 0, -3), (10, 2, 1)), ((-2, 0, -10), (2, 2, -7)), ((-1, 0, 7), (3, 2, 10)))):
        m.cube("socle", f"racine_sol{k}", "racine", de, a)                       # racines qui agrippent le sol
    m.cube("socle", "racine_pilier", "racine", (4, 2, -5), (6, 12, -3), ("z", -22.5))
    m.cube("cliquet", "roue", "fer_sombre", (6, 10, -4), (8, 18, 4))
    for k, (dy, dz) in enumerate(((0, -5), (0, 4), (-5, 0), (4, 0))):
        m.cube("cliquet", f"dent{k}", "fer", (6, 13.5 + dy, -0.5 + dz), (8, 14.5 + dy, 0.5 + dz))
    m.cube("manche", "axe", "fer", (-6, 13, -1), (6, 15, 1))
    m.cube("manche", "tige", "racine", (-1.5, 14, -1.5), (1.5, 29, 1.5))
    m.cube("manche", "noeud_bas", "racine", (-2, 18, -2), (2, 20, 2))
    m.cube("manche", "noeud_haut", "racine", (-2, 24, -2), (2, 25.5, 2))
    m.cube("manche", "radicelle", "racine", (1.5, 21, -0.5), (3.5, 22, 0.5))
    m.cube("cristal", "griffe_g", "racine", (-3, 28, -0.5), (-2, 31, 0.5))
    m.cube("cristal", "griffe_d", "racine", (2, 28, -0.5), (3, 31, 0.5))
    m.cube("cristal", "lueur_cristal", "ambre", (-2.5, 29, -2.5), (2.5, 35, 2.5))
    Z = (0, 0, 0)
    anim = {
        "tirer": (0.6, "hold", {"manche": {"rotation": [(0, (-35, 0, 0)), (0.45, (40, 0, 0)), (0.6, (35, 0, 0))]},
                                "cliquet": {"rotation": [(0, Z), (0.6, (90, 0, 0))]}}),
        "relacher": (0.6, "hold", {"manche": {"rotation": [(0, (35, 0, 0)), (0.45, (-40, 0, 0)), (0.6, (-35, 0, 0))]},
                                   "cliquet": {"rotation": [(0, (90, 0, 0)), (0.6, Z)]}}),
    }
    return m, anim


def main():
    SORTIE.mkdir(parents=True, exist_ok=True)
    TEXTURES.mkdir(parents=True, exist_ok=True)
    m, anim = porte()
    places = m.ranger()
    for couleur, pal in GEMMES.items():
        m.texture(places, pal).save(TEXTURES / f"porte_donjon_{couleur}.png")
    (SORTIE / "porte_donjon.bbmodel").write_text(json.dumps(bbmodel(m, places, m.texture(places, GEMMES["bleue"]), anim), indent=1) + "\n",
                                                 encoding="utf-8")
    m, anim = levier()
    places = m.ranger()
    tex = m.texture(places)
    tex.save(TEXTURES / "levier_racine.png")
    (SORTIE / "levier_racine.bbmodel").write_text(json.dumps(bbmodel(m, places, tex, anim), indent=1) + "\n", encoding="utf-8")
    for ancien in ("levier_racine_baisse.bbmodel",):
        (SORTIE / ancien).unlink(missing_ok=True)
    print("porte_donjon et levier_racine ecrits")


if __name__ == "__main__":
    main()
