# [claude-build]
"""Cree exports/Arene_Semaine_map.zip depuis le monde ferme, puis relit l'archive.
Usage : zip_semaine.py   (a lancer quand le monde n'est pas ouvert)"""
import gzip
import io
import zipfile
from pathlib import Path

import nbtlib

R = Path(__file__).resolve().parent.parent
W = R / "launcher/instances/Build-1.21.1/minecraft/saves/Arene Semaine"
Z = R / "exports/Arene_Semaine_map.zip"
with zipfile.ZipFile(Z, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
    for p in sorted(W.rglob("*")):
        if p.is_file() and p.name != "session.lock":
            z.write(p, Path("Arene Semaine") / p.relative_to(W))
with zipfile.ZipFile(Z) as z:
    noms = z.namelist()
    assert z.testzip() is None, "archive abimee"
    d = nbtlib.File.parse(io.BytesIO(gzip.decompress(z.read("Arene Semaine/level.dat"))))["Data"]
    print("fichiers", len(noms), "taille", round(Z.stat().st_size / 1e6, 1), "Mo")
    print("nom", d.get("LevelName"), "commandes", d.get("allowCommands"), "spawn", d.get("SpawnX"), d.get("SpawnY"), d.get("SpawnZ"), "heure", d.get("DayTime"))
    print("regions", sum(1 for n in noms if n.startswith("Arene Semaine/region/")), "session.lock", any(n.endswith("session.lock") for n in noms))
    st = [n for n in noms if "/data/atelier/structure/" in n]
    print("structures", len(st), "fonctions", sum(1 for n in noms if "/data/atelier/function/" in n), "racine unique", {n.split("/")[0] for n in noms})
