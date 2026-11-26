# [claude-build] Nouvelle apparence de la paravoile (mod donjon 4.4.0), comme le grappin (grappin_apparence.py) et les leviers :
#   - la voile deployee au-dessus du planeur : une aile de parapente de 9 caissons en arc (blanc et azur, bord d'attaque ouvert),
#     stabilos dores aux bouts, suspentes jusqu'aux elevateurs de cuir aux epaules ; animations « ouverture », « fermeture » et « vol »
#     (balancement, aile qui respire, stabilos qui battent) ; ParavoileClient y ajoute l'inclinaison en virage ;
#     modele « Modded Entity » atelier/modeles/paravoile/paravoile_voile.bbmodel + textures/entity/paravoile.png, puis
#       python atelier/scripts/bbmodel_vers_java.py atelier/modeles/paravoile/paravoile_voile.bbmodel atelier/donjon/arene/src/main/java/fr/atelier/grappin --nom Paravoile
#   - l'objet en main : la voile pliee en paquetage (rouleau raye, sangles de cuir, boucles et poignee de laiton) :
#     models/item/paravoile_main.json, texture textures/item/paravoile_modele.png, atelier/modeles/paravoile/paravoile_objet.bbmodel ;
#   - l'icone de l'inventaire (textures/item/paravoile.png : la voile ouverte et son planeur) ; models/item/paravoile.json choisit
#     l'icone dans l'inventaire et le paquetage 3D ailleurs (neoforge:separate_transforms).
# Une fois retouches dans Blockbench, les .bbmodel sont la source : ne plus relancer ce script pour eux.
import json
import math
import random
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import grappin_apparence as ga  # noqa: E402  (meme format de modele d'objet que le grappin)
from donjon_3d import Modele, bbmodel  # noqa: E402  (meme format de modele anime que la porte et le levier)

RACINE = Path(__file__).resolve().parents[2]
ASSETS = RACINE / "atelier/donjon/arene/src/main/resources/assets/donjon"
BBMODELES = RACINE / "atelier/modeles/paravoile"

BLANC = [(214, 222, 230), (232, 238, 244), (246, 248, 252)]
AZUR = [(36, 96, 170), (52, 124, 204), (86, 156, 228)]
OR = [(170, 126, 40), (212, 168, 66), (240, 206, 112)]
CUIR = [(88, 54, 30), (112, 72, 40), (136, 92, 54)]
SUSPENTE = [(180, 184, 190), (204, 208, 214), (228, 232, 236)]
SOMBRE = (30, 38, 52)


# ------------------------------------------------------------------------------------------------ la voile deployee (modele anime)
def peintre(matiere):
    """f(face, x, y, l, h, alea) ; face : 0 nord (l'avant, bord d'attaque), 1 sud, 2 est, 3 ouest, 4 dessus, 5 dessous."""
    def toile(pal):
        def f(face, x, y, l, h, a):
            if face == 0 and 0 < y < h - 1 and 0 < x < l - 1:
                return SOMBRE                                                     # la bouche du caisson : l'air entre par l'avant
            if face in (4, 5) and y % 4 == 3:
                return pal[0]                                                     # les coutures entre les cloisons
            return pal[a.choice((1, 1, 2))]
        return f

    def plein(pal):
        return lambda face, x, y, l, h, a: pal[a.choice((0, 1, 1, 2))]

    return {"toile_blanche": toile(BLANC), "toile_azur": toile(AZUR), "or": plein(OR), "cuir": plein(CUIR), "suspente": plein(SUSPENTE)}[matiere]


class ModeleVoile(Modele):
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


R_ARC, Y_CENTRE, PAS = 26.0, 26.0, 12.0          # l'arc de la voile : rayon, hauteur de son centre (les epaules du joueur, pieds en 0), pas angulaire
EPAULES = 25.0


def voile():
    """Os : voile (pivot aux epaules : balancement, ouverture, virage) > aile_g / aile_d (pivot au bord du caisson central : les ailes
    se replient vers le bas) > stabilo_g / stabilo_d (pivot a leur attache : ils battent au vent). Animations : ouverture, fermeture, vol."""
    m = ModeleVoile("paravoile", 256)
    m.os_("racine", None, (0, 0, 0))
    m.os_("voile", "racine", (0, EPAULES, 0))
    haut = Y_CENTRE + R_ARC
    m.os_("aile_g", "voile", (-2.9, haut, 0))
    m.os_("aile_d", "voile", (2.9, haut, 0))
    for i in range(-4, 5):
        th = math.radians(i * PAS)
        cx, cy = R_ARC * math.sin(th), Y_CENTRE + R_ARC * math.cos(th)
        larg, ep = 5.8, 3.0
        os = "voile" if i == 0 else ("aile_g" if i < 0 else "aile_d")
        m.cube(os, f"caisson{i + 4}", "toile_azur" if i % 2 else "toile_blanche",
               (cx - larg / 2, cy - ep / 2, -8), (cx + larg / 2, cy + ep / 2, 8), ("z", -i * PAS) if i else None)
    for s_ in (-1, 1):                                                               # stabilos dores aux deux bouts, qui retombent
        cote = "g" if s_ < 0 else "d"
        th_attache = math.radians(s_ * (4 * PAS + 4))
        ax, ay = (R_ARC - 0.5) * math.sin(th_attache), Y_CENTRE + (R_ARC - 0.5) * math.cos(th_attache)
        m.os_(f"stabilo_{cote}", f"aile_{cote}", (ax, ay, 0))
        th = math.radians(s_ * (4 * PAS + 9))
        cx, cy = (R_ARC - 1) * math.sin(th), Y_CENTRE + (R_ARC - 1) * math.cos(th)
        m.cube(f"stabilo_{cote}", f"stabilo_{cote}", "or", (cx - 1, cy - 4, -6), (cx + 1, cy + 4, 6), ("z", -s_ * 4 * PAS - s_ * 9))
    for cote in (-1, 1):                                                             # elevateurs de cuir aux epaules
        m.cube("voile", f"elevateur_{'g' if cote < 0 else 'd'}", "cuir", (cote * 4.5 - 0.75, EPAULES, -0.75), (cote * 4.5 + 0.75, EPAULES + 4, 0.75))
    for i in (-4, -2, -1, 1, 2, 4):                                                 # suspentes : du dessous des caissons a l'elevateur de leur cote
        th = math.radians(i * PAS)
        bx, by = (R_ARC - 1.5) * math.sin(th), Y_CENTRE + (R_ARC - 1.5) * math.cos(th)
        ex, ey = (4.5 if i > 0 else -4.5), EPAULES + 4
        for z in (-5, 4):
            dx, dy = bx - ex, by - ey
            long = math.hypot(dx, dy)
            mx, my = (bx + ex) / 2, (by + ey) / 2
            angle = -math.degrees(math.atan2(dx, dy))
            m.cube("aile_g" if i < 0 else "aile_d", f"suspente{i + 4}_{'av' if z < 0 else 'ar'}", "suspente",
                   (mx - 0.25, my - long / 2, z), (mx + 0.25, my + long / 2, z + 1), ("z", angle) if abs(angle) > 0.01 else None)
    Z = (0, 0, 0)
    PLIE = 80                                                                        # ailes repliees vers le bas
    anim = {
        # la voile surgit de derriere le dos, se redresse et deplie ses ailes, avec un petit rebond
        "ouverture": (0.7, "hold", {"voile": {"position": [(0, (0, -16, 6)), (0.4, (0, 1.5, 0)), (0.55, (0, -0.5, 0)), (0.7, Z)],
                                              "rotation": [(0, (-70, 0, 0)), (0.4, (6, 0, 0)), (0.55, (-2, 0, 0)), (0.7, Z)]},
                                    "aile_g": {"rotation": [(0, (0, 0, PLIE)), (0.15, (0, 0, PLIE)), (0.5, (0, 0, -6)), (0.7, Z)]},
                                    "aile_d": {"rotation": [(0, (0, 0, -PLIE)), (0.15, (0, 0, -PLIE)), (0.5, (0, 0, 6)), (0.7, Z)]}}),
        # a l'atterrissage : les ailes se replient et la voile retombe derriere le dos
        "fermeture": (0.5, "hold", {"voile": {"position": [(0, Z), (0.2, Z), (0.5, (0, -16, 6))],
                                              "rotation": [(0, Z), (0.2, (4, 0, 0)), (0.5, (-70, 0, 0))]},
                                    "aile_g": {"rotation": [(0, Z), (0.3, (0, 0, PLIE))]},
                                    "aile_d": {"rotation": [(0, Z), (0.3, (0, 0, -PLIE))]}}),
        # en vol : balancement sous la voile, l'aile qui respire, les stabilos qui battent
        "vol": (2.4, "loop", {"voile": {"rotation": [(0, Z), (0.6, (2, 0, 3)), (1.2, Z), (1.8, (-2, 0, -3)), (2.4, Z)],
                                        "position": [(0, Z), (1.2, (0, 0.6, 0)), (2.4, Z)]},
                              "aile_g": {"rotation": [(0, Z), (1.2, (0, 0, 2.5)), (2.4, Z)]},
                              "aile_d": {"rotation": [(0, Z), (1.2, (0, 0, -2.5)), (2.4, Z)]},
                              "stabilo_g": {"rotation": [(0, Z), (0.3, (0, 0, 8)), (0.6, Z), (0.9, (0, 0, -5)), (1.2, Z), (1.5, (0, 0, 8)), (1.8, Z),
                                                         (2.1, (0, 0, -5)), (2.4, Z)]},
                              "stabilo_d": {"rotation": [(0, Z), (0.3, (0, 0, -6)), (0.6, Z), (0.9, (0, 0, 7)), (1.2, Z), (1.5, (0, 0, -6)), (1.8, Z),
                                                         (2.1, (0, 0, 7)), (2.4, Z)]}}),
    }
    return m, anim


# ------------------------------------------------------------------------------------------------ l'objet en main : le paquetage
ZONES = {"blanc": (0, 0, 8, 16), "azur": (8, 0, 8, 16), "cuir": (16, 0, 8, 16), "laiton": (24, 0, 8, 8), "corde": (24, 8, 8, 8)}


def texture_objet():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    p = img.load()
    a = random.Random(11)
    for nom, pal in (("blanc", BLANC), ("azur", AZUR), ("cuir", CUIR), ("laiton", OR), ("corde", SUSPENTE)):
        x0, y0, l, h = ZONES[nom]
        for y in range(h):
            for x in range(l):
                c = pal[a.choice((1, 1, 2, 0))]
                if nom in ("blanc", "azur") and y % 4 == 3:
                    c = pal[0]                                                       # les plis de la toile roulee
                p[x0 + x, y0 + y] = c + (255,)
    return img


def cubes_paquetage():
    """Le rouleau de toile (raye blanc et azur, couche le long de x), deux sangles de cuir a boucles de laiton, la poignee, une suspente."""
    c = []
    for k, (x0, x1) in enumerate(((2, 5), (5, 8), (8, 11), (11, 14))):
        m = "blanc" if k % 2 == 0 else "azur"
        c.append((f"rouleau{k}", m, [x0, 3, 5], [x1, 9, 11]))
        c.append((f"rouleau{k}_haut", m, [x0, 9, 6], [x1, 10, 10]))
        c.append((f"rouleau{k}_bas", m, [x0, 2, 6], [x1, 3, 10]))
    for k, x in enumerate((4, 11)):
        c.append((f"sangle{k}", "cuir", [x, 1.5, 4.5], [x + 1.5, 10.5, 11.5]))
        c.append((f"boucle{k}", "laiton", [x, 5, 3.9], [x + 1.5, 7, 4.5]))
    c.append(("poignee", "cuir", [6, 10.5, 7.5], [10, 11.5, 8.5]))
    c.append(("poignee_g", "cuir", [6, 9.5, 7.5], [7, 10.5, 8.5]))
    c.append(("poignee_d", "cuir", [9, 9.5, 7.5], [10, 10.5, 8.5]))
    c.append(("anneau", "laiton", [7.5, 11.5, 7.5], [8.5, 12.5, 8.5]))
    c.append(("suspente", "corde", [13.5, 1, 7.5], [14, 3, 8]))
    return c


AFFICHAGE_PAQUETAGE = {
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.45, 0.45, 0.45]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.45, 0.45, 0.45]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.35, 0.35, 0.35]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]},
}


def modele_objet(cubes):
    """Comme ga.modele_json, mais avec les zones et la texture de la paravoile."""
    zones_grappin = ga.ZONES
    ga.ZONES = ZONES
    try:
        m = ga.modele_json(cubes, None, AFFICHAGE_PAQUETAGE)
        bb = ga.bbmodel("paravoile_objet", cubes, None, AFFICHAGE_PAQUETAGE, texture_objet())
    finally:
        ga.ZONES = zones_grappin
    m["textures"] = {"0": "donjon:item/paravoile_modele", "particle": "donjon:item/paravoile_modele"}
    bb["textures"][0]["name"] = "paravoile_modele.png"
    return m, bb


# ------------------------------------------------------------------------------------------------ l'icone
def icone():
    """16 x 16 : la voile ouverte en arc (caissons blanc et azur), les suspentes, le planeur sous elle."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = img.load()
    for x in range(1, 15):
        d = (x - 7.5) / 7.5
        y = int(round(5 - 3.2 * (1 - d * d)))                                       # l'arc de la voile
        pal = AZUR if (x // 2) % 2 else BLANC
        p[x, y] = pal[2] + (255,)
        p[x, y + 1] = pal[1] + (255,)
        if x in (1, 14):
            p[x, y + 2] = OR[1] + (255,)
    for x0 in (2, 5, 10, 13):                                                         # suspentes vers le planeur
        for k in range(1, 7):
            x = int(round(x0 + (7.5 - x0) * k / 7))
            y = 5 + k
            if p[x, y][3] == 0:
                p[x, y] = SUSPENTE[0] + (255,)
    for (x, y), c in (((7, 12), CUIR[2]), ((8, 12), CUIR[2]), ((7, 13), CUIR[1]), ((8, 13), CUIR[1]), ((7, 14), CUIR[0]), ((8, 14), CUIR[0])):
        p[x, y] = c + (255,)
    return img


def ecrire(chemin, donnees):
    chemin.parent.mkdir(parents=True, exist_ok=True)
    chemin.write_text(json.dumps(donnees, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main():
    BBMODELES.mkdir(parents=True, exist_ok=True)
    m, anim = voile()
    places = m.ranger()
    tex = m.texture(places)
    tex.save(ASSETS / "textures/entity/paravoile.png")
    ecrire(BBMODELES / "paravoile_voile.bbmodel", bbmodel(m, places, tex, anim))
    texture_objet().save(ASSETS / "textures/item/paravoile_modele.png")
    icone().save(ASSETS / "textures/item/paravoile.png")
    objet, bb = modele_objet(cubes_paquetage())
    ecrire(ASSETS / "models/item/paravoile_main.json", objet)
    ecrire(BBMODELES / "paravoile_objet.bbmodel", bb)
    ecrire(ASSETS / "models/item/paravoile_icone.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "donjon:item/paravoile"}})
    ecrire(ASSETS / "models/item/paravoile.json", {"loader": "neoforge:separate_transforms", "base": {"parent": "donjon:item/paravoile_main"},
                                                   "perspectives": {"gui": {"parent": "donjon:item/paravoile_icone"}}})
    print("paravoile : voile deployee, paquetage en main, icone et fichiers Blockbench ecrits")


if __name__ == "__main__":
    main()
