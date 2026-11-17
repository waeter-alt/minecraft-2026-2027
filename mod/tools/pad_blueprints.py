"""Donne la meme emprise a tous les niveaux d'un batiment (comme MineColonies).

Les blueprints de MineColonies ont la MEME taille a tous les niveaux : les niveaux bas sont completes avec de l'air, donc
la previsualisation du niveau 1 montre deja la place que le batiment prendra au niveau 5 (et le fantome des voisins,
la collision, la zone protegee suivent). Les notres grandissaient avec le niveau : le joueur posait un petit Relais
a cote d'une route, et il l'avalait au niveau 4.

Ce script lit relais1..5 (ou banque, depot, ...), cale chaque niveau sur la hutte (le bloc qui ne bouge pas quand on
ameliore), calcule la boite qui contient tous les niveaux et reecrit chaque fichier dans cette boite, completee d'air.
Il met a jour la taille, les positions des blocs a entite, les coins du batiment et le decalage de la hutte.
On peut le relancer : le resultat ne change plus.

Usage (depuis la racine du repo ; pip install nbtlib) :
    python -I -X utf8 mod/tools/pad_blueprints.py commerce relais
    python -I -X utf8 mod/tools/pad_blueprints.py regions banque
    python -I -X utf8 mod/tools/pad_blueprints.py regions depot
Ajouter --check pour seulement afficher les tailles sans rien ecrire.

A lancer APRES le script qui genere les blueprints du batiment, et a chaque fois qu'on le relance.
"""
import sys
from pathlib import Path

import nbtlib
from nbtlib.tag import Int, IntArray, Short

LEVELS = range(1, 6)
HUT_ID = "minecolonies:colonybuilding"
ROOT = Path(__file__).resolve().parents[1] / "src" / "main" / "resources" / "blueprints" / "interregne" / "interregne"


def decode(blocks, size):
    """IntArray (deux palettes par entier) -> liste a plat, index = (y * sizeZ + z) * sizeX + x."""
    sx, sy, sz = size
    flat = []
    for value in blocks:
        value = int(value)
        flat.append((value >> 16) & 0xFFFF)
        flat.append(value & 0xFFFF)
    return flat[: sx * sy * sz]


def encode(flat):
    ints = []
    for i in range(0, len(flat), 2):
        high = flat[i]
        low = flat[i + 1] if i + 1 < len(flat) else 0
        value = (high << 16) | low
        if value >= 0x80000000:
            value -= 0x100000000
        ints.append(value)
    return IntArray(ints)


def air_index(palette):
    for i, entry in enumerate(palette):
        if str(entry["Name"]) == "minecraft:air" and len(entry) == 1:
            return i
    raise SystemExit("palette sans air simple : a traiter a la main")


def load(path):
    f = nbtlib.load(path, gzipped=True)
    size = (int(f["size_x"]), int(f["size_y"]), int(f["size_z"]))
    huts = [t for t in f["tile_entities"] if str(t.get("id")) == HUT_ID]
    if len(huts) != 1:
        raise SystemExit("%s : %d huttes, il en faut une" % (path.name, len(huts)))
    hut = (int(huts[0]["x"]), int(huts[0]["y"]), int(huts[0]["z"]))
    return f, size, hut


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    check = "--check" in sys.argv
    if len(args) != 2:
        raise SystemExit(__doc__)
    folder, stem = args
    files = {n: ROOT / folder / ("%s%d.blueprint" % (stem, n)) for n in LEVELS}
    data = {n: load(p) for n, p in files.items()}

    # Boite commune, calee sur la hutte : de -H a (taille - h) de chaque cote
    box_h = tuple(max(data[n][2][i] for n in LEVELS) for i in range(3))
    after = tuple(max(data[n][1][i] - data[n][2][i] for n in LEVELS) for i in range(3))
    box = tuple(box_h[i] + after[i] for i in range(3))
    print("%s : boite commune %s, hutte en %s" % (stem, box, box_h))
    for n in LEVELS:
        print("  niveau %d : %s, hutte %s" % (n, data[n][1], data[n][2]))
    if check:
        return

    for n in LEVELS:
        f, size, hut = data[n]
        shift = tuple(box_h[i] - hut[i] for i in range(3))
        air = air_index(f["palette"])
        flat = decode(f["blocks"], size)
        out = [air] * (box[0] * box[1] * box[2])
        sx, sy, sz = size
        for y in range(sy):
            for z in range(sz):
                row = (y * sz + z) * sx
                dest = ((y + shift[1]) * box[2] + (z + shift[2])) * box[0] + shift[0]
                out[dest: dest + sx] = flat[row: row + sx]
        if len(f["entities"]) != 0:
            raise SystemExit("%s a des entites : decalage non gere" % files[n].name)
        for te in f["tile_entities"]:
            te["x"] = Short(int(te["x"]) + shift[0])
            te["y"] = Short(int(te["y"]) + shift[1])
            te["z"] = Short(int(te["z"]) + shift[2])
        hut_te = next(t for t in f["tile_entities"] if str(t.get("id")) == HUT_ID)
        provider = hut_te["blueprintDataProvider"]
        for axis, name in enumerate("xyz"):
            provider["corner1"][name] = Int(-box_h[axis])
            provider["corner2"][name] = Int(box[axis] - 1 - box_h[axis])
        for axis, name in enumerate("xyz"):
            f["optional_data"]["structurize"]["primary_offset"][name] = Int(box_h[axis])
        f["size_x"], f["size_y"], f["size_z"] = Short(box[0]), Short(box[1]), Short(box[2])
        f["blocks"] = encode(out)
        f.save(files[n], gzipped=True)
    print("  ecrit : 5 fichiers de %s" % (box,))


if __name__ == "__main__":
    main()
