# [claude-build]
"""station_retouches.py - les retouches faites a la main par Luca, en jeu, sur le Donjon de la Station (capturees par capture_retouches_station.py).
atelier/donjon/station_retouches.json : {"blocs": {"x,y,z": "minecraft:bloc[prop=val]"}} en coordonnees du gabarit. Le generateur les pose par-dessus tout."""
import json
import re
from pathlib import Path

FICHIER = Path(__file__).resolve().parent.parent / "donjon" / "station_retouches.json"


def etat(b):
    nom, props = b
    return nom + ("[" + ",".join(f"{k}={v}" for k, v in props) + "]" if props else "")


def lire_etat(texte):
    m = re.fullmatch(r"([a-z0-9_:.\-/]+)(?:\[(.*)\])?", texte)
    nom = m.group(1) if ":" in m.group(1) else "minecraft:" + m.group(1)
    props = tuple(sorted(tuple(kv.split("=", 1)) for kv in m.group(2).split(","))) if m.group(2) else ()
    return nom, props


def charger():
    """{(x, y, z): (nom, props)} ; vide s'il n'y a pas encore de retouches."""
    if not FICHIER.exists():
        return {}
    d = json.loads(FICHIER.read_text(encoding="utf-8"))
    return {tuple(int(v) for v in k.split(",")): lire_etat(e) for k, e in d.get("blocs", {}).items()}
