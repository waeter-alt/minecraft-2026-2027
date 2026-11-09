# [claude-build]
"""Arene piege, lieu de vie du scientifique : chaque zone doit rester accessible quand on bouche sa porte, puis quand on bouche ses deux arches.
Usage : verif_scientifique.py   (relit exports/piege.nbt et exports/piege_cibles.json, lance verif_acces.py)"""
import json
import math
import shutil
import subprocess
import sys
from pathlib import Path

R = Path(__file__).resolve().parent.parent
conf = json.loads((R / "exports/piege_cibles.json").read_text(encoding="utf-8"))
SOL, C = conf["sol"], 52
TMP = R / "exports/verif_tmp"
TMP.mkdir(exist_ok=True)


def dist(x, z):
    return math.hypot(x + .5 - C, z + .5 - C)


def ang(x, z):
    return math.degrees(math.atan2(z + .5 - C, x + .5 - C)) % 360


def adiff(a, b):
    return abs((a - b + 180) % 360 - 180)


def porte(k):
    return [[x, y, z] for x in range(104) for z in range(104) for y in range(SOL + 1, SOL + 4)
            if 34 <= dist(x, z) < 36 and adiff(ang(x, z), 45 * k) * math.pi / 180 * dist(x, z) < 2.2]


def arches(k):
    return [[x, y, z] for cb in (45 * k - 22.5, 45 * k + 22.5) for x in range(104) for z in range(104) for y in range(SOL + 1, SOL + 4)
            if 16.5 <= dist(x, z) < 34 and adiff(ang(x, z), cb % 360) < 57.3 / dist(x, z) + 1.5 and abs(dist(x, z) - 26) < 2.2]


zones = {n: p for n, p in conf["cibles"].items() if n.startswith("zone : ")}
ANGLE = {"Laboratoire": 0, "expériences": 45, "production": 90, "Réserve": 135, "Bureau": 180, "Quartiers": 225, "machines": 270, "Serre": 315}
err = 0
for n, p in zones.items():
    k = next(a for mot, a in ANGLE.items() if mot in n) // 45
    for titre, bloque in (("porte bouchee", porte(k)), ("arches bouchees", arches(k))):
        f = TMP / "cibles.json"
        f.write_text(json.dumps({"_marque": "[claude-build]", "sol": SOL, "depart": conf["depart"], "cibles": {n: p}, "bloquer": bloque}, ensure_ascii=False), encoding="utf-8")
        r = subprocess.run([sys.executable, str(R / "sources/verif_acces.py"), str(R / "exports/piege.nbt"), str(f)], capture_output=True, text=True, encoding="utf-8", errors="replace")
        ok = "tout est accessible" in r.stdout
        err += not ok
        print(f"{n:<55} {titre:<16} {len(bloque):>3} cases bouchees : {'OK' if ok else 'INACCESSIBLE'}")
shutil.rmtree(TMP)
print("RESULTAT :", "chaque zone a au moins deux chemins" if not err else f"{err} cas sans second chemin")
sys.exit(1 if err else 0)
