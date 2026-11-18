# [claude-build] Nouvelle apparence du grappin (mod grappin 3.1.0) : dessine les textures et ecrit les modeles.
#   - textures/item/grappin.png        : icone 16x16 (inventaire)
#   - textures/item/grappin_modele.png : texture 32x32 des deux modeles 3D
#   - models/item/grappin.json         : choisit l'icone dans l'inventaire et le modele 3D ailleurs (neoforge:separate_transforms)
#   - models/item/grappin_main.json    : le grappin en 3D, tenu en main
#   - models/item/grappin_icone.json   : l'icone plate
#   - models/item/grappin_tire.json, grappin_main_vide.json : le lanceur sans sa tete, quand le crochet est lance
#   - models/item/crochet_tige.json, crochet_griffe.json : le crochet lance (tige, et une griffe dessinee 4 fois et animee)
#   - atelier/modeles/grappin/*.bbmodel : les memes modeles, a ouvrir dans Blockbench
# Les cubes sont decrits une seule fois ici. Si Luca retouche un modele dans Blockbench, son export devient la source :
# ne plus relancer ce script pour ce modele (option --sans-modeles pour ne refaire que les textures).
import base64
import io
import json
import random
import sys
import uuid
from pathlib import Path

from PIL import Image

RACINE = Path(__file__).resolve().parents[2]
ASSETS = RACINE / "atelier/donjon/arene/src/main/resources/assets/donjon"
BBMODELES = RACINE / "atelier/modeles/grappin"

# Couleurs (celles de l'ancienne icone, plus des tons intermediaires)
BOIS, BOIS_F, BOIS_C = (125, 86, 50, 255), (90, 60, 35, 255), (150, 108, 66, 255)
FER, FER_C, FER_F = (165, 168, 175, 255), (215, 218, 222, 255), (95, 98, 105, 255)
FER_M = (135, 138, 146, 255)
NOIR = (55, 57, 62, 255)
CORDE, CORDE_F = (200, 185, 150, 255), (150, 130, 95, 255)

# Zones de la texture 32x32 (en pixels) : chaque matiere a son carre, les faces y piochent a partir du coin.
ZONES = {
    "bois": (0, 0, 8, 16),
    "fer": (8, 0, 8, 16),
    "sombre": (16, 0, 8, 8),
    "corde": (16, 8, 8, 8),
    "pointe": (24, 0, 8, 8),
}


def texture_modele():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    p = img.load()
    hasard = random.Random(7)

    def remplir(zone, choix):
        x0, y0, l, h = ZONES[zone]
        for y in range(h):
            for x in range(l):
                p[x0 + x, y0 + y] = choix(x, y)

    def bois(x, y):                                   # fil du bois vertical, quelques noeuds clairs
        if x % 3 == 2:
            return BOIS_F
        return BOIS_C if hasard.random() < 0.12 else BOIS

    def fer(x, y):
        r = hasard.random()
        return FER_C if r < 0.15 else FER_M if r < 0.35 else FER

    def sombre(x, y):
        return NOIR if hasard.random() < 0.25 else FER_F

    def corde(x, y):                                  # torsade : bandes en diagonale
        return CORDE_F if (x + y) % 3 == 0 else CORDE

    def pointe(x, y):
        return FER if hasard.random() < 0.3 else FER_C

    remplir("bois", bois)
    remplir("fer", fer)
    remplir("sombre", sombre)
    remplir("corde", corde)
    remplir("pointe", pointe)
    return img


# Icone 16x16, dessinee pixel par pixel : manche en bas a gauche, crochet en haut a droite.
ICONE = [
    "...............H",
    ".........HI...H.",
    "........I..I.I..",
    "........H..DD...",
    "...........IKI..",
    ".....D....HD..I.",
    "....DRDHHIK...D.",
    "....DrDHIMK.HI..",
    "....DRDIMDK.....",
    "...R..Ww.K......",
    "...r.HD.........",
    "..R.Ww..........",
    "...HD...........",
    "..Lw............",
    ".DK.............",
    "................",
]
PALETTE_ICONE = {"W": BOIS, "w": BOIS_F, "L": BOIS_C, "I": FER, "H": FER_C, "M": FER_M, "D": FER_F, "K": NOIR, "R": CORDE, "r": CORDE_F}


def icone():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = img.load()
    for y, ligne in enumerate(ICONE):
        assert len(ligne) == 16, f"ligne {y} de l'icone : {len(ligne)} pixels"
        for x, c in enumerate(ligne):
            if c != ".":
                p[x, y] = PALETTE_ICONE[c]
    return img


# --- Cubes -----------------------------------------------------------------------------------------------------
# Un cube = (nom, matiere, de, a), en coordonnees de modele (0-16).
GRIFFE_OUVERTE = (("bras", "fer", (13, 14), (1, 3)), ("coude", "fer", (11, 14), (3, 4)), ("pointe", "pointe", (10, 11), (3, 4)))
GRIFFE_REPLIEE = (("bras", "fer", (10, 14), (1, 2)), ("pointe", "pointe", (9, 10), (1, 2)))       # couchee le long de la tige


def griffes(vers_xyz, decalage, directions=("est", "ouest", "sud", "nord"), parties=GRIFFE_OUVERTE):
    """Griffes autour de l'axe : ouvertes (un bras qui sort, puis un crochet qui revient vers le manche) ou repliees le long de la tige.
    En coordonnees (s le long de l'axe, a et b ecarts a l'axe) ; vers_xyz convertit en x, y, z."""
    cubes = []
    for nom, (da, db) in {"est": (1, 0), "ouest": (-1, 0), "sud": (0, 1), "nord": (0, -1)}.items():
        if nom not in directions:
            continue
        for partie, matiere, s, r in parties:
            s0, s1 = s[0] + decalage, s[1] + decalage
            if da:
                a = (r[0], r[1]) if da > 0 else (-r[1], -r[0])
                b = (-0.5, 0.5)
            else:
                b = (r[0], r[1]) if db > 0 else (-r[1], -r[0])
                a = (-0.5, 0.5)
            p0, p1 = vers_xyz(s0, a[0], b[0]), vers_xyz(s1, a[1], b[1])
            cubes.append((f"griffe_{nom}_{partie}", matiere, [min(u, v) for u, v in zip(p0, p1)], [max(u, v) for u, v in zip(p0, p1)]))
    return cubes


def cubes_lanceur():
    """Le lanceur sans sa tete (poignee, corps, bobine) : ce qui reste en main quand le crochet est parti."""
    return [c for c in cubes_main_complet() if c[0] not in ("tige", "collier", "pointe")]


def cubes_main_vide():
    return cubes_lanceur() + [("embout", "sombre", [7, 11, 7], [9, 12, 9])]           # embout vide d'ou sort la corde


def cubes_main():
    """Le grappin tenu en main, crochet en place, griffes repliees le long de la tige."""
    return cubes_main_complet() + griffes(lambda s, a, b: (8 + a, s, 8 + b), 0, parties=GRIFFE_REPLIEE)


def cubes_main_complet():
    """Le grappin tenu en main, dresse le long de Y (le manche en bas) ; tout le modele est ensuite penche a 45 degres
    comme une icone d'outil, pour garder les reglages d'affichage en main de Minecraft."""
    return [
        ("pommeau", "sombre", [7, 0, 7], [9, 1, 9]),
        ("poignee", "bois", [7, 1, 7], [9, 7, 9]),
        ("bague_basse", "fer", [6.5, 2, 6.5], [9.5, 3, 9.5]),
        ("bague_haute", "fer", [6.5, 5, 6.5], [9.5, 6, 9.5]),
        ("corps", "fer", [6, 7, 6], [10, 10, 10]),
        ("plaque", "sombre", [6.5, 10, 6.5], [9.5, 11, 9.5]),
        ("bobine_corde", "corde", [4, 7.5, 7], [6, 9.5, 9]),
        ("bobine_flasque", "sombre", [3, 7, 6.5], [4, 10, 9.5]),
        ("manivelle", "sombre", [2, 8, 7.5], [3, 9, 8.5]),
        ("tige", "fer", [7.5, 11, 7.5], [8.5, 13, 8.5]),
        ("collier", "sombre", [7, 13, 7], [9, 14, 9]),
        ("pointe", "pointe", [7.5, 14, 7.5], [8.5, 16, 8.5]),
    ]


def cubes_crochet_griffe():
    """Une seule griffe ouverte du crochet lance, vers +Y ; GrappinClient la dessine 4 fois (quart de tour autour de la tige)
    et la replie autour de sa charniere, au collier, en (10, 9, 8)."""
    return griffes(lambda s, a, b: (s, 8 + a, 8 + b), -3, directions=("est",))


def cubes_crochet():
    """Le crochet complet (tige + 4 griffes ouvertes), pour le fichier Blockbench."""
    return cubes_crochet_tige() + griffes(lambda s, a, b: (s, 8 + a, 8 + b), -3)


def cubes_crochet_tige():
    """Le crochet lance sans ses griffes, couche le long de X : l'oeillet de la corde en x = 0, la pointe vers +X."""
    return [
        ("oeillet", "sombre", [0, 7, 7], [2, 9, 9]),
        ("tige", "fer", [2, 7.5, 7.5], [10, 8.5, 8.5]),
        ("collier", "sombre", [10, 7, 7], [11, 9, 9]),
        ("pointe", "pointe", [11, 7.5, 7.5], [13, 8.5, 8.5]),
    ]


# --- Faces et UV -----------------------------------------------------------------------------------------------
def taille_face(face, de, a):
    dx, dy, dz = (a[i] - de[i] for i in range(3))
    return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}[face]


def uv_pixels(matiere, face, de, a, indice):
    """Rectangle de texture (en pixels de la texture 32x32) : 1 pixel par 1/16 de bloc, comme Minecraft ;
    decale selon le cube pour que deux faces voisines ne soient pas identiques."""
    x0, y0, l, h = ZONES[matiere]
    w, hh = taille_face(face, de, a)
    dx = (indice * 3) % max(1, int(l - w) + 1)
    dy = (indice * 5) % max(1, int(h - hh) + 1)
    assert w <= l and hh <= h, f"face {face} trop grande pour la zone {matiere}"
    return [x0 + dx, y0 + dy, x0 + dx + w, y0 + dy + hh]


FACES = ("north", "east", "south", "west", "up", "down")

# Reglages d'affichage de minecraft:item/handheld (et item/generated pour sol, tete, cadre)
AFFICHAGE_MAIN = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
}
PENCHE = {"angle": -45, "axis": "z", "origin": [8, 8, 8]}       # manche en bas a gauche, crochet en haut a droite


def modele_json(cubes, penche, affichage):
    elements = []
    for i, (nom, matiere, de, a) in enumerate(cubes):
        el = {"name": nom, "from": de, "to": a, "faces": {}}
        if penche:
            el["rotation"] = dict(penche)
        for face in FACES:
            u = uv_pixels(matiere, face, de, a, i)
            el["faces"][face] = {"uv": [v / 2 for v in u], "texture": "#0"}
        elements.append(el)
    m = {"texture_size": [32, 32],
         "textures": {"0": "donjon:item/grappin_modele", "particle": "donjon:item/grappin_modele"},
         "elements": elements}
    if affichage:
        m["display"] = affichage
    return m


def bbmodel(nom, cubes, penche, affichage, texture):
    tampon = io.BytesIO()
    texture.save(tampon, "PNG")
    graine = uuid.UUID("6f1c1d2e-0000-4000-8000-000000000000")
    elements = []
    for i, (n, matiere, de, a) in enumerate(cubes):
        rot = [0, 0, 0]
        origine = [(de[k] + a[k]) / 2 for k in range(3)]
        if penche:
            rot[{"x": 0, "y": 1, "z": 2}[penche["axis"]]] = penche["angle"]
            origine = penche["origin"]
        elements.append({
            "name": n, "box_uv": False, "rescale": False, "locked": False, "render_order": "default",
            "allow_mirror_modeling": True, "from": de, "to": a, "autouv": 0, "color": i % 8,
            "origin": origine, "rotation": rot,
            "faces": {f: {"uv": uv_pixels(matiere, f, de, a, i), "texture": 0} for f in FACES},
            "type": "cube", "uuid": str(uuid.uuid5(graine, f"{nom}/{n}")),
        })
    return {
        "meta": {"format_version": "5.0", "model_format": "java_block", "box_uv": False},
        "name": nom, "parent": "", "ambientocclusion": True, "front_gui_light": False,
        "visible_box": [1, 1, 0], "variable_placeholders": "", "variable_placeholder_buttons": [],
        "unhandled_root_fields": {},
        "resolution": {"width": 32, "height": 32},
        "elements": elements,
        "outliner": [e["uuid"] for e in elements],
        "textures": [{
            "path": "", "name": "grappin_modele.png", "folder": "item", "namespace": "donjon", "id": "0",
            "width": 32, "height": 32, "uv_width": 32, "uv_height": 32, "particle": True, "use_as_default": True,
            "layers_enabled": False, "render_mode": "default", "render_sides": "auto", "frame_time": 1,
            "frame_order_type": "loop", "frame_order": "", "frame_interpolate": False, "visible": True,
            "internal": True, "saved": True, "uuid": str(uuid.uuid5(graine, f"{nom}/texture")),
            "relative_path": "", "source": "data:image/png;base64," + base64.b64encode(tampon.getvalue()).decode(),
        }],
        "display": affichage or {},
    }


def ecrire_json(chemin, donnees):
    chemin.parent.mkdir(parents=True, exist_ok=True)
    chemin.write_text(json.dumps(donnees, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main():
    sans_modeles = "--sans-modeles" in sys.argv
    tex = texture_modele()
    (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
    tex.save(ASSETS / "textures/item/grappin_modele.png")
    icone().save(ASSETS / "textures/item/grappin.png")
    if not sans_modeles:
        modeles = ASSETS / "models/item"
        ecrire_json(modeles / "grappin.json", {
            "loader": "neoforge:separate_transforms",
            "base": {"parent": "donjon:item/grappin_main"},
            "perspectives": {"gui": {"parent": "donjon:item/grappin_icone"}},
            # crochet lance (propriete grappin:tire, GrappinClient) : le lanceur sans sa tete
            "overrides": [{"predicate": {"donjon:tire": 1}, "model": "donjon:item/grappin_tire"}],
        })
        ecrire_json(modeles / "grappin_tire.json", {
            "loader": "neoforge:separate_transforms",
            "base": {"parent": "donjon:item/grappin_main_vide"},
            "perspectives": {"gui": {"parent": "donjon:item/grappin_icone"}},
        })
        ecrire_json(modeles / "grappin_icone.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": "donjon:item/grappin"}})
        ecrire_json(modeles / "grappin_main.json", modele_json(cubes_main(), PENCHE, AFFICHAGE_MAIN))
        ecrire_json(modeles / "grappin_main_vide.json", modele_json(cubes_main_vide(), PENCHE, AFFICHAGE_MAIN))
        ecrire_json(modeles / "crochet_tige.json", modele_json(cubes_crochet_tige(), None, None))
        ecrire_json(modeles / "crochet_griffe.json", modele_json(cubes_crochet_griffe(), None, None))
        (modeles / "crochet.json").unlink(missing_ok=True)                                  # remplace par crochet_tige + crochet_griffe
        ecrire_json(BBMODELES / "grappin.bbmodel", bbmodel("grappin", cubes_main(), PENCHE, AFFICHAGE_MAIN, tex))
        ecrire_json(BBMODELES / "crochet.bbmodel", bbmodel("crochet", cubes_crochet(), None, None, tex))
    print("grappin : textures" + ("" if sans_modeles else ", modeles et fichiers Blockbench") + " ecrits")


if __name__ == "__main__":
    main()
