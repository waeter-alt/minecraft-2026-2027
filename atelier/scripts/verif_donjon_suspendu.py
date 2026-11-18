# [claude-build]
"""
verif_donjon_suspendu.py - controle de la salle 1 du Donjon Suspendu, a pied : depuis l'entree (sur la plaine) on atteint le centre de la salle, l'emplacement du coffre et ceux des zombies ;
portes fermees, on n'atteint ni les couloirs 1, 2 et 3 ni les escaliers ; portes ouvertes, on atteint tout le fond des deux escaliers et le bout du couloir 3 ; aucune case atteignable n'est dans
le noir (lumiere d'au moins 5) ; chaque escalier descend d'une marche a la fois. Modele : la plaine et la roche sont pleines sous le sol (Y local de Y0), la structure ajoute ou creuse.
  test-local/venv/Scripts/python.exe atelier/scripts/verif_donjon_suspendu.py
"""
import json
import sys
from collections import deque
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from suspendu_outils import lire_structure  # noqa: E402

RACINE = Path(__file__).resolve().parent.parent
PASSE = {"air", "lantern", "chain", "light", "ladder", "stone_button", "lava", "cave_vines", "cave_vines_plant"}      # la lave et les lianes ne bloquent ni le pas, ni le tir, ni la lumiere (mais on ne se tient pas dans la lave : voir `Terrain.lave`)
SEUIL_LUMIERE = 5


class Terrain:
    """Les blocs de la structure sur un monde plein sous le sol (y <= sol) et vide au dessus."""

    def __init__(self, blocs, sol):
        self.blocs, self.sol = blocs, sol
        xs, zs = [p[0] for p in blocs], [p[2] for p in blocs]
        self.borne = (min(xs) - 6, max(xs) + 6, min(zs) - 6, max(zs) + 6)           # la plaine est infinie : on la ferme un peu autour de la structure

    def solide(self, p):
        x0, x1, z0, z1 = self.borne
        if not (x0 <= p[0] <= x1 and z0 <= p[2] <= z1):
            return True
        b = self.blocs.get(p)
        if b is None:
            return p[1] <= self.sol
        return b[0].split(":")[-1] not in PASSE

    def lave(self, p):
        b = self.blocs.get(p)
        return b is not None and b[0].endswith(":lava")


def marcher(t, departs, chute_max=6):
    """Cases de pieds atteignables : marche, montee de 1 au plus, chute de `chute_max` au plus (6 par defaut), deux cases libres de haut, echelles (on y monte et on en descend d'un bloc a la fois)."""
    def echelle(c):
        b = t.blocs.get(c)
        return b is not None and b[0].split(":")[-1] == "ladder"

    def valide(c):
        return not t.solide(c) and not t.solide((c[0], c[1] + 1, c[2])) and not t.lave(c)         # on ne se tient pas dans la lave

    def mur_haut(c):                                                        # un muret (1,5 bloc) ne se franchit pas d'un saut et ne sert pas de marche
        b = t.blocs.get(c)
        return b is not None and b[0].split(":")[-1].endswith(("_wall", "_fence"))          # une barriere (fence) monte aussi a 1,5

    def appui(c):
        dessous = (c[0], c[1] - 1, c[2])
        return (t.solide(dessous) and not mur_haut(dessous)) or echelle(c)

    vu, file_ = set(), deque()
    for d in departs:
        if valide(d) and appui(d):
            vu.add(d)
            file_.append(d)
    while file_:
        x, y, z = file_.popleft()
        voisins = []
        if echelle((x, y, z)):                                              # sur une echelle : un bloc plus haut ou plus bas, si l'echelle continue
            voisins += [c for c in ((x, y + 1, z), (x, y - 1, z)) if echelle(c) and valide(c)]
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, y, z + dz)
            cible = None
            if valide(n):
                if appui(n):
                    cible = n
                else:
                    yy = y
                    while yy > 1 and not appui((n[0], yy, n[2])) and valide((n[0], yy - 1, n[2])):
                        yy -= 1
                    if appui((n[0], yy, n[2])) and y - yy <= chute_max:
                        cible = (n[0], yy, n[2])
            u = (x + dx, y + 1, z + dz)
            if cible is None and valide(u) and appui(u) and not t.solide((x, y + 2, z)):
                cible = u
            if cible is not None:
                voisins.append(cible)
        for cible in voisins:
            if cible not in vu:
                vu.add(cible)
                file_.append(cible)
    return vu


def lumiere(t, sources, cases, minimum=SEUIL_LUMIERE):
    """Niveau de lumiere des `cases` (propagation depuis les sources a 15, un cran par bloc, a travers ce qui n'est pas solide ; on s'arrete a `minimum`)."""
    niveau, file_ = {}, deque()
    for s in sources:
        x, y, z = s[:3]
        n0 = s[3] if len(s) > 3 else 15                                # une source est (x, y, z) ou (x, y, z, niveau) : un bloc `light` n'emet pas toujours 15
        for q in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
            if not t.solide(q) and niveau.get(q, 0) < n0 - 1:
                niveau[q] = n0 - 1
                file_.append(q)
        if not t.solide((x, y, z)) and niveau.get((x, y, z), 0) < n0:
            niveau[(x, y, z)] = n0
            file_.append((x, y, z))
    while file_:
        p = file_.popleft()
        n = niveau[p]
        if n <= minimum:
            continue
        x, y, z = p
        for q in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
            if not t.solide(q) and niveau.get(q, 0) < n - 1:
                niveau[q] = n - 1
                file_.append(q)
    return {c: niveau.get(c, 0) for c in cases}


def verifier(structure_nbt, cibles_json):
    ci = json.loads(Path(cibles_json).read_text(encoding="utf-8"))
    sol = ci["sol"]
    blocs = lire_structure(structure_nbt, tuple(ci["decalages"]["salle1"]))
    erreurs = []
    ouvert = dict(blocs)
    for n, p in ci["portes"].items():                                      # les portes ouvertes : on retire la dalle et sa spirale
        for (x, z) in p["cases"]:
            for y in range(p["y"][0], p["y"][1] + 1):
                if ouvert.get((x, y, z), ("",))[0] in p["blocs"]:
                    del ouvert[(x, y, z)]
                    ouvert[(x, y, z)] = ("minecraft:air", ())
        if not p["cases"]:
            erreurs.append(f"porte {n} : aucune case de porte")
    ferme, libre = Terrain(blocs, sol), Terrain(ouvert, sol)
    depart = tuple(ci["entree"])
    vu_f = marcher(ferme, [depart])
    vu_o = marcher(libre, [depart])
    if depart not in vu_f:
        erreurs.append(f"l'entree {depart} n'est pas une case ou l'on se tient")
    for nom in ("centre", "coffre"):
        if tuple(ci[nom]) not in vu_f:
            erreurs.append(f"{nom} {tuple(ci[nom])} n'est pas atteignable depuis l'entree")
    for z in ci["zombies"]:
        if tuple(z) not in vu_f:
            erreurs.append(f"l'emplacement de zombie {tuple(z)} n'est pas atteignable")
    for nom, marches in ci["marches"].items():
        bas = min(marches, key=lambda m: m[1])
        haut = max(marches, key=lambda m: m[1])
        if tuple(bas) in vu_f or tuple(haut) in vu_f:
            erreurs.append(f"escalier {nom} : atteignable alors que la porte est fermee")
        if tuple(bas) not in vu_o:
            erreurs.append(f"escalier {nom} : le bas {tuple(bas)} n'est pas atteignable porte ouverte")
        ordre = sorted(marches, key=lambda m: m[2])
        parcours = {}
        for (x, y, z) in ordre:
            parcours.setdefault(z, set()).add(y)
        zs = sorted(parcours)
        for a, b in zip(zs, zs[1:]):
            if b - a != 1 or max(parcours[b]) != max(parcours[a]) - 1:
                erreurs.append(f"escalier {nom} : la marche en z={b} ne descend pas d'un cran")
                break
    cases_c3 = [c for c in vu_o if c not in vu_f]
    if not cases_c3:
        erreurs.append("porte ouverte : aucune case de plus n'est atteignable")
    sources = [tuple(s) for s in ci["sources"]]
    sous_toit = {(x, z) for (x, y, z), b in blocs.items() if y > sol + 1 and b[0].split(":")[-1] not in PASSE}       # les colonnes couvertes : le batiment ; la plaine autour n'est pas a eclairer
    noirs = [c for c, n in lumiere(libre, sources, [c for c in vu_o if (c[0], c[2]) in sous_toit]).items() if n < SEUIL_LUMIERE]
    if noirs:
        erreurs.append(f"{len(noirs)} cases atteignables sous la lumiere {SEUIL_LUMIERE} (ex. {sorted(noirs)[:4]})")
    (Path(structure_nbt).parent / "suspendu_verif.json").write_text(json.dumps({"ok": not erreurs, "erreurs": erreurs, "cases_porte_fermee": len(vu_f), "cases_porte_ouverte": len(vu_o)}), encoding="utf-8")
    return erreurs


if __name__ == "__main__":
    ex = RACINE / "exports"
    err = verifier(ex / "suspendu_salle1.nbt", ex / "suspendu_cibles.json")
    print("RESULTAT", "OK" if not err else f"{len(err)} ERREUR(S)")
    for e in err[:30]:
        print(" -", e)
    sys.exit(0 if not err else 1)
