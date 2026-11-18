# [claude-build]
"""
capture_retouches_station.py - garde les retouches que Luca fait a la main, en jeu, sur le Donjon de la Station.
Lit le monde (client ferme, ou apres /save-all), compare chaque case du gabarit a ce que le generateur pose, et ecrit les differences dans
atelier/donjon/station_retouches.json : {"x,y,z": "minecraft:bloc[prop=val]"} en coordonnees du gabarit. Le generateur (donjon_station.py) les repose
par-dessus tout (structure station_retouches, posee en dernier) : une regeneration ne les ecrase plus.
Ne sont PAS des retouches (exclues) : les cases que le datapack change en jouant (toute case visee par un setblock ou un fill de ses fonctions : vannes,
eau de la Cuve, moteurs, tartre, casier, panneaux...), l'eau qui coule (une eau en vaut une autre), et ce qui deborde du gabarit.
Ne se capturent pas : le contenu des coffres et le texte des panneaux (donnees d'entite de bloc).
  test-local/venv/Scripts/python.exe atelier/scripts/capture_retouches_station.py [--monde "<dossier du monde>"] [--fusionner]
--fusionner : ajoute aux retouches deja gardees (sinon on repart de zero... mais les retouches deja posees dans le monde sont recapturees de toute facon).
"""
import argparse
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import donjon_station as ds  # noqa: E402
import station_acte1 as a1  # noqa: E402
import station_secteurs as s2  # noqa: E402
from lire_monde import Monde  # noqa: E402

from station_retouches import FICHIER, charger, etat  # noqa: E402
from suspendu_outils import lire_structure  # noqa: E402


def pose_du_monde(save):
    """Ce qui a ete pose DANS CE MONDE : les structures de son propre datapack, aux places que disent ses fonctions de pose, dans l'ordre (coordonnees du gabarit).
    Comparer a cela, et non a la version d'aujourd'hui du generateur, ne fait ressortir que les retouches a la main."""
    dp = Path(save) / "datapacks" / "atelier" / "data" / "atelier"
    places = []
    for f in sorted((dp / "function").glob("station_pose_2*.mcfunction"), key=lambda p: int(p.stem.split("_")[-1]) if p.stem != "station_pose_2" else 0):
        for m in re.finditer(r"place template atelier:([a-z_0-9]+) (-?\d+) (-?\d+) (-?\d+)", f.read_text(encoding="utf-8")):
            places.append((m.group(1), tuple(int(v) for v in m.groups()[1:])))
    o = ds.ORIGINE
    pose = {}
    for nom, (wx, wy, wz) in places:
        pose.update(lire_structure(dp / "structure" / f"{nom}.nbt", (wx - o[0], wy - o[1], wz - o[2])))
    return pose, dp


def attendu_terrain(y):
    if y <= a1.PLAINE - 3:
        return "minecraft:stone"
    if y < a1.PLAINE:
        return "minecraft:dirt"
    if y == a1.PLAINE:
        return "minecraft:grass_block"
    return "minecraft:air"


def cases_dynamiques(dp=None):
    """Les cases que les fonctions du datapack changent (setblock, fill) : ce ne sont pas des retouches. `dp` : le datapack du monde (sinon, celui du generateur)."""
    if dp is not None:
        f = {p.stem: p.read_text(encoding="utf-8") for p in (dp / "function").glob("station_*.mcfunction")}
    else:
        f = ds.fonctions(a1.construire()[1], {n: (0, 0, 0) for n in ("station_cuve", "station_hall") + s2.ORDRE})
    o = ds.ORIGINE
    dyn = set()
    for nom, texte in f.items():
        if nom.startswith(("station_pose", "station_cine_station")):
            continue
        for m in re.finditer(r"setblock (-?\d+) (-?\d+) (-?\d+) ", texte):
            x, y, z = (int(v) for v in m.groups())
            dyn.add((x - o[0], y - o[1], z - o[2]))
        for m in re.finditer(r"fill (-?\d+) (-?\d+) (-?\d+) (-?\d+) (-?\d+) (-?\d+) ", texte):
            if nom.startswith("station_pose"):
                continue
            a = [int(v) for v in m.groups()]
            if (abs(a[3] - a[0]) + 1) * (abs(a[4] - a[1]) + 1) * (abs(a[5] - a[2]) + 1) > 40000:
                continue
            for x in range(min(a[0], a[3]), max(a[0], a[3]) + 1):
                for y in range(min(a[1], a[4]), max(a[1], a[4]) + 1):
                    for z in range(min(a[2], a[5]), max(a[2], a[5]) + 1):
                        dyn.add((x - o[0], y - o[1], z - o[2]))
    return dyn


def cases_vitales():
    """Les cases dont le donjon a besoin pour se jouer : volants, leviers, ancrages du grappin, casier de la cape."""
    _, i1 = a1.construire()
    _, i2 = s2.construire()
    v = {p: "volant du Hall" for p in i1["leviers"].values()}
    v[i1["casier"]] = "casier de la cape"
    for k in ("a", "b", "c"):
        v[i2[k]["volant_pos"]] = f"volant de la pompe {k.upper()}"
    v[i2["d"]["levier"]] = "levier general"
    for (nom, x, y, z) in i1["ancrages"] + i2["ancrages"]:
        v[(x, y, z)] = f"ancrage {nom}"
    return v


def meme(a, b):
    eaux = ("minecraft:water", "minecraft:bubble_column")                 # une colonne de bulles, c'est de l'eau (le sable des ames la fait apparaitre)
    if a[0] in eaux and b[0] in eaux:
        return True                                                         # l'eau qui coule : une eau en vaut une autre
    if a[0] != b[0]:
        return False
    da, db = dict(a[1]), dict(b[1])
    connexions = any(m in a[0] for m in ("_wall", "fence", "bars", "pane", "fluid_pipe"))       # le jeu recalcule leurs connexions
    for k in set(db):                                                       # seulement ce que le generateur fixe : le jeu ajoute ses valeurs par defaut (snowy...)
        if connexions and k in ("north", "south", "east", "west", "up", "down"):
            continue
        if k in ("waterlogged", "distance", "persistent", "powered", "lit", "open") and da.get(k) != db.get(k):
            continue                                                        # etats qui bougent seuls ou en jouant
        if da.get(k) != db.get(k):
            return False
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--monde", default=str(ds.SAVES / ds.NOM_MONDE))
    ap.add_argument("--fusionner", action="store_true")
    args = ap.parse_args()
    monde = Monde(args.monde)
    attendu, dp = pose_du_monde(args.monde)
    if not attendu:
        sys.exit("Le datapack du monde n'a pas de fonctions de pose : impossible de savoir ce qui y a ete pose.")
    gardees = charger()
    attendu.update(gardees)                                                 # une retouche deja gardee n'est pas une nouvelle difference
    dyn = cases_dynamiques(dp)
    o = ds.ORIGINE
    nouvelles, manque = {}, 0
    for x in range(ds.SX):
        for z in range(ds.SZ):
            for y in range(0, a1.PLAINE + 12):
                p = (x, y, z)
                if p in dyn:
                    continue
                vu = monde.bloc(x + o[0], y + o[1], z + o[2])
                if vu is None:
                    manque += 1
                    break
                voulu = attendu.get(p, (attendu_terrain(y), ()))
                if not meme(vu, voulu):
                    nouvelles[p] = vu
    vitales = cases_vitales()                                                # un volant, un levier, un ancrage, le casier : on ne les retouche pas (le donjon en a besoin)
    for p in [p for p in nouvelles if p in vitales]:
        print(f"PROTEGE : {p} ({vitales[p]}) est {etat(nouvelles[p])} dans le monde : non garde (il est necessaire au donjon).")
        del nouvelles[p]
    resultat = dict(gardees) if args.fusionner else {p: b for p, b in gardees.items()}
    resultat.update(nouvelles)
    FICHIER.parent.mkdir(parents=True, exist_ok=True)
    FICHIER.write_text(json.dumps({"description": "Retouches faites a la main en jeu par Luca sur le Donjon de la Station (coordonnees du gabarit) ; reposees par le generateur par-dessus tout.",
                                   "blocs": {f"{x},{y},{z}": etat(b) for (x, y, z), b in sorted(resultat.items())}}, indent=1, ensure_ascii=False), encoding="utf-8")
    par_nom = {}
    for b in nouvelles.values():
        par_nom[b[0]] = par_nom.get(b[0], 0) + 1
    print(f"{len(nouvelles)} nouvelle(s) retouche(s) ; {len(resultat)} gardee(s) en tout -> {FICHIER}")
    print("par bloc :", dict(sorted(par_nom.items(), key=lambda kv: -kv[1])[:15]))
    if manque:
        print(f"ATTENTION : {manque} colonne(s) jamais chargee(s) dans le monde (pas encore posees ?) : non comparees.")


if __name__ == "__main__":
    main()
