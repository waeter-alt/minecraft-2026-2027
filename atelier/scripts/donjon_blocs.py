# [claude-build] Blocs et objets du Donjon Suspendu (mod grappin) : portes de pierre gravee a runes, sceaux de pierre precieuse,
# levier de racine et cles, au code couleur des portes (bleu = porte 1, saphir ; jaune = porte 2, ambre ; rouge = porte 3, rubis).
# Ecrit les textures 16 x 16 (pixel art dessine ici), les etats de blocs, les modeles, les tables de butin, et les fichiers Blockbench
# du levier et des cles (atelier/modeles/donjon/). Une fois retouches dans Blockbench, ces fichiers sont la source : exporter alors
# depuis Blockbench (« Java Block/Item ») au lieu de relancer ce script.
#   test-local/venv/Scripts/python.exe -I atelier/scripts/donjon_blocs.py
import base64
import io
import json
import random
import uuid
from pathlib import Path

from PIL import Image

RACINE = Path(__file__).resolve().parents[2]
ASSETS = RACINE / "atelier/donjon/arene/src/main/resources/assets/grappin"
DATA = RACINE / "atelier/donjon/arene/src/main/resources/data/grappin"
BBMODELES = RACINE / "atelier/modeles/donjon"
GRAINE = uuid.UUID("9a1e4c2d-5b6f-4e70-8a1b-2c3d00000000")

# couleur : (nom de la porte, (sombre, moyen, clair, eclat)) ; les veines et les gemmes vont du sombre a l'eclat
COULEURS = {
    "bleue": ("bleu", ((24, 52, 140), (40, 96, 214), (96, 168, 255), (190, 230, 255))),
    "jaune": ("jaune", ((140, 82, 10), (214, 150, 28), (255, 205, 70), (255, 240, 170))),
    "rouge": ("rouge", ((110, 12, 22), (186, 28, 40), (240, 78, 72), (255, 180, 160))),
}
PIERRE = [(44, 44, 50), (56, 56, 62), (68, 68, 74), (82, 82, 88), (98, 98, 104)]
MOUSSE = [(52, 84, 30), (74, 112, 40), (96, 140, 52)]
BOIS = [(58, 38, 22), (82, 54, 30), (108, 74, 42), (134, 96, 58)]
FER = [(52, 54, 60), (84, 86, 94), (124, 126, 134), (172, 174, 182)]


def png(img, chemin):
    chemin.parent.mkdir(parents=True, exist_ok=True)
    img.save(chemin)


def pierre_gravee(graine):
    """Pierre sombre taillee : bord sculpte (clair en haut a gauche, sombre en bas a droite), grain, fissures, mousse dans les joints."""
    h = random.Random(graine)
    img = Image.new("RGBA", (16, 16))
    p = img.load()
    for y in range(16):
        for x in range(16):
            p[x, y] = PIERRE[h.choice((1, 1, 2, 2, 2, 3))] + (255,)
    for k in range(16):
        p[k, 0] = PIERRE[4] + (255,)
        p[0, k] = PIERRE[3] + (255,)
        p[k, 15] = PIERRE[0] + (255,)
        p[15, k] = PIERRE[0] + (255,)
    x, y = h.randint(3, 12), 1                                              # une fissure qui descend
    while y < 15:
        p[x, y] = PIERRE[0] + (255,)
        x = max(1, min(14, x + h.choice((-1, 0, 0, 1))))
        y += 1
    for _ in range(7):                                                      # mousse dans les joints du bord
        x, y = h.choice(((h.randint(1, 14), 14), (14, h.randint(1, 14)), (h.randint(1, 14), 15), (15, h.randint(1, 14))))
        p[x, y] = MOUSSE[h.randint(0, 2)] + (255,)
    return img


# rune gravee au centre (1 = trait de la veine), sur la pierre ; les veines partent de la rune vers les bords
RUNE = [
    "................",
    ".......1........",
    ".......1........",
    "......111.......",
    ".....1.1.1......",
    "....1..1..1.....",
    "...1...1...1....",
    ".......1........",
    "......111.......",
    ".....1...1......",
    ".....1...1......",
    "......111.......",
    ".......1........",
    "......1.1.......",
    ".....1...1......",
    "................",
]


def porte(couleur):
    _, (sombre, moyen, clair, eclat) = COULEURS[couleur]
    img = pierre_gravee(sum(map(ord, couleur)))
    p = img.load()
    for y, ligne in enumerate(RUNE):
        for x, c in enumerate(ligne):
            if c == "1":
                p[x, y] = (clair if (x + y) % 3 else eclat) + (255,)
                if x + 1 < 16 and RUNE[y][x + 1] == "." and p[x + 1, y][:3] in PIERRE:
                    p[x + 1, y] = sombre + (255,)                         # ombre de la gravure, de la couleur de la rune
    return img


def sceau(couleur):
    """Pierre precieuse taillee dans un cadre de pierre, avec un trou de serrure au milieu."""
    _, (sombre, moyen, clair, eclat) = COULEURS[couleur]
    img = pierre_gravee(sum(map(ord, couleur)) + 7)
    p = img.load()
    for y in range(3, 13):
        for x in range(3, 13):
            d = abs(x - 7.5) + abs(y - 7.5)                                   # losange taille en facettes
            if d <= 5.5:
                if d > 4.5:
                    c = sombre
                elif x < 7.5 and y < 7.5:
                    c = eclat if d < 2 else clair
                elif x >= 7.5 and y >= 7.5:
                    c = sombre if d > 3 else moyen
                else:
                    c = moyen if d > 2 else clair
                p[x, y] = c + (255,)
    for x, y in ((7, 6), (8, 6), (7, 7), (8, 7), (7, 8), (7, 9), (8, 9)):     # trou de serrure
        p[x, y] = (20, 18, 24, 255)
    p[5, 5] = (255, 255, 255, 255)
    return img


def unie(couleurs, graine, taille=16):
    h = random.Random(graine)
    img = Image.new("RGBA", (taille, taille))
    p = img.load()
    for y in range(taille):
        for x in range(taille):
            p[x, y] = couleurs[h.randint(0, len(couleurs) - 1)] + (255,)
    return img


def socle_levier():
    img = pierre_gravee(42)
    p = img.load()
    h = random.Random(5)
    for _ in range(40):
        p[h.randint(1, 14), h.randint(1, 6)] = MOUSSE[h.randint(0, 2)] + (255,)   # dessus moussu
    return img


def racine_levier():
    """Ecorce de racine : fil vertical, noeuds clairs."""
    h = random.Random(9)
    img = Image.new("RGBA", (16, 16))
    p = img.load()
    for y in range(16):
        for x in range(16):
            p[x, y] = BOIS[0 if x % 4 == 0 else (2 if h.random() < 0.2 else 1)] + (255,)
    for x, y in ((5, 4), (10, 9), (3, 12)):
        p[x, y] = BOIS[3] + (255,)
    return img


def ambre(allume):
    base = [(150, 92, 20), (200, 130, 30), (240, 170, 50)] if not allume else [(255, 180, 50), (255, 210, 90), (255, 240, 170)]
    img = unie(base, 77 if allume else 78)
    img.load()[4, 4] = (255, 255, 230, 255)
    return img


# Icone de cle 16 x 16 : anneau en haut a gauche avec la pierre, tige en diagonale, dents en bas a droite
CLE = [
    "................",
    ".FFFF...........",
    "FfGGfF..........",
    "FGeGGF..........",
    "FGGGsF..........",
    "FfGsfF..........",
    ".FFFFf..........",
    ".....Ff.........",
    "......Ff........",
    ".......Ff.......",
    "........Ff..f...",
    ".........FfFf...",
    "..........Ff....",
    "...........Ff...",
    "............ff..",
    "................",
]


def icone_cle(couleur):
    _, (sombre, moyen, clair, eclat) = COULEURS[couleur]
    pal = {"F": FER[2], "f": FER[1], "G": moyen, "e": eclat, "s": sombre}
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = img.load()
    for y, ligne in enumerate(CLE):
        for x, c in enumerate(ligne):
            if c != ".":
                p[x, y] = pal[c] + (255,)
    return img


def texture_cle_modele(couleur):
    """Texture du modele 3D de la cle : moitie gauche fer, quart haut droit la pierre, quart bas droit fer sombre."""
    _, (sombre, moyen, clair, eclat) = COULEURS[couleur]
    img = unie(FER[1:4], 3)
    p = img.load()
    h = random.Random(4)
    for y in range(8):
        for x in range(8, 16):
            p[x, y] = h.choice((moyen, moyen, clair, eclat)) + (255,)
    for y in range(8, 16):
        for x in range(8, 16):
            p[x, y] = FER[h.randint(0, 1)] + (255,)
    return img


# ---------------------------------------------------------------------------------------------------- modeles a cubes
def faces(de, a, uv_coin=(0, 0)):
    """Chaque face prend un rectangle de sa taille a partir du coin donne (texture 16 x 16, 1 pixel par unite)."""
    w, h, d = (a[i] - de[i] for i in range(3))
    u, v = uv_coin
    taille = {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}
    return {f: {"uv": [u, v, min(16, u + t[0]), min(16, v + t[1])], "texture": "#" + tex} for f, t in taille.items()
            for tex in ["0"]}


def element(nom, de, a, texture="0", uv_coin=(0, 0), rotation=None):
    e = {"name": nom, "from": list(de), "to": list(a), "faces": faces(de, a, uv_coin)}
    for f in e["faces"].values():
        f["texture"] = "#" + texture
    if rotation:
        e["rotation"] = rotation
    return e


def levier(allume):
    """Socle de pierre moussue (comme celui d'un levier) et racine tordue avec sa boule d'ambre, inclinee vers le nord (leve) ou le sud (baisse)."""
    angle = 45 if allume else -45
    rot = {"angle": angle, "axis": "x", "origin": [8, 1, 8]}
    elements = [
        element("socle", (5, 0, 4), (11, 3, 12), "socle"),
        element("racine", (7, 1, 7), (9, 10, 9), "racine", rotation=rot),
        element("noeud", (6.5, 4, 6.5), (9.5, 5.5, 9.5), "racine", (6, 6), rotation=rot),
        element("radicelle", (9, 2, 7.5), (10.5, 3, 8.5), "racine", (2, 9), rotation=rot),
        element("ambre", (6.5, 9.5, 6.5), (9.5, 12.5, 9.5), "ambre", rotation=rot),
    ]
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"socle": "grappin:block/levier_socle", "racine": "grappin:block/levier_racine",
                         "ambre": "grappin:block/levier_ambre" + ("_allume" if allume else ""), "particle": "grappin:block/levier_socle"},
            "elements": elements}


def etats_levier():
    """Memes variantes que le levier de Minecraft : sol (y selon facing), plafond (x 180), mur (x 90)."""
    y_sol = {"north": 0, "east": 90, "south": 180, "west": 270}
    y_plafond = {"north": 180, "east": 270, "south": 0, "west": 90}
    variantes = {}
    for powered in ("false", "true"):
        modele = "grappin:block/levier_racine" + ("_baisse" if powered == "true" else "")
        for facing in y_sol:
            variantes[f"face=floor,facing={facing},powered={powered}"] = {"model": modele, "y": y_sol[facing]}
            variantes[f"face=ceiling,facing={facing},powered={powered}"] = {"model": modele, "x": 180, "y": y_plafond[facing]}
            variantes[f"face=wall,facing={facing},powered={powered}"] = {"model": modele, "x": 90, "y": y_sol[facing]}
    for v in variantes.values():
        if v.get("y") == 0:
            del v["y"]
    return {"variants": variantes}


AFFICHAGE_CLE = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
}


def cle_modele(couleur):
    """La cle en main, en relief : anneau carre autour de la pierre, tige, deux dents ; penchee comme un outil."""
    rot = {"angle": -45, "axis": "z", "origin": [8, 8, 8]}
    els = [
        element("anneau_haut", (5, 13, 7.5), (11, 14, 8.5), "0", (0, 0), rot),
        element("anneau_bas", (5, 9, 7.5), (11, 10, 8.5), "0", (0, 2), rot),
        element("anneau_g", (5, 10, 7.5), (6, 13, 8.5), "0", (2, 4), rot),
        element("anneau_d", (10, 10, 7.5), (11, 13, 8.5), "0", (4, 4), rot),
        element("pierre", (6, 10, 7), (10, 13, 9), "0", (9, 1), rot),
        element("tige", (7.5, 1, 7.5), (8.5, 9, 8.5), "0", (1, 6), rot),
        element("dent_haute", (8.5, 3, 7.5), (10.5, 4, 8.5), "0", (9, 9), rot),
        element("dent_basse", (8.5, 1, 7.5), (11, 2, 8.5), "0", (9, 12), rot),
    ]
    return {"texture_size": [16, 16], "textures": {"0": f"grappin:item/cle_{couleur}_modele", "particle": f"grappin:item/cle_{couleur}_modele"},
            "elements": els, "display": AFFICHAGE_CLE}


def bbmodel(nom, modele, textures):
    """Fichier Blockbench (format Java Block/Item) du meme modele, textures integrees."""
    cles = list(modele["textures"].keys())
    cles = [c for c in cles if c != "particle"]
    elements = []
    for i, e in enumerate(modele["elements"]):
        r = e.get("rotation")
        rot = [0, 0, 0]
        if r:
            rot["xyz".index(r["axis"])] = r["angle"]
        elements.append({"name": e["name"], "box_uv": False, "rescale": False, "locked": False, "from": e["from"], "to": e["to"],
                         "autouv": 0, "color": i % 8, "origin": r["origin"] if r else [8, 8, 8], "rotation": rot,
                         "faces": {f: {"uv": v["uv"], "texture": cles.index(v["texture"][1:])} for f, v in e["faces"].items()},
                         "type": "cube", "uuid": str(uuid.uuid5(GRAINE, f"{nom}/{i}"))})
    texs = []
    for k, c in enumerate(cles):
        tampon = io.BytesIO()
        textures[c].save(tampon, "PNG")
        texs.append({"path": "", "name": c + ".png", "folder": "block", "namespace": "grappin", "id": str(k), "width": 16, "height": 16,
                     "uv_width": 16, "uv_height": 16, "particle": k == 0, "render_mode": "default", "visible": True, "internal": True,
                     "saved": True, "uuid": str(uuid.uuid5(GRAINE, f"{nom}/tex/{c}")),
                     "source": "data:image/png;base64," + base64.b64encode(tampon.getvalue()).decode()})
    return {"meta": {"format_version": "4.10", "model_format": "java_block", "box_uv": False}, "name": nom, "parent": "",
            "ambientocclusion": True, "front_gui_light": False, "visible_box": [1, 1, 0], "resolution": {"width": 16, "height": 16},
            "elements": elements, "outliner": [e["uuid"] for e in elements], "textures": texs, "display": modele.get("display", {})}


def ecrire(chemin, donnees):
    chemin.parent.mkdir(parents=True, exist_ok=True)
    chemin.write_text(json.dumps(donnees, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def butin(nom):
    return {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"grappin:{nom}"}],
                                                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]}


ICONE_LEVIER = [
    "......aAa.......",
    ".....aAAAa......",
    ".....aAAAa......",
    "......aAa.......",
    ".......r........",
    "......rR........",
    "......Rr........",
    ".....rR.........",
    ".....Rr.........",
    "....rR..........",
    "...SSrSS........",
    "..SPPPPPS.......",
    "..SPPpPPS.......",
    "..SPpPPPS.......",
    ".rSSSSSSSr......",
    "................",
]


def icone_levier():
    """Icone d'inventaire du levier de racine (le bloc lui-meme est dessine par le code, depuis levier_racine.bbmodel)."""
    pal = {"a": (200, 130, 30), "A": (255, 210, 90), "r": BOIS[1], "R": BOIS[2], "S": PIERRE[1], "P": PIERRE[3], "p": MOUSSE[1]}
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    q = img.load()
    for y, ligne in enumerate(ICONE_LEVIER):
        for x, c in enumerate(ligne):
            if c != ".":
                q[x, y] = pal[c] + (255,)
    return img


def main():
    # Les portes et les sceaux en blocs ont ete abandonnes (Luca, 2026-11-18) : la porte est une entite (atelier/scripts/donjon_3d.py).
    tb, tm = ASSETS / "textures/block", ASSETS / "textures/item"
    for couleur in COULEURS:
        png(icone_cle(couleur), tm / f"cle_{couleur}.png")
        png(texture_cle_modele(couleur), tm / f"cle_{couleur}_modele.png")
        modele = cle_modele(couleur)
        ecrire(ASSETS / f"models/item/cle_{couleur}_main.json", modele)
        ecrire(ASSETS / f"models/item/cle_{couleur}_icone.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": f"grappin:item/cle_{couleur}"}})
        ecrire(ASSETS / f"models/item/cle_{couleur}.json", {"loader": "neoforge:separate_transforms",
                                                            "base": {"parent": f"grappin:item/cle_{couleur}_main"},
                                                            "perspectives": {"gui": {"parent": f"grappin:item/cle_{couleur}_icone"}}})
        ecrire(BBMODELES / f"cle_{couleur}.bbmodel", bbmodel(f"cle_{couleur}", modele, {"0": texture_cle_modele(couleur)}))
    # Le levier de racine est dessine par le code (LevierRendu, depuis levier_racine.bbmodel de donjon_3d.py) : son modele de bloc ne
    # sert qu'aux particules quand on le casse, et son objet a une icone a plat.
    png(socle_levier(), tb / "levier_socle.png")
    png(icone_levier(), tm / "levier_racine.png")
    ecrire(ASSETS / "models/block/levier_racine.json", {"textures": {"particle": "grappin:block/levier_socle"}})
    etats = etats_levier()
    for v in etats["variants"].values():
        v["model"] = "grappin:block/levier_racine"
    ecrire(ASSETS / "blockstates/levier_racine.json", etats)
    ecrire(ASSETS / "models/item/levier_racine.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "grappin:item/levier_racine"}})
    ecrire(DATA / "loot_table/blocks/levier_racine.json", butin("levier_racine"))
    for vieux in (ASSETS / "models/block/levier_racine_baisse.json", tb / "levier_racine.png", tb / "levier_ambre.png", tb / "levier_ambre_allume.png"):
        vieux.unlink(missing_ok=True)
    print("donjon : levier de racine et cles ecrits")


if __name__ == "__main__":
    main()
