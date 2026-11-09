# [claude-build]
"""Controle le monde final Arene Semaine : emprises, arrivees devant chaque arene, passage de l'arrivee a l'entree, commandes des portes et des retours.
Usage : verif_semaine.py
Le passage reel d'un joueur et la pose se constatent en jeu."""
import json
import math
import re
import sys
from pathlib import Path

import nbtlib

R = Path(__file__).resolve().parent.parent
conf = json.loads((R / "exports/semaine.json").read_text(encoding="utf-8"))
ST = R / "launcher/instances/Build-1.21.1/minecraft/saves/Arene Semaine/datapacks/atelier/data/atelier"
err = []


def lire(n):
    f = nbtlib.load(ST / "structure" / f"{n}.nbt")
    pal = [str(p["Name"]).split(":")[1] for p in f["palette"]]
    return f, {tuple(int(v) for v in b["pos"]): pal[int(b["state"])] for b in f["blocks"]}, {tuple(int(v) for v in b["pos"]): str(b["nbt"]["Command"]) for b in f["blocks"] if "nbt" in b and "Command" in b["nbt"]}


zones = []
hub, H, HC = lire("hub7")
print("hub7", [int(v) for v in hub["size"]], len(H), "commandes", len(HC))
for a in conf["arenes"]:
    f, G, C_ = lire(a["f"])
    cx, cz = a["c"]
    ox, oy, oz = cx - 52, a["y"], cz - 52
    sx, sy, sz = [int(v) for v in f["size"]]
    zones.append((a["nom"], ox, oz, ox + sx, oz + sz))
    r, RG, RC = lire(f"retour_{a['f']}")
    fn = (ST / "function").glob("semaine_*.mcfunction")
    place = [l for p in fn if re.fullmatch(r"semaine_\d+\.mcfunction", p.name)          # la pose complete seulement, pas les mises a jour
             for l in p.read_text(encoding="utf-8").splitlines() if l.startswith(f"place template atelier:retour_{a['f']} ")]
    if len(place) != 1:
        err.append(f"pose du retour {a['f']}")
        continue
    ro = [int(v) for v in place[0].split()[3:6]]

    def bloc(wx, wy, wz):
        q = (ro[0], ro[1], ro[2])
        l = (wx - q[0], wy - q[1], wz - q[2])
        if l in RG:
            return RG[l]
        l = (wx - ox, wy - oy, wz - oz)
        if l in G:
            return G[l]
        return "air" if wy > 86 else "sol"
    ax, ay, az = a["arrivee"]
    c = (math.floor(ax), ay, math.floor(az))
    for k in (0, 1):
        if bloc(c[0], c[1] + k, c[2]) not in ("air", "light", "short_grass"):
            err.append(f"{a['nom']} arrivee bouchee {c} {bloc(c[0], c[1] + k, c[2])}")
    if bloc(c[0], c[1] - 1, c[2]) in ("air", "light", "barrier"):
        err.append(f"{a['nom']} arrivee sans sol")
    # de l'arrivee jusqu'a l'interieur de l'arene, en ligne droite
    if a["f"] == "croissant":
        pas = [(x, cz) for x in range(c[0], cx + 39, -1)]
    elif a["f"] == "jungle":
        pas = []
    else:
        pas = [(cx, z) for z in range(c[2], cz - (51 if a["f"] == "abysses" else 46))]    # la rampe des Abysses monte : verif_acces la controle
    for x, z in pas:
        for k in (0, 1):
            b = bloc(x, 87 + k, z)
            if b not in ("air", "light", "stone_pressure_plate"):
                err.append(f"{a['nom']} entree bouchee {(x, 87 + k, z)} {b}")
    rc = list(RC.values())
    if not any(" tp @s 0 87 20 " in x for x in rc):
        err.append(f"{a['nom']} sans retour")
    rp = a["retour"]
    if math.dist((ax, ay, az), rp) > 7:
        err.append(f"{a['nom']} retour trop loin")
    print(f"{a['n']} {a['nom']:<14} arrivee {c} retour {rp} entree libre sur {len(pas)} cases, commandes retour {len(rc)}")
    tp = [x for x in HC.values() if re.search(rf"run tp @s {ax} {ay} {az} ", x)]
    if len(tp) != 1:
        err.append(f"{a['nom']} : porte du hub absente")
    gx, gy, gz = a["porte"]
    l = (gx + 52, 9, gz + 52)
    if H.get(l, "air") != "air" or H.get((l[0], 10, l[2]), "air") != "air":
        err.append(f"{a['nom']} : centre de porte bouche {H.get(l)}")
for i, p in enumerate(zones):
    if -60 < p[3] and p[1] < 60 and -60 < p[4] and p[2] < 60:
        err.append(f"{p[0]} touche le hub")
    for q in zones[i + 1:]:
        if p[1] < q[3] and q[1] < p[3] and p[2] < q[4] and q[2] < p[4]:
            err.append(f"{p[0]} touche {q[0]}")
print("ERREURS", len(err), err[:8])
print("RESULTAT :", "monde final coherent" if not err else "A CORRIGER")
sys.exit(1 if err else 0)
