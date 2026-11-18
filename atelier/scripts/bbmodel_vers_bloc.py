# [claude-build] Exporte un modele Blockbench « Java Block » (.bbmodel) en modeles de bloc JSON de Minecraft 1.21.1 (NeoForge), dans
# assets/grappin/models/block/station/ :
#   <nom>.json                                  le modele ;
#   --a-part <prefixe>                          les cubes <prefixe>* vont dans <nom>_<prefixe sans _>.json (piece animee par le jeu) ;
#   --variantes <tex>:<suffixe>=<texture>,...   un modele <nom>_<suffixe>.json par variante, <texture> mise a la place de <tex> ;
#   --objet                                     <nom>_objet.json : tout le modele, reduit pour tenir dans une case (objet en main,
#                                               inventaire) quand il deborde du bloc.
# Lumineux (block_light/sky_light 15, sans ombrage) : les cubes nommes lueur_* et ceux dont une face porte une texture *_allume.
# Texture avec transparence : rendu cutout. Particules : la texture la plus employee. Textures : grappin:block/station/<nom>.
# Les commandes pour les blocs de la Station sont dans atelier/scripts/station_decor_modeles.sh.
import base64
import io
import json
import math
import sys
from collections import Counter
from pathlib import Path

from PIL import Image

RACINE = Path(__file__).resolve().parents[2]
ASSETS = RACINE / "atelier/donjon/arene/src/main/resources/assets/grappin"
MODELES = ASSETS / "models/block/station"
VUES = {"gui": ([30, 225, 0], 0.625), "ground": ([0, 0, 0], 0.25), "fixed": ([0, 0, 0], 0.5),
        "thirdperson_righthand": ([75, 45, 0], 0.375), "firstperson_righthand": ([0, 45, 0], 0.4), "firstperson_lefthand": ([0, 225, 0], 0.4)}


def transparente(nom):
    img = Image.open(ASSETS / "textures/block/station" / f"{nom}.png").convert("RGBA")
    return img.getchannel("A").getextrema()[0] < 255


def element(e, noms):
    sortie = {"name": e["name"], "from": e["from"], "to": e["to"]}
    r = e.get("rotation", [0, 0, 0])
    axes = [i for i in range(3) if abs(r[i]) > 1e-9]
    if axes:
        i = axes[0]
        sortie["rotation"] = {"angle": r[i], "axis": "xyz"[i], "origin": e.get("origin", [8, 8, 8])}
    faces = {}
    for nom, f in e["faces"].items():
        if f.get("texture") is None:
            continue
        faces[nom] = {"uv": f["uv"], "texture": "#" + noms[f["texture"]]}
    sortie["faces"] = faces
    return sortie


def modele(source, elements, textures, display=None):
    """textures : {variable: nom de texture}. Les cubes lumineux sont reperes ici, une fois les variantes appliquees."""
    els = []
    for e in elements:
        e = json.loads(json.dumps(e))
        if e["name"].startswith("lueur_") or any(textures[f["texture"][1:]].endswith("_allume") for f in e["faces"].values()):
            e["shade"] = False
            e["neoforge_data"] = {"block_light": 15, "sky_light": 15}
        els.append(e)
    compte = Counter(f["texture"][1:] for e in els for f in e["faces"].values())
    tex = {k: f"grappin:block/station/{v}" for k, v in textures.items()}
    tex["particle"] = "#" + compte.most_common(1)[0][0]
    m = {"credit": f"Genere par atelier/scripts/bbmodel_vers_bloc.py depuis {source}", "parent": "minecraft:block/block",
         "ambientocclusion": False, "textures": tex, "elements": els}
    if any(transparente(v) for v in set(textures.values())):
        m["render_type"] = "minecraft:cutout"
    if display:
        m["display"] = display
    return m


def rot_xyz(rx, ry, rz):
    """Matrice de Quaternionf.rotationXYZ (ItemTransform) : Rx * Ry * Rz."""
    cx, sx = math.cos(math.radians(rx)), math.sin(math.radians(rx))
    cy, sy = math.cos(math.radians(ry)), math.sin(math.radians(ry))
    cz, sz = math.cos(math.radians(rz)), math.sin(math.radians(rz))
    mx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    my = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    mz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]

    def mul(a, b):
        return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    return mul(mx, mul(my, mz))


def affichage(elements):
    """Reduit le modele pour qu'il tienne la place d'un bloc, et le recentre (translation = -R * S * decalage du centre)."""
    bas = [min(e["from"][i] for e in elements) for i in range(3)]
    haut = [max(e["to"][i] for e in elements) for i in range(3)]
    taille = max(haut[i] - bas[i] for i in range(3))
    centre = [(bas[i] + haut[i]) / 2 - 8 for i in range(3)]
    d = {}
    for vue, (rot, echelle) in VUES.items():
        s = echelle * 16 / taille
        r = rot_xyz(*rot)
        t = [-sum(r[i][k] * s * centre[k] for k in range(3)) for i in range(3)]
        d[vue] = {"rotation": rot, "translation": [round(max(-80, min(80, v)), 3) for v in t], "scale": [round(s, 4)] * 3}
    return d


def ecrire(nom, m):
    MODELES.mkdir(parents=True, exist_ok=True)
    (MODELES / f"{nom}.json").write_text(json.dumps(m, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("ecrit", f"models/block/station/{nom}.json")


def main():
    args = sys.argv[1:]
    chemin = Path(args[0]).resolve()
    bb = json.loads(chemin.read_text(encoding="utf-8"))
    source = chemin.relative_to(RACINE).as_posix()
    nom = chemin.stem
    noms = {i: t["name"].rsplit(".", 1)[0] for i, t in enumerate(bb["textures"])}
    textures = {n: n for n in noms.values()}
    tous = [element(e, noms) for e in bb["elements"]]
    corps = tous
    if "--a-part" in args:
        prefixe = args[args.index("--a-part") + 1]
        corps = [e for e in tous if not e["name"].startswith(prefixe)]
        ecrire(f"{nom}_{prefixe.strip('_')}", modele(source, [e for e in tous if e["name"].startswith(prefixe)], textures))
    if "--variantes" in args:
        tex, liste = args[args.index("--variantes") + 1].split(":")
        for v in liste.split(","):
            suffixe, remplacante = v.split("=")
            ecrire(f"{nom}_{suffixe}", modele(source, corps, dict(textures, **{tex: remplacante})))
    else:
        ecrire(nom, modele(source, corps, textures))
    if "--objet" in args:
        ecrire(f"{nom}_objet", modele(source, tous, textures, affichage(tous)))


if __name__ == "__main__":
    main()
