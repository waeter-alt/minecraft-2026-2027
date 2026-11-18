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
    """Renvoie f(x, y, l, h, alea, g) -> couleur d'un texel (x, y) d'une face de l x h texels ; g = (gx, gy) position du texel dans
    le modele pour les faces avant et arriere (pour peindre un motif d'un seul tenant a travers plusieurs cubes), None sinon."""
    def pierre(x, y, l, h, a, g=None):
        if y % 16 == 15 or (x + (8 if (y // 16) % 2 else 0)) % 24 == 23:
            return PIERRE[0]                                                   # joints des blocs taillés
        if y > h - 4 and a.random() < 0.45:
            return MOUSSE[a.randint(0, 2)]                                     # mousse au pied
        return PIERRE[a.choice((1, 2, 2, 3))]

    def pierre_gravee(x, y, l, h, a, g=None):
        c = pierre(x, y, l, h, a)
        for k, (cx, f, ph) in enumerate(((l * 0.25, 9.0, 0.0), (l * 0.55, 13.0, 1.7), (l * 0.8, 7.0, 3.1))):
            gx = cx + 5 * math.sin(y / f + ph) + (y / h) * 6 * (1 if k % 2 else -1)   # racine sculptee qui serpente
            if abs(x - gx) < 1.5:
                return PIERRE[0]
            if 1.5 <= x - gx < 2.5:
                return PIERRE[4]                                               # arete eclairee de la gravure
        return c

    def fer(x, y, l, h, a, g=None):
        if (x % 8 == 3 and y % 8 == 3):
            return FER[3]                                                      # rivets
        return FER[a.choice((1, 2, 2))]

    def fer_sombre(x, y, l, h, a, g=None):
        return FER[a.choice((0, 0, 1))]

    def racine(x, y, l, h, a, g=None):
        return BOIS[0] if x % 3 == 0 else BOIS[a.choice((1, 1, 2, 3))]

    def mousse(x, y, l, h, a, g=None):
        return MOUSSE[a.randint(0, 2)]

    def facettes(pal):
        def f(x, y, l, h, a, g=None):
            u, v = (x + 0.5) / l - 0.5, (y + 0.5) / h - 0.5
            if abs(u) + abs(v) < 0.18:
                return pal[3]
            if u < 0 and v < 0:
                return pal[2]
            if u > 0 and v > 0:
                return pal[0]
            return pal[1]
        return f

    pal = gemme or AMBRE

    def pierre_porte(x, y, l, h, a, g=None):
        """Pierre de la porte : grands blocs tailles, sombres et calmes ; sur les faces avant et arriere, un seul grand motif sculpte :
        un anneau autour du sceau et des racines qui en rayonnent, avec une veine de la couleur du sceau dans les plus proches."""
        base = [(38, 38, 44), (46, 46, 52), (54, 54, 60), (64, 64, 70)]
        if g is None:
            return base[a.choice((0, 1, 1, 2))]
        gx, gy = g
        if gy < 9 and a.random() < 0.35:
            return MOUSSE[a.randint(0, 2)]
        r = math.hypot(gx, gy - 96)
        if abs(r - 34) < 1.3:
            return pal[1] if r > 34 else (20, 20, 26)                                  # anneau grave autour du sceau, veine de couleur au fond
        if 34 - 2.6 < r <= 34 - 1.3:
            return (86, 86, 94)
        d, s, n = distance_racines(gx, gy)
        if d < 0.9 and n < 4 and s < 42:
            return pal[2] if s < 18 else pal[1]                                        # veine de couleur qui descend du sceau, sur un tiers de la racine
        if d < 1.4:
            return (20, 20, 26)                                                        # creux de la gravure
        if d < 2.3:
            return (86, 86, 94)                                                        # arete eclairee
        rang = int(gy // 16)
        if gy % 16 < 1 or (gx + 64 + (16 if rang % 2 else 0)) % 32 < 1:
            return (30, 30, 36)                                                         # joints des grands blocs
        return base[a.choice((0, 1, 1, 1, 2))]

    def fer_porte(x, y, l, h, a, g=None):
        if x % 10 in (4, 5) and y % 10 in (4, 5):
            return FER[3] if (x % 10, y % 10) == (4, 4) else FER[2]                     # gros rivets
        return FER[a.choice((0, 0, 1))]

    return {"pierre": pierre, "pierre_gravee": pierre_gravee, "fer": fer, "fer_sombre": fer_sombre, "racine": racine,
            "mousse": mousse, "gemme": facettes(pal), "ambre": facettes(AMBRE), "pierre_porte": pierre_porte, "fer_porte": fer_porte}[matiere]


# Racines sculptees qui partent de l'anneau du sceau (centre en (0, 96) dans la porte), en coordonnees du modele (x, y)
RACINES_SCULPTEES = [
    [(-3, 62), (-3, 44), (-9, 24), (-16, 4)],
    [(3, 62), (6, 46), (15, 28), (26, 4)],
    [(-32, 86), (-42, 76), (-49, 56), (-54, 26), (-56, 6)],
    [(32, 86), (42, 76), (49, 56), (54, 26), (56, 6)],
    [(-46, 64), (-36, 48), (-30, 24)],
    [(46, 64), (36, 48), (30, 24)],
    [(-22, 118), (-32, 112), (-40, 100)],
    [(22, 118), (32, 112), (40, 100)],
]


def distance_racines(x, y):
    """(distance a la racine sculptee la plus proche, longueur parcourue depuis son depart, numero de la racine)."""
    meilleur = (1e9, 1e9, -1)
    for numero, ligne in enumerate(RACINES_SCULPTEES):
        parcouru = 0.0
        for (ax, ay), (bx, by) in zip(ligne, ligne[1:]):
            lg = math.hypot(bx - ax, by - ay)
            t = max(0.0, min(1.0, ((x - ax) * (bx - ax) + (y - ay) * (by - ay)) / (lg * lg)))
            d = math.hypot(x - (ax + t * (bx - ax)), y - (ay + t * (by - ay)))
            if d < meilleur[0]:
                meilleur = (d, parcouru + t * lg, numero)
            parcouru += lg
    return meilleur


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
        """Place le patron (box UV) de chaque cube sur la texture, en rangees, les plus hauts d'abord (moins de place perdue)."""
        tailles = []
        for _, _, _, de, a, _ in self.cubes:
            w, h, d = (math.ceil(a[i] - de[i]) for i in range(3))
            tailles.append((2 * d + 2 * w, d + h))
        places = [None] * len(self.cubes)
        x = y = hauteur_rangee = 0
        for k in sorted(range(len(tailles)), key=lambda k: -tailles[k][1]):
            lw, lh = tailles[k]
            if x + lw > self.taille:
                x, y, hauteur_rangee = 0, y + hauteur_rangee, 0
            assert y + lh <= self.taille, f"texture trop petite pour {self.nom}"
            places[k] = (x, y)
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
            faces = ((u + d, v + d, w, h, "avant"), (u + 2 * d + w, v + d, w, h, "arriere"), (u, v + d, d, h, None), (u + d + w, v + d, d, h, None),
                     (u + d, v, w, d, None), (u + d + w, v, w, d, None))
            for (fu, fv, fl, fh, cote) in faces:
                for yy in range(fh):
                    for xx in range(fl):
                        g = None
                        if cote:
                            gx = de[0] + xx + 0.5 if cote == "arriere" else a[0] - xx - 0.5
                            g = (gx, a[1] - yy - 0.5)
                        p[fu + xx, fv + yy] = f(xx, yy, fl, fh, alea, g) + (255,)
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
    m.os_("sceau", "dalle", (0, 96, 0))
    # En arche, comme les arches du donjon (suspendu_salle1.profil) : 5 blocs de haut sur les cotes, 8 au milieu, arrondi entre les deux.
    # La dalle est faite de bandes verticales de 8 unites (un demi-bloc), chacune a la hauteur de l'arche, coiffees d'une bande de fer.
    def haut_arche(x):
        u = x / 16
        return round(16 * min(8, 5 + 3 * math.sqrt(max(0.0, 1 - (u / 4) ** 2))))
    for i in range(16):
        x0 = -64 + 8 * i
        h = haut_arche(x0 + 4)
        if i in (0, 15):
            m.cube("dalle", f"montant{i}", "fer_porte", (x0, 4, -6), (x0 + 8, h, 6))            # montants de fer aux deux bords
        else:
            m.cube("dalle", f"panneau{i}", "pierre_porte", (x0, 4, -4), (x0 + 8, h - 6, 4))
            m.cube("dalle", f"arc{i}", "fer_porte", (x0, h - 6, -6), (x0 + 8, h, 6))            # le cintre de fer suit l'arche
    m.cube("dalle", "joint", "fer_sombre", (-1, 4, -5), (1, haut_arche(0) - 6, 5))
    m.cube("dalle", "cadre_bas", "fer_porte", (-64, 0, -6), (64, 4, 6))
    for nom, y in (("traverse_basse", 22), ("traverse_haute", 60)):
        m.cube("dalle", nom, "fer_porte", (-56, y, -5), (56, y + 6, 5))
        for xr in (-48, -32, -16, 16, 32, 48):                                      # rivets des deux cotes
            for z0 in (-6, 5):
                m.cube("dalle", f"rivet_{nom}", "fer", (xr - 1.5, y + 1.5, z0), (xr + 1.5, y + 4.5, z0 + 1))
    # vraies racines en relief : elles sortent des coins bas de l'arche et montent en s'enroulant vers le sceau, des deux cotes
    for s in (-1, 1):
        points = [(s * 56, 6), (s * 52, 30), (s * 44, 52), (s * 32, 70), (s * 22, 80)]
        n = 0
        for (ax, ay), (bx, by) in zip(points, points[1:]):
            pas = max(1, round(math.hypot(bx - ax, by - ay) / 4))
            for k in range(pas):
                x = ax + (bx - ax) * k / pas
                y = ay + (by - ay) * k / pas
                r = 2.5 - 1.0 * n / 22                                                  # la racine s'affine en montant
                for z0, z1 in ((-6.5, -4), (4, 6.5)):
                    m.cube("dalle", f"racine{s}_{n}", "racine", (round(x - r), round(y - r), z0), (round(x + r), round(y + r), z1))
                n += 1
    m.cube("dalle", "mousse_g", "mousse", (-56, 4, -5), (-30, 7, 5))
    m.cube("dalle", "mousse_d", "mousse", (20, 4, -5), (50, 6, 5))
    m.cube("sceau", "serti_haut", "fer_porte", (-24, 116, -7), (24, 120, 7))           # sceau 1,5 fois plus gros
    m.cube("sceau", "serti_bas", "fer_porte", (-24, 72, -7), (24, 76, 7))
    m.cube("sceau", "serti_g", "fer_porte", (-24, 76, -7), (-20, 116, 7))
    m.cube("sceau", "serti_d", "fer_porte", (20, 76, -7), (24, 116, 7))
    m.cube("sceau", "lueur_gemme", "gemme", (-13.5, 82.5, -8), (13.5, 109.5, 8), ("z", 45))
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
