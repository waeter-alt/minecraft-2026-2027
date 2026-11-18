# [claude-build] Exporte un modele Blockbench « Java Block » (.bbmodel) en modeles de bloc JSON de Minecraft 1.21.1 (NeoForge), dans
# assets/grappin/models/block/station/ :
#   <nom>.json                    un bloc ;
#   <nom>_bas.json, <nom>_haut.json, <nom>_objet.json   avec --deux-blocs : les cubes de y >= 16 vont au bloc du haut (descendus de 16),
#                                 les autres au bas ; _objet garde tout, pour l'objet en main ou dans l'inventaire (rendu reduit) ;
#   --variantes <tex>:<a>,<b>,... un modele <nom>_<x>.json par texture <x> mise a la place de <tex> (panneau de profondeur).
# Cubes nommes lueur_* : pleine lumiere (neoforge_data block_light/sky_light 15, sans ombrage). Texture avec transparence : rendu cutout.
# Textures : grappin:block/station/<nom de la texture>.
#   python atelier/scripts/bbmodel_vers_bloc.py atelier/modeles/station/pompe.bbmodel --deux-blocs
import base64
import io
import json
import sys
from pathlib import Path

from PIL import Image

RACINE = Path(__file__).resolve().parents[2]
MODELES = RACINE / "atelier/donjon/arene/src/main/resources/assets/grappin/models/block/station"
OBJET_DEUX_BLOCS = {"gui": {"rotation": [30, 225, 0], "translation": [0, -3.5, 0], "scale": [0.36, 0.36, 0.36]},
                    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.2, 0.2, 0.2]},
                    "fixed": {"rotation": [0, 0, 0], "translation": [0, -2, 0], "scale": [0.36, 0.36, 0.36]},
                    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1.5, 1], "scale": [0.25, 0.25, 0.25]},
                    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.25, 0.25, 0.25]},
                    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.25, 0.25, 0.25]}}


def transparente(t):
    img = Image.open(io.BytesIO(base64.b64decode(t["source"].split(",", 1)[1]))).convert("RGBA")
    return any(p[3] < 255 for p in img.getdata())


def element(e, noms, dy):
    de, a = list(e["from"]), list(e["to"])
    de[1] -= dy
    a[1] -= dy
    sortie = {"name": e["name"], "from": de, "to": a}
    r = e.get("rotation", [0, 0, 0])
    axes = [i for i in range(3) if abs(r[i]) > 1e-9]
    if axes:
        i = axes[0]
        o = list(e.get("origin", [0, 0, 0]))
        o[1] -= dy
        sortie["rotation"] = {"angle": r[i], "axis": "xyz"[i], "origin": o}
    faces = {}
    for nom, f in e["faces"].items():
        if f.get("texture") is None:
            continue
        faces[nom] = {"uv": f["uv"], "texture": "#" + noms[f["texture"]]}
    sortie["faces"] = faces
    if e["name"].startswith("lueur_"):
        sortie["shade"] = False
        sortie["neoforge_data"] = {"block_light": 15, "sky_light": 15}
    return sortie


def modele(source, elements, textures, cutout, display=None):
    m = {"credit": f"Genere par atelier/scripts/bbmodel_vers_bloc.py depuis {source}", "parent": "minecraft:block/block",
         "ambientocclusion": False, "textures": textures, "elements": elements}
    if cutout:
        m["render_type"] = "minecraft:cutout"
    if display:
        m["display"] = display
    return m


def ecrire(nom, m):
    MODELES.mkdir(parents=True, exist_ok=True)
    (MODELES / f"{nom}.json").write_text(json.dumps(m, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("ecrit", f"models/block/station/{nom}.json")


def main():
    args = sys.argv[1:]
    chemin = Path(args[0])
    bb = json.loads(chemin.read_text(encoding="utf-8"))
    source = chemin.resolve().relative_to(RACINE).as_posix()
    nom = chemin.stem
    noms = {}
    textures = {}
    for i, t in enumerate(bb["textures"]):
        n = t["name"].rsplit(".", 1)[0]
        noms[i] = n
        textures[n] = f"grappin:block/station/{n}"
    textures["particle"] = "#" + noms[0]
    cutout = any(transparente(t) for t in bb["textures"])
    elements = bb["elements"]
    if "--deux-blocs" in args:
        bas = [element(e, noms, 0) for e in elements if e["from"][1] < 16]
        haut = [element(e, noms, 16) for e in elements if e["from"][1] >= 16]
        ecrire(nom + "_bas", modele(source, bas, textures, cutout))
        ecrire(nom + "_haut", modele(source, haut, textures, cutout))
        ecrire(nom + "_objet", modele(source, [element(e, noms, 0) for e in elements], textures, cutout, OBJET_DEUX_BLOCS))
    elif "--variantes" in args:
        tex, liste = args[args.index("--variantes") + 1].split(":")
        for v in liste.split(","):
            t = dict(textures, **{tex: f"grappin:block/station/{v}"})
            ecrire(f"{nom}_{v.removeprefix(tex.rsplit('_', 2)[0] + '_')}", modele(source, [element(e, noms, 0) for e in elements], t, cutout))
    else:
        ecrire(nom, modele(source, [element(e, noms, 0) for e in elements], textures, cutout))


if __name__ == "__main__":
    main()
