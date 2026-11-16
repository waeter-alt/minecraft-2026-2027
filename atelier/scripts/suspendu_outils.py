# [claude-build]
"""suspendu_outils.py - outils communs du Donjon Suspendu : l'etat de bloc S(), un hachage deterministe, et l'ecriture / relecture d'une structure .nbt.
Une salle est un dictionnaire {(x, y, z): etat de bloc} en coordonnees du gabarit du donjon ; l'ecriture le recadre sur sa boite englobante et renvoie le decalage a utiliser a la pose.
L'air compte comme un bloc : une structure posee sur de la roche la creuse (les sous-sols sont tailles dans la pierre)."""
from pathlib import Path

import nbtlib
from nbtlib import Compound, Int, List, String

DATA_VERSION = 3955


def S(nom, **props):
    nom = nom if ":" in nom else "minecraft:" + nom
    return (nom, tuple(sorted((k, str(v).lower() if isinstance(v, bool) else str(v)) for k, v in props.items())))


AIR = S("air")


def h01(x, y, z, g=0):
    h = (int(x) * 374761393 + int(y) * 1442695041 + int(z) * 668265263 + g * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h ^= h >> 16
    return h / 0xFFFFFFFF


def boite(blocs):
    xs, ys, zs = zip(*blocs)
    return (min(xs), min(ys), min(zs)), (max(xs), max(ys), max(zs))


def ecrire_structure(blocs, chemin, nbt=None):
    """Ecrit la structure `blocs` dans `chemin` (.nbt, gzip). Renvoie le decalage (x, y, z) de son coin dans le gabarit. `nbt` : {(x, y, z): Compound} pour les blocs a donnees (coffres...)."""
    (x0, y0, z0), (x1, y1, z1) = boite(blocs)
    palette, index, liste = [], {}, []
    for p in sorted(blocs, key=lambda q: (q[1], q[2], q[0])):
        s = blocs[p]
        if s not in index:
            index[s] = len(palette)
            c = Compound({"Name": String(s[0])})
            if s[1]:
                c["Properties"] = Compound({k: String(v) for k, v in s[1]})
            palette.append(c)
        e = Compound({"pos": List[Int]([Int(p[0] - x0), Int(p[1] - y0), Int(p[2] - z0)]), "state": Int(index[s])})
        if nbt and p in nbt:
            e["nbt"] = nbt[p]
        liste.append(e)
    f = nbtlib.File({"DataVersion": Int(DATA_VERSION), "size": List[Int]([Int(x1 - x0 + 1), Int(y1 - y0 + 1), Int(z1 - z0 + 1)]), "palette": List[Compound](palette),
                     "blocks": List[Compound](liste), "entities": List[Compound]([])}, gzipped=True)
    Path(chemin).parent.mkdir(parents=True, exist_ok=True)
    f.save(chemin)
    return (x0, y0, z0)


def lire_structure(chemin, decalage=(0, 0, 0)):
    """Relit un .nbt : {(x, y, z): (nom, props)} en coordonnees du gabarit (`decalage` : celui rendu par ecrire_structure)."""
    f = nbtlib.load(chemin)
    pal = [(str(p["Name"]), tuple(sorted((str(k), str(v)) for k, v in dict(p.get("Properties", {})).items()))) for p in f["palette"]]
    dx, dy, dz = decalage
    return {(int(b["pos"][0]) + dx, int(b["pos"][1]) + dy, int(b["pos"][2]) + dz): pal[int(b["state"])] for b in f["blocks"]}
